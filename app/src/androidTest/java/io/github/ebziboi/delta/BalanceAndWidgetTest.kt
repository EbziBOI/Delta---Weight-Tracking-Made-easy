package io.github.ebziboi.delta

import android.content.Intent
import android.graphics.*
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
import java.time.*

@RunWith(AndroidJUnit4::class)
class BalanceAndWidgetTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val context=inst.targetContext
    private val device=UiDevice.getInstance(inst)
    private val store=SettingsStore(context)
    private val out=File(context.filesDir,"balanced-review").apply {mkdirs()}
    init { Configurator.getInstance().setWaitForIdleTimeout(700) }
    private fun snapshot():ProgressSnapshot {
        val settings=AppSettings(targetKg=80.0,startEpochDay=LocalDate.now().minusDays(74).toEpochDay())
        val weights=SampleData.entries(LocalDate.now().minusDays(74))
        return ProgressCalculator.compute(weights,settings).copy(bodyFat=weights.map { BodyFatEntry(it.epochMs,27.0-(it.kg-88)*.12,false,it.id) },targetBodyFat=22.0)
    }
    @Test fun weeklyWidgetDataUsesTheCurrentWeekAndIgnoresFutureBodyFat() {
        val now=Instant.parse("2026-11-15T12:00:00Z");val end=now.toEpochMilli()
        val points=listOf(14 to 96.0,7 to 94.0,2 to 92.0,-1 to 600.0).map { (ago,kg)->TrendPoint(end-ago*86400000L,kg,kg) }
        val snap=snapshot().copy(history=points,points=points.takeLast(1),bodyFat=listOf(BodyFatEntry(end-1000,25.0,false,"a"),BodyFatEntry(end+1000,50.0,false,"b")))
        val data=WidgetData.from(snap,now)
        assertEquals(-2.0,data.weekChangeKg!!,.0001);assertEquals(25.0,data.bodyFat.single().percent,.0001)
        assertNull(WidgetData.from(snap.copy(history=points.filter {it.epochMs<end-9*86400000L}),now).weekChangeKg)
    }
    @Test fun everyWidgetFitsWithoutOverlappingOrShrinkingItsValuesAway() {
        val snap=snapshot()
        val failures=mutableListOf<String>()
        fun check(condition:Boolean,message:String) {if(!condition) failures.add(message)}
        val sizes=listOf(58f to 70f,76f to 85f,110f to 50f,110f to 150f,165f to 85f,172f to 100f,172f to 180f,165f to 290f,200f to 380f,255f to 85f,350f to 82f,350f to 120f,360f to 200f,345f to 290f,420f to 420f,697f to 51f)
        val empty=snap.copy(points=emptyList(),history=emptyList(),currentKg=null,currentMs=null,startKg=null,fraction=null,bodyFat=emptyList(),weeklyRateKg=null,projectedDay=null)
        val single=snap.copy(points=snap.points.take(1),history=snap.history.take(1),bodyFat=snap.bodyFat.take(1))
        for(theme in DesignStyle.entries) for(unit in WeightUnit.entries) for((w,h) in sizes) for(kind in WidgetKind.entries) for(state in listOf(snap,empty,single)) {
            val settings=AppSettings(unit=unit,appearance=theme.defaults(),widgetGlass=WidgetGlass.SOLID)
            val audit=mutableListOf<WidgetElement>()
            val image=WidgetBitmaps.render(context,kind,w,h,state,settings,maxSidePx=360f,audit=audit)
            val key="$theme / $unit / $kind / ${w}x$h / ${state.points.size} readings"
            check((image.getPixel(0,0) ushr 24)==0,"Corners $key")
            for(element in audit) {
                val r=element.bounds
                check(r.left>=-.1f && r.top>=-.1f && r.right<=w+.1f && r.bottom<=h+.1f,"Out of bounds $key: ${element.role} $r")
                if(element.fontSize>0) check(element.fontSize>=8.9f,"Type too small $key: $element")
                if(element.role=="weight" || element.role=="body-fat" || element.role=="rate" || element.role.startsWith("value-"))
                    check(!element.value.endsWith("…"),"Primary value incomplete $key: $element")
            }
            for(i in audit.indices) for(j in i+1 until audit.size) {
                val overlap=RectF(audit[i].bounds)
                if(overlap.intersect(audit[j].bounds)) check(overlap.width()<.5f || overlap.height()<.5f,"Overlapping $key: ${audit[i]} / ${audit[j]}")
            }
            image.recycle()
        }
        assertTrue(failures.take(30).joinToString("\n"),failures.isEmpty())
    }
    @Test fun summaryDetailWorksAtItsDefaultSize() {
        val snapshot=snapshot()
        for(theme in DesignStyle.entries) {
            val settings=AppSettings(appearance=theme.defaults())
            val audit=mutableListOf<WidgetElement>()
            val withDetail=WidgetBitmaps.render(context,WidgetKind.CARD,WidgetKind.CARD.defaultWidth,WidgetKind.CARD.defaultHeight,snapshot,settings,audit=audit)
            val without=WidgetBitmaps.render(context,WidgetKind.CARD,WidgetKind.CARD.defaultWidth,WidgetKind.CARD.defaultHeight,snapshot,settings.copy(widgetContent=WidgetContent(showDetail=false)))
            assertFalse("Detail toggle must visibly change the default preview",withDetail.sameAs(without))
            assertTrue(audit.joinToString { it.role+"="+it.value },audit.any {it.role=="summary" && it.value.contains("to go")})
            withDetail.recycle();without.recycle()
        }
    }
    @Test fun reviewAllWidgetMaterialsSizesAndComplications() {
        val snap=snapshot();val dim=listOf(172f to 180f,350f to 105f,350f to 200f)
        for(theme in DesignStyle.entries) for(night in listOf(false,true)) for(glass in WidgetGlass.entries) {
            val base=AppSettings(appearance=theme.defaults(),widgetTheme=WidgetTheme.SYSTEM,widgetGlass=glass,
                widgetOpacityDark=glass.defaultDark,widgetOpacityLight=glass.defaultLight)
            val gap=12;val cellW=350;val cellH=212
            val sheet=Bitmap.createBitmap(cellW*3+gap*4,cellH*6+gap*7,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(sheet);SampleData.wallpaper(canvas,sheet.width,sheet.height,!night)
            WidgetKind.entries.forEachIndexed {row,kind->dim.forEachIndexed {col,(w,h)->
                val image=WidgetBitmaps.render(context,kind,w,h,snap,base,night,maxSidePx=350f)
                canvas.drawBitmap(image,Rect(0,0,image.width,image.height),RectF((gap+col*(cellW+gap)).toFloat(),(gap+row*(cellH+gap)).toFloat(),(gap+col*(cellW+gap)+w).toFloat(),(gap+row*(cellH+gap)+h).toFloat()),null)
                image.recycle()
            }}
            SampleData.save(sheet,File(out,"widgets-$theme-$glass-$night.png"));sheet.recycle()
        }
        for(theme in DesignStyle.entries) {
            val sheet=Bitmap.createBitmap(7*180,210,Bitmap.Config.ARGB_8888);val c=Canvas(sheet);c.drawColor(Color.DKGRAY)
            PercentageDesign.entries.forEachIndexed {i,design->
                val image=WidgetBitmaps.render(context,WidgetKind.COMPLICATION,172f,180f,snap,AppSettings(appearance=theme.defaults(),percentageDesign=design),maxSidePx=172f)
                c.drawBitmap(image,i*180f,12f,null);image.recycle()
            }
            SampleData.save(sheet,File(out,"complications-$theme.png"));sheet.recycle()
        }
    }
    @Test fun plusButtonSharesTheDockAndMainCardStaysProportional() = runBlocking {
        val original=store.settings.first();val weights=store.weights.first()
        inst.uiAutomation.grantRuntimePermission(context.packageName,"android.permission.health.WRITE_WEIGHT")
        try {
            for(theme in DesignStyle.entries) {
                store.update { it.copy(onboardingDone=true,motion=false,targetKg=80.0,startEpochDay=LocalDate.now().minusDays(74).toEpochDay(),percentageDesign=PercentageDesign.MOSAIC,progressDisplay=ProgressDisplay.COMPLICATION,appearance=theme.defaults().copy(reduceMotion=true)) }
                store.saveWeights(SampleData.entries(LocalDate.now().minusDays(74)))
                context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                assertTrue(device.wait(Until.hasObject(By.desc("Log weight")),15000));Thread.sleep(700);inst.uiAutomation.clearCache()
                val plus=device.findObject(By.desc("Log weight")).visibleBounds
                val history=device.findObjects(By.text("History")).last().visibleBounds
                assertTrue("Plus must sit beside the dock",plus.left>history.right && kotlin.math.abs(plus.centerY()-history.centerY())<100)
                assertTrue("Log button must remain a small circle",plus.width()<=device.displayWidth*.27 && kotlin.math.abs(plus.width()-plus.height())<5)
                val art=device.findObject(By.descStartsWith("Mosaic:")).visibleBounds
                assertTrue("Complication must fit the card",art.width() in (device.displayWidth*.25f).toInt()..(device.displayWidth*.39f).toInt())
                device.takeScreenshot(File(out,"home-$theme.png"))
                device.findObject(By.desc("Log weight")).click()
                assertTrue("Plus must open the weight dialog",device.wait(Until.hasObject(By.text("Log weight")),5000))
                device.findObject(By.text("Cancel")).click()
                assertTrue(device.wait(Until.gone(By.text("Log weight")),5000))
                device.waitForIdle(700);Thread.sleep(400)
                val x=device.displayWidth/12
                device.swipe(x,device.displayHeight*65/100,x,device.displayHeight*38/100,70)
                assertTrue(device.wait(Until.gone(By.desc("Log weight")),5000));assertFalse(device.hasObject(By.text("History")))
                device.takeScreenshot(File(out,"hidden-$theme.png"))
                device.swipe(x,device.displayHeight*38/100,x,device.displayHeight*65/100,70)
                assertTrue(device.wait(Until.hasObject(By.desc("Log weight")),5000));assertTrue(device.hasObject(By.text("History")))
            }
        } finally {store.update { original };store.saveWeights(weights)}
        Unit
    }
}
