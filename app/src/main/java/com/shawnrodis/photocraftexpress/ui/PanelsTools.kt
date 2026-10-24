package com.shawnrodis.photocraftexpress.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawnrodis.photocraftexpress.engine.BackgroundFx
import com.shawnrodis.photocraftexpress.engine.BackgroundMode
import com.shawnrodis.photocraftexpress.engine.BorderFx
import com.shawnrodis.photocraftexpress.engine.BorderKind
import com.shawnrodis.photocraftexpress.engine.FaceAdjust
import com.shawnrodis.photocraftexpress.engine.OverlayFx
import com.shawnrodis.photocraftexpress.engine.StickerKind
import com.shawnrodis.photocraftexpress.engine.TextFont
import com.shawnrodis.photocraftexpress.engine.WarpMath

// ----------------------------------------------------------------------
// Shared bits
// ----------------------------------------------------------------------

val COLOR_CHOICES: List<Pair<String, Int>> = listOf(
    "White" to -0x1,
    "Black" to -0x1000000,
    "Red" to 0xFFE53935.toInt(),
    "Orange" to 0xFFFF8C00.toInt(),
    "Yellow" to 0xFFFFD600.toInt(),
    "Green" to 0xFF43A047.toInt(),
    "Blue" to 0xFF1E88E5.toInt(),
    "Purple" to 0xFF8E24AA.toInt(),
    "Pink" to 0xFFEC407A.toInt()
)

@Composable
fun ColorChips(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        items(COLOR_CHOICES, key = { it.first }) { (name, argb) ->
            val sel = argb == selected
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .then(
                        if (sel) Modifier.border(2.dp, Accent, CircleShape)
                        else Modifier.border(1.dp, Color(0xFF666666), CircleShape)
                    )
                    .clickable { onSelect(argb) }
            )
        }
    }
}

@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 13.sp,
        color = Color(0xFFBDBDBD),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
fun NoFaceText() {
    Text(
        "No face found in this photo. Face tools need a visible face.",
        fontSize = 13.sp,
        color = Color(0xFFBDBDBD),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
    )
}

// ----------------------------------------------------------------------
// Heal / Smooth
// ----------------------------------------------------------------------

@Composable
fun HealPanel(
    brushSize: Float,
    onBrushSize: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    PanelColumn(modifier) {
        HintText("Tap a blemish on the photo to heal it. The patch is sampled from the skin around the spot.")
        Column(Modifier.padding(horizontal = 16.dp)) {
            ToolSlider(
                "Brush size", brushSize, 0.01f..0.15f,
                onBrushSize, {}, format = { "${(it * 100).toInt()}%" }
            )
        }
    }
}

@Composable
fun SmoothPanel(
    strength: Float,
    onDraft: (Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    PanelColumn(modifier) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            ToolSlider("Smooth", strength, 0f..100f, onDraft, onCommit)
        }
        HintText("Global smoothing in this version (not face-masked).")
    }
}

// ----------------------------------------------------------------------
// Face
// ----------------------------------------------------------------------

@Composable
fun FacePanel(
    hasFace: Boolean,
    face: FaceAdjust,
    onDraft: (FaceAdjust) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableStateOf("Face") }
    PanelColumn(modifier) {
        if (!hasFace) {
            NoFaceText()
            return@PanelColumn
        }
        ModeChips(
            options = listOf("Face", "Lips", "Nose", "Eyes", "Contour"),
            selected = tab,
            onSelect = { tab = it }
        )
        Column(Modifier.padding(horizontal = 16.dp)) {
            when (tab) {
                "Face" -> {
                    ToolSlider("Face Size", face.faceSize, -100f..100f, { onDraft(face.copy(faceSize = it)) }, onCommit)
                    ToolSlider("Face Width", face.faceWidth, -100f..100f, { onDraft(face.copy(faceWidth = it)) }, onCommit)
                }
                "Lips" -> ToolSlider("Lip Size", face.lipSize, -100f..100f, { onDraft(face.copy(lipSize = it)) }, onCommit)
                "Nose" -> ToolSlider("Nose Width", face.noseWidth, -100f..100f, { onDraft(face.copy(noseWidth = it)) }, onCommit)
                "Eyes" -> ToolSlider("Eye Size", face.eyeSize, -100f..100f, { onDraft(face.copy(eyeSize = it)) }, onCommit)
                "Contour" -> ToolSlider("Contour", face.contour, -100f..100f, { onDraft(face.copy(contour = it)) }, onCommit)
            }
        }
    }
}

