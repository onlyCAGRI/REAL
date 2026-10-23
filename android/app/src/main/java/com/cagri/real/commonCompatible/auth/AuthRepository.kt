package com.cagri.real.auth

import com.cagri.real.i18n.t

data class AuthUser(
    val id: String,
    val email: String?,
    val displayName: String?,
    val isAnonymous: Boolean,
    val providerIds: List<String> = emptyList()
)

fun interface AuthSubscription {
    fun cancel()
}

/** Session boundary. Firebase types and credentials never cross into shared/UI code. */
interface AuthRepository {
    fun currentUser(): AuthUser?
    fun observeUser(onChanged: (AuthUser?) -> Unit): AuthSubscription

    suspend fun signInWithEmail(email: String, password: String): Result<AuthUser>
    suspend fun createAccount(email: String, password: String): Result<AuthUser>
    suspend fun createAccountWithName(email: String, password: String, firstName: String, lastName: String = ""): Result<AuthUser>
    suspend fun updateProfileName(expectedUid: String, firstName: String, lastName: String = ""): Result<AuthUser>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser>
    suspend fun idToken(forceRefresh: Boolean = false): Result<String?>

    /** Must obtain the token from this exact account, never whichever account replaced it. */
    suspend fun idTokenForUser(expectedUid: String): Result<String?>

    suspend fun reauthenticateWithEmail(expectedUid: String, password: String): Result<Unit> =
        Result.failure(AuthFailure(t("Bu hesap için doğrulama desteklenmiyor.")))
    suspend fun deleteAccount(expectedUid: String): Result<Unit> =
        Result.failure(AuthFailure(t("Hesap silme desteklenmiyor.")))
    suspend fun reauthenticateWithGoogleIdToken(expectedUid: String, idToken: String): Result<Unit> =
        Result.failure(AuthFailure(t("Google doğrulaması desteklenmiyor.")))

    fun signOut()
}

class AuthFailure(message: String) : Exception(message)
class AuthCancelled : Exception()

/** Account exists; retry its profile write instead of creating it again. */
class AuthProfileFailure(val createdUid: String, message: String) : Exception(message)
