package io.github.ebziboi.delta.transfer

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.nutrition.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

internal object FullTransfer {
    private val lock=Mutex()
    val preferences=setOf("home-chart","detail-card-order","nutrition-pie","food-stores")
    data class Parsed(val measurements:TransferSnapshot.Parsed,val nutrition:NutritionState,val prefs:JSONObject)
    suspend fun create(context:Context):JSONObject=withContext(Dispatchers.IO) {
        val app=context.applicationContext as DeltaApp
        val nutrition=app.nutrition.state.first {it.loaded};check(nutrition.loadError==null)
        val prefs=JSONObject();preferences.forEach {name->prefs.put(name,JSONObject(context.getSharedPreferences(name,0).all))}
        JSONObject().put("format","delta-full-copy").put("schema",1).put("source",context.packageName)
            .put("measurements",TransferSnapshot.create(context)).put("nutrition",NutritionArchive.encode(nutrition)).put("preferences",prefs)
    }
    fun parse(raw:String):Parsed {
        require(raw.toByteArray().size<=TransferSnapshot.MAX_BYTES)
        val root=JSONObject(raw);require(root.getString("format")=="delta-full-copy" && root.getInt("schema")==1)
        require(root.getString("source") in setOf(TransferSnapshot.STABLE,TransferSnapshot.BETA))
        val measures=TransferSnapshot.parse(root.getJSONObject("measurements").toString())
        val prefs=root.getJSONObject("preferences");require(prefs.length()<=preferences.size)
        prefs.keys().forEach {name->require(name in preferences);val values=prefs.getJSONObject(name);require(values.length()<=100)
            values.keys().forEach {key->val v=values.get(key);require(key.length<=100 && (v is String || v is Boolean || v is Int || v is Long || v is Double));if(v is String) require(v.length<=20000)}
        }
        return Parsed(measures,NutritionArchive.parse(root.getJSONObject("nutrition")),prefs)
    }
    suspend fun apply(context:Context,raw:String)=withContext(Dispatchers.IO) {lock.withLock {
        val checked=parse(raw);val previous=create(context);require(previous.toString().toByteArray().size<=TransferSnapshot.MAX_BYTES) {"The current data is too large for a reversible full copy"};val backup=AtomicFile(File(context.noBackupFilesDir,"before-full-transfer.json"));val stream=backup.startWrite()
        try {stream.write(previous.toString().toByteArray());backup.finishWrite(stream)} catch(e:Exception) {backup.failWrite(stream);throw e}
        suspend fun restore(p:Parsed) {
            fun localId(id:String):String {val original=id.removePrefix("beta-copy:");return if(context.packageName==TransferSnapshot.STABLE) "local-copy:"+original.removePrefix("local-copy:") else original}
            TransferSnapshot.apply(context,p.measurements.copy(weights=p.measurements.weights.map {it.copy(id=localId(it.id))},fat=p.measurements.fat.map {it.copy(id=localId(it.id))}),allowStable=true)
            (context.applicationContext as DeltaApp).nutrition.restore(p.nutrition)
            preferences.forEach {name->val values=p.prefs.optJSONObject(name) ?: JSONObject();val edit=context.getSharedPreferences(name,0).edit().clear()
                values.keys().forEach {key->when(val v=values.get(key)) {is String->edit.putString(key,v);is Boolean->edit.putBoolean(key,v);is Int->edit.putInt(key,v);is Long->edit.putLong(key,v);is Double->edit.putFloat(key,v.toFloat())}}
                check(edit.commit())
            }
            io.github.ebziboi.delta.widget.WidgetUpdater.updateAll(context)
        }
        try {restore(checked)} catch(e:Exception) {runCatching {withContext(NonCancellable) {restore(parse(previous.toString()))}};throw e}
    }}
    suspend fun read(context:Context,uri:Uri):String=withContext(Dispatchers.IO) {
        require(uri.scheme=="content")
        context.contentResolver.openInputStream(uri)?.use {it.readBytesBounded(TransferSnapshot.MAX_BYTES).toString(Charsets.UTF_8)} ?: error("Can't open the copy")
    }
}
