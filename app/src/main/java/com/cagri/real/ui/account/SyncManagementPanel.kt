package com.cagri.real.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.platform.android.sync.AndroidSyncHealthRepository
import com.cagri.real.sync.*
import com.cagri.real.ui.connections.ConnectionNote
import com.cagri.real.ui.connections.ConnectionPill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Existing sharing pills/panels; no polling, DB reads or writes during composition. */
@Composable
internal fun SyncManagementPanel(uid: String, enabled: Boolean) {
    val repository = remember(uid) { AndroidSyncHealthRepository(uid) }
    val published by repository.snapshots.collectAsState()
    var loaded by remember(uid) { mutableStateOf<SyncHealthSnapshot?>(null) }
    val snapshot = published?.takeIf { it.workspace == "u:$uid" } ?: loaded
    val scope = rememberCoroutineScope()
    var expanded by remember(uid) { mutableStateOf(false) }
    var limit by remember(uid) { mutableIntStateOf(20) }
    var busy by remember(uid) { mutableStateOf(false) }
    var error by remember(uid) { mutableStateOf<String?>(null) }
    var confirmation by remember(uid) { mutableStateOf<Pair<PersistedSyncConflict, SyncConflictChoice>?>(null) }
    var openConflicts by remember(uid) { mutableStateOf(emptySet<String>()) }
    var bulkConfirm by remember(uid) { mutableStateOf(false) }
    suspend fun refresh() { loaded = repository.refresh() }
    fun action(work: suspend () -> Unit) {
        if (busy || !enabled) return
        busy = true; error = null
        scope.launch {
            try { work(); refresh() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "İşlem tamamlanamadı veya sürüm değişti. Yenile ve tekrar seç; kayıtların korunuyor." }
            finally { busy = false }
        }
    }
    LaunchedEffect(repository) {
        try { refresh() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Eşitleme durumu alınamadı." }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        snapshot?.let { health ->
            val waiting = health.conflicts.size + health.issues.size + health.rejected.size
            ConnectionNote("${health.status.pending} bekleyen kayıt · ${health.conflicts.size} çakışma · ${health.issues.size} ek sorunu" +
                (if (health.rejected.isNotEmpty()) " · ${health.rejected.size} reddedilen kayıt" else ""))
            if (health.paused) ConnectionNote("Bulut eşitlemesi kapalı. Yerel verilerin korunuyor.")
            // The reason is shown too ("Bağlantını kontrol et" alone hid failures that had nothing to do with the connection).
            else if (health.status.lastError != null) ConnectionNote("Son eşitleme tamamlanamadı; bekleyen kayıtlar silinmez.\nSebep: ${health.status.lastError}")
            else if (waiting == 0) ConnectionNote("Kaydedilmiş bir çakışma veya ek hatası yok.")
            else ConnectionNote("Diğer kayıtlar eşitlendi. Aşağıdaki ${waiting} kayıt senin seçimini bekliyor; hiçbiri silinmedi.")
            ConnectionPill(if (expanded) "Ayrıntıları gizle" else "Sorunları ve sürümleri göster", { expanded = !expanded }, small = true)
            ConnectionPill("Yenile", { action { refresh() } }, enabled = enabled && !busy, small = true)
            val canAct = enabled && !busy && !health.paused
            if (expanded) {
                health.issues.take(limit).forEach { issue -> key(issue.token) {
                    Text(issue.title.ifBlank { "İçerik kartı" }, color = Color.White, fontSize = 15.sp)
                    ConnectionNote(if (issue.direction == SyncBinaryDirection.UPLOAD) "Bu kartın bir eki buluta gönderilemedi. Yerel içerik kaldırılmadı."
                        else "Bu kartın bulut sürümü ve ekleri bekliyor. Diğer kartlar eşitlenebilir.")
                    ConnectionNote(issue.code.explanation())
                    ConnectionNote(if (issue.automaticRetry) "Bağlantı uygun olduğunda mevcut eşitleme yeniden dener."
                        else "Sürekli tekrar denenmez. Dosyayı/izni düzeltip tekrar dene; istersen eki kart düzenleyicisinden kendin değiştir.")
                    ConnectionPill("Bu eki tekrar dene", { action { repository.retry(issue.token) } }, enabled = canAct, small = true)
                    Spacer(Modifier.height(6.dp))
                } }
                if (health.rejected.size > 1) {
                    Text("Hesabın kabul etmediği ${health.rejected.size} kayıt", color = Color.White, fontSize = 16.sp)
                    ConnectionNote("Hepsi bu cihazda duruyor. Tekrar denersen her biri yeniden gönderilir.")
                    ConnectionPill("Hepsini tekrar dene", {
                        action { health.rejected.forEach { repository.retryRejected(it.recordId) } }
                    }, enabled = canAct, small = true)
                    Spacer(Modifier.height(6.dp))
                }
                health.rejected.take(limit).forEach { rejected -> key("reject", rejected.recordId, rejected.generation) {
                    val open = "r:" + rejected.recordId in openConflicts
                    // One line per record; tapping it opens the reason and the choices.
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f))
                        .clickable { openConflicts = if (open) openConflicts - ("r:" + rejected.recordId) else openConflicts + ("r:" + rejected.recordId) }
                        .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rejected.title.ifBlank { if (rejected.kind == "CARD") "İçerik kartı" else "Kayıt" }, color = Color.White, fontSize = 15.sp,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("Hesap kabul etmedi", color = Color(0xFFFF9C8F), fontSize = 12.sp)
                        }
                        Text(if (open) "▴" else "▾", color = Color(0xFF9A9AA4), fontSize = 16.sp)
                    }
                    if (open) {
                        ConnectionNote(rejected.explanation())
                        ConnectionNote(if (rejected.hasCloudCopy) "Bu cihazdaki sürüm korunuyor. Kartı düzenlersen kendiliğinden tekrar gönderilir."
                            else "Bu kart hesabına hiç ulaşmadı; bu cihazdaki sürüm korunuyor.")
                        ConnectionPill("Tekrar dene", { action { repository.retryRejected(rejected.recordId) } }, enabled = canAct, small = true)
                        ConnectionPill(if (rejected.hasCloudCopy) "Hesaptakini kullan" else "Bu cihazdan kaldır",
                            { action { repository.useCloudForRejected(rejected.recordId) } }, enabled = canAct, small = true)
                    }
                    Spacer(Modifier.height(6.dp))
                } }
                // Cards the account deleted while they changed here come first, with one button for all of them.
                val deletedCards = health.conflicts.filter { it.kind == "CARD" && it.remote == null && it.currentLocal != null }
                if (deletedCards.size > 1) {
                    Text("Hesapta silinmiş ${deletedCards.size} kart", color = Color.White, fontSize = 16.sp)
                    ConnectionNote("Bu kartlar bu cihazda duruyor ama hesabında silinmiş görünüyor. Geri yüklersen her biri hesaba yeni bir kart olarak gönderilir; bu cihazda hiçbir şey kaybolmaz.")
                    if (bulkConfirm) {
                        ConnectionPill("Onayla: ${deletedCards.size} kartı geri yükle", {
                            action { deletedCards.forEach { repository.choose(it, SyncConflictChoice.LOCAL_AS_NEW_CARD) }; bulkConfirm = false }
                        }, enabled = canAct, small = true)
                        ConnectionPill("Vazgeç", { bulkConfirm = false }, enabled = !busy, small = true)
                    } else ConnectionPill("Hepsini hesaba geri yükle", { bulkConfirm = true }, enabled = canAct, small = true)
                    Spacer(Modifier.height(6.dp))
                }
                (deletedCards + (health.conflicts - deletedCards.toSet())).take(limit).forEach { conflict -> key(conflict.id, conflict.revision, conflict.generation) {
                    val open = conflict.id in openConflicts
                    val deletedThere = conflict.remote == null
                    // One line per conflict; tapping it opens the versions and the choices.
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f))
                        .clickable { openConflicts = if (open) openConflicts - conflict.id else openConflicts + conflict.id }
                        .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(conflictTitle(conflict), color = Color.White, fontSize = 15.sp, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(when {
                                conflict.kind == "CARD" && deletedThere -> "Hesapta silinmiş"
                                conflict.kind == "CARD" -> "İki tarafta da değişti"
                                else -> "Kayıt sürümleri çakışıyor"
                            }, color = Color(0xFFFFD27A), fontSize = 12.sp)
                        }
                        Text(if (open) "▴" else "▾", color = Color(0xFF9A9AA4), fontSize = 16.sp)
                    }
                    if (open) {
                        ConnectionNote(if (deletedThere) "Bu cihazda duruyor ama hesabında silinmiş görünüyor. Ne olacağını sen seç."
                            else "Otomatik olarak bir sürüm seçilmedi. Seçimden önce iki sürümü karşılaştır.")
                        BranchPreview("Bu cihazdaki güncel sürüm", conflict.currentLocal)
                        if (conflict.savedLocal != conflict.currentLocal) BranchPreview("Çakışma anında korunan yerel sürüm", conflict.savedLocal)
                        BranchPreview("Bulut sürümü", conflict.remote)
                        fun select(choice: SyncConflictChoice) { confirmation = conflict to choice }
                        if (conflict.kind == "CARD" && conflict.currentLocal != null && deletedThere)
                            ConnectionPill("Hesaba geri yükle", { select(SyncConflictChoice.LOCAL_AS_NEW_CARD) }, enabled = canAct, small = true)
                        if (conflict.remote != null || conflict.currentLocal == null || conflict.revision == 0L)
                            ConnectionPill("Bu cihazdaki sürümü koru", { select(SyncConflictChoice.CURRENT_LOCAL) }, enabled = canAct, small = true)
                        if (conflict.savedLocal != conflict.currentLocal && (conflict.remote != null || conflict.savedLocal == null || conflict.revision == 0L))
                            ConnectionPill("Korunan yerel sürümü seç", { select(SyncConflictChoice.SAVED_LOCAL) }, enabled = canAct, small = true)
                        ConnectionPill(if (deletedThere) "Silmeyi uygula (bu cihazdan da kaldır)" else "Bulut sürümünü kullan", { select(SyncConflictChoice.REMOTE) }, enabled = canAct, small = true)
                        if (conflict.kind == "CARD" && conflict.currentLocal != null && !deletedThere)
                            ConnectionPill("Yereli yeni kart olarak sakla", { select(SyncConflictChoice.LOCAL_AS_NEW_CARD) }, enabled = canAct, small = true)
                        val chosen = confirmation?.takeIf { it.first.id == conflict.id }
                        if (chosen != null) {
                            ConnectionNote(when (chosen.second) {
                                SyncConflictChoice.CURRENT_LOCAL -> "Bu cihazdaki sürüm eşitlenecek. Bulut sürümü değişecek."
                                SyncConflictChoice.SAVED_LOCAL -> "Güncel yerel sürüm yerine çakışma anında korunan sürüm kullanılacak."
                                SyncConflictChoice.REMOTE -> if (deletedThere) "Kart bu cihazdan da kaldırılacak." else "Bu cihazda bulut sürümü uygulanacak."
                                SyncConflictChoice.LOCAL_AS_NEW_CARD -> if (deletedThere)
                                    "Kart hesaba yeni bir kart olarak gönderilecek; bu cihazda içeriği aynen kalır. Takvim yayımlama kaydı, Pano referansı ve paylaşım bağlantısı yeni karta bağlanmaz."
                                    else "Yerel içerik ayrı bir kartta korunacak; eski karta bulut sürümü uygulanacak. Plan ve saat içeriği de kopyalanır. Takvim yayımlama kaydı, Pano referansı ve üretilmiş paylaşım bağlantısı yeni karta bağlanmaz."
                            })
                            ConnectionPill("Onayla", { action { repository.choose(chosen.first, chosen.second); confirmation = null } }, enabled = canAct, small = true)
                            ConnectionPill("Vazgeç", { confirmation = null }, enabled = !busy, small = true)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                } }
                if (health.issues.size > limit || health.conflicts.size > limit || health.rejected.size > limit)
                    ConnectionPill("Daha fazla göster", { limit += 20 }, small = true)
            }
        } ?: ConnectionNote("Durum yükleniyor…")
        error?.let { ConnectionNote(it) }
        if (busy) ConnectionNote("İşlem sürüyor…")
    }
}

