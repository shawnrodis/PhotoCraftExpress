package com.shawnrodis.photocraftexpress.engine

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * One-tap auto-enhance (the top bar's magic-wand action): histogram-based
 * auto exposure / contrast / saturation, expressed as [Adjustments]
 * deltas so the result is an ordinary undoable edit state — it lands in
 * the Adjustments sliders, where the user can refine or undo it.
 *
 * Analysis runs on whatever image is handed in (the editor passes the
 * current preview, so tapping the wand twice converges: the second tap
 * measures the already-enhanced image and returns near-zero deltas).
 *
 * Mappings, calibrated against AdjustMath's slider semantics:
 * - Exposure: gain = 2^(slider/50), so slider = EV * 50. The EV that
 *   would move the mean luma to mid-grey is log2(128/mean), clamped to
 *   +/-1 EV (+/-50 on the slider).
 * - Contrast: slope = tan((c+1)*pi/4) with c = slider/100*0.9, so small
 *   slider values already bite (20 -> slope 1.33). A narrow 1st-99th
 *   percentile spread earns at most +25 — but never on a dark photo
 *   (mean under 100) or a very bright one (mean over 156, the mirror
 *   of the dark bound around mid-grey 128): contrast pivots about the
 *   midpoint, so on dark photos it would crush the shadows the
 *   exposure lift just rescued, and on bright photos it would only
 *   clip highlights. Extreme photos get exposure first; contrast is
 *   for balanced photos.
 * - Vibrance: a dull image (mean saturation under 0.20) earns at most
 *   +30, scaled by how dull it is.
 * Nothing is ever applied here — this only computes the deltas.
 */
object AutoEnhance {

    /** Histogram percentiles and means for one image. */
    data class Stats(
        val meanLuma: Float,
        val p1: Int,
        val p99: Int,
        val meanSaturation: Float
    )

    fun analyze(img: PixelImage): Stats {
        val hist = IntArray(256)
        var sumLuma = 0.0
        var sumSat = 0.0
        var n = 0
        for (p in img.pixels) {
            val r = ((p shr 16) and 0xFF)
            val g = ((p shr 8) and 0xFF)
            val b = (p and 0xFF)
            val luma = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
            hist[luma]++
            sumLuma += luma
            val mx = maxOf(r, g, b)
            val mn = minOf(r, g, b)
            sumSat += if (mx == 0) 0.0 else (mx - mn).toDouble() / mx
            n++
        }
        if (n == 0) return Stats(128f, 0, 255, 0f)
        fun percentile(frac: Double): Int {
            val target = (frac * n).toInt().coerceIn(0, n - 1)
            var acc = 0
            for (i in 0..255) {
                acc += hist[i]
                if (acc > target) return i
            }
            return 255
        }
        return Stats(
            meanLuma = (sumLuma / n).toFloat(),
            p1 = percentile(0.01),
            p99 = percentile(0.99),
            meanSaturation = (sumSat / n).toFloat()
        )
    }

    /** Deltas to ADD to the current adjustments (each clamped to its slider range). */
    fun deltas(stats: Stats): Adjustments {
        // Exposure: EV that centres the mean, clamped to +/-1 EV.
        val mean = stats.meanLuma.coerceIn(1f, 254f)
        val ev = (ln(128.0 / mean) / ln(2.0)).toFloat().coerceIn(-1f, 1f)
        val exposure = (ev * 50f).let {
            // Dead zone: a well-exposed photo (mean within 112..144) earns nothing.
            if (stats.meanLuma in 112f..144f) 0f else it
        }
        // Contrast: narrow percentile spread earns up to +25, except on
        // dark photos where it would crush shadows, and on very bright
        // photos where it would only clip highlights (see header).
        val spread = (stats.p99 - stats.p1).coerceIn(0, 255)
        val contrast = if (spread >= 200 || stats.meanLuma < 100f ||
            stats.meanLuma > 156f
        ) 0f
        else ((200 - spread).toFloat() / 200f * 25f)
        // Vibrance: dull photos earn up to +30.
        val vibrance = if (stats.meanSaturation >= 0.20f) 0f
        else ((0.20f - stats.meanSaturation) / 0.20f * 30f)
        return Adjustments(
            exposure = exposure,
            contrast = contrast,
            vibrance = vibrance
        )
    }

    /** Deltas for [img] in one call. */
    fun deltasFor(img: PixelImage): Adjustments = deltas(analyze(img))

    /** Adds [delta] to [base], clamping every slider to -100..100. */
    fun apply(base: Adjustments, delta: Adjustments): Adjustments {
        fun clamp(v: Float) = v.coerceIn(-100f, 100f)
        return base.copy(
            exposure = clamp(base.exposure + delta.exposure),
            contrast = clamp(base.contrast + delta.contrast),
            vibrance = clamp(base.vibrance + delta.vibrance)
        )
    }
}
