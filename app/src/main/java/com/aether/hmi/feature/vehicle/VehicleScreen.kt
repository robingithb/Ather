package com.aether.hmi.feature.vehicle

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.hmi.R
import com.aether.hmi.domain.ClimateControl
import com.aether.hmi.domain.HeadlightMode
import com.aether.hmi.domain.SeatComfort
import com.aether.hmi.domain.SeatZone
import com.aether.hmi.domain.TurnSignal
import com.aether.hmi.domain.VehicleControlsState
import com.aether.hmi.domain.VehicleGear
import com.aether.hmi.domain.VehiclePart
import com.aether.hmi.domain.VehicleSnapshot
import com.aether.hmi.domain.VehicleWindow
import com.aether.hmi.domain.vehicleAttention
import com.aether.hmi.feature.climate.ClimateActions
import com.aether.hmi.feature.climate.ClimateGlyph
import com.aether.hmi.feature.climate.ClimateIcon
import com.aether.hmi.feature.climate.ClimateStepButton
import com.aether.hmi.feature.climate.climateTemperature
import com.aether.hmi.ui.components.AetherGlyph
import com.aether.hmi.ui.components.Glyph
import com.aether.hmi.ui.components.vehicleStatusColor
import com.aether.hmi.ui.components.vehicleStatusText
import com.aether.hmi.ui.theme.AetherColors
import kotlin.math.roundToInt

data class VehicleActions(
    val onPartToggle: (VehiclePart) -> Unit,
    val onLockedChange: (Boolean) -> Unit,
    val onHeadlightMode: (HeadlightMode) -> Unit,
    val onWindowPosition: (VehicleWindow, Int) -> Unit,
    val onParkingBrakeToggle: () -> Unit,
    val onChargingChange: (Boolean) -> Unit,
    val onTurnSignal: (TurnSignal) -> Unit = {},
    val onGear: (VehicleGear) -> Unit = {},
    val onBrakeLights: (Boolean) -> Unit = {},
    val onAmbientLights: (Boolean) -> Unit = {},
)

private enum class VehicleSection(val label: Int, val icon: VehicleIcon) {
    Overview(R.string.vehicle_overview, VehicleIcon.Overview),
    Doors(R.string.vehicle_doors, VehicleIcon.Door),
    Lights(R.string.vehicle_lights, VehicleIcon.Lights),
    Windows(R.string.vehicle_windows, VehicleIcon.Window),
    Trunk(R.string.vehicle_trunk, VehicleIcon.Trunk),
    Charging(R.string.vehicle_charging, VehicleIcon.Plug),
}

