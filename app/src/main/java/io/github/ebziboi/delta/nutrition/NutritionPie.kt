package io.github.ebziboi.delta.nutrition

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.ebziboi.delta.ui.*
import androidx.compose.ui.semantics.*

internal fun nutrientMass(value:Double,n:Nutrient):Double?=when(n.unit) {"g"->value;"mg"->value/1000;"µg"->value/1000000;else->null}
@Composable
internal fun NutritionPie(entries:List<DiaryEntry>) {
    val prefs=LocalContext.current.getSharedPreferences("nutrition-pie",0)
    var chosen by remember {mutableStateOf(prefs.getString("nutrients","PROTEIN,CARBS,FAT").orEmpty().split(",").mapNotNull {s->Nutrient.entries.find {it.name==s}}.toSet())}
    var mass by remember {mutableStateOf(prefs.getBoolean("mass",false))};var menu by remember {mutableStateOf(false)}
    val colours=macroColours()
    val list=Nutrient.entries.filter {it in chosen && (if(mass) nutrientMass(1.0,it)!=null else it in Nutrient.macros)};val totals=list.map {entries.total(it)}
    val weights=list.mapIndexed {i,n->if(totals[i].reported==0) 0.0 else if(mass) nutrientMass(totals[i].amount,n) ?: 0.0 else totals[i].amount*if(n==Nutrient.FAT) 9 else 4}
    val total=weights.sum()
    fun save() {prefs.edit().putString("nutrients",chosen.joinToString(",") {it.name}).putBoolean("mass",mass).apply()}
    ContentSection {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("Nutrient balance",style=LocalDeltaType.current.title,modifier=Modifier.weight(1f))
            GlassIconButton(io.github.ebziboi.delta.R.drawable.ic_tune,"Choose pie nutrients",{menu=true},size=44.dp)
        }
        SegmentedControl(listOf(false,true),mass,{if(it) "Nutrient mass" else "Macro energy"}) {mass=it;if(!it) chosen=chosen.intersect(Nutrient.macros.toSet()).ifEmpty {Nutrient.macros.toSet()};save()}
        val slices=list.mapIndexed {i,n->BreakdownSlice(n,weights[i],totals[i],colours[i%colours.size])}
        BreakdownDonut(slices,entries,if(mass) "${amountText(Math.round(total*10)/10.0)} g" else "${amountText(Math.round(total).toDouble())}",if(mass) "reported mass" else "kcal from macros",energyShares=!mass)
        Text(if(mass) "Shares of selected reported nutrient mass, converted to grams. Some nutrients overlap: sugars are part of carbohydrate." else "Energy calculated from protein and carbohydrate at 4 kcal/g, fat at 9 kcal/g. Missing labels leave this breakdown incomplete.",style=LocalDeltaType.current.bodySmall,color=Color(LocalDeltaColors.current.textDim))
    }
    if(menu) AlertDialog(onDismissRequest={menu=false},title={Text("Pie nutrients")},text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())) {
            Nutrient.entries.filter {nutrientMass(1.0,it)!=null}.forEach {n->
                Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    HapticCheckbox(n in chosen,{on->chosen=if(on) chosen+n else chosen-n;if(n !in Nutrient.macros && on) mass=true;save()})
                    Text(n.label,style=LocalDeltaType.current.body)
                }
            }
        }
    },confirmButton={HapticTextButton({menu=false}) {Text("Done")}})
}
