package io.github.ebziboi.delta.nutrition

import org.junit.Assert.*
import org.junit.Test

/** Label transcription cases drawn from real-world OCR output: languages, typos, misread units and column layout. */
class LabelReadingTest {
    private fun per100(text:String,column:Int=0)=LabelParser.parse(text).columns[column].per100()

    @Test fun englishTableStillReads() {
        val v=per100("Typical values\tPer 100g\tPer 30g\nEnergy\t1046kJ/250kcal\t314kJ/75kcal\nFat\t10g\t3g\nof which saturates\t4g\t1.2g\nCarbohydrate\t30g\t9g\nof which sugars\t12g\t3.6g\nProtein\t10g\t3g\nSalt\t1.0g\t0.3g")
        assertEquals(250.0,v.getValue(Nutrient.ENERGY),.5);assertEquals(10.0,v.getValue(Nutrient.FAT),.01);assertEquals(4.0,v.getValue(Nutrient.SATURATED),.01)
        assertEquals(1.0,v.getValue(Nutrient.SALT),.01)
    }
    @Test fun frenchAndGermanLabelsAreRecognised() {
        val fr=per100("Valeurs nutritionnelles pour 100 g\nÉnergie 1046 kJ / 250 kcal\nMatières grasses 10 g\ndont acides gras saturés 4 g\nGlucides 30 g\ndont sucres 12 g\nFibres alimentaires 2 g\nProtéines 10 g\nSel 1,0 g")
        assertEquals(250.0,fr.getValue(Nutrient.ENERGY),.5);assertEquals(10.0,fr.getValue(Nutrient.FAT),.01);assertEquals(4.0,fr.getValue(Nutrient.SATURATED),.01)
        assertEquals(30.0,fr.getValue(Nutrient.CARBS),.01);assertEquals(12.0,fr.getValue(Nutrient.SUGAR),.01);assertEquals(2.0,fr.getValue(Nutrient.FIBRE),.01)
        assertEquals(10.0,fr.getValue(Nutrient.PROTEIN),.01);assertEquals(1.0,fr.getValue(Nutrient.SALT),.01)
        val de=per100("Nährwerte pro 100 g\nBrennwert 1046 kJ / 250 kcal\nFett 10 g\ndavon gesättigte Fettsäuren 4 g\nKohlenhydrate 30 g\ndavon Zucker 12 g\nEiweiß 10 g\nSalz 1 g")
        assertEquals(250.0,de.getValue(Nutrient.ENERGY),.5);assertEquals(4.0,de.getValue(Nutrient.SATURATED),.01);assertEquals(12.0,de.getValue(Nutrient.SUGAR),.01)
        assertEquals(10.0,de.getValue(Nutrient.PROTEIN),.01)
    }
    @Test fun smallOcrMisspellingsStillMatchTheirRow() {
        val v=per100("Per 100g\nEnergy 250kcal\nFat 10g\nCarbohydrale 30g\nProtien 10g\nof which sugrs 12g")
        assertEquals(30.0,v.getValue(Nutrient.CARBS),.01);assertEquals(10.0,v.getValue(Nutrient.PROTEIN),.01);assertEquals(12.0,v.getValue(Nutrient.SUGAR),.01)
    }
    @Test fun aGramUnitReadAsNineIsCorrectedOnlyWhenTheMathsDemandsIt() {
        val read=LabelParser.parse("Per 100g\nEnergy 215 kcal\nFat 39\nCarbohydrate 37g\nProtein 10g")
        assertEquals(3.0,read.columns[0].per100().getValue(Nutrient.FAT),.01)
        assertTrue(read.warnings.any {it.contains("misread")})
        // A genuine 39 g of fat that adds up is left alone.
        val real=per100("Per 100g\nEnergy 523 kcal\nFat 39\nCarbohydrate 37g\nProtein 6g")
        assertEquals(39.0,real.getValue(Nutrient.FAT),.01)
    }
    @Test fun separatedUnitNinesBecomeGrams() {
        val v=per100("Per 100 g\nEnergy 250 kcal\nFat 10 9\nCarbohydrate 30 g\nProtein 10 g")
        assertEquals(10.0,v.getValue(Nutrient.FAT),.01)
        assertEquals(1,LabelParser.parse("Per 100 g\nEnergy 250 kcal\nFat 10 9\nCarbohydrate 30 g\nProtein 10 g").columns.size)
    }
    @Test fun unlabelledEnergyPairsUseTheKcalFigure() {
        val read=LabelParser.parse("Per 100g\tPer 30g\nEnergy kJ/kcal 1046/250 314/75\nFat 10g 3g")
        assertEquals(250.0,read.columns[0].per100().getValue(Nutrient.ENERGY),.5)
        assertEquals(250.0,read.columns[1].per100().getValue(Nutrient.ENERGY),.5)
        assertEquals(250.0,per100("Per 100g\nEnergy 1046 250\nFat 10g").getValue(Nutrient.ENERGY),.5)
    }
    @Test fun saltAndSodiumFillEachOther() {
        assertEquals(400.0,per100("Per 100g\nSalt 1.0g").withSaltAndSodium().getValue(Nutrient.SODIUM),.01)
        assertEquals(1.0,per100("Per 100g\nSodium 400mg").withSaltAndSodium().getValue(Nutrient.SALT),.001)
        assertFalse(per100("Per 100g\nSalt 1.0g").containsKey(Nutrient.SODIUM))
    }
    @Test fun ocrColumnsKeepTheirPlaceWhenACellIsEmpty() {
        fun w(t:String,x:Float,y:Float)=LabelWord(t,x,y,x+12f*t.length,y+20f)
        val words=listOf(w("Per",200f,0f),w("100g",240f,0f),w("Per",400f,0f),w("30g",440f,0f),
            w("Fat",0f,40f),w("10g",230f,40f),w("3g",430f,40f),
            w("Fibre",0f,80f),w("0.8g",425f,80f),
            w("Protein",0f,120f),w("10g",230f,120f),w("3g",430f,120f))
        val read=LabelParser.parse(LabelOcr.lines(words))
        assertEquals(2,read.columns.size)
        assertFalse(read.columns[0].observations.containsKey(Nutrient.FIBRE))
        assertEquals(0.8,read.columns[1].observations.getValue(Nutrient.FIBRE).value,.001)
        assertEquals(10.0,read.columns[0].per100().getValue(Nutrient.FAT),.01)
    }
    @Test fun percentColumnsAreDropped() {
        val read=LabelParser.parse("Per 100g\nFat\t10g\t14%\nProtein\t10g\t20%")
        assertEquals(1,read.columns.size)
        assertTrue(read.warnings.none {it.contains("incomplete")})
    }
    @Test fun consistencyScoreFlagsImpossibleColumns() {
        assertEquals(0.0,LabelParser.violation(mapOf(Nutrient.ENERGY to 250.0,Nutrient.FAT to 10.0,Nutrient.CARBS to 30.0,Nutrient.PROTEIN to 10.0),100.0),.0)
        assertTrue(LabelParser.violation(mapOf(Nutrient.SUGAR to 40.0,Nutrient.CARBS to 30.0),100.0)>0)
    }
    @Test fun searchUnderstandsPortionsAndTypos() {
        assertEquals(QueryPortion("chicken breast",amount=200.0),parsePortion("200g chicken breast"))
        assertEquals(QueryPortion("rice",amount=150.0),parsePortion("rice 150 g"))
        assertEquals(QueryPortion("eggs",count=2.0),parsePortion("2 eggs"))
        assertEquals(QueryPortion("avocado",count=.5),parsePortion("half avocado"))
        assertEquals("7up",parsePortion("7up").text)
        assertEquals("5000112637922",parsePortion("5000112637922").text)
        assertTrue(typoMatch("chiken","chicken"));assertTrue(typoMatch("bannana","banana"));assertFalse(typoMatch("beef","pork"))
    }
}

