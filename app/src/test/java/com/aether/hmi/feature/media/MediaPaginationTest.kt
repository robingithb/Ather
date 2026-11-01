package com.aether.hmi.feature.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPaginationTest {
    @Test fun firstAndLastPagesExposeOnlyValidNavigation() {
        val first = MediaPagination.window(7, 3, 0)
        assertEquals(0 until 3, first.startIndex until first.endExclusive)
        assertFalse(first.hasPrevious)
        assertTrue(first.hasNext)
        val last = MediaPagination.window(7, 3, first.nextAnchor + 3)
        assertEquals(6 until 7, last.startIndex until last.endExclusive)
        assertTrue(last.hasPrevious)
        assertFalse(last.hasNext)
        assertEquals(3, last.previousAnchor)
    }

    @Test fun queueShrinkClampsAnOutdatedPageToTheLastRemainingItem() {
        val page = MediaPagination.window(3, 2, 100)
        assertEquals(1, page.pageIndex)
        assertEquals(2, page.startIndex)
        assertEquals(3, page.endExclusive)
        assertFalse(page.hasNext)
    }

    @Test fun resizingKeepsTheAnchoredItemInsideTheVisibleWindow() {
        val original = MediaPagination.window(20, 3, 6)
        val larger = MediaPagination.window(20, 5, original.startIndex)
        assertTrue(original.startIndex in larger.startIndex until larger.endExclusive)
        val smaller = MediaPagination.window(20, 2, original.startIndex)
        assertTrue(original.startIndex in smaller.startIndex until smaller.endExclusive)
        assertEquals(6, smaller.startIndex)
    }

    @Test fun clearingAFilterToAnEmptyResultHasOneEmptyPage() {
        val page = MediaPagination.window(0, 4, 12)
        assertEquals(1, page.pageCount)
        assertEquals(0, page.startIndex)
        assertEquals(0, page.endExclusive)
        assertFalse(page.hasPrevious)
        assertFalse(page.hasNext)
    }

    @Test fun invalidCapacityAndNegativeAnchorCannotLoseItems() {
        val page = MediaPagination.window(2, 0, -5)
        assertEquals(1, page.capacity)
        assertEquals(0, page.startIndex)
        assertEquals(1, page.endExclusive)
        assertEquals(2, page.pageCount)
    }

    @Test fun veryLargeQueuesCannotOverflowTheirPageBounds() {
        val page = MediaPagination.window(Int.MAX_VALUE, 2, Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE - 1, page.startIndex)
        assertEquals(Int.MAX_VALUE, page.endExclusive)
        assertFalse(page.hasNext)
    }
}

class MediaListGeometryTest {
    @Test fun rowsAndPageControlsFitAllShortWindowsAndFontScales() {
        for (height in listOf(0f, 24f, 64f, 96f, 140f, 240f, 500f)) {
            for (count in listOf(0, 1, 2, 8, 200)) for (scale in listOf(1f, 1.5f, 2f)) {
                val layout = MediaListGeometry.fit(height, count, true, scale)
                val page = MediaPagination.window(count, layout.capacity, 0)
                val allocated = (page.endExclusive - page.startIndex) * layout.rowHeight + layout.controlsHeight
                assertTrue("Allocated $allocated for height $height", allocated <= height + .001f)
                assertTrue(layout.rowHeight >= 0f && layout.controlsHeight >= 0f)
                if (page.hasNext) assertTrue("Pages reserve navigation", layout.controlsHeight > 0f || height == 0f)
            }
        }
    }

    @Test fun aShortPageKeepsOneTrackAndNavigationInsteadOfCreatingAScrollFallback() {
        val layout = MediaListGeometry.fit(96f, 200, true, 1.5f)
        assertEquals(1, layout.capacity)
        assertTrue(layout.rowHeight >= 48f)
        assertTrue(layout.controlsHeight >= 36f)
    }

    @Test fun aSingleTrackUsesTheWholeBudgetWithoutUnusedPageControls() {
        val layout = MediaListGeometry.fit(64f, 1, true, 1.5f)
        assertEquals(64f, layout.rowHeight, .001f)
        assertEquals(0f, layout.controlsHeight, .001f)
    }

    @Test fun invalidInputsRemainFiniteAndEmptyListsHaveNoNavigation() {
        assertEquals(MediaListGeometry(1, 0f, 0f), MediaListGeometry.fit(Float.NaN, 0, true, Float.NaN))
        val layout = MediaListGeometry.fit(-1f, 5, false, Float.POSITIVE_INFINITY)
        assertEquals(MediaListGeometry(1, 0f, 0f), layout)
    }
}
