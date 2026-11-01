package com.aether.hmi.feature.media

internal data class MediaUpNextEntry(val queueIndex: Int? = null, val demoIndex: Int? = null)

/** Suggestions fill short queues without being silently inserted into the playback session. */
internal object MediaUpNextPolicy {
    fun entries(queueIds: List<String>, upcoming: List<Int>, currentIndex: Int,
        demoIds: List<String>, suggestedSize: Int = 6): List<MediaUpNextEntry> {
        val queued = upcoming.filter { it in queueIds.indices && it != currentIndex }.distinct()
        val excluded = queued.map { queueIds[it] }.toMutableSet()
        queueIds.getOrNull(currentIndex)?.let(excluded::add)
        val suggestions = demoIds.indices.filter { demoIds[it] !in excluded }
            .take((suggestedSize - queued.size).coerceAtLeast(0))
        return queued.map { MediaUpNextEntry(queueIndex = it) } +
            suggestions.map { MediaUpNextEntry(demoIndex = it) }
    }
}
