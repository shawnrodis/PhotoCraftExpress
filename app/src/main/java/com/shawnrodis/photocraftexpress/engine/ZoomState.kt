package com.shawnrodis.photocraftexpress.engine

import kotlin.math.max
import kotlin.math.min

/**
 * Canvas zoom as a pure view transform (never part of the edit state).
 *
 * Coordinates are normalized canvas fractions (0..1 across the canvas
 * box). A point p in the unzoomed canvas frame maps to the displayed
 * frame as:  c = 0.5 + (p - 0.5) * scale + offset.
 * [scale] is clamped to 1..8; [offset] is clamped to ±2 canvas fractions
 * per axis so the photo can never be panned clean out of reach.
 * Pure and JVM-testable; the UI applies it via graphicsLayer and maps
 * touch input back through [inverse].
 */
data class ZoomState(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
) {
    val isZoomed: Boolean get() = scale > 1.001f

    /** Maps an unzoomed canvas point to the displayed (zoomed) frame. */
    fun forward(x: Float, y: Float): Pair<Float, Float> =
        (0.5f + (x - 0.5f) * scale + offsetX) to
            (0.5f + (y - 0.5f) * scale + offsetY)

    /** Maps a displayed-frame point back to the unzoomed canvas frame. */
    fun inverse(x: Float, y: Float): Pair<Float, Float> =
        ((x - 0.5f - offsetX) / scale + 0.5f) to
            ((y - 0.5f - offsetY) / scale + 0.5f)

    /**
     * Zooms by [factor] keeping the displayed point (cx, cy) anchored
     * (the image point under the pinch centroid stays under it).
     */
    fun zoomAt(cx: Float, cy: Float, factor: Float): ZoomState {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val ratio = newScale / scale
        return ZoomState(
            scale = newScale,
            offsetX = (cx - 0.5f - (cx - 0.5f - offsetX) * ratio)
                .coerceIn(-MAX_OFFSET, MAX_OFFSET),
            offsetY = (cy - 0.5f - (cy - 0.5f - offsetY) * ratio)
                .coerceIn(-MAX_OFFSET, MAX_OFFSET)
        )
    }

    /** Pans by canvas-fraction deltas (finger movement on screen). */
    fun pan(dx: Float, dy: Float): ZoomState = copy(
        offsetX = (offsetX + dx).coerceIn(-MAX_OFFSET, MAX_OFFSET),
        offsetY = (offsetY + dy).coerceIn(-MAX_OFFSET, MAX_OFFSET)
    )

    /** Double-tap behavior: 1x -> 2.5x anchored at the tap, else back to 1x. */
    fun toggleAt(cx: Float, cy: Float): ZoomState =
        if (isZoomed) IDENTITY else zoomAt(cx, cy, 2.5f)

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 8f
        const val MAX_OFFSET = 2f
        val IDENTITY = ZoomState()
    }
}
