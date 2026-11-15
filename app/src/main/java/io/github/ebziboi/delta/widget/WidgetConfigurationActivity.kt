package io.github.ebziboi.delta.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
    /** A photo imported in this session but not yet saved; removed again if the user leaves without saving. */
    private var unsavedPhoto: String? = null
    private var store: WidgetStyleStore? = null

    override fun onDestroy() {
        if (isFinishing) unsavedPhoto?.let { photo -> store?.deletePhoto(photo) }
        super.onDestroy()
    }

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
        val store = WidgetStyleStore(this).also { this.store = it }
        lifecycleScope.launch {
            val initial = repo.widgetData.first()
            val previous = store.style(widgetId).first()
            val saved = previous?.takeUnless { it.useAppSettings } ?: WidgetStyle.from(initial.second)
                .copy(timeRange = previous?.timeRange ?: WidgetTimeRange.JOURNEY)
            setContent {
                val data by repo.widgetData.collectAsStateWithLifecycle(initialValue = initial)
                var draft by rememberSaveable { mutableStateOf(saved) }
                var saving by remember { mutableStateOf(false) }
                var failure by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                var importing by remember { mutableStateOf(false) }
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) scope.launch {
                        importing = true; failure = null
                        try {
                            val copied = importPhoto(uri, widgetId)
                            // A photo picked earlier in this session is replaced, so it can go.
                            unsavedPhoto?.let(store::deletePhoto)
                            unsavedPhoto = copied
                            draft = draft.copy(backgroundStyle = BackgroundStyle.IMAGE, backgroundImage = copied)
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) { failure = "Couldn't open that photo. Please choose another." }
                        finally { importing = false }
                    }
                }
                val effective = draft.apply(data.second, AppearanceEngine.resolve(this@WidgetConfigurationActivity, data.second))
                DeltaTheme(effective) {
                    // Light icons: the bars sit over the wallpaper, which is usually darkened in Delta's sheet area.
                    SystemBars(light = false)
                    Surface(Modifier.fillMaxSize(), color = Color.Transparent, contentColor = Color(LocalDeltaColors.current.text)) {
                    WidgetConfigurationScreen(kind, width, height, data.first, effective, draft, saving || importing, failure,
                        onChange = { draft = it }, onCancel = { finish() },
                        onPickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onSave = {
                            saving = true; failure = null
                            scope.launch {
                                try {
                                    store.save(widgetId, draft)
                                    // The photo this widget used before is no longer needed once a new one is saved.
                                    saved.backgroundImage?.takeIf { it != draft.backgroundImage }?.let(store::deletePhoto)
                                    if (draft.backgroundImage == unsavedPhoto) unsavedPhoto = null
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

/** Copies a picked photo into Delta's own storage, scaled down, so the widget can draw it at any time. */
private suspend fun android.content.Context.importPhoto(uri: android.net.Uri, widgetId: Int): String =
    withContext(Dispatchers.IO) {
        val bitmap = android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
            val scale = minOf(1f, 1600f / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val file = java.io.File(filesDir, "${WidgetStyleStore.PHOTO_PREFIX}$widgetId-${System.nanoTime()}.jpg")
        try {
            file.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)) }
            android.net.Uri.fromFile(file).toString()
        } catch (error: Exception) {
            file.delete(); throw error
        } finally {
            bitmap.recycle()
        }
    }

/**
 * The editor a launcher opens from a widget's long-press Settings. Its window is
 * see-through to the wallpaper, so the widget is previewed at its real size
 * against the user's own home screen as each control moves.
 */
@Composable
private fun WidgetConfigurationScreen(
    kind: WidgetKind, width: Float, height: Float, snapshot: ProgressSnapshot, effective: AppSettings,
    draft: WidgetStyle, saving: Boolean, failure: String?, onChange: (WidgetStyle) -> Unit, onCancel: () -> Unit,
    onPickPhoto: () -> Unit, onSave: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val baseAppearance = AppearanceEngine.resolve(context, effective)
    val colors = LocalDeltaColors.current
    val type = LocalDeltaType.current
    var colorPicker by remember { mutableStateOf<String?>(null) }
    val systemNight = androidx.compose.foundation.isSystemInDarkTheme()
    // Which mode the preview shows. Moving a mode's opacity slider switches to that mode.
    var previewNight by rememberSaveable { mutableStateOf(systemNight) }
    val followsPhone = Palettes.widgetFollowsSystem(effective)
    val night = if (followsPhone) previewNight else systemNight
    val appearance = if (baseAppearance.designStyle == DesignStyle.TUBIK && draft.background == null)
        baseAppearance.copy(backgroundBase = AppearanceEngine.forWidget(context, effective, night, kind).appearance.backgroundBase)
        else baseAppearance
    val preview by produceState<android.graphics.Bitmap?>(null, kind, width, height, snapshot, effective, night) {
        kotlinx.coroutines.delay(30)
        value = withContext(Dispatchers.Default) {
            WidgetBitmaps.render(context, kind, width, height, snapshot, effective, night, 1000f)
        }
    }
    val graphed = kind == WidgetKind.TREND || kind == WidgetKind.JOURNEY

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetMax = maxHeight * 0.55f
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                GlassButton("Cancel", onCancel)
                AccentButton(if (saving) "Saving…" else "Save", onSave, enabled = !saving)
            }
            // The widget itself, at the size it has on the home screen, over the real wallpaper.
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(vertical = 16.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center) {
                val scale = minOf(1f, maxWidth.value / width, maxHeight.value / height)
                val bitmap = preview
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), contentDescription = "Live widget preview",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.size((width * scale).dp, (height * scale).dp))
                } else {
                    Spacer(Modifier.size((width * scale).dp, (height * scale).dp))
                }
            }
            if (followsPhone) {
                SegmentedControl(listOf(false, true), previewNight, { if (it) "Dark" else "Light" },
                    Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally).padding(bottom = 12.dp)) { previewNight = it }
            }
            GlassSheet(Modifier.heightIn(max = sheetMax).padding(bottom = 12.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column {
                        Text("Widget settings", style = type.title)
                        Text(kind.title, color = Color(colors.textDim), style = type.bodySmall)
                    }
                    failure?.let { Text(it, color = Color(LocalDerivedTheme.current.danger)) }

                    if (graphed || kind == WidgetKind.BODY_FAT) {
                        Caption("Time range")
                        ChoiceRow(WidgetTimeRange.entries, draft.timeRange, { it.label }) { onChange(draft.copy(timeRange = it)) }
                        Text("Recent ranges change the graph. Journey includes your start, target and optional projection. Overall progress stays the same.",
                            style = type.bodySmall, color = Color(colors.textDim))
                    }
                    val dashboard = appearance.designStyle == DesignStyle.TUBIK
                    if (dashboard) {
                        Caption("Dashboard modules")
                        Text("Solid color modules follow the Tubik inspired design. Colors, content, photos and sizing can still be customized below.",
                            color = Color(colors.textDim), style = type.bodySmall)
                    } else {
                        Caption("Glass")
                        SegmentedControl(WidgetGlass.entries, draft.glass, { it.label }, Modifier.fillMaxWidth()) {
                            onChange(draft.copy(glass = it, opacityDark = it.defaultDark, opacityLight = it.defaultLight))
                        }
                        Text(draft.glass.detail, color = Color(colors.textDim), style = type.bodySmall)
                        if (draft.glass != WidgetGlass.SOLID) {
                            if (appearance.reduceTransparency) Text("Reduce transparency is enabled in Appearance. Turn it off to adjust widget opacity.",
                                style = type.bodySmall, color = Color(colors.textDim))
                            SliderRow("Opacity in dark mode", draft.opacityDark, "${(draft.opacityDark * 100).roundToInt()}%",
                                { previewNight = true; onChange(draft.copy(opacityDark = it)) }, {}, enabled = !appearance.reduceTransparency, valueRange = 0f..1f)
                            SliderRow("Opacity in light mode", draft.opacityLight, "${(draft.opacityLight * 100).roundToInt()}%",
                                { previewNight = false; onChange(draft.copy(opacityLight = it)) }, {}, enabled = !appearance.reduceTransparency, valueRange = 0f..1f)
                        }

                    }
                    Caption("Background")
                    val selectedBackground = effective.appearance?.backgroundStyle ?: BackgroundStyle.MESH
                    val background = if (dashboard && selectedBackground != BackgroundStyle.IMAGE) BackgroundStyle.SOLID else selectedBackground
                    ChoiceRow(if (dashboard) listOf(BackgroundStyle.SOLID, BackgroundStyle.IMAGE) else BackgroundStyle.entries, background, { backgroundLabel(it) }) { chosen ->
                        if (chosen == BackgroundStyle.IMAGE && draft.backgroundImage == null) onPickPhoto()
                        else onChange(draft.copy(backgroundStyle = chosen))
                    }
                    if (background == BackgroundStyle.IMAGE) {
                        GlassButton("Choose another photo", onPickPhoto, Modifier.fillMaxWidth())
                    }
                    GlassButton("Background colour", { colorPicker = "background" }, Modifier.fillMaxWidth())
                    GlassButton("Accent colour", { colorPicker = "accent" }, Modifier.fillMaxWidth())
                    val glow = draft.accentIntensity ?: appearance.accentIntensity
                    if (!dashboard) SliderRow("Accent glow", glow, "${(glow * 100).roundToInt()}%",
                        { onChange(draft.copy(accentIntensity = it)) }, {}, valueRange = 0f..1f)

                    val radius = draft.cornerRadius ?: appearance.cornerRadius
                    SliderRow("Corner radius", radius, "${radius.roundToInt()} dp", { onChange(draft.copy(cornerRadius = it)) }, {}, valueRange = 20f..32f)

                    Caption("Light and dark")
                    ChoiceRow(WidgetTheme.entries, draft.theme, { it.label }) { onChange(draft.copy(theme = it)) }
                    Caption("Text")
                    SegmentedControl(TextContrast.entries, draft.content.text, { it.label }, Modifier.fillMaxWidth()) {
                        onChange(draft.copy(content = draft.content.copy(text = it)))
                    }

                    Caption("Show")
                    val content = draft.content
                    fun show(change: (WidgetContent) -> WidgetContent) = onChange(draft.copy(content = change(content)))
                    when (kind) {
                        WidgetKind.BODY_FAT -> ToggleRow("Body fat target", content.showTarget, { on -> show { it.copy(showTarget = on) } })
                        WidgetKind.PROGRESS -> ToggleRow("How far there is to go", content.showDetail, { on -> show { it.copy(showDetail = on) } })
                        WidgetKind.SUMMARY -> ToggleRow("How far there is to go", content.showDetail, { on -> show { it.copy(showDetail = on) } },
                            note = "On taller widgets, under the bar.")
                        WidgetKind.PACE -> ToggleRow("Ahead, on track or behind", content.showStatus, { on -> show { it.copy(showStatus = on) } },
                            note = "Needs a target date.")
                        WidgetKind.TREND, WidgetKind.JOURNEY -> {
                            SegmentedControl(ChartStyle.entries, draft.chart ?: effective.widgetChart, { it.label },
                                Modifier.fillMaxWidth()) { onChange(draft.copy(chart = it)) }
                            if (draft.timeRange == WidgetTimeRange.JOURNEY) ToggleRow("Path to the goal", draft.projection ?: effective.showProjection,
                                { onChange(draft.copy(projection = it)) }, note = "A dashed line to your target at your current pace.")
                            ToggleRow("Target weight", content.showTarget, { on -> show { it.copy(showTarget = on) } })
                            if (kind == WidgetKind.JOURNEY) {
                                ToggleRow("Progress bar", content.showBar, { on -> show { it.copy(showBar = on) } })
                                ToggleRow("How far there is to go", content.showDetail, { on -> show { it.copy(showDetail = on) } })
                            }
                        }
                    }
                    TextAction("Reset to Delta's look", {
                        onChange(draft.copy(background = null, accent = null, cornerRadius = null, backgroundStyle = null,
                            accentIntensity = null, content = WidgetContent()))
                    })
                    Text("Only this widget changes. Your other widgets keep their own look.",
                        color = Color(colors.textDim), style = type.bodySmall)
                }
            }
        }
        colorPicker?.let { which ->
            CustomColorDialog(if (which == "background") "Background colour" else "Accent colour",
                if (which == "background") appearance.backgroundBase else appearance.accent,
                onDismiss = { colorPicker = null }, onConfirm = {
                    onChange(if (which == "background") draft.copy(background = it) else draft.copy(accent = it)); colorPicker = null
                })
        }
    }
}

private fun backgroundLabel(style: BackgroundStyle) = when (style) {
    BackgroundStyle.SOLID -> "Solid colour"
    BackgroundStyle.VERTICAL_GRADIENT -> "Gradient"
    BackgroundStyle.RADIAL_GRADIENT -> "Glow"
    BackgroundStyle.MESH -> "Mesh"
    BackgroundStyle.IMAGE -> "Photo"
}
