package io.github.ebziboi.delta.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** A textured backdrop makes both controls visible even with a solid app background. */
@Composable
fun GlassMaterialPreview() {
    val source = remember { HazeState() }
    val colors = LocalDeltaColors.current
    Box(Modifier.fillMaxWidth().height(136.dp).clip(RoundedCornerShape(Radii.corner))
        .semantics { contentDescription = "Glass material preview" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().hazeSource(source)) {
            drawRect(Color(colors.bg))
            val stripe = size.width / 12
            repeat(12) { i ->
                drawRect(Color(if (i % 2 == 0) colors.accent else colors.bgTop).copy(alpha = 0.35f),
                    Offset(i * stripe, 0f), Size(stripe * 0.65f, size.height))
            }
            drawCircle(Color(colors.accent2).copy(alpha = 0.32f), size.height * 0.42f, Offset(size.width * 0.8f, size.height * 0.2f))
        }
        CompositionLocalProvider(LocalGlassBackdrop provides source) {
            Column(Modifier.fillMaxWidth(0.76f).refractiveGlass(RoundedCornerShape(Radii.corner)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Live glass", style = LocalDeltaType.current.title)
                Text("Transparency & blur", style = LocalDeltaType.current.bodySmall)
            }
        }
    }
}
