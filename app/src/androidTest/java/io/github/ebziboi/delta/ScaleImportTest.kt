package io.github.ebziboi.delta

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ebziboi.delta.data.ScaleImport
import io.github.ebziboi.delta.data.ScaleImportException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset

/** Reads a smart scale export in the common "Time, Family Members, WEIGHT (kg), …" layout. Sample people only. */
@RunWith(AndroidJUnit4::class)
class ScaleImportTest {
    private val sample = """
        Time,Family Members,WEIGHT (kg),BMI,BODY FAT %,HEART RATE (bpm),MUSCLE MASS (kg),BODY TYPE,HEAD SIZE (cm)
        2026-11-13 13:36:13,Sam Example,94.9,27.1,28.1,113.0,64.9,Muscular Overweight,0.0
        2026-11-09 13:19:51,Sam Example,95.25,27.2,28.4,0.0,64.8,Muscular Overweight,0.0
        2026-11-09 13:19:02,Sam Example,95.25,0.0,0.0,0.0,0,,0.0
        2026-10-20 10:03:58,Sam Example,98.15,0.0,0.0,0.0,0,,0.0
        2026-10-21 02:03:38,Sam Example,0,0.0,0.0,0.0,0,,0.0
        2025-06-18 17:35:22,Alex Sample,75.55,0.0,0.0,0.0,0,,0.0
        2025-06-05 09:16:14,Alex Sample,74.8,23.0,19.9,255.0,57,Average,0.0
    """.trimIndent()

    @Test fun readsPeopleAndWeightsAndSkipsEmptyRows() {
        val export = ScaleImport.parse(sample, ZoneOffset.UTC)
        assertEquals(listOf("Sam Example", "Alex Sample"), export.people.keys.toList())
        val sam = export.people.getValue("Sam Example")
        assertEquals("The zero weight is skipped", 4, sam.size)
        assertEquals(98.15, sam.first().kg, 1e-9)
        assertEquals(94.9, sam.last().kg, 1e-9)
        assertEquals(2, export.people.getValue("Alex Sample").size)
    }

    @Test fun readsPoundsAndRejectsOtherFiles() {
        val pounds = ScaleImport.parse("Date,Weight (lb)\n2026-11-01 08:00,220.0\n", ZoneOffset.UTC)
        assertEquals(99.79, pounds.people.getValue("").single().kg, 0.01)
        val error = runCatching { ScaleImport.parse("Name,Score\nA,1\n") }.exceptionOrNull()
        assertTrue(error is ScaleImportException)
    }

    @Test fun startIsTheFirstWeighInAfterTheStartDateFromAnySource() {
        val zone = ZoneOffset.UTC
        val day = java.time.LocalDate.of(2026, 9, 1)
        fun at(date: String) = java.time.LocalDateTime.parse(date).atZone(zone).toInstant().toEpochMilli()
        val entries = listOf(
            io.github.ebziboi.delta.data.WeightEntry(at("2026-08-20T08:00"), 101.0, own = false, id = "before-start"),
            io.github.ebziboi.delta.data.WeightEntry(at("2026-10-05T09:00"), 97.9, own = false, id = "health-connect"),
            io.github.ebziboi.delta.data.WeightEntry(at("2026-09-23T11:49"), 98.85, own = true, id = "scale-import"),
            io.github.ebziboi.delta.data.WeightEntry(at("2026-11-13T13:36"), 94.9, own = false, id = "latest"),
        )
        val settings = io.github.ebziboi.delta.data.AppSettings(targetKg = 80.0, startEpochDay = day.toEpochDay())
        val snap = io.github.ebziboi.delta.data.ProgressCalculator.compute(entries, settings, zone,
            java.time.Instant.parse("2026-11-15T12:00:00Z"))
        assertEquals("The imported weigh-in is the first after the start date", 98.85, snap.startKg!!, 1e-9)
        assertEquals(at("2026-09-23T11:49"), snap.startMs)
        assertEquals((98.85 - 94.9) / (98.85 - 80.0), snap.fraction!!.toDouble(), 1e-4)
    }

    @Test fun importingTwiceAddsNothingTheSecondTime() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val repo = (context.applicationContext as DeltaApp).repo
        for (permission in repo.health.state().missing) {
            runCatching { instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission) }
        }
        check(repo.health.state().canWrite)
        // Recent dates, so they sit inside the window Delta reads back.
        val now = java.time.LocalDateTime.now().withNano(0).minusDays(2)
        val text = "Time,Family Members,WEIGHT (kg)\n" +
            "$now,Sam Example,91.3\n".replace('T', ' ') +
            "${now.plusSeconds(40)},Sam Example,91.3\n".replace('T', ' ') +
            "${now.minusDays(1)},Sam Example,91.8\n".replace('T', ' ')
        val readings = ScaleImport.parse(text).people.getValue("Sam Example")
        val first = repo.importReadings(readings)
        assertEquals("A reading saved twice within a minute counts once", 2, first.added + first.alreadyThere)
        val second = repo.importReadings(readings)
        assertEquals("Nothing new the second time", 0, second.added)
    }
}
