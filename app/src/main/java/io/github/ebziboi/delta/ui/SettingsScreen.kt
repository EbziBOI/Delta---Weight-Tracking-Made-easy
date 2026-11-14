package io.github.ebziboi.delta.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ebziboi.delta.data.Accent
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.data.HealthAvailability
import io.github.ebziboi.delta.data.HealthState
import io.github.ebziboi.delta.data.ThemeStyle
import io.github.ebziboi.delta.data.Units
import io.github.ebziboi.delta.data.WeightUnit
import java.time.LocalDate

/** Goal, units, look and Health Connect, each in its own card. */
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    settings: AppSettings,
    health: HealthState?,
    padding: PaddingValues,
    onConnect: () -> Unit,
) {
    val colors = LocalDeltaColors.current
    val context = LocalContext.current
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val unit = settings.unit
    var editing by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Settings",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )

        // Goal
        DeltaCard {
            Caption("Goal")
            SettingRow(
                title = "Target weight",
                value = settings.targetKg?.let { Units.weight(it, unit) } ?: "Set",
                onClick = { editing = "target" },
            )
            SettingRow(
                title = "Target date",
                value = settings.targetEpochDay?.let { Format.day(LocalDate.ofEpochDay(it)) } ?: "None",
                note = "Optional. Adds the weekly rate you need and whether you're on track.",
                onClick = { editing = "targetDate" },
            )
            if (settings.targetEpochDay != null) {
                TextButton(onClick = { vm.update { it.copy(targetEpochDay = null) } }) {
                    Text("Remove target date")
                }
            }
            SettingRow(
                title = "Start date",
                value = snapshot?.startDay?.let { Format.day(it) } ?: "—",
                note = "Progress is measured from here.",
                onClick = { editing = "startDate" },
            )
            SettingRow(
                title = "Starting weight",
                value = snapshot?.startKg?.let { Units.weight(it, unit) } ?: "—",
                note = if (settings.startKgOverride != null) {
                    "Set by you"
                } else {
                    "Your first weigh-in on or after the start date"
                },
                onClick = { editing = "startWeight" },
            )
            if (settings.startKgOverride != null) {
                TextButton(onClick = { vm.update { it.copy(startKgOverride = null) } }) {
                    Text("Use my first weigh-in instead")
                }
            }
        }

        // Units
        DeltaCard {
            Caption("Units")
            ChoiceRow(
                options = WeightUnit.values().toList(),
                selected = unit,
                label = { it.label },
                onSelect = { picked -> vm.update { it.copy(unit = picked) } },
            )
        }

        // Look
        DeltaCard {
            Caption("Theme")
            ChoiceRow(
                options = ThemeStyle.values().toList(),
                selected = settings.theme,
                label = { it.label },
                onSelect = { picked -> vm.update { it.copy(theme = picked) } },
            )
            Caption("Accent colour")
            if (settings.theme == ThemeStyle.DYNAMIC) {
                Text(
                    text = "Material You takes its colours from your wallpaper.",
                    color = Color(colors.textDim),
                    fontSize = 13.sp,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (accent in Accent.values()) {
                        val tint = Color((if (colors.isLight) accent.onLight else accent.onDark).toInt())
                        val selected = accent == settings.accent
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(tint)
                                .border(
                                    width = 3.dp,
                                    color = if (selected) Color(colors.text) else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable { vm.update { it.copy(accent = accent) } }
                                .semantics { contentDescription = "${accent.label} accent" },
                        )
                    }
                }
            }
            Text(
                text = "Widgets follow the theme and accent you choose here.",
                color = Color(colors.textDim),
                fontSize = 13.sp,
            )
        }

        // Health Connect
        DeltaCard {
            Caption("Health Connect")
            when {
                health == null -> Text(text = "Checking…", color = Color(colors.textDim))
                health.availability != HealthAvailability.AVAILABLE -> Text(
                    text = if (health.availability == HealthAvailability.NEEDS_UPDATE) {
                        "Health Connect needs an update before Delta can use it."
                    } else {
                        "Health Connect isn't available on this phone."
                    },
                    color = Color(colors.warn),
                )
                else -> {
                    AccessRow("Read weight", health.canRead)
                    AccessRow("Past data, back to your start date", health.hasHistory)
                    AccessRow("Background updates for widgets", health.hasBackground)
                    AccessRow("Write weight", health.canWrite)
                    if (health.missing.isNotEmpty()) {
                        Button(onClick = onConnect) { Text("Allow access") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { vm.refresh(announce = true) }, enabled = !syncing) {
                    Text(if (syncing) "Syncing…" else "Sync now")
                }
                OutlinedButton(onClick = { openHealthConnect(context) }) { Text("Open Health Connect") }
            }
            if (settings.lastSyncMs > 0L) {
                Text(
                    text = "Last synced  ·  ${Format.moment(settings.lastSyncMs)}",
                    color = Color(colors.textDim),
                    fontSize = 13.sp,
                )
            }
        }

        // Widgets and about
        DeltaCard {
            Caption("Widgets")
            Text(
                text = "Touch and hold an empty part of your home screen, choose Widgets, then Delta. " +
                    "There are four: progress, start/now/target, trend and pace. Each can be resized.",
                color = Color(colors.textDim),
                fontSize = 14.sp,
            )
        }
        Text(
            text = "Delta ${versionName(context)}",
            color = Color(colors.textDim),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(24.dp))
    }

    when (editing) {
        "target" -> WeightDialog(
            title = "Target weight",
            initialKg = settings.targetKg ?: snapshot?.currentKg,
            unit = unit,
            onDismiss = { editing = "" },
            onConfirm = { kg ->
                vm.update { it.copy(targetKg = kg) }
                editing = ""
            },
            confirmLabel = "Set",
        )
        "startWeight" -> WeightDialog(
            title = "Starting weight",
            initialKg = snapshot?.startKg,
            unit = unit,
            onDismiss = { editing = "" },
            onConfirm = { kg ->
                vm.update { it.copy(startKgOverride = kg) }
                editing = ""
            },
            confirmLabel = "Set",
        )
        "targetDate" -> DateDialog(
            initial = settings.targetEpochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now().plusMonths(3),
            onDismiss = { editing = "" },
            onConfirm = { day ->
                vm.update { it.copy(targetEpochDay = day.toEpochDay()) }
                editing = ""
            },
        )
        "startDate" -> DateDialog(
            initial = snapshot?.startDay,
            onDismiss = { editing = "" },
            onConfirm = { day ->
                vm.update { it.copy(startEpochDay = day.toEpochDay()) }
                editing = ""
            },
        )
    }
}

@Composable
private fun AccessRow(title: String, granted: Boolean) {
    val colors = LocalDeltaColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = title, modifier = Modifier.weight(1f), fontSize = 15.sp)
        Text(
            text = if (granted) "Allowed" else "Not allowed",
            color = if (granted) Color(colors.good) else Color(colors.warn),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Opens the system's Health Connect screen, where access can be reviewed or withdrawn. */
private fun openHealthConnect(context: Context) {
    val action = if (Build.VERSION.SDK_INT >= 34) {
        "android.health.connect.action.HEALTH_HOME_SETTINGS"
    } else {
        "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
    }
    runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun versionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        .getOrNull() ?: ""
