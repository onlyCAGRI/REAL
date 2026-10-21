package com.cagri.real.calendar

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cagri.real.data.ReminderItem
import com.cagri.real.platform.android.share.CardShareClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun CalendarPublicationPrompt(state: CalendarConnectionState, reminders: List<ReminderItem>, reduceDynamicBlur: Boolean = false) {
    val id = state.publicationCardId ?: return
    val card = reminders.firstOrNull { it.id == id } ?: return
    var busy by remember(id) { mutableStateOf(false) }
    var files by remember(id) { mutableStateOf(true) }
    var location by remember(id) { mutableStateOf(false) }
    var voice by remember(id) { mutableStateOf(false) }
    var journal by remember(id) { mutableStateOf(false) }
    var message by remember(id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val latestCard = rememberUpdatedState(card)
    fun publish() {
        if (busy) return
        busy = true; message = null
        scope.launch {
            try {
                state.publicationStatus = CalendarCardPublisher(state).publish(latestCard.value,
                    CardShareClient.Options(files, location, voice, journal))
                state.refresh()
                state.publicationCardId = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message = error.message ?: "Kart REAL'de kaydedildi; bağlantı eklenemedi. Yeniden deneyebilirsin." }
            finally { busy = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) publish() else message = "Yazma izni verilmedi. İçeriğin REAL'de duruyor; bağlantı eklenmedi."
    }
    // A plain bottom sheet like the notification question: just the question and two answers.
    fun close() { if (!busy) state.publicationCardId = null }
    androidx.compose.ui.window.Dialog(onDismissRequest = ::close, properties = androidx.compose.ui.window.DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false
    )) {
        val window = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        com.cagri.real.ui.components.PermissionSheet(
            visible = shown,
            title = "Takvime web bağlantısı eklensin mi?",
            description = "",
            onAllow = { if (state.provider.canWrite()) publish() else permission.launch(Manifest.permission.WRITE_CALENDAR) },
            onLater = ::close,
            primaryLabel = if (busy) "Ekleniyor…" else "Evet, bağlantı ekle",
            secondaryLabel = "Hayır, REAL'de kalsın",
            glyph = null,
            primaryEnabled = !busy,
            extraContent = {
                message?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 13.sp, lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 12.dp)) }
            }
        )
    }
}
