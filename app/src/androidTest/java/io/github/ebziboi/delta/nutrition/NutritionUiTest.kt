package io.github.ebziboi.delta.nutrition

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.MainActivity
import io.github.ebziboi.delta.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class NutritionUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val app get()=context.applicationContext as DeltaApp
    private val store=SettingsStore(context)
    private val repo get()=app.nutrition
    @Test fun diaryRecipesTargetsChartsFastingAndPhotoReviewInBothThemes()= runBlocking {
        val saved=store.settings.first();val baseline=repo.state.first {it.loaded}
        val names=mutableListOf<String>()
        try {
            for(style in DesignStyle.entries) {
                store.update {it.copy(onboardingDone=true,motion=false,appearance=style.defaults().copy(reduceMotion=true))}
                compose.activityRule.scenario.recreate()
                compose.waitUntil(15000) {compose.onAllNodesWithText("Diary").fetchSemanticsNodes().isNotEmpty()}
                val fixture=Food(name="UI fixture ${style.name}",per100=mapOf(Nutrient.ENERGY to 380.0,Nutrient.PROTEIN to 13.0,Nutrient.CARBS to 60.0,Nutrient.FAT to 7.0,Nutrient.FIBRE to 10.0,Nutrient.IRON to 4.25),serving=40.0)
                names+=fixture.name;repo.saveFood(fixture)
                repo.log((1L..20L).map {DiaryEntry(day=LocalDate.now().minusDays(it),meal=Meal.BREAKFAST,food=fixture,amount=30.0+it)})
                click("Diary");shot("$style-diary")
                addFood();click("Custom food")
                val foodName="UI custom ${style.name}";names+=foodName
                field("Food name",foodName);field("Energy (kcal)","100");field("Protein (g)","10");field("Carbohydrate (g)","10");field("Fat (g)","3")
                field("Serving (g)","50");field("Pack (g)","200");click("Save food")
                field("Number of servings","2");click("Add food")
                compose.waitUntil(10000) {repo.state.value.entries.any {it.food.name==foodName && it.amount==100.0}}
                compose.onAllNodesWithText(foodName).onLast().performScrollTo().performClick()
                field("Amount (g)","125");click("Save entry")
                compose.waitUntil(10000) {repo.state.value.entries.any {it.food.name==foodName && it.amount==125.0}}
                click("Targets");field("Daily energy (kcal)","2000");click("% of energy");click("Save targets")
                compose.waitUntil(10000) {repo.state.value.targets.mode==MacroMode.PERCENT}
                click("Charts");click("Weekly");click("30 days");shot("$style-charts");back()
                click("Fasting");click("Start fast");compose.waitUntil(10000) {repo.state.value.fasts.any {it.end==null}}
                shot("$style-fasting");click("End fast")
                compose.onAllNodesWithText("End fast").onLast().performClick()
                compose.waitUntil(10000) {repo.state.value.fasts.none {it.end==null}};back()
                // Clear the scroll position by selecting the visible diary tab if needed.
                addFood();click("New recipe")
                val recipeName="UI recipe ${style.name}";names+=recipeName
                field("Recipe name",recipeName);field("Number of servings","2");field("Finished recipe weight (g)","200")
                click("Add ingredient");click(fixture.name)
                click("g");field("Amount (g)","100");click("Add ingredient")
                compose.onNodeWithText(recipeName).assertExists();click("Save recipe")
                compose.onNodeWithText(recipeName).assertExists();shot("$style-recipe");click("Add food")
                compose.waitUntil(10000) {repo.state.value.entries.any {it.food.name==recipeName}}
                val logged=repo.state.value.entries.last {it.food.name==recipeName}
                assertEquals(190.0,logged.nutrients.getValue(Nutrient.ENERGY),.001)
                click("Copy day")
                val copyCount=repo.state.value.entries.count {it.day==LocalDate.now().minusDays(1)}
                click("Copy $copyCount ${if(copyCount==1) "entry" else "entries"}")
                compose.waitUntil(10000) {repo.state.value.entries.count {it.day==LocalDate.now()}>=3}
                addFood();click("Photo")
                click("Claude");shot("$style-photo-consent")
                click("Add API key");compose.onNodeWithText("Vision model ID").assertExists();shot("$style-photo-settings");back()
                // Inject an API response fixture locally; this test sends no photo or authenticated request.
                compose.runOnIdle {ViewModelProvider(compose.activity)[NutritionViewModel::class.java].photoReview=PhotoNutrition.parse("""{"foods":[{"name":"UI photo review","grams":150,"per100":{"ENERGY":130,"PROTEIN":2.7,"CARBS":28,"FAT":0.3}}]}""")}
                names+="UI photo review"
                click("UI photo review");field("Portion (g)","200");click("Save review");shot("$style-photo-review")
                click("Log 1 reviewed food");compose.waitUntil(10000) {repo.state.value.entries.any {it.food.name=="UI photo review" && it.amount==200.0}}
                assertTrue(repo.state.value.entries.last {it.food.name=="UI photo review"}.food.estimated)
                shot("$style-diary-complete")
                // Remove this style's fixture so the next pass starts with predictable recent foods.
                repo.state.value.entries.filter {it.food.name in names}.forEach {repo.deleteEntry(it.id)}
                repo.state.value.foods.filter {it.name in names}.forEach {repo.deleteFood(it.id)}
                repo.state.value.fasts.filter {f->baseline.fasts.none {it.id==f.id}}.forEach {repo.deleteFast(it.id)}
                repo.saveTargets(baseline.targets)
            }
        } catch(e:Throwable) {shot("failure");throw e} finally {
            repo.state.value.entries.filter {it.food.name in names}.forEach {repo.deleteEntry(it.id)}
            repo.state.value.foods.filter {it.name in names}.forEach {repo.deleteFood(it.id)}
            repo.state.value.fasts.filter {f->baseline.fasts.none {it.id==f.id}}.forEach {repo.deleteFast(it.id)}
            repo.saveTargets(baseline.targets);store.update {saved}
        }
    }
    @Test fun lightThemesLargeTextAndWeightUnitsKeepControlsUsable() = runBlocking {
        org.junit.Assume.assumeTrue(android.os.Build.PRODUCT.startsWith("sdk_"))
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val fontScale=device.executeShellCommand("settings get system font_scale").trim().toFloatOrNull() ?: 1f
        val saved=store.settings.first();val originalWeights=store.weights.first();repo.state.first {it.loaded}
        val fixture=Food(name="Large text fixture",per100=mapOf(Nutrient.ENERGY to 100.0),serving=25.0)
        try {
            repo.saveFood(fixture);repo.log(listOf(DiaryEntry(day=LocalDate.now().minusDays(1),meal=Meal.BREAKFAST,food=fixture,amount=25.0)))
            device.executeShellCommand("settings put system font_scale 1.35")
            for(style in DesignStyle.entries) {
                store.update {it.copy(onboardingDone=true,motion=false,unit=WeightUnit.LB,appearance=style.defaults().copy(
                    backgroundStyle=BackgroundStyle.SOLID,backgroundBase=0xFFF8F8F8.toInt(),backgroundSecondary=0xFFF0F0F0.toInt(),reduceMotion=true))}
                compose.activityRule.scenario.recreate()
                compose.waitUntil(15000) {compose.onAllNodesWithText("History").fetchSemanticsNodes().isNotEmpty()}
                // The existing weight destinations and top-right Settings remain reachable.
                click("History");compose.onNodeWithContentDescription("Settings").performClick()
                click("Daily targets");compose.onNodeWithText("Macronutrients",ignoreCase=true).assertExists();back()
                compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()}
                click("Diary")
                compose.onAllNodesWithText("Breakfast",useUnmergedTree=true).onFirst().performScrollTo()
                    .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {action->
                        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>();assertTrue(action(layouts));assertFalse(layouts.any {it.didOverflowWidth})
                    }
                shot("$style-large-light")
                val appVm=compose.runOnIdle {ViewModelProvider(compose.activity)[io.github.ebziboi.delta.ui.AppViewModel::class.java]}
                kotlinx.coroutines.withTimeout(10000) {appVm.health.first {it!=null};appVm.syncing.first {!it}}
                val date=LocalDate.now().minusDays(1);val zone=java.time.ZoneId.systemDefault()
                val weights=listOf(69.5,70.0,71.0).mapIndexed {index,kg->WeightEntry((if(index==0) date.minusDays(1) else date).atTime(8+index,0).atZone(zone).toInstant().toEpochMilli(),kg,false,"nutrition-comparison-$index")}
                store.saveWeights(weights);kotlinx.coroutines.withTimeout(10000) {appVm.entries.first {it==weights}}
                click("Charts");compose.onNodeWithText("Weight: right axis (lb)",substring=true).assertExists()
                compose.onNodeWithText("${Units.weightShort(69.0,WeightUnit.LB)} – ${Units.weightShort(71.0,WeightUnit.LB)}").assertExists()
                shot("$style-large-chart");back()
                addFood();click("Custom food")
                compose.onNodeWithText("Pack (g)").performScrollTo().performClick()
                compose.waitUntil(10000) {compose.activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())==true}
                compose.waitUntil(10000) {
                    val view=compose.activity.window.decorView
                    val keyboardTop=device.displayHeight-(view.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.ime())?.bottom ?: 0)
                    compose.onNodeWithText("Pack (g)").fetchSemanticsNode().boundsInWindow.bottom<=keyboardTop+2
                }
                shot("$style-keyboard")
                device.pressBack()
                compose.waitUntil(10000) {compose.activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())!=true}
                back();click("Scan barcode")
                // The camera permission UI or the manual barcode fallback must be available.
                assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text("Barcode number")),10000))
                device.pressBack()
                compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()}
            }
        } catch(e:Throwable) {shot("large-failure");throw e} finally {
            repo.state.value.entries.filter {it.food.id==fixture.id}.forEach {repo.deleteEntry(it.id)}
            repo.deleteFood(fixture.id);store.update {saved};store.saveWeights(originalWeights)
            device.executeShellCommand("settings put system font_scale $fontScale")
        }
    }
    private fun click(text:String) {
        stage("Click $text")
        compose.waitUntil(10000) {compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
        val node=compose.onAllNodesWithText(text,useUnmergedTree=true).onFirst()
        runCatching {node.performScrollTo()}
        // Bring a low target clear of the floating dock with a real scroll gesture.
        // performScrollTo alone does not trigger the dock's user-scroll animation.
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        if(text !in listOf("Progress","Diary","History") && node.fetchSemanticsNode().boundsInWindow.bottom>device.displayHeight*.72f) {
            device.swipe(24,(device.displayHeight*.72f).toInt(),24,(device.displayHeight*.40f).toInt(),24)
            compose.waitForIdle()
        }
        node.performClick();compose.waitForIdle()
    }
    private fun field(label:String,text:String) {
        stage("Enter $label")
        compose.waitUntil(10000) {compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText(label).performScrollTo().performTextReplacement(text);compose.waitForIdle()
    }
    private fun addFood() {
        compose.waitUntil(10000) {compose.onAllNodesWithContentDescription("Add food").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Add food").performClick();compose.waitForIdle()
    }
    private fun stage(text:String) {File(context.filesDir,"nutrition-review").apply {mkdirs()}.resolve("step.txt").writeText(text)}
    private fun back() {compose.onNodeWithContentDescription("Back").performScrollTo().performClick();compose.waitForIdle()}
    private fun shot(name:String) {
        compose.waitForIdle()
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val directory=File(context.filesDir,"nutrition-review").apply {mkdirs()}
        device.takeScreenshot(File(directory,"$name.png"))
    }
}
