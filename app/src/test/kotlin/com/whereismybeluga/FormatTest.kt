package com.whereismybeluga

import com.whereismybeluga.ui.formatPassDistance
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormatTest {
    @Test
    fun passDistanceIsRoundedToWholeKilometres() {
        assertEquals("2 km", formatPassDistance(2.2, Locale.FRENCH))
        assertEquals("3 km", formatPassDistance(2.5, Locale.FRENCH))
        assertEquals("12 km", formatPassDistance(12.4, Locale.ENGLISH))
        assertEquals("25 km", formatPassDistance(24.9, Locale.ENGLISH))
    }

    @Test
    fun passDistanceNeverShowsZero() {
        // Under 1 km the notification says "right over you"; just above, keep "1 km".
        assertEquals("1 km", formatPassDistance(1.0, Locale.FRENCH))
        assertEquals("1 km", formatPassDistance(0.6, Locale.FRENCH))
    }
}
