package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cagri.real.data.JournalMood
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.components.water.WaterTrackerScreen
import com.cagri.real.ui.components.water.rememberWaterTrackerState
import com.cagri.real.ui.theme.RealFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val YasamWaterTone = Color(0xFF6FD6FF)
internal val YasamMoodTone = Color(0xFFB59BFF)
internal val YasamBookTone = Color(0xFFFFB86F)
internal val YasamJournalTone = Color(0xFFFF8FB1)
internal val YasamMediaTone = Color(0xFF7BE0A4)
internal val YasamMuted = Color(0xFF9A9AA2)

// NOTLAR
// - Palet yalnız istenen değerlerle kuruldu; "Hesapları yönet" / Card Share sheet dosyaları pakette yoktu.
// - RealFont'ta Light (300) ağırlığı yok: büyük cümle Normal. Taslaktaki ince görünüm için Inter Light eklenmeli.

/** Cam yüzey: beyaz %6 dolgu, %10 çizgi. */
internal fun Modifier.yasamGlass(radius: Dp = 24.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return clip(shape)
        .background(Color.White.copy(alpha = 0.06f))
        .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
}

/** Üstte yumuşak radial parıltı. Renk çizim aşamasında okunur; animasyonda recomposition olmaz. */
internal fun Modifier.yasamGlow(color: () -> Color): Modifier = drawBehind {
    drawRect(
        Brush.radialGradient(
            0f to color().copy(alpha = 0.38f),
            0.6f to Color(0x1F6FD6FF),
            1f to Color.Transparent,
            center = Offset(size.width / 2f, 90.dp.toPx()),
            radius = 240.dp.toPx()
        )
    )
}

internal fun yasamMoodTone(mood: JournalMood?): Color = when (mood) {
    null, JournalMood.GOOD, JournalMood.NEUTRAL -> YasamMoodTone
    JournalMood.VERY_GOOD, JournalMood.HAPPY, JournalMood.EXCITED -> Color(0xFFFFB86F)
    JournalMood.CALM -> Color(0xFF6FD6FF)
    JournalMood.TIRED, JournalMood.BORED, JournalMood.LOW -> Color(0xFF8A94B8)
    JournalMood.HARD, JournalMood.BAD, JournalMood.SAD -> Color(0xFF6F8FFF)
    JournalMood.STRESSED, JournalMood.ANGRY, JournalMood.FUCK_OFF -> Color(0xFFFF8F8F)
}

/**
 * Alt mikrofon: Home dock'taki mic ile aynı sıvı cam (LiquidGlassRole.Dock). Arkasındaki içerik
 * (kaydıran kutular) camdan görünür. Cam, yakalanan sahnenin DIŞINDA çizilir.
 */
@Composable
internal fun YasamMic(
    onMicClick: () -> Unit,
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    designStyle: DesignStyle,
    reduceDynamicBlur: Boolean,
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().padding(top = 8.dp, bottom = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape)
                .clickable(role = Role.Button, onClick = onMicClick)
                .semantics { contentDescription = t("Sesli komut") },
            contentAlignment = Alignment.Center
        ) {
            YasamMicGlass(
                backdrop, backdropPosition, designStyle, reduceDynamicBlur, reduceAnimations,
                Modifier.fillMaxSize()
            ) {
                YasamIcon(YasamGlyph.MicBars, Color.White, 22.dp, Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun YasamMicGlass(
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    designStyle: DesignStyle,
    reduceDynamicBlur: Boolean,
    reduceAnimations: Boolean,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    when (designStyle) {
        DesignStyle.LiquidGlass -> LiquidGlassSurface(
            backdrop, backdropPosition, modifier,
            corners = 32.dp,
            reduceDynamicBlur = reduceDynamicBlur,
            reduceAnimations = reduceAnimations,
            role = LiquidGlassRole.Dock,
            content = content
        )
        DesignStyle.Ice -> IcePanel(
            backdrop, backdropPosition, modifier,
            corners = 32.dp, reduceDynamicBlur = reduceDynamicBlur, content = content
        )
        else -> GlassPanel(
            backdrop, backdropPosition, modifier,
            corners = 32.dp, reduceDynamicBlur = reduceDynamicBlur, content = content
        )
    }
}

/**
 * Su tam ekranını (kendi Dialog penceresi) eski kartın açılış/kapanış akışıyla barındırır;
 * eski önizleme kartını compose etmeden, yalnız açıkken.
 */
@Composable
internal fun YasamWaterHost(reduceAnimations: Boolean, reduceDynamicBlur: Boolean, onClosed: () -> Unit) {
    val state = rememberWaterTrackerState()
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reduceAnimations) {
        if (!reduceAnimations) withFrameNanos { }
        visible = true
    }
    WaterTrackerScreen(
        state = state,
        reduceAnimations = reduceAnimations,
        visible = visible,
        reduceDynamicBlur = reduceDynamicBlur,
        onDismiss = {
            if (!closing) {
                closing = true
                visible = false
                if (reduceAnimations) onClosed() else scope.launch { delay(230L); onClosed() }
            }
        }
    )
}
