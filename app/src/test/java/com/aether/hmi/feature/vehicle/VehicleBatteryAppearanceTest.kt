package com.aether.hmi.feature.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleBatteryAppearanceTest {
    @Test fun `charge fraction follows actual telemetry including empty and full states`() {
        for ((percent, expected) in listOf(0 to 0f, 1 to .01f, 25 to .25f, 68 to .68f, 100 to 1f)) {
            assertEquals(expected, VehicleBatteryAppearance.fromPercent(percent).chargeFraction!!, .000001f)
        }
    }

    @Test fun `invalid percentages are bounded to the nearest supported charge level`() {
        assertEquals(VehicleBatteryAppearance.fromPercent(0), VehicleBatteryAppearance.fromPercent(Int.MIN_VALUE))
        assertEquals(VehicleBatteryAppearance.fromPercent(100), VehicleBatteryAppearance.fromPercent(Int.MAX_VALUE))
    }

    @Test fun `unknown charge stays neutral without a fabricated level or green emission`() {
        val unknown = VehicleBatteryAppearance.fromPercent(null)
        assertNull(unknown.chargeFraction)
        assertTrue(unknown.green <= unknown.blue)
        assertEquals(0f, unknown.emissionRed + unknown.emissionGreen + unknown.emissionBlue, 0f)
    }

    @Test fun `known charge increases restrained green appearance without exceeding material bounds`() {
        var previousGreen = 0f
        var previousEmission = -1f
        for (percent in 0..100) {
            val appearance = VehicleBatteryAppearance.fromPercent(percent)
            assertTrue(appearance.green > appearance.red)
            assertTrue(appearance.green > appearance.blue)
            assertTrue(appearance.green > previousGreen)
            assertTrue(appearance.emissionGreen > previousEmission)
            assertTrue(appearance.red in 0f..1f && appearance.green in 0f..1f && appearance.blue in 0f..1f)
            assertTrue(appearance.emissionGreen in 0f..2f)
            assertTrue(appearance.emissionGreen >= appearance.emissionRed && appearance.emissionGreen >= appearance.emissionBlue)
            previousGreen = appearance.green
            previousEmission = appearance.emissionGreen
        }
        val empty = VehicleBatteryAppearance.fromPercent(0)
        assertEquals(0f, empty.emissionRed + empty.emissionGreen + empty.emissionBlue, 0f)
    }
}
