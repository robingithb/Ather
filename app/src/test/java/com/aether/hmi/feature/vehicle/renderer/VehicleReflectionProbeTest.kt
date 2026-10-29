package com.aether.hmi.feature.vehicle.renderer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleReflectionProbeTest {
    @Test fun panoramaFollowsMirroredSceneryAtCardinalDirections() {
        assertEquals(0.0, VehicleReflectionProbe.panoramaU(1.0, 0.0), 1e-9)
        assertEquals(.5, VehicleReflectionProbe.panoramaU(0.0, 1.0), 1e-9)
        assertEquals(.5, VehicleReflectionProbe.panoramaU(0.0, -1.0), 1e-9)
        assertEquals(1.0, VehicleReflectionProbe.panoramaU(-1.0, 0.0), 1e-9)
    }

    @Test fun horizontalRayHitsSceneryAtBodyHeight() {
        assertEquals(.496, VehicleReflectionProbe.panoramaV(1.0, 0.0, 0.0), 1e-9)
        // The panorama's painted floor begins below .62 and must never reflect.
        assertEquals(.62, VehicleReflectionProbe.panoramaV(1.0, -1.0, 0.0), 1e-9)
        assertEquals(0.0, VehicleReflectionProbe.panoramaV(1.0, 1.0, 0.0), 1e-9)
    }

    @Test fun polesAndMirroredSeamsStayFiniteAndContinuous() {
        assertEquals(0.0, VehicleReflectionProbe.panoramaV(0.0, 1.0, 0.0), 1e-9)
        assertEquals(.62, VehicleReflectionProbe.panoramaV(0.0, -1.0, 0.0), 1e-9)
        assertEquals(VehicleReflectionProbe.panoramaU(-1.0, 1e-8),
            VehicleReflectionProbe.panoramaU(-1.0, -1e-8), 1e-9)
        for (y in -100..100) {
            val v = VehicleReflectionProbe.panoramaV(1.0, y / 100.0, 1.0)
            assertTrue(v.isFinite() && v in 0.0.. .62)
        }
    }
}
