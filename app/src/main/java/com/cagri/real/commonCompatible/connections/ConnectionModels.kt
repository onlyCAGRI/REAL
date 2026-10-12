package com.cagri.real.connections

import java.net.URI

data class RealConnection(val uid: String, val name: String, val createdAt: Long, val updatedAt: Long, val acceptedAt: Long)
data class ConnectionInvitation(val id: String, val createdAt: Long, val expiresAt: Long)
data class BlockedConnection(val uid: String, val name: String)
data class ConnectionsSnapshot(
    val people: List<RealConnection> = emptyList(),
    val invitations: List<ConnectionInvitation> = emptyList(),
    val blocked: List<BlockedConnection> = emptyList()
)
data class ConnectionInviteLink(val id: String, val url: String, val expiresAt: Long)
private val tokenPattern = Regex("[0-9a-f]{64}")
fun connectionTokenFromLink(value: String?): String? {
    return runCatching {
        val uri = URI(value ?: return null)
        if (uri.scheme != "real" || uri.host != "connection" || uri.rawUserInfo != null || uri.port != -1 ||
            uri.rawQuery != null || uri.rawFragment != null) return null
        uri.rawPath?.removePrefix("/")?.takeIf { tokenPattern.matches(it) && uri.rawPath == "/$it" }
    }.getOrNull()
}
fun validatedConnectionInviteUrl(value: String): String? = runCatching {
    val uri = URI(value)
    val token = uri.rawPath?.removePrefix("/c/")
    if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
        (uri.port != -1 && uri.port != 443) || uri.rawQuery != null || uri.rawFragment != null ||
        token == null || !tokenPattern.matches(token) || uri.rawPath != "/c/$token") null else uri.toASCIIString()
}.getOrNull()

/** Small boundaries allow offline/account tests without Firebase or Android. */
interface ConnectionsGateway {
    suspend fun list(): ConnectionsSnapshot
    suspend fun invite(email: String?): ConnectionInviteLink
    suspend fun revoke(id: String)
    suspend fun remove(person: String)
    suspend fun block(person: String)
    suspend fun unblock(person: String)
}
interface ConnectionsCache {
    fun load(): ConnectionsSnapshot
    fun save(snapshot: ConnectionsSnapshot)
}
