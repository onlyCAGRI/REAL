package com.cagri.real.ui.privacy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.cagri.real.R
import com.cagri.real.ui.connections.ConnectionNote
import com.cagri.real.ui.connections.ConnectionPill

/** A user-reviewed mail draft; no automatic send, account lookup or card/file attachment. */
@Composable
internal fun PrivacyRightsDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val address = "cagrigull@outlook.com"
    var feedback by remember { mutableStateOf<Int?>(null) }
    val deletionUrl = privacyDocumentUrl("/account/delete")
    PrivacyDetailsDialog(stringResource(R.string.real_privacy_rights),
        stringResource(R.string.real_privacy_rights_subtitle), onClose) {
        ConnectionNote(stringResource(R.string.real_privacy_rights_intro))
        ConnectionNote(stringResource(R.string.real_privacy_draft_note))
        listOf(R.string.real_privacy_request_copy, R.string.real_privacy_request_correct,
            R.string.real_privacy_request_delete, R.string.real_privacy_request_object,
            R.string.real_privacy_request_portable, R.string.real_privacy_request_review,
            R.string.real_privacy_request_withdraw).forEach { kind ->
            val label = stringResource(kind)
            ConnectionPill(label, {
                val body = context.getString(R.string.real_privacy_rights_email_body, label)
                val uri = Uri.parse("mailto:$address?subject=" + Uri.encode("REAL data request") +
                    "&body=" + Uri.encode(body))
                feedback = if (runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, uri)) }.isFailure)
                    R.string.real_privacy_open_failed else null
            })
        }
        ConnectionNote(address)
        ConnectionPill(stringResource(R.string.real_privacy_copy_email), {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            feedback = if (clipboard != null && runCatching {
                clipboard.setPrimaryClip(ClipData.newPlainText("REAL privacy contact", address))
            }.isSuccess) R.string.real_privacy_copied else R.string.real_privacy_open_failed
        })
        ConnectionPill(stringResource(R.string.real_privacy_delete_web), {
            feedback = if (runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(deletionUrl)))
            }.isFailure) R.string.real_privacy_open_failed else null
        })
        feedback?.let { ConnectionNote(stringResource(it)) }
        PrivacyDocumentLinks()
    }
}
