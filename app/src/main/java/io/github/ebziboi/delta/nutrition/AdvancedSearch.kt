package io.github.ebziboi.delta.nutrition

import java.net.URI

/** What someone pasted into advanced search: a page to read, nutrition text to parse, or a product name to look up. */
internal sealed interface PastedInput {
    data object Empty:PastedInput
    data class Link(val url:String):PastedInput {val host:String get()=AdvancedSearch.host(url)}
    data class Text(val read:LabelRead):PastedInput {val count get()=read.columns.maxOfOrNull {it.observations.size} ?: 0}
    data class Name(val text:String):PastedInput
}

internal object AdvancedSearch {
    private val link=Regex("(?i)\\b(https?://[^\\s<>\"']+|www\\.[a-z0-9-]+(?:\\.[a-z0-9-]+)+[^\\s<>\"']*)")

    /** The first web link in pasted text (share sheets add titles around it), upgraded to https and stripped of trailing punctuation. */
    fun linkIn(text:String):String? {
        val raw=link.find(text.take(4000))?.value?.trimEnd('.',',',';',':','!','?',')',']','}','\'','"','’','”') ?: return null
        val https=when {
            raw.startsWith("https://",true) -> "https://"+raw.drop(8)
            raw.startsWith("http://",true) -> "https://"+raw.drop(7)
            else -> "https://$raw"
        }
        return https.takeIf(ProductPages::publicUrl)
    }

    fun host(url:String)=runCatching {URI(url).host.orEmpty().removePrefix("www.")}.getOrDefault("")

    /** A product page on Open Food Facts is better read from its database record than from the page. */
    fun openFoodFactsCode(url:String):String? = runCatching {
        val u=URI(url)
        if(!(u.host ?: "").lowercase().matches(Regex("(?:[a-z-]+\\.)?openfoodfacts\\.org"))) return null
        Regex("/(?:product|produit|produkt|producto|prodotto)/(\\d{8,14})(?:/|$)").find(u.path ?: "")?.groupValues?.get(1)?.takeIf {ProductPages.canonicalBarcode(it)!=null}
    }.getOrNull()

    fun classify(input:String):PastedInput {
        val text=input.trim()
        if(text.isEmpty()) return PastedInput.Empty
        linkIn(text)?.let {return PastedInput.Link(it)}
        val read=runCatching {LabelParser.parse(text.take(18000))}.getOrNull()
        if(read!=null && (read.columns.maxOfOrNull {it.observations.size} ?: 0)>=2) return PastedInput.Text(read)
        // A short line is a product to look up; anything longer is text that held no recognisable values.
        return if(text.length<=120 && text.lines().size<=2) PastedInput.Name(text.replace(Regex("\\s+")," ")) else PastedInput.Text(read ?: LabelParser.parse(""))
    }

    /** Enough of a table to trust without scrolling the page: energy plus at least two macros. */
    fun complete(read:LabelRead)=read.columns.any {c->Nutrient.ENERGY in c.observations && Nutrient.macros.count {it in c.observations}>=2}

    /** Ranks reads of the same page as it finishes loading. */
    fun score(read:LabelRead)=read.columns.maxOfOrNull {c->c.observations.size*10+(if(c.basis.amount!=null) 5 else 0)} ?: 0
}
