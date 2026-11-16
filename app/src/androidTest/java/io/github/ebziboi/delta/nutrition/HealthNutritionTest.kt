package io.github.ebziboi.delta.nutrition

import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*

@RunWith(AndroidJUnit4::class)
class HealthNutritionTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun record(id:String="meal-a")=NutritionRecord(
        startTime=Instant.parse("2026-11-16T00:30:00Z"),startZoneOffset=ZoneOffset.ofHours(-2),
        endTime=Instant.parse("2026-11-16T00:45:00Z"),endZoneOffset=ZoneOffset.ofHours(-2),
        metadata=Metadata.manualEntryWithId(id),name="A recorded meal",mealType=MealType.MEAL_TYPE_DINNER,
        energy=Energy.kilocalories(512.0),protein=Mass.grams(22.0),sodium=Mass.milligrams(450.0),
        vitaminD=Mass.micrograms(12.5),sugar=Mass.grams(0.0))
    @Test fun consumedTotalsUnitsAndOriginalLocalDateArePreserved() {
        val e=record().toDiaryEntry()
        assertTrue(e.fromHealthConnect);assertEquals(Meal.DINNER,e.meal);assertEquals(LocalDate.of(2026,11,15),e.day)
        assertEquals(512.0,e.nutrients.getValue(Nutrient.ENERGY),.001)
        assertEquals(22.0,e.nutrients.getValue(Nutrient.PROTEIN),.001)
        assertEquals(450.0,e.nutrients.getValue(Nutrient.SODIUM),.001)
        assertEquals(12.5,e.nutrients.getValue(Nutrient.VIT_D),.001)
        assertEquals(0.0,e.nutrients.getValue(Nutrient.SUGAR),.001)
        assertFalse(Nutrient.FAT in e.nutrients)
    }
    @Test fun refreshDeduplicatesUpdatesRemovesAndRetainsLocalDiary()=runBlocking {
        val name="health-test-${newId()}.db";val repo=NutritionRepository(context,name)
        try {
            repo.state.first {it.loaded}
            val local=DiaryEntry(day=LocalDate.now(),meal=Meal.LUNCH,food=Food(name="Local",per100=mapOf(Nutrient.ENERGY to 100.0)),amount=50.0)
            repo.log(listOf(local));val imported=record().toDiaryEntry()
            repo.replaceHealthEntries(listOf(imported,imported));assertEquals(2,repo.state.value.entries.size)
            val changed=imported.copy(food=imported.food.copy(per100=mapOf(Nutrient.ENERGY to 600.0)))
            repo.replaceHealthEntries(listOf(changed));assertEquals(600.0,repo.state.value.entries.single {it.fromHealthConnect}.nutrients.getValue(Nutrient.ENERGY),.001)
            repo.replaceHealthEntries(emptyList());assertEquals(listOf(local),repo.state.value.entries)
            repo.replaceHealthEntries(listOf(imported))
            try {repo.copy(imported.day,imported.day.minusDays(1));fail("Cannot copy read-only imports as local weighed portions")}
            catch(e:IllegalArgumentException) {assertEquals("There are no entries to copy",e.message)}
            try {repo.updateEntry(imported.copy(amount=50.0));fail("Cannot edit imported food")}
            catch(_:IllegalArgumentException) {}
        } finally {repo.close();context.deleteDatabase(name)}
    }
    @Test fun singleLoggedDayDoesNotCreatePointsForMissingOrUnknownDays() {
        val today=LocalDate.now();val meal=record().toDiaryEntry().copy(day=today)
        val unknown=meal.copy(id=newId(),day=today.minusDays(2),food=Food(name="Unknown",per100=emptyMap()))
        val daily=intakePoints(listOf(meal,unknown),emptyList(),today,14,false,Nutrient.ENERGY)
        assertEquals(1,daily.count {it.hasValue});assertEquals(today,daily.single {it.hasValue}.start)
        assertEquals(today,daily.last().days.last());assertEquals(512.0,daily.last().value,.001)
        val week=intakePoints(listOf(meal,unknown),emptyList(),today,7,true,Nutrient.ENERGY).single()
        assertEquals(512.0,week.value,.001);assertEquals(1,week.reportedDays)
    }
    @Test fun limitedHistoryRefreshKeepsOlderCopiedFoodAndReplacesRecentRecords()=runBlocking {
        val name="health-window-${newId()}.db";val repo=NutritionRepository(context,name)
        try {
            repo.state.first {it.loaded}
            val recent=record().toDiaryEntry();val older=recent.copy(id="health:older",created=recent.created-90L*86400000,day=recent.day.minusDays(90))
            repo.replaceHealthEntries(listOf(older,recent))
            repo.replaceHealthEntries(emptyList(),recent.created-1)
            assertEquals(listOf(older),repo.state.value.entries)
            repo.replaceHealthEntries(emptyList());assertTrue(repo.state.value.entries.isEmpty())
        } finally {repo.close();context.deleteDatabase(name)}
    }
    @Test fun aSingleMeasurementStillHasOneSelectableHitAndTodayOnTheAxis() {
        val point=io.github.ebziboi.delta.data.TrendPoint(Instant.parse("2026-11-15T10:00:00Z").toEpochMilli(),90.0,90.0)
        val end=Instant.parse("2026-11-16T23:59:59Z").toEpochMilli()
        val bitmap=android.graphics.Bitmap.createBitmap(700,400,android.graphics.Bitmap.Config.ARGB_8888)
        try {
            val spec=io.github.ebziboi.delta.render.ChartSpec(listOf(point),io.github.ebziboi.delta.data.ChartStyle.CONNECTED,
                io.github.ebziboi.delta.data.WeightUnit.KG,null,selectedPoint=point,endEpochMs=end)
            val frame=io.github.ebziboi.delta.render.TrendChart.draw(android.graphics.Canvas(bitmap),android.graphics.RectF(0f,0f,700f,400f),spec,
                io.github.ebziboi.delta.render.Palettes.resolve(context,io.github.ebziboi.delta.data.ThemeStyle.AURORA,io.github.ebziboi.delta.data.Accent.SKY),
                io.github.ebziboi.delta.render.DeltaFonts.canvas(context,io.github.ebziboi.delta.data.TypeStyle.EDITORIAL),1f,true)
            assertNotNull(frame);assertEquals(1,frame!!.hits.size);assertEquals(point,frame.hits.single().point);assertEquals(end,frame.full.last)
        } finally {bitmap.recycle()}
    }
    @Test fun previousVersionNutritionDatabaseMigratesWithoutLosingMeals()=runBlocking {
        val name="health-migration-${newId()}.db"
        val old=context.openOrCreateDatabase(name,0,null)
        old.execSQL("CREATE TABLE foods(id TEXT PRIMARY KEY,payload TEXT NOT NULL)")
        old.execSQL("CREATE TABLE diary(id TEXT PRIMARY KEY,day INTEGER,meal TEXT,food TEXT,amount REAL,created INTEGER)")
        old.execSQL("CREATE TABLE preferences(id TEXT PRIMARY KEY,payload TEXT NOT NULL)")
        old.execSQL("CREATE TABLE fasts(id TEXT PRIMARY KEY,start INTEGER,hours REAL,end INTEGER)")
        val food=Food(name="Saved before update",per100=mapOf(Nutrient.ENERGY to 123.0))
        old.execSQL("INSERT INTO diary VALUES(?,?,?,?,?,?)",arrayOf<Any>("saved-local",LocalDate.now().toEpochDay(),Meal.LUNCH.name,food.json().toString(),50.0,123L))
        old.version=1;old.close()
        val repo=NutritionRepository(context,name)
        try {assertNull(repo.state.first {it.loaded}.loadError);repo.replaceHealthEntries(listOf(record().toDiaryEntry()));assertEquals(2,repo.state.value.entries.size);assertEquals(61.5,repo.state.value.entries.single {it.id=="saved-local"}.nutrients.getValue(Nutrient.ENERGY),.001)}
        finally {repo.close();context.deleteDatabase(name)}
    }
}
