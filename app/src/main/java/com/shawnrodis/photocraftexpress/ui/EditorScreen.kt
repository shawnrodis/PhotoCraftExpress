package com.shawnrodis.photocraftexpress.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawnrodis.photocraftexpress.engine.AdjustMask
import com.shawnrodis.photocraftexpress.engine.Adjustments
import com.shawnrodis.photocraftexpress.engine.AutoEnhance
import com.shawnrodis.photocraftexpress.engine.BackgroundFx
import com.shawnrodis.photocraftexpress.engine.BlurFx
import com.shawnrodis.photocraftexpress.engine.BorderFx
import com.shawnrodis.photocraftexpress.engine.ColorMix
import com.shawnrodis.photocraftexpress.engine.CropCorner
import com.shawnrodis.photocraftexpress.engine.CropDragKind
import com.shawnrodis.photocraftexpress.engine.CropEdge
import com.shawnrodis.photocraftexpress.engine.CropGeometry
import com.shawnrodis.photocraftexpress.engine.CropRect
import com.shawnrodis.photocraftexpress.engine.cropHandleAt
import com.shawnrodis.photocraftexpress.engine.fitFrame
import com.shawnrodis.photocraftexpress.engine.fractionToNorm
import com.shawnrodis.photocraftexpress.engine.DrawStroke
import com.shawnrodis.photocraftexpress.engine.EditHistory
import com.shawnrodis.photocraftexpress.engine.EditState
import com.shawnrodis.photocraftexpress.engine.ExportSize
import com.shawnrodis.photocraftexpress.engine.ExportSizeMode
import com.shawnrodis.photocraftexpress.engine.FaceAdjust
import com.shawnrodis.photocraftexpress.engine.FaceRenderData
import com.shawnrodis.photocraftexpress.engine.HealMath
import com.shawnrodis.photocraftexpress.engine.InsertedImage
import com.shawnrodis.photocraftexpress.engine.Looks
import com.shawnrodis.photocraftexpress.engine.MaskPoint
import com.shawnrodis.photocraftexpress.engine.OverlayFx
import com.shawnrodis.photocraftexpress.engine.PixelImage
import com.shawnrodis.photocraftexpress.engine.PixelPipeline
import com.shawnrodis.photocraftexpress.engine.RenderAssets
import com.shawnrodis.photocraftexpress.engine.SplitTone
import com.shawnrodis.photocraftexpress.engine.StickerItem
import com.shawnrodis.photocraftexpress.engine.StickerKind
import com.shawnrodis.photocraftexpress.engine.TextFont
import com.shawnrodis.photocraftexpress.engine.TextItem
import com.shawnrodis.photocraftexpress.engine.Vignette
import com.shawnrodis.photocraftexpress.engine.ZoomState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import com.shawnrodis.photocraftexpress.engine.WarpMath
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The v0.2.0 editor: 16-tool carousel, canvas gestures for every brush /
 * tap / drag tool, live preview rendered through the full pipeline, and
 * an export sheet with output-size control. Undo/redo covers every edit:
 * sliders commit on release, taps and chips commit immediately, drags
 * commit once on release (live previews during a drag are drafts).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    source: PixelImage,
    sourceUri: Uri,
    onBack: () -> Unit,
    onCheckForUpdates: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- Edit history -------------------------------------------------
    val history = remember { EditHistory(EditState()) }
    var tick by remember { mutableStateOf(0) }
    var draft by remember { mutableStateOf<EditState?>(null) }
    @Suppress("UNUSED_VARIABLE")
    val historyTick = tick // read so recomposition follows history changes
    val cur = draft ?: history.current
    val curRef = rememberUpdatedState(cur)

    fun draftTo(next: EditState) {
        draft = next
    }

    fun commitDraft() {
        val d = draft ?: return
        history.commit(d)
        draft = null
        tick++
    }

    fun commitDirect(next: EditState) {
        draft = null
        history.commit(next)
        tick++
    }

    fun undo() {
        if (history.undo() != null) {
            draft = null
            tick++
        }
    }

    fun redo() {
        if (history.redo() != null) {
            draft = null
            tick++
        }
    }

    // --- Tool / panel state -------------------------------------------
    var tool by remember { mutableStateOf(EditorTool.Looks) }
    var adjustTab by remember { mutableStateOf(AdjustTab.ADJUSTMENTS) }
    var cropTab by remember { mutableStateOf(CropTab.ASPECT) }
    var aspectSel by remember { mutableStateOf<AspectSel>(AspectSel.Original) }
    var textTab by remember { mutableStateOf(TextTab.STYLES) }
    var borderTab by remember { mutableStateOf(BorderTab.BASIC) }
    var insertMode by remember { mutableStateOf(InsertMode.PHOTO) }
    var eyesMode by remember { mutableStateOf(EyesMode.ENHANCE) }
    var reshapeMode by remember { mutableStateOf(WarpMath.BrushMode.PUSH) }
    var reshapeBrush by remember { mutableStateOf(0.12f) }
    var healBrush by remember { mutableStateOf(0.05f) }
    var drawColor by remember { mutableStateOf(-0x1) }
    var drawWidth by remember { mutableStateOf(0.02f) }
    var drawOpacity by remember { mutableStateOf(1f) }
    var insertBrush by remember { mutableStateOf(0.06f) }
    var eyeBrush by remember { mutableStateOf(0.04f) }
    var stickerKind by remember { mutableStateOf(StickerKind.STAR) }
    var selTextId by remember { mutableStateOf<Long?>(null) }
    var selStickerId by remember { mutableStateOf<Long?>(null) }
    var selInsertedId by remember { mutableStateOf<Long?>(null) }
    var nextLayerId by remember { mutableStateOf(1L) }
    var pendingMask by remember { mutableStateOf<List<MaskPoint>>(emptyList()) }
    var showOriginal by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportSize by remember { mutableStateOf(ExportSize()) }
    var customW by remember { mutableStateOf("") }
    var customH by remember { mutableStateOf("") }
    // Export sheet settings (defaults match Express: JPEG at 85%).
    var exportFormat by remember { mutableStateOf(ExportFormat.JPEG) }
    var exportQuality by remember { mutableStateOf(Exporter.DEFAULT_QUALITY) }
    var exportWatermark by remember { mutableStateOf(WatermarkStyle.NONE) }
    // Top bar state: split compare, its divider, menu + About dialog.
    var splitCompare by remember { mutableStateOf(false) }
    val splitCompareRef = rememberUpdatedState(splitCompare)
    var divider by remember { mutableStateOf(0.5f) }
    var menuOpen by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    // --- Source, analysis, preview -------------------------------------
    val sourceBitmap = remember(source) { BitmapBridge.toBitmap(source) }
    var analysis by remember { mutableStateOf<FaceAnalysis.Result?>(null) }
    LaunchedEffect(sourceBitmap) {
        analysis = FaceAnalysis.analyze(sourceBitmap)
    }
    val hasFace = analysis?.faces?.isNotEmpty() == true
    val personFound = analysis?.personFound == true

    var insertedBitmaps by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }

    // Looks thumbnails, rendered LAZILY (v0.2.2): with 87 presets,
    // rendering every thumbnail eagerly on photo load would stall the
    // editor. The strip reports its visible items (plus immediate
    // neighbours) and only those render, into this cache; items outside
    // the viewport keep the dark placeholder until scrolled into view.
    // The cache resets with the photo.
    var lookThumbs by remember(sourceBitmap) { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    val lookThumbBase = remember(sourceBitmap) {
        BitmapBridge.downscale(BitmapBridge.toPixelImage(sourceBitmap), 240)
    }
    val lookThumbScope = rememberCoroutineScope()
    val lookThumbInflight = remember(sourceBitmap) { mutableSetOf<String>() }
    fun requestLookThumbs(ids: List<String>) {
        val missing = ids.filter { it !in lookThumbs && it !in lookThumbInflight }
        if (missing.isEmpty()) return
        lookThumbInflight += missing
        lookThumbScope.launch {
            val rendered = withContext(Dispatchers.Default) {
                missing.associate { id ->
                    id to BitmapBridge.toBitmap(
                        PixelPipeline.render(
                            lookThumbBase,
                            EditState(lookId = id, lookIntensity = 100f)
                        )
                    )
                }
            }
            lookThumbs = lookThumbs + rendered
            lookThumbInflight -= missing
        }
    }

    // Per-adjustment live thumbnails for the Adjustments strip: each
    // adjustment alone at a demo value, rendered from a small downscale
    // of the current photo (same pattern as lookThumbs).
    var adjustThumbs by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    LaunchedEffect(sourceBitmap) {
        adjustThumbs = withContext(Dispatchers.Default) {
            val small = BitmapBridge.downscale(BitmapBridge.toPixelImage(sourceBitmap), 160)
            adjustThumbStates().associate { (name, demoAdj) ->
                name to BitmapBridge.toBitmap(
                    PixelPipeline.render(small, EditState(adj = demoAdj))
                )
            }
        }
    }

    // Mask-selector thumbnails: Full is the plain photo; Subject dims
    // the background around the person, Background dims the person.
    // Without a detected person all three fall back to the plain photo
    // and the panel shows its "No person found" notice instead.
    var maskThumbs by remember { mutableStateOf<Map<AdjustMask, Bitmap>>(emptyMap()) }
    LaunchedEffect(sourceBitmap, analysis) {
        maskThumbs = withContext(Dispatchers.Default) {
            val smallPx = BitmapBridge.downscale(BitmapBridge.toPixelImage(sourceBitmap), 120)
            val fullBmp = BitmapBridge.toBitmap(smallPx)
            val mask = analysis?.personMask
            if (mask == null || analysis?.personFound != true) {
                mapOf(
                    AdjustMask.FULL to fullBmp,
                    AdjustMask.SUBJECT to fullBmp,
                    AdjustMask.BACKGROUND to fullBmp
                )
            } else {
                fun masked(darkenPerson: Boolean): Bitmap {
                    val out = IntArray(smallPx.pixels.size)
                    for (i in smallPx.pixels.indices) {
                        val x = (i % smallPx.width).toFloat() / smallPx.width
                        val y = (i / smallPx.width).toFloat() / smallPx.height
                        val mx = (x * mask.width).toInt().coerceIn(0, mask.width - 1)
                        val my = (y * mask.height).toInt().coerceIn(0, mask.height - 1)
                        // R channel = confidence 0..255 (FaceAnalysis).
                        val conf = ((mask.pixels[my * mask.width + mx] shr 16) and 0xFF) / 255f
                        val darken = if (darkenPerson) conf else 1f - conf
                        val p = smallPx.pixels[i]
                        val f = 1f - 0.5f * darken
                        out[i] = (p and -0x1000000) or
                            ((((p shr 16) and 0xFF) * f + 0.5f).toInt().coerceIn(0, 255) shl 16) or
                            ((((p shr 8) and 0xFF) * f + 0.5f).toInt().coerceIn(0, 255) shl 8) or
                            ((p and 0xFF) * f + 0.5f).toInt().coerceIn(0, 255)
                    }
                    return BitmapBridge.toBitmap(PixelImage(out, smallPx.width, smallPx.height))
                }
                mapOf(
                    AdjustMask.FULL to fullBmp,
                    AdjustMask.SUBJECT to masked(darkenPerson = false),
                    AdjustMask.BACKGROUND to masked(darkenPerson = true)
                )
            }
        }
    }

    // While the Crop tool is active the preview shows the uncropped photo
    // (the crop overlay draws the rect) — except on the Transform tab,
    // where the cropped + warped photo is the thing being edited.
    val applyCrop = tool != EditorTool.Crop || cropTab == CropTab.TRANSFORM

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    // The 1024px preview base is cached: previously every preview render
    // converted the full-resolution source bitmap and downscaled it again.
    // The pipeline never mutates its input (every stage allocates), so
    // sharing this base across renders is safe.
    val previewBase = remember(sourceBitmap) {
        BitmapBridge.downscale(BitmapBridge.toPixelImage(sourceBitmap), 1024)
    }
    // Live-slider render worker (v0.2.2 fix). Slider drags must update
    // the canvas DURING the drag, not only on release. The v0.2.1
    // approach — LaunchedEffect(cur) with cooperative cancellation —
    // could never do that: drag ticks arrive faster than one render
    // completes, so every render was cancelled before it finished and
    // only the release value ever surfaced. Instead, drag values are
    // offered to a conflated flow (only the latest survives) and a
    // single worker renders each queued value to completion, so the
    // preview advances at render rate throughout the drag. History
    // still commits once, on release. replay = 1 matters: without it,
    // an offer emitted before the worker (re)subscribes is lost — the
    // first preview after photo load would never render.
    val previewRequests = remember(sourceBitmap) {
        MutableSharedFlow<PreviewRequest>(
            replay = 1,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }
    LaunchedEffect(cur, sourceBitmap, analysis, insertedBitmaps, applyCrop, tool) {
        previewRequests.tryEmit(
            PreviewRequest(cur, analysis, insertedBitmaps, applyCrop, tool)
        )
    }
    LaunchedEffect(sourceBitmap) {
        renderLatest(previewRequests, { req ->
            val faceData: FaceRenderData? = req.analysis?.faces?.firstOrNull()?.let {
                FaceAnalysis.mapFace(
                    it, req.state, req.applyCrop,
                    sourceBitmap.width.toFloat() / sourceBitmap.height
                )
            }
            // Stage-boundary aborts still guard WORKER cancellation (a
            // new photo replacing the source); they no longer fire on
            // new drag ticks, which is the whole point of the worker.
            val ctx = currentCoroutineContext()
            val rendered = PixelPipeline.render(
                previewBase, req.state, req.applyCrop,
                RenderAssets(faceData, req.analysis?.personMask),
                isActive = { ctx.isActive }
            )
            ctx.ensureActive()
            var bmp = BitmapBridge.toBitmap(rendered)
            // Layers live in the cropped frame; while cropping (uncropped
            // preview) they would draw in the wrong place, so hide them.
            if (req.tool != EditorTool.Crop) {
                bmp = LayerComposite.composite(bmp, req.state, req.insertedBitmaps)
            }
            ctx.ensureActive()
            bmp
        }, { preview = it })
    }

    // Split-compare "before": the same geometry (rotation/flips/crop/
    // transform) with every pixel and layer edit stripped, so before and
    // after share dimensions and the divider lines up exactly. Only
    // rendered while split compare is on, to avoid doubling render cost.
    var beforeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(
        splitCompare, sourceBitmap,
        cur.rotation, cur.flipH, cur.flipV, cur.crop, cur.transform
    ) {
        if (!splitCompare) {
            beforeBitmap = null
            return@LaunchedEffect
        }
        val geometryState = cur.copy(
            lookId = Looks.ORIGINAL_ID, lookIntensity = 100f,
            adj = Adjustments(), splitTone = SplitTone(), colorMix = ColorMix(),
            blur = BlurFx(), vignette = Vignette(), smooth = 0f, face = FaceAdjust(),
            warpOps = emptyList(), healSpots = emptyList(), eyeFixes = emptyList(),
            drawStrokes = emptyList(), textItems = emptyList(),
            stickerItems = emptyList(), insertedImages = emptyList(),
            overlay = OverlayFx(), border = BorderFx(), background = BackgroundFx(),
            makeupLip = 0f, makeupBlush = 0f, makeupShadow = 0f, makeupLiner = 0f,
            eyesEnhance = 0f, adjustMask = AdjustMask.FULL
        )
        beforeBitmap = withContext(Dispatchers.Default) {
            BitmapBridge.toBitmap(
                PixelPipeline.render(
                    BitmapBridge.downscale(BitmapBridge.toPixelImage(sourceBitmap), 1024),
                    geometryState, true
                )
            )
        }
    }

    /** One-tap auto-enhance: histogram deltas applied as an undoable edit. */
    fun autoEnhance() {
        val base = preview ?: sourceBitmap
        scope.launch {
            val delta = withContext(Dispatchers.Default) {
                AutoEnhance.deltasFor(BitmapBridge.toPixelImage(base))
            }
            val current = curRef.value
            commitDirect(current.copy(adj = AutoEnhance.apply(current.adj, delta)))
        }
    }

    /** Download button: straight to Gallery with the sheet's settings. */
    fun quickSave() {
        scope.launch {
            val msg = withContext(Dispatchers.Default) {
                Exporter.export(
                    context, sourceUri, curRef.value, exportSize,
                    exportFormat, exportQuality, exportWatermark, ExportAction.SAVE
                )
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    /** ⋮ menu Reset: an undoable return to the untouched photo. */
    fun resetEdits() {
        commitDirect(EditState())
        aspectSel = AspectSel.Original
        selTextId = null
        selStickerId = null
        selInsertedId = null
        splitCompare = false
    }

    // --- Insert photo picker -------------------------------------------
    val insertPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val id = nextLayerId++
            val layer = InsertedImage(
                id = id,
                uri = uri.toString(),
                x = 0.5f,
                y = 0.5f,
                scale = 0.5f,
                maskStrokes = pendingMask
            )
            pendingMask = emptyList()
            commitDirect(
                curRef.value.copy(insertedImages = curRef.value.insertedImages + layer)
            )
            selInsertedId = id
            scope.launch(Dispatchers.IO) {
                val px = BitmapBridge.loadImage(context, uri, 1200)
                if (px != null) {
                    val bmp = BitmapBridge.toBitmap(px)
                    insertedBitmaps = insertedBitmaps + (id.toString() to bmp)
                }
            }
        }
    }

    // --- Canvas gestures ------------------------------------------------
    // Drag bookkeeping (not State: only the draft EditState recomposes).
    var dragBase by remember { mutableStateOf<EditState?>(null) }
    var dragOps by remember { mutableStateOf<List<WarpMath.BrushOp>>(emptyList()) }
    var dragLast by remember { mutableStateOf(0f to 0f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // Canvas zoom: a view transform only, never part of the edit state.
    var zoom by remember { mutableStateOf(ZoomState.IDENTITY) }
    LaunchedEffect(source, sourceUri) { zoom = ZoomState.IDENTITY }

    fun hitTest(x: Float, y: Float): Triple<String, Long, Float>? {
        var best: Triple<String, Long, Float>? = null
        fun consider(kind: String, id: Long, d: Float, threshold: Float) {
            if (d < threshold && (best == null || d < best!!.third)) {
                best = Triple(kind, id, d)
            }
        }
        for (item in cur.insertedImages) {
            val dx = x - item.x
            val dy = y - item.y
            if (kotlin.math.abs(dx) < item.scale * 0.6f && kotlin.math.abs(dy) < item.scale * 0.6f) {
                consider("inserted", item.id, kotlin.math.abs(dx) + kotlin.math.abs(dy), 1f)
            }
        }
        for (item in cur.textItems) {
            val dx = x - item.x
            val dy = y - item.y
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            consider("text", item.id, d, maxOf(0.12f, item.size * 1.2f))
        }
        for (item in cur.stickerItems) {
            val dx = x - item.x
            val dy = y - item.y
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            consider("sticker", item.id, d, item.scale)
        }
        return best
    }

    fun onCanvasTap(x: Float, y: Float) {
        when (tool) {
            EditorTool.Heal -> {
                commitDirect(
                    cur.copy(healSpots = cur.healSpots + HealMath.HealSpot(x, y, healBrush))
                )
            }
            EditorTool.Eyes -> {
                when (eyesMode) {
                    EyesMode.RED_EYE -> commitDirect(
                        cur.copy(eyeFixes = cur.eyeFixes + HealMath.EyeFix(x, y, eyeBrush, pet = false))
                    )
                    EyesMode.PET_EYE -> commitDirect(
                        cur.copy(eyeFixes = cur.eyeFixes + HealMath.EyeFix(x, y, eyeBrush, pet = true))
                    )
                    EyesMode.ENHANCE -> Unit
                }
            }
            EditorTool.Stickers -> {
                val hit = hitTest(x, y)
                if (hit != null && hit.first == "sticker") {
                    selStickerId = hit.second
                } else {
                    val id = nextLayerId++
                    commitDirect(
                        cur.copy(
                            stickerItems = cur.stickerItems +
                                StickerItem(id = id, kind = stickerKind, x = x, y = y)
                        )
                    )
                    selStickerId = id
                }
            }
            EditorTool.Text -> {
                val hit = hitTest(x, y)
                selTextId = if (hit != null && hit.first == "text") hit.second else null
            }
            EditorTool.InsertObjects -> {
                val hit = hitTest(x, y)
                selInsertedId = if (hit != null && hit.first == "inserted") hit.second else null
            }
            else -> Unit
        }
    }

    fun onCanvasDragStart(x: Float, y: Float) {
        dragBase = cur
        dragOps = emptyList()
        dragLast = x to y
        when (tool) {
            EditorTool.Draw -> {
                draftTo(
                    cur.copy(
                        drawStrokes = cur.drawStrokes +
                            DrawStroke(listOf(x to y), drawColor, drawWidth, drawOpacity)
                    )
                )
            }
            EditorTool.InsertObjects -> {
                if (insertMode == InsertMode.BRUSH) {
                    val sel = cur.insertedImages.firstOrNull { it.id == selInsertedId }
                    if (sel != null) {
                        draftTo(
                            cur.copy(
                                insertedImages = cur.insertedImages.map {
                                    if (it.id == sel.id) {
                                        it.copy(maskStrokes = it.maskStrokes + MaskPoint(x, y, insertBrush))
                                    } else {
                                        it
                                    }
                                }
                            )
                        )
                    } else {
                        pendingMask = pendingMask + MaskPoint(x, y, insertBrush)
                    }
                } else {
                    val hit = hitTest(x, y)
                    if (hit != null && hit.first == "inserted") selInsertedId = hit.second
                }
            }
            EditorTool.Text -> {
                val hit = hitTest(x, y)
                if (hit != null && hit.first == "text") selTextId = hit.second
            }
            EditorTool.Stickers -> {
                val hit = hitTest(x, y)
                if (hit != null && hit.first == "sticker") selStickerId = hit.second
            }
            else -> Unit
        }
    }

    fun onCanvasDrag(x: Float, y: Float) {
        val base = dragBase ?: return
        when (tool) {
            EditorTool.Draw -> {
                val strokes = cur.drawStrokes
                if (strokes.isEmpty()) return
                val last = strokes.last()
                draftTo(
                    cur.copy(
                        drawStrokes = strokes.dropLast(1) +
                            last.copy(points = last.points + (x to y))
                    )
                )
            }
            EditorTool.Reshape -> {
                val (lx, ly) = dragLast
                val op = WarpMath.BrushOp(
                    mode = reshapeMode,
                    cx = x, cy = y,
                    radius = reshapeBrush,
                    strength = 0.9f,
                    dx = if (reshapeMode == WarpMath.BrushMode.PUSH) x - lx else 0f,
                    dy = if (reshapeMode == WarpMath.BrushMode.PUSH) y - ly else 0f
                )
                dragOps = dragOps + op
                draftTo(base.copy(warpOps = base.warpOps + dragOps))
                dragLast = x to y
            }
            EditorTool.InsertObjects -> {
                if (insertMode == InsertMode.BRUSH) {
                    val sel = cur.insertedImages.firstOrNull { it.id == selInsertedId }
                    if (sel != null) {
                        draftTo(
                            cur.copy(
                                insertedImages = cur.insertedImages.map {
                                    if (it.id == sel.id) {
                                        it.copy(maskStrokes = it.maskStrokes + MaskPoint(x, y, insertBrush))
                                    } else {
                                        it
                                    }
                                }
                            )
                        )
                    } else {
                        pendingMask = pendingMask + MaskPoint(x, y, insertBrush)
                    }
                } else {
                    val sel = cur.insertedImages.firstOrNull { it.id == selInsertedId }
                    if (sel != null) {
                        draftTo(
                            cur.copy(
                                insertedImages = cur.insertedImages.map {
                                    if (it.id == sel.id) it.copy(x = x, y = y) else it
                                }
                            )
                        )
                    }
                }
            }
            EditorTool.Text -> {
                val id = selTextId ?: return
                draftTo(
                    cur.copy(
                        textItems = cur.textItems.map {
                            if (it.id == id) it.copy(x = x, y = y) else it
                        }
                    )
                )
            }
            EditorTool.Stickers -> {
                val id = selStickerId ?: return
                draftTo(
                    cur.copy(
                        stickerItems = cur.stickerItems.map {
                            if (it.id == id) it.copy(x = x, y = y) else it
                        }
                    )
                )
            }
            EditorTool.Adjustments -> {
                if (adjustTab == AdjustTab.BLUR && cur.blur.mode == com.shawnrodis.photocraftexpress.engine.BlurMode.RADIAL) {
                    draftTo(cur.copy(blur = cur.blur.copy(cx = x, cy = y)))
                }
            }
            else -> Unit
        }
    }

    fun onCanvasDragEnd() {
        dragBase = null
        dragOps = emptyList()
        commitDraft()
    }

    // --- Crop drag state (v0.2.3: gate-driven, zoom-aware) -----------
    // The gate owns crop input now, so these live here, not in the
    // overlay. A drag drafts every move and commits once on release,
    // like every other tool drag.
    var cropDragStartRect by remember { mutableStateOf<CropRect?>(null) }
    var cropDragStartNorm by remember { mutableStateOf(0f to 0f) }
    val cropDensity = LocalDensity.current
    // Handle grab radius in screen px: far beyond the 20dp visible
    // handle arm, and constant at any zoom (the hit test runs in
    // screen space, not in zoomed image space).
    val cropHandleRadiusPx = with(cropDensity) { 56.dp.toPx() }

    /** Aspect lock in normalized units, or null for free crops. */
    fun cropAspectNorm(): Float? {
        val pv = preview ?: return null
        val ratio = (aspectSel as? AspectSel.Fixed)?.ratio ?: return null
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return ratio
        val fit = fitFrame(
            canvasSize.width.toFloat(), canvasSize.height.toFloat(),
            pv.width, pv.height
        )
        if (fit.dispW <= 0f || fit.dispH <= 0f) return ratio
        // Normalized ratio = pixel ratio scaled by the frame: a 1:1
        // pixel square is NOT 1:1 in normalized units unless the frame
        // itself is square (the old overlay passed the raw ratio here,
        // so aspect-locked corner drags held the wrong ratio on
        // non-square photos).
        return ratio * fit.dispH / fit.dispW
    }

    fun onCropHitTest(px: Float, py: Float): CropDragKind {
        val pv = preview ?: return CropDragKind.None
        if (canvasSize.width <= 0) return CropDragKind.None
        val fit = fitFrame(
            canvasSize.width.toFloat(), canvasSize.height.toFloat(),
            pv.width, pv.height
        )
        return cropHandleAt(
            cur.crop, fit, zoom, px, py, cropHandleRadiusPx
        )
    }

    fun onCropDragStart(kind: CropDragKind, fx: Float, fy: Float) {
        val pv = preview ?: return
        if (canvasSize.width <= 0) return
        val fit = fitFrame(
            canvasSize.width.toFloat(), canvasSize.height.toFloat(),
            pv.width, pv.height
        )
        cropDragStartRect = cur.crop
        cropDragStartNorm = fractionToNorm(fit, fx, fy)
    }

    fun onCropDrag(kind: CropDragKind, fx: Float, fy: Float) {
        val start = cropDragStartRect ?: return
        val pv = preview ?: return
        if (canvasSize.width <= 0) return
        val fit = fitFrame(
            canvasSize.width.toFloat(), canvasSize.height.toFloat(),
            pv.width, pv.height
        )
        val (nx, ny) = fractionToNorm(fit, fx, fy)
        val (sx, sy) = cropDragStartNorm
        val next = when (kind) {
            is CropDragKind.Corner -> CropGeometry.resizeCorner(
                start, kind.corner, nx, ny, cropAspectNorm()
            )
            is CropDragKind.Edge -> CropGeometry.resizeEdge(
                start, kind.edge, nx, ny, cropAspectNorm()
            )
            CropDragKind.Move -> CropGeometry.moveRect(start, nx - sx, ny - sy)
            CropDragKind.None -> cur.crop
        }
        if (next != cur.crop) draftTo(cur.copy(crop = next))
    }

    fun onCropDragEnd() {
        cropDragStartRect = null
        commitDraft()
    }

    fun onCropDragCancel() {
        // Second finger mid-drag: restore the pre-drag rect without
        // committing, so the pinch starts clean.
        cropDragStartRect?.let { draftTo(cur.copy(crop = it)) }
        cropDragStartRect = null
    }

    val gestureModifier = canvasGestureModifier(
        // v0.2.3: the gate runs during Crop too — it owns crop drags
        // and pinch/double-tap zoom there. Split-compare keeps the gate
        // off (its divider has its own drag) for non-Crop tools; the
        // Crop tool never shows the split view.
        active = tool == EditorTool.Crop || !splitCompare,
        zoom = zoom,
        onZoomChange = { zoom = it },
        onDoubleTap = { x, y -> zoom = zoom.toggleAt(x, y) },
        onDragCancel = {
            // A second finger landing mid-drag restores the drag's
            // start state without committing, so the pinch starts clean.
            dragBase?.let { draftTo(it) }
            dragBase = null
            dragOps = emptyList()
        },
        onTap = { x, y -> onCanvasTap(x, y) },
        onDragStart = { x, y -> onCanvasDragStart(x, y) },
        onDrag = { x, y -> onCanvasDrag(x, y) },
        onDragEnd = { onCanvasDragEnd() },
        cropHitTest = if (tool == EditorTool.Crop && cropTab != CropTab.TRANSFORM) {
            { px, py -> onCropHitTest(px, py) }
        } else {
            null
        },
        cropDragStart = { kind, fx, fy -> onCropDragStart(kind, fx, fy) },
        cropDrag = { kind, fx, fy -> onCropDrag(kind, fx, fy) },
        cropDragEnd = { onCropDragEnd() },
        cropDragCancel = { onCropDragCancel() },
        cropPassthrough = tool == EditorTool.Crop && cropTab == CropTab.TRANSFORM
    )

    // --- Crop handlers ---------------------------------------------------
    fun onAspectSelected(sel: AspectSel) {
        aspectSel = sel
        val rotated = cur.rotation == 90 || cur.rotation == 270
        val frameW = if (rotated) sourceBitmap.height.toFloat() else sourceBitmap.width.toFloat()
        val frameH = if (rotated) sourceBitmap.width.toFloat() else sourceBitmap.height.toFloat()
        val newCrop = when (sel) {
            AspectSel.Original -> CropRect.FULL
            AspectSel.Free -> cur.crop
            is AspectSel.Fixed -> CropGeometry.rectWithAspect(
                cur.crop, sel.ratio, frameW / frameH
            )
        }
        commitDirect(cur.copy(crop = CropGeometry.clampRect(newCrop)))
    }

    // --- Layout ------------------------------------------------------------
    // The top bar lives inside MainActivity's safeDrawing inset gate, so
    // nothing here can render under the status bar or camera cutout.
    Column(modifier = Modifier.fillMaxSize().background(AppBg)) {
        // Top bar, Express order: back · help · auto-enhance · compare ·
        // download · share · menu. Undo/redo live on the canvas now.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            IconButton(onClick = { showAbout = true }) {
                Icon(Icons.Filled.HelpOutline, contentDescription = "Help", tint = Color.White)
            }
            IconButton(onClick = { autoEnhance() }) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = "Auto-enhance", tint = Color.White)
            }
            // Compare: tap toggles the split before/after view; holding
            // the button shows the untouched original until release (the
            // v0.1.x hold-to-compare, kept alongside the split view).
            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                val released = withTimeoutOrNull(300) { tryAwaitRelease() }
                                if (released == true) {
                                    splitCompare = !splitCompareRef.value
                                } else {
                                    showOriginal = true
                                    tryAwaitRelease()
                                    showOriginal = false
                                }
                            }
                        )
                    }
                    .padding(10.dp)
            ) {
                Icon(
                    SplitCompareIcon, contentDescription = "Compare before and after",
                    tint = if (splitCompare || showOriginal) Accent else Color.White
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { quickSave() }) {
                Icon(Icons.Filled.Download, contentDescription = "Save to gallery", tint = Color.White)
            }
            IconButton(onClick = { showExport = true }) {
                Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = Color.White)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("About") },
                        onClick = { menuOpen = false; showAbout = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Check for updates") },
                        onClick = { menuOpen = false; onCheckForUpdates() }
                    )
                    DropdownMenuItem(
                        text = { Text("Reset edits") },
                        onClick = { menuOpen = false; resetEdits() }
                    )
                }
            }
        }

        // Canvas: fitted to the available area, aspect = displayed image.
        val shownBitmap = if (showOriginal) sourceBitmap else preview
        val imgW = shownBitmap?.width ?: sourceBitmap.width
        val imgH = shownBitmap?.height ?: sourceBitmap.height
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val density = LocalDensity.current
            val maxWpx = constraints.maxWidth
            val maxHpx = constraints.maxHeight
            val dispWpx = minOf(maxWpx, (maxHpx.toFloat() * imgW / imgH).toInt())
            val dispHpx = (dispWpx.toFloat() * imgH / imgW).toInt()
            val dispWdp = with(density) { dispWpx.toDp() }
            val dispHdp = with(density) { dispHpx.toDp() }
            Box(
                modifier = Modifier
                    .size(dispWdp, dispHdp)
                    .onSizeChanged { canvasSize = it }
                    .then(gestureModifier)
            ) {
                // Zoom is a view transform only: the image, split view,
                // blur ellipse and crop overlays all live inside this
                // layer, so they stay aligned while zoomed; tool input
                // coordinates are inverse-mapped in the gesture gate.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = zoom.scale,
                            scaleY = zoom.scale,
                            translationX = zoom.offsetX * canvasSize.width,
                            translationY = zoom.offsetY * canvasSize.height,
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        )
                ) {
                if (showOriginal) {
                    Image(
                        bitmap = sourceBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (splitCompare && tool != EditorTool.Crop &&
                    beforeBitmap != null && preview != null
                ) {
                    SplitCompareView(
                        before = beforeBitmap!!,
                        after = preview!!,
                        divider = divider,
                        onDivider = { divider = it }
                    )
                } else {
                    preview?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                // Radial blur ellipse outline.
                if (!showOriginal && tool == EditorTool.Adjustments &&
                    adjustTab == AdjustTab.BLUR &&
                    cur.blur.mode == com.shawnrodis.photocraftexpress.engine.BlurMode.RADIAL
                ) {
                    val blur = cur.blur
                    Canvas(Modifier.fillMaxSize()) {
                        drawOval(
                            color = Accent,
                            topLeft = Offset(
                                (blur.cx - blur.rx) * size.width,
                                (blur.cy - blur.ry) * size.height
                            ),
                            size = Size(blur.rx * 2f * size.width, blur.ry * 2f * size.height),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
                // Crop TRANSFORM handles stay in the zoom layer (their
                // coords are canvas fractions, so they scale with it).
                if (!showOriginal && tool == EditorTool.Crop && canvasSize.width > 0 &&
                    cropTab == CropTab.TRANSFORM
                ) {
                    TransformHandles(
                        quad = cur.transform ?: WarpMath.Quad.IDENTITY,
                        canvasSize = canvasSize,
                        zoom = zoom,
                        onDraft = { q -> draftTo(cur.copy(transform = q)) },
                        onCommit = { commitDraft() }
                    )
                }
                } // end zoom layer
                // Crop rect overlay: draw-only, OUTSIDE the zoom layer,
                // mapping the rect to screen px explicitly through the
                // zoom (see CropOverlay) so handles stay constant screen
                // size and always align with the zoomed image. The
                // gesture gate owns its input; touches pass through
                // this layer (it has no pointer input of its own).
                if (!showOriginal && tool == EditorTool.Crop && canvasSize.width > 0 &&
                    cropTab != CropTab.TRANSFORM
                ) {
                    preview?.let { pv ->
                        CropOverlay(
                            containerSize = canvasSize,
                            imageWidth = pv.width,
                            imageHeight = pv.height,
                            crop = cur.crop,
                            zoom = zoom,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                if (zoom.isZoomed) {
                    // Reset affordance, unzoomed chrome above the canvas.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xAA000000))
                            .clickable { zoom = ZoomState.IDENTITY }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Reset zoom", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
            // Floating undo/redo, Express-style: bottom-left of the canvas,
            // above the tool strip. Undo is always here; redo appears beside
            // it only while a redoable state exists.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomStart
            ) {
                Row(
                    Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xAA000000), CircleShape)
                            .clickable(enabled = history.canUndo) { undo() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo",
                            tint = if (history.canUndo) Color.White else Color(0xFF888888)
                        )
                    }
                    if (history.canRedo) {
                        Spacer(Modifier.size(8.dp))
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xAA000000), CircleShape)
                                .clickable { redo() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Tool panel.
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp)
        ) {
            when (tool) {
                EditorTool.Looks -> LooksPanel(
                    thumbnails = lookThumbs,
                    onVisibleLooks = { requestLookThumbs(it) },
                    selectedLookId = cur.lookId,
                    intensity = cur.lookIntensity,
                    onSelectLook = { commitDirect(cur.copy(lookId = it)) },
                    onIntensityDraft = { draftTo(cur.copy(lookIntensity = it)) },
                    onIntensityCommit = { commitDraft() }
                )
                EditorTool.Adjustments -> AdjustmentsPanel(
                    adj = cur.adj,
                    splitTone = cur.splitTone,
                    colorMix = cur.colorMix,
                    blur = cur.blur,
                    vignette = cur.vignette,
                    adjustTab = adjustTab,
                    onAdjustTab = { adjustTab = it },
                    onDraftAdj = { draftTo(cur.copy(adj = it)) },
                    onDraftSplitTone = { draftTo(cur.copy(splitTone = it)) },
                    onDraftColorMix = { draftTo(cur.copy(colorMix = it)) },
                    onDraftBlur = { draftTo(cur.copy(blur = it)) },
                    onDraftVignette = { draftTo(cur.copy(vignette = it)) },
                    onCommit = { commitDraft() },
                    mask = cur.adjustMask,
                    onMask = { commitDirect(cur.copy(adjustMask = it)) },
                    personFound = personFound,
                    adjustThumbs = adjustThumbs,
                    maskThumbs = maskThumbs
                )
                EditorTool.Heal -> Box(Modifier.verticalScroll(rememberScrollState())) {
                    HealPanel(brushSize = healBrush, onBrushSize = { healBrush = it })
                }
                EditorTool.Crop -> Box(Modifier.verticalScroll(rememberScrollState())) {
                    CropPanel(
                        aspectSel = aspectSel,
                        cropTab = cropTab,
                        onCropTab = { cropTab = it },
                        onAspectSelected = { onAspectSelected(it) },
                        onRotate = {
                            val newRot = (cur.rotation + 90) % 360
                            commitDirect(
                                cur.copy(
                                    rotation = newRot,
                                    crop = CropGeometry.rotateRectCw(cur.crop)
                                )
                            )
                        },
                        onFlipH = {
                            commitDirect(
                                cur.copy(
                                    flipH = !cur.flipH,
                                    crop = CropGeometry.flipHRect(cur.crop)
                                )
                            )
                        },
                        onFlipV = {
                            commitDirect(
                                cur.copy(
                                    flipV = !cur.flipV,
                                    crop = CropGeometry.flipVRect(cur.crop)
                                )
                            )
                        },
                        onResetCrop = {
                            aspectSel = AspectSel.Original
                            commitDirect(cur.copy(crop = CropRect.FULL))
                        },
                        onResetTransform = { commitDirect(cur.copy(transform = null)) }
                    )
                }
                EditorTool.Smooth -> Box(Modifier.verticalScroll(rememberScrollState())) {
                    SmoothPanel(
                        strength = cur.smooth,
                        onDraft = { draftTo(cur.copy(smooth = it)) },
                        onCommit = { commitDraft() }
                    )
                }
                EditorTool.Face -> Box(Modifier.verticalScroll(rememberScrollState())) {
                    FacePanel(
                        hasFace = hasFace,
                        face = cur.face,
                        onDraft = { draftTo(cur.copy(face = it)) },
                        onCommit = { commitDraft() }
                    )
                }
                EditorTool.Reshape -> Box(Modifier.verticalScroll(rememberScrollState())) {
                    ReshapePanel(
                        mode = reshapeMode,
                        brushSize = reshapeBrush,
                        onMode = { reshapeMode = it },
                        onBrushSize = { reshapeBrush = it }
                    )
                }
                EditorTool.Makeup -> Box(Modifier.verticalScroll(rememberScrollState())) {
                    MakeupPanel(
                        hasFace = hasFace,
                        lipColor = cur.makeupLipColor,
                        lip = cur.makeupLip,
                        blush = cur.makeupBlush,
                        shadow = cur.makeupShadow,
                        liner = cur.makeupLiner,
                        onLipColor = { commitDirect(cur.copy(makeupLipColor = it)) },
                        onDraft = { l, b, s, ln ->
                            draftTo(
                                cur.copy(
                                    makeupLip = l, makeupBlush = b,
                                    makeupShadow = s, makeupLiner = ln
                                )
                            )
                        },
                        onCommit = { commitDraft() }
                    )
                }
                EditorTool.Draw -> Box(Modifier.verticalScroll(rememberScrollState())) {
                    DrawPanel(
                        color = drawColor,
                        width = drawWidth,
                        opacity = drawOpacity,
                        onColor = { drawColor = it },
                        onWidth = { drawWidth = it },
                        onOpacity = { drawOpacity = it }
                    )
                }
                EditorTool.Background -> Box(Modifier.verticalScroll(rememberScrollState())) {
                    BackgroundPanel(
                        personFound = personFound,
                        fx = cur.background,
                        onDraft = { draftTo(cur.copy(background = it)) },
                        onCommit = { commitDraft() }
                    )
                }
                EditorTool.InsertObjects -> Box(Modifier.verticalScroll(rememberScrollState())) {
                    val sel = cur.insertedImages.firstOrNull { it.id == selInsertedId }
                    InsertPanel(
                        mode = insertMode,
                        brushSize = insertBrush,
                        hasLayer = sel != null,
                        useMask = sel?.useMask == true,
                        layerScale = sel?.scale ?: 0.5f,
                        layerRotation = sel?.rotation ?: 0f,
                        onMode = { insertMode = it },
                        onBrushSize = { insertBrush = it },
                        onAddPhoto = {
                            insertPicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        onClearMask = {
                            pendingMask = emptyList()
                            val s = sel
                            if (s != null) {
                                commitDirect(
                                    cur.copy(
                                        insertedImages = cur.insertedImages.map {
                                            if (it.id == s.id) it.copy(maskStrokes = emptyList()) else it
                                        }
                                    )
                                )
                            }
                        },
                        onUseMask = { use ->
                            val s = sel ?: return@InsertPanel
                            commitDirect(
                                cur.copy(
                                    insertedImages = cur.insertedImages.map {
                                        if (it.id == s.id) it.copy(useMask = use) else it
                                    }
                                )
                            )
                        },
                        onLayerScale = { v ->
                            val s = sel ?: return@InsertPanel
                            draftTo(
                                cur.copy(
                                    insertedImages = cur.insertedImages.map {
                                        if (it.id == s.id) it.copy(scale = v) else it
                                    }
                                )
                            )
                        },
                        onLayerScaleCommit = { commitDraft() },
                        onLayerRotation = { v ->
                            val s = sel ?: return@InsertPanel
                            draftTo(
                                cur.copy(
                                    insertedImages = cur.insertedImages.map {
                                        if (it.id == s.id) it.copy(rotation = v) else it
                                    }
                                )
                            )
                        },
                        onLayerRotationCommit = { commitDraft() },
                        onDeleteLayer = {
                            val s = sel ?: return@InsertPanel
                            commitDirect(
                                cur.copy(
                                    insertedImages = cur.insertedImages.filter { it.id != s.id }
                                )
                            )
                            selInsertedId = null
                            insertedBitmaps = insertedBitmaps - s.id.toString()
                        }
                    )
                }
                EditorTool.Overlays -> Box(Modifier.verticalScroll(rememberScrollState())) {
                    OverlaysPanel(
                        overlay = cur.overlay,
                        onDraft = { draftTo(cur.copy(overlay = it)) },
                        onCommit = { commitDraft() }
                    )
                }
                EditorTool.Text -> Box(Modifier.verticalScroll(rememberScrollState())) {
                    val sel = cur.textItems.firstOrNull { it.id == selTextId }
                    TextPanel(
                        tab = textTab,
                        onTab = { textTab = it },
                        selectedText = sel,
                        onAddText = { text, preset ->
                            val base = TextItem(
                                id = nextLayerId++, text = text, x = 0.5f, y = 0.5f
                            )
                            val styled = when (preset) {
                                TextStylePreset.BASIC -> base
                                TextStylePreset.QUOTE -> base.copy(
                                    font = TextFont.SERIF, italic = true, size = 0.10f
                                )
                                TextStylePreset.MEME -> base.copy(
                                    bold = true, strokeWidth = 0.08f
                                )
                            }
                            commitDirect(cur.copy(textItems = cur.textItems + styled))
                            selTextId = styled.id
                        },
                        onApplyStyle = { preset ->
                            val item = sel ?: return@TextPanel
                            val updated = when (preset) {
                                TextStylePreset.BASIC -> item.copy(
                                    font = TextFont.SANS, bold = false,
                                    italic = false, strokeWidth = 0f
                                )
                                TextStylePreset.QUOTE -> item.copy(
                                    font = TextFont.SERIF, italic = true,
                                    strokeWidth = 0f,
                                    size = maxOf(item.size, 0.10f)
                                )
                                TextStylePreset.MEME -> item.copy(
                                    font = TextFont.SANS, bold = true,
                                    italic = false, strokeWidth = 0.08f,
                                    strokeColor = -0x1000000
                                )
                            }
                            commitDirect(
                                cur.copy(
                                    textItems = cur.textItems.map {
                                        if (it.id == item.id) updated else it
                                    }
                                )
                            )
                        },
                        onDraftSelected = { updated ->
                            draftTo(
                                cur.copy(
                                    textItems = cur.textItems.map {
                                        if (it.id == updated.id) updated else it
                                    }
                                )
                            )
                        },
                        onCommit = { commitDraft() },
                        onDeleteSelected = {
                            val item = sel ?: return@TextPanel
                            commitDirect(
                                cur.copy(textItems = cur.textItems.filter { it.id != item.id })
                            )
                            selTextId = null
                        }
                    )
                }
                EditorTool.Stickers -> Box(Modifier.verticalScroll(rememberScrollState())) {
                    val sel = cur.stickerItems.firstOrNull { it.id == selStickerId }
                    StickersPanel(
                        selectedKind = stickerKind,
                        onKind = { stickerKind = it },
                        hasSelection = sel != null,
                        selScale = sel?.scale ?: 0.15f,
                        selRotation = sel?.rotation ?: 0f,
                        onSelScale = { v ->
                            val s = sel ?: return@StickersPanel
                            draftTo(
                                cur.copy(
                                    stickerItems = cur.stickerItems.map {
                                        if (it.id == s.id) it.copy(scale = v) else it
                                    }
                                )
                            )
                        },
                        onSelScaleCommit = { commitDraft() },
                        onSelRotation = { v ->
                            val s = sel ?: return@StickersPanel
                            draftTo(
                                cur.copy(
                                    stickerItems = cur.stickerItems.map {
                                        if (it.id == s.id) it.copy(rotation = v) else it
                                    }
                                )
                            )
                        },
                        onSelRotationCommit = { commitDraft() },
                        onDeleteSelected = {
                            val s = sel ?: return@StickersPanel
                            commitDirect(
                                cur.copy(stickerItems = cur.stickerItems.filter { it.id != s.id })
                            )
                            selStickerId = null
                        }
                    )
                }
                EditorTool.Borders -> Box(Modifier.verticalScroll(rememberScrollState())) {
                    BordersPanel(
                        border = cur.border,
                        tab = borderTab,
                        onTab = { borderTab = it },
                        onDraft = { draftTo(cur.copy(border = it)) },
                        onCommit = { commitDraft() }
                    )
                }
                EditorTool.Eyes -> Box(Modifier.verticalScroll(rememberScrollState())) {
                    EyesPanel(
                        mode = eyesMode,
                        hasFace = hasFace,
                        enhance = cur.eyesEnhance,
                        brushSize = eyeBrush,
                        onMode = { eyesMode = it },
                        onEnhance = { draftTo(cur.copy(eyesEnhance = it)) },
                        onBrushSize = { eyeBrush = it },
                        onCommit = { commitDraft() }
                    )
                }
            }
        }

        // Tool carousel, Express order.
        LazyRow(
            modifier = Modifier.fillMaxWidth().background(PanelBg),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
        ) {
            items(EditorTool.values(), key = { it.name }) { t ->
                val selected = tool == t
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Color(0xFF2E4A66) else Color.Transparent)
                        .clickable { tool = t }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        t.icon, contentDescription = null,
                        tint = if (selected) Accent else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        t.label, fontSize = 11.sp,
                        color = if (selected) Accent else Color.White
                    )
                }
            }
        }
    }

    // Express-style export/share sheet.
    if (showExport) {
        ExportSheet(
            preview = preview,
            exportSize = exportSize,
            exportFormat = exportFormat,
            exportQuality = exportQuality,
            exportWatermark = exportWatermark,
            customW = customW,
            customH = customH,
            exporting = exporting,
            onSizeMode = { mode ->
                exportSize = exportSize.copy(mode = mode)
            },
            onFormat = { exportFormat = it },
            onQuality = { exportQuality = it },
            onWatermark = { exportWatermark = it },
            onCustomW = { customW = it },
            onCustomH = { customH = it },
            onLockAspect = { exportSize = exportSize.copy(lockAspect = it) },
            onDismiss = { if (!exporting) showExport = false },
            onExport = { action ->
                val w = customW.toIntOrNull() ?: 0
                val h = customH.toIntOrNull() ?: 0
                val size = exportSize.copy(customW = w, customH = h)
                exportSize = size
                exporting = true
                scope.launch {
                    if (action == ExportAction.PRINT) {
                        // Print shows system UI: render off-main, print on-main.
                        val (bmp, error) = Exporter.renderExport(
                            context, sourceUri, curRef.value, size,
                            exportFormat, exportQuality, exportWatermark
                        )
                        exporting = false
                        showExport = false
                        if (bmp != null) {
                            val msg = Exporter.printImage(
                                context, bmp, Exporter.exportFileName(exportFormat)
                            )
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(
                                context, error ?: "Export failed.", Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        val msg = withContext(Dispatchers.Default) {
                            Exporter.export(
                                context, sourceUri, curRef.value, size,
                                exportFormat, exportQuality, exportWatermark, action
                            )
                        }
                        exporting = false
                        showExport = false
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // About/credits dialog (help button and ⋮ menu).
    if (showAbout) {
        AboutDialog(
            onDismiss = { showAbout = false },
            onCheckForUpdates = { showAbout = false; onCheckForUpdates() }
        )
    }
}

/**
 * One pointer-input gate for the canvas: taps for Heal / Eyes / Stickers /
 * Text / Insert selection, drags for Draw / Reshape / Insert brush /
 * layer moves / radial-blur ellipse, and (v0.2.3) crop-rect drags.
 * Crop used to have its own detector on the overlay, separate from this
 * gate — with zoom disabled during Crop, and edge drags that either
 * moved the whole rect or did nothing (there were no edge handles).
 * Now the gate owns crop input too: single-finger corner/edge/move
 * drags, two-finger pinch/pan zoom, double-tap zoom toggle, all through
 * the same zoom mapping. A drag only starts after the touch slop, so
 * taps and drags never double-fire. [cropPassthrough] (Crop Transform
 * tab) observes single-finger touches without consuming them, so the
 * Transform handles' own detectors keep working, while pinch and
 * double-tap zoom still run.
 */
@Composable
private fun canvasGestureModifier(
    active: Boolean,
    zoom: ZoomState,
    onZoomChange: (ZoomState) -> Unit,
    onDoubleTap: (Float, Float) -> Unit,
    onDragCancel: () -> Unit,
    onTap: (Float, Float) -> Unit,
    onDragStart: (Float, Float) -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    cropHitTest: ((Float, Float) -> CropDragKind)? = null,
    cropDragStart: ((CropDragKind, Float, Float) -> Unit)? = null,
    cropDrag: ((CropDragKind, Float, Float) -> Unit)? = null,
    cropDragEnd: (() -> Unit)? = null,
    cropDragCancel: (() -> Unit)? = null,
    cropPassthrough: Boolean = false
): Modifier {
    if (!active) return Modifier
    val zoomRef = rememberUpdatedState(zoom)
    val zoomChangeRef = rememberUpdatedState(onZoomChange)
    val doubleTapRef = rememberUpdatedState(onDoubleTap)
    val dragCancelRef = rememberUpdatedState(onDragCancel)
    val tapRef = rememberUpdatedState(onTap)
    val dragStartRef = rememberUpdatedState(onDragStart)
    val dragRef = rememberUpdatedState(onDrag)
    val dragEndRef = rememberUpdatedState(onDragEnd)
    val cropHitTestRef = rememberUpdatedState(cropHitTest)
    val cropDragStartRef = rememberUpdatedState(cropDragStart)
    val cropDragRef = rememberUpdatedState(cropDrag)
    val cropDragEndRef = rememberUpdatedState(cropDragEnd)
    val cropDragCancelRef = rememberUpdatedState(cropDragCancel)
    return Modifier.pointerInput(Unit) {
        // Double-tap detection lives across gestures (same
        // pointer-input scope), so it survives each awaitEachGesture.
        var lastTapTime = 0L
        var lastTapPos = Offset.Zero
        awaitEachGesture {
            val down = awaitFirstDown()
            var last = down.position
            var dragging = false
            var cropKind: CropDragKind? = null
            var cropDecided = false
            var transforming = false
            var zoomAtStart = zoomRef.value
            var centroidAtStart = Offset.Zero
            var spanAtStart = 0f
            var done = false
            // Tool coordinates are in the unzoomed canvas frame: map
            // every pointer position back through the current zoom.
            fun norm(pos: Offset): Pair<Float, Float> {
                val z = zoomRef.value
                val p = z.inverse(pos.x / size.width, pos.y / size.height)
                return p.first to p.second
            }
            while (!done) {
                val ev = awaitPointerEvent()
                val pressed = ev.changes.filter { it.pressed }
                if (pressed.size >= 2 && !transforming) {
                    // Second finger down: cancel any in-progress tool
                    // or crop drag (its partial state is restored,
                    // uncommitted) and switch to pinch/pan for the rest
                    // of the gesture.
                    if (dragging) {
                        dragCancelRef.value()
                        dragging = false
                    }
                    if (cropKind != null) {
                        cropDragCancelRef.value?.invoke()
                        cropKind = null
                    }
                    transforming = true
                    zoomAtStart = zoomRef.value
                    centroidAtStart = pressed.fold(Offset.Zero) { acc, c -> acc + c.position } /
                        pressed.size.toFloat()
                    var max = 0f
                    for (a in pressed) for (b in pressed) {
                        max = maxOf(max, (a.position - b.position).getDistance())
                    }
                    spanAtStart = max
                    ev.changes.forEach { it.consume() }
                    continue
                }
                if (transforming) {
                    if (pressed.size < 2) {
                        // Last finger lifted: end the transform; no
                        // tool event fires for the leftover pointer.
                        done = true
                        ev.changes.forEach { it.consume() }
                        continue
                    }
                    val centroid = pressed.fold(Offset.Zero) { acc, c -> acc + c.position } /
                        pressed.size.toFloat()
                    var span = 0f
                    for (a in pressed) for (b in pressed) {
                        span = maxOf(span, (a.position - b.position).getDistance())
                    }
                    var next = zoomAtStart
                    if (spanAtStart > 0f && span > 0f) {
                        next = next.zoomAt(
                            centroidAtStart.x / size.width,
                            centroidAtStart.y / size.height,
                            span / spanAtStart
                        )
                    }
                    val dx = (centroid.x - centroidAtStart.x) / size.width
                    val dy = (centroid.y - centroidAtStart.y) / size.height
                    next = next.pan(dx, dy)
                    zoomChangeRef.value(next)
                    ev.changes.forEach { it.consume() }
                    continue
                }
                val ch = ev.changes.firstOrNull { it.id == down.id }
                if (ch == null) {
                    done = true
                    continue
                }
                // (Compose UI 1.7 dropped changedToUpOrCancelled(); a
                // released-or-cancelled pointer simply reads !pressed.)
                if (!ch.pressed) {
                    if (dragging) {
                        dragEndRef.value()
                    } else if (cropKind != null) {
                        cropDragEndRef.value?.invoke()
                        cropKind = null
                    } else {
                        // Passthrough included: taps are no-ops for the
                        // Crop tool, but double-tap zoom still applies.
                        val now = System.currentTimeMillis()
                        val (nx, ny) = norm(last)
                        if (now - lastTapTime < 300L &&
                            (last - lastTapPos).getDistance() < 100f
                        ) {
                            doubleTapRef.value(nx, ny)
                            lastTapTime = 0L
                        } else {
                            tapRef.value(nx, ny)
                            lastTapTime = now
                            lastTapPos = last
                        }
                    }
                    ch.consume()
                    done = true
                } else if (cropPassthrough) {
                    // Transform tab: handles own single-finger drags.
                    // Observe only (for double-tap tracking) — never
                    // consume, so the handles' detector keeps the drag.
                    last = ch.position
                } else if (cropHitTestRef.value != null) {
                    // Crop rect mode: the gate owns the drag. Hit-test
                    // at the DOWN position (where the finger landed),
                    // then drive the drag from unzoomed fractions.
                    val dist = (ch.position - down.position).getDistance()
                    if (!cropDecided && dist > 8f) {
                        cropDecided = true
                        val kind = cropHitTestRef.value!!(down.position.x, down.position.y)
                        if (kind != CropDragKind.None) {
                            cropKind = kind
                            val (sx, sy) = norm(down.position)
                            cropDragStartRef.value?.invoke(kind, sx, sy)
                        }
                    }
                    if (cropKind != null) {
                        val (nx, ny) = norm(ch.position)
                        cropDragRef.value?.invoke(cropKind!!, nx, ny)
                        last = ch.position
                    }
                    ch.consume()
                } else {
                    val dist = (ch.position - down.position).getDistance()
                    if (!dragging && dist > 8f) {
                        dragging = true
                        val (sx, sy) = norm(down.position)
                        dragStartRef.value(sx, sy)
                    }
                    if (dragging) {
                        val (nx, ny) = norm(ch.position)
                        dragRef.value(nx, ny)
                        last = ch.position
                    }
                    ch.consume()
                }
            }
        }
    }
}

/**
 * The four corner handles for Crop TRANSFORM (perspective/keystone).
 * Corners live in the cropped frame, which is exactly the canvas frame
 * while the Transform tab is active (the preview is cropped then).
 */
@Composable
private fun TransformHandles(
    quad: WarpMath.Quad,
    canvasSize: IntSize,
    zoom: ZoomState,
    onDraft: (WarpMath.Quad) -> Unit,
    onCommit: () -> Unit
) {
    // v0.2.4: the zoom at drag time, read live so a pinch mid-drag
    // still maps deltas correctly.
    val zoomRef = rememberUpdatedState(zoom)
    var corners by remember(quad, canvasSize) {
        mutableStateOf(
            List(4) { i ->
                val c = quad.corner(i)
                Offset(c.first * canvasSize.width, c.second * canvasSize.height)
            }
        )
    }
    Canvas(Modifier.fillMaxSize()) {
        val pts = corners
        for (i in 0 until 4) {
            drawLine(
                color = Accent,
                start = pts[i],
                end = pts[(i + 1) % 4],
                strokeWidth = 2.dp.toPx()
            )
        }
    }
    for (i in 0 until 4) {
        val pos = corners[i]
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (pos.x - 16.dp.toPx()).roundToInt(),
                        (pos.y - 16.dp.toPx()).roundToInt()
                    )
                }
                .size(32.dp)
                .background(Accent, CircleShape)
                .border(2.dp, Color.White, CircleShape)
                .pointerInput(i, canvasSize) {
                    detectDragGestures(
                        onDragEnd = { onCommit() }
                    ) { change, dragAmount ->
                        change.consume()
                        // v0.2.4: the graphics layer scales rendering
                        // but NOT pointer coordinates, so divide the
                        // displayed-px delta by the zoom scale to get
                        // canvas px — raw deltas moved corners 2x too
                        // far at 2x zoom and drifted off the fingertip.
                        val (ddx, ddy) = zoomRef.value.canvasDragDelta(dragAmount.x, dragAmount.y)
                        val updated = corners.toMutableList()
                        val cur = updated[i]
                        updated[i] = Offset(
                            (cur.x + ddx).coerceIn(0f, canvasSize.width.toFloat()),
                            (cur.y + ddy).coerceIn(0f, canvasSize.height.toFloat())
                        )
                        corners = updated
                        onDraft(
                            WarpMath.Quad(
                                updated[0].x / canvasSize.width,
                                updated[0].y / canvasSize.height,
                                updated[1].x / canvasSize.width,
                                updated[1].y / canvasSize.height,
                                updated[2].x / canvasSize.width,
                                updated[2].y / canvasSize.height,
                                updated[3].x / canvasSize.width,
                                updated[3].y / canvasSize.height
                            )
                        )
                    }
                }
        )
    }
}

/**
 * Split-rectangle compare icon for the top bar: left half an outline
 * (before), right half filled (after). Drawn fresh — not Adobe's icon.
 */
private val SplitCompareIcon: ImageVector by lazy {
    ImageVector.Builder("SplitCompare", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = listOf(
                PathNode.MoveTo(4f, 5f),
                PathNode.LineTo(11f, 5f),
                PathNode.LineTo(11f, 19f),
                PathNode.LineTo(4f, 19f),
                PathNode.Close
            ),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f
        )
        .addPath(
            pathData = listOf(
                PathNode.MoveTo(13f, 5f),
                PathNode.LineTo(20f, 5f),
                PathNode.LineTo(20f, 19f),
                PathNode.LineTo(13f, 19f),
                PathNode.Close
            ),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black)
        )
        .build()
}

/**
 * Split before/after view: the untouched-geometry "before" on the left,
 * the edited photo on the right, divided by a draggable divider line.
 * Dragging anywhere on the view moves the divider.
 */
@Composable
private fun SplitCompareView(
    before: Bitmap,
    after: Bitmap,
    divider: Float,
    onDivider: (Float) -> Unit
) {
    val beforeImg = remember(before) { before.asImageBitmap() }
    val afterImg = remember(after) { after.asImageBitmap() }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onDivider((change.position.x / size.width).coerceIn(0.05f, 0.95f))
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val dx = divider * w
            val splitPx = (divider * beforeImg.width).toInt().coerceIn(0, beforeImg.width)
            drawImage(
                image = beforeImg,
                srcOffset = androidx.compose.ui.unit.IntOffset(0, 0),
                srcSize = androidx.compose.ui.unit.IntSize(splitPx, beforeImg.height),
                dstOffset = androidx.compose.ui.unit.IntOffset(0, 0),
                dstSize = androidx.compose.ui.unit.IntSize(dx.toInt(), h.toInt())
            )
            val afterX = (divider * afterImg.width).toInt().coerceIn(0, afterImg.width)
            drawImage(
                image = afterImg,
                srcOffset = androidx.compose.ui.unit.IntOffset(afterX, 0),
                srcSize = androidx.compose.ui.unit.IntSize(afterImg.width - afterX, afterImg.height),
                dstOffset = androidx.compose.ui.unit.IntOffset(dx.toInt(), 0),
                dstSize = androidx.compose.ui.unit.IntSize((w - dx).toInt(), h.toInt())
            )
            drawLine(
                color = Accent,
                start = Offset(dx, 0f),
                end = Offset(dx, h),
                strokeWidth = 2.dp.toPx()
            )
            // Divider handle dot, centred vertically.
            drawCircle(
                color = Accent,
                radius = 14.dp.toPx(),
                center = Offset(dx, h / 2f)
            )
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = Offset(dx, h / 2f)
            )
        }
    }
}

/** About/credits dialog, shared by the help button and the ⋮ menu. */
@Composable
private fun AboutDialog(
    onDismiss: () -> Unit,
    onCheckForUpdates: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PhotoCraft Express 0.2.4") },
        text = {
            Text(
                "A photo editor with an original codebase and hand-drawn icons. " +
                    "Its adjustment engine's mathematics follows PhotoCraft " +
                    "(github.com/storytold/photocraft), the open-source clean-room " +
                    "photo editor by storytold / ArtCraft, used under the MIT OR " +
                    "Apache-2.0 licenses — see the NOTICE file in the source repo.\n\n" +
                    "The interface recreates Photoshop Express's layout and flow " +
                    "with original artwork. PhotoCraft Express is not affiliated " +
                    "with or endorsed by storytold/ArtCraft or Adobe.\n\n" +
                    "How to use: pick a tool along the bottom — Looks, Adjustments, " +
                    "Heal, Crop, Smooth, Face, Reshape, Makeup, Draw, Background, " +
                    "Insert Objects, Overlays, Text, Stickers, Borders, Eyes — then " +
                    "use its panel. Brush tools (Heal, Draw, Reshape, the Insert " +
                    "brush, Eyes) work by tapping or dragging on the photo. Undo " +
                    "lives at the canvas's bottom-left; redo appears beside it " +
                    "when available. The wand auto-enhances in one tap (undoable). " +
                    "The download button saves straight to your gallery; the share " +
                    "button opens the full export screen with format, size, quality " +
                    "and watermark options."
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = onCheckForUpdates) { Text("Check for updates") }
        }
    )
}

/**
 * Express-style export/share screen: rendered preview on top, format /
 * dimensions / quality chips, our own text watermark options, and the
 * share actions — Save to Gallery, system Share sheet, Set as Wallpaper,
 * Print. No Facebook targets, no app-specific targets of any kind.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(
    preview: Bitmap?,
    exportSize: ExportSize,
    exportFormat: ExportFormat,
    exportQuality: Int,
    exportWatermark: WatermarkStyle,
    customW: String,
    customH: String,
    exporting: Boolean,
    onSizeMode: (ExportSizeMode) -> Unit,
    onFormat: (ExportFormat) -> Unit,
    onQuality: (Int) -> Unit,
    onWatermark: (WatermarkStyle) -> Unit,
    onCustomW: (String) -> Unit,
    onCustomH: (String) -> Unit,
    onLockAspect: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onExport: (ExportAction) -> Unit
) {
    // Dimensions label for the chosen size setting. Long-edge and custom
    // modes depend only on the photo's aspect, so the preview's aspect
    // gives the exact export dimensions; Original and 50% need the
    // full-resolution size, so they show the setting instead.
    val dimsLabel = when (exportSize.mode) {
        ExportSizeMode.ORIGINAL -> "Original size"
        ExportSizeMode.LONG_1080, ExportSizeMode.LONG_2048 -> {
            val pv = preview
            if (pv == null) {
                "…"
            } else {
                val dims = exportSize.outputSize(pv.width, pv.height)
                if (dims == null) "Original size" else "${dims.first} × ${dims.second} px"
            }
        }
        ExportSizeMode.PERCENT_50 -> "50% of original"
        ExportSizeMode.CUSTOM -> {
            val w = customW.toIntOrNull()
            val h = customH.toIntOrNull()
            if (w == null && h == null) "Custom size"
            else "${w ?: "?"} × ${h ?: "?"} px"
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Export", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.size(10.dp))
            preview?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF000000))
                )
                Spacer(Modifier.size(10.dp))
            }
            Text("Format", fontSize = 14.sp, color = Color(0xFFE0E0E0))
            ModeChips(
                options = listOf("JPEG", "PNG"),
                selected = if (exportFormat == ExportFormat.JPEG) "JPEG" else "PNG",
                onSelect = { onFormat(if (it == "PNG") ExportFormat.PNG else ExportFormat.JPEG) }
            )
            Text("Size", fontSize = 14.sp, color = Color(0xFFE0E0E0))
            ModeChips(
                options = listOf("Original", "1080", "2048", "50%", "Custom"),
                selected = when (exportSize.mode) {
                    ExportSizeMode.ORIGINAL -> "Original"
                    ExportSizeMode.LONG_1080 -> "1080"
                    ExportSizeMode.LONG_2048 -> "2048"
                    ExportSizeMode.PERCENT_50 -> "50%"
                    ExportSizeMode.CUSTOM -> "Custom"
                },
                onSelect = {
                    onSizeMode(
                        when (it) {
                            "1080" -> ExportSizeMode.LONG_1080
                            "2048" -> ExportSizeMode.LONG_2048
                            "50%" -> ExportSizeMode.PERCENT_50
                            "Custom" -> ExportSizeMode.CUSTOM
                            else -> ExportSizeMode.ORIGINAL
                        }
                    )
                }
            )
            Text(
                dimsLabel, fontSize = 13.sp, color = Accent,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
            if (exportSize.mode == ExportSizeMode.CUSTOM) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customW,
                        onValueChange = { onCustomW(it.filter { c -> c.isDigit() }) },
                        label = { Text("Width px") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Text(" × ", fontSize = 18.sp, color = Color.White)
                    OutlinedTextField(
                        value = customH,
                        onValueChange = { onCustomH(it.filter { c -> c.isDigit() }) },
                        label = { Text("Height px") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Lock aspect ratio", fontSize = 14.sp, color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = exportSize.lockAspect, onCheckedChange = onLockAspect)
                }
            }
            Spacer(Modifier.size(6.dp))
            Text("Quality: $exportQuality%", fontSize = 14.sp, color = Color(0xFFE0E0E0))
            Slider(
                value = exportQuality.toFloat(),
                onValueChange = { onQuality(it.toInt()) },
                valueRange = 10f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = Color(0xFF4A4A4A)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            if (exportFormat == ExportFormat.PNG) {
                Text(
                    "PNG is lossless — quality doesn't change the file.",
                    fontSize = 12.sp, color = Color(0xFF9E9E9E),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            Spacer(Modifier.size(6.dp))
            Text("Watermark", fontSize = 14.sp, color = Color(0xFFE0E0E0))
            ModeChips(
                options = listOf("None", "Text", "Text + rules"),
                selected = when (exportWatermark) {
                    WatermarkStyle.NONE -> "None"
                    WatermarkStyle.TEXT -> "Text"
                    WatermarkStyle.TEXT_RULES -> "Text + rules"
                },
                onSelect = {
                    onWatermark(
                        when (it) {
                            "Text" -> WatermarkStyle.TEXT
                            "Text + rules" -> WatermarkStyle.TEXT_RULES
                            else -> WatermarkStyle.NONE
                        }
                    )
                }
            )
            HintText("Watermark draws our own name, \"PhotoCraft Express\", as plain text.")
            Spacer(Modifier.size(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onExport(ExportAction.SAVE) },
                    enabled = !exporting,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier.weight(1f)
                ) { Text("Save to Gallery") }
                Spacer(Modifier.size(12.dp))
                Button(
                    onClick = { onExport(ExportAction.SHARE) },
                    enabled = !exporting,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier.weight(1f)
                ) { Text("Share") }
            }
            Spacer(Modifier.size(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onExport(ExportAction.WALLPAPER) },
                    enabled = !exporting,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier.weight(1f)
                ) { Text("Set as Wallpaper") }
                Spacer(Modifier.size(12.dp))
                Button(
                    onClick = { onExport(ExportAction.PRINT) },
                    enabled = !exporting,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier.weight(1f)
                ) { Text("Print") }
                if (exporting) {
                    Spacer(Modifier.size(12.dp))
                    CircularProgressIndicator(color = Accent, modifier = Modifier.size(24.dp))
                }
            }
            TextButton(onClick = onDismiss, enabled = !exporting) {
                Text("Cancel", color = Color(0xFFBDBDBD))
            }
        }
    }
}
