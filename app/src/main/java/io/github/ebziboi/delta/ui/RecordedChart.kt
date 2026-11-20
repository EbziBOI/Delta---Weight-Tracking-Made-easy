package io.github.ebziboi.delta.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.ebziboi.delta.data.ChartStyle
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.*

internal data class RecordedSeries(val id:String,val label:String,val color:Color,val values:List<RecordedValue>,
    val format:(Double)->String,val bars:Boolean=false,val ceiling:Double?=null,val typical:Double?=null,val derived:Boolean=false)
internal fun latestRecordedDay(series:List<RecordedSeries>,first:Long,last:Long):LocalDate?=series.flatMap {it.values}.filter {it.day.toEpochDay() in first..last}.maxOfOrNull {it.day}

@Composable internal fun ChartHeading(title:String,controller:ChartController,periodPicker:Boolean=true) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,style=LocalDeltaType.current.title,modifier=Modifier.weight(1f))
        if(periodPicker) ChartPeriodControl(controller.period,controller::choosePeriod)
    }
}
@Composable internal fun ChartWindows(controller:ChartController) {
    SegmentedControl(controller.period.windows,controller.window,{it},Modifier.semantics {contentDescription="Chart time window"}) {controller.chooseWindow(it)}
}

/** One timeline implementation for weight, body fat, nutrition and derived rhythm data. */
@Composable internal fun RecordedChart(series:List<RecordedSeries>,start:LocalDate,end:LocalDate,description:String,
    onSelected:(LocalDate?)->Unit={},selectedDay:LocalDate?=null,onDoubleTap:(()->Unit)?=null,trend:Boolean=false,
    connectDots:Boolean=true,periodPicker:Boolean=true,controller:ChartController?=null,header:String?=null,
    controls:Boolean=true,journeyStart:LocalDate?=null,weekStartsOn:Int=0,fixedWindow:Boolean=false,
    onWindowEnd:((LocalDate)->Unit)?=null,minimumYSpan:Double=1.0,target:Double?=null,historyStart:LocalDate?=null,readingNavigation:Boolean=false,
    markers:Set<LocalDate> = emptySet()) {
    val profile=controller ?: rememberChartController(description,if(trend) ChartStyle.TREND else if(connectDots) ChartStyle.CONNECTED else ChartStyle.DOTS)
    val fixed=fixedWindow || !periodPicker
    val averaging=if(fixed) ChartPeriod.DAILY else profile.period
    val colors=LocalDeltaColors.current;val motion=LocalMotion.current;val haptics=LocalHapticFeedback.current;val scope=rememberCoroutineScope()
    val claim=LocalCardGestureClaim.current;val gestureToken=remember {Any()}
    val raw=remember(series,averaging,weekStartsOn,end,fixed) {series.map {it.copy(values=aggregateReadings(it.values,averaging,weekStartsOn,if(fixed) LocalDate.now() else end))}}
    val styled=raw.map {it.copy(color=Color(io.github.ebziboi.delta.render.ThemeContrast.readable(it.color.toArgb(),colors.bg)))}
    // Fitted once per data change across the whole history, so panning never reshapes the line.
    val trends=remember(raw) {raw.map {s->smoothRecorded(s.values).associate {it.day to it.value}}}
    val oldest=historyStart ?: series.flatMap {it.values}.minOfOrNull {it.day} ?: start
    val nominalFirst=if(fixed) start else chartWindowStart(profile.window,end,journeyStart ?: start,oldest,averaging)
    val targetSpan=(end.toEpochDay()-nominalFirst.toEpochDay()).toFloat().coerceAtLeast(1f)
    val spanAnimation=remember(description) {Animatable(targetSpan)}
    var resizing by remember {mutableStateOf(false)}
    LaunchedEffect(targetSpan) {resizing=true;try {spanAnimation.animateTo(targetSpan,tween(if(motion) 320 else 0))} finally {resizing=false}}
    val span=spanAnimation.value.toDouble()
    val earliest=if(fixed) io.github.ebziboi.delta.data.MetricDetails.weekStart(oldest,weekStartsOn) else oldest
    val minFirst=minOf(earliest.toEpochDay().toDouble(),nominalFirst.toEpochDay().toDouble())
    val maxLast=if(fixed) io.github.ebziboi.delta.data.MetricDetails.weekStart(LocalDate.now(),weekStartsOn).plusDays(6).toEpochDay().toDouble() else end.toEpochDay().toDouble()
    val minLast=minOf(maxLast,minFirst+span)
    var right by rememberSaveable(description,nominalFirst,end,averaging,profile.revision) {mutableDoubleStateOf(end.toEpochDay().toDouble())}
    right=right.coerceIn(minLast,maxLast)
    val left=right-span
    var width by remember {mutableFloatStateOf(1f)}
    var selectedEpoch by rememberSaveable(description) {mutableStateOf<Long?>(null)}
    var snapping by remember {mutableStateOf(false)}
    val currentSeries by rememberUpdatedState(styled)
    val selectedCallback by rememberUpdatedState(onSelected)
    val windowCallback by rememberUpdatedState(onWindowEnd)
    val currentSpan by rememberUpdatedState(span);val currentMin by rememberUpdatedState(minLast);val currentMax by rememberUpdatedState(maxLast)
    val scroll=rememberScrollableState {delta->
        right=(right-delta/width.coerceAtLeast(1f)*currentSpan).coerceIn(currentMin,currentMax)
        delta
    }
    val moving=scroll.isScrollInProgress || snapping || resizing
    var scrolled by remember {mutableStateOf(false)}
    fun selectRightmost() {
        val chosen=currentSeries.flatMap {it.values}.filter {it.day.toEpochDay().toDouble() in (right-currentSpan)..right}.maxOfOrNull {it.day}
        selectedEpoch=chosen?.toEpochDay();selectedCallback(chosen)
        profile.visibleFirst=LocalDate.ofEpochDay(ceil(right-currentSpan).toLong());profile.visibleLast=LocalDate.ofEpochDay(floor(right).toLong())
    }
    LaunchedEffect(description,averaging,profile.window,profile.revision,start,end,raw.map {it.id to it.values}) {
        if(selectedDay==null) selectRightmost() else selectedEpoch=selectedDay.toEpochDay()
    }
    LaunchedEffect(selectedDay) {selectedDay?.let {selectedEpoch=it.toEpochDay()}}
    LaunchedEffect(scroll.isScrollInProgress,resizing) {
        if(scroll.isScrollInProgress) scrolled=true
        if(!scroll.isScrollInProgress && !resizing) {
            val next=if(fixed) end.toEpochDay()+round((right-end.toEpochDay())/7)*7 else right.roundToLong().toDouble()
            val targetRight=next.coerceIn(currentMin,currentMax)
            if(abs(targetRight-right)>.001) {
                snapping=true;val before=right
                try {animate(0f,1f,animationSpec=tween(if(motion) 180 else 0)) {v,_->right=before+(targetRight-before)*v}} finally {snapping=false}
            }
            if(selectedDay==null || abs(right-end.toEpochDay())>.1) selectRightmost()
            if(scrolled) {haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove);scrolled=false}
            if(fixed && abs(right-end.toEpochDay())>.1) windowCallback?.invoke(LocalDate.ofEpochDay(right.roundToLong()))
        }
    }
    val fade by animateFloatAsState(if(moving || selectedEpoch==null) 0f else 1f,tween(if(motion) 190 else 0),label="chart-selection-reveal")
    val marker by animateFloatAsState(((selectedEpoch?.toDouble() ?: right)-left).div(span).toFloat(),if(motion) spring(.85f,480f) else snap(),label="rightmost-reading")
    val mode=profile.mode
    var lineMode by remember {mutableStateOf(if(mode==ChartStyle.DOTS) ChartStyle.CONNECTED else mode)}
    LaunchedEffect(mode) {if(mode!=ChartStyle.DOTS) lineMode=mode}
    val lineOpacity by animateFloatAsState(if(mode==ChartStyle.DOTS) 0f else 1f,tween(if(motion) 220 else 0),label="chart-line")
    val smoothing by animateFloatAsState(if(mode==ChartStyle.TREND) 1f else 0f,tween(if(motion) 280 else 0),label="chart-smoothing")
    val firstShown=LocalDate.ofEpochDay(ceil(left).toLong());val lastShown=LocalDate.ofEpochDay(floor(right).toLong())
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(controls) {
            if(header!=null) ChartHeading(header,profile,periodPicker)
            else if(periodPicker) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {ChartPeriodControl(profile.period,profile::choosePeriod)}
            if(!fixed) ChartWindows(profile)
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            styled.forEach {s->
                val points=s.values.filter {it.day.toEpochDay().toDouble() in left..right}
                Text(s.label+if(points.isEmpty()) " · No data" else " · ${s.format(if(s.bars) 0.0 else points.minOf {it.value})} – ${s.format(s.ceiling ?: points.maxOf {it.value})}",style=LocalDeltaType.current.bodySmall,color=s.color)
            }
        }
        Canvas(Modifier.fillMaxWidth().height(210.dp).semantics {
            contentDescription=description
            stateDescription="${profile.period.label}; ${mode.label}; $firstShown to $lastShown; "+styled.joinToString {s->"${s.label}: ${s.values.count {it.day.toEpochDay().toDouble() in left..right}} ${if(s.derived) "computed" else "recorded"} periods"}+
                (selectedEpoch?.let {"; Selected ${LocalDate.ofEpochDay(it)}"} ?: "")
            onClick(label="Change chart style") {profile.cycle();true}
        }.scrollable(scroll,Orientation.Horizontal).pointerInput(description) {
            // Tap a dot to select it straight away (no double-tap delay); tap the same spot twice to change style;
            // hold and slide to scrub through readings. Any other movement is left to panning and page scrolling.
            var lastTap=0L;var lastTapAt=Offset.Zero
            fun pick(at:Offset,byPosition:Boolean):LocalDate? {
                val inset=8.dp.toPx();val w=(size.width-2*inset).coerceAtLeast(1f);val top=8.dp.toPx();val bottom=size.height-8.dp.toPx()
                val r=right;val l=r-currentSpan
                val points=currentSeries.flatMap {s->
                    val (low,high)=seriesScale(s,l,r,minimumYSpan)
                    s.values.filter {it.day.toEpochDay().toDouble() in l..r}.map {p->
                        val x=inset+((p.day.toEpochDay()-l)/currentSpan*w).toFloat()
                        val y=bottom-((p.value.coerceIn(low,high)-low)/(high-low).coerceAtLeast(.001)*(bottom-top)).toFloat()
                        p.day to Offset(x,y)
                    }
                }
                if(points.isEmpty()) return null
                val reach=28.dp.toPx()
                val near=if(byPosition) points.filter {(it.second-at).getDistance()<=reach}.minByOrNull {(it.second-at).getDistance()} else null
                return (near ?: points.minByOrNull {abs(it.second.x-at.x)})?.first
            }
            fun choose(day:LocalDate?,feedback:HapticFeedbackType) {
                if(day==null) return
                if(selectedEpoch!=day.toEpochDay()) haptics.performHapticFeedback(feedback)
                selectedEpoch=day.toEpochDay();selectedCallback(day)
            }
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val slop=viewConfiguration.touchSlop
                var released=down.position
                val outcome=withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var result=2
                    while(true) {
                        val event=awaitPointerEvent();val change=event.changes.firstOrNull {it.id==down.id}
                        if(change==null || event.changes.size>1) break
                        if(!change.pressed) {released=change.position;change.consume();result=1;break}
                        if(change.isConsumed || (change.position-down.position).getDistance()>slop) break
                    }
                    result
                }
                when(outcome) {
                    1->{
                        val now=down.uptimeMillis
                        if(now-lastTap<viewConfiguration.doubleTapTimeoutMillis && (released-lastTapAt).getDistance()<32.dp.toPx()) {
                            lastTap=0L;profile.cycle();haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else {
                            lastTap=now;lastTapAt=released
                            choose(pick(released,true),HapticFeedbackType.TextHandleMove)
                        }
                    }
                    null->{
                        // Held still: scrub. Consuming each move keeps the chart from panning and the page from scrolling.
                        claim(gestureToken,true)
                        try {
                            choose(pick(down.position,false),HapticFeedbackType.LongPress)
                            while(true) {
                                val event=awaitPointerEvent();val change=event.changes.firstOrNull {it.id==down.id} ?: break
                                change.consume()
                                if(!change.pressed) break
                                choose(pick(change.position,false),HapticFeedbackType.TextHandleMove)
                            }
                        } finally {claim(gestureToken,false)}
                    }
                    else->{}
                }
            }
        }) {
            width=size.width
            val inset=8.dp.toPx();val w=size.width-2*inset;val top=8.dp.toPx();val bottom=size.height-8.dp.toPx()
            fun x(day:LocalDate)=inset+((day.toEpochDay()-left)/span*w).toFloat()
            drawLine(Color(colors.outline).copy(alpha=.5f),Offset(inset,bottom),Offset(size.width-inset,bottom),1.dp.toPx())
            // Workouts from Health Connect: small ticks on the baseline.
            markers.filter {it.toEpochDay().toDouble() in left..right}.forEach {d->
                val mx=x(d);drawCircle(Color(colors.good).copy(alpha=.8f),2.4.dp.toPx(),Offset(mx,bottom-3.dp.toPx()))
            }
            clipRect(0f,0f,size.width,size.height) {
                if(fade>0) drawLine(Color(colors.textDim).copy(alpha=.65f*fade),Offset(inset+marker*w,top),Offset(inset+marker*w,bottom),1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(),4.dp.toPx())))
                styled.forEachIndexed {seriesIndex,s->
                    val visible=s.values.filter {it.day.toEpochDay().toDouble() in left..right}
                    val neighbours=s.values.filter {it.day.toEpochDay().toDouble() in (left-35)..(right+35)}
                    val (low,high)=seriesScale(s,left,right,minimumYSpan)
                    fun y(value:Double)=bottom-((value.coerceIn(low,high)-low)/(high-low).coerceAtLeast(.001)*(bottom-top)).toFloat()
                    if(target!=null && styled.size==1 && target in low..high) drawLine(s.color.copy(alpha=.3f),Offset(inset,y(target)),Offset(size.width-inset,y(target)),1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),4.dp.toPx())))
                    if(lineOpacity>0 && neighbours.size>1) {
                        val fitted=trends.getOrNull(seriesIndex).orEmpty()
                        val plot=neighbours.map {p->p.copy(value=p.value+((fitted[p.day] ?: p.value)-p.value)*smoothing)}
                        val path=Path()
                        if(lineMode==ChartStyle.TREND) {
                            monotoneSpline(plot.map {SplinePoint(x(it.day),y(it.value))}).forEachIndexed {i,c->
                                if(i==0) path.moveTo(c.first.x,c.first.y)
                                path.cubicTo(c.c1.x,c.c1.y,c.c2.x,c.c2.y,c.end.x,c.end.y)
                            }
                        } else plot.forEachIndexed {i,p->if(i==0) path.moveTo(x(p.day),y(p.value)) else path.lineTo(x(p.day),y(p.value))}
                        drawPath(path,s.color.copy(alpha=.9f*lineOpacity),style=androidx.compose.ui.graphics.drawscope.Stroke(2.2.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
                    }
                    val radius=minOf(2.8.dp.toPx(),(w/visible.size.coerceAtLeast(1)*.3f).coerceAtLeast(1.2.dp.toPx()))
                    visible.forEach {p->
                        val at=Offset(x(p.day),y(p.value))
                        if(p.value>high && s.bars) {
                            val triangle=Path().apply {moveTo(at.x,0f);lineTo(at.x-4.dp.toPx(),7.dp.toPx());lineTo(at.x+4.dp.toPx(),7.dp.toPx());close()}
                            drawPath(triangle,Color(0xFFF16F68))
                        } else drawCircle(s.color.copy(alpha=if(mode==ChartStyle.TREND) .45f else 1f),radius,at)
                    }
                    visible.firstOrNull {it.day.toEpochDay()==selectedEpoch}?.let {p->
                        val at=Offset(inset+marker*w,y(p.value))
                        drawCircle(s.color.copy(alpha=.17f*fade),9.dp.toPx(),at)
                        drawCircle(Color(colors.bg).copy(alpha=fade),5.dp.toPx(),at)
                        drawCircle(s.color.copy(alpha=fade),3.5.dp.toPx(),at)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(firstShown.format(DateTimeFormatter.ofPattern("d MMM")),style=LocalDeltaType.current.bodySmall)
            Text(lastShown.format(DateTimeFormatter.ofPattern("d MMM yyyy")),style=LocalDeltaType.current.bodySmall)
        }
        Column(Modifier.graphicsLayer {alpha=fade},verticalArrangement=Arrangement.spacedBy(5.dp)) {
            val selected=selectedEpoch?.let(LocalDate::ofEpochDay)
            selected?.let {day->
                val rows=styled.mapNotNull {s->s.values.firstOrNull {it.day==day}?.let {s to it}}
                val first=rows.minOfOrNull {it.second.first} ?: day;val last=rows.maxOfOrNull {it.second.last} ?: day
                val label=if(averaging==ChartPeriod.DAILY) day.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")) else
                    "${first.format(DateTimeFormatter.ofPattern("d MMM"))} – ${last.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"+
                        if(last==LocalDate.now()) (if(averaging==ChartPeriod.MONTHLY) " · month to date" else " · week to date") else ""
                Text(label,style=LocalDeltaType.current.bodyStrong)
                styled.forEach {s->
                    val p=s.values.firstOrNull {it.day==day}
                    Text("${s.label} · ${p?.let {s.format(it.value)} ?: "—"}"+(if(p!=null && averaging!=ChartPeriod.DAILY) " average · ${p.recordedDays} recorded ${if(p.recordedDays==1) "day" else "days"}" else "")+(if(p?.partial==true) " · partial labels" else ""),style=LocalDeltaType.current.bodySmall,color=s.color)
                }
            }
        }
        if(readingNavigation) {
            val dates=styled.flatMap {it.values}.map {it.day.toEpochDay()}.distinct().sorted()
            val index=dates.indexOf(selectedEpoch)
            fun navigate(next:Int) {
                val value=dates.getOrNull(next) ?: return
                if(value<left) right=(value+span).coerceIn(minLast,maxLast)
                else if(value>right) right=value.toDouble().coerceIn(minLast,maxLast)
                selectedEpoch=value;selectedCallback(LocalDate.ofEpochDay(value))
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(24.dp,Alignment.CenterHorizontally)) {
                TextAction("Previous",{navigate(index-1)},enabled=index>0)
                TextAction("Next",{navigate(index+1)},enabled=index>=0 && index<dates.lastIndex)
            }
        }
    }
}
/** The value range a series is drawn against for the visible window, shared by drawing and tap hit-testing. */
internal fun seriesScale(s:RecordedSeries,left:Double,right:Double,minimumYSpan:Double):Pair<Double,Double> {
    val visible=s.values.filter {it.day.toEpochDay().toDouble() in left..right}
    val rawLow=visible.minOfOrNull {it.value} ?: 0.0;val rawHigh=visible.maxOfOrNull {it.value} ?: 1.0
    val extent=(rawHigh-rawLow).coerceAtLeast(minimumYSpan)
    val low=if(s.bars) 0.0 else (rawLow+rawHigh-extent)/2-extent*.12
    val high=if(s.bars) (s.ceiling ?: rawHigh).coerceAtLeast(.001) else (rawLow+rawHigh+extent)/2+extent*.12
    return low to high
}

