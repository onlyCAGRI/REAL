package com.cagri.real.ui.pano

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalView
import com.cagri.real.ui.components.LocalLiquidReduceMotion
import com.cagri.real.ui.components.RealHaptics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.cagri.real.boards.*
import com.cagri.real.data.boards.BoardRealContent
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import com.cagri.real.ui.components.RealMotion
import com.cagri.real.ui.components.liquidChrome
import kotlin.math.*

@Composable
fun BoardCanvas(board:PanoBoard,items:List<PanoItem>,viewport:BoardViewport,selected:String?,controller:PanoController,designStyle:DesignStyle,onSelect:(String?)->Unit,onViewport:(BoardViewport)->Unit,modifier:Modifier=Modifier,editable:Boolean=false,look:BoardLook=BoardLook()){
    val sortedItems = remember(items) { items.sortedWith(compareBy<PanoItem> { it.transform.z }.thenBy { it.id }) }
    val density=LocalDensity.current.density
    val latestViewport by rememberUpdatedState(viewport);
    val latestItems by rememberUpdatedState(items)
    val latestSelected by rememberUpdatedState(selected)
    val latestEditable by rememberUpdatedState(editable)
    val reduceMotion = LocalLiquidReduceMotion.current
    var alignmentVisible by remember(board.id) { mutableStateOf(false) }
    var lastAlignment by remember(board.id) { mutableStateOf<BoardAlignment?>(null) }
    val guideAlpha by animateFloatAsState(if (alignmentVisible && editable) 0.55f else 0f,
        if (reduceMotion) snap() else tween(100), label = "boardAlignmentGuides")
    val showAlignment: (BoardAlignment?) -> Unit = { value ->
        alignmentVisible = value != null && (value.guideX != null || value.guideY != null)
        if (value != null) lastAlignment = value
    }
    Box(modifier.clipToBounds().boardViewportGestures(density,{latestViewport},{latestItems},onViewport,{controller.viewport(board.id,it)},{onSelect(null)},controlHit={point->latestEditable && latestItems.firstOrNull { it.id==latestSelected }?.let { blockHit(point,it.transform,48f) }==true})){
        BoardBackground(viewport, look)
        // Culling removes off-screen GIF painters and media players from composition.
        BoxWithConstraints(Modifier.fillMaxSize()){
            val width=maxWidth.value;
            val height=maxHeight.value
            sortedItems.forEach {item->
                val t=item.transform;
                val sx=viewport.x+t.x*viewport.zoom;
                val sy=viewport.y+t.y*viewport.zoom
                val margin=hypot(t.width,t.height)*viewport.zoom+48f*viewport.zoom
                if(sx+t.width*viewport.zoom+margin>=0&&sy+t.height*viewport.zoom+margin>=0&&sx-margin<=width&&sy-margin<=height){
                    key(item.id){BoardBlockFrame(item,board.shared,viewport.zoom,viewport,density,editable && item.id==selected,controller,designStyle,onSelect,editable,
                        neighbours = items.filter { it.id != item.id }.map { it.transform },
                        gridEnabled = look.supported().pattern != BoardBackgroundPattern.NONE,
                        onAlignment = showAlignment)}
                }
            }
        }
        Canvas(Modifier.fillMaxSize().zIndex(Float.MAX_VALUE)) {
            lastAlignment?.guideX?.let { x ->
                val screen = (viewport.x + x * viewport.zoom) * density
                drawLine(Color.White.copy(alpha = guideAlpha), Offset(screen, 0f), Offset(screen, size.height), 1.dp.toPx())
            }
            lastAlignment?.guideY?.let { y ->
                val screen = (viewport.y + y * viewport.zoom) * density
                drawLine(Color.White.copy(alpha = guideAlpha), Offset(0f, screen), Offset(size.width, screen), 1.dp.toPx())
            }
        }
        if(items.isEmpty())Text("+ Add something to your board",color=Color(0xFF606067),modifier=Modifier.align(Alignment.Center))
    }
}

