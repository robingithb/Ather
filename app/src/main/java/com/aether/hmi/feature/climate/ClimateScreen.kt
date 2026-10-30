package com.aether.hmi.feature.climate

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.hmi.R
import com.aether.hmi.domain.AirflowMode
import com.aether.hmi.domain.CabinAir
import com.aether.hmi.domain.ClimateControl
import com.aether.hmi.domain.ClimateState
import com.aether.hmi.domain.SeatComfort
import com.aether.hmi.domain.SeatZone
import com.aether.hmi.ui.components.AetherGlyph
import com.aether.hmi.ui.components.Glyph
import com.aether.hmi.ui.theme.AetherColors
import com.aether.hmi.ui.theme.LocalAetherPalette

data class ClimateActions(
    val onAdjustDriver: (Int) -> Unit,
    val onAdjustPassenger: (Int) -> Unit,
    val onFanAdjust: (Int) -> Unit,
    val onAirflow: (AirflowMode) -> Unit,
    val onToggle: (ClimateControl) -> Unit,
    val onSeatLevel: (SeatZone, SeatComfort) -> Unit,
)

/** Vehicle state is hoisted; only the explanatory dialog is local UI state. */
@Composable
fun ClimateScreen(
    state: ClimateState,
    actions: ClimateActions,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
) {
    var showAirInfo by rememberSaveable { mutableStateOf(false) }
    val palette = LocalAetherPalette.current
    val appearance = remember(palette) { climateAppearance(palette) }
    val cabinFilter = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(.40f) }) }
    BoxWithConstraints(modifier) {
        val compact = maxWidth < 650.dp || maxHeight < 260.dp
        val gap = if (compact) 4.dp else 6.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Box(Modifier.weight(.8f).fillMaxHeight().clip(RoundedCornerShape(10.dp))) {
                    Image(painterResource(R.drawable.climate_cabin_clean_v2), contentDescription = null,
                        contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, -.36f),
                        colorFilter = cabinFilter, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(appearance.cabinTint))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(appearance.cabinScrimTop, appearance.cabinScrimBottom))))
                    CabinAirflowOverlay(mode = state.airflowMode, fanSpeed = state.fanSpeed,
                        maxFanSpeed = state.maxFanSpeed, available = state.available,
                        reducedMotion = reducedMotion, modifier = Modifier.fillMaxSize())
                    Row(Modifier.fillMaxSize().padding(top = if (compact) 7.dp else 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        ZoneTemperatureCard(state, passenger = false, compact, actions.onAdjustDriver,
                            Modifier.fillMaxWidth(.29f).fillMaxHeight(.94f))
                        ZoneTemperatureCard(state, passenger = true, compact, actions.onAdjustPassenger,
                            Modifier.fillMaxWidth(.41f).fillMaxHeight(.94f))
                    }
                }
                AirQualityCard(state, compact, { showAirInfo = true }, Modifier.weight(.2f).fillMaxHeight())
            }
            Row(Modifier.fillMaxWidth().height(if (compact) 30.dp else 36.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(gap)) {
                AirflowMode.entries.forEach { mode ->
                    val selected = state.airflowMode == mode
                    val label = stringResource(when (mode) {
                        AirflowMode.Face -> R.string.climate_face
                        AirflowMode.FaceFeet -> R.string.climate_face_feet
                        AirflowMode.Feet -> R.string.climate_feet
                        AirflowMode.Defrost -> R.string.climate_defrost
                    })
                    ClimateSurface(Modifier.weight(1f).fillMaxHeight().selectable(selected, enabled = state.available,
                        role = Role.RadioButton, onClick = { actions.onAirflow(mode) }), selected) {
                        Row(Modifier.fillMaxSize().padding(horizontal = if (compact) 5.dp else 9.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            val tint = if (selected) AetherColors.Accent else AetherColors.TextSecondary
                            if (mode == AirflowMode.Defrost) AetherGlyph(Glyph.Front, size = if (compact) 20.dp else 27.dp, color = tint)
                            else ClimateGlyph(when (mode) {
                                AirflowMode.Face -> ClimateIcon.Face
                                AirflowMode.FaceFeet -> ClimateIcon.FaceFeet
                                else -> ClimateIcon.Feet
                            }, size = if (compact) 21.dp else 28.dp, color = tint)
                            Spacer(Modifier.width(if (compact) 5.dp else 9.dp))
                            Text(label, fontSize = if (compact) 9.sp else 11.sp, lineHeight = if (compact) 11.sp else 13.sp,
                                color = tint, maxLines = 1)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(if (compact) 34.dp else 44.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                FanSpeedCard(state, compact, actions.onFanAdjust, Modifier.weight(3.1f).fillMaxHeight())
                ClimateToggleTile(stringResource(R.string.auto_mode), state.auto, state.available, compact,
                    { actions.onToggle(ClimateControl.Auto) }, Modifier.weight(1f).fillMaxHeight(), icon = ClimateIcon.Auto)
                ClimateToggleTile(stringResource(R.string.ac), state.ac, state.available, compact,
                    { actions.onToggle(ClimateControl.Ac) }, Modifier.weight(1f).fillMaxHeight(), icon = ClimateIcon.Snow, showSwitch = true)
                ClimateToggleTile(stringResource(R.string.sync), state.sync,
                    state.available && state.driverCelsius != null && state.passengerCelsius != null, compact,
                    { actions.onToggle(ClimateControl.Sync) }, Modifier.weight(1f).fillMaxHeight(), icon = ClimateIcon.Link, showSwitch = true)
            }
            Row(Modifier.fillMaxWidth().height(if (compact) 34.dp else 44.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                ClimateToggleTile(stringResource(R.string.climate_front_defrost), state.frontDefrost, state.available, compact,
                    { actions.onToggle(ClimateControl.FrontDefrost) }, Modifier.weight(1f).fillMaxHeight(), glyph = Glyph.Front)
                ClimateToggleTile(stringResource(R.string.climate_rear_defrost), state.rearDefrost, state.available, compact,
                    { actions.onToggle(ClimateControl.RearDefrost) }, Modifier.weight(1f).fillMaxHeight(), glyph = Glyph.Rear)
                SeatComfortCard(state, SeatZone.Driver, compact, actions.onSeatLevel, Modifier.weight(1.35f).fillMaxHeight())
                SeatComfortCard(state, SeatZone.Passenger, compact, actions.onSeatLevel, Modifier.weight(1.35f).fillMaxHeight())
            }
        }
    }
    if (showAirInfo) AlertDialog(onDismissRequest = { showAirInfo = false },
        title = { Text(stringResource(R.string.climate_demo_air_title)) },
        text = { Text(stringResource(R.string.climate_demo_air_message)) },
        confirmButton = { TextButton(onClick = { showAirInfo = false }) { Text(stringResource(R.string.close)) } })
}

@Composable
private fun AirQualityCard(state: ClimateState, compact: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val airDescription = state.cabinAirQualityPercent?.let { stringResource(R.string.climate_air_percent, it) }
        ?: stringResource(R.string.climate_air_unknown)
    ClimateSurface(modifier.clickable(role = Role.Button, onClick = onClick)) {
        Column(Modifier.fillMaxSize().padding(if (compact) 7.dp else 10.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                AetherGlyph(Glyph.Eco, size = if (compact) 15.dp else 21.dp, color = AetherColors.Good)
                Text(stringResource(R.string.climate_air_quality), fontSize = if (compact) 9.sp else 11.sp,
                    lineHeight = if (compact) 11.sp else 13.sp, color = AetherColors.TextSecondary, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(when {
                    state.cabinAirQualityPercent == null -> R.string.climate_air_unknown
                    state.cabinAir == CabinAir.Clean -> R.string.climate_air_good
                    else -> R.string.climate_air_attention
                }), Modifier.weight(1f), fontSize = if (compact) 17.sp else 22.sp,
                    lineHeight = if (compact) 20.sp else 25.sp, fontWeight = FontWeight.SemiBold,
                    color = AetherColors.TextPrimary, maxLines = 1)
                AetherGlyph(Glyph.Chevron, size = 11.dp, color = AetherColors.TextPrimary)
            }
            Text(stringResource(when {
                state.cabinAirQualityPercent == null -> R.string.climate_air_unknown
                state.cabinAir == CabinAir.Clean -> R.string.climate_air_clean
                else -> R.string.climate_air_check
            }), fontSize = if (compact) 8.sp else 10.sp, lineHeight = if (compact) 10.sp else 12.sp,
                color = AetherColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.fillMaxWidth().height(if (compact) 5.dp else 7.dp).clip(RoundedCornerShape(10.dp)).background(AetherColors.RailBorder)
                .semantics { contentDescription = airDescription }) {
                state.cabinAirQualityPercent?.let { quality ->
                    Box(Modifier.fillMaxWidth(quality.coerceIn(0, 100) / 100f).fillMaxHeight().background(AetherColors.Good))
                }
            }
            Spacer(Modifier.fillMaxWidth().height(.6.dp).background(AetherColors.Border))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)) {
                ClimateGlyph(ClimateIcon.Filter, size = if (compact) 22.dp else 31.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.climate_cabin_filter), fontSize = if (compact) 8.sp else 10.sp,
                        lineHeight = if (compact) 10.sp else 12.sp, color = AetherColors.TextSecondary, maxLines = 1)
                    Text(stringResource(when (state.cabinFilterHealthy) {
                        true -> R.string.climate_filter_healthy
                        false -> R.string.climate_filter_attention
                        null -> R.string.climate_air_unknown
                    }), fontSize = if (compact) 10.sp else 13.sp, lineHeight = if (compact) 12.sp else 15.sp,
                        color = if (state.cabinFilterHealthy == true) AetherColors.Good else AetherColors.TextSecondary, maxLines = 1)
                }
                AetherGlyph(Glyph.Chevron, size = if (compact) 8.dp else 11.dp, color = AetherColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun FanSpeedCard(state: ClimateState, compact: Boolean, onAdjust: (Int) -> Unit, modifier: Modifier = Modifier) {
    val fanLabel = stringResource(R.string.climate_fan_level, state.fanSpeed, state.maxFanSpeed)
    ClimateSurface(modifier) {
        Row(Modifier.fillMaxSize().padding(horizontal = if (compact) 6.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ClimateGlyph(ClimateIcon.Fan, size = if (compact) 19.dp else 25.dp)
            Text(stringResource(R.string.climate_fan_speed), fontSize = if (compact) 9.sp else 11.sp,
                lineHeight = if (compact) 11.sp else 13.sp, color = AetherColors.TextSecondary, maxLines = 1)
            ClimateStepButton(false, stringResource(R.string.climate_decrease_fan), state.available && state.fanSpeed > 0,
                { onAdjust(-1) }, diameter = if (compact) 23.dp else 31.dp)
            ClimateSegments(state.fanSpeed, state.maxFanSpeed, AetherColors.Accent,
                Modifier.weight(1f).height(if (compact) 17.dp else 23.dp).semantics { contentDescription = fanLabel }, stepped = true)
            ClimateStepButton(true, stringResource(R.string.climate_increase_fan), state.available && state.fanSpeed < state.maxFanSpeed,
                { onAdjust(1) }, diameter = if (compact) 23.dp else 31.dp)
        }
    }
}

@Composable
private fun SeatComfortCard(state: ClimateState, zone: SeatZone, compact: Boolean,
    onCycle: (SeatZone, SeatComfort) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(if (zone == SeatZone.Driver) R.string.climate_driver_seat else R.string.climate_passenger_seat)
    ClimateSurface(modifier) {
        Row(Modifier.fillMaxSize().padding(horizontal = if (compact) 5.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SeatLevelControl(label, state.seatLevel(zone, SeatComfort.Heating), state.maxSeatLevel, true, compact, state.available,
                { onCycle(zone, SeatComfort.Heating) }, Modifier.weight(1.25f))
            Spacer(Modifier.padding(horizontal = if (compact) 3.dp else 5.dp).width(.6.dp).fillMaxHeight(.7f).background(AetherColors.Border))
            SeatLevelControl(null, state.seatLevel(zone, SeatComfort.Cooling), state.maxSeatLevel, false, compact, state.available,
                { onCycle(zone, SeatComfort.Cooling) }, Modifier.weight(.8f), accessibleLabel = label)
        }
    }
}

@Composable
private fun SeatLevelControl(label: String?, level: Int, maximum: Int, heating: Boolean, compact: Boolean, enabled: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier, accessibleLabel: String = label.orEmpty()) {
    val description = stringResource(if (heating) R.string.climate_heat_level else R.string.climate_cool_level, accessibleLabel, level, maximum)
    val color = if (heating) ClimateWarm else ClimateCool
    Row(modifier.fillMaxHeight().clip(RoundedCornerShape(5.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp)) {
        ClimateGlyph(if (heating) ClimateIcon.HeatSeat else ClimateIcon.CoolSeat,
            size = if (compact) 19.dp else 26.dp, color = if (level > 0) color else AetherColors.Muted)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp)) {
            if (label != null) Text(label, fontSize = if (compact) 8.sp else 10.sp, lineHeight = if (compact) 10.sp else 12.sp,
                color = AetherColors.TextSecondary, maxLines = 1)
            ClimateSegments(level, maximum, color, Modifier.fillMaxWidth().height(if (compact) 5.dp else 6.dp))
        }
    }
}
