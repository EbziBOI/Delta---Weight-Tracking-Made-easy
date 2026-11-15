package io.github.ebziboi.delta.render

import android.content.Context
import android.graphics.*
import android.os.Build
import io.github.ebziboi.delta.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

enum class WidgetKind(val title: String, val detail: String) {
    BODY_FAT("Body fat", "Your body fat percentage, change and trend"),
    PROGRESS("Progress", "The delta, filling as you close the gap"),
    JOURNEY("Journey", "Your graph and progress bar together"),
    SUMMARY("Start, now, target", "Three numbers over a progress bar"),
    TREND("Trend", "Your weight graph with the target line"),
    PACE("Pace", "Weekly rate, projected date and streak"),
}

/** The same renderer supplies home-screen widgets, in-app previews and picker images. */
object WidgetBitmaps {
    const val DEFAULT_MAX_SIDE_PX = 1000f
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun render(
        context: Context, kind: WidgetKind, widthDp: Float, heightDp: Float,
        snap: ProgressSnapshot, settings: AppSettings,
        night: Boolean = Palettes.isNight(context), maxSidePx: Float = DEFAULT_MAX_SIDE_PX,
        audit: MutableList<WidgetElement>? = null,
    ): Bitmap {
        require(widthDp.isFinite() && widthDp > 0 && heightDp.isFinite() && heightDp > 0)
        require(maxSidePx.isFinite() && maxSidePx >= 1)
        val scale = min(context.resources.displayMetrics.density, maxSidePx / max(widthDp, heightDp))
        val bitmap = Bitmap.createBitmap(max(1, (widthDp * scale).roundToInt()), max(1, (heightDp * scale).roundToInt()), Bitmap.Config.ARGB_8888)
        val theme = AppearanceEngine.forWidget(context, settings, night, kind)
        val palette = theme.drawColors()
        val readable = if (settings.widgetGlass == WidgetGlass.CLEAR) palette.copy(textDim = palette.text, textFaint = palette.text) else palette
        // A widget may ask for light or dark text whatever its background; automatic keeps the contrast-derived colours.
        val colors = when (settings.widgetContent.text) {
            TextContrast.AUTO -> readable
            TextContrast.LIGHT -> readable.copy(text = Color.WHITE, textDim = 0xFFDCDDE5.toInt(), textFaint = 0xFFB6B8C4.toInt())
            TextContrast.DARK -> readable.copy(text = 0xFF0E0F14.toInt(), textDim = 0xFF383A46.toInt(), textFaint = 0xFF5A5D6A.toInt())
        }
        val type = DeltaFonts.canvas(context, settings.typeStyle)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        GlassWidgetSurface.draw(canvas, widthDp, heightDp, theme, settings, WidgetCorners.radius(context, theme.appearance.cornerRadius))

        val painter = Painter(canvas, widthDp, heightDp, snap, settings, colors, type, audit)
        when (kind) {
            WidgetKind.BODY_FAT -> painter.bodyFat()
            WidgetKind.PROGRESS -> painter.progress()
            WidgetKind.JOURNEY -> painter.trend(journey = true)
            WidgetKind.SUMMARY -> painter.summary()
            WidgetKind.TREND -> painter.trend(journey = false)
            WidgetKind.PACE -> painter.pace()
        }
        return bitmap
    }

