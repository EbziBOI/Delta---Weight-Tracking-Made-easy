package io.github.ebziboi.delta.nutrition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ebziboi.delta.data.Units
import io.github.ebziboi.delta.data.WeightUnit
import io.github.ebziboi.delta.data.WeightEntry
import io.github.ebziboi.delta.ui.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

internal data class IntakePoint(val start:LocalDate,val days:List<LocalDate>,val entries:List<DiaryEntry>,val nutrient:Nutrient,val weight:Double?) {
    val total=entries.total(nutrient)
    val loggedDays=entries.map {it.day}.distinct().size
    val value get()=total.amount / loggedDays.coerceAtLeast(1)
    val hasValue get()=entries.isNotEmpty() && total.reported>0
}
internal data class TargetComparison(val satisfying:Int,val eligible:Int,val incomplete:Int)
internal fun List<IntakePoint>.targetComparison(target:Double,limit:Boolean):TargetComparison {
    val logged=filter {it.entries.isNotEmpty()}
    val complete=logged.filter {it.hasValue && !it.total.partial}
    return TargetComparison(complete.count {if(limit) it.value<=target else it.value>=target},complete.size,logged.size-complete.size)
}
internal fun intakePoints(entries:List<DiaryEntry>,weights:List<WeightEntry>,end:LocalDate,days:Int,weekly:Boolean,nutrient:Nutrient):List<IntakePoint> {
    val start=end.minusDays(days.toLong()-1)
    val weightDays=weights.groupBy {java.time.Instant.ofEpochMilli(it.epochMs).atZone(ZoneId.systemDefault()).toLocalDate()}
    return (0 until days step if(weekly) 7 else 1).map {offset->
        val dates=(0 until if(weekly) minOf(7,days-offset) else 1).map {start.plusDays((offset+it).toLong())}
        val e=entries.filter {it.day in dates}
        val w=dates.mapNotNull {d->weightDays[d]?.map {it.kg}?.average()}.takeIf {it.isNotEmpty()}?.average()
        IntakePoint(dates.first(),dates,e,nutrient,w)
    }
}

@Composable
internal fun NutritionChartsPage(state:NutritionState,weights:List<WeightEntry>,padding:PaddingValues,onBack:()->Unit,weightUnit:WeightUnit=WeightUnit.KG) {
    var nutrient by rememberSaveable {mutableStateOf(Nutrient.ENERGY)}
    var days by rememberSaveable {mutableIntStateOf(14)}
    var weekly by rememberSaveable {mutableStateOf(false)}
    var endEpoch by rememberSaveable {mutableLongStateOf(LocalDate.now().toEpochDay())}
    var showWeight by rememberSaveable {mutableStateOf(true)}
    var selected by rememberSaveable {mutableIntStateOf(-1)}
    var menu by remember {mutableStateOf(false)}
    val end=LocalDate.ofEpochDay(endEpoch)
    val points=remember(state.entries,weights,end,days,weekly,nutrient) {intakePoints(state.entries,weights,end,days,weekly,nutrient)}
    val c=LocalDeltaColors.current
    NutritionPage("Nutrition trends",padding,onBack) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Box {SecondaryButton("${nutrient.label} ▾",{menu=true});DropdownMenu(menu,{menu=false}) { Nutrient.entries.forEach { n ->DropdownMenuItem({Text(n.label)},{nutrient=n;selected=-1;menu=false})} }}
        }
        SegmentedControl(listOf(14,30,90),days,{"$it days"}) {days=it;selected=-1}
        SegmentedControl(listOf(false,true),weekly,{if(it) "Weekly" else "Daily"}) {weekly=it;selected=-1}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
            TextButton({endEpoch-=days;selected=-1},enabled=state.entries.minOfOrNull {it.day}?.let {end.minusDays(days.toLong()-1)>it}==true) {Text("Previous")}
            TextButton({endEpoch=minOf(LocalDate.now().toEpochDay(),endEpoch+days);selected=-1},enabled=end<LocalDate.now()) {Text("Next")}
        }
        Text("${end.minusDays(days.toLong()-1).format(DateTimeFormatter.ofPattern("d MMM"))} – ${end.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}",style=LocalDeltaType.current.bodySmall)
        ContentSection(tone=ModuleTone.VIOLET,framed=true) {
            Caption(if(weekly) "Daily average by week" else "Daily intake")
            val available=points.filter {it.hasValue}
            val logged=state.entries.filter {it.day>=end.minusDays(days.toLong()-1) && it.day<=end}
            val loggedCount=logged.map {it.day}.distinct().size
            ValueWithUnit(if(available.isEmpty()) "—" else amountText(logged.total(nutrient).amount/loggedCount.coerceAtLeast(1)),nutrient.unit,style=LocalDeltaType.current.numberLarge)
            Text("Average across $loggedCount logged days",style=LocalDeltaType.current.bodySmall)
            val coverage=logged.total(nutrient)
            if(coverage.partial) Text("Partial labels · ${coverage.reported} of ${coverage.count} foods report ${nutrient.label.lowercase()}",style=LocalDeltaType.current.bodySmall)
            if(points.none {it.hasValue}) Text("No reported ${nutrient.label.lowercase()} in this period.",style=LocalDeltaType.current.body)
            else IntakeChart(points,showWeight,selected,weightUnit) {selected=it}
            val detail=points.getOrNull(selected)
            if(detail!=null) {
                HorizontalDivider(color=Color(c.outline))
                Text(detail.start.format(dayFormat)+(if(weekly) " – ${detail.days.last().format(DateTimeFormatter.ofPattern("d MMM"))}" else ""),style=LocalDeltaType.current.title)
                NutritionRow(nutrient.label,if(detail.hasValue) nutrientText(detail.value,nutrient) else "No data",if(weekly) "${detail.loggedDays} of ${detail.days.size} days logged" else "${detail.entries.size} foods")
                if(detail.total.partial) Text("${detail.total.reported} of ${detail.total.count} foods report this nutrient.",style=LocalDeltaType.current.bodySmall)
                if(showWeight) NutritionRow("Weight average",detail.weight?.let {Units.weight(it,weightUnit)} ?: "No measurement")
            }
        }
        ContentSection {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Compare weight",style=LocalDeltaType.current.body);Switch(showWeight,{showWeight=it})
            }
            Text("Intake: left axis (${nutrient.unit}). Weight: right axis (${weightUnit.label}). Unlogged days are gaps; averages include logged days only.",style=LocalDeltaType.current.bodySmall)
        }
        val active=points.filter {it.hasValue}
        if(active.isNotEmpty()) ContentSection {
            Caption("Period insights")
            val highest=active.maxBy {it.value};val lowest=active.minBy {it.value}
            NutritionRow("Highest ${if(weekly) "week" else "day"}",nutrientText(highest.value,nutrient),highest.start.format(dayFormat))
            NutritionRow("Lowest ${if(weekly) "week" else "day"}",nutrientText(lowest.value,nutrient),lowest.start.format(dayFormat))
            val covered=points.sumOf {it.total.reported};val count=points.sumOf {it.total.count}
            Text("$covered of $count foods report ${nutrient.label.lowercase()}.",style=LocalDeltaType.current.bodySmall)
            if(state.targets.target(nutrient)!=null) {
                val target=state.targets.target(nutrient)!!
                val comparison=points.targetComparison(target,nutrient in state.targets.limits)
                Text("${comparison.satisfying} of ${comparison.eligible} fully reported ${if(weekly) "weekly averages" else "logged days"} ${if(nutrient in state.targets.limits) "within your limit" else "reached your target"}.",style=LocalDeltaType.current.bodySmall)
                if(comparison.incomplete>0) Text("${comparison.incomplete} incomplete periods excluded from this comparison.",style=LocalDeltaType.current.bodySmall)
            }
            if(showWeight && weights.isNotEmpty()) Text("Food and weight are shown together for comparison; this does not establish cause and effect.",style=LocalDeltaType.current.bodySmall)
        }
    }
}

