package io.github.ebziboi.delta.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ebziboi.delta.R
import io.github.ebziboi.delta.data.DetailMetric
import io.github.ebziboi.delta.data.ProgressDisplay
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.data.ChartStyle
import io.github.ebziboi.delta.data.HealthAvailability
import io.github.ebziboi.delta.data.HealthState
import io.github.ebziboi.delta.data.Insights
import io.github.ebziboi.delta.data.InsightsCalculator
import io.github.ebziboi.delta.data.Pace
import io.github.ebziboi.delta.data.ProgressSnapshot
import io.github.ebziboi.delta.data.Units
import io.github.ebziboi.delta.data.WeightUnit
import io.github.ebziboi.delta.render.ChartSpec
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** "Journey" is since the start date; "All" is every weigh-in on record, from before the journey too. */
private val RANGES = listOf("1M", "3M", "Journey", "All")
private val EYEBROW: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/**
 * The home tab, built so the first screen answers the questions that matter
 * without scrolling: where you are, how far you have come and have to go, and
 * which way things are heading. The graph, observations and recent weigh-ins follow.
 */
@Composable
fun ProgressScreen(
    vm: AppViewModel,
    settings: AppSettings,
    health: HealthState?,
    padding: PaddingValues,
    onConnect: () -> Unit,
    onOpenSettings: () -> Unit,
    onMetric: (DetailMetric) -> Unit = {},
    onNutrition:()->Unit={},
    onEnergy:(String)->Unit={vm.openFood(if(it=="setup") "energy-setup" else it)},
) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val nutrition by vm.nutrition.collectAsStateWithLifecycle()
    val unit = settings.unit
    val snap = snapshot
    val insights = remember(entries, snap) { snap?.let { InsightsCalculator.compute(entries, it) } }
    val crash by vm.lastCrash.collectAsStateWithLifecycle()
    val cards=buildList {
        if(crash!=null) add(CardSlot("crash","Crash report"))
        if(health!=null && !health.connected) add(CardSlot("connect","Health Connect"))
        if(snap!=null && insights!=null) {
            add(CardSlot("progress","Progress"))
            if(snap.targetKg==null) add(CardSlot("goal_setup","Set target"))
            addAll(listOf(CardSlot("rate","Weekly rate",true),CardSlot("goal","Goal by",true),CardSlot("average","7-day average",true),CardSlot("streak","Streak",true)))
            add(CardSlot("trend","Trends"))
            if(settings.energy.enabled) add(CardSlot("energy","Energy balance"))
            add(CardSlot("bmi","BMI"))
            add(CardSlot("insights","Insights"))
            if(insights.recent.isNotEmpty()) add(CardSlot("recent","Recent weigh-ins"))
        }
    }
    ReorderableCards(cards,settings.homeCardOrder,padding,
        onOrderChange={order->vm.update {it.copy(homeCardOrder=io.github.ebziboi.delta.data.CardLayouts.mergeVisible(it.homeCardOrder,order,io.github.ebziboi.delta.data.CardLayouts.home))}},
        header={ScreenHeader(title="Progress",eyebrow=LocalDate.now().format(EYEBROW))}) {id->
        when(id) {
            "crash" -> crash?.let {CrashCard(it,vm::dismissCrash)}
            "connect" -> health?.let {ConnectCard(it,onConnect)}
            else -> if(snap!=null && insights!=null) when(id) {
                "progress" -> HeroCard(snap,insights,unit,Modifier,settings)
                "goal_setup" -> ContentSection {Text("Set a target weight",style=LocalDeltaType.current.title);PrimaryButton("Open settings",onOpenSettings)}
                "rate","goal","average","streak" -> KeyFigure(id,snap,insights,unit,onMetric)
                "trend" -> HomeTrends(entries,snap.bodyFat,nutrition,settings,onMetric,onNutrition)
                "energy" -> io.github.ebziboi.delta.nutrition.HomeEnergyCard {onEnergy("energy")}
                "bmi" -> io.github.ebziboi.delta.nutrition.HomeBmiCard(snap.currentKg,{onEnergy("energy")},{onEnergy("setup")})
                "insights" -> InsightsCard(snap,entries,settings,Modifier,onMetric)
                "recent" -> RecentCard(insights,snap.losing,unit,Modifier)
            }
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

/** Offers to share what went wrong the last time Delta stopped, so it can be fixed. */
@Composable
private fun CrashCard(details: String, onDismiss: () -> Unit) {
    val colors = LocalDeltaColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    ContentSection(accent = Color(colors.warn)) {
        Text("Delta closed unexpectedly", style = LocalDeltaType.current.title)
        Text("The details stay on your phone. Sharing them helps find and fix the cause.",
            color = Color(colors.textDim), style = LocalDeltaType.current.body)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Share details", {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_SUBJECT, "Delta crash details")
                    .putExtra(android.content.Intent.EXTRA_TEXT, details)
                runCatching { context.startActivity(android.content.Intent.createChooser(send, "Share crash details")) }
                onDismiss()
            })
            SecondaryButton("Dismiss", onDismiss)
        }
    }
}

