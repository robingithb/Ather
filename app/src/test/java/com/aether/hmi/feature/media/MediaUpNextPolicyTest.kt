package com.aether.hmi.feature.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUpNextPolicyTest {
    private val demos = (0 until 6).map { "demo:$it" }

    @Test fun emptyPlayerOffersPlayableRecommendationsWithoutQueueIndices() {
        val entries = MediaUpNextPolicy.entries(emptyList(), emptyList(), -1, demos)
        assertEquals((0 until 6).toList(), entries.map { it.demoIndex })
        assertTrue(entries.all { it.queueIndex == null })
    }

    @Test fun actualQueueOrderStaysAheadOfClearlySeparateSuggestions() {
        val entries = MediaUpNextPolicy.entries(listOf("current", "second", "third"), listOf(2, 1), 0, demos)
        assertEquals(listOf(2, 1), entries.take(2).map { it.queueIndex })
        assertEquals(listOf(0, 1, 2, 3), entries.drop(2).map { it.demoIndex })
    }

    @Test fun currentAndUpcomingDemosAreNotRepeatedAsSuggestions() {
        val entries = MediaUpNextPolicy.entries(listOf(demos[0], demos[1]), listOf(1), 0, demos)
        assertEquals(listOf(2, 3, 4, 5), entries.mapNotNull { it.demoIndex })
    }

    @Test fun reachingTheEndOfADemoQueueOffersOtherOriginalsAgain() {
        val entries = MediaUpNextPolicy.entries(demos, emptyList(), 5, demos)
        assertEquals(listOf(0, 1, 2, 3, 4), entries.map { it.demoIndex })
    }

    @Test fun longRealQueuesNeedNoFillerAndInvalidIndicesAreIgnored() {
        val queue = (0 until 10).map { "track:$it" }
        val entries = MediaUpNextPolicy.entries(queue, listOf(-1, 0, 3, 3, 8, 1, 2, 4, 7, 12), 0, demos)
        assertEquals(listOf(3, 8, 1, 2, 4, 7), entries.map { it.queueIndex })
        assertTrue(entries.all { it.demoIndex == null })
    }
}
