package com.cagri.real.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

internal enum class RealPetDetailEntranceScenario {
    DropOntoTopCard,
    TravelDownCards,
    SideEntrance,
    BottomBarExplorer
}

internal enum class RealPetDetailReturnScenario {
    JumpToComposer,
    StayWithReminderCard,
    LeaveOffscreen
}

/**
 * Scenario engine and selection orchestration for REAL pet guest visits.
 */
internal enum class RealPetGuestScenario {
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
    PoopAndRun
}

internal fun RealPetScenarioTestKind.toGuestScenario(): RealPetGuestScenario = when (this) {
    RealPetScenarioTestKind.AlreadySleeping -> RealPetGuestScenario.AlreadySleeping
    RealPetScenarioTestKind.WalkInAndWatch -> RealPetGuestScenario.WalkInAndWatch
    RealPetScenarioTestKind.RunAcross -> RealPetGuestScenario.RunAcross
    RealPetScenarioTestKind.CardHop -> RealPetGuestScenario.CardHop
    RealPetScenarioTestKind.CategoryPatrol -> RealPetGuestScenario.CategoryPatrol
    RealPetScenarioTestKind.ComposerVisit -> RealPetGuestScenario.ComposerVisit
    RealPetScenarioTestKind.ComposerPlaceholderPrank -> RealPetGuestScenario.ComposerPlaceholderPrank
    RealPetScenarioTestKind.SitAndObserve -> RealPetGuestScenario.SitAndObserve
    RealPetScenarioTestKind.DetailExplorer -> RealPetGuestScenario.DetailExplorer
    RealPetScenarioTestKind.PeekAndRun -> RealPetGuestScenario.PeekAndRun
    RealPetScenarioTestKind.AttentionVisit -> RealPetGuestScenario.AttentionVisit
    RealPetScenarioTestKind.AvatarRaid -> RealPetGuestScenario.AvatarRaid
    RealPetScenarioTestKind.MischiefVisit -> RealPetGuestScenario.MischiefVisit
    RealPetScenarioTestKind.QuietVisit -> RealPetGuestScenario.QuietVisit
    RealPetScenarioTestKind.PoopAndRun -> RealPetGuestScenario.PoopAndRun
    RealPetScenarioTestKind.DoubleTapSit -> RealPetGuestScenario.SitAndObserve
    RealPetScenarioTestKind.Walk -> RealPetGuestScenario.WalkInAndWatch
    RealPetScenarioTestKind.Run -> RealPetGuestScenario.RunAcross
    RealPetScenarioTestKind.Sleep -> RealPetGuestScenario.AlreadySleeping
    RealPetScenarioTestKind.Sit -> RealPetGuestScenario.SitAndObserve
    RealPetScenarioTestKind.Hearts -> RealPetGuestScenario.AttentionVisit
    RealPetScenarioTestKind.KeywordKedi -> RealPetGuestScenario.AttentionVisit
    RealPetScenarioTestKind.KeywordMama -> RealPetGuestScenario.ComposerVisit
}

internal fun RealPetActionScope.visibleReminderPerches(): List<String> =
    RealPetRuntime.perches.entries
        .filter { (id, bounds) ->
            id.startsWith("real_pet:reminder:") &&
                bounds.bottom >= 0f && bounds.top <= heightPx &&
                bounds.right >= 0f && bounds.left <= widthPx
        }
        .map { it.key }

internal fun RealPetActionScope.visibleDetailPerches(): List<String> =
    RealPetRuntime.perches.entries
        .filter { (id, bounds) ->
            id.startsWith("real_pet:detail:") && ":block:" in id &&
                bounds.bottom >= 0f && bounds.top <= heightPx &&
                bounds.right >= 0f && bounds.left <= widthPx
        }
        .map { it.key }

internal fun RealPetActionScope.visibleDetailBlockPerches(reminderId: Long? = null): List<String> =
    RealPetRuntime.perches.entries
        .filter { (id, bounds) ->
            id.startsWith(if (reminderId != null) "real_pet:detail:$reminderId:block:" else "real_pet:detail:") &&
                ":block:" in id &&
                bounds.bottom >= 0f && bounds.top <= heightPx &&
                bounds.right >= 0f && bounds.left <= widthPx
        }
        .map { it.key }

internal fun RealPetActionScope.orderedDetailPerches(reminderId: Long? = null): List<String> =
    visibleDetailBlockPerches(reminderId).sortedBy { id ->
        RealPetRuntime.perches[id]?.top ?: Float.MAX_VALUE
    }

internal fun RealPetActionScope.topDetailBlockPerch(reminderId: Long? = null): String? =
    orderedDetailPerches(reminderId).firstOrNull()

internal fun RealPetActionScope.bottomDetailBlockPerch(reminderId: Long? = null): String? =
    orderedDetailPerches(reminderId).lastOrNull()

