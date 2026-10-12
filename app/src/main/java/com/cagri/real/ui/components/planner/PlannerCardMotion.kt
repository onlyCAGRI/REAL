package com.cagri.real.ui.components.planner

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalSharedTransitionApi::class)
internal class PlannerCardMotion(val scope: SharedTransitionScope) {
    var selectedKey by mutableStateOf<String?>(null)
    var openedId by mutableStateOf<Long?>(null)
    var reduceMotion by mutableStateOf(false)
}

internal val LocalPlannerCardMotion = staticCompositionLocalOf<PlannerCardMotion?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.plannerEventMotion(event: PlannerEvent, surface: String, onOpen: () -> Unit): Modifier {
    val motion = LocalPlannerCardMotion.current
    val key = "planner:$surface:${event.occurrenceKey()}"
    val shared = if (motion != null && !motion.reduceMotion) {
        with(motion.scope) {
            Modifier.sharedElementWithCallerManagedVisibility(
                rememberSharedContentState(key),
                visible = motion.selectedKey != key || motion.openedId == null,
                boundsTransform = { _, _ -> tween(320, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)) },
                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(16.dp)),
                renderInOverlayDuringTransition = isTransitionActive
            )
        }
    } else Modifier
    return this.then(shared).clickable(role = Role.Button) {
        motion?.selectedKey = key
        onOpen()
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun plannerDetailMotion(
    reminderId: Long,
    renderInOverlay: Boolean = true
): Modifier {
    val motion = LocalPlannerCardMotion.current ?: return Modifier
    val key = motion.selectedKey ?: return Modifier
    if (motion.reduceMotion) return Modifier
    return with(motion.scope) {
        Modifier.sharedElementWithCallerManagedVisibility(
            rememberSharedContentState(key),
            visible = motion.openedId == reminderId,
            boundsTransform = { _, _ -> tween(320, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)) },
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(28.dp)),
            renderInOverlayDuringTransition = isTransitionActive && renderInOverlay
        )
    }
}
