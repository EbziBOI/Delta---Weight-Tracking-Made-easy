package io.github.ebziboi.delta.nutrition

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.ui.*
import org.json.JSONTokener

class ProductBrowserActivity:ComponentActivity() {
    private var browser:WebView?=null
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val query=intent.getStringExtra("query").orEmpty().take(200)
        val initial=intent.getStringExtra("url")?.takeIf(ProductPages::publicUrl) ?: ProductPages.searchUrl(query)
        setContent {
            val appSettings by (application as DeltaApp).repo.settings.collectAsStateWithLifecycle(initialValue=AppSettings())
            DeltaTheme(appSettings) {
                var address by remember {mutableStateOf(initial)};var progress by remember {mutableIntStateOf(0)};var message by remember {mutableStateOf<String?>(null)}
                BackHandler {if(browser?.canGoBack()==true) browser?.goBack() else finish()}
                Surface(color=Color(LocalDeltaColors.current.bg),contentColor=Color(LocalDeltaColors.current.text)) {
                    Column(Modifier.fillMaxSize().systemBarsPadding()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text(Uri.parse(address).host.orEmpty(),Modifier.weight(1f),maxLines=1,style=LocalDeltaType.current.bodySmall)
                            TextAction("Close",{finish()})
                        }
                        if(progress<100) LinearProgressIndicator({progress/100f},Modifier.fillMaxWidth())
                        message?.let {Text(it,Modifier.padding(12.dp),style=LocalDeltaType.current.bodySmall)}
                        AndroidView(factory={context->WebView(context).apply {
                            browser=this
                            settings.javaScriptEnabled=true;settings.domStorageEnabled=true
                            settings.allowFileAccess=false;settings.allowContentAccess=false
                            settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.setSupportMultipleWindows(false);settings.javaScriptCanOpenWindowsAutomatically=false
                            settings.mediaPlaybackRequiresUserGesture=true
                            webViewClient=object:WebViewClient() {
                                override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean = !ProductPages.publicUrl(request.url.toString())
                                override fun onPageFinished(view:WebView,url:String) {address=url}
                            }
                            webChromeClient=object:WebChromeClient() {override fun onProgressChanged(view:WebView,newProgress:Int) {progress=newProgress}}
                            if(savedInstanceState?.getBundle("browser")?.let {restoreState(it)}==null) loadUrl(initial)
                        }},modifier=Modifier.weight(1f))
                        Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            SecondaryButton("First result",{
                                browser?.evaluateJavascript(ProductPages.firstResultScript) {result->
                                    val url=runCatching {JSONTokener(result).nextValue() as? String}.getOrNull()
                                    if(url!=null && ProductPages.publicUrl(url)) browser?.loadUrl(url) else message="Choose a product result on this page."
                                }
                            },Modifier.weight(1f),enabled=Uri.parse(address).host.orEmpty().matches(Regex("(?:www\\.)?google\\.[a-z.]+")))
                            PrimaryButton("Import page",{
                                browser?.evaluateJavascript(ProductPages.extractScript) {result->
                                    val raw=runCatching {JSONTokener(result).nextValue() as? String}.getOrNull()
                                    if(raw!=null && raw.length<=200000) {
                                        runCatching {ProductPages.parse(raw,intent.getStringExtra("barcode"))}.onSuccess {
                                            setResult(RESULT_OK,Intent().putExtra("page",raw));finish()
                                        }.onFailure {message=it.message ?: "Could not read this page"}
                                    } else message="Could not read this page. Open a product page or try its label photo."
                                }
                            },Modifier.weight(1f),enabled=progress==100)
                        }
                    }
                }
            }
        }
    }
    override fun onSaveInstanceState(outState:Bundle) {outState.putBundle("browser",Bundle().also {browser?.saveState(it)});super.onSaveInstanceState(outState)}
    override fun onDestroy() {browser?.apply {stopLoading();destroy()};browser=null;super.onDestroy()}
}
