package io.github.ebziboi.delta

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.nutrition.*
import io.github.ebziboi.delta.render.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * Renders every complication design and every widget in the Tubik design system onto contact sheets
 * (in the app's files dir, `tubik-review/`) for visual review, and checks each one actually draws.
 */
@RunWith(AndroidJUnit4::class)
class TubikReviewGalleryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val out = File(context.filesDir, "tubik-review").apply { mkdirs() }
    private val today = LocalDate.now()
    private val start = today.minusDays(74)
    private val entries = SampleData.entries(start)
    private val tubik = AppSettings().withDesignStyle(DesignStyle.TUBIK, AppAppearance())
        .copy(onboardingDone = true, targetKg = 80.0, startEpochDay = start.toEpochDay())

    private fun sheet(name: String, tiles: List<Bitmap>, columns: Int, night: Boolean) {
        val gap = 24
        val cell = tiles.maxOf { it.width }; val rowH = tiles.maxOf { it.height }
        val rows = (tiles.size + columns - 1) / columns
        val sheet = Bitmap.createBitmap(gap + columns * (cell + gap), gap + rows * (rowH + gap), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet); SampleData.wallpaper(canvas, sheet.width, sheet.height, !night)
        tiles.forEachIndexed { i, b -> canvas.drawBitmap(b, (gap + i % columns * (cell + gap)).toFloat(), (gap + i / columns * (rowH + gap)).toFloat(), Paint()) }
        SampleData.save(sheet, File(out, "$name-${if (night) "night" else "day"}.png"))
        sheet.recycle()
    }

    /** True when the bitmap has drawn, opaque content beyond a flat fill. */
    private fun drawn(b: Bitmap): Boolean {
        val seen = HashSet<Int>()
        for (y in 0 until b.height step 6) for (x in 0 until b.width step 6) { val p = b.getPixel(x, y); if (p ushr 24 > 200) seen += p }
        return seen.size > 12
    }

    @Test fun complicationsInEveryDesignSizeAndProgressState() {
        for (night in listOf(true, false)) {
            val tiles = mutableListOf<Bitmap>()
            for (design in PercentageDesign.entries) {
                val look = tubik.copy(percentageDesign = design, progressDisplay = ProgressDisplay.COMPLICATION)
                val snapshot = ProgressCalculator.compute(entries, look)
                for ((w, h) in listOf(110f to 110f, 170f to 180f, 345f to 120f)) {
                    val b = WidgetBitmaps.render(context, WidgetKind.COMPLICATION, w, h, snapshot, look, night, 700f * maxOf(w, h) / 360f)
                    assertTrue("${design.label} ${w}x$h draws", drawn(b)); tiles += b
                }
            }
            sheet("complications", tiles, 3, night); tiles.forEach(Bitmap::recycle)
        }
        // Empty, just started and reached states must say why, not show a bare 0%.
        val states = listOf(emptyList<WeightEntry>(), entries.take(1), entries + WeightEntry(System.currentTimeMillis(), 79.0, true, "reached"))
        val tiles = states.map { e -> WidgetBitmaps.render(context, WidgetKind.COMPLICATION, 170f, 180f, ProgressCalculator.compute(e, tubik), tubik, true, 700f) }
        tiles.forEach { assertTrue(drawn(it)) }
        sheet("complication-states", tiles, 3, true); tiles.forEach(Bitmap::recycle)
    }

    @Test fun everyWidgetKindInTubik() {
        val snapshot = ProgressCalculator.compute(entries, tubik)
        val food = NutritionState(loaded = true, entries = listOf(
            DiaryEntry(day = today, meal = Meal.BREAKFAST, food = Food(name = "Porridge", per100 = mapOf(Nutrient.ENERGY to 280.0, Nutrient.PROTEIN to 12.0, Nutrient.CARBS to 40.0, Nutrient.FAT to 7.0)), amount = 250.0),
            DiaryEntry(day = today, meal = Meal.LUNCH, food = Food(name = "Chicken wrap", per100 = mapOf(Nutrient.ENERGY to 230.0, Nutrient.PROTEIN to 14.0, Nutrient.CARBS to 24.0, Nutrient.FAT to 8.0)), amount = 220.0)),
            targets = NutritionTargets(amounts = mapOf(Nutrient.ENERGY to 2000.0, Nutrient.PROTEIN to 120.0, Nutrient.CARBS to 220.0, Nutrient.FAT to 70.0)))
        for (night in listOf(true, false)) {
            val tiles = WidgetKind.entries.flatMap { k -> listOf(k.defaultWidth to k.defaultHeight, 345f to 120f).map { (w, h) ->
                WidgetBitmaps.render(context, k, w, h, snapshot, tubik, night, 700f * maxOf(w, h) / 360f) } } +
                FoodWidgetKind.entries.flatMap { k -> listOf(k.width to k.height, 180f to 110f).map { (w, h) ->
                    FoodWidgetBitmaps.render(context, k, w, h, food, tubik, night, maxSide = 700f * maxOf(w, h) / 360f) } }
            tiles.forEach { assertTrue(drawn(it)) }
            sheet("widgets", tiles, 2, night); tiles.forEach(Bitmap::recycle)
        }
    }
}
