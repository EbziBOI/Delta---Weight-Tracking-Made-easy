package io.github.ebziboi.delta

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.render.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Presentation checks on an emulator: presets, all backgrounds, custom-colour inputs and static widget materials. */
@RunWith(AndroidJUnit4::class)
class AppearanceGalleryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val store = SettingsStore(context)
    private val out = File(context.filesDir, "appearance-gallery").apply { mkdirs() }
    init { Configurator.getInstance().setWaitForIdleTimeout(1_000) }

    @Test fun presetsBackgroundsCustomInputsAndAccessibility() = runBlocking {
        val original = store.settings.first()
        val originalWeights = store.weights.first()
        val photo = File(context.filesDir, "appearance-gallery.png")
        val image = Bitmap.createBitmap(700, 1200, Bitmap.Config.ARGB_8888)
        SampleData.wallpaper(Canvas(image), image.width, image.height, light = false)
        SampleData.save(image, photo); image.recycle()
        val day = LocalDate.now().minusDays(74)
        val entries = SampleData.entries(day)
        store.saveWeights(entries)
        try {
            if (InstrumentationRegistry.getArguments().getString("delta.inputsOnly") != "true") {
            for (preset in AppearancePreset.entries) {
                val appearance = preset.appearance().copy(reduceMotion = true)
                val settings = original.copy(onboardingDone = true, targetKg = 80.0, startEpochDay = day.toEpochDay(),
                    appearance = appearance, motion = false, typeStyle = TypeStyle.MODERN)
                store.update { settings }
                launch()
                shot("app-${preset.name.lowercase()}")
                for (glass in WidgetGlass.entries) for (night in listOf(false, true)) {
                    val look = settings.copy(widgetTheme = WidgetTheme.SYSTEM, widgetGlass = glass, widgetOpacityDark = glass.defaultDark, widgetOpacityLight = glass.defaultLight)
                    val snapshot = ProgressCalculator.compute(entries, look)
                    val widgets = listOf(WidgetKind.PROGRESS to (172f to 180f), WidgetKind.PACE to (172f to 180f),
                        WidgetKind.SUMMARY to (360f to 105f), WidgetKind.JOURNEY to (360f to 200f), WidgetKind.TREND to (360f to 190f))
                        .map { (kind, size) -> WidgetBitmaps.render(context, kind, size.first, size.second, snapshot, look, night, 700f * maxOf(size.first, size.second) / 360f) }
                    val gap = 24
                    val width = widgets[2].width + gap * 2
                    val height = widgets[0].height + widgets.drop(2).sumOf { it.height } + gap * 5
                    val sheet = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(sheet); SampleData.wallpaper(canvas, width, height, !night)
                    canvas.drawBitmap(widgets[0], gap.toFloat(), gap.toFloat(), null)
                    canvas.drawBitmap(widgets[1], (width - gap - widgets[1].width).toFloat(), gap.toFloat(), null)
                    var y = widgets[0].height + gap * 2
                    for (widget in widgets.drop(2)) { canvas.drawBitmap(widget, gap.toFloat(), y.toFloat(), null); y += widget.height + gap }
                    SampleData.save(sheet, File(out, "widgets-${preset.name.lowercase()}-${glass.name.lowercase()}-${if (night) "night" else "day"}.png"))
                    widgets.forEach(Bitmap::recycle); sheet.recycle()
                }
            }
            for (style in BackgroundStyle.entries) {
                store.update { it.copy(appearance = AppearancePreset.ICE.appearance().copy(backgroundStyle = style,
                    reduceMotion = true, backgroundImageUri = Uri.fromFile(photo).toString())) }
                launch(); shot("background-${style.name.lowercase()}")
            }
            }
            store.update { it.copy(onboardingDone = true, motion = false, appearance = AppearancePreset.ICE.appearance().copy(reduceMotion = true)) }
            launch()
            tap("Settings")
            assertTrue(device.wait(Until.hasObject(By.text("Target weight")), 10_000))
            tap("Appearance")
            assertTrue(device.wait(Until.hasObject(By.text("APP ICON")), 10_000))
            shot("appearance-presets")
            tap("Background color")
            assertTrue(device.wait(Until.hasObject(By.text("Hex color")), 5_000))
            device.findObject(By.clazz("android.widget.EditText")).text = "#102030"
            shot("custom-background-picker")
            tap("Apply")
            waitForSaved { it.appearance?.backgroundBase == 0xFF102030.toInt() }
            tap("Accent color")
            device.findObject(By.clazz("android.widget.EditText")).text = "#59E1C5"
            tap("Apply")
            waitForSaved { it.appearance?.accent == 0xFF59E1C5.toInt() }
            tap("Progress"); shot("app-custom-colors")
            store.update { it.copy(appearance = null, theme = ThemeStyle.LIGHT) }
            launch(); shot("app-legacy-light")
            store.update { it.copy(appearance = AppearancePreset.ICE.appearance().copy(reduceTransparency = true, reduceMotion = true)) }
            launch(); shot("app-reduced-transparency")
            assertTrue(store.settings.first().appearance!!.reduceTransparency)
            assertFalse(store.settings.first().motion)
        } catch (error: Throwable) {
            shot("failed-current-interaction")
            device.dumpWindowHierarchy(File(out, "failed-hierarchy.xml"))
            throw error
        } finally { store.update { original }; store.saveWeights(originalWeights); photo.delete() }
    }

    private suspend fun waitForSaved(predicate: (AppSettings) -> Boolean) {
        kotlinx.coroutines.withTimeout(10_000) { store.settings.first(predicate) }
    }
    private fun launch() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        assertTrue(device.wait(Until.hasObject(By.text("History")), 15_000))
        Thread.sleep(600)
    }
    private fun shot(name: String) { device.waitForIdle(1_000); Thread.sleep(350); device.takeScreenshot(File(out, "$name.png")) }
    private fun scrollPage(forward: Boolean = true) {
        // Prefer UiAutomator's scroll of the page itself: it drags within the page's own bounds.
        val page = device.findObject(By.scrollable(true))
        if (page != null && page.scroll(if (forward) androidx.test.uiautomator.Direction.DOWN else androidx.test.uiautomator.Direction.UP, 0.4f) ) {
            device.waitForIdle(1_000); Thread.sleep(300); return
        }
        // Slow, short overlapping drags down the middle of the page: a quick flick at the edge can be
        // taken by gesture navigation, and these stay clear of the floating dock.
        val x = device.displayWidth / 2
        val lower = device.displayHeight * 7 / 10
        val upper = device.displayHeight * 4 / 10
        device.swipe(x, if (forward) lower else upper, x, if (forward) upper else lower, 60)
        device.waitForIdle(1_000)
        Thread.sleep(300)
    }

    private fun visibleTarget(text: String): androidx.test.uiautomator.UiObject2? {
        instrumentation.uiAutomation.clearCache()
        val candidates = device.findObjects(By.text(text))
        if (text in listOf("Progress", "History", "Settings"))
            return candidates.lastOrNull { it.visibleBounds.centerY() > device.displayHeight * 0.82f }
        return candidates.firstOrNull {
            val b = it.visibleBounds
            b.height() > 12 && b.centerY() > 100 && b.centerY() < device.displayHeight * 0.82f
        }
    }

    private fun tap(text: String) {
        if (text == "Progress" || text == "History") {
            instrumentation.uiAutomation.clearCache()
            if (device.findObjects(By.text(text)).none { it.visibleBounds.centerY() > device.displayHeight * 0.8f }) {
                val edgeX = device.displayWidth / 12
                device.swipe(edgeX, device.displayHeight * 40 / 100, edgeX, device.displayHeight * 60 / 100, 65)
                device.waitForIdle(1000); Thread.sleep(400)
            }
        }
        if (text == "Settings") { device.wait(Until.findObject(By.desc("Settings")), 10000)!!.click(); device.waitForIdle(1000); Thread.sleep(300); return }
        for (attempt in 0 until 12) {
            if (visibleTarget(text) != null) break
            scrollPage()
        }
        if (visibleTarget(text) == null) for (attempt in 0 until 16) {
            if (visibleTarget(text) != null) break
            scrollPage(false)
        }
        val target = visibleTarget(text) ?: throw AssertionError("Missing $text")
        var clickable = target
        while (!clickable.isClickable && clickable.parent != null) clickable = clickable.parent
        (if (clickable.isClickable) clickable else target).click()
        device.waitForIdle(1_000)
        Thread.sleep(300)
    }
}
