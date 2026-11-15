package io.github.ebziboi.delta.ui

import android.graphics.RectF
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.ebziboi.delta.R
import io.github.ebziboi.delta.render.ChartSpec
import io.github.ebziboi.delta.render.DeltaMark
import io.github.ebziboi.delta.render.DrawColors
import io.github.ebziboi.delta.render.TrendChart
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Delta's glass. Every surface is a pane of translucent material over a deep
 * background lit by soft washes of the accent colour. Screens are built from
 * these pieces only, so the look stays consistent and a theme change restyles
 * everything at once.
 */

/** Space between a screen's edge and its content, and between stacked cards. */
object Spacing {
    val screen = 20.dp
    val stack = 14.dp
    /** Room under the last item so it can scroll clear of the floating bar. */
    val floatingBarClearance = 112.dp
}

// ---- Material ------------------------------------------------------------------------------

/**
 * Paints [shape] as a pane of glass lit from above. The light rule, for every
 * pane in the app and on the widgets:
 *
 * - the top edge carries the brightest rim, strongest at its centre;
 * - the light wraps round the upper corners and runs a little way down the
 *   sides, fading as it goes;
 * - the bottom edge has almost no rim, and the lower part of the body is
 *   shaded slightly darker, as glass is where the light runs out;
 * - a thin, faint outline keeps the shape readable all the way round;
 * - the accent may tint the crest of the top rim, then fades out.
 *
 * [glow] adds a wash of colour inside the pane, as if a light behind it were
 * shining through. [elevated] lifts it with a soft shadow, for things that
 * float over content.
 */
fun Modifier.glass(
    shape: Shape,
    colors: DrawColors,
    glow: Color? = null,
    elevated: Boolean = true,
): Modifier {
    val g = colors.glass
    var result = this
    if (elevated && (g.shadow ushr 24) > 0) {
        result = result.dropShadow(
            shape,
            Shadow(radius = 24.dp, color = Color(g.shadow), offset = DpOffset(0.dp, 9.dp)),
        )
    }
    result = result
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color(g.fillTop), Color(g.fill), Color(g.fill))))
    if (glow != null) {
        result = result.drawBehind {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to glow.copy(alpha = glow.alpha * 0.34f),
                    0.55f to glow.copy(alpha = glow.alpha * 0.10f),
                    1f to Color.Transparent,
                    center = Offset(size.width * 0.9f, size.height * 0.05f),
                    radius = size.maxDimension * 0.8f,
                ),
                radius = size.maxDimension * 0.8f,
                center = Offset(size.width * 0.9f, size.height * 0.05f),
            )
        }
    }
    val light = Color(g.highlight)
    // White rims barely show on light glass, so light themes lean on the outline instead.
    val crestAlpha = if (colors.isLight) 0.95f else 0.78f
    val sideAlpha = if (colors.isLight) 0.55f else 0.34f
    val outline = if (colors.isLight) Color(g.edgeFaint) else Color(g.edge).copy(alpha = 0.10f)
    val tint = glow ?: Color(colors.accent).copy(alpha = 0.12f)
    return result
        .drawBehind {
            // The lower body sits in the glass's own shadow.
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = if (colors.isLight) 0.05f else 0.14f)),
                    startY = size.height * 0.5f,
                    endY = size.height,
                ),
            )
        }
        .drawWithCache {
            val path = shape.createOutline(size, layoutDirection, this)
            val hairline = 1.dp.toPx()
            val rim = 1.5.dp.toPx()
            // Down the sides: bright at the top corners, gone before the bottom edge.
            val sides = Brush.verticalGradient(
                0f to light.copy(alpha = sideAlpha),
                0.28f to light.copy(alpha = sideAlpha * 0.45f),
                0.62f to light.copy(alpha = sideAlpha * 0.08f),
                0.8f to Color.Transparent,
                startY = 0f,
                endY = size.height,
            )
            // The crest: a pool of light centred on the top edge, so the top outshines the sides.
            val reach = maxOf(size.width * 0.62f, 1f)
            val crest = Brush.radialGradient(
                0f to light.copy(alpha = crestAlpha),
                0.55f to light.copy(alpha = crestAlpha * 0.35f),
                1f to Color.Transparent,
                center = Offset(size.width / 2f, 0f),
                radius = reach,
            )
            val accentCrest = Brush.radialGradient(
                0f to tint.copy(alpha = tint.alpha * 0.9f),
                1f to Color.Transparent,
                center = Offset(size.width / 2f, 0f),
                radius = maxOf(size.width * 0.4f, 1f),
            )
            onDrawWithContent {
                drawContent()
                drawOutline(path, outline, style = Stroke(hairline))
                drawOutline(path, sides, style = Stroke(rim))
                drawOutline(path, crest, style = Stroke(rim))
                drawOutline(path, accentCrest, style = Stroke(hairline))
            }
        }
}

