package com.cagri.real.ai

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AiConsentTest {
    private class Store : AiConsentStore {
        val approved=mutableSetOf<Pair<String,AiFeature>>()
        var failWrite=false
        override suspend fun allowed(uid:String,feature:AiFeature)= (uid to feature) in approved
        override suspend fun save(uid:String,feature:AiFeature,allowed:Boolean) {
            check(!failWrite)
            if(allowed) approved+=uid to feature else approved-=uid to feature
        }
    }
    @Test fun requestCannotTransmitUntilExplicitConsentIsDurable()=runBlocking {
        val store=Store();val gate=AiConsentCoordinator(store);gate.setHostActive(true);var sent=0
        val job=async(start=CoroutineStart.UNDISPATCHED) { gate.requireConsent("a",AiFeature.VOICE) {};sent++ }
        assertEquals(0,sent);val prompt=gate.prompt.value!!
        gate.decide(prompt.id,true);job.await()
        assertEquals(1,sent);assertTrue(store.allowed("a",AiFeature.VOICE));assertNull(gate.prompt.value)
    }
    @Test fun dismissAndHostStopDoNotSendOrPersistPermission()=runBlocking {
        for(stop in listOf(false,true)) {
            val store=Store();val gate=AiConsentCoordinator(store);gate.setHostActive(true);var sent=0
            val job=async(start=CoroutineStart.UNDISPATCHED) { runCatching { gate.requireConsent("a",AiFeature.MOOD) {};sent++ } }
            if(stop) gate.setHostActive(false) else gate.decide(gate.prompt.value!!.id,false)
            assertTrue(job.await().exceptionOrNull() is AiConsentDeclined)
            assertEquals(0,sent);assertTrue(store.approved.isEmpty());assertNull(gate.prompt.value)
        }
    }
    @Test fun concurrentRequestsForSameFeatureShareOneApprovalAndDenialDoesNotPromptAgain()=runBlocking {
        for(approve in listOf(true,false)) {
            val store=Store();val gate=AiConsentCoordinator(store);gate.setHostActive(true)
            val jobs=(1..2).map { async(start=CoroutineStart.UNDISPATCHED) { runCatching { gate.requireConsent("a",AiFeature.MOOD) {} } } }
            val id=gate.prompt.value!!.id;gate.decide(id,approve)
            val results=jobs.map { it.await() }
            assertTrue(results.all { it.isSuccess==approve });assertNull(gate.prompt.value)
            assertEquals(approve,store.allowed("a",AiFeature.MOOD))
        }
    }
    @Test fun staleDialogCannotApproveAnotherRequest()=runBlocking {
        val gate=AiConsentCoordinator(Store());gate.setHostActive(true)
        val first=async(start=CoroutineStart.UNDISPATCHED) { runCatching { gate.requireConsent("a",AiFeature.MOOD) {} } }
        val stale=gate.prompt.value!!.id;gate.decide(stale,false);first.await()
        val next=async(start=CoroutineStart.UNDISPATCHED) { gate.requireConsent("a",AiFeature.VOICE) {} }
        val active=gate.prompt.value!!.id;gate.decide(stale,true)
        assertFalse(next.isCompleted);gate.decide(active,true);next.await()
    }
    @Test fun saveFailureCannotAllowTransmission()=runBlocking {
        val store=Store().apply { failWrite=true };val gate=AiConsentCoordinator(store);gate.setHostActive(true);var sent=0
        val job=async(start=CoroutineStart.UNDISPATCHED) { runCatching { gate.requireConsent("a",AiFeature.VOICE) {};sent++ } }
        gate.decide(gate.prompt.value!!.id,true)
        assertTrue(job.await().isFailure);assertEquals(0,sent);assertNull(gate.prompt.value)
    }
    @Test fun cancellationAndAccountSwitchCannotPersistAnApproval()=runBlocking {
        val store=Store();val gate=AiConsentCoordinator(store);gate.setHostActive(true)
        val cancelled=async(start=CoroutineStart.UNDISPATCHED) { gate.requireConsent("a",AiFeature.VOICE) {} }
        cancelled.cancelAndJoin();assertTrue(store.approved.isEmpty());assertNull(gate.prompt.value)
        var user="a"
        val switched=async(start=CoroutineStart.UNDISPATCHED) { runCatching { gate.requireConsent("a",AiFeature.VOICE) { check(user=="a") } } }
        user="b";gate.decide(gate.prompt.value!!.id,true)
        assertTrue(switched.await().isFailure);assertTrue(store.approved.isEmpty())
    }
    @Test fun permissionsAreAccountAndFeatureScopedAndRevocable()=runBlocking {
        val store=Store();store.approved+="a" to AiFeature.VOICE
        val gate=AiConsentCoordinator(store)
        gate.requireConsent("a",AiFeature.VOICE) {} // No UI required for already agreed work.
        assertFalse(gate.allowed("b",AiFeature.VOICE));assertFalse(gate.allowed("a",AiFeature.MOOD))
        gate.revoke("a",AiFeature.VOICE);assertFalse(gate.allowed("a",AiFeature.VOICE))
        assertTrue(runCatching { gate.requireConsent("a",AiFeature.VOICE) {} }.isFailure) // No active host, fail closed.
    }
    @Test fun generationEndpointsAreGatedButPrivateApisCatalogsAndReportsAreNot() {
        assertEquals(AiFeature.VOICE,aiFeatureForPath("/api/voice-intent"))
        assertEquals(AiFeature.MOOD,aiFeatureForPath("/api/mood-suggestions"))
        assertEquals(AiFeature.DISCOVERY,aiFeatureForPath("/api/media-recommendations"))
        assertEquals(AiFeature.DISCOVERY,aiFeatureForPath("/api/game-recommendations"))
        assertEquals(AiFeature.DIAGNOSTIC,aiFeatureForPath("/api/ai-diagnostic"))
        listOf("/api/cloud/v1/resources","/api/boards/v1/boards","/api/media-details","/api/media-search","/api/ai-health","/api/ai-reports/v1/reports","/send").forEach { assertNull(aiFeatureForPath(it)) }
    }
}
