package io.github.ebziboi.delta.nutrition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.ui.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

private fun kcal(v:Double)=java.text.DecimalFormat("#,##0").format(Math.round(abs(v)))
private fun signed(v:Double)=(if(v<0) "−" else if(v>0) "+" else "")+kcal(v)
private fun one(v:Double)=String.format(java.util.Locale.UK,"%.1f",v)

@Composable private fun Swatch(text:String,colour:Color,dashed:Boolean=false,line:Boolean=false) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        if(line || dashed) Canvas(Modifier.size(if(dashed) 10.dp else 3.dp,12.dp)) {
            drawLine(colour,Offset(size.width/2,0f),Offset(size.width/2,size.height),1.8.dp.toPx(),
                pathEffect=if(dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(),2.dp.toPx())) else null)
        } else Box(Modifier.size(8.dp).clip(CircleShape).background(colour))
        Text(text,style=type.label,color=Color(c.textDim),maxLines=1)
    }
}

/**
 * One day's energy as a gauge: what's eaten fills the track, the deficit zone between budget and maintenance
 * is shaded, and the budget (dashed) and maintenance (solid) are marked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeficitMeter(d:DayBudget,modifier:Modifier=Modifier) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    val eaten=d.intakeKcal ?: 0.0
    val left=d.budgetKcal-eaten
    val fill=Color(when {eaten<=d.budgetKcal+50->c.good;eaten<=d.maintenanceKcal->c.accent;else->c.warn})
    val good=Color(c.good);val text=Color(c.text)
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                ValueWithUnit(kcal(left),"kcal",style=type.numberLarge,color=if(left>=0) Color(c.text) else Color(c.warn))
                Text(if(left>=0) (if(d.isToday) "left today" else "under budget") else "over budget",style=type.label,color=Color(c.textDim))
            }
            Column(horizontalAlignment=Alignment.End) {
                val gap=d.maintenanceKcal-eaten
                Text(if(gap>=0) kcal(gap) else "+${kcal(gap)}",style=type.number,color=Color(if(gap>=0) c.good else c.warn))
                Text(if(gap>=0) (if(d.isToday) "under maintenance" else "deficit") else "surplus",style=type.label,color=Color(c.textDim))
            }
        }
        Canvas(Modifier.fillMaxWidth().height(30.dp).semantics {
            contentDescription="Energy gauge"
            stateDescription="Eaten ${kcal(eaten)}, budget ${kcal(d.budgetKcal)}, maintenance ${kcal(d.maintenanceKcal)} kcal"
        }) {
            val max=maxOf(d.maintenanceKcal,eaten,d.budgetKcal)*1.06
            fun x(v:Double)=(v/max*size.width).toFloat().coerceIn(0f,size.width)
            val h=12.dp.toPx();val top=(size.height-h)/2;val r=CornerRadius(h/2)
            drawRoundRect(text.copy(alpha=.08f),Offset(0f,top),Size(size.width,h),r)
            // The deficit zone: eating anywhere in here still loses weight.
            if(d.maintenanceKcal>d.budgetKcal) drawRect(good.copy(alpha=.16f),Offset(x(d.budgetKcal),top),Size(x(d.maintenanceKcal)-x(d.budgetKcal),h))
            if(eaten>0) drawRoundRect(fill,Offset(0f,top),Size(maxOf(x(eaten),h),h),r)
            drawLine(text.copy(alpha=.85f),Offset(x(d.budgetKcal),0f),Offset(x(d.budgetKcal),size.height),1.6.dp.toPx(),
                pathEffect=PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(),2.dp.toPx())))
            drawLine(text.copy(alpha=.6f),Offset(x(d.maintenanceKcal),2.dp.toPx()),Offset(x(d.maintenanceKcal),size.height-2.dp.toPx()),2.dp.toPx(),cap=StrokeCap.Round)
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Swatch("Eaten ${kcal(eaten)}",fill)
            Swatch("Budget ${kcal(d.budgetKcal)}",text.copy(alpha=.85f),dashed=true)
            Swatch("Maintenance ${kcal(d.maintenanceKcal)}",text.copy(alpha=.6f),line=true)
        }
    }
}

/** A compact week of deficit and surplus bars around maintenance, with each day's budget dashed. */
@Composable
internal fun MiniBalanceBars(days:List<DayBudget>,height:Dp=92.dp) {
    if(days.isEmpty()) return
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    val colours=days.map {dayColour(it)}
    val text=Color(c.text);val faint=Color(c.textFaint)
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(height).semantics {
            contentDescription="Week of energy balance"
            stateDescription=days.joinToString {d->"${d.day.dayOfWeek}: "+(d.vsMaintenance?.takeIf {d.complete || d.isToday}?.let {signed(it)} ?: "not fully logged")}
        }) {
            val values=days.map {d->d.vsMaintenance?.takeIf {d.complete || d.isToday}}
            val known=(values.filterNotNull()+days.map {it.planned})
            val step=250.0
            val hi=ceil(maxOf(known.maxOrNull() ?: 0.0,150.0)*1.1/step)*step;val lo=floor(minOf(known.minOrNull() ?: 0.0,-150.0)*1.1/step)*step
            fun y(v:Double)=((hi-v)/(hi-lo)*size.height).toFloat()
            val slot=size.width/days.size
            fun cx(i:Int)=slot*(i+.5f)
            drawLine(text.copy(alpha=.28f),Offset(0f,y(0.0)),Offset(size.width,y(0.0)),1.2.dp.toPx())
            val barW=(slot*.56f).coerceAtMost(26.dp.toPx())
            days.forEachIndexed {i,d->
                val v=values[i]
                when {
                    d.intakeKcal==null -> drawCircle(faint.copy(alpha=.5f),1.8.dp.toPx(),Offset(cx(i),y(0.0)))
                    v==null -> drawRoundRect(faint,Offset(cx(i)-barW/2,y(0.0)+2.dp.toPx()),Size(barW,10.dp.toPx()),CornerRadius(3.dp.toPx()),
                        style=Stroke(1.2.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(),2.dp.toPx()))))
                    else -> {
                        val y0=y(0.0);val y1=y(v)
                        drawRoundRect(colours[i].copy(alpha=if(d.isToday) .45f else .9f),Offset(cx(i)-barW/2,minOf(y0,y1)),Size(barW,maxOf(abs(y1-y0),1.5.dp.toPx())),CornerRadius(minOf(barW/2,4.dp.toPx())))
                    }
                }
            }
            val path=Path()
            days.forEachIndexed {i,d->val py=y(d.planned);if(i==0) path.moveTo(0f,py) else path.lineTo(slot*i,py);path.lineTo(slot*(i+1),py)}
            drawPath(path,text.copy(alpha=.55f),style=Stroke(1.2.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),3.dp.toPx()))))
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEach {d->
                Text(if(d.isToday) "Today" else d.day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT,java.util.Locale.getDefault()),Modifier.weight(1f),
                    style=type.label,color=Color(if(d.isToday) c.accent else c.textDim),textAlign=TextAlign.Center,maxLines=1)
            }
        }
    }
}

