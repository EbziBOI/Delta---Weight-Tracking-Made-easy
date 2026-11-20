package io.github.ebziboi.delta.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * What a day's budget was built from. Delta saves it while the day is current, so changing the goal or
 * profile later doesn't rewrite past days. Activity is added per day on top of [maintenanceBase].
 */
data class DayPlan(
    /** Maintenance without the day's own activity. */
    val maintenanceBase:Double,
    /** Planned change from maintenance: negative is a deficit. */
    val change:Double,
    val floor:Double,
    /** Whether measured activity is added day by day; false when a fixed activity level is already in the base. */
    val addsActivity:Boolean,
    /** The credited daily activity assumed when a day has no measurement. */
    val typicalActivity:Double,
    /** Share of measured active energy that counts. */
    val credit:Double,
) {
    fun encode()=listOf(Math.round(maintenanceBase),Math.round(change),Math.round(floor),if(addsActivity) 1 else 0,Math.round(typicalActivity),Math.round(credit*100)).joinToString(";")
    companion object {
        fun of(b:EnergyBalance,p:EnergyProfile)=DayPlan(b.maintenanceKcal-(if(b.activityFromHealth) b.activityKcal else 0.0),b.dailyChange,b.floorKcal,
            b.activityFromHealth,if(b.activityFromHealth) b.activityKcal else 0.0,p.activityCredit)
        fun decode(raw:String?):DayPlan?=raw?.split(';')?.map {it.toDoubleOrNull()}?.takeIf {it.size==6 && it.all {v->v!=null}}?.map {it!!}
            ?.takeIf {it[0] in 500.0..8000.0}?.let {DayPlan(it[0],it[1],it[2],it[3]==1.0,it[4],(it[5]/100).coerceIn(.3,1.0))}
    }
}

/** One day's budget, maintenance and what was eaten. */
data class DayBudget(
    val day:LocalDate,
    val maintenanceKcal:Double,
    val budgetKcal:Double,
    /** Active energy counted for the day; null when a fixed activity level is used. */
    val activityKcal:Double?,
    /** Whether [activityKcal] comes from a measurement rather than the usual average. */
    val activityMeasured:Boolean,
    val steps:Long?,
    val intakeKcal:Double?,
    /** Every food reported energy and the total looks like a whole day's eating. */
    val complete:Boolean,
    /** No plan was saved for the day, so the current profile and goal stand in. */
    val estimated:Boolean,
    val isToday:Boolean,
) {
    val planned get()=budgetKcal-maintenanceKcal
    val vsBudget get()=intakeKcal?.let {it-budgetKcal}
    val vsMaintenance get()=intakeKcal?.let {it-maintenanceKcal}
    /** Finished, fully logged days are the ones that count towards totals. */
    val counts get()=complete && !isToday && intakeKcal!=null
    /** Under budget while losing, reaching it while gaining, close to it while maintaining. */
    val onBudget get()=vsBudget?.let {v->when {planned< -50->v<=50;planned>50->v>= -100;else->abs(v)<=150}} ?: false
}

data class BalanceSummary(
    val days:Int,
    val countedDays:Int,
    val onBudgetDays:Int,
    val averageVsBudget:Double?,
    /** Sum of intake minus maintenance over counted days: negative is a deficit. */
    val totalVsMaintenance:Double,
    val expectedKg:Double,
    /** Change in the weigh-in trend over the same days, when there are enough weigh-ins. */
    val actualKg:Double?,
    val streak:Int,
    /** Extra eaten on Friday to Sunday compared with other days, when both are known. */
    val weekendExtra:Double?,
)

enum class AdviceTone {GOOD,INFO,WARN}
data class Advice(val title:String,val detail:String,val tone:AdviceTone)

data class AdviceInputs(
    val balance:EnergyBalance,
    val profile:EnergyProfile,
    /** The last two weeks, oldest first. */
    val recent:List<DayBudget>,
    val summary:BalanceSummary,
    val targetKg:Double?,
    val proteinPerDay:Double?=null,
    val fibrePerDay:Double?=null,
    val stepsPerDay:Double?=null,
    /** Workouts in the last 14 days, or null when Delta can't read them. */
    val workouts:Int?=null,
    val weight:(Double)->String={String.format(java.util.Locale.UK,"%.1f kg",it)},
    /** An unsigned difference in the person's unit. */
    val amount:(Double)->String=weight,
)

object EnergyHistory {
    private fun round10(v:Double)=(v/10).roundToLong()*10.0

