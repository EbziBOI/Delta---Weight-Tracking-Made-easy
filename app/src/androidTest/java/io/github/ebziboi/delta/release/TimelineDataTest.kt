package io.github.ebziboi.delta.release

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.nutrition.*
import io.github.ebziboi.delta.ui.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TimelineDataTest {
    @Test fun averagesEachRecordedDayEquallyAndPreservesCoverage() {
        val mon=LocalDate.of(2026,11,2)
        val input=listOf(RecordedValue(mon,100.0),RecordedValue(mon,120.0),RecordedValue(mon.plusDays(2),90.0))
        val daily=aggregateReadings(input,ChartPeriod.DAILY,1,mon.plusDays(6))
        assertEquals(listOf(110.0,90.0),daily.map {it.value})
        val weekly=aggregateReadings(input,ChartPeriod.WEEKLY,1,mon.plusDays(6)).single()
        assertEquals(100.0,weekly.value,.00001);assertEquals(2,weekly.recordedDays)
        assertEquals(mon,weekly.first);assertEquals(mon.plusDays(6),weekly.last)
        val month=aggregateReadings(input+RecordedValue(mon.plusMonths(1),999.0),ChartPeriod.MONTHLY,1,mon.plusDays(3)).single()
        assertEquals(100.0,month.value,.00001);assertEquals(mon.withDayOfMonth(1),month.first);assertEquals(mon.plusDays(3),month.last)
        assertEquals(2,month.recordedDays)
        assertTrue(aggregateReadings(emptyList(),ChartPeriod.MONTHLY).isEmpty())
    }
    @Test fun windowsStaySeparateFromAveragingAndProfilesRememberEachChart() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val prefs=context.getSharedPreferences("home-chart",0)
        val keys=listOf("profile.release-test-a","profile.release-test-b");val old=keys.associateWith {prefs.getString(it,null)}
        try {
            keys.forEach {prefs.edit().remove(it).commit()}
            val a=ChartController(context,"release-test-a",ChartStyle.DOTS)
            val b=ChartController(context,"release-test-b",ChartStyle.TREND)
            assertEquals(listOf("7D","1M","Journey","All"),a.period.windows)
            a.chooseWindow("7D");assertEquals(ChartPeriod.DAILY,a.period)
            a.choosePeriod(ChartPeriod.WEEKLY);assertEquals(listOf("2M","1Y","Journey","All"),a.period.windows);a.chooseWindow("1Y");a.cycle()
            assertEquals(ChartPeriod.DAILY,b.period);assertEquals("1M",b.window)
            a.choosePeriod(ChartPeriod.MONTHLY);assertEquals(listOf("6M","1Y","Journey","All"),a.period.windows);a.chooseWindow("6M")
            a.choosePeriod(ChartPeriod.WEEKLY);assertEquals("1Y",a.window)
            a.choosePeriod(ChartPeriod.DAILY);assertEquals("7D",a.window)
            val reopened=ChartController(context,"release-test-a",ChartStyle.CONNECTED)
            assertEquals(ChartStyle.TREND,reopened.mode);assertEquals("7D",reopened.window)
            reopened.cycle();assertEquals(ChartStyle.CONNECTED,reopened.mode);reopened.cycle();assertEquals(ChartStyle.DOTS,reopened.mode)
        } finally {old.forEach {(k,v)->prefs.edit().apply {if(v==null) remove(k) else putString(k,v)}.commit()}}
    }
    @Test fun sevenDayMacroPlotHasTwentyOneRealPointsAndNoInventedZeros() {
        val end=LocalDate.of(2026,11,16)
        val food=Food(name="Label",per100=mapOf(Nutrient.PROTEIN to 10.0,Nutrient.CARBS to 20.0,Nutrient.FAT to 0.0))
        val entries=(0..8).map {DiaryEntry(day=end.minusDays(it.toLong()),meal=Meal.LUNCH,food=food,amount=100.0)}
        val full=macroWeek(entries,end)
        assertEquals(21,full.values.sumOf {it.size});assertTrue(full.getValue(Nutrient.FAT).all {it.value==0.0})
        val missing=macroWeek(entries.filterNot {it.day==end.minusDays(2)},end)
        assertEquals(18,missing.values.sumOf {it.size});assertTrue(missing.values.flatten().none {it.day==end.minusDays(2)})
        val unknown=food.copy(per100=mapOf(Nutrient.PROTEIN to 10.0))
        assertTrue(macroWeek(listOf(DiaryEntry(day=end,meal=Meal.LUNCH,food=unknown,amount=100.0)),end).getValue(Nutrient.CARBS).isEmpty())
    }
    @Test fun monotoneSplineStaysWithinAdjacentReadings() {
        val values=listOf(SplinePoint(0f,10f),SplinePoint(2f,8f),SplinePoint(3f,11f),SplinePoint(7f,11.1f),SplinePoint(9f,3f))
        val curves=monotoneSpline(values);assertEquals(4,curves.size)
        curves.forEach {c->for(step in 0..100) {
            val t=step/100f;val u=1-t;val y=u*u*u*c.first.y+3*u*u*t*c.c1.y+3*u*t*t*c.c2.y+t*t*t*c.end.y
            assertTrue("Curve introduced an artificial extreme: $y",y>=minOf(c.first.y,c.end.y)-.0001 && y<=maxOf(c.first.y,c.end.y)+.0001)
        }}
        assertTrue(monotoneSpline(listOf(SplinePoint(1f,2f))).isEmpty())
    }
    @Test fun hapticsPreferenceRoundTripsWithoutChangingOtherSettings()=runBlocking {
        val store=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext);val original=store.settings.first()
        try {store.update {it.copy(haptics=false)};assertFalse(store.settings.first().haptics);assertEquals(original.appIcon,store.settings.first().appIcon);store.update {it.copy(haptics=true)};assertTrue(store.settings.first().haptics)} finally {store.update {original}}
    }
    @Test fun longerSignalsAcceptSparseDatesAndWindowBoundsAreCalendarBased() {
        val today=LocalDate.of(2026,11,16);val data=listOf(0L,3L,10L,18L,27L).associate {today.minusDays(it) to 90+it*.1}
        val rate=recentPace(data,30,today);assertEquals(-.7,rate.rate!!,.00001);assertEquals(5,rate.count)
        assertTrue(rate.reason.contains("5 recorded dates"))
        assertEquals(LocalDate.of(2026,6,1),chartWindowStart("6M",today,null,today.minusYears(4),ChartPeriod.MONTHLY))
        assertEquals(LocalDate.of(2025,12,1),chartWindowStart("1Y",today,null,today.minusYears(4),ChartPeriod.MONTHLY))
        assertEquals(today.minusDays(6),chartWindowStart("7D",today,null,today.minusYears(4),ChartPeriod.DAILY))
    }
}
