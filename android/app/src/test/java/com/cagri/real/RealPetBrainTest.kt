package com.cagri.real

import com.cagri.real.ui.components.InMemoryRealPetStore
import com.cagri.real.ui.components.RealPetBrain
import com.cagri.real.ui.components.RealPetEvent
import com.cagri.real.ui.components.RealPetIntent
import com.cagri.real.ui.components.RealPetIntentPriority
import com.cagri.real.ui.components.RealPetMode
import com.cagri.real.ui.components.RealPetReactionKind
import com.cagri.real.ui.components.RealPetState
import com.cagri.real.ui.components.RealPetTransitions
import com.cagri.real.ui.components.RealPetVisitPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

class RealPetBrainTest {
    private lateinit var store: InMemoryRealPetStore
    private lateinit var brain: RealPetBrain
    private val testZone: ZoneId = ZoneId.of("Europe/Istanbul")

    @Before
    fun setUp() {
        store = InMemoryRealPetStore()
        brain = RealPetBrain(store = store, random = Random(42), zoneId = testZone)
    }

    @Test
    fun defaultModeIsNormal() {
        assertEquals(RealPetMode.Normal, brain.mode)
        assertEquals("Normal", brain.mode.labelTr)
        assertTrue(brain.mode.isEnabled)
        assertEquals(10 * 60_000L, brain.mode.spontaneousIntervalMs)
    }

    @Test
    fun modeSettingPersistsAndMapsTableLimits() {
        brain.mode = RealPetMode.Normal
        assertEquals(RealPetMode.Normal, brain.mode)
        assertEquals(10 * 60_000L, brain.mode.spontaneousIntervalMs)
        assertEquals(3, brain.mode.maxDailyAskLove)
        assertEquals(1, brain.mode.maxDailyPranks)

        brain.mode = RealPetMode.Yaramaz
        assertEquals(RealPetMode.Yaramaz, brain.mode)
        assertEquals(5 * 60_000L, brain.mode.spontaneousIntervalMs)
        assertEquals(5, brain.mode.maxDailyAskLove)
        assertEquals(3, brain.mode.maxDailyPranks)
        assertTrue(brain.mischief > 0.7f)

        brain.mode = RealPetMode.Kapali
        assertEquals(RealPetMode.Kapali, brain.mode)
        assertFalse(brain.mode.isEnabled)
    }

    @Test
    fun kapaliModeBlocksAllArrivalsAndLeavesIfVisible() {
        brain.mode = RealPetMode.Kapali
        assertNull(brain.onEvent(RealPetEvent.AppOpened(daysSinceLast = 0, hourOfDay = 9), nowMs = 1_700_000_000_000L))
        assertNull(brain.onEvent(RealPetEvent.AppOpened(daysSinceLast = 5, hourOfDay = 9), nowMs = 1_700_000_000_000L))

        // If pet was on screen when switched to Kapalı, next event returns Leave
        brain.updateBodyState(RealPetState.Sitting)
        val intent = brain.onEvent(RealPetEvent.ScreenChanged("home"), nowMs = 1_700_000_001_000L)
        assertTrue(intent is RealPetIntent.Leave)
    }

    @Test
    fun sakinDoesNotComeByItselfOnAppOpenUnlessAbsentOverThreeDays() {
        val now = 1_700_000_000_000L
        brain.mode = RealPetMode.Sakin

        // Normal daily open in Sakin: must NOT start a visit
        assertNull(brain.onEvent(RealPetEvent.AppOpened(daysSinceLast = 0, hourOfDay = 10), nowMs = now))
        assertNull(brain.onEvent(RealPetEvent.AppOpened(daysSinceLast = 3, hourOfDay = 10), nowMs = now + 1_000L))

        // Section 6D exception: daysSinceLast > 3 starts already sleeping in any enabled mode
        val longAbsenceIntent = brain.onEvent(
            RealPetEvent.AppOpened(daysSinceLast = 4, hourOfDay = 10),
            nowMs = now + 2_000L
        )
        assertTrue(longAbsenceIntent is RealPetIntent.Nap)
        assertTrue((longAbsenceIntent as RealPetIntent.Nap).startAlreadySleeping)
    }

