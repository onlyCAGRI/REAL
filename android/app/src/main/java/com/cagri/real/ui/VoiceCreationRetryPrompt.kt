package com.cagri.real.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Retry only unsaved decoded intents; never rerun the AI or duplicate already saved cards. */
@Composable
internal fun VoiceCreationRetryPrompt(busy: Boolean, onRetry: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = Color(0xF21C1C1E), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("Kaydedilemeyen kartların bilgileri korundu.", color = Color.White)
            Row {
                TextButton(enabled = !busy, onClick = onRetry) { Text("Kalanları tekrar dene") }
                TextButton(enabled = !busy, onClick = onDismiss) { Text("Kapat") }
            }
        }
    }
}
