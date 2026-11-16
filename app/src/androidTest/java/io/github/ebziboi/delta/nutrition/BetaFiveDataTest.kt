package io.github.ebziboi.delta.nutrition

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ebziboi.delta.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BetaFiveDataTest {
    @Test fun sparseSevenAndFourteenDaySignalsUseAllDatesAndExplainConfidence() {
        val today=LocalDate.of(2026,11,16)
        val data=listOf(0L,2L,3L,5L,6L,8L,10L,13L).associate {today.minusDays(it) to (90+it*.1)}
        val seven=recentPace(data,7,today);val fourteen=recentPace(data,14,today)
        assertEquals(5,seven.count);assertEquals(8,fourteen.count)
        assertEquals(-.7,seven.rate!!,.00001);assertEquals(-.7,fourteen.rate!!,.00001)
        assertEquals("Higher confidence",seven.reliability);assertEquals("Higher confidence",fourteen.reliability)
        assertTrue(fourteen.reason.contains("8 recorded dates"));assertTrue(fourteen.reason.contains("14 calendar days"))
        val pair=recentPace(mapOf(today to 90.0,today.minusDays(3) to 90.3),7,today)
        assertEquals(-.7,pair.rate!!,.00001);assertEquals("Low confidence",pair.reliability)
        assertTrue(pair.reason.contains("Two points"))
        assertNull(recentPace(mapOf(today to 90.0),7,today).rate)
        assertNull(recentPace(mapOf(today to Double.NaN,today.minusDays(1) to 90.0),7,today).rate)
    }
    @Test fun returnedMeasurementsSurviveRefreshWithoutDuplicatingHealthConnect() {
        val local=WeightEntry(100000L,91.0,true,"local-copy:beta-local:test")
        assertEquals(listOf(local),mergeReturnedWeights(emptyList(),listOf(local)))
        val hc=local.copy(id="health-id",own=false)
        assertEquals(listOf(hc),mergeReturnedWeights(listOf(hc),listOf(local)))
        val fat=BodyFatEntry(100000L,25.0,true,"local-copy:test")
        assertEquals(listOf(fat),mergeReturnedFat(emptyList(),listOf(fat)))
        assertEquals(listOf(fat.copy(id="health")),mergeReturnedFat(listOf(fat.copy(id="health")),listOf(fat)))
    }
    @Test fun pieComparesMassInTheSameUnitsAndArchivePreservesFoodProvenance() {
        assertEquals(1.0,nutrientMass(1000.0,Nutrient.SODIUM)!!,.00001)
        assertEquals(.00001,nutrientMass(10.0,Nutrient.VIT_D)!!,.00000001)
        assertNull(nutrientMass(100.0,Nutrient.ENERGY))
        val ingredient=Food(id="ingredient",name="Test food",per100=mapOf(Nutrient.ENERGY to 150.0,Nutrient.PROTEIN to 20.0),stores=listOf("Morrisons"),sourceUrl="https://groceries.morrisons.com/product",labelChecked=setOf(Nutrient.PROTEIN),favourite=true)
        val recipe=makeRecipe("Recipe",listOf(Ingredient(ingredient,200.0)),2.0,180.0,id="recipe")
        val entry=DiaryEntry(id="diary",day=LocalDate.now(),meal=Meal.DINNER,food=recipe,amount=90.0)
        val state=NutritionState(listOf(ingredient,recipe),listOf(entry),NutritionTargets(mapOf(Nutrient.ENERGY to 2100.0)),listOf(Fast("fast",100000L,16.0,200000L)),loaded=true)
        val loaded=NutritionArchive.parse(NutritionArchive.encode(state))
        assertEquals(state,loaded)
        val broken=NutritionArchive.encode(state);broken.getJSONArray("entries").put(broken.getJSONArray("entries").getJSONObject(0))
        try {NutritionArchive.parse(broken);fail("Duplicate diary IDs must be rejected")} catch(_:IllegalArgumentException) {}
    }
}
