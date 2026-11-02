package com.aether.hmi.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Face colors stay between existing palette surfaces so labels retain their contrast. */
internal data class AetherDepthColors(
    val top: Color,
    val bottom: Color,
    val highlight: Color,
    val lowerEdge: Color,
    val shadow: Color,
)

internal fun aetherDepthColors(palette: AetherPalette, selected: Boolean, enabled: Boolean,
    background: Color? = null): AetherDepthColors {
    val base = background?.compositeOver(palette.Panel) ?: if (selected) palette.AccentSoft else palette.Panel
    val shadedTop = when {
        !enabled -> palette.Panel
        background != null -> lerp(base, palette.PanelRaised, .08f)
        selected -> base
        palette.isLight -> palette.Panel
        else -> palette.PanelRaised
    }
    val shadedBottom = when {
        !enabled -> palette.PanelDeep
        selected || background != null -> lerp(base, palette.PanelDeep, .12f)
        palette.isLight -> lerp(palette.Panel, palette.PanelRaised, .65f)
        else -> palette.PanelDeep
    }
    // A custom accent can make AccentSoft too dark for Arctic's secondary labels.
    // Keep all standard label inks readable; decorative color swatches retain their hue.
    fun readableFace(face: Color): Color {
        if (!enabled || background != null) return face
        val inks = listOf(palette.TextPrimary, palette.TextSecondary, palette.Accent).map { it.luminance() }
        fun contrast(color: Color): Float {
            val light = color.luminance()
            return inks.minOf { (maxOf(it, light) + .05f) / (minOf(it, light) + .05f) }
        }
        if (contrast(face) >= 4.6f) return face
        val target = if (palette.isLight) palette.Panel else palette.PanelDeep
        var lower = 0f
        var upper = 1f
        repeat(16) {
            val amount = (lower + upper) / 2f
            if (contrast(lerp(face, target, amount)) >= 4.6f) upper = amount else lower = amount
        }
        return lerp(face, target, upper)
    }
    return AetherDepthColors(readableFace(shadedTop), readableFace(shadedBottom),
        highlight = when {
            !enabled -> palette.Border.copy(alpha = .35f)
            selected -> palette.Accent.copy(alpha = .75f)
            palette.isLight -> Color.White.copy(alpha = .95f)
            else -> lerp(palette.Border, palette.TextPrimary, .12f)
        },
        lowerEdge = if (selected && enabled) palette.Accent.copy(alpha = .3f)
            else if (palette.isLight) palette.Border.copy(alpha = .8f) else Color.Black.copy(alpha = .8f),
        shadow = Color.Black.copy(alpha = if (palette.isLight) .24f else .85f))
}
