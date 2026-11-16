package io.github.ebziboi.delta.nutrition

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** A separate transactional database: Health Connect sync never replaces nutrition records. */
class NutritionRepository(context: Context, name: String = "nutrition.db") {
    private val appContext=context.applicationContext
    private val stores=StorePreferences(context)
    private val helper = NutritionDb(context.applicationContext,name)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(NutritionState())
    val state = mutable.asStateFlow()
    init { CoroutineScope(SupervisorJob()+Dispatchers.IO).launch { reload() } }

    suspend fun reload() = withContext(Dispatchers.IO) { mutex.withLock {
        try { mutable.value = read(helper.readableDatabase) }
        catch (_: Exception) { mutable.value=mutable.value.copy(loaded=true, loadError="Nutrition data could not be opened. Your saved database has been kept. Try again.") }
    } }
    private suspend fun change(block: (SQLiteDatabase) -> Unit) = withContext(Dispatchers.IO) { mutex.withLock {
        val db=helper.writableDatabase
        db.beginTransaction()
        try { block(db); db.setTransactionSuccessful() } finally { db.endTransaction() }
        mutable.value=read(db)
    };io.github.ebziboi.delta.widget.FoodWidgetUpdater.updateAll(appContext) }
    /** Replace only the completed Health Connect read, atomically; local meals are untouched. */
    suspend fun replaceHealthEntries(entries:List<DiaryEntry>,sinceMs:Long?=null) = change {db->
        require(entries.all {it.fromHealthConnect && it.food.source==FoodSource.HEALTH_CONNECT})
        if(sinceMs==null) db.delete("health_diary",null,null) else db.delete("health_diary","created>=?",arrayOf(sinceMs.toString()))
        entries.distinctBy {it.id}.forEach {db.insertWithOnConflict("health_diary",null,it.values(),SQLiteDatabase.CONFLICT_REPLACE)}
    }
    suspend fun syncHealth(manager:io.github.ebziboi.delta.data.HealthConnectManager):Int {
        val access=manager.state()
        if(access.availability!=io.github.ebziboi.delta.data.HealthAvailability.AVAILABLE) return 0
        // Without history access, read only within the platform's recent-data window.
        val since=if(access.hasHistory) java.time.Instant.EPOCH else java.time.Instant.now().minus(java.time.Duration.ofDays(29))
        val entries=if(access.canReadNutrition) manager.readNutrition(since) else emptyList()
        replaceHealthEntries(entries,if(access.canReadNutrition && !access.hasHistory) since.toEpochMilli() else null)
        return entries.size
    }
    suspend fun saveFood(food: Food) = change { db -> putFood(db,food) }
    suspend fun deleteFood(id: String) = change { it.delete("foods","id=?",arrayOf(id)) }
    suspend fun log(entries: List<DiaryEntry>) = change { db ->
        require(entries.isNotEmpty() && entries.size <= 500)
        require(entries.none {it.fromHealthConnect}) { "Health Connect entries are read-only" }
        entries.forEach { e -> putFood(db,e.food,replace=false); putEntry(db,e) }
        stores.record(entries.map {it.food})
    }
    suspend fun updateEntry(entry: DiaryEntry) = change { db ->
        require(!entry.fromHealthConnect) { "Health Connect entries are read-only" }
        require(db.update("diary",entry.values(),"id=?",arrayOf(entry.id)) == 1) { "This entry was removed" }
    }
    suspend fun deleteEntry(id: String) = change { it.delete("diary","id=?",arrayOf(id)) }
    suspend fun copy(from: LocalDate, to: LocalDate, meal: Meal? = null) = change { db ->
        require(from != to) { "Choose another day" }
        val source=readEntries(db).filter { !it.fromHealthConnect && it.day==from && (meal==null || it.meal==meal) }
        require(source.isNotEmpty()) { "There are no entries to copy" }
        source.forEach { putEntry(db,it.copy(id=newId(),day=to,created=System.currentTimeMillis())) }
    }
    suspend fun saveTargets(targets: NutritionTargets) = change { db ->
        targets.validated()
        val j=JSONObject().put("amounts",targets.amounts.toJson()).put("mode",targets.mode.name)
            .put("percentages",targets.percentages.toJson()).put("limits",JSONArray(targets.limits.map { it.name }))
        db.insertWithOnConflict("preferences",null,ContentValues().apply { put("id","targets");put("payload",j.toString()) },SQLiteDatabase.CONFLICT_REPLACE)
    }
    suspend fun startFast(hours: Double, now: Long = System.currentTimeMillis()) = change { db ->
        require(now <= System.currentTimeMillis()+60000) { "Start time is in the future" }
        require(readFasts(db).none { it.end==null }) { "A fast is already running" }
        putFast(db,Fast(start=now,targetHours=hours))
    }
    suspend fun finishFast(now: Long = System.currentTimeMillis()) = change { db ->
        val fast=readFasts(db).firstOrNull { it.end==null } ?: error("No fast is running")
        require(now >= fast.start) { "Check your device clock" }; putFast(db,fast.copy(end=now))
    }
    suspend fun deleteFast(id: String) = change { it.delete("fasts","id=?",arrayOf(id)) }
    fun close() = helper.close()

