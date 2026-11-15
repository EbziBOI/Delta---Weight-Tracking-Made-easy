package io.github.ebziboi.delta.render

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import io.github.ebziboi.delta.data.ChartStyle
import io.github.ebziboi.delta.data.ProgressSnapshot
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

/** A dashed path from the latest trend value on to the target, at the current pace. */
data class Projection(
    /** When the line reaches [targetKg]. May lie beyond the right edge of the graph. */
    val atEpochMs: Long,
    val targetKg: Double,
)

/** Everything one graph shows, worked out from the snapshot and the user's choices. */
data class ChartSpec(
    val points: List<TrendPoint>,
    val style: ChartStyle,
    val unit: WeightUnit,
    val targetKg: Double?,
    /**
     * When set, the vertical scale always takes in the starting weight as well as
     * the target, so the distance covered and the distance left are drawn to the
     * same scale and the latest point sits as far down as the percentage says.
     */
    val startKg: Double? = null,
    /** Progress from start to target, 0 to 1, tagged beside the latest point. */
    val fraction: Float? = null,
    val projection: Projection? = null,
    /** The goal date, marked on the time axis when it falls inside the graph. */
    val targetEpochMs: Long? = null,
    val metric: ChartMetric = ChartMetric.WEIGHT,
    val showFullProjection: Boolean = false,
    val viewport: GraphViewport? = null,
    val selectedPoint: TrendPoint? = null,
) {
    companion object {
        fun forWidget(snap: ProgressSnapshot, settings: io.github.ebziboi.delta.data.AppSettings,
                      now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): ChartSpec {
            val journey = journey(snap, settings.widgetChart, settings.unit, settings.showProjection, zone)
            val full = settings.widgetTimeRange == io.github.ebziboi.delta.data.WidgetTimeRange.JOURNEY
            return journey.copy(points = settings.widgetTimeRange.weightPoints(snap, now, zone),
                startKg = if (full) snap.startKg else null, fraction = if (full) snap.fraction else null,
                projection = if (full) journey.projection else null,
                targetKg = snap.targetKg.takeIf { settings.widgetContent.showTarget },
                targetEpochMs = if (full) journey.targetEpochMs else null)
        }

        /** The whole journey, as the Progress screen's "All" range and the widgets show it. */
        fun journey(
            snap: ProgressSnapshot,
            style: ChartStyle,
            unit: WeightUnit,
            showProjection: Boolean,
            zone: ZoneId = ZoneId.systemDefault(),
        ): ChartSpec {
            val target = snap.targetKg
            val projected = snap.projectedDay
            val projection = if (showProjection && target != null && projected != null && !snap.reached) {
                Projection(projected.atStartOfDay(zone).toInstant().toEpochMilli(), target)
            } else {
                null
            }
            return ChartSpec(
                points = snap.points,
                style = style,
                unit = unit,
                targetKg = target,
                startKg = snap.startKg,
                fraction = snap.fraction,
                projection = projection,
                targetEpochMs = snap.targetDay?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
            )
        }
    }
}

/**
 * The weight graph: the smoothed trend as a line over a soft fill, each raw
 * weigh-in as a faint dot, and the target as a dashed line to aim at.
 * Used by the Progress screen and by the Trend and Journey widgets.
 */
object TrendChart {
    private const val DAY_MS = 86_400_000L

