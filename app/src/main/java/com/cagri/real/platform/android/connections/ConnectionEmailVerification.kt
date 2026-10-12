package com.cagri.real.platform.android.connections

import com.cagri.real.platform.android.auth.AndroidBackend
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Limited sign-in handoff helper for email-bound invites; no changes to global auth behavior. */
object ConnectionEmailVerification {
    private fun user(uid: String): FirebaseUser {
        check(AndroidBackend.auth.currentUser()?.id == uid) { "Hesap değişti." }
        return checkNotNull(FirebaseAuth.getInstance().currentUser?.takeIf { it.uid == uid }) { "REAL'e giriş yapın." }
    }
    suspend fun send(uid: String) {
        val current = user(uid)
        if (!current.isEmailVerified) current.sendEmailVerification().awaitCompletion()
        user(uid)
    }
    suspend fun refresh(uid: String) {
        val current = user(uid)
        current.reload().awaitCompletion()
        user(uid).getIdToken(true).awaitCompletion()
        user(uid)
    }
    private suspend fun Task<*>.awaitCompletion(): Unit = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (continuation.isActive) {
                if (task.isSuccessful) continuation.resume(Unit)
                else continuation.resumeWithException(IllegalStateException("Email doğrulaması tamamlanamadı. Daha sonra tekrar deneyin."))
            }
        }
    }
}
