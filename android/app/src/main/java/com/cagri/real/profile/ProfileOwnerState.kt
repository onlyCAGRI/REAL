package com.cagri.real.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cagri.real.platform.android.auth.AndroidBackend

/** Rebind displayed profile and its pref listener immediately when Auth changes. */
@Composable
fun rememberProfileOwner(): String? {
    var owner by remember { mutableStateOf(ProfileAccountStore.currentOwner()) }
    DisposableEffect(Unit) {
        val subscription = AndroidBackend.auth.observeUser { owner = it?.id }
        onDispose { subscription.cancel() }
    }
    return owner
}
