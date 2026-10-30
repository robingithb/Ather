package com.aether.hmi.feature.vehicle

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
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
import com.aether.hmi.domain.HeadlightMode
import com.aether.hmi.domain.VehicleControlsState
import com.aether.hmi.domain.VehiclePart
import com.aether.hmi.domain.VehicleState
import com.aether.hmi.ui.components.AetherGlyph
import com.aether.hmi.ui.components.Glyph
import com.aether.hmi.ui.theme.AetherColors
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun VehicleSurface(modifier: Modifier = Modifier, selected: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.clip(shape)
        .background(Brush.linearGradient(listOf(AetherColors.Panel.copy(alpha = .95f), AetherColors.PanelDeep.copy(alpha = .95f))))
        .then(if (selected) Modifier.background(Brush.radialGradient(listOf(AetherColors.Accent.copy(alpha = .20f), Color.Transparent))) else Modifier)
        .border(if (selected) 1.dp else .6.dp, if (selected) AetherColors.Accent else AetherColors.Border, shape), content = content)
}

@Composable
internal fun VehicleBatteryCard(vehicle: VehicleState, compact: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    VehicleSurface(modifier.then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)) {
        Column(Modifier.fillMaxSize().padding(if (compact) 8.dp else 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.vehicle_battery), color = AetherColors.TextSecondary,
                fontSize = if (compact) 8.sp else 10.sp, lineHeight = if (compact) 10.sp else 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(vehicle.batteryPercent?.let { stringResource(R.string.battery_percent, it) } ?: stringResource(R.string.unavailable_value),
                    Modifier.weight(1f), fontSize = if (compact) 23.sp else 30.sp,
                    lineHeight = if (compact) 26.sp else 34.sp, color = AetherColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(if (compact) 20.dp else 27.dp).width(.6.dp).background(AetherColors.Border))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.vehicle_range), color = AetherColors.TextSecondary,
                        fontSize = if (compact) 8.sp else 10.sp, lineHeight = if (compact) 10.sp else 12.sp)
                    val locale = Locale.getDefault()
                    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
                    Text(vehicle.rangeKm?.let { stringResource(R.string.range_km, numbers.format(it)) } ?: stringResource(R.string.unavailable_value),
                        color = AetherColors.TextPrimary, fontSize = if (compact) 13.sp else 17.sp,
                        lineHeight = if (compact) 16.sp else 20.sp, maxLines = 1)
                }
                if (onClick != null) AetherGlyph(Glyph.Chevron, size = if (compact) 8.dp else 11.dp, color = AetherColors.TextSecondary)
            }
            Box(Modifier.fillMaxWidth().height(if (compact) 4.dp else 6.dp).clip(RoundedCornerShape(5.dp)).background(AetherColors.PanelRaised)) {
                vehicle.batteryPercent?.let {
                    Box(Modifier.fillMaxWidth((it / 100f).coerceIn(0f, 1f)).fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(AetherColors.Accent, lerp(AetherColors.Accent, AetherColors.TextPrimary, .25f)))))
                }
            }
        }
    }
}

@Composable
internal fun VehicleStatusTile(label: String, value: String, icon: VehicleIcon, compact: Boolean, modifier: Modifier = Modifier,
    selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    VehicleSurface(modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick), selected) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < 140.dp
        Row(Modifier.fillMaxSize().padding(horizontal = if (narrow) 5.dp else if (compact) 7.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (narrow) 4.dp else if (compact) 6.dp else 9.dp)) {
            VehicleGlyph(icon, size = if (narrow) 15.dp else if (compact) 20.dp else 27.dp, color = if (selected) AetherColors.Accent else AetherColors.TextSecondary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, color = AetherColors.TextSecondary, fontSize = if (compact) 8.sp else 10.sp,
                    lineHeight = if (compact) 10.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, color = if (selected) AetherColors.Accent else AetherColors.TextPrimary,
                    fontSize = if (compact) 10.sp else 13.sp, lineHeight = if (compact) 12.sp else 15.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!narrow) AetherGlyph(Glyph.Chevron, size = if (compact) 8.dp else 10.dp, color = AetherColors.TextSecondary)
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

/** Supplied transparent top view; rotate its south-facing nose north to match door labels. */
@Composable
internal fun VehicleDoorDiagram(controls: VehicleControlsState, compact: Boolean, onPartTap: (VehiclePart) -> Unit, modifier: Modifier = Modifier) {
    VehicleSurface(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // The supplied square PNG includes generous transparent side margins.
            // Scale its visible cutout, preserving the original pixels and aspect ratio.
            val fittedSide = minOf(maxWidth.value, maxHeight.value).coerceAtLeast(1f)
            val cutoutScale = minOf(
                maxWidth.value * .72f / (fittedSide * .506f),
                maxHeight.value * .84f / (fittedSide * .910f),
            )
            Box(Modifier.align(Alignment.Center).fillMaxWidth(.8f).fillMaxHeight(.82f)
                .background(Brush.radialGradient(listOf(AetherColors.Accent.copy(alpha = .16f), Color.Transparent))))
            Image(painterResource(R.drawable.vehicle_overview_top), contentDescription = null,
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().graphicsLayer {
                    rotationZ = 180f
                    scaleX = cutoutScale
                    scaleY = cutoutScale
                })
        }
        listOf(VehiclePart.FrontLeftDoor, VehiclePart.FrontRightDoor, VehiclePart.RearLeftDoor, VehiclePart.RearRightDoor, VehiclePart.Trunk).forEach { part ->
            val open = part in controls.openParts
            val label = vehiclePartLabel(part)
            val description = stringResource(R.string.vehicle_part_state, label,
                stringResource(if (!controls.available) R.string.unavailable_value else if (open) R.string.vehicle_open else R.string.vehicle_closed))
            val position = when (part) {
                VehiclePart.FrontLeftDoor -> Modifier.align(Alignment.CenterStart).offset(x = 3.dp, y = if (compact) (-17).dp else (-24).dp)
                VehiclePart.FrontRightDoor -> Modifier.align(Alignment.CenterEnd).offset(x = (-3).dp, y = if (compact) (-17).dp else (-24).dp)
                VehiclePart.RearLeftDoor -> Modifier.align(Alignment.CenterStart).offset(x = 3.dp, y = if (compact) 17.dp else 24.dp)
                VehiclePart.RearRightDoor -> Modifier.align(Alignment.CenterEnd).offset(x = (-3).dp, y = if (compact) 17.dp else 24.dp)
                VehiclePart.Trunk -> Modifier.align(Alignment.BottomCenter).offset(y = (-3).dp)
                else -> Modifier
            }
            VehicleSurface(position.size(if (compact) 20.dp else 25.dp)
                .clickable(enabled = controls.available, role = Role.Button) { onPartTap(part) }
                .semantics { contentDescription = description }, selected = open) {
                VehicleGlyph(if (controls.locked && !open) VehicleIcon.Lock else VehicleIcon.Door,
                    Modifier.align(Alignment.Center), size = if (compact) 11.dp else 14.dp,
                    color = if (open) AetherColors.Warning else AetherColors.TextSecondary)
            }
        }
    }
}
