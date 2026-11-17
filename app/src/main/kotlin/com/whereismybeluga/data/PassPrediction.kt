package com.whereismybeluga.data

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A pass alert fires when a Beluga is predicted to come within the radius the
 * user picked among these (horizontal distance, in km).
 */
val PASS_RADIUS_CHOICES_KM = listOf(5, 10, 15, 20, 25)

const val DEFAULT_PASS_RADIUS_KM = 5

/** A radius read from storage: anything but one of the choices falls back to the default. */
fun passRadiusOrDefault(storedKm: Int): Int =
    if (storedKm in PASS_RADIUS_CHOICES_KM) storedKm else DEFAULT_PASS_RADIUS_KM

/**
 * How far ahead passes are predicted: a little more than the 15-minute
 * background check period, so a pass between two checks is still announced.
 */
const val PASS_HORIZON_MINUTES = 20.0

/** Below this ground speed a plane is taxiing: its track says nothing about where it goes. */
private const val MIN_FLYING_SPEED_MPS = 40.0

private const val KM_PER_DEGREE = 111.195

/** A Beluga expected to come within [closestKm] of the user in [minutesUntil] minutes. */
data class PredictedPass(val entry: FleetEntry, val minutesUntil: Double, val closestKm: Double)

/**
 * Closest point of approach of an aircraft keeping its current track and
 * speed, within the next [horizonMinutes]; null if it is not flying.
 *
 * Uses a flat projection centred on the user. Over the ~250 km a Beluga covers
 * in 20 minutes the error stays small next to the effect of any turn, and the
 * prediction is checked again just before the pass anyway.
 */
fun predictPass(entry: FleetEntry, from: Coordinate, horizonMinutes: Double = PASS_HORIZON_MINUTES): PredictedPass? {
    val live = entry.live ?: return null
    if (!entry.isFlying) return null
    val position = live.position ?: return null
    val speed = live.velocityMetersPerSecond ?: return null
    val track = live.trueTrackDegrees ?: return null
    if (speed < MIN_FLYING_SPEED_MPS) return null

    // Aircraft position relative to the user, in km (x east, y north).
    val deltaLongitude = ((position.longitude - from.longitude + 540.0) % 360.0) - 180.0
    val x = deltaLongitude * KM_PER_DEGREE * cos(Math.toRadians(from.latitude))
    val y = (position.latitude - from.latitude) * KM_PER_DEGREE

    // Velocity in km per minute; the track is clockwise from north.
    val kmPerMinute = speed * 60.0 / 1_000.0
    val vx = kmPerMinute * sin(Math.toRadians(track))
    val vy = kmPerMinute * cos(Math.toRadians(track))

    // Time minimising |position + velocity × t|, kept within [0, horizon].
    val minutes = (-(x * vx + y * vy) / (vx * vx + vy * vy)).coerceIn(0.0, horizonMinutes)
    return PredictedPass(entry, minutes, hypot(x + vx * minutes, y + vy * minutes))
}

/** Every Beluga predicted to pass within [radiusKm] of [from], soonest first. */
fun predictPasses(
    entries: List<FleetEntry>,
    from: Coordinate,
    radiusKm: Double = DEFAULT_PASS_RADIUS_KM.toDouble(),
    horizonMinutes: Double = PASS_HORIZON_MINUTES,
): List<PredictedPass> =
    entries
        .mapNotNull { predictPass(it, from, horizonMinutes) }
        .filter { it.closestKm <= radiusKm }
        .sortedBy { it.minutesUntil }
