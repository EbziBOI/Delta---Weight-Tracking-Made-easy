package io.github.ebziboi.delta.data

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** One weigh-in as the summary shows it, with the change since the one before. */
data class Reading(val epochMs: Long, val kg: Double, val changeKg: Double?)

/** Figures that describe the last few weeks at a glance, beyond start, now and target. */
data class Insights(
    /** Change since the last weigh-in a week or more ago. */
    val weekChangeKg: Double?,
    /** Change since the last weigh-in thirty days or more ago. */
    val monthChangeKg: Double?,
    /** Mean of the last seven days' weigh-ins, and of the seven days before. */
    val average7Kg: Double?,
    val averagePrevious7Kg: Double?,
    val lowestKg: Double?,
    val lowestMs: Long?,
    /** Days in the last seven with a weigh-in. */
    val daysWeighedThisWeek: Int,
    val daysSinceLastWeighIn: Long?,
    val daysSinceStart: Long,
    /** The latest weigh-ins, newest first. */
    val recent: List<Reading>,
)

object InsightsCalculator {
    private const val DAY_MS = 86_400_000L

    /** Readings this close together and this similar are one weigh-in seen through two apps. */
    private const val SAME_READING_MS = 15 * 60_000L
    private const val SAME_READING_KG = 0.05

    /**
     * Drops repeats of the same weigh-in written by more than one app or scale, so a
     * reading counted twice does not skew averages or show up as a change of 0.0.
     */
    fun distinctReadings(entries: List<WeightEntry>): List<WeightEntry> {
        val out = ArrayList<WeightEntry>(entries.size)
        for (entry in entries.sortedBy { it.epochMs }) {
            val last = out.lastOrNull()
            if (last != null && entry.epochMs - last.epochMs <= SAME_READING_MS && abs(entry.kg - last.kg) < SAME_READING_KG) continue
            out += entry
        }
        return out
    }

    fun compute(
        entries: List<WeightEntry>,
        snap: ProgressSnapshot,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now(),
    ): Insights {
        val readings = distinctReadings(entries)
        val nowMs = now.toEpochMilli()
        val today = now.atZone(zone).toLocalDate()
        val latest = readings.lastOrNull()

        fun changeSince(days: Long): Double? {
            val cutoff = nowMs - days * DAY_MS
            val before = readings.lastOrNull { it.epochMs <= cutoff } ?: return null
            return latest?.let { it.kg - before.kg }
        }
        fun average(fromDaysAgo: Long, toDaysAgo: Long): Double? {
            val from = nowMs - fromDaysAgo * DAY_MS
            val to = nowMs - toDaysAgo * DAY_MS
            val window = readings.filter { it.epochMs in from..to }
            return if (window.isEmpty()) null else window.sumOf { it.kg } / window.size
        }

        val startMs = snap.startDay.atStartOfDay(zone).toInstant().toEpochMilli()
        val sinceStart = readings.filter { it.epochMs >= startMs }
        val lowest = sinceStart.minByOrNull { it.kg }
        val weekAgo = nowMs - 7 * DAY_MS
        val daysWeighed = readings.filter { it.epochMs > weekAgo }
            .mapTo(HashSet()) { Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate() }.size

        val recent = readings.takeLast(5).let { tail ->
            val offset = readings.size - tail.size
            tail.mapIndexed { i, entry ->
                val previous = readings.getOrNull(offset + i - 1)
                Reading(entry.epochMs, entry.kg, previous?.let { entry.kg - it.kg })
            }.asReversed()
        }

        return Insights(
            weekChangeKg = changeSince(7),
            monthChangeKg = changeSince(30),
            average7Kg = average(7, 0),
            averagePrevious7Kg = average(14, 7),
            lowestKg = lowest?.kg,
            lowestMs = lowest?.epochMs,
            daysWeighedThisWeek = daysWeighed,
            daysSinceLastWeighIn = latest?.let {
                ChronoUnit.DAYS.between(Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate(), today)
            },
            daysSinceStart = ChronoUnit.DAYS.between(snap.startDay, today).coerceAtLeast(0),
            recent = recent,
        )
    }

    /** True when a change moves towards the target rather than away from it. */
    fun towardGoal(changeKg: Double, losing: Boolean): Boolean = if (losing) changeKg < 0 else changeKg > 0
}