/** Shrinks a little while pressed, so glass feels like it gives under the finger. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, pressedScale: Float = 0.97f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val motion = LocalMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && motion) pressedScale else 1f,
        animationSpec = tween(if (motion) 180 else 0, easing = FastOutSlowInEasing),
        label = "press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Fades and lifts content into place the first time it appears; [index] staggers
 * a column of items so they arrive one after another. Shows at once when motion is off.
 */
@Composable
fun Modifier.entrance(index: Int = 0): Modifier {
    val motion = LocalMotion.current
    val progress = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(motion) {
        if (!motion) progress.snapTo(1f)
        else if (progress.value < 1f) {
            delay(45L * index.coerceAtMost(10))
            progress.animateTo(1f, tween(durationMillis = 280, easing = FastOutSlowInEasing))
        }
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 12.dp.toPx()
    }
}

/**
 * The backdrop for every screen: a deep gradient with soft washes of colour that
 * drift very slowly, so the glass above always has light to catch.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) =
    GlassBackground(modifier, content)

// ---- Containers ----------------------------------------------------------------------------

/** A plain pane of glass with no padding or layout of its own, for custom content. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radii.card),
    glow: Color? = null,
    elevated: Boolean = LocalDeltaColors.current.isLight,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalDeltaColors.current
    CompositionLocalProvider(LocalContentColor provides Color(colors.text)) {
        Box(modifier = modifier.refractiveGlass(shape, colors, glow, elevated), content = content)
    }
}

// ---- Text ----------------------------------------------------------------------------------

/** Small capitals label above a value. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = Color(LocalDeltaColors.current.textDim)) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = LocalDeltaType.current.label,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A screen's heading: an optional small line above ([eyebrow], such as today's
 * date), then the title, with an optional [accent] word set in the display
 * face's italic and the accent colour. [trailing] holds buttons on the right.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    accent: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalDeltaColors.current
    val type = LocalDeltaType.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (eyebrow != null) Caption(eyebrow)
            Text(
                text = buildAnnotatedString {
                    append(title)
                    if (accent != null) {
                        append(" ")
                        withStyle(type.displayAccent.toSpanStyle().merge(SpanStyle(color = Color(colors.accent)))) {
                            append(accent)
                        }
                    }
                },
                style = type.display,
                color = Color(colors.text),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = trailing)
    }
}

/** A value with a smaller unit after it, both on one baseline: "82.4 kg". */
@Composable
fun ValueWithUnit(
    value: String,
    unit: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalDeltaType.current.number,
    color: Color = Color(LocalDeltaColors.current.text),
) {
    val colors = LocalDeltaColors.current
    val type = LocalDeltaType.current
    Text(
        text = buildAnnotatedString {
            append(value)
            if (unit != null) {
                withStyle(type.label.toSpanStyle().merge(SpanStyle(color = Color(colors.textDim)))) {
                    append(" ")
                    append(unit)
                }
            }
        },
        modifier = modifier,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Text that rolls to its new value instead of jumping, for numbers that change in place. */
@Composable
fun RollingText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    if (!LocalMotion.current) {
        Text(text, modifier = modifier, style = style, color = color, maxLines = 1)
        return
    }
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            (slideInVertically(tween(320)) { it / 2 } + fadeIn(tween(320))) togetherWith
                (slideOutVertically(tween(260)) { -it / 2 } + fadeOut(tween(200)))
        },
        label = "rolling",
    ) { shown ->
        Text(text = shown, style = style, color = color, maxLines = 1)
    }
}

