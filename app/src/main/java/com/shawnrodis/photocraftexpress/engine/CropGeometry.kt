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
     * Drags one edge to (x, y) (normalized display coords). Without an
     * aspect lock only that edge moves; with [aspectNorm] (the target
     * w/h ratio expressed in normalized units) the opposite dimension
     * follows the ratio, centred on the rect's old centre, exactly as
     * the corner drag does.
     */
    fun resizeEdge(
        rect: CropRect,
        edge: CropEdge,
        x: Float,
        y: Float,
        aspectNorm: Float? = null
    ): CropRect {
        val nx = x.coerceIn(0f, 1f)
        val ny = y.coerceIn(0f, 1f)
        if (aspectNorm != null && aspectNorm > 0f) {
            return when (edge) {
                CropEdge.LEFT, CropEdge.RIGHT -> {
                    val fixedX = if (edge == CropEdge.LEFT) rect.right else rect.left
                    val w = abs(nx - fixedX)
                    val h = (w / aspectNorm).coerceAtMost(1f)
                    val cy = rect.centerY.coerceIn(h / 2f, 1f - h / 2f)
                    clampRect(CropRect(min(fixedX, nx), cy - h / 2f, max(fixedX, nx), cy + h / 2f))
                }
                CropEdge.TOP, CropEdge.BOTTOM -> {
                    val fixedY = if (edge == CropEdge.TOP) rect.bottom else rect.top
                    val h = abs(ny - fixedY)
                    val w = (h * aspectNorm).coerceAtMost(1f)
                    val cx = rect.centerX.coerceIn(w / 2f, 1f - w / 2f)
                    clampRect(CropRect(cx - w / 2f, min(fixedY, ny), cx + w / 2f, max(fixedY, ny)))
                }
            }
        }
        return clampRect(
            when (edge) {
                CropEdge.LEFT -> CropRect(nx, rect.top, rect.right, rect.bottom)
                CropEdge.RIGHT -> CropRect(rect.left, rect.top, nx, rect.bottom)
                CropEdge.TOP -> CropRect(rect.left, ny, rect.right, rect.bottom)
                CropEdge.BOTTOM -> CropRect(rect.left, rect.top, rect.right, ny)
            }
        )
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

/** Which edge of a crop rect a drag handle controls. */
enum class CropEdge { LEFT, RIGHT, TOP, BOTTOM }

/** What a crop drag grabbed: nothing, a corner, an edge, or the inside (move). */
sealed interface CropDragKind {
    object None : CropDragKind
    object Move : CropDragKind
    data class Corner(val corner: CropCorner) : CropDragKind
    data class Edge(val edge: CropEdge) : CropDragKind
}

/**
 * The fitted image frame inside a canvas box (ContentScale.Fit maths):
 * the image occupies [offX, offX + dispW] x [offY, offY + dispH] inside a
 * [canvasW] x [canvasH] box. Pure data so the screen<->normalized
 * mapping below is unit-testable without composing anything.
 */
data class FitFrame(
    val canvasW: Float,
    val canvasH: Float,
    val offX: Float,
    val offY: Float,
    val dispW: Float,
    val dispH: Float
)

/** Fit frame of an [imageW] x [imageH] image inside a canvas box. */
fun fitFrame(canvasW: Float, canvasH: Float, imageW: Int, imageH: Int): FitFrame {
    if (canvasW <= 0f || canvasH <= 0f || imageW <= 0 || imageH <= 0) {
        return FitFrame(canvasW, canvasH, 0f, 0f, 0f, 0f)
    }
    val scale = minOf(canvasW / imageW, canvasH / imageH)
    val dispW = imageW * scale
    val dispH = imageH * scale
    return FitFrame(canvasW, canvasH, (canvasW - dispW) / 2f, (canvasH - dispH) / 2f, dispW, dispH)
}

/**
 * Unzoomed canvas fractions (0..1 across the canvas box) -> normalized
 * image coords. NOT clamped: callers that need bounds use clampRect or
 * coerceIn themselves, and the hit test must see out-of-frame points
 * as outside, not clamped onto the rect's edge.
 */
fun fractionToNorm(fit: FitFrame, fx: Float, fy: Float): Pair<Float, Float> =
    ((fx * fit.canvasW - fit.offX) / fit.dispW) to
        ((fy * fit.canvasH - fit.offY) / fit.dispH)

/** Normalized image coords -> unzoomed canvas fractions. */
fun normToFraction(fit: FitFrame, nx: Float, ny: Float): Pair<Float, Float> =
    ((fit.offX + nx * fit.dispW) / fit.canvasW) to
        ((fit.offY + ny * fit.dispH) / fit.canvasH)

/** Normalized image coords -> screen px inside the canvas box, through [zoom]. */
fun normToScreen(fit: FitFrame, zoom: ZoomState, nx: Float, ny: Float): Pair<Float, Float> {
    val (fx, fy) = normToFraction(fit, nx, ny)
    val (zx, zy) = zoom.forward(fx, fy)
    return (zx * fit.canvasW) to (zy * fit.canvasH)
}

/** Screen px inside the canvas box -> normalized image coords, through [zoom]. */
fun screenToNorm(fit: FitFrame, zoom: ZoomState, px: Float, py: Float): Pair<Float, Float> {
    val (ux, uy) = zoom.inverse(px / fit.canvasW, py / fit.canvasH)
    return fractionToNorm(fit, ux, uy)
}

/**
 * Screen-space crop hit test (v0.2.3): handle positions are computed in
 * screen px through the zoom, and compared against a constant screen
 * radius, so a handle stays exactly as grabbable at 8x zoom as at 1x.
 * Corners win ties over edges; a point inside the rect (and inside the
 * frame) moves it; anything else grabs nothing. The old overlay tested
 * corners only, in unzoomed-local coords, with no edge handles at all —
 * so edge drags either moved the whole rect or did nothing.
 */
fun cropHandleAt(
    rect: CropRect,
    fit: FitFrame,
    zoom: ZoomState,
    px: Float,
    py: Float,
    radiusPx: Float
): CropDragKind {
    if (fit.dispW <= 0f || fit.dispH <= 0f) return CropDragKind.None
    val corners = listOf(
        CropCorner.TOP_LEFT to (rect.left to rect.top),
        CropCorner.TOP_RIGHT to (rect.right to rect.top),
        CropCorner.BOTTOM_LEFT to (rect.left to rect.bottom),
        CropCorner.BOTTOM_RIGHT to (rect.right to rect.bottom)
    )
    val edges = listOf(
        CropEdge.LEFT to (rect.left to rect.centerY),
        CropEdge.RIGHT to (rect.right to rect.centerY),
        CropEdge.TOP to (rect.centerX to rect.top),
        CropEdge.BOTTOM to (rect.centerX to rect.bottom)
    )
    var best: CropDragKind? = null
    var bestDist = Float.MAX_VALUE
    fun consider(kind: CropDragKind, nx: Float, ny: Float) {
        val (sx, sy) = normToScreen(fit, zoom, nx, ny)
        val d = kotlin.math.hypot(sx - px, sy - py)
        // Strictly-smaller wins, so corners (checked first) win ties.
        if (d <= radiusPx && d < bestDist) {
            bestDist = d
            best = kind
        }
    }
    for ((corner, xy) in corners) consider(CropDragKind.Corner(corner), xy.first, xy.second)
    for ((edge, xy) in edges) consider(CropDragKind.Edge(edge), xy.first, xy.second)
    if (best != null) return best!!
    val (nx, ny) = screenToNorm(fit, zoom, px, py)
    return if (nx in rect.left..rect.right && ny in rect.top..rect.bottom &&
        nx in 0f..1f && ny in 0f..1f
    ) {
        CropDragKind.Move
    } else {
        CropDragKind.None
    }
}
