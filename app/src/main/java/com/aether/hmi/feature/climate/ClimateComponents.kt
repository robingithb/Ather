package com.aether.hmi.feature.climate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.hmi.R
import com.aether.hmi.domain.ClimateState
import com.aether.hmi.ui.components.AetherGlyph
import com.aether.hmi.ui.components.Glyph
import com.aether.hmi.ui.theme.AetherColors
import com.aether.hmi.ui.theme.LocalAetherPalette
import java.text.NumberFormat
import java.util.Locale

// Temperature cues retain their hot/cold meaning in every theme, with darker
// tones on light surfaces so the seat icons and thin rails stay readable.
internal val ClimateWarm: Color
    @Composable get() = if (AetherColors.Panel.luminance() > .5f) Color(0xFFCA3C30) else Color(0xFFFF6255)
internal val ClimateCool: Color
    @Composable get() = if (AetherColors.Panel.luminance() > .5f) Color(0xFF0072A8) else Color(0xFF00B8FF)

@Composable
internal fun ClimateSurface(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalAetherPalette.current
    val appearance = remember(palette) { climateAppearance(palette) }
    val glowAlpha = .19f * palette.DecorativeGlowScale
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.clip(shape)
        .background(Brush.linearGradient(listOf(appearance.surfaceTop, appearance.surfaceBottom)))
        .then(if (selected) Modifier.background(Brush.radialGradient(listOf(AetherColors.Accent.copy(alpha = glowAlpha), Color.Transparent))) else Modifier)
        .border(if (selected) 1.1.dp else .65.dp, if (selected) AetherColors.Accent else AetherColors.Border, shape), content = content)
}

@Composable
internal fun ClimateStepButton(
    plus: Boolean,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 48.dp,
    accentColor: Color? = null,
) {
    Box(modifier.size(diameter).clip(CircleShape)
        .background(Brush.verticalGradient(listOf(
            if (enabled && accentColor != null) accentColor.copy(alpha = .16f) else AetherColors.PanelRaised,
            AetherColors.PanelDeep)))
        .border(.8.dp, if (enabled && accentColor != null) accentColor.copy(alpha = .40f) else AetherColors.Border, CircleShape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        AetherGlyph(if (plus) Glyph.Plus else Glyph.Minus, size = diameter * .43f,
            color = if (enabled) accentColor ?: AetherColors.TextPrimary else AetherColors.Muted.copy(alpha = .4f))
    }
}

@Composable
internal fun climateTemperature(value: Float?): String {
    val locale = Locale.getDefault()
    val format = remember(locale) { NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 } }
    return if (value == null || !value.isFinite()) stringResource(R.string.unavailable_value)
    else stringResource(R.string.celsius_value, format.format(value))
}

@Composable
internal fun ZoneTemperatureCard(
    state: ClimateState,
    passenger: Boolean,
    compact: Boolean,
    onAdjust: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(if (passenger) R.string.climate_passenger else R.string.climate_driver)
    val temperature = if (passenger) state.passengerCelsius else state.driverCelsius
    val value = climateTemperature(temperature)
    val accessibleValue = stringResource(R.string.climate_temperature, label, value)
    val enabled = state.available && temperature != null && !(passenger && state.sync)
    val diameter = if (compact) 48.dp else 76.dp
    ClimateSurface(modifier) {
        Column(Modifier.fillMaxWidth().padding(if (compact) 14.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, Modifier.weight(1f), fontSize = if (compact) 15.sp else 18.sp,
                    color = AetherColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (passenger && state.sync) Text(stringResource(R.string.climate_linked),
                    fontSize = if (compact) 13.sp else 15.sp, color = AetherColors.Accent)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClimateStepButton(false, stringResource(R.string.climate_decrease_zone, label),
                    enabled && temperature > state.minCelsius, { onAdjust(-1) }, diameter = diameter)
                Text(value, Modifier.weight(1f).semantics { contentDescription = accessibleValue },
                    fontSize = if (compact) 30.sp else 38.sp, color = AetherColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                ClimateStepButton(true, stringResource(R.string.climate_increase_zone, label),
                    enabled && temperature < state.maxCelsius, { onAdjust(1) }, diameter = diameter)
            }
        }
    }
}

@Composable
internal fun ClimateSegments(level: Int, maximum: Int, color: Color, modifier: Modifier = Modifier, stepped: Boolean = false) {
    val count = maximum.coerceIn(1, 32)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (count > 12) 1.dp else 2.dp), verticalAlignment = Alignment.Bottom) {
        repeat(count) { index ->
            val active = index < level
            Box(Modifier.weight(1f).fillMaxHeight(if (stepped) .45f + .55f * (index + 1) / count else 1f)
                .clip(RoundedCornerShape(1.5.dp))
                .background(if (active) color else AetherColors.RailBorder)
                .then(if (active) Modifier.border(.3.dp, color.copy(alpha = .6f), RoundedCornerShape(1.5.dp)) else Modifier))
        }
    }
}

@Composable
internal fun ClimateToggleTile(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    compact: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ClimateIcon? = null,
    glyph: Glyph? = null,
    showSwitch: Boolean = false,
) {
    val target = if (compact) 48.dp else 76.dp
    ClimateSurface(modifier.heightIn(min = target)
        .toggleable(selected, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() }), selected) {
        Row(Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val tint = if (!enabled) AetherColors.Muted else if (selected) AetherColors.Accent else AetherColors.TextSecondary
            if (icon != null) ClimateGlyph(icon, size = if (compact) 24.dp else 28.dp, color = tint)
            if (glyph != null) AetherGlyph(glyph, size = if (compact) 24.dp else 28.dp, color = tint)
            Text(label, Modifier.weight(1f), color = tint,
                fontSize = if (compact) 14.sp else 17.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (showSwitch) {
                Box(Modifier.width(30.dp).height(18.dp).clip(CircleShape)
                    .background(if (selected) AetherColors.Accent else AetherColors.RailBorder),
                    contentAlignment = if (selected) Alignment.CenterEnd else Alignment.CenterStart) {
                    Box(Modifier.padding(2.dp).size(14.dp)
                        .background(if (selected) AetherColors.OnAccent else AetherColors.TextSecondary, CircleShape))
                }
            }
        }
    }
}
