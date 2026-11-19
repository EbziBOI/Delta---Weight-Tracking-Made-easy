package io.github.ebziboi.delta.nutrition

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.ebziboi.delta.ui.*
import java.time.LocalDate

private val searchTabs=listOf("Recent","My foods","Recipes","Fast food")

/** Words in [query] each start a word of the food's name or brand. */
internal fun Food.matches(query:String):Boolean {
    val words=FastFood.normalise(query).split(' ').filter {it.isNotBlank()}
    val hay=FastFood.normalise("$name $brand").split(' ')
    return words.isNotEmpty() && words.all {w->hay.any {it.startsWith(w)}}
}

@Composable
internal fun FoodSearchPage(vm: NutritionViewModel,state: NutritionState,padding: PaddingValues,onBack:()->Unit,onFood:(Food)->Unit,
                            onCustom:()->Unit,onRecipe:()->Unit,onPhoto:()->Unit,ingredientsOnly:Boolean=false,onImport:((String)->Unit)?=null,
                            onMissing:((String)->Unit)?=null,onIncomplete:((Food)->Unit)?=null,onQuickAdd:((Food)->Unit)?=null,
                            mealLabel:String?=null,meal:Meal?=null,onMeal:((Meal)->Unit)?=null) {
    androidx.activity.compose.BackHandler(onBack=onBack)
    var query by rememberSaveable { mutableStateOf("") }
    var searchedQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var chainId by rememberSaveable { mutableStateOf(FastFood.chains.first().id) }
    val context=LocalContext.current;val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    val scope=rememberCoroutineScope();val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val pager=androidx.compose.foundation.pager.rememberPagerState(initialPage=tab) {searchTabs.size}
    LaunchedEffect(pager.currentPage) {tab=pager.currentPage}
    val missing=onMissing ?: onImport
    val scanner=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==android.app.Activity.RESULT_OK) result.data?.getStringExtra("barcode")?.let {vm.barcode(it,onFood,missing,onIncomplete)}
    }
    fun scan()=runCatching {scanner.launch(Intent(context,BarcodeActivity::class.java))}.onFailure {vm.message="Camera unavailable. Search by name instead."}
    fun searchOnline() {if(query.trim().length>=2 && !vm.busy) {focus.clearFocus();searchedQuery=query.trim();vm.search(query)}}
    val history=remember(state.entries,state.foods) {state.entries.filterNot {it.fromHealthConnect}.sortedByDescending {it.created}.distinctBy {it.food.id}.map { e -> state.foods.find {it.id==e.food.id} ?: e.food }}
    val quick=if(ingredientsOnly) null else onQuickAdd
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.padding(horizontal=20.dp).padding(top=padding.calculateTopPadding()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            ScreenHeader(if(ingredientsOnly) "Ingredient" else "Add food",eyebrow=mealLabel,leading={GlassIconButton(io.github.ebziboi.delta.R.drawable.ic_arrow_back,"Back",onBack,size=40.dp)}) {
                if(meal!=null && onMeal!=null) Box {
                    var open by remember {mutableStateOf(false)}
                    SecondaryButton(meal.label,{open=true},icon=io.github.ebziboi.delta.R.drawable.ic_expand_more)
                    DropdownMenu(open,{open=false}) {Meal.entries.forEach {m->HapticDropdownMenuItem({Text(m.label)},{onMeal(m);open=false})}}
                }
            }
            if(query.isBlank()) SegmentedControl(searchTabs.indices.toList(),pager.currentPage,{searchTabs[it]}) {i->scope.launch {pager.animateScrollToPage(i)}}
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if(query.isBlank()) androidx.compose.foundation.pager.HorizontalPager(pager,Modifier.fillMaxSize(),beyondViewportPageCount=1) {page->
                when(searchTabs[page]) {
                    "Fast food" -> FastFoodTab(chainId,{chainId=it},onFood,quick,vm.busy)
                    else -> {
                        val foods=when(searchTabs[page]) {
                            "My foods" -> state.foods.filter {it.source==FoodSource.CUSTOM || it.favourite}.sortedByDescending {it.favourite}
                            "Recipes" -> state.foods.filter {it.source==FoodSource.RECIPE}
                            else -> history.take(60)
                        }
                        FoodList(foods,onFood,quick,vm.busy,header={
                            if(page<=1 && !ingredientsOnly) CreateActions(onCustom,onRecipe,onPhoto,onImport?.let {{it("")}},vm.busy)
                            if(foods.isEmpty()) EmptyHint(when(searchTabs[page]) {
                                "Recipes" -> "Your saved recipes will appear here." to ("New recipe" to onRecipe)
                                "My foods" -> "Create a food or save a favourite." to ("Custom food" to onCustom)
                                else -> "Search below, scan a barcode, or pick from Fast food." to ("Scan barcode" to {scan();Unit})
                            })
                        })
                    }
                }
            } else {
                val local=remember(query,history,state.foods) {(history+state.foods).distinctBy {it.id}.filter {it.matches(query)}.take(12)}
                val menu=remember(query) {FastFood.search(query,12)}
                val fresh=searchedQuery==query.trim()
                androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=12.dp,end=12.dp,top=4.dp,bottom=16.dp)) {
                    if(local.isNotEmpty()) {
                        item {SectionLabel("Your foods")}
                        items(local,key={"l:"+it.id}) {FoodRow(it,{onFood(it)},quick?.let {q->{q(it)}},vm.busy)}
                    }
                    if(menu.isNotEmpty()) {
                        item {SectionLabel("Fast food · menu guide")}
                        items(menu,key={"m:"+it.id}) {FoodRow(it,{onFood(it)},quick?.let {q->{q(it)}},vm.busy)}
                    }
                    item {
                        Column(Modifier.padding(top=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            SectionLabel("Supermarket products · Open Food Facts")
                            StoreChips(vm) {if(fresh) searchOnline()}
                            if(!fresh || vm.busy) SecondaryButton(if(vm.busy) "Searching…" else "Search products for “${query.trim()}”",::searchOnline,Modifier.fillMaxWidth(),enabled=!vm.busy && query.trim().length>=2,icon=io.github.ebziboi.delta.R.drawable.ic_search)
                        }
                    }
                    if(fresh && !vm.busy) {
                        if(vm.searched && vm.results.isEmpty()) item {
                            Column(Modifier.padding(vertical=12.dp,horizontal=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("No products found for “${query.trim()}”.",style=type.body)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    SecondaryButton("Scan barcode",{scan()},Modifier.weight(1f))
                                    SecondaryButton("Custom food",onCustom,Modifier.weight(1f))
                                }
                            }
                        }
                        items(vm.results,key={"o:"+it.id}) {f->FoodRow(f,{if(f.missingNutrition && onIncomplete!=null) onIncomplete(f) else onFood(f)},if(f.missingNutrition) null else quick?.let {q->{q(f)}},vm.busy)}
                    }
                    item {
                        val uri=androidx.compose.ui.platform.LocalUriHandler.current
                        TextAction("Open Food Facts · ODbL licence",{uri.openUri("https://world.openfoodfacts.org/terms-of-use")},color=Color(c.textDim))
                    }
                }
            }
        }
        // Persistent search dock: always within thumb reach, and above the keyboard while typing.
        Column(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(c.bg).copy(alpha=0f),Color(c.bg).copy(alpha=.92f),Color(c.bg))))
            .navigationBarsPadding().padding(start=16.dp,end=16.dp,top=14.dp,bottom=10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                SearchField(query,{query=it},Modifier.weight(1f),onSearch=::searchOnline)
                GlassIconButton(io.github.ebziboi.delta.R.drawable.ic_barcode,"Scan barcode",{scan()},size=56.dp,tint=Color(c.accent),enabled=!vm.busy)
            }
        }
    }
}

