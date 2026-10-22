package com.aether.hmi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aether.hmi.ui.theme.AetherColors

enum class Glyph(val path: String, val filled: Boolean = false) {
    Arrow("M12,2 L22,22 L12,17 L2,22 Z", true),
    Gps("M22,2 L15,23 L11,13 L1,9 Z", true),
    SignalBars("M2,17 L5,17 L5,23 L2,23 Z M8,12 L11,12 L11,23 L8,23 Z M14,7 L17,7 L17,23 L14,23 Z M20,2 L23,2 L23,23 L20,23 Z", true),
    Eco("M21,2 Q4,3 4,17 Q10,19 15,13 Q19,8 21,2 Z M2,23 L3,23 L17,6 L16,6 Z", true),
    Chevron("M9,5 L16,12 L9,19"),
    Check("M5,12 L10,17 L20,6"),
    Plus("M12,4 L12,20 M4,12 L20,12"),
    Minus("M4,12 L20,12"),
    Wifi("M2,8 Q12,0 22,8 M5,12 Q12,6 19,12 M8,16 Q12,12 16,16 M12,20 L12,20.1"),
    Signal("M3,20 L3,17 M8,20 L8,13 M13,20 L13,8 M18,20 L18,3"),
    Battery("M2,5 L19,5 L19,19 L2,19 Z M19,9 L22,9 L22,15 L19,15 M5,8 L15,8 L15,16 L5,16 Z"),
    Charge("M14,1 L4,14 L11,14 L9,23 L21,9 L13,9 Z", true),
    Leaf("M20,3 Q3,4 4,17 Q16,22 20,3 Z M3,22 Q8,13 17,7"),
    Park("M7,19 L7,5 L13,5 Q19,5 19,10 Q19,14 13,14 L7,14 M3,2 L22,2 L22,22 L3,22 Z"),
    Front("M3,15 L1,7 Q12,0 23,7 L21,15 M7,20 L8,9 M12,20 L12,8 M17,20 L16,9 M5,15 L19,15"),
    Rear("M2,4 L22,4 L22,18 L2,18 Z M7,22 L9,9 M12,22 L14,9 M17,22 L19,9"),
    Seat("M6,2 Q2,2 4,7 L7,16 L20,16 Q24,17 21,21 L7,21 Q4,21 3,18 L1,7 M10,4 L11,12 L20,12"),
    Play("M7,3 L21,12 L7,21 Z", true),
    Pause("M5,3 L10,3 L10,21 L5,21 Z M15,3 L20,3 L20,21 L15,21 Z", true),
    Previous("M4,3 L7,3 L7,21 L4,21 Z M21,3 L8,12 L21,21 Z", true),
    Next("M17,3 L20,3 L20,21 L17,21 Z M3,3 L16,12 L3,21 Z", true),
    Heart("M12,21 C8,17 2,14 2,8 C2,2 9,1 12,6 C15,1 22,2 22,8 C22,14 16,17 12,21 Z"),
    Queue("M2,4 L16,4 M2,10 L16,10 M2,16 L10,16 M19,8 L19,20 Q14,23 14,19 Q14,17 19,17"),
    Music("M9,5 L21,2 L21,17 Q21,22 16,21 Q12,18 18,16 L18,7 L12,9 L12,20 Q12,24 7,23 Q3,20 9,19 Z", true),
}

@Composable
fun AetherGlyph(glyph: Glyph, modifier: Modifier = Modifier, color: Color = AetherColors.Accent, size: Dp = 24.dp) {
    val path = remember(glyph) { PathParser().parsePathString(glyph.path).toPath() }
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, this.size.height / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            if (glyph.filled) drawPath(path, color)
            else drawPath(path, color, style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
