package io.github.ebziboi.delta

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.content.res.Configuration
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.render.*
import io.github.ebziboi.delta.widget.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class RenderContractTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val start = LocalDate.of(2026, 9, 1)
    private val settings = AppSettings(targetKg = 80.0, startEpochDay = start.toEpochDay())
    private val sample = SampleData.entries(start)
    private val snapshot = ProgressCalculator.compute(sample, settings, ZoneOffset.UTC, Instant.parse("2026-11-14T12:00:00Z"))

    private fun chart(spec: ChartSpec): Bitmap {
        val bitmap = Bitmap.createBitmap(700, 400, Bitmap.Config.ARGB_8888)
        TrendChart.draw(Canvas(bitmap), RectF(0f, 0f, 700f, 400f), spec,
            Palettes.resolve(context, ThemeStyle.AURORA, Accent.SKY), DeltaFonts.canvas(context, TypeStyle.EDITORIAL), 1f, true)
        return bitmap
    }

    @Test fun perWidgetPhotoTextAndContentOptionsReachTheWidget() {
        // A plain red "photo" in Delta's storage, where the widget editor puts imported photos.
        val file = java.io.File(context.filesDir, "${WidgetStyleStore.PHOTO_PREFIX}test-${System.nanoTime()}.png")
        val photo = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        file.outputStream().use { photo.compress(Bitmap.CompressFormat.PNG, 100, it) }
        try {
            val app = settings.copy(widgetGlass = WidgetGlass.SOLID)
            val appearance = AppearanceEngine.resolve(context, app)
            fun draw(style: WidgetStyle) = WidgetBitmaps.render(context, WidgetKind.PROGRESS, 170f, 170f, snapshot,
                style.apply(app, appearance), night = true)
            val base = WidgetStyle.from(app)
            val mesh = draw(base.copy(backgroundStyle = BackgroundStyle.MESH))
            val photographed = draw(base.copy(backgroundStyle = BackgroundStyle.IMAGE,
                backgroundImage = android.net.Uri.fromFile(file).toString()))
            val corner = photographed.getPixel(photographed.width / 8, photographed.height * 3 / 4)
            assertTrue("The photo must show through: ${Integer.toHexString(corner)}",
                android.graphics.Color.red(corner) > android.graphics.Color.blue(corner) + 40)
            assertFalse(mesh.sameAs(photographed))
            assertFalse("Text colour must follow the widget's choice",
                draw(base.copy(content = WidgetContent(text = TextContrast.LIGHT)))
                    .sameAs(draw(base.copy(content = WidgetContent(text = TextContrast.DARK)))))
            assertFalse("Hiding the detail line must change the widget",
                draw(base).sameAs(draw(base.copy(content = WidgetContent(showDetail = false)))))
        } finally {
            file.delete()
        }
    }

    @Test fun graphControlsChangeTheRenderedGraph() {
        val base = ChartSpec.journey(snapshot, ChartStyle.TREND, WeightUnit.KG, false)
        val trend = chart(base)
        val connected = chart(base.copy(style = ChartStyle.CONNECTED))
        assertFalse("Connected dots must use raw values", trend.sameAs(connected))
        val projected = chart(base.copy(projection = Projection(sample.last().epochMs + 90L * 86_400_000, 80.0)))
        assertFalse("Projection must be visible", trend.sameAs(projected))
        val overridden = chart(base.copy(startKg = 130.0))
        assertFalse("A corrected start weight must affect the scale", trend.sameAs(overridden))
        listOf(trend, connected, projected, overridden).forEach(Bitmap::recycle)
    }

    @Test fun widgetsRespectSizeCapGlassAndTypography() {
        for (kind in WidgetKind.entries) {
            for ((w, h) in listOf(110f to 50f, 200f to 380f, 1200f to 1200f)) {
                val bitmap = WidgetBitmaps.render(context, kind, w, h, snapshot, settings, maxSidePx = 500f)
                assertTrue(bitmap.width <= 500 && bitmap.height <= 500)
                assertEquals("Corners must be transparent", 0, bitmap.getPixel(0, 0) ushr 24)
                bitmap.recycle()
            }
        }
        fun render(glass: WidgetGlass, type: TypeStyle = TypeStyle.EDITORIAL) = WidgetBitmaps.render(context,
            WidgetKind.SUMMARY, 350f, 120f, snapshot, settings.copy(widgetGlass = glass,
                widgetOpacityDark = glass.defaultDark, typeStyle = type), night = true)
        val solid = render(WidgetGlass.SOLID)
        val clear = render(WidgetGlass.CLEAR)
        val modern = render(WidgetGlass.SOLID, TypeStyle.MODERN)
        assertFalse("Type selection must reach widget values", solid.sameAs(modern))
        assertTrue((clear.getPixel(clear.width / 2, clear.height - 8) ushr 24) <
            (solid.getPixel(solid.width / 2, solid.height - 8) ushr 24))
        listOf(solid, clear, modern).forEach(Bitmap::recycle)
    }

    @Test fun journeyIsRegisteredAndDistinctFromTrend() {
        val providers = AppWidgetManager.getInstance(context).installedProviders.filter { it.provider.packageName == context.packageName }
        assertEquals(WidgetKind.entries.size, providers.size)
        assertTrue(providers.any { it.provider == ComponentName(context, io.github.ebziboi.delta.widget.BodyFatWidgetReceiver::class.java) })
        assertTrue(providers.any { it.provider == ComponentName(context, JourneyWidgetReceiver::class.java) })
        val journey = WidgetBitmaps.render(context, WidgetKind.JOURNEY, 350f, 200f, snapshot, settings)
        val trend = WidgetBitmaps.render(context, WidgetKind.TREND, 350f, 200f, snapshot, settings)
        assertFalse(journey.sameAs(trend))
        journey.recycle(); trend.recycle()
    }

    @Test fun remoteViewsSwitchImagesWithSystemMode() {
        if (android.os.Build.VERSION.SDK_INT < 31) return
        val views = BitmapWidget.views(context, WidgetKind.PROGRESS, 170f, 170f, snapshot,
            settings.copy(widgetTheme = WidgetTheme.SYSTEM), 400f)
        fun draw(night: Boolean): Bitmap {
            val configuration = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val themed = context.createConfigurationContext(configuration)
            val view = views.apply(themed, null)
            view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 400, 400)
            return Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val day = draw(false); val night = draw(true)
        assertFalse("The launcher must receive both day and night images", day.sameAs(night))
        day.recycle(); night.recycle()
    }

    @Test fun progressHandlesGainingLosingReachedAndEmpty() {
        val today = Instant.parse("2026-11-14T12:00:00Z")
        fun entry(day: Int, kg: Double) = WeightEntry(today.minusSeconds(day * 86400L).toEpochMilli(), kg, true, "$day")
        val loss = listOf(entry(14, 100.0), entry(7, 95.0), entry(0, 90.0))
        val losing = ProgressCalculator.compute(loss, AppSettings(targetKg = 80.0), ZoneOffset.UTC, today)
        assertEquals(0.5f, losing.fraction!!, 0.001f)
        assertTrue(losing.weeklyRateKg!! < 0)
        val gaining = ProgressCalculator.compute(loss.map { it.copy(kg = 180 - it.kg) }, AppSettings(targetKg = 100.0), ZoneOffset.UTC, today)
        assertEquals(0.5f, gaining.fraction!!, 0.001f)
        assertFalse(gaining.losing)
        val reached = ProgressCalculator.compute(loss, AppSettings(targetKg = 92.0), ZoneOffset.UTC, today)
        assertTrue(reached.reached)
        assertEquals(1f, reached.fraction!!, 0f)
        assertNull(reached.projectedDay)
        assertNull(ProgressCalculator.compute(emptyList(), settings, ZoneOffset.UTC, today).fraction)
    }
}
