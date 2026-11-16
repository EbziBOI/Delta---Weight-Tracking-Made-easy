package io.github.ebziboi.delta.render

import android.content.Context
import android.graphics.*
import io.github.ebziboi.delta.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

/**
 * The home-screen widgets. Each is drawn by [WidgetBitmaps] at whatever size the
 * home screen gives it, from a single cell to a whole page. [defaultWidth] and
 * [defaultHeight] (dp) are its size when first added, used for previews.
 */
enum class WidgetKind(val title: String, val detail: String, val defaultWidth: Float, val defaultHeight: Float) {
    CARD("Progress card", "Your weight, this week's change and the way to your target, as on the Progress tab", 345f, 185f),
    COMPLICATION("Complication", "How far along you are, drawn in your percentage design", 170f, 180f),
    TREND("Trend", "Your weight graph against the target, with a progress bar", 345f, 185f),
    PACE("Pace", "Weekly rate, goal date, 7-day average and streak", 170f, 180f),
    BODY_FAT("Body fat", "Your body fat percentage, its change and its trend", 170f, 180f),
}

/** The same renderer supplies home-screen widgets, in-app previews and picker images. */
object WidgetBitmaps {
    const val DEFAULT_MAX_SIDE_PX = 1000f

    fun render(
        context: Context, kind: WidgetKind, widthDp: Float, heightDp: Float,
        snap: ProgressSnapshot, settings: AppSettings,
        night: Boolean = Palettes.isNight(context), maxSidePx: Float = DEFAULT_MAX_SIDE_PX,
        audit: MutableList<WidgetElement>? = null,
        /** The tone behind the widget; by default the phone's own wallpaper. */
        backdrop: Int? = null,
    ): Bitmap {
        require(widthDp.isFinite() && widthDp > 0 && heightDp.isFinite() && heightDp > 0)
        require(maxSidePx.isFinite() && maxSidePx >= 1)
        val scale = min(context.resources.displayMetrics.density, maxSidePx / max(widthDp, heightDp))
        val bitmap = Bitmap.createBitmap(max(1, (widthDp * scale).roundToInt()), max(1, (heightDp * scale).roundToInt()), Bitmap.Config.ARGB_8888)
        val theme = AppearanceEngine.forWidget(context, settings, night, kind)
        val palette = theme.drawColors()
        val readable = if (settings.widgetGlass == WidgetGlass.CLEAR) palette.copy(textDim = palette.text, textFaint = palette.text) else palette
        // A widget may ask for light or dark text whatever its background; automatic keeps the contrast-derived colours.
        val chosen = when (settings.widgetContent.text) {
            TextContrast.AUTO -> readable
            TextContrast.LIGHT -> readable.copy(text = Color.WHITE, textDim = 0xFFDCDDE5.toInt(), textFaint = 0xFFB6B8C4.toInt())
            TextContrast.DARK -> readable.copy(text = 0xFF0E0F14.toInt(), textDim = 0xFF383A46.toInt(), textFaint = 0xFF5A5D6A.toInt())
        }
        // Where the wallpaper shows through the glass, colours are checked against what is actually seen.
        val a = theme.appearance
        val opaque = settings.widgetGlass == WidgetGlass.SOLID || a.reduceTransparency || a.backgroundStyle == BackgroundStyle.IMAGE ||
            a.designStyle == DesignStyle.TUBIK
        val opacity = (if (theme.isLight) settings.widgetOpacityLight else settings.widgetOpacityDark).takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: .65f
        val colors = if (opaque || opacity >= .97f) chosen
            else WidgetLegibility.over(chosen, theme.background, opacity, backdrop ?: WallpaperTone.backdrop(context))
        val type = DeltaFonts.canvas(context, settings.typeStyle)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        val host = WidgetCorners.radius(context, theme.appearance.cornerRadius)
        GlassWidgetSurface.draw(canvas, widthDp, heightDp, theme, settings, host)
        val corner = min(max(theme.appearance.cornerRadius, host), min(widthDp, heightDp) / 2)
        val painter = Painter(canvas, widthDp, heightDp, snap, settings, colors, type, audit, corner, theme.appearance.accentIntensity)
        when (kind) {
            WidgetKind.CARD -> painter.card()
            WidgetKind.COMPLICATION -> painter.complication()
            WidgetKind.TREND -> painter.trend()
            WidgetKind.PACE -> painter.pace()
            WidgetKind.BODY_FAT -> painter.bodyFat()
        }
        return bitmap
    }

    /** A run of text: a figure, or a unit drawn smaller and dimmer beside it. */
    private class Part(val text: String, val unit: Boolean = false)
    private fun fig(text: String) = Part(text)
    private fun tag(text: String) = Part(text, unit = true)

    /** One row or column handed out by [Painter.stack]. When space runs short, slots with the lowest [keep] go first. */
    private class Slot(val id: String, val size: Float, val grow: Float = 0f, val keep: Int = 0)

    /** A labelled value, as on the app's tiles. */
    private class Figure(
        val label: List<String>, val value: List<List<Part>>, val color: Int,
        val note: List<String>? = null, val noteColor: Int? = null,
    )

    /**
     * Lays out and draws one widget. Sizes are in dp. Layouts are chosen by size
     * class (one cell, a row, a block, a tall block) and every row is optional,
     * so the most useful things stay when a widget is made small and more
     * appears as it grows. Text shrinks only to a readable floor, then the next
     * shorter wording is used.
     */
    private class Painter(
        val c: Canvas, val w: Float, val h: Float, val snap: ProgressSnapshot,
        val settings: AppSettings, val colors: DrawColors, val type: CanvasType,
        val audit: MutableList<WidgetElement>?, val corner: Float, val glow: Float,
    ) {
        val unit = settings.unit
        val show = settings.widgetContent
        val data = WidgetData.from(snap)
        val zone: ZoneId = ZoneId.systemDefault()
        val matte = settings.appearance?.designStyle == DesignStyle.TUBIK
        val shadows = settings.widgetGlass != WidgetGlass.SOLID &&
            (!matte || settings.appearance?.backgroundStyle == BackgroundStyle.IMAGE)
        var pad = (min(w, h) * .09f).coerceIn(10f, 16f)
        val inner get() = RectF(pad, pad, w - pad, h - pad)
        val percent get() = snap.fraction?.let { (it.coerceIn(0f, 1f) * 100).roundToInt() }

        // ---- Text ---------------------------------------------------------------------------

        private val metrics = HashMap<Typeface, FloatArray>()

        /** Cap height and descender depth of [face], per unit of text size. */
        fun metrics(face: Typeface): FloatArray = metrics.getOrPut(face) {
            val probe = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = 100f }
            val bounds = Rect()
            probe.getTextBounds("H", 0, 1, bounds)
            val cap = -bounds.top / 100f
            probe.getTextBounds("gjpy", 0, 4, bounds)
            floatArrayOf(cap, bounds.bottom / 100f)
        }

        private val widths = HashMap<Triple<String, Typeface, Float>, Float>()

