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
    private var reminders by mutableStateOf<List<ReminderItem>>(emptyList())
    private var ready by mutableStateOf(false)
    private var loadFailed by mutableStateOf(false)
    private var exactAllowed by mutableStateOf(false)
    private var openedReminderId by mutableStateOf<Long?>(null)
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
        openedReminderId = intent.getLongExtra(ReminderNotifications.EXTRA_REMINDER_ID, -1L).takeIf { it > 0 }
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
                    onAddReminder = { text, alertAt, repeatRule ->
                        perform(warnIfBlocked = true) {
                            val newest = repository.add(text, alertAt, repeatRule).first()
                            if (alertAt == null && !ReminderNotifications.show(this@MainActivity, newest)) waiting.add(newest.id)
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
                    onEditReminder = { id, text, accentStart, accentEnd -> mutate {
                        repository.edit(id, text, accentStart, accentEnd).firstOrNull { it.id == id }
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
                    requestedReminderId = openedReminderId,
                    onRequestHandled = { openedReminderId = null; intent.removeExtra(ReminderNotifications.EXTRA_REMINDER_ID) },
                    reduceDynamicBlur = reduceBlur,
                    onReduceDynamicBlurChange = { reduceBlur = it; preferences.edit().putBoolean("reduce_dynamic_blur", it).apply() },
                    reduceAnimations = reduceAnimations,
                    onReduceAnimationsChange = { reduceAnimations = it; preferences.edit().putBoolean("reduce_animations", it).apply() },
                    designStyle = style,
                    onDesignStyleChange = { style = it; preferences.edit().putString("design_style", if (it == DesignStyle.Ice) "ice" else "glass").apply() },
                    exactAlarmsAllowed = exactAllowed,
                    onRequestExactAlarms = ::openAlarmAccess
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        exactAllowed = ReminderScheduler.canScheduleExact(this)
        if (::repository.isInitialized) lifecycleScope.launch { perform() }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        openedReminderId = intent.getLongExtra(ReminderNotifications.EXTRA_REMINDER_ID, -1L).takeIf { it > 0 }
    }
    private fun mutate(action: () -> Unit) { lifecycleScope.launch { perform(action = action) } }

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
