package com.aether.hmi.feature.media

import kotlin.math.abs
import kotlin.math.roundToLong

/** A committed scrub preview survives the next stale Media3 position emission. */
internal data class MediaPendingSeek(
    val targetPositionMs: Long,
    val positionBeforeSeekMs: Long,
    val requestedAtUptimeMs: Long,
)

internal object MediaSeekPolicy {
    // Controller position reports arrive asynchronously. Allow playback after a
    // seek lands, while recovering if the controller rejects the request.
    const val ARRIVAL_TOLERANCE_MS = 750L
    const val SETTLE_TIMEOUT_MS = 2_000L

    fun fraction(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0) 0f else
            (positionMs.coerceIn(0L, durationMs).toDouble() / durationMs.toDouble()).toFloat()

    fun targetPosition(fraction: Float, durationMs: Long): Long {
        if (durationMs <= 0) return 0L
        val bounded = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
        return (durationMs.toDouble() * bounded.toDouble()).roundToLong().coerceIn(0L, durationMs)
    }

    fun beginSeek(targetPositionMs: Long, observedPositionMs: Long, durationMs: Long,
        nowUptimeMs: Long): MediaPendingSeek {
        val duration = durationMs.coerceAtLeast(0L)
        return MediaPendingSeek(targetPositionMs.coerceIn(0L, duration),
            observedPositionMs.coerceIn(0L, duration), nowUptimeMs)
    }

    /** Use a monotonic uptime clock; reset the caller's pending state on media ID changes. */
    fun resolvePending(pending: MediaPendingSeek?, observedPositionMs: Long,
        nowUptimeMs: Long): MediaPendingSeek? {
        pending ?: return null
        if (nowUptimeMs - pending.requestedAtUptimeMs >= SETTLE_TIMEOUT_MS) return null
        val observed = observedPositionMs.coerceAtLeast(0L)
        val unchangedOldPosition = observed == pending.positionBeforeSeekMs &&
            observed != pending.targetPositionMs
        val atTarget = abs(observed.toDouble() - pending.targetPositionMs.toDouble()) <= ARRIVAL_TOLERANCE_MS
        return if (atTarget && !unchangedOldPosition) null else pending
    }
}
