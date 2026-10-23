package com.cagri.real.ui.account

import com.cagri.real.i18n.t
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.storage.LocalStorageRepository
import com.cagri.real.storage.LocalStorageUsage
import com.cagri.real.ui.connections.ConnectionCard
import com.cagri.real.ui.connections.ConnectionNote
import com.cagri.real.ui.connections.ConnectionPalette
import com.cagri.real.ui.connections.ConnectionPill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** "Bu cihazdaki alan" alt sayfasının içeriği. Başlık sayfada; yalnız açılışta ya da elle ölçer. */
@Composable
internal fun LocalStoragePanel() {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { LocalStorageRepository(context) }
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf<LocalStorageUsage?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun refresh(clear: Boolean = false) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { if (clear) repository.clearImageCache(); usage = repository.measure() }
            catch (c: CancellationException) { throw c }
            catch (_: Exception) { error = t("Depolama bilgisi okunamadı.") }
            finally { busy = false }
        }
    }
    LaunchedEffect(repository) { refresh() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ConnectionCard {
            Text(usage?.let { storageSize(it.total) } ?: t("Ölçülüyor…"), color = Color.White, fontSize = 24.sp)
            usage?.let { u ->
                Spacer(Modifier.height(10.dp))
                listOf(t("Kitaplar ve okuma verileri") to u.books, t("Fotoğraflar") to u.photos, t("GIF'ler") to u.gifs,
                    t("Sesler") to u.audio, t("Diğer ekler") to u.attachments, t("Pano kayıtları") to u.boardData,
                    t("Yerel veritabanı") to u.databases, t("Ayarlar") to u.preferences, t("Önbellek") to u.cache, t("Diğer") to u.other)
                    .filter { it.second > 0 }.forEach { (label, bytes) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, Modifier.weight(1f), color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
                            Text(storageSize(bytes), color = ConnectionPalette.Muted, fontSize = 13.sp)
                        }
                    }
            }
        }
        ConnectionNote(t("Kitaplar, Pano dosyaları ve bekleyen yüklemeler otomatik silinmez."))
        ConnectionPill(t("Görsel önbelleğini temizle"), { refresh(true) }, Modifier.fillMaxWidth(), enabled = !busy)
        ConnectionPill(t("Yeniden ölç"), { refresh() }, Modifier.fillMaxWidth(), enabled = !busy, small = true)
        error?.let { ConnectionNote(it) }
    }
}
private fun storageSize(bytes: Long) = if (bytes < 1024 * 1024) "%.1f KB".format(bytes / 1024.0) else "%.1f MB".format(bytes / 1048576.0)
