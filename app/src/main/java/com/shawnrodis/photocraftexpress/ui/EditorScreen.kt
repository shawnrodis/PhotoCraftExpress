package com.shawnrodis.photocraftexpress.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawnrodis.photocraftexpress.engine.CropGeometry
import com.shawnrodis.photocraftexpress.engine.CropRect
import com.shawnrodis.photocraftexpress.engine.EditHistory
import com.shawnrodis.photocraftexpress.engine.EditState
import com.shawnrodis.photocraftexpress.engine.Looks
import com.shawnrodis.photocraftexpress.engine.PixelImage
import com.shawnrodis.photocraftexpress.engine.PixelPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The editor: dark chrome, canvas centred, undo/redo + hold-to-compare +
 * Export on top, the tool carousel along the bottom, and the active tool's
 * panel swapped in above the carousel — the Photoshop Express layout,
 * recreated with original artwork.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    source: PixelImage,
    onBack: () -> Unit,
    onExport: suspend (EditState, Boolean) -> String,
    modifier: Modifier = Modifier
) {
    val history = remember { EditHistory(EditState()) }
    var state by remember { mutableStateOf(history.current) }
    var historyTick by remember { mutableIntStateOf(0) }
    var activeTool by remember { mutableStateOf(EditorTool.Looks) }
    var aspectSel by remember { mutableStateOf<AspectSel>(AspectSel.Original) }
    var showOriginal by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun commit(next: EditState) {
        history.commit(next)
        state = history.current
        historyTick++
    }

    fun commitCurrent() {
        history.commit(state)
        state = history.current
        historyTick++
    }

    BackHandler { onBack() }

    val cropMode = activeTool == EditorTool.Crop

    var rendered by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(state, cropMode, source) {
        val snapshot = state
        val cm = cropMode
        rendered = withContext(Dispatchers.Default) {
            BitmapBridge.toBitmap(PixelPipeline.render(source, snapshot, applyCrop = !cm))
        }
    }
    val originalBitmap = remember(source) { BitmapBridge.toBitmap(source) }

    // Looks thumbnails: the current geometry at thumbnail size, once per look.
    var lookThumbs by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    LaunchedEffect(source, state.rotation, state.flipH, state.flipV, state.crop) {
        val geoState = EditState(
            rotation = state.rotation,
            flipH = state.flipH,
            flipV = state.flipV,
            crop = state.crop
        )
        lookThumbs = withContext(Dispatchers.Default) {
            val base = BitmapBridge.downscale(PixelPipeline.applyGeometry(source, geoState), 168)
            Looks.ALL.associate { look ->
                look.id to BitmapBridge.toBitmap(
                    PixelPipeline.render(base, EditState(lookId = look.id, lookIntensity = 100f))
                )
            }
        }
    }

    // Display-frame (post-rotation) aspect, for crop aspect maths.
    val rotated = state.rotation == 90 || state.rotation == 270
    val frameW = if (rotated) source.height else source.width
    val frameH = if (rotated) source.width else source.height
    val frameAspect = frameW.toFloat() / frameH.toFloat()
    val aspectNorm = (aspectSel as? AspectSel.Fixed)?.let { it.ratio / frameAspect }

    Column(modifier = modifier.fillMaxSize().background(AppBg)) {
        // ---- Top bar ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    history.undo()?.let { state = it }
                    historyTick++
                },
                enabled = history.canUndo
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(
                onClick = {
                    history.redo()?.let { state = it }
                    historyTick++
                },
                enabled = history.canRedo
            ) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }
            // Hold-to-compare: press and hold to see the untouched original.
            Box(
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        showOriginal = true
                        tryAwaitRelease()
                        showOriginal = false
                    })
                }
            ) {
                IconButton(onClick = { /* hold gesture does the work */ }) {
                    Icon(Icons.Filled.Visibility, contentDescription = "Hold to view original")
                }
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = { showExportSheet = true },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export", fontWeight = FontWeight.SemiBold)
            }
        }

        // ---- Canvas ----
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(CanvasBg),
            contentAlignment = Alignment.Center
        ) {
            var boxSize by remember { mutableStateOf(IntSize.Zero) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { boxSize = it },
                contentAlignment = Alignment.Center
            ) {
                val shown = if (showOriginal) originalBitmap else rendered
                if (shown != null) {
                    Image(
                        bitmap = shown.asImageBitmap(),
                        contentDescription = "Photo being edited",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(color = Accent)
                }
                if (cropMode && !showOriginal) {
                    rendered?.let { bmp ->
                        CropOverlay(
                            containerSize = boxSize,
                            imageWidth = bmp.width,
                            imageHeight = bmp.height,
                            crop = state.crop,
                            aspectNorm = aspectNorm,
                            onCommit = { commit(state.copy(crop = it)) }
                        )
                    }
                }
                if (showOriginal) {
                    Text(
                        "Original",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                if (exporting) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Accent)
                            Spacer(Modifier.height(10.dp))
                            Text("Exporting at full resolution…", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // ---- Tool panel ----
        when (activeTool) {
            EditorTool.Looks -> LooksPanel(
                thumbnails = lookThumbs,
                selectedLookId = state.lookId,
                intensity = state.lookIntensity,
                onSelectLook = { id ->
                    commit(
                        state.copy(
                            lookId = id,
                            lookIntensity = if (id == state.lookId) state.lookIntensity else 100f
                        )
                    )
                },
                onIntensityDraft = { state = state.copy(lookIntensity = it) },
                onIntensityCommit = { commitCurrent() }
            )

            EditorTool.Adjust -> AdjustPanel(
                adj = state.adj,
                onDraft = { state = state.copy(adj = it) },
                onCommit = { commitCurrent() }
            )

            EditorTool.Crop -> CropPanel(
                aspectSel = aspectSel,
                onAspectSelected = { sel ->
                    aspectSel = sel
                    when (sel) {
                        is AspectSel.Fixed -> commit(
                            state.copy(crop = CropGeometry.rectWithAspect(state.crop, sel.ratio, frameAspect))
                        )
                        AspectSel.Original -> commit(state.copy(crop = CropRect.FULL))
                        AspectSel.Free -> Unit
                    }
                },
                onRotate = {
                    commit(
                        state.copy(
                            rotation = (state.rotation + 90) % 360,
                            crop = CropGeometry.rotateRectCw(state.crop)
                        )
                    )
                },
                onFlipH = {
                    commit(state.copy(flipH = !state.flipH, crop = CropGeometry.flipHRect(state.crop)))
                },
                onFlipV = {
                    commit(state.copy(flipV = !state.flipV, crop = CropGeometry.flipVRect(state.crop)))
                },
                onResetCrop = {
                    aspectSel = AspectSel.Original
                    commit(state.copy(crop = CropRect.FULL))
                }
            )

            else -> StubPanel(activeTool)
        }

        // ---- Tool carousel ----
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(ChromeBar),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            items(EditorTool.entries, key = { it.name }) { tool ->
                val selected = tool == activeTool
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(68.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Color(0xFF2C2C2C) else Color.Transparent)
                        .clickable { activeTool = tool }
                        .padding(vertical = 7.dp)
                ) {
                    Box {
                        Icon(
                            tool.icon,
                            contentDescription = tool.label,
                            tint = when {
                                selected -> Color.White
                                tool.implemented -> Color(0xFFC9C9C9)
                                else -> Color(0xFF6E6E6E)
                            },
                            modifier = Modifier.size(25.dp)
                        )
                        if (!tool.implemented) {
                            Text(
                                "SOON",
                                fontSize = 6.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 9.dp, y = (-3).dp)
                                    .background(Accent, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        tool.label,
                        fontSize = 11.sp,
                        color = when {
                            selected -> Color.White
                            tool.implemented -> Color(0xFFC9C9C9)
                            else -> Color(0xFF6E6E6E)
                        }
                    )
                }
            }
        }

        SnackbarHost(snackbar)
    }

    if (showExportSheet) {
        ModalBottomSheet(onDismissRequest = { showExportSheet = false }) {
            // The sheet is its own window, outside the root inset gate in
            // MainActivity: keep its actions clear of the navigation bar.
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                ListItem(
                    headlineContent = { Text("Save a copy") },
                    supportingContent = { Text("Full-resolution JPEG in Pictures/PhotoCraftExpress") },
                    leadingContent = { Icon(Icons.Filled.Save, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showExportSheet = false
                        exporting = true
                        scope.launch {
                            val msg = onExport(state, false)
                            exporting = false
                            snackbar.showSnackbar(msg)
                        }
                    }
                )
                ListItem(
                    headlineContent = { Text("Share") },
                    supportingContent = { Text("Send the edited photo to another app") },
                    leadingContent = { Icon(Icons.Filled.Share, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showExportSheet = false
                        exporting = true
                        scope.launch {
                            val msg = onExport(state, true)
                            exporting = false
                            snackbar.showSnackbar(msg)
                        }
                    }
                )
            }
        }
    }
}
