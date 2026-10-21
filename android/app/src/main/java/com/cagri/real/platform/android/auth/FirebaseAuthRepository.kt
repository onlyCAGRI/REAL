package com.cagri.real.platform.android.auth

import com.cagri.real.auth.AuthProfileFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import com.cagri.real.auth.AuthFailure
import com.cagri.real.auth.AuthRepository
import com.cagri.real.auth.AuthSubscription
import com.cagri.real.auth.AuthUser
import com.cagri.real.auth.profileDisplayName
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Android Firebase implementation. UI code only talks to AuthRepository/AuthUiController. */
class FirebaseAuthRepository(
    private val firebase: FirebaseAuth
) : AuthRepository {
    // Auth is app-owned. A startup screen transition after Firebase creates the account
    // must not cancel the remaining profile write and leave a nameless new account.
    private val profileCreationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun currentUser(): AuthUser? = firebase.currentUser?.toAuthUser()

    override fun observeUser(onChanged: (AuthUser?) -> Unit): AuthSubscription {
        val listener = FirebaseAuth.IdTokenListener { auth -> onChanged(auth.currentUser?.toAuthUser()) }
        firebase.addIdTokenListener(listener)
        return AuthSubscription { firebase.removeIdTokenListener(listener) }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isBlank() || password.isBlank()) {
            return Result.failure(AuthFailure("E-posta ve şifre gerekli."))
        }
        return firebase.signInWithEmailAndPassword(normalizedEmail, password)
            .awaitAuthResult()
    }

    override suspend fun createAccount(email: String, password: String): Result<AuthUser> {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isBlank() || password.isBlank()) {
            return Result.failure(AuthFailure("E-posta ve şifre gerekli."))
        }
        return firebase.createUserWithEmailAndPassword(normalizedEmail, password)
            .awaitAuthResult()
    }

    override suspend fun createAccountWithName(email: String, password: String, firstName: String, lastName: String): Result<AuthUser> {
        try { profileDisplayName(firstName, lastName) }
        catch (failure: IllegalArgumentException) { return Result.failure(AuthFailure(failure.message ?: "Adınızı yazın.")) }
        return profileCreationScope.async {
            val created = createAccount(email, password)
            if (created.isFailure) return@async created
            val uid = created.getOrThrow().id
            updateProfileName(uid, firstName, lastName).fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(AuthProfileFailure(uid, it.message ?: "İsim kaydedilemedi. Tekrar deneyin.")) }
            )
        }.await()
    }

    override suspend fun updateProfileName(expectedUid: String, firstName: String, lastName: String): Result<AuthUser> {
        val name = try { profileDisplayName(firstName, lastName) }
        catch (failure: IllegalArgumentException) { return Result.failure(AuthFailure(failure.message ?: "Adınızı yazın.")) }
        val user = firebase.currentUser
        if (user == null || user.uid != expectedUid || user.isAnonymous) {
            return Result.failure(AuthFailure("Hesap değişti. Tekrar giriş yapın."))
        }
        val saved = user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).awaitUnit()
        if (saved.isFailure) return Result.failure(saved.exceptionOrNull()!!)
        if (firebase.currentUser?.uid != expectedUid) return Result.failure(AuthFailure("Hesap değişti."))
        // The Worker reads the verified JWT name; do not send a stale token after saving.
        val refreshed = requestIdToken(expectedUid, true)
        if (refreshed.isFailure) return Result.failure(refreshed.exceptionOrNull()!!)
        if (firebase.currentUser?.uid != expectedUid) return Result.failure(AuthFailure("Hesap değişti."))
        return Result.success(user.toAuthUser())
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isBlank()) {
            return Result.failure(AuthFailure("E-posta adresini yazın."))
        }
        return firebase.sendPasswordResetEmail(normalizedEmail).awaitUnit()
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser> {
        if (idToken.isBlank()) return Result.failure(AuthFailure("Google kimlik bilgisi boş."))
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return firebase.signInWithCredential(credential).awaitAuthResult()
    }

    override suspend fun idToken(forceRefresh: Boolean): Result<String?> = requestIdToken(null, forceRefresh)

    override suspend fun idTokenForUser(expectedUid: String): Result<String?> = requestIdToken(expectedUid, false)

    private suspend fun requestIdToken(expectedUid: String?, forceRefresh: Boolean): Result<String?> {
        val user = firebase.currentUser ?: return Result.success(null)
        if (expectedUid != null && user.uid != expectedUid) return Result.failure(AuthFailure("Account changed"))
        return suspendCancellableCoroutine { continuation ->
            user.getIdToken(forceRefresh).addOnCompleteListener { task ->
                if (continuation.isActive) {
                    val result: Result<String?> = if (expectedUid != null && firebase.currentUser?.uid != expectedUid) {
                        Result.failure(AuthFailure("Account changed"))
                    } else if (task.isSuccessful) {
                        Result.success(task.result?.token)
                    } else {
                        Result.failure(mapFailure(task.exception))
                    }
                    continuation.resume(result)
                }
            }
        }
    }

    override suspend fun reauthenticateWithEmail(expectedUid: String, password: String): Result<Unit> {
        val user = firebase.currentUser
        if (user == null || user.uid != expectedUid || user.email == null || password.isBlank())
            return Result.failure(AuthFailure("Hesap değişti veya şifre boş."))
        return reauthenticate(expectedUid, com.google.firebase.auth.EmailAuthProvider.getCredential(user.email!!, password))
    }
    override suspend fun reauthenticateWithGoogleIdToken(expectedUid: String, idToken: String): Result<Unit> =
        reauthenticate(expectedUid, GoogleAuthProvider.getCredential(idToken, null))

    private suspend fun reauthenticate(expectedUid: String, credential: com.google.firebase.auth.AuthCredential): Result<Unit> {
        val user = firebase.currentUser
        if (user == null || user.uid != expectedUid) return Result.failure(AuthFailure("Hesap değişti."))
        val result = user.reauthenticate(credential).awaitUnit()
        if (result.isFailure) return result
        if (firebase.currentUser?.uid != expectedUid) return Result.failure(AuthFailure("Hesap değişti."))
        return requestIdToken(expectedUid, true).map { Unit }
    }
    override suspend fun deleteAccount(expectedUid: String): Result<Unit> {
        val user = firebase.currentUser
        if (user == null || user.uid != expectedUid) return Result.failure(AuthFailure("Hesap değişti."))
        return user.delete().awaitUnit()
    }

    override fun signOut() = firebase.signOut()

    private suspend fun Task<AuthResult>.awaitAuthResult(): Result<AuthUser> =
        suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                if (task.isSuccessful) {
                    val user = task.result?.user
                    if (user != null) continuation.resume(Result.success(user.toAuthUser()))
                    else continuation.resume(Result.failure(AuthFailure("Hesap bilgisi alınamadı.")))
                } else {
                    continuation.resume(Result.failure(mapFailure(task.exception)))
                }
            }
        }

    private suspend fun Task<Void>.awaitUnit(): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                if (task.isSuccessful) continuation.resume(Result.success(Unit))
                else continuation.resume(Result.failure(mapFailure(task.exception)))
            }
        }

    private fun mapFailure(error: Throwable?): AuthFailure {
        val message = when (error) {
            is FirebaseAuthWeakPasswordException -> "Şifre çok zayıf. En az 6 karakter kullanın."
            is FirebaseAuthUserCollisionException -> "Bu e-posta adresi zaten başka bir hesapta kullanılıyor."
            is FirebaseAuthInvalidCredentialsException -> "E-posta veya şifre geçersiz."
            is FirebaseTooManyRequestsException -> "Çok fazla deneme yapıldı. Biraz sonra tekrar deneyin."
            is FirebaseNetworkException -> "İnternet bağlantısını kontrol edin."
            else -> error?.localizedMessage?.takeIf { it.isNotBlank() } ?: "Kimlik doğrulama başarısız."
        }
        return AuthFailure(message)
    }

    private fun FirebaseUser.toAuthUser(): AuthUser = AuthUser(
        id = uid,
        email = email,
        displayName = displayName,
        isAnonymous = isAnonymous,
        providerIds = providerData
            .mapNotNull { it.providerId }
            .filterNot { it == "firebase" }
            .distinct()
    )
}
