package io.github.ebziboi.delta.nutrition

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

internal data class LabelBasis(val amount:Double?,val unit:FoodUnit=FoodUnit.GRAMS,val title:String="Choose basis")
internal data class LabelObservation(val value:Double,val bounded:Boolean=false,val raw:String="")
internal data class LabelColumn(val basis:LabelBasis,val observations:Map<Nutrient,LabelObservation>) {
    fun per100(amount:Double?=basis.amount):Nutrients = if(amount==null || !amount.isFinite() || amount<=0 || amount>100000) emptyMap() else
        observations.filterValues {!it.bounded}.mapValues {it.value.value*100/amount}.filterValues {it.validAmount()}
}
internal data class LabelRead(val columns:List<LabelColumn>,val raw:String,val warnings:List<String> = emptyList()) {
    val best get()=columns.indices.maxByOrNull {i->columns[i].observations.size+(if(columns[i].basis.amount==100.0 && columns[i].observations.isNotEmpty()) 100 else 0)} ?: 0
    fun json()=JSONObject().put("raw",raw.take(18000)).put("warnings",JSONArray(warnings)).put("columns",JSONArray().also {a->columns.forEach {c->a.put(JSONObject()
        .put("amount",c.basis.amount).put("unit",c.basis.unit.name).put("title",c.basis.title).put("values",JSONObject().also {v->c.observations.forEach {(n,o)->v.put(n.name,JSONObject().put("value",o.value).put("bounded",o.bounded).put("raw",o.raw))}}))}}).toString()
    companion object {
        fun fromJson(raw:String)=runCatching {
            val j=JSONObject(raw);val a=j.getJSONArray("columns")
            LabelRead((0 until a.length()).map {i->val c=a.getJSONObject(i);val v=c.getJSONObject("values")
                LabelColumn(LabelBasis(c.positive("amount"),FoodUnit.valueOf(c.getString("unit")),c.getString("title")),Nutrient.entries.mapNotNull {n->v.optJSONObject(n.name)?.let {n to LabelObservation(it.getDouble("value"),it.optBoolean("bounded"),it.optString("raw"))}}.toMap())
            },j.optString("raw"),j.optJSONArray("warnings")?.let {w->(0 until w.length()).map {w.getString(it)}} ?: emptyList())
        }.getOrNull()
    }
}

