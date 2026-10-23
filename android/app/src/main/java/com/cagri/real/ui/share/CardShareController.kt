package com.cagri.real.ui.share

import com.cagri.real.i18n.t
import com.cagri.real.share.validatedPublicShareUrl
import com.cagri.real.platform.android.share.CardShareClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Feature-scoped orchestration. No sharing network/state machinery in ReminderScreen. */
class CardShareController(
    private val cardId: Long,
    private val selectedIds: Set<String>?,
    private val scope: CoroutineScope,
    private val client: CardShareClient = CardShareClient(),
    private val requestId: String = UUID.randomUUID().toString(),
    initialLink: CardShareClient.Link? = null
) {
    data class State(val busy: Boolean = false, val link: CardShareClient.Link? = null, val message: String? = null, val revoked: Boolean = false)
    private val mutable = MutableStateFlow(State(link = initialLink?.takeIf {
        validatedPublicShareUrl(it.id, it.url) != null
    }))
    val state = mutable.asStateFlow()
    fun clearMessage() { mutable.value = mutable.value.copy(message = null) }
    fun create(options: CardShareClient.Options, onReady: (String) -> Unit) = run {
        val link = mutable.value.link ?: client.create(cardId, options.copy(selectedIds = selectedIds), requestId)
        mutable.value = mutable.value.copy(link = link)
        onReady(link.url)
    }
    fun send(email: String, options: CardShareClient.Options) = run {
        require(email.trim().matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) { t("Geçerli bir e-posta adresi gir.") }
        val link = mutable.value.link ?: client.create(cardId, options.copy(selectedIds = selectedIds), requestId)
        mutable.value = mutable.value.copy(link = link)
        client.send(link.id, email)
        mutable.value = mutable.value.copy(message = t("Gönderildi. Kart, alıcının REAL'i eşitlendiğinde görünür. REAL kullanmıyorsa bağlantıyı paylaş."))
    }
    suspend fun sendConnection(uid: String, options: CardShareClient.Options) {
        check(!mutable.value.busy && !mutable.value.revoked) { t("Paylaşım meşgul veya kapatılmış.") }
        mutable.value = mutable.value.copy(busy = true, message = null)
        try {
            val link = mutable.value.link ?: client.create(cardId, options.copy(selectedIds = selectedIds), requestId)
            mutable.value = mutable.value.copy(link = link)
            client.sendConnection(link.id, uid)
            mutable.value = mutable.value.copy(message = t("Gönderildi. Kart alıcının REAL sync akışında Kartlarım'a eklenecek."))
        } finally { mutable.value = mutable.value.copy(busy = false) }
    }
    fun revoke() = run {
        mutable.value.link?.let { client.revoke(it.id) }
        mutable.value = State(message = t("Paylaşım durduruldu. Daha önce kaydedilen kopyalar korunur."), revoked = true)
    }
    private fun run(block: suspend () -> Unit) {
        if (mutable.value.busy || mutable.value.revoked) return
        mutable.value = mutable.value.copy(busy = true, message = null)
        scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val userMessage = when {
                    failure is SecurityException || failure is java.io.FileNotFoundException ||
                    failure.message?.contains("Permission Denial", ignoreCase = true) == true ||
                    failure.message?.contains("MediaDocumentsProvider", ignoreCase = true) == true ||
                    failure.message?.contains("SecurityException", ignoreCase = true) == true -> t("Ekli dosyaya artık erişilemiyor")
                    else -> failure.message ?: t("Kart paylaşılamadı.")
                }
                mutable.value = mutable.value.copy(message = userMessage)
            }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