@Composable
private fun BoardBlockFrame(item:PanoItem,shared:Boolean,zoom:Float,viewport:BoardViewport,density:Float,selected:Boolean,controller:PanoController,designStyle:DesignStyle,onSelect:(String?)->Unit,editable:Boolean,neighbours:List<BoardTransform>,gridEnabled:Boolean,onAlignment:(BoardAlignment?)->Unit){
    var draft by remember(item.id){mutableStateOf(item.transform)};
    var collapsedHeight by remember(item.id){mutableStateOf<Float?>(null)}
    var dragging by remember{mutableStateOf(false)}
    val currentItem by rememberUpdatedState(item);
    val latestDraft by rememberUpdatedState(draft)
    var startItem by remember(item.id){mutableStateOf(item)}
    val reduceMotion = LocalLiquidReduceMotion.current
    val view = LocalView.current
    val currentNeighbours by rememberUpdatedState(neighbours)
    val currentAlignment by rememberUpdatedState(onAlignment)
    val currentZoom by rememberUpdatedState(zoom)
    val currentGrid by rememberUpdatedState(gridEnabled)
    // Pointer tracking remains exact; only the final small snap is spring animated.
    val shownX by animateFloatAsState(draft.x, if (dragging || reduceMotion) snap() else RealMotion.smooth(0.22f), label = "boardSnapX")
    val shownY by animateFloatAsState(draft.y, if (dragging || reduceMotion) snap() else RealMotion.smooth(0.22f), label = "boardSnapY")
    DisposableEffect(item.id, editable) {
        onDispose { if (dragging) { dragging = false; currentAlignment(null) } }
    }
    LaunchedEffect(item.transform){if(!dragging)draft=item.transform}
    val shape=RoundedCornerShape(18.dp)
    val move=Modifier.pointerInput(item.id,density,editable){detectDragGestures(
        onDragStart={dragging=true;
            currentAlignment(null)
            startItem=currentItem;
            onSelect(item.id)},
        onDragCancel={dragging=false;
            currentAlignment(null)
            draft=currentItem.transform},
        onDragEnd={
            val before = latestDraft
            val aligned = alignBoardBlock(before, currentNeighbours, currentZoom, currentGrid)
            draft = aligned.transform.clamped(item.type)
            dragging = false
            currentAlignment(null)
            if (draft.x != before.x || draft.y != before.y) RealHaptics.tick(view)
            controller.transform(startItem,draft)},
        onDrag={change,amount->change.consume();
            val d=boardLocalDragToWorld(amount,density,latestDraft.rotation);
            draft=latestDraft.copy(x=latestDraft.x+d.x,y=latestDraft.y+d.y).clamped(item.type)
            currentAlignment(alignBoardBlock(draft, currentNeighbours, currentZoom, currentGrid))}
    )}
    val presentation=LocalBoardRealPresentation.current
    val styleHost=remember(item.id){PanoTextStyleHost()}
    // Reserve the same outer hit area in both modes. Content stays at (x,y),
    // regardless of selection; controls never take space away from the block.
    Box(Modifier.offset {IntOffset(((viewport.x+shownX*zoom-48f)*density).roundToInt(),((viewport.y+shownY*zoom-48f)*density).roundToInt())}
        .wrapContentSize(Alignment.TopStart,unbounded=true).requiredSize((draft.width+96f).dp,(draft.height+96f).dp).zIndex(draft.z.toFloat())
        .graphicsLayer {
            transformOrigin=TransformOrigin(48f/(draft.width+96f),48f/(draft.height+96f))
            scaleX=zoom; scaleY=zoom; rotationZ=draft.rotation
        }) {
        val selectOnPress = Modifier.pointerInput(item.id) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                onSelect(item.id)
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
            }
        }
        val liftScale by animateFloatAsState(
            if (dragging && !reduceMotion) 1.025f else 1f,
            if (reduceMotion) snap() else RealMotion.snappy(0.2f),
            label = "panoBlockLift"
        )
        Box(
            Modifier.offset(48.dp, 48.dp).requiredSize(draft.width.dp, draft.height.dp)
                .graphicsLayer {
                    scaleX = liftScale
                    scaleY = liftScale
                    shadowElevation = if (dragging) 16.dp.toPx() else if (selected) 6.dp.toPx() else 0f
                    this.shape = shape
                    clip = false
                }
                .then(if (editable) selectOnPress.then(move) else Modifier)
                .border(
                    if (selected) 1.5.dp else 0.dp,
                    if (selected) Color.White.copy(alpha = 0.55f) else Color.Transparent,
                    shape
                )
        ) {
            Box(Modifier.fillMaxSize().clipToBounds()) {
                CompositionLocalProvider(LocalPanoTextStyleHost provides styleHost) { BoardItemContent(item, selected, controller, designStyle, editable) }
            }
        }
        if (shared && !(selected && item.type == BoardBlockType.TEXT)) Text(
            item.authorName, color = Color.White.copy(alpha = 0.34f), fontSize = 10.sp, maxLines = 1,
            modifier = Modifier.offset(48.dp, (draft.height + 50f).dp).width(draft.width.dp)
        )
        if (selected && item.type == BoardBlockType.TEXT) styleHost.actions?.let { a ->
            // Text settings live outside the box, like every other block control.
            Row(
                Modifier.offset(50.dp, (draft.height + 54f).dp).width((draft.width - 4f).coerceAtLeast(120f).dp).height(36.dp)
                    .liquidChrome(corners = 18.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                PanoTextColors.forEach { (key, color) -> PanoColorDot(color, a.color == key) { a.setColor(key) } }
                Box(Modifier.width(1.dp).height(18.dp).background(Color.White.copy(alpha = 0.15f)))
                listOf("left", "center", "right").forEach { k -> PanoAlignButton(k, a.align == k) { a.setAlign(k) } }
                Box(Modifier.width(1.dp).height(18.dp).background(Color.White.copy(alpha = 0.15f)))
                Box(Modifier.clip(RoundedCornerShape(12.dp)).clickable { a.toggleTitle() }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(if (a.title) "Body" else "Title", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                }
            }
        }
        if (selected) {
            // Drag / Move handle (top-left)
            Box(
                Modifier.offset(8.dp, 8.dp).size(34.dp).then(move).liquidChrome(corners = 17.dp),
                contentAlignment = Alignment.Center
            ) {
                ActionIcon(ActionGlyph.DragHandle, Modifier.size(16.dp), color = Color.White.copy(alpha = 0.85f))
            }
            // Top contextual control surface
            Row(
                Modifier.offset(48.dp, 2.dp).height(36.dp).liquidChrome(corners = 18.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.type == BoardBlockType.STACK) {
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .clickable { presentation.onEditStack(currentItem) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Edit", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                // Only card stacks can grow; every other block keeps its size.
                val canExpand = item.type == BoardBlockType.STACK ||
                    (item.type == BoardBlockType.CARD && BoardRealContent.isStackView(currentItem.content()))
                if (canExpand) {
                    val isExpanded = item.type == BoardBlockType.CARD && currentItem.content().optBoolean("expanded") || collapsedHeight != null
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (item.type == BoardBlockType.CARD) {
                                    val expanded = currentItem.content().optBoolean("expanded")
                                    controller.content(currentItem, currentItem.content().put("expanded", !expanded))
                                    controller.transform(currentItem, draft.copy(height = if (expanded) 180f else 600f))
                                } else {
                                    val bounds = item.type.bounds()
                                    val previous = collapsedHeight
                                    val height = if (previous != null) previous else (draft.height * 1.5f).coerceAtMost(bounds.maxHeight)
                                    collapsedHeight = if (previous != null) null else draft.height
                                    controller.transform(currentItem, draft.copy(height = height))
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(if (isExpanded) "Collapse" else "Expand", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                }
                Box(
                    Modifier.size(30.dp).clip(CircleShape).clickable {
                        val max = controller.repository.state.value.items[item.boardId].orEmpty().maxOfOrNull { it.transform.z } ?: 0
                        controller.transform(currentItem, draft.copy(z = (max + 1).coerceAtMost(1000000000)))
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Text("↑", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                }
                Box(
                    Modifier.size(30.dp).clip(CircleShape).clickable {
                        controller.delete(currentItem)
                        onSelect(null)
                    },
                    contentAlignment = Alignment.Center
                ) {
                    ActionIcon(ActionGlyph.Close, Modifier.size(13.dp), color = Color(0xFFFF6B6B))
                }
            }
            // Resize handle (bottom-right)
            Box(
                Modifier.offset((draft.width + 46f).dp, (draft.height + 46f).dp).size(34.dp).liquidChrome(corners = 17.dp)
                    .pointerInput(item.id, density, editable) {
                        detectDragGestures(
                            onDragStart = { dragging = true; startItem = currentItem },
                            onDragEnd = { dragging = false; controller.transform(startItem, latestDraft) },
                            onDragCancel = { dragging = false; draft = currentItem.transform }
                        ) { change, delta ->
                            change.consume()
                            val t = latestDraft
                            draft = if (item.type in listOf(BoardBlockType.PHOTO, BoardBlockType.GIF)) {
                                val ratio = t.height / t.width
                                val w = t.width + delta.x / density
                                t.copy(width = w, height = w * ratio).clamped(item.type)
                            } else {
                                t.copy(
                                    width = t.width + delta.x / density,
                                    height = t.height + delta.y / density
                                ).clamped(item.type)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                ActionIcon(ActionGlyph.ChevronRight, Modifier.size(14.dp).graphicsLayer { rotationZ = 45f }, color = Color.White.copy(alpha = 0.9f))
            }
            // Rotate handle (bottom-left)
            Box(
                Modifier.offset(8.dp, (draft.height + 46f).dp).size(34.dp).liquidChrome(corners = 17.dp)
                    .pointerInput(item.id, density, editable) {
                        detectDragGestures(
                            onDragStart = { dragging = true; startItem = currentItem },
                            onDragEnd = { dragging = false; controller.transform(startItem, latestDraft) },
                            onDragCancel = { dragging = false; draft = currentItem.transform }
                        ) { change, _ ->
                            change.consume()
                            // Handle coordinates are inverse-transformed by Compose. Both positions
                            // use the current transform, so their angular difference has no feedback.
                            val pivot = Offset(40f * density, (2f - latestDraft.height) * density)
                            val before = change.previousPosition - pivot
                            val after = change.position - pivot
                            draft = latestDraft.copy(rotation = latestDraft.rotation + boardRotationDelta(before, after)).clamped(item.type)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                ActionIcon(ActionGlyph.Repeat, Modifier.size(14.dp), color = Color.White.copy(alpha = 0.9f))
            }
        }
    }
}
