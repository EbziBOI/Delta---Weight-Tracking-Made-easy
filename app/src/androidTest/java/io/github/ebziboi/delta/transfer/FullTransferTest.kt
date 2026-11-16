package io.github.ebziboi.delta.transfer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.nutrition.*
import io.github.ebziboi.delta.ui.DeltaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.json.JSONObject
import java.time.LocalDate

class FullTransferTest {
    @get:Rule val compose=createComposeRule()
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val context=inst.targetContext
    private val app get()=context.applicationContext as DeltaApp
    @Test fun invalidFullCopyLeavesNutritionAndMeasurementsUntouched()=runBlocking {
        val original=FullTransfer.create(context).toString()
        val before=FullTransfer.parse(original)
        val broken=JSONObject(original);broken.getJSONObject("nutrition").getJSONArray("entries").put(JSONObject().put("amount",-1))
        try {FullTransfer.apply(context,broken.toString());fail("Invalid backup must fail before changes")} catch(_:Exception) {}
        val after=FullTransfer.parse(FullTransfer.create(context).toString())
        assertEquals(before.nutrition,after.nutrition);assertEquals(before.measurements.weights,after.measurements.weights)
        assertFalse(JSONObject(original).getJSONObject("preferences").has("photo-ai"))
    }
    @Test fun localCopyRoundTripPreservesLibraryOrderAndUnknownLabels()=runBlocking {
        val original=FullTransfer.create(context).toString()
        try {
            val first=Food(id="library-first",name="First",per100=mapOf(Nutrient.ENERGY to 100.0))
            val second=Food(id="library-second",name="Second",per100=mapOf(Nutrient.PROTEIN to 20.0))
            app.nutrition.restore(NutritionState(foods=listOf(first,second),loaded=true))
            val expected=app.nutrition.state.value
            val copy=FullTransfer.create(context).toString()
            app.nutrition.restore(NutritionState(loaded=true));FullTransfer.apply(context,copy)
            assertEquals(expected,app.nutrition.state.value)
            assertEquals(listOf("library-first","library-second"),app.nutrition.state.value.foods.map {it.id})
            assertFalse(Nutrient.ENERGY in app.nutrition.state.value.foods.last().per100)
        } finally {FullTransfer.apply(context,original)}
    }
    /** Run against the beta with its matching base companion installed. */
    @Test fun copyToBaseRequiresConsentAndKeepsSourceUnchanged()=runBlocking {
        assertEquals(TransferSnapshot.BETA,context.packageName)
        val original=FullTransfer.create(context).toString();val store=SettingsStore(context)
        val device=UiDevice.getInstance(inst)
        try {
            val food=Food(id="return-fixture-food",name="Return test",per100=mapOf(Nutrient.ENERGY to 400.0,Nutrient.PROTEIN to 21.0),favourite=true)
            app.nutrition.restore(NutritionState(listOf(food),listOf(DiaryEntry("return-fixture-diary",LocalDate.now(),Meal.DINNER,food,125.0)),NutritionTargets(mapOf(Nutrient.ENERGY to 2200.0)),listOf(Fast("return-fixture-fast",100000L,16.0,200000L)),loaded=true))
            store.saveBetaMeasurements(listOf(WeightEntry(123456789L,93.4,true,"beta-local:return-test")),listOf(BodyFatEntry(123456789L,24.7,true,"beta-local:return-fat")))
            store.update {it.copy(onboardingDone=true,targetKg=80.0,motion=false)}
            context.getSharedPreferences("detail-card-order",0).edit().putString("nutrition","balance,intake,insights,consistency,comparison,sources,targets").commit()
            val fixture=FullTransfer.parse(FullTransfer.create(context).toString())
            compose.setContent {DeltaTheme(AppSettings()) {FullTransferCard()}}
            compose.onNodeWithText("Copy to Delta").performClick()
            assertTrue(device.wait(Until.hasObject(By.text("Replace data")),10000))
            assertTrue(device.hasObject(By.textContains("1 diary entries")))
            device.findObject(By.text("Cancel")).click()
            compose.waitUntil(10000) {compose.onAllNodesWithText("Copy cancelled. This app's data stays here.").fetchSemanticsNodes().isNotEmpty()}
            assertEquals(fixture.nutrition,app.nutrition.state.value)
            compose.onNodeWithText("Copy to Delta").performClick()
            assertTrue(device.wait(Until.hasObject(By.text("Replace data")),10000))
            device.findObject(By.text("Replace data")).click()
            compose.waitUntil(15000) {compose.onAllNodesWithText("Copied to Delta. This app's data stays here.").fetchSemanticsNodes().isNotEmpty()}
            val after=FullTransfer.parse(FullTransfer.create(context).toString())
            assertEquals(fixture.nutrition,after.nutrition);assertEquals(fixture.measurements.weights,after.measurements.weights)
        } finally {FullTransfer.apply(context,original)}
    }
}

/** Run only in the base-package test APK after the cross-app test above. */
class ReturnTransferVerificationTest {
    @Test fun receivedDiaryGoalsFastingAndLocalMeasurementsSurviveBaseRefresh()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(TransferSnapshot.STABLE,context.packageName)
        val app=context.applicationContext as DeltaApp;val store=SettingsStore(context)
        val nutrition=app.nutrition.state.first {it.loaded}
        assertEquals("return-fixture-diary",nutrition.entries.single().id)
        assertEquals(500.0,nutrition.entries.single().nutrients.getValue(Nutrient.ENERGY),.001)
        assertTrue(nutrition.foods.single().favourite);assertEquals("return-fixture-fast",nutrition.fasts.single().id)
        assertEquals(2200.0,nutrition.targets.target(Nutrient.ENERGY)!!,.001)
        assertEquals(80.0,store.settings.first().targetKg!!,.001)
        assertEquals("local-copy:beta-local:return-test",store.weights.first().single().id)
        app.repo.sync();assertEquals(93.4,store.weights.first().single().kg,.001)
        assertTrue(context.getSharedPreferences("detail-card-order",0).getString("nutrition","")!!.startsWith("balance,intake"))
        assertTrue(java.io.File(context.noBackupFilesDir,"before-full-transfer.json").exists())
    }
}
