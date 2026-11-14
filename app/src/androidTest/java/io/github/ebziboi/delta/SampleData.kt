package io.github.ebziboi.delta

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import io.github.ebziboi.delta.data.WeightEntry
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.roundToInt

/** Sample data and drawing helpers shared by the on-device tests. Never anyone's real weight. */
object SampleData {

    /**
     * About eleven weeks of weigh-ins from [startDay]: quick at first, slower
     * later, with the odd missed day. Stops at yesterday, so that logging
     * "today" through the screen adds a new day.
     */
    fun entries(startDay: LocalDate): List<WeightEntry> {
        val zone = ZoneId.systemDefault()
        val wobble = listOf(0.4, -0.3, 0.2, -0.5, 0.3, 0.1, -0.2, 0.5, -0.4, 0.0, 0.2)
        val entries = ArrayList<WeightEntry>()
        for (day in 0..73) {
            if (day % 6 == 4 && day <= 66) continue
            val base = 96.4 - 9.0 * (1 - exp(-day / 55.0)) / (1 - exp(-74 / 55.0)) + (if (day in 38..47) 0.5 else 0.0)
            val kg = ((base + wobble[day % wobble.size]) * 10).roundToInt() / 10.0
            val at = startDay.plusDays(day.toLong()).atTime(7, 10).atZone(zone).toInstant().toEpochMilli()
            entries += WeightEntry(epochMs = at, kg = kg, own = true, id = "sample-$day")
        }
        return entries
    }

    /**
     * A bright, busy backdrop like a photo wallpaper, so pictures of translucent
     * widgets show how much of what is behind them comes through.
     */
    fun wallpaper(canvas: Canvas, width: Int, height: Int, light: Boolean) {
        val w = width.toFloat()
        val h = height.toFloat()
        val base = if (light) {
            intArrayOf(0xFFFDE3C8.toInt(), 0xFFF7B7CB.toInt(), 0xFFAFD5F7.toInt())
        } else {
            intArrayOf(0xFF151A45.toInt(), 0xFF4D2466.toInt(), 0xFF0B4A5A.toInt())
        }
        canvas.drawRect(0f, 0f, w, h, Paint().apply { shader = LinearGradient(0f, 0f, w, h, base, null, Shader.TileMode.CLAMP) })
        val highlights = if (light) {
            listOf(0xCCFFFFFF.toInt(), 0x99FFC46B.toInt(), 0x8873A7FF.toInt())
        } else {
            listOf(0x99FF7AB6.toInt(), 0x8862D0FF.toInt(), 0x77FFC46B.toInt())
        }
        val spots = listOf(Triple(0.18f, 0.22f, 0.32f), Triple(0.82f, 0.48f, 0.38f), Triple(0.35f, 0.86f, 0.30f))
        spots.forEachIndexed { index, (x, y, r) ->
            val radius = r * maxOf(w, h)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(x * w, y * h, radius, highlights[index], highlights[index] and 0x00FFFFFF, Shader.TileMode.CLAMP)
            }
            canvas.drawCircle(x * w, y * h, radius, paint)
        }
        // A few hard-edged shapes, the kind of detail glass should soften rather than hide.
        val shape = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (light) 0x66FFFFFF else 0x33FFFFFF }
        for (i in 0 until 6) {
            val cx = w * (0.1f + 0.16f * i)
            val cy = h * (if (i % 2 == 0) 0.62f else 0.38f)
            canvas.drawCircle(cx, cy, minOf(w, h) * 0.05f, shape)
        }
    }

    fun save(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
