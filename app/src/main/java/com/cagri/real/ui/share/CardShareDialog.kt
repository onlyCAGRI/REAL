package com.cagri.real.ui.share

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.cagri.real.ui.connections.ConnectionPill
import com.cagri.real.ui.connections.ConnectionNote
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.platform.android.share.CardShareClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

// UI-only: which sharing method is currently expanded.
private enum class ShareMode { None, RealUser, Link }

/**
 * Kartın içinde gerçekte ne olduğu. Verilirse "Dahil edilecek içerik" otomatik seçilir: kartta dosya/görsel, konum,
 * ses ya da günlük varsa açık gelir, yoksa o seçenek hiç gösterilmez. Verilmezse (null) eski varsayılan geçerlidir.
 */
data class ShareContent(val files: Int = 0, val hasLocation: Boolean = false, val voices: Int = 0, val hasJournal: Boolean = false)

private val SheetInk = Color(0xFF0B0B0D)
private val GlassFill = Color.White.copy(alpha = 0.06f)
private val GlassStroke = Color.White.copy(alpha = 0.10f)
private val MutedText = Color(0xFF9A9AA2)
private val AccentCyan = Color(0xFF6FD6FF)
private val Violet = Color(0xFFB59BFF)
private val Amber = Color(0xFFFFB86F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardShareDialog(cardId: Long, selectedIds: Set<String>? = null, fallbackText: String,
                    content: ShareContent? = null, onDismiss: () -> Unit) {
    val user = com.cagri.real.ui.connections.currentConnectionsUser()
    if (user != null && user.displayName.isNullOrBlank()) {
        com.cagri.real.ui.components.RealAlertDialog(onDismissRequest = onDismiss,
            title = { Text("Gönderen bilgisi") },
            text = { com.cagri.real.ui.auth.ProfileNameForm(user, onSaved = {}) },
            confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } })
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val requestId = rememberSaveable(cardId, selectedIds) { UUID.randomUUID().toString() }
    var savedLinkUrl by rememberSaveable(cardId, selectedIds) { mutableStateOf<String?>(null) }
    var frozen by rememberSaveable(cardId, selectedIds) { mutableStateOf(false) }
    val controller = remember(cardId, selectedIds, requestId) { CardShareController(cardId, selectedIds, scope, requestId = requestId,
        initialLink = savedLinkUrl?.let { CardShareClient.Link(requestId, it) }) }
    val state by controller.state.collectAsState()
    LaunchedEffect(state.link, state.revoked) { savedLinkUrl = if (state.revoked) null else state.link?.url }
    // İçerik biliniyorsa kartta olanlar otomatik seçilir; kartta ek yoksa kapalı gelir.
    var files by rememberSaveable(content) { mutableStateOf(content?.let { it.files > 0 } ?: false) }
    var location by rememberSaveable(content) { mutableStateOf(content?.hasLocation ?: false) }
    var voice by rememberSaveable(content) { mutableStateOf(content?.let { it.voices > 0 } ?: false) }
    var journal by rememberSaveable(content) { mutableStateOf(content?.hasJournal ?: false) }
    var contactError by remember { mutableStateOf<String?>(null) }
    var receive by remember { mutableStateOf<Boolean?>(null) }
    var profileBusy by remember { mutableStateOf(false) }
    // UI-only state (presentation, not business state)
    var mode by rememberSaveable { mutableStateOf(ShareMode.None) }
    var contentOpen by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val client = remember { CardShareClient() }
    val options = CardShareClient.Options(files, location, voice, journal, selectedIds)
    fun shareLink(url: String) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url)
        }, "Kartı paylaş"))
    }
    val phonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let { uri ->
            try {
                val phone = context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
                if (!phone.isNullOrBlank()) {
                    frozen = true
                    controller.create(options) { url ->
                        try { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone, null)).putExtra("sms_body", url)) }
                        catch (_: Exception) { shareLink(url) }
                    }
                }
            } catch (_: Exception) { contactError = "Seçilen telefon okunamadı. Bunun yerine bağlantıyı paylaş." }
        }
    }
    LaunchedEffect(client) {
        try { receive = client.profile() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Sharing action reports availability when used. */ }
    }

    val busyNow by rememberUpdatedState(state.busy)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !busyNow })
    val optionsEnabled = !state.busy && !frozen

    ModalBottomSheet(
        onDismissRequest = { if (!state.busy) onDismiss() },
        sheetState = sheetState,
        containerColor = SheetInk,
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(width = 36.dp, height = 4.dp)
                .clip(CircleShape).background(Color.White.copy(alpha = 0.22f)))
        }
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 20.dp)
        ) {
            AnimatedContent(
                targetState = showSettings,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "shareSheetPage"
            ) { settings ->
                if (settings) {
                    // ---------- Paylaşım ayarları (yönetim sayfası) ----------
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            com.cagri.real.ui.components.RealBackButton({ showSettings = false }, enabled = !state.busy)
                        }
                        Text("Paylaşım ayarları", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 14.dp))
                        Text("Açık paylaşım linklerini yönet ve gelen kartları kontrol et.", color = MutedText, fontSize = 14.sp,
                            modifier = Modifier.padding(top = 2.dp))
                        Spacer(Modifier.height(18.dp))
                        SharedLinksSection(client, state.link?.id)
                        receive?.let { enabled ->
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(22.dp))
                                    .background(GlassFill).border(1.dp, GlassStroke, RoundedCornerShape(22.dp))
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Gelen kartlara izin ver", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    Text("Kapalıyken kimse sana doğrudan kart gönderemez.", color = MutedText, fontSize = 12.sp)
                                }
                                Switch(
                                    checked = enabled,
                                    enabled = !profileBusy && !state.busy,
                                    onCheckedChange = { next ->
                                        profileBusy = true
                                        scope.launch {
                                            try { receive = client.profile(next) }
                                            catch (cancelled: CancellationException) { throw cancelled }
                                            catch (failure: Exception) { contactError = failure.message }
                                            finally { profileBusy = false }
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = AccentCyan.copy(alpha = 0.55f), checkedThumbColor = Color.White,
                                        uncheckedTrackColor = GlassFill, uncheckedThumbColor = MutedText, uncheckedBorderColor = GlassStroke
                                    )
                                )
                            }
                        }
                    }
                } else {
                    // ---------- Ana paylaşım ekranı ----------
                    Column(Modifier.fillMaxWidth().animateContentSize(tween(220))) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                            Text("Kartı paylaş", Modifier.weight(1f), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                            ConnectionPill("Ayarlar", { showSettings = true }, enabled = !state.busy, small = true)
                        }
                        // Paylaşılacak kartın kısa önizlemesi
                        val preview = fallbackText.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                        if (preview.isNotEmpty()) Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(GlassFill)
                                .border(1.dp, GlassStroke, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(AccentCyan))
                            Spacer(Modifier.width(10.dp))
                            Text(preview.take(140), color = Color.White.copy(alpha = .85f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(18.dp))

                        // 1 · REAL kullanıcısına gönder
                        MethodRow(MethodIcon.Person, AccentCyan, "REAL kullanıcısına gönder", "Kartın bağımsız bir kopyası iletilir",
                            selected = mode == ShareMode.RealUser, expandable = true, enabled = true) {
                            mode = if (mode == ShareMode.RealUser) ShareMode.None else ShareMode.RealUser
                        }
                        AnimatedVisibility(mode == ShareMode.RealUser,
                            enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                            exit = fadeOut(tween(120)) + shrinkVertically(tween(180))) {
                            Column(Modifier.fillMaxWidth().padding(top = 14.dp, start = 2.dp, end = 2.dp)) {
                                IncludedContent(content, contentOpen, { contentOpen = !contentOpen },
                                    files, location, voice, journal, optionsEnabled,
                                    { files = it }, { location = it }, { voice = it }, { journal = it })
                                Spacer(Modifier.height(14.dp))
                                com.cagri.real.ui.connections.ConnectionPeoplePicker(
                                    enabled = !state.busy && !state.revoked, actionLabel = "Gönder",
                                    onSelect = { person -> frozen = true; controller.sendConnection(person.uid, options) }
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // 2 · Bağlantı paylaş
                        MethodRow(MethodIcon.Link, Violet, "Bağlantı paylaş", "WhatsApp, e-posta ve daha fazlası",
                            selected = mode == ShareMode.Link, expandable = true, enabled = true) {
                            mode = if (mode == ShareMode.Link) ShareMode.None else ShareMode.Link
                        }
                        AnimatedVisibility(mode == ShareMode.Link,
                            enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                            exit = fadeOut(tween(120)) + shrinkVertically(tween(180))) {
                            Column(Modifier.fillMaxWidth().padding(top = 14.dp, start = 2.dp, end = 2.dp)) {
                                Text("Bağlantıya sahip olan herkes seçilen içeriği görüntüleyebilir. Yeni bağlantılar 24 saat geçerlidir.", color = MutedText, fontSize = 13.sp)
                                Spacer(Modifier.height(10.dp))
                                IncludedContent(content, contentOpen, { contentOpen = !contentOpen },
                                    files, location, voice, journal, optionsEnabled,
                                    { files = it }, { location = it }, { voice = it }, { journal = it })
                                Spacer(Modifier.height(14.dp))
                                state.link?.let { link ->
                                    // Oluşturulan link: kopyala / tekrar paylaş
                                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(16.dp)).background(GlassFill)
                                        .border(1.dp, GlassStroke, RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(link.url.removePrefix("https://"), Modifier.weight(1f), color = AccentCyan, fontSize = 13.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        ConnectionPill("Kopyala", {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("REAL kart linki", link.url))
                                            contactError = "Link kopyalandı."
                                        }, enabled = !state.busy, small = true)
                                    }
                                }
                                PrimaryButton(if (state.link == null) "Bağlantı oluştur ve paylaş" else "Bağlantıyı tekrar paylaş",
                                    enabled = !state.busy && !state.revoked) {
                                    frozen = true; controller.create(options, ::shareLink)
                                }
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    TextButton(
                                        onClick = {
                                            try { phonePicker.launch(Intent(Intent.ACTION_PICK).apply { type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE }) }
                                            catch (_: Exception) { contactError = "Telefon rehberi açılamadı. Bağlantıyı paylaş'ı kullan." }
                                        },
                                        enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)
                                    ) { Text("SMS ile gönder", color = AccentCyan, fontSize = 14.sp) }
                                    if (state.link != null) TextButton(
                                        onClick = { controller.revoke() }, enabled = !state.busy,
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) { Text("Bu linki kapat", color = MutedText, fontSize = 14.sp) }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // 3 · Metin olarak paylaş (hemen çalışır, ayar yok)
                        MethodRow(MethodIcon.Text, Amber, "Metin olarak paylaş", "İnternetsiz de çalışır",
                            selected = false, expandable = false, enabled = !state.busy) {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, fallbackText)
                            }, "Metin olarak paylaş"))
                        }
                    }
                }
            }

            if (state.busy) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    com.cagri.real.ui.components.RealSpinner(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                }
            }
            (state.message ?: contactError)?.let { ConnectionNote(it, Modifier.padding(top = 12.dp)) }
        }
    }
}

