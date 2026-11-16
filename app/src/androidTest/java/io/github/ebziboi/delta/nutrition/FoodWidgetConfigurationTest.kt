package io.github.ebziboi.delta.nutrition

import android.appwidget.*
import android.content.*
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.render.*
import io.github.ebziboi.delta.widget.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoodWidgetConfigurationTest {
    @Test fun aBoundFoodWidgetHasAnIndependentLiveEditorAndCorrectOpenRoutes()=runBlocking {
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        val device=UiDevice.getInstance(inst);val manager=AppWidgetManager.getInstance(context)
        val host=AppWidgetHost(context,973310);val id=host.allocateAppWidgetId()
        val store=WidgetStyleStore(context);val settings=SettingsStore(context);val saved=settings.settings.first()
        try {
            inst.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
            val bound=manager.bindAppWidgetIdIfAllowed(id,ComponentName(context,FoodWidgetCatalog.receiver(FoodWidgetKind.ENERGY)),Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,180);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,180)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,180);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,180)
            })
            inst.uiAutomation.dropShellPermissionIdentity();assertTrue("Test host must bind its own provider",bound)
            settings.update {it.copy(appearance=DesignStyle.GLASS.defaults().copy(reduceMotion=true),motion=false)}
            store.save(id,WidgetStyle.from(settings.settings.first()))
            val intent=Intent(context,FoodWidgetConfigurationActivity::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
            fun tap(label:String) {
                val selector=By.text(label);device.wait(Until.hasObject(selector),5000)
                repeat(12) {
                    inst.uiAutomation.clearCache()
                    device.findObject(selector)?.let {n->if(n.visibleBounds.height()>8 && n.visibleBounds.bottom<device.displayHeight-30) {n.click();device.waitForIdle(500);return}}
                    device.swipe(18,device.displayHeight*72/100,18,device.displayHeight*40/100,25)
                };error("Missing $label")
            }
            ActivityScenario.launch<FoodWidgetConfigurationActivity>(intent).use {scenario->
                assertTrue(device.wait(Until.hasObject(By.desc("Live Energy budget preview")),10000))
                tap("Solid");scenario.recreate();assertTrue(device.wait(Until.hasObject(By.desc("Live Energy budget preview")),10000));assertEquals(WidgetGlass.FROSTED,store.style(id).first()!!.glass)
                tap("Save");withTimeout(15000) {store.style(id).first {s->s?.glass==WidgetGlass.SOLID}}
            }
            ActivityScenario.launch<FoodWidgetConfigurationActivity>(intent).use {tap("Clear");tap("Cancel")}
            assertEquals(WidgetGlass.SOLID,store.style(id).first()!!.glass)
            assertEquals("charts",FoodBitmapWidget.launchIntent(context,FoodWidgetKind.ENERGY.route).getStringExtra("food_route"))
            assertEquals("search",FoodBitmapWidget.launchIntent(context,"search").getStringExtra("food_route"))
        } finally {
            inst.uiAutomation.dropShellPermissionIdentity();host.deleteAppWidgetId(id);store.delete(intArrayOf(id));settings.update {saved}
        }
    }
}
