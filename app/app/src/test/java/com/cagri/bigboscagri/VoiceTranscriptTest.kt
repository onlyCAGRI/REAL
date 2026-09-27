package com.cagri.bigboscagri

import com.cagri.bigboscagri.speech.VoiceDraft
import com.cagri.bigboscagri.speech.VoiceTranscriptFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTranscriptTest {

    @Test
    fun voiceDraftInitialStateAndUpdates() {
        val draft = VoiceDraft("Toplantı")
        assertEquals("Toplantı", draft.committed)
        assertEquals("", draft.partial)
        assertEquals("Toplantı", draft.text)

        draft.updatePartial("yarın saat 5te")
        assertEquals("Toplantı", draft.committed)
        assertEquals("yarın saat 5te", draft.partial)
        assertEquals("Toplantı yarın saat 5te", draft.text)

        draft.commitResult("yarın saat beşte")
        assertEquals("Toplantı yarın saat beşte", draft.committed)
        assertEquals("", draft.partial)
        assertEquals("Toplantı yarın saat beşte", draft.text)

        draft.updatePartial("hatırlat")
        assertEquals("Toplantı yarın saat beşte", draft.committed)
        assertEquals("hatırlat", draft.partial)
        assertEquals("Toplantı yarın saat beşte hatırlat", draft.text)

        draft.commitResult(null)
        assertEquals("Toplantı yarın saat beşte hatırlat", draft.committed)
        assertEquals("", draft.partial)
        assertEquals("Toplantı yarın saat beşte hatırlat", draft.text)
    }

    @Test
    fun voiceTranscriptFormatterWithExplicitCommitted() {
        val full = "Annemi yarın saat beşte ara"
        val committed = "Annemi yarın saat"
        val partial = "beşte ara"

        val (c, p) = VoiceTranscriptFormatter.splitCommittedAndPartial(full, committed, partial)
        assertEquals("Annemi yarın saat", c)
        assertEquals("beşte ara", p)
    }

    @Test
    fun voiceTranscriptFormatterPromptSentence() {
        // User test sentence: "yarın saat beşte annemi arıcam ve marketten süt alıcam"
        val full = "yarın saat beşte annemi arıcam ve marketten süt alıcam"

        // When earlier segment was already committed
        val (c2, p2) = VoiceTranscriptFormatter.splitCommittedAndPartial(
            full,
            "yarın saat beşte annemi arıcam",
            "ve marketten süt alıcam"
        )
        assertEquals("yarın saat beşte annemi arıcam", c2)
        assertEquals("ve marketten süt alıcam", p2)

        // When all text is in partial (continuous recognition)
        val (c3, p3) = VoiceTranscriptFormatter.splitCommittedAndPartial(full, "", full)
        assertEquals("yarın saat beşte annemi arıcam ve marketten", c3)
        assertEquals("süt alıcam", p3)
    }

    @Test
    fun voiceTranscriptFormatterWhenFinalized() {
        val full = "yarın saat 5te annemi ara"
        val (c, p) = VoiceTranscriptFormatter.splitCommittedAndPartial(full, full, "")
        assertEquals("yarın saat 5te annemi ara", c)
        assertEquals("", p)
    }

    @Test
    fun partialLongerThanFinalTrailingWordsNotLost() {
        val draft = VoiceDraft("")
        draft.updatePartial("yarın saat beşte annemi")
        // Final result emitted by recognizer cut off the trailing word "annemi"
        draft.commitResult("yarın saat beşte")
        assertEquals("yarın saat beşte annemi", draft.committed)
        assertEquals("", draft.partial)
    }

    @Test
    fun finalRicherThanPartialFinalWins() {
        val draft = VoiceDraft("")
        draft.updatePartial("yarın saat")
        // Final result has completed additional words
        draft.commitResult("yarın saat beşte")
        assertEquals("yarın saat beşte", draft.committed)
        assertEquals("", draft.partial)
    }

    @Test
    fun unrelatedPartialAndFinalDoesNotUnsafelyConcatenate() {
        val draft = VoiceDraft("")
        draft.updatePartial("toplantı iptal")
        // Completely unrelated final result
        draft.commitResult("doktora git")
        assertEquals("doktora git", draft.committed)
        assertEquals("", draft.partial)
    }
}
