package io.github.ebziboi.delta.nutrition

import java.util.Locale

/**
 * Bundled UK fast-food menu items, so popular chain meals can be logged instantly and offline.
 *
 * Values are per item as served, taken from the chains' published UK nutrition guides and rounded.
 * Chains change recipes and portion sizes, so every item is labelled as a menu-guide value, links to
 * the chain's own guide, and can be saved as an editable custom food. Columns:
 * name | serving label | serving size (g, or ml with an "ml" suffix) | kcal | fat | saturates | carbohydrate | sugars | fibre | protein | salt
 */
internal data class FastFoodChain(val id:String,val label:String,val guide:String,val items:List<Food>)

internal object FastFood {
    private fun chain(id:String,label:String,guide:String,table:String):FastFoodChain {
        val items=table.trimIndent().lines().filter {it.isNotBlank()}.map {line->
            val c=line.split("|").map {it.trim()}
            require(c.size==11) {"Malformed menu row: $line"}
            val volume=c[2].endsWith("ml");val size=c[2].removeSuffix("ml").toDouble()
            val per=listOf(Nutrient.ENERGY,Nutrient.FAT,Nutrient.SATURATED,Nutrient.CARBS,Nutrient.SUGAR,Nutrient.FIBRE,Nutrient.PROTEIN,Nutrient.SALT)
                .mapIndexed {i,n->n to c[3+i].toDouble()*100/size}.toMap()
            Food("menu:$id:"+c[0].lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"),"-").trim('-'),c[0],label,per,
                if(volume) FoodUnit.MILLILITRES else FoodUnit.GRAMS,serving=size,servingLabel=c[1],source=FoodSource.RESTAURANT,sourceUrl=guide)
        }
        return FastFoodChain(id,label,guide,items)
    }

