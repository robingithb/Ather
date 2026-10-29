package com.aether.hmi.feature.vehicle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleShadowPolicyTest {
    @Test fun `exterior shell panels wheels and mirrors retain ground shadows`() {
        for (name in listOf("BODY", "Door_FL", "Door_FR", "Door_Trunk", "BONNET", "CHARGE_PORT", "Wheel_FR", "MIRROR_LEFT")) {
            assertTrue(name, VehicleShadowPolicy.castsShadows(name, true))
        }
    }

    @Test fun `mixed cabin parts and internal hardware do not cast shadows`() {
        for (name in listOf("BODY_INTERIOR", "Door_FL_INTERIOR", "Door_Trunk_INTERIOR",
            "DRIVETRAIN_BATTERY", "DRIVETRAIN_INVERTER", "DRIVETRAIN_SHAFT_FL", "STEERING_WHEEL", "DASH_KEYS")) {
            assertTrue(name, VehicleShadowPolicy.isInterior(name))
            assertFalse(name, VehicleShadowPolicy.castsShadows(name, true))
        }
    }

    @Test fun `transparent surfaces and optional lamp overlays cannot cast solid rectangles`() {
        assertFalse(VehicleShadowPolicy.castsShadows("SUNROOF.001", false))
        for (name in listOf("MIRROR_INDICATOR_L_ON", "HEADLAMP_R_OFF", "DRL_BAR")) {
            assertFalse(name, VehicleShadowPolicy.castsShadows(name, true))
        }
    }
}
