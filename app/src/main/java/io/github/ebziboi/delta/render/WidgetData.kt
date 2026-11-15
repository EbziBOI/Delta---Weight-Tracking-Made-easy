package io.github.ebziboi.delta.render

import io.github.ebziboi.delta.data.*
import java.time.Instant

/** Calendar-aware values shared by every widget layout. */
data class WidgetData(val weekChangeKg: Double?, val bodyFat: List<BodyFatEntry>) {
    companion object {
        fun from(snap: ProgressSnapshot, now: Instant = Instant.now()): WidgetData {
            val end = now.toEpochMilli()
            val readings = InsightsCalculator.distinctReadings(snap.history.filter { it.epochMs <= end }
                .map { WeightEntry(it.epochMs,it.kg,false,it.epochMs.toString()) })
            val cutoff = end - 7L * 86_400_000L
            val latest = readings.lastOrNull()?.takeIf { it.epochMs >= cutoff }
            val before = readings.lastOrNull { it.epochMs <= cutoff }
            val change = if(latest != null && before != null) latest.kg-before.kg else null
            val fat = snap.bodyFat.filter { it.epochMs<=end && BodyFat.valid(it.percent) }
                .sortedBy { it.epochMs }.distinctBy { it.epochMs to it.percent }
            return WidgetData(change,fat)
        }
    }
}

data class WidgetElement(val role: String, val bounds: android.graphics.RectF, val value: String = "", val fontSize: Float = 0f)
