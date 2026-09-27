package com.cagri.bigboscagri.ui.components

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
                // Explicitly picking up the cat wakes it; ordinary navigation does not.
                scope.sleepUntil = 0L
                scope.dragX = scope.latestRenderX.value
                scope.dragY = scope.latestRenderY.value
                scope.anchorId = null
                val currentScenario = scope.activeScenarioJob
                if (currentScenario?.isActive == true) {
                    currentScenario.cancel()
                }
                val currentExclusive = scope.activeExclusiveJob
                if (currentExclusive?.isActive == true) {
                    currentExclusive.cancel()
                }
                scope.doubleTapSitActive = false
                scope.sitUntilDeadline = 0L
                scope.motion = RealPetMotion.None
                scope.isDragging = true
                scope.state = RealPetState.Idle
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
                                    scope.state = RealPetState.Idle
                                }
                            }
                        }
                    }
                } else {
                    scope.coroutineScope.launch {
                        scope.withExclusivePetMotion("dragSettleGround") {
                            scope.x.snapTo(scope.dragX.coerceIn(scope.sidePaddingPx, scope.maxX))
                            try {
                                if (scope.reduceAnimations) scope.y.snapTo(scope.groundY)
                                else scope.y.animateTo(scope.groundY, tween(420))
                            } finally {
                                RealHaptics.tick(scope.view)
                                scope.state = RealPetState.Idle
                            }
                        }
                    }
                }
            },
            onDragCancel = {
                scope.isDragging = false
                scope.coroutineScope.launch {
                    scope.withExclusivePetMotion("dragCancelGround") {
                        scope.x.snapTo(scope.dragX.coerceIn(scope.sidePaddingPx, scope.maxX))
                        try {
                            if (scope.reduceAnimations) scope.y.snapTo(scope.groundY)
                            else scope.y.animateTo(scope.groundY, tween(420))
                        } finally {
                            scope.state = RealPetState.Idle
                        }
                    }
                }
            }
        )
    }
    .pointerInput(scope.petSizePx, petVisible, RealPetRuntime.composerMicActive, scope.anchorId, scope.motionTargetPerchId) {
        val micRecordingAtMic = (scope.anchorId == REAL_PET_PERCH_COMPOSER_MIC || scope.motionTargetPerchId == REAL_PET_PERCH_COMPOSER_MIC) && RealPetRuntime.composerMicActive
        if (!petVisible || micRecordingAtMic) return@pointerInput
        detectTapGestures(
            onDoubleTap = {
                if (scope.isDragging) return@detectTapGestures
                RealPetRuntime.noteUserTouch()
                RealHaptics.tick(scope.view)
                RealPetRuntime.extendVisit(35_000L)
                if (scope.doubleTapSitActive) {
                    scope.sitUntilDeadline = SystemClock.elapsedRealtime() + 30_000L
                    RealHaptics.success(scope.view)
                    return@detectTapGestures
                }
                scope.coroutineScope.launch {
                    scope.withExclusivePetMotion("doubleTapSit") {
                        scope.doubleTapSitActive = true
                        try {
                            RealPetRuntime.markArrived()
                            scope.performDoubleTapSit()
                        } finally {
                            scope.doubleTapSitActive = false
                            scope.sitUntilDeadline = 0L
                        }
                    }
                }
            },
            onTap = {
                RealPetRuntime.noteUserTouch()
                RealHaptics.tick(scope.view)
                if (scope.peekActive) {
                    scope.peekFleeRequested = true
                } else {
                    if (scope.attentionActive) {
                        scope.attentionTapCount = (scope.attentionTapCount + 1).coerceAtMost(scope.attentionTapGoal)
                        RealPetRuntime.extendVisit(4_000L)
                    }
                    // Drag consumes movement, so releasing a drag does not spawn hearts.
                    if (scope.heartBursts.size >= 4) scope.heartBursts.removeAt(0)
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
            },
            onLongPress = {
                RealPetRuntime.noteUserTouch()
                RealPetRuntime.extendVisit(60_000L)
                RealHaptics.success(scope.view)
                scope.meowCaption = "Purr..."
                scope.coroutineScope.launch {
                    repeat(3) {
                        if (scope.heartBursts.size >= 4) scope.heartBursts.removeAt(0)
                        scope.heartBursts.add(
                            RealPetHeartBurst(
                                id = scope.nextHeartId++,
                                origin = Offset(
                                    scope.latestHeartOriginX.value,
                                    scope.latestHeartOriginY.value
                                )
                            )
                        )
                        delay(200L)
                    }
                    delay(1200L)
                    if (scope.meowCaption == "Purr...") scope.meowCaption = null
                }
            }
        )
    }
