package com.cagri.real.ui

import com.cagri.real.ui.motion.realEntrance
import com.cagri.real.ui.motion.realGrow
import com.cagri.real.ui.motion.realGrowGlow
import com.cagri.real.ui.motion.rememberRealEntrance
import com.cagri.real.ui.motion.RealEntrancePop
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.cagri.real.data.*
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import com.cagri.real.ui.components.LiquidGlassTokens
import com.cagri.real.ui.components.liquidChrome

/** Scoped to Card Detail, never changes the presentation of Pano or picker dialogs. */
internal val LocalCardContentComposition = staticCompositionLocalOf { false }
internal val LocalCardBlockHeight = staticCompositionLocalOf { 0.dp }
internal val LocalCardBlockDragging = staticCompositionLocalOf { false }

/** Apply to the painted surface, never an invisible wrapper around it. */
@Composable
internal fun Modifier.cardBlockSurface(): Modifier =
    heightIn(min = LocalCardBlockHeight.current)


@Composable
internal fun Modifier.cardContentChrome(corners: androidx.compose.ui.unit.Dp): Modifier =
    if (LocalCardContentComposition.current) clip(RoundedCornerShape(corners)).background(LiquidGlassTokens.contentColor)
    else liquidChrome(corners)

@Composable
internal fun CardCompositionGrid(
    keys: List<String>, placements: Map<String, CardBlockPlacement>,
    editing: Boolean, reduceAnimations: Boolean,
    dragState: CardBlockDragState, scrollState: ScrollState,
    selected: String?, onSelect: (String) -> Unit,
    onFreeDrop: (Map<String, CardBlockPlacement>) -> Unit,
    onPlacementChange: (String, CardBlockPlacement) -> Unit,
    onConfigure: (String) -> Unit = {},
    onBlockCoordinates: ((String, LayoutCoordinates?) -> Unit)? = null,
    content: @Composable (String, Int) -> Unit
) {
    // Bounds are gesture bookkeeping, not state. Removing a block cannot leave a stale drop target.
    val mountedBlocks = remember(dragState) { keys.toMutableSet() }
    val bounds = remember { mutableMapOf<String, Rect>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    val dragOffset = dragState.offset
    var dragOrigin by remember { mutableStateOf<Rect?>(null) }
    var dragPlacement by remember { mutableStateOf(CardBlockPlacement()) }
    var dragBoxes by remember { mutableStateOf<Map<String, CardMeasuredBox>>(emptyMap()) }
    var dragKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var dragLayout by remember { mutableStateOf<Map<String, CardBlockPlacement>>(emptyMap()) }
    var dragGridTop by remember { mutableFloatStateOf(0f) }
    var gridBounds by remember { mutableStateOf<Rect?>(null) }
    val currentKeys by rememberUpdatedState(keys)
    val currentFreeDrop by rememberUpdatedState(onFreeDrop)
    val currentEditing by rememberUpdatedState(editing)
    val currentPlacements by rememberUpdatedState(placements)
    val currentSelect by rememberUpdatedState(onSelect)
    val currentPlacementChange by rememberUpdatedState(onPlacementChange)
    val density = LocalDensity.current.density
    LaunchedEffect(keys, editing) {
        bounds.keys.retainAll(keys.toSet())
        if (!editing || dragging !in keys) { dragging = null; dragState.cancel() }
    }
    val gutter = with(LocalDensity.current) { 12.dp.roundToPx() }
    var gridWidthPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(gridWidthPx, density) {
        if (dragState.active) { dragState.cancel(); dragging = null }
    }
    CompositionLocalProvider(LocalCardContentComposition provides true, LocalCardBlockDragging provides dragState.active) {
        Layout(
            modifier = Modifier.fillMaxWidth().onSizeChanged { gridWidthPx = it.width }
                .onGloballyPositioned {
                    val origin = it.positionInRoot()
                    gridBounds = Rect(origin.x, origin.y, origin.x + it.size.width, origin.y + it.size.height)
                }
                .drawBehind {
                    // Temporary alignment guide around the dragged block, not a permanent content border.
                    val origin = dragOrigin
                    if (dragging != null && origin != null) {
                        val step = CARD_VERTICAL_GRID_DP.dp.toPx()
                        val localTop = origin.top - dragGridTop + dragOffset.y
                        var y = snappedCardRowBottom((localTop - 160.dp.toPx()).coerceAtLeast(0f), step)
                        val end = (localTop + origin.height + 160.dp.toPx()).coerceAtMost(size.height)
                        while (y <= end) {
                            drawLine(Color.White.copy(alpha = 0.045f), Offset(0f, y), Offset(size.width, y), 1f)
                            y += step
                        }
                        val unit = (size.width + gutter) / CARD_GRID_UNITS
                        for (column in 1 until CARD_GRID_UNITS) {
                            drawLine(Color.White.copy(alpha = 0.045f), Offset(column * unit, 0f), Offset(column * unit, size.height), 1f)
                        }
                    }
                }
                .then(if (reduceAnimations || editing) Modifier else Modifier.animateContentSize(spring(stiffness = 450f, dampingRatio = 1f))),
            content = {
                keys.forEachIndexed { index, blockKey ->
                    key(blockKey) {
                        val active = dragging == blockKey
                        val animateInsertion = remember(dragState) { editing && blockKey !in mountedBlocks }
                        val insertion = if (animateInsertion) rememberRealEntrance(reduceAnimations, identity = dragState, totalMs = 420) else null
                        var blockCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                        var placedPosition by remember { mutableStateOf<IntOffset?>(null) }
                        var positionReady by remember { mutableStateOf(false) }
                        val animatedPosition = remember { Animatable(IntOffset.Zero, IntOffset.VectorConverter) }
                        var releasedPosition by remember { mutableStateOf<IntOffset?>(null) }
                        var releaseAnimationStarted by remember { mutableStateOf(false) }
                        LaunchedEffect(placedPosition, reduceAnimations, releasedPosition) {
                            val target = placedPosition ?: return@LaunchedEffect
                            if (!positionReady || reduceAnimations) animatedPosition.snapTo(target)
                            else {
                                releasedPosition?.let { animatedPosition.snapTo(it) }
                                releaseAnimationStarted = true
                                animatedPosition.animateTo(target, spring(stiffness = 450f, dampingRatio = 1f))
                            }
                            positionReady = true
                            releasedPosition = null
                            releaseAnimationStarted = false
                        }
                        DisposableEffect(blockKey, mountedBlocks) {
                            mountedBlocks.add(blockKey)
                            onDispose {
                                mountedBlocks.remove(blockKey)
                                onBlockCoordinates?.invoke(blockKey, null)
                            }
                        }
                        val scale by animateFloatAsState(if (active) 1.025f else 1f,
                            if (reduceAnimations) androidx.compose.animation.core.snap() else spring(stiffness = 450f, dampingRatio = 1f), label = "cardBlockLift")
                        Box(
                            Modifier.fillMaxWidth()
                                // Grow: pill gibi açılıp büyür + kenarda kısa ışık halkası (sürükleme sırasında durur).
                                .then(insertion?.let {
                                    Modifier
                                        .realGrowGlow(it, durationMs = 420, paused = active || dragState.active)
                                        .realGrow(it, durationMs = 420, paused = active || dragState.active)
                                } ?: Modifier)
                                .onGloballyPositioned {
                                    blockCoordinates = it
                                    onBlockCoordinates?.invoke(blockKey, it)
                                }
                                .onPlaced {
                                    val position = it.positionInParent()
                                    placedPosition = IntOffset(position.x.roundToInt(), position.y.roundToInt())
                                }
                                .zIndex(if (active) 2f else 0f)
                                .graphicsLayer {
                                    scaleX = scale; scaleY = scale
                                    val target = placedPosition
                                    if (!active && positionReady && target != null && !reduceAnimations) {
                                        val displayedPosition = if (!releaseAnimationStarted) releasedPosition ?: animatedPosition.value else animatedPosition.value
                                        translationX = (displayedPosition.x - target.x).toFloat()
                                        translationY = (displayedPosition.y - target.y).toFloat()
                                    }
                                    if (active) {
                                        val unit = ((gridWidthPx + gutter) / CARD_GRID_UNITS.toFloat()).coerceAtLeast(1f)
                                        val column = (dragPlacement.column + dragOffset.x / unit).roundToInt().coerceIn(0, CARD_GRID_UNITS - dragPlacement.span)
                                        translationX += (column - dragPlacement.column) * unit
                                        val origin = dragOrigin
                                        val step = CARD_VERTICAL_GRID_DP * density
                                        translationY += if (origin != null) {
                                            ((origin.top + dragOffset.y - dragGridTop) / step).roundToInt() * step + dragGridTop - origin.top
                                        } else dragOffset.y
                                        alpha = 0.88f
                                    }
                                }
                                // One owner per drag. A second finger scrolls at the viewport level.
                                .pointerInput(blockKey, editing) {
                                    if (!editing) return@pointerInput
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                                        if (held.id != down.id || !currentEditing || dragState.active || currentEvent.changes.count { it.pressed } != 1)
                                            return@awaitEachGesture
                                        val coordinates = blockCoordinates?.takeIf { it.isAttached } ?: return@awaitEachGesture
                                        currentSelect(blockKey)
                                        dragging = blockKey
                                        val root = gridBounds
                                        dragGridTop = root?.top ?: 0f
                                        dragOrigin = bounds[blockKey]?.let { b ->
                                            Rect(b.left + (root?.left ?: 0f), b.top + dragGridTop,
                                                b.right + (root?.left ?: 0f), b.bottom + dragGridTop)
                                        }
                                        dragPlacement = currentPlacements[blockKey]?.normalized() ?: CardBlockPlacement()
                                        dragKeys = currentKeys.toList()
                                        dragLayout = currentPlacements.toMap()
                                        dragBoxes = bounds.mapValues { (_, b) ->
                                            CardMeasuredBox(b.left / density, b.top / density, b.width / density, b.height / density)
                                        }
                                        held.consume()
                                        dragState.begin(held.id, coordinates.localToRoot(held.position), scrollState.value) { commit ->
                                            val box = dragBoxes[blockKey]
                                            val offset = dragState.offset
                                            if (box != null) {
                                                val unit = (gridWidthPx + gutter) / CARD_GRID_UNITS.toFloat()
                                                val column = (dragPlacement.column + offset.x / unit).roundToInt()
                                                    .coerceIn(0, CARD_GRID_UNITS - dragPlacement.span)
                                                releaseAnimationStarted = false
                                                releasedPosition = IntOffset((column * unit).roundToInt(),
                                                    (((box.top * density + offset.y) / (CARD_VERTICAL_GRID_DP * density)).roundToInt() * CARD_VERTICAL_GRID_DP * density).roundToInt())
                                            }
                                            if (commit && currentEditing && box != null && offset.getDistance() >= 1f &&
                                                currentKeys == dragKeys && currentPlacements == dragLayout) {
                                                val unitDp = (gridWidthPx + gutter) / CARD_GRID_UNITS.toFloat() / density
                                                placeCardInFreeSpace(dragKeys, dragLayout, dragBoxes, blockKey,
                                                    box.left + offset.x / density, box.top + offset.y / density, unitDp)
                                                    ?.mapValues { (k, p) ->
                                                        if (k == blockKey) p else p.copy(top = dragBoxes.getValue(k).top.roundToInt())
                                                    }?.let(currentFreeDrop)
                                            }
                                            dragging = null
                                        }
                                    }
                                }
                                .then(if (editing && selected == blockKey) Modifier.border(1.dp, Color.White.copy(alpha = 0.42f), RoundedCornerShape(22.dp)) else Modifier)
                        ) {
                            CompositionLocalProvider(LocalCardBlockHeight provides
                                ((placements[blockKey]?.minHeight ?: 0).takeUnless {
                                    blockKey.substringBefore(':') in listOf("music", "media")
                                } ?: 0).dp) {
                                content(blockKey, index)
                            }
                            if (editing) {
                                val isSelected = selected == blockKey
                                // Sağ kenar: genişlik (1/6 adımlarla, sağa doğru büyür).
                                CardResizeHandle(
                                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(30.dp),
                                    horizontal = true, highlighted = isSelected,
                                    onStart = { currentSelect(blockKey) },
                                    onDelta = { startPlacement, dx, _, _ ->
                                        val unitPx = ((gridWidthPx + gutter) / CARD_GRID_UNITS.toFloat()).coerceAtLeast(1f)
                                        val minSpan = cardDetailMinimumSpan(blockKey, gridWidthPx / density)
                                        val end = (startPlacement.column + startPlacement.span + dx / unitPx).roundToInt()
                                            .coerceIn(startPlacement.column + minOf(minSpan, CARD_GRID_UNITS - startPlacement.column), CARD_GRID_UNITS)
                                        val catalog = blockKey.substringBefore(':') in listOf("music", "media")
                                        val requestedSpan = end - startPlacement.column
                                        val span = if (catalog) catalogCardWidthStep(requestedSpan).coerceAtMost(CARD_GRID_UNITS - startPlacement.column) else requestedSpan
                                        val next = startPlacement.copy(span = span).normalized()
                                        val now = currentPlacements[blockKey]?.normalized() ?: CardBlockPlacement()
                                        if (next.span != now.span && cardDetailResizeFits(blockKey, next.span, bounds[blockKey]?.height?.div(density) ?: 0f,
                                            bounds.mapValues { (_, b) -> CardMeasuredBox(b.left / density, b.top / density, b.width / density, b.height / density) },
                                            (gridWidthPx + gutter) / CARD_GRID_UNITS.toFloat() / density, gutter / density))
                                            currentPlacementChange(blockKey, now.copy(span = next.span).normalized())
                                    },
                                    startPlacement = { currentPlacements[blockKey]?.normalized() ?: CardBlockPlacement() },
                                    startHeightDp = { 0f }
                                )
                                // Alt kenar: yükseklik (sürekli, aşağı çekince uzar, yukarı çekince kısalır).
                                CardResizeHandle(
                                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(30.dp),
                                    horizontal = false, highlighted = isSelected,
                                    onStart = { currentSelect(blockKey) },
                                    onDelta = { startPlacement, _, dy, startHeight ->
                                        val h = (startHeight + dy / density).coerceIn(80f, 800f).roundToInt()
                                        val now = currentPlacements[blockKey]?.normalized() ?: CardBlockPlacement()
                                        val hidden = if (blockKey.substringBefore(':') in listOf("music", "media"))
                                            catalogActionsHiddenAfterResize(startPlacement.hideActions, dy / density) else now.hideActions
                                        val catalog = blockKey.substringBefore(':') in listOf("music", "media")
                                        val nextHeight = if (catalog) 0 else h
                                        if ((nextHeight != now.minHeight || hidden != now.hideActions) &&
                                            (catalog || cardDetailResizeFits(blockKey, now.span, h.toFloat(),
                                                bounds.mapValues { (_, b) -> CardMeasuredBox(b.left / density, b.top / density, b.width / density, b.height / density) },
                                                (gridWidthPx + gutter) / CARD_GRID_UNITS.toFloat() / density, gutter / density)))
                                            currentPlacementChange(blockKey, now.copy(minHeight = nextHeight, hideActions = hidden).normalized())
                                    },
                                    startPlacement = { currentPlacements[blockKey]?.normalized() ?: CardBlockPlacement() },
                                    startHeightDp = {
                                        (bounds[blockKey]?.height ?: 0f) / density
                                    }
                                )
                            }
                        }
                    }
                }
            }
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val unit = (width + gutter) / CARD_GRID_UNITS.toFloat()
            val measured = keys.mapIndexed { i, k ->
                val p = placements[k]?.normalized() ?: CardBlockPlacement()
                val childWidth = (p.span * unit - gutter).roundToInt().coerceIn(1, width)
                measurables[i].measure(Constraints(minWidth = childWidth, maxWidth = childWidth,
                    minHeight = 0))
            }
            val sizesDp = keys.mapIndexed { i, k -> k to (measured[i].width / density to measured[i].height / density) }.toMap()
            val geometry = cardDetailNonOverlappingBoxes(cardGridMeasuredBoxes(keys, placements, sizesDp, unit / density))
            // Keep complete measure-pass rectangles, including offscreen stacks. Global bounds clip in a scroll view.
            bounds.clear()
            geometry.forEach { (k, box) ->
                bounds[k] = Rect(box.left * density, box.top * density,
                    (box.left + box.width) * density, (box.top + box.height) * density)
            }
            val positions = geometry.mapValues { (_, box) ->
                (box.left * density).roundToInt() to (box.top * density).roundToInt()
            }
            var y = snappedCardRowBottom(
                (geometry.values.maxOfOrNull { it.top + it.height } ?: 0f) * density,
                CARD_VERTICAL_GRID_DP * density).roundToInt()
            // Allow dropping below the old content bottom without clipping the lifted block.
            dragging?.let { k -> dragBoxes[k]?.let { box ->
                y = maxOf(y, ((box.top + box.height) * density + dragOffset.y).roundToInt().coerceAtLeast(0))
            } }
            layout(width, constraints.constrainHeight(y)) {
                keys.forEachIndexed { i, k -> val (x, top) = positions.getValue(k); measured[i].placeRelative(x, top) }
            }
        }
    }
}