// ----------------------------------------------------------------------
// Reshape
// ----------------------------------------------------------------------

@Composable
fun ReshapePanel(
    mode: WarpMath.BrushMode,
    brushSize: Float,
    onMode: (WarpMath.BrushMode) -> Unit,
    onBrushSize: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    PanelColumn(modifier) {
        ModeChips(
            options = listOf("Reshape", "Swell", "Twirl", "Restore"),
            selected = when (mode) {
                WarpMath.BrushMode.PUSH -> "Reshape"
                WarpMath.BrushMode.SWELL -> "Swell"
                WarpMath.BrushMode.TWIRL -> "Twirl"
                WarpMath.BrushMode.RESTORE -> "Restore"
            },
            onSelect = {
                onMode(
                    when (it) {
                        "Swell" -> WarpMath.BrushMode.SWELL
                        "Twirl" -> WarpMath.BrushMode.TWIRL
                        "Restore" -> WarpMath.BrushMode.RESTORE
                        else -> WarpMath.BrushMode.PUSH
                    }
                )
            }
        )
        Column(Modifier.padding(horizontal = 16.dp)) {
            ToolSlider(
                "Brush size", brushSize, 0.02f..0.4f,
                onBrushSize, {}, format = { "${(it * 100).toInt()}%" }
            )
        }
        HintText(
            when (mode) {
                WarpMath.BrushMode.PUSH -> "Drag on the photo to push pixels along."
                WarpMath.BrushMode.SWELL -> "Drag over an area to swell it outward."
                WarpMath.BrushMode.TWIRL -> "Drag over an area to twirl it."
                WarpMath.BrushMode.RESTORE -> "Drag over reshaped areas to restore them."
            }
        )
    }
}

// ----------------------------------------------------------------------
// Makeup
// ----------------------------------------------------------------------

