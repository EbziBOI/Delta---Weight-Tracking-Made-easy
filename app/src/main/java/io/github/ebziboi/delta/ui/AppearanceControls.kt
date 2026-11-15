package io.github.ebziboi.delta.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.render.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.*

/** All controls edit one appearance value, persisted by the existing settings flow. */
@Composable
fun AppearanceControls(vm: AppViewModel, settings: AppSettings) {
    val context = LocalContext.current
    val a = LocalAppearance.current
    val colors = LocalDeltaColors.current
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var imageError by remember { mutableStateOf<String?>(null) }
    fun update(change: (AppAppearance) -> AppAppearance) {
        vm.update { current -> current.copy(appearance = change(AppearanceEngine.resolve(context, current)).normalized()) }
    }
    fun preview(change: (AppAppearance) -> AppAppearance) = vm.previewAppearance(change(a))
    fun commit(change: (AppAppearance) -> AppAppearance) = vm.saveAppearance(change(a))
    DisposableEffect(vm) { onDispose { vm.previewAppearance(null) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true; imageError = null
            try {
                val saved = withContext(Dispatchers.IO) {
                    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                        val scale = min(1f, 2048f / max(info.size.width, info.size.height))
                        decoder.setTargetSize(max(1, (info.size.width * scale).toInt()), max(1, (info.size.height * scale).toInt()))
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                    val file = File(context.filesDir, "appearance-bg-${System.nanoTime()}.png")
                    try {
                        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                        android.net.Uri.fromFile(file).toString()
                    } catch (error: Exception) { file.delete(); throw error }
                    finally { bitmap.recycle() }
                }
                update { it.copy(backgroundImageUri = saved, backgroundStyle = BackgroundStyle.IMAGE) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { imageError = "Couldn't open that image. Please choose another photo." }
            finally { importing = false }
        }
    }
    LaunchedEffect(a.backgroundImageUri, settings.savedGlassAppearance?.backgroundImageUri, settings.savedTubikAppearance?.backgroundImageUri) {
        withContext(Dispatchers.IO) {
            val retained = listOfNotNull(a.backgroundImageUri, settings.savedGlassAppearance?.backgroundImageUri,
                settings.savedTubikAppearance?.backgroundImageUri).mapNotNull { android.net.Uri.parse(it).path }.toSet()
            context.filesDir.listFiles()?.filter { it.name.startsWith("appearance-bg-") && it.name.endsWith(".png") && it.absolutePath !in retained }
                ?.forEach { it.delete() }
        }
    }

    IconCarousel(settings.appIcon, vm::selectIcon)

    Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Caption("Design system")
        SegmentedControl(DesignStyle.entries,a.designStyle,{if(it==DesignStyle.GLASS) "Glass" else "Tubik"},Modifier.fillMaxWidth()) { chosen ->
            vm.update { current -> current.withDesignStyle(chosen,AppearanceEngine.resolve(context,current)) }
        }
        if(a.designStyle==DesignStyle.TUBIK) TextAction("Reset palette", {
            update {DesignStyle.TUBIK.defaults().copy(reduceMotion=it.reduceMotion,reduceTransparency=it.reduceTransparency)}
        })
    }
    if (a.designStyle == DesignStyle.GLASS) ContentSection {
        Caption("Presets")
        AdaptiveOptionGrid(AppearancePreset.entries) { preset,cell ->
            val value=preset.appearance()
            val active=a.backgroundBase==value.backgroundBase && a.accent==value.accent
            Column(cell.heightIn(min=80.dp).clip(RoundedCornerShape(Radii.control))
                .background(Brush.verticalGradient(listOf(Color(Palettes.mix(value.backgroundBase,value.accent,.23f)),Color(value.backgroundBase))))
                .border(if(active) 2.dp else 1.dp,Color(if(active) colors.accent else colors.outline),RoundedCornerShape(Radii.control))
                .clickable(role=Role.RadioButton) {update {it.copy(backgroundBase=value.backgroundBase,backgroundSecondary=value.backgroundSecondary,
                    accent=value.accent,backgroundStyle=value.backgroundStyle,backgroundImageUri=null)}}
                .semantics {contentDescription="${preset.label} preset";selected=active}.padding(12.dp),
                verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(14.dp).background(Color(value.accent),CircleShape))
                Text(preset.label,color=Color.White,style=LocalDeltaType.current.bodySmall,maxLines=1)
            }
        }
    }
    ContentSection {
        Caption("Background")
        ColorSetting("Background color", a.backgroundBase) { picking = "base" }
        ChoiceRow(if (a.designStyle == DesignStyle.TUBIK) BackgroundStyle.entries.filter { it != BackgroundStyle.MESH } else BackgroundStyle.entries, a.backgroundStyle, { it.label }) { style -> update { it.copy(backgroundStyle = style) } }
        if (a.backgroundStyle in listOf(BackgroundStyle.VERTICAL_GRADIENT, BackgroundStyle.RADIAL_GRADIENT, BackgroundStyle.MESH)) {
            ColorSetting("Secondary background color", a.backgroundSecondary) { picking = "secondary" }
        }
        if (a.backgroundStyle == BackgroundStyle.IMAGE) {
            GlassButton(if (a.backgroundImageUri == null) "Choose background image" else "Change background image",
                onClick = { picker.launch(arrayOf("image/*")) }, enabled = !importing)
            if (importing) ShimmerPlaceholder()
            if (a.backgroundImageUri != null) TextAction("Remove image", { update { it.copy(backgroundImageUri = null, backgroundStyle = BackgroundStyle.MESH) } })
            imageError?.let { Text(it, color = Color(LocalDerivedTheme.current.danger)) }
        }
    }
    ContentSection {
        Caption("Accent colour")
        ColorSetting("Accent color", a.accent) { picking = "accent" }
        if (a.designStyle == DesignStyle.GLASS) AppearanceSlider("Accent intensity", a.accentIntensity, 0f..1f, preview = { v -> preview { it.copy(accentIntensity = v) } }) { v -> commit { it.copy(accentIntensity = v) } }
    }
    ContentSection {
        Caption("Material and shape")
        GlassMaterialPreview()
        AppearanceSlider("Glass blur", a.glassBlurRadius, 0f..48f, !a.reduceTransparency && android.os.Build.VERSION.SDK_INT >= 31,
            preview = { v -> preview { it.copy(glassBlurRadius = v) } }) { v -> commit { it.copy(glassBlurRadius = v) } }
        AppearanceSlider("Glass transparency", 1f - a.glassOpacity, 0.05f..0.94f, !a.reduceTransparency,
            preview = { v -> preview { it.copy(glassOpacity = 1 - v) } }) { v -> commit { it.copy(glassOpacity = 1 - v) } }
        if (android.os.Build.VERSION.SDK_INT < 31) Text("Blur requires Android 12 or later. Transparency still works here.", style = LocalDeltaType.current.bodySmall)
        if (a.reduceTransparency) Text("Turn off Reduce transparency to adjust blur and transparency.", style = LocalDeltaType.current.bodySmall)
        AppearanceSlider("Corner radius", a.cornerRadius, 20f..32f, preview = { v -> preview { it.copy(cornerRadius = v) } }) { v -> commit { it.copy(cornerRadius = v) } }
        ToggleRow("Reduce transparency", a.reduceTransparency, { enabled -> update { it.copy(reduceTransparency = enabled) } },
            note = null)
        ToggleRow("Reduce motion", a.reduceMotion || !settings.motion, { enabled ->
            vm.update { current -> current.copy(motion = !enabled, appearance = AppearanceEngine.resolve(context, current).copy(reduceMotion = enabled)) }
        }, note = null)
    }
    ContentSection {
        Caption("Live preview")
        StatusPill("Up to date", Color(colors.good))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccentButton("Primary", onClick = {})
            GlassButton("Secondary", onClick = {})
        }
    }
    ContentSection {
        Caption("Typography")
        ChoiceRow(TypeStyle.entries, settings.typeStyle, { it.label }) { chosen -> vm.update { it.copy(typeStyle = chosen) } }
        Text("82.4 kg", style = LocalDeltaType.current.number)
    }
    if (a.designStyle == DesignStyle.GLASS) ContentSection {
        Caption("Main theme")
        AdaptiveOptionGrid(ThemeStyle.entries) { theme,cell ->
            val look=AppearanceEngine.resolve(context,settings.copy(theme=theme,appearance=null))
            val active=settings.theme==theme && (settings.appearance==null ||
                (a.backgroundBase==look.backgroundBase && a.backgroundSecondary==look.backgroundSecondary && a.backgroundStyle==look.backgroundStyle))
            Row(cell.heightIn(min=52.dp).clip(RoundedCornerShape(Radii.control))
                .background(if(active) Color(colors.accent).copy(alpha=.18f) else Color(colors.surfaceHigh).copy(alpha=.5f))
                .clickable(role=Role.RadioButton) {vm.update {it.copy(theme=theme,appearance=null)}}
                .semantics {selected=active;contentDescription="${theme.label} main theme"}.padding(10.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(18.dp).background(Color(look.backgroundBase),CircleShape).border(1.dp,Color(colors.outline),CircleShape))
                Text(theme.label,style=LocalDeltaType.current.bodySmall,maxLines=2)
            }
        }
        Caption("Accent color")
        AdaptiveOptionGrid(Accent.entries) { accent,cell ->
            val color=(if(colors.isLight) accent.onLight else accent.onDark).toInt()
            val active=if(settings.appearance==null) settings.accent==accent else a.accent==color
            Row(cell.heightIn(min=48.dp).clip(RoundedCornerShape(Radii.control)).background(Color(colors.surfaceHigh).copy(alpha=if(active) .9f else .45f))
                .clickable(role=Role.RadioButton) {vm.update {current ->current.copy(accent=accent,appearance=AppearanceEngine.resolve(context,current).copy(accent=color))}}
                .semantics {selected=active;contentDescription="${accent.label} accent color"}.padding(10.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(18.dp).background(Color(color),CircleShape))
                Text(accent.label,style=LocalDeltaType.current.bodySmall,color=Color(if(active) colors.accent else colors.text),maxLines=1)
            }
        }
    }

    if (picking != null) {
        val which = picking!!
        CustomColorDialog(title = when (which) { "base" -> "Background color"; "secondary" -> "Secondary background color"; else -> "Accent color" },
            initial = when (which) { "base" -> a.backgroundBase; "secondary" -> a.backgroundSecondary; else -> a.accent },
            onDismiss = { picking = null }, onConfirm = { color ->
                update { when (which) { "base" -> it.copy(backgroundBase = color); "secondary" -> it.copy(backgroundSecondary = color); else -> it.copy(accent = color) } }
                picking = null
            })
    }
}

