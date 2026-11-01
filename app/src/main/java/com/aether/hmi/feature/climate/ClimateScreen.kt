package com.aether.hmi.feature.climate

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.hmi.R
import com.aether.hmi.domain.AirflowMode
import com.aether.hmi.domain.AppTheme
import com.aether.hmi.domain.CabinAir
import com.aether.hmi.domain.ClimateControl
import com.aether.hmi.domain.ClimateState
import com.aether.hmi.domain.SeatComfort
import com.aether.hmi.domain.SeatZone
import com.aether.hmi.ui.components.AetherGlyph
import com.aether.hmi.ui.components.Glyph
import com.aether.hmi.ui.theme.AetherColors
import com.aether.hmi.ui.theme.LocalAetherPalette
import kotlin.math.cos
import kotlin.math.sin

data class ClimateActions(
    val onAdjustDriver: (Int) -> Unit,
    val onAdjustPassenger: (Int) -> Unit,
    val onFanAdjust: (Int) -> Unit,
    val onAirflow: (AirflowMode) -> Unit,
    val onToggle: (ClimateControl) -> Unit,
    val onSeatLevel: (SeatZone, SeatComfort) -> Unit,
)

/** Finite row budgets keep the cabin and every primary control in one landscape page. */
internal data class ClimateLayoutBudget(val targetDp: Float, val commandHeightDp: Float, val gapDp: Float)

internal fun climateLayoutBudget(widthDp: Float, heightDp: Float, fontScale: Float): ClimateLayoutBudget {
    val target = minOf(widthDp * .085f, heightDp * .155f, 76f).coerceAtLeast(40f)
    return ClimateLayoutBudget(target, target + 20f * fontScale.coerceIn(1f, 1.5f), (heightDp * .025f).coerceIn(6f, 16f))
}

/** The original cabin animation stays visible, including on a landscape phone. */
@Composable
fun ClimateScreen(state: ClimateState, actions: ClimateActions, modifier: Modifier = Modifier, reducedMotion: Boolean = false) {
    var showMore by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        val budget = climateLayoutBudget(availableWidth.value, availableHeight.value, LocalDensity.current.fontScale)
        val gap = budget.gapDp.dp
        val target = budget.targetDp.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                if (!showMore) ModernZoneTemperature(state, false, actions.onAdjustDriver, target, reducedMotion,
                    Modifier.weight(.25f).fillMaxHeight())
                CabinVisual(state, reducedMotion, Modifier.weight(if (showMore) .46f else .50f).fillMaxHeight())
                if (showMore) ClimateControlDeck(state, actions, Modifier.weight(.54f).fillMaxHeight())
                else ModernZoneTemperature(state, true, actions.onAdjustPassenger, target, reducedMotion,
                    Modifier.weight(.25f).fillMaxHeight())
            }
            Row(Modifier.fillMaxWidth().height(budget.commandHeightDp.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                FittedFanControl(state, actions.onFanAdjust, target, Modifier.weight(2.1f).fillMaxHeight())
                PrimaryClimateButton(stringResource(R.string.auto_mode), state.auto, state.available,
                    { actions.onToggle(ClimateControl.Auto) }, Modifier.weight(1f).fillMaxHeight(), icon = ClimateIcon.Auto)
                PrimaryClimateButton(stringResource(R.string.climate_front_short), state.frontDefrost, state.available,
                    { actions.onToggle(ClimateControl.FrontDefrost) }, Modifier.weight(1f).fillMaxHeight(), glyph = Glyph.Front,
                    accessibilityLabel = stringResource(R.string.climate_front_defrost))
                PrimaryClimateButton(stringResource(R.string.climate_rear_short), state.rearDefrost, state.available,
                    { actions.onToggle(ClimateControl.RearDefrost) }, Modifier.weight(1f).fillMaxHeight(), glyph = Glyph.Rear,
                    accessibilityLabel = stringResource(R.string.climate_rear_defrost))
                PrimaryClimateButton(stringResource(if (showMore) R.string.climate_done_short else R.string.climate_more_short), showMore, true,
                    { showMore = !showMore }, Modifier.weight(1f).fillMaxHeight(), glyph = if (showMore) Glyph.Check else Glyph.Chevron,
                    accessibilityLabel = stringResource(if (showMore) R.string.climate_return_temperatures else R.string.climate_more_controls), isSwitch = false)
            }
        }
    }
}

