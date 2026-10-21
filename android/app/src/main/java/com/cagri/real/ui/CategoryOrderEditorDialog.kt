package com.cagri.real.ui

import com.cagri.real.ui.components.RealSheetHandle
import com.cagri.real.ui.components.realSheetSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.CustomCategory
import com.cagri.real.ui.components.*
import kotlin.math.roundToInt

@Composable
internal fun CategoryOrderEditorDialog(
    customCategories: List<CustomCategory>,
    currentOrderKeys: List<String>,
    currentPreviewCount: Int,
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    reduceDynamicBlur: Boolean,
    designStyle: DesignStyle,
    onDismiss: () -> Unit,
    onSave: (List<String>, Int) -> Unit,
    reduceAnimations: Boolean = false
) {
    val allItems = remember(customCategories) {
        resolveHomeCategoryOrder(emptyList(), customCategories).associateBy { it.orderKey }
    }
    val draft = remember(currentOrderKeys, customCategories) {
        mutableStateListOf<String>().apply {
            addAll(resolveCategoryOrder(currentOrderKeys, customCategories).map { it.orderKey }.filter { it in allItems })
            add(CATEGORY_SHOW_ALL_KEY)
        }
    }
    val hidden = remember(currentOrderKeys) {
        mutableStateListOf<String>().apply { addAll(hiddenCategoryKeys(currentOrderKeys)) }
    }
    var previewCount by remember(currentPreviewCount) { mutableIntStateOf(currentPreviewCount.coerceAtLeast(1)) }
    // The action stays below categories on Home; it is draggable to hide, not sortable.
    val visible = draft.filterNot { it in hidden || it == CATEGORY_SHOW_ALL_KEY } +
        if (CATEGORY_SHOW_ALL_KEY in hidden) emptyList() else listOf(CATEGORY_SHOW_ALL_KEY)
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val edge = with(density) { 48.dp.toPx() }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var dragWidth by remember { mutableFloatStateOf(0f) }
    var panelBounds by remember { mutableStateOf(Rect.Zero) }
    var listBounds by remember { mutableStateOf(Rect.Zero) }
    var hideBounds by remember { mutableStateOf(Rect.Zero) }
    val rowBounds = remember { mutableMapOf<String, Rect>() }
    val overHide = draggedKey != null && hideBounds.contains(pointer)
    fun title(key: String): String = when (val item = allItems[key]) {
        is OrderedCategoryItem.BuiltIn -> item.category.title
        is OrderedCategoryItem.Custom -> item.category.name
        else -> "Tümünü göster"
    }
    fun slot(): Int? {
        if (!listBounds.contains(pointer)) return null
        val y = pointer.y - listBounds.top
        val items = listState.layoutInfo.visibleItemsInfo
        return items.firstOrNull { y < it.offset + it.size / 2f }?.index
            ?: items.lastOrNull()?.let { it.index + 1 }
    }
    fun finish(commit: Boolean) {
        val key = draggedKey
        if (commit && key != null) {
            if (hideBounds.contains(pointer)) {
                if (key !in hidden) hidden.add(key)
            } else if (key != CATEGORY_SHOW_ALL_KEY) {
                slot()?.let { target ->
                    val reordered = moveCategoryToSlot(visible, key, target)
                    val hiddenOrder = draft.filter { it in hidden }
                    draft.clear()
                    draft.addAll(reordered + hiddenOrder)
                }
            }
        }
        draggedKey = null
    }
    // Scroll under the floating row. Its root-space position is independent of
    // recycled list rows and item placement animations; cancel never mutates order.
    LaunchedEffect(draggedKey) {
        if (draggedKey == null) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (draggedKey != null) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            val speed = when {
                !listBounds.contains(pointer) -> 0f
                pointer.y < listBounds.top + edge -> -((listBounds.top + edge - pointer.y) / edge)
                pointer.y > listBounds.bottom - edge -> (pointer.y - listBounds.bottom + edge) / edge
                else -> 0f
            }
            if (speed != 0f) listState.scrollBy(speed * edge * 8f * dt)
        }
    }
    val targetSlot = if (draggedKey != null && draggedKey != CATEGORY_SHOW_ALL_KEY) slot() else null
    val latestVisible by rememberUpdatedState(visible)
    val finishDrag by rememberUpdatedState<(Boolean) -> Unit> { finish(it) }
    // Slides up like the notification / SYNC sheet.
    val sheetIn = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { sheetIn.animateTo(1f, androidx.compose.animation.core.tween(420, easing = com.cagri.real.ui.components.RealMotion.Emphasized)) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f * sheetIn.value))
        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(0.88f)
                .graphicsLayer { translationY = size.height * (1f - sheetIn.value) }
                .realSheetSurface()
                .navigationBarsPadding()
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = {})
        ) {
            Box(Modifier.fillMaxSize().onGloballyPositioned { panelBounds = it.boundsInRoot() }) {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    RealSheetHandle(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Kategoriler", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f))
                        Text("Tamam", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(CircleShape).background(Color.White)
                                .clickable { onSave(categoryOrderWithVisibility(draft, hidden.toSet()), previewCount) }
                                .padding(horizontal = 18.dp, vertical = 9.dp))
                    }
                    // How many show on Home before "Tümünü göster".
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp).clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.05f)).padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Ana sayfada", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, modifier = Modifier.weight(1f))
                        OrderStepButton("−", previewCount > 1) { previewCount-- }
                        com.cagri.real.ui.components.RollingNumberText(previewCount, color = Color.White,
                            style = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.widthIn(min = 26.dp))
                        OrderStepButton("+", previewCount < allItems.size) { previewCount++ }
                    }
                    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()
                        .onGloballyPositioned { listBounds = it.boundsInRoot() }
                        .pointerInput(Unit) {
                            // Own the gesture at the stable list node, not a recycled row.
                            // The remaining row area keeps normal list scrolling.
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                if (!down.isConsumed && down.position.x >= size.width - 48.dp.toPx()) {
                                    val absolute = listBounds.topLeft + down.position
                                    val hit = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                        down.position.y >= it.offset && down.position.y < it.offset + it.size
                                    }
                                    val key = hit?.key as? String
                                    val row = key?.let { rowBounds[it] }
                                    if (key != null && key in latestVisible && row != null) {
                                        var started = false
                                        var released = false
                                        try {
                                            while (true) {
                                                // Claim only the handle, before LazyColumn's scroll
                                                // detector sees the same movement in the Main pass.
                                                val change = awaitPointerEvent(PointerEventPass.Initial)
                                                    .changes.firstOrNull { it.id == down.id } ?: break
                                                if (change.isConsumed) break
                                                if (!change.pressed) {
                                                    if (started) change.consume()
                                                    released = true
                                                    break
                                                }
                                                if (!started && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                                    grab = absolute - row.topLeft
                                                    dragWidth = row.width
                                                    draggedKey = key
                                                    started = true
                                                }
                                                if (started) {
                                                    pointer = listBounds.topLeft + change.position
                                                    change.consume()
                                                }
                                            }
                                        } finally {
                                            if (started) finishDrag(released)
                                        }
                                    }
                                }
                            }
                        },
                        contentPadding = PaddingValues(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(visible, key = { _, key -> key }) { index, key ->
                            val active = key == draggedKey
                            Box(Modifier.fillMaxWidth().then(if (reduceAnimations) Modifier else Modifier.animateItem())
                                .onGloballyPositioned { rowBounds[key] = it.boundsInRoot() }) {
                                CategoryOrderRow(title(key), key == CATEGORY_SHOW_ALL_KEY,
                                    Modifier.graphicsLayer { alpha = if (active) 0.16f else 1f }
                                        .semantics {
                                            customActions = listOf(
                                                CustomAccessibilityAction("Gizle") { hidden.add(key); true },
                                                CustomAccessibilityAction("Yukarı taşı") {
                                                    val moved = moveCategoryToSlot(visible, key, (index - 1).coerceAtLeast(0))
                                                    val rest = draft.filter { it in hidden }
                                                    draft.clear(); draft.addAll(moved + rest); true
                                                },
                                                CustomAccessibilityAction("Aşağı taşı") {
                                                    val moved = moveCategoryToSlot(visible, key, index + 2)
                                                    val rest = draft.filter { it in hidden }
                                                    draft.clear(); draft.addAll(moved + rest); true
                                                }
                                            )
                                        },
                                    handle = Modifier, item = allItems[key])
                                if (targetSlot == index) Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF9DC3FF)))
                                if (targetSlot == visible.size && index == visible.lastIndex)
                                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(Color(0xFF9DC3FF)))
                            }
                        }
                    }
                    // The drop target is the bottom strip itself; it lights up while a row is being dragged.
                    val dragging = draggedKey != null
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp).onGloballyPositioned { hideBounds = it.boundsInRoot() }
                        .clip(RoundedCornerShape(18.dp))
                        .background(when {
                            overHide -> Color(0xFFFF8A80).copy(alpha = 0.22f)
                            dragging -> Color.White.copy(alpha = 0.06f)
                            else -> Color.Transparent
                        }),
                        contentAlignment = Alignment.Center) {
                        if (dragging) {
                            Text("Gizle", color = if (overHide) Color(0xFFFFB4AB) else Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        } else {
                            Text("Varsayılan", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp,
                                modifier = Modifier.clip(CircleShape).clickable {
                                    draft.clear(); draft.addAll(allItems.keys); draft.add(CATEGORY_SHOW_ALL_KEY)
                                    hidden.clear(); previewCount = 4
                                }.padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                    }
                    val hiddenItems = draft.filter { it in hidden }
                    if (hiddenItems.isNotEmpty()) {
                        Text("Gizlenenler", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 6.dp, start = 4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(hiddenItems, key = { it }) { key ->
                                Text("+ ${title(key)}", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 1,
                                    modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.07f))
                                        .clickable { hidden.remove(key) }.padding(horizontal = 14.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
                // Outside the scrolling viewport so it can follow the finger onto the hide target.
                draggedKey?.let { key ->
                    CategoryOrderRow(title(key), key == CATEGORY_SHOW_ALL_KEY,
                        Modifier.offset { IntOffset((pointer.x - grab.x - panelBounds.left).roundToInt(),
                            (pointer.y - grab.y - panelBounds.top).roundToInt()) }
                            .width(with(density) { dragWidth.toDp() }), lifted = true, item = allItems[key])
                }
            }
        }
    }
}

@Composable
private fun OrderStepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White.copy(alpha = if (enabled) 0.9f else 0.25f), fontSize = 20.sp)
    }
}

