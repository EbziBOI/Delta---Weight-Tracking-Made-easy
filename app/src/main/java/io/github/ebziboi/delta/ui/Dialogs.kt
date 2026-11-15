package io.github.ebziboi.delta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ebziboi.delta.data.Units
import io.github.ebziboi.delta.data.WeightUnit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Any body weight outside this range is treated as a typing mistake. */
private val SENSIBLE_KG = 20.0..400.0

/**
 * Asks for a weight in the user's unit and hands it back in kilograms.
 * [extra] adds content under the field, such as the date of a weigh-in.
 */
@Composable
fun WeightDialog(
    title: String,
    initialKg: Double?,
    unit: WeightUnit,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
    confirmLabel: String = "Save",
    extra: (@Composable () -> Unit)? = null,
    extraValid: Boolean = true,
) {
    val initialStone = remember(initialKg) { initialKg?.let { Units.stoneParts(it) } }
    // Fields start with their contents selected, so typing a new number replaces the old one.
    var stonesField by remember { mutableStateOf(selected(initialStone?.first?.toString() ?: "")) }
    var mainField by remember {
        mutableStateOf(
            selected(
                when {
                    initialKg == null -> ""
                    unit == WeightUnit.KG -> Units.weightValue(initialKg, WeightUnit.KG)
                    unit == WeightUnit.LB -> Units.weightValue(initialKg, WeightUnit.LB)
                    else -> initialStone?.second?.toString() ?: ""
                }
            )
        )
    }
    val stones = stonesField.text
    val main = mainField.text

    val kg: Double? = when (unit) {
        WeightUnit.KG -> Units.parse(main)
        WeightUnit.LB -> Units.parse(main)?.let { Units.lbToKg(it) }
        WeightUnit.ST_LB -> {
            val wholeStones = stones.trim().toIntOrNull()
            val pounds = if (main.isBlank()) 0.0 else Units.parse(main)
            if (wholeStones != null && pounds != null && pounds >= 0.0 && pounds < 14.0) {
                Units.stoneToKg(wholeStones, pounds)
            } else {
                null
            }
        }
    }
    val valid = kg != null && kg in SENSIBLE_KG

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (unit == WeightUnit.ST_LB) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassTextField(
                            value = stonesField,
                            onValueChange = { stonesField = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focus),
                            singleLine = true,
                            suffix = { Text("st") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        GlassTextField(
                            value = mainField,
                            onValueChange = { mainField = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            suffix = { Text("lb") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                } else {
                    GlassTextField(
                        value = mainField,
                        onValueChange = { mainField = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                        singleLine = true,
                        suffix = { Text(Units.suffix(unit)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                if (main.isNotBlank() && !valid) {
                    Text(
                        text = "That doesn't look like a body weight.",
                        color = Color(LocalDeltaColors.current.warn),
                        fontSize = 13.sp,
                    )
                }
                extra?.invoke()
            }
        },
        confirmButton = {
            TextButton(onClick = { kg?.let(onConfirm) }, enabled = valid && extraValid) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun selected(text: String): TextFieldValue =
    TextFieldValue(text = text, selection = TextRange(0, text.length))

/** Logging a weigh-in: the weight, and the day if it is not today. */
@Composable
fun LogWeightDialog(
    lastKg: Double?,
    unit: WeightUnit,
    onDismiss: () -> Unit,
    onConfirm: (Double, Instant, Double?) -> Unit,
    canWriteBodyFat: Boolean = false,
    onBodyFatAccess: () -> Unit = {},
) {
    val today = remember { LocalDate.now() }
    var day by remember { mutableStateOf(today) }
    var choosingDay by remember { mutableStateOf(false) }
    var fat by remember { mutableStateOf("") }
    val parsedFat = fat.replace(',', '.').toDoubleOrNull()

    WeightDialog(
        title = "Log weight",
        initialKg = lastKg,
        unit = unit,
        onDismiss = onDismiss,
        onConfirm = { kg ->
            // Today's weigh-ins are stamped now; earlier days get a morning time.
            val at = if (day == today) {
                Instant.now()
            } else {
                day.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant()
            }
            onConfirm(kg, at, parsedFat.takeIf { canWriteBodyFat && fat.isNotBlank() })
        },
        extraValid = fat.isBlank() || (canWriteBodyFat && parsedFat?.let(io.github.ebziboi.delta.data.BodyFat::valid) == true),
        extra = {
            if (canWriteBodyFat) {
                OutlinedTextField(fat, { fat = it }, label = { Text("Body fat % (optional)") }, singleLine = true,
                    suffix = { Text("%") }, isError = fat.isNotBlank() && parsedFat?.let(io.github.ebziboi.delta.data.BodyFat::valid) != true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            } else TextButton(onClick = onBodyFatAccess) { Text("Add body fat · allow access") }

            TextButton(onClick = { choosingDay = true }) {
                Text(if (day == today) "Today  ·  change date" else "${Format.day(day)}  ·  change date")
            }
        },
    )

    if (choosingDay) {
        DateDialog(
            initial = day,
            onDismiss = { choosingDay = false },
            onConfirm = { picked ->
                // A weigh-in cannot be in the future.
                day = if (picked.isAfter(today)) today else picked
                choosingDay = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    // The picker works in UTC midnights, whatever the phone's time zone.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now())
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}
