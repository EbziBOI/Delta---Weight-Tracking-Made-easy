package io.github.ebziboi.delta.nutrition

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
    BackHandler(enabled=onBack!=null) {onBack?.invoke()}
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=20.dp)
        .padding(top=padding.calculateTopPadding(),bottom=padding.calculateBottomPadding()+16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        ScreenHeader(title)
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
    DatePickerDialog(onDismissRequest=onDismiss,confirmButton={HapticTextButton(onClick={state.selectedDateMillis?.let {onDate(LocalDate.ofEpochDay(it/86400000))};onDismiss()}) {Text("Choose")}},dismissButton={HapticTextButton(onClick=onDismiss){Text("Cancel")}}) { DatePicker(state,modifier=Modifier.hapticPickerTaps()) }
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
                    initialRoute: String="diary",openRoute:String="diary",openSignal:Long=0,weightUnit: io.github.ebziboi.delta.data.WeightUnit=io.github.ebziboi.delta.data.WeightUnit.KG,vm: NutritionViewModel=viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf(initialRoute) }
    var parent by rememberSaveable { mutableStateOf("diary") }
    var importParent by rememberSaveable {mutableStateOf("search")}
    var importCode by rememberSaveable {mutableStateOf("")}
    var importId by rememberSaveable {mutableStateOf(newId())}
    var dayEpoch by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var meal by rememberSaveable { mutableStateOf(mealForNow()) }
    var editorBarcode by rememberSaveable { mutableStateOf<String?>(null) }
    var editorTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var datePicker by remember { mutableStateOf(false) }
    val routeState=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val day=LocalDate.ofEpochDay(dayEpoch)
    val back: ()->Unit = {
        if(vm.busy) vm.cancel()
        route=when(route) { "food-import" -> importParent;"portion" -> parent;"food-edit" -> if(parent=="ingredient-search") "ingredient-search" else "search";"recipe" -> "search";"search" -> "diary";"ingredient-search" -> "recipe";"photo-settings" -> "photo";else -> "diary" }
        vm.message=null
    }
    fun openImport(code:String,from:String) {importCode=code;importParent=from;importId=newId();route="food-import"}
    /** Manual label entry: for a product missing from the database, or one it lists without nutrition. */
    fun openManual(draft:Food?,code:String?,from:String,title:String) {
        vm.selectedFood=draft?.copy(id=newId(),source=FoodSource.CUSTOM,labelChecked=emptySet(),stores=emptyList());vm.foodDraftId=newId()
        editorBarcode=code ?: draft?.barcode;editorTitle=title;parent=from;route="food-edit"
    }
    fun quickAdd(food:Food) {
        val entry=DiaryEntry(day=day,meal=meal,food=food,amount=food.serving ?: 100.0)
        vm.run {vm.repo.log(listOf(entry));vm.offerUndo("Added ${food.name} to ${meal.label}") {vm.repo.deleteEntry(entry.id)}}
    }
    val openFood: (Food,String)->Unit={f,p -> vm.selectedFood=f;vm.selectedEntry=null;parent=p;route="portion"}
    LaunchedEffect(route) { onDetailChanged(route!="diary") }
    DisposableEffect(Unit) { onDispose { onDetailChanged(false) } }
    var handledOpenSignal by rememberSaveable {mutableLongStateOf(0)}
    LaunchedEffect(openSignal) {if(openSignal!=0L && openSignal!=handledOpenSignal) {handledOpenSignal=openSignal;route=openRoute;parent="diary"}}
    var handledAddSignal by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(addSignal) { if(addSignal>handledAddSignal) { handledAddSignal=addSignal;route="search";parent="diary" } }
    BackHandler(route!="diary",onBack=back)
    val chartSettings by (LocalContext.current.applicationContext as io.github.ebziboi.delta.DeltaApp).repo.settings.collectAsStateWithLifecycle(initialValue=io.github.ebziboi.delta.data.AppSettings())
    Box(Modifier.fillMaxSize()) {
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
            "food-import" -> "food-import:$importId"
            "portion" -> "portion:${vm.selectedFood?.id}:${vm.selectedEntry?.id ?: "new"}:$parent:$dayEpoch:${meal.name}"
            "food-edit" -> "food-edit:${vm.selectedFood?.id ?: vm.foodDraftId}"
            "recipe" -> "recipe:${vm.editingRecipe?.id ?: vm.recipeDraftId}"
            else -> route
        }) { when(route) {
            "diary" -> DiaryPage(state,day,meal,padding,{dayEpoch=it.toEpochDay()},{meal=it},
                onAdd={meal=it;route="search";parent="diary"},onEntry={vm.selectedEntry=it;vm.selectedFood=it.food;parent="diary";route=if(it.fromHealthConnect) "health-entry" else "portion"},
                onRoute={route=it},onDate={datePicker=true},onRepeat={e->
                    val copy=e.copy(id=newId(),day=day,meal=meal,created=System.currentTimeMillis())
                    vm.run {vm.repo.log(listOf(copy));vm.offerUndo("Added ${e.food.name} to ${meal.label}") {vm.repo.deleteEntry(copy.id)}}
                },onDelete={e->vm.run {vm.repo.deleteEntry(e.id);vm.offerUndo("Deleted ${e.food.name}") {vm.repo.log(listOf(e))}}})
            "search" -> FoodSearchPage(vm,state,padding,back,{openFood(it,"search")},
                onCustom={editorBarcode=null;editorTitle=null;vm.selectedFood=null;vm.foodDraftId=newId();parent="search";route="food-edit"},
                onRecipe={vm.recipeIngredients=emptyList();vm.editingRecipe=null;vm.recipeDraftId=newId();route="recipe"},onPhoto={route="photo"},onImport={openImport(it,"search")},
                onMissing={code->openManual(null,code,"search","New product")},onIncomplete={f->openManual(f,null,"search","Add nutrition")},
                onQuickAdd={quickAdd(it)},mealLabel="${meal.label} · ${dayTitle(day)}",meal=meal,onMeal={meal=it})
            "food-import" -> FoodImportPage(vm,importCode,padding,back) {food->vm.run {vm.repo.saveFood(food);openFood(food,if(importParent=="ingredient-search") "recipe" else "search")}}
            "portion" -> vm.selectedFood?.let { food -> PortionPage(food,vm.selectedEntry,day,meal,padding,vm.busy,back,
                isIngredient=parent=="recipe",onSave={amount,m -> vm.run {
                    val e=vm.selectedEntry
                    if(parent=="recipe") { vm.recipeIngredients=vm.recipeIngredients+Ingredient(food,amount);route="recipe" }
                    else if(e!=null) { vm.repo.updateEntry(e.copy(amount=amount,meal=m));route="diary" }
                    else { vm.repo.log(listOf(DiaryEntry(day=day,meal=m,food=food,amount=amount)));route="diary" }
                } },onDelete=vm.selectedEntry?.let {e -> {vm.run {vm.repo.deleteEntry(e.id);route="diary"}} },
                onFavourite={vm.run {val current=state.foods.find {it.id==food.id} ?: food;vm.repo.saveFood(current.copy(favourite=!food.favourite));vm.selectedFood=food.copy(favourite=!food.favourite)}},
                onFillNutrition=if(food.missingNutrition && parent!="recipe") ({openManual(food,null,"search","Add nutrition")}) else null,
                onEditCopy=if(parent!="recipe" && food.source in listOf(FoodSource.RESTAURANT,FoodSource.OPEN_FOOD_FACTS) && !food.missingNutrition) ({openManual(food,null,"search","Edit a copy")}) else null,
                onEdit=if(parent!="recipe" && food.source==FoodSource.CUSTOM) ({editorBarcode=null;editorTitle=null;vm.selectedFood=state.foods.find {it.id==food.id} ?: food;parent="search";route="food-edit"}) else if(parent!="recipe" && food.source==FoodSource.RECIPE) ({val current=state.foods.find {it.id==food.id} ?: food;vm.editingRecipe=current;vm.recipeIngredients=current.ingredients;route="recipe"}) else null,
                onDeleteFood=if(parent!="recipe" && vm.selectedEntry==null && food.source in listOf(FoodSource.CUSTOM,FoodSource.RECIPE)) ({vm.run {vm.repo.deleteFood(food.id);route="search"}}) else null) } ?: LaunchedEffect(Unit) {route="search"}
            "food-edit" -> FoodEditor(vm.selectedFood?.takeIf { it.source==FoodSource.CUSTOM },padding,vm.busy,back,barcode=editorBarcode,
                title=editorTitle ?: if(vm.selectedFood?.let {f->state.foods.any {it.id==f.id}}==true) "Edit food" else "Custom food",
                onReadLabel=editorBarcode?.let {code->{openImport(code,if(parent=="ingredient-search") "ingredient-search" else "search")}}) {food -> vm.run {vm.repo.saveFood(food);editorBarcode=null;editorTitle=null;openFood(food,if(parent=="ingredient-search") "recipe" else "search")} }
            "recipe" -> RecipeEditor(vm,state,padding,back,onPick={route="ingredient-search"},onSave={f -> vm.run {vm.repo.saveFood(f);openFood(f,"search")} })
            "ingredient-search" -> FoodSearchPage(vm,state,padding,{route="recipe"},{openFood(it,"recipe")},onCustom={editorBarcode=null;editorTitle=null;vm.selectedFood=null;vm.foodDraftId=newId();parent="ingredient-search";route="food-edit"},onRecipe={route="recipe"},onPhoto={route="photo"},ingredientsOnly=true,onImport={openImport(it,"ingredient-search")},
                onMissing={code->openManual(null,code,"ingredient-search","New product")},onIncomplete={f->openManual(f,null,"ingredient-search","Add nutrition")})
            "targets" -> TargetsPage(state.targets,padding,vm.busy,back) { targets -> vm.run {vm.repo.saveTargets(targets);route="diary"} }
            "copy" -> CopyDiaryPage(state,day,padding,vm.busy,back) { from,m -> vm.run {vm.repo.copy(from,day,m);route="diary"} }
            "health-entry" -> vm.selectedEntry?.let {HealthFoodPage(it,padding,back)}
            "charts" -> NutritionChartsPage(state,weights,padding,back,weightUnit,chartSettings.startEpochDay?.let(LocalDate::ofEpochDay),chartSettings.weekStartsOn)
            "fasting" -> FastingPage(vm,state,padding,back)
            "photo" -> PhotoPage(vm,padding,back,onSettings={route="photo-settings"},onLog={m -> vm.run {
                vm.repo.log(vm.photoReview.map { DiaryEntry(day=day,meal=m,food=it.food,amount=it.amount) });vm.clearPhoto();route="diary"
            }},meal=meal)
            "photo-settings" -> PhotoSettingsPage(vm,padding,{route=if(initialRoute=="photo-settings") "diary" else "photo"})
        }
    }
    }
    UndoPill(vm,if(route=="diary") 84.dp else if(route=="search" || route=="ingredient-search") 132.dp else 16.dp)
    }
    if(datePicker) NutritionDatePicker(day,{datePicker=false},{dayEpoch=it.toEpochDay().coerceAtMost(LocalDate.now().toEpochDay())})
}

