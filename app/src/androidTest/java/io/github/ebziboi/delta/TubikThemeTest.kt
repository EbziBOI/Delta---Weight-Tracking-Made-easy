package io.github.ebziboi.delta

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.render.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TubikThemeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val store = SettingsStore(context)
    private val out = File(context.filesDir, "tubik-review").apply { mkdirs() }
    init { Configurator.getInstance().setWaitForIdleTimeout(1_000) }

    @Test fun designsRememberIndependentPalettesAcrossStorage() = runBlocking {
        val original = store.settings.first()
        val glass = AppearancePreset.ICE.appearance().copy(glassBlurRadius = 35f, cornerRadius = 31f, reduceMotion = true)
        try {
            val initial = original.copy(appearance = glass, savedGlassAppearance = null, savedTubikAppearance = null)
            store.update { initial.withDesignStyle(DesignStyle.TUBIK, glass) }
            val tube = store.settings.first()
            assertEquals(glass, tube.savedGlassAppearance)
            assertEquals(DesignStyle.TUBIK, tube.appearance!!.designStyle)
            val edited = tube.appearance!!.copy(accent = 0xFF89D8BD.toInt())
            store.update { it.copy(appearance = edited).withDesignStyle(DesignStyle.GLASS, edited) }
            val restored = SettingsStore(context).settings.first()
            assertEquals(glass, restored.appearance)
            assertEquals(edited, restored.savedTubikAppearance)
            store.update { it.withDesignStyle(DesignStyle.TUBIK, glass) }
            assertEquals(edited, store.settings.first().appearance)
            assertEquals(original.targetKg, restored.targetKg)
            assertEquals(original.unit, restored.unit)
            assertEquals(original.widgetChart, restored.widgetChart)
            val legacy = original.copy(appearance = null, theme = ThemeStyle.AUTO)
            val legacyResolved = AppearanceEngine.resolve(context, legacy)
            val switched = legacy.withDesignStyle(DesignStyle.TUBIK, legacyResolved)
            val back = switched.withDesignStyle(DesignStyle.GLASS, switched.appearance!!)
            assertNull("Auto/Material You should keep following the system", back.appearance)
            assertEquals(ThemeStyle.AUTO, back.theme)
        } finally { store.update { original } }
    }

    @Test fun allWidgetsHaveTheirOwnReadableDashboardTreatment() {
        val day = LocalDate.now().minusDays(74)
        val glass = AppSettings(onboardingDone = true, targetKg = 80.0, startEpochDay = day.toEpochDay(), appearance = AppearancePreset.ICE.appearance())
        val tube = glass.copy(appearance = DesignStyle.TUBIK.defaults())
        val snap = ProgressCalculator.compute(SampleData.entries(day), glass)
        for (kind in WidgetKind.entries) {
            val a = WidgetBitmaps.render(context, kind, 350f, 190f, snap, glass, maxSidePx = 700f)
            val b = WidgetBitmaps.render(context, kind, 350f, 190f, snap, tube.copy(widgetGlass = WidgetGlass.CLEAR), maxSidePx = 700f)
            assertFalse("${kind.title} must be a distinct visual treatment", a.sameAs(b))
            assertEquals(0, b.getPixel(0, 0) ushr 24)
            assertEquals(255, b.getPixel(b.width / 2, b.height - 5) ushr 24)
            SampleData.save(b, File(out, "widget-${kind.name.lowercase()}.png"))
            for (mode in WidgetTheme.entries) for (night in listOf(false, true)) {
                val theme = AppearanceEngine.forWidget(context, tube.copy(widgetTheme = mode), night, kind)
                assertTrue(ThemeContrast.ratio(theme.textPrimary, theme.background) >= 6.99)
                assertTrue(ThemeContrast.ratio(theme.onAccent, theme.accent) >= 4.5)
            }
            a.recycle(); b.recycle()
        }
    }

    @Test fun selectDashboardAndReturnToGlassWithoutLosingCustomization() = runBlocking {
        val original = store.settings.first()
        val weights = store.weights.first()
        val photo = File.createTempFile("appearance-bg-style-switch-", ".png", context.filesDir)
        val bitmap = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888)
        SampleData.save(bitmap, photo); bitmap.recycle()
        val glass = AppearancePreset.ICE.appearance().copy(reduceMotion = true, backgroundImageUri = android.net.Uri.fromFile(photo).toString())
        try {
            store.saveWeights(SampleData.entries(LocalDate.now().minusDays(74)))
            store.update { it.copy(onboardingDone = true, targetKg = 80.0, motion = false, appearance = glass,
                savedGlassAppearance = null, savedTubikAppearance = null) }
            launch(); shot("glass-progress")
            tap("Settings"); tap("Appearance")
            choose("Tubik inspired design")
            kotlinx.coroutines.withTimeout(10_000) { store.settings.first { it.appearance?.designStyle == DesignStyle.TUBIK } }
            assertTrue("Switching designs must retain the glass background photo", photo.exists())
            shot("design-selector")
            tap("Progress"); shot("dashboard-progress")
            scroll(); shot("dashboard-modules")
            tap("History"); shot("dashboard-history")
            tap("Settings"); shot("dashboard-settings")
            if (!device.hasObject(By.desc("Glass design")) && !device.hasObject(By.text("APPEARANCE"))) tap("Appearance")
            choose("Glass design")
            kotlinx.coroutines.withTimeout(10_000) { store.settings.first { it.appearance?.designStyle == DesignStyle.GLASS } }
            assertEquals(glass, store.settings.first().appearance)
            tap("Progress"); shot("restored-glass")
        } catch (error: Throwable) { shot("failed"); throw error }
        finally { store.update { original }; store.saveWeights(weights); photo.delete() }
    }
    private fun launch() {
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertTrue(device.wait(Until.hasObject(By.text("History")), 15_000)); Thread.sleep(800)
    }
    private fun scroll(forward: Boolean = true) {
        val x = device.displayWidth * 7 / 100
        device.swipe(x, device.displayHeight * (if (forward) 65 else 43) / 100,
            x, device.displayHeight * (if (forward) 43 else 65) / 100, 90)
        device.waitForIdle(1000); Thread.sleep(650)
    }
    private fun tap(text: String) {
        if (text == "Settings") { device.wait(Until.findObject(By.desc("Settings")), 10000)!!.click(); device.waitForIdle(1000); Thread.sleep(300); return }
        instrumentation.uiAutomation.clearCache()
        fun target() = runCatching { device.findObjects(By.textStartsWith(text)).let { nodes ->
            if (text in listOf("Progress", "History", "Settings")) nodes.lastOrNull { it.visibleBounds.centerY() > device.displayHeight * 0.82f }
            else nodes.firstOrNull { it.visibleBounds.centerY() in 65..(device.displayHeight * 0.8f).toInt() }
        } }.getOrNull()
        for (i in 0..12) { instrumentation.uiAutomation.clearCache(); if (target() != null) break; scroll() }
        if (target() == null) for (i in 0..14) { if (target() != null) break; scroll(false) }
        var node = target() ?: throw AssertionError("Missing $text")
        while (!node.isClickable && node.parent != null) node = node.parent
        node.click(); device.waitForIdle(1_000); Thread.sleep(350)
    }
    private fun choose(description: String) {
        for (i in 0..16) {
            instrumentation.uiAutomation.clearCache()
            val node = device.findObject(By.desc(description))
            if (node != null && node.visibleBounds.centerY() in 100..(device.displayHeight * 0.8f).toInt()) {
                node.click(); device.waitForIdle(1_000); Thread.sleep(500); return
            }
            scroll(i < 8)
        }
        throw AssertionError("Missing $description")
    }
    private fun shot(name: String) { device.waitForIdle(1_000); Thread.sleep(500); device.takeScreenshot(File(out, "$name.png")) }
}
