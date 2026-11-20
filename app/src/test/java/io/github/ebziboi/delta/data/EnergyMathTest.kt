package io.github.ebziboi.delta.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class EnergyMathTest {
    private val today=LocalDate.of(2026,11,20)
    private val zone=ZoneOffset.UTC
    private fun at(daysAgo:Long)=today.minusDays(daysAgo).atTime(8,0).toInstant(zone).toEpochMilli()
    private fun weigh(kg:Double,daysAgo:Long)=WeightEntry(at(daysAgo),kg,true,"w$daysAgo")
    private val profile=EnergyProfile(enabled=true,heightCm=180.0,birthYear=1990,sex=Sex.MALE,useHealthActivity=false,activityLevel=ActivityLevel.SEDENTARY)
    private fun inputs(p:EnergyProfile=profile,weights:List<WeightEntry> = listOf(weigh(90.0,0)),target:Double?=null,targetDay:LocalDate?=null,
                       intake:Map<LocalDate,Pair<Double,Boolean>> = emptyMap(),fat:List<BodyFatEntry> = emptyList(),health:EnergyHealthData=EnergyHealthData())=
        EnergyInputs(p,weights,fat,intake,health,target,targetDay,today,zone)

    @Test fun mifflinWithSedentaryActivity() {
        val b=EnergyMath.compute(inputs())!!
        // 10×90 + 6.25×180 − 5×36 + 5 = 1850
        assertEquals(1850.0,b.restingKcal,.01);assertEquals(1850*1.2,b.formulaKcal,.01)
        assertEquals(27.8,b.bmi!!,.05);assertEquals(0.0,b.dailyChange,.0)
    }
    @Test fun leanMassWinsWhenBodyFatIsRecent() {
        val b=EnergyMath.compute(inputs(fat=listOf(BodyFatEntry(at(3),20.0,true,"f"))))!!
        assertEquals(370+21.6*72,b.restingKcal,.01);assertTrue(b.restingMethod.contains("lean"))
    }
    @Test fun goalDateSetsTheDeficitAndIsCappedForSafety() {
        val normal=EnergyMath.compute(inputs(target=85.0,targetDay=today.plusDays(70)))!!
        assertEquals(-5*7700/70.0,normal.dailyChange,.5);assertFalse(normal.capped)
        // At most 1% of body weight a week.
        val rushed=EnergyMath.compute(inputs(p=profile.copy(activityLevel=ActivityLevel.VERY),target=70.0,targetDay=today.plusDays(30)))!!
        assertTrue(rushed.capped);assertFalse(rushed.floored);assertEquals(-.9,rushed.paceKgPerWeek,.05)
        // Never below resting energy.
        val sedentary=EnergyMath.compute(inputs(target=70.0,targetDay=today.plusDays(30)))!!
        assertTrue(sedentary.floored);assertEquals(1850.0,sedentary.budgetKcal,10.0)
    }
    @Test fun watchActivityIsDiscounted() {
        val activity=(1..10).map {DailyActivity(today.minusDays(it.toLong()),activeKcal=600.0)}
        val b=EnergyMath.compute(inputs(p=profile.copy(useHealthActivity=true,activityCredit=.75),health=EnergyHealthData(activity=activity)))!!
        assertEquals(450.0,b.activityKcal,.01);assertEquals(1850*1.1+450,b.formulaKcal,.01)
    }
    @Test fun maintenanceIsLearnedFromIntakeAndTrend() {
        // Eating 2,500 a day while losing 0.25 kg a week: real maintenance ≈ 2,500 + 275.
        val weights=(0..27).map {weigh(90.0+(27-it)*(.25/7),27L-it)}
        val intake=(1..27).associate {today.minusDays(it.toLong()) to (2500.0 to true)}
        val b=EnergyMath.compute(inputs(weights=weights,intake=intake))!!
        assertEquals(2775.0,b.observedKcal!!,40.0)
        assertTrue(b.confidence>.5)
        assertTrue(b.maintenanceKcal>b.formulaKcal)
    }
    @Test fun noWeightMeansNoBudget() {
        assertNull(EnergyMath.compute(inputs(weights=emptyList())))
    }
}
