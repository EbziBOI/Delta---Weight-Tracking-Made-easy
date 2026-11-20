package io.github.ebziboi.delta.nutrition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.ui.*
import kotlin.math.abs

private fun kcal(v:Double)=java.text.DecimalFormat("#,##0").format(Math.round(abs(v)))
private fun one(v:Double)=String.format(java.util.Locale.UK,"%.1f",v)

/** A day planned as a surplus, where the budget is something to reach rather than stay under. */
internal val DayBudget.gaining get()=planned>50

/** A rounded bar from the baseline up to [top]; above [split] (the budget) it turns to the over colour. */
internal fun DrawScope.splitBar(x:Float,width:Float,base:Float,top:Float,split:Float?,fill:Color,over:Color,alpha:Float=1f) {
    if(top>=base-.5f) return
    val r=CornerRadius(minOf(width/2,4.dp.toPx()))
    if(split==null || split<=top) {drawRoundRect(fill.copy(alpha=alpha),Offset(x,top),Size(width,base-top),r);return}
    drawRoundRect(fill.copy(alpha=alpha),Offset(x,split),Size(width,base-split),r)
    val gap=1.5.dp.toPx()
    if(split-gap>top) drawRoundRect(over.copy(alpha=alpha),Offset(x,top),Size(width,split-gap-top),r)
}

/** The fill for a finished day's bar: green unless a surplus day fell short. */
@Composable internal fun dayFill(d:DayBudget):Color {
    val c=LocalDeltaColors.current
    return Color(if(d.gaining && !d.onBudget && !d.isToday) c.accent else c.good)
}

/**
 * Eaten against the budget as one bar. Under the budget it fills green; anything over carries on past a
 * notch in the warning colour, so "over" needs no legend.
 */
@Composable
internal fun BudgetBar(eaten:Double,budget:Double,modifier:Modifier=Modifier,gaining:Boolean=false,height:Dp=10.dp) {
    val c=LocalDeltaColors.current
    val motion=LocalMotion.current
    val shown by animateFloatAsState(eaten.toFloat(),if(motion) tween(450) else tween(0),label="eaten")
    val good=Color(c.good);val warn=Color(c.warn);val track=Color(c.text).copy(alpha=.09f);val notch=Color(c.text)
    Canvas(modifier.fillMaxWidth().height(height+6.dp).semantics {
        contentDescription="Eaten against budget"
        stateDescription="${kcal(eaten)} of ${kcal(budget)} kcal"
    }) {
        val h=height.toPx();val top=(size.height-h)/2;val r=CornerRadius(h/2)
        val e=shown.toDouble()
        val max=maxOf(budget,e,1.0)
        fun x(v:Double)=(v/max*size.width).toFloat()
        drawRoundRect(track,Offset(0f,top),Size(size.width,h),r)
        if(e>0) {
            drawRoundRect(good,Offset(0f,top),Size(maxOf(x(minOf(e,budget)),h),h),r)
            if(!gaining && e>budget) drawRoundRect(warn,Offset(x(budget),top),Size(x(e)-x(budget),h),r)
        }
        if(e>budget) drawLine(notch,Offset(x(budget),0f),Offset(x(budget),size.height),2.dp.toPx())
    }
}

/** What's left today (or how far over), eaten against budget, and the bar. */
@Composable
internal fun TodayBudget(d:DayBudget,big:Boolean=false) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    val eaten=d.intakeKcal ?: 0.0
    val left=d.budgetKcal-eaten
    val over=left<0 && !d.gaining
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    RollingText(kcal(left),if(big) type.numberLarge.copy(fontSize=40.sp,lineHeight=44.sp) else type.numberLarge,
                        color=if(over) Color(c.warn) else Color(c.text))
                    Text("kcal",style=type.label,color=Color(c.textDim),modifier=Modifier.padding(bottom=if(big) 8.dp else 4.dp))
                }
                Text(when {over->"over today's budget";left<0->"past today's target";d.gaining->"still to eat today";else->"left today"},style=type.label,color=Color(c.textDim))
            }
            Column(horizontalAlignment=Alignment.End) {
                Text("${kcal(eaten)} / ${kcal(d.budgetKcal)}",style=type.number)
                Text("eaten / budget",style=type.label,color=Color(c.textDim))
            }
        }
        BudgetBar(eaten,d.budgetKcal,gaining=d.gaining)
    }
}

/**
 * The last week as small bars, each scaled to that day's own budget so the budget is one straight line.
 * Under the line is under budget; anything over pokes above it in the warning colour. Partly logged days
 * are outlines.
 */
