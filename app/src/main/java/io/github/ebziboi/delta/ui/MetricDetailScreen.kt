package io.github.ebziboi.delta.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.render.ChartSpec
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@Composable
fun MetricDetailScreen(vm: AppViewModel, settings: AppSettings, initial: DetailMetric, padding: PaddingValues, onBack: () -> Unit) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val metrics = listOf(DetailMetric.RATE,DetailMetric.AVERAGE,DetailMetric.STREAK)
    val pager = rememberPagerState(initialPage = metrics.indexOf(initial).coerceAtLeast(0), pageCount = {3})
    val scope = rememberCoroutineScope(); val motion = LocalMotion.current
    var weekEpoch by rememberSaveable { mutableStateOf(MetricDetails.weekStart(LocalDate.now(),settings.weekStartsOn).toEpochDay()) }
    var selectedEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top=padding.calculateTopPadding())
        .padding(horizontal=Spacing.screen).padding(bottom=padding.calculateBottomPadding()+8.dp), verticalArrangement=Arrangement.spacedBy(18.dp)) {
        if (initial == DetailMetric.GOAL) {
            ScreenHeader("Your forecast", eyebrow="GOAL BY",leading={TextAction("Back",onBack)})
            snap?.let { ForecastDetail(it,entries,settings) }
        } else {
            ScreenHeader("Your rhythm",eyebrow="PROGRESS DETAILS",leading={TextAction("Back",onBack)})
            SegmentedControl(metrics,metrics[pager.currentPage],{ when(it) { DetailMetric.RATE -> "Rate"; DetailMetric.AVERAGE -> "Average"; else -> "Streak" } },Modifier.fillMaxWidth()) {
                scope.launch { if(motion) pager.animateScrollToPage(metrics.indexOf(it)) else pager.scrollToPage(metrics.indexOf(it)) }
            }
            HorizontalPager(pager,Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top) { page ->
                MetricCalendar(metrics[page],entries,settings,weekEpoch,{weekEpoch=it},selectedEpoch,{selectedEpoch=it}) {index ->
                    weekEpoch=MetricDetails.weekStart(LocalDate.ofEpochDay(selectedEpoch),index).toEpochDay()
                    vm.update {it.copy(weekStartsOn=index)}
                }
            }
        }
    }
}

