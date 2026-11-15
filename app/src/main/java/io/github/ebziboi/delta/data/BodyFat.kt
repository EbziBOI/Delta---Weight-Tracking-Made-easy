package io.github.ebziboi.delta.data

import java.util.Locale
import kotlin.math.abs

data class BodyFatEntry(val epochMs: Long, val percent: Double, val own: Boolean, val id: String)
object BodyFat {
    fun valid(value: Double) = value.isFinite() && value > 0.0 && value <= 100.0
    fun format(value: Double) = String.format(Locale.getDefault(), "%.1f%%", value)
    /** Only associate readings from the same measurement session, never a nearby day. */
    fun near(time: Long, readings: List<BodyFatEntry>): BodyFatEntry? = readings
        .filter { valid(it.percent) && abs(it.epochMs - time) <= 15 * 60_000L }.minByOrNull { abs(it.epochMs - time) }
}
