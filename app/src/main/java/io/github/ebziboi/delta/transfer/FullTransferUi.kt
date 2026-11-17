package io.github.ebziboi.delta.transfer

import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.ui.*
import kotlinx.coroutines.*
import java.io.File
import io.github.ebziboi.delta.nutrition.readBytesBounded

@Composable
fun FullTransferCard() {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val beta=context.packageName==TransferSnapshot.BETA
    val target=if(beta) TransferSnapshot.STABLE else TransferSnapshot.BETA;val targetName=if(beta) "Delta" else "Delta Beta"
    var busy by remember {mutableStateOf(false)};var message by remember {mutableStateOf<String?>(null)}
    var pending by remember {mutableStateOf<String?>(null)}
    val copy=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {r->message=if(r.resultCode==Activity.RESULT_OK) "Copied to $targetName. This app's data stays here." else "Copy cancelled. This app's data stays here."}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri->if(uri!=null) scope.launch {
        busy=true;try {val raw=FullTransfer.create(context).toString();require(raw.toByteArray().size<=TransferSnapshot.MAX_BYTES);withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri)?.use {it.write(raw.toByteArray())} ?: error("Can't write backup")};message="Full backup saved"}
        catch(_:Exception) {message="Couldn't save the backup. Your data is unchanged."} finally {busy=false}
    }}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null) scope.launch {
        busy=true;try {val raw=FullTransfer.read(context,uri);FullTransfer.parse(raw);pending=raw} catch(_:Exception) {message="This file isn't a supported full Delta backup."} finally {busy=false}
    }}
    ContentSection {
        Text("Move between Delta apps",style=LocalDeltaType.current.title)
        Text("Copy measurements, diary, foods, recipes, fasting, targets and appearance. API keys, Android permissions and placed widgets stay with each app.",style=LocalDeltaType.current.bodySmall)
        message?.let {Text(it,style=LocalDeltaType.current.bodySmall)}
        PrimaryButton("Copy to $targetName",{scope.launch {
            busy=true
            try {
                check(context.packageManager.checkSignatures(context.packageName,target)==PackageManager.SIGNATURE_MATCH)
                val raw=FullTransfer.create(context).toString();require(raw.toByteArray().size<=TransferSnapshot.MAX_BYTES)
                val file=withContext(Dispatchers.IO) {val dir=File(context.cacheDir,"beta-transfer").apply {mkdirs()};dir.listFiles()?.forEach {it.delete()};File.createTempFile("full-",".json",dir).apply {writeText(raw)}}
                val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
                copy.launch(Intent().setComponent(ComponentName(target,FullTransferInActivity::class.java.name)).setDataAndType(uri,"application/json")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply {clipData=ClipData.newRawUri("Delta copy",uri)})
            } catch(_:Exception) {message="Install the matching base-app return update from this beta release, or export a full backup for later import."} finally {busy=false}
        }},Modifier.fillMaxWidth(),enabled=!busy)
        if(File(context.noBackupFilesDir,"before-full-transfer.json").exists()) TextAction("Restore pre-import backup",{scope.launch {
            busy=true;try {val raw=withContext(Dispatchers.IO) {File(context.noBackupFilesDir,"before-full-transfer.json").inputStream().use {it.readBytesBounded(TransferSnapshot.MAX_BYTES).toString(Charsets.UTF_8)}};FullTransfer.parse(raw);pending=raw} catch(_:Exception) {message="Couldn't read the pre-import backup"} finally {busy=false}
        }})
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Export backup",{export.launch("Delta-full-backup.json")},Modifier.weight(1f),enabled=!busy)
            SecondaryButton("Import backup",{import.launch(arrayOf("application/json","application/octet-stream"))},Modifier.weight(1f),enabled=!busy)
        }
    }
    pending?.let {raw->AlertDialog(onDismissRequest={pending=null},title={Text("Replace this app's data?")},text={Text("This replaces measurements, nutrition and settings with the backup. A private pre-import backup is kept on this phone. Permissions and API keys stay unchanged.")},
        confirmButton={HapticTextButton({pending=null;scope.launch {busy=true;try {FullTransfer.apply(context,raw);message="Backup imported"} catch(_:Exception) {message="Import failed. The pre-import backup has been retained."} finally {busy=false}}}) {Text("Replace data")}},dismissButton={HapticTextButton({pending=null}) {Text("Cancel")}})}
}

/** The receiver always asks for consent and accepts only the sibling app signed with the same key. */
class FullTransferInActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val source=if(packageName==TransferSnapshot.STABLE) TransferSnapshot.BETA else TransferSnapshot.STABLE
        val uri=intent.data
        if(callingPackage!=source || packageManager.checkSignatures(packageName,source)!=PackageManager.SIGNATURE_MATCH || uri?.authority!="$source.files") {setResult(RESULT_CANCELED);finish();return}
        setContent {
            val settings by (application as DeltaApp).repo.settings.collectAsStateWithLifecycle(initialValue=AppSettings())
            DeltaTheme(settings) {Surface(color=Color(LocalDeltaColors.current.bg),contentColor=Color(LocalDeltaColors.current.text)) {
                val scope=rememberCoroutineScope();var raw by remember {mutableStateOf<String?>(null)};var error by remember {mutableStateOf<String?>(null)};var busy by remember {mutableStateOf(false)}
                LaunchedEffect(uri) {try {raw=FullTransfer.read(this@FullTransferInActivity,uri).also {FullTransfer.parse(it)}} catch(_:Exception) {error="The copy could not be read. Your data is unchanged."}}
                Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    ScreenHeader("Copy into ${if(packageName==TransferSnapshot.STABLE) "Delta" else "Delta Beta"}")
                    Text("Replace this app's measurements, nutrition and settings? The sending app stays unchanged. API keys and permissions aren't copied.")
                    raw?.let {val p=remember(it) {FullTransfer.parse(it)};Text("${p.measurements.weights.size} weights · ${p.measurements.fat.size} body-fat readings · ${p.nutrition.entries.size} diary entries · ${p.nutrition.foods.size} foods · ${p.nutrition.fasts.size} fasts")}
                    error?.let {Text(it)}
                    PrimaryButton(if(busy) "Copying…" else "Replace data",{busy=true;scope.launch {
                        try {FullTransfer.apply(this@FullTransferInActivity,raw!!);setResult(RESULT_OK);finish()}
                        catch(_:Exception) {error="Import failed. The private pre-import backup has been retained.";busy=false}
                    }},Modifier.fillMaxWidth(),enabled=raw!=null && !busy)
                    SecondaryButton("Cancel",{setResult(RESULT_CANCELED);finish()},Modifier.fillMaxWidth(),enabled=!busy)
                }
            }}
        }
    }
}
