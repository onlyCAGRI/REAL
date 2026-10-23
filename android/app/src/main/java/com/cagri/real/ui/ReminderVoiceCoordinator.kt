package com.cagri.real.ui

import com.cagri.real.i18n.t
import androidx.compose.runtime.*
import com.cagri.real.data.*
import com.cagri.real.speech.*
import com.cagri.real.ui.components.*
import com.cagri.real.ui.components.planner.PlannerEventDetails
import java.time.ZoneId
import kotlinx.coroutines.*

/** AI response application and card actions. No Compose-generated orchestration body. */
internal fun reminderVoiceFinalize(
    input: ReminderComposerSnapshot,
    composerState: ReminderComposerState,
    voiceState: ReminderVoiceState,
    callbacks: ReminderComposerCallbacks,
    services: ReminderComposerServices,
    actions: ReminderVoiceActions
): (String) -> Unit = with(input) { with(callbacks) { with(services) { with(actions) {
    var editing by composerState.editing
    var isChecklistMode by composerState.isChecklistMode
    var isVoiceDraft by composerState.isVoiceDraft
    var requiresTimeSelection by composerState.requiresTimeSelection
    var pendingDateHintEpochDay by composerState.pendingDateHintEpochDay
    var pendingTimeOfDay by composerState.pendingTimeOfDay
    var manualDateClearedOrChanged by composerState.manualDateClearedOrChanged
    var checklistDraftItems by composerState.checklistDraftItems
    var sendImmediateNotification by composerState.sendImmediateNotification
    var voiceCategoryExplicit by composerState.voiceCategoryExplicit
    var draftCategoryId by composerState.draftCategoryId
    var voiceAiParsing by voiceState.voiceAiParsing
    var voiceAiRequestSerial by voiceState.voiceAiRequestSerial
    var pendingAutoSubmit by voiceState.pendingAutoSubmit
    var pendingVoiceDurationMinutes by voiceState.pendingVoiceDurationMinutes
    var pendingVoicePeople by voiceState.pendingVoicePeople
    var pendingVoiceLocation by voiceState.pendingVoiceLocation
    var pendingVoiceRepeatMask by voiceState.pendingVoiceRepeatMask
    var pendingVoiceRepeatUntil by voiceState.pendingVoiceRepeatUntil
    var lastVoiceCardId by voiceState.lastVoiceCardId
    var lastVoiceCardAt by voiceState.lastVoiceCardAt
    var pendingBulkPlan by voiceState.pendingBulkPlan
    var pendingVoiceMusicBlocks by voiceState.pendingVoiceMusicBlocks
    var pendingVoiceMediaBlocks by voiceState.pendingVoiceMediaBlocks
    fun voiceCategoryId(intent: GeminiVoiceIntent, createdCategory: CustomCategory?): Long? {
        val requestedName = intent.categoryName?.trim().orEmpty()
        if (requestedName.isNotBlank()) {
            if (createdCategory != null && voiceNameKey(createdCategory.name) == voiceNameKey(requestedName)) {
                return createdCategory.id
            }
            findVoiceCategory(requestedName, customCategories)?.let { return it.id }
        }
        return draftCategoryId ?: customCategory?.id
    }

    /** The place of a voice card with its address and map position (the phone's geocoder; name only if unknown). */
    suspend fun voiceLocation(intent: GeminiVoiceIntent): ReminderLocation? =
        intent.locationName?.trim()?.takeIf { it.isNotBlank() }?.let { VoicePlaceLookup.resolve(context, it) }
    suspend fun saveVoiceIntentDirect(prepared: PreparedVoiceIntent, createdCategory: CustomCategory?): Long? {
        val intent = prepared.intent
        val now = System.currentTimeMillis()
        val exactAlertAt = prepared.schedule.alertAt
        val quick24HourUntilAt = if (
            category == ReminderCategory.Today && exactAlertAt == null && intent.date == null
        ) now + ROLLING_24_HOURS_MILLIS else null
        val plannedDateEpochDay = if (quick24HourUntilAt == null) prepared.schedule.plannedEpochDay else null
        val location = voiceLocation(intent)
        val musicBlocks = prepared.musicBlocks
        val mediaBlocks = prepared.mediaBlocks
        val checklistItems = if (intent.isChecklist) {
            intent.checklistItems.map { ChecklistItem(id = 0L, text = it) }
        } else emptyList()

        val createdId = onAddReminder(
            intent.title,
            exactAlertAt,
            prepared.schedule.repeatRule,
            voiceCategoryId(intent, createdCategory),
            intent.isChecklist,
            checklistItems,
            false,
            null,
            emptyList(),
            emptyList(),
            location,
            plannedDateEpochDay,
            quick24HourUntilAt,
            musicBlocks,
            mediaBlocks
        )
        if (createdId != null) {
            lastVoiceCardId = createdId
            lastVoiceCardAt = System.currentTimeMillis()
            if (exactAlertAt != null && (intent.weekdaysMask != 0 || intent.repeatUntil != null)) {
                onSetRepeatPattern(createdId, intent.weekdaysMask, intent.repeatUntil?.toEpochDay())
            }
        }
        if (createdId != null && (intent.durationMinutes != null || intent.people.isNotEmpty())) {
            runCatching {
                plannerRepository.put(
                    createdId,
                    PlannerEventDetails(
                        durationMinutes = intent.durationMinutes,
                        people = intent.people,
                        colorIndex = 0
                    )
                )
            }
        }
        if (createdId != null) {
            entryTracker.onReminderCreated(createdId, reduceAnimations)
            RealPetKeywords.onReminderSaved(
                savedText = if (intent.isChecklist) {
                    (listOf(intent.title) + intent.checklistItems).joinToString("\n")
                } else intent.title,
                isKeywordsEnabled = RealPetRuntime.keywordsEnabled.value
            )
        }
        return createdId
    }

    fun offerRetry(serial: Long, work: suspend () -> Unit) {
        voiceState.retryVoiceCreation.value = {
            if (voiceAiRequestSerial == serial && !voiceAiParsing && !composerState.saving.value) {
                scope.launch {
                    voiceState.feedbackText.value = null
                    voiceAiParsing = true
                    voiceState.retryVoiceCreation.value = null
                    try { work() }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) {
                        if (voiceAiRequestSerial == serial) {
                            voiceAiParsing = false
                            editing = true
                            showVoiceFeedback(voiceState, error.message ?: t("Kartlar kaydedilemedi."))
                        }
                    } finally { if (voiceAiRequestSerial == serial) voiceAiParsing = false }
                }
            }
        }
    }

    suspend fun applyCreation(parsed: GeminiVoiceParseResult, autoSave: Boolean, requestSerial: Long,
        existingCategory: CustomCategory? = null, preparedOverride: List<PreparedVoiceIntent>? = null) {
        val preparedIntents = try { preparedOverride ?: prepareVoiceCreation(parsed.intents, System.currentTimeMillis()) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (voiceAiRequestSerial != requestSerial) return
                voiceAiParsing = false
                pendingAutoSubmit = false
                editing = true
                if (error is VoiceContentUnavailableException) {
                    offerRetry(requestSerial) { applyCreation(parsed, autoSave, requestSerial, existingCategory) }
                }
                showVoiceFeedback(voiceState, error.message ?: t("İçerik hazırlanamadı; metin korundu."))
                return
            }
        if (voiceAiRequestSerial != requestSerial) return
        val categoryRequest = parsed.newCategory
        val createdCategory = existingCategory ?: if (categoryRequest != null) {
            val colorIndex = categoryRequest.colorName?.let { requestedColor ->
                ReminderColorChoices.indexOfFirst {
                    it.label.equals(requestedColor, ignoreCase = true)
                }.takeIf { it >= 0 }
            } ?: DEFAULT_NEW_CATEGORY_COLOR_INDEX
            composerState.saving.value = true
            try { onCreateVoiceCategory(categoryRequest.name, categoryRequest.iconKey, colorIndex) }
            finally { composerState.saving.value = false }
        } else null

        if (voiceAiRequestSerial != requestSerial) return
        if (categoryRequest != null && createdCategory == null) {
            voiceAiParsing = false
            pendingAutoSubmit = false
            editing = true
            offerRetry(requestSerial) { applyCreation(parsed, autoSave, requestSerial, existingCategory) }
            showVoiceFeedback(voiceState, t("Kategori oluşturulamadı. Metin korundu."))
            return
        }

        if (parsed.intents.isEmpty()) {
            voiceAiParsing = false
            pendingAutoSubmit = false
            onDraftChange("")
            isVoiceDraft = false
            if (createdCategory != null) {
                RealHaptics.success(view)
                showVoiceFeedback(voiceState, t("{0} kategorisi hazır.", createdCategory.name))
            }
            return
        }

        // Several independent clauses in one breath become several REAL cards immediately.
        if (parsed.intents.size > 1) {
            var createdCount = 0
            val failedIntents = mutableListOf<GeminiVoiceIntent>()
            composerState.saving.value = true
            try {
                preparedIntents.forEach { prepared ->
                    if (voiceAiRequestSerial != requestSerial) return
                    val id = try { saveVoiceIntentDirect(prepared, createdCategory) }
                        catch (error: CancellationException) { throw error }
                        catch (_: Exception) { null }
                    if (id != null) createdCount++ else failedIntents.add(prepared.intent)
                }
            } finally { composerState.saving.value = false }
            val failedTitles = failedIntents.map { it.title }
            if (failedIntents.isNotEmpty()) {
                offerRetry(requestSerial) {
                    applyCreation(parsed.copy(intents = failedIntents, newCategory = null), autoSave, requestSerial, createdCategory)
                }
            } else voiceState.retryVoiceCreation.value = null
            voiceAiParsing = false
            pendingAutoSubmit = false
            pendingVoiceDurationMinutes = null
            pendingVoicePeople = emptyList()
            pendingVoiceLocation = null; pendingVoiceRepeatMask = 0; pendingVoiceRepeatUntil = null
            pendingVoiceMusicBlocks = emptyList()
            pendingVoiceMediaBlocks = emptyList()
            onDraftChange(failedTitles.joinToString("\n"))
            isVoiceDraft = failedTitles.isNotEmpty()
            editing = failedTitles.isNotEmpty()
            if (failedTitles.isNotEmpty()) showVoiceFeedback(voiceState, t("Bazı kartlar kaydedilemedi. Kalan kartların tüm bilgileri korundu."))
            requiresTimeSelection = false
            pendingDateHintEpochDay = null
            pendingTimeOfDay = null
            onAlertChange(null, RecurrenceRule.NONE)
            draftCategoryId = if (customCategory != null) customCategory.id else null
            voiceCategoryExplicit = false
            focusManager.clearFocus()
            keyboard?.hide()
            if (createdCount > 0) {
                RealHaptics.success(view)
                if (failedIntents.isEmpty()) voiceState.feedbackText.value = null
            }
            return
        }

        val prepared = preparedIntents.first()
        val intent = prepared.intent
        val musicBlocks = prepared.musicBlocks
        val mediaBlocks = prepared.mediaBlocks
        voiceState.retryVoiceCreation.value = null
        onDraftChange(intent.title.ifBlank { input.draft })
        pendingVoiceDurationMinutes = intent.durationMinutes
        pendingVoicePeople = intent.people
        pendingVoiceLocation = voiceLocation(intent)
        pendingVoiceRepeatMask = intent.weekdaysMask
        pendingVoiceRepeatUntil = intent.repeatUntil?.toEpochDay()
        pendingVoiceMusicBlocks = musicBlocks
        pendingVoiceMediaBlocks = mediaBlocks

        val matchedCategoryId = voiceCategoryId(intent, createdCategory)
        if (matchedCategoryId != null) {
            draftCategoryId = matchedCategoryId
            voiceCategoryExplicit = intent.categoryName != null || createdCategory != null
        }

        isChecklistMode = intent.isChecklist
        checklistDraftItems = if (intent.isChecklist) intent.checklistItems else listOf("")
        if (intent.isChecklist && intent.checklistItems.isNotEmpty()) {
            isChecklistMode = true
            checklistDraftItems = intent.checklistItems
        }

        // Keep an explicitly chosen manual date when the speech itself is unscheduled.
        // Otherwise replace the previous voice draft's schedule, rather than leaking it into a new task.
        if (intent.hasSchedule || !manualDateClearedOrChanged) {
            manualDateClearedOrChanged = false
            onAlertChange(prepared.schedule.alertAt, prepared.schedule.repeatRule)
            pendingDateHintEpochDay = prepared.schedule.plannedEpochDay
            pendingTimeOfDay = intent.timeOfDay
            requiresTimeSelection = prepared.schedule.needsTime
        }
        sendImmediateNotification = false
        if (requiresTimeSelection) {
            editing = true
            voiceState.timeFollowUp.value = VoiceTimeFollowUp(prepared, createdCategory, autoSave)
            showVoiceFeedback(voiceState, t("Saat kaçta olsun?"))
            voiceState.requestTimeListening.value = true
        }

        voiceAiParsing = false
        if (!requiresTimeSelection) {
            voiceState.timeFollowUp.value = null
            voiceState.requestTimeListening.value = false
        }
        if (autoSave && !requiresTimeSelection) pendingAutoSubmit = true
    }

    lateinit var onVoiceFinalize: (String) -> Unit
    onVoiceFinalize = { rawVoiceText ->
        val transcript = rawVoiceText.trim()
        val transcriptAlternatives = VoiceRecognitionAlternatives.consume(transcript)
        val lowConfidenceWords = VoiceRecognitionAlternatives.consumeLowConfidence()
        // A corrected sentence (picked or typed by the person) is not questioned again.
        val skipUnderstandingCheck = voiceState.skipNextUnderstandingCheck.value
        voiceState.skipNextUnderstandingCheck.value = false
        voiceState.understandingCheck.value = null
        voiceState.resubmitTypedCorrection.value = false
        voiceState.placeFix.value = null
        if (transcript.isNotBlank() && !composerState.saving.value) {
            val timeFollowUp = voiceState.timeFollowUp.value
            voiceState.requestTimeListening.value = false
            val cancelledTimeReply = timeFollowUp != null && voiceNameKey(transcript) in setOf("iptal", "vazgec", "bosver")
            // "Saat kaçta olsun?" -> "bilmiyorum / belirsiz / fark etmez": keep the day, no clock, stop asking.
            val noClockReply = timeFollowUp != null && !cancelledTimeReply && isNoClockReply(transcript)
            // On the home screen the voice window is the only UI: save automatically after the AI answer.
            val autoSave = category == null && customCategory == null
            // Keep the recognized words visible immediately; AI only restructures them.
            onDraftChange(transcript)
            isVoiceDraft = true
            voiceState.feedbackText.value = null
            voiceAiParsing = true
            pendingVoiceDurationMinutes = null
            pendingVoicePeople = emptyList()
            pendingVoiceLocation = null; pendingVoiceRepeatMask = 0; pendingVoiceRepeatUntil = null
            pendingVoiceMusicBlocks = emptyList()
            pendingVoiceMediaBlocks = emptyList()
            pendingBulkPlan = null
            voiceState.pendingDeletePlan.value = null
            voiceState.retryVoiceCreation.value = null
            pendingAutoSubmit = false
            voiceAiRequestSerial += 1L
            val requestSerial = voiceAiRequestSerial

            scope.launch {
                if (noClockReply && timeFollowUp != null) {
                    voiceState.timeFollowUp.value = null
                    voiceState.requestTimeListening.value = false
                    val pending = timeFollowUp.prepared.intent
                    val repeating = pending.recurrence != com.cagri.real.data.RecurrenceRule.NONE || pending.weekdaysMask != 0
                    val dayOnly = pending.copy(time = null, requiresTimeSelection = false, timeOfDay = null,
                        hasSchedule = true, date = pending.date ?: java.time.LocalDate.now())
                    val prepared = if (repeating) null else runCatching {
                        timeFollowUp.prepared.copy(intent = dayOnly,
                            schedule = resolveVoiceCreationSchedule(dayOnly, System.currentTimeMillis()))
                    }.getOrNull()
                    if (prepared == null) {
                        // A repeat needs a clock for its alarm; leave the choice to the picker.
                        voiceAiParsing = false
                        onDraftChange(pending.title)
                        editing = true
                        showVoiceFeedback(voiceState, t("Tekrar eden kart için saat gerekiyor. Saati elle seçebilirsin."))
                        return@launch
                    }
                    applyCreation(GeminiVoiceParseResult(intents = listOf(dayOnly)), timeFollowUp.autoSave, requestSerial,
                        timeFollowUp.createdCategory, listOf(prepared))
                    return@launch
                }
                if (cancelledTimeReply && timeFollowUp != null) {
                    voiceState.timeFollowUp.value = null
                    voiceState.requestTimeListening.value = false
                    voiceAiParsing = false
                    onDraftChange(timeFollowUp.prepared.intent.title)
                    showVoiceFeedback(voiceState, t("Saati elle seçebilirsin."))
                    return@launch
                }
                val nowMillis = System.currentTimeMillis()
                val editTarget = reminders.firstOrNull { it.id == lastVoiceCardId }
                    ?.takeIf { nowMillis - lastVoiceCardAt in 0 until 10 * 60 * 1000L }
                val clockTranscript = timeFollowUp?.let { followUp ->
                    val hint = if (Regex("\\b(sabah\\w*|aksam\\w*|oglen\\w*|gece\\w*)\\b").containsMatchIn(voiceNameKey(transcript))) "" else when (followUp.prepared.intent.timeOfDay) {
                        TimeOfDay.Morning -> "sabah"
                        TimeOfDay.Noon -> t("öğlen")
                        TimeOfDay.Evening -> t("akşam")
                        TimeOfDay.Night -> "gece"
                        else -> ""
                    }
                    val date = followUp.prepared.intent.date?.toString().orEmpty()
                    // Only the clock is read from this response; native task fields remain authoritative.
                    "${followUp.prepared.intent.title} ${date} ${hint} saat ${transcript}"
                }
                val localTranscript = if(timeFollowUp == null) transcript else {
                    val hasDaypart=Regex("\\b(sabah\\w*|aksam\\w*|oglen\\w*|gece\\w*)\\b").containsMatchIn(voiceNameKey(transcript))
                    val hint=if(hasDaypart) "" else when(timeFollowUp.prepared.intent.timeOfDay) {
                        TimeOfDay.Morning -> "sabah"
                        TimeOfDay.Noon -> t("öğlen")
                        TimeOfDay.Evening -> t("akşam")
                        TimeOfDay.Night -> "gece"
                        else -> ""
                    }
                    "$hint $transcript".trim()
                }
                val localParsed = withContext(Dispatchers.Default) {
                    runCatching { LocalVoiceIntentResolver.resolve(
                        transcript=localTranscript, cards=reminders, categories=customCategories,
                        lastCard=editTarget, now=java.time.Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()),
                        alternatives=if(timeFollowUp == null) transcriptAlternatives else emptyList(),
                        timeFollowUp=timeFollowUp != null
                    ) }.getOrNull()
                }
                if (voiceAiRequestSerial != requestSerial) return@launch
                val aiResult: Result<GeminiVoiceParseResult> = if(localParsed != null) {
                    Result.success(localParsed)
                } else {
                    // Build the network context only when the entire utterance needs AI interpretation.
                    val recentCards = if(timeFollowUp == null)
                        VoiceRecentCards.build(reminders, customCategories, editTarget?.id, nowMillis, transcript = transcript)
                        else emptyList()
                    GeminiVoiceIntentClient.parse(
                        transcript = clockTranscript ?: transcript,
                        categories = customCategories,
                        transcriptAlternatives = if (timeFollowUp == null) transcriptAlternatives else emptyList(),
                        recentCards = recentCards
                    )
                }
                // If the user already pressed save while AI was working, this response
                // belongs to an old composer session and must not touch current state.
                if (voiceAiRequestSerial != requestSerial) return@launch

                val parsed = aiResult.getOrNull()
                if (parsed == null) {
                    onDraftChange(timeFollowUp?.prepared?.intent?.title ?: transcript)
                    editing = true
                    showVoiceFeedback(voiceState,
                        if (aiResult.exceptionOrNull() is com.cagri.real.platform.android.access.ProRequired)
                            t("AI yardımı REAL Pro ile kullanılabilir. Metnin hazır; kart olarak ekleyebilirsin.")
                        else if (aiResult.exceptionOrNull() is com.cagri.real.ai.AiConsentDeclined)
                            t("AI kullanılmadı. Metnin hazır; düzenleyebilir veya kart olarak ekleyebilirsin.")
                        else t("AI çözümlenemedi. Metin korundu; tekrar söyleyebilir veya düzenleyebilirsin."))
                    voiceAiParsing = false
                    pendingAutoSubmit = false
                    return@launch
                }

                if (timeFollowUp != null) {
                    val answer = parsed.intents.singleOrNull()
                    val time = answer?.takeUnless { it.requiresTimeSelection }?.time
                    if (time == null || parsed.action != null || parsed.newCategory != null) {
                        onDraftChange(timeFollowUp.prepared.intent.title)
                        voiceAiParsing = false
                        pendingAutoSubmit = false
                        editing = true
                        if (answer?.timeOfDay != null) {
                            voiceState.timeFollowUp.value = timeFollowUp.copy(prepared = timeFollowUp.prepared.copy(
                                intent = timeFollowUp.prepared.intent.copy(timeOfDay = answer.timeOfDay)))
                        }
                        showVoiceFeedback(voiceState, t("Saat kaçta olsun?"))
                        voiceState.requestTimeListening.value = true
                        return@launch
                    }
                    val corrected = timeFollowUp.prepared.intent.copy(time = time,
                        requiresTimeSelection = false, hasSchedule = true, timeOfDay = answer?.timeOfDay ?: timeFollowUp.prepared.intent.timeOfDay)
                    val prepared = try {
                        timeFollowUp.prepared.copy(intent = corrected,
                            schedule = resolveVoiceCreationSchedule(corrected, System.currentTimeMillis()))
                    } catch (error: Exception) {
                        onDraftChange(timeFollowUp.prepared.intent.title)
                        voiceAiParsing = false
                        editing = true
                        showVoiceFeedback(voiceState, error.message ?: t("Başka bir saat söyle."))
                        return@launch
                    }
                    voiceState.timeFollowUp.value = null
                    voiceState.requestTimeListening.value = false
                    applyCreation(parsed.copy(intents = listOf(corrected), newCategory = null, edit = null, action = null),
                        timeFollowUp.autoSave, requestSerial, timeFollowUp.createdCategory, listOf(prepared))
                    return@launch
                }

                suspend fun proceed(parsed: GeminiVoiceParseResult) {
                    val action = parsed.action
                    val editRequest = parsed.edit
                    if (action != null || (editRequest != null && editTarget != null)) {
                        val liveCards = liveReminders?.invoke() ?: reminders
                        val targetId = action?.targetId ?: editTarget?.id
                        val original = reminders.firstOrNull { it.id == targetId }
                        val current = liveCards.firstOrNull { it.id == targetId }
                        if (targetId != null && (current == null || original?.updatedAt != current.updatedAt)) {
                            voiceAiParsing = false; pendingAutoSubmit = false
                            onDraftChange(""); isVoiceDraft = false
                            showVoiceFeedback(voiceState, t("Kart bu sırada değişmiş. Komutu yeniden söyle; işlem yapılmadı."))
                            return
                        }
                        voiceAiParsing = false
                        pendingAutoSubmit = false
                        onDraftChange("")
                        isVoiceDraft = false
                        requiresTimeSelection = false
                        focusManager.clearFocus()
                        keyboard?.hide()
                        val outcome = runVoiceCardAction(
                            action = action,
                            editRequest = editRequest,
                            editTarget = current ?: editTarget,
                            reminders = liveCards,
                            categories = customCategories,
                            callbacks = voiceCardCallbacks,
                            onEdit = onVoiceEdit,
                            onToggleCompleted = onToggleCompleted,
                            onPlan = { pendingBulkPlan = it },
                            onDeletePlan = { voiceState.pendingDeletePlan.value = it },
                            onFollowUp = { id ->
                                lastVoiceCardId = id
                                lastVoiceCardAt = System.currentTimeMillis()
                            }
                        )
                        if (outcome.success) RealHaptics.success(view)
                        if (outcome.message.isNotBlank()) showVoiceFeedback(voiceState, outcome.message)
                        return
                    }

                    try { applyCreation(parsed, autoSave, requestSerial) }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) {
                        if (voiceAiRequestSerial == requestSerial) {
                            voiceAiParsing = false; pendingAutoSubmit = false; editing = true
                            showVoiceFeedback(voiceState, error.message ?: t("Kartlar kaydedilemedi; metin korundu."))
                        }
                    }
                }

                // "Doğru mu anladım?": the AI is not sure what it heard. Nothing is saved or changed until the person
                // confirms (swipe up), picks another word, or types the fix.
                val heard = parsed.heard
                // Even a confident AI is asked about: names on the card that were never said (a misheard
                // foreign word turned into "When Kitchen"), shown underlined for a yes / fix.
                val substituted = if (skipUnderstandingCheck || parsed.action != null) emptyList()
                    else VoiceSubstitutions.names(transcript, transcriptAlternatives, parsed.intents)
                val shown = when {
                    skipUnderstandingCheck -> null
                    heard != null && !heard.confident -> VoiceUnderstanding.build(transcript, heard, lowConfidenceWords)
                    substituted.isNotEmpty() -> VoiceSubstitutions.text(
                        heard?.understood?.takeIf { u -> substituted.all { u.contains(it, ignoreCase = true) } }
                            ?: parsed.intents.joinToString(" · ") { listOfNotNull(it.title, it.locationName).joinToString(" — ") },
                        substituted)
                    else -> null
                }
                if (shown != null) {
                    voiceAiParsing = false
                    pendingAutoSubmit = false
                    voiceState.understandingCheck.value = VoiceUnderstandingCheck(
                        initial = shown,
                        confirm = { finalText ->
                            voiceState.understandingCheck.value = null
                            if (finalText.trim() == shown.text.trim() && voiceAiRequestSerial == requestSerial) {
                                voiceAiParsing = true
                                scope.launch { proceed(parsed) }
                            } else if (finalText.isNotBlank()) {
                                voiceState.skipNextUnderstandingCheck.value = true
                                onVoiceFinalize(finalText)
                            }
                        },
                        correct = { text ->
                            voiceState.understandingCheck.value = null
                            voiceAiRequestSerial += 1L
                            onDraftChange(text)
                            isVoiceDraft = true
                            editing = true
                            voiceState.resubmitTypedCorrection.value = true
                        },
                        cancel = {
                            voiceState.understandingCheck.value = null
                            voiceAiRequestSerial += 1L
                            onDraftChange("")
                            isVoiceDraft = false
                        },
                        place = parsed.intents.firstNotNullOfOrNull { it.locationName?.takeIf(String::isNotBlank) },
                        fixPlace = {
                            // The phone could not hear the place: the person types it, REAL finds the address and
                            // saves the same card with that place (no second AI call).
                            val oldPlace = parsed.intents.firstNotNullOfOrNull { it.locationName?.takeIf(String::isNotBlank) }.orEmpty()
                            voiceState.understandingCheck.value = null
                            onDraftChange(oldPlace)
                            isVoiceDraft = true
                            editing = true
                            voiceState.placeFix.value = { typed ->
                                val place = typed.trim()
                                voiceState.placeFix.value = null
                                if (place.isNotBlank() && voiceAiRequestSerial == requestSerial) {
                                    val oldName = oldPlace.substringBefore(',').trim()
                                    val newName = place.substringBefore(',').trim()
                                    val fixed = parsed.copy(intents = parsed.intents.map { intent ->
                                        if (intent.locationName.isNullOrBlank()) intent
                                        else intent.copy(locationName = place,
                                            title = if (oldName.length >= 2) intent.title.replace(oldName, newName, ignoreCase = true) else intent.title)
                                    })
                                    voiceAiParsing = true
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                    scope.launch { proceed(fixed) }
                                }
                            }
                        }
                    )
                    return@launch
                }
                proceed(parsed)

            }
        }
    }
    onVoiceFinalize
} } } }


/** Answers to "Saat kaçta olsun?" that mean "no particular time". */
internal fun isNoClockReply(transcript: String): Boolean {
    val key = voiceNameKey(transcript)
    return NoClockReply.containsMatchIn(key)
}

private val NoClockReply = Regex(
    "\\b(bilmiyorum|bilmem|bilemiyorum|belirsiz|belli degil|belli olmaz|fark etmez|farketmez|farketmiyor|" +
        "fark etmiyor|saat yok|saatsiz|saat olmasin|onemli degil|sonra bakarim|sonra belli olur|bakariz|gun yeter|" +
        "sadece gun|i don'?t know|dont know|no time|any time|whenever|not sure)\\b"
)