@Composable
private fun ColorSetting(title: String, value: Int, onClick: () -> Unit) {
    val c = LocalDeltaColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(Radii.control))
        .clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).background(Color(value), CircleShape).border(1.dp, Color(c.outline), CircleShape))
        Text(title, modifier = Modifier.weight(1f), style = LocalDeltaType.current.body)
        Text(hex(value), style = LocalDeltaType.current.numberSmall, color = Color(c.textDim))
    }
}

@Composable
private fun AppearanceSlider(title: String, stored: Float, range: ClosedFloatingPointRange<Float>,
                             enabled: Boolean = true, preview: (Float) -> Unit, save: (Float) -> Unit) {
    var value by remember { mutableFloatStateOf(stored) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(stored) { if (!dragging) value = stored }
    val display = if (title.contains("radius", true) || title.contains("blur", true)) "${value.roundToInt()} dp"
        else "${(value * 100).roundToInt()}%"
    SliderRow(title, value, display, { dragging = true; value = it; preview(it) },
        { dragging = false; save(value) }, enabled = enabled, valueRange = range)
}

private fun hex(value: Int) = "#%06X".format(value and 0xFFFFFF)
private fun parseColor(text: String): Int? = text.removePrefix("#").takeIf { it.length == 6 }
    ?.toLongOrNull(16)?.toInt()?.or(0xFF shl 24)

@Composable
fun CustomColorDialog(title: String, initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    var input by remember(initial) { mutableStateOf(hex(initial)) }
    val hsv = remember(value) { FloatArray(3).also { AndroidColor.colorToHSV(value, it) } }
    // Closing the dialog also closes the keyboard the hex field opened, so it is not left over the page.
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    fun close() { focus.clearFocus(force = true); keyboard?.hide() }
    GlassDialog({ close(); onDismiss() }, title, "Apply", { close(); onConfirm(value) }, confirmEnabled = parseColor(input) != null) {
        Box(Modifier.fillMaxWidth().height(60.dp).background(Color(value), RoundedCornerShape(Radii.control)), contentAlignment = Alignment.Center) {
            Text(hex(value), color = Color(ThemeContrast.on(value)), style = LocalDeltaType.current.number)
        }
        GlassTextField(input, { input = it; parseColor(it)?.let { parsed -> value = parsed } }, "Hex color", Modifier.fillMaxWidth(), parseColor(input) == null)
        fun change(index: Int, selected: Float) {
            val updated = hsv.copyOf(); updated[index] = selected
            value = AndroidColor.HSVToColor(updated); input = hex(value)
        }
        Text("Hue", style = LocalDeltaType.current.bodySmall)
        Slider(hsv[0], { change(0, it) }, valueRange = 0f..360f)
        Text("Saturation", style = LocalDeltaType.current.bodySmall)
        Slider(hsv[1], { change(1, it) })
        Text("Brightness", style = LocalDeltaType.current.bodySmall)
        Slider(hsv[2], { change(2, it) })
    }
}
