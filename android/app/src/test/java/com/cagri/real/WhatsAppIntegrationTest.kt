package com.cagri.real

import com.cagri.real.data.ChecklistItem
import com.cagri.real.data.RecurrenceRule
import com.cagri.real.data.ReminderRepository
import com.cagri.real.data.WhatsAppConsentRepository
import com.cagri.real.data.WhatsAppDraftCoordinator
import com.cagri.real.data.WhatsAppNetworkClient
import com.cagri.real.data.WhatsAppRecipientRepository
import com.cagri.real.data.WhatsAppSender
import com.cagri.real.data.buildWhatsAppPayloadJson
import com.cagri.real.data.normalizePhoneNumber
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

class WhatsAppIntegrationTest {

    private lateinit var originalNetworkClient: WhatsAppNetworkClient
    private lateinit var prefs: FakeSharedPreferences
    private lateinit var consentRepo: WhatsAppConsentRepository
    private lateinit var recipientRepo: WhatsAppRecipientRepository
    private lateinit var coordinator: WhatsAppDraftCoordinator

    @Before
    fun setUp() {
        originalNetworkClient = WhatsAppSender.networkClient
        prefs = FakeSharedPreferences()
        consentRepo = WhatsAppConsentRepository(prefs)
        recipientRepo = WhatsAppRecipientRepository(prefs)
        coordinator = WhatsAppDraftCoordinator(consentRepo, recipientRepo)
    }

    
    @After
    fun restoreNetworkClient() {
        WhatsAppSender.networkClient = originalNetworkClient
    }

