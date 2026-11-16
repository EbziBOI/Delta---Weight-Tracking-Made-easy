package io.github.ebziboi.delta.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.ebziboi.delta.data.WeightUnit
import io.github.ebziboi.delta.data.WeightEntry
import io.github.ebziboi.delta.ui.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class IntakePoint(val start:LocalDate,val days:List<LocalDate>,val entries:List<DiaryEntry>,val nutrient:Nutrient,val weight:Double?) {
    val total=entries.total(nutrient)
    val loggedDays=entries.map {it.day}.distinct().size
    val reportedDays=entries.filter {nutrient in it.food.per100}.map {it.day}.distinct().size
    val value get()=total.amount / reportedDays.coerceAtLeast(1)
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
    var endOffset by remember {mutableLongStateOf(0)}
    var selected by rememberSaveable {mutableIntStateOf(-1)}
    var menu by remember {mutableStateOf(false)}
    val end=LocalDate.now().plusDays(endOffset)
    val points=remember(state.entries,weights,end,days,weekly,nutrient) {intakePoints(state.entries,weights,end,days,weekly,nutrient)}
    val c=LocalDeltaColors.current
    NutritionPage("Nutrition trends",padding,onBack) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Box {SecondaryButton("${nutrient.label} ▾",{menu=true});DropdownMenu(menu,{menu=false}) { Nutrient.entries.forEach { n ->DropdownMenuItem({Text(n.label)},{nutrient=n;selected=-1;menu=false})} }}
        }
        SegmentedControl(listOf(14,30,90),days,{"$it days"}) {days=it;selected=-1}
        SegmentedControl(listOf(false,true),weekly,{if(it) "Weekly" else "Daily"}) {weekly=it;selected=-1}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
            TextButton({endOffset-=days;selected=-1},enabled=state.entries.minOfOrNull {it.day}?.let {end.minusDays(days.toLong()-1)>it}==true) {Text("Previous")}
            TextButton({endOffset=minOf(0L,endOffset+days);selected=-1},enabled=end<LocalDate.now()) {Text("Next")}
        }
        Text("${end.minusDays(days.toLong()-1).format(DateTimeFormatter.ofPattern("d MMM"))} – ${end.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}",style=LocalDeltaType.current.bodySmall)
        ContentSection(tone=ModuleTone.VIOLET,framed=true) {
            Caption(if(weekly) "Daily average by week" else "Daily intake")
            val available=points.filter {it.hasValue}
            val logged=state.entries.filter {it.day>=end.minusDays(days.toLong()-1) && it.day<=end}
            val loggedCount=logged.filter {nutrient in it.food.per100}.map {it.day}.distinct().size
            ValueWithUnit(if(available.isEmpty()) "—" else amountText(logged.total(nutrient).amount/loggedCount.coerceAtLeast(1)),nutrient.unit,style=LocalDeltaType.current.numberLarge)
            Text("Average across $loggedCount days with reported intake",style=LocalDeltaType.current.bodySmall)
            val coverage=logged.total(nutrient)
            if(coverage.partial) Text("Partial labels · ${coverage.reported} of ${coverage.count} foods report ${nutrient.label.lowercase()}",style=LocalDeltaType.current.bodySmall)
            if(points.none {it.hasValue}) Text("No reported ${nutrient.label.lowercase()} in this period.",style=LocalDeltaType.current.body)
            else IntakeChart(points,nutritionScale(state.entries,end,nutrient)) {selected=it}
            val detail=points.getOrNull(selected)
            if(detail!=null) {
                Text(if(weekly) "${detail.days.first().format(dayFormat)} – ${detail.days.last().format(DateTimeFormatter.ofPattern("d MMM"))} · ${detail.reportedDays} days reported"
                    else "${detail.entries.size} ${if(detail.entries.size==1) "food" else "foods"}",style=LocalDeltaType.current.bodySmall)
                if(detail.total.partial) Text("${detail.total.reported} of ${detail.total.count} foods report this nutrient.",style=LocalDeltaType.current.bodySmall)
            }
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
        }
    }
}

@Composable
private fun IntakeChart(points:List<IntakePoint>,scale:NutritionScale,onSelect:(Int)->Unit) {
    val c=LocalDeltaColors.current
    val values=points.filter {it.hasValue}.map {p->RecordedValue(p.entries.filter {p.nutrient in it.food.per100}.maxOf {it.day},p.value)}
    RecordedChart(listOf(RecordedSeries("intake",points.first().nutrient.label,Color(c.accent),values,
        {nutrientText(it,points.first().nutrient)},bars=true,ceiling=scale.ceiling,typical=scale.typical)),points.first().start,points.last().days.last(),"Daily intake graph") {day->
        onSelect(points.indexOfFirst {day in it.days})
    }
}
