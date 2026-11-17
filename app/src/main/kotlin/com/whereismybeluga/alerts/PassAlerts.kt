package com.whereismybeluga.alerts

import android.content.Context
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.whereismybeluga.data.DEFAULT_PASS_RADIUS_KM
import com.whereismybeluga.data.passRadiusOrDefault
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private const val PREFS_NAME = "pass_alerts"
private const val KEY_ENABLED = "enabled"
private const val KEY_RADIUS_KM = "radius_km"

private const val PERIODIC_WORK = "pass-check"
private const val NOW_WORK = "pass-check-now"
private const val RECHECK_TAG = "pass-recheck"
private const val KEY_RECHECK_PREFIX = "recheck_at_"

/** Two rechecks planned this close together are the same one. */
private const val RECHECK_TOLERANCE_MS = 60_000L

/** Shortest period Android allows for periodic background work. */
private const val CHECK_PERIOD_MINUTES = 15L

/**
 * Turns the "Beluga passing nearby" alerts on and off, keeps the radius the
 * user picked, and schedules the background checks: one every 15 minutes, plus a recheck just before each
 * predicted pass.
 */
object PassAlerts {
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    /** How close a Beluga must pass to be announced, in km. */
    fun radiusKm(context: Context): Int =
        passRadiusOrDefault(prefs(context).getInt(KEY_RADIUS_KM, DEFAULT_PASS_RADIUS_KM))

    fun setRadiusKm(context: Context, radiusKm: Int) {
        prefs(context).edit { putInt(KEY_RADIUS_KM, passRadiusOrDefault(radiusKm)) }
        // Apply it at once: announce what the wider radius now reaches, and
        // withdraw what the narrower one leaves out.
        if (isEnabled(context)) checkNow(context)
    }

    fun enable(context: Context) {
        prefs(context).edit { putBoolean(KEY_ENABLED, true) }
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PassCheckWorker>(CHECK_PERIOD_MINUTES, TimeUnit.MINUTES)
                .setConstraints(networkConstraint())
                .build(),
        )
        // First check right away, so a Beluga already on its way is announced.
        checkNow(context)
    }

    private fun checkNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PassCheckWorker>().setConstraints(networkConstraint()).build(),
        )
    }

    fun disable(context: Context) {
        prefs(context).edit { putBoolean(KEY_ENABLED, false) }
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(PERIODIC_WORK)
        workManager.cancelUniqueWork(NOW_WORK)
        workManager.cancelAllWorkByTag(RECHECK_TAG)
        prefs(context).edit {
            prefs(context).all.keys.filter { it.startsWith(KEY_RECHECK_PREFIX) }.forEach(::remove)
        }
        PassNotifier(context).cancelAll()
    }

    /**
     * Checks again for [icao24] in [delayMinutes], to confirm or correct the
     * prediction. Skipped if a recheck is already planned at about that time.
     *
     * Not unique work on purpose: a recheck that schedules the next one would
     * otherwise replace, and so cancel, itself while running.
     */
    fun scheduleRecheck(context: Context, icao24: String, delayMinutes: Double) {
        val delayMs = (delayMinutes * 60_000).toLong()
        val at = System.currentTimeMillis() + delayMs
        val key = KEY_RECHECK_PREFIX + icao24
        val planned = prefs(context).getLong(key, 0L)
        if (planned > System.currentTimeMillis() && abs(planned - at) < RECHECK_TOLERANCE_MS) return
        prefs(context).edit { putLong(key, at) }
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<PassCheckWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setConstraints(networkConstraint())
                .addTag(RECHECK_TAG)
                .build(),
        )
    }

    private fun networkConstraint() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
