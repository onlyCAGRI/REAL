package com.cagri.real.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Compact home-only side actions for REAL's bottom composer.
 *
 * The existing GlassComposer stays authoritative for text/input behaviour. This composable
 * only owns the two small side buttons so ReminderListScreen does not absorb another large
 * stateful UI implementation (important on ART verifier-sensitive builds).
 */
@Composable
fun HomeCompactDockSideActions(
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    reduceDynamicBlur: Boolean,
    onCalendarClick: () -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactDockButton(
            backdrop = backdrop,
            backdropPosition = backdropPosition,
            reduceDynamicBlur = reduceDynamicBlur,
            description = "Takvimi aç",
            onClick = onCalendarClick
        ) {
            ActionIcon(ActionGlyph.Calendar, Modifier.size(19.dp), Color(0xFFF2F2F4))
        }

        CompactDockButton(
            backdrop = backdrop,
            backdropPosition = backdropPosition,
            reduceDynamicBlur = reduceDynamicBlur,
            description = "Sesle REAL oluştur",
            onClick = onMicClick
        ) {
            ActionIcon(ActionGlyph.Mic, Modifier.size(20.dp), Color(0xFFF2F2F4))
        }
    }
}

@Composable
private fun CompactDockButton(
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    reduceDynamicBlur: Boolean,
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    GlassPanel(
        backdrop = backdrop,
        backdropPosition = backdropPosition,
        modifier = Modifier.size(42.dp),
        corners = 21.dp,
        reduceDynamicBlur = reduceDynamicBlur
    ) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .matchParentSize()
                    .semantics { contentDescription = description }
            ) {
                content()
            }
        }
    }
}
