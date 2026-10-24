package com.shawnrodis.photocraftexpress.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A normalized crop rectangle (0..1 on both axes) in the display frame. */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    companion object {
        val FULL = CropRect(0f, 0f, 1f, 1f)
    }
}

/** Which corner of a crop rect a drag handle controls. */
enum class CropCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

object CropGeometry {

    const val MIN_SIZE = 0.08f

    fun clampRect(rect: CropRect, minSize: Float = MIN_SIZE): CropRect {
        var l = rect.left.coerceIn(0f, 1f)
        var t = rect.top.coerceIn(0f, 1f)
        var r = rect.right.coerceIn(0f, 1f)
        var b = rect.bottom.coerceIn(0f, 1f)
        if (r - l < minSize) {
            val c = (l + r) / 2f
            l = (c - minSize / 2f).coerceIn(0f, 1f - minSize)
            r = l + minSize
        }
        if (b - t < minSize) {
            val c = (t + b) / 2f
            t = (c - minSize / 2f).coerceIn(0f, 1f - minSize)
            b = t + minSize
        }
        return CropRect(l, t, r, b)
    }

    /**
     * The largest rect with the given pixel aspect ratio (width/height) that
     * fits inside [rect], centred on [rect]'s centre and clamped into the
     * unit square. Aspect maths happens in normalized display space, where
     * the frame itself has aspect [frameAspect] (pixel w/h), so a normalized
     * rect's pixel aspect is (w * frameAspect) / h.
     */
    fun rectWithAspect(rect: CropRect, aspect: Float, frameAspect: Float): CropRect {
        if (aspect <= 0f || frameAspect <= 0f) return clampRect(rect)
        // Normalized height needed for a rect of normalized width w: h = w * frameAspect / aspect.
        var w = rect.width
        var h = w * frameAspect / aspect
        if (h > rect.height) {
            h = rect.height
            w = h * aspect / frameAspect
        }
        if (h > 1f) { h = 1f; w = h * aspect / frameAspect }
        if (w > 1f) { w = 1f; h = w * frameAspect / aspect }
        val cx = rect.centerX.coerceIn(w / 2f, 1f - w / 2f)
        val cy = rect.centerY.coerceIn(h / 2f, 1f - h / 2f)
        return CropRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    /** Rotating the frame 90° clockwise remaps a display-frame rect. */
    fun rotateRectCw(rect: CropRect): CropRect =
        CropRect(1f - rect.bottom, rect.left, 1f - rect.top, rect.right)

    fun flipHRect(rect: CropRect): CropRect =
        CropRect(1f - rect.right, rect.top, 1f - rect.left, rect.bottom)

    fun flipVRect(rect: CropRect): CropRect =
        CropRect(rect.left, 1f - rect.bottom, rect.right, 1f - rect.top)

    /** Moves the rect by (dx, dy), keeping its size and staying in frame. */
    fun moveRect(rect: CropRect, dx: Float, dy: Float): CropRect {
        val w = rect.width
        val h = rect.height
        val l = (rect.left + dx).coerceIn(0f, 1f - w)
        val t = (rect.top + dy).coerceIn(0f, 1f - h)
        return CropRect(l, t, l + w, t + h)
    }

    /**
     * Drags one corner to (x, y) (normalized display coords). When
     * [aspectNorm] (the target w/h ratio expressed in normalized units) is
     * non-null, the rect keeps that ratio, growing from the opposite corner.
     */
    fun resizeCorner(
        rect: CropRect,
        corner: CropCorner,
        x: Float,
        y: Float,
        aspectNorm: Float? = null
    ): CropRect {
        val fixedX = if (corner == CropCorner.TOP_LEFT || corner == CropCorner.BOTTOM_LEFT) rect.right else rect.left
        val fixedY = if (corner == CropCorner.TOP_LEFT || corner == CropCorner.TOP_RIGHT) rect.bottom else rect.top
        var nx = x.coerceIn(0f, 1f)
        var ny = y.coerceIn(0f, 1f)
        if (aspectNorm != null && aspectNorm > 0f) {
            // Keep ratio: drive from the dominant axis movement.
            val wFromX = abs(nx - fixedX)
            val hFromX = wFromX / aspectNorm
            val hFromY = abs(ny - fixedY)
            val wFromY = hFromY * aspectNorm
            if (wFromX * hFromX > wFromY * hFromY) {
                ny = fixedY + if (ny >= fixedY) hFromX else -hFromX
            } else {
                nx = fixedX + if (nx >= fixedX) wFromY else -wFromY
            }
            nx = nx.coerceIn(0f, 1f); ny = ny.coerceIn(0f, 1f)
        }
        val l = min(fixedX, nx)
        val r = max(fixedX, nx)
        val t = min(fixedY, ny)
        val b = max(fixedY, ny)
        return clampRect(CropRect(l, t, r, b))
    }
}
