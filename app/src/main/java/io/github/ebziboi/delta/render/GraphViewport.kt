package io.github.ebziboi.delta.render

import android.graphics.RectF
import io.github.ebziboi.delta.data.TrendPoint
import kotlin.math.*

internal fun RectF.includes(x: Float, y: Float) = x in left..right && y in top..bottom

enum class ChartMetric { WEIGHT, BODY_FAT }
data class GraphViewport(val first: Long, val last: Long, val low: Double, val high: Double) {
    fun transform(full: GraphViewport, scale: Float, anchorX: Float, anchorY: Float, panX: Float = 0f, panY: Float = 0f): GraphViewport {
        if (!scale.isFinite() || !anchorX.isFinite() || !anchorY.isFinite() || scale <= 0 || !panX.isFinite() || !panY.isFinite()) return this
        val fullTime = (full.last - full.first).toDouble().coerceAtLeast(1.0)
        val fullHeight = (full.high - full.low).coerceAtLeast(0.01)
        val time = (last - first).toDouble()
        val height = high - low
        val nextTime = (time / scale).coerceIn(fullTime / 32, fullTime)
        val nextHeight = fullHeight
        val ax = anchorX.coerceIn(0f, 1f); val ay = anchorY.coerceIn(0f, 1f)
        val start = (first + time * ax - nextTime * ax - panX * nextTime).coerceIn(full.first.toDouble(), full.last - nextTime)
        val bottom = full.low
        return GraphViewport(start.roundToLong(), (start + nextTime).roundToLong(), bottom, bottom + nextHeight)
    }
    fun zoom(full: GraphViewport) = (full.last - full.first).toDouble() / (last - first).coerceAtLeast(1)
}
data class ChartHit(val point: TrendPoint, val x: Float, val y: Float)
data class ChartLayout(val plot: RectF, val full: GraphViewport, val shown: GraphViewport, val hits: List<ChartHit>) {
    fun nearest(x: Float, y: Float): TrendPoint? = if (!plot.includes(x, y)) null else hits
        .filter { plot.includes(it.x, it.y) }.minByOrNull { hypot(it.x - x, it.y - y) }?.point
}
