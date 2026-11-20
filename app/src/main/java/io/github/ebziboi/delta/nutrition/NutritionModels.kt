package io.github.ebziboi.delta.nutrition

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Canonical amounts: kcal, g, mg or µg as named here. Absent values are unknown, never zero. */
enum class Nutrient(val label: String, val unit: String, val off: String) {
    ENERGY("Energy", "kcal", "energy-kcal"), PROTEIN("Protein", "g", "proteins"),
    CARBS("Carbohydrate", "g", "carbohydrates"), FAT("Fat", "g", "fat"),
    FIBRE("Fibre", "g", "fiber"), SUGAR("Sugars", "g", "sugars"), SATURATED("Saturated fat", "g", "saturated-fat"),
    SALT("Salt", "g", "salt"), SODIUM("Sodium", "mg", "sodium"),
    POTASSIUM("Potassium", "mg", "potassium"), CALCIUM("Calcium", "mg", "calcium"),
    IRON("Iron", "mg", "iron"), MAGNESIUM("Magnesium", "mg", "magnesium"),
    ZINC("Zinc", "mg", "zinc"), PHOSPHORUS("Phosphorus", "mg", "phosphorus"),
    SELENIUM("Selenium", "µg", "selenium"), IODINE("Iodine", "µg", "iodine"),
    VIT_A("Vitamin A", "µg", "vitamin-a"), VIT_C("Vitamin C", "mg", "vitamin-c"),
    VIT_D("Vitamin D", "µg", "vitamin-d"), VIT_E("Vitamin E", "mg", "vitamin-e"),
    VIT_K("Vitamin K", "µg", "vitamin-k"), B1("Thiamin · B1", "mg", "vitamin-b1"),
    B2("Riboflavin · B2", "mg", "vitamin-b2"), B3("Niacin · B3", "mg", "vitamin-pp"),
    B6("Vitamin B6", "mg", "vitamin-b6"), B12("Vitamin B12", "µg", "vitamin-b12"),
    FOLATE("Folate", "µg", "folates"), B5("Pantothenic acid · B5", "mg", "pantothenic-acid"),
    BIOTIN("Biotin · B7", "µg", "biotin"), COPPER("Copper", "mg", "copper"), MANGANESE("Manganese", "mg", "manganese"),
    CHROMIUM("Chromium", "µg", "chromium"), MOLYBDENUM("Molybdenum", "µg", "molybdenum"),
    CHLORIDE("Chloride", "mg", "chloride"), FLUORIDE("Fluoride", "mg", "fluoride"),
    CHOLINE("Choline", "mg", "choline"), OMEGA3("Omega 3", "g", "omega-3-fat"), OMEGA6("Omega 6", "g", "omega-6-fat"),
    MONOUNSATURATED("Monounsaturated fat", "g", "monounsaturated-fat"), POLYUNSATURATED("Polyunsaturated fat", "g", "polyunsaturated-fat"),
    TRANSFAT("Trans fat", "g", "trans-fat"), CHOLESTEROL("Cholesterol", "mg", "cholesterol"),
    ALCOHOL("Alcohol", "g", "alcohol"), CAFFEINE("Caffeine", "mg", "caffeine"),
    ENERGY_FROM_FAT("Energy from fat", "kcal", "energy-from-fat"), FOLIC_ACID("Folic acid", "µg", "folic-acid"),
    UNSATURATED("Unsaturated fat", "g", "unsaturated-fat");
    companion object { val macros = listOf(PROTEIN, CARBS, FAT); val primary = listOf(ENERGY) + macros }
}

typealias Nutrients = Map<Nutrient, Double>
fun Double.validAmount() = isFinite() && this >= 0.0 && this <= 1e9
fun Nutrients.scaled(factor: Double): Nutrients {
    require(factor.isFinite() && factor >= 0 && factor <= 1e6) { "Enter a valid amount" }
    return mapValues { (_, value) -> (value * factor).also { require(it.validAmount()) } }
}
fun newId(): String = UUID.randomUUID().toString()
enum class Meal(val label: String) { BREAKFAST("Breakfast"), LUNCH("Lunch"), DINNER("Dinner"), SNACKS("Snacks"), OTHER("Other") }
enum class FoodSource { OPEN_FOOD_FACTS, CUSTOM, RECIPE, PHOTO, HEALTH_CONNECT, RESTAURANT, GENERIC }
enum class FoodUnit(val symbol: String) { GRAMS("g"), MILLILITRES("ml") }

