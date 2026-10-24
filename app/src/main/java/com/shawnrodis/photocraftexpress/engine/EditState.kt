package com.shawnrodis.photocraftexpress.engine

/**
 * The 11 Express-style adjustment sliders, plus the deeper v0.2.0 sliders.
 * All values are -100..100 with 0 = identity, except [sharpen],
 * [reduceColorNoise], [reduceLuminanceNoise] and [grain], which are 0..100.
 */
data class Adjustments(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val vibrance: Float = 0f,
    val saturation: Float = 0f,
    val sharpen: Float = 0f,
    val texture: Float = 0f,
    val clarity: Float = 0f,
    val dehaze: Float = 0f,
    val reduceColorNoise: Float = 0f,
    val reduceLuminanceNoise: Float = 0f,
    val fade: Float = 0f,
    val grain: Float = 0f
) {
    val isIdentity: Boolean
        get() = exposure == 0f && contrast == 0f && highlights == 0f && shadows == 0f &&
            whites == 0f && blacks == 0f && temperature == 0f && tint == 0f &&
            vibrance == 0f && saturation == 0f && sharpen == 0f &&
            texture == 0f && clarity == 0f && dehaze == 0f &&
            reduceColorNoise == 0f && reduceLuminanceNoise == 0f &&
            fade == 0f && grain == 0f

    /** Any of the sliders that feed the tonal anchor curve. */
    val hasTonal: Boolean
        get() = highlights != 0f || shadows != 0f || whites != 0f || blacks != 0f

    /** Any of the sliders applied per-pixel after the tone LUT. */
    val hasPixelStage: Boolean
        get() = temperature != 0f || tint != 0f || vibrance != 0f || saturation != 0f
}

/** ML Kit face data, already mapped into rendered-image normalized coords. */
data class FaceRenderData(
    val rect: CropRect,
    /** Landmark name -> normalized point. Keys: leftEye, rightEye, nose, mouth, leftCheek, rightCheek. */
    val landmarks: Map<String, Pair<Float, Float>>,
    /** Face width as a fraction of image width. */
    val faceWidth: Float
)

/**
 * Runtime-only render inputs (never part of undo history): face data and
 * the person mask, both derived from the source photo by ML Kit and mapped
 * into the rendered frame by the caller.
 */
data class RenderAssets(
    val face: FaceRenderData? = null,
    /** Person mask in SOURCE frame (R channel = confidence 0..255); render() maps it through geometry. */
    val personMaskSrc: PixelImage? = null
)

/**
 * Immutable snapshot of every edit applied to a photo. Undo/redo is a stack
 * of these. [crop] lives in the *display frame* — the coordinate space of
 * the photo after [rotation] and the flips have been applied — so the crop
 * overlay can draw it directly; rotation/flip edits remap it (see
 * [CropGeometry]). [transform] corners live in the *cropped* frame.
 */
data class EditState(
    val lookId: String = Looks.ORIGINAL_ID,
    val lookIntensity: Float = 100f,
    val adj: Adjustments = Adjustments(),
    val rotation: Int = 0, // clockwise degrees: 0, 90, 180, 270
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val crop: CropRect = CropRect.FULL,
    val transform: WarpMath.Quad? = null, // perspective correction, null = identity
    val splitTone: SplitTone = SplitTone(),
    val colorMix: ColorMix = ColorMix(),
    /**
     * Which region Adjustments-panel edits apply to (Express mask
     * selector). Deliberately excluded from [isIdentity]: a mask
     * selection alone, with identity adjustments, renders identically
     * to the untouched photo.
     */
    val adjustMask: AdjustMask = AdjustMask.FULL,
    val blur: BlurFx = BlurFx(),
    val vignette: Vignette = Vignette(),
    val smooth: Float = 0f, // 0..100, global smoothing (not face-masked)
    val face: FaceAdjust = FaceAdjust(),
    val warpOps: List<WarpMath.BrushOp> = emptyList(), // Reshape liquify
    val healSpots: List<HealMath.HealSpot> = emptyList(),
    val eyeFixes: List<HealMath.EyeFix> = emptyList(), // red/pet-eye taps
    val drawStrokes: List<DrawStroke> = emptyList(),
    val textItems: List<TextItem> = emptyList(),
    val stickerItems: List<StickerItem> = emptyList(),
    val insertedImages: List<InsertedImage> = emptyList(),
    val overlay: OverlayFx = OverlayFx(),
    val border: BorderFx = BorderFx(),
    val background: BackgroundFx = BackgroundFx(),
    val makeupLipColor: Int = 0xFFD94F5C.toInt(),
    val makeupLip: Float = 0f, // 0..100
    val makeupBlush: Float = 0f,
    val makeupShadow: Float = 0f,
    val makeupLiner: Float = 0f,
    val eyesEnhance: Float = 0f // 0..100, face-gated eye brightening
) {
    val isIdentity: Boolean
        get() = lookId == Looks.ORIGINAL_ID && adj.isIdentity && rotation == 0 &&
            !flipH && !flipV && crop == CropRect.FULL &&
            (transform == null || transform.isIdentity) &&
            splitTone.isIdentity && colorMix.isIdentity && blur.isIdentity &&
            vignette.isIdentity && smooth == 0f && face.isIdentity &&
            warpOps.isEmpty() && healSpots.isEmpty() && eyeFixes.isEmpty() &&
            drawStrokes.isEmpty() && textItems.isEmpty() && stickerItems.isEmpty() &&
            insertedImages.isEmpty() && overlay.isIdentity && border.isIdentity &&
            background.isIdentity && makeupLip == 0f && makeupBlush == 0f &&
            makeupShadow == 0f && makeupLiner == 0f && eyesEnhance == 0f
}
