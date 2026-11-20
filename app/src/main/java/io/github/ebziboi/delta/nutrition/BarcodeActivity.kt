package io.github.ebziboi.delta.nutrition

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.ui.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Delta's own camera. "barcode" mode returns `barcode`; "label" mode photographs a nutrition label and returns
 * `photo` (a content URI), capturing automatically once a readable table is in view. Both modes offer every back
 * lens the phone exposes (ultrawide, main and telephotos), pinch zoom, tap to focus and the flashlight.
 */
class BarcodeActivity: ComponentActivity() {
    companion object {
        const val MODE="mode";const val LABEL="label"
        fun label(context:android.content.Context)=Intent(context,BarcodeActivity::class.java).putExtra(MODE,LABEL)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val labelMode=intent.getStringExtra(MODE)==LABEL
        setContent {
            val settings by (application as DeltaApp).repo.settings.collectAsStateWithLifecycle(initialValue=AppSettings())
            DeltaTheme(settings) {
                fun complete(code: String) { setResult(RESULT_OK,Intent().putExtra("barcode",code));finish() }
                fun photo(uri: android.net.Uri) { setResult(RESULT_OK,Intent().putExtra("photo",uri.toString()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));finish() }
                ScannerScreen(labelMode,::complete,::photo) { finish() }
            }
        }
    }
}

private val barcodePattern=Regex("[0-9]{8}|[0-9]{12,14}")

/** What the live label reader currently sees. */
private data class LiveLabel(val values:Int,val energy:Boolean,val frame:Long)

