package io.github.ebziboi.delta.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.data.Units
import io.github.ebziboi.delta.data.WeightEntry
import io.github.ebziboi.delta.data.WeightUnit
import kotlin.math.abs

/** Every weigh-in, newest first, with the change since the one before. */
@Composable
fun HistoryScreen(vm: AppViewModel, settings: AppSettings, padding: PaddingValues) {
    val colors = LocalDeltaColors.current
    val entries by vm.entries.collectAsStateWithLifecycle()
    val fat by vm.bodyFat.collectAsStateWithLifecycle()
    var metric by rememberSaveable { mutableStateOf("Weight") }
    var deletingFat by remember { mutableStateOf<io.github.ebziboi.delta.data.BodyFatEntry?>(null) }
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val newestFirst = remember(entries) {
        entries.distinctBy { it.id to it.epochMs }.sortedByDescending { it.epochMs }
    }
    val losing = snapshot?.losing ?: true
    var deleting by remember { mutableStateOf<WeightEntry?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") {
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                ScreenHeader(title = "History")
                SegmentedControl(listOf("Weight", "Body fat"), metric, { it }, Modifier.fillMaxWidth()) { metric = it }
                if (metric == "Weight") Text(
                    text = when (newestFirst.size) {
                        0 -> "Weigh-ins from Health Connect will appear here."
                        1 -> "1 weigh-in from Health Connect"
                        else -> "${newestFirst.size} weigh-ins from Health Connect"
                    },
                    color = Color(colors.textDim),
                    fontSize = 14.sp,
                )
            }
        }
        if (metric == "Body fat") {
            if (fat.isEmpty()) item { Text("No body fat readings yet. Log a measurement from Progress or allow body fat access in Health Connect.", color = Color(colors.textDim)) }
            itemsIndexed(fat.sortedByDescending { it.epochMs }, key = { _, r -> "fat:${r.id}:${r.epochMs}" }) { _, reading ->
                ListRow {
                    Column(Modifier.weight(1f)) {
                        Text(io.github.ebziboi.delta.data.BodyFat.format(reading.percent), style = LocalDeltaType.current.number)
                        Text(Format.moment(reading.epochMs), style = LocalDeltaType.current.bodySmall, color = Color(colors.textDim))
                    }
                    if (reading.own) IconButton({ deletingFat = reading }) {
                        Icon(Icons.Filled.Delete, "Delete body fat reading", tint = Color(colors.textDim))
                    }
                }
            }
        }
        if (metric == "Weight") itemsIndexed(newestFirst, key = { _, entry -> "${entry.id}:${entry.epochMs}" }) { index, entry ->
            HistoryRow(
                entry = entry,
                previous = newestFirst.getOrNull(index + 1),
                unit = settings.unit,
                losing = losing,
                onDelete = { deleting = entry },
                bodyFat = io.github.ebziboi.delta.data.BodyFat.near(entry.epochMs, fat)?.percent,
            )
        }
    }

    deletingFat?.let { entry ->
        GlassDialog({ deletingFat = null }, "Delete body fat reading?", "Delete", {
            deletingFat = null; vm.deleteBodyFat(entry)
        }, destructive = true) {
            Text("${io.github.ebziboi.delta.data.BodyFat.format(entry.percent)} on ${Format.moment(entry.epochMs)} will be removed from Health Connect.")
        }
    }
    val target = deleting
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this weigh-in?") },
            text = {
                Text("${Units.weight(target.kg, settings.unit)} on ${Format.moment(target.epochMs)} will be removed from Health Connect.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        vm.delete(target)
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HistoryRow(
    entry: WeightEntry,
    previous: WeightEntry?,
    unit: WeightUnit,
    losing: Boolean,
    onDelete: () -> Unit,
    bodyFat: Double? = null,
) {
    val colors = LocalDeltaColors.current
    ListRow {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = Units.weight(entry.kg, unit), style = LocalDeltaType.current.number)
            Text(text = Format.moment(entry.epochMs), color = Color(colors.textDim), fontSize = 13.sp)
            bodyFat?.let { Text("Body fat ${io.github.ebziboi.delta.data.BodyFat.format(it)}", style = LocalDeltaType.current.bodySmall, color = Color(colors.accent)) }
        }
        if (previous != null) {
            val change = entry.kg - previous.kg
            // A change in the direction of the goal is good news; the other way is not.
            val tint = when {
                abs(change) < 0.05 -> colors.textDim
                (change < 0) == losing -> colors.good
                else -> colors.warn
            }
            Text(
                text = Units.signed(change, unit),
                color = Color(tint),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (entry.own) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete weigh-in",
                    tint = Color(colors.textDim),
                )
            }
        } else {
            // Rows from other apps have no delete button; this keeps their text off the edge.
            Spacer(Modifier.width(10.dp))
        }
    }
}
