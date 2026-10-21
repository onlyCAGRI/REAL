package com.cagri.real.speech

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Owns the no-AI save lifecycle outside ReminderListScreen. */
@Composable
fun rememberDirectVoiceSave(
    onText: (String) -> Unit,
    onSave: suspend (String) -> Long?,
    onSaved: (Long) -> Unit,
    onBusy: (Boolean) -> Unit,
    onRecover: () -> Unit
): (String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestSave by rememberUpdatedState(onSave)
    val latestText by rememberUpdatedState(onText)
    val latestSaved by rememberUpdatedState(onSaved)
    val latestBusy by rememberUpdatedState(onBusy)
    val latestRecover by rememberUpdatedState(onRecover)
    val busy = remember { mutableStateOf(false) }
    return remember(scope) {
        { raw ->
            val text = raw.trim()
            if (text.isNotEmpty() && !busy.value) {
                busy.value = true
                latestBusy(true)
                latestText(text)
                val save = latestSave
                scope.launch {
                    try {
                        val id = save(text)
                        if (id != null) {
                            latestSaved(id)
                            latestText("")
                        } else {
                            latestRecover()
                            com.cagri.real.ui.components.RealToast.show(context, "Kaydedilemedi. Metin korundu.", long = true)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled // The composer already holds the final text.
                    } catch (_: Exception) {
                        latestRecover()
                        com.cagri.real.ui.components.RealToast.show(context, "Kaydedilemedi. Metin korundu.", long = true)
                    } finally {
                        busy.value = false
                        latestBusy(false)
                    }
                }
            }
        }
    }
}

/** Only a final result starts the silence timer; live partial changes never trigger submission. */
@Composable
fun HomeVoiceAutoFinish(voice: VoiceInputHandle, enabled: Boolean) {
    val finish by rememberUpdatedState(voice.finish)
    LaunchedEffect(enabled, voice.phase, voice.committedText, voice.partialText, voice.isSpeaking) {
        if (enabled && voice.phase == VoicePhase.Listening &&
            voice.committedText.isNotBlank() && voice.partialText.isBlank() && !voice.isSpeaking) {
            delay(1800L)
            finish()
        }
    }
}