    @Test
    fun normalAndYaramazStartVisitOnAppOpenAndScheduleSpontaneousVisits() {
        val t0 = 1_700_000_000_000L
        brain.mode = RealPetMode.Normal

        // AppOpened in Normal starts a visit and records pet_last_visit_ms
        val openIntent = brain.onEvent(RealPetEvent.AppOpened(daysSinceLast = 0, hourOfDay = 9), nowMs = t0)
        assertNotNull(openIntent)
        assertEquals(t0, store.getLong(RealPetBrain.KEY_LAST_VISIT_MS, 0L))

        // Once cat finishes visit and is Offscreen again:
        brain.clearActiveIntent()
        brain.updateBodyState(RealPetState.Offscreen)

        // Phone time alone does not count: an hour later with no in-app time, still no visit
        assertFalse(brain.canStartSpontaneousVisit(t0 + 60 * 60_000L))

        // 7 in-app minutes is under the 8–12 min window
        brain.noteInAppTime(7 * 60_000L)
        assertFalse(brain.canStartSpontaneousVisit(t0 + 60 * 60_000L))
        assertNull(brain.onEvent(RealPetEvent.SpontaneousTick, nowMs = t0 + 60 * 60_000L))

        // 12 in-app minutes is past any interval: a visit starts, and the in-app counter resets
        brain.noteInAppTime(5 * 60_000L)
        val t1 = t0 + 61 * 60_000L
        assertTrue(brain.canStartSpontaneousVisit(t1))
        val spontaneousIntent = brain.onEvent(RealPetEvent.SpontaneousTick, nowMs = t1)
        assertNotNull(spontaneousIntent)
        assertEquals(t1, store.getLong(RealPetBrain.KEY_LAST_VISIT_MS, 0L))
        assertEquals(0L, store.getLong(RealPetBrain.KEY_IN_APP_SINCE_VISIT_MS, -1L))
        val next = store.getLong(RealPetBrain.KEY_NEXT_VISIT_AFTER_MS, 0L)
        assertTrue(next in 8 * 60_000L..12 * 60_000L)

        // While cat is on screen, canStartSpontaneousVisit is false even after interval
        brain.updateBodyState(RealPetState.Sitting)
        brain.noteInAppTime(15 * 60_000L)
        assertFalse(brain.canStartSpontaneousVisit(t1 + 15 * 60_000L))

        // Sakin never comes by itself, however long the app is open
        brain.clearActiveIntent()
        brain.updateBodyState(RealPetState.Offscreen)
        brain.mode = RealPetMode.Sakin
        assertFalse(brain.canStartSpontaneousVisit(t1 + 30 * 60_000L))
    }

    @Test
    fun wallClockLastOpenComputesRealDaysSinceLastAndLocalDateRolloverResetsDailyCounters() {
        val day1Morning = LocalDate.of(2026, 10, 20)
            .atTime(9, 0)
            .atZone(testZone)
            .toInstant()
            .toEpochMilli()
        val day1LateEvening = LocalDate.of(2026, 10, 20)
            .atTime(23, 50)
            .atZone(testZone)
            .toInstant()
            .toEpochMilli()
        val day2JustAfterMidnight = LocalDate.of(2026, 10, 21)
            .atTime(0, 5)
            .atZone(testZone)
            .toInstant()
            .toEpochMilli()
        val day6Afternoon = LocalDate.of(2026, 10, 25)
            .atTime(14, 0)
            .atZone(testZone)
            .toInstant()
            .toEpochMilli()

        // First ever open -> 0 daysSinceLast, stores pet_last_open_ms
        assertEquals(0, brain.recordAppOpenAndComputeDaysSinceLast(day1Morning))
        assertEquals(day1Morning, store.getLong(RealPetBrain.KEY_LAST_OPEN_MS, 0L))

        // Same local day later in the evening -> 0 daysSinceLast
        assertEquals(0, brain.recordAppOpenAndComputeDaysSinceLast(day1LateEvening))

        // Simulate daily counters accumulated on day 1
        store.putInt(RealPetBrain.KEY_ASK_LOVE_TODAY, 1)
        store.putInt(RealPetBrain.KEY_PRANK_TODAY, 2)
        assertEquals(1, brain.askLoveCountToday(day1LateEvening))
        assertEquals(2, brain.prankCountToday(day1LateEvening))

        // Next local day 15 minutes later (across local midnight) -> 1 day elapsed AND daily counters reset!
        assertEquals(1, brain.recordAppOpenAndComputeDaysSinceLast(day2JustAfterMidnight))
        assertEquals(0, brain.askLoveCountToday(day2JustAfterMidnight))
        assertEquals(0, brain.prankCountToday(day2JustAfterMidnight))

        // 4 calendar days later -> 4 daysSinceLast
        assertEquals(4, brain.recordAppOpenAndComputeDaysSinceLast(day6Afternoon))
    }

    @Test
    fun energyIsHigherInMorningAndLowerAtNight() {
        val morningEnergy = brain.computeEnergyForHour(hourOfDay = 8, daysSinceLast = 1)
        val nightEnergy = brain.computeEnergyForHour(hourOfDay = 2, daysSinceLast = 1)
        assertTrue(morningEnergy > 0.75f)
        assertTrue(nightEnergy < 0.35f)
        assertTrue(morningEnergy > nightEnergy)
    }

    @Test
    fun loveNeedGrowsOverTimeAndResetsOnAffection() {
        val now = 1_700_000_000_000L
        val initialLove = brain.loveNeed
        brain.advanceTime(deltaMs = 3_600_000L, nowMs = now)
        assertTrue(brain.loveNeed > initialLove)

        brain.updateBodyState(RealPetState.Sitting)
        val intent = brain.onEvent(RealPetEvent.LongPress, nowMs = now + 10_000L)
        assertEquals(0f, brain.loveNeed, 0.001f)
        assertTrue(intent is RealPetIntent.React)
        assertEquals(RealPetReactionKind.HoldPurr, (intent as RealPetIntent.React).kind)
    }

