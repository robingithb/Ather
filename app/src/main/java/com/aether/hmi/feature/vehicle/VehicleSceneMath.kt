package com.aether.hmi.feature.vehicle

import com.aether.hmi.domain.VehiclePart
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class ScenePoint(val x: Double, val y: Double, val z: Double)

data class VehicleCameraPose(
    val eye: ScenePoint,
    val target: ScenePoint,
    val verticalFovDegrees: Double = 40.0,
    val near: Double = 0.1,
    val far: Double = 100.0,
)

enum class VehicleSceneFocus { Overview, Doors, Lights, Windows, Trunk, Charging, Bonnet, Sunroof }

/** Raw GenericSUV coordinates: +Y up, +Z nose, +X vehicle-left; wheel contact is Y=0. */
object VehicleSceneMath {
    const val groundY = 0.0
    val modelCenter = ScenePoint(0.0, 0.832024425, 0.000004768)
    private const val verticalFovDegrees = 40.0
    private const val fitMargin = 1.045

    private enum class Axis { X, Y, Slide }

    private data class Bounds(val min: ScenePoint, val max: ScenePoint) {
        fun corners(): List<ScenePoint> = buildList {
            for (x in listOf(min.x, max.x)) for (y in listOf(min.y, max.y)) {
                for (z in listOf(min.z, max.z)) add(ScenePoint(x, y, z))
            }
        }
    }

    private data class Motion(val origin: ScenePoint, val axis: Axis, val degrees: Double, val bounds: Bounds)

    // Authored local origins and measured world bounds from generic-suv-model-manifest.json.
    // Front-door bounds include the correctly parented mirror assemblies.
    private val motions = mapOf(
        VehiclePart.FrontLeftDoor to Motion(ScenePoint(0.8468122482, 0.9656572938, 0.8117077947), Axis.Y, -68.0,
            Bounds(ScenePoint(0.60486, 0.37406, -0.34436), ScenePoint(1.01961, 1.55725, 0.81171))),
        VehiclePart.FrontRightDoor to Motion(ScenePoint(-0.8496766090, 0.9607719183, 0.8117077947), Axis.Y, 68.0,
            Bounds(ScenePoint(-1.01961, 0.36429, -0.38607), ScenePoint(-0.60179, 1.55725, 0.81171))),
        VehiclePart.RearLeftDoor to Motion(ScenePoint(0.8618765473, 0.9718268514, -0.2292130142), Axis.Y, -68.0,
            Bounds(ScenePoint(0.59087, 0.38661, -1.22612), ScenePoint(0.90589, 1.55704, -0.22921))),
        VehiclePart.RearRightDoor to Motion(ScenePoint(-0.8620924354, 0.9632283449, -0.2292130291), Axis.Y, 68.0,
            Bounds(ScenePoint(-0.90589, 0.38564, -1.22612), ScenePoint(-0.59388, 1.54554, -0.22921))),
        VehiclePart.Trunk to Motion(ScenePoint(-0.0011070073, 1.5860192776, -1.5989445448), Axis.X, 70.0,
            Bounds(ScenePoint(-0.68961, 0.74451, -2.16170), ScenePoint(0.68740, 1.58602, -1.53399))),
        VehiclePart.Bonnet to Motion(ScenePoint(0.0, 1.1450982094, 1.0293693542), Axis.X, -65.0,
            Bounds(ScenePoint(-0.78736, 0.96109, 1.02936), ScenePoint(0.78736, 1.18547, 2.09970))),
        VehiclePart.ChargePort to Motion(ScenePoint(-0.8399999738, 1.0650000572, -1.2799999714), Axis.Y, 100.0,
            Bounds(ScenePoint(-0.88108, 0.96938, -1.52001), ScenePoint(-0.79803, 1.12717, -1.27809))),
        VehiclePart.Sunroof to Motion(ScenePoint(0.0, 0.0, 0.0), Axis.Slide, 0.0,
            Bounds(ScenePoint(-0.41707, 1.59763, -1.13460), ScenePoint(0.41707, 1.63404, 0.10316))),
    )
    private val bodyCorners = Bounds(
        ScenePoint(-1.01961, -0.00001, -2.20492), ScenePoint(1.01961, 1.66406, 2.20493),
    ).corners()
    private val panelCorners = motions.mapValues { (_, motion) -> motion.bounds.corners() }

    /** Presets are applied on focus changes; ordinary drags keep their pitch. */
    fun focusOrbit(focus: VehicleSceneFocus): Pair<Double, Double> = when (focus) {
        VehicleSceneFocus.Overview -> 38.0 to 8.0
        VehicleSceneFocus.Doors -> 72.0 to 8.0
        VehicleSceneFocus.Lights -> 18.0 to 8.0
        VehicleSceneFocus.Windows -> 58.0 to 8.0
        VehicleSceneFocus.Trunk -> 148.0 to 8.0
        VehicleSceneFocus.Charging -> -132.0 to 8.0
        VehicleSceneFocus.Bonnet -> 28.0 to 8.0
        VehicleSceneFocus.Sunroof -> 35.0 to 60.0
    }

