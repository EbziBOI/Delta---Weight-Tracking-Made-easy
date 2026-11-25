package io.github.ebziboi.delta.nutrition

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

internal data class LabelBasis(val amount:Double?,val unit:FoodUnit=FoodUnit.GRAMS,val title:String="Choose basis")
internal data class LabelObservation(val value:Double,val bounded:Boolean=false,val raw:String="",val alternative:Double?=null)
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

/**
 * Only transcribes supplied numbers. Missing, bounded and ambiguous values are never filled as zeros.
 * Rows are matched in English and the main European label languages, tolerate small OCR misspellings,
 * keep their column positions when cells are tab-separated, and are checked for internal consistency.
 */
/** Salt and sodium are the same measurement (salt = sodium × 2.5); fills whichever is missing. */
internal fun Nutrients.withSaltAndSodium():Nutrients {
    val salt=this[Nutrient.SALT];val sodium=this[Nutrient.SODIUM]
    return when {
        salt!=null && sodium==null -> this+(Nutrient.SODIUM to salt*400)
        sodium!=null && salt==null -> this+(Nutrient.SALT to sodium/400)
        else -> this
    }
}

internal object LabelParser {
    private val aliases=mapOf(
        Nutrient.ENERGY to listOf("energy","calories","calorie","energie","energia","brennwert","valeur energetique","valor energetico","valore energetico","energiewaarde","wartosc energetyczna","energeticka hodnota","energi"),
        Nutrient.FAT to listOf("total fat","fat","total lipid","matieres grasses","fett","grasas","grassi","vet","tluszcz","lipides","grasa total","gorduras totais","gordura","fedt"),
        Nutrient.SATURATED to listOf("of which saturates","of which saturated","saturated fat","saturated fatty acids","saturates","saturated",
            "dont acides gras satures","dont satures","acides gras satures","davon gesattigte fettsauren","gesattigte fettsauren","de las cuales saturadas","de los cuales saturados",
            "acidos grasos saturados","di cui acidi grassi saturi","acidi grassi saturi","waarvan verzadigde vetzuren","waarvan verzadigd","verzadigde vetzuren",
            "w tym kwasy tluszczowe nasycone","kwasy tluszczowe nasycone","dos quais saturados","heraf mattede fedtsyrer"),
        Nutrient.CARBS to listOf("total carbohydrate","total carbohydrates","carbohydrates","carbohydrate","carbs","glucides","kohlenhydrate","hidratos de carbono","carboidrati","koolhydraten","weglowodany","kulhydrat"),
        Nutrient.SUGAR to listOf("of which sugars","total sugars","sugars","sugar","dont sucres","sucres","davon zucker","zucker","de los cuales azucares","azucares","di cui zuccheri","zuccheri",
            "waarvan suikers","suikers","w tym cukry","cukry","dos quais acucares","acucares","heraf sukkerarter"),
        Nutrient.FIBRE to listOf("dietary fibre","dietary fiber","fibre","fiber","fibres alimentaires","fibres","ballaststoffe","fibra alimentaria","fibre alimentari","fibra","voedingsvezel","vezels","blonnik","kostfibre"),
        Nutrient.PROTEIN to listOf("protein","proteins","proteines","eiweiss","proteinas","proteine","eiwitten","eiwit","bialko"),
        Nutrient.SALT to listOf("salt","sel","salz","sale","zout","sol","sal"),Nutrient.SODIUM to listOf("sodium","sodio","natrium"),
        Nutrient.CHOLESTEROL to listOf("cholesterol"),Nutrient.TRANSFAT to listOf("trans fat"),Nutrient.MONOUNSATURATED to listOf("monounsaturated fat","mono-unsaturates","monounsaturates"),
        Nutrient.POLYUNSATURATED to listOf("polyunsaturated fat","polyunsaturates"),Nutrient.POTASSIUM to listOf("potassium"),Nutrient.CALCIUM to listOf("calcium"),
        Nutrient.IRON to listOf("iron"),Nutrient.MAGNESIUM to listOf("magnesium"),Nutrient.ZINC to listOf("zinc"),Nutrient.PHOSPHORUS to listOf("phosphorus"),
        Nutrient.SELENIUM to listOf("selenium"),Nutrient.IODINE to listOf("iodine"),Nutrient.COPPER to listOf("copper"),Nutrient.MANGANESE to listOf("manganese"),
        Nutrient.VIT_A to listOf("vitamin a"),Nutrient.VIT_C to listOf("vitamin c"),Nutrient.VIT_D to listOf("vitamin d"),Nutrient.VIT_E to listOf("vitamin e"),Nutrient.VIT_K to listOf("vitamin k"),
        Nutrient.B1 to listOf("thiamin","thiamine","vitamin b1"),Nutrient.B2 to listOf("riboflavin","vitamin b2"),Nutrient.B3 to listOf("niacin","vitamin b3"),
        Nutrient.B6 to listOf("vitamin b6"),Nutrient.B12 to listOf("vitamin b12"),Nutrient.FOLATE to listOf("folate","folacin"),Nutrient.FOLIC_ACID to listOf("folic acid"),
        Nutrient.B5 to listOf("pantothenic acid"),Nutrient.BIOTIN to listOf("biotin"),Nutrient.CAFFEINE to listOf("caffeine"),Nutrient.ALCOHOL to listOf("alcohol"))
    private fun fold(s:String)=java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"),"")
        .replace("ß","ss").replace('ł','l').replace('Ł','l').replace('ø','o')
    private val names=aliases.flatMap {(n,words)->words.map {fold(it) to n}}.sortedByDescending {it.first.length}
    private fun clean(s:String)=fold(s.lowercase(Locale.ROOT).replace('µ','u').replace('μ','u').replace('\u00a0',' ')).replace(Regex("[‐‑–—]"),"-")
        .replace(Regex(" +")," ").replace(Regex(" *\t *"),"\t").trim(' ').removePrefix("-").trim(' ')
        // A capital O read in place of a leading zero: "O.5 g".
        .replace(Regex("(?<![a-z0-9])o(?=[.,]\\d)"),"0")
    private fun distance(a:String,b:String)=editDistance(a,b)
    /** The nutrient a row starts with and how many characters its name takes, allowing an OCR typo in longer names. */
    private fun label(s:String):Pair<Nutrient,Int>? {
        val x=clean(s).substringBefore('\t')
        names.firstNotNullOfOrNull {(name,n)->if(x.startsWith(name) && (x.length==name.length || !x[name.length].isLetterOrDigit())) n to name.length else null}?.let {return it}
        val words=x.split(' ')
        return names.filter {it.first.length>=5}.mapNotNull {(name,n)->
            val count=name.count {it==' '}+1
            if(words.size<count) return@mapNotNull null
            val candidate=words.take(count).joinToString(" ").trimEnd(':','(',')')
            val d=distance(candidate,name)
            if(d in 1..(if(name.length>=9) 2 else 1) && candidate.firstOrNull()==name.first()) Triple(n,words.take(count).joinToString(" ").length,d*100-name.length) else null
        }.minByOrNull {it.third}?.let {it.first to it.second}
    }
    /** True when a recognised line starts with a nutrient row; used to find the table in a photo. */
    fun isNutrientRow(text:String)=label(text)!=null
    private val numbers=Regex("(?<![a-z0-9.,])([<>≤≥]\\s*|less than\\s+)?(\\d+(?:[.,]\\d+)?|[.,]\\d+)\\s*(kcal|calories|cal|kj|mcg|ug|mg|g|%)?(?![a-z0-9])",RegexOption.IGNORE_CASE)
    private fun energy(text:String):LabelObservation? {
        val x=clean(text);val hits=numbers.findAll(x).filter {it.groupValues[3]!="%"}.toList()
        fun value(m:MatchResult)=m.groupValues[2].replace(',','.').toDoubleOrNull()
        hits.firstOrNull {it.groupValues[3] in listOf("kcal","cal","calories")}?.let {m->return value(m)?.takeIf {it.validAmount()}?.let {LabelObservation(it,m.groupValues[1].isNotBlank(),text.take(160))}}
        // Unlabelled "1046 / 250" is a kJ / kcal pair.
        if(hits.size==2 && hits.all {it.groupValues[3].isBlank()}) {
            val a=value(hits[0]);val b=value(hits[1])
            if(a!=null && b!=null && b>0 && a/b in 3.9..4.45) return LabelObservation(b,hits[1].groupValues[1].isNotBlank(),text.take(160))
        }
        val hit=hits.firstOrNull() ?: return null
        val v=value(hit) ?: return null
        val kcal=when(hit.groupValues[3]) {"kj"->v/4.184;"","kcal","cal","calories"->v;else->return null}
        return kcal.takeIf {it.validAmount()}?.let {LabelObservation(it,hit.groupValues[1].isNotBlank(),text.take(160))}
    }
    private fun observation(text:String,n:Nutrient,defaultUnit:String=n.unit):LabelObservation? {
        if(n==Nutrient.ENERGY && defaultUnit!="kj") return energy(text)
        val hits=numbers.findAll(clean(text)).filter {it.groupValues[3]!="%"}.toList()
        val hit=hits.firstOrNull() ?: return null
        val digits=hit.groupValues[2]
        val value=digits.replace(',','.').toDoubleOrNull() ?: return null
        val unit=hit.groupValues[3].ifBlank {defaultUnit}.replace("µ","u")
        val factor=if(n==Nutrient.ENERGY) when(unit) {"kj"->1/4.184;"kcal","cal","calories"->1.0;else->return null}
            else {
                val grams=when(unit) {"g"->1.0;"mg"->.001;"ug","mcg"->.000001;else->return null}
                grams/when(n.unit) {"g"->1.0;"mg"->.001;"µg"->.000001;else->return null}
            }
        // "3.2g" is often recognised as "3.29": keep the reading, but remember the alternative for the consistency check.
        val alternative=if(hit.groupValues[3].isBlank() && n.unit=="g" && defaultUnit=="g" && digits.length>=2 && digits.endsWith('9'))
            digits.dropLast(1).trimEnd('.',',').replace(',','.').toDoubleOrNull()?.takeIf {it.validAmount()} else null
        return (value*factor).takeIf {it.validAmount()}?.let {LabelObservation(it,hit.groupValues[1].isNotBlank(),text.take(160),alternative)}
    }
    /** Values in a row: tab-separated cells keep their column (blank cells stay null); free text lists numbers in order. */
    private fun values(text:String,n:Nutrient,defaultUnit:String,pairs:Boolean=false):List<LabelObservation?> {
        if(text.contains('\t')) return text.split('\t').map {cell->if(cell.isBlank()) null else observation(cell,n,defaultUnit)}.dropLastWhile {it==null}
        val hits=numbers.findAll(text).filter {it.groupValues[3]!="%"}.toList()
        if(n==Nutrient.ENERGY) {
            if(pairs) return hits.chunked(2).filter {it.size==2}.mapNotNull {(_,kcal)->observation(kcal.value.replace(Regex("[a-z]+$"),"")+" kcal",n,"kcal")}
            val kcals=hits.filter {it.groupValues[3].lowercase() in listOf("kcal","calories","cal")}
            val kjs=hits.filter {it.groupValues[3].equals("kj",true)}
            val unlabelled=hits.filter {it.groupValues[3].isBlank()}
            // "1046 250 262 63": alternating kJ and kcal figures without units.
            if(kcals.isEmpty() && kjs.isEmpty() && defaultUnit!="kj" && unlabelled.size>=2 && unlabelled.size%2==0 && unlabelled.chunked(2).all {(a,b)->
                    val x=a.groupValues[2].replace(',','.').toDoubleOrNull();val y=b.groupValues[2].replace(',','.').toDoubleOrNull();x!=null && y!=null && y>0 && x/y in 3.9..4.45})
                return unlabelled.chunked(2).mapNotNull {(_,b)->observation(b.groupValues[2]+" kcal",n,"kcal")}
            val use=kcals.ifEmpty {kjs}.ifEmpty {hits}
            return use.mapNotNull {observation(it.value,n,defaultUnit)}
        }
        return hits.mapNotNull {observation(it.value,n,defaultUnit)}
    }
    fun parse(raw:String):LabelRead {
        require(raw.length<=200000) {"The label text is too long"}
        val initial=raw.lines().map(::clean).filter {it.isNotBlank()}
        val us=initial.take(8).any {it.contains("nutrition facts")}
        val spacedUnits=initial.any {Regex("\\d\\s+g\\b").containsMatchIn(it)}
        var lines=if(us) initial.map {Regex("(?<=\\d),(?=\\d{3}(?:\\D|$))").replace(it,"")} else initial
        // On labels that print "0.5 g", a separate "9" right after a number is the unit misread.
        if(spacedUnits) lines=lines.map {line->if(label(line)?.first?.unit=="g") line.replace(Regex("(\\d)\\s+9(?![\\d.,])"),"$1 g") else line}
        val first=lines.indexOfFirst {label(it)!=null}.takeIf {it>=0} ?: lines.size
        val header=lines.take(first).joinToString(" ")
        val serving=Regex("(?:serving size|portion size|per serving|per portion|portion|par portion|pro portion|por porcion|per porzione)[^\\n]{0,40}?(\\d+(?:[.,]\\d+)?)\\s*(g|ml)\\b").find(header)
        val servingBasis=serving?.let {LabelBasis(it.groupValues[1].replace(',','.').toDoubleOrNull()?.takeIf {n->n.isFinite() && n>0 && n<=100000},if(it.groupValues[2]=="ml") FoodUnit.MILLILITRES else FoodUnit.GRAMS,"Serving")}
        // "Per 100g", "per serving (30 g)", and any "per piece (12.5g)" / "per 1 biscuit (9 g)" heading.
        val basisHits=Regex("(?:per\\s*|pour\\s*|pro\\s*|por\\s*|je\\s*|/\\s*)(\\d+(?:[.,]\\d+)?)\\s*(g|ml)\\b|per\\s+(?:1\\s+|one\\s+|an?\\s+)?(serving|portion|pack|[a-z]{3,12}(?=\\s*\\(\\s*\\d))(?:\\s*\\(?\\s*(\\d+(?:[.,]\\d+)?)\\s*(g|ml)\\)?)?").findAll(header).toList()
        val bases=basisHits.map {m->
            if(m.groupValues[1].isNotBlank()) LabelBasis(m.groupValues[1].replace(',','.').toDoubleOrNull()?.takeIf {n->n.isFinite() && n>0 && n<=100000},if(m.groupValues[2]=="ml") FoodUnit.MILLILITRES else FoodUnit.GRAMS,"${m.groupValues[1]} ${m.groupValues[2]}")
            else if(m.groupValues[4].isNotBlank()) LabelBasis(m.groupValues[4].replace(',','.').toDoubleOrNull()?.takeIf {n->n.isFinite() && n>0 && n<=100000},if(m.groupValues[5]=="ml") FoodUnit.MILLILITRES else FoodUnit.GRAMS,"${m.groupValues[3]} (${m.groupValues[4]} ${m.groupValues[5]})")
            else servingBasis ?: LabelBasis(null,title=m.groupValues[3].replaceFirstChar {it.uppercase()})
        }.distinct().take(4).toMutableList()
        // US panels state a serving size, but do not repeat "per serving" above Calories.
        if(bases.isEmpty()) bases+=servingBasis ?: LabelBasis(null)
        val result=mutableMapOf<Nutrient,List<LabelObservation?>>();val conflicts=mutableSetOf<Nutrient>();val warnings=mutableListOf<String>()
        lines.forEachIndexed {index,line->
            val found=label(line) ?: return@forEachIndexed
            val (n,len)=found
            val tabbed=line.contains('\t')
            val labelCell=if(tabbed) line.substringBefore('\t') else line
            var tail=labelCell.drop(len).trim().trimStart(':').trim()
            val pairs=n==Nutrient.ENERGY && Regex("^\\(?\\s*kj\\s*/\\s*kcal\\s*\\)?(?=\\s|$)").containsMatchIn(tail)
            if(pairs) tail=tail.replace(Regex("^\\(?\\s*kj\\s*/\\s*kcal\\s*\\)?"),"").trim()
            val declared=if(pairs) null else Regex("^\\(?\\s*(g|mg|ug|mcg|kj|kcal)\\s*\\)?(?=\\s|$)").find(tail)
            val defaultUnit=declared?.groupValues?.get(1) ?: n.unit
            if(declared!=null) tail=tail.drop(declared.value.length).trim()
            if(tabbed) {
                // Positional cells: a value glued to the label fills the first empty column.
                val cells=line.split('\t').drop(1).toMutableList()
                if(tail.isNotBlank() && numbers.containsMatchIn(tail)) {if(cells.isNotEmpty() && cells[0].isBlank()) cells[0]=tail else cells.add(0,tail)}
                tail=cells.joinToString("\t")
            } else if(tail.isBlank() && index+1<lines.size && label(lines[index+1])==null) tail=lines[index+1]
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
            val v=values(tail,n,defaultUnit,pairs)
            if(v.any {it!=null} && n !in conflicts) {
                if(result.containsKey(n) && result[n]?.map {it?.value to it?.bounded}!=v.map {it?.value to it?.bounded}) {warnings+="Repeated ${n.label.lowercase()} rows disagree";result.remove(n);conflicts+=n}
                else result[n]=v
            }
        }
        // A tabbed header names each value column. Reference intake columns ("RI*", "GDA", "Adult RI": 2000 kcal, 70 g fat)
        // are not the food's values; they would otherwise read as one more column and hide the real quantities.
        val headerCells=lines.take(first).lastOrNull {it.contains('\t')}?.split('\t')?.map {it.trim()}
        if(headerCells!=null) {
            val cells=if(headerCells.firstOrNull()?.let {Regex("^(?:per|pour|pro|por|je)\\b").containsMatchIn(it)}==true) headerCells else headerCells.drop(1)
            val reference=cells.indices.filter {i->Regex("^%|^(?:adult\\s+)?(?:ri|gda|reference intakes?|daily values?)\\b").containsMatchIn(cells[i])}.toSet()
            if(reference.isNotEmpty() && reference.size<cells.size) result.keys.toList().forEach {n->result[n]=result.getValue(n).filterIndexed {i,_->i !in reference}}
        }
        // Drop positions no nutrient uses, such as a %RI column.
        val width=result.values.maxOfOrNull {it.size} ?: 0
        val used=(0 until width).filter {i->result.values.any {it.getOrNull(i)!=null}}
        val compact=result.mapValues {(_,v)->used.map {v.getOrNull(it)}}
        val count=maxOf(bases.size,compact.values.maxOfOrNull {it.size} ?: 1).coerceAtMost(4)
        if(count>bases.size && count>1) {
            bases.indices.forEach {i->bases[i]=LabelBasis(null,title="Column ${i+1}")}
            warnings+="Column headings are incomplete. Confirm the quantity for the chosen column."
        }
        while(bases.size<count) bases+=LabelBasis(null,title="Column ${bases.size+1}")
        val columns=(0 until count).map {i->
            val (checked,fixed)=reconcile(compact.mapNotNull {(n,vs)->vs.getOrNull(i)?.let {n to it}}.toMap(),bases[i].amount)
            if(fixed.isNotEmpty()) warnings+="Corrected a likely misread unit on ${fixed.joinToString {it.label.lowercase()}}. Check against the label."
            LabelColumn(bases[i],checked.mapValues {it.value.copy(alternative=null)})
        }
        if(columns.all {it.observations.isEmpty()}) warnings+="No nutrition values recognised. Try a tighter crop or edit the text."
        return LabelRead(columns,raw.take(18000),warnings.distinct())
    }
    /** How far a column's values break label arithmetic: parts above their totals, mass above 100 g per 100 g, energy far from its macros. */
    internal fun violation(v:Map<Nutrient,Double>,basis:Double?):Double {
        var score=0.0
        if(basis!=null && basis>0) Nutrient.entries.filter {it.unit=="g" && it!=Nutrient.ALCOHOL}.forEach {n->v[n]?.let {x->(x*100/basis-100).takeIf {it>0}?.let {score+=it}}}
        fun part(child:Nutrient,parent:Nutrient) {val c=v[child];val p=v[parent];if(c!=null && p!=null && c>p+.05) score+=(c-p)*5}
        part(Nutrient.SUGAR,Nutrient.CARBS);part(Nutrient.SATURATED,Nutrient.FAT)
        val e=v[Nutrient.ENERGY]
        if(e!=null && Nutrient.macros.all {it in v}) {
            val macro=v.getValue(Nutrient.FAT)*9+v.getValue(Nutrient.PROTEIN)*4+v.getValue(Nutrient.CARBS)*4+(v[Nutrient.FIBRE] ?: 0.0)*2
            val off=abs(macro-e)/maxOf(e,macro,20.0)
            if(off>.15) score+=off*40
        }
        return score
    }
    /** Tries the "g read as 9" alternatives and keeps the combination that makes the column consistent. */
    private fun reconcile(column:Map<Nutrient,LabelObservation>,basis:Double?):Pair<Map<Nutrient,LabelObservation>,List<Nutrient>> {
        val options=column.filterValues {it.alternative!=null && !it.bounded}.keys.take(5)
        if(options.isEmpty()) return column to emptyList()
        fun values(swap:Set<Nutrient>)=column.mapValues {(n,o)->if(n in swap) o.alternative!! else o.value}
        val current=violation(values(emptySet()),basis)
        if(current<=0) return column to emptyList()
        var best=emptySet<Nutrient>();var bestScore=current
        for(mask in 1 until (1 shl options.size)) {
            val swap=options.filterIndexed {i,_->mask and (1 shl i)!=0}.toSet()
            val score=violation(values(swap),basis)
            if(score<bestScore-.5 || (abs(score-bestScore)<=.5 && score<current && swap.size<best.size)) {best=swap;bestScore=score}
        }
        if(best.isEmpty()) return column to emptyList()
        return column.mapValues {(n,o)->if(n in best) o.copy(value=o.alternative!!,raw=o.raw+" (corrected)",alternative=null) else o} to best.toList()
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
