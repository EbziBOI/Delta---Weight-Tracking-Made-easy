package io.github.ebziboi.delta.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.data.Pace
import io.github.ebziboi.delta.data.ProgressSnapshot
import io.github.ebziboi.delta.data.Units
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The home-screen widgets Delta offers. The order is the order they are listed in settings. */
enum class WidgetKind(val title: String, val detail: String) {
    PROGRESS("Progress", "The delta, filling as you close the gap"),
    JOURNEY("Journey", "Your graph and progress bar together"),
    SUMMARY("Start, now, target", "Three numbers over a progress bar"),
    TREND("Trend", "Your weight graph with the target line"),
    PACE("Pace", "Weekly rate, projected date and streak"),
}

/**
 * Draws each home-screen widget as a single image. Drawing the whole widget
 * ourselves is what lets widgets carry the delta track, the graph and the
 * user's theme exactly as the app shows them, at any size they are resized to.
 */
object WidgetBitmaps {
    private val bold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val longDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    private class Sheet(val bitmap: Bitmap, val canvas: Canvas, val w: Float, val h: Float, val dp: Float)

    /** The longest side, in pixels, a widget is drawn at unless the caller asks for less. */
    const val DEFAULT_MAX_SIDE_PX = 1400f

    /**
     * Draws widget [kind] at [widthDp] by [heightDp]. [night] is the phone's mode
     * to draw for; it only changes the result when the widgets follow the phone.
     * Large widgets are drawn below native resolution so the longest side is at
     * most [maxSidePx], which keeps the image within what the home screen accepts.
     */
    fun render(
        context: Context,
        kind: WidgetKind,
        widthDp: Float,
        heightDp: Float,
        snap: ProgressSnapshot,
        settings: AppSettings,
        night: Boolean = Palettes.isNight(context),
        maxSidePx: Float = DEFAULT_MAX_SIDE_PX,
    ): Bitmap = when (kind) {
        WidgetKind.PROGRESS -> progress(context, widthDp, heightDp, snap, settings, night)
        WidgetKind.SUMMARY -> summary(context, widthDp, heightDp, snap, settings, night)
        WidgetKind.TREND, WidgetKind.JOURNEY -> trend(context, widthDp, heightDp, snap, settings, night)
        WidgetKind.PACE -> pace(context, widthDp, heightDp, snap, settings, night)
    }

    /** The delta track with the percentage inside it and how far there is to go. */
    private fun progress(
        context: Context,
        widthDp: Float,
        heightDp: Float,
        snap: ProgressSnapshot,
        settings: AppSettings,
        night: Boolean,
    ): Bitmap {
        val colors = Palettes.forWidget(context, settings, night)
        val sheet = sheet(context, widthDp, heightDp, colors)
        val canvas = sheet.canvas
        val dp = sheet.dp
        val pad = 12f * dp

        val fraction = snap.fraction
        val remaining = snap.remainingKg
        val current = snap.currentKg
        val ready = fraction != null && remaining != null && current != null
        val toGo = when {
            !ready -> "Open Delta to set up"
            snap.reached -> "Target reached"
            else -> "${Units.amount(remaining ?: 0.0, settings.unit)} to go"
        }

        if (sheet.w > sheet.h * 1.45f) {
            // Wide: the mark on the left, the numbers beside it.
            val size = sheet.h - pad * 2
            drawMark(canvas, pad, pad, size, fraction, ready, colors)

            val x = pad + size + 14f * dp
            var room = sheet.w - x - pad
            if (room > 40f * dp) {
                if (ready) {
                    val label = caption(colors, dp)
                    val labelTop = (sheet.h - (label.textSize + 5f * dp + 22f * dp + 6f * dp + 12f * dp)) / 2f

                    // With space to spare, the target sits on the right as the thing to aim at.
                    val target = snap.targetKg
                    if (target != null && room > 230f * dp) {
                        val right = sheet.w - pad - 4f * dp
                        val targetText = Units.weightShort(target, settings.unit)
                        var y = labelTop + label.textSize * 0.85f
                        canvas.drawText("TARGET", right, y, caption(colors, dp, Paint.Align.RIGHT))
                        y += 5f * dp + 22f * dp * 0.95f
                        canvas.drawText(
                            targetText, right, y,
                            ink(22f * dp, colors.accent, bold, Paint.Align.RIGHT).fit(targetText, room * 0.4f),
                        )
                        room *= 0.55f
                    }

                    val nowText = Units.weightShort(current ?: 0.0, settings.unit)
                    val value = ink(22f * dp, colors.text, bold).fit(nowText, room)
                    val detail = ink(12f * dp, colors.textDim).fit(toGo, room)
                    var y = labelTop + label.textSize * 0.85f
                    canvas.drawText("NOW", x, y, label)
                    y += 5f * dp + 22f * dp * 0.95f
                    canvas.drawText(nowText, x, y, value)
                    y += 6f * dp + 12f * dp
                    canvas.drawText(toGo, x, y, detail)
                } else {
                    val hint = ink(13f * dp, colors.textDim).fit(toGo, room)
                    canvas.drawText(toGo, x, sheet.h / 2f + hint.textSize * 0.35f, hint)
                }
            }
        } else {
            // Square or tall: the mark on top, one line beneath.
            val footer = 20f * dp
            val room = sheet.h - pad * 2 - footer
            val size = min(sheet.w - pad * 2, room)
            val left = (sheet.w - size) / 2f
            val top = pad + (room - size) / 2f
            drawMark(canvas, left, top, size, fraction, ready, colors)

            val line = ink(13f * dp, if (ready) colors.text else colors.textDim, bold, Paint.Align.CENTER)
                .fit(toGo, sheet.w - pad * 2)
            canvas.drawText(toGo, sheet.w / 2f, sheet.h - pad - 2f * dp, line)
        }
        return sheet.bitmap
    }

