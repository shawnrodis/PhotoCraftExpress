package com.shawnrodis.photocraftexpress.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawnrodis.photocraftexpress.engine.Adjustments
import com.shawnrodis.photocraftexpress.engine.Looks

/** The bottom tool carousel, in Photoshop Express's order. */
enum class EditorTool(val label: String, val icon: ImageVector, val implemented: Boolean) {
    Looks("Looks", LooksIcon, true),
    Crop("Crop", CropIcon, true),
    Adjust("Adjust", AdjustIcon, true),
    Healing("Healing", HealingIcon, false),
    Effects("Effects", EffectsIcon, false),
    Text("Text", TextIcon, false),
    Borders("Borders", BordersIcon, false),
    Resize("Resize", ResizeIcon, false)
}

/** Crop aspect choices; [ratio] null means "no fixed ratio". */
sealed interface AspectSel {
    object Original : AspectSel
    object Free : AspectSel
    data class Fixed(val ratio: Float) : AspectSel
}

val ASPECT_CHIPS: List<Pair<String, AspectSel>> = listOf(
    "Original" to AspectSel.Original,
    "Free" to AspectSel.Free,
    "1:1" to AspectSel.Fixed(1f),
    "4:3" to AspectSel.Fixed(4f / 3f),
    "3:2" to AspectSel.Fixed(3f / 2f),
    "16:9" to AspectSel.Fixed(16f / 9f),
    "9:16" to AspectSel.Fixed(9f / 16f),
    "3:4" to AspectSel.Fixed(3f / 4f),
    "2:3" to AspectSel.Fixed(2f / 3f)
)

// ----------------------------------------------------------------------
// Looks
// ----------------------------------------------------------------------

@Composable
fun LooksPanel(
    thumbnails: Map<String, Bitmap>,
    selectedLookId: String,
    intensity: Float,
    onSelectLook: (String) -> Unit,
    onIntensityDraft: (Float) -> Unit,
    onIntensityCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(PanelBg)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(Looks.ALL, key = { it.id }) { look ->
                val selected = look.id == selectedLookId
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2A2A2A))
                            .then(
                                if (selected) Modifier.border(2.dp, Accent, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .clickable { onSelectLook(look.id) }
                    ) {
                        thumbnails[look.id]?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = look.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        look.name,
                        fontSize = 11.sp,
                        color = if (selected) Color.White else Color(0xFFBDBDBD),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
        if (selectedLookId != Looks.ORIGINAL_ID) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
            ) {
                Text("Intensity", fontSize = 13.sp, color = Color(0xFFE0E0E0))
                Spacer(Modifier.width(12.dp))
                Slider(
                    value = intensity,
                    onValueChange = onIntensityDraft,
                    onValueChangeFinished = onIntensityCommit,
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = Accent,
                        activeTrackColor = Accent,
                        inactiveTrackColor = Color(0xFF4A4A4A)
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Text("${intensity.toInt()}", fontSize = 13.sp, color = Color(0xFFE0E0E0))
            }
        }
    }
}

// ----------------------------------------------------------------------
// Adjust
// ----------------------------------------------------------------------

private data class SliderSpec(
    val name: String,
    val range: ClosedFloatingPointRange<Float>,
    val get: (Adjustments) -> Float,
    val set: (Adjustments, Float) -> Adjustments
)

private val SLIDER_SPECS: List<SliderSpec> = listOf(
    SliderSpec("Exposure", -100f..100f, { it.exposure }, { a, v -> a.copy(exposure = v) }),
    SliderSpec("Contrast", -100f..100f, { it.contrast }, { a, v -> a.copy(contrast = v) }),
    SliderSpec("Highlights", -100f..100f, { it.highlights }, { a, v -> a.copy(highlights = v) }),
    SliderSpec("Shadows", -100f..100f, { it.shadows }, { a, v -> a.copy(shadows = v) }),
    SliderSpec("Whites", -100f..100f, { it.whites }, { a, v -> a.copy(whites = v) }),
    SliderSpec("Blacks", -100f..100f, { it.blacks }, { a, v -> a.copy(blacks = v) }),
    SliderSpec("Temperature", -100f..100f, { it.temperature }, { a, v -> a.copy(temperature = v) }),
    SliderSpec("Tint", -100f..100f, { it.tint }, { a, v -> a.copy(tint = v) }),
    SliderSpec("Vibrance", -100f..100f, { it.vibrance }, { a, v -> a.copy(vibrance = v) }),
    SliderSpec("Saturation", -100f..100f, { it.saturation }, { a, v -> a.copy(saturation = v) }),
    SliderSpec("Sharpen", 0f..100f, { it.sharpen }, { a, v -> a.copy(sharpen = v) })
)