@Composable
fun VehicleScreen(snapshot: VehicleSnapshot, actions: VehicleActions, climateActions: ClimateActions, modifier: Modifier = Modifier) {
    var section by rememberSaveable { mutableStateOf(VehicleSection.Overview) }
    var focusedPart by rememberSaveable { mutableStateOf<VehiclePart?>(null) }
    fun selectSection(next: VehicleSection) { section = next; focusedPart = null }
    fun togglePart(part: VehiclePart) {
        if (!snapshot.controls.available || (part == VehiclePart.ChargePort && snapshot.vehicle.charging == true)) return
        actions.onPartToggle(part)
        focusedPart = part
        section = when (part) {
            VehiclePart.Trunk -> VehicleSection.Trunk
            VehiclePart.ChargePort -> VehicleSection.Charging
            else -> VehicleSection.Doors
        }
    }
    val focus = when (focusedPart) {
        VehiclePart.Bonnet -> VehicleSceneFocus.Bonnet
        VehiclePart.Sunroof -> VehicleSceneFocus.Sunroof
        else -> when (section) {
            VehicleSection.Overview -> VehicleSceneFocus.Overview
            VehicleSection.Doors -> VehicleSceneFocus.Doors
            VehicleSection.Lights -> VehicleSceneFocus.Lights
            VehicleSection.Windows -> VehicleSceneFocus.Windows
            VehicleSection.Trunk -> VehicleSceneFocus.Trunk
            VehicleSection.Charging -> VehicleSceneFocus.Charging
        }
    }
    val focusedActions = actions.copy(onPartToggle = ::togglePart)
    BoxWithConstraints(modifier) {
        val compact = maxWidth < 650.dp || maxHeight < 280.dp
        val gap = if (compact) 5.dp else 8.dp
        val leftWeight = if (compact) .19f else .18f
        val modelWeight = if (compact) .49f else .52f
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                VehicleModelStage(snapshot.controls, onPartTap = ::togglePart, modifier = Modifier.fillMaxSize(),
                    focus = focus, charging = snapshot.vehicle.charging == true, batteryPercent = snapshot.vehicle.batteryPercent,
                    contentLeftFraction = leftWeight, contentWidthFraction = modelWeight)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Column(Modifier.weight(leftWeight).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp)) {
                        Column(Modifier.fillMaxWidth().padding(vertical = if (compact) 3.dp else 7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.vehicle_control_title), fontSize = if (compact) 6.sp else 8.sp,
                                lineHeight = if (compact) 8.sp else 10.sp, letterSpacing = 1.sp, color = AetherColors.TextSecondary, maxLines = 1)
                            Text(stringResource(R.string.vehicle_my_vehicle), fontSize = if (compact) 18.sp else 24.sp,
                                lineHeight = if (compact) 22.sp else 29.sp, fontWeight = FontWeight.SemiBold, color = AetherColors.TextPrimary, maxLines = 1)
                            val attention = snapshot.vehicleAttention()
                            Text(vehicleStatusText(attention, R.string.vehicle_everything_good),
                                fontSize = if (compact) 8.sp else 10.sp, lineHeight = if (compact) 10.sp else 12.sp,
                                color = vehicleStatusColor(attention, AetherColors.TextSecondary), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Column(Modifier.weight(1f).fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp)) {
                            VehicleSection.entries.forEach { item ->
                                val selected = section == item
                            VehicleSurface(Modifier.weight(1f).fillMaxWidth().selectable(selected, role = Role.Tab, onClick = { selectSection(item) }), selected) {
                                    Row(Modifier.fillMaxSize().padding(horizontal = if (compact) 5.dp else 7.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp)) {
                                        VehicleGlyph(item.icon, size = if (compact) 14.dp else 19.dp,
                                            color = if (selected) AetherColors.Accent else AetherColors.TextSecondary)
                                        Text(stringResource(item.label), Modifier.weight(1f), color = AetherColors.TextPrimary,
                                            fontSize = if (compact) 9.sp else 11.sp, lineHeight = if (compact) 11.sp else 13.sp, maxLines = 1)
                                        AetherGlyph(Glyph.Chevron, size = if (compact) 8.dp else 11.dp, color = AetherColors.TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(modelWeight).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.weight(1f).fillMaxWidth())
                        Text(stringResource(R.string.vehicle_gesture_hint), Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            color = AetherColors.TextSecondary, fontSize = if (compact) 7.sp else 9.sp,
                            lineHeight = if (compact) 9.sp else 11.sp, textAlign = TextAlign.Center, maxLines = 1)
                    }
                    Box(Modifier.weight(if (compact) .32f else .30f).fillMaxHeight()) {
                        when (section) {
                            VehicleSection.Overview -> OverviewPanel(snapshot, compact, focusedActions, ::selectSection, Modifier.fillMaxSize())
                            VehicleSection.Doors -> DoorsPanel(snapshot, compact, focusedActions, Modifier.fillMaxSize())
                            VehicleSection.Lights -> LightsPanel(snapshot.controls, compact, actions, Modifier.fillMaxSize(), charging = snapshot.vehicle.charging == true)
                            VehicleSection.Windows -> WindowsPanel(snapshot.controls, compact, actions, Modifier.fillMaxSize())
                            VehicleSection.Trunk -> TrunkPanel(snapshot.controls, compact, focusedActions, Modifier.fillMaxSize())
                            VehicleSection.Charging -> ChargingPanel(snapshot, compact, focusedActions, Modifier.fillMaxSize())
                        }
                    }
                }
            }
            VehicleClimateDock(snapshot, climateActions, compact, Modifier.fillMaxWidth().height(if (compact) 45.dp else 55.dp))
        }
    }
}

