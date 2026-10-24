package com.shawnrodis.photocraftexpress

import com.shawnrodis.photocraftexpress.engine.Adjustments
import com.shawnrodis.photocraftexpress.engine.EditHistory
import com.shawnrodis.photocraftexpress.engine.EditState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {

    private fun state(exposure: Float) = EditState(adj = Adjustments(exposure = exposure))

    @Test
    fun commitUndoRedo_roundTrip() {
        val h = EditHistory(state(0f))
        assertFalse(h.canUndo)
        h.commit(state(10f))
        h.commit(state(20f))
        assertTrue(h.canUndo)
        assertEquals(20f, h.current.adj.exposure, 1e-6f)
        assertEquals(10f, h.undo()!!.adj.exposure, 1e-6f)
        assertEquals(0f, h.undo()!!.adj.exposure, 1e-6f)
        assertNull(h.undo())
        assertTrue(h.canRedo)
        assertEquals(10f, h.redo()!!.adj.exposure, 1e-6f)
        assertEquals(20f, h.redo()!!.adj.exposure, 1e-6f)
        assertNull(h.redo())
    }

    @Test
    fun commitAfterUndo_clearsRedo() {
        val h = EditHistory(state(0f))
        h.commit(state(10f))
        h.undo()
        h.commit(state(99f))
        assertFalse(h.canRedo)
        assertEquals(99f, h.current.adj.exposure, 1e-6f)
    }

    @Test
    fun commitSameState_isNoOp() {
        val h = EditHistory(state(0f))
        h.commit(state(0f))
        assertFalse(h.canUndo)
        assertEquals(0, h.undoDepth)
    }

    @Test
    fun history_isCapped() {
        val h = EditHistory(state(0f), cap = 5)
        for (i in 1..12) h.commit(state(i.toFloat()))
        assertEquals(5, h.undoDepth)
        assertEquals(12f, h.current.adj.exposure, 1e-6f)
    }
}
