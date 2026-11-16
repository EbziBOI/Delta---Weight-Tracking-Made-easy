package io.github.ebziboi.delta.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.asImageBitmap
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.render.WidgetBitmaps
import io.github.ebziboi.delta.render.WidgetKind
import io.github.ebziboi.delta.widget.WidgetCatalog
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
import androidx.compose.ui.Alignment
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
import io.github.ebziboi.delta.widget.PaceWidgetReceiver
import io.github.ebziboi.delta.widget.ProgressWidgetReceiver
import io.github.ebziboi.delta.widget.SummaryWidgetReceiver
import io.github.ebziboi.delta.widget.TrendWidgetReceiver
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
    var fatTarget by remember { mutableStateOf(false) }
    if (fatTarget) PercentDialog("Target body fat", settings.targetBodyFat, { fatTarget = false }, showDate = false) { value, _ ->
        vm.update { it.copy(targetBodyFat = value) }; fatTarget = false
    }
    var section by rememberSaveable { mutableStateOf("") }
    if (section == "Nutrition targets" || section == "Photo logging") {
        androidx.activity.compose.BackHandler { section="" }
        io.github.ebziboi.delta.nutrition.NutritionSettingsEntry(section,padding,{section=""})
        return
    }
    if (section.isNotEmpty()) {
        SettingsDetail(section, vm, settings, snapshot, padding, onBack = { section = "" }, health = health, onConnect = onConnect)
        return
    }
    // Asked once: the answer depends on the home screen app, which does not change while this is open.
    val canPin = remember { canPinWidgets(context) }

    Column(
        // Scroll the inset with the content so the viewport continues beneath the edge fade.
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = padding.calculateTopPadding())
            .padding(bottom = padding.calculateBottomPadding())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(title = "Settings")
        if(io.github.ebziboi.delta.BuildConfig.IS_BETA) io.github.ebziboi.delta.transfer.BetaCopyCard(health,onConnect)
        io.github.ebziboi.delta.transfer.FullTransferCard()

        ContentSection(tone = ModuleTone.SAGE,framed=true) {
            Caption("Body composition")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Weight", style = LocalDeltaType.current.bodySmall)
                    Text(snapshot?.currentKg?.let { Units.weight(it, unit) } ?: "—", style = LocalDeltaType.current.numberLarge)
                }
                Column {
                    Text("Body fat", style = LocalDeltaType.current.bodySmall)
                    Text(snapshot?.bodyFat?.lastOrNull()?.percent?.let(io.github.ebziboi.delta.data.BodyFat::format) ?: "—", style = LocalDeltaType.current.numberLarge)
                }
            }
            SettingRow("Target body fat", settings.targetBodyFat?.let(io.github.ebziboi.delta.data.BodyFat::format) ?: "Not set", onClick = { fatTarget = true })
            if (settings.targetBodyFat != null) TextAction("Remove body fat target", { vm.update { it.copy(targetBodyFat = null) } })
        }

        ContentSection(onClick = { section = "Health Connect" }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Health Connect", style = LocalDeltaType.current.title)
                val ready = health?.availability == HealthAvailability.AVAILABLE && health.missing.isEmpty()
                val partial = health?.canRead == true || health?.canReadBodyFat == true || health?.canReadNutrition == true
                StatusPill(if (health == null) "Checking" else if(io.github.ebziboi.delta.BuildConfig.IS_BETA && ready) "Read only" else if (ready) "Connected" else if (partial) "Needs access" else "Not connected",
                    Color(if (health == null) colors.textDim else if (ready) colors.good else if (partial) colors.warn else LocalDerivedTheme.current.danger))
            }
        }

        ContentSection {
            Caption("Nutrition")
            SettingRow("Daily targets", "Edit", onClick={section="Nutrition targets"})
            SettingRow("Photo logging", "API keys", onClick={section="Photo logging"})
        }

        // Weight goals
        ContentSection {
            Caption("Weight")
            SettingRow(
                title = "Target weight",
                value = settings.targetKg?.let { Units.weight(it, unit) } ?: "Set",
                onClick = { editing = "target" },
            )
            SettingRow(
                title = "Target date",
                value = settings.targetEpochDay?.let { Format.day(LocalDate.ofEpochDay(it)) } ?: "None",
                note = "Optional",
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
                note = null,
                onClick = { editing = "startDate" },
            )
            SettingRow(
                title = "Starting weight",
                value = snapshot?.startKg?.let { Units.weight(it, unit) } ?: "—",
                note = snapshot?.startMs?.let { "First recorded on ${Format.moment(it)}" }
                    ?: "Your first weigh-in on or after the start date",
            )
        }


        // Units
        ContentSection {
            Caption("Units")
            SegmentedControl(
                options = WeightUnit.entries,
                selected = unit,
                label = { it.label },
                onSelect = { picked -> vm.update { it.copy(unit = picked) } },
            )
        }

        ContentSection {
            NavRow(io.github.ebziboi.delta.R.drawable.ic_palette, "Appearance", onClick = { section = "Appearance" })
            GlassDivider()
            NavRow(io.github.ebziboi.delta.R.drawable.ic_delta_mark, "Complications", onClick = { section = "Complications" })
            GlassDivider()
            NavRow(io.github.ebziboi.delta.R.drawable.ic_show_chart, "Graphs", onClick = { section = "Graphs" })
            GlassDivider()
            NavRow(io.github.ebziboi.delta.R.drawable.ic_widgets, "Widgets", onClick = { section = "Widgets" })
        }


        Text(
            text = "${if(io.github.ebziboi.delta.BuildConfig.IS_BETA) "Delta Beta" else "Delta"} ${versionName(context)}",
            color = Color(colors.textDim),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
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

/** Whether the home screen in use lets an app offer its widgets directly. */
private fun canPinWidgets(context: Context): Boolean =
    runCatching { AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported }.getOrDefault(false)

/** Asks the home screen to add a widget; the home screen shows its own confirmation. */
private fun pinWidget(context: Context, receiver: Class<*>) {
    runCatching {
        AppWidgetManager.getInstance(context)
            .requestPinAppWidget(ComponentName(context, receiver), null, null)
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


@Composable
private fun SettingsDetail(
    title: String, vm: AppViewModel, settings: AppSettings,
    snap: io.github.ebziboi.delta.data.ProgressSnapshot?, padding: PaddingValues, onBack: () -> Unit, health: HealthState?, onConnect: () -> Unit,
) {
    val colors = LocalDeltaColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(top = padding.calculateTopPadding()).padding(horizontal = 16.dp)
            .padding(bottom = padding.calculateBottomPadding() + 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(title = title,leading={TextAction("Back",onBack)})
        when (title) {
            "Health Connect" -> HealthConnectDetails(vm, settings, health, onConnect)
            "Appearance" -> AppearanceControls(vm, settings)
            "Complications" -> ComplicationControls(vm,settings)
            "Graphs" -> {
                ContentSection {
                    Caption("In the app")
                    SegmentedControl(ChartStyle.entries, settings.appChart, { it.label }) { chosen -> vm.update { it.copy(appChart = chosen) } }
                    if (snap != null) TrendChartView(
                        io.github.ebziboi.delta.render.ChartSpec.journey(snap, settings.appChart, settings.unit, settings.showProjection),
                        Modifier.fillMaxWidth().height(190.dp))
                }
                ContentSection {
                    ToggleRow("Path to the goal", settings.showProjection, { enabled -> vm.update { it.copy(showProjection = enabled) } },
                        note = null)
                }
            }
            "Widgets" -> {
                ContentSection {
                    Caption("Changing a widget's look")
                    Text("Hold a home-screen widget, then tap Settings.", color = Color(colors.textDim))
                }
                val food by vm.nutrition.collectAsStateWithLifecycle()
                FoodWidgetSettings(settings,food) {kind->pinWidget(context,io.github.ebziboi.delta.widget.FoodWidgetCatalog.receiver(kind))}
                for (kind in WidgetKind.entries) {
                    ContentSection {
                        Text(kind.title, style = LocalDeltaType.current.title)
                        Text(kind.detail, style = LocalDeltaType.current.bodySmall, color = Color(colors.textDim))
                        if (snap != null) {
                            // Drawn at the size it first takes on a home screen, shrunk only if the screen is narrower.
                            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val scale = minOf(1f, maxWidth.value / kind.defaultWidth)
                                val image = remember(kind, snap, settings) {
                                    WidgetBitmaps.render(context, kind, kind.defaultWidth, kind.defaultHeight, snap, settings, maxSidePx = 900f).asImageBitmap()
                                }
                                Image(image, contentDescription = "${kind.title} preview",
                                    modifier = Modifier.size((kind.defaultWidth * scale).dp, (kind.defaultHeight * scale).dp))
                            }
                        }
                        if (canPinWidgets(context)) SecondaryButton("Add", onClick = { pinWidget(context, WidgetCatalog.receiver(kind)) })
                    }
                }
            }
        }
    }
}

/**
 * Imports a smart scale app's CSV export into Health Connect. When the file holds
 * more than one person, asks whose weigh-ins to bring in.
 */
@Composable
private fun ScaleImportRow(vm: AppViewModel) {
    val colors = LocalDeltaColors.current
    val type = LocalDeltaType.current
    val importing by vm.importing.collectAsStateWithLifecycle()
    val choice by vm.importChoice.collectAsStateWithLifecycle()
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) vm.importFile(uri) }
    SecondaryButton(if (importing) "Importing…" else "Import scale export", onClick = {
        picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
    }, enabled = !importing, icon = io.github.ebziboi.delta.R.drawable.ic_download)
    Text("A CSV file from your smart scale's app. Weigh-ins already in Health Connect are skipped.",
        color = Color(colors.textDim), style = type.bodySmall)

    val export = choice ?: return
    GlassDialog(onDismissRequest = { vm.choosePerson(null) }, title = "Whose weigh-ins?", confirmLabel = "Cancel",
        onConfirm = { vm.choosePerson(null) }, dismissLabel = null) {
        Text("This file has weigh-ins for more than one person.", color = Color(colors.textDim), style = type.body)
        for ((name, readings) in export.people) {
            ListRow(onClick = { vm.choosePerson(name) }) {
                Text(name.ifBlank { "Unnamed" }, style = type.bodyStrong, color = Color(colors.text), modifier = Modifier.weight(1f))
                Text("${readings.size} weigh-ins", style = type.bodySmall, color = Color(colors.textDim))
            }
        }
    }
}

