package io.github.ebziboi.delta.nutrition

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
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
import java.time.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class BetaFoodUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val store=SettingsStore(context)
    private val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val out=File(context.filesDir,"beta3-review").apply {mkdirs()}
    private fun click(text:String) {
        val navigation=text in listOf("Progress","Diary","History")
        if(navigation && compose.onAllNodesWithText(text,ignoreCase=true).fetchSemanticsNodes().isEmpty()) {
            device.swipe(18,device.displayHeight*35/100,18,device.displayHeight*65/100,30);compose.waitForIdle()
        }
        compose.waitUntil(10000) {compose.onAllNodesWithText(text,ignoreCase=true).fetchSemanticsNodes().isNotEmpty()}
        val n=compose.onAllNodesWithText(text,ignoreCase=true).onFirst()
        runCatching {n.performScrollTo()}
        if(!navigation && n.fetchSemanticsNode().boundsInWindow.bottom>device.displayHeight*.75f) {
            device.swipe(18,(device.displayHeight*.75f).toInt(),18,(device.displayHeight*.4f).toInt(),25);compose.waitForIdle()
        }
        n.performClick();compose.waitForIdle()
    }
    private fun shot(name:String) {compose.waitForIdle();device.takeScreenshot(File(out,"$name.png"))}
    @Test fun homeNutritionDetailsReturnHomeAndDiaryChartsStillReturnDiary()=runBlocking {
        val saved=store.settings.first();val weights=store.weights.first();val prefs=context.getSharedPreferences("home-chart",0);val prior=prefs.getString("series",null)
        val repo=(context.applicationContext as DeltaApp).nutrition
        val food=Food(name="Scale review",per100=mapOf(Nutrient.ENERGY to 2000.0))
        try {
            val today=LocalDate.now();val time=today.atTime(8,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            store.saveWeights(listOf(WeightEntry(time,90.0,true,"beta3-home")))
            repo.log((0..6).map {DiaryEntry(day=today.minusDays(it.toLong()),meal=Meal.LUNCH,food=food,amount=if(it==0) 500.0 else 100.0)})
            prefs.edit().putString("series","WEIGHT,BODY_FAT,NUTRITION").commit()
            for(style in DesignStyle.entries) {
                store.update {it.copy(onboardingDone=true,motion=false,appearance=style.defaults().copy(reduceMotion=true),homeCardOrder=listOf("trend")+CardLayouts.home.filterNot {id->id=="trend"})}
                compose.activityRule.scenario.recreate()
                val chart=compose.onNodeWithContentDescription("Combined trends graph");chart.performScrollTo()
                assertTrue(chart.fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("scale ceiling 2666."))
                shot("$style-capped-chart")
                click("Nutrition details");compose.onNodeWithText("Nutrition trends",ignoreCase=true).assertExists()
                compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()};compose.waitForIdle()
                compose.onNodeWithContentDescription("Combined trends graph").assertExists()
                compose.onNodeWithText("Daily nutrition",ignoreCase=true).assertDoesNotExist()
                click("Nutrition details");compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()}
                compose.onNodeWithContentDescription("Combined trends graph").assertExists()
            }
            click("Diary");click("Charts")
            compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()};compose.waitForIdle();compose.onNodeWithText("Daily nutrition",ignoreCase=true).assertExists()
            // Store selection uses a filter menu, with no live network request needed to select it.
            compose.onNodeWithContentDescription("Add food").performClick();click("Search");click("All stores");click("Tesco")
            compose.onNodeWithText("Tesco").assertExists();shot("store-filter")
            context.startActivity(io.github.ebziboi.delta.widget.FoodBitmapWidget.launchIntent(context,"search"))
            compose.waitUntil(10000) {compose.activity.intent.getStringExtra("food_route")=="search"}
            compose.waitForIdle();click("Custom food")
            compose.onNodeWithText("Food name").performTextReplacement("Unsaved widget-link draft")
            compose.activityRule.scenario.recreate();compose.waitForIdle()
            compose.onNodeWithText("Unsaved widget-link draft").assertExists()
        } finally {
            repo.state.value.entries.filter {it.food.id==food.id}.forEach {repo.deleteEntry(it.id)};repo.deleteFood(food.id)
            store.update {saved};store.saveWeights(weights)
            prefs.edit().apply {if(prior==null) remove("series") else putString("series",prior)}.commit()
        }
        Unit
    }
    @Test fun swipingWeekChangesDatesWhileOtherHorizontalSwipesChangeMetrics()=runBlocking {
        val saved=store.settings.first();val weights=store.weights.first();val today=LocalDate.now();val zone=ZoneId.systemDefault()
        try {
            store.saveWeights((0..35).map {WeightEntry(today.minusDays(it.toLong()).atTime(8,0).atZone(zone).toInstant().toEpochMilli(),90+it*.1,true,"rhythm-$it")})
            store.update {it.copy(onboardingDone=true,motion=false,homeCardOrder=listOf("rate")+CardLayouts.home.filterNot {id->id=="rate"})}
            compose.activityRule.scenario.recreate();click("Weekly rate")
            compose.onNodeWithContentDescription("Next week").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Swipe week").performTouchInput {swipeRight(durationMillis=650)}
            compose.waitForIdle();compose.onNodeWithContentDescription("Next week").assertIsEnabled()
            compose.onNodeWithText("How your pace is changing").assertExists()
            compose.onNodeWithContentDescription("Previous day").assertDoesNotExist()
            compose.onNodeWithContentDescription("Next day").assertDoesNotExist()
            val swipeTarget=compose.onNodeWithText("How your pace is changing");swipeTarget.performScrollTo()
            for(step in 0..7) {
                val bounds=swipeTarget.fetchSemanticsNode().boundsInWindow
                if(bounds.height>8 && bounds.top>device.displayHeight*.15f && bounds.bottom<device.displayHeight*.72f) break
                device.swipe(18,(device.displayHeight*.78f).toInt(),18,(device.displayHeight*.42f).toInt(),25);compose.waitForIdle()
            }
            swipeTarget.assertIsDisplayed()
            shot("rhythm-before-page-swipe")
            swipeTarget.performTouchInput {swipeLeft(durationMillis=650)}
            shot("rhythm-after-page-swipe")
            compose.waitForIdle();compose.onNodeWithText("A clearer daily signal").assertExists()
            shot("rhythm-week")
        } finally {store.update {saved};store.saveWeights(weights)}
    }
}
