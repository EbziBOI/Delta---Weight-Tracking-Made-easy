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
import io.github.ebziboi.delta.data.ChartStyle
import io.github.ebziboi.delta.data.ProgressCalculator
import io.github.ebziboi.delta.data.SettingsStore
import io.github.ebziboi.delta.data.SyncResult
import io.github.ebziboi.delta.data.ThemeStyle
import io.github.ebziboi.delta.data.TypeStyle
import io.github.ebziboi.delta.data.WidgetGlass
import io.github.ebziboi.delta.data.WidgetTheme
import io.github.ebziboi.delta.render.WidgetBitmaps
import io.github.ebziboi.delta.render.WidgetKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.regex.Pattern
import kotlin.math.abs
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
    init {
        // Continuous sync indicators should not force a ten-second idle timeout before every action.
        androidx.test.uiautomator.Configurator.getInstance().setWaitForIdleTimeout(1_000)
    }
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device: UiDevice = UiDevice.getInstance(instrumentation)
    private val pkg: String = context.packageName
    private val out: File = File(context.filesDir, "smoke").apply { mkdirs() }
    private val repo get() = (context.applicationContext as DeltaApp).repo

    private val startDay: LocalDate = LocalDate.now().minusDays(74)

    /** One way the widgets can look: a name for the picture, the settings, and the phone's mode. */
    private class Look(val name: String, val settings: AppSettings, val night: Boolean)

    @Test
    fun t1_widgetsDrawOnDevice() {
        val entries = SampleData.entries(startDay)
        val base = AppSettings(
            theme = ThemeStyle.AURORA, accent = Accent.SKY,
            onboardingDone = true,
            targetKg = 80.0,
            startEpochDay = startDay.toEpochDay(),
            targetEpochDay = LocalDate.now().plusDays(95).toEpochDay(),
        )
        val clear = base.copy(
            widgetGlass = WidgetGlass.CLEAR,
            widgetOpacityDark = WidgetGlass.CLEAR.defaultDark,
            widgetOpacityLight = WidgetGlass.CLEAR.defaultLight,
        )
        val looks = listOf(
            Look("aurora-frosted", base, night = true),
            Look("aurora-clear", clear, night = true),
            Look("aurora-solid-connected", base.copy(widgetGlass = WidgetGlass.SOLID, widgetChart = ChartStyle.CONNECTED), night = true),
            Look("midnight-modern", base.copy(theme = ThemeStyle.MIDNIGHT, typeStyle = TypeStyle.MODERN), night = true),
            Look("graphite-technical", base.copy(theme = ThemeStyle.GRAPHITE, accent = Accent.MINT, typeStyle = TypeStyle.TECHNICAL), night = true),
            Look("neon", base.copy(theme = ThemeStyle.NEON, accent = Accent.LIME), night = true),
            Look("light-frosted", base.copy(theme = ThemeStyle.LIGHT, accent = Accent.VIOLET), night = false),
            Look("light-clear-connected", clear.copy(theme = ThemeStyle.LIGHT, accent = Accent.ORANGE, widgetChart = ChartStyle.CONNECTED), night = false),
            Look("follow-phone-day", base.copy(widgetTheme = WidgetTheme.SYSTEM), night = false),
            Look("follow-phone-night", base.copy(widgetTheme = WidgetTheme.SYSTEM), night = true),
            Look("material-you-day", base.copy(theme = ThemeStyle.DYNAMIC), night = false),
            Look("material-you-night", base.copy(theme = ThemeStyle.DYNAMIC), night = true),
        )
        val matrix = ThemeStyle.entries.flatMap { theme -> WidgetGlass.entries.flatMap { glass ->
            listOf(false, true).map { night -> Look("matrix-${theme.name.lowercase()}-${glass.name.lowercase()}-${if (night) "night" else "day"}",
                base.copy(theme = theme, accent = if (theme == ThemeStyle.EMBER) Accent.ORANGE else base.accent, widgetGlass = glass, widgetOpacityDark = glass.defaultDark, widgetOpacityLight = glass.defaultLight), night)
            }
        } }
        val density = context.resources.displayMetrics.density
        val gap = (16 * density).roundToInt()

        for (look in looks + matrix) {
            val snap = ProgressCalculator.compute(entries, look.settings)
            fun draw(kind: WidgetKind, widthDp: Float, heightDp: Float) =
                WidgetBitmaps.render(context, kind, widthDp, heightDp, snap, look.settings, night = look.night)
            val progress = draw(WidgetKind.PROGRESS, 172f, 190f)
            val pace = draw(WidgetKind.PACE, 172f, 190f)
            val summary = draw(WidgetKind.SUMMARY, 360f, 110f)
            val journey = draw(WidgetKind.JOURNEY, 360f, 200f)
            val trend = draw(WidgetKind.TREND, 360f, 190f)

            // One picture per look, laid out like a home screen over a busy wallpaper.
            val width = summary.width + gap * 2
            val height = gap * 5 + progress.height + summary.height + journey.height + trend.height
            val sheet = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            SampleData.wallpaper(canvas, width, height, light = !look.night)
            var y = gap.toFloat()
            canvas.drawBitmap(progress, gap.toFloat(), y, null)
            canvas.drawBitmap(pace, (width - gap - pace.width).toFloat(), y, null)
            y += progress.height + gap
            canvas.drawBitmap(summary, gap.toFloat(), y, null)
            y += summary.height + gap
            canvas.drawBitmap(journey, gap.toFloat(), y, null)
            y += journey.height + gap
            canvas.drawBitmap(trend, gap.toFloat(), y, null)
            SampleData.save(sheet, File(out, "widgets-${look.name}.png"))
            listOf(progress, pace, summary, journey, trend, sheet).forEach(Bitmap::recycle)
        }

        // Every widget from the smallest size the home screen allows to very large, so
        // anything cut off or crowded at an unusual size shows up.
        val sizes = listOf(110f to 50f, 110f to 110f, 172f to 190f, 250f to 110f, 360f to 110f, 360f to 200f, 200f to 380f, 420f to 420f)
        val look = base
        val snap = ProgressCalculator.compute(entries, look)
        for (kind in WidgetKind.values()) {
            val drawn = sizes.map { (w, h) -> WidgetBitmaps.render(context, kind, w, h, snap, look, night = true) }
            val width = gap + drawn.sumOf { it.width + gap }
            val height = gap * 2 + drawn.maxOf { it.height }
            val sheet = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            SampleData.wallpaper(canvas, width, height, light = false)
            var x = gap.toFloat()
            for (bitmap in drawn) {
                canvas.drawBitmap(bitmap, x, gap.toFloat(), null)
                x += bitmap.width + gap
            }
            SampleData.save(sheet, File(out, "widgets-sizes-${kind.name.lowercase()}.png"))
            drawn.forEach(Bitmap::recycle)
            sheet.recycle()
        }
    }

    @Test
    fun t2_firstRunAndHealthConnectScreens() {
        runBlocking { SettingsStore(context).update { it.copy(onboardingDone = false) } }
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

        // Health Connect may coalesce writes with identical timestamps on repeated runs.
        val offsetMs = System.currentTimeMillis() % 3_600_000L + 1L
        val sample = SampleData.entries(startDay).map { it.copy(epochMs = it.epochMs + offsetMs) }
        runBlocking {
            val state = repo.health.state()
            check(state.canRead && state.canWrite) { "Health Connect access could not be granted: $state" }

            repo.sync()
            val beforeIds = repo.entries.first().map { it.id }.toSet()
            for (entry in sample) {
                repo.health.writeWeight(entry.kg, Instant.ofEpochMilli(entry.epochMs))
            }
            val result = repo.sync()
            check(result is SyncResult.Done) { "Sync did not complete: $result" }

            val stored = repo.entries.first().filter { it.id !in beforeIds }
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
            check(repo.entries.first().size == beforeIds.size + sample.size - 1) { "A deleted weigh-in was still there" }
            check(repo.logWeight(oldest.kg, Instant.ofEpochMilli(oldest.epochMs))) { "Logging a weigh-in failed" }
            check(repo.entries.first().size == beforeIds.size + sample.size) { "A logged weigh-in did not appear" }

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

        // Changing theme restyles the whole app at once.
        tap("Appearance")
        waitFor("APP ICON")
        shot("13-appearance")
        tap("Neon")
        shot("14-theme-neon")
        tap("Light")
        shot("15-theme-light")
        tap("Graphite")
        shot("16-theme-graphite")
        tap(if (InstrumentationRegistry.getArguments().getString("delta.theme") == "EMBER") "Ember" else "Aurora")
        swipeUp()
        shot("17-appearance-lower")
        device.pressBack()

        tap("Graphs")
        waitFor("Connected dots")
        shot("18-graphs")
        tap("Connected dots")
        shot("19-graphs-connected")
        device.pressBack()

        // Widget looks are edited from the home screen; here the page only adds widgets.
        tap("Widgets")
        waitFor("CHANGING A WIDGET'S LOOK")
        shot("20-widgets")
        device.pressBack()
        waitFor("Target weight")
        swipeUp()
        shot("22-settings-lower")

        if (!saved) throw AssertionError("Saving from the Log weight dialog was not confirmed")
    }

    @Test
    fun t5_widgetsOnHomeScreen() {
        setGoal()
        launch()
        waitForMainScreen()
        tap("Settings")
        waitFor("Target weight")
        tap("Widgets")
        waitFor("CHANGING A WIDGET'S LOOK")
        repeat(6) { swipeUp() }
        shot("23-settings-widgets")

        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val confirm = Pattern.compile("(?i)add to home ?screen|add automatically|add")
        for (kind in WidgetKind.entries) {
            val component = android.content.ComponentName(context, io.github.ebziboi.delta.widget.WidgetCatalog.receiver(kind))
            val previous = manager.getAppWidgetIds(component).size
            check(manager.requestPinAppWidget(component, null, null)) { "Launcher rejected ${kind.title}" }
            val accept = device.wait(Until.findObject(By.text(confirm).clickable(true)), 10_000)
                ?: device.findObjects(By.text(confirm)).lastOrNull()
                ?: throw AssertionError("No launcher confirmation for ${kind.title}")
            shot("24-add-${kind.name.lowercase()}-prompt")
            accept.click()
            val deadline = System.currentTimeMillis() + 15_000
            while (manager.getAppWidgetIds(component).size <= previous && System.currentTimeMillis() < deadline) Thread.sleep(500)
            check(manager.getAppWidgetIds(component).size > previous) { "${kind.title} was not added" }
            device.pressHome()
            Thread.sleep(2_000)
            shot("25-home-${kind.name.lowercase()}")
            launch()
            waitForMainScreen()
        }
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
                appearance = InstrumentationRegistry.getArguments().getString("delta.preset")?.let { name ->
                    io.github.ebziboi.delta.data.AppearancePreset.entries.firstOrNull { p -> p.name == name }?.appearance()
                },
                theme = if (InstrumentationRegistry.getArguments().getString("delta.theme") == "EMBER") ThemeStyle.EMBER else ThemeStyle.AURORA,
                accent = if (InstrumentationRegistry.getArguments().getString("delta.theme") == "EMBER") Accent.ORANGE else Accent.SKY,
                typeStyle = TypeStyle.MODERN,
            )
        }
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
            instrumentation.uiAutomation.clearCache()
            if (device.wait(Until.hasObject(selector), 1_500) == true) return
        }
        shot("failed-waiting-for-${what.filter { it.isLetterOrDigit() || it == ' ' }.replace(' ', '-')}")
        throw AssertionError("\"$what\" never appeared")
    }

    private fun visibleTarget(text: String): androidx.test.uiautomator.UiObject2? {
        instrumentation.uiAutomation.clearCache()
        val candidates = device.findObjects(By.text(text))
        if (text in listOf("Progress", "History", "Settings"))
            return candidates.lastOrNull { it.visibleBounds.centerY() > device.displayHeight * 0.82f }
        // Onboarding has no floating dock or log button; its actions can sit near the bottom.
        val bottomLimit = if (device.hasObject(By.desc("Log weight"))) 0.82f else 0.94f
        return candidates.firstOrNull {
            val b = it.visibleBounds
            b.height() > 12 && b.centerY() > 100 && b.centerY() < device.displayHeight * bottomLimit
        }
    }

    private fun tap(text: String) {
        if (text == "Progress" || text == "History") {
            instrumentation.uiAutomation.clearCache()
            if (device.findObjects(By.text(text)).none { it.visibleBounds.centerY() > device.displayHeight * 0.8f }) {
                val edgeX = device.displayWidth / 12
                device.swipe(edgeX, device.displayHeight * 40 / 100, edgeX, device.displayHeight * 60 / 100, 65)
                device.waitForIdle(1000); Thread.sleep(400)
            }
        }
        if (text == "Settings") { device.wait(Until.findObject(By.desc("Settings")), 10000)!!.click(); device.waitForIdle(1000); Thread.sleep(300); return }
        for (attempt in 0 until 12) {
            if (visibleTarget(text) != null) break
            scrollPage()
        }
        if (visibleTarget(text) == null) for (attempt in 0 until 16) {
            if (visibleTarget(text) != null) break
            scrollPage(false)
        }
        repeat(3) {
            try {
                val target = visibleTarget(text) ?: throw StaleObjectException()
                var clickable = target
                while (!clickable.isClickable && clickable.parent != null) clickable = clickable.parent
                (if (clickable.isClickable) clickable else target).click()
                device.waitForIdle(1_000)
                Thread.sleep(300)
                return
            } catch (_: StaleObjectException) { Thread.sleep(400) }
        }
        throw AssertionError("Could not tap $text")
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

    private fun scrollPage(forward: Boolean = true) {
        // Short overlapping swipes avoid sliders and don't skip controls behind the floating dock.
        val x = device.displayWidth * 7 / 100
        val lower = device.displayHeight * 7 / 10
        val upper = device.displayHeight * 4 / 10
        device.swipe(x, if (forward) lower else upper, x, if (forward) upper else lower, 24)
        device.waitForIdle(1_000)
        Thread.sleep(300)
    }

    private fun swipeUp() = scrollPage()

    private fun shot(name: String) {
        device.waitForIdle()
        Thread.sleep(800)
        device.takeScreenshot(File(out, "$name.png"))
    }

    private fun note(line: String) {
        File(out, "notes.txt").appendText(line + "\n")
    }
}
