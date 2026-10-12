package com.cagri.real.ui.pano

import com.cagri.real.boards.BoardTransform
import kotlin.math.abs
import kotlin.math.round

internal data class BoardAlignment(
    val transform: BoardTransform,
    val guideX: Float? = null,
    val guideY: Float? = null
)

/** Screen-sized attraction zone; never resize, rotate, reorder or force uniform spacing. */
internal fun alignBoardBlock(
    draft: BoardTransform,
    neighbours: List<BoardTransform>,
    zoom: Float,
    gridEnabled: Boolean
): BoardAlignment {
    if (!zoom.isFinite() || zoom <= 0f || abs(draft.rotation) > 0.01f) return BoardAlignment(draft)
    val threshold = (6f / zoom).coerceAtMost(12f)
    fun axis(position: Float, targets: List<Pair<Float, Float>>): Pair<Float, Float?> {
        // Candidate = desired origin and guide position. Neighbours beat grid on ties.
        val candidates = targets.toMutableList()
        if (gridEnabled) {
            var spacing = 24f
            while (spacing * zoom < 12f) spacing *= 2f
            val grid = round(position / spacing) * spacing
            candidates += grid to grid
        }
        val nearest = candidates.filter { abs(it.first - position) <= threshold }
            .minByOrNull { abs(it.first - position) }
        return if (nearest == null) position to null else nearest.first to nearest.second
    }
    val xTargets = mutableListOf<Pair<Float, Float>>()
    val yTargets = mutableListOf<Pair<Float, Float>>()
    neighbours.filter { abs(it.rotation) <= 0.01f }.forEach { other ->
        // Consider visually nearby neighbours rather than aligning across the whole board.
        val nearY = draft.y < other.y + other.height + 96f && draft.y + draft.height > other.y - 96f
        val nearX = draft.x < other.x + other.width + 96f && draft.x + draft.width > other.x - 96f
        if (nearY) {
            xTargets += other.x to other.x
            xTargets += (other.x + (other.width - draft.width) / 2f) to (other.x + other.width / 2f)
            xTargets += (other.x + other.width - draft.width) to (other.x + other.width)
            xTargets += (other.x + other.width + 12f) to (other.x + other.width + 12f)
            xTargets += (other.x - draft.width - 12f) to (other.x - 12f)
        }
        if (nearX) {
            yTargets += other.y to other.y
            yTargets += (other.y + (other.height - draft.height) / 2f) to (other.y + other.height / 2f)
            yTargets += (other.y + other.height - draft.height) to (other.y + other.height)
            yTargets += (other.y + other.height + 12f) to (other.y + other.height + 12f)
            yTargets += (other.y - draft.height - 12f) to (other.y - 12f)
        }
    }
    val (x, guideX) = axis(draft.x, xTargets)
    val (y, guideY) = axis(draft.y, yTargets)
    return BoardAlignment(draft.copy(x = x, y = y), guideX, guideY)
}
