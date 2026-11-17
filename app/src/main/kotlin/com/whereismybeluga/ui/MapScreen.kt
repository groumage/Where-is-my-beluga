package com.whereismybeluga.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.annotation.StringRes
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whereismybeluga.R
import com.whereismybeluga.data.FleetEntry
import com.whereismybeluga.data.AdsbLolErrorKind
import com.whereismybeluga.data.ManualRefresh
import com.whereismybeluga.data.ParkedGroup
import com.whereismybeluga.data.markerKeyFor
import com.whereismybeluga.data.splitFleet
import com.whereismybeluga.map.BelugaMap
import com.whereismybeluga.map.INITIAL_CAMERA
import com.whereismybeluga.map.MapController
import com.whereismybeluga.map.MapInsets
import com.whereismybeluga.map.MapMarker
import com.whereismybeluga.map.MarkerKind
import com.whereismybeluga.map.MarkerPalette
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun MapScreen(viewModel: FleetViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalBelugaColors.current
    val controller = remember { MapController() }

    val context = LocalContext.current
    val activity = LocalActivity.current

    // Set when the user taps the card: only then may a refusal send them to the settings.
    var userAskedForLocation by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val permanentlyDenied = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
        // Once denied for good, Android no longer shows the dialog: only the app settings can grant it.
        if (userAskedForLocation && permanentlyDenied) context.openSettings(appDetailsIntent(context))
        userAskedForLocation = false
        viewModel.refreshLocation()
    }
    fun requestLocation() = permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)

    // Ask once per launch, not again each time the activity is recreated (e.g. theme switch).
    var askedForLocation by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!askedForLocation) {
            askedForLocation = true
            requestLocation()
        }
    }

    // Only poll adsb.lol while the app is on screen. Coming back (e.g. from the
    // location settings) also looks for the user again.
    LifecycleStartEffect(Unit) {
        viewModel.startPolling()
        if (askedForLocation) viewModel.refreshLocation()
        onStopOrDispose {
            viewModel.stopPolling()
            // In the background Android answers location requests at once with
            // nothing, so the retry loop would only spin.
            viewModel.stopLocation()
        }
    }

    // In-app messages: Android 12+ cuts system toasts to two lines.
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    /** [long] for messages worth reading twice; any message closes with a tap or its cross. */
    fun showMessage(@StringRes message: Int, vararg args: Any, long: Boolean = true) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                resources.getString(message, *args),
                withDismissAction = true,
                duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        }
    }

    fun enableAlerts() {
        viewModel.setAlertsEnabled(true)
        if (state.userLocation == null) {
            showMessage(R.string.alerts_enabled_no_location)
        } else {
            showMessage(R.string.alerts_enabled, state.alertRadiusKm)
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            enableAlerts()
        } else if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            // Denied for good: only the app's notification settings can allow it now.
            context.openSettings(notificationSettingsIntent(context))
        } else {
            showMessage(R.string.alerts_denied)
        }
    }

    fun onToggleAlerts() {
        when {
            state.alertsEnabled -> {
                viewModel.setAlertsEnabled(false)
                showMessage(R.string.alerts_disabled, long = false)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED ->
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            // Notifications switched off in the system settings (no runtime permission before Android 13).
            !NotificationManagerCompat.from(context).areNotificationsEnabled() ->
                context.openSettings(notificationSettingsIntent(context))
            else -> enableAlerts()
        }
    }

    fun onLocationCardClick() {
        when (state.locationStatus) {
            LocationStatus.DISABLED, LocationStatus.UNAVAILABLE ->
                context.openSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            LocationStatus.DENIED -> {
                userAskedForLocation = true
                requestLocation()
            }
            else -> viewModel.refreshLocation()
        }
    }

    val nearest = state.nearest
    val nearestIcao24 = nearest?.entry?.aircraft?.icao24
    val (tracked, parked) = remember(state.entries) { splitFleet(state.entries) }
    val markers = remember(tracked, parked, nearestIcao24) {
        tracked.map { entry ->
            val track = entry.live?.trueTrackDegrees
            MapMarker(
                key = entry.aircraft.icao24,
                position = entry.displayedPosition,
                kind = when {
                    entry.aircraft.icao24 == nearestIcao24 -> MarkerKind.NEAREST
                    entry.isFlying -> MarkerKind.FLYING
                    else -> MarkerKind.GROUND
                },
                // The drawing faces west: mirror it for eastbound flights.
                facingRight = entry.isFlying && track != null && track > 0 && track < 180,
            )
        } + parked.map { group ->
            MapMarker(group.key, group.base.position, MarkerKind.GROUND, count = group.entries.size)
        }
    }
    val palette = remember(colors) {
        MarkerPalette(
            flying = colors.markerFlying.toArgb(),
            ground = colors.markerParked.toArgb(),
            nearest = colors.highlight.toArgb(),
            outline = colors.markerOutline.toArgb(),
            badge = colors.button.toArgb(),
            badgeText = colors.buttonText.toArgb(),
        )
    }

    var topInset by remember { mutableIntStateOf(0) }
    var bottomInset by remember { mutableIntStateOf(0) }

    fun selectEntry(entry: FleetEntry) {
        viewModel.select(Selection.Aircraft(entry.aircraft.icao24))
        controller.center(entry.displayedPosition)
    }

    Box(Modifier.fillMaxSize()) {
        BelugaMap(
            markers = markers,
            userLocation = state.userLocation,
            dark = colors.dark,
            palette = palette,
            insets = MapInsets(topInset, bottomInset),
            initialCamera = viewModel.camera ?: INITIAL_CAMERA,
            controller = controller,
            onMarkerClick = { key ->
                val entry = state.entries.firstOrNull { it.aircraft.icao24 == key }
                val group = parked.firstOrNull { it.key == key }
                if (entry != null) {
                    selectEntry(entry)
                } else if (group != null) {
                    viewModel.select(Selection.Parked(key))
                    controller.center(group.base.position)
                }
            },
            onMapClick = { viewModel.select(Selection.None) },
            onMapLongClick = viewModel::setManualLocation,
            onCameraIdle = { viewModel.camera = it },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .onGloballyPositioned { topInset = it.boundsInRoot().bottom.roundToInt() },
        ) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NearestBelugaCard(
                    state = state,
                    onClick = { if (nearest != null) selectEntry(nearest.entry) else onLocationCardClick() },
                    modifier = Modifier.weight(1f),
                )
                RefreshButton(
                    refreshing = state.refreshing,
                    onClick = {
                        if (viewModel.refresh() == ManualRefresh.TOO_SOON) {
                            showMessage(R.string.next_update_soon, long = false)
                        }
                    },
                )
            }
            if (state.manualLocation != null) ManualLocationBanner(onReset = viewModel::clearManualLocation)
            state.error?.let { ErrorBanner(it, state.rateLimitedUntilMillis) }
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    data,
                    containerColor = colors.button,
                    contentColor = colors.buttonText,
                    dismissActionContentColor = colors.buttonText,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { data.dismiss() },
                )
            }
            val selection = state.selection
            val selectedEntry = (selection as? Selection.Aircraft)
                ?.let { s -> state.entries.firstOrNull { it.aircraft.icao24 == s.icao24 } }
            val selectedGroup = when {
                selection is Selection.Parked -> parked.firstOrNull { it.key == selection.markerKey }
                // An aircraft without signal is drawn inside its parked group.
                selectedEntry != null && selectedEntry.livePosition == null ->
                    parked.firstOrNull { it.key == markerKeyFor(selectedEntry) }
                else -> null
            }
            // The detail card counts as covered map too: a selected aircraft must stay above it.
            Column(Modifier.onSizeChanged { bottomInset = it.height }) {
                if (selectedGroup != null) {
                    ParkedDetailCard(selectedGroup, onClose = { viewModel.select(Selection.None) })
                } else if (selectedEntry != null) {
                    AircraftDetailCard(selectedEntry, onClose = { viewModel.select(Selection.None) })
                }
                FleetPanel(
                    state = state,
                    nearestIcao24 = nearestIcao24,
                    onSelect = ::selectEntry,
                    onSelectAll = {
                        viewModel.select(Selection.All)
                        controller.fit(markers.map { it.position })
                    },
                    onToggleAlerts = ::onToggleAlerts,
                    onOpenSettings = { settingsOpen = true },
                    onAlertRadiusChange = viewModel::setAlertRadiusKm,
                )
            }
        }
    }
}

