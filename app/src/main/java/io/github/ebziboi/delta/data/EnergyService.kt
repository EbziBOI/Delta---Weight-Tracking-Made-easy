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
            val intake=food.entries.groupBy {it.day}.mapValues {(_,day)->day.total(Nutrient.ENERGY).let {it.amount to (it.reported>0 && !it.partial)}}
            EnergyMath.compute(EnergyInputs(settings.energy,entries,fat,intake,data,settings.targetKg,settings.targetEpochDay?.let(LocalDate::ofEpochDay)))
        }
    }.flowOn(Dispatchers.Default).stateIn(scope,SharingStarted.Eagerly,null)

    /** Days with a recorded workout, for markers on the home graph. */
    val workoutDays:StateFlow<Set<LocalDate>> = health.map {d->d.workouts.map {java.time.Instant.ofEpochMilli(it.start).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}.toSet()}
        .stateIn(scope,SharingStarted.Eagerly,emptySet())

    private var writeJob:Job?=null
    init {
        nutrition.onChanged={scheduleWrite()}
        // Keep the diary's energy target equal to the budget when asked, without rewriting it for small wobbles.
        scope.launch {
            balance.filterNotNull().collect {b->
                val settings=weights.settings.first()
                if(!settings.energy.autoBudget) return@collect
                val state=nutrition.state.value
                if(!state.loaded || state.loadError!=null) return@collect
                val current=state.targets.amounts[Nutrient.ENERGY]
                if(current==null || kotlin.math.abs(current-b.budgetKcal)>=20)
                    runCatching {nutrition.saveTargets(state.targets.copy(amounts=state.targets.amounts+(Nutrient.ENERGY to b.budgetKcal)))}
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
                activity=if(access.canReadActivity) runCatching {weights.health.readDailyActivity(15,access.canReadActive,access.canReadSteps)}.getOrDefault(previous.activity) else emptyList(),
                workouts=if(access.canReadExercise) runCatching {weights.health.readWorkouts(Instant.now().minus(java.time.Duration.ofDays(15)))}.getOrDefault(previous.workouts) else emptyList(),
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
}
