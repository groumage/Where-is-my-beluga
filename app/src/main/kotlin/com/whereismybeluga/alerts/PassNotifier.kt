package com.whereismybeluga.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.whereismybeluga.MainActivity
import com.whereismybeluga.R
import com.whereismybeluga.data.BELUGA_FLEET
import com.whereismybeluga.data.PredictedPass
import com.whereismybeluga.ui.formatDistance
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

private const val CHANNEL_ID = "pass_alerts"
private const val PREFS_NAME = "pass_alerts_shown"

/** Two predictions this close in time are the same pass, refined. */
private const val SAME_PASS_WINDOW_MS = 15 * 60_000L

/** Closer than this, the pass is announced as overhead rather than with a distance. */
private const val OVERHEAD_KM = 1.0

/** The notification goes away on its own this long after the predicted pass. */
private const val EXPIRE_AFTER_PASS_MS = 10 * 60_000L

/**
 * Shows one notification per Beluga about to pass nearby. A refined prediction
 * for the same pass updates it silently; a notification the user dismissed is
 * not shown again for that pass.
 */
class PassNotifier(context: Context) {
    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)
    private val systemManager = this.context.getSystemService(NotificationManager::class.java)

    /** Predicted pass time of each announced aircraft, by ICAO24 address. */
    private val shown = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun show(pass: PredictedPass) {
        if (!canNotify()) return
        ensureChannel()
        val icao24 = pass.entry.aircraft.icao24
        val id = notificationId(icao24)
        val now = System.currentTimeMillis()
        val passAt = now + (pass.minutesUntil * 60_000).toLong()
        val previous = shown.getLong(icao24, 0L)
        val samePass = previous != 0L && abs(passAt - previous) < SAME_PASS_WINDOW_MS
        shown.edit { putLong(icao24, passAt) }
        // Dismissed by the user: do not bring it back for the same pass.
        if (samePass && !isShowing(id)) return

        val minutes = pass.minutesUntil.roundToInt()
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(passAt))
        // The user's position is only approximate: no point in "66 m".
        val overhead = pass.closestKm < OVERHEAD_KM
        val distance = formatDistance(pass.closestKm)
        val text = when {
            minutes < 1 && overhead -> context.getString(R.string.pass_now_overhead)
            minutes < 1 -> context.getString(R.string.pass_now, distance)
            overhead -> context.getString(R.string.pass_soon_overhead, minutes, time)
            else -> context.getString(R.string.pass_soon, distance, minutes, time)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.pass_title, pass.entry.aircraft.nickname))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            // A refined prediction updates the text without ringing again.
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(passAt - now + EXPIRE_AFTER_PASS_MS)
            .build()
        @Suppress("MissingPermission") // checked by canNotify()
        manager.notify(id, notification)
    }

    /** Removes the alerts of Beluga that were coming but no longer are (they turned away). */
    fun cancelDiverted(keep: Set<String>) {
        val now = System.currentTimeMillis()
        for (aircraft in BELUGA_FLEET) {
            val icao24 = aircraft.icao24
            if (icao24 in keep) continue
            val passAt = shown.getLong(icao24, 0L)
            // A pass already behind us simply expires; only a future one was called off.
            if (passAt > now) {
                manager.cancel(notificationId(icao24))
                shown.edit { remove(icao24) }
            }
        }
    }

    fun cancelAll() {
        BELUGA_FLEET.forEach { manager.cancel(notificationId(it.icao24)) }
        shown.edit { clear() }
    }

    private fun canNotify(): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permitted && manager.areNotificationsEnabled()
    }

    private fun isShowing(id: Int): Boolean = systemManager.activeNotifications.any { it.id == id }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, context.getString(R.string.pass_channel_name), NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.pass_channel_description) }
        // Creating an existing channel again only updates its name and description.
        systemManager.createNotificationChannel(channel)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** ICAO24 addresses are 6 hex digits: they fit in an Int and never collide. */
    private fun notificationId(icao24: String): Int = icao24.toIntOrNull(16) ?: icao24.hashCode()
}
