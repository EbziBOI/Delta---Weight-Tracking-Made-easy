package io.github.ebziboi.delta.ui

import android.os.SystemClock
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.composed
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.*
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch

/** One gate covers stock press feedback and all custom gesture haptics. */
@Composable internal fun HapticTheme(enabled:Boolean,content:@Composable ()->Unit) {
    val platform=LocalHapticFeedback.current;val active by rememberUpdatedState(enabled)
    val feedback=remember(platform) {object:HapticFeedback {
        private var last=0L
        override fun performHapticFeedback(hapticFeedbackType:HapticFeedbackType) {
            val now=SystemClock.uptimeMillis()
            if(active && now-last>35) {last=now;platform.performHapticFeedback(hapticFeedbackType)}
        }
    }}
    val original=LocalIndication.current
    val indication=remember(original,feedback) {HapticIndication(original as? IndicationNodeFactory,feedback)}
    CompositionLocalProvider(LocalHapticFeedback provides feedback,LocalIndication provides indication,content=content)
}
private data class HapticIndication(val original:IndicationNodeFactory?,val feedback:HapticFeedback):IndicationNodeFactory {
    override fun create(interactionSource:InteractionSource):DelegatableNode=object:DelegatingNode() {
        init {original?.let {delegate(it.create(interactionSource))}}
        override fun onAttach() {coroutineScope.launch {interactionSource.interactions.collect {if(it is PressInteraction.Release) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)}}}
    }
}
@Composable internal fun rememberHapticInteractionSource():MutableInteractionSource {
    val source=remember {MutableInteractionSource()};val feedback=LocalHapticFeedback.current
    LaunchedEffect(source,feedback) {source.interactions.collect {event->
        if(event is PressInteraction.Release || event is DragInteraction.Start) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }}
    return source
}
@Composable fun HapticTextButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,content:@Composable androidx.compose.foundation.layout.RowScope.()->Unit) {
    TextButton(onClick,modifier,enabled=enabled,interactionSource=rememberHapticInteractionSource(),content=content)
}
@Composable fun HapticCheckbox(checked:Boolean,onCheckedChange:((Boolean)->Unit)?,modifier:Modifier=Modifier,enabled:Boolean=true) {
    Checkbox(checked,onCheckedChange,modifier,enabled=enabled,interactionSource=rememberHapticInteractionSource())
}
@Composable fun HapticIconButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,content:@Composable ()->Unit) {
    IconButton(onClick,modifier,enabled=enabled,interactionSource=rememberHapticInteractionSource(),content=content)
}

@Composable fun HapticButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,content:@Composable androidx.compose.foundation.layout.RowScope.()->Unit) {
    Button(onClick,modifier,enabled=enabled,interactionSource=rememberHapticInteractionSource(),content=content)
}
@Composable fun HapticDropdownMenuItem(text:@Composable ()->Unit,onClick:()->Unit,modifier:Modifier=Modifier) {
    DropdownMenuItem(text,onClick,modifier,interactionSource=rememberHapticInteractionSource())
}
@Composable fun HapticSlider(value:Float,onValueChange:(Float)->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    valueRange:ClosedFloatingPointRange<Float> = 0f..1f,steps:Int=0,onValueChangeFinished:(()->Unit)?=null,
    colors:SliderColors=SliderDefaults.colors(),interactionSource:MutableInteractionSource?=null) {
    val haptic=LocalHapticFeedback.current;var lastTick by remember {mutableIntStateOf(Int.MIN_VALUE)}
    Slider(value,{next->
        val range=(valueRange.endInclusive-valueRange.start).coerceAtLeast(.0001f)
        val tick=((next-valueRange.start)/range*(if(steps in 1..19) steps+1 else 20)).toInt()
        if(tick!=lastTick) {lastTick=tick;haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
        onValueChange(next)
    },modifier,enabled=enabled,valueRange=valueRange,steps=steps,onValueChangeFinished={lastTick=Int.MIN_VALUE;onValueChangeFinished?.invoke()},colors=colors,interactionSource=interactionSource ?: rememberHapticInteractionSource())
}

/** Observe successful stock-picker taps without intercepting scrolling or clicks. */
fun Modifier.hapticPickerTaps():Modifier=composed {
    val feedback=LocalHapticFeedback.current
    this.then(Modifier.pointerInput(feedback) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false,pass=androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            var moved=false
            do {
                val event=awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
                val change=event.changes.firstOrNull {it.id==down.id} ?: break
                if((change.position-down.position).getDistance()>viewConfiguration.touchSlop) moved=true
                if(!change.pressed && !moved && change.isConsumed) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } while(event.changes.any {it.pressed})
        }
    })
}
