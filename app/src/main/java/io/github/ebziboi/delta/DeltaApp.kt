package io.github.ebziboi.delta

import android.app.Application
import io.github.ebziboi.delta.data.SyncWorker
import io.github.ebziboi.delta.data.WeightRepository

class DeltaApp : Application() {

    /** One repository for the screens, the widgets and the background refresh. */
    val repo: WeightRepository by lazy { WeightRepository(this) }

    override fun onCreate() {
        super.onCreate()
        CrashRecorder.install(this)
        SyncWorker.schedule(this)
    }
}
