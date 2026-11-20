package io.github.ebziboi.delta.nutrition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.ui.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

private fun kcal(v:Double)=java.text.DecimalFormat("#,##0").format(Math.round(abs(v)))
private fun signedKcal(v:Double)=(if(v<0) "−" else if(v>0) "+" else "")+kcal(v)

private enum class BalanceView(val label:String) {DAILY("Each day"),TOTAL("Running total")}

/** A gridline step that gives three to five lines. */
private fun niceStep(span:Double)=listOf(100.0,200.0,250.0,500.0,1000.0,2000.0,2500.0,5000.0,10000.0,20000.0).firstOrNull {span/it<=5} ?: 50000.0

internal fun DrawScope.label(text:String,x:Float,y:Float,color:Color,size:Float,alignRight:Boolean=false,center:Boolean=false) {
    val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color=color.toArgb();textSize=size
        textAlign=if(center) android.graphics.Paint.Align.CENTER else if(alignRight) android.graphics.Paint.Align.RIGHT else android.graphics.Paint.Align.LEFT
    }
    drawContext.canvas.nativeCanvas.drawText(text,x,y,paint)
}

/** Colour for a day: on budget, over budget but still under maintenance, or over both. */
@Composable internal fun dayColour(d:DayBudget):Color {
    val c=LocalDeltaColors.current
    val v=d.vsMaintenance ?: 0.0
    return Color(when {
        d.onBudget -> c.good
        d.planned< -50 && v<0 -> c.accent
        d.planned>50 && (d.vsBudget ?: 0.0)<0 -> c.accent
        else -> c.warn
    })
}

