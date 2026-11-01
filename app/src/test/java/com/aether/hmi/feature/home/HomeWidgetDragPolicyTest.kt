package com.aether.hmi.feature.home

import com.aether.hmi.domain.HomeWidget
import com.aether.hmi.domain.HomeWidgetKind
import com.aether.hmi.domain.HomeWidgets
import org.junit.Assert.*
import org.junit.Test

class HomeWidgetDragPolicyTest {
    @Test fun `only the outside edge requests a page turn and pages never wrap`() {
        assertEquals(-1, HomeWidgetDragPolicy.edgeDirection(20f, 600f, 48f))
        assertEquals(1, HomeWidgetDragPolicy.edgeDirection(580f, 600f, 48f))
        for (x in listOf(48f, 300f, 552f)) assertEquals(0, HomeWidgetDragPolicy.edgeDirection(x, 600f, 48f))
        assertEquals(0, HomeWidgetDragPolicy.edgeDirection(0f, 0f, 48f))
        assertEquals(0, HomeWidgetDragPolicy.edgeDirection(Float.NaN, 600f, 48f))
        assertEquals(0, HomeWidgetDragPolicy.edgeDirection(50f, 100f, 48f))
        assertEquals(0, HomeWidgetDragPolicy.targetPage(0, 3, -1))
        assertEquals(1, HomeWidgetDragPolicy.targetPage(0, 3, 1))
        assertEquals(2, HomeWidgetDragPolicy.targetPage(2, 3, 1))
        assertEquals(0, HomeWidgetDragPolicy.targetPage(0, 1, 1))
    }

    @Test fun `cross page drops retain every widget and survive saved order round trips`() {
        for (capacity in listOf(1, 2, 4)) for (count in 2..12) {
            val widgets = (0 until count).map { HomeWidget(it.toLong(), HomeWidgetKind.Clock) }
            val lastPage = (count - 1) / capacity
            for (source in widgets.indices) for (page in 0..lastPage) for (slot in 0 until capacity) {
                val target = HomeWidgetDragPolicy.dropIndex(count, capacity, page, slot)
                val moved = HomeWidgets.move(widgets, widgets[source].id, target - source)
                assertEquals(widgets[source], moved[target])
                assertEquals(page, target / capacity)
                assertEquals(widgets.map { it.id }.toSet(), moved.map { it.id }.toSet())
                assertEquals(widgets.size, moved.size)
                assertEquals(moved, HomeWidgets.decode(HomeWidgets.encode(moved)))
            }
        }
    }

    @Test fun `partial last pages and removal clamp drop destinations`() {
        assertEquals(4, HomeWidgetDragPolicy.dropIndex(5, 2, 2, 1))
        assertEquals(2, HomeWidgetDragPolicy.dropIndex(3, 2, 9, 1))
        assertEquals(0, HomeWidgetDragPolicy.dropIndex(0, 2, 1, 1))
        assertEquals(0, HomeWidgetDragPolicy.dropIndex(3, 2, -1, -1))
    }
}
