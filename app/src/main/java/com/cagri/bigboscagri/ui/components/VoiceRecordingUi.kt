package com.cagri.bigboscagri.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cagri.bigboscagri.data.ReminderVoiceRecording
import com.cagri.bigboscagri.data.VoiceRecordingStorage
import com.cagri.bigboscagri.data.formatVoiceDuration
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

@Suppress("DEPRECATION")
private fun newMediaRecorder(context: Context): MediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    MediaRecorder(context)
} else {
    MediaRecorder()
}

private fun readAudioDuration(path: String, fallbackMillis: Long): Long {
    return runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            retriever.release()
        }
    }.getOrNull()?.takeIf { it > 0L } ?: fallbackMillis.coerceAtLeast(1L)
}

@Composable
fun VoiceRecorderInline(
    recordings: List<ReminderVoiceRecording>,
    onRecorded: (ReminderVoiceRecording) -> Unit,
    onRemove: (ReminderVoiceRecording) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    compact: Boolean = false,
    enabled: Boolean = true,
    onRecordingStateChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var activeId by remember { mutableStateOf<String?>(null) }
    var activePath by remember { mutableStateOf<String?>(null) }
    var activeCreatedAt by remember { mutableLongStateOf(0L) }
    var startedElapsed by remember { mutableLongStateOf(0L) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var activityLevel by remember { mutableFloatStateOf(0f) }
    var isRecording by remember { mutableStateOf(false) }
    var permissionStartPending by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val cancelRecording: () -> Unit = {
        val current = recorder
        recorder = null
        if (current != null) {
            runCatching { current.stop() }
            runCatching { current.reset() }
            runCatching { current.release() }
        }
        activePath?.let { VoiceRecordingStorage.deleteRecordingFile(context, it) }
        activeId = null
        activePath = null
        activeCreatedAt = 0L
        startedElapsed = 0L
        elapsedMillis = 0L
        activityLevel = 0f
        if (isRecording) onRecordingStateChange(false)
        isRecording = false
    }

    val stopRecording: () -> Unit = stop@{
        val current = recorder ?: return@stop
        val id = activeId ?: return@stop
        val path = activePath ?: return@stop
        val createdAt = activeCreatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        val measured = max(1L, SystemClock.elapsedRealtime() - startedElapsed)
        recorder = null
        val stopped = runCatching { current.stop() }.isSuccess
        runCatching { current.reset() }
        runCatching { current.release() }
        if (isRecording) onRecordingStateChange(false)
        isRecording = false
        activityLevel = 0f
        elapsedMillis = measured
        activeId = null
        activePath = null
        activeCreatedAt = 0L
        startedElapsed = 0L
        if (stopped && File(path).isFile && File(path).length() > 0L) {
            onRecorded(ReminderVoiceRecording(id, path, readAudioDuration(path, measured), createdAt))
            errorText = null
        } else {
            VoiceRecordingStorage.deleteRecordingFile(context, path)
            errorText = "Ses kaydı oluşturulamadı."
        }
    }

    val startRecording: () -> Unit = start@{
        if (!enabled || isRecording) return@start
        errorText = null
        val (id, output) = VoiceRecordingStorage.createOutput(context)
        val current = newMediaRecorder(context)
        val started = runCatching {
            current.setAudioSource(MediaRecorder.AudioSource.MIC)
            current.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            current.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            current.setAudioSamplingRate(44_100)
            current.setAudioEncodingBitRate(128_000)
            current.setAudioChannels(1)
            current.setOutputFile(output.absolutePath)
            current.prepare()
            current.start()
        }.isSuccess
        if (started) {
            recorder = current
            activeId = id
            activePath = output.absolutePath
            activeCreatedAt = System.currentTimeMillis()
            startedElapsed = SystemClock.elapsedRealtime()
            elapsedMillis = 0L
            activityLevel = 0f
            isRecording = true
            onRecordingStateChange(true)
        } else {
            runCatching { current.reset() }
            runCatching { current.release() }
            output.delete()
            errorText = "Mikrofon başlatılamadı."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && permissionStartPending) startRecording()
        else if (!granted && permissionStartPending) errorText = "Ses kaydı için mikrofon izni gerekli."
        permissionStartPending = false
    }

    val requestStart: () -> Unit = request@{
        if (!enabled || isRecording) return@request
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            permissionStartPending = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            elapsedMillis = max(0L, SystemClock.elapsedRealtime() - startedElapsed)
            activityLevel = runCatching {
                ((recorder?.maxAmplitude ?: 0) / 32767f).coerceIn(0f, 1f)
            }.getOrDefault(0f)
            delay(90L)
        }
    }

    LaunchedEffect(enabled) {
        if (!enabled && (isRecording || recorder != null)) cancelRecording()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && isRecording) cancelRecording()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (isRecording || recorder != null) cancelRecording()
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
        if (showHeader) {
            Text(
                "Ses",
                color = Color(0xFFA5A5B0),
                fontSize = if (compact) 12.sp else 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        if (isRecording) {
            RecordingRow(elapsedMillis, activityLevel, onStop = stopRecording, compact = compact)
        } else {
            recordings.forEach { recording ->
                VoicePlaybackRow(
                    recording = recording,
                    onDelete = if (enabled) ({ onRemove(recording) }) else null,
                    compact = compact
                )
            }
        }
        if (!isRecording && enabled) {
            RecorderReadyRow(
                hasRecordings = recordings.isNotEmpty(),
                onStart = requestStart,
                compact = compact
            )
        }
        errorText?.let {
            Text(it, color = Color(0xFFE0B5A8), fontSize = 12.sp)
        }
    }
}

@Composable
private fun RecordingRow(elapsedMillis: Long, activityLevel: Float, onStop: () -> Unit, compact: Boolean) {
    RecorderControlRow(elapsedMillis, activityLevel, true, onStop, compact)
}

@Composable
private fun RecorderReadyRow(hasRecordings: Boolean, onStart: () -> Unit, compact: Boolean) {
    RecorderControlRow(0L, 0f, false, onStart, compact)
}

@Composable
private fun RecorderControlRow(elapsedMillis: Long, activityLevel: Float, active: Boolean,
    onClick: () -> Unit, compact: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp).semantics {
            contentDescription = if (active) "Ses kaydını durdur" else "Ses kaydını başlat"
        }) {
            ActionIcon(ActionGlyph.AudioWave, Modifier.size(23.dp), if (active) Color(0xFF91B9FF) else Color.White)
        }
        RecordingVoiceAnimation(activityLevel, Modifier.weight(1f).height(if (compact) 25.dp else 32.dp), active)
        Text(formatVoiceDuration(elapsedMillis), color = Color(0xFFEAEAF0), fontSize = 12.sp)
    }
}