@Composable
internal fun WeekStrip(days:List<DayBudget>,height:Dp=64.dp) {
    if(days.isEmpty()) return
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    val warn=Color(c.warn);val text=Color(c.text);val faint=Color(c.textFaint)
    val fills=days.map {dayFill(it)}
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(height).semantics {
            contentDescription="Last ${days.size} days against budget"
            stateDescription=days.joinToString {d->"${d.day.dayOfWeek}: "+dayVerdict(d)}
        }) {
            val maxRatio=1.5
            fun y(ratio:Double)=size.height*(1-(ratio.coerceIn(0.0,maxRatio)/maxRatio)).toFloat()
            val line=y(1.0)
            val slot=size.width/days.size;val barW=(slot*.5f).coerceAtMost(22.dp.toPx())
            days.forEachIndexed {i,d->
                val x=slot*(i+.5f)-barW/2
                val eaten=d.intakeKcal
                if(eaten==null || eaten<=0) {drawCircle(faint.copy(alpha=.6f),2.dp.toPx(),Offset(x+barW/2,size.height-2.dp.toPx()));return@forEachIndexed}
                val top=y(eaten/d.budgetKcal)
                if(!d.complete && !d.isToday) {
                    drawRoundRect(faint,Offset(x,top),Size(barW,size.height-top),CornerRadius(minOf(barW/2,4.dp.toPx())),style=Stroke(1.3.dp.toPx()))
                    return@forEachIndexed
                }
                splitBar(x,barW,size.height,top,if(d.gaining) null else line,fills[i],warn,if(d.isToday) .55f else 1f)
            }
            drawLine(text.copy(alpha=.5f),Offset(0f,line),Offset(size.width,line),1.2.dp.toPx())
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEach {d->
                Text(if(d.isToday) "Today" else d.day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT,java.util.Locale.getDefault()),Modifier.weight(1f),
                    style=type.label,color=Color(if(d.isToday) c.text else c.textDim),textAlign=TextAlign.Center,maxLines=1)
            }
        }
    }
}

/** A day in words: "On budget", "240 under", "310 over", "Partly logged". */
internal fun dayVerdict(d:DayBudget):String=when {
    d.intakeKcal==null -> "Nothing logged"
    !d.complete && !d.isToday -> "Partly logged"
    else -> d.vsBudget!!.let {v->if(abs(v)<5) "On budget" else "${kcal(v)} ${if(v<0) (if(d.isToday) "left" else "under") else "over"}"}
}

/** Colour for a BMI group: green for the healthy range, deepening amber away from it. */
@Composable internal fun bmiColour(g:BmiGroup):Color {
    val c=LocalDeltaColors.current
    return if(g==BmiGroup.HEALTHY) Color(c.good) else Color(c.warn).copy(alpha=when(g) {BmiGroup.UNDER,BmiGroup.OVER->.5f;BmiGroup.OBESE_1->.7f;BmiGroup.OBESE_2->.85f;else->1f})
}

internal val BmiGroup.short get()=when(this) {
    BmiGroup.UNDER->"Under";BmiGroup.HEALTHY->"Healthy";BmiGroup.OVER->"Over"
    BmiGroup.OBESE_1->"Obese I";BmiGroup.OBESE_2->"Obese II";BmiGroup.OBESE_3->"Obese III"
}

/**
 * Every BMI group side by side at equal widths so each is readable, named underneath with its boundaries.
 * You are the filled dot; your target, when set, the ring.
 */
