package com.cagri.real.sharedcategories

import android.content.Context
import com.cagri.real.data.CategoryRepository
import com.cagri.real.data.CustomCategory
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.ReminderJson
import com.cagri.real.data.ReminderRepository
import com.cagri.real.notifications.ReminderScheduler
import com.cagri.real.platform.android.auth.AndroidBackend
import com.cagri.real.platform.android.sync.SyncLocal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Ortak kategori on this phone. Shared cards are ordinary [ReminderItem]s (so lists, detail, reminders and alarms all
 * work) tagged with [ReminderItem.sharedCardId]; they never enter personal sync. This engine pulls the category's
 * change feed into them and pushes local edits back with revision checks (the server copy wins a conflict).
 *
 * Someone else's category lives here as a mirror [CustomCategory] ([CustomCategory.isSharedMirror]).
 */
object SharedCategorySync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var pending: Job? = null
    /** True while this engine writes cards, so its own writes do not schedule another round. */
    @Volatile var writing = false
        private set

    /** Runs a sync shortly (edits in a row collapse into one). */
    fun request(context: Context, delayMs: Long = 1500) {
        if (writing) return
        val app = context.applicationContext
        synchronized(this) {
            pending?.cancel()
            pending = scope.launch {
                delay(delayMs)
                scope.launch { runCatching { syncNow(app) } }
            }
        }
    }

    private fun me(): String? = AndroidBackend.auth.currentUser()?.takeIf { !it.isAnonymous }?.id

    // ---------------------------------------------------------------- state (per account, device-local)
    private class State(private val prefs: android.content.SharedPreferences, private val key: String) {
        val obj: JSONObject = runCatching { JSONObject(prefs.getString(key, null) ?: "{}") }.getOrDefault(JSONObject())
        var cursor: Long
            get() = obj.optLong("cursor", 0L)
            set(v) { obj.put("cursor", v) }
        private val cards get() = obj.optJSONObject("cards") ?: JSONObject().also { obj.put("cards", it) }
        val blobs get() = obj.optJSONObject("blobs") ?: JSONObject().also { obj.put("blobs", it) }
        fun card(id: String): JSONObject? = cards.optJSONObject(id)
        fun cardIds(): List<String> = cards.keys().asSequence().toList()
        fun setCard(id: String, localId: Long, revision: Int, hash: String) {
            cards.put(id, JSONObject().put("localId", localId).put("revision", revision).put("hash", hash))
        }
        fun removeCard(id: String) { cards.remove(id) }
        fun save() { prefs.edit().putString(key, obj.toString()).apply() }
        fun clear() { prefs.edit().remove(key).apply() }
    }
    private fun prefs(context: Context, uid: String) = context.getSharedPreferences(
        "real_shared_category_sync_" + sha(uid).take(16), Context.MODE_PRIVATE)
    private fun state(context: Context, uid: String, sharedId: String) = State(prefs(context, uid), "c_$sharedId")

    private fun sha(v: String) = MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun filesDir(context: Context, sharedId: String) = File(context.filesDir, "shared_categories/$sharedId")

    /** Same id for the same shared card on every device; far above timestamp ids so it never meets a personal card. */
    fun localIdFor(cardId: String): Long {
        val u = UUID.fromString(cardId)
        return 4_000_000_000_000_000L + Math.floorMod(u.mostSignificantBits xor u.leastSignificantBits, 1_000_000_000_000_000L)
    }

    // ---------------------------------------------------------------- card <-> payload
    private val localOnly = listOf("id", "customCategoryId", "notifiedAlertAt", "sentBy", "shareOrigin",
        "sharedCardId", "sharedAuthorUid", "sharedAuthorName")

    private fun encode(item: ReminderItem): JSONObject = JSONArray(ReminderJson.encode(listOf(item))).getJSONObject(0)

    /**
     * The server payload of a local card. Files become blob ids; with [api] null a file that has no blob yet makes this
     * return null (= the card has changes to upload).
     */
    private suspend fun outgoing(item: ReminderItem, state: State, api: SharedCategoryCardsApi?, sharedId: String): JSONObject? {
        val o = encode(item)
        localOnly.forEach { o.remove(it) }
        suspend fun file(obj: JSONObject, pathKey: String, kind: String, mime: String): Boolean {
            val path = obj.optString(pathKey).takeIf { it.isNotBlank() && it != "null" }
                ?: obj.optString("uri").takeIf { it.isNotBlank() && it != "null" }
            obj.remove(pathKey); obj.remove("uri")
            if (path == null) return true
            if (pathKey == "imageUri" && path.startsWith("https://")) { obj.put(pathKey, path); return true }
            val known = state.blobs.optString(path).takeIf { it.isNotBlank() }
            val blob = known ?: run {
                val f = File(path)
                if (api == null) return f.isFile.not() // a missing file is dropped; a new one still needs uploading
                if (!f.isFile) return true
                api.upload(sharedId, f, kind, mime).also { state.blobs.put(path, it) }
            }
            obj.put("blobId", blob)
            return true
        }
        o.optJSONArray("attachments")?.let { a ->
            for (i in 0 until a.length()) {
                val att = a.getJSONObject(i)
                if (att.optString("type") == "TEXT_SNIPPET") { att.remove("localPath"); att.remove("uri"); continue }
                val mime = att.optString("mimeType").takeIf { it.isNotBlank() && it != "null" } ?: "application/octet-stream"
                val kind = if (att.optString("type") == "IMAGE") "IMAGE" else if (mime == "application/pdf") "PDF" else "FILE"
                if (!file(att, "localPath", kind, mime)) return null
            }
        }
        o.optJSONArray("voiceRecordings")?.let { a ->
            for (i in 0 until a.length()) if (!file(a.getJSONObject(i), "localPath", "VOICE", "audio/mp4")) return null
        }
        o.optJSONObject("cardStack")?.optJSONArray("cards")?.let { a ->
            for (i in 0 until a.length()) if (!file(a.getJSONObject(i), "imageUri", "IMAGE", "image/jpeg")) return null
        }
        return o
    }
    private suspend fun hashOf(item: ReminderItem, state: State, sharedId: String): String? =
        outgoing(item, state, null, sharedId)?.let { sha(it.toString()) }

    /** A server card as a local card in [localCategoryId]; its files are downloaded into the app's own storage. */
    private suspend fun incoming(context: Context, api: SharedCategoryCardsApi, sharedId: String, card: JSONObject,
        localId: Long, localCategoryId: Long, existing: ReminderItem?, state: State): ReminderItem {
        val p = JSONObject(card.getJSONObject("payload").toString())
        val dir = filesDir(context, sharedId)
        // A file this phone already has for that blob (its own upload, or an adopted card) is used as it is.
        val known = HashMap<String, String>().apply {
            state.blobs.keys().forEach { path -> state.blobs.optString(path).takeIf { it.isNotBlank() }?.let { put(it, path) } }
        }
        suspend fun file(obj: JSONObject, pathKey: String) {
            val blob = obj.optString("blobId").takeIf { it.isNotBlank() } ?: return
            known[blob]?.takeIf { File(it).isFile }?.let { path -> obj.put(pathKey, path); obj.remove("blobId"); return }
            val target = File(dir, blob)
            api.download(sharedId, blob, target)
            obj.put(pathKey, target.absolutePath); obj.remove("blobId")
            state.blobs.put(target.absolutePath, blob)
        }
        p.optJSONArray("attachments")?.let { a -> for (i in 0 until a.length()) file(a.getJSONObject(i), "localPath") }
        p.optJSONArray("voiceRecordings")?.let { a -> for (i in 0 until a.length()) file(a.getJSONObject(i), "localPath") }
        p.optJSONObject("cardStack")?.optJSONArray("cards")?.let { a -> for (i in 0 until a.length()) file(a.getJSONObject(i), "imageUri") }
        p.put("id", localId).put("customCategoryId", localCategoryId)
            .put("sharedCardId", card.getString("id"))
            .put("sharedAuthorUid", card.optString("authorUid"))
            .put("sharedAuthorName", card.optString("authorName"))
        if (!p.has("createdAt")) p.put("createdAt", card.optLong("createdAt", System.currentTimeMillis()))
        // Delivery is device-local: keep "already notified" when the time did not change.
        if (existing != null && existing.alertAt == p.optLong("alertAt", -1L).takeIf { it > 0 }) {
            existing.notifiedAlertAt?.let { p.put("notifiedAlertAt", it) }
        }
        return ReminderJson.decode(JSONArray().put(p).toString()).single()
    }

    // ---------------------------------------------------------------- repository writes
    private fun <T> write(context: Context, change: (List<ReminderItem>) -> List<ReminderItem>): Unit =
        synchronized(ReminderRepository.mutationLock) {
            writing = true
            try {
                val repo = ReminderRepository(context)
                val before = repo.load()
                val after = change(before)
                if (after != before) { repo.restore(after); ReminderRepository.notifyChanged() }
            } finally { writing = false }
        }
    private fun upsert(context: Context, item: ReminderItem) = write<Unit>(context) { list ->
        if (list.any { it.id == item.id }) list.map { if (it.id == item.id) item else it } else list + item
    }
    private fun remove(context: Context, id: Long) = write<Unit>(context) { list -> list.filterNot { it.id == id } }

    // ---------------------------------------------------------------- sync
    suspend fun syncNow(context: Context) = mutex.withLock {
        SyncLocal.awaitInitialized()
        val uid = me() ?: return@withLock
        val api = SharedCategoryCardsApi(uid)
        val summaries = api.list()
        val shared = summaries.filter { it.collab }
        val categories = CategoryRepository(context)
        val sharedIds = shared.map { it.id }.toSet()

        // Mirrors of other people's categories: create / refresh, and drop the ones we no longer belong to.
        for (s in shared.filter { !it.isOwner }) ensureMirror(context, categories, s)
        for (gone in categories.load().filter { it.isSharedMirror && it.sharedId !in sharedIds }) dropMirror(context, uid, gone)
        // Own categories released (or deleted) elsewhere: their cards come home.
        for (own in categories.load().filter { it.sharedRole == "owner" && it.sharedId !in sharedIds }) bringHome(context, uid, own)
        // Own categories shared from another of this person's devices: mark them here too.
        for (s in shared.filter { it.isOwner }) {
            val key = s.category.optLong("id"); val gen = s.category.optLong("createdAt")
            val local = categories.load().firstOrNull { it.id == key && it.createdAt == gen }
            if (local != null && local.sharedId != s.id) saveCategory(categories, local.copy(sharedId = s.id, sharedRole = "owner"))
        }

        var changed = false
        for (s in shared) {
            val local = categories.load().firstOrNull { it.sharedId == s.id } ?: continue
            try {
                changed = syncCategory(context, api, uid, local, s.access) || changed
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { android.util.Log.w("Real", "Shared category sync failed", e) }
        }
        // Cards left behind by a shared category that no longer exists here (e.g. the owner deleted it but kept the
        // cards): they are personal cards now.
        val sharedLocal = categories.load().filter { it.sharedId != null }.map { it.id }.toSet()
        write<Unit>(context) { list -> list.map { if (it.sharedCardId != null && it.customCategoryId !in sharedLocal)
            it.copy(sharedCardId = null, sharedAuthorUid = null, sharedAuthorName = null) else it } }
        if (changed) runCatching { ReminderScheduler.reconcile(context, ReminderRepository(context)) }
    }

    private fun saveCategory(repo: CategoryRepository, c: CustomCategory) {
        synchronized(CategoryRepository.mutationLock) {
            val list = repo.load()
            repo.restore(if (list.any { it.id == c.id }) list.map { if (it.id == c.id) c else it } else list + c)
        }
        // The screens reload categories together with cards.
        ReminderRepository.notifyChanged()
    }

    private fun ensureMirror(context: Context, repo: CategoryRepository, s: SharedCategorySummary) {
        val c = s.category
        val existing = repo.load().firstOrNull { it.sharedId == s.id }
        val base = existing ?: CustomCategory(
            id = (repo.load().maxOfOrNull { it.id } ?: 0L).coerceAtLeast(System.currentTimeMillis()) + 1,
            name = c.optString("name"), iconKey = c.optString("iconKey", "NONE"), accentStart = c.optInt("accentStart"))
        val mirror = base.copy(
            name = c.optString("name", base.name), iconKey = c.optString("iconKey", base.iconKey),
            accentStart = c.optInt("accentStart", base.accentStart),
            accentEnd = if (c.isNull("accentEnd")) null else c.optInt("accentEnd"),
            useDarkText = c.optBoolean("useDarkText"),
            gradientBlendFraction = if (c.isNull("gradientBlendFraction")) null else c.optDouble("gradientBlendFraction").toFloat(),
            customSolidColorArgb = if (c.isNull("customSolidColorArgb")) null else c.optInt("customSolidColorArgb"),
            sharedId = s.id, sharedRole = s.access
        )
        if (mirror != existing) saveCategory(repo, mirror)
    }

    private fun dropMirror(context: Context, uid: String, mirror: CustomCategory) {
        write<Unit>(context) { list -> list.filterNot { it.customCategoryId == mirror.id && it.sharedCardId != null } }
        synchronized(CategoryRepository.mutationLock) {
            CategoryRepository(context).let { repo -> repo.restore(repo.load().filterNot { it.id == mirror.id }) }
        }
        ReminderRepository.notifyChanged()
        mirror.sharedId?.let { state(context, uid, it).clear(); filesDir(context, it).deleteRecursively() }
    }

    /** The category is private again: its shared cards become personal cards (new ids, so personal sync adds them). */
    private fun bringHome(context: Context, uid: String, own: CustomCategory) {
        var next = System.currentTimeMillis()
        write<Unit>(context) { list ->
            val used = list.map { it.id }.toMutableSet()
            list.map { item ->
                if (item.customCategoryId != own.id || item.sharedCardId == null) item
                else {
                    while (next in used) next++
                    used += next
                    item.copy(id = next, sharedCardId = null, sharedAuthorUid = null, sharedAuthorName = null)
                }
            }
        }
        val repo = CategoryRepository(context)
        saveCategory(repo, own.copy(sharedId = null, sharedRole = null, sharedOwnerName = null))
        own.sharedId?.let { state(context, uid, it).clear() }
    }

    /** Pull then push one category. True when local cards changed. */
    private suspend fun syncCategory(context: Context, api: SharedCategoryCardsApi, uid: String, category: CustomCategory, access: String): Boolean {
        val sharedId = category.sharedId ?: return false
        val st = state(context, uid, sharedId)
        var changed = false
        val readOnly = access == "view"

        // Pull.
        while (true) {
            val page = api.cards(sharedId, st.cursor)
            for (card in page.cards) {
                val cardId = card.getString("id")
                val entry = st.card(cardId)
                val localId = entry?.optLong("localId") ?: localIdFor(cardId)
                val current = ReminderRepository(context).load().firstOrNull { it.id == localId || it.sharedCardId == cardId }
                if (card.optBoolean("deleted")) {
                    if (current != null) { remove(context, current.id); changed = true }
                    st.removeCard(cardId); continue
                }
                if (current != null && entry != null && entry.optInt("revision") == card.getInt("revision")) continue // nothing new
                val dirty = !readOnly && current != null && entry != null && hashOf(current, st, sharedId) != entry.optString("hash")
                if (dirty) continue // our edit goes up below; the server answers with the winner
                val item = incoming(context, api, sharedId, card, current?.id ?: localId, category.id, current, st)
                upsert(context, item); changed = true
                st.setCard(cardId, item.id, card.getInt("revision"), hashOf(item, st, sharedId).orEmpty())
            }
            st.cursor = page.cursor; st.save()
            if (!page.hasMore) break
        }
        if (readOnly) return changed

        // Push: new cards, edits, deletions.
        val locals = ReminderRepository(context).load().filter { it.customCategoryId == category.id }
        for (item in locals) {
            if (item.sharedCardId == null) {
                val cardId = UUID.randomUUID().toString()
                val payload = outgoing(item, st, api, sharedId) ?: continue
                val r = api.create(sharedId, cardId, payload)
                if (r.status !in 200..299) continue
                val card = r.body.getJSONObject("card")
                val updated = item.copy(sharedCardId = cardId, sharedAuthorUid = card.optString("authorUid"), sharedAuthorName = card.optString("authorName"))
                upsert(context, updated); changed = true
                st.setCard(cardId, updated.id, card.getInt("revision"), hashOf(updated, st, sharedId).orEmpty())
                continue
            }
            val entry = st.card(item.sharedCardId) ?: continue
            val payload = outgoing(item, st, api, sharedId) ?: continue
            val hash = sha(payload.toString())
            if (hash == entry.optString("hash")) continue
            val r = api.update(sharedId, item.sharedCardId, entry.optInt("revision"), payload)
            when (r.status) {
                in 200..299 -> st.setCard(item.sharedCardId, item.id, r.body.getJSONObject("card").getInt("revision"), hash)
                409 -> {
                    val server = r.body.getJSONObject("card")
                    val fresh = incoming(context, api, sharedId, server, item.id, category.id, item, st)
                    upsert(context, fresh); changed = true
                    st.setCard(item.sharedCardId, item.id, server.getInt("revision"), hashOf(fresh, st, sharedId).orEmpty())
                }
                410 -> { remove(context, item.id); st.removeCard(item.sharedCardId); changed = true }
            }
        }
        val localIds = locals.map { it.id }.toSet()
        for (cardId in st.cardIds()) {
            val entry = st.card(cardId) ?: continue
            val localId = entry.optLong("localId")
            if (localId in localIds) continue
            val r = api.delete(sharedId, cardId, entry.optInt("revision"))
            when (r.status) {
                in 200..299, 410 -> {
                    st.removeCard(cardId)
                    // Moved to another category: it is a personal card now.
                    write<Unit>(context) { list -> list.map { if (it.id == localId && it.sharedCardId == cardId)
                        it.copy(sharedCardId = null, sharedAuthorUid = null, sharedAuthorName = null) else it } }
                }
                409 -> {
                    val server = r.body.getJSONObject("card")
                    val fresh = incoming(context, api, sharedId, server, localId, category.id, null, st)
                    upsert(context, fresh); changed = true
                    st.setCard(cardId, localId, server.getInt("revision"), hashOf(fresh, st, sharedId).orEmpty())
                }
            }
        }
        st.save()
        return changed
    }

    // ---------------------------------------------------------------- sharing actions (owner / member)

    /**
     * Owner shares [category] with [personUid]: the first time, the category becomes collaborative and its cards move to
     * the server ([progress] = cards moved so far).
     */
    suspend fun share(context: Context, category: CustomCategory, personUid: String, access: String, progress: (Int) -> Unit) = mutex.withLock {
        val uid = checkNotNull(me()) { "REAL hesabınla giriş yap." }
        val api = SharedCategoryCardsApi(uid)
        var sharedId = category.sharedId
        if (sharedId == null) {
            val item = SharedCategoryApi(context, uid).ensure(category, "")
            sharedId = item.id
            api.collab(sharedId)
            val repo = CategoryRepository(context)
            saveCategory(repo, (repo.load().firstOrNull { it.id == category.id } ?: category).copy(sharedId = sharedId, sharedRole = "owner"))
            val st = state(context, uid, sharedId)
            var after: String? = null
            var moved = 0
            do {
                val (page, next) = api.adopt(sharedId, after)
                for ((sourceKey, card) in page) {
                    val local = ReminderRepository(context).load().firstOrNull { it.id.toString() == sourceKey }
                    val cardId = card.getString("id")
                    val item = if (local != null) {
                        rememberBlobs(local, card.getJSONObject("payload"), st)
                        local.copy(sharedCardId = cardId, sharedAuthorUid = card.optString("authorUid"), sharedAuthorName = card.optString("authorName"))
                    } else incoming(context, api, sharedId, card, localIdFor(cardId), category.id, null, st)
                    upsert(context, item)
                    st.setCard(cardId, item.id, card.getInt("revision"), hashOf(item, st, sharedId).orEmpty())
                    progress(++moved)
                }
                st.save()
                after = next
            } while (after != null)
        }
        api.addMember(sharedId, personUid, access)
        request(context, 0)
    }

    /** Adopted cards keep their local files: remember which server blob each file became, so nothing uploads again. */
    private fun rememberBlobs(local: ReminderItem, payload: JSONObject, st: State) {
        fun pair(paths: List<String?>, arr: JSONArray?, key: String = "blobId") {
            if (arr == null) return
            paths.forEachIndexed { i, path ->
                val blob = arr.optJSONObject(i)?.optString(key)?.takeIf { it.isNotBlank() }
                if (path != null && blob != null) st.blobs.put(path, blob)
            }
        }
        pair(local.attachments.map { it.localPath.takeIf { p -> p.isNotBlank() } }, payload.optJSONArray("attachments"))
        pair(local.voiceRecordings.map { it.localPath.takeIf { p -> p.isNotBlank() } }, payload.optJSONArray("voiceRecordings"))
        pair(local.cardStack?.cards?.map { it.imageUri }.orEmpty(), payload.optJSONObject("cardStack")?.optJSONArray("cards"))
    }

    /** Owner, after the last person left: the cards come back home and the shared state goes away. */
    suspend fun release(context: Context, category: CustomCategory) = mutex.withLock {
        val uid = checkNotNull(me())
        val sharedId = category.sharedId ?: return@withLock
        SharedCategoryCardsApi(uid).release(sharedId)
        bringHome(context, uid, category)
        SyncLocal.requestSync()
    }

    /** A member leaves. With [keepCopy] the category and its cards stay as their own private copy. */
    suspend fun leave(context: Context, category: CustomCategory, keepCopy: Boolean) = mutex.withLock {
        val uid = checkNotNull(me())
        val sharedId = category.sharedId ?: return@withLock
        SharedCategoryCardsApi(uid).removeMember(sharedId, uid)
        if (keepCopy) {
            write<Unit>(context) { list -> list.map { if (it.customCategoryId == category.id && it.sharedCardId != null)
                it.copy(sharedCardId = null, sharedAuthorUid = null, sharedAuthorName = null) else it } }
            saveCategory(CategoryRepository(context), category.copy(sharedId = null, sharedRole = null, sharedOwnerName = null))
            state(context, uid, sharedId).clear()
            SyncLocal.requestSync()
        } else dropMirror(context, uid, category)
    }

    suspend fun members(category: CustomCategory): List<SharedMember> {
        val uid = checkNotNull(me()); val id = category.sharedId ?: return emptyList()
        return SharedCategoryCardsApi(uid).members(id)
    }
    suspend fun setAccess(category: CustomCategory, personUid: String, access: String) {
        SharedCategoryCardsApi(checkNotNull(me())).setAccess(checkNotNull(category.sharedId), personUid, access)
    }
    /** Owner removes someone. Returns true when nobody is left (the caller then releases the category). */
    suspend fun removeMember(category: CustomCategory, personUid: String): Boolean {
        val api = SharedCategoryCardsApi(checkNotNull(me()))
        val id = checkNotNull(category.sharedId)
        api.removeMember(id, personUid)
        return api.members(id).none { !it.isOwner }
    }
    /** Adds someone to an already shared category. */
    suspend fun addMember(category: CustomCategory, personUid: String, access: String) {
        SharedCategoryCardsApi(checkNotNull(me())).addMember(checkNotNull(category.sharedId), personUid, access)
    }
}
