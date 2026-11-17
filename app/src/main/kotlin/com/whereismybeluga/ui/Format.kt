package com.whereismybeluga.ui

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

fun formatDistance(km: Double, locale: Locale = Locale.getDefault()): String = when {
    km < 1 -> "${(km * 1000).roundToInt()} m"
    km < 10 -> NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(km) + " km"
    else -> NumberFormat.getIntegerInstance(locale).format(km.roundToInt()) + " km"
}

/**
 * Distance of a predicted pass, in whole kilometres (at least 1): the user's
 * position is only approximate and the prediction assumes a straight line, so
 * a decimal would be false precision.
 */
fun formatPassDistance(km: Double, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getIntegerInstance(locale).format(km.roundToInt().coerceAtLeast(1)) + " km"

fun formatAltitude(meters: Double?, locale: Locale = Locale.getDefault()): String =
    meters?.let { NumberFormat.getIntegerInstance(locale).format(it.roundToInt()) + " m" } ?: "—"

fun formatSpeed(metersPerSecond: Double?): String =
    metersPerSecond?.let { "${(it * 3.6).roundToInt()} km/h" } ?: "—"

fun formatHeading(degrees: Double?): String = degrees?.let { "${it.roundToInt()}°" } ?: "—"
