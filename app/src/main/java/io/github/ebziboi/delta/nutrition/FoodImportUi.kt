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
import androidx.compose.ui.Alignment
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

/**
 * Advanced search: paste a product or recipe link (read off-screen), nutrition text copied from any site, or a name to
 * look up on the web; or scan the label. Every source fills the same per-100 review, and differences are shown.
 */
@Composable
internal fun FoodImportPage(vm:NutritionViewModel,initialBarcode:String,padding:PaddingValues,onBack:()->Unit,onSave:(Food)->Unit,
                            initialInput:String="",onFood:((Food)->Unit)?=null) {
    val context=LocalContext.current
    var input by rememberSaveable {mutableStateOf(initialInput)}
    var readingHost by remember {mutableStateOf<String?>(null)}
    var failedLink by rememberSaveable {mutableStateOf<String?>(null)}
    var started by rememberSaveable {mutableStateOf(false)}
    var autoFilled by rememberSaveable {mutableStateOf(false)}
    var barcodeFromPage by rememberSaveable {mutableStateOf(false)}
    var servingName by rememberSaveable {mutableStateOf("Serving")};var servingSize by rememberSaveable {mutableStateOf("")}
    var pack by rememberSaveable {mutableStateOf("")};var servingFrom by rememberSaveable {mutableStateOf<String?>(null)}
    /** Takes the serving a source states, unless one was already set (by hand or by an earlier source). */
    fun offerServing(read:LabelRead,from:String) {
        if(servingSize.isNotBlank()) return
        AdvancedSearch.serving(read)?.let {(n,size)->servingName=n;servingSize=editText(size);servingFrom=from}
    }
    var name by rememberSaveable {mutableStateOf("")};var brand by rememberSaveable {mutableStateOf("")}
    var barcode by rememberSaveable {mutableStateOf(initialBarcode)}
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
        fields=fields+(n.name to text);checked=checked.filterNot {it==n.name}+(if(label) listOf(n.name) else emptyList());reviewed=false;autoFilled=false
    }
    fun apply(values:Nutrients,isLabel:Boolean,sourceUnit:FoodUnit) {
        if(unit!=sourceUnit) {fields=emptyMap();checked=emptyList()}
        unit=sourceUnit
        fields=fields+values.mapKeys {it.key.name}.mapValues {editText(it.value)}
        checked=checked.filterNot {n->values.keys.any {it.name==n}}+(if(isLabel) values.keys.map {it.name} else emptyList());reviewed=false
    }
    /** Takes a read from a page or pasted text. It fills the review only when that is empty or was filled by an earlier read. */
    fun adopt(read:LabelRead,meta:JSONObject) {
        pageJson=meta.put("read",read.json()).toString();failedLink=null
        offerServing(read,if(meta.optString("url").isBlank()) "the pasted text" else AdvancedSearch.host(meta.optString("url")))
        val column=read.columns[read.best];val values=column.per100()
        pageUnit=column.basis.unit;pageValuesJson=values.toJson().toString()
        if(values.isNotEmpty() && (fields.values.all {it.isBlank()} || autoFilled)) {
            fields=emptyMap();checked=emptyList();apply(values,false,column.basis.unit);autoFilled=true
            // A complete per-100 table whose numbers add up needs no extra confirmation; anything else asks for review.
            reviewed=AdvancedSearch.complete(read) && column.basis.amount==100.0 && LabelParser.warnings(values).isEmpty() && read.warnings.isEmpty()
        } else reviewed=false
    }
    fun usePage(raw:String) {
        val p=ProductPages.parse(raw,barcode)
        if(name.isBlank()) name=p.name;if(brand.isBlank()) brand=p.brand
        if(barcode.isBlank() && p.barcode!=null && ProductPages.canonicalBarcode(p.barcode)!=null) {barcode=p.barcode;barcodeFromPage=true}
        adopt(p.read,JSONObject().put("name",p.name).put("brand",p.brand).put("url",p.url).put("barcode",p.barcode).put("matched",p.barcodeMatch).put("conflict",p.barcodeConflict))
    }
    val browser=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {result->
        if(result.resultCode==android.app.Activity.RESULT_OK) result.data?.getStringExtra("page")?.let {raw->
            runCatching {usePage(raw)}.onFailure {vm.message=it.message}
        }
    }
    fun openWeb(url:String?=null,byName:Boolean=false,query:String?=null) {
        val store=vm.selectedStore?.let {id->FoodStores.uk.find {it.id==id}?.label}
        val named=listOf(name,brand,store).filterNotNull().filter {it.isNotBlank()}.joinToString(" ")
        val query=query?.let {q->listOfNotNull(q,store).joinToString(" ")} ?: if(byName) named else barcode.ifBlank {named}
        if(url==null && query.isBlank()) {vm.message="Enter a barcode or product name first";return}
        runCatching {browser.launch(Intent(context,ProductBrowserActivity::class.java).putExtra("query",query).putExtra("barcode",barcode).apply {url?.let {putExtra("url",it)}})}
            .onFailure {vm.message="Web lookup is unavailable on this device. You can still scan its label."}
    }
    fun useLabel(result:LabelOcrResult,uri:Uri) {
        lastPhoto=uri.toString()
        labelJson=result.read.json();checked=emptyList();uncertain=result.uncertain.map {it.name}
        offerServing(result.read,"the label")
        val column=result.read.columns[result.read.best];val values=column.per100()
        labelUnit=column.basis.unit;labelValuesJson=values.toJson().toString()
        val empty=fields.values.all {it.isBlank()}
        if(empty && values.isNotEmpty()) apply(values.filterKeys {it !in result.uncertain},true,column.basis.unit)
        // A clean, consistent read needs no extra confirmation step; anything doubtful still asks for review.
        reviewed=empty && result.confident
    }
    val scanner=rememberLabelScanner(::useLabel) {vm.message=it}
    val pasted=remember(input) {AdvancedSearch.classify(input)}
    fun readLink(url:String) {
        // vm.run ignores calls while busy, which would leave "Reading…" showing with nothing running.
        if(vm.busy) return
        val host=AdvancedSearch.host(url)
        // Open Food Facts links resolve through its database record, which carries serving sizes and stores.
        val code=AdvancedSearch.openFoodFactsCode(url)
        if(code!=null && onFood!=null) {vm.barcode(code,onFood,{failedLink=url;vm.message="Open Food Facts has no nutrition for $code yet. Open the page or scan its label."});return}
        failedLink=null;readingHost=host
        vm.run {
            try {usePage(PageReader.read(context,url,barcode.ifBlank {null}))}
            catch(e:kotlinx.coroutines.CancellationException) {throw e}
            catch(e:PageReadException) {failedLink=url;throw e}
            catch(e:Exception) {failedLink=url;throw PageReadException("Couldn't read $host. Open the page to import it yourself.")}
            finally {readingHost=null}
        }
    }
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    fun go(what:PastedInput=pasted) {
        // Put the keyboard away so the result below is visible.
        focus.clearFocus()
        when(what) {
            is PastedInput.Link -> readLink(what.url)
            is PastedInput.Text -> {
                failedLink=null
                if(what.count==0) vm.message="No nutrition values found in that text. Paste the table with its numbers."
                else adopt(what.read,JSONObject().put("name","").put("brand","").put("url","").put("pasted",true))
            }
            is PastedInput.Name -> {if(name.isBlank()) name=what.text;openWeb(query=what.text)}
            PastedInput.Empty -> Unit
        }
    }
    fun paste() {
        val clip=(context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)?.primaryClip
            ?.takeIf {it.itemCount>0}?.getItemAt(0)?.coerceToText(context)?.toString()?.take(18000)
        if(clip.isNullOrBlank()) {vm.message="Nothing to paste. Copy a link or the nutrition table first.";return}
        input=clip
        // A pasted link or table is read straight away; a name waits for the search tap.
        AdvancedSearch.classify(clip).takeIf {it !is PastedInput.Name}?.let(::go)
    }
    // Opened from a link typed into food search: start reading it at once.
    LaunchedEffect(Unit) {if(!started) {started=true;if(pasted is PastedInput.Link) go()}}
    val values=fields.mapNotNull {(n,v)->v.takeIf {it.isNotBlank()}?.number()?.let {Nutrient.valueOf(n) to it}}.toMap()
    val warnings=LabelParser.warnings(values)
    val valid=name.isNotBlank() && name.length<=250 && brand.length<=250 && fields.values.all {it.isBlank() || it.number()!=null} && values.isNotEmpty() &&
        (barcode.isBlank() || ProductPages.canonicalBarcode(barcode)!=null)
    NutritionPage("Advanced search",padding,onBack) {
        val c=LocalDeltaColors.current;val type=LocalDeltaType.current
        ContentSection(tone=ModuleTone.VIOLET) {
            Caption("Paste from the web")
            Text("A product or recipe link from any site, its nutrition table copied as text, or a product name to look up.",style=type.bodySmall,color=Color(c.textDim))
            OutlinedTextField(input,{input=it.take(18000);failedLink=null},Modifier.fillMaxWidth().heightIn(min=56.dp),
                label={Text("Link, nutrition text or name")},maxLines=6,shape=androidx.compose.foundation.shape.RoundedCornerShape(Radii.control),
                trailingIcon=if(input.isNotEmpty()) ({HapticIconButton({input="";failedLink=null}) {DeltaIcon(io.github.ebziboi.delta.R.drawable.ic_close,"Clear",tint=Color(c.textDim),size=18.dp)}}) else null,
                keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=KeyboardType.Uri,imeAction=androidx.compose.ui.text.input.ImeAction.Go),
                keyboardActions=androidx.compose.foundation.text.KeyboardActions(onGo={go()}),
                colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=Color(c.glass.fill),unfocusedContainerColor=Color(c.glass.fill),
                    focusedBorderColor=Color(c.accent),unfocusedBorderColor=Color(c.glass.edge),cursorColor=Color(c.accent),
                    focusedLabelColor=Color(c.text),unfocusedLabelColor=Color(c.textDim)))
            // Says what the input was taken as, so a mistyped link is obvious before reading.
            val kind:Pair<Int,String>?=when(val p=pasted) {
                is PastedInput.Link -> io.github.ebziboi.delta.R.drawable.ic_open_in_new to "Link · ${p.host}"
                is PastedInput.Text -> io.github.ebziboi.delta.R.drawable.ic_text_fields to if(p.count==0) "Text · no nutrition values recognised" else "Nutrition text · ${p.count} values"
                is PastedInput.Name -> io.github.ebziboi.delta.R.drawable.ic_search to "Product name · searches the web"
                PastedInput.Empty -> null
            }
            kind?.let {(icon,label)->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                DeltaIcon(icon,null,tint=Color(c.textDim),size=16.dp);Text(label,style=type.label,color=Color(c.textDim),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }}
            val host=readingHost
            if(host!=null) Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp),color=Color(c.accent),strokeWidth=2.dp)
                Text("Reading $host…",Modifier.weight(1f),style=type.bodySmall,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                TextAction("Cancel",{vm.cancel();readingHost=null})
            } else if(pasted==PastedInput.Empty) {
                PrimaryButton("Paste",{paste()},Modifier.fillMaxWidth(),enabled=!vm.busy,icon=io.github.ebziboi.delta.R.drawable.ic_content_paste)
            } else {
                PrimaryButton(when(pasted) {is PastedInput.Link->"Read page";is PastedInput.Text->"Read text";else->"Search the web"},{go()},Modifier.fillMaxWidth(),enabled=!vm.busy,
                    icon=when(pasted) {is PastedInput.Name->io.github.ebziboi.delta.R.drawable.ic_search;else->io.github.ebziboi.delta.R.drawable.ic_download})
            }
            // Reading on its own failed (a bot check, a cookie wall, a table behind a tab): load the page visibly instead.
            failedLink?.takeIf {readingHost==null}?.let {url->
                SecondaryButton("Open page and import",{openWeb(url)},Modifier.fillMaxWidth(),enabled=!vm.busy,icon=io.github.ebziboi.delta.R.drawable.ic_open_in_new)
                Text("Or copy the nutrition table from the page and paste it here.",style=type.bodySmall,color=Color(c.textDim))
            }
        }
        page?.let {p->ContentSection(tone=ModuleTone.VIOLET) {
            val url=p.optString("url").takeIf {it.isNotBlank()}
            Caption(if(url==null) "Pasted text" else "From ${AdvancedSearch.host(url)}")
            p.optString("name").takeIf {it.isNotBlank()}?.let {Text(it,style=type.title)}
            pageRead?.let {read->
                val column=read.columns[read.best]
                Text(if(column.observations.isEmpty()) "No nutrition values found" else "${column.observations.size} values ${basisText(column.basis)}",style=type.bodySmall,color=Color(c.textDim))
                // What was read, as stated on the page, so it can be checked before any conversion.
                valuesLine(column).takeIf {it.isNotBlank()}?.let {Text(it,style=type.body)}
            }
            if(url!=null) {
                val pageCode=p.optString("barcode").takeIf {it.isNotBlank() && it!="null"}
                val matched=ProductPages.canonicalBarcode(barcode)?.let {it==ProductPages.canonicalBarcode(pageCode)}==true
                when {
                    barcodeFromPage && matched -> StatusPill("Barcode from page",Color(c.good))
                    matched -> StatusPill("Barcode matched",Color(c.good))
                    pageCode!=null && barcode.isNotBlank() -> StatusPill("Different barcode",Color(c.warn))
                    barcode.isNotBlank() -> StatusPill("Barcode not detected on page",Color(c.warn))
                }
            }
            if(autoFilled && reviewed) StatusPill("Values add up · ready to save",Color(c.good))
            pageRead?.let {read->LabelReadControls(read,if(url==null) "Text" else "Page",{v,u->pageUnit=u;pageValuesJson=v.toJson().toString();apply(v,false,u)},
                onServing={n,size->servingName=n;servingSize=editText(size);servingFrom=null})}
            if(url!=null) {
                val uri=androidx.compose.ui.platform.LocalUriHandler.current
                TextAction("View on ${AdvancedSearch.host(url)}",{uri.openUri(url)})
            }
        }}
        ContentSection {
            Caption("Product")
            GlassTextField(name,{name=it},"Product name",Modifier.fillMaxWidth())
            GlassTextField(brand,{brand=it},"Brand",Modifier.fillMaxWidth())
            GlassTextField(barcode,{barcode=it.filter(Char::isDigit).take(14);barcodeFromPage=false},"Barcode",Modifier.fillMaxWidth())
            SecondaryButton("Find online",{openWeb()},Modifier.fillMaxWidth(),enabled=!vm.busy,icon=io.github.ebziboi.delta.R.drawable.ic_search)
            if(barcode.isNotBlank() && name.isNotBlank()) TextAction("Search by name instead",{openWeb(byName=true)})
        }
        ContentSection(tone=ModuleTone.SAGE) {
            Caption("Nutrition label")
            Text(if(scanner.reading) "Finding and reading the table…" else "Point the camera at the table; it's captured, cropped and read on this phone automatically",style=LocalDeltaType.current.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Scan label",scanner.camera,Modifier.weight(1f),enabled=!vm.busy && !scanner.reading)
                SecondaryButton("From photo",scanner.gallery,Modifier.weight(1f),enabled=!vm.busy && !scanner.reading)
            }
            if(reviewed && labelRead!=null && checked.isNotEmpty()) StatusPill("Values add up · ready to save",Color(LocalDeltaColors.current.good))
            labelRead?.let {read->
                read.warnings.forEach {Text(it,style=LocalDeltaType.current.bodySmall,color=Color(LocalDeltaColors.current.warn))}
                LabelReadControls(read,"Label",{v,u->labelUnit=u;labelValuesJson=v.toJson().toString();apply(v,true,u)},
                    onServing={n,size->servingName=n;servingSize=editText(size);servingFrom=null})
                TextAction("Edit recognised text",{transcript=read.raw;textEditor=true})
            }
            if(labelRead==null) TextAction("Paste label text",{transcript="";textEditor=true})
            lastPhoto?.let {photo->TextAction("Adjust crop",{cropUri=photo})}
            if(vm.busy) TextAction("Cancel reading",{vm.cancel()})
        }
        ContentSection {
            ServingFields(servingName,{servingName=it;servingFrom=null},servingSize,{servingSize=it;servingFrom=null},unit,pack,{pack=it},
                note=servingFrom?.let {"Taken from $it. Change it if you eat a different amount."})
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
                serving=servingSize.number()?.takeIf {it>0 && it<=100000},servingLabel=servingName.trim().ifBlank {"Serving"}.take(40),pack=pack.number()?.takeIf {it>0 && it<=100000},
                sourceUrl=page?.optString("url")?.takeIf {ProductPages.publicUrl(it)},labelChecked=checked.mapNotNull {key->Nutrient.entries.find {it.name==key}}.filter {it in values}.toSet()))
        },Modifier.fillMaxWidth(),enabled=valid && reviewed && !vm.busy)
    }
    cropUri?.let {text->LabelCropper(Uri.parse(text),{cropUri=null},onScan={crop->
        cropUri=null;lastPhoto=text
        vm.run {
            useLabel(LabelOcr.scan(context,Uri.parse(text),crop),Uri.parse(text))
        }
    })}
    if(textEditor) AlertDialog(onDismissRequest={textEditor=false},title={Text("Recognised label text")},text={
        OutlinedTextField(transcript,{transcript=it.take(18000)},Modifier.fillMaxWidth().heightIn(min=200.dp,max=420.dp),label={Text("Label transcription")})
    },confirmButton={HapticTextButton({val read=LabelParser.parse(transcript);labelJson=read.json();val column=read.columns[read.best];labelUnit=column.basis.unit;labelValuesJson=column.per100().toJson().toString();checked=emptyList();uncertain=emptyList();reviewed=false;textEditor=false}) {Text("Read text")}},dismissButton={HapticTextButton({textEditor=false}) {Text("Cancel")}})
}

