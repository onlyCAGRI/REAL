package com.cagri.real.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cagri.real.R
import com.cagri.real.ui.privacy.PrivacyDocumentLinks
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cagri.real.ai.*
import com.cagri.real.platform.android.ai.AndroidAiConsent
import com.cagri.real.ui.components.PermissionGlyph
import com.cagri.real.ui.components.PermissionSheet
import com.cagri.real.ui.theme.RealType
import com.cagri.real.ui.connections.ConnectionPill
import com.cagri.real.ui.privacy.PrivacyDetailsDialog

@Composable
fun AiConsentHost(uid: String?) {
    val coordinator=AndroidAiConsent.coordinator
    val pending by coordinator.prompt.collectAsState()
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner,uid) {
        coordinator.cancelPrompt()
        coordinator.setHostActive(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer=LifecycleEventObserver { _, event ->
            if(event==Lifecycle.Event.ON_START) coordinator.setHostActive(true)
            if(event==Lifecycle.Event.ON_STOP) coordinator.setHostActive(false)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); coordinator.setHostActive(false) }
    }
    pending?.takeIf { it.uid==uid }?.let { request ->
        AiExplanationDialog(request.feature, onAllow={ coordinator.decide(request.id,true) },
            onLater={ coordinator.decide(request.id,false) })
    }
}

@Composable
internal fun AiFeature.label() = when(this) {
    AiFeature.VOICE -> stringResource(R.string.real_privacy_ai_voice_label)
    AiFeature.MOOD -> stringResource(R.string.real_privacy_ai_mood_label)
    AiFeature.DISCOVERY -> stringResource(R.string.real_privacy_ai_discovery_label)
    AiFeature.DIAGNOSTIC -> stringResource(R.string.real_privacy_ai_diagnostic_label)
}
@Composable
internal fun AiFeature.disclosure() = when(this) {
    AiFeature.VOICE -> stringResource(R.string.real_privacy_ai_voice_data)
    AiFeature.MOOD -> stringResource(R.string.real_privacy_ai_mood_data)
    AiFeature.DISCOVERY -> stringResource(R.string.real_privacy_ai_discovery_data)
    AiFeature.DIAGNOSTIC -> stringResource(R.string.real_privacy_ai_diagnostic_data)
}

@Composable
internal fun AiFeature.shortDisclosure() = when(this) {
    AiFeature.VOICE -> stringResource(R.string.real_privacy_ai_voice_short)
    AiFeature.MOOD -> stringResource(R.string.real_privacy_ai_mood_short)
    AiFeature.DISCOVERY -> stringResource(R.string.real_privacy_ai_discovery_short)
    AiFeature.DIAGNOSTIC -> stringResource(R.string.real_privacy_ai_diagnostic_short)
}
@Composable
internal fun AiFeature.consentTitle() = when(this) {
    AiFeature.VOICE -> stringResource(R.string.real_privacy_ai_voice_title)
    AiFeature.MOOD -> stringResource(R.string.real_privacy_ai_mood_title)
    AiFeature.DISCOVERY -> stringResource(R.string.real_privacy_ai_discovery_title)
    AiFeature.DIAGNOSTIC -> stringResource(R.string.real_privacy_ai_diagnostic_title)
}

@Composable
private fun AiDetailSection(title: String, body: String) {
    Text(title,style=RealType.cardTitle,color=Color.White)
    Text(body,style=RealType.cardBody,color=Color.White.copy(alpha=.72f))
}

/**
 * Katman 1 (izin): kısa açıklama + İzin ver / Şimdi değil. Kaydırma yok, ekrana sığar.
 * Katman 2 (Ayrıntılar): geniş okuma ekranı. Geri, kararı değiştirmeden kısa panele döner.
 */
@Composable
internal fun AiExplanationDialog(feature: AiFeature, onAllow: () -> Unit, onLater: () -> Unit, informational: Boolean=false) {
    var showDetails by remember(feature) { mutableStateOf(false) }
    if(informational || showDetails) {
        AiDetailsSheet(feature,onClose={ if(informational) onLater() else showDetails=false })
        return
    }
    Dialog(onDismissRequest=onLater, properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        PermissionSheet(visible=true, title=feature.consentTitle(), description=feature.shortDisclosure(),
            glyph=PermissionGlyph.Brand, primaryLabel=stringResource(R.string.real_privacy_ai_allow), secondaryLabel=stringResource(R.string.real_privacy_ai_not_now),
            onAllow=onAllow,onLater=onLater,
            extraContent={
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.real_privacy_ai_optional),
                    style=RealType.cardMetaPrimary,color=Color.White.copy(alpha=.55f),textAlign=TextAlign.Center,
                    modifier=Modifier.fillMaxWidth().padding(horizontal=6.dp))
                Spacer(Modifier.height(12.dp))
                ConnectionPill(stringResource(R.string.real_privacy_ai_details),{ showDetails=true },small=true)
            })
    }
}

@Composable
private fun AiDetailsSheet(feature: AiFeature, onClose: () -> Unit) {
    PrivacyDetailsDialog(title=feature.label(), subtitle=stringResource(R.string.real_privacy_ai_detail_subtitle), onBack=onClose) {
        AiDetailSection(stringResource(R.string.real_privacy_ai_sent_title),feature.disclosure())
        AiDetailSection(stringResource(R.string.real_privacy_ai_processor_title),if(feature==AiFeature.VOICE || feature==AiFeature.MOOD)
            stringResource(R.string.real_privacy_ai_private_processor)
            else stringResource(R.string.real_privacy_ai_discovery_processor))
        AiDetailSection(stringResource(R.string.real_privacy_ai_location_title), stringResource(R.string.real_privacy_ai_location_body))
        AiDetailSection(stringResource(R.string.real_privacy_ai_retention_title),if(feature==AiFeature.VOICE || feature==AiFeature.MOOD)
            stringResource(R.string.real_privacy_ai_private_retention)
            else stringResource(R.string.real_privacy_ai_discovery_retention))
        if(feature==AiFeature.VOICE) AiDetailSection(stringResource(R.string.real_privacy_ai_audio_title),
            stringResource(R.string.real_privacy_ai_audio_body))
        if(feature==AiFeature.MOOD) AiDetailSection(stringResource(R.string.real_privacy_ai_history_title),
            stringResource(R.string.real_privacy_ai_history_body))
        AiDetailSection(stringResource(R.string.real_privacy_ai_withdraw_title),
            stringResource(R.string.real_privacy_ai_withdraw_body))
        PrivacyDocumentLinks()
    }
}
