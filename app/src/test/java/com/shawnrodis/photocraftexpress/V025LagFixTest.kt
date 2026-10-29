package com.shawnrodis.photocraftexpress

import com.shawnrodis.photocraftexpress.engine.AdjustMath
import com.shawnrodis.photocraftexpress.engine.PixelImage
import com.shawnrodis.photocraftexpress.ui.selectPreviewBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * v0.2.5 lag fix tests:
 * 1. The vibrance `v^1.6` hoist must not change pixel output: the new
 *    [AdjustMath.vibranceLin] (precomputed vPow) is compared against a
 *    verbatim copy of the pre-v0.2.5 implementation (pow per pixel) and
 *    must agree EXACTLY (the hoist reorders no floating-point operation:
 *    old `(0.64f * X) * w` == new `vPow * w` with `vPow = 0.64f * X`).
 * 2. The drag-base selection is pure and testable without Compose.
 */
class V025LagFixTest {

    // --- Reference: pre-v0.2.5 vibranceLin, verbatim (pow per pixel) ---

    private val refBoost = floatArrayOf(0.5001f, 0.0887f, 0.4374f, 0.7573f, -0.8113f, 0.5154f, 2.8026f)

    private fun refSkin(h: Float): Float = when {
        h <= 30f -> 1f
        h < 45f -> (45f - h) / 15f
        h < 300f -> 0f
        else -> (h - 300f) / 60f
    }

    private fun refHue(r: Float, g: Float, b: Float, mx: Float, mn: Float): Float {
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

    private fun vibranceLinReference(r: Float, g: Float, b: Float, v: Float, out: FloatArray) {
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
            val c0 = refBoost[0]; val c1 = refBoost[1]
            val p = refBoost[2]; val q = refBoost[3]
            val r0 = refBoost[4]; val ra = refBoost[5]; val rt = refBoost[6]
            val w = refSkin(refHue(r, g, b, mx, mn))
            val a = v * (1f - w) + 0.64f * v.toDouble().pow(1.6).toFloat() * w
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

    @Test
    fun vibrance_hoist_matchesReferenceExactly() {
        val vs = floatArrayOf(-1f, -0.5f, -0.25f, 0f, 0.25f, 0.5f, 1f)
        val pixels = arrayOf(
            floatArrayOf(0.50f, 0.42f, 0.40f), // muted skin-ish
            floatArrayOf(0.90f, 0.10f, 0.10f), // saturated red
            floatArrayOf(0.10f, 0.80f, 0.20f), // saturated green
            floatArrayOf(0.20f, 0.20f, 0.90f), // saturated blue
            floatArrayOf(0.00f, 0.00f, 0.00f), // black (early return)
            floatArrayOf(0.40f, 0.40f, 0.40f), // grey (early return)
            floatArrayOf(1.00f, 1.00f, 1.00f), // white
            floatArrayOf(0.05f, 0.03f, 0.02f), // near-black colour
            floatArrayOf(0.70f, 0.65f, 0.60f), // warm light
            floatArrayOf(0.30f, 0.55f, 0.50f) // cool mid
        )
        for (v in vs) {
            // Mirrors production: vPow is only read when v > 0.
            val vPow = if (v > 0f) 0.64f * v.toDouble().pow(1.6).toFloat() else 0f
            for (px in pixels) {
                val outNew = FloatArray(3)
                val outRef = FloatArray(3)
                AdjustMath.vibranceLin(px[0], px[1], px[2], v, vPow, outNew)
                vibranceLinReference(px[0], px[1], px[2], v, outRef)
                for (i in 0..2) {
                    assertEquals("v=$v px=${px.toList()} ch=$i", outRef[i], outNew[i], 0f)
                }
            }
        }
        // Deterministic pseudo-random sweep: 512 pixels x every slider value.
        var seed = 12345L
        fun nextChannel(): Float {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return ((seed ushr 40) and 0xFF).toFloat() / 255f
        }
        repeat(512) {
            val px = floatArrayOf(nextChannel(), nextChannel(), nextChannel())
            for (v in vs) {
                val vPow = if (v > 0f) 0.64f * v.toDouble().pow(1.6).toFloat() else 0f
                val outNew = FloatArray(3)
                val outRef = FloatArray(3)
                AdjustMath.vibranceLin(px[0], px[1], px[2], v, vPow, outNew)
                vibranceLinReference(px[0], px[1], px[2], v, outRef)
                for (i in 0..2) {
                    assertEquals("sweep v=$v px=${px.toList()} ch=$i", outRef[i], outNew[i], 0f)
                }
            }
        }
    }

    @Test
    fun dragBase_selectionPicksDragWhileDraggingAndFullOtherwise() {
        val full = PixelImage(IntArray(4), 2, 2)
        val drag = PixelImage(IntArray(4), 1, 1)
        assertSame(drag, selectPreviewBase(true, full, drag))
        assertSame(full, selectPreviewBase(false, full, drag))
    }
}
