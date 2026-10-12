package com.cagri.real

import android.content.SharedPreferences
import com.cagri.real.data.ReminderRepository
import org.junit.Assert.*
import org.junit.Test

class ReminderRepositoryNoOpTest {
    private class CountingPreferences(private val backing: SharedPreferences = FakeSharedPreferences()) : SharedPreferences by backing {
        var editorCount = 0
        override fun edit(): SharedPreferences.Editor {
            editorCount++
            return backing.edit()
        }
    }
    @Test fun deletingMissingCardDoesNotSerializeAndCommitAgain() {
        val prefs = CountingPreferences()
        val repo = ReminderRepository(prefs)
        val item = repo.add("Keep me")
        val count = prefs.editorCount
        repo.delete(-1L)
        assertEquals(count, prefs.editorCount)
        assertEquals(item, repo.load().single())
    }
    @Test fun restoringIdenticalSnapshotIsNoOp() {
        val prefs = CountingPreferences()
        val repo = ReminderRepository(prefs)
        repo.add("Original")
        val snapshot = repo.load()
        val count = prefs.editorCount
        repo.restore(snapshot.toList())
        assertEquals(count, prefs.editorCount)
    }
    @Test fun staleCacheCannotSkipAnExplicitRestoreAfterAnotherWriter() {
        val prefs = CountingPreferences()
        val first = ReminderRepository(prefs)
        val original = first.add("Original")
        val snapshot = first.load()
        ReminderRepository(prefs).delete(original.id)
        val count = prefs.editorCount
        first.restore(snapshot)
        assertEquals(count + 1, prefs.editorCount)
        assertEquals(original, first.load().single())
    }
    @Test fun callerMutatingRestoredListCannotHideARealDeletion() {
        val prefs = CountingPreferences()
        val repo = ReminderRepository(prefs)
        val item = repo.add("Keep until explicit restore")
        val mutableSnapshot = mutableListOf(item)
        repo.restore(mutableSnapshot)
        mutableSnapshot.clear()
        val count = prefs.editorCount
        repo.restore(mutableSnapshot)
        assertEquals(count + 1, prefs.editorCount)
        assertTrue(ReminderRepository(prefs).load().isEmpty())
    }
    @Test fun realDeletionStillPersistsEmptySnapshot() {
        val prefs = CountingPreferences()
        val repo = ReminderRepository(prefs)
        val item = repo.add("Delete me")
        val count = prefs.editorCount
        repo.delete(item.id)
        assertEquals(count + 1, prefs.editorCount)
        assertTrue(ReminderRepository(prefs).load().isEmpty())
    }
}
