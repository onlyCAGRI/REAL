package com.cagri.real.contextcards

import com.cagri.real.i18n.t
import com.cagri.real.data.JournalMood
import java.time.DayOfWeek
import java.time.ZonedDateTime

enum class ContextCardType(val key: String) {
    WATER("water"), MOOD("mood"), BOOK("book"), MEDIA("media"),
    WATCH("watch"), LISTEN("listen"), PLAY("play"), JOURNAL("journal");

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
    val unfinishedBookAuthor: String? = null,
    /** When the app was first installed (epoch millis); null = unknown, no warm-up quiet. */
    val installedAt: Long? = null,
    /** The series rated most recently in "Puanlarım" and its last rated episode (season, number). */
    val seriesInProgress: String? = null,
    val lastEpisode: Pair<Int, Int>? = null,
    /** null = journal data unknown -> the journal card is never shown. */
    val journalWrittenToday: Boolean? = null
)

data class ContextCard(
    val type: ContextCardType,
    val eyebrow: String,
    val title: String,
    val message: String,
    val primaryLabel: String,
    val secondaryLabel: String = t("Şimdi değil"),
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
    /** Cards currently in the Home row: type key -> since when (epoch millis). */
    val active: Map<String, Long> = emptyMap(),
    val lastType: String? = null,
    val snoozeUntil: Map<String, Long> = emptyMap(),
    val dismissStreak: Map<String, Int> = emptyMap(),
    /**
     * Test hook (Settings): show this card type right now, ignoring time/limit rules, until it is acted on.
     * [ContextCardEngine.FORCE_ALL] shows every type; [forcedDone] holds the ones already acted on.
     */
    val forcedType: String? = null,
    val forcedDone: Set<String> = emptySet()
) {
    fun rolled(day: Long): ContextCardHistory =
        if (epochDay == day) this else copy(epochDay = day, shownToday = emptyMap())

    fun activated(type: ContextCardType, nowMs: Long, day: Long): ContextCardHistory = activated(listOf(type), nowMs, day)

    /** A new batch of cards joins the Home row together; entries older than the active window are dropped. */
    fun activated(types: List<ContextCardType>, nowMs: Long, day: Long): ContextCardHistory {
        if (types.isEmpty()) return this
        val base = rolled(day)
        return base.copy(
            shownToday = base.shownToday + types.map { it.key to ((base.shownToday[it.key] ?: 0) + 1) },
            lastActivationAt = nowMs,
            active = base.active.filterValues { nowMs - it in 0 until ContextCardEngine.ACTIVE_WINDOW_MS } + types.map { it.key to nowMs },
            lastType = types.first().key
        )
    }

    /** User pressed "Şimdi değil". Three dismissals in a row silence that card type for days. */
    fun dismissed(type: ContextCardType, nowMs: Long): ContextCardHistory {
        val streak = (dismissStreak[type.key] ?: 0) + 1
        val limitHit = streak >= ContextCardEngine.STREAK_LIMIT
        return actedOn(type).copy(
            dismissStreak = dismissStreak + (type.key to if (limitHit) 0 else streak),
            snoozeUntil = snoozeUntil + (type.key to nowMs + if (limitHit) ContextCardEngine.STREAK_SNOOZE_MS else ContextCardEngine.DISMISS_SNOOZE_MS)
        )
    }

    /** User acted on the card (opened the module / added water). */
    fun completed(type: ContextCardType, nowMs: Long): ContextCardHistory = actedOn(type).copy(
        dismissStreak = dismissStreak + (type.key to 0),
        snoozeUntil = snoozeUntil + (type.key to nowMs + ContextCardEngine.COMPLETE_SNOOZE_MS)
    )

    private fun actedOn(type: ContextCardType): ContextCardHistory = when (forcedType) {
        type.key -> copy(active = active - type.key, forcedType = null, forcedDone = emptySet())
        ContextCardEngine.FORCE_ALL -> {
            val done = forcedDone + type.key
            if (ContextCardType.entries.all { it.key in done }) copy(active = active - type.key, forcedType = null, forcedDone = emptySet())
            else copy(active = active - type.key, forcedDone = done)
        }
        else -> copy(active = active - type.key)
    }
}

