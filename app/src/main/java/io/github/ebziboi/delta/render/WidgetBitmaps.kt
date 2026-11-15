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
        val palette = Palettes.forWidget(context, settings, night)
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
        val theme = AppearanceEngine.forWidget(context, settings, night)
        GlassWidgetSurface.draw(canvas, widthDp, heightDp, theme, settings, WidgetCorners.radius(context, theme.appearance.cornerRadius))

        // Draw tiny hosts in a larger logical coordinate space so content scales down together.
        val fit = min(1f, min(widthDp / 150f, heightDp / 82f))
        canvas.scale(fit, fit)
        val painter = Painter(context, canvas, widthDp / fit, heightDp / fit, snap, settings, colors, type)
        when (kind) {
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
                if (settings.widgetGlass != WidgetGlass.SOLID) setShadowLayer(2f, 0f, 1f, if (colors.isLight) Color.WHITE else Color.BLACK)
            }
            if (room <= 0) return
            val measured = paint.measureText(value)
            if (measured > room) paint.textSize *= room / measured
            if (settings.widgetGlass != WidgetGlass.SOLID) {
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
            c.drawRoundRect(left, top, right, top + height, height / 2, height / 2, paint)
            val fraction = snap.fraction ?: 0f
            if (fraction > 0) {
                paint.color = colors.accent
                c.drawRoundRect(left, top, left + (right - left) * fraction.coerceIn(0f, 1f), top + height, height / 2, height / 2, paint)
            }
        }
        fun mark(left: Float, top: Float, size: Float) {
            DeltaMark.draw(c, left, top, size, snap.fraction ?: 0f, colors, glyph = snap.fraction == null)
            snap.fraction?.let { DeltaMark.drawLabel(c, left, top, size, "${(it * 100).roundToInt()}", "%", colors.text, type.numberBold,
                shadowColor = if (settings.widgetGlass == WidgetGlass.SOLID) null else if (colors.isLight) Color.WHITE else Color.BLACK) }
        }
        fun remaining() = when {
            snap.reached -> "Target reached"
            snap.remainingKg != null -> "${Units.amount(snap.remainingKg, unit)} to go"
            else -> "Open Delta to set up"
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
            } else {
                val footer = if (show.showDetail) 52f else pad * 2
                val size = min(w - pad * 2, h - footer)
                mark((w - size) / 2, pad + (h - footer - size) / 2, size)
                if (show.showDetail) text(remaining(), w / 2, h - pad, 12f, align = Paint.Align.CENTER, face = type.body)
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
            if (h >= 115 && show.showDetail) text(remaining(), w / 2, center + 43, color = colors.textDim, align = Paint.Align.CENTER)
        }
        fun trend(journey: Boolean) {
            val compact = h < 110
            val header = if (compact) 27f else 38f
            text(snap.currentKg?.let { Units.weightShort(it, unit) } ?: "Weight trend", pad, pad + 15, 19f,
                face = type.numberBold, room = (w - pad * 2) * 0.55f)
            snap.targetKg?.takeIf { show.showTarget }?.let { text("Target ${Units.weightShort(it, unit)}", w - pad, pad + 13, 10f,
                colors.accent, align = Paint.Align.RIGHT, room = (w - pad * 2) * 0.42f) }
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
                        text(remaining(), pad, h - pad, 11f, colors.textDim, room = (w - pad * 2) * 0.8f)
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
            val rows = listOf("WEEKLY" to (snap.weeklyRateKg?.let { Units.rate(it, unit) } ?: "—"),
                "GOAL BY" to goal, "STREAK" to "${snap.streakDays} days")
            val wide = w > h * 1.8f
            val status = snap.pace?.takeIf { show.showStatus }
            val rowHeight = min(52f, (h - pad * 2 - if (status != null && !wide) 18f else 0f) / 3)
            val top = if (wide) h / 2 - 15 else (h - rowHeight * 3) / 2
            rows.forEachIndexed { i, (title, value) ->
                val left = if (wide) pad + i * (w - pad * 2) / 3 else pad
                val y = if (wide) top else top + rowHeight * i
                val room = if (wide) (w - pad * 2) / 3 - 8 else w - pad * 2
                label(title, left, y + 8, room = room)
                text(value, left, y + min(31f, rowHeight - 2), 17f, if (i == 0) colors.accent else colors.text,
                    type.numberBold, room = room)
            }
            if (!wide) status?.let {
                val title = when (it) { Pace.AHEAD -> "Ahead"; Pace.ON_TRACK -> "On track"; Pace.BEHIND -> "Behind" }
                text(title, w - pad, h - 7, 9f, if (it == Pace.BEHIND) colors.warn else colors.good,
                    type.label, Paint.Align.RIGHT)
            }
        }
    }
}
