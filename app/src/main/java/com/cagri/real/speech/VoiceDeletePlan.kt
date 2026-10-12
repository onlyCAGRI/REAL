package com.cagri.real.speech

import com.cagri.real.data.ReminderItem
import com.cagri.real.data.nextDeletableOccurrence

/** No mutation: the ordinary REAL delete prompt owns the user's explicit choice. */
data class VoiceDeletePlan(
    val cardId: Long,
    val title: String,
    val expectedUpdatedAt: Long,
    val occurrenceMillis: Long?
) {
    fun stillMatches(card: ReminderItem?): Boolean = card != null && card.id == cardId &&
        card.updatedAt == expectedUpdatedAt && card.text == title
}

fun voiceDeletePlan(card: ReminderItem, nowMillis: Long): VoiceDeletePlan = VoiceDeletePlan(
    card.id, card.text, card.updatedAt, nextDeletableOccurrence(card, nowMillis)
)