object ContextCardEngine {
    private const val HOUR_MS = 3_600_000L
    const val ACTIVE_WINDOW_MS = 2 * HOUR_MS
    /** A new card comes at most once per this gap. */
    const val MIN_GAP_MS = 3 * HOUR_MS
    /** At most two cards a day, each on its own (never a batch). */
    const val MAX_PER_DAY = 2
    const val MAX_VISIBLE = 1
    const val DISMISS_SNOOZE_MS = 4 * HOUR_MS
    const val COMPLETE_SNOOZE_MS = 12 * HOUR_MS
    const val STREAK_LIMIT = 3
    const val STREAK_SNOOZE_MS = 3 * 24 * HOUR_MS
    /** A fresh install stays quiet for its first day: no "you haven't had water" on the very first open. */
    const val INSTALL_QUIET_MS = 24 * HOUR_MS
    const val FORCE_ALL = "all"
    private const val SAME_TYPE_PENALTY = 25

    /** [cards] is the row to render (best first); [activate] lists the cards that just started (caller must persist them). */
    data class Decision(val cards: List<ContextCard>, val activate: List<ContextCardType>) {
        val card: ContextCard? get() = cards.firstOrNull()
    }

    private val NONE = Decision(emptyList(), emptyList())
    private val LOW_MOODS = setOf(
        JournalMood.TIRED, JournalMood.BORED, JournalMood.SAD, JournalMood.LOW,
        JournalMood.STRESSED, JournalMood.HARD, JournalMood.BAD
    )

    fun decide(signals: ContextCardSignals, history: ContextCardHistory): Decision {
        if (!history.enabled) return NONE
        if (history.forcedType == FORCE_ALL) {
            return Decision(ContextCardType.entries.filter { it.key !in history.forcedDone }.map { build(it, signals) }, emptyList())
        }
        ContextCardType.fromKey(history.forcedType)?.let { return Decision(listOf(build(it, signals)), emptyList()) }
        val nowMs = signals.now.toInstant().toEpochMilli()
        signals.installedAt?.let { if (nowMs - it in 0 until INSTALL_QUIET_MS) return NONE }
        val h = history.rolled(signals.now.toLocalDate().toEpochDay())

        // 1) Cards already in the row stay (sticky) until they are acted on, dismissed or expire.
        val sticky = h.active
            .filter { (_, since) -> nowMs - since in 0 until ACTIVE_WINDOW_MS }
            .keys.mapNotNull { ContextCardType.fromKey(it) }
            .filter { isEligible(it, signals) }

        // 2) A new card only when the slot is free, within the daily budget and after the gap.
        val room = minOf(MAX_VISIBLE - sticky.size, MAX_PER_DAY - h.shownToday.values.sum())
        val fresh = if (room <= 0 || (nowMs - h.lastActivationAt) in 0 until MIN_GAP_MS) emptyList()
        else ContextCardType.entries
            .filter { it !in sticky }
            .filter { (h.shownToday[it.key] ?: 0) == 0 }
            .filter { (h.snoozeUntil[it.key] ?: 0L) <= nowMs }
            .filter { isEligible(it, signals) }
            .sortedByDescending { score(it, signals) - if (it.key == h.lastType) SAME_TYPE_PENALTY else 0 }
            .take(room)
        val row = (sticky + fresh).sortedByDescending { score(it, signals) }
        return Decision(row.map { build(it, signals) }, fresh)
    }

    private fun isLowMood(s: ContextCardSignals) = s.lastMoodToday?.let { it in LOW_MOODS } == true

    private fun minutes(s: ContextCardSignals) = s.now.hour * 60 + s.now.minute

