package com.aether.hmi.feature.media

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

internal enum class MediaIcon(val path: String, val filled: Boolean = false) {
    Equalizer("M3,13 L7,13 L7,22 L3,22 Z M10,3 L14,3 L14,22 L10,22 Z M17,9 L21,9 L21,22 L17,22 Z", true),
    Songs("M9,5 L21,2 L21,17 Q21,22 16,21 Q12,18 18,16 L18,7 L12,9 L12,20 Q12,24 7,23 Q3,20 9,19 Z", true),
    Album("M22,12 A10,10 0,1 1,2,12 A10,10 0,1 1,22,12 M14,12 A2,2 0,1 1,10,12 A2,2 0,1 1,14,12 M17,5 L19,8"),
    Artist("M16,6 A4,4 0,1 1,8,6 A4,4 0,1 1,16,6 M3,22 L3,19 Q3,12 12,12 Q21,12 21,19 L21,22 Z", true),
    Playlist("M2,3 L16,3 M2,9 L16,9 M2,15 L10,15 M19,9 L19,20 Q14,24 14,20 Q14,17 19,17 M19,9 L23,12"),
    Usb("M8,7 L8,2 L16,2 L16,7 M6,7 L18,7 L18,22 L6,22 Z M10,4 L10,5 M14,4 L14,5 M12,10 L12,19 M9,14 L15,14"),
    Bluetooth("M11,1 L20,7 L5,19 M11,1 L11,23 L20,17 L5,5"),
    Radio("M12,13 L6,23 M12,13 L18,23 M14,9 A2,2 0,1 1,10,9 A2,2 0,1 1,14,9 M7,3 Q1,9 7,15 M17,3 Q23,9 17,15 M3,0 Q-5,9 3,18 M21,0 Q29,9 21,18"),
    Shuffle("M2,5 L5,5 Q8,5 12,12 Q16,19 19,19 L22,19 M18,15 L22,19 L18,23 M2,19 L5,19 Q8,19 12,12 Q16,5 19,5 L22,5 M18,1 L22,5 L18,9"),
    Repeat("M5,6 L20,6 L16,2 M20,6 L16,10 M19,18 L4,18 L8,22 M4,18 L8,14 M22,9 Q25,18 19,18 M2,15 Q-1,6 5,6"),
}

@Composable
internal fun MediaGlyph(icon: MediaIcon, color: Color, size: Dp = 24.dp) {
    val path = remember(icon) { PathParser().parsePathString(icon.path).toPath() }
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 24f, this.size.height / 24f, pivot = Offset.Zero) {
            drawPath(path, color, style = if (icon.filled) androidx.compose.ui.graphics.drawscope.Fill
                else Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
