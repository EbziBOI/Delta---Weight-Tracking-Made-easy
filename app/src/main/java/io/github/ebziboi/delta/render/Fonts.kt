package io.github.ebziboi.delta.render

import android.content.Context
import android.graphics.Typeface
import io.github.ebziboi.delta.R
import io.github.ebziboi.delta.data.TypeStyle
import java.util.concurrent.ConcurrentHashMap

/**
 * The faces one type pairing uses, ready for drawing on a canvas. The widgets
 * and the graph draw with these; the screens use the same files through
 * Compose (see ui/Type.kt), so a pairing looks the same everywhere.
 */
class CanvasType(
    val style: TypeStyle,
    /** Large readouts and headings: the weight in a widget, the percentage in the delta. */
    val display: Typeface,
    /** An italic partner to [display] for a word of emphasis. The same as [display] where there is none. */
    val displayItalic: Typeface,
    /** Figures in values: weights, rates, dates. */
    val number: Typeface,
    /** Heavier figures for the value that matters most in a group. */
    val numberBold: Typeface,
    /** Running text. */
    val body: Typeface,
    val bodyBold: Typeface,
    /** Small capitals above values ("START", "NOW", "TARGET"). */
    val label: Typeface,
    /** Letter spacing for [label], in ems. */
    val labelTracking: Float,
    /**
     * How much larger [display] must be drawn to look as big as the other faces.
     * The serif's figures are small for its size, so it is scaled up.
     */
    val displayScale: Float,
    /** True when [display] has figures of equal width, so a readout does not shimmer as it changes. */
    val displayTabular: Boolean,
)

object DeltaFonts {
    private val cache = ConcurrentHashMap<Int, Typeface>()

    fun canvas(context: Context, style: TypeStyle): CanvasType {
        fun face(id: Int): Typeface = load(context, id)
        return when (style) {
            TypeStyle.EDITORIAL -> CanvasType(
                style = style,
                display = face(R.font.instrument_serif_regular),
                displayItalic = face(R.font.instrument_serif_italic),
                number = face(R.font.space_grotesk_medium),
                numberBold = face(R.font.space_grotesk_bold),
                body = face(R.font.manrope_medium),
                bodyBold = face(R.font.manrope_bold),
                label = face(R.font.jetbrains_mono_medium),
                labelTracking = 0.12f,
                displayScale = 1.16f,
                displayTabular = false,
            )
            TypeStyle.MODERN -> CanvasType(
                style = style,
                display = face(R.font.space_grotesk_semibold),
                displayItalic = face(R.font.space_grotesk_semibold),
                number = face(R.font.space_grotesk_medium),
                numberBold = face(R.font.space_grotesk_bold),
                body = face(R.font.manrope_medium),
                bodyBold = face(R.font.manrope_bold),
                label = face(R.font.manrope_bold),
                labelTracking = 0.1f,
                displayScale = 1f,
                displayTabular = true,
            )
            TypeStyle.TECHNICAL -> CanvasType(
                style = style,
                display = face(R.font.jetbrains_mono_medium),
                displayItalic = face(R.font.jetbrains_mono_medium),
                number = face(R.font.jetbrains_mono_medium),
                numberBold = face(R.font.jetbrains_mono_bold),
                body = face(R.font.manrope_medium),
                bodyBold = face(R.font.manrope_bold),
                label = face(R.font.jetbrains_mono_medium),
                labelTracking = 0.08f,
                displayScale = 0.92f,
                displayTabular = true,
            )
        }
    }

    /** Fonts are read from the app once and kept; a font that cannot be read falls back to the system face. */
    private fun load(context: Context, id: Int): Typeface = cache.getOrPut(id) {
        runCatching { context.resources.getFont(id) }.getOrDefault(Typeface.DEFAULT)
    }
}
