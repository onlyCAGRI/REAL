package com.cagri.real.ui.account

import com.cagri.real.i18n.t
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.cagri.real.data.AttachmentType
import com.cagri.real.data.ReminderRepository
import com.cagri.real.platform.android.account.AndroidAccountRepository
import com.cagri.real.platform.android.sync.SyncLocal
import com.cagri.real.ui.connections.ConnectionPalette
import com.cagri.real.ui.connections.ConnectionPill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

/**
 * Bulut alanı uyarısı: once at 90% and once when full (again only after it dropped under 90%). The app keeps working;
 * new files stay on this phone (cloud-off badge) and go up by themselves once there is room.
 */
@Composable
fun CloudSpaceNoticeHost(uid: String?) {
    if (uid == null) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notice by remember(uid) { mutableIntStateOf(0) }
    var openSpace by remember(uid) { mutableStateOf(false) }
    var openPro by remember(uid) { mutableStateOf(false) }
    val waiting by SyncLocal.waitingForSpace.collectAsState()

    suspend fun check(force: Boolean) {
        val prefs = context.getSharedPreferences("real_cloud_space_$uid", Context.MODE_PRIVATE)
        val repository = AndroidAccountRepository(context, uid)
        val now = System.currentTimeMillis()
        val usage = if (!force && now - prefs.getLong("checked_at", 0) < CHECK_EVERY_MS) repository.cachedUsage()
            else try { repository.refresh().usage.also { prefs.edit().putLong("checked_at", now).apply() } }
                catch (e: CancellationException) { throw e } catch (_: Exception) { repository.cachedUsage() }
        if (usage == null || usage.quotaBytes <= 0) return
        val level = when { usage.usedBytes >= usage.quotaBytes -> 2; usage.usedBytes * 10 >= usage.quotaBytes * 9 -> 1; else -> 0 }
        val shown = prefs.getInt("shown_level", 0)
        if (level != shown && (level == 0 || level > shown)) prefs.edit().putInt("shown_level", level).apply()
        if (level > shown) notice = level
    }

    LifecycleResumeEffect(uid) {
        val job = scope.launch { check(force = false) }
        onPauseOrDispose { job.cancel() }
    }
    // A file was just refused for space: look now, not in six hours.
    LaunchedEffect(uid, waiting.isNotEmpty()) { if (waiting.isNotEmpty()) check(force = true) }

    if (notice != 0) {
        val full = notice == 2
        AlertDialog(
            onDismissRequest = { notice = 0 },
            containerColor = ConnectionPalette.Ink,
            title = { Text(if (full) t("Bulut alanın doldu") else t("Bulut alanın dolmak üzere"), color = Color.White) },
            text = {
                Text(if (full) t("REAL'i kullanmaya devam edebilirsin. Yeni fotoğraf ve ekler bu telefonda kalır, yer açınca kendiliğinden yüklenir.")
                    else t("Alanının %90'ını kullandın. Dolunca yeni ekler yalnız bu telefonda kalır."),
                    color = ConnectionPalette.Muted, fontSize = 14.sp)
            },
            confirmButton = { ConnectionPill(t("Yer aç"), { notice = 0; openSpace = true }, small = true, filled = true) },
            dismissButton = { ConnectionPill(t("Pro'ya geç"), { notice = 0; openPro = true }, small = true) },
        )
    }
    if (openSpace) AccountManagementSheet(onDismiss = { openSpace = false }, onAddAccount = { openSpace = false }, openSpace = true)
    if (openPro) com.cagri.real.ui.subscription.RealProRoute(onDismiss = { openPro = false })
}

private data class SpaceFile(val cardId: Long, val fileId: String, val title: String, val kind: String, val bytes: Long)

/** Yer aç: this phone's largest photos, files and voice recordings, with the card each sits on. */
@Composable
internal fun CloudSpaceFiles(onRemoved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val files by produceState<List<SpaceFile>?>(null, reload) {
        value = withContext(Dispatchers.IO) {
            ReminderRepository(context).load().flatMap { card ->
                val title = card.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: t("Başlıksız kart")
                card.attachments.filter { it.type != AttachmentType.TEXT_SNIPPET && it.localPath.isNotBlank() }.map {
                    SpaceFile(card.id, it.id, title, if (it.type == AttachmentType.IMAGE) t("Fotoğraf") else it.displayName ?: t("Dosya"),
                        File(it.localPath).length().takeIf { n -> n > 0 } ?: it.sizeBytes ?: 0)
                } + card.voiceRecordings.map { SpaceFile(card.id, it.id, title, t("Ses kaydı"), File(it.localPath).length()) }
            }.filter { it.bytes > 0 }.sortedByDescending { it.bytes }.take(10)
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("En büyük ekler"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        val list = files
        when {
            list == null -> Text(t("Yükleniyor…"), color = ConnectionPalette.Muted, fontSize = 13.sp)
            list.isEmpty() -> Text(t("Bu telefonda ek yok."), color = ConnectionPalette.Muted, fontSize = 13.sp)
            else -> list.forEach { file ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(file.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${file.kind} · ${cloudSize(file.bytes)}", color = ConnectionPalette.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(10.dp))
                    if (confirm == file.fileId) ConnectionPill(t("Sil"), {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                ReminderRepository(context).removeFile(file.cardId, file.fileId)
                            }
                            ReminderRepository.notifyChanged()
                            confirm = null; reload++; onRemoved()
                        }
                    }, danger = true, small = true)
                    else ConnectionPill(t("Kaldır"), { confirm = file.fileId }, small = true)
                }
            }
        }
        Text(t("Kaldırılan ekler buluttan 30 gün içinde silinir; hemen yer açmak için \"Bekleyenleri şimdi temizle\"."),
            color = ConnectionPalette.Muted, fontSize = 12.sp)
    }
}

private fun cloudSize(bytes: Long): String =
    if (bytes < 1024 * 1024) "%.0f KB".format(bytes / 1024.0) else "%.1f MB".format(bytes / (1024.0 * 1024.0))
