package io.github.ebziboi.delta.nutrition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.ebziboi.delta.data.ChartStyle
import io.github.ebziboi.delta.ui.*
import java.time.LocalDate
import kotlin.math.roundToInt

internal fun macroWeek(entries:List<DiaryEntry>,end:LocalDate):Map<Nutrient,List<RecordedValue>> = Nutrient.macros.associateWith {n->
    (6L downTo 0L).mapNotNull {back->val day=end.minusDays(back);val rows=entries.filter {it.day==day};val total=rows.total(n)
        total.takeIf {rows.isNotEmpty() && it.reported>0}?.let {RecordedValue(day,it.amount,partial=it.partial)}
    }
}
/**
 * Seven days of protein, carbohydrate and fat ending at [end]. Tapping a day selects it for the whole
 * diary; a horizontal swipe moves a week. Double-tap cycles dots, trend and connected lines.
 */
@Composable internal fun DiaryMacroChart(state:NutritionState,end:LocalDate,selected:LocalDate,onSelect:(LocalDate)->Unit,onWeek:(Int)->Unit) {
    val c=LocalDeltaColors.current;val haptic=LocalHapticFeedback.current;val motion=LocalMotion.current
    val profile=rememberChartController("diary-macros",ChartStyle.DOTS)
    val values=remember(state.entries,end) {macroWeek(state.entries,end)}
    val colours=macroColours().take(3)
    val cap=(values.values.flatten().maxOfOrNull {it.value} ?: 1.0).coerceAtLeast(1.0)*1.15
    val today=LocalDate.now()
    val first=end.minusDays(6)
    val focus by animateFloatAsState((selected.toEpochDay()-first.toEpochDay()).toFloat().coerceIn(0f,6f),if(motion) spring(.8f,520f) else snap(),label="diary-day")
    val currentEnd by rememberUpdatedState(end);val select by rememberUpdatedState(onSelect);val week by rememberUpdatedState(onWeek)
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            listOf("Protein","Carbs","Fat").forEachIndexed {i,label->
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    Canvas(Modifier.size(7.dp)) {drawCircle(colours[i])}
                    Text(label,style=LocalDeltaType.current.label,color=Color(c.textDim))
                }
            }
            Spacer(Modifier.weight(1f))
            Text("g / day",style=LocalDeltaType.current.label,color=Color(c.textDim))
        }
        Canvas(Modifier.fillMaxWidth().height(112.dp).semantics {
            contentDescription="Seven-day macro graph"
            stateDescription=values.entries.joinToString {"${it.key.label}: ${it.value.size} recorded days, ${it.value.count {p->p.partial}} partial"}+"; ${profile.mode.label}"+"; Selected $selected; "+Nutrient.macros.joinToString {n->"${n.label}: "+(values[n]?.firstOrNull {it.day==selected}?.let {(if(it.partial) "at least " else "")+nutrientText(it.value,n)} ?: "Unknown")}
            customActions=listOf(CustomAccessibilityAction("Change chart style") {profile.cycle();true},
                CustomAccessibilityAction("Previous week") {week(-1);true},CustomAccessibilityAction("Next week") {week(1);true})
        }.pointerInput(Unit) {
            detectTapGestures(onDoubleTap={profile.cycle();haptic.performHapticFeedback(HapticFeedbackType.LongPress)},onTap={p->
                val inset=14.dp.toPx();val index=((p.x-inset)/(size.width-2*inset)*6).roundToInt().coerceIn(0,6)
                val day=currentEnd.minusDays(6-index.toLong())
                if(day<=LocalDate.now()) {select(day);haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
            })
        }.pointerInput(Unit) {
            var travel=0f
            detectHorizontalDragGestures(onDragStart={travel=0f},onDragEnd={
                if(kotlin.math.abs(travel)>48.dp.toPx()) {week(if(travel<0) 1 else -1);haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
            }) {change,amount->travel+=amount;change.consume()}
        }) {
            val inset=14.dp.toPx();val w=size.width-2*inset;val top=6.dp.toPx();val h=size.height-4.dp.toPx()
            fun baseX(d:LocalDate)=inset+(d.toEpochDay()-first.toEpochDay())/6f*w
            fun y(v:Double)=h-(v/cap*(h-top)).toFloat()
            // Selected-day column
            val band=w/6f*.82f;val cx=inset+focus/6f*w
            drawRoundRect(Color(c.text).copy(alpha=.07f),Offset(cx-band/2,0f),androidx.compose.ui.geometry.Size(band,size.height),androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
            for(g in 1..3) {val gy=h-(h-top)*g/4f;drawLine(Color(c.text).copy(alpha=.05f),Offset(inset,gy),Offset(size.width-inset,gy),1.dp.toPx())}
            Nutrient.macros.forEachIndexed {i,n->
                val dots=values[n].orEmpty();val colour=colours[i]
                fun x(d:LocalDate)=baseX(d)+(i-1)*3.dp.toPx()
                if(profile.mode!=ChartStyle.DOTS && dots.size>1) {
                    val path=Path()
                    if(profile.mode==ChartStyle.TREND) monotoneSpline(dots.map {SplinePoint(x(it.day),y(it.value))}).forEachIndexed {j,s->if(j==0) path.moveTo(s.first.x,s.first.y);path.cubicTo(s.c1.x,s.c1.y,s.c2.x,s.c2.y,s.end.x,s.end.y)}
                    else dots.forEachIndexed {j,p->if(j==0) path.moveTo(x(p.day),y(p.value)) else path.lineTo(x(p.day),y(p.value))}
                    drawPath(path,colour.copy(alpha=.65f),style=androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx(),cap=StrokeCap.Round))
                }
                dots.forEach {p->
                    val active=p.day==selected
                    val radius=if(active) 4.dp.toPx() else 2.7.dp.toPx();val at=Offset(x(p.day),y(p.value))
                    if(active) drawCircle(colour.copy(alpha=.22f),8.dp.toPx(),at)
                    if(p.partial) drawCircle(colour,radius,at,style=androidx.compose.ui.graphics.drawscope.Stroke(1.3.dp.toPx())) else drawCircle(colour.copy(alpha=if(active) 1f else .8f),radius,at)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=2.dp)) {
            (6L downTo 0L).forEach {back->
                val d=end.minusDays(back);val active=d==selected
                Column(Modifier.weight(1f),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW,java.util.Locale.getDefault()),style=LocalDeltaType.current.label,color=Color(if(active) c.accent else c.textDim))
                    Text(d.dayOfMonth.toString(),style=if(active) LocalDeltaType.current.bodyStrong else LocalDeltaType.current.bodySmall,color=Color(if(active) c.text else if(d>today) c.textFaint else c.textDim))
                }
            }
        }
    }
}