@Composable
fun MakeupPanel(
    hasFace: Boolean,
    lipColor: Int,
    lip: Float,
    blush: Float,
    shadow: Float,
    liner: Float,
    onLipColor: (Int) -> Unit,
    onDraft: (Float, Float, Float, Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    PanelColumn(modifier) {
        if (!hasFace) {
            NoFaceText()
            return@PanelColumn
        }
        Text("Lip colour", fontSize = 13.sp, color = Color(0xFFE0E0E0), modifier = Modifier.padding(horizontal = 16.dp))
        ColorChips(selected = lipColor, onSelect = onLipColor)
        Column(Modifier.padding(horizontal = 16.dp)) {
            ToolSlider("Lip Tint", lip, 0f..100f, { onDraft(it, blush, shadow, liner) }, onCommit)
            ToolSlider("Blush", blush, 0f..100f, { onDraft(lip, it, shadow, liner) }, onCommit)
            ToolSlider("Eye Shadow", shadow, 0f..100f, { onDraft(lip, blush, it, liner) }, onCommit)
            ToolSlider("Eyeliner", liner, 0f..100f, { onDraft(lip, blush, shadow, it) }, onCommit)
        }
    }
}

// ----------------------------------------------------------------------
// Draw
// ----------------------------------------------------------------------

@Composable
fun DrawPanel(
    color: Int,
    width: Float,
    opacity: Float,
    onColor: (Int) -> Unit,
    onWidth: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    PanelColumn(modifier) {
        ColorChips(selected = color, onSelect = onColor)
        Column(Modifier.padding(horizontal = 16.dp)) {
            ToolSlider(
                "Brush width", width, 0.005f..0.08f,
                onWidth, {}, format = { "${(it * 100).toInt()}%" }
            )
            ToolSlider(
                "Opacity", opacity, 0.1f..1f,
                onOpacity, {}, format = { "${(it * 100).toInt()}%" }
            )
        }
        HintText("Draw on the photo. Lift your finger to finish a stroke.")
    }
}

// ----------------------------------------------------------------------
// Background
// ----------------------------------------------------------------------

@Composable
fun BackgroundPanel(
    personFound: Boolean,
    fx: BackgroundFx,
    onDraft: (BackgroundFx) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    PanelColumn(modifier) {
        if (!personFound) {
            Text(
                "No person found in this photo. Background works on photos with people in this version.",
                fontSize = 13.sp,
                color = Color(0xFFBDBDBD),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
            return@PanelColumn
        }
        ModeChips(
            options = listOf("None", "Blur", "Replace"),
            selected = when (fx.mode) {
                BackgroundMode.BLUR -> "Blur"
                BackgroundMode.REPLACE -> "Replace"
                BackgroundMode.NONE -> "None"
            },
            onSelect = {
                onDraft(
                    fx.copy(
                        mode = when (it) {
                            "Blur" -> BackgroundMode.BLUR
                            "Replace" -> BackgroundMode.REPLACE
                            else -> BackgroundMode.NONE
                        }
                    )
                )
                onCommit()
            }
        )
        Column(Modifier.padding(horizontal = 16.dp)) {
            if (fx.mode == BackgroundMode.BLUR) {
                ToolSlider("Blur amount", fx.amount, 0f..100f, { onDraft(fx.copy(amount = it)) }, onCommit)
            }
            if (fx.mode == BackgroundMode.REPLACE) {
                Text("Background colour", fontSize = 13.sp, color = Color(0xFFE0E0E0))
                ColorChips(selected = fx.color, onSelect = { onDraft(fx.copy(color = it)); onCommit() })
            }
        }
    }
}

// ----------------------------------------------------------------------
// Insert Objects
// ----------------------------------------------------------------------

enum class InsertMode { PHOTO, BRUSH }

@Composable
fun InsertPanel(
    mode: InsertMode,
    brushSize: Float,
    hasLayer: Boolean,
    useMask: Boolean,
    layerScale: Float,
    layerRotation: Float,
    onMode: (InsertMode) -> Unit,
    onBrushSize: (Float) -> Unit,
    onAddPhoto: () -> Unit,
    onClearMask: () -> Unit,
    onUseMask: (Boolean) -> Unit,
    onLayerScale: (Float) -> Unit,
    onLayerScaleCommit: () -> Unit,
    onLayerRotation: (Float) -> Unit,
    onLayerRotationCommit: () -> Unit,
    onDeleteLayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    PanelColumn(modifier) {
        ModeChips(
            options = listOf("Photo", "Brush Select"),
            selected = if (mode == InsertMode.PHOTO) "Photo" else "Brush Select",
            onSelect = { onMode(if (it == "Brush Select") InsertMode.BRUSH else InsertMode.PHOTO) }
        )
        when (mode) {
            InsertMode.PHOTO -> {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Button(
                        onClick = onAddPhoto,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) { Text("Add photo") }
                    if (hasLayer) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDeleteLayer) { Text("Remove", color = Color(0xFFFF8A80)) }
                    }
                }
                if (hasLayer) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        ToolSlider(
                            "Size", layerScale, 0.1f..1.5f,
                            onLayerScale, onLayerScaleCommit, format = { "${(it * 100).toInt()}%" }
                        )
                        ToolSlider(
                            "Rotation", layerRotation, -180f..180f,
                            onLayerRotation, onLayerRotationCommit, format = { "${it.toInt()}°" }
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Clip to brushed area", fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
                            Switch(checked = useMask, onCheckedChange = onUseMask)
                        }
                    }
                    HintText("Drag the photo on the canvas to move it.")
                } else {
                    HintText("Pick a photo to insert it as a movable layer.")
                }
            }
            InsertMode.BRUSH -> {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    ToolSlider(
                        "Brush size", brushSize, 0.01f..0.2f,
                        onBrushSize, {}, format = { "${(it * 100).toInt()}%" }
                    )
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    TextButton(onClick = onClearMask) { Text("Clear selection", color = Accent) }
                }
                HintText("Brush on the photo to select an area. With \"Clip to brushed area\" on, the inserted photo only shows inside the brushed region.")
            }
        }
    }
}

// ----------------------------------------------------------------------
// Overlays
// ----------------------------------------------------------------------

