package com.cagri.real.ui.pano

import com.cagri.real.i18n.t
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import com.cagri.real.boards.PanoItem
import com.cagri.real.data.ChecklistItem
import com.cagri.real.data.boards.BoardRealContent
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.DetailChecklistBox
import kotlinx.coroutines.delay
import org.json.JSONObject

/** REAL's checklist editor with a board-content save boundary, never a reminder mutation. */
@Composable
internal fun BoardChecklistBlock(item: PanoItem, selected: Boolean, controller: PanoController, style: DesignStyle, editable: Boolean = false) {
    var draft by remember(item.id) { mutableStateOf(item.payload) }
    var dirty by remember(item.id) { mutableStateOf(false) }
    var base by remember(item.id) { mutableStateOf(item) }
    var newText by remember(item.id) { mutableStateOf("") }
    val focus = remember(item.id) { FocusRequester() }
    val payload = remember(draft) { JSONObject(draft) }
    val items = remember(draft) { BoardRealContent.checklist(payload) }
    fun update(next: JSONObject) {
        if (!editable || !controller.canEdit(item.boardId)) return
        if (!dirty) base = item
        draft = next.toString()
        dirty = true
    }
    fun updateItems(next: List<ChecklistItem>) = update(BoardRealContent.checklistPayload(payload, next))
    fun save() {
        if (dirty) {
            controller.content(base, JSONObject(draft))
            dirty = false
        }
    }
    LaunchedEffect(item.payload) { if (!dirty) draft = item.payload }
    LaunchedEffect(draft, dirty) { if (dirty) { delay(350); save() } }
    LaunchedEffect(selected) { if (!selected) save() }
    val latestSave by rememberUpdatedState { save() }
    DisposableEffect(item.id, controller) {
        controller.registerDraft(item.id) { latestSave() }
        onDispose { latestSave(); controller.unregisterDraft(item.id) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).onFocusChanged { if (!it.hasFocus) save() }) {
        if (selected) OutlinedTextField(payload.optString("title"),
            { update(JSONObject(draft).put("title", it.take(300))) },
            label = { Text(t("Title")) }, modifier = Modifier.fillMaxWidth())
        else payload.optString("title").takeIf { it.isNotBlank() }?.let { Text(it, color = Color.White) }
        DetailChecklistBox(style, selected, items, newText, { newText = it.take(1000) },
            onAddItem = {
                if (newText.trim().isNotEmpty() && items.size < 100) {
                    var id = System.currentTimeMillis()
                    while (items.any { it.id == id }) id++
                    updateItems(items + ChecklistItem(id, newText.trim()))
                    newText = ""
                    save()
                    controller.transform(item, item.transform.copy(height = item.transform.height + 52f))
                }
            },
            onItemTextChange = { id, text -> updateItems(items.map { if (it.id == id) it.copy(text = text.take(1000)) else it }) },
            onDeleteItem = { id -> updateItems(items.filterNot { it.id == id }); save() },
            onToggleItem = { id -> updateItems(items.map { if (it.id == id) it.toggleCompleted() else it }); save() },
            onRemoveChecklist = { dirty = false; controller.delete(item) }, onFocusField = {},
            firstItemFocusRequester = focus, reduceAnimations = true, savingDetail = false)
    }
}
