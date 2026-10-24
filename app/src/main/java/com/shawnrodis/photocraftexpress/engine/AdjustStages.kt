package com.shawnrodis.photocraftexpress.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The deeper Express adjustment stages: Texture, Clarity, Dehaze, noise
 * reduction, Fade, Grain and Smooth. All operate on raw ARGB arrays so they
 * are fully JVM-testable. These are documented approximations of the
 * commercial originals (which are proprietary): Texture/Clarity are
 * unsharp-style local-contrast stages at different radii, Dehaze is a
 * shadow-contrast + saturation lift, and the noise stages are
 * edge-aware blur blends. Every stage is identity at 0.
 */
object AdjustStages {

    private fun r(p: Int) = (p shr 16) and 0xFF
    private fun g(p: Int) = (p shr 8) and 0xFF
    private fun b(p: Int) = p and 0xFF
    private fun pack(a: Int, r: Int, g: Int, b: Int) =
        a or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** Local contrast: out = src + k * (src - blur(src, radius)). */
    fun localContrast(src: IntArray, w: Int, h: Int, radius: Int, k: Float): IntArray {
        if (k == 0f) return src.copyOf()
        val blurred = PixelPipeline.boxBlur(src, w, h, radius, passes = 1)
        val out = IntArray(src.size)
        for (i in src.indices) {
            val p = src[i]
            val q = blurred[i]
            out[i] = pack(
                p and -0x1000000,
                (r(p) + k * (r(p) - r(q))).toInt(),
                (g(p) + k * (g(p) - g(q))).toInt(),
                (b(p) + k * (b(p) - b(q))).toInt()
            )
        }
        return out
    }

    /** Texture: fine detail (small radius). Amount -100..100. */
    fun texture(src: IntArray, w: Int, h: Int, amount: Float): IntArray {
        val radius = max(1, min(w, h) / 400)
        return localContrast(src, w, h, radius, amount / 100f * 0.9f)
    }

    /** Clarity: midtone local contrast (larger radius), weighted to midtones. */
    fun clarity(src: IntArray, w: Int, h: Int, amount: Float): IntArray {
        if (amount == 0f) return src.copyOf()
        val radius = max(2, min(w, h) / 120)
        val blurred = PixelPipeline.boxBlur(src, w, h, radius, passes = 1)
        val k = amount / 100f * 0.7f
        val out = IntArray(src.size)
        for (i in src.indices) {
            val p = src[i]
            val q = blurred[i]
            // Midtone weight: full effect at luma 0.5, fading to 0 at 0/1.
            val luma = (0.299f * r(p) + 0.587f * g(p) + 0.114f * b(p)) / 255f
            val mw = 1f - abs(luma - 0.5f) * 2f
            val kk = k * (0.35f + 0.65f * mw)
            out[i] = pack(
                p and -0x1000000,
                (r(p) + kk * (r(p) - r(q))).toInt(),
                (g(p) + kk * (g(p) - g(q))).toInt(),
                (b(p) + kk * (b(p) - b(q))).toInt()
            )
        }
        return out
    }

    /**
     * Dehaze approximation: lifts shadow contrast and adds saturation in
     * dark areas, where haze lives. Amount -100..100 (negative adds haze).
     */
    fun dehaze(src: IntArray, w: Int, h: Int, amount: Float): IntArray {
        if (amount == 0f) return src.copyOf()
        val k = amount / 100f
        val out = IntArray(src.size)
        for (i in src.indices) {
            val p = src[i]
            val rf = r(p) / 255f
            val gf = g(p) / 255f
            val bf = b(p) / 255f
            val luma = 0.299f * rf + 0.587f * gf + 0.114f * bf
            // Shadow-weighted contrast: darken darks, brighten brights.
            val contrast = 1f + 0.35f * k
            val darkW = (1f - luma).coerceIn(0f, 1f)
            fun curve(v: Float): Float {
                val c = ((v - 0.5f) * contrast + 0.5f).coerceIn(0f, 1f)
                // Extra saturation in dark areas: pull away from luma.
                val sat = 1f + 0.35f * k * darkW
                return (luma + (c - luma) * sat).coerceIn(0f, 1f)
            }
            out[i] = pack(
                p and -0x1000000,
                (curve(rf) * 255f + 0.5f).toInt(),
                (curve(gf) * 255f + 0.5f).toInt(),
                (curve(bf) * 255f + 0.5f).toInt()
            )
        }
        return out
    }

