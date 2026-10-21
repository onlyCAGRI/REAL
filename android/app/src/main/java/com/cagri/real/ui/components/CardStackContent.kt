package com.cagri.real.ui.components

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.cagri.real.ui.CardPhotoZoomState
import com.cagri.real.ui.cardPhotoZoomInput
import com.cagri.real.ui.cardPhotoPixels
import com.cagri.real.ui.LocalCardBlockDragging
import com.cagri.real.data.AttachmentStorage
import com.cagri.real.data.CardStackBlock
import com.cagri.real.data.CardStackCard
import com.cagri.real.data.cardStackFrontHeightDp
import com.cagri.real.ui.DesignStyle
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

val CardStackDefaultColor = Color(0xFF292B2D)

val CardStackPalette = listOf(
    CardStackDefaultColor, // default / jet black
    Color(0xFFEE5E37), // salmon orange
    Color(0xFFD9B8F3), // purple
    Color(0xFFDFF37D), // old flax
    Color(0xFF4558C8), // royal blue
    Color(0xFF2E9CA6), // teal
    Color(0xFF3D7C63), // green
    Color(0xFFB88A3A), // warm gold
    Color(0xFFB74769), // rose
    Color(0xFF6B4BB5), // violet
    Color(0xFFEFF0F6), // smoky white
    Color(0xFF366EBC)  // blue
)

private val demoStackCardHeight = 390.dp
private val demoStackCardMaxHeight = 780.dp
private val demoBackPeekNoImage = 72.dp
private val demoBackPeekWithImage = 140.dp
private val demoStackReservedVerticalSpace = 210.dp
private val demoStackWritingAreaReduction = 60.dp // ~3 body lines: keep card size, leave more breathing room
private const val demoStackCardWidthFraction = 1f

fun cardColorFor(card: CardStackCard, index: Int): Color {
    return card.colorArgb?.let { Color(it) } ?: CardStackDefaultColor
}

fun cardForegroundFor(backgroundColor: Color, hasImage: Boolean): Color {
    return if (hasImage || backgroundColor.luminance() < 0.48f) Color.White else Color(0xFF161616)
}

fun peekHeightFor(card: CardStackCard?): androidx.compose.ui.unit.Dp {
    return if (card?.imageUri != null) demoBackPeekWithImage else demoBackPeekNoImage
}