@Composable
private fun ScannerScreen(labelMode:Boolean,onCode:(String)->Unit,onPhoto:(android.net.Uri)->Unit,onClose:()->Unit) {
    val context=LocalContext.current;val c=LocalDeltaColors.current;val haptic=LocalHapticFeedback.current;val scope=rememberCoroutineScope()
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed=it }
    var manual by rememberSaveable { mutableStateOf("") };var typing by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) };var reading by remember { mutableStateOf(false) }
    var torch by rememberSaveable { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) };var found by remember { mutableStateOf(false) }
    var cropping by rememberSaveable { mutableStateOf<String?>(null) }
    var lenses by remember { mutableStateOf<LensSet?>(null) }
    var lensLabel by rememberSaveable { mutableStateOf("1×") }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var auto by rememberSaveable { mutableStateOf(true) }
    var live by remember { mutableStateOf<LiveLabel?>(null) }
    var steady by remember { mutableIntStateOf(0) }
    var capturing by remember { mutableStateOf(false) }
    val camera=remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    val capture=remember { mutableStateOf<ImageCapture?>(null) }
    val focusRequest=remember { mutableStateOf<Offset?>(null) }
    val lens=lenses?.lenses?.firstOrNull {it.label==lensLabel} ?: lenses?.main ?: CameraLens("1×",1f)
    fun accept(code:String) {
        if(found) return
        found=true;haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { kotlinx.coroutines.delay(220);onCode(code) }
    }
    fun shoot() {
        val use=capture.value ?: return
        if(capturing) return
        capturing=true;haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val file=runCatching {
            val dir=File(context.cacheDir,"meal_photos").apply {mkdirs()}
            dir.listFiles()?.filter {it.name.startsWith("label-") && System.currentTimeMillis()-it.lastModified()>86400000}?.forEach {it.delete()}
            File.createTempFile("label-",".jpg",dir)
        }.getOrNull() ?: run {capturing=false;error="Couldn't save the photo";return}
        use.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),ContextCompat.getMainExecutor(context),object:ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output:ImageCapture.OutputFileResults) {onPhoto(FileProvider.getUriForFile(context,"${context.packageName}.files",file))}
            override fun onError(exception:ImageCaptureException) {capturing=false;file.delete();error="Couldn't take the photo. Try again."}
        })
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
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if(uri!=null) {if(labelMode) onPhoto(uri) else decodePhoto(uri,LabelCrop(),true)} }
    fun openGallery() = runCatching { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.onFailure { error="No photo picker is available" }
    LaunchedEffect(torch,camera.value) { camera.value?.takeIf { hasFlash }?.cameraControl?.enableTorch(torch) }
    // The default camera's zoom ratio is how logical multi-cameras reach their ultrawide and telephoto lenses.
    LaunchedEffect(zoom,camera.value,lens.cameraId) { camera.value?.cameraControl?.setZoomRatio(if(lens.cameraId==null) zoom else lens.zoom) }
    // Auto-capture once the same readable table has been seen on two consecutive frames.
    LaunchedEffect(live) {
        val l=live
        steady=if(l!=null && l.energy && l.values>=4) steady+1 else 0
        if(labelMode && auto && steady>=2 && !capturing) shoot()
    }
    val ink=Color.White
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if(allowed) {
            DeltaCamera(Modifier.fillMaxSize(),lens,labelMode,camera,capture,focusRequest,{ lenses=it;if(it.lenses.none {l->l.label==lensLabel}) lensLabel=it.main.label },
                { hasFlash=it },{ accept(it) },{ live=it },{ error=it })
            Viewfinder(Modifier.fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { focusRequest.value=it } }
                .pointerInput(lenses,lens.cameraId) {
                    detectTransformGestures { _,_,scale,_ ->
                        val set=lenses ?: return@detectTransformGestures
                        if(lens.cameraId!=null || scale==1f) return@detectTransformGestures
                        val next=(zoom*scale).coerceIn(set.minZoom,set.maxZoom.coerceAtMost(30f))
                        set.nearest(next)?.let {near->if(near.label!=lensLabel) {lensLabel=near.label;haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}}
                        zoom=next
                    }
                },Color(c.accent),found || capturing,labelMode)
        } else Column(Modifier.align(Alignment.Center).padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)) {
            DeltaIcon(if(labelMode) io.github.ebziboi.delta.R.drawable.ic_text_fields else io.github.ebziboi.delta.R.drawable.ic_barcode,null,tint=ink,size=44.dp)
            Text(if(labelMode) "Camera access is needed to read a label" else "Camera access is needed to scan a barcode",color=ink,style=LocalDeltaType.current.title,textAlign=TextAlign.Center)
            Text(if(labelMode) "You can still choose a photo of the label." else "You can still type the number or choose a photo of it.",color=ink.copy(alpha=.7f),style=LocalDeltaType.current.bodySmall,textAlign=TextAlign.Center)
            PrimaryButton("Allow camera",{permission.launch(Manifest.permission.CAMERA)})
        }
        // Top controls
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
            ScannerButton(io.github.ebziboi.delta.R.drawable.ic_close,"Close",onClose)
            Text(if(labelMode) "Nutrition label" else "Scan barcode",color=ink,style=LocalDeltaType.current.title,modifier=Modifier.weight(1f),textAlign=TextAlign.Center)
            if(allowed && hasFlash) ScannerButton(if(torch) io.github.ebziboi.delta.R.drawable.ic_flash_on else io.github.ebziboi.delta.R.drawable.ic_flash_off,
                if(torch) "Turn flashlight off" else "Turn flashlight on",{ torch=!torch },active=torch)
            else Spacer(Modifier.size(48.dp))
        }
        // Status beneath the frame
        Column(Modifier.align(Alignment.Center).padding(top=if(labelMode) 420.dp else 230.dp).padding(horizontal=32.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            if(allowed) Text(when {
                found->"Got it"
                capturing->"Reading label…"
                reading->"Reading photo…"
                labelMode && live?.let {it.values>=4 && it.energy}==true->"Found ${live?.values} values · hold steady"
                labelMode && (live?.values ?: 0)>0->"Found ${live?.values} values · fit the whole table in the frame"
                labelMode->"Fit the nutrition table in the frame${if(auto) " · it's captured automatically" else ""}"
                lens.ratio>=2.5f->"Hold the phone further back at ${lens.label} · tap to focus"
                else->"Line up the barcode inside the frame · tap to focus"
            },color=ink.copy(alpha=.85f),style=LocalDeltaType.current.bodySmall,textAlign=TextAlign.Center)
            error?.let { Text(it,color=Color(c.warn),style=LocalDeltaType.current.bodySmall,textAlign=TextAlign.Center,modifier=Modifier.padding(top=6.dp)) }
        }
        // Bottom controls; manual entry slides up over them.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            if(allowed) lenses?.takeIf {it.lenses.size>1}?.let {set->LensBar(set.lenses,lens,if(lens.cameraId==null) zoom else lens.zoom) {chosen->
                lensLabel=chosen.label;zoom=chosen.zoom;haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }}
            if(!labelMode) AnimatedVisibility(typing || !allowed) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.card)).background(Color(c.bg).copy(alpha=.94f)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    GlassTextField(manual,{manual=it.filter(Char::isDigit).take(14)},"Barcode number",Modifier.fillMaxWidth())
                    PrimaryButton("Find product",{onCode(manual)},Modifier.fillMaxWidth(),manual.matches(barcodePattern))
                    if(!manual.matches(barcodePattern) && manual.isNotEmpty()) Text("Barcodes have 8, 12, 13 or 14 digits",style=LocalDeltaType.current.bodySmall,color=Color(c.textDim))
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                if(labelMode) ScannerPill(io.github.ebziboi.delta.R.drawable.ic_auto_awesome,if(auto) "Auto" else "Manual",active=auto) { auto=!auto;steady=0 }
                else ScannerPill(io.github.ebziboi.delta.R.drawable.ic_edit,if(typing) "Hide keypad" else "Type number") { typing=!typing }
                Spacer(Modifier.weight(1f))
                if(labelMode && allowed) Shutter(capturing) { shoot() }
                Spacer(Modifier.weight(1f))
                ScannerButton(io.github.ebziboi.delta.R.drawable.ic_photo_library,if(labelMode) "Choose label photo" else "Choose barcode photo",::openGallery,size=56.dp)
            }
        }
    }
    cropping?.let { uri -> LabelCropper(android.net.Uri.parse(uri),{ cropping=null },{ crop -> cropping=null;decodePhoto(android.net.Uri.parse(uri),crop,false) },title="Barcode photo",actionLabel="Read barcode") }
}

