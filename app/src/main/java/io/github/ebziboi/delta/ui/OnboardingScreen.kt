package io.github.ebziboi.delta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.data.HealthAvailability
import io.github.ebziboi.delta.data.HealthState
import io.github.ebziboi.delta.data.ProgressCalculator
import io.github.ebziboi.delta.data.Units
import io.github.ebziboi.delta.data.WeightUnit
import java.time.LocalDate

/** First run: say hello, connect Health Connect, set the goal. */
@Composable
fun OnboardingScreen(
    vm: AppViewModel,
    settings: AppSettings,
    health: HealthState?,
    onConnect: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (step) {
            0 -> Welcome(onNext = { step = 1 })
            1 -> Connect(health = health, onConnect = onConnect, onNext = { step = 2 })
            else -> Goal(vm = vm, settings = settings, onBack = { step = 1 })
        }
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    val colors = LocalDeltaColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        DeltaTrack(fraction = null, modifier = Modifier.size(190.dp))
        Text(text = "Delta", fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "Your weight, from where you started to where you're going.",
            color = Color(colors.textDim),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Get started", modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun Connect(health: HealthState?, onConnect: () -> Unit, onNext: () -> Unit) {
    val colors = LocalDeltaColors.current
    Text(
        text = "Connect Health Connect",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = "Delta reads the weigh-ins already on your phone, so your progress starts from " +
            "your real history rather than from today.",
        color = Color(colors.textDim),
    )

    if (health != null && health.availability != HealthAvailability.AVAILABLE) {
        DeltaCard {
            Text(
                text = if (health.availability == HealthAvailability.NEEDS_UPDATE) {
                    "Health Connect needs an update before Delta can use it. Update it in the Play Store, then come back."
                } else {
                    "Health Connect isn't available on this phone, so Delta has no weight data to read."
                },
                color = Color(colors.warn),
            )
        }
    } else {
        DeltaCard {
            AccessLine("Read weight", "Your weigh-ins, from any app or scale", health?.canRead == true)
            AccessLine("Past data", "Reaches back before today, to your start date", health?.hasHistory == true)
            AccessLine("Background updates", "Keeps widgets current while Delta is closed", health?.hasBackground == true)
            AccessLine("Write weight", "Saves weigh-ins you log in Delta", health?.canWrite == true)
        }
        val missing = health?.missing?.size ?: 4
        if (missing > 0) {
            // Health Connect's second screen has two switches that are off by default,
            // and the one for past data is what brings in the history.
            Text(
                text = "Health Connect asks in two steps. On the second, switch on both options: " +
                    "past data is what reaches back to your start date.",
                color = Color(colors.textDim),
                fontSize = 14.sp,
            )
            Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (health?.canRead == true) "Allow the rest" else "Connect",
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
        if (health?.canRead == true && !health.hasHistory) {
            Text(
                text = "Without past data, Delta can only see weigh-ins from the last 30 days.",
                color = Color(colors.warn),
                fontSize = 13.sp,
            )
        }
    }

    if (health?.canRead == true) {
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Continue", modifier = Modifier.padding(vertical = 6.dp))
        }
    } else {
        TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
    }
}

@Composable
private fun AccessLine(title: String, detail: String, granted: Boolean) {
    val colors = LocalDeltaColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.SemiBold)
            Text(text = detail, color = Color(colors.textDim), fontSize = 13.sp)
        }
        Text(
            text = if (granted) "Allowed" else "Not yet",
            color = if (granted) Color(colors.good) else Color(colors.textDim),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun Goal(vm: AppViewModel, settings: AppSettings, onBack: () -> Unit) {
    val colors = LocalDeltaColors.current
    val entries by vm.entries.collectAsStateWithLifecycle()
    val unit = settings.unit

    var startDay by remember {
        mutableStateOf(
            settings.startEpochDay?.let(LocalDate::ofEpochDay) ?: ProgressCalculator.defaultStartDay(LocalDate.now())
        )
    }
    var targetKg by remember { mutableStateOf(settings.targetKg) }
    var targetDay by remember { mutableStateOf(settings.targetEpochDay?.let(LocalDate::ofEpochDay)) }
    var editing by remember { mutableStateOf("") }

    // What the journey looks like with the start date as currently chosen.
    val preview = remember(entries, startDay) {
        ProgressCalculator.compute(entries, settings.copy(startEpochDay = startDay.toEpochDay()))
    }

    Text(text = "Set your goal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text(
        text = "Delta measures progress from your first weigh-in on or after your start date.",
        color = Color(colors.textDim),
    )

    Caption("Units")
    ChoiceRow(
        options = WeightUnit.values().toList(),
        selected = unit,
        label = { it.label },
        onSelect = { picked -> vm.update { it.copy(unit = picked) } },
    )

    DeltaCard {
        SettingRow(title = "Start date", value = Format.day(startDay), onClick = { editing = "start" })
        SettingRow(
            title = "Starting weight",
            value = preview.startKg?.let { Units.weight(it, unit) } ?: "No weigh-in found",
            note = if (preview.startKg == null) "You can still finish and sync later" else "From Health Connect",
        )
        SettingRow(
            title = "Current weight",
            value = preview.currentKg?.let { Units.weight(it, unit) } ?: "—",
        )
    }

    DeltaCard {
        SettingRow(
            title = "Target weight",
            value = targetKg?.let { Units.weight(it, unit) } ?: "Set",
            onClick = { editing = "target" },
        )
        SettingRow(
            title = "Target date",
            value = targetDay?.let { Format.day(it) } ?: "None",
            note = "Optional",
            onClick = { editing = "date" },
        )
    }

    Button(
        onClick = {
            vm.update {
                it.copy(
                    onboardingDone = true,
                    startEpochDay = startDay.toEpochDay(),
                    targetKg = targetKg,
                    targetEpochDay = targetDay?.toEpochDay(),
                )
            }
        },
        enabled = targetKg != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Finish", modifier = Modifier.padding(vertical = 6.dp))
    }
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }

    when (editing) {
        "start" -> DateDialog(
            initial = startDay,
            onDismiss = { editing = "" },
            onConfirm = {
                startDay = it
                editing = ""
            },
        )
        "target" -> WeightDialog(
            title = "Target weight",
            initialKg = targetKg ?: preview.currentKg,
            unit = unit,
            onDismiss = { editing = "" },
            onConfirm = {
                targetKg = it
                editing = ""
            },
            confirmLabel = "Set",
        )
        "date" -> DateDialog(
            initial = targetDay ?: LocalDate.now().plusMonths(3),
            onDismiss = { editing = "" },
            onConfirm = {
                targetDay = it
                editing = ""
            },
        )
    }
}
