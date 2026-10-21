package com.cagri.real.data

/** One resolved move of a card for a bulk day shift. Exactly one of the two targets is set. */
data class VoiceBulkShift(
    val id: Long,
    val alertAt: Long? = null,
    val plannedEpochDay: Long? = null
)