    /** Budgets for every day from [from] to [today]. Saved plans are used where they exist. */
    fun days(b:EnergyBalance,profile:EnergyProfile,plans:Map<LocalDate,DayPlan>,activity:List<DailyActivity>,
             intake:Map<LocalDate,Pair<Double,Boolean>>,from:LocalDate,today:LocalDate):List<DayBudget> {
        val current=DayPlan.of(b,profile)
        val byDay=activity.associateBy {it.day}
        return generateSequence(from) {it.plusDays(1)}.takeWhile {!it.isAfter(today)}.map {d->
            val saved=if(d==today) null else plans[d]
            val plan=saved ?: current
            val a=byDay[d]
            val measured=a?.activeKcal?.takeIf {it>0} ?: a?.steps?.takeIf {it>0}?.let {EnergyMath.stepKcal(it,b.currentKg)}
            val credited=if(!plan.addsActivity) null else when {
                measured==null -> plan.typicalActivity
                // Today's activity is still growing, so the usual amount stands until it's beaten.
                d==today -> maxOf(measured*plan.credit,plan.typicalActivity)
                else -> measured*plan.credit
            }
            val maintenance=plan.maintenanceBase+(credited ?: 0.0)
            var budget=maintenance+plan.change
            if(plan.change<0 && budget<plan.floor) budget=plan.floor
            val eaten=intake[d]
            DayBudget(d,maintenance,round10(budget),credited,measured!=null && plan.addsActivity,a?.steps,eaten?.first,
                eaten!=null && eaten.second && eaten.first>=maintenance*.4,saved==null && d<today,d==today)
        }.toList()
    }

    fun summary(days:List<DayBudget>,weights:List<WeightEntry>,zone:ZoneId=ZoneId.systemDefault()):BalanceSummary {
        val counted=days.filter {it.counts}
        val total=counted.sumOf {it.vsMaintenance!!}
        val actual=counted.takeIf {it.size>=5}?.let {c->
            val start=c.first().day;val end=c.last().day
            val points=weights.filter {w->Instant.ofEpochMilli(w.epochMs).atZone(zone).toLocalDate().let {it>=start && it<=end.plusDays(1)}}
                .map {it.epochMs/86_400_000.0 to it.kg}
            val span=points.takeIf {it.size>=3}?.let {it.maxOf {p->p.first}-it.minOf {p->p.first}} ?: 0.0
            if(span>=5) EnergyMath.slope(points)?.let {it*(end.toEpochDay()-start.toEpochDay()+1)} else null
        }
        var streak=0
        for(d in days.asReversed()) {if(d.isToday) continue;if(d.counts && d.onBudget) streak++ else break}
        fun weekend(d:DayBudget)=d.day.dayOfWeek in setOf(DayOfWeek.FRIDAY,DayOfWeek.SATURDAY,DayOfWeek.SUNDAY)
        val (end,rest)=counted.partition(::weekend)
        val weekendExtra=if(end.size>=2 && rest.size>=3) end.map {it.intakeKcal!!}.average()-rest.map {it.intakeKcal!!}.average() else null
        return BalanceSummary(days.size,counted.size,counted.count {it.onBudget},counted.takeIf {it.isNotEmpty()}?.map {it.vsBudget!!}?.average(),
            total,total/EnergyMath.KCAL_PER_KG,actual,streak,weekendExtra)
    }

    private fun k(v:Double)=java.text.DecimalFormat("#,##0").format(Math.round(v))
    private fun one(v:Double)=String.format(java.util.Locale.UK,"%.1f",v)

