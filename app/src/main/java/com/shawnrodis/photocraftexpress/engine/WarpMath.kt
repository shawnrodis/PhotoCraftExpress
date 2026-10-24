package com.shawnrodis.photocraftexpress.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mesh warps for Reshape (liquify brush) and Face (feature scaling).
 * Everything works in normalized image coordinates (0..1) and is
 * inverse-mapped: for each output pixel we compute the source coordinate
 * and bilinear-sample it. Identity warp returns the input unchanged.
 *
 * Brush ops are applied in list order; a RESTORE op deletes earlier ops
 * whose centre falls inside its radius (that is how Express's restore
 * brush erases previous liquify work).
 */
object WarpMath {

    enum class BrushMode { PUSH, SWELL, TWIRL, RESTORE }

    /** One liquify brush operation, in normalized coordinates. */
    data class BrushOp(
        val mode: BrushMode,
        val cx: Float, val cy: Float,
        val radius: Float, // normalized (fraction of image width)
        val strength: Float, // 0..1
        val dx: Float = 0f, // push drag vector (normalized)
        val dy: Float = 0f
    )

    /** One elliptical scale operation (Face): scale a region around a centre. */
    data class ScaleOp(
        val cx: Float, val cy: Float,
        val rx: Float, val ry: Float, // normalized radii
        val scaleX: Float, // 1 = identity
        val scaleY: Float
    )

    private fun falloff(dist: Float, radius: Float): Float {
        if (radius <= 0f) return 0f
        val t = (dist / radius).coerceIn(0f, 1f)
        // Smooth cosine falloff: 1 at centre, 0 at the edge.
        return (0.5f * (1f + cos(PI.toFloat() * t)))
    }

    /** Drops ops erased by later RESTORE ops (list order = paint order). */
    fun resolveOps(ops: List<BrushOp>): List<BrushOp> {
        val active = mutableListOf<BrushOp>()
        for (op in ops) {
            if (op.mode == BrushMode.RESTORE) {
                active.removeAll { existing ->
                    val dx = existing.cx - op.cx
                    val dy = existing.cy - op.cy
                    sqrt(dx * dx + dy * dy) <= op.radius
                }
            } else {
                active.add(op)
            }
        }
        return active
    }

    /**
     * Inverse-maps one output point through the warp field: returns the
     * source point that should be sampled for output point (x, y).
     * Coordinates are normalized; [aspect] is image width/height, used so
     * radii behave the same in both axes.
     */
    fun inverseMap(
        x: Float, y: Float,
        brushOps: List<BrushOp>,
        scaleOps: List<ScaleOp>,
        aspect: Float
    ): Pair<Float, Float> {
        var sx = x
        var sy = y
        // Elliptical scales first (face features), then brush warps.
        for (op in scaleOps) {
            val dx = (sx - op.cx) * aspect
            val dy = sy - op.cy
            val rx = op.rx * aspect
            val d = sqrt((dx / rx) * (dx / rx) + (dy / op.ry) * (dy / op.ry))
            if (d < 1f) {
                // Feather over the outer 35% of the ellipse.
                val w = if (d < 0.65f) 1f else (1f - d) / 0.35f
                val ix = op.cx + (sx - op.cx) / (1f + (op.scaleX - 1f) * w)
                val iy = op.cy + (sy - op.cy) / (1f + (op.scaleY - 1f) * w)
                sx = ix
                sy = iy
            }
        }
        for (op in brushOps) {
            val dx = (sx - op.cx) * aspect
            val dy = sy - op.cy
            val dist = sqrt(dx * dx + dy * dy) / aspect
            val f = falloff(dist, op.radius) * op.strength
            if (f <= 0f) continue
            when (op.mode) {
                BrushMode.PUSH -> {
                    sx -= op.dx * f
                    sy -= op.dy * f
                }
                BrushMode.SWELL -> {
                    // Swell makes the region bigger: sample closer to centre.
                    val s = 1f + f * 0.8f
                    sx = op.cx + (sx - op.cx) / s
                    sy = op.cy + (sy - op.cy) / s
                }
                BrushMode.TWIRL -> {
                    val ang = -f * 1.8f // radians; negative = clockwise twirl
                    val c = cos(ang)
                    val s = sin(ang)
                    val rx = sx - op.cx
                    val ry = sy - op.cy
                    sx = op.cx + rx * c - ry * s
                    sy = op.cy + rx * s + ry * c
                }
                BrushMode.RESTORE -> Unit // resolved out before mapping
            }
        }
        return sx to sy
    }

