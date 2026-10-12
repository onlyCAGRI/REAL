package com.cagri.real

import com.cagri.real.speech.VoiceDraft
import com.cagri.real.speech.VoiceTranscriptFormatter
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
        assertEquals("Toplantı yarın saat beşte", draft.committed)
        assertEquals("hatırlat", draft.partial)
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
        
        val full = "yarın saat beşte annemi arıcam ve marketten süt alıcam"

        
        val (c2, p2) = VoiceTranscriptFormatter.splitCommittedAndPartial(
            full,
            "yarın saat beşte annemi arıcam",
            "ve marketten süt alıcam"
        )
        assertEquals("yarın saat beşte annemi arıcam", c2)
        assertEquals("ve marketten süt alıcam", p2)

        
        val (c3, p3) = VoiceTranscriptFormatter.splitCommittedAndPartial(full, "", full)
        assertEquals("", c3)
        assertEquals(full, p3)
    }

    @Test
    fun voiceTranscriptFormatterWhenFinalized() {
        val full = "yarın saat 5te annemi ara"
        val (c, p) = VoiceTranscriptFormatter.splitCommittedAndPartial(full, full, "")
        assertEquals("yarın saat 5te annemi ara", c)
        assertEquals("", p)
    }

    @Test
    fun longerPartialNeverOverridesAuthoritativeFinal() {
        val draft = VoiceDraft("")
        draft.updatePartial("yarın saat beşte annemi")
        
        draft.commitResult("yarın saat beşte")
        assertEquals("yarın saat beşte", draft.committed)
        assertEquals("", draft.partial)
    }

    @Test
    fun finalRicherThanPartialFinalWins() {
        val draft = VoiceDraft("")
        draft.updatePartial("yarın saat")
        
        draft.commitResult("yarın saat beşte")
        assertEquals("yarın saat beşte", draft.committed)
        assertEquals("", draft.partial)
    }

    @Test
    fun unrelatedPartialAndFinalDoesNotUnsafelyConcatenate() {
        val draft = VoiceDraft("")
        draft.updatePartial("toplantı iptal")
        
        draft.commitResult("doktora git")
        assertEquals("doktora git", draft.committed)
        assertEquals("", draft.partial)
    }
}
