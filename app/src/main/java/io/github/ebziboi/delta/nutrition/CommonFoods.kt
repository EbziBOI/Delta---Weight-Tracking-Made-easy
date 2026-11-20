package io.github.ebziboi.delta.nutrition

import java.util.Locale

/**
 * Everyday foods and home-style dishes ("fried egg", "a bowl of pasta"), bundled so they can be logged
 * instantly and offline. Values are typical per 100 g (or 100 ml for drinks), rounded from UK and USDA
 * composition tables, with carbohydrate excluding fibre (the UK and EU label convention); home recipes and portions vary, so each one opens with a typical portion that can be
 * changed, and can be saved as an editable copy. Columns:
 * name | other names | serving label | serving size (g, or ml with "ml") | kcal | fat | saturates | carbohydrate | sugars | fibre | protein | salt
 */
internal data class CommonFoodGroup(val id:String,val label:String,val items:List<Food>)

internal object CommonFoods {
    private val aliases=mutableMapOf<String,String>()
    private fun group(id:String,label:String,table:String):CommonFoodGroup {
        val items=table.trimIndent().lines().filter {it.isNotBlank()}.map {line->
            val c=line.split("|").map {it.trim()}
            require(c.size==12) {"Malformed food row: $line"}
            val volume=c[3].endsWith("ml");val size=c[3].removeSuffix("ml").toDouble()
            val per=listOf(Nutrient.ENERGY,Nutrient.FAT,Nutrient.SATURATED,Nutrient.CARBS,Nutrient.SUGAR,Nutrient.FIBRE,Nutrient.PROTEIN,Nutrient.SALT)
                .mapIndexed {i,n->n to c[4+i].toDouble()}.toMap()
            val id="common:"+c[0].lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"),"-").trim('-')
            aliases[id]=c[1]
            Food(id,c[0],"",per,if(volume) FoodUnit.MILLILITRES else FoodUnit.GRAMS,serving=size,servingLabel=c[2],source=FoodSource.GENERIC)
        }
        return CommonFoodGroup(id,label,items)
    }

