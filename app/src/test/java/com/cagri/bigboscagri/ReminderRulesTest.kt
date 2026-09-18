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

    @Test fun customCategoryModelCreationAndProperties() {
        val cat = com.cagri.bigboscagri.data.CustomCategory(
            id = 42L,
            name = "Spor ve Sağlık",
            iconKey = "SPORT",
            accentStart = 3,
            accentEnd = 4
        )
        assertEquals(42L, cat.id)
        assertEquals("Spor ve Sağlık", cat.name)
        assertEquals("SPORT", cat.iconKey)
        assertEquals(3, cat.accentStart)
        assertEquals(4, cat.accentEnd)
        assertTrue(cat.createdAt > 0L)
    }

    @Test fun categoryIconFromKeyReturnsCorrectEnumAndDefaultsToWork() {
        assertEquals(com.cagri.bigboscagri.ui.components.CategoryIcon.HOME, com.cagri.bigboscagri.ui.components.CategoryIcon.fromKey("HOME"))
        assertEquals(com.cagri.bigboscagri.ui.components.CategoryIcon.SHOPPING, com.cagri.bigboscagri.ui.components.CategoryIcon.fromKey("shopping"))
        assertEquals(com.cagri.bigboscagri.ui.components.CategoryIcon.WORK, com.cagri.bigboscagri.ui.components.CategoryIcon.fromKey("non_existent_key"))
        assertEquals(16, com.cagri.bigboscagri.ui.components.CategoryIcon.entries.size)
    }

    @Test fun categoryPaginationComputesCorrectPageCount() {
        fun computePageCount(customCount: Int): Int {
            val totalItems = 5 + customCount + 1 // 5 system categories + custom categories + 1 Ekle card
            return (totalItems + 5) / 6
        }
        assertEquals(1, computePageCount(0))  // 6 items -> 1 page
        assertEquals(2, computePageCount(1))  // 7 items -> 2 pages
        assertEquals(2, computePageCount(6))  // 12 items -> 2 pages
        assertEquals(3, computePageCount(7))  // 13 items -> 3 pages
        assertEquals(3, computePageCount(12)) // 18 items -> 3 pages
        assertEquals(4, computePageCount(13)) // 19 items -> 4 pages
    }

    @Test fun unifiedCategoryListPlacesAddCardAtTheVeryEnd() {
        val system = listOf("Bugün", "Planlı", "Önemli", "Uyarı yok", "Tamamlananlar")
        val custom = listOf("Kahve", "Spor")
        val unified = system + custom + "Ekle"

        assertEquals(8, unified.size)
        assertEquals("Ekle", unified.last())
        assertEquals("Kahve", unified[5])
        assertEquals("Spor", unified[6])
    }

    @Test fun customCategoryUpdatePreservesIdAndCreatedAt() {
        val original = com.cagri.bigboscagri.data.CustomCategory(
            id = 100L,
            name = "Eski İsim",
            iconKey = "WORK",
            accentStart = 0,
            accentEnd = null,
            createdAt = 123456789L
        )
        val updated = original.copy(
            name = "Yeni İsim",
            iconKey = "FOOD",
            accentStart = 2,
            accentEnd = 7
        )
        assertEquals(100L, updated.id)
        assertEquals(123456789L, updated.createdAt)
        assertEquals("Yeni İsim", updated.name)
        assertEquals("FOOD", updated.iconKey)
        assertEquals(2, updated.accentStart)
        assertEquals(7, updated.accentEnd)
    }

    @Test fun legacyReminderJsonWithoutCustomCategoryDeserializesToNull() {
        val legacyJson = """
            {
                "id": 1,
                "text": "Eski not",
                "createdAt": 1000,
                "updatedAt": 1000,
                "isImportant": false,
                "repeatRule": "NONE"
            }
        """.trimIndent()
        val item = org.json.JSONObject(legacyJson)
        val reminder = ReminderItem(
            id = item.getLong("id"),
            text = item.getString("text"),
            createdAt = item.getLong("createdAt"),
            updatedAt = item.optLong("updatedAt", item.getLong("createdAt")),
            customCategoryId = if (!item.has("customCategoryId") || item.isNull("customCategoryId")) null else item.optLong("customCategoryId").takeIf { it > 0L }
        )
        assertNull(reminder.customCategoryId)
    }

    @Test fun customCategoryIdIsPersistedAndRestored() {
        val original = ReminderItem(
            id = 123L,
            text = "Kahve al",
            createdAt = 1000L,
            customCategoryId = 999L
        )
        val json = org.json.JSONObject()
            .put("id", original.id)
            .put("text", original.text)
            .put("createdAt", original.createdAt)
            .put("customCategoryId", original.customCategoryId ?: org.json.JSONObject.NULL)

        val restoredId = if (!json.has("customCategoryId") || json.isNull("customCategoryId")) null
            else json.optLong("customCategoryId").takeIf { it > 0L }
        assertEquals(999L, restoredId)
    }

    @Test fun createReminderPreservesCustomCategoryId() {
        val reminder = ReminderItem(
            id = 5L,
            text = "Yeni iş notu",
            createdAt = 2000L,
            customCategoryId = 42L
        )
        assertEquals(42L, reminder.customCategoryId)
        assertEquals("Yeni iş notu", reminder.text)
    }

    @Test fun editReminderUpdatesCustomCategoryWithoutAffectingOtherFields() {
        val original = ReminderItem(
            id = 10L,
            text = "Rapor hazırla",
            createdAt = 1000L,
            alertAt = 5000L,
            repeatRule = com.cagri.bigboscagri.data.RecurrenceRule.DAILY,
            isImportant = true,
            accentStart = 1,
            accentEnd = 2,
            customCategoryId = 100L
        )
        val edited = original.copy(
            customCategoryId = 200L,
            updatedAt = 3000L
        )
        assertEquals(200L, edited.customCategoryId)
        assertEquals(original.text, edited.text)
        assertEquals(original.alertAt, edited.alertAt)
        assertEquals(original.repeatRule, edited.repeatRule)
        assertEquals(original.isImportant, edited.isImportant)
        assertEquals(original.accentStart, edited.accentStart)
        assertEquals(original.accentEnd, edited.accentEnd)
    }

    @Test fun editReminderToNoCategoryClearsCustomCategoryId() {
        val original = ReminderItem(
            id = 10L,
            text = "Kategorili not",
            createdAt = 1000L,
            customCategoryId = 100L
        )
        val cleared = original.copy(
            customCategoryId = null,
            updatedAt = 2000L
        )
        assertNull(cleared.customCategoryId)
    }

    @Test fun unassignCategoryClearsCategoryFromRemindersWithoutDeletingReminders() {
        val items = listOf(
            ReminderItem(1L, "Kahve al", 1000L, alertAt = 2000L, customCategoryId = 42L),
            ReminderItem(2L, "Rapor yaz", 1100L, isImportant = true, customCategoryId = 42L),
            ReminderItem(3L, "Spor yap", 1200L, customCategoryId = 99L)
        )
        val targetCategoryId = 42L
        val unassigned = items.map {
            if (it.customCategoryId == targetCategoryId) it.copy(customCategoryId = null)
            else it
        }

        assertEquals(3, unassigned.size)
        val first = unassigned.first { it.id == 1L }
        assertNull(first.customCategoryId)
        assertEquals(2000L, first.alertAt)

        val second = unassigned.first { it.id == 2L }
        assertNull(second.customCategoryId)
        assertTrue(second.isImportant)

        val third = unassigned.first { it.id == 3L }
        assertEquals(99L, third.customCategoryId)
    }

    @Test fun customCategoryFilteringReturnsOnlyMatchingReminders() {
        val items = listOf(
            ReminderItem(1L, "Kahve 1", 1000L, customCategoryId = 10L),
            ReminderItem(2L, "Spor 1", 1100L, customCategoryId = 20L),
            ReminderItem(3L, "Kahve 2", 1200L, customCategoryId = 10L),
            ReminderItem(4L, "Genel", 1300L, customCategoryId = null)
        )
        val categoryId = 10L
        val filtered = items.filter { it.customCategoryId == categoryId }

        assertEquals(2, filtered.size)
        assertEquals(listOf(1L, 3L), filtered.map { it.id })
    }
}