/** The card's own title (else a short label) for its one-line row. */
private fun conflictTitle(c: PersistedSyncConflict): String = try {
    JSONObject(c.currentLocal ?: c.remote ?: "{}").let { o ->
        o.optString("text").trim().ifBlank { o.optString("name").trim() }.ifBlank { if (c.kind == "CARD") "İçerik kartı" else "Kayıt" }
    }
} catch (_: Exception) { if (c.kind == "CARD") "İçerik kartı" else "Kayıt" }

@Composable
private fun BranchPreview(label: String, payload: String?) {
    Text(label, color = Color(0xFF6FD6FF), fontSize = 13.sp)
    val preview = remember(payload) { syncBranchPreview(payload) }
    ConnectionNote(preview)
}

internal fun syncBranchPreview(payload: String?): String {
    if (payload == null) return "Silinmiş kayıt"
    return try {
        val obj = JSONObject(payload)
        buildList {
            obj.optString("text").takeIf { it.isNotBlank() }?.let { add(it.take(600)) }
            obj.optJSONArray("checklistItems")?.let { list ->
                for (i in 0 until minOf(list.length(), 8)) list.optJSONObject(i)?.let { add("• " + it.optString("text").take(120)) }
                if (list.length() > 8) add("… ${list.length()} kontrol maddesi")
            }
            for ((key, label) in listOf("attachments" to "ek", "voiceRecordings" to "ses kaydı", "musicBlocks" to "müzik", "mediaBlocks" to "film/dizi")) {
                obj.optJSONArray(key)?.let { list -> if (list.length()>0) {
                    add("${list.length()} $label")
                    for (i in 0 until minOf(list.length(), 4)) list.optJSONObject(i)?.let { item ->
                        (item.optString("title").ifBlank { item.optString("name") }).takeIf { it.isNotBlank() }?.let { add(it.take(100)) }
                    }
                } }
            }
            obj.optJSONObject("cardStack")?.optJSONArray("cards")?.let { add("${it.length()} stack kartı") }
            if (!obj.isNull("mood")) add("Mood içeriyor")
            if (!obj.isNull("location")) add("Konum içeriyor")
            if (obj.optLong("alertAt",0)>0) add("Planlı saat: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(obj.getLong("alertAt"))))
            if (obj.optLong("updatedAt",0)>0) add("Düzenleme: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(obj.getLong("updatedAt"))))
            if (isEmpty()) add("İçerik kaydı · alanlar: ${obj.length()}")
        }.joinToString("\n")
    } catch (_: Exception) { "İçerik önizlenemedi. Sürümler korunuyor." }
}
/** Plain words for the worker's refusal, with its code so it can be traced. */
private fun SyncRejectedRecord.explanation(): String {
    val tag = code ?: "HTTP $status"
    return when (code) {
        "INVALID_CARD", "INVALID_SYNC_PAYLOAD", "INVALID_CARD_LAYOUT", "LOCAL_ONLY_FIELD", "LOCAL_IMAGE_URI", "INVALID_ID",
        "INVALID_LOCAL_ID", "INVALID_RELATION", "INVALID_SHARE_PROVENANCE" -> "Kartın bir alanı hesabın kurallarına uymadı ($tag)."
        "SYNC_ARRAY_LIMIT", "ATTACHMENT_COUNT_LIMIT", "PAYLOAD_TOO_LARGE" -> "Kart eşitleme sınırını aşıyor ($tag)."
        "TOMBSTONE" -> "Bu kart hesabında silinmiş ($tag)."
        else -> if (status == 413) "Kart eşitleme sınırını aşıyor ($tag)." else "Hesap bu kaydı kabul etmedi ($tag)."
    }
}
private fun SyncBinaryCode.explanation() = when (this) {
    SyncBinaryCode.LOCAL_FILE_UNAVAILABLE -> "Yerel dosya bulunamıyor veya okuma izni yok."
    SyncBinaryCode.FILE_TOO_LARGE -> "Ek, eşitleme aktarım sınırını aşıyor."
    SyncBinaryCode.EMPTY_FILE -> "Dosya boş; içerik kendiliğinden kaldırılmadı."
    SyncBinaryCode.REMOTE_FILE_UNAVAILABLE -> "Buluttaki ek henüz hazır değil veya artık erişilemiyor."
    SyncBinaryCode.TRANSFER_PENDING -> "Ek aktarımı tamamlanmadı."
    SyncBinaryCode.IO_FAILURE -> "Dosya veya ağ erişimi tamamlanamadı."
    SyncBinaryCode.INVALID_BINARY -> "Ekin boyutu veya bağlı olduğu kart doğrulanamadı."
}