    fun sampleBilinear(src: IntArray, w: Int, h: Int, nx: Float, ny: Float): Int {
        val fx = (nx * w - 0.5f).coerceIn(0f, (w - 1).toFloat())
        val fy = (ny * h - 0.5f).coerceIn(0f, (h - 1).toFloat())
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val x1 = minOf(x0 + 1, w - 1)
        val y1 = minOf(y0 + 1, h - 1)
        val tx = fx - x0
        val ty = fy - y0
        fun chan(shift: Int): Int {
            val c00 = (src[y0 * w + x0] shr shift) and 0xFF
            val c10 = (src[y0 * w + x1] shr shift) and 0xFF
            val c01 = (src[y1 * w + x0] shr shift) and 0xFF
            val c11 = (src[y1 * w + x1] shr shift) and 0xFF
            val top = c00 + (c10 - c00) * tx
            val bot = c01 + (c11 - c01) * tx
            return (top + (bot - top) * ty).toInt().coerceIn(0, 255)
        }
        val a = (src[y0 * w + x0] and -0x1000000)
        return a or (chan(16) shl 16) or (chan(8) shl 8) or chan(0)
    }

    /** Applies brush + scale warps to a whole image. Identity if no ops. */
    fun warp(
        src: IntArray, w: Int, h: Int,
        brushOps: List<BrushOp>,
        scaleOps: List<ScaleOp>
    ): IntArray {
        val resolved = resolveOps(brushOps)
        if (resolved.isEmpty() && scaleOps.all { it.scaleX == 1f && it.scaleY == 1f }) {
            return src.copyOf()
        }
        val aspect = w.toFloat() / h.toFloat()
        val out = IntArray(src.size)
        for (y in 0 until h) {
            val ny = (y + 0.5f) / h
            for (x in 0 until w) {
                val nx = (x + 0.5f) / w
                val (sx, sy) = inverseMap(nx, ny, resolved, scaleOps, aspect)
                out[y * w + x] = sampleBilinear(src, w, h, sx, sy)
            }
        }
        return out
    }

    // ------------------------------------------------------------------
    // Perspective (homography) for the Crop TRANSFORM tab.
    // ------------------------------------------------------------------