@Composable
private fun OverviewPanel(snapshot: VehicleSnapshot, compact: Boolean, actions: VehicleActions,
    onSection: (VehicleSection) -> Unit, modifier: Modifier = Modifier) {
    val controls = snapshot.controls
    val unavailable = stringResource(R.string.unavailable_value)
    val gap = if (compact) 4.dp else 6.dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        VehicleBatteryCard(snapshot.vehicle, compact, Modifier.fillMaxWidth().height(if (compact) 65.dp else 83.dp), { onSection(VehicleSection.Charging) })
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(Modifier.weight(.55f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                VehicleStatusTile(stringResource(R.string.vehicle_doors), if (controls.available) stringResource(if (controls.locked) R.string.vehicle_locked else R.string.vehicle_unlocked) else unavailable,
                    if (controls.locked) VehicleIcon.Lock else VehicleIcon.Unlock, compact, Modifier.weight(1f).fillMaxWidth(),
                    onClick = { onSection(VehicleSection.Doors) })
                VehicleStatusTile(stringResource(R.string.vehicle_headlights), if (controls.available) stringResource(R.string.vehicle_light_state,
                    headlightModeLabel(controls.headlightMode), stringResource(if (controls.headlightsOn) R.string.vehicle_light_on else R.string.vehicle_light_off)) else unavailable,
                    VehicleIcon.Lights, compact, Modifier.weight(1f).fillMaxWidth(), selected = controls.available && controls.headlightsOn,
                    onClick = { onSection(VehicleSection.Lights) })
                VehicleStatusTile(stringResource(R.string.vehicle_parking_brake), if (controls.available) stringResource(if (controls.parkingBrakeEngaged) R.string.vehicle_brake_engaged else R.string.vehicle_brake_released) else unavailable,
                    VehicleIcon.Brake, compact, Modifier.weight(1f).fillMaxWidth(), selected = controls.available && controls.parkingBrakeEngaged,
                    enabled = controls.available, onClick = actions.onParkingBrakeToggle)
            }
            VehicleDoorDiagram(controls, compact, actions.onPartToggle, Modifier.weight(.45f).fillMaxHeight())
        }
    }
}

@Composable
private fun VehiclePanel(title: String, compact: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    VehicleSurface(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (compact) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)) {
            Text(title, color = AetherColors.TextPrimary, fontWeight = FontWeight.SemiBold,
                fontSize = if (compact) 14.sp else 18.sp, lineHeight = if (compact) 17.sp else 21.sp)
            content()
        }
    }
}

@Composable
private fun DoorsPanel(snapshot: VehicleSnapshot, compact: Boolean, actions: VehicleActions, modifier: Modifier = Modifier) {
    val controls = snapshot.controls
    VehiclePanel(stringResource(R.string.vehicle_doors), compact, modifier) {
        VehicleActionRow(stringResource(if (controls.locked) R.string.vehicle_unlock else R.string.vehicle_lock),
            stringResource(if (controls.available) if (controls.locked) R.string.vehicle_locked else R.string.vehicle_unlocked else R.string.unavailable_value),
            if (controls.locked) VehicleIcon.Lock else VehicleIcon.Unlock, controls.available, compact,
            onClick = { actions.onLockedChange(!controls.locked) })
        VehiclePart.entries.filter { it != VehiclePart.Trunk }.forEach { part ->
            val open = part in controls.openParts
            val label = vehiclePartLabel(part)
            VehicleActionRow(label, stringResource(if (!controls.available) R.string.unavailable_value else if (open) R.string.vehicle_open else R.string.vehicle_closed),
                if (part == VehiclePart.ChargePort) VehicleIcon.Plug else VehicleIcon.Door,
                controls.available && !(part == VehiclePart.ChargePort && snapshot.vehicle.charging == true), compact,
                selected = controls.available && open, onClick = { actions.onPartToggle(part) })
        }
        if (snapshot.vehicle.charging == true) VehicleHint(stringResource(R.string.vehicle_port_charging_locked), compact)
        VehicleHint(stringResource(R.string.vehicle_doors_hint), compact)
    }
}

