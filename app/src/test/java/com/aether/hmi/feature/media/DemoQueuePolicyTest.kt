package com.aether.hmi.feature.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DemoQueuePolicyTest {
    private val demos = listOf("demo:mountain", "demo:coastal", "demo:evening", "demo:ocean", "demo:midnight", "demo:golden")

    @Test fun choosingAnOriginalInAnEmptyQueueLoadsTheCatalogueAtTheRequestedTrack() {
        assertEquals(DemoQueuePolicy.Plan(listOf(0, 1, 2, 3, 4, 5), 4),
            DemoQueuePolicy.plan(emptyList(), demos, 4))
    }

    @Test fun selectingARecommendationPreservesDocumentsAndTheirOrder() {
        val documents = listOf("content:road-trip", "content:podcast")
        val plan = DemoQueuePolicy.plan(documents, demos, 3)!!
        val resultingQueue = documents + plan.appendCatalogueIndices.map { demos[it] }
        assertEquals(documents, resultingQueue.take(documents.size))
        assertEquals("demo:ocean", resultingQueue[plan.playIndex])
    }

    @Test fun partiallyLoadedDemosAreNotDuplicatedAndExistingRecommendationIsReused() {
        val queue = listOf("content:document", demos[3], demos[0])
        assertEquals(DemoQueuePolicy.Plan(listOf(1, 2, 4, 5), 1), DemoQueuePolicy.plan(queue, demos, 3))
    }

    @Test fun selectingTheSameRecommendationAgainDoesNotAppendAnotherCatalogue() {
        val initial = listOf("content:document")
        val first = DemoQueuePolicy.plan(initial, demos, 2)!!
        val loaded = initial + first.appendCatalogueIndices.map { demos[it] }
        assertEquals(DemoQueuePolicy.Plan(emptyList(), 3), DemoQueuePolicy.plan(loaded, demos, 2))
    }

    @Test fun anInvalidRecommendationCannotReplaceOrExtendAQueue() {
        assertNull(DemoQueuePolicy.plan(listOf("content:document"), demos, -1))
        assertNull(DemoQueuePolicy.plan(listOf("content:document"), demos, demos.size))
    }
}
