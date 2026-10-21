package com.cagri.real.boards

import org.junit.Assert.*
import org.junit.Test

class PanoPersistentContentTest {
    @Test fun `network status and membership refresh do not require a snapshot rewrite`() {
        val before = PanoState(loaded = true)
        assertTrue(before.samePersistentContent(before.copy(syncing = true, error = "offline")))
    }
    @Test fun `cursor viewport and background edits still require durable storage`() {
        val before = PanoState()
        assertFalse(before.samePersistentContent(before.copy(cursors = mapOf("board" to 10L))))
        assertFalse(before.samePersistentContent(before.copy(backgrounds = mapOf("board" to "black"))))
        assertFalse(before.samePersistentContent(before.copy(viewports = mapOf("board" to BoardViewport()))))
    }
}
