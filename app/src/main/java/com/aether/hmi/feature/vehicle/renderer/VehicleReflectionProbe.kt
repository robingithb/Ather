package com.aether.hmi.feature.vehicle.renderer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** Projects a reflection ray onto the same mirrored cylinder used by VehicleScenery. */
internal object VehicleReflectionProbe {
    fun panoramaU(x: Double, z: Double): Double = abs(atan2(z, x)) / PI

    fun panoramaV(x: Double, y: Double, z: Double): Double {
        val radial = sqrt(x * x + z * z)
        if (radial < 1e-8) return if (y >= 0.0) 0.0 else .62
        val hitHeight = .8 + 24.0 * y / radial
        return (.62 * (12.0 - hitHeight) / 14.0).coerceIn(0.0, .62)
    }
}
