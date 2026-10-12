package com.cagri.real.speech

import com.cagri.real.data.CustomCategory
import com.cagri.real.data.RecurrenceRule
import com.cagri.real.data.ReminderEditPatch
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.ReminderLocation
import com.cagri.real.data.reminderTimestamp
import com.cagri.real.data.firstPatternOccurrence
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Turns the backend's loose "edit" answer into a concrete [ReminderEditPatch] for one existing card,
 * or a short Turkish error message that can be shown as a toast. Pure function: easy to unit test.
 */
object VoiceEditResolver {

    fun resolve(
        item: ReminderItem,
        edit: VoiceEditPatch<LocalDate, LocalTime>,
        categories: List<CustomCategory>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Result<ReminderEditPatch> {
        var patch = ReminderEditPatch()

        // Title
        edit.newTitle?.trim()?.takeIf { it.isNotBlank() }?.let { patch = patch.copy(text = it) }

        // Date / time: a bare time ("onu 8'e al") keeps the card's day; a bare date keeps its clock time.
        var effectiveAlert = item.alertAt
        if (edit.date != null && edit.time == null && item.alertAt == null) {
            // Card without a clock time (date-only or undated): move just the day, never invent a time.
            val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            if (edit.date.isBefore(today)) {
                return Result.failure(IllegalStateException("Bu tarih geçmişte kaldı."))
            }
            patch = patch.copy(setPlannedDate = true, plannedEpochDay = edit.date.toEpochDay())
        } else if (edit.date != null || edit.time != null) {
            val currentZdt = item.alertAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
            val baseDate = edit.date
                ?: currentZdt?.toLocalDate()
                ?: item.plannedDateEpochDay?.let { LocalDate.ofEpochDay(it) }
                ?: Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().takeIf { edit.weekdaysMask != 0 }
                ?: return Result.failure(IllegalStateException("Hangi güne alayım?"))
            val baseTime = edit.time ?: currentZdt?.toLocalTime()
                ?: return Result.failure(IllegalStateException("Hangi saate alayım?"))
            val millis = if (edit.weekdaysMask != 0) {
                val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
                firstPatternOccurrence(maxOf(edit.date ?: today, today), baseTime, edit.weekdaysMask, nowMillis, zone)
            } else reminderTimestamp(baseDate, baseTime.hour, baseTime.minute, zone)
            if (millis == null) return Result.failure(IllegalStateException("Bu saat geçerli değil."))
            val chosen = Instant.ofEpochMilli(millis).atZone(zone)
            if (chosen.toLocalTime() != baseTime || zone.rules.getValidOffsets(chosen.toLocalDate().atTime(baseTime)).size != 1) {
                return Result.failure(IllegalStateException("Bu saat belirsiz veya geçerli değil. Başka bir saat seç."))
            }
            if (millis <= nowMillis) {
                return Result.failure(IllegalStateException("Bu zaman geçmişte kaldı."))
            }
            patch = patch.copy(setAlert = true, alertAt = millis)
            effectiveAlert = millis
        }

        // Repeat
        val wantsRepeat = edit.recurrence != null || edit.weekdaysMask != 0 || edit.clearRepeat
        if (edit.clearRepeat) {
            patch = patch.copy(setRepeat = true, repeatRule = RecurrenceRule.NONE)
        } else if (wantsRepeat || edit.repeatUntil != null) {
            if (effectiveAlert == null) {
                return Result.failure(IllegalStateException("Tekrar için önce bir saat gerekiyor."))
            }
            val rule = when {
                edit.weekdaysMask != 0 -> RecurrenceRule.WEEKLY
                edit.recurrence != null -> edit.recurrence
                item.hasRepeat -> item.repeatRule
                else -> return Result.failure(IllegalStateException("Ne sıklıkla tekrarlasın?"))
            }
            val mask = when {
                edit.weekdaysMask != 0 -> edit.weekdaysMask
                edit.recurrence == null && rule == RecurrenceRule.WEEKLY -> item.repeatWeekdaysMask
                else -> 0
            }
            if (edit.recurrence == RecurrenceRule.DAILY && edit.weekdaysMask == 0 && edit.date == null) {
                val clock = edit.time ?: Instant.ofEpochMilli(effectiveAlert).atZone(zone).toLocalTime()
                var day = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
                var aligned = reminderTimestamp(day, clock.hour, clock.minute, zone)
                    ?: return Result.failure(IllegalStateException("Bu saat geçerli değil."))
                if (aligned <= nowMillis) {
                    day = day.plusDays(1)
                    aligned = reminderTimestamp(day, clock.hour, clock.minute, zone)
                        ?: return Result.failure(IllegalStateException("Bu saat geçerli değil."))
                }
                if (zone.rules.getValidOffsets(day.atTime(clock)).size != 1) {
                    return Result.failure(IllegalStateException("Bu saat belirsiz veya geçerli değil. Başka bir saat seç."))
                }
                patch = patch.copy(setAlert = true, alertAt = aligned)
                effectiveAlert = aligned
            }
            if (edit.weekdaysMask != 0) {
                val clock = edit.time ?: Instant.ofEpochMilli(effectiveAlert).atZone(zone).toLocalTime()
                val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
                val start = edit.date ?: today
                val aligned = firstPatternOccurrence(maxOf(start, today), clock, mask, nowMillis, zone)
                    ?: return Result.failure(IllegalStateException("Bu günler için uygun tekrar bulunamadı."))
                val chosen = Instant.ofEpochMilli(aligned).atZone(zone)
                if (chosen.toLocalTime() != clock || zone.rules.getValidOffsets(chosen.toLocalDate().atTime(clock)).size != 1) {
                    return Result.failure(IllegalStateException("Bu saat belirsiz veya geçerli değil. Başka bir saat seç."))
                }
                patch = patch.copy(setAlert = true, alertAt = aligned)
                effectiveAlert = aligned
            }
            val alertDay = Instant.ofEpochMilli(effectiveAlert).atZone(zone).toLocalDate().toEpochDay()
            // A frequency/day correction must not accidentally turn a one-month series into an unlimited one.
            val until = edit.repeatUntil?.toEpochDay() ?: item.repeatUntilEpochDay
            if (until != null && until < alertDay) {
                return Result.failure(IllegalStateException("Tekrarın bitiş tarihi yeni ilk tekrardan önce. Bitiş tarihini de değiştir."))
            }
            patch = patch.copy(setRepeat = true, repeatRule = rule, weekdaysMask = mask, untilEpochDay = until)
        }

        // Location
        if (edit.clearLocation) {
            patch = patch.copy(setLocation = true, location = null)
        } else {
            edit.locationName?.trim()?.takeIf { it.isNotBlank() }?.let { placeName ->
                val encoded = java.net.URLEncoder.encode(placeName, Charsets.UTF_8.name())
                patch = patch.copy(
                    setLocation = true,
                    location = ReminderLocation(
                        placeName = placeName,
                        mapsUrl = "https://www.google.com/maps/search/?api=1&query=$encoded"
                    )
                )
            }
        }

        // Category
        edit.categoryName?.trim()?.takeIf { it.isNotBlank() }?.let { wanted ->
            val match = categories.firstOrNull { it.name.trim().equals(wanted, ignoreCase = true) }
                ?: return Result.failure(IllegalStateException("\"$wanted\" kategorisi bulunamadı."))
            patch = patch.copy(setCategory = true, customCategoryId = match.id)
        }

        return if (patch.isEmpty) {
            Result.failure(IllegalStateException("Neyi değiştireceğimi anlayamadım."))
        } else Result.success(patch)
    }

    /** One-line confirmation for the toast after a successful edit. */
    fun confirmation(patch: ReminderEditPatch, categoryName: String?): String {
        val parts = buildList {
            if (patch.text != null) add("başlık")
            if (patch.setAlert || patch.setPlannedDate) add("zaman")
            if (patch.setRepeat) add(if (patch.repeatRule == RecurrenceRule.NONE) "tekrar kaldırıldı" else "tekrar")
            if (patch.setLocation) add(if (patch.location == null) "konum kaldırıldı" else "konum")
            if (patch.setCategory) add(categoryName?.let { "$it kategorisi" } ?: "kategori")
        }
        return parts.joinToString(", ").replaceFirstChar { it.uppercase() } + " güncellendi."
    }
}