@Composable
private fun ModernZoneTemperature(state: ClimateState, passenger: Boolean, onAdjust: (Int) -> Unit,
    target: Dp, reducedMotion: Boolean, modifier: Modifier) {
    val label = stringResource(if (passenger) R.string.climate_passenger else R.string.climate_driver)
    val temperature = if (passenger) state.passengerCelsius else state.driverCelsius
    val value = climateTemperature(temperature)
    val temperatureDescription = stringResource(R.string.climate_temperature, label, value)
    val description = if (passenger && state.sync) "$temperatureDescription, ${stringResource(R.string.climate_linked)}" else temperatureDescription
    val enabled = state.available && temperature != null && !(passenger && state.sync)
    val fraction = temperatureFraction(temperature, state.minCelsius, state.maxCelsius)
    val progress by animateFloatAsState(fraction, animationSpec = tween(if (reducedMotion) 0 else 220), label = "temperature dial")
    val cool = ClimateCool
    val warm = ClimateWarm
    val text = AetherColors.TextPrimary
    val accent = lerp(cool, warm, fraction)
    val shape = RoundedCornerShape(22.dp)
    Box(modifier.clip(shape).background(Brush.verticalGradient(listOf(AetherColors.PanelRaised, AetherColors.PanelDeep)))
        .background(Brush.radialGradient(listOf(accent.copy(alpha = .10f), Color.Transparent)))
        .border(.7.dp, AetherColors.Border, shape)) {
        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FittedClimateText(label, Modifier.weight(1f).fillMaxHeight(), maxSize = 19.sp,
                    color = AetherColors.TextPrimary, weight = FontWeight.Medium)
                if (passenger && state.sync) ClimateGlyph(ClimateIcon.Link, size = 14.dp, color = AetherColors.Accent)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val dialSize = minOf(maxWidth, maxHeight)
                Box(Modifier.size(dialSize), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = (size.minDimension * .035f).coerceIn(2.dp.toPx(), 5.dp.toPx())
                        val inset = stroke * 2.5f
                        val diameter = size.minDimension - inset * 2f
                        val topLeft = Offset(inset, inset)
                        val arcSize = Size(diameter, diameter)
                        // A temperature range indicator, rather than an unconnected decorative gauge.
                        repeat(72) { segment ->
                            val position = segment / 71f
                            val color = lerp(cool, warm, position)
                            drawArc(color.copy(alpha = if (state.available && position <= progress) .95f else .19f),
                                135f + segment * 3.75f, 3.3f, false, topLeft, arcSize,
                                style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                        if (temperature != null && temperature.isFinite()) {
                            val angle = Math.toRadians((135f + 270f * progress).toDouble())
                            val radius = diameter / 2f
                            val marker = center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius)
                            drawCircle(accent.copy(alpha = .12f), stroke * 3f, marker)
                            drawCircle(text, stroke * .9f, marker)
                        }
                        drawCircle(accent.copy(alpha = .035f), diameter * .40f)
                    }
                    FittedClimateText(value, Modifier.fillMaxWidth(.78f).fillMaxHeight(.44f)
                        .semantics { contentDescription = description }, maxSize = 60.sp, minSize = 16.sp,
                        color = AetherColors.TextPrimary, weight = FontWeight.Light)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ClimateStepButton(false, stringResource(R.string.climate_decrease_zone, label),
                    enabled && temperature > state.minCelsius, { onAdjust(-1) }, diameter = target, accentColor = cool)
                ClimateStepButton(true, stringResource(R.string.climate_increase_zone, label),
                    enabled && temperature < state.maxCelsius, { onAdjust(1) }, diameter = target, accentColor = warm)
            }
        }
    }
}

