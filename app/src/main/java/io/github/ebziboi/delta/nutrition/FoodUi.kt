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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.ebziboi.delta.ui.*
import java.time.LocalDate

@Composable
internal fun FoodSearchPage(vm: NutritionViewModel,state: NutritionState,padding: PaddingValues,onBack:()->Unit,onFood:(Food)->Unit,
                            onCustom:()->Unit,onRecipe:()->Unit,onPhoto:()->Unit,ingredientsOnly:Boolean=false,onImport:((String)->Unit)?=null) {
    var query by rememberSaveable { mutableStateOf("") };var category by rememberSaveable { mutableStateOf("Recent") }
    val context=LocalContext.current
    var storeMenu by remember {mutableStateOf(false)}
    var barcodeImage by rememberSaveable {mutableStateOf<String?>(null)}
    val barcodePicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri->if(uri!=null) {
        runCatching {context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)};barcodeImage=uri.toString()
    }}
    val scanner=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==android.app.Activity.RESULT_OK) result.data?.getStringExtra("barcode")?.let {vm.barcode(it,onFood,onImport)}
    }
    val history=state.entries.filterNot {it.fromHealthConnect}.sortedByDescending {it.created}.distinctBy {it.food.id}.map { e -> state.foods.find {it.id==e.food.id} ?: e.food }
    NutritionPage(if(ingredientsOnly) "Ingredient" else "Add food",padding,onBack) {
        SegmentedControl(listOf("Recent","Search","My foods","Recipes"),category,{it}) {category=it}
        if(category=="Search") {
            Box {
                SecondaryButton(vm.selectedStore?.let {id->vm.storeOrder.firstOrNull {it.id==id}?.label} ?: "All stores",{storeMenu=true},enabled=!vm.busy)
                androidx.compose.material3.DropdownMenu(storeMenu,{storeMenu=false}) {
                    androidx.compose.material3.DropdownMenuItem({Text("All stores")},{vm.chooseStore(null);storeMenu=false})
                    vm.storeOrder.forEach {store->androidx.compose.material3.DropdownMenuItem({Text(store.label)},{vm.chooseStore(store.id);storeMenu=false})}
                }
            }
            GlassTextField(query,{query=it},"Search foods",Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Search",{vm.search(query)},Modifier.weight(1f),enabled=!vm.busy && query.trim().length>=2)
                SecondaryButton("Scan",{runCatching {scanner.launch(Intent(context,BarcodeActivity::class.java))}.onFailure {vm.message="Camera unavailable. Search by name instead."}},enabled=!vm.busy)
            }
            Text("Open Food Facts · ODbL",style=LocalDeltaType.current.bodySmall,color=Color(LocalDeltaColors.current.textDim))
            val uri=androidx.compose.ui.platform.LocalUriHandler.current
            TextAction("Database & licence",{uri.openUri("https://world.openfoodfacts.org/terms-of-use")})
            if(vm.searched && vm.results.isEmpty()) Text("No foods found. Try another name or create your own food.")
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(category!="Search") SecondaryButton("Scan barcode",{scanner.launch(Intent(context,BarcodeActivity::class.java))},enabled=!vm.busy)
            SecondaryButton("Barcode photo",{barcodePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!vm.busy)
            onImport?.let {SecondaryButton("Import food",{it("")},enabled=!vm.busy)}
            SecondaryButton("Custom food",onCustom,enabled=!vm.busy)
            if(!ingredientsOnly) {SecondaryButton("New recipe",onRecipe,enabled=!vm.busy);SecondaryButton("Photo",onPhoto,enabled=!vm.busy)}
        }
        val foods=when(category) {
            "Search" -> vm.results
            "My foods" -> state.foods.filter {it.source==FoodSource.CUSTOM || it.favourite}.sortedByDescending {it.favourite}
            "Recipes" -> state.foods.filter {it.source==FoodSource.RECIPE}
            else -> history
        }
        if(foods.isEmpty() && category!="Search") ContentSection { Text(when(category) {
            "Recipes" -> "Your saved recipes will appear here."
            "My foods" -> "Create a food or save a favourite."
            else -> "Recently logged foods will appear here."
        },style=LocalDeltaType.current.body) }
        foods.forEach { food ->
            ContentSection(onClick={if(!vm.busy) onFood(food)}) {
                Text(food.name,style=LocalDeltaType.current.title)
                Text(food.subtitle,style=LocalDeltaType.current.bodySmall,color=Color(LocalDeltaColors.current.textDim))
                Text("${nutrientText(food.per100[Nutrient.ENERGY],Nutrient.ENERGY)} / 100 ${food.unit.symbol}",style=LocalDeltaType.current.number)
            }
        }
    }
    barcodeImage?.let {image->LabelCropper(android.net.Uri.parse(image),{barcodeImage=null},{crop->barcodeImage=null;vm.barcodePhoto(android.net.Uri.parse(image),crop,onFood,onImport)},title="Barcode photo",actionLabel="Read barcode")}

}