/** Colour for a BMI group: green for the healthy range, deepening amber away from it. */
@Composable internal fun bmiColour(g:BmiGroup):Color {
    val c=LocalDeltaColors.current
    return if(g==BmiGroup.HEALTHY) Color(c.good) else Color(c.warn).copy(alpha=when(g) {BmiGroup.UNDER,BmiGroup.OVER->.45f;BmiGroup.OBESE_1->.65f;BmiGroup.OBESE_2->.85f;else->1f})
}

/** The BMI groups as one scale, with you marked and your target as a ring. */
@Composable
internal fun BmiScale(bmi:Double,targetBmi:Double?) {
    val c=LocalDeltaColors.current;val density=LocalDensity.current
    val colours=BmiGroup.entries.associateWith {bmiColour(it)}
    val faint=Color(c.textFaint);val text=Color(c.text)
    Canvas(Modifier.fillMaxWidth().height(46.dp).semantics {contentDescription="BMI scale";stateDescription="BMI ${one(bmi)}, ${BmiGroup.of(bmi).label}"+(targetBmi?.let {", target ${one(it)}"} ?: "")}) {
        val lo=15.0;val hi=45.0;val pad=6.dp.toPx();val w=size.width-2*pad
        fun x(v:Double)=pad+((v.coerceIn(lo,hi)-lo)/(hi-lo)*w).toFloat()
        val barTop=10.dp.toPx();val barH=8.dp.toPx();val gap=2.dp.toPx()
        BmiGroup.entries.forEachIndexed {i,g->
            val a=maxOf(g.from,lo);val z=minOf(g.to,hi)
            drawRoundRect(colours.getValue(g),Offset(x(a)+(if(i>0) gap/2 else 0f),barTop),Size(x(z)-x(a)-(if(i>0) gap else gap/2),barH),CornerRadius(barH/2))
        }
        val textSize=with(density) {10.dp.toPx()}
        listOf(18.5,25.0,30.0,35.0,40.0).forEach {v->label(if(v==18.5) "18.5" else v.toInt().toString(),x(v),barTop+barH+14.dp.toPx(),faint,textSize,center=true)}
        targetBmi?.let {tb->drawCircle(text.copy(alpha=.8f),5.dp.toPx(),Offset(x(tb),barTop+barH/2),style=Stroke(1.6.dp.toPx()))}
        val mx=x(bmi)
        drawLine(text,Offset(mx,barTop-6.dp.toPx()),Offset(mx,barTop+barH+4.dp.toPx()),2.dp.toPx(),cap=StrokeCap.Round)
        drawCircle(text,4.dp.toPx(),Offset(mx,barTop-6.dp.toPx()))
    }
}

