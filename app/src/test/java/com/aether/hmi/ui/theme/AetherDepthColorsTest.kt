package com.aether.hmi.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.aether.hmi.domain.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherDepthColorsTest {
    @Test fun `raised faces retain label contrast through the entire gradient in every theme`() {
        for (theme in AppTheme.entries) for (accent in listOf(null, 0xFFFFFFFFL, 0xFF000000L, 0xFFFF535CL, 0xFF00DFFFL)) {
            val palette = themePalette(theme, accent)
            for (selected in listOf(false, true)) {
                val colors = aetherDepthColors(palette, selected, true)
                for (fraction in listOf(0f, .25f, .5f, .75f, 1f)) {
                    val face = lerp(colors.top, colors.bottom, fraction)
                    for (ink in listOf(palette.TextPrimary, palette.TextSecondary, palette.Accent)) {
                        assertTrue("$theme accent=$accent ink=$ink selected=$selected gradient=$fraction contrast=${contrast(ink, face)}", contrast(ink, face) >= 4.5f)
                    }
                }
            }
        }
    }

    @Test fun `Midnight raised faces stay neutral and disabled faces lose their active tint`() {
        val palette = themePalette(AppTheme.Midnight)
        val colors = aetherDepthColors(palette, false, true)
        for (color in listOf(colors.top, colors.bottom, colors.highlight, colors.lowerEdge)) {
            assertEquals(color.red, color.green, 0f)
            assertEquals(color.green, color.blue, 0f)
        }
        for (theme in AppTheme.entries) {
            val p = themePalette(theme)
            val disabled = aetherDepthColors(p, true, false, p.Accent)
            assertEquals(p.Panel, disabled.top)
            assertEquals(p.PanelDeep, disabled.bottom)
        }
    }

    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
}
