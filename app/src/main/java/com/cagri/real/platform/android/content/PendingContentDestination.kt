package com.cagri.real.platform.android.content

import android.content.Context
import com.cagri.real.content.ContentRequest

/** Durable single-flight external handoff. Atomic consumption and token-aware cancellation. */
object PendingContentDestination {
    // Handoff identity must remain visible across an account switch so it is rejected,
    // rather than disappearing into an account-scoped preference namespace and opening a chooser.
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("real_content_destination", Context.MODE_PRIVATE)
    @Synchronized fun begin(context: Context, request: ContentRequest): Boolean =
        prefs(context).edit().clear().putString("request", request.json().toString()).commit()
    @Synchronized fun hasPending(context: Context): Boolean = prefs(context).all.isNotEmpty()
    @Synchronized fun peek(context: Context): ContentRequest? = runCatching {
        prefs(context).getString("request", null)?.let { ContentRequest.from(it) }
    }.getOrNull()
    @Synchronized fun consume(context: Context): ContentRequest? {
        val request = peek(context)
        return if (prefs(context).edit().clear().commit()) request else null
    }
    @Synchronized fun cancel(context: Context, token: String) {
        val current = peek(context)
        if (current != null && com.cagri.real.content.ContentRouting.cancel(current, token) == null)
            prefs(context).edit().clear().commit()
    }
    /** Called on return to REAL; shares have already consumed their request in onCreate/onNewIntent. */
    @Synchronized fun returnedWithoutResult(context: Context) { prefs(context).edit().clear().commit() }
}
