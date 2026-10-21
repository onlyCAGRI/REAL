package com.cagri.real.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.cagri.real.data.cardPhotoTransform

/** Owned by a card detail viewport, never shared with Pano or another card. */
internal class CardBlockDragState {
    var active by mutableStateOf(false)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var pointer: PointerId? = null
        private set
    private var origin = Offset.Zero
    private var scrollOrigin = 0
    private var complete: ((Boolean) -> Unit)? = null

    fun begin(id: PointerId, position: Offset, scroll: Int, onFinish: (Boolean) -> Unit) {
        cancel()
        pointer = id; origin = position; scrollOrigin = scroll
        offset = Offset.Zero; complete = onFinish; active = true
    }
    fun move(position: Offset, scroll: Int) {
        offset = position - origin + Offset(0f, (scroll - scrollOrigin).toFloat())
    }
    fun finish(commit: Boolean) {
        val callback = complete
        complete = null
        callback?.invoke(commit)
        active = false; pointer = null; offset = Offset.Zero
    }
    fun cancel() = finish(false)
}

/** Install OUTSIDE verticalScroll: pointer positions stay in viewport coordinates. */
internal fun Modifier.cardBlockDragInput(state: CardBlockDragState, scroll: ScrollState): Modifier = composed {
    var viewportOrigin by remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(state) { onDispose { state.cancel() } }
    onGloballyPositioned { viewportOrigin = it.positionInRoot() }
        .pointerInput(state, scroll) {
            try {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var secondary: PointerId? = null
                    var secondaryY: Float? = null
                    var finished = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (state.active) {
                            val primary = event.changes.firstOrNull { it.id == state.pointer }
                            if (primary == null || !primary.pressed) {
                                primary?.let { state.move(it.position + viewportOrigin, scroll.value) }
                                state.finish(commit = primary != null)
                                finished = true
                            } else {
                                val other = event.changes.firstOrNull { it.id == secondary && it.pressed }
                                    ?: event.changes.firstOrNull { it.id != state.pointer && it.pressed }
                                if (other?.id != secondary) {
                                    secondary = other?.id
                                    secondaryY = null
                                }
                                if (other != null) {
                                    secondaryY?.let { previous -> scroll.dispatchRawDelta(previous - other.position.y) }
                                    secondaryY = other.position.y
                                }
                                state.move(primary.position + viewportOrigin, scroll.value)
                            }
                            // Neither a photo pinch nor a click may take ownership during a held drag.
                            event.changes.forEach { it.consume() }
                        } else if (finished) {
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            } finally {
                state.cancel()
            }
        }
}

/** Only transforms image pixels. The parent's clipped viewport and layout size never change.
 * Two fingers zoom/pan; one finger remains available to scroll or long-press the block.
 * Intentionally transient: no attachment, source card, or share payload is rewritten.
 */
internal class CardPhotoZoomState {
    var scale by mutableFloatStateOf(1f)
    var translation by mutableStateOf(Offset.Zero)
}

internal fun Modifier.cardPhotoZoomInput(state: CardPhotoZoomState, enabled: Boolean): Modifier =
    pointerInput(state, enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var transformed = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (event.changes.any { it.isConsumed }) break
                if (pressed.size >= 2 && pressed.all { it.previousPressed }) {
                    val center = event.calculateCentroid(useCurrent = false)
                    val pan = event.calculatePan()
                    val next = cardPhotoTransform(state.scale, state.translation.x, state.translation.y,
                        event.calculateZoom(), pan.x, pan.y, center.x - size.width / 2f,
                        center.y - size.height / 2f, size.width.toFloat(), size.height.toFloat())
                    state.scale = next.scale; state.translation = Offset(next.x, next.y)
                    transformed = true
                    event.changes.forEach { it.consume() }
                } else if (pressed.size >= 2 || transformed) {
                    // Cancel tap/long-press as soon as a second finger joins; never transfer to one finger.
                    transformed = true
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }

internal fun Modifier.cardPhotoPixels(state: CardPhotoZoomState): Modifier = graphicsLayer {
        val scale = state.scale
        val translation = state.translation
        scaleX = scale; scaleY = scale
        // Re-clamp when a resize handle changes the viewport, without changing the block itself.
        translationX = translation.x.coerceIn(-size.width * (scale - 1f) / 2f, size.width * (scale - 1f) / 2f)
        translationY = translation.y.coerceIn(-size.height * (scale - 1f) / 2f, size.height * (scale - 1f) / 2f)
    }

/**
 * Determines whether a tap offset in root window coordinates qualifies as a genuine
 * background tap on the card detail screen while in edit mode.
 *
 * It returns false if:
 * - Not in edit mode
 * - Block reordering or dragging is in progress
 * - Detail is saving or busy (recording, attachment busy)
 * - An overlay/sheet/dialog is showing
 * - The tap falls inside the top header/status bar area
 * - The tap falls inside the bottom dock or action panel
 * - The tap falls inside any content block's bounding box
 * - The tap falls inside the footer metadata bounding box
 */
internal fun isCardDetailBackgroundTap(
    tapInRoot: Offset,
    editing: Boolean,
    isReordering: Boolean,
    isDragging: Boolean,
    isBusy: Boolean,
    hasActiveOverlay: Boolean,
    topAreaBottom: Float,
    bottomPanelBounds: Rect?,
    blockBounds: Collection<Rect>,
    footerBounds: Rect? = null
): Boolean {
    if (!editing) return false
    if (isReordering || isDragging || isBusy || hasActiveOverlay) return false
    if (tapInRoot.y < topAreaBottom) return false
    if (bottomPanelBounds != null && bottomPanelBounds.contains(tapInRoot)) return false
    if (blockBounds.any { it.contains(tapInRoot) }) return false
    if (footerBounds != null && footerBounds.contains(tapInRoot)) return false
    return true
}
