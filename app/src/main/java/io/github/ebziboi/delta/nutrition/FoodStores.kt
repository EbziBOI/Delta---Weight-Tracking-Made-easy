package io.github.ebziboi.delta.nutrition

import android.content.Context
import java.util.Locale

internal data class FoodStore(val id:String,val label:String,val queryTag:String=id,val searchName:String=label)
internal object FoodStores {
    // Starting order follows leading GB grocers; successful diary logs personalize it locally.
    val uk=listOf(FoodStore("tesco","Tesco"),FoodStore("sainsburys","Sainsbury’s","sainsbury-s","sainsbury's"),FoodStore("asda","Asda"),
        FoodStore("aldi","Aldi"),FoodStore("lidl","Lidl"),FoodStore("morrisons","Morrisons"),FoodStore("co-op","Co-op"),
        FoodStore("waitrose","Waitrose"),FoodStore("iceland","Iceland"),FoodStore("ocado","Ocado"),FoodStore("marks-spencer","M&S",searchName="marks & spencer"))
    fun canonical(raw:String):String? {
        val value=raw.lowercase(Locale.ROOT).removePrefix("en:").replace("’","").replace("'","").replace("&","-").replace(" ","-")
        return uk.firstOrNull {it.id==value || (it.id=="sainsburys" && value=="sainsbury-s") ||
            (it.id=="marks-spencer" && value in listOf("marks-and-spencer","m-s")) || (it.id=="co-op" && value in listOf("co-operative","coop"))}?.id
    }
    fun ranked(counts:Map<String,Int>)=uk.withIndex().sortedWith(compareByDescending<IndexedValue<FoodStore>> {counts[it.value.id] ?: 0}.thenBy {it.index}).map {it.value}
}
internal class StorePreferences(context:Context) {
    private val prefs=context.applicationContext.getSharedPreferences("food-stores",0)
    fun recordChoice(id:String) {if(FoodStores.uk.any {it.id==id}) prefs.edit().putInt(id,(prefs.getInt(id,0)+2).coerceAtMost(1000000)).apply()}
    fun ranked()=FoodStores.ranked(FoodStores.uk.associate {it.id to prefs.getInt(it.id,0)})
    fun record(foods:List<Food>) {
        val counts=mutableMapOf<String,Int>()
        foods.forEach {food->food.stores.mapNotNull(FoodStores::canonical).distinct().forEach {counts[it]=(counts[it] ?: 0)+1}}
        prefs.edit().also {edit->counts.forEach {(id,n)->edit.putInt(id,(prefs.getInt(id,0)+n).coerceAtMost(1000000))}}.apply()
    }
}
