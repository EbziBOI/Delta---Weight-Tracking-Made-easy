package io.github.ebziboi.delta.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import android.os.Build
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.ebziboi.delta.render.*
import kotlin.math.*

enum class ModuleTone { NEUTRAL, SAGE, VIOLET }

@Composable
fun DashboardModule(modifier: Modifier = Modifier, tone: ModuleTone = ModuleTone.NEUTRAL,
                    onClick: (() -> Unit)? = null, spacing: androidx.compose.ui.unit.Dp = 14.dp,
                    content: @Composable ColumnScope.() -> Unit) {
    val appearance = LocalAppearance.current
    val root = LocalDeltaColors.current
    val base = when (tone) {
        ModuleTone.NEUTRAL -> root.surface
        ModuleTone.SAGE -> TubikTokens.SAGE
        ModuleTone.VIOLET -> Palettes.mix(TubikTokens.VIOLET, android.graphics.Color.BLACK, 0.20f)
    }
    val opacity=if(appearance.reduceTransparency) 1f else appearance.glassOpacity
    val composited=Palettes.mix(root.bg,base,opacity)
    val theme = remember(appearance, composited) { DerivedTheme.derive(appearance.copy(backgroundBase = composited, backgroundSecondary = composited)) }
    val colors = theme.drawColors()
    val shape=RoundedCornerShape(Radii.card)
    val backdrop=LocalGlassBackdrop.current
    val source=rememberHapticInteractionSource()
    var pane = modifier.then(if(onClick!=null) Modifier.pressScale(source,.98f) else Modifier).then(if(LocalMotion.current) Modifier.animateContentSize() else Modifier).fillMaxWidth().clip(shape)
    pane=if(Build.VERSION.SDK_INT>=31 && backdrop!=null && appearance.glassBlurRadius>0 && !appearance.reduceTransparency) pane.hazeEffect(backdrop) {
        backgroundColor=Color(root.bg);blurRadius=appearance.glassBlurRadius.dp;noiseFactor=.01f
        tints=listOf(HazeTint(Color(base).copy(alpha=opacity)))
        fallbackTint=HazeTint(Color(base).copy(alpha=opacity))
    } else pane.background(Color(base).copy(alpha=opacity))
    if (onClick != null) pane = pane.clickable(interactionSource=source,indication=androidx.compose.material3.ripple(),role = Role.Button, onClick = onClick)
    CompositionLocalProvider(LocalDeltaColors provides colors, LocalDerivedTheme provides theme,
        LocalContentColor provides Color(colors.text)) {
        Column(pane.padding(20.dp), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

/** An instrument-style progress dial in place of the glass theme's triangular mark. */
@Composable
fun DashboardDial(fraction: Float?, modifier: Modifier = Modifier) {
    val c = LocalDeltaColors.current
    val progress = fraction?.coerceIn(0f, 1f) ?: 0f
    Box(modifier.semantics { contentDescription = fraction?.let { "${(it * 100).roundToInt()} percent of the way to your target" } ?: "No progress yet" },
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension * 0.43f
            repeat(60) { index ->
                val angle = (index * 6 - 90) * PI.toFloat() / 180
                val outer = radius
                val inner = radius - (if (index % 5 == 0) 8.dp.toPx() else 4.dp.toPx())
                val color = Color(c.text).copy(alpha = if (index % 5 == 0) 0.7f else 0.2f)
                drawLine(color, center + Offset(cos(angle) * inner, sin(angle) * inner),
                    center + Offset(cos(angle) * outer, sin(angle) * outer), 1.dp.toPx())
            }
            val inset = size.minDimension * 0.22f
            drawArc(Color(c.text).copy(alpha = 0.10f), -90f, 360f, false,
                Offset(inset, inset), androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(6.dp.toPx()))
            drawArc(Color(c.accent), -90f, progress * 360, false,
                Offset(inset, inset), androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(6.dp.toPx(), cap = StrokeCap.Butt))
        }
        Text(fraction?.let { "${(it * 100).roundToInt()}%" } ?: "—", style = LocalDeltaType.current.number)
    }
}

@Composable
fun <T> DashboardSegments(options: List<T>,selected:T,label:(T)->String,modifier:Modifier,onSelect:(T)->Unit) =
    SegmentedControl(options,selected,label,modifier,onSelect)
