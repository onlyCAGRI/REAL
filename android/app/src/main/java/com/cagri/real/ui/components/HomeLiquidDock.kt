package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.ui.DesignStyle
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

// 60dp navigation row + 10dp gap. The home list reserves this maximum extra space.
val HomeDockExtraBottomSpace = 70.dp

/** Owns only navigation expansion. The caller's existing composer owns all input state. */
@Composable
fun HomeLiquidDock(
    enabled: Boolean,
    interactionEnabled: Boolean,
    composerExpanded: Boolean,
    /** Read during measure only: the composer height animates every frame while it opens. */
    composerHeight: () -> Dp,
    listState: LazyListState,
    onOpenPlanner: () -> Unit,
    onOpenWidget: () -> Unit,
    onOpenPano: () -> Unit,
    onMicClick: () -> Unit,
    micEnabled: Boolean,
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    designStyle: DesignStyle,
    reduceDynamicBlur: Boolean,
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier,
    composer: @Composable () -> Unit
) {
    var navigationExpanded by rememberSaveable { mutableStateOf(false) }
    val showChrome = enabled && !composerExpanded

    val openPlanner: () -> Unit = {
        navigationExpanded = false
        onOpenPlanner()
    }
    val openWidget: () -> Unit = {
        navigationExpanded = false
        onOpenWidget()
    }

    val openPano: () -> Unit = {
        navigationExpanded = false
        onOpenPano()
    }

    LaunchedEffect(enabled, composerExpanded, interactionEnabled) {
        if (!enabled || composerExpanded || !interactionEnabled) {
            navigationExpanded = false
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling) navigationExpanded = false
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (showChrome && navigationExpanded && interactionEnabled) 1f else 0f,
        animationSpec = if (reduceAnimations) snap() else spring(dampingRatio = 0.85f, stiffness = 420f),
        label = "homeDockExpansion"
    )

    BackHandler(enabled = showChrome && navigationExpanded && interactionEnabled) {
        navigationExpanded = false
    }

    val dockGesture = if (showChrome && interactionEnabled) {
        Modifier
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(
                        if (navigationExpanded) t("Alt paneli küçült") else t("Alt paneli genişlet")
                    ) {
                        navigationExpanded = !navigationExpanded && !listState.isScrollInProgress
                        true
                    }
                )
            }
            .pointerInput(Unit) {
                var drag = 0f
                val threshold = 28.dp.toPx()
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragCancel = { drag = 0f },
                    onDragEnd = {
                        if (drag < -threshold && !listState.isScrollInProgress) {
                            navigationExpanded = true
                        } else if (drag > threshold) {
                            navigationExpanded = false
                        }
                        drag = 0f
                    },
                    onVerticalDrag = { change, amount ->
                        drag += amount
                        change.consume()
                    }
                )
            }
    } else Modifier

    // The composer remains at one stable composition location in every state.
    Layout(
        modifier = modifier.then(dockGesture).testTag("home_dock"),
        content = {
            Box { composer() }
            if (showChrome) {
                HomeDockGlass(backdrop, backdropPosition, designStyle, reduceDynamicBlur, reduceAnimations) {
                    if (progress < 0.5f) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = (1f - progress * 2f).coerceIn(0f, 1f) }
                                .clickable(enabled = interactionEnabled, role = Role.Button, onClick = openPlanner)
                                .semantics { contentDescription = t("Takvim") },
                            contentAlignment = Alignment.Center
                        ) {
                            ActionIcon(ActionGlyph.Calendar, Modifier.size(22.dp))
                        }
                    } else {
                        HomeDockExpandedNavigation(
                            onOpenPlanner = openPlanner,
                            onOpenWidget = openWidget,
                            onOpenPano = openPano,
                            enabled = interactionEnabled,
                            selectedPill = designStyle == DesignStyle.LiquidGlass,
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = ((progress - 0.5f) * 2f).coerceIn(0f, 1f) }
                        )
                    }
                }

                // 48dp hit target surrounding the separate 44dp visible circle.
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(enabled = interactionEnabled && micEnabled, role = Role.Button, onClick = onMicClick)
                        .semantics { contentDescription = t("Sesle REAL oluştur") },
                    contentAlignment = Alignment.Center
                ) {
                    HomeDockGlass(
                        backdrop,
                        backdropPosition,
                        designStyle,
                        reduceDynamicBlur,
                        reduceAnimations,
                        Modifier.size(44.dp)
                    ) {
                        ActionIcon(ActionGlyph.Mic, Modifier.align(Alignment.Center).size(23.dp))
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val composerPx = composerHeight().roundToPx().coerceAtMost(constraints.maxHeight)
        val p = if (showChrome) progress.coerceIn(0f, 1f) else 0f
        val extra = (HomeDockExtraBottomSpace.toPx() * p).roundToInt()
        val height = constraints.constrainHeight(composerPx + extra)
        val sideInset = if (showChrome) (56.dp.toPx() * (1f - p)).roundToInt() else 0
        val composerWidth = (width - sideInset * 2).coerceAtLeast(0)
        val input = measurables[0].measure(Constraints.fixed(composerWidth, composerPx))
        val navWidth = (44.dp.toPx() + (width - 56.dp.toPx() - 44.dp.toPx()) * p).roundToInt().coerceAtLeast(0)
        val navHeight = (44.dp.toPx() + 16.dp.toPx() * p).roundToInt()
        val navigation = if (showChrome) measurables[1].measure(Constraints.fixed(navWidth, navHeight)) else null
        val micSize = 48.dp.roundToPx()
        val microphone = if (showChrome) measurables[2].measure(Constraints.fixed(micSize, micSize)) else null

        layout(width, height) {
            input.placeRelative(sideInset, 0)
            val navBottomInset = (((composerPx - 44.dp.toPx()) / 2f) * (1f - p)).roundToInt()
            val navY = height - navHeight - navBottomInset
            navigation?.placeRelative(0, navY)
            microphone?.placeRelative(width - micSize, navY + (navHeight - micSize) / 2)
        }
    }
}

