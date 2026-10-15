package com.cagri.real.platform.android.sync

import android.app.Activity
import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.cagri.real.platform.android.auth.AndroidBackend
import com.cagri.real.data.ReminderRepository
import com.cagri.real.notifications.ReminderScheduler
import com.cagri.real.sync.SyncEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Android composition/lifecycle/storage boundary. No sync plumbing in Compose. */
object SyncLocal {
    val startup = com.cagri.real.startup.StartupSession()
    val lock=Any()
    private val mutableHealth = kotlinx.coroutines.flow.MutableStateFlow<com.cagri.real.sync.SyncHealthSnapshot?>(null)
    val health: kotlinx.coroutines.flow.StateFlow<com.cagri.real.sync.SyncHealthSnapshot?> = mutableHealth
    /** Called from storage/sync IO boundaries, never composition or a timer of its own. */
    private fun publishHealth() {
        synchronized(lock) { mutableHealth.value = if (::database.isInitialized && active.startsWith("u:")) database.health(active) else null }
    }
    suspend fun readSyncHealth(uid: String): com.cagri.real.sync.SyncHealthSnapshot = withContext(Dispatchers.IO) {
        awaitInitialized()
        synchronized(lock) {
            check(active=="u:$uid" && AndroidBackend.auth.currentUser()?.id==uid) { "Hesap değişti." }
            database.health(active)
        }
    }
    suspend fun retrySyncIssue(uid: String, token: String) = withContext(Dispatchers.IO) {
        awaitInitialized()
        runLock.withLock {
            synchronized(lock) {
                check(active=="u:$uid" && AndroidBackend.auth.currentUser()?.id==uid && !cloudPaused) { "Eşitleme bu hesap için kapalı." }
                database.retryIssue(active,token)
            }
            publishHealth()
        }
        requestSync()
    }
    suspend fun chooseSyncConflict(uid: String, conflict: com.cagri.real.sync.PersistedSyncConflict,
        choice: com.cagri.real.sync.SyncConflictChoice) = withContext(Dispatchers.IO) {
        awaitInitialized()
        runLock.withLock {
            synchronized(lock) {
                check(active=="u:$uid" && AndroidBackend.auth.currentUser()?.id==uid && !cloudPaused) { "Eşitleme bu hesap için kapalı." }
                database.resolve(active,conflict.id,conflict.revision,conflict.generation,choice)
            }
            publishHealth()
        }
        main.post { if (active=="u:$uid" && AndroidBackend.auth.currentUser()?.id==uid) ReminderRepository.notifyChanged() }
        requestSync()
    }
    internal val main by lazy { Handler(Looper.getMainLooper()) }
    private lateinit var application:Application
    internal lateinit var database:SyncDatabase
    @Volatile private var active=""
    @Volatile private var cloudPaused = false
    @Volatile private var initialized = CompletableDeferred<Unit>()
    private val accountSwitch = Mutex()
    suspend fun awaitInitialized() { initialized.await() }
    private fun requireInitialized() {
        if (!initialized.isCompleted) {
            check(Looper.myLooper() != Looper.getMainLooper()) { "Storage must be awaited before opening UI" }
            runBlocking { initialized.await() }
        } else runBlocking { initialized.await() }
    }
    private val activities=mutableSetOf<Activity>()
    private val wrappers=mutableListOf<java.lang.ref.WeakReference<SyncPreferences>>()
    private val runLock=Mutex()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    @Volatile private var foregroundJob:Job?=null
    private val requestLock = Any()
    private val syncDemand = SyncDemand()
    private val processSession = UUID.randomUUID().toString()
    private var inboxJob: Job? = null
    private var inboxUid: String? = null
    private fun syncSession() = "$processSession:$active"
    private data class UiPreferenceWrite(val workspace: String, val editor: SharedPreferences.Editor, val wrapper: SyncPreferences?)
    private val uiPreferenceWrites = kotlinx.coroutines.channels.Channel<UiPreferenceWrite>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private val uiPreferenceWriter = scope.launch {
        for (write in uiPreferenceWrites) {
            try {
                withContext(NonCancellable) {
                    synchronized(lock) {
                        if (write.workspace == active) check(write.editor.commit()) { "Preference write failed" }
                    }
                }
            } catch (_: Exception) {
                main.post { com.cagri.real.ui.components.RealToast.show(application, "Ayar kaydedilemedi. Yeniden dene.") }
            } finally { write.wrapper?.endUiWrite() }
        }
    }
    /** For callbacks that already publish their own optimistic UI state; cards/outbox still commit atomically on IO. */
    fun saveUiPreference(preferences: SharedPreferences, editor: SharedPreferences.Editor) {
        val wrapper = preferences as? SyncPreferences
        val workspace = wrapper?.scope ?: active
        wrapper?.beginUiWrite()
        if (uiPreferenceWrites.trySend(UiPreferenceWrite(workspace, editor, wrapper)).isFailure) {
            wrapper?.endUiWrite()
            error("Preference queue is unavailable")
        }
    }
    private var poll:Job?=null
    private var authSubscription:com.cagri.real.auth.AuthSubscription?=null
    fun initialize(app:Application) {
        application=app
        startStorageInitialization(app)
        app.registerActivityLifecycleCallbacks(object:Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity:Activity,state:Bundle?) { activities.add(activity) }
            override fun onActivityDestroyed(activity:Activity) { activities.remove(activity) }
            override fun onActivityResumed(activity:Activity) {
                requestSync()
                if(!cloudPaused && poll==null)poll=scope.launch { while(isActive) { delay(60_000);runCatching { sync() } } }
            }
            override fun onActivityPaused(activity:Activity) { poll?.cancel();poll=null }
            override fun onActivityStarted(activity:Activity) {}
            override fun onActivityStopped(activity:Activity) {}
            override fun onActivitySaveInstanceState(activity:Activity,state:Bundle) {}
        })
    }
    fun retryInitialization() = synchronized(requestLock) {
        if (initialized.isCancelled) {
            initialized = CompletableDeferred()
            startStorageInitialization(application)
        }
    }
    private fun startStorageInitialization(app: Application) {
        scope.launch {
        try {
        database=SyncDatabase(app)
        synchronized(lock) {
            database.transaction {
                var guest=database.meta("control","guest")
                if(guest==null) { guest="guest:"+UUID.randomUUID();database.meta("control","guest",guest) }
                active=database.meta("control","active")?:guest
                if(database.meta("control","imported")!="1") {
                    for(file in SyncProjection.files) app.getSharedPreferences(file,Context.MODE_PRIVATE).all
                        .filterKeys { SyncProjection.tracked(file,it) }.forEach { (key,v)->database.writeValue(guest,file,key,v) }
                    database.rebuild(guest)
                    database.meta("control","imported","1")
                }
            }
        }
        // SharedPreferences XML loads are complete before any UI getter uses the fallback.
        SyncProjection.files.forEach { app.getSharedPreferences(it, Context.MODE_PRIVATE).all }
        val uid = AndroidBackend.auth.currentUser()?.id
        runCatching { com.cagri.real.profile.ProfileAccountStore.migrateLegacy(app, uid) }
            .onFailure { android.util.Log.w("REAL_PROFILE", "PROFILE_MIGRATION_PENDING") }
        switchAccount(uid, recreate = false)
        initialized.complete(Unit)
        runCatching { com.cagri.real.platform.android.account.LocalAccountCleanup.resume(app) }
            .onFailure { android.util.Log.w("REAL_ACCOUNT", "LOCAL_CLEANUP_PENDING") }
        authSubscription=AndroidBackend.auth.observeUser { user ->
            scope.launch { accountSwitch.withLock {
                if (AndroidBackend.auth.currentUser()?.id == user?.id) switchAccount(user?.id)
            } }
        }
        if (uid != null) requestSync()
        } catch (failure: Throwable) { initialized.completeExceptionally(failure) }
        }
    }
    private fun switchAccount(uid:String?, recreate: Boolean = true) {
        val oldStartup = startup.state.value
        if (oldStartup.phase != com.cagri.real.startup.StartupPhase.UNKNOWN && oldStartup.uid == uid &&
            (if (uid == null) active.startsWith("guest:") else active == "u:$uid")) return
        var changed=false
        synchronized(lock) {
            val desired=if(uid!=null)"u:$uid" else {
                val old=database.meta("control","guest")!!
                if(database.meta(old,"adopted")!=null) {
                    val fresh="guest:"+UUID.randomUUID();database.meta("control","guest",fresh);fresh
                } else old
            }
            if(desired!=active) {
                foregroundJob?.cancel()
                synchronized(requestLock) { inboxJob?.cancel(); inboxUid = null }
                if(uid!=null) database.adopt(database.meta("control","guest")!!,desired)
                database.warmPreferences(desired)
                active=desired;database.meta("control","active",active);changed=true
            } else if(uid!=null) database.adopt(database.meta("control","guest")!!,desired)
        }
        // Upgrade old wire payloads from the still-preserved local product data exactly once.
        // Never invent a missing file/card or clear an existing failure marker.
        if (uid != null) database.transaction {
            if (database.meta(active,"lossless_binary_projection_v1") != "1") {
                database.rebuild(active,SyncProjection.CARDS)
                database.meta(active,"lossless_binary_projection_v1","1")
            }
        }
        if (!changed) database.warmPreferences(active)
        cloudPaused = database.meta(active,"cloud_paused") == "1"
        publishHealth()
        startup.authResolved(uid, workspaceToken())
        if (cloudPaused) startup.completed(startup.state.value.generation)
        if(changed)main.post {
            // Discard stale editor state, keep the exact existing screens and styling.
            application.getSystemService(android.app.NotificationManager::class.java).cancelAll()
            if (recreate) activities.toList().filter { !it.isFinishing && !it.isDestroyed }.forEach { it.recreate() }
            scope.launch { runCatching { ReminderScheduler.reconcile(application,ReminderRepository(application)) } }
        }
        val scheduler=application.getSystemService(JobScheduler::class.java)
        if(uid==null || cloudPaused) { scheduler.cancel(8101);scheduler.cancel(8102) }
        else {
            scheduler.schedule(JobInfo.Builder(8102,ComponentName(application,RealSyncJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setPeriodic(15*60*1000L).build())
            if (initialized.isCompleted) requestSync()
            // Every signed-in device gets a push token so card shares arrive immediately (not only Pano opt-ins).
            com.cagri.real.platform.android.boards.PanoPush.registerToken(application)
        }
    }
    fun preferences(context:Context,name:String,mode:Int=Context.MODE_PRIVATE):SharedPreferences {
        if(name !in SyncProjection.files)return context.getSharedPreferences(name,mode)
        requireInitialized()
        val wrapper = SyncPreferences(database, active, name, context.getSharedPreferences(name,mode))
        synchronized(wrappers) {
            wrappers.removeAll { it.get()==null }
            wrappers.add(java.lang.ref.WeakReference(wrapper))
        }
        return wrapper
    }
    fun workspaceToken():String = active
    fun preferenceRevision(): Long = if (::database.isInitialized) database.preferenceRevision else 0L
    fun protects(path:String):Boolean=if(!::database.isInitialized)false else synchronized(lock) { database.protects(path) }
    fun retryStartup() {
        startup.retry()
        requestSync()
    }
    fun requestSync() {
        if(!::application.isInitialized || !initialized.isCompleted || cloudPaused || AndroidBackend.auth.currentUser()==null)return
        val generation = syncDemand.request()
        val session = syncSession()
        val extras = android.os.PersistableBundle().apply {
            putLong("real_request_generation", generation)
            putString("real_request_session", session)
        }
        application.getSystemService(JobScheduler::class.java).schedule(JobInfo.Builder(8101,ComponentName(application,RealSyncJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setMinimumLatency(2000)
            .setExtras(extras).setBackoffCriteria(30_000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build())
        val job = synchronized(requestLock) {
            if (foregroundJob?.let { !it.isCompleted && !it.isCancelled } == true) null else scope.launch(start = CoroutineStart.LAZY) {
                if(startup.state.value.phase != com.cagri.real.startup.StartupPhase.SIGNED_IN_LOADING) delay(1000)
                while (isActive && session == syncSession() && !cloudPaused) {
                    val wanted = syncDemand.current()
                    if (!syncDemand.isPending(wanted)) break
                    try { sync(wanted, session) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { break } // Durable job still owns retry on failure.
                }
            }.also { foregroundJob = it }
        }
        job?.start()
    }

    suspend fun syncForSharing(targetCardId: Long? = null) {
        check(!cloudPaused) { "Bulut verileri silindi veya siliniyor; eşitleme kapalı." }
        if (targetCardId != null) {
            syncTargetCard(targetCardId)
        } else {
            sync()
        }
    }
    internal suspend fun syncTargetCard(cardId: Long) = runLock.withLock {
        check(!cloudPaused) { "Bulut eşitlemesi kapalı." }
        awaitInitialized()
        val uid = AndroidBackend.auth.currentUser()?.id ?: return@withLock
        val account = "u:$uid"
        fun ensureAccount() { check(AndroidBackend.auth.currentUser()?.id == uid && synchronized(lock) { active == account }) { "Account changed" } }
        ensureAccount()
        synchronized(lock) {
            database.rebuild(account, SyncProjection.CARDS)
        }
        val targetRow = synchronized(lock) {
            database.rowByLogicalKey(account, "CARD", cardId.toString())
        } ?: return@withLock
        synchronized(lock) {
            check(!database.hasConflict(account,targetRow.record.id)) { "Bu kartın sürümleri çakışıyor. Hesapları yönet → Eşitleme bölümünden bir sürüm seç." }
            check(database.meta(account,"sync_download:${targetRow.record.id}")==null) { "Bu kartın bulut eki bekliyor. Önce eşitleme sorununu çöz." }
        }
        if (!targetRow.dirty) return@withLock
        val client = AndroidBackend.cloudClient()
        val remote = AndroidSyncRemote(AndroidBackend.syncTransport())
        val binaries = AndroidSyncBinaries(application, database, account, client, AndroidBackend.attachmentTransfer(), remote)
        ensureAccount()
        try { binaries.prepareUpload(targetRow.record) }
        catch (failure: com.cagri.real.sync.SyncBinaryFailure) {
            ensureAccount()
            database.account(account).blockUpload(com.cagri.real.sync.PendingRecord(targetRow.record,targetRow.generation),failure)
            publishHealth()
            throw failure
        }
        ensureAccount()
        val accepted = try { remote.put(targetRow.record) }
        catch (_: com.cagri.real.sync.SyncRevisionConflict) {
            val latest=remote.get(targetRow.record.id)
            ensureAccount()
            val store=database.account(account)
            try {
                binaries.prepareDownload(latest)
                ensureAccount()
                store.receive(latest,store.cursor())
            } catch (failure: com.cagri.real.sync.SyncBinaryFailure) {
                ensureAccount()
                store.deferDownload(latest,failure)
            }
            publishHealth()
            main.post { if (active==account && AndroidBackend.auth.currentUser()?.id==uid) ReminderRepository.notifyChanged() }
            error("Kartın bulut sürümü değişti. Hesapları yönet → Eşitleme bölümünü kontrol edip paylaşmayı tekrar dene.")
        }
        ensureAccount()
        synchronized(lock) {
            database.account(account).acknowledge(
                com.cagri.real.sync.PendingRecord(targetRow.record, targetRow.generation),
                accepted
            )
            database.account(account).clearUploadIssue(targetRow.record.id)
        }
        publishHealth()
        check(synchronized(lock) { database.row(account,targetRow.record.id)?.record?.payload == targetRow.record.payload }) {
            "Kart paylaşım sırasında düzenlendi. Son sürümü göndermek için tekrar dene."
        }
    }
    internal suspend fun sync(requestGeneration: Long? = null, requestSession: String? = null)=runLock.withLock {
        awaitInitialized()
        if (cloudPaused) return@withLock
        if (requestGeneration != null && requestSession == syncSession() && !syncDemand.isPending(requestGeneration)) return@withLock
        val coveredGeneration = syncDemand.current()
        val uid=AndroidBackend.auth.currentUser()?.id?:return@withLock
        val account="u:$uid"
        val startupGeneration = startup.state.value.generation
        fun ensureAccount() { check(AndroidBackend.auth.currentUser()?.id==uid && synchronized(lock){active==account}) { "Account changed" } }
        ensureAccount()
        // Compare persisted local projections, not network activity. Partial restores still notify.
        val before = synchronized(lock) { SyncProjection.files.associateWith { database.values(account,it).toMap() } }
        val client=AndroidBackend.cloudClient()
        val remote=AndroidSyncRemote(AndroidBackend.syncTransport())
        val binaries=AndroidSyncBinaries(application,database,account,client,AndroidBackend.attachmentTransfer(),remote)
        var successfulRun = false
        try {
            ensureAccount()
            withTimeout(120_000) { SyncEngine(remote,database.account(account),binaries,::ensureAccount).run() }
            synchronized(lock) { database.meta(account,"last_error","");database.meta(account,"last_success",System.currentTimeMillis().toString()) }
            notifyReceivedSharedCards(application,database,account,uid)
            successfulRun = true
            syncDemand.complete(coveredGeneration)
        } catch(timeout:TimeoutCancellationException) {
            startup.failed(startupGeneration)
            throw timeout
        } catch(cancelled:CancellationException) { throw cancelled }
        catch(failure:Exception) {
            if (failure is com.cagri.real.cloud.CloudHttpFailure && failure.status == 423) {
                // Another device started erasure. Keep local cards available, stop retry loops.
                synchronized(lock) { database.meta(account,"cloud_paused","1"); cloudPaused = true }
                application.getSystemService(JobScheduler::class.java).apply { cancel(8101); cancel(8102) }
                poll?.cancel(); poll = null
                startup.completed(startupGeneration)
                return@withLock
            }
            startup.failed(startupGeneration)
            synchronized(lock) { database.meta(account,"last_error",failure.javaClass.simpleName) }
            throw failure
        } finally {
            publishHealth()
            // Restore affects all repositories; a projection change is not a new local mutation.
            if(AndroidBackend.auth.currentUser()?.id==uid) {
                // Keep the existing foreground overdue-delivery fallback even on an empty sync feed.
                // Its local writes must be included in the diff below.
                runCatching { ReminderScheduler.reconcile(application,ReminderRepository(application)) }
                // SQLite reads/diffing stay on the sync worker, never in a main-handler callback.
                val projectionChanged = synchronized(lock) {
                    if(active != account) false else {
                        val after = SyncProjection.files.associateWith { database.values(account,it).toMap() }
                        val live = synchronized(wrappers) {
                            wrappers.removeAll { it.get()==null }
                            wrappers.mapNotNull { it.get() }
                        }
                        live.filter { it.scope==account }.forEach { it.changed(emptySet(), after.getValue(it.file)) }
                        before != after
                    }
                }
                // READY revisions invalidate the Activity projection too. An empty successful poll
                // must not create a new revision; first restore and error recovery still complete.
                if(successfulRun && workspaceToken()==account &&
                    (projectionChanged || startup.state.value.phase != com.cagri.real.startup.StartupPhase.READY))
                    startup.completed(startupGeneration)
                if (successfulRun && workspaceToken() == account) refreshInbox(uid)
                if(projectionChanged) {
                    main.post {
                        if(AndroidBackend.auth.currentUser()?.id==uid && workspaceToken()==account)
                            ReminderRepository.notifyChanged()
                    }
                }
            }
        }
    }
    suspend fun pauseCloud(uid: String) = withContext(Dispatchers.IO) {
        awaitInitialized()
        runLock.withLock {
            synchronized(lock) {
                check(active == "u:$uid" && AndroidBackend.auth.currentUser()?.id == uid) { "Hesap değişti." }
                database.meta(active,"cloud_paused","1")
                cloudPaused = true
            }
            foregroundJob?.cancel()
            synchronized(requestLock) { inboxJob?.cancel(); inboxUid = null }
            poll?.cancel(); poll = null
            application.getSystemService(JobScheduler::class.java).apply { cancel(8101); cancel(8102) }
            publishHealth()
        }
    }

    /** Only called after a successful server check proved no erasure exists, with explicit UI action. */
    suspend fun resumeCloudAfterRejectedErasure(uid: String) = withContext(Dispatchers.IO) {
        awaitInitialized()
        runLock.withLock {
            synchronized(lock) {
                check(active == "u:$uid" && AndroidBackend.auth.currentUser()?.id == uid) { "Hesap değişti." }
                database.meta(active, "cloud_paused", "0"); cloudPaused = false
            }
            publishHealth()
        }
        application.getSystemService(JobScheduler::class.java).schedule(
            JobInfo.Builder(8102, ComponentName(application, RealSyncJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setPeriodic(15 * 60 * 1000L).build())
        requestSync()
    }

    suspend fun isCloudPaused(uid: String): Boolean = withContext(Dispatchers.IO) {
        awaitInitialized()
        synchronized(lock) { database.meta("u:$uid", "cloud_paused") == "1" }
    }

    /** Server grants a new epoch first. Local reset is idempotent after a lost response/crash. */
    suspend fun prepareRestartedBackup(uid: String, epoch: String) = withContext(Dispatchers.IO) {
        awaitInitialized()
        runLock.withLock {
            synchronized(lock) {
                check(active == "u:$uid" && AndroidBackend.auth.currentUser()?.id == uid) { "Hesap değişti." }
                check(cloudPaused) { "Önce bulut eşitlemesini durdur." }
                database.prepareFreshBackup(active, epoch)
            }
            publishHealth()
        }
    }

    suspend fun accountFilePaths(uid: String): List<String> = withContext(Dispatchers.IO) {
        awaitInitialized()
        synchronized(lock) {
            check(AndroidBackend.auth.currentUser()?.id == uid) { "Hesap değişti." }
            database.accountFilePaths("u:$uid")
        }
    }

    /** Called only after server erasure completed and Firebase deletion succeeded. */
    suspend fun eraseDeletedAccount(uid: String) = withContext(Dispatchers.IO) {
        awaitInitialized()
        runLock.withLock {
            synchronized(lock) {
                check(AndroidBackend.auth.currentUser()?.id != uid) { "Önce Firebase hesabı silinmeli." }
                database.eraseScope("u:$uid")
            }
            publishHealth()
        }
    }

    private fun refreshInbox(uid: String) = synchronized(requestLock) {
        if (inboxJob?.isActive == true && inboxUid == uid) return@synchronized
        inboxJob?.cancel()
        inboxUid = uid
        inboxJob = scope.launch {
            try {
                val delivered = withTimeout(12_000) { com.cagri.real.platform.android.share.CardShareClient().receive() }
                if (delivered > 0 && AndroidBackend.auth.currentUser()?.id == uid && active == "u:$uid") requestSync()
            } catch (_: TimeoutCancellationException) { /* next normal poll retries optional inbox */ }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* normal cloud restore is already complete */ }
            // Independent of normal card sharing: a share inbox failure cannot suppress REAL cards.
            if (AndroidBackend.auth.currentUser()?.id == uid && active == "u:$uid" && !cloudPaused) {
                try {
                    val changes = withTimeout(12_000) {
                        com.cagri.real.platform.android.announcements.AnnouncementCardClient.receive(uid)
                    }
                    if (changes > 0 && AndroidBackend.auth.currentUser()?.id == uid && active == "u:$uid") requestSync()
                } catch (_: TimeoutCancellationException) { /* next existing foreground refresh retries */ }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* optional delivery must not block private sync */ }
            }
        }
    }

}
