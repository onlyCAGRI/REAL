package com.cagri.real.data

import kotlin.math.roundToInt
import kotlin.math.ceil

const val CARD_MAX_TOP_DP = 16000

data class CardMeasuredBox(val left: Float, val top: Float, val width: Float, val height: Float)

/** Layout-local, full geometry: never clipped to the scroll viewport or transformed by drag animation. */
fun cardGridMeasuredBoxes(
    keys: List<String>, placements: Map<String, CardBlockPlacement>,
    sizesDp: Map<String, Pair<Float, Float>>, unitDp: Float
): Map<String, CardMeasuredBox> {
    val result = linkedMapOf<String, CardMeasuredBox>()
    var bottom = 0f
    fun place(key: String, top: Float) {
        val p = (placements[key] ?: CardBlockPlacement()).normalized()
        val size = sizesDp.getValue(key)
        result[key] = CardMeasuredBox(p.column * unitDp, top, size.first, size.second)
    }
    for (key in keys.filter { placements[it]?.top != null }) {
        val top = placements.getValue(key).normalized().top!!.toFloat()
        place(key, top)
        bottom = maxOf(bottom, top + sizesDp.getValue(key).second)
    }
    var anchor = ceil(bottom / CARD_VERTICAL_GRID_DP) * CARD_VERTICAL_GRID_DP
    for (row in cardContentRows(keys.filter { placements[it]?.top == null }, placements)) {
        var rowBottom = anchor
        for (key in row) {
            val top = anchor + (placements[key]?.normalized() ?: CardBlockPlacement()).gapBefore
            place(key, top)
            rowBottom = maxOf(rowBottom, top + sizesDp.getValue(key).second)
        }
        anchor = ceil(rowBottom / CARD_VERTICAL_GRID_DP) * CARD_VERTICAL_GRID_DP
    }
    return result
}

/** Reserve native controls/peeks, but remove the legacy 390dp floor for an explicitly resized detail stack. */
fun cardStackFrontHeightDp(totalHeight: Float, chromeHeight: Float, peekHeight: Float): Float =
    (totalHeight - chromeHeight.coerceAtLeast(0f) - peekHeight.coerceAtLeast(0f)).coerceIn(80f, 800f)

/** Drop into any empty grid cell, without reordering, resizing, or moving its neighbors. */
fun placeCardInFreeSpace(
    keys: List<String>, placements: Map<String, CardBlockPlacement>,
    boxes: Map<String, CardMeasuredBox>, moving: String,
    leftDp: Float, topDp: Float, unitDp: Float
): Map<String, CardBlockPlacement>? {
    if (moving !in keys || keys.any { it !in boxes } || !leftDp.isFinite() || !topDp.isFinite() ||
        !unitDp.isFinite() || unitDp <= 0f) return null
    if (keys.any { key -> boxes.getValue(key).let {
            !it.left.isFinite() || !it.top.isFinite() || !it.width.isFinite() || !it.height.isFinite() ||
                it.top < 0f || it.top > CARD_MAX_TOP_DP || it.width <= 0f || it.height < 0f
        } }) return null
    val p = (placements[moving] ?: CardBlockPlacement()).normalized()
    val column = (leftDp / unitDp).roundToInt().coerceIn(0, CARD_GRID_UNITS - p.span)
    val top = ((topDp.coerceIn(0f, CARD_MAX_TOP_DP.toFloat()) / CARD_VERTICAL_GRID_DP).roundToInt() * CARD_VERTICAL_GRID_DP)
    val box = boxes.getValue(moving)
    val left = column * unitDp
    val epsilon = 0.5f // tolerate pixel-to-dp measurement rounding, not real overlaps
    if (keys.any { key ->
            if (key == moving) false else boxes.getValue(key).let {
                left < it.left + it.width - epsilon && left + box.width > it.left + epsilon &&
                    top < it.top + it.height - epsilon && top + box.height > it.top + epsilon
            }
        }) return null
    // Freeze existing flowing positions once, so a moved box never pushes the rest of the card.
    return keys.associateWith { key ->
        val old = (placements[key] ?: CardBlockPlacement()).normalized()
        if (key == moving) old.copy(column = column, top = top)
        else if (old.top != null) old else old.copy(top = boxes.getValue(key).top.roundToInt().coerceIn(0, CARD_MAX_TOP_DP))
    }
}

/**
 * Top-edge resize: the block's bottom edge stays where it is while its top moves (snapped to the 8dp grid).
 * Like a free move, flowing neighbours are frozen at their current tops first, so nothing else shifts.
 * Null when the new top would run into a neighbour or leave less than [minHeightDp].
 */
fun cardTopEdgeResize(
    keys: List<String>, placements: Map<String, CardBlockPlacement>,
    boxes: Map<String, CardMeasuredBox>, moving: String, requestedTopDp: Float, minHeightDp: Float
): Map<String, CardBlockPlacement>? {
    val box = boxes[moving] ?: return null
    if (moving !in keys || keys.any { it !in boxes } || !requestedTopDp.isFinite()) return null
    val bottom = box.top + box.height
    val top = ((requestedTopDp / CARD_VERTICAL_GRID_DP).roundToInt() * CARD_VERTICAL_GRID_DP).toFloat()
        .coerceIn(0f, (bottom - minHeightDp).coerceAtLeast(0f))
    val candidate = box.copy(top = top, height = bottom - top)
    if (!cardDetailBoxFits(moving, candidate, boxes)) return null
    return keys.associateWith { key ->
        val old = (placements[key] ?: CardBlockPlacement()).normalized()
        when {
            key == moving -> old.copy(top = top.roundToInt().coerceIn(0, CARD_MAX_TOP_DP), minHeight = (bottom - top).roundToInt())
            old.top != null -> old
            else -> old.copy(top = boxes.getValue(key).top.roundToInt().coerceIn(0, CARD_MAX_TOP_DP))
        }
    }
}
