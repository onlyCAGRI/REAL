package com.cagri.real

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.cagri.real.boards.BoardBlockType
import com.cagri.real.boards.BoardTransform
import com.cagri.real.boards.PanoBoard
import com.cagri.real.data.*
import com.cagri.real.data.boards.BoardRealContent
import com.cagri.real.notifications.ReminderNotifications
import com.cagri.real.notifications.ReminderScheduler
import com.cagri.real.platform.android.boards.BoardRepositories
import com.cagri.real.platform.android.content.ShareShortcuts
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.components.*
import com.cagri.real.ui.theme.RealTheme
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

/**
 * Share → REAL. A sheet over the app you shared from: make a new card, add to an existing card, or put it on a Pano
 * board. Afterwards you are back in that app ("Eklendi · Aç" offers to open REAL). A share that arrives while the
 * sheet is open waits its turn instead of being dropped.
 */
class AttachmentImportActivity : ComponentActivity() {
    private lateinit var model: AttachmentImportModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedText = runCatching { IncomingContentParser.sharedText(intent) }.getOrNull()
        // Both destinations use the same Maps parser. Even an expired request must be
        // consumed/rejected by routing, never fall through into the card chooser.
        if (intent.action == Intent.ACTION_SEND && GoogleMapsShareParser.sharedUrl(sharedText) != null &&
            com.cagri.real.platform.android.content.PendingContentDestination.hasPending(this)) {
            startActivity(Intent(intent).setClass(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish(); return
        }
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val app = applicationContext
        val incoming = Intent(intent)
        val fresh = savedInstanceState == null
        val source = SnippetSourceDetector.detect(referrer?.host ?: referrer?.schemeSpecificPart, sharedText,
            intent.hasExtra(Intent.EXTRA_EMAIL) || intent.hasExtra(Intent.EXTRA_CC))
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AttachmentImportModel(app, incoming, fresh, source) as T
        })[AttachmentImportModel::class.java]
        val prefs = com.cagri.real.platform.android.sync.SyncLocal.preferences(this, "real_ui", MODE_PRIVATE)
        val style = DesignStyle.fromPreference(prefs.getString("design_style", "liquid_glass"))
        val reduceMotion = prefs.getBoolean("reduce_animations", false)
        setContent {
            RealTheme {
                CompositionLocalProvider(LocalDesignStyle provides style, LocalLiquidReduceMotion provides reduceMotion) {
                    LaunchedEffect(model.closed) { if (model.closed) finish() }
                    AttachmentImportScreen(model, reduceMotion,
                        onOpen = { open -> startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); model.closeNow() })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::model.isInitialized && model.enqueue(Intent(intent)))
            RealToast.show(this, "Yeni paylaşım sırada; bu bitince açılacak.", long = true)
    }
}

/** Where the shared content goes. */
sealed interface ShareDestination {
    data class NewCard(val title: String, val categoryId: Long?, val plannedDay: Long?) : ShareDestination
    data class ExistingCard(val id: Long, val replaceLink: Boolean) : ShareDestination
    data class Board(val id: String) : ShareDestination
}

