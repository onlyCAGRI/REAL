package com.cagri.real.ui.sharedcategories

import com.cagri.real.i18n.t
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.MainActivity
import com.cagri.real.sharedcategories.*
import com.cagri.real.ui.connections.*
import com.cagri.real.platform.android.connections.RealConnections
import com.cagri.real.ui.theme.RealTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class CategoryInviteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = categoryInviteToken(intent.dataString) ?: run { finish(); return }
        setContent { RealTheme { CategoryInviteScreen(token, onClose = ::finish, onSignIn = {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }) } }
    }
}

@Composable
private fun CategoryInviteScreen(token: String, onClose: () -> Unit, onSignIn: () -> Unit) {
    val context = LocalContext.current
    val user = currentConnectionsUser()
    val scope = rememberCoroutineScope()
    key(user?.id, token) {
        var title by remember { mutableStateOf<String?>(null) }
        var owner by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var accepted by remember { mutableStateOf(false) }
        var openCategory by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val api = remember(user?.id) { user?.let { SharedCategoryApi(context.applicationContext, it.id) } }
        suspend fun preview() {
            busy = true; error = null
            try { api?.preview(token)?.let { title = it.getString("name"); owner = it.getString("owner") } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message }
            finally { busy = false }
        }
        LaunchedEffect(api) { if (api != null) preview() }
        Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.Center) {
            Text(t("REAL kategori daveti"), color = Color.White, fontSize = 28.sp)
            Spacer(Modifier.height(16.dp))
            when {
                user == null -> {
                    Text(t("Daveti görmek için REAL hesabınla giriş yap, ardından bağlantıya geri dön."), color = Color.Gray)
                    ConnectionPill(t("Giriş yap"), onSignIn, Modifier.padding(top = 18.dp), filled = true)
                }
                user.displayName.isNullOrBlank() -> com.cagri.real.ui.auth.ProfileNameForm(user, onSaved = {})
                accepted -> {
                    Text(t("Kategoriye katıldın. Hedefler Kartlarım'a kopyalanmaz; takvimde Paylaşılan kategoriler altında görünür."), color = Color.Gray)
                    ConnectionPill(t("Kategoriyi aç"), { openCategory = true }, Modifier.padding(top = 18.dp), filled = true)
                }
                title != null -> {
                    Text("$owner · $title", color = Color.White, fontSize = 20.sp)
                    Text(t("Kabul edersen kategori hedeflerini, eklerini ve ortak linki görebilirsin. Ayrıca birbirinizin REAL Connections listesine ekleneceksiniz. Düzenleme kategori sahibine aittir."), color = Color.Gray, modifier = Modifier.padding(top = 10.dp))
                    ConnectionPill(t("Kabul et"), {
                        busy = true; error = null
                        scope.launch {
                            try { checkNotNull(api).accept(token); accepted = true
                                try { RealConnections.repository(context, user.id).refresh() }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { /* Acceptance is durable; cache can refresh later. */ }
                            }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { error = failure.message }
                            finally { busy = false }
                        }
                    }, Modifier.padding(top = 18.dp), enabled = !busy, filled = true)
                }
            }
            if (busy) com.cagri.real.ui.components.RealSpinner(Modifier.padding(top = 18.dp).size(24.dp), color = Color.White, strokeWidth = 2.dp)
            error?.let { ConnectionNote(it); ConnectionPill(t("Yeniden dene"), { scope.launch { preview() } }, enabled = !busy) }
            ConnectionPill(t("Kapat"), onClose, Modifier.padding(top = 18.dp))
        }
        if (openCategory) SharedCategoryBrowser(reduceAnimations = false) { openCategory = false }
    }
}
