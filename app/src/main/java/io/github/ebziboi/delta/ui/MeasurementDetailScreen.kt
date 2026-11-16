package io.github.ebziboi.delta.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.render.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal fun measurementValue(value: Double,kind: MeasurementKind,unit: WeightUnit):String =
    if(kind==MeasurementKind.WEIGHT) Units.weight(value,unit) else BodyFat.format(value)
internal fun measurementChange(value: Double,kind: MeasurementKind,unit: WeightUnit):String =
    if(kind==MeasurementKind.WEIGHT) Units.signed(value,unit) else String.format(Locale.getDefault(),"%+.2f pp",value)

data class MeasurementInsight(val id:String,val title:String,val value:String,val detail:String,val calculation:String)
internal fun measurementInsights(report: MeasurementReport,unit: WeightUnit):List<MeasurementInsight> {
    val r=report;val daily=r.daily;val values=daily.values.toList()
    if(values.isEmpty()) return emptyList()
    fun v(value:Double)=measurementValue(value,r.kind,unit)
    fun delta(value:Double)=measurementChange(value,r.kind,unit)
    val first=daily.keys.first();val last=daily.keys.last()
    val count="${r.readings.size} readings across ${daily.size} recorded days"
    val range="${Format.day(r.from)} – ${Format.day(r.through)}"
    return buildList {
        add(MeasurementInsight("average","Daily average",v(r.average!!),count,
            "$range. Each day contributes its mean once. ${daily.size} daily means total ${String.format(Locale.getDefault(),"%.2f",values.sum())} ${if(r.kind==MeasurementKind.WEIGHT) "kg" else "percentage points"}, divided by ${daily.size}. Unrecorded days are excluded."))
        r.change?.let {add(MeasurementInsight("change","Recorded change",delta(it),"${Format.day(first)} → ${Format.day(last)}",
            "${v(values.first())} to ${v(values.last())}, using daily means. This compares the first and last recorded days in the selected period; it is not a change across missing dates."))}
        r.previousAverage?.let {previous->add(MeasurementInsight("comparison","Previous period",delta(r.average!!-previous),"Current ${v(r.average!!)} · previous ${v(previous)}",
            "The preceding ${r.coverageDays} calendar days contain ${r.previousDaily.size} recorded days. Both periods give each recorded day equal weight; missing days are not filled in."))}
        add(MeasurementInsight("coverage","Recording coverage","${daily.size} / ${r.coverageDays} days","${(daily.size.toDouble()/r.coverageDays*100).roundToInt()}% of dates have a record",
            "$range. $count. Days without measurements contribute neither zero nor an estimated measurement."))
        r.weeklyRate?.let {rate->add(MeasurementInsight("pace","Period pace",if(r.kind==MeasurementKind.WEIGHT) Units.rate(rate,unit) else "${delta(rate)}/week",
            "Fitted across ${java.time.temporal.ChronoUnit.DAYS.between(first,last)} days",
            "A least-squares line through the ${daily.size} daily means, expressed per seven days. This period-specific fit differs from the home card’s recent weighted trend. It describes the recorded period and does not predict a future measurement."))}
        val low=daily.minBy {it.value};val high=daily.maxBy {it.value}
        add(MeasurementInsight("range","Recorded range","${v(low.value)} – ${v(high.value)}","Low ${Format.day(low.key)} · high ${Format.day(high.key)}",
            "Range of daily means: ${delta(high.value-low.value)}. Individual readings range from ${v(r.readings.minOf {it.value})} to ${v(r.readings.maxOf {it.value})}."))
        r.deviation?.takeIf {daily.size>=2}?.let {sd->add(MeasurementInsight("variation","Day-to-day variation",if(r.kind==MeasurementKind.WEIGHT) Units.amount(sd,unit) else String.format(Locale.getDefault(),"%.2f pp",sd),
            "Standard deviation of ${daily.size} daily means",
            "Calculated as the square root of the mean squared distance from ${v(r.average!!)}. It includes both an underlying change and short-term variation; it does not identify the cause of either."))}
        add(MeasurementInsight("consistency","Recording rhythm","${r.longestStreak}-day longest run","${r.largestGap} unrecorded days in the largest internal gap",
            "Consecutive calendar dates with at least one record, within $range. Only gaps between recorded dates are counted; the dates before the first or after the last record are excluded."))
        add(MeasurementInsight("latest","First and latest",v(values.last()),"${Format.day(first)} → ${Format.day(last)}",
            "First recorded daily mean ${v(values.first())}; latest recorded daily mean ${v(values.last())}. The latest individual measurement is ${v(r.readings.last().value)} at ${Format.moment(r.readings.last().epochMs)}."))
    }
}

