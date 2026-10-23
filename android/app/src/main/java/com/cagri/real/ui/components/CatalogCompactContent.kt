package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.CatalogCardDisplay

/** Reuses native artwork loaders; never adds a clickable provider link in compact modes. */
@Composable
internal fun CatalogCompactContent(
    title: String, display: CatalogCardDisplay, aspectRatio: Float,
    editing: Boolean, onRemove: () -> Unit,
    artwork: @Composable (Modifier) -> Unit
) {
    Box(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            artwork(Modifier.fillMaxWidth().aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(22.dp)).semantics { contentDescription = title })
            if (display == CatalogCardDisplay.COVER_TITLE) {
                Text(title, color = Color(0xFFF1F1F4), fontSize = 15.sp, lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (editing) {
            IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                ActionIcon(ActionGlyph.Close, Modifier.size(15.dp).semantics { contentDescription = t("İçeriği kaldır") }, Color.White)
            }
        }
    }
}
