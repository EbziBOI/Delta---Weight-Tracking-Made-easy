package io.github.ebziboi.delta

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.render.WidgetBitmaps
import io.github.ebziboi.delta.render.WidgetKind
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * Draws every widget at the sizes a home screen can give it, from one cell to
 * a whole page, onto contact sheets over a wallpaper. Nothing is asserted here:
 * the sheets are for looking at. The sizes are what four- and five-column
 * grids hand out, in dp.
 */
@RunWith(AndroidJUnit4::class)
class WidgetSizeSheetTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val out = File(context.filesDir, "widget-sizes").apply { deleteRecursively(); mkdirs() }

    private fun snapshot(settings: AppSettings): ProgressSnapshot {
        val start = LocalDate.now().minusDays(74)
        val weights = SampleData.entries(start)
        return ProgressCalculator.compute(weights, settings.copy(startEpochDay = start.toEpochDay()))
            .copy(bodyFat = weights.map { BodyFatEntry(it.epochMs, 27.0 - (it.kg - 88) * .12, false, it.id) }, targetBodyFat = 22.0)
    }

    /** One row per entry of [rows], each a list of (kind, width, height) drawn left to right. */
    private fun sheet(name: String, rows: List<List<Triple<WidgetKind, Float, Float>>>, settings: AppSettings,
                      snap: ProgressSnapshot, night: Boolean, scale: Float) {
        val gap = 14f
        val label = 13f
        val width = rows.maxOf { row -> row.sumOf { (it.second + gap).toDouble() }.toFloat() } + gap
        val height = rows.sumOf { row -> (row.maxOf { it.third } + gap + label).toDouble() }.toFloat() + gap
        val bitmap = Bitmap.createBitmap((width * scale).toInt(), (height * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        SampleData.wallpaper(canvas, bitmap.width, bitmap.height, !night)
        canvas.scale(scale, scale)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (night) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(); textSize = 9f }
        var y = gap
        for (row in rows) {
            var x = gap
            for ((kind, w, h) in row) {
                // The tone of the sample wallpaper the sheet is drawn over, standing in for the phone's own.
                val image = WidgetBitmaps.render(context, kind, w, h, snap, settings, night, maxSidePx = maxOf(w, h) * scale,
                    backdrop = if (night) 0xFF2E2556.toInt() else 0xFFF2CFD8.toInt())
                canvas.drawBitmap(image, null, RectF(x, y, x + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
                image.recycle()
                canvas.drawText("${kind.name.lowercase()} ${w.toInt()}×${h.toInt()}", x, y + h + 10f, ink)
                x += w + gap
            }
            y += row.maxOf { it.third } + gap + label
        }
        SampleData.save(bitmap, File(out, "$name.png"))
        bitmap.recycle()
    }

    @Test fun everyWidgetAtEverySize() {
        val settings = AppSettings(onboardingDone = true, targetKg = 80.0,
            targetEpochDay = LocalDate.now().plusDays(95).toEpochDay())
        val snap = snapshot(settings)
        // One row high, then two, three and four rows high.
        val small = listOf(58f to 70f, 76f to 85f, 128f to 85f, 165f to 85f, 198f to 105f, 255f to 85f, 345f to 85f, 345f to 105f)
        val medium = listOf(76f to 185f, 128f to 185f, 165f to 185f, 198f to 225f, 255f to 185f, 345f to 185f)
        val large = listOf(165f to 290f, 165f to 395f, 255f to 290f, 345f to 290f, 345f to 395f, 420f to 420f)
        for (kind in WidgetKind.entries) {
            val id = kind.name.lowercase()
            sheet("$id-1-small", listOf(small.map { Triple(kind, it.first, it.second) }), settings, snap, true, 2f)
            sheet("$id-2-medium", listOf(medium.map { Triple(kind, it.first, it.second) }), settings, snap, true, 1.5f)
            sheet("$id-3-large", listOf(large.map { Triple(kind, it.first, it.second) }), settings, snap, true, 1f)
        }
        // Every widget side by side at the sizes most people use.
        val common = listOf(76f to 85f, 165f to 85f, 165f to 185f, 345f to 85f, 345f to 185f)
        fun all(name: String, look: AppSettings, state: ProgressSnapshot = snap, night: Boolean = true) =
            sheet(name, WidgetKind.entries.map { kind -> common.map { Triple(kind, it.first, it.second) } }, look, state, night, 1.5f)
        all("all-common-dark", settings)
        all("all-common-light", settings, night = false)
        all("all-stone", settings.copy(unit = WeightUnit.ST_LB))
        all("all-dashboard", settings.copy(appearance = DesignStyle.TUBIK.defaults()))
        all("all-technical-solid", settings.copy(typeStyle = TypeStyle.TECHNICAL, widgetGlass = WidgetGlass.SOLID))
        val empty = snap.copy(points = emptyList(), history = emptyList(), currentKg = null, currentMs = null, startKg = null,
            fraction = null, bodyFat = emptyList(), weeklyRateKg = null, projectedDay = null, streakDays = 0)
        all("all-empty", settings, empty)
        all("all-no-target", settings.copy(targetKg = null), ProgressCalculator.compute(SampleData.entries(LocalDate.now().minusDays(74)),
            settings.copy(targetKg = null, startEpochDay = LocalDate.now().minusDays(74).toEpochDay())))
        // The Progress card in each of the app's progress displays, and the complication in each design.
        val cardSizes = listOf(76f to 185f, 165f to 85f, 165f to 185f, 345f to 85f, 345f to 185f, 345f to 395f)
        for (display in ProgressDisplay.entries) {
            sheet("card-${display.name.lowercase()}", listOf(cardSizes.map { Triple(WidgetKind.CARD, it.first, it.second) }),
                settings.copy(progressDisplay = display, progressBarStyle = ProgressBarStyle.SEGMENTS), snap, true, 1.5f)
        }
        for (design in PercentageDesign.entries) {
            sheet("complication-${design.name.lowercase()}", listOf(listOf(58f to 70f, 76f to 85f, 165f to 185f, 345f to 120f)
                .map { Triple(WidgetKind.COMPLICATION, it.first, it.second) }), settings.copy(percentageDesign = design), snap, true, 1.5f)
        }
    }
}