    @Test
    fun testNormalizePhoneNumber() {
        assertEquals("905321234567", normalizePhoneNumber("0532 123 45 67"))
        assertEquals("905321234567", normalizePhoneNumber("+90 532 123 45 67"))
        assertEquals("905321234567", normalizePhoneNumber("905321234567"))
        assertEquals("905321234567", normalizePhoneNumber("532 123 45 67"))
        assertEquals("905321234567", normalizePhoneNumber("0532-123-4567"))
        assertEquals("905551234567", normalizePhoneNumber("+90 (555) 123 45 67"))

        
        assertNull(normalizePhoneNumber("12345"))
        assertNull(normalizePhoneNumber(""))
        assertNull(normalizePhoneNumber("0212 123 45 67")) 
        assertNull(normalizePhoneNumber("abcdef"))
    }

    
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

    
    @Test
    fun testFirstTapFlowConsentThenRecipient() {
        var consentShown = false
        var recipientShown = false

        
        coordinator.handleIconClick(
            onShowConsentDialog = { consentShown = true },
            onShowRecipientDialog = { recipientShown = true }
        )
        assertTrue(consentShown)
        assertFalse(recipientShown)
        assertFalse(coordinator.isArmed)

        
        coordinator.onConsentGranted(onShowRecipientDialog = { recipientShown = true })
        assertTrue(consentRepo.hasConsent())
        assertTrue(recipientShown)
        assertFalse(coordinator.isArmed) 

        
        coordinator.onRecipientSaved("905321234567")
        assertTrue(recipientRepo.hasRecipient())
        assertTrue(coordinator.isArmed)

        
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


    
    @Test
    fun testConsentPersistence() {
        assertFalse("Default consent must be false", consentRepo.hasConsent())

        consentRepo.setConsent(true)
        assertTrue("Consent must be true after granting", consentRepo.hasConsent())

        
        val freshRepo = WhatsAppConsentRepository(prefs)
        assertTrue("Fresh repository instance must read persisted consent", freshRepo.hasConsent())

        consentRepo.setConsent(false)
        assertFalse("Consent must be false after revocation", consentRepo.hasConsent())
        assertFalse("Fresh repo must also reflect revocation", freshRepo.hasConsent())
    }

    
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

    
    @Test
    fun testGrantingPersistsConsentAndArmsCurrentDraft() {
        assertFalse(consentRepo.hasConsent())
        assertFalse(coordinator.isArmed)
        recipientRepo.setRecipient("905321234567")

        coordinator.onConsentGranted()

        assertTrue("Granting must persist consent to true", consentRepo.hasConsent())
        assertTrue("Granting must immediately arm the WhatsApp action for the current draft", coordinator.isArmed)
    }

    
    @Test
    fun testDismissingLeavesUnarmedAndUngranted() {
        assertFalse(consentRepo.hasConsent())
        assertFalse(coordinator.isArmed)

        coordinator.onConsentDismissed()

        assertFalse("Dismissing must keep consent as false", consentRepo.hasConsent())
        assertFalse("Dismissing must keep action unarmed", coordinator.isArmed)
    }

    
    @Test
    fun testDirectToggleWhenConsentGranted() {
        consentRepo.setConsent(true)
        recipientRepo.setRecipient("905321234567")
        var consentDialogOpened = false

        
        coordinator.handleIconClick { consentDialogOpened = true }
        assertTrue("Tap 1 must arm the action", coordinator.isArmed)
        assertFalse("Consent sheet must NOT be shown when consent is already granted", consentDialogOpened)

        
        coordinator.handleIconClick { consentDialogOpened = true }
        assertFalse("Tap 2 must disarm the action", coordinator.isArmed)
        assertFalse("Consent sheet must NOT be shown when consent is already granted", consentDialogOpened)

        
        coordinator.handleIconClick { consentDialogOpened = true }
        assertTrue("Tap 3 must re-arm the action", coordinator.isArmed)
        assertFalse(consentDialogOpened)
    }

    
    @Test
    fun testSubmittingResetsArmedStateButKeepsConsent() {
        consentRepo.setConsent(true)
        coordinator.setArmedDirectly(true)
        assertTrue(coordinator.isArmed)

        coordinator.onReminderSubmitted()

        assertFalse("Armed state must reset to false after submission", coordinator.isArmed)
        assertTrue("Consent must remain true after submission", consentRepo.hasConsent())
    }

    
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


    
    @Test
    fun testWorkerDispatchSuccess() = runBlocking {
        val fakeClient = object : WhatsAppNetworkClient {
            var capturedUrl: String? = null
            var capturedPayload: String? = null

            override suspend fun postJson(url: String, jsonPayload: String): Int {
                capturedUrl = url
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
            endpoint = "https://test.workers.dev/send"
        )

        assertTrue("sendReminder must return true on HTTP 200", success)
        assertEquals("https://test.workers.dev/send", fakeClient.capturedUrl)
        assertNotNull(fakeClient.capturedPayload)
    }

    @Test
    fun testWorkerDispatchHttpErrorResilience() = runBlocking {
        WhatsAppSender.networkClient = object : WhatsAppNetworkClient {
            override suspend fun postJson(url: String, jsonPayload: String): Int = 500
        }

        val success = WhatsAppSender.sendReminder(
            id = 102L,
            text = "Test reminder",
            isChecklist = false,
            checklistItems = emptyList(),
        )

        assertFalse("sendReminder must return false on HTTP 500 without crashing", success)
    }

    @Test
    fun testWorkerDispatchNetworkExceptionResilience() = runBlocking {
        WhatsAppSender.networkClient = object : WhatsAppNetworkClient {
            override suspend fun postJson(url: String, jsonPayload: String): Int {
                throw IOException("Connection timed out")
            }
        }

        val success = WhatsAppSender.sendReminder(
            id = 103L,
            text = "Test reminder",
            isChecklist = false,
            checklistItems = emptyList(),
        )

        assertFalse("sendReminder must return false on network IOException without crashing", success)
    }

    @Test
    fun testWorkerDispatchRejectedAuthentication() = runBlocking {
        for (status in listOf(401, 403)) {
            WhatsAppSender.networkClient = object : WhatsAppNetworkClient {
                override suspend fun postJson(url: String, jsonPayload: String): Int = status
            }
            assertFalse(WhatsAppSender.sendReminder(
                id = 104L, text = "Test reminder", isChecklist = false, checklistItems = emptyList()
            ))
        }
    }

    @Test
    fun testLocalReminderPersistenceIndependence() = runBlocking {
        val repo = ReminderRepository(prefs)

        
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

        
        WhatsAppSender.networkClient = object : WhatsAppNetworkClient {
            override suspend fun postJson(url: String, jsonPayload: String): Int {
                throw IOException("Network offline")
            }
        }
        val dispatchResult = WhatsAppSender.sendReminder(
            id = createdId,
            text = created.text,
            isChecklist = created.isChecklist,
            checklistItems = created.checklistItems.map { it.text },
        )
        assertFalse("Worker dispatch fails due to network offline", dispatchResult)

        
        val reloaded = repo.load()
        val found = reloaded.firstOrNull { it.id == createdId }
        assertNotNull("Local reminder must NOT be deleted or rolled back on worker failure", found)
        assertEquals("Önemli yerel not", found?.text)
        assertTrue(found?.isChecklist == true)
        assertEquals(2, found?.checklistItems?.size)
        assertEquals("Madde 1", found?.checklistItems?.get(0)?.text)
        assertEquals("Madde 2", found?.checklistItems?.get(1)?.text)
    }

    
    @Test
    fun testRealLabsFlowStateTransitions() {
        var isLabsOpen = false
        val onSwipeUp = { isLabsOpen = true }
        val onSwipeDown = { isLabsOpen = false }

        
        assertFalse("Real Labs must be closed by default", isLabsOpen)

        
        onSwipeUp()
        assertTrue("Deliberate upward drag must reveal Real Labs", isLabsOpen)

        
        onSwipeDown()
        assertFalse("Downward drag must collapse Real Labs", isLabsOpen)

        
        onSwipeUp()
        assertTrue(isLabsOpen)

        
        coordinator.onReminderSubmitted()
        isLabsOpen = false
        assertFalse("Submitting reminder must collapse Real Labs", isLabsOpen)
        assertFalse("Submitting reminder must disarm WhatsApp", coordinator.isArmed)
    }
}