val OVERLAY_CHOICES: List<Pair<String, String>> = listOf(
    "None" to "",
    "Light Leak" to "lightleak",
    "Bokeh" to "bokeh",
    "Warm Gradient" to "warmgrad",
    "Cool Gradient" to "coolgrad",
    "Old Photo" to "oldphoto"
)

@Composable
fun OverlaysPanel(
    overlay: OverlayFx,
    onDraft: (OverlayFx) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    PanelColumn(modifier) {
        ModeChips(
            options = OVERLAY_CHOICES.map { it.first },
            selected = OVERLAY_CHOICES.firstOrNull { it.second == overlay.id }?.first ?: "None",
            onSelect = { name ->
                val id = OVERLAY_CHOICES.firstOrNull { it.first == name }?.second ?: ""
                onDraft(overlay.copy(id = id, intensity = if (id.isEmpty()) 0f else if (overlay.intensity == 0f) 70f else overlay.intensity))
                onCommit()
            }
        )
        if (overlay.id.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                ToolSlider("Intensity", overlay.intensity, 0f..100f, { onDraft(overlay.copy(intensity = it)) }, onCommit)
            }
        }
    }
}

// ----------------------------------------------------------------------
// Text
// ----------------------------------------------------------------------

enum class TextTab { STYLES, FONT, COLOR, STROKE }

enum class TextStylePreset { BASIC, QUOTE, MEME }

