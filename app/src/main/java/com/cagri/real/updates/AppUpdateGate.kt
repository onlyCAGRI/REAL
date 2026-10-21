package com.cagri.real.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cagri.real.BuildConfig
import com.cagri.real.network.ClientUpdateSignal
import com.cagri.real.network.HttpRequest
import com.cagri.real.platform.android.auth.AndroidBackend
import com.cagri.real.ui.components.PermissionGlyph
import com.cagri.real.ui.components.PermissionSheet
import com.cagri.real.ui.components.RealToast
import com.cagri.real.ui.theme.RealType
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Minimum app version (set in the admin panel). Below it the app asks for an update; "Şimdilik sadece görüntüle"
 * keeps REAL open read-only: everything can be looked at, nothing can be added or changed (the server refuses
 * writes from this version too, with 426 UPDATE_REQUIRED).
 */
object AppUpdateGate {
    const val BLOCKED_MESSAGE = "Bu sürümde ekleme yapılamaz. Önce REAL'i güncelle."
    val required = MutableStateFlow(false)
    val sheetOpen = MutableStateFlow(false)
    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
        ClientUpdateSignal.onUpdateRequired = { markRequired() }
    }

    fun markRequired() {
        if (!required.value) sheetOpen.value = true
        required.value = true
    }

    /** True (with a short note and the update sheet) when this version may only look. */
    fun blocked(): Boolean {
        if (!required.value) return false
        app?.let { ctx -> android.os.Handler(android.os.Looper.getMainLooper()).post { RealToast.show(ctx, BLOCKED_MESSAGE) } }
        sheetOpen.value = true
        return true
    }

    /** For repositories: stops the change with a message the screen already shows. */
    fun requireWritable() { if (blocked()) throw IllegalArgumentException(BLOCKED_MESSAGE) }

    /** Asks the server at start (signed in): is this version still allowed to write? */
    suspend fun check() {
        val r = AndroidBackend.workerTransport.execute(HttpRequest("${AndroidBackend.ORIGIN}/api/app/v1/status", "GET", emptyMap(), "", 10_000, 15_000))
        if (r.statusCode !in 200..299) return
        val needed = JSONObject(r.body).optBoolean("updateRequired", false)
        if (needed) markRequired() else { required.value = false; sheetOpen.value = false }
    }
}

@Composable
fun RequiredUpdateHost(uid: String?) {
    val context = LocalContext.current
    val required by AppUpdateGate.required.collectAsState()
    val open by AppUpdateGate.sheetOpen.collectAsState()
    LaunchedEffect(uid) {
        if (uid == null) return@LaunchedEffect
        try { AppUpdateGate.check() } catch (e: CancellationException) { throw e } catch (_: Exception) { /* offline: try next start */ }
    }
    if (!required || !open) return
    val scope = rememberCoroutineScope()
    val client = remember(context) { TesterUpdateClient(context.applicationContext) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var ready by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = { AppUpdateGate.sheetOpen.value = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        PermissionSheet(
            visible = true,
            title = "Güncelleme gerekli",
            description = "Bu REAL sürümü artık desteklenmiyor. Güncelleyince her şey kaldığı yerden devam eder; kartların telefonunda güvende.",
            glyph = PermissionGlyph.Brand,
            primaryEnabled = !busy,
            primaryLabel = when {
                busy -> "İndiriliyor · ${(progress * 100).toInt()}%"
                ready?.exists() == true -> "Kur"
                else -> "Güncelle"
            },
            secondaryLabel = "Şimdilik sadece görüntüle",
            onLater = { if (!busy) AppUpdateGate.sheetOpen.value = false },
            onAllow = {
                if (busy) return@PermissionSheet
                error = null
                val apk = ready
                when {
                    apk != null && apk.exists() -> try {
                        if (!client.install(apk)) error = "İzni açtıktan sonra buraya dönüp Kur’a bas."
                    } catch (_: Exception) { error = "Android kurulum ekranı açılamadı." }
                    BuildConfig.REAL_TESTER_UPDATES_ENABLED -> {
                        busy = true; progress = 0f
                        scope.launch {
                            try {
                                val r = client.cachedLatest()?.takeIf { it.versionCode > BuildConfig.VERSION_CODE && it.minSdk <= Build.VERSION.SDK_INT }
                                if (r == null) error = "Yeni sürüm henüz hazır değil. Biraz sonra tekrar dene."
                                else ready = client.download(r) { progress = it }
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = e.message?.take(180) ?: "İndirme tamamlanamadı." }
                            finally { busy = false }
                        }
                    }
                    else -> runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure { error = "Mağaza açılamadı." }
                }
            },
            extraContent = {
                if (busy) {
                    Spacer(Modifier.height(18.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(),
                        color = Color.White, trackColor = Color.White.copy(alpha = .12f))
                }
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = RealType.cardMetaPrimary, color = Color(0xFFFFC8B8))
                }
            }
        )
    }
}
