package com.aether.hmi.domain

/** App-owned widgets; each instance has a stable ID so the user's order survives restarts. */
enum class HomeWidgetKind { Navigation, Music, Climate, Vehicle, Energy, Clock, Settings }

data class HomeWidget(val id: Long, val kind: HomeWidgetKind)

object HomeWidgets {
    const val limit = 12
    val defaults = listOf(HomeWidget(1, HomeWidgetKind.Navigation), HomeWidget(2, HomeWidgetKind.Music))

    fun normalize(widgets: List<HomeWidget>): List<HomeWidget> = widgets.filter { it.id >= 0 }
        .distinctBy { it.id }.take(limit)

    fun add(widgets: List<HomeWidget>, kind: HomeWidgetKind): List<HomeWidget> {
        val current = normalize(widgets)
        if (current.size >= limit) return current
        val used = current.map { it.id }.toSet()
        val max = used.maxOrNull() ?: 0L
        val next = if (max < Long.MAX_VALUE) max + 1 else generateSequence(0L) { it + 1 }.first { it !in used }
        return current + HomeWidget(next, kind)
    }

    fun move(widgets: List<HomeWidget>, id: Long, offset: Int): List<HomeWidget> {
        val index = widgets.indexOfFirst { it.id == id }
        if (index < 0) return widgets
        val target = (index.toLong() + offset).coerceIn(0L, (widgets.size - 1).toLong()).toInt()
        return widgets.toMutableList().apply { add(target, removeAt(index)) }
    }

    fun encode(widgets: List<HomeWidget>): String = normalize(widgets).joinToString("|") { "${it.id}:${it.kind.name}" }

    /** An intentional empty layout stays empty; damaged/unsupported records recover independently. */
    fun decode(stored: String): List<HomeWidget> {
        if (stored.isEmpty()) return emptyList()
        val decoded = stored.split('|').mapNotNull { entry ->
            val pair = entry.split(':', limit = 2)
            val id = pair.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val kind = HomeWidgetKind.entries.firstOrNull { it.name == pair.getOrNull(1) } ?: return@mapNotNull null
            HomeWidget(id, kind)
        }
        return normalize(decoded).ifEmpty { defaults }
    }
}
