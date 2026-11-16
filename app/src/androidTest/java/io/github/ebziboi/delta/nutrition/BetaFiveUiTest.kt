package io.github.ebziboi.delta.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.ui.*
import org.junit.Assert.*
import io.github.ebziboi.delta.DeltaApp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class BetaFiveUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun holdSlideAndReleaseChoosesTheHoveredDateWindow() {
        val today=LocalDate.now()
        val series=listOf(RecordedSeries("weight","Weight",androidx.compose.ui.graphics.Color.Red,(0..29).map {RecordedValue(today.minusDays(it.toLong()),90.0+it)},{"$it"}))
        compose.setContent {DeltaTheme(AppSettings()) {Column(Modifier.fillMaxSize()) {RecordedChart(series,today.minusDays(29),today,"Test date graph")}}}
        val dot=compose.onNodeWithContentDescription("Chart date view")
        dot.performTouchInput {down(center);advanceEventTime(700);moveTo(Offset(center.x,height*2.5f),300);up()}
        compose.waitForIdle()
        dot.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Weekly"))
        val state=compose.onNodeWithContentDescription("Test date graph").fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertTrue(state,state.contains("7 recorded days"));assertTrue(state,state.contains("Selected $today"))
        dot.performClick();compose.onNodeWithText("Daily").performClick();compose.waitForIdle()
        assertTrue(compose.onNodeWithContentDescription("Test date graph").fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("1 recorded days"))
        compose.onNodeWithContentDescription("Zoom in").assertDoesNotExist()
    }
    @Test fun periodMenuOpensUpwardsNearTheBottomOfTheScreen() {
        var selection by mutableStateOf(ChartPeriod.MONTHLY)
        compose.setContent {DeltaTheme(AppSettings()) {Column(Modifier.fillMaxSize()) {Spacer(Modifier.weight(1f));Box(Modifier.align(androidx.compose.ui.Alignment.End)) {ChartPeriodControl(selection) {selection=it}}}}}
        val dot=compose.onNodeWithContentDescription("Chart date view")
        dot.performTouchInput {down(center);advanceEventTime(700);moveTo(Offset(center.x,-height*1.5f),300);up()}
        compose.waitForIdle();assertEquals(ChartPeriod.WEEKLY,selection)
    }
    @Test fun selectedCalendarDateControlsWeeklyGraphAndGraphCanSelectAnotherDay() {
        val today=LocalDate.now();val values=(0..6).map {RecordedValue(today.minusDays(it.toLong()),90.0+it)}
        var selected by mutableStateOf(today.minusDays(3))
        compose.setContent {DeltaTheme(AppSettings()) {RecordedChart(listOf(RecordedSeries("w","Weight",androidx.compose.ui.graphics.Color.Red,values,{"$it"})),today.minusDays(6),today,"Weekly graph",selectedDay=selected,onSelected={if(it!=null) selected=it},periodPicker=false,connectDots=false)}}
        val chart=compose.onNodeWithContentDescription("Weekly graph")
        assertTrue(chart.fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("Selected ${today.minusDays(3)}"))
        chart.performTouchInput {click(Offset(width.toFloat()-1,center.y))};compose.waitForIdle()
        assertEquals(today,selected)
    }
    @Test fun forecastCardsReorderAndRhythmGraphsRenderInBothThemes()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=SettingsStore(context);val settings=store.settings.first();val weights=store.weights.first()
        val prefs=context.getSharedPreferences("detail-card-order",0);val old=prefs.getString("forecast",null)
        val holder=ViewModelStore();lateinit var vm:AppViewModel
        var metric by mutableStateOf(DetailMetric.GOAL);var theme by mutableStateOf(DesignStyle.GLASS)
        val today=LocalDate.now();val app=context.applicationContext as DeltaApp
        val folder=File(context.filesDir,"beta5-review").apply {mkdirs()}
        fun shot(name:String) {compose.waitForIdle();UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(folder,"$name.png"))}
        try {
            prefs.edit().remove("forecast").commit()
            store.update {it.copy(targetKg=80.0,motion=false)}
            store.saveWeights(listOf(0L,2L,3L,5L,6L,8L,10L,13L).map {WeightEntry(today.minusDays(it).atTime(10,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),90+it*.1,true,"forecast-$it")})
            compose.runOnUiThread {vm=ViewModelProvider(holder,ViewModelProvider.AndroidViewModelFactory(app))[AppViewModel::class.java]}
            compose.setContent {val current=AppSettings(targetKg=80.0,motion=false,appearance=theme.defaults().copy(reduceMotion=true));DeltaTheme(current) {key(metric) {MetricDetailScreen(vm,current,metric,PaddingValues(0.dp),{})}}}
            compose.waitUntil(10000) {compose.onAllNodesWithContentDescription("Forecast summary card").fetchSemanticsNodes().isNotEmpty()}
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
            val signals=compose.onNodeWithContentDescription("Recent signals card")
            val action=signals.fetchSemanticsNode().config[SemanticsActions.CustomActions].first {it.label=="Move earlier"}
            compose.runOnIdle {assertTrue(action.action())};compose.waitForIdle()
            assertTrue(prefs.getString("forecast","")!!.startsWith("summary,signals,path"))
            for(style in DesignStyle.entries) {
                compose.runOnIdle {theme=style;metric=DetailMetric.GOAL};compose.waitForIdle()
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2);shot("$style-forecast")
                compose.onNodeWithText("Last 7 days",substring=true).assertExists()
                compose.onNodeWithText("Last 14 days",substring=true).assertExists()
                for(m in listOf(DetailMetric.RATE,DetailMetric.AVERAGE,DetailMetric.STREAK)) {
                    compose.runOnIdle {metric=m};compose.waitForIdle()
                    val graph=compose.onNodeWithContentDescription("${m.label} weekly graph")
                    graph.performScrollTo().assertExists();shot("$style-$m-week")
                }
            }
        } finally {
            compose.runOnUiThread {holder.clear()};store.update {settings};store.saveWeights(weights)
            prefs.edit().apply {if(old==null) remove("forecast") else putString("forecast",old)}.commit()
        }
    }
    @Test fun nutritionCardsReorderAndPieChoicesPersist() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("detail-card-order",0);val old=prefs.getString("nutrition",null)
        val piePrefs=context.getSharedPreferences("nutrition-pie",0);val oldPie=piePrefs.all.toMap()
        prefs.edit().remove("nutrition").commit();piePrefs.edit().clear().commit()
        var theme by mutableStateOf(DesignStyle.GLASS)
        val state=NutritionState(entries=listOf(DiaryEntry(day=LocalDate.now(),meal=Meal.LUNCH,food=Food(name="Lunch",per100=mapOf(Nutrient.ENERGY to 500.0,Nutrient.PROTEIN to 20.0,Nutrient.CARBS to 30.0,Nutrient.FAT to 10.0)),amount=100.0)),loaded=true)
        try {
            compose.setContent {DeltaTheme(AppSettings(appearance=theme.defaults().copy(reduceMotion=true))) {NutritionChartsPage(state,emptyList(),PaddingValues(0.dp),{})}}
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
            val card=compose.onNodeWithContentDescription("Nutrient balance card");card.performScrollTo()
            val action=card.fetchSemanticsNode().config[SemanticsActions.CustomActions].first {it.label=="Move earlier"}
            compose.runOnIdle {assertTrue(action.action())}
            compose.waitForIdle();assertTrue(prefs.getString("nutrition","")!!.startsWith("balance,intake"))
            compose.onNodeWithContentDescription("Choose pie nutrients").performScrollTo().performClick()
            compose.onAllNodes(isToggleable())[3].performClick()
            compose.onNodeWithText("Done").performClick()
            compose.waitForIdle();assertTrue(piePrefs.getBoolean("mass",false));assertTrue(piePrefs.getString("nutrients","")!!.contains("FIBRE"))
            compose.onNodeWithText("Fibre · Unknown").assertExists()
            compose.onNodeWithContentDescription("Nutrient balance pie chart").assertExists()
            for(style in DesignStyle.entries) {
                compose.runOnIdle {theme=style};compose.onNodeWithContentDescription("Nutrient balance pie chart").performScrollTo();compose.waitForIdle()
                val folder=File(context.filesDir,"beta5-review").apply {mkdirs()}
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(folder,"$style-nutrition.png"))
            }
        } finally {
            prefs.edit().apply {if(old==null) remove("nutrition") else putString("nutrition",old)}.commit()
            piePrefs.edit().clear().apply {oldPie.forEach {(k,v)->when(v) {is String->putString(k,v);is Boolean->putBoolean(k,v)}}}.commit()
        }
    }
}
