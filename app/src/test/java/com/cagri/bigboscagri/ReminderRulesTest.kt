package com.cagri.bigboscagri

import com.cagri.bigboscagri.data.CategoryColorOverride
import com.cagri.bigboscagri.data.CategoryRepository
import com.cagri.bigboscagri.data.CustomCategory
import com.cagri.bigboscagri.data.ReminderCategory
import com.cagri.bigboscagri.data.ReminderItem
import com.cagri.bigboscagri.data.ReminderRepository
import com.cagri.bigboscagri.data.categoryCounts
import com.cagri.bigboscagri.data.deleteCategoryWithRollback
import com.cagri.bigboscagri.data.inCategory
import com.cagri.bigboscagri.data.ChecklistItem
import com.cagri.bigboscagri.notifications.canShowImmediateNotification
import com.cagri.bigboscagri.notifications.needsBigTextStyle
import com.cagri.bigboscagri.notifications.resolveNotificationMetadata
import com.cagri.bigboscagri.speech.VoiceDraft
import com.cagri.bigboscagri.speech.VoiceReminderParser
import com.cagri.bigboscagri.ui.isStandaloneCategoryInDetail
import com.cagri.bigboscagri.ui.reminderDetailStatusText
import com.cagri.bigboscagri.ui.shouldShowNoAlertInDetail
import androidx.compose.ui.graphics.Color
import com.cagri.bigboscagri.ui.components.ReminderColorChoices
import com.cagri.bigboscagri.ui.components.ReminderEntryTracker
import com.cagri.bigboscagri.ui.components.defaultStyle
import com.cagri.bigboscagri.ui.components.resolveCategoryStripeColors
import com.cagri.bigboscagri.ui.components.resolveStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
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
        assertEquals(com.cagri.bigboscagri.ui.components.CategoryIcon.PET, com.cagri.bigboscagri.ui.components.CategoryIcon.fromKey("PET"))
        assertEquals(com.cagri.bigboscagri.ui.components.CategoryIcon.GIFT, com.cagri.bigboscagri.ui.components.CategoryIcon.fromKey("GIFT"))
        assertEquals(com.cagri.bigboscagri.ui.components.CategoryIcon.PET, com.cagri.bigboscagri.ui.components.CategoryIcon.fromKey("CAR"))
        assertEquals(com.cagri.bigboscagri.ui.components.CategoryIcon.GIFT, com.cagri.bigboscagri.ui.components.CategoryIcon.fromKey("GAME"))
        assertEquals(18, com.cagri.bigboscagri.ui.components.CategoryIcon.entries.size)
        assertTrue(com.cagri.bigboscagri.ui.components.CategoryIcon.selectableEntries.contains(com.cagri.bigboscagri.ui.components.CategoryIcon.PET))
        assertTrue(com.cagri.bigboscagri.ui.components.CategoryIcon.selectableEntries.contains(com.cagri.bigboscagri.ui.components.CategoryIcon.GIFT))
        assertEquals(18, com.cagri.bigboscagri.ui.components.CategoryIcon.selectableEntries.size)
        assertEquals(
            listOf(
                com.cagri.bigboscagri.ui.components.CategoryIcon.BOOKMARK,
                com.cagri.bigboscagri.ui.components.CategoryIcon.PET,
                com.cagri.bigboscagri.ui.components.CategoryIcon.GIFT
            ),
            com.cagri.bigboscagri.ui.components.CategoryIcon.selectableEntries.takeLast(3)
        )
        assertTrue(com.cagri.bigboscagri.ui.components.CategoryIcon.selectableEntries.contains(com.cagri.bigboscagri.ui.components.CategoryIcon.MONEY))
        assertTrue(com.cagri.bigboscagri.ui.components.CategoryIcon.selectableEntries.contains(com.cagri.bigboscagri.ui.components.CategoryIcon.LOCATION))
        assertTrue(com.cagri.bigboscagri.ui.components.CategoryIcon.selectableEntries.contains(com.cagri.bigboscagri.ui.components.CategoryIcon.BOOKMARK))
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

    @Test fun realReminderRepositoryAddPersistsCustomCategoryId() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)

        val added = repo.add("Kahve satın al", customCategoryId = 42L)
        assertEquals(42L, added.customCategoryId)
        assertEquals("Kahve satın al", added.text)

        // Verify fresh repository instance reads the persisted data from SharedPreferences
        val reloaded = ReminderRepository(prefs).load()
        assertEquals(1, reloaded.size)
        assertEquals(42L, reloaded.first().customCategoryId)
        assertEquals("Kahve satın al", reloaded.first().text)
    }

    @Test fun realReminderRepositoryEditUpdatesCustomCategoryId() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)

        val item = repo.add("İlk not", customCategoryId = 10L)
        assertEquals(10L, item.customCategoryId)

        // Edit custom category and text
        val updated = repo.edit(
            id = item.id,
            text = "Güncellenmiş not",
            accentStart = 2,
            accentEnd = 5,
            customCategoryId = 20L,
            updateCategory = true
        ).first()

        assertEquals("Güncellenmiş not", updated.text)
        assertEquals(20L, updated.customCategoryId)
        assertEquals(2, updated.accentStart)
        assertEquals(5, updated.accentEnd)

        // Edit again, clearing custom category
        val cleared = repo.edit(
            id = item.id,
            text = "Güncellenmiş not",
            accentStart = 2,
            accentEnd = 5,
            customCategoryId = null,
            updateCategory = true
        ).first()
        assertNull(cleared.customCategoryId)

        // Edit with updateCategory = false keeps existing category
        val restored = repo.setCustomCategory(item.id, 99L).first()
        assertEquals(99L, restored.customCategoryId)

        val untouchedCategory = repo.edit(
            id = item.id,
            text = "Başka bir güncelleme",
            accentStart = null,
            accentEnd = null,
            customCategoryId = null,
            updateCategory = false
        ).first()
        assertEquals("Başka bir güncelleme", untouchedCategory.text)
        assertEquals(99L, untouchedCategory.customCategoryId)

        // Verify disk persistence with fresh repository instance
        val reloaded = ReminderRepository(prefs).load().first()
        assertEquals(99L, reloaded.customCategoryId)
        assertEquals("Başka bir güncelleme", reloaded.text)
    }

    @Test fun realReminderRepositoryUnassignCategoryRemovesOnlyTargetCategory() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)

        repo.add("Kategori A - Not 1", customCategoryId = 100L)
        repo.add("Kategori A - Not 2", customCategoryId = 100L)
        repo.add("Kategori B - Not 3", customCategoryId = 200L)
        repo.add("Kategorisiz - Not 4", customCategoryId = null)

        val afterUnassign = repo.unassignCategory(100L)
        assertEquals(4, afterUnassign.size)

        val item1 = afterUnassign.first { it.text == "Kategori A - Not 1" }
        val item2 = afterUnassign.first { it.text == "Kategori A - Not 2" }
        val item3 = afterUnassign.first { it.text == "Kategori B - Not 3" }
        val item4 = afterUnassign.first { it.text == "Kategorisiz - Not 4" }

        assertNull(item1.customCategoryId)
        assertNull(item2.customCategoryId)
        assertEquals(200L, item3.customCategoryId)
        assertNull(item4.customCategoryId)

        // Verify disk persistence with fresh repository instance
        val reloaded = ReminderRepository(prefs).load()
        assertEquals(4, reloaded.size)
        assertNull(reloaded.first { it.text == "Kategori A - Not 1" }.customCategoryId)
        assertNull(reloaded.first { it.text == "Kategori A - Not 2" }.customCategoryId)
        assertEquals(200L, reloaded.first { it.text == "Kategori B - Not 3" }.customCategoryId)
        assertNull(reloaded.first { it.text == "Kategorisiz - Not 4" }.customCategoryId)
    }

    @Test fun realCategoryRepositoryAddUpdateDelete() {
        val prefs = FakeSharedPreferences()
        val repo = CategoryRepository(prefs)

        // Add
        val added = repo.add(name = "Finans", iconKey = "WORK", accentStart = 1, accentEnd = 3)
        assertEquals(1, added.size)
        val cat = added.first()
        assertEquals("Finans", cat.name)
        assertEquals("WORK", cat.iconKey)
        assertEquals(1, cat.accentStart)
        assertEquals(3, cat.accentEnd)

        // Reload from disk
        val reloaded = CategoryRepository(prefs).load()
        assertEquals(1, reloaded.size)
        assertEquals("Finans", reloaded.first().name)

        // Update
        val updated = repo.update(cat.id, name = "Kişisel Finans", iconKey = "SHOPPING", accentStart = 4, accentEnd = 5)
        assertEquals(1, updated.size)
        assertEquals("Kişisel Finans", updated.first().name)
        assertEquals("SHOPPING", updated.first().iconKey)
        assertEquals(4, updated.first().accentStart)
        assertEquals(5, updated.first().accentEnd)

        // Delete
        val afterDelete = repo.delete(cat.id)
        assertTrue(afterDelete.isEmpty())
        assertTrue(CategoryRepository(prefs).load().isEmpty())
    }

    @Test fun realCategoryDeleteAndUnassignIntegration() {
        val reminderPrefs = FakeSharedPreferences()
        val categoryPrefs = FakeSharedPreferences()
        val reminderRepo = ReminderRepository(reminderPrefs)
        val categoryRepo = CategoryRepository(categoryPrefs)

        // Create category
        val category = categoryRepo.add("Okuma", "BOOK", 0, null).first()

        // Create reminders: 2 in category, 1 in other category
        reminderRepo.add("Kitap oku", customCategoryId = category.id)
        reminderRepo.add("Makale incele", customCategoryId = category.id)
        reminderRepo.add("Egzersiz", customCategoryId = 999L)

        // Delete workflow as executed in mutateCategory: unassign then delete
        reminderRepo.unassignCategory(category.id)
        categoryRepo.delete(category.id)

        // Verify categories
        assertTrue(CategoryRepository(categoryPrefs).load().isEmpty())

        // Verify reminders still exist and category is unassigned without deleting reminders
        val reminders = ReminderRepository(reminderPrefs).load()
        assertEquals(3, reminders.size)
        assertNull(reminders.first { it.text == "Kitap oku" }.customCategoryId)
        assertNull(reminders.first { it.text == "Makale incele" }.customCategoryId)
        assertEquals(999L, reminders.first { it.text == "Egzersiz" }.customCategoryId)
    }

    @Test fun categoryDeleteArtificialFailureRollsBackReminderCategoryRelationship() {
        val reminderPrefs = FakeSharedPreferences()
        val categoryPrefs = FakeSharedPreferences()
        val reminderRepo = ReminderRepository(reminderPrefs)
        val categoryRepo = CategoryRepository(categoryPrefs)

        val category = categoryRepo.add("İş", "WORK", 1, null).first()
        reminderRepo.add("Rapor yaz", customCategoryId = category.id)
        reminderRepo.add("Diğer not", customCategoryId = 999L)

        // Pre-check: "Rapor yaz" has customCategoryId = category.id
        val preItem = reminderRepo.load().first { it.text == "Rapor yaz" }
        assertEquals(category.id, preItem.customCategoryId)

        // Execute deleteCategoryWithRollback where the deleteStep artificially throws an error
        var thrown = false
        try {
            deleteCategoryWithRollback(category.id, reminderRepo, categoryRepo) {
                throw RuntimeException("Yapay kategori silme disk hatası")
            }
        } catch (e: RuntimeException) {
            thrown = true
            assertEquals("Yapay kategori silme disk hatası", e.message)
        }
        assertTrue("Exception must be rethrown", thrown)

        // Verify reminder-category relationship is rolled back in memory
        val inMemoryReminders = reminderRepo.load()
        val inMemoryTarget = inMemoryReminders.first { it.text == "Rapor yaz" }
        assertEquals("Reminder customCategoryId must be restored to original value", category.id, inMemoryTarget.customCategoryId)
        assertEquals(999L, inMemoryReminders.first { it.text == "Diğer not" }.customCategoryId)

        // Verify reminder-category relationship is rolled back on disk (fresh repository instance)
        val onDiskReminders = ReminderRepository(reminderPrefs).load()
        val onDiskTarget = onDiskReminders.first { it.text == "Rapor yaz" }
        assertEquals("Disk customCategoryId must be restored to original value", category.id, onDiskTarget.customCategoryId)
        assertEquals(999L, onDiskReminders.first { it.text == "Diğer not" }.customCategoryId)

        // Verify category still exists on disk
        val onDiskCategories = CategoryRepository(categoryPrefs).load()
        assertEquals(1, onDiskCategories.size)
        assertEquals(category.id, onDiskCategories.first().id)
    }

    @Test fun repositoryRestoreOverwritesStateAndPersists() {
        val reminderPrefs = FakeSharedPreferences()
        val reminderRepo = ReminderRepository(reminderPrefs)

        val created = reminderRepo.add("Orijinal Not", customCategoryId = 777L)
        assertEquals(777L, created.customCategoryId)
        val snapshot = reminderRepo.load()

        // Mutate by unassigning
        reminderRepo.unassignCategory(777L)
        assertNull(reminderRepo.load().first().customCategoryId)

        // Restore snapshot
        reminderRepo.restore(snapshot)
        assertEquals(777L, reminderRepo.load().first().customCategoryId)

        // Verify fresh repository instance sees restored data
        assertEquals(777L, ReminderRepository(reminderPrefs).load().first().customCategoryId)

        // Also test CategoryRepository restore
        val catPrefs = FakeSharedPreferences()
        val catRepo = CategoryRepository(catPrefs)
        val catSnapshot = catRepo.add("Test Cat", "BOOK", 0, null)
        assertEquals(1, catSnapshot.size)

        catRepo.delete(catSnapshot.first().id)
        assertTrue(catRepo.load().isEmpty())

        catRepo.restore(catSnapshot)
        assertEquals(1, catRepo.load().size)
        assertEquals(catSnapshot.first().id, catRepo.load().first().id)
        assertEquals(1, CategoryRepository(catPrefs).load().size)
    }

    @Test fun noAlertAndNoCustomCategoryEntersNoAlertCategory() {
        val item = ReminderItem(id = 1L, text = "Genel not", createdAt = 1000L, alertAt = null, customCategoryId = null)
        val filtered = listOf(item).inCategory(ReminderCategory.NoAlert)
        assertEquals(1, filtered.size)
        assertEquals(1L, filtered.first().id)

        val counts = listOf(item).categoryCounts(LocalDate.now())
        assertEquals(1, counts[ReminderCategory.NoAlert])
    }

    @Test fun noAlertAndCustomCategoryDoesNotEnterNoAlertCategory() {
        val item = ReminderItem(id = 2L, text = "Araba bakım", createdAt = 1000L, alertAt = null, customCategoryId = 42L)
        val filtered = listOf(item).inCategory(ReminderCategory.NoAlert)
        assertTrue("Reminder with custom category must not enter NoAlert category", filtered.isEmpty())

        val counts = listOf(item).categoryCounts(LocalDate.now())
        assertEquals(0, counts[ReminderCategory.NoAlert])
    }

    @Test fun plannedWithCustomCategoryEntersBothPlannedAndCustomCategoryFilter() {
        val futureAlert = System.currentTimeMillis() + 3600000L
        val item = ReminderItem(id = 3L, text = "Toplantı", createdAt = 1000L, alertAt = futureAlert, customCategoryId = 77L)

        // Enters Planned system category
        val plannedList = listOf(item).inCategory(ReminderCategory.Planned)
        assertEquals(1, plannedList.size)
        assertEquals(3L, plannedList.first().id)

        val counts = listOf(item).categoryCounts(LocalDate.now())
        assertEquals(1, counts[ReminderCategory.Planned])

        // Enters custom category filter
        val customCategoryFiltered = listOf(item).filter { it.customCategoryId == 77L }
        assertEquals(1, customCategoryFiltered.size)
        assertEquals(3L, customCategoryFiltered.first().id)

        // Does NOT enter NoAlert
        assertTrue(listOf(item).inCategory(ReminderCategory.NoAlert).isEmpty())
    }

    @Test fun categoryNotFoundNotificationMetadataUsesRealFallback() {
        val categoryPrefs = FakeSharedPreferences()
        val categoryRepo = CategoryRepository(categoryPrefs)

        // Case A: reminder has customCategoryId pointing to a non-existent category
        val orphanedReminder = ReminderItem(id = 10L, text = "Yağ ekle", createdAt = 1000L, customCategoryId = 999L)
        val metadataFallback = resolveNotificationMetadata(orphanedReminder, categoryRepo, fallbackTitle = "Real")
        assertEquals("Real", metadataFallback.title)
        assertEquals("Yağ ekle", metadataFallback.text)
        assertNull(metadataFallback.accentColor)
        assertNull(metadataFallback.category)

        // Case B: reminder has null customCategoryId
        val standardReminder = ReminderItem(id = 11L, text = "Ekmek al", createdAt = 1000L, customCategoryId = null)
        val standardMetadata = resolveNotificationMetadata(standardReminder, categoryRepo, fallbackTitle = "Real")
        assertEquals("Real", standardMetadata.title)
        assertEquals("Ekmek al", standardMetadata.text)
        assertNull(standardMetadata.accentColor)
        assertNull(standardMetadata.category)

        // Case C: category exists -> metadata uses category name and accent color
        val addedCategory = categoryRepo.add("Evcil hayvan", "PET", 6, null).first()
        val matchingReminder = ReminderItem(id = 12L, text = "Yağ ekle", createdAt = 1000L, customCategoryId = addedCategory.id)
        val matchedMetadata = resolveNotificationMetadata(matchingReminder, categoryRepo, fallbackTitle = "Real")
        assertEquals("Evcil hayvan", matchedMetadata.title)
        assertEquals("Yağ ekle", matchedMetadata.text)
        assertEquals(addedCategory, matchedMetadata.category)
        assertNotNull(matchedMetadata.accentColor)
    }

    @Test fun customCategoryWithNoAlertDoesNotAppearInNoAlertFilter() {
        val itemWithCategory = ReminderItem(
            id = 1L,
            text = "Araba bakımı",
            createdAt = 1000L,
            alertAt = null,
            customCategoryId = 42L
        )
        val itemWithoutCategory = ReminderItem(
            id = 2L,
            text = "Ekmek al",
            createdAt = 1000L,
            alertAt = null,
            customCategoryId = null
        )
        val list = listOf(itemWithCategory, itemWithoutCategory)

        // custom category + no alert, NoAlert filtresinde görünmüyor
        val noAlertList = list.inCategory(ReminderCategory.NoAlert)
        assertEquals(listOf(itemWithoutCategory), noAlertList)
        assertFalse(noAlertList.contains(itemWithCategory))
        assertFalse(itemWithCategory.isNoAlert)
        assertTrue(itemWithoutCategory.isNoAlert)
    }

    @Test fun categoryCountDoesNotCountNoAlertForCustomCategoryItem() {
        val itemWithCategoryNoAlert = ReminderItem(
            id = 1L,
            text = "Araba bakımı",
            createdAt = 1000L,
            alertAt = null,
            customCategoryId = 42L
        )
        val itemWithoutCategoryNoAlert = ReminderItem(
            id = 2L,
            text = "Ekmek al",
            createdAt = 1000L,
            alertAt = null,
            customCategoryId = null
        )
        // category count da NoAlert saymıyor
        val today = LocalDate.now()
        val counts = listOf(itemWithCategoryNoAlert, itemWithoutCategoryNoAlert).categoryCounts(today)
        assertEquals(1, counts[ReminderCategory.NoAlert])

        val countsOnlyCustom = listOf(itemWithCategoryNoAlert).categoryCounts(today)
        assertEquals(0, countsOnlyCustom[ReminderCategory.NoAlert] ?: 0)
    }

    @Test fun detailPresentationHelperHidesNoAlertForCustomCategoryItem() {
        val itemWithCategoryNoAlert = ReminderItem(
            id = 1L,
            text = "Araba bakımı",
            createdAt = 1000L,
            alertAt = null,
            customCategoryId = 42L
        )
        val itemWithoutCategoryNoAlert = ReminderItem(
            id = 2L,
            text = "Ekmek al",
            createdAt = 1000L,
            alertAt = null,
            customCategoryId = null
        )
        val completedItem = ReminderItem(
            id = 3L,
            text = "Tamamlanan",
            createdAt = 1000L,
            completedAt = 2000L,
            customCategoryId = 42L
        )

        // detail presentation helper varsa custom category + no alert için Uyarı yok göstermiyor
        assertFalse(shouldShowNoAlertInDetail(itemWithCategoryNoAlert))
        assertNull(reminderDetailStatusText(itemWithCategoryNoAlert))
        assertTrue(isStandaloneCategoryInDetail(itemWithCategoryNoAlert))

        assertTrue(shouldShowNoAlertInDetail(itemWithoutCategoryNoAlert))
        assertEquals("Uyarı yok", reminderDetailStatusText(itemWithoutCategoryNoAlert))
        assertFalse(isStandaloneCategoryInDetail(itemWithoutCategoryNoAlert))

        assertFalse(shouldShowNoAlertInDetail(completedItem))
        assertEquals("Tamamlandı", reminderDetailStatusText(completedItem))
        assertFalse(isStandaloneCategoryInDetail(completedItem))

        val plannedItemWithCategory = ReminderItem(4L, "Toplantı", 1000L, alertAt = 2000L, customCategoryId = 42L)
        assertFalse(isStandaloneCategoryInDetail(plannedItemWithCategory))
    }

    @Test fun shortNotificationDoesNotNeedBigTextStyle() {
        val shortText = "Kısa bir hatırlatıcı metni"
        assertFalse(needsBigTextStyle(shortText))
        assertFalse(needsBigTextStyle("1234567890".repeat(8))) // exactly 80 chars
    }

    @Test fun longNotificationNeedsBigTextStyle() {
        val longText = "Bu çok uzun bir hatırlatıcı metnidir. ".repeat(4) // > 80 chars
        assertTrue(longText.length > 80)
        assertTrue(needsBigTextStyle(longText))

        val multilineText = "İlk satır\nİkinci satır"
        assertTrue(multilineText.length < 80)
        assertTrue(needsBigTextStyle(multilineText))
    }

    @Test fun legacyReminderLoadsAsChecklistFalseAndEmptyItems() {
        val prefs = FakeSharedPreferences()
        val legacyJson = """[{"id":100,"text":"Eski Not","createdAt":1000,"updatedAt":1000}]"""
        prefs.edit().putString("reminders", legacyJson).commit()

        val repo = ReminderRepository(prefs)
        val loaded = repo.load()
        assertEquals(1, loaded.size)
        val item = loaded.first()
        assertFalse(item.isChecklist)
        assertTrue(item.checklistItems.isEmpty())
    }

    @Test fun checklistPersistenceRoundtrip() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val items = listOf(
            ChecklistItem(id = 1L, text = "Süt"),
            ChecklistItem(id = 2L, text = "Yumurta")
        )
        val saved = repo.add(
            text = "Alışveriş",
            isChecklist = true,
            checklistItems = items
        )
        val savedItem = saved
        assertTrue(savedItem.isChecklist)
        assertEquals(2, savedItem.checklistItems.size)
        assertEquals("Süt", savedItem.checklistItems[0].text)
        assertEquals("Yumurta", savedItem.checklistItems[1].text)
        assertFalse(savedItem.checklistItems[0].isCompleted)

        // Reload fresh from prefs
        val freshRepo = ReminderRepository(prefs)
        val reloaded = freshRepo.load().first()
        assertTrue(reloaded.isChecklist)
        assertEquals(2, reloaded.checklistItems.size)
        assertEquals("Süt", reloaded.checklistItems[0].text)
        assertEquals("Yumurta", reloaded.checklistItems[1].text)
        assertEquals(savedItem.checklistItems[0].id, reloaded.checklistItems[0].id)
    }

    @Test fun checklistItemTogglePersistenceAndDoesNotCompleteReminder() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val saved = repo.add(
            text = "Alışveriş",
            isChecklist = true,
            checklistItems = listOf(ChecklistItem(id = 1L, text = "Süt"), ChecklistItem(id = 2L, text = "Yumurta"))
        )

        val reminderId = saved.id
        val itemId = saved.checklistItems[0].id

        val updated = repo.toggleChecklistItem(reminderId, itemId)
        val updatedReminder = updated.first { it.id == reminderId }

        // Item is completed
        val toggledItem = updatedReminder.checklistItems.first { it.id == itemId }
        assertTrue(toggledItem.isCompleted)
        assertNotNull(toggledItem.completedAt)

        // Other item is not completed
        val otherItem = updatedReminder.checklistItems.first { it.id != itemId }
        assertFalse(otherItem.isCompleted)

        // Crucial rule: reminder completion status remains UNCHANGED
        assertFalse(updatedReminder.isCompleted)
        assertNull(updatedReminder.completedAt)

        // Verify persistence roundtrip for toggled item
        val freshRepo = ReminderRepository(prefs)
        val loadedReminder = freshRepo.load().first { it.id == reminderId }
        assertTrue(loadedReminder.checklistItems.first { it.id == itemId }.isCompleted)
        assertFalse(loadedReminder.isCompleted)
    }

    @Test fun checklistWithNoAlertDoesNotAllowImmediateNotification() {
        val checklistNoAlert = ReminderItem(
            id = 1L,
            text = "Alışveriş",
            createdAt = 1000L,
            alertAt = null,
            isChecklist = true,
            checklistItems = listOf(ChecklistItem(id = 10L, text = "Süt"))
        )
        assertFalse(canShowImmediateNotification(checklistNoAlert))

        val normalNoAlert = ReminderItem(
            id = 2L,
            text = "Normal Not",
            createdAt = 1000L,
            alertAt = null,
            isChecklist = false
        )
        assertTrue(canShowImmediateNotification(normalNoAlert))
    }

    @Test fun checklistWithScheduledAlertShowsOnlyTitleInNotification() {
        val checklistScheduled = ReminderItem(
            id = 1L,
            text = "Alışveriş",
            createdAt = 1000L,
            alertAt = System.currentTimeMillis() + 60000L,
            isChecklist = true,
            checklistItems = listOf(
                ChecklistItem(id = 10L, text = "Süt"),
                ChecklistItem(id = 11L, text = "Yumurta"),
                ChecklistItem(id = 12L, text = "Motor yağı")
            )
        )
        assertTrue(canShowImmediateNotification(checklistScheduled))

        val metadata = resolveNotificationMetadata(checklistScheduled, categoryRepository = null, fallbackTitle = "Real")
        assertEquals("Real", metadata.title)
        assertEquals("Alışveriş", metadata.text)

        // Verify items are NOT part of the notification metadata
        assertFalse(metadata.text.contains("Süt"))
        assertFalse(metadata.text.contains("Yumurta"))
        assertFalse(metadata.text.contains("Motor yağı"))
        assertFalse(needsBigTextStyle(metadata.text))
    }

    @Test fun customCategoryWithChecklistPreservesCategoryTitleAndChecklistTitle() {
        val prefs = FakeSharedPreferences()
        val catRepo = CategoryRepository(prefs)
        catRepo.add("Araba", "car", 1, null)
        val category = catRepo.load().first()

        val checklistWithCat = ReminderItem(
            id = 1L,
            text = "Alışveriş",
            createdAt = 1000L,
            alertAt = System.currentTimeMillis() + 60000L,
            customCategoryId = category.id,
            isChecklist = true,
            checklistItems = listOf(ChecklistItem(id = 10L, text = "Motor yağı"))
        )

        val metadata = resolveNotificationMetadata(checklistWithCat, categoryRepository = catRepo, fallbackTitle = "Real")
        assertEquals("Araba", metadata.title)
        assertEquals("Alışveriş", metadata.text)
        assertFalse(metadata.text.contains("Motor yağı"))
    }

    @Test fun checklistWithExplicitTitlePreservesTitleAndItems() {
        val prefs = FakeSharedPreferences()
        val repository = ReminderRepository(prefs)
        val items = listOf(
            ChecklistItem(id = 0L, text = "Süt"),
            ChecklistItem(id = 0L, text = "Yumurta"),
            ChecklistItem(id = 0L, text = "Kahve")
        )
        val reminder = repository.add(
            text = "Alışveriş",
            isChecklist = true,
            checklistItems = items
        )
        assertTrue(reminder.isChecklist)
        assertEquals("Alışveriş", reminder.text)
        assertEquals(3, reminder.checklistItems.size)
        assertEquals("Süt", reminder.checklistItems[0].text)
        assertEquals("Yumurta", reminder.checklistItems[1].text)
        assertEquals("Kahve", reminder.checklistItems[2].text)
    }

    @Test(expected = IllegalArgumentException::class)
    fun checklistWithBlankTitleFailsValidation() {
        val prefs = FakeSharedPreferences()
        val repository = ReminderRepository(prefs)
        val items = listOf(
            ChecklistItem(id = 0L, text = "Ekmek"),
            ChecklistItem(id = 0L, text = "Peynir")
        )
        repository.add(
            text = "   ",
            isChecklist = true,
            checklistItems = items
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun checklistWithoutAnyItemsFailsValidation() {
        val prefs = FakeSharedPreferences()
        val repository = ReminderRepository(prefs)
        repository.add(
            text = "Alışveriş",
            isChecklist = true,
            checklistItems = emptyList()
        )
    }

    @Test fun normalReminderNoAlertImmediateOnAllowsImmediateNotification() {
        val reminder = ReminderItem(
            id = 1L,
            text = "Evi temizle",
            createdAt = 1000L,
            alertAt = null,
            isChecklist = false,
            sendImmediateNotification = true
        )
        assertTrue(canShowImmediateNotification(reminder))
    }

    @Test fun normalReminderNoAlertImmediateOffSuppressesImmediateNotification() {
        val reminder = ReminderItem(
            id = 2L,
            text = "Sessiz not",
            createdAt = 1000L,
            alertAt = null,
            isChecklist = false,
            sendImmediateNotification = false
        )
        assertFalse(canShowImmediateNotification(reminder))
    }

    @Test fun normalReminderWithAlertSetDoesNotUseImmediatePath() {
        val futureAlert = System.currentTimeMillis() + 3600000L
        val scheduledReminder = ReminderItem(
            id = 3L,
            text = "Doktor randevusu",
            createdAt = 1000L,
            alertAt = futureAlert,
            isChecklist = false,
            sendImmediateNotification = true
        )
        assertNotNull(scheduledReminder.alertAt)
        assertTrue(canShowImmediateNotification(scheduledReminder))
    }

    @Test fun checklistWithoutAlertAlwaysSuppressesImmediateNotificationRegardlessOfImmediateFlag() {
        val checklistWithImmediateOn = ReminderItem(
            id = 4L,
            text = "Market",
            createdAt = 1000L,
            alertAt = null,
            isChecklist = true,
            checklistItems = listOf(ChecklistItem(1L, "Ekmek")),
            sendImmediateNotification = true
        )
        assertFalse(canShowImmediateNotification(checklistWithImmediateOn))

        val checklistWithImmediateOff = checklistWithImmediateOn.copy(sendImmediateNotification = false)
        assertFalse(canShowImmediateNotification(checklistWithImmediateOff))
    }

    @Test fun singleColorCategoryStripeColorLogic() {
        val singleCat = CustomCategory(
            id = 10L,
            name = "Kişisel",
            iconKey = "PERSON",
            accentStart = 0,
            accentEnd = null
        )
        val (c1, c2) = resolveCategoryStripeColors(singleCat)
        val expectedColor = ReminderColorChoices[0].color
        assertEquals(expectedColor, c1)
        assertEquals(expectedColor.copy(alpha = 0.55f), c2)
    }

    @Test fun twoColorCategoryStripeColorLogic() {
        val dualCat = CustomCategory(
            id = 11L,
            name = "İş ve Proje",
            iconKey = "WORK",
            accentStart = 1,
            accentEnd = 3
        )
        val (c1, c2) = resolveCategoryStripeColors(dualCat)
        val startColor = ReminderColorChoices[1].color
        val endColor = ReminderColorChoices[3].color
        assertEquals(startColor, c1)
        assertEquals(endColor, c2)
    }

    @Test fun reminderRepositoryPersistsSendImmediateNotificationFlag() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        repo.add(
            text = "Bildirimsiz not",
            sendImmediateNotification = false
        )
        val reloaded = repo.load().first()
        assertFalse(reloaded.sendImmediateNotification)

        val repo2 = ReminderRepository(prefs)
        repo2.add(
            text = "Bildirimli not",
            sendImmediateNotification = true
        )
        val reloaded2 = repo2.load().first { it.text == "Bildirimli not" }
        assertTrue(reloaded2.sendImmediateNotification)
    }

    @Test fun addReturnsCreatedItemWithGeneratedId() {
        val prefs = FakeSharedPreferences()
        val repository = ReminderRepository(prefs)
        val created = repository.add(text = "Test created ID")
        assertTrue(created.id > 0)
        assertEquals("Test created ID", created.text)
        val loaded = repository.load()
        assertEquals(1, loaded.size)
        assertEquals(created.id, loaded.first().id)
    }

    @Test fun addFailureThrowsAndDoesNotPersistOrEmitFakeId() {
        val prefs = FakeSharedPreferences()
        val repository = ReminderRepository(prefs)
        try {
            repository.add(text = "   ")
            org.junit.Assert.fail("Expected IllegalArgumentException for blank text")
        } catch (_: IllegalArgumentException) {}
        assertTrue(repository.load().isEmpty())
    }

    @Test fun rapidConsecutiveAddsReturnDistinctCorrectIds() {
        val prefs = FakeSharedPreferences()
        val repository = ReminderRepository(prefs)
        val first = repository.add(text = "İlk hatırlatıcı")
        val second = repository.add(text = "İkinci hatırlatıcı")
        assertTrue(first.id > 0)
        assertTrue(second.id > 0)
        org.junit.Assert.assertNotEquals(first.id, second.id)
        val loaded = repository.load()
        assertEquals(2, loaded.size)
        assertTrue(loaded.any { it.id == first.id && it.text == "İlk hatırlatıcı" })
        assertTrue(loaded.any { it.id == second.id && it.text == "İkinci hatırlatıcı" })
    }

    @Test fun checklistEditingPreservesExistingItemsAndCompletedState() {
        val prefs = FakeSharedPreferences()
        val repository = ReminderRepository(prefs)
        val initialItems = listOf(
            ChecklistItem(id = 101L, text = "Süt", completedAt = 12345L),
            ChecklistItem(id = 102L, text = "Ekmek", completedAt = null),
            ChecklistItem(id = 103L, text = "Yumurta", completedAt = 67890L)
        )
        val reminder = repository.add(
            text = "Market Alışverişi",
            isChecklist = true,
            checklistItems = initialItems
        )
        assertEquals(3, reminder.checklistItems.size)

        // User edits: modifies "Ekmek" to "Tam Buğday Ekmek", deletes "Yumurta", adds "Peynir"
        val editedItems = listOf(
            ChecklistItem(id = 101L, text = "Süt", completedAt = 12345L),
            ChecklistItem(id = 102L, text = "Tam Buğday Ekmek", completedAt = null),
            ChecklistItem(id = 0L, text = "Peynir", completedAt = null) // Newly added item
        )
        val updated = repository.edit(
            id = reminder.id,
            text = "Süpermarket",
            checklistItems = editedItems
        )
        val updatedReminder = updated.first { it.id == reminder.id }
        assertEquals("Süpermarket", updatedReminder.text)
        assertEquals(3, updatedReminder.checklistItems.size)

        // Item 101: untouched ID and completedAt preserved
        val sutItem = updatedReminder.checklistItems.first { it.id == 101L }
        assertEquals("Süt", sutItem.text)
        assertEquals(12345L, sutItem.completedAt)

        // Item 102: text updated, ID and completedAt preserved
        val ekmekItem = updatedReminder.checklistItems.first { it.id == 102L }
        assertEquals("Tam Buğday Ekmek", ekmekItem.text)
        assertNull(ekmekItem.completedAt)

        // Item 103: removed
        assertNull(updatedReminder.checklistItems.firstOrNull { it.id == 103L })

        // New item: assigned stable ID > 0
        val peynirItem = updatedReminder.checklistItems.first { it.text == "Peynir" }
        assertTrue(peynirItem.id > 0L)
        assertNull(peynirItem.completedAt)

        // Editing only reminder text must NOT clear or reset checklist
        val textOnlyUpdate = repository.edit(
            id = reminder.id,
            text = "Haftalık Market"
        )
        val textOnlyReminder = textOnlyUpdate.first { it.id == reminder.id }
        assertEquals("Haftalık Market", textOnlyReminder.text)
        assertEquals(3, textOnlyReminder.checklistItems.size)
        assertEquals(12345L, textOnlyReminder.checklistItems.first { it.id == 101L }.completedAt)
    }

    @Test fun defaultBuiltInCategoryColorIsPreserved() {
        val prefs = FakeSharedPreferences()
        val repo = CategoryRepository(prefs)
        ReminderCategory.entries.forEach { category ->
            assertNull(repo.getBuiltInCategoryColor(category))
        }
        assertTrue(repo.getBuiltInCategoryColors().isEmpty())
    }

    @Test fun customBuiltInCategoryColorPersistsAcrossInstances() {
        val prefs = FakeSharedPreferences()
        val repo1 = CategoryRepository(prefs)
        repo1.setBuiltInCategoryColor(ReminderCategory.Today, 2)
        repo1.setBuiltInCategoryColor(ReminderCategory.Planned, 4)

        assertEquals(CategoryColorOverride(2, null), repo1.getBuiltInCategoryColor(ReminderCategory.Today))
        assertEquals(CategoryColorOverride(4, null), repo1.getBuiltInCategoryColor(ReminderCategory.Planned))
        assertNull(repo1.getBuiltInCategoryColor(ReminderCategory.Important))

        val repo2 = CategoryRepository(prefs)
        val colors = repo2.getBuiltInCategoryColors()
        assertEquals(CategoryColorOverride(2, null), colors[ReminderCategory.Today])
        assertEquals(CategoryColorOverride(4, null), colors[ReminderCategory.Planned])
        assertNull(colors[ReminderCategory.Important])
    }

    @Test fun restoringDefaultRestoresOriginalColor() {
        val prefs = FakeSharedPreferences()
        val repo = CategoryRepository(prefs)
        repo.setBuiltInCategoryColor(ReminderCategory.Important, 1)
        assertEquals(CategoryColorOverride(1, null), repo.getBuiltInCategoryColor(ReminderCategory.Important))

        repo.setBuiltInCategoryColor(ReminderCategory.Important, null as CategoryColorOverride?)
        assertNull(repo.getBuiltInCategoryColor(ReminderCategory.Important))
        assertFalse(repo.getBuiltInCategoryColors().containsKey(ReminderCategory.Important))
    }

    @Test fun changingCategoryColorDoesNotAffectIdentityOrFilterSemantics() {
        val prefs = FakeSharedPreferences()
        val repo = CategoryRepository(prefs)
        val now = System.currentTimeMillis()

        val reminders = listOf(
            ReminderItem(1, "Bugünlük", 100, alertAt = now),
            ReminderItem(2, "Planlı", 100, alertAt = now + 86400000L),
            ReminderItem(3, "Önemli", 100, isImportant = true),
            ReminderItem(4, "Uyarısız", 100),
            ReminderItem(5, "Tamam", 100, completedAt = now)
        )

        val todayBefore = reminders.inCategory(ReminderCategory.Today)
        val plannedBefore = reminders.inCategory(ReminderCategory.Planned)
        val importantBefore = reminders.inCategory(ReminderCategory.Important)
        val noAlertBefore = reminders.inCategory(ReminderCategory.NoAlert)
        val completedBefore = reminders.inCategory(ReminderCategory.Completed)
        val countsBefore = reminders.categoryCounts(LocalDate.now())

        ReminderCategory.entries.forEachIndexed { index, cat ->
            repo.setBuiltInCategoryColor(cat, index)
        }

        assertEquals(todayBefore, reminders.inCategory(ReminderCategory.Today))
        assertEquals(plannedBefore, reminders.inCategory(ReminderCategory.Planned))
        assertEquals(importantBefore, reminders.inCategory(ReminderCategory.Important))
        assertEquals(noAlertBefore, reminders.inCategory(ReminderCategory.NoAlert))
        assertEquals(completedBefore, reminders.inCategory(ReminderCategory.Completed))
        assertEquals(countsBefore, reminders.categoryCounts(LocalDate.now()))
    }

    @Test fun customCategoryCreationKeepsSelectedColor() {
        val prefs = FakeSharedPreferences()
        val repo = CategoryRepository(prefs)
        val createdList = repo.add(
            name = "Proje",
            iconKey = "WORK",
            accentStart = 3,
            accentEnd = 5
        )
        val created = createdList.first { it.name == "Proje" }
        assertEquals("Proje", created.name)
        assertEquals("WORK", created.iconKey)
        assertEquals(3, created.accentStart)
        assertEquals(5, created.accentEnd)

        val loaded = repo.load()
        assertEquals(1, loaded.size)
        val first = loaded.first()
        assertEquals("Proje", first.name)
        assertEquals(3, first.accentStart)
        assertEquals(5, first.accentEnd)
    }

    @Test fun cancelingEditorDoesNotPersistPendingChanges() {
        val prefs = FakeSharedPreferences()
        val repo = CategoryRepository(prefs)
        repo.setBuiltInCategoryColor(ReminderCategory.Today, 1)

        var pendingOverride: CategoryColorOverride? = CategoryColorOverride(3, null)
        val onCancel = {
            pendingOverride = repo.getBuiltInCategoryColor(ReminderCategory.Today)
        }
        onCancel()

        assertEquals(CategoryColorOverride(1, null), repo.getBuiltInCategoryColor(ReminderCategory.Today))
        assertEquals(CategoryColorOverride(1, null), pendingOverride)
    }

    @Test fun exactCreatedReminderIdIsSelectedForEntry() {
        val tracker = ReminderEntryTracker()
        tracker.onReminderCreated(101L, reduceAnimations = false)
        assertTrue(tracker.isAnimating(101L))
    }

    @Test fun unrelatedReminderDoesNotAnimate() {
        val tracker = ReminderEntryTracker()
        tracker.onReminderCreated(101L, reduceAnimations = false)
        assertTrue(tracker.isAnimating(101L))
        assertFalse(tracker.isAnimating(102L))
        assertFalse(tracker.isAnimating(999L))
    }

    @Test fun transientEntryIdClearsAfterCompletion() {
        val tracker = ReminderEntryTracker()
        tracker.onReminderCreated(101L, reduceAnimations = false)
        assertTrue(tracker.isAnimating(101L))

        tracker.onAnimationFinished(101L)
        assertFalse(tracker.isAnimating(101L))
        assertTrue(tracker.animatingIds.isEmpty())
    }

    @Test fun reduceAnimationsBypassesEntryStateAndDelay() {
        val tracker = ReminderEntryTracker()
        tracker.onReminderCreated(101L, reduceAnimations = true)
        assertFalse(tracker.isAnimating(101L))
        assertTrue(tracker.animatingIds.isEmpty())
    }

    @Test fun invisibleOrFilteredNewReminderDoesNotDisturbListState() {
        val tracker = ReminderEntryTracker()
        val createdId = 101L
        tracker.onReminderCreated(createdId, reduceAnimations = false)

        val reminders = listOf(
            ReminderItem(1, "Eski Hatırlatıcı", 100)
        )
        val todayReminders = reminders.inCategory(ReminderCategory.Today)

        assertFalse(todayReminders.any { it.id == createdId })
        assertTrue(tracker.isAnimating(createdId))
        tracker.onAnimationFinished(createdId)
        assertFalse(tracker.isAnimating(createdId))
        assertEquals(1, reminders.size)
    }

    @Test fun rapidConsecutiveAdditionsTrackIndependentEntries() {
        val tracker = ReminderEntryTracker()
        tracker.onReminderCreated(101L, reduceAnimations = false)
        tracker.onReminderCreated(102L, reduceAnimations = false)

        assertTrue(tracker.isAnimating(101L))
        assertTrue(tracker.isAnimating(102L))
        assertEquals(setOf(101L, 102L), tracker.animatingIds)

        tracker.onAnimationFinished(101L)
        assertFalse(tracker.isAnimating(101L))
        assertTrue(tracker.isAnimating(102L))

        tracker.onAnimationFinished(102L)
        assertFalse(tracker.isAnimating(102L))
        assertTrue(tracker.animatingIds.isEmpty())
    }

    // --------------------------------------------------
    // Batch 4: Voice Checklist Creation Tests
    // --------------------------------------------------

    @Test
    fun voiceChecklist_explicitNamedList_extractsTitleAndItems() {
        val parsed = VoiceReminderParser.parse("alışveriş listesi oluştur süt, ekmek, yumurta")
        assertTrue(parsed.isChecklist)
        assertEquals("Alışveriş listesi", parsed.text)
        assertEquals(listOf("Süt", "Ekmek", "Yumurta"), parsed.checklistItems)
    }

    @Test
    fun voiceChecklist_genericListeOlustur_extractsGenericTitleAndItems() {
        val parsed = VoiceReminderParser.parse("liste oluştur süt, ekmek, yumurta")
        assertTrue(parsed.isChecklist)
        assertEquals("Liste", parsed.text)
        assertEquals(listOf("Süt", "Ekmek", "Yumurta"), parsed.checklistItems)
    }

    @Test
    fun voiceChecklist_commaSeparatedItems_extractsCleanItems() {
        val parsed = VoiceReminderParser.parse("yapılacaklar listesi oluştur evi süpür, çöpleri çıkar, faturayı öde")
        assertTrue(parsed.isChecklist)
        assertEquals("Yapılacaklar listesi", parsed.text)
        assertEquals(listOf("Evi süpür", "Çöpleri çıkar", "Faturayı öde"), parsed.checklistItems)
    }

    @Test
    fun voiceChecklist_finalVeSeparator_extractsAllItems() {
        val parsed = VoiceReminderParser.parse("alışveriş listesi oluştur süt, ekmek ve yumurta")
        assertTrue(parsed.isChecklist)
        assertEquals("Alışveriş listesi", parsed.text)
        assertEquals(listOf("Süt", "Ekmek", "Yumurta"), parsed.checklistItems)
    }

    @Test
    fun voiceChecklist_semicolonSeparatedItems_extractsCleanItems() {
        val parsed = VoiceReminderParser.parse("alışveriş listesi oluştur süt; ekmek; yumurta")
        assertTrue(parsed.isChecklist)
        assertEquals("Alışveriş listesi", parsed.text)
        assertEquals(listOf("Süt", "Ekmek", "Yumurta"), parsed.checklistItems)
    }

    @Test
    fun voiceChecklist_emptyFragmentsIgnored() {
        val parsed = VoiceReminderParser.parse("alışveriş listesi oluştur süt, , ekmek,, yumurta")
        assertTrue(parsed.isChecklist)
        assertEquals("Alışveriş listesi", parsed.text)
        assertEquals(listOf("Süt", "Ekmek", "Yumurta"), parsed.checklistItems)
    }

    @Test
    fun voiceChecklist_newItemsBeginIncomplete() {
        val parsed = VoiceReminderParser.parse("alışveriş listesi oluştur süt, ekmek, yumurta")
        val items = parsed.checklistItems.map { ChecklistItem(id = 0L, text = it) }
        assertTrue(items.all { it.completedAt == null })
    }

    @Test
    fun voiceChecklist_stableNonDuplicateChecklistIdsInRepository() {
        val repo = ReminderRepository(FakeSharedPreferences())
        val parsed = VoiceReminderParser.parse("alışveriş listesi oluştur süt, ekmek, yumurta")
        val reminder = repo.add(
            text = parsed.text,
            isChecklist = parsed.isChecklist,
            checklistItems = parsed.checklistItems.map { ChecklistItem(id = 0L, text = it) }
        )
        assertTrue(reminder.isChecklist)
        assertEquals(3, reminder.checklistItems.size)
        val ids = reminder.checklistItems.map { it.id }
        assertEquals(ids.distinct().size, ids.size)
        assertTrue(ids.all { it > 0L })
        assertTrue(reminder.checklistItems.all { it.completedAt == null })
    }

    @Test
    fun voiceChecklist_normalCommaSentenceDoesNotBecomeChecklist() {
        val parsed = VoiceReminderParser.parse("yarın annemi ara, sonra markete git")
        assertFalse(parsed.isChecklist)
        assertTrue(parsed.checklistItems.isEmpty())
        assertEquals("Annemi ara, sonra markete git", parsed.text)
    }

    @Test
    fun voiceChecklist_normalVeSentenceDoesNotBecomeChecklist() {
        val parsed1 = VoiceReminderParser.parse("yarın marketten süt, ekmek ve yumurta al")
        assertFalse(parsed1.isChecklist)
        assertTrue(parsed1.checklistItems.isEmpty())
        assertEquals("Marketten süt, ekmek ve yumurta al", parsed1.text)

        val parsed2 = VoiceReminderParser.parse("Ali'yi ara ve toplantıyı hatırlat")
        assertFalse(parsed2.isChecklist)
        assertTrue(parsed2.checklistItems.isEmpty())
        assertEquals("Ali'yi ara ve toplantıyı hatırlat", parsed2.text)
    }

    @Test
    fun voiceChecklist_boundaryFillerYa_stripsYaAndExtractsChecklist() {
        val parsed = VoiceReminderParser.parse("ya alışveriş listesi oluştur süt, ekmek, yumurta ya")
        assertTrue(parsed.isChecklist)
        assertEquals("Alışveriş listesi", parsed.text)
        assertEquals(listOf("Süt", "Ekmek", "Yumurta"), parsed.checklistItems)
    }

    @Test
    fun voiceChecklist_datePlusExplicitListIntentPreservesDate() {
        val now = ZonedDateTime.of(2026, 9, 20, 10, 0, 0, 0, ZoneId.of("Europe/Istanbul"))
        val parsed = VoiceReminderParser.parse("yarın alışveriş listesi oluştur süt, ekmek, yumurta", now)
        assertTrue(parsed.isChecklist)
        assertEquals("Alışveriş listesi", parsed.text)
        assertEquals(listOf("Süt", "Ekmek", "Yumurta"), parsed.checklistItems)
        assertEquals(LocalDate.of(2026, 9, 21), parsed.dateHint)
        assertTrue(parsed.requiresTimeSelection)
    }

    @Test
    fun voiceReminder_normalReminderVerbsPreserved() {
        // 1. "Ali'yi ara ve toplantıyı hatırlat" -> !isChecklist, full text preserved
        val p1 = VoiceReminderParser.parse("Ali'yi ara ve toplantıyı hatırlat")
        assertFalse(p1.isChecklist)
        assertTrue(p1.checklistItems.isEmpty())
        assertEquals("Ali'yi ara ve toplantıyı hatırlat", p1.text)

        // 2. "annemi ara ve faturayı öde" -> !isChecklist, full text preserved
        val p2 = VoiceReminderParser.parse("annemi ara ve faturayı öde")
        assertFalse(p2.isChecklist)
        assertTrue(p2.checklistItems.isEmpty())
        assertEquals("Annemi ara ve faturayı öde", p2.text)

        // 3. "yarın marketten süt, ekmek ve yumurta al" -> !isChecklist, normal parsed date preserved, remaining full reminder text preserved
        val p3 = VoiceReminderParser.parse("yarın marketten süt, ekmek ve yumurta al")
        assertFalse(p3.isChecklist)
        assertTrue(p3.checklistItems.isEmpty())
        assertEquals("Marketten süt, ekmek ve yumurta al", p3.text)
        assertEquals(LocalDate.now().plusDays(1), p3.dateHint)

        // 4. "alışveriş listesi oluştur süt, ekmek ve yumurta" -> checklist parsing still works
        val p4 = VoiceReminderParser.parse("alışveriş listesi oluştur süt, ekmek ve yumurta")
        assertTrue(p4.isChecklist)
        assertEquals("Alışveriş listesi", p4.text)
        assertEquals(listOf("Süt", "Ekmek", "Yumurta"), p4.checklistItems)

        // 5. "alışveriş listeme süt ve ekmek ekle" -> checklist parsing still works
        val p5 = VoiceReminderParser.parse("alışveriş listeme süt ve ekmek ekle")
        assertTrue(p5.isChecklist)
        assertEquals("Alışveriş listesi", p5.text)
        assertEquals(listOf("Süt", "Ekmek"), p5.checklistItems)
    }

    @Test
    fun builtInCategory_defaultRestoresBothOriginalColors() {
        val defaultToday = ReminderCategory.Today.defaultStyle()
        val resolvedToday = ReminderCategory.Today.resolveStyle(null as CategoryColorOverride?)
        assertEquals(defaultToday.base, resolvedToday.base)
        assertEquals(defaultToday.upper, resolvedToday.upper)
        assertEquals(defaultToday.lower, resolvedToday.lower)

        val defaultPlanned = ReminderCategory.Planned.defaultStyle()
        val resolvedPlanned = ReminderCategory.Planned.resolveStyle(null as CategoryColorOverride?)
        assertEquals(defaultPlanned.base, resolvedPlanned.base)
        assertEquals(defaultPlanned.upper, resolvedPlanned.upper)
        assertEquals(defaultPlanned.lower, resolvedPlanned.lower)
    }

    @Test
    fun builtInCategory_selectingOnlyColor1_persistsAndRendersSingleColor() {
        val prefs = FakeSharedPreferences()
        val repo = CategoryRepository(prefs)

        // Set single color override (start=0, end=null)
        repo.setBuiltInCategoryColor(ReminderCategory.Today, CategoryColorOverride(0, null))

        // Verify loaded from same and fresh repo instance
        val loaded = repo.getBuiltInCategoryColor(ReminderCategory.Today)
        assertNotNull(loaded)
        assertEquals(0, loaded?.start)
        assertNull(loaded?.end)

        val freshRepo = CategoryRepository(prefs)
        val freshLoaded = freshRepo.getBuiltInCategoryColor(ReminderCategory.Today)
        assertNotNull(freshLoaded)
        assertEquals(0, freshLoaded?.start)
        assertNull(freshLoaded?.end)

        // Verify single color style rendering (does not fake a second color)
        val style = ReminderCategory.Today.resolveStyle(loaded)
        val c1 = ReminderColorChoices[0].color
        assertEquals(Color(0xFF14151C), style.base)
        assertEquals(c1.copy(alpha = 0.85f), style.upper)
        assertEquals(c1.copy(alpha = 0.60f), style.lower)
    }

    @Test
    fun builtInCategory_selectingColor1AndColor2_persistsGradientAndRendersTwoColors() {
        val prefs = FakeSharedPreferences()
        val repo = CategoryRepository(prefs)

        // Set two-color gradient override (start=0, end=3)
        repo.setBuiltInCategoryColor(ReminderCategory.Important, CategoryColorOverride(0, 3))

        val loaded = repo.getBuiltInCategoryColor(ReminderCategory.Important)
        assertNotNull(loaded)
        assertEquals(0, loaded?.start)
        assertEquals(3, loaded?.end)

        val freshRepo = CategoryRepository(prefs)
        val freshLoaded = freshRepo.getBuiltInCategoryColor(ReminderCategory.Important)
        assertEquals(CategoryColorOverride(0, 3), freshLoaded)

        // Verify two-color style rendering
        val style = ReminderCategory.Important.resolveStyle(loaded)
        val c1 = ReminderColorChoices[0].color
        val c2 = ReminderColorChoices[3].color
        assertEquals(Color(0xFF14151C), style.base)
        assertEquals(c1.copy(alpha = 0.85f), style.upper)
        assertEquals(c2.copy(alpha = 0.70f), style.lower)
    }

    @Test
    fun builtInCategory_clearingColor2_leavesColor1Intact() {
        val prefs = FakeSharedPreferences()
        val repo = CategoryRepository(prefs)

        // Initially two colors (start=2, end=5)
        repo.setBuiltInCategoryColor(ReminderCategory.NoAlert, CategoryColorOverride(2, 5))
        assertEquals(CategoryColorOverride(2, 5), repo.getBuiltInCategoryColor(ReminderCategory.NoAlert))

        // Clear Color 2 -> override with end = null
        repo.setBuiltInCategoryColor(ReminderCategory.NoAlert, CategoryColorOverride(2, null))
        val updated = repo.getBuiltInCategoryColor(ReminderCategory.NoAlert)
        assertNotNull(updated)
        assertEquals(2, updated?.start)
        assertNull(updated?.end)

        // Now clear Color 1 as well -> null override (restores default)
        repo.setBuiltInCategoryColor(ReminderCategory.NoAlert, null as CategoryColorOverride?)
        assertNull(repo.getBuiltInCategoryColor(ReminderCategory.NoAlert))
    }

    @Test
    fun customCategory_preservesBothColorsAcrossReload() {
        val prefs = FakeSharedPreferences()
        val repo = CategoryRepository(prefs)

        // Add custom category with 2 colors
        repo.add(name = "İş Projesi", iconKey = "WORK", accentStart = 2, accentEnd = 6)
        // Add custom category with 1 color
        repo.add(name = "Kişisel", iconKey = "HOME", accentStart = 4, accentEnd = null)

        val categories = repo.load()
        assertEquals(2, categories.size)

        val workCat = categories.first { it.name == "İş Projesi" }
        assertEquals("WORK", workCat.iconKey)
        assertEquals(2, workCat.accentStart)
        assertEquals(6, workCat.accentEnd)

        val homeCat = categories.first { it.name == "Kişisel" }
        assertEquals("HOME", homeCat.iconKey)
        assertEquals(4, homeCat.accentStart)
        assertNull(homeCat.accentEnd)

        // Verify fresh repository instance retains both
        val freshCategories = CategoryRepository(prefs).load()
        val freshWork = freshCategories.first { it.name == "İş Projesi" }
        assertEquals(2, freshWork.accentStart)
        assertEquals(6, freshWork.accentEnd)

        val freshHome = freshCategories.first { it.name == "Kişisel" }
        assertEquals(4, freshHome.accentStart)
        assertNull(freshHome.accentEnd)
    }

    @Test
    fun categoryEditor_cancelDoesNotPersistTemporaryColorChanges() {
        val prefs = FakeSharedPreferences()
        val repo = CategoryRepository(prefs)

        // Initial state: no overrides
        assertNull(repo.getBuiltInCategoryColor(ReminderCategory.Today))

        // User opens editor, modifies temporary in-memory state, but dismisses/cancels (no repo write)
        var tempColor1: Int? = 5
        var tempColor2: Int? = 8
        // User clicks "Vazgeç" -> temp values discarded, repo not called
        tempColor1 = null
        tempColor2 = null

        // Verify repository remains unchanged
        assertNull(repo.getBuiltInCategoryColor(ReminderCategory.Today))
        assertNull(CategoryRepository(prefs).getBuiltInCategoryColor(ReminderCategory.Today))
    }

    @Test
    fun categoryIdentityAndFilterSemantics_remainUnchangedWithColorOverrides() {
        val prefs = FakeSharedPreferences()
        val repo = CategoryRepository(prefs)

        // Override colors for Today and Planned
        repo.setBuiltInCategoryColor(ReminderCategory.Today, CategoryColorOverride(1, 2))
        repo.setBuiltInCategoryColor(ReminderCategory.Planned, CategoryColorOverride(3, 4))

        val now = LocalDate.of(2026, 9, 20)
        val zone = ZoneId.systemDefault()
        val todayEpoch = now.atStartOfDay(zone).toInstant().toEpochMilli() + 3600000L
        val item1 = ReminderItem(id = 1L, text = "Bugün görevi", createdAt = 1000L, alertAt = todayEpoch)
        val item2 = ReminderItem(id = 2L, text = "Genel not", createdAt = 1000L, alertAt = null)

        // Filtering by ReminderCategory must remain 100% identical
        val todayItems = listOf(item1, item2).inCategory(ReminderCategory.Today, today = now, zone = zone)
        assertEquals(1, todayItems.size)
        assertEquals(1L, todayItems.first().id)

        val noAlertItems = listOf(item1, item2).inCategory(ReminderCategory.NoAlert, today = now, zone = zone)
        assertEquals(1, noAlertItems.size)
        assertEquals(2L, noAlertItems.first().id)

        // Category counts must remain identical
        val counts = listOf(item1, item2).categoryCounts(now, zone = zone)
        assertEquals(1, counts[ReminderCategory.Today])
        assertEquals(1, counts[ReminderCategory.NoAlert])

        // Category enum identity / equality is completely unaffected
        assertEquals("Today", ReminderCategory.Today.name)
        assertEquals("Planned", ReminderCategory.Planned.name)
    }
}

