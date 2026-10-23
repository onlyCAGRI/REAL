package com.cagri.real

import com.cagri.real.i18n.t
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cagri.real.data.*
import com.cagri.real.notifications.*
import com.cagri.real.platform.android.auth.AndroidAuthUiController
import com.cagri.real.platform.android.auth.AndroidBackend
import com.cagri.real.ui.auth.LocalAuthUiController
import com.cagri.real.ui.*
import com.cagri.real.ui.components.categoryDescriptionKey
import com.cagri.real.ui.components.saveCategoryDescription
import com.cagri.real.ui.theme.RealTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

private const val UNDO_WINDOW_MS = 5_000L

class MainActivity : ComponentActivity() {
    // Temporary visual preview requested for skeleton testing. Set to 0L to disable.
    private val startupSkeletonPreviewMs = 500L
    private var startupSkeletonStartedAt = 0L
    private lateinit var repository: ReminderRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var moodTrackerRepository: MoodEntryRepository
    private var reminders by mutableStateOf<List<ReminderItem>>(emptyList())
    private var customCategories by mutableStateOf<List<CustomCategory>>(emptyList())
    private var builtInCategoryColors by mutableStateOf<Map<ReminderCategory, CategoryColorOverride>>(emptyMap())
    private var moodTrackerEntries by mutableStateOf<List<MoodTrackerEntry>>(emptyList())
    private var ready by mutableStateOf(false)
    private var loadFailed by mutableStateOf(false)
    private var exactAllowed by mutableStateOf(false)
    private var notificationsAllowed by mutableStateOf(false)
    private var openedReminderId by mutableStateOf<Long?>(null)
    private var pendingShareDraft by mutableStateOf<SharedPayload?>(null)
    private var pendingPanoBoardId by mutableStateOf<String?>(null)
    private var pendingLocationShare by mutableStateOf<PendingReminderLocation?>(null)
    private val attemptedLocationPreviews = mutableSetOf<Pair<Long, String>>()
    private val operations = Mutex()
    private var refreshGeneration = 0L
    private var refreshedPreferenceRevision = -1L
    private var refreshedPermissionState: Pair<Boolean, Boolean>? = null
    private val waiting = mutableSetOf<Long>() 
    // Deleted cards leave the screen at once but are only removed (files included) after the
    // "Geri al" window, so an undo restores them untouched.
    private var pendingDeleteIds by mutableStateOf<Set<Long>>(emptySet())
    private var pendingDeleteJob: kotlinx.coroutines.Job? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = NotificationManagerCompat.from(this).areNotificationsEnabled()
        if (granted) lifecycleScope.launch {
            perform {
                repository.load().filter { it.id in waiting && !it.isCompleted && it.alertAt == null }
                    .forEach { ReminderNotifications.show(this@MainActivity, it) }
                waiting.clear()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun disableStatusBarContrastEnforcementCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
        }
    }

