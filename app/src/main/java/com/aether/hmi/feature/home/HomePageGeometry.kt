package com.aether.hmi.feature.home

import kotlin.math.min

internal data class HomePageGeometry(val header: Float, val gap: Float, val cards: Float) {
    companion object {
        fun fit(width: Float, height: Float): HomePageGeometry {
            val h = height.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
            val gap = min(12f, min(h * .025f, width.coerceAtLeast(0f) * .015f))
            val header = min(76f, h * .20f)
            return HomePageGeometry(header, gap, (h - header - gap).coerceAtLeast(0f))
        }
    }
}

internal data class HomeTransportGeometry(val control: Float) {
    companion object {
        fun fit(width: Float, height: Float): HomeTransportGeometry = HomeTransportGeometry(
            minOf(76f, width.coerceAtLeast(0f) / 3.4f, height.coerceAtLeast(0f) * .30f))
    }
}
