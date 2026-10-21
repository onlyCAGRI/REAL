package com.cagri.real.platform.android.sync

import android.content.Context
import android.net.Uri
import com.cagri.real.cloud.*
import com.cagri.real.sync.*
import java.io.File
import org.json.JSONObject

internal class AndroidSyncBinaries(private val context:Context,private val db:SyncDatabase,
    private val scope:String,private val cloud:CloudClient,private val transfer:AttachmentTransfer,
    private val remote:AndroidSyncRemote):SyncBinaries {
    override suspend fun prepareUpload(record:SyncRecord) {
        if(record.kind!="CARD")return
        // Resource title is bounded metadata; the complete card remains in its sync payload.
        // Native content-only cards legitimately have no main text.
        val resourceTitle = if (record.payload == null) "Deleted REAL" else {
            JSONObject(record.payload).optString("text").trim().ifBlank { "REAL Card" }.take(10_000)
        }
        try { cloud.create(record.id,CloudKind.CARD,resourceTitle) }
        catch(e:CloudHttpFailure) { if(e.status!=409)throw e }
        // The account restores a deleted resource of a card it still has (or never received); still deleted here
        // means the card was deleted in the account: a conflict for this card only.
        if(cloud.get(record.id).deletedAt!=null && record.payload!=null) throw SyncRemoteDeleted()
        for(id in record.payload?.let(::JSONObject)?.let(::blobIds).orEmpty()) {
            withBinaryFailure(id) {
            val b=synchronized(SyncLocal.lock) { db.blob(scope,id) }
                ?: throw SyncBinaryFailure(id, SyncBinaryCode.LOCAL_FILE_UNAVAILABLE, false)
            if (synchronized(SyncLocal.lock) { db.isBlobPermanentlyFailed(scope, id) || db.isPathPermanentlyFailed(scope, b.path) })
                throw SyncBinaryFailure(id, SyncBinaryCode.LOCAL_FILE_UNAVAILABLE, false)
            var metadata=try { cloud.attachment(id) } catch(e:CloudHttpFailure) { if(e.status==404)null else throw e }
            if(metadata!=null && metadata.resourceId!=record.id) throw SyncBinaryFailure(id, SyncBinaryCode.INVALID_BINARY, false)
            if(metadata?.state=="READY" && metadata.deletedAt==null)return@withBinaryFailure
            if(metadata?.state=="UPLOADING") {
                remote.recover(id)
                metadata=cloud.attachment(id)
                if(metadata.state=="READY" && metadata.deletedAt==null)return@withBinaryFailure
            }
            if(metadata!=null && (metadata.deletedAt!=null || metadata.state in setOf("FAILED","PURGED"))) {
                synchronized(SyncLocal.lock) { db.retryBlob(scope,id) }
                throw SyncBinaryFailure(id, SyncBinaryCode.TRANSFER_PENDING, true)
            }
            val bytes=try {
                readBounded(b.path, id)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (e: Exception) {
                if (isPermanentLocalFailure(e)) {
                    synchronized(SyncLocal.lock) {
                        db.markBlobPermanentlyFailed(scope, id, b.path)
                    }
                    throw SyncBinaryFailure(id, SyncBinaryCode.LOCAL_FILE_UNAVAILABLE, false, e)
                } else throw e
            }
            val wireMime=if(b.mime in setOf("image/jpeg","image/png","image/webp","audio/mp4","audio/m4a","audio/x-m4a","audio/mpeg","application/pdf","application/octet-stream")) b.mime else "application/octet-stream"
            if(metadata==null) {
                try { metadata=cloud.reserveAttachment(record.id,id,b.kind,wireMime,bytes.size.toLong()) }
                catch(e:CloudHttpFailure) { if(e.status!=409)throw e;metadata=cloud.attachment(id) }
            }
            if (metadata.resourceId!=record.id || metadata.byteSize!=bytes.size.toLong())
                throw SyncBinaryFailure(id, SyncBinaryCode.INVALID_BINARY, false)
            if(metadata.state!="READY")transfer.upload(id,wireMime,bytes)
            val ready=cloud.attachment(id)
            if (ready.state!="READY" || ready.deletedAt!=null)
                throw SyncBinaryFailure(id, SyncBinaryCode.TRANSFER_PENDING, true)
            }
        }
    }
    override suspend fun prepareDownload(record:SyncRecord) {
        if(record.kind!="CARD" || record.payload==null)return
        for(id in blobIds(JSONObject(record.payload))) {
            withBinaryFailure(id) {
            val existing=synchronized(SyncLocal.lock) { db.blobPath(scope,id) }
            val metadata=cloud.attachment(id)
            if (metadata.resourceId!=record.id) throw SyncBinaryFailure(id, SyncBinaryCode.INVALID_BINARY, false)
            if (metadata.state!="READY" || metadata.deletedAt!=null)
                throw SyncBinaryFailure(id, SyncBinaryCode.REMOTE_FILE_UNAVAILABLE, metadata.state=="UPLOADING" && metadata.deletedAt==null)
            if(existing!=null && !existing.startsWith("content://") && File(existing).isFile &&
                File(existing).length()==metadata.byteSize && metadata.byteSize>0L)return@withBinaryFailure
            val bytes=transfer.download(id)
            if (bytes.size.toLong()!=metadata.byteSize || bytes.isEmpty())
                throw SyncBinaryFailure(id, SyncBinaryCode.INVALID_BINARY, false)
            // Use existing attachment FileProvider root; names are server UUIDs, never remote filenames.
            val dir=File(context.filesDir,"reminder_attachments").apply { mkdirs() }
            val ext=when(metadata.mime) {"audio/mp4"->"m4a";"audio/mpeg"->"mp3";"application/pdf"->"pdf";"image/png"->"png";else->"bin"}
            val target=File(dir,"cloud_${stableUuid(scope)}_$id.$ext")
            val temp=File(dir,"cloud_${stableUuid(scope)}_$id.part")
            java.io.FileOutputStream(temp).use { it.write(bytes);it.fd.sync() }
            if (!temp.renameTo(target)) throw java.io.IOException("Binary restore failed")
            db.downloaded(scope,id,record.key,target.absolutePath,metadata.kind,metadata.mime)
            }
        }
    }
    private fun readBounded(path:String, id:String):ByteArray {
        val input=if(path.startsWith("content://")) (context.contentResolver.openInputStream(Uri.parse(path)) ?: throw java.io.FileNotFoundException("Unreadable attachment"))
            else File(if(path.startsWith("file://"))Uri.parse(path).path!! else path).inputStream()
        return input.use {
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) { val count=it.read(buffer);if(count<0)break;if(out.size()+count>16*1024*1024) throw SyncBinaryFailure(id, SyncBinaryCode.FILE_TOO_LARGE, false);out.write(buffer,0,count) }
            out.toByteArray().also { if(it.isEmpty()) throw SyncBinaryFailure(id, SyncBinaryCode.EMPTY_FILE, false) }
        }
    }
    private suspend fun withBinaryFailure(id: String, work: suspend () -> Unit) {
        try { work() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: SyncBinaryFailure) { throw failure }
        catch (failure: CloudHttpFailure) {
            // Session/security barriers must stop sync, not be presented as a broken file.
            when {
                failure.status in setOf(401,403,423) -> throw failure
                failure.status==413 -> throw SyncBinaryFailure(id, SyncBinaryCode.FILE_TOO_LARGE, false, failure)
                failure.status in setOf(404,410) -> throw SyncBinaryFailure(id, SyncBinaryCode.REMOTE_FILE_UNAVAILABLE, false, failure)
                failure.status in setOf(409,429) || failure.status>=500 -> throw SyncBinaryFailure(id, SyncBinaryCode.TRANSFER_PENDING, true, failure)
                else -> throw failure
            }
        }
        catch (failure: java.io.IOException) { throw SyncBinaryFailure(id, SyncBinaryCode.IO_FAILURE, true, failure) }
    }
    private fun isPermanentLocalFailure(e:Throwable):Boolean {
        if(e is SecurityException || e is java.io.FileNotFoundException) return true
        val msg=e.message.orEmpty()
        return msg.contains("Permission Denial", ignoreCase = true) ||
               msg.contains("MediaDocumentsProvider", ignoreCase = true) ||
               msg.contains("SecurityException", ignoreCase = true) ||
               msg.contains("does not exist", ignoreCase = true)
    }
}
