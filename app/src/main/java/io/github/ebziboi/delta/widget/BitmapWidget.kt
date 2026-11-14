package io.github.ebziboi.delta.widget

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.fillMaxSize
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.MainActivity
import io.github.ebziboi.delta.R
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.data.ProgressSnapshot
import io.github.ebziboi.delta.render.Palettes
import io.github.ebziboi.delta.render.WidgetBitmaps
import io.github.ebziboi.delta.render.WidgetKind
import kotlinx.coroutines.flow.first

abstract class BitmapWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    abstract val kind: WidgetKind

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = (context.applicationContext as DeltaApp).repo
        val initial = repo.widgetData.first()
        provideContent {
            val data by repo.widgetData.collectAsState(initial = initial)
            val size = LocalSize.current
            val views = remember(data, size) {
                views(context, kind, size.width.value.coerceAtLeast(1f), size.height.value.coerceAtLeast(1f), data.first, data.second)
            }
            AndroidRemoteViews(remoteViews = views, modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()))
        }
    }

    companion object {
        fun views(context: Context, kind: WidgetKind, width: Float, height: Float,
                  snap: ProgressSnapshot, settings: AppSettings, maxSide: Float = 1000f): RemoteViews {
            val metrics = context.resources.displayMetrics
            // Reserve room for both modes and orientation variants on small displays too.
            val side = minOf(maxSide, kotlin.math.sqrt(metrics.widthPixels.toDouble() * metrics.heightPixels / 4.0).toFloat().coerceAtLeast(1f))
            val views = RemoteViews(context.packageName, R.layout.widget_bitmap)
            views.setContentDescription(R.id.widget_image, kind.detail)
            // Two capped images stay well below RemoteViews' bitmap memory limit.
            if (Build.VERSION.SDK_INT >= 31 && Palettes.widgetFollowsSystem(settings)) {
                val day = WidgetBitmaps.render(context, kind, width, height, snap, settings, night = false, maxSidePx = side)
                val night = WidgetBitmaps.render(context, kind, width, height, snap, settings, night = true, maxSidePx = side)
                views.setIcon(R.id.widget_image, "setImageIcon", Icon.createWithBitmap(day), Icon.createWithBitmap(night))
            } else {
                views.setImageViewBitmap(R.id.widget_image, WidgetBitmaps.render(context, kind, width, height, snap, settings, maxSidePx = side))
            }
            return views
        }
    }
}