    val groups:List<CommonFoodGroup> by lazy {listOf(
        group("breakfast","Breakfast & eggs","""
            Fried egg|eggs fried, sunny side up|1 large egg|60|196|15|4.3|0.8|0.4|0|13.6|0.5
            Boiled egg|hard boiled egg, soft boiled egg, eggs|1 large egg|50|155|10.6|3.3|1.1|1.1|0|12.6|0.3
            Scrambled eggs|scrambled egg|2 eggs|120|149|11|3.3|1.6|1.4|0|10|0.4
            Poached egg|eggs poached|1 large egg|50|143|9.5|3.1|0.7|0.4|0|12.5|0.3
            Omelette|omelet, plain omelette|2-egg omelette|120|154|11.7|3.2|0.6|0.4|0|10.6|0.4
            Cheese omelette|cheese omelet|2-egg omelette|150|200|15.5|7|0.8|0.5|0|14|0.8
            Bacon, grilled|bacon rasher, back bacon|1 rasher|25|290|20|7.5|0|0|0|27|3.5
            Pork sausage, grilled|sausage, banger|1 sausage|45|290|22|8|9|1.5|0.6|15|1.8
            Baked beans|beans in tomato sauce|half a can|200|78|0.2|0|13|4.8|3.8|4.7|0.6
            Porridge with milk|oatmeal, oats with milk|1 bowl|250|102|3.3|1.6|13|4.6|1.1|4.7|0.1
            Porridge with water|oatmeal with water|1 bowl|250|62|1.4|0.2|10|0.2|1.3|2.1|0
            Porridge oats, dry|rolled oats, oats|1 portion|40|375|8|1.5|60|1|9|11|0
            Cornflakes|corn flakes cereal|1 bowl|30|378|0.9|0.2|84|8|3|7|1.1
            Wheat biscuit cereal|wheat biscuits, shredded wheat|2 biscuits|37|362|2|0.6|69|4.4|10|12|0.3
            Granola|crunchy oat cereal|1 bowl|45|450|17|4|62|20|7|10|0.1
            Muesli|swiss muesli|1 bowl|50|365|6|1|64|20|7|10|0.1
            Pancake|pancakes, plain pancake|1 pancake|60|227|9.7|2.1|28|5|0.9|6.4|0.9
            Waffle|waffles|1 waffle|75|290|14|3|33|6|1.5|7|1.1
            French toast|eggy bread|1 slice|65|230|11|3|25|4|1|8|0.8
            Croissant|butter croissant|1 croissant|60|406|21|12|45|11|2.6|8.2|1
            Pain au chocolat|chocolate croissant|1 pastry|60|420|23|13|45|14|2.5|7.5|0.8
            Smashed avocado on toast|avocado toast|1 slice|120|210|12|2|19|1.5|5|5|0.6
        """),
        group("bread","Bread & bakery","""
            White bread|sliced bread, white toast, bread|1 slice|36|245|1.6|0.4|47|3.5|2.6|8.9|1
            Wholemeal bread|brown bread, whole wheat bread, wholemeal toast|1 slice|36|230|2.5|0.5|40|3|7|10|1
            Sourdough bread|sourdough|1 slice|50|250|1.5|0.3|49|2|2.5|9.5|1.1
            Toast with butter|buttered toast|1 slice|43|326|14.6|8.6|39.5|3|2.2|7.5|1
            Bagel|plain bagel|1 bagel|85|257|1.6|0.3|50|5|2.3|10|1.1
            Tortilla wrap|wrap, flour tortilla|1 wrap|62|310|7.5|3|52|2.5|3|8.5|1.3
            Pitta bread|pita|1 pitta|60|265|1.2|0.2|55|2|2.2|9|1
            Naan bread|naan|1 naan|130|290|7.5|1.7|48|3.5|2|8.5|1.2
            Bread roll|white roll, bap|1 roll|60|265|3.5|0.8|48|3|2.5|9|1.1
            Crumpet|crumpets|1 crumpet|55|180|0.9|0.2|37|1.5|2|6|1.4
            English muffin|breakfast muffin|1 muffin|60|227|1.8|0.3|44|3|2.5|9|1
            Scone|plain scone|1 scone|70|360|14|6|51|14|2|7|1.2
            Garlic bread|garlic baguette|2 slices|60|350|16|7|43|2.5|2|8|1.3
        """),
        group("pasta","Pasta, rice & grains","""
            Pasta, cooked|spaghetti, penne, fusilli, macaroni, pasta bowl, plain pasta|1 bowl|250|158|0.9|0.2|31|0.6|1.8|5.8|0
            Wholewheat pasta, cooked|brown pasta|1 bowl|250|149|1.1|0.2|28|0.8|4|6|0
            Spaghetti bolognese|spag bol, pasta bolognese, bolognese|1 bowl|400|130|4.5|1.8|15|2.5|1.5|7|0.4
            Pasta with tomato sauce|tomato pasta, arrabbiata, marinara pasta|1 bowl|350|125|2.8|0.5|21|3|2|4.2|0.4
            Macaroni cheese|mac and cheese, mac n cheese|1 bowl|300|165|8|4.5|17|2|0.8|7|0.6
            Lasagne|lasagna, beef lasagne|1 portion|350|145|7|3.3|12.5|3|1|8|0.6
            Spaghetti carbonara|carbonara, pasta carbonara|1 bowl|350|190|9|4|19|1|1|8.5|0.7
            Pesto pasta|pasta with pesto|1 bowl|300|210|10|2|24|1|1.8|6|0.5
            Creamy chicken pasta|chicken pasta bake, pasta bake|1 bowl|400|160|7|3.5|15|2|1|9.5|0.5
            Tuna pasta|tuna pasta bake|1 bowl|350|150|5|1.5|17|2|1.5|10|0.5
            White rice, cooked|rice, boiled rice, basmati rice, jasmine rice, steamed rice|1 portion|180|130|0.3|0.1|28|0.1|0.4|2.7|0
            Brown rice, cooked|wholegrain rice|1 portion|180|123|1|0.3|26|0.4|1.6|2.7|0
            Egg fried rice|fried rice|1 portion|250|174|5|0.8|28|0.6|0.8|4.5|0.8
            Pilau rice|pilaf rice|1 portion|200|160|4|1|28|0.5|0.7|3|0.4
            Egg noodles, cooked|noodles|1 portion|200|138|2.1|0.4|25|0.4|1.2|4.5|0.1
            Rice noodles, cooked|vermicelli|1 portion|200|108|0.2|0|24|0|1|1.8|0
            Instant noodles|ramen noodles, pot noodle|1 pack, prepared|300|90|3.8|1.8|12|0.5|0.6|2|0.6
            Couscous, cooked|cous cous|1 portion|150|112|0.2|0|23|0.1|1.4|3.8|0
            Quinoa, cooked|quinoa|1 portion|150|120|1.9|0.2|21|0.9|2.8|4.4|0
            Mushroom risotto|risotto|1 bowl|350|140|4.5|2|20|1|0.8|3.5|0.5
            Paella|seafood paella|1 portion|400|150|5|1.2|18|1|1|9|0.6
        """),
        group("potatoes","Potatoes","""
            Boiled potatoes|potatoes, new potatoes|1 portion|175|86|0.1|0|19|0.9|1.8|1.7|0
            Jacket potato|baked potato|1 medium potato|220|93|0.1|0|21|1.2|2.2|2.5|0
            Jacket potato with beans and cheese|baked potato beans cheese|1 potato|380|122|4|2.4|15|2.2|2|5.5|0.5
            Mashed potato|mash, mashed potatoes|1 portion|180|104|4.2|2.6|15|1.4|1.3|1.8|0.3
            Roast potatoes|roasties|1 portion|200|149|4.5|0.6|25|0.6|2|2.6|0.1
            Oven chips|chips, fries, french fries, home fries|1 portion|150|162|4.2|0.5|28|0.6|2.4|3|0.1
            Chip shop chips|chippy chips, takeaway chips|1 portion|300|240|12|1|31|0.5|2.6|3.4|0.1
            Sweet potato, baked|sweet potato|1 medium|150|90|0.2|0|17.7|6.5|3.3|2|0.1
            Sweet potato fries|sweet potato chips|1 portion|150|180|7|0.8|27|7|3.5|2|0.4
            Potato wedges|wedges|1 portion|150|170|6|0.6|26|0.6|2.6|2.8|0.4
            Hash brown|hash browns|1 hash brown|60|265|14|1.5|30|0.5|3|2.6|0.9
            Potato salad|potato mayonnaise|1 portion|100|150|9|0.8|15|1.5|1.3|1.5|0.6
        """),
        group("meat","Meat, fish & protein","""
            Chicken breast, grilled|chicken, grilled chicken, chicken breast|1 breast|150|165|3.6|1|0|0|0|31|0.2
            Roast chicken with skin|roast chicken, chicken thigh, chicken leg|1 portion|150|223|13|3.7|0|0|0|25|0.2
            Fried chicken|breaded chicken, chicken drumstick|1 piece|120|260|15|4|9|0|0.4|22|1
            Chicken nuggets|nuggets|6 nuggets|100|270|16|2.5|17|0.5|1|15|1.1
            Beef steak, grilled|steak, sirloin steak, rump steak|1 steak|200|210|10|4.3|0|0|0|29|0.2
            Beef mince, cooked|minced beef, ground beef, mince|1 portion|125|250|17|7|0|0|0|24|0.2
            Burger patty, grilled|beef burger, hamburger patty|1 patty|113|250|17|7|0.5|0|0|23|0.6
            Burger in a bun|homemade burger, cheeseburger|1 burger|220|250|11|4.5|22|4|1.2|14|1.1
            Pork chop, grilled|pork chop|1 chop|150|230|13|4.5|0|0|0|28|0.2
            Lamb chop, grilled|lamb chop|1 chop|100|280|20|8.5|0|0|0|25|0.2
            Ham, sliced|ham|1 slice|23|107|3|1|1.4|1|0|19|2.2
            Salmon fillet, baked|salmon|1 fillet|130|206|12|2.5|0|0|0|22|0.2
            Tuna, canned in brine|tuna, tinned tuna|1 can, drained|112|109|0.6|0.2|0|0|0|25|0.9
            Cod fillet, baked|white fish, cod, haddock|1 fillet|150|105|0.9|0.2|0|0|0|23|0.3
            Fish and chips|battered fish and chips, fish supper|1 portion|500|236|12|1.2|23|0.5|2|9|0.3
            Fish fingers|fish sticks|3 fingers|84|220|9.5|1|19|1|1|13|0.7
            Prawns, cooked|shrimp, prawns|1 portion|100|99|0.9|0.2|0|0|0|22|1.5
            Tofu, firm|tofu, bean curd|1 portion|100|144|8.7|1.3|0.5|0.5|2.3|17|0
            Lentils, cooked|lentils|1 portion|150|116|0.4|0.1|12|1.8|8|9|0
            Chickpeas, cooked|chickpeas, garbanzo beans|1 portion|120|164|2.6|0.3|19.4|4.8|7.6|8.9|0
            Kidney beans, cooked|red kidney beans|1 portion|120|127|0.5|0.1|16.6|0.3|6.4|8.7|0
        """),
        group("meals","Home meals & takeaway dishes","""
            Chicken curry|curry|1 portion|350|140|8|2.5|5|2.5|1|12|0.7
            Chicken tikka masala|tikka masala|1 portion|350|150|9|3.5|6|3.5|1|12|0.8
            Thai green curry|green curry|1 portion|350|125|8|5.5|5|2.5|1|8|0.8
            Dal|dhal, daal, lentil curry|1 bowl|300|110|3.5|1|14|1.5|4|5.5|0.6
            Chilli con carne|chilli, chili con carne|1 portion|350|110|5|2|8|3|3|8.5|0.6
            Beef stew|stew, casserole|1 bowl|350|95|3.5|1.3|7|2|1.3|9|0.5
            Shepherd's pie|cottage pie|1 portion|350|120|5.5|2.5|10|1.5|1.3|7|0.5
            Roast dinner|sunday roast, roast chicken dinner|1 plate|550|135|5.5|1.5|11|2|2|10|0.4
            Chicken stir fry|stir fry, stir-fry|1 portion|300|110|4.5|0.8|7|4|1.8|11|0.9
            Sweet and sour chicken|sweet & sour chicken|1 portion|350|160|6|1|19|12|0.8|8|0.8
            Doner kebab|kebab|1 kebab with pitta|400|230|12|5|17|2|1.5|13|1.4
            Burrito|chicken burrito, beef burrito|1 burrito|300|190|7|3|23|1.5|3|8.5|1
            Taco|tacos|1 taco|80|220|11|4|18|1.5|2.5|12|0.9
            Sushi|maki rolls, sushi rolls|6 pieces|150|145|1.5|0.3|28|5|1|5|1
            Margherita pizza|pizza, cheese pizza|1 slice|100|266|10|4.5|33|3.6|2.3|11|1.3
            Pepperoni pizza|pizza pepperoni|1 slice|100|290|13|5.5|31|3.5|2|12|1.6
            Caesar salad|chicken caesar salad|1 bowl|250|150|11|2.5|6|1.5|1|7|0.8
            Quiche|quiche lorraine|1 slice|150|260|18|8|17|2|1|8|0.8
            Sausage roll|sausage rolls|1 roll|60|330|21|9|26|1|1.5|8.5|1.2
            Cornish pasty|pasty|1 pasty|230|270|16|7|25|1.5|1.8|6.5|1
            Vegetable soup|soup|1 bowl|300|40|1.2|0.2|6|2.5|1.5|1.3|0.6
            Tomato soup|cream of tomato soup|1 bowl|300|50|2|0.6|7|4.5|0.6|0.9|0.6
            Chicken noodle soup|chicken soup|1 bowl|300|38|1|0.3|4.5|0.5|0.3|2.5|0.7
            Cheese sandwich|cheese sarnie|1 sandwich|140|290|14|7.5|29|3|2|12|1.3
            Ham sandwich|ham sarnie|1 sandwich|150|220|7|2.5|27|3|2|12|1.4
            Chicken salad sandwich|chicken sandwich|1 sandwich|190|200|7|1.5|21|3|2|13|1
            BLT sandwich|bacon lettuce tomato|1 sandwich|180|240|12|3|22|3|2|11|1.4
            Tuna mayo sandwich|tuna sandwich|1 sandwich|180|230|10|1.5|22|2.5|2|13|1.1
            Egg mayo sandwich|egg sandwich|1 sandwich|170|240|12|2.5|22|2.5|2|10|1
            Hummus|houmous|2 tbsp|30|250|20|1.8|10|0.5|5|7|1
            Guacamole|avocado dip|2 tbsp|30|150|13|1.8|7|1|5|2|0.6
        """),
        group("dairy","Dairy & eggs","""
            Semi-skimmed milk|milk, semi skimmed milk|1 glass|200ml|47|1.7|1.1|4.8|4.8|0|3.6|0.1
            Whole milk|full fat milk|1 glass|200ml|64|3.6|2.3|4.7|4.7|0|3.4|0.1
            Skimmed milk|skim milk|1 glass|200ml|34|0.2|0.1|5|5|0|3.5|0.1
            Oat milk|oat drink|1 glass|200ml|46|1.5|0.2|6.6|4|0.8|0.3|0.1
            Cheddar cheese|cheese, cheddar|1 slice|30|416|35|22|0.1|0.1|0|25|1.8
            Mozzarella|fresh mozzarella|1 portion|30|254|19|13|1|1|0|19|0.5
            Feta cheese|feta|1 portion|30|265|21|14|1.5|1.5|0|14|2.7
            Cream cheese|soft cheese|1 portion|30|250|23|15|4|3|0|5.5|0.8
            Cottage cheese|cottage cheese|1 portion|100|98|4.3|2.3|3.4|2.7|0|11|0.8
            Greek yoghurt|greek yogurt, plain greek yoghurt|1 pot|150|133|10|7|3.8|3.8|0|5.7|0.1
            Fat-free Greek yoghurt|0% greek yogurt, skyr|1 pot|150|57|0.2|0.1|3.6|3.6|0|10|0.1
            Natural yoghurt|plain yogurt, yoghurt|1 pot|125|79|3|2|7.8|7.8|0|5.7|0.2
            Fruit yoghurt|fruit yogurt|1 pot|125|95|1.6|1|15|14|0.2|4.2|0.1
            Butter|salted butter|1 portion|10|744|82|51|0.6|0.6|0|0.6|1.2
            Ice cream|vanilla ice cream|1 scoop|60|207|11|7|24|21|0.7|3.5|0.2
        """),
        group("fruit","Fruit","""
            Banana|bananas|1 medium|118|89|0.3|0.1|20.4|12|2.6|1.1|0
            Apple|apples|1 medium|182|52|0.2|0|11.6|10|2.4|0.3|0
            Orange|oranges|1 medium|130|47|0.1|0|9.6|9.4|2.4|0.9|0
            Satsuma|clementine, mandarin, tangerine|1 fruit|70|47|0.2|0|10.3|9|1.7|0.8|0
            Pear|pears|1 medium|178|57|0.1|0|11.9|10|3.1|0.4|0
            Grapes|grape|1 handful|80|69|0.2|0.1|17.1|16|0.9|0.7|0
            Strawberries|strawberry|1 handful|80|32|0.3|0|5.7|4.9|2|0.7|0
            Blueberries|blueberry|1 handful|80|57|0.3|0|11.6|10|2.4|0.7|0
            Raspberries|raspberry|1 handful|80|52|0.7|0|5.5|4.4|6.5|1.2|0
            Avocado|avocados|half an avocado|70|160|15|2.1|1.8|0.7|6.7|2|0
            Mango|mangoes|1 portion|100|60|0.4|0.1|13.4|13.4|1.6|0.8|0
            Pineapple|pineapple chunks|1 portion|80|50|0.1|0|11.6|10|1.4|0.5|0
            Watermelon|melon|1 slice|280|30|0.2|0|7.2|6.2|0.4|0.6|0
            Kiwi|kiwi fruit|1 fruit|75|61|0.5|0|12|9|3|1.1|0
            Raisins|sultanas|1 small box|30|299|0.5|0.1|75.3|59|3.7|3.1|0
            Fruit salad|mixed fruit|1 bowl|150|50|0.2|0|12|10|1.5|0.6|0
        """),
        group("veg","Vegetables & salad","""
            Broccoli|broccoli boiled|1 portion|80|35|0.4|0.1|3.7|1.4|3.3|2.4|0
            Carrots|carrot|1 portion|80|35|0.2|0|5|3.5|3|0.8|0.1
            Peas|garden peas|1 portion|80|84|0.2|0|10.5|6|5.5|5.4|0
            Sweetcorn|corn|1 portion|80|96|1.5|0.2|18.6|4.5|2.4|3.4|0
            Green beans|beans green|1 portion|80|31|0.2|0|4.3|3.3|2.7|1.8|0
            Mixed vegetables|veg, frozen vegetables|1 portion|80|50|0.3|0|6.5|3|3.5|2.5|0
            Spinach|baby spinach|1 portion|80|23|0.4|0.1|1.4|0.4|2.2|2.9|0.2
            Fried mushrooms|mushrooms|1 portion|80|150|15|2|0.5|0.3|1.5|2.4|0
            Green salad|salad, side salad, lettuce|1 bowl|100|15|0.2|0|1.6|1.2|1.3|1.4|0
            Tomato|tomatoes|1 medium|120|18|0.2|0|2.7|2.6|1.2|0.9|0
            Cucumber|cucumber slices|1 portion|80|15|0.1|0|3.1|1.7|0.5|0.7|0
            Pepper|bell pepper, red pepper|half a pepper|80|31|0.3|0|3.9|3.9|2.1|1|0
            Onion|onions|1 medium|110|40|0.1|0|7.6|4.2|1.7|1.1|0
            Coleslaw|slaw|1 portion|80|150|14|1.5|6|5|1.5|1|0.6
            Roasted vegetables|roast veg|1 portion|150|80|4.5|0.6|8|5|2.5|1.5|0.3
        """),
        group("snacks","Snacks, sweets & spreads","""
            Crisps|potato chips, chips bag|1 bag|25|530|33|2.5|52|0.5|4|6.5|1.3
            Popcorn, salted|popcorn|1 bag|30|480|25|2.5|55|0.5|10|8|1.4
            Milk chocolate|chocolate, chocolate bar|1 bar|45|535|30|18|57|56|2|7.5|0.2
            Dark chocolate|70% chocolate|2 squares|20|600|43|26|35|28|11|8|0
            Digestive biscuit|biscuit|1 biscuit|15|480|21|10|63|17|3.5|7|1
            Chocolate chip cookie|cookie, cookies|1 cookie|30|490|24|12|63|35|2|5.5|0.7
            Sponge cake|cake, victoria sponge|1 slice|70|400|20|10|50|33|0.8|4.5|0.6
            Chocolate cake|chocolate fudge cake|1 slice|90|380|18|7|50|35|2.5|5|0.6
            Doughnut|donut|1 doughnut|60|420|23|11|46|20|1.5|5.5|0.7
            Blueberry muffin|muffin|1 muffin|110|380|19|3.5|47|25|1.3|5|0.8
            Brownie|chocolate brownie|1 brownie|60|440|24|10|52|38|2.5|5|0.4
            Flapjack|oat bar|1 piece|60|460|23|11|58|32|3.5|5.5|0.4
            Cereal bar|granola bar|1 bar|30|410|15|6|62|30|4|6|0.4
            Protein bar|protein snack bar|1 bar|60|360|12|5|35|5|5|33|0.5
            Almonds|nuts, almond|1 handful|30|579|50|3.8|22|4.4|12.5|21|0
            Peanuts, salted|peanuts, roasted peanuts|1 handful|30|600|51|8|14|4|8|26|1
            Mixed nuts|nuts|1 handful|30|610|54|7|14|4|7|18|0.1
            Peanut butter|peanut butter|1 tbsp|15|590|50|10|16|8|6|25|1
            Rice cake|rice cakes|1 cake|8|387|2.8|0.6|81|0.9|4.2|8|0.1
            Jam|jelly, preserve|1 tsp|15|250|0|0|62|60|0.8|0.4|0
            Honey|runny honey|1 tsp|7|304|0|0|82|82|0.2|0.3|0
            Sugar|white sugar, table sugar|1 tsp|4|400|0|0|100|100|0|0|0
            Chocolate spread|hazelnut spread|1 tbsp|15|540|31|10|57|56|3|6|0.1
            Olive oil|oil, cooking oil, vegetable oil|1 tbsp|14|884|100|14|0|0|0|0|0
            Mayonnaise|mayo|1 tbsp|15|680|75|6|1.5|1.3|0|1|1.3
            Ketchup|tomato ketchup, tomato sauce|1 tbsp|17|110|0.1|0|25|23|0.8|1.2|1.8
            Gravy|gravy|1 portion|50|30|1.5|0.6|3.5|0.3|0.2|0.6|1
        """),
        group("drinks","Drinks","""
            Black coffee|coffee, americano, filter coffee, espresso|1 mug|250ml|1|0|0|0|0|0|0.1|0
            Latte|cafe latte, flat white|1 regular|350ml|37|1.4|0.9|3.6|3.5|0|2.5|0.1
            Cappuccino|cappuccino|1 regular|250ml|38|1.5|1|3.8|3.8|0|2.6|0.1
            Tea with milk|cup of tea, tea|1 mug|250ml|7|0.2|0.1|0.7|0.7|0|0.5|0
            Hot chocolate|cocoa|1 mug|250ml|80|2.5|1.5|11|10|0.5|3.3|0.1
            Orange juice|juice, fruit juice|1 glass|200ml|45|0.2|0|10|9|0.2|0.7|0
            Fruit smoothie|smoothie|1 bottle|250ml|55|0.2|0|12|11|1|0.6|0
            Cola|coke, fizzy drink, soft drink|1 can|330ml|42|0|0|10.6|10.6|0|0|0
            Diet cola|diet coke, zero cola|1 can|330ml|0.4|0|0|0|0|0|0|0
            Lager|beer, pint of lager|1 pint|568ml|43|0|0|3.6|0.3|0|0.5|0
            Red wine|wine|1 glass (175 ml)|175ml|85|0|0|2.6|0.6|0|0.1|0
            White wine|wine white|1 glass (175 ml)|175ml|82|0|0|2.6|1|0|0.1|0
            Protein shake|whey protein, protein powder|1 scoop|30|400|6.5|3|10|6|0|78|0.6
            Water|still water, sparkling water|1 glass|250ml|0|0|0|0|0|0|0|0
        """),
    )}