@Composable
internal fun RecordingVoiceAnimation(
    activityLevel: Float,
    modifier: Modifier = Modifier,
    active: Boolean = true
) {
    // Read the latest microphone level inside each independent animation loop.
    // A soft response curve makes ordinary speech visible without requiring clipping-level input.
    val liveLevel by androidx.compose.runtime.rememberUpdatedState(
        kotlin.math.sqrt(activityLevel.coerceIn(0f, 1f)))
    val circles = remember { List(5) { Animatable(0.12f) } }
    val sensitivity = remember { floatArrayOf(1f, 0.78f, 1.12f, 0.88f, 1.04f) }
    LaunchedEffect(active) {
        coroutineScope {
            circles.forEachIndexed { index, circle ->
                launch {
                    if (active) {
                        delay(index * 37L)
                        while (true) {
                            // Independent targets/timing: never a synchronized equalizer sweep.
                            val energy = (liveLevel * sensitivity[index]).coerceIn(0f, 1f)
                            val target = 0.07f + Random.nextFloat() * (0.13f + energy * 0.80f)
                            val duration = (Random.nextInt(300, 610) * (1f - energy * 0.48f)).toInt()
                            circle.animateTo(target,
                                tween(duration, easing = FastOutSlowInEasing))
                        }
                    } else {
                        circle.animateTo(0.12f, tween(240))
                    }
                }
            }
        }
    }

    Canvas(modifier) {
        val count = 5
        val centerIndex = (count - 1) / 2f
        val maxDiameter = size.height * 0.72f
        val minDiameter = size.height * 0.20f
        val gap = (size.height * 0.78f).coerceAtMost(size.width / 5.8f)
        val clusterWidth = gap * (count - 1)
        val startX = size.width / 2f - clusterWidth / 2f

        for (index in 0 until count) {
            val distance = abs(index - centerIndex) / centerIndex.coerceAtLeast(1f)
            val diameter = (minDiameter + (maxDiameter - minDiameter) *
                circles[index].value).coerceIn(minDiameter, maxDiameter)
            val radius = diameter / 2f
            val x = startX + index * gap
            val glowRadius = radius * 1.55f

            drawCircle(
                color = Color(0xFF91B9FF).copy(alpha = 0.08f + 0.08f * (1f - distance)),
                radius = glowRadius,
                center = Offset(x, size.height / 2f)
            )
            drawCircle(
                color = Color(0xFFDDEAFF).copy(alpha = 0.70f + 0.24f * (1f - distance)),
                radius = radius,
                center = Offset(x, size.height / 2f)
            )
        }
    }
}

