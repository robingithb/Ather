package com.aether.hmi.feature.vehicle.renderer

/** Colors and visibility for the generated stage only; never applied to the vehicle asset. */
internal data class SceneryColor(val red: Float, val green: Float, val blue: Float) {
    fun map(transform: (Float) -> Float) = SceneryColor(transform(red), transform(green), transform(blue))
}

internal enum class ScenerySurfaceMode { Midnight, NeutralTint, Arctic }

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
        // Midnight now uses the neutral source too: its black theme must not reveal blue mountains.
        "AETHER_PANORAMA", "AETHER_FLOOR" -> false
        "AETHER_NEUTRAL_PANORAMA", "AETHER_NEUTRAL_FLOOR" -> mode != ScenerySurfaceMode.Arctic
        "AETHER_LIGHT_PANORAMA", "AETHER_LIGHT_FLOOR" -> mode == ScenerySurfaceMode.Arctic
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
                light -> ScenerySurfaceMode.Arctic
                backgroundArgb == 0xFF030303.toInt() || backgroundArgb == 0xFF020A14.toInt() -> ScenerySurfaceMode.Midnight
                else -> ScenerySurfaceMode.NeutralTint
            }
            val panorama = when (mode) {
                ScenerySurfaceMode.Arctic -> SceneryColor(.94f, .94f, .94f)
                ScenerySurfaceMode.Midnight -> SceneryColor(.40f, .40f, .40f)
                ScenerySurfaceMode.NeutralTint -> if (neutral) SceneryColor(.66f, .66f, .66f)
                    else hue.map { .30f + it * .40f }
            }
            // One scene tint keeps the landscape and ground in the same theme family.
            val floor = panorama
            val restrained = mode == ScenerySurfaceMode.Midnight
            return VehicleSceneryTheme(mode,
                // Midnight's silver trim stays quiet; custom accents still retain their hue.
                // Keep every theme below white clipping so the ring never loses its color.
                glow = hue.map { it * if (restrained) .10f else .18f },
                ring = hue.map { if (restrained) .01f + it * .20f else .012f + it * .28f },
                core = hue.map { if (restrained) .055f + it * .26f else .075f + it * .36f },
                rim = if (light) SceneryColor(.48f, .55f, .61f) else rgb(backgroundArgb).map { .025f + it * .18f },
                panorama = panorama, floor = floor)
        }
    }
}
