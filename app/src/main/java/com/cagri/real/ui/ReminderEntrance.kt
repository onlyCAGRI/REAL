package com.cagri.real.ui

import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.layout.LayoutCoordinates
import com.cagri.real.data.ReminderCategory
import com.cagri.real.data.CustomCategory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class ReminderEntrance(
    val shouldAnimateColdStart: Boolean,
    val isColdStarting: MutableState<Boolean>,
    val coldStartProgress: Animatable<Float, AnimationVector1D>,
    val containerCoordinates: MutableState<LayoutCoordinates?>,
    val headerCoordinates: MutableState<LayoutCoordinates?>,
    val isFirstHomeIntroPlaying: MutableState<Boolean>,
    val categoryEntranceProgress: Animatable<Float, AnimationVector1D>,
    val composerEntranceProgress: Animatable<Float, AnimationVector1D>,
    val isHomeReadyForShimmer: MutableState<Boolean>
)

@Composable
internal fun rememberReminderEntrance(
    category: ReminderCategory?, customCategory: CustomCategory?, reduceAnimations: Boolean,
    isFirstHomeIntro: Boolean, onFirstHomeIntroComplete: () -> Unit
): ReminderEntrance {
    val shouldAnimateColdStart = remember { category == null && customCategory == null && ColdStartTracker.isEligible(reduceAnimations) }
    val isColdStartingState = remember { mutableStateOf(shouldAnimateColdStart) }
    var isColdStarting by isColdStartingState
    val coldStartProgress = remember { Animatable(if (shouldAnimateColdStart) 0f else 1f) }
    val containerCoordinatesState = remember { mutableStateOf<LayoutCoordinates?>(null) }
    var containerCoordinates by containerCoordinatesState
    val headerCoordinatesState = remember { mutableStateOf<LayoutCoordinates?>(null) }
    var headerCoordinates by headerCoordinatesState

    val isFirstHome = remember { isFirstHomeIntro }
    val shouldAnimateFirstHomeIntro = remember { isFirstHome && !reduceAnimations && category == null && customCategory == null }
    val isFirstHomeIntroPlayingState = remember { mutableStateOf(shouldAnimateFirstHomeIntro) }
    var isFirstHomeIntroPlaying by isFirstHomeIntroPlayingState
    val categoryEntranceProgress = remember { Animatable(if (shouldAnimateFirstHomeIntro) 0f else 1f) }
    val composerEntranceProgress = remember { Animatable(if (shouldAnimateFirstHomeIntro) 0f else 1f) }
    val isHomeReadyForShimmerState = remember { mutableStateOf(!shouldAnimateColdStart && !shouldAnimateFirstHomeIntro) }
    var isHomeReadyForShimmer by isHomeReadyForShimmerState

    DisposableEffect(Unit) {
        onDispose {
            if (isFirstHome) {
                onFirstHomeIntroComplete()
            }
        }
    }

    LaunchedEffect(isFirstHome) {
        if (isFirstHome) {
            if (shouldAnimateFirstHomeIntro) {
                delay(80)
                launch {
                    categoryEntranceProgress.animateTo(
                        1f,
                        animationSpec = tween(505, easing = LinearEasing)
                    )
                }
                delay(330)
                launch {
                    composerEntranceProgress.animateTo(
                        1f,
                        animationSpec = tween(340, easing = LinearEasing)
                    )
                }
                delay(340)
                isFirstHomeIntroPlaying = false
                isHomeReadyForShimmer = true
                onFirstHomeIntroComplete()
            } else {
                isFirstHomeIntroPlaying = false
                isHomeReadyForShimmer = true
                onFirstHomeIntroComplete()
            }
        }
    }

    LaunchedEffect(isColdStarting) {
        if (!isColdStarting && !isFirstHomeIntroPlaying) {
            isHomeReadyForShimmer = true
        }
    }

    val isTargetReady = shouldAnimateColdStart &&
        containerCoordinates?.isAttached == true &&
        headerCoordinates?.isAttached == true

    LaunchedEffect(isTargetReady) {
        if (isTargetReady) {
            ColdStartTracker.markExecuted()
            coldStartProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 400,
                    easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
                )
            )
            isColdStarting = false
        }
    }

    LaunchedEffect(shouldAnimateColdStart) {
        if (shouldAnimateColdStart) {
            delay(1000)
            if (isColdStarting) {
                ColdStartTracker.markExecuted()
                isColdStarting = false
            }
        }
    }
    return ReminderEntrance(shouldAnimateColdStart, isColdStartingState, coldStartProgress,
        containerCoordinatesState, headerCoordinatesState, isFirstHomeIntroPlayingState,
        categoryEntranceProgress, composerEntranceProgress, isHomeReadyForShimmerState)
}
