package com.cagri.real.ui

import com.cagri.real.ui.components.RealMotion
import com.cagri.real.ui.components.RealHaptics
import androidx.compose.animation.core.FastOutSlowInEasing
import com.cagri.real.ui.components.pressScale
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.boundsInRoot
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
    // First launch (REAL_Welcome design): the word story plays, then Welcome to + REAL, then the
    // name prompt and sign-in options come in on their own (no separate "Get started" step).
    var wordsDone by remember { mutableStateOf(reduceAnimations) }
    var started by remember { mutableStateOf(false) }
    val view = androidx.compose.ui.platform.LocalView.current
    var name by remember { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }
    var showEmailAuth by remember { mutableStateOf(false) }
    var showDataUse by remember { mutableStateOf(false) }
    var authBusy by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    val authController = LocalAuthUiController.current
    val authScope = rememberCoroutineScope()

    val welcomeAlpha = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    val welcomeOffsetY = remember { Animatable(if (reduceAnimations) 0f else 39f) }
    val welcomeLetterSpacing = remember { Animatable(if (reduceAnimations) 0.5f else 1.5f) }
    // REAL appears by rising and scaling in (0.97 -> 1), not by a wipe.
    val logoIn = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    val revealProgress = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    val groupShiftProgress = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    val questionAlpha = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    val questionOffsetY = remember { Animatable(if (reduceAnimations) 0f else 12f) }
    val inputAlpha = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    val inputOffsetY = remember { Animatable(if (reduceAnimations) 0f else 12f) }
    val greetingAlpha = remember { Animatable(0f) }
    val shimmer = remember { Animatable(-1f) }
    val uriHandler = LocalUriHandler.current

    // The story's music keeps playing under Welcome to + REAL; it stops when the app goes to the background
    // or the welcome screen goes away.
    val soundContext = LocalContext.current
    val welcomeSound = remember { if (reduceAnimations) null else WelcomeSound(soundContext) }
    val welcomeLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(welcomeLifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) welcomeSound?.release()
        }
        welcomeLifecycle.addObserver(observer)
        onDispose {
            welcomeLifecycle.removeObserver(observer)
            welcomeSound?.fadeOut(600)                      // leaving the welcome screen: soften out, don't cut
        }
    }

    // REAL flying out of the story's sentence into the wordmark (see WelcomeWordStory).
    var flightFrom by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var logoBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var flight by remember { mutableStateOf<Flight?>(null) }
    val flightProgress = remember { Animatable(0f) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(wordsDone) {
        if (!wordsDone) return@LaunchedEffect
        // The wordmark is drawn whole; its entrance is logoIn (fade + rise + scale) with the shine on top.
        revealProgress.snapTo(1f)
        if (reduceAnimations) {
            welcomeAlpha.snapTo(1f)
            welcomeOffsetY.snapTo(0f)
            welcomeLetterSpacing.snapTo(0.5f)
            logoIn.snapTo(1f)
            started = true
            return@LaunchedEffect
        }
        val from = flightFrom
        val to = logoBounds
        if (from != null && to != null && to.width > 0f) {
            // The story ended on its own: "REAL." from the sentence becomes the wordmark (beat 20 of the music).
            // 0 REAL flies from the sentence into the logo (900 ms) with a haptic · 500 "Welcome to" rises in ·
            // 600 the shine sweeps · 2000 the name prompt and sign-in come in.
            val scaleFrom = (from.width * 0.86f) / to.width   // "REAL" without the dot vs the wordmark
            flight = Flight(from.center.x - to.center.x, from.center.y - to.center.y, scaleFrom)
            welcomeLetterSpacing.snapTo(0.5f)
            welcomeOffsetY.snapTo(10f)
            logoIn.snapTo(1f)
            launch { flightProgress.animateTo(1f, tween(900, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))) }
            RealHaptics.tick(view)
            launch {
                delay(500)
                launch { welcomeAlpha.animateTo(1f, tween(600, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))) }
                welcomeOffsetY.animateTo(0f, tween(600, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
            }
            delay(600)
            launch {
                shimmer.snapTo(0f)
                shimmer.animateTo(1f, animationSpec = tween(1050, easing = FastOutSlowInEasing))
                shimmer.snapTo(-1f)
            }
            delay(1400)
            started = true
            return@LaunchedEffect
        }
        // Skipped (tap): timeline after the last word (ms): 0 "Welcome to" fades in 39dp low, 650 it rises into place,
        // 750 REAL rises and scales in, 830 haptic, 980 the shine sweeps, 1450 the sign-in step comes in.
        launch { welcomeAlpha.animateTo(1f, animationSpec = tween(160, easing = LinearOutSlowInEasing)) }
        launch {
            delay(650)
            launch { welcomeLetterSpacing.animateTo(0.5f, animationSpec = tween(400, easing = RealMotion.Emphasized)) }
            welcomeOffsetY.animateTo(0f, animationSpec = tween(400, easing = RealMotion.Emphasized))
        }
        delay(750)
        launch { logoIn.animateTo(1f, animationSpec = tween(550, easing = RealMotion.Emphasized)) }
        delay(80)
        RealHaptics.tick(view)
        delay(150)
        launch {
            shimmer.snapTo(0f)
            shimmer.animateTo(1f, animationSpec = tween(1050, easing = FastOutSlowInEasing))
            shimmer.snapTo(-1f)
        }
        delay(470)
        started = true
    }

    // Welcome to + REAL move up and the existing name prompt / sign-in comes in.
    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
        if (reduceAnimations) {
            groupShiftProgress.snapTo(1f)
            questionAlpha.snapTo(1f)
            questionOffsetY.snapTo(0f)
            inputAlpha.snapTo(1f)
            inputOffsetY.snapTo(0f)
            phase = WelcomePhase.NamePrompt
            return@LaunchedEffect
        }
        delay(120)
        phase = WelcomePhase.NamePrompt
        launch { groupShiftProgress.animateTo(1f, animationSpec = tween(420, easing = RealMotion.Emphasized)) }
        delay(70)
        launch { questionOffsetY.animateTo(0f, animationSpec = tween(300, easing = RealMotion.Emphasized)) }
        launch { questionAlpha.animateTo(1f, animationSpec = tween(300, easing = LinearOutSlowInEasing)) }
        delay(60)
        launch { inputOffsetY.animateTo(0f, animationSpec = tween(300, easing = RealMotion.Emphasized)) }
        inputAlpha.animateTo(1f, animationSpec = tween(300, easing = LinearOutSlowInEasing))
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
        var promptMeasuredHeightPx by remember { mutableIntStateOf(0) }
        val imeBottomPx = WindowInsets.ime.getBottom(density).toFloat()
        val totalHeightPx = with(density) { totalHeight.toPx() }
        // When the name field + sign-in buttons + links are taller than the room under the logo, the whole group
        // lifts a little further so the links at the bottom stay on screen (keeping at least 12dp above the header).
        val baseFinalShiftPx = with(density) { finalShiftDp.toPx() }
        val sceneBelowHeaderPx = with(density) { (headerHeight + contentGapDp).toPx() }
        val overflowPx = if (promptMeasuredHeightPx > 0) {
            (initialHeaderTopPx - baseFinalShiftPx + sceneBelowHeaderPx + promptMeasuredHeightPx +
                with(density) { 12.dp.toPx() } - totalHeightPx)
                .coerceIn(0f, (initialHeaderTopPx - baseFinalShiftPx - with(density) { 12.dp.toPx() }).coerceAtLeast(0f))
        } else 0f
        val finalShiftPx by animateFloatAsState(
            targetValue = baseFinalShiftPx + overflowPx,
            animationSpec = tween(300, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
            label = "welcomeFitShift"
        )
        val promptTopPx = initialHeaderTopPx - finalShiftPx + sceneBelowHeaderPx
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

        
        // Plain black background throughout (REAL_Welcome design): no ambient glow.

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
                    // Same typeface and colour as the word story before it (REAL_Welcome: 23px, #F3F1F4, 400).
                    text = "Welcome to",
                    color = Color(0xFFF3F1F4),
                    fontSize = 23.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = (welcomeLetterSpacing.value - 1f).sp,
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
                        .onGloballyPositioned { if (flight == null) logoBounds = it.boundsInRoot() }
                        .graphicsLayer {
                            val p = logoIn.value
                            alpha = p
                            val f = flight
                            if (f != null) {
                                val t = flightProgress.value
                                translationX = f.dx * (1f - t)
                                translationY = f.dy * (1f - t)
                                val scale = f.scale + (1f - f.scale) * t
                                scaleX = scale; scaleY = scale
                            } else {
                                translationY = with(density) { (6f * (1f - p)).dp.toPx() }
                                val scale = 0.97f + 0.03f * p
                                scaleX = scale; scaleY = scale
                            }
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
                    // Shown for the iOS-ready sign-in set, but not available yet: it cannot be pressed.
                    WelcomeAuthButton(
                        label = "Apple ile devam et",
                        emphasized = false,
                        enabled = false,
                        modifier = Modifier.graphicsLayer { alpha = inputAlpha.value }
                    ) {}
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
                    Spacer(Modifier.height(22.dp))
                    // Kept quiet on purpose: the three documents in one small row. The longer explanations live behind
                    // "Veri kullanımı" and AI asks for its own consent before it is first used.
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.graphicsLayer { alpha = inputAlpha.value }
                    ) {
                        WelcomeLink(androidx.compose.ui.res.stringResource(com.cagri.real.R.string.real_privacy_policy)) { runCatching { uriHandler.openUri(privacyUrl) } }
                        WelcomeLinkDot()
                        WelcomeLink(androidx.compose.ui.res.stringResource(com.cagri.real.R.string.real_privacy_kvkk)) { runCatching { uriHandler.openUri(kvkkUrl) } }
                        WelcomeLinkDot()
                        WelcomeLink(androidx.compose.ui.res.stringResource(com.cagri.real.R.string.real_privacy_title)) { keyboardController?.hide(); focusManager.clearFocus(); showDataUse = true }
                    }
                }
            }
        }

        // The word story plays first, on top of everything; a tap skips straight to Welcome to + REAL.
        if (!wordsDone) WelcomeWordStory(
            sound = welcomeSound,
            onDone = { realBounds -> flightFrom = realBounds; wordsDone = true },
            modifier = Modifier.fillMaxSize()
        )
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
private fun WelcomeLinkDot() {
    Text("·", color = Color.White.copy(alpha = .3f), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 7.dp))
}

