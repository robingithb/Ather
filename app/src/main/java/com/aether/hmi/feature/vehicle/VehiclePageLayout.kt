package com.aether.hmi.feature.vehicle

/** A finite grid inside the right half of the vehicle viewport; dimensions are dp. */
internal data class VehicleControlGridLayout(
    val modelWidth: Float,
    val controlsWidth: Float,
    val padding: Float,
    val headerHeight: Float,
    val gap: Float,
    val columns: Int,
    val rows: Int,
    val cardWidth: Float,
    val cardHeight: Float,
    val roomy: Boolean,
)

internal fun vehicleControlGridLayout(featureWidth: Float, featureHeight: Float, itemCount: Int,
    windows: Boolean = false): VehicleControlGridLayout {
    val half = featureWidth / 2f
    val roomy = featureWidth >= 840f && featureHeight >= 400f
    val padding = if (roomy) 12f else 6f
    val headerHeight = if (roomy) 64f else 40f
    val gap = if (roomy) 12f else 6f
    val contentWidth = (half - padding * 2).coerceAtLeast(0f)
    val contentHeight = (featureHeight - padding * 2 - headerHeight - gap).coerceAtLeast(0f)
    val count = itemCount.coerceAtLeast(1)
    val columns = when {
        windows -> 2
        count <= 3 -> 1
        count >= 7 && contentWidth >= 380f -> 3
        else -> 2
    }
    val rows = (count + columns - 1) / columns
    return VehicleControlGridLayout(half, half, padding, headerHeight, gap, columns, rows,
        ((contentWidth - (columns - 1) * gap) / columns).coerceAtLeast(0f),
        ((contentHeight - (rows - 1) * gap) / rows).coerceAtLeast(0f), roomy)
}
