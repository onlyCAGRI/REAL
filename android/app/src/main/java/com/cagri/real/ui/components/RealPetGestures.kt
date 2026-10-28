package com.cagri.real.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Direct user touch interactions for REAL pet (PLAN.md Sections 7, 10, 11.1, 12.1):
 * - Dokunma (Tap): Kalp, biraz daha kalır (uyurken -> uyanır ve oturur; koşarken -> durur, döner, oturur)
 * - Art arda dokunma (RepeatedTap): Kalpler çoğalır, mırlar, sonra uzanır
 * - Basılı tutma (LongPress): Oturur, mırlar (purr)
 * - Sürükleme (Drag): Bıraktığın yere oturur; kartın üstüne bırakılırsa orada kalır
 * - Ekrandan dışarı fırlatma (FlungOffscreen): Koşarak çıkar, bir süre gelmez
 */
internal fun Modifier.realPetGestures(
    bodyState: RealPetBodyState,
    petVisible: Boolean
): Modifier = this
    .pointerInput(
        bodyState.widthPx,
        bodyState.heightPx,
        bodyState.reduceAnimations,
        petVisible,
        RealPetRuntime.composerMicActive
    ) {
        if (!petVisible || RealPetRuntime.composerMicActive) return@pointerInput

        var totalDragDx = 0f
        var lastStepDx = 0f

        detectDragGestures(
            onDragStart = {
                totalDragDx = 0f
                lastStepDx = 0f
                RealPetRuntime.noteUserTouch()
                bodyState.dragX = bodyState.renderX
                bodyState.dragY = bodyState.renderY
                bodyState.anchorId = null
                bodyState.isDragging = true
                bodyState.cancelActiveIntent()
                bodyState.motion = RealPetMotion.None
                RealPetRuntime.markArrived()
                RealPetRuntime.extendVisit(15_000L)
            },
            onDrag = { change, dragAmount ->
                change.consume()
                totalDragDx += dragAmount.x
                lastStepDx = dragAmount.x
                bodyState.dragX = (bodyState.dragX + dragAmount.x)
                    .coerceIn(-bodyState.petSizePx * 0.45f, bodyState.widthPx - bodyState.petSizePx * 0.55f)
                bodyState.dragY = (bodyState.dragY + dragAmount.y)
                    .coerceIn(-bodyState.petSizePx * 0.35f, bodyState.heightPx - bodyState.petSizePx * 0.25f)
            },
            onDragEnd = {
                bodyState.isDragging = false
                val flungLeft = bodyState.dragX <= bodyState.sidePaddingPx * 0.75f &&
                    (lastStepDx < -12f || totalDragDx < -bodyState.petSizePx * 0.65f)
                val flungRight = bodyState.dragX >= bodyState.maxX - bodyState.sidePaddingPx * 0.75f &&
                    (lastStepDx > 12f || totalDragDx > bodyState.petSizePx * 0.65f)

                if (flungLeft || flungRight) {
                    bodyState.coroutineScope.launch {
                        bodyState.x.snapTo(bodyState.dragX)
                        bodyState.y.snapTo(bodyState.dragY.coerceIn(0f, bodyState.groundY))
                        bodyState.dispatchEvent(RealPetEvent.FlungOffscreen)
                    }
                    return@detectDragGestures
                }

                val footPoint = Offset(
                    x = bodyState.dragX + bodyState.petSizePx / 2f,
                    y = bodyState.dragY + bodyState.petSizePx - bodyState.perchSinkPx
                )
                val nearest = RealPetRuntime.nearestPerch(footPoint, bodyState.snapDistancePx)
                    ?.takeIf { !realPetPerchIsInteractiveButton(it) }
                val bounds = nearest?.let { RealPetRuntime.perches[it] }

                if (nearest != null && bounds != null) {
                    val range = realPetPerchRange(
                        bounds.left,
                        bounds.right,
                        bodyState.petSizePx,
                        bodyState.sidePaddingPx,
                        bodyState.maxX
                    )
                    val targetX = bodyState.dragX.coerceIn(range.start, range.endInclusive)
                    val targetFraction = if (range.endInclusive > range.start) {
                        (targetX - range.start) / (range.endInclusive - range.start)
                    } else {
                        0.5f
                    }
                    val targetY = bodyState.perchY(nearest, bounds)
                    bodyState.coroutineScope.launch {
                        bodyState.motionStartX = bodyState.dragX
                        bodyState.motionStartY = bodyState.dragY
                        bodyState.motionTargetX = targetX
                        bodyState.motionTargetY = targetY
                        bodyState.motionHeightPx = with(bodyState.density) { 20.dp.toPx() }
                        bodyState.jumpVisual = RealPetJumpVisual.LegacyHop
                        bodyState.motion = if (bodyState.reduceAnimations) RealPetMotion.None else RealPetMotion.Jump
                        try {
                            if (!bodyState.reduceAnimations) {
                                bodyState.motionProgress.snapTo(0f)
                                bodyState.motionProgress.animateTo(1f, tween(280))
                            }
                        } finally {
                            bodyState.motionProgress.snapTo(0f)
                            bodyState.motion = RealPetMotion.None
                            bodyState.x.snapTo(targetX)
                            bodyState.y.snapTo(targetY)
                            bodyState.lastAnchorX = targetX
                            bodyState.lastAnchorY = targetY
                            bodyState.perchProgress.snapTo(targetFraction)
                            bodyState.anchorId = nearest
                            RealHaptics.tick(bodyState.view)
                        }
                        bodyState.dispatchEvent(RealPetEvent.Dragged(nearest))
                    }
                } else {
                    val targetX = bodyState.dragX.coerceIn(bodyState.sidePaddingPx, bodyState.maxX)
                    bodyState.coroutineScope.launch {
                        bodyState.anchorId = null
                        bodyState.x.snapTo(targetX)
                        bodyState.y.snapTo(bodyState.dragY)
                        try {
                            if (bodyState.reduceAnimations) {
                                bodyState.y.snapTo(bodyState.groundY)
                            } else {
                                bodyState.y.animateTo(bodyState.groundY, tween(360))
                            }
                        } finally {
                            RealHaptics.tick(bodyState.view)
                        }
                        bodyState.dispatchEvent(RealPetEvent.Dragged(null))
                    }
                }
            },
            onDragCancel = {
                bodyState.isDragging = false
                val targetX = bodyState.dragX.coerceIn(bodyState.sidePaddingPx, bodyState.maxX)
                bodyState.coroutineScope.launch {
                    bodyState.anchorId = null
                    bodyState.x.snapTo(targetX)
                    if (bodyState.reduceAnimations) {
                        bodyState.y.snapTo(bodyState.groundY)
                    } else {
                        bodyState.y.animateTo(bodyState.groundY, tween(360))
                    }
                    bodyState.dispatchEvent(RealPetEvent.Dragged(null))
                }
            }
        )
    }
    .pointerInput(bodyState.petSizePx, petVisible, RealPetRuntime.composerMicActive) {
        if (!petVisible || RealPetRuntime.composerMicActive) return@pointerInput

        var rapidTapCount = 0
        var lastTapElapsedMs = 0L

        fun handleTap() {
            if (bodyState.isDragging) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastTapElapsedMs <= 1_400L) {
                rapidTapCount += 1
            } else {
                rapidTapCount = 1
            }
            lastTapElapsedMs = now
            RealPetRuntime.noteUserTouch()

            if (rapidTapCount >= 3) {
                rapidTapCount = 0
                bodyState.dispatchEvent(RealPetEvent.RepeatedTap)
            } else {
                bodyState.dispatchEvent(RealPetEvent.Tap)
            }
        }

        detectTapGestures(
            onLongPress = {
                if (bodyState.isDragging) return@detectTapGestures
                rapidTapCount = 0
                RealPetRuntime.noteUserTouch()
                bodyState.dispatchEvent(RealPetEvent.LongPress)
            },
            onDoubleTap = {
                handleTap()
            },
            onTap = {
                handleTap()
            }
        )
    }