/**
 * Kenar tutamacı. Parmak başlangıçtaki yerleşime göre toplam kayma ile hesaplanır (birikimli hata yok);
 * tutamaç kendi sürüklemesini tükettiği için bloğun uzun basıp taşıma jesti tetiklenmez.
 */
@Composable
private fun CardResizeHandle(
    modifier: Modifier, horizontal: Boolean, highlighted: Boolean,
    onStart: () -> Unit,
    onDelta: (startPlacement: CardBlockPlacement, dx: Float, dy: Float, startHeightDp: Float) -> Unit,
    startPlacement: () -> CardBlockPlacement,
    startHeightDp: () -> Float
) {
    Box(
        modifier.pointerInput(horizontal) {
            var origin = CardBlockPlacement()
            var originHeight = 0f
            var total = Offset.Zero
            detectDragGestures(
                onDragStart = { total = Offset.Zero; origin = startPlacement(); originHeight = startHeightDp(); onStart() },
                onDrag = { change, amount ->
                    change.consume(); total += amount
                    onDelta(origin, total.x, total.y, originHeight)
                }
            )
        },
        contentAlignment = if (horizontal) Alignment.CenterEnd else Alignment.BottomCenter
    ) {
        Box(
            Modifier.padding(if (horizontal) 5.dp else 4.dp)
                .then(if (horizontal) Modifier.width(4.dp).height(36.dp) else Modifier.height(4.dp).width(36.dp))
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (highlighted) 0.85f else 0.45f))
        )
    }
}

