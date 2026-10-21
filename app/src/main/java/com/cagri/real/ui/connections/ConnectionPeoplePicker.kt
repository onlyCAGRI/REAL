package com.cagri.real.ui.connections

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cagri.real.auth.AuthUser
import com.cagri.real.connections.RealConnection
import com.cagri.real.platform.android.auth.AndroidBackend
import com.cagri.real.platform.android.connections.RealConnections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun currentConnectionsUser(): AuthUser? {
    var user by remember { mutableStateOf(AndroidBackend.auth.currentUser()) }
    DisposableEffect(Unit) {
        val subscription = AndroidBackend.auth.observeUser { user = it }
        onDispose { subscription.cancel() }
    }
    return user?.takeUnless { it.isAnonymous }
}

private val EmailPattern = Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")

/** The exact same people list, add/invite and lifecycle UI is used by Card Share and Pano. */
@Composable
fun ConnectionPeoplePicker(enabled: Boolean = true, actionLabel: String, onSelect: suspend (RealConnection) -> Unit) {
    val context = LocalContext.current
    val user = currentConnectionsUser()
    if (user == null) { ConnectionNote("Kayıtlı REAL kişilerin için giriş yap."); return }
    if (user.displayName.isNullOrBlank()) {
        com.cagri.real.ui.auth.ProfileNameForm(user, onSaved = {})
        return
    }
    key(user.id) {
        val repository = remember(user.id) { RealConnections.repository(context, user.id) }
        val snapshot by repository.state.collectAsState()
        val scope = rememberCoroutineScope()
        var busy by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }
        var add by rememberSaveable { mutableStateOf(false) }
        var manage by rememberSaveable { mutableStateOf(false) }
        var email by rememberSaveable { mutableStateOf("") }
        var inviteUrl by rememberSaveable { mutableStateOf<String?>(null) }
        var confirm by remember { mutableStateOf<Pair<RealConnection, Boolean>?>(null) }
        fun runAction(action: suspend () -> Unit) {
            if (busy || !enabled) return
            busy = true; message = null
            scope.launch {
                try { action() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { message = failure.message ?: "İşlem tamamlanamadı. Tekrar dene." }
                finally { busy = false }
            }
        }
        fun share(url: String) {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Seni REAL'de arkadaş olarak eklemek istiyorum. Daveti kabul etmek için: $url")
            }, "REAL davetini gönder"))
        }
        val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let { selectedUri ->
                try {
                    // Query ONLY the URI granted by explicit ACTION_PICK. No broad address-book scan.
                    context.contentResolver.query(selectedUri, arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS), null, null, null)?.use {
                        if (it.moveToFirst()) { email = it.getString(0).orEmpty(); inviteUrl = null }
                    }
                } catch (_: Exception) { message = "Seçilen e-posta okunamadı. E-posta yaz ya da genel davet linkini kullan." }
            }
        }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(repository, lifecycle) {
            try { repository.load() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (AndroidBackend.auth.currentUser()?.id != user.id) return@LaunchedEffect
                message = "Yerel kişi listesi okunamadı; çevrimiçi kayıt yeniden yükleniyor."
            }
            // Keep cached people visible while paused; resume refreshes immediately, then polls as before.
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    try { repository.refresh(); message = null }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message = "Güncellenemedi. Kayıtlı kişiler gösteriliyor; gönderim için internet gerekir." }
                    delay(30_000)
                }
            }
        }
        val canAct = enabled && !busy
        val emailValid = email.isBlank() || email.matches(EmailPattern)

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Başlık satırı: sayı + hızlı eylemler
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Kişilerim", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (snapshot.people.isEmpty()) "Henüz kimse yok" else "${snapshot.people.size} kişi",
                        color = ConnectionPalette.Muted, fontSize = 13.sp)
                }
                if (busy) com.cagri.real.ui.components.RealSpinner(Modifier.size(20.dp).padding(end = 0.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                ConnectionPill("Yenile", { runAction { repository.refresh() } }, enabled = canAct, small = true)
                Spacer(Modifier.width(6.dp))
                ConnectionPill(if (manage) "Bitti" else "Yönet", { manage = !manage }, enabled = enabled, filled = manage, small = true)
            }

            if (snapshot.people.isEmpty()) {
                ConnectionCard {
                    Text("Henüz REAL bağlantın yok", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("Bir arkadaşını davet et; kabul ettiğinde hem kart göndermek hem Pano'ya eklemek için listende görünür.",
                        color = ConnectionPalette.Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }

            snapshot.people.forEach { person ->
                ConnectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConnectionAvatar(person.name, person.uid, pictureVersion = person.pictureVersion)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(person.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("REAL bağlantısı · " + java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                                .format(java.util.Date(person.acceptedAt)), color = ConnectionPalette.Muted, fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (!manage) {
                            Spacer(Modifier.width(8.dp))
                            ConnectionPill(actionLabel, { runAction { onSelect(person); message = "$actionLabel: ${person.name}" } },
                                enabled = canAct, filled = true, small = true)
                        }
                    }
                    AnimatedVisibility(manage, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ConnectionPill("Kaldır", { confirm = person to false }, enabled = canAct, small = true)
                            ConnectionPill("Engelle", { confirm = person to true }, enabled = canAct, danger = true, small = true)
                        }
                    }
                }
            }

            // Kişi ekle: kapalıyken tek geniş düğme, açılınca davet paneli
            if (!add) ConnectionPill("+  Arkadaş ekle", { add = true }, Modifier.fillMaxWidth(), enabled = enabled)
            AnimatedVisibility(add, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                ConnectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Arkadaş davet et", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Davet bir kez kabul edilince kişi hem kart hem Pano için kaydedilir.",
                                color = ConnectionPalette.Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        ConnectionPill("Kapat", { add = false }, small = true)
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = email, onValueChange = { email = it.take(254); inviteUrl = null },
                        label = { Text("E-posta (isteğe bağlı)") }, singleLine = true, enabled = canAct,
                        isError = !emailValid, modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White, disabledTextColor = Color.White.copy(alpha = .5f),
                            focusedBorderColor = ConnectionPalette.Accent, unfocusedBorderColor = ConnectionPalette.Stroke,
                            focusedLabelColor = ConnectionPalette.Accent, unfocusedLabelColor = ConnectionPalette.Muted,
                            cursorColor = ConnectionPalette.Accent, errorBorderColor = ConnectionPalette.Danger,
                            errorLabelColor = ConnectionPalette.Danger, errorTextColor = Color.White,
                        ),
                    )
                    Text(
                        if (email.isBlank()) "E-posta boş bırakılırsa linki alan ilk REAL hesabı daveti kabul edebilir."
                        else "Yalnız bu e-postayla doğrulanmış REAL hesabı daveti kabul edebilir.",
                        color = ConnectionPalette.Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    ConnectionPill("Kişilerimden e-posta seç", {
                        try { contactPicker.launch(Intent(Intent.ACTION_PICK).apply { type = ContactsContract.CommonDataKinds.Email.CONTENT_TYPE }) }
                        catch (_: Exception) { message = "Kişi seçici açılamadı. E-posta yaz ya da davet linkini paylaş." }
                    }, Modifier.fillMaxWidth(), enabled = canAct)
                    Spacer(Modifier.height(8.dp))
                    ConnectionPill(if (inviteUrl == null) "Davet linki oluştur ve paylaş" else "Daveti tekrar paylaş", {
                        runAction {
                            val url = inviteUrl ?: repository.invite(email.takeIf { it.isNotBlank() }).url.also { inviteUrl = it }
                            share(url)
                        }
                    }, Modifier.fillMaxWidth(), enabled = canAct && emailValid, filled = true)
                    inviteUrl?.let { url ->
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = .05f)).padding(start = 14.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(url.removePrefix("https://"), Modifier.weight(1f), color = ConnectionPalette.Accent, fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            ConnectionPill("Kopyala", {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("REAL daveti", url))
                                message = "Davet linki kopyalandı."
                            }, enabled = canAct, small = true)
                        }
                        Text("Davet 7 gün geçerli.", color = ConnectionPalette.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                    }
                }
            }

            if (manage) {
                if (snapshot.invitations.isNotEmpty()) Text("Bekleyen davetler", color = Color.White, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
                snapshot.invitations.forEach { invite ->
                    ConnectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Bekleyen davet", color = Color.White, fontSize = 15.sp)
                                Text("Oluşturuldu: " + java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                                    .format(java.util.Date(invite.createdAt)), color = ConnectionPalette.Muted, fontSize = 12.sp)
                            }
                            ConnectionPill("İptal et", { runAction { repository.revoke(invite.id); inviteUrl = null } }, enabled = canAct, small = true)
                        }
                    }
                }
                if (snapshot.blocked.isNotEmpty()) Text("Engellenenler", color = Color.White, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
                snapshot.blocked.forEach { person ->
                    ConnectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ConnectionAvatar(person.name, person.uid, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(person.name, Modifier.weight(1f), color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            ConnectionPill("Engeli kaldır", { runAction { repository.unblock(person.uid) } }, enabled = canAct, small = true)
                        }
                    }
                }
                Text("Bağlantıyı kaldırmak mevcut kart kopyalarını veya Pano üyeliğini silmez. Pano üyelerini People alanından yönet.",
                    color = ConnectionPalette.Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 4.dp))
            }
            message?.let { ConnectionNote(it) }
        }
        confirm?.let { (person, block) ->
            com.cagri.real.ui.components.RealAlertDialog(onDismissRequest = { confirm = null },
                title = { Text(if (block) "${person.name} engellensin mi?" else "${person.name} kaldırılsın mı?") },
                text = { Text("Yeni doğrudan kart gönderimleri ve bağlantı üzerinden Pano davetleri durur. Mevcut kartlar ve üyelikler korunur.") },
                confirmButton = { TextButton(onClick = { confirm = null; runAction { if (block) repository.block(person) else repository.remove(person) } }) { Text("Onayla") } },
                dismissButton = { TextButton(onClick = { confirm = null }) { Text("Vazgeç") } })
        }
    }
}
