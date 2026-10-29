package com.aether.hmi.feature.vehicle

/** Presentation-only charging sweep; telemetry and the numeric charge level stay unchanged. */
object VehicleBatteryAnimation {
    private const val fillSeconds = 2.2
    private const val cycleSeconds = 3.0

    /**
     * Fill from zero to the actual charge over 2.2 seconds, hold for 0.8 seconds,
     * then repeat. The caller owns the charging clock and resets it when charging
     * starts or the reported charge changes. Unknown charge has no fill; reduced
     * motion shows the actual charge immediately without repeating.
     */
    fun fillFraction(elapsedSeconds: Double, actualFraction: Float?, reducedMotion: Boolean): Float? {
        val actual = actualFraction?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: return null
        if (reducedMotion || actual == 0f) return actual
        val elapsed = elapsedSeconds.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val progress = ((elapsed % cycleSeconds) / fillSeconds).coerceIn(0.0, 1.0)
        val eased = progress * progress * (3.0 - 2.0 * progress)
        return (actual * eased).toFloat().coerceIn(0f, actual)
    }
}