@Composable
private fun LightsPanel(controls: VehicleControlsState, compact: Boolean, actions: VehicleActions, modifier: Modifier = Modifier, charging: Boolean = false) {
    VehiclePanel(stringResource(R.string.vehicle_headlights), compact, modifier) {
        Text(if (controls.available) stringResource(R.string.vehicle_headlight_status,
            stringResource(if (controls.headlightsOn) R.string.vehicle_light_on else R.string.vehicle_light_off))
            else stringResource(R.string.vehicle_unavailable_controls),
            color = AetherColors.TextSecondary, fontSize = if (compact) 10.sp else 12.sp, lineHeight = if (compact) 12.sp else 14.sp)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HeadlightMode.entries.forEach { mode ->
                VehicleChoice(headlightModeLabel(mode), controls.available && controls.headlightMode == mode,
                    controls.available, compact, { actions.onHeadlightMode(mode) }, Modifier.weight(1f))
            }
        }
        VehicleHint(stringResource(R.string.vehicle_signal_status, if (controls.available)
            turnSignalLabel(controls.effectiveTurnSignal) else stringResource(R.string.unavailable_value)), compact)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TurnSignal.entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    pair.forEach { signal ->
                        VehicleChoice(turnSignalLabel(signal), controls.available && controls.effectiveTurnSignal == signal,
                            controls.available && controls.gear != VehicleGear.Park, compact,
                            { actions.onTurnSignal(signal) }, Modifier.weight(1f))
                    }
                }
            }
        }
        if (controls.available && controls.gear == VehicleGear.Park) {
            VehicleHint(stringResource(R.string.vehicle_park_hazard_active), compact)
            VehicleHint(stringResource(R.string.vehicle_park_signal_hint), compact)
        }
        VehicleHint(stringResource(R.string.vehicle_gear), compact)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            VehicleGear.entries.forEach { gear ->
                val label = stringResource(when (gear) {
                    VehicleGear.Park -> R.string.vehicle_gear_park
                    VehicleGear.Drive -> R.string.vehicle_gear_drive
                    VehicleGear.Reverse -> R.string.vehicle_gear_reverse
                })
                VehicleChoice(label, controls.available && controls.gear == gear,
                    controls.available && (!charging || gear == VehicleGear.Park), compact,
                    { actions.onGear(gear) }, Modifier.weight(1f))
            }
        }
        if (controls.reverseLightsOn) VehicleHint(stringResource(R.string.vehicle_reverse_white_active), compact)
        if (charging) VehicleHint(stringResource(R.string.vehicle_gear_charging_locked), compact)
        if (charging) VehicleHint(stringResource(R.string.vehicle_signal_charging_hint), compact)
        VehicleActionRow(stringResource(R.string.vehicle_brake_lights),
            stringResource(if (!controls.available) R.string.unavailable_value else if (controls.brakeLightsOn) R.string.vehicle_light_on else R.string.vehicle_light_off),
            VehicleIcon.Lights, controls.available, compact, selected = controls.available && controls.brakeLightsOn,
            onClick = { actions.onBrakeLights(!controls.brakeLightsOn) })
        VehicleActionRow(stringResource(R.string.vehicle_ambient_lights),
            stringResource(if (!controls.available) R.string.unavailable_value else if (controls.ambientLightsOn) R.string.vehicle_light_on else R.string.vehicle_light_off),
            VehicleIcon.Lights, controls.available, compact, selected = controls.available && controls.ambientLightsOn,
            onClick = { actions.onAmbientLights(!controls.ambientLightsOn) })
        VehicleHint(stringResource(R.string.vehicle_lights_hint), compact)
    }
}

