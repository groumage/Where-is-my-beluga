package com.whereismybeluga

import com.whereismybeluga.data.BELUGA_FLEET
import com.whereismybeluga.data.Coordinate
import com.whereismybeluga.data.FleetEntry
import com.whereismybeluga.data.predictPasses
import com.whereismybeluga.data.AdsbLolErrorKind
import com.whereismybeluga.data.AdsbLolException
import com.whereismybeluga.data.AdsbLolParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdsbLolParserTest {
    private val wanted = setOf("395d66", "395d67")

    @Test
    fun parsesValidState() {
        val body = """{"now":1700000000000,"ac":[
            {"hex":"395D66","flight":"BGA114B ","alt_baro":30000,"gs":100,"track":90,
             "lat":43.63,"lon":1.36,"seen_pos":1.5,"seen":2}]}"""
        val state = AdsbLolParser.parse(body, wanted).getValue("395d66")
        assertEquals("BGA114B", state.callsign)
        assertEquals(Coordinate(43.63, 1.36), state.position)
        assertEquals(9144.0, state.baroAltitudeMeters!!, 0.1)
        assertEquals(51.44, state.velocityMetersPerSecond!!, 0.01)
        assertEquals(90.0, state.trueTrackDegrees!!, 0.0)
        assertEquals(1699999998L, state.lastContact)
        assertFalse(state.onGround)
    }

    @Test
    fun groundAircraftUsesHeadingAndHasNoAltitude() {
        val body = """{"ac":[{"hex":"395d66","alt_baro":"ground","true_heading":140.6,
            "lat":53.5,"lon":9.8,"seen_pos":2}]}"""
        val state = AdsbLolParser.parse(body, wanted).getValue("395d66")
        assertTrue(state.onGround)
        assertNull(state.baroAltitudeMeters)
        assertEquals(140.6, state.trueTrackDegrees!!, 0.0)
    }

    @Test
    fun dropsHostileFieldsStalePositionsAndUnrequestedAircraft() {
        val body = """{"ac":[
            {"hex":"395d67","flight":"<script>","alt_baro":"x","gs":-5,"track":720,"lat":999,"lon":1,"seen_pos":1},
            {"hex":"395d66","lat":43.6,"lon":1.3,"seen_pos":900},
            {"hex":"ffffff","flight":"EVIL","lat":1,"lon":1,"seen_pos":1},
            "garbage", null, {"hex":42}]}"""
        val states = AdsbLolParser.parse(body, wanted)
        assertEquals(setOf("395d67", "395d66"), states.keys)
        val state = states.getValue("395d67")
        assertNull(state.callsign)
        assertNull(state.position)
        assertNull(state.baroAltitudeMeters)
        assertNull(state.velocityMetersPerSecond)
        assertNull(state.trueTrackDegrees)
        assertFalse(state.onGround)
        assertNull(states.getValue("395d66").position)
    }

    @Test
    fun noAircraftMeansNoLiveStates() {
        assertTrue(AdsbLolParser.parse("""{"ac":[],"total":0}""", wanted).isEmpty())
        assertTrue(AdsbLolParser.parse("""{"msg":"No error"}""", wanted).isEmpty())
    }

    @Test
    fun rejectsMalformedResponses() {
        for (body in listOf("<html>", """{"ac":"nope"}""", "x".repeat(300_000))) {
            val error = assertThrows(AdsbLolException::class.java) { AdsbLolParser.parse(body, wanted) }
            assertEquals(AdsbLolErrorKind.INVALID_RESPONSE, error.kind)
        }
    }

    @Test
    fun readsRetryAfterHeaderOnlyWhenSane() {
        assertEquals(120L, AdsbLolParser.parseRetryAfterSeconds(" 120 "))
        assertEquals(0L, AdsbLolParser.parseRetryAfterSeconds("0"))
        for (header in listOf(null, "", "soon", "-5", "1.5", "999999999999")) {
            assertNull(header, AdsbLolParser.parseRetryAfterSeconds(header))
        }
    }

    @Test
    fun debugFixturesAreValidResponses() {
        val fleet = BELUGA_FLEET.map { it.icao24 }.toSet()
        val dir = File("src/debug/assets/fake-adsblol")
        val flying = AdsbLolParser.parse(File(dir, "flying.json").readText(), fleet)
        assertEquals(3, flying.values.count { !it.onGround && it.position != null })
        assertTrue(AdsbLolParser.parse(File(dir, "ground.json").readText(), fleet).isEmpty())
        // Two Beluga heading for Toulouse city centre: one overhead in ~2 min, one 2 km off in ~8 min.
        val approaching = AdsbLolParser.parse(File(dir, "approaching.json").readText(), fleet)
        val passes = predictPasses(BELUGA_FLEET.map { FleetEntry(it, approaching[it.icao24]) }, Coordinate(43.6045, 1.4440))
        assertEquals(listOf("395d68", "395d6d"), passes.map { it.entry.aircraft.icao24 })
        val error = assertThrows(AdsbLolException::class.java) {
            AdsbLolParser.parse(File(dir, "invalid.json").readText(), fleet)
        }
        assertEquals(AdsbLolErrorKind.INVALID_RESPONSE, error.kind)
    }
}
