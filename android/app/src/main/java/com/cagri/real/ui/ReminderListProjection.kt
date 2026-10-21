package com.cagri.real.ui

import androidx.compose.runtime.*
import com.cagri.real.data.*
import com.cagri.real.ui.components.CategoryProgressStats
import com.cagri.real.ui.components.categoryProgress
import java.time.LocalDate
import kotlinx.coroutines.delay

internal data class ReminderListProjection(
    val today: LocalDate,
    val categoryProgressStats: CategoryProgressStats?,
    val customCategoriesById: Map<Long, CustomCategory>,
    val baseActive: List<ReminderItem>,
    val baseCompleted: List<ReminderItem>,
    val active: List<ReminderItem>,
    val completed: List<ReminderItem>,
    val journalGroups: List<Pair<LocalDate, List<ReminderItem>>>
)

/** Read-only projection. Owns the rolling-24-hour clock; never mutates cards. */
@Composable
internal fun rememberReminderListProjection(
    reminders: List<ReminderItem>, category: ReminderCategory?, customCategory: CustomCategory?,
    customCategories: List<CustomCategory>, categoryColorFilter: Int?, effectiveSortOrder: ReminderSortOrder
): ReminderListProjection {
    val today = rememberReminderDay()
    val categoryProgressStats = remember(reminders, category, customCategory, today) {
        categoryProgress(reminders, category, customCategory?.id, today)
    }
    val rollingDeadline = remember(reminders) {
        reminders.filter { !it.isCompleted }.mapNotNull { it.quick24HourUntilAt }.maxOrNull() ?: 0L
    }
    val usesRollingClock = rollingDeadline > System.currentTimeMillis()
    val rolling24Now = rememberReminderMinute(usesRollingClock)
    val customCategoriesById = remember(customCategories) { customCategories.associateBy { it.id } }
    
    val baseActive = remember(reminders, category, customCategory, today, customCategoriesById, rolling24Now) {
        when {
            category == ReminderCategory.Completed -> reminders.inCategory(ReminderCategory.Completed, today).filter {
                val cat = it.customCategoryId?.let { id -> customCategoriesById[id] }
                cat?.hideFromGeneralLists != true
            }
            // Journal is a first-class view: a custom category's "Yalnızca kategori içinde göster"
            // flag must not hide journal entries from Günlüklerim. The same entry can therefore
            // live in its custom category and in the journal timeline at the same time.
            category == ReminderCategory.JournalEntries -> reminders.inCategory(ReminderCategory.JournalEntries, today)
            category != null -> reminders.inCategory(category, today, nowMillis = rolling24Now).filter {
                val cat = it.customCategoryId?.let { id -> customCategoriesById[id] }
                cat?.hideFromGeneralLists != true
            }
            customCategory != null -> reminders.filter {
                it.customCategoryId == customCategory.id && !it.isCompleted
            }.sortedByDescending { it.createdAt }
            else -> reminders.filter {
                if (it.isCompleted) return@filter false
                // Cards moved into 24 hour stay on Home too (they also show in 24 hour / Planlı).
                val cat = it.customCategoryId?.let { id -> customCategoriesById[id] }
                cat?.hideFromGeneralLists != true
            }
        }
    }
    val active = remember(baseActive, categoryColorFilter, effectiveSortOrder, category, customCategory) {
        val effectiveColorFilter = if (category != null || customCategory != null) categoryColorFilter else null
        filterAndSortReminders(baseActive, effectiveColorFilter, effectiveSortOrder)
    }
    val journalGroups = remember(active, category) {
        if (category != ReminderCategory.JournalEntries) return@remember emptyList()
        active.filter { it.journal != null }
            .sortedWith(compareByDescending<ReminderItem> { it.journal?.createdAt ?: 0L }
                .thenByDescending { it.createdAt })
            .groupBy { item ->
                java.time.Instant.ofEpochMilli(item.journal!!.createdAt)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
            }
            .entries
            .sortedByDescending { it.key }
            .map { (date, items) ->
                date to items.sortedByDescending { it.journal?.createdAt ?: it.createdAt }
            }
    }
    val baseCompleted = remember(reminders, category, customCategory, customCategoriesById) {
        when {
            category == null && customCategory == null ->
                reminders.inCategory(ReminderCategory.Completed)
                    .filter {
                        val cat = it.customCategoryId?.let { id -> customCategoriesById[id] }
                        cat?.hideFromGeneralLists != true
                    }
                    .sortedByDescending { it.completedAt ?: it.createdAt }
            customCategory != null -> reminders.filter {
                it.customCategoryId == customCategory.id && it.isCompleted
            }.sortedByDescending { it.completedAt ?: it.createdAt }
            else -> emptyList()
        }
    }
    val completed = remember(baseCompleted, categoryColorFilter, effectiveSortOrder, category, customCategory) {
        val effectiveColorFilter = if (category != null || customCategory != null) categoryColorFilter else null
        filterAndSortReminders(baseCompleted, effectiveColorFilter, effectiveSortOrder)
    }
    return ReminderListProjection(today, categoryProgressStats, customCategoriesById,
        baseActive, baseCompleted, active, completed, journalGroups)
}