@Composable
fun TextPanel(
    tab: TextTab,
    onTab: (TextTab) -> Unit,
    selectedText: com.shawnrodis.photocraftexpress.engine.TextItem?,
    onAddText: (String, TextStylePreset) -> Unit,
    onApplyStyle: (TextStylePreset) -> Unit,
    onDraftSelected: (com.shawnrodis.photocraftexpress.engine.TextItem) -> Unit,
    onCommit: () -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember { mutableStateOf("") }
    PanelColumn(modifier) {
        ModeChips(
            options = listOf("Styles", "Font", "Color", "Stroke"),
            selected = when (tab) {
                TextTab.STYLES -> "Styles"
                TextTab.FONT -> "Font"
                TextTab.COLOR -> "Color"
                TextTab.STROKE -> "Stroke"
            },
            onSelect = {
                onTab(
                    when (it) {
                        "Font" -> TextTab.FONT
                        "Color" -> TextTab.COLOR
                        "Stroke" -> TextTab.STROKE
                        else -> TextTab.STYLES
                    }
                )
            }
        )
        when (tab) {
            TextTab.STYLES -> {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("Type something…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { if (draft.isNotBlank()) { onAddText(draft, TextStylePreset.BASIC); draft = "" } },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) { Text("Add Basic") }
                        Button(
                            onClick = { if (draft.isNotBlank()) { onAddText(draft, TextStylePreset.QUOTE); draft = "" } },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) { Text("Add Quote") }
                        Button(
                            onClick = { if (draft.isNotBlank()) { onAddText(draft, TextStylePreset.MEME); draft = "" } },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) { Text("Add Meme") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onApplyStyle(TextStylePreset.BASIC) }) { Text("Basic style", color = Accent) }
                        TextButton(onClick = { onApplyStyle(TextStylePreset.QUOTE) }) { Text("Quote style", color = Accent) }
                        TextButton(onClick = { onApplyStyle(TextStylePreset.MEME) }) { Text("Meme style", color = Accent) }
                    }
                    if (selectedText != null) {
                        TextButton(onClick = onDeleteSelected) { Text("Delete selected text", color = Color(0xFFFF8A80)) }
                    }
                    HintText("Basic: clean sans text. Quote: large serif italic. Meme: bold white text with a thick black outline. Tap text on the photo to select it, then drag to move.")
                }
            }
            TextTab.FONT -> {
                if (selectedText == null) {
                    HintText("Add text first, then tap it on the photo to select it.")
                } else {
                    ModeChips(
                        options = listOf("Sans", "Serif", "Mono"),
                        selected = when (selectedText.font) {
                            TextFont.SANS -> "Sans"
                            TextFont.SERIF -> "Serif"
                            TextFont.MONO -> "Mono"
                        },
                        onSelect = {
                            onDraftSelected(
                                selectedText.copy(
                                    font = when (it) {
                                        "Serif" -> TextFont.SERIF
                                        "Mono" -> TextFont.MONO
                                        else -> TextFont.SANS
                                    }
                                )
                            )
                            onCommit()
                        }
                    )
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        ToolSlider(
                            "Size", selectedText.size * 100f, 2f..30f,
                            { onDraftSelected(selectedText.copy(size = it / 100f)) }, onCommit,
                            format = { "${it.toInt()}%" }
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Bold", fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
                            Switch(
                                checked = selectedText.bold,
                                onCheckedChange = { onDraftSelected(selectedText.copy(bold = it)); onCommit() }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Italic", fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
                            Switch(
                                checked = selectedText.italic,
                                onCheckedChange = { onDraftSelected(selectedText.copy(italic = it)); onCommit() }
                            )
                        }
                    }
                }
            }
            TextTab.COLOR -> {
                if (selectedText == null) {
                    HintText("Add text first, then tap it on the photo to select it.")
                } else {
                    ColorChips(
                        selected = selectedText.color,
                        onSelect = { onDraftSelected(selectedText.copy(color = it)); onCommit() }
                    )
                }
            }
            TextTab.STROKE -> {
                if (selectedText == null) {
                    HintText("Add text first, then tap it on the photo to select it.")
                } else {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        ToolSlider(
                            "Outline width", selectedText.strokeWidth * 100f, 0f..25f,
                            { onDraftSelected(selectedText.copy(strokeWidth = it / 100f)) }, onCommit,
                            format = { "${it.toInt()}%" }
                        )
                    }
                    Text("Outline colour", fontSize = 13.sp, color = Color(0xFFE0E0E0), modifier = Modifier.padding(horizontal = 16.dp))
                    ColorChips(
                        selected = selectedText.strokeColor,
                        onSelect = { onDraftSelected(selectedText.copy(strokeColor = it)); onCommit() }
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Stickers
// ----------------------------------------------------------------------

@Composable
fun StickersPanel(
    selectedKind: StickerKind,
    onKind: (StickerKind) -> Unit,
    hasSelection: Boolean,
    selScale: Float,
    selRotation: Float,
    onSelScale: (Float) -> Unit,
    onSelScaleCommit: () -> Unit,
    onSelRotation: (Float) -> Unit,
    onSelRotationCommit: () -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    PanelColumn(modifier) {
        ModeChips(
            options = StickerKind.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            selected = selectedKind.name.lowercase().replaceFirstChar { c -> c.uppercase() },
            onSelect = { name ->
                onKind(StickerKind.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: StickerKind.STAR)
            }
        )
        HintText("Tap the photo to place the selected sticker, then drag it to move.")
        if (hasSelection) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                ToolSlider(
                    "Size", selScale * 100f, 5f..60f,
                    onSelScale, onSelScaleCommit, format = { "${it.toInt()}%" }
                )
                ToolSlider(
                    "Rotation", selRotation, -180f..180f,
                    onSelRotation, onSelRotationCommit, format = { "${it.toInt()}°" }
                )
            }
            TextButton(onClick = onDeleteSelected, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Delete selected sticker", color = Color(0xFFFF8A80))
            }
        }
    }
}

// ----------------------------------------------------------------------
// Borders
// ----------------------------------------------------------------------

enum class BorderTab { BASIC, EDGES, FRAMES }

@Composable
fun BordersPanel(
    border: BorderFx,
    tab: BorderTab,
    onTab: (BorderTab) -> Unit,
    onDraft: (BorderFx) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    PanelColumn(modifier) {
        ModeChips(
            options = listOf("Basic", "Edges", "Frames"),
            selected = when (tab) {
                BorderTab.BASIC -> "Basic"
                BorderTab.EDGES -> "Edges"
                BorderTab.FRAMES -> "Frames"
            },
            onSelect = {
                onTab(
                    when (it) {
                        "Edges" -> BorderTab.EDGES
                        "Frames" -> BorderTab.FRAMES
                        else -> BorderTab.BASIC
                    }
                )
            }
        )
        when (tab) {
            BorderTab.BASIC -> {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    ToolSlider(
                        "Width", border.width * 100f, 0f..15f,
                        { onDraft(border.copy(kind = BorderKind.PLAIN, preset = "", width = it / 100f)) }, onCommit,
                        format = { "${it.toInt()}%" }
                    )
                }
                Text("Colour", fontSize = 13.sp, color = Color(0xFFE0E0E0), modifier = Modifier.padding(horizontal = 16.dp))
                ColorChips(selected = border.color, onSelect = { onDraft(border.copy(color = it)); onCommit() })
            }
            BorderTab.EDGES -> {
                ModeChips(
                    options = listOf("Torn", "Scallop", "Double"),
                    selected = when (border.preset) {
                        "torn" -> "Torn"
                        "scallop" -> "Scallop"
                        "double" -> "Double"
                        else -> "Torn"
                    },
                    onSelect = { name ->
                        val id = name.lowercase()
                        onDraft(border.copy(kind = BorderKind.EDGE, preset = id, width = if (border.width == 0f) 0.05f else border.width))
                        onCommit()
                    }
                )
                Column(Modifier.padding(horizontal = 16.dp)) {
                    ToolSlider(
                        "Width", border.width * 100f, 1f..15f,
                        { onDraft(border.copy(width = it / 100f)) }, onCommit,
                        format = { "${it.toInt()}%" }
                    )
                }
                ColorChips(selected = border.color, onSelect = { onDraft(border.copy(color = it)); onCommit() })
            }
            BorderTab.FRAMES -> {
                ModeChips(
                    options = listOf("Polaroid", "Filmstrip", "Vintage"),
                    selected = when (border.preset) {
                        "polaroid" -> "Polaroid"
                        "filmstrip" -> "Filmstrip"
                        "vintage" -> "Vintage"
                        else -> "Polaroid"
                    },
                    onSelect = { name ->
                        val id = name.lowercase()
                        onDraft(border.copy(kind = BorderKind.FRAME, preset = id, width = if (border.width == 0f) 0.06f else border.width))
                        onCommit()
                    }
                )
                ColorChips(selected = border.color, onSelect = { onDraft(border.copy(color = it)); onCommit() })
                HintText("Polaroid: thick white frame, deeper at the bottom. Filmstrip: black with sprocket holes. Vintage: warm double frame with a gold line.")
            }
        }
    }
}

