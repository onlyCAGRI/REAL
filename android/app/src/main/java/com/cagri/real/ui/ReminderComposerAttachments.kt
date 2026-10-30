package com.cagri.real.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.runtime.*
import com.cagri.real.data.AttachmentStorage
import com.cagri.real.data.AttachmentType
import com.cagri.real.data.ReminderAttachment
import kotlinx.coroutines.*

internal class ReminderAttachmentActions(
    val pick: () -> Unit,
    val remove: (Int) -> Unit,
    /** Full-screen Ekler: any file via the system document picker. */
    val pickFiles: () -> Unit,
    /** Full-screen Ekler: a photo taken in CardCameraSheet. */
    val addCameraPhoto: (java.io.File) -> Unit
)

@Composable
internal fun rememberReminderAttachmentActions(
    context: Context,
    scope: CoroutineScope,
    composerState: ReminderComposerState,
    draftAttachments: List<ReminderAttachment>,
    onDraftAttachmentsChange: (List<ReminderAttachment>) -> Unit
): ReminderAttachmentActions {
    var copyingPhotos by composerState.copyingPhotos
    var saving by composerState.saving
    var photoImportGeneration by composerState.photoImportGeneration
    val composerPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(3)
    ) { uris ->
        if (uris.isNotEmpty() && !copyingPhotos && !saving) {
            copyingPhotos = true
            val generation = photoImportGeneration
            scope.launch {
                val copied = mutableListOf<ReminderAttachment>()
                var adopted = false
                try {
                    withContext(Dispatchers.IO + NonCancellable) {
                        uris.take((3 - draftAttachments.count { it.type == AttachmentType.IMAGE }).coerceAtLeast(0)).forEach { uri ->
                            AttachmentStorage.copyUriToAttachments(context, uri)?.let(copied::add)
                        }
                    }
                    if (generation == photoImportGeneration) {
                        onDraftAttachmentsChange(draftAttachments + copied)
                        adopted = true
                    }
                } finally {
                    if (!adopted) withContext(Dispatchers.IO + NonCancellable) { AttachmentStorage.deleteAttachmentFiles(context, copied) }
                    copyingPhotos = false
                }
            }
        }
    }
    val handlePickComposerAttachment: () -> Unit = {
        if (draftAttachments.count { it.type == AttachmentType.IMAGE } < 3 && !copyingPhotos && !saving) {
            composerPhotoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }
    // Files and camera shots go through the same import path the card detail uses.
    fun importInto(sources: List<() -> ReminderAttachment?>, cleanup: () -> Unit = {}) {
        if (sources.isEmpty() || copyingPhotos || saving) { cleanup(); return }
        copyingPhotos = true
        val generation = photoImportGeneration
        scope.launch {
            val copied = mutableListOf<ReminderAttachment>()
            var adopted = false
            try {
                withContext(Dispatchers.IO + NonCancellable) {
                    sources.forEach { source -> runCatching(source).getOrNull()?.let(copied::add) }
                    cleanup()
                }
                if (generation == photoImportGeneration) {
                    onDraftAttachmentsChange(draftAttachments + copied)
                    adopted = true
                }
            } finally {
                if (!adopted) withContext(Dispatchers.IO + NonCancellable) { AttachmentStorage.deleteAttachmentFiles(context, copied) }
                copyingPhotos = false
            }
        }
    }
    val composerFilePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val uris = buildList {
                data.data?.let(::add)
                data.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(::add) }
            }.distinct().take(20)
            importInto(uris.map { uri -> { AttachmentStorage.importUri(context, uri, data.flags) } })
        }
    }
    val handlePickFiles: () -> Unit = {
        if (!copyingPhotos && !saving) {
            composerFilePickerLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            })
        }
    }
    val handleCameraPhoto: (java.io.File) -> Unit = { file ->
        if (draftAttachments.count { it.type == AttachmentType.IMAGE } >= 3) file.delete()
        else importInto(listOf { AttachmentStorage.importCameraPhoto(context, file) }) { file.delete() }
    }
    val handleRemoveDraftAttachment: (Int) -> Unit = { index ->
        if (index in draftAttachments.indices) {
            val item = draftAttachments[index]
            scope.launch(Dispatchers.IO + NonCancellable) { AttachmentStorage.deleteAttachmentFile(context, item.localPath) }
            onDraftAttachmentsChange(draftAttachments.filterIndexed { i, _ -> i != index })
        }
    }

    return ReminderAttachmentActions(handlePickComposerAttachment, handleRemoveDraftAttachment, handlePickFiles, handleCameraPhoto)
}
