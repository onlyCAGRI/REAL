package com.cagri.real.ui.components

import android.content.Context
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cagri.real.data.ReminderItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Calendar
import kotlin.random.Random

internal const val REAL_PET_IDLE_NAP_AFTER_MS = 30_000L
internal const val REAL_PET_IDLE_NAP_EVERY_MS = 10 * 60_000L

/** Process-wide so a recomposed layer does not bring the idle nap back at once. */
private var lastIdleNapElapsed = -REAL_PET_IDLE_NAP_EVERY_MS

/** Cards the cat already sat on before their alert, so each gets one visit per app process. */
private val remindedDueSoon = mutableSetOf<Long>()

/**
 * Stage ("Sahne") for REAL pet companion (PLAN.md Sections 3, 11.3, 12.1).
 *
 * Stage 1 ("Temel") responsibilities:
 * - Binds [RealPetBrain] with SharedPreferences (`Kapalı / Sakin / Normal / Yaramaz`, default `Sakin`).
 * - Dispatches `AppOpened(daysSinceLast, hourOfDay)` once per foreground session (reset after >30m background).
 * - Polls `brain.canStartSpontaneousVisit(now)` once a minute in `Normal`/`Yaramaz` when offscreen and not busy.
 * - Renders [RealPetBody], [RealPetHearts], and [RealPetMeowSound].
 * - Never blocks touches outside the cat's visible body; takes zero space when offscreen or `Kapalı`.
 * - Stops all animations and coroutines immediately when the app goes to background.
 */
