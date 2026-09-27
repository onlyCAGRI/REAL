package com.cagri.bigboscagri.demo

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage

private data class EditableDemoCard(
    val title: String,
    val body: String,
    val color: Color,
    val imageUri: String? = null
)

private val demoCardColors = listOf(
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

private val demoStackCardHeight = 390.dp
private val demoBackPeekNoImage = 72.dp
private val demoBackPeekWithImage = 180.dp
private const val demoStackCardWidthFraction = 0.76f

/**
 * Local-only design experiment. Nothing here is written to REAL's reminder/category data.
 * Two editable cards exchange front/back positions when the exposed back edge is tapped.
 */
@Composable
internal fun DemoStackCards(modifier: Modifier = Modifier) {
    val cards = remember {
        mutableStateListOf(
            EditableDemoCard(
                title = "api integration",
                body = "Any kind of data API can be integrated into REAL.",
                color = Color(0xFFEE5E37)
            ),
            EditableDemoCard(
                title = "data sources",
                body = "Bir görsel, renk veya kısa fikir kartın arkasında kalabilir.",
                color = Color(0xFFD9B8F3)
            )
        )
    }
    var activeCard by remember { mutableIntStateOf(0) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            cards[activeCard] = cards[activeCard].copy(imageUri = uri.toString())
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "İki kart, tek deste.",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Arkada görünen kenara dokun; kart öne gelir, diğeri arkaya geçer.",
            color = Color(0xFFAAAAB1),
            fontSize = 12.sp
        )

        DemoTwoCardDeck(
            cards = cards,
            activeCard = activeCard,
            onPromote = { activeCard = it }
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DemoCardSelector(
                text = "Kart 1",
                selected = activeCard == 0,
                modifier = Modifier.weight(1f)
            ) { activeCard = 0 }
            DemoCardSelector(
                text = "Kart 2",
                selected = activeCard == 1,
                modifier = Modifier.weight(1f)
            ) { activeCard = 1 }
        }

        Text("Kart rengi", color = Color(0xFFB8B8C0), fontSize = 12.sp)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            demoCardColors.forEach { color ->
                val selected = cards[activeCard].color == color
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.23f),
                            shape = CircleShape
                        )
                        .clickable(role = Role.Button) {
                            cards[activeCard] = cards[activeCard].copy(color = color)
                        }
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (cards[activeCard].imageUri == null) "Resim ekle" else "Resmi değiştir")
            }
            OutlinedButton(
                onClick = {
                    cards[activeCard] = cards[activeCard].copy(imageUri = null)
                },
                enabled = cards[activeCard].imageUri != null,
                modifier = Modifier.weight(1f)
            ) {
                Text("Resmi kaldır")
            }
        }

        OutlinedTextField(
            value = cards[activeCard].title,
            onValueChange = { cards[activeCard] = cards[activeCard].copy(title = it) },
            label = { Text("Kart başlığı") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        )
        OutlinedTextField(
            value = cards[activeCard].body,
            onValueChange = { cards[activeCard] = cards[activeCard].copy(body = it) },
            label = { Text("Kart metni") },
            minLines = 3,
            maxLines = 7,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        )

        Text(
            "Yerel tasarım demosu · resim, metin ve renk seçimleri gerçek kartlara kaydedilmez.",
            color = Color(0xFF888890),
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DemoTwoCardDeck(
    cards: List<EditableDemoCard>,
    activeCard: Int,
    onPromote: (Int) -> Unit
) {
    val backCardIndex = if (activeCard == 0) 1 else 0
    val targetBackPeek = if (cards[backCardIndex].imageUri != null) {
        demoBackPeekWithImage
    } else {
        demoBackPeekNoImage
    }
    val containerHeight by animateDpAsState(
        targetValue = demoStackCardHeight + targetBackPeek,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 380f),
        label = "Demo deck height"
    )

    // activeCard changes immediately so the editor controls stay responsive, but the
    // cards get their own short depth-exchange animation. This avoids the old behavior
    // where the final Y positions were identical and only zIndex visibly flipped.
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
    val arc = sin(progress * PI).toFloat() // 0 → 1 → 0, used for the physical "lift".
    val isSwapping = progress < 0.999f

    Box(
        Modifier
            .fillMaxWidth()
            .height(containerHeight)
    ) {
        cards.forEachIndexed { index, card ->
            key(index) {
                val isActive = index == activeCard
                val isIncoming = isSwapping && index == activeCard
                val isOutgoing = isSwapping && index == swapFromCard

                val targetTop = when {
                    // Card 1 is front: card 2 peeks from below.
                    activeCard == 0 && isActive -> 0.dp
                    activeCard == 0 -> targetBackPeek
                    // Card 2 is front: card 1 peeks from above.
                    activeCard == 1 && isActive -> targetBackPeek
                    else -> 0.dp
                }
                val top by animateDpAsState(
                    targetValue = targetTop,
                    animationSpec = spring(dampingRatio = 0.84f, stiffness = 360f),
                    label = "Demo card Y $index"
                )

                // During the exchange the tapped back card travels slightly toward the
                // centre of the deck, rises toward the viewer, then settles into its new
                // layer. The outgoing card simultaneously sinks away. Final X is always 0.
                val incomingDirection = if (activeCard == 0) 1f else -1f
                val motionY = when {
                    // The incoming card first travels toward the centre while it is still
                    // physically behind the old front card, then crosses the z-plane.
                    isIncoming -> 24.dp * incomingDirection * arc
                    // The outgoing card gives way in the opposite direction instead of
                    // disappearing underneath in a single frame.
                    isOutgoing -> -9.dp * incomingDirection * arc
                    else -> 0.dp
                }
                // Both cards finish at exactly the same width/scale. Scale exists only
                // during the exchange to sell the depth change without breaking alignment.
                val animatedScale = when {
                    isIncoming -> 1f + (0.030f * arc)
                    isOutgoing -> 1f - (0.016f * arc)
                    else -> 1f
                }
                val shadowDp = when {
                    // Start at the old back-card elevation, rise above the old front,
                    // then settle at the normal front-card elevation.
                    isIncoming -> 5f + (11f * progress) + (12f * arc)
                    isOutgoing -> (16f - (11f * progress) - (4f * arc)).coerceAtLeast(3f)
                    isActive -> 16f
                    else -> 5f
                }
                val cardZ = when {
                    // Delay the z-order crossover very slightly. The incoming card moves
                    // first, then visibly passes in front instead of teleporting layers.
                    isIncoming && progress < 0.22f -> 1f
                    isIncoming -> 4f
                    isOutgoing -> 3f
                    isActive -> 2f
                    else -> 1f
                }

                EditableStackCard(
                    card = card,
                    index = index,
                    active = isActive,
                    motionEmphasis = if (isIncoming) arc else 0f,
                    promoteEnabled = !isSwapping,
                    modifier = Modifier
                        .fillMaxWidth(demoStackCardWidthFraction)
                        .align(Alignment.TopCenter)
                        .height(demoStackCardHeight)
                        .offset { IntOffset(0, top.roundToPx()) }
                        .zIndex(cardZ)
                        .graphicsLayer {
                            // Static cards remain perfectly aligned. Only Y/depth/scale
                            // participate in the swap, so the deck never drifts sideways.
                            translationY = motionY.toPx()
                            scaleX = animatedScale
                            scaleY = animatedScale
                            shadowElevation = shadowDp.dp.toPx()
                        },
                    onPromote = { if (!isActive) onPromote(index) }
                )
            }
        }
    }
}

@Composable
private fun EditableStackCard(
    card: EditableDemoCard,
    index: Int,
    active: Boolean,
    motionEmphasis: Float = 0f,
    promoteEnabled: Boolean = true,
    modifier: Modifier,
    onPromote: () -> Unit
) {
    val shape = RoundedCornerShape(30.dp)
    val imageMode = card.imageUri != null
    val foreground = if (imageMode || card.color.luminance() < 0.48f) Color.White else Color(0xFF161616)

    Box(
        modifier
            .clip(shape)
            .background(card.color)
            .border(
                0.9.dp,
                Color.White.copy(alpha = 0.24f + 0.16f * motionEmphasis),
                shape
            )
            .clickable(
                enabled = !active && promoteEnabled,
                role = Role.Button,
                onClickLabel = "Kart ${index + 1} öne gelsin",
                onClick = onPromote
            )
    ) {
        if (card.imageUri != null) {
            AsyncImage(
                model = card.imageUri,
                contentDescription = "Kart ${index + 1} görseli",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.08f),
                                Color.Black.copy(alpha = 0.20f),
                                Color.Black.copy(alpha = 0.64f)
                            )
                        )
                    )
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "KART 0${index + 1}",
                    color = foreground.copy(alpha = 0.83f),
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, foreground.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (active) "●" else "↗",
                    color = foreground.copy(alpha = 0.9f),
                    fontSize = 18.sp
                )
            }

            Column {
                Text(
                    card.body,
                    color = foreground.copy(alpha = 0.76f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    card.title.ifBlank { "Kart başlığı" },
                    color = foreground,
                    fontSize = 32.sp,
                    lineHeight = 33.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DemoCardSelector(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color(0xFF343439) else Color(0xFF171719))
            .border(
                0.8.dp,
                if (selected) Color.White.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(50)
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Color.White else Color(0xFFA5A5AD),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
