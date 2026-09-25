package com.mckimquyen.views

import android.graphics.Canvas
import android.graphics.RecordingCanvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * FISH-007: depth-of-field falloff for the lens grid. Everything here is pure math (unit-tested in
 * DepthOfFieldTest) except [DepthOfFieldLayers]. Blur never touches geometry: LensView computes
 * each cell's rect and hit-test first, and only picks *which canvas* the icon is drawn into.
 */
object DepthOfField {
    /** Normalized distance (0 = focus, 1 = farthest) that stays fully sharp around the finger. */
    const val FOCUS_ZONE = 0.2f
    /** Quantized blur levels above "sharp" - one RenderNode (one GPU blur pass) per level. */
    const val BLUR_BAND_COUNT = 2
    const val MAX_BLUR_RADIUS_DP = 4f
    /** Fallback floor: far icons never drop below half opacity, so they stay legible/tappable. */
    const val MIN_FALLBACK_ALPHA = 0.5f
    const val OPAQUE_ALPHA = 255
    /**
     * Blurred layers are recorded at 1/N resolution and scaled back up: blurred content has no
     * high-frequency detail to lose, and GPU blur cost scales with layer area (1/N^2). Measured on
     * Pixel 7 Pro: full-res layers pushed drag p50 from 6 ms to 27 ms.
     */
    const val BLUR_LAYER_DOWNSAMPLE = 8

    fun layerSize(sizePx: Int, downsample: Int): Int =
        if (sizePx <= 0) 0 else (sizePx + downsample - 1) / downsample

    enum class Mode { OFF, BLUR, ALPHA }

    /** Real blur needs RenderEffect (API 31) on a hardware canvas; anything else fades instead. */
    fun renderMode(enabled: Boolean, lensVisible: Boolean, sdkInt: Int, hardwareAccelerated: Boolean): Mode = when {
        !enabled || !lensVisible -> Mode.OFF
        sdkInt >= Build.VERSION_CODES.S && hardwareAccelerated -> Mode.BLUR
        else -> Mode.ALPHA
    }

    /**
     * Same per-axis normalization UtilCalculator.shiftPoint uses for the warp (distance divided by
     * the farther edge from the finger), combined radially and clamped to [0, 1].
     */
    fun focusDistance(touchX: Float, touchY: Float, cellX: Float, cellY: Float, width: Float, height: Float): Float {
        val nx = axisDistance(touchX, cellX, width)
        val ny = axisDistance(touchY, cellY, height)
        return min(1f, sqrt(nx * nx + ny * ny))
    }

    private fun axisDistance(touch: Float, cell: Float, boundary: Float): Float {
        val reach = max(touch, boundary - touch)
        return if (reach <= 0f) 0f else abs(touch - cell) / reach
    }

    /** Under reduced motion the blur is static: it snaps in/out instead of following the lens animation. */
    fun transition(animationMultiplier: Float, reduceMotion: Boolean): Float = when {
        !reduceMotion -> animationMultiplier.coerceIn(0f, 1f)
        animationMultiplier > 0f -> 1f
        else -> 0f
    }

    fun intensity(focusDistance: Float, transition: Float): Float {
        val ramp = ((focusDistance - FOCUS_ZONE) / (1f - FOCUS_ZONE)).coerceIn(0f, 1f)
        return ramp * transition.coerceIn(0f, 1f)
    }

    /** 0 = sharp, 1..[BLUR_BAND_COUNT] = increasingly blurred. */
    fun band(intensity: Float): Int =
        if (intensity <= 0f) 0 else ceil(intensity * BLUR_BAND_COUNT).toInt().coerceIn(1, BLUR_BAND_COUNT)

    fun blurRadiusPx(band: Int, density: Float): Float =
        MAX_BLUR_RADIUS_DP * density * band.coerceIn(0, BLUR_BAND_COUNT) / BLUR_BAND_COUNT

    fun fallbackAlpha(intensity: Float): Int =
        (OPAQUE_ALPHA * (1f - intensity.coerceIn(0f, 1f) * (1f - MIN_FALLBACK_ALPHA))).roundToInt()
}

/**
 * One RenderNode per band, blur effect fixed at construction, re-recorded every frame. Cost is at
 * most [DepthOfField.BLUR_BAND_COUNT] blur passes per frame regardless of icon count, and nothing
 * is allocated per frame (RecordingCanvas instances are pooled by the framework).
 */
@RequiresApi(Build.VERSION_CODES.S)
class DepthOfFieldLayers(density: Float) {
    private val nodes = Array(DepthOfField.BLUR_BAND_COUNT + 1) { band ->
        RenderNode("lens-dof-$band").apply {
            if (band > 0) {
                val radius = DepthOfField.blurRadiusPx(band, density) / DOWNSAMPLE
                setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL))
                setPivotX(0f)
                setPivotY(0f)
                setScaleX(DOWNSAMPLE)
                setScaleY(DOWNSAMPLE)
            }
        }
    }
    private val canvases = arrayOfNulls<RecordingCanvas>(nodes.size)

    fun begin(width: Int, height: Int) {
        for (band in nodes.indices) {
            val node = nodes[band]
            if (band == 0) {
                node.setPosition(0, 0, width, height)
                canvases[band] = node.beginRecording()
            } else {
                val ds = DepthOfField.BLUR_LAYER_DOWNSAMPLE
                node.setPosition(0, 0, DepthOfField.layerSize(width, ds), DepthOfField.layerSize(height, ds))
                canvases[band] = node.beginRecording().apply { scale(1f / DOWNSAMPLE, 1f / DOWNSAMPLE) }
            }
        }
    }

    private companion object {
        const val DOWNSAMPLE = DepthOfField.BLUR_LAYER_DOWNSAMPLE.toFloat()
    }

    fun canvas(band: Int): Canvas? = canvases.getOrNull(band)

    /** Most-blurred first, sharp last, so in-focus (enlarged, overlapping) icons stay on top. */
    fun endAndDraw(target: Canvas) {
        for (band in nodes.indices) {
            nodes[band].endRecording()
            canvases[band] = null
        }
        for (band in DepthOfField.BLUR_BAND_COUNT downTo 0) {
            target.drawRenderNode(nodes[band])
        }
    }

    fun discard() {
        for (node in nodes) node.discardDisplayList()
    }
}
