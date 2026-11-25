package io.github.ebziboi.delta.nutrition

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

internal data class ProductPage(val name:String,val brand:String,val url:String,val barcode:String?,val barcodeMatch:Boolean,val barcodeConflict:Boolean,val read:LabelRead)
internal object ProductPages {
    fun canonicalBarcode(code:String?)=code?.filter(Char::isDigit)?.takeIf {it.length in listOf(8,12,13,14)}?.padStart(14,'0')
    fun publicUrl(raw:String):Boolean=runCatching {
        val u=URI(raw);val h=u.host?.lowercase() ?: return false
        u.scheme=="https" && u.rawUserInfo==null && (u.port==-1 || u.port==443) && h.contains('.') &&
            !h.endsWith(".local") && !h.endsWith(".localhost") && !h.matches(Regex("[0-9.]+")) && !h.contains(':') && raw.length<=2048
    }.getOrDefault(false)
    fun searchUrl(query:String)="https://www.google.com/search?"+"q="+URLEncoder.encode(query.trim().take(200)+" nutrition","UTF-8")+"&gl=gb"
    fun parse(raw:String,expectedBarcode:String?=null):ProductPage {
        require(raw.length<=200000) {"The page is too large to import"}
        val root=JSONObject(raw);val url=root.getString("url");require(publicUrl(url)) {"Open a public HTTPS product page"}
        val host=URI(url).host
        require(!host.matches(Regex("(?:www\\.)?google\\.[a-z.]+"))) {"Open a product result before importing"}
        val title=root.optString("heading").ifBlank {root.optString("title")}.take(250)
        val products=mutableListOf<JSONObject>()
        fun visit(value:Any?,depth:Int) {
            if(depth>8 || products.size>=40) return
            when(value) {
                is JSONObject->{
                    val type=value.opt("@type")?.toString().orEmpty()
                    // Shops describe a Product; recipe and restaurant sites a Recipe or MenuItem with the same nutrition block.
                    if(Regex("\\b(?:Product|Recipe|MenuItem)\\b").containsMatchIn(type)) products+=value
                    value.opt("@graph")?.let {visit(it,depth+1)}
                    value.opt("mainEntity")?.let {visit(it,depth+1)}
                }
                is JSONArray->(0 until minOf(value.length(),50)).forEach {visit(value.opt(it),depth+1)}
            }
        }
        root.optJSONArray("structured")?.let {a->(0 until a.length()).forEach {i->runCatching {org.json.JSONTokener(a.getString(i)).nextValue()}.getOrNull()?.let {visit(it,0)}}}
        fun codes(p:JSONObject)=listOf("gtin","gtin8","gtin12","gtin13","gtin14").mapNotNull {key->p.optString(key).takeIf {it.matches(Regex("\\d{8}|\\d{12,14}"))}}.distinct()
        val expected=canonicalBarcode(expectedBarcode)
        val titleWords=title.lowercase().split(Regex("\\W+")).filter {it.length>2}
        fun titleScore(p:JSONObject)=titleWords.count {p.optString("name").lowercase().contains(it)}
        // A matching barcode on a related-product card must not confirm the main product's table.
        val product=if(products.size==1) products.single() else products.maxByOrNull(::titleScore)?.takeIf {titleScore(it)>0}
        val barcode=product?.let(::codes)?.firstOrNull()
        val match=expected!=null && product?.let(::codes)?.any {canonicalBarcode(it)==expected}==true
        val conflict=expected!=null && barcode!=null && !match
        val name=product?.optString("name")?.takeIf {it.isNotBlank()} ?: title
        val brand=product?.opt("brand").let {if(it is JSONObject) it.optString("name") else (it as? String).orEmpty()}
        val reads=mutableListOf<LabelRead>()
        root.optJSONArray("tables")?.let {a->(0 until minOf(a.length(),25)).forEach {i->
            val table=a.getJSONArray(i);val text=(0 until minOf(table.length(),100)).joinToString("\n") {k->val row=table.getJSONArray(k);(0 until minOf(row.length(),8)).joinToString("\t") {row.optString(it)}}
            reads+=LabelParser.parse(text)
        }}
        val body=root.optString("text").take(60000)
        val marker=Regex("nutrition(?:al)?(?:\\s+(?:information|data|facts))?",RegexOption.IGNORE_CASE).find(body)
        val tail=if(marker!=null) body.substring(marker.range.first).take(10000) else body.take(18000)
        val end=Regex("(?im)^\\s*(ingredients|storage|customer reviews|related products|you may also like)\\b").find(tail)
        val focused=end?.let {tail.take(it.range.first)} ?: tail
        reads+=LabelParser.parse(focused)
        product?.optJSONObject("nutrition")?.let {n->
            // Schema.org states values per serving; a serving weight ("1 bowl (250 g)") makes them convertible.
            val serving=n.optString("servingSize")
            val weight=Regex("(\\d+(?:[.,]\\d+)?)\\s*(g|ml)\\b",RegexOption.IGNORE_CASE).find(serving)
            val basis=when {
                Regex("^\\s*100\\s*(g|ml)\\s*$",RegexOption.IGNORE_CASE).matches(serving) -> "Per $serving"
                weight!=null -> "Per serving (${weight.groupValues[1]} ${weight.groupValues[2].lowercase()})"
                else -> "Per serving"
            }
            val rows=listOf("calories" to "Energy","fatContent" to "Fat","saturatedFatContent" to "Saturated fat","transFatContent" to "Trans fat",
                "unsaturatedFatContent" to "Unsaturated fat","carbohydrateContent" to "Carbohydrate","sugarContent" to "Sugars","fiberContent" to "Fibre",
                "proteinContent" to "Protein","sodiumContent" to "Sodium","cholesterolContent" to "Cholesterol")
            // Bare numbers are grams (calories in kcal), as the schema defines.
            val text="$basis\n"+rows.mapNotNull {(key,label)->n.opt(key)?.toString()?.trim()?.takeIf {it.isNotBlank() && it!="null"}?.let {v->
                "$label\t"+if(v.matches(Regex("[0-9.,]+"))) v+(if(key=="calories") " kcal" else if(key in listOf("sodiumContent","cholesterolContent")) " mg" else " g") else v}}.joinToString("\n")
            reads+=LabelParser.parse(text)
        }
        val read=reads.maxByOrNull {r->r.columns.maxOfOrNull {it.observations.size*10+if(it.basis.amount!=null) 5 else 0} ?: 0} ?: LabelParser.parse("")
        return ProductPage(name.take(250),brand.take(250),url,barcode,match,conflict,read)
    }
    // Read the displayed document; no JavaScript bridge, credentials, hidden app state or cross-origin requests.
    // Collapsed accordions (hidden="until-found", content-visibility) report empty innerText, so text falls back to textContent.
    val extractScript="""(() => {const tx=e=>((e.innerText||'').trim()||(e.textContent||'').replace(/\s+/g,' ').trim());
      return JSON.stringify({url:location.href,title:document.title,heading:tx(document.querySelector('h1')||document.createElement('h1')),
      structured:Array.from(document.querySelectorAll('script[type="application/ld+json"]')).slice(0,8).map(s=>s.textContent.slice(0,12000)),
      tables:Array.from(document.querySelectorAll('table')).filter(t=>/protein|carbohydrate|saturat|kcal/i.test(tx(t))).slice(0,10).map(t=>Array.from(t.rows).slice(0,75).map(r=>Array.from(r.cells).slice(0,6).map(c=>tx(c).slice(0,250)))),
      text:(document.querySelector('main')||document.body).innerText.slice(0,50000)})})()"""
    val firstResultScript="""(() => {for(const a of document.querySelectorAll('a')) {if(!a.querySelector('h3')) continue;try {let u=new URL(a.href);if(u.hostname.endsWith('google.com')&&u.pathname==='/url')u=new URL(u.searchParams.get('q')||u.searchParams.get('url'));if(u.protocol==='https:'&&!/(^|\.)google\.[a-z.]+$/.test(u.hostname)) return u.href;}catch(e){}}return '';})()"""
}
