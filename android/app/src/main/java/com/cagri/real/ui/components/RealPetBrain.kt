package com.cagri.real.ui.components

import android.content.SharedPreferences
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * Personality / activity setting from PLAN.md Section 2 & Section 8.
 * Default is [Sakin]. When [Kapali], the pet never appears.
 */
enum class RealPetMode(
    val storageKey: String,
    val labelTr: String,
    val reactionsAndRemindersEnabled: Boolean,
    val guideEnabled: Boolean,
    val spontaneousIntervalMs: Long?,
    val maxDailyAskLove: Int,
    val maxDailyPranks: Int,
    val baseMischief: Float
) {
    Kapali(
        storageKey = "kapali",
        labelTr = "Kapalı",
        reactionsAndRemindersEnabled = false,
        guideEnabled = false,
        spontaneousIntervalMs = null,
        maxDailyAskLove = 0,
        maxDailyPranks = 0,
        baseMischief = 0f
    ),
    Sakin(
        storageKey = "sakin",
        labelTr = "Sakin",
        reactionsAndRemindersEnabled = true,
        guideEnabled = true,
        spontaneousIntervalMs = null,
        maxDailyAskLove = 1,
        maxDailyPranks = 0,
        baseMischief = 0f
    ),
    Normal(
        storageKey = "normal",
        labelTr = "Normal",
        reactionsAndRemindersEnabled = true,
        guideEnabled = true,
        spontaneousIntervalMs = 10 * 60_000L,
        maxDailyAskLove = 3,
        maxDailyPranks = 1,
        baseMischief = 0.45f
    ),
    Yaramaz(
        storageKey = "yaramaz",
        labelTr = "Yaramaz",
        reactionsAndRemindersEnabled = true,
        guideEnabled = true,
        spontaneousIntervalMs = 5 * 60_000L,
        maxDailyAskLove = 5,
        maxDailyPranks = 3,
        baseMischief = 0.85f
    );

    val isEnabled: Boolean get() = this != Kapali

    companion object {
        val DEFAULT: RealPetMode = Sakin

        val Off: RealPetMode get() = Kapali
        val Calm: RealPetMode get() = Sakin
        val Mischievous: RealPetMode get() = Yaramaz

        fun fromStorageKey(key: String?): RealPetMode = when (key?.trim()?.lowercase()) {
            "kapali", "kapalı", "off", "disabled" -> Kapali
            "sakin", "calm", "quiet" -> Sakin
            "normal", "standard" -> Normal
            "yaramaz", "mischievous", "playful" -> Yaramaz
            else -> DEFAULT
        }
    }
}

/**
 * Priority ordering from PLAN.md Section 11.1:
 * kullanıcı dokunuşu > hatırlatma/tepki > rehber > kendi hayatı > şaka
 */
enum class RealPetIntentPriority(val rank: Int) {
    Prank(1),
    OwnLife(2),
    Guide(3),
    ReminderOrReaction(4),
    UserTouch(5)
}

/**
 * Input events to [RealPetBrain] as specified in PLAN.md Section 11.1.
 */
sealed interface RealPetEvent {
    // Reactions & Reminders (Stages 2–3)
    data class ReminderDone(val id: Long) : RealPetEvent
    data object AllClear : RealPetEvent
    data object WaterAdded : RealPetEvent
    data class MoodLogged(val bad: Boolean) : RealPetEvent
    data class CardAdded(val id: Long) : RealPetEvent
    data class ReminderDueSoon(val id: Long) : RealPetEvent

    // Guide (Stage 5)
    data class FeatureFirstOpen(val key: String) : RealPetEvent
    data class EmptyScreen(val key: String) : RealPetEvent

    // Keywords / Pranks (Stage 6)
    data object KeywordKedi : RealPetEvent
    data class KeywordMama(val cardId: Long) : RealPetEvent

