package io.github.ebziboi.delta

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.render.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AppearanceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun everyAppearanceControlSurvivesStorageWithoutChangingOtherSettings() = runBlocking {
        val store = SettingsStore(context)
        val original = store.settings.first()
        val custom = AppAppearance(BackgroundStyle.RADIAL_GRADIENT, 0xFF071A20.toInt(), 0xFF36183A.toInt(),
            0xFFFFEE22.toInt(), 0.87f, 37f, 0.33f, 31f, true, true, "file:///example/photo.png")
        try {
            store.update { it.copy(appearance = custom) }
            val restored = SettingsStore(context).settings.first()
            assertEquals(custom, restored.appearance)
            assertEquals(original, restored.copy(appearance = original.appearance))
        } finally { store.update { original } }
    }

    @Test fun arbitraryColorsHaveReadableDerivedRoles() {
        val backgrounds = listOf(0xFF000000, 0xFFFFFFFF, 0xFF747474, 0xFF880000, 0xFF00AA88, 0xFF181B55).map(Long::toInt)
        val accents = listOf(0xFF000000, 0xFFFFFFFF, 0xFFEA5A15, 0xFF4FC3F7, 0xFF8B5CF6, 0xFF34D399).map(Long::toInt)
        for (background in backgrounds) for (accent in accents) for (opacity in listOf(0.06f, 0.12f, 0.95f)) {
            val t = DerivedTheme.derive(AppAppearance(backgroundBase = background, backgroundSecondary = background.inv(), accent = accent, glassOpacity = opacity))
            assertTrue("Button text contrast", ThemeContrast.ratio(t.onAccent, t.accent) >= 4.5)
            assertTrue("Primary text contrast", ThemeContrast.ratio(t.textPrimary, t.surfaceElevated) >= 4.5)
            assertTrue("Secondary text contrast", ThemeContrast.ratio(t.textSecondary, t.surfaceElevated) >= 4.49)
            assertTrue("Muted text contrast", ThemeContrast.ratio(t.textMuted, t.surfaceElevated) >= 4.49)
            assertTrue("Background text contrast", ThemeContrast.ratio(t.textPrimary, t.background) >= 6.99)
            assertTrue("Visible focus/accent", ThemeContrast.ratio(t.accent, t.surface) >= 4.49)
        }
    }

    @Test fun reducedTransparencyIsOpaqueAndInvalidValuesAreClamped() {
        val normal = AppAppearance(accentIntensity = Float.NaN, glassBlurRadius = Float.POSITIVE_INFINITY,
            glassOpacity = -12f, cornerRadius = 200f, reduceTransparency = true).normalized()
        assertTrue(normal.accentIntensity.isFinite())
        assertTrue(normal.glassBlurRadius in 0f..48f)
        assertTrue(normal.cornerRadius in 20f..32f)
        val t = DerivedTheme.derive(normal)
        assertEquals(255, t.glass ushr 24)
        assertEquals(255, t.drawColors().glass.fillTop ushr 24)
    }

    @Test fun widgetsFollowCustomColorsRoundnessAndAccessibility() {
        val day = LocalDate.now().minusDays(74)
        val original = AppSettings(targetKg = 80.0, startEpochDay = day.toEpochDay(),
            appearance = AppearancePreset.ICE.appearance())
        val snapshot = ProgressCalculator.compute(SampleData.entries(day), original)
        val ice = WidgetBitmaps.render(context, WidgetKind.TREND, 350f, 200f, snapshot, original)
        val rose = WidgetBitmaps.render(context, WidgetKind.TREND, 350f, 200f, snapshot,
            original.copy(appearance = AppearancePreset.ROSE.appearance()))
        assertFalse("Widget must change immediately with the selected appearance", ice.sameAs(rose))
        val solid = WidgetBitmaps.render(context, WidgetKind.TREND, 350f, 200f, snapshot,
            original.copy(widgetGlass = WidgetGlass.CLEAR, appearance = original.appearance!!.copy(reduceTransparency = true)))
        assertEquals(255, solid.getPixel(solid.width / 2, solid.height - 8) ushr 24)
        assertEquals(0, solid.getPixel(0, 0) ushr 24)
        listOf(ice, rose, solid).forEach(Bitmap::recycle)
    }

    @Test fun customAppearanceAndLegacyThemeModesRemainIndependent() {
        val light = AppearanceEngine.forApp(context, AppSettings(theme = ThemeStyle.LIGHT, appearance = null))
        assertTrue("Legacy light mode stays light", light.isLight)
        assertTrue(ThemeContrast.luminance(light.background) > 0.5)
        val custom = AppearancePreset.MINT.appearance()
        val settings = AppSettings(theme = ThemeStyle.EMBER, appearance = custom)
        assertEquals(custom, AppearanceEngine.resolve(context, settings))
        assertFalse(Palettes.widgetFollowsSystem(settings))
        val following = settings.copy(widgetTheme = WidgetTheme.SYSTEM)
        assertTrue(Palettes.widgetFollowsSystem(following))
        assertTrue(AppearanceEngine.forWidget(context, following, false).isLight)
        assertFalse(AppearanceEngine.forWidget(context, following, true).isLight)
        assertEquals(custom.accent, AppearanceEngine.resolve(context, settings.copy(theme = ThemeStyle.GRAPHITE)).accent)
    }
}
