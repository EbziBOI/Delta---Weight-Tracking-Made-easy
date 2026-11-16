package io.github.ebziboi.delta.transfer

import android.content.Intent
import android.content.ClipData
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.data.SettingsStore
import io.github.ebziboi.delta.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Consent screen, callable only by the beta signed with the same Delta key. */
class TransferOutActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        if(packageName!=TransferSnapshot.STABLE || callingPackage!=TransferSnapshot.BETA ||
            packageManager.checkSignatures(packageName,TransferSnapshot.BETA)!=PackageManager.SIGNATURE_MATCH) {
            setResult(RESULT_CANCELED);finish();return
        }
        setContent {
            val settings by SettingsStore(this).settings.collectAsStateWithLifecycle(initialValue=AppSettings())
            DeltaTheme(settings) {
                val c=LocalDeltaColors.current
                Surface(color=Color(c.bg),contentColor=Color(c.text)) {
                    val scope=rememberCoroutineScope();var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf(false)}
                    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                        ScreenHeader("Copy to Beta")
                        ContentSection {
                            Text("Copy your Delta data",style=LocalDeltaType.current.title)
                            Text("Weight and body-fat history, goals, units, appearance and card layouts will be copied to Delta Beta.")
                            Text("Delta's data stays here. Beta test edits stay in the beta. Permissions, placed widgets and API keys are not transferred.",style=LocalDeltaType.current.bodySmall)
                        }
                        if(error) Text("The copy could not be prepared. Check storage and your background photo, then try again.")
                        PrimaryButton(if(busy) "Preparing copy…" else "Copy to Delta Beta",{
                            if(!busy) {busy=true;scope.launch {
                                try {
                                    val file=withContext(Dispatchers.IO) {
                                        val bytes=TransferSnapshot.create(this@TransferOutActivity).toString().toByteArray()
                                        require(bytes.size<=TransferSnapshot.MAX_BYTES)
                                        val dir=File(cacheDir,"beta-transfer").apply {mkdirs()};dir.listFiles()?.forEach {it.delete()}
                                        File.createTempFile("delta-",".json",dir).apply {writeBytes(bytes)}
                                    }
                                    val uri=FileProvider.getUriForFile(this@TransferOutActivity,"$packageName.files",file)
                                    val result=Intent().setDataAndType(uri,"application/json").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    result.clipData=ClipData.newRawUri("Delta beta copy",uri)
                                    setResult(RESULT_OK,result);finish()
                                } catch(_:Exception) {error=true;busy=false}
                            }}
                        },Modifier.fillMaxWidth(),enabled=!busy)
                        SecondaryButton("Cancel",{setResult(RESULT_CANCELED);finish()},Modifier.fillMaxWidth(),enabled=!busy)
                    }
                }
            }
        }
    }
}