    // App & User Context (Stage 1+)
    data class UserBusy(val active: Boolean) : RealPetEvent {
        companion object {
            val Start = UserBusy(true)
            val End = UserBusy(false)
        }
    }
    data class AppOpened(val daysSinceLast: Int = 0, val hourOfDay: Int = 12) : RealPetEvent
    data class ScreenChanged(val screenKey: String = "") : RealPetEvent
    data object SpontaneousTick : RealPetEvent

    // Touch Events (PLAN.md Section 7 & Section 11.1)
    data object Tap : RealPetEvent
    data object RepeatedTap : RealPetEvent
    data object LongPress : RealPetEvent
    data class Dragged(val targetPerchId: String?) : RealPetEvent
    data object FlungOffscreen : RealPetEvent
}

enum class RealPetVisitPurpose {
    RoamAndNap,
    RoamAndLeave,
    StepAsideAndSit,
    GuideSit,
    GuideMeow,
    GuideHop,
    RemindDueSoon,
    WaitBesideMamaCard,
    CuriousComposerVisit
}

enum class RealPetReactionKind {
    // Touch reactions (Stage 1)
    TapHeart,
    StopTurnAndSit,
    WakeAndSit,
    HoldPurr,
    RepeatedTapPurrAndLieDown,
    SettledAfterDrag,

    // App reactions (Stage 2, defined in model per Section 11)
    CelebrateReminder,
    CelebrateAllClear,
    DrinkWater,
    ComfortBadMood,
    InspectNewCard
}

enum class RealPetPrankKind {
    CategoryMeowText,
    AddToMamaText,
    PawPushCard,
    SitOnComposer
}

/**
 * Output intents produced by [RealPetBrain] as specified in PLAN.md Section 11.1.
 */
sealed interface RealPetIntent {
    val priority: RealPetIntentPriority

    data class Visit(
        val targetPerchId: String? = null,
        val purpose: RealPetVisitPurpose = RealPetVisitPurpose.RoamAndNap,
        val preferRunning: Boolean = false,
        override val priority: RealPetIntentPriority = RealPetIntentPriority.OwnLife
    ) : RealPetIntent

    data class React(
        val kind: RealPetReactionKind,
        val targetPerchId: String? = null,
        override val priority: RealPetIntentPriority = RealPetIntentPriority.ReminderOrReaction
    ) : RealPetIntent

    data class Nap(
        val targetPerchId: String? = null,
        val startAlreadySleeping: Boolean = false,
        override val priority: RealPetIntentPriority = RealPetIntentPriority.OwnLife
    ) : RealPetIntent

    data class AskLove(
        val targetPerchId: String? = null,
        override val priority: RealPetIntentPriority = RealPetIntentPriority.OwnLife
    ) : RealPetIntent

    data class Prank(
        val kind: RealPetPrankKind,
        val targetPerchId: String? = null,
        override val priority: RealPetIntentPriority = RealPetIntentPriority.Prank
    ) : RealPetIntent

    data class Leave(
        val running: Boolean = false,
        val cooldownMs: Long = 0L,
        override val priority: RealPetIntentPriority = RealPetIntentPriority.OwnLife
    ) : RealPetIntent
}

/**
 * Pure-Kotlin storage abstraction for [RealPetBrain] so the brain is 100% unit-testable
 * without Android runtime dependencies while persisting to [SharedPreferences] in production.
 */
interface RealPetStore {
    fun getString(key: String, default: String? = null): String?
    fun putString(key: String, value: String?)
    fun getInt(key: String, default: Int = 0): Int
    fun putInt(key: String, value: Int)
    fun getLong(key: String, default: Long = 0L): Long
    fun putLong(key: String, value: Long)
    fun getFloat(key: String, default: Float = 0f): Float
    fun putFloat(key: String, value: Float)
    fun getStringSet(key: String): Set<String>
    fun putStringSet(key: String, values: Set<String>)
    fun clear()
}

class InMemoryRealPetStore : RealPetStore {
    private val map = mutableMapOf<String, Any?>()

