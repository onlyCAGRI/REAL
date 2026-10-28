package com.cagri.real.ui.poster

import org.json.JSONArray
import org.json.JSONObject

/**
 * A map poster look: one colour per OpenMapTiles layer group (OpenFreeMap's vector tiles, OpenStreetMap data), no
 * labels. The same style drives the high-res poster snapshot and the pannable full-screen map.
 */
data class MapPosterTemplate(
    val id: String,
    /** Turkish key; shown through t(). */
    val name: String,
    val background: Long,
    val water: Long,
    val park: Long,
    val building: Long,
    val motorway: Long,
    val major: Long,
    val minor: Long,
    val rail: Long,
    val text: Long,
    val dark: Boolean
)

val MAP_POSTER_TEMPLATES = listOf(
    MapPosterTemplate("sage", "Adaçayı", 0xFFEEF3EE, 0xFFB9D5D8, 0xFFDCEBD8, 0xFF9DBFAE, 0xFF2F5C49, 0xFF3E6B57, 0xFF86AB99, 0xFF9DB5AA, 0xFF24493C, false),
    MapPosterTemplate("coast", "Kıyı", 0xFFE6F0EF, 0xFFA3CBCC, 0xFFDCEAE5, 0xFFA3C6C7, 0xFFC2702F, 0xFF4E8A8B, 0xFF93BEC0, 0xFF6E9C9D, 0xFF1F5357, false),
    MapPosterTemplate("neon", "Neon", 0xFF0B0D18, 0xFF0D2236, 0xFF14122A, 0xFFA448E8, 0xFFFF4FA3, 0xFFEE6CC6, 0xFF7440DE, 0xFF2CC3C9, 0xFFFFD000, true),
    MapPosterTemplate("lavender", "Lavanta", 0xFFF8F6F3, 0xFFD4E5EE, 0xFFEEF1EA, 0xFFC9BFCC, 0xFF5F6596, 0xFF8E8FB8, 0xFFB8B6CD, 0xFF9C9CB0, 0xFF57566C, false),
    MapPosterTemplate("gold", "Gece altını", 0xFF0A1730, 0xFF0F2645, 0xFF0D1C36, 0xFF6E5D3D, 0xFFE0B040, 0xFFB98E37, 0xFF5D5434, 0xFF7A6A45, 0xFFDDB65A, true),
    MapPosterTemplate("mint", "Nane", 0xFFE8F0EF, 0xFF8CC4C4, 0xFFDCEAE3, 0xFFA6CACA, 0xFF4A8B8A, 0xFF5D9898, 0xFFA9CCCC, 0xFF7FAAAA, 0xFFB0632A, false),
    MapPosterTemplate("wine", "Bordo", 0xFF1F0A12, 0xFF2A1220, 0xFF250E17, 0xFFF09AB0, 0xFF9A1E42, 0xFF8E1C3C, 0xFF54403F, 0xFFEEDCC8, 0xFFF7DCC5, true),
    MapPosterTemplate("sky", "Gökyüzü", 0xFFEEF6F9, 0xFFB9D8E6, 0xFFDCEBEA, 0xFF7DB5D4, 0xFF1C5A78, 0xFF3C7FA3, 0xFF93C3DA, 0xFF7FA9BF, 0xFF1C5A78, false),
    MapPosterTemplate("espresso", "Espresso", 0xFF1E110C, 0xFF2A1712, 0xFF2E1810, 0xFFE6B573, 0xFFC85A2A, 0xFFB2552B, 0xFF5E4C37, 0xFFE8CFA0, 0xFFFFD58E, true),
    MapPosterTemplate("paper", "Kâğıt", 0xFFF8F6F3, 0xFFE1E4E4, 0xFFEFEDEA, 0xFF2E2E2E, 0xFF8B2A10, 0xFF333333, 0xFF9C9C9C, 0xFF8A8A8A, 0xFF8B2A10, false),
    MapPosterTemplate("peach", "Şeftali", 0xFFF5EDE4, 0xFFA9C6C8, 0xFFEEE4D8, 0xFFD98A78, 0xFFC66A3D, 0xFFD07A52, 0xFFE2B096, 0xFFB79A88, 0xFF8A3E14, false),
    MapPosterTemplate("sand", "Kum", 0xFFE3D7C9, 0xFFCBD3D0, 0xFFDCD1C2, 0xFFA0705F, 0xFF7E4636, 0xFF8E5848, 0xFFB8907F, 0xFF9C8576, 0xFF3F3029, false),
    // The black-and-white pair: ink on white, and white on black.
    MapPosterTemplate("ink", "Mürekkep", 0xFFFFFFFF, 0xFFE8E8E8, 0xFFF3F3F3, 0xFF1A1A1A, 0xFF000000, 0xFF111111, 0xFF707070, 0xFF555555, 0xFF000000, false),
    MapPosterTemplate("night", "Gece", 0xFF000000, 0xFF151515, 0xFF0A0A0A, 0xFFE6E6E6, 0xFFFFFFFF, 0xFFF0F0F0, 0xFF7A7A7A, 0xFFAAAAAA, 0xFFFFFFFF, true),
    MapPosterTemplate("ocean", "Okyanus", 0xFF06202B, 0xFF0B3A4A, 0xFF0A2A33, 0xFF2E8C9A, 0xFF4FE3E0, 0xFF3BB8C0, 0xFF1F5F6A, 0xFF7FD6D6, 0xFF9FF5F0, true),
    MapPosterTemplate("forest", "Orman", 0xFF16241B, 0xFF1E3A3A, 0xFF1D3324, 0xFF7FA07A, 0xFFD9C27A, 0xFFA7C48F, 0xFF4D6B52, 0xFFB5A77A, 0xFFE8DDB0, true),
    MapPosterTemplate("rose", "Gül", 0xFFFBF0F1, 0xFFD6E3EC, 0xFFF3E3E5, 0xFFE3A3B0, 0xFFC2426A, 0xFFD46A88, 0xFFE8B4C0, 0xFFC99AA6, 0xFF9C2F52, false),
    MapPosterTemplate("olive", "Zeytin", 0xFFEFEDE0, 0xFFC9D3C9, 0xFFE3E3CC, 0xFF9C9A6A, 0xFF5E6B2E, 0xFF77823F, 0xFFB0AF85, 0xFF8A8A6A, 0xFF4A5420, false),
    MapPosterTemplate("swiss", "Kırmızı", 0xFFF4EFE6, 0xFFD7DEE0, 0xFFECE6DA, 0xFFC9B9A6, 0xFFD62828, 0xFF2B2B2B, 0xFFA39E95, 0xFF7A7A7A, 0xFFD62828, false),
    MapPosterTemplate("midnight", "Gece mavisi", 0xFF0E1B3D, 0xFF15285A, 0xFF12224A, 0xFF4A5F96, 0xFFFFFFFF, 0xFFC9D3F0, 0xFF4A5C8E, 0xFF9AA8D0, 0xFFFFFFFF, true),
    MapPosterTemplate("pastel", "Pastel", 0xFFF4F1FA, 0xFFCFE7E3, 0xFFE6F2EA, 0xFFC8B6E2, 0xFF8A6FC9, 0xFFA992D8, 0xFFCFC3E8, 0xFFB3A8CC, 0xFF6A4FB0, false),
    MapPosterTemplate("lava", "Lav", 0xFF1A1A1A, 0xFF262626, 0xFF202020, 0xFF8A3324, 0xFFFF5A1F, 0xFFE04A1A, 0xFF5A3A30, 0xFFB0B0B0, 0xFFFF7A3D, true),
    MapPosterTemplate("beach", "Kumsal", 0xFFF3EAD7, 0xFF7FC8C4, 0xFFE8DFC6, 0xFFD9B98A, 0xFFE07A3F, 0xFFC99A5E, 0xFFDCC6A0, 0xFFB8A88A, 0xFF2F7F7A, false),
    MapPosterTemplate("silver", "Gümüş", 0xFFD9DCDF, 0xFFBFC5CB, 0xFFD0D4D7, 0xFF8E949A, 0xFF2E3338, 0xFF4A5056, 0xFF9AA0A6, 0xFF6E747A, 0xFF23272B, false)
)