internal fun RealPetActionScope.detailBottomBarPerch(): String? =
    REAL_PET_PERCH_DETAIL_BOTTOM_BAR.takeIf {
        val bounds = RealPetRuntime.perches[it]
        bounds != null && bounds.bottom >= 0f && bounds.top <= heightPx
    }

internal fun RealPetActionScope.detailPerchFallback(reminderId: Long? = null): String? =
    topDetailBlockPerch(reminderId) ?: detailBottomBarPerch()

internal fun RealPetActionScope.visibleCategorySurfacePerches(): List<String> =
    RealPetRuntime.perches.entries
        .filter { (id, bounds) ->
            realPetPerchIsCategorySurface(id) &&
                bounds.bottom >= 0f && bounds.top <= heightPx &&
                bounds.right >= 0f && bounds.left <= widthPx
        }
        .map { it.key }

internal fun RealPetActionScope.visibleInterestingPerches(exclude: String? = null): List<String> {
    val visible = RealPetRuntime.perches.entries
        .filter { (id, bounds) ->
            id != exclude &&
                bounds.bottom >= 0f &&
                bounds.top <= heightPx &&
                bounds.right >= 0f &&
                bounds.left <= widthPx &&
                id != REAL_PET_PERCH_DETAIL_BOTTOM_BAR
        }
        .map { it.key }

    return buildList {
        visible.forEach { id ->
            add(id)
            if (":pill:" in id) {
                add(id)
                add(id)
            } else if (id.startsWith("real_pet:reminder:")) {
                add(id)
            } else if (
                id == REAL_PET_PERCH_AVATAR ||
                id == REAL_PET_PERCH_SHOW_ALL ||
                id == REAL_PET_PERCH_COMPOSER ||
                id == REAL_PET_PERCH_COMPOSER_TEXT ||
                id == REAL_PET_PERCH_COMPOSER_MIC
            ) {
                add(id)
                if (id == REAL_PET_PERCH_COMPOSER) {
                    add(id)
                    add(id)
                }
            }
        }
    }
}

internal fun RealPetActionScope.pickInterestingPerch(exclude: String? = null): String? {
    val candidates = visibleInterestingPerches(exclude)
    if (candidates.isEmpty()) return null
    repeat(6) {
        val candidate = candidates[random.nextInt(candidates.size)]
        if (candidate != REAL_PET_PERCH_COMPOSER_TEXT || !RealPetRuntime.isComposerTypingNow()) {
            return candidate
        }
    }
    return candidates.firstOrNull { it != REAL_PET_PERCH_COMPOSER_TEXT }
}

internal fun RealPetActionScope.guestTextChangeEligible(): Boolean {
    if (RealPetRuntime.isComposerTypingNow() && RealPetRuntime.perches[REAL_PET_PERCH_COMPOSER_TEXT] != null) return true
    if (visibleCategorySurfacePerches().isNotEmpty()) return true
    if (RealPetRuntime.perches[REAL_PET_PERCH_SHOW_ALL] != null) return true
    return RealPetRuntime.perches.keys.any { ":pill:" in it }
}

internal fun RealPetActionScope.guestPlaceholderPrankEligible(): Boolean =
    !RealPetRuntime.isComposerTypingNow() &&
        RealPetRuntime.placeholderOverride == null &&
        (RealPetRuntime.perches[REAL_PET_PERCH_COMPOSER] != null ||
            RealPetRuntime.perches[REAL_PET_PERCH_COMPOSER_TEXT] != null)

internal fun RealPetActionScope.scenarioTestContextReady(kind: RealPetScenarioTestKind): Boolean = when (kind) {
    RealPetScenarioTestKind.CardHop -> visibleReminderPerches().isNotEmpty()
    RealPetScenarioTestKind.CategoryPatrol -> visibleCategorySurfacePerches().isNotEmpty()
    RealPetScenarioTestKind.ComposerVisit ->
        RealPetRuntime.perches[REAL_PET_PERCH_COMPOSER] != null ||
            RealPetRuntime.perches[REAL_PET_PERCH_COMPOSER_TEXT] != null
    RealPetScenarioTestKind.ComposerPlaceholderPrank -> guestPlaceholderPrankEligible()
    RealPetScenarioTestKind.DetailExplorer -> visibleDetailPerches().isNotEmpty()
    RealPetScenarioTestKind.AvatarRaid -> RealPetRuntime.perches[REAL_PET_PERCH_AVATAR] != null
    RealPetScenarioTestKind.MischiefVisit -> guestTextChangeEligible()
    else -> true
}

