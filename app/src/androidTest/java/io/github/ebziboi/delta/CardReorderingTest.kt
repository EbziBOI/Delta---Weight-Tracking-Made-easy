package io.github.ebziboi.delta

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
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
class CardReorderingTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val context=inst.targetContext
    private val device=UiDevice.getInstance(inst)
    private val store=SettingsStore(context)
    private var fixtureWeights:List<WeightEntry> = emptyList()
    private var fixtureFat:List<BodyFatEntry> = emptyList()
    private val out=File(context.filesDir,"card-reordering").apply {mkdirs()}
    init {Configurator.getInstance().setWaitForIdleTimeout(700)}

    @Test fun layoutsRepairUnknownAndDuplicateIdsAndRetainHiddenCards() = runBlocking {
        assertEquals(listOf("graph","calendar","readings")+CardLayouts.measurement.drop(3),CardLayouts.normalized(listOf("graph","graph","retired"),CardLayouts.measurement))
        val hidden=CardLayouts.home.filterNot {it in listOf("rate","goal","average","streak")}
        val merged=CardLayouts.mergeVisible(CardLayouts.home,listOf("streak","rate","goal","average"),CardLayouts.home)
        assertEquals(hidden,merged.filterNot {it in listOf("rate","goal","average","streak")})
        assertEquals(listOf("streak","rate","goal","average"),merged.filter {it in listOf("rate","goal","average","streak")})
        val saved=store.settings.first()
        try {
            store.update {it.copy(homeCardOrder=merged,weightCardOrder=CardLayouts.measurement.reversed())}
            val loaded=SettingsStore(context).settings.first()
            assertEquals(merged,loaded.homeCardOrder)
            assertEquals(CardLayouts.measurement.reversed(),loaded.weightCardOrder)
            assertEquals(saved.bodyFatCardOrder,loaded.bodyFatCardOrder)
            assertEquals(saved.targetKg,loaded.targetKg)
        } finally {store.update {saved}}
    }
    @Test fun homeCardsDragIndividuallyAndSurviveRelaunchInBothThemes() = runBlocking {
        preserve {
            for(style in DesignStyle.entries) {
                seed(style);launch()
                show(By.desc("Weekly rate card"));show(By.desc("Goal by card"))
                val source=device.findObject(By.desc("Weekly rate card")).visibleBounds
                val target=device.findObject(By.desc("Goal by card")).visibleBounds
                drag(source.centerX(),source.top+38,target.centerX(),target.top+38)
                kotlinx.coroutines.withTimeout(7000) {store.settings.first {it.homeCardOrder.indexOf("goal")<it.homeCardOrder.indexOf("rate")}}
                assertFalse(device.hasObject(By.text("↗")))
                shot("$style-home-grid")
                launch();show(By.desc("Weekly rate card"));show(By.desc("Goal by card"))
                val rate=device.findObject(By.desc("Weekly rate card")).visibleBounds
                val goal=device.findObject(By.desc("Goal by card")).visibleBounds
                assertTrue("The saved grid order must be restored",goal.left<rate.left && kotlin.math.abs(goal.top-rate.top)<10)
            }
        }
    }
    @Test fun detailCardsAndIndividualInsightsHaveIndependentSavedLayouts() = runBlocking {
        preserve {
            for(style in DesignStyle.entries) {
                seed(style);launch();show(By.desc("Open weight calendar")).click();waitText("Previous month")
                show(By.desc("Selected measurements card"))
                val selected=device.findObject(By.desc("Selected measurements card")).visibleBounds
                val calendar=device.findObject(By.desc("Calendar card")).visibleBounds
                File(out,"stage.txt").writeText("$style weight readings")
                drag(selected.centerX(),selected.top+38,calendar.centerX(),maxOf(150,calendar.top+90))
                kotlinx.coroutines.withTimeout(7000) {store.settings.first {it.weightCardOrder.indexOf("readings")<it.weightCardOrder.indexOf("calendar")}}
                assertEquals(CardLayouts.measurement,store.settings.first().bodyFatCardOrder)
                show(By.desc("Insight Daily average")).click();device.waitForIdle(700)
                show(By.textContains("daily means total"))
                show(By.desc("Daily average card"))
                File(out,"stage.txt").writeText("$style weight insight")
                assertTrue(moveEarlier("Daily average card"))
                kotlinx.coroutines.withTimeout(7000) {store.settings.first {it.weightCardOrder.indexOf("insight-average")<it.weightCardOrder.indexOf("graph")}}
                show(By.textContains("daily means total"));shot("$style-weight-order")
                device.pressBack();show(By.desc("Open body composition calendar")).click();waitText("Previous month")
                show(By.desc("Selected measurements card"))
                val bodySelected=device.findObject(By.desc("Selected measurements card")).visibleBounds
                val bodyCalendar=device.findObject(By.desc("Calendar card")).visibleBounds
                File(out,"stage.txt").writeText("$style body readings")
                drag(bodySelected.centerX(),bodySelected.top+38,bodyCalendar.centerX(),maxOf(150,bodyCalendar.top+90))
                kotlinx.coroutines.withTimeout(7000) {store.settings.first {it.bodyFatCardOrder.indexOf("readings")<it.bodyFatCardOrder.indexOf("calendar")}}
                shot("$style-body-order")
            }
        }
    }
    @Test fun draggingAutoScrollsAndCalendarAndGraphTouchesDoNotReorder() = runBlocking {
        preserve {
            seed(DesignStyle.TUBIK);launch();show(By.desc("Weekly rate card"))
            val rate=device.findObject(By.desc("Weekly rate card")).visibleBounds
            drag(rate.centerX(),rate.top+38,rate.centerX(),(device.displayHeight*.90).toInt(),holdAtEnd=3500)
            kotlinx.coroutines.withTimeout(7000) {store.settings.first {it.homeCardOrder.indexOf("rate")>it.homeCardOrder.indexOf("trend")}}
            shot("auto-scroll-drop")
            show(By.desc("Open weight calendar")).click();waitText("Previous month")
            val before=store.settings.first().weightCardOrder
            val day=show(By.descStartsWith(LocalDate.now().minusDays(2).toString())).visibleBounds
            device.click(day.centerX(),day.centerY());device.waitForIdle(700)
            assertEquals(before,store.settings.first().weightCardOrder)
            show(By.desc("Zoom in")).click();device.waitForIdle(700)
            show(By.desc("Weight graph"))
            fun graphState():String? {
                fun state(node:AccessibilityNodeInfo?):String? {
                    node ?: return null
                    if(node.contentDescription?.toString()=="Weight graph") return node.stateDescription?.toString()
                    for(i in 0 until node.childCount) state(node.getChild(i))?.let {return it}
                    return null
                }
                inst.uiAutomation.clearCache();return state(inst.uiAutomation.rootInActiveWindow)
            }
            assertTrue(graphState()?.contains("2.0x")==true)
            show(By.desc("First and latest card"));show(By.desc("Weight graph"));device.waitForIdle(700)
            assertTrue("Zoom must survive scrolling a graph out of the layout: ${graphState()}",graphState()?.contains("2.0x")==true)
            val graph=device.findObject(By.desc("Weight graph")).visibleBounds
            val down=SystemClock.uptimeMillis();inject(down,MotionEvent.ACTION_DOWN,graph.centerX(),graph.centerY());Thread.sleep(700)
            inject(down,MotionEvent.ACTION_MOVE,graph.centerX(),graph.centerY()-150);inject(down,MotionEvent.ACTION_UP,graph.centerX(),graph.centerY()-150)
            device.waitForIdle(700)
            assertEquals(before,store.settings.first().weightCardOrder)
        }
    }
    private suspend fun preserve(block:suspend ()->Unit) {
        val saved=store.settings.first();val weights=store.weights.first();val fat=store.bodyFat.first()
        try {block()} catch(t:Throwable) {shot("failure");device.dumpWindowHierarchy(File(out,"failure.xml"));val failed=store.settings.first();File(out,"failure-orders.txt").writeText("home=${failed.homeCardOrder}\nweight=${failed.weightCardOrder}\nbody=${failed.bodyFatCardOrder}");throw t}
        finally {store.update {saved};store.saveWeights(weights);store.saveBodyFat(fat)}
    }
    private suspend fun seed(style:DesignStyle) {
        val today=LocalDate.now();val zone=ZoneId.systemDefault()
        store.update {it.copy(onboardingDone=true,motion=false,targetKg=75.0,startEpochDay=today.minusDays(74).toEpochDay(),
            appearance=style.defaults().copy(reduceMotion=true),homeCardOrder=CardLayouts.home,weightCardOrder=CardLayouts.measurement,bodyFatCardOrder=CardLayouts.measurement)}
        fixtureWeights=SampleData.entries(today.minusDays(74))
        fixtureFat=(1L..30L).map {BodyFatEntry(today.minusDays(it).atTime(8,0).atZone(zone).toInstant().toEpochMilli(),25.0+it*.04,false,"reorder-$it")}
        store.saveWeights(fixtureWeights);store.saveBodyFat(fixtureFat)
    }
    private fun moveEarlier(description:String):Boolean {
        inst.uiAutomation.clearCache()
        fun find(n:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
            n ?: return null
            if(n.contentDescription?.toString()==description && n.actionList.any {it.label?.toString()=="Move earlier"}) return n
            for(i in 0 until n.childCount) find(n.getChild(i))?.let {return it}
            return null
        }
        val node=find(inst.uiAutomation.rootInActiveWindow) ?: return false
        return node.performAction(node.actionList.first {it.label?.toString()=="Move earlier"}.id)
    }
    private fun drag(x:Int,y:Int,toX:Int,toY:Int,holdAtEnd:Long=0) {
        val down=SystemClock.uptimeMillis();inject(down,MotionEvent.ACTION_DOWN,x,y);Thread.sleep(700)
        for(step in 1..35) {inject(down,MotionEvent.ACTION_MOVE,x+(toX-x)*step/35,y+(toY-y)*step/35);Thread.sleep(16)}
        if(holdAtEnd>0) Thread.sleep(holdAtEnd)
        inject(down,MotionEvent.ACTION_UP,toX,toY);device.waitForIdle(700);Thread.sleep(300)
    }
    private fun inject(down:Long,action:Int,x:Int,y:Int) {
        val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x.toFloat(),y.toFloat(),0)
        event.source=InputDevice.SOURCE_TOUCHSCREEN;inst.uiAutomation.injectInputEvent(event,false);event.recycle()
    }
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

    private fun waitText(value:String) {assertTrue(device.wait(Until.hasObject(By.text(Pattern.compile(Pattern.quote(value),Pattern.CASE_INSENSITIVE))),5000));device.waitForIdle(700)}
    private fun show(selector:BySelector):UiObject2 {
        for(forward in listOf(true,false)) for(i in 0..26) {
            runCatching {device.findObject(By.res("android:id/aerr_wait"))?.click()}
            inst.uiAutomation.clearCache()
            val bottom=if(device.hasObject(By.desc("Navigation dock"))) .91 else .98
            device.findObjects(selector).firstOrNull {node->runCatching {node.visibleBounds.let {b->b.height()>10 && b.top>100 && b.bottom<device.displayHeight*bottom}}.getOrDefault(false)}?.let {return it}
            val x=device.displayWidth/12
            device.swipe(x,device.displayHeight*(if(forward) 68 else 38)/100,x,device.displayHeight*(if(forward) 38 else 68)/100,60)
            device.waitForIdle(700)
        }
        shot("missing");throw AssertionError("Missing $selector")
    }
    private fun shot(name:String) {device.waitForIdle(700);device.takeScreenshot(File(out,"$name.png"))}
}
