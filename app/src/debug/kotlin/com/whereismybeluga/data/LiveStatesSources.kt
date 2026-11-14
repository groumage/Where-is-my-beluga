package com.whereismybeluga.data

import android.content.Context
import com.whereismybeluga.BuildConfig

/**
 * Debug builds read canned adsb.lol answers by default, so testing spends none
 * of the daily adsb.lol quota. Pick a scenario at build time:
 *
 *     ./gradlew :app:installDebug -PfakeAdsbLol=ground
 *
 * `off` uses the real adsb.lol API; see [FakeAdsbLol] for the other scenarios.
 */
fun liveStatesSource(context: Context): LiveStatesSource =
    when (val scenario = BuildConfig.FAKE_ADSBLOL) {
        "off" -> AdsbLolClient
        else -> FakeAdsbLol(context.applicationContext.assets, scenario)
    }
