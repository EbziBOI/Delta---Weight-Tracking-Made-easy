package io.github.ebziboi.delta.nutrition

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.ui.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

private fun kcal(v:Double)=java.text.DecimalFormat("#,##0").format(Math.round(v))

@Composable internal fun rememberEnergyBalance():State<EnergyBalance?> {
    val app=LocalContext.current.applicationContext as DeltaApp
    return app.energy.balance.collectAsStateWithLifecycle()
}

@Composable internal fun rememberEnergyDays():State<List<DayBudget>> {
    val app=LocalContext.current.applicationContext as DeltaApp
    return app.energy.days.collectAsStateWithLifecycle()
}

/**
 * The selected day's budget on the Diary: what's left (or how it went), maintenance and that day's activity.
 * Tapping opens the breakdown.
 */
@Composable
internal fun EnergyCard(balance:EnergyBalance?,day:DayBudget?,enabled:Boolean,eaten:Double?,onOpen:()->Unit,onSetUp:()->Unit) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    if(!enabled) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.control)).background(Color(c.text).copy(alpha=.05f)).clickable(role=Role.Button,onClick=onSetUp)
            .padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            DeltaIcon(io.github.ebziboi.delta.R.drawable.ic_local_fire_department,null,tint=Color(c.accent),size=20.dp)
            Column(Modifier.weight(1f)) {
                Text("Work out your calorie budget",style=type.bodyStrong)
                Text("From your weight, goal and activity",style=type.label,color=Color(c.textDim))
            }
            DeltaIcon(io.github.ebziboi.delta.R.drawable.ic_chevron_right,null,tint=Color(c.textDim),size=18.dp)
        }
        return
    }
    ContentSection(onClick=onOpen,tone=ModuleTone.VIOLET) {
        if(balance==null) {
            Caption("Energy budget");Text("Log a weigh-in to work out your budget.",style=type.bodySmall,color=Color(c.textDim));return@ContentSection
        }
        val today=day==null || day.isToday
        val budget=day?.budgetKcal ?: balance.budgetKcal
        val maintenance=day?.maintenanceKcal ?: balance.maintenanceKcal
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Caption(if(today) "Energy budget" else "Budget that day")
                ValueWithUnit(kcal(budget),"kcal",style=type.numberLarge)
            }
            eaten?.let {e->
                val left=budget-e
                Column(horizontalAlignment=Alignment.End) {
                    Text(kcal(abs(left)),style=type.number,color=Color(if(left>=0) c.good else c.warn))
                    Text(if(left>=0) (if(today) "kcal left" else "kcal under") else "kcal over",style=type.label,color=Color(c.textDim))
                }
            }
        }
        Text("Maintenance ${kcal(maintenance)} · ${if(abs(budget-maintenance)<10) "maintaining" else if(budget<maintenance) "deficit ${kcal(maintenance-budget)}" else "surplus ${kcal(budget-maintenance)}"}",
            style=type.bodySmall,color=Color(c.textDim))
        if(today) {
            val now=listOfNotNull(balance.todayActiveKcal?.takeIf {it>0}?.let {"${kcal(it)} kcal active"},balance.todaySteps?.takeIf {it>0}?.let {"${java.text.DecimalFormat("#,##0").format(it)} steps"},
                balance.todayWorkouts.takeIf {it.isNotEmpty()}?.joinToString {w->"${w.title} ${((w.end-w.start)/60000)} min"})
            if(now.isNotEmpty()) Text("Today · "+now.joinToString(" · "),style=type.bodySmall)
            if(day!=null && day.budgetKcal-balance.budgetKcal>=10) Text("+${kcal(day.budgetKcal-balance.budgetKcal)} kcal earned above your usual activity",style=type.label,color=Color(c.good))
        } else if(day!=null) {
            val past=listOfNotNull(day.activityKcal?.takeIf {day.activityMeasured}?.let {"${kcal(it)} kcal activity counted"},day.steps?.takeIf {it>0}?.let {"${java.text.DecimalFormat("#,##0").format(it)} steps"})
            if(past.isNotEmpty()) Text(past.joinToString(" · "),style=type.bodySmall)
            day.vsMaintenance?.takeIf {day.complete}?.let {Text("${if(it<=0) "Deficit" else "Surplus"} of ${kcal(abs(it))} kcal against maintenance",style=type.label,color=Color(if(it<=0) c.good else c.warn))}
            if(day.estimated) Text("Estimated with your current profile",style=type.label,color=Color(c.textDim))
        }
        if(today) {
            if(balance.confidence>0) Text("Learned from your logs · ${Math.round(balance.confidence*100)}% of maintenance",style=type.label,color=Color(c.accent))
            else if(balance.needsProfile) Text("Add height and age for a closer estimate",style=type.label,color=Color(c.warn))
        }
    }
}

@Composable
internal fun EnergyDetailPage(balance:EnergyBalance?,padding:PaddingValues,onBack:()->Unit,onSettings:()->Unit) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    val app=LocalContext.current.applicationContext as DeltaApp
    val days by rememberEnergyDays()
    val settings by app.repo.settings.collectAsStateWithLifecycle(initialValue=null)
    val weights by app.repo.entries.collectAsStateWithLifecycle(initialValue=emptyList())
    val fat by app.repo.bodyFat.collectAsStateWithLifecycle(initialValue=emptyList())
    val food by app.nutrition.state.collectAsStateWithLifecycle()
    val health by app.energy.healthData.collectAsStateWithLifecycle()
    NutritionPage("Energy budget",padding,onBack) {
        if(balance==null) {
            Text("Turn on the energy budget and log a weigh-in to see it here.",style=type.body)
            PrimaryButton("Body & energy settings",onSettings,Modifier.fillMaxWidth());return@NutritionPage
        }
        val unit=settings?.unit ?: WeightUnit.KG
        val today=days.lastOrNull()?.takeIf {it.isToday}
        ContentSection(tone=ModuleTone.VIOLET,framed=true) {
            Caption("Today's budget")
            ValueWithUnit(kcal(today?.budgetKcal ?: balance.budgetKcal),"kcal",style=type.numberLarge.copy(fontSize=40.sp,lineHeight=44.sp))
            Text(balance.goalNote,style=type.bodySmall)
            if(today!=null && today.budgetKcal-balance.budgetKcal>=10) Text("Includes ${kcal(today.budgetKcal-balance.budgetKcal)} kcal of activity above your usual today.",style=type.label,color=Color(c.good))
            if(balance.capped || balance.floored) Text("Faster changes are hard to sustain and can cost muscle.",style=type.label,color=Color(c.textDim))
        }
        if(days.isNotEmpty()) EnergyBalanceSection(days,weights,unit) {app.energy.recalculatePast()}
        val current=settings
        if(current!=null && days.isNotEmpty()) {
            val advice=remember(balance,days,food.entries,health,current.targetKg,weights,unit) {
                EnergyHistory.advice(adviceInputs(balance,current.energy,days,food.entries,health,current.targetKg,weights,unit))
            }
            AdviceSection(advice)
        }
        val recentFat=remember(fat) {fat.filter {BodyFat.valid(it.percent) && System.currentTimeMillis()-it.epochMs<=45L*86_400_000}.maxByOrNull {it.epochMs}}
        BodyReport(balance,current?.energy ?: EnergyProfile(),current?.targetKg,recentFat,unit)
        ContentSection {
            Caption("How it's worked out")
            NutritionRow("Resting energy",kcal(balance.restingKcal),balance.restingMethod)
            NutritionRow("Digesting food","+${kcal(balance.restingKcal*.1)}","About 10% of what you eat")
            NutritionRow("Activity","+${kcal(balance.activityKcal)}",balance.activityMethod+if(balance.activityFromHealth) " · each day uses its own activity" else "")
            NutritionRow("Estimate",kcal(balance.formulaKcal),"Resting + digestion + activity")
            balance.observedKcal?.let {NutritionRow("From your logs",kcal(it),"${balance.loggedDays} fully logged days and ${balance.weighIns} weigh-ins in the last 4 weeks")}
                ?: Text("After about two weeks of complete logging and regular weigh-ins, Delta learns your real maintenance from how your weight responds.",style=type.bodySmall,color=Color(c.textDim))
            NutritionRow("Maintenance",kcal(balance.maintenanceKcal),if(balance.confidence>0) "${Math.round(balance.confidence*100)}% learned, ${Math.round((1-balance.confidence)*100)}% estimate" else "Estimate")
            NutritionRow(if(balance.dailyChange<=0) "Deficit" else "Surplus",kcal(abs(balance.budgetKcal-balance.maintenanceKcal)),"${String.format(java.util.Locale.UK,"%.2f",abs(balance.paceKgPerWeek))} kg a week at 7,700 kcal per kg")
            balance.age?.let {NutritionRow("Age","$it")}
        }
        if(balance.todayActiveKcal!=null || balance.todaySteps!=null || balance.todayWorkouts.isNotEmpty()) ContentSection {
            Caption("Today from Health Connect")
            balance.todayActiveKcal?.let {NutritionRow("Active energy","${kcal(it)} kcal")}
            balance.todaySteps?.let {NutritionRow("Steps",java.text.DecimalFormat("#,##0").format(it))}
            balance.todayWorkouts.forEach {w->NutritionRow(w.title,"${(w.end-w.start)/60000} min",java.time.Instant.ofEpochMilli(w.start).atZone(java.time.ZoneId.systemDefault()).toLocalTime().withNano(0).withSecond(0).toString())}
        }
        SecondaryButton("Body & energy settings",onSettings,Modifier.fillMaxWidth(),icon=io.github.ebziboi.delta.R.drawable.ic_tune)
        Text("Estimates, not medical advice. Talk to a professional before large changes, during pregnancy, or with a health condition.",style=type.label,color=Color(c.textDim))
    }
}

