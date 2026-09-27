package com.cagri.bigboscagri.ui.components

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.cagri.bigboscagri.data.CardStackBlock
import com.cagri.bigboscagri.data.CardStackCard
import com.cagri.bigboscagri.ui.DesignStyle
import kotlin.math.PI
import kotlin.math.sin

val CardStackPalette = listOf(
    Color(0xFFEE5E37), // salmon orange
    Color(0xFFD9B8F3), // purple
    Color(0xFFDFF37D), // old flax
    Color(0xFF4558C8), // royal blue
    Color(0xFF2E9CA6), // teal
    Color(0xFF3D7C63), // green
    Color(0xFFB88A3A), // warm gold
    Color(0xFFB74769), // rose
    Color(0xFF6B4BB5), // violet
    Color(0xFF292B2D), // jet black
    Color(0xFFEFF0F6), // smoky white
    Color(0xFF366EBC)  // blue
)

private val stackCardHeight = 290.dp
private val stackBackPeek = 52.dp

fun cardColorFor(card: CardStackCard, index: Int): Color {
    return card.colorArgb?.let { Color(it) } ?: CardStackPalette[index % CardStackPalette.size]
}

fun cardForegroundFor(backgroundColor: Color, hasImage: Boolean): Color {
    return if (hasImage || backgroundColor.luminance() < 0.50f) Color.White else Color(0xFF161616)
}

