package com.cagri.real

import com.cagri.real.data.ChecklistItem
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.buildReminderShareText
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderShareContentTest {

    @Test
    fun normalReminderSharesTitleOnly() {
        val reminder = ReminderItem(
            id = 1L,
            text = "Motor yağını değiştir",
            createdAt = 1000L,
            isChecklist = false
        )
        val sharedText = buildReminderShareText(reminder)
        assertEquals("Motor yağını değiştir", sharedText)
    }

    @Test
    fun checklistIncludesTitleAndEveryItemWithCorrectPrefixes() {
        val reminder = ReminderItem(
            id = 2L,
            text = "Alışveriş",
            createdAt = 1000L,
            isChecklist = true,
            checklistItems = listOf(
                ChecklistItem(id = 1L, text = "Süt", completedAt = null),
                ChecklistItem(id = 2L, text = "Ekmek", completedAt = 1000L),
                ChecklistItem(id = 3L, text = "Kahve", completedAt = null)
            )
        )
        val expected = "Alışveriş\n\n☐ Süt\n☑ Ekmek\n☐ Kahve"
        val sharedText = buildReminderShareText(reminder)
        assertEquals(expected, sharedText)
    }

    @Test
    fun checklistWithAllIncompleteItems() {
        val reminder = ReminderItem(
            id = 3L,
            text = "Görevler",
            createdAt = 1000L,
            isChecklist = true,
            checklistItems = listOf(
                ChecklistItem(id = 1L, text = "Görev 1", completedAt = null),
                ChecklistItem(id = 2L, text = "Görev 2", completedAt = null)
            )
        )
        val expected = "Görevler\n\n☐ Görev 1\n☐ Görev 2"
        val sharedText = buildReminderShareText(reminder)
        assertEquals(expected, sharedText)
    }

    @Test
    fun checklistWithAllCompletedItems() {
        val reminder = ReminderItem(
            id = 4L,
            text = "Tamamlananlar",
            createdAt = 1000L,
            isChecklist = true,
            checklistItems = listOf(
                ChecklistItem(id = 1L, text = "İş 1", completedAt = 1000L),
                ChecklistItem(id = 2L, text = "İş 2", completedAt = 1000L)
            )
        )
        val expected = "Tamamlananlar\n\n☑ İş 1\n☑ İş 2"
        val sharedText = buildReminderShareText(reminder)
        assertEquals(expected, sharedText)
    }

    @Test
    fun originalChecklistOrderIsPreserved() {
        val reminder = ReminderItem(
            id = 5L,
            text = "Sıralama Testi",
            createdAt = 1000L,
            isChecklist = true,
            checklistItems = listOf(
                ChecklistItem(id = 10L, text = "Zebra", completedAt = null),
                ChecklistItem(id = 2L, text = "Aslan", completedAt = 1000L),
                ChecklistItem(id = 30L, text = "Maymun", completedAt = null)
            )
        )
        val expected = "Sıralama Testi\n\n☐ Zebra\n☑ Aslan\n☐ Maymun"
        val sharedText = buildReminderShareText(reminder)
        assertEquals(expected, sharedText)
    }

    @Test
    fun turkishCharactersAndEmojisArePreserved() {
        val reminder = ReminderItem(
            id = 6L,
            text = "Pazar Çantası 🛒",
            createdAt = 1000L,
            isChecklist = true,
            checklistItems = listOf(
                ChecklistItem(id = 1L, text = "Ispanak & Çilek 🍓", completedAt = null),
                ChecklistItem(id = 2L, text = "Yoğurt & Peynir 🧀", completedAt = 1000L),
                ChecklistItem(id = 3L, text = "Şalgam & Zeytin 🫒", completedAt = null)
            )
        )
        val expected = "Pazar Çantası 🛒\n\n☐ Ispanak & Çilek 🍓\n☑ Yoğurt & Peynir 🧀\n☐ Şalgam & Zeytin 🫒"
        val sharedText = buildReminderShareText(reminder)
        assertEquals(expected, sharedText)
    }

    @Test
    fun blankAndWhitespaceChecklistItemsAreSkippedAndTextIsTrimmed() {
        val reminder = ReminderItem(
            id = 7L,
            text = " Boşluk Testi ",
            createdAt = 1000L,
            isChecklist = true,
            checklistItems = listOf(
                ChecklistItem(id = 1L, text = "   ", completedAt = null),
                ChecklistItem(id = 2L, text = "  Madde 1  ", completedAt = null),
                ChecklistItem(id = 3L, text = "", completedAt = 1000L),
                ChecklistItem(id = 4L, text = "Madde 2", completedAt = 1000L),
                ChecklistItem(id = 5L, text = "   \n\t  ", completedAt = null)
            )
        )
        val expected = "Boşluk Testi\n\n☐ Madde 1\n☑ Madde 2"
        val sharedText = buildReminderShareText(reminder)
        assertEquals(expected, sharedText)
    }

    @Test
    fun checklistWithNoValidItemsReturnsTitleOnly() {
        val reminder = ReminderItem(
            id = 8L,
            text = "Boş Liste",
            createdAt = 1000L,
            isChecklist = true,
            checklistItems = listOf(
                ChecklistItem(id = 1L, text = "   ", completedAt = null)
            )
        )
        val sharedText = buildReminderShareText(reminder)
        assertEquals("Boş Liste", sharedText)
    }
}
