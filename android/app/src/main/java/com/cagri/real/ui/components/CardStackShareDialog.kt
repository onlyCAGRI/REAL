package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.cagri.real.data.CardStackCard
import com.cagri.real.ui.DesignStyle
import java.io.File

@Composable
fun CardStackShareDialog(
    cards: List<CardStackCard>,
    designStyle: DesignStyle,
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    onDismiss: () -> Unit,
    onShare: (Set<String>) -> Unit
) {
    if (cards.isEmpty()) return

    var selectedIds by remember(cards) { mutableStateOf(cards.map { it.id }.toSet()) }
    val allSelected = selectedIds.size == cards.size

    Dialog(onDismissRequest = onDismiss) {
        DesignPanel(
            backdrop = backdrop,
            backdropPosition = backdropPosition,
            modifier = Modifier.fillMaxWidth().heightIn(max = 610.dp),
            corners = 28.dp,
            designStyle = designStyle,
            reduceDynamicBlur = true,
            liquidSheet = true
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Share cards", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("Choose the cards to share", color = Color(0xFFA7A7B1), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Text(
                        if (allSelected) "Clear selection" else "Select all",
                        color = Color(0xFFC9D3DB),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedIds = if (allSelected) emptySet() else cards.map { it.id }.toSet() }
                            .padding(horizontal = 9.dp, vertical = 7.dp)
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(cards, key = { _, card -> card.id }) { index, card ->
                        val selected = card.id in selectedIds
                        ShareCardRow(
                            card = card,
                            index = index,
                            selected = selected,
                            onClick = {
                                selectedIds = if (selected) selectedIds - card.id else selectedIds + card.id
                            }
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Cancel",
                        color = Color(0xFFB7B7BF),
                        fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    val enabled = selectedIds.isNotEmpty()
                    Text(
                        "Continue",
                        color = if (enabled) Color(0xFF91B9FF) else Color(0xFF62636C),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (enabled) Color(0x1A91B9FF) else Color(0x0DFFFFFF))
                            .clickable(enabled = enabled) { onShare(selectedIds) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareCardRow(
    card: CardStackCard,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val cardColor = Color(card.colorArgb ?: 0xFF242429.toInt())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardColor.copy(alpha = if (selected) 0.52f else 0.30f))
            .border(
                width = if (selected) 1.1.dp else 0.7.dp,
                color = if (selected) Color(0xFF91B9FF).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.10f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) Color(0xFF91B9FF) else Color(0xFF24242A))
                .border(0.7.dp, Color.White.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(if (selected) "✓" else "${index + 1}", color = if (selected) Color(0xFF101216) else Color(0xFFD1D2D8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.width(10.dp))

        if (!card.imageUri.isNullOrBlank()) {
            val model = remember(card.imageUri) {
                val path = card.imageUri.orEmpty()
                if (path.startsWith("/")) File(path) else Uri.parse(path)
            }
            AsyncImage(
                model = model,
                contentDescription = t("Card image"),
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(10.dp))
        }

        Column(Modifier.weight(1f)) {
            val title = card.title.trim().ifBlank { t("Card {0}", index + 1) }
            Text(title, color = Color(0xFFF0F0F3), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val preview = card.body.trim().ifBlank { if (!card.imageUri.isNullOrBlank()) "Image card" else "Empty card" }
            Text(preview, color = Color(0xFFB4B5BE), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
    }
}
