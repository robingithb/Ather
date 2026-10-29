package com.aether.hmi.feature.vehicle

import com.aether.hmi.domain.VehiclePart
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tan
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VehicleSceneMathTest {
    @Test fun `vertical drags keep every ordinary focus level and roof gestures remain bounded`() {
        VehicleSceneFocus.entries.filter { it != VehicleSceneFocus.Sunroof }.forEach { focus ->
            for (drag in listOf(-1000f, 0f, 1000f)) {
                assertEquals(8.0, VehicleSceneMath.dragPitch(focus, 60.0, drag), 0.0)
            }
        }
        assertEquals(61.8, VehicleSceneMath.dragPitch(VehicleSceneFocus.Sunroof, 60.0, 10f), .000001)
        assertEquals(8.0, VehicleSceneMath.dragPitch(VehicleSceneFocus.Sunroof, 60.0, -1000f), 0.0)
        assertEquals(68.0, VehicleSceneMath.dragPitch(VehicleSceneFocus.Sunroof, 60.0, 1000f), 0.0)
    }

    private data class Box(val min: ScenePoint, val max: ScenePoint) {
        fun corners() = buildList {
            for (x in listOf(min.x, max.x)) for (y in listOf(min.y, max.y)) {
                for (z in listOf(min.z, max.z)) add(ScenePoint(x, y, z))
            }
        }
    }
    // Independent measured GenericSUV world bounds; front doors include their mirror children.
    private val body = Box(ScenePoint(-1.0196089, -0.0000005, -2.2049099), ScenePoint(1.0196089, 1.6640493, 2.2049194))
    private val parts = mapOf(
        VehiclePart.FrontLeftDoor to Box(ScenePoint(.6048629, .3740651, -.3443562), ScenePoint(1.0196089, 1.5572497, .8117078)),
        VehiclePart.FrontRightDoor to Box(ScenePoint(-1.0196089, .3642941, -.3860603), ScenePoint(-.6017969, 1.5572497, .8117078)),
        VehiclePart.RearLeftDoor to Box(ScenePoint(.5908740, .3866139, -1.2261099), ScenePoint(.9058810, 1.5570398, -.2292130)),
        VehiclePart.RearRightDoor to Box(ScenePoint(-.9058810, .3856469, -1.2261099), ScenePoint(-.5938879, 1.5455398, -.2292130)),
        VehiclePart.Trunk to Box(ScenePoint(-.6896071, .7445142, -2.1616898), ScenePoint(.6873931, 1.5860193, -1.5339902)),
        VehiclePart.Bonnet to Box(ScenePoint(-.78735, .9610982, 1.0293694), ScenePoint(.78735, 1.18546, 2.09969)),
        VehiclePart.ChargePort to Box(ScenePoint(-.88107, .96939, -1.52), ScenePoint(-.79804, 1.12716, -1.27810)),
        VehiclePart.Sunroof to Box(ScenePoint(-.41706, 1.59764, -1.13459), ScenePoint(.41706, 1.63403, .10315)),
    )
    private val origins = mapOf(
        VehiclePart.FrontLeftDoor to ScenePoint(.8468122482, .9656572938, .8117077947),
        VehiclePart.FrontRightDoor to ScenePoint(-.8496766090, .9607719183, .8117077947),
        VehiclePart.RearLeftDoor to ScenePoint(.8618765473, .9718268514, -.2292130142),
        VehiclePart.RearRightDoor to ScenePoint(-.8620924354, .9632283449, -.2292130291),
        VehiclePart.Trunk to ScenePoint(-.0011070073, 1.5860192776, -1.5989445448),
        VehiclePart.Bonnet to ScenePoint(0.0, 1.1450982094, 1.0293693542),
        VehiclePart.ChargePort to ScenePoint(-.8399999738, 1.0650000572, -1.2799999714),
        VehiclePart.Sunroof to ScenePoint(0.0, 0.0, 0.0),
    )

    @Test fun `closed local transforms are exact identities with bounded fractions`() {
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        VehiclePart.entries.forEach { part ->
            for (fraction in listOf(0f, -1f, Float.NaN)) {
                assertArrayEquals(identity, VehicleSceneMath.hingeMatrix(part, fraction), 0f)
            }
            assertArrayEquals(VehicleSceneMath.hingeMatrix(part, 1f), VehicleSceneMath.hingeMatrix(part, 2f), 0f)
        }
    }

    @Test fun `rotations preserve authored local origin rather than reapplying world pivot translations`() {
        VehiclePart.entries.filter { it != VehiclePart.Sunroof }.forEach { part ->
            for (fraction in listOf(.25f, .5f, 1f)) {
                val matrix = VehicleSceneMath.hingeMatrix(part, fraction)
                assertPointEquals(ScenePoint(0.0, 0.0, 0.0), transform(matrix, ScenePoint(0.0, 0.0, 0.0)), 0.0)
                val origin = origins.getValue(part)
                val sample = subtract(parts.getValue(part).min, origin)
                assertEquals(length(sample), length(transform(matrix, sample)), .000001)
                assertPointEquals(origin, worldMotion(part, origin, fraction), .000001)
            }
        }
    }

    @Test fun `authored left and right door pivots open outward with mirror children attached`() {
        val latches = mapOf(
            VehiclePart.FrontLeftDoor to ScenePoint(.65, 1.0, -.34),
            VehiclePart.FrontRightDoor to ScenePoint(-.65, 1.0, -.38),
            VehiclePart.RearLeftDoor to ScenePoint(.89, 1.0, -1.22),
            VehiclePart.RearRightDoor to ScenePoint(-.89, 1.0, -1.22),
        )
        latches.forEach { (part, latch) ->
            val opened = worldMotion(part, latch, 1f)
            assertTrue(abs(opened.x) > abs(latch.x) + .6)
            assertEquals(latch.y, opened.y, .000001)
        }
        val mirror = ScenePoint(1.0196089, 1.20, .60)
        val mirrorOpened = worldMotion(VehiclePart.FrontLeftDoor, mirror, 1f)
        assertEquals(length(subtract(mirror, origins.getValue(VehiclePart.FrontLeftDoor))),
            length(subtract(mirrorOpened, origins.getValue(VehiclePart.FrontLeftDoor))), .000001)
        assertTrue(mirrorOpened.z > mirror.z)
    }

    @Test fun `tailgate bonnet and charge lid open in their physically correct directions`() {
        val trunkLatch = ScenePoint(.17, .74451, -2.14739)
        val trunk = worldMotion(VehiclePart.Trunk, trunkLatch, 1f)
        assertTrue(trunk.y > trunkLatch.y + 1.0)
        assertTrue(trunk.z < trunkLatch.z - .25)
        val bonnetTip = ScenePoint(0.0, .98, 2.09)
        val bonnet = worldMotion(VehiclePart.Bonnet, bonnetTip, 1f)
        assertTrue(bonnet.y > bonnetTip.y + .8)
        val chargeEdge = ScenePoint(-.84, 1.03, -1.51)
        val charge = worldMotion(VehiclePart.ChargePort, chargeEdge, 1f)
        assertTrue(charge.x < chargeEdge.x - .20)
    }

    @Test fun `sunroof slides rearward with a small lift and preserves panel shape`() {
        val roof = parts.getValue(VehiclePart.Sunroof)
        val a = worldMotion(VehiclePart.Sunroof, roof.min, 1f)
        val b = worldMotion(VehiclePart.Sunroof, roof.max, 1f)
        assertEquals(roof.min.x, a.x, .000001)
        assertEquals(roof.min.y + .045, a.y, .000001)
        assertEquals(roof.min.z - .7, a.z, .000001)
        assertEquals(length(subtract(roof.max, roof.min)), length(subtract(b, a)), .000001)
    }

    @Test fun `closed overview fits tightly instead of reserving space for every open panel`() {
        val closed = VehicleSceneMath.cameraPose(880, 400)
        val allOpen = VehicleSceneMath.cameraPose(880, 400, openParts = VehiclePart.entries.toSet())
        // At the default front-left orbit the body envelope dominates both fits.
        // Opening parts must never reduce the required distance, but equality is valid.
        assertTrue(length(subtract(allOpen.eye, allOpen.target)) >= length(subtract(closed.eye, closed.target)))
        // Side-on framing exposes the raised tailgate/bonnet envelope, making the
        // selected-state fit measurably larger than the closed vehicle's fit.
        val sideClosed = VehicleSceneMath.cameraPose(880, 400, yawDegrees = 90.0, pitchDegrees = 15.0)
        val sideOpen = VehicleSceneMath.cameraPose(880, 400, yawDegrees = 90.0, pitchDegrees = 15.0,
            openParts = VehiclePart.entries.toSet())
        assertTrue(length(subtract(sideOpen.eye, sideOpen.target)) > length(subtract(sideClosed.eye, sideClosed.target)) + .3)
        assertEquals(VehicleSceneMath.modelCenter, closed.target)
        assertTrue(closed.eye.x > closed.target.x && closed.eye.z > closed.target.z)
        assertEquals(0.0, VehicleSceneMath.groundY, 0.0)
        val projected = body.corners().map { project(closed, 880, 400, it) }
        assertTrue(projected.maxOf { maxOf(abs(it.first), abs(it.second)) } > .85)
    }

    @Test fun `all selected opening arcs remain visible at orbit and aspect extremes`() {
        val viewports = listOf(880 to 400, 640 to 320, 210 to 440, 1600 to 100)
        for (open in listOf(emptySet(), VehiclePart.entries.toSet())) {
            val points = body.corners() + open.flatMap { part ->
                (0..12).flatMap { step -> parts.getValue(part).corners().map { worldMotion(part, it, step / 12f) } }
            }
            for ((width, height) in viewports) for (yaw in 0 until 360 step 30) {
                for (pitch in listOf(8.0, 26.0, 60.0)) {
                    val camera = VehicleSceneMath.cameraPose(width, height, yaw.toDouble(), pitch, openParts = open)
                    assertFits(camera, width, height, points)
                }
            }
        }
    }

    @Test fun `focus presets expose their own region while keeping the body and open parts visible`() {
        val open = VehiclePart.entries.toSet()
        val points = body.corners() + open.flatMap { part ->
            (0..8).flatMap { step -> parts.getValue(part).corners().map { worldMotion(part, it, step / 8f) } }
        }
        VehicleSceneFocus.entries.forEach { focus ->
            val camera = VehicleSceneMath.cameraPose(640, 320, focus = focus, openParts = open)
            assertFits(camera, 640, 320, points)
        }
        val rear = VehicleSceneMath.cameraPose(640, 320, focus = VehicleSceneFocus.Trunk)
        assertTrue(rear.eye.z < rear.target.z)
        val roof = VehicleSceneMath.cameraPose(640, 320, focus = VehicleSceneFocus.Sunroof)
        assertTrue(roof.eye.y - roof.target.y > abs(roof.eye.z - roof.target.z))
        val charge = VehicleSceneMath.cameraPose(640, 320, focus = VehicleSceneFocus.Charging)
        assertTrue(charge.eye.x < charge.target.x && charge.eye.z < charge.target.z)
    }

    @Test fun `focus presets stay level except sunroof and retain their existing bearings`() {
        val bearings = mapOf(VehicleSceneFocus.Overview to 38.0, VehicleSceneFocus.Doors to 72.0,
            VehicleSceneFocus.Lights to 18.0, VehicleSceneFocus.Windows to 58.0,
            VehicleSceneFocus.Trunk to 148.0, VehicleSceneFocus.Charging to -132.0,
            VehicleSceneFocus.Bonnet to 28.0, VehicleSceneFocus.Sunroof to 35.0)
        bearings.forEach { (focus, bearing) ->
            val orbit = VehicleSceneMath.focusOrbit(focus)
            assertEquals(bearing, orbit.first, 0.0)
            assertEquals(if (focus == VehicleSceneFocus.Sunroof) 60.0 else 8.0, orbit.second, 0.0)
        }
    }

    @Test fun `azimuth easing crosses wrap boundaries using the shortest arc`() {
        assertEquals(0.0, VehicleSceneMath.orbitStep(350.0, 10.0, .5), .000001)
        assertEquals(0.0, VehicleSceneMath.orbitStep(10.0, 350.0, .5), .000001)
        assertEquals(355.0, VehicleSceneMath.orbitStep(350.0, 10.0, .25), .000001)
        assertEquals(5.0, VehicleSceneMath.orbitStep(10.0, 350.0, .25), .000001)
        assertEquals(313.0, VehicleSceneMath.orbitStep(38.0, -132.0, .5), .000001)
        assertEquals(350.0, VehicleSceneMath.orbitStep(-10.0, 20.0, -1.0), .000001)
        assertEquals(20.0, VehicleSceneMath.orbitStep(-10.0, 20.0, 2.0), .000001)
        assertEquals(75.0, VehicleSceneMath.orbitStep(Double.NaN, 75.0, .5), .000001)
        assertEquals(75.0, VehicleSceneMath.orbitStep(75.0, Double.POSITIVE_INFINITY, .5), .000001)
        assertEquals(75.0, VehicleSceneMath.orbitStep(75.0, 180.0, Double.NaN), .000001)
    }

    @Test fun `camera refits geometry around every interpolated focus target and bearing`() {
        val open = VehiclePart.entries.toSet()
        val points = body.corners() + open.flatMap { part ->
            (0..8).flatMap { step -> parts.getValue(part).corners().map { worldMotion(part, it, step / 8f) } }
        }
        val end = VehicleSceneMath.cameraPose(640, 320, focus = VehicleSceneFocus.Charging).target
        for (step in 0..12) {
            val amount = step / 12.0
            val start = VehicleSceneMath.modelCenter
            val target = ScenePoint(start.x+(end.x-start.x)*amount, start.y+(end.y-start.y)*amount,
                start.z+(end.z-start.z)*amount)
            val yaw = VehicleSceneMath.orbitStep(38.0, -132.0, amount)
            val pose = VehicleSceneMath.cameraPose(640, 320, yawDegrees = yaw, pitchDegrees = 8.0,
                focus = VehicleSceneFocus.Charging, openParts = open, targetOverride = target)
            assertEquals(target, pose.target)
            assertFits(pose, 640, 320, points)
        }
        assertEquals(VehicleSceneMath.cameraPose(640, 320), VehicleSceneMath.cameraPose(640, 320,
            targetOverride = ScenePoint(Double.NaN, 0.0, 0.0)))
    }

    @Test fun `camera clamps inputs and tolerates absent surface dimensions`() {
        assertEquals(VehicleSceneMath.cameraPose(640, 320, pitchDegrees = 8.0),
            VehicleSceneMath.cameraPose(640, 320, pitchDegrees = -90.0))
        assertEquals(VehicleSceneMath.cameraPose(640, 320, pitchDegrees = 68.0),
            VehicleSceneMath.cameraPose(640, 320, pitchDegrees = 90.0))
        assertEquals(VehicleSceneMath.cameraPose(640, 320, zoom = 1.0),
            VehicleSceneMath.cameraPose(640, 320, zoom = .2))
        assertEquals(VehicleSceneMath.cameraPose(640, 320, zoom = 1.35),
            VehicleSceneMath.cameraPose(640, 320, zoom = 5.0))
        assertEquals(VehicleSceneMath.cameraPose(1, 1), VehicleSceneMath.cameraPose(0, -1))
        val thin = VehicleSceneMath.cameraPose(1, 10_000, yawDegrees = Double.NaN,
            pitchDegrees = Double.POSITIVE_INFINITY, zoom = Double.NaN)
        assertTrue(thin.eye.x.isFinite() && thin.eye.y.isFinite() && thin.eye.z.isFinite())
        assertTrue(thin.far > length(subtract(thin.eye, thin.target)) + 3.0)
    }

    @Test fun `ground projection shares camera perspective and target maps to viewport center`() {
        VehicleSceneFocus.entries.forEach { focus ->
            val pose = VehicleSceneMath.cameraPose(880, 400, focus = focus)
            val projector = VehicleSceneMath.projector(pose, 880, 400)
            val target = requireNotNull(VehicleSceneMath.projectPoint(pose.target, pose, 880, 400))
            assertEquals(440.0, target.first, .000001)
            assertEquals(200.0, target.second, .000001)
            val wheelContact = ScenePoint(.765778, -.000000417, 1.271306)
            val ground = ScenePoint(wheelContact.x, VehicleSceneMath.groundY, wheelContact.z)
            val wheelPixel = requireNotNull(projector(wheelContact))
            val groundPixel = requireNotNull(projector(ground))
            assertEquals(wheelPixel.first, groundPixel.first, .001)
            assertEquals(wheelPixel.second, groundPixel.second, .001)
            val expected = project(pose, 880, 400, ground)
            assertEquals((expected.first + 1.0)*440.0, groundPixel.first, .000001)
            assertEquals((1.0 - expected.second)*200.0, groundPixel.second, .000001)
            val behind = ScenePoint(2*pose.eye.x-pose.target.x, 2*pose.eye.y-pose.target.y, 2*pose.eye.z-pose.target.z)
            assertNull(VehicleSceneMath.projectPoint(behind, pose, 880, 400))
            assertNull(projector(behind))
        }
    }

    private fun assertFits(camera: VehicleCameraPose, width: Int, height: Int, points: List<ScenePoint>) {
        points.forEach { point ->
            val projected = project(camera, width, height, point)
            val depth = dot(subtract(point, camera.eye), unit(subtract(camera.target, camera.eye)))
            if (depth <= camera.near || depth >= camera.far || abs(projected.first) > 1.0 || abs(projected.second) > 1.0) {
                fail("Geometry clipped: viewport=" + width + "x" + height + " point=" + point + " projected=" + projected)
            }
        }
    }

    private fun project(camera: VehicleCameraPose, width: Int, height: Int, point: ScenePoint): Pair<Double, Double> {
        val forward = unit(subtract(camera.target, camera.eye))
        val right = unit(cross(forward, ScenePoint(0.0, 1.0, 0.0)))
        val up = cross(right, forward)
        val relative = subtract(point, camera.eye)
        val depth = dot(relative, forward)
        val tangent = tan(camera.verticalFovDegrees * PI / 360.0)
        return dot(relative, right) / (depth * tangent * width / height) to dot(relative, up) / (depth * tangent)
    }

    private fun worldMotion(part: VehiclePart, point: ScenePoint, fraction: Float): ScenePoint {
        val origin = origins.getValue(part)
        val rotated = transform(VehicleSceneMath.hingeMatrix(part, fraction), subtract(point, origin))
        return ScenePoint(rotated.x + origin.x, rotated.y + origin.y, rotated.z + origin.z)
    }

    private fun transform(matrix: FloatArray, point: ScenePoint) = ScenePoint(
        matrix[0]*point.x + matrix[4]*point.y + matrix[8]*point.z + matrix[12],
        matrix[1]*point.x + matrix[5]*point.y + matrix[9]*point.z + matrix[13],
        matrix[2]*point.x + matrix[6]*point.y + matrix[10]*point.z + matrix[14],
    )
    private fun assertPointEquals(expected: ScenePoint, actual: ScenePoint, delta: Double) {
        assertEquals(expected.x, actual.x, delta); assertEquals(expected.y, actual.y, delta); assertEquals(expected.z, actual.z, delta)
    }
    private fun subtract(a: ScenePoint, b: ScenePoint) = ScenePoint(a.x-b.x, a.y-b.y, a.z-b.z)
    private fun dot(a: ScenePoint, b: ScenePoint) = a.x*b.x + a.y*b.y + a.z*b.z
    private fun cross(a: ScenePoint, b: ScenePoint) = ScenePoint(a.y*b.z-a.z*b.y, a.z*b.x-a.x*b.z, a.x*b.y-a.y*b.x)
    private fun length(point: ScenePoint) = sqrt(dot(point, point))
    private fun unit(point: ScenePoint): ScenePoint { val n = length(point); return ScenePoint(point.x/n, point.y/n, point.z/n) }
}
