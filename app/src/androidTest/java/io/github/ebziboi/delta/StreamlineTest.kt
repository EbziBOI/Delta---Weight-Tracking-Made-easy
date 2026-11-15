package io.github.ebziboi.delta

import android.content.Intent
import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.render.*
import io.github.ebziboi.delta.icons.LauncherIcons
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class StreamlineTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val device = UiDevice.getInstance(inst)
    private val store = SettingsStore(context)
    private val out = File(context.filesDir,"streamline").apply { mkdirs() }
    init { Configurator.getInstance().setWaitForIdleTimeout(700) }

    @Test fun zoomAndPanOnlyChangeDates() {
        val full = GraphViewport(0,32000,70.0,100.0)
        val zoomed = full.transform(full,4f,.3f,.8f,0f,.9f)
        assertEquals(4.0,zoomed.zoom(full),.001)
        assertEquals(full.low,zoomed.low,0.0);assertEquals(full.high,zoomed.high,0.0)
        assertEquals(zoomed,zoomed.transform(full,1f,.5f,.2f,0f,-.8f))
        assertNotEquals(zoomed.first,zoomed.transform(full,1f,.5f,.2f,.2f,0f).first)
    }
    @Test fun complicationsScaleAcrossThemesAndPersist() = runBlocking {
        val original=store.settings.first()
        try {
            for (design in PercentageDesign.entries) {
                store.update { it.copy(percentageDesign=design) }
                assertEquals(design,SettingsStore(context).settings.first().percentageDesign)
                if (design==PercentageDesign.ADAPTIVE) continue
                for (matte in listOf(false,true)) for (size in listOf(56,118,240)) {
                    val a=if(matte) DesignStyle.TUBIK.defaults() else AppAppearance()
                    val colors=DerivedTheme.derive(a).drawColors(); val type=DeltaFonts.canvas(context,TypeStyle.MODERN)
                    val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
                    PercentageArt.draw(Canvas(bitmap),RectF(0f,0f,size.toFloat(),size.toFloat()),.64f,design,colors,type,matte)
                    assertEquals(0,bitmap.getPixel(0,0) ushr 24)
                    SampleData.save(bitmap,File(out,"${design.name}-$matte-$size.png"));bitmap.recycle()
                }
            }
            for (icon in LauncherIcons.all) assertNotNull(androidx.core.content.ContextCompat.getDrawable(context,icon.art))
        } finally { store.update { original } }
    }
    @Test fun dockHidesOnScrollAndHeroHasRoomToLead() = runBlocking {
        val original=store.settings.first(); val weights=store.weights.first()
        try {
            store.update { it.copy(onboardingDone=true,motion=false,targetKg=80.0,percentageDesign=PercentageDesign.SUMMIT,
                appearance=DesignStyle.TUBIK.defaults().copy(reduceMotion=true)) }
            store.saveWeights(SampleData.entries(LocalDate.now().minusDays(74)))
            context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            assertTrue(device.wait(Until.hasObject(By.descStartsWith("Summit:")),15000))
            inst.uiAutomation.clearCache()
            val hero=device.findObject(By.descStartsWith("Summit:")).visibleBounds
            assertTrue("Home complication should fit alongside the weight",hero.width() in (device.displayWidth*.25f).toInt()..(device.displayWidth*.42f).toInt())
            assertTrue(device.findObject(By.text("Progress")).parent.visibleBounds.width()<device.displayWidth*.7f)
            device.takeScreenshot(File(out,"hero-geometric.png"))
            val x=device.displayWidth/12
            device.swipe(x,device.displayHeight*65/100,x,device.displayHeight*35/100,70)
            assertTrue("Dock should hide while scrolling down",device.wait(Until.gone(By.text("History")),5000))
            device.takeScreenshot(File(out,"dock-hidden.png"))
            device.swipe(x,device.displayHeight*35/100,x,device.displayHeight*65/100,70)
            assertTrue("Dock should return when scrolling up",device.wait(Until.hasObject(By.text("History")),5000))
            inst.uiAutomation.clearCache()
            device.findObjects(By.text("History")).last { it.visibleBounds.centerY()>device.displayHeight*.8f }.click()
            device.waitForIdle(700)
            device.swipe(x,device.displayHeight*65/100,x,device.displayHeight*40/100,70)
            device.waitForIdle(700);inst.uiAutomation.clearCache()
            val page=device.findObjects(By.scrollable(true)).firstOrNull()
            assertNotNull(page)
            assertTrue("History viewport must extend behind the toolbar",page!!.visibleBounds.top<device.displayHeight*.05f)
            device.takeScreenshot(File(out,"history-short-fade.png"))
        } finally { store.update { original };store.saveWeights(weights) }
        Unit
    }

    @Test fun compactNavigationAndHealthDetail() = runBlocking {
        val original=store.settings.first();val weights=store.weights.first()
        try {
            store.update { it.copy(onboardingDone=true,motion=false,appearance=AppAppearance(reduceMotion=true)) }
            store.saveWeights(SampleData.entries(LocalDate.now().minusDays(74)))
            context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            assertTrue(device.wait(Until.hasObject(By.desc("Settings")),15000))
            inst.uiAutomation.clearCache()
            assertFalse(device.findObjects(By.text("Settings")).any { it.visibleBounds.centerY()>device.displayHeight*.8f })
            device.takeScreenshot(File(out,"progress.png"))
            device.findObject(By.desc("Settings")).click();device.waitForIdle(1000)
            assertTrue(device.wait(Until.hasObject(By.text("BODY COMPOSITION")),5000))
            device.takeScreenshot(File(out,"dashboard.png"))
            var target:UiObject2?=null
            for(i in 0..15) {
                inst.uiAutomation.clearCache()
                target=device.findObjects(By.textStartsWith("Health Connect")).firstOrNull { it.visibleBounds.centerY() in 120..(device.displayHeight*.8f).toInt() }
                if(target!=null) break
                device.swipe(device.displayWidth/14,device.displayHeight*65/100,device.displayWidth/14,device.displayHeight*43/100,70)
                device.waitForIdle(700)
            }
            assertNotNull(target)
            assertFalse(device.hasObject(By.text("Read weight")))
            var node=target!!;while(!node.isClickable && node.parent!=null) node=node.parent
            node.click();device.waitForIdle(1000)
            assertTrue(device.wait(Until.hasObject(By.text("Read weight")),5000))
            device.takeScreenshot(File(out,"health-detail.png"))
        } finally { store.update { original };store.saveWeights(weights) }
        Unit
    }
}
