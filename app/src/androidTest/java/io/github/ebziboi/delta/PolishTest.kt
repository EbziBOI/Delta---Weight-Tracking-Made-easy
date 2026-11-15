package io.github.ebziboi.delta

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.icons.LauncherIcons
import io.github.ebziboi.delta.ui.balancedOptionRows
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.*
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class PolishTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val context=inst.targetContext
    private val device=UiDevice.getInstance(inst)
    private val store=SettingsStore(context)
    private val out=File(context.filesDir,"polish-review").apply {mkdirs()}
    init {Configurator.getInstance().setWaitForIdleTimeout(700)}
    private fun match(text:String)=By.text(Pattern.compile(Pattern.quote(text),Pattern.CASE_INSENSITIVE))
    private suspend fun seed(style:DesignStyle) {
        val day=LocalDate.now().minusDays(74)
        store.update {it.copy(onboardingDone=true,motion=false,targetKg=80.0,startEpochDay=day.toEpochDay(),
            appearance=style.defaults().copy(reduceMotion=true),progressDisplay=ProgressDisplay.COMPLICATION,percentageDesign=PercentageDesign.MOSAIC,
            bodyFatChart=ChartStyle.CONNECTED)}
        store.saveWeights(SampleData.entries(day))
        store.saveBodyFat((0..30).map {BodyFatEntry(Instant.now().minusSeconds((30L-it)*86400).toEpochMilli(),27.0-it*.05+if(it%3==0) .3 else 0.0,false,"polish-$it")})
    }
    private fun launch() {
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        val deadline=System.currentTimeMillis()+20000
        while(System.currentTimeMillis()<deadline) {
            inst.uiAutomation.clearCache()
            device.findObject(By.text("Wait"))?.click()
            if(device.wait(Until.hasObject(By.desc("Settings")),1000)) break
        }
        assertTrue("Main screen must be visible",device.hasObject(By.desc("Settings")));Thread.sleep(700);inst.uiAutomation.clearCache()
    }
    @Test fun iconsUseOriginalDetailedBitmapsWithSafeMasking() {
        val sheet=Bitmap.createBitmap(672,9*230,Bitmap.Config.ARGB_8888);val c=Canvas(sheet);c.drawColor(Color.DKGRAY)
        val ink=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.WHITE;textSize=13f}
        LauncherIcons.all.drop(1).forEachIndexed {i,icon->
            val drawable=ContextCompat.getDrawable(context,icon.art) as AdaptiveIconDrawable
            assertTrue("Launcher art must be a detailed raster, not the simplified vector",drawable.foreground is BitmapDrawable)
            assertTrue((drawable.foreground as BitmapDrawable).bitmap.width>=512)
            if(i<5) {
                val art=(drawable.foreground as BitmapDrawable).bitmap
                for(x in listOf(80,640)) for(y in listOf(80,200,360,520,640)) {
                    val pixel=art.getPixel(x,y)
                    assertTrue("Padding must not repeat white artwork outside the original tile",maxOf(Color.red(pixel),Color.green(pixel),Color.blue(pixel))<180)
                }
            }
            val sourceId=context.resources.getIdentifier("app_icon_%02d".format(i+1),"drawable",context.packageName)
            val source=ContextCompat.getDrawable(context,sourceId)!!.toBitmap(192,192)
            c.drawBitmap(source,12f,(i*230+24).toFloat(),null)
            val circle=drawable.toBitmap(192,192);c.drawBitmap(circle,236f,(i*230+24).toFloat(),null)
            val save=c.save();c.translate(460f,(i*230+24).toFloat())
            c.clipPath(Path().apply {addRoundRect(RectF(0f,0f,192f,192f),48f,48f,Path.Direction.CW)})
            drawable.foreground.bounds=Rect(-48,-48,240,240);drawable.foreground.draw(c);c.restoreToCount(save)
            c.drawText("${i+1} Original / Circle / Rounded square",12f,(i*230+17).toFloat(),ink)
            source.recycle();circle.recycle()
        }
        SampleData.save(sheet,File(out,"original-icons-and-masks.png"));sheet.recycle()
    }
    @Test fun selectorsBalanceRowsAndBodyFatTrendIsReal() = runBlocking {
        assertEquals(listOf(3,2,2),balancedOptionRows((0..6).toList(),3).map {it.size})
        assertEquals(listOf(3,3,2),balancedOptionRows((0..7).toList(),3).map {it.size})
        val now=Instant.now().toEpochMilli()
        val fat=(0..20).map {BodyFatEntry(now-(20L-it)*86400000,25.0+if(it%2==0) 1.0 else -1.0,false,"$it")}
        val points=ProgressCalculator.bodyFatTrend(fat)
        assertTrue(points.any {kotlin.math.abs(it.trendKg-it.kg)>.1})
        val original=store.settings.first()
        try {store.update {it.copy(bodyFatChart=ChartStyle.TREND)};assertEquals(ChartStyle.TREND,SettingsStore(context).settings.first().bodyFatChart)}
        finally {store.update {original}}
    }
    @Test fun portraitHeadersSettingsAndComplicationsWorkInBothThemes() = runBlocking {
        val original=store.settings.first();val weights=store.weights.first();val fat=store.bodyFat.first()
        try {
            for(style in DesignStyle.entries) {
                seed(style);launch()
                val settings=device.findObject(By.desc("Settings")).visibleBounds
                val heading=device.findObjects(match("Progress")).first {it.visibleBounds.centerY()<device.displayHeight/3}.visibleBounds
                assertTrue("Title and actions must start on the same row",kotlin.math.abs(settings.top-heading.top)<30)
                device.freezeRotation(1);Thread.sleep(600)
                var orientation=0
                inst.runOnMainSync {orientation=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first {it is MainActivity}.resources.configuration.orientation}
                assertEquals(Configuration.ORIENTATION_PORTRAIT,orientation)
                device.freezeRotation(0);Thread.sleep(500)
                device.findObject(By.desc("Settings")).click();device.waitForIdle(700);Thread.sleep(300)
                assertTrue(device.wait(Until.gone(By.desc("Settings")),5000))
                assertFalse(device.hasObject(By.desc("Sync with Health Connect")))
                val kg=clickable(show(By.text("kg"))).visibleBounds
                val lb=clickable(show(By.text("lb"))).visibleBounds
                val stone=clickable(show(By.text("st & lb"))).visibleBounds
                assertTrue("Equal unit touch targets: kg=$kg lb=$lb stone=$stone",kotlin.math.abs(kg.width()-lb.width())<4 && kotlin.math.abs(lb.width()-stone.width())<4)
                tap("Complications")
                assertTrue(device.wait(Until.hasObject(match("Percentage design")),5000))
                tap("Perimeter")
                assertTrue(device.wait(Until.gone(match("Percentage design")),5000))
                assertTrue(device.wait(Until.hasObject(match("of goal")),5000))
                shot("$style-perimeter")
                tap("Bar");assertFalse(device.hasObject(match("Percentage design")))
                assertTrue(device.wait(Until.hasObject(match("Bar style")),5000))
                tap("Art");assertTrue(device.wait(Until.hasObject(match("Percentage design")),5000))
                device.pressBack();tap("Appearance")
                show(By.desc("Tubik inspired design"))
                assertTrue(device.hasObject(By.desc("Glass design")))
                show(By.text("Glass blur"));show(By.text("Glass transparency"))
                shot("$style-material")
                device.pressBack();device.pressBack()
            }
        } catch(t:Throwable) {shot("failure-settings");throw t}
        finally {device.freezeRotation(0);store.update {original};store.saveWeights(weights);store.saveBodyFat(fat)}
        Unit
    }
    @Test fun dockStaysCenteredAndViewportDoesNotResizeWhenItHides() = runBlocking {
        val original=store.settings.first();val weights=store.weights.first();val fat=store.bodyFat.first()
        try {for(style in DesignStyle.entries) {
            seed(style);launch()
            val progress=clickable(device.findObjects(match("Progress")).last {it.visibleBounds.centerY()>device.displayHeight*.8}).visibleBounds
            val history=clickable(device.findObjects(By.text("History")).last()).visibleBounds
            val union=Rect(progress).apply {union(history)}
            assertTrue("Center the navigation bar independently of the plus",kotlin.math.abs(union.centerX()-device.displayWidth/2)<5)
            val plus=device.findObject(By.desc("Log weight")).visibleBounds
            assertTrue("Plus offset: plus=$plus bar=$union display=${device.displayWidth}",plus.left>union.right && plus.right<=device.displayWidth)
            shot("$style-header-and-dock")
            val before=device.findObject(By.scrollable(true)).visibleBounds
            fun scroll(down:Boolean) {
                val x=device.displayWidth/12
                device.swipe(x,device.displayHeight*(if(down) 65 else 38)/100,x,device.displayHeight*(if(down) 38 else 65)/100,70)
            }
            scroll(true);assertTrue(device.wait(Until.gone(By.desc("Log weight")),5000))
            assertEquals("The scroll viewport must stay stable while the overlay hides",before,device.findObject(By.scrollable(true)).visibleBounds)
            scroll(false);assertTrue(device.wait(Until.hasObject(By.desc("Log weight")),5000))
        }} finally {store.update {original};store.saveWeights(weights);store.saveBodyFat(fat)}
        Unit
    }
    @Test fun bodyFatGraphInteractionChangesThePersistedStyleWithoutHints() = runBlocking {
        val original=store.settings.first();val weights=store.weights.first();val fat=store.bodyFat.first()
        try {
            seed(DesignStyle.TUBIK);launch()
            val b=show(By.desc("Body fat graph")).visibleBounds
            shot("body-fat-before")
            fun tapPoint() {
                val down=android.os.SystemClock.uptimeMillis()
                for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
                    val event=android.view.MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,b.centerX().toFloat(),b.centerY().toFloat(),0)
                    event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                    inst.uiAutomation.injectInputEvent(event,false);event.recycle()
                    Thread.sleep(20)
                }
            }
            tapPoint();Thread.sleep(50);tapPoint()
            kotlinx.coroutines.withTimeout(7000) {store.settings.first {it.bodyFatChart==ChartStyle.TREND}}
            assertFalse(device.hasObject(By.textContains("double-tap")))
            shot("body-fat-style")
        } finally {store.update {original};store.saveWeights(weights);store.saveBodyFat(fat)}
        Unit
    }
    private fun clickable(node:UiObject2):UiObject2 {var n=node;while(!n.isClickable && !n.isSelected && n.parent!=null)n=n.parent;return n}
    private fun show(selector:BySelector):UiObject2 {
        for(forward in listOf(true,false)) for(i in 0..22) {
            inst.uiAutomation.clearCache()
            device.findObjects(selector).firstOrNull {it.visibleBounds.let {b->b.height()>10 && b.top>65 && b.bottom<device.displayHeight*.88}}?.let {return it}
            val x=device.displayWidth/12
            device.swipe(x,device.displayHeight*(if(forward) 65 else 40)/100,x,device.displayHeight*(if(forward) 40 else 65)/100,65)
            device.waitForIdle(700)
        }
        shot("missing");throw AssertionError("Missing $selector")
    }
    private fun tap(text:String) {show(match(text)).click();device.waitForIdle(700);Thread.sleep(300)}
    private fun shot(name:String) {device.waitForIdle(700);device.takeScreenshot(File(out,"$name.png"))}
}
