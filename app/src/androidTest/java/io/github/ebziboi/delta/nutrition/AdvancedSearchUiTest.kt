package io.github.ebziboi.delta.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Advanced search: links read off-screen (script-rendered tables and recipe schema), pasted text, and the search-bar hand-off, reviewed in Tubik. */
@RunWith(AndroidJUnit4::class)
class AdvancedSearchUiTest {
    @get:Rule val compose=createComposeRule()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val out=File(context.filesDir,"advanced-search").apply {mkdirs()}
    private fun shot(name:String) {compose.waitForIdle();Thread.sleep(300);device.takeScreenshot(File(out,"$name.png"))}
    private val tubikDark=AppSettings(appearance=DesignStyle.TUBIK.defaults().copy(reduceMotion=true))
    private val tubikLight=AppSettings(appearance=DesignStyle.TUBIK.defaults().copy(reduceMotion=true,backgroundBase=0xFFF3F2EE.toInt(),backgroundSecondary=0xFFE6E4DE.toInt()))

    @Test fun scriptRenderedShopTableIsReadOffScreen()=runBlocking {
        // The table only exists once the page's script runs, sits in a collapsed accordion (empty innerText), and carries
        // a reference-intake column, as on Tesco.
        val html="""<html><head><title>Ferrero Rocher 16 Pack 200G - Groceries</title>
            <script type="application/ld+json">{"@type":"Product","name":"Ferrero Rocher 16 Pack 200G","brand":{"@type":"Brand","name":"Ferrero"},"gtin13":"8000500037560"}</script></head>
            <body><main><h1>Ferrero Rocher 16 Pack 200G</h1><div id="n" hidden="until-found"></div></main><script>setTimeout(function(){document.getElementById('n').innerHTML=
            '<table><tr><th>Typical values</th><th>Per 100g</th><th>Per piece (12.5g)</th><th>RI*</th></tr><tr><td>Energy</td><td>2381kJ / 569kcal</td><td>298kJ / 71kcal</td><td>8400kJ / 2000kcal</td></tr>'+
            '<tr><td>Fat</td><td>36g</td><td>4.5g</td><td>70g</td></tr><tr><td>of which saturates</td><td>11.6g</td><td>1.4g</td></tr><tr><td>Carbohydrate</td><td>47g</td><td>5.9g</td></tr>'+
            '<tr><td>of which sugars</td><td>40g</td><td>5g</td></tr><tr><td>Protein</td><td>8.2g</td><td>1g</td></tr><tr><td>Salt</td><td>0.13g</td><td>0.02g</td></tr></table>'},1500)</script></body></html>"""
        val raw=PageReader.read(context,"https://shop.example.com/products/ferrero",html=html,timeoutMs=15000)
        val page=ProductPages.parse(raw)
        assertEquals("Ferrero Rocher 16 Pack 200G",page.name);assertEquals("Ferrero",page.brand);assertEquals("8000500037560",page.barcode)
        val per100=page.read.columns[page.read.best].per100()
        assertEquals(569.0,per100.getValue(Nutrient.ENERGY),.5);assertEquals(8.2,per100.getValue(Nutrient.PROTEIN),.01);assertEquals(.13,per100.getValue(Nutrient.SALT),.001)
    }

    @Test fun recipeSchemaPerServingConvertsWithItsServingWeight()=runBlocking {
        val html="""<html><head><title>Chilli con carne</title><script type="application/ld+json">{"@context":"https://schema.org","@graph":[{"@type":"WebPage"},
            {"@type":"Recipe","name":"Chilli con carne","nutrition":{"@type":"NutritionInformation","servingSize":"1 bowl (250 g)","calories":"350 calories","fatContent":"12 g",
            "carbohydrateContent":"30","proteinContent":"28 g","sodiumContent":"400 mg"}}]}</script></head><body><h1>Chilli con carne</h1><p>Method…</p></body></html>"""
        val page=ProductPages.parse(PageReader.read(context,"https://recipes.example.com/chilli",html=html,timeoutMs=10000))
        assertEquals("Chilli con carne",page.name)
        val column=page.read.columns[page.read.best]
        assertEquals(250.0,column.basis.amount!!,.01)
        val per100=column.per100()
        assertEquals(140.0,per100.getValue(Nutrient.ENERGY),.01);assertEquals(12.0,per100.getValue(Nutrient.CARBS),.01);assertEquals(160.0,per100.getValue(Nutrient.SODIUM),.01)
    }

