package io.github.ebziboi.delta.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class EnergyHistoryTest {
    private val today=LocalDate.of(2026,11,20)
    private val zone=ZoneOffset.UTC
    private fun at(daysAgo:Long)=today.minusDays(daysAgo).atTime(8,0).toInstant(zone).toEpochMilli()
    private val profile=EnergyProfile(enabled=true,heightCm=180.0,birthYear=1990,sex=Sex.MALE,useHealthActivity=true,activityCredit=.75,activityLevel=ActivityLevel.SEDENTARY)
    private val usual=(1..10).map {DailyActivity(today.minusDays(it.toLong()),activeKcal=600.0)}
    // 90 kg, 180 cm, 36, male: resting 1,850; 450 kcal credited activity; maintenance 2,485; 0.5 kg a week = −550.
    private fun balance(kg:Double=90.0,target:Double?=80.0,p:EnergyProfile=profile)=
        EnergyMath.compute(EnergyInputs(p,listOf(WeightEntry(at(0),kg,true,"w")),emptyList(),emptyMap(),EnergyHealthData(activity=usual),target,null,today,zone))!!

    private fun days(b:EnergyBalance,activity:List<DailyActivity>,plans:Map<LocalDate,DayPlan> = emptyMap(),intake:Map<LocalDate,Pair<Double,Boolean>> = emptyMap())=
        EnergyHistory.days(b,profile,plans,activity,intake,today.minusDays(6),today)

    @Test fun eachDayUsesItsOwnActivity() {
        val b=balance()
        assertTrue(b.activityFromHealth);assertEquals(1940.0,b.budgetKcal,.01)
        val d=days(b,listOf(DailyActivity(today.minusDays(1),activeKcal=1000.0),DailyActivity(today.minusDays(2),activeKcal=100.0)))
        val yesterday=d.first {it.day==today.minusDays(1)}
        assertEquals(2785.0,yesterday.maintenanceKcal,.5);assertEquals(2240.0,yesterday.budgetKcal,.01);assertTrue(yesterday.activityMeasured)
        // A quiet day can't push the budget under the minimum.
        assertEquals(1850.0,d.first {it.day==today.minusDays(2)}.budgetKcal,.01)
        // No measurement: the usual amount.
        val none=d.first {it.day==today.minusDays(3)}
        assertEquals(1940.0,none.budgetKcal,.01);assertFalse(none.activityMeasured)
    }
    @Test fun todayKeepsTheUsualActivityUntilItIsBeaten() {
        val b=balance()
        assertEquals(1940.0,days(b,listOf(DailyActivity(today,activeKcal=200.0))).last().budgetKcal,.01)
        assertEquals(2240.0,days(b,listOf(DailyActivity(today,activeKcal=1000.0))).last().budgetKcal,.01)
    }
    @Test fun savedPlansKeepPastDaysAsTheyWere() {
        val b=balance()
        val plan=DayPlan(2000.0,-500.0,1500.0,true,400.0,1.0)
        assertEquals(plan,DayPlan.decode(plan.encode()))
        val d=days(b,listOf(DailyActivity(today.minusDays(3),activeKcal=600.0)),plans=mapOf(today.minusDays(3) to plan))
        val saved=d.first {it.day==today.minusDays(3)}
        assertEquals(2600.0,saved.maintenanceKcal,.01);assertEquals(2100.0,saved.budgetKcal,.01);assertFalse(saved.estimated)
        assertTrue(d.first {it.day==today.minusDays(5)}.estimated)
        assertFalse(d.last().estimated)
    }
    @Test fun summaryCountsOnlyWholeLoggedDays() {
        val b=balance()
        val intake=mapOf(today.minusDays(1) to (1800.0 to true),today.minusDays(2) to (2600.0 to true),today.minusDays(3) to (500.0 to true),
            today.minusDays(4) to (1900.0 to false),today to (900.0 to true))
        val d=days(b,emptyList(),intake=intake)
        val s=EnergyHistory.summary(d,emptyList(),zone)
        assertEquals(2,s.countedDays);assertEquals(1,s.onBudgetDays)
        assertEquals((1800-2485.0)+(2600-2485.0),s.totalVsMaintenance,.5)
        assertEquals(s.totalVsMaintenance/7700,s.expectedKg,1e-9)
        assertEquals(1,s.streak)
    }
    @Test fun noDeficitIsPlannedWhenUnderweight() {
        val b=balance(kg=55.0,target=50.0)
        assertTrue(b.bmi!!<18.5)
        assertEquals(0.0,b.dailyChange,.01);assertTrue(b.goalNote.contains("18.5"))
    }
    @Test fun bmiAndBodyFatGroups() {
        assertEquals(BmiGroup.HEALTHY,BmiGroup.of(24.9));assertEquals(BmiGroup.OVER,BmiGroup.of(25.0))
        assertEquals(BmiGroup.UNDER,BmiGroup.of(17.0));assertEquals(BmiGroup.OBESE_3,BmiGroup.of(41.0))
        val r=BodyMetrics.healthyRange(180.0)
        assertEquals(59.94,r.start,.01);assertEquals(80.68,r.endInclusive,.01)
        assertEquals(FatGroup.AVERAGE,BodyMetrics.fatGroup(22.0,Sex.MALE));assertEquals(FatGroup.FIT,BodyMetrics.fatGroup(22.0,Sex.FEMALE))
        assertNull(BodyMetrics.fatGroup(22.0,Sex.UNSPECIFIED))
        assertEquals("18–24%",BodyMetrics.fatRange(FatGroup.AVERAGE,Sex.MALE))
        assertTrue(BodyMetrics.notes(15).first().contains("Under 18"))
    }
    @Test fun suggestionsComeFromTheNumbers() {
        val b=balance(kg=100.0,target=85.0)
        val intake=(1..13).associate {today.minusDays(it.toLong()) to (b.budgetKcal+400 to true)}
        val d=EnergyHistory.days(b,profile,emptyMap(),emptyList(),intake,today.minusDays(13),today)
        val s=EnergyHistory.summary(d,emptyList(),zone)
        val advice=EnergyHistory.advice(AdviceInputs(b,profile,d,s,85.0,proteinPerDay=70.0,fibrePerDay=30.0,stepsPerDay=4000.0,workouts=null))
        val titles=advice.map {it.title}
        assertTrue(titles.toString(),titles.any {it.startsWith("Running")})
        assertTrue(titles.contains("More protein"));assertTrue(titles.contains("Walk a little more"));assertTrue(titles.contains("Every 5% counts"))
        assertFalse(titles.contains("Add fibre"));assertFalse(titles.contains("Add strength training"))
        assertEquals(AdviceTone.WARN,advice.first().tone)
        // A healthy, steady logger hears they're on track.
        val good=(1..13).associate {today.minusDays(it.toLong()) to (b.budgetKcal-100 to true)}
        val gd=EnergyHistory.days(b,profile,emptyMap(),emptyList(),good,today.minusDays(13),today)
        assertTrue(EnergyHistory.advice(AdviceInputs(b,profile,gd,EnergyHistory.summary(gd,emptyList(),zone),85.0)).any {it.title=="On track"})
    }
    @Test fun newHomeCardsFollowTrendsInSavedLayouts() {
        val old=listOf("trend","progress","crash","connect","goal_setup","rate","goal","average","streak","body_fat","insights","recent")
        val order=CardLayouts.homeOrder(old)
        assertEquals(listOf("trend","energy","bmi","progress"),order.take(4))
        assertEquals(CardLayouts.home.size,order.size)
        assertEquals(CardLayouts.home,CardLayouts.homeOrder(emptyList()))
        // A layout that already has them keeps the user's placement.
        val moved=listOf("bmi")+CardLayouts.home.filterNot {it=="bmi"}
        assertEquals(moved,CardLayouts.homeOrder(moved))
    }
}
