package com.cagri.real.platform.android.sync

import android.content.SharedPreferences

/** Pinned to the session that created it: an old screen can never write the next account. */
internal class SyncPreferences(private val owner: SyncDatabase, val scope: String,
    val file: String, private val fallback: SharedPreferences) : SharedPreferences {
    private var knownValues: Map<String, Any?> = owner.cachedValues(scope,file)
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
    private val listenerLock = Any()
    private val notifications = PreferenceNotificationGate()
    private fun tracked(key: String) = SyncProjection.tracked(file, key)
    override fun getAll(): MutableMap<String, *> {
        val result = fallback.all.filterKeys { !tracked(it) }.toMutableMap()
        result.putAll(owner.cachedValues(scope, file))
        return result
    }
    private fun value(key: String): Any? = if (tracked(key)) owner.cachedValues(scope,file)[key]
        else fallback.all[key]
    override fun getString(key: String, defValue: String?): String? = value(key) as String? ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (value(key) as Set<String>?)?.toMutableSet() ?: defValues
    override fun getInt(key: String, defValue: Int) = (value(key) as Number?)?.toInt() ?: defValue
    override fun getLong(key: String, defValue: Long) = (value(key) as Number?)?.toLong() ?: defValue
    override fun getFloat(key: String, defValue: Float) = (value(key) as Number?)?.toFloat() ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = value(key) as Boolean? ?: defValue
    override fun contains(key: String) = value(key) != null
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(listenerLock) { listeners.add(listener) }
    }
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(listenerLock) { listeners.remove(listener) }
    }
    fun beginUiWrite() { synchronized(listenerLock) { notifications.begin() } }
    fun endUiWrite() {
        val delivery = synchronized(listenerLock) { listeners.toList() to notifications.end() }
        deliver(delivery.first, delivery.second)
    }
    fun changed(keys: Set<String>, values: Map<String, Any?>? = null) {
        val (copy, changedKeys) = synchronized(listenerLock) {
            val next = values ?: owner.cachedValues(scope,file)
            val actual = changedPreferenceKeys(knownValues, next) + keys.filterNot(::tracked)
            knownValues = next.toMap()
            listeners.toList() to notifications.receive(actual)
        }
        deliver(copy, changedKeys)
    }
    private fun deliver(copy: List<SharedPreferences.OnSharedPreferenceChangeListener>, changedKeys: Set<String>) {
        if (changedKeys.isNotEmpty() && copy.isNotEmpty()) SyncLocal.main.post {
            // A newer UI edit may have arrived after this receipt was posted to main.
            val currentKeys = synchronized(listenerLock) { notifications.receive(changedKeys) }
            currentKeys.forEach { key -> copy.forEach { listener ->
                if (synchronized(listenerLock) { listener in listeners }) listener.onSharedPreferenceChanged(this,key)
            } }
        }
    }
    private inner class Editor : SharedPreferences.Editor {
        private val changes = linkedMapOf<String, Any?>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { changes[key]=value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { changes[key]=values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { changes[key]=value }
        override fun putLong(key: String, value: Long) = apply { changes[key]=value }
        override fun putFloat(key: String, value: Float) = apply { changes[key]=value }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key]=value }
        override fun remove(key: String) = apply { changes[key]=null }
        override fun clear() = apply { clear=true }
        override fun apply() {
            // Device-only UI toggles keep normal SharedPreferences.apply semantics. They
            // cannot mutate the cloud projection or another workspace.
            if (!clear && changes.keys.none(::tracked)) {
                val edits = effectivePreferenceEdits(fallback.all.filterKeys { !tracked(it) }, changes, false)
                val editor = fallback.edit()
                writeFallback(editor, edits)
                editor.apply()
                if (edits.isNotEmpty()) changed(edits.keys)
            } else check(commit()) { "Local sync storage write failed" }
        }
        private fun writeFallback(editor: SharedPreferences.Editor, edits: Map<String, Any?>) {
            edits.forEach { (key,v) -> when(v) {
                null -> editor.remove(key); is String -> editor.putString(key,v)
                is Int -> editor.putInt(key,v); is Long -> editor.putLong(key,v)
                is Float -> editor.putFloat(key,v); is Boolean -> editor.putBoolean(key,v)
                is Set<*> -> editor.putStringSet(key,v.filterIsInstance<String>().toSet())
                else -> error("Unsupported preference type")
            } }
        }
        override fun commit(): Boolean = synchronized(SyncLocal.lock) {
            check(owner.meta(scope,"adopted")==null) { "Workspace changed; reopen the editor" }
            val requested = changes.filterKeys(::tracked)
            val edits = if (clear || requested.isNotEmpty())
                effectivePreferenceEdits(owner.values(scope,file), requested, clear) else emptyMap()
            val localRequested = changes.filterKeys { !tracked(it) }
            val localEdits = if (clear || localRequested.isNotEmpty())
                effectivePreferenceEdits(fallback.all.filterKeys { !tracked(it) }, localRequested, clear) else emptyMap()
            // Keep projection + durable outbox in one transaction, but skip no-op/fallback-only rebuilds.
            if (edits.isNotEmpty()) owner.edit(scope,file,edits)
            val other = fallback.edit()
            localEdits.forEach { (key,v) -> when(v) {
                null -> other.remove(key); is String -> other.putString(key,v)
                is Int -> other.putInt(key,v); is Long -> other.putLong(key,v)
                is Float -> other.putFloat(key,v); is Boolean -> other.putBoolean(key,v)
                is Set<*> -> other.putStringSet(key,v.filterIsInstance<String>().toSet())
                else -> error("Unsupported preference type")
            } }
            val ok = localEdits.isEmpty() || other.commit()
            if (edits.isNotEmpty() || localEdits.isNotEmpty()) changed(edits.keys + localEdits.keys)
            if (edits.isNotEmpty()) SyncLocal.requestSync()
            ok
        }
    }
}
