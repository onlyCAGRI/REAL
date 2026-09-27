package com.cagri.bigboscagri.ui

import android.annotation.SuppressLint

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.bigboscagri.R
import com.cagri.bigboscagri.ui.components.ActionGlyph
import com.cagri.bigboscagri.ui.components.ActionIcon
import com.cagri.bigboscagri.ui.components.RealWordmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class WelcomePhase {
    Intro,
    NamePrompt,
    Greeting
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun WelcomeScreen(
    reduceAnimations: Boolean = false,
    onComplete: (String) -> Unit
) {
    var phase by remember { mutableStateOf(WelcomePhase.Intro) }
    var name by remember { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }

    val welcomeAlpha = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    val welcomeOffsetY = remember { Animatable(if (reduceAnimations) 0f else 8f) }
    val welcomeLetterSpacing = remember { Animatable(if (reduceAnimations) 0.5f else 1.5f) }
    val logoOffsetY = remember { Animatable(if (reduceAnimations) 0f else 10f) }
    val revealProgress = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    val groupShiftProgress = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    val questionAlpha = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    val questionOffsetY = remember { Animatable(if (reduceAnimations) 0f else 12f) }
    val inputAlpha = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    val inputOffsetY = remember { Animatable(if (reduceAnimations) 0f else 12f) }
    val greetingAlpha = remember { Animatable(0f) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (reduceAnimations) {
            welcomeAlpha.snapTo(1f)
            welcomeOffsetY.snapTo(0f)
            welcomeLetterSpacing.snapTo(0.5f)
            logoOffsetY.snapTo(0f)
            revealProgress.snapTo(1f)
            groupShiftProgress.snapTo(1f)
            questionAlpha.snapTo(1f)
            questionOffsetY.snapTo(0f)
            inputAlpha.snapTo(1f)
            inputOffsetY.snapTo(0f)
            phase = WelcomePhase.NamePrompt
        } else {
            // 1. Initial pure AMOLED black hold (~280ms)
            delay(280)

            // 2. "Welcome to" entrance: subtle 8dp offset & calm 280ms fade
            launch {
                welcomeOffsetY.animateTo(
                    0f,
                    animationSpec = tween(280, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f))
                )
            }
            launch {
                welcomeLetterSpacing.animateTo(
                    0.5f,
                    animationSpec = tween(280, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f))
                )
            }
            welcomeAlpha.animateTo(
                1f,
                animationSpec = tween(280, easing = LinearOutSlowInEasing)
            )

            // 3. REAL logo – soft feathered mask reveal left-to-right (500ms, subtle 10dp settle)
            launch {
                logoOffsetY.animateTo(
                    0f,
                    animationSpec = tween(500, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f))
                )
            }
            revealProgress.animateTo(
                1f,
                animationSpec = tween(500, easing = CubicBezierEasing(0.2f, 0.0f, 0.2f, 1.0f))
            )

            // 4. Settled calm hold before header shift
            delay(220)

            // 5. Header visual group shifts up by ~54dp as ONE unified unit
            phase = WelcomePhase.NamePrompt
            launch {
                groupShiftProgress.animateTo(
                    1f,
                    animationSpec = tween(420, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f))
                )
            }

            // 6. Question & Name Input emerge smoothly in the scene
            delay(70)
            launch {
                questionOffsetY.animateTo(
                    0f,
                    animationSpec = tween(300, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f))
                )
            }
            launch {
                questionAlpha.animateTo(
                    1f,
                    animationSpec = tween(300, easing = LinearOutSlowInEasing)
                )
            }

            delay(60) // micro-stagger
            launch {
                inputOffsetY.animateTo(
                    0f,
                    animationSpec = tween(300, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f))
                )
            }
            inputAlpha.animateTo(
                1f,
                animationSpec = tween(300, easing = LinearOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(phase) {
        if (phase == WelcomePhase.Greeting) {
            greetingAlpha.animateTo(
                1f,
                animationSpec = if (reduceAnimations) snap() else tween(300, easing = LinearOutSlowInEasing)
            )
            delay(800)
            onComplete(name.trim())
        }
    }

    val onConfirm: () -> Unit = {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty() && phase == WelcomePhase.NamePrompt) {
            keyboardController?.hide()
            focusManager.clearFocus()
            phase = WelcomePhase.Greeting
        }
    }

    // Root container has NO imePadding: layout remains rock-solid when keyboard opens!
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val totalHeight = maxHeight
        val density = LocalDensity.current
        val headerHeight = 129.dp // 24dp ("Welcome to") + 14dp (spacer) + 91dp (wordmark)
        val initialHeaderTop = (totalHeight - headerHeight) / 2
        val finalShiftDp = 54.dp
        val contentGapDp = 52.dp

        val initialHeaderTopPx = with(density) { initialHeaderTop.toPx() }
        val finalShiftPx = with(density) { finalShiftDp.toPx() }
        val promptTopPx = with(density) { (initialHeaderTop - finalShiftDp + headerHeight + contentGapDp).toPx() }

        var promptMeasuredHeightPx by remember { mutableIntStateOf(0) }
        val imeBottomPx = WindowInsets.ime.getBottom(density).toFloat()
        val totalHeightPx = with(density) { totalHeight.toPx() }
        val unshiftedSceneBottomPx = promptTopPx + promptMeasuredHeightPx
        val keyboardTopPx = totalHeightPx - imeBottomPx
        val safetyMarginPx = with(density) { 16.dp.toPx() }
        val requiredShiftPx = if (promptMeasuredHeightPx > 0 && imeBottomPx > 0f) {
            (unshiftedSceneBottomPx + safetyMarginPx - keyboardTopPx).coerceAtLeast(0f)
        } else {
            0f
        }
        val headerTopPx = initialHeaderTopPx - finalShiftPx
        val minTopMarginPx = with(density) { 12.dp.toPx() }
        val maxAllowedShiftPx = (headerTopPx - minTopMarginPx).coerceAtLeast(0f)
        val targetImeShiftPx = requiredShiftPx.coerceAtMost(maxAllowedShiftPx)
        val animatedImeShiftPx by animateFloatAsState(
            targetValue = targetImeShiftPx,
            animationSpec = tween(240, easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)),
            label = "welcomeImeShift"
        )

        // Single visual parent container: All 4 elements move together as ONE unit when keyboard opens
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = -animatedImeShiftPx
                }
        ) {
            // 1. Header Visual Group (Welcome to + REAL): moves together as ONE unit during entrance
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .graphicsLayer {
                        translationY = initialHeaderTopPx - finalShiftPx * groupShiftProgress.value
                    }
            ) {
                Text(
                    text = "Welcome to",
                    color = Color(0xFFA4A4AD),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = welcomeLetterSpacing.value.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer {
                        alpha = welcomeAlpha.value
                        translationY = with(density) { welcomeOffsetY.value.dp.toPx() }
                    }
                )

                Spacer(Modifier.height(14.dp))

                WelcomeWordmark(
                    revealProgress = revealProgress.value,
                    modifier = Modifier
                        .size(width = 210.dp, height = 91.dp)
                        .graphicsLayer {
                            translationY = with(density) { logoOffsetY.value.dp.toPx() }
                        }
                )
            }

            // 2. Below Header: Question + Input OR Greeting (single continuous scene)
            if (phase == WelcomePhase.Greeting) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                        .graphicsLayer {
                            translationY = promptTopPx
                            alpha = greetingAlpha.value
                        }
                ) {
                    Text(
                        text = "Tanıştığımıza memnun oldum,\n${name.trim()}.",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp,
                        lineHeight = 30.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer {
                            translationY = if (reduceAnimations) 0f else (1f - greetingAlpha.value) * 12f
                        }
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                        .onGloballyPositioned { coords ->
                            promptMeasuredHeightPx = coords.size.height
                        }
                        .graphicsLayer {
                            translationY = promptTopPx
                        }
                ) {
                    Text(
                        text = "Size nasıl hitap edelim?",
                        color = Color(0xFFF7F7F8),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer {
                            alpha = questionAlpha.value
                            translationY = with(density) { questionOffsetY.value.dp.toPx() }
                        }
                    )

                    Spacer(Modifier.height(20.dp))

                    // Clean, minimal pill input (NO floating label) - entire container animated together
                    Row(
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = inputAlpha.value
                                translationY = with(density) { inputOffsetY.value.dp.toPx() }
                            }
                            .width(300.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color(0xFF141416))
                            .border(
                                width = 1.dp,
                                color = if (isFocused) Color.White.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(28.dp)
                            )
                            .padding(start = 22.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (name.isEmpty()) {
                                Text(
                                    text = "Adınız",
                                    color = Color(0xFF5A5A62),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                            BasicTextField(
                                value = name,
                                onValueChange = { name = it },
                                singleLine = true,
                                maxLines = 1,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(Color.White),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { isFocused = it.isFocused }
                            )
                        }

                        val isActionEnabled = name.trim().isNotEmpty()
                        val actionInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isActionEnabled) Color.White else Color.White.copy(alpha = 0.06f))
                            .clickable(
                                enabled = isActionEnabled,
                                interactionSource = actionInteraction,
                                indication = null,
                                onClick = onConfirm
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            ActionIcon(
                                glyph = ActionGlyph.Check,
                                modifier = Modifier.size(18.dp),
                                color = if (isActionEnabled) Color.Black else Color(0xFF4A4A52)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Wordmark utilizing transparent PNG asset.
 * Natural alpha channel acts as mask.
 * 1) Mask reveal: Letters emerge progressively from left to right with soft feather.
 * 2) Settles cleanly into calm, pure white logo (no specular streak, no zoom, no glow).
 */
@Composable
private fun WelcomeWordmark(
    revealProgress: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetWidthPx = with(density) { 210.dp.roundToPx() }

    val logoBitmap by produceState<ImageBitmap?>(null, targetWidthPx) {
        value = withContext(Dispatchers.IO) {
            try {
                val res = context.resources
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeResource(res, R.drawable.real_wordmark, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= targetWidthPx) sample *= 2
                BitmapFactory.decodeResource(
                    res, R.drawable.real_wordmark,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                )?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                if (revealProgress < 1f) {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
            }
            .drawWithContent {
                if (revealProgress <= 0f) return@drawWithContent
                drawContent()

                // Soft feathered mask reveal from left to right (no harsh wipe)
                if (revealProgress < 1f) {
                    val featherWidth = size.width * 0.18f
                    val head = revealProgress * (size.width + featherWidth)
                    val tail = head - featherWidth
                    val maskBrush = Brush.linearGradient(
                        listOf(Color.White, Color.Transparent),
                        start = Offset(tail, 0f),
                        end = Offset(head, 0f)
                    )
                    drawRect(
                        brush = maskBrush,
                        blendMode = BlendMode.DstIn
                    )
                }
            }
    ) {
        logoBitmap?.let {
            Image(
                bitmap = it,
                contentDescription = "Real",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } ?: RealWordmark(Modifier.fillMaxSize())
    }
}
