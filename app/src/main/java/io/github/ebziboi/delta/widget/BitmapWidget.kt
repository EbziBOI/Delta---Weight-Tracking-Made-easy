package io.github.ebziboi.delta.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.MainActivity
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.data.ProgressSnapshot
import kotlinx.coroutines.flow.first

/**
 * A widget that is one image, drawn to the exact size it has been given on the
 * home screen. Tapping it opens the app. Subclasses only say what to draw.
 */
abstract class BitmapWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    protected abstract val description: String

    protected abstract fun render(
        context: Context,
        widthDp: Float,
        heightDp: Float,
        snap: ProgressSnapshot,
        settings: AppSettings,
    ): Bitmap

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = (context.applicationContext as DeltaApp).repo
        // Load once before composing so the first frame already has real numbers.
        val initial = repo.widgetData.first()

        provideContent {
            val data by repo.widgetData.collectAsState(initial = initial)
            val size = LocalSize.current
            val bitmap = remember(data, size) {
                render(context, size.width.value, size.height.value, data.first, data.second)
            }
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = description,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
            }
        }
    }
}
