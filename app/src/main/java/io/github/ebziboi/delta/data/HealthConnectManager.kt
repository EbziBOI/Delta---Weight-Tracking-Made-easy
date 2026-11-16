package io.github.ebziboi.delta.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.NutritionRecord
import io.github.ebziboi.delta.nutrition.DiaryEntry
import io.github.ebziboi.delta.nutrition.toDiaryEntry
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId

enum class HealthAvailability { AVAILABLE, NEEDS_UPDATE, UNAVAILABLE }

/** What Delta is currently allowed to do with Health Connect. */
data class HealthState(
    val availability: HealthAvailability,
    val canRead: Boolean = false,
    val canReadNutrition: Boolean = false,
    val canWrite: Boolean = false,
    val canReadBodyFat: Boolean = false,
    val canWriteBodyFat: Boolean = false,
    /** Weigh-ins older than 30 days before access was granted, which is what reaches back to September. */
    val hasHistory: Boolean = false,
    /** Reading while the app is closed, which keeps widgets current. */
    val hasBackground: Boolean = false,
) {
    val connected: Boolean get() = availability == HealthAvailability.AVAILABLE && canRead

    /** The permissions still worth asking for. */
    val missing: Set<String>
        get() = buildSet {
            if (!canReadNutrition) add(HealthConnectManager.READ_NUTRITION)
            if (!canReadBodyFat) add(HealthConnectManager.READ_BODY_FAT)
            if (!io.github.ebziboi.delta.BuildConfig.IS_BETA && !canWriteBodyFat) add(HealthConnectManager.WRITE_BODY_FAT)
            if (!canRead) add(HealthConnectManager.READ_WEIGHT)
            if (!io.github.ebziboi.delta.BuildConfig.IS_BETA && !canWrite) add(HealthConnectManager.WRITE_WEIGHT)
            if (!hasHistory) add(HealthConnectManager.HISTORY)
            if (!io.github.ebziboi.delta.BuildConfig.IS_BETA && !hasBackground) add(HealthConnectManager.BACKGROUND)
        }
}

/** The only place that talks to Health Connect. */
class HealthConnectManager(context: Context) {
    private val appContext = context.applicationContext

    companion object {
        val READ_NUTRITION: String = HealthPermission.getReadPermission(NutritionRecord::class)
        val READ_BODY_FAT: String = HealthPermission.getReadPermission(BodyFatRecord::class)
        val WRITE_BODY_FAT: String = HealthPermission.getWritePermission(BodyFatRecord::class)
        val READ_WEIGHT: String = HealthPermission.getReadPermission(WeightRecord::class)
        val WRITE_WEIGHT: String = HealthPermission.getWritePermission(WeightRecord::class)

        // Platform permission names, as declared in the manifest.
        const val HISTORY = "android.permission.health.READ_HEALTH_DATA_HISTORY"
        const val BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    }

    fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(appContext)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NEEDS_UPDATE
        else -> HealthAvailability.UNAVAILABLE
    }

    private fun client(): HealthConnectClient? =
        if (availability() == HealthAvailability.AVAILABLE) HealthConnectClient.getOrCreate(appContext) else null

    suspend fun state(): HealthState {
        val availability = availability()
        val client = client() ?: return HealthState(availability)
        val granted = try {
            client.permissionController.getGrantedPermissions()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            emptySet()
        }
        return HealthState(
            availability = availability,
            canRead = READ_WEIGHT in granted,
            canReadNutrition = READ_NUTRITION in granted,
            canReadBodyFat = READ_BODY_FAT in granted,
            canWriteBodyFat = WRITE_BODY_FAT in granted,
            canWrite = WRITE_WEIGHT in granted,
            hasHistory = HISTORY in granted,
            hasBackground = BACKGROUND in granted,
        )
    }

    /** Every weigh-in since [since], oldest first, from every app that writes weight. */
    suspend fun readWeights(since: Instant): List<WeightEntry> {
        val client = client() ?: return emptyList()
        val entries = ArrayList<WeightEntry>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.after(since),
                    ascendingOrder = true,
                    pageSize = 1000,
                    pageToken = pageToken,
                )
            )
            for (record in response.records) {
                entries += WeightEntry(
                    epochMs = record.time.toEpochMilli(),
                    kg = record.weight.inKilograms,
                    own = record.metadata.dataOrigin.packageName == appContext.packageName,
                    id = record.metadata.id,
                )
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        return entries
    }

    suspend fun readNutrition(since: Instant = Instant.EPOCH): List<DiaryEntry> {
        val client=client() ?: error("Health Connect is not available")
        val result=mutableListOf<DiaryEntry>();var token:String?=null
        val end=Instant.now()
        do {
            val page=client.readRecords(ReadRecordsRequest(NutritionRecord::class,
                timeRangeFilter=TimeRangeFilter.between(since,end),ascendingOrder=true,pageSize=1000,pageToken=token))
            result+=page.records.map {r->
                val label=runCatching {appContext.packageManager.getApplicationInfo(r.metadata.dataOrigin.packageName,0)
                    .let {appContext.packageManager.getApplicationLabel(it).toString()}}.getOrDefault("Connected app")
                r.toDiaryEntry(sourceLabel=label)
            };token=page.pageToken?.takeIf {it.isNotBlank()}
        } while(token!=null)
        return result.distinctBy {it.id}
    }

    suspend fun writeWeight(kg: Double, at: Instant) {
        check(!io.github.ebziboi.delta.BuildConfig.IS_BETA) { "Beta cannot write to Health Connect" }
        val client = client() ?: throw IllegalStateException("Health Connect is not available")
        val record = WeightRecord(
            time = at,
            zoneOffset = ZoneId.systemDefault().rules.getOffset(at),
            weight = Mass.kilograms(kg),
            metadata = Metadata.manualEntry(),
        )
        client.insertRecords(listOf(record))
    }

    suspend fun readBodyFat(since: Instant): List<BodyFatEntry> {
        val client = client() ?: return emptyList()
        val result = ArrayList<BodyFatEntry>()
        var token: String? = null
        do {
            val response = client.readRecords(ReadRecordsRequest(BodyFatRecord::class,
                timeRangeFilter = TimeRangeFilter.after(since), ascendingOrder = true, pageSize = 1000, pageToken = token))
            response.records.forEach { r ->
                if (BodyFat.valid(r.percentage.value)) result += BodyFatEntry(r.time.toEpochMilli(), r.percentage.value,
                    r.metadata.dataOrigin.packageName == appContext.packageName, r.metadata.id)
            }
            token = response.pageToken
        } while (token != null)
        return result
    }

    suspend fun writeBodyFat(percent: Double, at: Instant, clientId: String? = null) {
        check(!io.github.ebziboi.delta.BuildConfig.IS_BETA) { "Beta cannot write to Health Connect" }
        require(BodyFat.valid(percent)) { "Body fat must be greater than 0 and at most 100 percent" }
        val client = client() ?: error("Health Connect is not available")
        client.insertRecords(listOf(BodyFatRecord(at, ZoneId.systemDefault().rules.getOffset(at), Percentage(percent), if (clientId == null) Metadata.manualEntry() else Metadata.manualEntry(clientRecordId = clientId))))
    }

    suspend fun writeMeasurement(kg: Double, percent: Double?, at: Instant) {
        check(!io.github.ebziboi.delta.BuildConfig.IS_BETA) { "Beta cannot write to Health Connect" }
        require(kg.isFinite() && kg in 20.0..400.0)
        require(percent == null || BodyFat.valid(percent))
        val client = client() ?: error("Health Connect is not available")
        val records = mutableListOf<androidx.health.connect.client.records.Record>(
            WeightRecord(at, ZoneId.systemDefault().rules.getOffset(at), Mass.kilograms(kg), Metadata.manualEntry()))
        percent?.let { records += BodyFatRecord(at, ZoneId.systemDefault().rules.getOffset(at), Percentage(it), Metadata.manualEntry()) }
        client.insertRecords(records)
    }

    suspend fun deleteBodyFat(id: String) {
        check(!io.github.ebziboi.delta.BuildConfig.IS_BETA) { "Beta cannot write to Health Connect" }
        val client = client() ?: error("Health Connect is not available")
        client.deleteRecords(BodyFatRecord::class, listOf(id), emptyList())
    }

    /** Writes many weigh-ins at once, in batches Health Connect accepts. */
    suspend fun writeWeights(readings: List<ImportedReading>) {
        check(!io.github.ebziboi.delta.BuildConfig.IS_BETA) { "Beta cannot write to Health Connect" }
        val client = client() ?: throw IllegalStateException("Health Connect is not available")
        for (batch in readings.chunked(500)) {
            client.insertRecords(batch.flatMap { reading ->
                val at = Instant.ofEpochMilli(reading.epochMs)
                buildList<androidx.health.connect.client.records.Record> { add(WeightRecord(
                    time = at,
                    zoneOffset = ZoneId.systemDefault().rules.getOffset(at),
                    weight = Mass.kilograms(reading.kg),
                    metadata = Metadata.manualEntry(clientRecordId = "delta-import-weight-${reading.epochMs}-${reading.kg}"),
                ))
                reading.bodyFatPercent?.let { add(BodyFatRecord(at, ZoneId.systemDefault().rules.getOffset(at), Percentage(it), Metadata.manualEntry())) }
                }
            })
        }
    }

    /** Health Connect only lets an app delete what it wrote itself. */
    suspend fun deleteWeight(id: String) {
        check(!io.github.ebziboi.delta.BuildConfig.IS_BETA) { "Beta cannot write to Health Connect" }
        val client = client() ?: throw IllegalStateException("Health Connect is not available")
        client.deleteRecords(
            recordType = WeightRecord::class,
            recordIdsList = listOf(id),
            clientRecordIdsList = emptyList(),
        )
    }
}