private fun hex(c: Long) = String.format("#%06X", c and 0xFFFFFF)

/**
 * Line widths are tuned for the poster's own camera (zoom [POSTER_ZOOM] on a 3000 px wide canvas) and grow with
 * zoom on the pannable map.
 */
private fun width(atPoster: Double): JSONArray = JSONArray(
    """["interpolate",["exponential",1.6],["zoom"],9,${atPoster * 0.35},$POSTER_ZOOM,$atPoster,16,${atPoster * 4},19,${atPoster * 12}]"""
)

private fun classFilter(vararg classes: String): JSONArray {
    val f = JSONArray().put("match").put(JSONArray().put("get").put("class")).put(JSONArray(classes.toList()))
    return f.put(true).put(false)
}

fun MapPosterTemplate.styleJson(): String {
    val layers = JSONArray()
    fun fill(id: String, layer: String, color: Long, filter: JSONArray? = null, opacity: Double = 1.0) {
        layers.put(JSONObject().put("id", id).put("type", "fill").put("source", "omt").put("source-layer", layer).apply {
            if (filter != null) put("filter", filter)
        }.put("paint", JSONObject().put("fill-color", hex(color)).put("fill-opacity", opacity).put("fill-antialias", true)))
    }
    fun line(id: String, layer: String, color: Long, w: JSONArray, filter: JSONArray? = null, dash: Boolean = false) {
        layers.put(JSONObject().put("id", id).put("type", "line").put("source", "omt").put("source-layer", layer).apply {
            if (filter != null) put("filter", filter)
        }.put("layout", JSONObject().put("line-cap", "round").put("line-join", "round"))
            .put("paint", JSONObject().put("line-color", hex(color)).put("line-width", w).apply {
                if (dash) put("line-dasharray", JSONArray().put(3).put(2))
            }))
    }
    layers.put(JSONObject().put("id", "bg").put("type", "background").put("paint", JSONObject().put("background-color", hex(background))))
    fill("landcover", "landcover", park, classFilter("wood", "grass", "farmland", "scrub"), 0.55)
    fill("park", "park", park, opacity = 0.8)
    fill("water", "water", water)
    line("waterway", "waterway", water, width(1.2))
    fill("aeroway", "aeroway", park, opacity = 0.7)
    fill("building", "building", building, opacity = if (dark) 0.85 else 0.75)
    line("road-minor", "transportation", minor, width(0.8), classFilter("minor", "service", "track"))
    line("road-secondary", "transportation", minor, width(1.3), classFilter("secondary", "tertiary"))
    line("road-primary", "transportation", major, width(2.0), classFilter("primary", "trunk"))
    line("rail", "transportation", rail, width(0.9), classFilter("rail"), dash = true)
    line("road-motorway", "transportation", motorway, width(3.0), classFilter("motorway"))
    return JSONObject()
        .put("version", 8)
        .put("sources", JSONObject().put("omt", JSONObject().put("type", "vector").put("url", "https://tiles.openfreemap.org/planet")))
        .put("layers", layers)
        .toString()
}

/** Camera for the poster: a 3000 px wide z13 canvas covers ~20-25 km, the whole city with street detail. */
const val POSTER_ZOOM = 13.0

/** The poster's zoom bar: ~90 km (region) to ~3 km (a neighbourhood) across. */
const val POSTER_ZOOM_MIN = 11.0
const val POSTER_ZOOM_MAX = 15.5
