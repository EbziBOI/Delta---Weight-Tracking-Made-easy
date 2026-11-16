package io.github.ebziboi.delta

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.ebziboi.delta.ui.AppViewModel
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
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class MeasurementDetailsTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val context=inst.targetContext
    private val device=UiDevice.getInstance(inst)
    private val store=SettingsStore(context)
    private var fixtureWeights:List<WeightEntry> = emptyList()
    private var fixtureFat:List<BodyFatEntry> = emptyList()
    private val out=File(context.filesDir,"measurement-details").apply {mkdirs()}
    init {Configurator.getInstance().setWaitForIdleTimeout(700)}

    @Test fun reportsKeepMissingDaysEmptyAndDoNotUseFutureRecords() {
        val today=LocalDate.of(2026,11,15);val zone=ZoneOffset.UTC;val now=today.atTime(23,59).toInstant(zone)
        fun e(ago:Long,value:Double,hour:Int=8,id:String="$ago-$hour")=WeightEntry(today.minusDays(ago).atTime(hour,0).toInstant(zone).toEpochMilli(),value,false,id)
        val raw=listOf(e(6,100.0),e(1,90.0),e(0,80.0),e(0,82.0,16),e(0,80.0,8,"duplicate"),e(-1,400.0),e(0,Double.NaN,11),e(8,110.0))
        val readings=MeasurementDetails.readings(raw,emptyList(),MeasurementKind.WEIGHT,now)
        val r=MeasurementDetails.report(MeasurementKind.WEIGHT,readings,today.minusDays(6),today,zone)
        assertEquals(4,r.readings.size);assertEquals(3,r.daily.size)
        assertEquals((100.0+90.0+81.0)/3,r.average!!,.00001)
        assertEquals(110.0,r.previousAverage!!,.00001)
        assertEquals(-19.0,r.change!!,.00001);assertEquals(7L,r.coverageDays)
        assertFalse(r.daily.containsKey(today.minusDays(2)))
        assertEquals(2,r.longestStreak);assertEquals(4L,r.largestGap)
        val old=MeasurementDetails.report(MeasurementKind.WEIGHT,readings,today.minusDays(7),today.minusDays(1),zone)
        assertFalse(old.readings.any {Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate()==today})
        assertEquals(-10.0,old.change!!,.00001)
    }
    @Test fun sparsePaceAndBodyFatRemainIndependentFromWeight() {
        val today=LocalDate.of(2026,11,15);val zone=ZoneOffset.UTC
        val values=listOf(0L,3L,7L,12L).map {MeasurementReading(today.minusDays(it).atTime(8,0).toInstant(zone).toEpochMilli(),80.0+it*.2)}
        val report=MeasurementDetails.report(MeasurementKind.WEIGHT,values.sortedBy {it.epochMs},today.minusDays(29),today,zone)
        assertEquals(-1.4,report.weeklyRate!!,.00001)
        assertNull(MeasurementDetails.report(MeasurementKind.WEIGHT,values.take(2),today.minusDays(6),today,zone).weeklyRate)
        val fat=listOf(BodyFatEntry(values[0].epochMs,25.0,false,"a"),BodyFatEntry(values[0].epochMs,25.0,false,"b"),BodyFatEntry(values[1].epochMs,0.0,false,"c"))
        val measured=MeasurementDetails.readings(emptyList(),fat,MeasurementKind.BODY_FAT,today.atTime(23,59).toInstant(zone))
        assertEquals(1,measured.size);assertEquals(25.0,measured.single().value,0.0)
        val r=MeasurementDetails.report(MeasurementKind.BODY_FAT,measured,today.minusDays(6),today,zone)
        assertNull(r.change);assertNull(r.weeklyRate);assertEquals(25.0,r.average!!,0.0)
    }
    @Test fun everyWeekStartAnchorsExactlyOneSevenDayWindowAndPersists() = runBlocking {
        val date=LocalDate.of(2026,11,15)
        for(index in 0..6) {
            val start=MetricDetails.weekStart(date,index)
            assertTrue(date in start..start.plusDays(6))
            assertEquals(index,start.dayOfWeek.value%7)
        }
        val saved=store.settings.first()
        try {store.update {it.copy(weekStartsOn=4)};assertEquals(4,SettingsStore(context).settings.first().weekStartsOn)} finally {store.update {saved}}
    }
    @Test fun calendarsShowExactDateReadingsAndInsightsExpandInBothThemes() = runBlocking {
        val saved=store.settings.first();val oldWeight=store.weights.first();val oldFat=store.bodyFat.first()
        val today=LocalDate.now();val zone=ZoneId.systemDefault();val recorded=today.minusDays(1);val fatOnly=today.minusDays(2)
        try {for(style in DesignStyle.entries) {
            seed(style);launch()
            show(By.desc("Open weight calendar")).click();waitText("Previous month")
            assertFalse(show(text("Next month")).isEnabled)
            assertTrue(device.hasObject(By.textContains("Weight 80.0")))
            assertTrue(device.hasObject(By.textContains("Weight 82.0")))
            assertTrue(device.hasObject(By.textContains("Body fat 25.0")))
            shot("$style-before-day-change");show(By.desc("$fatOnly, 0 weight readings, 1 body fat readings")).click();device.waitForIdle(700)
            show(By.textContains("No weight reading"))
            shot("$style-after-day-change");show(By.textContains("Body fat 26.5"))
            shot("$style-weight-calendar")
            device.pressBack();show(By.desc("Open body composition calendar")).click();waitText("Previous month")
            assertTrue(device.hasObject(By.textContains("Body fat 25.0")))
            show(By.desc("$today, 0 weight readings, 0 body fat readings")).click();Thread.sleep(250)
            show(By.textContains("No body fat reading"))
            shot("$style-body-calendar-empty")
            device.pressBack();show(text("All insights")).click();waitText("Open calendar")
            show(By.desc("Insight Daily average")).click();Thread.sleep(250)
            show(By.textContains("daily means total"));shot("$style-expanded-insight")
            show(text("Body fat")).click();device.waitForIdle(700)
            show(By.textContains("3 measurements"))
            show(By.desc("Insight Daily average")).click();device.waitForIdle(700)
            show(By.textContains("percentage points, divided by 3"))
            shot("$style-body-fat-insights")
            show(text("Open calendar")).click();waitText("Previous month");device.pressBack();waitText("Open calendar")
            show(By.textContains("3 measurements"))
            device.pressBack()
        }} catch(t:Throwable) {shot("failure");device.dumpWindowHierarchy(File(out,"failure.xml"));throw t} finally {store.update {saved};store.saveWeights(oldWeight);store.saveBodyFat(oldFat)}
        Unit
    }
    @Test fun weekStartBubblesShareOneCalendarAndStaySelectedAcrossMetricTabs() = runBlocking {
        val saved=store.settings.first();val oldWeight=store.weights.first();val oldFat=store.bodyFat.first()
        try {for(style in DesignStyle.entries) {
            seed(style);launch();show(text("Weekly rate")).click();waitText("Week starts")
            val thursday=show(By.desc("Start week on Thursday"));shot("$style-before-week-start");println("Thursday target ${thursday.visibleBounds}");thursday.click();device.waitForIdle(700);shot("$style-after-week-start")
            kotlinx.coroutines.withTimeout(7000) {store.settings.first {it.weekStartsOn==4}}
            assertTrue(show(By.desc("Start week on Thursday")).parent.isChecked)
            assertFalse(device.hasObject(text("This week")))
            shot("$style-week-start")
            show(text("Average")).click();Thread.sleep(300)
            assertTrue(show(By.desc("Start week on Thursday")).parent.isChecked)
            show(text("Streak")).click();Thread.sleep(300)
            assertTrue(show(By.desc("Start week on Thursday")).parent.isChecked)
            device.pressBack()
        }} catch(t:Throwable) {shot("failure");device.dumpWindowHierarchy(File(out,"failure.xml"));throw t} finally {store.update {saved};store.saveWeights(oldWeight);store.saveBodyFat(oldFat)}
        Unit
    }
    private suspend fun seed(style:DesignStyle) {
        val today=LocalDate.now();val zone=ZoneId.systemDefault()
        store.update {it.copy(onboardingDone=true,motion=false,weekStartsOn=0,startEpochDay=today.minusDays(40).toEpochDay(),targetKg=75.0,appearance=style.defaults().copy(reduceMotion=true))}
        val weights=listOf(28L,14L,7L,4L,1L).map {days->WeightEntry(today.minusDays(days).atTime(8,0).atZone(zone).toInstant().toEpochMilli(),if(days==1L) 80.0 else 80.0+days*.1,false,"w-$days")}
        fixtureWeights=weights+WeightEntry(today.minusDays(1).atTime(16,0).atZone(zone).toInstant().toEpochMilli(),82.0,false,"w-late")
        fixtureFat=listOf(14L to 28.0,2L to 26.5,1L to 25.0).map {(days,value)->BodyFatEntry(today.minusDays(days).atTime(9,0).atZone(zone).toInstant().toEpochMilli(),value,false,"f-$days")}
        store.saveWeights(fixtureWeights);store.saveBodyFat(fixtureFat)
    }
    private fun text(value:String)=By.text(Pattern.compile(Pattern.quote(value),Pattern.CASE_INSENSITIVE))
    private fun waitText(value:String) {assertTrue(device.wait(Until.hasObject(text(value)),5000));device.waitForIdle(700)}
    private suspend fun launch() {
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        val end=System.currentTimeMillis()+20000
        var ready=false
        while(System.currentTimeMillis()<end) {
            device.findObject(By.text("Wait"))?.click()
            if(device.wait(Until.hasObject(By.desc("Settings")),1000)) {ready=true;break}
        }
        assertTrue("Main screen did not open",ready)
        var model:AppViewModel?=null
        kotlinx.coroutines.withTimeout(15000) {
            while(model==null) {
                inst.runOnMainSync {ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()?.let {model=ViewModelProvider(it)[AppViewModel::class.java]}}
                if(model==null) kotlinx.coroutines.delay(50)
            }
            model!!.health.first {it!=null}
            model!!.syncing.first {!it}
        }
        // Reapply the cache fixture after the normal on-resume Health Connect refresh completes.
        store.saveWeights(fixtureWeights);store.saveBodyFat(fixtureFat)
        kotlinx.coroutines.withTimeout(15000) {
            model!!.entries.first {it.size==fixtureWeights.size && it.toSet()==fixtureWeights.toSet()}
            model!!.bodyFat.first {it.size==fixtureFat.size && it.toSet()==fixtureFat.toSet()}
        }
        device.waitForIdle(700);Thread.sleep(300);inst.uiAutomation.clearCache()
    }

    private fun show(selector:BySelector):UiObject2 {
        for(forward in listOf(true,false)) for(i in 0..22) {
            inst.uiAutomation.clearCache()
            device.findObjects(selector).firstOrNull {node->runCatching {node.visibleBounds.let {b->b.height()>10 && b.top>65 && b.bottom<device.displayHeight*.97}}.getOrDefault(false)}?.let {return it}
            val x=device.displayWidth/12;device.swipe(x,device.displayHeight*(if(forward) 65 else 40)/100,x,device.displayHeight*(if(forward) 40 else 65)/100,60)
            device.waitForIdle(700)
        }
        shot("missing");throw AssertionError("Missing $selector")
    }
    private fun shot(name:String) {device.waitForIdle(700);device.takeScreenshot(File(out,"$name.png"))}
}
