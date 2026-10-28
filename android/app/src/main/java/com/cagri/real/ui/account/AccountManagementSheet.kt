package com.cagri.real.ui.account

import com.cagri.real.i18n.t
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.account.*
import com.cagri.real.auth.AuthCancelled
import com.cagri.real.platform.android.account.AndroidAccountRepository
import com.cagri.real.platform.android.account.LocalAccountCleanup
import com.cagri.real.ui.auth.LocalAuthUiController
import com.cagri.real.ui.connections.ConnectionAvatar
import com.cagri.real.ui.connections.ConnectionCard
import com.cagri.real.ui.connections.ConnectionNote
import com.cagri.real.ui.connections.ConnectionPalette
import com.cagri.real.ui.connections.ConnectionPill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private enum class AccountPage { Main, Sync, Storage, Data }
private enum class RowGlyph { Sync, Device, Data }

/** Presentation + operation state for the sheet. Business rules stay in AndroidAccountRepository / Worker. */
@Stable
private class AccountUi(private val scope: CoroutineScope) {
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var usage by mutableStateOf<CloudUsage?>(null)
    var erasure by mutableStateOf<ErasureStatus?>(null)
    var deviceApproved by mutableStateOf(true)
    var pendingRestart by mutableStateOf(false)
    var confirmRestart by mutableStateOf(false)
    var restartAccepted by mutableStateOf(false)
    var cached by mutableStateOf(true)
    var remoteKnown by mutableStateOf(false)
    var confirm by mutableStateOf<ErasureMode?>(null)
    var accepted by mutableStateOf(false)
    var confirmPurge by mutableStateOf(false)
    var password by mutableStateOf("")
    var operationId by mutableStateOf(UUID.randomUUID().toString())

    fun run(work: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { work() }
            catch (_: AuthCancelled) { /* User cancelled provider chooser; nothing submitted. */ }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: t("İşlem tamamlanamadı.") }
            finally { busy = false }
        }
    }
}

private suspend fun refreshAccount(ui: AccountUi, repository: AndroidAccountRepository) {
    val snapshot = repository.refresh()
    ui.usage = snapshot.usage; ui.erasure = snapshot.erasure; ui.cached = false; ui.remoteKnown = true
    ui.deviceApproved = snapshot.backupDeviceApproved
    ui.pendingRestart = withContext(Dispatchers.IO) { repository.hasPendingRestart() }
}

