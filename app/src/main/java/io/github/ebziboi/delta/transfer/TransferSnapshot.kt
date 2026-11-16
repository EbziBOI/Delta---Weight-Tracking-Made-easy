package io.github.ebziboi.delta.transfer

import android.content.Context
import android.net.Uri
import android.graphics.ImageDecoder
import android.graphics.Bitmap
import android.util.Base64
import io.github.ebziboi.delta.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

object TransferSnapshot {
    const val STABLE="io.github.ebziboi.delta"
    const val BETA="io.github.ebziboi.delta.beta"
    const val ACTIVITY="io.github.ebziboi.delta.transfer.TransferOutActivity"
    const val MAX_BYTES=32*1024*1024
    data class Parsed(val settings:JSONArray,val weights:List<WeightEntry>,val fat:List<BodyFatEntry>,val images:Map<String,ByteArray>)

    suspend fun create(context:Context):JSONObject=withContext(Dispatchers.IO) {
        require(context.packageName in setOf(STABLE,BETA))
        val store=SettingsStore(context);val settings=store.settings.first()
        val result=JSONObject().put("format","delta-beta-copy").put("schema",1).put("source",STABLE)
            .put("settings",store.exportForBeta())
        result.put("weights",JSONArray().also {a->store.weights.first().forEach {r->a.put(JSONObject().put("id",r.id).put("time",r.epochMs).put("kg",r.kg))}})
        result.put("bodyFat",JSONArray().also {a->store.bodyFat.first().forEach {r->a.put(JSONObject().put("id",r.id).put("time",r.epochMs).put("percent",r.percent))}})
        val images=JSONObject()
        for((name,a) in mapOf("appearance" to settings.appearance,"glass" to settings.savedGlassAppearance,"tubik" to settings.savedTubikAppearance)) {
            val uri=a?.backgroundImageUri ?: continue
            if(a.backgroundStyle!=BackgroundStyle.IMAGE) continue
            val bitmap=try {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,Uri.parse(uri))) {d,info,_->
                    val scale=minOf(1.0,2048.0/maxOf(info.size.width,info.size.height));d.setTargetSize(maxOf(1,(info.size.width*scale).toInt()),maxOf(1,(info.size.height*scale).toInt()));d.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } catch(e:kotlinx.coroutines.CancellationException) {throw e} catch(_:Exception) {null} ?: continue
            try {val out=ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,93,out);images.put(name,Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP))} finally {bitmap.recycle()}
        }
        result.put("images",images)
    }
    fun parse(text:String):Parsed {
        require(text.toByteArray().size<=MAX_BYTES) {"The transfer is too large"}
        val j=JSONObject(text)
        require(j.getString("format")=="delta-beta-copy" && j.getInt("schema")==1 && j.getString("source")==STABLE) {"This is not a supported Delta transfer"}
        val wa=j.getJSONArray("weights");val fa=j.getJSONArray("bodyFat")
        require(wa.length()<=100000 && fa.length()<=100000)
        val weights=(0 until wa.length()).map {i->wa.getJSONObject(i).let {r->
            val kg=r.getDouble("kg");val time=r.getLong("time");val id=r.getString("id")
            require(kg.isFinite() && kg>0 && kg<=1000 && time>=0 && id.length<=250)
            WeightEntry(time,kg,true,"beta-copy:$id")
        }}.distinctBy {it.id}.sortedBy {it.epochMs}
        val fat=(0 until fa.length()).map {i->fa.getJSONObject(i).let {r->
            val percent=r.getDouble("percent");val time=r.getLong("time");val id=r.getString("id")
            require(BodyFat.valid(percent) && time>=0 && id.length<=250)
            BodyFatEntry(time,percent,true,"beta-copy:$id")
        }}.distinctBy {it.id}.sortedBy {it.epochMs}
        val source=j.optJSONObject("images") ?: JSONObject()
        val images=source.keys().asSequence().associateWith {key->
            require(key in setOf("appearance","glass","tubik"));Base64.decode(source.getString(key),Base64.DEFAULT).also {bytes->
                require(bytes.size<=8*1024*1024 && bytes.size>4 && bytes[0]==0xff.toByte() && bytes[1]==0xd8.toByte())
            }
        }
        return Parsed(j.getJSONArray("settings"),weights,fat,images)
    }
    suspend fun read(context:Context,uri:Uri):Parsed=withContext(Dispatchers.IO) {
        require(uri.scheme=="content" && uri.authority=="$STABLE.files") {"The transfer did not come from Delta"}
        val bytes=context.contentResolver.openInputStream(uri)?.use {input->
            val out=ByteArrayOutputStream();val buf=ByteArray(8192)
            while(true) {val n=input.read(buf);if(n<0) break;require(out.size()+n<=MAX_BYTES);out.write(buf,0,n)};out.toByteArray()
        } ?: error("The transfer could not be opened")
        parse(bytes.toString(Charsets.UTF_8))
    }
    suspend fun apply(context:Context,p:Parsed,allowStable:Boolean=false)=withContext(Dispatchers.IO) {
        require(context.packageName==BETA || (allowStable && context.packageName==STABLE))
        val store=SettingsStore(context)
        // Decode/validate settings before replacing any measurements.
        store.importForBeta(p.settings)
        val imageFiles=p.images.mapValues {(key,bytes)->
            val dir=File(context.filesDir,"beta-backgrounds").apply {mkdirs()}
            File(dir,"$key-${java.util.UUID.randomUUID()}.jpg").apply {writeBytes(bytes)}.let {Uri.fromFile(it).toString()}
        }
        store.update {s->
            fun image(a:AppAppearance?,key:String)=a?.let {value->imageFiles[key]?.let {value.copy(backgroundStyle=BackgroundStyle.IMAGE,backgroundImageUri=it)} ?: value}
            s.copy(appearance=image(s.appearance,"appearance"),savedGlassAppearance=image(s.savedGlassAppearance,"glass"),savedTubikAppearance=image(s.savedTubikAppearance,"tubik"))
        }
        store.saveBetaMeasurements(p.weights,p.fat)
        context.getSharedPreferences("beta-copy",0).edit().putLong("copied_at",System.currentTimeMillis()).apply()
        io.github.ebziboi.delta.widget.WidgetUpdater.updateAll(context)
        io.github.ebziboi.delta.icons.LauncherIcons.apply(context,store.settings.first().appIcon)
        store.update {it.copy(onboardingDone=true)}
    }
}
