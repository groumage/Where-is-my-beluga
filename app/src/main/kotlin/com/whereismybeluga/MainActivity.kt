package com.whereismybeluga

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.whereismybeluga.settings.AppSettings
import com.whereismybeluga.ui.BelugaTheme
import com.whereismybeluga.ui.MapScreen

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        // Theme and language picked in the app win over the phone's.
        val settings = AppSettings(newBase)
        settings.applyDefaultLocale()
        applyOverrideConfiguration(settings.overrideConfiguration())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BelugaTheme {
                MapScreen()
            }
        }
    }
}
