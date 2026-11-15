package io.github.ebziboi.delta.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.ebziboi.delta.data.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.IOException

private val Context.widgetStyles by preferencesDataStore("delta_widget_styles")

/** Overrides belong to a placed widget, so editing one never changes its neighbours. */
data class WidgetStyle(
    val useAppSettings: Boolean = true,
    val glass: WidgetGlass = WidgetGlass.FROSTED,
    val theme: WidgetTheme = WidgetTheme.APP,
    val opacityDark: Float = WidgetGlass.FROSTED.defaultDark,
    val opacityLight: Float = WidgetGlass.FROSTED.defaultLight,
    val cornerRadius: Float? = null,
    val background: Int? = null,
    val accent: Int? = null,
) : java.io.Serializable {
    fun apply(settings: AppSettings, appearance: AppAppearance): AppSettings {
        if (useAppSettings) return settings
        val custom = appearance.copy(backgroundBase = background ?: appearance.backgroundBase,
            accent = accent ?: appearance.accent, cornerRadius = cornerRadius ?: appearance.cornerRadius).normalized()
        val mode = if (theme == WidgetTheme.APP && settings.appearance == null && settings.theme.followsSystem)
            WidgetTheme.SYSTEM else theme
        return settings.copy(appearance = custom, widgetGlass = glass, widgetTheme = mode,
            widgetOpacityDark = opacityDark.coerceIn(0f, 1f), widgetOpacityLight = opacityLight.coerceIn(0f, 1f))
    }
    companion object {
        fun from(settings: AppSettings) = WidgetStyle(glass = settings.widgetGlass, theme = settings.widgetTheme,
            opacityDark = settings.widgetOpacityDark, opacityLight = settings.widgetOpacityLight)
    }
}

class WidgetStyleStore(context: Context) {
    private val app = context.applicationContext
    private fun key(id: Int) = stringPreferencesKey("widget_$id")
    fun style(id: Int) = app.widgetStyles.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { decode(it[key(id)]) }
    suspend fun save(id: Int, style: WidgetStyle) { require(id > 0); app.widgetStyles.edit { it[key(id)] = encode(style) } }
    suspend fun delete(ids: IntArray) { app.widgetStyles.edit { p -> ids.forEach { p.remove(key(it)) } } }
    suspend fun restore(oldIds: IntArray, newIds: IntArray) {
        app.widgetStyles.edit { p ->
            val saved = oldIds.zip(newIds).map { (old, new) -> new to p[key(old)] }
            oldIds.forEach { p.remove(key(it)) }
            saved.forEach { (id, value) -> if (value != null) p[key(id)] = value }
        }
    }
    private fun encode(s: WidgetStyle) = JSONObject().apply {
        put("follow", s.useAppSettings); put("glass", s.glass.name); put("theme", s.theme.name)
        put("dark", s.opacityDark); put("light", s.opacityLight)
        s.cornerRadius?.let { put("radius", it) }; s.background?.let { put("background", it) }; s.accent?.let { put("accent", it) }
    }.toString()
    private fun decode(value: String?): WidgetStyle? = runCatching {
        if (value == null) return null
        val j = JSONObject(value)
        fun amount(key: String, fallback: Float) = j.optDouble(key, fallback.toDouble()).toFloat().let {
            if (it.isFinite()) it.coerceIn(0f, 1f) else fallback
        }
        WidgetStyle(useAppSettings = j.optBoolean("follow", true),
            glass = WidgetGlass.entries.firstOrNull { it.name == j.optString("glass") } ?: WidgetGlass.FROSTED,
            theme = WidgetTheme.entries.firstOrNull { it.name == j.optString("theme") } ?: WidgetTheme.APP,
            opacityDark = amount("dark", WidgetGlass.FROSTED.defaultDark), opacityLight = amount("light", WidgetGlass.FROSTED.defaultLight),
            cornerRadius = if (j.has("radius")) j.optDouble("radius", 26.0).toFloat().takeIf { it.isFinite() }?.coerceIn(20f, 32f) else null,
            background = if (j.has("background")) j.getInt("background") else null,
            accent = if (j.has("accent")) j.getInt("accent") else null)
    }.getOrNull()
}
