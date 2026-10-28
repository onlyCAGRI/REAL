package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup

/** A single detached gear for the card title's text tools, with no contribution to grid measurement. */
@Composable
internal fun CardTextSettingsControl(onSettings: () -> Unit) {
    val offset = with(LocalDensity.current) { -32.dp.roundToPx() }
    Popup(alignment = Alignment.TopEnd, offset = IntOffset(0, offset)) {
        IconButton(onClick = onSettings, modifier = Modifier.size(28.dp).liquidDockChip(RoundedCornerShape(14.dp))
            .semantics { contentDescription = t("Metin araçları") }) {
            ActionIcon(ActionGlyph.Settings, Modifier.size(15.dp), Color(0xFFB7B7C0))
        }
    }
}
