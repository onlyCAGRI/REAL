package com.cagri.real

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.cagri.real.data.*
import com.cagri.real.notifications.ReminderNotifications
import com.cagri.real.notifications.ReminderScheduler
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.components.*
import com.cagri.real.ui.theme.RealTheme
import kotlinx.coroutines.*

class AttachmentImportActivity : ComponentActivity() {
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
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK))
        setContent { RealTheme { com.cagri.real.ui.StartupLoadingScreen(false, {}, {}) } }
        lifecycleScope.launch {
            try {
                com.cagri.real.platform.android.sync.SyncLocal.awaitInitialized()
                openImport()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                Toast.makeText(this@AttachmentImportActivity, "Yerel kayıtlar açılamadı. Yeniden dene.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
    private fun openImport() {
        val app = applicationContext
        val incoming = Intent(intent)
        val model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AttachmentImportModel(app, incoming) as T
        })[AttachmentImportModel::class.java]
        val prefs = com.cagri.real.platform.android.sync.SyncLocal.preferences(this, "real_ui", MODE_PRIVATE)
        val style = DesignStyle.fromPreference(prefs.getString("design_style", "liquid_glass"))
        val reduceMotion = prefs.getBoolean("reduce_animations", false)
        setContent {
            RealTheme {
                CompositionLocalProvider(LocalDesignStyle provides style, LocalLiquidReduceMotion provides reduceMotion) {
                    LaunchedEffect(model.finishedId) {
                        model.finishedId?.let { id ->
                            startActivity(Intent(this@AttachmentImportActivity, MainActivity::class.java)
                                .putExtra(ReminderNotifications.EXTRA_REMINDER_ID, id))
                            finish()
                        }
                    }
                    LaunchedEffect(model.cancelled) { if (model.cancelled) finish() }
                    AttachmentImportScreen(model.content, model.reminders, model.loading, model.busy, model.error,
                        style, prefs.getBoolean("reduce_dynamic_blur", false),
                        onImport = model::saveToReminder, onDismiss = model::discard)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        Toast.makeText(this, "Önce açık olan paylaşımı ekle veya iptal et, ardından yeniden paylaş", Toast.LENGTH_LONG).show()
    }
}

private class AttachmentImportModel(private val context: Context, incoming: Intent) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = ReminderRepository(context)
    private val store = PendingContentStore(context)
    var content by mutableStateOf<IncomingContent?>(null); private set
    var reminders by mutableStateOf<List<ReminderItem>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var finishedId by mutableStateOf<Long?>(null); private set
    var cancelled by mutableStateOf(false); private set

    init {
        scope.launch {
            try {
                content = withContext(Dispatchers.IO + NonCancellable) {
                    store.load()?.let { pending ->
                        pending.copy(warning = listOfNotNull("Kaydedilmeyi bekleyen paylaşım geri yüklendi. Yeni bir paylaşım yaptıysan bunu bitirdikten sonra yeniden gönder.", pending.warning).joinToString("\n"))
                    } ?: prepare(incoming)
                }
                reminders = withContext(Dispatchers.IO) { repository.load() }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Paylaşım okunamadı" }
            finally { loading = false }
        }
    }

    private fun prepare(intent: Intent): IncomingContent {
        require(intent.action in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_PROCESS_TEXT))
        var result = IncomingContentParser.text(IncomingContentParser.sharedText(intent), intent.getStringExtra(Intent.EXTRA_SUBJECT))
        var failures = 0
        val streams = IncomingContentParser.streams(intent)
        for (uri in streams) {
            val item = runCatching { AttachmentStorage.importUri(context, uri, intent.flags, intent.type) }.getOrNull()
            if (item == null) failures++ else result = result.copy(attachments = result.attachments + item)
            
            store.save(result.copy(warning = "İçe aktarma yarıda kaldıysa eksik dosyaları yeniden paylaş."))
        }
        require(result.attachments.isNotEmpty() || result.referenceUrl != null) { "Okunabilir içerik bulunamadı. Dosyayı yeniden paylaşabilirsin." }
        result = result.copy(warning = if (failures > 0) "$failures dosya okunamadı veya kopyalama sınırını (100 MB) aştı. Yalnızca aşağıdaki içerik eklenecek." else null)
        store.save(result)
        return result
    }

    fun saveToReminder(id: Long, source: SnippetSource, replaceLink: Boolean) {
        if (loading || busy) return
        val payload = content ?: return
        busy = true; error = null
        scope.launch {
            try {
                withContext(Dispatchers.IO + NonCancellable) {
                    synchronized(ReminderRepository.mutationLock) {
                        val current = repository.load().firstOrNull { it.id == id } ?: kotlin.error("Hatırlatıcı artık mevcut değil")
                        check(payload.referenceUrl == null || current.referenceUrl == null || current.referenceUrl == payload.referenceUrl || replaceLink) {
                            "Bu hatırlatıcının bağlantısı var. Değiştirmeyi onayla."
                        }
                        val added = payload.attachments.map { if (it.type == AttachmentType.TEXT_SNIPPET) it.copy(source = source) else it }
                        val updated = repository.appendContent(id, added, payload.referenceUrl)
                        
                        runCatching { ReminderNotifications.refreshEdited(context, updated); ReminderScheduler.reconcile(context, repository) }
                        store.clear()
                        ReminderRepository.notifyChanged()
                    }
                }
                finishedId = id
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                error = e.message ?: "Ekler kaydedilemedi"
                reminders = withContext(Dispatchers.IO) { repository.load() }
            }
            finally { busy = false }
        }
    }

    fun discard() {
        if (loading || busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO + NonCancellable) {
                    synchronized(ReminderRepository.mutationLock) {
                        val paths = (content ?: runCatching { store.load() }.getOrNull())?.attachments?.map { it.localPath }.orEmpty()
                        val used = repository.getAllAttachmentPaths()
                        store.clear()
                        ReminderRepository.notifyChanged()
                        AttachmentStorage.deleteAttachmentFilePaths(context, paths.filterNot { it in used })
                    }
                }
                cancelled = true
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "İçe aktarma kapatılamadı. Yeniden dene." }
            finally { busy = false }
        }
    }

    override fun onCleared() { scope.cancel() }
}
