package com.cagri.real.platform.android.auth

import com.cagri.real.BuildConfig
import com.cagri.real.auth.AuthRepository
import com.cagri.real.cloud.CloudClient
import com.cagri.real.network.*
import com.cagri.real.platform.android.cloud.AndroidCloudCodec
import com.cagri.real.platform.android.network.AndroidHttpTransport
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Android composition root. Firebase SDK never enters Compose or common-compatible code. */
object AndroidBackend {
    const val ORIGIN = "https://real-backend-staging.cagrigul1907.workers.dev"
    val auth: AuthRepository by lazy { FirebaseAuthRepository(FirebaseAuth.getInstance()) }
    private val attestation = AppCheckTokenProvider {
        if (!BuildConfig.REAL_APP_CHECK_ENABLED) Result.success(null)
        else suspendCancellableCoroutine { continuation ->
            FirebaseAppCheck.getInstance().getAppCheckToken(false).addOnCompleteListener { task ->
                if (continuation.isActive) continuation.resume(
                    if (task.isSuccessful) Result.success(task.result.token)
                    else Result.failure(IllegalStateException("App Check unavailable"))
                )
            }
        }
    }
    private val credentials by lazy {
        BackendCredentials(auth, attestation, com.cagri.real.platform.android.account.CloudBackupEpochStore::headers)
    }
    val workerTransport: HttpTransport by lazy {
        com.cagri.real.platform.android.ai.AiConsentTransport(
            AuthenticatedTransport(AndroidHttpTransport, credentials, ORIGIN),
            { uid -> AccountHttpTransport(uid, auth, AuthenticatedTransport(AndroidHttpTransport, credentials, ORIGIN, expectedUid = uid)) })
    }
    suspend fun diagnosticHeaders(expectedUid: String? = null): Map<String, String> = credentials.headers(expectedUid)
    fun attachmentTransfer(): com.cagri.real.cloud.AttachmentTransfer {
        val uid = checkNotNull(auth.currentUser()?.id) { "Sign-in required" }
        return com.cagri.real.platform.android.cloud.AndroidAttachmentTransfer(ORIGIN, uid, auth) {
            credentials.headers(expectedUid = uid)
        }
    }
    fun syncTransport(): HttpTransport {
        val uid = checkNotNull(auth.currentUser()?.id) { "Sign-in required" }
        return AccountHttpTransport(uid, auth,
            AuthenticatedTransport(AndroidHttpTransport, credentials, ORIGIN, expectedUid = uid))
    }
    fun cloudClient(): CloudClient {
        val uid = checkNotNull(auth.currentUser()?.id) { "Sign-in required" }
        return CloudClient(ORIGIN, AccountHttpTransport(uid, auth,
            AuthenticatedTransport(AndroidHttpTransport, credentials, ORIGIN, expectedUid = uid)), AndroidCloudCodec)
    }
}