    /** Start, now and target side by side over a progress bar. */
    private fun summary(
        context: Context,
        widthDp: Float,
        heightDp: Float,
        snap: ProgressSnapshot,
        settings: AppSettings,
        night: Boolean,
    ): Bitmap {
        val colors = Palettes.forWidget(context, settings, night)
        val sheet = sheet(context, widthDp, heightDp, colors)
        val canvas = sheet.canvas
        val unit = settings.unit

        // A taller widget gets a second line; either way the content is scaled to fit
        // the height it has been given, so nothing is cut off at the edges.
        val tall = heightDp >= 96f
        val contentHeight = if (tall) 71f else 49.5f
        val scale = min(min(1.5f, (heightDp - 18f) / contentHeight), widthDp / 240f)
        val k = sheet.dp * max(scale, 0.6f)
        val padX = 16f * k
        val labelSize = 9.5f * k
        val valueSize = 19f * k
        val barHeight = 6f * k
        val detailSize = 11.5f * k
        var block = labelSize + 5f * k + valueSize + 10f * k + barHeight
        if (tall) block += 10f * k + detailSize
        var y = (sheet.h - block) / 2f

        val column = (sheet.w - padX * 2) / 3f
        val startText = snap.startKg?.let { Units.weightShort(it, unit) } ?: "—"
        val nowText = snap.currentKg?.let { Units.weightShort(it, unit) } ?: "—"
        val targetText = snap.targetKg?.let { Units.weightShort(it, unit) } ?: "—"

        y += labelSize * 0.85f
        canvas.drawText("START", padX, y, caption(colors, k))
        canvas.drawText("NOW", sheet.w / 2f, y, caption(colors, k, Paint.Align.CENTER))
        canvas.drawText("TARGET", sheet.w - padX, y, caption(colors, k, Paint.Align.RIGHT))

        y += 5f * k + valueSize * 0.9f
        val side = column - 10f * k
        canvas.drawText(startText, padX, y, ink(16f * k, colors.text, medium).fit(startText, side))
        canvas.drawText(
            nowText, sheet.w / 2f, y,
            ink(valueSize, colors.accent, bold, Paint.Align.CENTER).fit(nowText, column),
        )
        canvas.drawText(
            targetText, sheet.w - padX, y,
            ink(16f * k, colors.text, medium, Paint.Align.RIGHT).fit(targetText, side),
        )

        y += 10f * k
        drawBar(canvas, padX, y, sheet.w - padX, barHeight, snap.fraction ?: 0f, colors)

        if (tall) {
            y += barHeight + 10f * k + detailSize * 0.85f
            val change = snap.changeKg
            val remaining = snap.remainingKg
            if (change != null) {
                val verb = if (change <= 0) "lost" else "gained"
                canvas.drawText("${Units.amount(change, unit)} $verb", padX, y, ink(detailSize, colors.textDim))
            }
            snap.fraction?.let {
                canvas.drawText(
                    "${(it * 100).roundToInt()}%", sheet.w / 2f, y,
                    ink(detailSize, colors.accent, bold, Paint.Align.CENTER),
                )
            }
            if (remaining != null) {
                val text = if (snap.reached) "Target reached" else "${Units.amount(remaining, unit)} to go"
                canvas.drawText(text, sheet.w - padX, y, ink(detailSize, colors.textDim, medium, Paint.Align.RIGHT))
            }
        }
        return sheet.bitmap
    }

