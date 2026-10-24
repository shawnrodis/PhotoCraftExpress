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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    modifier: Modifier = Modifier,
    state: LazyListState? = null
) {
    val listState = state ?: rememberLazyListState()
    LazyRow(
        state = listState,
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

/**
 * One parameter of a multi-parameter tab (Vignette, Split Tone, Blur).
 * Express's pattern: icon chips pick the parameter, ONE shared slider
 * controls the selected parameter — not a stack of labeled sliders.
 * Kept as top-level data so the parameter sets (names, ranges) are
 * unit-testable without composing anything.
 */
data class TabParam<T>(
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val get: (T) -> Float,
    val set: (T, Float) -> T,
    val format: (Float) -> String = ::formatSliderValue
)

/** Vignette parameters in Express's chip order. Amount keeps its +100..-100 range. */
val VIGNETTE_PARAMS = listOf(
    TabParam<Vignette>("Amount", -100f..100f, { it.amount }, { v, x -> v.copy(amount = x) }),
    TabParam<Vignette>("Mid Point", 0f..100f, { it.midPoint }, { v, x -> v.copy(midPoint = x) }),
    TabParam<Vignette>("Feather", 0f..100f, { it.feather }, { v, x -> v.copy(feather = x) }),
    TabParam<Vignette>("Roundness", -100f..100f, { it.roundness }, { v, x -> v.copy(roundness = x) })
)

/** Split Tone parameters in Express's chip order (shadow pair, highlight pair). */
val SPLIT_TONE_PARAMS = listOf(
    TabParam<SplitTone>("Shadow Hue", 0f..360f, { it.shadowHue }, { s, x -> s.copy(shadowHue = x) }, { "${it.toInt()}°" }),
    TabParam<SplitTone>("Shadow Saturation", 0f..100f, { it.shadowSat }, { s, x -> s.copy(shadowSat = x) }),
    TabParam<SplitTone>("Highlight Hue", 0f..360f, { it.highlightHue }, { s, x -> s.copy(highlightHue = x) }, { "${it.toInt()}°" }),
    TabParam<SplitTone>("Highlight Saturation", 0f..100f, { it.highlightSat }, { s, x -> s.copy(highlightSat = x) })
)

/** Blur parameters. Width/Height only apply to the Radial ellipse. */
val BLUR_PARAMS = listOf(
    TabParam<BlurFx>("Amount", 0f..100f, { it.amount }, { b, x -> b.copy(amount = x) }),
    TabParam<BlurFx>("Feather", 0f..100f, { it.feather }, { b, x -> b.copy(feather = x) }),
    TabParam<BlurFx>("Roundness", -100f..100f, { it.roundness }, { b, x -> b.copy(roundness = x) }),
    TabParam<BlurFx>("Width", 5f..100f, { it.rx * 100f }, { b, x -> b.copy(rx = x / 100f) }, { "${it.toInt()}%" }),
    TabParam<BlurFx>("Height", 5f..100f, { it.ry * 100f }, { b, x -> b.copy(ry = x / 100f) }, { "${it.toInt()}%" })
)

/** Small hand-drawn parameter icons (house style: simple geometry, no Adobe art). */
private fun paramIcon(name: String, paths: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(paths).build()

private fun ImageVector.Builder.filledPath(data: List<PathNode>) {
    addPath(data, pathFillType = PathFillType.NonZero, fill = SolidColor(Color.White))
}

private fun ImageVector.Builder.strokedPath(data: List<PathNode>, width: Float = 1.6f) {
    addPath(
        data, pathFillType = PathFillType.NonZero,
        fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.White),
        strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
    )
}

/** Teardrop: Amount (vignette strength / blur amount). */
private val AmountIcon: ImageVector = paramIcon("Amount") {
    filledPath(
        listOf(
            PathNode.MoveTo(12f, 2f),
            PathNode.CurveTo(12f, 2f, 5f, 10f, 5f, 15f),
            PathNode.ArcTo(7f, 7f, 0f, false, true, 19f, 15f),
            PathNode.CurveTo(19f, 10f, 12f, 2f, 12f, 2f),
            PathNode.Close
        )
    )
}

/** Target: Mid Point (where the vignette falloff centres). */
private val MidPointIcon: ImageVector = paramIcon("MidPoint") {
    strokedPath(
        listOf(
            PathNode.MoveTo(4f, 12f),
            PathNode.ArcTo(8f, 8f, 0f, true, false, 20f, 12f),
            PathNode.ArcTo(8f, 8f, 0f, true, false, 4f, 12f),
            PathNode.Close
        )
    )
    strokedPath(
        listOf(
            PathNode.MoveTo(8.5f, 12f),
            PathNode.ArcTo(3.5f, 3.5f, 0f, true, false, 15.5f, 12f),
            PathNode.ArcTo(3.5f, 3.5f, 0f, true, false, 8.5f, 12f),
            PathNode.Close
        )
    )
    filledPath(
        listOf(
            PathNode.MoveTo(10.5f, 12f),
            PathNode.ArcTo(1.5f, 1.5f, 0f, true, false, 13.5f, 12f),
            PathNode.ArcTo(1.5f, 1.5f, 0f, true, false, 10.5f, 12f),
            PathNode.Close
        )
    )
}

/** Nested squares: Feather (softness of an edge). */
private val FeatherIcon: ImageVector = paramIcon("Feather") {
    strokedPath(
        listOf(
            PathNode.MoveTo(4f, 4f), PathNode.LineTo(20f, 4f),
            PathNode.LineTo(20f, 20f), PathNode.LineTo(4f, 20f), PathNode.Close
        )
    )
    strokedPath(
        listOf(
            PathNode.MoveTo(8f, 8f), PathNode.LineTo(16f, 8f),
            PathNode.LineTo(16f, 16f), PathNode.LineTo(8f, 16f), PathNode.Close
        )
    )
}

/** Corner arcs: Roundness (how circular the falloff/ellipse is). */
private val RoundnessIcon: ImageVector = paramIcon("Roundness") {
    strokedPath(
        listOf(
            PathNode.MoveTo(4f, 20f), PathNode.LineTo(4f, 10f),
            PathNode.QuadTo(4f, 4f, 10f, 4f), PathNode.LineTo(20f, 4f)
        )
    )
    strokedPath(
        listOf(
            PathNode.MoveTo(8f, 20f), PathNode.LineTo(8f, 13f),
            PathNode.QuadTo(8f, 8f, 13f, 8f), PathNode.LineTo(20f, 8f)
        )
    )
}

/** Horizontal double arrow: Width (ellipse horizontal radius). */
private val WidthIcon: ImageVector = paramIcon("Width") {
    strokedPath(
        listOf(
            PathNode.MoveTo(3f, 12f), PathNode.LineTo(21f, 12f),
            PathNode.MoveTo(3f, 12f), PathNode.LineTo(7f, 8f),
            PathNode.MoveTo(3f, 12f), PathNode.LineTo(7f, 16f),
            PathNode.MoveTo(21f, 12f), PathNode.LineTo(17f, 8f),
            PathNode.MoveTo(21f, 12f), PathNode.LineTo(17f, 16f)
        )
    )
}

/** Vertical double arrow: Height (ellipse vertical radius). */
private val HeightIcon: ImageVector = paramIcon("Height") {
    strokedPath(
        listOf(
            PathNode.MoveTo(12f, 3f), PathNode.LineTo(12f, 21f),
            PathNode.MoveTo(12f, 3f), PathNode.LineTo(8f, 7f),
            PathNode.MoveTo(12f, 3f), PathNode.LineTo(16f, 7f),
            PathNode.MoveTo(12f, 21f), PathNode.LineTo(8f, 17f),
            PathNode.MoveTo(12f, 21f), PathNode.LineTo(16f, 17f)
        )
    )
}

private fun vignetteIconFor(label: String): ImageVector = when (label) {
    "Amount" -> AmountIcon
    "Mid Point" -> MidPointIcon
    "Feather" -> FeatherIcon
    else -> RoundnessIcon
}

private fun blurIconFor(label: String): ImageVector = when (label) {
    "Amount" -> AmountIcon
    "Feather" -> FeatherIcon
    "Roundness" -> RoundnessIcon
    "Width" -> WidthIcon
    else -> HeightIcon
}

/** One icon chip: small icon (or colour dot) above a label, highlighted when selected. */
data class ParamOption(
    val label: String,
    val icon: ImageVector? = null,
    val dot: Color? = null
)

/**
 * Express's parameter picker: a row of icon chips above one shared slider.
 * Chips are content-sized and the row scrolls, so long labels never wrap
 * and every parameter stays reachable.
 */
@Composable
fun ParamChips(
    options: List<ParamOption>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(options, key = { it.label }) { opt ->
            val sel = opt.label == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onSelect(opt.label) }
                    .padding(vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) Color(0xFF1E3A5F) else Color(0xFF2A2A2A))
                        .border(
                            width = if (sel) 2.dp else 0.dp,
                            color = if (sel) Accent else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        opt.icon != null -> Icon(
                            opt.icon, null,
                            tint = if (sel) Accent else Color(0xFFBDBDBD),
                            modifier = Modifier.size(22.dp)
                        )
                        opt.dot != null -> Box(
                            Modifier.size(18.dp).clip(CircleShape).background(opt.dot)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    opt.label, fontSize = 11.sp, maxLines = 1,
                    color = if (sel) Color.White else Color(0xFFBDBDBD),
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/** HSV (full saturation/value) to Compose colour, for Color Mix channel dots. */
fun channelDotColor(hueCentre: Float): Color {
    val h = ((hueCentre % 360f) + 360f) % 360f / 60f
    val i = h.toInt()
    val f = h - i
    val (r, g, b) = when (i) {
        0 -> Triple(1f, f, 0f)
        1 -> Triple(1f - f, 1f, 0f)
        2 -> Triple(0f, 1f, f)
        3 -> Triple(0f, 1f - f, 1f)
        4 -> Triple(f, 0f, 1f)
        else -> Triple(1f, 0f, 1f - f)
    }
    return Color(0.1f + 0.8f * r, 0.1f + 0.8f * g, 0.1f + 0.8f * b)
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

/** One entry of the continuous Looks strip: preset + its category. */
data class LooksStripEntry(val id: String, val name: String, val category: String)

/**
 * Flat Looks strip: every preset exactly once, categories in CATEGORIES
 * order, presets in their within-category order. This is the structure
 * the catalog expansion slots into: new categories/presets just extend
 * CATEGORIES and the strip, the tabs and the tracking all follow.
 */
fun looksStripEntries(): List<LooksStripEntry> =
    Looks.CATEGORIES.flatMap { (category, looks) ->
        looks.map { LooksStripEntry(it.id, it.name, category) }
    }

/** Category of the strip item at [index] (clamped); "" when the strip is empty. */
fun looksStripCategoryAt(entries: List<LooksStripEntry>, index: Int): String {
    if (entries.isEmpty()) return ""
    return entries[index.coerceIn(0, entries.size - 1)].category
}

/** First strip index of [category], or 0 when the category is absent. */
fun looksStripStartOf(entries: List<LooksStripEntry>, category: String): Int =
    entries.indexOfFirst { it.category == category }.coerceAtLeast(0)

@Composable
fun LooksPanel(
    thumbnails: Map<String, Bitmap>,
    selectedLookId: String,
    intensity: Float,
    onSelectLook: (String) -> Unit,
    onIntensityDraft: (Float) -> Unit,
    onIntensityCommit: () -> Unit,
    modifier: Modifier = Modifier,
    onVisibleLooks: (List<String>) -> Unit = {}
) {
    // v0.2.2: Express's Looks is ONE continuous strip, not per-category
    // strips. Scrolling flows through categories in order; the tab row
    // auto-tracks the strip position, and tapping a tab jumps the strip
    // to that category. The v0.2.0/v0.2.1 per-category filter (and its
    // snap-back fix) is gone: with a single strip, no category can be
    // hidden by a filter, so there is nothing to snap back from.
    val entries = remember { looksStripEntries() }
    val stripState = rememberLazyListState()
    val tabState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var activeCategory by remember {
        mutableStateOf(entries.firstOrNull()?.category ?: "Normal")
    }
    // Track: strip scroll position -> highlighted tab.
    LaunchedEffect(stripState) {
        snapshotFlow { stripState.firstVisibleItemIndex }.collect { idx ->
            val cat = looksStripCategoryAt(entries, idx)
            if (cat.isNotEmpty() && cat != activeCategory) activeCategory = cat
        }
    }
    // Keep the active tab visible in the tab row.
    LaunchedEffect(activeCategory) {
        val tabIdx = Looks.CATEGORIES.indexOfFirst { it.first == activeCategory }.coerceAtLeast(0)
        if (tabState.firstVisibleItemIndex != tabIdx) tabState.animateScrollToItem(tabIdx)
    }
    // Lazy thumbnails: report visible strip items (plus immediate
    // neighbours for fling-ahead) so only those render, cached.
    LaunchedEffect(stripState) {
        snapshotFlow { stripState.layoutInfo.visibleItemsInfo.map { it.index } }
            .collect { indices ->
                if (indices.isEmpty()) return@collect
                val ids = indices.flatMap { listOf(it - 1, it, it + 1) }
                    .filter { it in entries.indices }
                    .map { entries[it].id }
                    .distinct()
                onVisibleLooks(ids)
            }
    }
    Column(modifier = modifier.background(PanelBg)) {
        ModeChips(
            options = Looks.CATEGORIES.map { it.first },
            selected = activeCategory,
            onSelect = { name ->
                activeCategory = name
                scope.launch { stripState.animateScrollToItem(looksStripStartOf(entries, name)) }
            },
            modifier = Modifier.fillMaxWidth(),
            state = tabState
        )
        LazyRow(
            state = stripState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(entries, key = { it.id }) { entry ->
                val selected = entry.id == selectedLookId
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(76.dp)
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
                            .clickable {
                                onSelectLook(entry.id)
                                activeCategory = entry.category
                            }
                    ) {
                        thumbnails[entry.id]?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = entry.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        entry.name,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
 * Five adjustments read as identical to the base at 50 on a thumbnail
 * (v0.2.2 field report + render test), so they demo stronger — this is
 * the thumbnail render only, never the slider default.
 */
fun adjustThumbStates(): List<Pair<String, Adjustments>> =
    ADJUST_STRIP_ORDER.mapNotNull { name ->
        ADJUST_SLIDER_SPECS.firstOrNull { it.name == name }?.let { spec ->
            val demo = when (name) {
                "Clarity", "Whites", "Blacks" -> 80f
                "Reduce Luminance Noise" -> 90f
                "Reduce Color Noise" -> 100f
                else -> 50f
            }
            name to spec.set(Adjustments(), demo)
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
                // v0.2.2 strip fix: Express's row is clean because
                // every item is the SAME geometry. Ours was ragged:
                // item width followed the label text, so long labels
                // ("Reduce Color Noise") wrapped to two lines, making
                // items uneven heights and uneven gaps — which also
                // made the fling feel stiffer (variable item widths
                // make fling distance inconsistent). Fixed 84dp-wide
                // items, one 10dp gutter, single-line labels with
                // ellipsis, thumbs on one baseline. The row itself
                // was already a LazyRow with native spline fling; the
                // ragged geometry, not the container, was the problem.
                val stripState = rememberLazyListState()
                LazyRow(
                    state = stripState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(stripSpecs, key = { it.name }) { spec ->
                        val selected = spec.name == selectedSpec?.name
                        val value = spec.get(adj)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(84.dp).clickable { selectedSpec = spec }
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
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (selected) Color.White else Color(0xFFBDBDBD),
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                formatSliderValue(value),
                                fontSize = 11.sp,
                                maxLines = 1,
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
                // Express pattern: icon chips pick the parameter, one
                // shared slider controls it (was four stacked sliders).
                var param by remember { mutableStateOf(SPLIT_TONE_PARAMS[0]) }
                Column {
                    ParamChips(
                        options = SPLIT_TONE_PARAMS.map {
                            ParamOption(
                                it.label,
                                dot = if (it.label.startsWith("Shadow")) Color(0xFF37474F)
                                else Color(0xFFFFB74D)
                            )
                        },
                        selected = param.label,
                        onSelect = { name ->
                            SPLIT_TONE_PARAMS.firstOrNull { it.label == name }?.let { param = it }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        ToolSlider(
                            param.label, param.get(splitTone), param.range,
                            { onDraftSplitTone(param.set(splitTone, it)) }, onCommit,
                            format = param.format
                        )
                    }
                }
            }
            AdjustTab.COLOR_MIX -> {
                // Express pattern: colour-dot chips pick the channel,
                // parameter chips pick Hue/Saturation/Luminance, one
                // shared slider controls the (channel, parameter) pair
                // (was channel chips + three stacked sliders).
                var channel by remember { mutableStateOf(0) }
                var mixParam by remember { mutableStateOf("Hue") }
                Column {
                    ParamChips(
                        options = ColorMath.MIX_CHANNELS.map {
                            ParamOption(it.first, dot = channelDotColor(it.second.centre))
                        },
                        selected = ColorMath.MIX_CHANNELS[channel].first,
                        onSelect = { name ->
                            channel = ColorMath.MIX_CHANNELS.indexOfFirst { it.first == name }.coerceAtLeast(0)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ModeChips(
                        options = listOf("Hue", "Saturation", "Luminance"),
                        selected = mixParam,
                        onSelect = { mixParam = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        val cur = when (mixParam) {
                            "Saturation" -> colorMix.sat[channel]
                            "Luminance" -> colorMix.lum[channel]
                            else -> colorMix.hue[channel]
                        }
                        ToolSlider(
                            mixParam, cur, -100f..100f,
                            { v ->
                                onDraftColorMix(
                                    colorMix.withChannel(
                                        channel,
                                        if (mixParam == "Hue") v else colorMix.hue[channel],
                                        if (mixParam == "Saturation") v else colorMix.sat[channel],
                                        if (mixParam == "Luminance") v else colorMix.lum[channel]
                                    )
                                )
                            }, onCommit
                        )
                    }
                }
            }
            AdjustTab.BLUR -> {
                // Mode chips (None/Full/Radial) stay; the parameters
                // become icon chips + one shared slider (was stacked
                // sliders). Width/Height only apply to the Radial ellipse.
                var param by remember { mutableStateOf(BLUR_PARAMS[0]) }
                val visibleParams =
                    if (blur.mode == BlurMode.RADIAL) BLUR_PARAMS else listOf(BLUR_PARAMS[0])
                val shownParam =
                    visibleParams.firstOrNull { it.label == param.label } ?: BLUR_PARAMS[0]
                Column {
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
                    if (blur.mode == BlurMode.RADIAL) {
                        Text(
                            "Drag the photo to move the blur ellipse; sliders set its shape.",
                            fontSize = 12.sp, color = Color(0xFF9E9E9E),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }
                    ParamChips(
                        options = visibleParams.map {
                            ParamOption(it.label, icon = blurIconFor(it.label))
                        },
                        selected = shownParam.label,
                        onSelect = { name ->
                            BLUR_PARAMS.firstOrNull { it.label == name }?.let { param = it }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        ToolSlider(
                            shownParam.label, shownParam.get(blur), shownParam.range,
                            { onDraftBlur(shownParam.set(blur, it)) }, onCommit,
                            format = shownParam.format
                        )
                    }
                }
            }
            AdjustTab.VIGNETTE -> {
                // Express pattern: four icon chips (Amount, Mid Point,
                // Feather, Roundness) above ONE shared slider (was four
                // stacked labeled sliders). Amount keeps +100..-100.
                var param by remember { mutableStateOf(VIGNETTE_PARAMS[0]) }
                Column {
                    ParamChips(
                        options = VIGNETTE_PARAMS.map {
                            ParamOption(it.label, icon = vignetteIconFor(it.label))
                        },
                        selected = param.label,
                        onSelect = { name ->
                            VIGNETTE_PARAMS.firstOrNull { it.label == name }?.let { param = it }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        ToolSlider(
                            param.label, param.get(vignette), param.range,
                            { onDraftVignette(param.set(vignette, it)) }, onCommit,
                            format = param.format
                        )
                    }
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
