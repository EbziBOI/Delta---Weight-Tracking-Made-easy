package io.github.ebziboi.delta.nutrition

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.github.ebziboi.delta.ui.*
import org.json.JSONObject
import java.io.File

@Composable
internal fun FoodImportPage(vm:NutritionViewModel,initialBarcode:String,padding:PaddingValues,onBack:()->Unit,onSave:(Food)->Unit) {
    val context=LocalContext.current
    var name by rememberSaveable {mutableStateOf("")};var brand by rememberSaveable {mutableStateOf("")}
    var barcode by rememberSaveable {mutableStateOf(initialBarcode)};var link by rememberSaveable {mutableStateOf("")}
    var unit by rememberSaveable {mutableStateOf(FoodUnit.GRAMS)}
    var pageUnit by rememberSaveable {mutableStateOf(FoodUnit.GRAMS)};var labelUnit by rememberSaveable {mutableStateOf(FoodUnit.GRAMS)}
    var lastPhoto by rememberSaveable {mutableStateOf<String?>(null)}
    var fields by rememberSaveable {mutableStateOf(mapOf<String,String>())}
    var pageJson by rememberSaveable {mutableStateOf<String?>(null)};var labelJson by rememberSaveable {mutableStateOf<String?>(null)}
    var labelValuesJson by rememberSaveable {mutableStateOf("{}")};var pageValuesJson by rememberSaveable {mutableStateOf("{}")}
    var checked by rememberSaveable {mutableStateOf(listOf<String>())};var uncertain by rememberSaveable {mutableStateOf(listOf<String>())}
    var reviewed by rememberSaveable {mutableStateOf(false)};var expanded by rememberSaveable {mutableStateOf(false)}
    var pendingPhoto by rememberSaveable {mutableStateOf<String?>(null)};var cropUri by rememberSaveable {mutableStateOf<String?>(null)}
    var textEditor by rememberSaveable {mutableStateOf(false)};var transcript by rememberSaveable {mutableStateOf("")}
    val labelRead=remember(labelJson) {labelJson?.let(LabelRead::fromJson)}
    val pageRead=remember(pageJson) {pageJson?.let {runCatching {LabelRead.fromJson(JSONObject(it).getString("read"))}.getOrNull()}}
    val pageValues=remember(pageValuesJson) {JSONObject(pageValuesJson).nutrients()}
    val labelValues=remember(labelValuesJson) {JSONObject(labelValuesJson).nutrients()}
    val page=remember(pageJson) {pageJson?.let(::JSONObject)}
    fun setField(n:Nutrient,text:String,label:Boolean=false) {
        fields=fields+(n.name to text);checked=checked.filterNot {it==n.name}+(if(label) listOf(n.name) else emptyList());reviewed=false
    }
    fun apply(values:Nutrients,isLabel:Boolean,sourceUnit:FoodUnit) {
        if(unit!=sourceUnit) {fields=emptyMap();checked=emptyList()}
        unit=sourceUnit
        fields=fields+values.mapKeys {it.key.name}.mapValues {editText(it.value)}
        checked=checked.filterNot {n->values.keys.any {it.name==n}}+(if(isLabel) values.keys.map {it.name} else emptyList());reviewed=false
    }
    val browser=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {result->
        if(result.resultCode==android.app.Activity.RESULT_OK) result.data?.getStringExtra("page")?.let {raw->
            runCatching {ProductPages.parse(raw,barcode)}.onSuccess {p->
                pageJson=JSONObject().put("name",p.name).put("brand",p.brand).put("url",p.url).put("barcode",p.barcode).put("matched",p.barcodeMatch).put("conflict",p.barcodeConflict).put("read",p.read.json()).toString()
                if(name.isBlank()) name=p.name;if(brand.isBlank()) brand=p.brand
                val column=p.read.columns[p.read.best];val values=column.per100()
                pageUnit=column.basis.unit;pageValuesJson=values.toJson().toString()
                if(fields.values.all {it.isBlank()} && values.isNotEmpty()) apply(values,false,column.basis.unit)
                reviewed=false
            }.onFailure {vm.message=it.message}
        }
    }
    fun openWeb(url:String?=null,byName:Boolean=false) {
        val named=listOf(name,brand,vm.selectedStore?.let {id->FoodStores.uk.find {it.id==id}?.label}).filterNotNull().filter {it.isNotBlank()}.joinToString(" ")
        val query=if(byName) named else barcode.ifBlank {named}
        if(url==null && query.isBlank()) {vm.message="Enter a barcode or product name first";return}
        runCatching {browser.launch(Intent(context,ProductBrowserActivity::class.java).putExtra("query",query).putExtra("barcode",barcode).apply {url?.let {putExtra("url",it)}})}
            .onFailure {vm.message="Web lookup is unavailable on this device. You can still scan its label."}
    }
    val capture=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {ok->
        if(ok) cropUri=pendingPhoto else pendingPhoto?.let {runCatching {context.contentResolver.delete(Uri.parse(it),null,null)}}
        pendingPhoto=null
    }
    fun camera() {
        runCatching {
            val dir=File(context.cacheDir,"meal_photos").apply {mkdirs()}
            dir.listFiles()?.filter {it.name.startsWith("label-") && System.currentTimeMillis()-it.lastModified()>86400000}?.forEach {it.delete()}
            val file=File.createTempFile("label-",".jpg",dir);val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
            pendingPhoto=uri.toString();capture.launch(uri)
        }.onFailure {vm.message="Camera unavailable. Choose a label photo instead."}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {if(it) camera() else vm.message="Choose a photo to read the label without camera access."}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri->if(uri!=null) {
        runCatching {context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)};cropUri=uri.toString()
    }}
    val values=fields.mapNotNull {(n,v)->v.takeIf {it.isNotBlank()}?.number()?.let {Nutrient.valueOf(n) to it}}.toMap()
    val warnings=LabelParser.warnings(values)
    val valid=name.isNotBlank() && name.length<=250 && brand.length<=250 && fields.values.all {it.isBlank() || it.number()!=null} && values.isNotEmpty() &&
        (barcode.isBlank() || ProductPages.canonicalBarcode(barcode)!=null)
    NutritionPage("Import food",padding,onBack) {
        ContentSection {
            GlassTextField(name,{name=it;reviewed=false},"Product name",Modifier.fillMaxWidth())
            GlassTextField(brand,{brand=it;reviewed=false},"Brand",Modifier.fillMaxWidth())
            GlassTextField(barcode,{barcode=it.filter(Char::isDigit).take(14);reviewed=false},"Barcode",Modifier.fillMaxWidth())
            PrimaryButton("Find online",{openWeb()},Modifier.fillMaxWidth(),enabled=!vm.busy)
            if(barcode.isNotBlank() && name.isNotBlank()) TextAction("Search by name instead",{openWeb(byName=true)})
            GlassTextField(link,{link=it},"Product link (optional)",Modifier.fillMaxWidth())
            if(link.isNotBlank()) SecondaryButton("Open product link",{openWeb(link.trim())},Modifier.fillMaxWidth(),enabled=!vm.busy && ProductPages.publicUrl(link.trim()))
        }
        page?.let {p->ContentSection(tone=ModuleTone.VIOLET) {
            Caption("Product page")
            Text(p.optString("name"),style=LocalDeltaType.current.title)
            val matched=ProductPages.canonicalBarcode(barcode)?.let {it==ProductPages.canonicalBarcode(p.optString("barcode"))}==true
            StatusPill(when {matched->"Barcode matched";p.optString("barcode").let {it.isNotBlank() && it!="null"} && barcode.isNotBlank()->"Different barcode";else->"Barcode not detected on page"},Color(if(matched) LocalDeltaColors.current.good else LocalDeltaColors.current.warn))
            val uri=androidx.compose.ui.platform.LocalUriHandler.current
            TextAction(Uri.parse(p.getString("url")).host.orEmpty(),{uri.openUri(p.getString("url"))})
            pageRead?.let {read->LabelReadControls(read,"Page",{v,u->pageUnit=u;pageValuesJson=v.toJson().toString();apply(v,false,u)})}
        }}
        ContentSection(tone=ModuleTone.SAGE) {
            Caption("Nutrition label")
            Text("Read on this phone · no AI account",style=LocalDeltaType.current.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Take label photo",{if(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) camera() else permission.launch(Manifest.permission.CAMERA)},Modifier.weight(1f),enabled=!vm.busy)
                SecondaryButton("Choose photo",{picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},Modifier.weight(1f),enabled=!vm.busy)
            }
            labelRead?.let {read->
                read.warnings.forEach {Text(it,style=LocalDeltaType.current.bodySmall,color=Color(LocalDeltaColors.current.warn))}
                LabelReadControls(read,"Label",{v,u->labelUnit=u;labelValuesJson=v.toJson().toString();apply(v,true,u)})
                TextAction("Edit recognised text",{transcript=read.raw;textEditor=true})
            }
            if(labelRead==null) TextAction("Paste label text",{transcript="";textEditor=true})
            lastPhoto?.let {photo->TextAction("Adjust crop",{cropUri=photo})}
            if(vm.busy) TextAction("Cancel reading",{vm.cancel()})
        }
        ContentSection {
            Caption("Review per 100 ${unit.symbol}")
            SegmentedControl(FoodUnit.entries,unit,{"100 ${it.symbol}"}) {unit=it;checked=emptyList();reviewed=false}
            Nutrient.entries.filter {expanded || it in Nutrient.primary+listOf(Nutrient.SATURATED,Nutrient.SUGAR,Nutrient.FIBRE,Nutrient.SALT,Nutrient.SODIUM) || !fields[it.name].isNullOrBlank() || it in labelValues || it in pageValues}.forEach {n->
                NumberField(fields[n.name].orEmpty(),{setField(n,it)},"${n.label} (${n.unit})",Modifier.fillMaxWidth())
                val a=pageValues[n]?.takeIf {pageUnit==unit};val b=labelValues[n]?.takeIf {labelUnit==unit}
                if(a!=null || b!=null) {
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        a?.let {TextAction("Page ${java.math.BigDecimal.valueOf(it).round(java.math.MathContext(6)).stripTrailingZeros().toPlainString()} ${n.unit}",{setField(n,editText(it))})}
                        b?.let {TextAction("Label ${java.math.BigDecimal.valueOf(it).round(java.math.MathContext(6)).stripTrailingZeros().toPlainString()} ${n.unit}",{setField(n,editText(it),true)})}
                    }
                    if(a!=null && b!=null) Text(if(LabelParser.differs(a,b)) "Values differ" else "Values agree within rounding",style=LocalDeltaType.current.bodySmall,color=Color(if(LabelParser.differs(a,b)) LocalDeltaColors.current.warn else LocalDeltaColors.current.good))
                    if(n.name in uncertain) Text("Check this value against the photo",style=LocalDeltaType.current.bodySmall,color=Color(LocalDeltaColors.current.warn))
                }
            }
            TextAction(if(expanded) "Fewer nutrients" else "All nutrients",{expanded=!expanded})
            warnings.forEach {Text(it,style=LocalDeltaType.current.bodySmall,color=Color(LocalDeltaColors.current.warn))}
            if(checked.isNotEmpty()) Text("${checked.size} values taken from the label",style=LocalDeltaType.current.bodySmall)
            ToggleRow("I've reviewed these values",reviewed,{reviewed=it})
        }
        PrimaryButton("Save food",{
            onSave(Food(name=name.trim(),brand=brand.trim(),per100=values,unit=unit,barcode=barcode.ifBlank {null},
                sourceUrl=page?.optString("url"),labelChecked=checked.mapNotNull {key->Nutrient.entries.find {it.name==key}}.filter {it in values}.toSet()))
        },Modifier.fillMaxWidth(),enabled=valid && reviewed && !vm.busy)
    }
    cropUri?.let {text->LabelCropper(Uri.parse(text),{cropUri=null},onScan={crop->
        cropUri=null;lastPhoto=text
        vm.run {
            val result=LabelOcr.scan(context,Uri.parse(text),crop)
            labelJson=result.read.json();checked=emptyList();uncertain=result.uncertain.map {it.name}
            val column=result.read.columns[result.read.best];val values=column.per100()
            labelUnit=column.basis.unit;labelValuesJson=values.toJson().toString()
            if(fields.values.all {it.isBlank()} && values.isNotEmpty()) apply(values.filterKeys {it !in result.uncertain},true,column.basis.unit)
            reviewed=false
        }
    })}
    if(textEditor) AlertDialog(onDismissRequest={textEditor=false},title={Text("Recognised label text")},text={
        OutlinedTextField(transcript,{transcript=it.take(18000)},Modifier.fillMaxWidth().heightIn(min=200.dp,max=420.dp),label={Text("Label transcription")})
    },confirmButton={TextButton({val read=LabelParser.parse(transcript);labelJson=read.json();val column=read.columns[read.best];labelUnit=column.basis.unit;labelValuesJson=column.per100().toJson().toString();checked=emptyList();uncertain=emptyList();reviewed=false;textEditor=false}) {Text("Read text")}},dismissButton={TextButton({textEditor=false}) {Text("Cancel")}})
}

