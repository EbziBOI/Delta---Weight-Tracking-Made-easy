package io.github.ebziboi.delta.data

/** One weigh-in. Weight is always held in kilograms; units are a display concern. */
data class WeightEntry(
    val epochMs: Long,
    val kg: Double,
    /** True when Delta wrote this record to Health Connect, so Delta may delete it. */
    val own: Boolean,
    /** Health Connect record id. */
    val id: String,
)

enum class WeightUnit(val label: String) {
    KG("kg"),
    LB("lb"),
    ST_LB("st & lb"),
}

/**
 * The look of the app and, unless the widgets are told otherwise, the widgets.
 * Saved by name, so entries can be reordered or added freely; a saved name that
 * no longer exists falls back to the default.
 */
enum class ThemeStyle(val label: String, val detail: String) {
    EMBER("Ember", "Smoked glass with warm orange light"),
    AURORA("Aurora", "Deep glass lit by soft colour"),
    MIDNIGHT("Midnight", "Navy glass, cool and calm"),
    GRAPHITE("Graphite", "Neutral smoked glass"),
    NEON("Neon", "Black glass with a glowing edge"),
    LIGHT("Light", "Frosted white glass"),
    AUTO("Auto", "Light by day, Aurora by night"),
    DYNAMIC("Material You", "Colours from your wallpaper");

    /** True or false for a theme that is always light or always dark; null when it follows the phone. */
    val fixedLight: Boolean?
        get() = when (this) {
            LIGHT -> true
            EMBER, AURORA, MIDNIGHT, GRAPHITE, NEON -> false
            AUTO, DYNAMIC -> null
        }

    val followsSystem: Boolean get() = fixedLight == null
}

/**
 * Accent colours as ARGB. [onDark] suits dark themes and [onLight] keeps contrast
 * on light ones. Each accent has a partner hue that the glass themes use for the
 * second wash of colour behind the glass, so the shading never looks flat.
 */
enum class Accent(
    val label: String,
    val onDark: Long,
    val onLight: Long,
    val partnerDark: Long,
    val partnerLight: Long,
) {
    SKY("Sky", 0xFF7DD3FC, 0xFF2478D4, 0xFFA78BFA, 0xFF7C5CE6),
    MINT("Mint", 0xFF2FE0BD, 0xFF0B8F78, 0xFF38BDF8, 0xFF1C8BD1),
    LIME("Lime", 0xFFC5F74F, 0xFF5B8A08, 0xFF2FE0BD, 0xFF0E9C80),
    ORANGE("Orange", 0xFFFF9F45, 0xFFD2620A, 0xFFFB7185, 0xFFD9445F),
    VIOLET("Violet", 0xFFA78BFA, 0xFF6A45E0, 0xFFF472B6, 0xFFC93F8E),
    ROSE("Rose", 0xFFFB7185, 0xFFD43358, 0xFFFDBA74, 0xFFD97A1E),
}

/** How the weight graph is drawn. The app and the widgets each have their own choice. */
enum class ChartStyle(val label: String, val detail: String) {
    TREND("Trend line", "A smoothed line through your weigh-ins, with each one as a faint dot"),
    CONNECTED("Connected dots", "Every weigh-in as a dot, joined to the next"),
}

/**
 * The material the widgets are made of. [defaultDark] and [defaultLight] are the
 * starting opacity of the glass in each mode: light glass needs more body than
 * dark glass to keep dark text readable over a busy wallpaper.
 */
enum class WidgetGlass(val label: String, val detail: String, val defaultDark: Float, val defaultLight: Float) {
    SOLID("Solid", "Fully opaque: the wallpaper does not show through", 1f, 1f),
    FROSTED("Frosted", "Partly see-through: the wallpaper shows softly", 0.55f, 0.72f),
    CLEAR("Clear", "Mostly see-through: a light tint over the wallpaper", 0.14f, 0.24f),
}

