package com.aether.hmi.feature.vehicle

/** Keep the exterior ground silhouette; skip cabin and drivetrain shadow work. */
object VehicleShadowPolicy {
    fun isInterior(nodeName: String): Boolean = nodeName.endsWith("_INTERIOR") ||
        nodeName.startsWith("DRIVETRAIN_") || nodeName == "STEERING_WHEEL" || nodeName == "DASH_KEYS"

    fun castsShadows(nodeName: String, hasOpaqueSurface: Boolean): Boolean =
        hasOpaqueSurface && !isInterior(nodeName) && !nodeName.contains("_ON") &&
            !nodeName.contains("_OFF") && nodeName != "DRL_BAR"
}
