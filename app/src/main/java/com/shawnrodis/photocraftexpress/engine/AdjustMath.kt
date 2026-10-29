// Portions adapted from PhotoCraft (https://github.com/storytold/photocraft), MIT OR Apache-2.0.
// The Levels gamma toe, the natural-cubic-spline Curves LUT, the Vibrance pixel
// model (boost fit + skin-tone damping) and the Saturation grey weights below are
// Kotlin adaptations of photocraft-compose's adjust.rs (crates/compose). See the
// repo-root NOTICE for details and the documented adaptations.

package com.shawnrodis.photocraftexpress.engine

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** One control point of a Curves-style tone curve, coordinates in 0..1. */
data class CurvePoint(val input: Float, val output: Float)

/**
 * Pure per-value / per-pixel adjustment mathematics. No Android types here so
 * the whole engine runs in JVM unit tests.
 *
 * Sliders are -100..100 (Sharpen 0..100), 0 = identity, held in [Adjustments].
 */
object AdjustMath {

    const val LUT_SIZE = 256

    // ------------------------------------------------------------------
    // Levels (adapted from PhotoCraft `levels_gamma` / `levels_float`)
    // ------------------------------------------------------------------

    /** Exponent of the shadow toe in [levelsGamma] (fitted value from PhotoCraft). */
    private const val LEVELS_TOE_POWER = 2.2

    /**
     * Levels' midtone gamma on u in 0..1: u^(1/g), except that above g = 1,
     * where that power curve is infinitely steep at black, the curve starts
     * along a toe a·u − b·u^q of slope a = 2^g that joins the power curve with
     * the same value and slope. Ported from PhotoCraft's `levels_gamma`.
     */
    fun levelsGamma(u: Float, g: Float): Float {
        if (u <= 0f) return 0f
        if (u >= 1f) return 1f
        val ud = u.toDouble()
        val gf = max(g.toDouble(), 0.01)
        val c = 1.0 / gf
        if (gf <= 1.0) return ud.pow(c).toFloat()
        val a = 2.0.pow(gf)
        val q = LEVELS_TOE_POWER
        // Where the toe meets the curve.
        val u1 = ((1.0 - c / q) / (a * (1.0 - 1.0 / q))).pow(1.0 / (1.0 - c))
        if (ud >= u1 || u1 <= 0.0) return ud.pow(c).toFloat()
        val b = (a * u1 - u1.pow(c)) / u1.pow(q)
        // Just above gamma 1 the toe shrinks to nothing and b can blow up;
        // the power curve is the toe's limit there.
        if (!b.isFinite()) return ud.pow(c).toFloat()
        return (a * ud - b * ud.pow(q)).toFloat()
    }

    /**
     * Full Levels transform: input-range stretch, midtone gamma, output range.
     * Adapted from PhotoCraft's `levels_float` structure, clamped to 0..1 for
     * this app's 8-bit pipeline.
     */
    fun levelsTransform(
        v: Float,
        inBlack: Float,
        inWhite: Float,
        gamma: Float,
        outBlack: Float,
        outWhite: Float
    ): Float {
        val range = max(inWhite - inBlack, 1e-6f)
        val u = ((v - inBlack) / range).coerceIn(0f, 1f)
        val t = levelsGamma(u, gamma)
        return (outBlack + t * (outWhite - outBlack)).coerceIn(0f, 1f)
    }

    // ------------------------------------------------------------------
    // Curves (adapted from PhotoCraft `curve_lut`)
    // ------------------------------------------------------------------