// ---- Icons and buttons ---------------------------------------------------------------------

/** One of the bundled Material Symbols, tinted to the current content colour unless told otherwise. */
@Composable
fun DeltaIcon(
    icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 22.dp,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

/** A round glass button holding one icon. */
@Composable
fun GlassIconButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = Color(LocalDeltaColors.current.text),
    enabled: Boolean = true,
) {
    val colors = LocalDeltaColors.current
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .pressScale(source, 0.92f)
            .glass(CircleShape, colors, elevated = false)
            .clickable(
                interactionSource = source,
                indication = ripple(color = tint),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        DeltaIcon(icon, contentDescription = null, tint = if (enabled) tint else tint.copy(alpha = 0.4f), size = size * 0.48f)
    }
}

/**
 * The main action on a screen: a pill filled with the accent, running into its
 * partner hue, with a soft glow beneath it.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Int? = null,
) {
    val colors = LocalDeltaColors.current
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(Radii.control)
    val accent = Color(colors.accent)
    val intensity = LocalAppearance.current.accentIntensity
    val fill = if (enabled) {
        Modifier
            .dropShadow(shape, Shadow(radius = 22.dp, color = accent.copy(alpha = 0.28f * intensity), offset = DpOffset(0.dp, 8.dp)))
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(accent, Color(io.github.ebziboi.delta.render.Palettes.mix(colors.accent, colors.accent2, 0.55f))),
                ),
            )
            .innerShadow(shape, Shadow(radius = 2.dp, color = Color.White.copy(alpha = 0.30f), offset = DpOffset(0.dp, 1.dp)))
    } else {
        Modifier.glass(shape, colors, elevated = false)
    }
    val ink = if (enabled) Color(colors.onAccent) else Color(colors.textFaint)
    Row(
        modifier = modifier
            .heightIn(min = 54.dp)
            .pressScale(source)
            .then(fill)
            .clickable(
                interactionSource = source,
                indication = ripple(color = ink),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) DeltaIcon(icon, contentDescription = null, tint = ink, size = 20.dp)
        Text(text = text, style = LocalDeltaType.current.button, color = ink, maxLines = 1)
    }
}

/** A quieter action: a glass pill with plain text. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Int? = null,
) {
    val colors = LocalDeltaColors.current
    val source = remember { MutableInteractionSource() }
    val ink = if (enabled) Color(colors.text) else Color(colors.textFaint)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .pressScale(source)
            .refractiveGlass(RoundedCornerShape(Radii.control), colors, elevated = false)
            .clickable(
                interactionSource = source,
                indication = ripple(color = ink),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) DeltaIcon(icon, contentDescription = null, tint = ink, size = 18.dp)
        Text(text = text, style = LocalDeltaType.current.button, color = ink, maxLines = 1)
    }
}

/** The lightest action: accent-coloured text, for things like "Remove target date". */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Color(LocalDeltaColors.current.accent)) {
    Text(
        text = text,
        modifier = modifier.heightIn(min = 48.dp)
            .clip(RoundedCornerShape(Radii.control))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        style = LocalDeltaType.current.bodyStrong,
        color = color,
    )
}

/** A small status chip, such as "On track". */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, icon: Int? = null) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radii.pill))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(Radii.pill))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) DeltaIcon(icon, contentDescription = null, tint = color, size = 14.dp)
        Text(text = text.uppercase(), style = LocalDeltaType.current.label, color = color, maxLines = 1)
    }
}

// ---- Values --------------------------------------------------------------------------------

/** A figure with a label, a value and an optional note beneath. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueColor: Color = Color.Unspecified,
    note: String? = null,
    icon: Int? = null,
) {
    val colors = LocalDeltaColors.current
    val type = LocalDeltaType.current
    // An open figure, not a tile: label, value and note set straight onto the background.
    Box(modifier = modifier) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (icon != null) DeltaIcon(icon, contentDescription = null, tint = Color(colors.textDim), size = 13.dp)
                Caption(label)
            }
            ValueWithUnit(
                value = value,
                unit = unit,
                style = type.number,
                color = if (valueColor == Color.Unspecified) Color(colors.text) else valueColor,
            )
            if (note != null) {
                Text(
                    text = note,
                    style = type.bodySmall,
                    color = Color(colors.textDim),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The progress bar: a glass rail that fills with the accent from start to
 * target, with a glowing bead where you are now.
 */
