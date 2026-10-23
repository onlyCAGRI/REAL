package com.cagri.real.platform.android.sync

import com.cagri.real.i18n.t
import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import com.cagri.real.sync.*
import org.json.JSONObject
import java.util.UUID

internal class SyncDatabase(context: Context) : SQLiteOpenHelper(context,"real_sync_v1.db",null,1) {
    override fun onCreate(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE kv(scope TEXT,file TEXT,key TEXT,value TEXT NOT NULL,PRIMARY KEY(scope,file,key))")
        db.execSQL("CREATE TABLE meta(scope TEXT,key TEXT,value TEXT NOT NULL,PRIMARY KEY(scope,key))")
        db.execSQL("CREATE TABLE records(scope TEXT,id TEXT,kind TEXT,logical_key TEXT,payload TEXT,base TEXT,revision INTEGER NOT NULL DEFAULT 0,generation INTEGER NOT NULL DEFAULT 1,dirty INTEGER NOT NULL DEFAULT 1,PRIMARY KEY(scope,id),UNIQUE(scope,kind,logical_key))")
        db.execSQL("CREATE TABLE conflicts(scope TEXT,id TEXT,base TEXT,local TEXT,remote TEXT,revision INTEGER,PRIMARY KEY(scope,id))")
        db.execSQL("CREATE TABLE conflict_archive(scope TEXT,id TEXT,entry TEXT NOT NULL,created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE blobs(scope TEXT,id TEXT,parent TEXT,path TEXT,kind TEXT,mime TEXT,PRIMARY KEY(scope,id))")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) { error("Explicit migration required") }
    // Published only after a transaction ends. UI readers never take the SQLite lock.
    @Volatile private var preferenceSnapshots: Map<Pair<String, String>, Map<String, Any?>> = emptyMap()
    private val changedSnapshots = linkedSetOf<Pair<String, String>>()
    @Volatile var preferenceRevision = 0L; private set
    fun cachedValues(scope: String, file: String): Map<String, Any?> =
        checkNotNull(preferenceSnapshots[scope to file]) { "Workspace preferences are not ready" }
    fun warmPreferences(scope: String) = synchronized(SyncLocal.lock) {
        preferenceSnapshots = preferenceSnapshots + SyncProjection.files.associate { (scope to it) to values(scope, it).toMap() }
    }
    fun <T> transaction(block:()->T):T = synchronized(SyncLocal.lock) {
        writableDatabase.beginTransaction()
        try { val value=block();writableDatabase.setTransactionSuccessful();value }
        finally {
            writableDatabase.endTransaction()
            if (!writableDatabase.inTransaction() && changedSnapshots.isNotEmpty()) {
                val refresh = changedSnapshots.toList()
                changedSnapshots.clear()
                // Reload even on rollback: an uncommitted projection is never published.
                preferenceSnapshots = preferenceSnapshots + refresh.associate { it to values(it.first, it.second).toMap() }
                preferenceRevision++
            }
        }
    }
    fun meta(scope:String,key:String):String? = readableDatabase.rawQuery("SELECT value FROM meta WHERE scope=? AND key=?",arrayOf(scope,key)).use { if(it.moveToFirst())it.getString(0) else null }
    fun meta(scope:String,key:String,value:String) { writableDatabase.execSQL("INSERT OR REPLACE INTO meta VALUES(?,?,?)",arrayOf<Any?>(scope,key,value)) }
    private fun removeMeta(scope: String, key: String) {
        writableDatabase.execSQL("DELETE FROM meta WHERE scope=? AND key=?", arrayOf(scope, key))
    }
    private fun metadata(scope: String, prefix: String): List<JSONObject> = readableDatabase.rawQuery(
        "SELECT value FROM meta WHERE scope=? AND substr(key,1,?)=?", arrayOf(scope, prefix.length.toString(), prefix)
    ).use { cursor -> buildList { while(cursor.moveToNext()) add(JSONObject(cursor.getString(0))) } }
    private fun recordJson(record: SyncRecord) = JSONObject().put("id",record.id).put("kind",record.kind)
        .put("key",record.key).put("payload",record.payload ?: JSONObject.NULL).put("revision",record.revision).put("sequence",record.sequence)
    private fun decodeRecord(value: JSONObject) = SyncRecord(value.getString("id"),value.getString("kind"),value.getString("key"),
        value.stringOrNull("payload"),value.getLong("revision"),value.optLong("sequence"))
    private fun issueKey(direction: SyncBinaryDirection, id: String) = "sync_issue:${direction.name}:$id"
    private fun issueTitle(record: SyncRecord): String {
        val obj=record.payload?.let(::JSONObject) ?: return "Silinmiş kart"
        val main=obj.optString("text").trim()
        if(main.isNotEmpty()) return main.take(160)
        for(key in listOf("musicBlocks","mediaBlocks","checklistItems")) {
            val first=obj.optJSONArray(key)?.optJSONObject(0) ?: continue
            first.optString("title").ifBlank { first.optString("text") }.trim().takeIf { it.isNotEmpty() }?.let { return it.take(160) }
        }
        return if((obj.optJSONArray("voiceRecordings")?.length() ?: 0)>0) "Ses kaydı kartı"
            else if(obj.optJSONObject("cardStack")!=null) "Card stack"
            else "Ek içeren kart"
    }
    private fun putIssue(scope: String, record: SyncRecord, generation: Long, direction: SyncBinaryDirection, failure: SyncBinaryFailure) {
        meta(scope, issueKey(direction,record.id), JSONObject().put("recordId",record.id).put("key",record.key)
            .put("title",issueTitle(record))
            .put("blobId",failure.blobId).put("direction",direction.name).put("code",failure.code.name)
            .put("automaticRetry",failure.automaticRetry).put("generation",generation).put("token",UUID.randomUUID().toString()).toString())
    }
    private fun decodeIssue(value: JSONObject) = SyncBinaryIssue(value.getString("recordId"),value.getString("key"),value.getString("title"),
        value.getString("blobId"),SyncBinaryDirection.valueOf(value.getString("direction")),SyncBinaryCode.valueOf(value.getString("code")),
        value.getBoolean("automaticRetry"),value.getLong("generation"),value.getString("token"))
    fun issues(scope: String): List<SyncBinaryIssue> = metadata(scope,"sync_issue:").map(::decodeIssue).filter {
        it.direction==SyncBinaryDirection.DOWNLOAD || row(scope,it.recordId)?.generation==it.generation
    }
    private fun rejectKey(id: String) = "sync_reject:$id"
    /** Refused records whose local copy has not changed since (a new edit sends it again by itself). */
    fun rejected(scope: String): List<SyncRejectedRecord> = metadata(scope,"sync_reject:").mapNotNull { v ->
        val row=row(scope,v.getString("recordId")) ?: return@mapNotNull null
        if(!row.dirty || row.generation!=v.getLong("generation")) return@mapNotNull null
        SyncRejectedRecord(row.record.id,row.record.kind,row.record.key,v.optString("title"),v.getInt("status"),
            v.stringOrNull("code"),row.generation,row.base!=null)
    }
    fun health(scope: String) = SyncHealthSnapshot(scope,status(scope),issues(scope),conflicts(scope),meta(scope,"cloud_paused")=="1",rejected(scope))
    /** "Tekrar dene": the refused record goes up again on the next run. */
    fun retryRejected(scope: String, id: String) = transaction { removeMeta(scope,rejectKey(id)) }
    /** "Hesaptakini kullan": this device takes the account's copy back (none there: the record is removed here). */
    fun useCloudForRejected(scope: String, id: String) = transaction {
        val row=checkNotNull(row(scope,id)) { t("Durum değişti; yeniden yükle.") }
        writableDatabase.execSQL("UPDATE records SET payload=?,generation=generation+1,dirty=0 WHERE scope=? AND id=?",arrayOf<Any?>(row.base,scope,id))
        SyncProjection.applyRecord(this,scope,row.record.kind,row.record.key,row.base)
        removeMeta(scope,rejectKey(id))
    }
    fun retryIssue(scope: String, token: String) = transaction {
        val issue=issues(scope).firstOrNull { it.token==token } ?: error(t("Durum değişti; yeniden yükle."))
        if(issue.direction==SyncBinaryDirection.UPLOAD) {
            // Explicitly selected card only. Never clear other cards'/accounts' failure markers.
            val record=checkNotNull(row(scope,issue.recordId)).record
            check(issue.blobId in record.payload?.let(::JSONObject)?.let(::blobIds).orEmpty()) { t("Ek değişti; yeniden yükle.") }
            removeMeta(scope,"blob_failed:${issue.blobId}")
            blob(scope,issue.blobId)?.path?.let { removeMeta(scope,"path_failed:$it") }
            removeMeta(scope,issueKey(issue.direction,issue.recordId))
        } else {
            val key=issueKey(issue.direction,issue.recordId)
            val value=JSONObject(checkNotNull(meta(scope,key))).put("automaticRetry",true).put("token",UUID.randomUUID().toString())
            meta(scope,key,value.toString())
        }
    }
    fun values(scope:String,file:String):MutableMap<String,Any?> {
        val result=linkedMapOf<String,Any?>()
        readableDatabase.rawQuery("SELECT key,value FROM kv WHERE scope=? AND file=?",arrayOf(scope,file)).use { c->
            while(c.moveToNext()) {
                val v=JSONObject(c.getString(1));result[c.getString(0)]=v.get("value")
            }
        };return result
    }
    fun value(scope:String,file:String,key:String):Any? = readableDatabase.rawQuery(
        "SELECT value FROM kv WHERE scope=? AND file=? AND key=?", arrayOf(scope,file,key)
    ).use { if(it.moveToFirst()) JSONObject(it.getString(0)).get("value") else null }
    fun writeValue(scope:String,file:String,key:String,value:Any?) {
        changedSnapshots.add(scope to file)
        if(value==null || value===JSONObject.NULL)writableDatabase.execSQL("DELETE FROM kv WHERE scope=? AND file=? AND key=?",arrayOf<Any?>(scope,file,key))
        else writableDatabase.execSQL("INSERT OR REPLACE INTO kv VALUES(?,?,?,?)",arrayOf<Any?>(scope,file,key,JSONObject().put("value",value).toString()))
    }
    fun edit(scope:String,file:String,changes:Map<String,Any?>)=transaction {
        changes.forEach { (key,v)->writeValue(scope,file,key,v) }
        rebuild(scope,file)
    }
    data class Row(val record:SyncRecord,val base:String?,val generation:Long,val dirty:Boolean)
    fun rows(scope:String):List<Row> = readableDatabase.rawQuery("SELECT id,kind,logical_key,payload,base,revision,generation,dirty FROM records WHERE scope=?",arrayOf(scope)).use { c->
        buildList { while(c.moveToNext())add(Row(SyncRecord(c.getString(0),c.getString(1),c.getString(2),if(c.isNull(3))null else c.getString(3),c.getLong(5)),if(c.isNull(4))null else c.getString(4),c.getLong(6),c.getInt(7)!=0)) }
    }
    fun row(scope:String,id:String):Row? = readableDatabase.rawQuery(
        "SELECT id,kind,logical_key,payload,base,revision,generation,dirty FROM records WHERE scope=? AND id=?",
        arrayOf(scope,id)
    ).use { c -> if(c.moveToFirst()) Row(
        SyncRecord(c.getString(0),c.getString(1),c.getString(2),if(c.isNull(3))null else c.getString(3),c.getLong(5)),
        if(c.isNull(4))null else c.getString(4),c.getLong(6),c.getInt(7)!=0
    ) else null }
    fun rowByLogicalKey(scope:String,kind:String,logicalKey:String):Row? = readableDatabase.rawQuery(
        "SELECT id,kind,logical_key,payload,base,revision,generation,dirty FROM records WHERE scope=? AND kind=? AND logical_key=?",
        arrayOf(scope,kind,logicalKey)
    ).use { c -> if(c.moveToFirst()) Row(
        SyncRecord(c.getString(0),c.getString(1),c.getString(2),if(c.isNull(3))null else c.getString(3),c.getLong(5)),
        if(c.isNull(4))null else c.getString(4),c.getLong(6),c.getInt(7)!=0
    ) else null }
    fun isBlobPermanentlyFailed(scope:String,id:String):Boolean = meta(scope,"blob_failed:$id") == "1"
    fun isPathPermanentlyFailed(scope:String,path:String):Boolean = meta(scope,"path_failed:$path") == "1"
    fun markBlobPermanentlyFailed(scope:String,id:String,path:String?) = transaction {
        meta(scope,"blob_failed:$id","1")
        if(path!=null) meta(scope,"path_failed:$path","1")
    }
    fun rebuild(scope:String,file:String? = null) {
        // Local pending destination files have no cloud projection; never tombstone unrelated records.
        if(file != null && file in SyncProjection.localFiles) return
        val documents=SyncProjection.export(this,scope,file).associateBy { it.kind+"/"+it.key }
        val old=rows(scope).filter { file == null || SyncProjection.ownsDocument(file,it.record.kind,it.record.key) }
            .associateBy { it.record.kind+"/"+it.record.key }
        for((key,doc) in documents) {
            val row=old[key]
            if(row==null) writableDatabase.execSQL("INSERT INTO records(scope,id,kind,logical_key,payload) VALUES(?,?,?,?,?)",arrayOf<Any?>(scope,stableUuid(meta(scope, "backup_epoch")?.let { "REAL/2/$scope/$it/$key" } ?: "REAL/1/$scope/$key"),doc.kind,doc.key,doc.payload))
            else if(row.record.payload!=doc.payload) writableDatabase.execSQL("UPDATE records SET payload=?,generation=generation+1,dirty=1 WHERE scope=? AND id=?",arrayOf<Any?>(doc.payload,scope,row.record.id))
        }
        old.filterKeys { it !in documents }.values.filter { it.record.payload!=null }.forEach {
            writableDatabase.execSQL("UPDATE records SET payload=NULL,generation=generation+1,dirty=1 WHERE scope=? AND id=?",arrayOf<Any?>(scope,it.record.id))
        }
    }
    fun blobForPath(scope:String,parent:String,path:String,kind:String,mime:String):String {
        val found=readableDatabase.rawQuery("SELECT id FROM blobs WHERE scope=? AND parent=? AND path=? ORDER BY rowid DESC LIMIT 1",arrayOf(scope,parent,path)).use { if(it.moveToFirst())it.getString(0) else null }
        if(found!=null)return found
        val id=UUID.randomUUID().toString()
        writableDatabase.execSQL("INSERT INTO blobs VALUES(?,?,?,?,?,?)",arrayOf<Any?>(scope,id,parent,path,kind,mime));return id
    }
    data class Blob(val id:String,val parent:String,val path:String,val kind:String,val mime:String)
    fun blob(scope:String,id:String):Blob? = readableDatabase.rawQuery("SELECT id,parent,path,kind,mime FROM blobs WHERE scope=? AND id=?",arrayOf(scope,id)).use {
        if(it.moveToFirst())Blob(it.getString(0),it.getString(1),it.getString(2),it.getString(3),it.getString(4)) else null
    }
    fun blobPath(scope:String,id:String)=blob(scope,id)?.path
    fun downloaded(scope:String,id:String,parent:String,path:String,kind:String,mime:String)=transaction {
        writableDatabase.execSQL("INSERT OR REPLACE INTO blobs VALUES(?,?,?,?,?,?)",arrayOf<Any?>(scope,id,parent,path,kind,mime))
        // A fully verified new local copy heals only this blob/path's prior read failure.
        removeMeta(scope,"blob_failed:$id"); removeMeta(scope,"path_failed:$path")
        rowByLogicalKey(scope,"CARD",parent)?.record?.id?.let { recordId ->
            val key=issueKey(SyncBinaryDirection.UPLOAD,recordId)
            if(meta(scope,key)?.let { decodeIssue(JSONObject(it)).blobId==id }==true) removeMeta(scope,key)
        }
    }
    fun retryBlob(scope:String,id:String)=transaction {
        val old=checkNotNull(blob(scope,id))
        val next=UUID.randomUUID().toString()
        writableDatabase.execSQL("INSERT INTO blobs VALUES(?,?,?,?,?,?)",arrayOf<Any?>(scope,next,old.parent,old.path,old.kind,old.mime))
        for(row in rows(scope)) {
            val payload=row.record.payload?:continue
            val updated=payload.replace(JSONObject.quote(id),JSONObject.quote(next))
            if(updated!=payload) writableDatabase.execSQL("UPDATE records SET payload=?,generation=generation+1,dirty=1 WHERE scope=? AND id=?",arrayOf<Any?>(updated,scope,row.record.id))
        }
    }
    fun accountFilePaths(scope: String): List<String> = readableDatabase.rawQuery(
        "SELECT DISTINCT path FROM blobs WHERE (scope=? OR scope IN (SELECT scope FROM meta WHERE key='adopted' AND value=?)) AND path IS NOT NULL", arrayOf(scope, scope)
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
    fun eraseScope(scope: String) = transaction {
        // Only this account's rows. Keep a write barrier for stale UI/preferences instances.
        writableDatabase.execSQL("PRAGMA secure_delete=ON")
        val scopes = readableDatabase.rawQuery("SELECT scope FROM meta WHERE key='adopted' AND value=?", arrayOf(scope))
            .use { cursor -> buildList { add(scope); while (cursor.moveToNext()) add(cursor.getString(0)) } }
        for (owned in scopes) {
            for (table in listOf("kv", "records", "conflicts", "conflict_archive", "blobs", "meta"))
                writableDatabase.execSQL("DELETE FROM $table WHERE scope=?", arrayOf(owned))
            meta(owned, "adopted", "deleted")
            meta(owned, "cloud_paused", "1")
            SyncProjection.files.forEach { changedSnapshots.add(owned to it) }
        }
    }
    fun protects(path:String):Boolean=readableDatabase.rawQuery("SELECT 1 FROM blobs WHERE path=? LIMIT 1",arrayOf(path)).use { it.moveToFirst() }
    fun conflict(scope:String,id:String,base:String?,local:String?,remote:String?,revision:Long) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO conflicts VALUES(?,?,?,?,?,?)",arrayOf<Any?>(scope,id,base,local,remote,revision))
    }
    fun hasConflict(scope:String,id:String)=readableDatabase.rawQuery("SELECT 1 FROM conflicts WHERE scope=? AND id=?",arrayOf(scope,id)).use { it.moveToFirst() }
    /** Conflicts whose two sides became the same (e.g. deleted here too) are settled: nothing is left to choose. */
    fun settleAgreedConflicts(scope:String) {
        for(c in conflicts(scope).filter { it.currentLocal==it.remote }) {
            writableDatabase.execSQL("INSERT INTO conflict_archive VALUES(?,?,?,?)",arrayOf<Any?>(scope,c.id,
                JSONObject().put("base",c.base?:JSONObject.NULL).put("savedLocal",c.savedLocal?:JSONObject.NULL)
                    .put("currentLocal",c.currentLocal?:JSONObject.NULL).put("remote",c.remote?:JSONObject.NULL)
                    .put("choice","AGREED").put("revision",c.revision).toString(),System.currentTimeMillis()))
            writableDatabase.execSQL("UPDATE records SET base=?,revision=?,dirty=0 WHERE scope=? AND id=?",arrayOf<Any?>(c.remote,c.revision,scope,c.id))
            writableDatabase.execSQL("DELETE FROM conflicts WHERE scope=? AND id=?",arrayOf<Any?>(scope,c.id))
            removeMeta(scope,issueKey(SyncBinaryDirection.UPLOAD,c.id))
        }
    }
    fun conflicts(scope:String):List<PersistedSyncConflict> {
        return readableDatabase.rawQuery("""SELECT r.id,r.kind,r.logical_key,c.base,c.local,r.payload,c.remote,c.revision,r.generation
            FROM conflicts c JOIN records r ON r.scope=c.scope AND r.id=c.id WHERE c.scope=?""",arrayOf(scope)).use { c->
            buildList { while(c.moveToNext()) {
                fun value(i:Int):String?=if(c.isNull(i))null else c.getString(i)
                add(PersistedSyncConflict(c.getString(0),c.getString(1),c.getString(2),value(3),value(4),value(5),value(6),c.getLong(7),c.getLong(8)))
            } }
        }
    }

