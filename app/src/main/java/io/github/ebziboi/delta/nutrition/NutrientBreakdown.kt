package io.github.ebziboi.delta.nutrition

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ebziboi.delta.ui.*
import kotlin.math.atan2
import kotlin.math.hypot

/** One share of a breakdown donut. [weight] is the comparable amount (kcal or grams of mass). */
internal data class BreakdownSlice(val nutrient:Nutrient,val weight:Double,val total:NutrientTotal,val colour:Color)

@Composable internal fun macroColours():List<Color> {
    val c=LocalDeltaColors.current
    return listOf(Color(c.accent),Color(c.good),Color(c.warn),Color(0xFFB4A8FF),Color(0xFF6ABDD1),Color(0xFFD496BB))
}

/** Energy shares of protein, carbohydrate and fat, using 4/4/9 kcal per gram. */
@Composable internal fun macroEnergySlices(entries:List<DiaryEntry>):List<BreakdownSlice> {
    val colours=macroColours()
    return Nutrient.macros.mapIndexed {i,n->
        val t=entries.total(n)
        BreakdownSlice(n,if(t.reported==0) 0.0 else t.amount*if(n==Nutrient.FAT) 9 else 4,t,colours[i])
    }
}

/**
 * A donut whose segments can be tapped. Tapping a segment selects it and shows, beneath, which foods
 * supplied that nutrient; tapping it again (or the centre) clears the selection.
 */
