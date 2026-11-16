package io.github.ebziboi.delta.render

import io.github.ebziboi.delta.data.*
import java.time.Instant
import java.time.ZoneId

/** Calendar-aware values shared by every widget layout, worked out as the Progress tab does. */
data class WidgetData(
    val weekChangeKg: Double?,
    val bodyFat: List<BodyFatEntry>,
    /** Mean of the daily means in the last seven days, and in the seven before. */
    val average7Kg: Double? = null,
    val averagePrevious7Kg: Double? = null,
    /** Days in the last seven with a weigh-in. */
    val daysWeighed: Int = 0,
) {
    companion object {
        fun from(snap: ProgressSnapshot, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): WidgetData {
            val end = now.toEpochMilli()
            val entries = snap.history.filter { it.epochMs <= end }.map { WeightEntry(it.epochMs, it.kg, false, it.epochMs.toString()) }
            val readings = InsightsCalculator.distinctReadings(entries)
            val cutoff = end - 7L * 86_400_000L
            val latest = readings.lastOrNull()?.takeIf { it.epochMs >= cutoff }
            val before = readings.lastOrNull { it.epochMs <= cutoff }
            val change = if (latest != null && before != null) latest.kg - before.kg else null
            val fat = snap.bodyFat.filter { it.epochMs <= end && BodyFat.valid(it.percent) }
                .sortedBy { it.epochMs }.distinctBy { it.epochMs to it.percent }
            val insights = InsightsCalculator.compute(readings, snap, zone, now)
            return WidgetData(change, fat, insights.average7Kg, insights.averagePrevious7Kg, insights.daysWeighedThisWeek)
        }
    }
}

data class WidgetElement(val role: String, val bounds: android.graphics.RectF, val value: String = "", val fontSize: Float = 0f)
