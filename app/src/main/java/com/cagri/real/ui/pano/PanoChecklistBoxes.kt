package com.cagri.real.ui.pano

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.ChecklistItem
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon

/**
 * Checklist rows as plain rounded boxes: the text is written inside the box, with no circle beside it.
 * Done state = dimmed box, strike-through and a trailing check mark. Data model and callbacks are unchanged.
 */
@Composable
internal fun PanoChecklistBoxes(
    items: List<ChecklistItem>, editing: Boolean, canToggle: Boolean,
    newText: String = "", onNewText: (String) -> Unit = {}, onAdd: () -> Unit = {},
    onTextChange: (Long, String) -> Unit = { _, _ -> }, onDelete: (Long) -> Unit = {}, onToggle: (Long) -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { entry ->
            key(entry.id) {
                val done = entry.isCompleted
                if (editing) {
                    PanoFieldBox(entry.text, { onTextChange(entry.id, it) }, "Item", strike = done) {
                        PanoIconButton(ActionGlyph.Check, "Toggle done", if (done) PanoAccent else Color.White.copy(alpha = 0.35f)) { onToggle(entry.id) }
                        PanoIconButton(ActionGlyph.Close, "Delete item", PanoDanger) { onDelete(entry.id) }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().alpha(if (done) 0.55f else 1f).panoSurface(14.dp)
                        .then(if (canToggle) Modifier.clickable { onToggle(entry.id) } else Modifier)
                        .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.text, color = if (done) PanoMuted else Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f),
                            textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None)
                        if (done) ActionIcon(ActionGlyph.Check, Modifier.size(15.dp), color = PanoAccent)
                    }
                }
            }
        }
        if (editing) PanoFieldBox(newText, onNewText, "New item", singleLine = true, onDone = onAdd) {
            PanoIconButton(ActionGlyph.Add, "Add item", if (newText.isBlank()) Color.White.copy(alpha = 0.35f) else PanoAccent) { onAdd() }
        }
    }
}
