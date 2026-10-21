package com.cagri.real.ui.pano

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp
import com.cagri.real.boards.*
import com.cagri.real.data.*
import com.cagri.real.data.boards.BoardRealContent
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.components.*
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Only adapts a board save boundary to existing REAL detail renderers. */
@Composable
internal fun BoardCanonicalBlock(item: PanoItem, controller: PanoController, style: DesignStyle, editable: Boolean = false) {
    val payload = remember(item.payload) { item.content() }
    val assetId = payload.optString("assetId")
    var localPath by remember(item.id, assetId) { mutableStateOf(
        if (assetId.isBlank()) null else runCatching { controller.repository.assets.file(assetId) }
            .getOrNull()?.takeIf { it.isFile }?.path) }
    var failed by remember(item.id, assetId) { mutableStateOf(false) }
    var retry by remember(item.id) { mutableIntStateOf(0) }
    LaunchedEffect(item.id, assetId, retry) {
        if (assetId.isNotBlank() && localPath == null) {
            try { localPath = controller.repository.assets.cached(item.boardId, assetId).path; failed = false }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed = true }
        }
    }
    if (assetId.isNotBlank() && localPath == null) {
        if (failed) TextButton(onClick = { retry++ }) { Text("Retry file") }
        else Text("Loading file…", color = Color.Gray)
        return
    }
    val hydrated = JSONObject(payload.toString()).apply {
        localPath?.let { path -> optJSONObject("attachment")?.put("localPath", path); optJSONObject("voice")?.put("localPath", path) }
    }
    val presentation = LocalBoardRealPresentation.current
    val backdrop = rememberGraphicsLayer()
    when (item.type) {
        BoardBlockType.MUSIC -> MusicContentSection(listOf(BoardRealContent.music(hydrated)), style, false, {})
        BoardBlockType.MEDIA -> MediaContentSection(listOf(BoardRealContent.media(hydrated)), style, false, {})
        BoardBlockType.VOICE -> VoicePlaybackRow(BoardRealContent.voice(hydrated), compact = true)
        BoardBlockType.MOOD -> {
            val (mood, details) = BoardRealContent.mood(hydrated)
            var editing by remember(item.id) { mutableStateOf(false) }
            var draft by remember(item.id, mood) { mutableStateOf(mood) }
            PanoInfoBox {
                MoodV2Face(mood, 64.dp)
                Text(mood.label, color = Color.White)
                details?.let { d ->
                    Text(d.scope.label, color = Color.Gray)
                    if (d.descriptors.isNotEmpty()) Text(d.descriptors.joinToString { moodDescriptorLabelV2(it) }, color = Color.LightGray)
                    if (d.factors.isNotEmpty()) Text(d.factors.joinToString { moodFactorLabelV2(it) }, color = Color.LightGray)
                }
                if (editable) PanoPill("Edit") { editing = true }
            }
            if (editing && editable) MoodV3FullScreen(draft, { draft = it }, { editing = false },
                onSave = { value, d -> controller.content(item, BoardRealContent.moodPayload(value, d)); editing = false },
                initialDetails = details, reduceAnimations = true)
        }
        BoardBlockType.ATTACHMENT, BoardBlockType.LINK -> {
            val attachment = hydrated.optJSONObject("attachment")?.let { BoardRealContent.attachment(hydrated) }
            if (attachment?.type == AttachmentType.IMAGE) com.cagri.real.ui.AttachmentMediaSection(
                listOf(attachment), style, false, onOpen = {}, onRemove = {})
            else ReminderContentSections(listOfNotNull(attachment), hydrated.optString("referenceUrl").takeIf { it.isNotBlank() },
                false, style, backdrop, backdropPosition = { androidx.compose.ui.geometry.Offset.Zero },
                onRemove = {}, onRemoveLink = {}, onOpenAttachment = { controller.repository.assets.open(it) }, allowSnippetEditing = false)
        }
        else -> Unit
    }
}
