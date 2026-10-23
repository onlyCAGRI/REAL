package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Home "Kartlarım": swiping the title left turns it into "Kartlarda ara…" in the same place; the cards under it filter
 * while typing. Swiping the field right, "×" or back brings the title back. The first time, the title nudges left
 * twice so the gesture can be found.
 */
@Composable
internal fun HomeCardsSearchTitle(
    open: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier,
    /** Drawn right after the "Kartlarım" title and moves with it (the context-card dots). */
    titleAccessory: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {}
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val threshold = with(density) { 64.dp.toPx() }

    LaunchedEffect(Unit) {
        if (reduceAnimations || open) return@LaunchedEffect
        val prefs = context.getSharedPreferences("real_ui_hints", Context.MODE_PRIVATE)
        if (prefs.getBoolean("home_cards_search_swipe", false)) return@LaunchedEffect
        delay(1400)
        val nudge = with(density) { 22.dp.toPx() }
        repeat(2) {
            drag.animateTo(-nudge, tween(220))
            drag.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 500f))
        }
        prefs.edit().putBoolean("home_cards_search_swipe", true).apply()
    }

    // The field opens right where the title was (no slide in from the right edge); closing slides the title back in.
    AnimatedContent(open, modifier, label = "homeCardsSearch", transitionSpec = {
        if (reduceAnimations) fadeIn(snap()) togetherWith fadeOut(snap())
        else if (targetState) fadeIn(tween(200)) togetherWith fadeOut(tween(120))
        else (slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(220))) togetherWith fadeOut(tween(140))
    }) { isOpen ->
        if (!isOpen) {
            Row(
                Modifier.fillMaxWidth().height(44.dp)
                    .semantics {
                        customActions = listOf(CustomAccessibilityAction(t("Kartlarda ara")) { onOpen(); true })
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    // Pulling left: the title slides out with the finger and "Kartlarda ara" comes up in its place.
                    Text(
                        t("Kartlarda ara…"), color = Color(0xFF9A9AA4), fontSize = 17.sp,
                        modifier = Modifier.graphicsLayer {
                            val p = (-drag.value / threshold).coerceIn(0f, 1f)
                            alpha = p
                            translationX = (1f - p) * 24.dp.toPx()
                        }
                    )
                    // Only the title itself pulls (not the empty row beside it). The gesture sits before graphicsLayer,
                    // so its touch area stays put while the title slides with the finger.
                    Row(
                        Modifier.pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val pulled = drag.value < -threshold
                                    scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f)) }
                                    if (pulled) onOpen()
                                },
                                onDragCancel = { scope.launch { drag.animateTo(0f) } }
                            ) { change, dx ->
                                change.consume()
                                val next = (drag.value + dx).coerceIn(-threshold * 1.6f, 0f)
                                if (next < -threshold && drag.value >= -threshold) RealHaptics.tick(view)
                                scope.launch { drag.snapTo(next) }
                            }
                        }.graphicsLayer {
                            translationX = drag.value
                            alpha = 1f - (-drag.value / threshold).coerceIn(0f, 1f)
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(t("Kartlarım"), color = Color(0xFFF2F2F4), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        titleAccessory()
                    }
                }
                trailing()
            }
        } else {
            val focus = remember { FocusRequester() }
            val keyboard = LocalSoftwareKeyboardController.current
            LaunchedEffect(Unit) { focus.requestFocus() }
            var pulled = 0f
            Row(
                Modifier.fillMaxWidth().padding(end = 12.dp).height(44.dp).clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF1C1C21))
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(onDragStart = { pulled = 0f }, onDragEnd = {
                            if (pulled > threshold) { keyboard?.hide(); onClose() }
                        }) { change, dx -> change.consume(); pulled += dx }
                    }
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color(0xFFF2F2F4), fontSize = 17.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = t("Kartlarda ara") },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) Text(t("Kartlarda ara…"), color = Color(0xFF77777F), fontSize = 17.sp)
                            inner()
                        }
                    }
                )
                Box(
                    Modifier.size(38.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = t("Aramayı kapat")) {
                        keyboard?.hide(); onClose()
                    },
                    contentAlignment = Alignment.Center
                ) { Text("×", color = Color(0xFFC8C8D0), fontSize = 24.sp) }
            }
        }
    }
}