    /** The weight graph since the start, with the target line. */
    private fun trend(
        context: Context,
        widthDp: Float,
        heightDp: Float,
        snap: ProgressSnapshot,
        settings: AppSettings,
        night: Boolean,
    ): Bitmap {
        val colors = Palettes.forWidget(context, settings, night)
        val sheet = sheet(context, widthDp, heightDp, colors)
        val canvas = sheet.canvas
        val dp = sheet.dp
        val pad = 14f * dp
        val unit = settings.unit

        val header = 26f * dp
        val baseline = pad + 17f * dp
        val current = snap.currentKg
        var cursor = pad
        if (current != null) {
            val value = ink(18f * dp, colors.text, bold)
            val text = Units.weightShort(current, unit)
            canvas.drawText(text, cursor, baseline, value)
            cursor += value.measureText(text) + 8f * dp
        }
        val target = snap.targetKg
        var rightEdge = sheet.w - pad
        if (target != null) {
            val tag = ink(11f * dp, colors.accent, medium, Paint.Align.RIGHT)
            val text = "Target ${Units.weightShort(target, unit)}"
            if (tag.measureText(text) <= rightEdge - cursor) {
                canvas.drawText(text, rightEdge, baseline, tag)
                rightEdge -= tag.measureText(text) + 8f * dp
            }
        }
        val change = snap.changeKg
        if (change != null && snap.points.size >= 2 && rightEdge - cursor > 60f * dp) {
            val text = "${Units.signed(change, unit)} since ${snap.startDay.format(shortDate)}"
            val note = ink(11f * dp, colors.textDim)
            if (note.measureText(text) <= rightEdge - cursor) canvas.drawText(text, cursor, baseline, note)
        }

        val area = RectF(pad - 2f * dp, pad + header, sheet.w - pad + 4f * dp, sheet.h - pad + 2f * dp)
        TrendChart.draw(
            canvas = canvas,
            area = area,
            spec = ChartSpec.journey(snap, settings.widgetChart, unit, settings.showProjection),
            colors = colors,
            type = DeltaFonts.canvas(context, settings.typeStyle),
            density = dp,
            labels = heightDp >= 130f && widthDp >= 200f,
        )
        return sheet.bitmap
    }

