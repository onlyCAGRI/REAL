package com.cagri.real.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.R
import com.cagri.real.ui.theme.RealType
import kotlin.math.PI
import kotlin.math.sin

/** Hero glyph shown above the title of a permission sheet. */
enum class PermissionGlyph { Brand, Bell, Mic, Pin }

private val SheetEnterEasing = RealMotion.Emphasized
private val SheetExitEasing = RealMotion.Exit
private val TitleColor = Color(0xFFF3F1F4)
private val BodyColor = Color(0xFF9B9BA4)

@Composable
fun PermissionSheet(
    visible: Boolean,
    title: String,
    description: String,
    onAllow: () -> Unit,
    onLater: () -> Unit,
    reduceAnimations: Boolean = false,
    modifier: Modifier = Modifier,
    primaryLabel: String = "İzin ver",
    secondaryLabel: String = "Daha sonra",
    /** Null = no hero, just the title (e.g. the calendar web-link question). */
    glyph: PermissionGlyph? = PermissionGlyph.Brand,
    primaryEnabled: Boolean = true,
    extraContent: @Composable () -> Unit = {}
) {
    val reduce = reduceAnimations || LocalLiquidReduceMotion.current
    if (visible) {
        BackHandler(onBack = onLater)
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (reduce) EnterTransition.None else fadeIn(tween(300)),
        exit = if (reduce) ExitTransition.None else fadeOut(tween(250)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.68f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLater
                )
        ) {
            val sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

            AnimatedVisibility(
                visible = visible,
                enter = if (reduce) EnterTransition.None
                else slideInVertically(tween(480, easing = SheetEnterEasing)) { it } +
                    fadeIn(tween(220)),
                exit = if (reduce) ExitTransition.None
                else slideOutVertically(tween(300, easing = SheetExitEasing)) { it } +
                    fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                // Content is composed only while the sheet is visible, so this resets on every open.
                var entered by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { entered = true }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(sheetShape)
                        .background(LiquidGlassTokens.contentColor)
                        .drawBehind {
                            drawRect(
                                Brush.radialGradient(
                                    listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
                                    center = Offset(size.width / 2f, 0f),
                                    radius = size.width * 0.85f
                                )
                            )
                        }
                        .border(0.75.dp, liquidContentEdge(), sheetShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 4.dp)
                            .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(2.dp))
                    )

                    Spacer(Modifier.height(18.dp))

                    if (glyph != null) {
                        Box(Modifier.staggerIn(entered, 0, reduce)) {
                            PermissionHero(glyph = glyph, animate = !reduce)
                        }
                        Spacer(Modifier.height(6.dp))
                    } else {
                        Spacer(Modifier.height(16.dp))
                    }

                    Text(
                        text = title,
                        style = RealType.editorialSection.copy(
                            fontSize = 28.sp,
                            lineHeight = 31.sp,
                            letterSpacing = (-0.9).sp
                        ),
                        color = TitleColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggerIn(entered, 80, reduce)
                    )

                    if (description.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = description,
                        style = RealType.cardBodyProminent,
                        color = BodyColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp)
                            .staggerIn(entered, 150, reduce)
                    )
                    }

                    extraContent()

                    Spacer(Modifier.height(30.dp))

                    val primarySource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggerIn(entered, 220, reduce)
                            .pressScale(primarySource, !reduce)
                            .height(54.dp)
                            .clip(RoundedCornerShape(27.dp))
                            .background(Color.White.copy(alpha = if (primaryEnabled) 1f else 0.55f))
                            .clickable(
                                enabled = primaryEnabled,
                                interactionSource = primarySource,
                                indication = null,
                                role = Role.Button,
                                onClick = onAllow
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = primaryLabel,
                            style = RealType.actionLabel.copy(fontSize = 15.sp),
                            color = Color.Black
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    val secondarySource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggerIn(entered, 280, reduce)
                            .pressScale(secondarySource, !reduce)
                            .height(46.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .clickable(
                                interactionSource = secondarySource,
                                indication = null,
                                role = Role.Button,
                                onClick = onLater
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = secondaryLabel,
                            style = RealType.actionLabel.copy(fontSize = 14.sp),
                            color = BodyColor
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun NotificationPermissionSheet(
    visible: Boolean,
    onAllow: () -> Unit,
    onLater: () -> Unit,
    reduceAnimations: Boolean = false,
    modifier: Modifier = Modifier
) {
    PermissionSheet(
        visible = visible,
        title = "Bildirimlere izin verilsin mi?",
        description = "Real, hatırlatıcılarını zamanı geldiğinde gösterebilmek için bildirim iznine ihtiyaç duyar.",
        onAllow = onAllow,
        onLater = onLater,
        reduceAnimations = reduceAnimations,
        modifier = modifier,
        glyph = PermissionGlyph.Bell
    )
}

@Composable
fun MicrophonePermissionSheet(
    visible: Boolean,
    onAllow: () -> Unit,
    onLater: () -> Unit,
    reduceAnimations: Boolean = false,
    modifier: Modifier = Modifier
) {
    PermissionSheet(
        visible = visible,
        title = "Mikrofona izin verilsin mi?",
        description = "Real, sesli hatırlatıcı oluşturabilmek için mikrofon iznine ihtiyaç duyar.",
        onAllow = onAllow,
        onLater = onLater,
        reduceAnimations = reduceAnimations,
        modifier = modifier,
        glyph = PermissionGlyph.Mic
    )
}

@Composable
fun LocationGuideSheet(
    visible: Boolean,
    onOpenMaps: () -> Unit,
    onCancel: () -> Unit,
    reduceAnimations: Boolean = false
) {
    PermissionSheet(
        visible = visible,
        title = "Konum ekle",
        description = "Google Haritalar'da istediğiniz yeri seçin. Paylaş'a dokunun ve REAL'i seçin.",
        onAllow = onOpenMaps,
        onLater = onCancel,
        reduceAnimations = reduceAnimations,
        primaryLabel = "Google Haritalar'ı aç",
        secondaryLabel = "Vazgeç",
        glyph = PermissionGlyph.Pin
    )
}

// ---------------------------------------------------------------------------------------------
// Hero: glass disc + pulsing rings + animated glyph
// ---------------------------------------------------------------------------------------------

@Composable
internal fun PermissionHero(glyph: PermissionGlyph, animate: Boolean) {
    Box(
        modifier = Modifier
            .size(136.dp)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
                        radius = size.minDimension / 2f
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (animate) {
            val rings = rememberInfiniteTransition(label = "permissionRings")
            repeat(3) { i ->
                val p by rings.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2700, easing = LinearOutSlowInEasing),
                        repeatMode = RepeatMode.Restart,
                        initialStartOffset = StartOffset(i * 900, StartOffsetType.FastForward)
                    ),
                    label = "permissionRing$i"
                )
                Box(
                    Modifier
                        .size(88.dp)
                        .drawBehind {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.20f * (1f - p)),
                                radius = size.minDimension / 2f * (1f + 0.5f * p),
                                style = Stroke(1.dp.toPx())
                            )
                        }
                )
            }
        }

        Box(
            modifier = Modifier
                .size(88.dp)
                .liquidChrome(44.dp),
            contentAlignment = Alignment.Center
        ) {
            PermissionGlyphIcon(glyph = glyph, animate = animate)
        }
    }
}

