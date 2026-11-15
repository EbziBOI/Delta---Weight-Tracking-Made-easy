package io.github.ebziboi.delta.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ebziboi.delta.data.*

@Composable
fun ComplicationControls(vm: AppViewModel,settings: AppSettings) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val art=settings.progressDisplay in listOf(ProgressDisplay.COMPLICATION,ProgressDisplay.BOTH)
    ContentSection {
        Caption("Progress card")
        SegmentedControl(ProgressDisplay.entries,settings.progressDisplay,{when(it) {
            ProgressDisplay.COMPLICATION->"Art";ProgressDisplay.BAR->"Bar";ProgressDisplay.PERIMETER->"Perimeter";ProgressDisplay.BOTH->"Both"
        }}) {choice->vm.update {it.copy(progressDisplay=choice)}}
    }
    if(snap?.fraction!=null) {
        val s=snap!!
        HeroCard(s,InsightsCalculator.compute(entries,s),settings.unit,Modifier,settings)
    } else ContentSection(Modifier.cardPerimeter(settings.progressDisplay==ProgressDisplay.PERIMETER,.64f),framed=true) {
        Caption("Preview")
        if(art) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {PercentageComplication(.64f,settings.percentageDesign,Modifier.size(120.dp))}
        else Text("64% of goal",style=LocalDeltaType.current.numberLarge)
        if(settings.progressDisplay in listOf(ProgressDisplay.BAR,ProgressDisplay.BOTH)) ProgressRail(.64f,barStyle=settings.progressBarStyle)
    }
    if(settings.progressDisplay in listOf(ProgressDisplay.BAR,ProgressDisplay.BOTH)) ContentSection {
        Caption("Bar style")
        SegmentedControl(ProgressBarStyle.entries,settings.progressBarStyle,{it.label}) {choice->vm.update {it.copy(progressBarStyle=choice)}}
    }
    if(art) ContentSection {
        Caption("Percentage design")
        val colors=LocalDeltaColors.current
        AdaptiveOptionGrid(PercentageDesign.entries,minimumWidth=88.dp) {design,cell->
            Column(cell.clip(RoundedCornerShape(Radii.control)).background(Color(colors.surfaceHigh).copy(alpha=.55f))
                .clickable(role=Role.RadioButton) {vm.update {it.copy(percentageDesign=design)}}
                .semantics {selected=settings.percentageDesign==design;contentDescription="${design.label} percentage design"}
                .padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                PercentageComplication(.64f,design,Modifier.size(72.dp))
                Text(design.label,style=LocalDeltaType.current.bodySmall,color=Color(if(settings.percentageDesign==design) colors.accent else colors.text))
            }
        }
    }
}
