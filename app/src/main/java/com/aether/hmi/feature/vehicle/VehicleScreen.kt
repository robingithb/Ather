package com.aether.hmi.feature.vehicle

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.hmi.R
import com.aether.hmi.domain.HeadlightMode
import com.aether.hmi.domain.TurnSignal
import com.aether.hmi.domain.VehicleControlsState
import com.aether.hmi.domain.VehicleGear
import com.aether.hmi.domain.VehiclePart
import com.aether.hmi.domain.VehicleSnapshot
import com.aether.hmi.domain.VehicleWindow
import com.aether.hmi.feature.climate.ClimateActions
import com.aether.hmi.ui.components.PageAction
import com.aether.hmi.ui.components.PageText
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
    Access(R.string.vehicle_access, VehicleIcon.Door),
    Lights(R.string.vehicle_lights, VehicleIcon.Lights),
    Windows(R.string.vehicle_windows, VehicleIcon.Window),
    Charging(R.string.vehicle_charging, VehicleIcon.Plug),
    Demo(R.string.vehicle_demo_controls, VehicleIcon.Lights),
}

private enum class VehicleView { Controls, Sections, Headlights, Signals, Gear }

private data class VehicleGridEntry(
    val key: String,
    val label: String,
    val value: String,
    val icon: VehicleIcon,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val onClick: () -> Unit = {},
    val window: VehicleWindow? = null,
    val accessibilityLabel: String = label,
)