/** The headline values a column states, in its own units: "548 kcal · Protein 39 g · Carbs 37 g · Fat 22 g". */
internal fun valuesLine(column:LabelColumn):String {
    val short=mapOf(Nutrient.CARBS to "Carbs",Nutrient.SATURATED to "Saturates")
    return listOf(Nutrient.ENERGY,Nutrient.PROTEIN,Nutrient.CARBS,Nutrient.FAT,Nutrient.SUGAR,Nutrient.SATURATED,Nutrient.FIBRE,Nutrient.SALT).mapNotNull {n->
        column.observations[n]?.takeIf {!it.bounded}?.let {o->
            val v=java.math.BigDecimal.valueOf(if(n==Nutrient.ENERGY) Math.round(o.value).toDouble() else o.value).round(java.math.MathContext(3)).stripTrailingZeros().toPlainString()
            if(n==Nutrient.ENERGY) "$v kcal" else "${short[n] ?: n.label} $v ${n.unit}"
        }
    }.take(6).joinToString(" · ")
}

/** "per 100 g", "per serving (250 g)", or what's still needed to convert the values. */
internal fun basisText(b:LabelBasis)=when {
    b.amount!=null -> "per "+(if(b.title.first().isDigit()) b.title else b.title.replaceFirstChar {it.lowercase()})
    b.title.lowercase() in listOf("serving","portion","pack") -> "per ${b.title.lowercase()} · add its weight to convert"
    else -> "· choose the quantity they're for"
}

