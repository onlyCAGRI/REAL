package com.cagri.real.sharedcategories

import com.cagri.real.data.CustomCategory
import com.cagri.real.data.ReminderItem
import org.json.JSONObject

data class SharedCategory(val id: String, val category: CustomCategory, val isOwner: Boolean, val link: String, val revision: Int)
data class SharedCategoryMember(val uid: String, val name: String, val isOwner: Boolean)
data class SharedCategoryGoal(val recordId: String, val card: ReminderItem, val raw: String)
data class SharedCategoryDetail(val item: SharedCategory, val members: List<SharedCategoryMember>, val goals: List<SharedCategoryGoal>)

internal fun decodeSharedCategory(value: JSONObject): SharedCategory {
    val c = value.getJSONObject("category")
    return SharedCategory(value.getString("id"), CustomCategory(
        id = c.getLong("id"), name = c.getString("name"), iconKey = c.optString("iconKey", "WORK"),
        accentStart = c.optInt("accentStart"), accentEnd = if (c.isNull("accentEnd")) null else c.getInt("accentEnd"),
        useDarkText = c.optBoolean("useDarkText"),
        gradientBlendFraction = if (c.isNull("gradientBlendFraction")) null else c.getDouble("gradientBlendFraction").toFloat(),
        customSolidColorArgb = if (c.isNull("customSolidColorArgb")) null else c.getInt("customSolidColorArgb"),
        createdAt = c.getLong("createdAt")
    ), value.getBoolean("isOwner"), value.optString("link"), value.getInt("revision"))
}

fun categoryInviteToken(value: String?): String? = runCatching {
    val u = java.net.URI(value ?: return null)
    val token = u.rawPath?.removePrefix("/") ?: return null
    token.takeIf { u.scheme == "real" && u.host == "category-invite" && u.rawUserInfo == null &&
        u.port == -1 && u.rawQuery == null && u.rawFragment == null && u.rawPath == "/$token" && Regex("[0-9a-f]{64}").matches(it) }
}.getOrNull()