@Composable
fun VoicePlaybackRow(
    recording: ReminderVoiceRecording,
    onDelete: (() -> Unit)? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    var player by remember(recording.id, recording.localPath) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(recording.id) { mutableStateOf(false) }
    var playbackError by remember(recording.id) { mutableStateOf(false) }

    fun releasePlayer() {
        val current = player
        player = null
        runCatching { current?.release() }
        playing = false
    }

    val togglePlayback: () -> Unit = {
        if (!playing) {
            val current = player ?: run {
                val created = MediaPlayer()
                val prepared = runCatching {
                    created.apply {
                        setDataSource(recording.localPath)
                        prepare()
                        setOnCompletionListener {
                            playing = false
                            runCatching { it.seekTo(0) }
                        }
                    }
                }.getOrNull()
                if (prepared == null) {
                    playbackError = true
                    runCatching { created.release() }
                }
                player = prepared
                prepared
            }
            if (current != null) {
                runCatching { current.start() }.onSuccess {
                    playing = true
                    playbackError = false
                }.onFailure {
                    playbackError = true
                    releasePlayer()
                }
            }
        } else {
            runCatching { player?.pause() }
            playing = false
        }
    }

    val playbackLifecycle = LocalLifecycleOwner.current
    DisposableEffect(recording.id, recording.localPath, playbackLifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) releasePlayer()
        }
        playbackLifecycle.lifecycle.addObserver(observer)
        onDispose {
            playbackLifecycle.lifecycle.removeObserver(observer)
            releasePlayer()
        }
    }

    val shape = RoundedCornerShape(if (compact) 13.dp else 18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
    ) {
        IconButton(
            onClick = togglePlayback,
            modifier = Modifier.size(if (compact) 28.dp else 34.dp).semantics {
                contentDescription = if (playing) "Ses kaydını duraklat" else "Ses kaydını oynat"
            }
        ) {
            ActionIcon(
                if (playing) ActionGlyph.Pause else ActionGlyph.Play,
                Modifier.size(if (compact) 14.dp else 17.dp),
                if (playbackError) Color(0xFFF0B3B3) else Color(0xFFEAEAF0)
            )
        }
        RecordingVoiceAnimation(
            activityLevel = if (playing) 0.72f else 0f,
            active = playing,
            modifier = Modifier.weight(1f).height(if (compact) 20.dp else 28.dp)
        )
        Text(
            formatVoiceDuration(recording.durationMillis),
            color = Color(0xFFB8B8C2),
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.Medium
        )
        if (onDelete != null) {
            IconButton(
                onClick = {
                    releasePlayer()
                    onDelete()
                },
                modifier = Modifier.size(if (compact) 28.dp else 32.dp).semantics { contentDescription = "Ses kaydını sil" }
            ) {
                ActionIcon(ActionGlyph.Close, Modifier.size(if (compact) 11.dp else 13.dp), Color(0xFFA7A7B0))
            }
        }
    }
}
