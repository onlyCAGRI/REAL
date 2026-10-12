package com.cagri.real.platform.android.cloud

import com.cagri.real.cloud.*
import org.json.JSONObject

object AndroidCloudCodec : CloudCodec {
    override fun errorCode(json: String): String? =
        runCatching { JSONObject(json).optString("error").takeIf { it.isNotBlank() } }.getOrNull()
    override fun create(id: String, kind: CloudKind, title: String, completed: Boolean): String =
        JSONObject().put("id", id).put("kind", kind.name).put("title", title).put("completed", completed).toString()
    override fun update(revision: Long, title: String, completed: Boolean): String =
        JSONObject().put("revision", revision).put("title", title).put("completed", completed).toString()
    override fun delete(revision: Long): String = JSONObject().put("revision", revision).toString()
    private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)
    override fun resource(json: String): CloudResource = JSONObject(json).getJSONObject("resource").let {
        CloudResource(it.getString("id"), CloudKind.valueOf(it.getString("kind")), it.getString("title"),
            it.getInt("completed") != 0, it.getLong("revision"), it.getLong("created_at"), it.getLong("updated_at"), it.nullableLong("deleted_at"))
    }
    override fun page(json: String): CloudPage {
        val obj = JSONObject(json)
        val rows = obj.getJSONArray("changes")
        return CloudPage(List(rows.length()) { i -> rows.getJSONObject(i).let {
            CloudChange(it.getLong("seq"), it.getString("resource_id"), it.getString("entity_kind"),
                it.getString("entity_id"), it.getLong("revision"), it.nullableLong("deleted_at"))
        } }, obj.getLong("cursor"), obj.getBoolean("hasMore"))
    }
    override fun reservation(id: String, kind: String, mime: String, byteSize: Long): String =
        JSONObject().put("id", id).put("kind", kind).put("mime", mime).put("byte_size", byteSize).toString()
    override fun attachment(json: String): CloudAttachment = JSONObject(json).getJSONObject("attachment").let {
        CloudAttachment(it.getString("id"), it.getString("resource_id"), it.getString("kind"), it.getString("mime"),
            it.getLong("byte_size"), it.getString("state"), it.getLong("revision"), it.nullableLong("deleted_at"))
    }
}
