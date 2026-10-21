package com.cagri.real.ui

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.WeakHashMap

/** One clock per owner, no work when stopped or when its last consumer leaves. */
private class ReminderMinuteClock(lifecycle: Lifecycle, scope: kotlinx.coroutines.CoroutineScope) {
    val time = MutableStateFlow(System.currentTimeMillis())
    private var users = 0
    private var job: kotlinx.coroutines.Job? = null
    private val ownerLifecycle = lifecycle
    private val ownerScope = scope
    fun acquire() {
        if (users++ == 0) {
            time.value = System.currentTimeMillis()
            job = ownerScope.launch {
            ownerLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val now = System.currentTimeMillis()
                    time.value = now
                    delay(60_000L - now % 60_000L)
                }
            }
            }
        }
    }
    fun release() { if (--users == 0) { job?.cancel(); job = null } }
}
private val minuteClocks = WeakHashMap<Lifecycle, java.lang.ref.WeakReference<ReminderMinuteClock>>()
@Composable
internal fun rememberReminderMinute(enabled: Boolean = true): Long {
    val owner = LocalLifecycleOwner.current
    val clock = remember(owner) {
        minuteClocks[owner.lifecycle]?.get() ?: ReminderMinuteClock(owner.lifecycle, owner.lifecycleScope).also {
            minuteClocks[owner.lifecycle] = java.lang.ref.WeakReference(it)
        }
    }
    var now by remember(clock) { mutableLongStateOf(System.currentTimeMillis()) }
    DisposableEffect(clock, enabled) {
        if (enabled) clock.acquire()
        onDispose { if (enabled) clock.release() }
    }
    LaunchedEffect(clock, enabled) {
        if (enabled) clock.time.collect { now = it }
    }
    return now
}