/** Only transcribes supplied numbers. Missing, bounded and ambiguous values are never filled as zeros. */
internal object LabelParser {
    private val aliases=mapOf(
        Nutrient.ENERGY to listOf("energy","calories","calorie"),Nutrient.FAT to listOf("total fat","fat","total lipid"),
        Nutrient.SATURATED to listOf("of which saturates","of which saturated","saturated fat","saturated fatty acids","saturates","saturated"),
        Nutrient.CARBS to listOf("total carbohydrate","total carbohydrates","carbohydrates","carbohydrate","carbs"),
        Nutrient.SUGAR to listOf("of which sugars","total sugars","sugars","sugar"),Nutrient.FIBRE to listOf("dietary fibre","dietary fiber","fibre","fiber"),
        Nutrient.PROTEIN to listOf("protein","proteins"),Nutrient.SALT to listOf("salt"),Nutrient.SODIUM to listOf("sodium"),
        Nutrient.CHOLESTEROL to listOf("cholesterol"),Nutrient.TRANSFAT to listOf("trans fat"),Nutrient.MONOUNSATURATED to listOf("monounsaturated fat","mono-unsaturates","monounsaturates"),
        Nutrient.POLYUNSATURATED to listOf("polyunsaturated fat","polyunsaturates"),Nutrient.POTASSIUM to listOf("potassium"),Nutrient.CALCIUM to listOf("calcium"),
        Nutrient.IRON to listOf("iron"),Nutrient.MAGNESIUM to listOf("magnesium"),Nutrient.ZINC to listOf("zinc"),Nutrient.PHOSPHORUS to listOf("phosphorus"),
        Nutrient.SELENIUM to listOf("selenium"),Nutrient.IODINE to listOf("iodine"),Nutrient.COPPER to listOf("copper"),Nutrient.MANGANESE to listOf("manganese"),
        Nutrient.VIT_A to listOf("vitamin a"),Nutrient.VIT_C to listOf("vitamin c"),Nutrient.VIT_D to listOf("vitamin d"),Nutrient.VIT_E to listOf("vitamin e"),Nutrient.VIT_K to listOf("vitamin k"),
        Nutrient.B1 to listOf("thiamin","thiamine","vitamin b1"),Nutrient.B2 to listOf("riboflavin","vitamin b2"),Nutrient.B3 to listOf("niacin","vitamin b3"),
        Nutrient.B6 to listOf("vitamin b6"),Nutrient.B12 to listOf("vitamin b12"),Nutrient.FOLATE to listOf("folate","folacin"),Nutrient.FOLIC_ACID to listOf("folic acid"),
        Nutrient.B5 to listOf("pantothenic acid"),Nutrient.BIOTIN to listOf("biotin"),Nutrient.CAFFEINE to listOf("caffeine"),Nutrient.ALCOHOL to listOf("alcohol"))
    private val names=aliases.flatMap {(n,words)->words.map {it to n}}.sortedByDescending {it.first.length}
    private fun clean(s:String)=s.lowercase(Locale.ROOT).replace('µ','u').replace('μ','u').replace('\u00a0',' ').replace(Regex("[‐‑–—]"),"-").replace(Regex(" +")," ").trim().removePrefix("-").trim()
    private fun label(s:String):Pair<Nutrient,Int>? {
        val x=clean(s)
        return names.firstNotNullOfOrNull {(name,n)->if(x.startsWith(name) && (x.length==name.length || !x[name.length].isLetterOrDigit())) n to name.length else null}
    }
    private val numbers=Regex("(?<![a-z0-9.,])([<>≤≥]\\s*|less than\\s+)?(\\d+(?:[.,]\\d+)?|[.,]\\d+)\\s*(kcal|calories|cal|kj|mcg|ug|mg|g|%)?(?![a-z0-9])",RegexOption.IGNORE_CASE)
    private fun observation(text:String,n:Nutrient,defaultUnit:String=n.unit):LabelObservation? {
        val hits=numbers.findAll(clean(text)).filter {it.groupValues[3]!="%"}.toList()
        val hit=if(n==Nutrient.ENERGY) hits.firstOrNull {it.groupValues[3] in listOf("kcal","cal","calories")} ?: hits.firstOrNull() else hits.firstOrNull()
        hit ?: return null
        val value=hit.groupValues[2].replace(',','.').toDoubleOrNull() ?: return null
        val unit=hit.groupValues[3].ifBlank {defaultUnit}.replace("µ","u")
        val factor=if(n==Nutrient.ENERGY) when(unit) {"kj"->1/4.184;"kcal","cal","calories"->1.0;else->return null}
            else {
                val grams=when(unit) {"g"->1.0;"mg"->.001;"ug","mcg"->.000001;else->return null}
                grams/when(n.unit) {"g"->1.0;"mg"->.001;"µg"->.000001;else->return null}
            }
        return (value*factor).takeIf {it.validAmount()}?.let {LabelObservation(it,hit.groupValues[1].isNotBlank(),text.take(160))}
    }
    private fun values(text:String,n:Nutrient,defaultUnit:String):List<LabelObservation> {
        if(text.contains('\t')) return text.split('\t').filter {it.isNotBlank()}.mapNotNull {observation(it,n,defaultUnit)}
        val hits=numbers.findAll(text).filter {it.groupValues[3]!="%"}.toList()
        if(n==Nutrient.ENERGY) {
            val kcals=hits.filter {it.groupValues[3].lowercase() in listOf("kcal","calories","cal")}
            val use=kcals.ifEmpty {hits.filter {it.groupValues[3].equals("kj",true)}}.ifEmpty {hits}
            return use.mapNotNull {observation(it.value,n,defaultUnit)}
        }
        return hits.mapNotNull {observation(it.value,n,defaultUnit)}
    }
    fun parse(raw:String):LabelRead {
        require(raw.length<=200000) {"The label text is too long"}
        val initial=raw.lines().map(::clean).filter {it.isNotBlank()}
        val us=initial.take(8).any {it.contains("nutrition facts")}
        val lines=if(us) initial.map {Regex("(?<=\\d),(?=\\d{3}(?:\\D|$))").replace(it,"")} else initial
        val first=lines.indexOfFirst {label(it)!=null}.takeIf {it>=0} ?: lines.size
        val header=lines.take(first).joinToString(" ")
        val serving=Regex("(?:serving size|portion size|per serving|per portion)[^\\n]{0,40}?(\\d+(?:[.,]\\d+)?)\\s*(g|ml)\\b").find(header)
        val servingBasis=serving?.let {LabelBasis(it.groupValues[1].replace(',','.').toDoubleOrNull()?.takeIf {n->n.isFinite() && n>0 && n<=100000},if(it.groupValues[2]=="ml") FoodUnit.MILLILITRES else FoodUnit.GRAMS,"Serving")}
        val basisHits=Regex("(?:per\\s*|/\\s*)(\\d+(?:[.,]\\d+)?)\\s*(g|ml)\\b|per\\s+(serving|portion|pack)(?:\\s*\\(?\\s*(\\d+(?:[.,]\\d+)?)\\s*(g|ml)\\)?)?").findAll(header).toList()
        val bases=basisHits.map {m->
            if(m.groupValues[1].isNotBlank()) LabelBasis(m.groupValues[1].replace(',','.').toDoubleOrNull()?.takeIf {n->n.isFinite() && n>0 && n<=100000},if(m.groupValues[2]=="ml") FoodUnit.MILLILITRES else FoodUnit.GRAMS,"${m.groupValues[1]} ${m.groupValues[2]}")
            else if(m.groupValues[4].isNotBlank()) LabelBasis(m.groupValues[4].replace(',','.').toDoubleOrNull()?.takeIf {n->n.isFinite() && n>0 && n<=100000},if(m.groupValues[5]=="ml") FoodUnit.MILLILITRES else FoodUnit.GRAMS,"${m.groupValues[3]} (${m.groupValues[4]} ${m.groupValues[5]})")
            else servingBasis ?: LabelBasis(null,title=m.groupValues[3].replaceFirstChar {it.uppercase()})
        }.distinct().take(4).toMutableList()
        // US panels state a serving size, but do not repeat "per serving" above Calories.
        if(bases.isEmpty()) bases+=servingBasis ?: LabelBasis(null)
        val result=mutableMapOf<Nutrient,List<LabelObservation>>();val conflicts=mutableSetOf<Nutrient>();val warnings=mutableListOf<String>()
        lines.forEachIndexed {index,line->
            val found=label(line) ?: return@forEachIndexed
            val (n,len)=found
            var tail=line.drop(len).trim().trimStart(':').trim()
            val declared=Regex("^\\(?\\s*(g|mg|ug|mcg|kj|kcal)\\s*\\)?(?=\\s|$)").find(tail)
            val defaultUnit=declared?.groupValues?.get(1) ?: n.unit
            if(declared!=null) tail=tail.drop(declared.value.length).trim()
            if(tail.isBlank() && index+1<lines.size && label(lines[index+1])==null) tail=lines[index+1]
            if(n==Nutrient.ENERGY) {
                for(next in (index+1)..minOf(index+2,lines.lastIndex)) {
                    val continuation=lines[next]
                    if(label(continuation)!=null || !continuation.matches(Regex("[0-9.,\\s/kjcal]+",RegexOption.IGNORE_CASE))) break
                    if(!tail.contains(continuation)) {
                        val a=tail.split('\t');val b=continuation.split('\t')
                        tail=if(a.size>1 && a.size==b.size) a.zip(b).joinToString("\t") {(x,y)->"$x $y"} else tail+" "+continuation
                    }
                }
            }
            val v=values(tail,n,defaultUnit)
            if(v.isNotEmpty() && n !in conflicts) {
                if(result.containsKey(n) && result[n]?.map {it.value to it.bounded}!=v.map {it.value to it.bounded}) {warnings+="Repeated ${n.label.lowercase()} rows disagree";result.remove(n);conflicts+=n}
                else result[n]=v
            }
        }
        val count=maxOf(bases.size,result.values.maxOfOrNull {it.size} ?: 1).coerceAtMost(4)
        if(count>bases.size && count>1) {
            bases.indices.forEach {i->bases[i]=LabelBasis(null,title="Column ${i+1}")}
            warnings+="Column headings are incomplete. Confirm the quantity for the chosen column."
        }
        while(bases.size<count) bases+=LabelBasis(null,title="Column ${bases.size+1}")
        val columns=(0 until count).map {i->LabelColumn(bases[i],result.mapNotNull {(n,vs)->vs.getOrNull(i)?.let {n to it}}.toMap())}
        if(columns.all {it.observations.isEmpty()}) warnings+="No nutrition values recognised. Try a tighter crop or edit the text."
        return LabelRead(columns,raw.take(18000),warnings.distinct())
    }
    fun warnings(values:Nutrients):List<String> = buildList {
        val mass=Nutrient.entries.filter {it.unit=="g" && it!=Nutrient.ALCOHOL}
        if(mass.any {(values[it] ?: 0.0)>100.5}) add("A value exceeds 100 g per 100 g. Check the column and units.")
        if(values[Nutrient.ENERGY]?.let {it>1000}==true) add("Energy exceeds 1,000 kcal per 100. Check kJ, kcal and serving size.")
        fun subset(child:Nutrient,parent:Nutrient) {if(values[child]!=null && values[parent]!=null && values.getValue(child)>values.getValue(parent)+.5) add("${child.label} exceeds ${parent.label.lowercase()}.")}
        subset(Nutrient.SUGAR,Nutrient.CARBS);subset(Nutrient.SATURATED,Nutrient.FAT)
        if(Nutrient.macros.all {it in values} && Nutrient.ENERGY in values) {
            val macro=values.getValue(Nutrient.FAT)*9+values.getValue(Nutrient.PROTEIN)*4+values.getValue(Nutrient.CARBS)*4
            if(abs(macro-values.getValue(Nutrient.ENERGY))>maxOf(60.0,macro*.25)) add("Energy and macros differ substantially. Check the source values.")
        }
    }
    fun differs(a:Double,b:Double)=abs(a-b)>maxOf(.000001,maxOf(abs(a),abs(b))*.05)
}
