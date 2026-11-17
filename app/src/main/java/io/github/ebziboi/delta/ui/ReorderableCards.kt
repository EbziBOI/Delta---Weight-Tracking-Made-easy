package io.github.ebziboi.delta.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import io.github.ebziboi.delta.data.CardLayouts
import kotlin.math.abs

internal data class CardSlot(val id:String,val label:String,val compact:Boolean=false)
internal val LocalCardGestureClaim = staticCompositionLocalOf<(Any,Boolean)->Unit> {{_,_->}}
internal val LocalCardReordering = staticCompositionLocalOf<(Boolean)->Unit> {{}}

/** Long presses are confined to headings; chart and calendar gestures retain their own touch area. */
@Composable
internal fun ReorderableCards(
    cards:List<CardSlot>, savedOrder:List<String>, padding:PaddingValues,
    onOrderChange:(List<String>)->Unit, header:@Composable ()->Unit, content:@Composable (String)->Unit,
) {
    val grid=rememberLazyGridState()
    val gestureClaims=remember {mutableSetOf<Any>()}
    val currentContent by rememberUpdatedState(content)
    val currentHeader by rememberUpdatedState(header)
    val ids=cards.map {it.id}
    val proposed=CardLayouts.normalized(savedOrder,ids)
    var order by remember {mutableStateOf(proposed)}
    var dragging by remember {mutableStateOf<String?>(null)}
    var dragOrigin by remember {mutableStateOf(Offset.Zero)}
    var distance by remember {mutableStateOf(Offset.Zero)}
    var anchor by remember {mutableStateOf(Offset.Zero)}
    val slots=cards.associateBy {it.id}
    val visible=CardLayouts.normalized(order,ids)
    val motion=LocalMotion.current
    val liftedShape=RoundedCornerShape(Radii.card)
    val haptic=LocalHapticFeedback.current
    val notifyReordering by rememberUpdatedState(LocalCardReordering.current)
    val persist by rememberUpdatedState(onOrderChange)
    val headingHeight=with(LocalDensity.current) {48.dp.toPx()}
    val sidePadding=with(LocalDensity.current) {Spacing.screen.toPx()}
    val edge=with(LocalDensity.current) {96.dp.toPx()}
    val navigationPadding=with(LocalDensity.current) {padding.calculateBottomPadding().toPx()}
    val maxSpeed=with(LocalDensity.current) {680.dp.toPx()}

    LaunchedEffect(proposed) {
        if(dragging==null) order=proposed
        else {
            order=CardLayouts.normalized(order,ids)
            if(dragging !in ids) {dragging=null;notifyReordering(false)}
        }
    }
    fun persistMove(next:List<String>) {order=next;persist(next)}
    val target by rememberUpdatedState<(Offset)->Unit>({direction->
        val active=dragging
        if(active!=null) {
            val pointer=dragOrigin+distance+anchor
            val activeLayout=grid.layoutInfo.visibleItemsInfo.firstOrNull {it.key==active}
            // Wait for the previous move to lay out before accepting another crossing.
            if(activeLayout==null || activeLayout.index==order.indexOf(active)+1) {
                val hit=grid.layoutInfo.visibleItemsInfo.firstOrNull {item->
                    item.key.toString() in ids && pointer.x>=item.offset.x && pointer.x<item.offset.x+item.size.width &&
                        pointer.y>=item.offset.y && pointer.y<item.offset.y+item.size.height
                }
                if(hit!=null && hit.key!=active) {
                    val from=order.indexOf(active);val to=order.indexOf(hit.key.toString())
                    val axis=if(activeLayout!=null && hit.row==activeLayout.row) direction.x else direction.y
                    if(from>=0 && to>=0 && ((axis>0 && to>from) || (axis<0 && to<from))) {
                        order=order.toMutableList().apply {add(to,removeAt(from))}
                        if(to+1<=grid.firstVisibleItemIndex) grid.requestScrollToItem(to+1,0)
                    }
                }
            }
        }
    })
    val start by rememberUpdatedState<(String,Offset)->Boolean>({id,point->
        val item=grid.layoutInfo.visibleItemsInfo.firstOrNull {it.key==id}
        if(item==null || id !in ids) false else {
            order=visible;dragOrigin=Offset(item.offset.x.toFloat(),item.offset.y.toFloat())
            distance=Offset.Zero;anchor=point-dragOrigin;dragging=id
            haptic.performHapticFeedback(HapticFeedbackType.LongPress);notifyReordering(true);true
        }
    })
    val move by rememberUpdatedState<(Offset)->Unit>({delta->distance+=delta;target(delta)})
    val finish by rememberUpdatedState<(Boolean)->Unit>({cancelled->
        if(dragging!=null) {
            if(cancelled) order=proposed else if(order!=proposed) persist(order)
            dragging=null;notifyReordering(false)
        }
    })
    LaunchedEffect(dragging) {
        if(dragging==null) return@LaunchedEffect
        var previous=withFrameNanos {it}
        while(dragging!=null) {
            val now=withFrameNanos {it};val seconds=((now-previous)/1_000_000_000f).coerceAtMost(.05f);previous=now
            val pointer=(dragOrigin+distance+anchor).y-grid.layoutInfo.viewportStartOffset
            val top=0f
            val bottom=(grid.layoutInfo.viewportEndOffset-grid.layoutInfo.viewportStartOffset).toFloat()-navigationPadding
            val amount=when {
                pointer<top+edge -> -((top+edge-pointer)/edge).coerceIn(0f,1f)
                pointer>bottom-edge -> ((pointer-bottom+edge)/edge).coerceIn(0f,1f)
                else->0f
            }
            if(amount!=0f && grid.scrollBy(amount*maxSpeed*seconds)!=0f) target(Offset(0f,amount))
        }
    }
    DisposableEffect(Unit) {onDispose {notifyReordering(false)}}
    LazyVerticalGrid(
        columns=GridCells.Fixed(2),state=grid,
        modifier=Modifier.fillMaxSize().pointerInput(grid,headingHeight,sidePadding) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                if(gestureClaims.isNotEmpty()) return@awaitEachGesture
                val touch=down.position+Offset(-sidePadding,grid.layoutInfo.viewportStartOffset.toFloat())
                val item=grid.layoutInfo.visibleItemsInfo.firstOrNull {item->
                    item.index>0 && touch.x>=item.offset.x && touch.x<item.offset.x+item.size.width &&
                        touch.y>=item.offset.y && touch.y<item.offset.y+minOf(item.size.height.toFloat(),headingHeight)
                } ?: return@awaitEachGesture
                val press=awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                if(!start(item.key.toString(),touch)) return@awaitEachGesture
                press.consume()
                var completed=false
                try {completed=drag(press.id) {change->move(change.positionChange());change.consume()}}
                finally {finish(!completed)}
            }
        },
        contentPadding=PaddingValues(start=Spacing.screen,end=Spacing.screen,top=padding.calculateTopPadding(),bottom=padding.calculateBottomPadding()+8.dp),
        horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(16.dp),
        userScrollEnabled=dragging==null,
    ) {
        item(key="page-header",span={GridItemSpan(2)},contentType="header") {currentHeader()}
        items(visible,key={it},span={id->
            val index=visible.indexOf(id)
            if(slots.getValue(id).compact) {
                var first=index;while(first>0 && slots.getValue(visible[first-1]).compact) first--
                var last=index;while(last<visible.lastIndex && slots.getValue(visible[last+1]).compact) last++
                GridItemSpan(if((last-first+1)%2==1 && index==last) 2 else 1)
            } else GridItemSpan(2)
        },contentType={if(slots.getValue(it).compact) "compact-card" else "card"}) {id->
            val index=visible.indexOf(id)
            val active=dragging==id
            val position=grid.layoutInfo.visibleItemsInfo.firstOrNull {it.key==id}?.offset
            val desired=if(active && position!=null) dragOrigin+distance-Offset(position.x.toFloat(),position.y.toFloat()) else Offset.Zero
            val x by animateFloatAsState(desired.x,if(active || !motion) tween(0) else spring(stiffness=Spring.StiffnessMediumLow),label="card-x")
            val y by animateFloatAsState(desired.y,if(active || !motion) tween(0) else spring(stiffness=Spring.StiffnessMediumLow),label="card-y")
            val scale by animateFloatAsState(if(active && motion) 1.025f else 1f,tween(if(motion) 160 else 0),label="card-lift")
            val floating=active || abs(x)+abs(y)>1f
            Box(Modifier.fillMaxWidth().then(if(motion && !active) Modifier.animateItem() else Modifier)
                .zIndex(if(floating) 1f else 0f).graphicsLayer {translationX=x;translationY=y;scaleX=scale;scaleY=scale;shape=liftedShape;shadowElevation=if(floating) 8.dp.toPx() else 0f}
                .semantics {
                    contentDescription="${slots.getValue(id).label} card"
                    stateDescription="${if(active) "Moving, " else ""}position ${index+1} of ${visible.size}"
                    customActions=buildList {
                        if(index>0) add(CustomAccessibilityAction("Move earlier") {persistMove(visible.toMutableList().apply {add(index-1,removeAt(index))});true})
                        if(index<visible.lastIndex) add(CustomAccessibilityAction("Move later") {persistMove(visible.toMutableList().apply {add(index+1,removeAt(index))});true})
                    }
                }) {CompositionLocalProvider(LocalCardGestureClaim provides {token,active->if(active) gestureClaims.add(token) else gestureClaims.remove(token)}) {currentContent(id)}}
        }
    }
}
