package io.github.ebziboi.delta.nutrition

import android.content.Context
import io.github.ebziboi.delta.data.HealthConnectManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Mirrors the local diary into Health Connect. It remembers what it last wrote for each line, so it only
 * writes new or changed lines, removes deleted ones, and simply catches up after a failure.
 */
internal class NutritionHealthWriter(context:Context,private val health:HealthConnectManager) {
    private val prefs=context.applicationContext.getSharedPreferences("health-nutrition-written",0)
    private val lock=Mutex()

    private fun signature(e:DiaryEntry)=listOf(e.day,e.meal,e.amount,e.created,e.food.id,e.food.name,e.food.per100.toSortedMap().toString()).joinToString("|").hashCode().toString()

    /** Returns how many lines were written or removed. */
    suspend fun reconcile(entries:List<DiaryEntry>):Int=lock.withLock {
        if(io.github.ebziboi.delta.BuildConfig.IS_BETA || !health.state().canWriteNutrition) return 0
        val written=prefs.all.mapValues {it.value.toString()}
        val local=entries.filter {!it.fromHealthConnect}
        val changed=local.filter {written[it.id]!=signature(it)}
        val removed=written.keys-local.map {it.id}.toSet()
        val version=System.currentTimeMillis()
        for(batch in changed.chunked(200)) {
            health.upsertNutrition(batch,version)
            prefs.edit().apply {batch.forEach {putString(it.id,signature(it))}}.apply()
        }
        for(batch in removed.chunked(200)) {
            health.deleteNutrition(batch)
            prefs.edit().apply {batch.forEach {remove(it)}}.apply()
        }
        changed.size+removed.size
    }

    /** Removes everything Delta wrote, for when writing is turned off. */
    suspend fun removeAll():Int=lock.withLock {
        if(io.github.ebziboi.delta.BuildConfig.IS_BETA) return 0
        val ids=prefs.all.keys.toList()
        if(ids.isNotEmpty() && health.state().canWriteNutrition) for(batch in ids.chunked(200)) {health.deleteNutrition(batch);prefs.edit().apply {batch.forEach {remove(it)}}.apply()}
        ids.size
    }
    val count get()=prefs.all.size
}