@Composable
private fun HomeDockExpandedNavigation(
    onOpenPlanner: () -> Unit,
    onOpenWidget: () -> Unit,
    onOpenPano: () -> Unit,
    enabled: Boolean,
    selectedPill: Boolean,
    modifier: Modifier = Modifier
) {
    Row(modifier.padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        HomeDockNavigationItem(
            label = t("Yaşam"),
            glyph = ActionGlyph.Cards,
            enabled = enabled,
            selected = false,
            onClick = onOpenWidget,
            pill = selectedPill,
            modifier = Modifier.weight(1f).testTag("dock_yasam")
        )
        HomeDockNavigationItem(
            label = t("Pano"),
            glyph = ActionGlyph.Board,
            enabled = enabled,
            selected = false,
            onClick = onOpenPano,
            pill = selectedPill,
            modifier = Modifier.weight(1f)
        )
        HomeDockNavigationItem(
            label = t("Takvim"),
            glyph = ActionGlyph.Calendar,
            enabled = enabled,
            selected = true,
            onClick = onOpenPlanner,
            pill = selectedPill,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HomeDockNavigationItem(
    label: String,
    glyph: ActionGlyph,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    pill: Boolean,
    modifier: Modifier
) {
    // Selected tab: a dark inner capsule inside the glass bar (iOS-26 tab bar look).
    val pillShape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .fillMaxHeight()
            .padding(vertical = 5.dp)
            .then(
                if (pill && selected) {
                    Modifier.clip(pillShape)
                        .background(LiquidGlassTokens.dockSelectedPill)
                } else Modifier.clip(pillShape)
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                stateDescription = when {
                    selected -> t("Seçili")
                    !enabled -> t("Henüz kullanılamıyor")
                    else -> t("Kullanılabilir")
                }
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val tint = when {
            selected -> Color.White
            enabled -> Color(0xFFB8B8BE)
            else -> Color(0xFF929298)
        }
        ActionIcon(glyph, Modifier.size(22.dp), tint)
        Text(
            label, color = tint, fontSize = 12.sp, lineHeight = 14.sp,
            modifier = Modifier.offset(y = (-3).dp),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomeDockGlass(
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    designStyle: DesignStyle,
    reduceDynamicBlur: Boolean,
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    when (designStyle) {
        DesignStyle.LiquidGlass -> LiquidGlassSurface(
            backdrop,
            backdropPosition,
            modifier,
            corners = 36.dp,
            reduceDynamicBlur = reduceDynamicBlur,
            reduceAnimations = reduceAnimations,
            role = LiquidGlassRole.Dock,
            content = content
        )
        DesignStyle.Ice -> IcePanel(
            backdrop,
            backdropPosition,
            modifier,
            corners = 36.dp,
            reduceDynamicBlur = reduceDynamicBlur,
            content = content
        )
        else -> GlassPanel(
            backdrop,
            backdropPosition,
            modifier,
            corners = 36.dp,
            reduceDynamicBlur = reduceDynamicBlur,
            content = content
        )
    }
}
