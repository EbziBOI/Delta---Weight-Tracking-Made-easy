package io.github.ebziboi.delta.nutrition

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object BarcodePhotos {
    suspend fun decode(context:Context,uri:Uri,crop:LabelCrop):String?=withContext(Dispatchers.Default) {
        val source=LabelOcr.load(context,uri);val bitmap=LabelOcr.crop(source,crop)
        try {
            val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
            val luma=ByteArray(pixels.size) {i->val p=pixels[i];(((p shr 16 and 255)*299+(p shr 8 and 255)*587+(p and 255)*114)/1000).toByte()}
            decodeBarcode(luma,bitmap.width,bitmap.height) ?: decodeBarcode(ByteArray(luma.size) {i->(255-(luma[i].toInt() and 255)).toByte()},bitmap.width,bitmap.height)
        } finally {if(bitmap!==source) bitmap.recycle();source.recycle()}
    }
}