internal fun temperatureFraction(temperature: Float?, minimum: Float, maximum: Float): Float =
    if (temperature == null || !temperature.isFinite() || !minimum.isFinite() || !maximum.isFinite() || maximum <= minimum) 0f
    else ((temperature.toDouble() - minimum) / (maximum.toDouble() - minimum)).coerceIn(0.0, 1.0).toFloat()

@Composable
private fun FittedFanControl(state: ClimateState, onAdjust: (Int) -> Unit, target: Dp, modifier: Modifier) {
    val description = stringResource(R.string.climate_fan_level, state.fanSpeed, state.maxFanSpeed)
    ClimateSurface(modifier) {
        Row(Modifier.fillMaxSize().padding(6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ClimateStepButton(false, stringResource(R.string.climate_decrease_fan), state.available && state.fanSpeed > 0,
                { onAdjust(-1) }, diameter = target)
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                ClimateGlyph(ClimateIcon.Fan, size = 20.dp, color = AetherColors.TextSecondary)
                FittedClimateText(state.fanSpeed.toString(), Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = description },
                    maxSize = 26.sp, minSize = 14.sp, color = AetherColors.TextPrimary, weight = FontWeight.SemiBold)
            }
            ClimateStepButton(true, stringResource(R.string.climate_increase_fan), state.available && state.fanSpeed < state.maxFanSpeed,
                { onAdjust(1) }, diameter = target)
        }
    }
}

@Composable
private fun PrimaryClimateButton(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier,
    icon: ClimateIcon? = null, glyph: Glyph? = null, accessibilityLabel: String = label, isSwitch: Boolean = true) {
    val tint = if (!enabled) AetherColors.Muted else if (selected) AetherColors.Accent else AetherColors.TextSecondary
    ClimateSurface(modifier.then(if (isSwitch) Modifier.toggleable(selected, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
        else Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)).semantics { contentDescription = accessibilityLabel }, selected) {
        Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (icon != null) ClimateGlyph(icon, size = 24.dp, color = tint)
            if (glyph != null) AetherGlyph(glyph, size = 24.dp, color = tint)
            FittedClimateText(label, Modifier.fillMaxWidth().weight(1f), maxSize = 18.sp, color = tint)
        }
    }
}

@Composable
private fun CabinVisual(state: ClimateState, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalAetherPalette.current
    val appearance = remember(palette) { climateAppearance(palette) }
    val cabinFilter = remember(palette.theme) {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(if (palette.theme == AppTheme.Midnight) 0f else .40f) })
    }
    Box(modifier.clip(RoundedCornerShape(16.dp))) {
        Image(painterResource(R.drawable.climate_cabin_clean_v2), contentDescription = null,
            contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, -.36f), colorFilter = cabinFilter, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(appearance.cabinTint))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(appearance.cabinScrimTop, appearance.cabinScrimBottom))))
        CabinAirflowOverlay(state.airflowMode, state.fanSpeed, state.maxFanSpeed, state.available, reducedMotion, Modifier.fillMaxSize())
    }
}

