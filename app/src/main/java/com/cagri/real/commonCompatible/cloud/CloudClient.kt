package com.cagri.real.cloud

import com.cagri.real.network.HttpRequest
import com.cagri.real.network.HttpTransport

enum class CloudKind { CARD, CALENDAR }
enum class CloudRole { OWNER, EDITOR, VIEWER }
data class CloudResource(val id: String, val kind: CloudKind, val title: String, val completed: Boolean,
    val revision: Long, val createdAt: Long, val updatedAt: Long, val deletedAt: Long?)
data class CloudChange(val sequence: Long, val resourceId: String, val entityKind: String,
    val entityId: String, val revision: Long, val deletedAt: Long?)
data class CloudPage(val changes: List<CloudChange>, val cursor: Long, val hasMore: Boolean)
data class CloudAttachment(val id: String, val resourceId: String, val kind: String, val mime: String,
    val byteSize: Long, val state: String, val revision: Long, val deletedAt: Long?)

/** JSON implementation is replaceable; no org.json / JVM types in this boundary. */
interface CloudCodec {
    fun create(id: String, kind: CloudKind, title: String, completed: Boolean): String
    fun update(revision: Long, title: String, completed: Boolean): String
    fun delete(revision: Long): String
    fun resource(json: String): CloudResource
    fun page(json: String): CloudPage
    fun reservation(id: String, kind: String, mime: String, byteSize: Long): String
    fun attachment(json: String): CloudAttachment
    fun errorCode(json: String): String? = null
}
class CloudHttpFailure(
    val status: Int, val backendCode: String? = null, val operation: String? = null
) : Exception("Cloud HTTP $status" + (backendCode?.let { " · $it" } ?: "") +
    (operation?.let { " · $it" } ?: ""))

/** Explicit calls only. Does NOT read or replace ReminderRepository, start jobs or auto-upload. */
class CloudClient(private val origin: String, private val http: HttpTransport, private val codec: CloudCodec) {
    private fun validateId(value: String): String {
        require(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(value))
        return value
    }
    private suspend fun call(path: String, method: String, body: String = ""): String {
        val response = http.execute(HttpRequest("$origin/api/cloud/v1/$path", method,
            mapOf("Content-Type" to "application/json"), body, 10000, 20000))
        if (response.statusCode !in 200..299) {
            val safeCode = runCatching { codec.errorCode(response.body) }.getOrNull()
                ?.takeIf { Regex("[A-Z][A-Z0-9_]{0,63}").matches(it) }
            // Describe only the endpoint family; never display resource IDs, tokens or raw bodies.
            val family = if (path.endsWith("/attachments")) "resources/attachments" else path.substringBefore('/')
            throw CloudHttpFailure(response.statusCode, safeCode, "$method $family")
        }
        return response.body
    }
    suspend fun create(id: String, kind: CloudKind, title: String, completed: Boolean = false): CloudResource =
        codec.resource(call("resources", "POST", codec.create(validateId(id), kind, title, completed)))
    suspend fun get(id: String): CloudResource = codec.resource(call("resources/${validateId(id)}", "GET"))
    suspend fun update(id: String, revision: Long, title: String, completed: Boolean): CloudResource =
        codec.resource(call("resources/${validateId(id)}", "PATCH", codec.update(revision, title, completed)))
    suspend fun delete(id: String, revision: Long): CloudResource =
        codec.resource(call("resources/${validateId(id)}", "DELETE", codec.delete(revision)))
    suspend fun changes(cursor: Long = 0): CloudPage {
        require(cursor >= 0)
        return codec.page(call("changes?cursor=$cursor", "GET"))
    }
    suspend fun reserveAttachment(parentId: String, attachmentId: String, kind: String, mime: String, byteSize: Long): CloudAttachment =
        codec.attachment(call("resources/${validateId(parentId)}/attachments", "POST", codec.reservation(validateId(attachmentId), kind, mime, byteSize)))
    suspend fun attachment(id: String): CloudAttachment = codec.attachment(call("attachments/${validateId(id)}", "GET"))
    suspend fun deleteAttachment(id: String, revision: Long) { call("attachments/${validateId(id)}", "DELETE", codec.delete(revision)) }
}
