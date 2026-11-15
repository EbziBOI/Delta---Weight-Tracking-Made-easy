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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.ebziboi.delta.data.AppAppearance
import io.github.ebziboi.delta.render.AppearanceEngine
import io.github.ebziboi.delta.render.DerivedTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.toArgb
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

val LocalAppearance = staticCompositionLocalOf { AppAppearance() }
val LocalDerivedTheme = staticCompositionLocalOf { DerivedTheme.derive(AppAppearance()) }

/** Geometry follows the active appearance, including dialogs and list rows. */
object Radii {
    /**
     * One corner radius for everything in the app, so buttons, pickers, tiles, the tab
     * bar and dialogs all curve alike. On short controls it rounds the ends fully.
     * The names remain so each use says what it is rounding.
     */
    val corner: androidx.compose.ui.unit.Dp @Composable get() = LocalAppearance.current.cornerRadius.dp
    val card: androidx.compose.ui.unit.Dp @Composable get() = corner
    val tile: androidx.compose.ui.unit.Dp @Composable get() = corner
    val control: androidx.compose.ui.unit.Dp @Composable get() = corner
    val pill: androidx.compose.ui.unit.Dp @Composable get() = corner
}

@Composable
fun DeltaTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var systemMotion by remember { mutableStateOf(systemAllowsMotion(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { systemMotion = systemAllowsMotion(context) }
    // Reading the configuration means Auto and Material You follow the system light/dark switch.
    val night = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val configuration = LocalConfiguration.current
    val derived = remember(settings.appearance, settings.theme, settings.accent, settings.motion, configuration) {
        AppearanceEngine.forApp(context, settings, night)
    }
    val appearance = derived.appearance
    val target = derived.drawColors()
    val motion = settings.motion && !appearance.reduceMotion && systemMotion
    @Composable fun color(value: Int): Int {
        val animated = animateColorAsState(Color(value), tween(if (motion) 280 else 0), label = "theme-color").value.toArgb()
        return if (motion) animated else value
    }
    val background = color(target.bg)
    val surface = color(target.surface)
    val raised = color(target.surfaceHigh)
    val accent = io.github.ebziboi.delta.render.ThemeContrast.readable(color(target.accent), surface, 4.5)
    val onAccent = io.github.ebziboi.delta.render.ThemeContrast.on(accent)
    val palette = target.copy(bg = background, bgTop = color(target.bgTop), surface = surface,
        surfaceHigh = raised, accent = accent,
        accent2 = io.github.ebziboi.delta.render.ThemeContrast.readable(color(target.accent2), onAccent),
        onAccent = onAccent,
        text = io.github.ebziboi.delta.render.ThemeContrast.readable(color(target.text), raised),
        textDim = io.github.ebziboi.delta.render.ThemeContrast.readable(color(target.textDim), raised),
        textFaint = io.github.ebziboi.delta.render.ThemeContrast.readable(color(target.textFaint), raised))
    val scheme = palette.toColorScheme(derived.danger)
    // Material's components (menus, the date picker, text fields) use the same single radius.
    val shapes = remember(appearance.cornerRadius) {
        val corner = RoundedCornerShape(appearance.cornerRadius.dp)
        Shapes(extraSmall = corner, small = corner, medium = corner, large = corner, extraLarge = corner)
    }
    val type = remember(settings.typeStyle) { deltaType(settings.typeStyle) }
    val typography = remember(type) { type.toMaterial() }
    val canvasType = remember(settings.typeStyle) { DeltaFonts.canvas(context, settings.typeStyle) }

    CompositionLocalProvider(
        LocalDeltaColors provides palette,
        LocalAppearance provides appearance,
        LocalDerivedTheme provides derived,
        LocalDeltaType provides type,
        LocalCanvasType provides canvasType,
        LocalMotion provides motion,
    ) {
        MaterialTheme(colorScheme = scheme, shapes = shapes, typography = typography, content = content)
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
private fun DrawColors.toColorScheme(danger: Int): ColorScheme {
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
            error = Color(danger),
            onError = Color(io.github.ebziboi.delta.render.ThemeContrast.on(danger)),
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
            error = Color(danger),
            onError = Color(io.github.ebziboi.delta.render.ThemeContrast.on(danger)),
            surfaceContainerLowest = background,
            surfaceContainerLow = sheet,
            surfaceContainer = card,
            surfaceContainerHigh = sheet,
            surfaceContainerHighest = raised,
        )
    }
}
