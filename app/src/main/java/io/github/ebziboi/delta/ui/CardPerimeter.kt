package io.github.ebziboi.delta.ui

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

/** An inset progress stroke that follows the actual rounded card, including its corners. */
@Composable
fun Modifier.cardPerimeter(enabled: Boolean, fraction: Float?): Modifier {
    if (!enabled) return this
    val colors=LocalDeltaColors.current
    val motion=LocalMotion.current
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ready=true }
    val shown by animateFloatAsState(if(ready) (fraction ?: 0f).coerceIn(0f,1f) else 0f,
        tween(if(motion) 650 else 0,easing=FastOutSlowInEasing),label="card-perimeter")
    val cornerRadius=Radii.card
    return drawWithContent {
        drawContent()
        drawIntoCanvas { canvas ->
            val inset=4.dp.toPx();val radius=(cornerRadius.toPx()-inset).coerceAtLeast(0f)
            val path=Path().apply { addRoundRect(RectF(inset,inset,size.width-inset,size.height-inset),radius,radius,Path.Direction.CW) }
            val measure=PathMeasure(path,true);val active=Path()
            measure.getSegment(0f,measure.length*shown,active,true)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE;strokeWidth=2.5.dp.toPx();strokeCap=Paint.Cap.ROUND;color=colors.text;alpha=25 }
            canvas.nativeCanvas.drawPath(path,paint)
            paint.color=colors.accent;paint.alpha=255
            canvas.nativeCanvas.drawPath(active,paint)
        }
    }
}
