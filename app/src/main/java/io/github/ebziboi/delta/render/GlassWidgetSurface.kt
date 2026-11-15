package io.github.ebziboi.delta.render

import android.graphics.*
import io.github.ebziboi.delta.data.*
import kotlin.math.*

/** Static widget roles are derived from the very same appearance as the app. */
data class WidgetAppearance(
    val background: Int, val surface: Int, val accent: Int, val textPrimary: Int,
    val textSecondary: Int, val border: Int, val success: Int, val warning: Int, val danger: Int,
) {
    companion object {
        fun from(theme: DerivedTheme) = WidgetAppearance(theme.background, theme.surface, theme.accent,
            theme.textPrimary, theme.textSecondary, theme.glassBorder, theme.success, theme.warning, theme.danger)
    }
}

/** Widgets use a static translucent gradient and edge lighting, with no captures or live blur. */
object GlassWidgetSurface {
    fun draw(canvas: Canvas, width: Float, height: Float, theme: DerivedTheme, settings: AppSettings, hostRadius: Float = theme.appearance.cornerRadius) {
        val a = theme.appearance
        val c = WidgetAppearance.from(theme)
        val radius = min(max(a.cornerRadius, hostRadius), min(width, height) / 2)
        val shape = Path().apply { addRoundRect(RectF(0f, 0f, width, height), radius, radius, Path.Direction.CW) }
        canvas.clipPath(shape)
        val chosenOpacity = if (theme.isLight) settings.widgetOpacityLight else settings.widgetOpacityDark
        val opacity = if (a.reduceTransparency || settings.widgetGlass == WidgetGlass.SOLID) 1f else
            (chosenOpacity * (a.glassOpacity / 0.12f)).let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0.65f }
        fun alpha(color: Int) = Palettes.withAlpha(color, (opacity * 255).roundToInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = alpha(c.background)
            shader = when (a.backgroundStyle) {
                BackgroundStyle.SOLID -> null
                BackgroundStyle.RADIAL_GRADIENT -> RadialGradient(width * 0.2f, height * 0.1f, max(width, height),
                    alpha(theme.backgroundSecondary), alpha(c.background), Shader.TileMode.CLAMP)
                else -> LinearGradient(0f, 0f, 0f, height, alpha(theme.backgroundSecondary), alpha(c.background), Shader.TileMode.CLAMP)
            }
        }
        canvas.drawPath(shape, paint)
        if (a.accentIntensity > 0 && a.backgroundStyle != BackgroundStyle.SOLID && !a.reduceTransparency) {
            paint.shader = RadialGradient(width * 0.15f, 0f, max(width, height) * 0.9f,
                Palettes.fade(c.accent, a.accentIntensity * opacity * 0.22f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawPath(shape, paint)
        }
        // Shading is inside the shape; no straight highlight cuts across its curved corners.
        paint.shader = LinearGradient(0f, height * 0.55f, 0f, height,
            Color.TRANSPARENT, Palettes.withAlpha(Color.BLACK, (opacity * 30).toInt()), Shader.TileMode.CLAMP)
        canvas.drawPath(shape, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
        paint.shader = null; paint.color = Palettes.fade(c.border, 0.3f)
        canvas.drawRoundRect(0.5f, 0.5f, width - 0.5f, height - 0.5f, max(0f, radius - 0.5f), max(0f, radius - 0.5f), paint)
        paint.strokeWidth = 1.4f
        val top = Palettes.withAlpha(theme.textPrimary, if (theme.isLight) 55 else 170)
        paint.shader = LinearGradient(0f, 0f, 0f, height * 0.85f,
            intArrayOf(top, Palettes.fade(top, 0.42f), Palettes.fade(top, 0.06f), Color.TRANSPARENT),
            floatArrayOf(0f, 0.32f, 0.72f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(0.8f, 0.8f, width - 0.8f, height - 0.8f, max(0f, radius - 0.8f), max(0f, radius - 0.8f), paint)
    }
}

/** Use the host's corner radius as a minimum, so One UI cannot crop away the curved rim. */
object WidgetCorners {
    fun radius(context: android.content.Context, requested: Float): Float {
        val system = if (android.os.Build.VERSION.SDK_INT >= 31) runCatching {
            context.resources.getDimension(android.R.dimen.system_app_widget_background_radius) / context.resources.displayMetrics.density
        }.getOrDefault(requested) else requested
        return max(requested, system)
    }
}