    val chains:List<FastFoodChain> by lazy {listOf(
        chain("mcdonalds","McDonald’s","https://www.mcdonalds.com/gb/en-gb/good-to-know/nutrition-calculator.html","""
            Big Mac|1 burger|215|508|25|10|43|9|3.5|27|2.3
            Quarter Pounder with Cheese|1 burger|199|518|26|13|37|8|2.5|31|2.3
            Double Cheeseburger|1 burger|165|445|21|10|34|7|2|27|2.2
            Cheeseburger|1 burger|116|298|12|6|31|7|1.6|15|1.5
            Hamburger|1 burger|101|250|8|3.4|30|6|1.6|13|1
            McChicken Sandwich|1 sandwich|164|388|17|2|39|4|2.6|17|1.3
            Mayo Chicken|1 burger|122|319|14|1.6|35|3.5|2|12|1.1
            Filet-O-Fish|1 burger|140|329|14|2.6|35|4|1.5|15|1.4
            Chicken McNuggets (6)|6 pieces|108|259|15|1.5|15|0.4|1.4|16|0.6
            Chicken McNuggets (9)|9 pieces|162|389|23|2.3|22|0.6|2.1|24|0.9
            Fries (small)|Small|80|230|11|1|29|0.3|2.7|2.4|0.4
            Fries (medium)|Medium|114|337|17|1.5|42|0.4|3.9|3.4|0.6
            Fries (large)|Large|150|444|22|2|55|0.5|5.1|4.4|0.7
            Egg McMuffin|1 muffin|138|297|13|6|28|2|2|18|1.6
            Sausage & Egg McMuffin|1 muffin|168|427|25|10|27|2|2|23|1.9
            Hash Brown|1 hash brown|55|140|8.6|0.8|14|0.2|1.6|1.2|0.5
            McFlurry Oreo|Regular|186|339|11|7|52|42|0.6|7.4|0.4
            Coca-Cola (medium)|Medium · 400 ml|400ml|170|0|0|42|42|0|0|0
            Coca-Cola Zero Sugar (medium)|Medium · 400 ml|400ml|1|0|0|0|0|0|0|0.02
        """),
        chain("kfc","KFC","https://www.kfc.co.uk/","""
            Original Recipe Chicken|1 piece|110|250|15|3|6|0|0.5|23|1.5
            Zinger Burger|1 burger|205|450|20|2.7|41|4|3|26|2.3
            Fillet Burger|1 burger|215|470|19|2.6|46|4|3|29|2.4
            Zinger Tower Burger|1 burger|250|610|28|5|57|6|3.5|30|3
            Popcorn Chicken (regular)|Regular|110|285|16|2|18|0.3|1|17|1.6
            Hot Wings (3)|3 wings|95|240|15|2.6|8|0|0.6|18|1.4
            Fries (regular)|Regular|110|250|12|1|31|0.4|3|3|0.6
            Gravy (regular)|Regular|150|75|2.4|0.4|11|0.4|0.6|1.6|1.3
            Beans (regular)|Regular|150|110|0.6|0.1|18|7|5|6|0.9
        """),
        chain("burger-king","Burger King","https://www.burgerking.co.uk/","""
            Whopper|1 burger|270|627|35|10|49|11|3|28|2.4
            Whopper with Cheese|1 burger|295|716|42|15|50|11|3|33|3
            Whopper Jr|1 burger|150|333|17|5|30|7|2|14|1.3
            Chicken Royale|1 burger|215|573|30|4|51|4|3|25|2.2
            Bacon Double Cheeseburger|1 burger|175|475|25|12|30|7|1.8|30|2.6
            Chicken Nuggets (6)|6 pieces|100|258|15|2|17|0.5|1.2|14|0.9
            Fries (regular)|Regular|116|284|14|1.4|35|0.5|3.4|3.4|0.7
        """),
        chain("subway","Subway","https://www.subway.com/en-GB/MenuNutrition/Menu","""
            Sweet Onion Chicken Teriyaki (6-inch)|6-inch sub|240|330|4.5|1|51|17|4|26|2
            Italian B.M.T. (6-inch)|6-inch sub|225|400|18|7|41|6|3|20|2.6
            Meatball Marinara (6-inch)|6-inch sub|280|450|17|7|54|10|5|21|2.4
            Turkey Breast (6-inch)|6-inch sub|220|270|3.5|1|41|6|3|18|1.8
            Tuna (6-inch)|6-inch sub|225|470|25|4|40|5|3|20|1.6
            Chicken Breast (6-inch)|6-inch sub|230|300|4.5|1|41|6|3|24|1.6
            Veggie Delite (6-inch)|6-inch sub|170|210|2.5|0.5|40|6|4|8|0.9
        """),
        chain("greggs","Greggs","https://www.greggs.com/menu","""
            Sausage Roll|1 roll|100|327|22|11|22|0.8|1.4|9.4|1.5
            Vegan Sausage Roll|1 roll|100|312|19|9|25|1|3.5|12|1.4
            Steak Bake|1 bake|145|408|25|13|32|1.4|2|14|1.3
            Chicken Bake|1 bake|150|437|25|12|37|1.5|2|15|1.4
            Sausage, Bean & Cheese Melt|1 melt|155|444|27|14|35|3|3|13|1.6
            Glazed Ring Doughnut|1 doughnut|60|191|9|4.5|24|10|1|3|0.3
        """),
        chain("nandos","Nando’s","https://www.nandos.co.uk/food/menu","""
            1/4 Chicken (breast)|Quarter chicken|200|300|11|3|1|1|0|50|1.6
            1/2 Chicken|Half chicken|380|650|36|10|1|1|0|80|2.5
            Peri-Peri Chips (regular)|Regular|160|337|15|1.5|45|0.5|4|4.5|0.6
            Spicy Rice (regular)|Regular|220|253|5|1|47|1|1.5|5|1.2
            Garlic Bread (regular)|Regular|100|330|16|8|39|2|2|7|1.2
        """),
        chain("dominos","Domino’s","https://www.dominos.co.uk/","""
            Pepperoni Passion (medium, classic crust)|1 slice|85|210|9|4|20|1.5|1|10|1
            Margherita (medium, classic crust)|1 slice|75|170|6|3|21|1.5|1|8|0.7
            Garlic & Herb Dip|1 pot|28|170|18|1.6|1|1|0|0.2|0.4
        """),
        chain("five-guys","Five Guys","https://fiveguys.co.uk/","""
            Hamburger|1 burger|265|840|43|19.5|39|8|2|40|1.1
            Cheeseburger|1 burger|300|980|55|26|40|9|2|47|2
            Little Hamburger|1 burger|175|540|26|11.5|39|8|2|23|0.9
            Fries (little)|Little|227|526|23|4|72|2.8|8|7.4|0.5
            Fries (regular)|Regular|411|953|41|7.4|131|5|15|13|1
        """),
        chain("starbucks","Starbucks","https://www.starbucks.co.uk/menu","""
            Caffè Latte (tall, semi-skimmed)|Tall · 354 ml|354ml|130|4.5|2.9|13|12|0|9|0.3
            Caffè Latte (grande, semi-skimmed)|Grande · 473 ml|473ml|175|6.2|3.9|17|16|0|12|0.4
            Cappuccino (grande, semi-skimmed)|Grande · 473 ml|473ml|120|4.3|2.7|12|11|0|8.5|0.3
            Caramel Frappuccino (grande)|Grande · 473 ml|473ml|360|14|9|54|52|0|5|0.4
        """),
        chain("costa","Costa","https://www.costa.co.uk/menu","""
            Latte (medio, semi-skimmed)|Medio|360ml|144|5.2|3.3|14|14|0|10|0.3
            Cappuccino (medio, semi-skimmed)|Medio|300ml|105|3.8|2.4|10|10|0|7.3|0.2
            Flat White (primo, semi-skimmed)|Primo|200ml|90|3.4|2.1|8.6|8.6|0|6.3|0.2
        """),
    )}

    val items:List<Food> get()=chains.flatMap {it.items}

    /** Every word of the query must start a word in the item or chain name ("big mac", "mcd fries"). */
    fun search(query:String,limit:Int=30):List<Food> {
        val words=normalise(query).split(' ').filter {it.isNotBlank()}
        if(words.isEmpty()) return emptyList()
        return items.mapNotNull {food->
            val hay=normalise(food.name+" "+food.brand).split(' ')
            if(words.all {w->hay.any {it.startsWith(w)}}) food to hay.indexOfFirst {it.startsWith(words.first())} else null
        }.sortedBy {it.second}.take(limit).map {it.first}
    }
    internal fun normalise(text:String)=text.lowercase(Locale.ROOT).replace("’","").replace("'","").replace(Regex("[^a-z0-9]+")," ").trim()
}