private enum class MethodIcon { Person, Link, Text }

/** Yöntem satırları için küçük çizim ikonları (emoji/metin glifi yok, her cihazda aynı görünür). */
@Composable
private fun MethodGlyph(icon: MethodIcon, tint: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width; val h = size.height
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        when (icon) {
            MethodIcon.Person -> {
                drawCircle(tint, radius = w * .17f, center = Offset(w * .5f, h * .32f), style = stroke)
                drawArc(tint, 180f, 180f, false, topLeft = Offset(w * .17f, h * .56f), size = Size(w * .66f, h * .62f), style = stroke)
            }
            MethodIcon.Link -> rotate(-45f, Offset(w / 2, h / 2)) {
                drawRoundRect(tint, Offset(w * .02f, h * .35f), Size(w * .56f, h * .30f), CornerRadius(h * .15f), style = stroke)
                drawRoundRect(tint, Offset(w * .42f, h * .35f), Size(w * .56f, h * .30f), CornerRadius(h * .15f), style = stroke)
            }
            MethodIcon.Text -> {
                drawLine(tint, Offset(w * .15f, h * .28f), Offset(w * .85f, h * .28f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * .15f, h * .50f), Offset(w * .85f, h * .50f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * .15f, h * .72f), Offset(w * .55f, h * .72f), stroke.width, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun MethodRow(icon: MethodIcon, tone: Color, title: String, subtitle: String, selected: Boolean, expandable: Boolean,
                      enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f).clip(shape)
            .background(if (selected) tone.copy(alpha = 0.08f) else GlassFill)
            .border(1.dp, if (selected) tone.copy(alpha = 0.5f) else GlassStroke, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(tone.copy(alpha = if (selected) 0.22f else 0.14f)), contentAlignment = Alignment.Center) {
            MethodGlyph(icon, tone)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = MutedText, fontSize = 13.sp)
        }
        Text(if (expandable && selected) "⌄" else "›", color = MutedText, fontSize = 20.sp)
    }
}

/**
 * Dahil edilecek içerik. [content] biliniyorsa yalnız kartta olan türler gösterilir ve açık gelir (otomatik seçim);
 * bilinmiyorsa dört türün hepsi elle seçilebilir.
 */
@Composable
private fun IncludedContent(
    content: ShareContent?, open: Boolean, onToggle: () -> Unit,
    files: Boolean, location: Boolean, voice: Boolean, journal: Boolean, enabled: Boolean,
    onFiles: (Boolean) -> Unit, onLocation: (Boolean) -> Unit, onVoice: (Boolean) -> Unit, onJournal: (Boolean) -> Unit
) {
    val showFiles = (content?.files ?: 0) > 0
    val showLocation = content?.hasLocation ?: false
    val showVoice = (content?.voices ?: 0) > 0
    val showJournal = content?.hasJournal ?: false
    val filesLabel = if (content != null && content.files > 0) "${content.files} görsel ve dosya" else "Görsel ve dosyalar"
    val voiceLabel = if (content != null && content.voices > 0) "${content.voices} ses kaydı" else "Ses kayıtları"
    val summary = listOfNotNull(
        filesLabel.takeIf { files && showFiles }, "Konum".takeIf { location && showLocation },
        voiceLabel.takeIf { voice && showVoice }, "Günlük ve ruh hali".takeIf { journal && showJournal }
    ).joinToString(", ").ifEmpty { "Yalnızca metin" }
    val nothingExtra = !showFiles && !showLocation && !showVoice && !showJournal
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(GlassFill)
                .border(1.dp, GlassStroke, RoundedCornerShape(16.dp))
                .clickable(enabled = !nothingExtra, role = Role.Button, onClick = onToggle).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dahil edilecek içerik", color = Color.White, fontSize = 15.sp)
                    if (content != null) Text("  · otomatik", color = AccentCyan, fontSize = 12.sp)
                }
                if (!open || nothingExtra) Text(
                    if (nothingExtra) "Bu kartta dosya, konum ya da ses yok; yalnızca metin paylaşılır." else summary,
                    color = MutedText, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            if (!nothingExtra) Text(if (open) "⌄" else "›", color = MutedText, fontSize = 18.sp)
        }
        AnimatedVisibility(open && !nothingExtra, enter = fadeIn(tween(160)) + expandVertically(tween(200)),
            exit = fadeOut(tween(100)) + shrinkVertically(tween(160))) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val chips = buildList<@Composable (Modifier) -> Unit> {
                    if (showFiles) add { m -> Pill(filesLabel, files, enabled, onFiles, m) }
                    if (showLocation) add { m -> Pill("Konum", location, enabled, onLocation, m) }
                    if (showVoice) add { m -> Pill(voiceLabel, voice, enabled, onVoice, m) }
                    if (showJournal) add { m -> Pill("Günlük ve ruh hali", journal, enabled, onJournal, m) }
                }
                chips.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { chip -> chip(Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.alpha(if (enabled) 1f else 0.5f).heightIn(min = 48.dp).clip(shape)
            .background(if (checked) AccentCyan.copy(alpha = 0.14f) else GlassFill)
            .border(1.dp, if (checked) AccentCyan.copy(alpha = 0.6f) else GlassStroke, shape)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text((if (checked) "✓ " else "") + label, color = if (checked) Color.White else MutedText,
            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White, contentColor = Color.Black,
            disabledContainerColor = Color.White.copy(alpha = 0.12f), disabledContentColor = Color.White.copy(alpha = 0.38f)
        )
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}
