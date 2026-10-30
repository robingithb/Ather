package com.aether.hmi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.aether.hmi.domain.AppSettings
import com.aether.hmi.domain.AppTheme

@Immutable
data class AetherPalette(
    val theme: AppTheme,
    val isLight: Boolean,
    val Background: Color,
    val Panel: Color,
    val PanelDeep: Color,
    val PanelRaised: Color,
    val Rail: Color,
    val RailBorder: Color,
    val Accent: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val Good: Color,
    val Warning: Color,
    val Border: Color,
    val Muted: Color,
    val AccentSoft: Color,
    val OnAccent: Color,
)

/** A pure palette function also usable by renderers after capturing it in composition. */
fun themePalette(theme: AppTheme, accentColor: Long? = null): AetherPalette {
    val base = when (theme) {
        AppTheme.Midnight -> AetherPalette(theme, false,
            Color(0xFF020A14), Color(0xFF071B2B), Color(0xFF000D17), Color(0xFF103044),
            Color(0xFF00080D), Color(0xFF12364A), Color(0xFF00DFFF),
            Color(0xFFF2F7FF), Color(0xFFB8D1EF), Color(0xFF16DB9A), Color(0xFFFFAC3C),
            Color(0xFF1C536F), Color(0xFF758CA1), Color(0xFF003047), Color(0xFF00101B))
        AppTheme.Arctic -> AetherPalette(theme, true,
            Color(0xFFF5F9FC), Color(0xFFFFFFFF), Color(0xFFE7F0F6), Color(0xFFDDECF5),
            Color(0xFFEDF4F8), Color(0xFFC3D5E3), Color(0xFF006C9A),
            Color(0xFF102A3C), Color(0xFF405F74), Color(0xFF087B54), Color(0xFFA45B00),
            Color(0xFFB4CCDB), Color(0xFF617B8D), Color(0xFFCDEAF8), Color.White)
        AppTheme.Sport -> AetherPalette(theme, false,
            Color(0xFF100709), Color(0xFF211217), Color(0xFF140B0F), Color(0xFF3B2027),
            Color(0xFF0D0608), Color(0xFF51242C), Color(0xFFFF535C),
            Color(0xFFFFF3F4), Color(0xFFD7B9BF), Color(0xFF4BDEA5), Color(0xFFFFB85A),
            Color(0xFF6A303C), Color(0xFFB78C95), Color(0xFF481C25), Color(0xFF25060B))
        AppTheme.Minimal -> AetherPalette(theme, false,
            Color(0xFF141719), Color(0xFF202529), Color(0xFF191D20), Color(0xFF353D43),
            Color(0xFF101416), Color(0xFF414A50), Color(0xFFD1DBDF),
            Color(0xFFF2F4F5), Color(0xFFC0C9CE), Color(0xFF65D6A3), Color(0xFFF4BA66),
            Color(0xFF4A555D), Color(0xFF9DAAB2), Color(0xFF46525A), Color(0xFF142027))
    }
    val override = accentColor?.takeIf { it in 0xFF000000L..0xFFFFFFFFL } ?: return base
    val requestedAccent = Color(override)
    var accent = requestedAccent
    // Accent is also used for small labels, so check every surface those labels can sit on.
    // Include the derived selected surface because it changes along with a custom accent.
    val surfaces = listOf(base.Background, base.Panel, base.PanelDeep, base.PanelRaised, base.Rail)
    fun minimumContrast(candidate: Color): Float {
        val luminance = candidate.luminance()
        val soft = androidx.compose.ui.graphics.lerp(base.Panel, candidate, .19f)
        return (surfaces + soft).minOf { surface ->
            val surfaceLuminance = surface.luminance()
            (maxOf(surfaceLuminance, luminance) + .05f) / (minOf(surfaceLuminance, luminance) + .05f)
        }
    }
    if (minimumContrast(accent) < 4.5f) {
        val target = if (base.isLight) Color.Black else Color.White
        var lower = 0f
        var upper = 1f
        // Find the smallest brightness adjustment while leaving margin for float rounding.
        repeat(20) {
            val blend = (lower + upper) / 2f
            val candidate = androidx.compose.ui.graphics.lerp(requestedAccent, target, blend)
            if (minimumContrast(candidate) >= 4.51f) upper = blend else lower = blend
        }
        accent = androidx.compose.ui.graphics.lerp(requestedAccent, target, upper)
    }
    return base.copy(Accent = accent, AccentSoft = androidx.compose.ui.graphics.lerp(base.Panel, accent, .19f),
        OnAccent = readableContentColor(accent))
}

