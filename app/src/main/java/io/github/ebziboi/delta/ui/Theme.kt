package io.github.ebziboi.delta.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.render.DrawColors
import io.github.ebziboi.delta.render.Palettes

/** The palette the screens draw with. The widgets use the same one. */
val LocalDeltaColors = staticCompositionLocalOf<DrawColors> {
    error("DeltaTheme is missing above this composable")
}

private val DeltaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun DeltaTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Reading the configuration means Material You follows the system light/dark switch.
    val uiMode = LocalConfiguration.current.uiMode
    val palette = remember(settings.theme, settings.accent, uiMode) {
        Palettes.resolve(context, settings.theme, settings.accent)
    }
    val scheme = remember(palette) { palette.toColorScheme() }

    CompositionLocalProvider(LocalDeltaColors provides palette) {
        MaterialTheme(colorScheme = scheme, shapes = DeltaShapes, content = content)
    }
}

/** Maps the palette onto Material's roles so stock components match the rest of the app. */
private fun DrawColors.toColorScheme(): ColorScheme {
    val accentColor = Color(accent)
    val onAccentColor = Color(onAccent)
    val background = Color(bg)
    val card = Color(surface)
    val raised = Color(surfaceHigh)
    val ink = Color(text)
    val dim = Color(textDim)
    val line = Color(outline)

    return if (isLight) {
        lightColorScheme(
            primary = accentColor,
            onPrimary = onAccentColor,
            primaryContainer = raised,
            onPrimaryContainer = ink,
            secondary = accentColor,
            onSecondary = onAccentColor,
            secondaryContainer = raised,
            onSecondaryContainer = ink,
            tertiary = accentColor,
            onTertiary = onAccentColor,
            background = background,
            onBackground = ink,
            surface = background,
            onSurface = ink,
            surfaceVariant = raised,
            onSurfaceVariant = dim,
            surfaceTint = Color.Transparent,
            outline = line,
            outlineVariant = line,
            surfaceContainerLowest = background,
            surfaceContainerLow = card,
            surfaceContainer = card,
            surfaceContainerHigh = card,
            surfaceContainerHighest = raised,
        )
    } else {
        darkColorScheme(
            primary = accentColor,
            onPrimary = onAccentColor,
            primaryContainer = raised,
            onPrimaryContainer = ink,
            secondary = accentColor,
            onSecondary = onAccentColor,
            secondaryContainer = raised,
            onSecondaryContainer = ink,
            tertiary = accentColor,
            onTertiary = onAccentColor,
            background = background,
            onBackground = ink,
            surface = background,
            onSurface = ink,
            surfaceVariant = raised,
            onSurfaceVariant = dim,
            surfaceTint = Color.Transparent,
            outline = line,
            outlineVariant = line,
            surfaceContainerLowest = background,
            surfaceContainerLow = card,
            surfaceContainer = card,
            surfaceContainerHigh = raised,
            surfaceContainerHighest = raised,
        )
    }
}
