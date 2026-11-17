package io.github.ebziboi.delta.ui

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*

/** The track clips its normal selection; the held thumb is a separate raised layer. */
@Composable fun <T> SegmentedControl(options:List<T>,selected:T,label:(T)->String,modifier:Modifier=Modifier,onSelect:(T)->Unit) {
    val claim=LocalCardGestureClaim.current;val gestureToken=remember {Any()}
    if(options.isEmpty()) return
    val c=LocalDeltaColors.current;val type=LocalDeltaType.current;val haptic=LocalHapticFeedback.current
    val motion=LocalMotion.current;val dashboard=LocalAppearance.current.designStyle==io.github.ebziboi.delta.data.DesignStyle.TUBIK
    val corner=if(dashboard) Radii.control.coerceAtMost(10.dp) else Radii.control
    val shape=RoundedCornerShape(corner);val thumbShape=RoundedCornerShape((corner-3.dp).coerceAtLeast(0.dp))
    var held by remember {mutableStateOf(false)};var heldIndex by remember {mutableIntStateOf(options.indexOf(selected).coerceAtLeast(0))};var width by remember {mutableIntStateOf(1)}
    val currentOptions by rememberUpdatedState(options);val choose by rememberUpdatedState(onSelect)
    val selectedIndex=options.indexOf(selected).coerceAtLeast(0)
    val position by animateFloatAsState((if(held) heldIndex else selectedIndex).toFloat(),if(motion) spring(.82f,650f) else snap(),label="selector-position")
    val lift by animateFloatAsState(if(held) 1f else 0f,tween(if(motion) 130 else 0),label="selector-held")
    val fill=if(dashboard) Color(c.text).copy(alpha=.14f) else Color(c.accent)
    Box(modifier.fillMaxWidth().heightIn(min=44.dp).onSizeChanged {width=it.width}.semantics {stateDescription=if(held) "Held ${label(currentOptions[heldIndex.coerceIn(currentOptions.indices)])}" else label(selected)}
        .pointerInput(Unit) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                claim(gestureToken,true)
                try {
                val press=awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                held=true;heldIndex=(press.position.x/width*currentOptions.size).toInt().coerceIn(currentOptions.indices)
                choose(currentOptions[heldIndex]);haptic.performHapticFeedback(HapticFeedbackType.LongPress);press.consume()
                try {
                    do {
                        val event=awaitPointerEvent();val change=event.changes.firstOrNull {it.id==down.id} ?: break
                        val next=(change.position.x/width*currentOptions.size).toInt().coerceIn(currentOptions.indices)
                        if(next!=heldIndex) {heldIndex=next;choose(currentOptions[next]);haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
                        change.consume();if(!change.pressed) break
                    } while(true)
                } finally {held=false}
                } finally {claim(gestureToken,false)}
            }
        },contentAlignment=Alignment.Center) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).refractiveGlass(shape).padding(3.dp)) {
            options.forEachIndexed {i,option->
                val active=i==(if(held) heldIndex else selectedIndex)
                Box(Modifier.weight(1f).clip(thumbShape).clickable(role=Role.Tab) {choose(option)}.semantics {this.selected=active;if(option is io.github.ebziboi.delta.data.DesignStyle) contentDescription="${option.label} design"}
                    .padding(horizontal=5.dp,vertical=4.dp),contentAlignment=Alignment.Center) {
                    androidx.compose.foundation.text.BasicText(label(option),style=type.bodySmall.copy(fontWeight=type.bodyStrong.fontWeight,color=Color.Transparent),maxLines=1,
                        autoSize=androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize=10.sp,maxFontSize=type.bodySmall.fontSize,stepSize=.5.sp))
                }
            }
        }
        // Background-only overlay stays behind text, but may grow beyond the visual track when held.
        BoxWithConstraints(Modifier.matchParentSize(),contentAlignment=Alignment.CenterStart) {
            val segment=(maxWidth-6.dp)/options.size
            Box(Modifier.offset(x=3.dp+segment*position).width(segment).height((type.bodySmall.lineHeight.value*androidx.compose.ui.platform.LocalDensity.current.fontScale+8).coerceAtLeast(24f).dp)
                .graphicsLayer {scaleX=1f+.06f*lift;scaleY=1f+.42f*lift;translationY=-2.dp.toPx()*lift;shadowElevation=10.dp.toPx()*lift;this.shape=thumbShape}
                .background(fill,thumbShape))
        }
        // Labels are redrawn above the moving background without adding another interactive layer.
        Row(Modifier.fillMaxWidth().padding(3.dp)) {options.forEachIndexed {i,option->
            val active=i==(if(held) heldIndex else selectedIndex)
            val ink by animateColorAsState(Color(if(active) {if(dashboard) c.accent else c.onAccent} else c.textDim),tween(if(motion) 120 else 0),label="selector-ink")
            Box(Modifier.weight(1f).padding(horizontal=5.dp,vertical=4.dp).clearAndSetSemantics {},contentAlignment=Alignment.Center) {
                androidx.compose.foundation.text.BasicText(label(option),style=type.bodySmall.copy(fontWeight=type.bodyStrong.fontWeight,color=ink),maxLines=1,
                    autoSize=androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize=10.sp,maxFontSize=type.bodySmall.fontSize,stepSize=.5.sp))
            }
        }}
    }
}
