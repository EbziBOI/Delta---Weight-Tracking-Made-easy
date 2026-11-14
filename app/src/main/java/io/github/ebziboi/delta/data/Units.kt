package io.github.ebziboi.delta.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Formatting and conversion between kilograms and whatever the user reads in. */
object Units {
    const val LB_PER_KG = 2.2046226218
    private const val LB_PER_ST = 14

    private const val MINUS = "−"

    fun kgToLb(kg: Double): Double = kg * LB_PER_KG

    fun lbToKg(lb: Double): Double = lb / LB_PER_KG

    fun stoneToKg(stone: Int, lb: Double): Double = lbToKg(stone * LB_PER_ST + lb)

    /** Whole stones and remaining pounds, rounded so the pounds never print as 14.0. */
    fun stoneParts(kg: Double): Pair<Int, Double> {
        val tenths = (kgToLb(abs(kg)) * 10).roundToLong()
        val stone = (tenths / (LB_PER_ST * 10)).toInt()
        val lb = (tenths % (LB_PER_ST * 10)) / 10.0
        return stone to lb
    }

    private fun one(value: Double): String = String.format(Locale.US, "%.1f", value)

    /** A body weight with its unit: "82.4 kg", "181.7 lb", "12 st 13.7 lb". */
    fun weight(kg: Double, unit: WeightUnit): String = when (unit) {
        WeightUnit.KG -> "${one(kg)} kg"
        WeightUnit.LB -> "${one(kgToLb(kg))} lb"
        WeightUnit.ST_LB -> {
            val (stone, lb) = stoneParts(kg)
            "$stone st ${one(lb)} lb"
        }
    }

    /** As [weight] but kept short for tight spaces: stone drops the trailing "lb". */
    fun weightShort(kg: Double, unit: WeightUnit): String =
        if (unit == WeightUnit.ST_LB) weightValue(kg, unit) else weight(kg, unit)

    /** The number alone, for large readouts: "82.4", "181.7", "12 st 13.7". */
    fun weightValue(kg: Double, unit: WeightUnit): String = when (unit) {
        WeightUnit.KG -> one(kg)
        WeightUnit.LB -> one(kgToLb(kg))
        WeightUnit.ST_LB -> {
            val (stone, lb) = stoneParts(kg)
            "$stone st ${one(lb)}"
        }
    }

    /** The unit that follows [weightValue]. */
    fun suffix(unit: WeightUnit): String = when (unit) {
        WeightUnit.KG -> "kg"
        WeightUnit.LB, WeightUnit.ST_LB -> "lb"
    }

    /** A difference in weight, never signed: "5.2 kg", "11.5 lb", "1 st 2.4 lb". */
    fun amount(kg: Double, unit: WeightUnit): String = when (unit) {
        WeightUnit.KG -> "${one(abs(kg))} kg"
        WeightUnit.LB -> "${one(kgToLb(abs(kg)))} lb"
        WeightUnit.ST_LB -> {
            val (stone, lb) = stoneParts(kg)
            if (stone == 0) "${one(lb)} lb" else "$stone st ${one(lb)} lb"
        }
    }

    /** A signed difference: "−0.4 kg", "+0.9 lb". Stone users read small changes in pounds. */
    fun signed(kg: Double, unit: WeightUnit): String {
        val display = if (unit == WeightUnit.KG) kg else kgToLb(kg)
        val label = if (unit == WeightUnit.KG) "kg" else "lb"
        return "${sign(display)}${one(abs(display))} $label"
    }

    /** Weekly rate with its sign: "−0.6 kg/wk", "+0.3 lb/wk". */
    fun rate(kgPerWeek: Double, unit: WeightUnit): String = "${signed(kgPerWeek, unit)}/wk"

    /** Compact axis label: "82", "82.5", "181", "12st 7". */
    fun axis(kg: Double, unit: WeightUnit): String = when (unit) {
        WeightUnit.KG -> compact(kg)
        WeightUnit.LB -> compact(kgToLb(kg))
        WeightUnit.ST_LB -> {
            val pounds = kgToLb(kg).roundToLong()
            "${pounds / LB_PER_ST}st ${pounds % LB_PER_ST}"
        }
    }

    /** Reads "82.4" or "82,4". Returns null for anything that is not a number. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

    private fun compact(value: Double): String {
        val rounded = (value * 10).roundToLong()
        return if (rounded % 10 == 0L) (rounded / 10).toString() else one(value)
    }

    // A rate that rounds to zero prints without a sign rather than as "−0.0".
    private fun sign(value: Double): String = when {
        (abs(value) * 10).roundToLong() == 0L -> ""
        value < 0 -> MINUS
        else -> "+"
    }
}
