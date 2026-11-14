package io.github.ebziboi.delta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.data.HealthAvailability
import io.github.ebziboi.delta.data.HealthState
import io.github.ebziboi.delta.data.Pace
import io.github.ebziboi.delta.data.ProgressSnapshot
import io.github.ebziboi.delta.data.Units
import io.github.ebziboi.delta.data.WeightUnit
import java.time.LocalDate

private val RANGES = listOf("1M", "3M", "All")

/** The home tab: the live delta, the numbers that matter, the pace and the graph. */
@Composable
fun ProgressScreen(
    vm: AppViewModel,
    settings: AppSettings,
    health: HealthState?,
    padding: PaddingValues,
    onConnect: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = LocalDeltaColors.current
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val unit = settings.unit

    Column(
        // The top inset sits outside the scrolling area so content never slides under the status bar.
        modifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding())
            .verticalScroll(rememberScrollState())
            .padding(bottom = padding.calculateBottomPadding())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Delta", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (syncing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(12.dp)
                        .size(22.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = { vm.refresh(announce = true) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Sync with Health Connect")
                }
            }
        }

        if (health != null && !health.connected) {
            DeltaCard {
                Text(text = "Connect Health Connect", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    text = when (health.availability) {
                        HealthAvailability.AVAILABLE ->
                            "Delta reads your weigh-ins from Health Connect. Allow access to see your progress."
                        HealthAvailability.NEEDS_UPDATE ->
                            "Health Connect needs an update before Delta can read your weight."
                        HealthAvailability.UNAVAILABLE ->
                            "Health Connect isn't available on this phone."
                    },
                    color = Color(colors.textDim),
                )
                if (health.availability == HealthAvailability.AVAILABLE) {
                    Button(onClick = onConnect) { Text("Allow access") }
                }
            }
        }

        val snap = snapshot
        if (snap != null) {
            Hero(snap, unit)

            if (snap.targetKg == null) {
                DeltaCard {
                    Text(text = "Set a target weight", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        text = "With a target, the delta fills in as you close the gap.",
                        color = Color(colors.textDim),
                    )
                    Button(onClick = onOpenSettings) { Text("Open settings") }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = "Start",
                    value = snap.startKg?.let { Units.weightShort(it, unit) } ?: "—",
                    note = Format.day(snap.startDay),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = if ((snap.changeKg ?: 0.0) > 0.0) "Gained" else "Lost",
                    value = snap.changeKg?.let { Units.amount(it, unit) } ?: "—",
                    note = "${snap.weighIns} weigh-ins",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "To go",
                    value = when {
                        snap.reached -> "Done"
                        else -> snap.remainingKg?.let { Units.amount(it, unit) } ?: "—"
                    },
                    note = snap.targetKg?.let { "Target ${Units.weightShort(it, unit)}" },
                    valueColor = Color(colors.accent),
                    modifier = Modifier.weight(1f),
                )
            }

            PaceCard(snap, unit)
            TrendCard(snap, unit, settings)
        }

        // Room for the Log weight button to float without covering the last card.
        Spacer(Modifier.height(88.dp))
    }
}

@Composable
private fun Hero(snap: ProgressSnapshot, unit: WeightUnit) {
    val colors = LocalDeltaColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            DeltaTrack(fraction = snap.fraction, modifier = Modifier.size(232.dp))
        }
        Spacer(Modifier.height(6.dp))
        val current = snap.currentKg
        if (current != null) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = Units.weightValue(current, unit),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = Units.suffix(unit),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(colors.textDim),
                    modifier = Modifier.padding(bottom = 9.dp),
                )
            }
            snap.currentMs?.let {
                Text(text = "Last weigh-in  ·  ${Format.moment(it)}", color = Color(colors.textDim), fontSize = 13.sp)
            }
        } else {
            Text(text = "No weigh-ins yet", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Log a weight, or sync to pull in your history.",
                color = Color(colors.textDim),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun PaceCard(snap: ProgressSnapshot, unit: WeightUnit) {
    val colors = LocalDeltaColors.current
    val today = LocalDate.now()
    DeltaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Pace", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            val pace = snap.pace
            if (pace != null) {
                val (label, tint) = when (pace) {
                    Pace.AHEAD -> "Ahead of schedule" to colors.good
                    Pace.ON_TRACK -> "On track" to colors.good
                    Pace.BEHIND -> "Behind schedule" to colors.warn
                }
                Text(text = label, color = Color(tint), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        val rate = snap.weeklyRateKg
        SettingRow(
            title = "Weekly rate",
            value = rate?.let { Units.rate(it, unit) } ?: "—",
            note = if (rate == null) "Needs a week of weigh-ins" else "Trend over recent weeks",
        )
        SettingRow(
            title = "Projected goal date",
            value = when {
                snap.reached -> "Reached"
                snap.targetKg == null -> "—"
                else -> snap.projectedDay?.let { Format.day(it) } ?: "—"
            },
            note = when {
                snap.reached || snap.targetKg == null -> null
                snap.projectedDay == null && rate != null -> "The trend isn't heading for the target right now"
                else -> "If the current rate holds"
            },
        )
        val targetDay = snap.targetDay
        if (targetDay != null && !snap.reached) {
            val required = snap.requiredRateKg
            SettingRow(
                title = "Needed for ${Format.day(targetDay)}",
                value = required?.let { Units.rate(it, unit) } ?: "—",
                note = if (targetDay.isAfter(today)) null else "That date has passed",
            )
        }
        SettingRow(
            title = "Weigh-in streak",
            value = when (snap.streakDays) {
                0 -> "—"
                1 -> "1 day"
                else -> "${snap.streakDays} days"
            },
        )
    }
}

@Composable
private fun TrendCard(snap: ProgressSnapshot, unit: WeightUnit, settings: AppSettings) {
    var range by rememberSaveable { mutableStateOf("All") }
    val points = remember(snap.points, range) {
        val days = when (range) {
            "1M" -> 30L
            "3M" -> 90L
            else -> null
        }
        if (days == null) {
            snap.points
        } else {
            val cutoff = System.currentTimeMillis() - days * 86_400_000L
            snap.points.filter { it.epochMs >= cutoff }
        }
    }

    DeltaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Trend", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            ChoiceRow(options = RANGES, selected = range, label = { it }, onSelect = { range = it })
        }
        TrendChartView(
            spec = io.github.ebziboi.delta.render.ChartSpec.journey(snap, settings.appChart, unit, settings.showProjection).copy(points = points),
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp),
        )
    }
}