@Composable
private fun turnSignalLabel(signal: TurnSignal): String = stringResource(when (signal) {
    TurnSignal.Off -> R.string.vehicle_light_off
    TurnSignal.Left -> R.string.vehicle_signal_left
    TurnSignal.Right -> R.string.vehicle_signal_right
    TurnSignal.Hazard -> R.string.vehicle_signal_hazard
})

@Composable
private fun VehicleChoice(label: String, selected: Boolean, enabled: Boolean, compact: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier) {
    VehicleSurface(modifier.height(if (compact) 28.dp else 34.dp)
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick), selected) {
        Text(label, Modifier.align(Alignment.Center).padding(horizontal = 2.dp), fontSize = if (compact) 9.sp else 11.sp,
            lineHeight = if (compact) 11.sp else 13.sp,
            color = if (selected) AetherColors.Accent else if (!enabled) AetherColors.Muted.copy(alpha = .55f) else AetherColors.TextSecondary,
            maxLines = 1)
    }
}

@Composable
private fun WindowsPanel(controls: VehicleControlsState, compact: Boolean, actions: VehicleActions, modifier: Modifier = Modifier) {
    VehiclePanel(stringResource(R.string.vehicle_windows), compact, modifier) {
        VehicleWindow.entries.forEach { window ->
            val label = stringResource(when (window) {
                VehicleWindow.FrontLeft -> R.string.vehicle_front_left_window
                VehicleWindow.FrontRight -> R.string.vehicle_front_right_window
                VehicleWindow.RearLeft -> R.string.vehicle_rear_left_window
                VehicleWindow.RearRight -> R.string.vehicle_rear_right_window
            })
            val reading = controls.windowOpenPercent[window].takeIf { controls.available }
            var draft by remember(window) { mutableFloatStateOf(0f) }
            var dragging by remember(window) { mutableStateOf(false) }
            val position = if (dragging) draft else reading?.toFloat() ?: 0f
            val description = if (reading != null) stringResource(R.string.vehicle_window_position, label, position.roundToInt())
                else stringResource(R.string.vehicle_part_state, label, stringResource(R.string.unavailable_value))
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Text(label, Modifier.weight(1f), color = AetherColors.TextSecondary, fontSize = if (compact) 10.sp else 12.sp,
                        lineHeight = if (compact) 12.sp else 14.sp)
                    Text(if (reading != null) stringResource(R.string.vehicle_window_percent, position.roundToInt()) else stringResource(R.string.unavailable_value),
                        color = AetherColors.Accent, fontSize = if (compact) 10.sp else 12.sp, lineHeight = if (compact) 12.sp else 14.sp)
                }
                Slider(value = position, onValueChange = { draft = it; dragging = true }, valueRange = 0f..100f,
                    onValueChangeFinished = {
                        val committed = draft.roundToInt()
                        dragging = false
                        actions.onWindowPosition(window, committed)
                    }, enabled = reading != null,
                    modifier = Modifier.fillMaxWidth().height(if (compact) 29.dp else 35.dp).semantics { contentDescription = description },
                    colors = SliderDefaults.colors(thumbColor = AetherColors.Accent, activeTrackColor = AetherColors.Accent,
                        inactiveTrackColor = AetherColors.Border))
            }
        }
        VehicleHint(stringResource(R.string.vehicle_window_model_hint), compact)
    }
}

@Composable
private fun TrunkPanel(controls: VehicleControlsState, compact: Boolean, actions: VehicleActions, modifier: Modifier = Modifier) {
    val open = VehiclePart.Trunk in controls.openParts
    VehiclePanel(stringResource(R.string.vehicle_trunk), compact, modifier) {
        VehicleGlyph(VehicleIcon.Trunk, size = if (compact) 44.dp else 64.dp,
            color = if (open) AetherColors.Accent else AetherColors.TextSecondary)
        VehicleActionRow(stringResource(if (open) R.string.vehicle_close_part else R.string.vehicle_open_part, stringResource(R.string.vehicle_trunk)),
            stringResource(if (!controls.available) R.string.unavailable_value else if (open) R.string.vehicle_open else R.string.vehicle_closed),
            VehicleIcon.Trunk, controls.available, compact, selected = open, onClick = { actions.onPartToggle(VehiclePart.Trunk) })
        VehicleHint(stringResource(R.string.vehicle_trunk_hint), compact)
    }
}

