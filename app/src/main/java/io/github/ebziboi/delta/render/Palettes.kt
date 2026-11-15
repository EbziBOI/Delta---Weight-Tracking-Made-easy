package io.github.ebziboi.delta.render

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import io.github.ebziboi.delta.data.Accent
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.data.ThemeStyle
import io.github.ebziboi.delta.data.WidgetTheme

/**
 * Every colour the app and the widgets draw with, as ARGB ints. The screens
 * and the widget bitmaps both read from this, so a theme change restyles both.
 */
data class DrawColors(
    val isLight: Boolean,
    /** The deepest layer, behind everything. */
    val bg: Int,
    /** Top of the gradient behind screens and solid widgets. */
    val bgTop: Int,
    /** An opaque panel, for menus and dialogs where glass would make text hard to read. */
    val surface: Int,
    /** A raised opaque panel: rails, inactive chips, pressed states. */
    val surfaceHigh: Int,
    /** Hairlines between rows and around opaque panels. */
    val outline: Int,
    val text: Int,
    val textDim: Int,
    /** Third-level text: axis labels, hints, timestamps. */
    val textFaint: Int,
    val accent: Int,
    /** The part of the progress track still to go. */
    val accentSoft: Int,
    val onAccent: Int,
    /** A partner hue for the second wash of colour and for gradients that start at [accent]. */
    val accent2: Int,
    val good: Int,
    val warn: Int,
    /** Neon themes add a soft glow to accent strokes. */
    val glow: Boolean,
    val glass: GlassTokens,
    /** Soft washes of colour behind the glass. */
    val aurora: List<AuroraBlob>,
)

/**
 * The translucent material that cards, bars and widgets are made of. Colours
 * carry their own alpha. Dark glass is a whisper of white over a deep
 * background; light glass is mostly white with a cool shadow beneath.
 */
data class GlassTokens(
    /** The body of the glass. */
    val fill: Int,
    /** The top of the body: glass catches more light at the top. */
    val fillTop: Int,
    /** The rim where light strikes it, at the top left. */
    val edge: Int,
    /** The rim in shadow, at the bottom right. */
    val edgeFaint: Int,
    /** A thin specular line just inside the top edge. */
    val highlight: Int,
    /** Drop shadow under raised glass. Fully transparent where a shadow would not show. */
    val shadow: Int,
    /** A near-opaque version of the glass for sheets and dialogs. */
    val scrim: Int,
)

/**
 * One soft wash of colour. [x] and [y] place its centre as fractions of the
 * area it lights, and may sit just outside it; [radius] is a fraction of the
 * area's longer side. [color] already carries its alpha and fades to clear at the edge.
 */
data class AuroraBlob(val color: Int, val x: Float, val y: Float, val radius: Float)

object Palettes {

    /** True when the phone (or a context made for one mode) is in dark mode. */
    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /**
     * The palette for [theme]. [night] matters only for themes that follow the
     * phone (Auto and Material You); the others are always light or always dark.
     */
    fun resolve(
        context: Context,
        theme: ThemeStyle,
        accent: Accent,
        night: Boolean = isNight(context),
    ): DrawColors = when (theme) {
        ThemeStyle.EMBER -> ember(accent)
        ThemeStyle.AURORA -> aurora(accent)
        ThemeStyle.MIDNIGHT -> midnight(accent)
        ThemeStyle.GRAPHITE -> graphite(accent)
        ThemeStyle.NEON -> neon(accent)
        ThemeStyle.LIGHT -> light(accent)
        ThemeStyle.AUTO -> if (night) aurora(accent) else light(accent)
        ThemeStyle.DYNAMIC -> dynamic(context, night) ?: if (night) midnight(accent) else light(accent)
    }

    /**
     * Whether the widgets look different in light and dark mode. When they do,
     * each widget is drawn twice and the home screen picks the right one as the
     * phone switches, without Delta having to run.
     */
    fun widgetFollowsSystem(settings: AppSettings): Boolean = when (settings.widgetTheme) {
        WidgetTheme.APP -> settings.appearance == null && settings.theme.followsSystem
        WidgetTheme.SYSTEM -> true
        WidgetTheme.LIGHT, WidgetTheme.DARK -> false
    }

    /**
     * Whether a widget is light or dark, given the phone's mode [night]. A widget
     * that follows its own fixed theme ignores [night].
     */
    fun widgetIsLight(settings: AppSettings, night: Boolean): Boolean = when (settings.widgetTheme) {
        WidgetTheme.APP -> settings.appearance?.let { ThemeContrast.on(it.backgroundBase) == android.graphics.Color.BLACK }
            ?: (settings.theme.fixedLight ?: !night)
        WidgetTheme.SYSTEM -> !night
        WidgetTheme.LIGHT -> true
        WidgetTheme.DARK -> false
    }

