package com.cagri.real.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker

/** Drag progress shared between the detail card gesture and the route transition. */
internal class DetailPullDismissState {
    var active by mutableStateOf(false)
    var distancePx by mutableFloatStateOf(0f)
}

internal class DetailPullDismiss(
    val onStart: () -> Unit,
    /** Cumulative downward distance in px (never negative). */
    val onDrag: (Float) -> Unit,
    /** Final distance in px and vertical velocity in px/s (positive = downward). */
    val onEnd: (Float, Float) -> Unit,
    val onCancel: () -> Unit
)

internal fun shouldCommitDetailPull(distance: Float, velocity: Float, travel: Float, fling: Float): Boolean =
    distance.isFinite() && velocity.isFinite() && travel > 0f &&
        (distance / travel >= 0.33f || (distance / travel >= 0.06f && velocity >= fling))

/**
 * Lets the user grab the main detail card and pull it down. Only downward drags are taken,
 * and only while the detail content is scrolled to the top, so normal scrolling, text
 * selection and horizontal gestures are untouched.
 */
@Composable
internal fun Modifier.pullDownToDismiss(callbacks: DetailPullDismiss?, scroll: ScrollState): Modifier {
    if (callbacks == null) return this
    val current by rememberUpdatedState(callbacks)
    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (scroll.value > 0) return@awaitEachGesture
            var total = 0f
            val slopChange = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                if (over > 0f) {
                    change.consume()
                    total = over
                }
            } ?: return@awaitEachGesture
            current.onStart()
            current.onDrag(total)
            val tracker = VelocityTracker()
            tracker.addPosition(slopChange.uptimeMillis, Offset(0f, total))
            var delivered = false
            try {
                val finished = verticalDrag(slopChange.id) { change ->
                    total = (total + change.positionChange().y).coerceAtLeast(0f)
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, Offset(0f, total))
                    current.onDrag(total)
                }
                delivered = true
                if (finished) current.onEnd(total, tracker.calculateVelocity().y) else current.onCancel()
            } finally {
                if (!delivered) current.onCancel()
            }
        }
    }
}
