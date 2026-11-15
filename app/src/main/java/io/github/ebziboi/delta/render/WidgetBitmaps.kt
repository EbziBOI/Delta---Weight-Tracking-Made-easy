package io.github.ebziboi.delta.render

import android.content.Context
import android.graphics.*
import android.os.Build
import io.github.ebziboi.delta.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

enum class WidgetKind(val title: String, val detail: String) {
    BODY_FAT("Body fat", "Your body fat percentage, change and trend"),
    PROGRESS("Progress", "The delta, filling as you close the gap"),
    JOURNEY("Journey", "Your graph and progress bar together"),
    SUMMARY("Start, now, target", "Three numbers over a progress bar"),
    TREND("Trend", "Your weight graph with the target line"),
    PACE("Pace", "Weekly rate, projected date and streak"),
}

/** The same renderer supplies home-screen widgets, in-app previews and picker images. */
object WidgetBitmaps {
    const val DEFAULT_MAX_SIDE_PX = 1000f
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun render(
        context: Context, kind: WidgetKind, widthDp: Float, heightDp: Float,
        snap: ProgressSnapshot, settings: AppSettings,
        night: Boolean = Palettes.isNight(context), maxSidePx: Float = DEFAULT_MAX_SIDE_PX,
    ): Bitmap {
        require(widthDp.isFinite() && widthDp > 0 && heightDp.isFinite() && heightDp > 0)
        require(maxSidePx.isFinite() && maxSidePx >= 1)
        val scale = min(context.resources.displayMetrics.density, maxSidePx / max(widthDp, heightDp))
        val bitmap = Bitmap.createBitmap(max(1, (widthDp * scale).roundToInt()), max(1, (heightDp * scale).roundToInt()), Bitmap.Config.ARGB_8888)
        val theme = AppearanceEngine.forWidget(context, settings, night, kind)
        val palette = theme.drawColors()
        val readable = if (settings.widgetGlass == WidgetGlass.CLEAR) palette.copy(textDim = palette.text, textFaint = palette.text) else palette
        // A widget may ask for light or dark text whatever its background; automatic keeps the contrast-derived colours.
        val colors = when (settings.widgetContent.text) {
            TextContrast.AUTO -> readable
            TextContrast.LIGHT -> readable.copy(text = Color.WHITE, textDim = 0xFFDCDDE5.toInt(), textFaint = 0xFFB6B8C4.toInt())
            TextContrast.DARK -> readable.copy(text = 0xFF0E0F14.toInt(), textDim = 0xFF383A46.toInt(), textFaint = 0xFF5A5D6A.toInt())
        }
        val type = DeltaFonts.canvas(context, settings.typeStyle)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        GlassWidgetSurface.draw(canvas, widthDp, heightDp, theme, settings, WidgetCorners.radius(context, theme.appearance.cornerRadius))

        // Draw tiny hosts in a larger logical coordinate space so content scales down together.
        val fit = min(1f, min(widthDp / 150f, heightDp / 82f))
        canvas.scale(fit, fit)
        val painter = Painter(context, canvas, widthDp / fit, heightDp / fit, snap, settings, colors, type)
        when (kind) {
            WidgetKind.BODY_FAT -> painter.bodyFat()
            WidgetKind.PROGRESS -> painter.progress()
            WidgetKind.JOURNEY -> painter.trend(journey = true)
            WidgetKind.SUMMARY -> painter.summary()
            WidgetKind.TREND -> painter.trend(journey = false)
            WidgetKind.PACE -> painter.pace()
        }
        return bitmap
    }