    private val dayLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun draw(
        canvas: Canvas,
        area: RectF,
        spec: ChartSpec,
        colors: DrawColors,
        type: CanvasType,
        density: Float,
        labels: Boolean,
        reveal: Float = 1f,
        zone: ZoneId = ZoneId.systemDefault(),
        contrastShadow: Boolean = false,
    ): ChartLayout? {
        val points = spec.points.filter { it.kg.isFinite() && it.trendKg.isFinite() }.sortedBy { it.epochMs }
        val targetKg = spec.targetKg
        val unit = spec.unit
        val caption = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = type.number
            textSize = 10f * density
            color = colors.textDim
            fontFeatureSettings = "tnum"
            if (contrastShadow) setShadowLayer(2f * density, 0f, density, if (colors.isLight) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
        }

        if (points.size < 2) {
            caption.textAlign = Paint.Align.CENTER
            caption.textSize = 12f * density
            val middle = area.centerY() - (caption.ascent() + caption.descent()) / 2f
            canvas.drawText(if (points.isEmpty()) "No readings in this range" else if (spec.metric == ChartMetric.BODY_FAT) "Two body fat readings make a trend" else "Two weigh-ins make a trend", area.centerX(), middle, caption)
            return null
        }

        // Vertical range: the data, plus the target so the goal is always in view.
        var low = points.minOf { min(it.kg, it.trendKg) }
        var high = points.maxOf { max(it.kg, it.trendKg) }
        if (targetKg != null) {
            low = min(low, targetKg)
            high = max(high, targetKg)
        }
        spec.startKg?.let { low = min(low, it); high = max(high, it) }
        val margin = max((high - low) * 0.08, 0.25)
        val fullLow = if (spec.metric == ChartMetric.BODY_FAT) (low - margin).coerceAtLeast(0.0) else low - margin
        val fullHigh = if (spec.metric == ChartMetric.BODY_FAT) (high + margin).coerceAtMost(100.0) else high + margin
        val bottomKg = spec.viewport?.low ?: fullLow
        val topKg = spec.viewport?.high ?: fullHigh

        val toDisplay = if (spec.metric == ChartMetric.BODY_FAT || unit == WeightUnit.KG) 1.0 else Units.LB_PER_KG
        var tickStep = niceStep((topKg - bottomKg) * toDisplay / 4.5)
        if (spec.metric == ChartMetric.WEIGHT && unit == WeightUnit.ST_LB) tickStep = max(1.0, ceil(tickStep))
        val ticks = ArrayList<Double>()
        var tick = ceil(bottomKg * toDisplay / tickStep) * tickStep
        while (tick <= topKg * toDisplay + 1e-9) {
            ticks += tick / toDisplay
            tick += tickStep
        }

        fun axis(value: Double) = if (spec.metric == ChartMetric.BODY_FAT) String.format(Locale.getDefault(), "%.1f%%", value) else Units.axis(value, unit)
        val gutter = if (labels && ticks.isNotEmpty()) {
            ticks.maxOf { caption.measureText(axis(it)) } + 8f * density
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
        if (plot.width() <= 0f || plot.height() <= 0f) return null

        val fullFirst = points.first().epochMs
        val dataEnd = max(points.last().epochMs, fullFirst + DAY_MS)
        // Keep at least two thirds of the width for recorded data.
        val horizon = dataEnd + max(7 * DAY_MS, (dataEnd - fullFirst) / 2)
        val projection = spec.projection?.takeIf { it.atEpochMs > points.last().epochMs }
        val fullLast = projection?.let { if (spec.showFullProjection) it.atEpochMs else min(it.atEpochMs, horizon) }?.coerceAtLeast(dataEnd) ?: dataEnd
        val firstMs = spec.viewport?.first ?: fullFirst
        val lastMs = spec.viewport?.last ?: fullLast
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
                canvas.drawText(axis(value), plot.left - 6f * density, baseline, caption)
            }
        }