@Composable
internal fun PortionPage(food:Food,entry:DiaryEntry?,day:LocalDate,meal:Meal,padding:PaddingValues,busy:Boolean,onBack:()->Unit,
                         onSave:(Double,Meal)->Unit,onDelete:(()->Unit)?,onFavourite:()->Unit,onEdit:(()->Unit)?,onDeleteFood:(()->Unit)?,isIngredient:Boolean=false) {
    val options=listOfNotNull(food.unit.symbol,"Serving".takeIf {food.serving!=null},"Pack".takeIf {food.pack!=null})
    var portion by rememberSaveable(food.id,entry?.id) { mutableStateOf(if(entry!=null) food.unit.symbol else if(food.serving!=null) "Serving" else food.unit.symbol) }
    var quantity by rememberSaveable(food.id,entry?.id) { mutableStateOf(entry?.amount?.let(::editText) ?: if(food.serving!=null) "1" else "100") }
    var selectedMeal by rememberSaveable(entry?.id) {mutableStateOf(entry?.meal ?: meal)}
    var confirmDelete by remember { mutableStateOf(false) }
    val factor=when(portion) {"Serving" -> food.serving ?: 1.0;"Pack" -> food.pack ?: 1.0;else -> 1.0}
    val amount=quantity.number()?.times(factor)?.takeIf {it.isFinite() && it>0 && it<=100000}
    NutritionPage(if(entry==null) "Portion" else "Edit entry",padding,onBack) {
        ContentSection(tone=ModuleTone.SAGE) {
            Text(food.name,style=LocalDeltaType.current.title);Text(food.subtitle,style=LocalDeltaType.current.bodySmall)
            if(food.estimated) StatusPill("Estimated",Color(LocalDeltaColors.current.warn))
            Text(day.format(dayFormat),style=LocalDeltaType.current.bodySmall)
        }
        if(options.size>1) SegmentedControl(options,portion,{it}) { next ->
            val oldAmount=amount
            portion=next
            val nextFactor=when(next) {"Serving" -> food.serving!!;"Pack" -> food.pack!!;else -> 1.0}
            quantity=oldAmount?.let { editText(it/nextFactor) } ?: "1"
        }
        NumberField(quantity,{quantity=it},if(portion==food.unit.symbol) "Amount (${food.unit.symbol})" else "Number of ${portion.lowercase()}s",Modifier.fillMaxWidth())
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
        food.sourceUrl?.let {url->
            val uri=androidx.compose.ui.platform.LocalUriHandler.current
            TextAction("View product source",{uri.openUri(url)})
        }
        if(food.labelChecked.isNotEmpty()) Text("${food.labelChecked.size} values read from the package label",style=LocalDeltaType.current.bodySmall)
        if(food.source==FoodSource.OPEN_FOOD_FACTS) {
            val uri=androidx.compose.ui.platform.LocalUriHandler.current
            TextAction("View label on Open Food Facts",{uri.openUri("https://world.openfoodfacts.org/product/${food.barcode}")})
        }
        onDelete?.let { TextAction("Delete entry",{confirmDelete=true}) }
        onDeleteFood?.let { TextAction("Delete from library",{confirmDelete=true}) }
    }
    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text(if(onDelete!=null) "Delete this entry?" else "Delete this saved food?")},text={if(onDeleteFood!=null) Text("Past diary entries will be kept.")},confirmButton={TextButton({confirmDelete=false;(onDelete ?: onDeleteFood)?.invoke()}){Text("Delete")}},dismissButton={TextButton({confirmDelete=false}){Text("Cancel")}})
}

