package io.github.ebziboi.delta.nutrition

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.net.URI
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class BetaFoodFeaturesTest {
    private val today=LocalDate.now()
    private fun meal(day:LocalDate,kcal:Double?)=DiaryEntry(day=day,meal=Meal.LUNCH,food=Food(name="Meal",per100=kcal?.let {mapOf(Nutrient.ENERGY to it)} ?: emptyMap()),amount=100.0)
    @Test fun medianDailyScaleIgnoresMissingDatesUnknownLabelsAndIsolatedOutliers() {
        val entries=listOf(meal(today,1000.0),meal(today,1000.0),meal(today.minusDays(1),2000.0),meal(today.minusDays(2),100000.0),
            meal(today.minusDays(3),null),meal(today.minusDays(31),999999.0))
        val scale=nutritionScale(entries,today,Nutrient.ENERGY)
        assertEquals(3,scale.recordedDays);assertEquals(2000.0,scale.typical!!,.001)
        assertEquals(.75,scale.typical!!/scale.ceiling,.00001)
        assertTrue(100000.0>scale.ceiling)
        // All real values remain available; only their visual heights will be capped.
        assertEquals(100000.0,entries.single {it.day==today.minusDays(2)}.nutrients.getValue(Nutrient.ENERGY),.001)
    }
    @Test fun noDataAndExplicitZerosHaveFiniteUsefulScales() {
        val empty=nutritionScale(emptyList(),today,Nutrient.ENERGY);assertNull(empty.typical);assertTrue(empty.ceiling.isFinite() && empty.ceiling>0)
        val zero=nutritionScale(listOf(meal(today,0.0)),today,Nutrient.ENERGY)
        val trace=nutritionScale(listOf(DiaryEntry(day=today,meal=Meal.LUNCH,food=Food(name="Trace",per100=mapOf(Nutrient.OMEGA3 to .0003)),amount=100.0)),today,Nutrient.OMEGA3)
        assertEquals(.75,trace.typical!!/trace.ceiling,.000001)
        assertEquals(1,zero.recordedDays);assertEquals(0.0,zero.typical!!,0.0);assertTrue(zero.ceiling>0)
    }
    @Test fun storesUseRealFiltersCanonicalTagsStableDefaultsAndPersonalRanking() {
        assertEquals(listOf("tesco","sainsburys","asda","aldi","lidl"),FoodStores.ranked(emptyMap()).take(5).map {it.id})
        assertEquals("waitrose",FoodStores.ranked(mapOf("waitrose" to 8,"tesco" to 2)).first().id)
        assertEquals("sainsburys",FoodStores.canonical("en:sainsbury-s"))
        val url=OpenFoodFacts.searchUrl("bread & milk","sainsburys")
        assertEquals("search.openfoodfacts.org",URI(url).host)
        assertTrue(java.net.URLDecoder.decode(url,"UTF-8").contains("bread milk stores:\"sainsbury's\""))
        assertFalse(OpenFoodFacts.searchUrl("oats",null).contains("stores"))
        // Search hits list brands as an array and may carry an English name alongside the original.
        val hit=OpenFoodFacts.parse(JSONObject("""{"code":"8000500003787","product_name":"Rocher","product_name_en":"Ferrero Rocher","brands":["Ferrero","Ferrero Rocher"],"nutriments":{"energy-kcal_100g":603}}"""))!!
        assertEquals("Ferrero Rocher",hit.name);assertEquals("Ferrero, Ferrero Rocher",hit.brand)
    }
    @Test fun productStoresSurviveSnapshotsWithoutChangingPortionNutrition() {
        val product=JSONObject("""{"code":"5010255045301","product_name":"Oats","stores_tags":["tesco","sainsbury-s"],"nutriments":{"energy-kcal_100g":380}}""")
        val food=OpenFoodFacts.parse(product)!!;val restored=foodFromJson(food.json())
        assertEquals(listOf("tesco","sainsbury-s"),restored.stores)
        assertEquals(190.0,DiaryEntry(day=today,meal=Meal.BREAKFAST,food=restored,amount=50.0).nutrients.getValue(Nutrient.ENERGY),.001)
    }
}
