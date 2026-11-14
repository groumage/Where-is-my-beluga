package com.whereismybeluga.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whereismybeluga.R
import com.whereismybeluga.data.FleetEntry
import java.text.DateFormat
import java.util.Date

/** Bottom panel listing the whole fleet, plus an "All" card that frames it on the map. */
@Composable
fun FleetPanel(
    state: FleetUiState,
    nearestIcao24: String?,
    onSelect: (FleetEntry) -> Unit,
    onSelectAll: () -> Unit,
    onToggleAlerts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBelugaColors.current
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(8.dp, shape)
            .clip(shape)
            .background(colors.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(top = 10.dp, bottom = 8.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BelugaIcon(width = 30.dp, color = colors.markerFlying, faceColor = colors.surface)
                Text(
                    stringResource(R.string.fleet_title),
                    color = colors.title, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                AlertsButton(enabled = state.alertsEnabled, onClick = onToggleAlerts)
            }
            val updatedAt = state.lastUpdatedMillis
                ?.let { DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(it)) }
            Text(
                if (updatedAt != null) {
                    stringResource(R.string.fleet_summary_updated, state.flyingCount, updatedAt)
                } else {
                    stringResource(R.string.fleet_summary, state.flyingCount)
                },
                color = colors.textMuted, fontSize = 12.sp,
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "all") {
                FleetCard(selected = state.selection == Selection.All, nearest = false, onClick = onSelectAll) {
                    CardTitle(stringResource(R.string.all_title))
                    CardSubtitle(stringResource(R.string.all_subtitle))
                }
            }
            items(state.entries, key = { it.aircraft.icao24 }) { entry ->
                val selected = (state.selection as? Selection.Aircraft)?.icao24 == entry.aircraft.icao24
                FleetCard(selected, nearest = entry.aircraft.icao24 == nearestIcao24, onClick = { onSelect(entry) }) {
                    CardTitle(entry.aircraft.nickname)
                    CardSubtitle(entry.aircraft.registration)
                    Box(
                        Modifier
                            .padding(top = 6.dp, bottom = 4.dp)
                            .size(8.dp)
                            .background(if (entry.isFlying) colors.statusFlying else colors.statusGround, CircleShape),
                    )
                    Text(
                        if (entry.isFlying) {
                            stringResource(R.string.flight, entry.live?.callsign ?: "—")
                        } else {
                            stringResource(R.string.on_ground)
                        },
                        color = colors.text, fontSize = 11.sp,
                    )
                }
            }
        }

        Text(
            stringResource(R.string.data_source),
            color = colors.textMuted, fontSize = 10.sp, textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
    }
}

/** Bell that turns the pass alerts on and off. */
@Composable
private fun AlertsButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalBelugaColors.current
    val label = stringResource(if (enabled) R.string.alerts_disable else R.string.alerts_enable)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (enabled) colors.highlight else colors.card)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Text(if (enabled) "🔔" else "🔕", fontSize = 18.sp)
    }
}

@Composable
private fun FleetCard(selected: Boolean, nearest: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val colors = LocalBelugaColors.current
    val shape = RoundedCornerShape(12.dp)
    val borderColor = when {
        selected -> colors.title
        nearest -> colors.highlight
        else -> Color.Transparent
    }
    Column(
        Modifier
            .width(120.dp)
            .clip(shape)
            .background(if (nearest) colors.highlightSurface else colors.card)
            .border(2.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) { content() }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, color = LocalBelugaColors.current.title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun CardSubtitle(text: String) {
    Text(text, color = LocalBelugaColors.current.textMuted, fontSize = 12.sp)
}
