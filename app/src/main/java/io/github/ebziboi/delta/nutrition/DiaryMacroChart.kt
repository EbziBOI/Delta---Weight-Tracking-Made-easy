package io.github.ebziboi.delta.nutrition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
@Composable internal fun DiaryMacroChart(state:NutritionState,day:LocalDate) {
    val c=LocalDeltaColors.current;val density=LocalDensity.current;val haptic=LocalHapticFeedback.current
    val profile=rememberChartController("diary-macros",ChartStyle.DOTS)
    val values=remember(state.entries,day) {macroWeek(state.entries,day)}
    val daily=state.entries.filter {it.day==day}
    val line=with(density) {LocalDeltaType.current.bodySmall.lineHeight.toDp()}
    val oldHeight=line*3+24.dp+12.dp*Nutrient.macros.count {state.targets.target(it)!=null}+
        (line+7.dp)*Nutrient.macros.count {daily.total(it).partial}
    val colours=listOf(Color(c.accent),Color(c.good),Color(c.warn))
    val cap=(values.values.flatten().maxOfOrNull {it.value} ?: 1.0).coerceAtLeast(1.0)*1.12
    var selected by remember(day) {mutableStateOf<LocalDate?>(day)}
    Column(Modifier.fillMaxWidth().height(oldHeight.coerceAtLeast(94.dp)),verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            listOf("Protein","Carbs","Fat").forEachIndexed {i,label->Text(label,style=LocalDeltaType.current.bodySmall,color=colours[i])}
        }
        Canvas(Modifier.fillMaxWidth().weight(1f).semantics {
            contentDescription="Seven-day macro graph"
            stateDescription=values.entries.joinToString {"${it.key.label}: ${it.value.size} recorded days, ${it.value.count {p->p.partial}} partial"}+"; ${profile.mode.label}"+(selected?.let {d->"; Selected $d; "+Nutrient.macros.joinToString {n->"${n.label}: "+(values[n]?.firstOrNull {it.day==d}?.let {(if(it.partial) "at least " else "")+nutrientText(it.value,n)} ?: "Unknown")}} ?: "")
            customActions=listOf(CustomAccessibilityAction("Change chart style") {profile.cycle();true})
        }.pointerInput(day) {
            detectTapGestures(onDoubleTap={profile.cycle();haptic.performHapticFeedback(HapticFeedbackType.LongPress)},onTap={p->
                val index=(p.x/size.width*6).roundToInt().coerceIn(0,6);selected=day.minusDays(6-index.toLong());haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            })
        }) {
            val inset=8.dp.toPx();val w=size.width-2*inset;val h=size.height-6.dp.toPx()
            fun baseX(d:LocalDate)=inset+(d.toEpochDay()-day.minusDays(6).toEpochDay())/6f*w
            fun y(v:Double)=h-(v/cap*h).toFloat()
            selected?.let {d->drawLine(Color(c.textDim).copy(alpha=.25f),Offset(baseX(d),0f),Offset(baseX(d),h),1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(),3.dp.toPx())))}
            Nutrient.macros.forEachIndexed {i,n->
                val dots=values[n].orEmpty();val colour=colours[i]
                fun x(d:LocalDate)=baseX(d)+(i-1)*3.dp.toPx()
                if(profile.mode!=ChartStyle.DOTS && dots.size>1) {
                    val path=Path();val plotted=if(profile.mode==ChartStyle.TREND) smoothRecorded(dots) else dots
                    if(profile.mode==ChartStyle.TREND) monotoneSpline(plotted.map {SplinePoint(x(it.day),y(it.value))}).forEachIndexed {j,s->if(j==0) path.moveTo(s.first.x,s.first.y);path.cubicTo(s.c1.x,s.c1.y,s.c2.x,s.c2.y,s.end.x,s.end.y)}
                    else plotted.forEachIndexed {j,p->if(j==0) path.moveTo(x(p.day),y(p.value)) else path.lineTo(x(p.day),y(p.value))}
                    drawPath(path,colour.copy(alpha=.65f),style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(),cap=StrokeCap.Round))
                }
                dots.forEach {p->val radius=if(p.day==selected) 3.5.dp.toPx() else 2.5.dp.toPx();val at=Offset(x(p.day),y(p.value));if(p.partial) drawCircle(colour,radius,at,style=androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx())) else drawCircle(colour,radius,at)}
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            (6L downTo 0L).forEach {back->Text(day.minusDays(back).dayOfWeek.name.take(1),style=LocalDeltaType.current.label,color=Color(c.textDim))}
        }
    }
}
