package com.cagri.real.ui.components

import android.os.SystemClock
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlin.math.hypot
import kotlin.random.Random

const val REAL_PET_PERCH_COMPOSER = "real_pet:composer"
const val REAL_PET_PERCH_COMPOSER_TEXT = "real_pet:composer:text"
const val REAL_PET_PERCH_COMPOSER_MIC = "real_pet:composer:mic"
const val REAL_PET_PERCH_CATEGORY_HERO = "real_pet:category_hero"
const val REAL_PET_PERCH_DETAIL_BOTTOM_BAR = "real_pet:detail_bottom_bar"
const val REAL_PET_PERCH_AVATAR = "real_pet:avatar"
const val REAL_PET_PERCH_SHOW_ALL = "real_pet:show_all"

fun realPetCategoryPerchId(key: String): String = "real_pet:category:$key"
fun realPetCategoryPillPerchId(cardPerchId: String, kind: String): String = "$cardPerchId:pill:$kind"
fun realPetReminderPerchId(id: Long): String = "real_pet:reminder:$id"
fun realPetDetailBlockPerchId(reminderId: Long, blockKey: String): String = "real_pet:detail:$reminderId:block:$blockKey"

data class RealPetCommand(
    val token: Int,
    val perchId: String,
    val dropFromTop: Boolean
)

enum class RealPetPrankType {
    ComposerTypedMeow,
    CategoryTitleMeow,
    CategoryPillTextMeow,
    ShowAllTextMeow,
    MicNudge
}

enum class RealPetPriority(val level: Int) {
    Autonomous(1),
    ContextEvent(2),
    KeywordScenario(3),
    UserInteraction(4)
}

enum class RealPetScenarioTestKind {
    Walk,
    Run,
    Sleep,
    Sit,
    Hearts,
    KeywordKedi,
    KeywordMama,
    AlreadySleeping,
    WalkInAndWatch,
    RunAcross,
    CardHop,
    CategoryPatrol,
    ComposerVisit,
    ComposerPlaceholderPrank,
    SitAndObserve,
    DetailExplorer,
    PeekAndRun,
    AttentionVisit,
    AvatarRaid,
    MischiefVisit,
    QuietVisit,
    PoopAndRun,
    DoubleTapSit
}

data class RealPetMamaFeedingRequest(
    val token: Int,
    val cardTitle: String
)

data class RealPetScenarioTestRequest(
    val token: Int,
    val kind: RealPetScenarioTestKind
)

data class RealPetPrank(
    val token: Int,
    val perchId: String,
    val type: RealPetPrankType
)

/** Current visibility/presence lifecycle for the occasional visitor model. */
enum class RealPetPresence {
    Hidden,
    Arriving,
    Active,
    Leaving
}

enum class RealPetVisitReason {
    CasualVisit,
    Celebration,
    AllClear,
    NewCard,
    ComposerPrank,
    KeywordScenario,
    TestRequest
}

/** A reminder was just marked done (or all-clear); the cat celebrates at (or near) its card. */
data class RealPetCelebration(
    val token: Int,
    val reminderId: Long,
    val isAllClear: Boolean = false
)

enum class RealPetTokenState {
    PENDING,
    CLAIMED,
    EXECUTING,
    CONSUMED
}

data class RealPetDetailNavigation(
    val token: Int,
    val reminderId: Long,
    val isEntering: Boolean
)

object RealPetRuntime {
    val perches = mutableStateMapOf<String, Rect>()

    private val presenceState = mutableStateOf(RealPetPresence.Hidden)
    val presence: RealPetPresence get() = presenceState.value
    val isPresent: Boolean get() = presence != RealPetPresence.Hidden

    private val tokenStates = mutableMapOf<String, RealPetTokenState>()

    fun getTokenState(tokenKey: String): RealPetTokenState {
        synchronized(this) {
            return tokenStates[tokenKey] ?: RealPetTokenState.PENDING
        }
    }

    fun tryClaimToken(tokenKey: String): Boolean {
        synchronized(this) {
            val current = tokenStates[tokenKey] ?: RealPetTokenState.PENDING
            if (current == RealPetTokenState.PENDING) {
                tokenStates[tokenKey] = RealPetTokenState.CLAIMED
                return true
            }
            return false
        }
    }

    fun markTokenExecuting(tokenKey: String) {
        synchronized(this) {
            if (tokenStates[tokenKey] == RealPetTokenState.CLAIMED) {
                tokenStates[tokenKey] = RealPetTokenState.EXECUTING
            }
        }
    }

