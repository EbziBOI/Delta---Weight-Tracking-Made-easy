package io.github.ebziboi.delta.nutrition

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.*
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.render.*
import io.github.ebziboi.delta.widget.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.*

@RunWith(AndroidJUnit4::class)
class FoodWidgetTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val now=Instant.now().toEpochMilli()
    private val today=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    private fun state()=NutritionState(entries=listOf(
        DiaryEntry(day=today,meal=Meal.LUNCH,food=Food(name="Roasted vegetables and rice",per100=mapOf(Nutrient.ENERGY to 320.0,Nutrient.PROTEIN to 22.0,Nutrient.CARBS to 45.0,Nutrient.FAT to 9.0)),amount=100.0),
        DiaryEntry(day=today,meal=Meal.BREAKFAST,food=Food(name="Porridge",per100=mapOf(Nutrient.ENERGY to 280.0,Nutrient.PROTEIN to 12.0,Nutrient.CARBS to 40.0,Nutrient.FAT to 7.0)),amount=100.0)),
        targets=NutritionTargets(amounts=mapOf(Nutrient.ENERGY to 2000.0,Nutrient.PROTEIN to 100.0,Nutrient.CARBS to 250.0,Nutrient.FAT to 70.0)),
        fasts=listOf(Fast(start=now-5*3600000,targetHours=16.0)),loaded=true)
    @Test fun everySupportedGridSizeKeepsTextInsideTheWidgetForBothThemesAndAllMaterials() {
        val data=state()
        for(style in DesignStyle.entries) for(night in listOf(false,true)) for(glass in WidgetGlass.entries) {
            val settings=AppSettings(appearance=style.defaults(),widgetGlass=glass,widgetTheme=if(night) WidgetTheme.DARK else WidgetTheme.LIGHT)
            for(kind in FoodWidgetKind.entries) for(cols in 1..6) for(rows in 1..8) {
                val w=60f+(cols-1)*70;val h=60f+(rows-1)*80;val audit=mutableListOf<WidgetElement>()
                val image=FoodWidgetBitmaps.render(context,kind,w,h,data,settings,night,now,maxSide=500f,audit=audit)
                try {
                    assertTrue("Must show useful text: $kind $w x $h",audit.isNotEmpty())
                    audit.forEach {el->
                        assertTrue("Clipped ${el.role}: $kind $w x $h ${el.bounds}",el.bounds.left>=-.5 && el.bounds.top>=-.5 && el.bounds.right<=w+.5 && el.bounds.bottom<=h+.5)
                        assertTrue(el.fontSize.isFinite() && el.fontSize>0)
                    }
                } finally {image.recycle()}
            }
        }
    }
    @Test fun widgetsExposeDistinctRealDataAndMissingNutritionStaysUnknown() {
        val audit=mutableListOf<WidgetElement>();val settings=AppSettings()
        val image=FoodWidgetBitmaps.render(context,FoodWidgetKind.ENERGY,180f,180f,state(),settings,now=now,audit=audit)
        image.recycle();assertTrue(audit.any {it.role=="energy" && it.value=="1400"})
        audit.clear()
        FoodWidgetBitmaps.render(context,FoodWidgetKind.MACROS,320f,180f,state(),settings,now=now,audit=audit).recycle()
        assertTrue(audit.any {it.role=="macro-value-0" && it.value=="34g"})
        audit.clear()
        val unknown=state().copy(entries=listOf(DiaryEntry(day=today,meal=Meal.LUNCH,food=Food(name="Unlabelled",per100=emptyMap()),amount=100.0)))
        FoodWidgetBitmaps.render(context,FoodWidgetKind.ENERGY,180f,180f,unknown,settings,now=now,audit=audit).recycle()
        assertTrue(audit.any {it.role=="energy" && it.value=="—"})
    }
    @Test fun platformMetadataRemoteViewsAndPickerPreviewsCoverEveryFoodWidget() {
        val infos=AppWidgetManager.getInstance(context).installedProviders.filter {it.provider.packageName==context.packageName}
        val dir=File(context.filesDir,"food-widget-review").apply {mkdirs()}
        FoodWidgetKind.entries.forEach {kind->
            val info=infos.single {it.provider==ComponentName(context,FoodWidgetCatalog.receiver(kind))}
            assertNotNull(info.configure);assertTrue(info.resizeMode and 3==3)
            val remote=FoodBitmapWidget.views(context,kind,kind.width,kind.height,state(),AppSettings())
            val view=remote.apply(context,null);assertNotNull(view)
            val settings=AppSettings(appearance=DesignStyle.GLASS.defaults())
            val bitmap=FoodWidgetBitmaps.render(context,kind,kind.width,kind.height,state(),settings,now=now)
            File(dir,"preview-${kind.name.lowercase()}.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            for(style in DesignStyle.entries) {
                val shot=FoodWidgetBitmaps.render(context,kind,kind.width,kind.height,state(),settings.copy(appearance=style.defaults()),now=now)
                File(dir,"${style.name}-${kind.name}.png").outputStream().use {shot.compress(Bitmap.CompressFormat.PNG,100,it)};shot.recycle()
            }
        }
    }
}
