package com.cagri.bigboscagri

import com.cagri.bigboscagri.data.ReminderCategory
import com.cagri.bigboscagri.data.ReminderItem
import com.cagri.bigboscagri.data.inCategory
import com.cagri.bigboscagri.speech.VoiceDraft
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ReminderRulesTest {
    @Test fun completedNotesLeaveNoAlertAndKeepTheirCompletionDate() {
        val note = ReminderItem(1, "merhaba yeniyim", 100)
        val finished = note.toggleCompleted(200)
        assertTrue(listOf(finished).inCategory(ReminderCategory.NoAlert).isEmpty())
        assertEquals(listOf(finished), listOf(finished).inCategory(ReminderCategory.Completed))
        assertEquals(200L, finished.completedAt)
        assertEquals(100L, finished.createdAt)
    }

    @Test fun undoCompletionRestoresTheNoteWithoutDuplicatingIt() {
        val restored = ReminderItem(1, "not", 100).toggleCompleted(200).toggleCompleted(300)
        assertNull(restored.completedAt)
        assertEquals(300L, restored.updatedAt)
        assertEquals(1, listOf(restored).inCategory(ReminderCategory.NoAlert).size)
        assertTrue(listOf(restored).inCategory(ReminderCategory.Completed).isEmpty())
    }

    @Test fun completedListUsesCompletionOrderRatherThanCreationOrder() {
        val older = ReminderItem(1, "eski", 10, completedAt = 90)
        val newer = ReminderItem(2, "yeni", 20, completedAt = 70)
        assertEquals(listOf(1L, 2L), listOf(newer, older).inCategory(ReminderCategory.Completed).map { it.id })
    }

    @Test fun immediateNotificationNotesAreUnscheduledRegardlessOfTheirWords() {
        val note = ReminderItem(1, "10 dakika sonra uyu", 100)
        assertEquals(listOf(note), listOf(note).inCategory(ReminderCategory.NoAlert))
        assertTrue(listOf(note).inCategory(ReminderCategory.Planned).isEmpty())
    }

    @Test fun importantListExcludesCompletedNotes() {
        val note = ReminderItem(1, "önemli", 100, isImportant = true)
        assertEquals(listOf(note), listOf(note).inCategory(ReminderCategory.Important))
        assertTrue(listOf(note.toggleCompleted(200)).inCategory(ReminderCategory.Important).isEmpty())
    }

    @Test fun todayUsesThePhoneTimeZone() {
        val note = ReminderItem(1, "gece", 1, alertAt = Instant.parse("2026-09-16T22:30:00Z").toEpochMilli())
        assertEquals(listOf(note), listOf(note).inCategory(ReminderCategory.Today,
            LocalDate.of(2026, 9, 17), ZoneId.of("Europe/Istanbul")))
    }

    @Test fun partialSpeechReplacesEarlierPartialInsteadOfRepeatingWords() {
        val voice = VoiceDraft("")
        voice.updatePartial("yarın")
        voice.updatePartial("yarın marketten")
        voice.commitResult("yarın marketten süt al")
        assertEquals("yarın marketten süt al", voice.text)
    }

    @Test fun consecutiveUtterancesKeepTheTypedPrefixAndTheFinalWord() {
        val voice = VoiceDraft("Alışveriş:")
        voice.commitResult("süt al")
        voice.updatePartial("bir de")
        voice.commitResult("bir de yumurta al")
        assertEquals("Alışveriş: süt al bir de yumurta al", voice.text)
    }

    @Test fun timeoutKeepsTheVisiblePartialOnlyOnce() {
        val voice = VoiceDraft("")
        voice.updatePartial("merhaba yeniyim")
        voice.commitResult(null)
        voice.commitResult(null)
        assertEquals("merhaba yeniyim", voice.text)
    }

    @Test fun silenceDoesNotGenerateANoteOrEraseEarlierWords() {
        val voice = VoiceDraft("")
        voice.commitResult("")
        assertEquals("", voice.text)
        voice.updatePartial("Çağrı")
        voice.updatePartial("")
        assertEquals("Çağrı", voice.text)
    }

    @Test fun reminderItemSupportsNullableAccentColors() {
        val defaultItem = ReminderItem(1, "varsayılan", 100)
        assertNull(defaultItem.accentStart)
        assertNull(defaultItem.accentEnd)

        val singleColorItem = defaultItem.copy(accentStart = 0, accentEnd = null)
        assertEquals(0, singleColorItem.accentStart)
        assertNull(singleColorItem.accentEnd)

        val dualColorItem = defaultItem.copy(accentStart = 2, accentEnd = 9)
        assertEquals(2, dualColorItem.accentStart)
        assertEquals(9, dualColorItem.accentEnd)
    }

    @Test fun multipleItemsCanBeFilteredForBulkDeletion() {
        val items = listOf(
            ReminderItem(1, "birinci", 100),
            ReminderItem(2, "ikinci", 200),
            ReminderItem(3, "üçüncü", 300)
        )
        val toDelete = setOf(1L, 3L)
        val remaining = items.filterNot { it.id in toDelete }
        assertEquals(1, remaining.size)
        assertEquals(2L, remaining.first().id)
    }
}
