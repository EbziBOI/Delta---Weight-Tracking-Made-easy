package io.github.ebziboi.delta.nutrition

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ebziboi.delta.ui.*
import io.github.ebziboi.delta.data.WeightEntry
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun amountText(value: Double): String = java.text.DecimalFormat(if(kotlin.math.abs(value)<1 && value!=0.0) "0.########" else "0.#").format(value)
internal fun editText(value: Double): String = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
internal fun String.number(): Double? = trim().replace(',','.').toDoubleOrNull()?.takeIf { it.isFinite() && it>=0 && it<=1e7 }
internal fun nutrientText(value: Double?,n: Nutrient) = value?.let { "${amountText(it)} ${n.unit}" } ?: "—"
internal val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

@Composable
internal fun NutritionPage(title: String,padding: PaddingValues,onBack: (() -> Unit)? = null,content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=20.dp)
        .padding(top=padding.calculateTopPadding(),bottom=padding.calculateBottomPadding()+16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        ScreenHeader(title,leading=onBack?.let { { GlassIconButton(io.github.ebziboi.delta.R.drawable.ic_arrow_back,"Back",it) } })
        content()
    }
}
@Composable
internal fun NumberField(value: String,onValueChange: (String)->Unit,label: String,modifier: Modifier=Modifier) {
    val c=LocalDeltaColors.current
    OutlinedTextField(value,onValueChange,modifier.heightIn(min=56.dp),label={Text(label)},singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),shape=RoundedCornerShape(Radii.control),
        colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color(c.text),unfocusedTextColor=Color(c.text),focusedBorderColor=Color(c.accent),
            unfocusedBorderColor=Color(c.glass.edge),focusedContainerColor=Color(c.glass.fill),unfocusedContainerColor=Color(c.glass.fill)))
}
@Composable
internal fun NutritionDatePicker(initial: LocalDate,onDismiss: ()->Unit,onDate: (LocalDate)->Unit) {
    val state=rememberDatePickerState(initialSelectedDateMillis=initial.toEpochDay()*86400000)
    DatePickerDialog(onDismissRequest=onDismiss,confirmButton={TextButton(onClick={state.selectedDateMillis?.let {onDate(LocalDate.ofEpochDay(it/86400000))};onDismiss()}) {Text("Choose")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}}) { DatePicker(state) }
}
@Composable
internal fun NutritionRow(title: String,value: String,subtitle: String?=null,onClick: (() -> Unit)?=null) {
    ListRow(onClick=onClick) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,style=LocalDeltaType.current.body)
            subtitle?.let { Text(it,style=LocalDeltaType.current.bodySmall,color=Color(LocalDeltaColors.current.textDim)) }
        }
        Text(value,modifier=Modifier.widthIn(max=150.dp),maxLines=2,textAlign=androidx.compose.ui.text.style.TextAlign.End,style=LocalDeltaType.current.number,color=Color(LocalDeltaColors.current.accent))
    }
}
@Composable
internal fun NutrientProgress(n: Nutrient,total: NutrientTotal,target: Double?,limit: Boolean=false) {
    val c=LocalDeltaColors.current
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(n.label,style=LocalDeltaType.current.bodySmall,modifier=Modifier.weight(1f))
            Text((if(total.known) nutrientText(total.amount,n) else "Unknown")+(if(total.partial && total.reported>0) "*" else "")+
                (target?.let { " / ${nutrientText(it,n)}${if(limit) " max" else ""}" } ?: ""),style=LocalDeltaType.current.bodySmall)
        }
        if(target!=null) ProgressRail(if(total.known && target>0) (total.amount/target).toFloat() else 0f,height=5.dp)
        if(total.partial) Text("${total.reported} of ${total.count} foods report ${n.label.lowercase()}",style=LocalDeltaType.current.bodySmall,color=Color(c.textDim))
    }
}

