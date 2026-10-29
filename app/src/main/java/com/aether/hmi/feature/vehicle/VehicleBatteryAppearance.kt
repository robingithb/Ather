package com.aether.hmi.feature.vehicle

/** Linear material factors for the battery view, derived only from shared state-of-charge telemetry. */
data class VehicleBatteryAppearance(
    val chargeFraction: Float?,
    val red: Float,
    val green: Float,
    val blue: Float,
    val emissionRed: Float,
    val emissionGreen: Float,
    val emissionBlue: Float,
) {
    companion object {
        /** Unknown charge remains neutral and unlit; it must never look like a full green pack. */
        fun fromPercent(batteryPercent: Int?): VehicleBatteryAppearance {
            val fraction = batteryPercent?.coerceIn(0, 100)?.div(100f)
                ?: return VehicleBatteryAppearance(null, .11f, .12f, .13f, 0f, 0f, 0f)
            return VehicleBatteryAppearance(
                chargeFraction = fraction,
                red = .025f + .02f * fraction,
                green = .07f + .21f * fraction,
                blue = .035f + .04f * fraction,
                // The pack is a large surface: use a small green lift without a broad bloom halo.
                // No pulse or elapsed-time term invents a change in the repository charge level.
                emissionRed = .06f * fraction,
                emissionGreen = 1.5f * fraction,
                emissionBlue = .12f * fraction,
            )
        }
    }
}
