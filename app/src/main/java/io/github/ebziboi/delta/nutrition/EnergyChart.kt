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


/** A gridline step that gives three to five lines. */
private fun niceStep(span:Double)=listOf(100.0,200.0,250.0,500.0,1000.0,2000.0,2500.0,5000.0,10000.0,20000.0).firstOrNull {span/it<=5} ?: 50000.0

internal fun DrawScope.label(text:String,x:Float,y:Float,color:Color,size:Float,alignRight:Boolean=false,center:Boolean=false) {
    val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color=color.toArgb();textSize=size
        textAlign=if(center) android.graphics.Paint.Align.CENTER else if(alignRight) android.graphics.Paint.Align.RIGHT else android.graphics.Paint.Align.LEFT
    }
    drawContext.canvas.nativeCanvas.drawText(text,x,y,paint)
}

/**
 * What you ate each day as bars against that day's budget (the stepped line, which moves with the day's
 * activity). Anything over the budget shows in the warning colour; partly logged days are outlines and
 * don't count. Tap or slide to read a day.
 */
@Composable
internal fun EnergyBalanceSection(all:List<DayBudget>,weights:List<WeightEntry>,unit:WeightUnit,health:EnergyHealthData,usesHealth:Boolean,
                                  refreshing:Boolean,onRefresh:()->Unit,onAllow:()->Unit) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current;val haptic=LocalHapticFeedback.current;val density=LocalDensity.current
    var range by rememberSaveable {mutableStateOf(14)}
    val days=remember(all,range) {all.takeLast(range)}
    if(days.isEmpty()) return
    var selectedDay by rememberSaveable {mutableLongStateOf(Long.MIN_VALUE)}
    val selected=days.firstOrNull {it.day.toEpochDay()==selectedDay} ?: days.last()
    val summary=remember(days,weights) {EnergyHistory.summary(days,weights)}
    val fills=days.map {dayFill(it)}

    ContentSection {
        Caption("Eaten vs budget")
        SegmentedControl(listOf(7,14,30,90),range,{when(it) {7->"1W";14->"2W";30->"1M";else->"3M"}}) {range=it}
        // The selected day, in words, above the chart.
        Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(if(selected.isToday) "Today" else selected.day.format(DateTimeFormatter.ofPattern("EEE d MMM")),style=type.bodyStrong,modifier=Modifier.weight(1f))
                val v=selected.vsBudget
                Text(dayVerdict(selected),style=type.bodyStrong,color=Color(when {
                    v==null || (!selected.complete && !selected.isToday) -> c.textDim
                    v>5 && !selected.gaining -> c.warn
                    else -> c.good
                }))
            }
            Text(dayDetail(selected),style=type.label,color=Color(c.textDim),maxLines=2)
        }

        val currentDays by rememberUpdatedState(days)
        fun pick(x:Float,width:Float,right:Float) {
            val list=currentDays;val slot=(width-right)/list.size
            val d=list[(x/slot).toInt().coerceIn(0,list.size-1)]
            if(d.day.toEpochDay()!=selectedDay) {selectedDay=d.day.toEpochDay();haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
        }
        val faint=Color(c.textFaint);val text=Color(c.text);val warn=Color(c.warn)
        Canvas(Modifier.fillMaxWidth().height(170.dp).semantics {
            contentDescription="Eaten against budget, last $range days"
            stateDescription="${summary.onBudgetDays} of ${summary.countedDays} logged days on budget. Selected ${selected.day}: ${dayVerdict(selected)}"
        }.pointerInput(Unit) {
            detectTapGestures {p->pick(p.x,size.width.toFloat(),36.dp.toPx())}
        }.pointerInput(Unit) {
            detectHorizontalDragGestures {change,_->change.consume();pick(change.position.x,size.width.toFloat(),36.dp.toPx())}
        }) {
            val right=36.dp.toPx();val top=6.dp.toPx();val bottom=size.height-2.dp.toPx()
            val plot=size.width-right;val slot=plot/days.size
            val peak=((days.mapNotNull {it.intakeKcal}+days.map {it.budgetKcal}).maxOrNull() ?: 2000.0)*1.08
            val step=niceStep(peak)
            val hi=ceil(peak/step)*step
            fun y(v:Double)=(bottom-(v/hi)*(bottom-top)).toFloat()
            val textSize=with(density) {10.dp.toPx()}
            val si=days.indexOf(selected)
            drawRoundRect(text.copy(alpha=.07f),Offset(slot*(si+.5f)-maxOf(slot/2,6.dp.toPx()),0f),Size(maxOf(slot,12.dp.toPx()),size.height),CornerRadius(6.dp.toPx()))
            var g=step
            while(g<=hi+.1) {
                drawLine(text.copy(alpha=.06f),Offset(0f,y(g)),Offset(plot,y(g)),density.density)
                label(kcal(g),size.width,y(g)+textSize*.35f,faint,textSize,alignRight=true)
                g+=step
            }
            drawLine(text.copy(alpha=.25f),Offset(0f,bottom),Offset(plot,bottom),density.density)
            val barW=(slot*.62f).coerceIn(1.5.dp.toPx(),20.dp.toPx())
            days.forEachIndexed {i,d->
                val eaten=d.intakeKcal ?: return@forEachIndexed
                if(eaten<=0) return@forEachIndexed
                val x=slot*(i+.5f)-barW/2
                if(!d.complete && !d.isToday) {
                    drawRoundRect(faint,Offset(x,y(eaten)),Size(barW,bottom-y(eaten)),CornerRadius(minOf(barW/2,4.dp.toPx())),style=Stroke(1.2.dp.toPx()))
                    return@forEachIndexed
                }
                splitBar(x,barW,bottom,y(eaten),if(d.gaining) null else y(d.budgetKcal),fills[i],warn,if(d.isToday) .55f else if(i==si) 1f else .85f)
            }
            // Each day's budget as a stepped line.
            val path=Path()
            days.forEachIndexed {i,d->val py=y(d.budgetKcal);val x0=slot*i;if(i==0) path.moveTo(x0,py) else path.lineTo(x0,py);path.lineTo(x0+slot,py)}
            drawPath(path,text.copy(alpha=.7f),style=Stroke(1.5.dp.toPx(),cap=StrokeCap.Round))
        }
        Row(Modifier.fillMaxWidth().padding(end=36.dp)) {
            if(days.size<=14) days.forEach {d->
                Text(if(days.size<=7) d.day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT,java.util.Locale.getDefault()) else d.day.dayOfMonth.toString(),
                    Modifier.weight(1f),style=type.label,color=Color(if(d==selected) c.text else c.textDim),textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=1)
            } else {
                val f=DateTimeFormatter.ofPattern("d MMM")
                Text(days.first().day.format(f),style=type.label,color=Color(c.textDim));Spacer(Modifier.weight(1f))
                Text(days[days.size/2].day.format(f),style=type.label,color=Color(c.textDim));Spacer(Modifier.weight(1f))
                Text("Today",style=type.label,color=Color(c.textDim))
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
            Key("Eaten",Color(c.good));Key("Over budget",warn);Key("Budget",text.copy(alpha=.7f),line=true)
        }

        // Three plain numbers for the range.
        if(summary.countedDays>0) {
            Row(Modifier.fillMaxWidth().padding(top=4.dp)) {
                Stat("${summary.onBudgetDays} of ${summary.countedDays}","days on budget",Modifier.weight(1f))
                Stat(summary.averageVsBudget?.let {signedKcal(it)} ?: "—","kcal a day vs budget",Modifier.weight(1f))
                Stat(Units.signed(summary.expectedKg,unit),"expected change",Modifier.weight(1f))
            }
            Text("From ${kcal(summary.totalVsMaintenance)} kcal ${if(summary.totalVsMaintenance<=0) "under" else "over"} maintenance across ${summary.countedDays} logged ${if(summary.countedDays==1) "day" else "days"}"+
                (summary.actualKg?.let {"; the scale trend moved ${Units.signed(it,unit)}"} ?: "")+".",style=type.label,color=Color(c.textDim))
        } else Text("No fully logged days in this range yet. Log every food in a day and it counts here.",style=type.bodySmall,color=Color(c.textDim))

        HealthStatus(days,health,usesHealth,refreshing,onRefresh,onAllow)
        val partial=days.count {!it.isToday && it.intakeKcal!=null && !it.complete}
        if(partial>0) Text("Outlined ${if(partial==1) "bar is a day" else "bars are days"} where a food has no calories, so ${if(partial==1) "it's" else "they're"} left out of the numbers.",style=type.label,color=Color(c.textDim))
        if(days.any {it.estimated}) Text("Days before the budget was set up use today's settings.",style=type.label,color=Color(c.textDim))
    }
}

