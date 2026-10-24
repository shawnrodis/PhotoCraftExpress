package com.shawnrodis.photocraftexpress.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ----------------------------------------------------------------------
// New EditState pieces (see EditState.kt for the aggregate).
// ----------------------------------------------------------------------

/** Split Tone: hues in degrees, saturations 0..100. */
data class SplitTone(
    val shadowHue: Float = 220f,
    val shadowSat: Float = 0f,
    val highlightHue: Float = 40f,
    val highlightSat: Float = 0f
) {
    val isIdentity: Boolean get() = shadowSat == 0f && highlightSat == 0f
}

/**
 * Color Mix: one shift triple per channel (Hue/Sat/Lum, -100..100).
 * Plain class with content-based equals: EditState snapshots are compared
 * by Compose effect keys, and array-backed data classes would compare by
 * reference and re-render needlessly.
 */
class ColorMix(
    val hue: FloatArray = FloatArray(8),
    val sat: FloatArray = FloatArray(8),
    val lum: FloatArray = FloatArray(8)
) {
    val isIdentity: Boolean
        get() = hue.all { it == 0f } && sat.all { it == 0f } && lum.all { it == 0f }

    fun withChannel(i: Int, h: Float, s: Float, l: Float): ColorMix {
        val nh = hue.copyOf(); nh[i] = h
        val ns = sat.copyOf(); ns[i] = s
        val nl = lum.copyOf(); nl[i] = l
        return ColorMix(nh, ns, nl)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ColorMix) return false
        return hue.contentEquals(other.hue) && sat.contentEquals(other.sat) && lum.contentEquals(other.lum)
    }

    override fun hashCode(): Int {
        var r = hue.contentHashCode()
        r = 31 * r + sat.contentHashCode()
        r = 31 * r + lum.contentHashCode()
        return r
    }
}

/** Blur: Full (whole photo) or Radial (ellipse, draggable in the UI). */
enum class BlurMode { NONE, FULL, RADIAL }

data class BlurFx(
    val mode: BlurMode = BlurMode.NONE,
    val amount: Float = 0f, // 0..100
    val feather: Float = 50f, // 0..100 (radial only)
    val roundness: Float = 0f, // -100..100 (radial only)
    val cx: Float = 0.5f, val cy: Float = 0.5f, // normalized centre
    val rx: Float = 0.35f, val ry: Float = 0.35f // normalized radii
) {
    val isIdentity: Boolean get() = mode == BlurMode.NONE || amount == 0f
}

/** Vignette: amount -100..100, midPoint/feather 0..100, roundness -100..100. */
data class Vignette(
    val amount: Float = 0f,
    val midPoint: Float = 50f,
    val feather: Float = 50f,
    val roundness: Float = 0f
) {
    val isIdentity: Boolean get() = amount == 0f
}

/** Face adjustments, all -100..100, 0 = identity. Applied via ellipse warps. */
data class FaceAdjust(
    val faceSize: Float = 0f,
    val faceWidth: Float = 0f,
    val lipSize: Float = 0f,
    val noseWidth: Float = 0f,
    val eyeSize: Float = 0f,
    val contour: Float = 0f
) {
    val isIdentity: Boolean
        get() = faceSize == 0f && faceWidth == 0f && lipSize == 0f &&
            noseWidth == 0f && eyeSize == 0f && contour == 0f
}

/** One freehand Draw stroke: normalized points + brush attributes. */
data class DrawStroke(
    val points: List<Pair<Float, Float>>,
    val color: Int, // ARGB
    val width: Float, // normalized (fraction of image width)
    val opacity: Float // 0..1
)

/** Text layer. Size is text height as a fraction of image height. */
enum class TextFont { SANS, SERIF, MONO }

data class TextItem(
    val id: Long,
    val text: String,
    val x: Float, val y: Float, // normalized centre
    val size: Float = 0.08f,
    val color: Int = -0x1, // white
    val font: TextFont = TextFont.SANS,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strokeWidth: Float = 0f, // fraction of text height, 0 = none
    val strokeColor: Int = -0x1000000 // black
)

