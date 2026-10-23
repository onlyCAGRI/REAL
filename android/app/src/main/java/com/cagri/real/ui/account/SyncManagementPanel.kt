package com.cagri.real.ui.account

import com.cagri.real.i18n.t
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
            catch (_: Exception) { error = t("İşlem tamamlanamadı veya sürüm değişti. Yenile ve tekrar seç; kayıtların korunuyor.") }
            finally { busy = false }
        }
    }
    LaunchedEffect(repository) {
        try { refresh() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = t("Eşitleme durumu alınamadı.") }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        snapshot?.let { health ->
            val waiting = health.conflicts.size + health.issues.size + health.rejected.size
            ConnectionNote(t("{0} bekleyen kayıt · {1} çakışma · {2} ek sorunu", health.status.pending, health.conflicts.size, health.issues.size) +
                (if (health.rejected.isNotEmpty()) t(" · {0} reddedilen kayıt", health.rejected.size) else ""))
            if (health.paused) ConnectionNote(t("Bulut eşitlemesi kapalı. Yerel verilerin korunuyor."))
            // The reason is shown too ("Bağlantını kontrol et" alone hid failures that had nothing to do with the connection).
            else if (health.status.lastError != null) ConnectionNote(t("Son eşitleme tamamlanamadı; bekleyen kayıtlar silinmez.\nSebep: {0}", health.status.lastError))
            else if (waiting == 0) ConnectionNote(t("Kaydedilmiş bir çakışma veya ek hatası yok."))
            else ConnectionNote(t("Diğer kayıtlar eşitlendi. Aşağıdaki {0} kayıt senin seçimini bekliyor; hiçbiri silinmedi.", waiting))
            ConnectionPill(if (expanded) t("Ayrıntıları gizle") else t("Sorunları ve sürümleri göster"), { expanded = !expanded }, small = true)
            ConnectionPill(t("Yenile"), { action { refresh() } }, enabled = enabled && !busy, small = true)
            val canAct = enabled && !busy && !health.paused
            if (expanded) {
                health.issues.take(limit).forEach { issue -> key(issue.token) {
                    Text(issue.title.ifBlank { t("İçerik kartı") }, color = Color.White, fontSize = 15.sp)
                    ConnectionNote(if (issue.direction == SyncBinaryDirection.UPLOAD) t("Bu kartın bir eki buluta gönderilemedi. Yerel içerik kaldırılmadı.")
                        else t("Bu kartın bulut sürümü ve ekleri bekliyor. Diğer kartlar eşitlenebilir."))
                    ConnectionNote(issue.code.explanation())
                    ConnectionNote(if (issue.automaticRetry) t("Bağlantı uygun olduğunda mevcut eşitleme yeniden dener.")
                        else t("Sürekli tekrar denenmez. Dosyayı/izni düzeltip tekrar dene; istersen eki kart düzenleyicisinden kendin değiştir."))
                    ConnectionPill(t("Bu eki tekrar dene"), { action { repository.retry(issue.token) } }, enabled = canAct, small = true)
                    Spacer(Modifier.height(6.dp))
                } }
                if (health.rejected.size > 1) {
                    Text(t("Hesabın kabul etmediği {0} kayıt", health.rejected.size), color = Color.White, fontSize = 16.sp)
                    ConnectionNote(t("Hepsi bu cihazda duruyor. Tekrar denersen her biri yeniden gönderilir."))
                    ConnectionPill(t("Hepsini tekrar dene"), {
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
                            Text(rejected.title.ifBlank { if (rejected.kind == "CARD") t("İçerik kartı") else t("Kayıt") }, color = Color.White, fontSize = 15.sp,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(t("Hesap kabul etmedi"), color = Color(0xFFFF9C8F), fontSize = 12.sp)
                        }
                        Text(if (open) "▴" else "▾", color = Color(0xFF9A9AA4), fontSize = 16.sp)
                    }
                    if (open) {
                        ConnectionNote(rejected.explanation())
                        ConnectionNote(if (rejected.hasCloudCopy) t("Bu cihazdaki sürüm korunuyor. Kartı düzenlersen kendiliğinden tekrar gönderilir.")
                            else t("Bu kart hesabına hiç ulaşmadı; bu cihazdaki sürüm korunuyor."))
                        ConnectionPill(t("Tekrar dene"), { action { repository.retryRejected(rejected.recordId) } }, enabled = canAct, small = true)
                        ConnectionPill(if (rejected.hasCloudCopy) t("Hesaptakini kullan") else t("Bu cihazdan kaldır"),
                            { action { repository.useCloudForRejected(rejected.recordId) } }, enabled = canAct, small = true)
                    }
                    Spacer(Modifier.height(6.dp))
                } }
                // Cards the account deleted while they changed here come first, with one button for all of them.
                val deletedCards = health.conflicts.filter { it.kind == "CARD" && it.remote == null && it.currentLocal != null }
                if (deletedCards.size > 1) {
                    Text(t("Hesapta silinmiş {0} kart", deletedCards.size), color = Color.White, fontSize = 16.sp)
                    ConnectionNote(t("Bu kartlar bu cihazda duruyor ama hesabında silinmiş görünüyor. Geri yüklersen her biri hesaba yeni bir kart olarak gönderilir; bu cihazda hiçbir şey kaybolmaz."))
                    if (bulkConfirm) {
                        ConnectionPill(t("Onayla: {0} kartı geri yükle", deletedCards.size), {
                            action { deletedCards.forEach { repository.choose(it, SyncConflictChoice.LOCAL_AS_NEW_CARD) }; bulkConfirm = false }
                        }, enabled = canAct, small = true)
                        ConnectionPill(t("Vazgeç"), { bulkConfirm = false }, enabled = !busy, small = true)
                    } else ConnectionPill(t("Hepsini hesaba geri yükle"), { bulkConfirm = true }, enabled = canAct, small = true)
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
                                conflict.kind == "CARD" && deletedThere -> t("Hesapta silinmiş")
                                conflict.kind == "CARD" -> t("İki tarafta da değişti")
                                else -> t("Kayıt sürümleri çakışıyor")
                            }, color = Color(0xFFFFD27A), fontSize = 12.sp)
                        }
                        Text(if (open) "▴" else "▾", color = Color(0xFF9A9AA4), fontSize = 16.sp)
                    }
                    if (open) {
                        ConnectionNote(if (deletedThere) t("Bu cihazda duruyor ama hesabında silinmiş görünüyor. Ne olacağını sen seç.")
                            else t("Otomatik olarak bir sürüm seçilmedi. Seçimden önce iki sürümü karşılaştır."))
                        BranchPreview(t("Bu cihazdaki güncel sürüm"), conflict.currentLocal)
                        if (conflict.savedLocal != conflict.currentLocal) BranchPreview(t("Çakışma anında korunan yerel sürüm"), conflict.savedLocal)
                        BranchPreview(t("Bulut sürümü"), conflict.remote)
                        fun select(choice: SyncConflictChoice) { confirmation = conflict to choice }
                        if (conflict.kind == "CARD" && conflict.currentLocal != null && deletedThere)
                            ConnectionPill(t("Hesaba geri yükle"), { select(SyncConflictChoice.LOCAL_AS_NEW_CARD) }, enabled = canAct, small = true)
                        if (conflict.remote != null || conflict.currentLocal == null || conflict.revision == 0L)
                            ConnectionPill(t("Bu cihazdaki sürümü koru"), { select(SyncConflictChoice.CURRENT_LOCAL) }, enabled = canAct, small = true)
                        if (conflict.savedLocal != conflict.currentLocal && (conflict.remote != null || conflict.savedLocal == null || conflict.revision == 0L))
                            ConnectionPill(t("Korunan yerel sürümü seç"), { select(SyncConflictChoice.SAVED_LOCAL) }, enabled = canAct, small = true)
                        ConnectionPill(if (deletedThere) t("Silmeyi uygula (bu cihazdan da kaldır)") else t("Bulut sürümünü kullan"), { select(SyncConflictChoice.REMOTE) }, enabled = canAct, small = true)
                        if (conflict.kind == "CARD" && conflict.currentLocal != null && !deletedThere)
                            ConnectionPill(t("Yereli yeni kart olarak sakla"), { select(SyncConflictChoice.LOCAL_AS_NEW_CARD) }, enabled = canAct, small = true)
                        val chosen = confirmation?.takeIf { it.first.id == conflict.id }
                        if (chosen != null) {
                            ConnectionNote(when (chosen.second) {
                                SyncConflictChoice.CURRENT_LOCAL -> t("Bu cihazdaki sürüm eşitlenecek. Bulut sürümü değişecek.")
                                SyncConflictChoice.SAVED_LOCAL -> t("Güncel yerel sürüm yerine çakışma anında korunan sürüm kullanılacak.")
                                SyncConflictChoice.REMOTE -> if (deletedThere) t("Kart bu cihazdan da kaldırılacak.") else t("Bu cihazda bulut sürümü uygulanacak.")
                                SyncConflictChoice.LOCAL_AS_NEW_CARD -> if (deletedThere)
                                    t("Kart hesaba yeni bir kart olarak gönderilecek; bu cihazda içeriği aynen kalır. Takvim yayımlama kaydı, Pano referansı ve paylaşım bağlantısı yeni karta bağlanmaz.")
                                    else t("Yerel içerik ayrı bir kartta korunacak; eski karta bulut sürümü uygulanacak. Plan ve saat içeriği de kopyalanır. Takvim yayımlama kaydı, Pano referansı ve üretilmiş paylaşım bağlantısı yeni karta bağlanmaz.")
                            })
                            ConnectionPill(t("Onayla"), { action { repository.choose(chosen.first, chosen.second); confirmation = null } }, enabled = canAct, small = true)
                            ConnectionPill(t("Vazgeç"), { confirmation = null }, enabled = !busy, small = true)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                } }
                if (health.issues.size > limit || health.conflicts.size > limit || health.rejected.size > limit)
                    ConnectionPill(t("Daha fazla göster"), { limit += 20 }, small = true)
            }
        } ?: ConnectionNote(t("Durum yükleniyor…"))
        error?.let { ConnectionNote(it) }
        if (busy) ConnectionNote(t("İşlem sürüyor…"))
    }
}