/** A floating confirmation with Undo, above whatever bottom bar the current page has. */
@Composable
private fun UndoPill(vm:NutritionViewModel,lift:androidx.compose.ui.unit.Dp) {
    val c=LocalDeltaColors.current
    var last by remember {mutableStateOf<NutritionViewModel.Undo?>(null)}
    vm.undo?.let {last=it}
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom=lift,start=16.dp,end=16.dp),contentAlignment=Alignment.BottomCenter) {
        AnimatedVisibility(vm.undo!=null,enter=slideInVertically {it}+fadeIn(),exit=slideOutVertically {it}+fadeOut()) {
            Row(Modifier.widthIn(max=480.dp).fillMaxWidth().clip(RoundedCornerShape(Radii.pill)).background(Color(c.surfaceHigh)).padding(start=18.dp,end=6.dp,top=4.dp,bottom=4.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Text(last?.label.orEmpty(),style=LocalDeltaType.current.bodySmall,modifier=Modifier.weight(1f),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                TextAction("Undo",{vm.performUndo()})
            }
        }
    }
}

/** The meal most likely being logged right now, from the time of day. */
internal fun mealForNow(hour:Int=java.time.LocalTime.now().hour):Meal=when(hour) {in 4..10->Meal.BREAKFAST;in 11..14->Meal.LUNCH;in 17..21->Meal.DINNER;else->Meal.SNACKS}

/**
 * Foods worth repeating in [meal]: only foods previously logged in that same meal, ranked by how often
 * and how recently they were eaten there, leaving out ones already logged in it on [day].
 */
internal fun repeatSuggestions(entries:List<DiaryEntry>,meal:Meal,day:LocalDate,limit:Int=5):List<DiaryEntry> {
    val already=entries.filter {it.day==day && it.meal==meal}.map {it.food.id}.toSet()
    return entries.filter {!it.fromHealthConnect && it.meal==meal && it.food.id !in already && it.day<=day}
        .groupBy {it.food.id}.values.map {rows->
            val latest=rows.maxBy {it.created}
            val days=java.time.temporal.ChronoUnit.DAYS.between(latest.day,day).coerceAtLeast(0)
            latest to rows.map {it.day}.distinct().size*1.0/(1+days/7.0)
        }.sortedByDescending {it.second}.take(limit).map {it.first}
}

internal fun dayTitle(day:LocalDate,today:LocalDate=LocalDate.now()):String=when(day) {
    today->"Today";today.minusDays(1)->"Yesterday";today.plusDays(1)->"Tomorrow"
    else->day.format(DateTimeFormatter.ofPattern(if(day.year==today.year) "EEE d MMM" else "EEE d MMM yyyy"))
}

internal fun macroLine(entries:List<DiaryEntry>):String=Nutrient.macros.joinToString("  ·  ") {n->
    val t=entries.total(n);"${n.label.take(1)} ${if(t.reported==0) "–" else amountText(Math.round(t.amount*10)/10.0)}g"
}

/** A horizontal swipe of at least 64 dp calls [onSwipe] with −1 (to the right) or +1 (to the left). */
internal fun Modifier.horizontalSwipe(onSwipe:(Int)->Unit):Modifier=composed {
    val haptic=androidx.compose.ui.platform.LocalHapticFeedback.current;val latest by rememberUpdatedState(onSwipe)
    pointerInput(Unit) {
        var travel=0f;var vertical=0f
        detectHorizontalDragGestures(onDragStart={travel=0f},onDragEnd={
            if(kotlin.math.abs(travel)>64.dp.toPx()) {latest(if(travel<0) 1 else -1);haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)}
        }) {change,amount->travel+=amount;change.consume()}
    }
}