    val items:List<Food> get()=groups.flatMap {it.items}

    /** Matches names and everyday synonyms; "bowl of pasta" finds "Pasta, cooked", and small typos still match. */
    fun search(query:String,limit:Int=20):List<Food> {
        val filler=setOf("a","an","of","some","bowl","plate","portion","slice","cup","glass","mug","with","and","the","my")
        val words=FastFood.normalise(query).split(' ').filter {it.isNotBlank() && it !in filler}
        if(words.isEmpty()) return emptyList()
        groups.size // ensures aliases are loaded
        return items.mapNotNull {food->
            val name=FastFood.normalise(food.name).split(' ')
            val other=FastFood.normalise(aliases[food.id].orEmpty()).split(' ')
            val hay=name+other
            fun hit(w:String,h:List<String>)=h.any {it.startsWith(w) || (w.length>=4 && typoMatch(w,it))}
            if(!words.all {hit(it,hay)}) return@mapNotNull null
            // Best: every word in the name itself, then earlier and shorter names.
            val rank=(if(words.all {hit(it,name)}) 0 else 100)+name.indexOfFirst {it.startsWith(words.first())}.let {if(it<0) 50 else it*5}+food.name.length/4
            food to rank
        }.sortedBy {it.second}.take(limit).map {it.first}
    }
}
