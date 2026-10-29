package com.aether.hmi.feature.vehicle

import com.aether.hmi.domain.TurnSignal
import com.aether.hmi.domain.VehicleControlsState
import com.aether.hmi.domain.VehicleGear
import com.aether.hmi.domain.VehiclePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleLampPolicyTest {
    @Test fun `front and mirror unpowered lenses never cover active amber surfaces`() {
        for (side in listOf("L", "R")) {
            val indicator = if (side == "L") TurnSignal.Left else TurnSignal.Right
            val powered = VehicleControlsState(available = true, gear = VehicleGear.Drive, turnSignal = indicator)
            for (lens in listOf("FRONT_INDICATOR_$side", "MIRROR_INDICATOR_${side}_OFF")) {
                assertFalse(lens, visible(lens, powered, phase = true))
                assertTrue(lens, visible(lens, powered, phase = false))
                assertTrue(lens, visible(lens, powered.copy(turnSignal = TurnSignal.Off)))
            }
            assertTrue(visible("FRONT_INDICATOR_${side}_ON", powered, phase = true))
            assertTrue(visible("MIRROR_INDICATOR_${side}_ON", powered, phase = true))
        }
        assertTrue(VehicleLampPolicy.emissionGain("DRL_BAR") < VehicleLampPolicy.emissionGain("HEADLAMP_L_ON"))
    }

    private val off = VehicleControlsState(available = true, gear = VehicleGear.Drive)
    private val allOn = off.copy(headlightsOn = true, brakeLightsOn = true, ambientLightsOn = true,
        turnSignal = TurnSignal.Hazard, gear = VehicleGear.Reverse)

    @Test fun `regular geometry remains visible in normal and charging x-ray modes`() {
        val ordinaryNodes = listOf("BODY", "BONNET", "SUNROOF", "CHARGE_PORT", "Door_FL", "Door_Trunk",
            "Wheel_FR", "HEADLAMP_L", "FENDER_INDICATOR_R", "REAR_LAMP_CLUSTER_L", "TAILLAMP_R", "REAR_TAIL_BAR",
            "DRIVETRAIN_BATTERY", "DRIVETRAIN_INVERTER", "DRIVETRAIN_MOTOR_FRONT", "DRIVETRAIN_SHAFT_RR")
        ordinaryNodes.forEach { node ->
            assertTrue(node, visible(node, off))
            assertTrue(node, visible(node, allOn, charging = true, trunkOpen = true))
        }
    }

    @Test fun `headlight projector off surfaces are replaced only while powered LEDs are active`() {
        for (side in listOf("L", "R")) {
            val lens = "HEADLAMP_${side}_OFF"
            assertTrue(visible(lens, off))
            assertFalse(visible(lens, off.copy(headlightsOn = true)))
            assertTrue(visible(lens, off.copy(available = false, headlightsOn = true)))
            assertFalse(visible(lens, off, charging = true))
        }
    }

    @Test fun `headlights activate only authored LEDs daytime bar tail overlay and native headlamps`() {
        val headlampNodes = listOf("HEADLAMP_L_ON", "HEADLAMP_R_ON", "HEADLAMP_L_LIGHT", "HEADLAMP_R_LIGHT", "DRL_BAR", "REAR_TAIL_ON")
        headlampNodes.forEach { node ->
            assertFalse(node, visible(node, off))
            assertTrue(node, visible(node, off.copy(headlightsOn = true)))
            assertFalse(node, visible(node, off.copy(available = false, headlightsOn = true)))
        }
        assertFalse(visible("REAR_BRAKE_ON", off.copy(headlightsOn = true)))
        assertFalse(visible("REAR_REVERSE_ON_L", off.copy(headlightsOn = true)))
    }

    @Test fun `brake and reverse LEDs and their dark lenses follow independent control state`() {
        listOf("REAR_BRAKE_ON", "HIGH_STOP_ON", "REAR_BRAKE_LIGHT").forEach { node ->
            assertFalse(node, visible(node, off))
            assertTrue(node, visible(node, off.copy(brakeLightsOn = true)))
        }
        assertTrue(visible("HIGH_STOP_OFF", off))
        assertFalse(visible("HIGH_STOP_OFF", off.copy(brakeLightsOn = true)))
        for (side in listOf("L", "R")) {
            assertFalse(visible("REAR_REVERSE_ON_$side", off))
            assertTrue(visible("REAR_REVERSE_OFF_$side", off))
            val reverse = off.withGear(VehicleGear.Reverse)
            assertTrue(visible("REAR_REVERSE_ON_$side", reverse))
            assertFalse(visible("REAR_REVERSE_OFF_$side", reverse))
            assertFalse(visible("REAR_REVERSE_ON_$side", reverse.withGear(VehicleGear.Park)))
        }
    }

    @Test fun `left right and hazards flash all corresponding authored segments and restore dark lenses`() {
        for (signal in TurnSignal.entries) for (phase in listOf(false, true)) {
            val controls = off.withTurnSignal(signal)
            for (side in listOf("L", "R")) {
                val active = phase && (signal == TurnSignal.Hazard ||
                    (side == "L" && signal == TurnSignal.Left) || (side == "R" && signal == TurnSignal.Right))
                val nodes = listOf("FRONT_INDICATOR_${side}_ON", "FENDER_INDICATOR_${side}_ON", "MIRROR_INDICATOR_${side}_ON") +
                    (1..6).map { "REAR_TURN_${side}_ON_$it" }
                nodes.forEach { node -> assertEquals("$node signal=$signal phase=$phase", active, visible(node, controls, phase = phase)) }
                assertEquals(!active, visible("REAR_TURN_OFF_$side", controls, phase = phase))
            }
        }
    }

    @Test fun `Park hazards blink both sides including mirrors even with stored signal Off and in battery view`() {
        val parked = off.withGear(VehicleGear.Park)
        assertEquals(TurnSignal.Off, parked.turnSignal)
        val indicatorNodes = listOf("FRONT_INDICATOR_L_ON", "FRONT_INDICATOR_R_ON", "FENDER_INDICATOR_L_ON",
            "FENDER_INDICATOR_R_ON", "MIRROR_INDICATOR_L_ON", "MIRROR_INDICATOR_R_ON") +
            (1..6).flatMap { listOf("REAR_TURN_L_ON_$it", "REAR_TURN_R_ON_$it") }
        for (charging in listOf(false, true)) indicatorNodes.forEach { node ->
            assertTrue(node, VehicleLampPolicy.isIndicatorNode(node))
            assertTrue(node, visible(node, parked, charging = charging, phase = true))
            assertFalse(node, visible(node, parked, charging = charging, phase = false))
            assertFalse(node, visible(node, parked.copy(available = false), charging = charging))
        }
        for (side in listOf("L", "R")) {
            assertFalse(visible("REAR_REVERSE_ON_$side", parked))
            assertTrue(visible("REAR_REVERSE_OFF_$side", parked))
        }
    }

    @Test fun `original rear overlays retain independent tail and brake states without replacement surface families`() {
        for (available in listOf(false, true)) for (headlights in listOf(false, true))
            for (brake in listOf(false, true)) for (trunkOpen in listOf(false, true)) {
                val controls = off.copy(available = available, headlightsOn = headlights, brakeLightsOn = brake)
                assertEquals(available && headlights && !trunkOpen, visible("REAR_TAIL_ON", controls, trunkOpen = trunkOpen))
                assertEquals(available && brake && !trunkOpen, visible("REAR_BRAKE_ON", controls, trunkOpen = trunkOpen))
                assertEquals(available && brake, visible("HIGH_STOP_ON", controls, trunkOpen = trunkOpen))
                for (node in listOf("REAR_TAIL_ON", "REAR_BRAKE_ON", "HIGH_STOP_ON")) {
                    assertFalse(node, visible(node, controls, charging = true, trunkOpen = trunkOpen))
                }
            }
    }

    @Test fun `mixed rear overlays hide with an open trunk while attached lights and indicator lenses remain usable`() {
        assertFalse(visible("REAR_BRAKE_ON", allOn, trunkOpen = true))
        assertFalse(visible("REAR_TAIL_ON", off.copy(headlightsOn = true), trunkOpen = true))
        assertFalse(visible("REAR_BRAKE_GLOW_ON", allOn, trunkOpen = true))
        assertTrue(visible("HIGH_STOP_ON", allOn, trunkOpen = true))
        assertTrue(visible("REAR_TURN_L_ON_6", allOn, trunkOpen = true))
        assertTrue(visible("REAR_TURN_L_ON_5", allOn, trunkOpen = true))
        assertTrue(visible("REAR_TURN_R_ON_5", allOn, trunkOpen = true))
        assertTrue(visible("REAR_REVERSE_ON_R", allOn, trunkOpen = true))
        // NEW exports outer lenses on the quarter panel and inner lenses on the trunk.
        // Both remain visible in their dark state even while the trunk is moving.
        for (side in listOf("L", "R")) for (prefix in listOf("REAR_TURN_OFF_", "REAR_TURN_INNER_OFF_")) {
            assertTrue(visible(prefix + side, off, trunkOpen = true))
            assertFalse(visible(prefix + side, allOn, trunkOpen = true))
        }
    }

    @Test fun `billboard glows stay hidden while ambient LED strips follow only ambient lighting`() {
        val glowNodes = listOf("AMBIENT_GLOW_ON_Xn", "AMBIENT_GLOW_ON_Zp", "HEADLAMP_L_GLOW_ON", "HIGH_STOP_GLOW_ON",
            "REAR_BRAKE_GLOW_ON", "FRONT_INDICATOR_L_GLOW_ON", "FENDER_INDICATOR_R_GLOW_ON", "REAR_REVERSE_GLOW_ON_R") +
            (1..6).flatMap { listOf("REAR_TURN_L_GLOW_ON_$it", "REAR_TURN_R_GLOW_ON_$it") }
        glowNodes.forEach { assertFalse(it, visible(it, allOn)) }
        for (suffix in listOf("Xn", "Yn", "Zp")) {
            val node = "AMBIENT_STRIP_ON_$suffix"
            assertFalse(visible(node, off.copy(headlightsOn = true)))
            assertTrue(visible(node, off.copy(ambientLightsOn = true)))
        }
        assertFalse(visible("OPTIONAL_UNKNOWN_ON", allOn))
    }

    @Test fun `charge ring uses white for open idle port blue during charging and never green`() {
        val openPort = off.copy(openParts = setOf(VehiclePart.ChargePort))
        assertTrue(visible("CHARGE_PORT_RING_OFF", off))
        assertFalse(visible("CHARGE_PORT_RING_ON_WHITE", off))
        assertFalse(visible("CHARGE_PORT_RING_OFF", openPort))
        assertTrue(visible("CHARGE_PORT_RING_ON_WHITE", openPort))
        assertFalse(visible("CHARGE_PORT_RING_ON_BLUE", openPort))
        assertTrue(visible("CHARGE_PORT_RING_ON_BLUE", openPort, charging = true))
        assertFalse(visible("CHARGE_PORT_RING_ON_WHITE", openPort, charging = true))
        assertFalse(visible("CHARGE_PORT_RING_ON_GREEN", openPort, charging = true))
    }

    @Test fun `charging x-ray hides other exterior overlays but preserves amber indicators and blue ring`() {
        val overlays = listOf("HEADLAMP_L_ON", "HEADLAMP_R_LIGHT", "DRL_BAR", "REAR_TAIL_ON", "REAR_BRAKE_ON",
            "HIGH_STOP_ON", "HIGH_STOP_OFF", "REAR_REVERSE_ON_L", "REAR_REVERSE_OFF_R",
            "REAR_TURN_OFF_L", "AMBIENT_STRIP_ON_Xn", "CHARGE_PORT_RING_OFF", "CHARGE_PORT_RING_ON_WHITE")
        overlays.forEach { assertFalse(it, visible(it, allOn, charging = true)) }
        assertTrue(visible("FRONT_INDICATOR_L_ON", allOn, charging = true))
        assertTrue(visible("REAR_TURN_R_ON_6", allOn, charging = true))
        assertTrue(visible("CHARGE_PORT_RING_ON_BLUE", allOn, charging = true))
        assertTrue(visible("DRIVETRAIN_BATTERY", allOn, charging = true))
    }

    @Test fun `emission calibration boosts actual LEDs without lighting their unpowered lenses or billboard glows`() {
        val amber = VehicleLampPolicy.emissionGain("MIRROR_INDICATOR_L_ON")
        val white = VehicleLampPolicy.emissionGain("REAR_REVERSE_ON_R")
        val ambient = VehicleLampPolicy.emissionGain("AMBIENT_STRIP_ON_Xn")
        val charging = VehicleLampPolicy.emissionGain("CHARGE_PORT_RING_ON_BLUE")
        assertTrue(amber > white)
        assertTrue(charging > white)
        assertTrue(ambient > 1f)
        for (node in listOf("BODY", "HEADLAMP_L", "REAR_REVERSE_OFF_R", "REAR_TURN_OFF_L", "OPTIONAL_UNKNOWN_ON",
            "FRONT_INDICATOR_L_GLOW_ON", "REAR_TURN_L_GLOW_ON_2")) {
            assertFalse(node, VehicleLampPolicy.isIndicatorNode(node))
            assertEquals(node, 1f, VehicleLampPolicy.emissionGain(node), 0f)
        }
    }

    @Test fun `headlamp projectors receive visible glow gain while the wider DRL remains restrained`() {
        // Authored red and white RGB factors have different linear luminance.
        // Comparable per-area output makes a visible halo without boosting the full DRL bar.
        val upperStopLuminance = VehicleLampPolicy.emissionGain("HIGH_STOP_ON") * .2355f
        for (node in listOf("HEADLAMP_L_ON", "HEADLAMP_R_ON")) {
            val projectorLuminance = VehicleLampPolicy.emissionGain(node) * .99639f
            assertTrue(node, projectorLuminance in upperStopLuminance * .5f..upperStopLuminance * 1.5f)
            assertTrue(node, VehicleLampPolicy.emissionGain(node) > VehicleLampPolicy.emissionGain("DRL_BAR"))
        }
        assertEquals(2f, VehicleLampPolicy.emissionGain("DRL_BAR"), 0f)
    }

    @Test fun `reverse glow increases exactly twenty percent while its on and off state rules remain intact`() {
        for (node in listOf("REAR_REVERSE_ON_L", "REAR_REVERSE_ON_R")) {
            assertEquals(4f * 1.2f, VehicleLampPolicy.emissionGain(node), .000001f)
            assertFalse(visible(node, off.withGear(VehicleGear.Park)))
            assertTrue(visible(node, off.withGear(VehicleGear.Reverse)))
        }
    }

    @Test fun `original rear overlay calibration and the accepted upper stop glow remain unchanged`() {
        for (node in listOf("REAR_TAIL_ON", "REAR_BRAKE_ON", "HIGH_STOP_ON")) {
            assertEquals(node, 18_000f, VehicleLampPolicy.emissionGain(node), 0f)
        }
    }

    @Test fun `indicator timing uses six tenths on and off and rejects nonfinite clocks`() {
        for (time in listOf(0.0, 0.2, 0.599, 1.2, 1.7, 2.4)) assertTrue("$time", VehicleLampPolicy.blinkOn(time))
        for (time in listOf(0.6, 0.9, 1.199, 1.81, 3.1)) assertFalse("$time", VehicleLampPolicy.blinkOn(time))
        assertTrue(VehicleLampPolicy.blinkOn(-0.5))
        for (time in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFalse(VehicleLampPolicy.blinkOn(time))
        }
    }

    private fun visible(node: String, controls: VehicleControlsState, charging: Boolean = false,
        phase: Boolean = true, trunkOpen: Boolean = false): Boolean =
        VehicleLampPolicy.visible(node, controls, charging, phase, trunkOpen)
}
