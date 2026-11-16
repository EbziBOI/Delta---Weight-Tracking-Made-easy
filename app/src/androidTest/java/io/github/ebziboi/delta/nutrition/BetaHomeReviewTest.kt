package io.github.ebziboi.delta.nutrition

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.ebziboi.delta.*
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.icons.LauncherIcons
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class BetaHomeReviewTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun reviewCombinedHomeAcrossThemesAndPrepareIconUpdateCheck()=runBlocking {
        val store=SettingsStore(context);val app=context.applicationContext as DeltaApp
        val saved=store.settings.first();val weights=store.weights.first();val fat=store.bodyFat.first()
        val prefs=context.getSharedPreferences("home-chart",0);val old=prefs.getString("series",null)
        val today=LocalDate.now();val zone=ZoneId.systemDefault()
        fun time(days:Long)=today.minusDays(days).atTime(10,0).atZone(zone).toInstant().toEpochMilli()
        val food=Food(name="Home review fixture",per100=mapOf(Nutrient.ENERGY to 500.0,Nutrient.PROTEIN to 20.0,Nutrient.CARBS to 50.0,Nutrient.FAT to 15.0))
        val dir=File(context.filesDir,"chart-review").apply {mkdirs()}
        fun openTile(text:String,title:String) {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text,ignoreCase=true))
            compose.onNodeWithText(text,ignoreCase=true).performSemanticsAction(SemanticsActions.OnClick) {action->assertTrue(action())}
            compose.waitUntil(5000) {compose.onAllNodesWithText(title,ignoreCase=true).fetchSemanticsNodes().isNotEmpty()}
        }
        try {
            prefs.edit().putString("series","WEIGHT,BODY_FAT,NUTRITION").commit()
            app.nutrition.state.first {it.loaded}
            app.nutrition.log(listOf(0L,3L,12L).map {DiaryEntry(day=today.minusDays(it),meal=Meal.LUNCH,food=food,amount=100.0+it)})
            store.saveBetaMeasurements(listOf(0L,2L,6L,14L,23L).map {WeightEntry(time(it),91+it*.12,true,"home-$it")},
                listOf(1L,6L,14L).map {BodyFatEntry(time(it),25+it*.03,true,"fat-$it")})
            for(style in DesignStyle.entries) {
                store.update {it.copy(onboardingDone=true,motion=false,appearance=style.defaults().copy(reduceMotion=true),
                    homeCardOrder=listOf("trend")+CardLayouts.home.filterNot {id->id=="trend"})}
                compose.activityRule.scenario.recreate()
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
                compose.onNodeWithContentDescription("Combined trends graph").performScrollTo().assertExists()
                compose.onAllNodes(isToggleable() and isOn()).assertCountEquals(3)
                compose.waitForIdle()
                val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                device.takeScreenshot(File(dir,"$style-home.png"))
                compose.onNodeWithText("Nutrition details").performScrollTo();compose.waitForIdle();device.takeScreenshot(File(dir,"$style-details.png"))
                compose.onNodeWithText("Nutrition details").performClick()
                compose.onNodeWithContentDescription("Daily intake graph").performScrollTo();compose.waitForIdle();device.takeScreenshot(File(dir,"$style-intake.png"))
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
                compose.onNodeWithContentDescription("Nutrient balance pie chart").performScrollTo();compose.waitForIdle();device.takeScreenshot(File(dir,"$style-balance.png"))
                compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()}
                openTile("Goal by","Your forecast")
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3);compose.waitForIdle();device.takeScreenshot(File(dir,"$style-forecast.png"))
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
                compose.onNodeWithText("Back",ignoreCase=true).performClick()
                compose.waitUntil(5000) {compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty()}
                openTile("Weekly rate","Your rhythm")
                compose.onNodeWithContentDescription("Weekly rate weekly graph").performScrollTo();compose.waitForIdle();device.takeScreenshot(File(dir,"$style-rhythm.png"))
                compose.onNodeWithText("Back",ignoreCase=true).performScrollTo().performClick()
                compose.waitForIdle()
            }
        } finally {
            app.nutrition.state.value.entries.filter {it.food.id==food.id}.forEach {app.nutrition.deleteEntry(it.id)}
            app.nutrition.deleteFood(food.id);store.saveBetaMeasurements(weights,fat)
            store.update {saved}
            prefs.edit().apply {if(old==null) remove("series") else putString("series",old)}.commit()
        }
        // Leave a known, saved choice for the subsequent real APK replacement check.
        prefs.edit().putString("series","WEIGHT,BODY_FAT,NUTRITION").commit()
        store.update {it.copy(appIcon="Icon07")};LauncherIcons.apply(context,"Icon07")
        File(dir,"icon-before-update.txt").writeText(store.settings.first().appIcon)
    }
}