@Composable
private fun HealthConnectDetails(vm: AppViewModel, settings: AppSettings, health: HealthState?, onConnect: () -> Unit) {
    val colors = LocalDeltaColors.current
    val context = LocalContext.current
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    ContentSection {
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
                    if(!io.github.ebziboi.delta.BuildConfig.IS_BETA) AccessRow("Background updates for widgets", health.hasBackground)
                    if(!io.github.ebziboi.delta.BuildConfig.IS_BETA) AccessRow("Write weight", health.canWrite)
                    AccessRow("Read body fat", health.canReadBodyFat)
                    AccessRow("Read food and nutrients", health.canReadNutrition)
                    Text("Food records are read-only. Local diary entries stay in Delta.",style=LocalDeltaType.current.bodySmall)
                    if(!io.github.ebziboi.delta.BuildConfig.IS_BETA) AccessRow("Write body fat", health.canWriteBodyFat)
                    if (health.missing.isNotEmpty()) {
                        PrimaryButton("Allow access",onConnect)
                    }
                }
            }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton(if (syncing) "Syncing…" else "Sync now", { vm.refresh(announce = true) }, enabled = !syncing)
                GlassButton("Open Health Connect", { openHealthConnect(context) })
            }
            ScaleImportRow(vm)
            if (settings.lastSyncMs > 0L) {
                Text(
                    text = "Last synced  ·  ${Format.moment(settings.lastSyncMs)}",
                    color = Color(colors.textDim),
                    fontSize = 13.sp,
                )
            }
    }
}