/** Choose icon/text ink from the actual fill, including semantic fills independent of the accent. */
fun readableContentColor(background: Color): Color {
    val luminance = background.luminance()
    val blackContrast = (luminance + .05f) / .05f
    val whiteContrast = 1.05f / (luminance + .05f)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

val LocalAetherPalette = staticCompositionLocalOf { themePalette(AppTheme.Midnight) }

/** Existing UI consumes composition-scoped tokens, so changing settings updates every screen. */
object AetherColors {
    val Background: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.Background
    val Panel: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.Panel
    val PanelDeep: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.PanelDeep
    val PanelRaised: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.PanelRaised
    val Rail: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.Rail
    val RailBorder: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.RailBorder
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.Accent
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.TextPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.TextSecondary
    val Good: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.Good
    val Warning: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.Warning
    val Border: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.Border
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.Muted
    val AccentSoft: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.AccentSoft
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.OnAccent
}

@Composable
fun AetherTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    val palette = remember(settings.theme, settings.accentColor) { themePalette(settings.theme, settings.accentColor) }
    val colors = remember(palette) {
        if (palette.isLight) lightColorScheme(
            primary = palette.Accent, onPrimary = palette.OnAccent,
            primaryContainer = palette.AccentSoft, onPrimaryContainer = palette.TextPrimary,
            secondary = palette.Accent, onSecondary = palette.OnAccent,
            secondaryContainer = palette.AccentSoft, onSecondaryContainer = palette.TextPrimary,
            tertiary = palette.Accent, onTertiary = palette.OnAccent,
            tertiaryContainer = palette.AccentSoft, onTertiaryContainer = palette.TextPrimary,
            inverseSurface = palette.TextPrimary, inverseOnSurface = palette.Background,
            inversePrimary = if (palette.isLight) palette.AccentSoft else palette.PanelRaised,
            surfaceTint = palette.Accent,
            surfaceBright = palette.PanelRaised, surfaceDim = palette.PanelDeep,
            surfaceContainerLowest = palette.Background,
            background = palette.Background, onBackground = palette.TextPrimary,
            surface = palette.Panel, onSurface = palette.TextPrimary,
            surfaceVariant = palette.PanelDeep, onSurfaceVariant = palette.TextSecondary,
            surfaceContainer = palette.Panel, surfaceContainerHigh = palette.PanelRaised,
            surfaceContainerHighest = palette.PanelRaised, surfaceContainerLow = palette.PanelDeep,
            outline = palette.Border, outlineVariant = palette.RailBorder,
        ) else darkColorScheme(
            primary = palette.Accent, onPrimary = palette.OnAccent,
            primaryContainer = palette.AccentSoft, onPrimaryContainer = palette.TextPrimary,
            secondary = palette.Accent, onSecondary = palette.OnAccent,
            secondaryContainer = palette.AccentSoft, onSecondaryContainer = palette.TextPrimary,
            tertiary = palette.Accent, onTertiary = palette.OnAccent,
            tertiaryContainer = palette.AccentSoft, onTertiaryContainer = palette.TextPrimary,
            inverseSurface = palette.TextPrimary, inverseOnSurface = palette.Background,
            inversePrimary = if (palette.isLight) palette.AccentSoft else palette.PanelRaised,
            surfaceTint = palette.Accent,
            surfaceBright = palette.PanelRaised, surfaceDim = palette.PanelDeep,
            surfaceContainerLowest = palette.Background,
            background = palette.Background, onBackground = palette.TextPrimary,
            surface = palette.Panel, onSurface = palette.TextPrimary,
            surfaceVariant = palette.PanelDeep, onSurfaceVariant = palette.TextSecondary,
            surfaceContainer = palette.Panel, surfaceContainerHigh = palette.PanelRaised,
            surfaceContainerHighest = palette.PanelRaised, surfaceContainerLow = palette.PanelDeep,
            outline = palette.Border, outlineVariant = palette.RailBorder,
        )
    }
    CompositionLocalProvider(LocalAetherPalette provides palette) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
