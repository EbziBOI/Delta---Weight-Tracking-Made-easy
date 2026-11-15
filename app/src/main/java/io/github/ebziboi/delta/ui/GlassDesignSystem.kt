package io.github.ebziboi.delta.ui

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.*
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import io.github.ebziboi.delta.render.DrawColors
import io.github.ebziboi.delta.data.DesignStyle

/** The tab bar's surface: a frosted blur of the backdrop where supported, otherwise the plain translucent tile. */
@Composable
fun Modifier.refractiveGlass(
    shape: Shape, colors: DrawColors = LocalDeltaColors.current,
    glow: Color? = null, elevated: Boolean = true,
): Modifier {
    val a = LocalAppearance.current
    val backdrop = LocalGlassBackdrop.current
    if (a.designStyle == DesignStyle.TUBIK) return clip(shape).background(controlTile(colors))
    val live = Build.VERSION.SDK_INT >= 31 && a.glassBlurRadius > 0f && !a.reduceTransparency && backdrop != null
    if (!live) return glass(shape, colors)
    // Where the phone can blur, the tile is a frosted blur of what is behind it, tinted
    // with the same translucent tone as every other control, and still with no outline.
    return clip(shape).hazeEffect(backdrop) {
        backgroundColor = Color(colors.bg)
        blurRadius = a.glassBlurRadius.dp
        noiseFactor = 0.01f
        tints = listOf(HazeTint(glassTile(colors, a)))
        fallbackTint = HazeTint(glassTile(colors, a))
    }
}

@Composable
fun controlTile(colors: DrawColors = LocalDeltaColors.current): Color {
    val a = LocalAppearance.current
    return if (a.designStyle == DesignStyle.TUBIK) Color(io.github.ebziboi.delta.render.Palettes.mix(colors.surfaceHigh,colors.accent2,.12f))
        .copy(alpha = if(a.reduceTransparency) 1f else .86f) else glassTile(colors,a)
}

/**
 * A block of content laid straight onto the background, with no pane around it.
 * Glass is kept for the things you touch (buttons, pickers, the tab bar); content
 * is set apart by space and a hairline above it instead. [accent] tints the start
 * of that hairline for a section that needs attention or carries the main figure.
 */
@Composable
fun ContentSection(
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    accent: Color? = null, spacing: Dp = 12.dp, tone: ModuleTone = ModuleTone.NEUTRAL,
    framed: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalAppearance.current.designStyle == DesignStyle.TUBIK) {
        DashboardModule(modifier, tone, onClick, spacing, content)
        return
    }
    val colors = LocalDeltaColors.current
    if (framed) {
        CompositionLocalProvider(LocalContentColor provides Color(colors.text)) {
            Column(modifier.fillMaxWidth().then(if(LocalMotion.current) Modifier.animateContentSize() else Modifier).glass(RoundedCornerShape(Radii.card),colors).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(spacing),content=content)
        }
        return
    }
    val rule = Color(colors.text).copy(alpha = if (colors.isLight) 0.12f else 0.14f)
    var section = modifier.fillMaxWidth().drawBehind {
        val y = 0.5.dp.toPx()
        val line = 1.dp.toPx()
        drawLine(rule, Offset(0f, y), Offset(size.width, y), line)
        if (accent != null) {
            val reach = minOf(size.width, 140.dp.toPx())
            drawLine(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0f)), 0f, reach),
                Offset(0f, y), Offset(reach, y), 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
    if (onClick != null) section = section.clickable(role = Role.Button, onClick = onClick)
    CompositionLocalProvider(LocalContentColor provides Color(colors.text)) {
        Column(section.padding(top = 20.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

@Composable
fun GlassTextField(
    value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier, singleLine: Boolean = true,
    suffix: @Composable (() -> Unit)? = null, keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = LocalDeltaColors.current
    val shape = RoundedCornerShape(Radii.control)
    OutlinedTextField(value, onValueChange, modifier = modifier.heightIn(min = 56.dp), singleLine = singleLine,
        shape = shape, suffix = suffix, keyboardOptions = keyboardOptions,
        textStyle = LocalDeltaType.current.number,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color(c.text), unfocusedTextColor = Color(c.text),
            focusedContainerColor = Color(c.glass.fill), unfocusedContainerColor = Color(c.glass.fill),
            focusedBorderColor = Color(c.accent), unfocusedBorderColor = Color(c.glass.edge), cursorColor = Color(c.accent)))
}

@Composable
fun GlassTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    val c = LocalDeltaColors.current
    OutlinedTextField(value, onValueChange, label = { Text(label) }, singleLine = true, isError = isError,
        shape = RoundedCornerShape(Radii.control), modifier = modifier.heightIn(min = 56.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color(c.glass.fill),
            unfocusedContainerColor = Color(c.glass.fill), focusedBorderColor = Color(c.accent),
            unfocusedBorderColor = Color(c.glass.edge), cursorColor = Color(c.accent)))
}

@Composable
fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: Int? = null) =
    PrimaryButton(text, onClick, modifier, enabled, icon)