@Composable
internal fun MeasurementInsightCards(report:MeasurementReport,unit:WeightUnit) {
    val items=remember(report,unit) {measurementInsights(report,unit)}
    if(items.isEmpty()) ContentSection {Text("No measurements in this period",style=LocalDeltaType.current.title)}
    items.forEach {item->key(item.id) {MeasurementInsightCard(item,report.kind)}}
}

@Composable
private fun MeasurementInsightCard(item:MeasurementInsight,kind:MeasurementKind) {
    var expanded by rememberSaveable(item.id,kind) {mutableStateOf(false)}
    ContentSection(Modifier.semantics {contentDescription="Insight ${item.title}";stateDescription=if(expanded) "Expanded" else "Collapsed"},onClick={expanded=!expanded}) {
        val c=LocalDeltaColors.current;val type=LocalDeltaType.current
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text(item.title,style=type.bodyStrong,modifier=Modifier.weight(1f))
            Text(if(expanded) "−" else "+",style=type.bodyStrong,color=Color(c.accent))
        }
        Text(item.value,style=type.numberSmall)
        Text(item.detail,style=type.bodySmall,color=Color(c.textDim))
        if(expanded) Text(item.calculation,style=type.body)
    }
}

@Composable
fun MeasurementDetailScreen(vm:AppViewModel,settings:AppSettings,kind:MeasurementKind,padding:PaddingValues,onBack:()->Unit,onAccess:()->Unit) {
    val weights by vm.entries.collectAsStateWithLifecycle();val fat by vm.bodyFat.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    var logging by remember {mutableStateOf(false)}
    val zone=ZoneId.systemDefault();val today=LocalDate.now()
    val weightReadings=remember(weights) {MeasurementDetails.readings(weights,emptyList(),MeasurementKind.WEIGHT)}
    val fatReadings=remember(fat) {MeasurementDetails.readings(emptyList(),fat,MeasurementKind.BODY_FAT)}
    val data=if(kind==MeasurementKind.WEIGHT) weightReadings else fatReadings
    val weightDays=remember(weightReadings) {MeasurementDetails.grouped(weightReadings,zone)}
    val fatDays=remember(fatReadings) {MeasurementDetails.grouped(fatReadings,zone)}
    var selectedEpoch by rememberSaveable(kind) {mutableStateOf(data.lastOrNull()?.let {Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate().toEpochDay()} ?: today.toEpochDay())}
    val selected=LocalDate.ofEpochDay(selectedEpoch).coerceAtMost(today)
    var monthEpoch by rememberSaveable(kind) {mutableStateOf(selected.withDayOfMonth(1).toEpochDay())}
    val month=YearMonth.from(LocalDate.ofEpochDay(monthEpoch))
    var range by rememberSaveable(kind) {mutableStateOf("30 days")}
    val earliest=data.firstOrNull()?.let {Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate()} ?: selected
    val from=when(range) {"7 days"->selected.minusDays(6);"30 days"->selected.minusDays(29);else->minOf(settings.startEpochDay?.let(LocalDate::ofEpochDay) ?: earliest,selected)}
    val report=remember(data,from,selected) {MeasurementDetails.report(kind,data,from,selected,zone)}
    val insightItems=remember(report,settings.unit) {measurementInsights(report,settings.unit)}
    val byId=insightItems.associateBy {"insight-${it.id}"}
    val cards=listOf(CardSlot("calendar","Calendar"),CardSlot("readings","Selected measurements"),CardSlot("graph","Trend graph")) +
        insightItems.map {CardSlot("insight-${it.id}",it.title)} + if(insightItems.isEmpty()) listOf(CardSlot("empty","Measurement insights")) else emptyList()
    val saved=if(kind==MeasurementKind.WEIGHT) settings.weightCardOrder else settings.bodyFatCardOrder
    ReorderableCards(cards,saved,padding,onOrderChange={order->vm.update {
        if(kind==MeasurementKind.WEIGHT) it.copy(weightCardOrder=CardLayouts.mergeVisible(it.weightCardOrder,order,CardLayouts.measurement))
        else it.copy(bodyFatCardOrder=CardLayouts.mergeVisible(it.bodyFatCardOrder,order,CardLayouts.measurement))
    }},header={
        ScreenHeader(if(kind==MeasurementKind.WEIGHT) "Weight trend" else "Body composition",leading={TextAction("Back",onBack)},trailing={
            if(kind==MeasurementKind.BODY_FAT) GlassIconButton(io.github.ebziboi.delta.R.drawable.ic_add,"Log body fat",{if(health?.canWriteBodyFat==true) logging=true else onAccess()},size=40.dp)
        })
    }) {id->
        when(id) {
            "calendar" -> MeasurementCalendar(month,selected,weightDays,fatDays,today,{monthEpoch=it.atDay(1).toEpochDay();selectedEpoch=minOf(it.atDay(minOf(selected.dayOfMonth,it.lengthOfMonth())),today).toEpochDay()}) {selectedEpoch=it.toEpochDay()}
            "readings" -> ContentSection(framed=true,spacing=6.dp) {
                val c=LocalDeltaColors.current;val type=LocalDeltaType.current
                Text(Format.day(selected),style=type.bodyStrong)
                val primary=if(kind==MeasurementKind.WEIGHT) weightDays[selected].orEmpty() else fatDays[selected].orEmpty()
                if(primary.isEmpty()) Text("No ${kind.label.lowercase()} reading",style=type.bodySmall,color=Color(c.textDim))
                val ordered=if(kind==MeasurementKind.WEIGHT) listOf(kind to weightDays[selected].orEmpty(),MeasurementKind.BODY_FAT to fatDays[selected].orEmpty())
                    else listOf(kind to fatDays[selected].orEmpty(),MeasurementKind.WEIGHT to weightDays[selected].orEmpty())
                ordered.forEach {(metric,records)->records.forEach {reading->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("${metric.label} ${measurementValue(reading.value,metric,settings.unit)}",style=type.bodyStrong,modifier=Modifier.weight(1f))
                        Text(Instant.ofEpochMilli(reading.epochMs).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")),style=type.bodySmall,color=Color(c.textDim))
                    }
                }}
            }
            "graph" -> ContentSection(tone=if(kind==MeasurementKind.WEIGHT) ModuleTone.VIOLET else ModuleTone.SAGE) {
                Caption("${Format.day(from)} – ${Format.day(selected)}")
                SegmentedControl(listOf("7 days","30 days","Journey"),range,{it}) {range=it}
                if(report.readings.size>=2) {
                    val points=remember(report.readings) {ProgressCalculator.measurementTrend(report.readings)}
                    val style=if(kind==MeasurementKind.WEIGHT) settings.appChart else settings.bodyFatChart
                    InteractiveChart(ChartSpec(points,style,settings.unit,if(kind==MeasurementKind.WEIGHT) settings.targetKg else settings.targetBodyFat,
                        metric=if(kind==MeasurementKind.WEIGHT) ChartMetric.WEIGHT else ChartMetric.BODY_FAT),Modifier.height(220.dp),onDoubleTap={vm.update {
                        if(kind==MeasurementKind.WEIGHT) it.copy(appChart=if(it.appChart==ChartStyle.TREND) ChartStyle.CONNECTED else ChartStyle.TREND)
                        else it.copy(bodyFatChart=if(it.bodyFatChart==ChartStyle.TREND) ChartStyle.CONNECTED else ChartStyle.TREND)
                    }})
                } else Text(if(report.readings.isEmpty()) "No measurements in this period" else "One recorded measurement",style=LocalDeltaType.current.title)
            }
            "empty" -> ContentSection {Text("No measurements in this period",style=LocalDeltaType.current.title)}
            else -> byId[id]?.let {MeasurementInsightCard(it,kind)}
        }
    }
    if(logging) PercentDialog("Log body fat",fatReadings.lastOrNull()?.value,{logging=false}) {value,at->logging=false;vm.logBodyFat(value,at)}
}

