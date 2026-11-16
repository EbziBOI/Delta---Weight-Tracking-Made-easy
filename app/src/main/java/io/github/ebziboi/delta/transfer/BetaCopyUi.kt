package io.github.ebziboi.delta.transfer

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.data.HealthState
import io.github.ebziboi.delta.ui.*
import kotlinx.coroutines.launch

@Composable
fun BetaWelcome(vm:AppViewModel,health:HealthState?,onConnect:()->Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        ScreenHeader("Delta Beta")
        Text("Stage 2 preview",style=LocalDeltaType.current.title)
        BetaCopyCard(health,onConnect)
        SecondaryButton("Start with no data",{vm.update {it.copy(onboardingDone=true)}},Modifier.fillMaxWidth())
    }
}
@Composable
fun BetaCopyCard(health:HealthState?,onConnect:()->Unit) {
    val context=LocalContext.current;val app=context.applicationContext as DeltaApp
    val scope=rememberCoroutineScope();var busy by remember {mutableStateOf(false)};var message by remember {mutableStateOf<String?>(null)}
    var confirm by remember {mutableStateOf<String?>(null)}
    val uriHandler=androidx.compose.ui.platform.LocalUriHandler.current
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {result->
        if(result.resultCode==Activity.RESULT_OK) result.data?.data?.let {uri->
            scope.launch {
                busy=true
                try {TransferSnapshot.apply(context,TransferSnapshot.read(context,uri));message="Copied to beta. Your current Delta app is unchanged."}
                catch(_:Exception) {message="The copy could not be imported. Delta's original data has not changed."}
                finally {busy=false}
            }
        }
    }
    fun copy() {
        try {
            check(context.packageManager.checkSignatures(context.packageName,TransferSnapshot.STABLE)==PackageManager.SIGNATURE_MATCH)
            launcher.launch(Intent().setComponent(ComponentName(TransferSnapshot.STABLE,TransferSnapshot.ACTIVITY)))
        } catch(_:Exception) {message="Install the optional Delta transfer update from the beta release, then try Copy from Delta again."}
    }
    ContentSection(framed=true) {
        Caption("Independent test copy")
        Text("Test changes stay in Delta Beta. Health Connect is read-only, and automatic refresh keeps your beta snapshot.",style=LocalDeltaType.current.bodySmall)
        message?.let {Text(it,style=LocalDeltaType.current.bodySmall)}
        PrimaryButton(if(busy) "Copying…" else "Copy from Delta",{confirm="delta"},Modifier.fillMaxWidth(),enabled=!busy)
        if(message?.contains("transfer update")==true) TextAction("Open beta downloads",{uriHandler.openUri("https://github.com/EbziBOI/Delta---Weight-Tracking-Made-easy/releases/tag/stage2-beta-0.2.40-beta.3")})
        SecondaryButton(if(health?.canRead==true || health?.canReadBodyFat==true) "Copy Health Connect readings" else "Connect Health Connect",{
            if(health?.canRead==true || health?.canReadBodyFat==true) confirm="health" else onConnect()
        },Modifier.fillMaxWidth(),enabled=!busy)
    }
    confirm?.let {kind->
        AlertDialog(onDismissRequest={confirm=null},title={Text("Copy into beta?")},text={Text("This replaces the beta's measurements${if(kind=="delta") ", goals and appearance" else ""}. Your current Delta app, Health Connect records, and beta food diary stay unchanged.")},
            confirmButton={TextButton({confirm=null;if(kind=="delta") copy() else scope.launch {
                busy=true
                try {app.repo.copyHealthConnectForBeta();app.repo.updateSettings {it.copy(onboardingDone=true)};message="Readings copied to beta"}
                catch(_:Exception) {message="Couldn't copy readings. Check Health Connect read access and try again."}
                finally {busy=false}
            }}) {Text("Copy")}},dismissButton={TextButton({confirm=null}) {Text("Cancel")}})
    }
}
