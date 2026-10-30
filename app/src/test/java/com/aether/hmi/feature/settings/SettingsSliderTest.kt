package com.aether.hmi.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSliderTest {
    @Test fun `visible track endpoints and center select exact values on compact panels`() {
        assertEquals(0f, settingsSliderFraction(3f, 66f, 3f), 0f)
        assertEquals(.5f, settingsSliderFraction(33f, 66f, 3f), 0f)
        assertEquals(1f, settingsSliderFraction(63f, 66f, 3f), 0f)
    }

    @Test fun `edge touches clamp and a temporarily collapsed track stays finite`() {
        assertEquals(0f, settingsSliderFraction(-10f, 66f, 3f), 0f)
        assertEquals(1f, settingsSliderFraction(100f, 66f, 3f), 0f)
        assertEquals(0f, settingsSliderFraction(0f, 0f, 3f), 0f)
    }
}
