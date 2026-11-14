package com.whereismybeluga.data

import android.content.Context

/** Release builds always talk to adsb.lol; the fake data only exists in debug builds. */
@Suppress("UNUSED_PARAMETER")
fun liveStatesSource(context: Context): LiveStatesSource = AdsbLolClient