@Composable
private fun PermissionGlyphIcon(glyph: PermissionGlyph, animate: Boolean) {
    val transition = if (animate) rememberInfiniteTransition(label = "permissionGlyph") else null
    // 0..1 loop used for the bell ring (damped swing, then a pause).
    val cycle = transition?.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing), RepeatMode.Restart),
        label = "glyphCycle"
    )
    // 0..1..0 breathing used by the mic, pin and brand mark.
    val breathe = transition?.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glyphBreathe"
    )
    val ink = Color.White.copy(alpha = 0.96f)

    when (glyph) {
        PermissionGlyph.Brand -> Image(
            painter = painterResource(R.drawable.real_r_mark),
            contentDescription = null,
            modifier = Modifier
                .size(46.dp)
                .graphicsLayer {
                    val b = breathe?.value ?: 0f
                    scaleX = 1f + 0.06f * b
                    scaleY = 1f + 0.06f * b
                }
        )

        PermissionGlyph.Bell -> Canvas(Modifier.size(38.dp)) {
            val x = ((cycle?.value ?: 1f) / 0.28f).coerceIn(0f, 1f)
            val swing = if (cycle == null) 0f else sin(x * 4f * PI.toFloat()) * 14f * (1f - x)
            drawBell(ink, swing)
        }

        PermissionGlyph.Mic -> Canvas(Modifier.size(38.dp)) {
            drawMic(ink, breathe?.value ?: 0f)
        }

        PermissionGlyph.Pin -> Canvas(Modifier.size(38.dp)) {
            drawPin(ink, breathe?.value ?: 0f)
        }
    }
}

