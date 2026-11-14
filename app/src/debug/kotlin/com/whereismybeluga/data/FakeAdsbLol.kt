package com.whereismybeluga.data

import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Latency of a real request, so loading states show up on screen. */
private const val FAKE_LATENCY_MS = 600L

/**
 * Canned adsb.lol answers for debug builds. Scenarios:
 * - a file name in `assets/fake-adsblol/` (`flying`, `ground`, `approaching`, `invalid`):
 *   its content goes through [AdsbLolParser], exactly like a real response;
 * - `rate-limited`: adsb.lol refuses the request for quota, asking to wait 2 minutes;
 * - `offline`: no network.
 */
class FakeAdsbLol(private val assets: AssetManager, private val scenario: String) : LiveStatesSource {
    init {
        // The name becomes part of an asset path: keep it to a plain file name.
        require(SCENARIO_PATTERN.matches(scenario)) { "Invalid fake adsb.lol scenario: $scenario" }
    }

    override suspend fun fetchLiveStates(icao24List: List<String>): Map<String, LiveState> =
        withContext(Dispatchers.IO) {
            delay(FAKE_LATENCY_MS)
            when (scenario) {
                "rate-limited" -> throw AdsbLolException(
                    AdsbLolErrorKind.RATE_LIMITED, "Fake rate limit", retryAfterSeconds = 120,
                )
                "offline" -> throw AdsbLolException(AdsbLolErrorKind.NETWORK, "Fake network failure")
                else -> {
                    val body = assets.open("fake-adsblol/$scenario.json").use { it.reader().readText() }
                    AdsbLolParser.parse(body, icao24List.toSet())
                }
            }
        }

    private companion object {
        val SCENARIO_PATTERN = Regex("^[a-z][a-z-]*$")
    }
}
