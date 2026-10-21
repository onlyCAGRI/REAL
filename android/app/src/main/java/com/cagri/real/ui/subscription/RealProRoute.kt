package com.cagri.real.ui.subscription

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cagri.real.R
import com.cagri.real.platform.android.access.RealAccessRepository
import com.cagri.real.ui.theme.RealType
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

/** Shared route; grants come from the backend. Play Billing checkout remains a separate adapter. */
@Composable
internal fun RealProRoute(reduceAnimations: Boolean = false, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var uid by remember { mutableStateOf(auth.currentUser?.uid) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            if (uid != it.currentUser?.uid) RealAccessRepository.clear()
            uid = it.currentUser?.uid
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    // Key ALL account state, so changing accounts cannot show the previous person's entitlement.
    key(uid) {
        var cached by remember { mutableStateOf(uid?.let { RealAccessRepository.cached(it) }) }
        var failed by remember { mutableStateOf(false) }
        var loading by remember { mutableStateOf(false) }
        var expired by remember { mutableStateOf(false) }
        var storeOpenFailed by remember { mutableStateOf(false) }
        val owner = LocalLifecycleOwner.current
        LaunchedEffect(uid, owner) {
            val account = uid ?: return@LaunchedEffect
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                loading = true
                try { cached = RealAccessRepository.refresh(account); failed = false; expired = false }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { failed = true }
                finally { loading = false }
                awaitCancellation() // no periodic polling; refresh on re-entry
            }
        }
        LaunchedEffect(cached) {
            val value = cached ?: return@LaunchedEffect
            delay((value.validUntilElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            expired = true // never silently extend an offline entitlement beyond its TTL
        }
        val access = cached?.takeIf { !expired && it.valid() }?.snapshot
        RealProDialog(
            state = ProStoreUiState(verifiedProActive = access?.isPro == true,
                notice = if (storeOpenFailed) stringResource(R.string.real_pro_open_failed) else null),
            reduceAnimations = reduceAnimations,
            onDismiss = onDismiss,
            onPurchase = null,
            onRestore = null,
            onManage = {
                storeOpenFailed = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/account/subscriptions"))) }.isFailure
            },
            accountContent = {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val status = when {
                        uid == null -> stringResource(R.string.real_access_sign_in)
                        access != null -> stringResource(when (access.source) {
                            "ADMIN_GIFT" -> R.string.real_access_gift
                            "TESTER" -> R.string.real_access_tester
                            else -> R.string.real_access_free
                        })
                        loading -> stringResource(R.string.real_access_loading)
                        else -> stringResource(R.string.real_access_unavailable)
                    }
                    Text(status, style = RealType.cardTitle, color = Color.White)
                    access?.let {
                        it.expiresAt?.let { expiry ->
                            val formatted = remember(expiry) { java.text.DateFormat.getDateTimeInstance(
                                java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(expiry)) }
                            Text(stringResource(R.string.real_access_expires, formatted),
                                style = RealType.cardMetaPrimary, color = Color.White.copy(alpha = .65f))
                        }
                        if (it.isPro) Text(stringResource(R.string.real_access_usage, it.aiUsed, it.aiLimit),
                            style = RealType.cardMetaPrimary, color = Color.White.copy(alpha = .65f))
                    }
                    if (failed && access != null) Text(stringResource(R.string.real_access_cached),
                        style = RealType.cardMetaPrimary, color = Color.White.copy(alpha = .65f))

                }
            }
        )
    }
}
