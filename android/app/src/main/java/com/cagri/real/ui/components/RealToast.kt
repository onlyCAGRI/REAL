package com.cagri.real.ui.components

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cagri.real.ui.theme.RealType
import kotlinx.coroutines.delay

/**
 * REAL's own toast: a dark glass pill that springs down under the status bar instead of the grey
 * system Toast sitting on top of the dock. While no host is on screen (another Activity, app in
 * the background) it falls back to the system Toast, so no message is ever lost.
 */
object RealToast {
    internal data class Message(
        val id: Long, val text: String, val durationMs: Long,
        val actionLabel: String? = null, val onAction: (() -> Unit)? = null
    )

    internal var current by mutableStateOf<Message?>(null)
    @Volatile internal var resumedHosts = 0
    private val main = Handler(Looper.getMainLooper())
    private var sequence = 0L

    fun show(context: Context, text: CharSequence, long: Boolean = false) {
        val app = context.applicationContext
        val post = Runnable {
            if (resumedHosts > 0) current = Message(++sequence, text.toString(), if (long) 3_500L else 2_200L)
            else Toast.makeText(app, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) post.run() else main.post(post)
    }

    /**
     * A toast with one action ("Geri al"). Main thread only. Returns false when no REAL screen can show it,
     * so the caller must not wait for an action that can never be tapped.
     */
    fun showAction(context: Context, text: CharSequence, actionLabel: String, durationMs: Long, onAction: () -> Unit): Boolean {
        if (resumedHosts <= 0) {
            Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
            return false
        }
        current = Message(++sequence, text.toString(), durationMs, actionLabel, onAction)
        return true
    }

    internal fun dismiss(id: Long) {
        if (current?.id == id) current = null
    }
}

private val ToastShape = RoundedCornerShape(22.dp)
private val ToastFill = Color(0xF2161618)

@Composable
fun RealToastHost(modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        var counted = false
        fun set(resumed: Boolean) {
            if (resumed == counted) return
            counted = resumed
            RealToast.resumedHosts += if (resumed) 1 else -1
        }
        val observer = LifecycleEventObserver { _, _ -> set(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); set(false) }
    }

    val reduce = LocalLiquidReduceMotion.current
    val message = RealToast.current
    var shown by remember { mutableStateOf<RealToast.Message?>(null) }
    LaunchedEffect(message?.id) {
        val m = message ?: return@LaunchedEffect
        shown = m
        delay(m.durationMs)
        RealToast.dismiss(m.id)
    }

    Box(modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = message != null,
            enter = if (reduce) fadeIn(tween(120)) else
                slideInVertically(spring(dampingRatio = 0.78f, stiffness = 520f)) { -it * 2 } +
                    scaleIn(spring(dampingRatio = 0.78f, stiffness = 520f), initialScale = 0.9f) + fadeIn(tween(140)),
            exit = if (reduce) fadeOut(tween(120)) else
                slideOutVertically(tween(220, easing = RealMotion.Exit)) { -it * 2 } + fadeOut(tween(180))
        ) {
            val current = shown ?: return@AnimatedVisibility
            Box(
                Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 380.dp)
                    .shadow(18.dp, ToastShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                    .background(ToastFill, ToastShape)
                    .border(0.7.dp, liquidContentEdge(), ToastShape)
                    .clickable(remember { MutableInteractionSource() }, indication = null) { RealToast.dismiss(current.id) }
                    .pointerInput(current.id) {
                        var drag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = { if (drag < -12f) RealToast.dismiss(current.id) },
                            onVerticalDrag = { change, amount -> drag += amount; change.consume() }
                        )
                    }
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                // A newer message replaces the visible one in place with a short crossfade.
                AnimatedContent(current, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                    contentKey = { it.id }, label = "realToastText") { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            m.text, color = Color(0xFFF3F3F5),
                            style = RealType.actionLabel.copy(fontSize = 14.sp, lineHeight = 19.sp),
                            textAlign = TextAlign.Center
                        )
                        val action = m.onAction
                        if (m.actionLabel != null && action != null) {
                            Text(
                                m.actionLabel, color = Color(0xFF91B9FF),
                                style = RealType.actionLabel.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
                                modifier = Modifier
                                    .padding(start = 14.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(role = Role.Button) { RealToast.dismiss(m.id); action() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