    /** Vertical drag is reserved for inspecting the roof; ordinary vehicle views stay level. */
    fun dragPitch(focus: VehicleSceneFocus, currentPitch: Double, verticalPixels: Float): Double =
        if (focus == VehicleSceneFocus.Sunroof) {
            (currentPitch + verticalPixels * .18).coerceIn(8.0, 68.0)
        } else focusOrbit(focus).second

    fun cameraPose(
        width: Int,
        height: Int,
        yawDegrees: Double = Double.NaN,
        pitchDegrees: Double = Double.NaN,
        zoom: Double = 1.0,
        focus: VehicleSceneFocus = VehicleSceneFocus.Overview,
        openParts: Set<VehiclePart> = emptySet(),
        targetOverride: ScenePoint? = null,
    ): VehicleCameraPose {
        val preset = focusOrbit(focus)
        val aspect = width.coerceAtLeast(1).toDouble() / height.coerceAtLeast(1)
        val verticalTangent = tan(radians(verticalFovDegrees) / 2.0)
        val horizontalTangent = verticalTangent * aspect
        val safeZoom = (zoom.takeIf { it.isFinite() } ?: 1.0).coerceIn(1.0, 1.35)
        val yaw = radians((yawDegrees.takeIf { it.isFinite() } ?: preset.first) % 360.0)
        val pitch = radians((pitchDegrees.takeIf { it.isFinite() } ?: preset.second).coerceIn(8.0, 68.0))
        val presetTarget = when (focus) {
            VehicleSceneFocus.Trunk -> ScenePoint(0.0, modelCenter.y, -0.25)
            VehicleSceneFocus.Charging -> ScenePoint(0.0, 0.58, -0.10)
            VehicleSceneFocus.Bonnet -> ScenePoint(0.0, modelCenter.y, 0.25)
            VehicleSceneFocus.Sunroof -> ScenePoint(0.0, 0.95, -0.20)
            else -> modelCenter
        }
        val target = targetOverride?.takeIf { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() } ?: presetTarget
        val towardEye = ScenePoint(sin(yaw) * cos(pitch), sin(pitch), cos(yaw) * cos(pitch))
        val right = ScenePoint(cos(yaw), 0.0, -sin(yaw))
        val up = ScenePoint(-sin(yaw) * sin(pitch), cos(pitch), -cos(yaw) * sin(pitch))
        val fitDirections = listOf(
            plusScaled(towardEye, right, 1.0 / horizontalTangent),
            plusScaled(towardEye, right, -1.0 / horizontalTangent),
            plusScaled(towardEye, up, 1.0 / verticalTangent),
            plusScaled(towardEye, up, -1.0 / verticalTangent),
        )
        fun maximum(direction: ScenePoint) = maximumGeometryProjection(direction, target, openParts)
        val fittedDistance = fitDirections.maxOf(::maximum)
        val distance = max(fittedDistance * fitMargin * safeZoom, maximum(towardEye) + 0.2)
        return VehicleCameraPose(
            eye = ScenePoint(target.x + distance * towardEye.x, target.y + distance * towardEye.y,
                target.z + distance * towardEye.z),
            target = target,
            verticalFovDegrees = verticalFovDegrees,
            far = max(100.0, distance + maximum(plusScaled(towardEye, towardEye, -2.0)) + 1.0),
        )
    }

    /** Interpolate the shortest azimuth arc; result is normalized to [0, 360). */
    fun orbitStep(currentYaw: Double, targetYaw: Double, amount: Double): Double {
        fun normalize(value: Double) = ((value % 360.0) + 360.0) % 360.0
        val current = normalize(currentYaw.takeIf { it.isFinite() } ?: targetYaw.takeIf { it.isFinite() } ?: 0.0)
        val target = normalize(targetYaw.takeIf { it.isFinite() } ?: current)
        val fraction = (amount.takeIf { it.isFinite() } ?: 0.0).coerceIn(0.0, 1.0)
        val delta = (target - current + 540.0) % 360.0 - 180.0
        return normalize(current + delta*fraction)
    }

    /**
     * A LOCAL motion about the model's authored origin, not a raw-world pivot matrix.
     * Filament must compose currentLocal = authoredRest * hingeMatrix(part, fraction).
     * Closed is exactly identity, preserving every authored translation and child accessory.
     */
    fun hingeMatrix(part: VehiclePart, openFraction: Float): FloatArray {
        val matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        val fraction = (openFraction.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, 1f)
        if (fraction == 0f) return matrix
        val motion = motions.getValue(part)
        if (motion.axis == Axis.Slide) {
            matrix[13] = 0.045f * fraction
            matrix[14] = -0.7f * fraction
            return matrix
        }
        val angle = radians(motion.degrees * fraction)
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        when (motion.axis) {
            Axis.Y -> { matrix[0] = c; matrix[2] = -s; matrix[8] = s; matrix[10] = c }
            Axis.X -> { matrix[5] = c; matrix[6] = s; matrix[9] = -s; matrix[10] = c }
            Axis.Slide -> Unit
        }
        return matrix
    }

