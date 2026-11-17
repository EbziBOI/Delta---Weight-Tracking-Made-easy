package io.github.ebziboi.delta.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.ebziboi.delta.ui.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun durationText(ms:Long):String {
    val seconds=(ms/1000).coerceAtLeast(0)
    return "%02d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60)
}
@Composable
internal fun FastingPage(vm:NutritionViewModel,state:NutritionState,padding:PaddingValues,onBack:()->Unit) {
    val active=state.fasts.firstOrNull {it.end==null}
    var now by remember {mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(active?.id) {while(true) {now=System.currentTimeMillis();delay(1000)}}
    var hours by rememberSaveable {mutableStateOf(amountText(state.fasts.firstOrNull()?.targetHours ?: 16.0))}
    var startEpoch by rememberSaveable {mutableLongStateOf(LocalDate.now().toEpochDay())}
    var time by rememberSaveable {mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))}
    var customStart by rememberSaveable {mutableStateOf(false)}
    var datePicker by remember {mutableStateOf(false)}
    var ending by remember {mutableStateOf(false)}
    var deleting by remember {mutableStateOf<Fast?>(null)}
    val start=if(customStart) runCatching {LocalDate.ofEpochDay(startEpoch).atTime(LocalTime.parse(time)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()}.getOrNull() else now
    NutritionPage("Fasting",padding,onBack) {
        if(active!=null) ContentSection(tone=ModuleTone.SAGE,framed=true) {
            Caption(if(active.reached(now)) "Target reached" else "Fast in progress")
            ValueWithUnit(durationText(active.elapsed(now)),null,style=LocalDeltaType.current.numberLarge)
            Text("${amountText(active.targetHours)} hour target",style=LocalDeltaType.current.body)
            ProgressRail((active.elapsed(now).toDouble()/active.goalMs).toFloat())
            Text(if(active.reached(now)) "${durationText(active.elapsed(now)-active.goalMs)} beyond target" else "${durationText(active.goalMs-active.elapsed(now))} remaining",style=LocalDeltaType.current.bodySmall)
            Text("Started ${Instant.ofEpochMilli(active.start).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))}",style=LocalDeltaType.current.bodySmall)
            Text("Target ${active.targetEnd.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))}",style=LocalDeltaType.current.bodySmall)
            PrimaryButton("End fast",{ending=true},Modifier.fillMaxWidth(),enabled=!vm.busy)
        } else ContentSection(tone=ModuleTone.SAGE,framed=true) {
            Caption("Start a fast")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(12,14,16,18,20).forEach {h ->FilterChip(hours.number()==h.toDouble(),{hours=h.toString()},label={Text("$h:${24-h}")},interactionSource=rememberHapticInteractionSource())}
            }
            NumberField(hours,{hours=it},"Fasting hours",Modifier.fillMaxWidth())
            hours.number()?.takeIf {it>0 && it<24}?.let {Text("${amountText(it)} h fasting · ${amountText(24-it)} h eating window",style=LocalDeltaType.current.bodySmall)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Choose start time",style=LocalDeltaType.current.body);Switch(customStart,{customStart=it},interactionSource=rememberHapticInteractionSource())
            }
            if(customStart) {
                SecondaryButton(LocalDate.ofEpochDay(startEpoch).format(dayFormat),{datePicker=true})
                GlassTextField(time,{time=it},"Start time (HH:mm)",Modifier.fillMaxWidth())
            }
            PrimaryButton("Start fast",{vm.run {vm.repo.startFast(hours.number()!!,if(customStart) start!! else System.currentTimeMillis())}},Modifier.fillMaxWidth(),
                enabled=!vm.busy && hours.number()?.let {it>0 && it<=168}==true && start!=null && start<=now)
        }
        val history=state.fasts.filter {it.end!=null}
        if(history.isNotEmpty()) ContentSection {
            Caption("History")
            val achieved=history.count {it.reached()}
            NutritionRow("Targets reached","$achieved / ${history.size}")
            NutritionRow("Average duration",durationText(history.map {it.elapsed()}.average().toLong()))
        }
        history.forEach {f ->ContentSection {
            Text(Instant.ofEpochMilli(f.start).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm")),style=LocalDeltaType.current.title)
            NutritionRow("Duration",durationText(f.elapsed()),"${amountText(f.targetHours)} hour target · ${if(f.reached()) "Reached" else "Ended early"}")
            Text("Ended ${Instant.ofEpochMilli(f.end!!).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))}",style=LocalDeltaType.current.bodySmall)
            TextAction("Delete fast",{deleting=f})
        } }
    }
    if(datePicker) NutritionDatePicker(LocalDate.ofEpochDay(startEpoch),{datePicker=false},{startEpoch=it.toEpochDay()})
    if(ending) AlertDialog(onDismissRequest={ending=false},title={Text("End this fast?")},text={Text("Your elapsed time will be saved in fasting history.")},
        confirmButton={HapticTextButton({ending=false;vm.run {vm.repo.finishFast()}}){Text("End fast")}},dismissButton={HapticTextButton({ending=false}){Text("Continue fast")}})
    deleting?.let {f ->AlertDialog(onDismissRequest={deleting=null},title={Text("Delete this fast?")},confirmButton={HapticTextButton({deleting=null;vm.run {vm.repo.deleteFast(f.id)}}){Text("Delete")}},dismissButton={HapticTextButton({deleting=null}){Text("Cancel")}})}
}
