package io.github.ebziboi.delta.data

/** The WHO adult BMI groups, with a plain description of what each one generally means. */
enum class BmiGroup(val label:String,val from:Double,val to:Double,val summary:String,val alias:String?=null) {
    UNDER("Underweight",0.0,18.5,"Below the range linked with the lowest health risk. A low weight can mean less energy, weaker bones and lower immunity."),
    HEALTHY("Healthy weight",18.5,25.0,"The range linked with the lowest risk of weight-related conditions for most adults."),
    OVER("Overweight",25.0,30.0,"A somewhat raised risk of high blood pressure, type 2 diabetes and heart disease, less so if you carry a lot of muscle.","Pre-obesity"),
    OBESE_1("Obesity class I",30.0,35.0,"A clearly raised risk of type 2 diabetes, high blood pressure, sleep apnoea and joint pain.","Moderate obesity"),
    OBESE_2("Obesity class II",35.0,40.0,"A high risk of weight-related conditions. Support from a doctor, including treatment options, is worth asking about.","Severe obesity"),
    OBESE_3("Obesity class III",40.0,99.0,"A very high risk of weight-related conditions. A doctor can help with a plan, including treatments beyond diet and exercise.","Very severe obesity");
    companion object {fun of(bmi:Double)=entries.last {bmi>=it.from}}
}

/** Body-fat groups as used by the American Council on Exercise. */
enum class FatGroup(val label:String) {ESSENTIAL("Essential fat"),ATHLETIC("Athletic"),FIT("Fit"),AVERAGE("Average"),HIGH("Above average")}

object BodyMetrics {
    const val HEALTHY_LOW=18.5
    const val HEALTHY_HIGH=24.9

    fun weightAt(bmi:Double,heightCm:Double)=bmi*(heightCm/100)*(heightCm/100)
    fun healthyRange(heightCm:Double)=weightAt(HEALTHY_LOW,heightCm)..weightAt(HEALTHY_HIGH,heightCm)

    /** Lower limits of each group; women naturally carry more essential fat. */
    private fun limits(sex:Sex)=when(sex) {Sex.FEMALE->listOf(14.0,21.0,25.0,32.0);Sex.MALE->listOf(6.0,14.0,18.0,25.0);Sex.UNSPECIFIED->null}
    fun fatGroup(percent:Double,sex:Sex):FatGroup?=limits(sex)?.let {l->FatGroup.entries[l.count {percent>=it}]}
    fun fatRange(group:FatGroup,sex:Sex):String?=limits(sex)?.let {l->
        val i=group.ordinal
        when(i) {0->"under ${l[0].toInt()}%";FatGroup.entries.size-1->"${l.last().toInt()}% and over";else->"${l[i-1].toInt()}–${l[i].toInt()-1}%"}
    }

    /** Points worth knowing when reading the number, specific to the person where possible. */
    fun notes(age:Int?):List<String> = buildList {
        if(age!=null && age<18) add("Under 18, BMI is judged against percentiles for age and sex, so these adult groups don't apply.")
        add("BMI can't tell muscle from fat, so muscular people often read high. A body-fat reading or waist size says more.")
        if(age!=null && age>=65) add("Over 65, a slightly higher BMI is often fine. Keeping muscle matters more than a lower number.")
        add("For people of South Asian, Chinese, other Asian, Middle Eastern, Black African or African-Caribbean background, risk rises at lower BMIs: from about 23 and 27.5.")
    }
}
