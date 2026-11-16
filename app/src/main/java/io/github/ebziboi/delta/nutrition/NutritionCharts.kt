package io.github.ebziboi.delta.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
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
    var days by rememberSaveable {mutableIntStateOf(30)}
    var endOffset by rememberSaveable {mutableLongStateOf(0)}
    var selected by rememberSaveable {mutableIntStateOf(-1)};var menu by remember {mutableStateOf(false)}
    val end=LocalDate.now().plusDays(endOffset);val start=end.minusDays(days-1L)
    val points=remember(state.entries,end,days,nutrient) {intakePoints(state.entries,emptyList(),end,days,false,nutrient)}
    val logged=state.entries.filter {it.day in start..end};val active=points.filter {it.hasValue}
    val c=LocalDeltaColors.current
    val cards=listOf(CardSlot("intake","Daily intake"),CardSlot("balance","Nutrient balance"),CardSlot("insights","Period insights"),CardSlot("consistency","Logging coverage"),CardSlot("comparison","Recent comparison"),CardSlot("sources","Food contributions"),CardSlot("targets","Target comparison"))
    val (order,saveOrder)=rememberPageCardOrder("nutrition",cards.map {it.id})
    ReorderableCards(cards,order,padding,saveOrder,header={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            ScreenHeader("Nutrition trends",leading={TextAction("Back",onBack,Modifier.semantics {contentDescription="Back"})})
            Box {SecondaryButton("${nutrient.label} ▾",{menu=true});DropdownMenu(menu,{menu=false}) {Nutrient.entries.forEach {n->DropdownMenuItem({Text(n.label)},{nutrient=n;selected=-1;menu=false})}}}
            SegmentedControl(listOf(14,30,90),days,{"$it days"}) {days=it;selected=-1}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
                TextButton({endOffset-=days;selected=-1},enabled=state.entries.minOfOrNull {it.day}?.let {start>it}==true) {Text("Previous")}
                TextButton({endOffset=minOf(0L,endOffset+days);selected=-1},enabled=end<LocalDate.now()) {Text("Next")}
            }
            Text("${start.format(DateTimeFormatter.ofPattern("d MMM"))} – ${end.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}",style=LocalDeltaType.current.bodySmall)
        }
    }) {id->when(id) {
        "intake"->ContentSection(tone=ModuleTone.VIOLET,framed=true) {
            Caption("Daily intake")
            val total=logged.total(nutrient);val reported=active.size
            ValueWithUnit(if(reported==0) "—" else amountText(total.amount/reported),nutrient.unit,style=LocalDeltaType.current.numberLarge)
            Text("Average across $reported days with reported intake",style=LocalDeltaType.current.bodySmall)
            if(total.partial) Text("Partial labels · ${total.reported} of ${total.count} foods report ${nutrient.label.lowercase()}",style=LocalDeltaType.current.bodySmall)
            if(active.isEmpty()) Text("No reported ${nutrient.label.lowercase()} in this period.")
            else IntakeChart(points,nutritionScale(state.entries,end,nutrient)) {selected=it}
            points.getOrNull(selected)?.let {p->Text("${p.entries.size} foods · ${p.total.reported} report this nutrient",style=LocalDeltaType.current.bodySmall)}
        }
        "balance"->NutritionPie(logged)
        "insights"->ContentSection {
            Text("Period insights",style=LocalDeltaType.current.title)
            if(active.isEmpty()) Text("No reported values in this period") else {
                val values=active.map {it.value}.sorted();val median=if(values.size%2==1) values[values.size/2] else (values[values.size/2-1]+values[values.size/2])/2
                val mean=values.average();val sd=kotlin.math.sqrt(values.sumOf {(it-mean)*(it-mean)}/values.size)
                NutritionRow("Total reported",nutrientText(values.sum(),nutrient))
                NutritionRow("Median day",nutrientText(median,nutrient))
                NutritionRow("Day-to-day spread",nutrientText(sd,nutrient),"Standard deviation across reported dates")
                val high=active.maxBy {it.value};val low=active.minBy {it.value}
                NutritionRow("Highest day",nutrientText(high.value,nutrient),high.start.format(dayFormat))
                NutritionRow("Lowest day",nutrientText(low.value,nutrient),low.start.format(dayFormat))
            }
        }
        "consistency"->ContentSection {
            Text("Logging coverage",style=LocalDeltaType.current.title)
            val dates=logged.map {it.day}.distinct();val total=logged.total(nutrient)
            NutritionRow("Dates logged","${dates.size} / $days")
            NutritionRow("Days reporting ${nutrient.label.lowercase()}","${active.size}")
            NutritionRow("Food labels reporting it","${total.reported} / ${total.count}")
            NutritionRow("Imported foods",logged.count {it.fromHealthConnect}.toString(),"Health Connect records in this period")
            Text("Unlogged dates are excluded from averages. Reported days may still contain unlogged food.",style=LocalDeltaType.current.bodySmall)
        }
        "comparison"->ContentSection {
            Text("Recent comparison",style=LocalDeltaType.current.title)
            fun average(from:LocalDate,to:LocalDate)=state.entries.filter {it.day in from..to}.groupBy {it.day}.values.mapNotNull {e->e.total(nutrient).takeIf {it.reported>0}?.amount}.takeIf {it.isNotEmpty()}?.average()
            val now=average(end.minusDays(6),end);val before=average(end.minusDays(13),end.minusDays(7))
            NutritionRow("Last 7 days",nutrientText(now,nutrient),"Average per reported date")
            NutritionRow("Previous 7 days",nutrientText(before,nutrient))
            if(now!=null && before!=null) NutritionRow("Change",nutrientText(now-before,nutrient))
            val weekend=active.filter {it.start.dayOfWeek.value>=6};val weekday=active.filter {it.start.dayOfWeek.value<6}
            if(weekend.isNotEmpty()) NutritionRow("Weekend average",nutrientText(weekend.map {it.value}.average(),nutrient),"${weekend.size} reported dates")
            if(weekday.isNotEmpty()) NutritionRow("Weekday average",nutrientText(weekday.map {it.value}.average(),nutrient),"${weekday.size} reported dates")
        }
        "sources"->ContentSection {
            Text("Food contributions",style=LocalDeltaType.current.title)
            val sources=logged.filter {nutrient in it.food.per100}.groupBy {it.food.name}.mapValues {(_,e)->e.total(nutrient).amount}.entries.sortedByDescending {it.value}.take(5)
            if(sources.isEmpty()) Text("No reported sources in this period")
            sources.forEach {NutritionRow(it.key,nutrientText(it.value,nutrient))}
            Meal.entries.forEach {meal->val e=logged.filter {it.meal==meal};if(e.isNotEmpty()) NutritionRow(meal.label,nutrientText(e.total(nutrient).takeIf {it.reported>0}?.amount,nutrient),"${e.size} foods")}
        }
        "targets"->ContentSection {
            Text("Your targets",style=LocalDeltaType.current.title)
            (state.targets.amounts.keys+if(state.targets.mode==MacroMode.PERCENT) Nutrient.macros else emptyList()).forEach {n->
                val target=state.targets.target(n) ?: return@forEach
                val comparison=intakePoints(logged,emptyList(),end,days,false,n).targetComparison(target,n in state.targets.limits)
                NutritionRow(n.label,"${comparison.satisfying} / ${comparison.eligible}","Fully reported dates ${if(n in state.targets.limits) "within limit" else "reaching target"}; ${comparison.incomplete} incomplete dates excluded")
            }
        }
    }}
}

@Composable
private fun IntakeChart(points:List<IntakePoint>,scale:NutritionScale,onSelect:(Int)->Unit) {
    val c=LocalDeltaColors.current
    val values=points.filter {it.hasValue}.map {p->RecordedValue(p.start,p.value)}
    RecordedChart(listOf(RecordedSeries("intake",points.first().nutrient.label,Color(c.accent),values,
        {nutrientText(it,points.first().nutrient)},bars=true,ceiling=scale.ceiling,typical=scale.typical)),points.first().start,points.last().days.last(),"Daily intake graph",onSelected={day->onSelect(points.indexOfFirst {day in it.days})})
}
