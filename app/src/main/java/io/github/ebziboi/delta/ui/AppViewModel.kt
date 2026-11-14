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
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as DeltaApp).repo

    /** Null until the saved settings have been read. */
    val settings: StateFlow<AppSettings?> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val snapshot: StateFlow<ProgressSnapshot?> =
        repo.snapshot.stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
        viewModelScope.launch {
            _health.value = repo.health.state()
            if (_syncing.value) return@launch
            _syncing.value = true
            val result = try {
                repo.sync()
            } finally {
                _syncing.value = false
            }
            _health.value = repo.health.state()
            if (announce) {
                _messages.tryEmit(
                    when (result) {
                        is SyncResult.Done -> when (result.count) {
                            0 -> "No weigh-ins found in Health Connect yet"
                            1 -> "1 weigh-in synced"
                            else -> "${result.count} weigh-ins synced"
                        }
                        SyncResult.NoPermission -> "Delta needs permission to read your weight"
                        SyncResult.Unavailable -> "Health Connect isn't available on this phone"
                        is SyncResult.Failed -> "Sync failed: ${result.reason}"
                    }
                )
            }
        }
    }

    fun logWeight(kg: Double, at: Instant) {
        viewModelScope.launch {
            val saved = repo.logWeight(kg, at)
            _messages.tryEmit(
                if (saved) "Weigh-in saved" else "Couldn't save. Check Health Connect access in Settings."
            )
        }
    }

    fun delete(entry: WeightEntry) {
        viewModelScope.launch {
            val removed = repo.delete(entry)
            _messages.tryEmit(if (removed) "Weigh-in deleted" else "Couldn't delete that weigh-in")
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repo.updateSettings(transform) }
    }
}