    private fun putFood(db: SQLiteDatabase,f: Food,replace: Boolean = true) { db.insertWithOnConflict("foods",null,
        ContentValues().apply { put("id",f.id);put("payload",f.json().toString()) },if(replace) SQLiteDatabase.CONFLICT_REPLACE else SQLiteDatabase.CONFLICT_IGNORE) }
    private fun DiaryEntry.values() = ContentValues().apply {
        put("id",id);put("day",day.toEpochDay());put("meal",meal.name);put("food",food.json().toString());put("amount",amount);put("created",created)
    }
    private fun putEntry(db: SQLiteDatabase,e: DiaryEntry) { db.insertOrThrow("diary",null,e.values()) }
    private fun putFast(db: SQLiteDatabase,f: Fast) { db.insertWithOnConflict("fasts",null,ContentValues().apply {
        put("id",f.id);put("start",f.start);put("hours",f.targetHours); if(f.end==null) putNull("end") else put("end",f.end)
    },SQLiteDatabase.CONFLICT_REPLACE) }
    private fun readEntries(db: SQLiteDatabase,table:String="diary"): List<DiaryEntry> = buildList {
        db.rawQuery("SELECT id,day,meal,food,amount,created FROM $table ORDER BY day,created",null).use { c -> while(c.moveToNext()) {
            add(DiaryEntry(c.getString(0),LocalDate.ofEpochDay(c.getLong(1)),Meal.valueOf(c.getString(2)),foodFromJson(JSONObject(c.getString(3))),c.getDouble(4),c.getLong(5)))
        } }
    }
    private fun readFasts(db: SQLiteDatabase): List<Fast> = buildList {
        db.rawQuery("SELECT id,start,hours,end FROM fasts ORDER BY start DESC",null).use { c -> while(c.moveToNext())
            add(Fast(c.getString(0),c.getLong(1),c.getDouble(2),if(c.isNull(3)) null else c.getLong(3))) }
    }
    private fun read(db: SQLiteDatabase): NutritionState {
        val foods=buildList { db.rawQuery("SELECT payload FROM foods ORDER BY rowid DESC",null).use { c -> while(c.moveToNext()) add(foodFromJson(JSONObject(c.getString(0)))) } }
        val targets=db.rawQuery("SELECT payload FROM preferences WHERE id='targets'",null).use { c -> if(c.moveToFirst()) {
            val j=JSONObject(c.getString(0));val a=j.optJSONArray("limits") ?: JSONArray()
            NutritionTargets(j.getJSONObject("amounts").nutrients(),MacroMode.valueOf(j.getString("mode")),j.getJSONObject("percentages").nutrients(),
                (0 until a.length()).map { Nutrient.valueOf(a.getString(it)) }.toSet()).validated()
        } else NutritionTargets() }
        return NutritionState(foods,(readEntries(db)+readEntries(db,"health_diary")).sortedWith(compareBy<DiaryEntry> {it.day}.thenBy {it.created}),targets,readFasts(db),loaded=true)
    }
}

private class NutritionDb(context: Context,name: String): SQLiteOpenHelper(context,name,null,2) {
    init { setWriteAheadLoggingEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE foods(id TEXT PRIMARY KEY NOT NULL,payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE diary(id TEXT PRIMARY KEY NOT NULL,day INTEGER NOT NULL,meal TEXT NOT NULL,food TEXT NOT NULL,amount REAL NOT NULL CHECK(amount>0),created INTEGER NOT NULL)")
        createHealthTable(db)
        db.execSQL("CREATE INDEX diary_day ON diary(day)")
        db.execSQL("CREATE TABLE preferences(id TEXT PRIMARY KEY NOT NULL,payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE fasts(id TEXT PRIMARY KEY NOT NULL,start INTEGER NOT NULL,hours REAL NOT NULL,end INTEGER)")
        db.execSQL("CREATE UNIQUE INDEX one_running_fast ON fasts((1)) WHERE end IS NULL")
    }
    private fun createHealthTable(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS health_diary(id TEXT PRIMARY KEY NOT NULL,day INTEGER NOT NULL,meal TEXT NOT NULL,food TEXT NOT NULL,amount REAL NOT NULL,created INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase,oldVersion: Int,newVersion: Int) {if(oldVersion<2) createHealthTable(db)}
}
