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
    private data class History(val daily:java.util.TreeMap<LocalDate,Double>,val counts:Map<LocalDate,Int>,val streaks:Map<LocalDate,Int>)
    private fun history(entries:List<WeightEntry>,today:LocalDate,zone:ZoneId):History {
        val rows=InsightsCalculator.distinctReadings(entries).filter {it.kg.isFinite() && it.kg>0}.groupBy {Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate()}.filterKeys {it<=today}
        val daily=java.util.TreeMap(rows.mapValues {(_,v)->v.map {it.kg}.average()})
        val streaks=mutableMapOf<LocalDate,Int>();var previous:LocalDate?=null;var count=0
        daily.keys.forEach {date->count=if(previous?.plusDays(1)==date) count+1 else 1;streaks[date]=count;previous=date}
        return History(daily,rows.mapValues {it.value.size},streaks)
    }
    private fun value(h:History,date:LocalDate,today:LocalDate):MetricDay {
        if(date>today) return MetricDay(date,null,null,null,0,0)
        val window=h.daily.subMap(date.minusDays(89),true,date,true)
        val seven=h.daily.subMap(date.minusDays(6),true,date,true).values
        val rate=recentPace(window,28,date).rate ?: recentPace(window,90,date).rate
        return MetricDay(date,h.daily[date],seven.takeIf {it.isNotEmpty()}?.average(),rate,
            h.streaks[date] ?: if(date==today) h.streaks[date.minusDays(1)] ?: 0 else 0,h.counts[date] ?: 0)
    }
    fun days(entries:List<WeightEntry>,settings:AppSettings,week:LocalDate,today:LocalDate=LocalDate.now(),zone:ZoneId=ZoneId.systemDefault()):List<MetricDay> {
        val h=history(entries,today,zone)
        return (0L..6L).map {value(h,week.plusDays(it),today)}
    }
    fun graphDays(entries:List<WeightEntry>,today:LocalDate=LocalDate.now(),zone:ZoneId=ZoneId.systemDefault()):List<MetricDay> {
        val h=history(entries,today,zone)
        if(h.daily.isEmpty()) return emptyList()
        return generateSequence(h.daily.firstKey()) {it.plusDays(1)}.takeWhile {it<=today}.map {value(h,it,today)}.toList()
    }
    fun project(current: Double?, target: Double?, rate: Double?, today: LocalDate = LocalDate.now()): LocalDate? {
        if (current == null || target == null || rate == null || !rate.isFinite() || abs(rate) < .02) return null
        val days=(target-current)/rate*7
        return if (days > 0 && days <= 365*5) today.plusDays(ceil(days).toLong()) else null
    }
}
