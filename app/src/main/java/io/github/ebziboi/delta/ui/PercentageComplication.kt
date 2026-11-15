package io.github.ebziboi.delta.ui

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.*
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.render.PercentageArt

@Composable
fun PercentageComplication(fraction: Float?, design: PercentageDesign, modifier: Modifier = Modifier) {
    val matte = LocalAppearance.current.designStyle == DesignStyle.TUBIK
    if (design == PercentageDesign.ADAPTIVE) {
        if (matte) DashboardDial(fraction, modifier) else DeltaTrack(fraction, modifier)
    } else {
        val colors = LocalDeltaColors.current; val type = LocalCanvasType.current
        Canvas(modifier.semantics { contentDescription = "${design.label}: ${fraction?.let { "${(it.coerceIn(0f,1f)*100).toInt()} percent" } ?: "No progress yet"}" }) {
            drawIntoCanvas { PercentageArt.draw(it.nativeCanvas,RectF(0f,0f,size.width,size.height),fraction,design,colors,type,matte) }
        }
    }
}
