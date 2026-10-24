package com.shawnrodis.photocraftexpress.ui

import com.shawnrodis.photocraftexpress.engine.EditState
import kotlinx.coroutines.flow.Flow

/**
 * One preview render request: everything a preview render needs, captured
 * atomically at offer time so the worker never mixes a new slider value
 * with stale analysis or tool state.
 */
data class PreviewRequest(
    val state: EditState,
    val analysis: FaceAnalysis.Result?,
    val insertedBitmaps: Map<String, android.graphics.Bitmap>,
    val applyCrop: Boolean,
    val tool: EditorTool
)

/**
 * Sequential latest-wins render loop (v0.2.2 live-slider fix).
 *
 * The v0.2.1 approach — `LaunchedEffect(cur)` with cooperative
 * cancellation — could never update the canvas during a drag: drag ticks
 * arrive faster than one render completes, so every render was cancelled
 * before it finished and only the release value ever surfaced. Collecting
 * sequentially instead means a render always runs to completion, and the
 * caller feeds this loop through a conflated channel/buffer (only the
 * latest value survives), so the published bitmaps advance at render
 * rate throughout the drag instead of never. Undo history still commits
 * once, on release — this loop only affects what the canvas shows.
 */
suspend fun <T, R> renderLatest(
    requests: Flow<T>,
    render: suspend (T) -> R,
    publish: (R) -> Unit
) {
    requests.collect { req ->
        publish(render(req))
    }
}