    /**
     * The palette a widget draws with when the phone is in mode [night]. The
     * app's own theme is kept wherever it suits the mode the widget needs, so a
     * Neon app gets Neon widgets in dark mode and light glass by day.
     */
    fun forWidget(context: Context, settings: AppSettings, night: Boolean): DrawColors {
        return AppearanceEngine.forWidget(context, settings, night).drawColors()
    }

    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    /** [color] with its alpha scaled by [factor], for fading something that is already translucent. */
    fun fade(color: Int, factor: Float): Int {
        val alpha = ((color ushr 24) * factor.coerceIn(0f, 1f)).toInt()
        return withAlpha(color, alpha)
    }

    /** Blends [a] towards [b]; [t] of 0 is all [a], 1 is all [b]. The result is opaque. */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun channel(shift: Int): Int {
            val from = (a shr shift) and 0xFF
            val to = (b shr shift) and 0xFF
            return (from + (to - from) * t).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    // ---- Dark themes ------------------------------------------------------------------------

    private val darkGlass = GlassTokens(
        fill = 0x14FFFFFF,
        fillTop = 0x21FFFFFF,
        edge = 0x4DFFFFFF,
        edgeFaint = 0x0DFFFFFF,
        highlight = 0x38FFFFFF,
        shadow = 0x59000000,
        scrim = 0,
    )

    /** Warm light pools above a near-black canvas; surfaces remain neutral smoked glass. */
    private fun ember(accent: Accent): DrawColors {
        val warm = 0xFFFF7A12.toInt()
        val red = 0xFFB82808.toInt()
        return dark(
            bg = 0xFF070606, bgTop = 0xFF281009, surface = 0xFF191311, surfaceHigh = 0xFF30221B,
            outline = 0xFF594034, textDim = 0xFFC1B3AA,
            accent = if (accent == Accent.ORANGE) 0xFFFF922E else accent.onDark,
            partner = if (accent == Accent.ORANGE) 0xFFFF4A0A else accent.partnerDark,
            glass = GlassTokens(
                fill = 0xD90C0A09.toInt(), fillTop = 0xA62B201B.toInt(),
                edge = 0x55F7CFAD, edgeFaint = 0x14F7CFAD, highlight = 0x26FFFFFF,
                shadow = 0x99000000.toInt(), scrim = 0xF516100D.toInt(),
            ),
            aurora = listOf(
                AuroraBlob(withAlpha(warm, 0xE0), x = 0.35f, y = -0.12f, radius = 0.64f),
                AuroraBlob(withAlpha(red, 0x90), x = 1.18f, y = 0.28f, radius = 0.40f),
                AuroraBlob(withAlpha(red, 0x64), x = -0.18f, y = 0.97f, radius = 0.36f),
            ),
            glow = false,
        )
    }

    private fun aurora(accent: Accent): DrawColors {
        val tint = accent.onDark.toInt()
        val partner = accent.partnerDark.toInt()
        return dark(
            bg = 0xFF08071A, bgTop = 0xFF141236, surface = 0xFF17153A, surfaceHigh = 0xFF232052,
            outline = 0xFF2E2A62, textDim = 0xFFB1AEDB, accent = accent.onDark, partner = accent.partnerDark,
            glass = darkGlass.copy(scrim = 0xF2141233.toInt()),
            aurora = listOf(
                AuroraBlob(withAlpha(tint, 0x66), x = 0.08f, y = 0.04f, radius = 0.62f),
                AuroraBlob(withAlpha(partner, 0x59), x = 0.98f, y = 0.30f, radius = 0.58f),
                AuroraBlob(withAlpha(0xFF4338CA.toInt(), 0x66), x = 0.30f, y = 0.98f, radius = 0.70f),
            ),
            glow = false,
        )
    }

    private fun midnight(accent: Accent): DrawColors {
        val tint = accent.onDark.toInt()
        val partner = accent.partnerDark.toInt()
        return dark(
            bg = 0xFF070B1C, bgTop = 0xFF15224F, surface = 0xFF0F1838, surfaceHigh = 0xFF18265A,
            outline = 0xFF22306A, textDim = 0xFF9DAEDB, accent = accent.onDark, partner = accent.partnerDark,
            glass = darkGlass.copy(fill = 0x129DB4FF, fillTop = 0x1F9DB4FF, scrim = 0xF20F1838.toInt()),
            aurora = listOf(
                AuroraBlob(withAlpha(tint, 0x47), x = 0.92f, y = 0.02f, radius = 0.60f),
                AuroraBlob(withAlpha(0xFF2B3FB8.toInt(), 0x80), x = 0.02f, y = 0.42f, radius = 0.62f),
                AuroraBlob(withAlpha(partner, 0x29), x = 0.70f, y = 1.02f, radius = 0.60f),
            ),
            glow = false,
        )
    }

    private fun graphite(accent: Accent): DrawColors {
        val tint = accent.onDark.toInt()
        return dark(
            bg = 0xFF0B0C0F, bgTop = 0xFF1D2026, surface = 0xFF16181D, surfaceHigh = 0xFF21242B,
            outline = 0xFF2C3038, textDim = 0xFF9AA0AC, accent = accent.onDark, partner = accent.partnerDark,
            glass = darkGlass.copy(fill = 0x12FFFFFF, fillTop = 0x1CFFFFFF, scrim = 0xF216181D.toInt()),
            aurora = listOf(
                AuroraBlob(withAlpha(tint, 0x26), x = 0.95f, y = 0.00f, radius = 0.60f),
                AuroraBlob(0x14FFFFFF, x = 0.00f, y = 0.50f, radius = 0.60f),
                AuroraBlob(withAlpha(tint, 0x14), x = 0.55f, y = 1.02f, radius = 0.55f),
            ),
            glow = false,
        )
    }

    private fun neon(accent: Accent): DrawColors {
        val tint = accent.onDark.toInt()
        val partner = accent.partnerDark.toInt()
        return dark(
            bg = 0xFF000000, bgTop = 0xFF0B0B0E, surface = 0xFF0A0A0C, surfaceHigh = 0xFF16161A,
            outline = withAlpha(tint, 0x66).toLong() and 0xFFFFFFFF, textDim = 0xFF8E8E99,
            accent = accent.onDark, partner = accent.partnerDark,
            glass = GlassTokens(
                fill = 0x0DFFFFFF,
                fillTop = 0x17FFFFFF,
                edge = withAlpha(tint, 0x8C),
                edgeFaint = withAlpha(tint, 0x1F),
                highlight = withAlpha(tint, 0x4D),
                shadow = withAlpha(tint, 0x26),
                scrim = 0xF20A0A0C.toInt(),
            ),
            aurora = listOf(
                AuroraBlob(withAlpha(tint, 0x52), x = 0.50f, y = -0.06f, radius = 0.52f),
                AuroraBlob(withAlpha(partner, 0x2E), x = 1.02f, y = 0.88f, radius = 0.46f),
            ),
            glow = true,
        )
    }

    private fun dark(
        bg: Long,
        bgTop: Long,
        surface: Long,
        surfaceHigh: Long,
        outline: Long,
        textDim: Long,
        accent: Long,
        partner: Long,
        glass: GlassTokens,
        aurora: List<AuroraBlob>,
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
        textFaint = mix(textDim.toInt(), bg.toInt(), 0.38f),
        accent = accent.toInt(),
        accentSoft = withAlpha(accent.toInt(), 0x8C),
        onAccent = 0xFF06101F.toInt(),
        accent2 = partner.toInt(),
        good = 0xFF34D399.toInt(),
        warn = 0xFFFBBF24.toInt(),
        glow = glow,
        glass = glass,
        aurora = aurora,
    )

    // ---- Light theme ------------------------------------------------------------------------

    private fun light(accent: Accent): DrawColors {
        val tint = accent.onLight.toInt()
        val partner = accent.partnerLight.toInt()
        val ink = 0xFF0E1430.toInt()
        return DrawColors(
            isLight = true,
            bg = 0xFFEDF0F7.toInt(),
            bgTop = 0xFFFAFBFF.toInt(),
            surface = 0xFFFFFFFF.toInt(),
            surfaceHigh = 0xFFE6EAF3.toInt(),
            outline = 0xFFD5DBE7.toInt(),
            text = ink,
            textDim = 0xFF55617F.toInt(),
            textFaint = 0xFF8A93AD.toInt(),
            accent = tint,
            accentSoft = withAlpha(tint, 0x80),
            onAccent = 0xFFFFFFFF.toInt(),
            accent2 = partner,
            good = 0xFF0F9D6B.toInt(),
            warn = 0xFFB96A0B.toInt(),
            glow = false,
            glass = GlassTokens(
                fill = 0x9EFFFFFF.toInt(),
                fillTop = 0xDBFFFFFF.toInt(),
                edge = 0xFFFFFFFF.toInt(),
                edgeFaint = withAlpha(ink, 0x14),
                highlight = 0xFFFFFFFF.toInt(),
                shadow = withAlpha(0xFF2A3566.toInt(), 0x24),
                scrim = 0xF7FFFFFF.toInt(),
            ),
            aurora = listOf(
                AuroraBlob(withAlpha(tint, 0x38), x = 0.04f, y = 0.02f, radius = 0.62f),
                AuroraBlob(withAlpha(partner, 0x30), x = 1.00f, y = 0.34f, radius = 0.56f),
                AuroraBlob(withAlpha(0xFFFFB38A.toInt(), 0x47), x = 0.30f, y = 1.00f, radius = 0.62f),
            ),
        )
    }

    // ---- Material You -----------------------------------------------------------------------

    /** Colours from the wallpaper, in the light or dark version asked for. */
    private fun dynamic(context: Context, night: Boolean): DrawColors? {
        if (Build.VERSION.SDK_INT < 31) return null
        fun system(id: Int): Int = context.getColor(id)

        return if (night) {
            val base = system(android.R.color.system_neutral1_900)
            val raised = system(android.R.color.system_neutral1_800)
            val accent = system(android.R.color.system_accent1_200)
            val partner = system(android.R.color.system_accent3_200)
            val textDim = system(android.R.color.system_neutral2_200)
            DrawColors(
                isLight = false,
                bg = base,
                bgTop = mix(base, raised, 0.8f),
                surface = mix(base, raised, 0.5f),
                surfaceHigh = mix(base, raised, 0.95f),
                outline = system(android.R.color.system_neutral2_700),
                text = system(android.R.color.system_neutral1_50),
                textDim = textDim,
                textFaint = mix(textDim, base, 0.38f),
                accent = accent,
                accentSoft = withAlpha(accent, 0x8C),
                onAccent = system(android.R.color.system_accent1_800),
                accent2 = partner,
                good = 0xFF34D399.toInt(),
                warn = 0xFFFBBF24.toInt(),
                glow = false,
                glass = darkGlass.copy(scrim = withAlpha(mix(base, raised, 0.5f), 0xF2)),
                aurora = listOf(
                    AuroraBlob(withAlpha(accent, 0x52), x = 0.08f, y = 0.04f, radius = 0.62f),
                    AuroraBlob(withAlpha(partner, 0x40), x = 0.98f, y = 0.32f, radius = 0.56f),
                    AuroraBlob(withAlpha(system(android.R.color.system_accent2_400), 0x47), x = 0.30f, y = 1.0f, radius = 0.66f),
                ),
            )
        } else {
            val accent = system(android.R.color.system_accent1_600)
            val partner = system(android.R.color.system_accent3_500)
            val ink = system(android.R.color.system_neutral1_900)
            DrawColors(
                isLight = true,
                bg = system(android.R.color.system_neutral1_50),
                bgTop = system(android.R.color.system_neutral1_10),
                surface = system(android.R.color.system_neutral1_10),
                surfaceHigh = system(android.R.color.system_neutral2_100),
                outline = system(android.R.color.system_neutral2_200),
                text = ink,
                textDim = system(android.R.color.system_neutral2_700),
                textFaint = system(android.R.color.system_neutral2_500),
                accent = accent,
                accentSoft = withAlpha(accent, 0x80),
                onAccent = system(android.R.color.system_accent1_0),
                accent2 = partner,
                good = 0xFF0F9D6B.toInt(),
                warn = 0xFFB96A0B.toInt(),
                glow = false,
                glass = GlassTokens(
                    fill = 0x9EFFFFFF.toInt(),
                    fillTop = 0xDBFFFFFF.toInt(),
                    edge = 0xFFFFFFFF.toInt(),
                    edgeFaint = withAlpha(ink, 0x14),
                    highlight = 0xFFFFFFFF.toInt(),
                    shadow = withAlpha(ink, 0x1F),
                    scrim = withAlpha(system(android.R.color.system_neutral1_10), 0xF7),
                ),
                aurora = listOf(
                    AuroraBlob(withAlpha(system(android.R.color.system_accent1_300), 0x4D), x = 0.04f, y = 0.02f, radius = 0.62f),
                    AuroraBlob(withAlpha(system(android.R.color.system_accent3_300), 0x40), x = 1.0f, y = 0.34f, radius = 0.56f),
                    AuroraBlob(withAlpha(system(android.R.color.system_accent2_200), 0x59), x = 0.30f, y = 1.0f, radius = 0.62f),
                ),
            )
        }
    }
}