@Composable
fun ProgressRail(fraction: Float?, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val colors = LocalDeltaColors.current
    val motion = LocalMotion.current
    val shown by animateFloatAsState(
        targetValue = (fraction ?: 0f).coerceIn(0f, 1f),
        animationSpec = if (motion) tween(durationMillis = 300, easing = FastOutSlowInEasing) else tween(0),
        label = "rail",
    )
    val spoken = if (fraction == null) "No progress yet" else "${(fraction * 100).roundToInt()} percent of the way to your target"
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height + 12.dp)
            .semantics { contentDescription = spoken },
    ) {
        val bar = height.toPx()
        val top = (size.height - bar) / 2f
        val radius = CornerRadius(bar / 2f, bar / 2f)
        drawRoundRect(
            color = Color(colors.glass.fillTop).copy(alpha = max(Color(colors.glass.fillTop).alpha, 0.16f)),
            topLeft = Offset(0f, top),
            size = Size(size.width, bar),
            cornerRadius = radius,
        )
        drawRoundRect(
            color = Color(colors.glass.edgeFaint),
            topLeft = Offset(0f, top),
            size = Size(size.width, bar),
            cornerRadius = radius,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
        )
        if (fraction == null) return@Canvas
        val end = max(bar, size.width * shown)
        val accent = Color(colors.accent)
        val partner = Color(colors.accent2)
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(partner.copy(alpha = 0.85f), accent), startX = 0f, endX = end),
            topLeft = Offset(0f, top),
            size = Size(end, bar),
            cornerRadius = radius,
        )
        val bead = Offset(end - bar / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent), center = bead, radius = bar * 1.6f),
            radius = bar * 1.6f,
            center = bead,
        )
        drawCircle(color = Color.White, radius = bar * 0.42f, center = bead)
    }
}

/**
 * The live delta: the track fills as [fraction] grows and shows the percentage
 * inside, over a soft glow of the accent. Before there is a target it shows
 * the letter instead, as on the icon.
 */
@Composable
fun DeltaTrack(fraction: Float?, modifier: Modifier = Modifier, glow: Boolean = true) {
    val colors = LocalDeltaColors.current
    val canvasType = LocalCanvasType.current
    val motion = LocalMotion.current
    val intensity = LocalAppearance.current.accentIntensity
    val shown by animateFloatAsState(
        targetValue = fraction ?: 0f,
        animationSpec = if (motion) tween(durationMillis = 300, easing = FastOutSlowInEasing) else tween(0),
        label = "delta-progress",
    )
    val spoken = if (fraction == null) "Delta" else "${(fraction * 100).roundToInt()} percent of the way to your target"

    Canvas(modifier = modifier.semantics { contentDescription = spoken }) {
        val side = min(size.width, size.height)
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        if (glow) {
            val center = Offset(size.width / 2f, top + side * 0.6f)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(colors.accent).copy(alpha = (if (colors.isLight) 0.20f else 0.32f) * intensity),
                    0.5f to Color(colors.accent2).copy(alpha = (if (colors.isLight) 0.06f else 0.10f) * intensity),
                    1f to Color.Transparent,
                    center = center,
                    radius = side * 0.62f,
                ),
                radius = side * 0.62f,
                center = center,
            )
        }
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            DeltaMark.draw(native, left, top, side, shown, colors, glyph = fraction == null)
            if (fraction != null) {
                DeltaMark.drawLabel(
                    native, left, top, side,
                    (shown * 100).roundToInt().toString(), "%",
                    colors.text, canvasType.display, canvasType.displayScale,
                )
            }
        }
    }
}

/**
 * The weight graph. The line draws itself in from the left when the data
 * changes, unless motion is off.
 */
