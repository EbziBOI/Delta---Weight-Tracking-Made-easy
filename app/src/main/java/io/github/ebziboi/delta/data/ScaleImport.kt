package io.github.ebziboi.delta.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** One weigh-in read from a file. */
data class ImportedReading(val epochMs: Long, val kg: Double, val bodyFatPercent: Double? = null)

/**
 * The weigh-ins in a smart scale's CSV export, grouped by the person they belong
 * to. A file without a person column has everything under one unnamed person.
 */
data class ScaleExport(val people: Map<String, List<ImportedReading>>) {
    val total: Int get() = people.values.sumOf { it.size }
}

class ScaleImportException(message: String) : Exception(message)

/**
 * Reads the CSV exports smart scale apps produce: a header row, then one row per
 * weigh-in. Columns are found by their headings rather than their position, so
 * exports that add, drop or reorder body composition columns still read. The
 * weight may be in kilograms or pounds, as the heading says.
 */
object ScaleImport {
    private val SENSIBLE_KG = 20.0..400.0
    private const val LB_PER_KG = 2.2046226218

    private val dateTimes = listOf(
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm",
        "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm", "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm",
        "dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy HH:mm",
    ).map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }
    private val datesOnly = listOf("yyyy-MM-dd", "yyyy/MM/dd", "dd/MM/yyyy", "dd.MM.yyyy")
        .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): ScaleExport {
        val lines = text.removePrefix("\uFEFF").lines().filter { it.isNotBlank() }
        if (lines.size < 2) throw ScaleImportException("That file has no weigh-ins in it.")
        val header = split(lines.first()).map { it.trim().lowercase(Locale.ENGLISH) }

        val timeColumn = header.indexOfFirst { it == "time" || it.startsWith("time") || it.startsWith("date") || it.contains("measure") }
        val weightColumn = header.indexOfFirst { it.startsWith("weight") }
        val personColumn = header.indexOfFirst { it.contains("family") || it.contains("member") || it == "user" || it == "name" }
        if (timeColumn < 0 || weightColumn < 0) {
            throw ScaleImportException("Delta couldn't find a date and a weight column in that file.")
        }
        // Total body fat only: subcutaneous/visceral fat and fat mass are different measurements.
        val normalizedHeaders = header.map { it.replace(Regex("[^a-z]"), "") }
        val bodyFatColumn = normalizedHeaders.indexOfFirst { it in setOf("bodyfat", "bodyfatpercent", "bodyfatpercentage", "bodyfatrate") }
        val fatColumn = if (bodyFatColumn >= 0) bodyFatColumn else normalizedHeaders.indexOfFirst { it in setOf("fat", "fatpercent", "fatpercentage") }
        val inPounds = header[weightColumn].contains("lb")

        val people = LinkedHashMap<String, MutableList<ImportedReading>>()
        for (line in lines.drop(1)) {
            val cells = split(line)
            val at = cells.getOrNull(timeColumn)?.let { time(it.trim(), zone) } ?: continue
            val raw = cells.getOrNull(weightColumn)?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: continue
            val kg = if (inPounds) raw / LB_PER_KG else raw
            if (kg !in SENSIBLE_KG) continue
            val person = if (personColumn >= 0) cells.getOrNull(personColumn)?.trim().orEmpty() else ""
            val fatRaw = cells.getOrNull(fatColumn)?.trim()?.removeSuffix("%")?.trim()?.replace(',', '.')
            val fat = fatRaw?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
            if (!fatRaw.isNullOrBlank() && (fat == null || (fat != 0.0 && !BodyFat.valid(fat))))
                throw ScaleImportException("Invalid body fat percentage in the file. No readings were imported.")
            people.getOrPut(person) { ArrayList() } += ImportedReading(at, kg, fat?.takeUnless { it == 0.0 })
        }
        if (people.isEmpty()) throw ScaleImportException("Delta couldn't read any weigh-ins from that file.")
        return ScaleExport(people.mapValues { (_, readings) -> readings.sortedBy { it.epochMs } })
    }

    private fun time(value: String, zone: ZoneId): Long? {
        for (format in dateTimes) {
            try { return LocalDateTime.parse(value, format).atZone(zone).toInstant().toEpochMilli() } catch (_: DateTimeParseException) {}
        }
        for (format in datesOnly) {
            try { return LocalDate.parse(value, format).atTime(8, 0).atZone(zone).toInstant().toEpochMilli() } catch (_: DateTimeParseException) {}
        }
        return null
    }

    /** Splits one CSV line, keeping commas inside double quotes. */
    private fun split(line: String): List<String> {
        val cells = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { cells += cell.toString(); cell.clear() }
                else -> cell.append(c)
            }
            i++
        }
        cells += cell.toString()
        return cells
    }
}
