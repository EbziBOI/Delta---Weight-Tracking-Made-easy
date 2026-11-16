package io.github.ebziboi.delta.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.render.*
import io.github.ebziboi.delta.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

class FoodWidgetConfigurationActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)
        val result=Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);setResult(RESULT_CANCELED,result)
        val manager=AppWidgetManager.getInstance(this);val info=manager.getAppWidgetInfo(id)
        val kind=info?.takeIf {it.provider.packageName==packageName}?.let {FoodWidgetCatalog.kind(it.provider.className)}
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID || kind==null) {finish();return}
        val options=manager.getAppWidgetOptions(id)
        val width=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf {it>0}?.toFloat() ?: kind.width
        val height=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf {it>0}?.toFloat() ?: kind.height
        val app=application as DeltaApp;val store=WidgetStyleStore(this)
        enableEdgeToEdge()
        lifecycleScope.launch {
            val initial=app.repo.settings.first();val prior=store.style(id).first() ?: WidgetStyle()
            setContent {
                val saved by app.repo.settings.collectAsStateWithLifecycle(initial)
                val food by app.nutrition.state.collectAsStateWithLifecycle()
                var draft by rememberSaveable {mutableStateOf(prior)}
                var busy by remember {mutableStateOf(false)};var failure by remember {mutableStateOf(false)}
                var previewNight by rememberSaveable {mutableStateOf(Palettes.isNight(this@FoodWidgetConfigurationActivity))}
                val effective=draft.apply(saved,AppearanceEngine.resolve(this@FoodWidgetConfigurationActivity,saved))
                val reduced=AppearanceEngine.resolve(this@FoodWidgetConfigurationActivity,effective).reduceTransparency
                val preview by produceState<android.graphics.Bitmap?>(null,food,effective,previewNight) {
                    value=withContext(Dispatchers.Default) {FoodWidgetBitmaps.render(this@FoodWidgetConfigurationActivity,kind,width,height,food,effective,previewNight)}
                }
                val scope=rememberCoroutineScope()
                DeltaTheme(saved) {
                    Surface(color=Color(LocalDeltaColors.current.bg),contentColor=Color(LocalDeltaColors.current.text)) {
                        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            ScreenHeader(kind.title)
                            ContentSection {
                                BoxWithConstraints(Modifier.fillMaxWidth()) {
                                    val scale=minOf(1f,maxWidth.value/width,320f/height)
                                    preview?.let {Image(it.asImageBitmap(),"Live ${kind.title} preview",Modifier.size((width*scale).dp,(height*scale).dp))}
                                }
                            }
                            ToggleRow("Follow app appearance",draft.useAppSettings,{draft=draft.copy(useAppSettings=it)})
                            if(!draft.useAppSettings) ContentSection {
                                Caption("Theme")
                                SegmentedControl(WidgetTheme.entries,draft.theme,{it.label}) {draft=draft.copy(theme=it)}
                                Caption("Material")
                                SegmentedControl(WidgetGlass.entries,draft.glass,{it.label}) {draft=draft.copy(glass=it,opacityDark=it.defaultDark,opacityLight=it.defaultLight)}
                                SliderRow("Opacity in dark mode",draft.opacityDark,"${(draft.opacityDark*100).roundToInt()}%",{previewNight=true;draft=draft.copy(opacityDark=it)},{},enabled=!reduced && draft.glass!=WidgetGlass.SOLID,valueRange=0f..1f)
                                SliderRow("Opacity in light mode",draft.opacityLight,"${(draft.opacityLight*100).roundToInt()}%",{previewNight=false;draft=draft.copy(opacityLight=it)},{},enabled=!reduced && draft.glass!=WidgetGlass.SOLID,valueRange=0f..1f)
                                SliderRow("Corner radius",draft.cornerRadius ?: AppearanceEngine.resolve(this@FoodWidgetConfigurationActivity,saved).cornerRadius,"${(draft.cornerRadius ?: AppearanceEngine.resolve(this@FoodWidgetConfigurationActivity,saved).cornerRadius).roundToInt()} dp",{draft=draft.copy(cornerRadius=it)},{},valueRange=20f..32f)
                            }
                            if(failure) Text("Couldn't save this widget. Try again.")
                            PrimaryButton(if(busy) "Saving…" else "Save",{
                                busy=true;scope.launch {
                                    try {
                                        store.save(id,draft)
                                        FoodWidgetCatalog.widget(kind).update(this@FoodWidgetConfigurationActivity,GlanceAppWidgetManager(this@FoodWidgetConfigurationActivity).getGlanceIdBy(id))
                                        setResult(RESULT_OK,result);finish()
                                    } catch(e:CancellationException) {throw e} catch(_:Exception) {busy=false;failure=true}
                                }
                            },Modifier.fillMaxWidth(),enabled=!busy)
                            SecondaryButton("Cancel",{finish()},Modifier.fillMaxWidth(),enabled=!busy)
                        }
                    }
                }
            }
        }
    }
}
