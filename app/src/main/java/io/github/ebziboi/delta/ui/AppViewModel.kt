package io.github.ebziboi.delta.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.data.HealthState
import io.github.ebziboi.delta.data.ProgressSnapshot
import io.github.ebziboi.delta.data.SyncResult
import io.github.ebziboi.delta.data.WeightEntry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/** Holds what the screens show and carries out what they ask for. */
data class FoodOpenRequest(val route:String,val token:Long)
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val _foodOpen=MutableStateFlow<FoodOpenRequest?>(null)
    val foodOpen=_foodOpen.asStateFlow()
    fun openFood(route:String) {if(route in setOf("diary","search","charts","targets","fasting","energy","energy-setup")) _foodOpen.value=FoodOpenRequest(route,System.nanoTime())}
    fun consumeFoodOpen() {_foodOpen.value=null}
    private val repo = (application as DeltaApp).repo
    private val nutritionRepo=(application as DeltaApp).nutrition
    val nutrition=nutritionRepo.state

    private val _appearancePreview = MutableStateFlow<io.github.ebziboi.delta.data.AppAppearance?>(null)
    val appearancePreview = _appearancePreview.asStateFlow()

    fun previewAppearance(value: io.github.ebziboi.delta.data.AppAppearance?) {
        _appearancePreview.value = value?.normalized()
    }

    /** A drag updates the theme in memory; only the released value is persisted. */
    fun saveAppearance(value: io.github.ebziboi.delta.data.AppAppearance) {
        val chosen = value.normalized()
        viewModelScope.launch {
            try { repo.updateSettings { it.copy(appearance = chosen) } }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { _messages.tryEmit("Couldn't save that appearance. Please try again.") }
            finally { if (_appearancePreview.value == chosen) _appearancePreview.value = null }
        }
    }

    /** Null until the saved settings have been read. */
    val settings: StateFlow<AppSettings?> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val snapshot: StateFlow<ProgressSnapshot?> =
        repo.snapshot.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val bodyFat = repo.bodyFat.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val entries: StateFlow<List<WeightEntry>> =
        repo.entries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _health = MutableStateFlow<HealthState?>(null)
    val health: StateFlow<HealthState?> = _health.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Short notices for the user, shown once each. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /**
     * Checks what Health Connect allows and reads the latest weigh-ins. Runs when
     * the app comes to the front and after the permission screen closes; with
     * [announce] it also says how it went.
     */
    fun refresh(announce: Boolean = false) {
        // Activity for the energy budget is read on its own, so a failed weight sync can't hold it back.
        runCatching {(getApplication<android.app.Application>() as io.github.ebziboi.delta.DeltaApp).energy.refreshNow()}
        viewModelScope.launch {
            _health.value = runCatching { repo.health.state() }.getOrDefault(_health.value)
            if (_syncing.value) return@launch
            _syncing.value = true
            val result = try {
                val weights=repo.sync()
                try {nutritionRepo.syncHealth(repo.health)}
                catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
                catch(_:Exception) {if(announce) _messages.tryEmit("Food sync failed. Your saved meals are unchanged.")}
                weights
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                SyncResult.Failed(error.message ?: error.javaClass.simpleName)
            } finally {
                _syncing.value = false
            }
            _health.value = runCatching { repo.health.state() }.getOrDefault(_health.value)
            if (announce) {
                _messages.tryEmit(
                    when (result) {
                        is SyncResult.Done -> when (result.count) {
                            0 -> if(io.github.ebziboi.delta.BuildConfig.IS_BETA) "No beta weigh-ins yet" else "No weigh-ins found in Health Connect yet"
                            1 -> if(io.github.ebziboi.delta.BuildConfig.IS_BETA) "1 beta weigh-in" else "1 weigh-in synced"
                            else -> if(io.github.ebziboi.delta.BuildConfig.IS_BETA) "${result.count} beta weigh-ins" else "${result.count} weigh-ins synced"
                        }
                        SyncResult.NoPermission -> "Allow Delta to read weight or body fat in Health Connect"
                        SyncResult.Unavailable -> "Health Connect isn't available on this phone"
                        is SyncResult.Failed -> "Sync failed: ${result.reason}"
                    }
                )
            }
        }
    }

    fun logWeight(kg: Double, at: Instant, percent: Double? = null) {
        viewModelScope.launch {
            val saved = repo.logWeight(kg, at, percent)
            _messages.tryEmit(
                if (saved) "Weigh-in saved" else "Couldn't save. Check Health Connect access in Settings."
            )
        }
    }

    fun logBodyFat(percent: Double, at: Instant) {
        viewModelScope.launch {
            try { repo.logBodyFat(percent, at); _messages.tryEmit("Body fat saved") }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { _messages.tryEmit("Couldn't save body fat. Check Health Connect access.") }
        }
    }
    fun deleteBodyFat(entry: io.github.ebziboi.delta.data.BodyFatEntry) {
        viewModelScope.launch {
            try { val removed = repo.deleteBodyFat(entry); _messages.tryEmit(if (removed) "Body fat deleted" else "Couldn't delete that body fat reading") }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { _messages.tryEmit("Couldn't delete body fat") }
        }
    }

    fun delete(entry: WeightEntry) {
        viewModelScope.launch {
            val removed = repo.delete(entry)
            _messages.tryEmit(if (removed) "Weigh-in deleted" else "Couldn't delete that weigh-in")
        }
    }

    private val _importChoice = MutableStateFlow<io.github.ebziboi.delta.data.ScaleExport?>(null)

    /** A scale export holding more than one person, waiting for the user to say whose weigh-ins to import. */
    val importChoice: StateFlow<io.github.ebziboi.delta.data.ScaleExport?> = _importChoice.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    /** Reads a smart scale's CSV export. With one person in it, imports straight away; otherwise asks whose. */
    fun importFile(uri: android.net.Uri) {
        viewModelScope.launch {
            _importing.value = true
            try {
                val export = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        ?: throw io.github.ebziboi.delta.data.ScaleImportException("Delta couldn't open that file.")
                    io.github.ebziboi.delta.data.ScaleImport.parse(text)
                }
                if (export.people.size == 1) importPerson(export, export.people.keys.first()) else _importChoice.value = export
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: io.github.ebziboi.delta.data.ScaleImportException) {
                _messages.tryEmit(error.message ?: "Delta couldn't read that file.")
            } catch (error: Exception) {
                _messages.tryEmit("Delta couldn't read that file.")
            } finally {
                _importing.value = false
            }
        }
    }

    fun choosePerson(name: String?) {
        val export = _importChoice.value ?: return
        _importChoice.value = null
        if (name != null) viewModelScope.launch { importPerson(export, name) }
    }

    private suspend fun importPerson(export: io.github.ebziboi.delta.data.ScaleExport, name: String) {
        val readings = export.people[name].orEmpty()
        _importing.value = true
        try {
            if (!io.github.ebziboi.delta.BuildConfig.IS_BETA && runCatching { repo.health.state() }.getOrNull()?.canWrite != true) {
                _messages.tryEmit("Allow Delta to write weight in Health Connect, then import again.")
                return
            }
            if (!io.github.ebziboi.delta.BuildConfig.IS_BETA && readings.any { it.bodyFatPercent != null } && runCatching { repo.health.state() }.getOrNull()?.canWriteBodyFat != true) {
                _messages.tryEmit("Allow body fat write access in Health Connect before importing this file.")
                return
            }
            val result = repo.importReadings(readings)
            _messages.tryEmit(if(io.github.ebziboi.delta.BuildConfig.IS_BETA) "Added ${result.added} test weigh-ins and ${result.bodyFatAdded} body-fat readings to beta only" else if (result.bodyFatAdded > 0) "Imported ${result.added} weigh-ins and ${result.bodyFatAdded} body fat readings" else when {
                result.added == 0 -> "All ${result.alreadyThere} weigh-ins were already in Health Connect"
                result.alreadyThere == 0 -> "Imported ${result.added} weigh-ins"
                else -> "Imported ${result.added} weigh-ins; ${result.alreadyThere} were already there"
            })
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _messages.tryEmit("The import didn't finish: ${error.message ?: error.javaClass.simpleName}")
        } finally {
            _importing.value = false
        }
    }

    private val _lastCrash = MutableStateFlow(io.github.ebziboi.delta.CrashRecorder.last(application))

    /** Details of the last time Delta stopped unexpectedly, until the user shares or dismisses them. */
    val lastCrash: StateFlow<String?> = _lastCrash.asStateFlow()

    fun dismissCrash() {
        io.github.ebziboi.delta.CrashRecorder.clear(getApplication())
        _lastCrash.value = null
    }

    fun selectIcon(id: String) {
        viewModelScope.launch {
            val previous = settings.value?.appIcon ?: "Classic"
            try {
                repo.updateSettings { it.copy(appIcon = id) }
                io.github.ebziboi.delta.icons.LauncherIcons.apply(getApplication(), id)
                _messages.tryEmit("App icon updated")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                runCatching { repo.updateSettings {it.copy(appIcon=previous)};io.github.ebziboi.delta.icons.LauncherIcons.apply(getApplication(), previous) }
                _messages.tryEmit("Couldn't change the app icon. Please try again.")
            }
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            try {
                repo.updateSettings(transform)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _messages.tryEmit("Couldn't save that setting. Please try again.")
            }
        }
    }
}