internal fun pickRealPetGuestScenario(
    random: Random,
    hasCards: Boolean,
    hasCategories: Boolean,
    hasComposer: Boolean,
    hasDetailBlocks: Boolean,
    hasAvatar: Boolean,
    canTextPrank: Boolean,
    canPlaceholderPrank: Boolean
): RealPetGuestScenario {
    val weighted = buildList {
        add(RealPetGuestScenario.WalkInAndWatch to 7)
        add(RealPetGuestScenario.RunAcross to 4)
        add(RealPetGuestScenario.AlreadySleeping to 3)
        add(RealPetGuestScenario.SitAndObserve to 7)
        add(RealPetGuestScenario.QuietVisit to 6)
        add(RealPetGuestScenario.PeekAndRun to 3)
        add(RealPetGuestScenario.AttentionVisit to 2)
        if (hasCards) add(RealPetGuestScenario.CardHop to 5)
        if (hasCategories) add(RealPetGuestScenario.CategoryPatrol to 5)
        if (hasComposer) add(RealPetGuestScenario.ComposerVisit to 4)
        if (canPlaceholderPrank) add(RealPetGuestScenario.ComposerPlaceholderPrank to 3)
        if (hasDetailBlocks) add(RealPetGuestScenario.DetailExplorer to 5)
        if (hasAvatar) add(RealPetGuestScenario.AvatarRaid to 2)
        if (canTextPrank) add(RealPetGuestScenario.MischiefVisit to 2)
        if (hasCards) add(RealPetGuestScenario.PoopAndRun to 1)
    }
    val total = weighted.sumOf { it.second }
    var roll = random.nextInt(total.coerceAtLeast(1))
    weighted.forEach { (scenario, weight) ->
        if (roll < weight) return scenario
        roll -= weight
    }
    return RealPetGuestScenario.QuietVisit
}

internal suspend fun RealPetActionScope.performComposerPlaceholderPrank() {
    val target = REAL_PET_PERCH_COMPOSER
        .takeIf { RealPetRuntime.perches[it] != null }
        ?: REAL_PET_PERCH_COMPOSER_TEXT.takeIf { RealPetRuntime.perches[it] != null }
        ?: return
    if (target != anchorId) moveToPerch(target)
    hopOnPerch(target)
    val variants = listOf(
        "add to MEAW",
        "meaww meaw meaw",
        "meaw meaww meaw",
        "meawww meaww",
        "meaw meaw meaww"
    )
    RealPetRuntime.setPlaceholderOverride(variants[random.nextInt(variants.size)])
}

internal suspend fun RealPetActionScope.performGuestTextChange() {
    val options = mutableListOf<String>()
    if (RealPetRuntime.isComposerTypingNow() && RealPetRuntime.perches[REAL_PET_PERCH_COMPOSER_TEXT] != null) {
        options += "typed"
    }
    if (visibleCategorySurfacePerches().isNotEmpty()) options += "category"
    if (RealPetRuntime.perches[REAL_PET_PERCH_SHOW_ALL] != null) options += "show_all"
    if (RealPetRuntime.perches.keys.any { ":pill:" in it }) options += "pill"
    if (options.isEmpty()) return

    when (options[random.nextInt(options.size)]) {
        "typed" -> {
            moveToPerch(REAL_PET_PERCH_COMPOSER_TEXT)
            hopOnPerch(REAL_PET_PERCH_COMPOSER_TEXT)
            RealPetRuntime.forcePrank(REAL_PET_PERCH_COMPOSER, RealPetPrankType.ComposerTypedMeow)
        }
        "category" -> {
            val cardId = visibleCategorySurfacePerches().random(random)
            moveToPerch(cardId)
            hopOnPerch(cardId)
            RealPetRuntime.forcePrank(cardId, RealPetPrankType.CategoryTitleMeow)
        }
        "show_all" -> {
            moveToPerch(REAL_PET_PERCH_SHOW_ALL)
            hopOnPerch(REAL_PET_PERCH_SHOW_ALL)
            RealPetRuntime.forcePrank(REAL_PET_PERCH_SHOW_ALL, RealPetPrankType.ShowAllTextMeow)
        }
        "pill" -> {
            val pillId = RealPetRuntime.perches.keys.filter { ":pill:" in it }.random(random)
            moveToPerch(pillId)
            hopOnPerch(pillId)
            RealPetRuntime.forcePrank(pillId, RealPetPrankType.CategoryPillTextMeow)
        }
    }
}

internal suspend fun RealPetActionScope.performPoopOnLastCard(): Boolean {
    val reminderPerches = RealPetRuntime.perches.entries
        .filter { (id, bounds) ->
            id.startsWith("real_pet:reminder:") &&
                bounds.bottom >= 0f && bounds.top <= heightPx &&
                bounds.right >= 0f && bounds.left <= widthPx
        }
    val lastReminderPerch = reminderPerches.maxByOrNull { it.value.top }?.key
        ?: reminders().asReversed().firstNotNullOfOrNull { reminder ->
            val pid = realPetReminderPerchId(reminder.id)
            if (RealPetRuntime.perches[pid] != null) pid else null
        }
        ?: visibleInterestingPerches().lastOrNull { it.startsWith("real_pet:reminder:") }
        ?: pickInterestingPerch()

    if (lastReminderPerch != null) {
        if (anchorId != lastReminderPerch) {
            moveToPerch(lastReminderPerch)
        }
        hopOnPerch(lastReminderPerch)
        RealHaptics.tick(view)
        meowCaption = "💩"
        delay(if (reduceAnimations) 250L else 500L)
    }
    latestCreatePersistentCard.value("💩")
    RealHaptics.success(view)
    meowCaption = "💩"
    delay(if (reduceAnimations) 300L else 700L)
    meowCaption = null

    runOffscreenAndLeave()
    return true
}

