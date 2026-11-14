package com.whereismybeluga.data

data class Coordinate(val latitude: Double, val longitude: Double)

data class HomeBase(val name: String, val position: Coordinate)

data class BelugaAircraft(
    /** ICAO24 / Mode S hex transponder address, lowercase, as used by adsb.lol. */
    val icao24: String,
    val registration: String,
    val nickname: String,
    /** Where the aircraft is shown on the map when it has no live signal. */
    val homeBase: HomeBase,
)

/** A single adsb.lol state vector, trimmed to the fields we use. */
data class LiveState(
    val icao24: String,
    val callsign: String?,
    /** Null when adsb.lol has no usable position for the aircraft. */
    val position: Coordinate?,
    val baroAltitudeMeters: Double?,
    val onGround: Boolean,
    val velocityMetersPerSecond: Double?,
    val trueTrackDegrees: Double?,
    val lastContact: Long,
)

data class FleetEntry(val aircraft: BelugaAircraft, val live: LiveState?) {
    /** True when the aircraft has a live airborne position. */
    val isFlying: Boolean get() = live != null && !live.onGround && live.position != null

    /** Position reported over ADS-B (airborne or on the ground), if any. */
    val livePosition: Coordinate? get() = live?.position

    /** Where the aircraft is drawn on the map: live position if known, home base otherwise. */
    val displayedPosition: Coordinate get() = livePosition ?: aircraft.homeBase.position
}