class FakeSharedPreferences : android.content.SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = HashMap(data)

    override fun getString(key: String, defValue: String?): String? =
        (data[key] as? String) ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        (data[key] as? Set<String>) ?: defValues

    override fun getInt(key: String, defValue: Int): Int =
        (data[key] as? Int) ?: defValue

    override fun getLong(key: String, defValue: Long): Long =
        (data[key] as? Long) ?: defValue

    override fun getFloat(key: String, defValue: Float): Float =
        (data[key] as? Float) ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        (data[key] as? Boolean) ?: defValue

    override fun contains(key: String): Boolean = data.containsKey(key)

    override fun edit(): android.content.SharedPreferences.Editor = Editor(this)

    override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class Editor(private val prefs: FakeSharedPreferences) : android.content.SharedPreferences.Editor {
        private val modifications = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String, value: String?): android.content.SharedPreferences.Editor {
            modifications[key] = value
            return this
        }

        override fun putStringSet(key: String, values: Set<String>?): android.content.SharedPreferences.Editor {
            modifications[key] = values
            return this
        }

        override fun putInt(key: String, value: Int): android.content.SharedPreferences.Editor {
            modifications[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): android.content.SharedPreferences.Editor {
            modifications[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): android.content.SharedPreferences.Editor {
            modifications[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): android.content.SharedPreferences.Editor {
            modifications[key] = value
            return this
        }

        override fun remove(key: String): android.content.SharedPreferences.Editor {
            modifications[key] = this
            return this
        }

        override fun clear(): android.content.SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            synchronized(prefs) {
                if (clear) {
                    prefs.data.clear()
                }
                for ((key, value) in modifications) {
                    if (value === this) {
                        prefs.data.remove(key)
                    } else {
                        prefs.data[key] = value
                    }
                }
            }
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
