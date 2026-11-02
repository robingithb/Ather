package com.aether.hmi.feature.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aether.hmi.R
import com.aether.hmi.domain.HeadlightMode
import com.aether.hmi.domain.VehiclePart
import com.aether.hmi.ui.components.PageText
import com.aether.hmi.ui.components.aetherRaised
import com.aether.hmi.ui.components.aetherRaisedClickable
import com.aether.hmi.ui.theme.AetherColors

@Composable
internal fun VehicleSurface(modifier: Modifier = Modifier, selected: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.aetherRaised(shape = shape, selected = selected, elevation = 2.dp), content = content)
}

@Composable
internal fun VehicleStatusTile(label: String, value: String, icon: VehicleIcon, modifier: Modifier = Modifier,
    selected: Boolean = false, enabled: Boolean = true, roomy: Boolean = false, contentLabel: String = label, onClick: () -> Unit) {
    val description = if (value.isEmpty()) contentLabel else "$contentLabel: $value"
    Box(modifier.aetherRaisedClickable(onClick = onClick, selected = selected, enabled = enabled)
        .semantics { contentDescription = description; this.selected = selected }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val dense = maxHeight < 64.dp
            val showIcon = maxHeight >= if (roomy) 112.dp else 88.dp
            val padding = if (dense) 4.dp else if (roomy) 10.dp else 6.dp
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (dense) 0.dp else 4.dp)) {
                if (showIcon) VehicleGlyph(icon, size = if (roomy) 32.dp else 24.dp,
                    color = if (!enabled) AetherColors.Muted else if (selected) AetherColors.Accent else AetherColors.TextSecondary)
                PageText(label, Modifier.weight(1f).fillMaxWidth(),
                    color = if (enabled) AetherColors.TextPrimary else AetherColors.Muted,
                    maxFontSize = if (roomy) 18f else 14f, minFontSize = if (dense) 10f else 11f,
                    textAlign = TextAlign.Center)
                if (value.isNotEmpty()) PageText(value, Modifier.weight(1f).fillMaxWidth(),
                    color = if (selected) AetherColors.Accent else AetherColors.TextSecondary,
                    maxFontSize = if (roomy) 15f else 12f, minFontSize = 10f, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
internal fun vehiclePartLabel(part: VehiclePart): String = stringResource(when (part) {
    VehiclePart.FrontLeftDoor -> R.string.vehicle_front_left
    VehiclePart.FrontRightDoor -> R.string.vehicle_front_right
    VehiclePart.RearLeftDoor -> R.string.vehicle_rear_left
    VehiclePart.RearRightDoor -> R.string.vehicle_rear_right
    VehiclePart.Trunk -> R.string.vehicle_trunk
    VehiclePart.Bonnet -> R.string.vehicle_bonnet
    VehiclePart.Sunroof -> R.string.vehicle_sunroof
    VehiclePart.ChargePort -> R.string.vehicle_charge_port
})

@Composable
internal fun headlightModeLabel(mode: HeadlightMode): String = stringResource(when (mode) {
    HeadlightMode.Off -> R.string.vehicle_light_off
    HeadlightMode.Auto -> R.string.vehicle_light_auto
    HeadlightMode.On -> R.string.vehicle_light_on
})
