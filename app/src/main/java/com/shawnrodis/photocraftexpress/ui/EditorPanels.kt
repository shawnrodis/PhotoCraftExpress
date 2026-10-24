package com.shawnrodis.photocraftexpress.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.LaunchedEffect
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
import com.shawnrodis.photocraftexpress.engine.AdjustMask
import com.shawnrodis.photocraftexpress.engine.Adjustments
import com.shawnrodis.photocraftexpress.engine.BlurFx
import com.shawnrodis.photocraftexpress.engine.BlurMode
import com.shawnrodis.photocraftexpress.engine.ColorMix
import com.shawnrodis.photocraftexpress.engine.Looks
import com.shawnrodis.photocraftexpress.engine.SplitTone
import com.shawnrodis.photocraftexpress.engine.Vignette
import com.shawnrodis.photocraftexpress.engine.ColorMath

/** The bottom tool carousel, in Photoshop Express's order and names. */
enum class EditorTool(val label: String, val icon: ImageVector) {
    Looks("Looks", LooksIcon),
    Adjustments("Adjustments", AdjustIcon),
    Heal("Heal", HealingIcon),
    Crop("Crop", CropIcon),
    Smooth("Smooth", SmoothIcon),
    Face("Face", FaceIcon),
    Reshape("Reshape", ReshapeIcon),
    Makeup("Makeup", MakeupIcon),
    Draw("Draw", DrawIcon),
    Background("Background", BackgroundIcon),
    InsertObjects("Insert Objects", InsertIcon),
    Overlays("Overlays", OverlaysIcon),
    Text("Text", TextIcon),
    Stickers("Stickers", StickersIcon),
    Borders("Borders", BordersIcon),
    Eyes("Eyes", EyesIcon)
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
    "2:3" to AspectSel.Fixed(2f / 3f),
    "1.91:1" to AspectSel.Fixed(1.91f),
    "4:5" to AspectSel.Fixed(4f / 5f)
)

/** Crop panel tabs, mirroring Express: aspect ratio, rotate, transform. */
enum class CropTab { ASPECT, ROTATE, TRANSFORM }

// ----------------------------------------------------------------------
// Shared panel pieces
// ----------------------------------------------------------------------

fun formatSliderValue(v: Float): String = when {
    v > 0.5f -> "+${v.toInt()}"
    v < -0.5f -> "${v.toInt()}"
    else -> "0"
}

/** A labelled slider row, always visible (used by the v0.2.0 tool panels). */
@Composable
fun ToolSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onDraft: (Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
    format: (Float) -> String = ::formatSliderValue
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
            if (value != 0f && range.start != 0f) {
                IconButton(
                    onClick = { onDraft(0f); onCommit() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.RestartAlt,
                        contentDescription = "Reset $label",
                        tint = Color(0xFFBDBDBD),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            Text(
                format(value),
                fontSize = 13.sp,
                color = if (value != 0f) Accent else Color(0xFF9E9E9E),
                modifier = Modifier.width(44.dp)
            )
        }
        Slider(
            value = value,
            onValueChange = onDraft,
            onValueChangeFinished = onCommit,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Color(0xFF4A4A4A)
            )
        )
    }
}

/** A horizontal chip row for panel tabs / modes. */
@Composable
fun ModeChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(options, key = { it }) { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option, fontSize = 13.sp) },
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
}

@Composable
fun PanelColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .background(PanelBg)
            .fillMaxWidth()
            .heightIn(max = 380.dp)
            .padding(vertical = 6.dp),
        content = content
    )
}

