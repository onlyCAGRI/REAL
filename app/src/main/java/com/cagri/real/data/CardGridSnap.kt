package com.cagri.real.data

import kotlin.math.ceil
import kotlin.math.roundToInt

const val CARD_VERTICAL_GRID_DP = 8

/** The dragged block owns this gap. Other blocks keep their individual spacing. */
fun snappedCardGapBefore(startGapBefore: Int, deltaDp: Float): Int {
    val start = startGapBefore.coerceIn(0, 160)
    if (!deltaDp.isFinite()) return start
    val requested = (start + deltaDp).coerceIn(0f, 160f)
    return ((requested / CARD_VERTICAL_GRID_DP).roundToInt() * CARD_VERTICAL_GRID_DP).coerceIn(0, 160)
}

/** Align the next row anchor without ever shrinking the occupied row or overlapping content. */
fun snappedCardRowBottom(bottomPx: Float, stepPx: Float): Float {
    if (!bottomPx.isFinite() || !stepPx.isFinite() || stepPx <= 0f) return bottomPx
    return ceil(bottomPx / stepPx) * stepPx
}
