package io.github.ebziboi.delta.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import io.github.ebziboi.delta.data.SyncWorker
import io.github.ebziboi.delta.render.WidgetKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

class ProgressWidget : BitmapWidget() { override val kind = WidgetKind.CARD }
class ComplicationWidget : BitmapWidget() { override val kind = WidgetKind.COMPLICATION }
class TrendWidget : BitmapWidget() { override val kind = WidgetKind.TREND }
class PaceWidget : BitmapWidget() { override val kind = WidgetKind.PACE }
class BodyFatWidget : BitmapWidget() { override val kind = WidgetKind.BODY_FAT }

/*
 * Retired from the widget picker. Summary became the Progress card and Journey
 * became Trend (which has the progress bar), but their receivers stay so widgets
 * already on a home screen keep working, drawn as their replacements.
 */
class SummaryWidget : BitmapWidget() { override val kind = WidgetKind.CARD }
class JourneyWidget : BitmapWidget() { override val kind = WidgetKind.TREND }

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

class ProgressWidgetReceiver : DeltaWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ProgressWidget() }
class ComplicationWidgetReceiver : DeltaWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ComplicationWidget() }
class TrendWidgetReceiver : DeltaWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TrendWidget() }
class PaceWidgetReceiver : DeltaWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PaceWidget() }
class BodyFatWidgetReceiver : DeltaWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BodyFatWidget() }
class SummaryWidgetReceiver : DeltaWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = SummaryWidget() }
class JourneyWidgetReceiver : DeltaWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = JourneyWidget() }

/** Which receiver serves each kind of widget, and which kind a placed widget is. */
object WidgetCatalog {
    /** The receiver a new widget of [kind] is added through. */
    fun receiver(kind: WidgetKind): Class<out AppWidgetProvider> = when (kind) {
        WidgetKind.CARD -> ProgressWidgetReceiver::class.java
        WidgetKind.COMPLICATION -> ComplicationWidgetReceiver::class.java
        WidgetKind.TREND -> TrendWidgetReceiver::class.java
        WidgetKind.PACE -> PaceWidgetReceiver::class.java
        WidgetKind.BODY_FAT -> BodyFatWidgetReceiver::class.java
    }

    /** Every receiver Delta has, including retired ones that still serve widgets placed before. */
    private val receivers: Map<Class<out AppWidgetProvider>, () -> BitmapWidget> = linkedMapOf(
        ProgressWidgetReceiver::class.java to ::ProgressWidget,
        ComplicationWidgetReceiver::class.java to ::ComplicationWidget,
        TrendWidgetReceiver::class.java to ::TrendWidget,
        PaceWidgetReceiver::class.java to ::PaceWidget,
        BodyFatWidgetReceiver::class.java to ::BodyFatWidget,
        SummaryWidgetReceiver::class.java to ::SummaryWidget,
        JourneyWidgetReceiver::class.java to ::JourneyWidget,
    )

    /** One of each widget class, for redrawing them all. */
    fun all(): List<BitmapWidget> = receivers.values.map { it() }

    fun widget(providerClassName: String): BitmapWidget? = receivers.entries.firstOrNull { it.key.name == providerClassName }?.value?.invoke()

    fun kind(providerClassName: String): WidgetKind? = widget(providerClassName)?.kind
}

object WidgetUpdater {
    /** Redraws one placed widget, through the widget class of the receiver it was added with. */
    suspend fun updateOne(context: Context, widgetId: Int, kind: WidgetKind) = withContext(Dispatchers.Default) {
        val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)?.provider?.className
        val widget = provider?.let(WidgetCatalog::widget) ?: when (kind) {
            WidgetKind.CARD -> ProgressWidget()
            WidgetKind.COMPLICATION -> ComplicationWidget()
            WidgetKind.TREND -> TrendWidget()
            WidgetKind.PACE -> PaceWidget()
            WidgetKind.BODY_FAT -> BodyFatWidget()
        }
        val id = androidx.glance.appwidget.GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)
        widget.update(context, id)
    }

    private val previewLock = Mutex()
    private var lastPreviewAttempt = 0L

    /** Redraws every Delta widget on the home screen. Safe to call when there are none. */
    suspend fun updateAll(context: Context) = withContext(Dispatchers.Default) {
        FoodWidgetUpdater.updateAll(context)
        for (widget in WidgetCatalog.all()) {
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
                        manager.setWidgetPreview(ComponentName(context, WidgetCatalog.receiver(kind)),
                            android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                            BitmapWidget.views(context, kind, kind.defaultWidth, kind.defaultHeight, data.first, data.second, 600f))
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
