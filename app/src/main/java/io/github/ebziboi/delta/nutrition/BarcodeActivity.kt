package io.github.ebziboi.delta.nutrition

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.ui.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class BarcodeActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by (application as DeltaApp).repo.settings.collectAsStateWithLifecycle(initialValue=AppSettings())
            DeltaTheme(settings) {
                val c=LocalDeltaColors.current
                Surface(color=Color(c.bg),contentColor=Color(c.text)) {
                    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) }
                    var manual by remember { mutableStateOf("") };var error by remember { mutableStateOf<String?>(null) }
                    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed=it }
                    fun complete(code: String) { setResult(RESULT_OK,Intent().putExtra("barcode",code));finish() }
                    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text("Scan barcode",style=LocalDeltaType.current.title);TextAction("Close",{finish()})
                        }
                        if(allowed) BarcodeCamera(Modifier.weight(1f),{ complete(it) },{ error=it })
                        else { Text("Camera access is needed to scan a barcode.");SecondaryButton("Allow camera",{permission.launch(Manifest.permission.CAMERA)}) }
                        error?.let { Text(it,color=Color(c.warn)) }
                        GlassTextField(manual,{manual=it.filter(Char::isDigit).take(14)},"Barcode number",Modifier.fillMaxWidth())
                        PrimaryButton("Find product",{complete(manual)},Modifier.fillMaxWidth(),manual.matches(Regex("[0-9]{8}|[0-9]{12,14}")))
                    }
                }
            }
        }
    }
}

@Composable
private fun BarcodeCamera(modifier: Modifier,onFound: (String)->Unit,onError: (String)->Unit) {
    val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current
    val preview=remember { PreviewView(context) };val main=remember { ContextCompat.getMainExecutor(context) }
    val found=rememberUpdatedState(onFound);val error=rememberUpdatedState(onError)
    AndroidView({preview},modifier)
    DisposableEffect(lifecycle) {
        val executor=Executors.newSingleThreadExecutor();val done=AtomicBoolean(false);var disposed=false
        var lastFrame=0L
        val future=ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider?=null;var analysis: ImageAnalysis?=null;var cameraPreview: Preview?=null
        future.addListener({
            if(!disposed) try {
                provider=future.get();cameraPreview=Preview.Builder().build().apply { setSurfaceProvider(preview.surfaceProvider) }
                analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().apply {
                    setAnalyzer(executor) { proxy ->
                        try {
                            val tick=android.os.SystemClock.elapsedRealtime()
                            if(!done.get() && !disposed && tick-lastFrame>250) {
                                lastFrame=tick
                                val plane=proxy.planes[0];val buffer=plane.buffer;val base=buffer.position()
                                val pixels=ByteArray(proxy.width*proxy.height)
                                for(y in 0 until proxy.height) for(x in 0 until proxy.width)
                                    pixels[y*proxy.width+x]=buffer.get(base+y*plane.rowStride+x*plane.pixelStride)
                                decodeBarcode(pixels,proxy.width,proxy.height)?.let {code ->
                                    if(done.compareAndSet(false,true)) main.execute {if(!disposed) found.value(code)}
                                }
                            }
                        } catch(_: Exception) { /* A frame may close as its camera is detached. */ }
                        finally {proxy.close()}
                    }
                }
                provider!!.bindToLifecycle(lifecycle,CameraSelector.DEFAULT_BACK_CAMERA,cameraPreview!!,analysis!!)
            } catch(_: Exception) { error.value("Camera unavailable. Enter the barcode number below.") }
        },main)
        onDispose {
            disposed=true;analysis?.clearAnalyzer()
            analysis?.let { provider?.unbind(it) };cameraPreview?.let { provider?.unbind(it) }
            executor.shutdown()
        }
    }
}

/** Pure, offline luminance decoder. Try both sensor orientations, including UPC-E expansion. */
internal fun decodeBarcode(pixels: ByteArray,width: Int,height: Int): String? {
    require(width>0 && height>0 && pixels.size==width*height)
    val hints=mapOf<com.google.zxing.DecodeHintType,Any>(
        com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.EAN_13,
            com.google.zxing.BarcodeFormat.EAN_8,com.google.zxing.BarcodeFormat.UPC_A,
            com.google.zxing.BarcodeFormat.UPC_E,com.google.zxing.BarcodeFormat.ITF),
        com.google.zxing.DecodeHintType.TRY_HARDER to true)
    fun decode(data:ByteArray,w:Int,h:Int):String? {
        val reader=com.google.zxing.MultiFormatReader()
        return try {
            val source=com.google.zxing.PlanarYUVLuminanceSource(data,w,h,0,0,w,h,false)
            val result=reader.decode(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source)),hints)
            val code=if(result.barcodeFormat==com.google.zxing.BarcodeFormat.UPC_E) com.google.zxing.oned.UPCEReader.convertUPCEtoUPCA(result.text) else result.text
            code.takeIf {it.matches(Regex("[0-9]{8}|[0-9]{12,14}"))}
        } catch(_:com.google.zxing.ReaderException) {null} finally {reader.reset()}
    }
    decode(pixels,width,height)?.let {return it}
    val rotated=ByteArray(pixels.size)
    for(y in 0 until height) for(x in 0 until width) rotated[x*height+height-y-1]=pixels[y*width+x]
    return decode(rotated,height,width)
}
