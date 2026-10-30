package com.aether.hmi.feature.climate

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.aether.hmi.domain.AppTheme
import com.aether.hmi.ui.theme.themePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimateAppearanceTest {
    @Test fun `temperature card contrast is independent of the image and moving airflow`() {
        val imagePixels = listOf(Color.Black, Color.White, Color.Red, Color.Cyan)
        for (theme in AppTheme.entries) {
            val palette = themePalette(theme)
            val appearance = climateAppearance(palette)
            for (surface in listOf(appearance.surfaceTop, appearance.surfaceBottom)) {
                for (pixel in imagePixels) {
                    val visibleSurface = surface.compositeOver(pixel)
                    assertEquals("$theme card must hide the image", surface, visibleSurface)
                    assertTrue("$theme primary card text", contrast(palette.TextPrimary, visibleSurface) >= 4.5f)
                    assertTrue("$theme secondary card text", contrast(palette.TextSecondary, visibleSurface) >= 4.5f)
                }
            }
        }
    }

    @Test fun `cabin vignette never becomes a light theme white veil`() {
        val midnight = climateAppearance(themePalette(AppTheme.Midnight))
        for (theme in AppTheme.entries) {
            val appearance = climateAppearance(themePalette(theme))
            assertEquals(midnight.cabinScrimTop, appearance.cabinScrimTop)
            assertEquals(midnight.cabinScrimBottom, appearance.cabinScrimBottom)
            assertEquals(0f, appearance.cabinScrimBottom.luminance(), .0001f)
            assertTrue(appearance.cabinScrimBottom.alpha <= .25f)
        }
    }

    @Test fun `airflow uses bright scene ink across light themes and custom accents`() {
        for (theme in AppTheme.entries) {
            for (accent in listOf(null, 0xFF007DFFL, 0xFFFF45B4L, 0xFF00DDA3L, 0xFF000000L, 0xFFFFFFFFL)) {
                val appearance = climateAppearance(themePalette(theme, accent))
                assertTrue("$theme $accent airflow must glow on the dark image", appearance.airflow.luminance() >= .499f)
                assertTrue("$theme $accent moving core edge", contrast(appearance.airflowHighlight, appearance.airflowSeparator) >= 7f)
            }
        }
        val arctic = themePalette(AppTheme.Arctic)
        assertTrue(climateAppearance(arctic).airflow.luminance() > arctic.Accent.luminance())
    }

    @Test fun `brightening preserves the selected airflow color family`() {
        val sport = climateAppearance(themePalette(AppTheme.Sport))
        assertTrue(sport.airflow.red > sport.airflow.green)
        assertTrue(sport.airflow.red > sport.airflow.blue)
        val green = climateAppearance(themePalette(AppTheme.Arctic, 0xFF00DDA3L))
        assertTrue(green.airflow.green > green.airflow.red)
        assertTrue(green.airflow.green > green.airflow.blue)
    }

    private fun contrast(first: Color, second: Color): Float {
        val a = first.luminance()
        val b = second.luminance()
        return (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
    }
}
