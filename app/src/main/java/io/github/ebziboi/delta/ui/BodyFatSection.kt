package io.github.ebziboi.delta.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.render.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Composable
fun BodyFatSection(readings: List<BodyFatEntry>, target: Double?, journeyStart: Long?,
                   chartStyle: ChartStyle = ChartStyle.CONNECTED, onSwitchStyle: (() -> Unit)? = null, onOpen: () -> Unit) {
    val profile=rememberChartController("home-body-fat",chartStyle)
    val latest = readings.maxByOrNull { it.epochMs }
    val previous = readings.sortedBy { it.epochMs }.dropLast(1).lastOrNull()
    ContentSection(tone = ModuleTone.SAGE,onClick=onOpen) {
        val c = LocalDeltaColors.current
        Row(Modifier.fillMaxWidth().clickable(onClick=onOpen).semantics {contentDescription="Open body composition calendar"},verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {Caption("Body composition")}
            ChartPeriodControl(profile.period,profile::choosePeriod)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Body fat", style = LocalDeltaType.current.title)
                Text(latest?.let { BodyFat.format(it.percent) } ?: "No reading yet", style = LocalDeltaType.current.numberLarge)
            }
            if (latest != null && previous != null) Text(String.format(Locale.getDefault(), "%+.1f pp", latest.percent - previous.percent),
                style = LocalDeltaType.current.numberSmall, color = Color(c.textDim))
        }
        latest?.let { Text(Format.moment(it.epochMs), color = Color(c.textDim), style = LocalDeltaType.current.bodySmall) }
        target?.let { Text("Target ${BodyFat.format(it)}", style = LocalDeltaType.current.bodyStrong) }
        if (readings.isNotEmpty()) {
            ChartWindows(profile)
            val values=readings.map {RecordedValue(Instant.ofEpochMilli(it.epochMs).atZone(ZoneId.systemDefault()).toLocalDate(),it.percent)}
            RecordedChart(listOf(RecordedSeries("body-fat","Body fat",Color(bodyFatInk(c)),values,{BodyFat.format(it)})),values.minOf {it.day},LocalDate.now(),"Body fat overview graph",controller=profile,controls=false,journeyStart=journeyStart?.let {Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()},minimumYSpan=.5,target=target)
        }
    }
}

@Composable
fun PercentDialog(title: String, initial: Double?, onDismiss: () -> Unit, showDate: Boolean = true, onConfirm: (Double, Instant) -> Unit) {
    var text by remember { mutableStateOf(initial?.let { String.format(Locale.US, "%.1f", it) } ?: "") }
    var day by remember { mutableStateOf(LocalDate.now()) }
    var picking by remember { mutableStateOf(false) }
    val value = text.replace(',', '.').removeSuffix("%").toDoubleOrNull()?.takeIf(BodyFat::valid)
    GlassDialog(onDismiss, title, "Save", { value?.let {
        val at = if (day == LocalDate.now()) Instant.now() else day.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant()
        onConfirm(it, at)
    } }, confirmEnabled = value != null) {
        androidx.compose.material3.OutlinedTextField(text, { text = it }, label = { Text("Body fat %") },
            suffix = { Text("%") }, isError = text.isNotBlank() && value == null, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        if (showDate) TextAction(Format.day(day), { picking = true })
    }
    if (picking) DateDialog(day, { picking = false }, { day = it.coerceAtMost(LocalDate.now()); picking = false })
}