/** The 3D stage and its controls each own exactly half the feature viewport. */
@Suppress("UNUSED_PARAMETER") // Preserve the shell entry point; climate controls live on Climate.
@Composable
fun VehicleScreen(snapshot: VehicleSnapshot, actions: VehicleActions, climateActions: ClimateActions, modifier: Modifier = Modifier) {
    var section by rememberSaveable { mutableStateOf(VehicleSection.Overview) }
    var view by rememberSaveable { mutableStateOf(VehicleView.Controls) }
    var focusedPart by rememberSaveable { mutableStateOf<VehiclePart?>(null) }
    fun selectSection(next: VehicleSection) { section = next; focusedPart = null; view = VehicleView.Controls }
    fun togglePart(part: VehiclePart) {
        if (!snapshot.controls.available || (part == VehiclePart.ChargePort && snapshot.vehicle.charging == true)) return
        actions.onPartToggle(part)
        focusedPart = part
        view = VehicleView.Controls
        section = if (part == VehiclePart.ChargePort) VehicleSection.Charging else VehicleSection.Access
    }
    val focus = when (focusedPart) {
        VehiclePart.Bonnet -> VehicleSceneFocus.Bonnet
        VehiclePart.Sunroof -> VehicleSceneFocus.Sunroof
        VehiclePart.Trunk -> VehicleSceneFocus.Trunk
        else -> when (section) {
            VehicleSection.Overview -> VehicleSceneFocus.Overview
            VehicleSection.Access -> VehicleSceneFocus.Doors
            VehicleSection.Lights, VehicleSection.Demo -> VehicleSceneFocus.Lights
            VehicleSection.Windows -> VehicleSceneFocus.Windows
            VehicleSection.Charging -> VehicleSceneFocus.Charging
        }
    }
    val controls = snapshot.controls
    val unavailable = stringResource(R.string.unavailable_value)
    val open = stringResource(R.string.vehicle_open)
    val closed = stringResource(R.string.vehicle_closed)
    val on = stringResource(R.string.vehicle_light_on)
    val off = stringResource(R.string.vehicle_light_off)
    val focusedActions = actions.copy(onPartToggle = ::togglePart)
    val battery = VehicleGridEntry("battery", stringResource(R.string.vehicle_battery),
        stringResource(R.string.vehicle_energy_summary,
            snapshot.vehicle.batteryPercent?.let { stringResource(R.string.battery_percent, it) } ?: unavailable,
            snapshot.vehicle.rangeKm?.let { stringResource(R.string.range_km, it) } ?: unavailable),
        VehicleIcon.Plug, onClick = { selectSection(VehicleSection.Charging) })
    val lock = VehicleGridEntry("lock", stringResource(if (controls.locked) R.string.vehicle_unlock else R.string.vehicle_lock),
        if (!controls.available) unavailable else stringResource(if (controls.locked) R.string.vehicle_locked else R.string.vehicle_unlocked),
        if (controls.locked) VehicleIcon.Lock else VehicleIcon.Unlock, controls.available,
        onClick = { actions.onLockedChange(!controls.locked) })
    val partEntries = VehiclePart.entries.associateWith { part -> VehicleGridEntry(part.name, compactPartLabel(part),
        if (!controls.available) unavailable else if (part in controls.openParts) open else closed,
        if (part == VehiclePart.Trunk) VehicleIcon.Trunk else if (part == VehiclePart.ChargePort) VehicleIcon.Plug else VehicleIcon.Door,
        controls.available && !(part == VehiclePart.ChargePort && snapshot.vehicle.charging == true),
        controls.available && part in controls.openParts, onClick = { focusedActions.onPartToggle(part) }, accessibilityLabel = vehiclePartLabel(part)) }
    val controlsEntries = when (section) {
        VehicleSection.Overview -> listOf(battery, lock, partEntries.getValue(VehiclePart.Trunk))
        VehicleSection.Access -> listOf(lock) + listOf(VehiclePart.FrontLeftDoor, VehiclePart.FrontRightDoor,
            VehiclePart.RearLeftDoor, VehiclePart.RearRightDoor, VehiclePart.Trunk, VehiclePart.Bonnet, VehiclePart.Sunroof).map(partEntries::getValue)
        VehicleSection.Lights -> listOf(
            VehicleGridEntry("headlights", stringResource(R.string.vehicle_headlights), if (controls.available) headlightModeLabel(controls.headlightMode) else unavailable,
                VehicleIcon.Lights, controls.available, controls.headlightsOn, { view = VehicleView.Headlights }),
            VehicleGridEntry("ambient", stringResource(R.string.vehicle_ambient_lights), if (!controls.available) unavailable else if (controls.ambientLightsOn) on else off,
                VehicleIcon.Lights, controls.available, controls.ambientLightsOn, { actions.onAmbientLights(!controls.ambientLightsOn) }),
            VehicleGridEntry("demo", stringResource(R.string.vehicle_demo_controls), stringResource(R.string.vehicle_demo_controls_short),
                VehicleIcon.Lights, onClick = { selectSection(VehicleSection.Demo) }),
        )
        VehicleSection.Windows -> VehicleWindow.entries.map { window ->
            VehicleGridEntry(window.name, windowLabel(window), "", VehicleIcon.Window, window = window)
        }
        VehicleSection.Charging -> listOf(battery,
            VehicleGridEntry("charging", stringResource(if (snapshot.vehicle.charging == true) R.string.vehicle_stop_charging else R.string.vehicle_start_charging),
                if (!controls.available || snapshot.vehicle.charging == null) unavailable
                else if (controls.gear != VehicleGear.Park) stringResource(R.string.vehicle_park_required)
                else stringResource(if (snapshot.vehicle.charging == true) R.string.charging else R.string.vehicle_not_charging),
                VehicleIcon.Plug, controls.available && snapshot.vehicle.charging != null && controls.gear == VehicleGear.Park,
                snapshot.vehicle.charging == true, { actions.onChargingChange(snapshot.vehicle.charging != true) }),
            partEntries.getValue(VehiclePart.ChargePort))
        VehicleSection.Demo -> listOf(
            VehicleGridEntry("signals", stringResource(R.string.vehicle_signals), if (controls.available) turnSignalLabel(controls.effectiveTurnSignal) else unavailable,
                VehicleIcon.Lights, controls.available && controls.gear != VehicleGear.Park, onClick = { view = VehicleView.Signals }),
            VehicleGridEntry("gear", stringResource(R.string.vehicle_gear), if (controls.available) gearLabel(controls.gear) else unavailable,
                VehicleIcon.Overview, controls.available, onClick = { view = VehicleView.Gear }),
            VehicleGridEntry("brake-lamps", stringResource(R.string.vehicle_brake_lights), if (!controls.available) unavailable else if (controls.brakeLightsOn) on else off,
                VehicleIcon.Lights, controls.available, controls.brakeLightsOn, { actions.onBrakeLights(!controls.brakeLightsOn) }),
            VehicleGridEntry("parking-brake", stringResource(R.string.vehicle_parking_brake),
                stringResource(if (!controls.available) R.string.unavailable_value else if (controls.parkingBrakeEngaged) R.string.vehicle_brake_engaged else R.string.vehicle_brake_released),
                VehicleIcon.Brake, controls.available, controls.parkingBrakeEngaged, actions.onParkingBrakeToggle),
        )
    }
    val entries = when (view) {
        VehicleView.Controls -> controlsEntries
        VehicleView.Sections -> VehicleSection.entries.filter { it != VehicleSection.Demo }.map { item ->
            VehicleGridEntry(item.name, stringResource(item.label), "", item.icon, selected = section == item,
                onClick = { selectSection(item) })
        }
        VehicleView.Headlights -> HeadlightMode.entries.map { mode ->
            VehicleGridEntry(mode.name, headlightModeLabel(mode), "", VehicleIcon.Lights, controls.available,
                controls.headlightMode == mode, { actions.onHeadlightMode(mode); view = VehicleView.Controls })
        }
        VehicleView.Signals -> TurnSignal.entries.map { signal ->
            VehicleGridEntry(signal.name, turnSignalLabel(signal), "", VehicleIcon.Lights,
                controls.available && controls.gear != VehicleGear.Park, controls.effectiveTurnSignal == signal,
                { actions.onTurnSignal(signal); view = VehicleView.Controls })
        }
        VehicleView.Gear -> VehicleGear.entries.map { gear ->
            VehicleGridEntry(gear.name, gearLabel(gear), "", VehicleIcon.Overview,
                controls.available && (snapshot.vehicle.charging != true || gear == VehicleGear.Park), controls.gear == gear,
                { actions.onGear(gear); view = VehicleView.Controls })
        }
    }
    fun back() {
        if (view != VehicleView.Controls) view = VehicleView.Controls
        else if (section == VehicleSection.Demo) selectSection(VehicleSection.Lights)
        else view = VehicleView.Sections
    }
    BackHandler(enabled = view != VehicleView.Controls || section == VehicleSection.Demo, onBack = ::back)
    BoxWithConstraints(modifier) {
        val layout = vehicleControlGridLayout(maxWidth.value, maxHeight.value, entries.size,
            windows = view == VehicleView.Controls && section == VehicleSection.Windows)
        Row(Modifier.fillMaxSize()) {
            // Equal weights and no intervening gap preserve the exact 50/50 split at every width.
            Box(Modifier.weight(1f).fillMaxHeight()) {
                VehicleModelStage(controls, onPartTap = ::togglePart, modifier = Modifier.fillMaxSize(), focus = focus,
                    charging = snapshot.vehicle.charging == true, batteryPercent = snapshot.vehicle.batteryPercent)
                if (layout.roomy) Text(stringResource(R.string.vehicle_gesture_hint),
                    Modifier.align(Alignment.BottomCenter).padding(4.dp), color = AetherColors.TextSecondary,
                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(layout.padding.dp),
                verticalArrangement = Arrangement.spacedBy(layout.gap.dp)) {
                Row(Modifier.fillMaxWidth().height(layout.headerHeight.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(layout.gap.dp)) {
                    val menuAction = view == VehicleView.Controls && section != VehicleSection.Demo
                    PageAction(stringResource(if (menuAction) R.string.vehicle_menu else R.string.vehicle_back), ::back,
                        Modifier.width(if (layout.roomy) 100.dp else 76.dp).fillMaxHeight())
                    PageText(stringResource(when (view) {
                        VehicleView.Controls -> section.label
                        VehicleView.Sections -> R.string.vehicle_controls_menu
                        VehicleView.Headlights -> R.string.vehicle_headlights
                        VehicleView.Signals -> R.string.vehicle_signals
                        VehicleView.Gear -> R.string.vehicle_gear
                    }), Modifier.weight(1f).fillMaxHeight(), maxFontSize = if (layout.roomy) 24f else 18f,
                        minFontSize = 11f, fontWeight = FontWeight.SemiBold)
                }
                VehicleCardGrid(entries, controls, actions, layout, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun VehicleCardGrid(entries: List<VehicleGridEntry>, controls: VehicleControlsState,
    actions: VehicleActions, layout: VehicleControlGridLayout, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(layout.gap.dp)) {
        repeat(layout.rows) { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(layout.gap.dp)) {
                repeat(layout.columns) { column ->
                    val entry = entries.getOrNull(row * layout.columns + column)
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (entry != null) key(entry.key) {
                            if (entry.window != null) VehicleWindowControl(controls, entry.window, actions, layout.roomy, Modifier.fillMaxSize())
                            else VehicleStatusTile(entry.label, entry.value, entry.icon, Modifier.fillMaxSize(),
                                entry.selected, entry.enabled, layout.roomy, contentLabel = entry.accessibilityLabel, onClick = entry.onClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleWindowControl(controls: VehicleControlsState, window: VehicleWindow, actions: VehicleActions,
    roomy: Boolean, modifier: Modifier) {
    val reading = controls.windowOpenPercent[window].takeIf { controls.available }
    var draft by remember(window) { mutableFloatStateOf(0f) }
    var dragging by remember(window) { mutableStateOf(false) }
    val value = if (dragging) draft else reading?.toFloat() ?: 0f
    val label = windowLabel(window)
    val description = if (reading != null) stringResource(R.string.vehicle_window_position, label, value.roundToInt())
        else stringResource(R.string.vehicle_part_state, label, stringResource(R.string.unavailable_value))
    VehicleSurface(modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = if (roomy) 10.dp else 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val readingText = if (reading != null) stringResource(R.string.vehicle_window_percent, value.roundToInt())
                else stringResource(R.string.unavailable_value)
            PageText("$label\n$readingText", Modifier.weight(1f).fillMaxWidth(),
                maxFontSize = if (roomy) 18f else 14f, minFontSize = 10f, maxLines = 2,
                textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
            Slider(value = value, onValueChange = { draft = it; dragging = true }, valueRange = 0f..100f,
                onValueChangeFinished = { dragging = false; actions.onWindowPosition(window, draft.roundToInt()) }, enabled = reading != null,
                modifier = Modifier.fillMaxWidth().height(if (roomy) 64.dp else 48.dp).semantics { contentDescription = description },
                colors = SliderDefaults.colors(thumbColor = AetherColors.Accent, activeTrackColor = AetherColors.Accent, inactiveTrackColor = AetherColors.Border))
        }
    }
}

@Composable
private fun windowLabel(window: VehicleWindow): String = stringResource(when (window) {
    VehicleWindow.FrontLeft -> R.string.vehicle_front_left_window
    VehicleWindow.FrontRight -> R.string.vehicle_front_right_window
    VehicleWindow.RearLeft -> R.string.vehicle_rear_left_window
    VehicleWindow.RearRight -> R.string.vehicle_rear_right_window
})

@Composable
private fun gearLabel(gear: VehicleGear): String = stringResource(when (gear) {
    VehicleGear.Park -> R.string.vehicle_gear_park
    VehicleGear.Drive -> R.string.vehicle_gear_drive
    VehicleGear.Reverse -> R.string.vehicle_gear_reverse
})

@Composable
private fun turnSignalLabel(signal: TurnSignal): String = stringResource(when (signal) {
    TurnSignal.Off -> R.string.vehicle_light_off
    TurnSignal.Left -> R.string.vehicle_signal_left
    TurnSignal.Right -> R.string.vehicle_signal_right
    TurnSignal.Hazard -> R.string.vehicle_signal_hazard
})

@Composable
private fun compactPartLabel(part: VehiclePart): String = stringResource(when (part) {
    VehiclePart.FrontLeftDoor -> R.string.vehicle_front_left_window
    VehiclePart.FrontRightDoor -> R.string.vehicle_front_right_window
    VehiclePart.RearLeftDoor -> R.string.vehicle_rear_left_window
    VehiclePart.RearRightDoor -> R.string.vehicle_rear_right_window
    VehiclePart.Trunk -> R.string.vehicle_trunk
    VehiclePart.Bonnet -> R.string.vehicle_bonnet
    VehiclePart.Sunroof -> R.string.vehicle_sunroof
    VehiclePart.ChargePort -> R.string.vehicle_charge_port
})
