package com.whereismybeluga.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

enum class AdsbLolErrorKind { RATE_LIMITED, TIMEOUT, NETWORK, SERVER, INVALID_RESPONSE }

/** Failure with a known cause, so the UI can show a translated message instead of raw details. */
class AdsbLolException(
    val kind: AdsbLolErrorKind,
    message: String,
    /** For [AdsbLolErrorKind.RATE_LIMITED]: how long adsb.lol asks to wait, if it said so. */
    val retryAfterSeconds: Long? = null,
) : Exception(message)

/** Where live aircraft states come from: adsb.lol, or canned data in debug builds. */
fun interface LiveStatesSource {
    suspend fun fetchLiveStates(icao24List: List<String>): Map<String, LiveState>
}

/** Longest wait we accept from adsb.lol; anything above is treated as missing. */
private const val MAX_RETRY_AFTER_SECONDS = 2 * 24 * 60 * 60L

/** A response for a handful of aircraft is a few KB; anything far bigger is not legitimate. */
private const val MAX_RESPONSE_CHARS = 256_000

private const val FEET_TO_METERS = 0.3048
private const val KNOTS_TO_METERS_PER_SECOND = 0.514444

/** A position older than this is no longer shown as live. */
private const val MAX_POSITION_AGE_SECONDS = 300.0

/**
 * Parses an adsb.lol `/v2/hex` response (readsb JSON). The response is treated
 * as untrusted: every field is type- and range-checked, and only the aircraft
 * that were asked for are kept.
 */
object AdsbLolParser {
    /** Callsigns are up to 8 letters/digits; drop anything else rather than display it. */
    private val CALLSIGN_PATTERN = Regex("^[A-Z0-9]{1,8}$")

    fun parse(body: String, wanted: Set<String>): Map<String, LiveState> {
        if (body.length > MAX_RESPONSE_CHARS) invalid("adsb.lol response too large")
        val root = try {
            JSONObject(body)
        } catch (e: JSONException) {
            invalid("adsb.lol response is not JSON")
        }
        if (root.isNull("ac")) return emptyMap()
        val aircraft = root.opt("ac") as? JSONArray ?: invalid("adsb.lol response has an unexpected shape")
        // `now` is in milliseconds.
        val nowSeconds = number(root, "now", 0.0, 1e14)?.div(1_000) ?: 0.0

        val result = LinkedHashMap<String, LiveState>()
        for (i in 0 until aircraft.length()) {
            val state = parseState(aircraft.opt(i) as? JSONObject ?: continue, wanted, nowSeconds) ?: continue
            result[state.icao24] = state
        }
        return result
    }

    private fun parseState(state: JSONObject, wanted: Set<String>, nowSeconds: Double): LiveState? {
        val icao24 = (state.opt("hex") as? String)?.lowercase() ?: return null
        if (icao24 !in wanted) return null

        val latitude = number(state, "lat", -90.0, 90.0)
        val longitude = number(state, "lon", -180.0, 180.0)
        val positionAge = number(state, "seen_pos", 0.0, 1e9)
        val fresh = positionAge != null && positionAge <= MAX_POSITION_AGE_SECONDS

        return LiveState(
            icao24 = icao24,
            callsign = (state.opt("flight") as? String)?.trim()?.uppercase()
                ?.takeIf { CALLSIGN_PATTERN.matches(it) },
            // A position is only usable as a recent pair.
            position = if (latitude != null && longitude != null && fresh) Coordinate(latitude, longitude) else null,
            // `alt_baro` is a number of feet, or the string "ground".
            baroAltitudeMeters = number(state, "alt_baro", -3_300.0, 100_000.0)?.times(FEET_TO_METERS),
            onGround = state.opt("alt_baro") == "ground",
            velocityMetersPerSecond = number(state, "gs", 0.0, 2_000.0)?.times(KNOTS_TO_METERS_PER_SECOND),
            trueTrackDegrees = number(state, "track", 0.0, 360.0) ?: number(state, "true_heading", 0.0, 360.0),
            lastContact = (nowSeconds - (number(state, "seen", 0.0, 1e9) ?: 0.0)).toLong(),
        )
    }

    /** Reads the `Retry-After` header, in seconds; null if absent or not a sane number. */
    fun parseRetryAfterSeconds(header: String?): Long? =
        header?.trim()?.toLongOrNull()?.takeIf { it in 0..MAX_RETRY_AFTER_SECONDS }

    private fun number(json: JSONObject, key: String, min: Double, max: Double): Double? {
        val value = (json.opt(key) as? Number)?.toDouble() ?: return null
        return value.takeIf { it.isFinite() && it in min..max }
    }

    private fun invalid(message: String): Nothing =
        throw AdsbLolException(AdsbLolErrorKind.INVALID_RESPONSE, message)
}

object AdsbLolClient : LiveStatesSource {
    private const val HEX_URL = "https://api.adsb.lol/v2/hex/"

    /** Give up on a request after this long, so a stalled connection can't block polling. */
    private const val TIMEOUT_MS = 15_000

    /**
     * Fetches the current state vectors for the given ICAO24 addresses.
     * Aircraft with no current ADS-B signal (parked, powered off) are simply
     * absent from the result.
     */
    override suspend fun fetchLiveStates(icao24List: List<String>): Map<String, LiveState> =
        withContext(Dispatchers.IO) {
            val hexList = icao24List.joinToString(",") { URLEncoder.encode(it, "UTF-8") }
            val body = try {
                val connection = URL(HEX_URL + hexList).openConnection() as HttpsURLConnection
                try {
                    connection.connectTimeout = TIMEOUT_MS
                    connection.readTimeout = TIMEOUT_MS
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("Accept", "application/json")
                    when (val status = connection.responseCode) {
                        in 200..299 -> readLimited(connection)
                        429 -> throw AdsbLolException(
                            AdsbLolErrorKind.RATE_LIMITED,
                            "adsb.lol rate limit reached",
                            AdsbLolParser.parseRetryAfterSeconds(
                                connection.getHeaderField("Retry-After"),
                            ),
                        )
                        else -> throw AdsbLolException(AdsbLolErrorKind.SERVER, "adsb.lol request failed: $status")
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: SocketTimeoutException) {
                throw AdsbLolException(AdsbLolErrorKind.TIMEOUT, "adsb.lol request timed out")
            } catch (e: IOException) {
                throw AdsbLolException(AdsbLolErrorKind.NETWORK, "adsb.lol unreachable")
            }
            AdsbLolParser.parse(body, icao24List.toSet())
        }

    /** Reads the body, stopping early instead of buffering an oversized response. */
    private fun readLimited(connection: HttpsURLConnection): String {
        val buffer = CharArray(8_192)
        val body = StringBuilder()
        connection.inputStream.reader(Charsets.UTF_8).use { reader ->
            while (true) {
                val read = reader.read(buffer)
                if (read < 0) break
                body.append(buffer, 0, read)
                if (body.length > MAX_RESPONSE_CHARS) {
                    throw AdsbLolException(AdsbLolErrorKind.INVALID_RESPONSE, "adsb.lol response too large")
                }
            }
        }
        return body.toString()
    }
}
