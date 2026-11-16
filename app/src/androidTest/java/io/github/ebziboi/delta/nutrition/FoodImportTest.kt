package io.github.ebziboi.delta.nutrition

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FoodImportTest {
    private val uk="""Nutrition information
        |Typical values\tper 100g\tper portion (25g)
        |Energy\t2468 kJ / 590 kcal\t617 kJ / 147.5 kcal
        |Fat\t62.9 g\t15.725 g
        |of which saturates\t4.4 g\t1.1 g
        |Carbohydrate\t3.7 g\t0.925 g
        |of which sugars\t2.7 g\t0.675 g
        |Fibre\t0.5 g\t0.125 g
        |Protein\t2.0 g\t0.5 g
        |Salt\t1.13 g\t0.2825 g""".trimMargin().replace("\\t","\t")
    @Test fun ukColumnsPreserveActualValuesAndNormalisePortions() {
        val read=LabelParser.parse(uk);assertEquals(2,read.columns.size)
        assertEquals(100.0,read.columns[0].basis.amount!!,.001);assertEquals(25.0,read.columns[1].basis.amount!!,.001)
        for(c in read.columns) {assertEquals(590.0,c.per100().getValue(Nutrient.ENERGY),.01);assertEquals(1.13,c.per100().getValue(Nutrient.SALT),.0001)}
        assertEquals(read,LabelRead.fromJson(read.json()))
    }
    @Test fun usServingAndPercentagesDoNotBecomePerHundredOrFakeMicronutrients() {
        val read=LabelParser.parse("Nutrition Facts\nServing size 2 tbsp (30 g)\nCalories 90\nTotal Fat 3g 4%\nTotal Carbohydrate 12g 4%\nProtein 2g\nSodium 180mg 8%\nIron 8%\nVitamin D 0mcg 0%")
        val c=read.columns[0];assertEquals(30.0,c.basis.amount!!,.01)
        assertEquals(300.0,c.per100().getValue(Nutrient.ENERGY),.01);assertEquals(600.0,c.per100().getValue(Nutrient.SODIUM),.01)
        assertFalse(c.per100().containsKey(Nutrient.IRON));assertEquals(0.0,c.per100().getValue(Nutrient.VIT_D),.01)
    }
    @Test fun commasBoundsAndUnitsAreHandledWithoutInventingValues() {
        val c=LabelParser.parse("Per 100 ml\nEnergy 418,4 kJ\nFat <0,5 g\nSugars 0 g\nSodium 0,12 g\nVitamin B12 0,002 mg").columns[0]
        assertEquals(FoodUnit.MILLILITRES,c.basis.unit);assertEquals(100.0,c.per100().getValue(Nutrient.ENERGY),.01)
        assertFalse(c.per100().containsKey(Nutrient.FAT));assertTrue(c.observations.getValue(Nutrient.FAT).bounded)
        assertEquals(120.0,c.per100().getValue(Nutrient.SODIUM),.01);assertEquals(2.0,c.per100().getValue(Nutrient.B12),.01)
        assertFalse(c.per100().containsKey(Nutrient.PROTEIN))
        assertTrue(LabelParser.parse("Fat 5 g\nProtein 3 g").columns[0].per100().isEmpty())
    }
    @Test fun splitEnergyRowsAndThousandsAreReadWithoutPartialOcrNumbers() {
        val split=LabelParser.parse("Per 100g\nEnergy 418kJ\n100kcal\nFat 2g").columns[0]
        assertEquals(100.0,split.per100().getValue(Nutrient.ENERGY),.01)
        val columns=LabelParser.parse("Typical values\tPer 100g\tPer 25g\nEnergy\t418kJ\t104.5kJ\n100kcal\t25kcal\nFat\t8g\t2g")
        assertEquals(100.0,columns.columns[0].per100().getValue(Nutrient.ENERGY),.01)
        assertEquals(100.0,columns.columns[1].per100().getValue(Nutrient.ENERGY),.01)
        assertTrue(LabelParser.differs(.002,.02))
        val us=LabelParser.parse("Nutrition Facts\nServing size 100g\nSodium 1,000mg\nFat 24O0g").columns[0]
        assertEquals(1000.0,us.per100().getValue(Nutrient.SODIUM),.01)
        assertFalse(us.per100().containsKey(Nutrient.FAT))
        val duplicate=LabelParser.parse("Per 100g\nProtein 3g\nProtein 5g\nProtein 3g").columns[0]
        assertFalse(duplicate.per100().containsKey(Nutrient.PROTEIN))
    }
    @Test fun physicalRowsSurviveSeparateVerticalOcrBlocks() {
        val text=LabelOcr.lines(listOf(LabelWord("Fat",0f,50f,40f,70f),LabelWord("5g",200f,51f,230f,71f),LabelWord("Protein",0f,100f,60f,120f),LabelWord("2g",200f,101f,230f,121f)))
        val c=LabelParser.parse("Per 100g\n$text").columns[0]
        assertEquals(5.0,c.per100().getValue(Nutrient.FAT),.01);assertEquals(2.0,c.per100().getValue(Nutrient.PROTEIN),.01)
    }
    private fun page(barcode:String="5060048649352")=JSONObject().put("url","https://groceries.morrisons.com/products/dip/123").put("heading","Oasis Garlic & Herb Dip")
        .put("structured",JSONArray().put(JSONObject().put("@type","Product").put("name","Oasis Garlic & Herb Dip").put("brand",JSONObject().put("name","Oasis")).put("gtin13",barcode).toString()))
        .put("tables",JSONArray().put(JSONArray().also {t->uk.lines().drop(1).forEach {line->t.put(JSONArray(line.split('\t')))}})).put("text",uk)
    @Test fun productPagesVerifyIdentifiersAndKeepSourceAndLabelEvidence() {
        val p=ProductPages.parse(page().toString(),"5060048649352");assertTrue(p.barcodeMatch);assertFalse(p.barcodeConflict)
        assertEquals(590.0,p.read.columns[p.read.best].per100().getValue(Nutrient.ENERGY),.01)
        val f=Food(name=p.name,brand=p.brand,per100=p.read.columns[p.read.best].per100(),barcode=p.barcode,sourceUrl=p.url,labelChecked=setOf(Nutrient.ENERGY))
        assertEquals(f,foodFromJson(f.json()));assertFalse(f.estimated)
        val related=page().put("structured",JSONArray()
            .put(JSONObject().put("@type","Product").put("name","Oasis Garlic & Herb Dip").put("gtin13","5060048649567").toString())
            .put(JSONObject().put("@type","Product").put("name","Another sauce").put("gtin13","5060048649352").toString()))
        assertFalse(ProductPages.parse(related.toString(),"5060048649352").barcodeMatch)
        val different=ProductPages.parse(page().toString(),"5060048649567");assertFalse(different.barcodeMatch);assertTrue(different.barcodeConflict)
        val absent=page();absent.put("structured",JSONArray());assertFalse(ProductPages.parse(absent.toString(),"5060048649352").barcodeMatch)
        assertEquals(ProductPages.canonicalBarcode("123456789012"),ProductPages.canonicalBarcode("0123456789012"))
    }
    @Test fun unsafePagesAndGoogleSummariesCannotBeImportedAsProductEvidence() {
        listOf("http://example.com/a","https://127.0.0.1/a","https://localhost/a","https://user:pass@example.com/a","file:///tmp/a").forEach {assertFalse(it,ProductPages.publicUrl(it))}
        val google=page().put("url","https://www.google.com/search?q=dip")
        assertTrue(runCatching {ProductPages.parse(google.toString())}.isFailure)
        assertTrue(LabelParser.warnings(mapOf(Nutrient.FAT to 101.0,Nutrient.SUGAR to 9.0,Nutrient.CARBS to 2.0)).size>=2)
    }
    @Test fun barcodePhotographIsDecodedLocally()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val matrix=com.google.zxing.MultiFormatWriter().encode("5060048649352",com.google.zxing.BarcodeFormat.EAN_13,1100,400)
        val bitmap=Bitmap.createBitmap(matrix.width,matrix.height,Bitmap.Config.ARGB_8888)
        bitmap.setPixels(IntArray(matrix.width*matrix.height) {i->if(matrix[i%matrix.width,i/matrix.width]) Color.BLACK else Color.WHITE},0,matrix.width,0,0,matrix.width,matrix.height)
        val file=File(context.cacheDir,"barcode-photo-fixture.png");file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        try {assertEquals("5060048649352",BarcodePhotos.decode(context,Uri.fromFile(file),LabelCrop()))} finally {file.delete()}
        Unit
    }
    @Test fun bundledOcrReadsARealBitmapOfflineWithoutCloudCredentials()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val image=Bitmap.createBitmap(1800,1900,Bitmap.Config.ARGB_8888);val canvas=Canvas(image);canvas.drawColor(Color.WHITE)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.BLACK;textSize=64f;typeface=Typeface.create("sans-serif",Typeface.NORMAL)}
        listOf("Nutrition information","Per 100 g","Energy 200 kcal","Fat 8 g","Saturates 2 g","Carbohydrate 20 g","Sugars 5 g","Fibre 3 g","Protein 10 g","Salt 0.5 g").forEachIndexed {i,s->canvas.drawText(s,100f,160f+i*145,paint)}
        val file=File(context.cacheDir,"label-ocr-fixture.png");file.outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
        try {
            val result=LabelOcr.scan(context,Uri.fromFile(file),LabelCrop())
            val values=result.read.columns[result.read.best].per100()
            assertEquals(result.read.raw,200.0,values.getValue(Nutrient.ENERGY),.01)
            assertEquals(8.0,values.getValue(Nutrient.FAT),.01);assertEquals(.5,values.getValue(Nutrient.SALT),.01)
        } finally {file.delete()}
        Unit
    }
}