@Composable
internal fun BmiBand(bmi:Double,targetBmi:Double?) {
    val c=LocalDeltaColors.current;val density=LocalDensity.current
    val groups=BmiGroup.entries
    val colours=groups.map {bmiColour(it)}
    val mine=BmiGroup.of(bmi)
    val text=Color(c.text);val dim=Color(c.textDim);val faint=Color(c.textFaint);val halo=Color(c.bg)
    Canvas(Modifier.fillMaxWidth().height(60.dp).semantics {
        contentDescription="BMI groups"
        stateDescription="BMI ${one(bmi)}, ${mine.label}"+(targetBmi?.let {", target ${one(it)}, ${BmiGroup.of(it).label}"} ?: "")
    }) {
        val segW=size.width/groups.size;val gap=3.dp.toPx();val barTop=8.dp.toPx();val barH=10.dp.toPx()
        fun pos(v:Double):Float {
            val g=BmiGroup.of(v)
            val from=if(g==BmiGroup.UNDER) 15.0 else g.from;val to=if(g==BmiGroup.OBESE_3) 45.0 else g.to
            return segW*g.ordinal+segW*((v-from)/(to-from)).coerceIn(.06,.94).toFloat()
        }
        groups.forEachIndexed {i,g->
            drawRoundRect(colours[i].copy(alpha=colours[i].alpha*(if(g==mine) 1f else .7f)),Offset(segW*i+gap/2,barTop),Size(segW-gap,barH),CornerRadius(barH/2))
        }
        val small=with(density) {9.dp.toPx()};val name=with(density) {10.5.dp.toPx()}
        listOf(18.5,25.0,30.0,35.0,40.0).forEachIndexed {i,v->label(if(v==18.5) "18.5" else v.toInt().toString(),segW*(i+1),barTop+barH+12.dp.toPx(),faint,small,center=true)}
        groups.forEachIndexed {i,g->label(g.short,segW*(i+.5f),size.height-3.dp.toPx(),if(g==mine) text else dim,name,center=true)}
        val cy=barTop+barH/2
        targetBmi?.let {tb->
            val tx=pos(tb)
            drawCircle(halo,7.5.dp.toPx(),Offset(tx,cy));drawCircle(text,5.5.dp.toPx(),Offset(tx,cy),style=Stroke(2.dp.toPx()))
        }
        val mx=pos(bmi)
        drawCircle(halo,8.5.dp.toPx(),Offset(mx,cy));drawCircle(text,6.5.dp.toPx(),Offset(mx,cy))
    }
}

/** The BMI figure with its group, shared by the Home card and the energy page. */
@Composable
internal fun BmiHeader(bmi:Double,heightCm:Double) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    val group=BmiGroup.of(bmi)
    Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(one(bmi),style=type.numberLarge)
        Column(Modifier.padding(bottom=3.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(bmiColour(group)))
                Text(group.label,style=type.bodyStrong)
            }
            Text(listOfNotNull(group.alias,"at ${Math.round(heightCm)} cm").joinToString(" · "),style=type.label,color=Color(c.textDim),maxLines=1)
        }
    }
}

/** Under the band: the one distance that matters most, the healthy range, and what the ring is. */
@Composable
internal fun BmiNote(kg:Double,heightCm:Double,targetKg:Double?,unit:WeightUnit) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    val healthy=BodyMetrics.healthyRange(heightCm)
    val group=BmiGroup.of(EnergyMath.bmi(kg,heightCm))
    val line=when {
        group.ordinal>BmiGroup.OVER.ordinal -> {
            val next=BmiGroup.entries[group.ordinal-1]
            "${Units.amount(kg-(BodyMetrics.weightAt(group.from,heightCm)-.1),unit)} to ${next.label.lowercase()} · ${Units.amount(kg-healthy.endInclusive,unit)} to healthy"
        }
        group==BmiGroup.OVER -> "${Units.amount(kg-healthy.endInclusive,unit)} to the healthy range"
        group==BmiGroup.UNDER -> "${Units.amount(healthy.start-kg,unit)} to the healthy range"
        else -> "In the healthy range for your height"
    }
    Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(line,style=type.bodyStrong)
        Text("Healthy at your height: ${Units.weightValue(healthy.start,unit)}–${Units.weight(healthy.endInclusive,unit)}"+
            (targetKg?.let {" · ring: target ${Units.weight(it,unit)}"} ?: ""),style=type.label,color=Color(c.textDim))
    }
}

/** Each BMI group with the weight it means at your height, heaviest first. Your row is highlighted. */
@Composable
internal fun BmiLadder(heightCm:Double,kg:Double,targetKg:Double?,unit:WeightUnit) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    val mine=BmiGroup.of(EnergyMath.bmi(kg,heightCm))
    val target=targetKg?.let {BmiGroup.of(EnergyMath.bmi(it,heightCm))}
    Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
        BmiGroup.entries.reversed().forEach {g->
            val active=g==mine
            val from=BodyMetrics.weightAt(g.from,heightCm);val to=BodyMetrics.weightAt(g.to,heightCm)
            val range=when(g) {
                BmiGroup.UNDER -> "under ${Units.weight(to,unit)}"
                BmiGroup.OBESE_3 -> "${Units.weight(from,unit)}+"
                else -> "${Units.weightValue(from,unit)}–${Units.weight(to-.1,unit)}"
            }
            val bmiRange=when(g) {BmiGroup.UNDER->"under 18.5";BmiGroup.OBESE_3->"40+";else->"${one(g.from)}–${one(g.to-.1)}"}
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if(active) Color(c.text).copy(alpha=.07f) else Color.Transparent)
                .padding(horizontal=10.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(4.dp).height(26.dp).clip(RoundedCornerShape(2.dp)).background(bmiColour(g)))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(g.label,style=if(active) type.bodyStrong else type.bodySmall,color=Color(if(active) c.text else c.textDim),maxLines=1)
                        if(active) Tag("You")
                        if(target==g) Tag("Target",outlined=true)
                    }
                    g.alias?.let {Text(it,style=type.label,color=Color(c.textFaint),maxLines=1)}
                }
                Column(horizontalAlignment=Alignment.End) {
                    Text(range,style=type.label,color=Color(if(active) c.text else c.textDim),maxLines=1)
                    Text("BMI $bmiRange",style=type.label,color=Color(c.textFaint),maxLines=1)
                }
            }
        }
    }
}

