package com.aether.hmi.domain

import org.junit.Assert.*
import org.junit.Test

class HomeWidgetsTest {
    @Test fun `layout order and repeated widget instances survive storage round trip`() {
        val widgets = HomeWidgets.add(HomeWidgets.add(HomeWidgets.defaults, HomeWidgetKind.Climate), HomeWidgetKind.Music)
        val moved = HomeWidgets.move(widgets, widgets.last().id, -3)
        assertEquals(HomeWidgetKind.Music, moved.first().kind)
        assertEquals(moved, HomeWidgets.decode(HomeWidgets.encode(moved)))
        assertEquals(widgets.size, widgets.map { it.id }.distinct().size)
    }

    @Test fun `empty layouts remain empty and malformed entries recover without discarding valid widgets`() {
        assertTrue(HomeWidgets.decode("").isEmpty())
        assertEquals(listOf(HomeWidget(4, HomeWidgetKind.Energy)), HomeWidgets.decode("broken|4:Energy|4:Clock|-1:Music|7:Future"))
        assertEquals(HomeWidgets.defaults, HomeWidgets.decode("corrupt-only"))
    }

    @Test fun `bounds and extreme identifiers cannot corrupt ordering or create duplicate IDs`() {
        val initial = listOf(HomeWidget(Long.MAX_VALUE, HomeWidgetKind.Clock))
        val added = HomeWidgets.add(initial, HomeWidgetKind.Vehicle)
        assertEquals(2, added.map { it.id }.distinct().size)
        assertEquals(added, HomeWidgets.move(added, 999L, 1))
        val full = (0L..20L).map { HomeWidget(it, HomeWidgetKind.Music) }
        assertEquals(HomeWidgets.limit, HomeWidgets.normalize(full).size)
        assertEquals(HomeWidgets.limit, HomeWidgets.add(full, HomeWidgetKind.Climate).size)
    }

    @Test fun `theme and sound changes preserve home configuration`() {
        val settings = AppSettings(homeWidgets = listOf(HomeWidget(9, HomeWidgetKind.Energy)))
        assertEquals(settings.homeWidgets, settings.copy(theme = AppTheme.Arctic, mediaVolume = .2f).normalized().homeWidgets)
    }
}
