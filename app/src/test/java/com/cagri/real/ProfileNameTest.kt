package com.cagri.real

import com.cagri.real.auth.profileDisplayName
import org.junit.Assert.*
import org.junit.Test

class ProfileNameTest {
    @Test fun firstNameRequired() {
        for (first in listOf("", " ", "\t\n")) assertTrue(runCatching { profileDisplayName(first, "Gül") }.isFailure)
    }
    @Test fun lastNameOptional() { assertEquals("Çağrı", profileDisplayName(" Çağrı ")) }
    @Test fun fullNameKeepsTurkishCharacters() { assertEquals("Çağrı Gül", profileDisplayName("Çağrı", "Gül")) }
    @Test fun multipleGivenNamesAllowed() { assertEquals("Ali Can Yılmaz", profileDisplayName("Ali Can", "Yılmaz")) }
    @Test fun normalizeWhitespace() { assertEquals("Ali Can Gül", profileDisplayName("  Ali   Can  ", " Gül  ")) }
    @Test fun backendLimitNotSilentlyTruncated() {
        assertEquals(80, profileDisplayName("A".repeat(80)).length)
        assertTrue(runCatching { profileDisplayName("A".repeat(80), "B") }.isFailure)
    }
    @Test fun controlCharactersRejected() { assertTrue(runCatching { profileDisplayName("Ali\u0000") }.isFailure) }
}