@Composable
private fun DiaryPage(state: NutritionState,day: LocalDate,meal: Meal,padding: PaddingValues,onDay: (LocalDate)->Unit,onMeal: (Meal)->Unit,
                      onAdd:(Meal)->Unit,onEntry:(DiaryEntry)->Unit,onRoute:(String)->Unit,onDate:()->Unit,onRepeat:(DiaryEntry)->Unit,onDelete:(DiaryEntry)->Unit) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current;val today=LocalDate.now()
    val entries=state.entries.filter {it.day==day};val energy=entries.total(Nutrient.ENERGY);val target=state.targets.target(Nutrient.ENERGY)
    val colours=macroColours()
    // The chart's week follows the selected day, sliding only when the day leaves it.
    var chartEnd by rememberSaveable {mutableLongStateOf(day.toEpochDay())}
    if(day.toEpochDay() !in chartEnd-6..chartEnd) chartEnd=if(day.toEpochDay()>chartEnd) day.toEpochDay() else minOf(day.toEpochDay()+6,today.toEpochDay())
    var breakdown by rememberSaveable {mutableStateOf(false)}
    var nutrientsOpen by rememberSaveable {mutableStateOf(false)}
    fun moveDay(by:Long) {val next=day.plusDays(by);if(next<=today) onDay(next)}
    NutritionPage("Diary",padding) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            GlassIconButton(io.github.ebziboi.delta.R.drawable.ic_chevron_right,"Previous day",{moveDay(-1)},Modifier.graphicsLayer {rotationZ=180f},size=40.dp)
            Column(Modifier.weight(1f).clip(RoundedCornerShape(Radii.control)).clickable(role=androidx.compose.ui.semantics.Role.Button,onClickLabel="Choose date",onClick=onDate).padding(vertical=4.dp),
                horizontalAlignment=Alignment.CenterHorizontally) {
                Text(dayTitle(day,today),style=type.title)
                Text(if(day==today) day.format(DateTimeFormatter.ofPattern("EEEE d MMMM")) else "${java.time.temporal.ChronoUnit.DAYS.between(day,today)} days ago",style=type.label,color=Color(c.textDim))
            }
            GlassIconButton(io.github.ebziboi.delta.R.drawable.ic_chevron_right,"Next day",{moveDay(1)},size=40.dp,enabled=day<today)
        }
        if(day!=today) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {TextAction("Jump to today",{onDay(today)})}
        ContentSection(Modifier.horizontalSwipe {moveDay(it.toLong())},tone=ModuleTone.SAGE,framed=true) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Caption("Daily nutrition")
                    ValueWithUnit(if(energy.known) amountText(Math.round(energy.amount).toDouble()) else "—","kcal",style=type.numberLarge.copy(fontSize=40.sp,lineHeight=44.sp))
                    Text(when {
                        entries.isEmpty()->"No food logged"
                        target==null->"${entries.size} ${if(entries.size==1) "item" else "items"} logged"
                        energy.partial->"Energy totals are incomplete"
                        energy.amount<=target->"${amountText(Math.round(target-energy.amount).toDouble())} kcal left of ${amountText(target)}"
                        else->"${amountText(Math.round(energy.amount-target).toDouble())} kcal over ${amountText(target)}"
                    },style=type.bodySmall,color=Color(if(target!=null && energy.amount>target) c.warn else c.textDim))
                }
                Column(Modifier.weight(1.05f).padding(top=2.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Nutrient.macros.forEachIndexed {i,n->
                        val t=entries.total(n);val goal=state.targets.target(n)
                        Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(colours[i]))
                                Text(if(n==Nutrient.CARBS) "Carbs" else n.label,style=type.label,color=Color(c.textDim),modifier=Modifier.weight(1f),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(buildAnnotatedString {
                                    append(if(t.reported==0) "–" else amountText(Math.round(t.amount*10)/10.0))
                                    withStyle(SpanStyle(color=Color(c.textDim))) {append(goal?.let {" / ${amountText(Math.round(it).toDouble())}g"} ?: "g")}
                                },style=type.bodyStrong,maxLines=1)
                            }
                            if(goal!=null) Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(c.text).copy(alpha=.08f))) {
                                Box(Modifier.fillMaxWidth((t.amount/goal).toFloat().coerceIn(0f,1f)).fillMaxHeight().background(colours[i]))
                            }
                        }
                    }
                }
            }
            target?.let {ProgressRail((energy.amount/it).toFloat(),height=6.dp)}
            if(energy.partial) Text("${energy.reported} of ${energy.count} foods report energy",style=type.bodySmall,color=Color(c.textDim))
            DiaryMacroChart(state,LocalDate.ofEpochDay(chartEnd),day,onDay) {weeks->
                val next=day.plusDays(7L*weeks).coerceAtMost(today)
                chartEnd=(chartEnd+7L*weeks).coerceAtMost(today.toEpochDay());onDay(next)
            }
            val turn by animateFloatAsState(if(breakdown) 180f else 0f,label="breakdown-arrow")
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.control)).clickable(role=androidx.compose.ui.semantics.Role.Button) {breakdown=!breakdown}
                .semantics {stateDescription=if(breakdown) "Expanded" else "Collapsed"}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("Nutritional breakdown",style=type.bodyStrong,modifier=Modifier.weight(1f))
                Text(if(day==today) "today" else day.format(DateTimeFormatter.ofPattern("d MMM")),style=type.label,color=Color(c.textDim))
                Spacer(Modifier.width(6.dp))
                DeltaIcon(io.github.ebziboi.delta.R.drawable.ic_expand_more,"Show nutritional breakdown",Modifier.graphicsLayer {rotationZ=turn},tint=Color(c.accent))
            }
            AnimatedVisibility(breakdown) {
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(entries.isEmpty()) Text("Log food on this day to see its breakdown.",style=type.bodySmall,color=Color(c.textDim))
                    else {
                        val slices=macroEnergySlices(entries)
                        BreakdownDonut(slices,entries,amountText(Math.round(slices.sumOf {it.weight}).toDouble()),"kcal from macros",description="Day macro breakdown pie chart",height=180.dp)
                        Text("Protein and carbohydrate count 4 kcal/g, fat 9 kcal/g.",style=type.label,color=Color(c.textDim))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(Triple("Charts","charts",io.github.ebziboi.delta.R.drawable.ic_bar_chart),Triple("Fasting","fasting",io.github.ebziboi.delta.R.drawable.ic_schedule),
                Triple("Targets","targets",io.github.ebziboi.delta.R.drawable.ic_flag),Triple("Copy day","copy",io.github.ebziboi.delta.R.drawable.ic_layers)).forEach {(label,destination,icon)->
                Column(Modifier.weight(1f).heightIn(min=60.dp).refractiveGlass(RoundedCornerShape(Radii.control))
                    .clickable(role=androidx.compose.ui.semantics.Role.Button) {onRoute(destination)}.padding(horizontal=4.dp,vertical=9.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    DeltaIcon(icon,null,tint=Color(c.accent),size=18.dp)
                    Text(label,style=type.label,maxLines=1)
                }
            }
        }
        val suggestions=remember(state.entries,meal,day) {repeatSuggestions(state.entries,meal,day)}
        if(state.entries.any {!it.fromHealthConnect}) ContentSection(Modifier.horizontalSwipe {dir->
            val next=Meal.entries.getOrNull(Meal.entries.indexOf(meal)+dir);if(next!=null) onMeal(next)
        }) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Caption("Repeat",Modifier.weight(1f))
                Text("Swipe for other meals",style=type.label,color=Color(c.textDim))
            }
            SegmentedControl(Meal.entries,meal,{it.label},onSelect=onMeal)
            AnimatedContent(meal,transitionSpec={
                val forward=targetState.ordinal>initialState.ordinal
                (slideInHorizontally {w->if(forward) w/3 else -w/3}+fadeIn()) togetherWith (slideOutHorizontally {w->if(forward) -w/3 else w/3}+fadeOut())
            },label="repeat-meal") {m->
                val list=if(m==meal) suggestions else repeatSuggestions(state.entries,m,day)
                Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    if(list.isEmpty()) Text("Foods you log for ${m.label.lowercase()} will be suggested here.",style=type.bodySmall,color=Color(c.textDim),modifier=Modifier.padding(vertical=8.dp))
                    list.forEach {e->
                        val kcal=e.nutrients[Nutrient.ENERGY]
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.control)).clickable(role=androidx.compose.ui.semantics.Role.Button,onClickLabel="Add ${e.food.name} to ${m.label}") {onRepeat(e)}
                            .padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(e.food.name,style=type.body,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text("${amountText(e.amount)} ${e.food.unit.symbol}"+(kcal?.let {" · ${amountText(Math.round(it).toDouble())} kcal"} ?: ""),style=type.label,color=Color(c.textDim))
                            }
                            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(c.accent).copy(alpha=.16f)),contentAlignment=Alignment.Center) {
                                DeltaIcon(io.github.ebziboi.delta.R.drawable.ic_add,null,tint=Color(c.accent),size=18.dp)
                            }
                        }
                    }
                }
            }
        }
        Meal.entries.forEach { m ->
            val items=entries.filter {it.meal==m};val total=items.total(Nutrient.ENERGY)
            ContentSection(spacing=if(items.isEmpty()) 0.dp else 8.dp) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(m.label,style=type.title)
                        if(items.isNotEmpty()) Text(macroLine(items),style=type.label,color=Color(c.textDim))
                    }
                    if(items.isNotEmpty()) Text((if(total.known) "${amountText(Math.round(total.amount).toDouble())} kcal" else "Unknown")+if(total.partial) "*" else "",style=type.number,color=Color(c.accent))
                    GlassIconButton(io.github.ebziboi.delta.R.drawable.ic_add,"Add to ${m.label}",{onMeal(m);onAdd(m)},size=40.dp,tint=Color(c.accent))
                }
                items.forEach {e->DiaryEntryRow(e,{onEntry(e)},if(e.fromHealthConnect) null else ({onDelete(e)}))}
            }
        }
        val others=Nutrient.entries.filter {it !in Nutrient.primary && (state.targets.target(it)!=null || entries.any {e -> it in e.food.per100})}
        if(others.isNotEmpty()) ContentSection {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.control)).clickable {nutrientsOpen=!nutrientsOpen}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                Caption("Nutrients · ${others.size}",Modifier.weight(1f))
                DeltaIcon(io.github.ebziboi.delta.R.drawable.ic_expand_more,if(nutrientsOpen) "Show fewer nutrients" else "Show all nutrients",Modifier.graphicsLayer {rotationZ=if(nutrientsOpen) 180f else 0f},tint=Color(c.accent))
            }
            (if(nutrientsOpen) others else others.filter {state.targets.target(it)!=null || it in listOf(Nutrient.FIBRE,Nutrient.SUGAR,Nutrient.SATURATED,Nutrient.SALT)}.take(4))
                .forEach {NutrientProgress(it,entries.total(it),state.targets.target(it),it in state.targets.limits)}
        }
    }
}

