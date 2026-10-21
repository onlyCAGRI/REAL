package com.cagri.real.ui.pano

import androidx.compose.runtime.staticCompositionLocalOf
import com.cagri.real.data.CustomCategory
import com.cagri.real.data.ReminderItem

/** Only account-local presentation metadata; a remote sender's category ID is never resolved locally. */
internal data class BoardRealPresentation(
    val reminders: List<ReminderItem> = emptyList(),
    val categories: List<CustomCategory> = emptyList(),
    val uid: String = "guest",
    val onPreviewPhoto: (String) -> Unit = {},
    val onPreviewCard: (com.cagri.real.boards.PanoItem) -> Unit = {},
    val onEditStack: (com.cagri.real.boards.PanoItem) -> Unit = {}
)
internal val LocalBoardRealPresentation = staticCompositionLocalOf { BoardRealPresentation() }