class CommonFoodsTest {
    @Test fun everydayFoodsAreFoundByHowPeopleSayThem() {
        assertEquals("Pasta, cooked",CommonFoods.search("bowl of pasta").first().name)
        assertEquals("Fried egg",CommonFoods.search("fried egg").first().name)
        assertEquals("Fried egg",CommonFoods.search("fried eggs").first().name)
        assertTrue(CommonFoods.search("chiken breast").any {it.name.startsWith("Chicken breast")})
        assertEquals("Spaghetti bolognese",CommonFoods.search("spag bol").first().name)
        assertTrue(CommonFoods.search("eggs").size>=4)
    }
    @Test fun everyRowIsSaneAndPortioned() {
        val all=CommonFoods.items
        assertTrue(all.size>=150)
        assertEquals(all.size,all.map {it.id}.distinct().size)
        all.forEach {f->
            assertTrue(f.name,(f.serving ?: 0.0)>0)
            val v=f.per100
            val macro=v.getValue(Nutrient.FAT)*9+v.getValue(Nutrient.PROTEIN)*4+v.getValue(Nutrient.CARBS)*4+v.getValue(Nutrient.FIBRE)*2
            val kcal=v.getValue(Nutrient.ENERGY)
            // Drinks with alcohol carry energy the macros don't explain.
            if(!f.name.contains("wine") && f.name!="Lager") assertTrue("${f.name}: $kcal vs $macro",kcal<5 || kotlin.math.abs(macro-kcal)/kcal<.25)
            assertTrue(f.name,v.getValue(Nutrient.SUGAR)<=v.getValue(Nutrient.CARBS)+.05)
            assertTrue(f.name,v.getValue(Nutrient.SATURATED)<=v.getValue(Nutrient.FAT)+.05)
        }
    }
}
