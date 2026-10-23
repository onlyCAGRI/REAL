package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.ui.DesignStyle

/**
 * Settings now has one visual language: REAL Liquid Glass.
 *
 * The backdrop is an independent ambient layer. Glass surfaces sit above it so
 * they never sample themselves; this follows the same capture/surface split as
 * the home dock and avoids recursive backdrop rendering.
 */
@Composable
fun SettingsLayout(
    designStyle: DesignStyle,
    reduceDynamicBlur: Boolean,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val capture = shouldCaptureBackdrop(DesignStyle.LiquidGlass, reduceDynamicBlur, Build.VERSION.SDK_INT)
    val scene = rememberGraphicsLayer()
    var scenePosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .navigationBarsPadding()
    ) {
        // Independent backdrop for the settings glass. Keeping it separate from
        // the scrolling content prevents the glass from drawing/sampling itself.
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { scenePosition = it.positionInRoot() }
                .drawWithContent {
                    if (capture) {
                        scene.record { this@drawWithContent.drawContent() }
                        drawLayer(scene)
                    } else {
                        drawContent()
                    }
                }
                .background(Color.Black)
                .drawWithCache {
                    val upperGlow = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.075f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.82f, size.height * 0.08f),
                        radius = size.width * 0.78f
                    )
                    val lowerGlow = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.055f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.12f, size.height * 0.82f),
                        radius = size.width * 0.92f
                    )
                    val depth = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.64f to Color(0x12000000),
                        1f to Color(0xA6000000)
                    )
                    onDrawBehind {
                        drawRect(upperGlow)
                        drawRect(lowerGlow)
                        drawRect(depth)
                    }
                }
        )

        val bodyModifier = Modifier
            .align(Alignment.TopCenter)
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = status + 70.dp,
                bottom = 10.dp
            )
            .fillMaxSize()

        LiquidGlassSurface(
            backdrop = scene,
            backdropPosition = { scenePosition },
            modifier = bodyModifier,
            corners = 34.dp,
            reduceDynamicBlur = reduceDynamicBlur,
            role = LiquidGlassRole.Sheet
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 28.dp),
                content = content
            )
        }

        TopGlassOverlay(
            backdrop = scene,
            backdropPosition = { scenePosition },
            reduceDynamicBlur = reduceDynamicBlur
        )

        LiquidGlassSurface(
            backdrop = scene,
            backdropPosition = { scenePosition },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = status + 8.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
                .height(52.dp),
            corners = 26.dp,
            reduceDynamicBlur = reduceDynamicBlur,
            role = LiquidGlassRole.Chrome
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp)
            ) {
                RealBackButton(onBack, Modifier.align(Alignment.CenterStart))
                Text(
                    text = t("Ayarlar"),
                    color = Color.White,
                    fontSize = 18.sp,
                    letterSpacing = (-0.3).sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
