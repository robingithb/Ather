package com.aether.hmi.feature.vehicle.renderer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleSceneryThemeTest {
    private val midnight = VehicleSceneryTheme.from(0xFFE3E3E3.toInt(), 0xFF030303.toInt(), false)
    private val arctic = VehicleSceneryTheme.from(0xFF006C9A.toInt(), 0xFFF5F9FC.toInt(), true)
    private val sport = VehicleSceneryTheme.from(0xFFFF535C.toInt(), 0xFF100709.toInt(), false)
    private val minimal = VehicleSceneryTheme.from(0xFFD1DBDF.toInt(), 0xFF141719.toInt(), false)

    @Test fun `Arctic uses its own scenery surfaces and other themes use the right source`() {
        assertEquals(ScenerySurfaceMode.Arctic, arctic.mode)
        assertEquals(ScenerySurfaceMode.Midnight, midnight.mode)
        assertEquals(ScenerySurfaceMode.NeutralTint, sport.mode)
        assertEquals(ScenerySurfaceMode.NeutralTint, minimal.mode)
    }

    @Test fun `Arctic keeps snowy whites when users customize the accent`() {
        for (color in listOf(arctic.panorama, arctic.floor)) {
            assertTrue(color.red > .9f)
            assertEquals(color.red, color.green, 0f)
            assertEquals(color.green, color.blue, 0f)
        }
        val custom = VehicleSceneryTheme.from(0xFFFF535C.toInt(), 0xFFF5F9FC.toInt(), true)
        assertEquals(arctic.panorama, custom.panorama)
        assertEquals(arctic.floor, custom.floor)
    }

    @Test fun `every theme uses the same tint for the backdrop and matte floor`() {
        for (theme in listOf(midnight, arctic, sport, minimal,
            VehicleSceneryTheme.from(0xFF36D399.toInt(), 0xFF030303.toInt(), false),
            VehicleSceneryTheme.from(0xFF985CFF.toInt(), 0xFF141719.toInt(), false))) {
            assertEquals(theme.panorama, theme.floor)
        }
    }

    @Test fun `each theme exposes exactly one backdrop and floor without coplanar overlapping surfaces`() {
        val panoramas = listOf("AETHER_PANORAMA", "AETHER_NEUTRAL_PANORAMA", "AETHER_LIGHT_PANORAMA")
        val floors = listOf("AETHER_FLOOR", "AETHER_NEUTRAL_FLOOR", "AETHER_LIGHT_FLOOR")
        for (theme in listOf(midnight, arctic, sport, minimal)) {
            assertEquals(1, panoramas.count(theme::showsSurface))
            assertEquals(1, floors.count(theme::showsSurface))
            assertTrue(theme.showsSurface("AETHER_PLATFORM_RING"))
            assertTrue(theme.showsSurface("AETHER_CONTACT_SHADOW"))
        }
        assertFalse(arctic.showsSurface("AETHER_PANORAMA"))
        assertFalse(minimal.showsSurface("AETHER_PANORAMA"))
    }

    @Test fun `ring retains red blue and neutral hues without a near white radiance core`() {
        assertTrue(sport.ring.red > sport.ring.blue * 2f)
        assertTrue(sport.ring.red > sport.ring.green * 2f)
        assertEquals(midnight.ring.red, midnight.ring.green, 0f)
        assertEquals(midnight.ring.green, midnight.ring.blue, 0f)
        assertTrue(arctic.ring.blue > arctic.ring.red * 4f)
        assertTrue(maxOf(minimal.ring.red, minimal.ring.green, minimal.ring.blue) -
            minOf(minimal.ring.red, minimal.ring.green, minimal.ring.blue) < .025f)
        for (theme in listOf(midnight, arctic, sport, minimal)) {
            for (color in listOf(theme.glow, theme.ring, theme.core)) {
                assertTrue(minOf(color.red, color.green, color.blue) >= 0f)
                assertTrue(maxOf(color.red, color.green, color.blue) < .5f)
            }
        }
    }

    @Test fun `Midnight selects neutral dark scenery and restrained silver trim while retaining custom accents`() {
        assertFalse(midnight.showsSurface("AETHER_PANORAMA"))
        assertFalse(midnight.showsSurface("AETHER_FLOOR"))
        assertTrue(midnight.showsSurface("AETHER_NEUTRAL_PANORAMA"))
        assertTrue(midnight.showsSurface("AETHER_NEUTRAL_FLOOR"))
        for (color in listOf(midnight.panorama, midnight.floor, midnight.rim, midnight.glow, midnight.ring, midnight.core)) {
            assertEquals(color.red, color.green, 0f)
            assertEquals(color.green, color.blue, 0f)
        }
        assertTrue(midnight.panorama.red < minimal.panorama.red)
        assertTrue(midnight.floor.red < minimal.floor.red)
        assertTrue(midnight.ring.red < minimal.ring.red)
        val custom = VehicleSceneryTheme.from(0xFFFF535C.toInt(), 0xFF030303.toInt(), false)
        assertEquals(ScenerySurfaceMode.Midnight, custom.mode)
        assertTrue(custom.showsSurface("AETHER_NEUTRAL_PANORAMA"))
        assertTrue(custom.ring.red > custom.ring.blue * 2f)
    }

    @Test fun `Minimal scene colors are neutral instead of multiplying another tint into blue imagery`() {
        assertEquals(minimal.panorama.red, minimal.panorama.green, 0f)
        assertEquals(minimal.panorama.green, minimal.panorama.blue, 0f)
        assertEquals(minimal.floor.red, minimal.floor.green, 0f)
        assertEquals(minimal.floor.green, minimal.floor.blue, 0f)
        assertTrue(sport.panorama.red > sport.panorama.blue)
    }
}
