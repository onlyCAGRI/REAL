package com.cagri.bigboscagri

import com.cagri.bigboscagri.data.ChecklistItem
import com.cagri.bigboscagri.data.RecurrenceRule
import com.cagri.bigboscagri.data.ReminderAttachment
import com.cagri.bigboscagri.data.ReminderItem
import com.cagri.bigboscagri.data.ReminderRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkCardSelectionTest {

    @Test
    fun bulkCategoryAssignmentUpdatesSelectedRemindersAtomically() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)

        val item1 = repo.add("Task 1")
        val item2 = repo.add("Task 2")
        val item3 = repo.add("Task 3")

        val targetCatId = 42L
        val updated = repo.setCustomCategoryForReminders(setOf(item1.id, item2.id), targetCatId)

        val updated1 = updated.first { it.id == item1.id }
        val updated2 = updated.first { it.id == item2.id }
        val updated3 = updated.first { it.id == item3.id }

        assertEquals(targetCatId, updated1.customCategoryId)
        assertEquals(targetCatId, updated2.customCategoryId)
        assertNull(updated3.customCategoryId)
    }

    @Test
    fun bulkCategoryAssignmentPreservesAllOtherReminderFields() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)

        val now = System.currentTimeMillis()
        val original = repo.add(
            text = "Detailed task",
            alertAt = now + 3600_000L,
            repeatRule = RecurrenceRule.DAILY,
            customCategoryId = 10L,
            isChecklist = true,
            checklistItems = listOf(ChecklistItem(id = 101L, text = "Subtask 1", completedAt = null)),
            sendImmediateNotification = false,
            referenceUrl = "https://example.com"
        )
        val starred = repo.toggleImportant(original.id).first { it.id == original.id }

        // Bulk assign to new category 99L
        val afterBulk = repo.setCustomCategoryForReminders(setOf(starred.id), 99L).first { it.id == starred.id }

        assertEquals(99L, afterBulk.customCategoryId)
        assertEquals(starred.id, afterBulk.id)
        assertEquals(starred.text, afterBulk.text)
        assertEquals(starred.createdAt, afterBulk.createdAt)
        assertEquals(starred.completedAt, afterBulk.completedAt)
        assertEquals(starred.isImportant, afterBulk.isImportant)
        assertEquals(starred.alertAt, afterBulk.alertAt)
        assertEquals(starred.repeatRule, afterBulk.repeatRule)
        assertEquals(starred.isChecklist, afterBulk.isChecklist)
        assertEquals(starred.checklistItems, afterBulk.checklistItems)
        assertEquals(starred.sendImmediateNotification, afterBulk.sendImmediateNotification)
        assertEquals(starred.referenceUrl, afterBulk.referenceUrl)
        assertEquals(starred.starBorderColorIndex, afterBulk.starBorderColorIndex)
        assertEquals(starred.gradientBlendFraction, afterBulk.gradientBlendFraction)
        assertEquals(starred.customSolidColorArgb, afterBulk.customSolidColorArgb)
        assertEquals(starred.attachments, afterBulk.attachments)
        assertEquals(starred.voiceRecordings, afterBulk.voiceRecordings)
        assertEquals(starred.journal, afterBulk.journal)
        assertEquals(starred.mood, afterBulk.mood)
        assertEquals(starred.location, afterBulk.location)
        assertEquals(starred.musicBlocks, afterBulk.musicBlocks)
        assertEquals(starred.contentBlockOrder, afterBulk.contentBlockOrder)
    }

    @Test
    fun bulkCategoryAssignmentWithNullUnassignsCategory() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)

        val item1 = repo.add(text = "Task 1", customCategoryId = 77L)
        val item2 = repo.add(text = "Task 2", customCategoryId = 77L)
        assertEquals(77L, item1.customCategoryId)
        assertEquals(77L, item2.customCategoryId)

        val unassigned = repo.setCustomCategoryForReminders(setOf(item1.id, item2.id), null)
        assertNull(unassigned.first { it.id == item1.id }.customCategoryId)
        assertNull(unassigned.first { it.id == item2.id }.customCategoryId)
    }

    @Test
    fun bulkCategoryAssignmentWithEmptySetReturnsCurrentListUnmodified() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)

        val item1 = repo.add(text = "Task 1", customCategoryId = 5L)
        val result = repo.setCustomCategoryForReminders(emptySet(), 99L)
        assertEquals(1, result.size)
        assertEquals(5L, result.first().customCategoryId)
    }

    @Test
    fun selectionStateTransitionsCorrectly() {
        var selectedIds = emptySet<Long>()
        fun isSelectionMode() = selectedIds.isNotEmpty()

        assertFalse(isSelectionMode())

        // 1. Long press on card 10 starts selection
        selectedIds = selectedIds + 10L
        assertTrue(isSelectionMode())
        assertEquals(setOf(10L), selectedIds)

        // 2. Tap on card 20 adds it
        selectedIds = selectedIds + 20L
        assertTrue(isSelectionMode())
        assertEquals(setOf(10L, 20L), selectedIds)

        // 3. Tap on card 10 toggles off
        selectedIds = selectedIds - 10L
        assertTrue(isSelectionMode())
        assertEquals(setOf(20L), selectedIds)

        // 4. Tap on card 20 toggles off -> selection mode automatically closes
        selectedIds = selectedIds - 20L
        assertFalse(isSelectionMode())
        assertTrue(selectedIds.isEmpty())
    }
}
