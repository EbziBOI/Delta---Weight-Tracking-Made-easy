package io.github.ebziboi.delta.nutrition

import java.time.LocalDate

internal data class NutritionScale(val typical:Double?,val ceiling:Double,val recordedDays:Int)
/** A median of recorded daily totals: unlogged dates and unknown labels are absent. */
internal fun nutritionScale(entries:List<DiaryEntry>,end:LocalDate,nutrient:Nutrient):NutritionScale {
    val totals=entries.filter {it.day in end.minusDays(29)..end}.groupBy {it.day}.values
        .mapNotNull {day->day.total(nutrient).takeIf {it.reported>0}?.amount}.sorted()
    fun median(values:List<Double>):Double = if(values.size%2==1) values[values.size/2] else values[values.size/2-1]/2+values[values.size/2]/2
    val typical=totals.takeIf {it.isNotEmpty()}?.let(::median)
    val positive=totals.filter {it>0}
    val basis=typical?.takeIf {it>0} ?: positive.takeIf {it.isNotEmpty()}?.let(::median)
    return NutritionScale(typical,basis?.div(.75)?.coerceAtLeast(1e-12) ?: 1.0,totals.size)
}