@Composable private fun SearchField(value:String,onChange:(String)->Unit,modifier:Modifier,onSearch:()->Unit) {
    val c=LocalDeltaColors.current
    OutlinedTextField(value,onChange,modifier.heightIn(min=56.dp).semantics {contentDescription="Search foods"},singleLine=true,
        placeholder={Text("Search foods, brands, menus",maxLines=1)},
        leadingIcon={DeltaIcon(io.github.ebziboi.delta.R.drawable.ic_search,null,tint=Color(c.textDim),size=20.dp)},
        trailingIcon=if(value.isNotEmpty()) ({HapticIconButton({onChange("")}) {DeltaIcon(io.github.ebziboi.delta.R.drawable.ic_close,"Clear search",tint=Color(c.textDim),size=18.dp)}}) else null,
        keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Search,capitalization=androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
        keyboardActions=androidx.compose.foundation.text.KeyboardActions(onSearch={onSearch()}),
        shape=RoundedCornerShape(28.dp),
        colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color(c.text),unfocusedTextColor=Color(c.text),focusedContainerColor=Color(c.surfaceHigh),unfocusedContainerColor=Color(c.surfaceHigh),
            focusedBorderColor=Color(c.accent),unfocusedBorderColor=Color(c.glass.edge),cursorColor=Color(c.accent)))
}

@Composable private fun SectionLabel(text:String) {Caption(text,Modifier.padding(start=8.dp,top=12.dp,bottom=4.dp))}