    private var localWorkspaceToken = ""

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("real_local_workspace", localWorkspaceToken)
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val uid = AndroidBackend.auth.currentUser()?.id
        val savedWorkspace = savedInstanceState?.getString("real_local_workspace")
        super.onCreate(savedInstanceState?.takeIf {
            if (uid != null) savedWorkspace == "u:$uid" else savedWorkspace?.startsWith("guest:") == true
        })
        startupSkeletonStartedAt = android.os.SystemClock.elapsedRealtime()
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK))
        disableStatusBarContrastEnforcementCompat()
        setContent { RealTheme { StartupLoadingScreen(false, {}, {}) } }
        lifecycleScope.launch {
            try {
                com.cagri.real.platform.android.sync.SyncLocal.awaitInitialized()
                localWorkspaceToken = com.cagri.real.platform.android.sync.SyncLocal.workspaceToken()
                openRealContent()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                android.util.Log.e("Real", "Local storage initialization failed", failure)
                setContent { RealTheme { StartupLoadingScreen(true, {
                    com.cagri.real.platform.android.sync.SyncLocal.retryInitialization()
                    recreate()
                }, {}) } }
            }
        }
    }

    private fun openRealContent() {
        repository = ReminderRepository(applicationContext)
        categoryRepository = CategoryRepository(applicationContext)
        moodTrackerRepository = MoodTrackerRepository(applicationContext)
        // Existing startup/onResume refreshes load one snapshot on IO before marking home ready.
        ready = false
        exactAllowed = ReminderScheduler.canScheduleExact(this)
        notificationsAllowed = NotificationManagerCompat.from(this).areNotificationsEnabled()
        lifecycleScope.launch(Dispatchers.IO) { com.cagri.real.ui.components.prewarmRealWordmark(applicationContext) }
        openedReminderId = intent.getLongExtra(ReminderNotifications.EXTRA_REMINDER_ID, -1L).takeIf { it > 0 }
        // Pano push: tapping a board notification opens that board.
        intent.getStringExtra(com.cagri.real.platform.android.boards.PanoPush.EXTRA_BOARD)?.let { pendingPanoBoardId = it; intent.removeExtra(com.cagri.real.platform.android.boards.PanoPush.EXTRA_BOARD) }
        handleShareIntent(intent)
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            com.cagri.real.platform.android.content.PendingContentDestination.returnedWithoutResult(this)
        val preferences = com.cagri.real.platform.android.sync.SyncLocal.preferences(this, "real_ui", MODE_PRIVATE)
        if (preferences.getBoolean("onboarding_completed", false) && !preferences.contains("first_home_intro_completed")) {
            preferences.edit().putBoolean("first_home_intro_completed", true).apply()
        }
        lifecycleScope.launch {
            ReminderRepository.updates.collect {
                perform(reloadRelated = true)
                // A change to a shared category's card goes up shortly (the engine's own writes are skipped).
                com.cagri.real.sharedcategories.SharedCategorySync.request(this@MainActivity)
            }
        }
        // Compose always paints a full-screen black Surface from here on, so the window's own black
        // background is a hidden extra full-screen fill on every frame (GPU overdraw). The starting
        // window has already used it.
        window.setBackgroundDrawable(null)
        setContent {
            RealTheme {
                val pendingDeletes = pendingDeleteIds
                // Ayraçlar (category dividers) go only to the category list; every other screen sees real cards.
                val visibleDividers = remember(reminders, pendingDeletes) { reminders.filter { it.isDivider && it.id !in pendingDeletes } }
                val visibleReminders = remember(reminders, pendingDeletes) {
                    (if (pendingDeletes.isEmpty()) reminders else reminders.filterNot { it.id in pendingDeletes }).withoutDividers().map { r ->
                        // Black or white text follows the card's colour automatically (no manual switch any more).
                        val dark = com.cagri.real.ui.components.autoCardDarkText(r.accentStart, r.accentEnd, r.customSolidColorArgb)
                        if (r.useDarkText == dark) r else r.copy(useDarkText = dark)
                    }
                }
                val authController = remember {
                    AndroidAuthUiController(
                        activity = this@MainActivity,
                        repository = AndroidBackend.auth
                    )
                }
                DisposableEffect(authController) {
                    onDispose { authController.dispose() }
                }
                val authUser by authController.user.collectAsState()
                var reduceBlur by remember { mutableStateOf(preferences.getBoolean("reduce_dynamic_blur", false)) }
                var reduceAnimations by remember { mutableStateOf(preferences.getBoolean("reduce_animations", false)) }
                LaunchedEffect(Unit) {
                    preferences.edit().putBoolean("real_pet_enabled", false).apply()
                    getSharedPreferences("real_pet_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("pet_keywords_enabled", false).apply()
                }
                var realPetEnabled by remember { mutableStateOf(false) }
                var realPetSurfaceActive by remember { mutableStateOf(false) }
                val style = DesignStyle.LiquidGlass
                var hasCompletedOnboarding by remember { mutableStateOf(preferences.getBoolean("onboarding_completed", false)) }
                var userName by remember { mutableStateOf(preferences.getString("user_display_name", "") ?: "") }
                var playFirstHomeIntro by remember { mutableStateOf(false) }
                val startup by com.cagri.real.platform.android.sync.SyncLocal.startup.state.collectAsState()
                var loadedStartupRevision by remember { mutableStateOf(-1L) }
                // READY is published by sync; refresh this Activity's projection before showing
                // an empty result. A flow replay also covers updates missed during recreation.
                LaunchedEffect(startup.workspace, startup.revision) {
                    if (startup.workspace == localWorkspaceToken) {
                        if (perform(reloadRelated = true)) loadedStartupRevision = startup.revision
                        else { ready = false; loadFailed = true }
                    }
                }
                var skeletonPreviewFinished by remember { mutableStateOf(startupSkeletonPreviewMs == 0L) }
                LaunchedEffect(Unit) {
                    val remaining = startupSkeletonPreviewMs -
                        (android.os.SystemClock.elapsedRealtime() - startupSkeletonStartedAt)
                    if (remaining > 0L) delay(remaining)
                    skeletonPreviewFinished = true
                }
                val skeletonCategoryCount = remember(preferences) {
                    preferences.getInt("home_category_preview_count_v1", 4).coerceIn(1, 12)
                }
                val startupBlocksHome = startup.blocksHome(localWorkspaceToken, reminders.isNotEmpty(), loadedStartupRevision)
                // A failed first sync stays behind the startup skeleton: REAL retries quietly (1.5 s, 3 s, 4.5 s) and
                // only shows "Yeniden dene" when it is still failing after that and the phone has no cards to show.
                var startupOfflineVisible by remember { mutableStateOf(false) }
                LaunchedEffect(startup.generation) {
                    startupOfflineVisible = false
                    var failures = 0
                    while (true) {
                        val state = com.cagri.real.platform.android.sync.SyncLocal.startup.state.first {
                            it.phase == com.cagri.real.startup.StartupPhase.READY || it.phase == com.cagri.real.startup.StartupPhase.ERROR_OFFLINE
                        }
                        if (state.phase == com.cagri.real.startup.StartupPhase.READY) break
                        failures++
                        if (failures > 3) { startupOfflineVisible = true; break }
                        delay(1_500L * failures)
                        com.cagri.real.platform.android.sync.SyncLocal.retryStartup()
                    }
                }
                val startupOfflineShown = startup.phase == com.cagri.real.startup.StartupPhase.ERROR_OFFLINE && startupOfflineVisible
                val showWelcome = startup.showWelcome(hasCompletedOnboarding)


                androidx.compose.runtime.CompositionLocalProvider(
                    LocalAuthUiController provides authController,
                    com.cagri.real.ui.components.LocalDesignStyle provides style,
                    com.cagri.real.ui.components.LocalLiquidReduceMotion provides reduceAnimations,
                    com.cagri.real.ui.components.LocalSetReminderTiming provides { id, early, end -> mutate {
                        repository.setTiming(id, early, end).firstOrNull { it.id == id }
                            ?.let { runCatching { ReminderNotifications.refreshEdited(this@MainActivity, it) } }
                    } },
                    com.cagri.real.ui.components.LocalSetReminderAlarm provides { id, on -> mutate { repository.setAlarm(id, on) } },
                    com.cagri.real.ui.components.LocalSetReminderLocationTrigger provides { id, trigger -> mutate { repository.setLocationTrigger(id, trigger) } }
                ) {
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    Crossfade(
                    targetState = showWelcome,
                    animationSpec = if (reduceAnimations) snap() else tween(240),
                    label = "onboardingCrossfade"
                ) { welcome ->
                    if (startup.phase == com.cagri.real.startup.StartupPhase.UNKNOWN ||
                        startup.workspace != localWorkspaceToken) {
                        Surface(Modifier.fillMaxSize(), color = Color.Black) {}
                    } else if (welcome && startup.uid == null) {
                        WelcomeScreen(
                            reduceAnimations = reduceAnimations,
                            onComplete = { enteredName ->
                                val firstHomeIntroAlreadyDone = preferences.getBoolean("first_home_intro_completed", false)
                                preferences.edit()
                                    .putBoolean("onboarding_completed", true)
                                    .putBoolean("first_home_intro_completed", true)
                                    .putString("user_display_name", enteredName)
                                    .apply()
                                userName = enteredName
                                ColdStartTracker.markExecuted()
                                playFirstHomeIntro = !firstHomeIntroAlreadyDone
                                hasCompletedOnboarding = true
                            }
                        )
                    } else if (!skeletonPreviewFinished && !loadFailed && !startupOfflineShown) {
                        Surface(Modifier.fillMaxSize(), color = Color.Black) {}
                    } else if (startupBlocksHome && !loadFailed) {
                        if (startupOfflineShown) {
                            StartupLoadingScreen(
                                offline = true,
                                onRetry = { com.cagri.real.platform.android.sync.SyncLocal.retryStartup() },
                                onUseLocal = { com.cagri.real.platform.android.sync.SyncLocal.startup.allowOffline() },
                                categoryCount = skeletonCategoryCount
                            )
                        } else Surface(Modifier.fillMaxSize(), color = Color.Black) {}
                    } else if (!ready) {
                        Surface(Modifier.fillMaxSize(), color = Color.Black) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (loadFailed) TextButton(onClick = { lifecycleScope.launch {
                                    if (perform(reloadRelated = true)) loadedStartupRevision = startup.revision
                                } }) {
                                    Text(t("Notlar açılamadı. Yeniden dene"))
                                } else com.cagri.real.ui.components.RealSpinner()
                            }
                        }
                    } else {
                        ReminderScreen(
                            reminders = visibleReminders,
                            dividers = visibleDividers,
                            onAddDivider = { categoryId, title -> mutate { repository.addDivider(categoryId, title) } },
                            onEditDivider = { id, title -> mutate { repository.setDividerTitle(id, title) } },
                            onDeleteDivider = { id -> mutate { repository.delete(id) } },
                            onSetCompletionNote = { id, note -> mutate { repository.setCompletionNote(id, note) } },
                            onSetCheckRequest = { ids, request -> mutate { repository.setCheckRequest(ids, request) } },
                            onAddReminder = { text, alertAt, repeatRule, customCategoryId, isChecklist, checklistItems, sendImmediateNotification, referenceUrl, attachments, voiceRecordings, location, plannedDateEpochDay, quick24HourUntilAt, musicBlocks, mediaBlocks ->
                                var createdId: Long? = null
                                val success = perform(warnIfBlocked = true) {
                                    val newest = repository.add(
                                        text = text,
                                        alertAt = alertAt,
                                        plannedDateEpochDay = plannedDateEpochDay,
                                        quick24HourUntilAt = quick24HourUntilAt,
                                        repeatRule = repeatRule,
                                        customCategoryId = customCategoryId,
                                        isChecklist = isChecklist,
                                        checklistItems = checklistItems,
                                        sendImmediateNotification = sendImmediateNotification,
                                        referenceUrl = referenceUrl,
                                        attachments = attachments,
                                        voiceRecordings = voiceRecordings,
                                        musicBlocks = musicBlocks,
                                        mediaBlocks = mediaBlocks,
                                        location = location
                                    )
                                    createdId = newest.id
                                    if (alertAt == null && canShowImmediateNotification(newest) && !ReminderNotifications.show(this@MainActivity, newest)) waiting.add(newest.id)
                                }
                                if (success) createdId else null
                            },
                            onAddSuggestedReal = { text, musicBlock, mediaBlock, cardStackBlock ->
                                var createdId: Long? = null
                                val success = perform(warnIfBlocked = true) {
                                    val newest = repository.add(
                                        text = text,
                                        alertAt = null,
                                        repeatRule = RecurrenceRule.NONE,
                                        customCategoryId = null,
                                        isChecklist = false,
                                        checklistItems = emptyList(),
                                        sendImmediateNotification = false,
                                        referenceUrl = null,
                                        attachments = emptyList(),
                                        voiceRecordings = emptyList(),
                                        musicBlocks = musicBlock?.let { listOf(it) } ?: emptyList(),
                                        mediaBlocks = mediaBlock?.let { listOf(it) } ?: emptyList(),
                                        cardStack = cardStackBlock,
                                        location = null
                                    )
                                    createdId = newest.id
                                }
                                if (success) createdId else null
                            },
                            onAddGame = { game ->
                                var createdId: Long? = null
                                val success = perform(warnIfBlocked = true) {
                                    createdId = repository.add(text = game.title, sendImmediateNotification = false,
                                        gameBlocks = listOf(game)).id
                                }
                                if (success) createdId else null
                            },
                            onAddGameBlock = { reminderId, block ->
                                mutate { repository.addGameBlock(reminderId, block) }
                            },
                            onRemoveGameBlock = { reminderId, blockId ->
                                mutate { repository.removeGameBlock(reminderId, blockId) }
                            },
                            onUndoSuggestedReal = { id ->
                                perform {
                                    val paths = repository.getAttachmentPathsForReminders(setOf(id))
                                    val voicePaths = repository.getVoiceRecordingPathsForReminders(setOf(id))
                                    repository.delete(id)
                                    AttachmentStorage.deleteAttachmentFilePaths(
                                        this@MainActivity,
                                        paths.filterNot { it in repository.getAllAttachmentPaths() }
                                    )
                                    VoiceRecordingStorage.deleteRecordingPaths(
                                        this@MainActivity,
                                        voicePaths.filterNot { it in repository.getAllVoiceRecordingPaths() }
                                    )
                                    ReminderNotifications.cancel(this@MainActivity, id)
                                    waiting.remove(id)
                                }
                            },
                            onToggleChecklistItem = { reminderId, itemId ->
                                mutate {
                                    repository.toggleChecklistItem(reminderId, itemId)
                                }
                            },
                            onToggleCompleted = { id -> mutate {
                                repository.toggleCompleted(id)
                                ReminderNotifications.cancel(this@MainActivity, id)
                                waiting.remove(id)
                            } },
                            onToggleImportant = { id -> mutate {
                                val item = repository.toggleImportant(id).firstOrNull { it.id == id }
                                if (item?.shouldPinNotification == true) ReminderNotifications.show(this@MainActivity, item)
                                else { ReminderNotifications.cancel(this@MainActivity, id); waiting.remove(id) }
                            } },
                            onMoveTo24Hour = { id -> perform {
                                repository.moveTo24Hour(id)
                                ReminderNotifications.cancel(this@MainActivity, id)
                                waiting.remove(id)
                            } },
                            onEditReminder = { id, text, accentStart, accentEnd, customCategoryId, checklistItems, starBorderColorIndex, gradientBlendFraction, customSolidColorArgb, attachments, voiceRecordings, location, referenceUrl, musicBlocks, mediaBlocks, cardStack, contentBlockOrder, contentBlockLayout, useDarkText -> perform {
                                val before = repository.load().firstOrNull { it.id == id } ?: error("Hatırlatıcı artık mevcut değil")
                                val updated = repository.edit(id, text, accentStart, accentEnd, customCategoryId, checklistItems = checklistItems,
                                    starBorderColorIndex = starBorderColorIndex, gradientBlendFraction = gradientBlendFraction,
                                    customSolidColorArgb = customSolidColorArgb, attachments = attachments, voiceRecordings = voiceRecordings, musicBlocks = musicBlocks,
                                    mediaBlocks = mediaBlocks,
                                    cardStack = cardStack, updateCardStack = true,
                                    location = location, updateLocation = true,
                                    referenceUrl = referenceUrl, updateReferenceUrl = true,
                                    contentBlockOrder = contentBlockOrder,
                                    contentBlockLayout = contentBlockLayout,
                                    useDarkText = useDarkText).firstOrNull { it.id == id }

                                val usedPaths = repository.getAllAttachmentPaths()
                                AttachmentStorage.deleteAttachmentFilePaths(this@MainActivity, before.attachments.map { it.localPath }.filterNot { it in usedPaths })
                                val usedVoicePaths = repository.getAllVoiceRecordingPaths()
                                VoiceRecordingStorage.deleteRecordingPaths(this@MainActivity, before.voiceRecordings.map { it.localPath }.filterNot { it in usedVoicePaths })
                                updated?.let { runCatching { ReminderNotifications.refreshEdited(this@MainActivity, it) } }
                            } },
                            onSetAlert = { id, at, repeatRule -> mutate {
                                val item = repository.setAlert(id, at, repeatRule).firstOrNull { it.id == id }
                                ReminderNotifications.cancel(this@MainActivity, id)
                                if (item != null && (at == null || item.shouldPinNotification)) ReminderNotifications.show(this@MainActivity, item)
                            } },
                            onSetRepeatPattern = { id, weekdaysMask, untilEpochDay -> mutate {
                                val item = repository.setRepeatPattern(id, weekdaysMask, untilEpochDay).firstOrNull { it.id == id }
                                item?.let { runCatching { ReminderNotifications.refreshEdited(this@MainActivity, it) } }
                            } },
                            onVoiceEdit = { id, patch -> mutate {
                                val item = repository.applyEdit(id, patch).firstOrNull { it.id == id }
                                item?.let { runCatching { ReminderNotifications.refreshEdited(this@MainActivity, it) } }
                            } },
                            onVoiceSkipOccurrence = { id, occurrenceEpochDay ->
                                var outcome: SkipOccurrenceResult? = null
                                perform {
                                    val before = repository.load().firstOrNull { it.id == id }
                                    val occurrenceMillis = occurrenceEpochDay?.let {
                                        java.time.LocalDate.ofEpochDay(it).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    }
                                    val result = repository.skipNextOccurrence(id, occurrenceMillis = occurrenceMillis)
                                    outcome = result
                                    // A later day can be skipped without touching the upcoming alarm: only reset the
                                    // notification when the alarm actually moved or the series ended.
                                    val alarmMoved = result.item?.alertAt != before?.alertAt
                                    if (result.status == SkipStatus.SERIES_ENDED || (result.status == SkipStatus.SKIPPED && alarmMoved)) {
                                        ReminderNotifications.cancel(this@MainActivity, id)
                                        waiting.remove(id)
                                    }
                                    if (result.status == SkipStatus.SKIPPED && alarmMoved) {
                                        result.item?.let { runCatching { ReminderNotifications.refreshEdited(this@MainActivity, it) } }
                                    }
                                }
                                outcome
                            },
                            onVoiceAddChecklistItems = { id, texts ->
                                var outcome: AddChecklistResult? = null
                                perform { outcome = repository.addChecklistItems(id, texts) }
                                outcome
                            },
                            onVoiceBulkShift = { changes -> mutate {
                                val ids = changes.map { it.id }.toSet()
                                repository.shiftMany(changes).filter { it.id in ids }.forEach { item ->
                                    runCatching { ReminderNotifications.refreshEdited(this@MainActivity, item) }
                                }
                            } },
                            onSetJournal = { id, mood, text -> mutate {
                                repository.setJournal(id, mood, text)
                            } },
                            onDeleteJournal = { id -> mutate {
                                repository.deleteJournal(id)
                            } },
                            onSetMood = { id, mood, details -> mutate {
                                repository.setMood(id, mood, details)
                            } },
                            homeMoodEntries = moodTrackerEntries,
                            onAddHomeMood = { mood, details ->
                                moodTrackerEntries = moodTrackerRepository.add(mood, details)
                            },
                            onClearAllMoods = {
                                lifecycleScope.launch {
                                    val success = perform {
                                        repository.clearAllMoods()
                                        moodTrackerRepository.clearAll()
                                    }
                                    if (success) moodTrackerEntries = emptyList()
                                }
                            },
                            onDeleteReminder = { id -> requestDelete(setOf(id)) },
                            onDeleteReminderOccurrence = { id, occurrenceMillis, scope -> mutate {
                                val before = repository.load().firstOrNull { it.id == id }
                                val result = repository.deleteRepeatOccurrence(id, occurrenceMillis, scope)
                                when (result.status) {
                                    RepeatDeleteStatus.OCCURRENCE_REMOVED -> result.item?.let {
                                        // The next alarm moved off the deleted day: drop any stale notification/wait entry.
                                        if (before?.alertAt != it.alertAt) {
                                            ReminderNotifications.cancel(this@MainActivity, id); waiting.remove(id)
                                        }
                                        runCatching { ReminderNotifications.refreshEdited(this@MainActivity, it) }
                                    }
                                    RepeatDeleteStatus.SERIES_ENDED -> {
                                        ReminderNotifications.cancel(this@MainActivity, id); waiting.remove(id)
                                        result.item?.let { runCatching { ReminderNotifications.refreshEdited(this@MainActivity, it) } }
                                    }
                                    RepeatDeleteStatus.SERIES_DELETED -> {
                                        // Nothing of the series survives: same cleanup as a normal card delete.
                                        before?.let { gone ->
                                            val paths = gone.attachments.map { it.localPath }
                                            val voicePaths = gone.voiceRecordings.map { it.localPath }
                                            AttachmentStorage.deleteAttachmentFilePaths(this@MainActivity, paths.filterNot { it in repository.getAllAttachmentPaths() })
                                            VoiceRecordingStorage.deleteRecordingPaths(this@MainActivity, voicePaths.filterNot { it in repository.getAllVoiceRecordingPaths() })
                                        }
                                        ReminderNotifications.cancel(this@MainActivity, id); waiting.remove(id)
                                    }
                                    RepeatDeleteStatus.NOT_REPEATING -> {
                                        // Not a repeating card after all (e.g. edited elsewhere): fall back to the plain delete.
                                        repository.delete(id)
                                        before?.let { gone ->
                                            val paths = gone.attachments.map { it.localPath }
                                            val voicePaths = gone.voiceRecordings.map { it.localPath }
                                            AttachmentStorage.deleteAttachmentFilePaths(this@MainActivity, paths.filterNot { it in repository.getAllAttachmentPaths() })
                                            VoiceRecordingStorage.deleteRecordingPaths(this@MainActivity, voicePaths.filterNot { it in repository.getAllVoiceRecordingPaths() })
                                        }
                                        ReminderNotifications.cancel(this@MainActivity, id); waiting.remove(id)
                                    }
                                    RepeatDeleteStatus.NOT_FOUND -> Unit
                                }
                            } },
                            onDeleteReminders = { ids -> requestDelete(ids) },
                            onBulkSetCategory = { ids, catId -> mutate {
                                repository.setCustomCategoryForReminders(ids, catId)
                            } },
                            requestedReminderId = openedReminderId,
                            onRequestHandled = { openedReminderId = null; intent.removeExtra(ReminderNotifications.EXTRA_REMINDER_ID) },
                            reduceDynamicBlur = reduceBlur,
                            onReduceDynamicBlurChange = { reduceBlur = it; preferences.edit().putBoolean("reduce_dynamic_blur", it).apply() },
                            reduceAnimations = reduceAnimations,
                            onReduceAnimationsChange = { reduceAnimations = it; preferences.edit().putBoolean("reduce_animations", it).apply() },
                            realPetEnabled = false,
                            onRealPetEnabledChange = {
                                // Force disabled
                            },
                            onRealPetSurfaceActiveChange = { realPetSurfaceActive = it },
                            designStyle = style,
                            onDesignStyleChange = { _ -> /* REAL uses Liquid Glass as its only design style. */ },
                            exactAlarmsAllowed = exactAllowed,
                            onRequestExactAlarms = ::openAlarmAccess,
                            customCategories = customCategories,
                            onCreateCategory = { name, iconKey, start, end, hide, useDarkText, blendFraction, customSolidColorArgb, description ->
                                mutateCategory(t("Kategori oluşturulamadı.")) {
                                    val categories = categoryRepository.add(
                                        name, iconKey, start, end, hide, useDarkText,
                                        blendFraction, customSolidColorArgb
                                    )
                                    val newCategory = categories.maxByOrNull { it.createdAt }
                                    if (newCategory != null) {
                                        val key = categoryDescriptionKey(null, newCategory)
                                        saveCategoryDescription(this@MainActivity, key, description)
                                    }
                                }
                            },
                            onCreateVoiceCategory = { name, iconKey, colorIndex ->
                                createVoiceCategory(name, iconKey, colorIndex)
                            },
                            onEditCategory = { id, name, iconKey, start, end, hide, useDarkText, blendFraction, customSolidColorArgb ->
                                mutateCategory(t("Kategori güncellenemedi.")) {
                                    categoryRepository.update(
                                        id, name, iconKey, start, end, hide, useDarkText,
                                        blendFraction, customSolidColorArgb
                                    )
                                }
                            },
                            onDeleteCategory = { id, deleteReminders ->
                                mutateCategory(t("Kategori silinemedi. Yeniden deneyebilirsin.")) {
                                    deleteCategoryWithRollback(id, repository, categoryRepository, deleteReminders)
                                }
                            },
                            onDeleteCategories = { ids ->
                                mutateCategory(t("Kategoriler silinemedi. Yeniden deneyebilirsin.")) {
                                    deleteCategoriesWithRollback(ids, repository, categoryRepository)
                                }
                            },
                            builtInCategoryColors = builtInCategoryColors,
                            onUpdateBuiltInCategoryColor = { cat, override ->
                                builtInCategoryColors = categoryRepository.setBuiltInCategoryColor(cat, override)
                            },
                            pendingPanoBoardId = pendingPanoBoardId,
                            onPanoLocationHandled = { pendingPanoBoardId = null },
                            pendingLocationShare = pendingLocationShare,
                            onLocationShareHandled = { pendingLocationShare = null },
                            onRequestLocationShare = ::openMapsForLocation,
                            onResolveLocationPreview = ::resolveLocationPreview,
                            pendingShareDraft = pendingShareDraft,
                            onShareDraftHandled = { pendingShareDraft = null },
                            userName = userName.ifBlank { authUser?.displayName.orEmpty() },
                            notificationsAllowed = notificationsAllowed,
                            onRequestNotifications = ::openNotificationSettings,
                            onRequestNotificationPermission = ::requestNotificationPermission,
                            isFirstHomeIntro = playFirstHomeIntro,
                            onFirstHomeIntroComplete = {
                                preferences.edit().putBoolean("first_home_intro_completed", true).apply()
                                playFirstHomeIntro = false
                            }
                        )
                    }
                }

                    if (!showWelcome && !loadFailed && !startupOfflineShown) {
                        key(localWorkspaceToken) {
                            StartupSkeletonReveal(
                                readyToReveal = skeletonPreviewFinished && !startupBlocksHome && ready &&
                                    startup.workspace == localWorkspaceToken,
                                categoryCount = skeletonCategoryCount,
                                reduceMotion = reduceAnimations
                            )
                        }
                    }

                    com.cagri.real.ui.ai.AiConsentHost(authUser?.id)
                    com.cagri.real.updates.RequiredUpdateHost(authUser?.id)
                    com.cagri.real.ui.subscription.ProAccessHost(authUser?.id)
                    com.cagri.real.ui.account.CloudSpaceNoticeHost(authUser?.id)
                    com.cagri.real.ui.auth.SignInRequiredHost()

                    if (!showWelcome && !startupBlocksHome && ready && realPetEnabled) {
                        com.cagri.real.ui.components.RealPetLayer(
                            reduceAnimations = reduceAnimations,
                            surfaceActive = realPetSurfaceActive,
                            onCreatePersistentCard = { petText ->
                                mutate {
                                    repository.add(
                                        text = petText,
                                        sendImmediateNotification = false
                                    )
                                }
                            },
                            reminders = visibleReminders,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Topmost: REAL's own toast replaces the system Toast while this screen is resumed.
                    com.cagri.real.ui.components.RealToastHost()
                }
                }
            }
        }
    }

    override fun onStop() {
        // Leaving REAL ends the undo window: make the pending delete final now.
        commitPendingDeletes()
        super.onStop()
    }

    private fun requestDelete(ids: Set<Long>) {
        if (ids.isEmpty()) return
        // Only one undo at a time: an earlier pending batch becomes final first.
        commitPendingDeletes()
        pendingDeleteIds = pendingDeleteIds + ids
        val label = if (ids.size == 1) "Kart silindi" else "${ids.size} kart silindi"
        val undoable = com.cagri.real.ui.components.RealToast.showAction(this, label, t("Geri al"), UNDO_WINDOW_MS) {
            if (pendingDeleteIds.containsAll(ids)) {
                pendingDeleteJob?.cancel(); pendingDeleteJob = null
                pendingDeleteIds = pendingDeleteIds - ids
            }
        }
        if (!undoable) { commitPendingDeletes(); return }
        pendingDeleteJob = lifecycleScope.launch {
            delay(UNDO_WINDOW_MS + 150)
            commitPendingDeletes()
        }
    }

    private fun commitPendingDeletes() {
        val ids = pendingDeleteIds
        if (ids.isEmpty()) return
        pendingDeleteJob?.cancel(); pendingDeleteJob = null
        lifecycleScope.launch {
            perform { deleteRemindersNow(ids) }
            // Unhide only after the reload, so the cards never flash back.
            pendingDeleteIds = pendingDeleteIds - ids
        }
    }

    /** The original delete: cards, then attachment/voice files no other card uses, then notifications. */
    private fun deleteRemindersNow(ids: Set<Long>) {
        val paths = repository.getAttachmentPathsForReminders(ids)
        val voicePaths = repository.getVoiceRecordingPathsForReminders(ids)
        ids.forEach { id ->
            ReminderNotifications.cancel(this@MainActivity, id)
            waiting.remove(id)
        }
        repository.deleteAll(ids)
        AttachmentStorage.deleteAttachmentFilePaths(this@MainActivity, paths.filterNot { it in repository.getAllAttachmentPaths() })
        VoiceRecordingStorage.deleteRecordingPaths(this@MainActivity, voicePaths.filterNot { it in repository.getAllVoiceRecordingPaths() })
    }

    override fun onResume() {
        super.onResume()
        com.cagri.real.i18n.I18n.refresh(this)
        if (::repository.isInitialized)
            com.cagri.real.platform.android.content.PendingContentDestination.returnedWithoutResult(this)
        exactAllowed = ReminderScheduler.canScheduleExact(this)
        notificationsAllowed = NotificationManagerCompat.from(this).areNotificationsEnabled()
        if (::repository.isInitialized) lifecycleScope.launch { perform(reloadRelated = true) }
        com.cagri.real.sharedcategories.SharedCategorySync.request(this, 300)
        // Profile picture for connected people (only uploads when it, or the photo-sharing switch, changed).
        lifecycleScope.launch { com.cagri.real.platform.android.connections.ProfilePictures.push(applicationContext) }
        // Direct Share: category shortcuts in the share menu (Ayarlar → Paylaş menüsü kısayolları).
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                com.cagri.real.platform.android.content.ShareShortcuts.publish(this@MainActivity,
                    CategoryRepository(this@MainActivity).load(), ReminderRepository(this@MainActivity).load())
            }
        }
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        try { startActivity(intent) }
        catch (_: Exception) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        openedReminderId = intent.getLongExtra(ReminderNotifications.EXTRA_REMINDER_ID, -1L).takeIf { it > 0 }
        // Pano push: tapping a board notification opens that board.
        intent.getStringExtra(com.cagri.real.platform.android.boards.PanoPush.EXTRA_BOARD)?.let { pendingPanoBoardId = it; intent.removeExtra(com.cagri.real.platform.android.boards.PanoPush.EXTRA_BOARD) }
        if (::repository.isInitialized) handleShareIntent(intent)
        // While storage starts, openRealContent consumes the latest Intent after awaiting it.
    }

    private fun openMapsForLocation(reminderId: Long) {
        if (reminders.none { it.id == reminderId }) return
        com.cagri.real.platform.android.location.RealLocationShareFlow.startReminder(this, reminderId)
    }

    private fun resolveLocationPreview(reminderId: Long) {
        val existing = reminders.firstOrNull { it.id == reminderId }?.location ?: return
        val url = existing.mapsUrl ?: return
        if (existing.latitude != null || !attemptedLocationPreviews.add(reminderId to url)) return
        lifecycleScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                parseAndResolveSharedLocation("${existing.placeName}\n${existing.address}\n$url")
            }
            if (resolved?.latitude != null && resolved.longitude != null) {
                mutate { repository.enrichLocationCoordinates(reminderId, url, resolved) }
            }
        }
    }

    




    private fun parseAndResolveSharedLocation(text: String): ReminderLocation? {
        val parsed = GoogleMapsShareParser.parse(text) ?: return null
        if (parsed.latitude != null && parsed.longitude != null) return parsed
        return geocodeSharedPlace(parsed)
    }

    @Suppress("DEPRECATION")
    private fun geocodeSharedPlace(location: ReminderLocation): ReminderLocation {
        if (!Geocoder.isPresent()) return location
        val name = location.placeName.trim().takeUnless {
            it.isBlank() || it.equals("Google Haritalar konumu", ignoreCase = true)
        }
        val address = location.address.trim().takeIf { it.isNotBlank() }
        val query = listOfNotNull(name, address).distinct().joinToString(", ")
        if (query.isBlank()) return location

        return try {
            val geocoder = Geocoder(applicationContext, Locale.getDefault())
            // A voice place is "When Kitchen, Ordu": when the venue itself is unknown, at least show the city.
            val match = geocoder.getFromLocationName(query, 1)?.firstOrNull()
                ?: query.substringAfterLast(',', "").trim().takeIf { it.length >= 2 }
                    ?.let { city -> geocoder.getFromLocationName(city, 1)?.firstOrNull() }
                ?: return location
            if (!match.latitude.isFinite() || !match.longitude.isFinite() ||
                match.latitude !in -90.0..90.0 || match.longitude !in -180.0..180.0) return location
            location.copy(
                latitude = match.latitude,
                longitude = match.longitude,
                approximateCoordinates = true
            )
        } catch (_: Exception) {
            location
        }
    }

    private fun handleShareIntent(intent: Intent?) {
        val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true)
            intent.getStringExtra(Intent.EXTRA_TEXT) else null
        val mapsUrl = GoogleMapsShareParser.sharedUrl(sharedText)
        if (mapsUrl != null) {
            val hadPending = com.cagri.real.platform.android.content.PendingContentDestination.hasPending(this)
            val pendingRequest = com.cagri.real.platform.android.content.PendingContentDestination.consume(this)
            val currentUid = com.cagri.real.platform.android.auth.AndroidBackend.auth.currentUser()?.id ?: "guest"
            val route = com.cagri.real.content.ContentRouting.sharedResult(hadPending, pendingRequest,
                System.currentTimeMillis(), currentUid, "LOCATION")
            if (route == com.cagri.real.content.ContentRouteResult.ChooseCard) {
                startActivity(Intent(intent).setClass(this, AttachmentImportActivity::class.java))
                intent?.action = null
                return
            }
            intent?.removeExtra(Intent.EXTRA_TEXT)
            intent?.removeExtra(Intent.EXTRA_SUBJECT)
            intent?.removeExtra(Intent.EXTRA_TITLE)
            intent?.action = null
            if (route !is com.cagri.real.content.ContentRouteResult.Deliver) {
                com.cagri.real.ui.components.RealToast.show(this, t("Konum hedefi artık geçerli değil. REAL’den yeniden konum seç."), long = true)
                return
            }
            val request = route.request
            lifecycleScope.launch {
                try {
                    val location = withContext(Dispatchers.IO) { parseAndResolveSharedLocation(sharedText.orEmpty()) }
                        ?: error("Harita bağlantısı okunamadı")
                    check(request.isCurrent(System.currentTimeMillis(),
                        com.cagri.real.platform.android.auth.AndroidBackend.auth.currentUser()?.id ?: "guest", "LOCATION")) { t("Konum hedefi artık geçerli değil") }
                    when (val destination = request.destination) {
                        is com.cagri.real.content.ContentDestination.Board -> {
                            val user = com.cagri.real.platform.android.auth.AndroidBackend.auth.currentUser()
                            val boards = com.cagri.real.platform.android.boards.BoardRepositories.obtain(applicationContext,
                                request.uid, user?.displayName ?: "REAL user")
                            boards.load()
                            check(boards.state.value.boards.any { it.id == destination.boardId }) { "Board is unavailable" }
                            check(request.uid == (com.cagri.real.platform.android.auth.AndroidBackend.auth.currentUser()?.id ?: "guest")) { "Account changed" }
                            withContext(kotlinx.coroutines.NonCancellable) {
                                boards.add(destination.boardId, com.cagri.real.boards.BoardBlockType.LOCATION, destination.insertion,
                                    com.cagri.real.data.boards.BoardRealContent.locationPayload(location))
                            }
                            pendingPanoBoardId = destination.boardId
                        }
                        is com.cagri.real.content.ContentDestination.Card -> {
                            check(reminders.any { it.id == destination.reminderId }) { "Hatırlatıcı artık mevcut değil" }
                            pendingLocationShare = PendingReminderLocation(destination.reminderId, location)
                        }
                    }
                } catch (c: kotlinx.coroutines.CancellationException) { throw c }
                catch (e: Exception) { com.cagri.real.ui.components.RealToast.show(this@MainActivity, e.message ?: "Konum eklenemedi", long = true) }
            }
            return
        }
        if (intent?.action in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_PROCESS_TEXT)) {
            startActivity(Intent(intent).setClass(this, AttachmentImportActivity::class.java))
            intent?.action = null
        }
    }
    private fun mutate(action: () -> Unit) { lifecycleScope.launch { perform(action = action) } }

    private suspend fun createVoiceCategory(name: String, iconKey: String, colorIndex: Int): CustomCategory? = operations.withLock {
        try {
            val result = withContext(Dispatchers.IO) {
                synchronized(CategoryRepository.mutationLock) {
                    val categories = categoryRepository.load()
                    val existing = com.cagri.real.speech.findVoiceCategory(name, categories)
                    require(existing != null || categories.none {
                        com.cagri.real.speech.voiceNameKey(it.name) == com.cagri.real.speech.voiceNameKey(name)
                    }) { t("Bu kategori adı birden fazla kategoriyle eşleşiyor.") }
                    if (existing != null) {
                        existing to categoryRepository.load()
                    } else {
                        val updated = categoryRepository.add(
                            name = name,
                            iconKey = iconKey,
                            accentStart = colorIndex
                        )
                        updated.lastOrNull { com.cagri.real.speech.voiceNameKey(it.name) == com.cagri.real.speech.voiceNameKey(name) } to updated
                    }
                }
            }
            customCategories = result.second
            result.first
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("Real", "Voice category creation failed", e)
            com.cagri.real.ui.components.RealToast.show(this@MainActivity, t("Kategori oluşturulamadı."), long = true)
            null
        }
    }

    private fun mutateCategory(
        errorMessage: String = t("Kategori işlemi tamamlanamadı."),
        action: () -> Unit
    ) {
        lifecycleScope.launch {
            operations.withLock {
                var reminderSnapshot: List<ReminderItem>? = null
                var categorySnapshot: List<CustomCategory>? = null
                try {
                    val (loadedReminders, loadedCategories) = withContext(Dispatchers.IO) {
                        synchronized(ReminderRepository.mutationLock) {
                            synchronized(CategoryRepository.mutationLock) {
                                reminderSnapshot = repository.load()
                                categorySnapshot = categoryRepository.load()
                                action()
                                val after = repository.load()
                                val categoriesAfter = categoryRepository.load()
                                val usedPaths = after.flatMap { it.attachments }.map { it.localPath }.toSet()
                                AttachmentStorage.deleteAttachmentFilePaths(this@MainActivity,
                                    reminderSnapshot.orEmpty().flatMap { it.attachments }.map { it.localPath }.filterNot { it in usedPaths })
                                val usedVoicePaths = after.flatMap { it.voiceRecordings }.map { it.localPath }.toSet()
                                VoiceRecordingStorage.deleteRecordingPaths(this@MainActivity,
                                    reminderSnapshot.orEmpty().flatMap { it.voiceRecordings }.map { it.localPath }.filterNot { it in usedVoicePaths })
                                after to categoriesAfter
                            }
                        }
                    }
                    reminders = loadedReminders
                    customCategories = loadedCategories
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("Real", "Category operation failed", e)
                    try {
                        withContext(Dispatchers.IO) {
                            synchronized(ReminderRepository.mutationLock) {
                                synchronized(CategoryRepository.mutationLock) {
                                    reminderSnapshot?.let { repository.restore(it) }
                                    categorySnapshot?.let { categoryRepository.restore(it) }
                                }
                            }
                        }
                        reminderSnapshot?.let { reminders = it }
                        categorySnapshot?.let { customCategories = it }
                    } catch (rollbackError: Exception) {
                        android.util.Log.e("Real", "Category rollback failed", rollbackError)
                        try {
                            val (recoveredReminders, recoveredCategories) = withContext(Dispatchers.IO) {
                                synchronized(ReminderRepository.mutationLock) {
                                    synchronized(CategoryRepository.mutationLock) {
                                        repository.load() to categoryRepository.load()
                                    }
                                }
                            }
                            reminders = recoveredReminders
                            customCategories = recoveredCategories
                        } catch (_: Exception) { }
                    }
                    com.cagri.real.ui.components.RealToast.show(this@MainActivity, errorMessage, long = true)
                }
            }
        }
    }

    private data class RelatedSnapshot(
        val categories: List<CustomCategory>,
        val colors: Map<ReminderCategory, CategoryColorOverride>,
        val moods: List<MoodTrackerEntry>
    )
    private data class OperationSnapshot(
        val reminders: List<ReminderItem>, val waiting: Boolean, val related: RelatedSnapshot?
    )
    private suspend fun perform(
        warnIfBlocked: Boolean = false, reloadRelated: Boolean = false, action: (() -> Unit)? = null
    ): Boolean {
        // An app version below the minimum may only look: no card, category or mood change.
        if (action != null && com.cagri.real.updates.AppUpdateGate.blocked()) return false
        val requestedGeneration = refreshGeneration
        return operations.withLock {
        val preferenceRevision = com.cagri.real.platform.android.sync.SyncLocal.preferenceRevision()
        val permissionState = exactAllowed to notificationsAllowed
        if (action == null && reloadRelated && ready && requestedGeneration != refreshGeneration &&
            refreshedPreferenceRevision == preferenceRevision && refreshedPermissionState == permissionState) return@withLock true
        try {
            val result = withContext(Dispatchers.IO) {
                synchronized(ReminderRepository.mutationLock) {
                    if (com.cagri.real.platform.android.sync.SyncLocal.workspaceToken() != localWorkspaceToken)
                        throw CancellationException("Workspace changed")
                    action?.invoke()
                    if (action != null) com.cagri.real.sharedcategories.SharedCategorySync.request(this@MainActivity)
                    ReminderScheduler.reconcile(this@MainActivity, repository)
                    val all = repository.load()
                    ReminderNotifications.restorePinned(this@MainActivity, all)
                    val related = if (reloadRelated) RelatedSnapshot(
                        categoryRepository.load(), categoryRepository.getBuiltInCategoryColors(), moodTrackerRepository.load()
                    ) else null
                    OperationSnapshot(all, waiting.isNotEmpty(), related)
                }
            }
            // Discard a completed old-account read; Activity recreation owns the new workspace.
            if (com.cagri.real.platform.android.sync.SyncLocal.workspaceToken() != localWorkspaceToken) return@withLock false
            reminders = result.reminders
            result.related?.let {
                customCategories = it.categories; builtInCategoryColors = it.colors; moodTrackerEntries = it.moods
            }
            ready = true; loadFailed = false
            if (reloadRelated) {
                refreshGeneration++; refreshedPreferenceRevision = preferenceRevision
                refreshedPermissionState = permissionState
            }
            if (warnIfBlocked && result.waiting) {
                com.cagri.real.ui.components.RealToast.show(this, t("Not kaydedildi. Bildirimleri uygulama ayarlarından açın."), long = true)
                requestNotificationPermission()
            }
            true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            android.util.Log.e("Real", "Reminder operation failed", e)
            if (!ready) loadFailed = true
            com.cagri.real.ui.components.RealToast.show(this, t("İşlem tamamlanamadı. Notlarını silmeden yeniden deneyebilirsin."), long = true)
            false
        }
    }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            openNotificationSettings()
        }
    }
    private fun openAlarmAccess() {
        if (Build.VERSION.SDK_INT >= 31) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + packageName))
            try { startActivity(intent) }
            catch (_: android.content.ActivityNotFoundException) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName))) }
        }
    }
}