/**
 * Every BMI group as a row, heaviest at the top, with the weight range it means at your height.
 * Your group is highlighted and shows where you sit inside it.
 */
@Composable
internal fun BmiLadder(heightCm:Double,kg:Double,targetKg:Double?,unit:WeightUnit) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    val bmi=EnergyMath.bmi(kg,heightCm);val mine=BmiGroup.of(bmi)
    val target=targetKg?.let {BmiGroup.of(EnergyMath.bmi(it,heightCm))}
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
        BmiGroup.entries.reversed().forEach {g->
            val active=g==mine
            val from=BodyMetrics.weightAt(g.from,heightCm);val to=BodyMetrics.weightAt(g.to,heightCm)
            val range=when(g) {
                BmiGroup.UNDER -> "under ${Units.weight(to,unit)}"
                BmiGroup.OBESE_3 -> "${Units.weight(from,unit)}+"
                else -> "${Units.weightValue(from,unit)}–${Units.weight(to-.1,unit)}"
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if(active) bmiColour(g).copy(alpha=.16f) else Color.Transparent)
                .padding(horizontal=10.dp,vertical=if(active) 9.dp else 6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(5.dp).height(if(active) 34.dp else 22.dp).clip(RoundedCornerShape(3.dp)).background(bmiColour(g)))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(g.label,style=if(active) type.bodyStrong else type.bodySmall,color=Color(if(active) c.text else c.textDim),maxLines=1)
                        if(target==g && !active) Text("Target",style=type.label,color=Color(c.accent))
                    }
                    g.alias?.let {Text(it,style=type.label,color=Color(c.textFaint),maxLines=1)}
                }
                val bmiRange=when(g) {BmiGroup.UNDER->"BMI under 18.5";BmiGroup.OBESE_3->"BMI 40+";else->"BMI ${one(g.from)}–${one(g.to-.1)}"}
                Column(horizontalAlignment=Alignment.End) {
                    if(active) Text("You · ${Units.weight(kg,unit)}",style=type.bodyStrong,color=Color(c.text))
                    Text(range,style=type.label,color=Color(if(active) c.text else c.textDim),maxLines=1)
                    Text(bmiRange,style=type.label,color=Color(c.textFaint),maxLines=1)
                }
            }
        }
    }
}

