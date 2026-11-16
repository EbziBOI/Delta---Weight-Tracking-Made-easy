package io.github.ebziboi.delta.render

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.SystemClock

/**
 * Keeps a see-through widget's coloured text readable over the wallpaper it
 * actually sits on. Colours are worked out for the widget's own background, but
 * glass lets the wallpaper through: a dark widget over a pale wallpaper becomes
 * a mid tone, where an orange or green figure can all but vanish. Colours that
 * fall short are drawn towards the text colour, only as far as they need to go,
 * so on a wallpaper that suits the widget nothing changes.
 */
object WidgetLegibility {
    /** [colors] as they should be drawn on [surface] at [opacity] over a wallpaper of tone [backdrop]. */
    fun over(colors: DrawColors, surface: Int, opacity: Float, backdrop: Int): DrawColors {
        val seen = Palettes.mix(backdrop, surface, opacity.coerceIn(0f, 1f))
        fun lift(color: Int, target: Double): Int {
            if (ThemeContrast.ratio(color, seen) >= target) return color
            val most = .6f
            if (ThemeContrast.ratio(Palettes.mix(color, colors.text, most), seen) < target) return Palettes.mix(color, colors.text, most)
            var low = 0f
            var high = most
            repeat(12) {
                val middle = (low + high) / 2f
                if (ThemeContrast.ratio(Palettes.mix(color, colors.text, middle), seen) >= target) high = middle else low = middle
            }
            return Palettes.mix(color, colors.text, high)
        }
        return colors.copy(
            textDim = lift(colors.textDim, 3.6),
            textFaint = lift(colors.textFaint, 3.0),
            accent = lift(colors.accent, 3.0),
            good = lift(colors.good, 3.0),
            warn = lift(colors.warn, 3.0),
        )
    }
}

/**
 * The wallpaper's overall tone, from the colours Android reports for it (no
 * permission needed). Unknown wallpapers count as a mid grey, the case that
 * troubles both light and dark widgets most.
 */
object WallpaperTone {
    private const val UNKNOWN = 0xFF808080.toInt()
    @Volatile private var cached: Pair<Long, Int>? = null

    fun backdrop(context: Context): Int {
        cached?.let { (at, color) -> if (SystemClock.elapsedRealtime() - at < 60_000L) return color }
        val color = runCatching {
            val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                ?: return@runCatching UNKNOWN
            val primary = colors.primaryColor.toArgb()
            val pale = Build.VERSION.SDK_INT >= 31 && (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
            // A wallpaper Android calls pale is lighter overall than its most common colour alone suggests.
            if (pale) Palettes.mix(primary, Color.WHITE, .4f) else primary
        }.getOrDefault(UNKNOWN)
        cached = SystemClock.elapsedRealtime() to color
        return color
    }
}
