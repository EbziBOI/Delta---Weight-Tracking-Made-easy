package io.github.ebziboi.delta.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.app.PendingIntent
import android.content.*
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.glance.*
import androidx.glance.appwidget.*
import androidx.glance.layout.fillMaxSize
import androidx.work.*
import io.github.ebziboi.delta.*
import io.github.ebziboi.delta.R
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.render.*
import io.github.ebziboi.delta.nutrition.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class EnergyFoodWidget:FoodBitmapWidget() {override val kind=FoodWidgetKind.ENERGY}
class MacroFoodWidget:FoodBitmapWidget() {override val kind=FoodWidgetKind.MACROS}
class MealsFoodWidget:FoodBitmapWidget() {override val kind=FoodWidgetKind.MEALS}
class FastingFoodWidget:FoodBitmapWidget() {override val kind=FoodWidgetKind.FASTING}
class EnergyFoodReceiver:FoodWidgetReceiver() {override val glanceAppWidget:GlanceAppWidget=EnergyFoodWidget()}
class MacroFoodReceiver:FoodWidgetReceiver() {override val glanceAppWidget:GlanceAppWidget=MacroFoodWidget()}
class MealsFoodReceiver:FoodWidgetReceiver() {override val glanceAppWidget:GlanceAppWidget=MealsFoodWidget()}
class FastingFoodReceiver:FoodWidgetReceiver() {override val glanceAppWidget:GlanceAppWidget=FastingFoodWidget()}