@Composable
fun NutritionScreen(padding: PaddingValues,addSignal: Int=0,onDetailChanged: (Boolean)->Unit={},weights: List<WeightEntry> = emptyList(),
                    initialRoute: String="diary",weightUnit: io.github.ebziboi.delta.data.WeightUnit=io.github.ebziboi.delta.data.WeightUnit.KG,vm: NutritionViewModel=viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf(initialRoute) }
    var parent by rememberSaveable { mutableStateOf("diary") }
    var dayEpoch by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var meal by rememberSaveable { mutableStateOf(Meal.BREAKFAST) }
    var datePicker by remember { mutableStateOf(false) }
    val routeState=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val day=LocalDate.ofEpochDay(dayEpoch)
    val back: ()->Unit = {
        if(vm.busy) vm.cancel()
        route=when(route) { "portion" -> parent;"food-edit" -> if(parent=="ingredient-search") "ingredient-search" else "search";"recipe" -> "search";"search" -> "diary";"ingredient-search" -> "recipe";"photo-settings" -> "photo";else -> "diary" }
        vm.message=null
    }
    val openFood: (Food,String)->Unit={f,p -> vm.selectedFood=f;vm.selectedEntry=null;parent=p;route="portion"}
    LaunchedEffect(route) { onDetailChanged(route!="diary") }
    DisposableEffect(Unit) { onDispose { onDetailChanged(false) } }
    var handledAddSignal by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(addSignal) { if(addSignal>handledAddSignal) { handledAddSignal=addSignal;route="search";parent="diary" } }
    BackHandler(route!="diary",onBack=back)
    Column(Modifier.fillMaxSize()) {
        vm.message?.let { msg ->
            Surface(color=Color(LocalDeltaColors.current.surfaceHigh),modifier=Modifier.statusBarsPadding().fillMaxWidth()) {
                Row(Modifier.padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(msg,modifier=Modifier.weight(1f),style=LocalDeltaType.current.bodySmall)
                    TextAction("Dismiss",{vm.message=null})
                }
            }
        }
        if(vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth(),color=Color(LocalDeltaColors.current.accent),trackColor=Color.Transparent)
        if(!state.loaded) { Box(Modifier.fillMaxSize().padding(30.dp)) { ShimmerPlaceholder() } }
        else if(state.loadError!=null) NutritionPage("Diary",padding) {
            Text(state.loadError!!);PrimaryButton("Try again",{vm.run { vm.repo.reload() }})
        }
        else routeState.SaveableStateProvider(when(route) {
            "portion" -> "portion:${vm.selectedFood?.id}:${vm.selectedEntry?.id ?: "new"}:$parent:$dayEpoch:${meal.name}"
            "food-edit" -> "food-edit:${vm.selectedFood?.id ?: vm.foodDraftId}"
            "recipe" -> "recipe:${vm.editingRecipe?.id ?: vm.recipeDraftId}"
            else -> route
        }) { when(route) {
            "diary" -> DiaryPage(state,day,meal,padding,{dayEpoch=it.toEpochDay()},{meal=it},
                onAdd={meal=it;route="search";parent="diary"},onEntry={vm.selectedEntry=it;vm.selectedFood=it.food;parent="diary";route="portion"},
                onRoute={route=it},onDate={datePicker=true},onRepeat={e->vm.run {vm.repo.log(listOf(e.copy(id=newId(),day=day,meal=meal,created=System.currentTimeMillis())))}})
            "search" -> FoodSearchPage(vm,state,padding,back,{openFood(it,"search")},
                onCustom={vm.selectedFood=null;vm.foodDraftId=newId();parent="search";route="food-edit"},
                onRecipe={vm.recipeIngredients=emptyList();vm.editingRecipe=null;vm.recipeDraftId=newId();route="recipe"},onPhoto={route="photo"})
            "portion" -> vm.selectedFood?.let { food -> PortionPage(food,vm.selectedEntry,day,meal,padding,vm.busy,back,
                isIngredient=parent=="recipe",onSave={amount,m -> vm.run {
                    val e=vm.selectedEntry
                    if(parent=="recipe") { vm.recipeIngredients=vm.recipeIngredients+Ingredient(food,amount);route="recipe" }
                    else if(e!=null) { vm.repo.updateEntry(e.copy(amount=amount,meal=m));route="diary" }
                    else { vm.repo.log(listOf(DiaryEntry(day=day,meal=m,food=food,amount=amount)));route="diary" }
                } },onDelete=vm.selectedEntry?.let {e -> {vm.run {vm.repo.deleteEntry(e.id);route="diary"}} },
                onFavourite={vm.run {val current=state.foods.find {it.id==food.id} ?: food;vm.repo.saveFood(current.copy(favourite=!food.favourite));vm.selectedFood=food.copy(favourite=!food.favourite)}},
                onEdit=if(parent!="recipe" && food.source==FoodSource.CUSTOM) ({vm.selectedFood=state.foods.find {it.id==food.id} ?: food;parent="search";route="food-edit"}) else if(parent!="recipe" && food.source==FoodSource.RECIPE) ({val current=state.foods.find {it.id==food.id} ?: food;vm.editingRecipe=current;vm.recipeIngredients=current.ingredients;route="recipe"}) else null,
                onDeleteFood=if(parent!="recipe" && vm.selectedEntry==null && food.source in listOf(FoodSource.CUSTOM,FoodSource.RECIPE)) ({vm.run {vm.repo.deleteFood(food.id);route="search"}}) else null) } ?: LaunchedEffect(Unit) {route="search"}
            "food-edit" -> FoodEditor(vm.selectedFood?.takeIf { it.source==FoodSource.CUSTOM },padding,vm.busy,back) {food -> vm.run {vm.repo.saveFood(food);openFood(food,if(parent=="ingredient-search") "recipe" else "search")} }
            "recipe" -> RecipeEditor(vm,state,padding,back,onPick={route="ingredient-search"},onSave={f -> vm.run {vm.repo.saveFood(f);openFood(f,"search")} })
            "ingredient-search" -> FoodSearchPage(vm,state,padding,{route="recipe"},{openFood(it,"recipe")},onCustom={vm.selectedFood=null;vm.foodDraftId=newId();parent="ingredient-search";route="food-edit"},onRecipe={route="recipe"},onPhoto={route="photo"},ingredientsOnly=true)
            "targets" -> TargetsPage(state.targets,padding,vm.busy,back) { targets -> vm.run {vm.repo.saveTargets(targets);route="diary"} }
            "copy" -> CopyDiaryPage(state,day,padding,vm.busy,back) { from,m -> vm.run {vm.repo.copy(from,day,m);route="diary"} }
            "charts" -> NutritionChartsPage(state,weights,padding,back,weightUnit)
            "fasting" -> FastingPage(vm,state,padding,back)
            "photo" -> PhotoPage(vm,padding,back,onSettings={route="photo-settings"},onLog={m -> vm.run {
                vm.repo.log(vm.photoReview.map { DiaryEntry(day=day,meal=m,food=it.food,amount=it.amount) });vm.clearPhoto();route="diary"
            }},meal=meal)
            "photo-settings" -> PhotoSettingsPage(vm,padding,{route=if(initialRoute=="photo-settings") "diary" else "photo"})
        }
    }
    }
    if(datePicker) NutritionDatePicker(day,{datePicker=false},{dayEpoch=it.toEpochDay()})
}

