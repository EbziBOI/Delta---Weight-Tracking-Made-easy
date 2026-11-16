package io.github.ebziboi.delta.icons

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.ebziboi.delta.R

data class LauncherIcon(val id: String, val title: String, val art: Int)
object LauncherIcons {
    val all = listOf(LauncherIcon("Classic", "Delta classic", R.mipmap.ic_launcher),
        LauncherIcon("Icon01", "Original progress", R.mipmap.launcher_01),
        LauncherIcon("Icon02", "Data arc", R.mipmap.launcher_02),
        LauncherIcon("Icon03", "Coral scale", R.mipmap.launcher_03),
        LauncherIcon("Icon04", "Momentum", R.mipmap.launcher_04),
        LauncherIcon("Icon05", "Habit grid", R.mipmap.launcher_05),
        LauncherIcon("Icon06", "Arctic peak", R.mipmap.launcher_06),
        LauncherIcon("Icon07", "Arctic trend", R.mipmap.launcher_07),
        LauncherIcon("Icon08", "Arctic bars", R.mipmap.launcher_08),
        LauncherIcon("Icon09", "Arctic scale", R.mipmap.launcher_09))
    fun apply(context: Context, id: String) {
        val chosen = all.firstOrNull { it.id == id } ?: all.first()
        val pm = context.packageManager
        fun name(icon: LauncherIcon) = ComponentName(context, "io.github.ebziboi.delta.Launcher${icon.id}")
        fun state(icon: LauncherIcon) = if (icon == chosen) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val pending = all.filter { pm.getComponentEnabledSetting(name(it)) != state(it) }
        if (pending.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33) pm.setComponentEnabledSettings(pending.map {
            PackageManager.ComponentEnabledSetting(name(it), state(it), PackageManager.DONT_KILL_APP)
        }) else {
            // Enable the new entry before removing the old one, so the app is always launchable.
            pm.setComponentEnabledSetting(name(chosen), state(chosen), PackageManager.DONT_KILL_APP)
            all.filter { it != chosen }.forEach { pm.setComponentEnabledSetting(name(it), state(it), PackageManager.DONT_KILL_APP) }
        }
    }
}
