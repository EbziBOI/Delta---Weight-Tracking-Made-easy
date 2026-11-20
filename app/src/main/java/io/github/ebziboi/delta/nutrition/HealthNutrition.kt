package io.github.ebziboi.delta.nutrition

import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.MealType
import java.time.ZoneId

/** Health Connect amounts are consumed totals, not a label per 100 g. */
internal fun NutritionRecord.toDiaryEntry(sourceLabel:String="Connected app"):DiaryEntry {
    val r=this
    val nutrients=buildMap<Nutrient,Double> {
        fun add(n:Nutrient,v:Double?) {if(v!=null && v.validAmount()) put(n,v)}
        add(Nutrient.ENERGY,r.energy?.inKilocalories);add(Nutrient.ENERGY_FROM_FAT,r.energyFromFat?.inKilocalories)
        add(Nutrient.PROTEIN,r.protein?.inGrams);add(Nutrient.CARBS,r.totalCarbohydrate?.inGrams);add(Nutrient.FAT,r.totalFat?.inGrams)
        add(Nutrient.FIBRE,r.dietaryFiber?.inGrams);add(Nutrient.SUGAR,r.sugar?.inGrams);add(Nutrient.SATURATED,r.saturatedFat?.inGrams)
        add(Nutrient.MONOUNSATURATED,r.monounsaturatedFat?.inGrams);add(Nutrient.POLYUNSATURATED,r.polyunsaturatedFat?.inGrams)
        add(Nutrient.UNSATURATED,r.unsaturatedFat?.inGrams);add(Nutrient.TRANSFAT,r.transFat?.inGrams)
        add(Nutrient.SODIUM,r.sodium?.inMilligrams);add(Nutrient.POTASSIUM,r.potassium?.inMilligrams)
        add(Nutrient.CALCIUM,r.calcium?.inMilligrams);add(Nutrient.IRON,r.iron?.inMilligrams);add(Nutrient.MAGNESIUM,r.magnesium?.inMilligrams)
        add(Nutrient.ZINC,r.zinc?.inMilligrams);add(Nutrient.PHOSPHORUS,r.phosphorus?.inMilligrams)
        add(Nutrient.SELENIUM,r.selenium?.inMicrograms);add(Nutrient.IODINE,r.iodine?.inMicrograms)
        add(Nutrient.VIT_A,r.vitaminA?.inMicrograms);add(Nutrient.VIT_C,r.vitaminC?.inMilligrams);add(Nutrient.VIT_D,r.vitaminD?.inMicrograms)
        add(Nutrient.VIT_E,r.vitaminE?.inMilligrams);add(Nutrient.VIT_K,r.vitaminK?.inMicrograms)
        add(Nutrient.B1,r.thiamin?.inMilligrams);add(Nutrient.B2,r.riboflavin?.inMilligrams);add(Nutrient.B3,r.niacin?.inMilligrams)
        add(Nutrient.B5,r.pantothenicAcid?.inMilligrams);add(Nutrient.B6,r.vitaminB6?.inMilligrams);add(Nutrient.B12,r.vitaminB12?.inMicrograms)
        add(Nutrient.FOLATE,r.folate?.inMicrograms);add(Nutrient.FOLIC_ACID,r.folicAcid?.inMicrograms)
        add(Nutrient.BIOTIN,r.biotin?.inMicrograms);add(Nutrient.COPPER,r.copper?.inMilligrams);add(Nutrient.MANGANESE,r.manganese?.inMilligrams)
        add(Nutrient.CHROMIUM,r.chromium?.inMicrograms);add(Nutrient.MOLYBDENUM,r.molybdenum?.inMicrograms)
        add(Nutrient.CHLORIDE,r.chloride?.inMilligrams);add(Nutrient.CHOLESTEROL,r.cholesterol?.inMilligrams);add(Nutrient.CAFFEINE,r.caffeine?.inMilligrams)
    }
    val meal=when(mealType) {
        MealType.MEAL_TYPE_BREAKFAST->Meal.BREAKFAST;MealType.MEAL_TYPE_LUNCH->Meal.LUNCH
        MealType.MEAL_TYPE_DINNER->Meal.DINNER;MealType.MEAL_TYPE_SNACK->Meal.SNACKS;else->Meal.OTHER
    }
    val origin=metadata.dataOrigin.packageName
    val sourceId=metadata.id.ifBlank {error("Health Connect record has no ID")}
    val id="health:$origin:$sourceId"
    return DiaryEntry(id,startTime.atZone(startZoneOffset ?: ZoneId.systemDefault()).toLocalDate(),meal,
        Food(id=id,name=name?.takeIf {it.isNotBlank()}?.take(250) ?: "Recorded nutrition",brand=sourceLabel.take(250),per100=nutrients,
            source=FoodSource.HEALTH_CONNECT),100.0,startTime.toEpochMilli())
}