@Composable
internal fun FoodEditor(food:Food?,padding:PaddingValues,busy:Boolean,onBack:()->Unit,onSave:(Food)->Unit) {
    var name by rememberSaveable(food?.id) {mutableStateOf(food?.name ?: "")}
    var brand by rememberSaveable(food?.id) {mutableStateOf(food?.brand ?: "")}
    var unit by rememberSaveable(food?.id) {mutableStateOf(food?.unit ?: FoodUnit.GRAMS)}
    var serving by rememberSaveable(food?.id) {mutableStateOf(food?.serving?.let(::editText) ?: "")}
    var pack by rememberSaveable(food?.id) {mutableStateOf(food?.pack?.let(::editText) ?: "")}
    var fields by rememberSaveable(food?.id) {mutableStateOf(Nutrient.entries.associate {it.name to (food?.per100?.get(it)?.let(::editText) ?: "")})}
    var expanded by rememberSaveable {mutableStateOf(false)}
    val valid=name.isNotBlank() && name.length<=250 && fields.all {(_,v)->v.isBlank() || v.number()!=null} &&
        listOf(serving,pack).all {it.isBlank() || (it.number()?.let {v->v>0 && v<=100000}==true)}
    NutritionPage(if(food==null) "Custom food" else "Edit food",padding,onBack) {
        GlassTextField(name,{name=it},"Food name",Modifier.fillMaxWidth());GlassTextField(brand,{brand=it},"Brand (optional)",Modifier.fillMaxWidth())
        SegmentedControl(FoodUnit.entries,unit,{"100 ${it.symbol}"}) {unit=it}
        ContentSection {
            Caption("Per 100 ${unit.symbol}")
            Text("Leave values blank when the label does not supply them.",style=LocalDeltaType.current.bodySmall)
            Nutrient.entries.filter {expanded || it in Nutrient.primary + listOf(Nutrient.FIBRE,Nutrient.SUGAR,Nutrient.SALT)}.forEach {n ->
                NumberField(fields[n.name] ?: "",{s->fields=fields+(n.name to s)},"${n.label} (${n.unit})",Modifier.fillMaxWidth())
            }
            TextAction(if(expanded) "Fewer nutrients" else "Vitamins, minerals & more",{expanded=!expanded})
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            NumberField(serving,{serving=it},"Serving (${unit.symbol})",Modifier.weight(1f))
            NumberField(pack,{pack=it},"Pack (${unit.symbol})",Modifier.weight(1f))
        }
        PrimaryButton("Save food",{
            onSave(Food(food?.id ?: newId(),name.trim(),brand.trim().take(250),fields.mapNotNull {(k,v)->v.takeIf {it.isNotBlank()}?.number()?.let {Nutrient.valueOf(k) to it}}.toMap(),unit,
                serving.number()?.takeIf {it>0},pack=pack.number()?.takeIf {it>0},favourite=food?.favourite ?: false,
                barcode=food?.barcode,sourceUrl=food?.sourceUrl,labelChecked=food?.labelChecked?.filter {fields[it.name]?.number()==food.per100[it] && unit==food.unit}?.toSet() ?: emptySet()))
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
            confirmButton={TextButton({vm.recipeIngredients=ingredients.mapIndexed {index,value->if(index==editingIngredient) value.copy(amount=ingredientAmount.number()!!) else value};editingIngredient=-1},
                enabled=ingredientAmount.number()?.let {it>0 && it<=100000}==true) {Text("Save amount")}},dismissButton={TextButton({editingIngredient=-1}) {Text("Cancel")}})
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
            (listOf("Whole day")+Meal.entries.map {it.label}).forEach {label->FilterChip(selected==label,{selected=label},label={Text(label)})}
        }
        ContentSection {
            Text("${entries.size} entries",style=LocalDeltaType.current.title)
            entries.forEach {NutritionRow(it.food.name,"${amountText(it.amount)} ${it.food.unit.symbol}",it.meal.label)}
        }
        PrimaryButton("Copy ${entries.size} ${if(entries.size==1) "entry" else "entries"}",{onCopy(source,meal)},Modifier.fillMaxWidth(),enabled=!busy && entries.isNotEmpty() && source!=to)
    }
    if(picker) NutritionDatePicker(source,{picker=false},{sourceEpoch=it.toEpochDay()})
}
