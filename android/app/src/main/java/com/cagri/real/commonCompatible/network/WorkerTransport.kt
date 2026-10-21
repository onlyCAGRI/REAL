package com.cagri.real.network

/** Suspending boundary: each platform owns I/O execution and scheduling. */
fun interface HttpTransport {
    suspend fun execute(request: HttpRequest): HttpResponse
}

data class HttpRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: String,
    val connectTimeoutMillis: Int,
    val readTimeoutMillis: Int,
    // Media search historically throws before reading an HTTP error body.
    val readErrorBody: Boolean = true,
    val readResponseBody: Boolean = true
)

data class HttpResponse(val statusCode: Int, val body: String)

/** Worker protocol only. D1 remains behind the Worker; clients never connect to D1. */
class WorkerClient(private val transport: HttpTransport) {
    suspend fun postJson(endpoint: String, payload: String, connectTimeoutMillis: Int,
                 readTimeoutMillis: Int, readErrorBody: Boolean = true): HttpResponse = transport.execute(
        HttpRequest(
            url = endpoint,
            method = "POST",
            headers = buildMap {
                put("Content-Type", "application/json; charset=UTF-8")
                put("Accept", "application/json")
            },
            body = payload,
            connectTimeoutMillis = connectTimeoutMillis,
            readTimeoutMillis = readTimeoutMillis,
            readErrorBody = readErrorBody
        )
    )
}