@Composable private fun EmptyHint(content:Pair<String,Pair<String,()->Unit>>) {
    Column(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Text(content.first,style=LocalDeltaType.current.body,color=Color(LocalDeltaColors.current.textDim),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        SecondaryButton(content.second.first,content.second.second)
    }
}

/** Compact ways to create a food, kept to one tidy row. */
@Composable private fun CreateActions(onCustom:()->Unit,onRecipe:()->Unit,onPhoto:()->Unit,onImport:(()->Unit)?,busy:Boolean) {
    val c=LocalDeltaColors.current
    val actions=listOfNotNull(Triple("Custom food",io.github.ebziboi.delta.R.drawable.ic_edit,onCustom),Triple("New recipe",io.github.ebziboi.delta.R.drawable.ic_restaurant,onRecipe),
        Triple("Photo",io.github.ebziboi.delta.R.drawable.ic_auto_awesome,onPhoto),onImport?.let {Triple("Import food",io.github.ebziboi.delta.R.drawable.ic_download,it)})
    Row(Modifier.fillMaxWidth().padding(horizontal=4.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        actions.forEach {(label,icon,action)->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(Radii.control)).background(Color(c.text).copy(alpha=.05f))
                .clickable(enabled=!busy,role=androidx.compose.ui.semantics.Role.Button,onClick=action).padding(vertical=10.dp),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                DeltaIcon(icon,null,tint=Color(c.accent),size=20.dp)
                Text(label,style=LocalDeltaType.current.label,maxLines=1)
            }
        }
    }
}

@Composable private fun StoreChips(vm:NutritionViewModel,onChanged:()->Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(horizontal=4.dp)) {
        item {Chip("All stores",vm.selectedStore==null,!vm.busy) {vm.chooseStore(null);onChanged()}}
        items(vm.storeOrder,key={it.id}) {store->Chip(store.label,vm.selectedStore==store.id,!vm.busy) {vm.chooseStore(store.id);onChanged()}}
    }
}
@Composable internal fun Chip(label:String,selected:Boolean,enabled:Boolean=true,onClick:()->Unit) {
    val c=LocalDeltaColors.current
    Box(Modifier.heightIn(min=36.dp).clip(RoundedCornerShape(18.dp)).background(if(selected) Color(c.accent) else Color(c.text).copy(alpha=.06f))
        .clickable(enabled=enabled,role=androidx.compose.ui.semantics.Role.Tab,onClick=onClick).semantics {this.selected=selected}.padding(horizontal=14.dp,vertical=8.dp),contentAlignment=Alignment.Center) {
        Text(label,style=LocalDeltaType.current.bodySmall,color=Color(if(selected) c.onAccent else c.text),maxLines=1)
    }
}

@Composable private fun FoodList(foods:List<Food>,onFood:(Food)->Unit,onQuickAdd:((Food)->Unit)?,busy:Boolean,header:@Composable ()->Unit) {
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=12.dp,end=12.dp,top=8.dp,bottom=16.dp)) {
        item {header()}
        items(foods,key={it.id}) {f->FoodRow(f,{onFood(f)},if(f.missingNutrition) null else onQuickAdd?.let {q->{q(f)}},busy)}
    }
}

@Composable private fun FastFoodTab(chainId:String,onChain:(String)->Unit,onFood:(Food)->Unit,onQuickAdd:((Food)->Unit)?,busy:Boolean) {
    val chain=FastFood.chains.firstOrNull {it.id==chainId} ?: FastFood.chains.first()
    val uri=androidx.compose.ui.platform.LocalUriHandler.current;val c=LocalDeltaColors.current
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=12.dp,end=12.dp,top=8.dp,bottom=16.dp)) {
        item {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(horizontal=4.dp,vertical=4.dp)) {
                items(FastFood.chains,key={it.id}) {ch->Chip(ch.label,ch.id==chain.id) {onChain(ch.id)}}
            }
        }
        items(chain.items,key={it.id}) {f->FoodRow(f,{onFood(f)},onQuickAdd?.let {q->{q(f)}},busy)}
        item {
            Column(Modifier.padding(horizontal=8.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("Approximate values from ${chain.label}’s published UK nutrition information. Recipes and portions change, so check the official guide for exact figures; open an item to save an editable copy.",
                    style=LocalDeltaType.current.label,color=Color(c.textDim))
                TextAction("${chain.label} nutrition guide",{uri.openUri(chain.guide)})
            }
        }
    }
}

