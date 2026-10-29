package com.shawnrodis.photocraftexpress.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.CancellationException

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
    /**
     * Look + user merges for the deeper stages (v0.2.2). Shifts and
     * saturations ADD and clamp, matching how temperature/tint add in
     * applyTone; hues and vignette shapes are not additive, so the
     * user's value wins when the user touched that stage, else the
     * Look's value carries. A Look that sets nothing returns the user's
     * value untouched.
     */
    private fun mergeColorMix(user: ColorMix, look: ColorMix): ColorMix {
        if (look.isIdentity) return user
        return ColorMix(
            FloatArray(8) { (user.hue[it] + look.hue[it]).coerceIn(-100f, 100f) },
            FloatArray(8) { (user.sat[it] + look.sat[it]).coerceIn(-100f, 100f) },
            FloatArray(8) { (user.lum[it] + look.lum[it]).coerceIn(-100f, 100f) }
        )
    }

    private fun mergeSplitTone(user: SplitTone, look: SplitTone): SplitTone {
        if (look.isIdentity) return user
        return SplitTone(
            shadowHue = if (user.shadowSat > 0f) user.shadowHue else look.shadowHue,
            shadowSat = (user.shadowSat + look.shadowSat).coerceIn(0f, 100f),
            highlightHue = if (user.highlightSat > 0f) user.highlightHue else look.highlightHue,
            highlightSat = (user.highlightSat + look.highlightSat).coerceIn(0f, 100f)
        )
    }

    private fun mergeVignette(user: Vignette, look: Vignette): Vignette {
        if (look.isIdentity) return user
        return Vignette(
            amount = (user.amount + look.amount).coerceIn(-100f, 100f),
            midPoint = if (!user.isIdentity) user.midPoint else look.midPoint,
            feather = if (!user.isIdentity) user.feather else look.feather,
            roundness = if (!user.isIdentity) user.roundness else look.roundness
        )
    }

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

    /**
     * Full render. Stage order:
     *   geometry -> perspective transform -> Look/Adjust tone LUT
     *   -> pixel stage (temp/tint/vibrance/saturation) -> dehaze -> noise
     *   -> texture -> clarity -> fade -> smooth -> grain -> color mix
     *   -> split tone -> blur -> vignette -> face/reshape warp -> heal
     *   -> makeup -> eyes enhance -> eye fixes -> background -> draw
     *   -> overlays -> border.
     * Text, stickers and inserted photos are composited afterwards at the
     * Bitmap layer (LayerComposite), because they need Android Canvas.
     * [assets] carries ML Kit results already mapped into the rendered
     * frame (face) or in the source frame (person mask, mapped here).
     * [state.adjustMask] gates the Adjustments-panel edits through the
     * person mask (FULL = everywhere). [isActive] is checked at stage
     * boundaries so a superseded preview render can abort early instead
     * of competing with the render for the latest slider value.
     */
    fun render(
        img: PixelImage, state: EditState,
        applyCrop: Boolean = true,
        assets: RenderAssets = RenderAssets(),
        isActive: () -> Boolean = { true }
    ): PixelImage {
        // Lets a superseded preview render abort at stage boundaries
        // instead of running to completion on a background thread while
        // a newer slider value already waits (live-preview smoothness).
        fun checkActive() {
            if (!isActive()) throw CancellationException("preview render superseded")
        }
        var geo = applyGeometry(img, state, applyCrop)
        val transform = state.transform
        if (transform != null && !transform.isIdentity) {
            geo = PixelImage(
                WarpMath.perspectiveWarp(geo.pixels, geo.width, geo.height, transform),
                geo.width, geo.height
            )
        }
        val look = Looks.resolve(Looks.byId(state.lookId), state.lookIntensity)
        val adj = state.adj

        val curW = geo.width
        val curH = geo.height
        var current = applyTone(geo, look, adj)
        checkActive()
        val sharpen = max(adj.sharpen, look.sharpen).coerceIn(0f, 100f)
        current = if (sharpen > 0f) {
            val radius = if (max(curW, curH) > 2000) 2 else 1
            unsharp(current, curW, curH, radius, sharpen / 100f * 1.2f)
        } else {
            current
        }
        checkActive()

        // Deeper adjustments. Look stage contributions ADD to the user's
        // panel values (same philosophy as temperature/tint in
        // applyTone), so a Look grades the photo and the user's sliders
        // still stack on top. The mask-blend base below carries the
        // Look-only stages, so the mask gates panel edits, not the Look.
        val effDehaze = (adj.dehaze + look.dehaze).coerceIn(-100f, 100f)
        val effTexture = (adj.texture + look.texture).coerceIn(-100f, 100f)
        val effClarity = (adj.clarity + look.clarity).coerceIn(-100f, 100f)
        val effGrain = (adj.grain + look.grain).coerceIn(0f, 100f)
        val effColorMix = mergeColorMix(state.colorMix, look.colorMix)
        val effSplitTone = mergeSplitTone(state.splitTone, look.splitTone)
        val effVignette = mergeVignette(state.vignette, look.vignette)
        if (effDehaze != 0f) current = AdjustStages.dehaze(current, curW, curH, effDehaze)
        if (adj.reduceColorNoise > 0f) current = AdjustStages.reduceColorNoise(current, curW, curH, adj.reduceColorNoise)
        if (adj.reduceLuminanceNoise > 0f) current = AdjustStages.reduceLuminanceNoise(current, curW, curH, adj.reduceLuminanceNoise)
        if (effTexture != 0f) current = AdjustStages.texture(current, curW, curH, effTexture)
        if (effClarity != 0f) current = AdjustStages.clarity(current, curW, curH, effClarity)
        if (adj.fade != 0f) current = AdjustStages.fade(current, adj.fade)
        if (state.smooth > 0f) current = AdjustStages.smooth(current, curW, curH, state.smooth)
        if (effGrain > 0f) current = AdjustStages.grain(current, effGrain)
        checkActive()

        // Color Mix, then Split Tone.
        if (!effColorMix.isIdentity) {
            current = ColorMath.colorMix(current, effColorMix.hue, effColorMix.sat, effColorMix.lum)
        }
        if (!effSplitTone.isIdentity) {
            current = ColorMath.splitTone(
                current,
                effSplitTone.shadowHue, effSplitTone.shadowSat,
                effSplitTone.highlightHue, effSplitTone.highlightSat
            )
        }

        // Blur, then vignette.
        if (!state.blur.isIdentity) {
            current = when (state.blur.mode) {
                BlurMode.FULL -> {
                    val radius = max(1, (state.blur.amount / 100f * min(curW, curH) / 40f).toInt())
                    boxBlur(current, curW, curH, radius, passes = 2)
                }
                BlurMode.RADIAL -> AdjustStages.radialBlur(current, curW, curH, state.blur)
                BlurMode.NONE -> current
            }
        }
        if (!effVignette.isIdentity) {
            current = AdjustStages.vignette(
                current, curW, curH,
                effVignette.amount, effVignette.midPoint,
                effVignette.feather, effVignette.roundness
            )
        }

        checkActive()

        // Adjustment mask (Express Full / Subject / Background): blend
        // the look-only base with the fully adjusted image by the person
        // mask weight, so only the masked region carries the
        // Adjustments-panel edits (tone, deeper stages, color mix, split
        // tone, blur, vignette). Geometry, warps, heal, makeup, layers
        // and borders are unaffected and apply after this blend.
        if (state.adjustMask != AdjustMask.FULL) {
            // Look-only base: the Look's tone (applyTone) PLUS its
            // deeper stages, in pipeline order. The mask then gates
            // only the user's panel edits: the masked region gets
            // user+Look, the rest still gets the full Look.
            var base = applyTone(geo, look, Adjustments())
            if (look.dehaze != 0f) base = AdjustStages.dehaze(base, curW, curH, look.dehaze)
            if (look.texture != 0f) base = AdjustStages.texture(base, curW, curH, look.texture)
            if (look.clarity != 0f) base = AdjustStages.clarity(base, curW, curH, look.clarity)
            if (look.grain > 0f) base = AdjustStages.grain(base, look.grain)
            if (!look.colorMix.isIdentity) {
                base = ColorMath.colorMix(base, look.colorMix.hue, look.colorMix.sat, look.colorMix.lum)
            }
            if (!look.splitTone.isIdentity) {
                base = ColorMath.splitTone(
                    base,
                    look.splitTone.shadowHue, look.splitTone.shadowSat,
                    look.splitTone.highlightHue, look.splitTone.highlightSat
                )
            }
            if (!look.vignette.isIdentity) {
                base = AdjustStages.vignette(
                    base, curW, curH,
                    look.vignette.amount, look.vignette.midPoint,
                    look.vignette.feather, look.vignette.roundness
                )
            }
            val weights = maskWeights(
                assets.personMaskSrc, state, applyCrop, curW, curH, state.adjustMask
            )
            current = blendWithMask(base, current, weights)
        }
        checkActive()

        // Face + Reshape warps (face data arrives already mapped into the
        // rendered frame by the caller, with the same crop flag as here).
        val scaleOps = buildFaceScaleOps(state, assets.face)
        if (state.warpOps.isNotEmpty() || scaleOps.isNotEmpty()) {
            current = WarpMath.warp(current, curW, curH, state.warpOps, scaleOps)
        }

        // Heal spots.
        if (state.healSpots.isNotEmpty()) {
            current = HealMath.heal(current, curW, curH, state.healSpots)
        }

        // Makeup + eye brightening (face-gated).
        assets.face?.let { face ->
            val lm = face.landmarks + ("faceW" to (face.faceWidth to 0f))
            if (state.makeupLip > 0f || state.makeupBlush > 0f ||
                state.makeupShadow > 0f || state.makeupLiner > 0f
            ) {
                current = LayerRaster.applyMakeup(
                    current, curW, curH, lm,
                    state.makeupLipColor, state.makeupLip / 100f,
                    state.makeupBlush / 100f, state.makeupShadow / 100f,
                    state.makeupLiner / 100f
                )
            }
            if (state.eyesEnhance > 0f) {
                val eyes = listOfNotNull(face.landmarks["leftEye"], face.landmarks["rightEye"])
                current = LayerRaster.enhanceEyes(
                    current, curW, curH, eyes, face.faceWidth, state.eyesEnhance / 100f
                )
            }
        }

        // Tap-to-correct eye glare (no face detection needed).
        if (state.eyeFixes.isNotEmpty()) {
            current = HealMath.correctEyes(current, curW, curH, state.eyeFixes)
        }

        // Background (people-only mask, mapped through the same geometry).
        assets.personMaskSrc?.let { maskSrc ->
            if (!state.background.isIdentity) {
                val mask = mapMask(maskSrc, state, applyCrop, curW, curH)
                current = LayerRaster.applyBackground(current, curW, curH, mask, state.background)
            }
        }

        // Draw strokes, overlays, border (border is outermost).
        if (state.drawStrokes.isNotEmpty()) {
            current = LayerRaster.drawStrokes(current, curW, curH, state.drawStrokes)
        }
        if (!state.overlay.isIdentity) {
            current = LayerRaster.applyOverlay(current, curW, curH, state.overlay)
        }
        if (!state.border.isIdentity) {
            current = LayerRaster.applyBorder(current, curW, curH, state.border)
        }
        return PixelImage(current, curW, curH)
    }

    /** Builds the face-feature ellipse scale ops for the Face tool. */
    private fun buildFaceScaleOps(state: EditState, face: FaceRenderData?): List<WarpMath.ScaleOp> {
        if (face == null || state.face.isIdentity) return emptyList()
        val ops = mutableListOf<WarpMath.ScaleOp>()
        val f = state.face
        val rect = face.rect
        val cx = rect.centerX
        val cy = rect.centerY
        val rx = rect.width / 2f
        val ry = rect.height / 2f
        fun scale(p: Float) = 1f + p * 0.003f
        if (f.faceSize != 0f) {
            val s = scale(f.faceSize)
            ops.add(WarpMath.ScaleOp(cx, cy, rx, ry, s, s))
        }
        if (f.faceWidth != 0f) {
            ops.add(WarpMath.ScaleOp(cx, cy, rx, ry, scale(f.faceWidth), 1f))
        }
        if (f.contour != 0f) {
            // Slim the lower face: ellipse over the jaw area.
            ops.add(
                WarpMath.ScaleOp(
                    cx, cy + ry * 0.45f, rx * 0.9f, ry * 0.55f,
                    1f - f.contour * 0.002f, 1f
                )
            )
        }
        val lm = face.landmarks
        val fw = face.faceWidth
        lm["mouth"]?.let { (mx, my) ->
            if (f.lipSize != 0f) {
                val s = scale(f.lipSize)
                ops.add(WarpMath.ScaleOp(mx, my, fw * 0.14f, fw * 0.07f, s, s))
            }
        }
        lm["nose"]?.let { (nx, ny) ->
            if (f.noseWidth != 0f) {
                ops.add(WarpMath.ScaleOp(nx, ny, fw * 0.09f, fw * 0.12f, scale(f.noseWidth), 1f))
            }
        }
        for (key in listOf("leftEye", "rightEye")) {
            lm[key]?.let { (ex, ey) ->
                if (f.eyeSize != 0f) {
                    val s = scale(f.eyeSize)
                    ops.add(WarpMath.ScaleOp(ex, ey, fw * 0.09f, fw * 0.09f, s, s))
                }
            }
        }
        return ops
    }

    /**
     * Maps a source-frame person mask through the same geometry as the
     * photo (rotation/flips/crop/perspective) and resamples it to the
     * rendered dimensions, as a float mask (0..1, person confidence).
     */
    private fun mapMask(
        maskSrc: PixelImage, state: EditState, applyCrop: Boolean,
        targetW: Int, targetH: Int
    ): FloatArray {
        var m = applyGeometry(maskSrc, state, applyCrop)
        val t = state.transform
        if (t != null && !t.isIdentity) {
            m = PixelImage(
                WarpMath.perspectiveWarp(m.pixels, m.width, m.height, t),
                m.width, m.height
            )
        }
        val out = FloatArray(targetW * targetH)
        if (m.width == targetW && m.height == targetH) {
            for (i in out.indices) {
                out[i] = ((m.pixels[i] shr 16) and 0xFF) / 255f
            }
            return out
        }
        // Bilinear resample of the R channel.
        for (y in 0 until targetH) {
            val sy = (y + 0.5f) * m.height / targetH - 0.5f
            val y0 = sy.toInt().coerceIn(0, m.height - 1)
            val y1 = minOf(y0 + 1, m.height - 1)
            val ty = (sy - y0).coerceIn(0f, 1f)
            for (x in 0 until targetW) {
                val sx = (x + 0.5f) * m.width / targetW - 0.5f
                val x0 = sx.toInt().coerceIn(0, m.width - 1)
                val x1 = minOf(x0 + 1, m.width - 1)
                val tx = (sx - x0).coerceIn(0f, 1f)
                fun v(xx: Int, yy: Int) = ((m.pixels[yy * m.width + xx] shr 16) and 0xFF) / 255f
                val top = v(x0, y0) + (v(x1, y0) - v(x0, y0)) * tx
                val bot = v(x0, y1) + (v(x1, y1) - v(x0, y1)) * tx
                out[y * targetW + x] = top + (bot - top) * ty
            }
        }
        return out
    }


    /**
     * Tone stage: one composed LUT (Look first, then adjustments) plus
     * the per-pixel stage (mono blend, temperature/tint gains, vibrance,
     * saturation). Pure tone work only — no sharpening, no deeper
     * stages. Used both for the main render and for the adjustment-mask
     * base (look-only tone), so the two always agree exactly.
     */
    private fun applyTone(geo: PixelImage, look: ResolvedLook, adj: Adjustments): IntArray {
        val lut = AdjustMath.composeLuts(look.toneLut, AdjustMath.buildToneLut(adj))
        val lutR = IntArray(256) { (lut[it] * 255f + 0.5f).toInt().coerceIn(0, 255) }
        val temperature = (adj.temperature + look.temperature).coerceIn(-100f, 100f)
        val tint = (adj.tint + look.tint).coerceIn(-100f, 100f)
        val vibrance = (adj.vibrance + look.vibrance).coerceIn(-100f, 100f)
        val saturation = (adj.saturation + look.saturation).coerceIn(-100f, 100f)
        val mono = look.monoAmount
        val needsPixelStage = temperature != 0f || tint != 0f || vibrance != 0f || saturation != 0f || mono > 0f
        val tt = temperature / 100f
        val rGain = 1f + 0.18f * tt
        val bGain = 1f - 0.18f * tt
        val gGain = 1f - 0.14f * (tint / 100f)
        val vibV = vibrance / 100f
        // v0.2.5 lag fix: v^1.6 is loop-invariant per render (vibV is
        // constant across pixels) — compute once here instead of once per
        // pixel. Only read when vibV > 0 (the negative branch never uses it).
        val vibPow = if (vibV > 0f) 0.64f * vibV.toDouble().pow(1.6).toFloat() else 0f
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
                    AdjustMath.vibranceLin(rf, gf, bf, vibV, vibPow, vibBuf)
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
        return out
    }

    /**
     * Person-mask weights in the rendered frame for the adjustment mask.
     * SUBJECT = person confidence; BACKGROUND = its inverse. With no
     * person mask, or no confident person in it (max <= 0.5, the same
     * threshold FaceAnalysis uses for personFound), SUBJECT selects
     * nothing (all zeros) and BACKGROUND selects everything (all ones).
     */
    private fun maskWeights(
        maskSrc: PixelImage?, state: EditState, applyCrop: Boolean,
        targetW: Int, targetH: Int, mask: AdjustMask
    ): FloatArray {
        if (maskSrc == null) {
            return FloatArray(targetW * targetH) {
                if (mask == AdjustMask.BACKGROUND) 1f else 0f
            }
        }
        val mapped = mapMask(maskSrc, state, applyCrop, targetW, targetH)
        val hasPerson = mapped.any { it > 0.5f }
        if (!hasPerson) {
            return FloatArray(targetW * targetH) {
                if (mask == AdjustMask.BACKGROUND) 1f else 0f
            }
        }
        return if (mask == AdjustMask.BACKGROUND) {
            FloatArray(mapped.size) { 1f - mapped[it] }
        } else {
            mapped
        }
    }

    /** Per-pixel blend: out = base * (1 - w) + adjusted * w, rounded. */
    private fun blendWithMask(base: IntArray, adjusted: IntArray, w: FloatArray): IntArray {
        val out = IntArray(adjusted.size)
        for (i in adjusted.indices) {
            val weight = w[i].coerceIn(0f, 1f)
            val bp = base[i]
            val ap = adjusted[i]
            val r = ((bp shr 16) and 0xFF) * (1f - weight) + ((ap shr 16) and 0xFF) * weight + 0.5f
            val g = ((bp shr 8) and 0xFF) * (1f - weight) + ((ap shr 8) and 0xFF) * weight + 0.5f
            val b = (bp and 0xFF) * (1f - weight) + (ap and 0xFF) * weight + 0.5f
            out[i] = (ap and -0x1000000) or
                (r.toInt().coerceIn(0, 255) shl 16) or
                (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
        }
        return out
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
