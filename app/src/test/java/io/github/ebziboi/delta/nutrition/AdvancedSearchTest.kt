package io.github.ebziboi.delta.nutrition

import org.junit.Assert.*
import org.junit.Test

class AdvancedSearchTest {
    @Test fun linksAreFoundInShareTextUpgradedAndTrimmed() {
        assertEquals("https://www.tesco.com/groceries/en-GB/products/254656543",
            AdvancedSearch.linkIn("Ferrero Rocher 16 pack - Tesco Groceries https://www.tesco.com/groceries/en-GB/products/254656543."))
        assertEquals("https://example.com/a?b=1", AdvancedSearch.linkIn("http://example.com/a?b=1"))
        assertEquals("https://www.bbcgoodfood.com/recipes/chilli", AdvancedSearch.linkIn("(www.bbcgoodfood.com/recipes/chilli)"))
        // Local, raw-IP and credentialed links are never read.
        listOf("https://localhost/a", "https://192.168.0.1/x", "https://user:pw@example.com/a", "ferrero rocher", "").forEach {assertNull(it, AdvancedSearch.linkIn(it))}
    }

    @Test fun pastedInputIsTakenAsLinkTableOrName() {
        assertEquals("tesco.com", (AdvancedSearch.classify(" https://www.tesco.com/p/1 ") as PastedInput.Link).host)
        val table = AdvancedSearch.classify("Typical values\tPer 100g\nEnergy\t2381kJ / 569kcal\nFat\t36g\nCarbohydrate\t47g\nProtein\t8.2g")
        assertTrue(table is PastedInput.Text); table as PastedInput.Text
        assertEquals(4, table.count)
        val per100 = table.read.columns[table.read.best].per100()
        assertEquals(569.0, per100.getValue(Nutrient.ENERGY), .01); assertEquals(8.2, per100.getValue(Nutrient.PROTEIN), .01)
        assertEquals(PastedInput.Name("ferrero rocher"), AdvancedSearch.classify("ferrero   rocher"))
        assertEquals(PastedInput.Empty, AdvancedSearch.classify("   "))
        // Long text without values is text, so the screen can say nothing was recognised instead of searching for it.
        val essay = AdvancedSearch.classify("A lovely chocolate.\n".repeat(10))
        assertTrue(essay is PastedInput.Text && essay.count == 0)
    }

    @Test fun openFoodFactsLinksResolveToTheirBarcode() {
        assertEquals("3017620422003", AdvancedSearch.openFoodFactsCode("https://world.openfoodfacts.org/product/3017620422003/nutella-ferrero"))
        assertEquals("8000500037560", AdvancedSearch.openFoodFactsCode("https://uk.openfoodfacts.org/product/8000500037560"))
        assertNull(AdvancedSearch.openFoodFactsCode("https://example.com/product/3017620422003"))
        assertNull(AdvancedSearch.openFoodFactsCode("https://world.openfoodfacts.org/category/chocolates"))
    }

    @Test fun completenessNeedsEnergyAndTwoMacros() {
        assertTrue(AdvancedSearch.complete(LabelParser.parse("Per 100g\nEnergy 569kcal\nFat 36g\nProtein 8g")))
        assertFalse(AdvancedSearch.complete(LabelParser.parse("Per 100g\nEnergy 569kcal\nSalt 0.1g\nFat 36g")))
        assertTrue(AdvancedSearch.score(LabelParser.parse("Per 100g\nEnergy 569kcal\nFat 36g")) > AdvancedSearch.score(LabelParser.parse("Fat 36g")))
    }

    @Test fun perPieceColumnsKeepTheirWeightSoPer100StaysReadable() {
        val read = LabelParser.parse("Typical values\tPer 100g\tPer piece (12.5g)\nEnergy\t2381kJ / 569kcal\t298kJ / 71kcal\nFat\t36g\t4.5g\nProtein\t8.2g\t1g")
        assertEquals(listOf(100.0, 12.5), read.columns.map {it.basis.amount})
        assertTrue(read.warnings.none {it.contains("incomplete")})
        assertEquals(569.0, read.columns[read.best].per100().getValue(Nutrient.ENERGY), .01)
        assertEquals(568.0, read.columns[1].per100().getValue(Nutrient.ENERGY), .01)
        assertEquals(9.0, LabelParser.parse("Per 1 biscuit (9 g)\nEnergy 45 kcal\nFat 2 g").columns[0].basis.amount!!, .01)
    }

    @Test fun supermarketReferenceIntakeColumnsAreNotTakenAsTheFoodsValues() {
        // Tesco's table as served: per 100 ml, per 200 ml, %RI, and the adult RI itself.
        val read = LabelParser.parse("Typical Values\tPer 100ml\tPer 200ml\t%RI*\tRI*\nEnergy\t209kJ / 50kcal\t419kJ / 100kcal\t5%\t8400kJ / 2000kcal\n" +
            "Fat\t1.8g\t3.6g\t5%\t70g\nSaturates\t1.1g\t2.2g\t11%\t20g\nCarbohydrate\t4.8g\t9.6g\t4%\t260g\nSugars\t4.8g\t9.6g\t11%\t90g\nFibre\t0g\t0g\t\t\nProtein\t3.6g\t7.2g\t14%\t50g\nSalt\t0.11g\t0.22g\t4%\t6g")
        assertEquals(listOf(100.0, 200.0), read.columns.map {it.basis.amount})
        assertTrue(read.warnings.isEmpty())
        val per100 = read.columns[read.best].per100()
        assertEquals(FoodUnit.MILLILITRES, read.columns[read.best].basis.unit)
        assertEquals(50.0, per100.getValue(Nutrient.ENERGY), .01); assertEquals(1.8, per100.getValue(Nutrient.FAT), .01); assertEquals(3.6, per100.getValue(Nutrient.PROTEIN), .01)
        assertTrue(AdvancedSearch.complete(read))
        // Sainsbury's / Asda style: "Reference intake" and "GDA" headings.
        val gda = LabelParser.parse("Nutrition\tPer 100g\tPer bar (40g)\tReference intake\nEnergy\t480kcal\t192kcal\t2000kcal\nFat\t20g\t8g\t70g\nProtein\t6g\t2.4g\t50g")
        assertEquals(listOf(100.0, 40.0), gda.columns.map {it.basis.amount})
    }

    @Test fun basisIsDescribedPlainly() {
        assertEquals("per 100 g", basisText(LabelBasis(100.0, title = "100 g")))
        assertEquals("per serving (250 g)", basisText(LabelBasis(250.0, title = "serving (250 g)")))
        assertEquals("per serving · add its weight to convert", basisText(LabelBasis(null, title = "Serving")))
    }
}
