package io.github.ebziboi.delta.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Dates and times as the screens write them. */
object Format {
    private val day: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val dayOfWeek: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
    private val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    fun day(date: LocalDate): String = date.format(day)

    /** "Today", "Yesterday" or the full date. */
    fun relativeDay(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(dayOfWeek)
    }

    /** "Today, 07:10" or "Sat 14 Nov 2026, 07:10". */
    fun moment(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val at = Instant.ofEpochMilli(epochMs).atZone(zone)
        return "${relativeDay(at.toLocalDate(), LocalDate.now(zone))}, ${at.format(time)}"
    }
}
