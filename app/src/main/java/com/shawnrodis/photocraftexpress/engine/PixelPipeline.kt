package com.shawnrodis.photocraftexpress.engine

import kotlin.math.max
import kotlin.math.min

/** Pixels plus dimensions, the currency of the pure-Kotlin pipeline. */
class PixelImage(val pixels: IntArray, val width: Int, val height: Int)

/**
 * The render pipeline over raw ARGB pixel arrays (no Android types, so it is
 * fully JVM-testable). Stage order:
 *   geometry (rotate -> flips -> crop, crop rect lives in the display frame)
 *   -> Look tone LUT -> Adjust tone LUT (composed into one LUT)
 *   -> mono blend -> temperature/tint gains -> vibrance -> saturation
 *   -> unsharp mask.
 */
object PixelPipeline {

    // ------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------

    fun rotate90Cw(src: IntArray, w: Int, h: Int): PixelImage {
        val out = IntArray(src.size)
        val nw = h
        for (y in 0 until h) {
            val srcRow = y * w
            val nx = h - 1 - y
            for (x in 0 until w) {
                out[x * nw + nx] = src[srcRow + x]
            }
        }
        return PixelImage(out, nw, w)
    }

    fun flipHorizontal(src: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(src.size)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                out[row + (w - 1 - x)] = src[row + x]
            }
        }
        return out
    }

    fun flipVertical(src: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(src.size)
        for (y in 0 until h) {
            val srcRow = y * w
            val dstRow = (h - 1 - y) * w
            System.arraycopy(src, srcRow, out, dstRow, w)
        }
        return out
    }

    /** Crops to a pixel rect, clamped to the image. Rect is [l, t, r, b). */
    fun crop(src: IntArray, w: Int, h: Int, l: Int, t: Int, r: Int, b: Int): PixelImage {
        val cl = l.coerceIn(0, w - 1)
        val ct = t.coerceIn(0, h - 1)
        val cr = r.coerceIn(cl + 1, w)
        val cb = b.coerceIn(ct + 1, h)
        val nw = cr - cl
        val nh = cb - ct
        val out = IntArray(nw * nh)
        for (y in 0 until nh) {
            System.arraycopy(src, (ct + y) * w + cl, out, y * nw, nw)
        }
        return PixelImage(out, nw, nh)
    }

    /** Applies rotation, flips and (optionally) crop from [state]. */
    fun applyGeometry(img: PixelImage, state: EditState, applyCrop: Boolean = true): PixelImage {
        var cur = img
        when (((state.rotation % 360) + 360) % 360) {
            90 -> cur = rotate90Cw(cur.pixels, cur.width, cur.height)
            180 -> {
                cur = rotate90Cw(cur.pixels, cur.width, cur.height)
                cur = rotate90Cw(cur.pixels, cur.width, cur.height)
            }
            270 -> {
                cur = rotate90Cw(cur.pixels, cur.width, cur.height)
                cur = rotate90Cw(cur.pixels, cur.width, cur.height)
                cur = rotate90Cw(cur.pixels, cur.width, cur.height)
            }
        }
        if (state.flipH) cur = PixelImage(flipHorizontal(cur.pixels, cur.width, cur.height), cur.width, cur.height)
        if (state.flipV) cur = PixelImage(flipVertical(cur.pixels, cur.width, cur.height), cur.width, cur.height)
        if (applyCrop && state.crop != CropRect.FULL) {
            val c = state.crop
            cur = crop(
                cur.pixels, cur.width, cur.height,
                (c.left * cur.width).toInt(), (c.top * cur.height).toInt(),
                (c.right * cur.width).toInt().coerceAtLeast(1), (c.bottom * cur.height).toInt().coerceAtLeast(1)
            )
        }
        return cur
    }

    // ------------------------------------------------------------------
    // Tone / colour render
    // ------------------------------------------------------------------

    fun render(img: PixelImage, state: EditState, applyCrop: Boolean = true): PixelImage {
        val geo = applyGeometry(img, state, applyCrop)
        val look = Looks.resolve(Looks.byId(state.lookId), state.lookIntensity)
        val adj = state.adj

        // One composed tone LUT: Look first, then the user's adjustments.
        val lut = AdjustMath.composeLuts(look.toneLut, AdjustMath.buildToneLut(adj))
        val lutR = IntArray(256) { (lut[it] * 255f + 0.5f).toInt().coerceIn(0, 255) }

        val temperature = (adj.temperature + look.temperature).coerceIn(-100f, 100f)
        val tint = (adj.tint + look.tint).coerceIn(-100f, 100f)
        val vibrance = (adj.vibrance + look.vibrance).coerceIn(-100f, 100f)
        val saturation = (adj.saturation + look.saturation).coerceIn(-100f, 100f)
        val mono = look.monoAmount
        val needsPixelStage = temperature != 0f || tint != 0f || vibrance != 0f || saturation != 0f || mono > 0f

        // Temperature: warm pushes R up / B down; tint: positive = magenta
        // (G down), negative = green (G up).
        val tt = temperature / 100f
        val rGain = 1f + 0.18f * tt
        val bGain = 1f - 0.18f * tt
        val gGain = 1f - 0.14f * (tint / 100f)
        val vibV = vibrance / 100f
        val satS = saturation / 100f

        val px = geo.pixels
        val out = IntArray(px.size)
        val vibBuf = FloatArray(3)
        for (i in px.indices) {
            val p = px[i]
            val a = p and -0x1000000
            var r = lutR[(p shr 16) and 0xFF]
            var g = lutR[(p shr 8) and 0xFF]
            var b = lutR[p and 0xFF]
            if (needsPixelStage) {
                var rf = r / 255f
                var gf = g / 255f
                var bf = b / 255f
                if (mono > 0f) {
                    val luma = 0.299f * rf + 0.587f * gf + 0.114f * bf
                    rf += (luma - rf) * mono
                    gf += (luma - gf) * mono
                    bf += (luma - bf) * mono
                }
                if (temperature != 0f || tint != 0f) {
                    rf *= rGain; gf *= gGain; bf *= bGain
                }
                if (vibV != 0f) {
                    AdjustMath.vibranceLin(rf, gf, bf, vibV, vibBuf)
                    rf = vibBuf[0]; gf = vibBuf[1]; bf = vibBuf[2]
                }
                if (satS != 0f) {
                    val grey = AdjustMath.saturationGrey(rf, gf)
                    val f = 1f + satS
                    rf = grey + (rf - grey) * f
                    gf = grey + (gf - grey) * f
                    bf = grey + (bf - grey) * f
                }
                r = (rf.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                g = (gf.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                b = (bf.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            }
            out[i] = a or (r shl 16) or (g shl 8) or b
        }

        val sharpen = max(adj.sharpen, look.sharpen).coerceIn(0f, 100f)
        val result = if (sharpen > 0f) {
            val radius = if (max(geo.width, geo.height) > 2000) 2 else 1
            unsharp(out, geo.width, geo.height, radius, sharpen / 100f * 1.2f)
        } else {
            out
        }
        return PixelImage(result, geo.width, geo.height)
    }

    // ------------------------------------------------------------------
    // Blur + unsharp mask (three-pass box blur ~= gaussian, as in the
    // Toolbox Square Pic blur)
    // ------------------------------------------------------------------

    fun boxBlur(src: IntArray, w: Int, h: Int, radius: Int, passes: Int = 3): IntArray {
        var cur = src
        repeat(passes) {
            cur = boxBlurPass(cur, w, h, radius, horizontal = true)
            cur = boxBlurPass(cur, w, h, radius, horizontal = false)
        }
        return cur
    }

    private fun boxBlurPass(src: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean): IntArray {
        val out = IntArray(src.size)
        val window = 2 * radius + 1
        if (horizontal) {
            for (y in 0 until h) {
                val row = y * w
                var sr = 0; var sg = 0; var sb = 0
                for (k in -radius..radius) {
                    val p = src[row + k.coerceIn(0, w - 1)]
                    sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
                }
                for (x in 0 until w) {
                    out[row + x] = (src[row + x] and -0x1000000) or
                        ((sr / window) shl 16) or ((sg / window) shl 8) or (sb / window)
                    val outP = src[row + (x - radius).coerceIn(0, w - 1)]
                    val inP = src[row + (x + radius + 1).coerceIn(0, w - 1)]
                    sr += ((inP shr 16) and 0xFF) - ((outP shr 16) and 0xFF)
                    sg += ((inP shr 8) and 0xFF) - ((outP shr 8) and 0xFF)
                    sb += (inP and 0xFF) - (outP and 0xFF)
                }
            }
        } else {
            for (x in 0 until w) {
                var sr = 0; var sg = 0; var sb = 0
                for (k in -radius..radius) {
                    val p = src[k.coerceIn(0, h - 1) * w + x]
                    sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
                }
                for (y in 0 until h) {
                    val idx = y * w + x
                    out[idx] = (src[idx] and -0x1000000) or
                        ((sr / window) shl 16) or ((sg / window) shl 8) or (sb / window)
                    val outP = src[(y - radius).coerceIn(0, h - 1) * w + x]
                    val inP = src[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                    sr += ((inP shr 16) and 0xFF) - ((outP shr 16) and 0xFF)
                    sg += ((inP shr 8) and 0xFF) - ((outP shr 8) and 0xFF)
                    sb += (inP and 0xFF) - (outP and 0xFF)
                }
            }
        }
        return out
    }

    /** out = src + amount * (src - blur(src)). Returns a new array. */
    fun unsharp(src: IntArray, w: Int, h: Int, radius: Int, amount: Float): IntArray {
        if (amount <= 0f) return src.copyOf()
        val blurred = boxBlur(src, w, h, radius)
        val out = IntArray(src.size)
        for (i in src.indices) {
            val p = src[i]
            val q = blurred[i]
            val r = ((p shr 16) and 0xFF) + (amount * (((p shr 16) and 0xFF) - ((q shr 16) and 0xFF))).toInt()
            val g = ((p shr 8) and 0xFF) + (amount * (((p shr 8) and 0xFF) - ((q shr 8) and 0xFF))).toInt()
            val b = (p and 0xFF) + (amount * ((p and 0xFF) - (q and 0xFF))).toInt()
            out[i] = (p and -0x1000000) or
                (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        }
        return out
    }
}
