package com.cagri.real

import com.cagri.real.share.validatedPublicShareUrl
import org.junit.Assert.*
import org.junit.Test

class PublicShareUrlTest {
    private val id = "11111111-1111-4111-8111-111111111111"
    @Test fun publicHostIsIndependentOfPrivateApi() {
        for (host in listOf("chosen-project.pages.dev", "share.example.test", "legacy-account.workers.dev")) {
            val url = "https://$host/s/$id"
            assertEquals(url, validatedPublicShareUrl(id, url))
        }
    }
    @Test fun onlyExactHttpsCapabilityUrlIsAccepted() {
        for (url in listOf("http://share.example.test/s/$id", "https://user:password@share.example.test/s/$id",
            "https://share.example.test:8443/s/$id", "https://share.example.test/api/share/v1/shares/$id",
            "https://share.example.test/s/$id/assets/$id", "https://share.example.test/s/$id?redirect=evil",
            "https://share.example.test/s/$id#evil", "https://share.example.test/s/%2e%2e/$id",
            "https://share.example.test/s/$id/", "real://shared/$id", "not a URL")) {
            assertNull(url, validatedPublicShareUrl(id, url))
        }
        assertNull(validatedPublicShareUrl("invalid", "https://share.example.test/s/invalid"))
        assertNull(validatedPublicShareUrl(id, "https://share.example.test/s/22222222-2222-4222-8222-222222222222"))
    }
    @Test fun explicitStandardHttpsPortRemainsCompatible() {
        val url = "https://share.example.test:443/s/$id"
        assertEquals(url, validatedPublicShareUrl(id, url))
    }
}
