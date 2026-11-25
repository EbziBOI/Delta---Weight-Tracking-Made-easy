package io.github.ebziboi.delta.nutrition

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.ebziboi.delta.*
import io.github.ebziboi.delta.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/** Walks the reworked diary and Add food flow with realistic data, checking behaviour and saving screenshots. */
@RunWith(AndroidJUnit4::class)
class DiaryTourTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val store=SettingsStore(context)
    private val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val out=File(context.filesDir,"diary-tour").apply {mkdirs()}
    private val repo get()=(context.applicationContext as DeltaApp).nutrition
    private fun shot(name:String) {
        compose.waitForIdle()
        // The emulator's System UI sometimes raises a "not responding" prompt; keep it out of review shots.
        device.findObject(androidx.test.uiautomator.By.text("Wait"))?.click()
        Thread.sleep(250);device.takeScreenshot(File(out,"$name.png"))
    }
    private fun waitText(text:String) = compose.waitUntil(10000) {compose.onAllNodesWithText(text,substring=true,ignoreCase=true).fetchSemanticsNodes().isNotEmpty()}
    private fun food(name:String,kcal:Double,p:Double,c:Double,f:Double,serving:Double?=null)=
        Food("tour-"+name.lowercase().replace(' ','-'),name,per100=mapOf(Nutrient.ENERGY to kcal,Nutrient.PROTEIN to p,Nutrient.CARBS to c,Nutrient.FAT to f,Nutrient.FIBRE to 2.0),serving=serving)

    @Test fun diaryAndAddFoodTour()=runBlocking {
        val saved=store.settings.first();val weights=store.weights.first()
        val oats=food("Porridge oats",375.0,11.0,60.0,8.0,40.0);val yoghurt=food("Greek yoghurt",97.0,9.0,4.0,5.0,170.0)
        val chicken=food("Chicken breast",165.0,31.0,0.0,3.6);val rice=food("Basmati rice",130.0,2.7,28.0,0.3)
        val wrap=food("Chicken wrap",230.0,14.0,25.0,8.0,210.0);val apple=food("Apple",52.0,0.3,14.0,0.2,150.0);val salmon=food("Salmon fillet",208.0,20.0,0.0,13.0,120.0)
        val today=LocalDate.now()
        val seeded=(0L..9L).flatMap {d->
            val day=today.minusDays(d)
            listOfNotNull(DiaryEntry(day=day,meal=Meal.BREAKFAST,food=oats,amount=40.0+d),DiaryEntry(day=day,meal=Meal.BREAKFAST,food=yoghurt,amount=170.0),
                DiaryEntry(day=day,meal=Meal.LUNCH,food=wrap,amount=210.0),DiaryEntry(day=day,meal=Meal.DINNER,food=if(d%2==0L) chicken else salmon,amount=180.0+d*10),
                DiaryEntry(day=day,meal=Meal.DINNER,food=rice,amount=150.0+d*15),if(d%3==0L) DiaryEntry(day=day,meal=Meal.SNACKS,food=apple,amount=150.0) else null)
        }.filterNot {it.day==today && it.meal==Meal.DINNER}
        try {
            val zone=ZoneId.systemDefault()
            store.saveWeights((0..60).map {d->WeightEntry(today.minusDays(60L-d).atTime(7,30).atZone(zone).toInstant().toEpochMilli(),
                84.0-d*.06+kotlin.math.sin(d*1.7)*.45,true,"tour-$d")})
            repo.saveTargets(NutritionTargets(mapOf(Nutrient.ENERGY to 2200.0,Nutrient.PROTEIN to 150.0,Nutrient.CARBS to 230.0,Nutrient.FAT to 70.0)))
            repo.log(seeded)
            store.update {it.copy(onboardingDone=true,motion=false,appearance=DesignStyle.GLASS.defaults().copy(reduceMotion=true),
                homeCardOrder=listOf("trend")+io.github.ebziboi.delta.data.CardLayouts.home.filterNot {id->id=="trend"})}
            compose.activityRule.scenario.recreate();compose.waitForIdle()
            waitText("Trends")
            val trends=compose.onNodeWithContentDescription("Combined trends graph");trends.performScrollTo()
            repeat(3) {if(!trends.fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("Trend line")) trends.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)}
            compose.waitForIdle();Thread.sleep(600);shot("01-home-trends")
            device.swipe(device.displayWidth/2,(device.displayHeight*.4f).toInt(),device.displayWidth/2,(device.displayHeight*.7f).toInt(),20);compose.waitForIdle()

            compose.onNodeWithText("Diary").performClick();waitText("Daily nutrition");shot("02-diary-top")
            // Tapping a chart day selects that day for the whole diary, including the kcal headline.
            val chart=compose.onNodeWithContentDescription("Seven-day macro graph")
            chart.performTouchInput {click(Offset(14.dp.toPx()+(width-28.dp.toPx())*3f/6f,height/2f))}
            compose.waitForIdle();waitText("3 days ago")
            assertTrue(chart.fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("Selected ${today.minusDays(3)}"))
            shot("03-diary-day-selected")
            compose.onNodeWithText("Jump to today").performClick();waitText("Today")

            compose.onNodeWithText("Nutritional breakdown").performScrollTo().performClick();compose.waitForIdle()
            compose.onNodeWithContentDescription("Day macro breakdown pie chart").performScrollTo();shot("04-breakdown")
            compose.onAllNodesWithText("Protein").filterToOne(hasClickAction()).performClick();compose.waitForIdle()
            compose.onNodeWithText("Protein by food").performScrollTo();shot("05-protein-by-food")

            compose.onNodeWithText("Nutritional breakdown").performScrollTo().performClick();compose.waitForIdle()
            // Meals follow the summary directly; empty ones offer their usual foods inline.
            compose.onNodeWithContentDescription("Add to Breakfast").performScrollTo();shot("06-meals")

            compose.onNodeWithContentDescription("Add to Lunch").performScrollTo().performClick();compose.waitForIdle()
            waitText("Add food");shot("08-add-food")
            compose.onNodeWithText("Fast food").performClick();compose.waitForIdle();waitText("Big Mac");shot("09-fast-food")
            compose.onNodeWithContentDescription("Search foods").performTextInput("big mac");compose.waitForIdle();shot("10-search-big-mac")
            compose.onAllNodesWithText("Big Mac").onFirst().performClick();waitText("Portion");shot("11-portion")
            compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()};compose.waitForIdle()
            compose.onNodeWithContentDescription("Search foods").performTextClearance()
            compose.onNodeWithText("Recent").performClick();compose.waitForIdle();compose.onAllNodesWithText("Custom").onFirst().performClick();waitText("Nutrition label");shot("12-custom-food")
            compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()};compose.waitForIdle()

            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName,android.Manifest.permission.CAMERA)
            compose.onNodeWithContentDescription("Scan barcode").performClick()
            device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text("Scan barcode")),8000);Thread.sleep(2500)
            device.takeScreenshot(File(out,"13-scanner.png"))
            device.findObject(androidx.test.uiautomator.By.text("Type number"))?.click();Thread.sleep(800)
            device.takeScreenshot(File(out,"14-scanner-keypad.png"))
            device.pressBack();Thread.sleep(300);device.pressBack();Thread.sleep(800)
        } catch(e:Throwable) {shot("failure");throw e} finally {
            seeded.forEach {e->runCatching {repo.deleteEntry(e.id)}}
            listOf(oats,yoghurt,chicken,rice,wrap,apple,salmon).forEach {runCatching {repo.deleteFood(it.id)}}
            repo.state.value.entries.filter {it.food.source==FoodSource.RESTAURANT}.forEach {repo.deleteEntry(it.id)}
            store.update {saved};store.saveWeights(weights)
        }
        Unit
    }
}