// Glyphs are drawn in a 24x24 unit box and scaled to the canvas.

private fun DrawScope.drawBell(color: Color, swingDegrees: Float) {
    val unit = size.width / 24f
    scale(unit, unit, Offset.Zero) {
        rotate(swingDegrees, Offset(12f, 3f)) {
            val body = Path().apply {
                moveTo(6f, 17f)
                lineTo(6f, 10.5f)
                cubicTo(6f, 7.2f, 8.7f, 4.5f, 12f, 4.5f)
                cubicTo(15.3f, 4.5f, 18f, 7.2f, 18f, 10.5f)
                lineTo(18f, 17f)
                lineTo(20f, 19f)
                lineTo(4f, 19f)
                close()
            }
            drawPath(body, color)
            drawCircle(color, 1.25f, Offset(12f, 3.6f))
            drawCircle(color, 1.8f, Offset(12f, 21.2f))
        }
    }
}

private fun DrawScope.drawMic(color: Color, breathe: Float) {
    val unit = size.width / 24f
    scale(unit, unit, Offset.Zero) {
        scale(1f + 0.07f * breathe, pivot = Offset(12f, 12f)) {
            drawRoundRect(
                color = color,
                topLeft = Offset(8.75f, 2.5f),
                size = Size(6.5f, 11.5f),
                cornerRadius = CornerRadius(3.25f, 3.25f)
            )
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(5.9f, 4.9f),
                size = Size(12.2f, 12.2f),
                style = Stroke(width = 1.8f, cap = StrokeCap.Round)
            )
            drawLine(color, Offset(12f, 17.1f), Offset(12f, 20.6f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            drawLine(color, Offset(8.6f, 20.6f), Offset(15.4f, 20.6f), strokeWidth = 1.8f, cap = StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawPin(color: Color, bob: Float) {
    val unit = size.width / 24f
    scale(unit, unit, Offset.Zero) {
        translate(0f, -1.4f * bob) {
            val pin = Path().apply {
                fillType = PathFillType.EvenOdd
                moveTo(12f, 21f)
                cubicTo(12f, 21f, 5.5f, 14.2f, 5.5f, 9.5f)
                cubicTo(5.5f, 5.9f, 8.4f, 3f, 12f, 3f)
                cubicTo(15.6f, 3f, 18.5f, 5.9f, 18.5f, 9.5f)
                cubicTo(18.5f, 14.2f, 12f, 21f, 12f, 21f)
                close()
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        Offset(12f - 2.6f, 9.5f - 2.6f),
                        Size(5.2f, 5.2f)
                    )
                )
            }
            drawPath(pin, color)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Motion helpers
// ---------------------------------------------------------------------------------------------

/** Fades and lifts a child in after [delayMillis]; instant when motion is reduced. */
@Composable
private fun Modifier.staggerIn(entered: Boolean, delayMillis: Int, reduce: Boolean): Modifier {
    val progress by animateFloatAsState(
        targetValue = if (entered || reduce) 1f else 0f,
        animationSpec = if (reduce) tween(0) else tween(440, delayMillis, SheetEnterEasing),
        label = "permissionStagger"
    )
    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 18.dp.toPx()
    }
}

/** Slight press-in scale for tappable pills. */
@Composable
private fun Modifier.pressScale(source: MutableInteractionSource, enabled: Boolean): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(if (pressed) 120 else 220),
        label = "permissionPress"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}


/**
 * The REAL bottom sheet surface (notification permission / SYNC question): rounded top, dark glass, a soft light from
 * the top edge and the liquid edge line. Other windows (category editor, order, goals, members) use the same look.
 */
fun Modifier.realSheetSurface(): Modifier {
    val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    return this
        .clip(shape)
        .background(LiquidGlassTokens.contentColor)
        .drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
                    center = Offset(size.width / 2f, 0f),
                    radius = size.width * 0.85f
                )
            )
        }
        .border(0.75.dp, liquidContentEdge(), shape)
}

/** The small bar at the top of a REAL bottom sheet. */
@Composable
fun RealSheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(top = 12.dp)
            .size(width = 38.dp, height = 4.dp)
            .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(2.dp))
    )
}
