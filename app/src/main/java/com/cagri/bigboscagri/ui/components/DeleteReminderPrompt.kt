package com.cagri.bigboscagri.ui.components

import com.cagri.bigboscagri.ui.DesignStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DeleteReminderPrompt(
    backdrop: GraphicsLayer, backdropPosition: () -> Offset,
    onDismiss: () -> Unit, onConfirm: () -> Unit, modifier: Modifier = Modifier,
    designStyle: DesignStyle = DesignStyle.Glass, reduceDynamicBlur: Boolean = false
) {
    DesignPanel(backdrop, backdropPosition, designStyle = designStyle, reduceDynamicBlur = reduceDynamicBlur,
        modifier = modifier.fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            .semantics { paneTitle = "Hatırlatıcı silinsin mi?" }, corners = 26.dp
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Hatırlatıcı silinsin mi?", color = Color(0xFFF5F5F8), fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp, lineHeight = 23.sp)
            Text("Bu not listenden ve bildirim panelinden kaldırılacak. Bu işlem geri alınamaz.",
                color = Color(0xFFBEBEC8), fontSize = 13.sp, lineHeight = 19.sp)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(46.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(23.dp))
                ) { Text("Vazgeç", color = Color(0xFFE9E9EF), fontSize = 14.sp) }
                TextButton(onClick = onConfirm,
                    modifier = Modifier.weight(1f).height(46.dp).background(Color(0xFFFF858D).copy(alpha = 0.14f), RoundedCornerShape(23.dp))
                ) { Text("Sil", color = Color(0xFFFFA4AA), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
