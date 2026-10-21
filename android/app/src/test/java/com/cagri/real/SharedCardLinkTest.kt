package com.cagri.real

import com.cagri.real.share.sharedCardIdFromLink
import com.cagri.real.share.isSharedCardId
import org.junit.Assert.*
import org.junit.Test

class SharedCardLinkTest {
    private val id = "11111111-1111-4111-8111-111111111111"
    @Test fun exactWebHandoffIsAccepted() { assertEquals(id, sharedCardIdFromLink("real://shared/$id")) }
    @Test fun wrongSchemesAndHostsAreRejected() {
        assertNull(sharedCardIdFromLink("https://shared/$id"))
        assertNull(sharedCardIdFromLink("real://other/$id"))
    }
    @Test fun extraPathAndTraversalAreRejected() {
        assertNull(sharedCardIdFromLink("real://shared/$id/extra"))
        assertNull(sharedCardIdFromLink("real://shared/../$id"))
        assertNull(sharedCardIdFromLink("real://shared/%2e%2e/$id"))
    }
    @Test fun queryOrFragmentCannotSmuggleAnotherOperation() {
        assertNull(sharedCardIdFromLink("real://shared/$id?redirect=https://example.com"))
        assertNull(sharedCardIdFromLink("real://shared/$id#extra"))
    }
    @Test fun missingOrInvalidTokenIsRejected() {
        assertNull(sharedCardIdFromLink(null))
        assertNull(sharedCardIdFromLink("real://shared/"))
        assertFalse(isSharedCardId("some-card"))
    }
}
