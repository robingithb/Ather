package com.aether.hmi.feature.media

/** Pure queue rules shared by session restoration and the visible Up Next list. */
internal object MediaQueuePolicy {
    data class RestoredPosition(val index: Int, val positionMs: Long)

    /** Removing an unavailable URI must not move the playhead to an unrelated track. */
    fun restorePosition(savedIndex: Int, savedPositionMs: Long, retainedOriginalIndices: List<Int>): RestoredPosition {
        if (retainedOriginalIndices.isEmpty()) return RestoredPosition(0, 0)
        val exact = retainedOriginalIndices.indexOf(savedIndex)
        if (exact >= 0) return RestoredPosition(exact, savedPositionMs.coerceAtLeast(0))
        val next = retainedOriginalIndices.indexOfFirst { it > savedIndex }.takeIf { it >= 0 } ?: 0
        return RestoredPosition(next, 0)
    }

    /** The player supplies the traversal order, including its actual shuffle order. */
    fun upcomingIndices(currentIndex: Int, size: Int, nextIndex: (Int) -> Int): List<Int> {
        if (currentIndex !in 0 until size) return emptyList()
        val seen = mutableSetOf(currentIndex)
        val result = mutableListOf<Int>()
        var candidate = nextIndex(currentIndex)
        while (candidate in 0 until size && seen.add(candidate)) {
            result += candidate
            candidate = nextIndex(candidate)
        }
        return result
    }
}
