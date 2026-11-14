package io.github.ebziboi.delta.ui

import android.graphics.RectF
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ebziboi.delta.data.TrendPoint
import io.github.ebziboi.delta.data.WeightUnit
import io.github.ebziboi.delta.render.DeltaMark
import io.github.ebziboi.delta.render.TrendChart
import kotlin.math.min
import kotlin.math.roundToInt

/** The standard card: a bordered surface with evenly spaced content. */
@Composable
fun DeltaCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalDeltaColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color(colors.surface),
        contentColor = Color(colors.text),
        border = BorderStroke(1.dp, Color(colors.outline)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** Small capitals label above a value. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = Color(LocalDeltaColors.current.textDim),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.9.sp,
        maxLines = 1,
    )
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
    note: String? = null,
) {
    val colors = LocalDeltaColors.current
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = Color(colors.surface),
        contentColor = Color(colors.text),
        border = BorderStroke(1.dp, Color(colors.outline)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Caption(label)
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note != null) {
                Text(
                    text = note,
                    fontSize = 12.sp,
                    color = Color(colors.textDim),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The live delta: the track fills as [fraction] grows and shows the percentage
 * inside. Before there is a target it shows the letter instead, as on the icon.
 */
@Composable
fun DeltaTrack(fraction: Float?, modifier: Modifier = Modifier) {
    val colors = LocalDeltaColors.current
    val shown by animateFloatAsState(
        targetValue = fraction ?: 0f,
        animationSpec = tween(durationMillis = 900),
        label = "delta-progress",
    )
    val spoken = if (fraction == null) "Delta" else "${(fraction * 100).roundToInt()} percent of the way to your target"

    Canvas(modifier = modifier.semantics { contentDescription = spoken }) {
        val side = min(size.width, size.height)
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            DeltaMark.draw(native, left, top, side, shown, colors, glyph = fraction == null)
            if (fraction != null) {
                DeltaMark.drawLabel(native, left, top, side, (shown * 100).roundToInt().toString(), "%", colors.text)
            }
        }
    }
}

@Composable
fun TrendChartView(
    points: List<TrendPoint>,
    targetKg: Double?,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDeltaColors.current
    Canvas(modifier = modifier.semantics { contentDescription = "Weight trend graph" }) {
        drawIntoCanvas { canvas ->
            TrendChart.draw(
                canvas = canvas.nativeCanvas,
                area = RectF(0f, 0f, size.width, size.height),
                points = points,
                targetKg = targetKg,
                unit = unit,
                colors = colors,
                density = density,
                labels = true,
            )
        }
    }
}

/** A row of pills where exactly one is selected. Scrolls sideways if it runs out of room. */
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDeltaColors.current
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (option in options) {
            val isSelected = option == selected
            Surface(
                onClick = { onSelect(option) },
                shape = RoundedCornerShape(50),
                color = if (isSelected) Color(colors.accent) else Color(colors.surfaceHigh),
                contentColor = if (isSelected) Color(colors.onAccent) else Color(colors.text),
            ) {
                Text(
                    text = label(option),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A settings line: what it is on the left, its current value on the right. */
@Composable
fun SettingRow(
    title: String,
    value: String,
    note: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalDeltaColors.current
    val base = Modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.small)
    Row(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp)
            if (note != null) {
                Text(text = note, fontSize = 12.sp, color = Color(colors.textDim))
            }
        }
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (onClick != null) Color(colors.accent) else Color(colors.text),
            maxLines = 1,
        )
    }
}