    @Test fun pagesWithoutNutritionFailWithAWayForward()=runBlocking {
        val error=runCatching {PageReader.read(context,"https://blog.example.com/post",html="<html><body><h1>Our story</h1><p>Hello</p></body></html>",timeoutMs=4000)}.exceptionOrNull()
        assertTrue(error is PageReadException);assertTrue(error!!.message!!.contains("paste its nutrition text"))
        assertTrue(runCatching {PageReader.read(context,"http://example.com/a")}.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun pastedTextFillsTheReviewAndSavesInTubik() {
        val vm=NutritionViewModel(context.applicationContext as DeltaApp);var saved:Food?=null
        var settings by mutableStateOf(tubikDark)
        compose.setContent {key(settings) {DeltaTheme(settings) {Surface(color=Color(LocalDeltaColors.current.bg),contentColor=Color(LocalDeltaColors.current.text)) {
            Box(Modifier.fillMaxSize().systemBarsPadding()) {FoodImportPage(vm,"",PaddingValues(0.dp),{},{saved=it})}
        }}}}
        compose.onNodeWithText("Paste from the web",ignoreCase=true).assertExists()
        compose.onNodeWithText("Paste").assertExists()
        shot("tubik-dark-empty")
        compose.onNodeWithText("Link, nutrition text or name").performTextReplacement("Nutrition\tPer 100g\nEnergy\t2381kJ / 569kcal\nFat\t36g\nCarbohydrate\t47g\nProtein\t8.2g\nSalt\t0.13g")
        compose.onNodeWithText("Nutrition text · 5 values").assertExists()
        compose.onNodeWithText("Read text").performClick();compose.waitForIdle()
        compose.onNodeWithText("Pasted text",ignoreCase=true).assertExists()
        compose.onNodeWithText("5 values per 100 g").assertExists()
        compose.onNodeWithText("Values add up · ready to save",ignoreCase=true).assertExists()
        shot("tubik-dark-text-read")
        compose.onNodeWithText("Product name").performScrollTo().performTextReplacement("Ferrero Rocher")
        compose.onNodeWithText("Save food").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("Ferrero Rocher",saved!!.name);assertEquals(569.0,saved!!.per100.getValue(Nutrient.ENERGY),.5)
            assertNull("Pasted text has no page to cite",saved!!.sourceUrl);assertTrue(saved!!.labelChecked.isEmpty())
        }
        // A name is looked up on the web, not parsed.
        compose.onNodeWithText("Link, nutrition text or name").performScrollTo().performTextReplacement("ferrero rocher")
        compose.onNodeWithText("Product name · searches the web").assertExists();compose.onNodeWithText("Search the web").assertExists()
        // And a link is offered as a page to read.
        compose.onNodeWithText("Link, nutrition text or name").performTextReplacement("Look at this https://www.tesco.com/groceries/en-GB/products/254656543")
        compose.onNodeWithText("Link · tesco.com").assertExists();compose.onNodeWithText("Read page").assertExists()
        compose.runOnIdle {settings=tubikLight}
        compose.onNodeWithText("Link, nutrition text or name").performTextReplacement("Nutrition\tPer 100g\nEnergy\t569kcal\nFat\t36g\nProtein\t8.2g")
        compose.onNodeWithText("Read text").performClick();shot("tubik-light-text-read")
    }

    @Test fun aLinkTypedIntoFoodSearchOffersToReadThePage() {
        val vm=NutritionViewModel(context.applicationContext as DeltaApp);var opened:String?=null
        compose.setContent {DeltaTheme(tubikDark) {Surface(color=Color(LocalDeltaColors.current.bg),contentColor=Color(LocalDeltaColors.current.text)) {
            val state by vm.state.collectAsState()
            Box(Modifier.fillMaxSize().systemBarsPadding()) {FoodSearchPage(vm,state,PaddingValues(0.dp),{},{},{},{},{},onImport={opened=it},onCustomNamed={})}
        }}}
        compose.onAllNodesWithText("Advanced").onFirst().assertExists()
        compose.onNodeWithContentDescription("Search foods").performTextReplacement("https://www.sainsburys.co.uk/gol-ui/product/ferrero-rocher-x16-200g")
        compose.waitForIdle();Thread.sleep(1200);compose.waitForIdle()
        compose.onNodeWithText("Read nutrition from sainsburys.co.uk").assertExists()
        // The URL is not sent to the product database as search words.
        assertFalse(vm.searching);assertTrue(vm.results.isEmpty())
        compose.onAllNodesWithText("Supermarket products",substring=true).assertCountEquals(0)
        shot("tubik-search-link")
        compose.onNodeWithText("Read nutrition from sainsburys.co.uk").performClick()
        compose.runOnIdle {assertEquals("https://www.sainsburys.co.uk/gol-ui/product/ferrero-rocher-x16-200g",opened)}
        compose.onNodeWithContentDescription("Search foods").performTextReplacement("zzqx unknown snack")
        compose.waitUntil(20000) {compose.onAllNodesWithText("Advanced search").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Advanced search").performScrollTo().performClick()
        compose.runOnIdle {assertEquals("zzqx unknown snack",opened)}
    }
}
