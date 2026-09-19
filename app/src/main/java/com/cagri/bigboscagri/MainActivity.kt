package com.cagri.bigboscagri

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cagri.bigboscagri.data.*
import com.cagri.bigboscagri.notifications.*
import com.cagri.bigboscagri.ui.*
import com.cagri.bigboscagri.ui.theme.BigBosTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainActivity : ComponentActivity() {
    private lateinit var repository: ReminderRepository
    private lateinit var categoryRepository: CategoryRepository
    private var reminders by mutableStateOf<List<ReminderItem>>(emptyList())
    private var customCategories by mutableStateOf<List<CustomCategory>>(emptyList())
    private var ready by mutableStateOf(false)
    private var loadFailed by mutableStateOf(false)
    private var exactAllowed by mutableStateOf(false)
    private var openedReminderId by mutableStateOf<Long?>(null)
    private var pendingShareDraft by mutableStateOf<SharedPayload?>(null)
    private val operations = Mutex()
    private val waiting = mutableSetOf<Long>() // Accessed only under mutationLock on IO.
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
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
        reminders = try { repository.load() } catch (_: Exception) { emptyList() }
        customCategories = try { categoryRepository.load() } catch (_: Exception) { emptyList() }
        ready = true
        com.cagri.bigboscagri.ui.components.prewarmRealWordmark(applicationContext)
        openedReminderId = intent.getLongExtra(ReminderNotifications.EXTRA_REMINDER_ID, -1L).takeIf { it > 0 }
        handleShareIntent(intent)
        val preferences = getSharedPreferences("bigbos_ui", MODE_PRIVATE)
        setContent {
            BigBosTheme {
                var reduceBlur by remember { mutableStateOf(preferences.getBoolean("reduce_dynamic_blur", false)) }
                var reduceAnimations by remember { mutableStateOf(preferences.getBoolean("reduce_animations", false)) }
                var style by remember { mutableStateOf(if (preferences.getString("design_style", "glass") == "ice") DesignStyle.Ice else DesignStyle.Glass) }
                LaunchedEffect(Unit) { requestNotificationPermission() }
                if (!ready) Surface(Modifier.fillMaxSize(), color = Color.Black) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (loadFailed) TextButton(onClick = { lifecycleScope.launch { perform() } }) {
                            Text("Notlar açılamadı. Yeniden dene")
                        } else CircularProgressIndicator()
                    }
                } else ReminderScreen(
                    reminders = reminders,
                    onAddReminder = { text, alertAt, repeatRule, customCategoryId, isChecklist, checklistItems, sendImmediateNotification, referenceUrl ->
                        perform(warnIfBlocked = true) {
                            val newest = repository.add(
                                text = text,
                                alertAt = alertAt,
                                repeatRule = repeatRule,
                                customCategoryId = customCategoryId,
                                isChecklist = isChecklist,
                                checklistItems = checklistItems,
                                sendImmediateNotification = sendImmediateNotification,
                                referenceUrl = referenceUrl
                            ).first()
                            if (alertAt == null && canShowImmediateNotification(newest) && !ReminderNotifications.show(this@MainActivity, newest)) waiting.add(newest.id)
                        }
                    },
                    onToggleChecklistItem = { reminderId, itemId ->
                        mutate {
                            repository.toggleChecklistItem(reminderId, itemId)
                        }
                    },
                    onToggleCompleted = { id -> mutate {
                        val item = repository.toggleCompleted(id).firstOrNull { it.id == id }
                        if (item?.isCompleted == true) { ReminderNotifications.cancel(this, id); waiting.remove(id) }
                    } },
                    onToggleImportant = { id -> mutate {
                        val item = repository.toggleImportant(id).firstOrNull { it.id == id }
                        if (item?.shouldPinNotification == true) ReminderNotifications.show(this, item)
                        else { ReminderNotifications.cancel(this, id); waiting.remove(id) }
                    } },
                    onEditReminder = { id, text, accentStart, accentEnd, customCategoryId -> mutate {
                        repository.edit(id, text, accentStart, accentEnd, customCategoryId).firstOrNull { it.id == id }
                            ?.let { ReminderNotifications.refreshEdited(this, it) }
                    } },
                    onSetAlert = { id, at, repeatRule -> mutate {
                        val item = repository.setAlert(id, at, repeatRule).firstOrNull { it.id == id }
                        ReminderNotifications.cancel(this, id)
                        if (item != null && (at == null || item.shouldPinNotification)) ReminderNotifications.show(this, item)
                    } },
                    onDeleteReminder = { id -> mutate {
                        repository.delete(id); ReminderNotifications.cancel(this, id); waiting.remove(id)
                    } },
                    onDeleteReminders = { ids -> mutate {
                        ids.forEach { id ->
                            ReminderNotifications.cancel(this, id)
                            waiting.remove(id)
                        }
                        repository.deleteAll(ids)
                    } },
                    requestedReminderId = openedReminderId,
                    onRequestHandled = { openedReminderId = null; intent.removeExtra(ReminderNotifications.EXTRA_REMINDER_ID) },
                    reduceDynamicBlur = reduceBlur,
                    onReduceDynamicBlurChange = { reduceBlur = it; preferences.edit().putBoolean("reduce_dynamic_blur", it).apply() },
                    reduceAnimations = reduceAnimations,
                    onReduceAnimationsChange = { reduceAnimations = it; preferences.edit().putBoolean("reduce_animations", it).apply() },
                    designStyle = style,
                    onDesignStyleChange = { style = it; preferences.edit().putString("design_style", if (it == DesignStyle.Ice) "ice" else "glass").apply() },
                    exactAlarmsAllowed = exactAllowed,
                    onRequestExactAlarms = ::openAlarmAccess,
                    customCategories = customCategories,
                    onCreateCategory = { name, iconKey, start, end ->
                        mutateCategory("Kategori oluşturulamadı.") {
                            categoryRepository.add(name, iconKey, start, end)
                        }
                    },
                    onEditCategory = { id, name, iconKey, start, end ->
                        mutateCategory("Kategori güncellenemedi.") {
                            categoryRepository.update(id, name, iconKey, start, end)
                        }
                    },
                    onDeleteCategory = { id ->
                        mutateCategory("Kategori silinemedi. Yeniden deneyebilirsin.") {
                            deleteCategoryWithRollback(id, repository, categoryRepository)
                        }
                    },
                    onDeleteCategories = { ids ->
                        mutateCategory("Kategoriler silinemedi. Yeniden deneyebilirsin.") {
                            deleteCategoriesWithRollback(ids, repository, categoryRepository)
                        }
                    },
                    pendingShareDraft = pendingShareDraft,
                    onShareDraftHandled = { pendingShareDraft = null }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        exactAllowed = ReminderScheduler.canScheduleExact(this)
        if (::repository.isInitialized) lifecycleScope.launch { perform() }
        if (::categoryRepository.isInitialized) {
            customCategories = try { categoryRepository.load() } catch (_: Exception) { emptyList() }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        openedReminderId = intent.getLongExtra(ReminderNotifications.EXTRA_REMINDER_ID, -1L).takeIf { it > 0 }
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        ShareParser.parse(intent)?.let { payload ->
            pendingShareDraft = payload
            intent?.removeExtra(Intent.EXTRA_TEXT)
            intent?.removeExtra(Intent.EXTRA_SUBJECT)
            intent?.removeExtra(Intent.EXTRA_TITLE)
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
                                repository.load() to categoryRepository.load()
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
    }
    private fun openAlarmAccess() {
        if (Build.VERSION.SDK_INT >= 31) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + packageName))
            try { startActivity(intent) }
            catch (_: android.content.ActivityNotFoundException) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName))) }
        }
    }
}
