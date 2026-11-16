package io.github.ebziboi.delta.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ChartInteractionTest {
    @get:Rule val compose=createComposeRule()
    private val today=LocalDate.now()
    private fun entry(day:LocalDate=today)=DiaryEntry(day=day,meal=Meal.LUNCH,food=Food(name="One meal",per100=mapOf(Nutrient.ENERGY to 500.0)),amount=100.0)
    @Test fun intakeStartsAtTodayAndOnlyDrawsRecordedFood() {
        val weights=(0..13).map {WeightEntry(today.minusDays(it.toLong()).atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),90.0+it,false,"$it")}
        compose.setContent {DeltaTheme(AppSettings()) {NutritionChartsPage(NutritionState(entries=listOf(entry()),loaded=true),weights,PaddingValues(0.dp),{})}}
        val chart=compose.onNodeWithContentDescription("Daily intake graph")
        chart.performScrollTo()
        compose.waitUntil(5000) {chart.fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("Selected $today")}
        val state=chart.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertTrue(state.contains("Energy: 1 recorded days"));assertTrue(state.contains("to $today"))
        compose.onNodeWithText("Compare weight").assertDoesNotExist()
        chart.performTouchInput {click(Offset(width*.15f,height*.5f))}
        assertTrue(chart.fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("Selected $today"))
    }
    @Test fun combinedTogglesAllowAllThreeAndKeepTheirChoice() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("home-chart",0);val old=prefs.getString("series",null)
        val weights=listOf(WeightEntry(today.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),91.0,false,"weight"),
            WeightEntry(today.atTime(13,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),92.0,false,"later-weight"))
        val fat=listOf(BodyFatEntry(weights.first().epochMs,25.0,false,"fat"))
        var show by mutableStateOf(true)
        prefs.edit().putString("series",HomeSeries.WEIGHT.name).commit()
        try {
            compose.setContent {DeltaTheme(AppSettings()) {if(show) Column(Modifier.verticalScroll(rememberScrollState())) {HomeTrends(weights,fat,NutritionState(entries=listOf(entry()),loaded=true),AppSettings(),{})}}}
            compose.onNodeWithText("Body fat").performClick();compose.onNodeWithText("Nutrition").performClick()
            compose.onAllNodes(isToggleable() and isOn()).assertCountEquals(3)
            val chart=compose.onNodeWithContentDescription("Combined trends graph");chart.performScrollTo()
            assertTrue(chart.fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("Energy: 1 recorded days"))
            compose.onNodeWithText("Weight · ${Units.weight(92.0,WeightUnit.KG)}").assertExists()
            val links=listOf("Weight details","Body composition","Nutrition details")
            compose.onNodeWithText("Nutrition details").performScrollTo();compose.waitForIdle()
            val bounds=links.map {compose.onNodeWithText(it).fetchSemanticsNode().boundsInWindow}
            assertTrue("Detail links remain in a vertical stack",bounds.zipWithNext().all {(a,b)->b.top>=a.bottom})
            compose.onNodeWithText("Nutrition").performScrollTo().performClick();compose.waitForIdle()
            compose.onNodeWithText("Nutrition details").assertExists()
            compose.onNodeWithText("Body fat").performScrollTo().performClick();compose.waitForIdle()
            compose.onNodeWithText("Body composition").assertExists();compose.onNodeWithText("Weight details").assertExists()
            compose.onNodeWithText("Body fat").performClick();compose.onNodeWithText("Nutrition").performClick();compose.waitForIdle()
            compose.runOnIdle {show=false};compose.waitForIdle();compose.runOnIdle {show=true};compose.waitForIdle()
            compose.onAllNodes(isToggleable() and isOn()).assertCountEquals(3)
        } finally {prefs.edit().apply {if(old==null) remove("series") else putString("series",old)}.commit()}
    }
    @Test fun panSnapsToLatestVisibleRecordingAndVerticalDragStaysWithPage() {
        val recorded=(0..29 step 2).map {today.minusDays(it.toLong())}
        compose.setContent {DeltaTheme(AppSettings()) {NutritionChartsPage(NutritionState(entries=recorded.map {entry(it)},loaded=true),emptyList(),PaddingValues(0.dp),{})}}
        val chart=compose.onNodeWithContentDescription("Daily intake graph");chart.performScrollTo()
        chart.performTouchInput {pinch(Offset(width*.4f,height*.5f),Offset(width*.6f,height*.5f),Offset(width*.05f,height*.5f),Offset(width*.95f,height*.5f),800)}
        chart.performTouchInput {swipeRight(durationMillis=650)}
        compose.waitForIdle()
        val state=chart.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        val dates=Regex("(\\d{4}-\\d{2}-\\d{2}) to (\\d{4}-\\d{2}-\\d{2})").find(state)!!
        val first=LocalDate.parse(dates.groupValues[1]);val last=LocalDate.parse(dates.groupValues[2])
        val latest=recorded.filter {it in first..last}.maxOrNull()
        assertTrue("$state must select the latest recorded visible day",state.contains("Selected $latest"))
        chart.performTouchInput {swipeUp(durationMillis=600)};compose.waitForIdle()
        assertEquals(state,chart.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        compose.onNodeWithText("Reset zoom").performScrollTo().performClick();compose.waitForIdle()
        assertTrue(chart.fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("Selected $today"))
    }
}
