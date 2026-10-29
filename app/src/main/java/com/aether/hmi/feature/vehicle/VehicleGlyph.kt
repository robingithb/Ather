package com.aether.hmi.feature.vehicle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aether.hmi.ui.theme.AetherColors

internal enum class VehicleIcon(val path: String) {
    Overview("M4,10 L6,4 L18,4 L20,10 M3,10 L21,10 L22,18 L2,18 Z M4,18 L4,21 M20,18 L20,21 M5,13 L8,14 M16,14 L19,13"),
    Door("M5,21 L5,10 L12,3 L20,3 L20,21 Z M7,10 L12,5 L18,5 L18,12 L7,12 M15,15 L18,15"),
    Lights("M12,6 A6,6 0,1 0,12,18 A6,6 0,1 0,12,6 M12,1 L12,3 M12,21 L12,23 M1,12 L3,12 M21,12 L23,12 M4,4 L5,5 M19,19 L20,20 M4,20 L5,19 M19,5 L20,4"),
    Window("M3,20 L3,10 L10,3 L21,3 L21,20 Z M5,10 L19,10 L19,5 L11,5 Z"),
    Trunk("M2,20 L21,20 L21,14 L17,10 L6,10 L3,14 M5,20 L5,22 M19,20 L19,22 M2,5 L11,5 Q15,5 16,1 M14,4 L21,4 L23,9"),
    Plug("M7,3 L7,9 M17,3 L17,9 M5,9 L19,9 L19,13 Q19,19 12,19 Q5,19 5,13 Z M12,19 L12,24"),
    Lock("M5,10 L19,10 L19,22 L5,22 Z M8,10 L8,6 Q8,1 12,1 Q16,1 16,6 L16,10 M12,14 L12,18"),
    Unlock("M5,10 L19,10 L19,22 L5,22 Z M8,10 L8,6 Q8,1 12,1 Q16,1 16,6 M12,14 L12,18"),
    Brake("M12,4 A8,8 0,1 0,12,20 A8,8 0,1 0,12,4 M8,17 L8,7 L13,7 Q18,7 18,11 Q18,14 13,14 L8,14 M2,4 Q-3,12 2,20 M22,4 Q27,12 22,20"),
}

@Composable
internal fun VehicleGlyph(icon: VehicleIcon, modifier: Modifier = Modifier, color: Color = AetherColors.TextSecondary, size: Dp = 24.dp) {
    val path = remember(icon) { PathParser().parsePathString(icon.path).toPath() }
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, this.size.height / 24f, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(1.55f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
