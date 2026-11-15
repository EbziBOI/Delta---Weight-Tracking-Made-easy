package io.github.ebziboi.delta

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.render.*
import io.github.ebziboi.delta.widget.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class WidgetConfigurationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val manager = AppWidgetManager.getInstance(context)
    private val store = WidgetStyleStore(context)
    private val out = File(context.filesDir, "widget-configuration").apply { mkdirs() }
    init { Configurator.getInstance().setWaitForIdleTimeout(1_000) }

    @Test fun optionsSurviveStorageAndRestoringWidgetIds() = runBlocking {
        val a = 19_000_001; val b = 19_000_002
        val oldA = store.style(a).first(); val oldB = store.style(b).first()
        val custom = WidgetStyle(false, WidgetGlass.CLEAR, WidgetTheme.LIGHT, 0.38f, 0.74f,
            31f, 0xFF102030.toInt(), 0xFF59E1C5.toInt())
        val second = custom.copy(glass = WidgetGlass.SOLID, accent = 0xFFFB7185.toInt())
        try {
            val inherited = custom.copy(theme = WidgetTheme.APP).apply(AppSettings(theme = ThemeStyle.AUTO), AppAppearance())
            assertTrue("Per-widget edits must preserve the app's system mode", Palettes.widgetFollowsSystem(inherited))
            store.save(a, custom); store.save(b, second)
            assertEquals(custom, WidgetStyleStore(context).style(a).first())
            store.restore(intArrayOf(a, b), intArrayOf(b, a))
            assertEquals(custom, store.style(b).first()); assertEquals(second, store.style(a).first())
            store.delete(intArrayOf(a, b))
            assertNull(store.style(a).first()); assertNull(store.style(b).first())
        } finally {
            if (oldA != null) store.save(a, oldA) else store.delete(intArrayOf(a))
            if (oldB != null) store.save(b, oldB) else store.delete(intArrayOf(b))
        }
    }

    @Test fun launcherSettingsHasLivePreviewAndOnlySavesThisWidget() = runBlocking {
        for (kind in WidgetKind.entries) {
            val info = manager.installedProviders.first { it.provider == ComponentName(context, WidgetCatalog.receiver(kind)) }
            assertEquals(WidgetConfigurationActivity::class.java.name, info.configure.className)
            assertTrue(info.widgetFeatures and android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0)
        }
        val id = placedProgressWidget()
        val original = store.style(id).first()
        val settings = SettingsStore(context)
        val originalApp = settings.settings.first()
        val app = originalApp.copy(appearance = DesignStyle.GLASS.defaults().copy(reduceMotion = true), widgetGlass = WidgetGlass.FROSTED)
        val untouchedId = manager.installedProviders.filter { it.provider.packageName == context.packageName }
            .flatMap { manager.getAppWidgetIds(it.provider).toList() }.firstOrNull { it != id }
        val untouched = untouchedId?.let { store.style(it).first() }
        try {
            settings.update { app }
            store.save(id, WidgetStyle.from(app))
            open(id)
            assertTrue(device.wait(Until.hasObject(By.text("Frosted")), 5_000))
            val before = preview("before")
            tap("Solid")
            val after = preview("solid-preview")
            assertFalse("Live preview must reflect the unsaved material", before.sameAs(after))
            before.recycle(); after.recycle()
            assertEquals("Preview must not save changes", app.widgetGlass, store.style(id).first()!!.glass)
            tap("Save")
            assertTrue("Save should return to the launcher", device.wait(Until.gone(By.text("Widget settings")), 20_000))
            kotlinx.coroutines.withTimeout(15_000) { store.style(id).first { it?.glass == WidgetGlass.SOLID && !it.useAppSettings } }
            assertEquals(app.appearance, SettingsStore(context).settings.first().appearance)
            assertEquals(app.widgetGlass, SettingsStore(context).settings.first().widgetGlass)
            if (untouchedId != null) assertEquals(untouched, store.style(untouchedId).first())
            open(id)
            tap("Clear")
            preview("cancelled-preview").recycle()
            tap("Cancel")
            assertEquals("Cancel must preserve the last saved style", WidgetGlass.SOLID, store.style(id).first()!!.glass)
        } finally {
            if (original == null) store.delete(intArrayOf(id)) else store.save(id, original)
            settings.update { originalApp }
            WidgetUpdater.updateOne(context, id, WidgetKind.PROGRESS)
            device.pressHome()
        }
    }
    /** The editor keeps unsaved changes in its saved state, so leaving it for another app must not crash. */
    @Test fun editorSurvivesGoingToTheBackground() = runBlocking {
        val component = ComponentName(context, WidgetCatalog.receiver(WidgetKind.PROGRESS))
        val id = manager.getAppWidgetIds(component).firstOrNull() ?: return@runBlocking
        val settings = SettingsStore(context)
        val originalApp = settings.settings.first()
        val originalStyle = store.style(id).first()
        val glass = originalApp.copy(appearance = DesignStyle.GLASS.defaults().copy(reduceMotion = true))
        try {
            settings.update { glass }
            store.save(id, WidgetStyle.from(glass))
            open(id)
            tap("Clear")
            device.pressHome()
            Thread.sleep(2_500)
            device.pressRecentApps()
            Thread.sleep(1_000)
            device.pressHome()
            assertTrue("The app process must still be alive", (context.applicationContext as DeltaApp).repo != null)
        } finally {
            if (originalStyle == null) store.delete(intArrayOf(id)) else store.save(id, originalStyle)
            settings.update { originalApp }
        }
    }

    @Test fun tubikWidgetEditorUsesMatteModules() = runBlocking {
        val settings = SettingsStore(context)
        val original = settings.settings.first()
        val id = placedProgressWidget()
        try {
            settings.update { it.copy(onboardingDone = true, motion = false, appearance = DesignStyle.TUBIK.defaults()) }
            open(id)
            assertTrue(device.wait(Until.hasObject(By.text("DASHBOARD MODULES")), 10_000))
            assertFalse("Matte widgets should not offer ineffective glass controls", device.hasObject(By.text("Frosted")))
            preview("dashboard-widget-editor").recycle()
            tap("Cancel")
        } finally { settings.update { original } }
    }

    @Test fun graphRangePreviewsThenSavesOnlyThisWidget() = runBlocking {
        val settings = SettingsStore(context)
        val originalSettings = settings.settings.first()
        val originalWeights = settings.weights.first()
        val id = placedProgressWidget(WidgetKind.TREND)
        val originalStyle = store.style(id).first()
        try {
            settings.saveWeights(SampleData.entries(java.time.LocalDate.now().minusDays(74)))
            store.save(id, WidgetStyle.from(originalSettings))
            open(id)
            instrumentation.uiAutomation.clearCache()
            assertTrue(device.wait(Until.hasObject(By.text("1 week")), 5000))
            val before = preview("range-journey")
            tap("1 week")
            val after = preview("range-week")
            assertFalse("Range must change the preview before saving", before.sameAs(after))
            before.recycle(); after.recycle()
            assertEquals(io.github.ebziboi.delta.data.WidgetTimeRange.JOURNEY, store.style(id).first()!!.timeRange)
            tap("Save")
            kotlinx.coroutines.withTimeout(15000) { store.style(id).first { it?.timeRange == io.github.ebziboi.delta.data.WidgetTimeRange.WEEK } }
            open(id)
            assertEquals(io.github.ebziboi.delta.data.WidgetTimeRange.WEEK, store.style(id).first()!!.timeRange)
            tap("3 months"); tap("Cancel")
            assertEquals(io.github.ebziboi.delta.data.WidgetTimeRange.WEEK, store.style(id).first()!!.timeRange)
            assertEquals(originalSettings.copy(lastSyncMs = 0), settings.settings.first().copy(lastSyncMs = 0))
        } finally {
            if (originalStyle == null) store.delete(intArrayOf(id)) else store.save(id, originalStyle)
            settings.saveWeights(originalWeights)
        }
    }

    private fun placedProgressWidget(kind: WidgetKind = WidgetKind.PROGRESS): Int {
        val component = ComponentName(context, WidgetCatalog.receiver(kind))
        if (manager.getAppWidgetIds(component).isEmpty()) {
            assertTrue(manager.requestPinAppWidget(component, null, null))
            val accept = device.wait(Until.findObject(By.text(Pattern.compile("(?i)add to home ?screen|add automatically|add")).clickable(true)), 10_000)
                ?: device.findObjects(By.text(Pattern.compile("(?i)add to home ?screen|add automatically|add"))).lastOrNull()
                ?: throw AssertionError("Launcher didn't offer to add a widget")
            accept.click()
            repeat(30) { if (manager.getAppWidgetIds(component).isEmpty()) Thread.sleep(500) }
        }
        return manager.getAppWidgetIds(component).first()
    }

    private fun open(id: Int) {
        context.startActivity(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .setComponent(manager.getAppWidgetInfo(id).configure)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertTrue(device.wait(Until.hasObject(By.text("Widget settings")), 10_000))
        assertTrue(device.wait(Until.hasObject(By.desc("Live widget preview")), 10_000))
        Thread.sleep(600)
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
        var node = device.wait(Until.findObject(By.text(text)), 10_000) ?: throw AssertionError("Missing $text")
        while (!node.isClickable && node.parent != null) node = node.parent
        node.click(); device.waitForIdle(1_000); Thread.sleep(600)
    }
    private fun preview(name: String): Bitmap {
        Thread.sleep(750)
        val bounds = device.findObject(By.desc("Live widget preview")).visibleBounds
        val file = File(out, "$name.png")
        device.takeScreenshot(file)
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        return Bitmap.createBitmap(bitmap, bounds.left, bounds.top, bounds.width(), bounds.height()).also { bitmap.recycle() }
    }
}