/** One food in a list: name, source and portion on the left; energy and a one-tap add on the right. */
@Composable internal fun FoodRow(food:Food,onClick:()->Unit,onQuickAdd:(()->Unit)?,busy:Boolean) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    val basis=food.serving
    fun at(n:Nutrient)=food.per100[n]?.let {it*(basis ?: 100.0)/100}
    val portion=if(basis!=null) food.servingLabel.takeIf {it.isNotBlank() && it!="Serving"} ?: "${amountText(basis)} ${food.unit.symbol}" else "100 ${food.unit.symbol}"
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.tile)).clickable(enabled=!busy,onClick=onClick).padding(horizontal=8.dp,vertical=10.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        val icon=when(food.source) {
            FoodSource.RESTAURANT->io.github.ebziboi.delta.R.drawable.ic_restaurant;FoodSource.OPEN_FOOD_FACTS->io.github.ebziboi.delta.R.drawable.ic_barcode
            FoodSource.RECIPE->io.github.ebziboi.delta.R.drawable.ic_layers;FoodSource.PHOTO->io.github.ebziboi.delta.R.drawable.ic_auto_awesome
            else->if(food.favourite) io.github.ebziboi.delta.R.drawable.ic_favorite_fill else io.github.ebziboi.delta.R.drawable.ic_food
        }
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(c.accent).copy(alpha=.12f)),contentAlignment=Alignment.Center) {DeltaIcon(icon,null,tint=Color(c.accent),size=18.dp)}
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(food.name,style=type.body,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(listOf(food.brand.ifBlank {food.subtitle},portion).filter {it.isNotBlank()}.joinToString(" · "),style=type.label,color=Color(c.textDim),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if(food.missingNutrition) Text("No nutrition listed · add it from the label",style=type.label,color=Color(c.warn))
            else Text(Nutrient.macros.joinToString("  ") {n->"${n.label.take(1)} ${at(n)?.let {v->amountText(Math.round(v*10)/10.0)} ?: "–"}"},style=type.label,color=Color(c.textDim))
        }
        Column(horizontalAlignment=Alignment.End) {
            Text(at(Nutrient.ENERGY)?.let {amountText(Math.round(it).toDouble())} ?: "—",style=type.number)
            Text("kcal",style=type.label,color=Color(c.textDim))
        }
        if(onQuickAdd!=null) Box(Modifier.size(36.dp).clip(CircleShape).background(Color(c.accent).copy(alpha=.16f))
            .clickable(enabled=!busy,role=androidx.compose.ui.semantics.Role.Button,onClickLabel="Quick add ${food.name}",onClick=onQuickAdd).semantics {contentDescription="Quick add ${food.name}"},contentAlignment=Alignment.Center) {
            DeltaIcon(io.github.ebziboi.delta.R.drawable.ic_add,null,tint=Color(c.accent),size=18.dp)
        }
    }
}

