package com.cagri.real.platform.android.sync

/** Guarded by the wrapper's listener lock. Defer intermediate receipts while its UI edits are queued. */
internal class PreferenceNotificationGate {
    private var pending = 0
    private val deferred = linkedSetOf<String>()
    fun begin() { pending++ }
    fun receive(keys: Set<String>): Set<String> {
        if (pending == 0) return keys
        deferred.addAll(keys)
        return emptySet()
    }
    fun end(): Set<String> {
        check(pending > 0)
        if (--pending != 0) return emptySet()
        return deferred.toSet().also { deferred.clear() }
    }
}
