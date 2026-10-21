package com.cagri.real.speech

import android.os.SystemClock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale


/**
 * Words the recognizer should expect (category names, card titles, people): proper names and foreign titles are the
 * words it mishears most. Set by the screen that owns the mic; read when a recognition round starts.
 */
object VoiceRecognitionHints {
    @Volatile var phrases: List<String> = emptyList()
        set(value) {
            field = value.asSequence().map { it.trim() }.filter { it.length in 2..60 }
                .distinctBy { it.lowercase(Locale.ROOT) }.take(MAX).toList()
        }
    private const val MAX = 100
}

internal object VoiceRecognitionAlternatives {
    private var primary: String = ""
    private var alternatives: List<String> = emptyList()
    private var lowConfidence: List<String> = emptyList()

    /** Words the recognizer itself marked as low confidence (Android 14+), for the "Doğru mu anladım?" underline. */
    @Synchronized
    fun publishLowConfidence(words: Collection<String>) {
        lowConfidence = words.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase(Locale.ROOT) }.take(12)
    }

    @Synchronized
    fun consumeLowConfidence(): List<String> = lowConfidence.also { lowConfidence = emptyList() }

    @Synchronized
    fun publish(primaryTranscript: String, candidates: Collection<String>) {
        primary = primaryTranscript.trim()
        alternatives = candidates.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(primary, ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(5)
            .toList()
    }

    @Synchronized
    fun consume(primaryTranscript: String): List<String> {
        val normalized = primaryTranscript.trim()
        val result = if (primary.equals(normalized, ignoreCase = true)) alternatives else emptyList()
        primary = ""
        alternatives = emptyList()
        return result
    }

    @Synchronized
    fun clear() {
        primary = ""
        alternatives = emptyList()
        lowConfidence = emptyList()
    }
}

