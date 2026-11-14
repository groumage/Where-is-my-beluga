package com.whereismybeluga.location

import android.content.Context
import androidx.core.content.edit
import com.whereismybeluga.data.Coordinate
import com.whereismybeluga.data.coordinateOrNull

private const val PREFS_NAME = "location"

/**
 * Positions kept on the device only, shared by the screen and the background
 * pass check:
 * - the position set by hand with a long press on the map;
 * - the last position the device found while the app was open. With only the
 *   foreground location permission, the background check cannot ask for a
 *   fresh one, so it uses this.
 */
class LocationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var manual: Coordinate?
        get() = read(KEY_MANUAL_LAT, KEY_MANUAL_LON)
        set(value) = write(KEY_MANUAL_LAT, KEY_MANUAL_LON, value)

    var lastDevice: Coordinate?
        get() = read(KEY_DEVICE_LAT, KEY_DEVICE_LON)
        set(value) = write(KEY_DEVICE_LAT, KEY_DEVICE_LON, value)

    /** Where the user is, as the app shows it: the manual position wins. */
    val best: Coordinate? get() = manual ?: lastDevice

    private fun read(latKey: String, lonKey: String): Coordinate? {
        if (!prefs.contains(latKey) || !prefs.contains(lonKey)) return null
        // Values come from storage: validate them like any other input.
        return coordinateOrNull(
            Double.fromBits(prefs.getLong(latKey, 0)),
            Double.fromBits(prefs.getLong(lonKey, 0)),
        )
    }

    private fun write(latKey: String, lonKey: String, value: Coordinate?) {
        prefs.edit {
            if (value == null) {
                remove(latKey)
                remove(lonKey)
            } else {
                putLong(latKey, value.latitude.toRawBits())
                putLong(lonKey, value.longitude.toRawBits())
            }
        }
    }

    private companion object {
        // The manual keys predate this class: keep them so saved positions survive the update.
        const val KEY_MANUAL_LAT = "manual_latitude"
        const val KEY_MANUAL_LON = "manual_longitude"
        const val KEY_DEVICE_LAT = "device_latitude"
        const val KEY_DEVICE_LON = "device_longitude"
    }
}
