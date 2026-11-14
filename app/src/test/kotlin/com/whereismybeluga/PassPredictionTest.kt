package com.whereismybeluga

import com.whereismybeluga.data.BELUGA_FLEET
import com.whereismybeluga.data.Coordinate
import com.whereismybeluga.data.FleetEntry
import com.whereismybeluga.data.LiveState
import com.whereismybeluga.data.predictPass
import com.whereismybeluga.data.predictPasses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassPredictionTest {
    private val user = Coordinate(43.6045, 1.4440)

    /** 200 m/s = 12 km per minute. */
    private fun flying(latitude: Double, longitude: Double, track: Double?, speed: Double? = 200.0, onGround: Boolean = false) =
        FleetEntry(
            BELUGA_FLEET[0],
            LiveState(BELUGA_FLEET[0].icao24, "BGA1", Coordinate(latitude, longitude), 9000.0, onGround, speed, track, 0),
        )

    @Test
    fun planeHeadingStraightAtTheUserPassesOverhead() {
        // 24 km north, flying south at 12 km/min: overhead in 2 minutes.
        val pass = predictPass(flying(user.latitude + 24 / 111.195, user.longitude, track = 180.0), user)!!
        assertEquals(2.0, pass.minutesUntil, 0.05)
        assertEquals(0.0, pass.closestKm, 0.1)
    }

    @Test
    fun parallelTrackGivesTheSideOffset() {
        // 60 km north and 3 km east, flying due south: closest at 3 km in 5 minutes.
        val east = 3 / (111.195 * kotlin.math.cos(Math.toRadians(user.latitude)))
        val pass = predictPass(flying(user.latitude + 60 / 111.195, user.longitude + east, track = 180.0), user)!!
        assertEquals(5.0, pass.minutesUntil, 0.05)
        assertEquals(3.0, pass.closestKm, 0.05)
    }

    @Test
    fun planeFlyingAwayIsClosestNow() {
        val pass = predictPass(flying(user.latitude + 30 / 111.195, user.longitude, track = 0.0), user)!!
        assertEquals(0.0, pass.minutesUntil, 0.0)
        assertEquals(30.0, pass.closestKm, 0.1)
    }

    @Test
    fun passBeyondTheHorizonIsNotAnnounced() {
        // 600 km away: in 20 minutes it is still 360 km off.
        val far = flying(user.latitude + 600 / 111.195, user.longitude, track = 180.0)
        assertEquals(20.0, predictPass(far, user)!!.minutesUntil, 0.0)
        assertTrue(predictPasses(listOf(far), user).isEmpty())
    }

    @Test
    fun onlyPlanesInTheRadiusAreKeptSoonestFirst() {
        val later = flying(user.latitude + 120 / 111.195, user.longitude, track = 180.0)
        val sooner = flying(user.latitude + 24 / 111.195, user.longitude, track = 180.0)
        val wide = flying(user.latitude + 24 / 111.195, user.longitude + 0.2, track = 180.0)
        val passes = predictPasses(listOf(later, wide, sooner), user)
        assertEquals(listOf(sooner, later), passes.map { it.entry })
    }

    @Test
    fun noPredictionWithoutAUsableTrack() {
        assertNull(predictPass(flying(43.7, 1.4, track = null), user))
        assertNull(predictPass(flying(43.7, 1.4, track = 180.0, speed = null), user))
        assertNull(predictPass(flying(43.7, 1.4, track = 180.0, speed = 20.0), user))
        assertNull(predictPass(flying(43.7, 1.4, track = 180.0, onGround = true), user))
    }

    @Test
    fun longitudeDifferenceWrapsAroundTheAntimeridian() {
        val pacific = Coordinate(0.0, 179.99)
        val pass = predictPass(flying(0.0, -179.99, track = 270.0), pacific)!!
        assertEquals(0.0, pass.closestKm, 0.1)
    }
}