@Composable
fun CardStackContentSection(
    block: CardStackBlock,
    designStyle: DesignStyle,
    editing: Boolean,
    inlineTextEditing: Boolean = false,
    initialActiveCardIndex: Int = 0,
    onRequestInlineEdit: (Int) -> Unit = {},
    onUpdate: (CardStackBlock) -> Unit,
    onRemove: () -> Unit,
    onImageAdded: (String) -> Unit = {},
    onImageReplacedOrRemoved: (String) -> Unit = {},
    showRemoveControl: Boolean = true,
    heightOverride: androidx.compose.ui.unit.Dp? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val measureDetailHeight = com.cagri.real.ui.LocalCardContentComposition.current
    var showEditorTools by remember(block.id, editing) { mutableStateOf(false) }
    var sectionHeightPx by remember(block.id) { mutableIntStateOf(0) }
    var deckHeightPx by remember(block.id) { mutableIntStateOf(0) }
    val chromeHeightDp = with(density) { (sectionHeightPx - deckHeightPx).coerceAtLeast(0).toDp() }
    val bodyTextStyle = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    )
    val maxBodyHeightPx = with(density) { (demoStackCardMaxHeight - demoStackReservedVerticalSpace - demoStackWritingAreaReduction).toPx() }
    val approxTextWidthPx = with(density) { 320.dp.toPx().toInt() }

    // Safe normalization / reflow for legacy oversized cards
    LaunchedEffect(block.id, editing, inlineTextEditing) {
        if ((editing || inlineTextEditing) && block.cards.isNotEmpty()) {
            val reflowed = reflowOversizedCardsIfNeeded(
                cards = block.cards,
                textWidthPx = approxTextWidthPx,
                maxBodyHeightPx = maxBodyHeightPx,
                textMeasurer = textMeasurer,
                bodyTextStyle = bodyTextStyle,
                density = density
            )
            if (reflowed != null) {
                onUpdate(block.copy(cards = reflowed))
            }
        }
    }

    // In-memory reflow for read-only view so older oversized cards never scroll internally
    val rawCards = if (block.cards.isEmpty()) listOf(CardStackCard()) else block.cards
    val cards = remember(block.cards, approxTextWidthPx) {
        reflowOversizedCardsIfNeeded(
            cards = rawCards,
            textWidthPx = approxTextWidthPx,
            maxBodyHeightPx = maxBodyHeightPx,
            textMeasurer = textMeasurer,
            bodyTextStyle = bodyTextStyle,
            density = density
        ) ?: rawCards
    }

    var activeCardIndex by remember(block.id, inlineTextEditing) {
        mutableIntStateOf(initialActiveCardIndex.coerceIn(0, (cards.size - 1).coerceAtLeast(0)))
    }
    val safeActiveIndex = activeCardIndex.coerceIn(0, (cards.size - 1).coerceAtLeast(0))
    val activeCard = cards[safeActiveIndex]

    val photoScope = rememberCoroutineScope()
    val latestBlock by rememberUpdatedState(block)
    val latestCards by rememberUpdatedState(cards)
    val latestUpdate by rememberUpdatedState(onUpdate)
    val latestImageAdded by rememberUpdatedState(onImageAdded)
    val latestImageRemoved by rememberUpdatedState(onImageReplacedOrRemoved)
    var photoImportBusy by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && !photoImportBusy) {
            val destinationBlock = block.id
            val destinationCard = activeCard.id
            val workspace = com.cagri.real.platform.android.sync.SyncLocal.workspaceToken()
            photoImportBusy = true
            photoScope.launch {
                var imported: com.cagri.real.data.ReminderAttachment? = null
                var adopted = false
                try {
                    withContext(Dispatchers.IO + NonCancellable) { imported = AttachmentStorage.copyUriToAttachments(context, uri) }
                    val photo = imported ?: return@launch
                    val currentCards = latestCards
                    val index = currentCards.indexOfFirst { it.id == destinationCard }
                    if (latestBlock.id != destinationBlock || index < 0 || workspace != com.cagri.real.platform.android.sync.SyncLocal.workspaceToken()) return@launch
                    val oldImage = currentCards[index].imageUri
                    latestImageAdded(photo.localPath)
                    val newCards = currentCards.toMutableList()
                    newCards[index] = currentCards[index].copy(imageUri = photo.localPath)
                    latestUpdate(latestBlock.copy(cards = newCards))
                    adopted = true
                    if (oldImage != null) latestImageRemoved(oldImage)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Failed import leaves the previous image intact; no private paths are logged.
                } finally {
                    if (!adopted) withContext(Dispatchers.IO + NonCancellable) { imported?.let { AttachmentStorage.deleteAttachmentFiles(context, listOf(it)) } }
                    photoImportBusy = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { if (measureDetailHeight) sectionHeightPx = it.height }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Minimal header row: small Cards glyph on the left, unobtrusive close button on the right (edit mode)
        Row(
            modifier = Modifier
                .fillMaxWidth(demoStackCardWidthFraction)
                .padding(bottom = 6.dp)
                .then(if (measureDetailHeight) Modifier.height(28.dp) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(ActionGlyph.Cards, Modifier.size(16.dp), Color(0xFF8E8E98))
                if (editing && measureDetailHeight) {
                    IconButton(onClick = { showEditorTools = true }, modifier = Modifier.size(28.dp)
                        .semantics { contentDescription = "Kart yığını ayarları" }) {
                        ActionIcon(ActionGlyph.Settings, Modifier.size(15.dp), Color(0xFFB7B7C0))
                    }
                }
            }
            if (cards.getOrNull(safeActiveIndex)?.id?.startsWith("completed_journal_") == true) {
                Text(
                    text = "Aklında kalanı yaz.",
                    color = Color(0xFFBBBBC6),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
            }


            if (editing && showRemoveControl) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(28.dp)
                ) {
                    ActionIcon(
                        ActionGlyph.Close,
                        Modifier.size(14.dp).semantics { contentDescription = "Kart yığınını kaldır" },
                        Color(0xFF8E8E98)
                    )
                }
            }
        }

        // Optional AI / contextual prompt sitting quietly above the deck
        if (!block.prompt.isNullOrBlank() && cards.getOrNull(safeActiveIndex)?.id?.startsWith("completed_journal_") != true) {
            Text(
                text = "“${block.prompt}”",
                color = Color(0xFFBBBBC6),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier
                    .fillMaxWidth(demoStackCardWidthFraction)
                    .padding(bottom = 12.dp)
            )
        }

        // Production Deck with bidirectional peeks matching DemoStackCards physics
        ProductionCardStackDeck(
            cards = cards,
            heightOverride = heightOverride,
            chromeHeight = chromeHeightDp,
            stableDetailGeometry = measureDetailHeight,
            onDeckMeasured = { if (measureDetailHeight) deckHeightPx = it },
            activeCard = safeActiveIndex,
            editing = editing,
            inlineTextEditing = inlineTextEditing,
            onRequestInlineEdit = onRequestInlineEdit,
            onPromote = { activeCardIndex = it },
            onUpdateCard = { updatedCard ->
                val newCards = cards.toMutableList()
                newCards[safeActiveIndex] = updatedCard
                onUpdate(block.copy(cards = newCards))
            },
            onDeleteActiveCard = {
                if (cards.size > 1) {
                    val removed = activeCard.imageUri
                    if (removed != null) {
                        onImageReplacedOrRemoved(removed)
                    }
                    val newCards = cards.filterIndexed { idx, _ -> idx != safeActiveIndex }
                    val newActive = if (safeActiveIndex >= newCards.size) newCards.size - 1 else safeActiveIndex
                    activeCardIndex = newActive
                    onUpdate(block.copy(cards = newCards))
                }
            },
            onAddCard = {
                val nextColorArgb = CardStackDefaultColor.toArgb()
                val newCard = CardStackCard(colorArgb = nextColorArgb)
                val newCards = cards + newCard
                activeCardIndex = newCards.size - 1
                onUpdate(block.copy(cards = newCards))
            },
            onPickImage = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRemoveImage = {
                val oldImage = activeCard.imageUri
                if (oldImage != null) {
                    onImageReplacedOrRemoved(oldImage)
                }
                val newCards = cards.toMutableList()
                newCards[safeActiveIndex] = activeCard.copy(imageUri = null)
                onUpdate(block.copy(cards = newCards))
            },
            onRequestNewCardFromAutoFlow = { updatedCards, promotedIndex ->
                activeCardIndex = promotedIndex
                onUpdate(block.copy(cards = updatedCards))
            }
        )

        // Card navigation indicator pills in read-only mode (when > 1 cards)
        if ((!editing || measureDetailHeight) && cards.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(demoStackCardWidthFraction)
                    .padding(top = 14.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                cards.forEachIndexed { idx, _ ->
                    val selected = idx == safeActiveIndex
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.07f))
                            .border(
                                0.8.dp,
                                if (selected) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.14f),
                                RoundedCornerShape(50)
                            )
                            .clickable(role = Role.Button) {
                                if (idx != safeActiveIndex) {
                                    activeCardIndex = idx
                                }
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Kart ${idx + 1}",
                            color = if (selected) Color.White else Color(0xFFA5A5AD),
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Bottom controls in edit mode (Pills, + Kart Ekle, & 12-Color Palette)
        if (editing) {
            CardStackEditorTools(
                detached = measureDetailHeight, visible = showEditorTools,
                onDismiss = { showEditorTools = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(demoStackCardWidthFraction)
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (measureDetailHeight) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) { Text(if (activeCard.imageUri == null) "Fotoğraf ekle" else "Fotoğrafı değiştir") }
                            if (activeCard.imageUri != null) TextButton(onClick = {
                                activeCard.imageUri?.let(onImageReplacedOrRemoved)
                                onUpdate(block.copy(cards = cards.mapIndexed { i, card ->
                                    if (i == safeActiveIndex) card.copy(imageUri = null) else card
                                }))
                            }) { Text("Kaldır") }
                        }
                    }

                    // Card Selector Pills & + Kart Ekle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        cards.forEachIndexed { idx, _ ->
                            val selected = idx == safeActiveIndex
                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) Color(0xFF343439) else Color(0xFF171719))
                                    .border(
                                        0.8.dp,
                                        if (selected) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f),
                                        RoundedCornerShape(50)
                                    )
                                    .clickable(role = Role.Button) {
                                        if (idx != safeActiveIndex) {
                                            activeCardIndex = idx
                                        }
                                    }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Kart ${idx + 1}",
                                    color = if (selected) Color.White else Color(0xFFA5A5AD),
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }

                        // + Kart ekle button
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(0.8.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                                .clickable(role = Role.Button) {
                                    val nextColorArgb = CardStackDefaultColor.toArgb()
                                    val newCard = CardStackCard(colorArgb = nextColorArgb)
                                    val newCards = cards + newCard
                                    activeCardIndex = newCards.size - 1
                                    onUpdate(block.copy(cards = newCards))
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ActionIcon(ActionGlyph.Add, Modifier.size(12.dp), Color.White)
                                Text("Kart ekle", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // 12-Color Palette Row matching demo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CardStackPalette.forEach { color ->
                            val isSelected = activeCard.colorArgb == color.toArgb() ||
                                    (activeCard.colorArgb == null && color == CardStackDefaultColor)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 2.2.dp else 0.8.dp,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.25f),
                                        shape = CircleShape
                                    )
                                    .clickable(role = Role.Button) {
                                        val newCards = cards.toMutableList()
                                        newCards[safeActiveIndex] = activeCard.copy(colorArgb = color.toArgb())
                                        onUpdate(block.copy(cards = newCards))
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Detail tools use a separate window, so their height never enters persisted grid geometry. */
@Composable
private fun CardStackEditorTools(
    detached: Boolean, visible: Boolean, onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    if (!detached) {
        content()
    } else if (visible) {
        androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF171719)).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Kart yığını", color = Color.White, fontSize = 16.sp)
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text("Bitti", color = Color.White)
                    }
                }
                content()
            }
        }
    }
}


