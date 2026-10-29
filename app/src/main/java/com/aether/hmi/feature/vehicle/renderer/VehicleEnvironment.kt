package com.aether.hmi.feature.vehicle.renderer

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.get
import com.aether.hmi.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/** A static, scene-aligned reflection probe. Built once, then prefiltered by Filament.
 * Soft light strips give the paint, glass and chrome readable curved reflections;
 * the wet ground supplies subdued colour below the horizon without the painted ring.
 */
internal object VehicleEnvironment {
    const val faceSize = 128
    const val levels = 8
    val faceOffsets = IntArray(6) { it * faceSize * faceSize * 3 * Float.SIZE_BYTES }
    private val linear = DoubleArray(256) { value ->
        val srgb = value / 255.0
        if (srgb <= .04045) srgb / 12.92 else ((srgb + .055) / 1.055).pow(2.4)
    }
    private val key = Softbox(-.25, .45, -.90, .72, .11, 3.5)
    private val fill = Softbox(.85, .50, .40, .13, .26, 1.5)
    private val overhead = Softbox(.0, .95, .31, .70, .30, 2.0)

    fun read(resources: Resources): ByteBuffer {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 4
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val panorama = requireNotNull(BitmapFactory.decodeResource(resources, R.drawable.vehicle_stage, options))
        var ground: Bitmap? = null
        try {
            val floorBitmap = requireNotNull(BitmapFactory.decodeResource(resources, R.drawable.vehicle_ground, options))
            ground = floorBitmap
            val buffer = ByteBuffer.allocateDirect(6 * faceSize * faceSize * 3 * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
            for (face in 0..5) for (row in 0 until faceSize) for (column in 0 until faceSize) {
                val u = 2.0 * (column + .5) / faceSize - 1.0
                val v = 2.0 * (row + .5) / faceSize - 1.0
                val x: Double
                val y: Double
                val z: Double
                when (face) {
                    0 -> { x = 1.0; y = -v; z = -u }
                    1 -> { x = -1.0; y = -v; z = u }
                    2 -> { x = u; y = 1.0; z = v }
                    3 -> { x = u; y = -1.0; z = -v }
                    4 -> { x = u; y = -v; z = 1.0 }
                    else -> { x = -u; y = -v; z = -1.0 }
                }
                val length = sqrt(x * x + y * y + z * z)
                val nx = x / length
                val ny = y / length
                val nz = z / length
                val pixel = panorama[
                    (VehicleReflectionProbe.panoramaU(nx, nz) * (panorama.width - 1)).toInt(),
                    (VehicleReflectionProbe.panoramaV(nx, ny, nz) * (panorama.height - 1)).toInt()]
                val groundBlend = (-ny * 4.0).coerceIn(0.0, 1.0)
                val groundPixel = if (groundBlend > 0.0) {
                    // Match the repeating 6 m ground plane, viewed from body height.
                    val distance = .808 / -ny
                    val floorU = nx * distance / 6.0 + .5
                    val floorV = nz * distance / 6.0 + .5
                    floorBitmap[((floorU - floor(floorU)) * floorBitmap.width).toInt(),
                        ((floorV - floor(floorV)) * floorBitmap.height).toInt()]
                } else 0
                val keyLight = key.radiance(nx, ny, nz)
                val fillLight = fill.radiance(nx, ny, nz)
                val topLight = overhead.radiance(nx, ny, nz)
                for (channel in 0..2) {
                    val shift = 16 - channel * 8
                    // The panorama material uses the same cool, subdued base tint.
                    val skyTint = when (channel) { 0 -> .70; 1 -> .75; else -> .80 }
                    val groundTint = when (channel) { 0 -> .23; 1 -> .26; else -> .30 }
                    val groundFill = when (channel) { 0 -> .012; 1 -> .014; else -> .018 }
                    val keyTint = when (channel) { 0 -> 1.0; 1 -> .97; else -> .93 }
                    val fillTint = when (channel) { 0 -> .88; 1 -> .94; else -> 1.0 }
                    val sky = linear[(pixel shr shift) and 255] * skyTint
                    val floorColor = linear[(groundPixel shr shift) and 255] * groundTint + groundFill
                    buffer.putFloat((sky * (1.0 - groundBlend) + floorColor * groundBlend +
                        keyLight * keyTint + fillLight * fillTint + topLight).toFloat())
                }
            }
            buffer.flip()
            return buffer
        } finally {
            panorama.recycle()
            ground?.recycle()
        }
    }

    /** A rounded rectangular light source, with no per-texel allocations. */
    private class Softbox(x: Double, y: Double, z: Double,
                          private val width: Double, private val height: Double,
                          private val strength: Double) {
        private val length = sqrt(x * x + y * y + z * z)
        private val cx = x / length
        private val cy = y / length
        private val cz = z / length
        private val horizontal = sqrt(cx * cx + cz * cz)
        private val tx = cz / horizontal
        private val tz = -cx / horizontal
        private val bx = cy * tz
        private val by = cz * tx - cx * tz
        private val bz = -cy * tx

        fun radiance(x: Double, y: Double, z: Double): Double {
            val forward = x * cx + y * cy + z * cz
            if (forward <= 0.0) return 0.0
            val u = (x * tx + z * tz) / (forward * width)
            val v = (x * bx + y * by + z * bz) / (forward * height)
            if (u < -2.0 || u > 2.0 || v < -2.0 || v > 2.0) return 0.0
            val u2 = u * u
            val v2 = v * v
            return strength * exp(-(u2 * u2 + v2 * v2))
        }
    }
}
