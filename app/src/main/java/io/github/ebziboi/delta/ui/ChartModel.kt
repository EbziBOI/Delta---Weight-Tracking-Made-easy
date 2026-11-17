package io.github.ebziboi.delta.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import io.github.ebziboi.delta.data.ChartStyle
import io.github.ebziboi.delta.data.MetricDetails
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.*

internal enum class ChartPeriod(val label:String,val days:Double) {
    DAILY("Daily",1.0), WEEKLY("Weekly",7.0), MONTHLY("Monthly",30.0);
    val windows get()=when(this) {DAILY->listOf("7D","1M","Journey","All");WEEKLY->listOf("2M","1Y","Journey","All");MONTHLY->listOf("6M","1Y","Journey","All")}
    val defaultWindow get()=when(this) {DAILY->"1M";WEEKLY->"2M";MONTHLY->"1Y"}
}

internal data class RecordedValue(val day:LocalDate,val value:Double,val first:LocalDate=day,val last:LocalDate=day,val recordedDays:Int=1,val partial:Boolean=false)

/** First average within a date, then give each recorded date equal weight. */
internal fun aggregateReadings(values:List<RecordedValue>,period:ChartPeriod,weekStartsOn:Int=0,asOf:LocalDate=LocalDate.now()):List<RecordedValue> {
    val daily=values.filter {it.day<=asOf && it.value.isFinite()}.groupBy {it.day}.map {(d,rows)->RecordedValue(d,rows.map {it.value}.average(),partial=rows.any {it.partial})}
    fun start(d:LocalDate)=when(period) {ChartPeriod.DAILY->d;ChartPeriod.WEEKLY->MetricDetails.weekStart(d,weekStartsOn);ChartPeriod.MONTHLY->d.withDayOfMonth(1)}
    return daily.groupBy {start(it.day)}.map {(first,rows)->
        val end=minOf(asOf,when(period) {ChartPeriod.DAILY->first;ChartPeriod.WEEKLY->first.plusDays(6);ChartPeriod.MONTHLY->YearMonth.from(first).atEndOfMonth()})
        RecordedValue(if(period==ChartPeriod.DAILY) first else end,rows.map {it.value}.average(),first,end,rows.size,rows.any {it.partial})
    }.sortedBy {it.day}
}

internal fun chartWindowStart(window:String,end:LocalDate,journey:LocalDate?,oldest:LocalDate,period:ChartPeriod):LocalDate=when(window) {
    "7D"->end.minusDays(6)
    "1M"->end.minusMonths(1).plusDays(1)
    "2M"->end.minusMonths(2).plusDays(1)
    "6M"->if(period==ChartPeriod.MONTHLY) end.withDayOfMonth(1).minusMonths(5) else end.minusMonths(6).plusDays(1)
    "1Y"->if(period==ChartPeriod.MONTHLY) end.withDayOfMonth(1).minusMonths(11) else end.minusYears(1).plusDays(1)
    "Journey"->(journey ?: oldest).coerceAtMost(end)
    else->oldest.coerceAtMost(end)
}

internal fun ChartStyle.nextMode()=when(this) {ChartStyle.DOTS->ChartStyle.TREND;ChartStyle.TREND->ChartStyle.CONNECTED;ChartStyle.CONNECTED->ChartStyle.DOTS}

@Stable
internal class ChartController(context:Context,val id:String,private val initialStyle:ChartStyle,initialPeriod:ChartPeriod=ChartPeriod.DAILY) {
    private val prefs=context.getSharedPreferences("home-chart",0)
    private var saved=runCatching {JSONObject(prefs.getString("profile.$id","{}").orEmpty())}.getOrDefault(JSONObject())
    var period by mutableStateOf(ChartPeriod.entries.find {it.name==saved.optString("period")} ?: initialPeriod);private set
    var mode by mutableStateOf(ChartStyle.entries.find {it.name==saved.optString("mode")} ?: initialStyle);private set
    var window by mutableStateOf(saved.optString(period.name).takeIf {it in period.windows} ?: period.defaultWindow);private set
    var revision by mutableIntStateOf(0);private set
    var visibleFirst by mutableStateOf<LocalDate?>(null)
    var visibleLast by mutableStateOf<LocalDate?>(null)
    fun choosePeriod(next:ChartPeriod) {saved.put(period.name,window);period=next;window=saved.optString(next.name).takeIf {it in next.windows} ?: next.defaultWindow;visibleFirst=null;visibleLast=null;revision++;save()}
    fun chooseWindow(next:String) {if(next in period.windows) {window=next;visibleFirst=null;visibleLast=null;revision++;save()}}
    fun cycle() {mode=mode.nextMode();save()}
    fun chooseMode(next:ChartStyle) {mode=next;save()}
    fun observe():()->Unit {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,key->
            if(key=="profile.$id") {
                val value=prefs.getString(key,"{}").orEmpty()
                if(value!=saved.toString()) {
                    saved=runCatching {JSONObject(value)}.getOrDefault(JSONObject())
                    period=ChartPeriod.entries.find {it.name==saved.optString("period")} ?: ChartPeriod.DAILY
                    mode=ChartStyle.entries.find {it.name==saved.optString("mode")} ?: initialStyle
                    window=saved.optString(period.name).takeIf {it in period.windows} ?: period.defaultWindow
                    visibleFirst=null;visibleLast=null;revision++
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return {prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    private fun save() {saved.put("period",period.name).put("mode",mode.name).put(period.name,window);prefs.edit().putString("profile.$id",saved.toString()).apply()}
}
@Composable internal fun rememberChartController(id:String,style:ChartStyle=ChartStyle.DOTS,period:ChartPeriod=ChartPeriod.DAILY):ChartController {
    val context=LocalContext.current
    val profile=remember(context,id) {ChartController(context,id,style,period)}
    DisposableEffect(profile) {val stop=profile.observe();onDispose(stop)}
    return profile
}

internal data class SplinePoint(val x:Float,val y:Float)
internal data class SplineCurve(val first:SplinePoint,val c1:SplinePoint,val c2:SplinePoint,val end:SplinePoint)
/** Shape-preserving cubic interpolation: no artificial peaks outside neighbouring values. */
internal fun monotoneSpline(points:List<SplinePoint>):List<SplineCurve> {
    if(points.size<2) return emptyList()
    val p=points.distinctBy {it.x}.sortedBy {it.x};if(p.size<2) return emptyList()
    val h=(0 until p.lastIndex).map {(p[it+1].x-p[it].x).coerceAtLeast(.0001f)}
    val d=h.indices.map {(p[it+1].y-p[it].y)/h[it]}
    val m=FloatArray(p.size);m[0]=d.first();m[m.lastIndex]=d.last()
    for(i in 1 until p.lastIndex) m[i]=if(d[i-1]*d[i]<=0f) 0f else {
        val a=2*h[i]+h[i-1];val b=h[i]+2*h[i-1];(a+b)/(a/d[i-1]+b/d[i])
    }
    for(i in d.indices) {
        if(abs(d[i])<1e-8f) {m[i]=0f;m[i+1]=0f} else {
            val a=m[i]/d[i];val b=m[i+1]/d[i];val length=a*a+b*b
            if(length>9f) {val t=3/sqrt(length);m[i]=t*a*d[i];m[i+1]=t*b*d[i]}
        }
    }
    return h.indices.map {i->val dx=h[i]/3;SplineCurve(p[i],SplinePoint(p[i].x+dx,p[i].y+m[i]*dx),SplinePoint(p[i+1].x-dx,p[i+1].y-m[i+1]*dx),p[i+1])}
}
