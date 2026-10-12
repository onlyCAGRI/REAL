package com.cagri.real.platform.android.cloud

import com.cagri.real.auth.AuthRepository
import com.cagri.real.cloud.AttachmentTransfer
import com.cagri.real.cloud.CloudHttpFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class AndroidAttachmentTransfer(
    private val origin: String,
    private val uid: String,
    private val auth: AuthRepository,
    private val headers: suspend () -> Map<String, String>
) : AttachmentTransfer {
    override suspend fun upload(id: String, mime: String, bytes: ByteArray) {
        require(bytes.size in 1..MAX_BYTES)
        exchange(id, mime, bytes)
    }
    override suspend fun download(id: String): ByteArray = exchange(id, null, null)
    private suspend fun exchange(id: String, mime: String?, bytes: ByteArray?): ByteArray = withContext(Dispatchers.IO) {
        require(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(id))
        require(origin.startsWith("https://"))
        check(auth.currentUser()?.id == uid) { "Account changed" }
        val credentials = headers()
        check(auth.currentUser()?.id == uid) { "Account changed" }
        val connection = URL("$origin/api/cloud/v1/attachments/$id/content").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
            connection.requestMethod = if (bytes == null) "GET" else "PUT"
            credentials.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            if (bytes != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.setRequestProperty("Content-Type", mime)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            if (status !in 200..299) throw CloudHttpFailure(status)
            val result = if (bytes != null) ByteArray(0) else connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= MAX_BYTES) { "Attachment exceeds client transfer limit" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            check(auth.currentUser()?.id == uid) { "Account changed" }
            result
        } finally { connection.disconnect() }
    }
    companion object { private const val MAX_BYTES = 16 * 1024 * 1024 }
}
