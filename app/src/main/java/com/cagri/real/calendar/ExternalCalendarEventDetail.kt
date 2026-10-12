package com.cagri.real.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Read-only external event detail. Never dispatches REAL reminder mutations. */
@Composable
internal fun ExternalCalendarEventDetail(occurrence: CalendarOccurrence, onDismiss: () -> Unit,
    onCreateRealCard: suspend () -> Unit, reduceDynamicBlur: Boolean = false) {
    val context = LocalContext.current
    val provider = remember(context) { DeviceCalendarProvider(context) }
    var description by remember(occurrence) { mutableStateOf<String?>(null) }
    var location by remember(occurrence) { mutableStateOf<String?>(null) }
    var error by remember(occurrence) { mutableStateOf<String?>(null) }
    var available by remember(occurrence) { mutableStateOf(false) }
    var loading by remember(occurrence) { mutableStateOf(true) }
    var creating by remember(occurrence) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(occurrence) {
        try {
            val record = withContext(Dispatchers.IO) { provider.event(occurrence.eventId) }
            if (record == null || record.calendarId != occurrence.calendarId || record.fields[0] != occurrence.title) {
                error = "Etkinlik değişmiş veya kaldırılmış olabilir. Takvime dönüp yenile."
            } else {
                // A REAL export marker is bookkeeping, not user-facing event content.
                description = record.description?.replace(Regex("\\n\\[REAL:[0-9a-fA-F-]{36}\\]$"), "")
                    ?.replace(Regex("\\n\\[REAL-CALENDAR:[0-9a-fA-F-]{36}\\]"), "")
                    ?.trim()?.takeIf { it.isNotEmpty() }
                location = record.fields[8]?.trim()?.takeIf { it.isNotEmpty() }
                available = true
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Etkinlik ayrıntıları okunamadı. Takvim iznini kontrol et." }
        finally { loading = false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false
    )) {
        BackHandler(onBack = onDismiss)
        CalendarGlassFrame(reduceDynamicBlur) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                com.cagri.real.ui.components.RealBackButton(onDismiss)
                Text("Takvim etkinliği", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).padding(start = 14.dp))
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SelectionContainer {
                    Text(occurrence.title, color = Color.White, fontSize = 28.sp, lineHeight = 34.sp,
                        fontWeight = FontWeight.SemiBold)
                }
                DetailSection("Tarih ve saat", externalCalendarTimeLabel(occurrence))
                DetailSection("Takvim", occurrence.calendarName)
                if (loading) Text("Ayrıntılar yükleniyor…", color = Color.LightGray)
                description?.let { DetailSection("Açıklama", it) }
                location?.let { DetailSection("Konum", it) }
                error?.let { Text(it, color = Color(0xFFFFB4AB)) }
                Text("Bu etkinliğin tarih ve saati bağlı takviminden gelir. İçine fotoğraf, checklist, CardStack ve diğer REAL içeriklerini eklemek için bir REAL kartı bağlayabilirsin. Bağlantı kurmak içerikleri public yapmaz.",
                    color = Color(0xFF9B9BA6), fontSize = 13.sp, lineHeight = 19.sp)
            }
            CalendarGlassButton(enabled = available && !creating, onClick = {
                creating = true
                scope.launch {
                    try { onCreateRealCard() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "REAL kartı bağlanamadı." }
                    finally { creating = false }
                }
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(if (creating) "Bağlanıyor…" else "REAL içeriği ekle", color = Color.White)
            }
            CalendarGlassButton(enabled = available, onClick = {
                try { provider.open(occurrence.eventId, occurrence.start) }
                catch (_: Exception) { error = "Etkinliği düzenleyecek takvim uygulaması bulunamadı." }
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                ActionIcon(ActionGlyph.Edit, Modifier.size(18.dp), Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Takvimde düzenle", color = Color.White)
            }
        }
        }
    }
}

@Composable
private fun DetailSection(label: String, value: String) {
    CalendarGlassPanel(Modifier.fillMaxWidth(), corners = 22.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = Color(0xFF9B9BA6), fontSize = 12.sp)
            SelectionContainer { Text(value, color = Color.White, fontSize = 16.sp, lineHeight = 23.sp) }
        }
    }
}

internal fun externalCalendarTimeLabel(event: CalendarOccurrence, zone: ZoneId = ZoneId.systemDefault()): String {
    val locale = Locale.forLanguageTag("tr-TR")
    val dayFormat = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", locale)
    if (event.allDay) {
        val utc = ZoneId.of("UTC")
        val first = Instant.ofEpochMilli(event.start).atZone(utc).toLocalDate()
        // Calendar end is exclusive; a one-day event must not show the following day.
        val last = Instant.ofEpochMilli(maxOf(event.start, event.end - 1)).atZone(utc).toLocalDate()
        return if (first == last) "${first.format(dayFormat)}\nTüm gün"
        else "${first.format(dayFormat)} – ${last.format(dayFormat)}\nTüm gün"
    }
    val start = Instant.ofEpochMilli(event.start).atZone(zone)
    val end = Instant.ofEpochMilli(event.end).atZone(zone)
    val time = DateTimeFormatter.ofPattern("HH:mm", locale)
    return if (start.toLocalDate() == end.toLocalDate())
        "${start.format(dayFormat)}\n${start.format(time)} – ${end.format(time)}"
    else "${start.format(dayFormat)} ${start.format(time)} – ${end.format(dayFormat)} ${end.format(time)}"
}
