package io.github.ebziboi.delta.ui

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.*
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.abs

@Composable
fun InteractiveChart(spec: ChartSpec, modifier: Modifier = Modifier, bodyFat: List<BodyFatEntry> = emptyList(), onDoubleTap: (() -> Unit)? = null,periodPicker:Boolean=true) {
    val haptic=androidx.compose.ui.platform.LocalHapticFeedback.current
    val profile=rememberChartController(if(spec.showFullProjection) "forecast-projection" else "interactive-${spec.metric}",spec.style)
    if(!spec.showFullProjection) {
        val zone=java.time.ZoneId.systemDefault();val today=java.time.LocalDate.now();val c=LocalDeltaColors.current
        val values=spec.points.map {RecordedValue(java.time.Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate(),it.kg)}
        RecordedChart(listOf(RecordedSeries("${spec.metric}",if(spec.metric==ChartMetric.BODY_FAT) "Body fat" else "Weight",Color(c.accent),values,{if(spec.metric==ChartMetric.BODY_FAT) BodyFat.format(it) else Units.weight(it,spec.unit)})),values.minOfOrNull {it.day} ?: today,today,if(spec.metric==ChartMetric.BODY_FAT) "Body fat graph" else "Weight graph",controller=profile,header=if(spec.metric==ChartMetric.BODY_FAT) "Body composition" else "Weight trend",target=spec.targetKg)
        return
    }
    val scope=rememberCoroutineScope();var period by rememberSaveable {mutableStateOf(ChartPeriod.MONTHLY)}
    val colors = LocalDeltaColors.current
    val motion=LocalMotion.current
    val reveal=remember(spec.points.firstOrNull()?.epochMs,spec.metric) { androidx.compose.animation.core.Animatable(if(motion) 0f else 1f) }
    LaunchedEffect(reveal,motion) { if(motion) reveal.animateTo(1f,androidx.compose.animation.core.tween(500)) else reveal.snapTo(1f) }
    val fonts = LocalCanvasType.current
    val viewportSaver=remember {listSaver<GraphViewport?,Any>(save={v->if(v==null) emptyList() else listOf(v.first,v.last,v.low,v.high)},restore={v->if(v.isEmpty()) null else GraphViewport(v[0] as Long,v[1] as Long,v[2] as Double,v[3] as Double)})}
    val pointSaver=remember {listSaver<TrendPoint?,Any>(save={p->if(p==null) emptyList() else listOf(p.epochMs,p.kg,p.trendKg)},restore={p->if(p.isEmpty()) null else TrendPoint(p[0] as Long,p[1] as Double,p[2] as Double)})}
    var viewport by rememberSaveable(spec.points.firstOrNull()?.epochMs,spec.points.size,spec.metric,stateSaver=viewportSaver) {mutableStateOf<GraphViewport?>(null)}
    var selected by rememberSaveable(spec.points,spec.metric,stateSaver=pointSaver) {mutableStateOf<TrendPoint?>(spec.points.lastOrNull())}
    val layout = remember { arrayOfNulls<ChartLayout>(1) }
    var fullRange by remember(spec.points.firstOrNull()?.epochMs,spec.points.size,spec.metric) {mutableStateOf<GraphViewport?>(null)}
    var interacting by remember {mutableStateOf(false)}
    var periodAnimation by remember {mutableStateOf<Job?>(null)}
    val markerReveal by animateFloatAsState(if(interacting) 0f else 1f,
        if(motion) spring(dampingRatio=.85f,stiffness=450f) else snap(),label="graph-reading-snap")
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val currentSpec by rememberUpdatedState(spec)
    fun selectVisible() {
        val shown=viewport ?: layout[0]?.full
        selected=currentSpec.points.lastOrNull {p->shown==null || p.epochMs in shown.first..shown.last}
    }
    fun transform(scale: Float, center: Offset, pan: Offset = Offset.Zero) {
        if (!center.x.isFinite() || !center.y.isFinite()) return
        val frame = layout[0] ?: return
        viewport = (viewport ?: frame.full).transform(frame.full, scale,
            (center.x - frame.plot.left) / frame.plot.width(), (frame.plot.bottom - center.y) / frame.plot.height(),
            pan.x / frame.plot.width(), pan.y / frame.plot.height())
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if(periodPicker) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {ChartPeriodControl(period) {p->
            period=p
            layout[0]?.let {frame->
                val full=frame.full;val old=viewport ?: full
                val span=minOf((p.days*86400000).toLong(),full.last-full.first).coerceAtLeast(1)
                val end=(selected?.epochMs ?: full.last).coerceIn(full.first+span,full.last)
                periodAnimation?.cancel()
                periodAnimation=scope.launch {
                    interacting=true
                    animate(0f,1f,animationSpec=tween(if(motion) 380 else 0)) {v,_->viewport=GraphViewport((old.first+(end-span-old.first)*v).toLong(),(old.last+(end-old.last)*v).toLong(),full.low,full.high)}
                    selectVisible();interacting=false
                }
            }
        }}
        Canvas(modifier.fillMaxWidth().semantics {
            val zoom = fullRange?.let {viewport?.zoom(it)} ?: 1.0
            contentDescription = if (spec.metric == ChartMetric.BODY_FAT) "Body fat graph" else "Weight graph"
            stateDescription = String.format(Locale.US, "Zoom %.1fx", zoom) +
                (viewport?.let { " · ${Format.moment(it.first)} to ${Format.moment(it.last)}" } ?: "")
            onClick(label = "Change chart style") { profile.cycle(); true }
        }.pointerInput(spec.metric) {
            detectTapGestures(onTap = { selected = layout[0]?.nearest(it.x, it.y) },
                onDoubleTap = { profile.cycle();haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) })
        }.pointerInput(spec.metric) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var accumulated = Offset.Zero
                var transforming = false
                var vertical = false
                try { do {
                    val event = awaitPointerEvent()
                    val pan = event.calculatePan()
                    accumulated += pan
                    val multiple = event.changes.count { it.pressed } > 1
                    // Lock a one-finger drag to its first clear direction. Vertical drags
                    // remain with the page even when the chart has already been zoomed.
                    if (!transforming && !vertical && !multiple) {
                        if (event.changes.any { it.isConsumed } ||
                            (abs(accumulated.y) > viewConfiguration.touchSlop && abs(accumulated.y) >= abs(accumulated.x))) vertical = true
                        else if (viewport != null && abs(accumulated.x) > viewConfiguration.touchSlop &&
                            abs(accumulated.x) > abs(accumulated.y) * 1.4f) transforming = true
                    }

                    if (transforming) {
                        periodAnimation?.cancel();interacting=true
                        transform(1f, event.calculateCentroid(useCurrent = false), pan)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                } finally {if(transforming) {selectVisible();interacting=false}}
            }
        }) {
            drawIntoCanvas { canvas ->
                val frame = TrendChart.draw(canvas.nativeCanvas, RectF(0f, 0f, size.width, size.height),
                    currentSpec.copy(style=profile.mode,viewport = viewport, selectedPoint = selected,selectionReveal=markerReveal,highlightLatest=false,
                        endEpochMs=if(currentSpec.showFullProjection) null else java.time.LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()-1), colors, fonts, density, true, reveal = reveal.value)
                layout[0]=frame
                fullRange=frame?.full
            }
        }
        Column(Modifier.graphicsLayer {alpha=markerReveal}) { selected?.let { point ->
            val value = if (spec.metric == ChartMetric.BODY_FAT) BodyFat.format(point.kg) else Units.weight(point.kg, spec.unit)
            Text("$value · ${Format.moment(point.epochMs)}", style = LocalDeltaType.current.bodyStrong,
                modifier = Modifier.semantics { contentDescription = "Selected reading $value" })
            if (spec.metric == ChartMetric.WEIGHT) BodyFat.near(point.epochMs, bodyFat)?.let {
                Text("Body fat ${BodyFat.format(it.percent)}", style = LocalDeltaType.current.bodySmall, color = Color(colors.textDim))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp, androidx.compose.ui.Alignment.CenterHorizontally)) {
                val index = spec.points.indexOf(point)
                TextAction("Previous", { selected = spec.points[index - 1] }, enabled = index > 0)
                TextAction("Next", { selected = spec.points[index + 1] }, enabled = index >= 0 && index < spec.points.lastIndex)
            }
        } ?: Column {
            if (spec.points.isNotEmpty()) TextAction("Inspect latest reading", { selected = spec.points.last() })
        } }
    }
}