    /** Suggestions from the person's own numbers, most useful first. */
    fun advice(i:AdviceInputs):List<Advice> {
        val b=i.balance;val out=mutableListOf<Advice>()
        val losing=b.dailyChange< -50;val gaining=b.dailyChange>50
        val height=b.heightCm
        val group=b.bmi?.let(BmiGroup::of)
        val counted=i.recent.filter {it.counts}

        if(height==null) out+=Advice("Add your height","BMI, your healthy weight range and resting energy all need it. Add it in Body & energy, or let Health Connect supply it.",AdviceTone.INFO)
        if(b.age!=null && b.age<18) out+=Advice("Talk to a doctor before dieting","Adult BMI groups and calorie deficits aren't designed for under-18s, who are still growing.",AdviceTone.WARN)
        if(height!=null && i.targetKg!=null) {
            val targetBmi=EnergyMath.bmi(i.targetKg,height)
            if(targetBmi<BodyMetrics.HEALTHY_LOW) out+=Advice("Your target is below a healthy weight",
                "${i.weight(i.targetKg)} is a BMI of ${one(targetBmi)} for your height. The healthy range starts at ${i.weight(BodyMetrics.weightAt(BodyMetrics.HEALTHY_LOW,height))}.",AdviceTone.WARN)
        }
        if(group!=null && height!=null) when(group) {
            BmiGroup.UNDER -> out+=Advice("Aim to gain gently","A healthy weight for your height starts at ${i.weight(BodyMetrics.weightAt(BodyMetrics.HEALTHY_LOW,height))}. If the low weight is unexpected, it's worth a check with your doctor.",AdviceTone.WARN)
            BmiGroup.HEALTHY -> if(losing && b.paceKgPerWeek< -.3) out+=Advice("You're already in the healthy range",
                "A slower pace such as 0.25 kg a week, with enough protein and some strength training, keeps more muscle as you lean out.",AdviceTone.INFO)
            else -> {
                val five=b.currentKg*.05;val ten=b.currentKg*.10
                out+=Advice("Every 5% counts","Losing ${i.amount(five)} to ${i.amount(ten)} (5–10%) improves blood pressure, blood sugar and joint load for most people, well before the healthy range at ${i.weight(BodyMetrics.healthyRange(height).endInclusive)}.",AdviceTone.INFO)
            }
        }
        val completeDays=i.recent.count {it.counts}
        val finished=i.recent.count {!it.isToday}
        if(finished>=7 && completeDays<finished*.6) out+=Advice("Log whole days",
            "$completeDays of the last $finished days are fully logged. Complete days let Delta learn your real maintenance and make the balance chart reliable.",AdviceTone.INFO)
        val avg=i.summary.averageVsBudget
        if(losing && avg!=null && counted.size>=5 && avg>150) {
            val gentler=(i.profile.weeklyKg-.25).takeIf {it>0 && i.targetKg!=null}?.let {" Choosing 0.25 kg a week in Body & energy adds about ${k(it*EnergyMath.KCAL_PER_KG/7)} kcal back to the budget."} ?: ""
            out+=Advice("Running ${k(avg)} kcal over a day","That's the average over ${counted.size} logged days.$gentler",AdviceTone.WARN)
        }
        i.summary.weekendExtra?.takeIf {it>300}?.let {out+=Advice("Weekends add up","You eat about ${k(it)} kcal more on Friday to Sunday than on other days. Planning one treat meal instead of three keeps most of the fun.",AdviceTone.INFO)}
        val low=counted.count {it.intakeKcal!!<b.floorKcal-100}
        if(losing && low>=3) out+=Advice("Some days are very low","$low days were under ${k(b.floorKcal)} kcal. Very low days make hunger, energy and keeping muscle harder; spreading intake evenly is easier to sustain.",AdviceTone.WARN)
        i.proteinPerDay?.let {protein->
            // With a high BMI, protein is based on the weight at a BMI of 25.
            val reference=if(height!=null && (b.bmi ?: 0.0)>=30) BodyMetrics.weightAt(25.0,height) else b.currentKg
            val perKg=if(losing || gaining) 1.6 else 1.2
            val goal=reference*perKg
            if(protein<goal*.8) out+=Advice("More protein","You average ${k(protein)} g a day. Around ${k(goal)} g (${one(perKg)} g per kg) ${if(losing) "protects muscle while losing and keeps you fuller" else if(gaining) "helps the gain be muscle" else "supports muscle and fullness"}.",AdviceTone.INFO)
        }
        i.fibrePerDay?.takeIf {it<22}?.let {out+=Advice("Add fibre","About ${k(it)} g a day; 30 g helps with fullness and digestion. Beans, oats, wholegrains, fruit and veg are easy wins.",AdviceTone.INFO)}
        i.stepsPerDay?.takeIf {it<7000}?.let {steps->
            val extra=EnergyMath.stepKcal(2000,b.currentKg)
            out+=Advice("Walk a little more","You average ${k(steps)} steps. Another 2,000 a day burns about ${k(extra)} kcal, roughly ${i.amount(extra*30/EnergyMath.KCAL_PER_KG)} a month.",AdviceTone.INFO)
        }
        if(losing && i.workouts==0) out+=Advice("Add strength training","No workouts in the last two weeks. Two or three strength sessions a week help keep muscle while you lose weight.",AdviceTone.INFO)
        val s=i.summary
        if(s.actualKg!=null && s.countedDays>=10) {
            val gap=s.actualKg-s.expectedKg
            if(s.expectedKg< -.3 && gap>.5 && gap>abs(s.expectedKg)*.4) out+=Advice("Slower than your logs suggest",
                "Your logs predict ${i.amount(abs(s.expectedKg))} lost; the scale trend shows ${i.amount(abs(minOf(s.actualKg,0.0)))}. Cooking oils, drinks and bites are the usual gap. Learned maintenance adjusts your budget as it sees this.",AdviceTone.INFO)
            else if(s.expectedKg< -.3 && gap< -.7) out+=Advice("Faster than expected",
                "The scale is ahead of your logs by ${i.amount(abs(gap))}. Early on that is mostly water; if it continues, learned maintenance raises your budget.",AdviceTone.INFO)
        }
        if(counted.size>=7 && s.onBudgetDays>=s.countedDays*.7) out+=Advice("On track",
            "${s.onBudgetDays} of ${s.countedDays} logged days on budget${if(s.streak>=3) ", ${s.streak} in a row" else ""}. Keep weighing in so the budget keeps learning.",AdviceTone.GOOD)
        return out.distinctBy {it.title}.sortedBy {when(it.tone) {AdviceTone.WARN->0;AdviceTone.INFO->1;AdviceTone.GOOD->2}}.take(6)
    }
}
