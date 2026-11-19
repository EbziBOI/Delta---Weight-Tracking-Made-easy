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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
        enableEdgeToEdge(androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            val settings by (application as DeltaApp).repo.settings.collectAsStateWithLifecycle(initialValue=AppSettings())
            DeltaTheme(settings) {
                fun complete(code: String) { setResult(RESULT_OK,Intent().putExtra("barcode",code));finish() }
                ScannerScreen(::complete) { finish() }
            }
        }
    }
}

private val barcodePattern=Regex("[0-9]{8}|[0-9]{12,14}")

@Composable
private fun ScannerScreen(onCode:(String)->Unit,onClose:()->Unit) {
    val context=LocalContext.current;val c=LocalDeltaColors.current;val haptic=LocalHapticFeedback.current;val scope=rememberCoroutineScope()
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed=it }
    var manual by rememberSaveable { mutableStateOf("") };var typing by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) };var reading by remember { mutableStateOf(false) }
    var torch by rememberSaveable { mutableStateOf(false) };var zoomed by rememberSaveable { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) };var found by remember { mutableStateOf(false) }
    var cropping by rememberSaveable { mutableStateOf<String?>(null) }
    val camera=remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    val focusRequest=remember { mutableStateOf<Offset?>(null) }
    fun accept(code:String) {
        if(found) return
        found=true;haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        scope.launch { kotlinx.coroutines.delay(220);onCode(code) }
    }
    fun decodePhoto(uri:android.net.Uri,crop:LabelCrop,offerCrop:Boolean) {
        reading=true;error=null
        scope.launch {
            val code=runCatching { BarcodePhotos.decode(context,uri,crop) }.getOrNull()
            reading=false
            when {
                code!=null -> accept(code)
                offerCrop -> { cropping=uri.toString();error="Crop around the barcode so it can be read" }
                else -> error="No barcode recognised. Crop closer, rotate the photo, or type the number."
            }
        }
    }
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if(uri!=null) decodePhoto(uri,LabelCrop(),true) }
    fun openGallery() = runCatching { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.onFailure { error="No photo picker is available" }
    LaunchedEffect(torch,camera.value) { camera.value?.takeIf { hasFlash }?.cameraControl?.enableTorch(torch) }
    LaunchedEffect(zoomed,camera.value) { camera.value?.cameraControl?.setZoomRatio(if(zoomed) 2f else 1f) }
    val ink=Color.White
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if(allowed) {
            BarcodeCamera(Modifier.fillMaxSize(),camera,focusRequest,{ hasFlash=it },{ accept(it) },{ error=it })
            Viewfinder(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { focusRequest.value=it } },Color(c.accent),found)
        } else Column(Modifier.align(Alignment.Center).padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)) {
            DeltaIcon(io.github.ebziboi.delta.R.drawable.ic_barcode,null,tint=ink,size=44.dp)
            Text("Camera access is needed to scan a barcode",color=ink,style=LocalDeltaType.current.title,textAlign=TextAlign.Center)
            Text("You can still type the number or choose a photo of it.",color=ink.copy(alpha=.7f),style=LocalDeltaType.current.bodySmall,textAlign=TextAlign.Center)
            PrimaryButton("Allow camera",{permission.launch(Manifest.permission.CAMERA)})
        }
        // Top controls
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
            ScannerButton(io.github.ebziboi.delta.R.drawable.ic_close,"Close",onClose)
            Text("Scan barcode",color=ink,style=LocalDeltaType.current.title,modifier=Modifier.weight(1f),textAlign=TextAlign.Center)
            if(allowed && hasFlash) ScannerButton(if(torch) io.github.ebziboi.delta.R.drawable.ic_flash_on else io.github.ebziboi.delta.R.drawable.ic_flash_off,
                if(torch) "Turn flashlight off" else "Turn flashlight on",{ torch=!torch },active=torch)
            else Spacer(Modifier.size(48.dp))
        }
        // Status beneath the frame
        Column(Modifier.align(Alignment.Center).padding(top=230.dp).padding(horizontal=32.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            if(allowed) Text(when { found->"Got it";reading->"Reading photo…";else->"Line up the barcode inside the frame · tap to focus" },
                color=ink.copy(alpha=.85f),style=LocalDeltaType.current.bodySmall,textAlign=TextAlign.Center)
            error?.let { Text(it,color=Color(c.warn),style=LocalDeltaType.current.bodySmall,textAlign=TextAlign.Center,modifier=Modifier.padding(top=6.dp)) }
        }
        // Bottom controls; manual entry slides up over them.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            AnimatedVisibility(typing || !allowed) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.card)).background(Color(c.bg).copy(alpha=.94f)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    GlassTextField(manual,{manual=it.filter(Char::isDigit).take(14)},"Barcode number",Modifier.fillMaxWidth())
                    PrimaryButton("Find product",{onCode(manual)},Modifier.fillMaxWidth(),manual.matches(barcodePattern))
                    if(!manual.matches(barcodePattern) && manual.isNotEmpty()) Text("Barcodes have 8, 12, 13 or 14 digits",style=LocalDeltaType.current.bodySmall,color=Color(c.textDim))
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                ScannerPill(io.github.ebziboi.delta.R.drawable.ic_edit,if(typing) "Hide keypad" else "Type number") { typing=!typing }
                Spacer(Modifier.weight(1f))
                if(allowed) {
                    ScannerPill(null,if(zoomed) "2×" else "1×",active=zoomed) { zoomed=!zoomed }
                    Spacer(Modifier.width(10.dp))
                }
                ScannerButton(io.github.ebziboi.delta.R.drawable.ic_photo_library,"Choose barcode photo",::openGallery,size=56.dp)
            }
        }
    }
    cropping?.let { uri -> LabelCropper(android.net.Uri.parse(uri),{ cropping=null },{ crop -> cropping=null;decodePhoto(android.net.Uri.parse(uri),crop,false) },title="Barcode photo",actionLabel="Read barcode") }
}

