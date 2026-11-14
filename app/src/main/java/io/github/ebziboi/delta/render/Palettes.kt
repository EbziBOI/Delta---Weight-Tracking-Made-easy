package io.github.ebziboi.delta.render

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import io.github.ebziboi.delta.data.Accent
import io.github.ebziboi.delta.data.ThemeStyle

/**
 * Every colour the app and the widgets draw with, as ARGB ints. The screens
 * and the widget bitmaps both read from this, so a theme change restyles both.
 */
data class DrawColors(
    val isLight: Boolean,
    val bg: Int,
    /** Top of the gradient behind widgets and the hero. */
    val bgTop: Int,
    val surface: Int,
    val surfaceHigh: Int,
    val outline: Int,
    val text: Int,
    val textDim: Int,
    val accent: Int,
    /** The part of the progress track still to go. */
    val accentSoft: Int,
    val onAccent: Int,
    val good: Int,
    val warn: Int,
    /** Neon themes add a soft glow to accent strokes. */
    val glow: Boolean,
)

object Palettes {

    fun resolve(context: Context, theme: ThemeStyle, accent: Accent): DrawColors = when (theme) {
        ThemeStyle.MIDNIGHT -> dark(
            bg = 0xFF070B1C, bgTop = 0xFF15224F, surface = 0xFF0F1838, surfaceHigh = 0xFF18265A,
            outline = 0xFF22306A, textDim = 0xFF9DAEDB, accent = accent.onDark, glow = false,
        )
        ThemeStyle.GRAPHITE -> dark(
            bg = 0xFF0B0C0F, bgTop = 0xFF1D2026, surface = 0xFF16181D, surfaceHigh = 0xFF21242B,
            outline = 0xFF2C3038, textDim = 0xFF9AA0AC, accent = accent.onDark, glow = false,
        )
        ThemeStyle.NEON -> dark(
            bg = 0xFF000000, bgTop = 0xFF0B0B0E, surface = 0xFF0A0A0C, surfaceHigh = 0xFF16161A,
            outline = withAlpha(accent.onDark.toInt(), 0x66).toLong() and 0xFFFFFFFF, textDim = 0xFF8E8E99,
            accent = accent.onDark, glow = true,
        )
        ThemeStyle.LIGHT -> DrawColors(
            isLight = true,
            bg = 0xFFF2F4F8.toInt(),
            bgTop = 0xFFFFFFFF.toInt(),
            surface = 0xFFFFFFFF.toInt(),
            surfaceHigh = 0xFFE8ECF3.toInt(),
            outline = 0xFFD7DDE8.toInt(),
            text = 0xFF101A3C.toInt(),
            textDim = 0xFF5B6785.toInt(),
            accent = accent.onLight.toInt(),
            accentSoft = withAlpha(accent.onLight.toInt(), 0x80),
            onAccent = 0xFFFFFFFF.toInt(),
            good = 0xFF0F9D6B.toInt(),
            warn = 0xFFB96A0B.toInt(),
            glow = false,
        )
        ThemeStyle.DYNAMIC -> dynamic(context) ?: resolve(context, ThemeStyle.MIDNIGHT, accent)
    }

    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    /** Blends [a] towards [b]; [t] of 0 is all [a], 1 is all [b]. */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun channel(shift: Int): Int {
            val from = (a shr shift) and 0xFF
            val to = (b shr shift) and 0xFF
            return (from + (to - from) * t).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun dark(
        bg: Long,
        bgTop: Long,
        surface: Long,
        surfaceHigh: Long,
        outline: Long,
        textDim: Long,
        accent: Long,
        glow: Boolean,
    ): DrawColors = DrawColors(
        isLight = false,
        bg = bg.toInt(),
        bgTop = bgTop.toInt(),
        surface = surface.toInt(),
        surfaceHigh = surfaceHigh.toInt(),
        outline = outline.toInt(),
        text = 0xFFFFFFFF.toInt(),
        textDim = textDim.toInt(),
        accent = accent.toInt(),
        accentSoft = withAlpha(accent.toInt(), 0x8C),
        onAccent = 0xFF06101F.toInt(),
        good = 0xFF34D399.toInt(),
        warn = 0xFFFBBF24.toInt(),
        glow = glow,
    )

    /** Material You: colours from the wallpaper, following the system light or dark setting. */
    private fun dynamic(context: Context): DrawColors? {
        if (Build.VERSION.SDK_INT < 31) return null
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        fun system(id: Int): Int = context.getColor(id)

        return if (night) {
            val base = system(android.R.color.system_neutral1_900)
            val raised = system(android.R.color.system_neutral1_800)
            val accent = system(android.R.color.system_accent1_200)
            DrawColors(
                isLight = false,
                bg = base,
                bgTop = mix(base, raised, 0.8f),
                surface = mix(base, raised, 0.4f),
                surfaceHigh = mix(base, raised, 0.85f),
                outline = system(android.R.color.system_neutral2_700),
                text = system(android.R.color.system_neutral1_50),
                textDim = system(android.R.color.system_neutral2_200),
                accent = accent,
                accentSoft = withAlpha(accent, 0x8C),
                onAccent = system(android.R.color.system_accent1_800),
                good = 0xFF34D399.toInt(),
                warn = 0xFFFBBF24.toInt(),
                glow = false,
            )
        } else {
            val accent = system(android.R.color.system_accent1_600)
            DrawColors(
                isLight = true,
                bg = system(android.R.color.system_neutral1_50),
                bgTop = system(android.R.color.system_neutral1_10),
                surface = system(android.R.color.system_neutral1_10),
                surfaceHigh = system(android.R.color.system_neutral2_100),
                outline = system(android.R.color.system_neutral2_200),
                text = system(android.R.color.system_neutral1_900),
                textDim = system(android.R.color.system_neutral2_700),
                accent = accent,
                accentSoft = withAlpha(accent, 0x80),
                onAccent = system(android.R.color.system_accent1_0),
                good = 0xFF0F9D6B.toInt(),
                warn = 0xFFB96A0B.toInt(),
                glow = false,
            )
        }
    }
}