// ----------------------------------------------------------------------
// Eyes
// ----------------------------------------------------------------------

enum class EyesMode { ENHANCE, RED_EYE, PET_EYE }

@Composable
fun EyesPanel(
    mode: EyesMode,
    hasFace: Boolean,
    enhance: Float,
    brushSize: Float,
    onMode: (EyesMode) -> Unit,
    onEnhance: (Float) -> Unit,
    onBrushSize: (Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    PanelColumn(modifier) {
        ModeChips(
            options = listOf("Enhance", "Red Eye", "Pet Eye"),
            selected = when (mode) {
                EyesMode.ENHANCE -> "Enhance"
                EyesMode.RED_EYE -> "Red Eye"
                EyesMode.PET_EYE -> "Pet Eye"
            },
            onSelect = {
                onMode(
                    when (it) {
                        "Red Eye" -> EyesMode.RED_EYE
                        "Pet Eye" -> EyesMode.PET_EYE
                        else -> EyesMode.ENHANCE
                    }
                )
            }
        )
        when (mode) {
            EyesMode.ENHANCE -> {
                if (!hasFace) {
                    NoFaceText()
                } else {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        ToolSlider("Eye Brightness", enhance, 0f..100f, onEnhance, onCommit)
                    }
                }
            }
            EyesMode.RED_EYE -> {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    ToolSlider(
                        "Brush size", brushSize, 0.01f..0.12f,
                        onBrushSize, {}, format = { "${(it * 100).toInt()}%" }
                    )
                }
                HintText("Tap on the red eyes to correct them.")
            }
            EyesMode.PET_EYE -> {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    ToolSlider(
                        "Brush size", brushSize, 0.01f..0.12f,
                        onBrushSize, {}, format = { "${(it * 100).toInt()}%" }
                    )
                }
                HintText("Tap on the glowing eyes to correct them to a natural tone.")
            }
        }
    }
}
