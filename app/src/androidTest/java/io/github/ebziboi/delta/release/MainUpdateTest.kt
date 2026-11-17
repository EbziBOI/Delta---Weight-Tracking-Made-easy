package io.github.ebziboi.delta.release

import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.icons.LauncherIcons
import io.github.ebziboi.delta.transfer.FullTransfer
import io.github.ebziboi.delta.ui.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Run prepare on the signed debug APK, then verify after replacing it with the release APK. */
class MainUpdateTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val original get()=File(context.noBackupFilesDir,"before-main43-update-test.json")
    private val expected get()=File(context.noBackupFilesDir,"expected-main43-update-test.json")
    @Test fun preparePersistedChoicesAndIcon()=runBlocking {
        original.writeText(FullTransfer.create(context).toString())
        SettingsStore(context).update {it.copy(onboardingDone=true,haptics=false,appIcon="Icon07")}
        context.getSharedPreferences("home-chart",0).edit().putString("series","WEIGHT,BODY_FAT,NUTRITION").commit()
        ChartController(context,"home-trends",ChartStyle.DOTS).apply {choosePeriod(ChartPeriod.WEEKLY);chooseWindow("1Y");chooseMode(ChartStyle.TREND)}
        ChartController(context,"measurement-BODY_FAT",ChartStyle.DOTS).apply {choosePeriod(ChartPeriod.MONTHLY);chooseWindow("6M");chooseMode(ChartStyle.CONNECTED)}
        LauncherIcons.apply(context,"Icon07")
        // Chart choices are saved asynchronously; the update replaces this process at once, so finish the write first.
        context.getSharedPreferences("home-chart",0).edit().commit()
        expected.writeText(FullTransfer.create(context).toString())
    }
    @Test fun verifySignedReleaseKeptDataChartsHapticsAndIcon()=runBlocking {
        try {
            assertEquals("io.github.ebziboi.delta",context.packageName)
            assertEquals(0,context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
            val previous=FullTransfer.parse(expected.readText())
            val current=FullTransfer.parse(FullTransfer.create(context).toString())
            assertEquals(previous.nutrition,current.nutrition)
            assertEquals(previous.measurements.weights,current.measurements.weights)
            assertEquals(previous.measurements.fat,current.measurements.fat)
            val settings=SettingsStore(context).settings.first();assertFalse(settings.haptics);assertEquals("Icon07",settings.appIcon)
            val home=ChartController(context,"home-trends",ChartStyle.DOTS)
            assertEquals(ChartPeriod.WEEKLY,home.period);assertEquals("1Y",home.window);assertEquals(ChartStyle.TREND,home.mode)
            val fat=ChartController(context,"measurement-BODY_FAT",ChartStyle.DOTS)
            assertEquals(ChartPeriod.MONTHLY,fat.period);assertEquals("6M",fat.window);assertEquals(ChartStyle.CONNECTED,fat.mode)
            assertEquals("WEIGHT,BODY_FAT,NUTRITION",context.getSharedPreferences("home-chart",0).getString("series",null))
        } finally {
            if(original.exists()) {FullTransfer.apply(context,original.readText());original.delete()}
            expected.delete()
        }
    }
}
