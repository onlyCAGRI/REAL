package com.cagri.real.platform.android.boards

import com.cagri.real.platform.android.auth.AndroidBackend
import com.cagri.real.network.HttpRequest
import com.cagri.real.boards.PanoItem
import org.json.JSONObject

class BoardHttpFailure(val status:Int,val result:JSONObject):Exception(when {
    status==503 -> "Pano cloud is unavailable. Local changes are saved."
    status==401||status==403 -> "Sign in again to sync Pano."
    status==404 -> "This board is no longer available to this account."
    status==409 -> "This block changed on another device."
    status==429 -> "Board limit reached."
    else -> "Could not sync Pano (${result.optString("error","network")})."
})
class BoardClient(val uid:String) {
    suspend fun request(path:String,method:String="GET",body:JSONObject=JSONObject()):JSONObject {
        check(AndroidBackend.auth.currentUser()?.id==uid){"Account changed"}
        val r=AndroidBackend.syncTransport().execute(HttpRequest(AndroidBackend.ORIGIN+"/api/boards/v1/"+path,method,mapOf("Content-Type" to "application/json"),if(method=="GET")"" else body.toString(),10000,60000))
        check(AndroidBackend.auth.currentUser()?.id==uid){"Account changed"}
        val result=runCatching { JSONObject(r.body) }.getOrElse { JSONObject() }
        if(r.statusCode !in 200..299)throw BoardHttpFailure(r.statusCode,result)
        return result
    }
}
