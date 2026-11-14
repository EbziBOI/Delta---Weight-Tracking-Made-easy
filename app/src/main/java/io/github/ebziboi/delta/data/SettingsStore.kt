package io.github.ebziboi.delta.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

// Settings and the weigh-in cache live in separate files so that changing a
// setting never rewrites the history, and the other way round.
private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "delta_settings")
private val Context.weightStore: DataStore<Preferences> by preferencesDataStore(name = "delta_weights")

/**
 * What Delta keeps on the phone: the user's choices, and a copy of the
 * weigh-ins last read from Health Connect so the app and its widgets can draw
 * straight away, including while Health Connect cannot be read in the background.
 */
class SettingsStore(context: Context) {
    private val appContext = context.applicationContext

    private object Keys {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val TARGET_KG = doublePreferencesKey("target_kg")
        val START_DAY = longPreferencesKey("start_day")
        val START_KG = doublePreferencesKey("start_kg")
        val TARGET_DAY = longPreferencesKey("target_day")
        val UNIT = stringPreferencesKey("unit")
        val THEME = stringPreferencesKey("theme")
        val ACCENT = stringPreferencesKey("accent")
        val LAST_SYNC = longPreferencesKey("last_sync")
        val WEIGHTS = stringPreferencesKey("weights")
    }

    val settings: Flow<AppSettings> = appContext.settingsStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { decode(it) }
        .distinctUntilChanged()

    val weights: Flow<List<WeightEntry>> = appContext.weightStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { decodeWeights(it[Keys.WEIGHTS]) }
        .distinctUntilChanged()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        appContext.settingsStore.edit { prefs -> encode(transform(decode(prefs)), prefs) }
    }

    suspend fun saveWeights(entries: List<WeightEntry>) {
        appContext.weightStore.edit { prefs -> prefs[Keys.WEIGHTS] = encodeWeights(entries) }
    }

    private fun decode(prefs: Preferences): AppSettings = AppSettings(
        onboardingDone = prefs[Keys.ONBOARDED] ?: false,
        targetKg = prefs[Keys.TARGET_KG],
        startEpochDay = prefs[Keys.START_DAY],
        startKgOverride = prefs[Keys.START_KG],
        targetEpochDay = prefs[Keys.TARGET_DAY],
        unit = named(prefs[Keys.UNIT], WeightUnit.KG),
        theme = named(prefs[Keys.THEME], ThemeStyle.MIDNIGHT),
        accent = named(prefs[Keys.ACCENT], Accent.SKY),
        lastSyncMs = prefs[Keys.LAST_SYNC] ?: 0L,
    )

    private fun encode(settings: AppSettings, prefs: MutablePreferences) {
        prefs[Keys.ONBOARDED] = settings.onboardingDone
        put(prefs, Keys.TARGET_KG, settings.targetKg)
        put(prefs, Keys.START_DAY, settings.startEpochDay)
        put(prefs, Keys.START_KG, settings.startKgOverride)
        put(prefs, Keys.TARGET_DAY, settings.targetEpochDay)
        prefs[Keys.UNIT] = settings.unit.name
        prefs[Keys.THEME] = settings.theme.name
        prefs[Keys.ACCENT] = settings.accent.name
        prefs[Keys.LAST_SYNC] = settings.lastSyncMs
    }

    private fun <T> put(prefs: MutablePreferences, key: Preferences.Key<T>, value: T?) {
        if (value == null) prefs.remove(key) else prefs[key] = value
    }

    /** A saved name that no longer matches an option falls back to the default. */
    private inline fun <reified T : Enum<T>> named(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback

    // One weigh-in per segment: time|kg|own|id. Health Connect ids never contain these separators.
    private fun encodeWeights(entries: List<WeightEntry>): String =
        entries.joinToString(";") { "${it.epochMs}|${it.kg}|${if (it.own) 1 else 0}|${it.id}" }

    private fun decodeWeights(text: String?): List<WeightEntry> {
        if (text.isNullOrEmpty()) return emptyList()
        return text.split(';').mapNotNull { segment ->
            val parts = segment.split('|', limit = 4)
            if (parts.size < 4) return@mapNotNull null
            val time = parts[0].toLongOrNull() ?: return@mapNotNull null
            val kg = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            WeightEntry(epochMs = time, kg = kg, own = parts[2] == "1", id = parts[3])
        }
    }
}