@Composable
private fun DiaryPage(state: NutritionState,day: LocalDate,meal: Meal,padding: PaddingValues,onDay: (LocalDate)->Unit,onMeal: (Meal)->Unit,
                      onAdd:(Meal)->Unit,onEntry:(DiaryEntry)->Unit,onRoute:(String)->Unit,onDate:()->Unit,onRepeat:(DiaryEntry)->Unit) {
    val entries=state.entries.filter {it.day==day};val energy=entries.total(Nutrient.ENERGY);val target=state.targets.target(Nutrient.ENERGY)
    NutritionPage("Diary",padding) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
            TextButton({onDay(day.minusDays(1))}) { Text("‹",fontSize=26.sp) }
            TextButton(onDate) { Text(if(day==LocalDate.now()) "Today" else day.format(DateTimeFormatter.ofPattern("d MMM yyyy")),style=LocalDeltaType.current.title) }
            TextButton({onDay(day.plusDays(1))},enabled=day<LocalDate.now()) { Text("›",fontSize=26.sp) }
        }
        ContentSection(tone=ModuleTone.SAGE,framed=true,onClick={onRoute("targets")}) {
            Caption("Daily nutrition")
            ValueWithUnit(if(energy.known) amountText(energy.amount) else "—","kcal",style=LocalDeltaType.current.numberLarge.copy(fontSize=40.sp,lineHeight=44.sp))
            target?.let { Text(if(energy.partial) "Energy totals are incomplete" else "${amountText((it-energy.amount).coerceAtLeast(0.0))} remaining",style=LocalDeltaType.current.bodySmall) }
            if(entries.isEmpty()) Text("No food logged",style=LocalDeltaType.current.bodySmall)
            target?.let {
                Text("${amountText(it)} kcal target",style=LocalDeltaType.current.bodySmall)
                ProgressRail((energy.amount/it).toFloat(),height=6.dp)
            }
            if(energy.partial) Text("${energy.reported} of ${energy.count} foods report energy",style=LocalDeltaType.current.bodySmall)
            Nutrient.macros.forEach { NutrientProgress(it,entries.total(it),state.targets.target(it)) }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Charts" to "charts","Fasting" to "fasting","Targets" to "targets","Copy day" to "copy").forEach {(label,destination)->
                Box(Modifier.weight(1f).heightIn(min=48.dp).refractiveGlass(RoundedCornerShape(Radii.control))
                    .clickable(role=androidx.compose.ui.semantics.Role.Button) {onRoute(destination)}.padding(horizontal=6.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
                    Text(label,style=LocalDeltaType.current.bodySmall,maxLines=1)
                }
            }
        }
        val recent=state.entries.sortedByDescending {it.created}.distinctBy {it.food.id}.take(4)
        if(recent.isNotEmpty()) ContentSection {
            Caption("Repeat · ${meal.label}")
            SegmentedControl(Meal.entries,meal,{it.label},onSelect=onMeal)
            recent.forEach {e-> NutritionRow(e.food.name,"+", "${amountText(e.amount)} ${e.food.unit.symbol}",{onRepeat(e)}) }
        }
        Meal.entries.forEach { m ->
            val items=entries.filter {it.meal==m};val total=items.total(Nutrient.ENERGY)
            ContentSection {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Text(m.label,style=LocalDeltaType.current.title)
                    TextAction("+ Add",{onMeal(m);onAdd(m)})
                }
                if(items.isEmpty()) Text("No entries",style=LocalDeltaType.current.bodySmall,color=Color(LocalDeltaColors.current.textDim))
                items.forEach { e -> NutritionRow(e.food.name,nutrientText(e.nutrients[Nutrient.ENERGY],Nutrient.ENERGY),
                    "${amountText(e.amount)} ${e.food.unit.symbol}"+(if(e.food.estimated) " · Estimated" else ""),{onEntry(e)}) }
                if(items.isNotEmpty()) Text("Meal total · ${if(total.known) nutrientText(total.amount,Nutrient.ENERGY) else "Unknown"}${if(total.partial) " · partial" else ""}",style=LocalDeltaType.current.bodySmall)
            }
        }
        val others=Nutrient.entries.filter {it !in Nutrient.primary && (state.targets.target(it)!=null || entries.any {e -> it in e.food.per100})}
        if(others.isNotEmpty()) ContentSection {
            Caption("Nutrients")
            others.forEach { NutrientProgress(it,entries.total(it),state.targets.target(it),it in state.targets.limits) }
        }
    }
}

@Composable
fun NutritionSettingsEntry(page:String,padding:PaddingValues,onBack:()->Unit,vm:NutritionViewModel=viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column {
        vm.message?.let { Text(it,Modifier.statusBarsPadding().padding(20.dp));TextAction("Dismiss",{vm.message=null}) }
        if(page=="Photo logging") PhotoSettingsPage(vm,padding,onBack)
        else if(state.loaded && state.loadError==null) TargetsPage(state.targets,padding,vm.busy,onBack) {t->vm.run {vm.repo.saveTargets(t);onBack()} }
        else NutritionPage("Nutrition",padding,onBack) {Text(state.loadError ?: "Loading nutrition targets…")}
    }
}