@Composable
private fun ChargingPanel(snapshot: VehicleSnapshot, compact: Boolean, actions: VehicleActions, modifier: Modifier = Modifier) {
    val charging = snapshot.vehicle.charging
    val parked = snapshot.controls.gear == VehicleGear.Park
    VehiclePanel(stringResource(R.string.vehicle_charging), compact, modifier) {
        VehicleBatteryCard(snapshot.vehicle, compact, modifier = Modifier.fillMaxWidth().height(if (compact) 68.dp else 86.dp))
        VehicleActionRow(stringResource(if (charging == true) R.string.vehicle_stop_charging else R.string.vehicle_start_charging),
            stringResource(when (charging) { true -> R.string.charging; false -> R.string.vehicle_not_charging; null -> R.string.unavailable_value }),
            VehicleIcon.Plug, snapshot.controls.available && charging != null && parked, compact, selected = charging == true,
            onClick = { actions.onChargingChange(charging != true) })
        val portOpen = VehiclePart.ChargePort in snapshot.controls.openParts
        VehicleActionRow(stringResource(R.string.vehicle_charge_port),
            stringResource(if (!snapshot.controls.available) R.string.unavailable_value else if (portOpen) R.string.vehicle_open else R.string.vehicle_closed),
            VehicleIcon.Plug, snapshot.controls.available && charging != true, compact,
            selected = snapshot.controls.available && portOpen, onClick = { actions.onPartToggle(VehiclePart.ChargePort) })
        if (!parked) VehicleHint(stringResource(R.string.vehicle_charging_park_required), compact)
        if (charging == true) {
            VehicleHint(stringResource(R.string.vehicle_battery_view_active), compact)
            VehicleHint(stringResource(R.string.vehicle_port_charging_locked), compact)
        } else VehicleHint(stringResource(R.string.vehicle_battery_view_hint), compact)
        VehicleHint(stringResource(R.string.vehicle_charging_hint), compact)
    }
}

@Composable
private fun VehicleActionRow(label: String, value: String, icon: VehicleIcon, enabled: Boolean, compact: Boolean,
    selected: Boolean = false, onClick: () -> Unit) {
    VehicleStatusTile(label, value, icon, compact, Modifier.fillMaxWidth().height(if (compact) 33.dp else 42.dp),
        selected = selected, enabled = enabled, onClick = onClick)
}

@Composable
private fun VehicleHint(text: String, compact: Boolean) {
    Text(text, color = AetherColors.TextSecondary, fontSize = if (compact) 9.sp else 11.sp,
        lineHeight = if (compact) 12.sp else 15.sp)
}