    private class Painter(
        val context: Context, val c: Canvas, val w: Float, val h: Float,
        val snap: ProgressSnapshot, val settings: AppSettings, val colors: DrawColors, val type: CanvasType,
    ) {
        val pad = 14f
        val unit get() = settings.unit
        val show get() = settings.widgetContent
        fun text(value: String, x: Float, y: Float, size: Float = 12f, color: Int = colors.text,
                 face: Typeface = type.number, align: Paint.Align = Paint.Align.LEFT, room: Float = w - pad * 2) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                textSize = size; this.color = color; typeface = face; textAlign = align; fontFeatureSettings = "tnum"
                if (settings.widgetGlass != WidgetGlass.SOLID && (settings.appearance?.designStyle != DesignStyle.TUBIK || settings.appearance.backgroundStyle == BackgroundStyle.IMAGE)) setShadowLayer(2f, 0f, 1f, if (colors.isLight) Color.WHITE else Color.BLACK)
            }
            if (room <= 0) return
            val measured = paint.measureText(value)
            if (measured > room) paint.textSize *= room / measured
            if (settings.widgetGlass != WidgetGlass.SOLID && (settings.appearance?.designStyle != DesignStyle.TUBIK || settings.appearance.backgroundStyle == BackgroundStyle.IMAGE)) {
                // A crisp opposite-luminance halo keeps glyphs legible over unknown launcher wallpapers.
                val halo = Paint(paint).apply {
                    clearShadowLayer()
                    this.color = ThemeContrast.on(color)
                    style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = 0.9f
                }
                c.drawText(value, x, y, halo)
            }
            c.drawText(value, x, y, paint)
        }
        fun label(value: String, x: Float, y: Float, align: Paint.Align = Paint.Align.LEFT, room: Float = w - pad * 2) =
            text(value, x, y, 9f, colors.textDim, type.label, align, room)
        fun bar(left: Float, top: Float, right: Float, height: Float = 6f) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.surfaceHigh }
            if (settings.appearance?.designStyle == DesignStyle.TUBIK) {
                val count = 24; val gap = 1.5f
                val width = ((right - left - gap * (count - 1)) / count).coerceAtLeast(0f)
                repeat(count) { index ->
                    paint.color = if ((index + 0.5f) / count <= (snap.fraction ?: 0f)) colors.accent else Palettes.fade(colors.text, 0.12f)
                    val x = left + index * (width + gap)
                    c.drawRect(x, top, x + width, top + height, paint)
                }
                return
            }
            c.drawRoundRect(left, top, right, top + height, height / 2, height / 2, paint)
            val fraction = snap.fraction ?: 0f
            if (fraction > 0) {
                paint.color = colors.accent
                c.drawRoundRect(left, top, left + (right - left) * fraction.coerceIn(0f, 1f), top + height, height / 2, height / 2, paint)
            }
        }
        fun mark(left: Float, top: Float, size: Float) {
            if (settings.appearance?.designStyle == DesignStyle.TUBIK) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 0.8f; color = Palettes.fade(colors.text, 0.4f) }
                val cx = left + size / 2; val cy = top + size / 2; val radius = size * 0.46f
                repeat(60) { index ->
                    val angle = (index * 6 - 90) * PI.toFloat() / 180
                    val inner = radius - if (index % 5 == 0) 7f else 3f
                    c.drawLine(cx + cos(angle) * inner, cy + sin(angle) * inner,
                        cx + cos(angle) * radius, cy + sin(angle) * radius, paint)
                }
                val inset = size * 0.20f
                val ring = RectF(left + inset, top + inset, left + size - inset, top + size - inset)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = size * 0.045f
                paint.color = Palettes.fade(colors.text, 0.12f); c.drawOval(ring, paint)
                paint.color = colors.accent; c.drawArc(ring, -90f, (snap.fraction ?: 0f).coerceIn(0f, 1f) * 360f, false, paint)
                text(snap.fraction?.let { "${(it * 100).roundToInt()}%" } ?: "—", cx, cy + size * 0.06f,
                    size = size * 0.18f, face = type.numberBold, align = Paint.Align.CENTER, room = size * 0.5f)
                return
            }
            DeltaMark.draw(c, left, top, size, snap.fraction ?: 0f, colors, glyph = snap.fraction == null)
            snap.fraction?.let { DeltaMark.drawLabel(c, left, top, size, "${(it * 100).roundToInt()}", "%", colors.text, type.numberBold,
                shadowColor = if (settings.widgetGlass == WidgetGlass.SOLID) null else if (colors.isLight) Color.WHITE else Color.BLACK) }
        }
        /** Change over the last week of weigh-ins, from the latest back to the last one a week or more before. */
        fun weekChange(): Double? {
            val last = snap.points.lastOrNull() ?: return null
            val before = snap.points.lastOrNull { it.epochMs <= last.epochMs - 7 * 86_400_000L } ?: return null
            return last.kg - before.kg
        }
        /** Green towards the target, amber away from it, dim when there is no real change. */
        fun changeTint(change: Double) = when {
            kotlin.math.abs(change) < 0.05 -> colors.textDim
            (change < 0) == snap.losing -> colors.good
            else -> colors.warn
        }
        fun weekLine() = weekChange()?.let { "${Units.signed(it, unit)} this week" }
        fun goalLine(): String? = when {
            snap.reached -> "Target reached"
            snap.projectedDay == null -> null
            snap.projectedDay.year == LocalDate.now().year -> "Goal ${snap.projectedDay.format(shortDate)}"
            else -> "Goal ${snap.projectedDay.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))}"
        }

        fun remaining(): String {
            val remaining = when {
            snap.reached -> "Target reached"
            snap.remainingKg != null -> "${Units.amount(snap.remainingKg, unit)} to go"
            else -> "Open Delta to set up"
            }
            return remaining + (snap.bodyFat.lastOrNull()?.let { " · ${BodyFat.format(it.percent)} fat" } ?: "")
        }
        fun bodyFat() {
            val readings = snap.bodyFat.sortedBy { it.epochMs }
            label("BODY FAT", pad, pad + 8)
            val latest = readings.lastOrNull()
            text(latest?.let { BodyFat.format(it.percent) } ?: "—", pad, pad + 39, 30f, colors.accent, type.numberBold)
            if (latest == null) {
                text("Log or sync a measurement", pad, h * 0.68f, 12f, colors.textDim, type.body)
                return
            }
            val previous = readings.dropLast(1).lastOrNull()
            if (previous != null && w >= 230) text(String.format(Locale.getDefault(), "%+.1f pp", latest.percent - previous.percent), w - pad, pad + 35,
                12f, colors.textDim, type.number, Paint.Align.RIGHT, room = w * 0.40f)
            if (h >= 135 && readings.size >= 2) {
                TrendChart.draw(c, RectF(pad, pad + 50, w - pad, h - 27),
                    ChartSpec(readings.map { io.github.ebziboi.delta.data.TrendPoint(it.epochMs, it.percent, it.percent) },
                        ChartStyle.CONNECTED, WeightUnit.KG, snap.targetBodyFat.takeIf { show.showTarget }, metric = ChartMetric.BODY_FAT), colors, type, 1f, true)
            }
            val footer = snap.targetBodyFat?.takeIf { show.showTarget }?.let { "Target ${BodyFat.format(it)} · " }.orEmpty() +
                java.time.Instant.ofEpochMilli(latest.epochMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(shortDate)
            text(footer, pad, h - pad, 10f, colors.textDim, type.body)
        }

        fun progress() {
            if (w > h * 1.5f) {
                val size = h - pad * 2
                mark(pad, pad, size)
                val left = pad + size + 12f
                val room = w - left - pad
                label("NOW", left, h / 2 - 20f, room = room)
                text(snap.currentKg?.let { Units.weightShort(it, unit) } ?: "—", left, h / 2 + 6f, 24f, face = type.numberBold, room = room)
                if (show.showDetail) text(remaining(), left, h / 2 + 25f, color = colors.textDim, face = type.body, room = room)
                // With height to spare: the week's change and the projected goal date.
                if (h >= 110) {
                    val week = weekChange()
                    val line = listOfNotNull(weekLine(), goalLine()).joinToString("  ·  ")
                    if (line.isNotEmpty()) text(line, left, h / 2 + 42f, 10.5f,
                        week?.let { changeTint(it) } ?: colors.textDim, type.body, room = room)
                }
            } else {
                val week = weekChange().takeIf { h >= 150 }
                val lines = (if (show.showDetail) 1 else 0) + (if (week != null) 1 else 0)
                val footer = when (lines) { 0 -> pad * 2; 1 -> 52f; else -> 68f }
                val size = min(w - pad * 2, h - footer)
                mark((w - size) / 2, pad + (h - footer - size) / 2, size)
                val bottom = h - pad
                if (week != null) text(weekLine()!!, w / 2, bottom, 10.5f, changeTint(week), type.body, Paint.Align.CENTER)
                if (show.showDetail) text(remaining(), w / 2, if (week != null) bottom - 17f else bottom, 12f,
                    align = Paint.Align.CENTER, face = type.body)
            }
        }
        fun summary() {
            val center = h / 2
            val positions = listOf(pad, w / 2, w - pad)
            val aligns = listOf(Paint.Align.LEFT, Paint.Align.CENTER, Paint.Align.RIGHT)
            val values = listOf(snap.startKg, snap.currentKg, snap.targetKg)
            listOf("START", "NOW", "TARGET").forEachIndexed { i, title ->
                label(title, positions[i], center - 18f, aligns[i], (w - pad * 2) / 3 - 6)
                text(values[i]?.let { Units.weightShort(it, unit) } ?: "—", positions[i], center + 6f,
                    if (i == 1) 21f else 17f, if (i == 1) colors.accent else colors.text,
                    type.numberBold, aligns[i], (w - pad * 2) / 3 - 6)
            }
            bar(pad, center + 18, w - pad)
            if (h >= 115) {
                // A line of detail under the bar: what is left, how far along, and the week's change.
                val third = (w - pad * 2) / 3 - 6
                if (show.showDetail) text(remaining(), pad, center + 43, 11f, colors.textDim, type.body, room = third)
                snap.fraction?.let { text("${(it * 100).roundToInt()}%", w / 2, center + 43, 11f, colors.accent,
                    type.numberBold, Paint.Align.CENTER, third) }
                weekChange()?.let { text(weekLine()!!, w - pad, center + 43, 11f, changeTint(it), type.body, Paint.Align.RIGHT, third) }
            }
        }
        fun trend(journey: Boolean) {
            val compact = h < 110
            // A second header line (the week's change and the goal date) once there is room for it.
            val detailed = !compact && h >= 150
            val header = when { compact -> 27f; detailed -> 54f; else -> 38f }
            text(snap.currentKg?.let { Units.weightShort(it, unit) } ?: "Weight trend", pad, pad + 15, 19f,
                face = type.numberBold, room = (w - pad * 2) * 0.55f)
            snap.targetKg?.takeIf { show.showTarget }?.let { text("Target ${Units.weightShort(it, unit)}", w - pad, pad + 13, 10f,
                colors.accent, align = Paint.Align.RIGHT, room = (w - pad * 2) * 0.42f) }
            if (detailed) {
                weekChange()?.let { text(weekLine()!!, pad, pad + 33, 10.5f, changeTint(it), type.body, room = (w - pad * 2) * 0.55f) }
                goalLine()?.let { text(it, w - pad, pad + 31, 10f, colors.textDim, type.body, Paint.Align.RIGHT, (w - pad * 2) * 0.42f) }
            }
            val footer = when {
                !journey || compact -> 0f
                show.showBar && show.showDetail -> 37f
                show.showBar -> 18f
                show.showDetail -> 20f
                else -> 0f
            }
            TrendChart.draw(c, RectF(pad - 3, header + pad, w - pad, h - pad - footer),
                ChartSpec.journey(snap, settings.widgetChart, unit, settings.showProjection), colors, type, 1f,
                labels = h >= 150 && w >= 240, contrastShadow = settings.widgetGlass != WidgetGlass.SOLID)
            if (journey) {
                if (compact) {
                    if (show.showBar) bar(pad, h - 9, w - pad, 3f)
                } else {
                    if (show.showBar) bar(pad, h - pad - (if (show.showDetail) 24 else 6), w - pad)
                    if (show.showDetail) {
                        val pace = snap.weeklyRateKg?.let { "  ·  ${Units.rate(it, unit)}" }.orEmpty()
                        text(remaining() + pace, pad, h - pad, 11f, colors.textDim, room = (w - pad * 2) * 0.8f)
                        text(snap.fraction?.let { "${(it * 100).roundToInt()}%" } ?: "—", w - pad, h - pad, 11f,
                            colors.accent, type.numberBold, Paint.Align.RIGHT, (w - pad * 2) * 0.18f)
                    }
                }
            }
        }
        fun pace() {
            val goal = when {
                snap.reached -> "Reached"
                snap.targetKg == null -> "Set a target"
                snap.projectedDay == null -> "—"
                snap.projectedDay.year == LocalDate.now().year -> snap.projectedDay.format(shortDate)
                else -> snap.projectedDay.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
            }
            val week = weekChange()
            val cells = listOf(
                Triple("WEEKLY", snap.weeklyRateKg?.let { Units.rate(it, unit) } ?: "—", colors.accent),
                Triple("THIS WEEK", week?.let { Units.signed(it, unit) } ?: "—", week?.let { changeTint(it) } ?: colors.text),
                Triple("GOAL BY", goal, colors.text),
                Triple("STREAK", "${snap.streakDays} days", colors.text),
            )
            val wide = w > h * 1.8f
            val status = snap.pace?.takeIf { show.showStatus }
            if (wide) {
                // A row of figures across the widget: four when it is wide enough, else the first three.
                val shown = if (w >= 300) cells else cells.filter { it.first != "THIS WEEK" }
                val column = (w - pad * 2) / shown.size
                shown.forEachIndexed { i, (title, value, tint) ->
                    val left = pad + i * column
                    label(title, left, h / 2 - 7, room = column - 8)
                    text(value, left, h / 2 + 16, 17f, tint, type.numberBold, room = column - 8)
                }
            } else {
                // Two columns, two rows.
                val footer = if (status != null) 18f else 0f
                val rowHeight = (h - pad * 2 - footer) / 2
                val column = (w - pad * 2) / 2
                cells.forEachIndexed { i, (title, value, tint) ->
                    val left = pad + (i % 2) * column
                    val y = pad + (i / 2) * rowHeight + max(0f, (rowHeight - 40f) / 2)
                    label(title, left, y + 8, room = column - 8)
                    text(value, left, y + 31f, 17f, tint, type.numberBold, room = column - 8)
                }
            }
            if (!wide) status?.let {
                val title = when (it) { Pace.AHEAD -> "Ahead"; Pace.ON_TRACK -> "On track"; Pace.BEHIND -> "Behind" }
                text(title, w - pad, h - 7, 9f, if (it == Pace.BEHIND) colors.warn else colors.good,
                    type.label, Paint.Align.RIGHT)
            }
        }
    }
}
