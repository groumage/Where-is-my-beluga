package com.whereismybeluga.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.whereismybeluga.data.Coordinate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val RETRY_DELAY_MS = 1_000L
private const val MAX_CACHED_AGE_MS = 6 * 60 * 60 * 1_000L

/**
 * Approximate user location straight from the platform LocationManager:
 * no Google Play services, and only the coarse location permission.
 */
class LocationProvider(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** False when the system-wide location switch is off: no provider can answer. */
    fun isLocationEnabled(): Boolean = LocationManagerCompat.isLocationEnabled(manager)

    /** Most recent cached fix across providers, if under a few hours old: instant, but possibly stale. */
    @SuppressLint("MissingPermission")
    fun lastKnown(): Coordinate? {
        if (!hasPermission()) return null
        return manager.getProviders(true)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            // A fix from days ago may be in another country: better none than a wrong one.
            .filter { it.ageMillis() <= MAX_CACHED_AGE_MS }
            .minByOrNull { it.ageMillis() }
            ?.toCoordinate()
    }

    /**
     * Asks every usable provider for a fresh fix and keeps the first answer;
     * null if none arrives within [timeoutMs].
     *
     * One provider is not enough: with only the coarse permission the system
     * downgrades fused requests to low power, which never turns the GPS on, so
     * fused stays silent when network location is switched off or missing.
     */
    suspend fun current(timeoutMs: Long = 60_000): Coordinate? {
        if (!hasPermission()) return null
        val providers = freshProviders()
        return withTimeoutOrNull(timeoutMs) {
            channelFlow {
                providers.forEach { provider ->
                    launch {
                        // The system gives up on a request after 30 s, less than a
                        // cold GPS start can take: ask again until the deadline.
                        while (true) {
                            request(provider)?.let { return@launch send(it) }
                            delay(RETRY_DELAY_MS)
                        }
                    }
                }
            }.firstOrNull()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun request(provider: String): Coordinate? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(
                    manager, provider, signal, ContextCompat.getMainExecutor(context),
                ) { location: Location? ->
                    if (continuation.isActive) continuation.resume(location?.toCoordinate())
                }
            } catch (_: RuntimeException) {
                // Provider refused or gone (SecurityException, IllegalArgumentException).
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private fun freshProviders(): List<String> {
        val enabled = manager.getProviders(true)
        val sinceS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        return listOfNotNull(
            LocationManager.FUSED_PROVIDER.takeIf { sinceS },
            LocationManager.NETWORK_PROVIDER,
            // Before Android 12 the GPS provider demands the precise permission.
            LocationManager.GPS_PROVIDER.takeIf { sinceS },
        ).filter { it in enabled }
    }

    private fun Location.ageMillis() =
        (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000

    private fun Location.toCoordinate() = Coordinate(latitude, longitude)
}
