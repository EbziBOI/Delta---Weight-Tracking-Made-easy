package io.github.ebziboi.delta.ui

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
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
import io.github.ebziboi.delta.render.CanvasType
import io.github.ebziboi.delta.render.DeltaFonts
import io.github.ebziboi.delta.render.DrawColors
import io.github.ebziboi.delta.render.Palettes

/** The palette the screens draw with. The widgets use the same one. */
val LocalDeltaColors = staticCompositionLocalOf<DrawColors> {
    error("DeltaTheme is missing above this composable")
}

/** The type pairing as canvas faces, for anything drawn by hand: the delta, the graph, widget previews. */
val LocalCanvasType = staticCompositionLocalOf<CanvasType> {
    error("DeltaTheme is missing above this composable")
}

/**
 * Whether decorative motion runs: the drifting light behind the glass, entrances,
 * count-ups. Off when the user turns it off in Delta, or switches animations off
 * for the whole phone. Functional animation (a tab sliding across) still runs.
 */
val LocalMotion = staticCompositionLocalOf { true }

/** Corner radii. Glass reads best with generous, consistent curves. */
object Radii {
    val card = 28.dp
    val tile = 22.dp
    val control = 16.dp
    val pill = 999.dp
}

private val DeltaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(Radii.tile),
    large = RoundedCornerShape(Radii.card),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun DeltaTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Reading the configuration means Auto and Material You follow the system light/dark switch.
    val night = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val palette = remember(settings.theme, settings.accent, night) {
        Palettes.resolve(context, settings.theme, settings.accent, night)
    }
    val scheme = remember(palette) { palette.toColorScheme() }
    val type = remember(settings.typeStyle) { deltaType(settings.typeStyle) }
    val typography = remember(type) { type.toMaterial() }
    val canvasType = remember(settings.typeStyle) { DeltaFonts.canvas(context, settings.typeStyle) }
    val systemMotion = remember { systemAllowsMotion(context) }

    CompositionLocalProvider(
        LocalDeltaColors provides palette,
        LocalDeltaType provides type,
        LocalCanvasType provides canvasType,
        LocalMotion provides (settings.motion && systemMotion),
    ) {
        MaterialTheme(colorScheme = scheme, shapes = DeltaShapes, typography = typography, content = content)
    }
}

/** False when the phone's animator duration scale is zero (animations switched off in accessibility or developer settings). */
private fun systemAllowsMotion(context: Context): Boolean =
    runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }.getOrDefault(true)

/**
 * Maps the palette onto Material's roles so stock components match the rest of
 * the app. Containers that hold text over other content (dialogs, sheets, the
 * date picker) use the near-opaque glass scrim so they stay readable.
 */
private fun DrawColors.toColorScheme(): ColorScheme {
    val accentColor = Color(accent)
    val onAccentColor = Color(onAccent)
    val background = Color(bg)
    val card = Color(surface)
    val raised = Color(surfaceHigh)
    val sheet = Color(glass.scrim)
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
            tertiary = Color(accent2),
            onTertiary = onAccentColor,
            background = background,
            onBackground = ink,
            surface = background,
            onSurface = ink,
            surfaceVariant = raised,
            onSurfaceVariant = dim,
            surfaceTint = Color.Transparent,
            inverseSurface = ink,
            inverseOnSurface = background,
            inversePrimary = accentColor,
            outline = line,
            outlineVariant = line,
            error = Color(warn),
            surfaceContainerLowest = background,
            surfaceContainerLow = sheet,
            surfaceContainer = card,
            surfaceContainerHigh = sheet,
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
            tertiary = Color(accent2),
            onTertiary = onAccentColor,
            background = background,
            onBackground = ink,
            surface = background,
            onSurface = ink,
            surfaceVariant = raised,
            onSurfaceVariant = dim,
            surfaceTint = Color.Transparent,
            inverseSurface = ink,
            inverseOnSurface = background,
            inversePrimary = accentColor,
            outline = line,
            outlineVariant = line,
            error = Color(warn),
            surfaceContainerLowest = background,
            surfaceContainerLow = sheet,
            surfaceContainer = card,
            surfaceContainerHigh = sheet,
            surfaceContainerHighest = raised,
        )
    }
}
