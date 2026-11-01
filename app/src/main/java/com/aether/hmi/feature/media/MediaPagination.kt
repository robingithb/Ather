package com.aether.hmi.feature.media

/** A bounded window anchored to an item, so resizing keeps the visible region in view. */
internal data class MediaPageWindow(
    val pageIndex: Int,
    val pageCount: Int,
    val startIndex: Int,
    val endExclusive: Int,
    val capacity: Int,
) {
    val hasPrevious: Boolean get() = pageIndex > 0
    val hasNext: Boolean get() = pageIndex + 1 < pageCount
    val previousAnchor: Int get() = (startIndex - capacity).coerceAtLeast(0)
    val nextAnchor: Int get() = endExclusive
}

internal object MediaPagination {
    fun window(itemCount: Int, capacity: Int, anchor: Int): MediaPageWindow {
        val count = itemCount.coerceAtLeast(0)
        val size = capacity.coerceAtLeast(1)
        if (count == 0) return MediaPageWindow(0, 1, 0, 0, size)
        val pageCount = (count - 1) / size + 1
        val pageIndex = anchor.coerceIn(0, count - 1) / size
        val start = pageIndex * size
        val end = minOf(count.toLong(), start.toLong() + size).toInt()
        return MediaPageWindow(pageIndex, pageCount, start, end, size)
    }
}

/** A page always reserves its navigation before allocating rows, including very short windows. */
internal data class MediaListGeometry(val capacity: Int, val rowHeight: Float, val controlsHeight: Float) {
    companion object {
        fun fit(height: Float, itemCount: Int, compact: Boolean, fontScale: Float): MediaListGeometry {
            val boundedHeight = height.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
            if (itemCount <= 0) return MediaListGeometry(1, 0f, 0f)
            val preferred = (if (compact) 64f else 88f) *
                (fontScale.takeIf { it.isFinite() } ?: 1f).coerceIn(1f, 1.3f)
            val withoutControls = (boundedHeight / preferred).toInt().coerceAtLeast(1)
            val controls = if (itemCount > withoutControls)
                minOf(if (compact) 48f else 76f, boundedHeight * .38f) else 0f
            val listHeight = (boundedHeight - controls).coerceAtLeast(0f)
            val capacity = (listHeight / preferred).toInt().coerceAtLeast(1)
            return MediaListGeometry(capacity, minOf(preferred, listHeight / minOf(capacity, itemCount)), controls)
        }
    }
}