        // Target.
        if (targetKg != null && targetKg in bottomKg..topKg) {
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

        val drawing = canvas.save()
        canvas.clipRect(plot.left - 7f * density, plot.top - 7f * density,
            plot.left + (plot.width() + 7f * density) * reveal.coerceIn(0f, 1f), plot.bottom + 7f * density)
        fun value(point: TrendPoint) = if (spec.style == ChartStyle.CONNECTED) point.kg else point.trendKg

        // Fill under the selected line.
        val line = Path()
        val fill = Path()
        points.forEachIndexed { index, point ->
            val px = x(point.epochMs)
            val py = y(value(point))
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
        // The wash hangs just below the line and fades out within a short drop, so a graph
        // whose scale runs down to a distant target does not fill with a solid slab.
        val washTop = points.minOf { y(value(it)) }
        val washBottom = minOf(plot.bottom, washTop + 110f * density)
        val wash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f, washTop, 0f, maxOf(washBottom, washTop + 1f),
                Palettes.withAlpha(colors.accent, 0x48), Palettes.withAlpha(colors.accent, 0x00),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(fill, wash)

        // Raw weigh-ins.
        val raw = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (spec.style == ChartStyle.CONNECTED) colors.accent else Palettes.withAlpha(colors.textDim, 0x8C)
        }
        val rawRadius = (if (points.size > 150) 1.1f else if (spec.style == ChartStyle.CONNECTED) 2.5f else 1.7f) * density
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
        if (contrastShadow) stroke.setShadowLayer(2f * density, 0f, density, if (colors.isLight) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
        else if (colors.glow) stroke.setShadowLayer(6f * density, 0f, 0f, colors.accent)
        canvas.drawPath(line, stroke)

        // Where things stand now.
        val last = points.last()
        val lastX = x(last.epochMs)
        val lastY = y(value(last))
        if (projection != null) {
            val endMs = min(projection.atEpochMs, lastMs)
            val ratio = (endMs - last.epochMs).toDouble() / (projection.atEpochMs - last.epochMs)
            val endY = y(value(last) + (projection.targetKg - value(last)) * ratio)
            val dash = Paint(stroke).apply {
                clearShadowLayer()
                strokeWidth = 1.5f * density
                pathEffect = DashPathEffect(floatArrayOf(5f * density, 4f * density), 0f)
            }
            canvas.drawLine(lastX, lastY, x(endMs), endY, dash)
            if (projection.atEpochMs > lastMs) {
                dash.pathEffect = null
                val arrow = Path().apply {
                    moveTo(plot.right - 5f * density, endY - 4f * density)
                    lineTo(plot.right, endY)
                    lineTo(plot.right - 5f * density, endY + 4f * density)
                }
                canvas.drawPath(arrow, dash)
            }
        }
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
        canvas.restoreToCount(drawing)

        spec.selectedPoint?.let { selected ->
            val sx = x(selected.epochMs); val sy = y(selected.kg)
            if (plot.includes(sx, sy)) {
                val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.text; strokeWidth = density; alpha = 100 }
                canvas.drawLine(sx, plot.top, sx, plot.bottom, cross)
                cross.alpha = 255; canvas.drawCircle(sx, sy, 6f * density, cross)
                cross.color = colors.accent; canvas.drawCircle(sx, sy, 3.5f * density, cross)
            }
        }
        if (labels && reveal >= 1f && plot.includes(lastX, lastY)) {
            spec.fraction?.let { fraction ->
                val tag = Paint(caption).apply { color = colors.accent; textAlign = Paint.Align.RIGHT }
                val text = "${(fraction.coerceIn(0f, 1f) * 100).toInt()}%"
                canvas.drawText(text, lastX.coerceAtLeast(plot.left + tag.measureText(text)),
                    (lastY - 10f * density).coerceIn(plot.top + tag.textSize, plot.bottom), tag)
            }
        }

        if (labels) {
            val baseline = area.bottom - 3f * density
            caption.textAlign = Paint.Align.LEFT
            canvas.drawText(format(firstMs, zone), plot.left, baseline, caption)
            caption.textAlign = Paint.Align.RIGHT
            canvas.drawText(format(lastMs, zone), plot.right, baseline, caption)
            spec.targetEpochMs?.takeIf { it > firstMs && it < lastMs }?.let { goal ->
                val text = "Goal ${format(goal, zone)}"
                val center = x(goal)
                val width = caption.measureText(text)
                if (center - width / 2 > plot.left + caption.measureText(format(firstMs, zone)) + 8f * density &&
                    center + width / 2 < plot.right - caption.measureText(format(lastMs, zone)) - 8f * density) {
                    caption.textAlign = Paint.Align.CENTER
                    caption.color = colors.accent
                    canvas.drawText(text, center, baseline, caption)
                }
            }
        }
        return ChartLayout(plot, GraphViewport(fullFirst, fullLast, fullLow, fullHigh),
            GraphViewport(firstMs, lastMs, bottomKg, topKg), points.map { ChartHit(it, x(it.epochMs), y(it.kg)) })
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
