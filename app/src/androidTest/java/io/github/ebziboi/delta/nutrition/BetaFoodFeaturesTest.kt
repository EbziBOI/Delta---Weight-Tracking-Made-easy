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
        assertEquals("Ferrero Rocher",hit.name)
        // "Ferrero" is dropped because "Ferrero Rocher" already starts with it.
        assertEquals("Ferrero Rocher",hit.brand)
        val kj=OpenFoodFacts.parse(JSONObject("""{"code":"8000500003788","product_name":"kJ only","brands":"Mars, Galaxy","nutriments":{"energy-kj_100g":2506}}"""))!!
        assertEquals("Energy listed only in kJ becomes kcal",599.0,kj.per100.getValue(Nutrient.ENERGY),.5)
        assertEquals("Mars, Galaxy",kj.brand)
    }
    @Test fun productStoresSurviveSnapshotsWithoutChangingPortionNutrition() {
        val product=JSONObject("""{"code":"5010255045301","product_name":"Oats","stores_tags":["tesco","sainsbury-s"],"nutriments":{"energy-kcal_100g":380}}""")
        val food=OpenFoodFacts.parse(product)!!;val restored=foodFromJson(food.json())
        assertEquals(listOf("tesco","sainsbury-s"),restored.stores)
        assertEquals(190.0,DiaryEntry(day=today,meal=Meal.BREAKFAST,food=restored,amount=50.0).nutrients.getValue(Nutrient.ENERGY),.001)
    }
}