/** Top-of-screen banner: the Beluga in flight closest to the user. */
@Composable
private fun NearestBelugaCard(state: FleetUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalBelugaColors.current
    val nearest = state.nearest
    val noneFlying = state.lastUpdatedMillis != null && state.flyingCount == 0
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .shadow(4.dp, shape)
            .clip(shape)
            .background(colors.surface)
            .border(2.dp, if (noneFlying) colors.error else colors.highlight, shape)
            .clickable(enabled = !noneFlying, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        when {
            noneFlying -> {
                Text(
                    stringResource(R.string.no_beluga_in_flight),
                    color = colors.error, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                )
                Text(stringResource(R.string.no_beluga_in_flight_detail), color = colors.text, fontSize = 13.sp)
            }
            state.lastUpdatedMillis == null ->
                Text(stringResource(R.string.loading_positions), color = colors.text, fontSize = 13.sp)
            else -> {
                Text(
                    stringResource(R.string.nearest_label).uppercase(),
                    color = colors.highlightLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                )
                if (nearest != null) {
                    Text(
                        "${nearest.entry.aircraft.nickname} · ${formatDistance(nearest.distanceKm)}",
                        color = colors.title, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(flyingLabel(nearest.entry), color = colors.text, fontSize = 13.sp)
                } else {
                    Text(
                        stringResource(
                            when (state.locationStatus) {
                                LocationStatus.DENIED -> R.string.location_denied
                                LocationStatus.DISABLED -> R.string.location_disabled
                                LocationStatus.UNAVAILABLE -> R.string.location_unavailable
                                else -> R.string.locating
                            },
                        ),
                        color = colors.text, fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

private fun notificationSettingsIntent(context: Context) =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

private fun appDetailsIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

/** Opens a settings screen; some devices lack one, so fail quietly rather than crash. */
private fun Context.openSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}

@Composable
private fun flyingLabel(entry: FleetEntry): String =
    entry.live?.callsign?.let { stringResource(R.string.in_flight_callsign, it) }
        ?: stringResource(R.string.in_flight)

@Composable
private fun RefreshButton(refreshing: Boolean, onClick: () -> Unit) {
    val colors = LocalBelugaColors.current
    val label = stringResource(R.string.refresh_positions)
    val shape = RoundedCornerShape(14.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
            .shadow(4.dp, shape)
            .clip(shape)
            .background(colors.button)
            .clickable(enabled = !refreshing, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        if (refreshing) {
            CircularProgressIndicator(Modifier.size(24.dp), color = colors.buttonText, strokeWidth = 2.5.dp)
        } else {
            Text("⟳", color = colors.buttonText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Reminds that the position was set by hand, and offers to go back to the device location. */
@Composable
private fun ManualLocationBanner(onReset: () -> Unit) {
    val colors = LocalBelugaColors.current
    Text(
        stringResource(R.string.manual_location),
        color = colors.text, fontSize = 12.sp, textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onReset)
            .padding(10.dp),
    )
}

@Composable
private fun ErrorBanner(kind: AdsbLolErrorKind, retryAtMillis: Long?) {
    val colors = LocalBelugaColors.current
    val retryAt = retryAtMillis?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }
    Text(
        if (kind == AdsbLolErrorKind.RATE_LIMITED && retryAt != null) {
            stringResource(R.string.error_rate_limited_until, retryAt)
        } else stringResource(
            when (kind) {
                AdsbLolErrorKind.RATE_LIMITED -> R.string.error_rate_limited
                AdsbLolErrorKind.TIMEOUT -> R.string.error_timeout
                AdsbLolErrorKind.NETWORK -> R.string.error_network
                AdsbLolErrorKind.SERVER -> R.string.error_server
                AdsbLolErrorKind.INVALID_RESPONSE -> R.string.error_invalid_response
            },
        ),
        color = colors.buttonText, fontSize = 12.sp, textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.error)
            .padding(10.dp),
    )
}

/** Details of the selected aircraft, shown just above the fleet panel. */
@Composable
private fun AircraftDetailCard(entry: FleetEntry, onClose: () -> Unit) {
    val live = entry.live
    DetailCard("${entry.aircraft.nickname} · ${entry.aircraft.registration}", onClose) {
        if (entry.isFlying && live != null) {
            DetailLine(stringResource(R.string.flight, live.callsign ?: "—"))
            DetailLine(stringResource(R.string.altitude, formatAltitude(live.baroAltitudeMeters)))
            DetailLine(stringResource(R.string.speed, formatSpeed(live.velocityMetersPerSecond)))
            DetailLine(stringResource(R.string.heading, formatHeading(live.trueTrackDegrees)))
        } else {
            DetailLine(stringResource(R.string.on_ground))
        }
    }
}

@Composable
private fun ParkedDetailCard(group: ParkedGroup, onClose: () -> Unit) {
    val colors = LocalBelugaColors.current
    DetailCard(group.base.name, onClose) {
        Text(stringResource(R.string.no_signal), color = colors.textMuted, fontSize = 12.sp)
        group.entries.forEach { DetailLine("• ${it.aircraft.nickname} · ${it.aircraft.registration}") }
    }
}

@Composable
private fun DetailCard(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    val colors = LocalBelugaColors.current
    val shape = RoundedCornerShape(12.dp)
    val closeLabel = stringResource(R.string.close)
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .shadow(4.dp, shape)
            .clip(shape)
            .background(colors.surface)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            content()
        }
        Text(
            "✕", color = colors.textMuted, fontSize = 16.sp, textAlign = TextAlign.Center,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onClose)
                .semantics { contentDescription = closeLabel }
                .padding(horizontal = 14.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun DetailLine(text: String) {
    Text(text, color = LocalBelugaColors.current.text, fontSize = 13.sp)
}
