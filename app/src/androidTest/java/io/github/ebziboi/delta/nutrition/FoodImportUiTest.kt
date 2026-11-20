package io.github.ebziboi.delta.nutrition

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.ui.*
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class FoodImportUiTest {
    @get:Rule val compose=createComposeRule()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    @Test fun labelReviewIsEditableRestorableAndUsableInBothThemes() {
        val vm=NutritionViewModel(context.applicationContext as DeltaApp)
        var design by mutableStateOf(DesignStyle.GLASS)
        var saved:Food?=null
        val restoration=StateRestorationTester(compose)
        restoration.setContent {key(design) {DeltaTheme(AppSettings(appearance=design.defaults().copy(reduceMotion=true))) {
            Surface(color=Color(LocalDeltaColors.current.bg),contentColor=Color(LocalDeltaColors.current.text)) {
                Box(Modifier.fillMaxSize().systemBarsPadding()) {FoodImportPage(vm,"5060048649352",PaddingValues(0.dp),{}, {saved=it})}
            }
        }}}
        for(style in DesignStyle.entries) {
            compose.runOnIdle {design=style;saved=null}
            compose.onNodeWithText("Product name").performTextReplacement("Oasis label review")
            compose.onNodeWithText("Paste label text").performScrollTo().performClick()
            compose.onNodeWithText("Label transcription").performTextReplacement("Per 100g\nEnergy 590 kcal\nFat 62.9g\nCarbohydrate 3.7g\nProtein 2g\nSalt 1.13g")
            compose.onNodeWithText("Read text").performClick()
            // The dialog's keyboard may still be closing and moving the page; retry once if the tap missed.
            compose.onNodeWithText("Use label values").performScrollTo().performClick();compose.waitForIdle()
            if(compose.onAllNodesWithText("values taken from the label",substring=true).fetchSemanticsNodes().isEmpty()) {
                Thread.sleep(800);compose.onNodeWithText("Use label values").performScrollTo().performClick();compose.waitForIdle()
            }
            compose.onNodeWithText("values taken from the label",substring=true).assertExists()
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("Oasis label review").assertExists()
            compose.onNodeWithText("Save food").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("I've reviewed these values").performScrollTo().performClick()
            compose.onNodeWithText("Save food").performScrollTo().assertIsEnabled()
            val dir=File(context.filesDir,"beta4-review").apply {mkdirs()}
            UiDevice.getInstance(instrumentation).takeScreenshot(File(dir,"$style-label-review.png"))
            compose.onNodeWithText("Save food").performClick()
            compose.runOnIdle {
                assertNotNull(saved);assertEquals("5060048649352",saved!!.barcode)
                assertEquals(590.0,saved!!.per100.getValue(Nutrient.ENERGY),.01)
                assertEquals(setOf(Nutrient.ENERGY,Nutrient.FAT,Nutrient.CARBS,Nutrient.PROTEIN,Nutrient.SALT),saved!!.labelChecked)
            }
        }
    }
    @Test fun optionalLabelComparisonNeverSilentlyOverwritesThePage() {
        val fixture=org.json.JSONObject().put("url","https://example.com/oasis-dip").put("heading","Oasis dip")
            .put("structured",org.json.JSONArray().put("{\"@type\":\"Product\",\"name\":\"Oasis dip\",\"gtin13\":\"5060048649352\"}"))
            .put("text","Nutrition information\nPer 100g\nEnergy 590kcal\nFat 62.9g\nSalt 1.13g")
        val registry=object:androidx.activity.result.ActivityResultRegistry() {
            override fun <I,O> onLaunch(code:Int,contract:androidx.activity.result.contract.ActivityResultContract<I,O>,input:I,options:androidx.core.app.ActivityOptionsCompat?) {
                dispatchResult(code,android.app.Activity.RESULT_OK,android.content.Intent().putExtra("page",fixture.toString()))
            }
        }
        val owner=object:androidx.activity.result.ActivityResultRegistryOwner {override val activityResultRegistry=registry}
        val vm=NutritionViewModel(context.applicationContext as DeltaApp);var saved:Food?=null
        compose.setContent {androidx.compose.runtime.CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides owner) {
            DeltaTheme(AppSettings()) {Surface(color=Color(LocalDeltaColors.current.bg)) {
                FoodImportPage(vm,"5060048649352",PaddingValues(0.dp),{}, {saved=it})
            }}
        }}
        compose.onNodeWithText("Find online").performClick();compose.waitForIdle()
        compose.onNodeWithText("Barcode matched",ignoreCase=true).assertExists()
        compose.onNodeWithText("Paste label text").performScrollTo().performClick()
        compose.onNodeWithText("Label transcription").performTextReplacement("Per 100g\nEnergy 700kcal\nFat 80g\nSalt 2g")
        compose.onNodeWithText("Read text").performClick()
        compose.onAllNodesWithText("Values differ").assertCountEquals(3)
        compose.onNodeWithText("590").assertExists()
        compose.onNodeWithText("Label 700 kcal").performScrollTo().performClick()
        compose.onNodeWithText("700").assertExists()
        compose.onNodeWithText("I've reviewed these values").performScrollTo().performClick()
        compose.onNodeWithText("Save food").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(700.0,saved!!.per100.getValue(Nutrient.ENERGY),.01)
            assertEquals(62.9,saved!!.per100.getValue(Nutrient.FAT),.01)
            assertEquals(setOf(Nutrient.ENERGY),saved!!.labelChecked)
            assertEquals("https://example.com/oasis-dip",saved!!.sourceUrl)
        }
    }
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    @Test fun actualWebDocumentExtractionImportsTheProductAndNutritionTable() {
        val done=CountDownLatch(1);val output=AtomicReference<String>();var browser:WebView?=null
        instrumentation.runOnMainSync {
            browser=WebView(context).apply {
                settings.javaScriptEnabled=true
                webViewClient=object:WebViewClient() {
                    override fun onPageFinished(view:WebView,url:String) {view.evaluateJavascript(ProductPages.extractScript) {output.set(it);done.countDown()}}
                }
                loadDataWithBaseURL("https://example.com/product/dip","""<html><head><title>Oasis dip</title><script type="application/ld+json">{"@type":"Product","name":"Oasis dip","gtin13":"5060048649352"}</script></head><body><main><h1>Oasis dip</h1><table><tr><th>Typical values</th><th>Per 100 g</th></tr><tr><td>Energy</td><td>590 kcal</td></tr><tr><td>Fat</td><td>62.9 g</td></tr><tr><td>Protein</td><td>2 g</td></tr></table></main></body></html>""","text/html","UTF-8",null)
            }
        }
        try {
            assertTrue("Rendered page extraction timed out",done.await(25,TimeUnit.SECONDS))
            val raw=JSONTokener(output.get()).nextValue() as String
            val product=ProductPages.parse(raw,"5060048649352")
            assertTrue(product.barcodeMatch);assertEquals("Oasis dip",product.name)
            assertEquals(590.0,product.read.columns[product.read.best].per100().getValue(Nutrient.ENERGY),.01)
        } finally {instrumentation.runOnMainSync {browser?.destroy()}}
    }
}