/** Built-in sticker kinds, drawn as vector paths at composite time. */
enum class StickerKind { STAR, HEART, ARROW, CIRCLE, SQUARE, TRIANGLE, SPARKLE, SMILEY }

data class StickerItem(
    val id: Long,
    val kind: StickerKind,
    val x: Float, val y: Float, // normalized centre
    val scale: Float = 0.15f, // fraction of image width
    val rotation: Float = 0f, // degrees
    val color: Int = -0x1 // white
)

/** One brush point of an Insert Objects selection mask. */
data class MaskPoint(val x: Float, val y: Float, val radius: Float)

/** An inserted photo layer. [uri] is the source photo's URI string. */
data class InsertedImage(
    val id: Long,
    val uri: String,
    val x: Float, val y: Float, // normalized centre
    val scale: Float = 0.5f, // fraction of canvas width
    val rotation: Float = 0f, // degrees
    val useMask: Boolean = false,
    val maskStrokes: List<MaskPoint> = emptyList()
)

/** Border: plain width+colour frame, or a decorative Edge / Frame preset. */
enum class BorderKind { PLAIN, EDGE, FRAME }

data class BorderFx(
    val kind: BorderKind = BorderKind.PLAIN,
    val width: Float = 0f, // fraction of min(image w, h); 0 = none
    val color: Int = -0x1, // white
    val preset: String = "" // edge/frame preset id, "" = plain
) {
    val isIdentity: Boolean get() = width == 0f && preset.isEmpty()
}

/** One named overlay effect + intensity (0..100). */
data class OverlayFx(val id: String = "", val intensity: Float = 0f) {
    val isIdentity: Boolean get() = id.isEmpty() || intensity == 0f
}

/** Background treatment: blur or solid-colour replace, people-only (mask). */
enum class BackgroundMode { NONE, BLUR, REPLACE }

data class BackgroundFx(
    val mode: BackgroundMode = BackgroundMode.NONE,
    val amount: Float = 0f, // blur amount 0..100
    val color: Int = -0x1000000 // replace colour
) {
    val isIdentity: Boolean get() = mode == BackgroundMode.NONE || (mode == BackgroundMode.BLUR && amount == 0f)
}

/** Export output size. Custom dimensions are in pixels. */
enum class ExportSizeMode { ORIGINAL, LONG_1080, LONG_2048, PERCENT_50, CUSTOM }

data class ExportSize(
    val mode: ExportSizeMode = ExportSizeMode.ORIGINAL,
    val customW: Int = 0,
    val customH: Int = 0,
    val lockAspect: Boolean = true
) {
    /** Computes output dimensions for an input image, or null = unchanged. */
    fun outputSize(inW: Int, inH: Int): Pair<Int, Int>? {
        if (inW <= 0 || inH <= 0) return null
        return when (mode) {
            ExportSizeMode.ORIGINAL -> null
            ExportSizeMode.LONG_1080 -> scaleToLongEdge(inW, inH, 1080)
            ExportSizeMode.LONG_2048 -> scaleToLongEdge(inW, inH, 2048)
            ExportSizeMode.PERCENT_50 -> Pair(max(1, inW / 2), max(1, inH / 2))
            ExportSizeMode.CUSTOM -> {
                if (customW <= 0 && customH <= 0) return null
                if (lockAspect) {
                    if (customW > 0) {
                        val h = (customW.toFloat() * inH / inW).toInt().coerceAtLeast(1)
                        Pair(customW, h)
                    } else {
                        val w = (customH.toFloat() * inW / inH).toInt().coerceAtLeast(1)
                        Pair(w, customH)
                    }
                } else {
                    Pair(customW.coerceAtLeast(1), customH.coerceAtLeast(1))
                }
            }
        }
    }

    private fun scaleToLongEdge(inW: Int, inH: Int, edge: Int): Pair<Int, Int>? {
        val long = max(inW, inH)
        if (long <= edge) return null
        val s = edge.toFloat() / long
        return Pair(max(1, (inW * s).toInt()), max(1, (inH * s).toInt()))
    }
}