    override fun getString(key: String, default: String?): String? = map[key] as? String ?: default
    override fun putString(key: String, value: String?) { map[key] = value }
    override fun getInt(key: String, default: Int): Int = map[key] as? Int ?: default
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun getLong(key: String, default: Long): Long = map[key] as? Long ?: default
    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun getFloat(key: String, default: Float): Float = map[key] as? Float ?: default
    override fun putFloat(key: String, value: Float) { map[key] = value }
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String): Set<String> =
        (map[key] as? Set<String>)?.toSet() ?: emptySet()
    override fun putStringSet(key: String, values: Set<String>) { map[key] = values.toSet() }
    override fun clear() { map.clear() }
}

class SharedPreferencesRealPetStore(
    private val prefs: SharedPreferences
) : RealPetStore {
    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)
    override fun putString(key: String, value: String?) { prefs.edit().putString(key, value).apply() }
    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    override fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
    override fun putLong(key: String, value: Long) { prefs.edit().putLong(key, value).apply() }
    override fun getFloat(key: String, default: Float): Float = prefs.getFloat(key, default)
    override fun putFloat(key: String, value: Float) { prefs.edit().putFloat(key, value).apply() }
    override fun getStringSet(key: String): Set<String> =
        prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
    override fun putStringSet(key: String, values: Set<String>) {
        prefs.edit().putStringSet(key, values.toSet()).apply()
    }
    override fun clear() { prefs.edit().clear().apply() }
}

/**
 * State transition graph enforcing PLAN.md Section 5 ("Doğal akış ve geçişler").
 *
 * Allowed transitions:
 * - Ekran dışı (Offscreen) -> Yürüme (Walking), Koşma (Running), (açılışta) Uyku (Sleeping)
 * - Yürüme / Koşma -> Ayakta (Idle), Oturma (Sitting), İleri zıplama (Jumping), Zıplayıp konma (Hopping), Ekran dışı (Offscreen)
 * - Ayakta (Idle) -> Yürüme (Walking), Koşma (Running), Oturma (Sitting), Miyav (Meowing)
 * - Oturma (Sitting) -> Ayakta (Idle), Uzanma (LyingDown)->Uyku (Sleeping), Mırlama (Purring),
 *   Yalanma (Grooming), Sevgi isteme (Calling), Pati (Pawing), Su içme (Drinking), Miyav (Meowing)
 * - Uyku (Sleeping) -> Uyanma (Waking)->Oturma (Sitting)
 * - Mırlama / Yalanma / Sevgi / Pati / Su -> Oturma (Sitting)
 */
object RealPetTransitions {

    fun allowedNextStates(
        from: RealPetState,
        allowOpeningSleep: Boolean = false
    ): Set<RealPetState> = when (from) {
        RealPetState.Offscreen -> if (allowOpeningSleep) {
            setOf(RealPetState.Walking, RealPetState.Running, RealPetState.Sleeping)
        } else {
            setOf(RealPetState.Walking, RealPetState.Running)
        }

        RealPetState.Walking,
        RealPetState.Running -> setOf(
            RealPetState.Idle,
            RealPetState.Sitting,
            RealPetState.Jumping,
            RealPetState.Hopping,
            RealPetState.Offscreen
        )

        RealPetState.Jumping,
        RealPetState.Hopping -> setOf(
            RealPetState.Idle,
            RealPetState.Sitting,
            RealPetState.Walking,
            RealPetState.Running
        )

        RealPetState.Idle -> setOf(
            RealPetState.Walking,
            RealPetState.Running,
            RealPetState.Sitting,
            RealPetState.Meowing
        )

        RealPetState.Sitting -> setOf(
            RealPetState.Idle,
            RealPetState.LyingDown,
            RealPetState.Purring,
            RealPetState.Grooming,
            RealPetState.Calling,
            RealPetState.Pawing,
            RealPetState.Drinking,
            RealPetState.Meowing
        )

        RealPetState.LyingDown -> setOf(
            RealPetState.Sleeping
        )

        RealPetState.Sleeping -> setOf(
            RealPetState.Waking
        )

        RealPetState.Waking -> setOf(
            RealPetState.Sitting
        )

        RealPetState.Purring,
        RealPetState.Grooming,
        RealPetState.Calling,
        RealPetState.Pawing,
        RealPetState.Drinking -> setOf(
            RealPetState.Sitting
        )

        RealPetState.Meowing -> setOf(
            RealPetState.Idle,
            RealPetState.Sitting
        )

        RealPetState.Peeking -> setOf(
            RealPetState.Idle,
            RealPetState.Offscreen
        )
    }

