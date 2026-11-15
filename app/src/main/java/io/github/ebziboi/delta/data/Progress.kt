package io.github.ebziboi.delta.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max

enum class Pace { AHEAD, ON_TRACK, BEHIND }

/** A weigh-in with the smoothed trend value at that moment. */
data class TrendPoint(val epochMs: Long, val kg: Double, val trendKg: Double)

/** Everything the screens and widgets show, worked out once from weigh-ins and settings. */
data class ProgressSnapshot(
    val startDay: LocalDate,
    /** The first weigh-in on or after the start day, whichever app or import it came from. */
    val startKg: Double?,
    /** When that first weigh-in was taken. */
    val startMs: Long?,
    val currentKg: Double?,
    val currentMs: Long?,
    val targetKg: Double?,
    /** True when the target is below the start, so "progress" means weight coming off. */
    val losing: Boolean,
    /** 0 to 1 from start to target. Null until start, current and target are all known. */
    val fraction: Float?,
    /** Current minus start, signed. */
    val changeKg: Double?,
    /** Distance left to the target, never negative. */
    val remainingKg: Double?,
    val reached: Boolean,
    /** Recent trend in kg per week, signed. */
    val weeklyRateKg: Double?,
    /** When the target is reached if the recent trend holds. */
    val projectedDay: LocalDate?,
    val targetDay: LocalDate?,
    /** Rate needed to land on [targetDay], signed. */
    val requiredRateKg: Double?,
    val pace: Pace?,
    /** Days in a row with a weigh-in, counting back from today or yesterday. */
    val streakDays: Int,
    /** Weigh-ins since the start day. */
    val weighIns: Int,
    val points: List<TrendPoint>,
)

object ProgressCalculator {
    private const val DAY_MS = 86_400_000.0

    /** How many days either side carry most of the weight in the trend line. */
    private const val TREND_DAYS = 4.0

    /** Weigh-ins further away than this are ignored when smoothing. */
    private const val TREND_WINDOW_DAYS = 12.0

    /** The journey began "back in September": the most recent 1 September. */
    fun defaultStartDay(today: LocalDate): LocalDate {
        val september = LocalDate.of(today.year, 9, 1)
        return if (september.isAfter(today)) september.minusYears(1) else september
    }

    fun compute(
        entries: List<WeightEntry>,
        settings: AppSettings,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now(),
    ): ProgressSnapshot {
        val today = now.atZone(zone).toLocalDate()
        val sorted = entries.sortedBy { it.epochMs }

        val startDay = settings.startEpochDay?.let(LocalDate::ofEpochDay) ?: defaultStartDay(today)
        val startMs = startDay.atStartOfDay(zone).toInstant().toEpochMilli()
        val sinceStart = sorted.filter { it.epochMs >= startMs }

        // The journey starts at the first weigh-in on or after the start day, from any source:
        // Health Connect, another app, or an imported scale export.
        val startKg = sinceStart.firstOrNull()?.kg
        val latest = sorted.lastOrNull()
        val currentKg = latest?.kg
        val targetKg = settings.targetKg

        val reference = startKg ?: currentKg
        val losing = if (targetKg != null && reference != null) targetKg <= reference else true

        var remainingKg: Double? = null
        var reached = false
        if (targetKg != null && currentKg != null) {
            val left = if (losing) currentKg - targetKg else targetKg - currentKg
            reached = left <= 0.0
            remainingKg = max(left, 0.0)
        }

        var fraction: Float? = null
        if (startKg != null && targetKg != null && currentKg != null) {
            val total = startKg - targetKg
            fraction = when {
                reached -> 1f
                abs(total) < 1e-6 -> 0f
                else -> ((startKg - currentKg) / total).toFloat().coerceIn(0f, 1f)
            }
        }

        val changeKg = if (startKg != null && currentKg != null) currentKg - startKg else null

        val nowMs = now.toEpochMilli()
        val weeklyRateKg = slopePerWeek(sorted.filter { it.epochMs >= nowMs - 28 * DAY_MS })
            ?: slopePerWeek(sorted.filter { it.epochMs >= nowMs - 56 * DAY_MS })
            ?: slopePerWeek(sinceStart)
            ?: twoPointRate(sinceStart)

        var projectedDay: LocalDate? = null
        if (!reached && weeklyRateKg != null && remainingKg != null) {
            val towardTarget = if (losing) -weeklyRateKg else weeklyRateKg
            if (towardTarget > 0.02) {
                val days = ceil(remainingKg / towardTarget * 7.0)
                if (days <= 365.0 * 5) projectedDay = today.plusDays(days.toLong())
            }
        }

        val targetDay = settings.targetEpochDay?.let(LocalDate::ofEpochDay)
        var requiredRateKg: Double? = null
        var pace: Pace? = null
        if (targetDay != null && remainingKg != null && !reached) {
            val daysLeft = ChronoUnit.DAYS.between(today, targetDay)
            if (daysLeft > 0) {
                val perWeek = remainingKg / (daysLeft / 7.0)
                requiredRateKg = if (losing) -perWeek else perWeek
            }
            pace = when {
                projectedDay == null -> Pace.BEHIND
                projectedDay.isBefore(targetDay.minusDays(6)) -> Pace.AHEAD
                projectedDay.isAfter(targetDay.plusDays(6)) -> Pace.BEHIND
                else -> Pace.ON_TRACK
            }
        }

        return ProgressSnapshot(
            startDay = startDay,
            startKg = startKg,
            currentKg = currentKg,
            startMs = sinceStart.firstOrNull()?.epochMs,
            currentMs = latest?.epochMs,
            targetKg = targetKg,
            losing = losing,
            fraction = fraction,
            changeKg = changeKg,
            remainingKg = remainingKg,
            reached = reached,
            weeklyRateKg = weeklyRateKg,
            projectedDay = projectedDay,
            targetDay = targetDay,
            requiredRateKg = requiredRateKg,
            pace = pace,
            streakDays = streak(sorted, zone, today),
            weighIns = sinceStart.size,
            points = trend(sinceStart),
        )
    }