    fun markTokenConsumed(tokenKey: String, onConsume: () -> Unit) {
        synchronized(this) {
            val current = tokenStates[tokenKey]
            if (current == RealPetTokenState.CLAIMED || current == RealPetTokenState.EXECUTING) {
                tokenStates[tokenKey] = RealPetTokenState.CONSUMED
                onConsume()
            }
        }
    }

    fun cleanupToken(tokenKey: String) {
        synchronized(this) {
            tokenStates.remove(tokenKey)
        }
    }

    @Volatile
    private var visitUntilElapsedRealtime: Long = 0L

    @Volatile
    private var nextCasualVisitAllowedAtElapsedRealtime: Long = 0L

    private fun currentElapsedRealtime(): Long {
        return try {
            SystemClock.elapsedRealtime()
        } catch (_: RuntimeException) {
            System.currentTimeMillis()
        }
    }

    fun canStartCasualVisit(): Boolean =
        currentElapsedRealtime() >= nextCasualVisitAllowedAtElapsedRealtime

    fun casualVisitCooldownRemainingMs(): Long =
        (nextCasualVisitAllowedAtElapsedRealtime - currentElapsedRealtime()).coerceAtLeast(0L)

    private fun beginCasualVisitCooldown() {
        // After the cat leaves, autonomous/casual appearances stay quiet for a while.
        // Reactive events (completion/new card/etc.) intentionally do not consult this.
        val cooldownMs = Random.nextLong(120_000L, 300_001L)
        nextCasualVisitAllowedAtElapsedRealtime = currentElapsedRealtime() + cooldownMs
    }

    fun requestVisit(
        reason: RealPetVisitReason = RealPetVisitReason.CasualVisit,
        durationMs: Long = 45_000L
    ): Boolean {
        if ((reason == RealPetVisitReason.CasualVisit || reason == RealPetVisitReason.ComposerPrank) &&
            !canStartCasualVisit()
        ) {
            return false
        }
        val now = currentElapsedRealtime()
        visitUntilElapsedRealtime = maxOf(visitUntilElapsedRealtime, now + durationMs)
        if (presenceState.value == RealPetPresence.Hidden || presenceState.value == RealPetPresence.Leaving) {
            presenceState.value = RealPetPresence.Arriving
            return true
        }
        return false
    }

    fun extendVisit(extraMs: Long = 40_000L) {
        val now = currentElapsedRealtime()
        visitUntilElapsedRealtime = maxOf(visitUntilElapsedRealtime, now + extraMs)
    }

    fun isVisitExpired(): Boolean {
        return currentElapsedRealtime() >= visitUntilElapsedRealtime
    }

    fun markArrived() {
        presenceState.value = RealPetPresence.Active
    }

    fun startLeaving() {
        presenceState.value = RealPetPresence.Leaving
    }

    fun markLeft() {
        presenceState.value = RealPetPresence.Hidden
        visitUntilElapsedRealtime = 0L
        beginCasualVisitCooldown()
    }

    private var nextToken = 0
    private val commandState = mutableStateOf<RealPetCommand?>(null)
    val command: RealPetCommand? get() = commandState.value

    private var nextPrankToken = 0
    private val prankState = mutableStateOf<RealPetPrank?>(null)
    val prank: RealPetPrank? get() = prankState.value

    private var nextScenarioTestToken = 0
    private val scenarioTestRequestState = mutableStateOf<RealPetScenarioTestRequest?>(null)
    val scenarioTestRequest: RealPetScenarioTestRequest? get() = scenarioTestRequestState.value

    val keywordsEnabled = mutableStateOf(false)

    private var nextKediToken = 0
    private val kediAppearanceRequestState = mutableStateOf<Int?>(null)
    val kediAppearanceRequest: Int? get() = kediAppearanceRequestState.value

    private var nextMamaToken = 0
    private val mamaFeedingRequestState = mutableStateOf<RealPetMamaFeedingRequest?>(null)
    val mamaFeedingRequest: RealPetMamaFeedingRequest? get() = mamaFeedingRequestState.value

    private val surfaceDropRequestState = mutableStateOf(0)
    val surfaceDropRequestToken: Int get() = surfaceDropRequestState.value

    private val placeholderOverrideState = mutableStateOf<String?>(null)
    val placeholderOverride: String? get() = placeholderOverrideState.value

    private var nextCelebrationToken = 0
    private val celebrationState = mutableStateOf<RealPetCelebration?>(null)
    val celebration: RealPetCelebration? get() = celebrationState.value

    private val newCardState = mutableStateOf<Long?>(null)
    val newCardReminderId: Long? get() = newCardState.value

