package io.github.ebziboi.delta.data

import java.time.Instant
import java.time.ZoneId

/** Each placed widget chooses its own history window; journey totals remain unchanged. */
enum class WidgetTimeRange(val label: String) {
    WEEK("1 week"), MONTH("1 month"), THREE_MONTHS("3 months"), YEAR("1 year"), JOURNEY("Journey");

    fun cutoff(snapshot: ProgressSnapshot, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Long {
        val at = now.atZone(zone)
        return when (this) {
            WEEK -> at.minusWeeks(1)
            MONTH -> at.minusMonths(1)
            THREE_MONTHS -> at.minusMonths(3)
            YEAR -> at.minusYears(1)
            JOURNEY -> snapshot.startDay.atStartOfDay(zone)
        }.toInstant().toEpochMilli()
    }

    fun weightPoints(snapshot: ProgressSnapshot, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): List<TrendPoint> {
        val points = if (this == JOURNEY) snapshot.points else snapshot.history
        val since = cutoff(snapshot, now, zone)
        return points.filter { it.epochMs >= since && it.epochMs <= now.toEpochMilli() }
    }

    fun bodyFat(snapshot: ProgressSnapshot, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): List<BodyFatEntry> {
        val since = cutoff(snapshot, now, zone)
        return snapshot.bodyFat.filter { it.epochMs >= since && it.epochMs <= now.toEpochMilli() }.sortedBy { it.epochMs }
    }
}