/** One line on what a day's numbers were made of. */
private fun dayDetail(d:DayBudget):String=buildList {
    d.intakeKcal?.let {add((if(!d.complete && !d.isToday) "at least " else "ate ")+kcal(it))}
    add("budget ${kcal(d.budgetKcal)}")
    when {
        d.activityKcal==null -> {}
        d.activityMeasured -> add("+${kcal(d.activityKcal)} activity"+(d.steps?.takeIf {it>0}?.let {" (${java.text.DecimalFormat("#,##0").format(it)} steps)"} ?: ""))
        else -> add("usual activity")
    }
}.joinToString(" · ").replaceFirstChar {it.uppercase()}

@Composable private fun Key(text:String,colour:Color,line:Boolean=false) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        if(line) Box(Modifier.size(12.dp,2.dp).background(colour)) else Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(colour))
        Text(text,style=type.label,color=Color(c.textDim),maxLines=1)
    }
}

@Composable private fun Stat(value:String,label:String,modifier:Modifier) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    Column(modifier,verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(value,style=type.number,maxLines=1)
        Text(label,style=type.label,color=Color(c.textDim),maxLines=2)
    }
}

/** Whether Health Connect activity is actually arriving, how much of the range it covers, and a way to read it again. */
@Composable
private fun HealthStatus(days:List<DayBudget>,health:EnergyHealthData,usesHealth:Boolean,refreshing:Boolean,onRefresh:()->Unit,onAllow:()->Unit) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    if(!usesHealth) {
        Text("Activity: your chosen level each day. Turn on Health Connect activity in Body & energy to use what you actually did.",style=type.label,color=Color(c.textDim))
        return
    }
    Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(Radii.control)).background(Color(c.text).copy(alpha=.05f)).padding(start=12.dp,top=4.dp,bottom=4.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        val past=days.filter {!it.isToday}
        val covered=past.count {it.activityMeasured}
        val blocked=!health.canReadActivity && health.readAt>0
        val (line,tone)=when {
            blocked -> "Delta can't read activity from Health Connect, so each day uses your usual amount." to c.warn
            health.readAt==0L -> "Reading activity from Health Connect…" to c.textDim
            covered==0 && past.isNotEmpty() -> "No activity found in Health Connect for these days. Check that your watch or phone app shares active calories or steps with it." to c.warn
            else -> "Activity from Health Connect on $covered of ${past.size} past days · read at ${java.time.Instant.ofEpochMilli(health.readAt).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}" to c.textDim
        }
        Text(line,style=type.label,color=Color(tone),modifier=Modifier.weight(1f))
        if(blocked) TextAction("Allow",onAllow)
        else TextAction(if(refreshing) "Reading…" else "Read again",{if(!refreshing) onRefresh()})
    }
}

