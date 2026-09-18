package com.cagri.bigboscagri.speech

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class VoiceInputHandle(
    val phase: VoicePhase, val level: Float,
    val toggle: () -> Unit, val finish: () -> Unit, val cancel: () -> Unit
)

@Composable
fun rememberVoiceInput(draft: String, onText: (String) -> Unit, onSubmit: (String) -> Unit, observeLevel: Boolean = true): VoiceInputHandle {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val trackLevel by rememberUpdatedState(observeLevel)
    val latestDraft by rememberUpdatedState(draft)
    val latestOnText by rememberUpdatedState(onText)
    val latestOnSubmit by rememberUpdatedState(onSubmit)
    var phase by remember { mutableStateOf(VoicePhase.Idle) }
    var level by remember { mutableFloatStateOf(0f) }
    var requestingPermission by remember { mutableStateOf(false) }
    val controller = remember(context) {
        VoiceInputController(
            context.applicationContext, onText = { latestOnText(it) },
            onState = { nextPhase, nextLevel -> phase = nextPhase; level = if (trackLevel) nextLevel else 0f },
            onSubmit = { latestOnSubmit(it) },
            onErrorMessage = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        requestingPermission = false
        if (granted && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            controller.start(latestDraft)
        } else if (!granted) {
            Toast.makeText(context, "Sesle yazmak için uygulama ayarlarından mikrofon iznini açabilirsin.", Toast.LENGTH_LONG).show()
        }
    }
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) controller.cancel() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.dispose() }
    }
    return VoiceInputHandle(phase, level,
        toggle = {
            when (phase) {
                VoicePhase.Listening -> controller.finish()
                VoicePhase.Finishing -> Unit
                VoicePhase.Idle -> {
                    if (!requestingPermission) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            controller.start(latestDraft)
                        } else {
                            requestingPermission = true
                            permission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
            }
        }, finish = controller::finish, cancel = controller::cancel
    )
}
