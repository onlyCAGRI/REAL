package com.cagri.bigboscagri.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextOverflow
import com.cagri.bigboscagri.ui.reminderDate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.bigboscagri.speech.VoicePhase
import com.cagri.bigboscagri.ui.DesignStyle

@Composable
fun GlassComposer(
    value: String,
    alertAt: Long? = null,
    saving: Boolean = false,
    onPickDate: () -> Unit = {},
    onClearDate: () -> Unit = {},
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    expanded: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    voicePhase: VoicePhase,
    voiceLevel: Float,
    onMicClick: () -> Unit,
    reduceDynamicBlur: Boolean = false,
    reduceAnimations: Boolean = false,
    designStyle: DesignStyle = DesignStyle.Glass,
    modifier: Modifier = Modifier
) {
    val corners by animateDpAsState(
        if (expanded) 26.dp else 30.dp,
        if (reduceAnimations) snap() else tween(250),
        label = "glassCorners"
    )
    val handleAlpha by animateFloatAsState(
        if (expanded) 1f else 0f,
        if (reduceAnimations) snap() else tween(200),
        label = "handle"
    )
    val textInset by animateDpAsState(
        if (expanded) 14.dp else 0.dp,
        if (reduceAnimations) snap() else tween(250),
        label = "textInset"
    )
    val recording = voicePhase != VoicePhase.Idle
    val micColor by animateColorAsState(
        if (recording) Color(0xFF69ACFF) else Color(0xFFE4E4EA),
        if (reduceAnimations) snap() else tween(180),
        label = "micColor"
    )
    val micScale by animateFloatAsState(
        if (recording && !reduceAnimations) 1f + voiceLevel * 0.1f else 1f,
        if (reduceAnimations) snap() else tween(100),
        label = "micLevel"
    )

    val panelContent: @Composable BoxScope.() -> Unit = {
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(width = 36.dp, height = 3.dp)
                .graphicsLayer { alpha = handleAlpha }
                .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(2.dp))
        )
        // Keep the text field mounted during all focus, permission and mic changes.
        Row(
            Modifier.fillMaxSize().padding(start = 22.dp, end = 9.dp, top = textInset, bottom = if (expanded) 42.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.replace("\r", "").replace("\n", " ")) },
                modifier = Modifier.weight(1f).padding(end = 6.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChanged(it.isFocused) }
                    .semantics { contentDescription = "Hatırlatıcı metni" },
                readOnly = recording || saving,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = Color(0xFFF7F7F8), fontSize = 16.sp, lineHeight = 22.sp
                ),
                cursorBrush = SolidColor(Color(0xFF9AB9FF)),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                decorationBox = { field ->
                    Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                when (voicePhase) {
                                    VoicePhase.Idle -> "Hatırlatıcı ekle"
                                    VoicePhase.Listening -> "Türkçe dinleniyor…"
                                    VoicePhase.Finishing -> "Metin hazırlanıyor…"
                                },
                                color = if (recording) Color(0xFFA2C7FF) else Color(0xFFB8B8BF), fontSize = 16.sp
                            )
                        }
                        field()
                    }
                }
            )
            if (!expanded) IconButton(onClick = onPickDate, enabled = !recording && !saving,
                modifier = Modifier.size(42.dp).semantics { contentDescription = "Tarih ve saat ekle" }) {
                ActionIcon(ActionGlyph.Calendar, Modifier.size(21.dp), Color(0xFFBABAC3))
            }
            IconButton(
                onClick = onMicClick,
                enabled = voicePhase != VoicePhase.Finishing && !saving,
                modifier = Modifier.size(48.dp).semantics {
                    contentDescription = if (recording) "Dinlemeyi bitir ve kaydet" else "Türkçe sesle yaz"
                    stateDescription = when (voicePhase) {
                        VoicePhase.Idle -> "Mikrofon kapalı"
                        VoicePhase.Listening -> "Türkçe dinleniyor"
                        VoicePhase.Finishing -> "Metin hazırlanıyor"
                    }
                }
            ) {
                Box(
                    Modifier.size(40.dp).graphicsLayer { scaleX = micScale; scaleY = micScale }
                        .background(if (recording) Color(0xFF2D6099).copy(alpha = 0.32f + voiceLevel * 0.15f) else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (voicePhase == VoicePhase.Finishing && !reduceAnimations) {
                        CircularProgressIndicator(Modifier.size(23.dp), color = micColor, strokeWidth = 2.dp)
                    } else ActionIcon(ActionGlyph.Mic, Modifier.size(24.dp), micColor)
                }
            }
        }
        if (expanded) Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .padding(start = 16.dp, end = 10.dp, bottom = 6.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable(enabled = !recording && !saving, onClick = onPickDate).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(ActionGlyph.Calendar, Modifier.size(18.dp), Color(0xFF91B9FF))
                Text(alertAt?.let { reminderDate(it) } ?: "Tarih ve saat ekle", color = Color(0xFFBFCDE6), fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 9.dp))
            }
            if (alertAt != null) IconButton(onClick = onClearDate, enabled = !saving,
                modifier = Modifier.size(38.dp).semantics { contentDescription = "Tarih ve saati kaldır" }) {
                ActionIcon(ActionGlyph.Close, Modifier.size(17.dp), Color(0xFFAAAAB4))
            }
        }
    }

    if (designStyle == DesignStyle.Ice) {
        IcePanel(
            backdrop = backdrop,
            backdropPosition = backdropPosition,
            modifier = modifier,
            corners = corners,
            reduceDynamicBlur = reduceDynamicBlur,
            blurRadius = 38.dp,
            dense = false,
            content = panelContent
        )
    } else {
        GlassPanel(
            backdrop = backdrop,
            backdropPosition = backdropPosition,
            modifier = modifier,
            corners = corners,
            reduceDynamicBlur = reduceDynamicBlur,
            content = panelContent
        )
    }
}
