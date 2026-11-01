package com.aether.hmi.feature.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPlayerGeometryTest {
    @Test fun artworkAndAllPlaybackSlotsFitActualLandscapeViewports() {
        // Includes shell deductions and a short player when an error banner is visible.
        val viewports = listOf(500f to 180f, 500f to 240f, 620f to 230f,
            800f to 390f, 1120f to 570f, 176f to 120f, 0f to 0f)
        for ((width, height) in viewports) for (hasMedia in listOf(true, false)) {
            val layout = MediaPlayerGeometry.fit(width, height, hasMedia)
            val detailsHeight = layout.metadata + layout.progress + layout.transport +
                layout.gap * if (hasMedia) 2 else 1
            val detailsWidth = width - layout.artwork - if (layout.artwork > 0f) layout.gap else 0f
            assertTrue("Details fit $width x $height", detailsHeight <= height + .001f)
            assertTrue("Cover fits $width x $height", layout.artwork <= height + .001f)
            assertTrue(layout.playButton <= layout.transport)
            assertTrue(layout.playButton + 2 * layout.smallButton <= detailsWidth + .001f)
            assertTrue(listOf(layout.artwork, layout.metadata, layout.progress, layout.transport,
                layout.gap, layout.playButton, layout.smallButton).all { it >= 0f && it.isFinite() })
        }
    }

    @Test fun shortDemoViewportKeepsSeekAndAllTransportTargetsVisible() {
        val layout = MediaPlayerGeometry.fit(500f, 180f, true)
        assertTrue(layout.metadata >= 48f)
        assertTrue(layout.progress >= 48f)
        assertTrue(layout.playButton >= 48f)
        assertTrue(layout.smallButton >= 48f)
    }

    @Test fun aRoomierDisplayGrowsArtworkAndControlsWithoutStretchingMetadata() {
        val compact = MediaPlayerGeometry.fit(500f, 180f, true)
        val roomy = MediaPlayerGeometry.fit(1100f, 560f, true)
        assertTrue(roomy.artwork > compact.artwork)
        assertTrue(roomy.playButton > compact.playButton)
        assertEquals(88f, roomy.playButton, .001f)
        assertEquals(76f, roomy.smallButton, .001f)
        assertTrue(roomy.metadata < 160f)
    }

    @Test fun invalidDimensionsCannotProduceInvalidLayoutSizes() {
        val layout = MediaPlayerGeometry.fit(Float.NaN, -20f, true)
        assertEquals(MediaPlayerGeometry(0f, 0f, 0f, 0f, 0f, 0f, 0f), layout)
    }
}