/** Whether the widgets follow the app's theme or switch with the phone's dark mode. */
enum class WidgetTheme(val label: String, val detail: String) {
    APP("Same as app", "Widgets use the theme you picked for the app"),
    SYSTEM("Follow phone", "Light glass by day, dark glass when the phone is in dark mode"),
    LIGHT("Always light", "Light glass all the time"),
    DARK("Always dark", "Dark glass all the time"),
}

/** Pairings of typefaces. Each sets the faces for headings, numbers and small labels. */
enum class TypeStyle(val label: String, val detail: String) {
    EDITORIAL("Editorial", "Serif headlines, grotesk numbers, mono labels"),
    MODERN("Modern", "Clean grotesk throughout"),
    STUDIO("Studio", "Bold geometric headings, editorial accents, precise mono labels"),
    TECHNICAL("Technical", "Monospaced numbers and headings"),
}

/** The colour of a widget's text: chosen for contrast with its background, or always light or dark. */
enum class TextContrast(val label: String) { AUTO("Automatic"), LIGHT("Light"), DARK("Dark") }

/**
 * What a single home-screen widget shows besides its main figure. These are set
 * per widget from the home screen and never saved with the app's own settings.
 */
data class WidgetContent(
    /** The secondary line: how far there is to go. */
    val showDetail: Boolean = true,
    /** The progress bar on the Journey widget. */
    val showBar: Boolean = true,
    /** The target beside the graph on the Trend and Journey widgets. */
    val showTarget: Boolean = true,
    /** Ahead, on track or behind, on the Pace widget. */
    val showStatus: Boolean = true,
    val text: TextContrast = TextContrast.AUTO,
) : java.io.Serializable

data class AppSettings(
    val onboardingDone: Boolean = false,
    val targetKg: Double? = null,
    val targetBodyFat: Double? = null,
    /** Day the journey started. Null until onboarding, when it defaults to 1 September. */
    val startEpochDay: Long? = null,
    /** Optional goal date. */
    val targetEpochDay: Long? = null,
    val unit: WeightUnit = WeightUnit.KG,
    val theme: ThemeStyle = ThemeStyle.EMBER,
    val accent: Accent = Accent.ORANGE,
    val typeStyle: TypeStyle = TypeStyle.MODERN,
    /** Slow drifting light behind the glass. Off also stops it when the phone asks for less motion. */
    val motion: Boolean = true,
    /** The graph on the Progress screen. */
    val appChart: ChartStyle = ChartStyle.TREND,
    /** The graph on the Trend and Journey widgets. */
    val widgetChart: ChartStyle = ChartStyle.TREND,
    /** A dashed line from where you are to the target, at the current pace. */
    val showProjection: Boolean = true,
    val widgetGlass: WidgetGlass = WidgetGlass.FROSTED,
    val widgetTheme: WidgetTheme = WidgetTheme.APP,
    /** Opacity of widget glass, 0 to 1, when the widget is dark. Ignored for solid widgets. */
    val widgetOpacityDark: Float = WidgetGlass.FROSTED.defaultDark,
    /** Opacity of widget glass, 0 to 1, when the widget is light. Ignored for solid widgets. */
    val widgetOpacityLight: Float = WidgetGlass.FROSTED.defaultLight,
    val lastSyncMs: Long = 0L,
    /** The selected launcher icon alias, independent of the app theme. */
    val appIcon: String = "Classic",
    /** Null preserves a saved legacy theme until appearance controls are first changed. */
    val appearance: AppAppearance? = null,
    val savedGlassAppearance: AppAppearance? = null,
    val savedTubikAppearance: AppAppearance? = null,
    val savedGlassUsesLegacy: Boolean = false,
    /** Per-widget display options, filled in only while drawing a widget that has its own style. */
    val widgetContent: WidgetContent = WidgetContent(),
    /** A placed widget may override the dashboard module color. Not a global preference. */
    val widgetBackgroundOverride: Int? = null,
    /** A placed widget's chart window, applied by WidgetStyle rather than global preferences. */
    val widgetTimeRange: WidgetTimeRange = WidgetTimeRange.JOURNEY,
)
