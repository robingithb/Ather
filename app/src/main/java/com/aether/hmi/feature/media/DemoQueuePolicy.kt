package com.aether.hmi.feature.media

/** Preserve the user's queue when a bundled recommendation is chosen. */
internal object DemoQueuePolicy {
    data class Plan(val appendCatalogueIndices: List<Int>, val playIndex: Int)

    fun plan(existingIds: List<String>, catalogueIds: List<String>, requestedIndex: Int): Plan? {
        val requestedId = catalogueIds.getOrNull(requestedIndex) ?: return null
        val seen = existingIds.toMutableSet()
        val additions = catalogueIds.indices.filter { seen.add(catalogueIds[it]) }
        val existingIndex = existingIds.indexOf(requestedId)
        val playIndex = if (existingIndex >= 0) existingIndex else {
            existingIds.size + additions.indexOfFirst { catalogueIds[it] == requestedId }
        }
        return Plan(additions, playIndex)
    }
}
