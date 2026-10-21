package com.cagri.real.ui.pano

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cagri.real.boards.BoardBlockType
import com.cagri.real.content.ContentRequest
import com.cagri.real.data.*
import com.cagri.real.data.boards.BoardRealContent
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.components.*
import kotlinx.coroutines.*
import org.json.JSONObject

/** Destination adapter only: search, picker, recording, mood and parsing remain REAL components. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PanoContentFlow(request: ContentRequest, style: DesignStyle, onDismiss: () -> Unit,
    onSave: suspend (BoardBlockType, JSONObject, () -> Boolean) -> Unit) {
    val type = BoardBlockType.valueOf(request.kind)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember(request.token) { mutableStateOf(false) }
    var error by remember(request.token) { mutableStateOf<String?>(null) }
    var fileLaunched by rememberSaveable(request.token) { mutableStateOf(false) }
    var text by remember(request.token) { mutableStateOf("") }
    var mood by remember(request.token) { mutableStateOf(JournalMood.NEUTRAL) }
    var alive by remember(request.token) { mutableStateOf(true) }
    DisposableEffect(request.token) { onDispose { alive = false } }
    fun dismiss() { if (!busy) onDismiss() }
    fun submit(prepare: suspend () -> JSONObject, cleanup: () -> Unit = {}) {
        if (busy || !alive) { cleanup(); return }
        busy = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Once a result is accepted, finish the app-owned copy/save even if Compose disposes.
            withContext(NonCancellable) {
                try {
                    val payload = prepare()
                    check(alive) { "Content destination was cancelled" }
                    onSave(type, payload) { alive }
                    if (alive) onDismiss()
                } catch (e: Exception) { if (alive) error = e.message ?: "Content could not be added" }
                finally { cleanup(); busy = false }
            }
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) dismiss()
        else {
            var imported: ReminderAttachment? = null
            submit(prepare = {
                withContext(Dispatchers.IO) {
                    AttachmentStorage.importUri(context, uri, result.data?.flags ?: 0,
                        maxCopyBytes = com.cagri.real.platform.android.boards.BoardAssets.MAX_BYTES.toLong(), requireLocalCopy = true)
                        .also { imported = it }.let(BoardRealContent::attachmentPayload)
                }
            }, cleanup = { imported?.let { AttachmentStorage.deleteAttachmentFiles(context, listOf(it)) } })
        }
    }
    fun pickFile() { filePicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE); setType("*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }) }
    when (type) {
        BoardBlockType.MUSIC -> MusicSearchSheet(style, ::dismiss) { value -> submit({ BoardRealContent.musicPayload(value) }) }
        BoardBlockType.MEDIA -> MediaSearchSheet(style, ::dismiss) { value -> submit({ BoardRealContent.mediaPayload(value) }) }
        BoardBlockType.MOOD -> MoodV3FullScreen(mood, { mood = it }, ::dismiss,
            onSave = { value, details -> submit({ BoardRealContent.moodPayload(value, details) }) }, reduceAnimations = true)
        BoardBlockType.VOICE -> ModalBottomSheet(onDismissRequest = ::dismiss) {
            VoiceRecorderInline(emptyList(), onRecorded = { recording ->
                submit({ BoardRealContent.voicePayload(recording) },
                    { VoiceRecordingStorage.deleteRecordingFile(context, recording.localPath) })
            }, onRemove = {}, enabled = !busy, modifier = Modifier.padding(20.dp))
            Spacer(Modifier.height(24.dp))
        }
        BoardBlockType.ATTACHMENT -> {
            LaunchedEffect(request.token) { if (!fileLaunched) { fileLaunched = true; pickFile() } }
            ModalBottomSheet(onDismissRequest = ::dismiss) {
                TextButton(onClick = ::pickFile, enabled = !busy) { Text("Choose file") }
                Spacer(Modifier.height(24.dp))
            }
        }
        BoardBlockType.LINK -> ModalBottomSheet(onDismissRequest = ::dismiss) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text("Link / text snippet") }, enabled = !busy)
                TextButton(enabled = !busy && text.isNotBlank(), onClick = {
                    submit({
                        val parsed = IncomingContentParser.text(text)
                        if (parsed.referenceUrl != null) JSONObject().put("referenceUrl", parsed.referenceUrl)
                        else BoardRealContent.attachmentPayload(parsed.attachments.single())
                    })
                }) { Text("Add") }
            }
        }
        else -> kotlin.error("Unsupported content picker")
    }
    error?.let { message -> com.cagri.real.ui.components.RealAlertDialog(onDismissRequest = { error = null }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
}
