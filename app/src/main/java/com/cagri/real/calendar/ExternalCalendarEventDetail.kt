package com.cagri.real.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
    onCreateRealCard: suspend () -> Unit, reduceDynamicBlur: Boolean = false,
    /** REAL categories to file the event (or its whole calendar) under. */
    categories: List<com.cagri.real.data.CustomCategory> = emptyList(),
    currentCategoryId: Long? = null,
    currentIsWholeCalendar: Boolean = true,
    /** categoryId = existing category, newName = create one with that name; both null = remove the link. */
    onCategorize: (categoryId: Long?, newName: String?, wholeCalendar: Boolean) -> Unit = { _, _, _ -> }) {
    val context = LocalContext.current
    val provider = remember(context) { DeviceCalendarProvider(context) }
    var description by remember(occurrence) { mutableStateOf<String?>(null) }
    var location by remember(occurrence) { mutableStateOf<String?>(null) }
    var error by remember(occurrence) { mutableStateOf<String?>(null) }
    var available by remember(occurrence) { mutableStateOf(false) }
    var loading by remember(occurrence) { mutableStateOf(true) }
    var creating by remember(occurrence) { mutableStateOf(false) }
    var categorizeOpen by remember(occurrence) { mutableStateOf(false) }
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
            val detailScroll = rememberScrollState()
            LaunchedEffect(categorizeOpen) { if (categorizeOpen) detailScroll.animateScrollTo(detailScroll.maxValue) }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(detailScroll).padding(20.dp),
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
                if (categorizeOpen) CategorizePanel(occurrence.calendarName, categories, currentCategoryId,
                    currentIsWholeCalendar) { id, name, whole -> onCategorize(id, name, whole); categorizeOpen = false }
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
            // Kategorize et: shows the current category (with its colour) once linked.
            val linked = categories.firstOrNull { it.id == currentCategoryId }
            CalendarGlassButton(onClick = { categorizeOpen = !categorizeOpen },
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                if (linked != null) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(categoryDotColor(linked)))
                    Spacer(Modifier.width(8.dp))
                }
                Text(linked?.name ?: "Kategorize et", color = Color.White)
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

/**
 * Kategorize et: file this event — or everything in its calendar — under a REAL category, so the planner shows them
 * in that category's colour. The events stay in their calendar.
 */
internal fun categoryDotColor(c: com.cagri.real.data.CustomCategory): Color = c.customSolidColorArgb?.let { Color(it) }
    ?: com.cagri.real.ui.components.ReminderColorChoices.getOrNull(c.accentStart)?.color ?: Color(0xFF4558C8)

@Composable
private fun CategorizePanel(
    calendarName: String,
    categories: List<com.cagri.real.data.CustomCategory>,
    currentCategoryId: Long?,
    currentIsWholeCalendar: Boolean,
    onCategorize: (Long?, String?, Boolean) -> Unit
) {
    var whole by remember(currentIsWholeCalendar) { mutableStateOf(currentIsWholeCalendar) }
    CalendarGlassPanel(Modifier.fillMaxWidth(), corners = 22.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { whole = !whole }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp).clip(CircleShape)
                    .background(if (whole) Color(0xFF91B9FF) else Color.Transparent)
                    .border(1.2.dp, if (whole) Color(0xFF91B9FF) else Color(0xFF777780), CircleShape),
                    contentAlignment = Alignment.Center) {
                    if (whole) ActionIcon(ActionGlyph.Check, Modifier.size(12.dp), Color(0xFF111216))
                }
                Text("$calendarName takvimindeki tüm etkinlikler", color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.padding(start = 10.dp))
            }
            categories.forEach { c ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable { onCategorize(c.id, null, whole) }
                    .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(categoryDotColor(c)))
                    Text(c.name, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    if (c.id == currentCategoryId) ActionIcon(ActionGlyph.Check, Modifier.size(16.dp), Color(0xFF91B9FF))
                }
            }
            if (categories.none { it.name.equals(calendarName, ignoreCase = true) }) {
                Text("+ Yeni kategori: $calendarName", color = Color(0xFF91B9FF), fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable { onCategorize(null, calendarName, whole) }
                        .padding(vertical = 8.dp))
            }
            if (currentCategoryId != null) {
                Text("Kategoriden çıkar", color = Color(0xFFFFB4AB), fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        .clickable { onCategorize(null, null, false) }
                        .padding(vertical = 6.dp))
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
