package com.aether.hmi.domain

/** App appearance and app-only preferences. These never modify vehicle/model materials. */
enum class AppTheme { Midnight, Arctic, Sport, Minimal }

data class AppSettings(
    val theme: AppTheme = AppTheme.Midnight,
    val brightness: Float = .7f,
    val autoBrightness: Boolean = true,
    val mediaVolume: Float = .8f,
    val navigationVolume: Float = .6f,
    val routeType: String = "Balanced",
    val avoidTolls: Boolean = false,
    val avoidFerries: Boolean = false,
    val map3D: Boolean = true,
    val ambientColor: Long = 0xFF00DFFF,
    val accentColor: Long? = null,
) {
    /** Validate values at the storage boundary as old/corrupt preferences can outlive app versions. */
    fun normalized(): AppSettings = copy(
        brightness = brightness.finiteFraction(.7f),
        mediaVolume = mediaVolume.finiteFraction(.8f),
        navigationVolume = navigationVolume.finiteFraction(.6f),
        routeType = routeType.takeIf { it in routeTypes } ?: "Balanced",
        ambientColor = ambientColor.takeIf(::validOpaqueColor) ?: 0xFF00DFFF,
        accentColor = accentColor?.takeIf(::validOpaqueColor),
    )

    companion object {
        val routeTypes = listOf("Balanced", "Fastest", "Eco")
    }
}

private fun Float.finiteFraction(fallback: Float): Float = if (isFinite()) coerceIn(0f, 1f) else fallback
private fun validOpaqueColor(color: Long): Boolean = color in 0xFF000000L..0xFFFFFFFFL
