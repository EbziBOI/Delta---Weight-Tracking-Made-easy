package io.github.ebziboi.delta.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

internal data class RecordedValue(val day:LocalDate,val value:Double)
internal data class RecordedSeries(val id:String,val label:String,val color:Color,val values:List<RecordedValue>,
                                   val format:(Double)->String,val bars:Boolean=false,val ceiling:Double?=null,val typical:Double?=null)
internal fun latestRecordedDay(series:List<RecordedSeries>,first:Long,last:Long):LocalDate? = series.flatMap {it.values}
    .filter {it.day.toEpochDay() in first..last}.maxOfOrNull {it.day}

/** Shared date-only viewport. Empty dates never become measurements. */
@Composable
internal fun RecordedChart(series:List<RecordedSeries>,start:LocalDate,end:LocalDate,description:String,
                           onSelected:(LocalDate?)->Unit={}) {
    val colors=LocalDeltaColors.current;val motion=LocalMotion.current;val haptic=LocalHapticFeedback.current
    val styledSeries=series.map {it.copy(color=Color(io.github.ebziboi.delta.render.ThemeContrast.readable(it.color.toArgb(),colors.bg)))}
    val fullFirst=start.toEpochDay().toDouble();val fullLast=end.toEpochDay().toDouble()
    val fullSpan=(fullLast-fullFirst).coerceAtLeast(1.0)
    var first by remember(start,end) {mutableDoubleStateOf(fullFirst)}
    var last by remember(start,end) {mutableDoubleStateOf(fullLast)}
    var moving by remember {mutableStateOf(false)}
    var selected by remember(start,end,styledSeries.map {it.id}) {mutableStateOf<LocalDate?>(null)}
    val currentSeries by rememberUpdatedState(styledSeries);val selectedCallback by rememberUpdatedState(onSelected)
    val shownFirst=kotlin.math.ceil(first).toLong();val shownLast=kotlin.math.floor(last).toLong()
    fun selectLatest() {
        selected=latestRecordedDay(currentSeries,kotlin.math.ceil(first).toLong(),kotlin.math.floor(last).toLong())
        selectedCallback(selected)
    }
    LaunchedEffect(start,end,styledSeries.map {it.id to it.values}) {selectLatest()}
    val fade by animateFloatAsState(if(moving || selected==null) 0f else 1f,if(motion) tween(220) else snap(),label="reading-highlight")
    val markerX by animateFloatAsState(((selected?.toEpochDay()?.minus(first) ?: 0.0)/(last-first).coerceAtLeast(1.0)).toFloat(),
        if(motion) spring(dampingRatio=.85f,stiffness=500f) else snap(),label="reading-snap")
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            styledSeries.forEach {s->
                val range=s.values.filter {it.day>=start && it.day<=end}.map {it.value}
                Text(s.label+if(range.isEmpty()) " · No data" else " · ${s.format(if(s.bars) 0.0 else range.min())} – ${s.format(s.ceiling ?: range.max())}",
                    style=LocalDeltaType.current.bodySmall,color=s.color)
            }
        }
        styledSeries.filter {it.bars && it.typical!=null}.forEach {s->
            Text("30-day median · ${s.format(s.typical!!)}"+(if(s.typical>0) " at 75% height" else ""),style=LocalDeltaType.current.bodySmall,color=Color(colors.textDim))
        }
        Canvas(Modifier.fillMaxWidth().height(220.dp).semantics {
            contentDescription=description
            stateDescription="${LocalDate.ofEpochDay(shownFirst)} to ${LocalDate.ofEpochDay(shownLast)}; "+
                styledSeries.joinToString {s->"${s.label}: ${s.values.count {it.day.toEpochDay() in shownFirst..shownLast}} recorded days"+(s.ceiling?.let {"; scale ceiling $it; ${s.values.count {p->p.value>it && p.day.toEpochDay() in shownFirst..shownLast}} above scale"} ?: "")}+
                (selected?.let {"; Selected $it"} ?: "")
        }.pointerInput(start,end,styledSeries.map {it.id}) {
            detectTapGestures {tap->
                val day=first+(tap.x/size.width).coerceIn(0f,1f)*(last-first)
                selected=currentSeries.flatMap {it.values}.filter {it.day.toEpochDay().toDouble() in first..last}
                    .minByOrNull {abs(it.day.toEpochDay()-day)}?.day
                selectedCallback(selected)
            }
        }.pointerInput(start,end,styledSeries.map {it.id}) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed=false)
                var distance=Offset.Zero;var horizontal=false;var vertical=false
                try {
                    do {
                        val event=awaitPointerEvent();val pan=event.calculatePan();distance+=pan
                        val multiple=event.changes.count {it.pressed}>1
                        if(!horizontal && !vertical) {
                            if(event.changes.any {it.isConsumed} || (!multiple && abs(distance.y)>viewConfiguration.touchSlop && abs(distance.y)>=abs(distance.x))) vertical=true
                            else if(multiple || (abs(distance.x)>viewConfiguration.touchSlop && abs(distance.x)>abs(distance.y)*1.4f)) horizontal=true
                        }
                        if(horizontal) {
                            moving=true
                            val span=(last-first).coerceAtLeast(1.0)
                            val zoom=event.calculateZoom().takeIf {it.isFinite() && it>0f} ?: 1f
                            val next=(span/zoom).coerceIn(minOf(2.0,fullSpan),fullSpan)
                            val anchor=(event.calculateCentroid(useCurrent=false).x/size.width).takeIf {it.isFinite()}?.coerceIn(0f,1f) ?: .5f
                            val from=(first+span*anchor-next*anchor-pan.x/size.width*span).coerceIn(fullFirst,fullLast-next)
                            first=from;last=from+next
                            event.changes.forEach {if(it.positionChanged()) it.consume()}
                        }
                    } while(event.changes.any {it.pressed})
                } finally {
                    if(horizontal) {
                        selected=latestRecordedDay(currentSeries,kotlin.math.ceil(first).toLong(),kotlin.math.floor(last).toLong())
                        selectedCallback(selected);moving=false
                        if(selected!=null && motion) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            }
        }) {
            val inset=8.dp.toPx();val w=size.width-2*inset;val h=size.height-16.dp.toPx()
            fun x(day:LocalDate)=inset+((day.toEpochDay()-first)/(last-first).coerceAtLeast(1.0)*w).toFloat()
            drawLine(Color(colors.outline).copy(alpha=.45f),Offset(inset,h),Offset(size.width-inset,h),1.dp.toPx())
            if(fade>0) {
                val px=inset+markerX.coerceIn(0f,1f)*w
                drawLine(Color(colors.textDim).copy(alpha=fade*.6f),Offset(px,0f),Offset(px,h),1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(),4.dp.toPx())))
            }
            styledSeries.forEach {s->
                val all=s.values.filter {it.day>=start && it.day<=end};val values=all.filter {it.day.toEpochDay().toDouble() in first..last}
                val low=if(s.bars) 0.0 else (all.minOfOrNull {it.value} ?: 0.0)-.5
                val high=if(s.bars) s.ceiling ?: (all.maxOfOrNull {it.value} ?: 1.0).coerceAtLeast(1e-12) else (all.maxOfOrNull {it.value} ?: 1.0)+.5
                fun y(v:Double)=8.dp.toPx()+(1-(v.coerceIn(low,high)-low)/(high-low).coerceAtLeast(1e-12))*(h-8.dp.toPx())
                var previous:RecordedValue?=null
                values.forEach {p->
                    val px=x(p.day);val py=y(p.value).toFloat()
                    if(s.bars) {
                        val width=(w/(last-first+1)*.55f).toFloat().coerceIn(2.dp.toPx(),26.dp.toPx())
                        if(p.value>0) drawRoundRect(s.color.copy(alpha=.65f),Offset(px-width/2,py),Size(width,(h-py).coerceAtLeast(1f)),androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                    } else previous?.let {prev->drawLine(s.color.copy(alpha=.75f),Offset(x(prev.day),y(prev.value).toFloat()),Offset(px,py),2.dp.toPx(),StrokeCap.Round)}
                    if(s.bars && p.value>high) {
                        val triangle=androidx.compose.ui.graphics.Path().apply {
                            moveTo(px,1.dp.toPx());lineTo(px-4.dp.toPx(),7.dp.toPx());lineTo(px+4.dp.toPx(),7.dp.toPx());close()
                        }
                        drawPath(triangle,Color(colors.text))
                    } else drawCircle(s.color,2.8.dp.toPx(),Offset(px,py))
                    previous=p
                }
                selected?.let {day->values.firstOrNull {it.day==day}?.let {p->
                    val px=inset+markerX.coerceIn(0f,1f)*w;val py=y(p.value).toFloat()
                    drawCircle(s.color.copy(alpha=fade*.15f),9.dp.toPx(),Offset(px,py))
                    drawCircle(Color(colors.bg).copy(alpha=fade),5.dp.toPx(),Offset(px,py))
                    drawCircle(s.color.copy(alpha=fade),3.5.dp.toPx(),Offset(px,py))
                }}
            }

        }
        if(styledSeries.any {s->s.ceiling?.let {cap->s.values.any {it.value>cap && it.day.toEpochDay().toDouble() in first..last}}==true})
            Text("△ Above chart scale · select for full value",style=LocalDeltaType.current.bodySmall,color=Color(colors.textDim))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(LocalDate.ofEpochDay(shownFirst).format(java.time.format.DateTimeFormatter.ofPattern("d MMM")),style=LocalDeltaType.current.bodySmall)
            Text((if(LocalDate.ofEpochDay(shownLast)==LocalDate.now()) "Today · " else "")+LocalDate.ofEpochDay(shownLast).format(java.time.format.DateTimeFormatter.ofPattern("d MMM")),style=LocalDeltaType.current.bodySmall)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextAction("−",{
                val span=((last-first)*2).coerceAtMost(fullSpan)
                first=(last-span).coerceAtLeast(fullFirst);last=(first+span).coerceAtMost(fullLast);selectLatest()
            },Modifier.semantics {contentDescription="Zoom out"},enabled=last-first<fullSpan-.01)
            TextAction("Reset zoom",{first=fullFirst;last=fullLast;selectLatest()},enabled=last-first<fullSpan-.01)
            TextAction("+",{
                val span=((last-first)/2).coerceAtLeast(minOf(2.0,fullSpan))
                first=last-span;selectLatest()
            },Modifier.semantics {contentDescription="Zoom in"},enabled=last-first>2.01)
        }
            Column(Modifier.graphicsLayer {alpha=fade},verticalArrangement=Arrangement.spacedBy(4.dp)) {
                selected?.let {day->
                    Text(day.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),style=LocalDeltaType.current.bodyStrong)
                    styledSeries.forEach {s->s.values.firstOrNull {it.day==day}?.let {Text("${s.label} · ${s.format(it.value)}",style=LocalDeltaType.current.bodySmall,color=s.color)}}
                }
            }
    }
}
