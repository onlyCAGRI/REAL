package com.cagri.real.ui.sharedcategories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cagri.real.data.*
import com.cagri.real.sharedcategories.*
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.components.*
import kotlinx.coroutines.CancellationException

/** Canonical REAL read-only sections; no local reminder ID is ever used to open/edit an owner's card. */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
internal fun SharedGoalDetailDialog(api: SharedCategoryApi, categoryId: String, goal: SharedCategoryGoal, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var card by remember(goal.recordId) { mutableStateOf(goal.card) }
    var busy by remember(goal.recordId) { mutableStateOf(true) }
    var unavailable by remember(goal.recordId) { mutableStateOf(false) }
    val backdrop = androidx.compose.ui.graphics.rememberGraphicsLayer()
    LaunchedEffect(api, goal.recordId, goal.raw) {
        try { val result = api.hydrate(categoryId, goal); card = result.first; unavailable = result.second }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { unavailable = true }
        finally { busy = false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            com.cagri.real.ui.components.RealBackButton(onDismiss)
            Text(card.text, color = Color.White, fontSize = 25.sp)
            if (busy) com.cagri.real.ui.components.RealSpinner(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            if (card.checklistItems.isNotEmpty()) {
                com.cagri.real.ui.DetailChecklistBox(DesignStyle.LiquidGlass, false, card.checklistItems, "", {}, {}, { _, _ -> }, {}, {}, {}, {},
                    remember { androidx.compose.ui.focus.FocusRequester() }, true, false)
            }
            card.location?.let { place ->
                Text("⌖ ${place.placeName}", color = Color.White, modifier = Modifier.clickable { openReminderLocationInMaps(context, place) })
                if (place.latitude != null && place.longitude != null) ReminderLocationMap(place, Modifier.fillMaxWidth().height(180.dp)) { openReminderLocationInMaps(context, place) }
            }
            MusicContentSection(card.musicBlocks, DesignStyle.LiquidGlass, false, {})
            MediaContentSection(card.mediaBlocks, DesignStyle.LiquidGlass, false, {})
            card.cardStack?.let { CardStackContentSection(it, DesignStyle.LiquidGlass, false, onUpdate = {}, onRemove = {}) }
            com.cagri.real.ui.AttachmentMediaSection(card.attachments.filter { it.type == AttachmentType.IMAGE }, DesignStyle.LiquidGlass, false,
                onOpen = { runCatching { api.open(it) }.onFailure { com.cagri.real.ui.components.RealToast.show(context, it.message ?: "Dosya açılamadı.") } }, onRemove = {})
            ReminderContentSections(card.attachments, card.referenceUrl, false, DesignStyle.LiquidGlass, backdrop,
                backdropPosition = { androidx.compose.ui.geometry.Offset.Zero }, onRemove = {}, onRemoveLink = {},
                onOpenAttachment = { runCatching { api.open(it) }.onFailure { com.cagri.real.ui.components.RealToast.show(context, it.message ?: "Dosya açılamadı.") } }, allowSnippetEditing = false)
            card.voiceRecordings.forEach { VoicePlaybackRow(it, compact = true) }
            card.mood?.let { Text(it.label, color = Color.White) }
            card.journal?.let { Text(it.text, color = Color.White.copy(alpha = 0.8f)) }
            if (unavailable) Text("Bazı dosyalar şu anda açılamıyor. İnternet bağlantısını kontrol et.", color = Color.Gray)
            Spacer(Modifier.height(24.dp))
        }
    }
}