    private class Painter(
        val c: Canvas, val w: Float, val h: Float, val snap: ProgressSnapshot,
        val settings: AppSettings, val colors: DrawColors, val type: CanvasType,
        val audit: MutableList<WidgetElement>?,
    ) {
        val pad = (min(w,h)*.08f).coerceIn(10f,18f)
        val unit get() = settings.unit
        val show get() = settings.widgetContent
        val data = WidgetData.from(snap)
        val inner get() = w-pad*2
        val compact get() = h < 105f
        fun rect(left: Float, top: Float, right: Float, bottom: Float) = RectF(left,top,right,bottom)
        fun line(role: String, value: String, area: RectF, size: Float = 12f, color: Int = colors.text,
                 face: Typeface = type.number, align: Paint.Align = Paint.Align.LEFT, secondary: Boolean = false) {
            if(area.width()<8 || area.height()<8) return
            val ink=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                textSize=size;this.color=color;typeface=face;textAlign=align;fontFeatureSettings="tnum"
            }
            val glyph=Rect()
            ink.getTextBounds(value,0,value.length,glyph)
            ink.textSize *= min(1f,area.height()/glyph.height().coerceAtLeast(1))
            val floor=if(secondary) 9f else 10f
            if(ink.measureText(value)>area.width()) {
                var low=min(floor,ink.textSize)
                var high=ink.textSize
                repeat(12) {
                    val candidate=(low+high)/2
                    ink.textSize=candidate
                    if(ink.measureText(value)<=area.width()-.5f) low=candidate else high=candidate
                }
                ink.textSize=low
            }
            var drawn=value
            if(ink.measureText(drawn)>area.width()+.1f) {
                // Drop optional prose before making its type smaller than the readable floor.
                while(drawn.isNotEmpty() && ink.measureText(drawn+"…")>area.width()) drawn=drawn.dropLast(1)
                drawn += "…"
            }
            ink.getTextBounds(drawn,0,drawn.length,glyph)
            val baseline=area.centerY()-(glyph.top+glyph.bottom)/2f
            val x=when(align) { Paint.Align.CENTER->area.centerX();Paint.Align.RIGHT->area.right;else->area.left }
            val width=ink.measureText(drawn)
            val left=when(align) { Paint.Align.CENTER->x-width/2;Paint.Align.RIGHT->x-width;else->x }
            audit?.add(WidgetElement(role,RectF(left+glyph.left,baseline+glyph.top,left+glyph.right,baseline+glyph.bottom),drawn,ink.textSize))
            if(settings.widgetGlass!=WidgetGlass.SOLID && (settings.appearance?.designStyle!=DesignStyle.TUBIK || settings.appearance.backgroundStyle==BackgroundStyle.IMAGE)) {
                ink.setShadowLayer(1.5f,0f,.7f,ThemeContrast.on(color))
            }
            c.drawText(drawn,x,baseline,ink)
        }
        fun label(role:String,value:String,area:RectF,align:Paint.Align=Paint.Align.LEFT) =
            line(role,value,area,9f,colors.textDim,type.label,align,true)
        fun value() = snap.currentKg?.let { Units.weightShort(it,unit) } ?: "—"
        fun remaining() = when {
            snap.reached -> "Target reached"
            snap.remainingKg != null -> "${Units.amount(snap.remainingKg,unit)} to go"
            snap.targetKg == null -> "No target"
            else -> "No weigh-ins"
        }
        fun date(time:Long?) = time?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(shortDate) } ?: "No readings"
        fun tint(change:Double) = when { abs(change)<.05 -> colors.textDim; InsightsCalculator.towardGoal(change,snap.losing)->colors.good;else->colors.warn }
        fun week() = data.weekChangeKg?.let { "${Units.signed(it,unit)} this week" }
        fun goal(compactDate:Boolean=false) = when {
            snap.reached -> "Reached"
            snap.projectedDay != null -> snap.projectedDay.format(DateTimeFormatter.ofPattern(if(compactDate) "d MMM ’yy" else "d MMM yyyy",Locale.ENGLISH))
            else -> "—"
        }
        fun bar(area:RectF) {
            if(area.width()<1 || area.height()<1) return
            audit?.add(WidgetElement("bar",RectF(area)))
            val ink=Paint(Paint.ANTI_ALIAS_FLAG)
            if(settings.appearance?.designStyle==DesignStyle.TUBIK) {
                val count=(area.width()/8f).toInt().coerceIn(8,32);val gap=2f
                val cell=(area.width()-(count-1)*gap)/count
                repeat(count) { i ->
                    ink.color=if((i+.5f)/count <= (snap.fraction ?: 0f)) colors.accent else Palettes.fade(colors.text,.12f)
                    val x=area.left+i*(cell+gap);c.drawRect(x,area.top,x+cell,area.bottom,ink)
                }
            } else {
                ink.color=Palettes.fade(colors.text,.13f);c.drawRoundRect(area,area.height()/2,area.height()/2,ink)
                val filled=RectF(area).apply { right=left+width()*(snap.fraction ?: 0f).coerceIn(0f,1f) }
                if(filled.width()>0) { ink.color=colors.accent;c.drawRoundRect(filled,area.height()/2,area.height()/2,ink) }
            }
        }
        fun mark(area:RectF) {
            if(area.width()<40f) return
            audit?.add(WidgetElement("complication",RectF(area)))
            val size=min(area.width(),area.height())
            if(settings.percentageDesign!=PercentageDesign.ADAPTIVE) {
                PercentageArt.draw(c,area,snap.fraction,settings.percentageDesign,colors,type,settings.appearance?.designStyle==DesignStyle.TUBIK)
            } else if(settings.appearance?.designStyle==DesignStyle.TUBIK) {
                val ring=RectF(area).apply { inset(size*.12f,size*.12f) }
                val ink=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE;strokeWidth=size*.045f;strokeCap=Paint.Cap.ROUND }
                ink.color=Palettes.fade(colors.text,.12f);c.drawOval(ring,ink)
                ink.color=colors.accent;c.drawArc(ring,-90f,(snap.fraction ?: 0f)*360,false,ink)
                // The dial owns this region; its internal label is intentionally part of the art.
                val saved=audit?.size
                line("dial-label",snap.fraction?.let { "${(it*100).roundToInt()}%" } ?: "—",
                    RectF(area.centerX()-size*.3f,area.centerY()-size*.13f,area.centerX()+size*.3f,area.centerY()+size*.13f),size*.22f,face=type.numberBold,align=Paint.Align.CENTER)
                if(saved!=null) while(audit!!.size>saved) audit.removeAt(audit.lastIndex)
            } else {
                DeltaMark.draw(c,area.left,area.top,size,snap.fraction ?: 0f,colors,glyph=snap.fraction==null)
                snap.fraction?.let { DeltaMark.drawLabel(c,area.left,area.top,size,"${(it*100).roundToInt()}","%",colors.text,type.numberBold,
                    shadowColor=if(settings.widgetGlass==WidgetGlass.SOLID) null else ThemeContrast.on(colors.text)) }
            }
        }
        fun chart(area:RectF,spec:ChartSpec,axes:Boolean) {
            if(area.height()<22f || area.width()<35f) return
            if(spec.points.size<2) {
                line("chart-empty",if(spec.points.isEmpty()) "No readings" else "One reading",area,11f,colors.textDim,type.body,Paint.Align.CENTER,true)
                return
            }
            audit?.add(WidgetElement("chart",RectF(area)))
            TrendChart.draw(c,area,spec,colors,type,1f,axes,contrastShadow=settings.widgetGlass!=WidgetGlass.SOLID)
        }
        fun progress() {
            if(compact || (w<260f && h<145f)) {
                val split=w-pad-inner*.30f
                label("title","NOW",rect(pad,pad,split-8,pad+12))
                line("weight",value(),rect(pad,pad+14,split-8,h-pad-(if(h>=100 && show.showDetail) 20 else 0)),23f,face=type.numberBold)
                line("percent",snap.fraction?.let { "${(it*100).roundToInt()}%" } ?: "—",rect(split,pad,w-pad,h-pad-(if(h>=100 && show.showDetail) 20 else 0)),25f,colors.accent,type.numberBold,Paint.Align.RIGHT)
                if(h>=100 && show.showDetail) line("remaining",remaining(),rect(pad,h-pad-16,w-pad,h-pad),11f,colors.textDim,type.body,secondary=true)
                return
            }
            if(w>=260f && w>=h*1.35f) {
                val size=min(h-pad*2,inner*.36f).coerceAtMost(144f)
                mark(rect(w-pad-size,(h-size)/2,w-pad,(h+size)/2))
                val end=w-pad-size-14
                label("title","NOW",rect(pad,pad,end,pad+13))
                line("weight",value(),rect(pad,pad+19,end,pad+52),30f,face=type.numberBold)
                if(show.showDetail) line("remaining",remaining(),rect(pad,pad+57,end,pad+75),12f,colors.textDim,type.body,secondary=true)
                if(h>=145) line("date",date(snap.currentMs),rect(pad,h-pad-15,end,h-pad),10f,colors.textDim,type.body,secondary=true)
            } else {
                val valueH=30f
                label("title","NOW",rect(pad,pad,w-pad,pad+12))
                line("weight",value(),rect(pad,pad+14,w-pad,pad+14+valueH),24f,face=type.numberBold)
                val bottom=h-pad-(if(show.showDetail) 19f else 0f)
                val size=min(inner*.73f,bottom-(pad+valueH+20f)).coerceAtLeast(0f)
                mark(rect((w-size)/2,pad+valueH+20,(w+size)/2,pad+valueH+20+size))
                if(show.showDetail) line("remaining",remaining(),rect(pad,h-pad-16,w-pad,h-pad),11f,colors.textDim,type.body,Paint.Align.CENTER,true)
            }
        }
        fun summary() {
            if(h<78f && w>=240f) {
                val values=listOf(snap.startKg,snap.currentKg,snap.targetKg)
                val cell=inner/3
                listOf("START","NOW","TARGET").forEachIndexed { i,title ->
                    val x=pad+i*cell
                    label("label-$i",title,rect(x,pad,x+cell-6,pad+10))
                    line("value-$i",values[i]?.let { Units.weightShort(it,unit) } ?: "—",rect(x,pad+12,x+cell-6,h-pad),17f,
                        if(i==1) colors.accent else colors.text,type.numberBold)
                }
                return
            }
            if(w<145f && h<100f) {
                label("title","NOW",rect(pad,pad,w-pad,pad+11))
                line("weight",value(),rect(pad,pad+12,w-pad,h-pad),22f,face=type.numberBold)
                return
            }
            val values=listOf(snap.startKg,snap.currentKg,snap.targetKg)
            val titles=listOf("START","NOW","TARGET")
            if(w<145f) {
                val row=(h-pad*2)/3
                titles.forEachIndexed { i,title ->
                    val y=pad+i*row
                    label("label-$i",title,rect(pad,y,w-pad,y+10))
                    line("value-$i",values[i]?.let { Units.weightShort(it,unit) } ?: "—",rect(pad,y+12,w-pad,y+row-2),22f,
                        if(i==1) colors.accent else colors.text,type.numberBold)
                }
                return
            }
            val extra=show.showDetail && h>=100f
            val barTop=h-pad-(if(extra) 27f else 6f)
            if(w>=260f && w>=h*2.3f) {
                val cell=inner/3
                val center=(pad+barTop)/2
                titles.forEachIndexed { i,title ->
                    val left=pad+i*cell
                    label("label-$i",title,rect(left,center-21,left+cell-8,center-8))
                    line("value-$i",values[i]?.let { Units.weightShort(it,unit) } ?: "—",rect(left,center-5,left+cell-8,center+23),if(i==1) 25f else 21f,
                        if(i==1) colors.accent else colors.text,type.numberBold)
                }
            } else {
                val row=(barTop-pad-6)/3
                titles.forEachIndexed { i,title ->
                    val top=pad+i*row
                    label("label-$i",title,rect(pad,top,pad+inner*.30f,top+row-2))
                    line("value-$i",values[i]?.let { Units.weightShort(it,unit) } ?: "—",rect(pad+inner*.34f,top,w-pad,top+row-2),if(row>40) 30f else 18f,
                        if(i==1) colors.accent else colors.text,type.numberBold,Paint.Align.RIGHT)
                }
            }
            bar(rect(pad,barTop,w-pad,barTop+5))
            if(extra) line("remaining",remaining(),rect(pad,h-pad-16,w-pad,h-pad),11f,colors.textDim,type.body,secondary=true)
        }
        fun trend(journey:Boolean) {
            val title=if(journey) "JOURNEY" else "WEIGHT"
            if(h<135f && w>=260f) {
                val leftEnd=pad+inner*.41f
                label("title",title,rect(pad,pad,leftEnd,pad+11))
                line("weight",value(),rect(pad,pad+14,leftEnd,min(h-pad,pad+42)),23f,face=type.numberBold)
                val graphLeft=pad+inner*.47f
                val barSpace=if(journey && show.showBar && h>=70) 14 else 0
                val graphTop=if(h<70) pad else pad+20
                if(h>=70) label("date",date(snap.currentMs),rect(graphLeft,pad,w-pad,pad+12),Paint.Align.RIGHT)
                chart(rect(graphLeft,graphTop,w-pad,h-pad-barSpace),ChartSpec.forWidget(snap,settings),false)
                if(journey && show.showBar && h>=70) bar(rect(pad,h-pad-5,w-pad,h-pad))
                if(h>=100 && journey && show.showDetail) line("remaining",remaining(),rect(pad,pad+50,leftEnd,pad+67),11f,colors.textDim,type.body,secondary=true)
                else if(h>=90 && !journey) label("range",settings.widgetTimeRange.label,rect(pad,h-pad-12,leftEnd,h-pad))
                return
            }
            val titleEnd=if(w>=260f) w-pad-inner*.34f else w-pad
            label("title",title+(if(w in 145f..259f) " · ${date(snap.currentMs)}" else ""),rect(pad,pad,titleEnd,pad+11))
            line("weight",value(),rect(pad,pad+13,titleEnd,min(h-pad,pad+38)),23f,face=type.numberBold)
            if(w>=260f) label("date",date(snap.currentMs),rect(titleEnd+8,pad,w-pad,pad+13),Paint.Align.RIGHT)
            if(h<80) return
            val short=h<150f
            val footer=if(journey && show.showBar) (if(show.showDetail && !short) 30f else 13f) else if(short) 0f else 15f
            chart(rect(pad,pad+(if(short) 42 else 47),w-pad,h-pad-footer),ChartSpec.forWidget(snap,settings),axes=w>=260f && h>=170)
            if(journey && show.showBar) bar(rect(pad,h-pad-(if(show.showDetail && !short) 23 else 5),w-pad,h-pad-(if(show.showDetail && !short) 18 else 0)))
            if(journey && show.showDetail && !short) line("remaining",remaining(),rect(pad,h-pad-15,w-pad,h-pad),11f,colors.textDim,type.body,secondary=true)
            else if(!short) label("range",settings.widgetTimeRange.label,rect(pad,h-pad-12,w-pad,h-pad))
        }
        fun pace() {
            val cells=listOf(Triple("WEEKLY RATE",snap.weeklyRateKg?.let { Units.signed(it,unit) } ?: "—",colors.accent),
                Triple("THIS WEEK",data.weekChangeKg?.let { Units.signed(it,unit) } ?: "—",data.weekChangeKg?.let(::tint) ?: colors.text),
                Triple("GOAL BY",goal(w<260f || h<140f),colors.text),Triple("STREAK","${snap.streakDays} ${if(snap.streakDays==1) "day" else "days"}",colors.text))
            if(compact && w<300f) {
                label("title",cells[0].first,rect(pad,pad,w-pad,pad+12))
                line("rate",cells[0].second,rect(pad,pad+15,w-pad,h-pad),25f,colors.accent,type.numberBold)
                return
            }
            if(w<150f) {
                val status=snap.pace?.takeIf {show.showStatus && h>=200}
                val row=(h-pad*2-(if(status!=null) 18 else 0))/4
                cells.forEachIndexed {i,(title,value,tint)->
                    val y=pad+i*row+(row-32).coerceAtLeast(0f)/2
                    label("label-$i",title,rect(pad,y,w-pad,y+10))
                    line("value-$i",value,rect(pad,y+12,w-pad,min(pad+(i+1)*row-2,y+34)),22f,tint,type.numberBold)
                }
                status?.let {label("status",when(it) {Pace.AHEAD->"Ahead";Pace.ON_TRACK->"On track";Pace.BEHIND->"Behind"},rect(pad,h-pad-12,w-pad,h-pad))}
                return
            }
            val wide=w>=300f && h<140f
            val status=snap.pace?.takeIf { show.showStatus && !wide && h>=155 }
            val usable=h-pad*2-(if(status!=null) 20 else 0)
            cells.forEachIndexed { i,(title,value,tint) ->
                val cellW=if(wide) inner/4 else inner/2
                val cellH=if(wide) usable else usable/2
                val x=pad+(if(wide) i else i%2)*cellW
                val y=pad+(if(wide) 0 else i/2)*cellH
                val top=y+(cellH-39).coerceAtLeast(0f)/2
                label("label-$i",title,rect(x,top,x+cellW-8,top+12))
                line("value-$i",value,rect(x,top+15,x+cellW-8,min(y+cellH-3,top+48)),min(30f,cellW*.29f),tint,type.numberBold)
            }
            status?.let { label("status",when(it) { Pace.AHEAD->"Ahead";Pace.ON_TRACK->"On track";Pace.BEHIND->"Behind" },rect(pad,h-pad-12,w-pad,h-pad),Paint.Align.RIGHT) }
        }
        fun bodyFat() {
            val latest=data.bodyFat.lastOrNull()
            label("title","BODY FAT"+(if(settings.widgetTimeRange!=WidgetTimeRange.JOURNEY) " · ${settings.widgetTimeRange.label.uppercase()}" else ""),rect(pad,pad,w-pad,pad+12))
            val valueEnd=if(w>=260f) w-pad-inner*(if(h<135f) .59f else .36f) else w-pad
            line("body-fat",latest?.let { BodyFat.format(it.percent) } ?: "—",rect(pad,pad+14,valueEnd,min(h-pad,pad+43)),29f,colors.accent,type.numberBold)
            if(latest==null) {
                if(h>=105) line("empty","No measurements",rect(pad,h-pad-18,w-pad,h-pad),12f,colors.textDim,type.body,secondary=true)
                return
            }
            if(h in 80f..134.99f && w>=260f) {
                chart(rect(pad+inner*.47f,pad,w-pad,h-pad-20),
                    ChartSpec(settings.widgetTimeRange.bodyFat(snap).map {TrendPoint(it.epochMs,it.percent,it.percent)},ChartStyle.CONNECTED,WeightUnit.KG,
                        snap.targetBodyFat.takeIf {show.showTarget},metric=ChartMetric.BODY_FAT),false)
                label("date",date(latest.epochMs),rect(pad,h-pad-12,w-pad,h-pad))
                return
            }
            if(compact) return
            if(w>=260f) data.bodyFat.dropLast(1).lastOrNull()?.let { previous ->
                line("change",String.format(Locale.getDefault(),"%+.1f pp",latest.percent-previous.percent),rect(valueEnd+10,pad+17,w-pad,pad+41),14f,colors.textDim,type.number,Paint.Align.RIGHT)
            }
            val points=settings.widgetTimeRange.bodyFat(snap).map { TrendPoint(it.epochMs,it.percent,it.percent) }
            val area=rect(pad,pad+(if(h<135f) 45 else 50),w-pad,h-pad-(if(h<135f) 18 else 22))
            chart(area,ChartSpec(points,ChartStyle.CONNECTED,WeightUnit.KG,snap.targetBodyFat.takeIf {show.showTarget},metric=ChartMetric.BODY_FAT),w>=260f && area.height()>=70)
            val footer=if(show.showTarget && snap.targetBodyFat!=null && w>=260f) "Target ${BodyFat.format(snap.targetBodyFat)} · ${date(latest.epochMs)}" else date(latest.epochMs)
            label("date",footer,rect(pad,h-pad-13,w-pad,h-pad))
        }
    }
}
