package com.aether.hmi.feature.climate

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

internal enum class ClimateIcon(val path: String, val filled: Boolean = false) {
    Person("M12,3 A3.2,3.2 0,1 0,12,9.4 A3.2,3.2 0,1 0,12,3 M5,21 L5,18 Q5,12 12,12 Q19,12 19,18 L19,21 Z"),
    Face("M17,2 A2.2,2.2 0,1 0,17,6.4 A2.2,2.2 0,1 0,17,2 M15,9 L12,15 L7,15 L3,22 M15,9 L13,18 L7,18 M2,8 L9,8 M7,6 L9,8 L7,10"),
    FaceFeet("M17,2 A2.2,2.2 0,1 0,17,6.4 A2.2,2.2 0,1 0,17,2 M15,9 L12,15 L7,15 L3,22 M15,9 L13,18 L7,18 M1,8 L8,8 M6,6 L8,8 L6,10 M1,17 L5,17 M3,15 L5,17 L3,19"),
    Feet("M17,2 A2.2,2.2 0,1 0,17,6.4 A2.2,2.2 0,1 0,17,2 M15,9 L12,15 L7,15 L3,22 M15,9 L13,18 L7,18 M1,17 L5,17 M3,15 L5,17 L3,19"),
    Fan("M11,11 C4,12 2,8 5,4 C8,1 12,3 11,11 Z M13,11 C12,4 16,2 20,5 C23,8 21,12 13,11 Z M13,13 C20,12 22,16 19,20 C16,23 12,21 13,13 Z M11,13 C12,20 8,22 4,19 C1,16 3,12 11,13 Z", true),
    Auto("M19,5 Q12,-1 5,5 Q1,10 4,17 M2,14 L4,18 L7,15 M5,20 Q12,25 20,18 Q24,12 20,6 M17,8 L20,5 L22,9 M8,16 L12,6 L16,16 M10,12 L14,12"),
    Snow("M12,1 L12,23 M2,6 L22,18 M2,18 L22,6 M9,3 L12,6 L15,3 M9,21 L12,18 L15,21 M2,10 L7,9 L6,4 M18,20 L17,15 L22,14 M2,14 L7,15 L6,20 M18,4 L17,9 L22,10"),
    Link("M9,15 L15,9 M7,14 L4,17 Q1,21 5,23 Q8,24 11,21 L15,17 Q17,14 14,11 M17,10 L20,7 Q23,3 19,1 Q16,0 13,3 L9,7 Q7,10 10,13"),
    Filter("M7,2 L17,2 L17,22 L7,22 Z M10,5 L10,19 M14,5 L14,19 M2,5 L3,6 M2,11 L3,12 M2,17 L3,18 M21,5 L22,6 M21,11 L22,12 M21,17 L22,18"),
    HeatSeat("M16,2 A1.8,1.8 0,1 0,16,5.6 A1.8,1.8 0,1 0,16,2 M15,8 L12,17 L7,17 L5,22 L16,22 L20,10 M4,8 Q7,10 4,12 Q1,14 4,16 M8,7 Q11,9 8,11 Q5,13 8,15 M12,6 Q15,8 12,10 Q9,12 12,14"),
    CoolSeat("M16,2 A1.8,1.8 0,1 0,16,5.6 A1.8,1.8 0,1 0,16,2 M15,8 L12,17 L7,17 L5,22 L16,22 L20,10 M5,8 L5,15 M1,10 L9,13 M1,13 L9,10"),
}

@Composable
internal fun ClimateGlyph(icon: ClimateIcon, modifier: Modifier = Modifier, size: Dp = 24.dp, color: Color = AetherColors.TextSecondary) {
    val path = remember(icon) { PathParser().parsePathString(icon.path).toPath() }
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, this.size.height / 24f, pivot = Offset.Zero) {
            if (icon.filled) drawPath(path, color)
            else drawPath(path, color, style = Stroke(1.55f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