    /**
     * Samples a tone curve through [points] into a 256-entry LUT. The curve is
     * a natural cubic spline through the points (it may overshoot between
     * points, as Photoshop's does), constant outside the first/last point and
     * clamped to 0..1. Ported from PhotoCraft's `curve_lut`; PhotoCraft
     * samples 4096 entries for float pipelines, this adaptation samples 256
     * for an 8-bit mobile pipeline.
     */
    fun curveLut(points: List<CurvePoint>): FloatArray {
        val ident = FloatArray(LUT_SIZE) { it / (LUT_SIZE - 1).toFloat() }
        val pts = points
            .filter { it.input.isFinite() && it.output.isFinite() }
            .sortedBy { it.input }
            .fold(mutableListOf<CurvePoint>()) { acc, p ->
                if (acc.isEmpty() || kotlin.math.abs(acc.last().input - p.input) >= 1e-6f) acc.add(p)
                acc
            }
        if (pts.size < 2) return ident
        val n = pts.size
        val x = DoubleArray(n) { pts[it].input.toDouble() }
        val y = DoubleArray(n) { pts[it].output.toDouble() }
        // Second derivatives of the natural spline (tridiagonal solve).
        val m2 = DoubleArray(n)
        if (n > 2) {
            val c = DoubleArray(n)
            val d = DoubleArray(n)
            for (i in 1 until n - 1) {
                val h0 = x[i] - x[i - 1]
                val h1 = x[i + 1] - x[i]
                val a = h0 / 6.0
                val b = (h0 + h1) / 3.0
                val cc = h1 / 6.0
                val r = (y[i + 1] - y[i]) / h1 - (y[i] - y[i - 1]) / h0
                val denom = b - a * c[i - 1]
                c[i] = cc / denom
                d[i] = (r - a * d[i - 1]) / denom
            }
            for (i in n - 2 downTo 1) {
                m2[i] = d[i] - c[i] * m2[i + 1]
            }
        }
        return FloatArray(LUT_SIZE) { k ->
            val xv = k / (LUT_SIZE - 1).toFloat()
            when {
                xv <= pts[0].input -> pts[0].output.coerceIn(0f, 1f)
                xv >= pts[n - 1].input -> pts[n - 1].output.coerceIn(0f, 1f)
                else -> {
                    var i = 0
                    while (i < n - 2 && xv > pts[i + 1].input) i++
                    val h = x[i + 1] - x[i]
                    val t = xv.toDouble()
                    val a = (x[i + 1] - t) / h
                    val b = (t - x[i]) / h
                    val v = a * y[i] + b * y[i + 1] +
                        ((a * a * a - a) * m2[i] + (b * b * b - b) * m2[i + 1]) * h * h / 6.0
                    v.toFloat().coerceIn(0f, 1f)
                }
            }
        }
    }

    /** Linear-interpolated sample of a 256-entry LUT at x in 0..1. */
    fun sampleLut(lut: FloatArray, x: Float): Float {
        val pos = x.coerceIn(0f, 1f) * (LUT_SIZE - 1)
        val i = pos.toInt()
        if (i >= LUT_SIZE - 1) return lut[LUT_SIZE - 1]
        val f = pos - i
        return lut[i] + (lut[i + 1] - lut[i]) * f
    }

    // ------------------------------------------------------------------
    // Vibrance + Saturation (adapted from PhotoCraft `vibrance_px` /
    // `vibrance_lin`)
    // ------------------------------------------------------------------

    /** Positive Vibrance's saturation boost fit coefficients (PhotoCraft). */
    private val VIBRANCE_BOOST = floatArrayOf(0.5001f, 0.0887f, 0.4374f, 0.7573f, -0.8113f, 0.5154f, 2.8026f)

    /** Grey that PhotoCraft's Saturation desaturates towards: 0.288 R + 0.712 G. */
    fun saturationGrey(r: Float, g: Float): Float = 0.288f * r + 0.712f * g

    /**
     * The Vibrance slider (v in -1..1) on one pixel, hue-preserving in HSV
     * terms. Ported from PhotoCraft's `vibrance_lin`. Adaptation: PhotoCraft
     * applies this in linear light through a transfer curve; this app applies
     * it directly to sRGB-encoded values, which keeps the same hue-preserving
     * behaviour with slightly different shadow weighting.
     *
     * Writes the result into [out] (r, g, b in 0..1).
     *
     * [vPow] is the loop-invariant `0.64f * v^1.6`, precomputed once per
     * render by the caller (v0.2.5 lag fix: computing it per pixel cost a
     * `pow` call per pixel). It must equal
     * `0.64f * v.toDouble().pow(1.6).toFloat()` for the given v; it is
     * only read when v > 0, so callers may pass 0f otherwise.
     */
    fun vibranceLin(r: Float, g: Float, b: Float, v: Float, vPow: Float, out: FloatArray) {
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        if (mx <= 0f || mx - mn <= 1e-9f) {
            out[0] = r; out[1] = g; out[2] = b
            return
        }
        val t = 1f - mn / mx
        val shape = t * (1f - t) * (2f - t + t * t) * mx * max(1f - mx, 0f)
        val t2: Float
        val v2: Float
        if (v < 0f) {
            val a = -v
            t2 = t * (1f - a / 4f) * (1f - a + a * t * (1f + t) / 2f)
            v2 = mx - a * shape
        } else {
            val c0 = VIBRANCE_BOOST[0]; val c1 = VIBRANCE_BOOST[1]
            val p = VIBRANCE_BOOST[2]; val q = VIBRANCE_BOOST[3]
            val r0 = VIBRANCE_BOOST[4]; val ra = VIBRANCE_BOOST[5]; val rt = VIBRANCE_BOOST[6]
            val w = vibranceSkin(hsvHue(r, g, b, mx, mn))
            val a = v * (1f - w) + vPow * w
            val boost = (c0 * a + c1 * a * a) *
                t.toDouble().pow(p.toDouble()).toFloat() *
                (1f - t).toDouble().pow(q.toDouble()).toFloat() *
                exp(((r0 + ra * a + rt * (1f - t)) * max(1f - mx, 0f)).toDouble()).toFloat()
            t2 = min(t * (1f + boost), 1f)
            v2 = mx + v / 4f * shape
        }
        val m2 = v2 * (1f - t2)
        val k = (v2 - m2) / (mx - mn)
        out[0] = m2 + (r - mn) * k
        out[1] = m2 + (g - mn) * k
        out[2] = m2 + (b - mn) * k
    }