internal suspend fun RealPetActionScope.leavePoopSurprise(): Boolean {
    if (!RealPetRuntime.canTriggerEvent("poop_card", 480_000L)) return false
    RealPetRuntime.recordEventTriggered("poop_card")
    return performPoopOnLastCard()
}

// Random attention script. There is no 90-second hunger timer anymore.
// When chosen as a guest action, the cat asks for 2-4 taps with repeated meows.
// Enough taps = satisfied; no taps = gives up. Either way it then leaves naturally.
internal suspend fun RealPetActionScope.performAttentionScript(): Boolean {
    if (attentionActive) return false
    attentionActive = true
    attentionTapCount = 0
    attentionTapGoal = random.nextInt(2, 5)
    RealPetRuntime.extendVisit(20_000L)

    val targetPerch = REAL_PET_PERCH_COMPOSER
        .takeIf { RealPetRuntime.perches[it] != null }
        ?: pickInterestingPerch(anchorId)

    if (targetPerch != null && targetPerch != anchorId) {
        moveToPerch(targetPerch)
    }

    val giveUpAt = SystemClock.elapsedRealtime() + random.nextLong(9_000L, 14_000L)
    try {
        while (attentionTapCount < attentionTapGoal && SystemClock.elapsedRealtime() < giveUpAt) {
            meow(durationMs = if (reduceAnimations) 220L else random.nextLong(360L, 520L))
            if (attentionTapCount >= attentionTapGoal) break
            delay(if (reduceAnimations) 600L else random.nextLong(900L, 1_650L))
        }

        if (attentionTapCount >= attentionTapGoal) {
            RealHaptics.success(view)
            playSitSequence(if (reduceAnimations) 500L else 1_100L)
            // Satisfied: continue to one nearby visible surface before disappearing.
            val nextPerch = pickInterestingPerch(anchorId)
            if (nextPerch != null && nextPerch != anchorId) {
                moveToPerch(nextPerch)
                delay(if (reduceAnimations) 200L else 650L)
            }
        } else {
            // One final quiet meow before giving up, with no floating caption.
            meow(durationMs = if (reduceAnimations) 180L else 340L)
        }
    } finally {
        attentionActive = false
    }

    runOffscreenAndLeave()
    return true
}