@Composable
fun TrendChartView(spec: ChartSpec, modifier: Modifier = Modifier, labels: Boolean = true) {
    val colors = LocalDeltaColors.current
    val canvasType = LocalCanvasType.current
    val motion = LocalMotion.current
    val reveal = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(spec.points.size, spec.points.firstOrNull()?.epochMs, spec.style, motion) {
        if (motion) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(durationMillis = 300, easing = FastOutSlowInEasing))
        } else {
            reveal.snapTo(1f)
        }
    }
    Canvas(modifier = modifier.semantics { contentDescription = "Weight graph" }) {
        drawIntoCanvas { canvas ->
            TrendChart.draw(
                canvas = canvas.nativeCanvas,
                area = RectF(0f, 0f, size.width, size.height),
                spec = spec,
                colors = colors,
                type = canvasType,
                density = density,
                labels = labels,
                reveal = reveal.value,
            )
        }
    }
}

// ---- Choices -------------------------------------------------------------------------------

/**
 * A row of options where exactly one is selected, as segments of one glass
 * pill with the selection sliding between them. Segments share the width of
 * the widest label; give it [Modifier.fillMaxWidth] to stretch.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    val colors = LocalDeltaColors.current
    val type = LocalDeltaType.current
    val index = options.indexOf(selected).coerceAtLeast(0)
    val position by animateFloatAsState(
        targetValue = index.toFloat(),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "segment",
    )
    val shape = RoundedCornerShape(Radii.control)
    val accent = Color(colors.accent)
    Row(
        modifier = modifier
            .width(IntrinsicSize.Max)
            .glass(shape, colors, elevated = false)
            .drawBehind {
                if (options.isEmpty()) return@drawBehind
                val inset = 3.dp.toPx()
                val segment = size.width / options.size
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(segment * position + inset, inset),
                    size = Size(segment - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                )
            }
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, option ->
            val isSelected = i == index
            val ink by animateColorAsState(
                targetValue = if (isSelected) Color(colors.onAccent) else Color(colors.textDim),
                label = "segment-ink",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .clickable(role = Role.Tab) { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = label(option), style = type.bodySmall.copy(fontWeight = type.bodyStrong.fontWeight), color = ink, maxLines = 1)
            }
        }
    }
}

/** A set of glass pills where exactly one is selected. Wraps onto another line if it runs out of room. */
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    val colors = LocalDeltaColors.current
    val type = LocalDeltaType.current
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (option in options) {
            val isSelected = option == selected
            val shape = RoundedCornerShape(Radii.pill)
            val look = if (isSelected) {
                Modifier
                    .clip(shape)
                    .background(Color(colors.accent))
            } else {
                Modifier.glass(shape, colors, elevated = false)
            }
            Text(
                text = label(option),
                modifier = look.heightIn(min = 48.dp)
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .wrapContentHeight(Alignment.CenterVertically)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                style = type.bodySmall.copy(fontWeight = type.bodyStrong.fontWeight),
                color = if (isSelected) Color(colors.onAccent) else Color(colors.text),
                maxLines = 1,
            )
        }
    }
}

// ---- Rows ----------------------------------------------------------------------------------

/** A settings line: what it is on the left, its current value on the right. */
@Composable
fun SettingRow(
    title: String,
    value: String,
    note: String? = null,
    icon: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalDeltaColors.current
    val type = LocalDeltaType.current
    val base = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(Radii.control))
    Row(
        modifier = (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) IconBadge(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = type.body, color = Color(colors.text))
            if (note != null) Text(text = note, style = type.bodySmall, color = Color(colors.textDim))
        }
        Text(
            text = value,
            style = type.numberSmall.copy(fontWeight = type.bodyStrong.fontWeight),
            color = if (onClick != null) Color(colors.accent) else Color(colors.text),
            maxLines = 1,
        )
    }
}

/** A line that opens another page: icon, title, a short summary, and a chevron. */
@Composable
fun NavRow(
    icon: Int,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    value: String? = null,
) {
    val colors = LocalDeltaColors.current
    val type = LocalDeltaType.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.control))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(icon)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(text = title, style = type.bodyStrong, color = Color(colors.text))
            if (subtitle != null) {
                Text(text = subtitle, style = type.bodySmall, color = Color(colors.textDim), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (value != null) Text(text = value, style = type.bodySmall, color = Color(colors.textDim), maxLines = 1)
        DeltaIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = Color(colors.textFaint), size = 20.dp)
    }
}