/** A diary line: swipe left to delete (with undo), tap to edit. */
@Composable
private fun DiaryEntryRow(e:DiaryEntry,onClick:()->Unit,onDelete:(()->Unit)?) {
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current
    val content:@Composable ()->Unit={
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.control)).clickable(onClick=onClick).padding(vertical=9.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(e.food.name,style=type.body,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(((if(e.fromHealthConnect) "Health Connect · ${e.food.brand}" else "${amountText(e.amount)} ${e.food.unit.symbol}")+(if(e.food.estimated) " · Estimated" else "")+"  ·  "+macroLine(listOf(e))),
                    style=type.label,color=Color(c.textDim),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Text(e.nutrients[Nutrient.ENERGY]?.let {"${amountText(Math.round(it).toDouble())}"} ?: "—",style=type.number)
        }
    }
    if(onDelete==null) {content();return}
    val dismiss=rememberSwipeToDismissBoxState()
    LaunchedEffect(dismiss.currentValue) {if(dismiss.currentValue==SwipeToDismissBoxValue.EndToStart) {onDelete();dismiss.snapTo(SwipeToDismissBoxValue.Settled)}}
    SwipeToDismissBox(dismiss,enableDismissFromStartToEnd=false,backgroundContent={
        if(dismiss.dismissDirection==SwipeToDismissBoxValue.EndToStart) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(Radii.control)).background(Color(0xFFE5534B)).padding(horizontal=16.dp),contentAlignment=Alignment.CenterEnd) {
            DeltaIcon(io.github.ebziboi.delta.R.drawable.ic_delete,"Delete",tint=Color.White,size=20.dp)
        }
    }) {content()}
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

