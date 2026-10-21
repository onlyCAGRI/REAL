package com.cagri.real.ui.sharedcategories

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import com.cagri.real.ui.components.realSheetSurface
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cagri.real.data.CustomCategory
import com.cagri.real.sharedcategories.*
import com.cagri.real.ui.connections.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Category membership is independent of the global Connections relationship. */
@Composable
fun SharedCategoryMembersDialog(
    category: CustomCategory,
    initialLink: String,
    onChanged: (SharedCategoryDetail) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val user = currentConnectionsUser()
    val scope = rememberCoroutineScope()
    val api = remember(user?.id) { user?.let { SharedCategoryApi(context.applicationContext, it.id) } }
    var detail by remember(category.id, user?.id) { mutableStateOf<SharedCategoryDetail?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<SharedCategoryMember?>(null) }
    suspend fun refresh() {
        val client = checkNotNull(api) { "REAL hesabınla giriş yap." }
        val item = detail?.item ?: client.ensure(category, initialLink)
        val loaded = client.metadata(item.id)
        detail = loaded; onChanged(loaded)
    }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "İşlem tamamlanamadı." }
            finally { busy = false }
        }
    }
    LaunchedEffect(category.id, api) {
        busy = true
        try { refresh() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message }
        finally { busy = false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // A bottom sheet like the notification / SYNC question.
        val window = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        val sheetIn = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(Unit) { sheetIn.animateTo(1f, androidx.compose.animation.core.tween(420, easing = com.cagri.real.ui.components.RealMotion.Emphasized)) }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f * sheetIn.value))
                .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 680.dp)
                .graphicsLayer { translationY = size.height * (1f - sheetIn.value) }
                .realSheetSurface()
                .navigationBarsPadding().imePadding()
                .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = {}),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            com.cagri.real.ui.components.RealSheetHandle()
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(category.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Members · ${detail?.members?.size ?: 0}", color = ConnectionPalette.Muted, fontSize = 13.sp)
                }
                ConnectionPill("Bitti", onDismiss, filled = true, small = true)
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ConnectionNote("Üyeler aynı kategori hedeflerini ve ortak linki görüntüler. Kartlar onların ana ekranına kopyalanmaz. Düzenleme sende kalır.")

                detail?.members?.takeIf { it.isNotEmpty() }?.let { list ->
                    val groupShape = RoundedCornerShape(22.dp)
                    Column(Modifier.fillMaxWidth().clip(groupShape).background(ConnectionPalette.Glass).border(1.dp, ConnectionPalette.Stroke, groupShape)) {
                        list.forEachIndexed { index, member ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ConnectionAvatar(member.name, key = member.uid, size = 42.dp, pictureVersion = member.pictureVersion)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(member.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (member.isOwner) Text("Owner", color = ConnectionPalette.Accent, fontSize = 12.sp)
                                }
                                if (!member.isOwner) ConnectionPill("Kaldır", { removing = member }, enabled = !busy, small = true, danger = true)
                            }
                            if (index != list.lastIndex) {
                                Box(Modifier.fillMaxWidth().padding(start = 70.dp).height(0.5.dp).background(ConnectionPalette.Stroke))
                            }
                        }
                    }
                }

                if (detail != null) {
                    Text("KİŞİ EKLE", color = ConnectionPalette.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp, modifier = Modifier.padding(start = 6.dp))
                    ConnectionPeoplePicker(enabled = !busy, actionLabel = "Ekle") { person ->
                        checkNotNull(api).add(checkNotNull(detail).item.id, person.uid)
                        refresh()
                    }
                    ConnectionPill("Kategori davet linki paylaş", { action {
                        val url = checkNotNull(api).invite(checkNotNull(detail).item.id)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${category.name} kategorime katıl: $url")
                        }, "REAL kategori daveti"))
                    } }, Modifier.fillMaxWidth(), enabled = !busy, filled = true)
                    Text("Davet 7 gün geçerli ve bir kişi tarafından kullanılabilir. Kaldırmak, kişinin REAL bağlantısını silmez.",
                        color = ConnectionPalette.Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 6.dp))
                }
                if (busy) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    com.cagri.real.ui.components.RealSpinner(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                }
                error?.let { ConnectionNote(it); ConnectionPill("Yeniden dene", { action { refresh() } }, enabled = !busy) }
            }
        }
        }
    }
    removing?.let { member ->
        com.cagri.real.ui.components.RealAlertDialog(onDismissRequest = { removing = null }, containerColor = Color(0xFF17171B), shape = RoundedCornerShape(28.dp),
            title = { Text("${member.name} kaldırılsın mı?", color = Color.White) },
            text = { Text("Bu kategorinin çevrimiçi erişimi kesilir. Kişilerim bağlantınız korunur.", color = Color.Gray) },
            confirmButton = { TextButton(onClick = { removing = null; action {
                checkNotNull(api).remove(checkNotNull(detail).item.id, member.uid); refresh()
            } }) { Text("Kaldır") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Vazgeç") } })
    }
}
