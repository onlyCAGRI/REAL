package com.cagri.bigboscagri.speech

import android.os.SystemClock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** All entry points and recognition callbacks run on the main thread. */
class VoiceInputController(
    private val context: Context,
    private val onText: (String) -> Unit,
    private val onState: (VoicePhase, Float) -> Unit,
    private val onSubmit: (String) -> Unit,
    private val onErrorMessage: (String) -> Unit,
    private val onDraftChange: ((committed: String, partial: String) -> Unit)? = null
) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var draft: VoiceDraft? = null
    private var phase = VoicePhase.Idle
    private var generation = 0L
    private var round = 0L
    private var roundRunning = false
    private var busyRetries = 0

    private fun notifyDraft() {
        val d = draft
        if (d != null) {
            onText(d.text)
            onDraftChange?.invoke(d.committed, d.partial)
        } else {
            onDraftChange?.invoke("", "")
        }
    }

    fun start(initialText: String) {
        if (phase != VoicePhase.Idle) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onErrorMessage("Telefonda bir konuşma tanıma hizmeti bulunamadı. Sesle yazma hizmetini etkinleştir.")
            return
        }
        generation++
        draft = VoiceDraft(initialText)
        busyRetries = 0
        phase = VoicePhase.Listening
        onState(phase, 0f)
        notifyDraft()
        startRound(generation)
    }

    fun finish() {
        if (phase != VoicePhase.Listening) return
        phase = VoicePhase.Finishing
        onState(phase, 0f)
        handler.removeCallbacksAndMessages(null)
        if (!roundRunning || recognizer == null) {
            submitOnce()
            return
        }
        // stopListening asks for a final result; cancel would discard the tail.
        val currentGeneration = generation
        handler.postDelayed({
            if (generation == currentGeneration && phase == VoicePhase.Finishing) submitOnce()
        }, 5000L)
        runCatching { recognizer?.stopListening() }.onFailure { submitOnce() }
    }

    /** Leaving the screen stops recording and preserves the visible draft. */
    fun cancel() {
        if (phase != VoicePhase.Idle) draft?.text?.let(onText)
        reset()
    }

    fun dispose() = cancel()

    private fun startRound(session: Long) {
        if (generation != session || phase != VoicePhase.Listening) return
        val thisRound = ++round
        roundRunning = true
        fun valid() = generation == session && round == thisRound && phase != VoicePhase.Idle && roundRunning
        try {
            val speech = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = speech
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() { if (valid()) busyRetries = 0 }
                private var lastLevelUpdate = 0L
                override fun onRmsChanged(rmsdB: Float) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastLevelUpdate < 80) return
                    lastLevelUpdate = now
                    if (valid()) onState(phase, if (phase == VoicePhase.Listening) ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) else 0f)
                }
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onPartialResults(partialResults: Bundle?) {
                    if (!valid()) return
                    bestText(partialResults)?.let { draft?.updatePartial(it) }
                    notifyDraft()
                }
                override fun onResults(results: Bundle?) {
                    if (!valid()) return
                    roundRunning = false
                    draft?.commitResult(bestText(results))
                    notifyDraft()
                    releaseRecognizer()
                    if (phase == VoicePhase.Finishing) submitOnce() else nextRound(session, 120L)
                }
                override fun onError(error: Int) {
                    if (!valid()) return
                    roundRunning = false
                    releaseRecognizer()
                    if (phase == VoicePhase.Finishing) {
                        submitOnce()
                    } else when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            draft?.commitResult(null)
                            notifyDraft()
                            nextRound(session, 300L)
                        }
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            if (busyRetries++ < 2) nextRound(session, 800L)
                            else fail("Mikrofon başka bir uygulamada kullanılıyor olabilir. Tekrar dene.")
                        }
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail("Sesle yazmak için mikrofon izni gerekiyor.")
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            fail("Sesle yazma hizmetinde Türkçe dilini etkinleştir veya Türkçe dil paketini indir.")
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            fail("Ses tanıma bağlantısı kurulamadı. Yazılan metin korundu; tekrar deneyebilirsin.")
                        else -> fail("Ses tanıma durdu. Yazılan metin korundu; tekrar deneyebilirsin.")
                    }
                }
            })
            speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            })
        } catch (_: SecurityException) {
            fail("Sesle yazmak için mikrofon izni gerekiyor.")
        } catch (_: RuntimeException) {
            fail("Konuşma tanıma başlatılamadı. Telefonun sesle yazma hizmetini kontrol et.")
        }
    }

    private fun nextRound(session: Long, delay: Long) {
        onState(phase, 0f)
        handler.postDelayed({ startRound(session) }, delay)
    }

    private fun bestText(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull { it.isNotBlank() }

    private fun submitOnce() {
        if (phase != VoicePhase.Finishing) return
        val currentDraft = draft
        currentDraft?.commitResult(null)
        val text = currentDraft?.text.orEmpty()
        val hasSpoken = currentDraft?.hasSpokenText == true
        reset() // Invalidates late callbacks before saving or changing screens.
        if (hasSpoken && text.isNotBlank()) {
            onSubmit(text)
        } else {
            currentDraft?.text?.let(onText)
            onErrorMessage("Konuşma algılanmadı. Tekrar deneyebilirsin.")
        }
    }

    private fun fail(message: String) {
        draft?.text?.let(onText)
        reset()
        onErrorMessage(message)
    }

    private fun releaseRecognizer() {
        val previous = recognizer
        recognizer = null
        runCatching { previous?.destroy() }
    }

    private fun reset() {
        generation++
        phase = VoicePhase.Idle
        roundRunning = false
        handler.removeCallbacksAndMessages(null)
        releaseRecognizer()
        draft = null
        onState(VoicePhase.Idle, 0f)
        notifyDraft()
    }
}