@Composable
internal fun PortionPage(food:Food,entry:DiaryEntry?,day:LocalDate,meal:Meal,padding:PaddingValues,busy:Boolean,onBack:()->Unit,
                         onSave:(Double,Meal)->Unit,onDelete:(()->Unit)?,onFavourite:()->Unit,onEdit:(()->Unit)?,onDeleteFood:(()->Unit)?,isIngredient:Boolean=false,
                         onFillNutrition:(()->Unit)?=null,onEditCopy:(()->Unit)?=null) {
    val options=listOfNotNull(food.unit.symbol,"Serving".takeIf {food.serving!=null},"Pack".takeIf {food.pack!=null})
    var portion by rememberSaveable(food.id,entry?.id) { mutableStateOf(if(entry!=null) food.unit.symbol else if(food.serving!=null) "Serving" else food.unit.symbol) }
    var quantity by rememberSaveable(food.id,entry?.id) { mutableStateOf(entry?.amount?.let(::editText) ?: if(food.serving!=null) "1" else "100") }
    var selectedMeal by rememberSaveable(entry?.id) {mutableStateOf(entry?.meal ?: meal)}
    var confirmDelete by remember { mutableStateOf(false) }
    val factor=when(portion) {"Serving" -> food.serving ?: 1.0;"Pack" -> food.pack ?: 1.0;else -> 1.0}
    val amount=quantity.number()?.times(factor)?.takeIf {it.isFinite() && it>0 && it<=100000}
    NutritionPage(if(entry==null) "Portion" else "Edit entry",padding,onBack) {
        val c=LocalDeltaColors.current;val type=LocalDeltaType.current;val colours=macroColours()
        ContentSection(tone=ModuleTone.SAGE) {
            Text(food.name,style=type.title);Text(food.subtitle,style=type.bodySmall)
            if(food.estimated) StatusPill("Estimated",Color(c.warn))
            if(food.missingNutrition) {
                Text("This product has no nutrition information yet. Copy it from the label to log it accurately.",style=type.bodySmall,color=Color(c.warn))
                onFillNutrition?.let {PrimaryButton("Add nutrition from label",it,Modifier.fillMaxWidth())}
            } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                ValueWithUnit(amount?.let {a->food.per100[Nutrient.ENERGY]?.let {amountText(Math.round(it*a/100).toDouble())}} ?: "—","kcal",style=type.numberLarge)
                Spacer(Modifier.weight(1f))
                Nutrient.macros.forEachIndexed {i,n->
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(amount?.let {a->food.per100[n]?.let {"${amountText(Math.round(it*a/10)/10.0)}g"}} ?: "–",style=type.bodyStrong,color=colours[i])
                        Text(if(n==Nutrient.CARBS) "Carbs" else n.label,style=type.label,color=Color(c.textDim))
                    }
                }
            }
            Text(day.format(dayFormat),style=type.label,color=Color(c.textDim))
        }
        if(options.size>1) SegmentedControl(options,portion,{it}) { next ->
            val oldAmount=amount
            portion=next
            val nextFactor=when(next) {"Serving" -> food.serving!!;"Pack" -> food.pack!!;else -> 1.0}
            quantity=oldAmount?.let { editText(it/nextFactor) } ?: "1"
        }
        NumberField(quantity,{quantity=it},if(portion==food.unit.symbol) "Amount (${food.unit.symbol})" else "Number of ${portion.lowercase()}s",Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            val presets=if(portion==food.unit.symbol) listOf(50.0,100.0,150.0,200.0) else listOf(.5,1.0,1.5,2.0)
            presets.forEach {v->Box(Modifier.weight(1f)) {Chip(if(portion==food.unit.symbol) "${amountText(v)} ${food.unit.symbol}" else "${amountText(v)}×",quantity.number()==v) {quantity=editText(v)}}}
        }
        if(portion!="${food.unit.symbol}") Text("1 ${portion.lowercase()} = ${amountText(factor)} ${food.unit.symbol}",style=LocalDeltaType.current.bodySmall)
        if(amount!=null) Text("${amountText(amount)} ${food.unit.symbol} total",style=LocalDeltaType.current.bodySmall)
        if(!isIngredient) SegmentedControl(Meal.entries,selectedMeal,{it.label}) {selectedMeal=it}
        PrimaryButton(if(isIngredient) "Add ingredient" else if(entry==null) "Add food" else "Save entry",{onSave(amount!!,selectedMeal)},Modifier.fillMaxWidth(),enabled=amount!=null && !busy)
        ContentSection {
            Caption("Nutrition for this portion")
            Nutrient.entries.filter {it in food.per100}.forEach {n -> NutritionRow(n.label,nutrientText(amount?.let {food.per100.getValue(n)*it/100},n)) }
            if(food.per100.isEmpty()) Text("Nutrition not supplied. Add a custom food using its label.")
            if(food.per100.size<Nutrient.entries.size) Text("Unlisted nutrients are unknown.",style=LocalDeltaType.current.bodySmall,color=Color(LocalDeltaColors.current.textDim))
        }
        SecondaryButton(if(food.favourite) "Remove favourite" else "Save favourite",onFavourite,Modifier.fillMaxWidth(),enabled=!busy)
        onEdit?.let {SecondaryButton(if(food.source==FoodSource.RECIPE) "Edit recipe" else "Edit custom food",it,Modifier.fillMaxWidth(),enabled=!busy)}
        onEditCopy?.let {SecondaryButton("Correct values (saves a copy)",it,Modifier.fillMaxWidth(),enabled=!busy,icon=io.github.ebziboi.delta.R.drawable.ic_edit)}
        if(food.source==FoodSource.RESTAURANT) Text("Approximate values from ${food.brand}’s published UK nutrition information. Recipes and portions change; check the official guide for exact figures.",style=type.label,color=Color(c.textDim))
        food.sourceUrl?.let {url->
            val uri=androidx.compose.ui.platform.LocalUriHandler.current
            TextAction(if(food.source==FoodSource.RESTAURANT) "Official nutrition guide" else "View product source",{uri.openUri(url)})
        }
        if(food.labelChecked.isNotEmpty()) Text("${food.labelChecked.size} values read from the package label",style=LocalDeltaType.current.bodySmall)
        if(food.source==FoodSource.OPEN_FOOD_FACTS) {
            val uri=androidx.compose.ui.platform.LocalUriHandler.current
            TextAction("View label on Open Food Facts",{uri.openUri("https://world.openfoodfacts.org/product/${food.barcode}")})
        }
        onDelete?.let { TextAction("Delete entry",{confirmDelete=true}) }
        onDeleteFood?.let { TextAction("Delete from library",{confirmDelete=true}) }
    }
    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text(if(onDelete!=null) "Delete this entry?" else "Delete this saved food?")},text={if(onDeleteFood!=null) Text("Past diary entries will be kept.")},confirmButton={HapticTextButton({confirmDelete=false;(onDelete ?: onDeleteFood)?.invoke()}){Text("Delete")}},dismissButton={HapticTextButton({confirmDelete=false}){Text("Cancel")}})
}