@Composable private fun Tag(text:String,outlined:Boolean=false) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    val shape=RoundedCornerShape(50)
    Text(text,style=type.label,color=Color(c.text),maxLines=1,
        modifier=(if(outlined) Modifier.border(1.dp,Color(c.text).copy(alpha=.4f),shape) else Modifier.clip(shape).background(Color(c.text).copy(alpha=.14f)))
            .padding(horizontal=7.dp,vertical=1.dp))
}

@Composable internal fun CardTitle(text:String) {
    val c=LocalDeltaColors.current
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Caption(text,Modifier.weight(1f))
        DeltaIcon(io.github.ebziboi.delta.R.drawable.ic_chevron_right,null,tint=Color(c.textDim),size=18.dp)
    }
}

internal fun weekLine(s:BalanceSummary,unit:WeightUnit)=
    "${kcal(s.totalVsMaintenance)} kcal ${if(s.totalVsMaintenance<=0) "under" else "over"} maintenance ≈ ${Units.signed(s.expectedKg,unit)}"

/** Home card: what's left today and the last week against budget. Opens the energy page. */
@Composable
internal fun HomeEnergyCard(onOpen:()->Unit) {
    val app=LocalContext.current.applicationContext as DeltaApp
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    val days by app.energy.days.collectAsStateWithLifecycle()
    val settings by app.repo.settings.collectAsStateWithLifecycle(initialValue=null)
    val unit=settings?.unit ?: WeightUnit.KG
    ContentSection(onClick=onOpen,tone=ModuleTone.VIOLET) {
        CardTitle("Energy today")
        val today=days.lastOrNull()?.takeIf {it.isToday}
        if(today==null) {Text("Log a weigh-in to work out your budget.",style=type.bodySmall,color=Color(c.textDim));return@ContentSection}
        TodayBudget(today)
        val week=days.takeLast(7)
        val summary=remember(week) {EnergyHistory.summary(week,emptyList())}
        Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Last 7 days",style=type.bodyStrong,modifier=Modifier.weight(1f))
            Text(if(summary.countedDays==0) "No fully logged days" else "${summary.onBudgetDays} of ${summary.countedDays} on budget",style=type.label,color=Color(c.textDim))
        }
        WeekStrip(week)
        if(summary.countedDays>0) Text(weekLine(summary,unit),style=type.label,color=Color(c.textDim))
    }
}

/** Home card: your BMI among every group, and the distance to the next one. Opens the energy page. */
@Composable
internal fun HomeBmiCard(latestKg:Double?,onOpen:()->Unit,onSetUp:()->Unit) {
    val app=LocalContext.current.applicationContext as DeltaApp
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    val balance by app.energy.balance.collectAsStateWithLifecycle()
    val settings by app.repo.settings.collectAsStateWithLifecycle(initialValue=null)
    val s=settings ?: return
    val height=s.energy.heightCm ?: balance?.heightCm
    val kg=balance?.currentKg ?: latestKg
    ContentSection(onClick=if(height!=null && s.energy.enabled) onOpen else onSetUp) {
        CardTitle("BMI & weight group")
        if(height==null || kg==null) {
            Text("Add your height to see where your weight sits among the BMI groups.",style=type.bodySmall,color=Color(c.textDim))
            SecondaryButton("Add height",onSetUp)
            return@ContentSection
        }
        val bmi=EnergyMath.bmi(kg,height)
        BmiHeader(bmi,height)
        BmiBand(bmi,s.targetKg?.let {EnergyMath.bmi(it,height)})
        BmiNote(kg,height,s.targetKg,s.unit)
    }
}
