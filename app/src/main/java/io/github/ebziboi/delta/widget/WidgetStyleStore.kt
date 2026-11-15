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
    /** The graph on Trend and Journey widgets; null follows the app's default. */
    val chart: ChartStyle? = null,
    /** The dashed path to the goal on Trend and Journey widgets; null follows the app's default. */
    val projection: Boolean? = null,
    /** Solid, gradient, glow, mesh or photo; null follows the app. */
    val backgroundStyle: BackgroundStyle? = null,
    /** A photo copied into Delta's own storage for this widget, as a file URI. */
    val backgroundImage: String? = null,
    /** How strongly the accent glows across the glass, 0 to 1; null follows the app. */
    val accentIntensity: Float? = null,
    val content: WidgetContent = WidgetContent(),
) : java.io.Serializable {
    fun apply(settings: AppSettings, appearance: AppAppearance): AppSettings {
        if (useAppSettings) return settings
        val style = backgroundStyle ?: appearance.backgroundStyle
        val image = backgroundImage ?: appearance.backgroundImageUri
        // A photo style without a photo falls back to the soft mesh rather than a blank pane.
        val shown = if (style == BackgroundStyle.IMAGE && image == null) BackgroundStyle.MESH else style
        val custom = appearance.copy(backgroundBase = background ?: appearance.backgroundBase,
            accent = accent ?: appearance.accent, cornerRadius = cornerRadius ?: appearance.cornerRadius,
            backgroundStyle = shown, backgroundImageUri = image,
            accentIntensity = accentIntensity ?: appearance.accentIntensity).normalized()
        val mode = if (theme == WidgetTheme.APP && settings.appearance == null && settings.theme.followsSystem)
            WidgetTheme.SYSTEM else theme
        return settings.copy(appearance = custom, widgetGlass = glass, widgetTheme = mode,
            widgetOpacityDark = opacityDark.coerceIn(0f, 1f), widgetOpacityLight = opacityLight.coerceIn(0f, 1f),
            widgetChart = chart ?: settings.widgetChart, showProjection = projection ?: settings.showProjection,
            widgetContent = content, widgetBackgroundOverride = background)
    }
    companion object {
        /** A widget's own style, starting from the defaults every new widget gets. */
        fun from(settings: AppSettings) = WidgetStyle(useAppSettings = false, glass = settings.widgetGlass,
            theme = settings.widgetTheme, opacityDark = settings.widgetOpacityDark, opacityLight = settings.widgetOpacityLight,
            chart = settings.widgetChart, projection = settings.showProjection)
    }
}

class WidgetStyleStore(context: Context) {
    private val app = context.applicationContext
    private fun key(id: Int) = stringPreferencesKey("widget_$id")
    fun style(id: Int) = app.widgetStyles.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { decode(it[key(id)]) }
    suspend fun save(id: Int, style: WidgetStyle) { require(id > 0); app.widgetStyles.edit { it[key(id)] = encode(style) } }
    suspend fun delete(ids: IntArray) {
        app.widgetStyles.edit { p ->
            ids.forEach { id ->
                decode(p[key(id)])?.backgroundImage?.let(::deletePhoto)
                p.remove(key(id))
            }
        }
    }

    /** Deletes a widget photo, but only one Delta itself copied into its storage. */
    fun deletePhoto(uri: String) {
        val path = android.net.Uri.parse(uri).path ?: return
        val file = java.io.File(path)
        if (file.parentFile == app.filesDir && file.name.startsWith(PHOTO_PREFIX)) file.delete()
    }

    companion object {
        const val PHOTO_PREFIX = "widget-bg-"
    }
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
        s.chart?.let { put("chart", it.name) }; s.projection?.let { put("projection", it) }
        s.backgroundStyle?.let { put("bgStyle", it.name) }; s.backgroundImage?.let { put("bgImage", it) }
        s.accentIntensity?.let { put("glow", it) }
        put("showDetail", s.content.showDetail); put("showBar", s.content.showBar)
        put("showTarget", s.content.showTarget); put("showStatus", s.content.showStatus); put("text", s.content.text.name)
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
            accent = if (j.has("accent")) j.getInt("accent") else null,
            chart = ChartStyle.entries.firstOrNull { it.name == j.optString("chart") },
            projection = if (j.has("projection")) j.getBoolean("projection") else null,
            backgroundStyle = BackgroundStyle.entries.firstOrNull { it.name == j.optString("bgStyle") },
            backgroundImage = j.optString("bgImage").takeIf { it.isNotBlank() },
            accentIntensity = if (j.has("glow")) amount("glow", 0.55f) else null,
            content = WidgetContent(showDetail = j.optBoolean("showDetail", true), showBar = j.optBoolean("showBar", true),
                showTarget = j.optBoolean("showTarget", true), showStatus = j.optBoolean("showStatus", true),
                text = TextContrast.entries.firstOrNull { it.name == j.optString("text") } ?: TextContrast.AUTO))
    }.getOrNull()
}
