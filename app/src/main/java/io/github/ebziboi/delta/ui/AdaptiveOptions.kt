package io.github.ebziboi.delta.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.sqrt

/** Balanced final rows avoid a lone small chip or unused right-hand gutter. */
fun <T> balancedOptionRows(options: List<T>, columns: Int): List<List<T>> {
    if(options.isEmpty()) return emptyList()
    var remainingRows=ceil(options.size.toDouble()/columns.coerceAtLeast(1)).toInt()
    var start=0
    val result=mutableListOf<List<T>>()
    while(start<options.size) {
        val count=ceil((options.size-start).toDouble()/remainingRows).toInt()
        result.add(options.subList(start,start+count));start+=count;remainingRows--
    }
    return result
}

@Composable
fun <T> AdaptiveOptionGrid(options: List<T>, modifier: Modifier=Modifier, minimumWidth: Dp=100.dp,
                           content: @Composable (T,Modifier)->Unit) {
    val fontScale=LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns=((maxWidth.value+8)/(minimumWidth.value*sqrt(fontScale.coerceAtLeast(1f))+8)).toInt().coerceIn(1,3)
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            balancedOptionRows(options,columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    row.forEach {content(it,Modifier.weight(1f).fillMaxHeight())}
                }
            }
        }
    }
}