data class Ingredient(val food: Food, val amount: Double) {
    init { require(amount.isFinite() && amount > 0 && amount <= 100000) }
    val nutrients get() = food.per100.scaled(amount / 100)
}
data class Food(
    val id: String = newId(), val name: String, val brand: String = "",
    val per100: Nutrients, val unit: FoodUnit = FoodUnit.GRAMS,
    val serving: Double? = null, val servingLabel: String = "Serving", val pack: Double? = null,
    val source: FoodSource = FoodSource.CUSTOM, val barcode: String? = null,
    val ingredients: List<Ingredient> = emptyList(), val recipeServings: Double? = null,
    val yieldGrams: Double? = null, val favourite: Boolean = false, val estimatedOrigin: Boolean = false,
    val stores:List<String> = emptyList(),
    val sourceUrl:String? = null, val labelChecked:Set<Nutrient> = emptySet(),
) {
    init {
        require(name.isNotBlank() && name.length <= 250) { "Enter a food name" }
        require(per100.values.all { it.validAmount() }) { "Nutrients must be valid positive numbers" }
        require(listOfNotNull(serving, pack, recipeServings, yieldGrams).all { it.isFinite() && it > 0 && it <= 100000 }) { "Enter a valid serving size" }
        require(ingredients.size <= 200)
        require(sourceUrl==null || ProductPages.publicUrl(sourceUrl))
        require(stores.size<=50 && stores.all {it.length<=100})
        require(brand.length<=250 && servingLabel.length<=100) { "Food details are too long" }
    }
    val estimated: Boolean get() = estimatedOrigin || source == FoodSource.PHOTO || ingredients.any { it.food.estimated }
    val subtitle get() = listOf(brand.takeIf { it.isNotBlank() }, when(source) {
        FoodSource.OPEN_FOOD_FACTS -> "Open Food Facts"; FoodSource.CUSTOM -> when {sourceUrl!=null->"Product page";labelChecked.isNotEmpty()->"Package label";else->"Custom food"}
        FoodSource.RECIPE -> "Recipe"; FoodSource.PHOTO -> "Photo estimate"; FoodSource.HEALTH_CONNECT -> "Health Connect"
        FoodSource.RESTAURANT -> "Menu guide · approx."
        FoodSource.GENERIC -> "Typical values"
    }).filterNotNull().joinToString(" · ")
    /** A product record that lacks the label values needed to log it meaningfully. */
    val missingNutrition: Boolean get() = Nutrient.ENERGY !in per100 && Nutrient.macros.none { it in per100 }
}

data class DiaryEntry(
    val id: String = newId(), val day: LocalDate, val meal: Meal, val food: Food,
    val amount: Double, val created: Long = System.currentTimeMillis(),
) {
    init { require(amount.isFinite() && amount > 0 && amount <= 100000) { "Enter an amount above zero" } }
    val nutrients get() = food.per100.scaled(amount / 100)
    val fromHealthConnect get() = id.startsWith("health:")
}

data class NutrientTotal(val amount: Double, val reported: Int, val count: Int) {
    val partial get() = reported < count
    val known get() = count == 0 || reported > 0
}
fun List<DiaryEntry>.total(nutrient: Nutrient) = NutrientTotal(
    sumOf { it.nutrients[nutrient] ?: 0.0 }, count { nutrient in it.food.per100 }, size,
)

fun makeRecipe(name: String, ingredients: List<Ingredient>, servings: Double, yieldGrams: Double, id: String = newId()): Food {
    require(ingredients.isNotEmpty()) { "Add at least one ingredient" }
    require(servings.isFinite() && servings > 0 && servings <= 100000 && yieldGrams.isFinite() && yieldGrams > 0 && yieldGrams <= 100000) { "Enter servings and finished weight" }
    // A missing ingredient label stays unknown in the recipe instead of becoming a false zero.
    val values = Nutrient.entries.mapNotNull { n ->
        if (ingredients.all { n in it.food.per100 }) n to ingredients.sumOf { it.nutrients.getValue(n) } * 100 / yieldGrams else null
    }.toMap()
    return Food(id, name.trim(), per100 = values, source = FoodSource.RECIPE, serving = yieldGrams / servings,
        ingredients = ingredients, recipeServings = servings, yieldGrams = yieldGrams)
}