@Composable
internal fun FoodEditor(food:Food?,padding:PaddingValues,busy:Boolean,onBack:()->Unit,barcode:String?=null,title:String?=null,onReadLabel:(()->Unit)?=null,onSave:(Food)->Unit) {
    var name by rememberSaveable(food?.id) {mutableStateOf(food?.name ?: "")}
    var brand by rememberSaveable(food?.id) {mutableStateOf(food?.brand ?: "")}
    var unit by rememberSaveable(food?.id) {mutableStateOf(food?.unit ?: FoodUnit.GRAMS)}
    var serving by rememberSaveable(food?.id) {mutableStateOf(food?.serving?.let(::editText) ?: "")}
    var pack by rememberSaveable(food?.id) {mutableStateOf(food?.pack?.let(::editText) ?: "")}
    var fields by rememberSaveable(food?.id) {mutableStateOf(Nutrient.entries.associate {it.name to (food?.per100?.get(it)?.let(::editText) ?: "")})}
    var expanded by rememberSaveable {mutableStateOf(false)}
    var code by rememberSaveable(food?.id) {mutableStateOf(food?.barcode ?: barcode ?: "")}
    var perServing by rememberSaveable(food?.id) {mutableStateOf(false)}
    val servingSize=serving.number()?.takeIf {it>0 && it<=100000}
    val factor=if(perServing) servingSize?.let {100/it} else 1.0
    val valid=name.isNotBlank() && name.length<=250 && fields.all {(_,v)->v.isBlank() || v.number()!=null} &&
        listOf(serving,pack).all {it.isBlank() || (it.number()?.let {v->v>0 && v<=100000}==true)} && factor!=null &&
        (code.isBlank() || ProductPages.canonicalBarcode(code)!=null)
    fun switchBasis(toServing:Boolean) {
        val size=servingSize
        if(size!=null && toServing!=perServing) fields=fields.mapValues {(_,v)->v.number()?.let {n->editText(Math.round((if(toServing) n*size/100 else n*100/size)*1000)/1000.0)} ?: v}
        perServing=toServing
    }
    NutritionPage(title ?: if(food==null) "Custom food" else "Edit food",padding,onBack) {
        val c=LocalDeltaColors.current
        if((food==null && barcode!=null) || food?.missingNutrition==true) ContentSection(tone=ModuleTone.SAGE) {
            Text(if(food==null) "This barcode isn’t in the food database yet." else "The database has no nutrition for this product.",style=LocalDeltaType.current.bodyStrong)
            Text("Copy the values from the pack’s nutrition label. It’s saved to My foods, so the next scan finds it instantly.",style=LocalDeltaType.current.bodySmall)
            onReadLabel?.let {SecondaryButton("Read the label with the camera instead",it,Modifier.fillMaxWidth(),icon=io.github.ebziboi.delta.R.drawable.ic_auto_awesome)}
        }
        GlassTextField(name,{name=it},"Food name",Modifier.fillMaxWidth());GlassTextField(brand,{brand=it},"Brand (optional)",Modifier.fillMaxWidth())
        if(food?.barcode!=null || barcode!=null) GlassTextField(code,{code=it.filter(Char::isDigit).take(14)},"Barcode",Modifier.fillMaxWidth())
        SegmentedControl(FoodUnit.entries,unit,{if(it==FoodUnit.GRAMS) "Solid (g)" else "Liquid (ml)"}) {unit=it}
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            NumberField(serving,{serving=it},"Serving (${unit.symbol})",Modifier.weight(1f))
            NumberField(pack,{pack=it},"Pack (${unit.symbol})",Modifier.weight(1f))
        }
        ContentSection {
            Caption("Nutrition label")
            SegmentedControl(listOf(false,true),perServing,{if(it) "Per serving" else "Per 100 ${unit.symbol}"}) {switchBasis(it)}
            if(perServing && servingSize==null) Text("Enter the serving size above to use per-serving values.",style=LocalDeltaType.current.bodySmall,color=Color(c.warn))
            else Text("Type the label’s column as printed. Leave values blank when the label doesn’t list them.",style=LocalDeltaType.current.bodySmall,color=Color(c.textDim))
            Nutrient.entries.filter {expanded || it in Nutrient.primary + listOf(Nutrient.SATURATED,Nutrient.SUGAR,Nutrient.FIBRE,Nutrient.SALT)}.forEach {n ->
                NumberField(fields[n.name] ?: "",{s->fields=fields+(n.name to s)},"${n.label} (${n.unit})",Modifier.fillMaxWidth())
                if(n==Nutrient.ENERGY && fields[n.name].isNullOrBlank()) {
                    val macros=Nutrient.macros.map {fields[it.name]?.number()}
                    if(macros.all {it!=null}) {
                        val kcal=Math.round(macros[0]!!*4+macros[1]!!*4+macros[2]!!*9).toDouble()
                        TextAction("Use ≈ ${amountText(kcal)} kcal from protein, carbs and fat",{fields=fields+(n.name to editText(kcal))})
                    }
                }
            }
            TextAction(if(expanded) "Fewer nutrients" else "Vitamins, minerals & more",{expanded=!expanded})
        }
        PrimaryButton("Save food",{
            val scale=factor ?: 1.0
            onSave(Food(food?.id ?: newId(),name.trim(),brand.trim().take(250),fields.mapNotNull {(k,v)->v.takeIf {it.isNotBlank()}?.number()?.let {Nutrient.valueOf(k) to it*scale}}.toMap(),unit,
                servingSize,pack=pack.number()?.takeIf {it>0},favourite=food?.favourite ?: false,servingLabel=food?.servingLabel ?: "Serving",
                barcode=code.ifBlank {null},sourceUrl=food?.sourceUrl,labelChecked=if(perServing) emptySet() else food?.labelChecked?.filter {fields[it.name]?.number()==food.per100[it] && unit==food.unit}?.toSet() ?: emptySet()))
        },Modifier.fillMaxWidth(),enabled=valid && !busy)
    }
}

