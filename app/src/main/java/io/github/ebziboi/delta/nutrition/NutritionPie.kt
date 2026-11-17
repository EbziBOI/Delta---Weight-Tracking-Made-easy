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
    val c=LocalDeltaColors.current;val colours=listOf(Color(c.accent),Color(c.good),Color(c.warn),Color(0xFFB4A8FF),Color(0xFF6ABDD1),Color(0xFFD496BB))
    val list=Nutrient.entries.filter {it in chosen && (if(mass) nutrientMass(1.0,it)!=null else it in Nutrient.macros)};val totals=list.map {entries.total(it)}
    val weights=list.mapIndexed {i,n->if(totals[i].reported==0) 0.0 else if(mass) nutrientMass(totals[i].amount,n) ?: 0.0 else totals[i].amount*if(n==Nutrient.FAT) 9 else 4}
    val total=weights.sum();val motion=LocalMotion.current;val reveal=remember {Animatable(if(motion) 0f else 1f)}
    LaunchedEffect(weights,motion) {if(motion) {reveal.snapTo(.65f);reveal.animateTo(1f,tween(380))} else reveal.snapTo(1f)}
    fun save() {prefs.edit().putString("nutrients",chosen.joinToString(",") {it.name}).putBoolean("mass",mass).apply()}
    ContentSection {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("Nutrient balance",style=LocalDeltaType.current.title,modifier=Modifier.weight(1f))
            GlassIconButton(io.github.ebziboi.delta.R.drawable.ic_add,"Choose pie nutrients",{menu=true},size=44.dp)
        }
        SegmentedControl(listOf(false,true),mass,{if(it) "Nutrient mass" else "Macro energy"}) {mass=it;if(!it) chosen=chosen.intersect(Nutrient.macros.toSet()).ifEmpty {Nutrient.macros.toSet()};save()}
        if(total>0) Canvas(Modifier.fillMaxWidth().height(190.dp).semantics {contentDescription="Nutrient balance pie chart"}) {
            val diameter=minOf(size.width,size.height);val origin=androidx.compose.ui.geometry.Offset((size.width-diameter)/2,0f);var angle=-90f
            weights.forEachIndexed {i,v->val sweep=(v/total*360).toFloat();if(sweep>0) drawArc(colours[i%colours.size],angle,sweep*reveal.value,true,origin,androidx.compose.ui.geometry.Size(diameter,diameter));angle+=sweep}
        } else Text("No positive reported amounts in this period")
        list.forEachIndexed {i,n->
            val t=totals[i];val percent=if(t.reported>0 && total>0) " · ${amountText(weights[i]/total*100)}%" else ""
            Text("${n.label} · ${if(t.reported==0) "Unknown" else (if(t.partial) "≥ " else "")+nutrientText(t.amount,n)}$percent",color=colours[i%colours.size],style=LocalDeltaType.current.body)
        }
        Text(if(mass) "Shares of selected reported nutrient mass, converted to grams. Some nutrients overlap: sugars are part of carbohydrate." else "Energy calculated from protein and carbohydrate at 4 kcal/g, fat at 9 kcal/g. Missing labels leave this breakdown incomplete.",style=LocalDeltaType.current.bodySmall)
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
