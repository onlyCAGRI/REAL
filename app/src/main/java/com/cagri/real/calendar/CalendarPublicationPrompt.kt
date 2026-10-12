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
    CalendarGlassDialog(
        onDismiss = { if (!busy) state.publicationCardId = null },
        title = "Takvime web bağlantısı eklensin mi?",
        reduceBlur = reduceDynamicBlur,
        actions = {
            CalendarGlassButton(primary = true, enabled = !busy, onClick = {
                if (state.provider.canWrite()) publish() else permission.launch(Manifest.permission.WRITE_CALENDAR)
            }) { Text(if (busy) "Ekleniyor…" else "Evet, bağlantı ekle", fontWeight = FontWeight.Medium) }
            TextButton(
                enabled = !busy, onClick = { state.publicationCardId = null }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = CalendarMuted, disabledContentColor = CalendarMuted.copy(alpha = 0.5f))
            ) { Text("Hayır, REAL'de kalsın") }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Kartın REAL'de kaydedildi. Seçtiğin içeriğe bağlı bir web bağlantısı takvim açıklamasına eklenir.",
                color = Color(0xFFE4E5EA), fontSize = 15.sp, lineHeight = 22.sp
            )
            // Visibility warning in its own tinted block so it is read, not skimmed past.
            Text(
                "Linki bilen herkes bu içeriği görebilir; paylaşılan takvimin üyeleri de linki görür.",
                color = Color(0xFFFFD79A), fontSize = 13.sp, lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFD79A).copy(alpha = 0.10f)).padding(horizontal = 14.dp, vertical = 11.dp)
            )
            Text("PAYLAŞILACAK İÇERİK", color = CalendarMuted, fontSize = 11.sp, letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f))) {
                PublishChoice("Fotoğraflar ve dosyalar", files, !busy) { files = it }
                PublishDivider()
                PublishChoice("Konum", location, !busy) { location = it }
                PublishDivider()
                PublishChoice("Ses kayıtları", voice, !busy) { voice = it }
                PublishDivider()
                PublishChoice("Günlük", journal, !busy) { journal = it }
            }
            Text(
                "Başlık, checklist, CardStack metinleri ve medya bilgileri her zaman dahildir. Dosyaların kopyası oluşturulmaz; " +
                    "asıl dosya silinirse linkte de açılmaz. Link 30 gün geçerlidir, yayımlarken yenilenir. " +
                    "Hayır dersen yeni içerik yalnız REAL'de kalır; önceden yayımlanan içerik mevcut linkte kalır.",
                color = CalendarMuted, fontSize = 12.sp, lineHeight = 18.sp
            )
            message?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 13.sp, lineHeight = 19.sp) }
            if (busy) LinearProgressIndicator(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)),
                color = CalendarAccent, trackColor = Color.White.copy(alpha = 0.12f)
            )
        }
    }
}

@Composable
private fun PublishDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 52.dp).height(0.5.dp).background(Color.White.copy(alpha = 0.08f)))
}

@Composable
private fun PublishChoice(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onCheckedChange
        ).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked, enabled = enabled, onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = CalendarAccent, checkmarkColor = Color.Black,
                uncheckedColor = CalendarMuted, disabledCheckedColor = CalendarAccent.copy(alpha = 0.4f),
                disabledUncheckedColor = CalendarMuted.copy(alpha = 0.4f)
            )
        )
        Text(label, color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f), fontSize = 15.sp,
            modifier = Modifier.weight(1f).padding(start = 8.dp))
    }
}
