package com.aether.hmi.feature.media

import kotlin.math.min

/** Dimensions in dp. Artwork sits beside three finite detail slots, never above the controls. */
internal data class MediaPlayerGeometry(
    val artwork: Float,
    val metadata: Float,
    val progress: Float,
    val transport: Float,
    val gap: Float,
    val playButton: Float,
    val smallButton: Float,
) {
    companion object {
        fun fit(width: Float, height: Float, hasMedia: Boolean): MediaPlayerGeometry {
            val boundedWidth = width.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
            val boundedHeight = height.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
            val detailsHeight = min(340f, boundedHeight)
            val gap = min(12f, detailsHeight * .025f)
            val available = (detailsHeight - gap * if (hasMedia) 2 else 1).coerceAtLeast(0f)
            val transport = min(88f, available * if (hasMedia) .36f else .46f)
            val progress = if (hasMedia) min(92f, available * .29f) else 0f
            val metadata = (available - transport - progress).coerceAtLeast(0f)
            val artwork = min(boundedHeight, min(boundedWidth * .38f,
                (boundedWidth - 180f - gap).coerceAtLeast(0f)))
            val detailsWidth = (boundedWidth - artwork - if (artwork > 0) gap else 0f).coerceAtLeast(0f)
            val play = min(88f, min(transport, detailsWidth / 3f))
            return MediaPlayerGeometry(artwork, metadata, progress, transport, gap, play,
                min(76f, play * .9f))
        }
    }
}
