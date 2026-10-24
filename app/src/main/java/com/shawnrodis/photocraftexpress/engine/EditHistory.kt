package com.shawnrodis.photocraftexpress.engine

/**
 * Undo/redo over immutable [EditState] snapshots. [commit] is called when a
 * gesture finishes (slider release, chip tap, crop drag end) — live previews
 * during a drag mutate a draft state without touching history.
 */
class EditHistory(initial: EditState, private val cap: Int = 60) {

    var current: EditState = initial
        private set

    private val undoStack = ArrayDeque<EditState>()
    private val redoStack = ArrayDeque<EditState>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoDepth: Int get() = undoStack.size

    fun commit(next: EditState) {
        if (next == current) return
        undoStack.addLast(current)
        while (undoStack.size > cap) undoStack.removeFirst()
        redoStack.clear()
        current = next
    }

    fun undo(): EditState? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(current)
        current = undoStack.removeLast()
        return current
    }

    fun redo(): EditState? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(current)
        current = redoStack.removeLast()
        return current
    }

    fun reset(state: EditState) {
        undoStack.clear()
        redoStack.clear()
        current = state
    }
}
