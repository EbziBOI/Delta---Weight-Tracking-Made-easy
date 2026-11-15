package io.github.ebziboi.delta.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
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
// A file damaged on disk is replaced rather than failing every later write: settings fall back to
// their defaults and the weigh-in copy is simply read again from Health Connect.
private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "delta_settings",
    corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler { emptyPreferences() },
)
private val Context.weightStore: DataStore<Preferences> by preferencesDataStore(
    name = "delta_weights",
    corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler { emptyPreferences() },
)

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
        val TYPE_STYLE = stringPreferencesKey("type_style")
        val MOTION = booleanPreferencesKey("motion")
        val APP_CHART = stringPreferencesKey("app_chart")
        val WIDGET_CHART = stringPreferencesKey("widget_chart")
        val PROJECTION = booleanPreferencesKey("projection")
        val WIDGET_GLASS = stringPreferencesKey("widget_glass")
        val WIDGET_THEME = stringPreferencesKey("widget_theme")
        val WIDGET_OPACITY_DARK = floatPreferencesKey("widget_opacity_dark")
        val WIDGET_OPACITY_LIGHT = floatPreferencesKey("widget_opacity_light")
        val LAST_SYNC = longPreferencesKey("last_sync")
        val WEIGHTS = stringPreferencesKey("weights")
        val APPEARANCE = stringPreferencesKey("appearance_v1")
        val GLASS_APPEARANCE = stringPreferencesKey("glass_appearance_v1")
        val TUBIK_APPEARANCE = stringPreferencesKey("tubik_appearance_v1")
        val GLASS_LEGACY = booleanPreferencesKey("glass_design_legacy")
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

    private fun decode(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        val glass = named(prefs[Keys.WIDGET_GLASS], defaults.widgetGlass)
        return AppSettings(
            onboardingDone = prefs[Keys.ONBOARDED] ?: false,
            targetKg = prefs[Keys.TARGET_KG],
            startEpochDay = prefs[Keys.START_DAY],
            targetEpochDay = prefs[Keys.TARGET_DAY],
            unit = named(prefs[Keys.UNIT], defaults.unit),
            theme = named(prefs[Keys.THEME], defaults.theme),
            accent = named(prefs[Keys.ACCENT], defaults.accent),
            typeStyle = named(prefs[Keys.TYPE_STYLE], defaults.typeStyle),
            motion = prefs[Keys.MOTION] ?: defaults.motion,
            appChart = named(prefs[Keys.APP_CHART], defaults.appChart),
            widgetChart = named(prefs[Keys.WIDGET_CHART], defaults.widgetChart),
            showProjection = prefs[Keys.PROJECTION] ?: defaults.showProjection,
            widgetGlass = glass,
            widgetTheme = named(prefs[Keys.WIDGET_THEME], defaults.widgetTheme),
            // Until the user moves a slider, each mode starts from the chosen glass's own default.
            widgetOpacityDark = opacity(prefs[Keys.WIDGET_OPACITY_DARK], glass.defaultDark),
            widgetOpacityLight = opacity(prefs[Keys.WIDGET_OPACITY_LIGHT], glass.defaultLight),
            lastSyncMs = prefs[Keys.LAST_SYNC] ?: 0L,
            savedGlassUsesLegacy = prefs[Keys.GLASS_LEGACY] ?: false,
            savedGlassAppearance = decodeAppearance(prefs[Keys.GLASS_APPEARANCE]),
            savedTubikAppearance = decodeAppearance(prefs[Keys.TUBIK_APPEARANCE]),
            appearance = decodeAppearance(prefs[Keys.APPEARANCE]) ?: if (prefs[Keys.THEME] == null) AppAppearance() else null,
        )
    }

    private fun encode(settings: AppSettings, prefs: MutablePreferences) {
        prefs[Keys.ONBOARDED] = settings.onboardingDone
        put(prefs, Keys.TARGET_KG, settings.targetKg)
        put(prefs, Keys.START_DAY, settings.startEpochDay)
        // A starting weight typed in by hand is no longer used: the first weigh-in always is.
        prefs.remove(Keys.START_KG)
        put(prefs, Keys.TARGET_DAY, settings.targetEpochDay)
        prefs[Keys.UNIT] = settings.unit.name
        prefs[Keys.THEME] = settings.theme.name
        prefs[Keys.ACCENT] = settings.accent.name
        prefs[Keys.TYPE_STYLE] = settings.typeStyle.name
        prefs[Keys.MOTION] = settings.motion
        prefs[Keys.APP_CHART] = settings.appChart.name
        prefs[Keys.WIDGET_CHART] = settings.widgetChart.name
        prefs[Keys.PROJECTION] = settings.showProjection
        prefs[Keys.WIDGET_GLASS] = settings.widgetGlass.name
        prefs[Keys.WIDGET_THEME] = settings.widgetTheme.name
        prefs[Keys.WIDGET_OPACITY_DARK] = opacity(settings.widgetOpacityDark, settings.widgetGlass.defaultDark)
        prefs[Keys.WIDGET_OPACITY_LIGHT] = opacity(settings.widgetOpacityLight, settings.widgetGlass.defaultLight)
        prefs[Keys.LAST_SYNC] = settings.lastSyncMs
        put(prefs, Keys.APPEARANCE, settings.appearance?.let(::encodeAppearance))
        put(prefs, Keys.GLASS_APPEARANCE, settings.savedGlassAppearance?.let(::encodeAppearance))
        put(prefs, Keys.TUBIK_APPEARANCE, settings.savedTubikAppearance?.let(::encodeAppearance))
        prefs[Keys.GLASS_LEGACY] = settings.savedGlassUsesLegacy
    }

    private fun encodeAppearance(value: AppAppearance): String {
        val a = value.normalized()
        return org.json.JSONObject().apply {
            put("designStyle", a.designStyle.name)
            put("style", a.backgroundStyle.name)
            put("base", a.backgroundBase); put("secondary", a.backgroundSecondary); put("accent", a.accent)
            put("intensity", a.accentIntensity); put("blur", a.glassBlurRadius)
            put("opacity", a.glassOpacity); put("radius", a.cornerRadius)
            put("reduceMotion", a.reduceMotion); put("reduceTransparency", a.reduceTransparency)
            a.backgroundImageUri?.let { put("image", it) }
        }.toString()
    }

    private fun decodeAppearance(text: String?): AppAppearance? {
        if (text == null) return null
        return runCatching {
            val json = org.json.JSONObject(text)
            val d = AppAppearance()
            AppAppearance(
                backgroundStyle = named(json.optString("style"), d.backgroundStyle),
                designStyle = named(json.optString("designStyle"), DesignStyle.GLASS),
                backgroundBase = json.optInt("base", d.backgroundBase),
                backgroundSecondary = json.optInt("secondary", d.backgroundSecondary),
                accent = json.optInt("accent", d.accent),
                accentIntensity = json.optDouble("intensity", d.accentIntensity.toDouble()).toFloat(),
                glassBlurRadius = json.optDouble("blur", d.glassBlurRadius.toDouble()).toFloat(),
                glassOpacity = json.optDouble("opacity", d.glassOpacity.toDouble()).toFloat(),
                cornerRadius = json.optDouble("radius", d.cornerRadius.toDouble()).toFloat(),
                reduceMotion = json.optBoolean("reduceMotion", false),
                reduceTransparency = json.optBoolean("reduceTransparency", false),
                backgroundImageUri = json.optString("image").takeIf { it.isNotBlank() && it != "null" },
            ).normalized()
        }.getOrNull()
    }

    /** Keeps an opacity between 0 and 1; anything unreadable becomes [fallback]. */
    private fun opacity(value: Float?, fallback: Float): Float =
        if (value == null || value.isNaN()) fallback else value.coerceIn(0f, 1f)

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
            val kg = parts[1].toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return@mapNotNull null
            WeightEntry(epochMs = time, kg = kg, own = parts[2] == "1", id = parts[3])
        }
    }
}
