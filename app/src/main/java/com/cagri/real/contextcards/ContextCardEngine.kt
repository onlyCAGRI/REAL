package com.cagri.real.contextcards

import com.cagri.real.data.JournalMood
import java.time.DayOfWeek
import java.time.ZonedDateTime

enum class ContextCardType(val key: String) {
    WATER("water"), MOOD("mood"), BOOK("book"), MEDIA("media");

    companion object {
        fun fromKey(key: String?): ContextCardType? = entries.firstOrNull { it.key == key }
    }
}

/** What the engine knows about "right now". Pure data: no Android, no I/O. */
data class ContextCardSignals(
    val now: ZonedDateTime,
    /** null = water data not available (not loaded / unreadable) -> water card is never shown. */
    val waterTodayMl: Int? = null,
    val waterGoalMl: Int = 2_000,
    val moodLoggedToday: Boolean = false,
    val lastMoodToday: JournalMood? = null,
    val unfinishedBookTitle: String? = null,
    val unfinishedBookAuthor: String? = null
)

data class ContextCard(
    val type: ContextCardType,
    val eyebrow: String,
    val title: String,
    val message: String,
    val primaryLabel: String,
    val secondaryLabel: String = "Şimdi değil",
    /** 0..1, only used by the water card. */
    val progress: Float? = null
)

/**
 * Persisted bookkeeping that keeps cards rare. All transitions are pure so they can be unit tested.
 * Times are epoch millis, [epochDay] is LocalDate.toEpochDay().
 */
data class ContextCardHistory(
    val enabled: Boolean = true,
    val epochDay: Long = Long.MIN_VALUE,
    val shownToday: Map<String, Int> = emptyMap(),
    val lastActivationAt: Long = 0L,
    val activeType: String? = null,
    val activeSince: Long = 0L,
    val lastType: String? = null,
    val snoozeUntil: Map<String, Long> = emptyMap(),
    val dismissStreak: Map<String, Int> = emptyMap(),
    /** Test hook (Settings): show this card type right now, ignoring time/limit rules, until it is acted on. */
    val forcedType: String? = null
) {
    fun rolled(day: Long): ContextCardHistory =
        if (epochDay == day) this else copy(epochDay = day, shownToday = emptyMap())

    fun activated(type: ContextCardType, nowMs: Long, day: Long): ContextCardHistory {
        val base = rolled(day)
        return base.copy(
            shownToday = base.shownToday + (type.key to ((base.shownToday[type.key] ?: 0) + 1)),
            lastActivationAt = nowMs,
            activeType = type.key,
            activeSince = nowMs,
            lastType = type.key
        )
    }

    /** User pressed "Şimdi değil". Three dismissals in a row silence that card type for days. */
    fun dismissed(type: ContextCardType, nowMs: Long): ContextCardHistory {
        val streak = (dismissStreak[type.key] ?: 0) + 1
        val limitHit = streak >= ContextCardEngine.STREAK_LIMIT
        return copy(
            activeType = if (activeType == type.key) null else activeType,
            forcedType = if (forcedType == type.key) null else forcedType,
            dismissStreak = dismissStreak + (type.key to if (limitHit) 0 else streak),
            snoozeUntil = snoozeUntil + (type.key to nowMs + if (limitHit) ContextCardEngine.STREAK_SNOOZE_MS else ContextCardEngine.DISMISS_SNOOZE_MS)
        )
    }

    /** User acted on the card (opened the module / added water). */
    fun completed(type: ContextCardType, nowMs: Long): ContextCardHistory = copy(
        activeType = if (activeType == type.key) null else activeType,
        forcedType = if (forcedType == type.key) null else forcedType,
        dismissStreak = dismissStreak + (type.key to 0),
        snoozeUntil = snoozeUntil + (type.key to nowMs + ContextCardEngine.COMPLETE_SNOOZE_MS)
    )
}

object ContextCardEngine {
    private const val HOUR_MS = 3_600_000L
    const val ACTIVE_WINDOW_MS = 2 * HOUR_MS
    const val MIN_GAP_MS = 3 * HOUR_MS
    const val MAX_PER_DAY = 2
    const val DISMISS_SNOOZE_MS = 4 * HOUR_MS
    const val COMPLETE_SNOOZE_MS = 12 * HOUR_MS
    const val STREAK_LIMIT = 3
    const val STREAK_SNOOZE_MS = 3 * 24 * HOUR_MS
    private const val SAME_TYPE_PENALTY = 25

    /** [card] is what to render; [activate] is set only when a brand-new card starts (caller must persist it). */
    data class Decision(val card: ContextCard?, val activate: ContextCardType?)

    private val NONE = Decision(null, null)
    private val LOW_MOODS = setOf(
        JournalMood.TIRED, JournalMood.BORED, JournalMood.SAD, JournalMood.LOW,
        JournalMood.STRESSED, JournalMood.HARD, JournalMood.BAD
    )