    @Test
    fun userBusyPreventsArrivalAndStepsAsideWhenOnScreen() {
        val now = 1_700_000_000_000L
        brain.mode = RealPetMode.Normal

        // When offscreen and user starts typing/busy, pet does not arrive
        assertNull(brain.onEvent(RealPetEvent.UserBusy(true), nowMs = now))
        assertTrue(brain.isUserBusy)
        assertNull(brain.onEvent(RealPetEvent.AppOpened(daysSinceLast = 0, hourOfDay = 10), nowMs = now + 1_000L))

        // When on screen and user becomes busy, pet steps aside and sits
        brain.onEvent(RealPetEvent.UserBusy(false), nowMs = now + 2_000L)
        brain.updateBodyState(RealPetState.Walking)
        val stepAside = brain.onEvent(RealPetEvent.UserBusy(true), nowMs = now + 3_000L)
        assertTrue(stepAside is RealPetIntent.Visit)
        assertEquals(RealPetVisitPurpose.StepAsideAndSit, (stepAside as RealPetIntent.Visit).purpose)
    }

    @Test
    fun touchEventsProduceCorrectStage1ReactionsWithHighestPriority() {
        val now = 1_700_000_000_000L

        // Tapping while sleeping wakes up and sits
        brain.updateBodyState(RealPetState.Sleeping)
        val wakeIntent = brain.onEvent(RealPetEvent.Tap, nowMs = now) as? RealPetIntent.React
        assertNotNull(wakeIntent)
        assertEquals(RealPetReactionKind.WakeAndSit, wakeIntent!!.kind)
        assertEquals(RealPetIntentPriority.UserTouch, wakeIntent.priority)

        // Tapping while running stops, turns, and sits
        brain.updateBodyState(RealPetState.Running)
        val stopIntent = brain.onEvent(RealPetEvent.Tap, nowMs = now + 1_000L) as? RealPetIntent.React
        assertNotNull(stopIntent)
        assertEquals(RealPetReactionKind.StopTurnAndSit, stopIntent!!.kind)

        // Repeated tap purrs and lies down
        brain.updateBodyState(RealPetState.Sitting)
        val repeatedIntent = brain.onEvent(RealPetEvent.RepeatedTap, nowMs = now + 2_000L) as? RealPetIntent.React
        assertNotNull(repeatedIntent)
        assertEquals(RealPetReactionKind.RepeatedTapPurrAndLieDown, repeatedIntent!!.kind)

        // Fling offscreen triggers running leave and wall-clock cooldown
        val flingIntent = brain.onEvent(RealPetEvent.FlungOffscreen, nowMs = now + 5_000L) as? RealPetIntent.Leave
        assertNotNull(flingIntent)
        assertTrue(flingIntent!!.running)
        assertTrue(brain.isInCooldown(nowMs = now + 10_000L))
        assertFalse(brain.isInCooldown(nowMs = now + 5_000L + RealPetBrain.FLING_COOLDOWN_MS + 1L))
    }

    @Test
    fun section5TransitionTableEnforcesNaturalOrder() {
        assertFalse(RealPetTransitions.canTransition(RealPetState.Sleeping, RealPetState.Sitting))
        assertFalse(RealPetTransitions.canTransition(RealPetState.Sleeping, RealPetState.Walking))
        assertTrue(RealPetTransitions.canTransition(RealPetState.Sleeping, RealPetState.Waking))
        assertTrue(RealPetTransitions.canTransition(RealPetState.Waking, RealPetState.Sitting))

        val wakeToSitPath = RealPetTransitions.findPath(RealPetState.Sleeping, RealPetState.Sitting)
        assertEquals(listOf(RealPetState.Waking, RealPetState.Sitting), wakeToSitPath)

        assertFalse(RealPetTransitions.canTransition(RealPetState.Sitting, RealPetState.Sleeping))
        val sitToSleepPath = RealPetTransitions.findPath(RealPetState.Sitting, RealPetState.Sleeping)
        assertEquals(listOf(RealPetState.LyingDown, RealPetState.Sleeping), sitToSleepPath)

        val sleepToOffscreen = RealPetTransitions.findPath(RealPetState.Sleeping, RealPetState.Offscreen)
        assertEquals(
            listOf(
                RealPetState.Waking,
                RealPetState.Sitting,
                RealPetState.Idle,
                RealPetState.Walking,
                RealPetState.Offscreen
            ),
            sleepToOffscreen
        )

        for (seatedAction in listOf(
            RealPetState.Purring,
            RealPetState.Grooming,
            RealPetState.Calling,
            RealPetState.Pawing,
            RealPetState.Drinking
        )) {
            assertEquals(setOf(RealPetState.Sitting), RealPetTransitions.allowedNextStates(seatedAction))
        }
    }
}