/** Home card: today's gauge and the last seven days. Opens the energy page. */
@Composable
internal fun HomeEnergyCard(onOpen:()->Unit) {
    val app=LocalContext.current.applicationContext as DeltaApp
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    val days by app.energy.days.collectAsStateWithLifecycle()
    val settings by app.repo.settings.collectAsStateWithLifecycle(initialValue=null)
    val unit=settings?.unit ?: WeightUnit.KG
    ContentSection(onClick=onOpen,tone=ModuleTone.VIOLET) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Caption("Energy balance",Modifier.weight(1f))
            DeltaIcon(io.github.ebziboi.delta.R.drawable.ic_chevron_right,null,tint=Color(c.textDim),size=18.dp)
        }
        val today=days.lastOrNull()?.takeIf {it.isToday}
        if(today==null) {Text("Log a weigh-in to work out your budget.",style=type.bodySmall,color=Color(c.textDim));return@ContentSection}
        DeficitMeter(today)
        val week=days.takeLast(7)
        val summary=remember(week) {EnergyHistory.summary(week,emptyList())}
        Row(Modifier.fillMaxWidth().padding(top=6.dp),verticalAlignment=Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Last 7 days",style=type.bodyStrong)
                Text(if(summary.countedDays==0) "No fully logged days yet" else "${summary.onBudgetDays} of ${summary.countedDays} logged days on budget",style=type.label,color=Color(c.textDim))
            }
            if(summary.countedDays>0) Column(horizontalAlignment=Alignment.End) {
                Text("${signed(summary.totalVsMaintenance)} kcal",style=type.number,color=Color(if(summary.totalVsMaintenance<=0) c.good else c.warn))
                Text("≈ ${Units.signed(summary.expectedKg,unit)}",style=type.label,color=Color(c.textDim))
            }
        }
        MiniBalanceBars(week)
    }
}

/** Home card: your BMI on the scale and every group's weight range at your height. Opens the energy page. */
@Composable
internal fun HomeBmiCard(latestKg:Double?,onOpen:()->Unit,onSetUp:()->Unit) {
    val app=LocalContext.current.applicationContext as DeltaApp
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    val balance by app.energy.balance.collectAsStateWithLifecycle()
    val settings by app.repo.settings.collectAsStateWithLifecycle(initialValue=null)
    val s=settings ?: return
    val unit=s.unit
    val height=s.energy.heightCm ?: balance?.heightCm
    val kg=balance?.currentKg ?: latestKg
    ContentSection(onClick=if(height!=null) onOpen else onSetUp) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Caption("BMI & weight group",Modifier.weight(1f))
            DeltaIcon(io.github.ebziboi.delta.R.drawable.ic_chevron_right,null,tint=Color(c.textDim),size=18.dp)
        }
        if(height==null || kg==null) {
            Text("Add your height to see where your weight sits among the BMI groups.",style=type.bodySmall,color=Color(c.textDim))
            SecondaryButton("Add height",onSetUp)
            return@ContentSection
        }
        val bmi=EnergyMath.bmi(kg,height);val group=BmiGroup.of(bmi)
        Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(one(bmi),style=type.numberLarge)
            Column(Modifier.padding(bottom=3.dp)) {
                Text(group.label,style=type.bodyStrong,color=if(group==BmiGroup.HEALTHY) Color(c.good) else Color(c.warn))
                Text(listOfNotNull(group.alias,"${Math.round(height)} cm").joinToString(" · "),style=type.label,color=Color(c.textDim))
            }
        }
        BmiScale(bmi,s.targetKg?.let {EnergyMath.bmi(it,height)})
        BmiLadder(height,kg,s.targetKg,unit)
        val healthy=BodyMetrics.healthyRange(height)
        val next=if(group.ordinal>BmiGroup.HEALTHY.ordinal) BmiGroup.entries[group.ordinal-1] else null
        val note=when {
            next!=null && next!=BmiGroup.HEALTHY -> "${Units.amount(kg-(BodyMetrics.weightAt(group.from,height)-.1),unit)} to ${next.label.lowercase().replaceFirstChar {it.uppercase()}} · ${Units.amount(kg-healthy.endInclusive,unit)} to the healthy range"
            next==BmiGroup.HEALTHY -> "${Units.amount(kg-healthy.endInclusive,unit)} to the healthy range"
            group==BmiGroup.UNDER -> "${Units.amount(healthy.start-kg,unit)} to the healthy range"
            else -> "Healthy range for your height: ${Units.weightValue(healthy.start,unit)}–${Units.weight(healthy.endInclusive,unit)}"
        }
        Text(note,style=type.bodySmall,color=Color(c.textDim))
    }
}
