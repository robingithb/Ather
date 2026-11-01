package com.aether.hmi.feature.home

/** Shared by touch dragging and the accessible move-to-page actions. */
internal object HomeWidgetDragPolicy {
    fun edgeDirection(x: Float, width: Float, edgeWidth: Float): Int {
        if (!x.isFinite() || !width.isFinite() || width <= 0f) return 0
        val edge = edgeWidth.coerceAtLeast(0f).coerceAtMost(width * .2f)
        return when {
            x < edge -> -1
            x > width - edge -> 1
            else -> 0
        }
    }

    fun targetPage(page: Int, count: Int, direction: Int): Int =
        (page + direction.coerceIn(-1, 1)).coerceIn(0, (count - 1).coerceAtLeast(0))

    fun dropIndex(count: Int, capacity: Int, page: Int, slot: Int): Int {
        if (count <= 0) return 0
        val size = capacity.coerceAtLeast(1)
        val lastPage = (count - 1) / size
        return (page.coerceIn(0, lastPage) * size + slot.coerceIn(0, size - 1)).coerceAtMost(count - 1)
    }
}