@Composable
private fun HealthFoodPage(entry:DiaryEntry,padding:PaddingValues,onBack:()->Unit) {
    NutritionPage("Recorded food",padding,onBack) {
        ContentSection {
            Text(entry.food.name,style=LocalDeltaType.current.title)
            StatusPill("Health Connect · Read only",Color(LocalDeltaColors.current.good))
            Text(entry.day.format(dayFormat)+" · "+entry.meal.label,style=LocalDeltaType.current.bodySmall)
            Text(entry.food.brand,style=LocalDeltaType.current.bodySmall)
            Text("Consumed totals supplied by the source app. Edit this record in that app.",style=LocalDeltaType.current.bodySmall)
        }
        ContentSection {
            entry.nutrients.forEach {(n,v)->NutritionRow(n.label,nutrientText(v,n))}
            if(entry.nutrients.isEmpty()) Text("The source app did not supply nutrient values.")
        }
    }
}

@Composable
fun HomeNutritionCharts(padding:PaddingValues,weights:List<WeightEntry>,unit:io.github.ebziboi.delta.data.WeightUnit,onBack:()->Unit,vm:NutritionViewModel=viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(onBack=onBack)
    val settings by (androidx.compose.ui.platform.LocalContext.current.applicationContext as io.github.ebziboi.delta.DeltaApp).repo.settings.collectAsStateWithLifecycle(initialValue=io.github.ebziboi.delta.data.AppSettings())
    NutritionChartsPage(state,weights,padding,onBack,unit,settings.startEpochDay?.let(LocalDate::ofEpochDay),settings.weekStartsOn)
}
