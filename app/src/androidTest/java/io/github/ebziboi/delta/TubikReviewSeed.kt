package io.github.ebziboi.delta

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.nutrition.*
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Opt-in (`-e delta.seedReview true`): switches the installed app to Tubik and logs 60 days of meals
 * so charts can be reviewed by hand. Leaves the data in place; skipped in normal test runs.
 */
@RunWith(AndroidJUnit4::class)
class TubikReviewSeed {
    @Test fun seedTubikWithTwoMonthsOfMeals() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("delta.seedReview") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DeltaApp
        SettingsStore(app).update { it.withDesignStyle(DesignStyle.TUBIK, it.appearance ?: AppAppearance()) }
        val today = LocalDate.now()
        val meal = Food(id = "review-meal", name = "Review meal", per100 = mapOf(Nutrient.ENERGY to 100.0, Nutrient.PROTEIN to 6.0, Nutrient.CARBS to 12.0, Nutrient.FAT to 3.5))
        val repo = app.nutrition
        repo.reload()
        repo.state.value.entries.filter { it.id.startsWith("review-") }.forEach { repo.deleteEntry(it.id) }
        // Mostly 1,600–2,200 kcal with a few heavy days, and recent weeks a little higher than older ones.
        val days = (1L..60L).map { back ->
            val base = 1650 + (back * 37 % 9) * 60 + (if (back < 14) 150 else 0) + (if (back % 11 == 0L) 1300 else 0)
            DiaryEntry(id = "review-$back", day = today.minusDays(back), meal = Meal.DINNER, food = meal, amount = base / 1.0)
        }
        repo.log(days)
        Unit
    }
}