@Composable internal fun BreakdownDonut(slices:List<BreakdownSlice>,entries:List<DiaryEntry>,centreValue:String,centreCaption:String,
                                        description:String="Nutrient balance pie chart",energyShares:Boolean=true,height:androidx.compose.ui.unit.Dp=196.dp) {
    val c=LocalDeltaColors.current;val haptic=LocalHapticFeedback.current;val motion=LocalMotion.current
    val total=slices.sumOf {it.weight}
    var selected by remember(slices.map {it.nutrient}) {mutableStateOf<Nutrient?>(null)}
    val reveal=remember {Animatable(if(motion) 0f else 1f)}
    LaunchedEffect(slices.map {it.weight},motion) {if(motion) {reveal.snapTo(.6f);reveal.animateTo(1f,tween(420,easing=FastOutSlowInEasing))} else reveal.snapTo(1f)}
    val emphasis=slices.associate {s->s.nutrient to animateFloatAsState(when(selected) {null->1f;s.nutrient->1f;else->.32f},tween(if(motion) 200 else 0),label="slice-${s.nutrient}").value}
    val grow=slices.associate {s->s.nutrient to animateFloatAsState(if(selected==s.nutrient) 1f else 0f,if(motion) spring(.7f,500f) else snap(),label="grow-${s.nutrient}").value}
    if(total<=0) {
        Text("No reported amounts yet",style=LocalDeltaType.current.bodySmall,color=Color(c.textDim))
        return
    }
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
    // The donut and its legend share the row only while both stay readable; otherwise the legend moves underneath.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale=androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
        val legendMin=(150*fontScale).dp
        val sideBySide=maxWidth>=height*.62f+16.dp+legendMin
        val donut=if(sideBySide) minOf(height*.82f,maxWidth-16.dp-legendMin) else minOf(height*.92f,maxWidth*.72f)
        val ring:@Composable ()->Unit={
            Box(Modifier.size(donut),contentAlignment=Alignment.Center) {
                Canvas(Modifier.fillMaxSize().semantics {
                    contentDescription=description
                    stateDescription=slices.joinToString {s->"${s.nutrient.label} ${amountText(s.weight/total*100)}%"}+(selected?.let {"; Selected ${it.label}"} ?: "")
                    customActions=slices.map {s->CustomAccessibilityAction("Show foods for ${s.nutrient.label}") {selected=s.nutrient;true}}
                }.pointerInput(slices.map {it.nutrient to it.weight}) {
                    detectTapGestures {tap->
                        val centre=Offset(size.width/2f,size.height/2f);val radius=minOf(size.width,size.height)/2f
                        val distance=hypot(tap.x-centre.x,tap.y-centre.y)
                        if(distance<radius*.42f || distance>radius*1.08f) {selected=null;return@detectTapGestures}
                        var angle=Math.toDegrees(atan2((tap.y-centre.y).toDouble(),(tap.x-centre.x).toDouble())).toFloat()+90f
                        if(angle<0) angle+=360f
                        var start=0f
                        val hit=slices.firstOrNull {s->val sweep=(s.weight/total*360).toFloat();val inside=angle>=start && angle<start+sweep;start+=sweep;inside && sweep>0}
                        selected=if(hit?.nutrient==selected) null else hit?.nutrient
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }) {
                    val diameter=minOf(size.width,size.height);val band=diameter*.17f
                    val inset=band/2+diameter*.04f
                    var angle=-90f
                    slices.forEach {s->
                        val sweep=(s.weight/total*360).toFloat()*reveal.value
                        if(sweep>0) {
                            val lift=(grow[s.nutrient] ?: 0f)*diameter*.035f
                            val gap=if(slices.count {it.weight>0}>1) 1.6f else 0f
                            drawArc(s.colour.copy(alpha=emphasis[s.nutrient] ?: 1f),angle+gap/2,(sweep-gap).coerceAtLeast(.5f),false,
                                Offset(inset-lift,inset-lift)+Offset((size.width-diameter)/2,(size.height-diameter)/2),Size(diameter-2*inset+2*lift,diameter-2*inset+2*lift),
                                style=Stroke(band+lift*1.4f,cap=StrokeCap.Butt))
                        }
                        angle+=(s.weight/total*360).toFloat()
                    }
                }
                val focus=slices.firstOrNull {it.nutrient==selected}
                // The hole is 58% of the diameter; text is sized to fit inside it rather than overflow the ring.
                val hole=donut*.58f
                Column(Modifier.width(hole*.86f),horizontalAlignment=Alignment.CenterHorizontally) {
                    AnimatedContent(focus,transitionSpec={fadeIn(tween(160)) togetherWith fadeOut(tween(120))},label="donut-centre") {f->
                        Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
                            val number=LocalDeltaType.current.number;val caption=LocalDeltaType.current.label
                            androidx.compose.foundation.text.BasicText(if(f==null) centreValue else "${amountText(Math.round(f.weight/total*1000)/10.0)}%",
                                style=number.copy(color=Color(c.text),textAlign=TextAlign.Center),maxLines=1,
                                autoSize=androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize=10.sp,maxFontSize=number.fontSize,stepSize=.5.sp))
                            androidx.compose.foundation.text.BasicText(if(f==null) centreCaption else f.nutrient.label.substringBefore(" ·").lowercase(),
                                style=caption.copy(color=Color(c.textDim),textAlign=TextAlign.Center),maxLines=1,
                                autoSize=androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize=8.sp,maxFontSize=caption.fontSize,stepSize=.5.sp))
                        }
                    }
                }
            }
        }
        val legend:@Composable (Modifier)->Unit={modifier->
            Column(modifier,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                slices.forEach {s->
                    val active=selected==s.nutrient
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.control-4.dp)).background(if(active) s.colour.copy(alpha=.14f) else Color.Transparent)
                        .clickable(role=Role.Button,onClickLabel="Show foods for ${s.nutrient.label}") {selected=if(active) null else s.nutrient;haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
                        .padding(horizontal=8.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(s.colour.copy(alpha=emphasis[s.nutrient] ?: 1f)))
                        Column(Modifier.weight(1f)) {
                            Text(s.nutrient.label,style=LocalDeltaType.current.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text(if(s.total.reported==0) "Unknown" else (if(s.total.partial) "≥ " else "")+nutrientText(s.total.amount,s.nutrient),style=LocalDeltaType.current.label,color=Color(c.textDim),maxLines=1)
                        }
                        Text(if(s.weight>0) "${amountText(Math.round(s.weight/total*1000)/10.0)}%" else "—",style=LocalDeltaType.current.bodySmall,color=s.colour,maxLines=1,softWrap=false)
                    }
                }
                if(selected==null) Text("Tap a segment for its foods",style=LocalDeltaType.current.label,color=Color(c.textDim),modifier=Modifier.padding(start=8.dp,top=2.dp))
            }
        }
        if(sideBySide) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            ring();legend(Modifier.weight(1f))
        } else Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            ring();legend(Modifier.fillMaxWidth())
        }
    }
        val focus=slices.firstOrNull {it.nutrient==selected}
        androidx.compose.animation.AnimatedVisibility(focus!=null) {
            var shown by remember {mutableStateOf(focus)}
            if(focus!=null) shown=focus
            shown?.let {s->FoodContributions(entries,s.nutrient,s.colour,energyShares && s.nutrient in Nutrient.macros)}
        }
    }
}

