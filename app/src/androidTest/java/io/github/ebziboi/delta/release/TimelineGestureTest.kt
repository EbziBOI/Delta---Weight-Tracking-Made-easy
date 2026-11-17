package io.github.ebziboi.delta.release

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class TimelineGestureTest {
    @get:Rule val compose=createComposeRule()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val today=LocalDate.now()
    private fun series()=listOf(RecordedSeries("w","Weight",Color.Red,(0..239 step 2).map {RecordedValue(today.minusDays(it.toLong()),90+it*.03)},{"$it kg"}))
    @Test fun dotLivesInTheTitleRowAndHoldSlideUpdatesWhileStillPressed() {
        var selected by mutableStateOf("A")
        compose.setContent {DeltaTheme(AppSettings()) {Column {SegmentedControl(listOf("A","B","C"),selected,{it},Modifier.testTag("selector")) {selected=it};TextValue(selected)}}}
        val selector=compose.onNodeWithTag("selector")
        selector.performTouchInput {down(Offset(width*.15f,center.y));advanceEventTime(650);moveTo(Offset(width*.85f,center.y),200)}
        compose.waitForIdle();assertEquals("C",selected)
        selector.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Held C"))
        selector.performTouchInput {up()};compose.waitForIdle();assertEquals("C",selected)
        compose.onNodeWithText("C",useUnmergedTree=false).assertExists()
    }
    @Composable private fun TextValue(value:String) {androidx.compose.material3.Text("Selected $value")}
    @Test fun horizontalFlingSelectsTheRightmostRecordedBucketAndDoubleTapRemembersItsMode() {
        val id="release-gesture";context.getSharedPreferences("home-chart",0).edit().remove("profile.$id").commit()
        lateinit var profile:ChartController
        compose.setContent {DeltaTheme(AppSettings(motion=false)) {profile=rememberChartController(id);RecordedChart(series(),today.minusDays(239),today,"Gesture graph",controller=profile,header="Trends")}}
        val graph=compose.onNodeWithContentDescription("Gesture graph");val dot=compose.onNodeWithContentDescription("Chart date view")
        val title=compose.onNodeWithText("Trends").fetchSemanticsNode().boundsInWindow;val button=dot.fetchSemanticsNode().boundsInWindow
        assertTrue("The dot belongs at the card's title row",kotlin.math.abs(title.center.y-button.center.y)<4)
        assertTrue(button.left>title.right)
        graph.performTouchInput {swipeRight(durationMillis=600)};compose.waitForIdle()
        val state=graph.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        val dates=Regex("(\\d{4}-\\d{2}-\\d{2}) to (\\d{4}-\\d{2}-\\d{2})").find(state)!!
        val first=LocalDate.parse(dates.groupValues[1]);val last=LocalDate.parse(dates.groupValues[2])
        assertTrue("History must actually pan: $state",last<today)
        val expected=series().flatMap {it.values}.filter {it.day in first..last}.maxOf {it.day}
        assertTrue(state,state.contains("Selected $expected"))
        graph.performTouchInput {doubleClick()};compose.waitForIdle();assertEquals(ChartStyle.TREND,profile.mode)
        assertEquals(ChartStyle.TREND,ChartController(context,id,ChartStyle.DOTS).mode)
    }
    @Test fun weeklySelectionShowsCoverageAndWindowsDoNotChangeAveraging() {
        val id="release-weekly";context.getSharedPreferences("home-chart",0).edit().remove("profile.$id").commit()
        lateinit var profile:ChartController
        compose.setContent {DeltaTheme(AppSettings(motion=false)) {profile=rememberChartController(id);RecordedChart(series(),today.minusDays(239),today,"Average graph",controller=profile,header="Trends",weekStartsOn=1)}}
        compose.onNodeWithContentDescription("Chart date view").performClick();compose.onNodeWithText("Weekly").performClick();compose.waitForIdle()
        compose.onNodeWithText("2M").assertExists();compose.onNodeWithText("1Y").performClick();compose.waitForIdle()
        assertEquals(ChartPeriod.WEEKLY,profile.period);assertEquals("1Y",profile.window)
        compose.onNodeWithText("average ·",substring=true).assertExists()
        compose.onNodeWithContentDescription("Average graph").assert(SemanticsMatcher("weekly buckets") {it.config[SemanticsProperties.StateDescription].startsWith("Weekly")})
    }
    @Test fun theMasterHapticsSettingSilencesSharedButtonsAndCustomGestures() {
        val events=mutableListOf<HapticFeedbackType>();val fake=object:HapticFeedback {override fun performHapticFeedback(hapticFeedbackType:HapticFeedbackType) {events+=hapticFeedbackType}}
        var enabled by mutableStateOf(true)
        compose.setContent {CompositionLocalProvider(LocalHapticFeedback provides fake) {DeltaTheme(AppSettings(haptics=enabled)) {Column {
            HapticTextButton({}) {androidx.compose.material3.Text("Press")}
            SegmentedControl(listOf("One","Two"),"One",{it},Modifier.testTag("feedback-selector")) {}
        }}}}
        compose.onNodeWithText("Press").performClick();compose.waitForIdle();assertTrue(events.isNotEmpty())
        compose.runOnIdle {enabled=false;events.clear()};compose.waitForIdle()
        compose.onNodeWithText("Press").performClick()
        compose.onNodeWithTag("feedback-selector").performTouchInput {down(center);advanceEventTime(650);moveTo(Offset(width*.9f,center.y),200);up()}
        compose.waitForIdle();assertTrue("All app haptics must respect the master switch",events.isEmpty())
    }
    @Test fun verticalDragStartingOnTheGraphScrollsThePage() {
        compose.setContent {DeltaTheme(AppSettings(motion=false)) {Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(80.dp));RecordedChart(series(),today.minusDays(239),today,"Vertical graph",header="Trends");Spacer(Modifier.height(1000.dp))
        }}}
        val graph=compose.onNodeWithContentDescription("Vertical graph");val before=graph.fetchSemanticsNode().boundsInWindow.top
        graph.performTouchInput {swipeUp(durationMillis=500)};compose.waitForIdle()
        assertTrue(graph.fetchSemanticsNode().boundsInWindow.top<before-20)
    }
}
