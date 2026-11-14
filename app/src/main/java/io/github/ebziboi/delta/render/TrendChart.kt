package io.github.ebziboi.delta.render

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import io.github.ebziboi.delta.data.TrendPoint
import io.github.ebziboi.delta.data.Units
import io.github.ebziboi.delta.data.WeightUnit
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The weight graph: the smoothed trend as a line over a soft fill, each raw
 * weigh-in as a faint dot, and the target as a dashed line to aim at.
 * Used by the Progress screen and by the Trend widget.
 */
object TrendChart {
    private const val DAY_MS = 86_400_000L

    private val dayLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val face: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun draw(
        canvas: Canvas,
        area: RectF,
        points: List<TrendPoint>,
        targetKg: Double?,
        unit: WeightUnit,
        colors: DrawColors,
        density: Float,
        labels: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val caption = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = face
            textSize = 10f * density
            color = colors.textDim
            fontFeatureSettings = "tnum"
        }

        if (points.size < 2) {
            caption.textAlign = Paint.Align.CENTER
            caption.textSize = 12f * density
            val middle = area.centerY() - (caption.ascent() + caption.descent()) / 2f
            canvas.drawText("Two weigh-ins make a trend", area.centerX(), middle, caption)
            return
        }

        // Vertical range: the data, plus the target so the goal is always in view.
        var low = points.minOf { min(it.kg, it.trendKg) }
        var high = points.maxOf { max(it.kg, it.trendKg) }
        if (targetKg != null) {
            low = min(low, targetKg)
            high = max(high, targetKg)
        }
        val margin = max((high - low) * 0.08, 0.25)
        val bottomKg = low - margin
        val topKg = high + margin

        val toDisplay = if (unit == WeightUnit.KG) 1.0 else Units.LB_PER_KG
        var tickStep = niceStep((topKg - bottomKg) * toDisplay / 4.5)
        if (unit == WeightUnit.ST_LB) tickStep = max(1.0, ceil(tickStep))
        val ticks = ArrayList<Double>()
        var tick = ceil(bottomKg * toDisplay / tickStep) * tickStep
        while (tick <= topKg * toDisplay + 1e-9) {
            ticks += tick / toDisplay
            tick += tickStep
        }

        val gutter = if (labels && ticks.isNotEmpty()) {
            ticks.maxOf { caption.measureText(Units.axis(it, unit)) } + 8f * density
        } else {
            0f
        }
        val footer = if (labels) 16f * density else 0f
        val plot = RectF(
            area.left + gutter,
            area.top + 6f * density,
            area.right - 6f * density,
            area.bottom - footer - 2f * density,
        )
        if (plot.width() <= 0f || plot.height() <= 0f) return

        val firstMs = points.first().epochMs
        val lastMs = max(points.last().epochMs, firstMs + DAY_MS)
        fun x(epochMs: Long): Float =
            plot.left + (epochMs - firstMs).toFloat() / (lastMs - firstMs).toFloat() * plot.width()
        fun y(kg: Double): Float =
            plot.bottom - ((kg - bottomKg) / (topKg - bottomKg)).toFloat() * plot.height()

        // Grid.
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density
            color = Palettes.withAlpha(colors.textDim, 0x2B)
        }
        caption.textAlign = Paint.Align.RIGHT
        for (value in ticks) {
            val lineY = y(value)
            canvas.drawLine(plot.left, lineY, plot.right, lineY, grid)
            if (labels) {
                val baseline = lineY - (caption.ascent() + caption.descent()) / 2f
                canvas.drawText(Units.axis(value, unit), plot.left - 6f * density, baseline, caption)
            }
        }

        // Target.
        if (targetKg != null) {
            val lineY = y(targetKg)
            val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * density
                color = Palettes.withAlpha(colors.accent, 0xCC)
                pathEffect = DashPathEffect(floatArrayOf(6f * density, 5f * density), 0f)
            }
            canvas.drawLine(plot.left, lineY, plot.right, lineY, dashed)
            if (labels) {
                val tag = Paint(caption).apply {
                    textAlign = Paint.Align.LEFT
                    color = colors.accent
                }
                val above = lineY - 5f * density
                val baseline = if (above - tag.textSize < plot.top) lineY + 13f * density else above
                canvas.drawText("Target", plot.left + 4f * density, baseline, tag)
            }
        }

        // Fill under the trend.
        val line = Path()
        val fill = Path()
        points.forEachIndexed { index, point ->
            val px = x(point.epochMs)
            val py = y(point.trendKg)
            if (index == 0) {
                line.moveTo(px, py)
                fill.moveTo(px, py)
            } else {
                line.lineTo(px, py)
                fill.lineTo(px, py)
            }
        }
        fill.lineTo(x(points.last().epochMs), plot.bottom)
        fill.lineTo(x(firstMs), plot.bottom)
        fill.close()
        val wash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f, plot.top, 0f, plot.bottom,
                Palettes.withAlpha(colors.accent, 0x55), Palettes.withAlpha(colors.accent, 0x00),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(fill, wash)

        // Raw weigh-ins.
        val raw = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Palettes.withAlpha(colors.textDim, 0x8C)
        }
        val rawRadius = (if (points.size > 150) 1.1f else 1.7f) * density
        for (point in points) {
            canvas.drawCircle(x(point.epochMs), y(point.kg), rawRadius, raw)
        }

        // Trend line.
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = colors.accent
        }
        if (colors.glow) stroke.setShadowLayer(6f * density, 0f, 0f, colors.accent)
        canvas.drawPath(line, stroke)

        // Where things stand now.
        val last = points.last()
        val lastX = x(last.epochMs)
        val lastY = y(last.trendKg)
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = colors.bg
        }
        val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = colors.accent
        }
        canvas.drawCircle(lastX, lastY, 6f * density, halo)
        canvas.drawCircle(lastX, lastY, 4f * density, marker)

        if (labels) {
            val baseline = area.bottom - 3f * density
            caption.textAlign = Paint.Align.LEFT
            canvas.drawText(format(firstMs, zone), plot.left, baseline, caption)
            caption.textAlign = Paint.Align.RIGHT
            canvas.drawText(format(last.epochMs, zone), plot.right, baseline, caption)
        }
    }

    private fun format(epochMs: Long, zone: ZoneId): String =
        dayLabel.format(Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate())

    /** Rounds a rough tick spacing to 1, 2, 2.5 or 5 times a power of ten. */
    private fun niceStep(rough: Double): Double {
        if (rough <= 0.0) return 1.0
        val magnitude = 10.0.pow(floor(log10(rough)))
        val scaled = rough / magnitude
        val nice = when {
            scaled <= 1.0 -> 1.0
            scaled <= 2.0 -> 2.0
            scaled <= 2.5 -> 2.5
            scaled <= 5.0 -> 5.0
            else -> 10.0
        }
        return nice * magnitude
    }
}
