package io.github.ebziboi.delta

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import io.github.ebziboi.delta.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.*

@RunWith(AndroidJUnit4::class)
class MetricDetailsTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val context=inst.targetContext
    private val device=UiDevice.getInstance(inst)
    private val store=SettingsStore(context)
    private val out=File(context.filesDir,"metric-details").apply { mkdirs() }
    init { Configurator.getInstance().setWaitForIdleTimeout(700) }

    @Test fun calendarDoesNotLeakFutureDataOrOverweightBusyDays() {
        val today=LocalDate.of(2026,11,15);val zone=ZoneOffset.UTC
        fun entry(day:Long,kg:Double,hour:Int=8)=WeightEntry(today.minusDays(day).atTime(hour,0).toInstant(zone).toEpochMilli(),kg,false,"$day-$hour")
        val data=listOf(entry(6,100.0),entry(1,90.0),entry(0,80.0),entry(0,82.0,16),entry(-1,400.0))
        val settings=AppSettings(targetKg=75.0,startEpochDay=today.minusDays(30).toEpochDay())
        val days=MetricDetails.days(data,settings,MetricDetails.monday(today),today,zone)
        val current=days.last();assertEquals((100.0+90.0+81.0)/3,current.averageKg!!,.0001)
        assertEquals(81.0,current.kg!!,.0001);assertEquals(2,current.readings)
        val yesterday=days[5]
        val changed=MetricDetails.days(data.map { if(it.epochMs>today.atStartOfDay(zone).toInstant().toEpochMilli()) it.copy(kg=600.0) else it },settings,MetricDetails.monday(today),today,zone)
        assertEquals(yesterday.rateKg,changed[5].rateKg)
        assertEquals(0,days[4].streak)
        val snapshot=ProgressCalculator.compute(data.filter { it.epochMs<today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() },settings,zone,today.atTime(23,59).toInstant(zone))
        assertEquals(current.averageKg!!,InsightsCalculator.compute(data.filter { it.epochMs<today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() },snapshot,zone,today.atTime(23,59).toInstant(zone)).average7Kg!!,.0001)
        assertEquals(today.plusDays(70),MetricDetails.project(90.0,80.0,-1.0,today))
        assertNull(MetricDetails.project(90.0,80.0,1.0,today))
        assertNull(MetricDetails.project(90.0,80.0,0.0,today))
    }

    @Test fun progressChoicesPersistIndependently() = runBlocking {
        val original=store.settings.first()
        try { for(display in ProgressDisplay.entries) for(bar in ProgressBarStyle.entries) {
            store.update { it.copy(progressDisplay=display,progressBarStyle=bar) }
            val saved=SettingsStore(context).settings.first()
            assertEquals(display,saved.progressDisplay);assertEquals(bar,saved.progressBarStyle)
            assertEquals(original.targetKg,saved.targetKg);assertEquals(original.percentageDesign,saved.percentageDesign)
        } } finally { store.update { original } }
    }

    @Test fun eachTileOpensDetailsAndCalendarsWorkInBothThemes() = runBlocking {
        val original=store.settings.first();val weights=store.weights.first()
        try {
            for(theme in DesignStyle.entries) {
                seed(theme)
                launch()
                tap("Weekly rate")
                assertTrue(device.wait(Until.hasObject(By.text(java.util.regex.Pattern.compile("Your rhythm",java.util.regex.Pattern.CASE_INSENSITIVE))),5000))
                assertFalse(show(By.text("Next week")).isEnabled)
                tap("Previous week");assertTrue(show(By.text("Next week")).isEnabled)
                shot("$theme-rate")
                val averageTab=show(By.text("Average"))
                averageTab.click();device.waitForIdle(700);Thread.sleep(350)
                shot("$theme-average")
                val pagerArea=show(By.text("A clearer daily signal")).visibleBounds
                device.swipe(device.displayWidth*8/10,pagerArea.centerY(),device.displayWidth*2/10,pagerArea.centerY(),45)
                device.waitForIdle(700);Thread.sleep(400)
                inst.uiAutomation.clearCache()
                assertTrue("Swiping must switch the metric page",device.wait(Until.hasObject(By.text("Consistency, one day at a time")),5000))
                shot("$theme-streak")
                device.pressBack();tap("Goal by")
                assertTrue(device.wait(Until.hasObject(By.text(java.util.regex.Pattern.compile("Your forecast",java.util.regex.Pattern.CASE_INSENSITIVE))),5000))
                show(By.desc("Weight graph"));shot("$theme-forecast")
                device.pressBack()
                tap("7-day average");assertTrue(device.wait(Until.hasObject(By.text("A clearer daily signal")),5000))
                device.pressBack()
                tap("Streak");assertTrue(device.wait(Until.hasObject(By.text("Consistency, one day at a time")),5000));device.pressBack()
            }
        } catch(t:Throwable) { shot("failure");throw t }
        finally { store.update { original };store.saveWeights(weights) }
        Unit
    }

    @Test fun zoomedGraphReleasesVerticalDragsAndDisablesEndNavigation() = runBlocking {
        val original=store.settings.first();val weights=store.weights.first()
        try {
            seed(DesignStyle.GLASS);launch()
            tap("+");Thread.sleep(400)
            val graph=show(By.desc("Weight graph"));val b=graph.visibleBounds
            assertTrue("Zoom in must apply before dragging: ${graphState()}",graphState().contains("2.0x"))
            val before=graphState()
            device.swipe(b.centerX(),b.centerY(),b.centerX()+45,b.centerY()-190,70)
            device.waitForIdle(700);inst.uiAutomation.clearCache()
            val after=device.findObject(By.desc("Weight graph"))!!.visibleBounds
            assertTrue("Vertical drag starting on a zoomed chart must scroll the page",after.top<b.top-60)
            assertEquals("Vertical page scrolling must not pan dates",before,graphState())
            tap("Inspect latest reading")
            assertFalse(show(By.text("Next")).isEnabled)
            assertTrue(show(By.text("Previous")).isEnabled)
            tap("Previous");assertTrue(show(By.text("Next")).isEnabled)
            assertFalse(device.hasObject(By.textContains("double-tap the graph")))
            shot("graph-scroll-and-bounds")
        } finally { store.update { original };store.saveWeights(weights) }
        Unit
    }

    @Test fun progressModesRenderInBothThemes() = runBlocking {
        val original=store.settings.first();val weights=store.weights.first()
        try { for(theme in DesignStyle.entries) {
            seed(theme)
            for(mode in ProgressDisplay.entries) {
                store.update { it.copy(progressDisplay=mode,percentageDesign=PercentageDesign.MOSAIC) }
                launch();shot("$theme-$mode")
                val art=device.hasObject(By.descStartsWith("Mosaic:"))
                assertEquals(mode in listOf(ProgressDisplay.COMPLICATION,ProgressDisplay.BOTH),art)
            }
        } } finally { store.update { original };store.saveWeights(weights) }
        Unit
    }

    private suspend fun seed(theme:DesignStyle) {
        store.update { it.copy(onboardingDone=true,motion=false,targetKg=80.0,startEpochDay=LocalDate.now().minusDays(74).toEpochDay(),appearance=theme.defaults().copy(reduceMotion=true)) }
        store.saveWeights(SampleData.entries(LocalDate.now().minusDays(74)))
    }
    private fun launch() {
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertTrue(device.wait(Until.hasObject(By.desc("Settings")),15000));device.waitForIdle(700);Thread.sleep(500)
    }
    private fun show(selector:BySelector):UiObject2 {
        for(forward in listOf(true,false)) for(i in 0..18) {
            inst.uiAutomation.clearCache()
            device.findObjects(selector).firstOrNull { it.visibleBounds.let { b -> b.height()>12 && b.top>105 && b.bottom<device.displayHeight*(if(device.hasObject(By.desc("Log weight"))) .82f else .94f) } }?.let { return it }
            val x=device.displayWidth/12
            device.swipe(x,device.displayHeight*(if(forward) 65 else 40)/100,x,device.displayHeight*(if(forward) 40 else 65)/100,60)
            device.waitForIdle(700)
        }
        shot("missing");throw AssertionError("Missing $selector")
    }
    private fun tap(text:String) { show(By.text(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(text),java.util.regex.Pattern.CASE_INSENSITIVE))).click();device.waitForIdle(700);Thread.sleep(300) }
    private fun shot(name:String) { device.waitForIdle(700);device.takeScreenshot(File(out,"$name.png")) }
    private fun graphState():String {
        inst.uiAutomation.clearCache()
        fun find(n:AccessibilityNodeInfo?):String? {
            n ?: return null
            if(n.contentDescription?.toString()=="Weight graph") return n.stateDescription?.toString()
            for(i in 0 until n.childCount) find(n.getChild(i))?.let { return it }
            return null
        }
        return find(inst.uiAutomation.rootInActiveWindow).orEmpty()
    }
}
