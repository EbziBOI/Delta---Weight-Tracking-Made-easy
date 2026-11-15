package io.github.ebziboi.delta.ui

import android.os.Build
import androidx.compose.animation.core.*
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

/** Blur only the shared backdrop. Older devices retain the same translucent shading and luminous rim. */
@Composable
fun Modifier.refractiveGlass(
    shape: Shape, colors: DrawColors = LocalDeltaColors.current,
    glow: Color? = null, elevated: Boolean = true,
): Modifier {
    val a = LocalAppearance.current
    val backdrop = LocalGlassBackdrop.current
    val live = Build.VERSION.SDK_INT >= 31 && a.glassBlurRadius > 0f && !a.reduceTransparency && backdrop != null
    var result = this
    if (live) {
        if (elevated) result = result.dropShadow(shape,
            Shadow(radius = 20.dp, color = Color(colors.glass.shadow), offset = DpOffset(0.dp, 7.dp)))
        result = result.clip(shape).hazeEffect(backdrop) {
            backgroundColor = Color(colors.bg)
            blurRadius = a.glassBlurRadius.dp
            noiseFactor = 0.012f
            tints = emptyList()
            fallbackTint = HazeTint(Color(colors.glass.fillTop))
        }
    }
    return result.glass(shape, colors, glow ?: Color(colors.accent).copy(alpha = a.accentIntensity * 0.22f),
        elevated = elevated && !live)
}

@Composable
fun RefractiveGlassCard(
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(Radii.card), glow: Color? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp), spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalDeltaColors.current
    val source = remember { MutableInteractionSource() }
    var pane = modifier.fillMaxWidth().refractiveGlass(shape, colors, glow)
    if (onClick != null) pane = pane.heightIn(min = 48.dp).pressScale(source, 0.98f)
        .clickable(interactionSource = source, indication = ripple(color = Color(colors.text)), role = Role.Button, onClick = onClick)
    CompositionLocalProvider(LocalContentColor provides Color(colors.text)) {
        Column(pane.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
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

@Composable
fun GlassListItem(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(Radii.tile)
    val source = remember { MutableInteractionSource() }
    var m = modifier.fillMaxWidth().heightIn(min = 56.dp).refractiveGlass(shape)
    if (onClick != null) m = m.pressScale(source, 0.98f).clickable(interactionSource = source,
        indication = ripple(), onClick = onClick)
    Row(m.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
fun GlassBottomNavBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    NavigationBar(modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 12.dp)
        .refractiveGlass(RoundedCornerShape((LocalAppearance.current.cornerRadius + 4).dp)),
        windowInsets = WindowInsets(0), containerColor = Color.Transparent, tonalElevation = 0.dp, content = content)
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
    Box(modifier.size(44.dp).drawBehind {
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = intensity * 0.2f), Color.Transparent),
            radius = size.minDimension / 2), radius = size.minDimension / 2)
    }, contentAlignment = Alignment.Center) { DeltaIcon(icon, contentDescription = contentDescription, tint = tint, size = 22.dp) }
}
