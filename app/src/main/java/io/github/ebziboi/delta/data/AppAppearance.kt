package io.github.ebziboi.delta.data

/** Presentation preferences only. ARGB values keep storage and widget rendering independent of Compose. */
data class AppAppearance(
    val backgroundStyle: BackgroundStyle = BackgroundStyle.MESH,
    val backgroundBase: Int = 0xFF050505.toInt(),
    val backgroundSecondary: Int = 0xFF201008.toInt(),
    val accent: Int = 0xFFEA5A15.toInt(),
    val accentIntensity: Float = 0.55f,
    val glassBlurRadius: Float = 24f,
    val glassOpacity: Float = 0.12f,
    val cornerRadius: Float = 26f,
    val reduceMotion: Boolean = false,
    val reduceTransparency: Boolean = false,
    val backgroundImageUri: String? = null,
) {
    fun normalized(): AppAppearance = copy(
        backgroundBase = backgroundBase or (0xFF shl 24),
        backgroundSecondary = backgroundSecondary or (0xFF shl 24),
        accent = accent or (0xFF shl 24),
        accentIntensity = accentIntensity.valid(0f, 1f, 0.55f),
        glassBlurRadius = glassBlurRadius.valid(0f, 48f, 24f),
        glassOpacity = glassOpacity.valid(0.06f, 0.95f, 0.12f),
        cornerRadius = cornerRadius.valid(20f, 32f, 26f),
    )

    private fun Float.valid(min: Float, max: Float, fallback: Float) =
        if (isFinite()) coerceIn(min, max) else fallback
}

enum class BackgroundStyle(val label: String) {
    SOLID("Solid"), VERTICAL_GRADIENT("Vertical gradient"), RADIAL_GRADIENT("Radial gradient"), MESH("Mesh"), IMAGE("Image")
}

enum class AppearancePreset(val label: String, val base: Long, val highlight: Long) {
    EMBER("Ember", 0xFF050505, 0xFFEA5A15),
    ICE("Ice", 0xFF04070D, 0xFF4FC3F7),
    VIOLET("Violet", 0xFF07040D, 0xFF8B5CF6),
    MINT("Mint", 0xFF03100B, 0xFF34D399),
    ROSE("Rose", 0xFF0D0408, 0xFFFB7185),
    MONO("Mono", 0xFF0A0A0A, 0xFFE5E5E5);

    fun appearance(): AppAppearance {
        fun tint(shift: Int): Int {
            val b = (base.toInt() ushr shift) and 255
            val a = (highlight.toInt() ushr shift) and 255
            return ((b * 0.88f + a * 0.12f).toInt() shl shift)
        }
        val secondary = (0xFF shl 24) or tint(16) or tint(8) or tint(0)
        return AppAppearance(backgroundBase = base.toInt(), backgroundSecondary = secondary, accent = highlight.toInt())
    }
}
