package com.cagri.real.auth

import com.cagri.real.i18n.t
import kotlinx.coroutines.flow.StateFlow

/**
 * UI-facing authentication boundary. Platform implementations may use Credential Manager,
 * Firebase Apple/Google SDKs, or another identity provider, but UI code sees only REAL types.
 */
interface AuthUiController {
    val user: StateFlow<AuthUser?>

    suspend fun signInWithGoogle(): Result<AuthUser>
    suspend fun signInWithEmail(email: String, password: String): Result<AuthUser>
    suspend fun createAccount(email: String, password: String): Result<AuthUser>
    suspend fun createAccountWithName(email: String, password: String, firstName: String, lastName: String = ""): Result<AuthUser>
    suspend fun updateProfileName(expectedUid: String, firstName: String, lastName: String = ""): Result<AuthUser>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun reauthenticateWithEmail(expectedUid: String, password: String): Result<Unit> =
        Result.failure(AuthFailure(t("Bu hesap için doğrulama desteklenmiyor.")))
    suspend fun deleteAccount(expectedUid: String): Result<Unit> =
        Result.failure(AuthFailure(t("Hesap silme desteklenmiyor.")))
    suspend fun reauthenticateWithGoogle(expectedUid: String): Result<Unit> =
        Result.failure(AuthFailure(t("Google doğrulaması desteklenmiyor.")))

    suspend fun signOut(): Result<Unit>

    /** Releases platform listeners owned by this controller. */
    fun dispose()
}