internal class AttachmentImportModel(
    private val context: Context, incoming: Intent, fresh: Boolean, val detectedSource: SnippetSource
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = ReminderRepository(context)
    private val store = PendingContentStore(context)
    private val queue = ArrayDeque<Intent>()

    var content by mutableStateOf<IncomingContent?>(null); private set
    var reminders by mutableStateOf<List<ReminderItem>>(emptyList()); private set
    var categories by mutableStateOf<List<CustomCategory>>(emptyList()); private set
    var boards by mutableStateOf<List<PanoBoard>>(emptyList()); private set
    var presetCategoryId by mutableStateOf<Long?>(null); private set
    var loading by mutableStateOf(true); private set
    var resolving by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    /** Set after saving: what was added and how to open it. */
    var done by mutableStateOf<Pair<String, Intent>?>(null); private set
    var closed by mutableStateOf(false); private set
    /** Bumped for every share shown, so the sheet resets its choices. */
    var generation by mutableIntStateOf(0); private set

    init { start(incoming, restore = !fresh) }

    private fun start(intent: Intent, restore: Boolean) {
        loading = true; error = null; done = null; content = null; resolving = false
        presetCategoryId = ShareShortcuts.categoryId(intent)
        generation++
        scope.launch {
            try {
                content = withContext(Dispatchers.IO + NonCancellable) {
                    // A new share replaces one that was left half-way; only a restarted sheet restores it.
                    val pending = store.load()
                    if (restore && pending != null) pending
                    else {
                        if (pending != null) cleanUp(pending)
                        prepare(intent)
                    }
                }
                withContext(Dispatchers.IO) {
                    reminders = repository.load().withoutDividers()
                    categories = CategoryRepository(context).load().filter { !it.isReadOnlyShared }
                }
                boards = runCatching { withContext(Dispatchers.IO) { loadBoards() } }.getOrDefault(emptyList())
                content?.let { if (it.referenceUrl != null && it.previewTitle == null && it.musicBlocks.isEmpty()) resolve(it) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Paylaşım okunamadı" }
            finally { loading = false }
        }
    }

    /** A share that arrived while this one is open. False when nothing usable came. */
    fun enqueue(intent: Intent): Boolean {
        if (intent.action !in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_PROCESS_TEXT)) return false
        queue.addLast(intent)
        return true
    }

    private fun boardRepository() = run {
        val user = com.cagri.real.platform.android.auth.AndroidBackend.auth.currentUser()
        BoardRepositories.obtain(context, user?.id ?: "guest", user?.displayName ?: "REAL user")
    }

    private suspend fun loadBoards(): List<PanoBoard> {
        val repo = boardRepository()
        repo.load()
        return repo.state.value.boards.filter { it.role != "VIEWER" }.sortedByDescending { it.updatedAt }
    }

    private fun prepare(intent: Intent): IncomingContent {
        require(intent.action in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_PROCESS_TEXT)) { "Paylaşım okunamadı" }
        var result = IncomingContentParser.smart(IncomingContentParser.sharedText(intent),
            intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: intent.getStringExtra(Intent.EXTRA_TITLE))
        var failures = 0
        for (uri in IncomingContentParser.streams(intent)) {
            val voice = runCatching { importVoice(uri) }.getOrNull()
            if (voice != null) {
                result = result.copy(voiceRecordings = result.voiceRecordings + voice)
            } else {
                val item = runCatching { AttachmentStorage.importUri(context, uri, intent.flags, intent.type) }.getOrNull()
                if (item == null) failures++ else result = result.copy(attachments = result.attachments + item)
            }
            store.save(result.copy(warning = "İçe aktarma yarıda kaldıysa eksik dosyaları yeniden paylaş."))
        }
        require(!result.isEmpty) { "Okunabilir içerik bulunamadı. Dosyayı yeniden paylaşabilirsin." }
        result = result.copy(warning = if (failures > 0) "$failures dosya okunamadı veya kopyalama sınırını (100 MB) aştı. Yalnızca aşağıdaki içerik eklenecek." else null)
        store.save(result)
        return result
    }

    /** m4a / aac audio (voice memos, most recorders) becomes a REAL voice recording; other audio stays a file. */
    private fun importVoice(uri: Uri): ReminderVoiceRecording? {
        val mime = context.contentResolver.getType(uri)?.lowercase() ?: return null
        if (mime !in setOf("audio/mp4", "audio/m4a", "audio/x-m4a", "audio/aac", "audio/mp4a-latm")) return null
        val (id, target) = VoiceRecordingStorage.createOutput(context)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024); var total = 0L
                    while (true) {
                        val n = input.read(buffer); if (n <= 0) break
                        total += n; require(total <= 25L * 1024 * 1024) { "Ses dosyası çok büyük" }
                        output.write(buffer, 0, n)
                    }
                }
            } ?: return null.also { target.delete() }
            val duration = runCatching {
                MediaMetadataRetriever().run {
                    try { setDataSource(target.path); extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() }
                    finally { release() }
                }
            }.getOrNull()?.takeIf { it > 0 } ?: return null.also { target.delete() }
            return ReminderVoiceRecording(id, target.path, duration, System.currentTimeMillis())
        } catch (e: Exception) { target.delete(); throw e }
    }

    /** Link preview and music recognition; the sheet is usable meanwhile. */
    private fun resolve(base: IncomingContent) {
        val url = base.referenceUrl ?: return
        resolving = true
        scope.launch {
            try {
                val found = SharedLinkResolver.resolve(url)
                val current = content ?: return@launch
                if (current.referenceUrl != url) return@launch
                val updated = if (found.music != null) current.copy(
                    musicBlocks = listOf(found.music), referenceUrl = null, previewTitle = found.title,
                    previewImageUrl = found.imageUrl,
                    suggestedTitle = current.suggestedTitle ?: listOf(found.music.title, found.music.artist).filter { it.isNotBlank() }.joinToString(" – "))
                else current.copy(previewTitle = found.title ?: "", previewImageUrl = found.imageUrl,
                    suggestedTitle = current.suggestedTitle ?: found.title)
                content = updated
                withContext(Dispatchers.IO + NonCancellable) { runCatching { store.save(updated) } }
            } finally { resolving = false }
        }
    }

    fun save(destination: ShareDestination, source: SnippetSource) {
        if (loading || busy || done != null) return
        val payload = content ?: return
        busy = true; error = null
        scope.launch {
            try {
                val snippetsTagged = payload.attachments.map { if (it.type == AttachmentType.TEXT_SNIPPET) it.copy(source = source) else it }
                done = withContext(Dispatchers.IO + NonCancellable) {
                    when (destination) {
                        is ShareDestination.NewCard -> saveNew(payload, snippetsTagged, destination)
                        is ShareDestination.ExistingCard -> saveExisting(payload, snippetsTagged, destination)
                        is ShareDestination.Board -> saveBoard(payload, snippetsTagged, destination.id)
                    }
                }
                scope.launch { delay(1600); if (done != null) next() }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                error = e.message ?: "Kaydedilemedi"
                reminders = withContext(Dispatchers.IO) { repository.load().withoutDividers() }
            }
            finally { busy = false }
        }
    }

    private fun afterCardChange(updated: ReminderItem) {
        runCatching { ReminderNotifications.refreshEdited(context, updated); ReminderScheduler.reconcile(context, repository) }
        store.clear()
        ReminderRepository.notifyChanged()
        runCatching { com.cagri.real.platform.android.sync.SyncLocal.requestSync() }
        runCatching { com.cagri.real.sharedcategories.SharedCategorySync.request(context, 300) }
    }

    private fun openCard(id: Long) = Intent(context, MainActivity::class.java).putExtra(ReminderNotifications.EXTRA_REMINDER_ID, id)

    private fun saveNew(payload: IncomingContent, attachments: List<ReminderAttachment>, d: ShareDestination.NewCard): Pair<String, Intent> =
        synchronized(ReminderRepository.mutationLock) {
            val title = d.title.trim().ifBlank {
                payload.suggestedTitle ?: payload.previewTitle?.takeIf { it.isNotBlank() } ?: payload.referenceUrl
                    ?: attachments.firstOrNull { it.type != AttachmentType.TEXT_SNIPPET }?.displayName
                    ?: payload.voiceRecordings.firstOrNull()?.let { "" } ?: "Paylaşılan içerik"
            }.take(2000)
            val created = repository.add(text = title, plannedDateEpochDay = d.plannedDay, customCategoryId = d.categoryId,
                sendImmediateNotification = false, referenceUrl = payload.referenceUrl,
                attachments = ReminderRepository.limitPhotos(attachments, 0), voiceRecordings = payload.voiceRecordings,
                musicBlocks = payload.musicBlocks)
            afterCardChange(created)
            val where = categories.firstOrNull { it.id == d.categoryId }?.name
            (if (where != null) "$where kategorisine eklendi" else "Yeni kart eklendi") to openCard(created.id)
        }

    private fun saveExisting(payload: IncomingContent, attachments: List<ReminderAttachment>, d: ShareDestination.ExistingCard): Pair<String, Intent> =
        synchronized(ReminderRepository.mutationLock) {
            val current = repository.load().firstOrNull { it.id == d.id } ?: kotlin.error("Kart artık mevcut değil")
            check(payload.referenceUrl == null || current.referenceUrl == null || current.referenceUrl == payload.referenceUrl || d.replaceLink) {
                "Bu kartın bağlantısı var. Değiştirmeyi onayla."
            }
            val updated = repository.appendContent(d.id, attachments, payload.referenceUrl, payload.voiceRecordings, payload.musicBlocks)
            afterCardChange(updated)
            "Karta eklendi" to openCard(d.id)
        }

    /** Every piece becomes its own block, stacked below what is already on the board. */
    private suspend fun saveBoard(payload: IncomingContent, attachments: List<ReminderAttachment>, boardId: String): Pair<String, Intent> {
        val repo = boardRepository()
        repo.load()
        val board = repo.state.value.boards.firstOrNull { it.id == boardId } ?: kotlin.error("Pano artık mevcut değil")
        val existing = repo.state.value.items[boardId].orEmpty().filterNot { it.deleted }
        var y = (existing.maxOfOrNull { it.transform.y + it.transform.height } ?: 0f) + 40f
        val x = existing.minOfOrNull { it.transform.x } ?: 0f
        var z = (existing.maxOfOrNull { it.transform.z } ?: 0) + 1
        suspend fun put(type: BoardBlockType, payloadJson: org.json.JSONObject, width: Float = 320f, height: Float = 200f) {
            val t = BoardTransform(x = x, y = y, width = width, height = height, z = z++).clamped(type)
            val staged = if (type == BoardBlockType.ATTACHMENT || type == BoardBlockType.VOICE) repo.assets.stageContent(type, payloadJson) else payloadJson
            repo.add(boardId, type, t, staged)
            y += t.height + 24f
        }
        for (item in attachments) when (item.type) {
            AttachmentType.IMAGE -> {
                val photo = repo.assets.importLocalImage(item.localPath)
                val height = (320f / photo.optDouble("aspect", 1.0).toFloat()).coerceIn(100f, 800f)
                put(if (photo.optString("mime") == "image/gif") BoardBlockType.GIF else BoardBlockType.PHOTO, photo, 320f, height)
            }
            else -> put(BoardBlockType.ATTACHMENT, BoardRealContent.attachmentPayload(item))
        }
        payload.referenceUrl?.let { put(BoardBlockType.LINK, org.json.JSONObject().put("referenceUrl", it)) }
        payload.musicBlocks.forEach { put(BoardBlockType.MUSIC, BoardRealContent.musicPayload(it), 320f, 160f) }
        payload.voiceRecordings.forEach { put(BoardBlockType.VOICE, BoardRealContent.voicePayload(it)) }
        // The board keeps its own copies; the share's working copies go.
        cleanUp(payload)
        store.clear()
        // Sync outlives this sheet.
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) { runCatching { repo.sync(boardId) } }
        return "${board.title} panosuna eklendi" to Intent(context, MainActivity::class.java)
            .putExtra(com.cagri.real.platform.android.boards.PanoPush.EXTRA_BOARD, boardId)
    }

    /** Deletes the share's copied files that no card uses. */
    private fun cleanUp(payload: IncomingContent) = synchronized(ReminderRepository.mutationLock) {
        val used = repository.getAllAttachmentPaths()
        AttachmentStorage.deleteAttachmentFilePaths(context, payload.attachments.map { it.localPath }.filter { it.isNotBlank() && it !in used })
        val voiceUsed = repository.load().flatMap { r -> r.voiceRecordings.map { it.localPath } }.toSet()
        VoiceRecordingStorage.deleteRecordingPaths(context, payload.voiceRecordings.map { it.localPath }.filter { it !in voiceUsed })
    }

    fun discard() {
        if (loading || busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO + NonCancellable) {
                    (content ?: runCatching { store.load() }.getOrNull())?.let(::cleanUp)
                    store.clear()
                    ReminderRepository.notifyChanged()
                }
                next()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Paylaşım kapatılamadı. Yeniden dene." }
            finally { busy = false }
        }
    }

    /** The next waiting share, or close. */
    fun next() {
        val waiting = queue.removeFirstOrNull()
        if (waiting == null) closed = true else start(waiting, restore = false)
    }

    fun closeNow() { queue.clear(); closed = true }

    override fun onCleared() { scope.cancel() }
}
