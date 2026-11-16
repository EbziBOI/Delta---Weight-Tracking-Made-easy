package io.github.ebziboi.delta.data

import android.content.Context
import io.github.ebziboi.delta.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/** How an import went: weigh-ins added, and ones Health Connect already had. */
data class ImportResult(val added: Int, val alreadyThere: Int, val bodyFatAdded: Int = 0)

sealed interface SyncResult {
    data class Done(val count: Int) : SyncResult
    data object NoPermission : SyncResult
    data object Unavailable : SyncResult
    data class Failed(val reason: String) : SyncResult
}

/**
 * Health Connect is the record of truth for weigh-ins. This keeps a local copy
 * in step with it, and tells the widgets whenever something they show changes.
 */
class WeightRepository(context: Context) {
    private val beta = io.github.ebziboi.delta.BuildConfig.IS_BETA
    private val appContext = context.applicationContext
    private val store = SettingsStore(appContext)
    private val syncLock = Mutex()

    val health = HealthConnectManager(appContext)

    val settings: Flow<AppSettings> = store.settings
    val entries: Flow<List<WeightEntry>> = store.weights

    val bodyFat: Flow<List<BodyFatEntry>> = store.bodyFat

    val snapshot: Flow<ProgressSnapshot> =
        combine(entries, settings, bodyFat) { entries, settings, fat -> ProgressCalculator.compute(entries, settings).copy(bodyFat = fat, targetBodyFat = settings.targetBodyFat) }
            .flowOn(Dispatchers.Default)

