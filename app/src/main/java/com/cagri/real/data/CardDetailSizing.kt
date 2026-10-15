package com.cagri.real.data

import kotlin.math.ceil

/** UI-only constraints. Wire placement format and Pano transforms are unchanged. */
/** [compactKeys]: small widget blocks (counter, market) that can sit two in a row at half width. */
fun cardDetailMinimumSpan(key: String, widthDp: Float, compactKeys: Set<String> = emptySet()): Int {
    val minimumDp = when {
        key in compactKeys -> 150f
        else -> when (key.substringBefore(':')) {
        "text", "checklist", "journal", "voice", "files", "location" -> 224f
        "card_stack" -> 156f
        else -> 88f
        }
    }
    return ceil((minimumDp + 12f) / ((widthDp + 12f) / CARD_GRID_UNITS).coerceAtLeast(1f))
        .toInt().coerceIn(2, CARD_GRID_UNITS)
}

/** Legacy narrow text fields are presented at the same readable floor as new handle edits. */
fun cardDetailReadablePlacements(
    placements: Map<String, CardBlockPlacement>, widthDp: Float, compactKeys: Set<String> = emptySet()
): Map<String, CardBlockPlacement> =
    placements.mapValues { (key, value) ->
        val p = value.normalized()
        val span = maxOf(p.span, cardDetailMinimumSpan(key, widthDp, compactKeys))
        p.copy(span = span, column = p.column.coerceAtMost(CARD_GRID_UNITS - span))
    }

private fun intersects(a: CardMeasuredBox, b: CardMeasuredBox): Boolean =
    a.left < b.left + b.width - .5f && a.left + a.width > b.left + .5f &&
        a.top < b.top + b.height - .5f && a.top + a.height > b.top + .5f

/** Reject a handle step into a neighbor; shrinking remains possible at the content's natural floor. */
fun cardDetailResizeFits(key: String, span: Int, height: Float,
    boxes: Map<String, CardMeasuredBox>, unitDp: Float, gutterDp: Float): Boolean {
    val old = boxes[key] ?: return false
    if (!height.isFinite() || !unitDp.isFinite() || height < 0 || unitDp <= 0) return false
    val candidate = old.copy(width = (span * unitDp - gutterDp).coerceAtLeast(1f), height = height)
    return boxes.none { (other, box) -> other != key && intersects(candidate, box) }
}

/** Any edge resize: the candidate rectangle must stay inside the card and clear of every neighbour. */
fun cardDetailBoxFits(key: String, candidate: CardMeasuredBox, boxes: Map<String, CardMeasuredBox>): Boolean {
    if (listOf(candidate.left, candidate.top, candidate.width, candidate.height).any { !it.isFinite() }) return false
    if (candidate.left < -0.5f || candidate.top < 0f || candidate.width <= 0f || candidate.height <= 0f) return false
    return boxes.none { (other, box) -> other != key && intersects(candidate, box) }
}

/** Legacy layouts and growing text may outgrow their saved slots. Keep surfaces and handles visible.
 * Resolve downward in stable visual order; never resize data, discard text, or overlap neighbors.
 */
fun cardDetailNonOverlappingBoxes(boxes: Map<String, CardMeasuredBox>): Map<String, CardMeasuredBox> {
    val result = linkedMapOf<String, CardMeasuredBox>()
    boxes.entries.sortedBy { it.value.top }.forEach { (key, initial) ->
        var box = initial
        while (true) {
            val collisions = result.values.filter { intersects(box, it) }
            if (collisions.isEmpty()) break
            box = box.copy(top = ceil(collisions.maxOf { it.top + it.height } / CARD_VERTICAL_GRID_DP) * CARD_VERTICAL_GRID_DP)
        }
        result[key] = box
    }
    return result
}

data class CardPhotoTransform(val scale: Float, val x: Float, val y: Float)

fun cardPhotoTransform(scale: Float, x: Float, y: Float, zoom: Float, panX: Float, panY: Float,
    focalX: Float, focalY: Float, width: Float, height: Float): CardPhotoTransform {
    if (listOf(scale, x, y, zoom, panX, panY, focalX, focalY, width, height).any { !it.isFinite() } ||
        scale <= 0 || zoom <= 0 || width <= 0 || height <= 0) return CardPhotoTransform(1f, 0f, 0f)
    val next = (scale * zoom).coerceIn(1f, 4f)
    val ratio = next / scale
    val limitX = width * (next - 1f) / 2f
    val limitY = height * (next - 1f) / 2f
    return CardPhotoTransform(next,
        (x * ratio + focalX * (1f - ratio) + panX).coerceIn(-limitX, limitX),
        (y * ratio + focalY * (1f - ratio) + panY).coerceIn(-limitY, limitY))
}
