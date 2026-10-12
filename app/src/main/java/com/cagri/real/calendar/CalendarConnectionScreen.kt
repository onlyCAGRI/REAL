package com.cagri.real.calendar

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.ReminderItem
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon

/**
 * Settings > Takvim bağlantısı. Three clearly separated jobs:
 *  1) which device calendars are SHOWN inside REAL (read),
 *  2) whether REAL plans are SENT to a calendar (write) — where to, and which plans,
 *  3) permissions and manual sync.
 * One save bar at the bottom; it is only active when something changed.
 */
@Composable
internal fun CalendarConnectionScreen(
    state: CalendarConnectionState, reminders: List<ReminderItem>, reduceDynamicBlur: Boolean, onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var draft by remember(state.options) { mutableStateOf(state.options) }
    var confirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    val readPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRevision++; state.refresh()
    }
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRevision++; state.refresh()
    }
    val readable = remember(permissionRevision, state.revision) { state.provider.canRead() }
    val writable = remember(permissionRevision, state.revision) { state.provider.canWrite() }
    val linkedExternalIds = remember(state.revision) {
        runCatching { CalendarCardLinks(state.store).associations().map { it.cardId }.toSet() }.getOrDefault(emptySet())
    }
    val planned = remember(reminders, linkedExternalIds) { reminders.filter {
        !it.isCompleted && it.id !in linkedExternalIds && (it.alertAt != null || it.plannedDateEpochDay != null)
    } }
    val hasLinks = remember(state.revision) { runCatching { state.store.links().isNotEmpty() }.getOrDefault(true) }
    val writableCalendars = state.calendars.filter { it.writable }
    val dirty = draft != state.options

    fun commit() {
        try { state.save(draft); error = null } catch (_: Exception) { error = "Seçimler kaydedilemedi." }
    }

    fun trySave() {
        val targetValid = state.calendars.any { it.id == draft.targetId && it.identity == draft.targetIdentity && it.writable }
        if (draft.exportEnabled && (!writable || !targetValid)) {
            error = "Göndermek için yazma izni ver ve bir hedef takvim seç."; return
        }
        val before = state.options
        // Ask for consent only when this save would publish MORE than what was already approved.
        val widened = when {
            !draft.exportEnabled -> false
            !before.exportEnabled || before.targetId != draft.targetId -> true
            draft.exportAll -> !before.exportAll
            before.exportAll -> false
            else -> (draft.selectedIds - before.selectedIds).isNotEmpty()
        }
        if (widened) confirm = true else commit()
    }

    if (confirm) CalendarGlassDialog(
        onDismiss = { confirm = false },
        title = "Takvime gönderilsin mi?",
        reduceBlur = reduceDynamicBlur,
        actions = {
            CalendarGlassButton(primary = true, onClick = { confirm = false; commit() }) { Text("Onayla ve gönder") }
            TextButton(onClick = { confirm = false }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("Vazgeç") }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val body = Color(0xFFD6D7DE)
            Text("Seçtiğin planların başlığı, günü ve saati takvime yazılır; kartın web bağlantısı da otomatik eklenir.", color = body, fontSize = 14.sp, lineHeight = 20.sp)
            Text("Linki bilen herkes başlığı, checklist ve CardStack metinlerini, medya bilgilerini, fotoğraf ve dosyaları görebilir. Konum, ses kaydı ve günlük yayımlanmaz.", color = body, fontSize = 14.sp, lineHeight = 20.sp)
            Text("Takvimi paylaşıyorsan üyeler bağlantıyı görür. Dosyalar kopyalanmaz; asıl dosyayı silersen linkte de açılmaz. Link 30 gün geçerlidir, eşitlemede yenilenir.", color = body, fontSize = 14.sp, lineHeight = 20.sp)
            Text("Tekrarlıların gelecek 90 günü aktarılır. REAL'de sildiğin planların takvimdeki kopyaları (geçmiş olanlar dahil) da silinir; tamamlananların yalnız gelecek kopyaları kaldırılır. Takvimde sen değiştirdiysen kopyaya dokunulmaz.", color = body, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }

    CalendarGlassFrame(reduceBlur = reduceDynamicBlur) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
            com.cagri.real.ui.components.RealBackButton(onBack, Modifier.padding(vertical = 8.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Takvim bağlantısı", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text("Telefondaki Google, Samsung ve diğer takvimleri REAL'de göster; istersen REAL planlarını bir takvime gönder.",
                        color = CalendarMuted, fontSize = 14.sp, lineHeight = 20.sp)
                }

                // Plain-language summary: what comes in, what goes out, when it runs.
                CalendarSection("Nasıl çalışır?") {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("REAL'e alınan: Seçtiğin takvimlerin etkinlikleri yalnızca gösterilir. REAL'e kopyalanmaz, onayın olmadan değiştirilmez.",
                            color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                        Text("Takvime gönderilen: Yalnız 'Takvime gönder' açıksa, seçtiğin hedef takvime; başlık, gün, saat ve kartın web bağlantısı. REAL'de sildiğin plan takvimden de silinir.",
                            color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                        Text("Ne zaman: Uygulamayı açınca, takvim ekranına girince, ayar kaydedince ve 'yenile'ye basınca. Arka planda sürekli çalışmaz.",
                            color = CalendarMuted, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }

                // Status + manual sync
                CalendarGlassPanel(Modifier.fillMaxWidth(), corners = 22.dp) {
                    Row(Modifier.fillMaxWidth().padding(start = 18.dp, top = 14.dp, bottom = 14.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(if (state.busy) "Eşitleniyor…" else "Durum", color = CalendarMuted, fontSize = 12.sp)
                            Text(state.status.ifBlank { "Takvim bağlantısı hazır." }, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                            if (state.publicationStatus.isNotBlank())
                                Text(state.publicationStatus, color = Color(0xFFFFD79A), fontSize = 12.sp, lineHeight = 17.sp)
                            error?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 13.sp, lineHeight = 18.sp) }
                        }
                        if (readable) IconButton(onClick = { state.refresh() }, enabled = !state.busy) {
                            if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = CalendarAccent)
                            else ActionIcon(ActionGlyph.Repeat, Modifier.size(22.dp), Color.White)
                        }
                    }
                }

                if (!readable) {
                    CalendarSection("Başlamak için",
                        footer = "REAL, takvim etkinliklerini yalnızca bu izinle okuyabilir.") {
                        ActionRow("Takvimleri görmeye izin ver", "Google hesabı cihazda ekli ve takvim senkronizasyonu açık olmalı.") {
                            readPermission.launch(Manifest.permission.READ_CALENDAR)
                        }
                    }
                } else {
                    // 1) READ
                    CalendarSection("1 · REAL'de göster",
                        footer = "Seçtiğin takvimlerin etkinlikleri Focus ve takvim ekranlarında görünür.") {
                        if (state.calendars.isEmpty()) {
                            Text("Takvim bulunamadı. Google hesabını cihaz ayarlarından ekleyip takvim senkronizasyonunu aç.",
                                color = CalendarMuted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(18.dp))
                        }
                        state.calendars.forEachIndexed { index, calendar ->
                            if (index > 0) RowDivider()
                            val checked = calendar.id in draft.readIds && draft.readIdentities[calendar.id] == calendar.identity
                            CheckRow(
                                label = calendar.name,
                                supporting = calendar.account + if (!calendar.syncEnabled) " · cihaz senkronizasyonu kapalı" else "",
                                checked = checked
                            ) {
                                val identities = if (checked) draft.readIdentities - calendar.id
                                else draft.readIdentities + (calendar.id to calendar.identity)
                                draft = draft.copy(readIds = identities.keys, readIdentities = identities)
                            }
                        }
                    }

                    // 2) SEND
                    CalendarSection("2 · REAL planlarını takvime gönder",
                        footer = "Kapatmak mevcut takvim kayıtlarını silmez; sonraki değişiklikleri göndermeyi durdurur.") {
                        ToggleRow("Takvime gönder", "Kapalıyken REAL'den hiçbir şey takvime yazılmaz.", draft.exportEnabled) {
                            draft = draft.copy(exportEnabled = it)
                        }
                    }

                    if (draft.exportEnabled) {
                        CalendarSection("Nereye gönderilsin?",
                            footer = if (hasLinks) "Mevcut bağlantılar korunurken hedef takvim değiştirilemez." else null) {
                            if (!writable) {
                                ActionRow("Takvime yazmaya izin ver", "Gönderim için gerekli.") {
                                    writePermission.launch(Manifest.permission.WRITE_CALENDAR)
                                }
                                if (writableCalendars.isNotEmpty()) RowDivider()
                            }
                            if (writable && writableCalendars.isEmpty()) {
                                Text("Yazılabilir takvim bulunamadı.", color = CalendarMuted, fontSize = 14.sp, modifier = Modifier.padding(18.dp))
                            }
                            writableCalendars.forEachIndexed { index, calendar ->
                                if (index > 0) RowDivider()
                                val selected = draft.targetId == calendar.id && draft.targetIdentity == calendar.identity
                                val enabled = !hasLinks || selected
                                SelectRow(
                                    label = calendar.name,
                                    supporting = calendar.account + if (calendar.isGoogle) " · Google" else " · cihaz/diğer hesap",
                                    selected = selected, enabled = enabled
                                ) { draft = draft.copy(targetId = calendar.id, targetIdentity = calendar.identity) }
                            }
                        }

                        CalendarSection("Hangi planlar?",
                            footer = "Tekrarlı planlarda gelecek 90 gün gönderilir; REAL açıldığında uzatılır. Tarihsiz metin, Pano blokları ve geçici 24 hour kartları gönderilmez.") {
                            Segmented(listOf("Tüm planlı kartlar", "Seçtiklerim"), if (draft.exportAll) 0 else 1) {
                                draft = draft.copy(exportAll = it == 0)
                            }
                            if (draft.exportAll) {
                                Text("Yeni planlar da otomatik gönderilir.", color = CalendarMuted, fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 14.dp))
                            } else if (planned.isEmpty()) {
                                Text("Tarihi veya saati olan plan yok.", color = CalendarMuted, fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 14.dp))
                            } else {
                                planned.forEach { reminder ->
                                    RowDivider()
                                    CheckRow(reminder.text, null, reminder.id in draft.selectedIds) {
                                        draft = draft.copy(selectedIds = if (reminder.id in draft.selectedIds)
                                            draft.selectedIds - reminder.id else draft.selectedIds + reminder.id)
                                    }
                                }
                            }
                        }
                    }
                }

                // 3) PERMISSIONS
                CalendarSection("İzinler",
                    footer = "Google/Samsung'da değiştirilen veya silinen kopyalar korunur, zorla geri yazılmaz. Harici etkinliğe dokunarak cihaz takviminde düzenleyebilirsin.") {
                    PermissionRow("Takvimi okuma", readable) { readPermission.launch(Manifest.permission.READ_CALENDAR) }
                    RowDivider()
                    PermissionRow("Takvime yazma", writable) { writePermission.launch(Manifest.permission.WRITE_CALENDAR) }
                    RowDivider()
                    ActionRow("Uygulama izinlerini aç", null) {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            if (readable) CalendarGlassButton(
                primary = true, enabled = dirty && !state.busy, onClick = ::trySave,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
            ) { Text(if (dirty) "Değişiklikleri kaydet" else "Kaydedildi", fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ---- building blocks -------------------------------------------------------------------------

@Composable
private fun CalendarSection(title: String, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = CalendarMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp))
        CalendarGlassPanel(Modifier.fillMaxWidth(), corners = 22.dp) { Column(Modifier.fillMaxWidth(), content = content) }
        footer?.let {
            Text(it, color = CalendarMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 6.dp))
        }
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(Modifier.padding(start = 18.dp), color = Color.White.copy(alpha = 0.08f))

@Composable
private fun RowText(label: String, supporting: String?, modifier: Modifier = Modifier, dim: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Color.White.copy(alpha = if (dim) 0.45f else 1f), fontSize = 16.sp, lineHeight = 21.sp)
        supporting?.let { Text(it, color = CalendarMuted, fontSize = 12.sp, lineHeight = 16.sp) }
    }
}

@Composable
private fun ToggleRow(label: String, supporting: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RowText(label, supporting, Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = null, colors = SwitchDefaults.colors(
            checkedTrackColor = CalendarAccent, checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent,
            uncheckedTrackColor = Color.White.copy(alpha = 0.14f), uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
            uncheckedBorderColor = Color.Transparent))
    }
}