    /** Least-squares slope in kg per week. Needs three weigh-ins across at least six days. */
    private fun slopePerWeek(points: List<WeightEntry>): Double? {
        if (points.size < 3) return null
        val first = points.first().epochMs
        if ((points.last().epochMs - first) / DAY_MS < 6.0) return null

        var sumX = 0.0
        var sumY = 0.0
        var sumXX = 0.0
        var sumXY = 0.0
        for (point in points) {
            val x = (point.epochMs - first) / DAY_MS
            sumX += x
            sumY += point.kg
            sumXX += x * x
            sumXY += x * point.kg
        }
        val n = points.size
        val denominator = n * sumXX - sumX * sumX
        if (abs(denominator) < 1e-9) return null
        return (n * sumXY - sumX * sumY) / denominator * 7.0
    }

    /** Fallback when there are only two weigh-ins: the straight line between them. */
    private fun twoPointRate(points: List<WeightEntry>): Double? {
        if (points.size < 2) return null
        val days = (points.last().epochMs - points.first().epochMs) / DAY_MS
        if (days < 6.0) return null
        return (points.last().kg - points.first().kg) / days * 7.0
    }

    private fun streak(sorted: List<WeightEntry>, zone: ZoneId, today: LocalDate): Int {
        val days = sorted.mapTo(HashSet()) { Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate() }
        // A streak survives until the end of today: no weigh-in yet this morning is not a break.
        var cursor = when {
            today in days -> today
            today.minusDays(1) in days -> today.minusDays(1)
            else -> return 0
        }
        var count = 0
        while (cursor in days) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /**
     * A smooth line through the weigh-ins. At each one a straight line is fitted
     * to its neighbours, with nearby days counting most. Unlike a running average
     * this does not trail above the data while weight is steadily coming off.
     */
    private fun trend(points: List<WeightEntry>): List<TrendPoint> {
        val out = ArrayList<TrendPoint>(points.size)
        var windowStart = 0
        for (point in points) {
            val centre = point.epochMs
            while ((centre - points[windowStart].epochMs) / DAY_MS > TREND_WINDOW_DAYS) windowStart++

            var sumW = 0.0
            var sumWX = 0.0
            var sumWY = 0.0
            var sumWXX = 0.0
            var sumWXY = 0.0
            var index = windowStart
            while (index < points.size) {
                val days = (points[index].epochMs - centre) / DAY_MS
                if (days > TREND_WINDOW_DAYS) break
                val weight = exp(-0.5 * (days / TREND_DAYS) * (days / TREND_DAYS))
                val kg = points[index].kg
                sumW += weight
                sumWX += weight * days
                sumWY += weight * kg
                sumWXX += weight * days * days
                sumWXY += weight * days * kg
                index++
            }

            // The fitted line's value at this weigh-in; a plain weighted mean if a line cannot be fitted.
            val denominator = sumW * sumWXX - sumWX * sumWX
            val trendKg = if (abs(denominator) < 1e-9) {
                sumWY / sumW
            } else {
                (sumWXX * sumWY - sumWX * sumWXY) / denominator
            }
            out += TrendPoint(centre, point.kg, trendKg)
        }
        return out
    }
}
