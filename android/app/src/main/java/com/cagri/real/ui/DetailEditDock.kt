package com.cagri.real.ui

import com.cagri.real.i18n.t
import com.cagri.real.ui.components.RealMotion
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

internal fun detailDockCollapsedAfterDrag(collapsed: Boolean, deltaDp: Float): Boolean = when {
    !deltaDp.isFinite() -> collapsed
    deltaDp >= 24f -> true
    deltaDp <= -24f -> false
    else -> collapsed
}

/**
 * The edit dock. Its grab handle lives inside the dock: [content] places it at the top of the main
 * panel, and when the dock is lowered [collapsedBar] shows it in a slim glass bar. Only the handle
 * owns the drag (down = lower, up = raise, tap = toggle); colour controls, icon scroll and Save keep
 * their own gestures.
 */
@Composable
internal fun DetailEditDock(
    collapsed: Boolean, enabled: Boolean, reduceAnimations: Boolean,
    onCollapsedChange: (Boolean) -> Unit,
    collapsedBar: @Composable (handle: @Composable () -> Unit) -> Unit,
    content: @Composable ColumnScope.(handle: @Composable () -> Unit) -> Unit
) {
    val density = LocalDensity.current.density
    val currentCollapsed by rememberUpdatedState(collapsed)
    val currentChange by rememberUpdatedState(onCollapsedChange)
    val interaction = remember { MutableInteractionSource() }
    val handle: @Composable () -> Unit = {
        Box(
            Modifier.fillMaxWidth().height(20.dp)
                .semantics {
                    contentDescription = t("Düzenleme dock'u")
                    stateDescription = if (currentCollapsed) t("Küçültülmüş") else t("Açık")
                }
                .pointerInput(enabled, density) {
                    if (!enabled) return@pointerInput
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragCancel = { total = 0f },
                        onDragEnd = {
                            val next = detailDockCollapsedAfterDrag(currentCollapsed, total / density)
                            if (next != currentCollapsed) currentChange(next)
                            total = 0f
                        },
                        onVerticalDrag = { change, amount -> change.consume(); total += amount }
                    )
                }
                .clickable(interactionSource = interaction, indication = null, enabled = enabled,
                    role = Role.Button, onClickLabel = if (currentCollapsed) t("Dock'u aç") else t("Dock'u küçült")) {
                    currentChange(!currentCollapsed)
                }, contentAlignment = Alignment.Center
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = if (enabled) 0.42f else 0.2f)))
        }
    }
    Column(Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = collapsed,
            enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(180, delayMillis = 60)),
            exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(100))
        ) {
            collapsedBar(handle)
        }
        AnimatedVisibility(
            visible = !collapsed,
            enter = if (reduceAnimations) EnterTransition.None else expandVertically(tween(220, easing = RealMotion.Emphasized), expandFrom = Alignment.Bottom) + fadeIn(tween(160)),
            exit = if (reduceAnimations) ExitTransition.None else shrinkVertically(tween(180), shrinkTowards = Alignment.Bottom) + fadeOut(tween(120))
        ) {
            Column(Modifier.fillMaxWidth()) { content(handle) }
        }
    }
}
