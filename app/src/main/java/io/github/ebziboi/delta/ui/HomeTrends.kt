package io.github.ebziboi.delta.ui

import androidx.compose.animation.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.nutrition.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal enum class HomeSeries(val label:String) {WEIGHT("Weight"),BODY_FAT("Body fat"),NUTRITION("Nutrition")}

@Composable
internal fun HomeTrends(weights:List<WeightEntry>,fat:List<BodyFatEntry>,nutrition:NutritionState,settings:AppSettings,
                        onMetric:(DetailMetric)->Unit,onNutrition:()->Unit={}) {
    val context=LocalContext.current
    val prefs=remember {context.getSharedPreferences("home-chart",0)}
    var chosen by rememberSaveable {mutableStateOf(prefs.getString("series",null)?.split(",")?.mapNotNull {name->HomeSeries.entries.find {it.name==name}}?.toSet() ?: setOf(HomeSeries.WEIGHT))}
    val profile=rememberChartController("home-trends",if(prefs.contains("trend")) {if(prefs.getBoolean("trend",true)) ChartStyle.TREND else ChartStyle.CONNECTED} else settings.appChart)
    val today=LocalDate.now();val zone=ZoneId.systemDefault();val c=LocalDeltaColors.current
    fun day(ms:Long)=Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val weightValues=remember(weights,today) {weights.filter {it.kg.isFinite() && it.kg>0 && day(it.epochMs)<=today}.groupBy {day(it.epochMs)}
        .map {(d,v)->RecordedValue(d,v.map {it.kg}.average())}.sortedBy {it.day}}
    val fatValues=remember(fat,today) {fat.filter {BodyFat.valid(it.percent) && day(it.epochMs)<=today}.groupBy {day(it.epochMs)}
        .map {(d,v)->RecordedValue(d,v.map {it.percent}.average())}.sortedBy {it.day}}
    val foodValues=remember(nutrition.entries,today) {nutrition.entries.filter {it.day<=today}.groupBy {it.day}.mapNotNull {(d,v)->
        v.total(Nutrient.ENERGY).takeIf {it.reported>0}?.let {RecordedValue(d,it.amount,partial=it.partial)}}.sortedBy {it.day}}
    val foodScale=remember(nutrition.entries,today) {nutritionScale(nutrition.entries,today,Nutrient.ENERGY,true)}
    val allSeries=listOf(
        HomeSeries.WEIGHT to RecordedSeries("weight","Weight",Color(c.accent),weightValues,{Units.weight(it,settings.unit)}),
        HomeSeries.BODY_FAT to RecordedSeries("body-fat","Body fat",Color(bodyFatInk(c)),fatValues,{BodyFat.format(it)}),
        HomeSeries.NUTRITION to RecordedSeries("nutrition","Energy",Color(c.warn),foodValues,{nutrientText(it,Nutrient.ENERGY)},bars=true,ceiling=foodScale.ceiling,typical=foodScale.typical))
    val series=allSeries.filter {it.first in chosen}.map {it.second}
    val oldest=allSeries.flatMap {it.second.values}.minOfOrNull {it.day} ?: today.minusDays(29)
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        // Independent toggles live outside the graph card; any combination remains selected.
        Row(Modifier.fillMaxWidth().refractiveGlass(RoundedCornerShape(Radii.control)).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            HomeSeries.entries.forEach {item->
                val active=item in chosen
                val tint=allSeries.first {it.first==item}.second.color
                val fill by animateColorAsState(if(active) tint.copy(alpha=.19f) else Color.Transparent,label="chart-channel")
                Box(Modifier.weight(1f).heightIn(min=44.dp).background(fill,RoundedCornerShape((Radii.control-4.dp).coerceAtLeast(4.dp)))
                    .clip(RoundedCornerShape((Radii.control-4.dp).coerceAtLeast(4.dp))).toggleable(active,role=Role.Checkbox,onValueChange={
                        chosen=if(active) chosen-item else chosen+item
                        prefs.edit().putString("series",chosen.joinToString(",") {it.name}).apply()
                    }).padding(horizontal=3.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
                    Text(item.label,style=LocalDeltaType.current.bodySmall,color=if(active) tint else Color(c.textDim))
                }
            }
        }
        ContentSection(tone=ModuleTone.VIOLET) {
            ChartHeading("Trends",profile)
            ChartWindows(profile)
            when {
                series.isEmpty()->Text("Choose measurements above",style=LocalDeltaType.current.bodySmall)
                series.all {s->s.values.none {it.day<=today}}->Text("No recordings in this period",style=LocalDeltaType.current.bodySmall)
                else->{
                    val workouts by (context.applicationContext as io.github.ebziboi.delta.DeltaApp).energy.workoutDays.collectAsStateWithLifecycle()
                    RecordedChart(series,oldest,today,"Combined trends graph",controller=profile,controls=false,historyStart=oldest,journeyStart=settings.startEpochDay?.let(LocalDate::ofEpochDay),weekStartsOn=settings.weekStartsOn,
                        markers=if(profile.period==ChartPeriod.DAILY) workouts else emptySet())
                }
            }
            if(series.isNotEmpty()) Text("Tap a dot to inspect it · hold and slide to scrub · double-tap for style",style=LocalDeltaType.current.label,color=Color(c.textDim))
            if(series.size>1) Text("Each series uses its own scale",style=LocalDeltaType.current.bodySmall,color=Color(c.textDim))
        }
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            HomeSeries.entries.forEachIndexed {index,item->
                val tint=allSeries.first {it.first==item}.second.color
                Box(Modifier.fillMaxWidth(),contentAlignment=when(index) {0->Alignment.CenterStart;1->Alignment.Center;else->Alignment.CenterEnd}) {
                Row(Modifier.fillMaxWidth(.78f).heightIn(min=(56*androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)).dp)
                    .refractiveGlass(RoundedCornerShape(Radii.control))
                    .clickable(role=Role.Button) {when(item) {HomeSeries.WEIGHT->onMetric(DetailMetric.WEIGHT);HomeSeries.BODY_FAT->onMetric(DetailMetric.BODY_FAT);HomeSeries.NUTRITION->onNutrition()}}
                    .padding(horizontal=14.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    androidx.compose.foundation.Canvas(Modifier.size(12.dp)) {
                        drawPath(androidx.compose.ui.graphics.Path().apply {moveTo(0f,0f);lineTo(size.width,size.height/2);lineTo(0f,size.height);close()},tint)
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Text(when(item) {HomeSeries.WEIGHT->"Weight details";HomeSeries.BODY_FAT->"Body composition details";HomeSeries.NUTRITION->"Nutrition details"},style=LocalDeltaType.current.bodyStrong,color=tint)
                    }
                }
                }
            }
        }
    }
}
