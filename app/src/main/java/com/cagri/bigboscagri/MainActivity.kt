package com.cagri.bigboscagri

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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cagri.bigboscagri.data.*
import com.cagri.bigboscagri.notifications.*
import com.cagri.bigboscagri.ui.*
import com.cagri.bigboscagri.ui.theme.BigBosTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var repository: ReminderRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var moodTrackerRepository: MoodTrackerRepository
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
    private var pendingLocationShare by mutableStateOf<PendingReminderLocation?>(null)
    private val attemptedLocationPreviews = mutableSetOf<Pair<Long, String>>()
    private val operations = Mutex()
    private val waiting = mutableSetOf<Long>() 
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK))
        if (Build.VERSION.SDK_INT >= 29) window.isStatusBarContrastEnforced = false
        repository = ReminderRepository(applicationContext)
        categoryRepository = CategoryRepository(applicationContext)
        moodTrackerRepository = MoodTrackerRepository(applicationContext)
        reminders = try { repository.load() } catch (_: Exception) { emptyList() }
        customCategories = try { categoryRepository.load() } catch (_: Exception) { emptyList() }
        builtInCategoryColors = try { categoryRepository.getBuiltInCategoryColors() } catch (_: Exception) { emptyMap() }
        moodTrackerEntries = try { moodTrackerRepository.load() } catch (_: Exception) { emptyList() }
        ready = true
        exactAllowed = ReminderScheduler.canScheduleExact(this)
        notificationsAllowed = NotificationManagerCompat.from(this).areNotificationsEnabled()
        com.cagri.bigboscagri.ui.components.prewarmRealWordmark(applicationContext)
        openedReminderId = intent.getLongExtra(ReminderNotifications.EXTRA_REMINDER_ID, -1L).takeIf { it > 0 }
        handleShareIntent(intent)
        val preferences = getSharedPreferences("bigbos_ui", MODE_PRIVATE)
        if (preferences.getBoolean("onboarding_completed", false) && !preferences.contains("first_home_intro_completed")) {
            preferences.edit().putBoolean("first_home_intro_completed", true).apply()
        }
        lifecycleScope.launch {
            ReminderRepository.updates.collect {
                perform()
            }
        }
        setContent {
            BigBosTheme {
                var reduceBlur by remember { mutableStateOf(preferences.getBoolean("reduce_dynamic_blur", false)) }
                var reduceAnimations by remember { mutableStateOf(preferences.getBoolean("reduce_animations", false)) }
                var realPetEnabled by remember { mutableStateOf(preferences.getBoolean("real_pet_enabled", false)) }
                var style by remember { mutableStateOf(DesignStyle.fromPreference(preferences.getString("design_style", "liquid_glass"))) }
                var hasCompletedOnboarding by remember { mutableStateOf(preferences.getBoolean("onboarding_completed", false)) }
                var userName by remember { mutableStateOf(preferences.getString("user_display_name", "") ?: "") }
                var playFirstHomeIntro by remember { mutableStateOf(false) }

                androidx.compose.runtime.CompositionLocalProvider(
                    com.cagri.bigboscagri.ui.components.LocalDesignStyle provides style,
                    com.cagri.bigboscagri.ui.components.LocalLiquidReduceMotion provides reduceAnimations
                ) {
                Box(Modifier.fillMaxSize()) {
                    Crossfade(
                    targetState = hasCompletedOnboarding,
                    animationSpec = if (reduceAnimations) snap() else tween(240),
                    label = "onboardingCrossfade"
                ) { completed ->
                    if (!completed) {
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
                    } else if (!ready) {
                        Surface(Modifier.fillMaxSize(), color = Color.Black) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (loadFailed) TextButton(onClick = { lifecycleScope.launch { perform() } }) {
                                    Text("Notlar açılamadı. Yeniden dene")
                                } else CircularProgressIndicator()
                            }
                        }
                    } else {
                        ReminderScreen(
                            reminders = reminders,
                            onAddReminder = { text, alertAt, repeatRule, customCategoryId, isChecklist, checklistItems, sendImmediateNotification, referenceUrl, attachments, voiceRecordings, location ->
                                var createdId: Long? = null
                                val success = perform(warnIfBlocked = true) {
                                    val newest = repository.add(
                                        text = text,
                                        alertAt = alertAt,
                                        repeatRule = repeatRule,
                                        customCategoryId = customCategoryId,
                                        isChecklist = isChecklist,
                                        checklistItems = checklistItems,
                                        sendImmediateNotification = sendImmediateNotification,
                                        referenceUrl = referenceUrl,
                                        attachments = attachments,
                                        voiceRecordings = voiceRecordings,
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
                            onEditReminder = { id, text, accentStart, accentEnd, customCategoryId, checklistItems, starBorderColorIndex, gradientBlendFraction, customSolidColorArgb, attachments, voiceRecordings, location, referenceUrl, musicBlocks, mediaBlocks, cardStack, contentBlockOrder -> perform {
                                val before = repository.load().firstOrNull { it.id == id } ?: error("Hatırlatıcı artık mevcut değil")
                                val updated = repository.edit(id, text, accentStart, accentEnd, customCategoryId, checklistItems = checklistItems,
                                    starBorderColorIndex = starBorderColorIndex, gradientBlendFraction = gradientBlendFraction,
                                    customSolidColorArgb = customSolidColorArgb, attachments = attachments, voiceRecordings = voiceRecordings, musicBlocks = musicBlocks,
                                    mediaBlocks = mediaBlocks,
                                    cardStack = cardStack, updateCardStack = true,
                                    location = location, updateLocation = true,
                                    referenceUrl = referenceUrl, updateReferenceUrl = true,
                                    contentBlockOrder = contentBlockOrder).firstOrNull { it.id == id }

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
                            onDeleteReminder = { id -> mutate {
                                val paths = repository.getAttachmentPathsForReminders(setOf(id))
                                val voicePaths = repository.getVoiceRecordingPathsForReminders(setOf(id))
                                repository.delete(id)
                                AttachmentStorage.deleteAttachmentFilePaths(this@MainActivity, paths.filterNot { it in repository.getAllAttachmentPaths() })
                                VoiceRecordingStorage.deleteRecordingPaths(this@MainActivity, voicePaths.filterNot { it in repository.getAllVoiceRecordingPaths() })
                                ReminderNotifications.cancel(this@MainActivity, id); waiting.remove(id)
                            } },
                            onDeleteReminders = { ids -> mutate {
                                val paths = repository.getAttachmentPathsForReminders(ids)
                                val voicePaths = repository.getVoiceRecordingPathsForReminders(ids)
                                ids.forEach { id ->
                                    ReminderNotifications.cancel(this@MainActivity, id)
                                    waiting.remove(id)
                                }
                                repository.deleteAll(ids)
                                AttachmentStorage.deleteAttachmentFilePaths(this@MainActivity, paths.filterNot { it in repository.getAllAttachmentPaths() })
                                VoiceRecordingStorage.deleteRecordingPaths(this@MainActivity, voicePaths.filterNot { it in repository.getAllVoiceRecordingPaths() })
                            } },
                            onBulkSetCategory = { ids, catId -> mutate {
                                repository.setCustomCategoryForReminders(ids, catId)
                            } },
                            requestedReminderId = openedReminderId,
                            onRequestHandled = { openedReminderId = null; intent.removeExtra(ReminderNotifications.EXTRA_REMINDER_ID) },
                            reduceDynamicBlur = reduceBlur,
                            onReduceDynamicBlurChange = { reduceBlur = it; preferences.edit().putBoolean("reduce_dynamic_blur", it).apply() },
                            reduceAnimations = reduceAnimations,
                            onReduceAnimationsChange = { reduceAnimations = it; preferences.edit().putBoolean("reduce_animations", it).apply() },
                            realPetEnabled = realPetEnabled,
                            onRealPetEnabledChange = {
                                realPetEnabled = it
                                preferences.edit().putBoolean("real_pet_enabled", it).apply()
                            },
                            designStyle = style,
                            onDesignStyleChange = { style = it; preferences.edit().putString("design_style", it.preferenceKey).apply() },
                            exactAlarmsAllowed = exactAllowed,
                            onRequestExactAlarms = ::openAlarmAccess,
                            customCategories = customCategories,
                            onCreateCategory = { name, iconKey, start, end, hide, useDarkText, blendFraction, customSolidColorArgb ->
                                mutateCategory("Kategori oluşturulamadı.") {
                                    categoryRepository.add(
                                        name, iconKey, start, end, hide, useDarkText,
                                        blendFraction, customSolidColorArgb
                                    )
                                }
                            },
                            onEditCategory = { id, name, iconKey, start, end, hide, useDarkText, blendFraction, customSolidColorArgb ->
                                mutateCategory("Kategori güncellenemedi.") {
                                    categoryRepository.update(
                                        id, name, iconKey, start, end, hide, useDarkText,
                                        blendFraction, customSolidColorArgb
                                    )
                                }
                            },
                            onDeleteCategory = { id, deleteReminders ->
                                mutateCategory("Kategori silinemedi. Yeniden deneyebilirsin.") {
                                    deleteCategoryWithRollback(id, repository, categoryRepository, deleteReminders)
                                }
                            },
                            onDeleteCategories = { ids ->
                                mutateCategory("Kategoriler silinemedi. Yeniden deneyebilirsin.") {
                                    deleteCategoriesWithRollback(ids, repository, categoryRepository)
                                }
                            },
                            builtInCategoryColors = builtInCategoryColors,
                            onUpdateBuiltInCategoryColor = { cat, override ->
                                builtInCategoryColors = categoryRepository.setBuiltInCategoryColor(cat, override)
                            },
                            pendingLocationShare = pendingLocationShare,
                            onLocationShareHandled = { pendingLocationShare = null },
                            onRequestLocationShare = ::openMapsForLocation,
                            onResolveLocationPreview = ::resolveLocationPreview,
                            pendingShareDraft = pendingShareDraft,
                            onShareDraftHandled = { pendingShareDraft = null },
                            userName = userName,
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

                    if (hasCompletedOnboarding && ready && realPetEnabled) {
                        com.cagri.bigboscagri.ui.components.RealPetLayer(
                            reduceAnimations = reduceAnimations,
                            onCreatePersistentCard = { petText ->
                                mutate {
                                    repository.add(
                                        text = petText,
                                        sendImmediateNotification = false
                                    )
                                }
                            },
                            reminders = reminders,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        exactAllowed = ReminderScheduler.canScheduleExact(this)
        notificationsAllowed = NotificationManagerCompat.from(this).areNotificationsEnabled()
        if (::repository.isInitialized) lifecycleScope.launch { perform() }
        if (::categoryRepository.isInitialized) {
            customCategories = try { categoryRepository.load() } catch (_: Exception) { emptyList() }
            builtInCategoryColors = try { categoryRepository.getBuiltInCategoryColors() } catch (_: Exception) { emptyMap() }
        }
        if (::moodTrackerRepository.isInitialized) {
            moodTrackerEntries = try { moodTrackerRepository.load() } catch (_: Exception) { emptyList() }
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
        handleShareIntent(intent)
    }

    private fun openMapsForLocation(reminderId: Long) {
        if (reminders.none { it.id == reminderId }) return
        val prefs = getSharedPreferences("real_pending_location", MODE_PRIVATE)
        
        if (!prefs.edit().putLong("reminder_id", reminderId)
                .putLong("started_at", System.currentTimeMillis()).commit()) {
            Toast.makeText(this, "Konum seçimi başlatılamadı", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps"))
                .setPackage("com.google.android.apps.maps"))
        } catch (_: Exception) {
            prefs.edit().clear().apply()
            Toast.makeText(this, "Google Haritalar açılamadı", Toast.LENGTH_SHORT).show()
        }
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
            val match = Geocoder(applicationContext, Locale.getDefault())
                .getFromLocationName(query, 1)
                ?.firstOrNull()
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
            val prefs = getSharedPreferences("real_pending_location", MODE_PRIVATE)
            val reminderId = prefs.getLong("reminder_id", -1L)
            val startedAt = prefs.getLong("started_at", 0L)
            prefs.edit().clear().apply()
            intent?.removeExtra(Intent.EXTRA_TEXT)
            intent?.removeExtra(Intent.EXTRA_SUBJECT)
            intent?.removeExtra(Intent.EXTRA_TITLE)
            if (reminderId > 0 && System.currentTimeMillis() - startedAt in 0..3_600_000L &&
                reminders.any { it.id == reminderId }) {
                lifecycleScope.launch {
                    val location = withContext(Dispatchers.IO) { parseAndResolveSharedLocation(sharedText.orEmpty()) }
                    if (location != null) pendingLocationShare = PendingReminderLocation(reminderId, location)
                    else Toast.makeText(this@MainActivity, "Harita bağlantısı okunamadı", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Konumu eklemek için hatırlatmanın Düzenle ekranından başla", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (intent?.action in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_PROCESS_TEXT)) {
            startActivity(Intent(intent).setClass(this, AttachmentImportActivity::class.java))
            intent?.action = null
        }
    }
    private fun mutate(action: () -> Unit) { lifecycleScope.launch { perform(action = action) } }

    private fun mutateCategory(
        errorMessage: String = "Kategori işlemi tamamlanamadı.",
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
                    Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun perform(warnIfBlocked: Boolean = false, action: () -> Unit = {}): Boolean = operations.withLock {
        try {
            val result = withContext(Dispatchers.IO) {
                synchronized(ReminderRepository.mutationLock) {
                    action()
                    ReminderScheduler.reconcile(this@MainActivity, repository)
                    val all = repository.load()
                    ReminderNotifications.restorePinned(this@MainActivity, all)
                    all to waiting.isNotEmpty()
                }
            }
            reminders = result.first; ready = true; loadFailed = false
            if (warnIfBlocked && result.second) {
                Toast.makeText(this, "Not kaydedildi. Bildirimleri uygulama ayarlarından açın.", Toast.LENGTH_LONG).show()
                requestNotificationPermission()
            }
            true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            android.util.Log.e("Real", "Reminder operation failed", e)
            if (!ready) loadFailed = true
            Toast.makeText(this, "İşlem tamamlanamadı. Notlarını silmeden yeniden deneyebilirsin.", Toast.LENGTH_LONG).show()
            false
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