@Composable
private fun CategoryOrderRow(title: String, action: Boolean, modifier: Modifier = Modifier,
    handle: Modifier = Modifier, lifted: Boolean = false, item: OrderedCategoryItem? = null) {
    Row(modifier.heightIn(min = 56.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
        .background(if (lifted) Color(0xFF2E2F36) else Color.White.copy(alpha = 0.05f)),
        verticalAlignment = Alignment.CenterVertically) {
        val color = when (item) {
            is OrderedCategoryItem.BuiltIn -> item.category.defaultStyle().upper
            is OrderedCategoryItem.Custom -> item.category.customSolidColorArgb?.let { Color(it) }
                ?: ReminderColorChoices.getOrNull(item.category.accentStart)?.color ?: Color(0xFF3B82F6)
            else -> null
        }
        if (color != null) {
            Box(Modifier.padding(start = 16.dp).size(12.dp).clip(CircleShape).background(color))
        } else {
            Box(Modifier.padding(start = 16.dp).size(12.dp).clip(CircleShape).border(1.2.dp, Color.White.copy(alpha = 0.35f), CircleShape))
        }
        Text(title, color = if (action) Color.White.copy(alpha = 0.6f) else Color.White, fontSize = 15.sp,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 14.dp, top = 8.dp, bottom = 8.dp))
        Box(handle.size(48.dp), contentAlignment = Alignment.Center) {
            ActionIcon(ActionGlyph.DragHandle, Modifier.size(18.dp), Color.White.copy(alpha = 0.4f))
        }
    }
}
