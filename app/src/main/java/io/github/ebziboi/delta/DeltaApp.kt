package io.github.ebziboi.delta

import android.app.Application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import io.github.ebziboi.delta.data.SyncWorker
import io.github.ebziboi.delta.data.WeightRepository

class DeltaApp : Application() {

    /** One repository for the screens, the widgets and the background refresh. */
    val repo: WeightRepository by lazy { WeightRepository(this) }
    val nutrition by lazy { io.github.ebziboi.delta.nutrition.NutritionRepository(this) }

    override fun onCreate() {
        super.onCreate()
        CrashRecorder.install(this)
        SyncWorker.schedule(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { io.github.ebziboi.delta.icons.LauncherIcons.apply(this@DeltaApp, repo.settings.first().appIcon) }
        }
    }
}