@Composable
internal fun RecipeEditor(vm:NutritionViewModel,state:NutritionState,padding:PaddingValues,onBack:()->Unit,onPick:()->Unit,onSave:(Food)->Unit) {
    val existing=vm.editingRecipe
    var name by rememberSaveable(existing?.id ?: vm.recipeDraftId) {mutableStateOf(existing?.name ?: "")}
    var servings by rememberSaveable(existing?.id ?: vm.recipeDraftId) {mutableStateOf(existing?.recipeServings?.let(::editText) ?: "")}
    var yield by rememberSaveable(existing?.id ?: vm.recipeDraftId) {mutableStateOf(existing?.yieldGrams?.let(::editText) ?: "")}
    val ingredients=vm.recipeIngredients
    var editingIngredient by remember {mutableIntStateOf(-1)}
    var ingredientAmount by remember {mutableStateOf("")}
    NutritionPage(if(existing==null) "New recipe" else "Edit recipe",padding,onBack) {
        GlassTextField(name,{name=it},"Recipe name",Modifier.fillMaxWidth())
        ContentSection {
            Caption("Ingredients")
            ingredients.forEachIndexed {i,item ->
                NutritionRow(item.food.name,"${amountText(item.amount)} ${item.food.unit.symbol}","${nutrientText(item.nutrients[Nutrient.ENERGY],Nutrient.ENERGY)}",{editingIngredient=i;ingredientAmount=editText(item.amount)})
                TextAction("Remove ${item.food.name}",{vm.recipeIngredients=ingredients.filterIndexed {index,_->index!=i}})
            }
            SecondaryButton("Add ingredient",onPick,enabled=!vm.busy)
        }
        NumberField(servings,{servings=it},"Number of servings",Modifier.fillMaxWidth())
        NumberField(yield,{yield=it},"Finished recipe weight (g)",Modifier.fillMaxWidth())
        if(yield.isBlank() && ingredients.isNotEmpty() && ingredients.all {it.food.unit==FoodUnit.GRAMS})
            TextAction("Use ingredient weight (${amountText(ingredients.sumOf {it.amount})} g)",{yield=editText(ingredients.sumOf {it.amount})})
        Text("Use the cooked weight so portions scale correctly.",style=LocalDeltaType.current.bodySmall)
        val recipe=runCatching {makeRecipe(name,ingredients,servings.number() ?: 0.0,yield.number() ?: 0.0,existing?.id ?: "preview")}.getOrNull()
        if(recipe!=null) ContentSection(tone=ModuleTone.SAGE) {
            Caption("Per serving · ${amountText(recipe.serving!!)} g")
            Nutrient.primary.forEach {NutritionRow(it.label,nutrientText(recipe.per100[it]?.times(recipe.serving/100),it))}
        }
        PrimaryButton("Save recipe",{onSave(recipe!!.copy(id=existing?.id ?: newId(),favourite=existing?.favourite ?: false))},Modifier.fillMaxWidth(),enabled=recipe!=null && !vm.busy)
    }
    ingredients.getOrNull(editingIngredient)?.let {item->
        AlertDialog(onDismissRequest={editingIngredient=-1},title={Text(item.food.name)},text={NumberField(ingredientAmount,{ingredientAmount=it},"Amount (${item.food.unit.symbol})",Modifier.fillMaxWidth())},
            confirmButton={HapticTextButton({vm.recipeIngredients=ingredients.mapIndexed {index,value->if(index==editingIngredient) value.copy(amount=ingredientAmount.number()!!) else value};editingIngredient=-1},
                enabled=ingredientAmount.number()?.let {it>0 && it<=100000}==true) {Text("Save amount")}},dismissButton={HapticTextButton({editingIngredient=-1}) {Text("Cancel")}})
    }
}