@Composable
private fun LabelReadControls(read:LabelRead,title:String,onUse:(Nutrients,FoodUnit)->Unit) {
    var selected by rememberSaveable(read.json()) {mutableIntStateOf(read.best)}
    val column=read.columns.getOrNull(selected) ?: return
    var amount by rememberSaveable(read.json(),selected) {mutableStateOf(column.basis.amount?.let(::editText) ?: "")}
    var unit by rememberSaveable(read.json(),selected) {mutableStateOf(column.basis.unit)}
    var menu by remember {mutableStateOf(false)}
    if(read.columns.size>1) Box {
        SecondaryButton("Column: ${column.basis.title}",{menu=true})
        DropdownMenu(menu,{menu=false}) {read.columns.forEachIndexed {i,c->DropdownMenuItem({Text(c.basis.title)},{selected=i;menu=false})}}
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        NumberField(amount,{amount=it},"Values per",Modifier.weight(1f))
        SegmentedControl(FoodUnit.entries,unit,{it.symbol},Modifier.weight(1f)) {unit=it}
    }
    column.observations.filterValues {it.bounded}.forEach {(n,v)->Text("${n.label}: ${v.raw} · enter a value manually if needed",style=LocalDeltaType.current.bodySmall,color=Color(LocalDeltaColors.current.warn))}
    if(column.basis.amount==null) Text("Choose the quantity represented by this column.",style=LocalDeltaType.current.bodySmall)
    val values=column.per100(amount.number()?.takeIf {it>0 && it<=100000})
    SecondaryButton("Use ${title.lowercase()} values",{onUse(values,unit)},Modifier.fillMaxWidth(),enabled=values.isNotEmpty())
}