@Composable
private fun CheckRow(label: String, supporting: String?, checked: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null, colors = CheckboxDefaults.colors(
            checkedColor = CalendarAccent, uncheckedColor = Color.White.copy(alpha = 0.5f), checkmarkColor = Color.Black))
        RowText(label, supporting, Modifier.weight(1f).padding(start = 10.dp, end = 8.dp))
    }
}

@Composable
private fun SelectRow(label: String, supporting: String?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null, enabled = enabled, colors = RadioButtonDefaults.colors(
            selectedColor = CalendarAccent, unselectedColor = Color.White.copy(alpha = 0.5f),
            disabledSelectedColor = CalendarAccent.copy(alpha = 0.5f), disabledUnselectedColor = Color.White.copy(alpha = 0.2f)))
        RowText(label, supporting, Modifier.weight(1f).padding(start = 10.dp, end = 8.dp), dim = !enabled)
    }
}

@Composable
private fun ActionRow(label: String, supporting: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RowText(label, supporting, Modifier.weight(1f).padding(end = 12.dp))
        ActionIcon(ActionGlyph.ChevronRight, Modifier.size(16.dp), CalendarMuted)
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onRequest: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = !granted, onClick = onRequest).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(if (granted) "Verildi" else "İzin ver", fontSize = 14.sp, fontWeight = FontWeight.Medium,
            color = if (granted) CalendarAccent else Color(0xFFFFD79A))
    }
}

/** Two-or-more option single-select; the chosen segment is a soft light pill inside the glass panel. */
@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            Box(Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                .background(if (on) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                .clickable { onSelect(index) }.padding(vertical = 11.dp),
                contentAlignment = Alignment.Center) {
                Text(label, color = if (on) Color.White else CalendarMuted, fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}
