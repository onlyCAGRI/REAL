package com.cagri.real.data

import kotlin.math.abs

enum class CatalogCardDisplay { FULL, COVER_TITLE, COVER_ONLY }

/** Existing detail width ladder: full -> first reduction -> title -> cover. */
fun catalogCardDisplay(span: Int): CatalogCardDisplay = when {
    span >= 4 -> CatalogCardDisplay.FULL
    span == 3 -> CatalogCardDisplay.COVER_TITLE
    else -> CatalogCardDisplay.COVER_ONLY
}

fun catalogCardWidthStep(requested: Int): Int = listOf(2, 3, 4, 6).minBy { abs(it - requested) }

fun catalogActionsHiddenAfterResize(wasHidden: Boolean, deltaDp: Float): Boolean = when {
    !deltaDp.isFinite() -> wasHidden
    deltaDp <= -8f -> true
    deltaDp >= 8f -> false
    else -> wasHidden
}