        /**
         * The width of [text] per unit of text size. Width grows in step with size, so each run
         * of text is shaped once however many sizes are tried while fitting it.
         */
        fun unitWidth(text: String, face: Typeface, tracking: Float): Float = widths.getOrPut(Triple(text, face, tracking)) {
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                textSize = 100f; typeface = face; letterSpacing = tracking; fontFeatureSettings = "tnum"
            }.measureText(text) / 100f
        }

        fun ink(size: Float, color: Int, face: Typeface, tracking: Float = 0f) =
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                textSize = size; this.color = color; typeface = face; letterSpacing = tracking
                fontFeatureSettings = "tnum"
                if (shadows) setShadowLayer(1.5f, 0f, .7f, ThemeContrast.on(color))
            }

        /**
         * Writes the first of [options] that fits [area] at [floor] or larger, on one
         * line, as large as [size] and the area allow. Units are drawn at
         * [unitScale] of the figure (never below a readable 9) in the dimmer tone.
         * If even the last option is too wide at the floor, its end is cut with an
         * ellipsis. Every option in a row shares one baseline, whatever its letters.
         */
        fun write(
            role: String, options: List<List<Part>>, area: RectF, size: Float, color: Int = colors.text,
            face: Typeface = type.numberBold, align: Paint.Align = Paint.Align.LEFT, floor: Float = 9f,
            unitScale: Float = .5f, unitColor: Int = colors.textDim, unitFace: Typeface = type.body, tracking: Float = 0f,
            comfort: Float = 0f,
        ): RectF? {
            if (area.width() < 4f || area.height() < 4f || options.isEmpty()) return null
            val (cap, descent) = metrics(face).let { it[0] to it[1] }
            val tallest = min(size, area.height() / (cap + descent))
            val lowest = min(floor, tallest)
            fun unitSize(s: Float) = max(s * unitScale, min(9f, s))
            fun widthAt(parts: List<Part>, s: Float): Float = parts.sumOf {
                (if (it.unit) unitWidth(it.text, unitFace, tracking) * unitSize(s) else unitWidth(it.text, face, tracking) * s).toDouble()
            }.toFloat()
            var chosen = options.last()
            var textSize = lowest
            // Words read better in a shorter form than squeezed; figures keep their units and shrink instead.
            val preferred = max(lowest, tallest * comfort)
            if (comfort > 0f) options.firstOrNull { widthAt(it, preferred) <= area.width() }?.let { option ->
                chosen = option
                var low = preferred; var high = tallest
                repeat(14) {
                    val middle = (low + high) / 2
                    if (widthAt(option, middle) <= area.width()) low = middle else high = middle
                }
                textSize = low
            }
            if (textSize == lowest || comfort == 0f) for (option in options) {
                if (widthAt(option, tallest) <= area.width()) { chosen = option; textSize = tallest; break }
                if (widthAt(option, lowest) <= area.width()) {
                    var low = lowest; var high = tallest
                    repeat(14) {
                        val middle = (low + high) / 2
                        if (widthAt(option, middle) <= area.width()) low = middle else high = middle
                    }
                    chosen = option; textSize = low; break
                }
            }
            val figure = ink(textSize, color, face, tracking)
            val small = ink(unitSize(textSize), unitColor, unitFace, tracking)
            var parts = chosen
            if (widthAt(parts, textSize) > area.width() + .5f) {
                var text = parts.joinToString("") { it.text }
                while (text.isNotEmpty() && figure.measureText("$text…") > area.width()) text = text.dropLast(1)
                parts = listOf(Part(text.trimEnd() + "…"))
            }
            val width = parts.sumOf { (if (it.unit) small else figure).measureText(it.text).toDouble() }.toFloat()
            val baseline = area.top + (area.height() - (cap + descent) * textSize) / 2f + cap * textSize
            var x = when (align) {
                Paint.Align.CENTER -> area.centerX() - width / 2f
                Paint.Align.RIGHT -> area.right - width
                else -> area.left
            }
            val drawn = RectF()
            val glyph = Rect()
            var first = true
            for (part in parts) {
                val paint = if (part.unit) small else figure
                c.drawText(part.text, x, baseline, paint)
                if (part.text.isNotBlank()) {
                    paint.getTextBounds(part.text, 0, part.text.length, glyph)
                    val box = RectF(x + glyph.left, baseline + glyph.top, x + glyph.right, baseline + glyph.bottom)
                    if (first) drawn.set(box) else drawn.union(box)
                    first = false
                }
                x += paint.measureText(part.text)
            }
            audit?.add(WidgetElement(role, drawn, parts.joinToString("") { it.text }, textSize))
            return drawn
        }

        /** The largest size, up to [size], at which [parts] fit [width] as a figure with small units. */
        fun fitSize(parts: List<Part>, width: Float, size: Float, face: Typeface = type.numberBold, unitScale: Float = .5f): Float {
            fun widthAt(s: Float): Float = parts.sumOf {
                (if (it.unit) unitWidth(it.text, type.body, 0f) * max(s * unitScale, min(9f, s)) else unitWidth(it.text, face, 0f) * s).toDouble()
            }.toFloat()
            if (widthAt(size) <= width) return size
            var low = 1f; var high = size
            repeat(14) { val middle = (low + high) / 2; if (widthAt(middle) <= width) low = middle else high = middle }
            return low
        }

        fun text(role: String, options: List<String>, area: RectF, size: Float, color: Int = colors.text,
                 face: Typeface = type.body, align: Paint.Align = Paint.Align.LEFT, floor: Float = 9f) =
            write(role, options.map { listOf(Part(it)) }, area, size, color, face, align, floor, comfort = .9f)

        /** Small capitals above a value. */
        fun label(role: String, options: List<String>, area: RectF, align: Paint.Align = Paint.Align.LEFT, color: Int = colors.textDim) =
            write(role, options.map { listOf(Part(it.uppercase(Locale.ENGLISH))) }, area, 9.5f, color, type.label, align, 9f,
                tracking = type.labelTracking, comfort = .99f)

        /** A line of supporting text. */
        fun note(role: String, options: List<String>, area: RectF, color: Int = colors.textDim, align: Paint.Align = Paint.Align.LEFT) =
            text(role, options, area, 11.5f, color, type.body, align)

        // ---- Layout -------------------------------------------------------------------------

        /**
         * Shares [area] out between [slots] along one axis, [gap] apart. Slots that
         * grow share what is left; when none grow and [center] is set the group is
         * centred. When the slots do not fit, those with the lowest keep go first.
         */
        fun stack(area: RectF, slots: List<Slot?>, gap: Float, vertical: Boolean = true, center: Boolean = true): Map<String, RectF> {
            val length = if (vertical) area.height() else area.width()
            val shown = slots.filterNotNull().toMutableList()
            fun needed() = shown.sumOf { it.size.toDouble() }.toFloat() + gap * (shown.size - 1).coerceAtLeast(0)
            while (shown.size > 1 && needed() > length) shown.remove(shown.minBy { it.keep })
            val squeeze = if (needed() > length && needed() > 0f) length / needed() else 1f
            val spare = (length - needed()).coerceAtLeast(0f)
            val growth = shown.sumOf { it.grow.toDouble() }.toFloat()
            var at = (if (vertical) area.top else area.left) + if (growth == 0f && center) spare / 2f else 0f
            val out = LinkedHashMap<String, RectF>()
            for (slot in shown) {
                val extent = slot.size * squeeze + if (growth > 0f) spare * slot.grow / growth else 0f
                out[slot.id] = if (vertical) RectF(area.left, at, area.right, at + extent) else RectF(at, area.top, at + extent, area.bottom)
                at += extent + gap * squeeze
            }
            return out
        }

        /** Splits [area] into a left part and a right part [rightShare] wide, [gap] apart. */
        fun split(area: RectF, rightWidth: Float, gap: Float): Pair<RectF, RectF> {
            val right = rightWidth.coerceIn(0f, area.width())
            return RectF(area.left, area.top, area.right - right - gap, area.bottom) to RectF(area.right - right, area.top, area.right, area.bottom)
        }

        /** A title on the left and a short note on the right of one row, never touching. */
        fun header(area: RectF, title: List<String>, right: List<String>?, rightColor: Int = colors.textDim) {
            if (right == null) { label("title", title, area); return }
            val probe = ink(9.5f, rightColor, type.label, type.labelTracking)
            val wanted = probe.measureText(right.first().uppercase(Locale.ENGLISH)) + 2f
            val (left, side) = split(area, min(wanted, area.width() * .46f), 8f)
            label("title", title, left)
            label("date", right, side, Paint.Align.RIGHT, rightColor)
        }

        // ---- Values -------------------------------------------------------------------------

        fun one(value: Double) = String.format(Locale.US, "%.1f", value)

        /** A weight, large figures with small units: "88.0 kg", "13 st 12.0 lb". */
        fun weight(kg: Double): List<List<Part>> = when (unit) {
            WeightUnit.KG -> listOf(listOf(fig(one(kg)), tag(" kg")), listOf(fig(one(kg))))
            WeightUnit.LB -> one(Units.kgToLb(kg)).let { listOf(listOf(fig(it), tag(" lb")), listOf(fig(it))) }
            WeightUnit.ST_LB -> {
                val (stone, pounds) = Units.stoneParts(kg)
                val whole = Units.kgToLb(abs(kg)).roundToInt()
                listOf(
                    listOf(fig("$stone"), tag(" st "), fig(one(pounds)), tag(" lb")),
                    listOf(fig("$stone"), tag(" st "), fig(one(pounds))),
                    listOf(fig("${whole / 14}st ${whole % 14}")),
                )
            }
        }

        /** A distance, such as what is left to go. Stone users read less than a stone in pounds. */
        fun amount(kg: Double): List<List<Part>> =
            if (unit == WeightUnit.ST_LB && Units.stoneParts(kg).first == 0) {
                one(Units.stoneParts(kg).second).let { listOf(listOf(fig(it), tag(" lb")), listOf(fig(it))) }
            } else weight(abs(kg))

        /** A signed change, "−0.5 kg", with [suffix] such as "/wk" after the unit when there is room. */
        fun change(kg: Double, suffix: String = ""): List<List<Part>> {
            val display = if (unit == WeightUnit.KG) kg else Units.kgToLb(kg)
            val word = if (unit == WeightUnit.KG) "kg" else "lb"
            val sign = when {
                (abs(display) * 10).roundToLong() == 0L -> ""
                display < 0 -> "−"
                else -> "+"
            }
            val number = sign + one(abs(display))
            return if (suffix.isNotEmpty()) listOf(listOf(fig(number), tag(" $word$suffix")), listOf(fig(number), tag(suffix)), listOf(fig(number)))
            else listOf(listOf(fig(number), tag(" $word")), listOf(fig(number)))
        }

        fun tint(change: Double) = when {
            abs(change) < .05 -> colors.textDim
            InsightsCalculator.towardGoal(change, snap.losing) -> colors.good
            else -> colors.warn
        }

        fun dash() = listOf(listOf(fig("—")))

        fun goal(): List<List<Part>> {
            val day = snap.projectedDay
            return when {
                snap.reached -> listOf(listOf(fig("Reached")))
                snap.targetKg == null -> listOf(listOf(fig("No target")), listOf(fig("—")))
                day == null -> listOf(listOf(fig("Not yet")), listOf(fig("—")))
                else -> {
                    val date = day.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
                    if (day.year == LocalDate.now(zone).year) listOf(listOf(fig(date)))
                    else listOf(listOf(fig(date), tag(" ${day.year}")), listOf(fig(date), tag(" ’${day.year % 100}")), listOf(fig(date)))
                }
            }
        }

        fun streak(): List<List<Part>> = when (val days = snap.streakDays) {
            0 -> dash()
            else -> listOf(listOf(fig("$days"), tag(if (days == 1) " day" else " days")), listOf(fig("$days")))
        }

        fun shortDate(time: Long?): String? = time?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
        }

        /** "Today, 07:10", "Yesterday, 07:10" or "Sat 14 Nov". */
        fun moment(time: Long?): List<String>? {
            time ?: return null
            val at = Instant.ofEpochMilli(time).atZone(zone)
            val today = LocalDate.now(zone)
            val clock = at.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH))
            return when (at.toLocalDate()) {
                today -> listOf("Today, $clock", "Today")
                today.minusDays(1) -> listOf("Yesterday, $clock", "Yesterday")
                else -> listOf(at.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)), shortDate(time)!!)
            }
        }

        /** "8.8 kg lost · 8.0 kg to go · 52%", shortened as space requires. */
        fun summary(withPercent: Boolean): List<String> {
            val moved = snap.changeKg?.let { "${Units.amount(it, unit)} ${if (it <= 0) "lost" else "gained"}" }
            val left = if (snap.reached) "Target reached" else snap.remainingKg?.let { "${Units.amount(it, unit)} to go" }
            val share = percent?.takeIf { withPercent }?.let { "$it%" }
            return listOf(listOfNotNull(moved, left, share), listOfNotNull(left, share), listOfNotNull(left))
                .map { it.joinToString("  ·  ") }.filter { it.isNotBlank() }.distinct()
        }

        /** "−0.5 kg this week", tinted towards or away from the target. */
        fun weekLine(role: String, kg: Double, area: RectF, align: Paint.Align = Paint.Align.LEFT, size: Float = 11.5f) {
            val withUnit = change(kg).first().map { Part(it.text) }
            val bare = change(kg).last()
            write(role, listOf(withUnit + tag(" this week"), withUnit + tag(" this wk"), withUnit, bare),
                area, size, tint(kg), type.number, align, unitColor = colors.textDim, unitScale = 1f, comfort = .9f)
        }

        // ---- Shapes -------------------------------------------------------------------------

        /** The progress bar, in the bar style chosen in the app. */
        fun rail(area: RectF) {
            if (area.width() < 8f || area.height() < 2f) return
            audit?.add(WidgetElement("bar", RectF(area)))
            val f = (snap.fraction ?: 0f).coerceIn(0f, 1f)
            val track = Palettes.fade(colors.text, if (colors.isLight) .10f else .14f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            when (settings.progressBarStyle) {
                ProgressBarStyle.DOTS -> {
                    val count = (area.width() / 9f).toInt().coerceIn(10, 40)
                    val radius = min(area.height() / 2f, 2.6f)
                    repeat(count) { i ->
                        paint.color = if ((i + .5f) / count <= f) colors.accent else track
                        c.drawCircle(area.left + (i + .5f) * area.width() / count, area.centerY(), radius, paint)
                    }
                }
                ProgressBarStyle.SEGMENTS -> {
                    val count = (area.width() / 9f).toInt().coerceIn(10, 40)
                    val gap = 2f
                    val cell = (area.width() - gap * (count - 1)) / count
                    repeat(count) { i ->
                        paint.color = if ((i + .5f) / count <= f) colors.accent else track
                        val x = area.left + i * (cell + gap)
                        c.drawRect(x, area.top, x + cell, area.bottom, paint)
                    }
                }
                ProgressBarStyle.SMOOTH -> {
                    val r = area.height() / 2f
                    paint.color = track
                    c.drawRoundRect(area, r, r, paint)
                    if (snap.fraction == null) return
                    val end = max(area.height(), area.width() * f)
                    val filled = RectF(area.left, area.top, area.left + end, area.bottom)
                    // A shader is drawn at the paint's own alpha, so the faint track colour must not linger.
                    paint.color = Color.WHITE
                    paint.shader = LinearGradient(area.left, 0f, filled.right, 0f,
                        Palettes.fade(colors.accent2, .85f), colors.accent, Shader.TileMode.CLAMP)
                    c.drawRoundRect(filled, r, r, paint)
                    paint.shader = null
                    val bead = PointF(filled.right - r, area.centerY())
                    if (!matte) {
                        paint.shader = RadialGradient(bead.x, bead.y, area.height() * 1.6f,
                            Palettes.withAlpha(colors.accent, 140), Palettes.withAlpha(colors.accent, 0), Shader.TileMode.CLAMP)
                        c.drawCircle(bead.x, bead.y, area.height() * 1.6f, paint)
                        paint.shader = null
                    }
                    paint.color = Color.WHITE
                    c.drawCircle(bead.x, bead.y, area.height() * .42f, paint)
                }
            }
        }

        /** The percentage art from the Progress tab, as a square centred in [area]. */
        fun art(area: RectF) {
            val side = min(area.width(), area.height())
            if (side < 28f) return
            val box = RectF(area.centerX() - side / 2f, area.centerY() - side / 2f, area.centerX() + side / 2f, area.centerY() + side / 2f)
            audit?.add(WidgetElement("complication", RectF(box)))
            val design = settings.percentageDesign
            val labelled = artLabelled(side)
            when {
                design != PercentageDesign.ADAPTIVE -> PercentageArt.draw(c, box, snap.fraction, design, colors, type, matte, labelled)
                matte -> dial(box, labelled)
                else -> {
                    if (glow > 0f) {
                        val cx = box.centerX(); val cy = box.top + side * .6f
                        val strength = (if (colors.isLight) .20f else .32f) * glow
                        val wash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            shader = RadialGradient(cx, cy, side * .62f, intArrayOf(
                                Palettes.withAlpha(colors.accent, (strength * 255).roundToInt()),
                                Palettes.withAlpha(colors.accent2, (strength * .3f * 255).roundToInt()),
                                Palettes.withAlpha(colors.accent2, 0)), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
                        }
                        c.drawCircle(cx, cy, side * .62f, wash)
                    }
                    DeltaMark.draw(c, box.left, box.top, side, snap.fraction ?: 0f, colors, glyph = snap.fraction == null)
                    percent?.takeIf { labelled }?.let {
                        DeltaMark.drawLabel(c, box.left, box.top, side, "$it", "%", colors.text, type.display, type.displayScale,
                            shadowColor = if (shadows) ThemeContrast.on(colors.text) else null)
                    }
                }
            }
        }

        /** The dashboard style's instrument dial. */
        fun dial(box: RectF, labelled: Boolean = true) {
            val size = box.width()
            val cx = box.centerX(); val cy = box.centerY()
            val radius = size * .43f
            val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = max(1f, size * .007f) }
            repeat(60) { i ->
                val angle = Math.toRadians(i * 6.0 - 90)
                val major = i % 5 == 0
                tick.color = Palettes.fade(colors.text, if (major) .7f else .2f)
                val from = radius - size * if (major) .055f else .028f
                c.drawLine(cx + cos(angle).toFloat() * from, cy + sin(angle).toFloat() * from,
                    cx + cos(angle).toFloat() * radius, cy + sin(angle).toFloat() * radius, tick)
            }
            val inset = size * .22f
            val oval = RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset)
            val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = size * .042f }
            arc.color = Palettes.fade(colors.text, .10f)
            c.drawArc(oval, -90f, 360f, false, arc)
            arc.color = colors.accent
            c.drawArc(oval, -90f, (snap.fraction ?: 0f).coerceIn(0f, 1f) * 360f, false, arc)
            if (!labelled) return
            val label = percent?.let { "$it%" } ?: "—"
            val paint = ink(size * .16f, colors.text, type.number).apply { textAlign = Paint.Align.CENTER }
            val room = oval.width() * .7f
            if (paint.measureText(label) > room) paint.textSize *= room / paint.measureText(label)
            c.drawText(label, cx, cy - (paint.ascent() + paint.descent()) / 2f, paint)
        }

        /** Progress drawn around the edge of the widget, as the app's perimeter card. */
        fun perimeter() {
            val inset = 4f
            val radius = (corner - inset).coerceAtLeast(0f)
            val path = Path().apply { addRoundRect(RectF(inset, inset, w - inset, h - inset), radius, radius, Path.Direction.CW) }
            val measure = PathMeasure(path, true)
            val active = Path()
            measure.getSegment(0f, measure.length * (snap.fraction ?: 0f).coerceIn(0f, 1f), active, true)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 3.5f; strokeCap = Paint.Cap.ROUND; color = colors.text; alpha = 28
            }
            c.drawPath(path, paint)
            paint.color = colors.accent
            c.drawPath(active, paint)
        }

        fun chart(area: RectF, spec: ChartSpec, axes: Boolean) {
            if (area.height() < 22f || area.width() < 35f) return
            if (spec.points.size < 2) {
                text("chart-empty", listOf(if (spec.points.isEmpty()) "No readings in this range" else "One reading so far",
                    if (spec.points.isEmpty()) "No readings" else "One reading"), area, 11.5f, colors.textDim, type.body, Paint.Align.CENTER)
                return
            }
            audit?.add(WidgetElement("chart", RectF(area)))
            TrendChart.draw(c, area, spec, colors, type, 1f, axes, contrastShadow = shadows)
        }

        /** A translucent tile behind one figure, like the app's figure tiles. */
        fun tile(area: RectF) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (colors.isLight) Palettes.withAlpha(Color.BLACK, 13) else Palettes.withAlpha(Color.WHITE, 17)
            }
            val radius = min(corner * .6f, 16f)
            c.drawRoundRect(area, radius, radius, paint)
        }

        fun divider(x: Float, top: Float, bottom: Float) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palettes.fade(colors.text, .14f); strokeWidth = 1f }
            c.drawLine(x, top, x, bottom, paint)
        }

        /** A label over a value, or beside it when the cell is a wide, low strip. */
        fun figure(index: Int, item: Figure, area: RectF, align: Paint.Align = Paint.Align.LEFT, valueSize: Float = 30f, inline: Boolean = false,
                   valueComfort: Float = 0f) {
            if (inline) {
                val (left, right) = split(area, area.width() * .55f, 6f)
                label("label-$index", item.label, left)
                write("value-$index", item.value, right, min(valueSize, 20f), item.color, align = Paint.Align.RIGHT)
                return
            }
            val noted = item.note != null && area.height() >= 62f
            val valueHeight = min(valueSize * 1.05f, area.height() - (if (noted) 32f else 15f)).coerceAtLeast(12f)
            val rows = stack(area, listOf(
                Slot("label", 11f, keep = 3),
                Slot("value", valueHeight, keep = 9),
                if (noted) Slot("note", 13f, keep = 1) else null,
            ), 3f)
            rows["label"]?.let { label("label-$index", item.label, it, align) }
            rows["value"]?.let { write("value-$index", item.value, it, valueSize, item.color, align = align, comfort = valueComfort) }
            rows["note"]?.let { note("note-$index", item.note!!, it, item.noteColor ?: colors.textDim, align) }
        }

        /** Figures side by side, the first against the left and the last against the right. */
        fun figureRow(area: RectF, items: List<Figure>, valueSize: Float, spread: Boolean, dividers: Boolean = false, first: Int = 0) {
            val gap = 12f
            val cell = (area.width() - gap * (items.size - 1)) / items.size
            items.forEachIndexed { i, item ->
                val left = area.left + i * (cell + gap)
                val align = when {
                    !spread -> Paint.Align.LEFT
                    i == 0 -> Paint.Align.LEFT
                    i == items.lastIndex -> Paint.Align.RIGHT
                    else -> Paint.Align.CENTER
                }
                figure(first + i, item, RectF(left, area.top, left + cell, area.bottom), align, valueSize)
                if (dividers && i > 0) divider(left - gap / 2f, area.top + 2f, area.bottom - 2f)
            }
        }

        fun empty(title: List<String>, message: List<String>, hint: List<String>? = null) {
            val rows = stack(inner, listOf(
                Slot("title", 11f, keep = 1),
                Slot("message", 16f, keep = 9),
                if (hint != null && h >= 130f && w >= 120f) Slot("hint", 13f, keep = 0) else null,
            ), 4f, center = w < 120f || h < 130f)
            rows["title"]?.let { label("title", title, it) }
            rows["message"]?.let { text("empty", message, it, 13f, colors.text, type.bodyBold) }
            rows["hint"]?.let { note("hint", hint!!, it) }
        }

        /**
         * Figures in as many tiles as the area holds, from four in a row to one,
         * choosing the arrangement whose tiles are closest to a comfortable shape.
         */
        fun figureGrid(area: RectF, items: List<Figure>, first: Int = 0) {
            val gap = 8f
            fun cell(cols: Int, rows: Int) = ((area.width() - gap * (cols - 1)) / cols) to ((area.height() - gap * (rows - 1)) / rows)
            fun fits(cols: Int, rows: Int): Boolean {
                val (cw, ch) = cell(cols, rows)
                return (cw >= 60f && ch >= 36f) || (cw >= 120f && ch >= 22f) || (cols == 1 && cw >= 44f && ch >= 30f)
            }
            val layouts = listOf(4 to 1, 2 to 2, 1 to 4, 1 to 3, 2 to 1, 1 to 2, 1 to 1).filter { (cols, rows) -> cols * rows <= items.size && fits(cols, rows) }
            val (cols, rows) = layouts.maxWithOrNull(compareBy<Pair<Int, Int>>({ it.first * it.second }, { layout ->
                val (cw, ch) = cell(layout.first, layout.second)
                -abs(ln((cw / ch) / 1.5f))
            })) ?: (1 to 1)
            val (cw, ch) = cell(cols, rows)
            val tiled = cw >= 70f && ch >= 54f
            val inset = if (tiled) (min(cw, ch) * .12f).coerceIn(7f, 14f) else 0f
            val inline = !tiled && ch < 34f && cw >= 110f
            val shown = items.take(cols * rows)
            // One value size for every tile, so neighbours line up: a value too long for it takes its
            // shorter wording ("1 Mar" for "1 Mar 2027") rather than shrinking on its own.
            val base = if (tiled) min(34f, ch * .36f).coerceAtLeast(16f) else min(30f, ch * .5f).coerceAtLeast(14f)
            val room = cw - inset * 2
            // Sized to each value's shortest proper wording (the bare number is the last resort, not the yardstick).
            val common = if (inline) base else shown.minOf { item ->
                item.value.dropLast(1).ifEmpty { item.value }.maxOf { fitSize(it, room, base) }
            }.coerceAtLeast(base * .72f)
            shown.forEachIndexed { i, item ->
                val x = area.left + (i % cols) * (cw + gap)
                val y = area.top + (i / cols) * (ch + gap)
                val box = RectF(x, y, x + cw, y + ch)
                if (tiled) {
                    tile(box)
                    figure(first + i, item, RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset),
                        valueSize = common, valueComfort = 1f)
                } else {
                    if (i % cols > 0) divider(x - gap / 2f, y + 2f, y + ch - 2f)
                    figure(first + i, item, box, valueSize = common, inline = inline, valueComfort = if (inline) 0f else 1f)
                }
            }
        }

        /** The percentage written large, for when there is no art or the art would be too small to read. */
        fun percentBlock(area: RectF) {
            val rows = stack(area, listOf(Slot("value", min(area.height() - 15f, 40f).coerceAtLeast(14f), keep = 9),
                Slot("caption", 11f, keep = 1)), 3f)
            rows["value"]?.let { write("percent", listOf(listOf(fig("${percent ?: 0}"), tag("%"))), it, 40f, colors.accent,
                align = Paint.Align.CENTER, unitColor = colors.accent, unitFace = type.numberBold, unitScale = .55f) }
            rows["caption"]?.let { label("caption", listOf("OF THE WAY", "THERE"), it, Paint.Align.CENTER) }
        }

        /** Whether the art at [side] can carry its own percentage at a readable size. */
        fun artLabelled(side: Float): Boolean = when {
            settings.percentageDesign != PercentageDesign.ADAPTIVE -> side >= 48f
            matte -> side >= 58f
            else -> side >= 50f
        }

        /** The art where it can carry its own percentage, and the percentage written large where it cannot. */
        fun artOrPercent(area: RectF) {
            if (artLabelled(min(area.width(), area.height()))) art(area)
            else percentBlock(RectF(area.left, area.centerY() - min(area.height(), 56f) / 2f, area.right, area.centerY() + min(area.height(), 56f) / 2f))
        }

        // ---- Progress card ------------------------------------------------------------------

        /** The Progress tab's first card: now, the week's change, the delta, start and target. */
        fun card() {
            val now = snap.currentKg ?: return empty(listOf("NOW"), listOf("No weigh-ins yet", "No weigh-ins", "No data"),
                listOf("Weigh in, or import from your scale", "Weigh in or import", "Weigh in"))
            val hasGoal = snap.fraction != null
            val display = settings.progressDisplay
            val withArt = hasGoal && (display == ProgressDisplay.COMPLICATION || display == ProgressDisplay.BOTH)
            val withBar = hasGoal && (display == ProgressDisplay.BAR || display == ProgressDisplay.BOTH)
            val ringed = hasGoal && display == ProgressDisplay.PERIMETER
            if (ringed) { perimeter(); pad = max(pad, 14f) }
            val week = data.weekChangeKg?.takeIf { show.showChange }
            val fat = data.bodyFat.lastOrNull()?.takeIf { show.showBodyFat }
            val ends = show.showTarget && snap.startKg != null && snap.targetKg != null
            val detail = show.showDetail && (hasGoal || snap.reached)
            val noGoal = if (snap.targetKg == null) listOf("Set a target in Delta", "Set a target", "No target")
                else listOf("Weigh in to start the journey", "Weigh in to start", "Weigh in")

            when {
                // One cell wide.
                w < 120f -> {
                    val roomy = h >= 150f
                    val rows = stack(inner, listOf(
                        Slot("title", 11f, keep = 2),
                        Slot("weight", if (roomy) 26f else 20f, grow = if (withArt && roomy) 0f else 1f, keep = 9),
                        if (roomy && week != null) Slot("week", 13f, keep = 3) else null,
                        if (withArt && roomy) Slot("art", 40f, grow = 1f, keep = 6) else null,
                        if (roomy && hasGoal && !withArt) Slot("block", 40f, grow = 1f, keep = 6) else null,
                        if (!roomy || !hasGoal) Slot("percent", 13f, keep = 4) else null,
                        if (withBar && roomy) Slot("bar", 6f, keep = 5) else null,
                    ), 4f)
                    rows["title"]?.let { label("title", listOf("NOW"), it) }
                    rows["weight"]?.let { write("weight", weight(now), it, 30f) }
                    rows["week"]?.let { weekLine("week", week!!, it) }
                    rows["art"]?.let { artOrPercent(it) }
                    rows["block"]?.let { percentBlock(RectF(it.left, it.centerY() - min(it.height(), 52f) / 2f, it.right, it.centerY() + min(it.height(), 52f) / 2f)) }
                    rows["percent"]?.let {
                        if (hasGoal) text("percent", listOf("$percent% there", "$percent%"), it, 12f, colors.accent, type.numberBold)
                        else note("percent", noGoal, it)
                    }
                    rows["bar"]?.let { rail(it) }
                }
                // One row high.
                h < 130f -> {
                    val artSide = min(inner.height(), inner.width() * .36f)
                    val showArt = withArt && artLabelled(artSide)
                    val side = when {
                        showArt -> artSide
                        withArt || ringed || withBar -> min(inner.width() * .26f, 76f)
                        else -> 0f
                    }
                    val (body, block) = split(inner, side, if (side > 0f) 12f else 0f)
                    if (showArt) art(block) else if (side > 0f) percentBlock(block)
                    val parts = stack(body, listOf(Slot("main", 30f, grow = 1f, keep = 9),
                        if (withBar && h >= 72f) Slot("bar", 6f, keep = 3) else null), 7f)
                    parts["bar"]?.let { rail(it) }
                    val main = parts.getValue("main")
                    val middle = (detail || ends) && w >= 300f && main.width() >= 210f
                    val (left, mid) = if (middle) split(main, main.width() * .44f, 14f) else main to null
                    val rows = stack(left, listOf(
                        Slot("title", 11f, keep = 2),
                        Slot("weight", 20f, grow = 1f, keep = 9),
                        if (week != null) Slot("week", 13f, keep = 3) else if (!hasGoal) Slot("goal", 13f, keep = 1) else null,
                    ), 3f)
                    rows["title"]?.let { label("title", listOf("NOW"), it) }
                    rows["weight"]?.let { write("weight", weight(now), it, 34f) }
                    rows["week"]?.let { weekLine("week", week!!, it) }
                    rows["goal"]?.let { note("goal", noGoal, it) }
                    mid?.let { area ->
                        // Two figures fit: what is left and where to with both options on, otherwise each option's own pair.
                        val toGo = if (snap.reached) Figure(listOf("TARGET"), listOf(listOf(fig("Reached"))), colors.good)
                            else snap.remainingKg?.let { Figure(listOf("TO GO"), amount(it), colors.text) }
                        val moved = snap.changeKg?.let { Figure(listOf(if (it <= 0) "LOST" else "GAINED"), amount(it), colors.text) }
                        val start = snap.startKg?.let { Figure(listOf("START"), weight(it), colors.text) }
                        val goal = snap.targetKg?.takeUnless { snap.reached }?.let { Figure(listOf("TARGET"), weight(it), colors.accent) }
                        val items = when {
                            detail && ends -> listOfNotNull(toGo, goal)
                            ends -> listOfNotNull(start, goal)
                            else -> listOfNotNull(toGo, moved)
                        }
                        if (items.isNotEmpty()) figureRow(RectF(area.left, area.centerY() - min(area.height(), 40f) / 2f,
                            area.right, area.centerY() + min(area.height(), 40f) / 2f), items, 20f, spread = false)
                    }
                }
                // Two cells wide and two rows or more high.
                w < 240f -> {
                    val big = h >= 250f
                    val rows = stack(inner, listOf(
                        Slot("title", 11f, keep = 4),
                        Slot("weight", if (big) 34f else 27f, keep = 10),
                        if (big) Slot("moment", 13f, keep = 0) else null,
                        if (week != null) Slot("week", 13f, keep = 5) else null,
                        if (fat != null && big) Slot("fat", 13f, keep = 1) else null,
                        when {
                            withArt -> Slot("art", 44f, grow = 1f, keep = 8)
                            ringed || withBar -> Slot("percent", 30f, grow = 1f, keep = 7)
                            !hasGoal -> Slot("goal", 13f, grow = 1f, keep = 2)
                            else -> null
                        },
                        if (withBar) Slot("bar", 6f, keep = 6) else null,
                        if (ends && big) Slot("ends", 32f, keep = 2) else null,
                        if (detail) Slot("summary", 13f, keep = 3) else null,
                    ), 5f)
                    rows["title"]?.let { label("title", listOf("NOW"), it) }
                    rows["weight"]?.let { write("weight", weight(now), it, if (big) 38f else 30f) }
                    rows["moment"]?.let { r -> moment(snap.currentMs)?.let { note("moment", it, r) } }
                    rows["week"]?.let { weekLine("week", week!!, it) }
                    rows["fat"]?.let { note("fat", listOf("Body fat ${BodyFat.format(fat!!.percent)}", BodyFat.format(fat.percent)), it) }
                    rows["art"]?.let { artOrPercent(it) }
                    rows["percent"]?.let { percentBlock(RectF(it.left, it.centerY() - min(it.height(), 56f) / 2f, it.right, it.centerY() + min(it.height(), 56f) / 2f)) }
                    rows["goal"]?.let { note("goal", noGoal, it) }
                    rows["bar"]?.let { rail(it) }
                    rows["ends"]?.let { ends(it) }
                    rows["summary"]?.let { note("summary", summary(false), it) }
                }
                // Wide: the card as it is in the app and, from three rows high, the figure tiles that follow it there.
                h >= 300f -> {
                    val parts = stack(inner, listOf(
                        Slot("hero", min(inner.height() * .56f, 240f), keep = 10),
                        Slot("tiles", 60f, grow = 1f, keep = 5),
                    ), 12f, center = false)
                    hero(parts.getValue("hero"), now, withArt, withBar, ringed, week, fat, ends, detail)
                    parts["tiles"]?.let { figureGrid(it, listOf(rateFigure(), goalFigure(), averageFigure(), streakFigure()), first = 10) }
                }
                else -> hero(inner, now, withArt, withBar, ringed, week, fat, ends, detail)
            }
        }

        /** The Progress tab's first card, laid out across [area]. */
        fun hero(area: RectF, now: Double, withArt: Boolean, withBar: Boolean, ringed: Boolean, week: Double?, fat: BodyFatEntry?,
                 ends: Boolean, detail: Boolean) {
            val hasGoal = snap.fraction != null
            val parts = stack(area, listOf(
                Slot("top", 60f, grow = 1f, keep = 10),
                if (withBar) Slot("bar", 6f, keep = 6) else null,
                if (ends) Slot("ends", 32f, keep = 4) else null,
                if (detail) Slot("summary", 13f, keep = 5) else null,
            ), if (area.height() >= 170f) 9f else 6f)
            val top = parts.getValue("top")
            val side = if (withArt || ringed) min(top.height(), top.width() * .40f).coerceAtMost(200f) else 0f
            val (left, block) = split(top, side, if (side > 0f) 14f else 0f)
            if (withArt) artOrPercent(block) else if (ringed) percentBlock(RectF(block.left, block.centerY() - min(block.height(), 70f) / 2f,
                block.right, block.centerY() + min(block.height(), 70f) / 2f))
            val weightHeight = (top.height() * .36f).coerceIn(22f, 52f)
            val rows = stack(left, listOf(
                Slot("title", 11f, keep = 4),
                Slot("weight", weightHeight, keep = 10),
                Slot("moment", 13f, keep = 1),
                if (week != null) Slot("week", 13f, keep = 3) else null,
                if (!hasGoal && !snap.reached) Slot("goal", 13f, keep = 5) else null,
                if (fat != null) Slot("fat", 13f, keep = 2) else null,
            ), 4f)
            rows["title"]?.let { label("title", listOf("NOW"), it) }
            rows["weight"]?.let { write("weight", weight(now), it, 48f, face = if (weightHeight >= 36f) type.number else type.numberBold) }
            rows["moment"]?.let { r -> moment(snap.currentMs)?.let { note("moment", it, r) } }
            rows["week"]?.let { weekLine("week", week!!, it) }
            rows["goal"]?.let {
                note("goal", if (snap.targetKg == null) listOf("Set a target in Delta to see your progress", "Set a target in Delta", "No target")
                    else listOf("Weigh in to start the journey", "Weigh in to start"), it, colors.accent)
            }
            rows["fat"]?.let { note("fat", listOf("Body fat ${BodyFat.format(fat!!.percent)}", BodyFat.format(fat.percent)), it) }
            parts["bar"]?.let { rail(it) }
            parts["ends"]?.let { ends(it) }
            parts["summary"]?.let { note("summary", summary(!withArt && !ringed), it) }
        }

        /** Start on the left, target on the right. */
        fun ends(area: RectF) {
            val start = snap.startKg ?: return
            val target = snap.targetKg ?: return
            figureRow(area, listOf(Figure(listOf("START"), weight(start), colors.text), Figure(listOf("TARGET"), weight(target), colors.accent)),
                16f, spread = true, first = 20)
        }

        // ---- Complication -------------------------------------------------------------------

        /** The percentage art on its own, with what is left to go beneath it when there is room. */
        fun complication() {
            // Why there is no progress to show, whatever the widget's options.
            val missing = when {
                snap.currentKg == null -> listOf("No weigh-ins yet", "No weigh-ins", "No data")
                snap.targetKg == null -> listOf("Set a target in Delta", "Set a target", "No target")
                snap.fraction == null -> listOf("Weigh in to start", "Weigh in")
                else -> null
            }
            val remaining = snap.remainingKg?.takeIf { missing == null && !snap.reached }
            val target = snap.targetKg?.let { Units.weight(it, unit) }
            if (w >= 150f && w >= h * 1.6f) {
                val side = inner.height()
                art(RectF(inner.left, inner.top, inner.left + side, inner.bottom))
                val words = RectF(inner.left + side + 16f, inner.top, inner.right, inner.bottom)
                val rows = stack(words, listOf(
                    Slot("title", 11f, keep = 2),
                    Slot("value", min(words.height() * .4f, 34f).coerceAtLeast(16f), keep = 9),
                    if (show.showTarget && target != null && missing == null) Slot("target", 13f, keep = 1) else null,
                ), 4f)
                val showsRemaining = remaining != null && show.showDetail
                rows["title"]?.let { label("title", listOf(if (showsRemaining) "TO GO" else "PROGRESS"), it) }
                rows["value"]?.let {
                    when {
                        missing != null -> note("value", missing, it, colors.text)
                        snap.reached -> text("value", listOf("Reached"), it, 30f, colors.good, type.numberBold)
                        showsRemaining -> write("value", amount(remaining!!), it, 34f)
                        else -> write("value", listOf(listOf(fig("${percent ?: 0}"), tag("%")), listOf(fig("${percent ?: 0}"))), it, 34f,
                            colors.accent, unitColor = colors.accent, unitFace = type.numberBold, unitScale = .55f)
                    }
                }
                // Always with its label: a bare second weight would read as the current one.
                rows["target"]?.let { note("target", listOf("Target $target", "To $target"), it) }
                return
            }
            // The art and its caption are one group: the caption sits just under the art, both centred.
            val captionSize = (min(inner.width(), inner.height()) * .075f).coerceIn(11.5f, 16f)
            val captionHeight = captionSize * 1.15f
            val gap = 6f
            // Judged at the size the art has once a caption is under it, which is how it is drawn.
            val labelled = artLabelled(min(inner.width(), inner.height() - captionHeight - gap))
            val caption = when {
                missing != null -> missing
                !labelled -> listOf("${percent ?: 0}%")
                !show.showDetail -> null
                snap.reached -> listOf("Target reached", "Reached")
                remaining != null -> listOf("${Units.amount(remaining, unit)} to go", Units.amount(remaining, unit))
                else -> null
            }
            val withCaption = caption != null && inner.height() - captionHeight - gap >= 36f
            val side = min(inner.width(), inner.height() - if (withCaption) captionHeight + gap else 0f)
            val top = inner.centerY() - (side + if (withCaption) gap + captionHeight else 0f) / 2f
            art(RectF(inner.centerX() - side / 2f, top, inner.centerX() + side / 2f, top + side))
            if (withCaption) {
                val area = RectF(inner.left, top + side + gap, inner.right, top + side + gap + captionHeight)
                if (!labelled && missing == null) text("caption", caption!!, area, captionSize + 2f, colors.accent, type.numberBold, Paint.Align.CENTER)
                else text("caption", caption!!, area, captionSize, colors.textDim, type.body, Paint.Align.CENTER)
            }
        }

        // ---- Trend --------------------------------------------------------------------------

        /** The weight graph, with the target, the latest weight and, if wanted, the progress bar. */
        fun trend() {
            val now = snap.currentKg ?: return empty(listOf("TREND"), listOf("No weigh-ins yet", "No weigh-ins", "No data"),
                listOf("Your graph starts with two weigh-ins", "Needs two weigh-ins"))
            val spec = ChartSpec.forWidget(snap, settings)
            val range = settings.widgetTimeRange
            val title = if (range == WidgetTimeRange.JOURNEY) listOf("TREND") else listOf("TREND · ${range.label}", "TREND")
            val hasGoal = snap.fraction != null
            val bar = show.showBar && hasGoal
            val week = data.weekChangeKg?.takeIf { show.showChange }
            val date = shortDate(snap.currentMs)?.let { listOf(it) }
            when {
                w < 120f -> {
                    val rows = stack(inner, listOf(
                        Slot("title", 11f, keep = 1),
                        Slot("weight", 20f, keep = 9),
                        if (week != null && h >= 150f) Slot("week", 13f, keep = 2) else null,
                        if (h >= 82f) Slot("chart", 24f, grow = 1f, keep = 5) else null,
                        if (bar && h >= 130f) Slot("bar", 5f, keep = 3) else null,
                    ), 4f)
                    rows["title"]?.let { label("title", listOf("TREND"), it) }
                    rows["weight"]?.let { write("weight", weight(now), it, 26f) }
                    rows["week"]?.let { weekLine("week", week!!, it) }
                    rows["chart"]?.let { chart(it, spec, false) }
                    rows["bar"]?.let { rail(it) }
                }
                h < 130f && w >= 240f -> {
                    val parts = stack(inner, listOf(Slot("main", 30f, grow = 1f, keep = 9), if (bar && h >= 72f) Slot("bar", 5f, keep = 3) else null), 7f)
                    parts["bar"]?.let { rail(it) }
                    val (left, right) = split(parts.getValue("main"), parts.getValue("main").width() * .56f, 14f)
                    val rows = stack(left, listOf(
                        Slot("title", 11f, keep = 2),
                        Slot("weight", 20f, grow = 1f, keep = 9),
                        if (week != null) Slot("week", 13f, keep = 3) else null,
                    ), 3f)
                    rows["title"]?.let { label("title", title, it) }
                    rows["weight"]?.let { write("weight", weight(now), it, 32f) }
                    rows["week"]?.let { weekLine("week", week!!, it) }
                    val graph = stack(right, listOf(if (date != null && h >= 76f) Slot("date", 11f, keep = 1) else null,
                        Slot("chart", 22f, grow = 1f, keep = 9)), 3f)
                    graph["date"]?.let { label("date", date!!, it, Paint.Align.RIGHT) }
                    graph["chart"]?.let { chart(it, spec, false) }
                }
                h < 130f -> {
                    val rows = stack(inner, listOf(
                        Slot("header", 11f, keep = 2),
                        Slot("weight", 20f, keep = 9),
                        Slot("chart", 18f, grow = 1f, keep = 5),
                        if (bar && h >= 100f) Slot("bar", 5f, keep = 3) else null,
                    ), 4f)
                    rows["header"]?.let { header(it, title, date) }
                    rows["weight"]?.let { r ->
                        if (week != null && r.width() >= 140f) {
                            val (left, right) = split(r, r.width() * .42f, 8f)
                            write("weight", weight(now), left, 26f)
                            weekLine("week", week, RectF(right.left, right.centerY() - 7f, right.right, right.centerY() + 7f), Paint.Align.RIGHT)
                        } else write("weight", weight(now), r, 26f)
                    }
                    rows["chart"]?.let { chart(it, spec, false) }
                    rows["bar"]?.let { rail(it) }
                }
                else -> {
                    val wide = w >= 240f
                    val big = h >= 300f
                    val weightHeight = (h * .15f).coerceIn(24f, 46f)
                    val rows = stack(inner, listOf(
                        Slot("header", 11f, keep = 3),
                        Slot("weight", weightHeight, keep = 10),
                        if (week != null && !wide) Slot("week", 13f, keep = 4) else null,
                        Slot("chart", 40f, grow = 1f, keep = 8),
                        if (bar) Slot("bar", 6f, keep = 5) else null,
                        if (show.showDetail && hasGoal) Slot("summary", 13f, keep = 2) else null,
                        if (big && wide && snap.startKg != null) Slot("figures", 38f, keep = 1) else null,
                    ), if (h >= 170f) 7f else 5f)
                    rows["header"]?.let { header(it, title, date) }
                    rows["weight"]?.let { r ->
                        if (wide && week != null) {
                            val (left, right) = split(r, r.width() * .44f, 10f)
                            write("weight", weight(now), left, 46f, face = if (weightHeight >= 36f) type.number else type.numberBold)
                            weekLine("week", week, RectF(right.left, r.bottom - 15f, right.right, r.bottom - 1f), Paint.Align.RIGHT, 12.5f)
                        } else write("weight", weight(now), r, 46f, face = if (weightHeight >= 36f) type.number else type.numberBold)
                    }
                    rows["week"]?.let { weekLine("week", week!!, it) }
                    rows["chart"]?.let { chart(it, spec, wide && it.height() >= 88f) }
                    rows["bar"]?.let { rail(it) }
                    rows["summary"]?.let { note("summary", summary(true), it) }
                    rows["figures"]?.let { area ->
                        val items = listOfNotNull(
                            snap.startKg?.let { Figure(listOf("START"), weight(it), colors.text) },
                            snap.changeKg?.let { Figure(listOf(if (it <= 0) "LOST" else "GAINED"), amount(it), colors.text) },
                            if (snap.reached) Figure(listOf("TARGET"), listOf(listOf(fig("Reached"))), colors.good)
                            else snap.remainingKg?.let { Figure(listOf("TO GO"), amount(it), colors.accent) },
                        )
                        figureRow(area, items, 20f, spread = false, dividers = true)
                    }
                }
            }
        }

        // ---- Pace ---------------------------------------------------------------------------

        fun rateFigure(): Figure {
            val rate = snap.weeklyRateKg
            val tint = when {
                rate == null || abs(rate) < .02 -> colors.text
                InsightsCalculator.towardGoal(rate, snap.losing) -> colors.good
                else -> colors.warn
            }
            return Figure(listOf("WEEKLY RATE", "RATE"), rate?.let { change(it, "/wk") } ?: dash(), tint,
                listOf(if (rate == null) "Needs a week of data" else "Recent trend", if (rate == null) "Not yet" else "Trend"))
        }

        fun goalFigure(): Figure {
            val note = when (snap.pace) {
                Pace.AHEAD -> listOf("Ahead of your date", "Ahead")
                Pace.ON_TRACK -> listOf("On track for your date", "On track")
                Pace.BEHIND -> listOf("Behind your date", "Behind")
                null -> when {
                    snap.reached -> listOf("Well done", "Done")
                    snap.targetKg == null -> listOf("Set a target in Delta", "Set a target")
                    snap.projectedDay != null -> listOf("At the current rate", "At this rate")
                    else -> listOf("Trend isn't heading there", "Off course")
                }
            }
            val noteColor = when (snap.pace) { Pace.AHEAD -> colors.good; Pace.BEHIND -> colors.warn; else -> null }
            return Figure(listOf("GOAL BY", "GOAL"), goal(), colors.text, note, noteColor)
        }

        fun averageFigure(): Figure {
            val average = data.average7Kg
            val previous = data.averagePrevious7Kg
            val note = if (average != null && previous != null) {
                val difference = change(average - previous).first().joinToString("") { it.text }
                listOf("$difference vs last week", difference)
            } else listOf("Last 7 days")
            return Figure(listOf("7-DAY AVERAGE", "7-DAY AVG", "AVERAGE"), average?.let(::weight) ?: dash(), colors.text, note)
        }

        fun streakFigure(): Figure =
            Figure(listOf("STREAK"), streak(), colors.text, listOf("${data.daysWeighed} of the last 7 days", "${data.daysWeighed}/7 days"))

        /** The four figures of the Progress tab's tiles, in as many tiles as fit. */
        fun pace() {
            val items = listOf(rateFigure(), goalFigure(), averageFigure(), streakFigure())
            val status = snap.pace?.takeIf { show.showStatus && h >= 150f && w >= 120f }
            val parts = stack(inner, listOf(Slot("grid", 30f, grow = 1f, keep = 9), status?.let { Slot("status", 13f, keep = 1) }), 7f)
            figureGrid(parts.getValue("grid"), items)
            parts["status"]?.let { area2 ->
                val needed = snap.requiredRateKg?.let { change(it, "/wk").first().joinToString("") { p -> p.text } }
                val (words, color) = when (status!!) {
                    Pace.AHEAD -> listOf("Ahead of your goal date", "Ahead") to colors.good
                    Pace.ON_TRACK -> listOf("On track for your goal date", "On track") to colors.text
                    Pace.BEHIND -> listOfNotNull(needed?.let { "Behind · needs $it" }, "Behind your goal date", "Behind") to colors.warn
                }
                note("status", words, area2, color)
            }
        }

        // ---- Body fat -----------------------------------------------------------------------

        fun bodyFat() {
            val title = settings.widgetTimeRange.let { range ->
                if (range == WidgetTimeRange.JOURNEY) listOf("BODY FAT", "FAT") else listOf("BODY FAT · ${range.label}", "BODY FAT", "FAT")
            }
            val latest = data.bodyFat.lastOrNull() ?: return empty(title, listOf("No readings yet", "No readings", "None yet"),
                listOf("Body fat from your scale shows here", "From your scale"))
            val previous = data.bodyFat.dropLast(1).lastOrNull()
            val delta = previous?.let { latest.percent - it.percent }
            val target = snap.targetBodyFat?.takeIf { show.showTarget }
            val value = listOf(listOf(fig(one(latest.percent)), tag("%")))
            // Towards the target is good; with no target, falling body fat is.
            val wantsMore = target != null && target > latest.percent
            val deltaTint = delta?.let {
                when {
                    abs(it) < .05 -> colors.textDim
                    (it > 0) == wantsMore -> colors.good
                    else -> colors.warn
                }
            }
            val deltaText = delta?.let { d ->
                val sign = if ((abs(d) * 10).roundToLong() == 0L) "" else if (d < 0) "−" else "+"
                listOf(listOf(fig(sign + one(abs(d))), tag(" pp")), listOf(fig(sign + one(abs(d)))))
            }
            val spec = ChartSpec(settings.widgetTimeRange.bodyFat(snap).map { TrendPoint(it.epochMs, it.percent, it.percent) },
                ChartStyle.CONNECTED, WeightUnit.KG, target, metric = ChartMetric.BODY_FAT)
            val date = shortDate(latest.epochMs)
            val footer = listOfNotNull(
                target?.let { "Target ${BodyFat.format(it)} · $date" }, date?.let { "Measured $it" }, date,
            )
            when {
                w < 120f -> {
                    val rows = stack(inner, listOf(
                        Slot("title", 11f, keep = 2),
                        Slot("value", 20f, keep = 9),
                        if (deltaText != null && h >= 100f) Slot("delta", 13f, keep = 3) else null,
                        if (h >= 150f) Slot("chart", 24f, grow = 1f, keep = 4) else null,
                    ), 4f)
                    rows["title"]?.let { label("title", listOf("BODY FAT", "FAT"), it) }
                    rows["value"]?.let { write("body-fat", value, it, 26f, colors.accent) }
                    rows["delta"]?.let { write("delta", deltaText!!, it, 11.5f, deltaTint!!, type.number) }
                    rows["chart"]?.let { chart(it, spec, false) }
                }
                h < 130f -> {
                    val graphed = w >= 200f && h >= 70f
                    val (left, right) = if (graphed) split(inner, inner.width() * .52f, 14f) else inner to null
                    val rows = stack(left, listOf(
                        Slot("title", 11f, keep = 2),
                        Slot("value", 20f, grow = 1f, keep = 9),
                        if (deltaText != null || date != null) Slot("delta", 13f, keep = 3) else null,
                    ), 3f)
                    rows["title"]?.let { label("title", title, it) }
                    rows["value"]?.let { write("body-fat", value, it, 32f, colors.accent) }
                    rows["delta"]?.let { r ->
                        if (deltaText != null) {
                            val dated = date?.let { d -> listOf(deltaText.first() + tag("  ·  $d")) } ?: emptyList()
                            write("delta", dated + deltaText, r, 11.5f, deltaTint!!, type.number, unitScale = 1f, comfort = .9f)
                        } else date?.let { note("date", listOf("Measured $it", it), r) }
                    }
                    right?.let { chart(it, spec, false) }
                }
                else -> {
                    val wide = w >= 240f
                    val valueHeight = (h * .15f).coerceIn(24f, 46f)
                    val rows = stack(inner, listOf(
                        Slot("title", 11f, keep = 3),
                        Slot("value", valueHeight, keep = 10),
                        Slot("chart", 40f, grow = 1f, keep = 8),
                        if (footer.isNotEmpty()) Slot("footer", 13f, keep = 2) else null,
                    ), if (h >= 170f) 7f else 5f)
                    rows["title"]?.let { header(it, title, null) }
                    rows["value"]?.let { r ->
                        if (deltaText != null) {
                            val (left, right) = split(r, r.width() * .4f, 8f)
                            write("body-fat", value, left, 46f, colors.accent)
                            write("delta", listOf(deltaText.first() + tag(" since last")) + deltaText, RectF(right.left, r.bottom - 15f, right.right, r.bottom - 1f),
                                12.5f, deltaTint!!, type.number, Paint.Align.RIGHT, unitScale = 1f)
                        } else write("body-fat", value, r, 46f, colors.accent)
                    }
                    rows["chart"]?.let { chart(it, spec, wide && it.height() >= 88f) }
                    rows["footer"]?.let { note("footer", footer, it) }
                }
            }
        }
    }
}