/** A local diary line as a Health Connect record, timed at when it was logged or at a typical time for its meal. */
internal fun nutritionRecord(e:DiaryEntry,version:Long):NutritionRecord {
    val zone=ZoneId.systemDefault()
    val logged=java.time.Instant.ofEpochMilli(e.created)
    val start=if(logged.atZone(zone).toLocalDate()==e.day && logged.isBefore(java.time.Instant.now())) logged
        else e.day.atTime(when(e.meal) {Meal.BREAKFAST->8;Meal.LUNCH->13;Meal.DINNER->19;Meal.SNACKS->16;else->12},0).atZone(zone).toInstant()
    val end=start.plusSeconds(60)
    val n=e.nutrients
    fun g(x:Nutrient)=n[x]?.let {androidx.health.connect.client.units.Mass.grams(it)}
    fun mg(x:Nutrient)=n[x]?.let {androidx.health.connect.client.units.Mass.milligrams(it)}
    fun ug(x:Nutrient)=n[x]?.let {androidx.health.connect.client.units.Mass.micrograms(it)}
    return NutritionRecord(startTime=start,startZoneOffset=zone.rules.getOffset(start),endTime=end,endZoneOffset=zone.rules.getOffset(end),
        metadata=androidx.health.connect.client.records.metadata.Metadata.manualEntry(clientRecordId=io.github.ebziboi.delta.data.HealthConnectManager.diaryRecordId(e.id),clientRecordVersion=version),
        energy=n[Nutrient.ENERGY]?.let {androidx.health.connect.client.units.Energy.kilocalories(it)},
        energyFromFat=n[Nutrient.ENERGY_FROM_FAT]?.let {androidx.health.connect.client.units.Energy.kilocalories(it)},
        protein=g(Nutrient.PROTEIN),totalCarbohydrate=g(Nutrient.CARBS),totalFat=g(Nutrient.FAT),dietaryFiber=g(Nutrient.FIBRE),sugar=g(Nutrient.SUGAR),
        saturatedFat=g(Nutrient.SATURATED),monounsaturatedFat=g(Nutrient.MONOUNSATURATED),polyunsaturatedFat=g(Nutrient.POLYUNSATURATED),
        unsaturatedFat=g(Nutrient.UNSATURATED),transFat=g(Nutrient.TRANSFAT),
        sodium=mg(Nutrient.SODIUM),potassium=mg(Nutrient.POTASSIUM),calcium=mg(Nutrient.CALCIUM),iron=mg(Nutrient.IRON),magnesium=mg(Nutrient.MAGNESIUM),
        zinc=mg(Nutrient.ZINC),phosphorus=mg(Nutrient.PHOSPHORUS),selenium=ug(Nutrient.SELENIUM),iodine=ug(Nutrient.IODINE),
        vitaminA=ug(Nutrient.VIT_A),vitaminC=mg(Nutrient.VIT_C),vitaminD=ug(Nutrient.VIT_D),vitaminE=mg(Nutrient.VIT_E),vitaminK=ug(Nutrient.VIT_K),
        thiamin=mg(Nutrient.B1),riboflavin=mg(Nutrient.B2),niacin=mg(Nutrient.B3),pantothenicAcid=mg(Nutrient.B5),vitaminB6=mg(Nutrient.B6),vitaminB12=ug(Nutrient.B12),
        folate=ug(Nutrient.FOLATE),folicAcid=ug(Nutrient.FOLIC_ACID),biotin=ug(Nutrient.BIOTIN),copper=mg(Nutrient.COPPER),manganese=mg(Nutrient.MANGANESE),
        chromium=ug(Nutrient.CHROMIUM),molybdenum=ug(Nutrient.MOLYBDENUM),chloride=mg(Nutrient.CHLORIDE),cholesterol=mg(Nutrient.CHOLESTEROL),caffeine=mg(Nutrient.CAFFEINE),
        name=e.food.name.take(250),mealType=when(e.meal) {Meal.BREAKFAST->MealType.MEAL_TYPE_BREAKFAST;Meal.LUNCH->MealType.MEAL_TYPE_LUNCH;Meal.DINNER->MealType.MEAL_TYPE_DINNER;Meal.SNACKS->MealType.MEAL_TYPE_SNACK;else->MealType.MEAL_TYPE_UNKNOWN})
}