@Composable
private fun WelcomeLink(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color.White.copy(alpha = .45f),
        fontSize = 11.sp,
        maxLines = 1,
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

// The story runs on the welcome music's beat grid (120 BPM, 500 ms a beat): three pairs (each word two beats),
// "Not someday." blurred, "Today." snaps sharp, "Make it happen." rolls to "Make it REAL.", and on beat 20 REAL
// flies into the wordmark. Same timeline as the REAL_Welcome demo ("Benim önerim" motion, REAL sound).
private const val WelcomeBeatMillis = 500L

/** Where the wordmark starts its flight, relative to its resting place (px, scale). */
private data class Flight(val dx: Float, val dy: Float, val scale: Float)
private val WelcomePairs = listOf("Every idea." to "Captured.", "Every plan." to "On time.", "Every moment." to "Kept.")
private const val WelcomeSomeday = "Not someday."
private val StoryEase = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val StoryOutEase = CubicBezierEasing(0.4f, 0f, 1f, 1f)
private val StoryInk = Color(0xFFF3F1F4)

/**
 * "Every idea. Captured. … Not someday. Today. Make it happen. Make it REAL." The first word of each pair comes
 * from the side its time sits on and drifts away when it leaves. When the story ends on its own, [onDone] gets the
 * on-screen bounds of "REAL." so the wordmark can fly out of it; a tap skips (bounds = null).
 */
@Composable
private fun WelcomeWordStory(
    sound: WelcomeSound?,
    onDone: (androidx.compose.ui.geometry.Rect?) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    val alpha = remember { Animatable(0f) }
    val blur = remember { Animatable(0f) }
    val dx = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    var sentence by remember { mutableStateOf(false) }
    val sentenceAlpha = remember { Animatable(0f) }
    val sentenceBlur = remember { Animatable(6f) }
    val keepAlpha = remember { Animatable(1f) }
    var slotWord by remember { mutableStateOf("happen.") }
    val slotShift = remember { Animatable(0f) }          // in line heights: -1.1 = gone up, 1.1 = waiting below
    val slotAlpha = remember { Animatable(1f) }
    var realBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val currentDone by androidx.compose.runtime.rememberUpdatedState(onDone)

    LaunchedEffect(Unit) {
        sound?.prepare()
        delay(350)
        sound?.start()                                   // the music's first note is the first word
        // Every step is placed against the start time, so the motion never drifts off the music.
        val start = android.os.SystemClock.uptimeMillis()
        suspend fun at(beat: Float) {
            val wait = start + (beat * WelcomeBeatMillis).toLong() - android.os.SystemClock.uptimeMillis()
            if (wait > 0) delay(wait)
        }
        fun wordIn(word: String, fromDx: Float, fromBlur: Float = 6f, fromScale: Float = 0.97f, duration: Int = 450, dim: Float = 1f) {
            text = word
            launch { alpha.snapTo(0f); alpha.animateTo(dim, tween(duration, easing = StoryEase)) }
            launch { blur.snapTo(fromBlur); blur.animateTo(0f, tween(duration, easing = StoryEase)) }
            launch { dx.snapTo(fromDx); dx.animateTo(0f, tween(duration, easing = StoryEase)) }
            launch { scale.snapTo(fromScale); scale.animateTo(1f, tween(duration, easing = StoryEase)) }
        }
        fun wordOut(toDx: Float = 0f) {
            launch { alpha.animateTo(0f, tween(200, easing = StoryOutEase)) }
            launch { blur.animateTo(4f, tween(200, easing = StoryOutEase)) }
            launch { dx.animateTo(toDx, tween(200, easing = StoryOutEase)) }
        }

        WelcomePairs.forEachIndexed { p, (first, second) ->
            val dir = p - 1                              // past-ish ← · present · future-ish →
            val base = p * 4f
            at(base); wordIn(first, fromDx = dir * -18f)
            at(base + 1.6f); wordOut(dir * 14f)
            at(base + 2f); wordIn(second, fromDx = dir * -18f)
            at(base + 3.6f); wordOut(dir * 14f)
        }
        at(12f); wordIn(WelcomeSomeday, fromDx = 0f, fromBlur = 14f, fromScale = 0.94f, duration = 900, dim = 0.55f)
        at(14f)                                          // "Today." snaps into focus
        text = "Today."
        launch { alpha.snapTo(0.5f); alpha.animateTo(1f, tween(290, easing = StoryEase)) }
        launch { blur.snapTo(8f); blur.animateTo(0f, tween(290, easing = StoryEase)) }
        launch { dx.snapTo(0f) }
        launch {
            scale.snapTo(1.06f)
            scale.animateTo(1f, androidx.compose.animation.core.keyframes {
                durationMillis = 520
                0.985f at 290 using StoryEase
            })
        }
        at(15.6f); wordOut()
        at(16f)                                          // "Make it happen."
        alpha.snapTo(0f)
        sentence = true
        launch { sentenceAlpha.animateTo(1f, tween(480, easing = StoryEase)) }
        launch { sentenceBlur.animateTo(0f, tween(480, easing = StoryEase)) }
        at(18f)                                          // happen. rolls up, REAL. rolls in
        launch {
            launch { slotAlpha.animateTo(0f, tween(200, easing = StoryOutEase)) }
            slotShift.animateTo(-1.1f, tween(200, easing = StoryOutEase))
            slotWord = "REAL."
            slotShift.snapTo(1.1f)
            launch { slotAlpha.animateTo(1f, tween(380, easing = StoryEase)) }
            slotShift.animateTo(0f, tween(380, easing = StoryEase))
        }
        at(19.5f); launch { keepAlpha.animateTo(0f, tween(250, easing = StoryOutEase)) }
        at(20f)
        currentDone(realBounds)
    }

    val density = LocalDensity.current
    val style = TextStyle(color = StoryInk, fontSize = 31.sp, lineHeight = 38.sp, letterSpacing = (-1.1).sp, fontWeight = FontWeight.Normal)
    Box(
        modifier.background(Color.Black)
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                sound?.fadeOut()
                currentDone(null)
            },
        contentAlignment = Alignment.Center
    ) {
        if (!sentence) {
            Text(
                text, style = style, maxLines = 1,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .blurIfSupported(blur.value)
                    .graphicsLayer {
                        this.alpha = alpha.value
                        translationX = dx.value.dp.toPx()
                        scaleX = scale.value; scaleY = scale.value
                    }
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .blurIfSupported(sentenceBlur.value)
                    .graphicsLayer { this.alpha = sentenceAlpha.value }
            ) {
                Text("Make it", style = style, maxLines = 1, modifier = Modifier.graphicsLayer { this.alpha = keepAlpha.value })
                Spacer(Modifier.width(8.dp))
                Box(Modifier.clipToBounds()) {
                    Text(
                        slotWord, style = style, maxLines = 1,
                        modifier = Modifier
                            .onGloballyPositioned { realBounds = it.boundsInRoot() }
                            .graphicsLayer {
                                this.alpha = slotAlpha.value
                                translationY = slotShift.value * with(density) { 38.sp.toPx() }
                            }
                    )
                }
            }
        }
    }
}

/** Blur on Android 12+ (RenderEffect); older versions just skip the blur, the fade still carries the motion. */
private fun Modifier.blurIfSupported(radiusDp: Float): Modifier =
    if (radiusDp > 0.05f && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        this.then(Modifier.blur(radiusDp.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded))
    } else this

