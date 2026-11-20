package io.github.ebziboi.delta.nutrition

import androidx.compose.ui.geometry.Offset
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

/** The energy page with three weeks of realistic logs: balance chart, suggestions, BMI report and past-day budgets. */
@RunWith(AndroidJUnit4::class)
class EnergyTourTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val store=SettingsStore(context)
    private val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val out=File(context.filesDir,"energy-tour").apply {mkdirs()}
    private val app get()=context.applicationContext as DeltaApp
    private fun shot(name:String) {
        compose.waitForIdle()
        device.findObject(androidx.test.uiautomator.By.text("Wait"))?.click()
        Thread.sleep(300);device.takeScreenshot(File(out,"$name.png"))
    }
    private fun waitText(text:String) = compose.waitUntil(10000) {compose.onAllNodesWithText(text,substring=true,ignoreCase=true).fetchSemanticsNodes().isNotEmpty()}

    @Test fun energyTour()=runBlocking {
        val saved=store.settings.first();val weights=store.weights.first();val fat=store.bodyFat.first()
        val mix=Food("tour-energy-mix","Home cooking",per100=mapOf(Nutrient.ENERGY to 100.0,Nutrient.PROTEIN to 5.0,Nutrient.CARBS to 12.0,Nutrient.FAT to 3.5,Nutrient.FIBRE to 1.2))
        val today=LocalDate.now();val zone=ZoneId.systemDefault()
        val seeded=(0L..20L).flatMap {d->
            val day=today.minusDays(d)
            val weekend=day.dayOfWeek in setOf(DayOfWeek.FRIDAY,DayOfWeek.SATURDAY,DayOfWeek.SUNDAY)
            when(d) {
                6L -> emptyList()
                4L,11L -> listOf(DiaryEntry(day=day,meal=Meal.BREAKFAST,food=mix,amount=450.0))
                0L -> listOf(DiaryEntry(day=day,meal=Meal.BREAKFAST,food=mix,amount=500.0),DiaryEntry(day=day,meal=Meal.LUNCH,food=mix,amount=650.0))
                else -> {
                    val kcal=1950.0+(if(weekend) 650.0 else 0.0)+kotlin.math.sin(d*2.3)*220
                    listOf(DiaryEntry(day=day,meal=Meal.BREAKFAST,food=mix,amount=kcal*.25),DiaryEntry(day=day,meal=Meal.LUNCH,food=mix,amount=kcal*.35),DiaryEntry(day=day,meal=Meal.DINNER,food=mix,amount=kcal*.4))
                }
            }
        }
        val plans=context.getSharedPreferences("energy-day-plans",0)
        try {
            plans.edit().clear().commit()
            store.saveWeights((0..60).map {d->WeightEntry(today.minusDays(60L-d).atTime(7,30).atZone(zone).toInstant().toEpochMilli(),
                98.0-d*.05+kotlin.math.sin(d*1.7)*.35,true,"energy-tour-$d")})
            store.saveBodyFat(listOf(BodyFatEntry(today.minusDays(5).atTime(7,31).atZone(zone).toInstant().toEpochMilli(),26.0,true,"energy-tour-fat")))
            app.nutrition.log(seeded)
            store.update {it.copy(onboardingDone=true,motion=false,targetKg=85.0,targetEpochDay=null,appearance=DesignStyle.GLASS.defaults().copy(reduceMotion=true),
                energy=EnergyProfile(enabled=true,heightCm=180.0,birthYear=1990,sex=Sex.MALE,useHealthActivity=false,activityLevel=ActivityLevel.LIGHT,autoBudget=true))}
            compose.activityRule.scenario.recreate();compose.waitForIdle()
            compose.waitUntil(10000) {app.energy.days.value.size>=90}
            val days=app.energy.days.value
            assertTrue(days.last().isToday)
            assertTrue(days.count {it.counts}>=15)

            device.findObject(androidx.test.uiautomator.By.text("Wait"))?.click()
            compose.onNodeWithText("Diary").performClick();waitText("Daily nutrition")
            compose.onNodeWithText("Energy budget",ignoreCase=true).performScrollTo();shot("01-diary-today-card")
            // Past days have their own budget card.
            compose.onNodeWithContentDescription("Previous day").performClick();compose.onNodeWithContentDescription("Previous day").performClick();waitText("2 days ago")
            compose.onNodeWithText("Budget that day",ignoreCase=true).performScrollTo();shot("02-diary-past-card")
            compose.onNodeWithText("Budget that day",ignoreCase=true).performClick();waitText("Balance")
            shot("03-energy-top")
            compose.onNodeWithText("2W").performScrollTo()
            val chart=compose.onNodeWithContentDescription("Energy balance chart");chart.performScrollTo()
            chart.performTouchInput {click(Offset(width*.3f,height/2f))};compose.waitForIdle()
            assertTrue(chart.fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("Selected"))
            shot("04-balance-daily")
            compose.onNodeWithText("1M").performClick();compose.waitForIdle();shot("05-balance-month")
            compose.onNodeWithText("Running total").performClick();compose.waitForIdle();shot("06-balance-total")
            compose.onNodeWithText("Suggestions",ignoreCase=true).performScrollTo();shot("07-suggestions")
            compose.onNodeWithText("BMI & weight group",ignoreCase=true).performScrollTo();shot("08-bmi")
            compose.onNodeWithText("Lean mass").performScrollTo();shot("09-bmi-more")
            compose.onNodeWithText("How it's worked out",ignoreCase=true).performScrollTo();shot("10-how")
        } catch(e:Throwable) {shot("failure");throw e} finally {
            seeded.forEach {e->runCatching {app.nutrition.deleteEntry(e.id)}}
            runCatching {app.nutrition.deleteFood(mix.id)}
            plans.edit().clear().commit()
            store.update {saved};store.saveWeights(weights);store.saveBodyFat(fat)
        }
        Unit
    }
}
