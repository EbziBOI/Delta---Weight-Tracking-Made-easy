package io.github.ebziboi.delta.ui

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.render.*
import java.util.Locale
import kotlin.math.abs

@Composable
fun InteractiveChart(spec: ChartSpec, modifier: Modifier = Modifier, bodyFat: List<BodyFatEntry> = emptyList(), onDoubleTap: (() -> Unit)? = null) {
    val colors = LocalDeltaColors.current
    val fonts = LocalCanvasType.current
    var viewport by remember(spec.points.firstOrNull()?.epochMs, spec.points.size, spec.metric) { mutableStateOf<GraphViewport?>(null) }
    var selected by remember(spec.points, spec.metric) { mutableStateOf<TrendPoint?>(null) }
    val layout = remember { arrayOfNulls<ChartLayout>(1) }
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val currentSpec by rememberUpdatedState(spec)
    val zoom = layout[0]?.let { viewport?.zoom(it.full) } ?: 1.0
    fun transform(scale: Float, center: Offset, pan: Offset = Offset.Zero) {
        if (!center.x.isFinite() || !center.y.isFinite()) return
        val frame = layout[0] ?: return
        viewport = (viewport ?: frame.full).transform(frame.full, scale,
            (center.x - frame.plot.left) / frame.plot.width(), (frame.plot.bottom - center.y) / frame.plot.height(),
            pan.x / frame.plot.width(), pan.y / frame.plot.height())
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(modifier.fillMaxWidth().semantics {
            contentDescription = if (spec.metric == ChartMetric.BODY_FAT) "Body fat graph" else "Weight graph"
            stateDescription = String.format(Locale.US, "Zoom %.1fx", zoom) +
                (viewport?.let { " · ${Format.moment(it.first)} to ${Format.moment(it.last)}" } ?: "")
            if (onDoubleTap != null) onClick(label = "Switch graph style") { doubleTap?.invoke(); true }
        }.pointerInput(spec.metric) {
            detectTapGestures(onTap = { selected = layout[0]?.nearest(it.x, it.y) },
                onDoubleTap = { doubleTap?.invoke() })
        }.pointerInput(spec.metric) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var accumulated = Offset.Zero
                var transforming = false
                do {
                    val event = awaitPointerEvent()
                    val pan = event.calculatePan()
                    accumulated += pan
                    val multiple = event.changes.count { it.pressed } > 1
                    if (multiple || (viewport != null && abs(accumulated.x) > viewConfiguration.touchSlop)) transforming = true
                    if (transforming) {
                        transform(event.calculateZoom(), event.calculateCentroid(useCurrent = false), pan)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }) {
            drawIntoCanvas { canvas ->
                layout[0] = TrendChart.draw(canvas.nativeCanvas, RectF(0f, 0f, size.width, size.height),
                    currentSpec.copy(viewport = viewport, selectedPoint = selected), colors, fonts, density, true)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            GlassButton("−", { layout[0]?.let { transform(0.5f, Offset(it.plot.centerX(), it.plot.centerY())) } }, Modifier.widthIn(min = 48.dp).semantics { contentDescription = "Zoom out" })
            TextAction("Reset zoom", { viewport = null; selected = null })
            GlassButton("+", { layout[0]?.let { transform(2f, Offset(it.plot.centerX(), it.plot.centerY())) } }, Modifier.widthIn(min = 48.dp).semantics { contentDescription = "Zoom in" })
        }
        selected?.let { point ->
            val value = if (spec.metric == ChartMetric.BODY_FAT) BodyFat.format(point.kg) else Units.weight(point.kg, spec.unit)
            Text("$value · ${Format.moment(point.epochMs)}", style = LocalDeltaType.current.bodyStrong,
                modifier = Modifier.semantics { contentDescription = "Selected reading $value" })
            if (spec.metric == ChartMetric.WEIGHT) BodyFat.near(point.epochMs, bodyFat)?.let {
                Text("Body fat ${BodyFat.format(it.percent)}", style = LocalDeltaType.current.bodySmall, color = Color(colors.textDim))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextAction("Previous", { val index = spec.points.indexOf(point); if (index > 0) selected = spec.points[index - 1] })
                TextAction("Next", { val index = spec.points.indexOf(point); if (index < spec.points.lastIndex) selected = spec.points[index + 1] })
            }
        } ?: Column {
            Text("Pinch to zoom · drag horizontally to pan dates · tap a reading", style = LocalDeltaType.current.bodySmall, color = Color(colors.textDim))
            if (spec.points.isNotEmpty()) TextAction("Inspect latest reading", { selected = spec.points.last() })
        }
    }
}
