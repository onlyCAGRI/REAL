package com.cagri.real.updates

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cagri.real.ui.components.PermissionSheet
import com.cagri.real.ui.components.PermissionGlyph
import com.cagri.real.ui.theme.RealType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.cagri.real.ui.components.LocalLiquidReduceMotion
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.firebase.auth.FirebaseAuth
import com.cagri.real.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import java.io.File

/** One check on foreground entry, throttled after success; no polling or startup blocking. */
@Composable
fun TesterUpdateButton() {
    if (!BuildConfig.REAL_TESTER_UPDATES_ENABLED) return
    val context = LocalContext.current
    val client = remember(context) { TesterUpdateClient(context.applicationContext) }
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }
    var uid by remember { mutableStateOf(auth.currentUser?.uid) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { uid = it.currentUser?.uid }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    var release by remember { mutableStateOf<TesterRelease?>(null) }
    var checkedAt by remember { mutableLongStateOf(0L) }
    var open by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(owner, uid) {
        release = null; ready = null; open = false; checkedAt = 0L; job?.cancel()
        if (uid == null) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (System.currentTimeMillis() - checkedAt >= 15*60*1000L) {
                try {
                    val r = client.cachedLatest()
                    release = r?.takeIf { it.versionCode > BuildConfig.VERSION_CODE && it.minSdk <= Build.VERSION.SDK_INT }
                    checkedAt = System.currentTimeMillis()
                } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Offline leaves current badge intact. */ }
            }
            awaitCancellation()
        }
    }
    val reduceMotion = LocalLiquidReduceMotion.current
    AnimatedVisibility(release != null,
        enter = if (reduceMotion) EnterTransition.None else fadeIn(),
        exit = if (reduceMotion) ExitTransition.None else fadeOut()) {
        Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 6.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { open = true })
            .semantics { contentDescription = "Yeni REAL güncellemesi" }, contentAlignment = Alignment.Center) {
            Text("update", style = RealType.actionLabel.copy(fontSize = 14.sp), color = Color.White)
        }
    }
    if (open) release?.let { r ->
        fun dismiss() { job?.cancel(); open = false }
        Dialog(onDismissRequest = { dismiss() }, properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false
        )) {
            PermissionSheet(
                visible = true,
                title = "REAL güncellendi",
                description = "${r.versionName} · ${r.sizeBytes / (1024*1024)} MB",
                glyph = PermissionGlyph.Brand,
                primaryEnabled = !busy,
                primaryLabel = when {
                    busy -> "İndiriliyor · ${(progress * 100).toInt()}%"
                    ready?.exists() == true -> "Kur"
                    error != null -> "Tekrar indir"
                    else -> "Güncellemeyi indir"
                },
                secondaryLabel = if (busy) "İptal et" else "Daha sonra",
                onLater = { dismiss() },
                onAllow = {
                    if (!busy) {
                        error = null
                        val apk = ready
                        if (apk != null && apk.exists()) {
                            try { if (!client.install(apk)) error = "İzni açtıktan sonra buraya dönüp Kur’a bas." }
                            catch (_: Exception) { error = "Android kurulum ekranı açılamadı." }
                        } else {
                            busy = true; progress = 0f
                            job = scope.launch {
                                try { ready = client.download(r) { progress = it } }
                                catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = e.message?.take(180) ?: "İndirme tamamlanamadı." }
                                finally { busy = false }
                            }
                        }
                    }
                },
                extraContent = {
                    if (r.notes.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Column(Modifier.fillMaxWidth().heightIn(max = 150.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            r.notes.forEach { Text("• $it", style = RealType.cardBodyProminent, color = Color.White.copy(alpha = .78f)) }
                        }
                    }
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
}
