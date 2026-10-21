package com.cagri.real.data

interface WhatsAppNetworkClient {
    suspend fun postJson(url: String, jsonPayload: String): Int
}