    fun canTransition(
        from: RealPetState,
        to: RealPetState,
        allowOpeningSleep: Boolean = false
    ): Boolean = to in allowedNextStates(from, allowOpeningSleep)

    /**
     * Computes the shortest valid state transition sequence from [from] to [to]
     * (excluding [from], including [to]) strictly following the Section 5 table.
     */
    fun findPath(
        from: RealPetState,
        to: RealPetState,
        allowOpeningSleep: Boolean = false,
        preferRunning: Boolean = false
    ): List<RealPetState> {
        if (from == to) return emptyList()
        val queue = ArrayDeque<List<RealPetState>>()
        val visited = mutableSetOf(from)
        queue.add(listOf(from))

        while (queue.isNotEmpty()) {
            val currentPath = queue.removeFirst()
            val current = currentPath.last()
            val neighbors = allowedNextStates(
                from = current,
                allowOpeningSleep = allowOpeningSleep && current == RealPetState.Offscreen
            ).let { set ->
                if (preferRunning && RealPetState.Running in set && RealPetState.Walking in set) {
                    listOf(RealPetState.Running) + (set - RealPetState.Running)
                } else {
                    set.toList()
                }
            }

            for (next in neighbors) {
                if (next == to) {
                    return currentPath.drop(1) + next
                }
                if (visited.add(next)) {
                    queue.add(currentPath + next)
                }
            }
        }
        return emptyList()
    }
}

/**
 * Pure Kotlin decision & state engine ("Beyin") for REAL pet (PLAN.md Sections 4, 6D, 8, 11, 12.1).
 *
 * All persisted timestamps (`pet_last_open_ms`, `pet_last_visit_ms`, `pet_cooldown_until_ms`)
 * use wall-clock time (`System.currentTimeMillis()`), and daily counter rollovers use
 * `java.time.LocalDate` in [zoneId].
 */
