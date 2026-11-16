package io.github.ebziboi.delta.nutrition

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.ebziboi.delta.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot

@Composable
internal fun LabelCropper(uri:Uri,onDismiss:()->Unit,onScan:(LabelCrop)->Unit,title:String="Nutrition label",actionLabel:String="Read label") {
    val context=LocalContext.current
    var rotation by rememberSaveable(uri.toString()) {mutableIntStateOf(0)}
    var l by rememberSaveable(uri.toString()) {mutableFloatStateOf(0f)};var t by rememberSaveable(uri.toString()) {mutableFloatStateOf(0f)}
    var r by rememberSaveable(uri.toString()) {mutableFloatStateOf(1f)};var b by rememberSaveable(uri.toString()) {mutableFloatStateOf(1f)}
    var failure by remember {mutableStateOf(false)}
    val bitmap by produceState<Bitmap?>(null,uri,rotation) {
        value=null
        value=withContext(Dispatchers.Default) {runCatching {
            val original=LabelOcr.load(context,uri)
            val shown=if(rotation==0) original else Bitmap.createBitmap(original,0,0,original.width,original.height,Matrix().apply {postRotate(rotation.toFloat())},true)
            if(shown!==original) original.recycle();shown
        }.onFailure {failure=true}.getOrNull()}
    }
    Dialog(onDismiss,DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(color=Color(LocalDeltaColors.current.bg),contentColor=Color(LocalDeltaColors.current.text)) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                ScreenHeader(title)
                Text(if(title=="Nutrition label") "Frame the whole table, including its serving-size headings." else "Frame the barcode clearly.",style=LocalDeltaType.current.bodySmall)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=androidx.compose.ui.Alignment.Center) {
                    bitmap?.let {image->
                        val width=minOf(maxWidth.value,maxHeight.value*image.width/image.height).dp
                        Box(Modifier.width(width).aspectRatio(image.width.toFloat()/image.height)) {
                            Image(image.asImageBitmap(),"Label crop preview",Modifier.fillMaxSize())
                            Canvas(Modifier.fillMaxSize().pointerInput(rotation) {
                                var handle=-1
                                detectDragGestures(onDragStart={p->
                                    val corners=listOf(Offset(l*size.width,t*size.height),Offset(r*size.width,t*size.height),Offset(r*size.width,b*size.height),Offset(l*size.width,b*size.height))
                                    handle=corners.indices.minByOrNull {i->(corners[i]-p).getDistance()} ?: 0
                                }) {change,delta->
                                    change.consume();val dx=delta.x/size.width;val dy=delta.y/size.height
                                    when(handle) {0->{l=(l+dx).coerceIn(0f,r-.08f);t=(t+dy).coerceIn(0f,b-.08f)}
                                        1->{r=(r+dx).coerceIn(l+.08f,1f);t=(t+dy).coerceIn(0f,b-.08f)}
                                        2->{r=(r+dx).coerceIn(l+.08f,1f);b=(b+dy).coerceIn(t+.08f,1f)}
                                        3->{l=(l+dx).coerceIn(0f,r-.08f);b=(b+dy).coerceIn(t+.08f,1f)}}
                                }
                            }) {
                                val x=l*size.width;val y=t*size.height;val right=r*size.width;val bottom=b*size.height
                                drawRect(Color.Black.copy(alpha=.55f),Offset.Zero,androidx.compose.ui.geometry.Size(size.width,y))
                                drawRect(Color.Black.copy(alpha=.55f),Offset(0f,bottom),androidx.compose.ui.geometry.Size(size.width,size.height-bottom))
                                drawRect(Color.Black.copy(alpha=.55f),Offset(0f,y),androidx.compose.ui.geometry.Size(x,bottom-y))
                                drawRect(Color.Black.copy(alpha=.55f),Offset(right,y),androidx.compose.ui.geometry.Size(size.width-right,bottom-y))
                                drawRect(Color.White,Offset(x,y),androidx.compose.ui.geometry.Size(right-x,bottom-y),style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                                listOf(Offset(x,y),Offset(right,y),Offset(right,bottom),Offset(x,bottom)).forEach {drawCircle(Color.White,7.dp.toPx(),it)}
                            }
                        }
                    } ?: if(failure) Text("Couldn't open this photo. Choose another image.") else CircularProgressIndicator()
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Rotate",{rotation=(rotation+90)%360;l=0f;t=0f;r=1f;b=1f},Modifier.weight(1f),enabled=bitmap!=null)
                    SecondaryButton("Reset crop",{l=0f;t=0f;r=1f;b=1f},Modifier.weight(1f),enabled=bitmap!=null)
                }
                PrimaryButton(actionLabel,{onScan(LabelCrop(l,t,r,b,rotation))},Modifier.fillMaxWidth(),enabled=bitmap!=null)
                TextAction("Cancel",onDismiss)
            }
        }
    }
}
