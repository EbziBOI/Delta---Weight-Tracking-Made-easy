package io.github.ebziboi.delta

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.ebziboi.delta.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Uses the app the way a real history looks: weigh-ins from more than one app,
 * including the same reading written twice within minutes. Every tab is opened
 * and scrolled, and the app must still be running at the end.
 */
@RunWith(AndroidJUnit4::class)
class RealDataTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val repo get() = (context.applicationContext as DeltaApp).repo
    private val out = File(context.filesDir, "real-data").apply { mkdirs() }

    @Test fun duplicateReadingsAndEveryTab() {
        val missing = runBlocking { repo.health.state() }.missing
        for (permission in missing) runCatching { instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission) }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        // Days ago, time, kg: the shape of a real history, duplicates included.
        val readings = listOf(
            9 to "10:58" to 95.3, 7 to "10:58" to 95.3, 6 to "13:42" to 95.4, 5 to "13:19" to 95.3, 5 to "13:19" to 95.3,
            3 to "20:58" to 95.2, 1 to "13:36" to 94.9, 1 to "13:39" to 94.9, 40 to "08:10" to 97.6, 20 to "08:05" to 96.1,
        )
        runBlocking {
            check(repo.health.state().canWrite) { "Health Connect access could not be granted" }
            for ((dayTime, kg) in readings) {
                val (daysAgo, time) = dayTime
                val at = today.minusDays(daysAgo.toLong()).atTime(LocalTime.parse(time)).atZone(zone).toInstant()
                repo.health.writeWeight(kg, at)
            }
            repo.sync()
            SettingsStore(context).update {
                it.copy(onboardingDone = true, targetKg = 80.0, startEpochDay = today.minusDays(45).toEpochDay(),
                    targetEpochDay = today.plusDays(120).toEpochDay())
            }
        }

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        assertTrue(device.wait(Until.hasObject(By.text("History")), 20_000))
        Thread.sleep(2_500)
        device.takeScreenshot(File(out, "1-progress.png"))
        repeat(4) { i -> swipeUp(); device.takeScreenshot(File(out, "2-progress-$i.png")) }
        for (tab in listOf("History", "Settings", "Progress")) {
            device.findObject(By.text(tab))?.click()
            Thread.sleep(1_500)
            device.takeScreenshot(File(out, "3-$tab.png"))
            repeat(3) { swipeUp() }
        }
        // A double tap on the graph switches its style, and the choice is saved.
        val before = runBlocking { SettingsStore(context).settings.first() }.appChart
        // Bring the whole graph into view, clear of the status bar and the floating tab bar (which
        // covers the bottom of the page), stepping a little at a time from the top of the page.
        // Reopen the app so Progress starts at the top, then step down to the graph.
        context.startActivity(intent)
        assertTrue(device.wait(Until.hasObject(By.text("History")), 20_000))
        Thread.sleep(2_000)
        fun clearGraph() = device.findObject(By.desc("Weight graph"))?.takeIf {
            val b = it.visibleBounds
            b.top > device.displayHeight / 10 && b.bottom < device.displayHeight * 85 / 100
        }
        var graph = clearGraph()
        for (step in 0 until 20) {
            if (graph != null) break
            val x = device.displayWidth / 2
            device.swipe(x, device.displayHeight * 62 / 100, x, device.displayHeight * 54 / 100, 140)
            device.waitForIdle(1_000)
            graph = clearGraph()
        }
        if (graph == null) {
            val seen = device.findObject(By.desc("Weight graph"))?.visibleBounds
            device.takeScreenshot(File(out, "failed-graph.png"))
            println("Graph bounds when giving up: $seen")
            device.dumpWindowHierarchy(File(out, "failed-graph.xml"))
            throw AssertionError("The graph never came into view (last seen at $seen)")
        }
        run {
            val centre = graph!!.visibleCenter
            doubleTap(centre.x.toFloat(), centre.y.toFloat())
            Thread.sleep(1_500)
            device.takeScreenshot(File(out, "4-graph-switched.png"))
            val after = runBlocking { SettingsStore(context).settings.first() }.appChart
            assertTrue("Double tap should switch the graph style ($before → $after)", after != before)
        }
        // Stone and pounds must fit the main weight on one line.
        runBlocking { SettingsStore(context).update { it.copy(unit = io.github.ebziboi.delta.data.WeightUnit.ST_LB) } }
        repeat(8) { swipeDown() }
        Thread.sleep(1_500)
        device.takeScreenshot(File(out, "5-stone-and-pounds.png"))
        runBlocking { SettingsStore(context).update { it.copy(unit = io.github.ebziboi.delta.data.WeightUnit.KG) } }
        Thread.sleep(2_000)
        assertEquals("Delta must still be in front", context.packageName, device.currentPackageName)
    }

    /** Two taps 80 ms apart, injected directly so nothing waits between them, as a finger would. */
    private fun doubleTap(x: Float, y: Float) {
        val automation = instrumentation.uiAutomation
        var time = android.os.SystemClock.uptimeMillis()
        repeat(2) {
            val down = android.view.MotionEvent.obtain(time, time, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
            val up = android.view.MotionEvent.obtain(time, time + 40, android.view.MotionEvent.ACTION_UP, x, y, 0)
            down.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            up.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            automation.injectInputEvent(down, true)
            automation.injectInputEvent(up, true)
            down.recycle(); up.recycle()
            time += 120
            Thread.sleep(80)
        }
    }

    private fun swipeUp(short: Boolean = false) {
        val x = device.displayWidth / 2
        val to = if (short) device.displayHeight * 5 / 10 else device.displayHeight * 3 / 10
        // A short step is a slow drag, so the page moves exactly that far instead of flinging past.
        device.swipe(x, device.displayHeight * 7 / 10, x, to, if (short) 120 else 40)
        device.waitForIdle(1_000)
    }

    private fun swipeDown(short: Boolean = false) {
        val x = device.displayWidth / 2
        val to = if (short) device.displayHeight * 5 / 10 else device.displayHeight * 7 / 10
        device.swipe(x, device.displayHeight * 3 / 10, x, to, if (short) 120 else 24)
        device.waitForIdle(1_000)
    }
}
