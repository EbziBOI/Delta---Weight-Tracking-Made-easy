package io.github.ebziboi.delta.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.data.ProgressSnapshot
import io.github.ebziboi.delta.render.WidgetBitmaps
import kotlinx.coroutines.CancellationException

/** The delta track with the percentage inside it. */
class ProgressWidget : BitmapWidget() {
    override val description = "Progress towards your target weight"

    override fun render(
        context: Context,
        widthDp: Float,
        heightDp: Float,
        snap: ProgressSnapshot,
        settings: AppSettings,
    ): Bitmap = WidgetBitmaps.progress(context, widthDp, heightDp, snap, settings)
}

/** Start, now and target over a progress bar. */
class SummaryWidget : BitmapWidget() {
    override val description = "Starting, current and target weight"

    override fun render(
        context: Context,
        widthDp: Float,
        heightDp: Float,
        snap: ProgressSnapshot,
        settings: AppSettings,
    ): Bitmap = WidgetBitmaps.summary(context, widthDp, heightDp, snap, settings)
}

/** The weight graph since the start. */
class TrendWidget : BitmapWidget() {
    override val description = "Weight trend against your target"

    override fun render(
        context: Context,
        widthDp: Float,
        heightDp: Float,
        snap: ProgressSnapshot,
        settings: AppSettings,
    ): Bitmap = WidgetBitmaps.trend(context, widthDp, heightDp, snap, settings)
}

/** Weekly rate, projected goal date and streak. */
class PaceWidget : BitmapWidget() {
    override val description = "Weekly rate, projected goal date and weigh-in streak"

    override fun render(
        context: Context,
        widthDp: Float,
        heightDp: Float,
        snap: ProgressSnapshot,
        settings: AppSettings,
    ): Bitmap = WidgetBitmaps.pace(context, widthDp, heightDp, snap, settings)
}

class ProgressWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProgressWidget()
}

class SummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SummaryWidget()
}

class TrendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TrendWidget()
}

class PaceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PaceWidget()
}

object WidgetUpdater {

    /** Redraws every Delta widget on the home screen. Safe to call when there are none. */
    suspend fun updateAll(context: Context) {
        val widgets = listOf(ProgressWidget(), SummaryWidget(), TrendWidget(), PaceWidget())
        for (widget in widgets) {
            try {
                widget.updateAll(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // A widget that fails to redraw must not stop the others or the caller.
            }
        }
    }
}