private fun formatSliderValue(v: Float): String = when {
    v > 0.5f -> "+${v.toInt()}"
    v < -0.5f -> "${v.toInt()}"
    else -> "0"
}

@Composable
fun AdjustPanel(
    adj: Adjustments,
    onDraft: (Adjustments) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = modifier
            .background(PanelBg)
            .heightIn(max = 336.dp)
    ) {
        items(SLIDER_SPECS, key = { it.name }) { spec ->
            val value = spec.get(adj)
            val isExpanded = expanded == spec.name
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = if (isExpanded) null else spec.name }
                        .padding(horizontal = 16.dp, vertical = 13.dp)
                ) {
                    Text(
                        spec.name,
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    if (value != 0f) {
                        IconButton(
                            onClick = { onDraft(spec.set(adj, 0f)); onCommit() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Filled.RestartAlt,
                                contentDescription = "Reset ${spec.name}",
                                tint = Color(0xFFBDBDBD),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Text(
                        formatSliderValue(value),
                        fontSize = 13.sp,
                        color = if (value != 0f) Accent else Color(0xFF9E9E9E),
                        modifier = Modifier.width(36.dp)
                    )
                    Icon(
                        if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (isExpanded) {
                    Slider(
                        value = value,
                        onValueChange = { onDraft(spec.set(adj, it)) },
                        onValueChangeFinished = onCommit,
                        valueRange = spec.range,
                        colors = SliderDefaults.colors(
                            thumbColor = Accent,
                            activeTrackColor = Accent,
                            inactiveTrackColor = Color(0xFF4A4A4A)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(0.7.dp)
                        .background(Color(0xFF2C2C2C))
                )
            }
        }
    }
}

// ----------------------------------------------------------------------
// Crop
// ----------------------------------------------------------------------

@Composable
fun CropPanel(
    aspectSel: AspectSel,
    onAspectSelected: (AspectSel) -> Unit,
    onRotate: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onResetCrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(PanelBg)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ASPECT_CHIPS, key = { it.first }) { (label, sel) ->
                val selected = when {
                    sel is AspectSel.Fixed && aspectSel is AspectSel.Fixed -> sel.ratio == aspectSel.ratio
                    else -> sel::class == aspectSel::class
                }
                FilterChip(
                    selected = selected,
                    onClick = { onAspectSelected(sel) },
                    label = { Text(label, fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF2A2A2A),
                        labelColor = Color(0xFFE0E0E0),
                        selectedContainerColor = Accent,
                        selectedLabelColor = Color.White
                    ),
                    border = null
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        ) {
            CropAction(Icons.Filled.RotateRight, "Rotate", onRotate)
            CropAction(Icons.Filled.Flip, "Flip H", onFlipH)
            CropAction(Icons.Filled.Flip, "Flip V", onFlipV, rotateIcon = true)
            CropAction(Icons.Filled.RestartAlt, "Reset", onResetCrop)
        }
    }
}

@Composable
private fun CropAction(icon: ImageVector, label: String, onClick: () -> Unit, rotateIcon: Boolean = false) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier
                    .size(22.dp)
                    .then(if (rotateIcon) Modifier.rotate(90f) else Modifier)
            )
            Text(label, fontSize = 11.sp, color = Color(0xFFE0E0E0))
        }
    }
}

// ----------------------------------------------------------------------
// Stub tools (present in the carousel, honestly not built yet)
// ----------------------------------------------------------------------

@Composable
fun StubPanel(tool: EditorTool, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(PanelBg)
            .fillMaxWidth()
            .padding(vertical = 22.dp, horizontal = 24.dp)
    ) {
        Icon(
            tool.icon,
            contentDescription = null,
            tint = Color(0xFF8A8A8A),
            modifier = Modifier.size(34.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${tool.label} is coming in a future update.",
            fontSize = 14.sp,
            color = Color(0xFFE0E0E0),
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Not part of v0.1.0 yet — Looks, Crop and Adjust are fully working.",
            fontSize = 12.sp,
            color = Color(0xFF9E9E9E)
        )
    }
}
