package com.cagri.real.ui.components

import kotlin.math.roundToInt

/** Left-edge travel interval. Narrow surfaces keep the cat centred with no unsafe range. */
internal fun realPetPerchRange(
    left: Float,
    right: Float,
    petWidth: Float,
    screenMinX: Float,
    screenMaxX: Float
): ClosedFloatingPointRange<Float> {
    val start = maxOf(left, screenMinX)
    val end = minOf(right - petWidth, screenMaxX)
    if (end >= start) return start..end
    val centre = ((left + right - petWidth) / 2f).coerceIn(screenMinX, screenMaxX)
    return centre..centre
}

internal fun realPetTravelDuration(distanceDp: Float, running: Boolean): Int {
    val speedDpPerSecond = if (running) 100f else 42f
    return (distanceDp / speedDpPerSecond * 1_000f).roundToInt().coerceAtLeast(250)
}

/**
 * Pure movement/presence helpers shared by the scenario-driven visitor loop.
 * Scenario selection lives in RealPetLayer; these helpers only calculate timing/ranges.
 */

/** Duration for a natural visit before the cat trots offscreen (30-60s). */
internal fun realPetVisitDurationMs(factor: Float): Long =
    (30_000L + (factor.coerceIn(0f, 1f) * 30_000L).toLong()).coerceIn(30_000L, 60_000L)

/**
 * Guest-mode launch plan. Half of normal sessions have no casual visitor.
 * When a visit exists it starts immediately, within ~15s, or within ~30s.
 * The old 30-60s arrival bucket was intentionally removed because it felt too late.
 */
internal fun realPetGuestArrivalDelayMs(roll: Float, jitter: Float): Long? {
    val r = roll.coerceIn(0f, 0.9999f)
    val j = jitter.coerceIn(0f, 1f)
    return when {
        r < 0.12f -> 0L
        r < 0.32f -> 5_000L + (j * 10_000L).toLong()
        r < 0.50f -> 15_000L + (j * 15_000L).toLong()
        else -> null
    }
}

/** Chance for a hit-and-run prank when user is typing while cat was off-screen (25%). */
internal fun realPetHitAndRunPrankChance(roll: Float, chance: Float = 0.25f): Boolean =
    roll < chance