    private fun isWeekend(s: ContextCardSignals) = s.now.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

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
            ContextCardType.WATCH -> s.seriesInProgress != null && s.lastEpisode != null && m in (19 * 60)..(23 * 60 + 30)
            // Commute hours: on the way in, on the way back.
            ContextCardType.LISTEN -> m in (8 * 60) until (10 * 60) || m in (17 * 60) until (19 * 60 + 30)
            ContextCardType.PLAY -> if (isWeekend(s)) m in (14 * 60) until (22 * 60) else m in (20 * 60 + 30) until (23 * 60)
            ContextCardType.JOURNAL -> s.journalWrittenToday == false && m >= 21 * 60
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
        ContextCardType.WATCH -> 58
        ContextCardType.JOURNAL -> 52
        ContextCardType.LISTEN -> 40
        ContextCardType.PLAY -> 38 + if (isLowMood(s)) 10 else 0
    }

    fun build(type: ContextCardType, s: ContextCardSignals): ContextCard = when (type) {
        ContextCardType.WATER -> {
            val ml = s.waterTodayMl ?: 0
            val goal = s.waterGoalMl.coerceAtLeast(1)
            ContextCard(
                type = type, eyebrow = t("Bugün"),
                title = if (ml == 0) t("Bugün su tarafı sessiz") else t("Su hedefinin biraz gerisindesin"),
                message = if (ml == 0) t("Henüz hiç su içmedin. Bir bardakla başlayalım.")
                else t("{0} / {1} ml. Bir bardak iyi gelir.", ml, goal),
                primaryLabel = "+250 ml",
                progress = (ml.toFloat() / goal).coerceIn(0f, 1f)
            )
        }
        ContextCardType.MOOD -> ContextCard(
            type = type, eyebrow = t("Bugün"),
            title = t("Bugün nasıl hissediyorsun?"),
            message = t("Günün havasını tek dokunuşla kaydet."),
            primaryLabel = t("Kaydet")
        )
        ContextCardType.BOOK -> ContextCard(
            type = type, eyebrow = t("Akşam"),
            title = t("Kaldığın yerden devam"),
            message = listOfNotNull(s.unfinishedBookTitle, s.unfinishedBookAuthor?.takeIf { it.isNotBlank() })
                .joinToString(" · ").ifBlank { t("Kitaplığındaki kitap") } + t(" seni bekliyor."),
            primaryLabel = t("Oku")
        )
        ContextCardType.MEDIA -> ContextCard(
            type = type, eyebrow = t("Akşam"),
            title = t("Bu akşam ne izlesek?"),
            message = if (isLowMood(s)) t("Yorucu bir gün gibi. Hafif bir şey bulalım.")
            else t("Türünü seç, afişleri keşfet."),
            primaryLabel = t("Keşfet")
        )
        ContextCardType.WATCH -> ContextCard(
            type = type, eyebrow = t("Akşam"),
            title = t("Sıradaki bölüm"),
            message = s.seriesInProgress?.let { series ->
                s.lastEpisode?.let { (season, number) -> t("{0} · {1}. sezon {2}. bölüm seni bekliyor.", series, season, number + 1) }
                    ?: t("{0} seni bekliyor.", series)
            } ?: t("Yarım kalan dizin seni bekliyor."),
            primaryLabel = t("Puanlarım")
        )
        ContextCardType.LISTEN -> ContextCard(
            type = type, eyebrow = if (minutes(s) < 12 * 60) t("Sabah") else t("Yolda"),
            title = t("Ne dinlesek?"),
            message = t("Türünü seç, kartları kaydır."),
            primaryLabel = t("Keşfet")
        )
        ContextCardType.PLAY -> ContextCard(
            type = type, eyebrow = if (isWeekend(s)) t("Hafta sonu") else t("Akşam"),
            title = t("Ne oynasak?"),
            message = if (isLowMood(s)) t("Kafa dağıtacak hafif bir oyun bulalım.") else t("Bir tür seç, sana uyanları bulalım."),
            primaryLabel = t("Keşfet")
        )
        ContextCardType.JOURNAL -> ContextCard(
            type = type, eyebrow = t("Gece"),
            title = t("Bugünü iki satırla yaz"),
            message = t("Kısa bir not, yarına güzel bir iz bırakır."),
            primaryLabel = t("Yaz")
        )
    }
}
