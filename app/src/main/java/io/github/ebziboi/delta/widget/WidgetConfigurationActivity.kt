package io.github.ebziboi.delta.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.render.*
import io.github.ebziboi.delta.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Android launchers, including One UI Home, open this from a widget's long-press Settings action. */
class WidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_CANCELED, result)
        val manager = AppWidgetManager.getInstance(this)
        val info = manager.getAppWidgetInfo(widgetId)
        val kind = info?.takeIf { it.provider.packageName == packageName }?.let { WidgetCatalog.kind(it.provider.className) }
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || kind == null) { finish(); return }
        val options = manager.getAppWidgetOptions(widgetId)
        val defaults = when (kind) {
            WidgetKind.PROGRESS, WidgetKind.PACE -> 170f to 170f
            WidgetKind.SUMMARY -> 350f to 100f
            else -> 350f to 200f
        }
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, defaults.first.toInt()).toFloat().coerceIn(90f, 700f)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, defaults.second.toInt()).toFloat().coerceIn(60f, 700f)
        enableEdgeToEdge()
        val repo = (application as DeltaApp).repo
        val store = WidgetStyleStore(this)
        lifecycleScope.launch {
            val initial = repo.widgetData.first()
            val saved = store.style(widgetId).first() ?: WidgetStyle.from(initial.second)
            setContent {
                val data by repo.widgetData.collectAsStateWithLifecycle(initialValue = initial)
                var draft by rememberSaveable { mutableStateOf(saved) }
                var saving by remember { mutableStateOf(false) }
                var failure by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                val effective = draft.apply(data.second, AppearanceEngine.resolve(this@WidgetConfigurationActivity, data.second))
                DeltaTheme(effective) {
                    SystemBars(light = LocalDeltaColors.current.isLight)
                    Surface(Modifier.fillMaxSize(), color = Color.Transparent, contentColor = Color(LocalDeltaColors.current.text)) {
                    WidgetConfigurationScreen(kind, width, height, data.first, effective, draft, saving, failure,
                        onChange = { draft = it }, onCancel = { finish() }, onSave = {
                            saving = true; failure = null
                            scope.launch {
                                try {
                                    store.save(widgetId, draft)
                                    WidgetUpdater.updateOne(this@WidgetConfigurationActivity, widgetId, kind)
                                    setResult(RESULT_OK, result)
                                    finish()
                                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                catch (_: Exception) { failure = "Couldn't save this widget. Please try again."; saving = false }
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetConfigurationScreen(
    kind: WidgetKind, width: Float, height: Float, snapshot: ProgressSnapshot, effective: AppSettings,
    draft: WidgetStyle, saving: Boolean, failure: String?, onChange: (WidgetStyle) -> Unit, onCancel: () -> Unit, onSave: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appearance = AppearanceEngine.resolve(context, effective)
    val colors = LocalDeltaColors.current
    var colorPicker by remember { mutableStateOf<String?>(null) }
    val night = androidx.compose.foundation.isSystemInDarkTheme()
    val preview by produceState<android.graphics.Bitmap?>(null, kind, width, height, snapshot, effective, night) {
        kotlinx.coroutines.delay(35)
        value = withContext(Dispatchers.Default) {
            WidgetBitmaps.render(context, kind, width, height, snapshot, effective, night, 850f)
        }
    }
    GlassBackground {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val previewHeight = (maxHeight * 0.28f).coerceIn(100.dp, 220.dp)
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TextAction("Cancel", onCancel)
                Text("Widget settings", style = LocalDeltaType.current.title)
                AccentButton(if (saving) "Saving…" else "Save", onSave, enabled = !saving)
            }
            Text(kind.detail, color = Color(colors.textDim), modifier = Modifier.padding(vertical = 8.dp))
            Box(Modifier.fillMaxWidth().height(previewHeight).padding(vertical = 12.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                preview?.let { bitmap -> Image(bitmap.asImageBitmap(), contentDescription = "Live widget preview",
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) } ?: ShimmerPlaceholder()
            }
            failure?.let { Text(it, color = Color(LocalDerivedTheme.current.danger)) }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RefractiveGlassCard {
                    ToggleRow("Use app widget settings", draft.useAppSettings,
                        { onChange(draft.copy(useAppSettings = it)) }, note = "Turn off to give just this widget its own look.")
                }
                if (!draft.useAppSettings) {
                    RefractiveGlassCard {
                        Caption("Glass")
                        ChoiceRow(WidgetGlass.entries, draft.glass, { it.label }) {
                            onChange(draft.copy(glass = it, opacityDark = it.defaultDark, opacityLight = it.defaultLight))
                        }
                        Caption("Color mode")
                        ChoiceRow(WidgetTheme.entries, draft.theme, { it.label }) { onChange(draft.copy(theme = it)) }
                        if (draft.glass != WidgetGlass.SOLID) {
                            SliderRow("Dark mode opacity", draft.opacityDark, "${(draft.opacityDark * 100).roundToInt()}%",
                                { onChange(draft.copy(opacityDark = it)) }, {}, valueRange = 0f..1f)
                            SliderRow("Light mode opacity", draft.opacityLight, "${(draft.opacityLight * 100).roundToInt()}%",
                                { onChange(draft.copy(opacityLight = it)) }, {}, valueRange = 0f..1f)
                        }
                    }
                    RefractiveGlassCard {
                        Caption("Colors and shape")
                        GlassButton("Background color", { colorPicker = "background" })
                        GlassButton("Accent color", { colorPicker = "accent" })
                        val radius = draft.cornerRadius ?: appearance.cornerRadius
                        SliderRow("Corner radius", radius, "${radius.roundToInt()} dp", { onChange(draft.copy(cornerRadius = it)) }, {}, valueRange = 20f..32f)
                        Text("Corners adapt to your launcher's shape. Reduced transparency in the app also applies here.",
                            color = Color(colors.textDim), style = LocalDeltaType.current.bodySmall)
                        TextAction("Reset colors and corners", { onChange(draft.copy(background = null, accent = null, cornerRadius = null)) })
                    }
                }
                Text("Changes affect this widget after you save.", style = LocalDeltaType.current.bodySmall, color = Color(colors.textDim))
            }
        }
        }
        colorPicker?.let { which ->
            CustomColorDialog(if (which == "background") "Background color" else "Accent color",
                if (which == "background") appearance.backgroundBase else appearance.accent,
                onDismiss = { colorPicker = null }, onConfirm = {
                    onChange(if (which == "background") draft.copy(background = it) else draft.copy(accent = it)); colorPicker = null
                })
        }
    }
}