    /**
     * Colour noise reduction: blurs chroma only, keeping luma sharp.
     * Amount 0..100 (blend toward the chroma-blurred image).
     */
    fun reduceColorNoise(src: IntArray, w: Int, h: Int, amount: Float): IntArray {
        if (amount <= 0f) return src.copyOf()
        val radius = max(1, min(w, h) / 500)
        val blurred = PixelPipeline.boxBlur(src, w, h, radius, passes = 1)
        val k = (amount / 100f).coerceIn(0f, 1f)
        val out = IntArray(src.size)
        for (i in src.indices) {
            val p = src[i]
            val q = blurred[i]
            // Keep luma from src, take chroma (colour difference) from blur.
            val y = 0.299f * r(p) + 0.587f * g(p) + 0.114f * b(p)
            val cr = (r(q) - (0.299f * r(q) + 0.587f * g(q) + 0.114f * b(q)))
            val cg = (g(q) - (0.299f * r(q) + 0.587f * g(q) + 0.114f * b(q)))
            val cb = (b(q) - (0.299f * r(q) + 0.587f * g(q) + 0.114f * b(q)))
            val rr = y + cr
            val gg = y + cg
            val bb = y + cb
            out[i] = pack(
                p and -0x1000000,
                (r(p) + (rr - r(p)) * k).toInt(),
                (g(p) + (gg - g(p)) * k).toInt(),
                (b(p) + (bb - b(p)) * k).toInt()
            )
        }
        return out
    }

    /**
     * Luminance noise reduction: edge-aware blend toward the blurred image.
     * Flat areas smooth out; strong edges keep their detail. Amount 0..100.
     */
    fun reduceLuminanceNoise(src: IntArray, w: Int, h: Int, amount: Float): IntArray {
        if (amount <= 0f) return src.copyOf()
        val radius = max(1, min(w, h) / 500)
        val blurred = PixelPipeline.boxBlur(src, w, h, radius, passes = 1)
        val k = (amount / 100f).coerceIn(0f, 1f)
        val out = IntArray(src.size)
        for (i in src.indices) {
            val p = src[i]
            val q = blurred[i]
            val d = (abs(r(p) - r(q)) + abs(g(p) - g(q)) + abs(b(p) - b(q))) / 3f
            val wgt = k * exp(-d / 28f)
            out[i] = pack(
                p and -0x1000000,
                (r(p) + (r(q) - r(p)) * wgt).toInt(),
                (g(p) + (g(q) - g(p)) * wgt).toInt(),
                (b(p) + (b(q) - b(p)) * wgt).toInt()
            )
        }
        return out
    }

    /**
     * Fade: lifts the black point and slightly desaturates, the matte-film
     * look. Amount -100..100 (negative deepens blacks instead).
     */
    fun fade(src: IntArray, amount: Float): IntArray {
        if (amount == 0f) return src.copyOf()
        val k = amount / 100f
        val out = IntArray(src.size)
        for (i in src.indices) {
            val p = src[i]
            val rf = r(p) / 255f
            val gf = g(p) / 255f
            val bf = b(p) / 255f
            val luma = 0.299f * rf + 0.587f * gf + 0.114f * bf
            fun lift(v: Float): Float {
                // Black lift + slight white compression + gentle desat.
                var c = v * (1f - 0.14f * k) + 0.10f * k
                c = luma + (c - luma) * (1f - 0.25f * k)
                return c.coerceIn(0f, 1f)
            }
            out[i] = pack(
                p and -0x1000000,
                (lift(rf) * 255f + 0.5f).toInt(),
                (lift(gf) * 255f + 0.5f).toInt(),
                (lift(bf) * 255f + 0.5f).toInt()
            )
        }
        return out
    }

    /**
     * Film grain: deterministic per-pixel luminance noise (same hash for
     * every channel, so the grain reads as luminance grain, not colour
     * speckle). Amount 0..100. Deterministic: same input, same grain.
     */
    fun grain(src: IntArray, amount: Float): IntArray {
        if (amount <= 0f) return src.copyOf()
        val k = amount / 100f * 14f
        val out = IntArray(src.size)
        for (i in src.indices) {
            val p = src[i]
            // Integer hash of the pixel index -> [-1, 1).
            var h = i * 2654435761L.toInt()
            h = h xor (h ushr 13)
            h *= 1274126177
            h = h xor (h ushr 16)
            val n = ((h and 0x7FFFFFFF) / 1073741824f - 1f) * k
            out[i] = pack(
                p and -0x1000000,
                (r(p) + n).toInt(),
                (g(p) + n).toInt(),
                (b(p) + n).toInt()
            )
        }
        return out
    }

    /**
     * Smooth (global, not face-masked, in v0.2.0): edge-aware smoothing,
     * the same blend as luminance noise reduction but with a wider radius
     * and a skin-tone-friendly strength curve. Amount 0..100.
     */
    fun smooth(src: IntArray, w: Int, h: Int, amount: Float): IntArray {
        if (amount <= 0f) return src.copyOf()
        val radius = max(2, min(w, h) / 200)
        val blurred = PixelPipeline.boxBlur(src, w, h, radius, passes = 2)
        val k = (amount / 100f).coerceIn(0f, 1f)
        val out = IntArray(src.size)
        for (i in src.indices) {
            val p = src[i]
            val q = blurred[i]
            val d = (abs(r(p) - r(q)) + abs(g(p) - g(q)) + abs(b(p) - b(q))) / 3f
            val wgt = k * exp(-d / 20f)
            out[i] = pack(
                p and -0x1000000,
                (r(p) + (r(q) - r(p)) * wgt).toInt(),
                (g(p) + (g(q) - g(p)) * wgt).toInt(),
                (b(p) + (b(q) - b(p)) * wgt).toInt()
            )
        }
        return out
    }

