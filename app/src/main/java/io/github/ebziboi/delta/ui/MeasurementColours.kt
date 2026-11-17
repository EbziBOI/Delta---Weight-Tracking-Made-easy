package io.github.ebziboi.delta.ui

import io.github.ebziboi.delta.render.DrawColors
import io.github.ebziboi.delta.render.ThemeContrast

/** Keep the two measurement identities distinct even when the chosen accent is green. */
internal fun bodyFatInk(colors:DrawColors):Int {
    val a=FloatArray(3);val b=FloatArray(3)
    android.graphics.Color.colorToHSV(colors.accent,a);android.graphics.Color.colorToHSV(colors.good,b)
    val difference=kotlin.math.abs(a[0]-b[0]).let {minOf(it,360-it)}
    return ThemeContrast.readable(if(difference<40) 0xFF6DA7FF.toInt() else colors.good,colors.bg)
}
