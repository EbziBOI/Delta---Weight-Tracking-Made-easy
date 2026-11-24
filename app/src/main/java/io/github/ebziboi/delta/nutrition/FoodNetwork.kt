package io.github.ebziboi.delta.nutrition

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

internal object NutritionHttp {
    suspend fun request(url: String, body: JSONObject? = null, headers: Map<String,String> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
        ensureActive()
        val connection=URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout=15000; connection.readTimeout=45000; connection.instanceFollowRedirects=false
        connection.setRequestProperty("Accept","application/json")
        connection.setRequestProperty("User-Agent","Delta/0.2 (https://github.com/ebziboi/Delta---Weight-Tracking-Made-easy)")
        headers.forEach { (k,v) -> connection.setRequestProperty(k,v) }
        try {
            if(body!=null) {
                connection.requestMethod="POST"; connection.doOutput=true
                connection.setRequestProperty("Content-Type","application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status=connection.responseCode
            if(status !in 200..299) throw FoodNetworkException(when(status) {
                401,403 -> if(body==null) "The food service refused this request. Please try again later." else "Access was refused. Check your API key, model and provider permissions."
                404 -> "No matching product or model was found."
                429 -> "The service limit was reached. Please try again later."
                in 500..599 -> "The service is temporarily unavailable. Please try again."
                else -> "The service could not complete this request ($status)."
            })
            // Bound remote responses and do not expose response bodies, images or credentials in errors.
            val bytes=connection.inputStream.use { it.readBytesBounded(4*1024*1024) }
            ensureActive(); JSONObject(bytes.toString(Charsets.UTF_8))
        } catch(e: FoodNetworkException) { throw e }
        catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(_: java.io.IOException) { throw FoodNetworkException("Could not connect. Check your connection and try again.") }
        finally { connection.disconnect() }
    }
}
internal fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
    while(true) { val n=read(buffer);if(n<0) break;require(out.size()+n<=limit) { "The response is too large" };out.write(buffer,0,n) }
    return out.toByteArray()
}
class FoodNetworkException(message: String): Exception(message)

class OpenFoodFacts {
    private val cache=mutableMapOf<String,List<Food>>()
    private var lastSearch=0L
    private val lock=kotlinx.coroutines.sync.Mutex()
    private val fields="code,product_name,product_name_en,generic_name,brands,nutriments,serving_size,serving_quantity,serving_quantity_unit,product_quantity,product_quantity_unit,quantity,nutrition_data_per,no_nutrition_data,stores_tags"
    private val searchFields="code,product_name,product_name_en,generic_name,brands,nutriments,quantity,no_nutrition_data,countries_tags"
    suspend fun search(query: String,store:String?=null): List<Food> {
        val q=query.trim().take(120);require(q.length>=2) { "Enter at least two characters" }
        require(store==null || FoodStores.uk.any {it.id==store})
        if(searchTerms(q).length<2) return emptyList()
        val key=q.lowercase(Locale.ROOT)+"|"+(store ?: "all")
        lock.lock()
        try {
            cache[key]?.let { return it }
            // Space requests out politely: a quick second search waits briefly instead of failing.
            val wait=minSpacing-(android.os.SystemClock.elapsedRealtime()-lastSearch)
            if(lastSearch!=0L && wait>0) kotlinx.coroutines.delay(wait)
            lastSearch=android.os.SystemClock.elapsedRealtime()
            val response=NutritionHttp.request(searchUrl(q,store,searchFields))
            val hits=response.optJSONArray("hits") ?: return emptyList()
            val all=(0 until hits.length()).mapNotNull { i -> hits.getJSONObject(i).let { h -> parse(h)?.let { it to h.ukProduct() } } }
            // UK products first, keeping the service's relevance order within each group.
            return all.sortedByDescending { it.second }.map { it.first }.distinctBy { it.id }.take(30).also { cache[key]=it }
        } finally { lock.unlock() }
    }
    /** The full product record (serving size, pack and stores), which search results leave out. */
    suspend fun details(food: Food): Food {
        val code=food.barcode?.takeIf { food.source==FoodSource.OPEN_FOOD_FACTS } ?: return food
        return runCatching { barcode(code) }.getOrNull()?.takeUnless { it.missingNutrition && !food.missingNutrition } ?: food
    }
    suspend fun barcode(code: String): Food? {
        require(code.matches(Regex("[0-9]{8}|[0-9]{12,14}"))) { "Enter an 8, 12, 13 or 14 digit barcode" }
        val response=NutritionHttp.request("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=$fields")
        return response.optJSONObject("product")?.let(::parse)
    }
    companion object {
        private const val minSpacing=1500L
        /** Characters that the search service treats as query syntax; typed ones are read as spaces. */
        internal fun searchTerms(query:String)=query.replace(Regex("[\\\\:\"()\\[\\]{}^~*?/+!&|<>=-]")," ").replace(Regex("\\s+")," ").trim()
        internal fun searchUrl(query:String,store:String?,fields:String="code,product_name,nutriments"):String {
            val terms=searchTerms(query)
            val filter=store?.let { id -> FoodStores.uk.firstOrNull { it.id==id }?.let { " stores:\"${it.searchName}\" countries_tags:\"en:united-kingdom\"" } } ?: ""
            return "https://search.openfoodfacts.org/search?q=${URLEncoder.encode(terms+filter,"UTF-8")}&page_size=40&langs=en&fields=${URLEncoder.encode(fields,"UTF-8")}"
        }
        private fun JSONObject.ukProduct()=optJSONArray("countries_tags")?.let { a -> (0 until a.length()).any { a.optString(it)=="en:united-kingdom" } } ?: false

        fun parse(p: JSONObject): Food? = runCatching { parseProduct(p) }.getOrNull()
        private fun parseProduct(p: JSONObject): Food? {
            val name=sequenceOf("product_name_en","product_name","generic_name").map { p.optString(it).trim() }.firstOrNull { it.isNotBlank() } ?: return null
            val code=p.optString("code"); if(!code.matches(Regex("[0-9]{8}|[0-9]{12,14}"))) return null
            val n=if(p.optString("no_nutrition_data")=="on") JSONObject() else p.optJSONObject("nutriments") ?: JSONObject()
            val quantity=p.optString("quantity").lowercase(Locale.ROOT)
            val volume=p.optString("nutrition_data_per").lowercase()=="100ml" ||
                p.optString("product_quantity_unit").lowercase() in listOf("ml","cl","l") ||
                (quantity.contains(Regex("\\d\\s*(ml|cl|l)\\b")) && !quantity.contains(Regex("\\d\\s*(g|kg)\\b"))) ||
                (quantity.isBlank() && (p.optString("serving_quantity_unit").lowercase()=="ml" || p.optString("serving_size").contains(Regex("\\d\\s*ml\\b",RegexOption.IGNORE_CASE))))
            val unit=if(volume) FoodUnit.MILLILITRES else FoodUnit.GRAMS
            val nutrition=Nutrient.entries.mapNotNull { nutrient ->
                if(nutrient==Nutrient.ALCOHOL && n.optString("alcohol_unit")!="g") return@mapNotNull null
                val key=if(nutrient==Nutrient.FOLATE && !n.has("folates_100g")) "vitamin-b9" else nutrient.off
                val raw=n.optDouble("${key}_100g",Double.NaN)
                val value=if(nutrient==Nutrient.ENERGY) {
                    if(raw.isFinite()) raw else n.optDouble("energy_100g",Double.NaN)/4.184
                } else raw * when(nutrient.unit) { "mg" -> 1000.0; "µg" -> 1000000.0; else -> 1.0 }
                value.takeIf { it.validAmount() }?.let { nutrient to it }
            }.toMap()
            fun measure(text: String): Double? {
                val pattern=if(volume) Regex("([0-9]+(?:[.,][0-9]+)?)\\s*(ml|cl|l)\\b",RegexOption.IGNORE_CASE)
                    else Regex("([0-9]+(?:[.,][0-9]+)?)\\s*(kg|g)\\b",RegexOption.IGNORE_CASE)
                val m=pattern.find(text) ?: return null
                return m.groupValues[1].replace(',','.').toDoubleOrNull()?.times(when(m.groupValues[2].lowercase()) { "kg","l" -> 1000.0;"cl" -> 10.0;else -> 1.0 })?.takeIf { it>0 && it<=100000 }
            }
            val servingText=p.optString("serving_size")
            // Never turn an unspecified 'portion' or a volume into grams.
            val serving=measure(servingText) ?: p.positive("serving_quantity")?.takeIf {p.optString("serving_quantity_unit").lowercase() == if(volume) "ml" else "g"}
            val canonicalPack=p.positive("product_quantity")?.takeIf {
                it<=100000 && p.optString("product_quantity_unit").lowercase() == if(volume) "ml" else "g"
            }
            val multiple=Regex("^\\s*([0-9]+)\\s*[x×]\\s*(.+)$").find(quantity)
            val textPack=if(multiple!=null) measure(multiple.groupValues[2])?.times(multiple.groupValues[1].toDouble()) else measure(quantity)
            val pack=canonicalPack ?: textPack?.takeIf {it>0 && it<=100000}
            // Search results list brands as an array; product records as comma-separated text.
            val brands=p.optJSONArray("brands")?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotBlank() }.joinToString(", ") } ?: p.optString("brands")
            return Food("off:$code",name.take(250),brands.take(250),nutrition,unit,
                serving,servingText.ifBlank { "Serving" }.take(100),pack,FoodSource.OPEN_FOOD_FACTS,code,stores=p.optJSONArray("stores_tags")?.let {a->(0 until a.length()).map {a.getString(it).take(100)}.take(50)} ?: emptyList())
        }
    }
}