/** Body profile, activity source, budget choices and diary writing. Works from Settings and from the Diary. */
@Composable
internal fun EnergySettings() {
    val app=LocalContext.current.applicationContext as DeltaApp
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current;val scope=rememberCoroutineScope()
    val settings by app.repo.settings.collectAsStateWithLifecycle(initialValue=null)
    val balance by app.energy.balance.collectAsStateWithLifecycle()
    val writeStatus by app.energy.writeStatus.collectAsStateWithLifecycle()
    var health by remember {mutableStateOf<HealthState?>(null)}
    suspend fun check() {health=runCatching {app.repo.health.state()}.getOrNull();runCatching {app.energy.refresh()}}
    LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {scope.launch {check()}}
    val launcher=rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {scope.launch {check()}}
    val current=settings ?: return
    val p=current.energy
    fun update(change:(EnergyProfile)->EnergyProfile)=scope.launch {app.repo.updateSettings {it.copy(energy=change(it.energy))}}
    var confirmRemove by remember {mutableStateOf(false)}

    ContentSection {
        ToggleRow("Calculate my energy budget",p.enabled,{on->update {it.copy(enabled=on)};if(on) scope.launch {check()}},
            note="Maintenance calories and a daily target from your weight, goal and activity")
    }
    if(p.enabled) {
        ContentSection {
            Caption("About you · optional")
            var metric by rememberSaveable {mutableStateOf(current.unit==WeightUnit.KG)}
            SegmentedControl(listOf(true,false),metric,{if(it) "cm" else "ft & in"}) {metric=it}
            var cm by rememberSaveable(p.heightCm==null) {mutableStateOf(p.heightCm?.let {Math.round(it).toString()} ?: "")}
            var feet by rememberSaveable(p.heightCm==null) {mutableStateOf(p.heightCm?.let {(it/30.48).toInt().toString()} ?: "")}
            var inches by rememberSaveable(p.heightCm==null) {mutableStateOf(p.heightCm?.let {Math.round((it/2.54)%12).toString()} ?: "")}
            fun saveHeight(value:Double?) {if(value==null || value in 50.0..260.0) update {it.copy(heightCm=value)}}
            if(metric) NumberField(cm,{cm=it;saveHeight(it.number())},"Height (cm)",Modifier.fillMaxWidth())
            else Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                fun save() {val f=feet.number();val i=inches.number() ?: 0.0;saveHeight(f?.let {(it*12+i)*2.54})}
                NumberField(feet,{feet=it;save()},"Feet",Modifier.weight(1f));NumberField(inches,{inches=it;save()},"Inches",Modifier.weight(1f))
            }
            if(p.heightCm==null) balance?.heightCm?.let {Text("Using ${Math.round(it)} cm from Health Connect",style=type.bodySmall,color=Color(c.textDim))}
            var year by rememberSaveable(p.birthYear==null) {mutableStateOf(p.birthYear?.toString() ?: "")}
            NumberField(year,{year=it.filter(Char::isDigit).take(4);year.toIntOrNull()?.takeIf {y->y in 1900..LocalDate.now().year-10}.let {y->if(y!=null || year.isEmpty()) update {e->e.copy(birthYear=y)}}},"Year of birth",Modifier.fillMaxWidth())
            SegmentedControl(Sex.entries,p.sex,{if(it==Sex.UNSPECIFIED) "Not saying" else it.label}) {s->update {it.copy(sex=s)}}
            Text("Used only on this phone, for the resting-energy formula. With a recent body-fat reading, lean mass is used instead.",style=type.bodySmall,color=Color(c.textDim))
            balance?.bmi?.let {Text("BMI ${String.format(java.util.Locale.UK,"%.1f",it)} · ${balance?.bmiBand}",style=type.bodyStrong)}
        }
        ContentSection {
            Caption("Activity")
            ToggleRow("Use activity from Health Connect",p.useHealthActivity,{on->update {it.copy(useHealthActivity=on)}},note="Active calories, steps and workouts from your phone or watch")
            if(p.useHealthActivity) {
                Text("Count ${Math.round(p.activityCredit*100)}% of active calories",style=type.bodySmall)
                HapticSlider(p.activityCredit.toFloat(),{v->update {it.copy(activityCredit=(Math.round(v*20)/20.0))}},valueRange=.5f..1f,steps=9)
                Text("Watches usually overestimate exercise calories; 75% is a good start.",style=type.label,color=Color(c.textDim))
            } else ActivityLevel.entries.forEach {level->
                val chosen=level==p.activityLevel
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.control)).background(if(chosen) Color(c.accent).copy(alpha=.14f) else Color.Transparent)
                    .clickable(role=Role.RadioButton) {update {it.copy(activityLevel=level)}}.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {Text(level.label,style=type.bodyStrong,color=if(chosen) Color(c.accent) else Color(c.text));Text(level.detail,style=type.label,color=Color(c.textDim))}
                }
            }
            val h=health
            if(h!=null && h.availability==HealthAvailability.AVAILABLE && h.missingEnergy.isNotEmpty())
                PrimaryButton("Allow height, activity and workouts",{runCatching {launcher.launch(h.missingEnergy)}},Modifier.fillMaxWidth())
            else if(h!=null && h.availability==HealthAvailability.AVAILABLE) Text("Reading height, resting energy, active calories, steps and workouts",style=type.label,color=Color(c.good))
        }
        ContentSection {
            Caption("Budget")
            ToggleRow("Learn from my logs and weight",p.adaptive,{on->update {it.copy(adaptive=on)}},note="Adjusts maintenance to how your weight actually responds to what you eat")
            if(current.targetEpochDay==null && current.targetKg!=null) {
                Text("Pace towards your target",style=type.bodySmall)
                SegmentedControl(listOf(.25,.5,.75,1.0),p.weeklyKg,{"${it} kg"}) {v->update {it.copy(weeklyKg=v)}}
                Text("A week; set a target date in Settings to pace from it instead. Delta caps loss at 1% of your weight a week.",style=type.label,color=Color(c.textDim))
            }
            if(current.targetKg==null) Text("Set a target weight in Settings to plan a deficit or surplus.",style=type.bodySmall,color=Color(c.warn))
            ToggleRow("Set my daily energy target automatically",p.autoBudget,{on->update {it.copy(autoBudget=on)}},note="Keeps the Diary target and food widgets in step with the budget")
            balance?.let {Text("Today's budget: ${kcal(it.budgetKcal)} kcal · maintenance ${kcal(it.maintenanceKcal)}",style=type.bodyStrong,color=Color(c.accent))}
        }
    }
    if(!io.github.ebziboi.delta.BuildConfig.IS_BETA) ContentSection {
        Caption("Health Connect food")
        val h=health
        ToggleRow("Write my food diary to Health Connect",p.writeNutrition,{on->
            update {it.copy(writeNutrition=on)}
            if(on && h?.canWriteNutrition==false) runCatching {launcher.launch(setOf(HealthConnectManager.WRITE_NUTRITION))}
            if(on) app.energy.writeNow() else if(app.energy.writtenCount>0) confirmRemove=true
        },note="Each food you log appears in Health Connect, so other apps see your intake. Edits and deletions follow.")
        if(p.writeNutrition) {
            if(h?.canWriteNutrition==false) PrimaryButton("Allow writing food",{runCatching {launcher.launch(setOf(HealthConnectManager.WRITE_NUTRITION))}},Modifier.fillMaxWidth())
            writeStatus?.let {Text(it,style=type.bodySmall,color=Color(c.textDim))}
            TextAction("Sync diary now",{app.energy.writeNow()})
        }
        Text("Food that other apps write is still read separately, and Delta never reads its own entries back.",style=type.label,color=Color(c.textDim))
    }
    if(confirmRemove) AlertDialog(onDismissRequest={confirmRemove=false},title={Text("Remove Delta's food from Health Connect?")},
        text={Text("Stops writing new entries. You can also remove the ${app.energy.writtenCount} entries Delta already wrote; your diary in Delta is unchanged.")},
        confirmButton={HapticTextButton({confirmRemove=false;scope.launch {app.energy.removeWritten()}}) {Text("Remove them")}},
        dismissButton={HapticTextButton({confirmRemove=false}) {Text("Keep them")}})
}
