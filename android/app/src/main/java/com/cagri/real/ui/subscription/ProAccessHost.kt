package com.cagri.real.ui.subscription

import androidx.compose.runtime.*
import com.cagri.real.access.RealFeature
import com.cagri.real.platform.android.access.ProAccess
import com.cagri.real.platform.android.auth.AndroidBackend
import kotlinx.coroutines.launch

@Composable
fun ProAccessHost(uid: String?) {
    val prompt by ProAccess.prompt.collectAsState()
    DisposableEffect(uid) {
        ProAccess.dismiss()
        com.cagri.real.platform.android.access.RealAccessRepository.clear()
        onDispose { ProAccess.dismiss() }
    }
    val current = prompt
    if (current != null && current.offline) {
        com.cagri.real.ui.components.NoInternetSheet(visible = true, onDismiss = ProAccess::dismiss)
    } else if (current != null && current.uid == uid) {
        RealProRoute(onDismiss = ProAccess::dismiss)
    }
}

/** Action is never queued across accounts, navigation or an upgrade dialog. Tap again after upgrade. */
@Composable
fun rememberProAction(feature: RealFeature): ((() -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    return { action ->
        if (!busy) {
            busy = true
            val uid = AndroidBackend.auth.currentUser()?.id
            scope.launch {
                try {
                    if (ProAccess.allowed(feature) && AndroidBackend.auth.currentUser()?.id == uid) action()
                } finally { busy = false }
            }
        }
    }
}
