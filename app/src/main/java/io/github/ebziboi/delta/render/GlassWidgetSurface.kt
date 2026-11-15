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

/** Widgets are a plain rounded pane: a steady gradient from background to accent, or a photo. */
object GlassWidgetSurface {
    fun draw(canvas: Canvas, width: Float, height: Float, theme: DerivedTheme, settings: AppSettings, hostRadius: Float = theme.appearance.cornerRadius) {
        val a = theme.appearance
        val c = WidgetAppearance.from(theme)
        val radius = min(max(a.cornerRadius, hostRadius), min(width, height) / 2)
        val shape = Path().apply { addRoundRect(RectF(0f, 0f, width, height), radius, radius, Path.Direction.CW) }
        canvas.clipPath(shape)
        if (a.designStyle == DesignStyle.TUBIK && a.backgroundStyle != BackgroundStyle.IMAGE) {
            canvas.drawColor(c.background)
            return
        }
        val chosenOpacity = if (theme.isLight) settings.widgetOpacityLight else settings.widgetOpacityDark
        val opacity = if (a.reduceTransparency || settings.widgetGlass == WidgetGlass.SOLID) 1f else
            chosenOpacity.let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0.65f }
        fun alpha(color: Int) = Palettes.withAlpha(color, (opacity * 255).roundToInt())
        val photo = if (a.backgroundStyle == BackgroundStyle.IMAGE) a.backgroundImageUri?.let(WidgetPhotos::load) else null
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (photo != null) {
            // The photo fills the pane like a wallpaper (centre-cropped), within the chosen
            // opacity, under a wash of the background colour so text stays readable.
            val crop = max(width / photo.width, height / photo.height)
            val matrix = Matrix().apply {
                setScale(crop, crop)
                postTranslate((width - photo.width * crop) / 2f, (height - photo.height * crop) / 2f)
            }
            paint.isFilterBitmap = true
            paint.alpha = (opacity * 255).roundToInt()
            canvas.drawBitmap(photo, matrix, paint)
            paint.alpha = 255
            paint.color = Palettes.withAlpha(c.background, (opacity * 0.42f * 255).roundToInt())
            canvas.drawPath(shape, paint)
        } else if (a.backgroundStyle == BackgroundStyle.SOLID) {
            paint.color = alpha(c.background)
            canvas.drawPath(shape, paint)
        } else {
            // A steady gradient: the background colour at the bottom rising into the accent at
            // the top. The accent glow setting decides how much accent the top takes on.
            val top = Palettes.mix(c.background, c.accent, 0.25f + 0.5f * a.accentIntensity)
            paint.shader = LinearGradient(0f, 0f, 0f, height, alpha(top), alpha(c.background), Shader.TileMode.CLAMP)
            canvas.drawPath(shape, paint)
            paint.shader = null
        }
    }
}

/**
 * Photos chosen as widget backgrounds, decoded once and kept small. They live in
 * Delta's own storage, so they can be read whenever a widget is redrawn.
 */
object WidgetPhotos {
    private val cache = object : android.util.LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun load(uri: String): Bitmap? = cache.get(uri) ?: runCatching {
        val path = android.net.Uri.parse(uri).path ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1200) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()?.also { cache.put(uri, it) }
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
