package io.github.ebziboi.delta.data

import java.time.*
import java.time.temporal.ChronoUnit
import kotlin.math.sqrt

enum class MeasurementKind(val label: String) { WEIGHT("Weight"), BODY_FAT("Body fat") }
data class MeasurementReading(val epochMs: Long, val value: Double)
data class MeasurementReport(
    val kind: MeasurementKind, val from: LocalDate, val through: LocalDate,
    val readings: List<MeasurementReading>, val daily: Map<LocalDate,Double>,
    val previousDaily: Map<LocalDate,Double>, val weeklyRate: Double?, val deviation: Double?,
    val longestStreak: Int, val largestGap: Long,
) {
    val average: Double? get() = daily.values.takeIf {it.isNotEmpty()}?.average()
    val previousAverage: Double? get() = previousDaily.values.takeIf {it.isNotEmpty()}?.average()
    val change: Double? get() = if(daily.size>=2) daily.values.last()-daily.values.first() else null
    val coverageDays: Long get() = ChronoUnit.DAYS.between(from,through)+1
}

/** Calendar and insight summaries use the same filtered, de-duplicated records. Missing dates remain missing. */
object MeasurementDetails {
    fun readings(weights: List<WeightEntry>, fat: List<BodyFatEntry>, kind: MeasurementKind,
                 now: Instant=Instant.now()): List<MeasurementReading> {
        val source=if(kind==MeasurementKind.WEIGHT) weights else fat.filter {BodyFat.valid(it.percent)}
            .map {WeightEntry(it.epochMs,it.percent,it.own,it.id)}
        return InsightsCalculator.distinctReadings(source.filter {it.epochMs<=now.toEpochMilli() && it.kg.isFinite() && it.kg>0})
            .map {MeasurementReading(it.epochMs,it.kg)}
    }
    fun grouped(readings: List<MeasurementReading>, zone: ZoneId=ZoneId.systemDefault()): Map<LocalDate,List<MeasurementReading>> =
        readings.groupBy {Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate()}.toSortedMap()

    fun report(kind: MeasurementKind, readings: List<MeasurementReading>, from: LocalDate, through: LocalDate,
               zone: ZoneId=ZoneId.systemDefault()): MeasurementReport {
        require(from<=through)
        val grouped=grouped(readings,zone)
        val days=grouped.filterKeys {it in from..through}.mapValues {(_,values)->values.map {it.value}.average()}.toSortedMap()
        val length=ChronoUnit.DAYS.between(from,through)+1
        val previous=grouped.filterKeys {it in from.minusDays(length)..from.minusDays(1)}
            .mapValues {(_,values)->values.map {it.value}.average()}.toSortedMap()
        val selected=grouped.filterKeys {it in from..through}.values.flatten()
        val mean=days.values.takeIf {it.isNotEmpty()}?.average()
        val deviation=mean?.let {m->sqrt(days.values.sumOf {(it-m)*(it-m)}/days.size)}
        val origin=days.keys.firstOrNull()
        val rate=if(days.size>=3 && ChronoUnit.DAYS.between(days.firstKey(),days.lastKey())>=6) {
            val xs=days.keys.map {ChronoUnit.DAYS.between(origin,it).toDouble()};val ys=days.values.toList()
            val mx=xs.average();val my=ys.average();val denominator=xs.sumOf {(it-mx)*(it-mx)}
            if(denominator>0) xs.indices.sumOf {(xs[it]-mx)*(ys[it]-my)}/denominator*7 else null
        } else null
        var run=0;var longest=0;var last:LocalDate?=null;var gap=0L
        for(day in days.keys) {
            val distance=last?.let {ChronoUnit.DAYS.between(it,day)}
            run=if(distance==1L) run+1 else 1;longest=maxOf(longest,run)
            if(distance!=null) gap=maxOf(gap,distance-1);last=day
        }
        return MeasurementReport(kind,from,through,selected,days,previous,rate,deviation,longest,gap)
    }
}
