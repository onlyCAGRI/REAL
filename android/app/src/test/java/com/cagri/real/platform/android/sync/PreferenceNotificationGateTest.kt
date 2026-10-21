package com.cagri.real.platform.android.sync

import org.junit.Assert.*
import org.junit.Test

class PreferenceNotificationGateTest {
    @Test fun `rapid UI edits do not deliver the older selection between commits`() {
        val gate = PreferenceNotificationGate()
        gate.begin(); gate.begin()
        assertTrue(gate.receive(setOf("sort")).isEmpty())
        assertTrue(gate.end().isEmpty())
        assertTrue(gate.receive(setOf("sort")).isEmpty())
        assertEquals(setOf("sort"), gate.end())
    }
    @Test fun `remote changes during a pending UI write are not lost`() {
        val gate = PreferenceNotificationGate()
        gate.begin()
        gate.receive(setOf("colour")); gate.receive(setOf("sort"))
        assertEquals(setOf("colour", "sort"), gate.end())
    }
    @Test fun `discarded queued edit releases the gate for normal notifications`() {
        val gate = PreferenceNotificationGate()
        gate.begin(); assertTrue(gate.end().isEmpty())
        assertEquals(setOf("remote"), gate.receive(setOf("remote")))
    }
}
