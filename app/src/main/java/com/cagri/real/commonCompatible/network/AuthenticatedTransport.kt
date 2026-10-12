package com.cagri.real.network

import com.cagri.real.auth.AuthRepository

fun interface AppCheckTokenProvider {
    suspend fun token(): Result<String?>
}

/** Firebase identity and App Check are both required; credentials are never persisted here. */
class BackendCredentials(
    private val auth: AuthRepository,
    private val appCheck: AppCheckTokenProvider,
    private val extraHeaders: suspend (String) -> Map<String, String> = { emptyMap() }
) {
    suspend fun headers(expectedUid: String? = null): Map<String, String> {
        val user = checkNotNull(auth.currentUser()) { "Sign-in required" }
        if (expectedUid != null) check(user.id == expectedUid) { "Account changed" }
        val bearer = auth.idTokenForUser(user.id).getOrThrow()
        check(!bearer.isNullOrBlank()) { "Identity token unavailable" }
        check(auth.currentUser()?.id == user.id) { "Account changed" }
        val attestation = appCheck.token().getOrThrow()
        check(!attestation.isNullOrBlank()) { "App Check unavailable" }
        check(auth.currentUser()?.id == user.id) { "Account changed" }
        val extra = extraHeaders(user.id)
        check(auth.currentUser()?.id == user.id) { "Account changed" }
        return extra + mapOf(
            "Authorization" to "Bearer $bearer",
            "X-Firebase-AppCheck" to attestation
        )
    }
}

/** Caller supplies the trusted origin; redirects must also be disabled by platform I/O. */
class AuthenticatedTransport(
    private val delegate: HttpTransport,
    private val credentials: BackendCredentials,
    private val trustedOrigin: String,
    private val expectedUid: String? = null
) : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse {
        require(trustedOrigin.startsWith("https://") && request.url.startsWith("$trustedOrigin/"))
        val clean = request.headers.filterKeys {
            !it.equals("Authorization", true) && !it.equals("X-API-Key", true) && !it.equals("X-Firebase-AppCheck", true) && !it.equals("X-REAL-Cloud-Epoch", true)
        }
        return delegate.execute(request.copy(headers = clean + credentials.headers(expectedUid)))
    }
}

/** Prevents a queued operation/response being used under another signed-in account. */
class AccountHttpTransport(
    private val expectedUid: String,
    private val auth: AuthRepository,
    private val delegate: HttpTransport
) : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse {
        check(auth.currentUser()?.id == expectedUid) { "Account changed" }
        val result = delegate.execute(request)
        check(auth.currentUser()?.id == expectedUid) { "Account changed" }
        return result
    }
}
