package com.cagri.real.platform.android.network

import com.cagri.real.network.HttpRequest
import com.cagri.real.network.HttpResponse
import com.cagri.real.network.HttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Mirrors the legacy clients' UTF-8, timeout and disconnect behavior. */
object AndroidHttpTransport : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                requestMethod = request.method
                connectTimeout = request.connectTimeoutMillis
                readTimeout = request.readTimeoutMillis
                doInput = true
                instanceFollowRedirects = false
                doOutput = request.method != "GET" && request.method != "HEAD"
                request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }
            if (connection.doOutput) connection.outputStream.use { stream ->
                stream.write(request.body.toByteArray(Charsets.UTF_8))
                stream.flush()
            }
            val code = connection.responseCode
            val stream = if (!request.readResponseBody) null
                else if (code in 200..299) connection.inputStream
                else if (request.readErrorBody) connection.errorStream else null
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            HttpResponse(code, body)
        } finally {
            runCatching { connection?.disconnect() }
        }
    }
}
