package com.cagri.bigboscagri.ui

import android.os.Build
import android.app.Activity
import com.cagri.bigboscagri.data.AttachmentType
import com.cagri.bigboscagri.data.ReminderRepository
import com.cagri.bigboscagri.ui.components.ReminderContentSections
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import android.graphics.BitmapFactory
import com.cagri.bigboscagri.ui.components.LiquidGlassTokens
import com.cagri.bigboscagri.ui.components.liquidContentEdge
import com.cagri.bigboscagri.ui.components.liquidChrome
import com.cagri.bigboscagri.ui.components.DesignPanel
import com.cagri.bigboscagri.ui.components.AnimatedDateTimePickerOverlay
import com.cagri.bigboscagri.ui.components.NotificationPermissionSheet
import com.cagri.bigboscagri.ui.components.LocationGuideSheet
import androidx.compose.material3.TextButton
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File
import com.cagri.bigboscagri.data.ReminderAttachment
import com.cagri.bigboscagri.data.ReminderLocation
import com.cagri.bigboscagri.data.MusicBlock
import com.cagri.bigboscagri.data.ReminderVoiceRecording
import com.cagri.bigboscagri.data.VoiceRecordingStorage
import com.cagri.bigboscagri.ui.components.ReminderLocationMap
import com.cagri.bigboscagri.ui.components.MusicSearchSheet
import com.cagri.bigboscagri.ui.components.MusicContentSection
import com.cagri.bigboscagri.ui.components.TimelineContent
import com.cagri.bigboscagri.ui.components.VoiceRecorderInline
import com.cagri.bigboscagri.ui.components.openReminderLocationInMaps
import com.cagri.bigboscagri.data.AttachmentStorage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalView
import com.cagri.bigboscagri.ui.components.RealHaptics
import com.cagri.bigboscagri.ui.components.pressScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.key
import com.cagri.bigboscagri.data.ChecklistItem
import com.cagri.bigboscagri.data.JournalMood
import com.cagri.bigboscagri.data.MoodDetails
import com.cagri.bigboscagri.data.MoodScope
import com.cagri.bigboscagri.data.RecurrenceRule
import com.cagri.bigboscagri.data.ReminderItem
import com.cagri.bigboscagri.data.buildReminderShareText
import com.cagri.bigboscagri.data.repeatSummary
import com.cagri.bigboscagri.data.descriptionFor
import com.cagri.bigboscagri.ui.components.ActionGlyph
import com.cagri.bigboscagri.ui.components.ActionIcon
import com.cagri.bigboscagri.ui.components.journalMoodLabel
import com.cagri.bigboscagri.ui.components.MoodGlyph
import com.cagri.bigboscagri.ui.components.MoodV2Selector
import com.cagri.bigboscagri.ui.components.MoodV2FullScreen
import com.cagri.bigboscagri.ui.components.MoodV2Face
import com.cagri.bigboscagri.ui.components.moodV2Tint
import com.cagri.bigboscagri.ui.components.moodV2FamilyTitle
import com.cagri.bigboscagri.ui.components.moodDescriptorLabelV2
import com.cagri.bigboscagri.ui.components.moodFactorLabelV2
import com.cagri.bigboscagri.ui.components.DeleteReminderPrompt
import com.cagri.bigboscagri.data.CustomCategory
import com.cagri.bigboscagri.ui.components.CategoryIcon
import com.cagri.bigboscagri.ui.components.CategoryIconGlyph
import com.cagri.bigboscagri.ui.components.CategorySelectionOverlay
import com.cagri.bigboscagri.data.DEFAULT_GRADIENT_BLEND_FRACTION
import com.cagri.bigboscagri.ui.components.ReminderColorChoices
import com.cagri.bigboscagri.ui.components.ReminderColorPicker
import com.cagri.bigboscagri.ui.components.reminderAccentBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.lerp
import com.cagri.bigboscagri.data.resolveGradientBlendFraction
import com.cagri.bigboscagri.notifications.NowBarReminderController
import com.cagri.bigboscagri.ui.components.TopGlassOverlay
import com.cagri.bigboscagri.ui.components.REAL_PET_PERCH_DETAIL_BOTTOM_BAR
import com.cagri.bigboscagri.ui.components.RealPetRuntime
import com.cagri.bigboscagri.ui.components.realPetDetailBlockPerchId
import com.cagri.bigboscagri.ui.components.realPetPerch
import com.cagri.bigboscagri.ui.theme.BigBosTheme

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ReminderDetailScreen(
    reminder: ReminderItem, onBack: () -> Unit, onToggleCompleted: () -> Unit,
    onToggleImportant: () -> Unit, onSave: suspend (String, Int?, Int?, Long?, List<ChecklistItem>?, Int?, Float?, Int?, List<ReminderAttachment>?, List<ReminderVoiceRecording>?, ReminderLocation?, String?, List<MusicBlock>, List<String>?) -> Boolean, onDelete: () -> Unit,
    incomingLocation: ReminderLocation? = null,
    onIncomingLocationHandled: () -> Unit = {},
    onRequestLocationShare: () -> Unit = {},
    onResolveLocationPreview: () -> Unit = {},
    onSaveJournal: (JournalMood?, String) -> Unit = { _, _ -> },
    onDeleteJournal: () -> Unit = {},
    onAddMoodCard: (JournalMood, MoodDetails) -> Unit = { _, _ -> },
    customCategories: List<CustomCategory> = emptyList(),
    reduceAnimations: Boolean = false,
    reduceDynamicBlur: Boolean = false,
    designStyle: DesignStyle = DesignStyle.Glass,
    onSetAlert: (Long?, RecurrenceRule) -> Unit = { _, _ -> },
    onToggleChecklistItem: (Long, Long) -> Unit = { _, _ -> },
    exactAlarmsAllowed: Boolean = true,
    onRequestExactAlarms: () -> Unit = {},
    notificationsAllowed: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    var editing by rememberSaveable(reminder.id) { mutableStateOf(false) }
    var editedText by rememberSaveable(reminder.id) { mutableStateOf(reminder.text) }
    var accentStart by rememberSaveable(reminder.id) { mutableStateOf<Int?>(reminder.accentStart) }
    var accentEnd by rememberSaveable(reminder.id) { mutableStateOf<Int?>(reminder.accentEnd) }
    var customSolidColorArgb by rememberSaveable(reminder.id) { mutableStateOf(reminder.customSolidColorArgb) }
    var gradientBlendFraction by rememberSaveable(reminder.id) {
        mutableFloatStateOf(reminder.gradientBlendFraction ?: DEFAULT_GRADIENT_BLEND_FRACTION)
    }
    var starBorderColorIndex by rememberSaveable(reminder.id) {
        mutableIntStateOf(reminder.starBorderColorIndex ?: Math.floorMod(reminder.id, 10))
    }
    var selectedCategoryId by rememberSaveable(reminder.id) { mutableStateOf(reminder.customCategoryId) }
    var editedChecklistItems by remember(reminder.id, editing) { mutableStateOf(reminder.checklistItems) }
    var editedContentBlockOrder by remember(reminder.id, editing) { mutableStateOf(reminder.contentBlockOrder) }
    var isReorderingBlocks by rememberSaveable(reminder.id) { mutableStateOf(false) }
    var newChecklistItemText by rememberSaveable(reminder.id) { mutableStateOf("") }
    var secondaryPanelsExpanded by rememberSaveable(reminder.id) { mutableStateOf(false) }
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val detailContentScroll = rememberScrollState()
    var checklistBlockOpen by remember(reminder.id, editing) { mutableStateOf(reminder.checklistItems.isNotEmpty()) }
    val firstItemFocusRequester = remember { FocusRequester() }
    var shouldFocusFirstItem by remember { mutableStateOf(false) }

    DisposableEffect(reminder.id) {
        RealPetRuntime.noteDetailEntered(reminder.id)
        onDispose {
            RealPetRuntime.noteDetailExited(reminder.id)
        }
    }

    LaunchedEffect(shouldFocusFirstItem) {
        if (shouldFocusFirstItem) {
            shouldFocusFirstItem = false
            delay(80)
            try {
                firstItemFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }
    var checklistToolsExpanded by remember(reminder.id) { mutableStateOf(true) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showNotificationPermissionSheet by rememberSaveable { mutableStateOf(false) }
    var pendingDetailDatePicker by rememberSaveable(reminder.id) { mutableStateOf(false) }
    var editedAttachments by remember(reminder.id, editing) { mutableStateOf(reminder.attachments) }
    var editedVoiceRecordings by remember(reminder.id, editing) { mutableStateOf(reminder.voiceRecordings) }
    var editedMusicBlocks by remember(reminder.id, editing) { mutableStateOf(reminder.musicBlocks) }
    var musicSearchOpen by remember(reminder.id) { mutableStateOf(false) }
    var voiceRecorderOpen by remember(reminder.id) { mutableStateOf(false) }
    var voiceRecordingActive by remember(reminder.id) { mutableStateOf(false) }
    var editedReferenceUrl by remember(reminder.id, editing) { mutableStateOf(reminder.referenceUrl) }
    var attachmentBusy by remember { mutableStateOf(false) }
    var savingDetail by remember { mutableStateOf(false) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    var editGeneration by remember { mutableStateOf(0) }
    var screenAlive by remember(reminder.id) { mutableStateOf(true) }
    val attachmentScope = LocalLifecycleOwner.current.lifecycleScope
    var editedLocation by remember(reminder.id) { mutableStateOf(reminder.location) }
    var journalEditing by remember(reminder.id) { mutableStateOf(false) }
    var journalCreating by remember(reminder.id) { mutableStateOf(false) }
    // Journal and Mood V2 are separate systems. Existing journal moods remain readable
    // for backwards compatibility, but new mood entries are standalone cards.
    var editedJournalMood by remember(reminder.id, journalEditing, journalCreating) { mutableStateOf(reminder.journal?.mood) }
    var editedJournalText by remember(reminder.id, journalEditing, journalCreating) { mutableStateOf(reminder.journal?.text ?: "") }
    var showMoodSelector by remember(reminder.id, journalEditing, journalCreating) { mutableStateOf(false) }
    var showMoodFullscreen by remember(reminder.id) { mutableStateOf(false) }
    var standaloneMoodDraft by remember(reminder.id) { mutableStateOf(JournalMood.NEUTRAL) }

    var baselineAttachments by remember(reminder.id, editing) { mutableStateOf(reminder.attachments) }
    var baselineVoiceRecordings by remember(reminder.id, editing) { mutableStateOf(reminder.voiceRecordings) }
    var baselineReferenceUrl by remember(reminder.id, editing) { mutableStateOf(reminder.referenceUrl) }
    LaunchedEffect(reminder.attachments, reminder.voiceRecordings, reminder.referenceUrl, savingDetail) {
        if (editing && !savingDetail) {
            val oldIds = baselineAttachments.map { it.id }.toSet()
            val currentIds = reminder.attachments.map { it.id }.toSet()
            editedAttachments = (editedAttachments.filter { it.id !in oldIds || it.id in currentIds } +
                reminder.attachments.filter { it.id !in oldIds }).distinctBy { it.id }
            val oldVoiceIds = baselineVoiceRecordings.map { it.id }.toSet()
            val currentVoiceIds = reminder.voiceRecordings.map { it.id }.toSet()
            editedVoiceRecordings = (editedVoiceRecordings.filter { it.id !in oldVoiceIds || it.id in currentVoiceIds } +
                reminder.voiceRecordings.filter { it.id !in oldVoiceIds }).distinctBy { it.id }
            if (editedReferenceUrl == baselineReferenceUrl) editedReferenceUrl = reminder.referenceUrl
            baselineAttachments = reminder.attachments
            baselineVoiceRecordings = reminder.voiceRecordings
            baselineReferenceUrl = reminder.referenceUrl
        }
    }

    var newlyAddedAttachments by remember(reminder.id, editing) { mutableStateOf<List<ReminderAttachment>>(emptyList()) }
    var newlyAddedVoiceRecordings by remember(reminder.id, editing) { mutableStateOf<List<ReminderVoiceRecording>>(emptyList()) }
    var previewAttachmentPath by rememberSaveable(reminder.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(previewAttachmentPath, showMoodFullscreen) {
        if (previewAttachmentPath != null || showMoodFullscreen) {
            // Full-screen media replaces the content surface; let the cat physically fall
            // instead of hovering over a card hidden behind the photo/fullscreen overlay.
            RealPetRuntime.requestSurfaceDrop()
        }
    }
    val view = LocalView.current
    val context = LocalContext.current
    var showLocationGuide by rememberSaveable(reminder.id) { mutableStateOf(false) }
    var locationJustShared by remember(reminder.id) { mutableStateOf(false) }
    LaunchedEffect(reminder.id, reminder.location?.mapsUrl, reminder.location?.latitude) {
        if (reminder.location?.mapsUrl != null && reminder.location?.latitude == null) onResolveLocationPreview()
    }
    LaunchedEffect(incomingLocation) {
        if (incomingLocation != null) {
            editing = true
            editedLocation = incomingLocation
            locationJustShared = true
            onIncomingLocationHandled()
        }
    }
    val starInteractionSource = remember(reminder.id) { MutableInteractionSource() }
    val nowBarInteractionSource = remember(reminder.id) { MutableInteractionSource() }
    var nowBarPinned by remember(reminder.id) {
        mutableStateOf(NowBarReminderController.isManuallyPinned(context, reminder.id))
    }
    val nowBarEnabled = NowBarReminderController.isEnabled(context)
    val nowBarAvailable = nowBarEnabled && !reminder.isCompleted
    LaunchedEffect(reminder.id, reminder.isCompleted, nowBarEnabled) {
        nowBarPinned = nowBarEnabled && !reminder.isCompleted &&
            NowBarReminderController.isManuallyPinned(context, reminder.id)
    }

    
    fun discardDraft(items: List<ReminderAttachment>) {
        attachmentScope.launch(Dispatchers.IO + NonCancellable) {
            synchronized(ReminderRepository.mutationLock) {
                val used = ReminderRepository(context).getAllAttachmentPaths()
                AttachmentStorage.deleteAttachmentFilePaths(context, items.map { it.localPath }.filterNot { it in used })
            }
        }
    }
    fun discardVoiceDraft(items: List<ReminderVoiceRecording>) {
        attachmentScope.launch(Dispatchers.IO + NonCancellable) {
            synchronized(ReminderRepository.mutationLock) {
                val used = ReminderRepository(context).getAllVoiceRecordingPaths()
                VoiceRecordingStorage.deleteRecordingPaths(context, items.map { it.localPath }.filterNot { it in used })
            }
        }
    }
    val pendingDraftFiles by androidx.compose.runtime.rememberUpdatedState(newlyAddedAttachments)
    val pendingDraftVoices by androidx.compose.runtime.rememberUpdatedState(newlyAddedVoiceRecordings)
    DisposableEffect(reminder.id) {
        screenAlive = true
        onDispose {
            screenAlive = false
            if (!savingDetail) {
                discardDraft(pendingDraftFiles)
                discardVoiceDraft(pendingDraftVoices)
            }
        }
    }
    fun importPicked(uris: List<android.net.Uri>, flags: Int, photosOnly: Boolean) {
        if (attachmentBusy || savingDetail || !editing || uris.isEmpty()) return
        if (uris.size > 20) { attachmentError = "Bir seferde en fazla 20 dosya seçebilirsin"; return }
        val generation = editGeneration
        val slots = 3 - editedAttachments.count { it.type == AttachmentType.IMAGE }
        attachmentBusy = true; attachmentError = null
        attachmentScope.launch {
            val imported = mutableListOf<ReminderAttachment>()
            var failed = 0
            withContext(Dispatchers.IO + NonCancellable) {
                uris.forEach { uri ->
                    val item = runCatching {
                        if (photosOnly) AttachmentStorage.copyUriToAttachments(context, uri)
                        else AttachmentStorage.importUri(context, uri, flags)
                    }.getOrNull()
                    if (item == null) failed++
                    else if (item.type == AttachmentType.IMAGE && imported.count { it.type == AttachmentType.IMAGE } >= slots) {
                        AttachmentStorage.deleteAttachmentFiles(context, listOf(item)); failed++
                    } else imported.add(item)
                }
                
                withContext(Dispatchers.Main + NonCancellable) {
                    if (screenAlive && editing && generation == editGeneration) {
                        editedAttachments = editedAttachments + imported
                        newlyAddedAttachments = newlyAddedAttachments + imported
                        if (failed > 0) attachmentError = "$failed ek eklenemedi. Fotoğraf sınırı: 3; kopyalama sınırı: 100 MB."
                    } else discardDraft(imported)
                    attachmentBusy = false
                }
            }
        }
    }
    val detailPhotoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { uris ->
        importPicked(uris, 0, photosOnly = true)
    }
    val detailFilePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val uris = buildList {
                data.data?.let(::add)
                data.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(::add) }
            }.distinct()
            importPicked(uris, data.flags, photosOnly = false)
        }
    }

    LaunchedEffect(notificationsAllowed) {
        if (notificationsAllowed && pendingDetailDatePicker) {
            showDatePicker = true
            pendingDetailDatePicker = false
        }
    }

    LaunchedEffect(reminder.isImportant, reminder.starBorderColorIndex) {
        if (reminder.starBorderColorIndex != null) {
            starBorderColorIndex = reminder.starBorderColorIndex
        } else if (reminder.isImportant) {
            starBorderColorIndex = Math.floorMod(reminder.id, 10)
        }
    }

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scene = rememberGraphicsLayer()
    val statusHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var scenePosition by remember { mutableStateOf(Offset.Zero) }

    val requestPermissionForDatePicker: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
        if (!notificationsAllowed) {
            pendingDetailDatePicker = true
            showNotificationPermissionSheet = true
        } else {
            showDatePicker = true
        }
    }

    val finishEditing: () -> Unit = finish@ {
        if (savingDetail) return@finish
        editGeneration++
        if (newlyAddedAttachments.isNotEmpty()) {
            discardDraft(newlyAddedAttachments)
            newlyAddedAttachments = emptyList()
        }
        if (newlyAddedVoiceRecordings.isNotEmpty()) {
            discardVoiceDraft(newlyAddedVoiceRecordings)
            newlyAddedVoiceRecordings = emptyList()
        }
        journalCreating = false
        journalEditing = false
        showMoodSelector = false
        showMoodFullscreen = false
        editedJournalMood = reminder.journal?.mood
        editedJournalText = reminder.journal?.text ?: ""
        editedAttachments = reminder.attachments
        editedVoiceRecordings = reminder.voiceRecordings
        editedMusicBlocks = reminder.musicBlocks
        musicSearchOpen = false
        voiceRecorderOpen = false
        voiceRecordingActive = false
        editedReferenceUrl = reminder.referenceUrl
        editedLocation = reminder.location
        locationJustShared = false
        editing = false
        secondaryPanelsExpanded = false
        accentStart = reminder.accentStart
        accentEnd = reminder.accentEnd
        customSolidColorArgb = reminder.customSolidColorArgb
        selectedCategoryId = reminder.customCategoryId
        editedChecklistItems = reminder.checklistItems
        editedContentBlockOrder = reminder.contentBlockOrder
        isReorderingBlocks = false
        starBorderColorIndex = reminder.starBorderColorIndex ?: Math.floorMod(reminder.id, 10)
        gradientBlendFraction = reminder.gradientBlendFraction ?: DEFAULT_GRADIENT_BLEND_FRACTION
        newChecklistItemText = ""
        checklistBlockOpen = reminder.checklistItems.isNotEmpty()
        focusManager.clearFocus()
        keyboard?.hide()
    }
    BackHandler(showMoodFullscreen || previewAttachmentPath != null || isReorderingBlocks || editing || deleting || showCategoryPicker || journalEditing || journalCreating || showMoodSelector) {
        if (showMoodFullscreen) showMoodFullscreen = false
        else if (previewAttachmentPath != null) previewAttachmentPath = null
        else if (isReorderingBlocks) isReorderingBlocks = false
        else if (showCategoryPicker) showCategoryPicker = false
        else if (deleting) deleting = false
        else if (showMoodSelector) showMoodSelector = false
        else if (journalEditing || journalCreating) finishEditing()
        else finishEditing()
    }

    val chromeAlpha by if (animatedVisibilityScope != null && !reduceAnimations) {
        animatedVisibilityScope.transition.animateFloat(
            transitionSpec = {
                if (EnterExitState.PreEnter isTransitioningTo EnterExitState.Visible) {
                    tween(200, delayMillis = 80, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
                } else {
                    tween(120, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))
                }
            },
            label = "detailChromeAlpha"
        ) { state ->
            if (state == EnterExitState.Visible) 1f else 0f
        }
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    val detailHeader: @Composable () -> Unit = {
        AnimatedVisibility(
            visible = !editing,
            enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(180)),
            exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(140))
        ) {
            Row(
                Modifier.fillMaxWidth().graphicsLayer { alpha = chromeAlpha },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.cagri.bigboscagri.ui.components.DesignIconButton(
                    backdrop = scene, backdropPosition = { scenePosition },
                    designStyle = designStyle, reduceDynamicBlur = reduceDynamicBlur,
                    onClick = { if (editing) finishEditing() else onBack() },
                    modifier = Modifier.then(if (designStyle == DesignStyle.LiquidGlass) Modifier else Modifier.background(Color(0xFF161619), CircleShape))
                        .semantics { contentDescription = if (editing) "Düzenlemeyi iptal et" else "Geri" }
                ) { ActionIcon(ActionGlyph.Back, Modifier.size(23.dp)) }
                val starSettleScale = remember(reminder.id) { Animatable(1f) }
                var hasRenderedInitialStar by remember(reminder.id) { mutableStateOf(false) }
                LaunchedEffect(reminder.isImportant) {
                    if (!hasRenderedInitialStar) {
                        hasRenderedInitialStar = true
                        return@LaunchedEffect
                    }
                    if (reminder.isImportant && !reduceAnimations) {
                        starSettleScale.snapTo(1.06f)
                        starSettleScale.animateTo(1.0f, tween(160, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)))
                    } else {
                        starSettleScale.snapTo(1.0f)
                    }
                }
                val starColor by animateColorAsState(
                    targetValue = if (reminder.isImportant) Color(0xFFEACB75) else Color.White,
                    animationSpec = if (reduceAnimations) snap() else tween(160),
                    label = "starColor"
                )
                val nowBarColor by animateColorAsState(
                    targetValue = when {
                        !nowBarAvailable -> Color(0xFF66666F)
                        nowBarPinned -> Color(0xFF91B9FF)
                        else -> Color.White
                    },
                    animationSpec = if (reduceAnimations) snap() else tween(160),
                    label = "nowBarColor"
                )
                val groupedActions: @Composable () -> Unit = {
                    Row(
                        Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = {
                                if (nowBarAvailable) {
                                    val pinned = NowBarReminderController.toggleManualPin(context, reminder.id)
                                    nowBarPinned = pinned
                                }
                            },
                            enabled = nowBarAvailable,
                            interactionSource = nowBarInteractionSource,
                            modifier = Modifier
                                .size(55.dp, 48.dp)
                                .pressScale(nowBarInteractionSource, enabled = nowBarAvailable, reduceAnimations = reduceAnimations, targetScale = 0.95f)
                                .background(if (nowBarPinned && nowBarAvailable) Color(0xFF91B9FF).copy(alpha = 0.10f) else Color.Transparent)
                                .semantics {
                                    contentDescription = when {
                                        reminder.isCompleted -> "Tamamlanan hatırlatıcı Now Bar'a sabitlenemez"
                                        !nowBarEnabled -> "Now Bar ayarlardan kapalı"
                                        nowBarPinned -> "Now Bar sabitlemesini kaldır"
                                        else -> "Now Bar'a sabitle"
                                    }
                                }
                        ) {
                            ActionIcon(ActionGlyph.NowBar, Modifier.size(23.dp), nowBarColor, filled = nowBarPinned)
                        }
                        Box(Modifier.size(1.dp, 26.dp).background(Color.White.copy(alpha = 0.13f)))
                        IconButton(
                            onClick = {
                                RealHaptics.toggle(view, on = !reminder.isImportant)
                                onToggleImportant()
                            },
                            interactionSource = starInteractionSource,
                            modifier = Modifier
                                .size(55.dp, 48.dp)
                                .pressScale(starInteractionSource, reduceAnimations = reduceAnimations, targetScale = 0.95f)
                                .background(if (reminder.isImportant) Color(0xFFEACB75).copy(alpha = 0.08f) else Color.Transparent)
                                .semantics { contentDescription = if (reminder.isImportant) "Önemli işaretini kaldır" else "Önemli olarak işaretle" }
                        ) {
                            ActionIcon(
                                ActionGlyph.Star,
                                Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = starSettleScale.value
                                        scaleY = starSettleScale.value
                                    },
                                starColor,
                                filled = reminder.isImportant
                            )
                        }
                    }
                }
                if (designStyle == DesignStyle.LiquidGlass) {
                    com.cagri.bigboscagri.ui.components.LiquidGlassSurface(
                        backdrop = scene,
                        backdropPosition = { scenePosition },
                        modifier = Modifier.width(112.dp).height(48.dp),
                        corners = 24.dp,
                        reduceDynamicBlur = reduceDynamicBlur,
                        reduceAnimations = reduceAnimations
                    ) { groupedActions() }
                } else {
                    Box(
                        Modifier
                            .width(112.dp)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF161619))
                            .border(0.8.dp, Color(0xFF303036), RoundedCornerShape(24.dp))
                    ) { groupedActions() }
                }
            }
        }
    }

    val headerSpacerHeight by animateDpAsState(
        targetValue = if (editing) 0.dp else 48.dp,
        animationSpec = if (reduceAnimations) snap() else tween(180, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)),
        label = "detailHeaderSpacerHeight"
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        val activeBlockKeys = remember(
            editing, checklistBlockOpen, editedChecklistItems, reminder.isChecklist, reminder.checklistItems,
            editedAttachments, reminder.attachments, editedReferenceUrl, reminder.referenceUrl,
            editedVoiceRecordings, reminder.voiceRecordings, voiceRecorderOpen,
            editedMusicBlocks, reminder.musicBlocks,
            editedLocation, reminder.location,
            reminder.journal, journalCreating, journalEditing, reminder.mood
        ) {
            buildSet {
                add("text")
                if (editing) {
                    if (checklistBlockOpen || editedChecklistItems.isNotEmpty() || reminder.isChecklist) add("checklist")
                    if (editedAttachments.any { it.type == AttachmentType.IMAGE }) add("photos")
                    if (editedAttachments.any { it.type != AttachmentType.IMAGE } || editedReferenceUrl != null) add("files")
                    if (editedVoiceRecordings.isNotEmpty() || voiceRecorderOpen) add("voice")
                    if (editedMusicBlocks.isNotEmpty()) add("music")
                    if (editedLocation != null) add("location")
                    if (reminder.mood != null) add("mood")
                    if (reminder.journal != null || journalCreating || journalEditing) add("journal")
                } else {
                    if (reminder.checklistItems.isNotEmpty()) add("checklist")
                    if (reminder.attachments.any { it.type == AttachmentType.IMAGE }) add("photos")
                    if (reminder.attachments.any { it.type != AttachmentType.IMAGE } || reminder.referenceUrl != null) add("files")
                    if (reminder.voiceRecordings.isNotEmpty()) add("voice")
                    if (reminder.musicBlocks.isNotEmpty()) add("music")
                    if (reminder.location != null) add("location")
                    if (reminder.mood != null) add("mood")
                    if (reminder.journal != null || journalCreating) add("journal")
                }
            }
        }

        val currentOrder = remember(editedContentBlockOrder, reminder.contentBlockOrder, activeBlockKeys, editing) {
            resolveBlockOrder(if (editing) editedContentBlockOrder else reminder.contentBlockOrder, activeBlockKeys)
        }

        val blockDescriptors = remember(
            editedText, reminder.text, editedChecklistItems, reminder.checklistItems,
            editedAttachments, reminder.attachments, editedReferenceUrl, reminder.referenceUrl,
            editedVoiceRecordings, reminder.voiceRecordings, editedMusicBlocks, reminder.musicBlocks,
            editedLocation, reminder.location, reminder.journal, editedJournalMood, editedJournalText, reminder.mood,
            editing
        ) {
            mapOf(
                "text" to ContentBlockDescriptor(
                    key = "text",
                    title = "Not Metni",
                    subtitle = if (editing) editedText.ifBlank { "Boş not" } else reminder.text.ifBlank { "Boş not" },
                    glyph = ActionGlyph.Note
                ),
                "checklist" to ContentBlockDescriptor(
                    key = "checklist",
                    title = "Checklist",
                    subtitle = "${(if (editing) editedChecklistItems else reminder.checklistItems).size} madde",
                    glyph = ActionGlyph.Checklist
                ),
                "photos" to ContentBlockDescriptor(
                    key = "photos",
                    title = "Fotoğraflar",
                    subtitle = "${(if (editing) editedAttachments else reminder.attachments).count { it.type == AttachmentType.IMAGE }} fotoğraf",
                    glyph = ActionGlyph.Photo
                ),
                "files" to ContentBlockDescriptor(
                    key = "files",
                    title = "Dosyalar & Bağlantılar",
                    subtitle = run {
                        val fCount = (if (editing) editedAttachments else reminder.attachments).count { it.type != AttachmentType.IMAGE }
                        val hasUrl = (if (editing) editedReferenceUrl else reminder.referenceUrl) != null
                        if (fCount > 0 && hasUrl) "$fCount dosya • 1 bağlantı" else if (fCount > 0) "$fCount dosya" else "1 bağlantı"
                    },
                    glyph = ActionGlyph.File
                ),
                "voice" to ContentBlockDescriptor(
                    key = "voice",
                    title = "Ses Kaydı",
                    subtitle = "${(if (editing) editedVoiceRecordings else reminder.voiceRecordings).size} kayıt",
                    glyph = ActionGlyph.AudioWave
                ),
                "music" to ContentBlockDescriptor(
                    key = "music",
                    title = "Müzik",
                    subtitle = "${(if (editing) editedMusicBlocks else reminder.musicBlocks).size} parça",
                    glyph = ActionGlyph.Music
                ),
                "location" to ContentBlockDescriptor(
                    key = "location",
                    title = "Konum",
                    subtitle = (if (editing) editedLocation else reminder.location)?.placeName?.ifBlank { "Konum" } ?: "Konum",
                    glyph = ActionGlyph.Pin
                ),
                "mood" to ContentBlockDescriptor(
                    key = "mood",
                    title = "Mood",
                    subtitle = reminder.mood?.label ?: "Mood",
                    glyph = ActionGlyph.MoodVeryGood
                ),
                "journal" to ContentBlockDescriptor(
                    key = "journal",
                    title = "Günlük",
                    subtitle = "Günlük notu",
                    glyph = ActionGlyph.Note
                )
            )
        }

        if (editing && isReorderingBlocks) {
            Column(
                Modifier.fillMaxSize()
                    .then(if (deleting) Modifier.clearAndSetSemantics { } else Modifier)
                    .onGloballyPositioned { scenePosition = it.positionInRoot() }
                    .drawWithContent {
                        if (com.cagri.bigboscagri.ui.components.shouldCaptureBackdrop(designStyle, reduceDynamicBlur, Build.VERSION.SDK_INT)) {
                            scene.record { this@drawWithContent.drawContent() }
                            drawLayer(scene)
                        } else drawContent()
                    }
                    .padding(
                        start = 16.dp, end = 16.dp, top = statusHeight + 10.dp,
                        bottom = if (secondaryPanelsExpanded) 400.dp else 250.dp
                    )
            ) {
                if (designStyle == DesignStyle.LiquidGlass) {
                    if (headerSpacerHeight > 0.dp) Spacer(Modifier.height(headerSpacerHeight))
                } else detailHeader()

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("İçerik Sıralaması", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("Sıralamak için basılı tut ve sürükle", color = Color(0xFF9D9DA7), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))

                val reorderListState = rememberLazyListState()
                var draggedIndex by remember { mutableStateOf<Int?>(null) }
                var dragOffsetY by remember { mutableFloatStateOf(0f) }
                val reorderKeys = remember(editedContentBlockOrder, activeBlockKeys) {
                    mutableStateListOf<String>().apply {
                        addAll(resolveBlockOrder(editedContentBlockOrder, activeBlockKeys))
                    }
                }

                LazyColumn(
                    state = reorderListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .pointerInput(activeBlockKeys) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    reorderListState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { item ->
                                            offset.y.toInt() in item.offset..(item.offset + item.size)
                                        }
                                        ?.also { item ->
                                            draggedIndex = item.index
                                            dragOffsetY = 0f
                                        }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val currentIdx = draggedIndex ?: return@detectDragGesturesAfterLongPress
                                    dragOffsetY += dragAmount.y

                                    val currentItem = reorderListState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.index == currentIdx } ?: return@detectDragGesturesAfterLongPress

                                    val currentMid = currentItem.offset + dragOffsetY + currentItem.size / 2f
                                    val targetItem = reorderListState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { other ->
                                            other.index != currentIdx &&
                                            currentMid in other.offset.toFloat()..(other.offset + other.size).toFloat()
                                        }

                                    if (targetItem != null && targetItem.index in 0..reorderKeys.lastIndex) {
                                        val targetIdx = targetItem.index
                                        val moved = reorderKeys.removeAt(currentIdx)
                                        reorderKeys.add(targetIdx, moved)
                                        dragOffsetY += (currentItem.offset - targetItem.offset)
                                        draggedIndex = targetIdx
                                        editedContentBlockOrder = reorderKeys.toList()
                                    }
                                },
                                onDragEnd = {
                                    draggedIndex = null
                                    dragOffsetY = 0f
                                    editedContentBlockOrder = reorderKeys.toList()
                                },
                                onDragCancel = {
                                    draggedIndex = null
                                    dragOffsetY = 0f
                                }
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(reorderKeys, key = { _, key -> key }) { index, key ->
                        val desc = blockDescriptors[key] ?: return@itemsIndexed
                        val isDragging = draggedIndex == index
                        val rowBackground = if (isDragging) Color(0xFF282A33) else Color(0xFF141416)
                        val rowBorderColor = if (isDragging) Color(0xFF91B9FF).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.10f)

                        Row(
                            modifier = Modifier
                                .animateItem()
                                .zIndex(if (isDragging) 2f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffsetY else 0f
                                    scaleX = if (isDragging) 1.02f else 1f
                                    scaleY = if (isDragging) 1.02f else 1f
                                    shadowElevation = if (isDragging) 10.dp.toPx() else 0f
                                }
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(rowBackground)
                                .border(if (isDragging) 1.25.dp else 0.75.dp, rowBorderColor, RoundedCornerShape(18.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF91B9FF).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                ActionIcon(desc.glyph, Modifier.size(18.dp), Color(0xFF91B9FF))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(desc.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(desc.subtitle, color = Color(0xFF9797A2), fontSize = 12.sp, maxLines = 1)
                            }
                            ActionIcon(
                                ActionGlyph.DragHandle,
                                Modifier.size(20.dp),
                                color = if (isDragging) Color(0xFF91B9FF) else Color(0xFF75757F)
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize()
                    .then(if (deleting) Modifier.clearAndSetSemantics { } else Modifier)
                    .onGloballyPositioned { scenePosition = it.positionInRoot() }
                    .drawWithContent {
                        if (com.cagri.bigboscagri.ui.components.shouldCaptureBackdrop(designStyle, reduceDynamicBlur, Build.VERSION.SDK_INT)) { scene.record { this@drawWithContent.drawContent() }; drawLayer(scene) }
                        else drawContent()
                    }
                    .verticalScroll(detailContentScroll)
                    .padding(start = 16.dp, end = 16.dp, top = statusHeight + 10.dp,
                        bottom = if (editing) {
                            if (secondaryPanelsExpanded) 400.dp else 250.dp
                        } else 114.dp)
            ) {
                if (designStyle == DesignStyle.LiquidGlass) {
                    if (headerSpacerHeight > 0.dp) Spacer(Modifier.height(headerSpacerHeight))
                } else detailHeader()

                val cardShape = RoundedCornerShape(28.dp)
                val shownAccentStart = if (editing) accentStart else reminder.accentStart
                val shownAccentEnd = if (editing) accentEnd else reminder.accentEnd
                val shownBlendFraction = if (editing) gradientBlendFraction else reminder.gradientBlendFraction
                val shownCustomSolidColorArgb = if (editing) customSolidColorArgb else reminder.customSolidColorArgb
                val accentBrush = remember(shownAccentStart, shownAccentEnd, reminder.isCompleted, shownBlendFraction, shownCustomSolidColorArgb) {
                    reminderAccentBrush(shownAccentStart, shownAccentEnd, reminder.isCompleted, shownBlendFraction, shownCustomSolidColorArgb)
                }
                val sharedCardModifier = if (!reduceAnimations && sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedBounds(
                            rememberSharedContentState(key = "reminder-card-${reminder.id}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ -> tween(320, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)) },
                            clipInOverlayDuringTransition = OverlayClip(cardShape)
                        )
                    }
                } else Modifier

                val currentStarBorderIndex = if (editing) starBorderColorIndex else (reminder.starBorderColorIndex ?: Math.floorMod(reminder.id, 10))
                val cardBorderColor = if (reminder.isImportant) {
                    ReminderColorChoices.getOrNull(currentStarBorderIndex ?: 0)?.color?.copy(alpha = 0.85f)
                        ?: Color(0xFFEACB75).copy(alpha = 0.85f)
                } else null
                val cardBorderModifier = if (cardBorderColor != null) {
                    Modifier.border(1.25.dp, cardBorderColor, cardShape)
                } else {
                    Modifier.border(0.8.dp, if (designStyle == DesignStyle.LiquidGlass) liquidContentEdge() else Brush.verticalGradient(listOf(Color(0xFF494953), Color(0xFF252529))), cardShape)
                }

                val visibleBlocks = remember(
                    currentOrder, editing, checklistBlockOpen, editedChecklistItems, reminder.isChecklist, reminder.checklistItems,
                    editedAttachments, reminder.attachments, editedReferenceUrl, reminder.referenceUrl,
                    editedVoiceRecordings, reminder.voiceRecordings, voiceRecorderOpen,
                    editedMusicBlocks, reminder.musicBlocks,
                    editedLocation, reminder.location,
                    reminder.journal, journalCreating, journalEditing, reminder.mood
                ) {
                    currentOrder.filter { blockKey ->
                        when (blockKey) {
                            "text" -> true
                            "checklist" -> if (editing) (checklistBlockOpen || editedChecklistItems.isNotEmpty() || reminder.isChecklist) else reminder.checklistItems.isNotEmpty()
                            "photos" -> (if (editing) editedAttachments else reminder.attachments).any { it.type == AttachmentType.IMAGE }
                            "files" -> (if (editing) editedAttachments else reminder.attachments).any { it.type != AttachmentType.IMAGE } || (if (editing) editedReferenceUrl else reminder.referenceUrl) != null
                            "voice" -> (if (editing) editedVoiceRecordings else reminder.voiceRecordings).isNotEmpty() || (editing && voiceRecorderOpen)
                            "music" -> (if (editing) editedMusicBlocks else reminder.musicBlocks).isNotEmpty()
                            "location" -> (if (editing) editedLocation else reminder.location) != null
                            "mood" -> reminder.mood != null
                            "journal" -> if (editing) (reminder.journal != null || journalCreating || journalEditing) else (reminder.journal != null || journalCreating)
                            else -> false
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                visibleBlocks.forEachIndexed { blockIndex, blockKey ->
                    if (blockIndex > 0) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            TimelineConnector()
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .realPetPerch(realPetDetailBlockPerchId(reminder.id, blockKey))
                    ) {
                        when (blockKey) {
                        "text" -> {
                            Column(
                                Modifier
                                    .then(if (blockIndex == 0) sharedCardModifier else Modifier)
                                    .fillMaxWidth()
                                    .clip(cardShape)
                                    .then(
                                        if (accentBrush == null) Modifier.background(
                                            if (designStyle == DesignStyle.LiquidGlass) Brush.verticalGradient(listOf(Color(0xFF18181A), LiquidGlassTokens.contentColor))
                                            else Brush.linearGradient(listOf(Color(0xFF25252C), Color(0xFF17171B), Color(0xFF131315)))
                                        ) else Modifier.background(accentBrush)
                                    )
                                    .then(cardBorderModifier)
                                    .padding(horizontal = 22.dp, vertical = 25.dp)
                            ) {
                                if (editing) {
                                    BasicTextField(
                                        value = editedText, onValueChange = { editedText = it },
                                        modifier = Modifier.fillMaxWidth()
                                            .heightIn(min = 96.dp)
                                            .onFocusChanged { if (it.isFocused) secondaryPanelsExpanded = false }
                                            .semantics { contentDescription = "Hatırlatıcı metni" },
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontSize = 21.sp, lineHeight = 31.sp),
                                        cursorBrush = SolidColor(Color(0xFF82B3FF)),
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                                    )
                                } else {
                                    SelectionContainer {
                                        Text(
                                            reminder.text,
                                            color = if (reminder.isCompleted && reminder.journal == null) Color(0xFF9999A2) else Color(0xFFF4F4F6),
                                            fontSize = 21.sp,
                                            lineHeight = 31.sp,
                                            textDecoration = if (reminder.isCompleted && reminder.journal == null) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                    }
                                }

                                if (!editing) {
                                    HorizontalDivider(Modifier.padding(top = 24.dp, bottom = 18.dp), color = Color(0xFF38383F))
                                    val assignedCategory = customCategories.firstOrNull { it.id == reminder.customCategoryId }
                                    if (assignedCategory != null && isStandaloneCategoryInDetail(reminder)) {
                                        val startColor = ReminderColorChoices.getOrNull(assignedCategory.accentStart)?.color ?: Color(0xFF233F73)
                                        val endColor = assignedCategory.accentEnd?.let { ReminderColorChoices.getOrNull(it)?.color } ?: startColor
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                Modifier.size(32.dp).clip(CircleShape).background(Brush.linearGradient(listOf(startColor, endColor))),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CategoryIconGlyph(CategoryIcon.fromKey(assignedCategory.iconKey), Modifier.size(16.dp), Color.White)
                                            }
                                            Text(
                                                assignedCategory.name,
                                                color = Color(0xFFE2E2E8),
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    } else {
                                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            ActionIcon(
                                                if (reminder.isCompleted) ActionGlyph.Check else ActionGlyph.Note,
                                                Modifier.padding(top = 2.dp).size(21.dp),
                                                Color(0xFF94B8F7)
                                            )
                                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                val statusText = reminderDetailStatusText(reminder, hasCategory = assignedCategory != null)
                                                if (statusText != null) {
                                                    if (reminder.isCompleted || reminder.alertAt == null) {
                                                        Text(statusText, color = Color(0xFFD3D3D9), fontSize = 14.sp)
                                                    } else {
                                                        val isOverdue = reminder.alertAt < System.currentTimeMillis()
                                                        val dateColor = if (isOverdue) Color(0xFFF87171) else Color(0xFF94B8F7)
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Text(statusText, color = Color(0xFFD3D3D9), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                            Text("•", color = Color(0xFF6B6B75), fontSize = 12.sp)
                                                            Text(detailReminderDate(reminder.alertAt), color = dateColor, fontSize = 14.sp)
                                                        }
                                                        val repeatText = reminder.repeatRule.repeatSummary(reminder.anchorAlertAt ?: reminder.alertAt)
                                                        if (repeatText != null) {
                                                            Text(
                                                                repeatText,
                                                                color = Color(0xFFA5A5B0),
                                                                fontSize = 12.sp,
                                                                lineHeight = 16.sp,
                                                                modifier = Modifier.padding(top = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                if (assignedCategory != null) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier.padding(top = 1.dp)
                                                    ) {
                                                        val startColor = ReminderColorChoices.getOrNull(assignedCategory.accentStart)?.color ?: Color(0xFF233F73)
                                                        val endColor = assignedCategory.accentEnd?.let { ReminderColorChoices.getOrNull(it)?.color } ?: startColor
                                                        Box(
                                                            Modifier.size(16.dp).clip(CircleShape).background(Brush.linearGradient(listOf(startColor, endColor))),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            CategoryIconGlyph(CategoryIcon.fromKey(assignedCategory.iconKey), Modifier.size(10.dp), Color.White)
                                                        }
                                                        Text(assignedCategory.name, color = Color(0xFFC4C4CE), fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "mood" -> {
                            reminder.mood?.let { mood ->
                                val moodTint = moodV2Tint(mood)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(cardShape)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    moodTint.copy(alpha = 0.30f),
                                                    Color(0xFF17171B).copy(alpha = 0.98f),
                                                    Color(0xFF121214).copy(alpha = 0.99f)
                                                )
                                            )
                                        )
                                        .border(0.9.dp, moodTint.copy(alpha = 0.52f), cardShape)
                                        .padding(horizontal = 22.dp, vertical = 22.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                                ) {
                                    MoodV2Face(
                                        mood = mood,
                                        size = 82.dp,
                                        tint = moodTint
                                    )
                                    Column(Modifier.weight(1f)) {
                                        val moodDetails = reminder.moodDetails
                                        Text(
                                            moodDetails?.scope?.label ?: "Mood",
                                            color = moodTint.copy(alpha = 0.92f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            moodV2FamilyTitle(mood),
                                            color = Color(0xFFF7F7F9),
                                            fontSize = 23.sp,
                                            lineHeight = 29.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                        if (!moodDetails?.descriptors.isNullOrEmpty()) {
                                            Text(
                                                moodDetails!!.descriptors.take(3).joinToString(" · ") { moodDescriptorLabelV2(it) },
                                                color = Color(0xFFC9C9D0),
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                        if (!moodDetails?.factors.isNullOrEmpty()) {
                                            Text(
                                                moodDetails!!.factors.take(4).joinToString(" · ") { moodFactorLabelV2(it) },
                                                color = Color(0xFF8E8E98),
                                                fontSize = 11.sp,
                                                lineHeight = 16.sp,
                                                modifier = Modifier.padding(top = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        "checklist" -> {
                            DetailChecklistBox(
                                designStyle = designStyle,
                                editing = editing,
                                items = if (editing) editedChecklistItems else reminder.checklistItems,
                                newItemText = newChecklistItemText,
                                onNewItemTextChange = { newChecklistItemText = it },
                                onAddItem = {
                                    if (newChecklistItemText.trim().isNotBlank()) {
                                        val nextId = maxOf(
                                            System.currentTimeMillis(),
                                            (editedChecklistItems.maxOfOrNull { it.id } ?: 0L) + 1L
                                        )
                                        val newItem = ChecklistItem(id = nextId, text = newChecklistItemText.trim())
                                        editedChecklistItems = editedChecklistItems + newItem
                                        newChecklistItemText = ""
                                    }
                                },
                                onItemTextChange = { itemId, newText ->
                                    editedChecklistItems = editedChecklistItems.map {
                                        if (it.id == itemId) it.copy(text = newText) else it
                                    }
                                },
                                onDeleteItem = { itemId ->
                                    editedChecklistItems = editedChecklistItems.filter { it.id != itemId }
                                },
                                onToggleItem = { itemId ->
                                    if (editing) {
                                        editedChecklistItems = editedChecklistItems.map {
                                            if (it.id == itemId) it.toggleCompleted(System.currentTimeMillis()) else it
                                        }
                                    } else {
                                        onToggleChecklistItem(reminder.id, itemId)
                                    }
                                },
                                onRemoveChecklist = {
                                    editedChecklistItems = emptyList()
                                    newChecklistItemText = ""
                                    checklistBlockOpen = false
                                },
                                onFocusField = { secondaryPanelsExpanded = false },
                                firstItemFocusRequester = firstItemFocusRequester,
                                reduceAnimations = reduceAnimations,
                                savingDetail = savingDetail
                            )
                        }
                        "photos" -> {
                            val displayedAttachments = if (editing) editedAttachments else reminder.attachments
                            val displayedPhotos = displayedAttachments.filter { it.type == AttachmentType.IMAGE }
                            if (displayedPhotos.isNotEmpty()) {
                                AttachmentMediaSection(
                                    attachments = displayedPhotos,
                                    designStyle = designStyle,
                                    editing = editing,
                                    onOpen = { previewAttachmentPath = it.localPath },
                                    onRemove = { attachment ->
                                        editedAttachments = editedAttachments.filter { it.id != attachment.id }
                                    }
                                )
                            }
                        }
                        "files" -> {
                            val displayedAttachments = if (editing) editedAttachments else reminder.attachments
                            ReminderContentSections(
                                attachments = displayedAttachments,
                                referenceUrl = if (editing) editedReferenceUrl else reminder.referenceUrl,
                                editing = editing && !savingDetail, designStyle = designStyle,
                                backdrop = scene, backdropPosition = { scenePosition },
                                onRemove = { item -> editedAttachments = editedAttachments.filterNot { it.id == item.id } },
                                onRemoveLink = { editedReferenceUrl = null }
                            )
                        }
                        "voice" -> {
                            val displayedVoiceRecordings = if (editing) editedVoiceRecordings else reminder.voiceRecordings
                            if (displayedVoiceRecordings.isNotEmpty() || (editing && voiceRecorderOpen)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(22.dp))
                                        .then(
                                            if (designStyle == DesignStyle.LiquidGlass) Modifier.liquidChrome(corners = 22.dp)
                                            else Modifier
                                                .background(Color(0xFF141416))
                                                .border(0.7.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
                                        )
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    VoiceRecorderInline(
                                        recordings = displayedVoiceRecordings,
                                        onRecorded = { recording ->
                                            editedVoiceRecordings = editedVoiceRecordings + recording
                                            newlyAddedVoiceRecordings = newlyAddedVoiceRecordings + recording
                                        },
                                        onRemove = { recording ->
                                            editedVoiceRecordings = editedVoiceRecordings.filterNot { it.id == recording.id }
                                            if (newlyAddedVoiceRecordings.any { it.id == recording.id }) {
                                                newlyAddedVoiceRecordings = newlyAddedVoiceRecordings.filterNot { it.id == recording.id }
                                                discardVoiceDraft(listOf(recording))
                                            }
                                        },
                                        showHeader = true,
                                        compact = false,
                                        enabled = editing && !savingDetail && !attachmentBusy,
                                        onRecordingStateChange = { voiceRecordingActive = it },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        "music" -> {
                            MusicContentSection(
                                blocks = if (editing) editedMusicBlocks else reminder.musicBlocks,
                                designStyle = designStyle, editing = editing && !savingDetail,
                                onRemove = { id -> editedMusicBlocks = editedMusicBlocks.filterNot { it.id == id } }
                            )
                        }
                        "location" -> {
                            val place = if (editing) editedLocation else reminder.location
                            if (place != null) {
                                val locCardShape = RoundedCornerShape(22.dp)
                                if (place.latitude != null && place.longitude != null) {
                                    Box(
                                        Modifier.fillMaxWidth().height(180.dp)
                                            .clip(locCardShape)
                                            .background(if (designStyle == DesignStyle.LiquidGlass) LiquidGlassTokens.contentColor else Color(0xFF131820))
                                            .border(0.7.dp, Color.White.copy(alpha = 0.10f), locCardShape)
                                    ) {
                                        ReminderLocationMap(place, Modifier.fillMaxSize()) {
                                            if (!openReminderLocationInMaps(context, place))
                                                Toast.makeText(context, "Harita uygulaması açılamadı", Toast.LENGTH_SHORT).show()
                                        }
                                        if (editing) {
                                            IconButton(
                                                onClick = { editedLocation = null; locationJustShared = false },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xD918181B))
                                                    .border(0.5.dp, Color(0x4DFFFFFF), CircleShape)
                                            ) {
                                                ActionIcon(ActionGlyph.Close, Modifier.size(13.dp), Color(0xFFF1F1F4))
                                            }
                                        }
                                    }
                                } else {
                                    Box(
                                        Modifier.fillMaxWidth()
                                            .clip(locCardShape)
                                            .background(if (designStyle == DesignStyle.LiquidGlass) LiquidGlassTokens.contentColor else Color(0xFF141416))
                                            .border(0.7.dp, Color.White.copy(alpha = 0.10f), locCardShape)
                                            .padding(horizontal = 18.dp, vertical = 16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        if (!openReminderLocationInMaps(context, place)) {
                                                            Toast.makeText(context, "Harita uygulaması açılamadı", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                ActionIcon(ActionGlyph.Pin, Modifier.size(20.dp), Color(0xFF91B9FF))
                                                Column {
                                                    if (place.placeName.isNotBlank()) {
                                                        Text(place.placeName, color = Color(0xFFE2E2E8), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                                    }
                                                    if (place.address.isNotBlank()) {
                                                        Text(place.address, color = Color(0xFFA5A5B0), fontSize = 13.sp, maxLines = 2)
                                                        Text("Harita için koordinat bulunamadı", color = Color(0xFF9E9EA4), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                                                    } else {
                                                        Text("Harita için koordinat bulunamadı", color = Color(0xFFE2E2E8), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                        Text("Harita uygulamasında aç", color = Color(0xFFA5A5B0), fontSize = 13.sp)
                                                    }
                                                }
                                            }
                                            if (editing) {
                                                IconButton(
                                                    onClick = { editedLocation = null; locationJustShared = false },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    ActionIcon(ActionGlyph.Close, Modifier.size(15.dp), Color(0xFF8E8E98))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "journal" -> {
                            val showJournalCard = reminder.journal != null || journalCreating
                            AnimatedVisibility(
                                visible = showJournalCard,
                                enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { -it / 8 },
                                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 8 }
                            ) {
                                val isJournalActive = journalCreating || journalEditing
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(if (designStyle == DesignStyle.LiquidGlass) LiquidGlassTokens.contentColor else Color(0xFF141416))
                                        .then(if (designStyle == DesignStyle.LiquidGlass) Modifier.border(0.65.dp, liquidContentEdge(), RoundedCornerShape(22.dp)) else Modifier.border(0.8.dp, Color(0xFF28282C), RoundedCornerShape(22.dp)))
                                        .padding(18.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "Günlük",
                                                    color = Color(0xFFA5A5B0),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                        if (isJournalActive) {
                                            Spacer(Modifier.height(14.dp))
                                            BasicTextField(
                                                value = editedJournalText,
                                                onValueChange = { editedJournalText = it },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .semantics { contentDescription = "Günlük notu" },
                                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFF4F4F6), fontSize = 16.sp, lineHeight = 22.sp),
                                                cursorBrush = SolidColor(Color(0xFF82B3FF)),
                                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                                decorationBox = { innerTextField ->
                                                    if (editedJournalText.isEmpty()) {
                                                        Text("Bir not bırak...", color = Color(0xFF6E6E77), fontSize = 16.sp)
                                                    }
                                                    innerTextField()
                                                }
                                            )
                                            Spacer(Modifier.height(16.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (journalEditing) {
                                                    TextButton(onClick = { onDeleteJournal(); journalEditing = false }) {
                                                        Text("Sil", color = Color(0xFFF87171), fontSize = 14.sp)
                                                    }
                                                    Spacer(Modifier.weight(1f))
                                                } else {
                                                    TextButton(onClick = { finishEditing() }) {
                                                        Text("İptal", color = Color(0xFFA5A5B0), fontSize = 14.sp)
                                                    }
                                                    TextButton(
                                                        onClick = {
                                                            val validText = editedJournalText.trim()
                                                            onSaveJournal(reminder.journal?.mood, validText)
                                                            journalCreating = false
                                                            journalEditing = false
                                                        },
                                                        enabled = editedJournalText.trim().isNotBlank()
                                                    ) {
                                                        Text("Kaydet", color = Color(0xFF91B9FF), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                    }
                                                }
                                            }
                                        } else {
                                            if (reminder.journal?.text?.isNotBlank() == true) {
                                                Spacer(Modifier.height(12.dp))
                                                Text(
                                                    text = reminder.journal.text,
                                                    color = Color(0xFFF4F4F6),
                                                    fontSize = 16.sp,
                                                    lineHeight = 22.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }

                if (musicSearchOpen && editing) {
                    MusicSearchSheet(designStyle = designStyle,
                        onDismiss = { musicSearchOpen = false },
                        onSelect = { track ->
                            editedMusicBlocks = editedMusicBlocks + track
                            musicSearchOpen = false
                        })
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = chromeAlpha }
                        .padding(top = 15.dp, start = 10.dp, end = 10.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Oluşturuldu · ${detailReminderDate(reminder.createdAt)}",
                        color = Color(0xFF8E8E98),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.End
                    )
                    if (reminder.completedAt != null) {
                        Text(
                            text = "Tamamlandı · ${detailReminderDate(reminder.completedAt)}",
                            color = Color(0xFF8E8E98),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.End
                        )
                    }
                    if (reminder.journal != null) {
                        Text(
                            text = "Günlüğe eklendi · ${detailReminderDate(reminder.journal.createdAt)}",
                            color = Color(0xFF8E8E98),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
        TopGlassOverlay(scene, { scenePosition }, Modifier.align(Alignment.TopCenter), reduceDynamicBlur)
        if (designStyle == DesignStyle.LiquidGlass) {
            Box(Modifier.align(Alignment.TopCenter).then(if (deleting) Modifier.clearAndSetSemantics { } else Modifier).padding(start = 16.dp, end = 16.dp, top = statusHeight + 10.dp)) {
                detailHeader()
            }
        }
        BottomFade(Modifier.align(Alignment.BottomCenter).height(130.dp))
        if (deleting) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.16f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { deleting = false }
                .semantics { contentDescription = "Silme sorusunu kapat" })
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().graphicsLayer { alpha = chromeAlpha }.padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            AnimatedVisibility(
                visible = editing && secondaryPanelsExpanded,
                enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(180)) + slideInVertically(tween(240)) { it / 8 } + scaleIn(tween(240), initialScale = 0.97f),
                exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 8 } + scaleOut(tween(180), targetScale = 0.97f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .isolateFloatingPanelGestures(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReminderColorPicker(
                        startIndex = accentStart,
                        endIndex = accentEnd,
                        onStartChange = {
                            accentStart = it
                            customSolidColorArgb = null
                        },
                        onEndChange = {
                            accentEnd = it
                            customSolidColorArgb = null
                        },
                        gradientBlendFraction = gradientBlendFraction,
                        onGradientBlendFractionChange = { gradientBlendFraction = it },
                        customSolidColorArgb = customSolidColorArgb,
                        onUseTransitionColor = {
                            val startColor = ReminderColorChoices.getOrNull(accentStart ?: -1)?.color
                            val endColor = ReminderColorChoices.getOrNull(accentEnd ?: -1)?.color
                            if (startColor != null && endColor != null) {
                                val frac = resolveGradientBlendFraction(gradientBlendFraction)
                                val bridge = lerp(startColor, endColor, frac)
                                customSolidColorArgb = bridge.toArgb()
                                accentEnd = null
                                gradientBlendFraction = DEFAULT_GRADIENT_BLEND_FRACTION
                            }
                        },
                        isStarred = reminder.isImportant,
                        starBorderIndex = starBorderColorIndex,
                        onStarBorderChange = { if (it != null) starBorderColorIndex = it },
                        backdrop = scene,
                        backdropPosition = { scenePosition },
                        designStyle = designStyle,
                        reduceDynamicBlur = reduceDynamicBlur,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DesignPanel(
                        backdrop = scene,
                        backdropPosition = { scenePosition },
                        modifier = Modifier.fillMaxWidth(),
                        corners = 22.dp,
                        designStyle = designStyle,
                        reduceDynamicBlur = reduceDynamicBlur
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .clickable {
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                    showCategoryPicker = true
                                }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Kategori", color = Color(0xFFB7B7C0), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                val currentCat = customCategories.firstOrNull { it.id == selectedCategoryId }
                                if (currentCat != null) {
                                    val startColor = ReminderColorChoices.getOrNull(currentCat.accentStart)?.color ?: Color(0xFF233F73)
                                    val endColor = currentCat.accentEnd?.let { ReminderColorChoices.getOrNull(it)?.color } ?: startColor
                                    Box(
                                        Modifier.size(18.dp).clip(CircleShape)
                                            .background(Brush.linearGradient(listOf(startColor, endColor))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CategoryIconGlyph(CategoryIcon.fromKey(currentCat.iconKey), Modifier.size(11.dp), Color.White)
                                    }
                                    Text(currentCat.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                } else {
                                    Text("Kategori yok", color = Color(0xFF8E8E98), fontSize = 13.sp)
                                }
                                ActionIcon(ActionGlyph.ChevronRight, Modifier.size(14.dp), Color(0xFF8E8E98))
                            }
                        }
                    }
                }
            }
            if (editing) {
                DesignPanel(backdrop = scene, backdropPosition = { scenePosition },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .isolateFloatingPanelGestures(),
                    corners = 26.dp,
                    designStyle = designStyle, reduceDynamicBlur = reduceDynamicBlur,
                    liquidDynamic = true) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .realPetPerch(realPetDetailBlockPerchId(reminder.id, "color_category"))
                                .padding(horizontal = 2.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        focusManager.clearFocus()
                                        keyboard?.hide()
                                        secondaryPanelsExpanded = !secondaryPanelsExpanded
                                    }
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Renk ve kategori", color = Color(0xFFB7B7C0), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                ActionIcon(
                                    ActionGlyph.ChevronRight,
                                    Modifier.size(14.dp).graphicsLayer {
                                        rotationZ = if (secondaryPanelsExpanded) 90f else 0f
                                    },
                                    Color(0xFF8E8E98)
                                )
                            }

                            val sortInteractionSource = remember { MutableInteractionSource() }
                            val sortIconColor = if (isReorderingBlocks) Color(0xFF91B9FF) else Color(0xFFD2D2DA)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .pressScale(
                                        sortInteractionSource,
                                        enabled = true,
                                        reduceAnimations = reduceAnimations,
                                        targetScale = 0.94f
                                    )
                                    .clip(CircleShape)
                                    .clickable(
                                        interactionSource = sortInteractionSource,
                                        indication = LocalIndication.current
                                    ) {
                                        focusManager.clearFocus()
                                        keyboard?.hide()
                                        isReorderingBlocks = !isReorderingBlocks
                                    }
                                    .semantics { contentDescription = "Sırala" },
                                contentAlignment = Alignment.Center
                            ) {
                                ActionIcon(
                                    ActionGlyph.Sort,
                                    Modifier.size(20.dp),
                                    sortIconColor
                                )
                            }
                        }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 2.dp, end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val photoCount = editedAttachments.count { it.type == AttachmentType.IMAGE }
                        val fileCount = editedAttachments.count { it.type != AttachmentType.IMAGE }
                        val musicCount = editedMusicBlocks.size
                        val voiceCount = editedVoiceRecordings.size
                        val checklistCount = editedChecklistItems.size

                        DetailQuickActionButton(
                            designStyle = designStyle,
                            reduceAnimations = reduceAnimations,
                            glyph = ActionGlyph.Calendar,
                            description = "Tarih / Saat",
                            enabled = !savingDetail && !reminder.isCompleted,
                            active = reminder.alertAt != null,
                            onClick = requestPermissionForDatePicker
                        )
                        DetailQuickActionButton(
                            designStyle = designStyle,
                            reduceAnimations = reduceAnimations,
                            glyph = ActionGlyph.Pin,
                            description = "Konum",
                            enabled = !savingDetail,
                            active = editedLocation != null,
                            onClick = { showLocationGuide = true }
                        )
                        DetailQuickActionButton(
                            designStyle = designStyle,
                            reduceAnimations = reduceAnimations,
                            glyph = ActionGlyph.Checklist,
                            description = "Checklist",
                            enabled = !savingDetail,
                            active = checklistCount > 0,
                            count = if (checklistCount > 0) checklistCount else null,
                            onClick = {
                                checklistBlockOpen = true
                                if (editedChecklistItems.isEmpty()) {
                                    editedChecklistItems = listOf(ChecklistItem(id = System.currentTimeMillis(), text = ""))
                                }
                                shouldFocusFirstItem = true
                            }
                        )
                        DetailQuickActionButton(
                            designStyle = designStyle,
                            reduceAnimations = reduceAnimations,
                            glyph = ActionGlyph.Photo,
                            description = "Fotoğraf ekle",
                            enabled = !savingDetail && !attachmentBusy,
                            active = photoCount > 0,
                            count = if (photoCount > 0) photoCount else null,
                            isFull = photoCount >= 3,
                            onClick = {
                                if (photoCount >= 3) {
                                    Toast.makeText(context, "En fazla 3 fotoğraf eklenebilir", Toast.LENGTH_SHORT).show()
                                } else {
                                    detailPhotoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            }
                        )
                        DetailQuickActionButton(
                            designStyle = designStyle,
                            reduceAnimations = reduceAnimations,
                            glyph = ActionGlyph.File,
                            description = "Dosya ekle",
                            enabled = !savingDetail && !attachmentBusy,
                            active = fileCount > 0,
                            count = if (fileCount > 0) fileCount else null,
                            onClick = {
                                detailFilePickerLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                                })
                            }
                        )
                        DetailQuickActionButton(
                            designStyle = designStyle,
                            reduceAnimations = reduceAnimations,
                            glyph = ActionGlyph.Music,
                            description = "Müzik",
                            enabled = !savingDetail && !attachmentBusy && !voiceRecordingActive,
                            active = musicCount > 0,
                            count = if (musicCount > 0) musicCount else null,
                            onClick = { focusManager.clearFocus(); keyboard?.hide(); musicSearchOpen = true }
                        )
                        DetailQuickActionButton(
                            designStyle = designStyle,
                            reduceAnimations = reduceAnimations,
                            glyph = ActionGlyph.AudioWave,
                            description = "Ses",
                            enabled = !savingDetail && !attachmentBusy && !voiceRecordingActive,
                            active = voiceCount > 0,
                            count = if (voiceCount > 0) voiceCount else null,
                            onClick = { focusManager.clearFocus(); keyboard?.hide(); voiceRecorderOpen = true }
                        )
                        DetailQuickActionButton(
                            designStyle = designStyle,
                            reduceAnimations = reduceAnimations,
                            glyph = ActionGlyph.Note,
                            description = "Günlük",
                            enabled = !savingDetail,
                            active = reminder.journal != null || journalCreating || journalEditing,
                            onClick = {
                                focusManager.clearFocus()
                                keyboard?.hide()
                                editedJournalText = reminder.journal?.text ?: ""
                                editedJournalMood = reminder.journal?.mood
                                journalEditing = reminder.journal != null
                                journalCreating = reminder.journal == null
                                showMoodSelector = false
                            }
                        )
                        DetailQuickActionButton(
                            designStyle = designStyle,
                            reduceAnimations = reduceAnimations,
                            glyph = ActionGlyph.MoodVeryGood,
                            description = "Mood",
                            enabled = !savingDetail,
                            active = reminder.mood != null,
                            onClick = {
                                focusManager.clearFocus()
                                keyboard?.hide()
                                standaloneMoodDraft = reminder.mood ?: JournalMood.NEUTRAL
                                showMoodFullscreen = true
                            }
                        )
                    }
                if (editing && !reminder.isCompleted) {
                    if (reminder.alertAt != null) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val scheduleShape = RoundedCornerShape(12.dp)
                                Row(
                                    modifier = Modifier
                                        .clip(scheduleShape)
                                        .then(
                                            if (designStyle == DesignStyle.LiquidGlass) Modifier.liquidChrome(12.dp, selected = true)
                                            else Modifier.background(Color(0xFF263247)).border(0.7.dp, Color(0xFF41516A), scheduleShape)
                                        )
                                        .clickable(onClick = requestPermissionForDatePicker)
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ActionIcon(ActionGlyph.Calendar, Modifier.size(16.dp), Color(0xFF91B9FF))
                                    Text(detailReminderDate(reminder.alertAt), color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                                if (reminder.hasRepeat) {
                                    val repeatLabel = reminder.repeatRule.descriptionFor(reminder.anchorAlertAt ?: reminder.alertAt)
                                    Row(
                                        modifier = Modifier
                                            .clip(scheduleShape)
                                            .then(
                                                if (designStyle == DesignStyle.LiquidGlass) Modifier.liquidChrome(12.dp)
                                                else Modifier.background(Color.White.copy(alpha = 0.07f)).border(0.7.dp, Color.White.copy(alpha = 0.12f), scheduleShape)
                                            )
                                            .clickable(onClick = requestPermissionForDatePicker)
                                            .padding(horizontal = 9.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        ActionIcon(ActionGlyph.Repeat, Modifier.size(14.dp), Color(0xFF91B9FF))
                                        Text(repeatLabel, color = Color(0xFFD2D2DA), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                            IconButton(
                                onClick = { onSetAlert(null, RecurrenceRule.NONE) },
                                modifier = Modifier.semantics { contentDescription = "Tarih ve saati kaldır" }
                            ) {
                                ActionIcon(ActionGlyph.Close, Modifier.size(18.dp))
                            }
                        }
                    }
                }
                    if (attachmentBusy) Text("Ek hazırlanıyor…", color = Color(0xFFB4B4BE), fontSize = 12.sp)
                    attachmentError?.let { Text(it, color = Color(0xFFE0B5A8), fontSize = 12.sp) }
                    }
                }
            }
            AnimatedVisibility(
                visible = deleting,
                enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(180)) + slideInVertically(tween(240)) { it / 8 } + scaleIn(tween(240), initialScale = 0.97f),
                exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 8 } + scaleOut(tween(180), targetScale = 0.97f)
            ) {
                DeleteReminderPrompt(scene, { scenePosition },
                    onDismiss = { deleting = false }, onConfirm = { deleting = false; onDelete() },
                    modifier = Modifier.padding(bottom = 12.dp), designStyle = designStyle, reduceDynamicBlur = reduceDynamicBlur)
            }

            AnimatedVisibility(
                visible = reminder.isCompleted && reminder.journal == null && !editing && !journalCreating,
                enter = fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 4 },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { it / 4 }
            ) {
                val howItWentRow: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .clickable {
                                editedJournalText = reminder.journal?.text ?: ""
                                editedJournalMood = reminder.journal?.mood
                                journalCreating = true
                                journalEditing = false
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bir iz bırak", color = Color(0xFFD3D3D9), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        ActionIcon(ActionGlyph.Add, Modifier.size(16.dp), Color(0xFF91B9FF))
                    }
                }
                if (designStyle == DesignStyle.LiquidGlass) {
                    
                    
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .liquidChrome(corners = 22.dp)
                    ) {
                        howItWentRow()
                    }
                } else {
                    DesignPanel(
                        backdrop = scene,
                        backdropPosition = { scenePosition },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        corners = 22.dp,
                        designStyle = designStyle,
                        reduceDynamicBlur = reduceDynamicBlur
                    ) {
                        howItWentRow()
                    }
                }
            }

            DesignPanel(
                scene,
                { scenePosition },
                modifier = Modifier
                    .fillMaxWidth()
                    .realPetPerch(REAL_PET_PERCH_DETAIL_BOTTOM_BAR),
                corners = 36.dp,
                designStyle = designStyle,
                reduceDynamicBlur = reduceDynamicBlur,
                liquidDynamic = true
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (editing) {
                        DetailAction("Vazgeç", ActionGlyph.Close, Modifier.weight(1f), reduceAnimations = reduceAnimations, onClick = finishEditing)
                        DetailAction("Kaydet", ActionGlyph.Check, Modifier.weight(1f),
                            enabled = !attachmentBusy && !savingDetail && !voiceRecordingActive && (editedText.isNotBlank() || editedVoiceRecordings.isNotEmpty()) && (!journalEditing || editedJournalMood != null || editedJournalText.trim().isNotBlank()),
                            color = Color(0xFF91B9FF), reduceAnimations = reduceAnimations, onClick = {
                                val finalItems = if (reminder.isChecklist || checklistBlockOpen || editedChecklistItems.isNotEmpty()) {
                                    val pending = if (newChecklistItemText.trim().isNotBlank()) {
                                        listOf(ChecklistItem(id = System.currentTimeMillis(), text = newChecklistItemText.trim()))
                                    } else emptyList()
                                    (editedChecklistItems + pending).filter { it.text.isNotBlank() }
                                } else emptyList()
                                savingDetail = true
                                attachmentScope.launch {
                                    var saved = false
                                    try {
                                        saved = onSave(
                                            editedText.trim(), accentStart, accentEnd, selectedCategoryId, finalItems,
                                            if (reminder.isImportant) starBorderColorIndex else reminder.starBorderColorIndex,
                                            if (accentStart != null && accentEnd != null) gradientBlendFraction else null,
                                            customSolidColorArgb, editedAttachments, editedVoiceRecordings, editedLocation, editedReferenceUrl, editedMusicBlocks,
                                            editedContentBlockOrder
                                        )
                                    } finally {
                                        savingDetail = false
                                        if (!screenAlive) {
                                            discardDraft(newlyAddedAttachments)
                                            discardVoiceDraft(newlyAddedVoiceRecordings)
                                        }
                                    }
                                    if (saved) {
                                        
                                        discardDraft(newlyAddedAttachments.filter { added -> editedAttachments.none { it.id == added.id } })
                                        newlyAddedAttachments = emptyList()
                                        discardVoiceDraft(newlyAddedVoiceRecordings.filter { added -> editedVoiceRecordings.none { it.id == added.id } })
                                        newlyAddedVoiceRecordings = emptyList()
                                        if (journalEditing) {
                                            onSaveJournal(reminder.journal?.mood, editedJournalText.trim())
                                        }
                                        finishEditing()
                                    } else attachmentError = "Değişiklikler kaydedilemedi. Yeniden dene."
                                }
                            })
                    } else {
                        DetailAction(if (reminder.isCompleted) "Geri al" else "Tamamla",
                            if (reminder.isCompleted) ActionGlyph.Undo else ActionGlyph.Check,
                            Modifier.weight(1f), enabled = !deleting, reduceAnimations = reduceAnimations, onClick = {
                                if (!reminder.isCompleted) {
                                    RealHaptics.success(view)
                                }
                                onToggleCompleted()
                            })
                        DetailAction("Düzenle", ActionGlyph.Edit, Modifier.weight(1f), enabled = !deleting, reduceAnimations = reduceAnimations, onClick = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                            editedText = reminder.text
                            accentStart = reminder.accentStart
                            accentEnd = reminder.accentEnd
                            customSolidColorArgb = reminder.customSolidColorArgb
                            selectedCategoryId = reminder.customCategoryId
                            editedChecklistItems = reminder.checklistItems
                            editedContentBlockOrder = reminder.contentBlockOrder
                            isReorderingBlocks = false
                            starBorderColorIndex = reminder.starBorderColorIndex ?: Math.floorMod(reminder.id, 10)
                            gradientBlendFraction = reminder.gradientBlendFraction ?: DEFAULT_GRADIENT_BLEND_FRACTION
                            newChecklistItemText = ""
                            checklistBlockOpen = reminder.checklistItems.isNotEmpty()
                            editedAttachments = reminder.attachments
                            editedVoiceRecordings = reminder.voiceRecordings
                            editedMusicBlocks = reminder.musicBlocks
                            musicSearchOpen = false
                            voiceRecorderOpen = false
                            voiceRecordingActive = false
                            editedReferenceUrl = reminder.referenceUrl
                            editedLocation = reminder.location
                            newlyAddedAttachments = emptyList()
                            newlyAddedVoiceRecordings = emptyList()
                            secondaryPanelsExpanded = false
                            journalEditing = false
                            journalCreating = false
                            editing = true
                        })
                        DetailAction("Paylaş", ActionGlyph.Share, Modifier.weight(1f), enabled = !deleting, reduceAnimations = reduceAnimations, onClick = {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, buildReminderShareText(reminder))
                            }, "Hatırlatıcıyı paylaş"))
                        })
                        DetailAction("Sil", ActionGlyph.Delete, Modifier.weight(1f), reduceAnimations = reduceAnimations, onClick = { deleting = !deleting })
                    }
                }
            }
        }
        AnimatedDateTimePickerOverlay(visible = showDatePicker, initial = reminder.alertAt,
            initialRepeatRule = reminder.repeatRule,
            onDismiss = { showDatePicker = false },
            backdrop = scene, backdropPosition = { scenePosition },
            designStyle = designStyle, reduceDynamicBlur = reduceDynamicBlur,
            reduceAnimations = reduceAnimations,
            onConfirm = { at, rule -> onSetAlert(at, rule); showDatePicker = false },
            exactAlarmsAllowed = exactAlarmsAllowed,
            onRequestExactAlarms = onRequestExactAlarms)
        LocationGuideSheet(
            visible = showLocationGuide,
            onOpenMaps = { showLocationGuide = false; onRequestLocationShare() },
            onCancel = { showLocationGuide = false },
            reduceAnimations = reduceAnimations
        )
        NotificationPermissionSheet(
            visible = showNotificationPermissionSheet,
            onAllow = {
                showNotificationPermissionSheet = false
                onRequestNotificationPermission()
            },
            onLater = {
                showNotificationPermissionSheet = false
                pendingDetailDatePicker = false
            },
            reduceAnimations = reduceAnimations
        )
        CategorySelectionOverlay(
            visible = showCategoryPicker,
            customCategories = customCategories,
            selectedCategoryId = selectedCategoryId,
            onSelectCategory = { selectedCategoryId = it },
            onDismiss = { showCategoryPicker = false },
            backdrop = scene,
            backdropPosition = { scenePosition },
            designStyle = designStyle,
            reduceDynamicBlur = reduceDynamicBlur,
            reduceAnimations = reduceAnimations
        )
        }
        if (showMoodFullscreen) {
            MoodV2FullScreen(
                selectedMood = standaloneMoodDraft,
                onMoodSelected = { standaloneMoodDraft = it },
                onDismiss = { showMoodFullscreen = false },
                initialDetails = reminder.moodDetails,
                reminderContextLabel = reminder.text,
                onSave = { mood, details ->
                    standaloneMoodDraft = mood
                    onAddMoodCard(mood, details)
                    RealHaptics.success(view)
                    showMoodFullscreen = false
                },
                reduceAnimations = reduceAnimations
            )
        }

        if (previewAttachmentPath != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        previewAttachmentPath = null
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = File(previewAttachmentPath!!),
                    contentDescription = "Fotoğraf tam ekran önizleme",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { previewAttachmentPath = null },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = statusHeight + 10.dp, start = 16.dp)
                        .background(Color(0x80161619), CircleShape)
                        .semantics { contentDescription = "Önizlemeyi kapat" }
                ) {
                    ActionIcon(ActionGlyph.Close, Modifier.size(22.dp), Color.White)
                }
            }
        }
    }
}


private val DEFAULT_BLOCK_ORDER = listOf("text", "mood", "checklist", "photos", "files", "voice", "music", "location", "journal")

private fun resolveBlockOrder(savedOrder: List<String>?, activeKeys: Set<String>): List<String> {
    val base = if (!savedOrder.isNullOrEmpty()) savedOrder else DEFAULT_BLOCK_ORDER
    val orderedActive = base.filter { it in activeKeys }.toMutableList()
    DEFAULT_BLOCK_ORDER.forEach { key ->
        if (key in activeKeys && key !in orderedActive) {
            orderedActive.add(key)
        }
    }
    return orderedActive
}

private data class ContentBlockDescriptor(
    val key: String,
    val title: String,
    val subtitle: String,
    val glyph: ActionGlyph
)

@Composable
private fun TimelineConnector(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(28.dp)
            .width(1.5.dp)
            .background(Color(0xFF38383F))
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(Color(0xFF38383F))
                .align(Alignment.Center)
        )
    }
}

@Composable
private fun DetailChecklistBox(
    designStyle: DesignStyle,
    editing: Boolean,
    items: List<ChecklistItem>,
    newItemText: String,
    onNewItemTextChange: (String) -> Unit,
    onAddItem: () -> Unit,
    onItemTextChange: (Long, String) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onToggleItem: (Long) -> Unit,
    onRemoveChecklist: () -> Unit,
    onFocusField: () -> Unit,
    firstItemFocusRequester: FocusRequester,
    reduceAnimations: Boolean,
    savingDetail: Boolean
) {
    val cardShape = RoundedCornerShape(22.dp)
    val total = items.size
    val completedCount = items.count { it.isCompleted }
    val summaryText = if (total > 0) {
        if (completedCount > 0) "$total madde • $completedCount tamamlandı" else "$total madde"
    } else ""

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .then(
                if (designStyle == DesignStyle.LiquidGlass) Modifier.liquidChrome(corners = 22.dp)
                else Modifier
                    .background(Color(0xFF141416))
                    .border(0.7.dp, Color.White.copy(alpha = 0.10f), cardShape)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Checklist",
                    color = Color(0xFFB7B7C0),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (summaryText.isNotBlank()) {
                    Text(
                        text = "  •  $summaryText",
                        color = Color(0xFFA5A5B5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (editing) {
                    IconButton(
                        onClick = onRemoveChecklist,
                        enabled = !savingDetail,
                        modifier = Modifier
                            .size(28.dp)
                            .semantics { contentDescription = "Checklist kutusunu kaldır" }
                    ) {
                        ActionIcon(ActionGlyph.Close, Modifier.size(15.dp), Color(0xFF8E8E98))
                    }
                }
            }

            if (editing) {
                items.forEachIndexed { index, item ->
                    key(item.id) {
                        EditableChecklistItemRow(
                            item = item,
                            onTextChange = { newText -> onItemTextChange(item.id, newText) },
                            onDelete = { onDeleteItem(item.id) },
                            onToggle = { onToggleItem(item.id) },
                            onFocus = onFocusField,
                            reduceAnimations = reduceAnimations,
                            customFocusRequester = if (index == 0) firstItemFocusRequester else null
                        )
                    }
                }
                AddChecklistItemRow(
                    text = newItemText,
                    onTextChange = onNewItemTextChange,
                    onFocus = onFocusField,
                    onAdd = onAddItem
                )
            } else {
                items.forEach { item ->
                    key(item.id) {
                        ChecklistItemRow(
                            item = item,
                            reduceAnimations = reduceAnimations,
                            onToggle = { onToggleItem(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistItemRow(
    item: ChecklistItem,
    reduceAnimations: Boolean,
    onToggle: () -> Unit
) {
    val view = LocalView.current
    val itemTextColor by animateColorAsState(
        if (item.isCompleted) Color(0xFF6E6E77) else Color(0xFFF1F1F5),
        animationSpec = if (reduceAnimations) snap() else tween(180),
        label = "checkItemColor_${item.id}"
    )
    val itemAlpha by animateFloatAsState(
        if (item.isCompleted) 0.65f else 1.0f,
        animationSpec = if (reduceAnimations) snap() else tween(180),
        label = "checkItemAlpha_${item.id}"
    )
    val circleBorderColor by animateColorAsState(
        if (item.isCompleted) Color.Transparent else Color(0xFF5E5E68),
        animationSpec = if (reduceAnimations) snap() else tween(120),
        label = "checkCircleBorder_${item.id}"
    )
    val circleBgColor by animateColorAsState(
        if (item.isCompleted) Color(0xFF686873) else Color.Transparent,
        animationSpec = if (reduceAnimations) snap() else tween(120),
        label = "checkCircleBg_${item.id}"
    )
    val checkItemInteractionSource = remember(item.id) { MutableInteractionSource() }
    val checkSettle = remember(item.id) { Animatable(1f) }
    var hasRenderedInitialCheck by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.isCompleted) {
        if (!hasRenderedInitialCheck) {
            hasRenderedInitialCheck = true
            return@LaunchedEffect
        }
        if (item.isCompleted && !reduceAnimations) {
            checkSettle.snapTo(1.05f)
            checkSettle.animateTo(1.0f, tween(140, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)))
        } else {
            checkSettle.snapTo(1.0f)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pressScale(checkItemInteractionSource, reduceAnimations = reduceAnimations, targetScale = 0.98f)
            .clickable(
                interactionSource = checkItemInteractionSource,
                indication = LocalIndication.current,
                onClick = {
                    if (!item.isCompleted) {
                        RealHaptics.toggle(view, on = true)
                    }
                    onToggle()
                }
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    scaleX = checkSettle.value
                    scaleY = checkSettle.value
                }
                .background(circleBgColor, CircleShape)
                .border(1.5.dp, circleBorderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (item.isCompleted) {
                ActionIcon(ActionGlyph.Check, Modifier.size(12.dp), Color(0xFF1B1B1F))
            }
        }
        Text(
            text = item.text,
            color = itemTextColor,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.weight(1f).graphicsLayer { alpha = itemAlpha }
        )
    }
}

@Composable
private fun EditableChecklistItemRow(
    item: ChecklistItem,
    onTextChange: (String) -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit,
    onFocus: () -> Unit = {},
    reduceAnimations: Boolean,
    customFocusRequester: FocusRequester? = null
) {
    val view = LocalView.current
    val circleBorderColor = if (item.isCompleted) Color.Transparent else Color(0xFF5E5E68)
    val circleBgColor = if (item.isCompleted) Color(0xFF686873) else Color.Transparent
    val defaultFocusRequester = remember { FocusRequester() }
    val focusRequester = customFocusRequester ?: defaultFocusRequester
    val keyboard = LocalSoftwareKeyboardController.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onFocus()
                focusRequester.requestFocus()
                keyboard?.show()
            }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(circleBgColor, CircleShape)
                .border(1.5.dp, circleBorderColor, CircleShape)
                .clickable {
                    if (!item.isCompleted) {
                        RealHaptics.toggle(view, on = true)
                    }
                    onToggle()
                },
            contentAlignment = Alignment.Center
        ) {
            if (item.isCompleted) {
                ActionIcon(ActionGlyph.Check, Modifier.size(12.dp), Color(0xFF1B1B1F))
            }
        }

        BasicTextField(
            value = item.text,
            onValueChange = {
                onFocus()
                onTextChange(it)
            },
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onFocus() }
                .semantics { contentDescription = "Madde metni" },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (item.isCompleted) Color(0xFF8E8E98) else Color(0xFFF1F1F5),
                fontSize = 16.sp,
                lineHeight = 22.sp,
                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
            ),
            cursorBrush = SolidColor(Color(0xFF82B3FF)),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done)
        )

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(28.dp)
                .semantics { contentDescription = "Maddeyi sil" }
        ) {
            ActionIcon(ActionGlyph.Close, Modifier.size(16.dp), Color(0xFF8E8E98))
        }
    }
}

@Composable
private fun AddChecklistItemRow(
    text: String,
    onTextChange: (String) -> Unit,
    onFocus: () -> Unit = {},
    onAdd: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onFocus()
                focusRequester.requestFocus()
                keyboard?.show()
            }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(1.5.dp, Color(0xFF5E5E68), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onFocus()
                    focusRequester.requestFocus()
                    keyboard?.show()
                },
            contentAlignment = Alignment.Center
        ) {
            ActionIcon(ActionGlyph.Add, Modifier.size(12.dp), Color(0xFF8E8E98))
        }

        BasicTextField(
            value = text,
            onValueChange = {
                onFocus()
                onTextChange(it)
            },
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onFocus() }
                .semantics { contentDescription = "Yeni madde ekle" },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 22.sp
            ),
            cursorBrush = SolidColor(Color(0xFF82B3FF)),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (text.isNotBlank()) onAdd()
                }
            ),
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text(
                        "Yeni madde ekle...",
                        color = Color(0xFF6E6E77),
                        fontSize = 16.sp
                    )
                }
                innerTextField()
            }
        )

        if (text.isNotBlank()) {
            IconButton(
                onClick = onAdd,
                modifier = Modifier
                    .size(28.dp)
                    .semantics { contentDescription = "Maddeyi kaydet" }
            ) {
                ActionIcon(ActionGlyph.Check, Modifier.size(16.dp), Color(0xFF91B9FF))
            }
        }
    }
}

private fun attachmentAspectRatio(path: String): Float {
    return runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth.toFloat() / options.outHeight.toFloat()
        } else {
            1f
        }
    }.getOrDefault(1f)
}

@Composable
private fun AttachmentMediaSection(
    attachments: List<ReminderAttachment>,
    designStyle: DesignStyle,
    editing: Boolean,
    onOpen: (ReminderAttachment) -> Unit,
    onRemove: (ReminderAttachment) -> Unit
) {
    if (attachments.isEmpty()) return

    val sectionShape = RoundedCornerShape(22.dp)
    val sectionFill = if (designStyle == DesignStyle.LiquidGlass) {
        LiquidGlassTokens.contentColor
    } else {
        Color(0xFF141416)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (attachments.size) {
            1 -> {
                val attachment = attachments.first()
                val ratio = remember(attachment.localPath) {
                    attachmentAspectRatio(attachment.localPath).coerceIn(0.62f, 1.95f)
                }
                val sizeModifier = when {
                    ratio < 0.9f -> Modifier.fillMaxWidth(0.72f).aspectRatio(ratio)
                    ratio > 1.15f -> Modifier.fillMaxWidth().aspectRatio(ratio)
                    else -> Modifier.fillMaxWidth(0.82f).aspectRatio(1f)
                }
                Box(
                    modifier = sizeModifier
                        .clip(sectionShape)
                        .background(sectionFill)
                        .then(
                            if (designStyle == DesignStyle.LiquidGlass) {
                                Modifier.border(0.75.dp, liquidContentEdge(), sectionShape)
                            } else {
                                Modifier.border(0.75.dp, Color(0xFF2D2D33), sectionShape)
                            }
                        )
                        .padding(3.dp)
                ) {
                    AttachmentPhotoTile(
                        attachment = attachment,
                        editing = editing,
                        onOpen = onOpen,
                        onRemove = onRemove,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(19.dp)
                    )
                }
            }

            2 -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(sectionShape)
                        .background(sectionFill)
                        .then(
                            if (designStyle == DesignStyle.LiquidGlass) {
                                Modifier.border(0.75.dp, liquidContentEdge(), sectionShape)
                            } else {
                                Modifier.border(0.75.dp, Color(0xFF2D2D33), sectionShape)
                            }
                        )
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    attachments.forEach { attachment ->
                        AttachmentPhotoTile(
                            attachment = attachment,
                            editing = editing,
                            onOpen = onOpen,
                            onRemove = onRemove,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(18.dp)
                        )
                    }
                }
            }

            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(218.dp)
                        .clip(sectionShape)
                        .background(sectionFill)
                        .then(
                            if (designStyle == DesignStyle.LiquidGlass) {
                                Modifier.border(0.75.dp, liquidContentEdge(), sectionShape)
                            } else {
                                Modifier.border(0.75.dp, Color(0xFF2D2D33), sectionShape)
                            }
                        )
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    AttachmentPhotoTile(
                        attachment = attachments[0],
                        editing = editing,
                        onOpen = onOpen,
                        onRemove = onRemove,
                        modifier = Modifier.weight(1.18f).fillMaxHeight(),
                        shape = RoundedCornerShape(18.dp)
                    )
                    Column(
                        modifier = Modifier.weight(0.82f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        attachments.drop(1).take(2).forEach { attachment ->
                            AttachmentPhotoTile(
                                attachment = attachment,
                                editing = editing,
                                onOpen = onOpen,
                                onRemove = onRemove,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPhotoTile(
    attachment: ReminderAttachment,
    editing: Boolean,
    onOpen: (ReminderAttachment) -> Unit,
    onRemove: (ReminderAttachment) -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF101012))
            .clickable { onOpen(attachment) }
            .semantics { contentDescription = "Fotoğrafı büyüt" }
    ) {
        AsyncImage(
            model = File(attachment.localPath),
            contentDescription = "Fotoğraf eki",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (editing) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Color(0xD918181B))
                    .border(0.5.dp, Color(0x4DFFFFFF), CircleShape)
                    .clickable { onRemove(attachment) }
                    .semantics { contentDescription = "Fotoğrafı kaldır" },
                contentAlignment = Alignment.Center
            ) {
                ActionIcon(ActionGlyph.Close, Modifier.size(11.dp), Color(0xFFF1F1F4))
            }
        }
    }
}

private fun Modifier.isolateFloatingPanelGestures(): Modifier = this.pointerInput(Unit) {
    // Make the whole floating panel participate in hit-testing so touches do not
    // fall through to the detail content behind it. Do not consume movement here:
    // child clickables/horizontalScroll/color controls must keep ownership of their
    // own gestures. Consuming every tiny position change made normal taps flaky.
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent()
        }
    }
}

@Composable
private fun DetailQuickActionButton(
    designStyle: DesignStyle,
    reduceAnimations: Boolean,
    glyph: ActionGlyph,
    description: String,
    enabled: Boolean = true,
    active: Boolean = false,
    count: Int? = null,
    isFull: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val baseTint = when {
        !enabled -> Color(0xFF6B6B75)
        active -> Color(0xFF91B9FF)
        else -> Color(0xFFBABAC3)
    }
    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .pressScale(
                    interactionSource,
                    enabled = enabled,
                    reduceAnimations = reduceAnimations,
                    targetScale = 0.96f
                )
                .clip(CircleShape)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick
                )
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            ActionIcon(
                glyph,
                Modifier.size(20.dp),
                baseTint.copy(alpha = if (enabled) 1f else 0.35f)
            )
        }

        if (count != null && count > 0) {
            val countColor = if (isFull) Color(0xFF8A9FB8) else Color(0xFF91B9FF)
            Text(
                text = count.toString(),
                color = countColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-3).dp, y = 3.dp)
            )
        }
    }
}

@Composable
private fun DetailAction(
    label: String, glyph: ActionGlyph, modifier: Modifier,
    enabled: Boolean = true, color: Color = Color(0xFFF0F0F4),
    reduceAnimations: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (enabled) color else color.copy(alpha = 0.35f)
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(interactionSource, enabled = enabled, targetScale = 0.96f, reduceAnimations = reduceAnimations)
            .clip(RoundedCornerShape(28.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .heightIn(min = 48.dp).padding(vertical = 3.5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically)
    ) {
        ActionIcon(glyph, Modifier.size(22.dp), tint)
        Text(label, fontSize = 11.sp, color = tint, maxLines = 1)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(name = "Cam detay", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun DetailPreview() {
    BigBosTheme {
        ReminderDetailScreen(ReminderItem(1, "Akşam için birkaç küçük not", 1789610400000), {}, {}, {}, { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> true }, {})
    }
}

fun shouldShowNoAlertInDetail(reminder: ReminderItem): Boolean =
    !reminder.isCompleted && reminder.alertAt == null && reminder.customCategoryId == null

fun isStandaloneCategoryInDetail(reminder: ReminderItem): Boolean =
    !reminder.isCompleted && reminder.alertAt == null && reminder.customCategoryId != null

fun reminderDetailStatusText(
    reminder: ReminderItem,
    hasCategory: Boolean = (reminder.customCategoryId != null)
): String? = when {
    reminder.isCompleted -> "Tamamlandı"
    reminder.alertAt == null -> if (!hasCategory) "Uyarı yok" else null
    else -> "Planlı"
}

fun formatDisplayUrl(url: String): String {
    return try {
        val uri = android.net.Uri.parse(url)
        uri.host?.removePrefix("www.") ?: url
    } catch (_: Exception) {
        url
    }
}