/** The card's own title (else a short label) for its one-line row. */
private fun conflictTitle(c: PersistedSyncConflict): String = try {
    JSONObject(c.currentLocal ?: c.remote ?: "{}").let { o ->
        o.optString("text").trim().ifBlank { o.optString("name").trim() }.ifBlank { if (c.kind == "CARD") t("İçerik kartı") else t("Kayıt") }
    }
} catch (_: Exception) { if (c.kind == "CARD") t("İçerik kartı") else t("Kayıt") }

@Composable
private fun BranchPreview(label: String, payload: String?) {
    Text(label, color = Color(0xFF6FD6FF), fontSize = 13.sp)
    val preview = remember(payload) { syncBranchPreview(payload) }
    ConnectionNote(preview)
}

internal fun syncBranchPreview(payload: String?): String {
    if (payload == null) return t("Silinmiş kayıt")
    return try {
        val obj = JSONObject(payload)
        buildList {
            obj.optString("text").takeIf { it.isNotBlank() }?.let { add(it.take(600)) }
            obj.optJSONArray("checklistItems")?.let { list ->
                for (i in 0 until minOf(list.length(), 8)) list.optJSONObject(i)?.let { add("• " + it.optString("text").take(120)) }
                if (list.length() > 8) add(t("… {0} kontrol maddesi", list.length()))
            }
            for ((key, label) in listOf("attachments" to t("ek"), "voiceRecordings" to t("ses kaydı"), "musicBlocks" to t("müzik"), "mediaBlocks" to t("film/dizi"))) {
                obj.optJSONArray(key)?.let { list -> if (list.length()>0) {
                    add("${list.length()} $label")
                    for (i in 0 until minOf(list.length(), 4)) list.optJSONObject(i)?.let { item ->
                        (item.optString("title").ifBlank { item.optString("name") }).takeIf { it.isNotBlank() }?.let { add(it.take(100)) }
                    }
                } }
            }
            obj.optJSONObject("cardStack")?.optJSONArray("cards")?.let { add(t("{0} stack kartı", it.length())) }
            if (!obj.isNull("mood")) add(t("Mood içeriyor"))
            if (!obj.isNull("location")) add(t("Konum içeriyor"))
            if (obj.optLong("alertAt",0)>0) add(t("Planlı saat: {0}", java.text.DateFormat.getDateTimeInstance().format(java.util.Date(obj.getLong("alertAt")))))
            if (obj.optLong("updatedAt",0)>0) add(t("Düzenleme: {0}", java.text.DateFormat.getDateTimeInstance().format(java.util.Date(obj.getLong("updatedAt")))))
            if (isEmpty()) add(t("İçerik kaydı · alanlar: {0}", obj.length()))
        }.joinToString("\n")
    } catch (_: Exception) { t("İçerik önizlenemedi. Sürümler korunuyor.") }
}
/** Plain words for the worker's refusal, with its code so it can be traced. */
private fun SyncRejectedRecord.explanation(): String {
    val tag = code ?: "HTTP ${status}"
    return when (code) {
        "INVALID_CARD", "INVALID_SYNC_PAYLOAD", "INVALID_CARD_LAYOUT", "LOCAL_ONLY_FIELD", "LOCAL_IMAGE_URI", "INVALID_ID",
        "INVALID_LOCAL_ID", "INVALID_RELATION", "INVALID_SHARE_PROVENANCE" -> t("Kartın bir alanı hesabın kurallarına uymadı ({0}).", tag)
        "SYNC_ARRAY_LIMIT", "ATTACHMENT_COUNT_LIMIT", "PAYLOAD_TOO_LARGE" -> t("Kart eşitleme sınırını aşıyor ({0}).", tag)
        "TOMBSTONE" -> t("Bu kart hesabında silinmiş ({0}).", tag)
        else -> if (status == 413) t("Kart eşitleme sınırını aşıyor ({0}).", tag) else t("Hesap bu kaydı kabul etmedi ({0}).", tag)
    }
}
private fun SyncBinaryCode.explanation() = when (this) {
    SyncBinaryCode.LOCAL_FILE_UNAVAILABLE -> t("Yerel dosya bulunamıyor veya okuma izni yok.")
    SyncBinaryCode.FILE_TOO_LARGE -> t("Ek, eşitleme aktarım sınırını aşıyor.")
    SyncBinaryCode.EMPTY_FILE -> t("Dosya boş; içerik kendiliğinden kaldırılmadı.")
    SyncBinaryCode.REMOTE_FILE_UNAVAILABLE -> t("Buluttaki ek henüz hazır değil veya artık erişilemiyor.")
    SyncBinaryCode.TRANSFER_PENDING -> t("Ek aktarımı tamamlanmadı.")
    SyncBinaryCode.IO_FAILURE -> t("Dosya veya ağ erişimi tamamlanamadı.")
    SyncBinaryCode.INVALID_BINARY -> t("Ekin boyutu veya bağlı olduğu kart doğrulanamadı.")
    SyncBinaryCode.STORAGE_FULL -> t("Bulut alanın doldu; ek bu telefonda duruyor. Yer açınca kendiliğinden yüklenir.")
}
