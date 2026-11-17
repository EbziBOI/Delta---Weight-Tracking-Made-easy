package io.github.ebziboi.delta.nutrition

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.github.ebziboi.delta.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun PhotoPage(vm:NutritionViewModel,padding:PaddingValues,onBack:()->Unit,onSettings:()->Unit,onLog:(Meal)->Unit,meal:Meal) {
    val context=LocalContext.current
    val prefs=remember {context.getSharedPreferences("photo_options",0)}
    var provider by rememberSaveable {mutableStateOf(runCatching {PhotoProvider.valueOf(prefs.getString("provider","OPENAI")!!)}.getOrDefault(PhotoProvider.OPENAI))}
    var selectedMeal by rememberSaveable {mutableStateOf(meal)}
    var editing by remember {mutableIntStateOf(-2)}
    var pendingUri by rememberSaveable {mutableStateOf<String?>(null)}
    val capture=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {ok ->
        if(ok) {vm.clearPhoto();vm.photoUri=pendingUri?.let(Uri::parse)}
        else pendingUri?.let {runCatching {context.contentResolver.delete(Uri.parse(it),null,null)}}
        pendingUri=null
    }
    val launchCamera:()->Unit={
        runCatching {
            val directory=File(context.cacheDir,"meal_photos").apply {mkdirs()}
            // Clear abandoned captures without touching a currently reviewed image.
            directory.listFiles()?.filter {System.currentTimeMillis()-it.lastModified()>86400000}?.forEach {it.delete()}
            val file=File.createTempFile("meal-",".jpg",directory)
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
            pendingUri=uri.toString();capture.launch(uri)
        }.onFailure {vm.message="No camera app is available. Choose a photo instead."}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {if(it) launchCamera() else vm.message="Camera permission was declined. You can choose a photo instead."}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri ->if(uri!=null) {vm.clearPhoto();vm.photoUri=uri}}
    val uri=vm.photoUri
    val preview by produceState<android.graphics.Bitmap?>(null,uri) {
        value=uri?.let {withContext(Dispatchers.IO) {runCatching {preparePhoto(context,it).let {bytes -> BitmapFactory.decodeByteArray(bytes,0,bytes.size)}}.getOrNull()}}
    }
    NutritionPage("Photo diary",padding,onBack) {
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Camera",{if(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) launchCamera() else permission.launch(Manifest.permission.CAMERA)},enabled=!vm.busy)
            SecondaryButton("Choose photo",{picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!vm.busy)
        }
        preview?.let {Image(it.asImageBitmap(),"Selected meal photo",Modifier.fillMaxWidth().heightIn(max=220.dp))}
        SegmentedControl(PhotoProvider.entries,provider,{it.label}) {provider=it;prefs.edit().putString("provider",it.name).apply()}
        ContentSection {
            Text("Analyse with ${provider.label}",style=LocalDeltaType.current.title)
            Text("This sends the selected photo to ${provider.label} using your API key and may incur API charges. Portions and nutrition are estimates; review them before logging.",style=LocalDeltaType.current.bodySmall)
            TextAction(if(provider in vm.savedProviders) "Manage API keys" else "Add API key",onSettings)
            PrimaryButton("Analyse photo",{vm.analyse(provider)},Modifier.fillMaxWidth(),enabled=uri!=null && provider in vm.savedProviders && !vm.busy)
            if(vm.busy) TextAction("Cancel analysis",{vm.cancel()})
        }
        if(vm.photoReview.isNotEmpty()) {
            ContentSection(tone=ModuleTone.SAGE) {
                Caption("Review estimates")
                vm.photoReview.forEachIndexed {index,item ->
                    NutritionRow(item.food.name,"${amountText(item.amount)} g",nutrientText(item.nutrients[Nutrient.ENERGY],Nutrient.ENERGY),{editing=index})
                    TextAction("Remove ${item.food.name}",{vm.photoReview=vm.photoReview.filterIndexed {i,_->i!=index}})
                }
                TextAction("Add another food",{editing=-1})
            }
            SegmentedControl(Meal.entries,selectedMeal,{it.label}) {selectedMeal=it}
            PrimaryButton("Log ${vm.photoReview.size} reviewed ${if(vm.photoReview.size==1) "food" else "foods"}",{onLog(selectedMeal)},Modifier.fillMaxWidth(),enabled=!vm.busy)
            TextAction("Discard estimates",{vm.clearPhoto()})
        }
    }
    if(editing>=-1) PhotoEstimateDialog(vm.photoReview.getOrNull(editing),{editing=-2}) {ingredient ->
        vm.photoReview=if(editing==-1) vm.photoReview+ingredient else vm.photoReview.mapIndexed {i,item->if(i==editing) ingredient else item}
        editing=-2
    }
}

