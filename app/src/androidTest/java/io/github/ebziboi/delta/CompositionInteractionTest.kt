package io.github.ebziboi.delta

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.icons.LauncherIcons
import io.github.ebziboi.delta.render.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class CompositionInteractionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val repo get() = (context.applicationContext as DeltaApp).repo
    private val store = SettingsStore(context)
    private val out = File(context.filesDir, "composition-review").apply { mkdirs() }
    init { Configurator.getInstance().setWaitForIdleTimeout(700) }

    @Test fun viewportKeepsAnchorClampsAndSelectsActualReadings() {
        val full = GraphViewport(0, 32000, 0.0, 100.0)
        val zoomed = full.transform(full, 2f, 0.25f, 0.75f)
        assertEquals(4000L, zoomed.first)
        assertEquals(0.0, zoomed.low, 1e-6)
        assertEquals(100.0, zoomed.high, 1e-6)
        assertEquals(2.0, zoomed.zoom(full), 1e-6)
        val limited = zoomed.transform(full, 1000f, 0.5f, 0.5f, 900f, -900f)
        assertEquals(0L, limited.first)
        assertEquals(0.0, limited.low, 1e-6)
        assertEquals(32.0, limited.zoom(full), 1e-6)
        assertEquals(full, limited.transform(full, 0.0001f, 0.5f, 0.5f))
        assertEquals(full, full.transform(full, Float.NaN, 0f, 0f))
        val a = TrendPoint(1, 90.0, 89.0); val b = TrendPoint(2, 91.0, 89.5)
        val layout = ChartLayout(RectF(0f, 0f, 100f, 100f), full, full,
            listOf(ChartHit(a, 20f, 30f), ChartHit(b, 80f, 70f)))
        assertEquals(b, layout.nearest(75f, 60f))
        assertNull(layout.nearest(-1f, 30f))
        assertNull(BodyFat.near(Instant.now().toEpochMilli(), listOf(BodyFatEntry(1, 24.0, false, "old"))))
    }

    @Test fun importsMeasuredPercentagesAndHandlesScaleZeros() {
        val csv = "Date,Weight (kg),Body fat %,Fat mass (kg)\n2026-11-01,91,24.5,22\n2026-11-02,90,0,0\n2026-11-03,89,,0"
        val readings = ScaleImport.parse(csv).people.getValue("")
        assertEquals(24.5, readings[0].bodyFatPercent!!, 1e-6)
        assertNull(readings[1].bodyFatPercent)
        assertNull(readings[2].bodyFatPercent)
        assertTrue(runCatching { ScaleImport.parse(csv.replace("24.5", "101")) }.exceptionOrNull() is ScaleImportException)
        assertTrue(runCatching { ScaleImport.parse(csv.replace("24.5", "NaN")) }.exceptionOrNull() is ScaleImportException)
        assertFalse(BodyFat.valid(Double.NaN)); assertFalse(BodyFat.valid(0.0))
        val differentFatTypes = "Date,Weight (kg),Subcutaneous fat %,Visceral fat rating,Body fat %,Fat mass (kg)\n2026-11-01,91,18.2,10,24.5,22"
        assertEquals(24.5, ScaleImport.parse(differentFatTypes).people.getValue("").single().bodyFatPercent!!, 1e-6)
    }

    @Test fun allTenIconsStayLaunchableAndPersistWithoutChangingMeasurements() = runBlocking {
        val original = store.settings.first()
        val weights = store.weights.first(); val body = store.bodyFat.first()
        try {
            assertEquals(10, LauncherIcons.all.size)
            for (icon in LauncherIcons.all) {
                LauncherIcons.apply(context, icon.id)
                val launchers = context.packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
                assertEquals("Exactly one launcher for ${icon.title}", 1, launchers.size)
                assertTrue(launchers.single().activityInfo.name.endsWith("Launcher${icon.id}"))
            }
            store.update { it.copy(appIcon = "Icon09", targetBodyFat = 21.5, typeStyle = TypeStyle.STUDIO) }
            assertEquals("Icon09", SettingsStore(context).settings.first().appIcon)
            assertEquals(21.5, SettingsStore(context).settings.first().targetBodyFat!!, 1e-9)
            assertEquals(weights, store.weights.first()); assertEquals(body, store.bodyFat.first())
            assertTrue(context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0).enabled)
        } finally { store.update { original }; LauncherIcons.apply(context, original.appIcon) }
    }

    @Test fun healthConnectCompositionAndActualGestures() = runBlocking {
        val original = store.settings.first()
        for (p in repo.health.state().missing) runCatching { instrumentation.uiAutomation.grantRuntimePermission(context.packageName, p) }
        check(repo.health.state().canReadBodyFat && repo.health.state().canWriteBodyFat)
        val marker = Instant.now().minusSeconds(3600).toEpochMilli()
        val times = (0..12).map { marker - (12L - it) * 86400000 }
        val extraTime = marker + 60_000
        try {
            for ((i, time) in times.withIndex()) repo.health.writeMeasurement(94.0 - i * 0.2, 26.0 - i * 0.1, Instant.ofEpochMilli(time))
            repo.health.writeBodyFat(24.7, Instant.ofEpochMilli(extraTime))
            assertTrue(repo.sync() is SyncResult.Done)
            assertEquals(13, store.weights.first().count { it.epochMs in times })
            assertEquals(14, store.bodyFat.first().count { it.epochMs in times || it.epochMs == extraTime })
            val extra = store.bodyFat.first().single { it.epochMs == extraTime }
            repo.deleteBodyFat(extra)
            assertTrue(repo.health.readWeights(Instant.ofEpochMilli(marker - 1)).any { it.epochMs == marker })
            assertFalse(repo.health.readBodyFat(Instant.ofEpochMilli(marker)).any { it.epochMs == extraTime })
            store.update { it.copy(onboardingDone = true, targetKg = 80.0, targetBodyFat = 22.0,
                startEpochDay = java.time.LocalDate.now().minusDays(30).toEpochDay(),
                appearance = AppearancePreset.ICE.appearance().copy(reduceMotion = true), motion = false, typeStyle = TypeStyle.STUDIO) }
            launch()
            show(By.text("Log body fat")); shot("body-composition")
            val loggedAfter = System.currentTimeMillis()
            tap("Log body fat")
            assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 5000))
            device.findObject(By.clazz("android.widget.EditText")).text = "23.8"
            println("Composition: saving a measured percentage")
            tap("Save")
            kotlinx.coroutines.withTimeout(12000) { store.bodyFat.first { rs -> rs.any { it.percent == 23.8 && it.epochMs >= loggedAfter } } }
            tap("History"); tap("Body fat"); shot("body-fat-history")
            instrumentation.uiAutomation.clearCache()
            assertTrue(device.wait(Until.hasObject(By.textContains("23.8%")), 5000))
            tap("Progress")
            println("Composition: checking graph gestures")
            val graph = show(By.desc("Weight graph"))
            graph.setGestureMargin(30)
            graph.click()
            assertTrue(device.wait(Until.hasObject(By.descStartsWith("Selected reading")), 5000))
            show(By.text("+")); tap("+")
            assertTrue("Zoom button must change viewport", graphState().contains("2.0x"))
            val before = graphState()
            val zoomGraph = show(By.desc("Weight graph")); zoomGraph.setGestureMargin(35); zoomGraph.pinchOpen(0.5f, 300)
            Thread.sleep(500)
            assertNotEquals("Pinch must change viewport", before, graphState())
            val dragGraph = show(By.desc("Weight graph")).visibleBounds
            val beforePan = graphState()
            device.swipe(dragGraph.centerX(), dragGraph.centerY(), dragGraph.centerX() + dragGraph.width() / 5, dragGraph.centerY(), 30)
            Thread.sleep(400)
            assertNotEquals("Dragging a zoomed graph must move its time window", beforePan, graphState())
            shot("zoomed-weight-selection")
            tap("Reset zoom"); assertTrue(graphState().contains("1.0x"))
            println("Composition: checking icon carousel")
            tap("Settings"); tap("Appearance")
            val carousel = show(By.desc("App icon carousel"))
            val b = carousel.visibleBounds
            device.swipe(b.right - 50, b.centerY(), b.left + 50, b.centerY(), 30)
            Thread.sleep(500)
            val newIcon = show(By.desc("Original progress icon"))
            newIcon.longClick()
            kotlinx.coroutines.withTimeout(10000) { store.settings.first { it.appIcon == "Icon01" } }
            shot("gesture-icon-carousel")
            assertTrue(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.component!!.className.endsWith("LauncherIcon01"))
        } catch (error: Throwable) { shot("failure"); device.dumpWindowHierarchy(File(out, "failure.xml")); throw error }
        finally {
            repo.health.readWeights(Instant.ofEpochMilli(times.first() - 1)).filter { it.own && it.epochMs in times }.forEach { repo.health.deleteWeight(it.id) }
            repo.health.readBodyFat(Instant.ofEpochMilli(times.first() - 1)).filter { it.own && (it.epochMs in times || it.epochMs == extraTime || (it.epochMs > marker && it.percent == 23.8)) }.forEach { repo.health.deleteBodyFat(it.id) }
            store.update { original }; LauncherIcons.apply(context, original.appIcon); repo.sync()
        }
    }

    @Test fun bodyFatWidgetSupportsAllThemesAndMaterials() {
        val now = Instant.now().toEpochMilli()
        val readings = (0..8).map { BodyFatEntry(now - (8L - it) * 86400000, 26.0 - it * 0.15, false, "sample-$it") }
        val base = AppSettings(targetBodyFat = 23.0, onboardingDone = true)
        val snapshot = ProgressCalculator.compute(emptyList(), base).copy(bodyFat = readings, targetBodyFat = 23.0)
        for (theme in ThemeStyle.entries) for (glass in WidgetGlass.entries) for (night in listOf(false, true)) {
            val settings = base.copy(theme = theme, widgetGlass = glass, widgetTheme = WidgetTheme.SYSTEM,
                widgetOpacityLight = glass.defaultLight, widgetOpacityDark = glass.defaultDark)
            val bitmap = WidgetBitmaps.render(context, WidgetKind.BODY_FAT, 350f, 180f, snapshot, settings, night, 700f)
            val empty = WidgetBitmaps.render(context, WidgetKind.BODY_FAT, 350f, 180f, snapshot.copy(bodyFat = emptyList()), settings, night, 700f)
            assertFalse("Body fat data must render in $theme / $glass / $night", bitmap.sameAs(empty))
            assertEquals(0, bitmap.getPixel(0, 0) ushr 24)
            if (theme == ThemeStyle.EMBER) SampleData.save(bitmap, File(out, "body-fat-${glass.name}-$night.png"))
            bitmap.recycle(); empty.recycle()
        }
    }

    private fun graphState(): String {
        instrumentation.uiAutomation.clearCache()
        fun find(node: AccessibilityNodeInfo?): String? {
            node ?: return null
            if (node.contentDescription?.toString() == "Weight graph") return node.stateDescription?.toString()
            for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
            return null
        }
        return find(instrumentation.uiAutomation.rootInActiveWindow).orEmpty()
    }
    private fun launch() {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertTrue(device.wait(Until.hasObject(By.text("History")), 15000)); Thread.sleep(1200)
    }
    private fun show(selector: BySelector): UiObject2 {
        fun target(): UiObject2? = try {
            instrumentation.uiAutomation.clearCache()
            device.findObjects(selector).firstOrNull { node ->
                try { node.visibleBounds.let { b -> b.height() > 4 && b.top > 65 && b.bottom < device.displayHeight * 0.83f } }
                catch (_: StaleObjectException) { false }
            }
        } catch (_: StaleObjectException) { null }
        fun scroll(forward: Boolean) {
            val x = device.displayWidth * 7 / 100
            device.swipe(x, device.displayHeight * (if (forward) 65 else 43) / 100,
                x, device.displayHeight * (if (forward) 43 else 65) / 100, 90)
            device.waitForIdle(1000); Thread.sleep(650)
        }
        for (forward in listOf(true, false)) for (i in 0..24) {
            target()?.let { return it }
            scroll(forward)
        }
        device.dumpWindowHierarchy(File(out, "missing.xml"))
        throw AssertionError("Missing or obscured $selector")
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
        instrumentation.uiAutomation.clearCache()
        var node = if (text in listOf("Settings", "Progress", "History")) device.findObjects(By.text(text)).last { it.visibleBounds.centerY() > device.displayHeight * 0.82f } else show(By.text(text))
        while (!node.isClickable && node.parent != null) node = node.parent
        node.click(); device.waitForIdle(800); Thread.sleep(300)
    }
    private fun shot(name: String) { device.waitForIdle(800); device.takeScreenshot(File(out, "$name.png")) }
}