    /** What a widget needs to draw itself. */
    val widgetData: Flow<Pair<ProgressSnapshot, AppSettings>> =
        combine(entries, settings, bodyFat) { entries, settings, fat -> ProgressCalculator.compute(entries, settings).copy(bodyFat = fat, targetBodyFat = settings.targetBodyFat) to settings }
            .flowOn(Dispatchers.Default)

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        store.update(transform)
        WidgetUpdater.updateAll(appContext)
    }

    /** Reads the full weight history from Health Connect and refreshes everything that shows it. */
    suspend fun sync(): SyncResult = syncLock.withLock {
        if(beta) return@withLock SyncResult.Done(store.weights.first().size)
        if (health.availability() != HealthAvailability.AVAILABLE) return@withLock SyncResult.Unavailable
        val access = health.state()
        if (!access.canRead && !access.canReadBodyFat) return@withLock SyncResult.NoPermission

        val since = Instant.EPOCH
        val errors = mutableListOf<String>()
        var synced = false
        var count = store.weights.first().size
        if (access.canRead) {
            try {
                val fresh = health.readWeights(since)
                store.saveWeights(fresh)
                count = fresh.size
                synced = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { errors += "Weight: ${error.message ?: error.javaClass.simpleName}" }
        }
        if (access.canReadBodyFat) {
            try {
                store.saveBodyFat(health.readBodyFat(since))
                synced = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { errors += "Body fat: ${error.message ?: error.javaClass.simpleName}" }
        }
        if (synced) store.update { it.copy(lastSyncMs = System.currentTimeMillis()) }
        WidgetUpdater.updateAll(appContext)
        if (errors.isNotEmpty()) SyncResult.Failed(errors.joinToString("; ")) else SyncResult.Done(count)
    }

    /** Writes a weigh-in to Health Connect, then reads back so the copy here matches. */
    suspend fun logWeight(kg: Double, at: Instant, percent: Double? = null): Boolean = try {
        if(beta) syncLock.withLock {
            require(kg.isFinite() && kg in 20.0..400.0);require(percent==null || BodyFat.valid(percent))
            val weights=store.weights.first()+WeightEntry(at.toEpochMilli(),kg,true,"beta-local:${java.util.UUID.randomUUID()}")
            val fat=store.bodyFat.first()+listOfNotNull(percent?.let {BodyFatEntry(at.toEpochMilli(),it,true,"beta-local:${java.util.UUID.randomUUID()}")})
            saveBetaMeasurements(weights.sortedBy {it.epochMs},fat.sortedBy {it.epochMs});true
        } else {health.writeMeasurement(kg, percent, at);sync();true}
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        false
    }

    suspend fun logBodyFat(percent: Double, at: Instant): Boolean {
        if(beta) return syncLock.withLock {
            require(BodyFat.valid(percent));saveBetaMeasurements(store.weights.first(),
                (store.bodyFat.first()+BodyFatEntry(at.toEpochMilli(),percent,true,"beta-local:${java.util.UUID.randomUUID()}")).sortedBy {it.epochMs});true
        }
        health.writeBodyFat(percent, at)
        sync()
        return true
    }

    suspend fun deleteBodyFat(entry: BodyFatEntry): Boolean {
        if (!entry.own) return false
        if(beta) return syncLock.withLock {saveBetaMeasurements(store.weights.first(),store.bodyFat.first().filterNot {it.id==entry.id});true}
        health.deleteBodyFat(entry.id)
        sync()
        return true
    }

    /**
     * Adds weigh-ins from a scale's export to Health Connect. Readings the scale
     * saved twice within minutes count once, and anything already in Health
     * Connect (often the scale's own app syncs there too) is skipped.
     */
    suspend fun importReadings(readings: List<ImportedReading>): ImportResult {
        val existing = store.weights.first()
        val distinct = ArrayList<ImportedReading>()
        for (reading in readings.sortedBy { it.epochMs }) {
            val last = distinct.lastOrNull()
            if (last != null && same(last.epochMs, last.kg, reading)) continue
            distinct += reading
        }
        val fresh = distinct.filter { reading -> existing.none { same(it.epochMs, it.kg, reading) } }
        val body = store.bodyFat.first()
        val distinctFat = ArrayList<ImportedReading>()
        for (r in readings.filter { it.bodyFatPercent != null }.sortedBy { it.epochMs }) {
            require(BodyFat.valid(r.bodyFatPercent!!))
            if (distinctFat.none { kotlin.math.abs(it.epochMs - r.epochMs) <= 15 * 60_000L && kotlin.math.abs(it.bodyFatPercent!! - r.bodyFatPercent) < 0.05 }) distinctFat += r
        }
        val newFat = distinctFat.filter { r -> body.none { kotlin.math.abs(it.epochMs - r.epochMs) <= 15 * 60_000L && kotlin.math.abs(it.percent - r.bodyFatPercent!!) < 0.05 } }
        if(beta) return syncLock.withLock {
            val w=existing+fresh.map {WeightEntry(it.epochMs,it.kg,true,"beta-local:${java.util.UUID.randomUUID()}")}
            val f=body+newFat.map {BodyFatEntry(it.epochMs,it.bodyFatPercent!!,true,"beta-local:${java.util.UUID.randomUUID()}")}
            saveBetaMeasurements(w.sortedBy {it.epochMs},f.sortedBy {it.epochMs})
            ImportResult(fresh.size,distinct.size-fresh.size,newFat.size)
        }
        if (fresh.isNotEmpty()) health.writeWeights(fresh.map { it.copy(bodyFatPercent = null) })
        for (r in newFat) health.writeBodyFat(r.bodyFatPercent!!, Instant.ofEpochMilli(r.epochMs), "delta-import-fat-${r.epochMs}-${r.bodyFatPercent}")
        if (fresh.isNotEmpty() || newFat.isNotEmpty()) sync()
        return ImportResult(added = fresh.size, alreadyThere = distinct.size - fresh.size, bodyFatAdded = newFat.size)
    }

    private suspend fun saveBetaMeasurements(weights:List<WeightEntry>,fat:List<BodyFatEntry>) {
        store.saveBetaMeasurements(weights,fat)
        WidgetUpdater.updateAll(appContext)
    }

    /** Explicit, one-way reading import. Normal beta refresh never overwrites a test snapshot. */
    suspend fun copyHealthConnectForBeta() = syncLock.withLock {
        check(beta)
        val state=health.state();require(state.canRead || state.canReadBodyFat) {"Allow read access in Health Connect first"}
        val weights=if(state.canRead) health.readWeights(Instant.EPOCH).map {it.copy(id="beta-copy:${it.id}",own=true)} else store.weights.first()
        val fat=if(state.canReadBodyFat) health.readBodyFat(Instant.EPOCH).map {it.copy(id="beta-copy:${it.id}",own=true)} else store.bodyFat.first()
        saveBetaMeasurements(weights,fat)
    }

    private fun same(epochMs: Long, kg: Double, reading: ImportedReading) =
        kotlin.math.abs(epochMs - reading.epochMs) <= 15 * 60_000L && kotlin.math.abs(kg - reading.kg) < 0.1

    suspend fun delete(entry: WeightEntry): Boolean = try {
        require(entry.own)
        if(beta) syncLock.withLock {saveBetaMeasurements(store.weights.first().filterNot {it.id==entry.id},store.bodyFat.first());true}
        else {health.deleteWeight(entry.id);sync();true}
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        false
    }

}
