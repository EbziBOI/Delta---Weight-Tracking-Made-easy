package io.github.ebziboi.delta.render

import android.graphics.*
import io.github.ebziboi.delta.data.PercentageDesign
import kotlin.math.*

/** Unit-based geometry keeps the same visual weight at dashboard and widget sizes. */
object PercentageArt {
    fun draw(c: Canvas, box: RectF, fraction: Float?, design: PercentageDesign, colors: DrawColors, type: CanvasType, matte: Boolean, showLabel: Boolean = true) {
        val size = min(box.width(), box.height())
        val cx = box.centerX(); val cy = box.centerY(); val p = (fraction?.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, 1f)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        fun ring(radius: Float, width: Float, start: Float, sweep: Float, active: Boolean) {
            ink.strokeWidth = size * width
            ink.color = if (active) colors.accent else colors.text
            ink.alpha = if (active) 255 else 28
            ink.shader = if (active && !matte) SweepGradient(cx, cy, colors.accent2, colors.accent) else null
            c.drawArc(RectF(cx-radius*size,cy-radius*size,cx+radius*size,cy+radius*size),start,sweep,false,ink)
            ink.shader = null
        }
        fun tile(rect: RectF, amount: Float, color: Int, radius: Float) {
            ink.style = Paint.Style.FILL; ink.shader = null; ink.color = colors.text; ink.alpha = 28
            c.drawRoundRect(rect, radius, radius, ink)
            if (amount > 0f) {
                ink.alpha = 255; ink.color = color
                if (!matte) ink.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, color, colors.accent2, Shader.TileMode.CLAMP)
                val saved = c.save(); c.clipRect(rect.left,rect.top,rect.left+rect.width()*amount,rect.bottom)
                c.drawRoundRect(rect,radius,radius,ink);c.restoreToCount(saved);ink.shader=null
            }
        }
        when (design) {
            PercentageDesign.RIBBON -> repeat(4) { i ->
                val y = box.top + size * (0.20f + i * 0.105f)
                val left = cx - size * (0.35f - i * 0.025f)
                tile(RectF(left,y,cx+size*.35f,y+size*(if(matte) .065f else .045f)),
                    (p*4-i).coerceIn(0f,1f),if(i%2==0) colors.accent else colors.accent2,size*.025f)
            }
            PercentageDesign.MOSAIC -> repeat(20) { i ->
                val row = 3-i/5; val col=i%5
                val x=cx-size*.30f+col*size*.123f;val y=box.top+size*.15f+row*size*.123f
                tile(RectF(x,y,x+size*.105f,y+size*.105f),(p*20-i).coerceIn(0f,1f),
                    if((row+col)%3==0) colors.accent2 else colors.accent,size*.014f)
            }
            PercentageDesign.SUMMIT -> {
                val path=Path().apply { moveTo(cx-size*.36f,box.top+size*.65f);lineTo(cx,box.top+size*.14f);lineTo(cx+size*.36f,box.top+size*.65f);close() }
                ink.style=Paint.Style.FILL;ink.color=colors.text;ink.alpha=28;c.drawPath(path,ink)
                val saved=c.save();c.clipPath(path);ink.alpha=255;ink.color=colors.accent
                if(!matte) ink.shader=LinearGradient(cx,box.top+size*.14f,cx,box.top+size*.65f,colors.accent2,colors.accent,Shader.TileMode.CLAMP)
                c.drawRect(box.left,box.top+size*(.65f-.51f*p),box.right,box.top+size*.66f,ink);ink.shader=null
                ink.color=colors.bg;ink.alpha=110;ink.strokeWidth=size*.009f
                repeat(4) { i -> val y=box.top+size*(.24f+i*.10f);c.drawLine(box.left,y,box.right,y,ink) }
                c.restoreToCount(saved)
            }

            PercentageDesign.BLOOM -> repeat(24) { i ->
                val angle = Math.toRadians(i * 15.0 - 90)
                ink.strokeWidth = size * if (matte) 0.043f else 0.03f
                ink.color = if (i < (p * 24).roundToInt()) colors.accent else colors.text
                ink.alpha = if (i < (p * 24).roundToInt()) 255 else 30
                val inner = if (i % 2 == 0) 0.31f else 0.35f
                c.drawLine(cx+cos(angle).toFloat()*size*inner,cy+sin(angle).toFloat()*size*inner,
                    cx+cos(angle).toFloat()*size*0.45f,cy+sin(angle).toFloat()*size*0.45f,ink)
            }
            PercentageDesign.CONTOURS -> repeat(4) { i ->
                val r = 0.27f+i*0.055f
                ring(r,0.018f,135f,270f,false)
                ring(r,0.018f,135f,270f*p,true)
            }
            else -> {
                ring(0.40f,if (matte) 0.062f else 0.038f,-90f,360f,false)
                ring(0.40f,if (matte) 0.062f else 0.038f,-90f,360f*p,true)
                repeat(20) { i ->
                    val a = Math.toRadians(i*18.0-90)
                    ink.style=Paint.Style.FILL; ink.color=colors.accent2; ink.alpha=95
                    c.drawCircle(cx+cos(a).toFloat()*size*0.30f,cy+sin(a).toFloat()*size*0.30f,size*0.007f,ink)
                }
                val a = Math.toRadians(p*360.0-90)
                ink.style=Paint.Style.FILL;ink.alpha=255;ink.color=colors.text
                c.drawCircle(cx+cos(a).toFloat()*size*0.40f,cy+sin(a).toFloat()*size*0.40f,size*0.033f,ink)
            }
        }
        if (!showLabel) return
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=colors.text;typeface=type.numberBold;textSize=size*0.20f;textAlign=Paint.Align.CENTER }
        if (!matte) text.setShadowLayer(size*.018f, 0f, size*.008f, ThemeContrast.on(colors.text))
        val label = fraction?.takeIf { it.isFinite() }?.let { "${(p*100).roundToInt()}%" } ?: "—"
        val geometric = design in listOf(PercentageDesign.RIBBON,PercentageDesign.MOSAIC,PercentageDesign.SUMMIT)
        val room = size * if(geometric) .65f else .43f
        if (text.measureText(label)>room) text.textSize*=room/text.measureText(label)
        c.drawText(label,cx,(if(geometric) box.top+size*.82f else cy)-(text.ascent()+text.descent())/2,text)
    }
}