    /** How much positive Vibrance treats a hue (degrees) as skin (PhotoCraft). */
    private fun vibranceSkin(h: Float): Float = when {
        h <= 30f -> 1f
        h < 45f -> (45f - h) / 15f
        h < 300f -> 0f
        else -> (h - 300f) / 60f
    }

    /** HSV hue in degrees; mx/mn are the channel max/min and mx > mn. */
    private fun hsvHue(r: Float, g: Float, b: Float, mx: Float, mn: Float): Float {
        val d = mx - mn
        val h = when (mx) {
            r -> {
                var m = ((g - b) / d) % 6f
                if (m < 0f) m += 6f
                m
            }
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        }
        return h * 60f
    }

    // ------------------------------------------------------------------
    // Tone LUT: exposure -> contrast -> tonal-anchor curve
    // ------------------------------------------------------------------

    /** Exposure gain: stops = slider/50, applied through a gamma round trip. */
    fun exposureGain(exposure: Float): Float = 2.0.pow((exposure / 50f).toDouble()).toFloat()

    /** GIMP-style contrast slope: tan((c+1)·pi/4), c = slider/100 scaled by 0.9. */
    fun contrastSlope(contrast: Float): Float {
        val c = (contrast / 100f * 0.9f).toDouble()
        return kotlin.math.tan((c + 1.0) * Math.PI / 4.0).toFloat()
    }

    /**
     * The master tone curve value for input x in 0..1 under [adj]:
     * exposure (in a 2.2-gamma linear round trip, following PhotoCraft's
     * linear-light Exposure), then contrast about 0.5, then the
     * Highlights/Shadows/Whites/Blacks anchor curve (a [curveLut] spline
     * through displaced anchors).
     */
    fun toneValue(x: Float, adj: Adjustments): Float {
        var y = x
        if (adj.exposure != 0f) {
            val lin = y.toDouble().pow(2.2) * exposureGain(adj.exposure).toDouble()
            y = lin.pow(1.0 / 2.2).toFloat()
        }
        if (adj.contrast != 0f) {
            y = (y - 0.5f) * contrastSlope(adj.contrast) + 0.5f
        }
        if (adj.hasTonal) {
            y = sampleLut(tonalAnchorLut(adj), y.coerceIn(0f, 1f))
        }
        return y.coerceIn(0f, 1f)
    }

    /** Spline LUT for the Highlights/Shadows/Whites/Blacks anchor curve. */
    fun tonalAnchorLut(adj: Adjustments): FloatArray {
        val s = adj.shadows / 100f
        val h = adj.highlights / 100f
        val w = adj.whites / 100f
        val b = adj.blacks / 100f
        return curveLut(
            listOf(
                CurvePoint(0f, (0.20f * b + 0.05f * s).coerceIn(0f, 1f)),
                CurvePoint(0.25f, (0.25f + 0.16f * s + 0.05f * b).coerceIn(0f, 1f)),
                CurvePoint(0.5f, 0.5f),
                CurvePoint(0.75f, (0.75f + 0.16f * h + 0.05f * w).coerceIn(0f, 1f)),
                CurvePoint(1f, (1f + 0.20f * w).coerceIn(0f, 1f))
            )
        )
    }

    /** The full 256-entry master tone LUT for [adj]. */
    fun buildToneLut(adj: Adjustments): FloatArray =
        FloatArray(LUT_SIZE) { toneValue(it / (LUT_SIZE - 1).toFloat(), adj) }

    /** out(x) = second(first(x)) — LUT composition. */
    fun composeLuts(first: FloatArray, second: FloatArray): FloatArray =
        FloatArray(LUT_SIZE) { sampleLut(second, first[it]) }

    /** Blends a LUT towards identity by amount 0..1 (0 = identity, 1 = full). */
    fun blendLut(lut: FloatArray, amount: Float): FloatArray {
        val a = amount.coerceIn(0f, 1f)
        if (a <= 0f) return FloatArray(LUT_SIZE) { it / (LUT_SIZE - 1).toFloat() }
        if (a >= 1f) return lut
        return FloatArray(LUT_SIZE) { i ->
            val id = i / (LUT_SIZE - 1).toFloat()
            id + (lut[i] - id) * a
        }
    }
}
