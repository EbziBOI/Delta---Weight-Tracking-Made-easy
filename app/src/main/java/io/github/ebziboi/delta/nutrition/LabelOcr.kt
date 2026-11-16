package io.github.ebziboi.delta.nutrition

import android.content.Context
import android.graphics.*
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.*

internal data class LabelCrop(val left:Float=0f,val top:Float=0f,val right:Float=1f,val bottom:Float=1f,val rotation:Int=0)
internal data class LabelWord(val text:String,val left:Float,val top:Float,val right:Float,val bottom:Float)
internal data class LabelOcrResult(val read:LabelRead,val uncertain:Set<Nutrient>)
internal object LabelOcr {
    suspend fun load(context:Context,uri:Uri,maxEdge:Int=2800):Bitmap=withContext(Dispatchers.IO) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,uri)) {decoder,info,_->
            val scale=minOf(1.0,maxEdge.toDouble()/maxOf(info.size.width,info.size.height),sqrt(8_000_000.0/info.size.width/info.size.height))
            decoder.setTargetSize(maxOf(1,(info.size.width*scale).toInt()),maxOf(1,(info.size.height*scale).toInt()))
            decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
    fun crop(bitmap:Bitmap,crop:LabelCrop):Bitmap {
        val matrix=Matrix().apply {postRotate((crop.rotation%360).toFloat())}
        val rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
        val x=(crop.left.coerceIn(0f,.99f)*rotated.width).toInt();val y=(crop.top.coerceIn(0f,.99f)*rotated.height).toInt()
        val w=((crop.right.coerceIn(crop.left+.01f,1f)-crop.left)*rotated.width).toInt().coerceIn(1,rotated.width-x)
        val h=((crop.bottom.coerceIn(crop.top+.01f,1f)-crop.top)*rotated.height).toInt().coerceIn(1,rotated.height-y)
        val result=Bitmap.createBitmap(rotated,x,y,w,h)
        if(rotated!==bitmap && rotated!==result) rotated.recycle()
        return result
    }
    /** Reassemble physical rows, even when the recognizer splits a table into vertical text blocks. */
    fun lines(words:List<LabelWord>):String {
        val rows=mutableListOf<MutableList<LabelWord>>()
        words.sortedBy {(it.top+it.bottom)/2}.forEach {word->
            val y=(word.top+word.bottom)/2;val h=word.bottom-word.top
            val row=rows.lastOrNull {r->val first=r.first();abs(y-(first.top+first.bottom)/2)<minOf(h,first.bottom-first.top)*.55f}
            if(row==null) rows+=mutableListOf(word) else row+=word
        }
        return rows.joinToString("\n") {row->row.sortedBy {it.left}.joinToString(" ") {it.text}}
    }
    suspend fun scan(context:Context,uri:Uri,crop:LabelCrop):LabelOcrResult=withContext(Dispatchers.Default) {
        val original=load(context,uri,5000);val clipped=try {crop(original,crop)} catch(e:Exception) {original.recycle();throw e}
        val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        suspend fun read(bitmap:Bitmap,rotation:Int=0):LabelRead {
            val text=suspendCoroutine<Text> {c->recognizer.process(InputImage.fromBitmap(bitmap,rotation)).addOnSuccessListener {c.resume(it)}.addOnFailureListener {c.resumeWithException(it)}}
            currentCoroutineContext().ensureActive()
            val lines=text.textBlocks.flatMap {it.lines}
            val angles=lines.mapNotNull {line->line.cornerPoints?.takeIf {it.size>=2}?.let {p->
                Math.toDegrees(atan2((p[1].y-p[0].y).toDouble(),(p[1].x-p[0].x).toDouble())).toFloat().takeIf {abs(it)<25}
            }}.sorted()
            val deskew=Matrix().apply {postRotate(-(angles.getOrNull(angles.size/2) ?: 0f))}
            val words=lines.flatMap {it.elements}.mapNotNull {e->e.boundingBox?.let {box->
                val rect=RectF(box);deskew.mapRect(rect);LabelWord(e.text,rect.left,rect.top,rect.right,rect.bottom)
            }}
            return LabelParser.parse(lines(words).ifBlank {text.text})
        }
        fun score(r:LabelRead)=r.columns.maxOfOrNull {c->c.observations.size*10+(if(c.basis.amount!=null) 8 else 0)} ?: 0
        try {
            val first=read(clipped)
            val enhanced=Bitmap.createBitmap(clipped.width,clipped.height,Bitmap.Config.ARGB_8888)
            val desaturate=ColorMatrix().apply {setSaturation(0f)}
            desaturate.postConcat(ColorMatrix(floatArrayOf(1.3f,0f,0f,0f,-28f,0f,1.3f,0f,0f,-28f,0f,0f,1.3f,0f,-28f,0f,0f,0f,1f,0f)))
            Canvas(enhanced).drawBitmap(clipped,0f,0f,Paint().apply {colorFilter=ColorMatrixColorFilter(desaturate)})
            val second=try {read(enhanced)} finally {enhanced.recycle()}
            val reads=mutableListOf(first,second)
            if(reads.maxOf(::score)<40) for(rotation in listOf(90,270,180)) {reads+=read(clipped,rotation);if(score(reads.last())>=60) break}
            val best=reads.maxBy(::score);val column=best.columns[best.best]
            val uncertain=mutableSetOf<Nutrient>()
            // Different enhancement passes are independent transcriptions, not a confirmation of the web page.
            reads.filter {it!==best && score(it)>=score(best)*.7}.forEach {r->r.columns.firstOrNull {it.basis.amount==column.basis.amount && it.basis.unit==column.basis.unit}?.let {other->
                column.observations.forEach {(n,v)->other.observations[n]?.let {if(LabelParser.differs(v.value,it.value) || v.bounded!=it.bounded) uncertain+=n}}
            }}
            LabelOcrResult(best.copy(warnings=(best.warnings+if(uncertain.isEmpty()) emptyList() else listOf("Image passes disagree on ${uncertain.joinToString {it.label.lowercase()}}. Check these values.")).distinct()),uncertain)
        } finally {recognizer.close();if(clipped!==original) clipped.recycle();original.recycle()}
    }
}