abstract class FoodWidgetReceiver:GlanceAppWidgetReceiver() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) {
        super.onUpdate(context,manager,ids);FoodWidgetWorker.schedule(context)
    }
    override fun onDeleted(context:Context,appWidgetIds:IntArray) {
        // Glance owns the broadcast's asynchronous result; finish style cleanup first.
        runCatching {runBlocking {WidgetStyleStore(context).delete(appWidgetIds)}}
        super.onDeleted(context,appWidgetIds)
    }
    override fun onRestored(context:Context,oldWidgetIds:IntArray,newWidgetIds:IntArray) {
        runCatching {runBlocking {WidgetStyleStore(context).restore(oldWidgetIds,newWidgetIds)}}
        super.onRestored(context,oldWidgetIds,newWidgetIds)
    }
    override fun onDisabled(context:Context) {
        super.onDisabled(context)
        if(FoodWidgetKind.entries.all {AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context,FoodWidgetCatalog.receiver(it))).isEmpty()})
            WorkManager.getInstance(context).cancelUniqueWork("delta-food-widgets")
    }
}
object FoodWidgetCatalog {
    fun receiver(kind:FoodWidgetKind):Class<out AppWidgetProvider> = when(kind) {
        FoodWidgetKind.ENERGY->EnergyFoodReceiver::class.java;FoodWidgetKind.MACROS->MacroFoodReceiver::class.java
        FoodWidgetKind.MEALS->MealsFoodReceiver::class.java;FoodWidgetKind.FASTING->FastingFoodReceiver::class.java
    }
    fun kind(className:String)=FoodWidgetKind.entries.firstOrNull {receiver(it).name==className}
    fun widget(kind:FoodWidgetKind):FoodBitmapWidget=when(kind) {
        FoodWidgetKind.ENERGY->EnergyFoodWidget();FoodWidgetKind.MACROS->MacroFoodWidget();FoodWidgetKind.MEALS->MealsFoodWidget();FoodWidgetKind.FASTING->FastingFoodWidget()
    }
}
abstract class FoodBitmapWidget:GlanceAppWidget() {
    abstract val kind:FoodWidgetKind
    override val sizeMode:SizeMode=SizeMode.Exact
    override suspend fun provideGlance(context:Context,id:GlanceId) {
        val app=context.applicationContext as DeltaApp
        val initial=app.nutrition.state.first {it.loaded};val settings=app.repo.settings.first()
        val styles=WidgetStyleStore(context).style(GlanceAppWidgetManager(context).getAppWidgetId(id));val initialStyle=styles.first()
        provideContent {
            val data by app.nutrition.state.collectAsState(initial)
            val saved by app.repo.settings.collectAsState(settings)
            val style by styles.collectAsState(initialStyle)
            val effective=style?.apply(saved,AppearanceEngine.resolve(context,saved)) ?: saved
            val size=LocalSize.current
            val views=remember(data,effective,size) {views(context,kind,size.width.value,size.height.value,data,effective)}
            AndroidRemoteViews(views,modifier=GlanceModifier.fillMaxSize().appWidgetBackground()
                .cornerRadius(WidgetCorners.radius(context,AppearanceEngine.resolve(context,effective).cornerRadius).dp))
        }
    }
    companion object {
        fun launchIntent(context:Context,route:String)=Intent(context,MainActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .putExtra("food_route",route).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        fun views(context:Context,kind:FoodWidgetKind,width:Float,height:Float,state:NutritionState,settings:AppSettings):RemoteViews {
            val result=RemoteViews(context.packageName,R.layout.widget_food)
            val now=System.currentTimeMillis();val active=state.fasts.firstOrNull {it.end==null}
            val clock=kind==FoodWidgetKind.FASTING && active!=null && width>=150 && height>=140
            val metrics=context.resources.displayMetrics
            val cap=minOf(900f,kotlin.math.sqrt(metrics.widthPixels.toDouble()*metrics.heightPixels/4).toFloat())
            fun bitmap(night:Boolean)=FoodWidgetBitmaps.render(context,kind,width,height,state,settings,night,now,cap,clock)
            if(Build.VERSION.SDK_INT>=31 && Palettes.widgetFollowsSystem(settings)) result.setIcon(R.id.widget_food_image,"setImageIcon",Icon.createWithBitmap(bitmap(false)),Icon.createWithBitmap(bitmap(true)))
            else result.setImageViewBitmap(R.id.widget_food_image,bitmap(Palettes.isNight(context)))
            val day=java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val entries=state.entries.filter {it.day==day}
            fun total(n:Nutrient):String {val t=entries.total(n);return if(t.reported==0) "not reported" else (if(t.partial) "at least " else "")+amountText(t.amount)+" "+n.unit}
            val spoken=when(kind) {
                FoodWidgetKind.ENERGY->"Energy logged: ${total(Nutrient.ENERGY)}. "+(state.targets.target(Nutrient.ENERGY)?.let {"Target: ${amountText(it)} kcal."} ?: "No target set.")
                FoodWidgetKind.MACROS->Nutrient.macros.joinToString {"${it.label}: ${total(it)}"}
                FoodWidgetKind.MEALS->"${entries.size} foods today. "+entries.takeLast(3).joinToString {it.food.name}
                FoodWidgetKind.FASTING->active?.let {"Elapsed ${it.elapsed(now)/3600000} hours, ${it.elapsed(now)/60000%60} minutes. Target ${it.targetHours} hours."} ?: "No active fast."
            }
            result.setContentDescription(R.id.widget_food_image,kind.title+". "+spoken)
            fun action(route:String)=PendingIntent.getActivity(context,route.hashCode(),launchIntent(context,route),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            result.setOnClickPendingIntent(R.id.widget_food_image,action(kind.route))
            result.setViewVisibility(R.id.widget_food_add,if(kind==FoodWidgetKind.MEALS && width>=170 && height>=120) View.VISIBLE else View.GONE)
            result.setOnClickPendingIntent(R.id.widget_food_add,action("search"))
            fun foreground(night:Boolean):DrawColors {
                val theme=FoodWidgetBitmaps.theme(context,kind,settings,night);val colors=theme.drawColors()
                return if(settings.widgetGlass==WidgetGlass.SOLID || theme.appearance.reduceTransparency) colors else
                    WidgetLegibility.over(colors,theme.background,if(theme.isLight) settings.widgetOpacityLight else settings.widgetOpacityDark,WallpaperTone.backdrop(context))
            }
            fun tint(viewId:Int) {
                if(Build.VERSION.SDK_INT>=31 && Palettes.widgetFollowsSystem(settings)) result.setColorStateList(viewId,"setTextColor",
                    android.content.res.ColorStateList.valueOf(foreground(false).accent),android.content.res.ColorStateList.valueOf(foreground(true).accent))
                else result.setTextColor(viewId,foreground(Palettes.isNight(context)).accent)
            }
            tint(R.id.widget_food_add)
            result.setViewVisibility(R.id.widget_food_clock,if(clock) View.VISIBLE else View.GONE)
            if(clock) {
                result.setChronometer(R.id.widget_food_clock,SystemClock.elapsedRealtime()-active!!.elapsed(now),null,true)
                tint(R.id.widget_food_clock)
                result.setTextViewTextSize(R.id.widget_food_clock,android.util.TypedValue.COMPLEX_UNIT_SP,minOf(28f,(width-36)/5,(minOf(width,height)-2*(minOf(width,height)*.09f).coerceIn(10f,16f)-66)/4.8f).coerceAtLeast(8f))
                result.setOnClickPendingIntent(R.id.widget_food_clock,action("fasting"))
            }
            return result
        }
    }
}
object FoodWidgetUpdater {
    suspend fun updateAll(context:Context) {
        val manager=AppWidgetManager.getInstance(context)
        FoodWidgetKind.entries.forEach {kind->
            if(manager.getAppWidgetIds(ComponentName(context,FoodWidgetCatalog.receiver(kind))).isNotEmpty()) {
                try {FoodWidgetCatalog.widget(kind).updateAll(context)}
                catch(e:CancellationException) {throw e}
                catch(e:Exception) {android.util.Log.w("DeltaFoodWidgets","Could not update ${kind.name}",e)}
            }
        }
    }
}
class FoodWidgetWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result {FoodWidgetUpdater.updateAll(applicationContext);return Result.success()}
    companion object {
        fun request(context:Context) {WorkManager.getInstance(context).enqueueUniqueWork("delta-food-widget-now",ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<FoodWidgetWorker>().build())}
        fun schedule(context:Context) {WorkManager.getInstance(context).enqueueUniquePeriodicWork("delta-food-widgets",ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<FoodWidgetWorker>(15,TimeUnit.MINUTES).build())}
    }
}
