package io.github.ebziboi.delta.render

import android.graphics.*
import io.github.ebziboi.delta.data.PercentageDesign
import kotlin.math.*

/** Unit-based geometry keeps the same visual weight at dashboard and widget sizes. */
object PercentageArt {
    fun draw(c: Canvas, box: RectF, fraction: Float?, design: PercentageDesign, colors: DrawColors, type: CanvasType, matte: Boolean) {
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
        when (design) {
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
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=colors.text;typeface=type.numberBold;textSize=size*0.20f;textAlign=Paint.Align.CENTER }
        if (!matte) text.setShadowLayer(size*.018f, 0f, size*.008f, ThemeContrast.on(colors.text))
        val label = fraction?.takeIf { it.isFinite() }?.let { "${(p*100).roundToInt()}%" } ?: "—"
        if (text.measureText(label)>size*.43f) text.textSize*=size*.43f/text.measureText(label)
        c.drawText(label,cx,cy-(text.ascent()+text.descent())/2,text)
    }
}
