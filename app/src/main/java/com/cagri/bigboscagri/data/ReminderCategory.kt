package com.cagri.bigboscagri.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ReminderCategory(val title: String, val emptyMessage: String) {
    Today("Bugün", "Bugün için planlanan hatırlatıcı yok"),
    Planned("Planlı", "Henüz planlanan hatırlatıcı yok"),
    Important("Önemli", "Yıldız eklediğin hatırlatıcılar burada görünür"),
    NoAlert("Uyarı yok", "Henüz hatırlatıcı yok"),
    Completed("Tamamlananlar", "Tamamladığın hatırlatıcılar burada görünür")
}

fun List<ReminderItem>.inCategory(
    category: ReminderCategory,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault()
): List<ReminderItem> = filter { item ->
    when (category) {
        ReminderCategory.Completed -> item.isCompleted
        ReminderCategory.NoAlert -> !item.isCompleted && item.alertAt == null
        ReminderCategory.Important -> !item.isCompleted && item.isImportant
        ReminderCategory.Planned -> !item.isCompleted && item.alertAt != null
        ReminderCategory.Today -> !item.isCompleted && item.alertAt?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today
        } == true
    }
}.let { items ->
    if (category == ReminderCategory.Completed) items.sortedByDescending { it.completedAt }
    else items.sortedByDescending { it.createdAt }
}

/** Counts without allocating and sorting five filtered lists on every edit. */
fun List<ReminderItem>.categoryCounts(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Map<ReminderCategory, Int> {
    val counts = ReminderCategory.entries.associateWith { 0 }.toMutableMap()
    fun add(category: ReminderCategory) { counts[category] = counts.getValue(category) + 1 }
    forEach { item ->
        if (item.isCompleted) add(ReminderCategory.Completed)
        else {
            if (item.isImportant) add(ReminderCategory.Important)
            if (item.alertAt == null) add(ReminderCategory.NoAlert)
            else {
                add(ReminderCategory.Planned)
                if (Instant.ofEpochMilli(item.alertAt).atZone(zone).toLocalDate() == today) add(ReminderCategory.Today)
            }
        }
    }
    return counts
}
