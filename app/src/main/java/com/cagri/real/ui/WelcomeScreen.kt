package com.cagri.real.ui

import android.annotation.SuppressLint

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.cagri.real.R
import com.cagri.real.auth.AuthCancelled
import com.cagri.real.auth.AuthUser
import com.cagri.real.ui.auth.EmailAuthDialog
import com.cagri.real.ui.auth.LocalAuthUiController
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import com.cagri.real.ui.components.RealWordmark
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
    var showEmailAuth by remember { mutableStateOf(false) }
    var showDataUse by remember { mutableStateOf(false) }
    var authBusy by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    val authController = LocalAuthUiController.current
    val authScope = rememberCoroutineScope()

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
    val shimmer = remember { Animatable(-1f) }
    val uriHandler = LocalUriHandler.current

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
            
            delay(280)

            
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

            // Logo açıldıktan sonra üzerinden tek seferlik ışık süzülür.
            launch {
                shimmer.snapTo(0f)
                shimmer.animateTo(1f, animationSpec = tween(1300, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)))
                shimmer.snapTo(-1f)
            }
            delay(220)

            
            phase = WelcomePhase.NamePrompt
            launch {
                groupShiftProgress.animateTo(
                    1f,
                    animationSpec = tween(420, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f))
                )
            }

            
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

            delay(60) 
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

    val finishAfterAuthentication: (AuthUser) -> Unit = { user ->
        val resolvedName = name.trim().ifBlank {
            user.displayName
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.substringBefore(' ')
                .orEmpty()
        }
        if (resolvedName.isBlank()) {
            authError = "Hesap bağlandı. Devam etmek için size nasıl hitap edeceğimizi yazın."
        } else if (phase == WelcomePhase.NamePrompt) {
            name = resolvedName
            authError = null
            keyboardController?.hide()
            focusManager.clearFocus()
            phase = WelcomePhase.Greeting
        }
    }

    
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val totalHeight = maxHeight
        val density = LocalDensity.current
        val headerHeight = 129.dp 
        val initialHeaderTop = (totalHeight - headerHeight) / 2
        val baseShiftDp = 54.dp
        // Welcome grubunu (Welcome to + logo + prompt/auth) mevcut konumuna göre yukarı taşıyan ek kaydırma.
        val welcomeGroupLiftDp = 24.dp
        val finalShiftDp = baseShiftDp + welcomeGroupLiftDp
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

        
        WelcomeAmbient(
            intensity = if (reduceAnimations) 1f else revealProgress.value,
            animate = !reduceAnimations,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = -animatedImeShiftPx
                }
        ) {
            
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
                    shimmer = shimmer.value,
                    modifier = Modifier
                        .size(width = 210.dp, height = 91.dp)
                        .graphicsLayer {
                            translationY = with(density) { logoOffsetY.value.dp.toPx() }
                        }
                )
            }

            
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

                    Spacer(Modifier.height(14.dp))
                    WelcomeAuthButton(
                        label = if (authBusy) "Google açılıyor…" else "Google ile devam et",
                        emphasized = true,
                        enabled = !authBusy && authController != null,
                        modifier = Modifier.graphicsLayer { alpha = inputAlpha.value }
                    ) {
                        val active = authController ?: return@WelcomeAuthButton
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        authBusy = true
                        authError = null
                        authScope.launch {
                            val result = active.signInWithGoogle()
                            authBusy = false
                            result.onSuccess(finishAfterAuthentication)
                                .onFailure { throwable ->
                                    if (throwable !is AuthCancelled) {
                                        authError = throwable.message ?: "Google ile giriş yapılamadı."
                                    }
                                }
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    WelcomeAuthButton(
                        label = "E-posta ile devam et",
                        emphasized = false,
                        enabled = !authBusy && authController != null,
                        modifier = Modifier.graphicsLayer { alpha = inputAlpha.value }
                    ) {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        authError = null
                        showEmailAuth = true
                    }
                    authError?.let { message ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = message,
                            color = Color(0xFFFF9A9A),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(300.dp)
                        )
                    }

                    val privacyUrl = com.cagri.real.ui.privacy.privacyDocumentUrl("/privacy")
                    val kvkkUrl = com.cagri.real.ui.privacy.privacyDocumentUrl("/kvkk")
                    Spacer(Modifier.height(18.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(300.dp).graphicsLayer { alpha = inputAlpha.value }
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.cagri.real.R.string.real_privacy_welcome),
                            color = Color.White.copy(alpha = .5f),
                            fontSize = 12.sp, lineHeight = 16.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            WelcomeLink(androidx.compose.ui.res.stringResource(com.cagri.real.R.string.real_privacy_policy)) { runCatching { uriHandler.openUri(privacyUrl) } }
                            WelcomeLink(androidx.compose.ui.res.stringResource(com.cagri.real.R.string.real_privacy_kvkk)) { runCatching { uriHandler.openUri(kvkkUrl) } }
                            WelcomeLink(androidx.compose.ui.res.stringResource(com.cagri.real.R.string.real_privacy_title)) { keyboardController?.hide(); focusManager.clearFocus(); showDataUse = true }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = com.cagri.real.ui.privacy.RealDataUseSummary,
                            color = Color.White.copy(alpha = .38f),
                            fontSize = 11.sp, lineHeight = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    if (showDataUse) {
        com.cagri.real.ui.privacy.RealDataUseDialog { showDataUse = false }
    }

    if (showEmailAuth) {
        EmailAuthDialog(
            onDismiss = { showEmailAuth = false },
            onAuthenticated = { user ->
                showEmailAuth = false
                finishAfterAuthentication(user)
            }
        )
    }
}







@Composable
private fun WelcomeAuthButton(
    label: String,
    emphasized: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(if (pressed) 110 else 220),
        label = "welcomeAuthPress"
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .width(300.dp)
            .height(50.dp)
            .clip(shape)
            .background(
                when {
                    !enabled -> Color.White.copy(alpha = 0.05f)
                    emphasized -> Color.White
                    else -> Color(0xFF141416)
                },
                shape
            )
            .border(
                1.dp,
                if (emphasized && enabled) Color.Transparent else Color.White.copy(alpha = if (enabled) 0.12f else 0.06f),
                shape
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> Color(0xFF66666E)
                emphasized -> Color.Black
                else -> Color.White
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun WelcomeWordmark(
    revealProgress: Float,
    modifier: Modifier = Modifier,
    shimmer: Float = -1f
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
                if (revealProgress < 1f || shimmer in 0f..1f) {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
            }
            .drawWithContent {
                if (revealProgress <= 0f) return@drawWithContent
                drawContent()

                if (shimmer in 0f..1f) {
                    val band = size.width * 0.22f
                    val cx = -band + shimmer * (size.width + band * 2f)
                    drawRect(
                        brush = Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                            start = Offset(cx - band, 0f),
                            end = Offset(cx + band, size.height * 0.35f)
                        ),
                        blendMode = BlendMode.SrcAtop
                    )
                }

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

/** Yavaş hareket eden iki soluk ışık; siyah zemine derinlik verir. */
@Composable
private fun WelcomeAmbient(intensity: Float, animate: Boolean, modifier: Modifier = Modifier) {
    val t = if (animate) {
        rememberInfiniteTransition(label = "welcomeAmbient").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse),
            label = "welcomeAmbientT"
        ).value
    } else 0.5f
    Canvas(modifier) {
        val a = intensity.coerceIn(0f, 1f)
        val w = size.width
        val h = size.height
        drawRect(
            Brush.radialGradient(
                listOf(Color(0xFF9FB4FF).copy(alpha = 0.16f * a), Color.Transparent),
                center = Offset(w * (0.25f + 0.35f * t), h * (0.22f + 0.06f * t)),
                radius = w * 0.85f
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(Color(0xFFC9A6FF).copy(alpha = 0.12f * a), Color.Transparent),
                center = Offset(w * (0.8f - 0.35f * t), h * (0.78f - 0.06f * t)),
                radius = w * 0.9f
            )
        )
    }
}

@Composable
private fun WelcomeLink(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color.White.copy(alpha = .78f),
        fontSize = 12.sp,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick
            )
            .padding(vertical = 6.dp)
    )
}
