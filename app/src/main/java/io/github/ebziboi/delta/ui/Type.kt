package io.github.ebziboi.delta.ui

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.ebziboi.delta.R
import io.github.ebziboi.delta.data.TypeStyle

/*
 * Four families, each with a job. The pairing chosen in settings decides which
 * family takes the headings and the numbers; running text is always Manrope.
 * The widgets draw with the same files (render/Fonts.kt).
 */

/** An editorial serif for headlines and the big readouts. */
val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

/** A grotesk with characterful figures, for values. */
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

/** A warm, very readable sans for everything else. */
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

/** A monospace for small labels and the Technical pairing. */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/**
 * Text styles by role. Screens ask for a role ("a number", "a label") rather
 * than a font, so changing the pairing restyles every screen consistently.
 */
@Immutable
data class DeltaType(
    val style: TypeStyle,
    /** The current weight on the Progress screen. */
    val hero: TextStyle,
    /** Screen titles. */
    val display: TextStyle,
    /** A word of emphasis inside a display line: italic serif in Editorial. */
    val displayAccent: TextStyle,
    /** Card and section titles. */
    val title: TextStyle,
    /** Large values in a grid, such as the pace figures. */
    val numberLarge: TextStyle,
    /** Values in tiles and rows. */
    val number: TextStyle,
    /** Small values beside text. */
    val numberSmall: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val bodySmall: TextStyle,
    /** Small capitals above values. Always shown uppercase. */
    val label: TextStyle,
    val button: TextStyle,
)

private val tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

private fun style(
    family: FontFamily,
    weight: FontWeight,
    size: TextUnit,
    lineHeight: TextUnit = TextUnit.Unspecified,
    tracking: TextUnit = TextUnit.Unspecified,
    italic: Boolean = false,
    tabular: Boolean = false,
): TextStyle = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = tracking,
    fontFeatureSettings = if (tabular) "tnum" else null,
    lineHeightStyle = if (lineHeight != TextUnit.Unspecified) tight else null,
)

fun deltaType(style: TypeStyle): DeltaType {
    val body = style(Manrope, FontWeight.Medium, 15.sp, 22.sp)
    val bodyStrong = style(Manrope, FontWeight.Bold, 15.sp, 22.sp)
    val bodySmall = style(Manrope, FontWeight.Medium, 13.sp, 18.sp)
    val button = style(Manrope, FontWeight.Bold, 15.sp, 20.sp, tracking = 0.1.sp)
    return when (style) {
        TypeStyle.EDITORIAL -> DeltaType(
            style = style,
            hero = style(InstrumentSerif, FontWeight.Normal, 84.sp, 84.sp, tracking = (-0.02).em),
            display = style(InstrumentSerif, FontWeight.Normal, 42.sp, 46.sp, tracking = (-0.01).em),
            displayAccent = style(InstrumentSerif, FontWeight.Normal, 42.sp, 46.sp, tracking = (-0.01).em, italic = true),
            title = style(InstrumentSerif, FontWeight.Normal, 25.sp, 30.sp),
            numberLarge = style(SpaceGrotesk, FontWeight.Medium, 26.sp, 30.sp, tracking = (-0.02).em, tabular = true),
            number = style(SpaceGrotesk, FontWeight.SemiBold, 19.sp, 24.sp, tracking = (-0.01).em, tabular = true),
            numberSmall = style(SpaceGrotesk, FontWeight.Medium, 14.sp, 18.sp, tabular = true),
            body = body,
            bodyStrong = bodyStrong,
            bodySmall = bodySmall,
            label = style(JetBrainsMono, FontWeight.Medium, 10.5.sp, 14.sp, tracking = 0.12.em),
            button = button,
        )
        TypeStyle.MODERN -> DeltaType(
            style = style,
            hero = style(SpaceGrotesk, FontWeight.Medium, 72.sp, 74.sp, tracking = (-0.04).em, tabular = true),
            display = style(SpaceGrotesk, FontWeight.Bold, 34.sp, 40.sp, tracking = (-0.03).em),
            displayAccent = style(SpaceGrotesk, FontWeight.Medium, 34.sp, 40.sp, tracking = (-0.03).em),
            title = style(SpaceGrotesk, FontWeight.SemiBold, 19.sp, 24.sp, tracking = (-0.01).em),
            numberLarge = style(SpaceGrotesk, FontWeight.Medium, 26.sp, 30.sp, tracking = (-0.02).em, tabular = true),
            number = style(SpaceGrotesk, FontWeight.SemiBold, 19.sp, 24.sp, tracking = (-0.01).em, tabular = true),
            numberSmall = style(SpaceGrotesk, FontWeight.Medium, 14.sp, 18.sp, tabular = true),
            body = body,
            bodyStrong = bodyStrong,
            bodySmall = bodySmall,
            label = style(Manrope, FontWeight.Bold, 10.5.sp, 14.sp, tracking = 0.1.em),
            button = button,
        )
        TypeStyle.TECHNICAL -> DeltaType(
            style = style,
            hero = style(JetBrainsMono, FontWeight.Medium, 62.sp, 66.sp, tracking = (-0.05).em),
            display = style(JetBrainsMono, FontWeight.Bold, 28.sp, 34.sp, tracking = (-0.03).em),
            displayAccent = style(JetBrainsMono, FontWeight.Normal, 28.sp, 34.sp, tracking = (-0.03).em),
            title = style(JetBrainsMono, FontWeight.Bold, 16.sp, 22.sp, tracking = (-0.01).em),
            numberLarge = style(JetBrainsMono, FontWeight.Medium, 23.sp, 28.sp, tracking = (-0.03).em),
            number = style(JetBrainsMono, FontWeight.Bold, 17.sp, 22.sp, tracking = (-0.02).em),
            numberSmall = style(JetBrainsMono, FontWeight.Medium, 13.sp, 18.sp),
            body = body,
            bodyStrong = bodyStrong,
            bodySmall = bodySmall,
            label = style(JetBrainsMono, FontWeight.Medium, 10.5.sp, 14.sp, tracking = 0.08.em),
            button = button,
        )
    }
}