enum class MacroMode { GRAMS, PERCENT }
data class NutritionTargets(
    val amounts: Nutrients = emptyMap(), val mode: MacroMode = MacroMode.GRAMS,
    val percentages: Map<Nutrient, Double> = mapOf(Nutrient.PROTEIN to 25.0, Nutrient.CARBS to 45.0, Nutrient.FAT to 30.0),
    val limits: Set<Nutrient> = setOf(Nutrient.SUGAR, Nutrient.SATURATED, Nutrient.SALT, Nutrient.SODIUM),
) {
    fun validated(): NutritionTargets {
        require(amounts.values.all { it.isFinite() && it > 0 && it <= 1e7 }) { "Targets must be above zero" }
        if (mode == MacroMode.PERCENT) {
            require((amounts[Nutrient.ENERGY] ?: 0.0) > 0) { "Set an energy target first" }
            require(Nutrient.macros.all { (percentages[it] ?: -1.0).let { v -> v.isFinite() && v in 0.0..100.0 } } &&
                kotlin.math.abs(Nutrient.macros.sumOf { percentages.getValue(it) } - 100) < .01) { "Macro percentages must total 100%" }
        }
        return this
    }
    fun target(n: Nutrient): Double? = if (mode == MacroMode.PERCENT && n in Nutrient.macros) {
        amounts[Nutrient.ENERGY]?.let { it * (percentages[n] ?: 0.0) / 100 / if(n == Nutrient.FAT) 9 else 4 }
    } else amounts[n]
}

data class Fast(val id: String = newId(), val start: Long, val targetHours: Double, val end: Long? = null) {
    init { require(targetHours.isFinite() && targetHours > 0 && targetHours <= 168); require(end == null || end >= start) }
    fun elapsed(now: Long = System.currentTimeMillis()) = ((end ?: now) - start).coerceAtLeast(0)
    val goalMs get() = (targetHours * 3600000).toLong()
    fun reached(now: Long = System.currentTimeMillis()) = elapsed(now) >= goalMs
    val targetEnd get() = Instant.ofEpochMilli(start + goalMs)
}

data class NutritionState(val foods: List<Food> = emptyList(), val entries: List<DiaryEntry> = emptyList(),
    val targets: NutritionTargets = NutritionTargets(), val fasts: List<Fast> = emptyList(), val loaded: Boolean = false,
    val loadError: String? = null)

internal fun Nutrients.toJson() = JSONObject().also { j -> forEach { (n,v) -> j.put(n.name,v) } }
internal fun JSONObject.nutrients(): Nutrients = Nutrient.entries.mapNotNull { n ->
    if (!has(n.name) || isNull(n.name)) null else optDouble(n.name, Double.NaN).takeIf { it.validAmount() }?.let { n to it }
}.toMap()
internal fun JSONObject.positive(key: String): Double? = optDouble(key, Double.NaN).takeIf { it.isFinite() && it > 0 }
internal fun Food.json(depth: Int = 0): JSONObject = JSONObject().apply {
    require(depth < 10) { "Recipes are nested too deeply" }
    put("id",id); put("name",name); put("brand",brand); put("nutrients",per100.toJson()); put("unit",unit.name)
    put("serving",serving); put("servingLabel",servingLabel); put("pack",pack); put("source",source.name)
    put("barcode",barcode); put("servings",recipeServings); put("yield",yieldGrams); put("favourite",favourite); put("estimated",estimated);put("stores",JSONArray(stores))
    put("sourceUrl",sourceUrl);put("labelChecked",JSONArray(labelChecked.map {it.name}))
    put("ingredients",JSONArray().also { a -> ingredients.forEach { a.put(JSONObject().put("food",it.food.copy(ingredients=emptyList(),estimatedOrigin=it.food.estimated).json(depth+1)).put("amount",it.amount)) } })
}
internal fun foodFromJson(j: JSONObject, depth: Int = 0): Food {
    require(depth < 10)
    val a=j.optJSONArray("ingredients") ?: JSONArray()
    return Food(j.getString("id"),j.getString("name"),j.optString("brand"),j.getJSONObject("nutrients").nutrients(),
        FoodUnit.valueOf(j.optString("unit","GRAMS")),j.positive("serving"),j.optString("servingLabel","Serving"),j.positive("pack"),
        FoodSource.entries.find { it.name==j.optString("source") } ?: FoodSource.CUSTOM,j.optString("barcode").takeIf { it.isNotBlank() && it != "null" },
        (0 until a.length()).map { a.getJSONObject(it).let { i -> Ingredient(foodFromJson(i.getJSONObject("food"),depth+1),i.getDouble("amount")) } },
        j.positive("servings"),j.positive("yield"),j.optBoolean("favourite"),j.optBoolean("estimated"),
        j.optJSONArray("stores")?.let {a->(0 until a.length()).map {a.getString(it).take(100)}.take(50)} ?: emptyList(),
        j.optString("sourceUrl").takeIf(ProductPages::publicUrl),
        j.optJSONArray("labelChecked")?.let {a->(0 until a.length()).mapNotNull {i->Nutrient.entries.find {it.name==a.optString(i)}}.toSet()} ?: emptySet())
}