internal suspend fun RealPetActionScope.runGuestScenario(scenario: RealPetGuestScenario): Boolean {
    when (scenario) {
        RealPetGuestScenario.AlreadySleeping -> {
            // Opening-only scenario; if reached here just nap where we are.
            takeNap(random.nextLong(8_000L, 15_000L))
        }

        RealPetGuestScenario.WalkInAndWatch -> {
            val target = pickInterestingPerch(anchorId)
            if (target != null && target != anchorId) moveToPerch(target)
            patrolCurrentSurface(running = false)
            sitAndObserve(4_000L, 9_000L)
        }

        RealPetGuestScenario.RunAcross -> {
            patrolCurrentSurface(running = true)
            if (random.nextFloat() < 0.55f) patrolCurrentSurface(running = true)
        }

        RealPetGuestScenario.CardHop -> {
            val cards = visibleReminderPerches()
            if (cards.isEmpty()) {
                patrolCurrentSurface(running = false)
            } else {
                val currentAnchor = anchorId
                val first = if (currentAnchor != null && currentAnchor in cards) {
                    currentAnchor
                } else {
                    cards[random.nextInt(cards.size)]
                }
                if (first != anchorId) moveToPerch(first)
                val current = latestRenderX.value to latestRenderY.value
                val nearby = cards.filter { it != anchorId }.filter { id ->
                    val target = perchTargetPosition(id, 0.5f) ?: return@filter false
                    val distanceDp = kotlin.math.hypot(target.x - current.first, target.y - current.second) / density.density
                    distanceDp in 18f..135f
                }
                if (nearby.isNotEmpty()) {
                    moveToPerch(nearby[random.nextInt(nearby.size)])
                } else {
                    patrolCurrentSurface(running = false)
                }
            }
        }

        RealPetGuestScenario.CategoryPatrol -> {
            val categories = visibleCategorySurfacePerches()
            if (categories.isNotEmpty()) {
                val target = categories.filter { it != anchorId }.let { choices ->
                    if (choices.isEmpty()) categories.first() else choices[random.nextInt(choices.size)]
                }
                if (target != anchorId) moveToPerch(target)
                patrolCurrentSurface(running = random.nextFloat() < 0.30f)
            }
        }

        RealPetGuestScenario.ComposerVisit -> {
            val target = REAL_PET_PERCH_COMPOSER.takeIf { RealPetRuntime.perches[it] != null }
                ?: REAL_PET_PERCH_COMPOSER_TEXT.takeIf { RealPetRuntime.perches[it] != null }
            if (target != null && target != anchorId) moveToPerch(target)
            if (target != null) sitAndObserve(3_500L, 8_500L)
        }

        RealPetGuestScenario.ComposerPlaceholderPrank -> {
            performComposerPlaceholderPrank()
        }

        RealPetGuestScenario.SitAndObserve -> {
            if (anchorId == null) {
                pickInterestingPerch()?.let { moveToPerch(it) }
            }
            sitAndObserve(5_000L, 12_000L)
        }

        RealPetGuestScenario.DetailExplorer -> {
            val details = (visibleDetailPerches() + listOfNotNull(detailBottomBarPerch())).distinct()
            if (details.isNotEmpty()) {
                val first = details[random.nextInt(details.size)]
                if (first != anchorId) moveToPerch(first)
                val secondChoices = details.filter { it != first }
                if (secondChoices.isNotEmpty() && random.nextFloat() < 0.72f) {
                    delay(if (reduceAnimations) 250L else random.nextLong(700L, 1_600L))
                    moveToPerch(secondChoices[random.nextInt(secondChoices.size)])
                }
                sitAndObserve(3_000L, 7_000L)
            }
        }

        RealPetGuestScenario.PeekAndRun -> {
            if (anchorId != null) detachToGround()
            edgePeek()
            if (RealPetRuntime.presence == RealPetPresence.Hidden) return true
            runOffscreenAndLeave()
            return true
        }

        RealPetGuestScenario.AttentionVisit -> {
            return performAttentionScript()
        }

        RealPetGuestScenario.AvatarRaid -> {
            if (RealPetRuntime.perches[REAL_PET_PERCH_AVATAR] != null) {
                moveToPerch(REAL_PET_PERCH_AVATAR)
                facing = RealPetFacing.Right
                state = RealPetState.Idle
                delay(if (reduceAnimations) 800L else random.nextLong(1_800L, 3_600L))
            }
        }

        RealPetGuestScenario.MischiefVisit -> {
            performGuestTextChange()
        }

        RealPetGuestScenario.QuietVisit -> {
            if (random.nextBoolean()) patrolCurrentSurface(running = false)
            if (random.nextFloat() < 0.72f) sitAndObserve(3_000L, 7_500L)
        }

        RealPetGuestScenario.PoopAndRun -> {
            if (leavePoopSurprise()) return true
        }
    }
    return false
}

/**
 * Continues the same guest story after a direct user interruption (drag/hold/navigation pause).
 * This deliberately resumes with a safe tail instead of replaying side effects such as poop/text
 * pranks from the beginning. The pet accepts the user's new anchor, finishes a recognizable part
 * of the original scenario, and then the normal visitor loop decides whether to leave.
 */
