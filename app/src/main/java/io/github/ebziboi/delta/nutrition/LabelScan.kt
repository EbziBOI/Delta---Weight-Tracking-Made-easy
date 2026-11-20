package io.github.ebziboi.delta.nutrition

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Opens Delta's label camera (or a photo), finds the nutrition table and reads it, with no crop step. */
internal class LabelScanner(val camera:()->Unit,val gallery:()->Unit,val reading:Boolean)

@Composable
internal fun rememberLabelScanner(onResult:(LabelOcrResult,Uri)->Unit,onError:(String)->Unit):LabelScanner {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var reading by remember {mutableStateOf(false)}
    val result by rememberUpdatedState(onResult);val error by rememberUpdatedState(onError)
    fun read(uri:Uri) {
        reading=true
        scope.launch {
            try {
                val read=LabelOcr.scan(context,uri,LabelCrop(),auto=true)
                if(read.read.columns.all {it.observations.isEmpty()}) error("No nutrition values found. Fill the frame with the table, or adjust the crop.")
                result(read,uri)
            } catch(e:CancellationException) {throw e}
            catch(_:Exception) {error("Couldn't read the label. Try again with more light.")}
            finally {reading=false}
        }
    }
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {r->
        if(r.resultCode==android.app.Activity.RESULT_OK) r.data?.getStringExtra("photo")?.let {read(Uri.parse(it))}
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri->if(uri!=null) read(uri)}
    return LabelScanner(
        {runCatching {camera.launch(BarcodeActivity.label(context))}.onFailure {error("Camera unavailable. Choose a label photo instead.")}},
        {runCatching {picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}.onFailure {error("No photo picker is available")}},
        reading)
}

/** The serving size printed on a label: a column whose quantity isn't 100 g/ml. */
internal fun LabelRead.servingSize():Double?=columns.mapNotNull {it.basis.amount}.firstOrNull {it!=100.0}
