package io.github.ebziboi.delta.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Typeface
import kotlin.math.max

/**
 * The Delta mark, drawn live. One delta-shaped track fills clockwise from the
 * apex as progress grows; what is still to go shows as dots leading back up to
 * the apex. Geometry is the approved app icon, in its 300-unit design space,
 * so the icon is simply this drawing frozen at one moment.
 */
object DeltaMark {
    private const val APEX_X = 150f
    private const val APEX_Y = 51.33f
    private const val RIGHT_X = 244.7f
    private const val LEFT_X = 55.3f
    private const val BASE_Y = 240.75f

    // Bounds of the mark including the width of the track.
    private const val MIN_X = 51.3f
    private const val MIN_Y = 47.33f
    private const val SPAN = 197.42f

    private const val TRACK_WIDTH = 8f
    private const val DOT_RADIUS = 3f

    /** The perimeter divided by this gives the dot spacing used in the icon. */
    private const val DOT_SLOTS = 52.1f

    private const val LABEL_BASELINE = 206f
    private const val LABEL_SIZE = 44f

    /**
     * Draws the mark inside the square at [left], [top] with side [size].
     * [fraction] runs 0 to 1. With [glyph] the letter is drawn in the middle,
     * as on the icon; without it the middle is left free for [drawLabel].
     */
    fun draw(
        canvas: Canvas,
        left: Float,
        top: Float,
        size: Float,
        fraction: Float,
        colors: DrawColors,
        glyph: Boolean,
    ) {
        val scale = size / SPAN
        val checkpoint = canvas.save()
        canvas.translate(left - MIN_X * scale, top - MIN_Y * scale)
        canvas.scale(scale, scale)

        val loop = Path().apply {
            moveTo(APEX_X, APEX_Y)
            lineTo(RIGHT_X, BASE_Y)
            lineTo(LEFT_X, BASE_Y)
            close()
        }
        val measure = PathMeasure(loop, false)
        val length = measure.length
        val progress = fraction.coerceIn(0f, 1f)
        val solidEnd = progress * length
        val spacing = length / DOT_SLOTS

        // Dots for what is left, walking back from the apex until they meet the track.
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = colors.accentSoft
        }
        val position = FloatArray(2)
        var step = 1
        while (true) {
            val distance = length - step * spacing
            if (distance <= solidEnd + spacing * 0.9f) break
            measure.getPosTan(distance, position, null)
            canvas.drawCircle(position[0], position[1], DOT_RADIUS, dot)
            step++
        }

        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = TRACK_WIDTH
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = colors.accent
        }
        if (colors.glow) track.setShadowLayer(7f, 0f, 0f, colors.accent)
        if (progress >= 0.999f) {
            canvas.drawPath(loop, track)
        } else {
            // Even at zero the track shows its starting point at the apex.
            val solid = Path()
            measure.getSegment(0f, max(solidEnd, 0.01f), solid, true)
            canvas.drawPath(solid, track)
        }

        if (glyph) {
            val letter = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                moveTo(144.93f, 107.254f)
                lineTo(155.07f, 107.254f)
                lineTo(211.57f, 220.254f)
                lineTo(88.43f, 220.254f)
                close()
                moveTo(138.82f, 135.124f)
                lineTo(102.75f, 207.254f)
                lineTo(174.89f, 207.254f)
                close()
            }
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = 3f
                strokeJoin = Paint.Join.ROUND
                color = colors.text
            }
            canvas.drawPath(letter, ink)
        }

        canvas.restoreToCount(checkpoint)
    }

    /**
     * Writes a short readout such as "62" with a smaller "%" inside the mark,
     * where the letter would otherwise sit. [sizeScale] lets a face whose figures
     * run small, such as the serif, be drawn larger to look the same size.
     */
    fun drawLabel(
        canvas: Canvas,
        left: Float,
        top: Float,
        size: Float,
        value: String,
        suffix: String,
        color: Int,
        typeface: Typeface,
        sizeScale: Float = 1f,
        shadowColor: Int? = null,
    ) {
        val scale = size / SPAN
        val big = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface
            textSize = (if (value.length >= 3) LABEL_SIZE * 0.86f else LABEL_SIZE) * scale * sizeScale
            fontFeatureSettings = "tnum"
            this.color = color
            shadowColor?.let { setShadowLayer(2f * scale, 0f, scale, it) }
        }
        val small = Paint(big).apply { textSize = big.textSize * 0.52f }

        val valueWidth = big.measureText(value)
        val total = valueWidth + small.measureText(suffix)
        val startX = left + (APEX_X - MIN_X) * scale - total / 2f
        val baseline = top + (LABEL_BASELINE - MIN_Y) * scale
        canvas.drawText(value, startX, baseline, big)
        canvas.drawText(suffix, startX + valueWidth, baseline, small)
    }
}
