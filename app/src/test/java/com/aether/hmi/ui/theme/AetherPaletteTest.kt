package com.aether.hmi.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.aether.hmi.domain.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherPaletteTest {
    @Test fun `each theme changes surfaces and accent with readable primary and secondary text`() {
        val palettes = AppTheme.entries.map { themePalette(it) }
        assertEquals(4, palettes.map { it.Background }.distinct().size)
        assertEquals(4, palettes.map { it.Accent }.distinct().size)
        for (palette in palettes) {
            for (surface in listOf(palette.Background, palette.Panel, palette.PanelDeep, palette.PanelRaised, palette.Rail, palette.AccentSoft)) {
                assertTrue("${palette.theme} primary text", contrast(palette.TextPrimary, surface) >= 4.5f)
                assertTrue("${palette.theme} secondary text", contrast(palette.TextSecondary, surface) >= 4.5f)
                assertTrue("${palette.theme} accent text", contrast(palette.Accent, surface) >= 4.5f)
            }
            assertTrue("${palette.theme} filled controls", contrast(palette.OnAccent, palette.Accent) >= 4.5f)
        }
    }

    @Test fun `Arctic uses a white surface and dark text while other themes remain dark`() {
        val arctic = themePalette(AppTheme.Arctic)
        assertTrue(arctic.isLight)
        assertEquals(Color.White, arctic.Panel)
        assertTrue(arctic.Background.luminance() > .9f)
        assertTrue(arctic.TextPrimary.luminance() < .1f)
        AppTheme.entries.filter { it != AppTheme.Arctic }.forEach {
            val palette = themePalette(it)
            assertFalse(palette.isLight)
            assertTrue(palette.Background.luminance() < .05f)
        }
    }

    @Test fun `custom accent remains readable for each theme and does not leak between palettes`() {
        for (theme in AppTheme.entries) {
            val original = themePalette(theme)
            for (accent in listOf(0xFFFFFFFFL, 0xFF000000L, 0xFF00DFFFL, 0xFFFF535CL)) {
                val selected = themePalette(theme, accent)
                for (surface in listOf(selected.Background, selected.Panel, selected.PanelDeep,
                    selected.PanelRaised, selected.Rail, selected.AccentSoft)) {
                    assertTrue("$theme custom accent text", contrast(selected.Accent, surface) >= 4.5f)
                }
                assertTrue(contrast(selected.OnAccent, selected.Accent) >= 4.5f)
                assertEquals(original, themePalette(theme))
            }
        }
    }

    @Test fun `semantic badge icon contrast follows its fill independently of custom accents`() {
        for (theme in AppTheme.entries) {
            for (accent in listOf(0xFFFFFFFFL, 0xFF000000L)) {
                val palette = themePalette(theme, accent)
                for (fill in listOf(palette.Good, palette.Warning, palette.Muted)) {
                    assertTrue("$theme badge fill $fill", contrast(readableContentColor(fill), fill) >= 4.5f)
                }
            }
        }
    }

    private fun contrast(first: Color, second: Color): Float {
        val a = first.luminance()
        val b = second.luminance()
        return (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
    }
}