    /** Weekly rate, projected goal date and weigh-in streak. */
    private fun pace(
        context: Context,
        widthDp: Float,
        heightDp: Float,
        snap: ProgressSnapshot,
        settings: AppSettings,
        night: Boolean,
    ): Bitmap {
        val colors = Palettes.forWidget(context, settings, night)
        val sheet = sheet(context, widthDp, heightDp, colors)
        val canvas = sheet.canvas
        val dp = sheet.dp
        val pad = 14f * dp

        val today = LocalDate.now()
        val projected = snap.projectedDay
        val goalBy = when {
            snap.reached -> "Reached"
            snap.targetKg == null -> "Set a target"
            projected == null -> "—"
            projected.year == today.year -> projected.format(shortDate)
            else -> projected.format(longDate)
        }
        val streak = when (snap.streakDays) {
            0 -> "—"
            1 -> "1 day"
            else -> "${snap.streakDays} days"
        }
        // The weekly rate is tinted by whether it is heading for the target or away from it.
        val rate = snap.weeklyRateKg
        val rateTint = when {
            rate == null || snap.targetKg == null || snap.reached -> colors.text
            (snap.losing && rate < -0.02) || (!snap.losing && rate > 0.02) -> colors.good
            (snap.losing && rate > 0.02) || (!snap.losing && rate < -0.02) -> colors.warn
            else -> colors.text
        }
        val rows = listOf(
            Triple("WEEKLY", rate?.let { Units.rate(it, settings.unit) } ?: "—", rateTint),
            Triple("GOAL BY", goalBy, colors.text),
            Triple("STREAK", streak, colors.text),
        )

        val labelSize = 9.5f * dp
        val valueSize = 17f * dp
        val rowBlock = labelSize + 4f * dp + valueSize

        if (sheet.w > sheet.h * 1.7f) {
            // Wide: three columns.
            val column = (sheet.w - pad * 2) / 3f
            val top = (sheet.h - rowBlock) / 2f
            rows.forEachIndexed { index, (label, value, tint) ->
                val x = pad + column * index
                canvas.drawText(label, x, top + labelSize * 0.85f, caption(colors, dp))
                canvas.drawText(
                    value, x, top + labelSize + 4f * dp + valueSize * 0.9f,
                    ink(valueSize, tint, bold).fit(value, column - 8f * dp),
                )
            }
        } else {
            // Square or tall: three rows.
            val gap = ((sheet.h - pad * 2 - rowBlock * 3) / 2f).coerceIn(4f * dp, 22f * dp)
            var top = (sheet.h - (rowBlock * 3 + gap * 2)) / 2f
            for ((label, value, tint) in rows) {
                canvas.drawText(label, pad, top + labelSize * 0.85f, caption(colors, dp))
                canvas.drawText(
                    value, pad, top + labelSize + 4f * dp + valueSize * 0.9f,
                    ink(valueSize, tint, bold).fit(value, sheet.w - pad * 2),
                )
                top += rowBlock + gap
            }
        }

        // With a goal date set, say plainly whether the trend will make it.
        val pace = snap.pace
        if (pace != null && widthDp >= 130f) {
            val (text, tint) = when (pace) {
                Pace.AHEAD -> "Ahead" to colors.good
                Pace.ON_TRACK -> "On track" to colors.good
                Pace.BEHIND -> "Behind" to colors.warn
            }
            val tag = ink(9.5f * dp, tint, bold, Paint.Align.CENTER)
            val width = tag.measureText(text) + 14f * dp
            val height = 18f * dp
            val right = sheet.w - pad
            val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Palettes.withAlpha(tint, 0x33)
            }
            canvas.drawRoundRect(right - width, pad - 3f * dp, right, pad - 3f * dp + height, height / 2f, height / 2f, pill)
            canvas.drawText(text, right - width / 2f, pad - 3f * dp + height / 2f + tag.textSize * 0.36f, tag)
        }
        return sheet.bitmap
    }

    private fun drawMark(
        canvas: Canvas,
        left: Float,
        top: Float,
        size: Float,
        fraction: Float?,
        ready: Boolean,
        colors: DrawColors,
    ) {
        DeltaMark.draw(canvas, left, top, size, fraction ?: 0f, colors, glyph = !ready)
        if (ready) {
            val percent = ((fraction ?: 0f) * 100).roundToInt().toString()
            DeltaMark.drawLabel(canvas, left, top, size, percent, "%", colors.text, Typeface.DEFAULT_BOLD)
        }
    }

    private fun drawBar(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        height: Float,
        fraction: Float,
        colors: DrawColors,
    ) {
        val radius = height / 2f
        val rail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = colors.surfaceHigh
        }
        canvas.drawRoundRect(left, top, right, top + height, radius, radius, rail)

        val filled = (right - left) * fraction.coerceIn(0f, 1f)
        if (filled <= 0f) return
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = colors.accent
        }
        if (colors.glow) fill.setShadowLayer(height, 0f, 0f, colors.accent)
        canvas.drawRoundRect(left, top, left + max(filled, height), top + height, radius, radius, fill)
    }

    private fun sheet(context: Context, widthDp: Float, heightDp: Float, colors: DrawColors): Sheet {
        val real = context.resources.displayMetrics.density
        // Very large widgets are drawn slightly below native resolution to keep the image small.
        val longest = max(widthDp, heightDp) * real
        val dp = if (longest > DEFAULT_MAX_SIDE_PX) real * DEFAULT_MAX_SIDE_PX / longest else real
        val width = max(1, (widthDp * dp).roundToInt())
        val height = max(1, (heightDp * dp).roundToInt())

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = min(cornerRadius(context) * dp / real, min(w, h) / 2f)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, h, colors.bgTop, colors.bg, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(0f, 0f, w, h, radius, radius, fill)

        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp
            color = colors.outline
        }
        val inset = dp / 2f
        canvas.drawRoundRect(inset, inset, w - inset, h - inset, radius - inset, radius - inset, edge)

        return Sheet(bitmap, canvas, w, h, dp)
    }

    private fun cornerRadius(context: Context): Float =
        if (Build.VERSION.SDK_INT >= 31) {
            context.resources.getDimension(android.R.dimen.system_app_widget_background_radius)
        } else {
            24f * context.resources.displayMetrics.density
        }

    private fun caption(colors: DrawColors, dp: Float, align: Paint.Align = Paint.Align.LEFT): Paint =
        ink(9.5f * dp, colors.textDim, medium, align).apply { letterSpacing = 0.08f }

    private fun ink(
        size: Float,
        color: Int,
        face: Typeface = medium,
        align: Paint.Align = Paint.Align.LEFT,
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textSize = size
        this.color = color
        typeface = face
        textAlign = align
        fontFeatureSettings = "tnum"
    }

    /** Shrinks the text a little at a time until it fits [maxWidth]. */
    private fun Paint.fit(text: String, maxWidth: Float): Paint {
        var attempts = 0
        while (measureText(text) > maxWidth && attempts < 14) {
            textSize *= 0.94f
            attempts++
        }
        return this
    }
}
