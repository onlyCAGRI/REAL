package com.cagri.real.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker

/**
 * True while a category is pulled down to home (and while its release settles): the list shrinks into a card with the
 * finger, then the hero flies into its home card, drawn above the closing page (ReminderScreen, CategoryCollections).
 */
internal object CategoryPullMotion {
    var active by mutableStateOf(false)
}

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

/**
 * Lets the user grab the main detail card and pull it down. Only downward drags are taken,
 * and only while the detail content is scrolled to the top, so normal scrolling, text
 * selection and horizontal gestures are untouched.
 */
@Composable
internal fun Modifier.pullDownToDismiss(callbacks: DetailPullDismiss?, scroll: ScrollState): Modifier =
    pullDownWhenAtTop(callbacks) { scroll.value == 0 }

/**
 * Same pull for a list page (a category). A LazyColumn takes the drag itself (and stretches at the top), so a pointer
 * gesture never sees it: the pull is read from nested scroll instead — what the list could not use going down at its
 * top becomes the pull, and the release (fling, even at zero speed) commits or cancels it.
 */
@Composable
internal fun Modifier.pullDownToDismiss(callbacks: DetailPullDismiss?, list: LazyListState): Modifier {
    if (callbacks == null) return this
    val current by rememberUpdatedState(callbacks)
    val connection = remember(list) {
        object : NestedScrollConnection {
            var pulling = false
            var total = 0f
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Finger going back up while pulling: shrink the pull before the list scrolls.
                if (!pulling || available.y >= 0f) return Offset.Zero
                val used = maxOf(available.y, -total)
                total += used
                current.onDrag(total)
                return Offset(0f, used)
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                if (list.firstVisibleItemIndex != 0 || list.firstVisibleItemScrollOffset != 0) return Offset.Zero
                if (!pulling) { pulling = true; total = 0f; current.onStart() }
                total += available.y
                current.onDrag(total)
                return Offset(0f, available.y)
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!pulling) return Velocity.Zero
                pulling = false
                current.onEnd(total, available.y)
                total = 0f
                return available
            }
        }
    }
    return this.nestedScroll(connection)
}

@Composable
private fun Modifier.pullDownWhenAtTop(callbacks: DetailPullDismiss?, atTop: () -> Boolean): Modifier {
    if (callbacks == null) return this
    val current by rememberUpdatedState(callbacks)
    val currentAtTop by rememberUpdatedState(atTop)
    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!currentAtTop()) return@awaitEachGesture
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
            val finished = verticalDrag(slopChange.id) { change ->
                total = (total + change.positionChange().y).coerceAtLeast(0f)
                change.consume()
                tracker.addPosition(change.uptimeMillis, Offset(0f, total))
                current.onDrag(total)
            }
            if (finished) current.onEnd(total, tracker.calculateVelocity().y) else current.onCancel()
        }
    }
}
