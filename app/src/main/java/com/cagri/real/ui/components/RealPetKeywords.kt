package com.cagri.real.ui.components

import android.os.SystemClock
import java.util.Locale

/**
 * Deterministic local keyword detection for REAL Pet behaviors.
 *
 * Requirements:
 * - KEDI (case-insensitive, standalone word): live typing triggers a brief pet visit to composer.
 *   Triggered once per appearance in the current typing session with cooldown against spam.
 * - MAMA (case-insensitive, standalone word): after-save hook triggers pet feeding animation.
 * - Deterministic, local only (no Gemini, Cloudflare, or network).
 * - Standalone-word matching avoids false matches on words like "panama" or "kedicik".
 */
object RealPetKeywords {
    private val TR_LOCALE = Locale.forLanguageTag("tr-TR")

    // Live typing state for "kedi"
    private var kediActiveInDraft = false
    private var lastKediTriggerElapsedRealtime = 0L
    private const val KEDI_COOLDOWN_MS = 25_000L

    fun containsStandaloneWord(text: String, word: String): Boolean {
        if (text.isBlank() || word.isBlank()) return false
        val normalizedText = text.lowercase(TR_LOCALE)
        val normalizedWord = word.lowercase(TR_LOCALE)
        val pattern = Regex("""(^|[^\p{L}\d])${Regex.escape(normalizedWord)}($|[^\p{L}\d])""")
        return pattern.containsMatchIn(normalizedText)
    }

    /**
     * Called on each keystroke/change in the Add to REAL composer.
     */
    fun onComposerDraftChanged(newText: String, isKeywordsEnabled: Boolean) {
        if (!isKeywordsEnabled) {
            kediActiveInDraft = false
            return
        }

        val hasKedi = containsStandaloneWord(newText, "kedi")
        if (hasKedi) {
            if (!kediActiveInDraft) {
                kediActiveInDraft = true
                val now = SystemClock.elapsedRealtime()
                if (now - lastKediTriggerElapsedRealtime >= KEDI_COOLDOWN_MS) {
                    lastKediTriggerElapsedRealtime = now
                    RealPetRuntime.requestKediAppearance()
                }
            }
        } else {
            // Word was removed by the user in this typing session
            kediActiveInDraft = false
        }
    }

    /**
     * Called when the composer is cleared, closed, or submitted.
     */
    fun onComposerClosedOrSubmitted() {
        kediActiveInDraft = false
    }

    /**
     * Called AFTER a reminder is successfully persisted to the database.
     */
    fun onReminderSaved(savedText: String, isKeywordsEnabled: Boolean) {
        if (!isKeywordsEnabled) return
        if (containsStandaloneWord(savedText, "mama")) {
            RealPetRuntime.requestMamaFeeding(savedText.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { "Mama" })
        }
    }

    fun resetForTesting() {
        kediActiveInDraft = false
        lastKediTriggerElapsedRealtime = 0L
    }
}