// ----------------------------------------------------------------------
// Looks (with categories)
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
    var category by remember { mutableStateOf(Looks.CATEGORIES.firstOrNull()?.first ?: "Normal") }
    val looksInCategory = Looks.CATEGORIES.firstOrNull { it.first == category }?.second
        ?: Looks.ALL
    // Follow the selected look's category ONLY when the look itself
    // changes. (Previously this ran on every composition, which snapped
    // the tab row back to the current look's category the moment the
    // user tapped any other tab — categories beyond the first were
    // unreachable. That was the v0.2.0 Looks bug.)
    LaunchedEffect(selectedLookId) {
        Looks.CATEGORIES.firstOrNull { (_, looks) ->
            looks.any { it.id == selectedLookId }
        }?.let { category = it.first }
    }
    Column(modifier = modifier.background(PanelBg)) {
        ModeChips(
            options = Looks.CATEGORIES.map { it.first },
            selected = category,
            onSelect = { category = it },
            modifier = Modifier.fillMaxWidth()
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(looksInCategory, key = { it.id }) { look ->
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
// Adjustments (tabs: Adjustments / Split Tone / Color Mix / Blur / Vignette)
// ----------------------------------------------------------------------

private data class SliderSpec(
    val name: String,
    val range: ClosedFloatingPointRange<Float>,
    val get: (Adjustments) -> Float,
    val set: (Adjustments, Float) -> Adjustments
)

private val ADJUST_SLIDER_SPECS: List<SliderSpec> = listOf(
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
    SliderSpec("Sharpen", 0f..100f, { it.sharpen }, { a, v -> a.copy(sharpen = v) }),
    SliderSpec("Texture", -100f..100f, { it.texture }, { a, v -> a.copy(texture = v) }),
    SliderSpec("Clarity", -100f..100f, { it.clarity }, { a, v -> a.copy(clarity = v) }),
    SliderSpec("Dehaze", -100f..100f, { it.dehaze }, { a, v -> a.copy(dehaze = v) }),
    SliderSpec("Reduce Color Noise", 0f..100f, { it.reduceColorNoise }, { a, v -> a.copy(reduceColorNoise = v) }),
    SliderSpec("Reduce Luminance Noise", 0f..100f, { it.reduceLuminanceNoise }, { a, v -> a.copy(reduceLuminanceNoise = v) }),
    SliderSpec("Fade", -100f..100f, { it.fade }, { a, v -> a.copy(fade = v) }),
    SliderSpec("Grain", 0f..100f, { it.grain }, { a, v -> a.copy(grain = v) })
)

/**
 * Demo states for the adjustment thumbnail strip: each adjustment alone
 * at +50 (or 50 for 0..100 sliders), rendered from the current photo.
 */
fun adjustThumbStates(): List<Pair<String, Adjustments>> =
    ADJUST_STRIP_ORDER.mapNotNull { name ->
        ADJUST_SLIDER_SPECS.firstOrNull { it.name == name }?.let { spec ->
            name to spec.set(Adjustments(), 50f)
        }
    }

enum class AdjustTab { ADJUSTMENTS, SPLIT_TONE, COLOR_MIX, BLUR, VIGNETTE, OPTICS }

/**
 * Strip order for the per-adjustment live thumbnails (Express order,
 * with our Texture appended last — Express's strip has no Texture, but
 * dropping an existing slider would be a feature cut, so it keeps its
 * place at the end of the strip).
 */
private val ADJUST_STRIP_ORDER: List<String> = listOf(
    "Clarity", "Sharpen", "Reduce Color Noise", "Reduce Luminance Noise",
    "Dehaze", "Grain", "Fade", "Exposure", "Contrast", "Highlights",
    "Shadows", "Whites", "Blacks", "Temperature", "Tint", "Vibrance",
    "Saturation", "Texture"
)

private val ADJUST_TAB_LABELS = listOf(
    "ADJUSTMENTS", "SPLIT TONE", "COLOR MIX", "BLUR", "VIGNETTE", "OPTICS"
)

@Composable
fun AdjustmentsPanel(
    adj: Adjustments,
    splitTone: SplitTone,
    colorMix: ColorMix,
    blur: BlurFx,
    vignette: Vignette,
    adjustTab: AdjustTab,
    onAdjustTab: (AdjustTab) -> Unit,
    onDraftAdj: (Adjustments) -> Unit,
    onDraftSplitTone: (SplitTone) -> Unit,
    onDraftColorMix: (ColorMix) -> Unit,
    onDraftBlur: (BlurFx) -> Unit,
    onDraftVignette: (Vignette) -> Unit,
    onCommit: () -> Unit,
    mask: AdjustMask,
    onMask: (AdjustMask) -> Unit,
    personFound: Boolean,
    adjustThumbs: Map<String, Bitmap>,
    maskThumbs: Map<AdjustMask, Bitmap>,
    modifier: Modifier = Modifier
) {
    val stripSpecs = ADJUST_STRIP_ORDER.mapNotNull { name ->
        ADJUST_SLIDER_SPECS.firstOrNull { it.name == name }
    }
    var selectedSpec by remember { mutableStateOf(stripSpecs.firstOrNull()) }
    if (selectedSpec == null) selectedSpec = stripSpecs.firstOrNull()
    Column(modifier = modifier.background(PanelBg)) {
        // Mask selector (Express Full / Subject / Background), with
        // small thumbnails: Subject dims the background, Background dims
        // the person. Without a detected person, selecting Subject or
        // Background shows a plain "No person found" notice.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (m in AdjustMask.values()) {
                val selected = m == mask
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onMask(m) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2A2A2A))
                            .then(
                                if (selected) Modifier.border(2.dp, Accent, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                    ) {
                        maskThumbs[m]?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = m.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        when (m) {
                            AdjustMask.FULL -> "Full"
                            AdjustMask.SUBJECT -> "Subject"
                            AdjustMask.BACKGROUND -> "Background"
                        },
                        fontSize = 11.sp,
                        color = if (selected) Color.White else Color(0xFFBDBDBD),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
        if (mask != AdjustMask.FULL && !personFound) {
            Text(
                "No person found",
                fontSize = 12.sp, color = Color(0xFF9E9E9E),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
        ModeChips(
            options = ADJUST_TAB_LABELS,
            selected = when (adjustTab) {
                AdjustTab.ADJUSTMENTS -> "ADJUSTMENTS"
                AdjustTab.SPLIT_TONE -> "SPLIT TONE"
                AdjustTab.COLOR_MIX -> "COLOR MIX"
                AdjustTab.BLUR -> "BLUR"
                AdjustTab.VIGNETTE -> "VIGNETTE"
                AdjustTab.OPTICS -> "OPTICS"
            },
            onSelect = {
                onAdjustTab(
                    when (it) {
                        "SPLIT TONE" -> AdjustTab.SPLIT_TONE
                        "COLOR MIX" -> AdjustTab.COLOR_MIX
                        "BLUR" -> AdjustTab.BLUR
                        "VIGNETTE" -> AdjustTab.VIGNETTE
                        "OPTICS" -> AdjustTab.OPTICS
                        else -> AdjustTab.ADJUSTMENTS
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        when (adjustTab) {
            AdjustTab.ADJUSTMENTS -> {
                // Per-adjustment live thumbnails: tapping one selects
                // that adjustment for the single main slider below.
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(stripSpecs, key = { it.name }) { spec ->
                        val selected = spec.name == selectedSpec?.name
                        val value = spec.get(adj)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { selectedSpec = spec }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2A2A2A))
                                    .then(
                                        if (selected) Modifier.border(2.dp, Accent, RoundedCornerShape(8.dp))
                                        else Modifier
                                    )
                            ) {
                                adjustThumbs[spec.name]?.let { bmp ->
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = spec.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                spec.name,
                                fontSize = 11.sp,
                                color = if (selected) Color.White else Color(0xFFBDBDBD),
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                formatSliderValue(value),
                                fontSize = 11.sp,
                                color = if (value != 0f) Accent else Color(0xFF9E9E9E)
                            )
                        }
                    }
                }
                selectedSpec?.let { spec ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        ToolSlider(
                            spec.name, spec.get(adj), spec.range,
                            { onDraftAdj(spec.set(adj, it)) }, onCommit
                        )
                    }
                }
            }
            AdjustTab.SPLIT_TONE -> {
                LazyColumn(Modifier.heightIn(max = 300.dp).padding(horizontal = 16.dp)) {
                    item {
                        ToolSlider(
                            "Shadow Hue", splitTone.shadowHue, 0f..360f,
                            { onDraftSplitTone(splitTone.copy(shadowHue = it)) }, onCommit,
                            format = { "${it.toInt()}°" }
                        )
                        ToolSlider(
                            "Shadow Saturation", splitTone.shadowSat, 0f..100f,
                            { onDraftSplitTone(splitTone.copy(shadowSat = it)) }, onCommit
                        )
                        ToolSlider(
                            "Highlight Hue", splitTone.highlightHue, 0f..360f,
                            { onDraftSplitTone(splitTone.copy(highlightHue = it)) }, onCommit,
                            format = { "${it.toInt()}°" }
                        )
                        ToolSlider(
                            "Highlight Saturation", splitTone.highlightSat, 0f..100f,
                            { onDraftSplitTone(splitTone.copy(highlightSat = it)) }, onCommit
                        )
                    }
                }
            }
            AdjustTab.COLOR_MIX -> {
                var channel by remember { mutableStateOf(0) }
                Column {
                    ModeChips(
                        options = ColorMath.MIX_CHANNELS.map { it.first },
                        selected = ColorMath.MIX_CHANNELS[channel].first,
                        onSelect = { name ->
                            channel = ColorMath.MIX_CHANNELS.indexOfFirst { it.first == name }.coerceAtLeast(0)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        ToolSlider(
                            "Hue", colorMix.hue[channel], -100f..100f,
                            { v ->
                                onDraftColorMix(
                                    colorMix.withChannel(
                                        channel, v, colorMix.sat[channel], colorMix.lum[channel]
                                    )
                                )
                            }, onCommit
                        )
                        ToolSlider(
                            "Saturation", colorMix.sat[channel], -100f..100f,
                            { v ->
                                onDraftColorMix(
                                    colorMix.withChannel(
                                        channel, colorMix.hue[channel], v, colorMix.lum[channel]
                                    )
                                )
                            }, onCommit
                        )
                        ToolSlider(
                            "Luminance", colorMix.lum[channel], -100f..100f,
                            { v ->
                                onDraftColorMix(
                                    colorMix.withChannel(
                                        channel, colorMix.hue[channel], colorMix.sat[channel], v
                                    )
                                )
                            }, onCommit
                        )
                    }
                }
            }
            AdjustTab.BLUR -> {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    ModeChips(
                        options = listOf("None", "Full", "Radial"),
                        selected = when (blur.mode) {
                            BlurMode.FULL -> "Full"
                            BlurMode.RADIAL -> "Radial"
                            BlurMode.NONE -> "None"
                        },
                        onSelect = {
                            onDraftBlur(
                                blur.copy(
                                    mode = when (it) {
                                        "Full" -> BlurMode.FULL
                                        "Radial" -> BlurMode.RADIAL
                                        else -> BlurMode.NONE
                                    }
                                )
                            )
                            onCommit()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ToolSlider(
                        "Amount", blur.amount, 0f..100f,
                        { onDraftBlur(blur.copy(amount = it)) }, onCommit
                    )
                    if (blur.mode == BlurMode.RADIAL) {
                        Text(
                            "Drag the photo to move the blur ellipse; sliders set its shape.",
                            fontSize = 12.sp, color = Color(0xFF9E9E9E),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        ToolSlider(
                            "Feather", blur.feather, 0f..100f,
                            { onDraftBlur(blur.copy(feather = it)) }, onCommit
                        )
                        ToolSlider(
                            "Roundness", blur.roundness, -100f..100f,
                            { onDraftBlur(blur.copy(roundness = it)) }, onCommit
                        )
                        ToolSlider(
                            "Width", blur.rx * 100f, 5f..100f,
                            { onDraftBlur(blur.copy(rx = it / 100f)) }, onCommit,
                            format = { "${it.toInt()}%" }
                        )
                        ToolSlider(
                            "Height", blur.ry * 100f, 5f..100f,
                            { onDraftBlur(blur.copy(ry = it / 100f)) }, onCommit,
                            format = { "${it.toInt()}%" }
                        )
                    }
                }
            }
            AdjustTab.VIGNETTE -> {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    ToolSlider(
                        "Amount", vignette.amount, -100f..100f,
                        { onDraftVignette(vignette.copy(amount = it)) }, onCommit
                    )
                    ToolSlider(
                        "Mid Point", vignette.midPoint, 0f..100f,
                        { onDraftVignette(vignette.copy(midPoint = it)) }, onCommit
                    )
                    ToolSlider(
                        "Feather", vignette.feather, 0f..100f,
                        { onDraftVignette(vignette.copy(feather = it)) }, onCommit
                    )
                    ToolSlider(
                        "Roundness", vignette.roundness, -100f..100f,
                        { onDraftVignette(vignette.copy(roundness = it)) }, onCommit
                    )
                }
            }
            AdjustTab.OPTICS -> {
                // Express itself shows this for photos without a lens
                // profile; we do not invent lens-profile correction.
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Lens profile unavailable", fontSize = 15.sp, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "No lens profile for this photo.",
                        fontSize = 12.sp, color = Color(0xFF9E9E9E)
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Crop (tabs: Aspect Ratio / Rotate / Transform)
// ----------------------------------------------------------------------

@Composable
fun CropPanel(
    aspectSel: AspectSel,
    cropTab: CropTab,
    onCropTab: (CropTab) -> Unit,
    onAspectSelected: (AspectSel) -> Unit,
    onRotate: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onResetCrop: () -> Unit,
    onResetTransform: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(PanelBg)) {
        ModeChips(
            options = listOf("Aspect Ratio", "Rotate", "Transform"),
            selected = when (cropTab) {
                CropTab.ASPECT -> "Aspect Ratio"
                CropTab.ROTATE -> "Rotate"
                CropTab.TRANSFORM -> "Transform"
            },
            onSelect = {
                onCropTab(
                    when (it) {
                        "Rotate" -> CropTab.ROTATE
                        "Transform" -> CropTab.TRANSFORM
                        else -> CropTab.ASPECT
                    }
                )
            }
        )
        when (cropTab) {
            CropTab.ASPECT -> {
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
            }
            CropTab.ROTATE -> {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    CropAction(Icons.Filled.RotateRight, "Rotate", onRotate)
                    CropAction(Icons.Filled.Flip, "Flip H", onFlipH)
                    CropAction(Icons.Filled.Flip, "Flip V", onFlipV, rotateIcon = true)
                    CropAction(Icons.Filled.RestartAlt, "Reset", onResetCrop)
                }
            }
            CropTab.TRANSFORM -> {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        "Drag the four corner handles on the photo to correct perspective (keystone).",
                        fontSize = 13.sp, color = Color(0xFFE0E0E0)
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onResetTransform) {
                        Text("Reset perspective", color = Accent)
                    }
                }
            }
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
