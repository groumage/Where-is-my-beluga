package com.whereismybeluga

import com.whereismybeluga.settings.LanguageChoice
import com.whereismybeluga.settings.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {
    @Test
    fun storedChoicesAreReadBack() {
        ThemeChoice.entries.forEach { assertEquals(it, ThemeChoice.fromStored(it.name)) }
        LanguageChoice.entries.forEach { assertEquals(it, LanguageChoice.fromStored(it.name)) }
    }

    @Test
    fun unknownStoredChoiceFollowsThePhone() {
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.fromStored(null))
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.fromStored("PURPLE"))
        assertEquals(LanguageChoice.SYSTEM, LanguageChoice.fromStored(null))
        assertEquals(LanguageChoice.SYSTEM, LanguageChoice.fromStored("../etc"))
    }

    @Test
    fun onlyAppLanguagesAreOffered() {
        assertEquals(listOf(null, "en", "fr"), LanguageChoice.entries.map { it.languageTag })
    }
}
