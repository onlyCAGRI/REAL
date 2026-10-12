package com.cagri.real.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
            ConnectionNote("${health.status.pending} bekleyen kayıt · ${health.conflicts.size} çakışma · ${health.issues.size} ek sorunu")
            if (health.paused) ConnectionNote("Bulut eşitlemesi kapalı. Yerel verilerin korunuyor.")
            else if (health.status.lastError != null) ConnectionNote("Son eşitleme tamamlanamadı. Bağlantını kontrol et; bekleyen kayıtlar silinmez.")
            else if (health.conflicts.isEmpty() && health.issues.isEmpty()) ConnectionNote("Kaydedilmiş bir çakışma veya ek hatası yok.")
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
                health.conflicts.take(limit).forEach { conflict -> key(conflict.id, conflict.revision, conflict.generation) {
                    Text(if (conflict.kind == "CARD") "Kart sürümleri çakışıyor" else "Kayıt sürümleri çakışıyor", color = Color.White, fontSize = 16.sp)
                    ConnectionNote("Otomatik olarak bir sürüm seçilmedi. Seçimden önce iki sürümü karşılaştır.")
                    BranchPreview("Bu cihazdaki güncel sürüm", conflict.currentLocal)
                    if (conflict.savedLocal != conflict.currentLocal) BranchPreview("Çakışma anında korunan yerel sürüm", conflict.savedLocal)
                    BranchPreview("Bulut sürümü", conflict.remote)
                    fun select(choice: SyncConflictChoice) { confirmation = conflict to choice }
                    if (conflict.remote != null || conflict.currentLocal == null || conflict.revision == 0L)
                        ConnectionPill("Bu cihazdaki sürümü koru", { select(SyncConflictChoice.CURRENT_LOCAL) }, enabled = canAct, small = true)
                    if (conflict.savedLocal != conflict.currentLocal && (conflict.remote != null || conflict.savedLocal == null || conflict.revision == 0L))
                        ConnectionPill("Korunan yerel sürümü seç", { select(SyncConflictChoice.SAVED_LOCAL) }, enabled = canAct, small = true)
                    ConnectionPill(if (conflict.remote == null) "Buluttaki silmeyi uygula" else "Bulut sürümünü kullan", { select(SyncConflictChoice.REMOTE) }, enabled = canAct, small = true)
                    if (conflict.kind == "CARD" && conflict.currentLocal != null)
                        ConnectionPill("Yereli yeni kart olarak sakla", { select(SyncConflictChoice.LOCAL_AS_NEW_CARD) }, enabled = canAct, small = true)
                    if (conflict.remote == null) ConnectionNote("Bulutta silinen eski kart yeniden canlandırılmaz. Yerel sürüm yeni bir kart olarak korunabilir.")
                    val chosen = confirmation?.takeIf { it.first.id == conflict.id }
                    if (chosen != null) {
                        ConnectionNote(when (chosen.second) {
                            SyncConflictChoice.CURRENT_LOCAL -> "Bu cihazdaki sürüm eşitlenecek. Bulut sürümü değişecek."
                            SyncConflictChoice.SAVED_LOCAL -> "Güncel yerel sürüm yerine çakışma anında korunan sürüm kullanılacak."
                            SyncConflictChoice.REMOTE -> "Bu cihazda bulut sürümü uygulanacak; bulutta silindiyse kart kaldırılacak."
                            SyncConflictChoice.LOCAL_AS_NEW_CARD -> "Yerel içerik ayrı bir kartta korunacak; eski karta bulut sürümü uygulanacak. Plan ve saat içeriği de kopyalanır. Takvim yayımlama kaydı, Pano referansı ve üretilmiş paylaşım bağlantısı yeni karta bağlanmaz."
                        })
                        ConnectionPill("Onayla", { action { repository.choose(chosen.first, chosen.second); confirmation = null } }, enabled = canAct, small = true)
                        ConnectionPill("Vazgeç", { confirmation = null }, enabled = !busy, small = true)
                    }
                    Spacer(Modifier.height(10.dp))
                } }
                if (health.issues.size > limit || health.conflicts.size > limit)
                    ConnectionPill("Daha fazla göster", { limit += 20 }, small = true)
            }
        } ?: ConnectionNote("Durum yükleniyor…")
        error?.let { ConnectionNote(it) }
        if (busy) ConnectionNote("İşlem sürüyor…")
    }
}

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
private fun SyncBinaryCode.explanation() = when (this) {
    SyncBinaryCode.LOCAL_FILE_UNAVAILABLE -> "Yerel dosya bulunamıyor veya okuma izni yok."
    SyncBinaryCode.FILE_TOO_LARGE -> "Ek, eşitleme aktarım sınırını aşıyor."
    SyncBinaryCode.EMPTY_FILE -> "Dosya boş; içerik kendiliğinden kaldırılmadı."
    SyncBinaryCode.REMOTE_FILE_UNAVAILABLE -> "Buluttaki ek henüz hazır değil veya artık erişilemiyor."
    SyncBinaryCode.TRANSFER_PENDING -> "Ek aktarımı tamamlanmadı."
    SyncBinaryCode.IO_FAILURE -> "Dosya veya ağ erişimi tamamlanamadı."
    SyncBinaryCode.INVALID_BINARY -> "Ekin boyutu veya bağlı olduğu kart doğrulanamadı."
}
