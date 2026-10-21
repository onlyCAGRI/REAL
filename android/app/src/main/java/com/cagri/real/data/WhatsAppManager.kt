package com.cagri.real.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class WhatsAppConsentRepository(private val preferences: SharedPreferences) {
    companion object {
        const val KEY_CONSENT = "whatsapp_internet_consent"
        const val PREFERENCES_NAME = "real_ui"
    }

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    )

    fun hasConsent(): Boolean {
        return preferences.getBoolean(KEY_CONSENT, false)
    }

    fun setConsent(granted: Boolean) {
        preferences.edit().putBoolean(KEY_CONSENT, granted).apply()
    }
}

class WhatsAppRecipientRepository(private val preferences: SharedPreferences) {
    companion object {
        const val KEY_RECIPIENT = "whatsapp_recipient_phone"
        const val PREFERENCES_NAME = "real_ui"
    }

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    )

    fun hasRecipient(): Boolean {
        return !preferences.getString(KEY_RECIPIENT, null).isNullOrBlank()
    }

    fun getRecipient(): String? {
        return preferences.getString(KEY_RECIPIENT, null)
    }

    fun setRecipient(phoneNumber: String) {
        preferences.edit().putString(KEY_RECIPIENT, phoneNumber).apply()
    }

    fun clearRecipient() {
        preferences.edit().remove(KEY_RECIPIENT).apply()
    }
}

fun normalizePhoneNumber(input: String): String? {
    val digits = input.filter { it.isDigit() }
    return when {
        
        digits.length == 11 && digits.startsWith("05") -> "9" + digits
        
        digits.length == 12 && digits.startsWith("905") -> digits
        
        digits.length == 10 && digits.startsWith("5") -> "90" + digits
        else -> null
    }
}

class WhatsAppDraftCoordinator(
    private val consentRepository: WhatsAppConsentRepository,
    private val recipientRepository: WhatsAppRecipientRepository? = null
) {
    var isArmed: Boolean = false
        private set

    fun handleIconClick(onShowConsentDialog: () -> Unit) {
        handleIconClick(onShowConsentDialog, {})
    }

    fun handleIconClick(
        onShowConsentDialog: () -> Unit,
        onShowRecipientDialog: () -> Unit
    ) {
        if (!consentRepository.hasConsent()) {
            onShowConsentDialog()
        } else if (recipientRepository != null && !recipientRepository.hasRecipient()) {
            onShowRecipientDialog()
        } else {
            isArmed = !isArmed
        }
    }

    fun onConsentGranted(onShowRecipientDialog: () -> Unit = {}) {
        consentRepository.setConsent(true)
        if (recipientRepository != null && !recipientRepository.hasRecipient()) {
            onShowRecipientDialog()
        } else {
            isArmed = true
        }
    }

    fun onRecipientSaved(phoneNumber: String) {
        recipientRepository?.setRecipient(phoneNumber)
        isArmed = true
    }

    fun onConsentDismissed() {
        
    }

    fun onReminderSubmitted() {
        isArmed = false
    }

    fun setArmedDirectly(armed: Boolean) {
        isArmed = armed
    }
}

fun buildWhatsAppPayloadJson(
    id: Long,
    text: String,
    isChecklist: Boolean,
    checklistItems: List<String>,
    recipient: String? = null
): String {
    val json = JSONObject()
    json.put("id", id)
    json.put("text", text)
    json.put("isChecklist", isChecklist)
    val array = JSONArray()
    checklistItems.forEach { array.put(it) }
    json.put("checklistItems", array)
    if (!recipient.isNullOrBlank()) {
        json.put("to", recipient)
    }
    return json.toString()
}

object WhatsAppSender {
    const val WORKER_ENDPOINT = "https://real-backend-staging.cagrigul1907.workers.dev/send"

    var networkClient: WhatsAppNetworkClient = HttpUrlConnectionWhatsAppClient()

    suspend fun sendReminder(
        id: Long,
        text: String,
        isChecklist: Boolean,
        checklistItems: List<String>,
        recipient: String? = null,
        endpoint: String = WORKER_ENDPOINT
    ): Boolean {
        return try {
            val payload = buildWhatsAppPayloadJson(id, text, isChecklist, checklistItems, recipient)
            val statusCode = networkClient.postJson(endpoint, payload)
            statusCode in 200..299
        } catch (_: Exception) {
            false
        }
    }
}