/** Lens chips in the style of the phone's own camera: the active one shows the live zoom. */
@Composable private fun LensBar(lenses:List<CameraLens>,current:CameraLens,zoom:Float,onLens:(CameraLens)->Unit) {
    val accent=Color(LocalDeltaColors.current.accent)
    Row(Modifier.clip(RoundedCornerShape(28.dp)).background(Color.Black.copy(alpha=.45f)).padding(5.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
        lenses.forEach {l->
            val active=l.label==current.label
            val text=if(active && current.cameraId==null && kotlin.math.abs(zoom-l.zoom)>.05f) "${(zoom*10).toInt()/10f}×".replace(".0×","×") else l.label
            Box(Modifier.size(if(active) 48.dp else 42.dp).clip(CircleShape).background(if(active) Color.White.copy(alpha=.16f) else Color.Transparent)
                .clickable(role=Role.Tab,onClickLabel="${l.label} lens") {onLens(l)}.semantics {selected=active;contentDescription="${l.label} lens"},contentAlignment=Alignment.Center) {
                Text(text,color=if(active) accent else Color.White,style=if(active) LocalDeltaType.current.bodyStrong else LocalDeltaType.current.bodySmall,maxLines=1)
            }
        }
    }
}

@Composable private fun Shutter(busy:Boolean,onClick:()->Unit) {
    Box(Modifier.size(76.dp).clip(CircleShape).border(4.dp,Color.White,CircleShape).padding(8.dp).clip(CircleShape)
        .background(if(busy) Color.White.copy(alpha=.4f) else Color.White).clickable(enabled=!busy,role=Role.Button,onClickLabel="Take label photo",onClick=onClick)
        .semantics {contentDescription="Take label photo"})
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

/** Dims everything outside the scanning window, with corner guides and a sweeping scan line. */
@Composable private fun Viewfinder(modifier:Modifier,accent:Color,found:Boolean,label:Boolean) {
    val motion=LocalMotion.current
    val sweep=if(motion && !found) rememberInfiniteTransition(label="scan").animateFloat(0f,1f,infiniteRepeatable(tween(1700,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="scan-line").value else .5f
    val success by animateFloatAsState(if(found) 1f else 0f,tween(180),label="scan-found")
    Canvas(modifier.semantics { contentDescription=if(label) "Label viewfinder" else "Barcode viewfinder" }) {
        val w=size.width*(if(label) .86f else .78f);val h=if(label) minOf(w*1.25f,size.height*.56f) else w*.52f
        val left=(size.width-w)/2;val top=size.height/2-h/2-(if(label) 40.dp else 24.dp).toPx()
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

/**
 * Binds the preview, a frame analyser and (for labels) a full-resolution capture to [lens]. Changing to a lens
 * on another physical camera rebinds; lenses reached by zoom on the default camera only change its zoom ratio.
 */
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
private fun DeltaCamera(modifier:Modifier,lens:CameraLens,labelMode:Boolean,camera:MutableState<androidx.camera.core.Camera?>,capture:MutableState<ImageCapture?>,
                        focus:MutableState<Offset?>,onLenses:(LensSet)->Unit,onFlash:(Boolean)->Unit,onFound:(String)->Unit,onLabel:(LiveLabel)->Unit,onError:(String)->Unit) {
    val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current
    val preview=remember { PreviewView(context).apply { scaleType=PreviewView.ScaleType.FILL_CENTER } };val main=remember { ContextCompat.getMainExecutor(context) }
    val found=rememberUpdatedState(onFound);val error=rememberUpdatedState(onError);val flash=rememberUpdatedState(onFlash)
    val lensCallback=rememberUpdatedState(onLenses);val label=rememberUpdatedState(onLabel)
    AndroidView({preview},modifier)
    LaunchedEffect(focus.value) {
        val at=focus.value ?: return@LaunchedEffect
        val cam=camera.value ?: return@LaunchedEffect
        runCatching { cam.cameraControl.startFocusAndMetering(androidx.camera.core.FocusMeteringAction.Builder(preview.meteringPointFactory.createPoint(at.x,at.y)).build()) }
    }
    DisposableEffect(lifecycle,lens.cameraId,labelMode) {
        val executor=Executors.newSingleThreadExecutor();val done=AtomicBoolean(false);val inFlight=AtomicBoolean(false);var disposed=false
        var lastFrame=0L
        val recognizer=if(labelMode) com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS) else null
        val future=ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider?=null;var analysis: ImageAnalysis?=null;var cameraPreview: Preview?=null;var still: ImageCapture?=null
        future.addListener({
            if(!disposed) try {
                provider=future.get()
                runCatching {lensCallback.value(CameraLenses.discover(context,provider!!))}
                cameraPreview=Preview.Builder().build().apply { setSurfaceProvider(preview.surfaceProvider) }
                val resolution=ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(Size(1280,720),ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build()
                analysis=ImageAnalysis.Builder().setResolutionSelector(resolution).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().apply {
                    setAnalyzer(executor) { proxy ->
                        val tick=android.os.SystemClock.elapsedRealtime()
                        if(recognizer!=null) {
                            // Live label reading: a quick on-device pass every ~0.7 s decides when to take the real photo.
                            val media=proxy.image
                            if(disposed || media==null || tick-lastFrame<700 || !inFlight.compareAndSet(false,true)) {proxy.close();return@setAnalyzer}
                            lastFrame=tick
                            recognizer.process(com.google.mlkit.vision.common.InputImage.fromMediaImage(media,proxy.imageInfo.rotationDegrees))
                                .addOnSuccessListener {text->
                                    val words=text.textBlocks.flatMap {it.lines}.flatMap {it.elements}.mapNotNull {e->e.boundingBox?.let {b->LabelWord(e.text,b.left.toFloat(),b.top.toFloat(),b.right.toFloat(),b.bottom.toFloat())}}
                                    val read=runCatching {LabelParser.parse(LabelOcr.lines(words).ifBlank {text.text})}.getOrNull()
                                    val column=read?.columns?.getOrNull(read.best)
                                    if(!disposed) label.value(LiveLabel(column?.observations?.size ?: 0,column?.observations?.containsKey(Nutrient.ENERGY)==true,tick))
                                }
                                .addOnCompleteListener {proxy.close();inFlight.set(false)}
                            return@setAnalyzer
                        }
                        try {
                            if(!done.get() && !disposed && tick-lastFrame>150) {
                                lastFrame=tick
                                val plane=proxy.planes[0];val buffer=plane.buffer;val base=buffer.position()
                                val w=proxy.width;val h=proxy.height;val pixels=ByteArray(w*h)
                                if(plane.pixelStride==1) for(y in 0 until h) {buffer.position(base+y*plane.rowStride);buffer.get(pixels,y*w,w)}
                                else for(y in 0 until h) for(x in 0 until w) pixels[y*w+x]=buffer.get(base+y*plane.rowStride+x*plane.pixelStride)
                                buffer.position(base)
                                decodeBarcode(pixels,w,h)?.let {code ->
                                    if(done.compareAndSet(false,true)) main.execute {if(!disposed) found.value(code)}
                                }
                            }
                        } catch(_: Exception) { /* A frame may close as its camera is detached. */ }
                        finally {proxy.close()}
                    }
                }
                val useCases=mutableListOf<androidx.camera.core.UseCase>(cameraPreview!!,analysis!!)
                if(labelMode) {still=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build();useCases+=still!!}
                provider!!.unbindAll()
                val selector=runCatching {lens.selector.also {s->require(provider!!.hasCamera(s))}}.getOrDefault(CameraSelector.DEFAULT_BACK_CAMERA)
                val bound=provider!!.bindToLifecycle(lifecycle,selector,*useCases.toTypedArray())
                camera.value=bound;capture.value=still;flash.value(bound.cameraInfo.hasFlashUnit())
            } catch(_: Exception) { error.value(if(labelMode) "Camera unavailable. Choose a label photo instead." else "Camera unavailable. Enter the barcode number below.") }
        },main)
        onDispose {
            disposed=true;analysis?.clearAnalyzer();camera.value=null;capture.value=null
            listOfNotNull(analysis,cameraPreview,still).forEach { provider?.unbind(it) }
            recognizer?.close();executor.shutdown()
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