@Composable
private fun VehicleClimateDock(snapshot: VehicleSnapshot, actions: ClimateActions, compact: Boolean, modifier: Modifier = Modifier) {
    val state = snapshot.climate
    VehicleSurface(modifier) {
        Row(Modifier.fillMaxSize().padding(horizontal = if (compact) 7.dp else 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 9.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(false, true).forEach { passenger ->
                if (passenger) {
                    val auto = stringResource(R.string.auto_mode)
                    VehicleDockControl(auto, state.auto, state.available, compact, { actions.onToggle(ClimateControl.Auto) },
                        Modifier.width(if (compact) 30.dp else 42.dp)) {
                        ClimateGlyph(ClimateIcon.Fan, size = if (compact) 16.dp else 21.dp,
                            color = if (state.auto) AetherColors.Accent else AetherColors.TextSecondary)
                    }
                }
                val value = if (passenger) state.passengerCelsius else state.driverCelsius
                val label = stringResource(if (passenger) R.string.climate_passenger else R.string.climate_driver)
                val enabled = state.available && value != null && !(passenger && state.sync)
                val adjust = if (passenger) actions.onAdjustPassenger else actions.onAdjustDriver
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    ClimateStepButton(false, stringResource(R.string.climate_decrease_zone, label), enabled && value > state.minCelsius,
                        { adjust(-1) }, diameter = if (compact) 27.dp else 34.dp)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(climateTemperature(value), color = AetherColors.TextPrimary,
                            fontSize = if (compact) 16.sp else 21.sp, lineHeight = if (compact) 19.sp else 24.sp, maxLines = 1)
                        Text(label, color = if (passenger && state.sync) AetherColors.Accent else AetherColors.TextSecondary,
                            fontSize = if (compact) 8.sp else 10.sp, lineHeight = if (compact) 10.sp else 12.sp, maxLines = 1)
                    }
                    ClimateStepButton(true, stringResource(R.string.climate_increase_zone, label), enabled && value < state.maxCelsius,
                        { adjust(1) }, diameter = if (compact) 27.dp else 34.dp)
                }
            }
            Spacer(Modifier.height(if (compact) 28.dp else 38.dp).width(.7.dp).background(AetherColors.Border))
            val heatLabel = stringResource(R.string.vehicle_driver_heat, state.driverHeatingLevel, state.maxSeatLevel)
            VehicleDockControl(stringResource(R.string.vehicle_heat), state.driverHeatingLevel > 0, state.available, compact,
                { actions.onSeatLevel(SeatZone.Driver, SeatComfort.Heating) }, Modifier.width(if (compact) 29.dp else 39.dp), heatLabel) {
                ClimateGlyph(ClimateIcon.HeatSeat, size = if (compact) 17.dp else 22.dp,
                    color = if (state.driverHeatingLevel > 0) Color(0xFFFF6255) else AetherColors.TextSecondary)
            }
            val coolLabel = stringResource(R.string.vehicle_driver_cool, state.driverCoolingLevel, state.maxSeatLevel)
            VehicleDockControl(stringResource(R.string.vehicle_cool), state.driverCoolingLevel > 0, state.available, compact,
                { actions.onSeatLevel(SeatZone.Driver, SeatComfort.Cooling) }, Modifier.width(if (compact) 29.dp else 39.dp), coolLabel) {
                ClimateGlyph(ClimateIcon.CoolSeat, size = if (compact) 17.dp else 22.dp,
                    color = if (state.driverCoolingLevel > 0) AetherColors.Accent else AetherColors.TextSecondary)
            }
            listOf(ClimateControl.FrontDefrost to Glyph.Front, ClimateControl.RearDefrost to Glyph.Rear).forEach { (control, glyph) ->
                val label = stringResource(if (control == ClimateControl.FrontDefrost) R.string.vehicle_front else R.string.vehicle_rear)
                val description = stringResource(if (control == ClimateControl.FrontDefrost) R.string.front_defrost else R.string.rear_defrost)
                VehicleDockControl(label, state.isEnabled(control), state.available, compact, { actions.onToggle(control) },
                    Modifier.width(if (compact) 29.dp else 39.dp), description) {
                    AetherGlyph(glyph, size = if (compact) 17.dp else 22.dp,
                        color = if (state.isEnabled(control)) AetherColors.Accent else AetherColors.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun VehicleDockControl(label: String, selected: Boolean, enabled: Boolean, compact: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, description: String = label, icon: @Composable () -> Unit) {
    VehicleSurface(modifier.fillMaxHeight().toggleable(selected, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
        .semantics { contentDescription = description }, selected) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            icon()
            Text(label, color = if (selected) AetherColors.Accent else AetherColors.TextSecondary,
                fontSize = if (compact) 7.sp else 9.sp, lineHeight = if (compact) 9.sp else 11.sp, maxLines = 1)
        }
    }
}
