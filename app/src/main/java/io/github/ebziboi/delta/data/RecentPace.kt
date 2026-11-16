package io.github.ebziboi.delta.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.*

data class RecentPace(val days:Int,val count:Int,val span:Int,val rate:Double?,val residual:Double?,val reliability:String,val reason:String)
fun recentPace(data:Map<LocalDate,Double>,days:Int,today:LocalDate=LocalDate.now()):RecentPace {
    val rows=data.filter {it.key in today.minusDays(days-1L)..today && it.value.isFinite() && it.value>0}.toSortedMap().entries.toList()
    val span=if(rows.size>1) ChronoUnit.DAYS.between(rows.first().key,rows.last().key).toInt() else 0
    if(rows.size<2 || span==0) return RecentPace(days,rows.size,span,null,null,"Unavailable","Two separate recorded dates are needed; consecutive days are not required.")
    val x=rows.map {ChronoUnit.DAYS.between(rows.first().key,it.key).toDouble()};val y=rows.map {it.value}
    val mx=x.average();val my=y.average();val ss=x.sumOf {(it-mx).pow(2)}
    val slope=x.indices.sumOf {(x[it]-mx)*(y[it]-my)}/ss
    val residual=sqrt(x.indices.sumOf {(y[it]-(my+slope*(x[it]-mx))).pow(2)}/rows.size)
    val reliability=when {rows.size>=maxOf(5,days/2) && span>=days-3 && residual<.6->"Higher confidence";rows.size>=3 && span>=days/2 && residual<1.2->"Moderate confidence";else->"Low confidence"}
    val reason="${rows.size} recorded dates across ${span+1} calendar days. "+when {
        rows.size==2->"Two points give an estimate but cannot establish how steady the trend is."
        span<days/2->"Readings cover only a short part of the window."
        residual>=1.2->"Large day-to-day variation makes the rate sensitive to individual readings."
        reliability=="Higher confidence"->"Readings are spread through the window with relatively small variation."
        else->"Some gaps or variation reduce the strength of this estimate."
    }
    return RecentPace(days,rows.size,span,slope*7,residual,reliability,reason+" This is a data-quality assessment, not a guaranteed forecast.")
}
