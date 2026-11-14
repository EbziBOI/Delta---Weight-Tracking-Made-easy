package io.github.ebziboi.delta.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.widget.WidgetUpdater
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Keeps the widgets current without Delta being opened: an hourly refresh, plus
 * one whenever the home screen asks a widget to update, so a weigh-in made in
 * another app or on a scale shows up on its own.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as DeltaApp

        // Several widgets ask at once; only the first needs to do the reading.
        if (inputData.getBoolean(KEY_SKIP_IF_FRESH, false)) {
            val sinceLast = System.currentTimeMillis() - app.repo.settings.first().lastSyncMs
            if (sinceLast in 0 until FRESH_FOR_MS) return Result.success()
        }

        // Without permission to read in the background this changes nothing, and
        // the widgets are redrawn from the last copy so dates and streaks stay right.
        app.repo.sync()
        WidgetUpdater.updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "delta-sync"
        private const val ON_DEMAND = "delta-sync-now"
        private const val KEY_SKIP_IF_FRESH = "skip_if_fresh"
        private const val FRESH_FOR_MS = 10 * 60 * 1000L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Asks for a refresh soon. Does nothing if one is already waiting or the data is minutes old. */
        fun requestNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf(KEY_SKIP_IF_FRESH to true))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ON_DEMAND, ExistingWorkPolicy.KEEP, request)
        }
    }
}
