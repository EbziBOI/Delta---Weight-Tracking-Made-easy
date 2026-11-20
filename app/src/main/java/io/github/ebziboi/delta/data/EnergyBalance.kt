package io.github.ebziboi.delta.data

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

enum class Sex(val label:String) {FEMALE("Female"),MALE("Male"),UNSPECIFIED("Prefer not to say")}
enum class ActivityLevel(val label:String,val factor:Double,val detail:String) {
    SEDENTARY("Mostly sitting",1.2,"Desk work, little exercise"),
    LIGHT("Lightly active",1.375,"On your feet some of the day, or exercise 1–3 times a week"),
    MODERATE("Active",1.55,"Exercise 3–5 times a week"),
    VERY("Very active",1.725,"Hard exercise most days or a physical job"),
}

/** The optional body profile and energy choices. Every field can stay unset; the maths uses what is there. */
data class EnergyProfile(
    val enabled:Boolean=false,
    val heightCm:Double?=null,
    val birthYear:Int?=null,
    val sex:Sex=Sex.UNSPECIFIED,
    /** Use active calories and steps from Health Connect instead of a fixed activity level. */
    val useHealthActivity:Boolean=true,
    /** Share of a watch's active calories that is counted; wearables tend to overestimate. */
    val activityCredit:Double=.75,
    val activityLevel:ActivityLevel=ActivityLevel.LIGHT,
    /** Learn maintenance from logged intake and the weight trend. */
    val adaptive:Boolean=true,
    /** Keep the diary's daily energy target equal to the calculated budget. */
    val autoBudget:Boolean=false,
    /** Pace used when no goal date is set, in kg per week. */
    val weeklyKg:Double=.5,
    /** Write local diary entries to Health Connect. */
    val writeNutrition:Boolean=false,
) {
    fun age(today:LocalDate=LocalDate.now())=birthYear?.let {today.year-it}?.takeIf {it in 10..110}
    fun json():String=JSONObject().put("enabled",enabled).put("height",heightCm ?: JSONObject.NULL).put("birthYear",birthYear ?: JSONObject.NULL).put("sex",sex.name)
        .put("healthActivity",useHealthActivity).put("credit",activityCredit).put("level",activityLevel.name).put("adaptive",adaptive)
        .put("autoBudget",autoBudget).put("weeklyKg",weeklyKg).put("writeNutrition",writeNutrition).toString()
    companion object {
        fun parse(raw:String?):EnergyProfile=raw?.let {text->runCatching {
            val j=JSONObject(text)
            EnergyProfile(j.optBoolean("enabled"),j.optDouble("height").takeIf {it.isFinite() && it in 50.0..260.0},
                j.optInt("birthYear",0).takeIf {it in 1900..2100},Sex.entries.find {it.name==j.optString("sex")} ?: Sex.UNSPECIFIED,
                j.optBoolean("healthActivity",true),j.optDouble("credit",.75).takeIf {it.isFinite()}?.coerceIn(.3,1.0) ?: .75,
                ActivityLevel.entries.find {it.name==j.optString("level")} ?: ActivityLevel.LIGHT,j.optBoolean("adaptive",true),
                j.optBoolean("autoBudget"),j.optDouble("weeklyKg",.5).takeIf {it.isFinite()}?.coerceIn(.1,1.0) ?: .5,j.optBoolean("writeNutrition"))
        }.getOrNull()} ?: EnergyProfile()
    }
}

data class DailyActivity(val day:LocalDate,val activeKcal:Double?=null,val steps:Long?=null)
data class Workout(val start:Long,val end:Long,val title:String)

/** What Health Connect supplied for the energy maths. */
data class EnergyHealthData(val heightCm:Double?=null,val restingKcal:Double?=null,val activity:List<DailyActivity> = emptyList(),
                            val workouts:List<Workout> = emptyList(),val readAt:Long=0)

data class EnergyInputs(
    val profile:EnergyProfile,
    val weights:List<WeightEntry>,
    val bodyFat:List<BodyFatEntry> = emptyList(),
    /** Logged energy per day and whether every food that day reported it. */
    val intake:Map<LocalDate,Pair<Double,Boolean>> = emptyMap(),
    val health:EnergyHealthData=EnergyHealthData(),
    val targetKg:Double?=null,
    val targetDay:LocalDate?=null,
    val today:LocalDate=LocalDate.now(),
    val zone:ZoneId=ZoneId.systemDefault(),
)

