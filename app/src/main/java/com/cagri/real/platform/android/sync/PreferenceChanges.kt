package com.cagri.real.platform.android.sync

/** Value and presence comparison; removed keys must still notify their listeners. */
internal fun changedPreferenceKeys(before: Map<String, *>, after: Map<String, *>): Set<String> =
    (before.keys + after.keys).filterTo(linkedSetOf()) { key ->
        before.containsKey(key) != after.containsKey(key) || before[key] != after[key]
    }

/** clear() removes old values, then explicit edits are applied, just like SharedPreferences. */
internal fun effectivePreferenceEdits(
    current: Map<String, *>, requested: Map<String, Any?>, clear: Boolean
): Map<String, Any?> {
    val desired = if (clear) linkedMapOf<String, Any?>() else current.toMutableMap()
    requested.forEach { (key, value) -> if (value == null) desired.remove(key) else desired[key] = value }
    return changedPreferenceKeys(current, desired).associateWith { desired[it] }
}
