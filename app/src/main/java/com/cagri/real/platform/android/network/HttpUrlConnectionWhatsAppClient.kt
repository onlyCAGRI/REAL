package com.cagri.real.data

import com.cagri.real.network.HttpRequest
import com.cagri.real.network.HttpTransport
import com.cagri.real.platform.android.auth.AndroidBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HttpUrlConnectionWhatsAppClient(
    private val connectTimeoutMs: Int = 5000,
    private val readTimeoutMs: Int = 5000,
    // Resolve the authenticated Android transport only when a real request is made.
    // Constructing the default sender must allow JVM tests to inject their fake client.
    private val transport: HttpTransport? = null
) : WhatsAppNetworkClient {
    override suspend fun postJson(url: String, jsonPayload: String): Int = withContext(Dispatchers.IO) {
        try {
            (transport ?: AndroidBackend.workerTransport).execute(HttpRequest(
                url = url,
                method = "POST",
                headers = mapOf(
                    "Content-Type" to "application/json; charset=UTF-8"
                ),
                body = jsonPayload,
                connectTimeoutMillis = connectTimeoutMs,
                readTimeoutMillis = readTimeoutMs,
                readResponseBody = false
            )).statusCode
        } catch (_: Exception) {
            -1
        }
    }
}