@Composable
internal fun TargetsPage(targets:NutritionTargets,padding:PaddingValues,busy:Boolean,onBack:()->Unit,onSave:(NutritionTargets)->Unit) {
    var mode by rememberSaveable {mutableStateOf(targets.mode)}
    var fields by rememberSaveable {mutableStateOf(Nutrient.entries.associate {it.name to (targets.amounts[it]?.let(::editText) ?: "")})}
    var percentages by rememberSaveable {mutableStateOf(Nutrient.macros.associate {it.name to editText(targets.percentages[it] ?: 0.0)})}
    var limits by rememberSaveable {mutableStateOf(targets.limits.map {it.name})}
    var more by rememberSaveable {mutableStateOf(false)}
    val candidate=runCatching {
        require(fields.values.all {it.isBlank() || it.number()?.let {n->n>0}==true}) {"Targets must be above zero"}
        require(mode!=MacroMode.PERCENT || percentages.values.all {it.number()!=null})
        NutritionTargets(fields.mapNotNull {(k,v)->v.number()?.let {Nutrient.valueOf(k) to it}}.toMap(),mode,
            percentages.mapNotNull {(k,v)->v.number()?.let {Nutrient.valueOf(k) to it}}.toMap(),limits.map(Nutrient::valueOf).toSet()).validated()
    }
    NutritionPage("Targets",padding,onBack) {
        NumberField(fields[Nutrient.ENERGY.name] ?: "",{fields=fields+(Nutrient.ENERGY.name to it)},"Daily energy (kcal)",Modifier.fillMaxWidth())
        SegmentedControl(MacroMode.entries,mode,{if(it==MacroMode.GRAMS) "Grams" else "% of energy"}) {mode=it}
        ContentSection {
            Caption("Macronutrients")
            Nutrient.macros.forEach {n ->
                val percent=mode==MacroMode.PERCENT
                NumberField(if(percent) percentages[n.name] ?: "" else fields[n.name] ?: "",{
                    if(percent) percentages=percentages+(n.name to it) else fields=fields+(n.name to it)
                },"${n.label} (${if(percent) "%" else "g"})",Modifier.fillMaxWidth())
                if(percent) candidate.getOrNull()?.target(n)?.let {Text("${nutrientText(it,n)} daily",style=LocalDeltaType.current.bodySmall)}
            }
            if(mode==MacroMode.PERCENT) Text("${amountText(percentages.values.sumOf {it.number() ?: 0.0})}% of 100%",style=LocalDeltaType.current.bodySmall)
        }
        ContentSection {
            Caption("Nutrient targets")
            Nutrient.entries.filter {it !in Nutrient.primary && (more || it in listOf(Nutrient.FIBRE,Nutrient.SUGAR,Nutrient.SODIUM))}.forEach {n ->
                NumberField(fields[n.name] ?: "",{fields=fields+(n.name to it)},"${n.label} (${n.unit})",Modifier.fillMaxWidth())
                if(!fields[n.name].isNullOrBlank()) SegmentedControl(listOf(false,true),n.name in limits,{if(it) "Daily limit" else "Daily goal"}) {limited -> limits=if(limited) (limits+n.name).distinct() else limits-n.name}
            }
            TextAction(if(more) "Fewer nutrients" else "All vitamins & minerals",{more=!more})
        }
        if(candidate.isFailure) Text(candidate.exceptionOrNull()?.message ?: "Check your target values",style=LocalDeltaType.current.bodySmall,color=Color(LocalDeltaColors.current.warn))
        PrimaryButton("Save targets",{onSave(candidate.getOrThrow())},Modifier.fillMaxWidth(),enabled=candidate.isSuccess && !busy)
    }
}

@Composable
internal fun CopyDiaryPage(state:NutritionState,to:LocalDate,padding:PaddingValues,busy:Boolean,onBack:()->Unit,onCopy:(LocalDate,Meal?)->Unit) {
    var sourceEpoch by rememberSaveable {mutableLongStateOf(to.minusDays(1).toEpochDay())}
    var picker by remember {mutableStateOf(false)};var selected by rememberSaveable {mutableStateOf("Whole day")}
    val source=LocalDate.ofEpochDay(sourceEpoch)
    val meal=Meal.entries.firstOrNull {it.label==selected}
    val entries=state.entries.filter {!it.fromHealthConnect && it.day==source && (meal==null || it.meal==meal)}
    NutritionPage("Copy diary",padding,onBack) {
        Text("To ${to.format(dayFormat)}",style=LocalDeltaType.current.title)
        SecondaryButton("From ${source.format(dayFormat)}",{picker=true},Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            (listOf("Whole day")+Meal.entries.map {it.label}).forEach {label->FilterChip(selected==label,{selected=label},label={Text(label)},interactionSource=rememberHapticInteractionSource())}
        }
        ContentSection {
            Text("${entries.size} entries",style=LocalDeltaType.current.title)
            entries.forEach {NutritionRow(it.food.name,"${amountText(it.amount)} ${it.food.unit.symbol}",it.meal.label)}
        }
        PrimaryButton("Copy ${entries.size} ${if(entries.size==1) "entry" else "entries"}",{onCopy(source,meal)},Modifier.fillMaxWidth(),enabled=!busy && entries.isNotEmpty() && source!=to)
    }
    if(picker) NutritionDatePicker(source,{picker=false},{sourceEpoch=it.toEpochDay()})
}
