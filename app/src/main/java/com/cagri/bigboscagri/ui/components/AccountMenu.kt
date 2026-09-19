package com.cagri.bigboscagri.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.bigboscagri.ui.DesignStyle

@Composable
fun AccountMenuOverlay(
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    onDismiss: () -> Unit,
    onSettingsClick: () -> Unit,
    reduceDynamicBlur: Boolean = false,
    reduceAnimations: Boolean = false,
    designStyle: DesignStyle = DesignStyle.Glass,
    userName: String = "",
    modifier: Modifier = Modifier
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val dismissInteraction = remember { MutableInteractionSource() }
    val revealProgress = remember { Animatable(if (reduceAnimations) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!reduceAnimations) {
            revealProgress.animateTo(
                1f,
                animationSpec = tween(
                    durationMillis = 260,
                    easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
                )
            )
        }
    }

    val progress = revealProgress.value

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.matchParentSize()
                .graphicsLayer { alpha = progress }
                .background(Color.Black.copy(alpha = 0.40f))
                .clickable(interactionSource = dismissInteraction, indication = null, onClick = onDismiss)
                .semantics { contentDescription = "Hesap menüsünü kapat" }
        )

        val panelContent: @Composable BoxScope.() -> Unit = {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 15.dp)) {
                Text(
                    text = "Hesap",
                    color = Color(0xFFF7F7F8),
                    fontSize = 21.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Bu cihazdaki notlar",
                    color = Color(0xFFA4A4AD),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(13.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GuestAvatar(Modifier.size(44.dp))
                    Text(
                        text = userName.trim().ifBlank { "Kullanıcı" },
                        color = Color(0xFFF7F7F8),
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(12.dp))
                AccountMenuItem(icon = ActionGlyph.Add, label = "Hesap ekle")
                Spacer(Modifier.height(3.dp))
                AccountMenuItem(icon = ActionGlyph.Manage, label = "Hesapları yönet")
                Spacer(Modifier.height(3.dp))
                AccountMenuItem(icon = ActionGlyph.Settings, label = "Ayarlar", onClick = {
                    onDismiss()
                    onSettingsClick()
                })
            }
        }

        val panelModifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = statusTop + 14.dp)
            .fillMaxWidth(0.90f)
            .widthIn(max = 380.dp)
            .graphicsLayer {
                alpha = progress
                val scale = if (reduceAnimations) 1f else 0.96f + 0.04f * progress
                scaleX = scale
                scaleY = scale
                translationY = if (reduceAnimations) 0f else (1f - progress) * -16f
            }
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }

        if (designStyle == DesignStyle.Ice) {
            IcePanel(
                backdrop = backdrop,
                backdropPosition = backdropPosition,
                modifier = panelModifier,
                corners = 30.dp,
                reduceDynamicBlur = reduceDynamicBlur,
                blurRadius = 46.dp,
                dense = true,
                content = panelContent
            )
        } else {
            GlassPanel(
                backdrop = backdrop,
                backdropPosition = backdropPosition,
                modifier = panelModifier,
                corners = 30.dp,
                reduceDynamicBlur = reduceDynamicBlur,
                blurRadius = 46.dp,
                extraFrosted = true,
                content = panelContent
            )
        }
    }
}

@Composable
fun GuestAvatar(modifier: Modifier = Modifier) {
    Box(
        modifier.background(Color.White.copy(alpha = 0.11f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        ActionIcon(
            glyph = ActionGlyph.Person,
            modifier = Modifier.size(21.dp),
            color = Color(0xFFF1F1F5)
        )
    }
}

@Composable
private fun AccountMenuItem(
    icon: ActionGlyph,
    label: String,
    onClick: (() -> Unit)? = null
) {
    val enabled = onClick != null
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Transparent, shape)
            .let {
                if (enabled) {
                    // Same bounded press/ripple feedback used by ReminderCard.
                    it.clickable(
                        role = Role.Button,
                        onClickLabel = label,
                        onClick = onClick!!
                    )
                } else it
            }
            .padding(vertical = 6.dp)
    ) {
        Box(Modifier.size(31.dp), contentAlignment = Alignment.Center) {
            ActionIcon(
                glyph = icon,
                modifier = Modifier.size(22.dp),
                color = if (enabled) Color(0xFFF1F1F5) else Color(0xFF77777F)
            )
        }
        Text(
            text = label,
            color = if (enabled) Color(0xFFF7F7F8) else Color(0xFF77777F),
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
