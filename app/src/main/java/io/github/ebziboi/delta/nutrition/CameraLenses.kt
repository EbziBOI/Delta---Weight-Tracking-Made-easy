@file:OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)

package io.github.ebziboi.delta.nutrition

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One choosable back lens. A lens with no [cameraId] is reached through the default camera's zoom ratio:
 * phones with a logical multi-camera (recent Samsung Galaxy, Pixel and others) switch to their ultrawide
 * below 1× and to each telephoto at its native ratio by themselves. A lens with a [cameraId] is a
 * separately exposed camera that has to be bound on its own.
 */
internal data class CameraLens(val label:String,val ratio:Float,val cameraId:String?=null,val zoom:Float=ratio) {
    val selector:CameraSelector get()=cameraId?.let {id->CameraSelector.Builder().addCameraFilter {infos->infos.filter {cameraIdOf(it)==id}}.build()} ?: CameraSelector.DEFAULT_BACK_CAMERA
}

internal data class LensSet(val lenses:List<CameraLens>,val minZoom:Float,val maxZoom:Float) {
    val main get()=lenses.firstOrNull {it.cameraId==null && abs(it.ratio-1f)<.01f} ?: lenses.first()
    /** The lens chip matching a pinch-zoom ratio on the default camera. */
    fun nearest(zoom:Float):CameraLens?=lenses.filter {it.cameraId==null && it.zoom<=zoom+.05f}.maxByOrNull {it.zoom}
}

internal fun cameraIdOf(info:CameraInfo):String?=runCatching {Camera2CameraInfo.from(info).cameraId}.getOrNull()

internal object CameraLenses {
    /** Label shown on a lens chip: 0.6×, 1×, 3×, 5×… */
    fun label(ratio:Float):String=when {
        ratio<.95f -> "${(ratio*10).roundToInt()/10f}×".replace(".0×","×")
        abs(ratio-1f)<.05f -> "1×"
        else -> "${ratio.roundToInt()}×"
    }

    /** The nominal ratio a phone's camera stack expects for a lens (3.0 rather than 2.9). */
    fun nominal(ratio:Float):Float=if(ratio<.95f) (ratio*10).roundToInt()/10f else if(ratio<1.5f) 1f else ratio.roundToInt().toFloat()

    private fun equivalentFocal(c:CameraCharacteristics):Float? {
        val focal=c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull() ?: return null
        val sensor=c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
        val diagonal=sqrt(sensor.width*sensor.width+sensor.height*sensor.height)
        return if(diagonal>0 && focal>0) focal*43.27f/diagonal else null
    }
    private fun sensorArea(c:CameraCharacteristics)=c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.let {it.width*it.height} ?: 0f

    private data class Physical(val id:String,val focal:Float,val area:Float)

    fun discover(context:Context,provider:ProcessCameraProvider):LensSet {
        val manager=context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val back=provider.availableCameraInfos.filter {it.lensFacing==CameraSelector.LENS_FACING_BACK}
        val default=runCatching {CameraSelector.DEFAULT_BACK_CAMERA.filter(provider.availableCameraInfos).firstOrNull()}.getOrNull() ?: back.firstOrNull()
            ?: return LensSet(listOf(CameraLens("1×",1f)),1f,1f)
        val defaultId=cameraIdOf(default)
        val chars=defaultId?.let {runCatching {manager.getCameraCharacteristics(it)}.getOrNull()}
        val zoomState=default.zoomState.value
        val range=if(Build.VERSION.SDK_INT>=30) chars?.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) else null
        val minZoom=range?.lower ?: zoomState?.minZoomRatio ?: 1f
        val maxZoom=range?.upper ?: zoomState?.maxZoomRatio ?: (chars?.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f)

        // Lenses inside the default logical camera.
        val members=if(Build.VERSION.SDK_INT>=28) chars?.physicalCameraIds.orEmpty() else emptySet()
        val physical=members.mapNotNull {id->runCatching {manager.getCameraCharacteristics(id)}.getOrNull()?.let {c->equivalentFocal(c)?.let {Physical(id,it,sensorArea(c))}}}
        // Other back cameras the phone lists on their own.
        val separate=back.filter {cameraIdOf(it)!=defaultId && cameraIdOf(it) !in members}.mapNotNull {info->
            val id=cameraIdOf(info) ?: return@mapNotNull null
            runCatching {manager.getCameraCharacteristics(id)}.getOrNull()?.let {c->equivalentFocal(c)?.let {Physical(id,it,sensorArea(c))}}
        }
        // The main camera has the largest sensor; every ratio is relative to it.
        val reference=physical.maxByOrNull {it.area}?.focal ?: chars?.let(::equivalentFocal) ?: (physical+separate).maxByOrNull {it.area}?.focal

        val lenses=mutableListOf(CameraLens("1×",1f,zoom=1f.coerceIn(minZoom,maxZoom)))
        fun add(ratio:Float,separateId:String?) {
            val nominal=nominal(ratio)
            if(nominal in .2f..15f && lenses.none {it.label==label(nominal)}) {
                val reachable=nominal>=minZoom-.05f && nominal<=maxZoom+.05f
                when {
                    reachable && separateId==null -> lenses+=CameraLens(label(nominal),nominal,zoom=nominal.coerceIn(minZoom,maxZoom))
                    separateId!=null -> lenses+=CameraLens(label(nominal),nominal,cameraId=separateId,zoom=1f)
                }
            }
        }
        if(reference!=null) {
            physical.forEach {p->val r=p.focal/reference;if(abs(r-1f)>.15f) add(r,null)}
            separate.forEach {p->
                val r=p.focal/reference
                // Prefer the default camera's own zoom when it already reaches this lens.
                if(abs(r-1f)>.15f) add(r,if(nominal(r)>=minZoom-.05f && nominal(r)<=maxZoom+.05f && members.isNotEmpty()) null else p.id)
            }
        }
        // Without lens details, still offer what the zoom range reaches: the ultrawide and a close-up crop.
        if(lenses.none {it.ratio<.95f} && minZoom<.9f) lenses+=CameraLens(label(minZoom),minZoom,zoom=minZoom)
        if(lenses.none {it.ratio>1.4f} && maxZoom>=2f) lenses+=CameraLens("2×",2f,zoom=2f)
        return LensSet(lenses.sortedBy {it.ratio},minZoom,maxZoom)
    }
}