@Composable
fun CardStackContentSection(
    block: CardStackBlock,
    designStyle: DesignStyle,
    editing: Boolean,
    onUpdate: (CardStackBlock) -> Unit,
    onRemove: () -> Unit
) {
    val cards = if (block.cards.isEmpty()) listOf(CardStackCard()) else block.cards
    var activeCardIndex by remember(block.id) { mutableIntStateOf(0) }
    val safeActiveIndex = activeCardIndex.coerceIn(0, (cards.size - 1).coerceAtLeast(0))

    val activeCard = cards[safeActiveIndex]
    val activeBgColor = cardColorFor(activeCard, safeActiveIndex)
    val ambienceColor = remember(activeBgColor) { activeBgColor.copy(alpha = 0.22f) }

    TimelineContent(designStyle, ambienceColor) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionIcon(ActionGlyph.Cards, Modifier.size(16.dp), Color(0xFFA5A5B0))
                Text(
                    text = "Kart Yığını",
                    color = Color(0xFFA5A5B0),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "• ${cards.size} kart",
                    color = Color(0xFF70707A),
                    fontSize = 12.sp
                )
            }

            if (editing) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(36.dp)
                ) {
                    ActionIcon(
                        ActionGlyph.Close,
                        Modifier.size(15.dp).semantics { contentDescription = "Kart yığınını kaldır" },
                        Color(0xFFB8B8C2)
                    )
                }
            }
        }

        // Optional AI / contextual prompt
        if (!block.prompt.isNullOrBlank()) {
            Text(
                text = "“${block.prompt}”",
                color = Color(0xFFBBBBC6),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            )
        }

        // Production Card Stack Deck
        ProductionCardStackDeck(
            cards = cards,
            activeCard = safeActiveIndex,
            editing = editing,
            onPromote = { activeCardIndex = it },
            onUpdateCard = { updatedCard ->
                val newCards = cards.toMutableList()
                newCards[safeActiveIndex] = updatedCard
                onUpdate(block.copy(cards = newCards))
            },
            onDeleteActiveCard = {
                if (cards.size > 1) {
                    val newCards = cards.filterIndexed { idx, _ -> idx != safeActiveIndex }
                    val newActive = if (safeActiveIndex >= newCards.size) newCards.size - 1 else safeActiveIndex
                    activeCardIndex = newActive
                    onUpdate(block.copy(cards = newCards))
                }
            },
            onAddCard = {
                val newCard = CardStackCard(
                    colorArgb = CardStackPalette[cards.size % CardStackPalette.size].toArgb()
                )
                val newCards = cards + newCard
                activeCardIndex = newCards.size - 1
                onUpdate(block.copy(cards = newCards))
            }
        )

        // Bottom Controls in Edit Mode
        if (editing) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card Color Picker Row
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CardStackPalette.forEach { color ->
                        val isSelected = activeCard.colorArgb == color.toArgb() ||
                                (activeCard.colorArgb == null && color == CardStackPalette[safeActiveIndex % CardStackPalette.size])
                        Box(
                            modifier = Modifier
                                .size(32.dp)
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

                // Add Card & Card Selector Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // + Add card button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
                            .clickable(role = Role.Button) {
                                val newCard = CardStackCard(
                                    colorArgb = CardStackPalette[cards.size % CardStackPalette.size].toArgb()
                                )
                                val newCards = cards + newCard
                                activeCardIndex = newCards.size - 1
                                onUpdate(block.copy(cards = newCards))
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ActionIcon(ActionGlyph.Add, Modifier.size(13.dp), Color.White)
                            Text("Kart ekle", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    if (cards.size > 1) {
                        Row(
                            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            cards.forEachIndexed { idx, _ ->
                                val selected = idx == safeActiveIndex
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (selected) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.05f))
                                        .border(
                                            0.8.dp,
                                            if (selected) Color.White.copy(alpha = 0.40f) else Color.Transparent,
                                            RoundedCornerShape(16.dp)
                                        )
                                        .clickable { activeCardIndex = idx }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        color = if (selected) Color.White else Color(0xFF90909A),
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (cards.size > 1) {
            // Read-only card indicators
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                cards.forEachIndexed { idx, _ ->
                    val isCurrent = idx == safeActiveIndex
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isCurrent) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (isCurrent) Color.White else Color.White.copy(alpha = 0.25f))
                            .clickable { activeCardIndex = idx }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductionCardStackDeck(
    cards: List<CardStackCard>,
    activeCard: Int,
    editing: Boolean,
    onPromote: (Int) -> Unit,
    onUpdateCard: (CardStackCard) -> Unit,
    onDeleteActiveCard: () -> Unit,
    onAddCard: () -> Unit
) {
    val isSingleCard = cards.size <= 1
    val backCardIndex = if (isSingleCard) 0 else (activeCard + 1) % cards.size

    val containerHeight by animateDpAsState(
        targetValue = stackCardHeight + stackBackPeek,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 380f),
        label = "Deck container height"
    )

    // Smooth physical depth-exchange adapted from DemoStackCards
    var previousActiveCard by remember { mutableIntStateOf(activeCard) }
    var swapFromCard by remember { mutableIntStateOf(activeCard) }
    val swapProgress = remember { Animatable(1f) }

    LaunchedEffect(activeCard) {
        if (activeCard != previousActiveCard) {
            swapFromCard = previousActiveCard
            previousActiveCard = activeCard
            swapProgress.snapTo(0f)
            swapProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 390, easing = FastOutSlowInEasing)
            )
        }
    }

    val progress = swapProgress.value.coerceIn(0f, 1f)
    val arc = sin(progress * PI).toFloat()
    val isSwapping = progress < 0.999f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(containerHeight)
    ) {
        if (isSingleCard) {
            // Render decorative non-persistent backing card so the stack concept is visually recognizable
            val frontCard = cards.firstOrNull() ?: CardStackCard()
            val frontColor = cardColorFor(frontCard, 0)
            val decorativeBackShape = RoundedCornerShape(26.dp)

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .align(Alignment.TopCenter)
                    .height(stackCardHeight)
                    .offset { IntOffset(0, stackBackPeek.roundToPx()) }
                    .zIndex(1f)
                    .clip(decorativeBackShape)
                    .background(frontColor.copy(alpha = 0.28f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.18f), decorativeBackShape)
                    .clickable(
                        enabled = editing,
                        onClick = onAddCard
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (editing) {
                    Text(
                        "+ Kart ekle",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Single front card
            SingleStackCard(
                card = frontCard,
                index = 0,
                totalCards = 1,
                active = true,
                editing = editing,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .align(Alignment.TopCenter)
                    .height(stackCardHeight)
                    .offset { IntOffset(0, 0) }
                    .zIndex(2f)
                    .graphicsLayer { shadowElevation = 14.dp.toPx() },
                onPromote = {},
                onUpdateCard = onUpdateCard,
                onDeleteCard = onDeleteActiveCard
            )
        } else {
            // Two or more real cards: pair active card and back card
            val indicesToRender = listOf(backCardIndex, activeCard).distinct()

            indicesToRender.forEach { index ->
                key(index) {
                    val isActive = index == activeCard
                    val isIncoming = isSwapping && index == activeCard
                    val isOutgoing = isSwapping && index == swapFromCard

                    val targetTop = if (isActive) 0.dp else stackBackPeek
                    val top by animateDpAsState(
                        targetValue = targetTop,
                        animationSpec = spring(dampingRatio = 0.84f, stiffness = 360f),
                        label = "Card Y $index"
                    )

                    val incomingDirection = if (activeCard % 2 == 0) 1f else -1f
                    val motionY = when {
                        isIncoming -> 20.dp * incomingDirection * arc
                        isOutgoing -> -8.dp * incomingDirection * arc
                        else -> 0.dp
                    }
                    val animatedScale = when {
                        isIncoming -> 1f + (0.028f * arc)
                        isOutgoing -> 1f - (0.015f * arc)
                        else -> 1f
                    }
                    val shadowDp = when {
                        isIncoming -> 4f + (10f * progress) + (10f * arc)
                        isOutgoing -> (14f - (10f * progress) - (4f * arc)).coerceAtLeast(3f)
                        isActive -> 14f
                        else -> 4f
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
                        index = index,
                        totalCards = cards.size,
                        active = isActive,
                        editing = editing,
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .align(Alignment.TopCenter)
                            .height(stackCardHeight)
                            .offset { IntOffset(0, top.roundToPx()) }
                            .zIndex(cardZ)
                            .graphicsLayer {
                                translationY = motionY.toPx()
                                scaleX = animatedScale
                                scaleY = animatedScale
                                shadowElevation = shadowDp.dp.toPx()
                            },
                        onPromote = { if (!isActive && !isSwapping) onPromote(index) },
                        onUpdateCard = onUpdateCard,
                        onDeleteCard = onDeleteActiveCard
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
    modifier: Modifier,
    onPromote: () -> Unit,
    onUpdateCard: (CardStackCard) -> Unit,
    onDeleteCard: () -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    val cardColor = cardColorFor(card, index)
    val foreground = cardForegroundFor(cardColor, card.imageUri != null)

    Box(
        modifier = modifier
            .clip(shape)
            .background(cardColor)
            .border(0.9.dp, Color.White.copy(alpha = if (active) 0.30f else 0.15f), shape)
            .clickable(
                enabled = !active,
                role = Role.Button,
                onClickLabel = "Kart ${index + 1} öne gelsin",
                onClick = onPromote
            )
    ) {
        // Optional Background Image
        if (card.imageUri != null) {
            AsyncImage(
                model = card.imageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.12f),
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Subtle index indicator
                Text(
                    text = "${index + 1} / $totalCards",
                    color = foreground.copy(alpha = 0.80f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(foreground.copy(alpha = 0.12f))
                        .border(0.8.dp, foreground.copy(alpha = 0.25f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )

                Spacer(Modifier.weight(1f))

                if (editing && active && totalCards > 1) {
                    IconButton(
                        onClick = onDeleteCard,
                        modifier = Modifier.size(28.dp)
                    ) {
                        ActionIcon(ActionGlyph.Close, Modifier.size(13.dp), foreground.copy(alpha = 0.85f))
                    }
                } else if (!active) {
                    Text(
                        text = "↗",
                        color = foreground.copy(alpha = 0.85f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Card Body & Title
            if (editing && active) {
                // Live in-place editable inputs
                Column(
                    modifier = Modifier.weight(1f).padding(top = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Body text input
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (card.body.isEmpty()) {
                            Text(
                                text = "Buraya yaz…",
                                color = foreground.copy(alpha = 0.45f),
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                        }
                        BasicTextField(
                            value = card.body,
                            onValueChange = { onUpdateCard(card.copy(body = it.take(4000))) },
                            modifier = Modifier.fillMaxSize(),
                            textStyle = TextStyle(
                                color = foreground,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(foreground)
                        )
                    }

                    // Title input
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        if (card.title.isEmpty()) {
                            Text(
                                text = "Başlık (isteğe bağlı)",
                                color = foreground.copy(alpha = 0.40f),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        BasicTextField(
                            value = card.title,
                            onValueChange = { onUpdateCard(card.copy(title = it.take(150))) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = TextStyle(
                                color = foreground,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            cursorBrush = SolidColor(foreground)
                        )
                    }
                }
            } else {
                // Read-only card view
                Column(
                    modifier = Modifier.weight(1f).padding(top = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (card.body.isNotBlank()) card.body else if (card.title.isBlank()) "Boş kart" else "",
                        color = foreground.copy(alpha = if (card.body.isNotBlank()) 0.85f else 0.45f),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (card.title.isNotBlank()) {
                        Text(
                            text = card.title,
                            color = foreground,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
