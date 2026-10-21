package com.cagri.real.calendar

import org.junit.Assert.*
import org.junit.Test

class CalendarCardLinksTest {
    private val association = CalendarCardAssociation(7, 10, "account", "marker", 5)
    private val url = "https://example.invalid/s/opaque-token"
    @Test fun `first publication preserves original description`() {
        val result = withExternalCardLink("Toplantı notları", association, url)
        assertTrue(result.startsWith("Toplantı notları\n\n"))
        assertTrue(result.contains(url))
    }
    @Test fun `publication retry does not duplicate link block`() {
        val first = withExternalCardLink("Notlar", association, url)
        assertEquals(first, withExternalCardLink(first, association, url))
    }
    @Test fun `new publication replaces only the exact owned block`() {
        val block = externalCardLinkBlock(association.marker, url)
        val result = withExternalCardLink("Original\n$block\nYeni not", association.copy(publicBlock = block),
            "https://example.invalid/s/new-token")
        assertTrue(result.startsWith("Original\n")); assertTrue(result.endsWith("\nYeni not"))
        assertFalse(result.contains(url)); assertTrue(result.contains("new-token"))
    }
    @Test fun `ambiguous owned block does not silently replace user text`() {
        val block = externalCardLinkBlock(association.marker, url)
        try {
            withExternalCardLink("$block\n$block", association.copy(publicBlock = block), url)
            fail("Ambiguous descriptions must be rejected")
        } catch (_: IllegalStateException) { }
    }
    @Test fun `native plan URL is included without exposing UID or changing ownership marker`() {
        val plan = CalendarPlan("5:single", 5, "Plan", 1, 2, false, "UTC", url)
        val description = DeviceCalendarProvider.expected(plan, "opaque-marker")[5]!!
        assertTrue(description.contains(url))
        assertTrue(DeviceCalendarProvider.ownsDescription(description, "opaque-marker"))
        assertFalse(DeviceCalendarProvider.ownsDescription(description, "different-marker"))
    }
}
