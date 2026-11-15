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
    LaunchedEffect(a.backgroundImageUri) {
        withContext(Dispatchers.IO) {
            val current = a.backgroundImageUri?.let { android.net.Uri.parse(it).path }
            context.filesDir.listFiles()?.filter { it.name.startsWith("appearance-bg-") && it.name.endsWith(".png") && it.absolutePath != current }
                ?.forEach { it.delete() }
        }
    }

    RefractiveGlassCard {
        Caption("Presets")
        Text("Choose a starting point, then make it yours.", style = LocalDeltaType.current.bodySmall, color = Color(colors.textDim))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (preset in AppearancePreset.entries) {
                val value = preset.appearance()
                val selected = a.backgroundBase == value.backgroundBase && a.accent == value.accent
                Column(Modifier.width(94.dp).heightIn(min = 76.dp).clip(RoundedCornerShape(Radii.control))
                    .background(Brush.verticalGradient(listOf(Color(Palettes.mix(value.backgroundBase, value.accent, 0.23f)), Color(value.backgroundBase))))
                    .border(if (selected) 2.dp else 1.dp, Color(if (selected) colors.accent else colors.outline), RoundedCornerShape(Radii.control))
                    .clickable(role = Role.RadioButton) {
                        update { it.copy(backgroundBase = value.backgroundBase, backgroundSecondary = value.backgroundSecondary,
                            accent = value.accent, backgroundStyle = value.backgroundStyle, backgroundImageUri = null) }
                    }.semantics { contentDescription = "${preset.label} preset"; this.selected = selected }
                    .padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(14.dp).background(Color(value.accent), CircleShape))
                    Text(preset.label, color = Color.White, style = LocalDeltaType.current.bodySmall)
                }
            }
        }
    }
    RefractiveGlassCard {
        Caption("Background")
        Text("Your colors are balanced automatically to keep text readable.", style = LocalDeltaType.current.bodySmall, color = Color(colors.textDim))
        ColorSetting("Background color", a.backgroundBase) { picking = "base" }
        ChoiceRow(BackgroundStyle.entries, a.backgroundStyle, { it.label }) { style -> update { it.copy(backgroundStyle = style) } }
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
    RefractiveGlassCard {
        Caption("Accent colour")
        ColorSetting("Accent color", a.accent) { picking = "accent" }
        AppearanceSlider("Accent intensity", a.accentIntensity, 0f..1f, "${(a.accentIntensity * 100).roundToInt()}%") { v -> update { it.copy(accentIntensity = v) } }
    }
    RefractiveGlassCard {
        Caption("Glass and shape")
        AppearanceSlider("Glass blur", a.glassBlurRadius, 0f..48f, "${a.glassBlurRadius.roundToInt()} dp", !a.reduceTransparency) { v -> update { it.copy(glassBlurRadius = v) } }
        AppearanceSlider("Glass transparency", 1f - a.glassOpacity, 0.05f..0.94f, "${((1 - a.glassOpacity) * 100).roundToInt()}%", !a.reduceTransparency) { v -> update { it.copy(glassOpacity = 1 - v) } }
        AppearanceSlider("Corner radius", a.cornerRadius, 20f..32f, "${a.cornerRadius.roundToInt()} dp") { v -> update { it.copy(cornerRadius = v) } }
        ToggleRow("Reduce transparency", a.reduceTransparency, { enabled -> update { it.copy(reduceTransparency = enabled) } },
            note = "Use solid, elevated surfaces for easier reading.")
        ToggleRow("Reduce motion", a.reduceMotion || !settings.motion, { enabled ->
            vm.update { current -> current.copy(motion = !enabled, appearance = AppearanceEngine.resolve(context, current).copy(reduceMotion = enabled)) }
        }, note = "Disable ambient movement, shimmer and decorative transitions.")
    }
    RefractiveGlassCard {
        Caption("Live preview")
        Text("A theme that is yours", style = LocalDeltaType.current.title)
        Text("Your background and accent shape every screen and widget.", color = Color(colors.textDim))
        StatusPill("Up to date", Color(colors.good))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccentButton("Primary", onClick = {})
            GlassButton("Secondary", onClick = {})
        }
    }
    RefractiveGlassCard {
        Caption("Typography")
        ChoiceRow(TypeStyle.entries, settings.typeStyle, { it.label }) { chosen -> vm.update { it.copy(typeStyle = chosen) } }
        Text("Your progress, beautifully clear", style = LocalDeltaType.current.title)
        Text("82.4 kg", style = LocalDeltaType.current.number)
    }
    RefractiveGlassCard {
        Caption("Existing themes")
        Text("Your earlier theme choices are still available.", style = LocalDeltaType.current.bodySmall, color = Color(colors.textDim))
        ChoiceRow(ThemeStyle.entries, settings.theme, { it.label }) { chosen -> vm.update { it.copy(theme = chosen, appearance = null) } }
        ChoiceRow(Accent.entries, settings.accent, { it.label }) { chosen ->
            vm.update { current -> current.copy(accent = chosen, appearance = AppearanceEngine.resolve(context, current)
                .copy(accent = (if (colors.isLight) chosen.onLight else chosen.onDark).toInt())) }
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
private fun AppearanceSlider(title: String, stored: Float, range: ClosedFloatingPointRange<Float>, label: String,
                             enabled: Boolean = true, save: (Float) -> Unit) {
    var value by remember(stored) { mutableStateOf(stored) }
    val display = when {
        title.contains("radius", true) || title.contains("blur", true) -> "${value.roundToInt()} dp"
        else -> "${(value * 100).roundToInt()}%"
    }
    SliderRow(title, value, display, { value = it }, { save(value) }, enabled = enabled, valueRange = range)
}

private fun hex(value: Int) = "#%06X".format(value and 0xFFFFFF)
private fun parseColor(text: String): Int? = text.removePrefix("#").takeIf { it.length == 6 }
    ?.toLongOrNull(16)?.toInt()?.or(0xFF shl 24)

@Composable
fun CustomColorDialog(title: String, initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    var input by remember(initial) { mutableStateOf(hex(initial)) }
    val hsv = remember(value) { FloatArray(3).also { AndroidColor.colorToHSV(value, it) } }
    GlassDialog(onDismiss, title, "Apply", { onConfirm(value) }, confirmEnabled = parseColor(input) != null) {
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