/** BMI among every group and the weight each means at your height, milestones, target and body fat. */
@Composable
internal fun BodyReport(b:EnergyBalance,profile:EnergyProfile,targetKg:Double?,bodyFat:BodyFatEntry?,unit:WeightUnit) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    ContentSection {
        Caption("BMI & weight group")
        val height=b.heightCm
        val bmi=b.bmi
        if(height==null || bmi==null) {
            NutritionRow("Weight",Units.weight(b.currentKg,unit),"7-day average")
            Text("Add your height in Body & energy to see your BMI, weight group and healthy weight range.",style=type.bodySmall,color=Color(c.textDim))
        } else {
            val group=BmiGroup.of(bmi)
            BmiHeader(bmi,height)
            BmiBand(bmi,targetKg?.let {EnergyMath.bmi(it,height)})
            Text(group.summary,style=type.bodySmall)
            BmiLadder(height,b.currentKg,targetKg,unit)
            val healthy=BodyMetrics.healthyRange(height)
            val kg=b.currentKg
            when {
                kg>healthy.endInclusive -> NutritionRow("To the healthy range",Units.amount(kg-healthy.endInclusive,unit),"${Math.round((kg-healthy.endInclusive)/kg*100)}% of your weight")
                kg<healthy.start -> NutritionRow("To the healthy range",Units.amount(healthy.start-kg,unit),"to gain")
                else -> NutritionRow("In the healthy range","${Units.weightValue(healthy.start,unit)}–${Units.weight(healthy.endInclusive,unit)}","BMI 18.5 to 24.9")
            }
            if(group.ordinal>BmiGroup.OVER.ordinal) {
                val next=BmiGroup.entries[group.ordinal-1]
                val at=BodyMetrics.weightAt(group.from,height)-.1
                NutritionRow("Next group",Units.amount(kg-at,unit),"${next.label} below ${Units.weight(at+.1,unit)}")
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
