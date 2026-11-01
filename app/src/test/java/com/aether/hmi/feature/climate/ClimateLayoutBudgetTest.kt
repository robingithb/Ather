package com.aether.hmi.feature.climate

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ClimateLayoutBudgetTest {
    @Test
    fun primaryControlsAndCabinFitTheLandscapeContentArea() {
        // Sizes include shell deductions from 640x320, 780x360, 960x540 and 1280x720.
        val contentSizes = listOf(500f to 260f, 532f to 270f, 660f to 304f, 836f to 476f, 1152f to 644f)
        for ((width, height) in contentSizes) for (fontScale in listOf(1f, 1.5f)) {
            val budget = climateLayoutBudget(width, height, fontScale)
            val upperHeight = height - budget.gapDp - budget.commandHeightDp
            val zoneWidth = (width - 2f * budget.gapDp) * .25f
            val fanWidth = (width - 4f * budget.gapDp) * 2.1f / 6.1f
            assertTrue("Temperature steps fit at $width x $height", zoneWidth - 16f >= 2f * budget.targetDp)
            assertTrue("Temperature readout keeps 48dp at $width x $height", upperHeight >= budget.targetDp + 100f)
            assertTrue("Fan value retains room between steps at $width x $height", fanWidth - 20f - 2f * budget.targetDp >= 28f)
            assertTrue("Cabin remains prominent at $width x $height", upperHeight >= 160f)
            assertTrue(budget.targetDp in 40f..76f)
        }
    }

    @Test
    fun inlineDeckKeepsAllFlowChoicesAtLeastFortyDpTall() {
        for ((width, height) in listOf(500f to 260f, 532f to 270f, 660f to 304f, 836f to 476f, 1152f to 644f)) {
            for (fontScale in listOf(1f, 1.5f)) {
                val main = climateLayoutBudget(width, height, fontScale)
                val upperHeight = height - main.gapDp - main.commandHeightDp
                val deck = climateDeckBudget(upperHeight)
                val bodyHeight = upperHeight - 2f * deck.paddingDp - deck.tabHeightDp - deck.gapDp
                val rowHeight = (bodyHeight - 2f * deck.gapDp) / 3f
                assertTrue("All six flow commands fit beside cabin at $width x $height", rowHeight >= 40f)
                val cabinWidth = (width - main.gapDp) * .46f
                assertTrue("Cabin stays visible while editing at $width x $height", cabinWidth >= 220f)
            }
        }
    }

    @Test
    fun temperatureRingShowsTheActualClampedTemperatureRange() {
        assertEquals(0f, temperatureFraction(16f, 16f, 30f), 0f)
        assertEquals(.5f, temperatureFraction(23f, 16f, 30f), 0f)
        assertEquals(1f, temperatureFraction(30f, 16f, 30f), 0f)
        assertEquals(0f, temperatureFraction(-30f, 16f, 30f), 0f)
        assertEquals(1f, temperatureFraction(90f, 16f, 30f), 0f)
        assertEquals(0f, temperatureFraction(null, 16f, 30f), 0f)
        assertEquals(0f, temperatureFraction(Float.NaN, 16f, 30f), 0f)
        assertEquals(0f, temperatureFraction(23f, 16f, 16f), 0f)
        assertEquals(.5f, temperatureFraction(0f, -Float.MAX_VALUE, Float.MAX_VALUE), .0001f)
    }
}