@Composable
fun RealPetLayer(
    reduceAnimations: Boolean,
    surfaceActive: Boolean = true,
    @Suppress("UNUSED_PARAMETER") onCreatePersistentCard: (String) -> Unit = {},
    reminders: List<ReminderItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    remember(context) {
        val prefs = context.getSharedPreferences("real_pet_prefs", Context.MODE_PRIVATE)
        RealPetRuntime.bindPreferences(prefs)
        true
    }

    val mode = RealPetRuntime.mode
    if (mode == RealPetMode.Kapali) return

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val isForeground = lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val view = LocalView.current
        val scope = rememberCoroutineScope()
        val random = remember { Random(System.nanoTime().toInt()) }

        val petSize = 114.dp
        val petSizePx = with(density) { petSize.toPx() }
        val sidePaddingPx = with(density) { 8.dp.toPx() }
        val groundPaddingPx = with(density) { 5.dp.toPx() }
        val perchSinkPx = with(density) { 6.dp.toPx() }
        val snapDistancePx = with(density) { 116.dp.toPx() }
        val navigationBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val maxX = (widthPx - petSizePx - sidePaddingPx).coerceAtLeast(sidePaddingPx)
        val groundY = (heightPx - navigationBottomPx - petSizePx - groundPaddingPx).coerceAtLeast(0f)

        val x = remember(widthPx) { Animatable(-petSizePx) }
        val y = remember(heightPx) { Animatable(groundY) }
        val motionProgress = remember { Animatable(0f) }
        val perchProgress = remember { Animatable(0.5f) }

        val stateState = remember { mutableStateOf(RealPetState.Offscreen) }
        val facingState = remember { mutableStateOf(RealPetFacing.Left) }
        val motionState = remember { mutableStateOf(RealPetMotion.None) }
        val jumpVisualState = remember { mutableStateOf(RealPetJumpVisual.ForwardTravel) }
        val motionStartXState = remember { mutableFloatStateOf(sidePaddingPx) }
        val motionStartYState = remember { mutableFloatStateOf(groundY) }
        val motionTargetXState = remember { mutableFloatStateOf(sidePaddingPx) }
        val motionTargetYState = remember { mutableFloatStateOf(groundY) }
        val motionHeightPxState = remember { mutableFloatStateOf(with(density) { 44.dp.toPx() }) }
        val anchorIdState = remember { mutableStateOf<String?>(null) }
        val lastAnchorXState = remember { mutableFloatStateOf(sidePaddingPx) }
        val lastAnchorYState = remember { mutableFloatStateOf(groundY) }
        val sleepPoseIndexState = remember { mutableIntStateOf(-1) }
        val isDraggingState = remember { mutableStateOf(false) }
        val dragXState = remember { mutableFloatStateOf(sidePaddingPx) }
        val dragYState = remember { mutableFloatStateOf(groundY) }
        val visitExtendMsState = remember { mutableLongStateOf(0L) }
        val heartBursts = remember { mutableStateListOf<RealPetHeartBurst>() }
        val nextHeartIdState = remember { mutableIntStateOf(0) }

        val bodyState = remember(
            widthPx,
            heightPx,
            reduceAnimations,
            RealPetRuntime.brain
        ) {
            RealPetBodyState(
                brain = RealPetRuntime.brain,
                density = density,
                view = view,
                coroutineScope = scope,
                random = random,
                reduceAnimations = reduceAnimations,
                petSizePx = petSizePx,
                sidePaddingPx = sidePaddingPx,
                groundPaddingPx = groundPaddingPx,
                perchSinkPx = perchSinkPx,
                snapDistancePx = snapDistancePx,
                widthPx = widthPx,
                heightPx = heightPx,
                maxX = maxX,
                groundY = groundY,
                x = x,
                y = y,
                motionProgress = motionProgress,
                perchProgress = perchProgress,
                stateState = stateState,
                facingState = facingState,
                motionState = motionState,
                jumpVisualState = jumpVisualState,
                motionStartXState = motionStartXState,
                motionStartYState = motionStartYState,
                motionTargetXState = motionTargetXState,
                motionTargetYState = motionTargetYState,
                motionHeightPxState = motionHeightPxState,
                anchorIdState = anchorIdState,
                lastAnchorXState = lastAnchorXState,
                lastAnchorYState = lastAnchorYState,
                sleepPoseIndexState = sleepPoseIndexState,
                isDraggingState = isDraggingState,
                dragXState = dragXState,
                dragYState = dragYState,
                visitExtendMsState = visitExtendMsState,
                heartBursts = heartBursts,
                nextHeartIdState = nextHeartIdState
            )
        }

        // PLAN.md Section 11.3: "Uygulama arka plana geçince her şeyi durdurur."
        // Also tracks >30 min background duration to reset once-per-session AppOpened flag.
        LaunchedEffect(isForeground) {
            val now = System.currentTimeMillis()
            if (isForeground) {
                RealPetRuntime.onAppForegrounded(now)
            } else {
                RealPetRuntime.onAppBackgrounded(now)
                bodyState.stopAll()
                RealPetRuntime.clearComposerTyping()
            }
        }

        if (!isForeground) return@BoxWithConstraints

        // PLAN.md Section 3 Rule 2: "Sen meşgulken gelmez. Yazarken, ses kaydederken,
        // düzenleme ekranında, diyaloglarda ve ayarlarda ortaya çıkmaz. O sırada ekrandaysa kenara çekilip oturur."
        val composerTypingSession = RealPetRuntime.composerTypingSession
        val composerMicActive = RealPetRuntime.composerMicActive
        var micWasActive by remember { mutableStateOf(false) }
        LaunchedEffect(surfaceActive, composerTypingSession, composerMicActive) {
            val isTyping = RealPetRuntime.isComposerTypingNow()
            val busyNow = !surfaceActive || composerMicActive || isTyping
            Log.d("RealPet", "surface active=$surfaceActive typing=$isTyping mic=$composerMicActive")
            bodyState.dispatchEvent(RealPetEvent.UserBusy(busyNow, hidden = !surfaceActive))
            // Mic just turned on: the cat may come and sit by it instead of stepping aside.
            if (composerMicActive && !micWasActive && surfaceActive) bodyState.dispatchEvent(RealPetEvent.MicStarted)
            micWasActive = composerMicActive
            if (isTyping) {
                delay(3_100L)
                val stillBusy = !surfaceActive || RealPetRuntime.composerMicActive || RealPetRuntime.isComposerTypingNow()
                bodyState.dispatchEvent(RealPetEvent.UserBusy(stillBusy))
                if (!stillBusy && RealPetRuntime.composerDraft.isNotBlank()) bodyState.dispatchEvent(RealPetEvent.TypingPaused)
            }
        }

        // Dispatch AppOpened ONCE per foreground session (not on every surface/size change).
        LaunchedEffect(isForeground, surfaceActive, widthPx > petSizePx && heightPx > petSizePx) {
            if (widthPx <= petSizePx || heightPx <= petSizePx) return@LaunchedEffect
            if (!isForeground || !surfaceActive || !mode.isEnabled) return@LaunchedEffect
            if (RealPetRuntime.hasDispatchedAppOpenInSession()) return@LaunchedEffect

            delay(350L) // Allow first-screen perches to measure
            if (!surfaceActive || RealPetRuntime.isComposerTypingNow() || RealPetRuntime.composerMicActive) {
                return@LaunchedEffect
            }
            val now = System.currentTimeMillis()
            val daysSinceLast = RealPetRuntime.consumeSessionAppOpen(now) ?: return@LaunchedEffect
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            bodyState.dispatchEvent(
                event = RealPetEvent.AppOpened(daysSinceLast = daysSinceLast, hourOfDay = currentHour),
                nowMs = now
            )
        }

        // Spontaneous visit scheduler for Normal (~10 min) and Yaramaz (~5 min) of in-app time:
        // every minute the app is open with the cat away counts; checks while not busy.
        LaunchedEffect(isForeground, surfaceActive, mode) {
            if (!isForeground || !surfaceActive || mode.spontaneousIntervalMs == null) return@LaunchedEffect
            while (isActive) {
                delay(60_000L)
                if (bodyState.state == RealPetState.Offscreen) bodyState.brain.noteInAppTime(60_000L)
                val busyNow = !surfaceActive || RealPetRuntime.composerMicActive || RealPetRuntime.isComposerTypingNow()
                if (!busyNow && bodyState.state == RealPetState.Offscreen) {
                    val now = System.currentTimeMillis()
                    if (bodyState.brain.canStartSpontaneousVisit(now)) {
                        bodyState.dispatchEvent(RealPetEvent.SpontaneousTick, nowMs = now)
                    }
                }
            }
        }

        // Support Settings scenario test buttons for Stage 1 states (Walk, Run, Sleep, Sit, Hearts)
        val scenarioTestRequest = RealPetRuntime.scenarioTestRequest
        LaunchedEffect(scenarioTestRequest?.token, surfaceActive) {
            if (!surfaceActive) return@LaunchedEffect
            val req = scenarioTestRequest ?: return@LaunchedEffect
            RealPetRuntime.consumeScenarioTest(req.token)
            when (req.kind) {
                RealPetScenarioTestKind.Walk -> {
                    bodyState.executeIntent(
                        RealPetIntent.Visit(
                            purpose = RealPetVisitPurpose.RoamAndLeave,
                            preferRunning = false,
                            priority = RealPetIntentPriority.UserTouch
                        )
                    )
                }
                RealPetScenarioTestKind.Run -> {
                    bodyState.executeIntent(
                        RealPetIntent.Visit(
                            purpose = RealPetVisitPurpose.RoamAndLeave,
                            preferRunning = true,
                            priority = RealPetIntentPriority.UserTouch
                        )
                    )
                }
                RealPetScenarioTestKind.Sleep,
                RealPetScenarioTestKind.AlreadySleeping -> {
                    bodyState.executeIntent(
                        RealPetIntent.Nap(
                            startAlreadySleeping = req.kind == RealPetScenarioTestKind.AlreadySleeping,
                            priority = RealPetIntentPriority.UserTouch
                        )
                    )
                }
                RealPetScenarioTestKind.RunAcross -> {
                    bodyState.executeIntent(
                        RealPetIntent.Visit(
                            purpose = RealPetVisitPurpose.RunThrough,
                            preferRunning = true,
                            priority = RealPetIntentPriority.UserTouch
                        )
                    )
                }
                RealPetScenarioTestKind.FoundSitting -> {
                    bodyState.executeIntent(
                        RealPetIntent.Visit(
                            purpose = RealPetVisitPurpose.FoundSitting,
                            priority = RealPetIntentPriority.UserTouch
                        )
                    )
                }
                RealPetScenarioTestKind.TypeMeow -> bodyState.executeIntent(
                    RealPetIntent.Prank(RealPetPrankKind.SitOnComposer, priority = RealPetIntentPriority.UserTouch)
                )
                RealPetScenarioTestKind.NamePrank -> bodyState.executeIntent(
                    RealPetIntent.Prank(RealPetPrankKind.CategoryMeowText, priority = RealPetIntentPriority.UserTouch)
                )
                RealPetScenarioTestKind.Drink -> bodyState.executeIntent(RealPetIntent.Pose(RealPetState.Drinking))
                RealPetScenarioTestKind.Purr -> bodyState.executeIntent(RealPetIntent.Pose(RealPetState.Purring))
                RealPetScenarioTestKind.Groom -> bodyState.executeIntent(RealPetIntent.Pose(RealPetState.Grooming))
                RealPetScenarioTestKind.Paw -> bodyState.executeIntent(RealPetIntent.Pose(RealPetState.Pawing))
                RealPetScenarioTestKind.Meow -> bodyState.executeIntent(RealPetIntent.Pose(RealPetState.Meowing))
                RealPetScenarioTestKind.Sit,
                RealPetScenarioTestKind.SitAndObserve,
                RealPetScenarioTestKind.DoubleTapSit -> {
                    bodyState.executeIntent(
                        RealPetIntent.Visit(
                            purpose = RealPetVisitPurpose.RoamAndNap,
                            preferRunning = false,
                            priority = RealPetIntentPriority.UserTouch
                        )
                    )
                }
                RealPetScenarioTestKind.Hearts -> {
                    bodyState.spawnHeartBurst(3)
                }
                RealPetScenarioTestKind.AttentionVisit -> {
                    bodyState.executeIntent(RealPetIntent.AskLove(priority = RealPetIntentPriority.UserTouch))
                }
                RealPetScenarioTestKind.Leave -> {
                    bodyState.executeIntent(RealPetIntent.Leave(priority = RealPetIntentPriority.UserTouch))
                }
                else -> {
                    bodyState.executeIntent(
                        RealPetIntent.Visit(
                            purpose = RealPetVisitPurpose.RoamAndNap,
                            preferRunning = false,
                            priority = RealPetIntentPriority.UserTouch
                        )
                    )
                }
            }
        }

        // Explicit perch placement commands (e.g. Category hero tap)
        val command = RealPetRuntime.command
        LaunchedEffect(command?.token, surfaceActive) {
            if (!surfaceActive) return@LaunchedEffect
            val cmd = command ?: return@LaunchedEffect
            RealPetRuntime.consumeCommand(cmd.token)
            if (RealPetRuntime.perches[cmd.perchId] != null) {
                bodyState.executeIntent(
                    RealPetIntent.Visit(
                        targetPerchId = cmd.perchId,
                        purpose = RealPetVisitPurpose.RoamAndNap,
                        preferRunning = false,
                        priority = RealPetIntentPriority.UserTouch
                    )
                )
            }
        }

        // The cat lives in the page: scrolling carries it (and a card it sits on) out of view and back.
        // A card that scrolls away keeps its last position in bodyState instead of dropping the cat to the ground.
        // Scrolls on another screen (a card's detail) are not this page's; ignored while it is hidden.
        val surfaceShown by rememberUpdatedState(surfaceActive)
        DisposableEffect(bodyState) {
            RealPetRuntime.onContentScrolled = { dy -> if (surfaceShown) bodyState.onContentScrolled(dy) }
            onDispose { RealPetRuntime.onContentScrolled = null }
        }
        LaunchedEffect(bodyState) { bodyState.watchPerchShifts() }

        // 30 s without a touch: comes and dozes by the user (at most every 10 min); a touch startles it awake.
        LaunchedEffect(bodyState, surfaceActive, mode) {
            if (!surfaceActive || mode.spontaneousIntervalMs == null) return@LaunchedEffect
            while (isActive) {
                delay(1_000L)
                if (bodyState.state == RealPetState.Offscreen) bodyState.idleNapActive = false
                val now = android.os.SystemClock.elapsedRealtime()
                val idleFor = now - RealPetRuntime.lastScreenTouchElapsed
                val sinceLast = now - lastIdleNapElapsed
                val busy = RealPetRuntime.composerMicActive || RealPetRuntime.isComposerTypingNow()
                if (idleFor > REAL_PET_IDLE_NAP_AFTER_MS && sinceLast > REAL_PET_IDLE_NAP_EVERY_MS &&
                    bodyState.state == RealPetState.Offscreen && !busy
                ) {
                    lastIdleNapElapsed = now
                    // Set first: the nap starts on its own coroutine, still Offscreen right after dispatch.
                    bodyState.idleNapActive = true
                    bodyState.dispatchEvent(RealPetEvent.UserIdle)
                    delay(1_500L)
                }
            }
        }
        // A card's alert is 15 min or less away and the card is on screen: the cat goes and sits on it.
        val currentReminders by rememberUpdatedState(reminders)
        LaunchedEffect(surfaceActive, mode) {
            if (!surfaceActive || !mode.reactionsAndRemindersEnabled) return@LaunchedEffect
            while (isActive) {
                delay(20_000L)
                if (bodyState.state != RealPetState.Offscreen) continue
                if (RealPetRuntime.composerMicActive || RealPetRuntime.isComposerTypingNow()) continue
                val now = System.currentTimeMillis()
                val due = currentReminders.firstOrNull { r ->
                    val at = r.alertAt
                    r.completedAt == null && at != null && at > now && at - now <= 15 * 60_000L &&
                        r.id !in remindedDueSoon &&
                        realPetReminderPerchId(r.id).let { id ->
                            val b = RealPetRuntime.perches[id]
                            b != null && b.top > 0f && b.bottom < heightPx && !RealPetRuntime.isPerchCovered(id)
                        }
                } ?: continue
                remindedDueSoon += due.id
                bodyState.dispatchEvent(RealPetEvent.ReminderDueSoon(due.id))
            }
        }
        val appEvent = RealPetRuntime.appEvent
        LaunchedEffect(appEvent?.first) {
            val event = appEvent?.second ?: return@LaunchedEffect
            if (!surfaceActive) return@LaunchedEffect
            delay(700L) // a new card needs a frame or two to land in the list and report its bounds
            bodyState.dispatchEvent(event)
        }
        val screenTouch = RealPetRuntime.screenTouchToken
        LaunchedEffect(screenTouch) {
            if (bodyState.idleNapActive) bodyState.startleAwake()
        }

        val currentState = bodyState.state
        val isSoundState = currentState == RealPetState.Meowing || currentState == RealPetState.Calling
        // Never while the mic records: the cat "talking" by the mic is silent.
        if (surfaceActive && !composerMicActive) {
            RealPetMeowSound(
                state = if (isSoundState) RealPetState.Meowing else currentState
            )
        }

        val rx = bodyState.renderX
        val ry = bodyState.renderY
        val onScreenBounds = rx + petSizePx > 0f && rx < widthPx && ry + petSizePx > 0f && ry < heightPx
        val petVisible = surfaceActive && currentState != RealPetState.Offscreen && onScreenBounds && !bodyState.isCovered

        RealPetBody(
            bodyState = bodyState,
            petSize = petSize,
            petVisible = petVisible
        )

        if (surfaceActive) {
            heartBursts.toList().forEach { burst ->
                key(burst.id) {
                    RealPetHearts(burst, reduceAnimations) { heartBursts.remove(burst) }
                }
            }
        }
    }
}