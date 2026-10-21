package com.cagri.real.platform.android.sync

import org.json.JSONArray
import org.json.JSONObject

internal fun canonical(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "null"
    is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
        JSONObject.quote(it) + ":" + canonical(value.get(it))
    }
    is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
    is String -> JSONObject.quote(value)
    is Number -> java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
    else -> value.toString()
}
internal fun JSONObject.stringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else getString(key)
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
internal fun stableUuid(value: String): String = java.util.UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()
