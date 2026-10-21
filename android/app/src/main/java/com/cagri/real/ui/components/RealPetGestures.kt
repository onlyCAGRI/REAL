package com.cagri.real.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Direct user touch interactions (drag, double tap, single tap, long-press purr) for REAL pet.
 */
internal fun Modifier.realPetGestures(
    scope: RealPetActionScope,
    petVisible: Boolean
): Modifier = this
    .pointerInput(scope.widthPx, scope.heightPx, scope.reduceAnimations, petVisible, RealPetRuntime.composerMicActive, scope.anchorId, scope.motionTargetPerchId) {
        val micRecordingAtMic = (scope.anchorId == REAL_PET_PERCH_COMPOSER_MIC || scope.motionTargetPerchId == REAL_PET_PERCH_COMPOSER_MIC) && RealPetRuntime.composerMicActive
        if (!petVisible || micRecordingAtMic) return@pointerInput
        detectDragGestures(
            onDragStart = {
                RealPetRuntime.noteUserTouch()
                scope.dragX = scope.latestRenderX.value
                scope.dragY = scope.latestRenderY.value
                scope.anchorId = null
                // Mark dragging before cancelling motions so a cancelled leave animation cannot
                // incorrectly finish by hiding the pet underneath the user's finger.
                scope.isDragging = true
                scope.requestScenarioResumeAfterInterruption(pauseMs = 2_000L)
                val currentExclusive = scope.activeExclusiveJob
                if (currentExclusive?.isActive == true) {
                    currentExclusive.cancel()
                }
                scope.doubleTapSitActive = false
                scope.sitUntilDeadline = 0L
                scope.motion = RealPetMotion.None
                // Dragging a sleeping pet preserves the original sleep deadline.
                scope.state = if (scope.sleepUntil > SystemClock.elapsedRealtime()) {
                    RealPetState.Sleeping
                } else {
                    RealPetState.Idle
                }
                RealPetRuntime.markArrived()
                RealPetRuntime.extendVisit(15_000L)
            },
            onDrag = { change, dragAmount ->
                change.consume()
                scope.dragX = (scope.dragX + dragAmount.x).coerceIn(scope.sidePaddingPx, scope.maxX)
                scope.dragY = (scope.dragY + dragAmount.y).coerceIn(-scope.petSizePx * 0.5f, scope.heightPx - scope.petSizePx * 0.25f)
            },
            onDragEnd = {
                scope.isDragging = false
                val footPoint = Offset(scope.dragX + scope.petSizePx / 2f, scope.dragY + scope.petSizePx - scope.perchSinkPx)
                val nearest = RealPetRuntime.nearestPerch(footPoint, scope.snapDistancePx)
                if (nearest != null) {
                    val bounds = RealPetRuntime.perches[nearest]
                    if (bounds != null) {
                        val range = realPetPerchRange(bounds.left, bounds.right, scope.petSizePx, scope.sidePaddingPx, scope.maxX)
                        val targetX = scope.dragX.coerceIn(range.start, range.endInclusive)
                        val targetFraction = if (range.endInclusive > range.start) {
                            (targetX - range.start) / (range.endInclusive - range.start)
                        } else 0.5f
                        val targetY = scope.perchY(nearest, bounds)
                        scope.coroutineScope.launch {
                            scope.withExclusivePetMotion("dragSettlePerch") {
                                scope.motionStartX = scope.dragX
                                scope.motionStartY = scope.dragY
                                scope.motionTargetX = targetX
                                scope.motionTargetY = targetY
                                scope.motionTargetPerchId = nearest
                                scope.motionHeightPx = with(scope.density) { 24.dp.toPx() }
                                scope.jumpVisual = RealPetJumpVisual.LegacyHop
                                scope.motion = if (scope.reduceAnimations) RealPetMotion.None else RealPetMotion.Jump
                                scope.state = RealPetState.Jumping
                                try {
                                    if (!scope.reduceAnimations) {
                                        scope.motionProgress.snapTo(0f)
                                        scope.motionProgress.animateTo(1f, tween(300))
                                        scope.motionProgress.snapTo(0f)
                                    }
                                    scope.motion = RealPetMotion.None
                                    scope.x.snapTo(targetX)
                                    scope.y.snapTo(targetY)
                                    RealHaptics.tick(scope.view)
                                } finally {
                                    scope.motion = RealPetMotion.None
                                    scope.motionProgress.snapTo(0f)
                                    scope.lastAnchorX = targetX
                                    scope.lastAnchorY = targetY
                                    scope.perchProgress.snapTo(targetFraction)
                                    scope.motionTargetPerchId = null
                                    scope.anchorId = nearest
                                    if (scope.sleepUntil > SystemClock.elapsedRealtime()) {
                                        scope.sleepX = targetX
                                        scope.sleepY = targetY
                                        scope.sleepPerchFraction = targetFraction
                                        scope.state = RealPetState.Sleeping
                                    } else {
                                        scope.state = RealPetState.Idle
                                    }
                                }
                                scope.restAfterUserPlacement()
                            }
                        }
                    }
                } else {
                    scope.coroutineScope.launch {
                        scope.withExclusivePetMotion("dragSettleGround") {
                            val targetX = scope.dragX.coerceIn(scope.sidePaddingPx, scope.maxX)
                            scope.x.snapTo(targetX)
                            try {
                                if (scope.reduceAnimations) scope.y.snapTo(scope.groundY)
                                else scope.y.animateTo(scope.groundY, tween(420))
                            } finally {
                                RealHaptics.tick(scope.view)
                                scope.anchorId = null
                                if (scope.sleepUntil > SystemClock.elapsedRealtime()) {
                                    scope.sleepX = targetX
                                    scope.sleepY = scope.groundY
                                    scope.state = RealPetState.Sleeping
                                } else {
                                    scope.state = RealPetState.Idle
                                }
                            }
                            scope.restAfterUserPlacement()
                        }
                    }
                }
            },
            onDragCancel = {
                scope.isDragging = false
                scope.coroutineScope.launch {
                    scope.withExclusivePetMotion("dragCancelGround") {
                        val targetX = scope.dragX.coerceIn(scope.sidePaddingPx, scope.maxX)
                        scope.x.snapTo(targetX)
                        try {
                            if (scope.reduceAnimations) scope.y.snapTo(scope.groundY)
                            else scope.y.animateTo(scope.groundY, tween(420))
                        } finally {
                            scope.anchorId = null
                            if (scope.sleepUntil > SystemClock.elapsedRealtime()) {
                                scope.sleepX = targetX
                                scope.sleepY = scope.groundY
                                scope.state = RealPetState.Sleeping
                            } else {
                                scope.state = RealPetState.Idle
                            }
                        }
                        scope.restAfterUserPlacement()
                    }
                }
            }
        )
    }
    .pointerInput(scope.petSizePx, petVisible, RealPetRuntime.composerMicActive, scope.anchorId, scope.motionTargetPerchId) {
        val micRecordingAtMic = (scope.anchorId == REAL_PET_PERCH_COMPOSER_MIC || scope.motionTargetPerchId == REAL_PET_PERCH_COMPOSER_MIC) && RealPetRuntime.composerMicActive
        if (!petVisible || micRecordingAtMic) return@pointerInput

        var heldFor3Seconds = false
        var lastHeartBurstTimestamp = 0L

        fun spawnAffectionateHearts() {
            val now = SystemClock.elapsedRealtime()
            if (now - lastHeartBurstTimestamp < 140L) return
            lastHeartBurstTimestamp = now

            // Affectionate taps are overlays on top of the current scenario; they should
            // not kill the scenario or reset its route.
            RealPetRuntime.noteUserTouch()
            RealHaptics.tick(scope.view)
            RealPetRuntime.extendVisit(25_000L)

            if (scope.peekActive) {
                scope.peekFleeRequested = true
            } else {
                if (scope.attentionActive) {
                    scope.attentionTapCount = (scope.attentionTapCount + 1).coerceAtMost(scope.attentionTapGoal)
                    RealPetRuntime.extendVisit(4_000L)
                }
                if (scope.heartBursts.size >= 6) scope.heartBursts.removeAt(0)
                scope.heartBursts.add(
                    RealPetHeartBurst(
                        id = scope.nextHeartId++,
                        origin = Offset(
                            scope.latestHeartOriginX.value,
                            scope.latestHeartOriginY.value
                        )
                    )
                )
            }
        }

        detectTapGestures(
            onPress = onPress@{
                if (scope.isDragging) return@onPress
                heldFor3Seconds = false
                val holdTimerJob = scope.coroutineScope.launch {
                    delay(3000L)
                    heldFor3Seconds = true
                    RealHaptics.success(scope.view)
                    scope.coroutineScope.launch {
                        scope.withExclusivePetMotion("holdSit") {
                            scope.sleepUntil = 0L
                            scope.sitAndObserve(4_000L, 6_000L)
                        }
                    }
                }
                try {
                    tryAwaitRelease()
                } finally {
                    holdTimerJob.cancel()
                }
            },
            onDoubleTap = onDoubleTap@{
                if (scope.isDragging) return@onDoubleTap
                // Double tap must NOT sit; treat as affectionate tap producing hearts
                spawnAffectionateHearts()
            },
            onTap = onTap@{
                if (scope.isDragging) return@onTap
                if (heldFor3Seconds) {
                    heldFor3Seconds = false
                    return@onTap
                }
                spawnAffectionateHearts()
            }
        )
    }
