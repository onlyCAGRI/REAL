package com.cagri.real.sharedcategories

import org.junit.Assert.*
import org.junit.Test

class CategoryInviteTokenTest {
    @Test fun onlyExactOpaqueCategoryInviteIsAccepted() {
        val token = "a".repeat(64)
        assertEquals(token, categoryInviteToken("real://category-invite/$token"))
        for (url in listOf("real://connection/$token", "real://category-invite/$token?uid=person", "real://category-invite/$token#fragment", "real://category-invite/short", "https://category-invite/$token"))
            assertNull(categoryInviteToken(url))
    }
}
