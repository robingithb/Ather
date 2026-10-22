package com.aether.hmi.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.hmi.R
import com.aether.hmi.ui.components.AetherGlyph
import com.aether.hmi.ui.components.Glyph
import com.aether.hmi.ui.theme.AetherColors

/** Design-only HMI indicators. Selection never sends vehicle or connectivity commands. */
@Composable
fun StatusChips(state: DashboardState, highlighted: Int, onToggle: (Int) -> Unit, modifier: Modifier = Modifier) {
    val labels = listOf(stringResource(R.string.online), stringResource(R.string.hmi_gps_active),
        stringResource(R.string.hmi_5g), state.snapshot.vehicle.batteryPercent?.let {
            stringResource(R.string.battery_percent, it)
        } ?: stringResource(R.string.unavailable_value), stringResource(R.string.charging),
        stringResource(R.string.eco_mode), stringResource(R.string.parked))
    val glyphs = listOf(null, Glyph.Gps, Glyph.SignalBars, Glyph.Battery, Glyph.Charge, Glyph.Eco, Glyph.Park)
    BoxWithConstraints(modifier.fillMaxWidth().height(38.dp)) {
        val compact = maxWidth < 650.dp
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
            labels.forEachIndexed { index, label ->
                val selected = highlighted and (1 shl index) != 0
                val shape = RoundedCornerShape(8.dp)
                val description = stringResource(R.string.hmi_indicator_description, label)
                val color = if (index == 0 || index == 5) AetherColors.Good else AetherColors.Accent
                Box(Modifier.weight(1f).fillMaxHeight().clip(shape)
                    .background(Brush.verticalGradient(listOf(
                        if (selected) AetherColors.Accent.copy(alpha = 0.28f) else AetherColors.Panel,
                        AetherColors.Background)))
                    .border(if (selected) 1.2.dp else 0.6.dp,
                        if (selected) AetherColors.Accent else AetherColors.Border, shape)
                    .toggleable(selected, role = Role.Checkbox, onValueChange = { onToggle(index) })
                    .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
                    val icon: @Composable () -> Unit = {
                        val glyph = glyphs[index]
                        if (glyph == null) Box(Modifier.size(if (compact) 13.dp else 16.dp).background(color, CircleShape))
                        else AetherGlyph(glyph, size = if (compact) 17.dp else 21.dp, color = color)
                    }
                    val text: @Composable () -> Unit = {
                        Text(label, color = AetherColors.TextPrimary, fontSize = if (compact) 9.sp else 10.sp,
                            lineHeight = 11.sp, letterSpacing = 0.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 9.dp)) { icon(); text() }
                }
            }
        }
    }
}
