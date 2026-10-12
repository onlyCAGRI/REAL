package com.cagri.real.ui.connections

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.MainActivity
import com.cagri.real.connections.connectionTokenFromLink
import com.cagri.real.platform.android.connections.ConnectionsApi
import com.cagri.real.platform.android.connections.RealConnections
import com.cagri.real.ui.theme.RealTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Token survives the sign-in round trip in this activity's intent; acceptance is explicit. */
class ConnectionInviteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = connectionTokenFromLink(intent.dataString) ?: run { finish(); return }
        setContent { RealTheme { ConnectionInviteScreen(token, onSignIn = {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }, onClose = ::finish) } }
    }
}

private val Screen = Brush.radialGradient(listOf(Color(0xFF16222B), Color(0xFF050507)), center = Offset(540f, 0f), radius = 1500f)

@Composable
private fun InviteFrame(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Screen).systemBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp)) {
        Column(Modifier.align(Alignment.Center).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
private fun InviteHero(done: Boolean) {
    val accent = ConnectionPalette.Accent
    Box(Modifier.size(112.dp).background(Brush.radialGradient(listOf(accent.copy(alpha = .22f), Color.Transparent)), androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center) {
        if (done) Box(Modifier.size(72.dp).background(Color.White.copy(alpha = .08f), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(30.dp)) {
                val w = 3.dp.toPx()
                drawLine(accent, Offset(size.width * .12f, size.height * .54f), Offset(size.width * .40f, size.height * .80f), w, StrokeCap.Round)
                drawLine(accent, Offset(size.width * .40f, size.height * .80f), Offset(size.width * .90f, size.height * .22f), w, StrokeCap.Round)
            }
        } else androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.cagri.real.R.drawable.real_invite_logo),
            contentDescription = "REAL",
            modifier = Modifier.size(84.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp)),
        )
    }
}

@Composable
private fun InviteHeading(title: String, subtitle: String? = null) {
    Spacer(Modifier.height(22.dp))
    Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 31.sp)
    if (subtitle != null) Text(subtitle, color = ConnectionPalette.Muted, fontSize = 15.sp, textAlign = TextAlign.Center,
        lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
    Spacer(Modifier.height(26.dp))
}

@Composable
private fun ConnectionInviteScreen(token: String, onSignIn: () -> Unit, onClose: () -> Unit) {
    val user = currentConnectionsUser()
    if (user != null && user.displayName.isNullOrBlank()) {
        InviteFrame {
            Text("Önce adını ekle", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Arkadaşların seni tanıyabilsin diye adın davette görünür.", color = ConnectionPalette.Muted, fontSize = 14.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
            com.cagri.real.ui.auth.ProfileNameForm(user, onSaved = {})
            ConnectionPill("Vazgeç", onClose, Modifier.padding(top = 12.dp))
        }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    key(user?.id, token) {
        var name by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        var done by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val api = remember(user?.id) { user?.let { ConnectionsApi(it.id) } }
        suspend fun preview() {
            busy = true; error = null
            try { name = api?.preview(token) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Davet açılamadı." }
            finally { busy = false }
        }
        LaunchedEffect(api) { if (api != null) preview() }
        InviteFrame {
            when {
                user == null -> {
                    InviteHero(false)
                    InviteHeading("REAL daveti", "Daveti görmek için REAL hesabınla giriş yap, ardından bu bağlantıya geri dön.")
                    ConnectionPill("REAL'e giriş yap", onSignIn, Modifier.fillMaxWidth(), filled = true)
                    ConnectionPill("Vazgeç", onClose, Modifier.padding(top = 10.dp))
                }
                busy && name == null && !done -> {
                    InviteHero(false)
                    InviteHeading("Davet kontrol ediliyor…")
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                }
                done -> {
                    InviteHero(true)
                    InviteHeading("Bağlandınız!", "${name ?: "Arkadaşın"} artık kişi listende. Kart paylaşırken ve Pano davetlerinde aynı kişiyi kullanabilirsin.")
                    ConnectionPill("Bitti", onClose, Modifier.fillMaxWidth(), filled = true)
                }
                name != null -> {
                    InviteHero(false)
                    InviteHeading("${name} seni REAL'e davet ediyor", "Kabul edersen birbirinizin listesinde görünür, kart ve Pano paylaşabilirsiniz.")
                    ConnectionCard {
                        Text("Hesap", color = ConnectionPalette.Muted, fontSize = 12.sp)
                        Text(user.email ?: user.displayName ?: "REAL", color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Spacer(Modifier.height(18.dp))
                    ConnectionPill(if (busy) "Kabul ediliyor…" else "Kabul et", {
                        busy = true; error = null
                        scope.launch {
                            try {
                                checkNotNull(api).accept(token)
                                done = true // Acceptance is committed even if subsequent cache refresh is offline.
                                try { RealConnections.repository(context, user.id).refresh() }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { error = "Bağlantı kaydedildi; kişi listesi çevrimiçi olduğunda güncellenecek." }
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { error = failure.message ?: "Bağlantı kurulamadı." }
                            finally { busy = false }
                        }
                    }, Modifier.fillMaxWidth(), enabled = !busy, filled = true)
                    ConnectionPill("Şimdi değil", onClose, Modifier.padding(top = 10.dp), enabled = !busy)
                }
                else -> {
                    InviteHero(false)
                    InviteHeading("Davet açılamadı", "Davetin süresi dolmuş, iptal edilmiş ya da bu hesapla kabul edilemiyor olabilir.")
                    ConnectionPill("Tekrar dene", { scope.launch { preview() } }, Modifier.fillMaxWidth(), filled = true)
                    ConnectionPill("Kapat", onClose, Modifier.padding(top = 10.dp))
                }
            }
            error?.let { ConnectionNote(it, Modifier.padding(top = 16.dp)) }
            if (error != null && !done && user != null && !user.email.isNullOrBlank()) {
                ConnectionCard(Modifier.padding(top = 12.dp)) {
                    Text("E-postaya özel davet mi?", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("Önce hesabının e-posta adresini doğrula, sonra tekrar dene.", color = ConnectionPalette.Muted, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                    ConnectionPill("Doğrulama e-postası gönder", {
                        busy = true
                        scope.launch {
                            try {
                                com.cagri.real.platform.android.connections.ConnectionEmailVerification.send(user.id)
                                error = "Doğrulama e-postanı kontrol et; ardından \"Doğruladım\"a bas."
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { error = failure.message }
                            finally { busy = false }
                        }
                    }, Modifier.fillMaxWidth(), enabled = !busy, small = true)
                    ConnectionPill("Doğruladım · tekrar dene", {
                        busy = true
                        scope.launch {
                            try {
                                com.cagri.real.platform.android.connections.ConnectionEmailVerification.refresh(user.id)
                                preview()
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { error = failure.message }
                            finally { busy = false }
                        }
                    }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !busy, small = true)
                }
            }
        }
    }
}