/** An icon in a small glass square, tinted with the accent. Leads settings rows. */
@Composable
fun IconBadge(icon: Int, modifier: Modifier = Modifier, tint: Color = Color(LocalDeltaColors.current.accent)) {
    GlowingIcon(icon, modifier, tint)
}

/** A setting that is on or off. */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    note: String? = null,
    icon: Int? = null,
) {
    val colors = LocalDeltaColors.current
    val type = LocalDeltaType.current
    Row(
        modifier = Modifier
            .fillMaxWidth().heightIn(min = 48.dp)
            .clip(RoundedCornerShape(Radii.control))
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) IconBadge(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = type.body, color = Color(colors.text))
            if (note != null) Text(text = note, style = type.bodySmall, color = Color(colors.textDim))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(colors.onAccent),
                checkedTrackColor = Color(colors.accent),
                checkedBorderColor = Color(colors.accent),
                uncheckedThumbColor = Color(colors.textDim),
                uncheckedTrackColor = Color(colors.glass.fill),
                uncheckedBorderColor = Color(colors.outline),
            ),
        )
    }
}

/**
 * A setting chosen on a scale, such as widget opacity. [onValueChange] runs
 * while dragging, for a live preview; [onValueChangeFinished] runs once on
 * release, which is the moment to save.
 */
@Composable
fun SliderRow(
    title: String,
    value: Float,
    valueText: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    icon: Int? = null,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    val colors = LocalDeltaColors.current
    val type = LocalDeltaType.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon != null) IconBadge(icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = type.body, color = Color(if (enabled) colors.text else colors.textFaint))
                if (note != null) Text(text = note, style = type.bodySmall, color = Color(colors.textDim))
            }
            Text(text = valueText, style = type.numberSmall, color = Color(if (enabled) colors.accent else colors.textFaint))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            enabled = enabled,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color(colors.accent),
                activeTrackColor = Color(colors.accent),
                inactiveTrackColor = Color(colors.glass.fillTop).copy(alpha = max(Color(colors.glass.fillTop).alpha, 0.2f)),
                disabledThumbColor = Color(colors.textFaint),
                disabledActiveTrackColor = Color(colors.textFaint),
            ),
        )
    }
}

/** A thin rule between groups inside a card. */
@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    val colors = LocalDeltaColors.current
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(colors.glass.edgeFaint)),
    )
}

// ---- Dialogs -------------------------------------------------------------------------------

/**
 * A dialog on a near-opaque pane of glass. [content] sits between the title and
 * the buttons. A [destructive] confirmation is coloured as a warning.
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    dismissLabel: String? = "Cancel",
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalDeltaColors.current
    val type = LocalDeltaType.current
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier) {
        GlassSheet(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(text = title, style = type.title, color = Color(colors.text))
            CompositionLocalProvider(LocalContentColor provides Color(colors.text)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dismissLabel != null) {
                    TextAction(dismissLabel, onClick = onDismissRequest, color = Color(colors.textDim))
                    Spacer(Modifier.width(8.dp))
                }
                if (destructive) {
                    TextAction(confirmLabel, onClick = { if (confirmEnabled) onConfirm() }, color = Color(LocalDerivedTheme.current.danger))
                } else {
                    AccentButton(text = confirmLabel, onClick = onConfirm, enabled = confirmEnabled)
                }
            }
        }
    }
}

/** A full-height column for a screen's content: fills the width, padded at the sides. */
@Composable
fun ScreenColumnSpacer() {
    Spacer(Modifier.height(Spacing.floatingBarClearance))
}

/** Lays out [content] centred in the available space, for empty states. */
@Composable
fun EmptyState(icon: Int, title: String, message: String, modifier: Modifier = Modifier) {
    val colors = LocalDeltaColors.current
    val type = LocalDeltaType.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconBadge(icon)
        Text(text = title, style = type.title, color = Color(colors.text))
        Text(
            text = message,
            style = type.body,
            color = Color(colors.textDim),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