/** An inline deck replaces the temperature panels while the animated cabin stays alongside it. */
@Composable
private fun ClimateControlDeck(state: ClimateState, actions: ClimateActions, modifier: Modifier) {
    var page by rememberSaveable { mutableStateOf(0) }
    BoxWithConstraints(modifier) {
        val deckHeight = maxHeight
        val compact = deckHeight < 320.dp
        val geometry = climateDeckBudget(deckHeight.value)
        val padding = geometry.paddingDp.dp
        val gap = geometry.gapDp.dp
        val chromeHeight = geometry.tabHeightDp.dp
        ClimateSurface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.fillMaxWidth().height(chromeHeight).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(R.string.climate_flow_short, R.string.climate_seats_short, R.string.climate_air_short).forEachIndexed { index, label ->
                        val selected = page == index
                        val shape = RoundedCornerShape(12.dp)
                        Box(Modifier.weight(1f).fillMaxHeight().clip(shape)
                            .background(if (selected) AetherColors.Accent.copy(alpha = .13f) else Color.Transparent)
                            .border(if (selected) 1.dp else .5.dp, if (selected) AetherColors.Accent.copy(alpha = .55f) else AetherColors.Border, shape)
                            .selectable(selected, role = Role.Tab, onClick = { page = index }), contentAlignment = Alignment.Center) {
                            FittedClimateText(stringResource(label), Modifier.fillMaxSize().padding(horizontal = 4.dp), maxSize = 18.sp,
                                color = if (selected) AetherColors.Accent else AetherColors.TextSecondary,
                                weight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (page) {
                        0 -> ClimateAirflowPage(state, actions, gap, Modifier.fillMaxSize())
                        1 -> ClimateSeatsPage(state, actions.onSeatLevel, compact, gap, Modifier.fillMaxSize())
                        else -> ClimateAirPage(state, compact, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

internal data class ClimateDeckBudget(val paddingDp: Float, val gapDp: Float, val tabHeightDp: Float)

internal fun climateDeckBudget(heightDp: Float): ClimateDeckBudget =
    if (heightDp < 320f) ClimateDeckBudget(4f, 4f, 40f) else ClimateDeckBudget(12f, 8f, 48f)

@Composable
private fun ClimateAirflowPage(state: ClimateState, actions: ClimateActions, gap: Dp, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            DeckToggle(stringResource(R.string.ac), state.ac, state.available, { actions.onToggle(ClimateControl.Ac) },
                ClimateIcon.Snow, Modifier.weight(1f).fillMaxHeight())
            DeckToggle(stringResource(R.string.sync), state.sync, state.available && state.driverCelsius != null && state.passengerCelsius != null,
                { actions.onToggle(ClimateControl.Sync) }, ClimateIcon.Link, Modifier.weight(1f).fillMaxHeight())
        }
        AirflowMode.entries.chunked(2).forEach { modes ->
            Row(Modifier.fillMaxWidth().weight(1f).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                modes.forEach { mode -> AirflowChoice(state, mode, actions.onAirflow, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun DeckToggle(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, icon: ClimateIcon, modifier: Modifier) {
    val tint = if (!enabled) AetherColors.Muted else if (selected) AetherColors.Accent else AetherColors.TextSecondary
    ClimateSurface(modifier.toggleable(selected, enabled = enabled, role = Role.Switch, onValueChange = { onClick() }), selected) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            ClimateGlyph(icon, size = 22.dp, color = tint)
            FittedClimateText(label, Modifier.weight(1f).fillMaxHeight(), color = tint, maxSize = 20.sp, align = TextAlign.Start)
        }
    }
}

@Composable
private fun AirflowChoice(state: ClimateState, mode: AirflowMode, onAirflow: (AirflowMode) -> Unit, modifier: Modifier) {
    val selected = state.airflowMode == mode
    val label = stringResource(when (mode) {
        AirflowMode.Face -> R.string.climate_face
        AirflowMode.FaceFeet -> R.string.climate_face_feet
        AirflowMode.Feet -> R.string.climate_feet
        AirflowMode.Defrost -> R.string.climate_defrost
    })
    val tint = if (selected) AetherColors.Accent else AetherColors.TextSecondary
    ClimateSurface(modifier.selectable(selected, enabled = state.available, role = Role.RadioButton, onClick = { onAirflow(mode) }), selected) {
        Row(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (mode == AirflowMode.Defrost) AetherGlyph(Glyph.Front, size = 20.dp, color = tint)
            else ClimateGlyph(when (mode) {
                AirflowMode.Face -> ClimateIcon.Face
                AirflowMode.FaceFeet -> ClimateIcon.FaceFeet
                else -> ClimateIcon.Feet
            }, size = 20.dp, color = tint)
            FittedClimateText(label, Modifier.weight(1f).fillMaxHeight(), color = tint, maxSize = 18.sp, align = TextAlign.Start)
        }
    }
}

@Composable
private fun ClimateSeatsPage(state: ClimateState, onCycle: (SeatZone, SeatComfort) -> Unit, compact: Boolean, gap: Dp, modifier: Modifier) {
    var selectedZone by rememberSaveable { mutableStateOf(SeatZone.Driver) }
    val label = stringResource(if (selectedZone == SeatZone.Driver) R.string.climate_driver_seat else R.string.climate_passenger_seat)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(Modifier.fillMaxWidth().height(if (compact) 40.dp else 48.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            SeatZone.entries.forEach { zone ->
                ClimateSurface(Modifier.weight(1f).fillMaxHeight().selectable(selectedZone == zone, role = Role.Tab, onClick = { selectedZone = zone }), selectedZone == zone) {
                    FittedClimateText(stringResource(if (zone == SeatZone.Driver) R.string.climate_driver else R.string.climate_passenger),
                        Modifier.fillMaxSize().padding(horizontal = 6.dp), maxSize = 18.sp,
                        color = if (selectedZone == zone) AetherColors.Accent else AetherColors.TextSecondary)
                }
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            SeatComfort.entries.forEach { comfort ->
                SeatLevelControl(label, state.seatLevel(selectedZone, comfort), state.maxSeatLevel, comfort == SeatComfort.Heating,
                    state.available && state.maxSeatLevel > 0, { onCycle(selectedZone, comfort) }, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun SeatLevelControl(label: String, level: Int, maximum: Int, heating: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val description = stringResource(if (heating) R.string.climate_heat_level else R.string.climate_cool_level, label, level, maximum)
    val color = if (heating) ClimateWarm else ClimateCool
    ClimateSurface(modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = description }, level > 0) {
        Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ClimateGlyph(if (heating) ClimateIcon.HeatSeat else ClimateIcon.CoolSeat, size = 24.dp,
                color = if (level > 0) color else AetherColors.TextSecondary)
            FittedClimateText(stringResource(if (heating) R.string.climate_seat_heat else R.string.climate_seat_cool),
                Modifier.fillMaxWidth().weight(1f), color = AetherColors.TextPrimary, maxSize = 18.sp)
            FittedClimateText(stringResource(R.string.climate_level_value, level, maximum), Modifier.fillMaxWidth().weight(1f),
                color = if (level > 0) color else AetherColors.TextSecondary, maxSize = 22.sp, weight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ClimateAirPage(state: ClimateState, compact: Boolean, modifier: Modifier) {
    ClimateSurface(modifier) {
        Column(Modifier.fillMaxSize().padding(if (compact) 8.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().weight(1.2f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AetherGlyph(Glyph.Eco, size = 24.dp, color = AetherColors.Good)
                FittedClimateText(stringResource(when {
                    state.cabinAirQualityPercent == null -> R.string.climate_air_unknown
                    state.cabinAir == CabinAir.Clean -> R.string.climate_air_clean
                    else -> R.string.climate_air_check
                }), Modifier.weight(1f).fillMaxHeight(), color = AetherColors.TextPrimary, maxSize = 24.sp, maxLines = 2, align = TextAlign.Start)
            }
            FittedClimateText(stringResource(R.string.climate_filter_status, stringResource(when (state.cabinFilterHealthy) {
                true -> R.string.climate_filter_healthy
                false -> R.string.climate_filter_attention
                null -> R.string.climate_air_unknown
            })), Modifier.fillMaxWidth().weight(1f), maxSize = 18.sp, maxLines = 2, align = TextAlign.Start)
            FittedClimateText(stringResource(R.string.climate_demo_air_summary), Modifier.fillMaxWidth().weight(.8f),
                maxSize = 15.sp, maxLines = 2, align = TextAlign.Start)
        }
    }
}

/** Text fits its own slot; hit areas and the cabin are never scaled as a bitmap. */
@Composable
private fun FittedClimateText(text: String, modifier: Modifier = Modifier, maxSize: TextUnit = 18.sp, minSize: TextUnit = 11.sp,
    color: Color = AetherColors.TextSecondary, weight: FontWeight = FontWeight.Normal, maxLines: Int = 1, align: TextAlign = TextAlign.Center) {
    Box(modifier, contentAlignment = if (align == TextAlign.Start) Alignment.CenterStart else Alignment.Center) {
        BasicText(text, Modifier.fillMaxWidth(), style = TextStyle(color = color, fontWeight = weight, textAlign = align),
            maxLines = maxLines, autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = maxSize, stepSize = 1.sp))
    }
}
