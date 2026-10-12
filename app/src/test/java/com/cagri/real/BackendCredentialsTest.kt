package com.cagri.real

import com.cagri.real.auth.*
import com.cagri.real.network.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BackendCredentialsTest {
    private class Session : AuthRepository {
        var user: AuthUser? = AuthUser("A", null, null, false)
        var issued: Result<String?> = Result.success("fixture-token")
        override fun currentUser() = user
        override fun observeUser(onChanged: (AuthUser?) -> Unit) = AuthSubscription {}
        override suspend fun idToken(forceRefresh: Boolean) = issued
        override suspend fun idTokenForUser(expectedUid: String): Result<String?> =
            if (user?.id == expectedUid) issued else Result.failure(AuthFailure("Account changed"))
        override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> = error("unused")
        override suspend fun createAccountWithName(email: String, password: String, firstName: String, lastName: String): Result<AuthUser> = error("unused")
        override suspend fun updateProfileName(expectedUid: String, firstName: String, lastName: String): Result<AuthUser> = error("unused")
        override suspend fun createAccount(email: String, password: String): Result<AuthUser> = error("unused")
        override suspend fun sendPasswordReset(email: String): Result<Unit> = error("unused")
        override suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser> = error("unused")
        override fun signOut() { user = null }
    }
    @Test fun signedInRequiresBothTokens() = runBlocking {
        val credentials = BackendCredentials(Session(), AppCheckTokenProvider { Result.success("fixture-app") })
        assertEquals(mapOf("Authorization" to "Bearer fixture-token", "X-Firebase-AppCheck" to "fixture-app"), credentials.headers())
    }
    @Test fun missingIdentityNeverSendsRequest() = runBlocking {
        for (issued in listOf(Result.success<String?>(null), Result.success(""), Result.failure(IllegalStateException("fixture")))) {
            val session = Session().apply { this.issued = issued }
            var sent = false
            val credentials = BackendCredentials(session, AppCheckTokenProvider { Result.success("fixture-app") })
            val transport = AuthenticatedTransport(HttpTransport { sent = true; HttpResponse(200, "") }, credentials, "https://real.example")
            assertTrue(runCatching { transport.execute(request()) }.isFailure)
            assertFalse(sent)
        }
    }
    @Test fun signedOutNeverRequestsAttestationOrNetwork() = runBlocking {
        var attested = false
        var sent = false
        val session = Session().apply { signOut() }
        val credentials = BackendCredentials(session, AppCheckTokenProvider { attested = true; Result.success("fixture-app") })
        val transport = AuthenticatedTransport(HttpTransport { sent = true; HttpResponse(200, "") }, credentials, "https://real.example")
        assertTrue(runCatching { transport.execute(request()) }.isFailure)
        assertFalse(attested)
        assertFalse(sent)
    }
    @Test fun missingOrFailedAppCheckNeverSendsRequest() = runBlocking {
        for (attestation in listOf(Result.success<String?>(null), Result.success(""), Result.failure(IllegalStateException("fixture")))) {
            var sent = false
            val credentials = BackendCredentials(Session(), AppCheckTokenProvider { attestation })
            val transport = AuthenticatedTransport(HttpTransport { sent = true; HttpResponse(200, "") }, credentials, "https://real.example")
            assertTrue(runCatching { transport.execute(request()) }.isFailure)
            assertFalse(sent)
        }
    }
    @Test fun accountSwitchDuringAttestationRejectsRequest() = runBlocking {
        val session = Session()
        val credentials = BackendCredentials(session, AppCheckTokenProvider {
            session.signOut(); Result.success("fixture-app")
        })
        assertTrue(runCatching { credentials.headers() }.isFailure)
    }
    @Test fun credentialsCannotBeSentToDifferentOrigin() = runBlocking {
        var sent = false
        val credentials = BackendCredentials(Session(), AppCheckTokenProvider { Result.success("fixture-app") })
        val transport = AuthenticatedTransport(HttpTransport { sent = true; HttpResponse(200, "") }, credentials, "https://real.example")
        assertTrue(runCatching { transport.execute(request().copy(url = "https://real.example.evil/api")) }.isFailure)
        assertFalse(sent)
    }
    @Test fun accountSwitchDuringResponseDiscardsResponse() = runBlocking {
        val session = Session()
        val transport = AccountHttpTransport("A", session, HttpTransport {
            session.signOut(); HttpResponse(200, "private data")
        })
        assertTrue(runCatching { transport.execute(request()) }.isFailure)
    }
    @Test fun queuedAccountCannotAcquireAnotherAccountsToken() = runBlocking {
        val session = Session().apply { user = AuthUser("B", null, null, false) }
        val credentials = BackendCredentials(session, AppCheckTokenProvider { Result.success("fixture-app") })
        assertTrue(runCatching { credentials.headers(expectedUid = "A") }.isFailure)
    }
    @Test fun transportReplacesCallerCredentialsAndDoesNotRetryRejection() = runBlocking {
        var calls = 0
        var sent: HttpRequest? = null
        val credentials = BackendCredentials(Session(), AppCheckTokenProvider { Result.success("fixture-app") })
        val transport = AuthenticatedTransport(HttpTransport {
            calls++; sent = it; HttpResponse(401, "denied")
        }, credentials, "https://real.example")
        val response = transport.execute(request().copy(headers = mapOf(
            "authorization" to "untrusted", "X-API-Key" to "untrusted", "x-firebase-appcheck" to "untrusted"
        )))
        assertEquals(401, response.statusCode)
        assertEquals(1, calls)
        assertEquals(mapOf("Authorization" to "Bearer fixture-token", "X-Firebase-AppCheck" to "fixture-app"), sent!!.headers)
    }
    private fun request() = HttpRequest("https://real.example/api", "POST", emptyMap(), "{}", 1000, 1000)
}
