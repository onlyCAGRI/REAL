package com.cagri.real.ui.share

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.platform.android.share.CardShareClient
import com.cagri.real.ui.connections.ConnectionCard
import com.cagri.real.ui.connections.ConnectionNote
import com.cagri.real.ui.connections.ConnectionPalette
import com.cagri.real.ui.connections.ConnectionPill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Yalnız sahibine açık yönetim alanı: aktif paylaşım linkleri hemen listelenir; e-posta/kullanıcı dizini gösterilmez. */
@Composable
internal fun SharedLinksSection(client: CardShareClient, currentId: String?) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(true) }
    var links by remember { mutableStateOf<List<CardShareClient.SharedLink>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(client) {
        try { links = client.links(); error = null }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message }
        finally { busy = false }
    }
    val visible = links.filter { it.id != currentId }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Aktif paylaşım linkleri", Modifier.weight(1f), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
        }
        visible.forEach { link ->
            ConnectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(link.title.ifBlank { "Adsız kart" }, Modifier.weight(1f), color = Color.White, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    ConnectionPill("Paylaşımı durdur", {
                        busy = true
                        scope.launch {
                            try { client.revoke(link.id); links = links.filterNot { it.id == link.id }; error = null }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { error = failure.message }
                            finally { busy = false }
                        }
                    }, enabled = !busy, danger = true, small = true)
                }
            }
        }
        if (visible.isEmpty() && !busy) Text("Şu an aktif paylaşım linkin yok.", color = ConnectionPalette.Muted, fontSize = 13.sp)
        error?.let { ConnectionNote(it) }
    }
}