    /**
     * Radial blur: the photo blurred inside an ellipse (centre + radii),
     * feathering out to sharp at the edge. Amount 0..100 scales the blur
     * radius; feather 0..100 widens the soft edge; roundness -100..100
     * morphs the ellipse from rectangular to circular.
     */
    fun radialBlur(src: IntArray, w: Int, h: Int, blur: BlurFx): IntArray {
        if (blur.mode != BlurMode.RADIAL || blur.amount == 0f) return src.copyOf()
        val radius = max(1, (blur.amount / 100f * min(w, h) / 40f).toInt())
        val blurred = PixelPipeline.boxBlur(src, w, h, radius, passes = 2)
        val fea = (blur.feather / 100f).coerceIn(0f, 1f)
        val rnd = (blur.roundness / 100f).coerceIn(-1f, 1f)
        val t = (rnd + 1f) / 2f
        val out = IntArray(src.size)
        val cx = blur.cx * w
        val cy = blur.cy * h
        val rx = max(1f, blur.rx * w)
        val ry = max(1f, blur.ry * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val nx = (x - cx) / rx
                val ny = (y - cy) / ry
                val circ = sqrt(nx * nx + ny * ny)
                val rect = max(abs(nx), abs(ny))
                val dist = rect * (1f - t) + circ * t
                val m = if (fea <= 0.01f) {
                    if (dist <= 1f) 1f else 0f
                } else {
                    ((1f + fea - dist) / (2f * fea)).coerceIn(0f, 1f)
                }
                val i = y * w + x
                val p = src[i]
                val q = blurred[i]
                out[i] = pack(
                    p and -0x1000000,
                    (r(p) + (r(q) - r(p)) * m).toInt(),
                    (g(p) + (g(q) - g(p)) * m).toInt(),
                    (b(p) + (b(q) - b(p)) * m).toInt()
                )
            }
        }
        return out
    }

    /**
     * Vignette: darkens (positive amount) or brightens (negative) toward
     * the frame edges. [midPoint] 0..100 is where the falloff starts,
     * [feather] 0..100 its softness, [roundness] -100..100 morphs the
     * falloff shape from rectangular (-100) to circular (+100).
     *
     * The falloff curve is a smoothstep (f²·(3−2f)) over the feather
     * zone, not a bare quadratic: its slope is zero at both ends of the
     * zone, so there is no visible seam where the falloff starts or
     * ends and no banding steps in between. Channels are rounded, not
     * truncated, for the same reason. This exact function backs both
     * the live preview and the export render.
     */
    fun vignette(
        src: IntArray, w: Int, h: Int,
        amount: Float, midPoint: Float, feather: Float, roundness: Float
    ): IntArray {
        if (amount == 0f) return src.copyOf()
        val k = amount / 100f
        val mid = (midPoint / 100f).coerceIn(0f, 1f)
        val fea = (feather / 100f).coerceIn(0.01f, 1f)
        val rnd = (roundness / 100f).coerceIn(-1f, 1f)
        val out = IntArray(src.size)
        val cx = w / 2f
        val cy = h / 2f
        for (y in 0 until h) {
            val ny = (y - cy) / cy
            for (x in 0 until w) {
                val nx = (x - cx) / cx
                // Roundness -1..1 blends rectangular (max-norm) distance
                // into circular distance: -1 = rectangular, +1 = circular.
                val circ = sqrt(nx * nx + ny * ny)
                val rect = max(abs(nx), abs(ny))
                val t = (rnd + 1f) / 2f
                val dist = rect * (1f - t) + circ * t
                val lin = ((dist - mid) / fea).coerceIn(0f, 1f)
                val fall = lin * lin * (3f - 2f * lin)
                val f = 1f - k * fall
                val i = y * w + x
                val p = src[i]
                out[i] = pack(
                    p and -0x1000000,
                    (r(p) * f + 0.5f).toInt(),
                    (g(p) * f + 0.5f).toInt(),
                    (b(p) * f + 0.5f).toInt()
                )
            }
        }
        return out
    }

    /** The vignette multiplier at one normalized distance (for tests). */
    fun vignetteFactor(
        dist: Float, amount: Float, midPoint: Float, feather: Float
    ): Float {
        val k = amount / 100f
        val mid = (midPoint / 100f).coerceIn(0f, 1f)
        val fea = (feather / 100f).coerceIn(0.01f, 1f)
        val lin = ((dist - mid) / fea).coerceIn(0f, 1f)
        return 1f - k * lin * lin * (3f - 2f * lin)
    }
}