// ----------------------------------------------------------------------
// Pure raster helpers: draw strokes, borders, overlays, masks.
// ----------------------------------------------------------------------

object LayerRaster {

    private fun r(p: Int) = (p shr 16) and 0xFF
    private fun g(p: Int) = (p shr 8) and 0xFF
    private fun b(p: Int) = p and 0xFF
    private fun pack(a: Int, r: Int, g: Int, b: Int) =
        a or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    private fun blend(dst: Int, srcColor: Int, alpha: Float): Int {
        val a = alpha.coerceIn(0f, 1f)
        return pack(
            dst and -0x1000000,
            (r(dst) + (r(srcColor) - r(dst)) * a).toInt(),
            (g(dst) + (g(srcColor) - g(dst)) * a).toInt(),
            (b(dst) + (b(srcColor) - b(dst)) * a).toInt()
        )
    }

    private fun dot(img: IntArray, w: Int, h: Int, cx: Float, cy: Float, rad: Float, color: Int, alpha: Float) {
        val r0 = (cx - rad).toInt().coerceAtLeast(0)
        val r1 = (cx + rad).toInt().coerceAtMost(w - 1)
        val c0 = (cy - rad).toInt().coerceAtLeast(0)
        val c1 = (cy + rad).toInt().coerceAtMost(h - 1)
        for (y in c0..c1) {
            for (x in r0..r1) {
                val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy))
                if (d > rad) continue
                val fall = 1f - (d / rad) * (d / rad) * 0.35f
                val i = y * w + x
                img[i] = blend(img[i], color, alpha * fall)
            }
        }
    }

    /** Draws freehand strokes (round caps, interpolated between points). */
    fun drawStrokes(img: IntArray, w: Int, h: Int, strokes: List<DrawStroke>): IntArray {
        if (strokes.isEmpty()) return img
        val out = img.copyOf()
        for (s in strokes) {
            val rad = max(1f, s.width * w / 2f)
            val pts = s.points
            if (pts.isEmpty()) continue
            if (pts.size == 1) {
                dot(out, w, h, pts[0].first * w, pts[0].second * h, rad, s.color, s.opacity)
                continue
            }
            for (k in 0 until pts.size - 1) {
                val (x0, y0) = pts[k]
                val (x1, y1) = pts[k + 1]
                val steps = max(1, (sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0)) * w / (rad * 0.5f)).toInt())
                for (t in 0..steps) {
                    val f = t.toFloat() / steps
                    dot(
                        out, w, h,
                        (x0 + (x1 - x0) * f) * w,
                        (y0 + (y1 - y0) * f) * h,
                        rad, s.color, s.opacity
                    )
                }
            }
        }
        return out
    }

    /**
     * Rasterizes brush-mask strokes into a float mask (0..1) at image
     * resolution. Used to clip inserted photos to a brushed region.
     */
    fun rasterizeMask(w: Int, h: Int, strokes: List<MaskPoint>): FloatArray {
        val mask = FloatArray(w * h)
        for (s in strokes) {
            val cx = s.x * w
            val cy = s.y * h
            val rad = max(1f, s.radius * w)
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
                    if (wgt > mask[i]) mask[i] = wgt
                }
            }
        }
        return mask
    }

    /** Applies a border (plain / edge / frame preset) to the whole image. */
    fun applyBorder(img: IntArray, w: Int, h: Int, border: BorderFx): IntArray {
        if (border.isIdentity) return img
        val out = img.copyOf()
        val bw = (border.width * min(w, h)).toInt()
        if (bw <= 0 && border.kind == BorderKind.PLAIN) return img
        when (border.kind) {
            BorderKind.PLAIN -> {
                val t = bw
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        if (x < t || x >= w - t || y < t || y >= h - t) {
                            out[y * w + x] = border.color
                        }
                    }
                }
            }
            BorderKind.EDGE -> when (border.preset) {
                "torn" -> {
                    // Noise-modulated inner boundary, deterministic hash.
                    val t = max(2, bw)
                    for (y in 0 until h) {
                        for (x in 0 until w) {
                            val edgeNoise = hashNoise(x / 7, y / 7) * t * 0.6f
                            val dEdge = minOf(x, w - 1 - x, y, h - 1 - y)
                            if (dEdge < t + edgeNoise) out[y * w + x] = border.color
                        }
                    }
                }
                "scallop" -> {
                    val t = max(4, bw)
                    val bump = t / 2f
                    for (y in 0 until h) {
                        for (x in 0 until w) {
                            val dEdge = minOf(x, w - 1 - x, y, h - 1 - y)
                            // Scallops: sine bumps along each edge.
                            val along = if (y < t || y >= h - t) x else y
                            val wave = (kotlin.math.sin(along / (bump * 2f) * Math.PI.toFloat()) * 0.5f + 0.5f) * bump
                            if (dEdge < t * 0.5f + wave) out[y * w + x] = border.color
                        }
                    }
                }
                "double" -> {
                    val t = max(2, bw)
                    val gap = max(2, t / 3)
                    for (y in 0 until h) {
                        for (x in 0 until w) {
                            val d = minOf(x, w - 1 - x, y, h - 1 - y)
                            if (d < t / 2 || (d >= t / 2 + gap && d < t + gap)) {
                                out[y * w + x] = border.color
                            }
                        }
                    }
                }
                else -> Unit
            }
            BorderKind.FRAME -> when (border.preset) {
                "polaroid" -> {
                    val side = max(2, bw)
                    val bottom = side * 3
                    for (y in 0 until h) {
                        for (x in 0 until w) {
                            if (x < side || x >= w - side || y < side || y >= h - bottom) {
                                out[y * w + x] = border.color
                            }
                        }
                    }
                }
                "filmstrip" -> {
                    val t = max(6, bw)
                    val holeW = t * 2 / 3
                    val holeH = t / 2
                    val holeGap = t * 2
                    for (y in 0 until h) {
                        for (x in 0 until w) {
                            if (y < t || y >= h - t) {
                                // Sprocket holes: lighter rectangles punched along the strips.
                                val inHoleRow = (y >= t / 4 && y < t / 4 + holeH) ||
                                    (y >= h - t / 4 - holeH && y < h - t / 4)
                                val holeX = x % (holeW + holeGap)
                                val inHole = inHoleRow && holeX < holeW
                                out[y * w + x] = if (inHole) 0xFFBBBBBB.toInt() else -0x1000000
                            }
                        }
                    }
                }
                "vintage" -> {
                    val t = max(4, bw)
                    val gold = 0xFFC9A227.toInt()
                    for (y in 0 until h) {
                        for (x in 0 until w) {
                            val d = minOf(x, w - 1 - x, y, h - 1 - y)
                            out[y * w + x] = when {
                                d < t -> border.color
                                d < t + 2 -> gold
                                d < t + 4 -> border.color
                                else -> out[y * w + x]
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
        return out
    }

    private fun hashNoise(x: Int, y: Int): Float {
        var h = (x * 374761393 + y * 668265263)
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return ((h and 0x7FFFFFFF) / 1073741824f - 1f) * 0.5f + 0.5f // 0..1
    }

    /** Applies a named overlay effect at [intensity] 0..100. */
    fun applyOverlay(img: IntArray, w: Int, h: Int, overlay: OverlayFx): IntArray {
        if (overlay.isIdentity) return img
        val k = (overlay.intensity / 100f).coerceIn(0f, 1f)
        val out = img.copyOf()
        when (overlay.id) {
            "lightleak" -> {
                // Warm glow sweeping from the top-left corner.
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val d = sqrt((x.toFloat() / w) * (x.toFloat() / w) + (y.toFloat() / h) * (y.toFloat() / h))
                        val glow = (1f - d.coerceIn(0f, 1f)) * k * 0.55f
                        val i = y * w + x
                        out[i] = blend(out[i], 0xFFFFB36B.toInt(), glow)
                    }
                }
            }
            "bokeh" -> {
                // Deterministic translucent circles, seeded by index.
                var seed = 1234567
                fun next(): Float {
                    seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
                    return seed / 1073741824f
                }
                repeat(28) {
                    val cx = next() * w
                    val cy = next() * h
                    val rad = (0.04f + next() * 0.10f) * w
                    val warm = next() > 0.5f
                    dot(out, w, h, cx, cy, rad, if (warm) 0xFFFFD9A0.toInt() else 0xFFBFE3FF.toInt(), k * 0.20f)
                }
            }
            "warmgrad" -> {
                for (y in 0 until h) {
                    val f = y.toFloat() / h * k * 0.35f
                    for (x in 0 until w) {
                        val i = y * w + x
                        out[i] = blend(out[i], 0xFFFF9A3C.toInt(), f)
                    }
                }
            }
            "coolgrad" -> {
                for (y in 0 until h) {
                    val f = (1f - y.toFloat() / h) * k * 0.35f
                    for (x in 0 until w) {
                        val i = y * w + x
                        out[i] = blend(out[i], 0xFF6BB3FF.toInt(), f)
                    }
                }
            }
            "oldphoto" -> {
                // Sepia wash + slight fade, the antique-print look.
                for (i in out.indices) {
                    val p = out[i]
                    val luma = 0.299f * r(p) + 0.587f * g(p) + 0.114f * b(p)
                    val sr = (luma * 1.05f).toInt()
                    val sg = (luma * 0.90f).toInt()
                    val sb = (luma * 0.70f).toInt()
                    out[i] = pack(
                        p and -0x1000000,
                        (r(p) + (sr - r(p)) * k).toInt(),
                        (g(p) + (sg - g(p)) * k).toInt(),
                        (b(p) + (sb - b(p)) * k).toInt()
                    )
                }
            }
        }
        return out
    }

    /** Blurs or replaces the background where [mask] (0..1, person) is low. */
    fun applyBackground(
        img: IntArray, w: Int, h: Int,
        mask: FloatArray, fx: BackgroundFx
    ): IntArray {
        if (fx.isIdentity || mask.size != img.size) return img
        return when (fx.mode) {
            BackgroundMode.BLUR -> {
                val radius = max(1, (fx.amount / 100f * min(w, h) / 60f).toInt())
                val blurred = PixelPipeline.boxBlur(img, w, h, radius, passes = 2)
                val out = IntArray(img.size)
                for (i in img.indices) {
                    val keep = mask[i].coerceIn(0f, 1f)
                    val p = img[i]
                    val q = blurred[i]
                    val wgt = (1f - keep) * (fx.amount / 100f).coerceIn(0f, 1f)
                    out[i] = pack(
                        p and -0x1000000,
                        (r(p) + (r(q) - r(p)) * wgt).toInt(),
                        (g(p) + (g(q) - g(p)) * wgt).toInt(),
                        (b(p) + (b(q) - b(p)) * wgt).toInt()
                    )
                }
                out
            }
            BackgroundMode.REPLACE -> {
                val out = img.copyOf()
                for (i in img.indices) {
                    val keep = mask[i].coerceIn(0f, 1f)
                    if (keep >= 1f) continue
                    out[i] = blend(img[i], fx.color, 1f - keep)
                }
                out
            }
            BackgroundMode.NONE -> img
        }
    }

    /**
     * Applies makeup overlays at face landmarks (normalized coords in the
     * rendered frame): lip tint ellipse at the mouth, blush at the cheeks,
     * eye shadow above the eyes, liner along the eye line.
     */
    fun applyMakeup(
        img: IntArray, w: Int, h: Int,
        landmarks: Map<String, Pair<Float, Float>>,
        lipColor: Int, lipIntensity: Float,
        blushIntensity: Float, shadowIntensity: Float, linerIntensity: Float
    ): IntArray {
        if (lipIntensity == 0f && blushIntensity == 0f && shadowIntensity == 0f && linerIntensity == 0f) {
            return img
        }
        val out = img.copyOf()
        fun softDot(cx: Float, cy: Float, rx: Float, ry: Float, color: Int, alpha: Float) {
            val r0 = (cx - rx).toInt().coerceAtLeast(0)
            val r1 = (cx + rx).toInt().coerceAtMost(w - 1)
            val c0 = (cy - ry).toInt().coerceAtLeast(0)
            val c1 = (cy + ry).toInt().coerceAtMost(h - 1)
            for (y in c0..c1) {
                for (x in r0..r1) {
                    val dx = (x - cx) / rx
                    val dy = (y - cy) / ry
                    val d = dx * dx + dy * dy
                    if (d > 1f) continue
                    val fall = (1f - d) * alpha
                    val i = y * w + x
                    out[i] = blend(out[i], color, fall)
                }
            }
        }
        val faceW = landmarks["faceW"]?.first ?: 0.3f
        val mouth = landmarks["mouth"]
        val leftCheek = landmarks["leftCheek"]
        val rightCheek = landmarks["rightCheek"]
        val leftEye = landmarks["leftEye"]
        val rightEye = landmarks["rightEye"]
        if (mouth != null && lipIntensity > 0f) {
            val lw = faceW * w * 0.28f
            val lh = faceW * w * 0.10f
            softDot(mouth.first * w, mouth.second * h, lw, lh, lipColor, lipIntensity * 0.55f)
        }
        if (blushIntensity > 0f) {
            val bw = faceW * w * 0.22f
            val bh = faceW * w * 0.14f
            leftCheek?.let { softDot(it.first * w, it.second * h, bw, bh, 0xFFFF8FA3.toInt(), blushIntensity * 0.35f) }
            rightCheek?.let { softDot(it.first * w, it.second * h, bw, bh, 0xFFFF8FA3.toInt(), blushIntensity * 0.35f) }
        }
        if (shadowIntensity > 0f) {
            val sw = faceW * w * 0.20f
            val sh = faceW * w * 0.07f
            leftEye?.let { softDot(it.first * w, (it.second * h) - sh, sw, sh, 0xFF8A6BAE.toInt(), shadowIntensity * 0.30f) }
            rightEye?.let { softDot(it.first * w, (it.second * h) - sh, sw, sh, 0xFF8A6BAE.toInt(), shadowIntensity * 0.30f) }
        }
        if (linerIntensity > 0f) {
            val lw = faceW * w * 0.20f
            leftEye?.let { dot(out, w, h, it.first * w, it.second * h, lw * 0.12f, -0x1000000, linerIntensity * 0.5f) }
            rightEye?.let { dot(out, w, h, it.first * w, it.second * h, lw * 0.12f, -0x1000000, linerIntensity * 0.5f) }
        }
        return out
    }

    /** Eye brightening around the eye landmarks (normalized coords). */
    fun enhanceEyes(
        img: IntArray, w: Int, h: Int,
        eyes: List<Pair<Float, Float>>, faceW: Float, intensity: Float
    ): IntArray {
        if (intensity == 0f || eyes.isEmpty()) return img
        val out = img.copyOf()
        val k = intensity.coerceIn(0f, 1f)
        for ((ex, ey) in eyes) {
            val cx = ex * w
            val cy = ey * h
            val rad = faceW * w * 0.16f
            val r0 = (cx - rad).toInt().coerceAtLeast(0)
            val r1 = (cx + rad).toInt().coerceAtMost(w - 1)
            val c0 = (cy - rad).toInt().coerceAtLeast(0)
            val c1 = (cy + rad).toInt().coerceAtMost(h - 1)
            for (y in c0..c1) {
                for (x in r0..r1) {
                    val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / rad
                    if (d >= 1f) continue
                    val fall = (1f - d) * k * 0.35f
                    val i = y * w + x
                    val p = out[i]
                    // Brighten toward white (sclera pop) + gentle sharpen feel.
                    out[i] = pack(
                        p and -0x1000000,
                        (r(p) + (255 - r(p)) * fall).toInt(),
                        (g(p) + (255 - g(p)) * fall).toInt(),
                        (b(p) + (255 - b(p)) * fall).toInt()
                    )
                }
            }
        }
        return out
    }
}
