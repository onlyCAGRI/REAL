package com.cagri.bigboscagri.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
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
import com.cagri.bigboscagri.ui.DesignStyle

@Composable
fun SettingsLayout(
    designStyle: DesignStyle,
    reduceDynamicBlur: Boolean,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val isLiquid = designStyle == DesignStyle.LiquidGlass
    val capture = shouldCaptureBackdrop(designStyle, reduceDynamicBlur, Build.VERSION.SDK_INT)
    val scene = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }

    val topBarContent: @Composable () -> Unit = {
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .semantics { contentDescription = "Geri" }
            ) {
                ActionIcon(ActionGlyph.Back, Modifier.size(23.dp), color = Color.White)
            }
            Text(
                text = "Ayarlar",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .navigationBarsPadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .then(
                    if (isLiquid) {
                        Modifier
                            .onGloballyPositioned { position = it.positionInRoot() }
                            .drawWithContent {
                                if (capture) {
                                    scene.record { this@drawWithContent.drawContent() }
                                    drawLayer(scene)
                                } else {
                                    drawContent()
                                }
                            }
                    } else Modifier
                )
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = status + 68.dp, bottom = 32.dp),
            content = content
        )

        val topBarModifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = status + 8.dp, start = 16.dp, end = 16.dp)
            .fillMaxWidth()
            .height(52.dp)

        if (isLiquid) {
            TopGlassOverlay(scene, { position }, reduceDynamicBlur = reduceDynamicBlur)
            LiquidGlassSurface(
                scene,
                { position },
                topBarModifier,
                corners = 26.dp,
                reduceDynamicBlur = reduceDynamicBlur
            ) {
                topBarContent()
            }
        } else {
            val glassShape = RoundedCornerShape(26.dp)
            Box(
                topBarModifier
                    .clip(glassShape)
                    .background(Color(0xFF161619))
                    .border(0.8.dp, Color(0xFF28282C), glassShape)
            ) {
                topBarContent()
            }
        }
    }
}