internal suspend fun RealPetActionScope.resumeGuestScenarioAfterInterruption(
    scenario: RealPetGuestScenario
): Boolean {
    when (scenario) {
        RealPetGuestScenario.AlreadySleeping -> {
            if (sleepUntil > SystemClock.elapsedRealtime()) {
                delay((sleepUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            }
            if (RealPetRuntime.presence == RealPetPresence.Active) {
                sitAndObserve(1_500L, 3_200L)
            }
        }

        RealPetGuestScenario.WalkInAndWatch -> {
            patrolCurrentSurface(running = false)
            sitAndObserve(2_500L, 5_500L)
        }

        RealPetGuestScenario.RunAcross -> {
            // Keep this scenario energetic even after the user puts the pet somewhere else.
            patrolCurrentSurface(running = true)
            if (random.nextFloat() < 0.60f) patrolCurrentSurface(running = true)
        }

        RealPetGuestScenario.CardHop -> {
            val cards = visibleReminderPerches().filter { it != anchorId }
            if (cards.isNotEmpty()) {
                val next = cards.random(random)
                moveToPerch(next)
                if (anchorId == next) hopOnPerch(next)
            } else {
                patrolCurrentSurface(running = false)
            }
            sitAndObserve(1_800L, 4_000L)
        }

        RealPetGuestScenario.CategoryPatrol -> {
            val categories = visibleCategorySurfacePerches().filter { it != anchorId }
            if (categories.isNotEmpty()) moveToPerch(categories.random(random))
            patrolCurrentSurface(running = random.nextFloat() < 0.35f)
        }

        RealPetGuestScenario.ComposerVisit -> {
            val target = REAL_PET_PERCH_COMPOSER.takeIf { RealPetRuntime.perches[it] != null }
                ?: REAL_PET_PERCH_COMPOSER_TEXT.takeIf { RealPetRuntime.perches[it] != null }
            if (target != null && target != anchorId) moveToPerch(target)
            sitAndObserve(2_500L, 5_500L)
        }

        RealPetGuestScenario.ComposerPlaceholderPrank -> {
            // Do not fire the text prank twice after a drag; finish at the composer instead.
            val target = REAL_PET_PERCH_COMPOSER.takeIf { RealPetRuntime.perches[it] != null }
                ?: REAL_PET_PERCH_COMPOSER_TEXT.takeIf { RealPetRuntime.perches[it] != null }
            if (target != null && target != anchorId) moveToPerch(target)
            if (target != null) sitAndObserve(1_800L, 3_800L)
        }

        RealPetGuestScenario.SitAndObserve -> sitAndObserve(2_500L, 5_500L)

        RealPetGuestScenario.DetailExplorer -> {
            val details = (visibleDetailPerches() + listOfNotNull(detailBottomBarPerch()))
                .distinct()
                .filter { it != anchorId }
            if (details.isNotEmpty()) moveToPerch(details.random(random))
            sitAndObserve(2_000L, 4_500L)
        }

        RealPetGuestScenario.PeekAndRun -> {
            // Once the user has physically moved a peek visitor, finish with an energetic exit
            // rather than trying to reconstruct the old edge-peek coordinates.
            if (anchorId != null) patrolCurrentSurface(running = true)
            runOffscreenAndLeave()
            return true
        }

        RealPetGuestScenario.AttentionVisit -> return performAttentionScript()

        RealPetGuestScenario.AvatarRaid -> {
            if (RealPetRuntime.perches[REAL_PET_PERCH_AVATAR] != null) {
                moveToPerch(REAL_PET_PERCH_AVATAR)
                facing = RealPetFacing.Right
                state = RealPetState.Idle
                delay(if (reduceAnimations) 450L else random.nextLong(1_000L, 2_400L))
            }
        }

        RealPetGuestScenario.MischiefVisit -> {
            // The original prank may already have fired. Do not mutate UI text a second time.
            if (random.nextBoolean()) patrolCurrentSurface(running = false)
            sitAndObserve(1_500L, 3_500L)
        }

        RealPetGuestScenario.QuietVisit -> {
            if (random.nextBoolean()) patrolCurrentSurface(running = false)
            sitAndObserve(2_000L, 4_500L)
        }

        RealPetGuestScenario.PoopAndRun -> {
            // Avoid creating a second persistent poop card after an interrupted prank.
            runOffscreenAndLeave()
            return true
        }
    }
    return false
}

internal suspend fun RealPetActionScope.performDetailEntranceTopCardDrop(reminderId: Long) {
    val targetPerch = topDetailBlockPerch(reminderId) ?: detailBottomBarPerch() ?: return
    val bounds = RealPetRuntime.perches[targetPerch] ?: return
    val targetRange = realPetPerchRange(bounds.left, bounds.right, petSizePx, sidePaddingPx, maxX)
    val targetX = (targetRange.start + targetRange.endInclusive) / 2f
    val targetY = perchY(targetPerch, bounds)

    if (sleepUntil > 0L) {
        anchorId = targetPerch
        sleepX = targetX
        sleepY = targetY
        x.snapTo(targetX)
        y.snapTo(targetY)
        return
    }

    performDropMotion(
        targetX = targetX,
        targetY = targetY,
        targetPerchId = targetPerch,
        startX = targetX,
        startY = -petSizePx,
        heightPx = with(density) { 48.dp.toPx() }
    )

    when (random.nextInt(4)) {
        0 -> sitAndObserve(3_500L, 7_500L)
        1 -> {
            state = RealPetState.Idle
            delay(random.nextLong(1_800L, 3_500L))
        }
        2 -> patrolCurrentSurface(running = false)
        3 -> takeNap(random.nextLong(10_000L, 20_000L))
    }
}

internal suspend fun RealPetActionScope.performDetailEntranceTravelDown(reminderId: Long) {
    val orderedBlocks = orderedDetailPerches(reminderId)
    val topBlock = orderedBlocks.firstOrNull() ?: detailBottomBarPerch() ?: return

    if (sleepUntil > 0L) {
        val bounds = RealPetRuntime.perches[topBlock] ?: return
        val targetRange = realPetPerchRange(bounds.left, bounds.right, petSizePx, sidePaddingPx, maxX)
        val targetX = (targetRange.start + targetRange.endInclusive) / 2f
        val targetY = perchY(topBlock, bounds)
        anchorId = topBlock
        sleepX = targetX
        sleepY = targetY
        x.snapTo(targetX)
        y.snapTo(targetY)
        return
    }

    if (anchorId != topBlock) {
        val bounds = RealPetRuntime.perches[topBlock]
        if (bounds != null && random.nextFloat() < 0.5f) {
            val targetRange = realPetPerchRange(bounds.left, bounds.right, petSizePx, sidePaddingPx, maxX)
            val targetX = (targetRange.start + targetRange.endInclusive) / 2f
            val targetY = perchY(topBlock, bounds)
            performDropMotion(targetX, targetY, topBlock, startX = targetX, startY = -petSizePx, heightPx = with(density) { 36.dp.toPx() })
        } else {
            moveToPerch(topBlock)
        }
    }

    var currentPerch = topBlock
    if (orderedBlocks.size > 1) {
        val hopsCount = if (orderedBlocks.size > 2 && random.nextBoolean()) 2 else 1
        for (i in 1..hopsCount.coerceAtMost(orderedBlocks.lastIndex)) {
            val nextBlock = orderedBlocks[i]
            delay(if (reduceAnimations) 250L else random.nextLong(450L, 850L))
            moveToPerch(nextBlock)
            currentPerch = nextBlock
        }
    } else {
        val bottomBar = detailBottomBarPerch()
        if (bottomBar != null && bottomBar != currentPerch) {
            delay(if (reduceAnimations) 250L else random.nextLong(450L, 850L))
            moveToPerch(bottomBar)
            currentPerch = bottomBar
        }
    }

    if (currentPerch != detailBottomBarPerch() && random.nextFloat() < 0.45f) {
        val bottomBar = detailBottomBarPerch()
        if (bottomBar != null) {
            delay(if (reduceAnimations) 250L else random.nextLong(450L, 800L))
            moveToPerch(bottomBar)
            currentPerch = bottomBar
        }
    }

    when (random.nextInt(6)) {
        0 -> patrolCurrentSurface(running = false)
        1 -> patrolCurrentSurface(running = true)
        2 -> hopOnPerch(currentPerch)
        3 -> {
            state = RealPetState.Idle
            delay(random.nextLong(1_800L, 3_500L))
        }
        4 -> sitAndObserve(3_000L, 7_000L)
        5 -> takeNap(random.nextLong(10_000L, 20_000L))
    }
}

internal suspend fun RealPetActionScope.performDetailEntranceSide(reminderId: Long) {
    val candidateBlocks = orderedDetailPerches(reminderId).take(2)
    val targetPerch = candidateBlocks.randomOrNull(random) ?: topDetailBlockPerch(reminderId) ?: detailBottomBarPerch() ?: return
    val bounds = RealPetRuntime.perches[targetPerch] ?: return
    val targetFraction = 0.25f + random.nextFloat() * 0.50f
    val target = perchTargetPosition(targetPerch, targetFraction) ?: return

    if (sleepUntil > 0L) {
        anchorId = targetPerch
        sleepX = target.x
        sleepY = target.y
        x.snapTo(target.x)
        y.snapTo(target.y)
        return
    }

    val enterLeft = random.nextBoolean()
    val enterStart = if (enterLeft) -petSizePx * 0.95f else widthPx + petSizePx * 0.02f
    anchorId = null
    x.snapTo(enterStart)
    y.snapTo(target.y)
    facing = if (enterLeft) RealPetFacing.Right else RealPetFacing.Left
    state = RealPetState.Running
    motionStartX = enterStart
    motionStartY = target.y
    motionTargetX = target.x
    motionTargetY = target.y
    motionHeightPx = 0f
    motion = RealPetMotion.Drop
    val travelDistDp = abs(target.x - enterStart) / density.density
    try {
        if (reduceAnimations) {
            x.snapTo(target.x)
        } else {
            motionProgress.snapTo(0f)
            motionProgress.animateTo(
                1f,
                tween(realPetTravelDuration(travelDistDp, running = true), easing = LinearEasing)
            )
        }
    } finally {
        motionProgress.snapTo(0f)
        motion = RealPetMotion.None
        x.snapTo(target.x)
        y.snapTo(target.y)
        lastAnchorX = target.x
        lastAnchorY = target.y
        perchProgress.snapTo(targetFraction)
        anchorId = targetPerch
        state = RealPetState.Idle
    }

    RealHaptics.tick(view)

    when (random.nextInt(6)) {
        0 -> patrolCurrentSurface(running = false)
        1 -> patrolCurrentSurface(running = true)
        2 -> hopOnPerch(targetPerch)
        3 -> sitAndObserve(3_000L, 7_000L)
        4 -> {
            state = RealPetState.Idle
            delay(random.nextLong(1_800L, 3_500L))
        }
        5 -> takeNap(random.nextLong(10_000L, 20_000L))
    }
}

internal suspend fun RealPetActionScope.performDetailEntranceBottomBar(reminderId: Long) {
    val bottomBar = detailBottomBarPerch() ?: topDetailBlockPerch(reminderId) ?: return
    val bounds = RealPetRuntime.perches[bottomBar] ?: return

    if (sleepUntil > 0L) {
        val targetRange = realPetPerchRange(bounds.left, bounds.right, petSizePx, sidePaddingPx, maxX)
        val targetX = (targetRange.start + targetRange.endInclusive) / 2f
        val targetY = perchY(bottomBar, bounds)
        anchorId = bottomBar
        sleepX = targetX
        sleepY = targetY
        x.snapTo(targetX)
        y.snapTo(targetY)
        return
    }

    if (anchorId != bottomBar) {
        if (random.nextFloat() < 0.45f) {
            val targetRange = realPetPerchRange(bounds.left, bounds.right, petSizePx, sidePaddingPx, maxX)
            val targetX = (targetRange.start + targetRange.endInclusive) / 2f
            val targetY = perchY(bottomBar, bounds)
            performDropMotion(targetX, targetY, bottomBar, startX = targetX, startY = -petSizePx, heightPx = with(density) { 36.dp.toPx() })
        } else {
            moveToPerch(bottomBar)
        }
    }

    val canJumpToBlock = visibleDetailBlockPerches(reminderId).isNotEmpty()
    val roll = if (canJumpToBlock) random.nextInt(6) else random.nextInt(5)

    when (roll) {
        0 -> patrolCurrentSurface(running = false)
        1 -> patrolCurrentSurface(running = true)
        2 -> sitAndObserve(3_500L, 7_500L)
        3 -> takeNap(random.nextLong(10_000L, 20_000L))
        4 -> {
            hopOnPerch(bottomBar)
            state = RealPetState.Idle
            delay(random.nextLong(1_500L, 3_000L))
        }
        5 -> {
            val otherBlocks = visibleDetailBlockPerches(reminderId)
            if (otherBlocks.isNotEmpty()) {
                delay(if (reduceAnimations) 250L else random.nextLong(500L, 1_100L))
                val targetBlock = otherBlocks.random(random)
                moveToPerch(targetBlock)
                sitAndObserve(3_000L, 6_000L)
            }
        }
    }
}

internal suspend fun RealPetActionScope.performDetailReturn(
    reminderId: Long,
    preferredScenario: RealPetDetailReturnScenario? = null
) {
    if (RealPetRuntime.presence == RealPetPresence.Hidden) return

    val composerPerch = REAL_PET_PERCH_COMPOSER.takeIf { RealPetRuntime.perches[it] != null }
    val reminderPerch = realPetReminderPerchId(reminderId).takeIf { RealPetRuntime.perches[it] != null }

    val scenario = preferredScenario ?: run {
        val candidates = mutableListOf<RealPetDetailReturnScenario>()
        if (composerPerch != null) candidates += RealPetDetailReturnScenario.JumpToComposer
        if (reminderPerch != null) candidates += RealPetDetailReturnScenario.StayWithReminderCard
        candidates += RealPetDetailReturnScenario.LeaveOffscreen
        candidates.random(random)
    }

    when (scenario) {
        RealPetDetailReturnScenario.JumpToComposer -> {
            if (composerPerch != null) {
                if (sleepUntil > 0L) {
                    val bounds = RealPetRuntime.perches[composerPerch]
                    if (bounds != null) {
                        val pos = perchTargetPosition(composerPerch, 0.5f) ?: Offset(sleepX, sleepY)
                        anchorId = composerPerch
                        sleepX = pos.x
                        sleepY = pos.y
                        x.snapTo(pos.x)
                        y.snapTo(pos.y)
                    }
                    return
                }
                moveToPerch(composerPerch)
                when (random.nextInt(3)) {
                    0 -> patrolCurrentSurface(running = false)
                    1 -> sitAndObserve(3_500L, 8_000L)
                    2 -> {
                        state = RealPetState.Idle
                        delay(random.nextLong(1_800L, 3_500L))
                    }
                }
            } else if (reminderPerch != null) {
                moveToPerch(reminderPerch)
                sitAndObserve(3_000L, 7_000L)
            } else {
                runOffscreenAndLeave()
            }
        }

        RealPetDetailReturnScenario.StayWithReminderCard -> {
            if (reminderPerch != null) {
                if (sleepUntil > 0L) {
                    val bounds = RealPetRuntime.perches[reminderPerch]
                    if (bounds != null) {
                        val pos = perchTargetPosition(reminderPerch, 0.5f) ?: Offset(sleepX, sleepY)
                        anchorId = reminderPerch
                        sleepX = pos.x
                        sleepY = pos.y
                        x.snapTo(pos.x)
                        y.snapTo(pos.y)
                    }
                    return
                }
                moveToPerch(reminderPerch)
                when (random.nextInt(4)) {
                    0 -> sitAndObserve(4_000L, 9_000L)
                    1 -> patrolCurrentSurface(running = false)
                    2 -> {
                        state = RealPetState.Idle
                        delay(random.nextLong(2_000L, 4_000L))
                    }
                    3 -> takeNap(random.nextLong(12_000L, 25_000L))
                }
            } else if (composerPerch != null) {
                moveToPerch(composerPerch)
                sitAndObserve(3_500L, 8_000L)
            } else {
                runOffscreenAndLeave()
            }
        }

        RealPetDetailReturnScenario.LeaveOffscreen -> {
            runOffscreenAndLeave()
        }
    }
}
