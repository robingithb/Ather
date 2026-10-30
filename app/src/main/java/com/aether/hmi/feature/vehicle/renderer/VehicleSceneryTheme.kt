package com.aether.hmi.feature.vehicle.renderer

/** Colors and visibility for the generated stage only; never applied to the vehicle asset. */
internal data class SceneryColor(val red: Float, val green: Float, val blue: Float) {
    fun map(transform: (Float) -> Float) = SceneryColor(transform(red), transform(green), transform(blue))
}

internal enum class ScenerySurfaceMode { Midnight, NeutralTint, LightStudio }

internal data class VehicleSceneryTheme(
    val mode: ScenerySurfaceMode,
    val glow: SceneryColor,
    val ring: SceneryColor,
    val core: SceneryColor,
    val rim: SceneryColor,
    val panorama: SceneryColor,
    val floor: SceneryColor,
) {
    fun showsSurface(name: String): Boolean = when (name) {
        "AETHER_PANORAMA", "AETHER_FLOOR" -> mode == ScenerySurfaceMode.Midnight
        "AETHER_NEUTRAL_PANORAMA", "AETHER_NEUTRAL_FLOOR" -> mode == ScenerySurfaceMode.NeutralTint
        "AETHER_LIGHT_PANORAMA", "AETHER_LIGHT_FLOOR" -> mode == ScenerySurfaceMode.LightStudio
        else -> true
    }

    companion object {
        fun from(accentArgb: Int, backgroundArgb: Int, light: Boolean): VehicleSceneryTheme {
            fun rgb(argb: Int) = SceneryColor(((argb ushr 16) and 255) / 255f,
                ((argb ushr 8) and 255) / 255f, (argb and 255) / 255f)
            val accent = rgb(accentArgb)
            val peak = maxOf(accent.red, accent.green, accent.blue).coerceAtLeast(.001f)
            val hue = accent.map { it / peak }
            val neutral = maxOf(hue.red, hue.green, hue.blue) - minOf(hue.red, hue.green, hue.blue) < .2f
            val mode = when {
                light -> ScenerySurfaceMode.LightStudio
                backgroundArgb == 0xFF020A14.toInt() -> ScenerySurfaceMode.Midnight
                else -> ScenerySurfaceMode.NeutralTint
            }
            val panorama = when (mode) {
                ScenerySurfaceMode.LightStudio -> SceneryColor(.88f, .93f, .97f)
                ScenerySurfaceMode.Midnight -> SceneryColor(.70f, .75f, .80f)
                ScenerySurfaceMode.NeutralTint -> if (neutral) SceneryColor(.66f, .66f, .66f)
                    else hue.map { .30f + it * .40f }
            }
            val floor = when (mode) {
                ScenerySurfaceMode.LightStudio -> SceneryColor(.88f, .91f, .94f)
                ScenerySurfaceMode.Midnight -> SceneryColor(.70f, .73f, .78f)
                ScenerySurfaceMode.NeutralTint -> if (neutral) SceneryColor(.62f, .62f, .62f)
                    else hue.map { .28f + it * .35f }
            }
            return VehicleSceneryTheme(mode,
                // Keep these below white clipping: bright unlit factors previously erased the hue.
                glow = hue.map { it * .18f },
                ring = hue.map { .012f + it * .28f },
                core = hue.map { .075f + it * .36f },
                rim = if (light) SceneryColor(.48f, .55f, .61f) else rgb(backgroundArgb).map { .025f + it * .18f },
                panorama = panorama, floor = floor)
        }
    }
}
