package com.cagri.real.platform.android.location

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.cagri.real.boards.BoardTransform
import com.cagri.real.platform.android.auth.AndroidBackend

/** The same Google Maps -> Share -> REAL handoff for reminders and board placements. */
object RealLocationShareFlow {
    fun startReminder(context: Context, reminderId: Long) = start(context, reminderId, null, null)
    fun startBoard(context: Context, boardId: String, transform: BoardTransform) = start(context, null, boardId, transform)

    private fun start(context: Context, reminderId: Long?, boardId: String?, transform: BoardTransform?) {
        val destination = if (boardId != null) com.cagri.real.content.ContentDestination.Board(boardId, checkNotNull(transform))
            else com.cagri.real.content.ContentDestination.Card(checkNotNull(reminderId))
        val request = com.cagri.real.content.ContentRequest(destination,
            AndroidBackend.auth.currentUser()?.id ?: "guest", "LOCATION", System.currentTimeMillis())
        if (!com.cagri.real.platform.android.content.PendingContentDestination.begin(context, request)) {
            Toast.makeText(context, "Konum seçimi başlatılamadı", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps"))
                .setPackage("com.google.android.apps.maps"))
        } catch (_: Exception) {
            com.cagri.real.platform.android.content.PendingContentDestination.cancel(context, request.token)
            Toast.makeText(context, "Google Haritalar açılamadı", Toast.LENGTH_SHORT).show()
        }
    }
}
