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
        // With a shader the gradient colours carry the opacity, so the paint itself stays opaque:
        // a paint's alpha multiplies its shader and would apply the opacity twice.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (a.backgroundStyle == BackgroundStyle.SOLID) alpha(c.background) else Color.WHITE
            shader = when (a.backgroundStyle) {
                BackgroundStyle.SOLID -> null
                BackgroundStyle.RADIAL_GRADIENT -> RadialGradient(width * 0.2f, height * 0.1f, max(width, height),
                    alpha(theme.backgroundSecondary), alpha(c.background), Shader.TileMode.CLAMP)
                else -> LinearGradient(0f, 0f, 0f, height, alpha(theme.backgroundSecondary), alpha(c.background), Shader.TileMode.CLAMP)
            }
        }
        val photo = if (a.backgroundStyle == BackgroundStyle.IMAGE) a.backgroundImageUri?.let(WidgetPhotos::load) else null
        if (photo != null) {
            // The photo fills the pane like a wallpaper (centre-cropped), within the glass's
            // opacity, under a wash of the background colour so text stays readable.
            val crop = max(width / photo.width, height / photo.height)
            val matrix = Matrix().apply {
                setScale(crop, crop)
                postTranslate((width - photo.width * crop) / 2f, (height - photo.height * crop) / 2f)
            }
            val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = (opacity * 255).roundToInt() }
            canvas.drawBitmap(photo, matrix, image)
            paint.shader = null
            paint.color = Palettes.withAlpha(c.background, (opacity * 0.42f * 255).roundToInt())
            canvas.drawPath(shape, paint)
        } else {
            canvas.drawPath(shape, paint)
        }
        paint.color = Color.WHITE
        if (a.accentIntensity > 0 && a.backgroundStyle != BackgroundStyle.SOLID && !a.reduceTransparency) {
            paint.shader = RadialGradient(width * 0.15f, 0f, max(width, height) * 0.9f,
                Palettes.fade(c.accent, a.accentIntensity * opacity * 0.22f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawPath(shape, paint)
        }
        // Shading is inside the shape; no straight highlight cuts across its curved corners.
        paint.shader = LinearGradient(0f, height * 0.55f, 0f, height,
            Color.TRANSPARENT, Palettes.withAlpha(Color.BLACK, (opacity * 30).toInt()), Shader.TileMode.CLAMP)
        canvas.drawPath(shape, paint)
        // The rim follows the app's light rule: a thin outline all round, light
        // pooling at the centre of the top edge, wrapping the upper corners and
        // fading down the sides, with almost nothing along the bottom.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.shader = null
        paint.color = Palettes.fade(c.border, 0.3f)
        canvas.drawRoundRect(0.5f, 0.5f, width - 0.5f, height - 0.5f, max(0f, radius - 0.5f), max(0f, radius - 0.5f), paint)

        val light = if (theme.isLight) Color.WHITE else theme.textPrimary
        val side = Palettes.withAlpha(light, if (theme.isLight) 150 else 96)
        val crest = Palettes.withAlpha(light, if (theme.isLight) 245 else 205)
        val inset = 0.8f
        val rimRadius = max(0f, radius - inset)
        // A paint's own alpha multiplies its shader, so reset it to opaque before the gradients.
        paint.color = Color.WHITE
        paint.strokeWidth = 1.4f
        paint.shader = LinearGradient(0f, 0f, 0f, height,
            intArrayOf(side, Palettes.fade(side, 0.45f), Palettes.fade(side, 0.08f), Color.TRANSPARENT),
            floatArrayOf(0f, 0.28f, 0.62f, 0.8f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, rimRadius, rimRadius, paint)
        paint.shader = RadialGradient(width / 2f, 0f, max(width * 0.62f, 1f),
            intArrayOf(crest, Palettes.fade(crest, 0.35f), Color.TRANSPARENT), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, rimRadius, rimRadius, paint)
        if (a.accentIntensity > 0 && !a.reduceTransparency) {
            paint.strokeWidth = 1f
            paint.shader = RadialGradient(width / 2f, 0f, max(width * 0.4f, 1f),
                Palettes.fade(c.accent, a.accentIntensity * 0.7f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, rimRadius, rimRadius, paint)
        }
        paint.shader = null
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