@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: Int? = null) =
    SecondaryButton(text, onClick, modifier, enabled, icon)

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier, icon: Int? = null) = Pill(text, color, modifier, icon)

/** A row in a plain list: no pane, just a hairline beneath to separate it from the next. */
@Composable
fun ListRow(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val colors = LocalDeltaColors.current
    val rule = Color(colors.text).copy(alpha = if (colors.isLight) 0.10f else 0.11f)
    val dashboard = LocalAppearance.current.designStyle == DesignStyle.TUBIK
    var row = modifier.fillMaxWidth().heightIn(min = 56.dp)
    if (dashboard) row = row.clip(RoundedCornerShape(Radii.tile)).background(Color(colors.surface))
    else row = row.drawBehind {
        drawLine(rule, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx())
    }
    if (onClick != null) row = row.clickable(onClick = onClick)
    Row(row.padding(horizontal = if (dashboard) 16.dp else 0.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
fun GlassBottomNavBar(modifier: Modifier = Modifier, onLog: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics { contentDescription = "Navigation dock" }) {
            NavigationBar(modifier.width(216.dp).height(56.dp).refractiveGlass(RoundedCornerShape(28.dp)),
                windowInsets = WindowInsets(0), containerColor = Color.Transparent, tonalElevation = 0.dp, content = content)
            if (onLog != null) GlassIconButton(io.github.ebziboi.delta.R.drawable.ic_add, "Log weight", onLog,
                size = 48.dp, tint = Color(LocalDeltaColors.current.accent))
        }
    }
}

@Composable
fun GlassSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalDeltaColors.current
    val shape = RoundedCornerShape(Radii.card)
    val solid = LocalAppearance.current.reduceTransparency
    val pane = colors.copy(glass = colors.glass.copy(
        fill = io.github.ebziboi.delta.render.Palettes.withAlpha(colors.surface, if (solid) 255 else 240),
        fillTop = io.github.ebziboi.delta.render.Palettes.withAlpha(colors.surfaceHigh, if (solid) 255 else 245)))
    Column(modifier.fillMaxWidth().background(Color(colors.glass.scrim), shape)
        .refractiveGlass(shape, pane).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

@Composable
fun ProgressRing(progress: Float? = null, modifier: Modifier = Modifier) {
    val c = LocalDeltaColors.current
    val turn = if (progress == null && LocalMotion.current) rememberInfiniteTransition(label = "progress-ring")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "progress-turn").value else 0f
    Canvas(modifier.size(24.dp).semantics { contentDescription = if (progress == null) "Loading" else "${(progress * 100).toInt()} percent" }) {
        val width = 2.5.dp.toPx()
        drawCircle(Color(c.outline), style = Stroke(width))
        drawArc(Color(c.accent), turn - 90f, (progress?.coerceIn(0f, 1f) ?: 0.24f) * 360,
            false, topLeft = Offset(width / 2, width / 2), size = size.copy(width = size.width - width, height = size.height - width),
            style = Stroke(width, cap = StrokeCap.Round))
    }
}

@Composable
fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    val c = LocalDeltaColors.current
    val phase = if (LocalMotion.current) rememberInfiniteTransition(label = "shimmer")
        .animateFloat(-1f, 2f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "shimmer-position").value else 0.5f
    Canvas(modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(Radii.tile)).semantics { contentDescription = "Loading content" }) {
        drawRect(Color(c.surface))
        drawRect(Brush.linearGradient(listOf(Color.Transparent, Color(c.accentSoft), Color.Transparent),
            Offset(size.width * (phase - 0.5f), 0f), Offset(size.width * (phase + 0.5f), size.height)))
    }
}

@Composable
fun GlowingIcon(icon: Int, modifier: Modifier = Modifier, tint: Color = Color(LocalDeltaColors.current.accent), contentDescription: String? = null) {
    val intensity = LocalAppearance.current.accentIntensity
    val dashboard = LocalAppearance.current.designStyle == DesignStyle.TUBIK
    Box(modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).drawBehind {
        if (dashboard) {
            drawCircle(tint.copy(alpha = 0.15f))
            return@drawBehind
        }
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = intensity * 0.2f), Color.Transparent),
            radius = size.minDimension / 2), radius = size.minDimension / 2)
    }, contentAlignment = Alignment.Center) { DeltaIcon(icon, contentDescription = contentDescription, tint = tint, size = 22.dp) }
}