    private var nextDetailNavToken = 0
    private val detailNavigationState = mutableStateOf<RealPetDetailNavigation?>(null)
    val detailNavigation: RealPetDetailNavigation? get() = detailNavigationState.value

    // One global anti-spam gap plus per-event cooldowns keeps the cat playful rather than noisy.
    private var lastEventElapsedRealtime = 0L
    private var lastEventType: String? = null
    private val cooldowns = mutableMapOf<String, Long>()


    @Volatile
    private var composerTypingUntilElapsedRealtime: Long = 0L
    private val composerTypingSessionState = mutableStateOf(0)
    val composerTypingSession: Int get() = composerTypingSessionState.value

    private val composerMicActiveState = mutableStateOf(false)
    val composerMicActive: Boolean get() = composerMicActiveState.value

    fun canTriggerEvent(eventType: String, minCooldownMs: Long = 45_000L): Boolean {
        val now = currentElapsedRealtime()
        if (now - lastEventElapsedRealtime < 8_000L) return false
        val lastForType = cooldowns[eventType] ?: 0L
        if (now - lastForType < minCooldownMs) return false
        if (lastEventType == eventType && now - lastForType < (minCooldownMs * 3L) / 2L) return false
        return true
    }

    fun recordEventTriggered(eventType: String) {
        val now = currentElapsedRealtime()
        lastEventElapsedRealtime = now
        lastEventType = eventType
        cooldowns[eventType] = now
    }

    fun updatePerch(id: String, bounds: Rect) {
        perches[id] = bounds
    }

    fun removePerch(id: String) {
        perches.remove(id)
        if (prankState.value?.perchId == id) prankState.value = null
        if (id == REAL_PET_PERCH_COMPOSER) clearComposerTyping()
    }

    fun requestPlaceOn(perchId: String) {
        nextToken += 1
        commandState.value = RealPetCommand(nextToken, perchId, dropFromTop = false)
    }

    fun requestDropTo(perchId: String) {
        nextToken += 1
        commandState.value = RealPetCommand(nextToken, perchId, dropFromTop = true)
    }

    fun consumeCommand(token: Int) {
        if (commandState.value?.token == token) commandState.value = null
    }

    /** Marks genuine keyboard activity so the pet can time typing-related mischief. */
    fun noteComposerTyping() {
        val now = currentElapsedRealtime()
        val startingNewSession = composerTypingUntilElapsedRealtime <= now
        composerTypingUntilElapsedRealtime = now + 3_000L
        if (startingNewSession) {
            composerTypingSessionState.value = composerTypingSessionState.value + 1
        }
    }

    fun clearComposerTyping() {
        composerTypingUntilElapsedRealtime = 0L
    }

    fun isComposerTypingNow(): Boolean =
        composerTypingUntilElapsedRealtime > currentElapsedRealtime()

    fun consumePrank(token: Int) {
        if (prankState.value?.token == token) prankState.value = null
    }

    fun forcePrank(perchId: String, type: RealPetPrankType) {
        nextPrankToken += 1
        prankState.value = RealPetPrank(nextPrankToken, perchId, type)
    }

    fun requestScenarioTest(kind: RealPetScenarioTestKind) {
        requestVisit(RealPetVisitReason.TestRequest, 60_000L)
        nextScenarioTestToken += 1
        scenarioTestRequestState.value = RealPetScenarioTestRequest(nextScenarioTestToken, kind)
    }

    fun consumeScenarioTest(token: Int) {
        if (scenarioTestRequestState.value?.token == token) scenarioTestRequestState.value = null
    }

    fun requestKediAppearance() {
        requestVisit(RealPetVisitReason.KeywordScenario, 12_000L)
        nextKediToken += 1
        kediAppearanceRequestState.value = nextKediToken
    }

    fun consumeKediAppearance(token: Int) {
        if (kediAppearanceRequestState.value == token) {
            kediAppearanceRequestState.value = null
        }
    }

    fun requestMamaFeeding(cardTitle: String) {
        requestVisit(RealPetVisitReason.KeywordScenario, 14_000L)
        nextMamaToken += 1
        mamaFeedingRequestState.value = RealPetMamaFeedingRequest(nextMamaToken, cardTitle)
    }

    fun consumeMamaFeeding(token: Int) {
        if (mamaFeedingRequestState.value?.token == token) {
            mamaFeedingRequestState.value = null
        }
    }

    fun requestSurfaceDrop() {
        surfaceDropRequestState.value = surfaceDropRequestState.value + 1
    }