@Composable
private fun ConnectCard(health: HealthState, onConnect: () -> Unit) {
    val colors = LocalDeltaColors.current
    ContentSection(accent = Color(colors.warn)) {
        Text("Connect Health Connect", style = LocalDeltaType.current.title)
        Text(
            text = when (health.availability) {
                HealthAvailability.AVAILABLE -> "Sync your measurements from Health Connect."
                HealthAvailability.NEEDS_UPDATE -> "Health Connect needs an update before Delta can read your weight."
                HealthAvailability.UNAVAILABLE -> "Health Connect isn't available on this phone."
            },
            color = Color(colors.textDim), style = LocalDeltaType.current.body,
        )
        if (health.availability == HealthAvailability.AVAILABLE) PrimaryButton("Allow access", onConnect)
    }
}

/** Where you are now, the week's change, the delta, and the journey from start to target. */
@Composable
internal fun HeroCard(snap: ProgressSnapshot, insights: Insights, unit: WeightUnit, modifier: Modifier, settings: AppSettings) {
    val colors = LocalDeltaColors.current
    val type = LocalDeltaType.current
    val withArt = settings.progressDisplay in listOf(ProgressDisplay.COMPLICATION, ProgressDisplay.BOTH) && snap.fraction != null
    ContentSection(modifier.cardPerimeter(settings.progressDisplay == ProgressDisplay.PERIMETER,snap.fraction),
        accent = Color(colors.accent), spacing = 10.dp, tone = ModuleTone.SAGE, framed = true) {
        val panel = LocalDeltaColors.current
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val artSize = (maxWidth * .40f).coerceIn(104.dp, 148.dp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Caption("Now")
                    if (snap.currentKg == null) Text("No weigh-ins yet", style = type.title)
                    else {
                        HeroWeight(snap.currentKg, unit, type.numberLarge.copy(fontSize = 44.sp, lineHeight = 48.sp))
                        snap.currentMs?.let {
                            Text(Format.moment(it), color = Color(panel.textDim), style = type.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        insights.weekChangeKg?.let { change ->
                            val tint = if (abs(change) < .05) panel.textDim else if (InsightsCalculator.towardGoal(change,snap.losing)) panel.good else panel.warn
                            Text("${Units.signed(change,unit)} this week", color = Color(tint), style = type.bodySmall)
                        }
                    }
                }
                if (withArt) PercentageComplication(snap.fraction, settings.percentageDesign, Modifier.size(artSize))
                else if(settings.progressDisplay==ProgressDisplay.PERIMETER) Column(Modifier.size(artSize),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                    RollingText(snap.fraction?.let {"${(it*100).roundToInt()}%"} ?: "—",type.numberLarge.copy(fontSize=34.sp),color=Color(panel.accent))
                    Caption("of goal")
                }
            }
        }
        snap.bodyFat.lastOrNull()?.let { reading ->
            Text("Body fat ${io.github.ebziboi.delta.data.BodyFat.format(reading.percent)}", style = type.bodySmall, color = Color(panel.textDim))
        }
        if (snap.targetKg != null && snap.startKg != null) {
            if (settings.progressDisplay in listOf(ProgressDisplay.BAR,ProgressDisplay.BOTH)) ProgressRail(snap.fraction,barStyle=settings.progressBarStyle)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Caption("Start")
                    Text(Units.weightShort(snap.startKg, unit), style = type.numberSmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Caption("Target")
                    Text(Units.weightShort(snap.targetKg, unit), style = type.numberSmall, color = Color(panel.accent))
                }
            }
            val summary = buildList {
                snap.changeKg?.let { add("${Units.amount(it, unit)} ${if (it <= 0) "lost" else "gained"}") }
                if (snap.reached) add("Target reached") else snap.remainingKg?.let { add("${Units.amount(it, unit)} to go") }
                if (!withArt && settings.progressDisplay!=ProgressDisplay.PERIMETER) snap.fraction?.let { add("${(it * 100).roundToInt()}%") }
            }.joinToString("  ·  ")
            Text(summary, style = type.bodySmall, color = Color(panel.textDim))
        }
    }
}

