package io.github.ebziboi.delta.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.ebziboi.delta.DeltaApp
import io.github.ebziboi.delta.widget.WidgetUpdater
import java.util.concurrent.TimeUnit

/**
 * Hourly refresh so widgets pick up weigh-ins made in other apps or on a scale
 * without Delta being opened.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as DeltaApp
        // Without permission to read in the background this changes nothing, and
        // the widgets are redrawn from the last copy so dates and streaks stay right.
        app.repo.sync()
        WidgetUpdater.updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "delta-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