    /** Fresh cloud IDs/revisions; keep every local preference/file and conflict archive. */
    fun prepareFreshBackup(scope: String, epoch: String) = transaction {
        if (meta(scope, "backup_epoch") == epoch) return@transaction
        for (conflict in conflicts(scope)) {
            writableDatabase.execSQL("INSERT INTO conflict_archive VALUES(?,?,?,?)", arrayOf<Any?>(scope, conflict.id,
                JSONObject().put("base", conflict.base ?: JSONObject.NULL).put("savedLocal", conflict.savedLocal ?: JSONObject.NULL)
                    .put("currentLocal", conflict.currentLocal ?: JSONObject.NULL).put("remote", conflict.remote ?: JSONObject.NULL)
                    .put("choice", "FRESH_BACKUP_CURRENT_LOCAL").put("revision", conflict.revision).toString(), System.currentTimeMillis()))
        }
        for (table in listOf("records", "conflicts", "blobs")) writableDatabase.execSQL("DELETE FROM $table WHERE scope=?", arrayOf(scope))
        writableDatabase.execSQL("DELETE FROM meta WHERE scope=? AND (key LIKE 'sync_issue:%' OR key LIKE 'sync_download:%' OR key LIKE 'sync_reject:%')",arrayOf(scope))
        // The failure markers are preserved: a broken URI must not suddenly enter a retry loop.
        meta(scope, "backup_epoch", epoch)
        meta(scope, "cursor", "0"); meta(scope, "restored", "0")
        meta(scope, "last_error", ""); meta(scope, "last_success", "0")
        rebuild(scope)
    }

