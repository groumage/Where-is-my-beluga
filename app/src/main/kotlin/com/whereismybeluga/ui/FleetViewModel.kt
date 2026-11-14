package com.whereismybeluga.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whereismybeluga.alerts.PassAlerts
import com.whereismybeluga.data.BELUGA_FLEET
import com.whereismybeluga.data.Coordinate
import com.whereismybeluga.data.FleetEntry
import com.whereismybeluga.data.NearestBeluga
import com.whereismybeluga.data.AdsbLolErrorKind
import com.whereismybeluga.data.AdsbLolException
import com.whereismybeluga.data.PollSchedule
import com.whereismybeluga.data.findNearestInFlight
import com.whereismybeluga.data.liveStatesSource
import com.whereismybeluga.location.LocationProvider
import com.whereismybeluga.location.LocationStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


enum class LocationStatus {
    LOADING,
    GRANTED,
    DENIED,

    /** The system-wide location switch is off. */
    DISABLED,

    /** Location is on, but no provider found a position (indoors, no Wi-Fi location...). */
    UNAVAILABLE,
}

sealed interface Selection {
    data object None : Selection
    data object All : Selection
    data class Aircraft(val icao24: String) : Selection

    /** A group of aircraft without signal, parked on the same home base. */
    data class Parked(val markerKey: String) : Selection
}

/** Map camera, kept across activity recreation (e.g. light/dark theme switch). */
data class CameraState(val center: Coordinate, val zoom: Double)

data class FleetUiState(
    val entries: List<FleetEntry> = BELUGA_FLEET.map { FleetEntry(it, null) },
    val lastUpdatedMillis: Long? = null,
    /** Why the last fetch failed; the UI turns it into a translated message. */
    val error: AdsbLolErrorKind? = null,
    /** Wall-clock time adsb.lol accepts requests again, after it refused one for quota. */
    val rateLimitedUntilMillis: Long? = null,
    val refreshing: Boolean = false,
    /** Position found by the device's location providers. */
    val deviceLocation: Coordinate? = null,
    /** Position set by hand with a long press on the map; wins over [deviceLocation]. */
    val manualLocation: Coordinate? = null,
    val locationStatus: LocationStatus = LocationStatus.LOADING,
    val selection: Selection = Selection.None,
    /** Background "Beluga passing within 5 km" notifications. */
    val alertsEnabled: Boolean = false,
) {
    val flyingCount: Int get() = entries.count { it.isFlying }

    val userLocation: Coordinate? get() = manualLocation ?: deviceLocation

    /** Closest Beluga in flight; null without a user location or when none is flying. */
    val nearest: NearestBeluga? get() = userLocation?.let { findNearestInFlight(entries, it) }
}

class FleetViewModel(application: Application) : AndroidViewModel(application) {
    private val locationProvider = LocationProvider(application)
    private val fleetIcao24 = BELUGA_FLEET.map { it.icao24 }
    private val liveStates = liveStatesSource(application)
    private val schedule = PollSchedule()

    private val locationStore = LocationStore(application)

    private val _state = MutableStateFlow(
        FleetUiState(manualLocation = locationStore.manual, alertsEnabled = PassAlerts.isEnabled(application)),
    )
    val state: StateFlow<FleetUiState> = _state.asStateFlow()

    var camera: CameraState? = null

    private var pollJob: Job? = null
    private var locationJob: Job? = null

    /**
     * Polls adsb.lol while the app is on screen. The first request goes out at
     * once, later ones keep to the schedule: leaving and coming back to the app
     * does not spend an extra request.
     */
    fun startPolling() {
        if (pollJob?.isActive == true) return
        launchPolling(fetchNow = false)
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        _state.update { it.copy(refreshing = false) }
    }

    /** Refresh asked by the user: skipped if the data is very recent or adsb.lol said to wait. */
    fun refresh() {
        refreshLocation()
        if (schedule.canRefreshNow(SystemClock.elapsedRealtime())) launchPolling(fetchNow = true)
    }

    private fun launchPolling(fetchNow: Boolean) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            if (fetchNow) fetchPositions()
            while (true) {
                delay(schedule.delayBeforeNextFetch(SystemClock.elapsedRealtime()))
                fetchPositions()
            }
        }
    }

    private suspend fun fetchPositions() {
        _state.update { it.copy(refreshing = true) }
        try {
            val live = liveStates.fetchLiveStates(fleetIcao24)
            // Counted once answered: a request cancelled because the app left the
            // screen must not delay the next one by a whole interval.
            schedule.onRequest(SystemClock.elapsedRealtime())
            _state.update {
                it.copy(
                    entries = BELUGA_FLEET.map { aircraft -> FleetEntry(aircraft, live[aircraft.icao24]) },
                    lastUpdatedMillis = System.currentTimeMillis(),
                    error = null,
                    rateLimitedUntilMillis = null,
                    refreshing = false,
                )
            }
        } catch (e: AdsbLolException) {
            schedule.onRequest(SystemClock.elapsedRealtime())
            val rateLimitedUntil = if (e.kind == AdsbLolErrorKind.RATE_LIMITED) {
                val now = SystemClock.elapsedRealtime()
                val resumeAt = schedule.onRateLimited(now, e.retryAfterSeconds)
                System.currentTimeMillis() + (resumeAt - now)
            } else {
                null
            }
            _state.update {
                it.copy(error = e.kind, rateLimitedUntilMillis = rateLimitedUntil, refreshing = false)
            }
        }
    }

    fun refreshLocation() {
        if (!locationProvider.hasPermission()) {
            _state.update { it.copy(locationStatus = LocationStatus.DENIED) }
            return
        }
        if (!locationProvider.isLocationEnabled()) {
            locationJob?.cancel()
            _state.update { it.copy(locationStatus = LocationStatus.DISABLED) }
            return
        }
        // A fix can take a while: let a running request finish instead of restarting it.
        if (locationJob?.isActive == true) return
        if (_state.value.deviceLocation == null) {
            _state.update { it.copy(locationStatus = LocationStatus.LOADING) }
        }
        locationJob = viewModelScope.launch {
            // Show a cached fix right away, then refine it with a fresh one.
            locationProvider.lastKnown()?.let(::onLocation)
            val fresh = locationProvider.current()
            if (fresh != null) {
                onLocation(fresh)
            } else if (_state.value.deviceLocation == null) {
                _state.update { it.copy(locationStatus = LocationStatus.UNAVAILABLE) }
            }
        }
    }

    /** Stops looking for the user, e.g. when the app leaves the screen. */
    fun stopLocation() {
        locationJob?.cancel()
        locationJob = null
    }

    private fun onLocation(location: Coordinate) {
        // Kept for the background pass check, which cannot ask for a fresh position.
        locationStore.lastDevice = location
        _state.update { it.copy(deviceLocation = location, locationStatus = LocationStatus.GRANTED) }
    }

    /** Uses [location] as the user's position, kept across launches until [clearManualLocation]. */
    fun setManualLocation(location: Coordinate) {
        locationStore.manual = location
        _state.update { it.copy(manualLocation = location) }
    }

    /** Goes back to the device's own location. */
    fun clearManualLocation() {
        locationStore.manual = null
        _state.update { it.copy(manualLocation = null) }
        refreshLocation()
    }

    /** Call only once notifications are allowed. */
    fun setAlertsEnabled(enabled: Boolean) {
        if (enabled) PassAlerts.enable(getApplication()) else PassAlerts.disable(getApplication())
        _state.update { it.copy(alertsEnabled = enabled) }
    }

    fun select(selection: Selection) {
        _state.update { it.copy(selection = selection) }
    }
}
