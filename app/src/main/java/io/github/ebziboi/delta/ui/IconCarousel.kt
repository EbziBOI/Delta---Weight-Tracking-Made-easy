package io.github.ebziboi.delta.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.ebziboi.delta.icons.LauncherIcons
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun IconCarousel(selected: String, onApply: (String) -> Unit) {
    val context = LocalContext.current
    val icons = LauncherIcons.all
    val pager = rememberPagerState(initialPage = icons.indexOfFirst { it.id == selected }.coerceAtLeast(0), pageCount = { icons.size })
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val c = LocalDeltaColors.current
    fun apply(index: Int) { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onApply(icons[index].id) }
    ContentSection {
        Caption("App icon")
        Text("A mark of your own", style = LocalDeltaType.current.title)
        HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 54.dp), pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth().height(190.dp).semantics { contentDescription = "App icon carousel" }) { index ->
            val icon = icons[index]
            val offset = abs(pager.currentPage - index + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = 1 - offset * 0.16f; scaleY = scaleX; alpha = 1 - offset * 0.4f }
                .clip(RoundedCornerShape(32.dp)).background(Color(c.surfaceHigh))
                .combinedClickable(onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    onLongClick = { apply(index) }, onDoubleClick = { apply(index) })
                .semantics { contentDescription = "${icon.title} icon"; this.selected = selected == icon.id }, contentAlignment = Alignment.Center) {
                val artwork = remember(icon.art) { androidx.core.content.ContextCompat.getDrawable(context, icon.art)!!.toBitmap(256, 256).asImageBitmap() }
                Image(artwork, contentDescription = null, modifier = Modifier.size(154.dp).clip(RoundedCornerShape(30.dp)))
            }
        }
        val current = icons[pager.currentPage]
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(current.title, style = LocalDeltaType.current.bodyStrong)
            Text("${pager.currentPage + 1} / ${icons.size}", style = LocalDeltaType.current.label, color = Color(c.textDim))
        }
        AccentButton(if (current.id == selected) "Current app icon" else "Use ${current.title}", { apply(pager.currentPage) },
            enabled = current.id != selected, modifier = Modifier.fillMaxWidth())
    }
}
