package io.github.ebziboi.delta.icons

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.ebziboi.delta.data.SettingsStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Restore the saved launcher alias before the app is next opened after updating. */
class IconUpdateReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!=Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {LauncherIcons.apply(context,SettingsStore(context).settings.first().appIcon);io.github.ebziboi.delta.widget.FoodWidgetWorker.request(context)}
            finally {pending.finish()}
        }
    }
}
