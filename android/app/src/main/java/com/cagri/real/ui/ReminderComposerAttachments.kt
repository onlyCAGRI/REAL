package com.cagri.real.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.runtime.*
import com.cagri.real.data.AttachmentStorage
import com.cagri.real.data.ReminderAttachment
import kotlinx.coroutines.*

internal class ReminderAttachmentActions(val pick: () -> Unit, val remove: (Int) -> Unit)

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
                        uris.take((3 - draftAttachments.size).coerceAtLeast(0)).forEach { uri ->
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
        if (draftAttachments.size < 3 && !copyingPhotos && !saving) {
            composerPhotoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }
    val handleRemoveDraftAttachment: (Int) -> Unit = { index ->
        if (index in draftAttachments.indices) {
            val item = draftAttachments[index]
            scope.launch(Dispatchers.IO + NonCancellable) { AttachmentStorage.deleteAttachmentFile(context, item.localPath) }
            onDraftAttachmentsChange(draftAttachments.filterIndexed { i, _ -> i != index })
        }
    }

    return ReminderAttachmentActions(handlePickComposerAttachment, handleRemoveDraftAttachment)
}
