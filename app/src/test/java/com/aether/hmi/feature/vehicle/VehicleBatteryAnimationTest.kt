package com.aether.hmi.feature.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleBatteryAnimationTest {
    @Test fun `fill starts empty reaches actual charge then holds before repeating`() {
        val actual = .68f
        assertEquals(0f, fill(0.0, actual), 0f)
        assertEquals(actual / 2f, fill(1.1, actual), .000001f)
        for (time in listOf(2.2, 2.5, 2.999)) assertEquals(actual, fill(time, actual), 0f)
        assertEquals(0f, fill(3.0, actual), 0f)
        assertEquals(fill(.7, actual), fill(3.7, actual), .000001f)
        assertEquals(fill(1.5, actual), fill(10.5, actual), .000001f)
    }

    @Test fun `charging sweep is monotonic while filling and never exceeds reported charge`() {
        for (actual in listOf(.01f, .25f, .68f, 1f)) {
            var previous = 0f
            for (step in 0..220) {
                val current = fill(step / 100.0, actual)
                assertTrue("charge=$actual step=$step", current >= previous && current <= actual)
                previous = current
            }
            assertEquals(actual, previous, 0f)
            for (step in 0..900) assertTrue(fill(step / 100.0, actual) in 0f..actual)
        }
    }

    @Test fun `unknown and nonfinite charge never fabricate a level`() {
        for (actual in listOf(null, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            for (reducedMotion in listOf(false, true)) {
                assertNull(VehicleBatteryAnimation.fillFraction(1.0, actual, reducedMotion))
            }
        }
    }

    @Test fun `empty charge stays empty and out of range charge is bounded`() {
        for (time in listOf(0.0, 1.1, 2.5, 3.0, 5.0)) {
            assertEquals(0f, fill(time, 0f), 0f)
            assertEquals(0f, fill(time, -1f), 0f)
            assertEquals(fill(time, 1f), fill(time, 2f), 0f)
        }
    }

    @Test fun `invalid or negative clocks safely start at empty without affecting reported charge`() {
        for (time in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals(0f, fill(time, .68f), 0f)
            assertEquals(.68f, VehicleBatteryAnimation.fillFraction(time, .68f, true)!!, 0f)
        }
        assertTrue(fill(Double.MAX_VALUE, .68f).isFinite())
    }

    @Test fun `reduced motion shows actual charge at every point in the cycle`() {
        for (actual in listOf(0f, .01f, .25f, .68f, 1f)) {
            for (time in listOf(0.0, .7, 1.1, 2.2, 2.999, 3.0, 17.4)) {
                assertEquals(actual, VehicleBatteryAnimation.fillFraction(time, actual, true)!!, 0f)
            }
        }
    }

    private fun fill(elapsedSeconds: Double, actualFraction: Float): Float =
        VehicleBatteryAnimation.fillFraction(elapsedSeconds, actualFraction, false)!!
}
