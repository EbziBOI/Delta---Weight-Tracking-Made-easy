package io.github.ebziboi.delta.ui

import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.github.ebziboi.delta.data.BackgroundStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

val LocalGlassBackdrop = staticCompositionLocalOf<HazeState?> { null }

/** Only the background is captured for cards, avoiding recursive captures of the scrolling card stack. */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val appearance = LocalAppearance.current
    val colors = LocalDeltaColors.current
    val context = LocalContext.current
    val backdrop = remember { HazeState() }
    val imageUri = appearance.backgroundImageUri.takeIf { appearance.backgroundStyle == BackgroundStyle.IMAGE }
    val photo by produceState<ImageBitmap?>(null, imageUri) {
        value = null
        val uri = imageUri ?: return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))) { decoder, info, _ ->
                    val scale = min(1f, 2048f / max(info.size.width, info.size.height))
                    decoder.setTargetSize(max(1, (info.size.width * scale).toInt()), max(1, (info.size.height * scale).toInt()))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }.asImageBitmap()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }
    }
    val dashboard = appearance.designStyle == io.github.ebziboi.delta.data.DesignStyle.TUBIK
    val drift = if (!dashboard && LocalMotion.current && appearance.backgroundStyle == BackgroundStyle.MESH) {
        rememberInfiniteTransition(label = "ambient-light").animateFloat(0f, (PI * 2).toFloat(),
            infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Reverse), label = "ambient-drift")
    } else null
    // Keep the source layer attached at zero blur too, so dragging away from zero
    // does not replace the backdrop layer in the middle of the touch gesture.
    val capture = android.os.Build.VERSION.SDK_INT >= 31 && !appearance.reduceTransparency
    val source = if (capture) Modifier.hazeSource(backdrop) else Modifier
    val imageScrim = remember(colors.bg, colors.textDim) { io.github.ebziboi.delta.render.ThemeContrast.imageScrim(colors.bg, colors.textDim) }
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(source)) {
            Canvas(Modifier.fillMaxSize()) {
                val turn = drift?.value ?: 0f
                val base = Color(colors.bg); val secondary = Color(colors.bgTop)
                when (appearance.backgroundStyle) {
                    BackgroundStyle.VERTICAL_GRADIENT -> drawRect(Brush.verticalGradient(listOf(secondary, base)))
                    BackgroundStyle.RADIAL_GRADIENT -> drawRect(Brush.radialGradient(listOf(secondary, base), center = Offset(size.width * 0.3f, size.height * 0.2f), radius = size.maxDimension * 0.8f))
                    else -> drawRect(base)
                }
                if (appearance.backgroundStyle == BackgroundStyle.MESH || (appearance.backgroundStyle == BackgroundStyle.IMAGE && photo == null)) {
                    drawRect(Brush.verticalGradient(listOf(secondary, base)))
                    colors.aurora.forEachIndexed { i, blob ->
                        val center = Offset(size.width * (blob.x + sin(turn + i) * 0.035f), size.height * (blob.y + cos(turn + i) * 0.025f))
                        val radius = blob.radius * size.maxDimension
                        drawCircle(Brush.radialGradient(listOf(Color(blob.color), Color.Transparent), center, radius), radius, center)
                    }
                }
            }
            if (appearance.backgroundStyle == BackgroundStyle.IMAGE && photo != null) {
                Image(photo!!, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize()) {
                    // A stable scrim keeps the same foreground readable on arbitrary photos.
                    drawRect(Color(colors.bg).copy(alpha = imageScrim))
                }
            }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides if (capture) backdrop else null) { content() }
    }
}
