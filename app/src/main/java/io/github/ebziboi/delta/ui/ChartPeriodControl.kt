package io.github.ebziboi.delta.ui

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties


/** The initiating pointer stays captured: drag down from the dot and release over a choice. */
@Composable
internal fun ChartPeriodControl(selected:ChartPeriod,onChoose:(ChartPeriod)->Unit) {
    val claim=LocalCardGestureClaim.current;val gestureToken=remember {Any()}
    val c=LocalDeltaColors.current;val motion=LocalMotion.current;val haptic=LocalHapticFeedback.current
    var open by remember {mutableStateOf(false)};var hover by remember {mutableStateOf(selected)}
    val current by rememberUpdatedState(onChoose)
    val selectedNow by rememberUpdatedState(selected)
    val view=LocalView.current;val density=LocalDensity.current
    val navigationInset=WindowInsets.navigationBars.getBottom(density)
    val rowHeight=(48*density.fontScale.coerceAtLeast(1f)).dp
    val rowPx=with(density) {rowHeight.toPx()}
    var roomBelow by remember {mutableFloatStateOf(Float.MAX_VALUE)}
    var above by remember {mutableStateOf(false)}
    fun openMenu() {above=roomBelow<rowPx*3;hover=selectedNow;open=true}
    Box {
        Box(Modifier.size(48.dp).onGloballyPositioned {roomBelow=view.height-it.boundsInWindow().bottom-navigationInset}.semantics {
            contentDescription="Chart date view";stateDescription=selected.label
            customActions=ChartPeriod.entries.map {p->CustomAccessibilityAction(p.label) {current(p);true}}
        }.clickable {if(open) open=false else openMenu()}.pointerInput(rowPx) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                claim(gestureToken,true)
                try {
                val press=awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                openMenu();press.consume();haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                var chosen:ChartPeriod?=null
                do {
                    val event=awaitPointerEvent();val change=event.changes.firstOrNull {it.id==down.id} ?: break
                    val fromTop=change.position.y-(if(above) -3*rowPx else 48.dp.toPx())
                    val index=(fromTop/rowPx).toInt()
                    chosen=if(fromTop>=0 && fromTop<3*rowPx && change.position.x in -130.dp.toPx()..48.dp.toPx()) ChartPeriod.entries.getOrNull(index) else null
                    if(chosen!=null && chosen!=hover) {hover=chosen;current(chosen);haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
                    change.consume()
                    if(!change.pressed) break
                } while(true)
                chosen?.let(current);open=false
                } finally {claim(gestureToken,false)}
            }
        },contentAlignment=Alignment.Center) {Canvas(Modifier.size(7.dp)) {drawCircle(Color(c.accent))}}
        var entered by remember {mutableStateOf(false)}
        LaunchedEffect(open) {entered=open}
        val opening by animateFloatAsState(if(entered) 1f else 0f,tween(if(motion) 150 else 0),label="period-menu-open")
        if(open) Popup(alignment=Alignment.TopEnd,offset=androidx.compose.ui.unit.IntOffset(0,with(density) {if(above) -(rowHeight*3).roundToPx() else 48.dp.roundToPx()}),
            onDismissRequest={open=false},properties=PopupProperties(focusable=false,clippingEnabled=false)) {
            Column(Modifier.width(178.dp).graphicsLayer {alpha=opening;scaleX=.9f+.1f*opening;scaleY=.9f+.1f*opening;transformOrigin=TransformOrigin(1f,if(above) 1f else 0f)}.clip(RoundedCornerShape(Radii.control)).background(Color(c.surfaceHigh),RoundedCornerShape(Radii.control))) {
                ChartPeriod.entries.forEach {p->
                    val active=p==hover
                    val fill by animateColorAsState(if(active) Color(c.accent).copy(alpha=.22f) else Color.Transparent,tween(if(motion) 110 else 0),label="period-choice")
                    val scale by animateFloatAsState(if(active) 1f else .95f,tween(if(motion) 110 else 0),label="period-emphasis")
                    Row(Modifier.fillMaxWidth().height(rowHeight).background(fill).clickable {current(p);open=false}.padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(p.label,style=LocalDeltaType.current.bodyStrong,color=Color(if(active) c.accent else c.text))
                        if(p==selected) Canvas(Modifier.size((6*scale).dp)) {drawCircle(Color(c.accent))}
                    }
                }
            }
        }
    }
}
