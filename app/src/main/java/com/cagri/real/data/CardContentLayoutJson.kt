package com.cagri.real.data

import org.json.JSONObject

internal fun decodeCardContentLayout(value: JSONObject?): Map<String, CardBlockPlacement> {
    if (value == null) return emptyMap()
    val result = linkedMapOf<String, CardBlockPlacement>()
    value.keys().asSequence().filter(::isCardLayoutKey).take(100).forEach { key ->
        val p = value.optJSONObject(key) ?: return@forEach
        // "grid" yoksa eski 2 kolonlu kayıt: span 1 = yarım, 2 = tam; column 0/1 -> 0/3.
        val legacy = !p.has("grid")
        val span = if (legacy) (if (p.optInt("span", 2).coerceIn(1, 2) == 1) 3 else 6) else p.optInt("span", CARD_GRID_UNITS)
        val column = if (legacy) p.optInt("column", 0).coerceIn(0, 1) * 3 else p.optInt("column", 0)
        result[key] = CardBlockPlacement(span, column, p.optInt("gapBefore", 12), p.optInt("minHeight", 0),
            if (legacy || p.isNull("top")) null else p.optInt("top", 0),
            !legacy && p.optBoolean("hideActions", false),
            if (key == "text" || key.startsWith("files:")) p.optString("textAlignment", "start") else "start",
            (key == "text" || key.startsWith("files:")) && p.optBoolean("textBold", false),
            (key == "text" || key.startsWith("files:")) && p.optBoolean("textItalic", false)).normalized()
    }
    return result
}

internal fun encodeCardContentLayout(value: Map<String, CardBlockPlacement>): JSONObject = JSONObject().apply {
    normalizeCardLayout(value).forEach { (key, p) ->
        put(key, JSONObject().put("grid", CARD_GRID_UNITS).put("span", p.span).put("column", p.column)
            .put("gapBefore", p.gapBefore).put("minHeight", p.minHeight).apply {
                p.top?.let { put("top", it) }
                if (p.hideActions) put("hideActions", true)
                if (key == "text" || key.startsWith("files:")) {
                    if (p.textAlignment != "start") put("textAlignment", p.textAlignment)
                    if (p.textBold) put("textBold", true)
                    if (p.textItalic) put("textItalic", true)
                }
            })
    }
}