@Composable
private fun MetricCalendar(metric: DetailMetric, entries: List<WeightEntry>, settings: AppSettings,
                           weekEpoch: Long, onWeek: (Long) -> Unit, selectedEpoch: Long, onDay: (Long) -> Unit,onStart:(Int)->Unit) {
    val today=LocalDate.now();val currentWeek=MetricDetails.weekStart(today,settings.weekStartsOn)
    val first=entries.minOfOrNull { Instant.ofEpochMilli(it.epochMs).atZone(ZoneId.systemDefault()).toLocalDate() } ?: today
    val firstWeek=MetricDetails.weekStart(minOf(first,today),settings.weekStartsOn)
    val week=LocalDate.ofEpochDay(weekEpoch).coerceIn(firstWeek,currentWeek)
    val days=remember(entries,settings,week,today) { MetricDetails.days(entries,settings,week,today) }
    val selected=days.firstOrNull { it.day.toEpochDay()==selectedEpoch && it.day<=today } ?: days.last { it.day<=today }
    val c=LocalDeltaColors.current; val type=LocalDeltaType.current
    fun value(day: MetricDay): String = when(metric) {
        DetailMetric.RATE -> day.rateKg?.let { Units.rate(it,settings.unit) } ?: "—"
        DetailMetric.AVERAGE -> day.averageKg?.let { Units.weightShort(it,settings.unit) } ?: "—"
        else -> "${day.streak} ${if(day.streak==1) "day" else "days"}"
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        ContentSection(tone=ModuleTone.SAGE,framed=true) {
            val panel=LocalDeltaColors.current
            Caption(metric.label)
            if(LocalMotion.current) AnimatedContent(value(selected),label="daily-value") { Text(it,style=type.numberLarge) }
            else Text(value(selected),style=type.numberLarge)
            Text(Format.day(selected.day),style=type.bodySmall,color=Color(panel.textDim))
            Caption("Week starts")
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                listOf(DayOfWeek.SUNDAY,DayOfWeek.MONDAY,DayOfWeek.TUESDAY,DayOfWeek.WEDNESDAY,DayOfWeek.THURSDAY,DayOfWeek.FRIDAY,DayOfWeek.SATURDAY).forEachIndexed {index,day->
                    val active=settings.weekStartsOn==index
                    Box(Modifier.weight(1f).heightIn(min=44.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(if(active) panel.accent else panel.text).copy(alpha=if(active) .24f else .07f))
                        .clickable(role=Role.RadioButton) {onStart(index)}
                        .semantics {this.selected=active;contentDescription="Start week on ${day.getDisplayName(java.time.format.TextStyle.FULL,java.util.Locale.ENGLISH)}"},contentAlignment=Alignment.Center) {
                        Text(day.name.take(1),style=type.bodyStrong,color=Color(if(active) panel.accent else panel.text))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp,Alignment.CenterHorizontally)) {
                TextAction("Previous week",{onWeek(week.minusWeeks(1).toEpochDay());onDay(week.minusWeeks(1).toEpochDay())},enabled=week>firstWeek)
                TextAction("Next week",{onWeek(week.plusWeeks(1).toEpochDay());onDay(minOf(week.plusDays(13),today).toEpochDay())},enabled=week<currentWeek)
            }
            Text("${Format.day(week)} – ${Format.day(week.plusDays(6))}",style=type.bodyStrong)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                days.forEach { day ->
                    val active=day.day==selected.day;val available=day.day<=today
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(Radii.control))
                        .background(if(active) Color(panel.accent).copy(alpha=.22f) else Color(panel.text).copy(alpha=.05f))
                        .clickable(enabled=available,role=Role.Button) { onDay(day.day.toEpochDay()) }
                        .semantics { contentDescription="${Format.day(day.day)}, ${value(day)}";this.selected=active }
                        .padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(day.day.dayOfWeek.name.take(1),style=type.label,color=Color(panel.textDim).copy(alpha=if(available) 1f else .35f))
                        Text(day.day.dayOfMonth.toString(),style=type.bodyStrong,color=Color(panel.text).copy(alpha=if(available) 1f else .35f))
                        Box(Modifier.size(5.dp).background(if(day.readings>0) Color(panel.accent) else Color(panel.textDim).copy(alpha=.2f),androidx.compose.foundation.shape.CircleShape))
                    }
                }
            }
        }
        ContentSection {
            Text(when(metric) { DetailMetric.RATE -> "How your pace is changing";DetailMetric.AVERAGE -> "A clearer daily signal";else -> "Consistency, one day at a time" },style=type.title)
            Text(selected.kg?.let { "${Format.day(selected.day)}: ${Units.weight(it,settings.unit)} · ${selected.readings} ${if(selected.readings==1) "reading" else "readings"}" }
                ?: "No weigh-in on ${Format.day(selected.day)}.",style=type.bodyStrong)
            val past=days.filter {it.day<=today};val recorded=past.filter {it.kg!=null}
            Text("${recorded.size} of ${past.size} elapsed days recorded · ${past.sumOf {it.readings}} measurements",style=type.body)
            if(recorded.isNotEmpty()) Text("Window average ${Units.weight(recorded.map {it.kg!!}.average(),settings.unit)}",style=type.body)
            if(recorded.size>=2) Text("Window change ${Units.signed(recorded.last().kg!!-recorded.first().kg!!,settings.unit)} · ${Format.day(recorded.first().day)} to ${Format.day(recorded.last().day)}",style=type.body)
            Text(when(metric) {
                DetailMetric.RATE -> "28-day fitted rate, using readings available on the selected date. Sparse data uses a longer window."
                DetailMetric.AVERAGE -> "Mean of recorded daily means in the seven days ending on the selected date."
                else -> "Consecutive recorded dates through the selected day. Missing dates break the run. Yesterday’s active run is retained until today ends."
            },style=type.bodySmall,color=Color(c.textDim))
        }
    }
}

