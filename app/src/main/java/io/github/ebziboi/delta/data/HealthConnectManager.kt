package io.github.ebziboi.delta.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
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
    val canWrite: Boolean = false,
    /** Weigh-ins older than 30 days before access was granted, which is what reaches back to September. */
    val hasHistory: Boolean = false,
    /** Reading while the app is closed, which keeps widgets current. */
    val hasBackground: Boolean = false,
) {
    val connected: Boolean get() = availability == HealthAvailability.AVAILABLE && canRead

    /** The permissions still worth asking for. */
    val missing: Set<String>
        get() = buildSet {
            if (!canRead) add(HealthConnectManager.READ_WEIGHT)
            if (!canWrite) add(HealthConnectManager.WRITE_WEIGHT)
            if (!hasHistory) add(HealthConnectManager.HISTORY)
            if (!hasBackground) add(HealthConnectManager.BACKGROUND)
        }
}

/** The only place that talks to Health Connect. */
class HealthConnectManager(context: Context) {
    private val appContext = context.applicationContext

    companion object {
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

    suspend fun writeWeight(kg: Double, at: Instant) {
        val client = client() ?: throw IllegalStateException("Health Connect is not available")
        val record = WeightRecord(
            time = at,
            zoneOffset = ZoneId.systemDefault().rules.getOffset(at),
            weight = Mass.kilograms(kg),
            metadata = Metadata.manualEntry(),
        )
        client.insertRecords(listOf(record))
    }

    /** Writes many weigh-ins at once, in batches Health Connect accepts. */
    suspend fun writeWeights(readings: List<ImportedReading>) {
        val client = client() ?: throw IllegalStateException("Health Connect is not available")
        for (batch in readings.chunked(500)) {
            client.insertRecords(batch.map { reading ->
                val at = Instant.ofEpochMilli(reading.epochMs)
                WeightRecord(
                    time = at,
                    zoneOffset = ZoneId.systemDefault().rules.getOffset(at),
                    weight = Mass.kilograms(reading.kg),
                    metadata = Metadata.manualEntry(),
                )
            })
        }
    }

    /** Health Connect only lets an app delete what it wrote itself. */
    suspend fun deleteWeight(id: String) {
        val client = client() ?: throw IllegalStateException("Health Connect is not available")
        client.deleteRecords(
            recordType = WeightRecord::class,
            recordIdsList = listOf(id),
            clientRecordIdsList = emptyList(),
        )
    }
}
