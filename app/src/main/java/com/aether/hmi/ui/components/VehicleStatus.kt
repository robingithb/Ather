package com.aether.hmi.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aether.hmi.R
import com.aether.hmi.domain.VehicleAttention
import com.aether.hmi.domain.VehicleAttentionSeverity
import com.aether.hmi.domain.VehicleIssue
import com.aether.hmi.domain.VehiclePart
import com.aether.hmi.domain.VehicleSnapshot
import com.aether.hmi.domain.VehicleWindow
import com.aether.hmi.domain.vehicleAttention
import com.aether.hmi.ui.theme.AetherColors

@Composable
fun vehicleStatusText(snapshot: VehicleSnapshot, @StringRes goodLabel: Int = R.string.systems_good): String =
    vehicleStatusText(snapshot.vehicleAttention(), goodLabel)

@Composable
fun vehicleStatusText(attention: VehicleAttention, @StringRes goodLabel: Int = R.string.systems_good): String {
    val primary = when (val issue = attention.primaryIssue) {
        null -> stringResource(goodLabel)
        VehicleIssue.Unavailable -> stringResource(R.string.systems_unavailable)
        VehicleIssue.ServiceAttention -> stringResource(R.string.systems_attention)
        is VehicleIssue.OpenPart -> stringResource(R.string.vehicle_status_part_open, stringResource(when (issue.part) {
            VehiclePart.FrontLeftDoor -> R.string.vehicle_front_left
            VehiclePart.FrontRightDoor -> R.string.vehicle_front_right
            VehiclePart.RearLeftDoor -> R.string.vehicle_rear_left
            VehiclePart.RearRightDoor -> R.string.vehicle_rear_right
            VehiclePart.Trunk -> R.string.vehicle_trunk
            VehiclePart.Bonnet -> R.string.vehicle_bonnet
            VehiclePart.Sunroof -> R.string.vehicle_sunroof
            VehiclePart.ChargePort -> R.string.vehicle_charge_port
        }))
        is VehicleIssue.OpenWindow -> stringResource(R.string.vehicle_status_window_open, stringResource(when (issue.window) {
            VehicleWindow.FrontLeft -> R.string.vehicle_front_left_window
            VehicleWindow.FrontRight -> R.string.vehicle_front_right_window
            VehicleWindow.RearLeft -> R.string.vehicle_rear_left_window
            VehicleWindow.RearRight -> R.string.vehicle_rear_right_window
        }))
    }
    return if (attention.additionalIssueCount > 0) stringResource(R.string.vehicle_status_more, primary, attention.additionalIssueCount) else primary
}

fun vehicleStatusColor(attention: VehicleAttention, goodColor: Color = AetherColors.TextPrimary): Color = when (attention.severity) {
    VehicleAttentionSeverity.Good -> goodColor
    VehicleAttentionSeverity.Attention -> AetherColors.Warning
    VehicleAttentionSeverity.Unavailable -> AetherColors.Muted
}

/** Decorative badge; the adjacent status text supplies its accessible description. */
@Composable
fun VehicleStatusBadge(attention: VehicleAttention, modifier: Modifier = Modifier) {
    val color = vehicleStatusColor(attention, AetherColors.Good)
    Box(modifier.background(color, CircleShape), contentAlignment = Alignment.Center) {
        if (attention.severity == VehicleAttentionSeverity.Good) {
            AetherGlyph(Glyph.Check, modifier = Modifier.fillMaxSize(.7f), color = AetherColors.Background)
        } else Canvas(Modifier.fillMaxSize(.6f)) {
            val stroke = 1.6.dp.toPx()
            if (attention.severity == VehicleAttentionSeverity.Attention) {
                drawLine(AetherColors.Background, Offset(size.width * .5f, size.height * .12f),
                    Offset(size.width * .5f, size.height * .55f), stroke, StrokeCap.Round)
                drawCircle(AetherColors.Background, stroke / 2, Offset(size.width * .5f, size.height * .84f))
            } else drawLine(AetherColors.Background, Offset(size.width * .2f, size.height * .5f),
                Offset(size.width * .8f, size.height * .5f), stroke, StrokeCap.Round)
        }
    }
}
