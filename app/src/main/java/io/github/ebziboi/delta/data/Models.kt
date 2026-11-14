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

enum class ThemeStyle(val label: String) {
    MIDNIGHT("Midnight"),
    GRAPHITE("Graphite"),
    NEON("Neon"),
    LIGHT("Light"),
    DYNAMIC("Material You"),
}

/** Accent colours as ARGB. [onDark] suits dark themes, [onLight] keeps contrast on light ones. */
enum class Accent(val label: String, val onDark: Long, val onLight: Long) {
    SKY("Sky", 0xFF7DD3FC, 0xFF2478D4),
    MINT("Mint", 0xFF2FE0BD, 0xFF0B8F78),
    LIME("Lime", 0xFFC5F74F, 0xFF5B8A08),
    ORANGE("Orange", 0xFFFF9F45, 0xFFD2620A),
    VIOLET("Violet", 0xFFA78BFA, 0xFF6A45E0),
    ROSE("Rose", 0xFFFB7185, 0xFFD43358),
}

data class AppSettings(
    val onboardingDone: Boolean = false,
    val targetKg: Double? = null,
    /** Day the journey started. Null until onboarding, when it defaults to 1 September. */
    val startEpochDay: Long? = null,
    /** Set only when the user corrects the starting weight; otherwise the first weigh-in is used. */
    val startKgOverride: Double? = null,
    /** Optional goal date. */
    val targetEpochDay: Long? = null,
    val unit: WeightUnit = WeightUnit.KG,
    val theme: ThemeStyle = ThemeStyle.MIDNIGHT,
    val accent: Accent = Accent.SKY,
    val lastSyncMs: Long = 0L,
)
