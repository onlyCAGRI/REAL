package com.cagri.real.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cagri.real.R
import com.cagri.real.ui.privacy.PrivacyDocumentLinks
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.ai.AiFeature
import com.cagri.real.platform.android.ai.AndroidAiConsent
import com.cagri.real.platform.android.auth.AndroidBackend
import com.cagri.real.ui.connections.*
import com.cagri.real.ui.privacy.PrivacyDetailsDialog
import com.cagri.real.ui.privacy.PrivacyDisclosureRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AiPrivacyPanel(uid: String, enabled: Boolean) {
    val readError=stringResource(R.string.real_privacy_read_error)
    val writeError=stringResource(R.string.real_privacy_write_error)
    val accountChanged=stringResource(R.string.real_privacy_account_changed)
    val scope=rememberCoroutineScope()
    val revision by AndroidAiConsent.coordinator.revision.collectAsState()
    var states by remember(uid) { mutableStateOf<Map<AiFeature,Boolean>>(emptyMap()) }
    var decisions by remember(uid) { mutableStateOf<Map<AiFeature, AndroidAiConsent.Decision?>>(emptyMap()) }
    var busy by remember(uid) { mutableStateOf(false) }
    var expanded by remember(uid) { mutableStateOf<AiFeature?>(null) }
    var info by remember(uid) { mutableStateOf<AiFeature?>(null) }
    var error by remember(uid) { mutableStateOf<String?>(null) }
    suspend fun refresh() {
        states=AiFeature.entries.associateWith { AndroidAiConsent.coordinator.allowed(uid,it) }
        decisions=AiFeature.entries.associateWith { AndroidAiConsent.latestDecision(uid,it) }
    }
    LaunchedEffect(uid,revision) {
        try { refresh() }
        catch(cancelled: CancellationException) { throw cancelled }
        catch(_:Exception) { error=readError }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ConnectionNote(stringResource(R.string.real_privacy_mic_note))
        AiFeature.entries.forEach { feature ->
            PrivacyDisclosureRow(title = feature.label(), expanded = expanded == feature,
                summary = if (states[feature] == true) stringResource(R.string.real_privacy_approved) else stringResource(R.string.real_privacy_ask_first),
                onToggle = { expanded = if (expanded == feature) null else feature }) {
                Text(feature.shortDisclosure(), color = Color.White.copy(alpha = .72f),
                    style = com.cagri.real.ui.theme.RealType.cardBody)
                decisions[feature]?.let { decision ->
                    val date = decision.decidedAtEpochMillis?.let {
                        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                            .format(java.util.Date(it))
                    }
                    Text(if (date != null) stringResource(R.string.real_privacy_decision_date, date, decision.disclosureVersion)
                        else stringResource(R.string.real_privacy_decision_unknown),
                        color = Color.White.copy(alpha = .55f), fontSize = 12.sp)
                }
                ConnectionPill(stringResource(R.string.real_privacy_details), { info = feature }, enabled = !busy)
                if (states[feature] == true) ConnectionPill(stringResource(R.string.real_privacy_revoke), {
                    if (!busy) {
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                check(AndroidBackend.auth.currentUser()?.id == uid) { accountChanged }
                                AndroidAiConsent.coordinator.revoke(uid, feature)
                                refresh()
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { error = writeError }
                            finally { busy = false }
                        }
                    }
                }, enabled = enabled && !busy)
            }
        }
        error?.let { ConnectionNote(it) }
    }
    info?.let { feature -> AiExplanationDialog(feature,{ info=null },{ info=null },informational=true) }
}

/** The settings page uses the same reading surface as data use; it never grants permission. */
@Composable
fun AiPrivacyDialog(uid: String?, onClose: () -> Unit) {
    PrivacyDetailsDialog(title = stringResource(R.string.real_privacy_ai), subtitle = stringResource(R.string.real_privacy_subtitle), onBack = onClose) {
        if (uid != null) {
            key(uid) { AiPrivacyPanel(uid, enabled = true) }
        } else {
            ConnectionNote(stringResource(R.string.real_privacy_signin_note))
            var info by remember { mutableStateOf<AiFeature?>(null) }
            AiFeature.entries.forEach { feature ->
                ConnectionPill(feature.label(), { info = feature })
            }
            info?.let { feature ->
                AiExplanationDialog(feature, { info = null }, { info = null }, informational = true)
            }
        }
        PrivacyDocumentLinks()
    }
}