    fun setComposerMicActive(active: Boolean) {
        composerMicActiveState.value = active
    }

    fun isComposerMicActive(): Boolean = composerMicActiveState.value

    /** A reminder was just checked off; request a short celebration at its card if visible. */
    fun requestCelebration(reminderId: Long, minCooldownMs: Long = 15_000L): Boolean {
        if (!canTriggerEvent("celebration", minCooldownMs)) return false
        recordEventTriggered("celebration")
        requestVisit(RealPetVisitReason.Celebration, 14_000L)
        nextCelebrationToken += 1
        celebrationState.value = RealPetCelebration(nextCelebrationToken, reminderId, isAllClear = false)
        return true
    }

    /** All reminders are completed (Inbox Zero); celebratory center visit. */
    fun requestAllClearCelebration(minCooldownMs: Long = 20_000L): Boolean {
        if (!canTriggerEvent("all_clear", minCooldownMs)) return false
        recordEventTriggered("all_clear")
        requestVisit(RealPetVisitReason.AllClear, 16_000L)
        nextCelebrationToken += 1
        celebrationState.value = RealPetCelebration(nextCelebrationToken, reminderId = -1L, isAllClear = true)
        return true
    }

    fun consumeCelebration(token: Int) {
        if (celebrationState.value?.token == token) celebrationState.value = null
    }

    /** A new reminder card was just added by the user; cat can greet it. */
    fun noteNewCardAdded(reminderId: Long, minCooldownMs: Long = 25_000L): Boolean {
        if (!canTriggerEvent("new_card", minCooldownMs)) return false
        recordEventTriggered("new_card")
        requestVisit(RealPetVisitReason.NewCard, 14_000L)
        newCardState.value = reminderId
        return true
    }

    fun consumeNewCard(reminderId: Long) {
        if (newCardState.value == reminderId) newCardState.value = null
    }

    fun noteDetailEntered(reminderId: Long) {
        if (presence == RealPetPresence.Hidden) return
        nextDetailNavToken += 1
        detailNavigationState.value = RealPetDetailNavigation(nextDetailNavToken, reminderId, isEntering = true)
    }

    fun noteDetailExited(reminderId: Long) {
        if (presence == RealPetPresence.Hidden) return
        nextDetailNavToken += 1
        detailNavigationState.value = RealPetDetailNavigation(nextDetailNavToken, reminderId, isEntering = false)
    }

    fun consumeDetailNavigation(token: Int) {
        if (detailNavigationState.value?.token == token) {
            detailNavigationState.value = null
        }
    }

    /** Genuine interaction with the cat only extends the current visit; there is no touch-hunger timer. */
    fun noteUserTouch() {
        extendVisit(12_000L)
    }

    fun setPlaceholderOverride(value: String?) {
        placeholderOverrideState.value = value
        if (value != null) recordEventTriggered("composer_placeholder")
    }

    fun clearPlaceholderOverride() {
        placeholderOverrideState.value = null
    }

    fun nearestPerch(point: Offset, maxDistancePx: Float): String? {
        var bestId: String? = null
        var bestDistance = maxDistancePx
        perches.forEach { (id, bounds) ->
            val target = Offset(point.x.coerceIn(bounds.left, bounds.right), bounds.top)
            val distance = hypot(target.x - point.x, target.y - point.y)
            if (distance <= bestDistance) {
                bestDistance = distance
                bestId = id
            }
        }
        return bestId
    }

    fun resetForTesting() {
        lastEventElapsedRealtime = 0L
        lastEventType = null
        cooldowns.clear()
        celebrationState.value = null
        newCardState.value = null
        detailNavigationState.value = null
        scenarioTestRequestState.value = null
        kediAppearanceRequestState.value = null
        mamaFeedingRequestState.value = null
        commandState.value = null
        prankState.value = null
        placeholderOverrideState.value = null
        composerMicActiveState.value = false
        perches.clear()
        tokenStates.clear()
        nextCasualVisitAllowedAtElapsedRealtime = 0L
        visitUntilElapsedRealtime = 0L
    }
}

fun Modifier.realPetPerch(id: String): Modifier = composed {
    androidx.compose.runtime.DisposableEffect(id) {
        onDispose { RealPetRuntime.removePerch(id) }
    }
    this.onGloballyPositioned { coordinates ->
        val position = coordinates.positionInRoot()
        val width = coordinates.size.width.toFloat()
        val height = coordinates.size.height.toFloat()
        RealPetRuntime.updatePerch(
            id,
            Rect(position.x, position.y, position.x + width, position.y + height)
        )
    }
}
