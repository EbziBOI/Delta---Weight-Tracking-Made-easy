package io.github.ebziboi.delta.render

import android.content.Context
import android.graphics.*
import io.github.ebziboi.delta.data.*
import io.github.ebziboi.delta.nutrition.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

enum class FoodWidgetKind(val title:String,val description:String,val route:String,val width:Float,val height:Float) {
    ENERGY("Energy budget","Energy logged and remaining against your daily target","charts",180f,180f),
    MACROS("Macro balance","Protein, carbohydrate and fat with independent daily targets","targets",320f,180f),
    MEALS("Food diary","Today's meals, recent food and a quick way to add another","diary",320f,180f),
    FASTING("Fasting clock","Live elapsed fasting time, your target and finishing time","fasting",180f,180f)
}

internal data class FoodWidgetData(val day:LocalDate,val entries:List<DiaryEntry>,val targets:NutritionTargets,val fast:Fast?,val now:Long) {
    fun total(n:Nutrient)=entries.total(n)
    companion object {fun from(state:NutritionState,now:Long=System.currentTimeMillis(),zone:ZoneId=ZoneId.systemDefault()):FoodWidgetData {
        val day=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return FoodWidgetData(day,state.entries.filter {it.day==day},state.targets,state.fasts.firstOrNull {it.end==null},now)
    }}
}

