package io.github.ebziboi.delta.render

import android.content.Context
import android.graphics.Color
import io.github.ebziboi.delta.data.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG relative luminance and contrast; shared by custom colour controls, text and widget labels. */
object ThemeContrast {
    fun luminance(color: Int): Double {
        fun c(shift: Int): Double {
            val s = ((color ushr shift) and 255) / 255.0
            return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * c(16) + 0.7152 * c(8) + 0.0722 * c(0)
    }
    fun ratio(a: Int, b: Int): Double {
        val x = luminance(a); val y = luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }
    fun on(color: Int): Int = if (ratio(Color.WHITE, color) >= ratio(Color.BLACK, color)) Color.WHITE else Color.BLACK
    fun imageScrim(background: Int, foreground: Int): Float {
        var opacity = 0.45f
        repeat(28) {
            if (ratio(foreground, Palettes.mix(Color.WHITE, background, opacity)) < 4.6 ||
                ratio(foreground, Palettes.mix(Color.BLACK, background, opacity)) < 4.6) opacity = (opacity + 0.02f).coerceAtMost(1f)
        }
        return opacity
    }
    fun readable(color: Int, background: Int, minimum: Double = 4.5): Int {
        if (ratio(color, background) >= minimum) return color
        val target = on(background)
        var low = 0f; var high = 1f
        repeat(18) {
            val mid = (low + high) / 2
            if (ratio(Palettes.mix(color, target, mid), background) >= minimum) high = mid else low = mid
        }
        return Palettes.mix(color, target, high)
    }
}

/** All display roles are derived from the chosen canvas and accent, never a fixed brand palette. */
data class DerivedTheme(
    val appearance: AppAppearance,
    val background: Int,
    val backgroundSecondary: Int,
    val surface: Int,
    val surfaceElevated: Int,
    val glass: Int,
    val glassBorder: Int,
    val glassHighlight: Int,
    val accent: Int,
    val accentSoft: Int,
    val accentGlow: Int,
    val onAccent: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textMuted: Int,
    val success: Int,
    val warning: Int,
    val danger: Int,
    val isLight: Boolean,
) {
    fun drawColors(): DrawColors {
        val a = appearance
        var ambient = a.accentIntensity * 0.30f
        repeat(24) { if (ThemeContrast.ratio(textPrimary, Palettes.mix(background, accent, ambient)) < 7.0) ambient *= 0.8f }
        val fillTop = if (a.reduceTransparency) surfaceElevated else Palettes.withAlpha(surfaceElevated,
            ((if (a.backgroundStyle == BackgroundStyle.IMAGE) max(a.glassOpacity, 0.88f) else a.glassOpacity + 0.10f).coerceAtMost(1f) * 255).toInt())
        return DrawColors(
            isLight = isLight, bg = background, bgTop = backgroundSecondary,
            surface = surface, surfaceHigh = surfaceElevated, outline = glassBorder,
            text = textPrimary, textDim = textSecondary, textFaint = textMuted,
            accent = accent, accentSoft = accentSoft, onAccent = onAccent,
            accent2 = Palettes.mix(accent, if (onAccent == Color.BLACK) Color.WHITE else Color.BLACK, 0.15f), good = success, warn = warning,
            glow = a.accentIntensity > 0.75f,
            glass = GlassTokens(glass, fillTop, glassBorder, Palettes.fade(glassBorder, 0.25f), glassHighlight,
                Palettes.withAlpha(Color.BLACK, if (isLight) 28 else 64), surface),
            aurora = if (a.backgroundStyle == BackgroundStyle.SOLID) emptyList() else listOf(
                AuroraBlob(Palettes.fade(accent, ambient), 0.06f, 0.10f, 0.52f),
                AuroraBlob(Palettes.fade(accent, ambient * 0.55f), 0.98f, 0.64f, 0.46f),
            ),
        )
    }

    companion object {
        fun derive(value: AppAppearance): DerivedTheme {
            val a = value.normalized()
            val ink = ThemeContrast.on(a.backgroundBase)
            val base = ThemeContrast.readable(a.backgroundBase, ink, 7.0)
            val light = ink == Color.BLACK
            val lift = if (light) Color.BLACK else Color.WHITE
            val surface = Palettes.mix(base, lift, if (light) 0.045f else 0.065f)
            val elevated = Palettes.mix(base, lift, if (light) 0.09f else 0.11f)
            // A custom secondary colour is softened if it would make the same foreground unreadable.
            var secondary = a.backgroundSecondary
            repeat(24) {
                if (ThemeContrast.ratio(ink, secondary) < 7.0) secondary = Palettes.mix(secondary, base, 0.12f)
            }
            val accent = ThemeContrast.readable(a.accent, surface, 4.5)
            val dim = ThemeContrast.readable(Palettes.mix(ink, surface, 0.26f), elevated)
            val muted = ThemeContrast.readable(Palettes.mix(ink, surface, 0.45f), elevated)
            val actualOpacity = if (a.backgroundStyle == BackgroundStyle.IMAGE) max(a.glassOpacity, 0.86f) else a.glassOpacity
            val glass = if (a.reduceTransparency) surface else if (a.backgroundStyle == BackgroundStyle.IMAGE)
                Palettes.withAlpha(surface, (actualOpacity * 255).toInt()) else Palettes.withAlpha(elevated, (actualOpacity * 255).toInt())
            return DerivedTheme(a, base, secondary, surface, elevated, glass,
                Palettes.withAlpha(ink, if (light) 46 else 42), Palettes.withAlpha(ink, if (light) 55 else 80),
                accent, Palettes.fade(accent, 0.12f + 0.06f * a.accentIntensity),
                Palettes.fade(accent, 0.65f * a.accentIntensity), ThemeContrast.on(accent),
                ink, dim, muted,
                ThemeContrast.readable(Color.rgb(52, 211, 153), surface),
                ThemeContrast.readable(Color.rgb(251, 191, 36), surface),
                ThemeContrast.readable(Color.rgb(248, 113, 113), surface), light)
        }
    }
}

object AppearanceEngine {
    fun resolve(context: Context, settings: AppSettings, night: Boolean = Palettes.isNight(context)): AppAppearance {
        settings.appearance?.let { return it.normalized() }
        val legacy = Palettes.resolve(context, settings.theme, settings.accent, night)
        return AppAppearance(backgroundBase = legacy.bg, backgroundSecondary = legacy.bgTop,
            accent = legacy.accent, reduceMotion = !settings.motion)
    }
    fun forApp(context: Context, settings: AppSettings, night: Boolean = Palettes.isNight(context)) =
        DerivedTheme.derive(resolve(context, settings, night))

    fun forWidget(context: Context, settings: AppSettings, night: Boolean): DerivedTheme {
        var a = resolve(context, settings, night)
        val appLight = ThemeContrast.on(a.backgroundBase) == Color.BLACK
        val light = when (settings.widgetTheme) {
            WidgetTheme.APP -> appLight
            WidgetTheme.SYSTEM -> !night
            WidgetTheme.LIGHT -> true
            WidgetTheme.DARK -> false
        }
        if (light != appLight) {
            val neutral = if (light) Color.WHITE else Color.BLACK
            a = a.copy(backgroundBase = Palettes.mix(a.backgroundBase, neutral, 0.96f),
                backgroundSecondary = Palettes.mix(a.backgroundSecondary, neutral, 0.96f))
        }
        return DerivedTheme.derive(a)
    }
}
