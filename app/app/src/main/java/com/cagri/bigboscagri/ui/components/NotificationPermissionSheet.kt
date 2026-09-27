package com.cagri.bigboscagri.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    secondaryLabel: String = "Daha sonra"
) {
    val liquid = LocalDesignStyle.current == com.cagri.bigboscagri.ui.DesignStyle.LiquidGlass
    if (visible) {
        BackHandler(onBack = onLater)
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(300)),
        exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(250)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLater
                )
        ) {
            val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

            AnimatedVisibility(
                visible = visible,
                enter = if (reduceAnimations) EnterTransition.None
                else if (liquid) fadeIn(tween(180)) else slideInVertically(
                    animationSpec = tween(
                        durationMillis = 400,
                        easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
                    )
                ) { fullHeight -> fullHeight },
                exit = if (reduceAnimations) ExitTransition.None
                else if (liquid) fadeOut(tween(140)) else slideOutVertically(
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
                    )
                ) { fullHeight -> fullHeight },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(sheetShape)
                        .background(if (liquid) LiquidGlassTokens.contentColor else Color(0xFF18181B))
                        .border(0.75.dp, Color.White.copy(alpha = 0.12f), sheetShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {} // Prevent clicks on the sheet from bubbling up
                        )
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .navigationBarsPadding()
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 4.dp)
                            .background(Color(0xFF76767D), RoundedCornerShape(2.dp))
                            .align(Alignment.CenterHorizontally)
                    )

                    Spacer(Modifier.height(20.dp))

                    // Title
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )

                    Spacer(Modifier.height(8.dp))

                    // Description
                    Text(
                        text = description,
                        color = Color(0xFFA4A4AD),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Spacer(Modifier.height(28.dp))

                    // Primary Button: "İzin ver"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(25.dp))
                            .then(if (liquid) Modifier.liquidChrome(25.dp) else Modifier.background(Color.White))
                            .clickable(onClick = onAllow),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = primaryLabel,
                            color = if (liquid) Color.White else Color.Black,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Secondary Button: "Daha sonra"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .clickable(onClick = onLater),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = secondaryLabel,
                            color = Color(0xFFA4A4AD),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
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
        modifier = modifier
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
        modifier = modifier
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
        secondaryLabel = "Vazgeç"
    )
}