internal data class FoodShare(val name:String,val detail:String,val amount:Double,val share:Double)

/** Foods that supplied [nutrient], largest first; the long tail folds into "Other foods". */
internal fun foodShares(entries:List<DiaryEntry>,nutrient:Nutrient,limit:Int=7):List<FoodShare> {
    val groups=entries.filter {nutrient in it.food.per100}.groupBy {it.food.name.trim().lowercase()+"|"+it.food.brand.trim().lowercase()}
        .map {(_,rows)->val first=rows.first();Triple(first.food,rows,rows.sumOf {it.nutrients[nutrient] ?: 0.0})}
        .filter {it.third>0}.sortedByDescending {it.third}
    val total=groups.sumOf {it.third}
    if(total<=0) return emptyList()
    val shown=groups.take(if(groups.size>limit) limit-1 else limit).map {(food,rows,amount)->
        val portions=rows.size
        FoodShare(food.name,listOfNotNull(food.brand.takeIf {it.isNotBlank()},if(portions>1) "$portions portions" else "${amountText(rows.first().amount)} ${food.unit.symbol}").joinToString(" · "),amount,amount/total)
    }
    val rest=groups.drop(shown.size)
    return if(rest.isEmpty()) shown else shown+FoodShare("Other foods","${rest.size} foods",rest.sumOf {it.third},rest.sumOf {it.third}/total)
}

/** A horizontal bar chart of each food's contribution to [nutrient]. */
@Composable internal fun FoodContributions(entries:List<DiaryEntry>,nutrient:Nutrient,colour:Color,showEnergy:Boolean=false) {
    val c=LocalDeltaColors.current;val motion=LocalMotion.current
    val shares=remember(entries,nutrient) {foodShares(entries,nutrient)}
    val missing=entries.count {nutrient !in it.food.per100}
    val grow=remember(nutrient) {Animatable(if(motion) 0f else 1f)}
    LaunchedEffect(nutrient,shares.size) {if(motion) {grow.snapTo(0f);grow.animateTo(1f,tween(460,easing=FastOutSlowInEasing))}}
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.tile)).background(colour.copy(alpha=.07f)).padding(14.dp).semantics(mergeDescendants=false) {contentDescription="${nutrient.label} by food"},
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(colour))
            Text("${nutrient.label} by food",style=LocalDeltaType.current.bodyStrong,modifier=Modifier.weight(1f))
            Text(nutrientText(shares.sumOf {it.amount},nutrient),style=LocalDeltaType.current.bodySmall,color=colour)
        }
        if(shares.isEmpty()) Text("No logged food reports ${nutrient.label.lowercase()}.",style=LocalDeltaType.current.bodySmall,color=Color(c.textDim))
        shares.forEach {s->
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name,style=LocalDeltaType.current.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(s.detail,style=LocalDeltaType.current.label,color=Color(c.textDim),maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    Text(nutrientText(s.amount,nutrient)+(if(showEnergy) " · ${amountText(Math.round(s.amount*(if(nutrient==Nutrient.FAT) 9 else 4)).toDouble())} kcal" else ""),style=LocalDeltaType.current.label,color=Color(c.textDim))
                    Text("${Math.round(s.share*100)}%",style=LocalDeltaType.current.bodyStrong,color=colour,modifier=Modifier.widthIn(min=36.dp),textAlign=TextAlign.End)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(c.text).copy(alpha=.07f))) {
                    Box(Modifier.fillMaxWidth((s.share*grow.value).toFloat().coerceIn(0f,1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(colour))
                }
            }
        }
        if(missing>0) Text("$missing ${if(missing==1) "food doesn't" else "foods don't"} report ${nutrient.label.lowercase()}",style=LocalDeltaType.current.label,color=Color(c.textDim))
    }
}
