package com.shawnrodis.photocraftexpress.engine

/**
 * Which region the Adjustments-panel edits apply to, mirroring Express's
 * mask selector. FULL is the whole image (the historical behavior).
 * SUBJECT / BACKGROUND use the ML Kit person mask when a person was
 * detected; with no person detected, SUBJECT selects nothing (edits
 * have no visible effect) and BACKGROUND selects everything.
 */
enum class AdjustMask { FULL, SUBJECT, BACKGROUND }
