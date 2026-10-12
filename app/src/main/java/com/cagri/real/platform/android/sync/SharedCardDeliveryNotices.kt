package com.cagri.real.platform.android.sync

import android.content.Context
import com.cagri.real.platform.android.auth.AndroidBackend

/** Device-local receipt notifications. No alarm/pinning state or Compose state is mutated. */
internal fun notifyReceivedSharedCards(context: Context, database: SyncDatabase, account: String, uid: String) {
    val notices=synchronized(SyncLocal.lock) {
        val raw=database.values(account,SyncProjection.CARDS)["reminders"] as String?
        raw?.let { org.json.JSONArray(it) }?.objects().orEmpty().filter {
            val origin=it.stringOrNull("shareOrigin")
            origin!=null && !it.stringOrNull("sentBy").isNullOrBlank() && database.meta(account,"shared_notice/$origin")!="1"
        }.take(5)
    }
    SyncLocal.main.post {
        if(AndroidBackend.auth.currentUser()?.id==uid) for(card in notices) {
            val shown=runCatching { com.cagri.real.platform.android.share.SharedCardNotifications.show(
                context,uid,card.getLong("id"),card.getString("sentBy"),card.getString("text")) }.getOrDefault(false)
            if(shown) synchronized(SyncLocal.lock) { database.meta(account,"shared_notice/${card.getString("shareOrigin")}","1") }
        }
    }
}
