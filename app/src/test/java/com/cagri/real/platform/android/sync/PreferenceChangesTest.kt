package com.cagri.real.platform.android.sync

import org.junit.Assert.*
import org.junit.Test

class PreferenceChangesTest {
    @Test fun unchangedSnapshotProducesNoNotifications() {
        assertTrue(changedPreferenceKeys(mapOf("cards" to "[]"), mapOf("cards" to "[]")).isEmpty())
    }
    @Test fun addsUpdatesAndRemovalsAllNotify() {
        assertEquals(setOf("deleted", "edited", "added"), changedPreferenceKeys(
            mapOf("deleted" to "a", "edited" to "b", "same" to "c"),
            mapOf("edited" to "new", "same" to "c", "added" to "d")
        ))
    }
    @Test fun noOpPutAndMissingRemoveProduceNoCommitEdits() {
        assertTrue(effectivePreferenceEdits(mapOf("a" to "x"), mapOf("a" to "x", "missing" to null), false).isEmpty())
    }
    @Test fun clearKeepsExplicitReinsertAndDeletesOtherKeys() {
        assertEquals(mapOf("removed" to null, "new" to "v"), effectivePreferenceEdits(
            mapOf("kept" to "same", "removed" to "old"), mapOf("kept" to "same", "new" to "v"), true
        ))
    }
    @Test fun clearEmptyStoreIsNoOp() {
        assertTrue(effectivePreferenceEdits(emptyMap<String, Any?>(), emptyMap(), true).isEmpty())
    }
    @Test fun removalsCannotDisguiseThemselvesAsUnchangedValues() {
        assertEquals(mapOf("a" to null), effectivePreferenceEdits(mapOf("a" to "x"), mapOf("a" to null), false))
    }
    @Test fun booleansAndSetsCompareByStoredValue() {
        assertTrue(effectivePreferenceEdits(mapOf("enabled" to false, "set" to setOf("a", "b")),
            mapOf("enabled" to false, "set" to setOf("b", "a")), false).isEmpty())
    }
    @Test fun computingEditsDoesNotMutateEitherInput() {
        val current = mutableMapOf<String, Any?>("a" to "x")
        val requested = mutableMapOf<String, Any?>("a" to null, "b" to "y")
        effectivePreferenceEdits(current, requested, true)
        assertEquals(mapOf("a" to "x"), current)
        assertEquals(mapOf("a" to null, "b" to "y"), requested)
    }
}
