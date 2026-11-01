package com.aether.hmi.feature.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MediaSeekPolicyTest {
    @Test fun completedScrubKeepsPreviewWhileControllerStillReportsOldPosition() {
        val pending = MediaSeekPolicy.beginSeek(60_000, 10_000, 120_000, 1_000)
        assertSame(pending, MediaSeekPolicy.resolvePending(pending, 10_000, 1_500))
    }

    @Test fun nextPositionTickReleasesPreviewWhenSeekHasLanded() {
        val pending = MediaSeekPolicy.beginSeek(60_000, 10_000, 120_000, 1_000)
        assertNull(MediaSeekPolicy.resolvePending(pending, 60_500, 1_500))
    }

    @Test fun smallSeekDoesNotMistakeUnchangedOldPositionForAcknowledgement() {
        val pending = MediaSeekPolicy.beginSeek(10_200, 10_000, 120_000, 1_000)
        assertSame(pending, MediaSeekPolicy.resolvePending(pending, 10_000, 1_100))
        assertNull(MediaSeekPolicy.resolvePending(pending, 10_700, 1_500))
    }

    @Test fun rejectedSeekCannotLeavePreviewStuck() {
        val pending = MediaSeekPolicy.beginSeek(60_000, 10_000, 120_000, 1_000)
        assertSame(pending, MediaSeekPolicy.resolvePending(pending, 11_500, 2_999))
        assertNull(MediaSeekPolicy.resolvePending(pending, 12_000, 3_000))
    }

    @Test fun pausedSeekReleasesWhenControllerReportsExactTarget() {
        val pending = MediaSeekPolicy.beginSeek(40_000, 60_000, 120_000, 1_000)
        assertNull(MediaSeekPolicy.resolvePending(pending, 40_000, 1_050))
    }

    @Test fun fractionAndTargetStayWithinTrackForUnknownDurationAndOutOfRangeInput() {
        assertEquals(0f, MediaSeekPolicy.fraction(10_000, 0), 0f)
        assertEquals(0f, MediaSeekPolicy.fraction(-5, 100), 0f)
        assertEquals(1f, MediaSeekPolicy.fraction(150, 100), 0f)
        assertEquals(0L, MediaSeekPolicy.targetPosition(.5f, -1))
        assertEquals(0L, MediaSeekPolicy.targetPosition(-1f, 100))
        assertEquals(100L, MediaSeekPolicy.targetPosition(2f, 100))
    }

    @Test fun nonFiniteFractionsNeverReachMedia3AsInvalidTargets() {
        assertEquals(0L, MediaSeekPolicy.targetPosition(Float.NaN, 100))
        assertEquals(0L, MediaSeekPolicy.targetPosition(Float.NEGATIVE_INFINITY, 100))
        assertEquals(100L, MediaSeekPolicy.targetPosition(Float.POSITIVE_INFINITY, 100))
    }

    @Test fun longAudioFilesKeepMillisecondPrecisionWithoutLongMultiplicationOverflow() {
        assertEquals(3_600_000_000L, MediaSeekPolicy.targetPosition(.5f, 7_200_000_000L))
        assertEquals(.5f, MediaSeekPolicy.fraction(3_600_000_000L, 7_200_000_000L), 0f)
        assertEquals(Long.MAX_VALUE, MediaSeekPolicy.targetPosition(1f, Long.MAX_VALUE))
    }

    @Test fun pendingTargetAndOldPositionAreClampedToActualMediaDuration() {
        val pending = MediaSeekPolicy.beginSeek(10_000, -1_000, 5_000, 1_000)
        assertEquals(5_000L, pending.targetPositionMs)
        assertEquals(0L, pending.positionBeforeSeekMs)
    }
}
