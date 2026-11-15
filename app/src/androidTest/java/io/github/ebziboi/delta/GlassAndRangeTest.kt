package io.github.ebziboi.delta

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.*
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.render.*
import io.github.ebziboi.delta.ui.AppViewModel
import io.github.ebziboi.delta.ui.glassTile
import io.github.ebziboi.delta.widget.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class GlassAndRangeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val store = SettingsStore(context)
    private val out = File(context.filesDir, "glass-range").apply { mkdirs() }
    init { Configurator.getInstance().setWaitForIdleTimeout(700) }

    @Test fun glassOpacityHonorsTheSliderAndAccessibilityInBothModes() {
        for (background in listOf(0xFF050505.toInt(), 0xFFF0F3F6.toInt())) {
            val base = AppAppearance(backgroundBase = background)
            val colors = DerivedTheme.derive(base).drawColors()
            assertEquals(0.09f, glassTile(colors, base.copy(glassOpacity = 0.09f)).alpha, 0.005f)
            assertEquals(0.91f, glassTile(colors, base.copy(glassOpacity = 0.91f)).alpha, 0.005f)
            assertEquals(1f, glassTile(colors, base.copy(reduceTransparency = true)).alpha, 0f)
            assertEquals(1f, glassTile(colors, base.copy(designStyle = DesignStyle.TUBIK)).alpha, 0f)
        }
        val settings = AppSettings(appearance = AppAppearance(backgroundStyle = BackgroundStyle.SOLID, glassOpacity = 0.91f),
            widgetOpacityDark = 0.27f, widgetGlass = WidgetGlass.FROSTED)
        val bitmap = Bitmap.createBitmap(300, 180, Bitmap.Config.ARGB_8888)
        GlassWidgetSurface.draw(Canvas(bitmap), 300f, 180f, AppearanceEngine.forWidget(context, settings, true), settings)
        assertEquals("Widget's 27% opacity must stay 27% regardless of app opacity", 0.27f, (bitmap.getPixel(150, 90) ushr 24) / 255f, 0.005f)
        bitmap.recycle()
    }

    @Test fun graphRangesSelectTheCorrectHistoryWithoutChangingJourneyTotals() {
        val now = Instant.parse("2026-11-15T12:00:00Z")
        val days = listOf(1L, 6, 12, 40, 110, 300, 400)
        val weights = days.map { WeightEntry(now.minusSeconds(it * 86400).toEpochMilli(), 80.0 + it * 0.04, false, "$it") }
        val settings = AppSettings(startEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(), targetKg = 75.0)
        val snapshot = ProgressCalculator.compute(weights, settings, ZoneOffset.UTC, now).copy(
            projectedDay = LocalDate.of(2027, 3, 1), bodyFat = weights.map { BodyFatEntry(it.epochMs, 23.0, false, it.id) })
        val sizes = mapOf(WidgetTimeRange.WEEK to 2, WidgetTimeRange.MONTH to 3, WidgetTimeRange.THREE_MONTHS to 4,
            WidgetTimeRange.YEAR to 6, WidgetTimeRange.JOURNEY to 3)
        for ((range, count) in sizes) {
            val spec = ChartSpec.forWidget(snapshot, settings.copy(widgetTimeRange = range), now, ZoneOffset.UTC)
            assertEquals(range.label, count, spec.points.size)
            assertEquals(count, range.bodyFat(snapshot, now, ZoneOffset.UTC).size)
            if (range == WidgetTimeRange.JOURNEY) {
                assertEquals(snapshot.startKg, spec.startKg); assertNotNull(spec.projection)
            } else {
                assertNull(spec.startKg); assertNull(spec.fraction); assertNull(spec.projection)
            }
            assertEquals(75.0, spec.targetKg!!, 0.0)
        }
        assertNull(ChartSpec.forWidget(snapshot, settings.copy(widgetContent = WidgetContent(showTarget = false)), now).targetKg)
        assertEquals(80.48, snapshot.startKg!!, 0.001)
        assertEquals(7, snapshot.history.size)
        val empty = snapshot.copy(history = emptyList(), points = emptyList())
        assertTrue(ChartSpec.forWidget(empty, settings.copy(widgetTimeRange = WidgetTimeRange.WEEK), now).points.isEmpty())
    }

    @Test fun eachWidgetKeepsItsRangeEvenWhenFollowingAppAppearance() = runBlocking {
        val styles = WidgetStyleStore(context)
        val a = 19_300_001; val b = 19_300_002
        val oldA = styles.style(a).first(); val oldB = styles.style(b).first()
        try {
            for (range in WidgetTimeRange.entries) {
                val chosen = WidgetStyle(timeRange = range)
                styles.save(a, chosen)
                assertEquals(range, WidgetStyleStore(context).style(a).first()!!.timeRange)
                val settings = AppSettings(targetKg = 73.0)
                assertEquals(range, chosen.apply(settings, AppAppearance()).widgetTimeRange)
                assertEquals(73.0, chosen.apply(settings, AppAppearance()).targetKg!!, 0.0)
            }
            styles.save(b, WidgetStyle(timeRange = WidgetTimeRange.MONTH))
            styles.restore(intArrayOf(a, b), intArrayOf(b, a))
            assertEquals(WidgetTimeRange.MONTH, styles.style(a).first()!!.timeRange)
            assertEquals(WidgetTimeRange.JOURNEY, styles.style(b).first()!!.timeRange)
        } finally {
            if (oldA == null) styles.delete(intArrayOf(a)) else styles.save(a, oldA)
            if (oldB == null) styles.delete(intArrayOf(b)) else styles.save(b, oldB)
        }
    }

    @Test fun glassSlidersPreviewWhileHeldAndPersistOnRelease() = runBlocking {
        val original = store.settings.first()
        val weights = store.weights.first()
        var vm: AppViewModel? = null
        try {
            store.saveWeights(SampleData.entries(LocalDate.now().minusDays(74)))
            store.update { it.copy(onboardingDone = true, motion = false, appearance = AppAppearance(
                backgroundStyle = BackgroundStyle.SOLID, glassBlurRadius = 0f, glassOpacity = 0.4f, reduceMotion = true)) }
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            assertTrue(device.wait(Until.hasObject(By.text("History")), 15000)); Thread.sleep(700)
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                vm = ViewModelProvider(activity)[AppViewModel::class.java]
            }
            tap("Settings"); tap("Appearance")
            show(By.desc("Glass blur"))
            show(By.desc("Glass material preview"))
            val sharp = preview("blur-zero")
            val blur = show(By.desc("Glass blur")).visibleBounds
            val down = dragHeld(blur)
            try {
                assertTrue("Blur must preview before releasing the finger", vm!!.appearancePreview.value!!.glassBlurRadius > 30)
                assertEquals("Dragging must not write each frame", 0f, store.settings.first().appearance!!.glassBlurRadius, 0.001f)
            } finally { release(down, blur) }
            device.waitForIdle(1000)
            println("Released blur; waiting for the chosen value to persist")
            try { kotlinx.coroutines.withTimeout(15000) { vm!!.settings.first { it?.appearance?.glassBlurRadius?.let { blur -> blur > 30 } == true } } }
            catch (error: Exception) { throw AssertionError("Blur release: stored=${store.settings.first().appearance?.glassBlurRadius}, preview=${vm!!.appearancePreview.value?.glassBlurRadius}, displayed=${vm!!.settings.value?.appearance?.glassBlurRadius}", error) }
            Thread.sleep(600)
            val blurred = preview("blur-high")
            var changed = 0
            for (y in 0 until sharp.height step 2) for (x in 0 until sharp.width step 2) {
                val a = sharp.getPixel(x, y); val b = blurred.getPixel(x, y)
                if (abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) + abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) + abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)) > 30) changed++
            }
            assertTrue("Changing blur must visibly alter the textured glass preview", changed > sharp.width * sharp.height / 400)
            sharp.recycle(); blurred.recycle()
            val transparency = show(By.desc("Glass transparency")).visibleBounds
            val second = dragHeld(transparency)
            try {
                assertTrue("Transparency must preview while held", vm!!.appearancePreview.value!!.glassOpacity < 0.35f)
                assertEquals(0.4f, store.settings.first().appearance!!.glassOpacity, 0.001f)
            } finally { release(second, transparency) }
            device.waitForIdle(1000)
            println("Released transparency; waiting for the chosen value to persist")
            try { kotlinx.coroutines.withTimeout(15000) { vm!!.settings.first { it?.appearance?.glassOpacity?.let { opacity -> opacity < 0.35f } == true } } }
            catch (error: Exception) { throw AssertionError("Transparency release: stored=${store.settings.first().appearance?.glassOpacity}, preview=${vm!!.appearancePreview.value?.glassOpacity}, displayed=${vm!!.settings.value?.appearance?.glassOpacity}", error) }
            assertEquals(original.targetKg, store.settings.first().targetKg)
            assertEquals(original.unit, store.settings.first().unit)
        } catch (error: Throwable) { device.takeScreenshot(File(out, "failure.png")); device.dumpWindowHierarchy(File(out, "failure.xml")); throw error }
        finally { vm?.previewAppearance(null); store.update { original }; store.saveWeights(weights) }
    }

    private fun inject(start: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        instrumentation.uiAutomation.injectInputEvent(event, true); event.recycle()
    }
    private fun dragHeld(b: android.graphics.Rect): Long {
        val start = SystemClock.uptimeMillis()
        inject(start, MotionEvent.ACTION_DOWN, b.left + b.width() * 0.15f, b.exactCenterY())
        repeat(5) { i -> Thread.sleep(45); inject(start, MotionEvent.ACTION_MOVE, b.left + b.width() * (0.2f + i * 0.15f), b.exactCenterY()) }
        Thread.sleep(300); return start
    }
    private fun release(start: Long, b: android.graphics.Rect) = inject(start, MotionEvent.ACTION_UP, b.left + b.width() * 0.8f, b.exactCenterY())
    private fun preview(name: String): Bitmap {
        instrumentation.uiAutomation.clearCache()
        val b = show(By.desc("Glass material preview")).visibleBounds
        val file = File(out, "$name.png"); device.takeScreenshot(file)
        val all = BitmapFactory.decodeFile(file.absolutePath)
        return Bitmap.createBitmap(all, b.left, b.top, b.width(), b.height()).also { all.recycle() }
    }
    private fun show(selector: BySelector): UiObject2 {
        for (forward in listOf(true, false)) for (i in 0..22) {
            instrumentation.uiAutomation.clearCache()
            val target = device.findObjects(selector).firstOrNull {
                runCatching { it.visibleBounds.let { b -> b.top > 65 && b.bottom < device.displayHeight * 0.82f && b.height() > 6 } }.getOrDefault(false)
            }
            if (target != null) return target
            val x = device.displayWidth * 7 / 100
            device.swipe(x, device.displayHeight * (if (forward) 65 else 43) / 100,
                x, device.displayHeight * (if (forward) 43 else 65) / 100, 70)
            device.waitForIdle(700); Thread.sleep(350)
        }
        throw AssertionError("Missing $selector")
    }
    private fun tap(text: String) {
        if (text == "Settings") { device.wait(Until.findObject(By.desc("Settings")), 10000)!!.click(); device.waitForIdle(1000); Thread.sleep(300); return }
        instrumentation.uiAutomation.clearCache()
        var node = if (text in listOf("Settings", "History", "Progress")) device.findObjects(By.text(text)).last { it.visibleBounds.centerY() > device.displayHeight * 0.82f } else show(By.textStartsWith(text))
        while (!node.isClickable && node.parent != null) node = node.parent
        node.click(); device.waitForIdle(700); Thread.sleep(350)
    }
}
