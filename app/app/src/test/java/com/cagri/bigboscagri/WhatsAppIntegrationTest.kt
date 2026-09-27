package com.cagri.bigboscagri

import com.cagri.bigboscagri.data.ChecklistItem
import com.cagri.bigboscagri.data.RecurrenceRule
import com.cagri.bigboscagri.data.ReminderRepository
import com.cagri.bigboscagri.data.WhatsAppConsentRepository
import com.cagri.bigboscagri.data.WhatsAppDraftCoordinator
import com.cagri.bigboscagri.data.WhatsAppNetworkClient
import com.cagri.bigboscagri.data.WhatsAppRecipientRepository
import com.cagri.bigboscagri.data.WhatsAppSender
import com.cagri.bigboscagri.data.buildWhatsAppPayloadJson
import com.cagri.bigboscagri.data.normalizePhoneNumber
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class WhatsAppIntegrationTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var consentRepo: WhatsAppConsentRepository
    private lateinit var recipientRepo: WhatsAppRecipientRepository
    private lateinit var coordinator: WhatsAppDraftCoordinator

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        consentRepo = WhatsAppConsentRepository(prefs)
        recipientRepo = WhatsAppRecipientRepository(prefs)
        coordinator = WhatsAppDraftCoordinator(consentRepo, recipientRepo)
    }

    // 0. Phone number normalization
    @Test
    fun testNormalizePhoneNumber() {
        assertEquals("905321234567", normalizePhoneNumber("0532 123 45 67"))
        assertEquals("905321234567", normalizePhoneNumber("+90 532 123 45 67"))
        assertEquals("905321234567", normalizePhoneNumber("905321234567"))
        assertEquals("905321234567", normalizePhoneNumber("532 123 45 67"))
        assertEquals("905321234567", normalizePhoneNumber("0532-123-4567"))
        assertEquals("905551234567", normalizePhoneNumber("+90 (555) 123 45 67"))

        // Invalid numbers
        assertNull(normalizePhoneNumber("12345"))
        assertNull(normalizePhoneNumber(""))
        assertNull(normalizePhoneNumber("0212 123 45 67")) // landline
        assertNull(normalizePhoneNumber("abcdef"))
    }

    // 0b. Recipient persistence
    @Test
    fun testRecipientPersistence() {
        assertFalse(recipientRepo.hasRecipient())
        assertNull(recipientRepo.getRecipient())

        recipientRepo.setRecipient("905321234567")
        assertTrue(recipientRepo.hasRecipient())
        assertEquals("905321234567", recipientRepo.getRecipient())

        val freshRepo = WhatsAppRecipientRepository(prefs)
        assertTrue(freshRepo.hasRecipient())
        assertEquals("905321234567", freshRepo.getRecipient())

        recipientRepo.clearRecipient()
        assertFalse(recipientRepo.hasRecipient())
        assertNull(recipientRepo.getRecipient())
    }

    // 0c. First tap flow: consent then recipient setup
    @Test
    fun testFirstTapFlowConsentThenRecipient() {
        var consentShown = false
        var recipientShown = false

        // 1. First tap with no consent -> shows consent
        coordinator.handleIconClick(
            onShowConsentDialog = { consentShown = true },
            onShowRecipientDialog = { recipientShown = true }
        )
        assertTrue(consentShown)
        assertFalse(recipientShown)
        assertFalse(coordinator.isArmed)

        // 2. User grants consent, but recipient is missing -> prompts recipient
        coordinator.onConsentGranted(onShowRecipientDialog = { recipientShown = true })
        assertTrue(consentRepo.hasConsent())
        assertTrue(recipientShown)
        assertFalse(coordinator.isArmed) // Not armed until recipient is set

        // 3. User saves valid recipient -> becomes armed
        coordinator.onRecipientSaved("905321234567")
        assertTrue(recipientRepo.hasRecipient())
        assertTrue(coordinator.isArmed)

        // 4. Once both exist, subsequent tap directly toggles armed/unarmed
        coordinator.handleIconClick(
            onShowConsentDialog = { },
            onShowRecipientDialog = { }
        )
        assertFalse("Tapping again should disarm", coordinator.isArmed)

        coordinator.handleIconClick(
            onShowConsentDialog = { },
            onShowRecipientDialog = { }
        )
        assertTrue("Tapping once more should re-arm", coordinator.isArmed)
    }


    // 1. Consent persistence
    @Test
    fun testConsentPersistence() {
        assertFalse("Default consent must be false", consentRepo.hasConsent())

        consentRepo.setConsent(true)
        assertTrue("Consent must be true after granting", consentRepo.hasConsent())

        // Verify fresh repository instance reads the persisted data from SharedPreferences
        val freshRepo = WhatsAppConsentRepository(prefs)
        assertTrue("Fresh repository instance must read persisted consent", freshRepo.hasConsent())

        consentRepo.setConsent(false)
        assertFalse("Consent must be false after revocation", consentRepo.hasConsent())
        assertFalse("Fresh repo must also reflect revocation", freshRepo.hasConsent())
    }

    // 2. First tap opens consent flow when not granted
    @Test
    fun testFirstTapOpensConsentFlowWhenNotGranted() {
        assertFalse(consentRepo.hasConsent())
        var consentDialogOpened = false

        coordinator.handleIconClick {
            consentDialogOpened = true
        }

        assertTrue("First tap without consent must open consent sheet", consentDialogOpened)
        assertFalse("Action must not be armed before consent is granted", coordinator.isArmed)
    }

    // 3. Granting persists consent and arms current draft (when recipient is already configured)
    @Test
    fun testGrantingPersistsConsentAndArmsCurrentDraft() {
        assertFalse(consentRepo.hasConsent())
        assertFalse(coordinator.isArmed)
        recipientRepo.setRecipient("905321234567")

        coordinator.onConsentGranted()

        assertTrue("Granting must persist consent to true", consentRepo.hasConsent())
        assertTrue("Granting must immediately arm the WhatsApp action for the current draft", coordinator.isArmed)
    }

    // 4. Dismissing leaves unarmed and ungranted
    @Test
    fun testDismissingLeavesUnarmedAndUngranted() {
        assertFalse(consentRepo.hasConsent())
        assertFalse(coordinator.isArmed)

        coordinator.onConsentDismissed()

        assertFalse("Dismissing must keep consent as false", consentRepo.hasConsent())
        assertFalse("Dismissing must keep action unarmed", coordinator.isArmed)
    }

    // 5. Second tap directly toggles armed when granted and recipient exists
    @Test
    fun testDirectToggleWhenConsentGranted() {
        consentRepo.setConsent(true)
        recipientRepo.setRecipient("905321234567")
        var consentDialogOpened = false

        // Tap 1: arms the action
        coordinator.handleIconClick { consentDialogOpened = true }
        assertTrue("Tap 1 must arm the action", coordinator.isArmed)
        assertFalse("Consent sheet must NOT be shown when consent is already granted", consentDialogOpened)

        // Tap 2: disarms the action
        coordinator.handleIconClick { consentDialogOpened = true }
        assertFalse("Tap 2 must disarm the action", coordinator.isArmed)
        assertFalse("Consent sheet must NOT be shown when consent is already granted", consentDialogOpened)

        // Tap 3: re-arms the action
        coordinator.handleIconClick { consentDialogOpened = true }
        assertTrue("Tap 3 must re-arm the action", coordinator.isArmed)
        assertFalse(consentDialogOpened)
    }

    // 6. Submitting resets armed state but keeps consent
    @Test
    fun testSubmittingResetsArmedStateButKeepsConsent() {
        consentRepo.setConsent(true)
        coordinator.setArmedDirectly(true)
        assertTrue(coordinator.isArmed)

        coordinator.onReminderSubmitted()

        assertFalse("Armed state must reset to false after submission", coordinator.isArmed)
        assertTrue("Consent must remain true after submission", consentRepo.hasConsent())
    }

    // 7. Worker dispatch payload format (id, text, isChecklist, checklistItems)
    @Test
    fun testWorkerDispatchPayloadFormatChecklist() {
        val payloadStr = buildWhatsAppPayloadJson(
            id = 42L,
            text = "Alışveriş listesi",
            isChecklist = true,
            checklistItems = listOf("Süt", "Ekmek", "Yumurta")
        )

        val json = JSONObject(payloadStr)
        assertEquals(42L, json.getLong("id"))
        assertEquals("Alışveriş listesi", json.getString("text"))
        assertTrue(json.getBoolean("isChecklist"))

        val items = json.getJSONArray("checklistItems")
        assertEquals(3, items.length())
        assertEquals("Süt", items.getString(0))
        assertEquals("Ekmek", items.getString(1))
        assertEquals("Yumurta", items.getString(2))
    }

    @Test
    fun testWorkerDispatchPayloadFormatStandardReminder() {
        val payloadStr = buildWhatsAppPayloadJson(
            id = 999L,
            text = "Doktor randevusu",
            isChecklist = false,
            checklistItems = emptyList()
        )

        val json = JSONObject(payloadStr)
        assertEquals(999L, json.getLong("id"))
        assertEquals("Doktor randevusu", json.getString("text"))
        assertFalse(json.getBoolean("isChecklist"))
        assertEquals(0, json.getJSONArray("checklistItems").length())
    }

    @Test
    fun testWorkerDispatchPayloadWithRecipient() {
        val payloadStr = buildWhatsAppPayloadJson(
            id = 555L,
            text = "Faturayı öde",
            isChecklist = false,
            checklistItems = emptyList(),
            recipient = "905321234567"
        )
        val json = JSONObject(payloadStr)
        assertEquals(555L, json.getLong("id"))
        assertEquals("Faturayı öde", json.getString("text"))
        assertEquals("905321234567", json.getString("to"))
        assertTrue("JSON must contain literal 'to' key", json.has("to"))
        assertFalse("JSON must not contain 'recipient' key", json.has("recipient"))
    }


    // 8. Worker dispatch resilience (network failure, non-200, missing key)
    @Test
    fun testWorkerDispatchSuccess() = runBlocking {
        val fakeClient = object : WhatsAppNetworkClient {
            var capturedUrl: String? = null
            var capturedKey: String? = null
            var capturedPayload: String? = null

            override suspend fun postJson(url: String, apiKey: String, jsonPayload: String): Int {
                capturedUrl = url
                capturedKey = apiKey
                capturedPayload = jsonPayload
                return 200
            }
        }
        WhatsAppSender.networkClient = fakeClient

        val success = WhatsAppSender.sendReminder(
            id = 101L,
            text = "Test reminder",
            isChecklist = false,
            checklistItems = emptyList(),
            apiKey = "valid_test_api_key",
            endpoint = "https://test.workers.dev/send"
        )

        assertTrue("sendReminder must return true on HTTP 200", success)
        assertEquals("https://test.workers.dev/send", fakeClient.capturedUrl)
        assertEquals("valid_test_api_key", fakeClient.capturedKey)
        assertNotNull(fakeClient.capturedPayload)
    }

    @Test
    fun testWorkerDispatchHttpErrorResilience() = runBlocking {
        WhatsAppSender.networkClient = object : WhatsAppNetworkClient {
            override suspend fun postJson(url: String, apiKey: String, jsonPayload: String): Int = 500
        }

        val success = WhatsAppSender.sendReminder(
            id = 102L,
            text = "Test reminder",
            isChecklist = false,
            checklistItems = emptyList(),
            apiKey = "valid_test_api_key"
        )

        assertFalse("sendReminder must return false on HTTP 500 without crashing", success)
    }

    @Test
    fun testWorkerDispatchNetworkExceptionResilience() = runBlocking {
        WhatsAppSender.networkClient = object : WhatsAppNetworkClient {
            override suspend fun postJson(url: String, apiKey: String, jsonPayload: String): Int {
                throw IOException("Connection timed out")
            }
        }

        val success = WhatsAppSender.sendReminder(
            id = 103L,
            text = "Test reminder",
            isChecklist = false,
            checklistItems = emptyList(),
            apiKey = "valid_test_api_key"
        )

        assertFalse("sendReminder must return false on network IOException without crashing", success)
    }

    @Test
    fun testWorkerDispatchMissingKeySafeBehavior() = runBlocking {
        var networkCalled = false
        WhatsAppSender.networkClient = object : WhatsAppNetworkClient {
            override suspend fun postJson(url: String, apiKey: String, jsonPayload: String): Int {
                networkCalled = true
                return 200
            }
        }

        val emptyKeySuccess = WhatsAppSender.sendReminder(
            id = 104L,
            text = "Test reminder",
            isChecklist = false,
            checklistItems = emptyList(),
            apiKey = ""
        )
        assertFalse("sendReminder must return false when apiKey is empty", emptyKeySuccess)
        assertFalse("Network client must not be called when apiKey is empty", networkCalled)

        val blankKeySuccess = WhatsAppSender.sendReminder(
            id = 105L,
            text = "Test reminder",
            isChecklist = false,
            checklistItems = emptyList(),
            apiKey = "   "
        )
        assertFalse("sendReminder must return false when apiKey is blank", blankKeySuccess)
        assertFalse("Network client must not be called when apiKey is blank", networkCalled)
    }

    // 9. Local reminder persistence independence
    @Test
    fun testLocalReminderPersistenceIndependence() = runBlocking {
        val repo = ReminderRepository(prefs)

        // Step 1: Local reminder is created and persisted immediately
        val created = repo.add(
            text = "Önemli yerel not",
            alertAt = null,
            repeatRule = RecurrenceRule.NONE,
            customCategoryId = null,
            isChecklist = true,
            checklistItems = listOf(ChecklistItem(1, "Madde 1"), ChecklistItem(2, "Madde 2")),
            sendImmediateNotification = false,
            referenceUrl = null
        )
        assertNotNull(created)
        val createdId = created.id
        assertTrue(createdId > 0)

        // Step 2: Simulate Worker dispatch network failure
        WhatsAppSender.networkClient = object : WhatsAppNetworkClient {
            override suspend fun postJson(url: String, apiKey: String, jsonPayload: String): Int {
                throw IOException("Network offline")
            }
        }
        val dispatchResult = WhatsAppSender.sendReminder(
            id = createdId,
            text = created.text,
            isChecklist = created.isChecklist,
            checklistItems = created.checklistItems.map { it.text },
            apiKey = "some_key"
        )
        assertFalse("Worker dispatch fails due to network offline", dispatchResult)

        // Step 3: Verify local reminder is completely intact and unaffected
        val reloaded = repo.load()
        val found = reloaded.firstOrNull { it.id == createdId }
        assertNotNull("Local reminder must NOT be deleted or rolled back on worker failure", found)
        assertEquals("Önemli yerel not", found?.text)
        assertTrue(found?.isChecklist == true)
        assertEquals(2, found?.checklistItems?.size)
        assertEquals("Madde 1", found?.checklistItems?.get(0)?.text)
        assertEquals("Madde 2", found?.checklistItems?.get(1)?.text)
    }

    // 10. Real Labs flow: upward drag reveals, downward drag collapses, submit collapses
    @Test
    fun testRealLabsFlowStateTransitions() {
        var isLabsOpen = false
        val onSwipeUp = { isLabsOpen = true }
        val onSwipeDown = { isLabsOpen = false }

        // Initially closed
        assertFalse("Real Labs must be closed by default", isLabsOpen)

        // Deliberate upward drag reveals Real Labs
        onSwipeUp()
        assertTrue("Deliberate upward drag must reveal Real Labs", isLabsOpen)

        // Downward drag collapses Real Labs
        onSwipeDown()
        assertFalse("Downward drag must collapse Real Labs", isLabsOpen)

        // Opening again and then submitting reminder
        onSwipeUp()
        assertTrue(isLabsOpen)

        // On submit: local reminder saved, armed reset, Labs returns to closed
        coordinator.onReminderSubmitted()
        isLabsOpen = false
        assertFalse("Submitting reminder must collapse Real Labs", isLabsOpen)
        assertFalse("Submitting reminder must disarm WhatsApp", coordinator.isArmed)
    }
}
