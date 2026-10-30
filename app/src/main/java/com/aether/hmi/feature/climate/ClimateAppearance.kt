package com.aether.hmi.feature.climate

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.aether.hmi.ui.theme.AetherPalette

/**
 * The cabin is a dark photograph in every app theme. Its emissive graphics need
 * bright ink even when the app uses dark ink on a white surface (Arctic).
 * Keep that image treatment separate from the opaque surfaces of its controls.
 */
internal data class ClimateAppearance(
    val surfaceTop: Color,
    val surfaceBottom: Color,
    val cabinTint: Color,
    val cabinScrimTop: Color,
    val cabinScrimBottom: Color,
    val airflow: Color,
    val airflowHighlight: Color,
    val airflowSeparator: Color,
)

internal fun climateAppearance(palette: AetherPalette): ClimateAppearance {
    val accent = palette.Accent.copy(alpha = 1f)
    var brightAccent = accent
    if (accent.luminance() < .5f) {
        var lower = 0f
        var upper = 1f
        repeat(16) {
            val fraction = (lower + upper) / 2f
            if (lerp(accent, Color.White, fraction).luminance() >= .5f) upper = fraction else lower = fraction
        }
        brightAccent = lerp(accent, Color.White, upper)
    }
    return ClimateAppearance(
        // Photo and moving airflow cannot alter the contrast of text or controls.
        surfaceTop = palette.Panel.copy(alpha = 1f),
        surfaceBottom = palette.PanelDeep.copy(alpha = 1f),
        cabinTint = brightAccent.copy(alpha = .035f),
        // A neutral vignette preserves the photo's depth; a white UI surface
        // must never become a white veil over the footwell in the light theme.
        cabinScrimTop = Color.Black.copy(alpha = .03f),
        cabinScrimBottom = Color.Black.copy(alpha = .20f),
        airflow = brightAccent,
        airflowHighlight = lerp(brightAccent, Color.White, .45f),
        // A narrow dark outline gives the bright moving core an edge even where
        // its route crosses a reflection or a bright part of the photograph.
        airflowSeparator = Color(0xFF071018),
    )
}
