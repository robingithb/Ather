package com.aether.hmi.feature.climate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import java.text.NumberFormat
import java.util.Locale

internal val ClimateWarm = Color(0xFFFF6255)
internal val ClimateCool = Color(0xFF00B8FF)

@Composable
internal fun ClimateSurface(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.clip(shape)
        .background(Brush.linearGradient(listOf(Color(0xF2071B2B), Color(0xF0000D17))))
        .then(if (selected) Modifier.background(Brush.radialGradient(listOf(AetherColors.Accent.copy(alpha = .19f), Color.Transparent))) else Modifier)
        .border(if (selected) 1.1.dp else .65.dp, if (selected) AetherColors.Accent else AetherColors.Border, shape), content = content)
}

@Composable
internal fun ClimateStepButton(
    plus: Boolean,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 30.dp,
) {
    Box(modifier.size(diameter).clip(CircleShape)
        .background(Brush.verticalGradient(listOf(Color(0xFF183247), Color(0xFF071421))))
        .border(.65.dp, AetherColors.Border, CircleShape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        AetherGlyph(if (plus) Glyph.Plus else Glyph.Minus, size = diameter * .43f,
            color = if (enabled) AetherColors.TextPrimary else AetherColors.Muted.copy(alpha = .4f))
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
    val synced = passenger && state.sync
    val diameter = if (compact) 24.dp else 30.dp
    ClimateSurface(modifier) {
        Row(Modifier.fillMaxSize().padding(if (compact) 6.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)) {
            // The driver's rail sits on the right; the passenger's mirrors it on the left.
            if (passenger) TemperatureRail(state, temperature, enabled, onAdjust, label, diameter)
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ClimateGlyph(ClimateIcon.Person, size = if (compact) 12.dp else 15.dp)
                    Text(label, fontSize = if (compact) 9.sp else 11.sp, lineHeight = if (compact) 11.sp else 13.sp,
                        color = AetherColors.TextSecondary, maxLines = 1)
                }
                Text(value, modifier = Modifier.semantics { contentDescription = accessibleValue },
                    fontSize = if (compact) 24.sp else 31.sp, lineHeight = if (compact) 29.sp else 36.sp,
                    color = AetherColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(if (synced) stringResource(R.string.climate_linked) else stringResource(R.string.climate_temperature_range,
                    climateTemperature(state.minCelsius), climateTemperature(state.maxCelsius)),
                    fontSize = if (compact) 8.sp else 10.sp, lineHeight = if (compact) 10.sp else 12.sp,
                    color = if (synced) AetherColors.Accent else AetherColors.TextSecondary, maxLines = 1)
            }
            if (!passenger) TemperatureRail(state, temperature, enabled, onAdjust, label, diameter)
        }
    }
}

@Composable
private fun TemperatureRail(state: ClimateState, temperature: Float?, enabled: Boolean, onAdjust: (Int) -> Unit, label: String, diameter: Dp) {
    Column(Modifier.width(diameter).fillMaxHeight().clip(RoundedCornerShape(diameter / 2))
        .background(Color(0xFF081724)).border(.6.dp, AetherColors.Border, RoundedCornerShape(diameter / 2)),
        horizontalAlignment = Alignment.CenterHorizontally) {
        ClimateStepButton(true, stringResource(R.string.climate_increase_zone, label), enabled && temperature != null && temperature < state.maxCelsius,
            { onAdjust(1) }, diameter = diameter)
        Box(Modifier.weight(1f).width(5.dp).padding(vertical = 1.dp).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(ClimateWarm, ClimateWarm, ClimateCool))))
        ClimateStepButton(false, stringResource(R.string.climate_decrease_zone, label), enabled && temperature != null && temperature > state.minCelsius,
            { onAdjust(-1) }, diameter = diameter)
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
                .background(if (active) color else Color(0xFF1A3549))
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
    ClimateSurface(modifier.toggleable(selected, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() }), selected) {
        Row(Modifier.fillMaxSize().padding(horizontal = if (compact) 5.dp else 9.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp), verticalAlignment = Alignment.CenterVertically) {
            val tint = if (!enabled) AetherColors.Muted else if (selected) AetherColors.Accent else AetherColors.TextSecondary
            if (icon != null) ClimateGlyph(icon, size = if (compact) 18.dp else 24.dp, color = tint)
            if (glyph != null) AetherGlyph(glyph, size = if (compact) 19.dp else 25.dp, color = tint)
            Text(label, Modifier.weight(1f), color = if (selected) AetherColors.Accent else AetherColors.TextSecondary,
                fontSize = if (compact) 9.sp else 11.sp, lineHeight = if (compact) 11.sp else 13.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (showSwitch) {
                Box(Modifier.width(if (compact) 23.dp else 29.dp).height(if (compact) 13.dp else 17.dp)
                    .clip(CircleShape).background(if (selected) AetherColors.Accent else Color(0xFF244254)),
                    contentAlignment = if (selected) Alignment.CenterEnd else Alignment.CenterStart) {
                    Box(Modifier.padding(2.dp).size(if (compact) 9.dp else 13.dp).background(AetherColors.TextPrimary, CircleShape))
                }
            }
        }
    }
}
