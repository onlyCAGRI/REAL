package com.cagri.real.ui

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
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.34f))
        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        .padding(horizontal = 18.dp, vertical = 28.dp), contentAlignment = Alignment.Center) {
        DesignPanel(
            backdrop = backdrop, backdropPosition = backdropPosition,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = {}),
            corners = 32.dp, reduceDynamicBlur = reduceDynamicBlur, designStyle = designStyle,
            liquidDynamic = true, liquidSheet = true
        ) {
            Box(Modifier.fillMaxSize().onGloballyPositioned { panelBounds = it.boundsInRoot() }) {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text("Kategori düzeni", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text("Tutamacı sürükle. Gizlemek için aşağıdaki alana bırak.", color = Color(0xFFAAAAB4), fontSize = 12.sp,
                        modifier = Modifier.padding(top = 5.dp, bottom = 10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Ana sayfada göster", color = Color.White, fontSize = 14.sp)
                            Text("Daha az göster görünümündeki kart sayısı", color = Color(0xFF8F909A), fontSize = 11.sp)
                        }
                        TextButton(enabled = previewCount > 1, onClick = { previewCount-- }) { Text("−") }
                        Text("$previewCount", color = Color.White)
                        TextButton(enabled = previewCount < allItems.size, onClick = { previewCount++ }) { Text("+") }
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
                    Box(Modifier.fillMaxWidth().height(52.dp).onGloballyPositioned { hideBounds = it.boundsInRoot() }
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (overHide) Color(0xFF453240) else Color(0xFF232329))
                        .border(1.dp, if (overHide) Color(0xFFFFA6B5) else Color(0xFF45454E), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center) {
                        Text(if (overHide) "Bırak ve gizle" else "Gizlemek için buraya bırak", color = Color(0xFFDDDDE5), fontSize = 13.sp)
                    }
                    val hiddenItems = draft.filter { it in hidden }
                    if (hiddenItems.isNotEmpty()) {
                        Text("GİZLENENLER · Dokunarak geri ekle", color = Color(0xFF8F909A), fontSize = 10.sp,
                            modifier = Modifier.padding(top = 10.dp))
                        LazyRow {
                            items(hiddenItems, key = { it }) { key ->
                                TextButton(onClick = { hidden.remove(key) }) { Text("+ ${title(key)}", maxLines = 1) }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            draggedKey = null
                            draft.clear(); draft.addAll(allItems.keys); draft.add(CATEGORY_SHOW_ALL_KEY)
                            hidden.clear(); previewCount = 4
                        }) { Text("Varsayılan", color = Color(0xFFB8B9C2)) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("Kapat", color = Color(0xFFB8B9C2)) }
                        TextButton(onClick = { onSave(categoryOrderWithVisibility(draft, hidden.toSet()), previewCount) }) {
                            Text("Tamam", color = Color(0xFF9DC3FF))
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
private fun CategoryOrderRow(title: String, action: Boolean, modifier: Modifier = Modifier,
    handle: Modifier = Modifier, lifted: Boolean = false, item: OrderedCategoryItem? = null) {
    Row(modifier.heightIn(min = 58.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
        .background(if (lifted) Color(0xFF30313A) else Color(0xFF1C1D23))
        .border(0.8.dp, Color.White.copy(alpha = if (lifted) 0.3f else 0.08f), RoundedCornerShape(18.dp)),
        verticalAlignment = Alignment.CenterVertically) {
        if (item != null) {
            val colors = when (item) {
                is OrderedCategoryItem.BuiltIn -> item.category.defaultStyle().let { listOf(it.upper, it.lower) }
                is OrderedCategoryItem.Custom -> {
                    val start = ReminderColorChoices.getOrNull(item.category.accentStart)?.color ?: Color(0xFF3B82F6)
                    listOf(start, item.category.accentEnd?.let { ReminderColorChoices.getOrNull(it)?.color } ?: start)
                }
            }
            Box(Modifier.padding(start = 14.dp).size(32.dp).clip(CircleShape).background(Brush.linearGradient(colors)),
                contentAlignment = Alignment.Center) {
                when (item) {
                    is OrderedCategoryItem.BuiltIn -> CategoryGlyph(item.category.glyph(), Modifier.size(15.dp), Color.White)
                    is OrderedCategoryItem.Custom -> CategoryIconGlyph(CategoryIcon.fromKey(item.category.iconKey), Modifier.size(15.dp), Color.White)
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp, top = 8.dp, bottom = 8.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (action) Text("Ana sayfa düğmesi", color = Color(0xFF858691), fontSize = 11.sp)
        }
        Box(handle.size(48.dp), contentAlignment = Alignment.Center) {
            ActionIcon(ActionGlyph.DragHandle, Modifier.size(20.dp), Color(0xFFAAAAB4))
        }
    }
}
