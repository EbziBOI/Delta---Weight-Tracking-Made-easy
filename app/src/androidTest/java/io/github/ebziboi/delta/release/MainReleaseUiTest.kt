package io.github.ebziboi.delta.release

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.ebziboi.delta.*
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.nutrition.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class MainReleaseUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val store=SettingsStore(context)
    private fun waitFor(text:String) {compose.waitUntil(12000) {compose.onAllNodesWithText(text,ignoreCase=true).fetchSemanticsNodes().isNotEmpty()}}
    private fun back() {compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()};compose.waitForIdle()}
    private fun tile(text:String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text,ignoreCase=true))
        compose.onNodeWithText(text,ignoreCase=true).performSemanticsAction(SemanticsActions.OnClick) {assertTrue(it())}
        compose.waitForIdle()
    }
    private fun shot(name:String) {
        compose.waitForIdle();val dir=File(context.filesDir,"main43-review").apply {mkdirs()}
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(dir,"$name.png"))
    }
    @Test fun actualScreensRetainChartChoicesAndAndroidBackAcrossBothThemes()=runBlocking {
        val app=context.applicationContext as DeltaApp;val saved=store.settings.first();val weights=store.weights.first();val fat=store.bodyFat.first()
        val prefs=context.getSharedPreferences("home-chart",0);val savedPrefs=prefs.all.toMap()
        val today=LocalDate.now();val zone=ZoneId.systemDefault()
        fun time(back:Long)=today.minusDays(back).atTime(10,0).atZone(zone).toInstant().toEpochMilli()
        val food=Food(name="Main release chart fixture",per100=mapOf(Nutrient.ENERGY to 430.0,Nutrient.PROTEIN to 25.0,Nutrient.CARBS to 45.0,Nutrient.FAT to 15.0))
        try {
            app.nutrition.state.first {it.loaded}
            app.nutrition.log((0L..6L).map {DiaryEntry(day=today.minusDays(it),meal=Meal.LUNCH,food=food,amount=100.0+it)})
            store.saveBetaMeasurements((0L..239L step 2).map {WeightEntry(time(it),90+it*.03,true,"main43-$it")},
                (0L..239L step 3).map {BodyFatEntry(time(it),25+it*.01,true,"main43-fat-$it")})
            for(style in DesignStyle.entries) {
                prefs.edit().clear().putString("series","WEIGHT,BODY_FAT,NUTRITION").commit()
                store.update {it.copy(onboardingDone=true,motion=false,haptics=false,appChart=ChartStyle.DOTS,targetKg=80.0,
                    appearance=style.defaults().copy(reduceMotion=true),homeCardOrder=listOf("trend")+CardLayouts.home.filterNot {id->id=="trend"})}
                compose.activityRule.scenario.recreate();waitFor("Trends")
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
                val dot=compose.onNodeWithContentDescription("Chart date view")
                dot.performTouchInput {down(center);advanceEventTime(650);moveTo(Offset(center.x,height*2.5f),250)}
                compose.waitForIdle()
                dot.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Weekly"))
                val cards=compose.onAllNodes(hasContentDescription("card",substring=true)).fetchSemanticsNodes()
                assertFalse("Holding the chart control must not reorder the card",cards.any {it.config.getOrElse(SemanticsProperties.StateDescription) {""}.startsWith("Moving")})
                shot("$style-held-date-menu")
                dot.performTouchInput {up()};compose.waitForIdle()
                val window=compose.onNodeWithContentDescription("Chart time window")
                window.performTouchInput {down(Offset(width*.12f,center.y));advanceEventTime(650);moveTo(Offset(width*.37f,center.y),200)}
                compose.waitForIdle()
                window.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Held 1Y"))
                assertTrue(compose.onNodeWithContentDescription("Combined trends graph").fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains(today.minusYears(1).plusDays(1).toString()))
                shot("$style-held-window")
                window.performTouchInput {up()};compose.onNodeWithText("2M").performClick()
                val graph=compose.onNodeWithContentDescription("Combined trends graph")
                graph.performTouchInput {doubleClick()};compose.waitForIdle()
                assertTrue(graph.fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("Trend"))
                shot("$style-home-weekly")
                compose.activityRule.scenario.recreate();waitFor("Trends")
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
                compose.onNodeWithContentDescription("Chart date view").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Weekly"))
                assertTrue(compose.onNodeWithContentDescription("Combined trends graph").fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("Trend"))
                tile("Weight details");waitFor("Weight trend")
                compose.onNodeWithText("Back").assertDoesNotExist();shot("$style-measurement-calendar")
                back();waitFor("Trends")
                tile("Nutrition details");waitFor("Nutrition trends")
                compose.onNodeWithText("Back").assertDoesNotExist();shot("$style-nutrition-details")
                back();waitFor("Trends")
                tile("Weekly rate");waitFor("Your rhythm")
                val weekly=compose.onNodeWithContentDescription("Weekly rate weekly graph")
                weekly.performScrollTo();compose.waitForIdle()
                val before=weekly.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
                weekly.performTouchInput {swipeRight(durationMillis=600)};compose.waitForIdle()
                val after=weekly.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
                assertNotEquals("The rhythm graph must scroll to a different week",before,after)
                assertTrue(after,after.contains("Selected"));shot("$style-rhythm-history")
                weekly.performTouchInput {doubleClick()};compose.waitForIdle()
                assertTrue(weekly.fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("Trend"))
                back();waitFor("Trends")
                tile("Goal by");waitFor("Your forecast")
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Recent signals"))
                compose.onNodeWithText("Last 30 days",substring=true).assertExists();shot("$style-forecast-signals")
                back();waitFor("Trends")
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
                compose.onNode(hasScrollToIndexAction()).performTouchInput {swipeDown(durationMillis=300)}
                compose.onNodeWithText("Diary").performClick();waitFor("Daily nutrition")
                val macros=compose.onNodeWithContentDescription("Seven-day macro graph",useUnmergedTree=true)
                macros.assertExists();val state=macros.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
                for(label in listOf("Protein","Carbohydrate","Fat")) assertTrue(state,state.contains("$label: 7 recorded days"))
                shot("$style-diary-macros")
                compose.onNodeWithText("Charts").performScrollTo().performClick();waitFor("Nutrition trends")
                back();waitFor("Daily nutrition")
                compose.onNodeWithText("Progress").performClick();waitFor("Trends")
            }
        } catch(e:Throwable) {shot("failure");throw e} finally {
            app.nutrition.state.value.entries.filter {it.food.id==food.id}.forEach {app.nutrition.deleteEntry(it.id)}
            app.nutrition.deleteFood(food.id);store.saveBetaMeasurements(weights,fat);store.update {saved}
            prefs.edit().clear().apply {savedPrefs.forEach {(k,v)->when(v) {is String->putString(k,v);is Boolean->putBoolean(k,v);is Int->putInt(k,v);is Long->putLong(k,v);is Float->putFloat(k,v)}}}.commit()
        }
    }
}