@Composable
private fun PhotoEstimateDialog(initial:Ingredient?,onDismiss:()->Unit,onSave:(Ingredient)->Unit) {
    var name by remember {mutableStateOf(initial?.food?.name ?: "")}
    var grams by remember {mutableStateOf(initial?.amount?.let(::editText) ?: "")}
    var fields by remember {mutableStateOf(Nutrient.entries.associate {it.name to (initial?.food?.per100?.get(it)?.let(::editText) ?: "")})}
    var more by remember {mutableStateOf(false)}
    val valid=name.isNotBlank() && name.length<=250 && grams.number()?.let {it>0 && it<=10000}==true && fields.values.all {it.isBlank() || it.number()!=null}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Review food")},text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            GlassTextField(name,{name=it},"Food name",Modifier.fillMaxWidth());NumberField(grams,{grams=it},"Portion (g)",Modifier.fillMaxWidth())
            Text("Nutrition per 100 g",style=LocalDeltaType.current.title)
            Nutrient.entries.filter {more || it in Nutrient.primary || !fields[it.name].isNullOrBlank()}.forEach {n ->
                NumberField(fields[n.name] ?: "",{fields=fields+(n.name to it)},"${n.label} (${n.unit})",Modifier.fillMaxWidth())
            }
            TextAction(if(more) "Fewer nutrients" else "More nutrients",{more=!more})
        }
    },confirmButton={HapticTextButton({onSave(Ingredient(Food(id=initial?.food?.id ?: newId(),name=name.trim(),per100=fields.mapNotNull {(k,v)->v.number()?.let {Nutrient.valueOf(k) to it}}.toMap(),source=FoodSource.PHOTO),grams.number()!!))},enabled=valid){Text("Save review")}},dismissButton={HapticTextButton(onDismiss){Text("Cancel")}})
}

@Composable
internal fun PhotoSettingsPage(vm:NutritionViewModel,padding:PaddingValues,onBack:()->Unit) {
    val context=LocalContext.current
    var provider by rememberSaveable {mutableStateOf(runCatching {PhotoProvider.valueOf(context.getSharedPreferences("photo_options",0).getString("provider","OPENAI")!!)}.getOrDefault(PhotoProvider.OPENAI))}
    // Deliberately not saveable: credentials never enter saved-instance state or backups.
    var key by remember(provider) {mutableStateOf("")}
    var model by rememberSaveable(provider) {mutableStateOf(provider.defaultModel)}
    LaunchedEffect(provider) {
        runCatching {withContext(Dispatchers.IO) {vm.vault.load(provider)?.model}}.onSuccess {model=it ?: provider.defaultModel}.onFailure {vm.message=it.message}
    }
    val saved=provider in vm.savedProviders
    NutritionPage("Photo settings",padding,onBack) {
        SegmentedControl(PhotoProvider.entries,provider,{it.label}) {provider=it}
        ContentSection {
            Caption("${provider.label} API")
            StatusPill(if(saved) "Key saved on this device" else "No key saved",Color(if(saved) LocalDeltaColors.current.good else LocalDeltaColors.current.textDim))
            OutlinedTextField(key,{key=it},label={Text(if(saved) "Replace API key (optional)" else "API key")},singleLine=true,
                keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(autoCorrectEnabled=false,keyboardType=androidx.compose.ui.text.input.KeyboardType.Password),
                visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(Radii.control))
            GlassTextField(model,{model=it},"Vision model ID",Modifier.fillMaxWidth())
            Text("Keys are encrypted on this device and excluded from backups. Your selected provider receives a photo only when you tap Analyse. Your provider's data policy and API pricing apply.",style=LocalDeltaType.current.bodySmall)
            PrimaryButton("Save ${provider.label} settings",{
                if(key.isBlank() && saved) vm.run {
                    val old=withContext(Dispatchers.IO) {vm.vault.load(provider)} ?: error("Save a key first")
                    withContext(Dispatchers.IO) {vm.vault.save(provider,old.key,model)};vm.message="${provider.label} settings saved"
                } else vm.saveKey(provider,key.trim(),model.trim()) {key="";vm.message="${provider.label} settings saved"}
            },Modifier.fillMaxWidth(),enabled=!vm.busy && (key.isNotBlank() || saved) && model.matches(Regex("[a-zA-Z0-9._-]{1,120}")))
            if(saved) TextAction("Remove ${provider.label} key",{vm.removeKey(provider);key=""})
        }
    }
}