@Composable
private fun LabelReadControls(read:LabelRead,title:String,onUse:(Nutrients,FoodUnit)->Unit,onServing:((String,Double)->Unit)?=null) {
    var selected by rememberSaveable(read.json()) {mutableIntStateOf(read.best)}
    val column=read.columns.getOrNull(selected) ?: return
    var amount by rememberSaveable(read.json(),selected) {mutableStateOf(column.basis.amount?.let(::editText) ?: "")}
    var unit by rememberSaveable(read.json(),selected) {mutableStateOf(column.basis.unit)}
    var menu by remember {mutableStateOf(false)}
    if(read.columns.size>1) Box {
        SecondaryButton("Column: ${column.basis.title}",{menu=true})
        DropdownMenu(menu,{menu=false}) {read.columns.forEachIndexed {i,c->HapticDropdownMenuItem({Text(c.basis.title)},{selected=i;menu=false})}}
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        val serving=column.basis.amount==null && column.basis.title.lowercase() in listOf("serving","portion","pack")
        NumberField(amount,{amount=it},if(serving) "One ${column.basis.title.lowercase()} weighs" else "Values per",Modifier.weight(1f))
        SegmentedControl(FoodUnit.entries,unit,{it.symbol},Modifier.weight(1f)) {unit=it}
    }
    column.observations.filterValues {it.bounded}.forEach {(n,v)->Text("${n.label}: ${v.raw} · enter a value manually if needed",style=LocalDeltaType.current.bodySmall,color=Color(LocalDeltaColors.current.warn))}
    if(column.basis.amount==null) Text(if(column.basis.title.lowercase() in listOf("serving","portion","pack")) "These values are per ${column.basis.title.lowercase()}. Enter what one weighs to convert them to per 100."
        else "Choose the quantity represented by this column.",style=LocalDeltaType.current.bodySmall)
    val values=column.per100(amount.number()?.takeIf {it>0 && it<=100000})
    SecondaryButton("Use ${title.lowercase()} values",{
        onUse(values,unit)
        // A per-serving column whose weight was given (or typed here) also becomes the food's serving.
        amount.number()?.takeIf {it>0 && it<=100000 && it!=100.0}?.let {onServing?.invoke(AdvancedSearch.servingName(column.basis.title),it)}
    },Modifier.fillMaxWidth(),enabled=values.isNotEmpty())
}
