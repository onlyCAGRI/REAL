package com.cagri.bigboscagri.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.bigboscagri.ui.DesignStyle

/** Separate chrome/source siblings; never records its own refractive toolbar. */
@Composable
fun SettingsLayout(
    designStyle: DesignStyle, reduceDynamicBlur: Boolean, onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    if (designStyle != DesignStyle.LiquidGlass) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()
            .navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp), content = content)
        return
    }
    val scene = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val capture = shouldCaptureBackdrop(designStyle, reduceDynamicBlur, Build.VERSION.SDK_INT)
    Box(Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding()) {
        Column(Modifier.fillMaxSize().onGloballyPositioned { position = it.positionInRoot() }
            .drawWithContent {
                if (capture) { scene.record { this@drawWithContent.drawContent() }; drawLayer(scene) }
                else drawContent()
            }
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = status + 76.dp, bottom = 24.dp), content = content)
        TopGlassOverlay(scene, { position }, reduceDynamicBlur = reduceDynamicBlur)
        LiquidGlassSurface(scene, { position },
            Modifier.align(Alignment.TopCenter).padding(top = status + 8.dp, start = 16.dp, end = 16.dp).fillMaxWidth(),
            corners = 28.dp, reduceDynamicBlur = reduceDynamicBlur) {
            Box(Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).semantics { contentDescription = "Geri" }) {
                    ActionIcon(ActionGlyph.Back, Modifier.size(23.dp))
                }
                Text("Ayarlar", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

/** User-facing appearance choices: Focus (classic glass) and Liquid. */
@Composable
fun LiquidAppearanceSelector(selected: DesignStyle, onSelect: (DesignStyle) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(DesignStyle.Glass, DesignStyle.LiquidGlass).forEach { style ->
            val chosen = selected == style
            val label = when (style) {
                DesignStyle.Glass -> "Focus"
                DesignStyle.LiquidGlass -> "Liquid"
                DesignStyle.Ice -> "Focus"
            }
            val description = when (style) {
                DesignStyle.Glass -> "Sade ve odaklı cam"
                DesignStyle.LiquidGlass -> "Derinlik ve ışık"
                DesignStyle.Ice -> "Sade ve odaklı cam"
            }
            Row(Modifier.fillMaxWidth().liquidChrome(18.dp, selected = chosen)
                .selectable(selected = chosen, role = Role.RadioButton, onClick = { onSelect(style) })
                .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val sampleFill = when (style) {
                    DesignStyle.Glass -> listOf(Color(0xFF5C5E64), Color(0xFF26282C))
                    DesignStyle.LiquidGlass -> listOf(Color(0xFF333335), Color(0xFF101012), Color(0xFF222224))
                    DesignStyle.Ice -> listOf(Color(0xFF5C5E64), Color(0xFF26282C))
                }
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(sampleFill))
                    .border(0.65.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                    ActionIcon(ActionGlyph.LiquidGlass, Modifier.size(21.dp), Color.White)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(description, color = Color(0xFFAAAEB6), fontSize = 12.sp)
                }
                if (chosen) ActionIcon(ActionGlyph.Check, Modifier.size(20.dp), LiquidGlassTokens.selectionAccent)
            }
        }
    }
}