class VoiceInputController(
    private val context: Context,
    private val onText: (String) -> Unit,
    private val onState: (VoicePhase, Float) -> Unit,
    private val onSubmit: (String) -> Unit,
    private val onErrorMessage: (String) -> Unit,
    private val onDraftChange: ((committed: String, partial: String) -> Unit)? = null,
    private val onDirectSubmit: (String) -> Unit = onSubmit,
    private val onSpeechState: (Boolean) -> Unit = {}
) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var draft: VoiceDraft? = null
    private var phase = VoicePhase.Idle
    private var generation = 0L
    private var round = 0L
    private var roundRunning = false
    private var busyRetries = 0
    private var languageSwitchEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    private var hypotheses = VoiceHypotheses("")
    private var directSave = false
    private val lowConfidenceWords = mutableListOf<String>()

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
        hypotheses = VoiceHypotheses(initialText)
        directSave = false
        lowConfidenceWords.clear()
        VoiceRecognitionAlternatives.clear()
        languageSwitchEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        phase = VoicePhase.Listening
        onState(phase, 0f)
        notifyDraft()
        startRound(generation)
    }

    fun finishDirect() {
        if (phase != VoicePhase.Listening) return
        directSave = true
        finish()
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
        
        val currentGeneration = generation
        handler.postDelayed({
            if (generation == currentGeneration && phase == VoicePhase.Finishing)
                fail("Son konuşma sonucu alınamadı. Metni kontrol edip kaydedebilirsin.")
        }, 5000L)
        runCatching { recognizer?.stopListening() }.onFailure { fail("Ses tanıma bitirilemedi. Metin korundu.") }
    }


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
                override fun onBeginningOfSpeech() { if (valid()) { busyRetries = 0; onSpeechState(true) } }
                private var lastLevelUpdate = 0L
                override fun onRmsChanged(rmsdB: Float) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastLevelUpdate < 80) return
                    lastLevelUpdate = now
                    if (valid()) onState(phase, if (phase == VoicePhase.Listening) ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) else 0f)
                }
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { if (valid()) onSpeechState(false) }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onPartialResults(partialResults: Bundle?) {
                    if (!valid()) return
                    bestText(partialResults)?.let { draft?.updatePartial(it) }
                    notifyDraft()
                }
                override fun onResults(results: Bundle?) {
                    if (!valid()) return
                    roundRunning = false
                    onSpeechState(false)
                    val candidates = recognitionTexts(results)
                    val primary = candidates.firstOrNull()
                    if (primary == null && draft?.partial?.isNotBlank() == true) {
                        fail("Son konuşma sonucu alınamadı. Metni kontrol edip kaydedebilirsin.")
                        return
                    }
                    hypotheses.append(candidates)
                    lowConfidenceWords += lowConfidenceParts(results)
                    draft?.commitResult(primary)
                    notifyDraft()
                    releaseRecognizer()
                    if (phase == VoicePhase.Finishing) submitOnce() else nextRound(session, 120L)
                }
                override fun onError(error: Int) {
                    if (!valid()) return
                    roundRunning = false
                    onSpeechState(false)
                    releaseRecognizer()
                    if (draft?.partial?.isNotBlank() == true) {
                        fail("Konuşma tamamlanamadı. Metni kontrol edip kaydedebilirsin.")
                    } else if (phase == VoicePhase.Finishing) {
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) submitOnce()
                        else fail("Ses tanıma tamamlanamadı. Metin korundu.")
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
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                            if (languageSwitchEnabled) {
                                languageSwitchEnabled = false
                                nextRound(session, 250L)
                            } else {
                                fail("Konuştuğun dil için ses tanıma desteğini veya dil paketini kontrol et.")
                            }
                        }
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            fail("Ses tanıma bağlantısı kurulamadı. Yazılan metin korundu; tekrar deneyebilirsin.")
                        else -> fail("Ses tanıma durdu. Yazılan metin korundu; tekrar deneyebilirsin.")
                    }
                }
            })
            speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                // Android 14+ can ask the installed speech service to detect and switch languages
                // during the utterance. Older devices fall back to the device language instead of
                // forcing Turkish, so the existing mic remains useful outside tr-TR.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && languageSwitchEnabled) {
                    putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                    putExtra(
                        RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,
                        RecognizerIntent.LANGUAGE_SWITCH_QUICK_RESPONSE
                    )
                    // Turkish and English both stay possible (a foreign title inside a Turkish sentence).
                    val languages = arrayListOf(Locale.getDefault().toLanguageTag(), "tr-TR", "en-US").distinct()
                    putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, ArrayList(languages))
                    putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, ArrayList(languages))
                    putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_CONFIDENCE, true)
                } else {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    VoiceRecognitionHints.phrases.takeIf { it.isNotEmpty() }?.let {
                        putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(it))
                    }
                }
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
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

    private fun recognitionTexts(bundle: Bundle?): List<String> =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.asSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinctBy { it.lowercase(Locale.ROOT) }
            ?.take(5)
            ?.toList()
            .orEmpty()

    private fun bestText(bundle: Bundle?): String? = recognitionTexts(bundle).firstOrNull()

    /** Android 14+: the words of the best result the speech service was not sure about (empty when it does not say). */
    private fun lowConfidenceParts(bundle: Bundle?): List<String> {
        if (bundle == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return emptyList()
        return runCatching {
            bundle.getParcelableArrayList(SpeechRecognizer.RECOGNITION_PARTS, android.speech.RecognitionPart::class.java)
                .orEmpty()
                .filter {
                    it.confidenceLevel == android.speech.RecognitionPart.CONFIDENCE_LEVEL_LOW ||
                        it.confidenceLevel == android.speech.RecognitionPart.CONFIDENCE_LEVEL_MEDIUM_LOW
                }
                .mapNotNull { (it.formattedText ?: it.rawText).trim().takeIf { word -> word.isNotBlank() } }
        }.getOrDefault(emptyList())
    }

    private fun submitOnce() {
        if (phase != VoicePhase.Finishing) return
        val currentDraft = draft
        if (currentDraft?.partial?.isNotBlank() == true) {
            fail("Son konuşma sonucu alınamadı. Metni kontrol edip kaydedebilirsin.")
            return
        }
        val text = currentDraft?.committed.orEmpty()
        val saveDirectly = directSave
        val alternatives = hypotheses.alternatives
        val lowWords = lowConfidenceWords.toList()
        val hasSpoken = currentDraft?.hasSpokenText == true
        reset() 
        if (hasSpoken && text.isNotBlank()) {
            if (saveDirectly) {
                VoiceRecognitionAlternatives.clear()
                onDirectSubmit(text)
            } else {
                VoiceRecognitionAlternatives.publish(text, alternatives)
                VoiceRecognitionAlternatives.publishLowConfidence(lowWords)
                onSubmit(text)
            }
        } else {
            currentDraft?.text?.let(onText)
            onErrorMessage("Konuşma algılanmadı. Tekrar deneyebilirsin.")
        }
    }

    private fun fail(message: String) {
        draft?.text?.let(onText)
        VoiceRecognitionAlternatives.clear()
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
        directSave = false
        phase = VoicePhase.Idle
        onSpeechState(false)
        roundRunning = false
        handler.removeCallbacksAndMessages(null)
        releaseRecognizer()
        draft = null
        onState(VoicePhase.Idle, 0f)
        notifyDraft()
    }
}
