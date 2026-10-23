package com.cagri.real.platform.android.connections

import com.cagri.real.connections.ConnectionsSnapshot
import com.cagri.real.i18n.t
import android.content.Context
import com.cagri.real.connections.ConnectionsCache
import java.security.MessageDigest

/** Local-only per-account cache. Never enters Card/Pano sync or the address book. */
class ConnectionsStore(context: Context, uid: String) : ConnectionsCache {
    private val key = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private val preferences = context.getSharedPreferences("real_connections_$key", Context.MODE_PRIVATE)
    override fun load(): ConnectionsSnapshot = preferences.getString("snapshot", null)?.let {
        ConnectionsCodec.decode(it)
    } ?: ConnectionsSnapshot()
    override fun save(snapshot: ConnectionsSnapshot) {
        check(preferences.edit().putString("snapshot", ConnectionsCodec.encode(snapshot)).commit()) { t("Bağlantılar cihaza kaydedilemedi.") }
    }
}
