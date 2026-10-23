package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import com.cagri.real.ui.DesignStyle
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
    designStyle: DesignStyle = DesignStyle.Glass, reduceDynamicBlur: Boolean = false,
    /** When set, the prompt asks which part of a repeating series to delete instead of a plain yes/no. */
    onConfirmThisOccurrence: (() -> Unit)? = null,
    onConfirmThisAndFuture: (() -> Unit)? = null,
    /** True when no Planner day was selected (Home / Category): the target is the next upcoming repeat. */
    occurrenceIsNext: Boolean = false,
    reminderTitle: String? = null
) {
    val scoped = onConfirmThisOccurrence != null && onConfirmThisAndFuture != null
    DesignPanel(backdrop, backdropPosition, designStyle = designStyle, reduceDynamicBlur = reduceDynamicBlur, liquidDynamic = true,
        modifier = modifier.fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            .semantics { paneTitle = if (scoped) t("Tekrarlayan etkinlik silinsin mi?") else t("Hatırlatıcı silinsin mi?") }, corners = 26.dp
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (scoped) t("Tekrarlayan etkinlik silinsin mi?") else t("Hatırlatıcı silinsin mi?"), color = Color(0xFFF5F5F8), fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp, lineHeight = 23.sp)
            reminderTitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color(0xFFF5F5F8), fontSize = 14.sp, lineHeight = 20.sp, maxLines = 3)
            }
            Text(if (scoped && occurrenceIsNext) t("Yalnızca sıradaki tekrarı ya da sıradaki tekrardan itibaren tüm tekrarları silebilirsin. Önceki tekrarlar korunur. Bu işlem geri alınamaz.")
                else if (scoped) t("Yalnızca seçtiğin günü ya da seçtiğin günden itibaren tüm tekrarları silebilirsin. Önceki günler korunur. Bu işlem geri alınamaz.")
                else t("Bu not listenden ve bildirim panelinden kaldırılacak. Bu işlem geri alınamaz."),
                color = Color(0xFFBEBEC8), fontSize = 13.sp, lineHeight = 19.sp)
            if (scoped) {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onConfirmThisOccurrence!!,
                        modifier = Modifier.fillMaxWidth().height(46.dp).background(Color(0xFFFF858D).copy(alpha = 0.14f), RoundedCornerShape(23.dp))
                    ) { Text(if (occurrenceIsNext) t("Sadece sıradaki tekrarı sil") else t("Sadece bu günü sil"), color = Color(0xFFFFA4AA), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    TextButton(onClick = onConfirmThisAndFuture!!,
                        modifier = Modifier.fillMaxWidth().height(46.dp).background(Color(0xFFFF858D).copy(alpha = 0.14f), RoundedCornerShape(23.dp))
                    ) { Text(t("Bu ve sonraki tüm tekrarları sil"), color = Color(0xFFFFA4AA), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    TextButton(onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(46.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(23.dp))
                    ) { Text(t("Vazgeç"), color = Color(0xFFE9E9EF), fontSize = 14.sp) }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(46.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(23.dp))
                    ) { Text(t("Vazgeç"), color = Color(0xFFE9E9EF), fontSize = 14.sp) }
                    TextButton(onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(46.dp).background(Color(0xFFFF858D).copy(alpha = 0.14f), RoundedCornerShape(23.dp))
                    ) { Text(t("Sil"), color = Color(0xFFFFA4AA), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
