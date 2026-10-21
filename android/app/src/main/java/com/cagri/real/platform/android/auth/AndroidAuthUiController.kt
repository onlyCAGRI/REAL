package com.cagri.real.platform.android.auth

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.cagri.real.R
import com.cagri.real.auth.AuthCancelled
import com.cagri.real.auth.AuthFailure
import com.cagri.real.auth.AuthRepository
import com.cagri.real.auth.AuthUiController
import com.cagri.real.auth.AuthUser
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android identity bridge. Credential Manager stays platform-side; Firebase remains behind
 * AuthRepository so Compose and future commonMain code never depend on Firebase types.
 */
class AndroidAuthUiController(
    private val activity: Activity,
    private val repository: AuthRepository
) : AuthUiController {
    private val credentialManager = CredentialManager.create(activity)
    private val _user = MutableStateFlow(repository.currentUser())
    override val user: StateFlow<AuthUser?> = _user.asStateFlow()

    private val subscription = repository.observeUser { _user.value = it }

    companion object {
        // Shared across Activity recreation. Never launch a second concurrent chooser.
        private val googleSignIn = Mutex()
    }

    override suspend fun signInWithGoogle(): Result<AuthUser> {
        if (!googleSignIn.tryLock()) return Result.failure(AuthCancelled())
        return try {
            val serverClientId = activity.getString(R.string.default_web_client_id).trim()
            if (serverClientId.isBlank()) {
                return Result.failure(AuthFailure("Google oturum açma yapılandırması bulunamadı."))
            }

            val googleOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build()
            val credential = credentialManager.getCredential(
                context = activity,
                request = request
            ).credential

            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return Result.failure(AuthFailure("Google hesabı doğrulanamadı."))
            }

            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            repository.signInWithGoogleIdToken(googleCredential.idToken).also { result ->
                if (result.isSuccess) _user.value = repository.currentUser()
            }
        } catch (_: GetCredentialCancellationException) {
            Result.failure(AuthCancelled())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(AuthFailure(error.localizedMessage ?: "Google ile giriş yapılamadı."))
        } finally {
            googleSignIn.unlock()
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> =
        repository.signInWithEmail(email, password)

    override suspend fun createAccount(email: String, password: String): Result<AuthUser> =
        repository.createAccount(email, password)

    override suspend fun createAccountWithName(email: String, password: String, firstName: String, lastName: String): Result<AuthUser> =
        repository.createAccountWithName(email, password, firstName, lastName).also {
            _user.value = repository.currentUser()
        }

    override suspend fun updateProfileName(expectedUid: String, firstName: String, lastName: String): Result<AuthUser> =
        repository.updateProfileName(expectedUid, firstName, lastName).also { result ->
            result.onSuccess { _user.value = it }
        }

    override suspend fun sendPasswordReset(email: String): Result<Unit> =
        repository.sendPasswordReset(email)

    override suspend fun reauthenticateWithEmail(expectedUid: String, password: String): Result<Unit> =
        repository.reauthenticateWithEmail(expectedUid, password)

    override suspend fun deleteAccount(expectedUid: String): Result<Unit> = repository.deleteAccount(expectedUid)

    override suspend fun reauthenticateWithGoogle(expectedUid: String): Result<Unit> {
        if (repository.currentUser()?.id != expectedUid) return Result.failure(AuthFailure("Hesap değişti."))
        if (!googleSignIn.tryLock()) return Result.failure(AuthCancelled())
        return try {
            val option = GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id)).build()
            val credential = credentialManager.getCredential(activity,
                GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
                return Result.failure(AuthFailure("Google hesabı doğrulanamadı."))
            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
            // Reauthenticate the existing FirebaseUser. Never sign in as another selected account.
            repository.reauthenticateWithGoogleIdToken(expectedUid, token)
        } catch (_: GetCredentialCancellationException) { Result.failure(AuthCancelled()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Result.failure(AuthFailure("Google doğrulaması tamamlanamadı.")) }
        finally { googleSignIn.unlock() }
    }

    override suspend fun signOut(): Result<Unit> {
        com.cagri.real.platform.android.boards.PanoPush.releaseToken(activity)
        repository.signOut()
        return runCatching {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            Unit
        }
    }

    override fun dispose() {
        subscription.cancel()
    }
}
