package com.cagri.real.platform.android.sync

import com.cagri.real.cloud.CloudHttpFailure
import com.cagri.real.network.HttpRequest
import com.cagri.real.network.HttpTransport
import com.cagri.real.platform.android.auth.AndroidBackend
import com.cagri.real.sync.*
import org.json.JSONObject

internal class AndroidSyncRemote(private val http:HttpTransport):SyncRemote {
    private suspend fun call(path:String,method:String="GET",body:String=""):JSONObject {
        val response=http.execute(HttpRequest("${AndroidBackend.ORIGIN}/api/cloud/v1/sync/$path",method,
            mapOf("Content-Type" to "application/json"),body,10000,30000))
        if(response.statusCode==409)throw SyncRevisionConflict()
        if(response.statusCode !in 200..299)throw CloudHttpFailure(response.statusCode)
        return JSONObject(response.body)
    }
    private fun decode(o:JSONObject)=SyncRecord(o.getString("id"),o.getString("kind"),o.getString("key"),
        if(o.isNull("payload"))null else canonical(o.getJSONObject("payload")),o.getLong("revision"),o.optLong("sequence"))
    override suspend fun changes(cursor:Long):SyncPage {
        val o=call("changes?cursor=$cursor")
        return SyncPage(o.getJSONArray("records").objects().map(::decode),o.getLong("cursor"),o.getBoolean("hasMore"))
    }
    override suspend fun get(id:String):SyncRecord=decode(call("records/$id"))
    override suspend fun put(record:SyncRecord):SyncRecord=decode(call("records/${record.id}","PUT",JSONObject()
        .put("schema",1).put("kind",record.kind).put("key",record.key).put("revision",record.revision)
        .put("payload",record.payload?.let(::JSONObject)?:JSONObject.NULL).toString()))
    suspend fun recover(id:String) { call("attachments/$id/recover","POST","{}") }
}
