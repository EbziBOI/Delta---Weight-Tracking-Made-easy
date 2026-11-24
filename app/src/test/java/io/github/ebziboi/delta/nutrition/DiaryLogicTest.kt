package io.github.ebziboi.delta.nutrition

import io.github.ebziboi.delta.ui.RecordedValue
import io.github.ebziboi.delta.ui.smoothRecorded
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DiaryLogicTest {
    private val today=LocalDate.of(2026,11,19)
    private fun food(name:String,kcal:Double=100.0,id:String=name)=Food(id,name,per100=mapOf(Nutrient.ENERGY to kcal,Nutrient.PROTEIN to 10.0,Nutrient.CARBS to 10.0,Nutrient.FAT to 5.0))
    private fun entry(f:Food,meal:Meal,daysAgo:Long,created:Long=daysAgo)=DiaryEntry(day=today.minusDays(daysAgo),meal=meal,food=f,amount=100.0,created=1_000_000-created)

    @Test fun repeatSuggestionsOnlyUseTheSameMeal() {
        val oats=food("Oats");val sandwich=food("Sandwich");val curry=food("Curry")
        val entries=listOf(entry(oats,Meal.BREAKFAST,1),entry(oats,Meal.BREAKFAST,2),entry(sandwich,Meal.LUNCH,1),entry(curry,Meal.DINNER,1))
        assertEquals(listOf("Oats"),repeatSuggestions(entries,Meal.BREAKFAST,today).map {it.food.name})
        assertEquals(listOf("Sandwich"),repeatSuggestions(entries,Meal.LUNCH,today).map {it.food.name})
        assertTrue(repeatSuggestions(entries,Meal.SNACKS,today).isEmpty())
    }

    @Test fun repeatSuggestionsRankFrequentRecentFoodsAndSkipOnesAlreadyLogged() {
        val oats=food("Oats");val toast=food("Toast");val eggs=food("Eggs")
        val entries=(1L..6L).map {entry(oats,Meal.BREAKFAST,it)}+entry(toast,Meal.BREAKFAST,40)+entry(eggs,Meal.BREAKFAST,0)
        val names=repeatSuggestions(entries,Meal.BREAKFAST,today).map {it.food.name}
        assertEquals("Oats",names.first())
        assertFalse("Already logged today",names.contains("Eggs"))
        assertTrue(names.contains("Toast"))
    }

    @Test fun mealDefaultsFollowTheTimeOfDay() {
        assertEquals(Meal.BREAKFAST,mealForNow(7));assertEquals(Meal.LUNCH,mealForNow(12))
        assertEquals(Meal.DINNER,mealForNow(19));assertEquals(Meal.SNACKS,mealForNow(16));assertEquals(Meal.SNACKS,mealForNow(23))
    }

    @Test fun trendIsCentredAndDoesNotLagASteadyFall() {
        // A steady 0.1 kg/day fall: a running average trails above the data; the centred fit sits on it.
        val values=(0..40).map {RecordedValue(today.minusDays(40L-it),80.0-it*.1)}
        val fitted=smoothRecorded(values)
        fitted.zip(values).forEach {(f,v)->assertEquals(v.value,f.value,1e-6)}
    }

    @Test fun trendSmoothsNoiseAndStaysWithinNeighbours() {
        val noisy=(0..30).map {RecordedValue(today.minusDays(30L-it),80.0+if(it%2==0) .8 else -.8)}
        val fitted=smoothRecorded(noisy)
        assertTrue(fitted.all {it.value in 79.2..80.8})
        val wiggle=fitted.zipWithNext {a,b->kotlin.math.abs(a.value-b.value)}.drop(3).dropLast(3).max()
        assertTrue("trend should be far calmer than the 1.6 kg zig-zag, was $wiggle",wiggle<.4)
        assertEquals(listOf(1.0,2.0),smoothRecorded(listOf(RecordedValue(today,1.0),RecordedValue(today.plusDays(1),2.0))).map {it.value})
    }

    @Test fun missingNutritionMeansNoEnergyAndNoMacros() {
        assertTrue(Food(name="Mystery",per100=emptyMap()).missingNutrition)
        assertTrue(Food(name="Salt only",per100=mapOf(Nutrient.SALT to 1.0)).missingNutrition)
        assertFalse(Food(name="Energy only",per100=mapOf(Nutrient.ENERGY to 50.0)).missingNutrition)
        assertFalse(Food(name="Protein only",per100=mapOf(Nutrient.PROTEIN to 5.0)).missingNutrition)
    }

    @Test fun foodSharesAddUpAndFoldTheTail() {
        val entries=(1..10).map {i->DiaryEntry(day=today,meal=Meal.LUNCH,food=food("Food $i"),amount=i*10.0)}
        val shares=foodShares(entries,Nutrient.PROTEIN,limit=5)
        assertEquals(5,shares.size);assertEquals("Food 10",shares.first().name);assertEquals("Other foods",shares.last().name)
        assertEquals(1.0,shares.sumOf {it.share},1e-9)
        assertEquals(entries.sumOf {it.nutrients[Nutrient.PROTEIN]!!},shares.sumOf {it.amount},1e-9)
    }

    @Test fun fastFoodMenuParsesAndPortionsMatchTheGuide() {
        assertTrue(FastFood.items.size>50)
        assertEquals(FastFood.items.size,FastFood.items.map {it.id}.toSet().size)
        FastFood.items.forEach {f->
            assertEquals(FoodSource.RESTAURANT,f.source);assertNotNull(f.serving);assertFalse(f.missingNutrition)
            // Atwater check: stated kcal should be near 4/4/9 from the macros, catching typos in the table.
            val s=f.serving!!/100;val kcal=f.per100.getValue(Nutrient.ENERGY)*s
            val atwater=(f.per100.getValue(Nutrient.PROTEIN)+f.per100.getValue(Nutrient.CARBS))*4*s+f.per100.getValue(Nutrient.FAT)*9*s
            if(kcal>40) assertEquals("${f.brand} ${f.name}",1.0,atwater/kcal,.2)
        }
        val bigMac=FastFood.search("big mac").first()
        assertEquals("Big Mac",bigMac.name)
        assertEquals(508.0,bigMac.per100.getValue(Nutrient.ENERGY)*bigMac.serving!!/100,1e-6)
        assertTrue(FastFood.search("mcd fries").all {it.brand.startsWith("McDonald")})
        assertTrue(FastFood.search("nandos chips").isNotEmpty())
    }

    @Test fun localSearchMatchesWordStarts() {
        val f=Food(name="Greek Yoghurt",brand="Fage",per100=mapOf(Nutrient.ENERGY to 90.0))
        assertTrue(f.matches("greek"));assertTrue(f.matches("fage yog"));assertFalse(f.matches("reek"))
    }

    @Test fun quickAddStartsFromTheAmountLastLogged() {
        val oats=food("Oats");val milk=food("Milk")
        val entries=listOf(DiaryEntry(day=today,meal=Meal.BREAKFAST,food=oats,amount=40.0,created=1),
            DiaryEntry(day=today,meal=Meal.BREAKFAST,food=oats,amount=55.0,created=3),
            DiaryEntry(day=today,meal=Meal.BREAKFAST,food=oats,amount=70.0,created=2),
            DiaryEntry(id="health:1",day=today,meal=Meal.BREAKFAST,food=milk,amount=500.0,created=9))
        val last=lastAmounts(entries)
        assertEquals(55.0,last.getValue("Oats"),0.0)
        assertNull("Health Connect entries aren't typed amounts",last["Milk"])
    }

    @Test fun lastAmountsShowAsServingsOnlyWhenTheyAreWholeOrHalfServings() {
        val bar=Food("bar","Bar",per100=mapOf(Nutrient.ENERGY to 500.0),serving=30.0)
        assertEquals("Serving" to 2.0,startingPortion(bar,60.0))
        assertEquals("Serving" to 1.5,startingPortion(bar,45.0))
        assertEquals("g" to 42.0,startingPortion(bar,42.0))
        assertEquals("g" to 42.0,startingPortion(food("Rice"),42.0))
    }

    @Test fun productSearchUsesTheWorkingServiceAndStoreFilters() {
        val url=OpenFoodFacts.searchUrl("ferrero rocher",null)
        assertEquals("search.openfoodfacts.org",java.net.URI(url).host)
        assertTrue(url.contains("q=ferrero+rocher&"))
        assertFalse(url.contains("stores"))
        val tesco=java.net.URLDecoder.decode(OpenFoodFacts.searchUrl("oats","tesco"),"UTF-8")
        assertTrue(tesco.contains("stores:\"Tesco\""));assertTrue(tesco.contains("countries_tags:\"en:united-kingdom\""))
        assertTrue(java.net.URLDecoder.decode(OpenFoodFacts.searchUrl("oats","marks-spencer"),"UTF-8").contains("stores:\"marks & spencer\""))
        assertEquals("ben jerry s cookie dough",OpenFoodFacts.searchTerms("ben & jerry's: (cookie) dough*").replace("'"," ").replace(Regex("\\s+")," "))
        assertEquals("",OpenFoodFacts.searchTerms("\"*:*\""))
    }
}