    /** Four corners in normalized coordinates, in TL/TR/BR/BL order. */
    data class Quad(
        val x1: Float, val y1: Float,
        val x2: Float, val y2: Float,
        val x3: Float, val y3: Float,
        val x4: Float, val y4: Float
    ) {
        val isIdentity: Boolean
            get() = x1 == 0f && y1 == 0f && x2 == 1f && y2 == 0f &&
                x3 == 1f && y3 == 1f && x4 == 0f && y4 == 1f

        companion object {
            val IDENTITY = Quad(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        }

        fun corner(i: Int): Pair<Float, Float> = when (i) {
            0 -> x1 to y1
            1 -> x2 to y2
            2 -> x3 to y3
            else -> x4 to y4
        }
    }

    /**
     * Solves for the 3x3 homography mapping the unit square onto [dst]
     * (corners TL/TR/BR/BL), via Gaussian elimination on the 8x8 system.
     * Returns the matrix as 9 floats, row-major, with h[8] = 1.
     */
    fun homographyUnitTo(dst: Quad): FloatArray {
        // Equations: for each corner (sx, sy) -> (dx, dy):
        //   dx = (a*sx + b*sy + c) / (g*sx + h*sy + 1)
        //   dy = (d*sx + e*sy + f) / (g*sx + h*sy + 1)
        val src = arrayOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f)
        val m = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val (sx, sy) = src[i]
            val (dx, dy) = dst.corner(i)
            m[2 * i][0] = sx.toDouble(); m[2 * i][1] = sy.toDouble(); m[2 * i][2] = 1.0
            m[2 * i][6] = (-dx * sx).toDouble(); m[2 * i][7] = (-dx * sy).toDouble()
            m[2 * i][8] = dx.toDouble()
            m[2 * i + 1][3] = sx.toDouble(); m[2 * i + 1][4] = sy.toDouble(); m[2 * i + 1][5] = 1.0
            m[2 * i + 1][6] = (-dy * sx).toDouble(); m[2 * i + 1][7] = (-dy * sy).toDouble()
            m[2 * i + 1][8] = dy.toDouble()
        }
        // Gaussian elimination with partial pivoting.
        for (col in 0 until 8) {
            var piv = col
            for (row in col + 1 until 8) {
                if (kotlin.math.abs(m[row][col]) > kotlin.math.abs(m[piv][col])) piv = row
            }
            val tmp = m[col]; m[col] = m[piv]; m[piv] = tmp
            val d = m[col][col]
            if (kotlin.math.abs(d) < 1e-12) return floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
            for (row in 0 until 8) {
                if (row == col) continue
                val f = m[row][col] / d
                for (k in col until 9) m[row][k] -= f * m[col][k]
            }
        }
        val h = FloatArray(9)
        for (i in 0 until 8) h[i] = (m[i][8] / m[i][i]).toFloat()
        h[8] = 1f
        return h
    }

    fun invertHomography(h: FloatArray): FloatArray {
        // (Kotlin destructuring only goes to component5; index directly.)
        val a = h[0]
        val b = h[1]
        val c = h[2]
        val d = h[3]
        val e = h[4]
        val f = h[5]
        val g = h[6]
        val hh = h[7]
        val i = h[8]
        val det = a * (e * i - f * hh) - b * (d * i - f * g) + c * (d * hh - e * g)
        if (kotlin.math.abs(det) < 1e-12f) return floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        val inv = 1f / det
        return floatArrayOf(
            (e * i - f * hh) * inv, (c * hh - b * i) * inv, (b * f - c * e) * inv,
            (f * g - d * i) * inv, (a * i - c * g) * inv, (c * d - a * f) * inv,
            (d * hh - e * g) * inv, (b * g - a * hh) * inv, (a * e - b * d) * inv
        )
    }

    /** Applies a perspective warp: output pixel (nx, ny) samples via H^-1. */
    fun perspectiveWarp(src: IntArray, w: Int, h: Int, quad: Quad): IntArray {
        if (quad.isIdentity) return src.copyOf()
        val hMat = homographyUnitTo(quad)
        val inv = invertHomography(hMat)
        val out = IntArray(src.size)
        for (y in 0 until h) {
            val ny = (y + 0.5f) / h
            for (x in 0 until w) {
                val nx = (x + 0.5f) / w
                // Map output point back through the inverse homography.
                val sx = inv[0] * nx + inv[1] * ny + inv[2]
                val sy = inv[3] * nx + inv[4] * ny + inv[5]
                val sw = inv[6] * nx + inv[7] * ny + inv[8]
                val px = (sx / sw).coerceIn(0f, 1f)
                val py = (sy / sw).coerceIn(0f, 1f)
                out[y * w + x] = sampleBilinear(src, w, h, px, py)
            }
        }
        return out
    }

    /** Forward-maps a normalized point through the unit-square -> quad warp. */
    fun forwardPoint(quad: Quad, x: Float, y: Float): Pair<Float, Float> {
        if (quad.isIdentity) return x to y
        val h = homographyUnitTo(quad)
        val sx = h[0] * x + h[1] * y + h[2]
        val sy = h[3] * x + h[4] * y + h[5]
        val sw = h[6] * x + h[7] * y + h[8]
        return (sx / sw) to (sy / sw)
    }
}
