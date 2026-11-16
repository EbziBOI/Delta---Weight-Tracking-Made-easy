package io.github.ebziboi.delta.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.nutrition.NutritionState
import io.github.ebziboi.delta.render.*

@Composable
internal fun FoodWidgetSettings(settings:AppSettings,state:NutritionState,onPin:(FoodWidgetKind)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    FoodWidgetKind.entries.forEach {kind->
        ContentSection {
            Text(kind.title,style=LocalDeltaType.current.title)
            Text(kind.description,style=LocalDeltaType.current.bodySmall)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val scale=minOf(1f,maxWidth.value/kind.width)
                val image=remember(kind,state,settings) {FoodWidgetBitmaps.render(context,kind,kind.width,kind.height,state,settings)}
                Image(image.asImageBitmap(),"${kind.title} preview",Modifier.size((kind.width*scale).dp,(kind.height*scale).dp))
            }
            if(android.appwidget.AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported) SecondaryButton("Add",{onPin(kind)})
        }
    }
}
