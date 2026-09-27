package com.cagri.bigboscagri.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ReminderCategory(val title: String, val emptyMessage: String) {
    Today("Bugün", "Bugün için planlanan hatırlatıcı yok"),
    Planned("Planlı", "Henüz planlanan hatırlatıcı yok"),
    Important("Önemli", "Yıldız eklediğin hatırlatıcılar burada görünür"),
    NoAlert("Uyarı yok", "Henüz hatırlatıcı yok"),
    Completed("Tamamlananlar", "Tamamladığın hatırlatıcılar burada görünür"),
    JournalEntries("Günlüklerim", "Henüz günlük eklenmemiş")
}

fun List<ReminderItem>.inCategory(
    category: ReminderCategory,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault()
): List<ReminderItem> = filter { item ->
    when (category) {
        ReminderCategory.Completed -> item.isCompleted
        ReminderCategory.NoAlert -> item.isNoAlert
        ReminderCategory.Important -> !item.isCompleted && item.isImportant
        ReminderCategory.Planned -> !item.isCompleted && item.alertAt != null
        ReminderCategory.Today -> !item.isCompleted && item.alertAt?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today
        } == true
        ReminderCategory.JournalEntries -> item.journal != null
    }
}.let { items ->
    if (category == ReminderCategory.Completed) items.sortedByDescending { it.completedAt }
    else if (category == ReminderCategory.JournalEntries) items.sortedByDescending { it.journal?.createdAt ?: it.createdAt }
    else items.sortedByDescending { it.createdAt }
}


fun List<ReminderItem>.categoryCounts(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Map<ReminderCategory, Int> {
    val counts = ReminderCategory.entries.associateWith { 0 }.toMutableMap()
    fun add(category: ReminderCategory) { counts[category] = counts.getValue(category) + 1 }
    forEach { item ->
        if (item.journal != null) add(ReminderCategory.JournalEntries)
        if (item.isCompleted) add(ReminderCategory.Completed)
        else {
            if (item.isImportant) add(ReminderCategory.Important)
            if (item.isNoAlert) add(ReminderCategory.NoAlert)
            if (item.alertAt != null) {
                add(ReminderCategory.Planned)
                if (Instant.ofEpochMilli(item.alertAt).atZone(zone).toLocalDate() == today) add(ReminderCategory.Today)
            }
        }
    }
    return counts
}