data class EnergyBalance(
    val currentKg:Double,
    val heightCm:Double?,
    val age:Int?,
    val bmi:Double?,
    val bmiBand:String?,
    val restingKcal:Double,
    val restingMethod:String,
    val activityKcal:Double,
    val activityMethod:String,
    val todayActiveKcal:Double?,
    val todaySteps:Long?,
    val todayWorkouts:List<Workout>,
    val formulaKcal:Double,
    val observedKcal:Double?,
    /** 0 = formula only, up to 0.9 = mostly learned from your own intake and weight. */
    val confidence:Double,
    val maintenanceKcal:Double,
    /** Planned daily change from maintenance: negative is a deficit. */
    val dailyChange:Double,
    val budgetKcal:Double,
    val paceKgPerWeek:Double,
    val goalNote:String,
    val capped:Boolean,
    val floored:Boolean,
    val loggedDays:Int,
    val weighIns:Int,
    val needsProfile:Boolean,
)

object EnergyMath {
    const val KCAL_PER_KG=7700.0
    /** Roughly 0.04 kcal per step for a 70 kg adult, scaled by weight. */
    fun stepKcal(steps:Long,kg:Double)=steps*kg*.00055
    fun bmi(kg:Double,heightCm:Double)=kg/((heightCm/100)*(heightCm/100))
    fun bmiBand(bmi:Double)=when {bmi<18.5->"Below the healthy range";bmi<25->"Healthy range";bmi<30->"Above the healthy range";else->"Well above the healthy range"}
    /** Mifflin–St Jeor; an unspecified sex uses the midpoint of the two constants. */
    fun mifflin(kg:Double,heightCm:Double,age:Int,sex:Sex)=10*kg+6.25*heightCm-5*age+when(sex) {Sex.MALE->5.0;Sex.FEMALE->-161.0;Sex.UNSPECIFIED->-78.0}
    /** Katch–McArdle, from lean mass: better than height/age formulas whenever body fat is known. */
    fun katch(kg:Double,bodyFatPercent:Double)=370+21.6*kg*(1-bodyFatPercent/100)
    fun minimumIntake(sex:Sex)=when(sex) {Sex.FEMALE->1200.0;Sex.MALE->1500.0;Sex.UNSPECIFIED->1350.0}

    /** Least-squares slope in kg per day. */
    internal fun slope(points:List<Pair<Double,Double>>):Double? {
        if(points.size<2) return null
        val mx=points.sumOf {it.first}/points.size;val my=points.sumOf {it.second}/points.size
        val sxx=points.sumOf {(it.first-mx)*(it.first-mx)}
        if(sxx<=0) return null
        return points.sumOf {(it.first-mx)*(it.second-my)}/sxx
    }

