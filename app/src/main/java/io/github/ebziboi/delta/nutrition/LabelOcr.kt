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
internal data class LabelOcrResult(val read:LabelRead,val uncertain:Set<Nutrient>,val crop:LabelCrop=LabelCrop()) {
    /** All of the chosen column's main values were read, agree between passes and add up. */
    val confident:Boolean get() {
        val column=read.columns.getOrNull(read.best) ?: return false
        val values=column.observations.filterValues {!it.bounded}.mapValues {it.value.value}
        return column.basis.amount!=null && uncertain.isEmpty() && Nutrient.primary.all {it in values} && LabelParser.violation(values,column.basis.amount)==0.0
    }
}
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
    /** Physical rows of words, left to right, even when the recognizer splits a table into vertical text blocks. */
    fun rows(words:List<LabelWord>):List<List<LabelWord>> {
        val rows=mutableListOf<MutableList<LabelWord>>()
        words.sortedBy {(it.top+it.bottom)/2}.forEach {word->
            val y=(word.top+word.bottom)/2;val h=word.bottom-word.top
            val row=rows.lastOrNull {r->val first=r.first();abs(y-(first.top+first.bottom)/2)<minOf(h,first.bottom-first.top)*.55f}
            if(row==null) rows+=mutableListOf(word) else row+=word
        }
        return rows.map {it.sortedBy {w->w.left}}
    }
    private data class Cell(val text:String,val left:Float,val right:Float) {val centre get()=(left+right)/2}
    /**
     * Reassemble the table as text. Words far apart become tab-separated cells, and when the numbers line up in
     * two or more columns each value is placed in its column, so a missing value doesn't shift the others.
     */
    fun lines(words:List<LabelWord>):String {
        val rows=rows(words)
        if(rows.isEmpty()) return ""
        val heights=words.map {it.bottom-it.top}.filter {it>0}.sorted()
        val h=heights.getOrNull(heights.size/2) ?: 10f
        val cellRows=rows.map {row->
            val cells=mutableListOf<Cell>()
            row.forEach {w->
                val last=cells.lastOrNull()
                if(last!=null && w.left-last.right<h*1.1f) cells[cells.lastIndex]=Cell(last.text+" "+w.text,last.left,maxOf(last.right,w.right))
                else cells+=Cell(w.text,w.left,w.right)
            }
            cells
        }
        val centres=cellRows.flatMap {r->r.drop(1).filter {c->c.text.any(Char::isDigit)}.map {it.centre}}.sorted()
        val clusters=mutableListOf<MutableList<Float>>()
        centres.forEach {x->if(clusters.isEmpty() || x-clusters.last().last()>h*2.5f) clusters+=mutableListOf(x) else clusters.last()+=x}
        val columns=clusters.filter {it.size>=2}.map {it.average().toFloat()}
        return cellRows.joinToString("\n") {cells->
            if(cells.size==1) cells[0].text
            else if(columns.size<2) cells.joinToString("\t") {it.text}
            else {
                val slots=Array(columns.size) {""}
                cells.drop(1).forEach {c->
                    val i=columns.indices.minBy {abs(columns[it]-c.centre)}
                    slots[i]=if(slots[i].isEmpty()) c.text else slots[i]+" "+c.text
                }
                cells[0].text+"\t"+slots.joinToString("\t")
            }
        }
    }
    /** The part of an image holding the nutrition table, from the rows that start with a nutrient. */
    fun tableRegion(words:List<LabelWord>,width:Int,height:Int):LabelCrop? {
        if(width<=0 || height<=0) return null
        val rows=rows(words)
        val hits=rows.indices.filter {LabelParser.isNutrientRow(rows[it].joinToString(" ") {w->w.text})}
        if(hits.size<3) return null
        // Include up to three heading rows above the first nutrient ("Typical values  Per 100g  Per serving").
        val used=(maxOf(0,hits.first()-3)..hits.last()).flatMap {rows[it]}
        val left=used.minOf {it.left};val right=used.maxOf {it.right};val top=used.minOf {it.top};val bottom=used.maxOf {it.bottom}
        val padX=(right-left)*.06f+8;val padY=(bottom-top)*.05f+8
        val crop=LabelCrop(((left-padX)/width).coerceIn(0f,1f),((top-padY)/height).coerceIn(0f,1f),((right+padX)/width).coerceIn(0f,1f),((bottom+padY)/height).coerceIn(0f,1f))
        return crop.takeIf {(it.right-it.left)*(it.bottom-it.top)<.8f}
    }
    /**
     * Reads a label photo. With no manual crop ([auto]), the whole photo is read first and, when a nutrition table
     * is found inside a larger picture, that table is read again on its own at full resolution.
     */
    suspend fun scan(context:Context,uri:Uri,crop:LabelCrop,auto:Boolean=false):LabelOcrResult=withContext(Dispatchers.Default) {
        val original=load(context,uri,5000)
        val region=if(auto && crop==LabelCrop()) runCatching {
            val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val text=suspendCoroutine<Text> {c->recognizer.process(InputImage.fromBitmap(original,0)).addOnSuccessListener {c.resume(it)}.addOnFailureListener {c.resumeWithException(it)}}
                val words=text.textBlocks.flatMap {it.lines}.flatMap {it.elements}.mapNotNull {e->e.boundingBox?.let {b->LabelWord(e.text,b.left.toFloat(),b.top.toFloat(),b.right.toFloat(),b.bottom.toFloat())}}
                tableRegion(words,original.width,original.height)
            } finally {recognizer.close()}
        }.getOrNull() else null
        val chosen=region ?: crop
        val clipped=try {crop(original,chosen)} catch(e:Exception) {original.recycle();throw e}
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
            LabelOcrResult(best.copy(warnings=(best.warnings+if(uncertain.isEmpty()) emptyList() else listOf("Image passes disagree on ${uncertain.joinToString {it.label.lowercase()}}. Check these values.")).distinct()),uncertain,chosen)
        } finally {recognizer.close();if(clipped!==original) clipped.recycle();original.recycle()}
    }
}
