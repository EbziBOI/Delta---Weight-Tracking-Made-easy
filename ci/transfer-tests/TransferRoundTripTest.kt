package io.github.ebziboi.delta

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import io.github.ebziboi.delta.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.regex.Pattern

/** Runs in the Stage 1 transfer helper's UID; all fixture writes are local caches. */
@RunWith(AndroidJUnit4::class)
class TransferRoundTripTest {
    @Test fun copiesToSeparateBetaAndTestChangesLeaveSourceUntouched() = runBlocking {
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext;val device=UiDevice.getInstance(inst)
        val store=SettingsStore(context);val before=store.settings.first();val beforeWeight=store.weights.first();val beforeFat=store.bodyFat.first()
        val date=LocalDate.now().minusDays(1).atTime(10,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val sourceWeights=listOf(WeightEntry(date,96.1,false,"source-fixture-weight"))
        val sourceFat=listOf(BodyFatEntry(date,27.2,false,"source-fixture-fat"))
        fun tap(label:String) {
            val selector=By.text(Pattern.compile(Pattern.quote(label),Pattern.CASE_INSENSITIVE))
            device.wait(Until.hasObject(selector),4000)
            for(i in 0..14) {
                inst.uiAutomation.clearCache()
                device.findObject(selector)?.let {n->n.visibleBounds.let {b->if(b.height()>10 && b.top>50 && b.bottom<device.displayHeight-30) {n.click();device.waitForIdle(700);return}}}
                device.swipe(30,device.displayHeight*70/100,30,device.displayHeight*35/100,35)
            }
            throw AssertionError("Cannot find $label")
        }
        try {
            store.update {it.copy(onboardingDone=true,targetKg=81.7,unit=WeightUnit.KG,startEpochDay=LocalDate.now().minusDays(30).toEpochDay(),
                appearance=DesignStyle.TUBIK.defaults().copy(reduceMotion=true),motion=false)}
            store.saveWeights(sourceWeights);store.saveBodyFat(sourceFat)
            context.startActivity(Intent().setClassName("io.github.ebziboi.delta.beta","io.github.ebziboi.delta.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            if(!device.wait(Until.hasObject(By.text("Copy from Delta")),3000)) {
                device.wait(Until.findObject(By.desc("Settings")),10000)?.click()
            }
            assertTrue(device.wait(Until.hasObject(By.text("Copy from Delta")),10000))
            tap("Copy from Delta");tap("Copy");tap("Copy to Delta Beta");device.waitForIdle(700);tap("Progress")
            assertTrue("The copied reading must appear in the beta",device.wait(Until.hasObject(By.textContains("96.1")),15000))
            assertEquals(sourceWeights,store.weights.first());assertEquals(sourceFat,store.bodyFat.first());assertEquals(81.7,store.settings.first().targetKg!!,.001)
            // Returning from a scrolled Settings page can leave the dock hidden.
            device.swipe(16,device.displayHeight*35/100,16,device.displayHeight*65/100,25)
            device.waitForIdle(1000);inst.uiAutomation.clearCache()
            val log=device.wait(Until.findObject(By.desc("Log weight")),15000);assertNotNull(log);log.click()
            val field=device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000);assertNotNull(field);field.text="97.3"
            tap("Save")
            assertTrue(device.wait(Until.hasObject(By.textContains("97.3")),10000))
            assertEquals("A beta test log cannot touch the stable cache",sourceWeights,store.weights.first())
            assertEquals(sourceFat,store.bodyFat.first())
            tap("History");assertTrue(device.wait(Until.hasObject(By.text("2 beta weigh-ins")),10000))
            device.wait(Until.findObject(By.desc("Delete weigh-in")),5000)?.click() ?: error("Beta copied entries must be deletable locally")
            tap("Delete");assertTrue(device.wait(Until.hasObject(By.text("1 beta weigh-in")),10000))
            assertEquals(sourceWeights,store.weights.first());assertEquals(sourceFat,store.bodyFat.first())
        } finally {store.update {before};store.saveWeights(beforeWeight);store.saveBodyFat(beforeFat)}
    }
}