/**
 * The current weight, as large as the space allows. Stone and pounds show both
 * figures large with small units ("14 st 13.1 lb"), and the whole line shrinks
 * step by step only as far as it must to fit on one line.
 */
@Composable
private fun HeroWeight(kg: Double, unit: WeightUnit, style: androidx.compose.ui.text.TextStyle) {
    val colors = LocalDeltaColors.current
    val small = LocalDeltaType.current.label.toSpanStyle().merge(androidx.compose.ui.text.SpanStyle(color = Color(colors.textDim)))
    val text = androidx.compose.ui.text.buildAnnotatedString {
        if (unit == WeightUnit.ST_LB) {
            val (stone, pounds) = Units.stoneParts(kg)
            append(stone.toString())
            pushStyle(small); append(" st  "); pop()
            append(String.format(Locale.US, "%.1f", pounds))
            pushStyle(small); append(" lb"); pop()
        } else {
            append(Units.weightValue(kg, unit))
            pushStyle(small); append(" ${Units.suffix(unit)}"); pop()
        }
    }
    androidx.compose.foundation.text.BasicText(
        text = text,
        style = style.copy(color = Color(colors.text)),
        maxLines = 1,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
            minFontSize = 22.sp, maxFontSize = style.fontSize, stepSize = 1.sp,
        ),
    )
}

/** A change with a sign and a colour: green towards the target, amber away from it. */
@Composable
private fun ChangePill(changeKg: Double, label: String, losing: Boolean, unit: WeightUnit) {
    val colors = LocalDeltaColors.current
    val tint = when {
        abs(changeKg) < 0.05 -> colors.textDim
        InsightsCalculator.towardGoal(changeKg, losing) -> colors.good
        else -> colors.warn
    }
    val icon = when {
        abs(changeKg) < 0.05 -> R.drawable.ic_trending_flat
        changeKg < 0 -> R.drawable.ic_trending_down
        else -> R.drawable.ic_trending_up
    }
    Pill("${Units.signed(changeKg, unit)} $label", Color(tint), icon = icon)
}

/** The four numbers worth checking every day, in a two-by-two grid. */
@Composable
private fun KeyFigure(id:String,snap: ProgressSnapshot, insights: Insights, unit: WeightUnit, onMetric: (DetailMetric) -> Unit) {
    val colors = LocalDeltaColors.current
    val rate = snap.weeklyRateKg
    val rateTint = when {
        rate == null || abs(rate) < 0.02 -> Color.Unspecified
        InsightsCalculator.towardGoal(rate, snap.losing) -> Color(colors.good)
        else -> Color(colors.warn)
    }
    val goal = when {
        snap.reached -> "Reached"
        snap.targetKg == null -> "No target"
        else -> snap.projectedDay?.let { Format.day(it) } ?: "Not yet"
    }
    val goalNote = when (snap.pace) {
        Pace.AHEAD -> "Ahead of your date"
        Pace.ON_TRACK -> "On track for your date"
        Pace.BEHIND -> "Behind your date"
        null -> if (snap.projectedDay != null) "At the current rate" else "Trend isn't heading there"
    }
    val average = insights.average7Kg
    val previous = insights.averagePrevious7Kg
    val averageNote = if (average != null && previous != null) "${Units.signed(average - previous, unit)} vs last week" else "Last 7 days"
    val streak = snap.streakDays

    when(id) {
        "rate" -> StatTile("Weekly rate",rate?.let {Units.rate(it,unit)} ?: "—",Modifier.fillMaxWidth(),valueColor=rateTint,
            note=if(rate==null) "Needs a week of data" else "Recent trend",icon=R.drawable.ic_speed,onClick={onMetric(DetailMetric.RATE)})
        "goal" -> StatTile("Goal by",goal,Modifier.fillMaxWidth(),note=goalNote,icon=R.drawable.ic_flag,onClick={onMetric(DetailMetric.GOAL)})
        "average" -> StatTile("7-day average",average?.let {Units.weightShort(it,unit)} ?: "—",Modifier.fillMaxWidth(),note=averageNote,icon=R.drawable.ic_insights,onClick={onMetric(DetailMetric.AVERAGE)})
        "streak" -> StatTile("Streak",when(streak) {0->"—";1->"1 day";else->"$streak days"},Modifier.fillMaxWidth(),note="${insights.daysWeighedThisWeek} of the last 7 days",icon=R.drawable.ic_local_fire_department,onClick={onMetric(DetailMetric.STREAK)})
    }
}

