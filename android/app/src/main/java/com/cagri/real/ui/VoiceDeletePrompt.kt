package com.cagri.real.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.RepeatDeleteScope
import com.cagri.real.speech.VoiceCardCallbacks
import com.cagri.real.speech.VoiceDeletePlan
import com.cagri.real.ui.components.DeleteReminderPrompt

@Composable
internal fun VoiceDeletePrompt(
    plan: VoiceDeletePlan,
    reminders: List<ReminderItem>,
    callbacks: VoiceCardCallbacks,
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    designStyle: DesignStyle,
    reduceDynamicBlur: Boolean,
    onDeleteMany: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler(onBack = onDismiss)
    fun confirm(scope: RepeatDeleteScope?) {
        val card = reminders.firstOrNull { it.id == plan.cardId }
        onDismiss()
        if (!plan.stillMatches(card)) {
            com.cagri.real.ui.components.RealToast.show(context, "Kart değişmiş; silinmedi. Komutu yeniden söyle.", long = true)
            return
        }
        if (scope != null) {
            val occurrence = plan.occurrenceMillis ?: return
            callbacks.deleteOccurrence?.invoke(plan.cardId, occurrence, scope)
        } else if (plan.occurrenceMillis != null) {
            com.cagri.real.ui.components.RealToast.show(context, "Tekrarlı kartı silmek için kart detayındaki tekrar seçimini kullan.", long = true)
        } else onDeleteMany(setOf(plan.cardId))
    }
    val scoped = plan.occurrenceMillis != null && callbacks.deleteOccurrence != null
    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f))
        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        .padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        DeleteReminderPrompt(
            backdrop = backdrop, backdropPosition = backdropPosition,
            onDismiss = onDismiss, onConfirm = { confirm(null) },
            designStyle = designStyle, reduceDynamicBlur = reduceDynamicBlur,
            onConfirmThisOccurrence = if (scoped) ({ confirm(RepeatDeleteScope.THIS_OCCURRENCE) }) else null,
            onConfirmThisAndFuture = if (scoped) ({ confirm(RepeatDeleteScope.THIS_AND_FUTURE) }) else null,
            occurrenceIsNext = scoped,
            reminderTitle = plan.title + (plan.occurrenceMillis?.let {
                " · " + java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm"))
            } ?: "")
        )
    }
}