@Composable
private fun ProductionCardStackDeck(
    cards: List<CardStackCard>,
    activeCard: Int,
    editing: Boolean,
    inlineTextEditing: Boolean,
    onRequestInlineEdit: (Int) -> Unit,
    onPromote: (Int) -> Unit,
    onUpdateCard: (CardStackCard) -> Unit,
    onDeleteActiveCard: () -> Unit,
    onAddCard: () -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onRequestNewCardFromAutoFlow: (List<CardStackCard>, Int) -> Unit,
    heightOverride: androidx.compose.ui.unit.Dp? = null,
    chromeHeight: androidx.compose.ui.unit.Dp = 0.dp,
    stableDetailGeometry: Boolean = false,
    onDeckMeasured: (Int) -> Unit = {}
) {
    val density = LocalDensity.current
    val isSingleCard = cards.size <= 1

    val prevIndex = if (activeCard > 0) activeCard - 1 else null
    val nextIndex = if (activeCard < cards.size - 1) activeCard + 1 else null

    val prevCard = prevIndex?.let { cards.getOrNull(it) }
    val nextCard = nextIndex?.let { cards.getOrNull(it) }

    val prevPeek = if (prevCard != null) peekHeightFor(prevCard) else 0.dp
    val nextPeek = if (nextCard != null) peekHeightFor(nextCard) else 0.dp

    val heightTextMeasurer = rememberTextMeasurer()
    val heightBodyStyle = remember {
        TextStyle(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Normal
        )
    }
    val heightTextWidthPx = with(density) { 320.dp.toPx().toInt() }
    val activeCardForHeight = cards.getOrNull(activeCard)
    val activeBodyHeight = remember(activeCardForHeight?.id, activeCardForHeight?.body, heightTextWidthPx) {
        val body = activeCardForHeight?.body.orEmpty()
        if (body.isBlank()) {
            0.dp
        } else {
            val px = heightTextMeasurer.measure(
                text = AnnotatedString(body),
                style = heightBodyStyle,
                constraints = Constraints(maxWidth = heightTextWidthPx)
            ).size.height
            with(density) { px.toDp() }
        }
    }
    val totalPeekHeight = if (isSingleCard) {
        if (editing && !stableDetailGeometry) demoBackPeekNoImage * 0.5f else 0.dp
    } else prevPeek + nextPeek
    val desiredFrontHeight = if (heightOverride != null) {
        cardStackFrontHeightDp(heightOverride.value, chromeHeight.value, totalPeekHeight.value).dp
    } else (demoStackReservedVerticalSpace + demoStackWritingAreaReduction + activeBodyHeight)
        .coerceIn(demoStackCardHeight, demoStackCardMaxHeight)
    val currentFrontHeight by animateDpAsState(
        targetValue = desiredFrontHeight,
        animationSpec = spring(dampingRatio = 0.90f, stiffness = 420f),
        label = "Card stack writing height"
    )

    val frontHeight = if (heightOverride != null) desiredFrontHeight else currentFrontHeight
    val targetContainerHeight = if (isSingleCard) {
        frontHeight + (if (editing && !stableDetailGeometry) demoBackPeekNoImage * 0.5f else 0.dp)
    } else {
        prevPeek + frontHeight + nextPeek
    }

    val containerHeight by animateDpAsState(
        targetValue = targetContainerHeight,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 380f),
        label = "Production deck height"
    )

    var previousActiveCard by remember { mutableIntStateOf(activeCard) }
    var swapFromCard by remember { mutableIntStateOf(activeCard) }
    val swapProgress = remember { Animatable(1f) }

    LaunchedEffect(activeCard, cards.size) {
        if (cards.isEmpty()) return@LaunchedEffect
        val safePrevious = previousActiveCard.coerceIn(0, cards.lastIndex)
        if (activeCard != safePrevious) {
            swapFromCard = safePrevious
            previousActiveCard = activeCard
            swapProgress.snapTo(0f)
            swapProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 390, easing = FastOutSlowInEasing)
            )
        } else {
            previousActiveCard = activeCard
            swapFromCard = activeCard
        }
    }

    val progress = swapProgress.value.coerceIn(0f, 1f)
    val arc = sin(progress * PI).toFloat()
    val isSwapping = progress < 0.999f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (heightOverride != null) targetContainerHeight else containerHeight)
            .onSizeChanged { onDeckMeasured(it.height) },
        contentAlignment = Alignment.TopCenter
    ) {
        if (isSingleCard) {
            val frontCard = cards.firstOrNull() ?: CardStackCard()
            val frontColor = cardColorFor(frontCard, 0)
            val decorativeBackShape = RoundedCornerShape(30.dp)

            // Decorative backing is excluded from the detail canvas geometry.
            if (editing && !stableDetailGeometry) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(demoStackCardWidthFraction * 0.95f)
                        .height(frontHeight)
                        .offset { IntOffset(0, (demoBackPeekNoImage * 0.5f).roundToPx()) }
                        .zIndex(1f)
                        .clip(decorativeBackShape)
                        .background(frontColor.copy(alpha = 0.30f))
                        .border(0.9.dp, Color.White.copy(alpha = 0.18f), decorativeBackShape)
                        .clickable(role = Role.Button, onClick = onAddCard)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        "+ Kart ekle",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Single active front card
            SingleStackCard(
                card = frontCard,
                allowCompactHeight = heightOverride != null,
                index = 0,
                totalCards = 1,
                active = true,
                editing = editing,
                inlineTextEditing = inlineTextEditing,
                onRequestInlineEdit = onRequestInlineEdit,
                motionEmphasis = 0f,
                promoteEnabled = false,
                cards = cards,
                isTopPeek = false,
                isBottomPeek = false,
                onPromote = {},
                onUpdateCard = onUpdateCard,
                onDeleteCard = onDeleteActiveCard,
                onPickImage = onPickImage,
                onRemoveImage = onRemoveImage,
                onMeasuredHeight = {},
                onRequestNewCardFromAutoFlow = onRequestNewCardFromAutoFlow,
                modifier = Modifier
                    .fillMaxWidth(demoStackCardWidthFraction)
                    .height(frontHeight)
                    .offset { IntOffset(0, 0) }
                    .zIndex(2f)
                    .graphicsLayer { shadowElevation = 16.dp.toPx() }
            )
        } else {
            // Multi-card deck: render active card + neighboring peeks + swapping cards
            val safeSwapFromCard = swapFromCard.coerceIn(0, cards.lastIndex)
            val indicesToRender = listOfNotNull(prevIndex, nextIndex, safeSwapFromCard, activeCard)
                .filter { it in cards.indices }
                .distinct()

            indicesToRender.forEach { index ->
                key(index) {
                    val isActive = index == activeCard
                    val isIncoming = isSwapping && index == activeCard
                    val isOutgoing = isSwapping && index == safeSwapFromCard

                    val targetTop = when (index) {
                        activeCard -> prevPeek
                        activeCard - 1 -> 0.dp
                        activeCard + 1 -> prevPeek + nextPeek
                        else -> if (index < activeCard) 0.dp else prevPeek + nextPeek
                    }

                    val top by animateDpAsState(
                        targetValue = targetTop,
                        animationSpec = spring(dampingRatio = 0.84f, stiffness = 360f),
                        label = "Production card Y $index"
                    )

                    val incomingDirection = if (activeCard >= safeSwapFromCard) 1f else -1f
                    val motionY = when {
                        isIncoming -> 24.dp * incomingDirection * arc
                        isOutgoing -> -9.dp * incomingDirection * arc
                        else -> 0.dp
                    }
                    val animatedScale = when {
                        isIncoming -> 1f + (0.030f * arc)
                        isOutgoing -> 1f - (0.016f * arc)
                        else -> 1f
                    }
                    val shadowDp = when {
                        isIncoming -> 5f + (11f * progress) + (12f * arc)
                        isOutgoing -> (16f - (11f * progress) - (4f * arc)).coerceAtLeast(3f)
                        isActive -> 16f
                        else -> 5f
                    }
                    val cardZ = when {
                        isIncoming && progress < 0.22f -> 1f
                        isIncoming -> 4f
                        isOutgoing -> 3f
                        isActive -> 2f
                        else -> 1f
                    }

                    SingleStackCard(
                        card = cards[index],
                        allowCompactHeight = heightOverride != null,
                        index = index,
                        totalCards = cards.size,
                        active = isActive,
                        editing = editing,
                        inlineTextEditing = inlineTextEditing,
                        onRequestInlineEdit = onRequestInlineEdit,
                        motionEmphasis = if (isIncoming) arc else 0f,
                        promoteEnabled = !isSwapping,
                        cards = cards,
                        isTopPeek = !isActive && index < activeCard,
                        isBottomPeek = !isActive && index > activeCard,
                        onPromote = { if (!isActive) onPromote(index) },
                        onUpdateCard = onUpdateCard,
                        onDeleteCard = onDeleteActiveCard,
                        onPickImage = onPickImage,
                        onRemoveImage = onRemoveImage,
                        onMeasuredHeight = {},
                        onRequestNewCardFromAutoFlow = onRequestNewCardFromAutoFlow,
                        modifier = Modifier
                            .fillMaxWidth(demoStackCardWidthFraction)
                            .height(frontHeight)
                            .offset { IntOffset(0, top.roundToPx()) }
                            .zIndex(cardZ)
                            .graphicsLayer {
                                translationY = motionY.toPx()
                                scaleX = animatedScale
                                scaleY = animatedScale
                                shadowElevation = shadowDp.dp.toPx()
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleStackCard(
    card: CardStackCard,
    index: Int,
    totalCards: Int,
    active: Boolean,
    editing: Boolean,
    inlineTextEditing: Boolean,
    onRequestInlineEdit: (Int) -> Unit,
    motionEmphasis: Float,
    promoteEnabled: Boolean,
    cards: List<CardStackCard>,
    modifier: Modifier = Modifier,
    isTopPeek: Boolean = false,
    isBottomPeek: Boolean = false,
    onPromote: () -> Unit,
    onUpdateCard: (CardStackCard) -> Unit,
    onDeleteCard: () -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onMeasuredHeight: (Int) -> Unit,
    onRequestNewCardFromAutoFlow: (List<CardStackCard>, Int) -> Unit,
    allowCompactHeight: Boolean = false
) {
    val shape = RoundedCornerShape(30.dp)
    val photoZoom = remember(card.id, card.imageUri) { CardPhotoZoomState() }
    val allowPhotoZoom = com.cagri.real.ui.LocalCardContentComposition.current && active && card.imageUri != null && !LocalCardBlockDragging.current
    val cardColor = cardColorFor(card, index)
    val hasImage = card.imageUri != null
    val foreground = cardForegroundFor(cardColor, hasImage)
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    var cardWidthPx by remember { mutableIntStateOf(0) }

    val bodyFocusRequester = remember(card.id) { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var bodyTextFieldValue by remember(card.id) {
        mutableStateOf(TextFieldValue(text = card.body))
    }
    androidx.compose.runtime.LaunchedEffect(card.id, card.body) {
        if (card.body != bodyTextFieldValue.text) {
            bodyTextFieldValue = bodyTextFieldValue.copy(
                text = card.body,
                selection = androidx.compose.ui.text.TextRange(card.body.length)
            )
        }
    }

    val bodyTextStyle = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    )

    val detailComposition = com.cagri.real.ui.LocalCardContentComposition.current
    BoxWithConstraints(
        modifier = modifier
            .cardPhotoZoomInput(photoZoom, allowPhotoZoom)
            .then(if (allowCompactHeight) Modifier.heightIn(min = 80.dp, max = 800.dp)
                else Modifier.defaultMinSize(minHeight = demoStackCardHeight)
                    .heightIn(min = demoStackCardHeight, max = demoStackCardMaxHeight))
            .clip(shape)
            .background(cardColor)
            .border(
                0.9.dp,
                Color.White.copy(alpha = 0.24f + 0.16f * motionEmphasis),
                shape
            )
            .onSizeChanged { size ->
                cardWidthPx = size.width
            }
            .clickable(
                enabled = (!active && promoteEnabled) || (active && !editing && !inlineTextEditing),
                role = Role.Button,
                onClickLabel = if (active) "Kart ${index + 1} içine yaz" else "Kart ${index + 1} öne gelsin",
                onClick = {
                    if (active) onRequestInlineEdit(index) else onPromote()
                }
            )
    ) {
        val viewportHeight = maxHeight
        val compactWidth = detailComposition && maxWidth < 240.dp
        val shallow = detailComposition && maxHeight < 180.dp
        val minimal = detailComposition && maxHeight < 120.dp
        val inset = if (compactWidth || shallow) 12.dp else 24.dp
        val titleSize = if (compactWidth || shallow) 18.sp else 28.sp
        val titleLine = if (compactWidth || shallow) 23.sp else 32.sp
        val bodyLines = if (detailComposition) ((maxHeight.value - 100f) / 20f).toInt().coerceIn(1, 30) else Int.MAX_VALUE
        // Background Image with editorial gradient overlay tailored for image cards
        if (card.imageUri != null) {
            val imageModel = remember(card.imageUri) {
                val path = card.imageUri
                if (path.startsWith("/")) File(path) else Uri.parse(path)
            }
            AsyncImage(
                model = imageModel,
                contentDescription = "Kart ${index + 1} görseli",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().cardPhotoPixels(photoZoom)
            )
            val hasText = card.title.isNotBlank() || card.body.isNotBlank()
            val gradientColors = if (hasText) {
                listOf(
                    Color.Black.copy(alpha = 0.06f),
                    Color.Black.copy(alpha = 0.22f),
                    Color.Black.copy(alpha = 0.70f)
                )
            } else {
                listOf(
                    Color.Black.copy(alpha = 0.02f),
                    Color.Black.copy(alpha = 0.12f),
                    Color.Black.copy(alpha = 0.35f)
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(gradientColors))
            )
        }

        // Inner Card Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = inset, vertical = if (!detailComposition) 22.dp else if (shallow) 8.dp else 18.dp)
                .onSizeChanged { size ->
                    if (active) {
                        onMeasuredHeight(size.height)
                    }
                },
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row: KART 01 Badge + Action Buttons / Directional Hints
            if (!minimal) Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val badgeBackground = if (hasImage) Color.Black.copy(alpha = 0.36f) else Color.Transparent
                Text(
                    if (compactWidth) "${index + 1}" else "KART 0${index + 1}",
                    maxLines = 1,
                    color = foreground.copy(alpha = 0.90f),
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(badgeBackground)
                        .border(1.dp, foreground.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .padding(horizontal = if (compactWidth) 8.dp else 12.dp, vertical = if (shallow) 3.dp else 7.dp)
                )

                Spacer(Modifier.weight(1f))

                if (editing && active && !compactWidth && !shallow) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Photo Picker Controls (Add / Replace / Remove)
                        if (card.imageUri != null) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(onClick = onPickImage, modifier = Modifier.size(28.dp)) {
                                    ActionIcon(ActionGlyph.Photo, Modifier.size(15.dp), Color.White)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(onClick = onRemoveImage, modifier = Modifier.size(28.dp)) {
                                    ActionIcon(ActionGlyph.Close, Modifier.size(13.dp), Color.White)
                                }
                            }
                        } else {
                            IconButton(onClick = onPickImage, modifier = Modifier.size(28.dp)) {
                                ActionIcon(ActionGlyph.Photo, Modifier.size(15.dp), foreground.copy(alpha = 0.85f))
                            }
                        }

                        // Remove Active Card (if >1 cards)
                        if (totalCards > 1) {
                            IconButton(onClick = onDeleteCard, modifier = Modifier.size(28.dp)) {
                                ActionIcon(ActionGlyph.Delete, Modifier.size(14.dp), foreground.copy(alpha = 0.85f))
                            }
                        }
                    }
                } else {
                    val indicatorText = when {
                        active && totalCards > 1 -> "${index + 1} / $totalCards"
                        isTopPeek -> "↗"
                        isBottomPeek -> "↘"
                        active -> "●"
                        else -> "↗"
                    }
                    Text(
                        indicatorText,
                        color = foreground.copy(alpha = 0.88f),
                        fontSize = if (active && totalCards > 1) 12.sp else 16.sp,
                        fontWeight = if (active && totalCards > 1) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }

            val journalTimestamp = remember(card.id) {
                if (card.id.startsWith("completed_journal_")) {
                    card.id.removePrefix("completed_journal_").toLongOrNull()?.takeIf { it > 0L }
                } else null
            }
            if (journalTimestamp != null && !shallow) {
                Text(
                    text = java.time.Instant.ofEpochMilli(journalTimestamp)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern(
                            "d MMMM yyyy · HH:mm", java.util.Locale.forLanguageTag("tr-TR")
                        )),
                    color = foreground.copy(alpha = 0.60f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            if (!minimal) Spacer(Modifier.height(if (!detailComposition) 18.dp else if (shallow) 4.dp else 12.dp))

            // Body & Title Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                if ((editing || inlineTextEditing) && active) {
                    if (editing && !shallow) {
                        // Optional Title Field in full edit mode.
                        BasicTextField(
                            value = card.title,
                            onValueChange = { newTitle ->
                                onUpdateCard(card.copy(title = newTitle))
                            },
                            textStyle = TextStyle(
                                color = foreground,
                                fontSize = titleSize,
                                lineHeight = titleLine,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(foreground),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                if (card.title.isBlank()) {
                                    Text(
                                        "Kart başlığı",
                                        color = foreground.copy(alpha = 0.40f),
                                        fontSize = titleSize,
                                        lineHeight = titleLine
                                    )
                                }
                                innerTextField()
                            }
                        )
                    } else if (card.title.isNotBlank() && !shallow) {
                        Text(
                            card.title,
                            color = foreground,
                            fontSize = titleSize,
                            lineHeight = titleLine,
                            fontWeight = FontWeight.Normal,
                            maxLines = if (detailComposition) 2 else Int.MAX_VALUE,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    // Content-aware measurement parameters for auto-flow:
                    // Keep a real page boundary: reserve header/title/padding space so the text field never starts internally scrolling.
                    val maxBodyHeight = demoStackCardMaxHeight - demoStackReservedVerticalSpace - demoStackWritingAreaReduction
                    val maxBodyHeightPx = with(density) { maxBodyHeight.toPx() }
                    val effectiveTextWidthPx = remember(cardWidthPx, density) {
                        if (cardWidthPx > 0) {
                            (cardWidthPx - with(density) { 48.dp.toPx() }).toInt().coerceAtLeast(100)
                        } else {
                            with(density) { 320.dp.toPx().toInt() }
                        }
                    }

                    if (!shallow) Spacer(Modifier.height(if (detailComposition) 8.dp else 10.dp))

                    // Inline entry starts writing immediately; the existing parent owns Done/save.
                    LaunchedEffect(card.id, inlineTextEditing, active) {
                        if (inlineTextEditing && active) {
                            bodyTextFieldValue = bodyTextFieldValue.copy(
                                selection = TextRange(bodyTextFieldValue.text.length)
                            )
                            withFrameNanos { }
                            bodyFocusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    }

                    // Dynamic Body Field with Auto-Flow
                    Box(Modifier.fillMaxWidth()) {
                        BasicTextField(
                            value = bodyTextFieldValue,
                            onValueChange = { tfv ->
                                val newText = tfv.text

                                val didAutoFlow = handleBodyAutoFlow(
                                    newBody = newText,
                                    currentCard = card,
                                    safeActiveIndex = index,
                                    cards = cards,
                                    textWidthPx = effectiveTextWidthPx,
                                    maxBodyHeightPx = maxBodyHeightPx,
                                    textMeasurer = textMeasurer,
                                    bodyTextStyle = bodyTextStyle,
                                    density = density,
                                    onUpdate = onRequestNewCardFromAutoFlow
                                )

                                if (!didAutoFlow) {
                                    bodyTextFieldValue = tfv
                                    onUpdateCard(card.copy(body = newText))
                                }
                            },
                            textStyle = bodyTextStyle.copy(color = foreground.copy(alpha = 0.88f)),
                            cursorBrush = SolidColor(foreground),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = if (detailComposition) 40.dp else 48.dp, max = if (detailComposition)
                                    (viewportHeight - (if (minimal) 16.dp else if (shallow) 56.dp else 130.dp)).coerceIn(40.dp, maxBodyHeight)
                                    else maxBodyHeight)
                                .focusRequester(bodyFocusRequester),
                            decorationBox = { innerTextField ->
                                if (bodyTextFieldValue.text.isBlank()) {
                                    Text(
                                        if (hasImage) "Fotoğraf açıklaması ekle…" else "Buraya yaz…",
                                        color = foreground.copy(alpha = 0.40f),
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                } else {
                    // Read-only Title
                    if (card.title.isNotBlank() && !(minimal && hasImage)) {
                        Text(
                            card.title,
                            color = foreground,
                            fontSize = titleSize,
                            lineHeight = titleLine,
                            fontWeight = FontWeight.Normal,
                            maxLines = if (detailComposition) 2 else Int.MAX_VALUE,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (!shallow) Spacer(Modifier.height(if (detailComposition) 8.dp else 10.dp))
                    }

                    // Read-only Body (No maxLines, no ellipsis, no internal scroll)
                    val bodyDisplay = card.body.ifBlank {
                        if (editing || inlineTextEditing) ""
                        else if (hasImage) ""
                        else "Boş kart"
                    }
                    if (bodyDisplay.isNotBlank() && !(shallow && hasImage)) {
                        Text(
                            text = bodyDisplay,
                            color = foreground.copy(alpha = 0.88f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = bodyLines,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Bottom hint on exposed bottom peek
            if (isBottomPeek) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Kart 0${index + 1} ↘",
                        color = foreground.copy(alpha = 0.70f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun handleBodyAutoFlow(
    newBody: String,
    currentCard: CardStackCard,
    safeActiveIndex: Int,
    cards: List<CardStackCard>,
    textWidthPx: Int,
    maxBodyHeightPx: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    bodyTextStyle: TextStyle,
    density: androidx.compose.ui.unit.Density,
    onUpdate: (List<CardStackCard>, Int) -> Unit
): Boolean {
    val measureResult = textMeasurer.measure(
        text = AnnotatedString(newBody),
        style = bodyTextStyle,
        constraints = Constraints(maxWidth = textWidthPx.coerceAtLeast(100))
    )

    if (measureResult.size.height <= maxBodyHeightPx) {
        return false
    }

    val safetyMarginPx = with(density) { 14.dp.toPx() }
    val fittingLine = measureResult.getLineForVerticalPosition(maxBodyHeightPx - safetyMarginPx)
    val rawSplit = measureResult.getLineEnd(fittingLine.coerceAtLeast(0), visibleEnd = true).coerceIn(0, newBody.length)

    var splitIndex = rawSplit
    if (splitIndex < newBody.length && splitIndex > 0) {
        val searchStart = (splitIndex - 40).coerceAtLeast(0)
        val lastWs = newBody.substring(searchStart, splitIndex).lastIndexOfAny(charArrayOf(' ', '\n', '\t'))
        if (lastWs >= 0 && (searchStart + lastWs) > 0) {
            splitIndex = searchStart + lastWs + 1
        }
    }
    if (splitIndex <= 0) splitIndex = rawSplit.coerceAtLeast(1)

    val currentCardText = newBody.substring(0, splitIndex)
    var leftover = newBody.substring(splitIndex)

    val updatedCards = cards.toMutableList()
    updatedCards[safeActiveIndex] = currentCard.copy(body = currentCardText)

    var insertIdx = safeActiveIndex + 1

    // Consume all overflow. There is no arbitrary card/loop cap: every character must survive auto-flow.
    while (leftover.isNotEmpty()) {
        val nextMeasure = textMeasurer.measure(
            text = AnnotatedString(leftover),
            style = bodyTextStyle,
            constraints = Constraints(maxWidth = textWidthPx.coerceAtLeast(100))
        )
        val chunkText: String
        if (nextMeasure.size.height > maxBodyHeightPx) {
            val nextLine = nextMeasure.getLineForVerticalPosition(maxBodyHeightPx - safetyMarginPx)
            var nextSplit = nextMeasure.getLineEnd(nextLine.coerceAtLeast(0), visibleEnd = true).coerceIn(0, leftover.length)
            if (nextSplit < leftover.length && nextSplit > 0) {
                val sStart = (nextSplit - 40).coerceAtLeast(0)
                val lWs = leftover.substring(sStart, nextSplit).lastIndexOfAny(charArrayOf(' ', '\n', '\t'))
                if (lWs >= 0 && (sStart + lWs) > 0) {
                    nextSplit = sStart + lWs + 1
                }
            }
            if (nextSplit <= 0) nextSplit = 1
            chunkText = leftover.substring(0, nextSplit)
            leftover = leftover.substring(nextSplit)
        } else {
            chunkText = leftover
            leftover = ""
        }

        val nextColorArgb = CardStackDefaultColor.toArgb()
        val newCard = CardStackCard(
            title = "",
            body = chunkText,
            colorArgb = nextColorArgb
        )
        updatedCards.add(insertIdx, newCard)
        insertIdx++
    }

    val promotedIndex = (insertIdx - 1).coerceIn(0, updatedCards.size - 1)
    onUpdate(updatedCards, promotedIndex)
    return true
}

/**
 * Normalizes and reflows oversized legacy card content into multiple cards.
 * Returns null if no cards exceed max comfortable body height.
 */
fun reflowOversizedCardsIfNeeded(
    cards: List<CardStackCard>,
    textWidthPx: Int,
    maxBodyHeightPx: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    bodyTextStyle: TextStyle,
    density: androidx.compose.ui.unit.Density
): List<CardStackCard>? {
    var hasOverflow = false
    for (card in cards) {
        if (card.body.isBlank()) continue
        val measure = textMeasurer.measure(
            text = AnnotatedString(card.body),
            style = bodyTextStyle,
            constraints = Constraints(maxWidth = textWidthPx.coerceAtLeast(100))
        )
        if (measure.size.height > maxBodyHeightPx) {
            hasOverflow = true
            break
        }
    }
    if (!hasOverflow) return null

    val safetyMarginPx = with(density) { 14.dp.toPx() }
    val result = mutableListOf<CardStackCard>()

    for (card in cards) {
        if (card.body.isBlank()) {
            result.add(card)
            continue
        }
        val measure = textMeasurer.measure(
            text = AnnotatedString(card.body),
            style = bodyTextStyle,
            constraints = Constraints(maxWidth = textWidthPx.coerceAtLeast(100))
        )
        if (measure.size.height <= maxBodyHeightPx) {
            result.add(card)
            continue
        }

        // Split oversized card content without losing a single character
        var leftover = card.body
        var isFirstChunk = true

        while (leftover.isNotEmpty()) {
            val chunkMeasure = textMeasurer.measure(
                text = AnnotatedString(leftover),
                style = bodyTextStyle,
                constraints = Constraints(maxWidth = textWidthPx.coerceAtLeast(100))
            )
            val chunkText: String
            if (chunkMeasure.size.height > maxBodyHeightPx) {
                val nextLine = chunkMeasure.getLineForVerticalPosition(maxBodyHeightPx - safetyMarginPx)
                var nextSplit = chunkMeasure.getLineEnd(nextLine.coerceAtLeast(0), visibleEnd = true).coerceIn(0, leftover.length)
                if (nextSplit < leftover.length && nextSplit > 0) {
                    val sStart = (nextSplit - 40).coerceAtLeast(0)
                    val lWs = leftover.substring(sStart, nextSplit).lastIndexOfAny(charArrayOf(' ', '\n', '\t'))
                    if (lWs >= 0 && (sStart + lWs) > 0) {
                        nextSplit = sStart + lWs + 1
                    }
                }
                if (nextSplit <= 0) nextSplit = 1
                chunkText = leftover.substring(0, nextSplit)
                leftover = leftover.substring(nextSplit)
            } else {
                chunkText = leftover
                leftover = ""
            }

            if (isFirstChunk) {
                // First card retains original id, title, colorArgb, imageUri
                result.add(card.copy(body = chunkText))
                isFirstChunk = false
            } else {
                val nextColorArgb = CardStackDefaultColor.toArgb()
                result.add(
                    CardStackCard(
                        title = "",
                        body = chunkText,
                        colorArgb = nextColorArgb,
                        imageUri = null
                    )
                )
            }
        }
    }
    return result
}