class RealPetBrain(
    private val store: RealPetStore = InMemoryRealPetStore(),
    private val random: Random = Random.Default,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    companion object {
        const val KEY_MODE = "pet_mode"
        const val KEY_ENERGY = "pet_energy"
        const val KEY_LOVE_NEED = "pet_love_need"
        const val KEY_DAY_EPOCH = "pet_day_epoch"
        const val KEY_ASK_LOVE_TODAY = "pet_ask_love_today"
        const val KEY_PRANK_TODAY = "pet_prank_today"
        const val KEY_LAST_OPEN_MS = "pet_last_open_ms"
        const val KEY_LAST_VISIT_MS = "pet_last_visit_ms"
        const val KEY_COOLDOWN_UNTIL_MS = "pet_cooldown_until_ms"
        const val KEY_SEEN_GUIDES = "pet_seen_guides"
        const val KEY_REMINDED_CARDS = "pet_reminded_cards"

        const val LOVE_NEED_THRESHOLD = 0.75f
        const val FLING_COOLDOWN_MS = 180_000L
    }

    var mode: RealPetMode
        get() = RealPetMode.fromStorageKey(store.getString(KEY_MODE, RealPetMode.DEFAULT.storageKey))
        set(value) {
            store.putString(KEY_MODE, value.storageKey)
        }

    var energy: Float
        get() = store.getFloat(KEY_ENERGY, 0.65f).coerceIn(0f, 1f)
        set(value) {
            store.putFloat(KEY_ENERGY, value.coerceIn(0f, 1f))
        }

    var loveNeed: Float
        get() = store.getFloat(KEY_LOVE_NEED, 0.35f).coerceIn(0f, 1f)
        set(value) {
            store.putFloat(KEY_LOVE_NEED, value.coerceIn(0f, 1f))
        }

    val mischief: Float
        get() = mode.baseMischief

    var isUserBusy: Boolean = false
        private set

    var currentState: RealPetState = RealPetState.Offscreen
        private set

    var activeIntent: RealPetIntent? = null
        private set

    val isOnScreen: Boolean
        get() = currentState != RealPetState.Offscreen

    fun updateBodyState(state: RealPetState) {
        currentState = state
        if (state == RealPetState.Offscreen && activeIntent is RealPetIntent.Leave) {
            activeIntent = null
        }
    }

    fun clearActiveIntent() {
        activeIntent = null
    }

    /**
     * Reads the previous wall-clock open time (`pet_last_open_ms`), computes the number of local
     * calendar days elapsed in [zoneId], and persists [nowMs] as the new `pet_last_open_ms`.
     */
    fun recordAppOpenAndComputeDaysSinceLast(nowMs: Long = System.currentTimeMillis()): Int {
        val previousOpenMs = store.getLong(KEY_LAST_OPEN_MS, 0L)
        if (nowMs > 0L) {
            store.putLong(KEY_LAST_OPEN_MS, nowMs)
        }
        if (previousOpenMs <= 0L || nowMs <= previousOpenMs) return 0
        val prevDate = Instant.ofEpochMilli(previousOpenMs).atZone(zoneId).toLocalDate()
        val currDate = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        return ChronoUnit.DAYS.between(prevDate, currDate).toInt().coerceAtLeast(0)
    }

    /**
     * Calculates energy from time of day and usage pattern (PLAN.md Section 4):
     * High in the morning and on light-usage days (runs more), low at night (sleeps more).
     */
    fun computeEnergyForHour(hourOfDay: Int, daysSinceLast: Int = 0): Float {
        val h = ((hourOfDay % 24) + 24) % 24
        val baseByHour = when (h) {
            in 6..11 -> 0.85f  // Morning: high energy
            in 12..17 -> 0.68f // Afternoon: moderate-high energy
            in 18..21 -> 0.48f // Evening: calm
            else -> 0.22f      // Night (22..5): low energy, sleepy
        }
        val usageAdjustment = when {
            daysSinceLast > 3 -> -0.25f // Long absence: starts sleepy
            daysSinceLast in 1..2 -> 0.10f // Light/fresh day: slightly higher energy
            else -> 0.05f // Regular daily usage
        }
        return (baseByHour + usageAdjustment).coerceIn(0.10f, 1.0f)
    }

    fun advanceTime(deltaMs: Long, nowMs: Long = 0L) {
        if (deltaMs <= 0L) return
        ensureDayRollover(nowMs)
        val loveDelta = (deltaMs.toFloat() / 3_600_000f) * 0.15f
        loveNeed = (loveNeed + loveDelta).coerceIn(0f, 1f)
    }

    fun askLoveCountToday(nowMs: Long = 0L): Int {
        ensureDayRollover(nowMs)
        return store.getInt(KEY_ASK_LOVE_TODAY, 0)
    }

    fun prankCountToday(nowMs: Long = 0L): Int {
        ensureDayRollover(nowMs)
        return store.getInt(KEY_PRANK_TODAY, 0)
    }

    fun canAskLoveToday(nowMs: Long = 0L): Boolean {
        if (!mode.isEnabled) return false
        return askLoveCountToday(nowMs) < mode.maxDailyAskLove
    }

    fun canPrankToday(nowMs: Long = 0L): Boolean {
        if (!mode.isEnabled) return false
        return prankCountToday(nowMs) < mode.maxDailyPranks
    }

    fun isInCooldown(nowMs: Long): Boolean {
        if (nowMs <= 0L) return false
        val until = store.getLong(KEY_COOLDOWN_UNTIL_MS, 0L)
        return nowMs < until
    }

    /**
     * Checks whether a spontaneous visit can start at wall-clock [nowMs] (PLAN.md Section 8).
     * Only true in Normal (~10 min) or Yaramaz (~5 min) when offscreen, not busy, and not in cooldown.
     */
    fun canStartSpontaneousVisit(nowMs: Long): Boolean {
        if (!mode.isEnabled || isUserBusy || isOnScreen || isInCooldown(nowMs)) return false
        val interval = mode.spontaneousIntervalMs ?: return false
        val lastVisit = store.getLong(KEY_LAST_VISIT_MS, 0L)
        return lastVisit <= 0L || (nowMs - lastVisit) >= interval
    }

    /**
     * Checks whether [candidate] can preempt [current] according to PLAN.md Section 11.1:
     * UserTouch > ReminderOrReaction > Guide > OwnLife > Prank
     */
    fun canPreempt(current: RealPetIntent?, candidate: RealPetIntent): Boolean {
        if (current == null) return true
        return candidate.priority.rank >= current.priority.rank
    }

    /**
     * Processes an input [event] at wall-clock [nowMs] and returns a new [RealPetIntent]
     * if a state/intent change should be executed by [RealPetBody], or `null` if ignored/blocked.
     */
    fun onEvent(
        event: RealPetEvent,
        nowMs: Long = System.currentTimeMillis()
    ): RealPetIntent? {
        ensureDayRollover(nowMs)

        // If mode is Kapalı, the pet never appears and immediately leaves if visible.
        if (mode == RealPetMode.Kapali) {
            if (isOnScreen) {
                val leave = RealPetIntent.Leave(running = false)
                activeIntent = leave
                return leave
            }
            activeIntent = null
            return null
        }

        val candidate: RealPetIntent? = when (event) {
            is RealPetEvent.UserBusy -> {
                isUserBusy = event.active
                if (event.active) {
                    if (isOnScreen) {
                        RealPetIntent.Visit(
                            targetPerchId = null,
                            purpose = RealPetVisitPurpose.StepAsideAndSit,
                            preferRunning = false,
                            priority = RealPetIntentPriority.ReminderOrReaction
                        )
                    } else {
                        null
                    }
                } else {
                    null
                }
            }

            is RealPetEvent.AppOpened -> {
                if (nowMs > 0L) {
                    store.putLong(KEY_LAST_OPEN_MS, nowMs)
                }
                energy = computeEnergyForHour(event.hourOfDay, event.daysSinceLast)
                if (isUserBusy || isInCooldown(nowMs)) {
                    null
                } else if (event.daysSinceLast > 3) {
                    // PLAN.md Section 6D exception: if >3 days since last open, any enabled mode
                    // starts already sleeping on screen.
                    if (nowMs > 0L) {
                        store.putLong(KEY_LAST_VISIT_MS, nowMs)
                    }
                    RealPetIntent.Nap(
                        targetPerchId = null,
                        startAlreadySleeping = true,
                        priority = RealPetIntentPriority.OwnLife
                    )
                } else if (mode.spontaneousIntervalMs != null) {
                    // PLAN.md Section 8: Sakin never comes by itself; only Normal/Yaramaz start spontaneous visits.
                    if (nowMs > 0L) {
                        store.putLong(KEY_LAST_VISIT_MS, nowMs)
                    }
                    val isNightSleepy = energy <= 0.28f && random.nextFloat() < 0.45f
                    if (isNightSleepy) {
                        RealPetIntent.Nap(
                            targetPerchId = null,
                            startAlreadySleeping = true,
                            priority = RealPetIntentPriority.OwnLife
                        )
                    } else {
                        val preferRunning = energy >= 0.70f || (energy >= 0.50f && random.nextBoolean())
                        RealPetIntent.Visit(
                            targetPerchId = null,
                            purpose = RealPetVisitPurpose.RoamAndNap,
                            preferRunning = preferRunning,
                            priority = RealPetIntentPriority.OwnLife
                        )
                    }
                } else {
                    null
                }
            }

            RealPetEvent.SpontaneousTick -> {
                if (!canStartSpontaneousVisit(nowMs)) {
                    null
                } else {
                    if (nowMs > 0L) {
                        store.putLong(KEY_LAST_VISIT_MS, nowMs)
                    }
                    val preferRunning = energy >= 0.70f || (energy >= 0.50f && random.nextBoolean())
                    RealPetIntent.Visit(
                        targetPerchId = null,
                        purpose = RealPetVisitPurpose.RoamAndNap,
                        preferRunning = preferRunning,
                        priority = RealPetIntentPriority.OwnLife
                    )
                }
            }

            is RealPetEvent.ScreenChanged -> {
                null
            }

            // Touch events (PLAN.md Section 5, Section 7, Section 12.1) — highest priority
            RealPetEvent.Tap -> {
                if (!isOnScreen) return null
                loveNeed = (loveNeed - 0.25f).coerceAtLeast(0f)
                val reactionKind = when (currentState) {
                    RealPetState.Sleeping,
                    RealPetState.LyingDown -> RealPetReactionKind.WakeAndSit
                    RealPetState.Running,
                    RealPetState.Walking -> RealPetReactionKind.StopTurnAndSit
                    else -> RealPetReactionKind.TapHeart
                }
                RealPetIntent.React(
                    kind = reactionKind,
                    priority = RealPetIntentPriority.UserTouch
                )
            }

            RealPetEvent.RepeatedTap -> {
                if (!isOnScreen) return null
                loveNeed = 0f
                RealPetIntent.React(
                    kind = RealPetReactionKind.RepeatedTapPurrAndLieDown,
                    priority = RealPetIntentPriority.UserTouch
                )
            }

            RealPetEvent.LongPress -> {
                if (!isOnScreen) return null
                loveNeed = 0f
                RealPetIntent.React(
                    kind = RealPetReactionKind.HoldPurr,
                    priority = RealPetIntentPriority.UserTouch
                )
            }

            is RealPetEvent.Dragged -> {
                if (!isOnScreen) return null
                RealPetIntent.React(
                    kind = RealPetReactionKind.SettledAfterDrag,
                    targetPerchId = event.targetPerchId,
                    priority = RealPetIntentPriority.UserTouch
                )
            }

            RealPetEvent.FlungOffscreen -> {
                if (!isOnScreen) return null
                if (nowMs > 0L) {
                    store.putLong(KEY_COOLDOWN_UNTIL_MS, nowMs + FLING_COOLDOWN_MS)
                }
                RealPetIntent.Leave(
                    running = true,
                    cooldownMs = FLING_COOLDOWN_MS,
                    priority = RealPetIntentPriority.UserTouch
                )
            }

            // Stages 2–6 events: defined in Section 11 event model, no-op in Stage 1 ("Do not touch stages 2–6 yet")
            is RealPetEvent.ReminderDone,
            RealPetEvent.AllClear,
            RealPetEvent.WaterAdded,
            is RealPetEvent.MoodLogged,
            is RealPetEvent.CardAdded,
            is RealPetEvent.ReminderDueSoon,
            is RealPetEvent.FeatureFirstOpen,
            is RealPetEvent.EmptyScreen,
            RealPetEvent.KeywordKedi,
            is RealPetEvent.KeywordMama -> null
        }

        if (candidate != null && canPreempt(activeIntent, candidate)) {
            activeIntent = candidate
            return candidate
        }
        return null
    }

    fun resetForTesting() {
        store.clear()
        isUserBusy = false
        currentState = RealPetState.Offscreen
        activeIntent = null
    }

    private fun ensureDayRollover(nowMs: Long) {
        if (nowMs <= 0L) return
        val currentEpochDay = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().toEpochDay()
        val storedEpochDay = store.getLong(KEY_DAY_EPOCH, Long.MIN_VALUE)
        if (storedEpochDay != currentEpochDay) {
            store.putLong(KEY_DAY_EPOCH, currentEpochDay)
            store.putInt(KEY_ASK_LOVE_TODAY, 0)
            store.putInt(KEY_PRANK_TODAY, 0)
        }
    }
}