@Composable
private fun ForecastDetail(snap: ProgressSnapshot, entries: List<WeightEntry>, settings: AppSettings) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    val today=LocalDate.now();val zone=ZoneId.systemDefault()
    val dayWeights=remember(entries) { InsightsCalculator.distinctReadings(entries).groupBy { Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate() }
        .filterKeys { it<=today }.toSortedMap().mapValues { (_,v) -> v.map { it.kg }.average() } }
    fun recentRate(days:Long):Double? {
        val list=dayWeights.filterKeys { it>=today.minusDays(days-1) }.entries.toList()
        if(list.size<3) return null
        val span=ChronoUnit.DAYS.between(list.first().key,list.last().key)
        return if(span>=6) (list.last().value-list.first().value)/span*7 else null
    }
    val recent=recentRate(7);val fortnight=recentRate(14)
    val recentDate=MetricDetails.project(snap.currentKg,snap.targetKg,recent)
    ContentSection(tone=ModuleTone.SAGE,framed=true) {
        Caption(if(snap.reached) "Goal reached" else "At your recent pace")
        Text(when { snap.reached -> "You made it";snap.targetKg==null -> "Choose a target";else -> snap.projectedDay?.let(Format::day) ?: "No date yet" },style=type.number)
        Text(snap.weeklyRateKg?.let { "${Units.rate(it,settings.unit)} · recent fitted rate" } ?: "More recorded days are needed for a forecast.",style=type.bodySmall)
        if(snap.targetKg!=null) Text("Target ${Units.weight(snap.targetKg,settings.unit)}",style=type.bodyStrong)
    }
    ContentSection(tone=ModuleTone.VIOLET) {
        Text("The path ahead",style=type.title)
        InteractiveChart(ChartSpec.journey(snap,settings.appChart,settings.unit,true).copy(showFullProjection=true),Modifier.fillMaxWidth().height(280.dp))
    }
    ContentSection {
        Text("Recent signals",style=type.title)
        Text(recent?.let { "Last 7 days: ${Units.rate(it,settings.unit)}." } ?: "The last 7 days do not yet contain enough measurements to compare pace.",style=type.body)
        fortnight?.let { Text("Last 14 days: ${Units.rate(it,settings.unit)}.",style=type.body) }
        if(recentDate!=null) Text("If the last week’s pace continued: ${Format.day(recentDate)}. The main forecast uses a longer fitted trend.",style=type.body)
        if(recent!=null && snap.weeklyRateKg!=null && abs(recent-snap.weeklyRateKg)>.2)
            Text("Your latest week differs from the longer trend. A short change can move the estimate sharply; compare several weeks before treating it as a new pace.",style=type.bodyStrong)
        snap.requiredRateKg?.let { Text("Your chosen date needs ${Units.rate(it,settings.unit)} from here.",style=type.body) }
    }
    ContentSection {
        Text("What can skew the estimate",style=type.title)
        val recentDays=dayWeights.keys.count { it>=today.minusDays(27) }
        Text("$recentDays recorded days in the last 28. ${if(recentDays<8) "Sparse measurements make the forecast more sensitive to individual readings." else "More consistently recorded days give a steadier picture."}",style=type.body)
        val last=dayWeights.keys.lastOrNull();val age=last?.let { ChronoUnit.DAYS.between(it,today) }
        if(age!=null && age>1) Text("Your latest reading is $age days old, so the forecast may lag behind recent changes.",style=type.body)
        val times=entries.filter { Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate()>=today.minusDays(27) }.map { Instant.ofEpochMilli(it.epochMs).atZone(zone).hour }
        if(times.isNotEmpty() && times.max()-times.min()>=6) Text("Your recent measurements span different times of day. That can add variation unrelated to the underlying trend.",style=type.body)
        Text("Estimate based on your recorded trend.",style=type.bodySmall,color=Color(c.textDim))
    }
}
