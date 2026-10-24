package com.shawnrodis.photocraftexpress.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * HSL colour work for Split Tone and Color Mix. Hue is degrees 0..360,
 * saturation and lightness are 0..1. All functions are pure and
 * JVM-testable.
 */
object ColorMath {

    /** RGB (0..1) -> HSL (h degrees, s, l). */
    fun rgbToHsl(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        if (mx == mn) return Triple(0f, 0f, l)
        val d = mx - mn
        val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
        val h = when (mx) {
            r -> ((g - b) / d + if (g < b) 6f else 0f) * 60f
            g -> ((b - r) / d + 2f) * 60f
            else -> ((r - g) / d + 4f) * 60f
        }
        return Triple(h, s, l)
    }

    /** HSL -> RGB (0..1). */
    fun hslToRgb(h: Float, s: Float, l: Float): Triple<Float, Float, Float> {
        if (s == 0f) return Triple(l, l, l)
        val hh = ((h % 360f) + 360f) % 360f / 60f
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs(hh % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when (hh.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Triple(r + m, g + m, b + m)
    }

    private fun r(p: Int) = (p shr 16) and 0xFF
    private fun g(p: Int) = (p shr 8) and 0xFF
    private fun b(p: Int) = p and 0xFF
    private fun pack(a: Int, r: Int, g: Int, b: Int) =
        a or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /**
     * Split Tone: tints shadows toward [shadowHue]/[shadowSat] and
     * highlights toward [highlightHue]/[highlightSat]. Weights grow with
     * distance from mid-grey, so midtones stay neutral. Hues are degrees,
     * saturations 0..100.
     */
    fun splitTone(
        src: IntArray,
        shadowHue: Float, shadowSat: Float,
        highlightHue: Float, highlightSat: Float
    ): IntArray {
        if (shadowSat == 0f && highlightSat == 0f) return src.copyOf()
        val out = IntArray(src.size)
        for (i in src.indices) {
            val p = src[i]
            val rf = r(p) / 255f
            val gf = g(p) / 255f
            val bf = b(p) / 255f
            val luma = 0.299f * rf + 0.587f * gf + 0.114f * bf
            val (_, _, l) = rgbToHsl(rf, gf, bf)
            var nr = rf
            var ng = gf
            var nb = bf
            // Shadow tint: weight (1-luma)^2; highlight tint: luma^2.
            val sw = (1f - luma) * (1f - luma) * (shadowSat / 100f)
            val hw = luma * luma * (highlightSat / 100f)
            if (sw > 0f) {
                val (tr, tg, tb) = hslToRgb(shadowHue, 1f, l)
                nr += (tr - nr) * sw
                ng += (tg - ng) * sw
                nb += (tb - nb) * sw
            }
            if (hw > 0f) {
                val (tr, tg, tb) = hslToRgb(highlightHue, 1f, l)
                nr += (tr - nr) * hw
                ng += (tg - ng) * hw
                nb += (tb - nb) * hw
            }
            out[i] = pack(
                p and -0x1000000,
                (nr.coerceIn(0f, 1f) * 255f + 0.5f).toInt(),
                (ng.coerceIn(0f, 1f) * 255f + 0.5f).toInt(),
                (nb.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            )
        }
        return out
    }

    /** One Color Mix channel: hue range centre + half-width in degrees. */
    data class MixChannel(val centre: Float, val halfWidth: Float)

    /** Express's eight mix channels and their hue centres. */
    val MIX_CHANNELS: List<Pair<String, MixChannel>> = listOf(
        "Red" to MixChannel(0f, 15f),
        "Orange" to MixChannel(30f, 15f),
        "Yellow" to MixChannel(60f, 15f),
        "Green" to MixChannel(120f, 40f),
        "Aqua" to MixChannel(180f, 15f),
        "Blue" to MixChannel(225f, 30f),
        "Purple" to MixChannel(270f, 15f),
        "Magenta" to MixChannel(315f, 15f)
    )

    private fun hueDistance(h1: Float, h2: Float): Float {
        var d = abs(h1 - h2) % 360f
        if (d > 180f) d = 360f - d
        return d
    }

    /**
     * Color Mix: per-channel Hue (-100..100, mapped to ±30°), Saturation
     * (-100..100) and Luminance (-100..100) shifts. A pixel is affected by
     * a channel in proportion to its hue distance from the channel centre
     * (trapezoid falloff over the channel's half-width).
     */
    fun colorMix(
        src: IntArray,
        hueShifts: FloatArray, // 8, -100..100
        satShifts: FloatArray, // 8, -100..100
        lumShifts: FloatArray // 8, -100..100
    ): IntArray {
        if (hueShifts.all { it == 0f } && satShifts.all { it == 0f } && lumShifts.all { it == 0f }) {
            return src.copyOf()
        }
        val out = IntArray(src.size)
        for (i in src.indices) {
            val p = src[i]
            val rf = r(p) / 255f
            val gf = g(p) / 255f
            val bf = b(p) / 255f
            var (h, s, l) = rgbToHsl(rf, gf, bf)
            if (s > 0.02f) {
                for (ci in MIX_CHANNELS.indices) {
                    val (_, ch) = MIX_CHANNELS[ci]
                    val d = hueDistance(h, ch.centre)
                    if (d >= ch.halfWidth) continue
                    // Smooth falloff: 1 at centre, 0 at the edge.
                    val w = 1f - d / ch.halfWidth
                    val ww = w * w * (3f - 2f * w) // smoothstep
                    h = ((h + hueShifts[ci] * 0.3f * ww) % 360f + 360f) % 360f
                    s = (s * (1f + satShifts[ci] / 100f * ww)).coerceIn(0f, 1f)
                    l = (l * (1f + lumShifts[ci] / 100f * ww)).coerceIn(0f, 1f)
                }
            }
            val (nr, ng, nb) = hslToRgb(h, s, l)
            out[i] = pack(
                p and -0x1000000,
                (nr.coerceIn(0f, 1f) * 255f + 0.5f).toInt(),
                (ng.coerceIn(0f, 1f) * 255f + 0.5f).toInt(),
                (nb.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            )
        }
        return out
    }
}