/** Home and detail pages derive their observations from the same recorded days. */
@Composable
private fun InsightsCard(snap:ProgressSnapshot,entries:List<io.github.ebziboi.delta.data.WeightEntry>,settings:AppSettings,modifier:Modifier,onMetric:(DetailMetric)->Unit) {
    val today=LocalDate.now()
    val weights=remember(entries) {io.github.ebziboi.delta.data.MeasurementDetails.readings(entries,emptyList(),io.github.ebziboi.delta.data.MeasurementKind.WEIGHT)}
    val fat=remember(snap.bodyFat) {io.github.ebziboi.delta.data.MeasurementDetails.readings(emptyList(),snap.bodyFat,io.github.ebziboi.delta.data.MeasurementKind.BODY_FAT)}
    val items=remember(weights,fat,settings.unit,today) {buildList {
        val weightReport=io.github.ebziboi.delta.data.MeasurementDetails.report(io.github.ebziboi.delta.data.MeasurementKind.WEIGHT,weights,today.minusDays(29),today)
        measurementInsights(weightReport,settings.unit).filter {it.id in listOf("change","comparison","coverage")}.forEach {add(it to DetailMetric.WEIGHT)}
        val fatReport=io.github.ebziboi.delta.data.MeasurementDetails.report(io.github.ebziboi.delta.data.MeasurementKind.BODY_FAT,fat,today.minusDays(29),today)
        measurementInsights(fatReport,settings.unit).filter {it.id in listOf("change","average")}.take(1).forEach {add(it to DetailMetric.BODY_FAT)}
    }}
    ContentSection(modifier,onClick={onMetric(DetailMetric.INSIGHTS)}) {
        Text("Insights",style=LocalDeltaType.current.title)
        if(items.isEmpty()) Text("No measurements in the last 30 days",style=LocalDeltaType.current.bodySmall)
        items.forEach {(item,route)->
            ListRow(onClick={onMetric(route)}) {
                Column(Modifier.weight(1f)) {
                    Text("${if(route==DetailMetric.BODY_FAT) "Body fat · " else ""}${item.title}",style=LocalDeltaType.current.bodySmall,color=Color(LocalDeltaColors.current.textDim))
                    Text(item.value,style=LocalDeltaType.current.bodyStrong)
                    Text(item.detail,style=LocalDeltaType.current.bodySmall,color=Color(LocalDeltaColors.current.textDim))
                }
                }
        }
        TextAction("All insights",{onMetric(DetailMetric.INSIGHTS)})
    }
}

/** The last few weigh-ins with how each moved from the one before. */
@Composable
private fun RecentCard(insights: Insights, losing: Boolean, unit: WeightUnit, modifier: Modifier) {
    val colors = LocalDeltaColors.current
    val type = LocalDeltaType.current
    ContentSection(modifier, spacing = 10.dp) {
        Text("Recent weigh-ins", style = type.title)
        insights.recent.forEachIndexed { index, reading ->
            if (index > 0) GlassDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Units.weight(reading.kg, unit), style = type.number, color = Color(colors.text))
                    Text(Format.moment(reading.epochMs), style = type.bodySmall, color = Color(colors.textDim))
                }
                reading.changeKg?.let { change ->
                    val tint = when {
                        abs(change) < 0.05 -> colors.textDim
                        InsightsCalculator.towardGoal(change, losing) -> colors.good
                        else -> colors.warn
                    }
                    Text(Units.signed(change, unit), style = type.numberSmall, color = Color(tint))
                }
            }
        }
    }
}