/**
 * Centred, time-aware trend: a Gaussian-weighted local linear fit around each reading.
 * Unlike a running average it does not lag behind recent readings, irregular gaps are weighted
 * by real time, and the ends follow the local slope instead of flattening. Each fitted value is
 * kept within the readings that contributed to it, so the line never invents peaks.
 */
internal fun smoothRecorded(values:List<RecordedValue>,strength:Double=1.0):List<RecordedValue> {
    val sorted=values.sortedBy {it.day}
    if(sorted.size<3) return sorted
    val x=DoubleArray(sorted.size) {sorted[it].day.toEpochDay().toDouble()}
    val gaps=(1 until x.size).map {x[it]-x[it-1]}.filter {it>0}.sorted()
    val typicalGap=(gaps.getOrNull(gaps.size/2) ?: 1.0).coerceAtLeast(1.0)
    val sigma=maxOf(4.5,typicalGap*2.5)*strength.coerceIn(.25,4.0)
    val reach=sigma*3;val twoSigmaSq=2*sigma*sigma
    var lo=0;var hi=0
    return sorted.mapIndexed {i,p->
        while(x[i]-x[lo]>reach) lo++
        if(hi<i) hi=i
        while(hi+1<x.size && x[hi+1]-x[i]<=reach) hi++
        var sw=0.0;var sd=0.0;var sy=0.0;var sdd=0.0;var sdy=0.0;var low=Double.MAX_VALUE;var high=-Double.MAX_VALUE
        for(j in lo..hi) {
            val d=x[j]-x[i];val w=exp(-d*d/twoSigmaSq);val v=sorted[j].value
            sw+=w;sd+=w*d;sy+=w*v;sdd+=w*d*d;sdy+=w*d*v
            if(w>.01) {low=minOf(low,v);high=maxOf(high,v)}
        }
        val det=sw*sdd-sd*sd
        val fit=if(det>1e-9*sw*sw) (sy*sdd-sd*sdy)/det else sy/sw
        p.copy(value=if(fit.isFinite()) fit.coerceIn(low,high) else p.value)
    }
}