/** Same sheet geometry, rows and pills as Card Share. */
/** [openSpace]: opened from the cloud-space notice, with the "Yer aç" list already open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagementSheet(onDismiss: () -> Unit, onAddAccount: () -> Unit, openSpace: Boolean = false) {
    val controller = LocalAuthUiController.current
    val user = controller?.user?.collectAsState()?.value
    val scope = rememberCoroutineScope()
    val ui = remember { AccountUi(scope) }
    var page by rememberSaveable { mutableStateOf(AccountPage.Main) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !ui.busy })
    ModalBottomSheet(onDismissRequest = { if (!ui.busy) onDismiss() }, sheetState = state,
        containerColor = ConnectionPalette.Ink, contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = .6f), shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(36.dp, 4.dp)
            .clip(CircleShape).background(Color.White.copy(alpha = .22f))) }) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            if (user == null) {
                AnimatedContent(page, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "accountPage") { p ->
                    if (p == AccountPage.Storage) SubPage(t("Bu cihazdaki alan"), { page = AccountPage.Main }) { LocalStoragePanel() }
                    else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PageTitle(t("Hesapları yönet"), t("Bu cihazda REAL hesabı bağlı değil."))
                        Spacer(Modifier.height(6.dp))
                        AccountRow(RowGlyph.Device, ConnectionPalette.Accent, t("Bu cihazdaki alan"), t("Önbellek ve yerel dosyalar"), true) { page = AccountPage.Storage }
                        Spacer(Modifier.height(6.dp))
                        ConnectionPill(t("Hesap ekle"), { onDismiss(); onAddAccount() }, Modifier.fillMaxWidth(), filled = true)
                        ConnectionPill(t("Kapat"), onDismiss, Modifier.fillMaxWidth(), small = true)
                    }
                }
            } else key(user.id) {
                val context = LocalContext.current.applicationContext
                val repository = remember(user.id) { AndroidAccountRepository(context, user.id) }
                LaunchedEffect(repository) {
                    try { ui.usage = withContext(Dispatchers.IO) { repository.cachedUsage() }; refreshAccount(ui, repository) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { ui.error = failure.message ?: t("Bulut kullanımı alınamadı.") }
                }
                val hasPassword = "password" in user.providerIds
                val hasGoogle = "google.com" in user.providerIds
                val reauth: suspend (Boolean) -> Unit = { google ->
                    val result = if (google) controller.reauthenticateWithGoogle(user.id)
                        else controller.reauthenticateWithEmail(user.id, ui.password)
                    ui.password = ""
                    result.getOrThrow()
                }
                AnimatedContent(page, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "accountPage") { p ->
                    when (p) {
                        AccountPage.Main -> MainPage(ui, user.displayName?.takeIf { it.isNotBlank() } ?: t("REAL hesabı"), user.email, user.id,
                            open = { page = it }, openSpace = openSpace,
                            onPurge = { ui.run { repository.purgeRetained(); ui.confirmPurge = false; refreshAccount(ui, repository) } },
                            onRefresh = { ui.run { refreshAccount(ui, repository) } },
                            onSignOut = { ui.run { controller.signOut().getOrThrow(); onDismiss() } },
                            onAdd = { onDismiss(); onAddAccount() }, onClose = onDismiss,
                            onEditName = { onDismiss(); com.cagri.real.ui.auth.ProfileNamePrompt.forced.value = true })
                        AccountPage.Sync -> SubPage(t("Eşitleme"), { page = AccountPage.Main }) { SyncManagementPanel(user.id, enabled = !ui.busy) }
                        AccountPage.Storage -> SubPage(t("Bu cihazdaki alan"), { page = AccountPage.Main }) { LocalStoragePanel() }
                        AccountPage.Data -> SubPage(t("Veri ve silme"), { page = AccountPage.Main }) {
                            DataPage(ui, repository, hasPassword, hasGoogle, reauth, onFinishAccount = {
                                withContext(NonCancellable) {
                                    LocalAccountCleanup.prepare(context, user.id)
                                    controller.deleteAccount(user.id).getOrThrow()
                                    LocalAccountCleanup.finish(context, user.id)
                                }
                                onDismiss()
                            })
                        }
                    }
                }
            }
            if (ui.busy) Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.Center) {
                com.cagri.real.ui.components.RealSpinner(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            }
            ui.error?.let { ConnectionNote(it, Modifier.padding(top = 12.dp)) }
        }
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String? = null) {
    Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
    subtitle?.let { Text(it, color = ConnectionPalette.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp)) }
}

@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) { com.cagri.real.ui.components.RealBackButton(onBack) }
        Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
        Spacer(Modifier.height(18.dp))
        content()
    }
}

@Composable
private fun MainPage(ui: AccountUi, name: String, email: String?, userId: String, open: (AccountPage) -> Unit, openSpace: Boolean,
                     onPurge: () -> Unit, onRefresh: () -> Unit, onSignOut: () -> Unit, onAdd: () -> Unit, onClose: () -> Unit,
                     onEditName: () -> Unit) {
    Column(Modifier.fillMaxWidth().animateContentSize(tween(220)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageTitle(t("Hesapları yönet"))
        Spacer(Modifier.height(6.dp))
        // Hesap
        ConnectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConnectionAvatar(name, userId, 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    email?.let { Text(it, color = ConnectionPalette.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            // Adın: the name others see in shared categories and on shared cards.
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Ortak kategorilerde ve paylaşımlarda bu ad görünür."), Modifier.weight(1f), color = ConnectionPalette.Muted, fontSize = 13.sp)
                Spacer(Modifier.width(10.dp))
                ConnectionPill(t("Adını değiştir"), onEditName, enabled = !ui.busy, small = true)
            }
        }
        // Bulut alanı
        ConnectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Bulut alanı"), Modifier.weight(1f), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                ConnectionPill(t("Yenile"), onRefresh, enabled = !ui.busy, small = true)
            }
            val usage = ui.usage
            if (usage == null) {
                Text(t("Kullanım bilgisi henüz alınamadı."), color = ConnectionPalette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            } else {
                val fraction = if (usage.quotaBytes > 0) (usage.usedBytes.toFloat() / usage.quotaBytes).coerceIn(0f, 1f) else 0f
                Text("${size(usage.usedBytes)} / ${size(usage.quotaBytes)}", color = ConnectionPalette.Accent, fontSize = 24.sp, modifier = Modifier.padding(top = 6.dp))
                Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = .08f))) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(CircleShape)
                        .background(if (fraction > .9f) Color(0xFFFFB86F) else ConnectionPalette.Accent))
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Stat(t("Aktif"), size(usage.activeBytes), Modifier.weight(1f))
                    Stat(t("Temizlenmeyi bekleyen"), size(usage.retainedBytes), Modifier.weight(1f))
                }
                Text(t("{0} dosya", usage.fileCount) + if (ui.cached) t(" · son kaydedilen bilgi") else "", color = ConnectionPalette.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                var showFiles by rememberSaveable { mutableStateOf(openSpace) }
                if (showFiles) CloudSpaceFiles(onRemoved = {})
                else if (fraction > .8f) ConnectionPill(t("Yer aç"), { showFiles = true }, Modifier.fillMaxWidth().padding(top = 10.dp), small = true)
                if (usage.retainedBytes > 0) {
                    Spacer(Modifier.height(10.dp))
                    if (!ui.confirmPurge) ConnectionPill(t("Bekleyenleri şimdi temizle"), { ui.confirmPurge = true }, Modifier.fillMaxWidth(), enabled = !ui.busy && ui.remoteKnown && ui.erasure == null, small = true)
                    else {
                        Text(t("Silinmiş dosyalar buluttan kalıcı olarak kaldırılır. Aktif dosyaların etkilenmez."), color = ConnectionPalette.Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ConnectionPill(t("Temizle"), onPurge, Modifier.weight(1f), enabled = !ui.busy, danger = true, small = true)
                            ConnectionPill(t("Vazgeç"), { ui.confirmPurge = false }, Modifier.weight(1f), enabled = !ui.busy, small = true)
                        }
                    }
                }
            }
        }
        if (!ui.deviceApproved && !ui.pendingRestart)
            ConnectionNote(t("Bulut yedeği başka bir cihazda yeniden başlatılmış. Bu cihazdaki eski veriler otomatik yüklenmez."))
        val erasure = ui.erasure
        AccountRow(RowGlyph.Sync, ConnectionPalette.Accent, t("Eşitleme"), t("Bekleyen kayıtlar ve çakışmalar"), true) { open(AccountPage.Sync) }
        AccountRow(RowGlyph.Device, Color(0xFFB59BFF), t("Bu cihazdaki alan"), t("Önbellek ve yerel dosyalar"), true) { open(AccountPage.Storage) }
        AccountRow(RowGlyph.Data, ConnectionPalette.Danger, t("Veri ve silme"),
            when { erasure != null && !erasure.complete -> t("Silme devam ediyor"); ui.pendingRestart -> t("Yedekleme yeniden başlatılmayı bekliyor")
                else -> t("Bulut verilerini veya hesabı sil") }, true) { open(AccountPage.Data) }
        Spacer(Modifier.height(6.dp))
        ConnectionPill(t("Çıkış yap"), onSignOut, Modifier.fillMaxWidth(), enabled = !ui.busy, filled = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConnectionPill(t("Başka hesap ekle"), onAdd, Modifier.weight(1f), enabled = !ui.busy, small = true)
            ConnectionPill(t("Kapat"), { if (!ui.busy) onClose() }, Modifier.weight(1f), enabled = !ui.busy, small = true)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = ConnectionPalette.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DataPage(ui: AccountUi, repository: AndroidAccountRepository, hasPassword: Boolean, hasGoogle: Boolean,
                     reauth: suspend (Boolean) -> Unit, onFinishAccount: suspend () -> Unit) {
    val current = ui.erasure
    Column(Modifier.fillMaxWidth().animateContentSize(tween(220)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (current != null) {
            ConnectionNote(when {
                !current.complete -> t("Silme devam ediyor. Dosya temizliği bitmeden işlem tamamlanmış sayılmaz; pencereyi kapatıp sonra devam edebilirsin.")
                current.mode == ErasureMode.CLOUD -> t("Bulut verilerin silindi. Yerel kartların duruyor; otomatik bulut eşitlemesi kapalı.")
                else -> t("Bulut temizliği tamamlandı. Firebase hesabını silmek için yeniden doğrulayıp son adımı bitir.")
            })
            if (!current.complete) ConnectionPill(t("Silme durumunu yenile"), {
                ui.run { ui.erasure = repository.advance(current.operationId); if (ui.erasure?.complete == true) refreshAccount(ui, repository) }
            }, Modifier.fillMaxWidth(), enabled = !ui.busy)
            if (current.complete && current.mode == ErasureMode.ACCOUNT) {
                if (hasPassword) PasswordField(ui, t("Şifre"))
                if (hasPassword) ConnectionPill(t("Hesap silmeyi bitir"), { ui.run { reauth(false); onFinishAccount() } },
                    Modifier.fillMaxWidth(), enabled = !ui.busy && ui.password.isNotBlank(), danger = true)
                if (hasGoogle) ConnectionPill(t("Google ile doğrula ve bitir"), { ui.run { reauth(true); onFinishAccount() } },
                    Modifier.fillMaxWidth(), enabled = !ui.busy, danger = true)
            }
        }
        if ((current?.mode == ErasureMode.CLOUD && current.complete) || ui.pendingRestart) {
            if (!ui.confirmRestart) ConnectionCard {
                Text(t("Bulut yedeklemesi"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(t("Bu cihazdaki kartlar, kategoriler ve planlar yeniden buluta yedeklenir."), color = ConnectionPalette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                ConnectionPill(if (ui.pendingRestart) t("Yeniden başlatmayı tamamla") else t("Yedeklemeyi yeniden başlat"), {
                    ui.confirmRestart = true; ui.restartAccepted = false; ui.password = ""
                }, Modifier.fillMaxWidth(), enabled = !ui.busy && ui.remoteKnown)
            } else ConnectionCard {
                Text(t("Yedeklemeyi yeniden başlat"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                ConnectionNote(t("Yalnız bu cihazda hâlâ bulunan kartlar, kategoriler ve planlar yeniden yedeklenir. Yerelde de silinen kartlar geri gelmez. Silinen ortak panolar, arkadaş bağlantıları ve paylaşım linkleri geri oluşturulmaz. Diğer cihazlardaki eski veriler yüklenmez ve bu işlem yeniden bulut alanı kullanır."))
                ConsentRow(ui.restartAccepted, !ui.busy, t("Bu cihazdaki mevcut verileri yeniden yedeklemek istiyorum.")) { ui.restartAccepted = it }
                if (hasPassword) PasswordField(ui, t("Şifreyle doğrula"))
                val operation = current?.operationId
                if (hasPassword) ConnectionPill(t("Doğrula ve başlat"), {
                    ui.run { reauth(false); repository.restartBackup(operation); ui.confirmRestart = false; ui.restartAccepted = false; refreshAccount(ui, repository) }
                }, Modifier.fillMaxWidth(), enabled = !ui.busy && ui.restartAccepted && ui.password.isNotBlank())
                if (hasGoogle) ConnectionPill(t("Google ile doğrula ve başlat"), {
                    ui.run { reauth(true); repository.restartBackup(operation); ui.confirmRestart = false; ui.restartAccepted = false; refreshAccount(ui, repository) }
                }, Modifier.fillMaxWidth(), enabled = !ui.busy && ui.restartAccepted)
                ConnectionPill(t("Vazgeç"), { ui.confirmRestart = false; ui.restartAccepted = false; ui.password = "" }, Modifier.fillMaxWidth(), enabled = !ui.busy, small = true)
            }
        }
        val mode = ui.confirm
        if (mode != null && !ui.confirmRestart) ConnectionCard {
            Text(if (mode == ErasureMode.CLOUD) t("Bulut verilerini sil") else t("Hesabı sil"), color = ConnectionPalette.Danger, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            ConnectionNote(if (mode == ErasureMode.CLOUD)
                t("Buluttaki kartların, kategorilerin, planların, eklerin, paylaşım bağlantıların, bağlantıların ve sahip olduğun tüm panolar silinir. Yerel dosyaların ve Firebase hesabın kalır; otomatik sync kapatılır.")
                else t("Buluttaki verilerin, paylaşım bağlantıların, bağlantıların ve sahip olduğun tüm panolar silinir. Ardından Firebase hesabın ve bu hesabın yerel çalışma alanı silinir."))
            ConnectionNote(t("Ortak panolarının üyeleri erişimi kaybeder. Başkasının panosu ve alıcının içe aktardığı bağımsız kart kopyaları silinmez. Bu işlem geri alınamaz."))
            ConsentRow(ui.accepted, !ui.busy, t("Sahip olduğum ortak panoların da silineceğini anladım.")) { ui.accepted = it }
            if (hasPassword) PasswordField(ui, t("Şifreyle doğrula"))
            val begin: (Boolean) -> Unit = { google ->
                ui.run {
                    reauth(google)
                    ui.erasure = repository.start(mode, ui.operationId)
                    ui.confirm = null
                    ui.erasure = repository.advance(ui.erasure!!.operationId)
                }
            }
            if (hasPassword) ConnectionPill(t("Doğrula ve sil"), { begin(false) }, Modifier.fillMaxWidth(),
                enabled = !ui.busy && ui.accepted && ui.password.isNotBlank(), danger = true)
            if (hasGoogle) ConnectionPill(t("Google ile doğrula ve sil"), { begin(true) }, Modifier.fillMaxWidth(),
                enabled = !ui.busy && ui.accepted, danger = true)
            ConnectionPill(t("Vazgeç"), { ui.confirm = null; ui.accepted = false; ui.password = "" }, Modifier.fillMaxWidth(), enabled = !ui.busy, small = true)
        } else if (!ui.confirmRestart && (current == null || (current.complete && current.mode == ErasureMode.CLOUD))) {
            if (current == null) ConnectionCard {
                Text(t("Bulut verilerini sil"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(t("Buluttaki kartlar, ekler ve paylaşımlar silinir. Yerel verilerin kalır."), color = ConnectionPalette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                ConnectionPill(t("Bulut verilerini sil"), {
                    ui.confirm = ErasureMode.CLOUD; ui.accepted = false; ui.operationId = UUID.randomUUID().toString()
                }, Modifier.fillMaxWidth(), enabled = !ui.busy && ui.remoteKnown, danger = true)
            }
            ConnectionCard {
                Text(t("Hesabı sil"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(t("Bulut verilerin, hesabın ve bu hesabın yerel çalışma alanı silinir."), color = ConnectionPalette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                ConnectionPill(t("Hesabı sil"), {
                    ui.confirm = ErasureMode.ACCOUNT; ui.accepted = false; ui.operationId = UUID.randomUUID().toString()
                }, Modifier.fillMaxWidth(), enabled = !ui.busy && ui.remoteKnown, danger = true)
            }
        }
        if (ui.error != null && current == null) ConnectionPill(t("Silme başlamadıysa eşitlemeyi aç"), {
            ui.run { repository.resumeCloudIfNoErasure(); refreshAccount(ui, repository) }
        }, Modifier.fillMaxWidth(), enabled = !ui.busy, small = true)
    }
}

@Composable
private fun PasswordField(ui: AccountUi, label: String) {
    OutlinedTextField(ui.password, { ui.password = it }, label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(), colors = accountFieldColors(), enabled = !ui.busy,
        singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ConsentRow(checked: Boolean, enabled: Boolean, text: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange, enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = ConnectionPalette.Accent, checkmarkColor = Color.Black, uncheckedColor = ConnectionPalette.Muted))
        Text(text, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun AccountRow(glyph: RowGlyph, tone: Color, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(Modifier.fillMaxWidth().alpha(if (enabled) 1f else .5f).clip(shape).background(ConnectionPalette.Glass)
        .border(1.dp, ConnectionPalette.Stroke, shape).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(tone.copy(alpha = .14f)), contentAlignment = Alignment.Center) { RowIcon(glyph, tone) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = ConnectionPalette.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", color = ConnectionPalette.Muted, fontSize = 20.sp)
    }
}

/** Küçük çizim ikonları (emoji/glif yok; her cihazda aynı görünür). */
@Composable
private fun RowIcon(glyph: RowGlyph, tint: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width; val h = size.height
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        when (glyph) {
            RowGlyph.Sync -> {
                drawArc(tint, 200f, 150f, false, topLeft = Offset(w * .12f, h * .12f), size = Size(w * .76f, h * .76f), style = stroke)
                drawArc(tint, 20f, 150f, false, topLeft = Offset(w * .12f, h * .12f), size = Size(w * .76f, h * .76f), style = stroke)
            }
            RowGlyph.Device -> {
                drawRoundRect(tint, Offset(w * .26f, h * .06f), Size(w * .48f, h * .88f), CornerRadius(w * .10f), style = stroke)
                drawLine(tint, Offset(w * .43f, h * .80f), Offset(w * .57f, h * .80f), stroke.width, StrokeCap.Round)
            }
            RowGlyph.Data -> {
                drawLine(tint, Offset(w * .14f, h * .24f), Offset(w * .86f, h * .24f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * .38f, h * .10f), Offset(w * .62f, h * .10f), stroke.width, StrokeCap.Round)
                drawRoundRect(tint, Offset(w * .24f, h * .34f), Size(w * .52f, h * .58f), CornerRadius(w * .08f), style = stroke)
            }
        }
    }
}

private fun size(bytes: Long): String = if (bytes < 1024 * 1024) "%.1f KB".format(bytes / 1024.0) else "%.1f MB".format(bytes / (1024.0 * 1024.0))

@Composable
private fun accountFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color.White, disabledTextColor = Color.White.copy(alpha = .5f),
    cursorColor = Color.White, focusedLabelColor = Color.White, unfocusedLabelColor = Color.White.copy(alpha = .6f),
    focusedBorderColor = Color.White.copy(alpha = .4f), unfocusedBorderColor = Color.White.copy(alpha = .15f))
