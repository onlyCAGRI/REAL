package com.cagri.real.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoClockReplyTest {
    @Test fun noParticularTimeAnswersSaveTheDayOnly() {
        listOf("bilmiyorum", "Bilmiyorum ki", "belirsiz", "fark etmez", "farketmez", "belli değil", "saat yok",
            "sonra bakarım", "I don't know").forEach { assertTrue(it, isNoClockReply(it)) }
    }

    @Test fun realClocksAreNotMistakenForNoClock() {
        listOf("sekizde", "akşam 8", "saat 9 buçuk", "20:30", "öğlen").forEach { assertFalse(it, isNoClockReply(it)) }
    }
}