internal enum class CardDropZone { Before, After, Left, Right }

internal val CARD_WIDTH_STEPS = listOf(2, 3, 4, 6)

/**
 * Bırakma bölgesine göre sıra + yerleşim üretir. Sol/Sağ: hedefle aynı satıra girer
 * (1 hedef -> ikisi yarım, 2 hedef -> üçü de üçte bir). Satır doluysa (3) üst/alt kuralına düşer.
 * Hiçbir içerik üst üste binmez veya gizlenmez.
 */
internal fun applyCardDrop(
    keys: List<String>, layout: Map<String, CardBlockPlacement>,
    moving: String, target: String, zone: CardDropZone, gapBefore: Int? = null
): Pair<List<String>, Map<String, CardBlockPlacement>> {
    if (moving == target || moving !in keys || target !in keys) return keys to layout
    val rowKeys = cardContentRows(keys, layout).first { target in it }.filter { it != moving }
    val side = zone == CardDropZone.Left || zone == CardDropZone.Right
    val without = keys.filter { it != moving }
    if (side && rowKeys.size < CARD_MAX_PER_ROW) {
        val at = without.indexOf(target) + if (zone == CardDropZone.Right) 1 else 0
        val order = without.toMutableList().apply { add(at, moving) }
        val newRow = order.filter { it == moving || it in rowKeys }
        val span = CARD_GRID_UNITS / newRow.size
        var next = layout
        newRow.forEachIndexed { i, k ->
            val base = layout[k]?.normalized() ?: CardBlockPlacement()
            next = next + (k to base.copy(span = span, column = i * span).normalized())
        }
        if (gapBefore != null) {
            val moved = next[moving] ?: CardBlockPlacement()
            next = next + (moving to moved.copy(gapBefore = gapBefore).normalized())
        }
        return order to next
    }
    val before = zone == CardDropZone.Before || zone == CardDropZone.Left
    val at = if (before) without.indexOf(rowKeys.first()) else without.indexOf(rowKeys.last()) + 1
    val base = layout[moving]?.normalized() ?: CardBlockPlacement()
    return without.toMutableList().apply { add(at, moving) } to (layout + (moving to base.copy(column = 0, gapBefore = gapBefore ?: base.gapBefore).normalized()))
}

internal fun cardContentLabel(key: String): String = when (key.substringBefore(':')) {
    "text" -> "Metin"; "photos" -> "Fotoğraf"; "files" -> "Ek"; "voice" -> "Ses"
    "music" -> "Müzik"; "media" -> "Film / Dizi"; "location" -> "Konum"
    "checklist" -> "Checklist"; "mood" -> "Mood"; "journal" -> "Günlük"; else -> "Kart destesi"
}
