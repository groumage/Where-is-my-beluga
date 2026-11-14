package com.whereismybeluga.alerts

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.whereismybeluga.data.AdsbLolException
import com.whereismybeluga.data.BELUGA_FLEET
import com.whereismybeluga.data.FleetEntry
import com.whereismybeluga.data.liveStatesSource
import com.whereismybeluga.data.predictPasses
import com.whereismybeluga.location.LocationProvider
import com.whereismybeluga.location.LocationStore

/** Check again this long before a predicted pass, to confirm it with fresh data. */
private const val RECHECK_BEFORE_PASS_MINUTES = 2.0

/**
 * Background check: fetches the fleet once, predicts which Beluga will pass
 * within 5 km of the user in the next 20 minutes, and notifies.
 */
class PassCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!PassAlerts.isEnabled(context)) return Result.success()

        val store = LocationStore(context)
        // The manual position wins, as on screen. A fresh fix is only possible
        // while the app is open; otherwise use the last one it found.
        val from = store.manual ?: LocationProvider(context).lastKnown() ?: store.lastDevice
            ?: return Result.success()

        val live = try {
            liveStatesSource(context).fetchLiveStates(BELUGA_FLEET.map { it.icao24 })
        } catch (e: AdsbLolException) {
            // Quota, network or server trouble: the next periodic check will try again.
            return Result.success()
        }
        val entries = BELUGA_FLEET.map { FleetEntry(it, live[it.icao24]) }
        val passes = predictPasses(entries, from)

        val notifier = PassNotifier(context)
        for (pass in passes) {
            notifier.show(pass)
            if (pass.minutesUntil > RECHECK_BEFORE_PASS_MINUTES + 1) {
                PassAlerts.scheduleRecheck(
                    context, pass.entry.aircraft.icao24, pass.minutesUntil - RECHECK_BEFORE_PASS_MINUTES,
                )
            }
        }
        // A Beluga announced earlier that turned away is no longer coming.
        notifier.cancelDiverted(keep = passes.map { it.entry.aircraft.icao24 }.toSet())
        return Result.success()
    }
}
