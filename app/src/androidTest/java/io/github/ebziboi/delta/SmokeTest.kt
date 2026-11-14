package io.github.ebziboi.delta

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.ebziboi.delta.data.Accent
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.data.ProgressCalculator
import io.github.ebziboi.delta.data.SettingsStore
import io.github.ebziboi.delta.data.SyncResult
import io.github.ebziboi.delta.data.ThemeStyle
import io.github.ebziboi.delta.data.WeightEntry
import io.github.ebziboi.delta.render.WidgetBitmaps
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Runs the app on a real Android system and photographs it: the widgets as
 * drawn by the device, first-run set-up including Health Connect's own
 * permission screens, a full write-and-read round trip through Health Connect,
 * the main screens, and the widgets sitting on a home screen. Pictures and
 * notes are saved in the app's files folder for the build to collect.
 *
 * The numbers are sample data, never anyone's real weight.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device: UiDevice = UiDevice.getInstance(instrumentation)
    private val pkg: String = context.packageName
    private val out: File = File(context.filesDir, "smoke").apply { mkdirs() }
    private val repo get() = (context.applicationContext as DeltaApp).repo

    private val startDay: LocalDate = LocalDate.now().minusDays(74)

    @Test
    fun t1_widgetsDrawOnDevice() {
        val entries = sampleEntries()
        val base = AppSettings(onboardingDone = true, targetKg = 80.0, startEpochDay = startDay.toEpochDay())
        val looks = listOf(
            "midnight" to base,
            "graphite" to base.copy(theme = ThemeStyle.GRAPHITE, accent = Accent.MINT),
            "neon" to base.copy(theme = ThemeStyle.NEON, accent = Accent.LIME),
            "light" to base.copy(theme = ThemeStyle.LIGHT, accent = Accent.VIOLET),
            "material-you" to base.copy(theme = ThemeStyle.DYNAMIC),
        )
        val density = context.resources.displayMetrics.density
        val gap = (16 * density).roundToInt()

        for ((name, look) in looks) {
            val snap = ProgressCalculator.compute(entries, look)
            val progress = WidgetBitmaps.progress(context, 172f, 190f, snap, look)
            val pace = WidgetBitmaps.pace(context, 172f, 190f, snap, look)
            val summary = WidgetBitmaps.summary(context, 360f, 110f, snap, look)
            val trend = WidgetBitmaps.trend(context, 360f, 190f, snap, look)

            // One picture per theme, laid out like a home screen.
            val width = summary.width + gap * 2
            val height = gap * 4 + progress.height + summary.height + trend.height
            val sheet = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(if (look.theme == ThemeStyle.LIGHT) 0xFFC9D2E3.toInt() else 0xFF20232E.toInt())
            var y = gap.toFloat()
            canvas.drawBitmap(progress, gap.toFloat(), y, null)
            canvas.drawBitmap(pace, (width - gap - pace.width).toFloat(), y, null)
            y += progress.height + gap
            canvas.drawBitmap(summary, gap.toFloat(), y, null)
            y += summary.height + gap
            canvas.drawBitmap(trend, gap.toFloat(), y, null)

            File(out, "widgets-$name.png").outputStream().use {
                sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test
    fun t2_firstRunAndHealthConnectScreens() {
        launch()
        waitFor("Get started")
        shot("01-welcome")
        tap("Get started")
        waitFor("Connect Health Connect")
        shot("02-connect")

        // "Connect" must bring up Health Connect's own permission screens. If the app's
        // declarations were wrong, the system would refuse silently and nothing would appear.
        tap("Connect")
        val appeared = allowHealthAccess()
        repeat(3) {
            if (device.currentPackageName != pkg) {
                device.pressBack()
                Thread.sleep(700)
            }
        }
        device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 8_000)
        Thread.sleep(1_500)
        shot("04-connect-after")

        val state = runBlocking { repo.health.state() }
        note("Health Connect permission screens appeared: $appeared")
        note(
            "After those screens: read=${state.canRead} write=${state.canWrite} " +
                "history=${state.hasHistory} background=${state.hasBackground}"
        )

        val next = device.findObject(By.text("Continue")) ?: device.findObject(By.text("Skip for now"))
        next?.click()
        waitFor("Set your goal")
        shot("05-goal")

        if (!appeared) throw AssertionError("Health Connect's permission screens never appeared")
    }

    @Test
    fun t3_healthConnectRoundTrip() {
        // Anything the permission screens did not grant is granted directly, so the
        // data path is tested either way.
        val missing = runBlocking { repo.health.state() }.missing
        for (permission in missing) {
            runCatching { instrumentation.uiAutomation.grantRuntimePermission(pkg, permission) }
        }
        note("Permissions the test had to grant itself: ${if (missing.isEmpty()) "none" else missing.toString()}")

        val sample = sampleEntries()
        runBlocking {
            val state = repo.health.state()
            check(state.canRead && state.canWrite) { "Health Connect access could not be granted: $state" }

            for (entry in sample) {
                repo.health.writeWeight(entry.kg, Instant.ofEpochMilli(entry.epochMs))
            }
            val result = repo.sync()
            check(result is SyncResult.Done) { "Sync did not complete: $result" }

            val stored = repo.entries.first()
            check(stored.size == sample.size) { "Wrote ${sample.size} weigh-ins but read back ${stored.size}" }
            check(stored.all { it.own }) { "Weigh-ins written by Delta were not recognised as its own" }
            check(stored == stored.sortedBy { it.epochMs }) { "Weigh-ins did not come back oldest first" }
            for ((wrote, read) in sample.zip(stored)) {
                check(abs(wrote.kg - read.kg) < 0.001 && wrote.epochMs == read.epochMs) {
                    "Wrote ${wrote.kg} kg at ${wrote.epochMs} but read ${read.kg} kg at ${read.epochMs}"
                }
            }

            // Delete the oldest, check it is gone, then put it back for the screens that follow.
            val oldest = stored.first()
            check(repo.delete(oldest)) { "Deleting a weigh-in failed" }
            check(repo.entries.first().size == sample.size - 1) { "A deleted weigh-in was still there" }
            check(repo.logWeight(oldest.kg, Instant.ofEpochMilli(oldest.epochMs))) { "Logging a weigh-in failed" }
            check(repo.entries.first().size == sample.size) { "A logged weigh-in did not appear" }

            note(
                "Health Connect round trip: wrote ${sample.size}, read ${stored.size} back unchanged, " +
                    "deleted one, logged it again"
            )
        }
    }

    @Test
    fun t4_mainScreens() {
        setGoal()
        launch()
        waitForMainScreen()
        Thread.sleep(2_500)
        shot("06-progress")
        swipeUp()
        shot("07-progress-lower")
        swipeUp()
        shot("08-progress-trend")

        // Log a weigh-in the way a person would.
        device.findObject(By.desc("Log weight")).click()
        waitFor("Log weight")
        shot("09-log-weight")
        tap("Save")
        val saved = device.wait(Until.hasObject(By.text("Weigh-in saved")), 10_000) == true
        shot("10-after-saving")
        note("Logging a weigh-in through the screen confirmed it was saved: $saved")

        tap("History")
        waitFor(Pattern.compile(".*weigh-ins from Health Connect"))
        shot("11-history")

        tap("Settings")
        waitFor("Target weight")
        shot("12-settings")
        swipeUp()
        shot("13-settings-lower")

        // Changing theme restyles the whole app at once.
        tap("Neon")
        shot("14-theme-neon")
        tap("Light")
        shot("15-theme-light")
        tap("Graphite")
        shot("16-theme-graphite")
        tap("Midnight")

        if (!saved) throw AssertionError("Saving from the Log weight dialog was not confirmed")
    }

    @Test
    fun t5_widgetsOnHomeScreen() {
        setGoal()
        launch()
        waitForMainScreen()
        tap("Settings")
        waitFor("Target weight")
        repeat(3) { swipeUp() }
        shot("17-settings-widgets")

        val confirm = Pattern.compile("(?i)add to home ?screen|add automatically")
        device.wait(Until.hasObject(By.text("Add")), 10_000)
        val buttons = device.findObjects(By.text("Add")).size
        for (index in 0 until buttons) {
            val add = device.findObjects(By.text("Add")).getOrNull(index) ?: break
            add.click()
            // The home screen app shows its own confirmation. Its title can read the
            // same as its button, so insist on something clickable.
            val accept = device.wait(Until.findObject(By.text(confirm).clickable(true)), 8_000)
                ?: device.findObjects(By.text(confirm)).lastOrNull()
            if (index == 0) shot("18-add-widget-prompt")
            accept?.click()
            device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 5_000)
            Thread.sleep(1_000)
        }

        device.pressHome()
        Thread.sleep(6_000)
        shot("19-home")

        if (buttons == 0) throw AssertionError("No Add buttons: this home screen does not accept widgets from apps")
    }

    /**
     * Works through Health Connect's permission screens, allowing everything on
     * offer. Returns whether those screens appeared at all.
     */
    private fun allowHealthAccess(): Boolean {
        var appeared = false
        var quietChecks = 0
        for (step in 1..12) {
            device.waitForIdle()
            Thread.sleep(1_500)
            dismissSystemDialogs()
            val foreground = device.currentPackageName
            shot("03-health-connect-step-$step")
            note("Permission step $step: foreground is $foreground")

            if (foreground == pkg) {
                // One screen hands over to the next through our own app for a moment,
                // so only treat it as finished once it has stayed put.
                quietChecks++
                if (quietChecks >= 3) return appeared
                continue
            }
            quietChecks = 0
            appeared = true

            // A phone that has never used Health Connect shows its introduction first.
            if (clickIfPresent("(?i)get started")) continue

            // First screen: the data types. Second screen: past data and background access,
            // each with its own switch and no "Allow all".
            clickIfPresent("(?i)allow all")
            clickIfPresent("(?i)access past data")
            clickIfPresent("(?i)access data in the background")
            Thread.sleep(700)
            if (!clickIfPresent("(?i)allow", mustBeClickable = true)) clickIfPresent("(?i)allow")
        }
        return appeared
    }

    /**
     * Taps the first thing on screen whose text matches, if there is one. Screens
     * that are still changing can invalidate what was just found, so it retries.
     */
    private fun clickIfPresent(pattern: String, mustBeClickable: Boolean = false): Boolean {
        repeat(3) {
            try {
                var selector = By.text(Pattern.compile(pattern))
                if (mustBeClickable) selector = selector.clickable(true)
                val target = device.findObject(selector) ?: return false
                target.click()
                return true
            } catch (stale: StaleObjectException) {
                Thread.sleep(400)
            }
        }
        return false
    }

    /** Finishes set-up directly: a goal, a start date and a target date. Weigh-ins come from Health Connect. */
    private fun setGoal() = runBlocking {
        SettingsStore(context).update {
            it.copy(
                onboardingDone = true,
                targetKg = 80.0,
                startEpochDay = startDay.toEpochDay(),
                targetEpochDay = LocalDate.now().plusDays(95).toEpochDay(),
                theme = ThemeStyle.MIDNIGHT,
                accent = Accent.SKY,
            )
        }
    }

    /** About eleven weeks of weigh-ins: quick at first, slower later, with the odd missed day. */
    private fun sampleEntries(): List<WeightEntry> {
        val zone = ZoneId.systemDefault()
        val wobble = listOf(0.4, -0.3, 0.2, -0.5, 0.3, 0.1, -0.2, 0.5, -0.4, 0.0, 0.2)
        val entries = ArrayList<WeightEntry>()
        // Stops at yesterday, so that logging "today" through the screen adds a new day.
        for (day in 0..73) {
            if (day % 6 == 4 && day <= 66) continue
            val base = 96.4 - 9.0 * (1 - exp(-day / 55.0)) / (1 - exp(-74 / 55.0)) + (if (day in 38..47) 0.5 else 0.0)
            val kg = ((base + wobble[day % wobble.size]) * 10).roundToInt() / 10.0
            val at = startDay.plusDays(day.toLong()).atTime(7, 10).atZone(zone).toInstant().toEpochMilli()
            entries += WeightEntry(epochMs = at, kg = kg, own = true, id = "sample-$day")
        }
        return entries
    }

    private fun launch() {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: throw AssertionError("The app has no launcher activity")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 15_000)
    }

    /** The tabs only exist once set-up is done, and the log button must be findable by name. */
    private fun waitForMainScreen() {
        waitFor("History")
        waitFor(By.desc("Log weight"), "the Log weight button")
    }

    private fun waitFor(text: String) = waitFor(By.text(text), text)

    private fun waitFor(pattern: Pattern) = waitFor(By.text(pattern), pattern.toString())

    /** Waits for something to be on screen, clearing any system pop-up that gets in the way. */
    private fun waitFor(selector: BySelector, what: String) {
        val deadline = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < deadline) {
            dismissSystemDialogs()
            if (device.wait(Until.hasObject(selector), 1_500) == true) return
        }
        shot("failed-waiting-for-${what.filter { it.isLetterOrDigit() || it == ' ' }.replace(' ', '-')}")
        throw AssertionError("\"$what\" never appeared")
    }

    private fun tap(text: String) {
        waitFor(text)
        repeat(3) {
            try {
                device.findObject(By.text(text))?.click()
                device.waitForIdle()
                return
            } catch (stale: StaleObjectException) {
                Thread.sleep(400)
            }
        }
        throw AssertionError("Could not tap \"$text\"")
    }

    /**
     * A slow emulator sometimes reports that the home screen app is not
     * responding. That pop-up covers the app under test, so tell it to wait.
     */
    private fun dismissSystemDialogs() {
        repeat(3) {
            device.findObject(By.text(Pattern.compile(".*isn.t responding.*"))) ?: return
            clickIfPresent("(?i)wait")
            Thread.sleep(1_000)
        }
    }

    private fun swipeUp() {
        val x = device.displayWidth / 2
        device.swipe(x, device.displayHeight * 7 / 10, x, device.displayHeight * 3 / 10, 24)
        device.waitForIdle()
    }

    private fun shot(name: String) {
        device.waitForIdle()
        Thread.sleep(800)
        device.takeScreenshot(File(out, "$name.png"))
    }

    private fun note(line: String) {
        File(out, "notes.txt").appendText(line + "\n")
    }
}
