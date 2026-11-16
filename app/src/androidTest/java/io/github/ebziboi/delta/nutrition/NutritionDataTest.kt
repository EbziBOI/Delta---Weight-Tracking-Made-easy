package io.github.ebziboi.delta.nutrition

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BarcodeFormat
import com.google.zxing.oned.EAN13Writer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class NutritionDataTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val oats=Food(name="Oats",per100=mapOf(Nutrient.ENERGY to 380.0,Nutrient.PROTEIN to 13.0,Nutrient.CARBS to 60.0,Nutrient.FAT to 7.0,Nutrient.IRON to 4.25),serving=40.0)
    @Test fun scalingRecipesAndMissingLabels() {
        val e=DiaryEntry(day=LocalDate.now(),meal=Meal.BREAKFAST,food=oats,amount=50.0)
        assertEquals(190.0,e.nutrients[Nutrient.ENERGY]!!,.00001)
        assertEquals(2.125,e.nutrients[Nutrient.IRON]!!,.00001)
        val unknown=Food(name="Unlabelled",per100=emptyMap())
        val total=listOf(e,e.copy(id=newId(),food=unknown)).total(Nutrient.ENERGY)
        assertTrue(total.partial);assertEquals(1,total.reported);assertEquals(2,total.count)
        assertFalse(listOf(e.copy(food=unknown)).total(Nutrient.ENERGY).known)
        val recipe=makeRecipe("Porridge",listOf(Ingredient(oats,80.0)),2.0,240.0)
        assertEquals(120.0,recipe.serving!!,.001)
        assertEquals(304.0/2.4,recipe.per100.getValue(Nutrient.ENERGY),.001)
    }
    @Test fun recipeYieldAndStrictCompleteness() {
        val recipe=makeRecipe("Porridge",listOf(Ingredient(oats,80.0)),2.0,240.0)
        assertEquals(304.0,recipe.per100.getValue(Nutrient.ENERGY)*2.4,.001)
        assertEquals(152.0,Ingredient(recipe,recipe.serving!!).nutrients.getValue(Nutrient.ENERGY),.001)
        val partial=makeRecipe("Mixed",listOf(Ingredient(oats,80.0),Ingredient(Food(name="Mystery",per100=mapOf(Nutrient.ENERGY to 100.0)),20.0)),2.0,100.0)
        assertFalse(Nutrient.PROTEIN in partial.per100);assertEquals(324.0,partial.per100.getValue(Nutrient.ENERGY),.001)
        expectFailure { makeRecipe("Invalid",emptyList(),2.0,100.0) }
        expectFailure { oats.per100.scaled(Double.NaN) }
        expectFailure { oats.copy(serving=0.0) }
    }
    @Test fun nestedRecipeSnapshotsStayBoundedAndKeepEstimateProvenance() {
        val photo=Food(name="Photo ingredient",per100=mapOf(Nutrient.ENERGY to 150.0),source=FoodSource.PHOTO)
        var recipe=makeRecipe("Base",listOf(Ingredient(photo,100.0)),1.0,100.0)
        repeat(12) { recipe=foodFromJson(makeRecipe("Batch $it",List(20) {Ingredient(recipe,10.0)},2.0,200.0).json()) }
        val encoded=recipe.json().toString()
        assertTrue("Ingredient snapshots must not grow recursively",encoded.length<50000)
        val restored=foodFromJson(JSONObject(encoded))
        assertEquals(150.0,restored.per100.getValue(Nutrient.ENERGY),.001)
        assertTrue(restored.estimated);assertEquals(20,restored.ingredients.size)
        assertTrue(restored.ingredients.all {it.food.ingredients.isEmpty() && it.food.estimated})
    }
    @Test fun macroTargetsRespectEnergyAndUnits() {
        val t=NutritionTargets(mapOf(Nutrient.ENERGY to 2000.0),MacroMode.PERCENT).validated()
        assertEquals(125.0,t.target(Nutrient.PROTEIN)!!,.001);assertEquals(225.0,t.target(Nutrient.CARBS)!!,.001)
        assertEquals(600.0/9,t.target(Nutrient.FAT)!!,.001)
        expectFailure {t.copy(percentages=mapOf(Nutrient.PROTEIN to 50.0,Nutrient.CARBS to 50.0,Nutrient.FAT to 50.0)).validated()}
        expectFailure {t.copy(amounts=emptyMap()).validated()}
        expectFailure {NutritionTargets(mapOf(Nutrient.IRON to Double.NaN)).validated()}
    }
    @Test fun openFoodFactsUsesCanonicalAmountsAndDoesNotMixServingData() {
        val food=OpenFoodFacts.parse(JSONObject("""{"code":"3017620422003","product_name":"Label fixture","quantity":"1 l","serving_size":"250 ml","nutriments":{"energy_100g":418.4,"proteins_100g":3.2,"sodium_100g":0.12,"vitamin-d_100g":0.0000025,"iron_serving":10}}"""))!!
        assertEquals(FoodUnit.MILLILITRES,food.unit);assertEquals(250.0,food.serving!!,.001);assertEquals(1000.0,food.pack!!,.001)
        assertEquals(100.0,food.per100.getValue(Nutrient.ENERGY),.001);assertEquals(120.0,food.per100.getValue(Nutrient.SODIUM),.001)
        assertEquals(2.5,food.per100.getValue(Nutrient.VIT_D),.001);assertFalse(Nutrient.IRON in food.per100)
        val unspecified=OpenFoodFacts.parse(JSONObject("""{"code":"3017620422003","product_name":"No portion mass","serving_size":"1 piece","nutriments":{"fat_100g":0}}"""))!!
        val drink=OpenFoodFacts.parse(JSONObject("""{"code":"3017620422003","product_name":"Drink","serving_size":"250 ml","nutriments":{"energy-kcal_100g":42,"alcohol_100g":5,"alcohol_unit":"% vol","vitamin-b9_100g":0.00002}}"""))!!
        assertEquals(FoodUnit.MILLILITRES,drink.unit);assertEquals(250.0,drink.serving!!,.001)
        assertFalse(Nutrient.ALCOHOL in drink.per100);assertEquals(20.0,drink.per100.getValue(Nutrient.FOLATE),.001)
        val multipack=OpenFoodFacts.parse(JSONObject("""{"code":"3017620422003","product_name":"Multipack","quantity":"6 x 180 g","nutriments":{}}"""))!!
        assertEquals(1080.0,multipack.pack!!,.001)
        assertNull(unspecified.serving);assertEquals(0.0,unspecified.per100.getValue(Nutrient.FAT),.001);assertFalse(Nutrient.ENERGY in unspecified.per100)
    }
    @Test fun databaseCopiesAreAtomicAndSnapshotsSurviveFoodEditsAndReopening() = runBlocking {
        val name="nutrition-test-${newId()}.db";val day=LocalDate.of(2026,3,28)
        var repo=NutritionRepository(context,name)
        try {
            withTimeout(5000) {repo.state.first {it.loaded}}
            val e=DiaryEntry(day=day,meal=Meal.DINNER,food=oats,amount=75.0)
            repo.log(listOf(e));repo.saveFood(oats.copy(name="Edited",per100=mapOf(Nutrient.ENERGY to 999.0)))
            assertEquals("Oats",repo.state.value.entries.single().food.name)
            assertEquals(285.0,repo.state.value.entries.single().nutrients.getValue(Nutrient.ENERGY),.001)
            repo.copy(day,day.plusDays(1),Meal.DINNER)
            repo.log(listOf(e.copy(id=newId(),day=day.plusDays(2))))
            assertEquals("Edited",repo.state.value.foods.single().name)
            repo.deleteEntry(repo.state.value.entries.last().id)
            assertEquals(2,repo.state.value.entries.size);assertEquals(2,repo.state.value.entries.map {it.id}.distinct().size)
            expectSuspendFailure {repo.copy(day,day)};assertEquals(2,repo.state.value.entries.size)
            val fastStart=1774738800000L;repo.startFast(16.0,fastStart)
            expectSuspendFailure {repo.startFast(14.0,fastStart)}
            repo.saveTargets(NutritionTargets(mapOf(Nutrient.ENERGY to 2000.0),MacroMode.PERCENT))
            repo.close();repo=NutritionRepository(context,name);withTimeout(5000) {repo.state.first {it.loaded}}
            assertNull(repo.state.value.loadError);assertEquals(2,repo.state.value.entries.size)
            assertEquals(17*3600000L,repo.state.value.fasts.single().elapsed(fastStart+17*3600000L))
            assertTrue(repo.state.value.fasts.single().reached(fastStart+17*3600000L))
            repo.finishFast(fastStart+17*3600000L);assertEquals(fastStart+17*3600000L,repo.state.value.fasts.single().end)
            assertEquals(125.0,repo.state.value.targets.target(Nutrient.PROTEIN)!!,.001)
            repo.updateEntry(repo.state.value.entries.first().copy(amount=100.0));assertEquals(380.0,repo.state.value.entries.first().nutrients.getValue(Nutrient.ENERGY),.001)
        } finally {repo.close();context.deleteDatabase(name)}
    }
    @Test fun chartAveragesDoNotFillUnloggedDaysWithZero() {
        val day=LocalDate.now();val entries=listOf(DiaryEntry(day=day,meal=Meal.LUNCH,food=oats,amount=100.0),DiaryEntry(day=day.minusDays(1),meal=Meal.LUNCH,food=oats,amount=200.0))
        val points=intakePoints(entries,emptyList(),day,7,true,Nutrient.ENERGY)
        assertEquals(2,points.single().loggedDays);assertEquals(570.0,points.single().value,.001)
        assertFalse(intakePoints(entries,emptyList(),day.minusDays(2),7,true,Nutrient.ENERGY).single().hasValue)
        val zone=java.time.ZoneId.systemDefault()
        fun weight(d:LocalDate,hour:Int,kg:Double)=io.github.ebziboi.delta.data.WeightEntry(d.atTime(hour,0).atZone(zone).toInstant().toEpochMilli(),kg,false,newId())
        val weights=listOf(weight(day,8,80.0),weight(day,20,82.0),weight(day.minusDays(1),8,84.0),weight(day.plusDays(1),8,200.0))
        // Average within a day first, so frequent weigh-ins do not overweight a date.
        assertEquals(82.5,intakePoints(entries,weights,day,7,true,Nutrient.ENERGY).single().weight!!,.001)
        val daily=intakePoints(entries,weights,day,7,false,Nutrient.ENERGY)
        assertEquals(81.0,daily.last().weight!!,.001);assertNull(daily.first().weight)
    }
    @Test fun incompleteLabelsCannotClaimThatANutrientLimitWasMet() {
        val day=LocalDate.now()
        val known=Food(name="Label",per100=mapOf(Nutrient.SODIUM to 100.0))
        val unknown=Food(name="No sodium label",per100=emptyMap())
        val entries=listOf(DiaryEntry(day=day.minusDays(1),meal=Meal.LUNCH,food=known,amount=100.0),
            DiaryEntry(day=day.minusDays(1),meal=Meal.LUNCH,food=unknown,amount=100.0),
            DiaryEntry(day=day,meal=Meal.LUNCH,food=known,amount=300.0))
        val points=intakePoints(entries,emptyList(),day,7,false,Nutrient.SODIUM)
        val limit=points.targetComparison(250.0,true)
        assertEquals(0,limit.satisfying);assertEquals(1,limit.eligible);assertEquals(1,limit.incomplete)
        val goal=points.targetComparison(250.0,false)
        assertEquals(1,goal.satisfying);assertEquals(1,goal.eligible)
    }
    @Test fun photoContractsValidationAndEncryptedCredentials() {
        val sample="""{"foods":[{"name":"Rice","grams":150,"per100":{"ENERGY":130,"PROTEIN":2.7,"CARBS":28,"FAT":0.3}}]}"""
        val food=PhotoNutrition.parse(sample).single();assertEquals(195.0,food.nutrients.getValue(Nutrient.ENERGY),.001);assertTrue(food.food.estimated)
        expectFailure {PhotoNutrition.parse(sample.replace("150","-1"))}
        expectFailure {PhotoNutrition.parse(sample.replace("130","\"NaN\""))}
        expectFailure {PhotoNutrition.parse("{\"foods\":[]}")}
        for(p in PhotoProvider.entries) {
            val request=PhotoNutrition.request(p,PhotoCredential("test-secret-value",p.defaultModel),"FAKEBASE64")
            assertTrue(request.url.startsWith("https://"));assertFalse(request.url.contains("test-secret"));assertFalse(request.body.toString().contains("test-secret"))
            assertEquals("PhotoRequest(redacted)",request.toString());assertTrue(request.body.toString().contains("FAKEBASE64"))
        }
        val namespace="photo_test_${newId()}";val vault=PhotoKeyVault(context,namespace)
        try {
            vault.save(PhotoProvider.OPENAI,"test-secret-value","gpt-4.1-mini")
            val file=File(context.noBackupFilesDir,"$namespace/OPENAI")
            assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("test-secret-value"))
            assertEquals("test-secret-value",PhotoKeyVault(context,namespace).load(PhotoProvider.OPENAI)!!.key)
            vault.remove(PhotoProvider.OPENAI);assertNull(vault.load(PhotoProvider.OPENAI))
        } finally {File(context.noBackupFilesDir,namespace).deleteRecursively();java.security.KeyStore.getInstance("AndroidKeyStore").apply {load(null);deleteEntry("delta.$namespace.v1")}}
    }
    @Test fun providerEnvelopesAndTruncatedResultsAreHandled() {
        val text="""{"foods":[{"name":"Fixture","grams":100,"per100":{"ENERGY":100}}]}"""
        val openai=JSONObject().put("status","completed").put("output",org.json.JSONArray().put(JSONObject().put("type","message").put("content",org.json.JSONArray().put(JSONObject().put("type","output_text").put("text",text)))))
        val claude=JSONObject().put("stop_reason","end_turn").put("content",org.json.JSONArray().put(JSONObject().put("type","text").put("text",text)))
        val gemini=JSONObject().put("candidates",org.json.JSONArray().put(JSONObject().put("finishReason","STOP").put("content",JSONObject().put("parts",org.json.JSONArray().put(JSONObject().put("text",text))))))
        for((p,j) in listOf(PhotoProvider.OPENAI to openai,PhotoProvider.CLAUDE to claude,PhotoProvider.GEMINI to gemini)) {
            assertEquals("Fixture",PhotoNutrition.parse(PhotoNutrition.extract(p,j)).single().food.name)
        }
        expectFailure {PhotoNutrition.extract(PhotoProvider.OPENAI,openai.put("status","incomplete"))}
        expectFailure {PhotoNutrition.extract(PhotoProvider.CLAUDE,claude.put("stop_reason","max_tokens"))}
        expectFailure {PhotoNutrition.extract(PhotoProvider.GEMINI,gemini.apply {getJSONArray("candidates").getJSONObject(0).put("finishReason","MAX_TOKENS")})}
    }
    @Test fun bundledScannerRecognisesAnEanWithoutNetwork() {
        val code="3017620422003";val matrix=EAN13Writer().encode(code,BarcodeFormat.EAN_13,720,360)
        val pixels=ByteArray(matrix.width*matrix.height)
        for(y in 0 until matrix.height) for(x in 0 until matrix.width) pixels[y*matrix.width+x]=if(matrix[x,y]) 0 else 255.toByte()
        assertEquals(code,decodeBarcode(pixels,matrix.width,matrix.height))
        val rotated=ByteArray(pixels.size)
        for(y in 0 until matrix.height) for(x in 0 until matrix.width) rotated[x*matrix.height+matrix.height-y-1]=pixels[y*matrix.width+x]
        assertEquals(code,decodeBarcode(rotated,matrix.height,matrix.width))
    }
    private fun expectFailure(action:()->Unit) {try {action();fail("Expected validation failure")} catch(_:IllegalArgumentException){} }
    private suspend fun expectSuspendFailure(action:suspend ()->Unit) {try {action();fail("Expected validation failure")} catch(_:IllegalArgumentException){} }
}