/**
 * Deficit and surplus against each day's own maintenance, with that day's budget marked. Tap or slide
 * to inspect a day. The running total shows what the days add up to, in kcal and in body weight.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun EnergyBalanceSection(all:List<DayBudget>,weights:List<WeightEntry>,unit:WeightUnit,onRecalculate:()->Unit) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current;val haptic=LocalHapticFeedback.current;val density=LocalDensity.current
    var range by rememberSaveable {mutableStateOf(14)}
    var view by rememberSaveable {mutableStateOf(BalanceView.DAILY)}
    val days=remember(all,range) {all.takeLast(range)}
    if(days.isEmpty()) return
    var selectedDay by rememberSaveable {mutableLongStateOf(Long.MIN_VALUE)}
    val selected=days.firstOrNull {it.day.toEpochDay()==selectedDay} ?: days.lastOrNull {it.counts} ?: days.last()
    val summary=remember(days,weights) {EnergyHistory.summary(days,weights)}
    val colours=days.map {dayColour(it)}
    val running=remember(days) {
        var actual=0.0;var plan=0.0
        days.map {d->if(d.counts) {actual+=d.vsMaintenance!!;plan+=d.planned};actual to plan}
    }

    ContentSection {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Caption("Balance",Modifier.weight(1f))
            Text(if(summary.totalVsMaintenance<=0) "deficit" else "surplus",style=type.label,color=Color(c.textDim))
        }
        Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            ValueWithUnit(signedKcal(summary.totalVsMaintenance),"kcal",style=type.numberLarge)
            Text("≈ ${Units.signed(summary.expectedKg,unit)}",style=type.number,color=Color(if(summary.totalVsMaintenance<=0) c.good else c.warn),modifier=Modifier.padding(bottom=2.dp))
        }
        Text(if(summary.countedDays==0) "No fully logged days in this range yet." else
            "Over ${summary.countedDays} fully logged ${if(summary.countedDays==1) "day" else "days"} against each day's maintenance"+
                (summary.actualKg?.let {" · scale trend ${Units.signed(it,unit)}"} ?: ""),style=type.bodySmall,color=Color(c.textDim))
        SegmentedControl(listOf(7,14,30,90),range,{when(it) {7->"1W";14->"2W";30->"1M";else->"3M"}}) {range=it}
        SegmentedControl(BalanceView.entries,view,{it.label}) {view=it}

        val currentDays by rememberUpdatedState(days)
        fun pick(x:Float,width:Float,inset:Float,right:Float) {
            val list=currentDays;val slot=(width-inset-right)/list.size
            val d=list[((x-inset)/slot).toInt().coerceIn(0,list.size-1)]
            if(d.day.toEpochDay()!=selectedDay) {selectedDay=d.day.toEpochDay();haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
        }
        val faint=Color(c.textFaint);val text=Color(c.text);val accent=Color(c.accent);val good=Color(c.good);val warn=Color(c.warn)
        Canvas(Modifier.fillMaxWidth().height(190.dp).semantics {
            contentDescription="Energy balance chart"
            stateDescription="${summary.countedDays} logged days, total ${signedKcal(summary.totalVsMaintenance)} kcal. Selected ${selected.day}: "+
                (selected.intakeKcal?.let {"ate ${kcal(it)}, budget ${kcal(selected.budgetKcal)}, maintenance ${kcal(selected.maintenanceKcal)}"} ?: "nothing logged")
        }.pointerInput(Unit) {
            val inset=4.dp.toPx();val right=40.dp.toPx()
            detectTapGestures {p->pick(p.x,size.width.toFloat(),inset,right)}
        }.pointerInput(Unit) {
            val inset=4.dp.toPx();val right=40.dp.toPx()
            detectHorizontalDragGestures {change,_->change.consume();pick(change.position.x,size.width.toFloat(),inset,right)}
        }) {
            val inset=4.dp.toPx();val right=40.dp.toPx();val top=8.dp.toPx();val bottom=size.height-6.dp.toPx()
            val slot=(size.width-inset-right)/days.size
            fun cx(i:Int)=inset+slot*(i+.5f)
            val values:List<Double?>;val plans:List<Double?>
            if(view==BalanceView.DAILY) {values=days.map {d->d.vsMaintenance?.takeIf {d.complete || d.isToday}};plans=days.map {it.planned}}
            else {values=running.mapIndexed {i,r->if(days.take(i+1).any {it.counts}) r.first else null};plans=running.mapIndexed {i,r->if(days.take(i+1).any {it.counts}) r.second else null}}
            val known=(values+plans).filterNotNull()
            var hi=maxOf(known.maxOrNull() ?: 0.0,200.0);var lo=minOf(known.minOrNull() ?: 0.0,-200.0)
            val step=if(view==BalanceView.TOTAL) (listOf(.1,.2,.25,.5,1.0,2.0,2.5,5.0,10.0).firstOrNull {(hi-lo)/EnergyMath.KCAL_PER_KG/it<=5} ?: 20.0)*EnergyMath.KCAL_PER_KG
                else niceStep(hi-lo)
            hi=ceil(hi*1.08/step)*step;lo=floor(lo*1.08/step)*step
            fun y(v:Double)=(top+(hi-v)/(hi-lo)*(bottom-top)).toFloat()
            val textSize=with(density) {10.dp.toPx()}

            // Selected-day column
            val si=days.indexOf(selected)
            if(si>=0) drawRoundRect(text.copy(alpha=.07f),Offset(cx(si)-maxOf(slot*.5f,5.dp.toPx()),0f),Size(maxOf(slot,10.dp.toPx()),size.height),CornerRadius(8.dp.toPx()))
            // Gridlines with kcal (and body weight on the running total) on the right.
            var g=lo
            while(g<=hi+.1) {
                val gy=y(g)
                drawLine(text.copy(alpha=if(g==0.0) .28f else .06f),Offset(inset,gy),Offset(size.width-right,gy),(if(g==0.0) 1.4f else 1f)*density.density)
                val tag=if(view==BalanceView.TOTAL && g!=0.0) Units.signed(g/EnergyMath.KCAL_PER_KG,unit).replace(" ","") else signedKcal(g)
                label(tag,size.width,gy+textSize*.35f,faint,textSize,alignRight=true)
                g+=step
            }
            if(view==BalanceView.DAILY) {
                val barW=(slot*.62f).coerceIn(1.5.dp.toPx(),18.dp.toPx())
                days.forEachIndexed {i,d->
                    if(d.intakeKcal==null) {drawCircle(faint.copy(alpha=.5f),1.6.dp.toPx(),Offset(cx(i),y(0.0)));return@forEachIndexed}
                    // Partly logged: a short outlined stub, since the real figure isn't known.
                    if(!d.complete && !d.isToday) {
                        drawRoundRect(faint,Offset(cx(i)-barW/2,y(0.0)+2.dp.toPx()),Size(barW,12.dp.toPx()),CornerRadius(minOf(barW/2,3.dp.toPx())),
                            style=Stroke(1.2.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(),2.dp.toPx()))))
                        return@forEachIndexed
                    }
                    val v=d.vsMaintenance!!
                    val y0=y(0.0);val y1=y(v)
                    val topLeft=Offset(cx(i)-barW/2,minOf(y0,y1));val sz=Size(barW,maxOf(abs(y1-y0),1.5.dp.toPx()))
                    val r=CornerRadius(minOf(barW/2,3.dp.toPx()))
                    when {
                        d.isToday -> drawRoundRect(colours[i].copy(alpha=.45f),topLeft,sz,r)
                        else -> drawRoundRect(colours[i].copy(alpha=if(i==si) 1f else .85f),topLeft,sz,r)
                    }
                }
                // Each day's budget as a dashed step line.
                val path=Path()
                days.forEachIndexed {i,d->val py=y(d.planned);val x0=inset+slot*i;if(i==0) path.moveTo(x0,py) else path.lineTo(x0,py);path.lineTo(x0+slot,py)}
                drawPath(path,text.copy(alpha=.6f),style=Stroke(1.3.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),4.dp.toPx())),cap=StrokeCap.Round))
            } else {
                val first=values.indexOfFirst {it!=null}
                if(first>=0) {
                    val line=Path();val area=Path();val planLine=Path()
                    for(i in first until days.size) {
                        val v=values[i]!!;val p=plans[i]!!;val x=cx(i)
                        if(i==first) {line.moveTo(x,y(v));area.moveTo(x,y(0.0));area.lineTo(x,y(v));planLine.moveTo(x,y(p))} else {line.lineTo(x,y(v));area.lineTo(x,y(v));planLine.lineTo(x,y(p))}
                    }
                    area.lineTo(cx(days.size-1),y(0.0));area.close()
                    val tone=if((values.last() ?: 0.0)<=0) good else warn
                    drawPath(area,tone.copy(alpha=.16f))
                    drawPath(planLine,text.copy(alpha=.55f),style=Stroke(1.3.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),4.dp.toPx()))))
                    drawPath(line,tone,style=Stroke(2.2.dp.toPx(),cap=StrokeCap.Round))
                    if(si>=first) {val v=values[si]!!;drawCircle(tone.copy(alpha=.25f),8.dp.toPx(),Offset(cx(si),y(v)));drawCircle(tone,4.dp.toPx(),Offset(cx(si),y(v)))}
                }
            }
        }
        // Dates under the chart
        Row(Modifier.fillMaxWidth().padding(end=40.dp)) {
            if(days.size<=14) days.forEach {d->
                Text(if(days.size<=7) d.day.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW,java.util.Locale.getDefault()) else d.day.dayOfMonth.toString(),
                    Modifier.weight(1f),style=type.label,color=Color(if(d==selected) c.accent else c.textDim),textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=1)
            } else {
                val f=DateTimeFormatter.ofPattern("d MMM")
                Text(days.first().day.format(f),style=type.label,color=Color(c.textDim));Spacer(Modifier.weight(1f))
                Text(days[days.size/2].day.format(f),style=type.label,color=Color(c.textDim));Spacer(Modifier.weight(1f))
                Text(days.last().day.format(f),style=type.label,color=Color(c.textDim))
            }
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            @Composable fun swatch(t:String,col:Color,dashed:Boolean=false,line:Boolean=false) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    if(line || dashed) Canvas(Modifier.size(16.dp,7.dp)) {
                        drawLine(col,Offset(0f,size.height/2),Offset(size.width,size.height/2),1.6.dp.toPx(),
                            pathEffect=if(dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),3.dp.toPx())) else null,cap=StrokeCap.Round)
                    } else Box(Modifier.size(7.dp).clip(CircleShape).background(col))
                    Text(t,style=type.label,color=Color(c.textDim),maxLines=1)
                }
            }
            if(view==BalanceView.DAILY) {
                swatch("On budget",good);swatch("Over budget",accent);swatch("Over maintenance",warn);swatch("Budget",text.copy(alpha=.6f),dashed=true)
            } else {
                swatch("Your total",if(summary.totalVsMaintenance<=0) good else warn,line=true);swatch("If on budget every day",text.copy(alpha=.6f),dashed=true)
            }
        }
        Text(if(view==BalanceView.DAILY) "Bars show each day against its own maintenance (0): below the line is a deficit."
            else "What the fully logged days add up to. 7,700 kcal is about 1 kg of body weight.",style=type.label,color=Color(c.textDim))
        DayDetail(selected,unit)
        if(summary.countedDays>0) {
            summary.averageVsBudget?.let {NutritionRow("Average vs budget",signedKcal(it)+" kcal",if(it<=0) "Under budget per logged day" else "Over budget per logged day")}
            NutritionRow("On budget","${summary.onBudgetDays} of ${summary.countedDays}",if(summary.streak>=2) "${summary.streak} days in a row" else "Fully logged days")
        }
        val skipped=days.count {!it.isToday && it.intakeKcal!=null && !it.complete}
        if(skipped>0) Text("$skipped partly logged ${if(skipped==1) "day is" else "days are"} shown as dashed stubs and left out of the totals.",style=type.label,color=Color(c.textDim))
        if(days.any {it.estimated}) {
            Text("Days before the budget was set up are estimated with today's profile and goal. Later days keep the plan they had.",style=type.label,color=Color(c.textDim))
        }
        TextAction("Recalculate past days with current settings",onRecalculate)
    }
}

@Composable private fun DayDetail(d:DayBudget,unit:WeightUnit) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(Radii.control)).background(Color(c.text).copy(alpha=.05f)).padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(if(d.isToday) "Today" else d.day.format(DateTimeFormatter.ofPattern("EEE d MMM")),style=type.bodyStrong,modifier=Modifier.weight(1f))
            d.vsBudget?.let {v->
                Text(if(abs(v)<5) "On budget" else "${kcal(v)} kcal ${if(v<0) (if(d.isToday) "left" else "under") else "over"}",style=type.bodyStrong,color=if(d.complete) dayColour(d) else Color(c.textDim))
            }
        }
        val cells=listOfNotNull(
            "Eaten" to (d.intakeKcal?.let {kcal(it)} ?: "—"),
            "Budget" to kcal(d.budgetKcal),
            "Maintenance" to kcal(d.maintenanceKcal),
            d.activityKcal?.let {"Activity" to "+${kcal(it)}"},
        )
        Row(Modifier.fillMaxWidth()) {
            cells.forEach {(t,v)->
                Column(Modifier.weight(1f)) {Text(v,style=type.number);Text(t,style=type.label,color=Color(c.textDim),maxLines=1)}
            }
        }
        val notes=buildList {
            d.vsMaintenance?.takeIf {d.complete}?.let {add("${if(it<=0) "Deficit" else "Surplus"} ${kcal(it)} kcal vs maintenance ≈ ${Units.signed(it/EnergyMath.KCAL_PER_KG,unit)}")}
            if(d.activityKcal!=null) add(if(d.activityMeasured) "Activity from Health Connect${d.steps?.takeIf {it>0}?.let {" · ${java.text.DecimalFormat("#,##0").format(it)} steps"} ?: ""}" else "No activity recorded, so your usual amount is used")
            if(d.intakeKcal==null) add("Nothing logged") else if(!d.complete && !d.isToday) add("Not fully logged, so it's left out of totals")
            if(d.isToday) add("In progress")
            if(d.estimated) add("Plan estimated with today's settings")
        }
        notes.forEach {Text(it,style=type.label,color=Color(c.textDim))}
    }
}

/** BMI with its WHO group on a scale, the healthy range for your height, milestones, target and body fat. */
@Composable
internal fun BodyReport(b:EnergyBalance,profile:EnergyProfile,targetKg:Double?,bodyFat:BodyFatEntry?,unit:WeightUnit) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current;val density=LocalDensity.current
    ContentSection {
        Caption("BMI & weight group")
        val height=b.heightCm
        val bmi=b.bmi
        if(height==null || bmi==null) {
            NutritionRow("Weight",Units.weight(b.currentKg,unit),"7-day average")
            Text("Add your height in Body & energy to see your BMI, weight group and healthy weight range.",style=type.bodySmall,color=Color(c.textDim))
        } else {
            val group=BmiGroup.of(bmi)
            val tone=Color(if(group==BmiGroup.HEALTHY) c.good else c.warn)
            Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(String.format(java.util.Locale.UK,"%.1f",bmi),style=type.numberLarge)
                Column(Modifier.padding(bottom=3.dp)) {
                    Text(listOfNotNull(group.label,group.alias).joinToString(" · "),style=type.bodyStrong,color=tone)
                    Text("${Units.weight(b.currentKg,unit)} at ${Math.round(height)} cm",style=type.label,color=Color(c.textDim))
                }
            }
            BmiScale(bmi,targetKg?.let {EnergyMath.bmi(it,height)})
            Text(group.summary,style=type.bodySmall)
            BmiLadder(height,b.currentKg,targetKg,unit)
            val healthy=BodyMetrics.healthyRange(height)
            NutritionRow("Healthy weight for your height","${Units.weightValue(healthy.start,unit)}–${Units.weight(healthy.endInclusive,unit)}","BMI 18.5 to 24.9")
            val kg=b.currentKg
            when {
                kg>healthy.endInclusive -> NutritionRow("To the healthy range",Units.amount(kg-healthy.endInclusive,unit),"${Math.round((kg-healthy.endInclusive)/kg*100)}% of your weight")
                kg<healthy.start -> NutritionRow("To the healthy range",Units.amount(healthy.start-kg,unit),"to gain")
                else -> NutritionRow("Within the healthy range",Units.amount(kg-healthy.start,unit),"above its lower end")
            }
            // The next group boundary on the way to the healthy range.
            if(group.ordinal>BmiGroup.OVER.ordinal) {
                val next=BmiGroup.entries[group.ordinal-1]
                val at=BodyMetrics.weightAt(group.from,height)-.1
                NutritionRow("Next milestone",Units.amount(kg-at,unit),"${next.label} below ${Units.weight(at+.1,unit)}")
            }
            if(group.ordinal>=BmiGroup.OVER.ordinal) NutritionRow("A first 5%",Units.weight(kg*.95,unit),"Losing ${Units.amount(kg*.05,unit)} already brings measurable health benefits")
            targetKg?.let {t->
                val tb=EnergyMath.bmi(t,height)
                NutritionRow("Your target",Units.weight(t,unit),"BMI ${String.format(java.util.Locale.UK,"%.1f",tb)} · ${BmiGroup.of(tb).label}")
            }
        }
        bodyFat?.let {f->
            val fat=f.percent
            val fg=BodyMetrics.fatGroup(fat,profile.sex)
            NutritionRow("Body fat",BodyFat.format(fat),fg?.let {"${it.label} for ${profile.sex.label.lowercase()} adults (${BodyMetrics.fatRange(it,profile.sex)})"} ?: "Set your sex in Body & energy to see its group")
            NutritionRow("Lean mass",Units.weight(b.currentKg*(1-fat/100),unit),"Fat mass ${Units.amount(b.currentKg*fat/100,unit)}")
        }
        BodyMetrics.notes(b.age).forEach {Text(it,style=type.label,color=Color(c.textDim))}
    }
}

