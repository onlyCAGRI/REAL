package com.cagri.bigboscagri.ui.components

import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlin.random.Random

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
            val details = visibleDetailPerches()
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
