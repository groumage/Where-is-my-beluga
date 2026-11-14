package com.whereismybeluga.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_KM = 6371.0

/** Great-circle distance between two points (haversine), in kilometers. */
fun distanceKm(a: Coordinate, b: Coordinate): Double {
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_KM * asin(sqrt(h))
}

/** A coordinate from untrusted storage, or null if out of range or not a number. */
fun coordinateOrNull(latitude: Double, longitude: Double): Coordinate? =
    if (latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0) {
        Coordinate(latitude, longitude)
    } else {
        null
    }

data class NearestBeluga(val entry: FleetEntry, val distanceKm: Double)

/** Closest Beluga currently in flight, or null when the whole fleet is on the ground. */
fun findNearestInFlight(entries: List<FleetEntry>, from: Coordinate): NearestBeluga? =
    entries
        .filter { it.isFlying }
        .map { NearestBeluga(it, distanceKm(from, it.displayedPosition)) }
        .minByOrNull { it.distanceKm }

/** Aircraft with no signal, drawn as a single marker on their shared home base. */
data class ParkedGroup(val base: HomeBase, val entries: List<FleetEntry>) {
    val key: String get() = baseKey(base)
}

private fun baseKey(base: HomeBase) = "base:${base.name}"

/** Identifies the map marker an aircraft is drawn in (its own, or its parked group's). */
fun markerKeyFor(entry: FleetEntry): String =
    if (entry.livePosition != null) entry.aircraft.icao24 else baseKey(entry.aircraft.homeBase)

/**
 * Splits the fleet into aircraft with a live position (one marker each) and
 * aircraft without signal, grouped by home base so they don't stack up as
 * identical markers on the same spot.
 */
fun splitFleet(entries: List<FleetEntry>): Pair<List<FleetEntry>, List<ParkedGroup>> {
    val (tracked, silent) = entries.partition { it.livePosition != null }
    val parked = silent.groupBy { it.aircraft.homeBase }.map { (base, group) -> ParkedGroup(base, group) }
    return tracked to parked
}
