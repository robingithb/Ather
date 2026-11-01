package com.aether.hmi.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePageGeometryTest {
    @Test fun `header and both cards consume exactly one landscape page`() {
        for ((width, height) in listOf(500f to 250f, 532f to 270f, 652f to 316f, 836f to 476f, 1152f to 644f)) {
            val page = HomePageGeometry.fit(width, height)
            assertEquals(height, page.header + page.gap + page.cards, .001f)
            assertTrue(page.cards >= height * .70f)
            val mediaWidth = (width - page.gap) * .54f - 20f
            val mediaHeight = page.cards - 20f
            val controls = HomeTransportGeometry.fit(mediaWidth, mediaHeight)
            assertTrue("Three transport controls stay inside the card", controls.control * 3 <= mediaWidth)
            assertTrue("Metadata retains most of the card", controls.control <= mediaHeight * .31f)
            assertTrue("Compact transport remains usable", controls.control >= 40f)
        }
    }

    @Test fun `a temporarily collapsed viewport never allocates space outside its bounds`() {
        val page = HomePageGeometry.fit(0f, 0f)
        assertEquals(0f, page.cards + page.header + page.gap, 0f)
        assertEquals(0f, HomeTransportGeometry.fit(0f, 0f).control, 0f)
    }
}
