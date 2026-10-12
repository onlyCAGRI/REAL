package com.cagri.real.platform.android.boards

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import com.cagri.real.platform.android.auth.AndroidBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Uses REAL credential providers, existing cloud quota and app-owned files, with bounded transfers. */
class BoardAssets(private val context:Context,private val store:BoardStore,private val uid:String) {
    private val mutex=Mutex()
    fun file(id:String):File {require(UUID_PATTERN.matches(id));
        return File(store.assetsDirectory,"asset_$id")}
    suspend fun import(uri:Uri,gif:Boolean):JSONObject=withContext(Dispatchers.IO) {
        require(uri.scheme=="content")
        // Canonical REAL URI metadata, validation, bounded/atomic copy and model.
        val attachment=com.cagri.real.data.AttachmentStorage.importUri(context,uri,0,
            mimeHint=if(gif)"image/gif" else "image/jpeg",forceImage=true,maxCopyBytes=MAX_BYTES.toLong())
        var mime=attachment.mimeType?:"application/octet-stream"
        val id=attachment.id
        val target=file(id)
        try {
            File(attachment.localPath).copyTo(target,overwrite=false)
            val header=target.inputStream().use { input -> val bytes=ByteArray(12); var n=0; while(n<bytes.size){val count=input.read(bytes,n,bytes.size-n);if(count<=0)break;n+=count};bytes.copyOf(n) }
            mime=com.cagri.real.boards.boardImageMime(header) ?: error("Choose a supported photo or GIF.")
            require(!gif||mime=="image/gif"){"Choose a GIF file."}
            val options=BitmapFactory.Options().apply { inJustDecodeBounds=true };
            BitmapFactory.decodeFile(target.path,options)
            require(options.outWidth in 1..8192&&options.outHeight in 1..8192&&options.outWidth.toLong()*options.outHeight<=20000000){"Image dimensions are too large."}
            JSONObject().put("assetId",id).put("mime",mime).put("aspect",options.outWidth.toDouble()/options.outHeight)
        }catch(e:Exception){target.delete();
            throw e}finally{com.cagri.real.data.AttachmentStorage.deleteAttachmentFile(context,attachment.localPath)}
    }
    /** Transport an existing REAL attachment/voice without changing its native model. */
    suspend fun stageContent(type:com.cagri.real.boards.BoardBlockType,payload:JSONObject):JSONObject = withContext(Dispatchers.IO) {
        val result=JSONObject(payload.toString())
        val key=if(type==com.cagri.real.boards.BoardBlockType.VOICE) "voice" else "attachment"
        val model=result.getJSONObject(key)
        if(model.optString("type")=="TEXT_SNIPPET")return@withContext result
        val source=File(model.getString("localPath"))
        val id=model.getString("id")
        val declaredMime=if(key=="voice")"audio/mp4" else model.optString("mimeType").takeIf{it.isNotBlank()&&it!="null"}?:"application/octet-stream"
        // Generic files keep their canonical MIME metadata; existing R2 transport uses octet-stream.
        val mime=if(model.optString("type")=="FILE"&&declaredMime !in CONTENT_MIMES)"application/octet-stream" else declaredMime
        require(source.isFile&&source.length() in 1..MAX_BYTES.toLong()){ "File is missing or exceeds the 10 MB board transfer limit." }
        require(mime in CONTENT_MIMES){ "This file type is not supported by board storage." }
        val target=file(id)
        check(!target.exists()) {"This content is already stored on the board"}
        try {
            source.copyTo(target,overwrite=false)
            model.put("localPath",target.path).remove("uri")
            result.put("assetId",id).put("mime",mime)
        } catch(e:Exception){target.delete();throw e}
        result
    }
    /** Same native stack/photos, with board-owned asset references for collaboration. */
    suspend fun stageStack(block:com.cagri.real.data.CardStackBlock,previous:JSONObject,removedImages:Set<String>):JSONObject = withContext(Dispatchers.IO) {
        require(block.cards.size in 1..100) {"A stack needs 1–100 cards"}
        val images=org.json.JSONArray()
        val created=mutableListOf<JSONObject>()
        val oldImages=previous.optJSONArray("images")
        val previousImages=if(oldImages==null)emptyMap() else (0 until oldImages.length()).associate {
            val image=oldImages.getJSONObject(it);image.getString("cardId") to image
        }
        try {
            val cards=block.cards.map { card ->
                val path=card.imageUri?.takeIf {it.isNotBlank()}
                if(path==null) {
                    // Missing offline cache is not an explicit user removal.
                    if(card.id !in removedImages)previousImages[card.id]?.let {images.put(JSONObject(it.toString()))}
                    card
                } else {
                    val source=File(path)
                    check(source.isFile) {"Stack photo is missing"}
                    val options=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                    BitmapFactory.decodeFile(path,options)
                    val mime=options.outMimeType
                    require(mime in listOf("image/jpeg","image/png","image/webp")) {"Unsupported stack photo"}
                    require(options.outWidth in 1..8192 && options.outHeight in 1..8192 && options.outWidth.toLong()*options.outHeight<=20000000) {"Stack photo is too large"}
                    val existingId=source.name.removePrefix("asset_")
                    val existing=source.canonicalFile.parentFile==store.assetsDirectory.canonicalFile && UUID_PATTERN.matches(existingId)
                    val staged=if(existing)JSONObject().put("assetId",existingId).put("mime",mime) else {
                        val id=UUID.randomUUID().toString()
                        val model=com.cagri.real.data.ReminderAttachment(id,path,mimeType=mime)
                        val payload=JSONObject().put("attachment",com.cagri.real.data.boards.BoardRealContent.attachmentPayload(model).getJSONObject("attachment"))
                        stageContent(com.cagri.real.boards.BoardBlockType.ATTACHMENT,payload).also {created+=it}
                    }
                    images.put(JSONObject().put("cardId",card.id).put("assetId",staged.getString("assetId")).put("mime",mime))
                    card.copy(imageUri=file(staged.getString("assetId")).path)
                }
            }
            JSONObject().put("stack",com.cagri.real.data.CardStackBlock.encode(block.copy(cards=cards))).put("images",images).also {
                require(it.toString().toByteArray(Charsets.UTF_8).size<=60000) {"Stack exceeds the board payload limit"}
            }
        }catch(e:Exception) {created.forEach(::discardStaged);throw e}
    }
    suspend fun ensureStackUploaded(board:String,payload:JSONObject,client:BoardClient) {
        val images=payload.optJSONArray("images") ?: return
        for(i in 0 until images.length()) {
            val image=images.getJSONObject(i)
            // A remote reference may be ready even when its local cache is absent.
            // Server validates every reference's board ownership and READY state.
            if(file(image.getString("assetId")).isFile)ensureUploaded(board,image,client)
        }
    }
    fun discardStaged(payload:JSONObject) {
        payload.optString("assetId").takeIf{UUID_PATTERN.matches(it)}?.let{file(it).delete()}
    }
    fun open(attachment:com.cagri.real.data.ReminderAttachment) {
        val target=File(attachment.localPath)
        require(target.isFile && target.canonicalFile.parentFile==store.assetsDirectory.canonicalFile){"File is unavailable"}
        val uri=androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.attachments",target)
        // BoardAssets owns application context; the shared opener uses a task flag only here.
        com.cagri.real.data.AttachmentStorage.openContentUri(context,uri,attachment.mimeType,attachment.displayName)
    }
    suspend fun ensureUploaded(board:String,p:JSONObject,client:BoardClient):Unit=withContext(Dispatchers.IO) {
        val id=p.getString("assetId");
        val local=file(id);
        check(local.isFile){"Local image is missing."}
        val r=client.request("boards/$board/assets/$id","POST",JSONObject().put("mime",p.getString("mime")).put("byte_size",local.length()).put("kind",
            if(p.has("voice"))"VOICE" else if(p.optJSONObject("attachment")?.optString("type")=="FILE")"FILE" else "IMAGE"))
        if(r.optString("state",r.optJSONObject("attachment")?.optString("state")?:"")!="READY")exchange(board,id,p.getString("mime"),local.readBytes())
        Unit
    }
    suspend fun cached(board:String,id:String):File=mutex.withLock {
        withContext(Dispatchers.IO){val target=file(id);
            if(!target.isFile){val part=File(target.parentFile,target.name+".part");
                try{part.writeBytes(exchange(board,id,null,null));
                    check(part.renameTo(target))}catch(e:Exception){part.delete();
                    throw e}};
            target}
    }
    private suspend fun exchange(board:String,id:String,mime:String?,bytes:ByteArray?):ByteArray=withContext(Dispatchers.IO) {
        require(UUID_PATTERN.matches(board)&&UUID_PATTERN.matches(id));
        check(AndroidBackend.auth.currentUser()?.id==uid){"Account changed"}
        val headers=AndroidBackend.diagnosticHeaders().toMutableMap()
        headers["Authorization"]="Bearer "+checkNotNull(AndroidBackend.auth.idTokenForUser(uid).getOrThrow()){ "Sign in to load this asset." }
        check(AndroidBackend.auth.currentUser()?.id==uid){"Account changed"}
        val c=URL(AndroidBackend.ORIGIN+"/api/boards/v1/boards/$board/assets/$id/content").openConnection() as HttpURLConnection
        try{c.instanceFollowRedirects=false;
            c.connectTimeout=10000;
            c.readTimeout=60000;
            c.requestMethod=if(bytes==null)"GET" else "PUT";
            headers.forEach { (k,v)->c.setRequestProperty(k,v) }
            if(bytes!=null){require(bytes.size<=MAX_BYTES);
                c.doOutput=true;
                c.setRequestProperty("Content-Type",mime);
                c.setFixedLengthStreamingMode(bytes.size);
                c.outputStream.use { it.write(bytes) }}
            if(c.responseCode !in 200..299)throw BoardHttpFailure(c.responseCode,JSONObject())
            val result=if(bytes!=null)ByteArray(0)else c.inputStream.use { input->val out=java.io.ByteArrayOutputStream();
                val b=ByteArray(65536);
                while(true){val n=input.read(b);
                    if(n<0)break;
                    check(out.size()+n<=16*1024*1024){"Asset is too large."};
                    out.write(b,0,n)};
                out.toByteArray() }
            check(AndroidBackend.auth.currentUser()?.id==uid){"Account changed"};
            result
        }finally{c.disconnect()}
    }
    companion object {
        val CONTENT_MIMES=setOf("image/jpeg","image/png","image/webp","image/gif","audio/mp4","audio/m4a","audio/x-m4a","audio/mpeg","application/pdf","application/octet-stream")
        const val MAX_BYTES=10*1024*1024;
        val UUID_PATTERN=Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")}
}
