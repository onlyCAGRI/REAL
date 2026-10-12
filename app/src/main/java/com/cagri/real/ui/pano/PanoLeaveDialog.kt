package com.cagri.real.ui.pano

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.cagri.real.boards.PanoBoard

@Composable
internal fun PanoLeaveDialog(board: PanoBoard, controller: PanoController, onDismiss: () -> Unit, onLeft: () -> Unit) {
    var leaving by remember(board.id) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!leaving) onDismiss() }, containerColor = Color(0xFF161618),
        title = { Text("Leave board?", color = Color.White) },
        text = { Text("You will lose access to ${board.title}. Unsynced edits on this board will be discarded. Other members and original REAL cards are kept.", color = Color.LightGray) },
        confirmButton = { TextButton(enabled = !leaving, onClick = {
            leaving = true
            controller.run {
                try {
                    if (controller.editingBoard == board.id) controller.endEdit()
                    controller.repository.leaveBoard(board.id)
                    onLeft()
                } finally { leaving = false }
            }
        }) { Text(if (leaving) "Leaving…" else "Leave") } },
        dismissButton = { TextButton(enabled = !leaving, onClick = onDismiss) { Text("Cancel") } })
}