@Composable
internal fun AdviceSection(advice:List<Advice>) {
    if(advice.isEmpty()) return
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    ContentSection {
        Caption("Suggestions")
        advice.forEach {a->
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.padding(top=6.dp).size(8.dp).clip(CircleShape).background(Color(when(a.tone) {AdviceTone.GOOD->c.good;AdviceTone.INFO->c.accent;AdviceTone.WARN->c.warn})))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(a.title,style=type.bodyStrong)
                    Text(a.detail,style=type.bodySmall,color=Color(c.textDim))
                }
            }
        }
        Text("Based on your own logs and Health Connect data. General guidance, not medical advice.",style=type.label,color=Color(c.textFaint))
    }
}

/** Averages for the suggestions from the last two weeks. */
internal fun adviceInputs(b:EnergyBalance,profile:EnergyProfile,days:List<DayBudget>,entries:List<DiaryEntry>,health:EnergyHealthData,targetKg:Double?,weights:List<WeightEntry>,unit:WeightUnit):AdviceInputs {
    val recent=days.takeLast(14)
    val full=recent.filter {it.counts}.map {it.day}.toSet()
    fun average(n:Nutrient)=full.mapNotNull {d->entries.filter {it.day==d}.total(n).takeIf {it.reported>0 && !it.partial}?.amount}.takeIf {it.size>=4}?.average()
    val today=LocalDate.now()
    val steps=health.activity.filter {it.day<today && it.day>=today.minusDays(14)}.mapNotNull {it.steps?.takeIf {s->s>0}}.takeIf {it.size>=5}?.average()
    val since=today.minusDays(14).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val workouts=if(health.canReadWorkouts) health.workouts.count {it.start>=since} else null
    return AdviceInputs(b,profile,recent,EnergyHistory.summary(recent,weights),targetKg,average(Nutrient.PROTEIN),average(Nutrient.FIBRE),steps,workouts,
        weight={Units.weight(it,unit)},amount={Units.amount(it,unit)})
}