val LocalDeltaType = staticCompositionLocalOf { deltaType(TypeStyle.EDITORIAL) }

/** Material's text roles mapped onto the pairing, so stock components (dialogs, the date picker) match. */
fun DeltaType.toMaterial(): Typography {
    val heading = display.fontFamily ?: Manrope
    val headingWeight = display.fontWeight ?: FontWeight.Normal
    fun head(size: TextUnit, line: TextUnit) = TextStyle(
        fontFamily = heading,
        fontWeight = headingWeight,
        fontSize = size,
        lineHeight = line,
    )
    fun text(weight: FontWeight, size: TextUnit, line: TextUnit, tracking: TextUnit = 0.sp) = TextStyle(
        fontFamily = Manrope,
        fontWeight = weight,
        fontSize = size,
        lineHeight = line,
        letterSpacing = tracking,
    )
    return Typography(
        displayLarge = head(57.sp, 64.sp),
        displayMedium = head(45.sp, 52.sp),
        displaySmall = head(36.sp, 44.sp),
        headlineLarge = head(32.sp, 40.sp),
        headlineMedium = head(28.sp, 36.sp),
        headlineSmall = head(24.sp, 32.sp),
        titleLarge = head(22.sp, 28.sp),
        titleMedium = text(FontWeight.Bold, 16.sp, 24.sp),
        titleSmall = text(FontWeight.Bold, 14.sp, 20.sp),
        bodyLarge = text(FontWeight.Medium, 16.sp, 24.sp),
        bodyMedium = text(FontWeight.Medium, 14.sp, 20.sp),
        bodySmall = text(FontWeight.Medium, 12.sp, 16.sp),
        labelLarge = text(FontWeight.Bold, 14.sp, 20.sp, 0.1.sp),
        labelMedium = text(FontWeight.SemiBold, 12.sp, 16.sp, 0.3.sp),
        labelSmall = text(FontWeight.SemiBold, 11.sp, 16.sp, 0.4.sp),
    )
}

/** Compact, industrial headings and tabular figures for the alternate dashboard system. */
fun dashboardType(style: TypeStyle): DeltaType {
    val base = deltaType(style)
    val condensed = FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD))
    return base.copy(display = base.display.copy(fontFamily = condensed, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp),
        title = base.title.copy(fontFamily = condensed, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 27.sp),
        label = base.label.copy(letterSpacing = 1.sp), button = base.button.copy(letterSpacing = 0.4.sp))
}