object FoodWidgetBitmaps {
    internal fun theme(context:Context,kind:FoodWidgetKind,settings:AppSettings,night:Boolean)=AppearanceEngine.forWidget(context,settings,night,when(kind) {
        FoodWidgetKind.ENERGY->WidgetKind.CARD;FoodWidgetKind.MACROS->WidgetKind.TREND;FoodWidgetKind.MEALS->WidgetKind.PACE;FoodWidgetKind.FASTING->WidgetKind.COMPLICATION
    })
    fun render(context:Context,kind:FoodWidgetKind,width:Float,height:Float,state:NutritionState,settings:AppSettings,
               night:Boolean=Palettes.isNight(context),now:Long=System.currentTimeMillis(),maxSide:Float=900f,
               liveClock:Boolean=false,audit:MutableList<WidgetElement>?=null):Bitmap {
        require(width.isFinite() && height.isFinite() && width>0 && height>0 && maxSide.isFinite() && maxSide>=1)
        val scale=min(context.resources.displayMetrics.density,maxSide/max(width,height))
        val image=Bitmap.createBitmap(max(1,(width*scale).roundToInt()),max(1,(height*scale).roundToInt()),Bitmap.Config.ARGB_8888)
        val theme=theme(context,kind,settings,night)
        val colors=theme.drawColors()
        val canvas=Canvas(image);canvas.scale(scale,scale)
        val corner=WidgetCorners.radius(context,theme.appearance.cornerRadius)
        val opacity=if(theme.isLight) settings.widgetOpacityLight else settings.widgetOpacityDark
        if(theme.appearance.designStyle==DesignStyle.TUBIK && settings.widgetGlass!=WidgetGlass.SOLID && !theme.appearance.reduceTransparency) {
            val layer=canvas.saveLayerAlpha(0f,0f,width,height,(opacity.coerceIn(0f,1f)*255).roundToInt())
            GlassWidgetSurface.draw(canvas,width,height,theme,settings,corner);canvas.restoreToCount(layer)
        } else GlassWidgetSurface.draw(canvas,width,height,theme,settings,corner)
        val palette=if(settings.widgetGlass==WidgetGlass.SOLID || theme.appearance.reduceTransparency) colors
            else WidgetLegibility.over(colors,theme.background,opacity,WallpaperTone.backdrop(context))
        val safe=(max(corner,theme.appearance.cornerRadius)*(1f-1f/sqrt(2f))+3f).coerceAtMost(min(width,height)*.21f)
        Painter(canvas,width,height,palette,DeltaFonts.canvas(context,settings.typeStyle),FoodWidgetData.from(state,now),audit,
            max(safe,min(width,height)*.08f).coerceAtLeast(4f),liveClock).draw(kind)
        return image
    }
    private class Painter(val c:Canvas,val w:Float,val h:Float,val colors:DrawColors,val fonts:CanvasType,val data:FoodWidgetData,
                          val audit:MutableList<WidgetElement>?,val pad:Float,val liveClock:Boolean) {
        val box=RectF(pad,pad,w-pad,h-pad);val tiny=(w<120 && h<200) || h<100;val roomy=w>=240 && h>=140
        /** A short, wide widget (such as 2×1 cells): one line of figures beats a dial too small to read. */
        val strip=!tiny && h<150 && w>=h*1.3f
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        val energy=data.total(Nutrient.ENERGY)
        fun short(v:Double):String=when {v>=1e6->"${round(v/1e5)/10}M";v>=10000->"${round(v/100)/10}k";else->amountText(v)}
        fun text(role:String,value:String,r:RectF,max:Float=15f,color:Int=colors.text,bold:Boolean=false,center:Boolean=false) {
            if(r.width()<1 || r.height()<1) return
            paint.typeface=if(bold) fonts.numberBold else fonts.body;paint.color=color;paint.style=Paint.Style.FILL
            paint.textSize=min(max,r.height()*.74f)
            while(paint.measureText(value)>r.width() && paint.textSize>(if(role.startsWith("food-")) minOf(11f,paint.textSize) else 6f)) paint.textSize-=.5f
            val shown=if(paint.measureText(value)<=r.width()) value else android.text.TextUtils.ellipsize(value,android.text.TextPaint(paint),r.width(),android.text.TextUtils.TruncateAt.END).toString()
            val metrics=paint.fontMetrics
            val baseline=r.centerY()-(metrics.ascent+metrics.descent)/2
            val x=if(center) r.centerX()-paint.measureText(shown)/2 else r.left
            c.drawText(shown,x,baseline,paint)
            audit?.add(WidgetElement(role,RectF(x,baseline+metrics.ascent,x+paint.measureText(shown),baseline+metrics.descent),shown,paint.textSize))
        }
        fun rail(r:RectF,f:Double?,color:Int) {
            paint.style=Paint.Style.FILL;paint.color=Palettes.withAlpha(colors.textDim,45);c.drawRoundRect(r,r.height()/2,r.height()/2,paint)
            if(f!=null && f>0) {paint.color=color;c.drawRoundRect(RectF(r.left,r.top,r.left+r.width()*f.coerceIn(0.0,1.0).toFloat(),r.bottom),r.height()/2,r.height()/2,paint)}
        }
        fun arc(rect:RectF,f:Double?,color:Int,width:Float=5f,start:Float=140f,sweep:Float=260f) {
            paint.style=Paint.Style.STROKE;paint.strokeWidth=width;paint.strokeCap=Paint.Cap.ROUND;paint.color=Palettes.withAlpha(colors.textDim,50)
            c.drawArc(rect,start,sweep,false,paint)
            if(f!=null && f>0) {paint.color=color;c.drawArc(rect,start,(sweep*f.coerceIn(0.0,1.0)).toFloat(),false,paint)}
            paint.style=Paint.Style.FILL
        }
        fun row(y:Float,height:Float=20f)=RectF(box.left,y,box.right,y+height)
        fun known(n:Nutrient,unit:Boolean=true,whole:Boolean=false):String {val v=data.total(n);return if(v.reported==0) "—" else (if(v.partial) "≥" else "")+(if(whole) short(Math.round(v.amount).toDouble()) else short(v.amount))+(if(unit) "g" else "")}
        fun draw(kind:FoodWidgetKind) {when(kind) {FoodWidgetKind.ENERGY->energy();FoodWidgetKind.MACROS->macros();FoodWidgetKind.MEALS->meals();FoodWidgetKind.FASTING->fasting()}}
        fun energy() {
            val target=data.targets.target(Nutrient.ENERGY);val reported=energy.reported>0
            val left=target?.minus(energy.amount)
            val title=when { !reported->"ENERGY";target==null->"LOGGED ENERGY";energy.partial->"LEFT · PARTIAL";left!!<0->"ABOVE TARGET";else->"LEFT TODAY"}
            val figure=if(!reported) "—" else (if(energy.partial) {if(left!=null && left>=0) "≤" else "≥"} else "")+short(if(left==null) energy.amount else abs(left))
            if(w<120 && h>=200) {
                text("title",if(target==null) "ENERGY" else "LEFT",row(box.top,18f),10f,colors.textDim)
                val track=RectF(box.right-5,box.top+28,box.right,box.bottom-24)
                paint.color=Palettes.withAlpha(colors.textDim,45);c.drawRoundRect(track,3f,3f,paint)
                if(target!=null && reported) {paint.color=colors.accent;c.drawRoundRect(RectF(track.left,track.bottom-track.height()*(energy.amount/target).coerceIn(0.0,1.0).toFloat(),track.right,track.bottom),3f,3f,paint)}
                text("energy",figure,RectF(box.left,box.centerY()-22,track.left-3,box.centerY()+10),32f,colors.accent,true)
                text("unit","kcal",RectF(box.left,box.centerY()+12,track.left-3,box.centerY()+30),10f,colors.textDim)
                text("context",if(!reported) "No data" else "${short(energy.amount)} in",row(box.bottom-18,18f),10f,colors.textDim)
                return
            }
            if(tiny) {
                text("title",if(w<70) "kcal" else title,row(box.top,box.height()*.25f),11f,colors.textDim)
                text("energy",figure+(if(w>=90) " kcal" else ""),RectF(box.left,box.top+box.height()*.24f,box.right,box.bottom-5f),32f,colors.accent,true)
                rail(RectF(box.left,box.bottom-3f,box.right,box.bottom),target?.takeIf {it>0}?.let {energy.amount/it},colors.accent)
                return
            }
            if(strip) {
                text("title",title,row(box.top,16f),11f,colors.textDim)
                text("energy",figure+" kcal",RectF(box.left,box.top+18,box.right,box.bottom-26),34f,colors.accent,true)
                text("context",if(!reported) {if(data.entries.isEmpty()) "No food logged" else "Energy not supplied"}
                    else "${short(energy.amount)} logged"+(target?.let {" of ${short(it)}"} ?: ""),row(box.bottom-22,14f),11f,colors.textDim)
                rail(RectF(box.left,box.bottom-4,box.right,box.bottom),target?.takeIf {it>0 && reported}?.let {energy.amount/it},colors.accent)
                return
            }
            text("title",title,row(box.top),12f,colors.textDim)
            val side=min(box.width(),box.height()-40f).coerceAtLeast(30f)
            val dial=RectF(box.centerX()-side/2,box.top+26,box.centerX()+side/2,box.top+26+side)
            arc(RectF(dial.left+5,dial.top+5,dial.right-5,dial.bottom-5),target?.takeIf {it>0}?.let {energy.amount/it},colors.accent,6f)
            text("energy",figure,RectF(dial.left+8,dial.top+side*.23f,dial.right-8,dial.top+side*.65f),42f,colors.accent,true,true)
            text("unit","kcal",RectF(dial.left,dial.top+side*.65f,dial.right,dial.top+side*.83f),12f,colors.textDim,center=true)
            text("context",if(!reported) {if(data.entries.isEmpty()) "No food logged" else "Energy not supplied"}
                else "${short(energy.amount)} logged"+(target?.let {" / ${short(it)}"} ?: ""),row(box.bottom-18,18f),12f,colors.textDim,center=true)
        }
        fun macros() {
            val nutrients=listOf(Nutrient.PROTEIN,Nutrient.CARBS,Nutrient.FAT);val hues=listOf(colors.accent,colors.good,colors.warn)
            if(tiny && w>=220) {
                val col=(box.width()-16)/3
                nutrients.forEachIndexed {i,n->val x=box.left+i*(col+8)
                    text("macro-label-$i",listOf("Protein","Carbs","Fat")[i],RectF(x,box.top,x+col,box.top+box.height()*.3f),11f,hues[i])
                    text("macro-value-$i",known(n),RectF(x,box.top+box.height()*.3f,x+col,box.bottom),26f,hues[i],true)
                }
                return
            }
            if(tiny) {
                text("title","PROTEIN",row(box.top,box.height()*.25f),10f,colors.textDim)
                text("protein",known(Nutrient.PROTEIN),RectF(box.left,box.top+box.height()*.25f,box.right,box.bottom-8),32f,colors.accent,true)
                nutrients.forEachIndexed {i,n->val total=data.total(n);val target=data.targets.target(n)
                    val width=(box.width()-6)/3
                    rail(RectF(box.left+i*(width+3),box.bottom-3,box.left+i*(width+3)+width,box.bottom),if(total.reported>0 && target!=null) total.amount/target else null,hues[i])}
                return
            }
            if(strip) {
                text("title","MACROS",row(box.top,16f),11f,colors.textDim)
                val col=(box.width()-16)/3
                nutrients.forEachIndexed {i,n->val x=box.left+i*(col+8);val total=data.total(n);val target=data.targets.target(n)
                    text("macro-label-$i",if(col<64) listOf("Prot","Carbs","Fat")[i] else listOf("Protein","Carbs","Fat")[i],RectF(x,box.top+20,x+col,box.top+36),11f,hues[i])
                    // Whole grams keep the three figures the same size side by side.
                    text("macro-value-$i",known(n,whole=true),RectF(x,box.top+38,x+col,box.bottom-12),26f,hues[i],true)
                    rail(RectF(x,box.bottom-4,x+col,box.bottom),if(total.reported>0 && target!=null) total.amount/target else null,hues[i])
                }
                return
            }
            text("title","MACRO BALANCE",row(box.top),12f,colors.textDim)
            if(w>=220 && (w>=h || h<230)) {
                val col=(box.width()-12)/3
                nutrients.forEachIndexed {i,n->
                    val x=box.left+i*(col+6);val total=data.total(n);val target=data.targets.target(n)
                    val top=box.top+30;val areaH=box.height()-30
                    text("macro-label-$i",if(col<70) listOf("P","C","F")[i] else listOf("Protein","Carbs","Fat")[i],RectF(x,top,x+col,top+18),12f,hues[i],center=true)
                    text("macro-value-$i",known(n),RectF(x,top+20,x+col,top+areaH*.65f),30f,hues[i],true,true)
                    rail(RectF(x,top+areaH*.75f,x+col,top+areaH*.75f+5),if(total.reported>0 && target!=null) total.amount/target else null,hues[i])
                    if(areaH>90) text("target-$i",target?.let {"${short(it)}g goal"} ?: "No target",RectF(x,top+areaH*.8f,x+col,box.bottom),10f,colors.textDim,center=true)
                }
            } else {
                val height=(box.height()-32)/3
                nutrients.forEachIndexed {i,n->val y=box.top+30+i*height;val total=data.total(n);val target=data.targets.target(n)
                    text("macro-label-$i",if(box.width()<90) listOf("Protein","Carbs","Fat")[i] else n.label,row(y,min(18f,height*.24f)),13f,hues[i])
                    text("macro-value-$i",known(n),row(y+height*.28f,min(42f,height*.46f)),36f,hues[i],true)
                    rail(RectF(box.left,y+height*.86f,box.right,y+height*.86f+4),if(total.reported>0 && target!=null) total.amount/target else null,hues[i])
                }
            }
        }
        fun meals() {
            text("title",if(tiny) "FOODS" else "TODAY'S DIARY",row(box.top,if(tiny) box.height()*.25f else 20f),12f,colors.textDim)
            if(tiny) {text("count",data.entries.size.toString(),RectF(box.left,box.top+box.height()*.25f,box.right,box.bottom),34f,colors.accent,true);return}
            text("energy",if(energy.reported==0) "— kcal" else (if(energy.partial) "≥" else "")+"${short(energy.amount)} kcal",row(box.top+23,32f),29f,colors.accent,true)
            val rows=((box.height()-88)/30).toInt().coerceIn(1,8)
            data.entries.sortedByDescending {it.created}.take(rows).forEachIndexed {i,e->
                val y=box.top+63+i*30
                if(y+24<box.bottom-16) {
                    paint.color=colors.accent;c.drawCircle(box.left+3,y+10,2f,paint)
                    text("food-$i",e.food.name,RectF(box.left+12,y,box.right,y+20),13f)
                }
            }
            if(data.entries.isEmpty() && box.height()>110) text("empty","No food logged",row(box.top+60),13f,colors.textDim)
            text("meal-summary",if(data.entries.isEmpty()) "No food logged" else "${data.entries.size} ${if(data.entries.size==1) "food" else "foods"} · ${data.entries.map {it.meal}.distinct().size} meals",row(box.bottom-16,16f),11f,colors.textDim)
        }
        fun fasting() {
            val fast=data.fast
            val elapsed=fast?.elapsed(data.now) ?: 0L
            val hours=elapsed/3600000;val minutes=elapsed/60000%60
            if(w<120 && h>=200) {
                text("title","FAST",row(box.top,18f),11f,colors.textDim)
                val track=RectF(box.centerX()-2,box.top+32,box.centerX()+2,box.bottom-32)
                paint.color=Palettes.withAlpha(colors.textDim,45);c.drawRoundRect(track,2f,2f,paint)
                if(fast!=null) {paint.color=colors.accent;c.drawRoundRect(RectF(track.left,track.top,track.right,track.top+track.height()*(elapsed.toDouble()/fast.goalMs).coerceIn(0.0,1.0).toFloat()),2f,2f,paint)}
                paint.color=colors.bg;c.drawRect(RectF(box.left,box.centerY()-26,box.right,box.centerY()+26),paint)
                text("fasting",if(fast==null) "—" else "${hours}h",row(box.centerY()-24,30f),28f,colors.accent,true,center=true)
                text("fasting-unit","elapsed",row(box.centerY()+8,18f),10f,colors.textDim,center=true)
                text("fasting-context",fast?.let {"${amountText(it.targetHours)}h goal"} ?: "Ready",row(box.bottom-18,18f),10f,colors.textDim,center=true)
                return
            }
            if(tiny) {
                text("title",if(fast==null) "FASTING" else "ELAPSED",row(box.top,box.height()*.25f),10f,colors.textDim)
                text("fasting",if(fast==null) "—" else "${hours}h",RectF(box.left,box.top+box.height()*.25f,box.right,box.bottom),30f,colors.accent,true)
                return
            }
            val finishAt=fast?.targetEnd?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("HH:mm"))
            if(strip) {
                text("title",if(fast==null) "FASTING" else "ELAPSED FAST",row(box.top,16f),11f,colors.textDim)
                if(fast==null) text("fasting","Not fasting",RectF(box.left,box.top+22,box.right,box.bottom-30),22f,colors.text,true)
                else if(!liveClock) text("fasting",String.format(java.util.Locale.ROOT,"%d:%02d",hours,minutes),RectF(box.left,box.top+18,box.right,box.bottom-26),34f,colors.accent,true)
                text("fasting-context",when {fast==null->"Tap to start a fast";fast.reached(data.now)->"${amountText(fast.targetHours)}h goal reached";else->"${amountText(fast.targetHours)}h goal · ends $finishAt"},row(box.bottom-22,14f),11f,colors.textDim)
                rail(RectF(box.left,box.bottom-4,box.right,box.bottom),fast?.let {elapsed.toDouble()/it.goalMs},colors.accent)
                return
            }
            text("title",if(fast==null) "FASTING" else "ELAPSED FAST",row(box.top),12f,colors.textDim)
            val side=min(box.width(),box.height()-42).coerceAtLeast(30f)
            val ring=RectF(box.centerX()-side/2,box.centerY()-side/2,box.centerX()+side/2,box.centerY()+side/2)
            arc(RectF(ring.left+4,ring.top+4,ring.right-4,ring.bottom-4),fast?.let {elapsed.toDouble()/it.goalMs},colors.accent,4f,0f,360f)
            if(!liveClock || fast==null) text("fasting",if(fast==null) "Ready" else String.format(java.util.Locale.ROOT,"%02d:%02d",hours,minutes),
                RectF(ring.left+10,ring.centerY()-22,ring.right-10,ring.centerY()+22),34f,colors.accent,true,true)
            val finish=fast?.targetEnd?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("HH:mm"))
            text("fasting-context",when {fast==null->"Open your timer";fast.reached(data.now)->"${amountText(fast.targetHours)}h goal reached";else->"${amountText(fast.targetHours)}h · until $finish"},row(box.bottom-18,18f),12f,colors.textDim,center=true)
        }
    }
}