    private fun count(scope:String,table:String,condition:String="1") = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM $table WHERE scope=? AND $condition",arrayOf(scope)).use { it.moveToFirst();it.getInt(0) }
    fun status(scope:String):SyncStatus = SyncStatus(meta(scope,"restored")=="1",count(scope,"records","dirty=1"),count(scope,"conflicts"),
        meta(scope,"last_error")?.takeIf { it.isNotBlank() },meta(scope,"last_success")?.toLongOrNull())
    fun resolve(scope:String,id:String,revision:Long,generation:Long,choice:SyncConflictChoice)=transaction {
        val conflict=conflicts(scope).first { it.id==id }
        val chosen=conflictResolutionPayload(conflict,revision,generation,choice)
        if(choice==SyncConflictChoice.LOCAL_AS_NEW_CARD) {
            check(conflict.kind=="CARD" && conflict.currentLocal!=null) { t("Yalnız yerel kart kopyalanabilir.") }
            val copy=JSONObject(conflict.currentLocal)
            var localId=System.currentTimeMillis()
            while(rowByLogicalKey(scope,"CARD",localId.toString())!=null) localId++
            val key=localId.toString()
            // notifiedAlertAt is device-local: the account refuses the key even as null (LOCAL_ONLY_FIELD).
            copy.put("id",localId).put("createdAt",localId).put("updatedAt",localId).remove("notifiedAlertAt")
            var payload=copy.toString()
            for(id in blobIds(copy)) {
                val old=checkNotNull(blob(scope,id)) { t("Yerel ek bulunamadı; sürümler korundu.") }
                val next=blobForPath(scope,key,old.path,old.kind,old.mime)
                payload=payload.replace(JSONObject.quote(id),JSONObject.quote(next))
            }
            payload=canonical(JSONObject(payload))
            val recordId=stableUuid(meta(scope,"backup_epoch")?.let { "REAL/2/$scope/$it/CARD/$key" } ?: "REAL/1/$scope/CARD/$key")
            writableDatabase.execSQL("INSERT INTO records(scope,id,kind,logical_key,payload) VALUES(?,?,?,?,?)",arrayOf<Any?>(scope,recordId,"CARD",key,payload))
            SyncProjection.applyRecord(this,scope,"CARD",key,payload)
        }
        val archive=JSONObject().put("base",conflict.base?:JSONObject.NULL)
            .put("savedLocal",conflict.savedLocal?:JSONObject.NULL).put("currentLocal",conflict.currentLocal?:JSONObject.NULL)
            .put("remote",conflict.remote?:JSONObject.NULL).put("choice",choice.name).put("revision",revision)
        writableDatabase.execSQL("INSERT INTO conflict_archive VALUES(?,?,?,?)",arrayOf<Any?>(scope,id,archive.toString(),System.currentTimeMillis()))
        writableDatabase.execSQL("UPDATE records SET payload=?,base=?,revision=?,generation=generation+1,dirty=? WHERE scope=? AND id=?",
            arrayOf<Any?>(chosen,conflict.remote,revision,if(chosen==conflict.remote)0 else 1,scope,id))
        SyncProjection.applyRecord(this,scope,conflict.kind,conflict.key,chosen)
        writableDatabase.execSQL("DELETE FROM conflicts WHERE scope=? AND id=?",arrayOf<Any?>(scope,id))
        removeMeta(scope,issueKey(SyncBinaryDirection.UPLOAD,id))
    }
    /** Original preferences are retained untouched. Guest adoption is recorded exactly once. */
    fun adopt(guest:String,target:String)=transaction {
        if(meta(guest,"adopted")!=null)return@transaction
        val existing=rows(target).associateBy { it.record.kind+"/"+it.record.key }
        for(doc in SyncProjection.export(this,guest)) {
            var payload=doc.payload
            // Native paths stay local; each account gets independent attachment reservations.
            val ids=blobIds(JSONObject(payload))
            for(id in ids) {
                val b=checkNotNull(blob(guest,id))
                val next=blobForPath(target,b.parent,b.path,b.kind,b.mime)
                payload=payload.replace(JSONObject.quote(id),JSONObject.quote(next))
            }
            val old=existing[doc.kind+"/"+doc.key]
            if(old==null) {
                val id=stableUuid(meta(target, "backup_epoch")?.let { "REAL/2/$target/$it/${doc.kind}/${doc.key}" } ?: "REAL/1/$target/${doc.kind}/${doc.key}")
                writableDatabase.execSQL("INSERT INTO records(scope,id,kind,logical_key,payload) VALUES(?,?,?,?,?)",arrayOf<Any?>(target,id,doc.kind,doc.key,payload))
                SyncProjection.applyRecord(this,target,doc.kind,doc.key,payload)
            } else if(old.record.payload!=payload) {
                // A colliding guest record is retained in its archive AND as a conflict branch.
                conflict(target,old.record.id,old.base,payload,old.base,old.record.revision)
            }
        }
        for(file in SyncProjection.localFiles) {
            val existingValues=values(target,file)
            values(guest,file).filterKeys { it !in existingValues }.forEach { (key,value)->writeValue(target,file,key,value) }
        }
        meta(guest,"adopted",target)
    }
    fun account(scope:String):SyncStore=object:SyncStore {
        override fun cursor()=synchronized(SyncLocal.lock) { meta(scope,"cursor")?.toLong()?:0L }
        override fun checkpoint(cursor:Long,initialPullComplete:Boolean)=transaction {
            meta(scope,"cursor",maxOf(cursor,this.cursor()).toString())
            if(initialPullComplete)meta(scope,"restored","1")
            settleAgreedConflicts(scope)
        }
        override fun pending():List<PendingRecord> = synchronized(SyncLocal.lock) {
            // Cards copied by "Yereli yeni kart olarak sakla" in the first version carried notifiedAlertAt (refused by the
            // account): strip it once; the new generation sends them again by itself.
            transaction {
                rows(scope).filter { it.dirty && it.record.kind=="CARD" && it.record.payload?.contains("\"notifiedAlertAt\"")==true }.forEach { row ->
                    val clean=JSONObject(row.record.payload!!).apply { remove("notifiedAlertAt") }
                    writableDatabase.execSQL("UPDATE records SET payload=?,generation=generation+1 WHERE scope=? AND id=?",arrayOf<Any?>(canonical(clean),scope,row.record.id))
                    removeMeta(scope,rejectKey(row.record.id))
                }
            }
            if(meta(scope,"restored")!="1") emptyList() else rows(scope).filter { it.dirty && !hasConflict(scope,it.record.id) && meta(scope,"sync_download:${it.record.id}")==null &&
                    // Refused as it is now: wait for a new edit or the person's choice (not a failure every run).
                    meta(scope,rejectKey(it.record.id))?.let { v -> JSONObject(v).getLong("generation")==it.generation } != true &&
                    meta(scope,issueKey(SyncBinaryDirection.UPLOAD,it.record.id))?.let { value ->
                        val issue=decodeIssue(JSONObject(value));issue.generation!=it.generation || issue.automaticRetry
                    } != false }
                .map { PendingRecord(it.record,it.generation) }
        }
        override fun deferDownload(record:SyncRecord,failure:SyncBinaryFailure)=transaction {
            val key="sync_download:${record.id}"
            val prior=meta(scope,key)?.let(::JSONObject)?.let(::decodeRecord)
            if(prior==null || record.revision>=prior.revision) {
                meta(scope,key,recordJson(record).put("lastAttempt",System.currentTimeMillis()).toString())
                putIssue(scope,record,0L,SyncBinaryDirection.DOWNLOAD,failure)
            }
        }
        override fun deferredDownloads():List<SyncRecord> = synchronized(SyncLocal.lock) {
            metadata(scope,"sync_download:").sortedBy { it.optLong("lastAttempt") }.map(::decodeRecord).filter { record ->
                meta(scope,issueKey(SyncBinaryDirection.DOWNLOAD,record.id))?.let { JSONObject(it).getBoolean("automaticRetry") } == true
            }
        }
        override fun blockUpload(pending:PendingRecord,failure:SyncBinaryFailure)=transaction {
            putIssue(scope,pending.record,pending.generation,SyncBinaryDirection.UPLOAD,failure)
        }
        override fun clearUploadIssue(id:String)=transaction {
            removeMeta(scope,issueKey(SyncBinaryDirection.UPLOAD,id)); removeMeta(scope,rejectKey(id))
        }
        override fun remoteDeleted(pending:PendingRecord)=transaction {
            val row=row(scope,pending.record.id) ?: return@transaction
            // Deleted here too: both sides agree, the deletion is done (not a choice for the person).
            if(pending.record.payload==null && row.record.payload==null) {
                writableDatabase.execSQL("UPDATE records SET base=NULL,dirty=0 WHERE scope=? AND id=? AND generation=?",arrayOf<Any?>(scope,pending.record.id,pending.generation))
                return@transaction
            }
            // The account no longer has this card: the person chooses (apply the deletion / keep it as a new card).
            if(!hasConflict(scope,pending.record.id)) conflict(scope,pending.record.id,row.base,row.record.payload,null,row.record.revision)
        }
        override fun rejectUpload(pending:PendingRecord,failure:SyncRecordRejected)=transaction {
            meta(scope,rejectKey(pending.record.id),JSONObject().put("recordId",pending.record.id).put("title",issueTitle(pending.record))
                .put("status",failure.status).put("code",failure.code ?: JSONObject.NULL).put("generation",pending.generation).toString())
        }
        override fun receive(record:SyncRecord,cursor:Long)=transaction {
            val deferred=meta(scope,"sync_download:${record.id}")?.let(::JSONObject)?.let(::decodeRecord)
            if(deferred!=null && deferred.revision>record.revision) return@transaction
            removeMeta(scope,"sync_download:${record.id}")
            removeMeta(scope,issueKey(SyncBinaryDirection.DOWNLOAD,record.id))
            val old=row(scope,record.id)
            if(old==null) {
                writableDatabase.execSQL("INSERT INTO records(scope,id,kind,logical_key,payload,base,revision,dirty) VALUES(?,?,?,?,?,?,?,0)",arrayOf<Any?>(scope,record.id,record.kind,record.key,record.payload,record.payload,record.revision))
                SyncProjection.applyRecord(this@SyncDatabase,scope,record.kind,record.key,record.payload)
            } else if(record.payload==null && old.record.payload!=null && old.record.payload!=old.base && record.revision>=old.record.revision) {
                if(!hasConflict(scope,record.id)) conflict(scope,record.id,old.base,old.record.payload,null,record.revision)
                else writableDatabase.execSQL("UPDATE conflicts SET remote=NULL,revision=? WHERE scope=? AND id=?",arrayOf<Any?>(record.revision,scope,record.id))
                writableDatabase.execSQL("UPDATE records SET base=NULL,revision=? WHERE scope=? AND id=?",arrayOf<Any?>(record.revision,scope,record.id))
            } else if(record.revision>old.record.revision) {
                check(old.record.kind==record.kind && old.record.key==record.key)
                if(hasConflict(scope,record.id)) {
                    writableDatabase.execSQL("UPDATE conflicts SET remote=?,revision=? WHERE scope=? AND id=?",arrayOf<Any?>(record.payload,record.revision,scope,record.id))
                    writableDatabase.execSQL("UPDATE records SET base=?,revision=? WHERE scope=? AND id=?",arrayOf<Any?>(record.payload,record.revision,scope,record.id))
                } else when(val merged=reconcile(old.base,old.record.payload,record.payload)) {
                    is Reconciliation.Conflict -> {
                        conflict(scope,record.id,old.base,old.record.payload,record.payload,record.revision)
                        writableDatabase.execSQL("UPDATE records SET base=?,revision=? WHERE scope=? AND id=?",arrayOf<Any?>(record.payload,record.revision,scope,record.id))
                    }
                    is Reconciliation.Value -> {
                        // Cloud tombstones cannot be resurrected even when local base was null.
                        if(record.payload==null && merged.payload!=null) {
                            conflict(scope,record.id,old.base,old.record.payload,null,record.revision)
                            writableDatabase.execSQL("UPDATE records SET base=NULL,revision=? WHERE scope=? AND id=?",arrayOf<Any?>(record.revision,scope,record.id))
                        } else {
                            writableDatabase.execSQL("UPDATE records SET payload=?,base=?,revision=?,generation=generation+1,dirty=? WHERE scope=? AND id=?",arrayOf<Any?>(merged.payload,record.payload,record.revision,if(merged.payload==record.payload)0 else 1,scope,record.id))
                            SyncProjection.applyRecord(this@SyncDatabase,scope,record.kind,record.key,merged.payload)
                        }
                    }
                }
            }
            // A redownload may replace a stale content URI/path even at the same cloud revision.
            // Re-project the CURRENT local branch, never the remote branch over an edit/conflict.
            if(record.kind=="CARD") row(scope,record.id)?.record?.payload?.let {
                SyncProjection.applyRecord(this@SyncDatabase,scope,"CARD",record.key,it)
            }
            meta(scope,"cursor",maxOf(cursor,this.cursor()).toString())
        }
        override fun current(id:String):SyncRecord?=row(scope,id)?.record
        override fun acknowledge(sent:PendingRecord,accepted:SyncRecord)=transaction {
            val row=row(scope,accepted.id)?:error("Missing durable record")
            writableDatabase.execSQL("UPDATE records SET base=?,revision=?,dirty=? WHERE scope=? AND id=?",arrayOf<Any?>(accepted.payload,accepted.revision,if(row.record.payload==accepted.payload)0 else 1,scope,accepted.id))
        }
    }
}
internal fun blobIds(value:Any?):List<String> = buildList {
    when(value) {
        is JSONObject->{value.stringOrNull("blobId")?.let(::add);value.keys().forEach { addAll(blobIds(value.get(it))) }}
        is org.json.JSONArray->(0 until value.length()).forEach { addAll(blobIds(value.get(it))) }
        else -> Unit
    }
}.distinct()
