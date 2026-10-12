package com.cagri.real.platform.android.boards

import android.content.Context

/** Share local locks across route recreation, without keeping any UI scope or poller alive. */
object BoardRepositories {
    private val accounts=mutableMapOf<String,BoardRepository>()
    @Synchronized fun obtain(context:Context,uid:String,name:String):BoardRepository = accounts.getOrPut(uid){
        val app=context.applicationContext
        val store=BoardStore(app,uid)
        BoardRepository(store,uid,name,BoardAssets(app,store,uid))
    }
}
