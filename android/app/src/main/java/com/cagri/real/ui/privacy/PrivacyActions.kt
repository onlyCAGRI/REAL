package com.cagri.real.ui.privacy

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.cagri.real.R
import com.cagri.real.ui.connections.ConnectionNote
import com.cagri.real.ui.connections.ConnectionPill

private const val PrivacyOrigin = "https://real-privacy-staging.realapp.workers.dev"

/** Language selects copy only; it is not evidence of residence or a consent decision. */
@Composable
internal fun privacyDocumentUrl(path: String): String {
    val language = if (LocalConfiguration.current.locales[0].language == "tr") "tr" else "en"
    return "$PrivacyOrigin$path?lang=$language"
}

@Composable
internal fun PrivacyDocumentLinks(includeDeviceSettings: Boolean = false) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    val links = listOf(
        R.string.real_privacy_policy to privacyDocumentUrl("/privacy"),
        R.string.real_privacy_regions to privacyDocumentUrl("/regions"),
        R.string.real_privacy_kvkk to privacyDocumentUrl("/kvkk"),
        R.string.real_privacy_web_rights to privacyDocumentUrl("/rights")
    )
    links.forEach { (label, url) ->
        ConnectionPill(stringResource(label), {
            failed = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isFailure
        })
    }
    if (includeDeviceSettings) ConnectionPill(stringResource(R.string.real_privacy_permission_settings), {
        failed = runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)))
        }.isFailure
    })
    if (failed) ConnectionNote(stringResource(R.string.real_privacy_open_failed))
}
