package com.cagri.real.speech

import com.cagri.real.data.CustomCategory
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.VoiceBulkShift
import com.cagri.real.data.reminderTimestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A bulk request turned into concrete card ids / moves, waiting for the user's confirmation chip. */
data class VoiceBulkPlan(
    val operation: VoiceBulkOperation,
    val cardIds: List<Long>,
    val shifts: List<VoiceBulkShift>,
    val question: String,
    val doneMessage: String
)

/**
 * Selects the cards a bulk voice command means from the FULL local list (not just the 15 the AI saw),
 * and prepares the exact changes. Pure function: nothing is modified until the user confirms.
 */
object VoiceBulkResolver {
    private val months = listOf(
        "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
        "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
    )

    fun resolve(
        bulk: VoiceBulk,
        reminders: List<ReminderItem>,
        categories: List<CustomCategory>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Result<VoiceBulkPlan> {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        fun dayOf(card: ReminderItem): LocalDate? =
            card.alertAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                ?: card.plannedDateEpochDay?.let { LocalDate.ofEpochDay(it) }
        fun isOverdue(card: ReminderItem): Boolean =
            card.alertAt?.let { it < nowMillis } ?: card.plannedDateEpochDay?.let { it < today.toEpochDay() } ?: false

        val open = reminders.filter { !it.isCompleted && it.text.isNotBlank() }
        val categoryId: Long? = if (bulk.categoryName.isBlank()) null else {
            categories.firstOrNull { it.name.trim().equals(bulk.categoryName.trim(), ignoreCase = true) }?.id
                ?: return Result.failure(IllegalStateException("\"${bulk.categoryName}\" kategorisi bulunamadı."))
        }
        val explicitDate = runCatching { LocalDate.parse(bulk.date) }.getOrNull()
        val newDate = runCatching { LocalDate.parse(bulk.newDate) }.getOrNull()

        val scoped = when (bulk.scope) {
            VoiceBulkScope.TODAY -> open.filter { dayOf(it) == today }
            VoiceBulkScope.TOMORROW -> open.filter { dayOf(it) == today.plusDays(1) }
            VoiceBulkScope.OVERDUE -> open.filter { isOverdue(it) }
            VoiceBulkScope.DATE -> {
                val day = explicitDate ?: return Result.failure(IllegalStateException("Hangi günü kastettiğini anlayamadım."))
                open.filter { dayOf(it) == day }
            }
            VoiceBulkScope.CATEGORY -> open.filter { it.customCategoryId == categoryId }
            VoiceBulkScope.LISTED -> {
                val ids = bulk.targetIds.toSet()
                open.filter { it.id in ids }
            }
            VoiceBulkScope.NONE -> return Result.failure(IllegalStateException("Toplu işlemi anlayamadım."))
        }
        val cards = if (categoryId != null && bulk.scope != VoiceBulkScope.CATEGORY) {
            scoped.filter { it.customCategoryId == categoryId }
        } else scoped
        if (cards.isEmpty()) return Result.failure(IllegalStateException("Uygun kart bulunamadı."))

        val scopeLabel = when (bulk.scope) {
            VoiceBulkScope.TODAY -> "Bugünkü"
            VoiceBulkScope.TOMORROW -> "Yarınki"
            VoiceBulkScope.OVERDUE -> "Geciken"
            VoiceBulkScope.DATE -> explicitDate?.let { "${it.dayOfMonth} ${months[it.monthValue - 1]} tarihli" } ?: ""
            VoiceBulkScope.CATEGORY -> "${bulk.categoryName} kategorisindeki"
            else -> "Seçilen"
        }
        val prefix = "$scopeLabel ${cards.size} kart".trim()

        return when (bulk.operation) {
            VoiceBulkOperation.COMPLETE -> Result.success(
                VoiceBulkPlan(bulk.operation, cards.map { it.id }, emptyList(), "$prefix tamamlansın mı?", "${cards.size} kart tamamlandı.")
            )
            VoiceBulkOperation.DELETE -> Result.success(
                VoiceBulkPlan(bulk.operation, cards.map { it.id }, emptyList(),
                    "$prefix silinsin mi?" + cards.count { it.hasRepeat }.takeIf { it > 0 }?.let {
                        " $it tekrarlı kartın tüm serisi de silinecek."
                    }.orEmpty(), "${cards.size} kart silindi.")
            )
            VoiceBulkOperation.SHIFT -> {
                if (newDate == null && bulk.shiftDays < 1) {
                    return Result.failure(IllegalStateException("Kaç gün ileri taşıyacağımı anlayamadım."))
                }
                fun target(day: LocalDate): LocalDate = newDate ?: day.plusDays(bulk.shiftDays.toLong())
                val shifts = mutableListOf<VoiceBulkShift>()
                var skipped = 0
                for (card in cards) {
                    val alert = card.alertAt
                    if (alert != null) {
                        val zdt = Instant.ofEpochMilli(alert).atZone(zone)
                        val millis = reminderTimestamp(target(zdt.toLocalDate()), zdt.hour, zdt.minute, zone)
                        // Never move a card into the past.
                        if (millis == null || millis <= nowMillis) skipped++ else shifts += VoiceBulkShift(card.id, alertAt = millis)
                    } else {
                        val planned = card.plannedDateEpochDay
                        val day = planned?.let { target(LocalDate.ofEpochDay(it)) }
                        if (day == null || day.isBefore(today)) skipped++ else shifts += VoiceBulkShift(card.id, plannedEpochDay = day.toEpochDay())
                    }
                }
                if (shifts.isEmpty()) return Result.failure(IllegalStateException("Taşınabilecek uygun kart yok."))
                val dest = when {
                    newDate == today.plusDays(1) -> "yarına"
                    newDate == today -> "bugüne"
                    newDate != null -> "${newDate.dayOfMonth} ${months[newDate.monthValue - 1]} tarihine"
                    bulk.shiftDays == 7 -> "1 hafta sonraya"
                    else -> "${bulk.shiftDays} gün sonraya"
                }
                val note = if (skipped > 0) " ($skipped kart geçmişe düşeceği için atlanacak)" else ""
                Result.success(
                    VoiceBulkPlan(
                        bulk.operation, shifts.map { it.id }, shifts,
                        "$scopeLabel ${cards.size} kart $dest taşınsın mı?$note".trim(),
                        "${shifts.size} kart taşındı."
                    )
                )
            }
            VoiceBulkOperation.NONE -> Result.failure(IllegalStateException("Toplu işlemi anlayamadım."))
        }
    }
}
