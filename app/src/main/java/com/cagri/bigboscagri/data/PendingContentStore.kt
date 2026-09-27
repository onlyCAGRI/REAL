package com.cagri.bigboscagri.data

import android.content.Context


class PendingContentStore(context: Context) {
    private val preferences = context.getSharedPreferences("real_pending_content", Context.MODE_PRIVATE)
    fun load(): IncomingContent? = preferences.getString("content", null)?.let(IncomingContent::decode)
    fun save(content: IncomingContent) { check(preferences.edit().putString("content", content.encode()).commit()) }
    fun clear() { check(preferences.edit().clear().commit()) }
}
