package com.whereismybeluga.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.whereismybeluga.R
import com.whereismybeluga.settings.LanguageChoice
import com.whereismybeluga.settings.ThemeChoice

/** Lets the user pick the theme and the language instead of following the phone. */
@Composable
fun SettingsDialog(
    theme: ThemeChoice,
    language: LanguageChoice,
    onThemeChange: (ThemeChoice) -> Unit,
    onLanguageChange: (LanguageChoice) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalBelugaColors.current
    Dialog(onDismissRequest = onClose) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.settings),
                color = colors.title, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            )
            SettingLabel(stringResource(R.string.settings_theme))
            ChoiceRow(
                choices = ThemeChoice.entries,
                selected = theme,
                label = {
                    stringResource(
                        when (it) {
                            ThemeChoice.SYSTEM -> R.string.settings_follow_phone
                            ThemeChoice.LIGHT -> R.string.settings_theme_light
                            ThemeChoice.DARK -> R.string.settings_theme_dark
                        },
                    )
                },
                onSelect = onThemeChange,
            )
            SettingLabel(stringResource(R.string.settings_language))
            ChoiceRow(
                choices = LanguageChoice.entries,
                selected = language,
                label = {
                    stringResource(
                        when (it) {
                            LanguageChoice.SYSTEM -> R.string.settings_follow_phone
                            LanguageChoice.ENGLISH -> R.string.settings_language_english
                            LanguageChoice.FRENCH -> R.string.settings_language_french
                        },
                    )
                },
                onSelect = onLanguageChange,
            )
            Text(
                stringResource(R.string.close),
                color = colors.title, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(8.dp))
                    .selectable(selected = false, role = Role.Button, onClick = onClose)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text, color = LocalBelugaColors.current.textMuted, fontSize = 12.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** One choice among a few, side by side. */
@Composable
private fun <T> ChoiceRow(choices: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    val colors = LocalBelugaColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        choices.forEach { choice ->
            val isSelected = choice == selected
            Text(
                label(choice),
                color = if (isSelected) colors.buttonText else colors.text,
                fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) colors.button else colors.card)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(choice) })
                    .padding(horizontal = 4.dp, vertical = 12.dp),
            )
        }
    }
}