@Composable private fun ScannerButton(icon:Int,label:String,onClick:()->Unit,size:androidx.compose.ui.unit.Dp=48.dp,active:Boolean=false) {
    val accent=Color(LocalDeltaColors.current.accent)
    Box(Modifier.size(size).clip(CircleShape).background(if(active) accent else Color.Black.copy(alpha=.45f)).border(1.dp,Color.White.copy(alpha=.22f),CircleShape)
        .clickable(role=Role.Button,onClickLabel=label,onClick=onClick).semantics { contentDescription=label },contentAlignment=Alignment.Center) {
        DeltaIcon(icon,null,tint=if(active) Color.Black else Color.White,size=size*.46f)
    }
}
@Composable private fun ScannerPill(icon:Int?,label:String,active:Boolean=false,onClick:()->Unit) {
    val accent=Color(LocalDeltaColors.current.accent)
    Row(Modifier.heightIn(min=48.dp).clip(RoundedCornerShape(24.dp)).background(if(active) accent else Color.Black.copy(alpha=.45f)).border(1.dp,Color.White.copy(alpha=.22f),RoundedCornerShape(24.dp))
        .clickable(role=Role.Button,onClick=onClick).padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        icon?.let { DeltaIcon(it,null,tint=if(active) Color.Black else Color.White,size=18.dp) }
        Text(label,color=if(active) Color.Black else Color.White,style=LocalDeltaType.current.bodyStrong)
    }
}

/** Dims everything outside a barcode-shaped window, with corner guides and a sweeping scan line. */
@Composable private fun Viewfinder(modifier:Modifier,accent:Color,found:Boolean) {
    val motion=LocalMotion.current
    val sweep=if(motion && !found) rememberInfiniteTransition(label="scan").animateFloat(0f,1f,infiniteRepeatable(tween(1700,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="scan-line").value else .5f
    val success by animateFloatAsState(if(found) 1f else 0f,tween(180),label="scan-found")
    Canvas(modifier.semantics { contentDescription="Barcode viewfinder" }) {
        val w=size.width*.78f;val h=w*.52f;val left=(size.width-w)/2;val top=size.height/2-h/2-24.dp.toPx()
        val radius=18.dp.toPx()
        val window=Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(left,top,left+w,top+h,androidx.compose.ui.geometry.CornerRadius(radius))) }
        clipPath(window,ClipOp.Difference) { drawRect(Color.Black.copy(alpha=.55f)) }
        val ink=lerp(Color.White,accent,success);val stroke=4.dp.toPx();val arm=28.dp.toPx()
        listOf(Offset(left,top) to Offset(1f,1f),Offset(left+w,top) to Offset(-1f,1f),Offset(left,top+h) to Offset(1f,-1f),Offset(left+w,top+h) to Offset(-1f,-1f)).forEach { (corner,dir) ->
            val p=Path().apply {
                moveTo(corner.x,corner.y+dir.y*arm);lineTo(corner.x,corner.y+dir.y*radius)
                quadraticTo(corner.x,corner.y,corner.x+dir.x*radius,corner.y);lineTo(corner.x+dir.x*arm,corner.y)
            }
            drawPath(p,ink,style=Stroke(stroke,cap=StrokeCap.Round))
        }
        if(success>0) drawRoundRect(accent.copy(alpha=.22f*success),Offset(left,top),androidx.compose.ui.geometry.Size(w,h),androidx.compose.ui.geometry.CornerRadius(radius))
        else {
            val y=top+12.dp.toPx()+(h-24.dp.toPx())*sweep
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent,accent,Color.Transparent),left+12.dp.toPx(),left+w-12.dp.toPx()),Offset(left+12.dp.toPx(),y),Offset(left+w-12.dp.toPx(),y),2.dp.toPx())
        }
    }
}

@Composable
private fun BarcodeCamera(modifier: Modifier,camera:MutableState<androidx.camera.core.Camera?>,focus:MutableState<Offset?>,onFlash:(Boolean)->Unit,onFound: (String)->Unit,onError: (String)->Unit) {
    val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current
    val preview=remember { PreviewView(context).apply { scaleType=PreviewView.ScaleType.FILL_CENTER } };val main=remember { ContextCompat.getMainExecutor(context) }
    val found=rememberUpdatedState(onFound);val error=rememberUpdatedState(onError);val flash=rememberUpdatedState(onFlash)
    AndroidView({preview},modifier)
    LaunchedEffect(focus.value) {
        val at=focus.value ?: return@LaunchedEffect
        val cam=camera.value ?: return@LaunchedEffect
        runCatching { cam.cameraControl.startFocusAndMetering(androidx.camera.core.FocusMeteringAction.Builder(preview.meteringPointFactory.createPoint(at.x,at.y)).build()) }
    }
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
                val bound=provider!!.bindToLifecycle(lifecycle,CameraSelector.DEFAULT_BACK_CAMERA,cameraPreview!!,analysis!!)
                camera.value=bound;flash.value(bound.cameraInfo.hasFlashUnit())
            } catch(_: Exception) { error.value("Camera unavailable. Enter the barcode number below.") }
        },main)
        onDispose {
            disposed=true;analysis?.clearAnalyzer();camera.value=null
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
