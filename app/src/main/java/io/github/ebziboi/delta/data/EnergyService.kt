package io.github.ebziboi.delta.data

import android.content.Context
import io.github.ebziboi.delta.nutrition.Nutrient
import io.github.ebziboi.delta.nutrition.NutritionHealthWriter
import io.github.ebziboi.delta.nutrition.NutritionRepository
import io.github.ebziboi.delta.nutrition.total
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.LocalDate

/**
 * The energy budget: combines the body profile, weigh-ins, body fat, the food diary and (when allowed)
 * Health Connect height, resting energy, activity and workouts. Also mirrors the diary to Health Connect.
 */
class EnergyService(context:Context,private val weights:WeightRepository,private val nutrition:NutritionRepository) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val health=MutableStateFlow(EnergyHealthData())
    private val writer=NutritionHealthWriter(context,weights.health)
    private val _writeStatus=MutableStateFlow<String?>(null)
    val writeStatus:StateFlow<String?> = _writeStatus.asStateFlow()

    val balance:StateFlow<EnergyBalance?> = combine(weights.settings,weights.entries,weights.bodyFat,nutrition.state,health) {settings,entries,fat,food,data->
        if(!settings.energy.enabled) null else {
            EnergyMath.compute(EnergyInputs(settings.energy,entries,fat,intakeByDay(food.entries),data,settings.targetKg,settings.targetEpochDay?.let(LocalDate::ofEpochDay)))
        }
    }.flowOn(Dispatchers.Default).stateIn(scope,SharingStarted.Eagerly,null)

    /** Days with a recorded workout, for markers on the home graph. */
    val workoutDays:StateFlow<Set<LocalDate>> = health.map {d->d.workouts.map {java.time.Instant.ofEpochMilli(it.start).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}.toSet()}
        .stateIn(scope,SharingStarted.Eagerly,emptySet())

    // Each day's plan, saved while the day is current so later goal or profile changes don't rewrite it.
    private val planPrefs=context.applicationContext.getSharedPreferences("energy-day-plans",0)
    private val plans=MutableStateFlow(loadPlans())
    private fun loadPlans():Map<LocalDate,DayPlan> = planPrefs.all.mapNotNull {(k,v)->k.toLongOrNull()?.let(LocalDate::ofEpochDay)?.let {d->DayPlan.decode(v?.toString())?.let {d to it}}}.toMap()

    /** Budgets for the last [HISTORY_DAYS] days, oldest first, with each day's own activity. Empty when the budget is off. */
    val days:StateFlow<List<DayBudget>> = combine(balance,weights.settings,nutrition.state,health,plans) {b,settings,food,data,saved->
        if(b==null || !settings.energy.enabled) emptyList() else {
            val today=LocalDate.now()
            EnergyHistory.days(b,settings.energy,saved,data.activity,intakeByDay(food.entries),today.minusDays(HISTORY_DAYS-1L),today)
        }
    }.flowOn(Dispatchers.Default).stateIn(scope,SharingStarted.Eagerly,emptyList())

    /** Forgets saved plans so past days are worked out again with the current profile and goal. */
    fun recalculatePast()=scope.launch {planPrefs.edit().clear().apply();plans.value=emptyMap();balance.value?.let {savePlan(it)}}
    private suspend fun savePlan(b:EnergyBalance) {
        val today=LocalDate.now();val settings=weights.settings.first()
        val plan=DayPlan.of(b,settings.energy)
        if(plans.value[today]?.encode()==plan.encode()) return
        planPrefs.edit().putString(today.toEpochDay().toString(),plan.encode()).apply()
        plans.value=plans.value+(today to plan)
    }

    private var writeJob:Job?=null
    init {
        scope.launch {balance.filterNotNull().collect {savePlan(it)}}
        nutrition.onChanged={scheduleWrite()}
        // Keep the diary's energy target equal to the budget when asked, without rewriting it for small wobbles.
        scope.launch {
            // Today's budget includes today's activity once it beats the usual amount.
            days.mapNotNull {d->d.lastOrNull()?.takeIf {it.isToday}?.budgetKcal}.distinctUntilChanged().collect {budget->
                val settings=weights.settings.first()
                if(!settings.energy.autoBudget) return@collect
                val state=nutrition.state.value
                if(!state.loaded || state.loadError!=null) return@collect
                val current=state.targets.amounts[Nutrient.ENERGY]
                if(current==null || kotlin.math.abs(current-budget)>=20)
                    runCatching {nutrition.saveTargets(state.targets.copy(amounts=state.targets.amounts+(Nutrient.ENERGY to budget)))}
            }
        }
    }

    /** Reads what Health Connect allows for the budget. Called with the regular sync. */
    suspend fun refresh() {
        val profile=weights.settings.first().energy
        if(!profile.enabled && !profile.writeNutrition) return
        val access=runCatching {weights.health.state()}.getOrNull() ?: return
        if(access.availability!=HealthAvailability.AVAILABLE) return
        if(profile.enabled) {
            val previous=health.value
            health.value=EnergyHealthData(
                heightCm=if(access.canReadHeight) runCatching {weights.health.readLatestHeight()}.getOrNull() else null,
                restingKcal=if(access.canReadResting) runCatching {weights.health.readLatestResting()}.getOrNull() else null,
                // Back-fills past days: as far as Health Connect allows, 30 days before access or longer with history access.
                activity=if(access.canReadActivity) runCatching {weights.health.readDailyActivity(HISTORY_DAYS,access.canReadActive,access.canReadSteps)}
                    .recoverCatching {weights.health.readDailyActivity(30,access.canReadActive,access.canReadSteps)}.getOrDefault(previous.activity) else emptyList(),
                workouts=if(access.canReadExercise) runCatching {weights.health.readWorkouts(Instant.now().minus(java.time.Duration.ofDays(HISTORY_DAYS.toLong())))}
                    .recoverCatching {weights.health.readWorkouts(Instant.now().minus(java.time.Duration.ofDays(30)))}.getOrDefault(previous.workouts) else emptyList(),
                canReadWorkouts=access.canReadExercise,
                readAt=System.currentTimeMillis())
        }
        if(profile.writeNutrition) write()
    }

    fun scheduleWrite() {
        writeJob?.cancel()
        writeJob=scope.launch {delay(1500);if(weights.settings.first().energy.writeNutrition) write()}
    }

    private suspend fun write() {
        val state=nutrition.state.value
        if(!state.loaded) return
        _writeStatus.value=try {
            val changed=writer.reconcile(state.entries)
            if(changed>0) "Updated $changed food ${if(changed==1) "entry" else "entries"} in Health Connect" else _writeStatus.value ?: "Diary is up to date in Health Connect"
        } catch(e:CancellationException) {throw e} catch(_:Exception) {"Couldn't write food to Health Connect. Delta will try again."}
    }

    /** Writes the whole diary now. */
    fun writeNow()=scope.launch {write()}

    /** Removes Delta's food records from Health Connect. */
    suspend fun removeWritten():Int=runCatching {writer.removeAll()}.getOrDefault(0)
    val writtenCount get()=writer.count

    /** Health Connect data behind the budget, for the suggestions. */
    val healthData:StateFlow<EnergyHealthData> get()=health

    companion object {
        const val HISTORY_DAYS=90
        internal fun intakeByDay(entries:List<io.github.ebziboi.delta.nutrition.DiaryEntry>)=
            entries.groupBy {it.day}.mapValues {(_,day)->day.total(Nutrient.ENERGY).let {it.amount to (it.reported>0 && !it.partial)}}
    }
}
