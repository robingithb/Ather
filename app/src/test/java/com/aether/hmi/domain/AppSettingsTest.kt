package com.aether.hmi.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
    @Test fun `invalid saved preferences recover independently to valid values`() {
        val normalized = AppSettings(
            theme = AppTheme.Sport, brightness = Float.NaN, mediaVolume = Float.POSITIVE_INFINITY,
            navigationVolume = -.5f, routeType = "obsolete", ambientColor = -1L, accentColor = 0x6600DFFFL,
            avoidTolls = true, map3D = false,
        ).normalized()
        assertEquals(AppTheme.Sport, normalized.theme)
        assertEquals(.7f, normalized.brightness, 0f)
        assertEquals(.8f, normalized.mediaVolume, 0f)
        assertEquals(0f, normalized.navigationVolume, 0f)
        assertEquals("Balanced", normalized.routeType)
        assertEquals(0xFF00DFFFL, normalized.ambientColor)
        assertNull(normalized.accentColor)
        assertTrue(normalized.avoidTolls)
        assertEquals(false, normalized.map3D)
    }

    @Test fun `normalization clamps finite values and preserves complete valid preferences`() {
        val valid = AppSettings(theme = AppTheme.Arctic, brightness = .2f, mediaVolume = .4f,
            navigationVolume = .5f, routeType = "Eco", ambientColor = 0xFFDD7788L,
            accentColor = 0xFF223344L, autoBrightness = false, avoidFerries = true)
        assertEquals(valid, valid.normalized())
        val clamped = valid.copy(brightness = 3f, mediaVolume = -1f, navigationVolume = 2f).normalized()
        assertEquals(1f, clamped.brightness, 0f)
        assertEquals(0f, clamped.mediaVolume, 0f)
        assertEquals(1f, clamped.navigationVolume, 0f)
    }
}
