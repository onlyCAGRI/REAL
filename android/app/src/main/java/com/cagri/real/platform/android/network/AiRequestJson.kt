package com.cagri.real.platform.android.network

import com.cagri.real.network.MoodSuggestionRequest
import com.cagri.real.network.VoiceIntentRequest
import org.json.JSONArray
import org.json.JSONObject

internal fun MoodSuggestionRequest.toJson(): String = JSONObject().apply {
    put("source", source)
    put("mood", mood)
    put("descriptors", JSONArray().apply { descriptors.forEach { put(it) } })
    put("factors", JSONArray().apply { factors.forEach { put(it) } })
    put("excludeMusic", JSONArray().apply { normalizedMusicExclusions().forEach { put(it) } })
    if (replaceKind != null) {
        put("replaceKind", replaceKind.name)
        put("exclude", JSONArray().apply { normalizedExclusions().forEach { put(it) } })
    }
}.toString()

internal fun VoiceIntentRequest.toJson(): String = JSONObject().apply {
    put("source", source)
    put("maxCreationItems", maxCreationItems.coerceIn(1, 20))
    put("transcript", normalizedTranscript())
    put("transcriptAlternatives", JSONArray().apply { normalizedAlternatives().forEach { put(it) } })
    put("now", nowIsoOffset)
    put("timezone", timezone)
    put("locale", locale)
    put("categories", JSONArray().apply { limitedCategories().forEach { put(it) } })
    val cards = limitedRecentCards()
    if (cards.isNotEmpty()) {
        put("recentCards", JSONArray().apply {
            cards.forEach { card ->
                put(JSONObject().apply {
                    put("id", card.id.toString())
                    put("title", card.title)
                    put("alertAt", card.alertAtIso.orEmpty())
                    put("plannedDate", card.plannedDate.orEmpty())
                    put("categoryName", card.categoryName.orEmpty())
                    // Saved location context is intentionally omitted.
                    put("repeat", card.repeatSummary.orEmpty())
                    put("isChecklist", card.isChecklist)
                    put("completed", card.completed)
                    put("isLast", card.isLast)
                })
            }
        })
    } else if (lastCard != null) {
        put("lastCard", JSONObject().apply {
            put("title", lastCard.title)
            put("alertAt", lastCard.alertAtIso.orEmpty())
            put("plannedDate", lastCard.plannedDate.orEmpty())
            put("categoryName", lastCard.categoryName.orEmpty())
            // New locations spoken by the user still remain in the transcript.
            put("repeat", lastCard.repeatSummary.orEmpty())
        })
    }
}.toString()
