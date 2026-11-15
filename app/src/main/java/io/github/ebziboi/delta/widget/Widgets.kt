package io.github.ebziboi.delta.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.ComponentName
import android.os.Build
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import android.os.SystemClock
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import io.github.ebziboi.delta.data.SyncWorker
import io.github.ebziboi.delta.render.WidgetKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

class BodyFatWidget : BitmapWidget() { override val kind = WidgetKind.BODY_FAT }
class BodyFatWidgetReceiver : DeltaWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BodyFatWidget() }

class ProgressWidget : BitmapWidget() { override val kind = WidgetKind.PROGRESS }
class SummaryWidget : BitmapWidget() { override val kind = WidgetKind.SUMMARY }
class TrendWidget : BitmapWidget() { override val kind = WidgetKind.TREND }
class PaceWidget : BitmapWidget() { override val kind = WidgetKind.PACE }
class JourneyWidget : BitmapWidget() { override val kind = WidgetKind.JOURNEY }

/**
 * The home screen asks each widget to update about every half hour. As well as
 * redrawing, that is taken as the cue to fetch the latest weigh-ins.
 */
abstract class DeltaWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        runCatching { runBlocking { WidgetStyleStore(context).delete(appWidgetIds) } }
    }
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        runCatching { runBlocking { WidgetStyleStore(context).restore(oldWidgetIds, newWidgetIds) } }
        super.onRestored(context, oldWidgetIds, newWidgetIds)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        runCatching { SyncWorker.requestNow(context) }
    }
}

class ProgressWidgetReceiver : DeltaWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProgressWidget()
}

class SummaryWidgetReceiver : DeltaWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SummaryWidget()
}

class TrendWidgetReceiver : DeltaWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TrendWidget()
}

class PaceWidgetReceiver : DeltaWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PaceWidget()
}

class JourneyWidgetReceiver : DeltaWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = JourneyWidget()
}

/** Which receiver serves each kind of widget, for adding one from inside the app. */
object WidgetCatalog {
    fun widget(kind: WidgetKind): BitmapWidget = when (kind) {
        WidgetKind.BODY_FAT -> BodyFatWidget()
        WidgetKind.PROGRESS -> ProgressWidget()
        WidgetKind.JOURNEY -> JourneyWidget()
        WidgetKind.SUMMARY -> SummaryWidget()
        WidgetKind.TREND -> TrendWidget()
        WidgetKind.PACE -> PaceWidget()
    }

    fun kind(providerClassName: String): WidgetKind? = WidgetKind.entries.firstOrNull { receiver(it).name == providerClassName }

    fun receiver(kind: WidgetKind): Class<out android.appwidget.AppWidgetProvider> = when (kind) {
        WidgetKind.BODY_FAT -> BodyFatWidgetReceiver::class.java
        WidgetKind.PROGRESS -> ProgressWidgetReceiver::class.java
        WidgetKind.JOURNEY -> JourneyWidgetReceiver::class.java
        WidgetKind.SUMMARY -> SummaryWidgetReceiver::class.java
        WidgetKind.TREND -> TrendWidgetReceiver::class.java
        WidgetKind.PACE -> PaceWidgetReceiver::class.java
    }
}

object WidgetUpdater {
    suspend fun updateOne(context: Context, widgetId: Int, kind: WidgetKind) = withContext(Dispatchers.Default) {
        val id = androidx.glance.appwidget.GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)
        WidgetCatalog.widget(kind).update(context, id)
    }

    private val previewLock = Mutex()
    private var lastPreviewAttempt = 0L

    /** Redraws every Delta widget on the home screen. Safe to call when there are none. */
    suspend fun updateAll(context: Context) = withContext(Dispatchers.Default) {
        val widgets = listOf(BodyFatWidget(), ProgressWidget(), SummaryWidget(), TrendWidget(), PaceWidget(), JourneyWidget())
        for (widget in widgets) {
            try {
                widget.updateAll(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("DeltaWidgets", "Could not update ${widget.kind}", error)
            }
        }
        if (Build.VERSION.SDK_INT >= 35 && previewLock.tryLock()) {
            try {
                if (lastPreviewAttempt != 0L && SystemClock.elapsedRealtime() - lastPreviewAttempt < 30 * 60 * 1000L) return@withContext
                val data = (context.applicationContext as io.github.ebziboi.delta.DeltaApp).repo.widgetData.first()
                if (!data.second.onboardingDone || data.first.currentKg == null) return@withContext
                lastPreviewAttempt = SystemClock.elapsedRealtime()
                val manager = AppWidgetManager.getInstance(context)
                for (kind in WidgetKind.entries) {
                    try {
                        val (width, height) = when (kind) {
                            WidgetKind.PROGRESS, WidgetKind.PACE -> 170f to 170f
                            WidgetKind.SUMMARY -> 350f to 100f
                            else -> 350f to 200f
                        }
                        manager.setWidgetPreview(ComponentName(context, WidgetCatalog.receiver(kind)),
                            android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                            BitmapWidget.views(context, kind, width, height, data.first, data.second, 600f))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        // The platform rate-limits generated previews; static previews remain available.
                        Log.d("DeltaWidgets", "Preview deferred for $kind", error)
                    }
                }
            } finally { previewLock.unlock() }
        }
    }
}
