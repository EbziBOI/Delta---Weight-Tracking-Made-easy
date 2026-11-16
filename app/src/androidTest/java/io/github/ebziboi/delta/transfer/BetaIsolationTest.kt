package io.github.ebziboi.delta.transfer

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ebziboi.delta.BuildConfig
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.icons.LauncherIcons
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class BetaIsolationTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun packagePermissionsAndAllLauncherAliasesAreIndependent() {
        assertTrue(BuildConfig.IS_BETA);assertEquals(TransferSnapshot.BETA,context.packageName)
        val pm=context.packageManager
        val permissions=pm.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(permissions.any {it.startsWith("android.permission.health.WRITE_")})
        assertTrue(HealthConnectManager.READ_NUTRITION in permissions)
        val previous=pm.getLaunchIntentForPackage(context.packageName)!!.component!!
        try {
            for(icon in LauncherIcons.all) {
                LauncherIcons.apply(context,icon.id)
                val component=ComponentName(context,"io.github.ebziboi.delta.Launcher${icon.id}")
                assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED,pm.getComponentEnabledSetting(component))
                assertNotNull(pm.getLaunchIntentForPackage(context.packageName))
            }
        } finally {
            LauncherIcons.apply(context,LauncherIcons.all.firstOrNull {previous.className.endsWith("Launcher${it.id}")}?.id ?: "Classic")
        }
    }
    @Test fun logsDeletesRefreshAndImportsNeverWriteToHealthConnect()=runBlocking {
        val app=context.applicationContext as DeltaApp;val store=SettingsStore(context)
        val before=store.weights.first();val fat=store.bodyFat.first()
        try {
            store.saveBetaMeasurements(emptyList(),emptyList())
            assertTrue(app.repo.logWeight(95.5,Instant.now(),27.1))
            val w=store.weights.first().single();val f=store.bodyFat.first().single()
            assertTrue(w.id.startsWith("beta-local:"));assertTrue(f.id.startsWith("beta-local:"))
            app.repo.sync();assertEquals(listOf(w),store.weights.first());assertEquals(listOf(f),store.bodyFat.first())
            app.repo.delete(w);app.repo.deleteBodyFat(f)
            assertTrue(store.weights.first().isEmpty());assertTrue(store.bodyFat.first().isEmpty())
            try {app.repo.health.writeMeasurement(96.0,null,Instant.now());fail("Beta must not call HC writes")} catch(_:IllegalStateException){}
            assertFalse(app.repo.health.state().missing.any {it==HealthConnectManager.WRITE_WEIGHT || it==HealthConnectManager.WRITE_BODY_FAT || it==HealthConnectManager.BACKGROUND})
        } finally {store.saveBetaMeasurements(before,fat)}
    }
    @Test fun transferValidatesSchemaAndPreservesGoalsAppearanceAndSnapshots()=runBlocking {
        val store=SettingsStore(context);val original=store.settings.first();val w=store.weights.first();val f=store.bodyFat.first()
        try {
            store.update {it.copy(targetKg=81.7,unit=WeightUnit.LB,appearance=DesignStyle.TUBIK.defaults(),homeCardOrder=CardLayouts.home.reversed())}
            val source=JSONObject().put("format","delta-beta-copy").put("schema",1).put("source",TransferSnapshot.STABLE)
                .put("settings",store.exportForBeta()).put("images",JSONObject())
                .put("weights",JSONArray().put(JSONObject().put("id","source-one").put("time",123456789L).put("kg",94.2)))
                .put("bodyFat",JSONArray().put(JSONObject().put("id","source-fat").put("time",123456789L).put("percent",26.2)))
            store.update {AppSettings()}
            TransferSnapshot.apply(context,TransferSnapshot.parse(source.toString()))
            val loaded=store.settings.first()
            assertTrue(loaded.onboardingDone);assertEquals(81.7,loaded.targetKg!!,.001);assertEquals(WeightUnit.LB,loaded.unit)
            assertEquals(DesignStyle.TUBIK,loaded.appearance!!.designStyle);assertEquals(CardLayouts.home.reversed(),loaded.homeCardOrder)
            assertEquals("beta-copy:source-one",store.weights.first().single().id);assertTrue(store.weights.first().single().own)
            assertEquals(26.2,store.bodyFat.first().single().percent,.001)
            try {TransferSnapshot.parse(source.put("source","another.app").toString());fail("Reject wrong source")} catch(_:IllegalArgumentException){}
        } finally {store.saveBetaMeasurements(w,f);store.update {original}}
    }
}
