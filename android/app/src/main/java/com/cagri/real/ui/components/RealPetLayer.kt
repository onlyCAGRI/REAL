package com.cagri.real.ui.components

import android.content.Context
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
    @Suppress("UNUSED_PARAMETER") reminders: List<ReminderItem> = emptyList(),
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
        LaunchedEffect(surfaceActive, composerTypingSession, composerMicActive) {
            val isTyping = RealPetRuntime.isComposerTypingNow()
            val busyNow = !surfaceActive || composerMicActive || isTyping
            bodyState.dispatchEvent(RealPetEvent.UserBusy(busyNow))
            if (isTyping) {
                delay(3_100L)
                val stillBusy = !surfaceActive || RealPetRuntime.composerMicActive || RealPetRuntime.isComposerTypingNow()
                bodyState.dispatchEvent(RealPetEvent.UserBusy(stillBusy))
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

        // Spontaneous visit scheduler for Normal (~10 min) and Yaramaz (~5 min):
        // Polls about once a minute while foreground, surface active, not busy, and cat is offscreen.
        LaunchedEffect(isForeground, surfaceActive, mode) {
            if (!isForeground || !surfaceActive || mode.spontaneousIntervalMs == null) return@LaunchedEffect
            while (isActive) {
                delay(60_000L)
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
                            startAlreadySleeping = false,
                            priority = RealPetIntentPriority.UserTouch
                        )
                    )
                }
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
        DisposableEffect(bodyState) {
            RealPetRuntime.onContentScrolled = { dy -> bodyState.onContentScrolled(dy) }
            onDispose { RealPetRuntime.onContentScrolled = null }
        }

        val currentState = bodyState.state
        val isSoundState = currentState == RealPetState.Meowing || currentState == RealPetState.Calling
        if (surfaceActive) {
            RealPetMeowSound(
                state = if (isSoundState) RealPetState.Meowing else currentState
            )
        }

        val rx = bodyState.renderX
        val ry = bodyState.renderY
        val onScreenBounds = rx + petSizePx > 0f && rx < widthPx && ry + petSizePx > 0f && ry < heightPx
        val petVisible = surfaceActive && currentState != RealPetState.Offscreen && onScreenBounds

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