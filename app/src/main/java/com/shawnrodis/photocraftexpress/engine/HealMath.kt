package com.shawnrodis.photocraftexpress.engine

import kotlin.math.sqrt

/**
 * Spot healing (Heal tool) and eye-glare correction (Eyes tool, red-eye
 * and pet-eye modes). Both are pure pixel math and deterministic.
 *
 * Heal: for each spot, the donor is a ring around the spot (offset
 * outward by 2.2x the radius, sampled from the eight compass directions
 * and averaged per channel), blended in with a smooth circular falloff
 * so the patch edge disappears. It removes small blemishes well; it is
 * not AI inpainting and will not reconstruct structure.
 */
object HealMath {

    private fun r(p: Int) = (p shr 16) and 0xFF
    private fun g(p: Int) = (p shr 8) and 0xFF
    private fun b(p: Int) = p and 0xFF
    private fun pack(a: Int, r: Int, g: Int, b: Int) =
        a or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** One heal tap, in normalized image coordinates. */
    data class HealSpot(val x: Float, val y: Float, val radius: Float)

    fun heal(src: IntArray, w: Int, h: Int, spots: List<HealSpot>): IntArray {
        if (spots.isEmpty()) return src.copyOf()
        val out = src.copyOf()
        for (spot in spots) {
            val cx = spot.x * w
            val cy = spot.y * h
            val rad = spot.radius * w
            if (rad < 1f) continue
            // Donor colour: average of 8 ring samples at 2.2x radius.
            var sr = 0f
            var sg = 0f
            var sb = 0f
            var n = 0
            for (k in 0 until 8) {
                val ang = k * (Math.PI / 4.0)
                val dx = kotlin.math.cos(ang) * rad * 2.2
                val dy = kotlin.math.sin(ang) * rad * 2.2
                val px = (cx + dx).toInt().coerceIn(0, w - 1)
                val py = (cy + dy).toInt().coerceIn(0, h - 1)
                val p = src[py * w + px]
                sr += r(p); sg += g(p); sb += b(p)
                n++
            }
            if (n == 0) continue
            val dr = sr / n
            val dg = sg / n
            val db = sb / n
            val r0 = (cx - rad).toInt().coerceAtLeast(0)
            val r1 = (cx + rad).toInt().coerceAtMost(w - 1)
            val c0 = (cy - rad).toInt().coerceAtLeast(0)
            val c1 = (cy + rad).toInt().coerceAtMost(h - 1)
            for (y in c0..c1) {
                for (x in r0..r1) {
                    val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / rad
                    if (d >= 1f) continue
                    // Smoothstep falloff: full donor at centre, 0 at edge.
                    val t = 1f - d
                    val wgt = t * t * (3f - 2f * t)
                    val i = y * w + x
                    val p = out[i]
                    out[i] = pack(
                        p and -0x1000000,
                        (r(p) + (dr - r(p)) * wgt).toInt(),
                        (g(p) + (dg - g(p)) * wgt).toInt(),
                        (b(p) + (db - b(p)) * wgt).toInt()
                    )
                }
            }
        }
        return out
    }

    /** One tap-to-correct eye-glare fix, in normalized coordinates. */
    data class EyeFix(val x: Float, val y: Float, val radius: Float, val pet: Boolean)

    /**
     * Red-eye: pixels where red dominates (r > 1.25*g and r > 1.25*b) get
     * their red pulled down toward the pixel's luminance, removing the
     * flash glare while keeping the iris shape. Pet-eye: the same for
     * green/yellow glare (g or r+g dominant over b), pulled toward a
     * natural dark tone. Both feather at the radius edge.
     */
    fun correctEyes(src: IntArray, w: Int, h: Int, fixes: List<EyeFix>): IntArray {
        if (fixes.isEmpty()) return src.copyOf()
        val out = src.copyOf()
        for (fix in fixes) {
            val cx = fix.x * w
            val cy = fix.y * h
            val rad = fix.radius * w
            if (rad < 1f) continue
            val r0 = (cx - rad).toInt().coerceAtLeast(0)
            val r1 = (cx + rad).toInt().coerceAtMost(w - 1)
            val c0 = (cy - rad).toInt().coerceAtLeast(0)
            val c1 = (cy + rad).toInt().coerceAtMost(h - 1)
            for (y in c0..c1) {
                for (x in r0..r1) {
                    val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / rad
                    if (d >= 1f) continue
                    val t = 1f - d
                    val wgt = t * t * (3f - 2f * t)
                    val i = y * w + x
                    val p = out[i]
                    val rf = r(p).toFloat()
                    val gf = g(p).toFloat()
                    val bf = b(p).toFloat()
                    val luma = 0.299f * rf + 0.587f * gf + 0.114f * bf
                    val isGlare = if (!fix.pet) {
                        rf > gf * 1.25f && rf > bf * 1.25f
                    } else {
                        (gf > rf * 1.1f && gf > bf * 1.2f) || // green glare
                            (rf > bf * 1.5f && gf > bf * 1.5f && kotlin.math.abs(rf - gf) < 60f) // yellow glare
                    }
                    if (!isGlare) continue
                    // Pull the dominant channel(s) toward a natural dark iris tone.
                    val target = luma * 0.55f
                    val nr = if (!fix.pet) rf + (target - rf) * wgt else rf + (target - rf) * wgt * 0.7f
                    val ng = if (fix.pet) gf + (target - gf) * wgt else gf
                    val nb = bf
                    out[i] = pack(
                        p and -0x1000000,
                        nr.toInt().coerceIn(0, 255),
                        ng.toInt().coerceIn(0, 255),
                        nb.toInt().coerceIn(0, 255)
                    )
                }
            }
        }
        return out
    }
}
