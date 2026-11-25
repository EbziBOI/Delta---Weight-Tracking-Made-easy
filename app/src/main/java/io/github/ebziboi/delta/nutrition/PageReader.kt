package io.github.ebziboi.delta.nutrition

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.*
import kotlinx.coroutines.*
import org.json.JSONTokener
import kotlin.coroutines.resume

/** Why a pasted page could not be read, so the screen can offer the right way forward. */
internal class PageReadException(message:String):IllegalStateException(message)

/**
 * Reads a pasted product or recipe page without showing it: the page loads off-screen in a locked-down WebView
 * (no file access, pop-ups or non-https navigation), its scripts get time to render the nutrition table, and the
 * same extraction as the visible product browser runs until a complete table appears or time runs out.
 */
internal object PageReader {
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun read(context:Context,url:String,expectedBarcode:String?=null,html:String?=null,timeoutMs:Long=22000):String = withContext(Dispatchers.Main) {
        if(!ProductPages.publicUrl(url)) throw IllegalArgumentException("Paste a public https link to a product or recipe page")
        val host=AdvancedSearch.host(url)
        val web=WebView(context.applicationContext)
        var finished=false;var failure:String?=null;var status=0
        try {
            web.settings.apply {
                javaScriptEnabled=true;domStorageEnabled=true;allowFileAccess=false;allowContentAccess=false
                mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW;setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically=false;mediaPlaybackRequiresUserGesture=true;blockNetworkImage=true
            }
            web.webViewClient=object:WebViewClient() {
                override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=!ProductPages.publicUrl(request.url.toString())
                override fun onPageFinished(view:WebView,url:String) {finished=true}
                override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError) {
                    if(request.isForMainFrame) failure="Couldn't open $host. Check the link and your connection."
                }
                override fun onReceivedHttpError(view:WebView,request:WebResourceRequest,response:WebResourceResponse) {
                    if(request.isForMainFrame) status=response.statusCode
                }
            }
            // A phone-sized layout so responsive pages render their mobile table.
            val w=View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY);val h=View.MeasureSpec.makeMeasureSpec(2340,View.MeasureSpec.EXACTLY)
            web.measure(w,h);web.layout(0,0,1080,2340)
            if(html!=null) web.loadDataWithBaseURL(url,html,"text/html","UTF-8",null) else web.loadUrl(url)
            var best:String?=null;var bestScore=-1;var ticks=0
            withTimeoutOrNull(timeoutMs) {
                while(true) {
                    delay(if(finished) 900 else 600);ticks++
                    failure?.let {throw PageReadException(it)}
                    // An error page (404, bot check) has nothing more coming: stop once it has loaded rather than waiting out the clock.
                    if(finished && status>=400 && ticks>=3) break
                    if(!finished && web.progress<40) continue
                    val raw=extract(web) ?: continue
                    val page=runCatching {ProductPages.parse(raw,expectedBarcode)}.getOrNull() ?: continue
                    val score=AdvancedSearch.score(page.read)
                    if(score>bestScore) {best=raw;bestScore=score}
                    // Pages that keep loading adverts never "finish"; a complete table is enough after a few seconds.
                    if(AdvancedSearch.complete(page.read) && (finished || ticks>=6)) break
                }
            }
            failure?.let {throw PageReadException(it)}
            when {
                best!=null && bestScore>0 -> best!!
                status==401 || status==403 || status==429 -> throw PageReadException("$host blocked automatic reading. Open the page to load it yourself, then import it.")
                status>=400 -> throw PageReadException("$host returned an error ($status). Check the link.")
                else -> throw PageReadException("No nutrition table found on $host. Open the page to find it, or paste its nutrition text.")
            }
        } finally {web.stopLoading();web.destroy()}
    }

    private suspend fun extract(web:WebView):String? = suspendCancellableCoroutine {c->
        web.evaluateJavascript(ProductPages.extractScript) {result->
            if(c.isActive) c.resume(runCatching {JSONTokener(result).nextValue() as? String}.getOrNull()?.takeIf {it.length<=200000})
        }
    }
}