@Composable
private fun MeasurementCalendar(month:YearMonth,selected:LocalDate,weight:Map<LocalDate,List<MeasurementReading>>,fat:Map<LocalDate,List<MeasurementReading>>,
                                today:LocalDate,onMonth:(YearMonth)->Unit,onDay:(LocalDate)->Unit) {
    val first=(weight.keys+fat.keys).minOrNull() ?: today
    ContentSection(framed=true,spacing=10.dp) {
        val c=LocalDeltaColors.current;val type=LocalDeltaType.current
        Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),style=type.title)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(18.dp,Alignment.CenterHorizontally)) {
            TextAction("Previous month",{onMonth(month.minusMonths(1))},enabled=month>YearMonth.from(first))
            TextAction("Next month",{onMonth(month.plusMonths(1))},enabled=month<YearMonth.from(today))
        }
        Row(Modifier.fillMaxWidth()) {listOf("S","M","T","W","T","F","S").forEach {Text(it,Modifier.weight(1f),style=type.label,textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=Color(c.textDim))}}
        val offset=month.atDay(1).dayOfWeek.value%7
        val rows=(offset+month.lengthOfMonth()+6)/7
        repeat(rows) {row->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                repeat(7) {column->
                    val number=row*7+column-offset+1
                    if(number !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(44.dp)) else {
                        val date=month.atDay(number);val enabled=date<=today;val active=date==selected
                        Column(Modifier.weight(1f).heightIn(min=44.dp).clip(RoundedCornerShape(Radii.control))
                            .background(if(active) Color(c.accent).copy(alpha=.2f) else Color.Transparent)
                            .clickable(enabled=enabled,role=Role.Button) {onDay(date)}
                            .semantics {this.selected=active;contentDescription="${date}, ${weight[date]?.size ?: 0} weight readings, ${fat[date]?.size ?: 0} body fat readings"}
                            .padding(vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(number.toString(),style=type.bodySmall,color=Color(c.text).copy(alpha=if(enabled) 1f else .3f))
                            Row(horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                                Box(Modifier.size(4.dp).background(if(weight[date].isNullOrEmpty()) Color.Transparent else Color(c.accent),CircleShape))
                                Box(Modifier.size(5.dp).rotate(45f).background(if(fat[date].isNullOrEmpty()) Color.Transparent else Color(c.accent2),RoundedCornerShape(1.dp)))
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {listOf("Weight" to c.accent,"Body fat" to c.accent2).forEach {(name,color)->
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(5.dp).then(if(name=="Body fat") Modifier.rotate(45f) else Modifier).background(Color(color),if(name=="Body fat") RoundedCornerShape(1.dp) else CircleShape))
                Text(name,style=type.bodySmall)
            }
        }}
    }
}

@Composable
fun InsightsDetailScreen(vm:AppViewModel,settings:AppSettings,padding:PaddingValues,onBack:()->Unit,onOpen:(DetailMetric)->Unit) {
    val weights by vm.entries.collectAsStateWithLifecycle();val fat by vm.bodyFat.collectAsStateWithLifecycle()
    var kind by rememberSaveable {mutableStateOf(MeasurementKind.WEIGHT)}
    var range by rememberSaveable {mutableStateOf("30 days")}
    val today=LocalDate.now();val zone=ZoneId.systemDefault()
    val readings=remember(weights,fat,kind) {MeasurementDetails.readings(weights,fat,kind)}
    val earliest=readings.firstOrNull()?.let {Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate()} ?: today
    val from=when(range) {"7 days"->today.minusDays(6);"30 days"->today.minusDays(29);else->minOf(settings.startEpochDay?.let(LocalDate::ofEpochDay) ?: earliest,today)}
    val report=remember(readings,from,today) {MeasurementDetails.report(kind,readings,from,today,zone)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top=padding.calculateTopPadding(),bottom=padding.calculateBottomPadding()+8.dp)
        .padding(horizontal=Spacing.screen),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        ScreenHeader("Insights",leading={TextAction("Back",onBack)})
        SegmentedControl(MeasurementKind.entries,kind,{it.label}) {kind=it}
        SegmentedControl(listOf("7 days","30 days","Journey"),range,{it}) {range=it}
        ContentSection(tone=ModuleTone.SAGE,framed=true,onClick={onOpen(if(kind==MeasurementKind.WEIGHT) DetailMetric.WEIGHT else DetailMetric.BODY_FAT)}) {
            Caption("${Format.day(from)} – ${Format.day(today)}")
            Text("${report.readings.size} measurements",style=LocalDeltaType.current.numberSmall)
            TextAction("Open calendar",{onOpen(if(kind==MeasurementKind.WEIGHT) DetailMetric.WEIGHT else DetailMetric.BODY_FAT)})
        }
        MeasurementInsightCards(report,settings.unit)
    }
}
