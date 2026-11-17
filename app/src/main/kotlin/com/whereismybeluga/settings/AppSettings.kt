package com.whereismybeluga.settings

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.core.content.edit
import java.util.Locale

private const val PREFS_NAME = "settings"
private const val KEY_THEME = "theme"
private const val KEY_LANGUAGE = "language"

enum class ThemeChoice {
    /** Follows the phone's light/dark setting. */
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        /** Values come from storage: anything unknown falls back to [SYSTEM]. */
        fun fromStored(name: String?): ThemeChoice = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

enum class LanguageChoice(val languageTag: String?) {
    /** Follows the phone's language. */
    SYSTEM(null),
    ENGLISH("en"),
    FRENCH("fr");

    companion object {
        /** Values come from storage: anything unknown falls back to [SYSTEM]. */
        fun fromStored(name: String?): LanguageChoice = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * Theme and language picked in the app's settings, kept on the device. They
 * are applied as a configuration override, so resources, Compose and the
 * system bars all follow without knowing about the choice.
 */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var theme: ThemeChoice
        get() = ThemeChoice.fromStored(prefs.getString(KEY_THEME, null))
        set(value) = prefs.edit { putString(KEY_THEME, value.name) }

    var language: LanguageChoice
        get() = LanguageChoice.fromStored(prefs.getString(KEY_LANGUAGE, null))
        set(value) = prefs.edit { putString(KEY_LANGUAGE, value.name) }

    // Not named `locale`: inside Configuration.apply that would read Configuration.locale instead.
    private val chosenLocale: Locale? get() = language.languageTag?.let(Locale::forLanguageTag)

    /** What to lay over the phone's configuration: only the chosen values, the rest is left alone. */
    fun overrideConfiguration(): Configuration = Configuration().apply {
        chosenLocale?.let(::setLocale)
        when (theme) {
            ThemeChoice.SYSTEM -> Unit
            ThemeChoice.LIGHT -> uiMode = Configuration.UI_MODE_NIGHT_NO
            ThemeChoice.DARK -> uiMode = Configuration.UI_MODE_NIGHT_YES
        }
    }

    /** [base] with the chosen language, for texts built outside the activity (notifications). */
    fun localizedContext(base: Context): Context =
        chosenLocale?.let { base.createConfigurationContext(Configuration().apply { setLocale(it) }) } ?: base

    /** Numbers and times are formatted with the default locale: keep it in step with the texts. */
    fun applyDefaultLocale() {
        Locale.setDefault(chosenLocale ?: Resources.getSystem().configuration.locales[0])
    }
}
