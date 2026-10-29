package com.aether.hmi.feature.home

import com.aether.hmi.domain.VehicleGear
import com.aether.hmi.domain.VehicleSnapshot

/** A presentation of shared telemetry, or an independent design-only selection for manual chips. */
internal data class HomeStatusChipState(val selected: Boolean, val readOnly: Boolean, val available: Boolean)

internal object HomeStatusChipPolicy {
    const val ChargingIndex = 4
    const val ParkedIndex = 6

    /** Stored manual bits never override the two automatic vehicle indicators. */
    fun state(snapshot: VehicleSnapshot, manualHighlights: Int, index: Int): HomeStatusChipState = when (index) {
        ChargingIndex -> {
            val available = snapshot.controls.available && snapshot.vehicle.charging != null
            HomeStatusChipState(available && snapshot.vehicle.charging == true, readOnly = true, available = available)
        }
        ParkedIndex -> HomeStatusChipState(
            selected = snapshot.controls.available && snapshot.controls.gear == VehicleGear.Park,
            readOnly = true,
            available = snapshot.controls.available,
        )
        else -> HomeStatusChipState(manualHighlights and (1 shl index) != 0, readOnly = false, available = true)
    }
}
