package com.whereismybeluga

import com.whereismybeluga.data.BELUGA_FLEET
import com.whereismybeluga.data.Coordinate
import com.whereismybeluga.data.FleetEntry
import com.whereismybeluga.data.LiveState
import com.whereismybeluga.data.coordinateOrNull
import com.whereismybeluga.data.distanceKm
import com.whereismybeluga.data.findNearestInFlight
import com.whereismybeluga.data.splitFleet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeoTest {
    private val paris = Coordinate(48.8566, 2.3522)
    private val toulouse = Coordinate(43.6294, 1.3638)
    private val hamburg = Coordinate(53.5353, 9.8353)

    private fun entry(index: Int, position: Coordinate?, onGround: Boolean = false) = FleetEntry(
        BELUGA_FLEET[index],
        LiveState(BELUGA_FLEET[index].icao24, "BGA1", position, 9000.0, onGround, 200.0, 90.0, 0),
    )

    @Test
    fun haversineDistance() {
        assertEquals(588.0, distanceKm(paris, toulouse), 5.0)
    }

    @Test
    fun nearestIgnoresAircraftOnTheGround() {
        val entries = listOf(
            entry(0, paris, onGround = true), // closest, but not flying
            entry(1, hamburg),
            entry(2, toulouse),
            FleetEntry(BELUGA_FLEET[3], null),
        )
        val nearest = findNearestInFlight(entries, paris)!!
        assertEquals(BELUGA_FLEET[2].icao24, nearest.entry.aircraft.icao24)
    }

    @Test
    fun noNearestWhenNothingFlies() {
        val entries = listOf(entry(0, paris, onGround = true), FleetEntry(BELUGA_FLEET[1], null))
        assertNull(findNearestInFlight(entries, paris))
    }

    @Test
    fun silentAircraftAreGroupedByHomeBase() {
        val entries = listOf(entry(0, hamburg)) + BELUGA_FLEET.drop(1).map { FleetEntry(it, null) }
        val (tracked, parked) = splitFleet(entries)
        assertEquals(1, tracked.size)
        assertEquals(1, parked.size)
        assertEquals(5, parked.single().entries.size)
    }

    @Test
    fun coordinateOrNullKeepsValidPositions() {
        assertEquals(Coordinate(43.6, 1.44), coordinateOrNull(43.6, 1.44))
        assertEquals(Coordinate(-90.0, 180.0), coordinateOrNull(-90.0, 180.0))
    }

    @Test
    fun coordinateOrNullRejectsOutOfRangeOrNotANumber() {
        assertNull(coordinateOrNull(90.1, 0.0))
        assertNull(coordinateOrNull(0.0, -180.1))
        assertNull(coordinateOrNull(Double.NaN, 0.0))
        assertNull(coordinateOrNull(0.0, Double.POSITIVE_INFINITY))
    }
}
