package com.aether.hmi.feature.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehiclePageLayoutTest {
    private val viewports = listOf(500f to 250f, 532f to 270f, 672f to 310f, 852f to 490f, 1172f to 670f)

    @Test fun `the model and controls always own exactly half the available feature width`() {
        for ((width, height) in viewports) for (count in listOf(3, 4, 5, 8)) {
            val layout = vehicleControlGridLayout(width, height, count)
            assertEquals(width / 2f, layout.modelWidth, 0f)
            assertEquals(layout.modelWidth, layout.controlsWidth, 0f)
        }
    }

    @Test fun `all section and access cards fit one finite grid with compact physical targets`() {
        for ((width, height) in viewports) for (count in listOf(3, 4, 5, 8)) {
            val layout = vehicleControlGridLayout(width, height, count)
            assertTrue(layout.columns * layout.rows >= count)
            assertTrue(layout.cardWidth >= 40f)
            assertTrue(layout.cardHeight >= 40f)
            assertEquals(layout.controlsWidth,
                layout.padding * 2 + layout.columns * layout.cardWidth + (layout.columns - 1) * layout.gap, .001f)
            assertEquals(height, layout.padding * 2 + layout.headerHeight + layout.gap +
                layout.rows * layout.cardHeight + (layout.rows - 1) * layout.gap, .001f)
        }
    }

    @Test fun `dense access cards fit both text lines at font scale one point five`() {
        val layout = vehicleControlGridLayout(500f, 250f, 8)
        assertEquals(2, layout.columns)
        assertEquals(4, layout.rows)
        // Native text fitting can retain at least 10sp per line inside the 4dp card padding.
        assertTrue(layout.cardHeight - 8f >= 2 * 10f * 1.5f * 1.12f)
    }

    @Test fun `all four window sliders fit together in a two by two grid at enlarged font size`() {
        for ((width, height) in viewports) {
            val layout = vehicleControlGridLayout(width, height, 4, windows = true)
            assertEquals(2, layout.columns)
            assertEquals(2, layout.rows)
            val slider = if (layout.roomy) 64f else 48f
            // Two readable caption lines, a native touch slider, a 2dp gap and 2dp vertical padding.
            assertTrue(layout.cardHeight >= 2 * 10f * 1.5f * 1.12f + slider + 2f + 4f)
        }
    }
}