    fun decide(signals: ContextCardSignals, history: ContextCardHistory): Decision {
        if (!history.enabled) return NONE
        ContextCardType.fromKey(history.forcedType)?.let { return Decision(build(it, signals), null) }
        val nowMs = signals.now.toInstant().toEpochMilli()
        val h = history.rolled(signals.now.toLocalDate().toEpochDay())

        // 1) A card that is already on screen stays (sticky) until it is acted on, dismissed or expires.
        val active = ContextCardType.fromKey(h.activeType)
        if (active != null && (nowMs - h.activeSince) in 0 until ACTIVE_WINDOW_MS && isEligible(active, signals)) {
            return Decision(build(active, signals), null)
        }

        // 2) Otherwise decide whether a new card is allowed at all.
        if (h.shownToday.values.sum() >= MAX_PER_DAY) return NONE
        if ((nowMs - h.lastActivationAt) in 0 until MIN_GAP_MS) return NONE

        val best = ContextCardType.entries
            .filter { (h.shownToday[it.key] ?: 0) == 0 }
            .filter { (h.snoozeUntil[it.key] ?: 0L) <= nowMs }
            .filter { isEligible(it, signals) }
            .maxByOrNull { score(it, signals) - if (it.key == h.lastType) SAME_TYPE_PENALTY else 0 }
            ?: return NONE
        return Decision(build(best, signals), best)
    }

    private fun isLowMood(s: ContextCardSignals) = s.lastMoodToday?.let { it in LOW_MOODS } == true

    private fun minutes(s: ContextCardSignals) = s.now.hour * 60 + s.now.minute

    fun isEligible(type: ContextCardType, s: ContextCardSignals): Boolean {
        val m = minutes(s)
        return when (type) {
            ContextCardType.WATER -> {
                val ml = s.waterTodayMl ?: return false
                val goal = s.waterGoalMl.coerceAtLeast(1)
                val threshold = if (m >= 17 * 60) 0.75f else 0.5f
                m in (11 * 60) until (21 * 60) && ml.toFloat() / goal < threshold
            }
            ContextCardType.MOOD -> !s.moodLoggedToday && m in (12 * 60) until (23 * 60)
            ContextCardType.BOOK -> s.unfinishedBookTitle != null && m in (20 * 60)..(23 * 60 + 30)
            ContextCardType.MEDIA -> m in (17 * 60)..(23 * 60 + 30) && (isWeekendEvening(s) || isLowMood(s))
        }
    }

    private fun isWeekendEvening(s: ContextCardSignals) =
        s.now.dayOfWeek in setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) && minutes(s) >= 18 * 60

    private fun score(type: ContextCardType, s: ContextCardSignals): Int = when (type) {
        ContextCardType.WATER -> {
            val progress = (s.waterTodayMl ?: 0).toFloat() / s.waterGoalMl.coerceAtLeast(1)
            60 + ((1f - progress.coerceIn(0f, 1f)) * 20).toInt() + if (minutes(s) >= 15 * 60) 10 else 0
        }
        ContextCardType.MOOD -> 55 + if (minutes(s) >= 18 * 60) 15 else 0
        ContextCardType.BOOK -> 50 + if (isLowMood(s)) 10 else 0
        ContextCardType.MEDIA -> 45 + if (isLowMood(s)) 20 else 0
    }

    fun build(type: ContextCardType, s: ContextCardSignals): ContextCard = when (type) {
        ContextCardType.WATER -> {
            val ml = s.waterTodayMl ?: 0
            val goal = s.waterGoalMl.coerceAtLeast(1)
            ContextCard(
                type = type, eyebrow = "Bugün",
                title = if (ml == 0) "Bugün su tarafı sessiz" else "Su hedefinin biraz gerisindesin",
                message = if (ml == 0) "Henüz hiç su içmedin. Bir bardakla başlayalım."
                else "$ml / $goal ml. Bir bardak iyi gelir.",
                primaryLabel = "+250 ml",
                progress = (ml.toFloat() / goal).coerceIn(0f, 1f)
            )
        }
        ContextCardType.MOOD -> ContextCard(
            type = type, eyebrow = "Bugün",
            title = "Bugün nasıl hissediyorsun?",
            message = "Günün havasını tek dokunuşla kaydet.",
            primaryLabel = "Kaydet"
        )
        ContextCardType.BOOK -> ContextCard(
            type = type, eyebrow = "Akşam",
            title = "Kaldığın yerden devam",
            message = listOfNotNull(s.unfinishedBookTitle, s.unfinishedBookAuthor?.takeIf { it.isNotBlank() })
                .joinToString(" · ").ifBlank { "Kitaplığındaki kitap" } + " seni bekliyor.",
            primaryLabel = "Oku"
        )
        ContextCardType.MEDIA -> ContextCard(
            type = type, eyebrow = "Akşam",
            title = "Bu akşam ne izlesek?",
            message = if (isLowMood(s)) "Yorucu bir gün gibi. Hafif bir şey bulalım."
            else "Türünü seç, afişleri keşfet.",
            primaryLabel = "Keşfet"
        )
    }
}
