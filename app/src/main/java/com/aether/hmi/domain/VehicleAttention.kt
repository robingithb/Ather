package com.aether.hmi.domain

enum class VehicleAttentionSeverity { Good, Attention, Unavailable }

sealed interface VehicleIssue {
    data object Unavailable : VehicleIssue
    data object ServiceAttention : VehicleIssue
    data class OpenPart(val part: VehiclePart) : VehicleIssue
    data class OpenWindow(val window: VehicleWindow) : VehicleIssue
}

/** Operational notices are derived from the shared snapshot, never stored as a second state. */
data class VehicleAttention(val issues: List<VehicleIssue>) {
    val primaryIssue: VehicleIssue? get() = issues.firstOrNull()
    val additionalIssueCount: Int get() = (issues.size - 1).coerceAtLeast(0)
    val severity: VehicleAttentionSeverity get() = when {
        primaryIssue == VehicleIssue.Unavailable -> VehicleAttentionSeverity.Unavailable
        issues.isNotEmpty() -> VehicleAttentionSeverity.Attention
        else -> VehicleAttentionSeverity.Good
    }
}

fun VehicleSnapshot.vehicleAttention(): VehicleAttention {
    if (!controls.available || vehicle.source == DataSource.Unavailable || vehicle.health == VehicleHealth.Unavailable) {
        return VehicleAttention(listOf(VehicleIssue.Unavailable))
    }
    return VehicleAttention(buildList {
        if (vehicle.health == VehicleHealth.Attention) add(VehicleIssue.ServiceAttention)
        // Enum order gives a deterministic front/rear door, trunk, bonnet and roof priority.
        VehiclePart.entries.forEach { part ->
            if (part in controls.openParts && !(part == VehiclePart.ChargePort && vehicle.charging == true)) {
                add(VehicleIssue.OpenPart(part))
            }
        }
        VehicleWindow.entries.forEach { window ->
            if ((controls.windowOpenPercent[window] ?: 0) > 0) add(VehicleIssue.OpenWindow(window))
        }
    })
}