@Composable
private fun IntakeChart(points:List<IntakePoint>,showWeight:Boolean,selected:Int,weightUnit:WeightUnit,onSelect:(Int)->Unit) {
    val c=LocalDeltaColors.current;val accent=Color(c.accent);val weightColor=Color(c.accent2);val muted=Color(c.textDim)
    val max=points.maxOfOrNull {if(it.hasValue) it.value else 0.0}?.coerceAtLeast(1.0) ?: 1.0
    val weights=points.mapNotNull {it.weight};val minW=(weights.minOrNull() ?: 0.0)-.5;val maxW=(weights.maxOrNull() ?: 1.0)+.5
    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text("0 – ${amountText(max)} ${points.first().nutrient.unit}",style=LocalDeltaType.current.bodySmall,color=accent)
        if(showWeight && weights.isNotEmpty()) Text("${Units.weightShort(minW,weightUnit)} – ${Units.weightShort(maxW,weightUnit)}",style=LocalDeltaType.current.bodySmall,color=weightColor)
    }
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        val width=maxOf(280,points.size*28).dp
        Canvas(Modifier.width(width).height(190.dp).semantics {contentDescription="${points.size} intake periods; choose a bar for details"}
            .pointerInput(points) {detectTapGestures {offset ->onSelect((offset.x/size.width*points.size).toInt().coerceIn(points.indices))}}) {
            val chartHeight=size.height-12.dp.toPx();val step=size.width/points.size
            drawLine(muted.copy(alpha=.3f),Offset(0f,chartHeight),Offset(size.width,chartHeight))
            points.forEachIndexed {i,p ->
                val x=step*i+step*.2f;val w=step*.6f
                if(p.hasValue) {
                    val h=(p.value/max*chartHeight).toFloat().coerceAtLeast(2f)
                    drawRoundRect(if(i==selected) accent else accent.copy(alpha=.65f),Offset(x,chartHeight-h),Size(w,h),CornerRadius(3.dp.toPx()))
                } else drawCircle(muted.copy(alpha=.3f),2.dp.toPx(),Offset(x+w/2,chartHeight-2.dp.toPx()))
            }
            if(showWeight && weights.isNotEmpty()) {
                var previous:Offset?=null
                points.forEachIndexed {i,p ->
                    if(p.weight==null) previous=null
                    else {
                        val current=Offset(step*(i+.5f),(chartHeight-(p.weight-minW)/(maxW-minW)*chartHeight).toFloat())
                        previous?.let {drawLine(weightColor,it,current,2.dp.toPx(),StrokeCap.Round)}
                        drawCircle(weightColor,3.dp.toPx(),current);previous=current
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(points.first().start.format(DateTimeFormatter.ofPattern("d MMM")),style=LocalDeltaType.current.bodySmall)
        Text(points.last().days.last().format(DateTimeFormatter.ofPattern("d MMM")),style=LocalDeltaType.current.bodySmall)
    }
}
