package io.github.ebziboi.delta.data

import android.content.Context
import io.github.ebziboi.delta.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZonedDateTime

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
    private val appContext = context.applicationContext
    private val store = SettingsStore(appContext)
    private val syncLock = Mutex()

    val health = HealthConnectManager(appContext)

    val settings: Flow<AppSettings> = store.settings
    val entries: Flow<List<WeightEntry>> = store.weights

    val snapshot: Flow<ProgressSnapshot> =
        combine(entries, settings) { entries, settings -> ProgressCalculator.compute(entries, settings) }
            .flowOn(Dispatchers.Default)

    /** What a widget needs to draw itself. */
    val widgetData: Flow<Pair<ProgressSnapshot, AppSettings>> =
        combine(entries, settings) { entries, settings -> ProgressCalculator.compute(entries, settings) to settings }
            .flowOn(Dispatchers.Default)

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        store.update(transform)
        WidgetUpdater.updateAll(appContext)
    }

    /** Reads the full weight history from Health Connect and refreshes everything that shows it. */
    suspend fun sync(): SyncResult = syncLock.withLock {
        if (health.availability() != HealthAvailability.AVAILABLE) return@withLock SyncResult.Unavailable
        if (!health.state().canRead) return@withLock SyncResult.NoPermission

        val since = ZonedDateTime.now().minusYears(HISTORY_YEARS).toInstant()
        val fresh = try {
            health.readWeights(since)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (denied: SecurityException) {
            // Typically a background run without permission to read while closed.
            return@withLock SyncResult.NoPermission
        } catch (error: Exception) {
            return@withLock SyncResult.Failed(error.message ?: error.javaClass.simpleName)
        }

        store.saveWeights(fresh)
        store.update { it.copy(lastSyncMs = System.currentTimeMillis()) }
        WidgetUpdater.updateAll(appContext)
        SyncResult.Done(fresh.size)
    }

    /** Writes a weigh-in to Health Connect, then reads back so the copy here matches. */
    suspend fun logWeight(kg: Double, at: Instant): Boolean = try {
        health.writeWeight(kg, at)
        sync()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        false
    }

    suspend fun delete(entry: WeightEntry): Boolean = try {
        health.deleteWeight(entry.id)
        sync()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        false
    }

    private companion object {
        /** Far enough back to cover any start date someone is likely to choose. */
        const val HISTORY_YEARS = 5L
    }
}
