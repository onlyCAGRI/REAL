package com.cagri.real.platform.android.connections

import com.cagri.real.connections.*
import org.json.JSONArray
import org.json.JSONObject

internal object ConnectionsCodec {
    fun decode(value: String): ConnectionsSnapshot {
        val obj = JSONObject(value)
        fun rows(key: String): List<JSONObject> = obj.getJSONArray(key).let { array ->
            (0 until array.length()).map { array.getJSONObject(it) }
        }
        return ConnectionsSnapshot(
            rows("connections").map { RealConnection(it.getString("uid"), it.getString("name"), it.getLong("createdAt"), it.getLong("updatedAt"), it.getLong("acceptedAt")) },
            rows("invites").map { ConnectionInvitation(it.getString("id"), it.getLong("createdAt"), it.getLong("expiresAt")) },
            rows("blocked").map { BlockedConnection(it.getString("uid"), it.optString("name").takeIf { n -> n.isNotBlank() && n != "null" } ?: "REAL user") }
        )
    }
    fun encode(snapshot: ConnectionsSnapshot): String = JSONObject()
        .put("connections", JSONArray(snapshot.people.map { JSONObject().put("uid", it.uid).put("name", it.name)
            .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt).put("acceptedAt", it.acceptedAt) }))
        .put("invites", JSONArray(snapshot.invitations.map { JSONObject().put("id", it.id).put("createdAt", it.createdAt).put("expiresAt", it.expiresAt) }))
        .put("blocked", JSONArray(snapshot.blocked.map { JSONObject().put("uid", it.uid).put("name", it.name) })).toString()
}
