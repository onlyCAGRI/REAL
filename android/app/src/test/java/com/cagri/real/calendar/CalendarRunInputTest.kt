package com.cagri.real.calendar

import com.cagri.real.data.ReminderItem
import org.junit.Assert.*
import org.junit.Test

class CalendarRunInputTest {
    private val options = CalendarOptions(exportEnabled = true, exportAll = true)
    private val scheduled = ReminderItem(1, "Spor", 1, alertAt = 100_000)
    private val note = ReminderItem(2, "Not", 1)
    @Test fun `unplanned text edit does not restart calendar writer`() {
        val before = calendarRunInput(listOf(scheduled, note), options)
        val after = calendarRunInput(listOf(scheduled, note.copy(text = "Başka not", updatedAt = 3)), options)
        assertEquals(before, after)
    }
    @Test fun `scheduled card content is retained for public link refresh`() {
        val before = calendarRunInput(listOf(scheduled, note), options)
        val after = calendarRunInput(listOf(scheduled.copy(text = "Yeni başlık", updatedAt = 3), note), options)
        assertNotEquals(before, after)
        assertEquals("Yeni başlık", after.cards.single().text)
    }
    @Test fun `deletion and completion preserve authoritative card identity`() {
        val completed = scheduled.copy(completedAt = 4)
        val input = calendarRunInput(listOf(completed, note), options)
        assertTrue(input.cards.isEmpty()); assertEquals(setOf(1L, 2L), input.existingCardIds)
        assertEquals(setOf(2L), calendarRunInput(listOf(note), options).existingCardIds)
    }
}
