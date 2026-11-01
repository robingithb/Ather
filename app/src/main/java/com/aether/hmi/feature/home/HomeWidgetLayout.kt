package com.aether.hmi.feature.home

internal data class HomeWidgetPage(val start: Int, val end: Int, val index: Int, val count: Int, val capacity: Int) {
    val previous: Int get() = (start - capacity).coerceAtLeast(0)
    val next: Int get() = end
}

internal object HomeWidgetLayout {
    const val pageCapacity = 2

    /** A vacant slot stays present instead of letting a lone widget expand. */
    fun slots(count: Int): List<Int?> = List(pageCapacity) { index -> index.takeIf { it < count } }
    fun page(count: Int, capacity: Int, anchor: Int): HomeWidgetPage {
        val size = capacity.coerceAtLeast(1)
        if (count <= 0) return HomeWidgetPage(0, 0, 0, 1, size)
        val pages = (count - 1) / size + 1
        val index = anchor.coerceIn(0, count - 1) / size
        val start = index * size
        return HomeWidgetPage(start, minOf(count, start + size), index, pages, size)
    }
}
