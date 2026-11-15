package io.github.ebziboi.delta

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.data.ProgressCalculator
import io.github.ebziboi.delta.data.WidgetGlass
import io.github.ebziboi.delta.render.WidgetBitmaps
import io.github.ebziboi.delta.render.WidgetKind
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * Draws the pictures the home screen's widget picker shows, with the real
 * widget renderer and sample data, so the picker previews always match the
 * widgets. Run it on a device after changing how widgets look, then copy the
 * files into res/drawable-nodpi:
 *
 *     ./gradlew :app:connectedDebugAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=io.github.ebziboi.delta.WidgetPreviewRender
 *     adb exec-out run-as io.github.ebziboi.delta tar -cf - -C files previews | tar -xf - -C /tmp
 *     Copy the generated PNG files from /tmp/previews into res/drawable-nodpi.
 *
 * On Android 15 and later the app also publishes previews drawn from the
 * user's own data and theme, which replace these.
 */
@RunWith(AndroidJUnit4::class)
class WidgetPreviewRender {

    @Test
    fun renderPickerPreviews() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val out = File(context.filesDir, "previews").apply { mkdirs() }
        val startDay = LocalDate.now().minusDays(74)
        // Solid glass: in the picker a widget sits on the picker's own plain
        // background, where translucent glass would only look muddy.
        val settings = AppSettings(
            onboardingDone = true,
            targetKg = 80.0,
            startEpochDay = startDay.toEpochDay(),
            targetEpochDay = LocalDate.now().plusDays(95).toEpochDay(),
            widgetGlass = WidgetGlass.SOLID,
        )
        val snap = ProgressCalculator.compute(SampleData.entries(startDay), settings).copy(bodyFat = (0..30).map {
            io.github.ebziboi.delta.data.BodyFatEntry(java.time.Instant.now().minusSeconds((30L - it) * 86400).toEpochMilli(), 25.0 - it * 0.08, false, "preview-$it")
        })

        // Roughly each widget's default size on a phone home screen, in dp.
        val sizes = mapOf(
            WidgetKind.BODY_FAT to (350f to 180f),
            WidgetKind.PROGRESS to (170f to 170f),
            WidgetKind.JOURNEY to (350f to 180f),
            WidgetKind.SUMMARY to (350f to 100f),
            WidgetKind.TREND to (350f to 180f),
            WidgetKind.PACE to (170f to 170f),
        )
        for ((kind, size) in sizes) {
            val bitmap = WidgetBitmaps.render(
                context, kind, size.first, size.second, snap, settings,
                night = true, maxSidePx = 900f,
            )
            SampleData.save(bitmap, File(out, "widget_preview_${kind.name.lowercase()}.png"))
        }
    }
}
