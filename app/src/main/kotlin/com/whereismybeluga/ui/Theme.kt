package com.whereismybeluga.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class BelugaColors(
    val dark: Boolean,
    /** Floating surfaces over the map (top card, bottom panel, detail card). */
    val surface: Color,
    /** Fleet cards inside the bottom panel. */
    val card: Color,
    val title: Color,
    val text: Color,
    val textMuted: Color,
    /** Accent used to highlight the nearest Beluga. */
    val highlight: Color,
    val highlightSurface: Color,
    val highlightLabel: Color,
    val button: Color,
    val buttonText: Color,
    val error: Color,
    val statusFlying: Color,
    val statusGround: Color,
    /** Beluga silhouettes on the map. */
    val markerFlying: Color,
    val markerParked: Color,
    val markerOutline: Color,
)

private val LightColors = BelugaColors(
    dark = false,
    surface = Color(0xF7FFFFFF),
    card = Color(0xFFF2F6FA),
    title = Color(0xFF1D4E89),
    text = Color(0xFF333333),
    textMuted = Color(0xFF666666),
    highlight = Color(0xFFF1C40F),
    highlightSurface = Color(0xFFFFFBE6),
    highlightLabel = Color(0xFFB7950B),
    button = Color(0xFF1D4E89),
    buttonText = Color.White,
    error = Color(0xFFC0392B),
    statusFlying = Color(0xFF2ECC71),
    statusGround = Color(0xFFE67E22),
    markerFlying = Color(0xFF1D4E89),
    markerParked = Color(0xFF6B86A6),
    markerOutline = Color.White,
)

private val DarkColors = BelugaColors(
    dark = true,
    surface = Color(0xF7181F28),
    card = Color(0xFF243140),
    title = Color(0xFF8AB4F8),
    text = Color(0xFFE3E8EE),
    textMuted = Color(0xFF9AA5B1),
    highlight = Color(0xFFF1C40F),
    highlightSurface = Color(0xFF3A3214),
    highlightLabel = Color(0xFFF4D03F),
    button = Color(0xFF2F6FB5),
    buttonText = Color.White,
    error = Color(0xFFC0392B),
    statusFlying = Color(0xFF2ECC71),
    statusGround = Color(0xFFE67E22),
    markerFlying = Color(0xFF8AB4F8),
    markerParked = Color(0xFF7D8FA3),
    markerOutline = Color(0xFF10161D),
)

val LocalBelugaColors = staticCompositionLocalOf { LightColors }

/** Follows the device's light/dark setting. */
@Composable
fun BelugaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalBelugaColors provides colors) {
        MaterialTheme(
            colorScheme = if (dark) {
                darkColorScheme(primary = colors.title, surface = colors.surface, onSurface = colors.text)
            } else {
                lightColorScheme(primary = colors.title, surface = colors.surface, onSurface = colors.text)
            },
            content = content,
        )
    }
}