    fun compute(input:EnergyInputs):EnergyBalance? {
        val p=input.profile;val today=input.today;val zone=input.zone
        fun day(ms:Long)=Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val weights=input.weights.filter {it.kg.isFinite() && it.kg in 20.0..400.0 && day(it.epochMs)<=today}.sortedBy {it.epochMs}
        val latest=weights.lastOrNull() ?: return null
        // A week's average is steadier than one weigh-in.
        val recent=weights.filter {it.epochMs>=latest.epochMs-7*86_400_000L}
        val kg=recent.map {it.kg}.average()
        val height=p.heightCm ?: input.health.heightCm
        val age=p.age(today)
        val bmi=height?.let {bmi(kg,it)}
        val fat=input.bodyFat.filter {BodyFat.valid(it.percent) && it.percent in 3.0..70.0 && ChronoUnit.DAYS.between(day(it.epochMs),today) in 0..45}.maxByOrNull {it.epochMs}
        val (resting,restingMethod)=when {
            input.health.restingKcal!=null && input.health.restingKcal in 800.0..4500.0 -> input.health.restingKcal to "Resting energy from Health Connect"
            fat!=null -> katch(kg,fat.percent) to "From lean mass (Katch–McArdle, ${BodyFat.format(fat.percent)} body fat)"
            height!=null && age!=null -> mifflin(kg,height,age,p.sex) to "From height, age${if(p.sex!=Sex.UNSPECIFIED) ", sex" else ""} and weight (Mifflin–St Jeor)"
            else -> kg*22 to "Estimated from weight only · add height and age for accuracy"
        }
        val needsProfile=fat==null && (height==null || age==null) && input.health.restingKcal==null

        // Activity from the last 14 complete days.
        val window=input.health.activity.filter {it.day<today && ChronoUnit.DAYS.between(it.day,today)<=14}
        val perDay=window.mapNotNull {a->a.activeKcal?.takeIf {it>0} ?: a.steps?.takeIf {it>0}?.let {stepKcal(it,kg)}}
        val (activity,activityMethod)=if(p.useHealthActivity && perDay.size>=3) {
            val average=perDay.average()
            average*p.activityCredit to "Health Connect · ${Math.round(average)} kcal/day active over ${perDay.size} days, ${Math.round(p.activityCredit*100)}% counted"
        } else resting*(p.activityLevel.factor-1.1) to "${p.activityLevel.label} (×${p.activityLevel.factor})"
        // 10% of intake goes on digesting food.
        val formula=resting*1.1+activity

        // Adaptive maintenance: average intake minus the energy in the weight trend's change over 28 days.
        val start=today.minusDays(28)
        val logged=input.intake.filter {(d,v)->d>=start && d<today && v.second && v.first>=formula*.4}
        val trendPoints=weights.filter {day(it.epochMs)>=start && day(it.epochMs)<today}.map {it.epochMs/86_400_000.0 to it.kg}
        val span=trendPoints.takeIf {it.size>=2}?.let {it.last().first-it.first().first} ?: 0.0
        val observedRaw=if(p.adaptive && logged.size>=10 && trendPoints.size>=4 && span>=10) slope(trendPoints)?.let {logged.values.map {it.first}.average()-it*KCAL_PER_KG} else null
        val plausible=observedRaw?.let {it in formula*.6..formula*1.5}==true
        val observed=observedRaw?.coerceIn(formula*.6,formula*1.5)
        val confidence=if(observed==null) 0.0 else
            (((logged.size-7)/14.0).coerceIn(0.0,1.0)*(trendPoints.size/8.0).coerceIn(0.0,1.0)*(if(plausible) 1.0 else .5)).coerceIn(0.0,.9)
        val maintenance=formula*(1-confidence)+(observed ?: formula)*confidence

        // The plan: the goal date sets the pace when there is one; otherwise the chosen weekly pace.
        val target=input.targetKg?.takeIf {it.isFinite() && it in 20.0..400.0}
        val gap=target?.let {it-kg} ?: 0.0
        var capped=false
        val wanted=when {
            target==null || abs(gap)<.3 -> 0.0
            input.targetDay!=null && input.targetDay.isAfter(today) -> gap*KCAL_PER_KG/ChronoUnit.DAYS.between(today,input.targetDay).toDouble()
            else -> (if(gap<0) -p.weeklyKg else p.weeklyKg.coerceAtMost(.5))*KCAL_PER_KG/7
        }
        // At most 1% of body weight a week down, or 0.5% up.
        val limit=if(wanted<0) kg*.01*KCAL_PER_KG/7 else kg*.005*KCAL_PER_KG/7
        val change=if(abs(wanted)>limit) {capped=true;if(wanted<0) -limit else limit} else wanted
        val floor=maxOf(resting,minimumIntake(p.sex))
        var budget=maintenance+change
        val floored=budget<floor && change<0
        if(floored) budget=floor
        budget=(budget/10).roundToLong()*10.0
        val pace=(budget-maintenance)*7/KCAL_PER_KG
        val note=when {
            target==null -> "Set a target weight for a deficit or surplus. This budget maintains your weight."
            abs(gap)<.3 -> "You're at your target, so this budget maintains it."
            floored -> "Held at a minimum of ${Math.round(floor)} kcal; that paces ${fmt(abs(pace))} kg a week."
            capped && input.targetDay!=null -> "Your goal date needs ${fmt(abs(wanted)*7/KCAL_PER_KG)} kg a week; held at a safer ${fmt(abs(pace))} kg."
            capped -> "Held at a safer ${fmt(abs(pace))} kg a week."
            else -> "${if(pace<0) "Losing" else "Gaining"} ${fmt(abs(pace))} kg a week${if(input.targetDay!=null) " reaches your target by your goal date" else
                pace.takeIf {abs(it)>.01}?.let {" reaches your target in about ${Math.round(abs(gap/it))} weeks"} ?: ""}."
        }
        val todayActivity=input.health.activity.firstOrNull {it.day==today}
        return EnergyBalance(kg,height,age,bmi,bmi?.let(::bmiBand),resting,restingMethod,activity,activityMethod,
            todayActivity?.activeKcal,todayActivity?.steps,input.health.workouts.filter {day(it.start)==today},
            formula,observed,confidence,maintenance,change,budget,pace,note,capped,floored,logged.size,trendPoints.size,needsProfile)
    }
    private fun fmt(v:Double)=String.format(java.util.Locale.UK,"%.2f",v).trimEnd('0').trimEnd('.')
}
