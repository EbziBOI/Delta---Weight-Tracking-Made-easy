package io.github.ebziboi.delta.data

import java.time.*
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.ceil

enum class DetailMetric(val label: String) { RATE("Weekly rate"), AVERAGE("7-day average"), STREAK("Streak"), GOAL("Goal by"), WEIGHT("Weight trend"), BODY_FAT("Body composition"), INSIGHTS("Insights") }
data class MetricDay(val day: LocalDate, val kg: Double?, val averageKg: Double?, val rateKg: Double?, val streak: Int, val readings: Int)

/** Calendar values use only readings available by the end of that day, never future data. */
object MetricDetails {
    fun monday(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun weekStart(day: LocalDate, sundayIndex: Int): LocalDate = day.with(
        TemporalAdjusters.previousOrSame(DayOfWeek.of((sundayIndex.coerceIn(0,6)+6)%7+1)))
    fun days(entries: List<WeightEntry>, settings: AppSettings, week: LocalDate,
             today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): List<MetricDay> {
        val readings = InsightsCalculator.distinctReadings(entries).filter { it.kg.isFinite() }
        val grouped = readings.groupBy { Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate() }
        return (0L..6L).map { offset ->
            val date = week.plusDays(offset)
            if (date > today) MetricDay(date,null,null,null,0,0) else {
                val cutoff = date.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1)
                val history = readings.filter { it.epochMs <= cutoff.toEpochMilli() }
                val snap = ProgressCalculator.compute(history,settings,zone,cutoff)
                val window = grouped.filterKeys { it in date.minusDays(6)..date }.values.map { day -> day.map { it.kg }.average() }
                val current = grouped[date].orEmpty()
                MetricDay(date,current.takeIf { it.isNotEmpty() }?.map { it.kg }?.average(),
                    window.takeIf { it.isNotEmpty() }?.average(),snap.weeklyRateKg,if(date<today && current.isEmpty()) 0 else snap.streakDays,current.size)
            }
        }
    }
    fun project(current: Double?, target: Double?, rate: Double?, today: LocalDate = LocalDate.now()): LocalDate? {
        if (current == null || target == null || rate == null || !rate.isFinite() || abs(rate) < .02) return null
        val days=(target-current)/rate*7
        return if (days > 0 && days <= 365*5) today.plusDays(ceil(days).toLong()) else null
    }
}