    /** Perspective projection in top-left viewport pixels; null for invalid/behind-camera points. */
    fun projectPoint(point: ScenePoint, pose: VehicleCameraPose, width: Int, height: Int): Pair<Double, Double>? =
        projector(pose, width, height)(point)

    /** Cache the camera basis once when projecting a ground path or many shadow samples. */
    fun projector(pose: VehicleCameraPose, width: Int, height: Int): (ScenePoint) -> Pair<Double, Double>? {
        fun unit(vector: ScenePoint): ScenePoint? {
            val length = sqrt(vector.x*vector.x + vector.y*vector.y + vector.z*vector.z)
            if (!length.isFinite() || length <= 1e-12) return null
            return ScenePoint(vector.x/length, vector.y/length, vector.z/length)
        }
        fun dot(a: ScenePoint, b: ScenePoint) = a.x*b.x + a.y*b.y + a.z*b.z
        val forward = unit(ScenePoint(pose.target.x-pose.eye.x, pose.target.y-pose.eye.y, pose.target.z-pose.eye.z)) ?: return { null }
        val right = unit(ScenePoint(-forward.z, 0.0, forward.x)) ?: return { null }
        val up = ScenePoint(right.y*forward.z-right.z*forward.y,
            right.z*forward.x-right.x*forward.z, right.x*forward.y-right.y*forward.x)
        val w = width.coerceAtLeast(1).toDouble()
        val h = height.coerceAtLeast(1).toDouble()
        val tangent = tan(radians(pose.verticalFovDegrees)/2.0)
        val horizontalScale = 2.0*tangent*w/h
        val verticalScale = 2.0*tangent
        return project@{ point ->
            val relative = ScenePoint(point.x-pose.eye.x, point.y-pose.eye.y, point.z-pose.eye.z)
            val depth = dot(relative, forward)
            if (!depth.isFinite() || depth <= 1e-9) return@project null
            val x = (0.5 + dot(relative, right)/(depth*horizontalScale))*w
            val y = (0.5 - dot(relative, up)/(depth*verticalScale))*h
            if (x.isFinite() && y.isFinite()) x to y else null
        }
    }

    private fun maximumGeometryProjection(direction: ScenePoint, target: ScenePoint, openParts: Set<VehiclePart>): Double {
        val bodyMaximum = bodyCorners.maxOf { point ->
            (point.x - target.x) * direction.x + (point.y - target.y) * direction.y + (point.z - target.z) * direction.z
        }
        val panelMaximum = openParts.maxOfOrNull { part ->
            val motion = motions.getValue(part)
            panelCorners.getValue(part).maxOf { point -> maximumArcProjection(point, motion, direction, target) }
        } ?: Double.NEGATIVE_INFINITY
        return max(bodyMaximum, panelMaximum)
    }

    private fun maximumArcProjection(point: ScenePoint, motion: Motion, direction: ScenePoint, target: ScenePoint): Double {
        if (motion.axis == Axis.Slide) {
            val closed = (point.x-target.x)*direction.x + (point.y-target.y)*direction.y + (point.z-target.z)*direction.z
            return max(closed, closed + 0.045*direction.y - 0.7*direction.z)
        }
        val p = motion.origin
        val vx = point.x - p.x
        val vy = point.y - p.y
        val vz = point.z - p.z
        val base = (p.x-target.x)*direction.x + (p.y-target.y)*direction.y + (p.z-target.z)*direction.z
        val constant: Double
        val a: Double
        val b: Double
        when (motion.axis) {
            Axis.Y -> { constant = base + vy*direction.y; a = vx*direction.x + vz*direction.z; b = vz*direction.x - vx*direction.z }
            Axis.X -> { constant = base + vx*direction.x; a = vy*direction.y + vz*direction.z; b = vy*direction.z - vz*direction.y }
            Axis.Slide -> error("Slide handled before rotation")
        }
        val end = radians(motion.degrees)
        fun projectedAt(angle: Double) = constant + a*cos(angle) + b*sin(angle)
        var maximum = max(projectedAt(0.0), projectedAt(end))
        val peak = atan2(b, a)
        for (turn in -1..1) {
            val angle = peak + turn*2.0*PI
            if (angle >= min(0.0, end) && angle <= max(0.0, end)) maximum = max(maximum, projectedAt(angle))
        }
        return maximum
    }

    private fun plusScaled(a: ScenePoint, b: ScenePoint, scale: Double) =
        ScenePoint(a.x+b.x*scale, a.y+b.y*scale, a.z+b.z*scale)

    private fun radians(degrees: Double) = degrees*PI/180.0
}
