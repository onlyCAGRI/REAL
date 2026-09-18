package com.cagri.bigboscagri.ui

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.*

/** Refresh Today after midnight or returning from background, with no background polling. */
@Composable
fun rememberReminderDay(): LocalDate {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var date by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = ZonedDateTime.now()
                date = now.toLocalDate()
                delay(Duration.between(now, date.plusDays(1).atStartOfDay(now.zone)).toMillis().coerceAtLeast(1000))
            }
        }
    }
    return date
}
