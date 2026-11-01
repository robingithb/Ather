package com.aether.hmi.feature.home

import org.junit.Assert.*
import org.junit.Test

class HomeWidgetLayoutTest {
    @Test fun `changing landscape size keeps an anchored widget on its page`() {
        for (anchor in 0..11) for (capacity in listOf(1, 2, 4)) {
            val page = HomeWidgetLayout.page(12, capacity, anchor)
            assertTrue(anchor in page.start until page.end)
            assertTrue(page.end - page.start <= capacity)
            if (page.index + 1 < page.count) assertEquals(page.end, page.next)
            if (page.index > 0) assertEquals(page.start - capacity, page.previous)
        }
        assertEquals(2, HomeWidgetLayout.pageCapacity)
    }
    @Test fun `a single widget retains a vacant second slot and pages never exceed two widgets`() {
        assertEquals(listOf(null, null), HomeWidgetLayout.slots(0))
        assertEquals(listOf(0, null), HomeWidgetLayout.slots(1))
        assertEquals(listOf(0, 1), HomeWidgetLayout.slots(2))
        for (count in 1..12) {
            val pages = HomeWidgetLayout.page(count, HomeWidgetLayout.pageCapacity, 0).count
            val seen = (0 until pages).flatMap { index ->
                val page = HomeWidgetLayout.page(count, HomeWidgetLayout.pageCapacity, index * 2)
                val occupied = HomeWidgetLayout.slots(page.end - page.start).filterNotNull()
                assertTrue(occupied.size <= 2)
                occupied.map { page.start + it }
            }
            assertEquals((0 until count).toList(), seen)
        }
    }

    @Test fun `removal clamps the page and an empty home never requests an invalid sublist`() {
        assertEquals(0, HomeWidgetLayout.page(0, 2, 20).start)
        assertEquals(0, HomeWidgetLayout.page(0, 2, 20).end)
        val page = HomeWidgetLayout.page(3, 2, 10)
        assertEquals(2, page.start)
        assertEquals(3, page.end)
    }
}
