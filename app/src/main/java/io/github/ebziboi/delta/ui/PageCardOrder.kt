package io.github.ebziboi.delta.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import io.github.ebziboi.delta.data.CardLayouts

@Composable
internal fun rememberPageCardOrder(page:String,defaults:List<String>):Pair<List<String>,(List<String>)->Unit> {
    val prefs=LocalContext.current.getSharedPreferences("detail-card-order",0)
    var order by remember(page) {mutableStateOf(CardLayouts.normalized(prefs.getString(page,"").orEmpty().split(","),defaults))}
    return order to {next->order=CardLayouts.normalized(next,defaults);prefs.edit().putString(page,order.joinToString(",")).apply()}
}
