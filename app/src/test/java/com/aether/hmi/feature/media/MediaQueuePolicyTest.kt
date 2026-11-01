package com.aether.hmi.feature.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaQueuePolicyTest {
    @Test fun revokedDocumentBeforeCurrentTrackPreservesTrackAndPlayhead() {
        assertEquals(MediaQueuePolicy.RestoredPosition(1, 45_000),
            MediaQueuePolicy.restorePosition(2, 45_000, listOf(0, 2, 3)))
    }

    @Test fun revokedCurrentDocumentStartsNextAvailableTrackAtBeginning() {
        assertEquals(MediaQueuePolicy.RestoredPosition(1, 0),
            MediaQueuePolicy.restorePosition(1, 45_000, listOf(0, 2, 3)))
    }

    @Test fun unavailableTailReturnsToFirstAvailableTrack() {
        assertEquals(MediaQueuePolicy.RestoredPosition(0, 0),
            MediaQueuePolicy.restorePosition(4, 45_000, listOf(0, 2)))
    }

    @Test fun emptyOrNegativeSavedPositionCannotProduceInvalidPlayhead() {
        assertEquals(MediaQueuePolicy.RestoredPosition(0, 0),
            MediaQueuePolicy.restorePosition(1, 45_000, emptyList()))
        assertEquals(MediaQueuePolicy.RestoredPosition(0, 0),
            MediaQueuePolicy.restorePosition(0, -20, listOf(0)))
    }

    @Test fun upNextUsesThePlayersShuffleOrder() {
        val shuffledOrder = mapOf(0 to 3, 3 to 1, 1 to 2, 2 to -1)
        assertEquals(listOf(3, 1, 2), MediaQueuePolicy.upcomingIndices(0, 4) { shuffledOrder[it] ?: -1 })
    }

    @Test fun repeatAllWrapsWithoutListingCurrentTrackOrLoopingForever() {
        assertEquals(listOf(0, 1), MediaQueuePolicy.upcomingIndices(2, 3) { (it + 1) % 3 })
    }

    @Test fun repeatOneHasNoDifferentUpcomingTrack() {
        assertEquals(emptyList<Int>(), MediaQueuePolicy.upcomingIndices(1, 3) { it })
    }

    @Test fun emptyQueueAndInvalidTraversalAreBounded() {
        assertEquals(emptyList<Int>(), MediaQueuePolicy.upcomingIndices(-1, 0) { error("Must not traverse an empty queue") })
        assertEquals(emptyList<Int>(), MediaQueuePolicy.upcomingIndices(0, 2) { 5 })
    }
}
