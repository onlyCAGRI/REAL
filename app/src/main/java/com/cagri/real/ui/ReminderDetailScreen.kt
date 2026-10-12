package com.cagri.real.ui

import androidx.compose.ui.layout.onSizeChanged

import com.cagri.real.ui.components.liquidDockChip

import android.os.Build
import android.app.Activity
import com.cagri.real.data.AttachmentType
import com.cagri.real.data.ReminderRepository
import java.time.ZoneId
import com.cagri.real.ui.components.ReminderContentSections
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import android.graphics.BitmapFactory
import com.cagri.real.ui.components.LiquidGlassTokens
import com.cagri.real.ui.components.liquidContentEdge
import com.cagri.real.ui.components.liquidChrome
import com.cagri.real.ui.components.StarCardBorderColor
import com.cagri.real.ui.components.DesignPanel
import com.cagri.real.ui.components.CardStackDefaultColor
import com.cagri.real.ui.components.AnimatedDateTimePickerOverlay
import com.cagri.real.ui.components.NotificationPermissionSheet
import com.cagri.real.ui.components.LocationGuideSheet
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
import com.cagri.real.data.ReminderAttachment
import com.cagri.real.data.ReminderLocation
import com.cagri.real.data.MusicBlock
import com.cagri.real.data.MediaBlock
import com.cagri.real.data.ReminderVoiceRecording
import com.cagri.real.data.VoiceRecordingStorage
import com.cagri.real.ui.components.ReminderLocationMap
import com.cagri.real.ui.components.MusicSearchSheet
import com.cagri.real.ui.components.MusicContentSection
import com.cagri.real.ui.components.MediaContentSection
import com.cagri.real.ui.components.MediaSearchSheet
import com.cagri.real.ui.components.VoiceRecorderInline
import com.cagri.real.ui.components.openReminderLocationInMaps
import com.cagri.real.ui.components.RealMotion
import com.cagri.real.data.AttachmentStorage
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
import com.cagri.real.ui.components.RealHaptics
import com.cagri.real.ui.components.pressScale
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.runtime.Composable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.key
import com.cagri.real.data.ChecklistItem
import com.cagri.real.data.JournalMood
import com.cagri.real.data.MoodDetails
import com.cagri.real.data.RecurrenceRule
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.isQuick24HourActive
import com.cagri.real.data.buildReminderShareText
import com.cagri.real.data.repeatSummary
import com.cagri.real.data.descriptionFor
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import com.cagri.real.ui.components.MoodV2FullScreen
import com.cagri.real.ui.components.MoodV2Face
import com.cagri.real.ui.components.moodV2Tint
import com.cagri.real.ui.components.moodV2FamilyTitle
import com.cagri.real.ui.components.moodDescriptorLabelV2
import com.cagri.real.ui.components.moodFactorLabelV2
import com.cagri.real.ui.components.DeleteReminderPrompt
import com.cagri.real.data.CustomCategory
import com.cagri.real.ui.components.CategoryIcon
import com.cagri.real.ui.components.CategoryIconGlyph
import com.cagri.real.ui.components.CategorySelectionOverlay
import com.cagri.real.data.DEFAULT_GRADIENT_BLEND_FRACTION
import com.cagri.real.ui.components.ReminderColorChoices
import com.cagri.real.ui.components.ReminderColorPicker
import com.cagri.real.ui.components.reminderAccentBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.lerp
import com.cagri.real.data.resolveGradientBlendFraction
import com.cagri.real.notifications.NowBarReminderController
import com.cagri.real.ui.components.TopGlassOverlay
import com.cagri.real.ui.components.REAL_PET_PERCH_DETAIL_BOTTOM_BAR
import com.cagri.real.ui.components.RealPetRuntime
import com.cagri.real.ui.theme.RealType
import com.cagri.real.ui.components.realPetDetailBlockPerchId
import com.cagri.real.ui.components.realPetPerch
import com.cagri.real.data.CardStackBlock
import com.cagri.real.data.CardStackCard
import com.cagri.real.ui.components.CardStackContentSection
import com.cagri.real.ui.components.CardStackShareDialog
import com.cagri.real.ui.theme.RealTheme
import com.cagri.real.data.CardBlockPlacement
import com.cagri.real.data.cardContentKeys
import com.cagri.real.data.cardLayoutForKeys
import com.cagri.real.data.cardDetailReadablePlacements
import com.cagri.real.data.moveCardContent
import com.cagri.real.data.resolveCardContentOrder

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun ReminderDetailScreen(
    reminder: ReminderItem, onBack: () -> Unit, onToggleCompleted: () -> Unit,
    onToggleImportant: () -> Unit, onSave: suspend (String, Int?, Int?, Long?, List<ChecklistItem>?, Int?, Float?, Int?, List<ReminderAttachment>?, List<ReminderVoiceRecording>?, ReminderLocation?, String?, List<MusicBlock>, List<MediaBlock>, CardStackBlock?, List<String>?, Map<String, com.cagri.real.data.CardBlockPlacement>?, Boolean) -> Boolean, onDelete: () -> Unit,
    /** Planner-selected occurrence of a repeating reminder; enables the "only this day / this and future" delete prompt. */
    repeatOccurrenceMillis: Long? = null,
    onDeleteRepeatOccurrence: (com.cagri.real.data.RepeatDeleteScope) -> Unit = {},
    /** True when [repeatOccurrenceMillis] is the resolved next repeat (Home / Category) rather than a Planner-selected day. */
    repeatOccurrenceIsNext: Boolean = false,
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
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    calendarSyncPublished: Boolean? = null,
    onCalendarSyncClick: () -> Unit = {},
    pullDismiss: DetailPullDismiss? = null
) {
    var editing by rememberSaveable(reminder.id) { mutableStateOf(false) }
    var editedText by rememberSaveable(reminder.id) { mutableStateOf(reminder.text) }
    var accentStart by rememberSaveable(reminder.id) { mutableStateOf<Int?>(reminder.accentStart) }
    var accentEnd by rememberSaveable(reminder.id) { mutableStateOf<Int?>(reminder.accentEnd) }
    var customSolidColorArgb by rememberSaveable(reminder.id) { mutableStateOf(reminder.customSolidColorArgb) }
    var useDarkText by rememberSaveable(reminder.id) { mutableStateOf(reminder.useDarkText) }
    var gradientBlendFraction by rememberSaveable(reminder.id) {
        mutableFloatStateOf(reminder.gradientBlendFraction ?: DEFAULT_GRADIENT_BLEND_FRACTION)
    }
    var starBorderColorIndex by rememberSaveable(reminder.id) {
        mutableIntStateOf(reminder.starBorderColorIndex ?: Math.floorMod(reminder.id, 10))
    }
    var selectedCategoryId by rememberSaveable(reminder.id) { mutableStateOf(reminder.customCategoryId) }
    var editedChecklistItems by remember(reminder.id, editing) { mutableStateOf(reminder.checklistItems) }
    var editedContentBlockOrder by remember(reminder.id, editing) { mutableStateOf(reminder.contentBlockOrder) }
    val detailWidthDp = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 32).coerceAtLeast(1).toFloat()
    var editedContentBlockLayout by remember(reminder.id, editing) { mutableStateOf(reminder.contentBlockLayout) }
    var showTitleTools by remember(reminder.id, editing) { mutableStateOf(false) }
    var textToolsKey by remember(reminder.id, editing) { mutableStateOf("text") }
    var selectedLayoutBlock by remember(reminder.id) { mutableStateOf<String?>(null) }
    var configuringLayoutBlock by remember(reminder.id) { mutableStateOf<String?>(null) }
    var isReorderingBlocks by rememberSaveable(reminder.id) { mutableStateOf(false) }
    var newChecklistItemText by rememberSaveable(reminder.id) { mutableStateOf("") }
    var secondaryPanelsExpanded by rememberSaveable(reminder.id) { mutableStateOf(false) }
    var editDockCollapsed by rememberSaveable(reminder.id, editing) { mutableStateOf(false) }
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val detailContentScroll = rememberScrollState()
    val cardBlockDrag = remember(reminder.id) { CardBlockDragState() }
    var checklistBlockOpen by remember(reminder.id, editing) { mutableStateOf(reminder.checklistItems.isNotEmpty()) }
    val firstItemFocusRequester = remember { FocusRequester() }
    var shouldFocusFirstItem by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    var rootBoxCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var bottomPanelCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var bottomPanelHeightPx by remember { mutableIntStateOf(0) }
    var footerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val contentBlockCoordinates = remember(reminder.id) { mutableMapOf<String, LayoutCoordinates>() }
    LaunchedEffect(editing) {
        if (!editing) contentBlockCoordinates.clear()
    }

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
    var showCamera by remember(reminder.id, editing) { mutableStateOf(false) }
    var pendingDetailDatePicker by rememberSaveable(reminder.id) { mutableStateOf(false) }
    var editedAttachments by remember(reminder.id, editing) { mutableStateOf(reminder.attachments) }
    var editedVoiceRecordings by remember(reminder.id, editing) { mutableStateOf(reminder.voiceRecordings) }
    var editedMusicBlocks by remember(reminder.id, editing) { mutableStateOf(reminder.musicBlocks) }
    var editedMediaBlocks by remember(reminder.id, editing) { mutableStateOf(reminder.mediaBlocks) }
    var editedCardStack by remember(reminder.id, editing) { mutableStateOf(reminder.cardStack) }
    var showCardStackShareDialog by rememberSaveable(reminder.id) { mutableStateOf(false) }
    var sharedStackIds by rememberSaveable(reminder.id) { mutableStateOf<ArrayList<String>?>(null) }
    var showRealShareDialog by rememberSaveable(reminder.id) { mutableStateOf(false) }
    var inlineCardStackEditing by rememberSaveable(reminder.id) { mutableStateOf(false) }
    var inlineCardStackStartIndex by rememberSaveable(reminder.id) { mutableIntStateOf(0) }
    var inlineCardStackKey by rememberSaveable(reminder.id) { mutableStateOf("card_stack") }
    val cardStackBringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    var musicSearchOpen by remember(reminder.id) { mutableStateOf(false) }
    var mediaSearchOpen by remember(reminder.id) { mutableStateOf(false) }
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
    val plannerRepo = remember(context) {
        com.cagri.real.ui.components.planner.PlannerRepository(
            com.cagri.real.platform.android.sync.SyncLocal.preferences(context, "real_planner_v1", android.content.Context.MODE_PRIVATE)
        )
    }
    var plannerDetails by remember(reminder.id) {
        mutableStateOf<com.cagri.real.ui.components.planner.PlannerEventDetails?>(null)
    }
    LaunchedEffect(reminder.id) {
        plannerDetails = plannerRepo.load()[reminder.id]
    }
    LaunchedEffect(plannerRepo, reminder.id) {
        com.cagri.real.data.ReminderRepository.updates.collect {
            plannerDetails = plannerRepo.load()[reminder.id]
        }
    }
    var detailDurationMinutes by rememberSaveable(reminder.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(plannerDetails) {
        detailDurationMinutes = plannerDetails?.durationMinutes
    }
    var showDetailEndPicker by rememberSaveable(reminder.id) { mutableStateOf(false) }
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
    var newlyAddedCardStackImages by remember(reminder.id, editing) { mutableStateOf<List<String>>(emptyList()) }
    fun discardCardStackImageDraft(paths: List<String>) {
        if (paths.isEmpty()) return
        attachmentScope.launch(Dispatchers.IO + NonCancellable) {
            synchronized(ReminderRepository.mutationLock) {
                val used = ReminderRepository(context).getAllAttachmentPaths()
                AttachmentStorage.deleteAttachmentFilePaths(context, paths.filterNot { it in used })
            }
        }
    }
    val pendingDraftFiles by androidx.compose.runtime.rememberUpdatedState(newlyAddedAttachments)
    val pendingDraftVoices by androidx.compose.runtime.rememberUpdatedState(newlyAddedVoiceRecordings)
    val pendingDraftCardImages by androidx.compose.runtime.rememberUpdatedState(newlyAddedCardStackImages)
    DisposableEffect(reminder.id) {
        screenAlive = true
        onDispose {
            screenAlive = false
            if (!savingDetail) {
                discardDraft(pendingDraftFiles)
                discardVoiceDraft(pendingDraftVoices)
                discardCardStackImageDraft(pendingDraftCardImages)
            }
        }
    }
    fun importPicked(uris: List<android.net.Uri>, flags: Int, photosOnly: Boolean, cameraFile: java.io.File? = null) {
        if (attachmentBusy || savingDetail || !editing || (uris.isEmpty() && cameraFile == null)) {
            cameraFile?.delete()
            return
        }
        if (uris.size > 20) { attachmentError = "Bir seferde en fazla 20 dosya seçebilirsin"; return }
        val generation = editGeneration
        val slots = 3 - editedAttachments.count { it.type == AttachmentType.IMAGE }
        attachmentBusy = true; attachmentError = null
        attachmentScope.launch {
            val imported = mutableListOf<ReminderAttachment>()
            var failed = 0
            withContext(Dispatchers.IO + NonCancellable) {
                val sources = buildList<() -> ReminderAttachment?> {
                    uris.forEach { uri -> add {
                        if (photosOnly) AttachmentStorage.copyUriToAttachments(context, uri)
                        else AttachmentStorage.importUri(context, uri, flags)
                    } }
                    if (cameraFile != null) add {
                        try { AttachmentStorage.importCameraPhoto(context, cameraFile) }
                        finally { cameraFile.delete() }
                    }
                }
                sources.forEach { read ->
                    val item = runCatching { read() }.getOrNull()
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
    val outsideEditInteractionSource = remember(reminder.id) { MutableInteractionSource() }

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
        if (newlyAddedCardStackImages.isNotEmpty()) {
            discardCardStackImageDraft(newlyAddedCardStackImages)
            newlyAddedCardStackImages = emptyList()
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
        editedMediaBlocks = reminder.mediaBlocks
        editedCardStack = reminder.cardStack
        musicSearchOpen = false
        mediaSearchOpen = false
        voiceRecorderOpen = false
        voiceRecordingActive = false
        editedReferenceUrl = reminder.referenceUrl
        editedLocation = reminder.location
        locationJustShared = false
        editing = false
        inlineCardStackEditing = false
        inlineCardStackStartIndex = 0
        secondaryPanelsExpanded = false
        accentStart = reminder.accentStart
        accentEnd = reminder.accentEnd
        customSolidColorArgb = reminder.customSolidColorArgb
        useDarkText = reminder.useDarkText
        selectedCategoryId = reminder.customCategoryId
        editedChecklistItems = reminder.checklistItems
        editedContentBlockOrder = reminder.contentBlockOrder
        editedContentBlockLayout = cardDetailReadablePlacements(reminder.contentBlockLayout, detailWidthDp)
        selectedLayoutBlock = null
        configuringLayoutBlock = null
        isReorderingBlocks = false
        starBorderColorIndex = reminder.starBorderColorIndex ?: Math.floorMod(reminder.id, 10)
        gradientBlendFraction = reminder.gradientBlendFraction ?: DEFAULT_GRADIENT_BLEND_FRACTION
        newChecklistItemText = ""
        checklistBlockOpen = reminder.checklistItems.isNotEmpty()
        focusManager.clearFocus()
        keyboard?.hide()
    }
    val loadReminderIntoDraft: () -> Unit = {
        editedText = reminder.text
        accentStart = reminder.accentStart
        accentEnd = reminder.accentEnd
        customSolidColorArgb = reminder.customSolidColorArgb
        useDarkText = reminder.useDarkText
        selectedCategoryId = reminder.customCategoryId
        editedChecklistItems = reminder.checklistItems
        editedContentBlockOrder = reminder.contentBlockOrder
        editedContentBlockLayout = cardDetailReadablePlacements(reminder.contentBlockLayout, detailWidthDp)
        selectedLayoutBlock = null
        configuringLayoutBlock = null
        isReorderingBlocks = false
        starBorderColorIndex = reminder.starBorderColorIndex ?: Math.floorMod(reminder.id, 10)
        gradientBlendFraction = reminder.gradientBlendFraction ?: DEFAULT_GRADIENT_BLEND_FRACTION
        newChecklistItemText = ""
        checklistBlockOpen = reminder.checklistItems.isNotEmpty()
        editedAttachments = reminder.attachments
        editedVoiceRecordings = reminder.voiceRecordings
        editedMusicBlocks = reminder.musicBlocks
        editedMediaBlocks = reminder.mediaBlocks
        editedCardStack = reminder.cardStack
        editedReferenceUrl = reminder.referenceUrl
        editedLocation = reminder.location
        newlyAddedAttachments = emptyList()
        newlyAddedVoiceRecordings = emptyList()
        newlyAddedCardStackImages = emptyList()
        musicSearchOpen = false
        mediaSearchOpen = false
        voiceRecorderOpen = false
        voiceRecordingActive = false
        secondaryPanelsExpanded = false
        journalEditing = false
        journalCreating = false
    }

    val saveDraftAndClose: () -> Unit = save@ {
        if (savingDetail || attachmentBusy || voiceRecordingActive) return@save
        if (editedText.isBlank() && editedVoiceRecordings.isEmpty()) {
            attachmentError = "REAL metni boş bırakılamaz."
            return@save
        }
        val finalItems = if (reminder.isChecklist || checklistBlockOpen || editedChecklistItems.isNotEmpty()) {
            val pending = if (newChecklistItemText.trim().isNotBlank()) {
                listOf(ChecklistItem(id = System.currentTimeMillis(), text = newChecklistItemText.trim()))
            } else emptyList()
            (editedChecklistItems + pending).filter { it.text.isNotBlank() }
        } else emptyList()

        focusManager.clearFocus()
        keyboard?.hide()
        savingDetail = true
        attachmentScope.launch {
            var saved = false
            try {
                saved = onSave(
                    editedText.trim(), accentStart, accentEnd, selectedCategoryId, finalItems,
                    if (reminder.isImportant) starBorderColorIndex else reminder.starBorderColorIndex,
                    if (accentStart != null && accentEnd != null) gradientBlendFraction else null,
                    customSolidColorArgb, editedAttachments, editedVoiceRecordings, editedLocation, editedReferenceUrl, editedMusicBlocks,
                    editedMediaBlocks, editedCardStack, editedContentBlockOrder,
                    cardLayoutForKeys(editedContentBlockLayout, reminder.copy(
                        attachments = editedAttachments, voiceRecordings = editedVoiceRecordings,
                        checklistItems = finalItems, cardStack = editedCardStack, location = editedLocation,
                        referenceUrl = editedReferenceUrl, musicBlocks = editedMusicBlocks, mediaBlocks = editedMediaBlocks
                    ).cardContentKeys()), useDarkText
                )
            } finally {
                savingDetail = false
                if (!screenAlive) {
                    discardDraft(newlyAddedAttachments)
                    discardVoiceDraft(newlyAddedVoiceRecordings)
                    discardCardStackImageDraft(newlyAddedCardStackImages)
                }
            }
            if (saved) {
                discardDraft(newlyAddedAttachments.filter { added -> editedAttachments.none { it.id == added.id } })
                newlyAddedAttachments = emptyList()
                discardVoiceDraft(newlyAddedVoiceRecordings.filter { added -> editedVoiceRecordings.none { it.id == added.id } })
                newlyAddedVoiceRecordings = emptyList()
                discardCardStackImageDraft(newlyAddedCardStackImages.filter { added ->
                    editedCardStack?.cards?.none { it.imageUri == added } ?: true
                })
                newlyAddedCardStackImages = emptyList()
                if (journalEditing) {
                    onSaveJournal(reminder.journal?.mood, editedJournalText.trim())
                }
                finishEditing()
            } else {
                attachmentError = "Değişiklikler kaydedilemedi. Yeniden dene."
            }
        }
    }

    BackHandler(showMoodFullscreen || previewAttachmentPath != null || isReorderingBlocks || editing || inlineCardStackEditing || deleting || showCategoryPicker || journalEditing || journalCreating || showMoodSelector) {
        if (showMoodFullscreen) showMoodFullscreen = false
        else if (previewAttachmentPath != null) previewAttachmentPath = null
        else if (isReorderingBlocks) isReorderingBlocks = false
        else if (showCategoryPicker) showCategoryPicker = false
        else if (deleting) deleting = false
        else if (showMoodSelector) showMoodSelector = false
        else if (editing || inlineCardStackEditing) saveDraftAndClose()
        else if (journalEditing || journalCreating) finishEditing()
    }

    val chromeAlpha by if (animatedVisibilityScope != null && !reduceAnimations) {
        animatedVisibilityScope.transition.animateFloat(
            transitionSpec = {
                RealMotion.smooth(0.32f)
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
                com.cagri.real.ui.components.RealBackButton(
                    onBack = {
                        when {
                            editing -> finishEditing()
                            inlineCardStackEditing -> saveDraftAndClose()
                            else -> onBack()
                        }
                    },
                    description = when {
                        editing -> "Düzenlemeyi iptal et"
                        inlineCardStackEditing -> "Yazıyı kaydet"
                        else -> "Geri"
                    },
                    visualSize = 40.dp,
                    glyphSize = 21.dp,
                    surface = {
                        DesignPanel(
                            scene, { scenePosition }, Modifier.fillMaxSize(),
                            corners = 20.dp, designStyle = designStyle, reduceDynamicBlur = reduceDynamicBlur,
                            liquidDynamic = true, liquidDock = true
                        ) {}
                    }
                )
                if (calendarSyncPublished != null) {
                    // No glass pill behind SYNC: the capsule rendered dim on first frame and clipped the text.
                    val syncColor = if (calendarSyncPublished) Color(0xFF78B9FF) else Color(0xFFF2F3F7)
                    Row(
                        Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(onClick = onCalendarSyncClick)
                            .padding(horizontal = 14.dp)
                            .semantics { contentDescription = if (calendarSyncPublished)
                                "Takvim web bağlantısı güncel" else "İçerik yalnız REAL'de veya web bağlantısı güncel değil" },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ActionIcon(ActionGlyph.Repeat, Modifier.size(16.dp), syncColor)
                        Text(
                            "SYNC", fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.SemiBold, color = syncColor, maxLines = 1
                        )
                    }
                }
                val starSettleScale = remember(reminder.id) { Animatable(1f) }
                var hasRenderedInitialStar by remember(reminder.id) { mutableStateOf(false) }
                LaunchedEffect(reminder.isImportant, reduceAnimations) {
                    if (!hasRenderedInitialStar) {
                        hasRenderedInitialStar = true
                        return@LaunchedEffect
                    }
                    if (reminder.isImportant && !reduceAnimations) {
                        starSettleScale.animateTo(1.16f, tween(80))
                        starSettleScale.animateTo(1.0f, RealMotion.snappy(0.24f))
                    } else {
                        starSettleScale.snapTo(1.0f)
                    }
                }
                val starColor by animateColorAsState(
                    targetValue = if (reminder.isImportant) Color(0xFFEACB75) else Color(0xFFC9CBD3),
                    animationSpec = if (reduceAnimations) snap() else tween(160),
                    label = "starColor"
                )
                val nowBarColor by animateColorAsState(
                    targetValue = when {
                        !nowBarAvailable -> Color(0xFF66666F)
                        nowBarPinned -> Color(0xFF91B9FF)
                        else -> Color(0xFFC9CBD3)
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
                                .size(48.dp)
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
                            ActionIcon(ActionGlyph.NowBar, Modifier.size(20.dp), nowBarColor, filled = nowBarPinned)
                        }
                        Box(Modifier.size(1.dp, 18.dp).background(Color.White.copy(alpha = 0.13f)))
                        IconButton(
                            onClick = {
                                RealHaptics.toggle(view, on = !reminder.isImportant)
                                onToggleImportant()
                            },
                            interactionSource = starInteractionSource,
                            modifier = Modifier
                                .size(48.dp)
                                .pressScale(starInteractionSource, reduceAnimations = reduceAnimations, targetScale = 0.95f)
                                .background(if (reminder.isImportant) Color(0xFFEACB75).copy(alpha = 0.08f) else Color.Transparent)
                                .semantics { contentDescription = if (reminder.isImportant) "Önemli işaretini kaldır" else "Önemli olarak işaretle" }
                        ) {
                            ActionIcon(
                                ActionGlyph.Star,
                                Modifier
                                    .size(21.dp)
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
                DesignPanel(
                    scene, { scenePosition }, Modifier.width(97.dp).height(48.dp).padding(vertical = 4.dp),
                    corners = 20.dp, designStyle = designStyle, reduceDynamicBlur = reduceDynamicBlur,
                    liquidDynamic = true, liquidDock = true
                ) { groupedActions() }
            }
        }
    }

    val headerSpacerHeight by animateDpAsState(
        targetValue = if (editing) 0.dp else 48.dp,
        animationSpec = if (reduceAnimations) snap() else tween(180, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)),
        label = "detailHeaderSpacerHeight"
    )

    // The panel already animates its measured height; applying another animation here lags behind it.
    val detailBottomPadding = with(LocalDensity.current) {
        if (bottomPanelHeightPx > 0) bottomPanelHeightPx.toDp() + 16.dp else 114.dp
    }

    DetailLayoutBox(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { rootBoxCoordinates = it }
            .pointerInput(editing) {
                if (!editing) return@pointerInput
                detectTapGestures(
                    onTap = { localOffset ->
                        val tapInRoot = rootBoxCoordinates?.takeIf { it.isAttached }?.localToRoot(localOffset) ?: localOffset
                        val topAreaBottomPx = with(density) { (statusHeight + 10.dp).toPx() }
                        val bottomBounds = bottomPanelCoordinates?.takeIf { it.isAttached && it.size.height > 0 }?.boundsInRoot()
                        val blockBounds = contentBlockCoordinates.values
                            .filter { it.isAttached && it.size.width > 0 && it.size.height > 0 }
                            .map { it.boundsInRoot() }
                        val footerBounds = footerCoordinates?.takeIf { it.isAttached && it.size.height > 0 }?.boundsInRoot()
                        val hasOverlay = showCamera || showDatePicker || showDetailEndPicker || showLocationGuide ||
                            showNotificationPermissionSheet || showCategoryPicker ||
                            showMoodFullscreen || previewAttachmentPath != null ||
                            musicSearchOpen || mediaSearchOpen || deleting ||
                            showRealShareDialog || showCardStackShareDialog

                        if (isCardDetailBackgroundTap(
                                tapInRoot = tapInRoot,
                                editing = editing,
                                isReordering = isReorderingBlocks,
                                isDragging = cardBlockDrag.active,
                                isBusy = savingDetail || attachmentBusy || voiceRecordingActive,
                                hasActiveOverlay = hasOverlay,
                                topAreaBottom = topAreaBottomPx,
                                bottomPanelBounds = bottomBounds,
                                blockBounds = blockBounds,
                                footerBounds = footerBounds
                            )
                        ) {
                            saveDraftAndClose()
                        }
                    }
                )
            }
    ) {
        DetailLayoutBox(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        val activeBlockKeys = remember(
            editing, checklistBlockOpen, editedChecklistItems, reminder.isChecklist, reminder.checklistItems,
            editedAttachments, reminder.attachments, editedReferenceUrl, reminder.referenceUrl,
            editedVoiceRecordings, reminder.voiceRecordings, voiceRecorderOpen,
            editedMusicBlocks, reminder.musicBlocks,
            editedMediaBlocks, reminder.mediaBlocks,
            editedCardStack, reminder.cardStack, inlineCardStackEditing,
            editedLocation, reminder.location,
            reminder.journal, journalCreating, journalEditing, reminder.mood
        ) {
            val displayed = if (editing) reminder.copy(
                attachments = editedAttachments, voiceRecordings = editedVoiceRecordings,
                checklistItems = editedChecklistItems, cardStack = editedCardStack,
                musicBlocks = editedMusicBlocks, mediaBlocks = editedMediaBlocks,
                referenceUrl = editedReferenceUrl, location = editedLocation
            ) else reminder.copy(cardStack = if (inlineCardStackEditing) editedCardStack else reminder.cardStack)
            displayed.cardContentKeys(
                includeEmptyChecklist = editing && (checklistBlockOpen || reminder.isChecklist),
                includeVoiceRecorder = editing && voiceRecorderOpen,
                includeJournal = journalCreating || (editing && journalEditing)
            )
        }

        LaunchedEffect(activeBlockKeys, editing) {
            if (!editing || configuringLayoutBlock !in activeBlockKeys) configuringLayoutBlock = null
            if (selectedLayoutBlock !in activeBlockKeys) selectedLayoutBlock = null
        }

        val currentOrder = remember(editedContentBlockOrder, reminder.contentBlockOrder, activeBlockKeys, editing, inlineCardStackEditing) {
            resolveBlockOrder(if (editing || inlineCardStackEditing) editedContentBlockOrder else reminder.contentBlockOrder, activeBlockKeys)
        }

        LaunchedEffect(currentOrder, editing, inlineCardStackEditing) {
            if ((editing || inlineCardStackEditing) && editedContentBlockOrder != currentOrder) {
                editedContentBlockOrder = currentOrder
            }
        }

        val blockDescriptors = remember(
            editedText, reminder.text, editedChecklistItems, reminder.checklistItems,
            editedAttachments, reminder.attachments, editedReferenceUrl, reminder.referenceUrl,
            editedVoiceRecordings, reminder.voiceRecordings, editedMusicBlocks, reminder.musicBlocks,
            editedMediaBlocks, reminder.mediaBlocks,
            editedCardStack, reminder.cardStack, inlineCardStackEditing,
            editedLocation, reminder.location, reminder.journal, editedJournalMood, editedJournalText, reminder.mood,
            editing
        ) {
            val sections = mapOf(
                "text" to ContentBlockDescriptor(
                    key = "text",
                    title = "Not Metni",
                    subtitle = if (editing) editedText.ifBlank { "Boş not" } else reminder.text.ifBlank { "Boş not" },
                    glyph = ActionGlyph.Note
                ),
                "card_stack" to ContentBlockDescriptor(
                    key = "card_stack",
                    title = "Kart Yığını",
                    subtitle = "${(if (editing || inlineCardStackEditing) editedCardStack else reminder.cardStack)?.cards?.size ?: 0} kart",
                    glyph = ActionGlyph.Cards
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
                "media" to ContentBlockDescriptor(
                    key = "media",
                    title = "Film & Dizi",
                    subtitle = "${(if (editing) editedMediaBlocks else reminder.mediaBlocks).size} içerik",
                    glyph = ActionGlyph.Video
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
            activeBlockKeys.associateWith { key ->
                val section = sections.getValue(key.substringBefore(':'))
                val id = key.substringAfter(':', "")
                val music = (if (editing) editedMusicBlocks else reminder.musicBlocks).firstOrNull { it.id == id }
                val media = (if (editing) editedMediaBlocks else reminder.mediaBlocks).firstOrNull { it.id == id }
                val attachment = (if (editing) editedAttachments else reminder.attachments).firstOrNull { it.id == id }
                when (key.substringBefore(':')) {
                    "music" -> section.copy(key = key, title = music?.title ?: section.title, subtitle = music?.artist ?: "")
                    "media" -> section.copy(key = key, title = media?.title ?: section.title, subtitle = media?.type?.name ?: "")
                    "photos" -> section.copy(key = key, title = "Fotoğraf", subtitle = attachment?.displayName.orEmpty())
                    "files" -> section.copy(key = key, title = if (key == "files:reference") "Bağlantı" else attachment?.displayName ?: section.title)
                    else -> section.copy(key = key)
                }
            }
        }

        if (editing && isReorderingBlocks) {
            DetailLayoutColumn(
                Modifier.fillMaxSize()
                    .then(if (deleting) Modifier.clearAndSetSemantics { } else Modifier)
                    .onGloballyPositioned { scenePosition = it.positionInRoot() }
                    .drawWithContent {
                        if (com.cagri.real.ui.components.shouldCaptureBackdrop(designStyle, reduceDynamicBlur, Build.VERSION.SDK_INT)) {
                            scene.record { this@drawWithContent.drawContent() }
                            drawLayer(scene)
                        } else drawContent()
                    }
                    .padding(
                        start = 16.dp, end = 16.dp, top = statusHeight + 10.dp,
                        bottom = detailBottomPadding
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
                    }
                }
                Spacer(Modifier.height(10.dp))

                val reorderListState = rememberLazyListState()
                val density = LocalDensity.current
                val edge = with(density) { 48.dp.toPx() }
                val handleTouchWidth = with(density) { 72.dp.toPx() }

                var draggedKey by remember { mutableStateOf<String?>(null) }
                var pointer by remember { mutableStateOf(Offset.Zero) }
                var grab by remember { mutableStateOf(Offset.Zero) }
                var dragWidth by remember { mutableFloatStateOf(0f) }
                var containerBounds by remember { mutableStateOf(Rect.Zero) }
                var listBounds by remember { mutableStateOf(Rect.Zero) }
                val rowBounds = remember { mutableMapOf<String, Rect>() }

                val reorderKeys = remember(editedContentBlockOrder, activeBlockKeys) {
                    mutableStateListOf<String>().apply {
                        addAll(resolveBlockOrder(editedContentBlockOrder, activeBlockKeys))
                    }
                }

                val settleDrag: () -> Unit = {
                    val key = draggedKey
                    if (key != null) {
                        val currentIdx = reorderKeys.indexOf(key)
                        if (currentIdx != -1) {
                            val visibleItems = reorderListState.layoutInfo.visibleItemsInfo
                            if (visibleItems.isNotEmpty()) {
                                val itemHeight = rowBounds[key]?.height ?: with(density) { 64.dp.toPx() }
                                val dragCenterY = pointer.y - grab.y + (itemHeight / 2f) - listBounds.top

                                val targetItem = if (dragCenterY < 0f) {
                                    visibleItems.firstOrNull { it.key != key && it.index < currentIdx }
                                } else if (dragCenterY > listBounds.height) {
                                    visibleItems.lastOrNull { it.key != key && it.index > currentIdx }
                                } else {
                                    visibleItems.firstOrNull { it.key != key && it.index < currentIdx && dragCenterY < it.offset + it.size / 2f }
                                        ?: visibleItems.lastOrNull { it.key != key && it.index > currentIdx && dragCenterY > it.offset + it.size / 2f }
                                }

                                if (targetItem != null) {
                                    val targetKey = targetItem.key as? String
                                    if (targetKey != null && targetKey != key) {
                                        val targetIdx = reorderKeys.indexOf(targetKey)
                                        if (targetIdx != -1 && targetIdx != currentIdx) {
                                            val moved = reorderKeys.removeAt(currentIdx)
                                            val safeInsertIdx = targetIdx.coerceIn(0, reorderKeys.size)
                                            reorderKeys.add(safeInsertIdx, moved)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                fun finishDrag(commit: Boolean) {
                    val key = draggedKey
                    if (commit && key != null) {
                        editedContentBlockOrder = reorderKeys.toList()
                        editedContentBlockLayout = editedContentBlockLayout.mapValues { (_, p) -> p.copy(top = null) }
                    } else if (!commit && key != null) {
                        reorderKeys.clear()
                        reorderKeys.addAll(resolveBlockOrder(editedContentBlockOrder, activeBlockKeys))
                    }
                    draggedKey = null
                }

                val finishDragAction by rememberUpdatedState<(Boolean) -> Unit> { finishDrag(it) }

                LaunchedEffect(draggedKey) {
                    if (draggedKey == null) return@LaunchedEffect
                    var last = withFrameNanos { it }
                    while (draggedKey != null) {
                        val now = withFrameNanos { it }
                        val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                        last = now
                        val speed = when {
                            listBounds.height <= 0f -> 0f
                            pointer.y < listBounds.top + edge -> -((listBounds.top + edge - pointer.y) / edge).coerceIn(0f, 2.5f)
                            pointer.y > listBounds.bottom - edge -> ((pointer.y - (listBounds.bottom - edge)) / edge).coerceIn(0f, 2.5f)
                            else -> 0f
                        }
                        if (speed != 0f) {
                            reorderListState.scrollBy(speed * edge * 8f * dt)
                            settleDrag()
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .onGloballyPositioned { containerBounds = it.boundsInRoot() }
                ) {
                    LazyColumn(
                        state = reorderListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { listBounds = it.boundsInRoot() }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    if (!down.isConsumed && down.position.x >= size.width - handleTouchWidth) {
                                        val absolute = listBounds.topLeft + down.position
                                        val hit = reorderListState.layoutInfo.visibleItemsInfo.firstOrNull {
                                            down.position.y >= it.offset && down.position.y < it.offset + it.size
                                        }
                                        val key = hit?.key as? String
                                        if (key != null && key in reorderKeys) {
                                            val row = rowBounds[key]
                                            val rowTopLeft = row?.topLeft ?: (listBounds.topLeft + Offset(0f, hit.offset.toFloat()))
                                            val rowWidth = row?.width ?: size.width.toFloat()
                                            var started = false
                                            var released = false
                                            try {
                                                while (true) {
                                                    val change = awaitPointerEvent(PointerEventPass.Initial)
                                                        .changes.firstOrNull { it.id == down.id } ?: break
                                                    if (change.isConsumed) break
                                                    if (!change.pressed) {
                                                        if (started) change.consume()
                                                        released = true
                                                        break
                                                    }
                                                    if (!started && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                                        grab = absolute - rowTopLeft
                                                        dragWidth = rowWidth
                                                        draggedKey = key
                                                        started = true
                                                    }
                                                    if (started) {
                                                        pointer = listBounds.topLeft + change.position
                                                        settleDrag()
                                                        change.consume()
                                                    }
                                                }
                                            } finally {
                                                if (started) finishDragAction(released)
                                            }
                                        }
                                    }
                                }
                            },
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(reorderKeys, key = { _, key -> key }) { index, key ->
                            val desc = blockDescriptors[key] ?: return@itemsIndexed
                            val isItemDragged = key == draggedKey

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (reduceAnimations) Modifier else Modifier.animateItem())
                                    .onGloballyPositioned { rowBounds[key] = it.boundsInRoot() }
                            ) {
                                ContentBlockReorderRow(
                                    desc = desc,
                                    isDragging = false,
                                    modifier = Modifier.graphicsLayer {
                                        alpha = if (isItemDragged) 0.16f else 1f
                                    }
                                )
                            }
                        }
                    }

                    draggedKey?.let { key ->
                        val desc = blockDescriptors[key]
                        if (desc != null) {
                            ContentBlockReorderRow(
                                desc = desc,
                                isDragging = true,
                                modifier = Modifier
                                    .zIndex(10f)
                                    .offset {
                                        IntOffset(
                                            (pointer.x - grab.x - containerBounds.left).roundToInt(),
                                            (pointer.y - grab.y - containerBounds.top).roundToInt()
                                        )
                                    }
                                    .then(
                                        if (dragWidth > 0f) Modifier.width(with(density) { dragWidth.toDp() })
                                        else Modifier.fillMaxWidth()
                                    )
                            )
                        }
                    }
                }
            }
        } else {
            DetailLayoutColumn(
                Modifier.fillMaxSize()
                    .then(if (deleting) Modifier.clearAndSetSemantics { } else Modifier)

                    .onGloballyPositioned { scenePosition = it.positionInRoot() }
                    .drawWithContent {
                        if (com.cagri.real.ui.components.shouldCaptureBackdrop(designStyle, reduceDynamicBlur, Build.VERSION.SDK_INT)) { scene.record { this@drawWithContent.drawContent() }; drawLayer(scene) }
                        else drawContent()
                    }
                    .cardBlockDragInput(cardBlockDrag, detailContentScroll)
                    .verticalScroll(detailContentScroll)
                    .padding(start = 16.dp, end = 16.dp, top = statusHeight + 10.dp,
                        bottom = detailBottomPadding)
            ) {
                if (designStyle == DesignStyle.LiquidGlass) {
                    if (headerSpacerHeight > 0.dp) Spacer(Modifier.height(headerSpacerHeight))
                } else detailHeader()

                val corner = if (!reduceAnimations && animatedVisibilityScope != null) {
                    animatedVisibilityScope.transition.animateFloat(
                        transitionSpec = { RealMotion.smooth(0.32f) }, label = "detailCardCorner"
                    ) { if (it == EnterExitState.Visible) 28f else 22f }.value
                } else 28f
                val cardShape = RoundedCornerShape(corner.dp)
                val shownAccentStart = if (editing) accentStart else reminder.accentStart
                val shownAccentEnd = if (editing) accentEnd else reminder.accentEnd
                val shownBlendFraction = if (editing) gradientBlendFraction else reminder.gradientBlendFraction
                val shownCustomSolidColorArgb = if (editing) customSolidColorArgb else reminder.customSolidColorArgb
                val accentBrush = remember(shownAccentStart, shownAccentEnd, reminder.isCompleted, shownBlendFraction, shownCustomSolidColorArgb) {
                    reminderAccentBrush(shownAccentStart, shownAccentEnd, reminder.isCompleted, shownBlendFraction, shownCustomSolidColorArgb)
                }
                val hasActiveFullscreenOverlay = showMoodFullscreen || previewAttachmentPath != null
                val sharedCardModifier = if (com.cagri.real.ui.components.planner.LocalPlannerCardMotion.current != null) {
                    com.cagri.real.ui.components.planner.plannerDetailMotion(
                        reminder.id,
                        renderInOverlay = !hasActiveFullscreenOverlay
                    )
                } else if (!reduceAnimations && sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedBounds(
                            rememberSharedContentState(key = "reminder-card-${reminder.id}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ -> RealMotion.smooth(0.32f) },
                            clipInOverlayDuringTransition = OverlayClip(cardShape),
                            renderInOverlayDuringTransition = !hasActiveFullscreenOverlay
                        )
                    }
                } else Modifier

                val currentStarBorderIndex = if (editing) starBorderColorIndex else (reminder.starBorderColorIndex ?: Math.floorMod(reminder.id, 10))
                // Same white ring as the list card; the stored star border index is ignored for drawing.
                val cardBorderColor = if (reminder.isImportant) StarCardBorderColor else null
                val cardBorderModifier = if (cardBorderColor != null) {
                    Modifier.border(1.25.dp, cardBorderColor, cardShape)
                } else Modifier

                val visibleBlocks = currentOrder
                val displayedLayout = cardDetailReadablePlacements(cardLayoutForKeys(
                    if (editing || inlineCardStackEditing) editedContentBlockLayout else reminder.contentBlockLayout,
                    activeBlockKeys
                ), detailWidthDp)
                Spacer(Modifier.height(12.dp))

                CardCompositionGrid(
                    keys = visibleBlocks, placements = displayedLayout,
                    editing = editing && !savingDetail && !voiceRecordingActive,
                    reduceAnimations = reduceAnimations, selected = selectedLayoutBlock,
                    dragState = cardBlockDrag, scrollState = detailContentScroll,
                    onSelect = { selectedLayoutBlock = it },
                    onFreeDrop = { layout -> editedContentBlockLayout = layout },
                    onPlacementChange = { key, placement -> editedContentBlockLayout = displayedLayout + (key to placement.normalized()) },
                    onBlockCoordinates = { key, coords ->
                        if (coords != null) contentBlockCoordinates[key] = coords
                        else contentBlockCoordinates.remove(key)
                    }
                ) { blockKey, blockIndex ->
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .realPetPerch(realPetDetailBlockPerchId(reminder.id, blockKey))
                            .onGloballyPositioned { contentBlockCoordinates[blockKey] = it }
                    ) {
                        val blockWidth = maxWidth
                        val narrowBlock = blockWidth < 240.dp
                        when (blockKey.substringBefore(':')) {
                        "text" -> {
                            val titlePlacement = displayedLayout["text"] ?: CardBlockPlacement()
                            val titleHeight = titlePlacement.minHeight
                            val compactTitle = titleHeight in 1 until 200
                            val titlePadding = if (compactTitle) 8.dp else 25.dp
                            val titleLines = if (titleHeight > 0)
                                ((titleHeight - (if (compactTitle) 16 else 50) -
                                    (if (!editing && !compactTitle) 120 else 0)) / 31).coerceAtLeast(1)
                                else Int.MAX_VALUE
                            val titleAlignment = when (titlePlacement.textAlignment) {
                                "center" -> TextAlign.Center
                                "end" -> TextAlign.End
                                else -> TextAlign.Start
                            }
                            Box(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier
                                    .then(sharedCardModifier)
                                    .fillMaxWidth()
                                    .pullDownToDismiss(
                                        if (!editing && !inlineCardStackEditing && !deleting && !hasActiveFullscreenOverlay) pullDismiss else null,
                                        detailContentScroll
                                    )
                                    .then(if (titleHeight > 0) Modifier.height(titleHeight.dp) else Modifier)
                                    .cardBlockSurface()
                                    .clip(cardShape)
                                    .then(
                                        if (accentBrush == null) Modifier.background(
                                            if (designStyle == DesignStyle.LiquidGlass) Brush.verticalGradient(listOf(Color(0xFF18181A), LiquidGlassTokens.contentColor))
                                            else Brush.linearGradient(listOf(Color(0xFF25252C), Color(0xFF17171B), Color(0xFF131315)))
                                        ) else Modifier.background(accentBrush)
                                    )
                                    .then(cardBorderModifier)
                                    .padding(horizontal = 22.dp, vertical = titlePadding)
                            ) {
                                val shownUseDarkText = if (editing) useDarkText else reminder.useDarkText
                                if (editing) {
                                    BasicTextField(
                                        value = editedText, onValueChange = { editedText = it },
                                        maxLines = titleLines,
                                        modifier = Modifier.fillMaxWidth()
                                            .heightIn(min = if (titleHeight > 0) 0.dp else 96.dp)
                                            .onFocusChanged { if (it.isFocused) secondaryPanelsExpanded = false }
                                            .semantics { contentDescription = "Hatırlatıcı metni" },
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            color = if (shownUseDarkText) Color(0xFF101010) else Color.White,
                                            fontSize = 21.sp,
                                            lineHeight = 31.sp,
                                            textAlign = titleAlignment,
                                            fontWeight = if (titlePlacement.textBold) FontWeight.Bold else FontWeight.Normal,
                                            fontStyle = if (titlePlacement.textItalic) FontStyle.Italic else FontStyle.Normal
                                        ),
                                        cursorBrush = SolidColor(if (shownUseDarkText) Color(0xFF101010) else Color(0xFF82B3FF)),
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                                    )
                                } else {
                                    SelectionContainer {
                                        Text(
                                            reminder.text,
                                            maxLines = titleLines,
                                            overflow = TextOverflow.Ellipsis,
                                            color = when {
                                                shownUseDarkText -> if (reminder.isCompleted && reminder.journal == null) Color(0xFF55555A) else Color(0xFF101010)
                                                reminder.isCompleted && reminder.journal == null -> Color(0xFF9999A2)
                                                else -> Color(0xFFF4F4F6)
                                            },
                                            fontSize = 21.sp,
                                            lineHeight = 31.sp,
                                            textAlign = titleAlignment,
                                            fontWeight = if (titlePlacement.textBold) FontWeight.Bold else FontWeight.Normal,
                                            fontStyle = if (titlePlacement.textItalic) FontStyle.Italic else FontStyle.Normal,
                                            modifier = Modifier.fillMaxWidth(),
                                            textDecoration = if (reminder.isCompleted && reminder.journal == null) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                    }
                                }

                                if (!editing && !compactTitle) {
                                    HorizontalDivider(
                                        Modifier.padding(top = 24.dp, bottom = 18.dp),
                                        color = if (shownUseDarkText) Color(0xFF101010).copy(alpha = 0.2f) else Color(0xFF38383F)
                                    )
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
                                                    when {
                                                        reminder.isCompleted -> {
                                                            Text(statusText, color = Color(0xFFD3D3D9), style = RealType.detailBlockLabel)
                                                        }
                                                        reminder.alertAt != null -> {
                                                            val isOverdue = reminder.alertAt < System.currentTimeMillis()
                                                            val dateColor = if (isOverdue) Color(0xFFF87171) else Color(0xFF94B8F7)
                                                            val dur = detailDurationMinutes
                                                            val endAt = dur?.let { reminder.alertAt + it * 60_000L }
                                                            val endTimeStr = endAt?.let { com.cagri.real.ui.components.planner.formatTimeAmPm(it, ZoneId.systemDefault()) }
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Text(statusText, color = Color(0xFFD3D3D9), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                                Text("•", color = Color(0xFF6B6B75), style = RealType.detailBlockMetaSecondary)
                                                                Text(detailReminderDate(reminder.alertAt), color = dateColor, style = RealType.detailBlockLabel)
                                                            }
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                modifier = Modifier.padding(top = 2.dp)
                                                            ) {
                                                                Text("Süre: $dur dk", color = Color(0xFFA5A5B0), style = RealType.detailBlockMetaSecondary)
                                                                Text("•", color = Color(0xFF6B6B75), style = RealType.detailBlockMetaSecondary)
                                                                Text("Bitiş: $endTimeStr", color = Color(0xFFA5A5B0), style = RealType.detailBlockMetaSecondary)
                                                            }
                                                            val repeatText = reminder.repeatRule.repeatSummary(reminder.anchorAlertAt ?: reminder.alertAt)
                                                            if (repeatText != null) {
                                                                Text(
                                                                    repeatText,
                                                                    color = Color(0xFFA5A5B0),
                                                                    style = RealType.cardMetaPrimary,
                                                                    modifier = Modifier.padding(top = 1.dp)
                                                                )
                                                            }
                                                        }
                                                        reminder.plannedDateEpochDay != null -> {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Text(statusText, color = Color(0xFFD3D3D9), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                                Text("•", color = Color(0xFF6B6B75), style = RealType.detailBlockMetaSecondary)
                                                                Text(detailPlannedDate(reminder.plannedDateEpochDay), color = Color(0xFF94B8F7), style = RealType.detailBlockLabel)
                                                            }
                                                        }
                                                        else -> Text(statusText, color = Color(0xFFD3D3D9), style = RealType.detailBlockLabel)
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
                                                        Text(assignedCategory.name, color = Color(0xFFC4C4CE), style = RealType.detailBlockMetaSecondary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (editing && !savingDetail && (selectedLayoutBlock == null || selectedLayoutBlock == "text")) {
                                com.cagri.real.ui.components.CardTextSettingsControl {
                                    textToolsKey = "text"
                                    showTitleTools = true
                                }
                            }
                            }
                        }
                        "mood" -> {
                            reminder.mood?.let { mood ->
                                val moodTint = moodV2Tint(mood)
                                Row(
                                    modifier = Modifier.fillMaxWidth().cardBlockSurface()
                                        .clip(cardShape)
                                        .background(Brush.verticalGradient(listOf(moodTint.copy(alpha = .30f), Color(0xFF121214))))
                                        .padding(if (narrowBlock) 12.dp else 20.dp)
                                        .semantics { contentDescription = moodV2FamilyTitle(mood) },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = if (narrowBlock) Arrangement.Center else Arrangement.spacedBy(14.dp)
                                ) {
                                    MoodV2Face(mood = mood,
                                        size = if (narrowBlock) (blockWidth - 24.dp).coerceIn(36.dp, 64.dp) else 72.dp,
                                        tint = moodTint)
                                    if (!narrowBlock) Column(Modifier.weight(1f)) {
                                        val details = reminder.moodDetails
                                        Text(details?.scope?.label ?: "Mood", color = moodTint, fontSize = 12.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(moodV2FamilyTitle(mood), color = Color(0xFFF7F7F9), fontSize = 21.sp,
                                            lineHeight = 26.sp, fontWeight = FontWeight.SemiBold,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (!details?.descriptors.isNullOrEmpty()) Text(
                                            details!!.descriptors.take(3).joinToString(" · ") { moodDescriptorLabelV2(it) },
                                            color = Color(0xFFC9C9D0), fontSize = 13.sp, lineHeight = 18.sp,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (!details?.factors.isNullOrEmpty()) Text(
                                            details!!.factors.take(4).joinToString(" · ") { moodFactorLabelV2(it) },
                                            color = Color(0xFF8E8E98), fontSize = 11.sp, lineHeight = 16.sp,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                            val displayedPhotos = displayedAttachments.filter { it.type == AttachmentType.IMAGE && it.id == blockKey.substringAfter(':') }
                            if (displayedPhotos.isNotEmpty()) {
                                AttachmentMediaSection(
                                    attachments = displayedPhotos,
                                    designStyle = designStyle,
                                    editing = editing,
                                    heightOverride = displayedLayout[blockKey]?.minHeight?.takeIf { it > 0 }?.dp,
                                    onOpen = { previewAttachmentPath = it.localPath },
                                    onRemove = { attachment ->
                                        editedAttachments = editedAttachments.filter { it.id != attachment.id }
                                    }
                                )
                            }
                        }
                        "files" -> {
                            val displayedAttachments = if (editing) editedAttachments else reminder.attachments
                            val inlineText = displayedAttachments.firstOrNull {
                                it.id == blockKey.substringAfter(':') && it.type == AttachmentType.TEXT_SNIPPET &&
                                    it.sourceTitle == "REAL_TEXT_BLOCK"
                            }
                            if (inlineText != null) {
                                com.cagri.real.ui.components.CardStandaloneTextBox(
                                    text = inlineText.text.orEmpty(),
                                    editing = editing && !savingDetail,
                                    placement = displayedLayout[blockKey] ?: CardBlockPlacement(),
                                    darkText = if (editing) useDarkText else reminder.useDarkText,
                                    designStyle = designStyle,
                                    accentBrush = accentBrush,
                                    showSettings = editing && selectedLayoutBlock == blockKey,
                                    onSettings = { textToolsKey = blockKey; showTitleTools = true },
                                    onTextChange = { value -> editedAttachments = editedAttachments.map {
                                        if (it.id == inlineText.id) it.copy(text = value) else it
                                    } },
                                    onRemove = {
                                        editedAttachments = editedAttachments.filterNot { it.id == inlineText.id }
                                        selectedLayoutBlock = null
                                    }
                                )
                            } else ReminderContentSections(
                                attachments = displayedAttachments.filter { it.id == blockKey.substringAfter(':') },
                                referenceUrl = if (blockKey == "files:reference") (if (editing) editedReferenceUrl else reminder.referenceUrl) else null,
                                editing = editing && !savingDetail, designStyle = designStyle,
                                backdrop = scene, backdropPosition = { scenePosition },
                                onRemove = { item -> editedAttachments = editedAttachments.filterNot { it.id == item.id } },
                                onRemoveLink = { editedReferenceUrl = null },
                                onUpdateSnippet = { snippet, updatedText ->
                                    val sourceAttachments = if (editing) editedAttachments else reminder.attachments
                                    val updatedAttachments = sourceAttachments.map { attachment ->
                                        if (attachment.id == snippet.id) attachment.copy(text = updatedText) else attachment
                                    }
                                    if (editing) {
                                        editedAttachments = updatedAttachments
                                        true
                                    } else if (savingDetail) {
                                        false
                                    } else {
                                        savingDetail = true
                                        try {
                                            onSave(
                                                reminder.text, reminder.accentStart, reminder.accentEnd, reminder.customCategoryId, reminder.checklistItems,
                                                reminder.starBorderColorIndex, reminder.gradientBlendFraction, reminder.customSolidColorArgb, updatedAttachments,
                                                reminder.voiceRecordings, reminder.location, reminder.referenceUrl, reminder.musicBlocks, reminder.mediaBlocks,
                                                reminder.cardStack, reminder.contentBlockOrder, reminder.contentBlockLayout, reminder.useDarkText
                                            )
                                        } finally {
                                            savingDetail = false
                                        }
                                    }
                                }
                            )
                        }
                        "voice" -> {
                            val displayedVoiceRecordings = if (editing) editedVoiceRecordings else reminder.voiceRecordings
                            if (displayedVoiceRecordings.isNotEmpty() || (editing && voiceRecorderOpen)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .cardBlockSurface()
                                        .clip(RoundedCornerShape(22.dp))
                                        .then(
                                            if (designStyle == DesignStyle.LiquidGlass) Modifier.cardContentChrome(corners = 22.dp)
                                            else Modifier
                                                .background(Color(0xFF141416))
                                        )
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
                                        showHeader = !narrowBlock,
                                        compact = narrowBlock,
                                        enabled = editing && !savingDetail && !attachmentBusy,
                                        onRecordingStateChange = { voiceRecordingActive = it },
                                        onClose = {
                                            discardVoiceDraft(newlyAddedVoiceRecordings)
                                            newlyAddedVoiceRecordings = emptyList()
                                            editedVoiceRecordings = emptyList()
                                            voiceRecorderOpen = false
                                            voiceRecordingActive = false
                                            selectedLayoutBlock = null
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        "music" -> {
                            MusicContentSection(
                                blocks = (if (editing) editedMusicBlocks else reminder.musicBlocks).filter { it.id == blockKey.substringAfter(':') },
                                display = com.cagri.real.data.catalogCardDisplay(displayedLayout[blockKey]?.span ?: com.cagri.real.data.CARD_GRID_UNITS),
                                showActions = displayedLayout[blockKey]?.hideActions != true,
                                designStyle = designStyle, editing = editing && !savingDetail,
                                onRemove = { id -> editedMusicBlocks = editedMusicBlocks.filterNot { it.id == id } }
                            )
                        }
                        "media" -> {
                            MediaContentSection(
                                blocks = (if (editing) editedMediaBlocks else reminder.mediaBlocks).filter { it.id == blockKey.substringAfter(':') },
                                display = com.cagri.real.data.catalogCardDisplay(displayedLayout[blockKey]?.span ?: com.cagri.real.data.CARD_GRID_UNITS),
                                showActions = displayedLayout[blockKey]?.hideActions != true,
                                designStyle = designStyle, editing = editing && !savingDetail,
                                onRemove = { id -> editedMediaBlocks = editedMediaBlocks.filterNot { it.id == id } }
                            )
                        }
                        "card_stack" -> {
                            val allStacks = if (editing || inlineCardStackEditing) editedCardStack else reminder.cardStack
                            val stack = allStacks?.layoutStacks()?.firstOrNull { it.first == blockKey }?.second
                            if (stack != null) {
                                val stackBringRequester = remember(blockKey) { BringIntoViewRequester() }
                                LaunchedEffect(blockKey) {
                                    if (editing && selectedLayoutBlock == blockKey) {
                                        withFrameNanos { }
                                        try { stackBringRequester.bringIntoView() } catch (_: Exception) { }
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .bringIntoViewRequester(stackBringRequester)
                                        .then(if (blockKey == inlineCardStackKey) Modifier.bringIntoViewRequester(cardStackBringIntoViewRequester) else Modifier)
                                ) {
                                    CardStackContentSection(
                                        block = stack,
                                        heightOverride = displayedLayout[blockKey]?.minHeight?.takeIf { it > 0 }?.dp,
                                        designStyle = designStyle,
                                        editing = editing && !savingDetail,
                                        inlineTextEditing = inlineCardStackEditing && inlineCardStackKey == blockKey && !savingDetail,
                                        initialActiveCardIndex = inlineCardStackStartIndex,
                                        onRequestInlineEdit = { index ->
                                            if (!editing && !inlineCardStackEditing && !savingDetail) {
                                                loadReminderIntoDraft()
                                                inlineCardStackStartIndex = index
                                                inlineCardStackKey = blockKey
                                                inlineCardStackEditing = true
                                            }
                                        },
                                        onUpdate = { updated -> editedCardStack = editedCardStack?.replaceLayoutStack(blockKey, updated) },
                                        onRemove = {
                                            val removedPaths = stack.cards.mapNotNull { it.imageUri }.toSet()
                                            val unsaved = newlyAddedCardStackImages.filter { it in removedPaths }
                                            discardCardStackImageDraft(unsaved)
                                            newlyAddedCardStackImages = newlyAddedCardStackImages - unsaved.toSet()
                                            editedCardStack = editedCardStack?.replaceLayoutStack(blockKey, null)
                                            editedContentBlockOrder = editedContentBlockOrder.filter { it != blockKey }
                                            editedContentBlockLayout = editedContentBlockLayout - blockKey
                                        },
                                        onImageAdded = { path ->
                                            newlyAddedCardStackImages = newlyAddedCardStackImages + path
                                        },
                                        onImageReplacedOrRemoved = { oldPath ->
                                            if (oldPath in newlyAddedCardStackImages) {
                                                newlyAddedCardStackImages = newlyAddedCardStackImages - oldPath
                                                discardCardStackImageDraft(listOf(oldPath))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        "location" -> {
                            val place = if (editing) editedLocation else reminder.location
                            if (place != null) {
                                val locCardShape = RoundedCornerShape(22.dp)
                                if (place.latitude != null && place.longitude != null) {
                                    Box(
                                        Modifier.fillMaxWidth().height((displayedLayout[blockKey]?.minHeight?.takeIf { it > 0 } ?: 180).coerceAtLeast(120).dp)
                                            .clip(locCardShape)
                                            .background(if (designStyle == DesignStyle.LiquidGlass) LiquidGlassTokens.contentColor else Color(0xFF131820))
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
                                        Modifier.fillMaxWidth().cardBlockSurface()
                                            .clip(locCardShape)
                                            .background(if (designStyle == DesignStyle.LiquidGlass) LiquidGlassTokens.contentColor else Color(0xFF141416))
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
                                                Column(Modifier.weight(1f)) {
                                                    if (place.placeName.isNotBlank()) {
                                                        Text(place.placeName, color = Color(0xFFE2E2E8), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                    if (place.address.isNotBlank()) {
                                                        Text(place.address, color = Color(0xFFA5A5B0), style = RealType.detailBlockMeta, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                        Text("Haritada aç", color = Color(0xFF9E9EA4), style = RealType.detailBlockMetaSecondary, modifier = Modifier.padding(top = 2.dp))
                                                    } else if (!place.mapsUrl.isNullOrBlank()) {
                                                        Text("Haritada aç", color = Color(0xFFA5A5B0), style = RealType.detailBlockMeta)
                                                    } else {
                                                        Text("Konum ayrıntısı bulunamadı", color = Color(0xFFA5A5B0), style = RealType.detailBlockMeta)
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
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showJournalCard,
                                enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { -it / 8 },
                                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 8 }
                            ) {
                                val isJournalActive = journalCreating || journalEditing
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .cardBlockSurface()
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(if (designStyle == DesignStyle.LiquidGlass) LiquidGlassTokens.contentColor else Color(0xFF141416))
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
                                        reminder.journal?.createdAt?.let { createdAt ->
                                            Text(
                                                text = java.time.Instant.ofEpochMilli(createdAt)
                                                    .atZone(java.time.ZoneId.systemDefault())
                                                    .format(java.time.format.DateTimeFormatter.ofPattern(
                                                        "d MMMM yyyy · HH:mm", java.util.Locale.forLanguageTag("tr-TR")
                                                    )),
                                                color = Color(0xFF8E8E98),
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(top = 8.dp)
                                            )
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

                if (mediaSearchOpen && editing) {
                    MediaSearchSheet(
                        designStyle = designStyle,
                        onDismiss = { mediaSearchOpen = false },
                        onSelect = { mediaBlock ->
                            val alreadyExists = editedMediaBlocks.any { existing ->
                                (!existing.providerId.isNullOrBlank() && existing.providerId == mediaBlock.providerId) ||
                                existing.providerUrl == mediaBlock.providerUrl ||
                                (existing.title.equals(mediaBlock.title, ignoreCase = true) && existing.type == mediaBlock.type && existing.year == mediaBlock.year)
                            }
                            if (!alreadyExists) {
                                editedMediaBlocks = editedMediaBlocks + mediaBlock
                            }
                            mediaSearchOpen = false
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { footerCoordinates = it }
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
        DetailLayoutColumn(
            Modifier
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { bottomPanelCoordinates = it }
                .onSizeChanged { bottomPanelHeightPx = it.height }
                .fillMaxWidth()
                .graphicsLayer { alpha = chromeAlpha }
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            if (editing) {
                DetailEditDock(
                    collapsed = editDockCollapsed, enabled = !savingDetail,
                    reduceAnimations = reduceAnimations,
                    onCollapsedChange = { collapsed ->
                        focusManager.clearFocus()
                        keyboard?.hide()
                        editDockCollapsed = collapsed
                    }
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
                                useDarkText = useDarkText,
                                onUseDarkTextChange = { useDarkText = it },
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
                                            Text(currentCat.name, color = Color.White, style = RealType.categoryPill)
                                        } else {
                                            Text("Kategori yok", color = Color(0xFF8E8E98), style = RealType.detailBlockMeta)
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
                                val mediaCount = editedMediaBlocks.size
                                val voiceCount = editedVoiceRecordings.size
                                val checklistCount = editedChecklistItems.size

                                DetailQuickActionButton(
                                    designStyle = designStyle, reduceAnimations = reduceAnimations,
                                    glyph = ActionGlyph.Note, description = "Metin kutusu ekle",
                                    enabled = !savingDetail && !voiceRecordingActive,
                                    active = editedAttachments.any { it.sourceTitle == "REAL_TEXT_BLOCK" },
                                    onClick = {
                                        focusManager.clearFocus()
                                        val id = java.util.UUID.randomUUID().toString()
                                        editedAttachments = editedAttachments + com.cagri.real.data.ReminderAttachment(
                                            id = id, type = AttachmentType.TEXT_SNIPPET, mimeType = "text/plain",
                                            displayName = "Metin", text = "", sourceTitle = "REAL_TEXT_BLOCK"
                                        )
                                        val key = "files:$id"
                                        editedContentBlockOrder = (editedContentBlockOrder + key).distinct()
                                        editedContentBlockLayout = editedContentBlockLayout + (key to CardBlockPlacement())
                                        selectedLayoutBlock = key
                                    }
                                )
                                DetailQuickActionButton(
                                    designStyle = designStyle,
                                    reduceAnimations = reduceAnimations,
                                    glyph = ActionGlyph.Calendar,
                                    description = "Tarih / Saat",
                                    enabled = !savingDetail && !reminder.isCompleted,
                                    active = reminder.alertAt != null || reminder.plannedDateEpochDay != null,
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
                                    glyph = ActionGlyph.Camera,
                                    description = "Kamera ile fotoğraf ekle",
                                    enabled = !savingDetail && !attachmentBusy && !voiceRecordingActive,
                                    active = showCamera,
                                    isFull = photoCount >= 3,
                                    onClick = {
                                        if (photoCount >= 3) Toast.makeText(context, "En fazla 3 fotoğraf eklenebilir", Toast.LENGTH_SHORT).show()
                                        else {
                                            focusManager.clearFocus()
                                            keyboard?.hide()
                                            showCamera = true
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
                                    glyph = ActionGlyph.Video,
                                    description = "Film & Dizi",
                                    enabled = !savingDetail && !attachmentBusy && !voiceRecordingActive,
                                    active = mediaCount > 0,
                                    count = if (mediaCount > 0) mediaCount else null,
                                    onClick = { focusManager.clearFocus(); keyboard?.hide(); mediaSearchOpen = true }
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
                                    glyph = ActionGlyph.Cards,
                                    description = "Kart Yığını",
                                    enabled = !savingDetail,
                                    active = editedCardStack != null,
                                    count = editedCardStack?.layoutStacks()?.size?.takeIf { it > 0 },
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboard?.hide()
                                        val groupId = java.util.UUID.randomUUID().toString()
                                        val newKey = "card_stack:$groupId"
                                        val newCard = CardStackCard(stackGroupId = groupId)
                                        editedCardStack = editedCardStack?.let { it.copy(cards = it.cards + newCard) }
                                            ?: CardStackBlock(prompt = null, cards = listOf(newCard))
                                        val stackKeys = editedCardStack?.layoutStacks().orEmpty().map { it.first }
                                        val nextOrder = resolveBlockOrder(editedContentBlockOrder, (activeBlockKeys + stackKeys).toSet())
                                            .filterNot { it == newKey }.toMutableList()
                                        nextOrder.add(newKey)
                                        editedContentBlockOrder = nextOrder
                                        // New stacks start at normal width; the existing handles can shrink them later.
                                        editedContentBlockLayout = editedContentBlockLayout + (newKey to com.cagri.real.data.CardBlockPlacement(span = com.cagri.real.data.CARD_GRID_UNITS))
                                        selectedLayoutBlock = newKey

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
                                            Text(detailReminderDate(reminder.alertAt), color = Color(0xFFE2E8F0), style = RealType.composerScheduleChip)
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
                                                Text(repeatLabel, color = Color(0xFFD2D2DA), style = RealType.composerAuxiliaryPill)
                                            }
                                        }
                                        val currentEndAt = detailDurationMinutes?.let { reminder.alertAt + it * 60_000L }
                                        val currentEndTimeStr = if (currentEndAt != null) com.cagri.real.ui.components.planner.formatTimeAmPm(currentEndAt, ZoneId.systemDefault()) else "—"

                                        Row(
                                            modifier = Modifier
                                                .clip(scheduleShape)
                                                .then(
                                                    if (designStyle == DesignStyle.LiquidGlass) Modifier.liquidChrome(12.dp)
                                                    else Modifier.background(Color.White.copy(alpha = 0.07f)).border(0.7.dp, Color.White.copy(alpha = 0.12f), scheduleShape)
                                                )
                                                .clickable {
                                                    val nextDur = when (detailDurationMinutes) {
                                                        null -> 30
                                                        30 -> 45
                                                        45 -> 60
                                                        60 -> 90
                                                        90 -> 120
                                                        120 -> 150
                                                        150 -> 180
                                                        else -> null
                                                    }
                                                    detailDurationMinutes = nextDur
                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        plannerRepo.put(reminder.id, (plannerDetails ?: com.cagri.real.ui.components.planner.PlannerEventDetails()).copy(durationMinutes = nextDur))
                                                    }
                                                }
                                                .padding(horizontal = 9.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            ActionIcon(ActionGlyph.Note, Modifier.size(14.dp), Color(0xFF91B9FF))
                                            Text(if (detailDurationMinutes != null) "Süre: $detailDurationMinutes min" else "Süre ekle", color = Color(0xFFD2D2DA), style = RealType.composerAuxiliaryPill)
                                        }

                                        Row(
                                            modifier = Modifier
                                                .clip(scheduleShape)
                                                .then(
                                                    if (designStyle == DesignStyle.LiquidGlass) Modifier.liquidChrome(12.dp)
                                                    else Modifier.background(Color.White.copy(alpha = 0.07f)).border(0.7.dp, Color.White.copy(alpha = 0.12f), scheduleShape)
                                                )
                                                .clickable { showDetailEndPicker = true }
                                                .padding(horizontal = 9.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            ActionIcon(ActionGlyph.Calendar, Modifier.size(14.dp), Color(0xFF91B9FF))
                                            Text("Bitiş: $currentEndTimeStr", color = Color(0xFFD2D2DA), style = RealType.composerAuxiliaryPill)
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
                            if (attachmentBusy) Text("Ek hazırlanıyor…", color = Color(0xFFB4B4BE), style = RealType.detailBlockMetaSecondary)
                            attachmentError?.let { Text(it, color = Color(0xFFE0B5A8), style = RealType.detailBlockMetaSecondary) }
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = deleting,
                enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(180)) + slideInVertically(tween(240)) { it / 8 } + scaleIn(tween(240), initialScale = 0.97f),
                exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 8 } + scaleOut(tween(180), targetScale = 0.97f)
            ) {
                val scopedDelete = reminder.hasRepeat && repeatOccurrenceMillis != null
                DeleteReminderPrompt(scene, { scenePosition },
                    onDismiss = { deleting = false }, onConfirm = { deleting = false; onDelete() },
                    onConfirmThisOccurrence = if (scopedDelete) ({ deleting = false; onDeleteRepeatOccurrence(com.cagri.real.data.RepeatDeleteScope.THIS_OCCURRENCE) }) else null,
                    onConfirmThisAndFuture = if (scopedDelete) ({ deleting = false; onDeleteRepeatOccurrence(com.cagri.real.data.RepeatDeleteScope.THIS_AND_FUTURE) }) else null,
                    occurrenceIsNext = repeatOccurrenceIsNext,
                    modifier = Modifier.padding(bottom = 12.dp), designStyle = designStyle, reduceDynamicBlur = reduceDynamicBlur)
            }

            val hasCompletionJournalCard = remember(reminder.cardStack) {
                reminder.cardStack?.cards?.any { card ->
                    card.id.startsWith(COMPLETED_JOURNAL_CARD_ID_PREFIX) ||
                        card.title.trim().equals("Nasıl geçti?", ignoreCase = true)
                } == true
            }
            AnimatedVisibility(
                visible = reminder.isCompleted && !editing && !inlineCardStackEditing && !hasCompletionJournalCard,
                enter = fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 4 },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { it / 4 }
            ) {
                val addJournalCardRow: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .clickable {
                                loadReminderIntoDraft()
                                val existing = reminder.cardStack
                                val journalCard = CardStackCard(
                                    id = COMPLETED_JOURNAL_CARD_ID_PREFIX + System.currentTimeMillis(),
                                    title = "",
                                    colorArgb = CardStackDefaultColor.toArgb()
                                )
                                editedCardStack = if (existing == null) {
                                    CardStackBlock(prompt = null, cards = listOf(journalCard))
                                } else {
                                    existing.copy(cards = existing.cards + journalCard)
                                }
                                val nextOrder = resolveBlockOrder(
                                    if (editing || inlineCardStackEditing) editedContentBlockOrder else reminder.contentBlockOrder,
                                    activeBlockKeys + setOf("card_stack")
                                ).filterNot { it == "card_stack" }.toMutableList()
                                nextOrder.add("card_stack")
                                editedContentBlockOrder = nextOrder
                                inlineCardStackKey = "card_stack"
                                inlineCardStackStartIndex = editedCardStack?.cards?.filter { it.stackGroupId == null }?.lastIndex ?: 0
                                inlineCardStackEditing = true
                                coroutineScope.launch {
                                    delay(80)
                                    try {
                                        cardStackBringIntoViewRequester.bringIntoView()
                                    } catch (_: Exception) {}
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Günlük ekle", color = Color(0xFFD3D3D9), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("Nasıl geçti? Aklında kalanı yaz.", color = Color(0xFF8E8E98), fontSize = 12.sp)
                        }
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
                        addJournalCardRow()
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
                        addJournalCardRow()
                    }
                }
            }

            DesignPanel(
                scene,
                { scenePosition },
                modifier = Modifier
                    .fillMaxWidth()
                    .realPetPerch(REAL_PET_PERCH_DETAIL_BOTTOM_BAR),
                corners = 28.dp,
                designStyle = designStyle,
                reduceDynamicBlur = reduceDynamicBlur,
                liquidDynamic = true,
                liquidDock = true
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 0.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (inlineCardStackEditing && !editing) {
                        DetailAction(
                            "Bitti",
                            ActionGlyph.Check,
                            Modifier.weight(1f),
                            enabled = !savingDetail,
                            color = Color(0xFF91B9FF),
                            reduceAnimations = reduceAnimations,
                            onClick = saveDraftAndClose
                        )
                    } else if (editing) {
                        DetailAction("Vazgeç", ActionGlyph.Close, Modifier.weight(1f), reduceAnimations = reduceAnimations, onClick = finishEditing)
                        DetailAction(
                            "Kaydet",
                            ActionGlyph.Check,
                            Modifier.weight(1f),
                            enabled = !attachmentBusy && !savingDetail && !voiceRecordingActive &&
                                (editedText.isNotBlank() || editedVoiceRecordings.isNotEmpty()) &&
                                (!journalEditing || editedJournalMood != null || editedJournalText.trim().isNotBlank()),
                            color = Color(0xFF91B9FF),
                            reduceAnimations = reduceAnimations,
                            onClick = saveDraftAndClose
                        )
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
                            loadReminderIntoDraft()
                            inlineCardStackEditing = false
                            editing = true
                        })
                        DetailAction("Share", ActionGlyph.Share, Modifier.weight(1f), enabled = !deleting, reduceAnimations = reduceAnimations, onClick = {
                            val stackCards = reminder.cardStack?.cards.orEmpty()
                            if (stackCards.size == 1) {
                                // Only one card: nothing to choose. Same ids the dialog would have passed on.
                                sharedStackIds = arrayListOf(stackCards.first().id)
                                showRealShareDialog = true
                            } else if (stackCards.isNotEmpty()) {
                                showCardStackShareDialog = true
                            } else {
                                sharedStackIds = null
                                showRealShareDialog = true
                            }
                        })
                        DetailAction("Sil", ActionGlyph.Delete, Modifier.weight(1f), reduceAnimations = reduceAnimations, onClick = { deleting = !deleting })
                    }
                }
            }
        }
        if (editing && showTitleTools) {
            com.cagri.real.ui.components.CardTitleTools(
                placement = editedContentBlockLayout[textToolsKey] ?: CardBlockPlacement(),
                onChange = { placement -> editedContentBlockLayout = editedContentBlockLayout + (textToolsKey to placement.normalized()) },
                onDismiss = { showTitleTools = false }
            )
        }
        if (showRealShareDialog) {
            val shareContent = remember(reminder, sharedStackIds) {
                val fileCount = reminder.attachments.count { it.type != com.cagri.real.data.AttachmentType.TEXT_SNIPPET } +
                    (sharedStackIds?.size ?: (reminder.cardStack?.cards?.size ?: 0))
                val voiceCount = reminder.voiceRecordings.size
                val hasLocation = reminder.location != null
                val hasJournal = reminder.journal != null || reminder.mood != null || reminder.moodDetails != null
                com.cagri.real.ui.share.ShareContent(
                    files = fileCount,
                    hasLocation = hasLocation,
                    voices = voiceCount,
                    hasJournal = hasJournal
                )
            }
            com.cagri.real.ui.share.CardShareDialog(
                cardId = reminder.id,
                selectedIds = sharedStackIds?.toSet(),
                fallbackText = sharedStackIds?.toSet()?.let { buildReminderShareText(reminder, it) } ?: buildReminderShareText(reminder),
                content = shareContent
            ) { showRealShareDialog = false }
        }
        if (showCardStackShareDialog) {
            CardStackShareDialog(
                cards = reminder.cardStack?.cards.orEmpty(),
                designStyle = designStyle,
                backdrop = scene,
                backdropPosition = { scenePosition },
                onDismiss = { showCardStackShareDialog = false },
                onShare = { selectedIds ->
                    showCardStackShareDialog = false
                    sharedStackIds = ArrayList(selectedIds)
                    showRealShareDialog = true
                }
            )
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

        AnimatedDateTimePickerOverlay(
            visible = showDetailEndPicker,
            initial = (reminder.alertAt ?: System.currentTimeMillis()) + (detailDurationMinutes ?: 60) * 60_000L,
            initialRepeatRule = RecurrenceRule.NONE,
            initialTimeTab = true,
            onDismiss = { showDetailEndPicker = false },
            backdrop = scene,
            backdropPosition = { scenePosition },
            designStyle = designStyle,
            reduceDynamicBlur = reduceDynamicBlur,
            reduceAnimations = reduceAnimations,
            onConfirm = { at, _ ->
                val base = reminder.alertAt ?: System.currentTimeMillis()
                val diff = maxOf(1, ((at - base) / 60_000L).toInt())
                detailDurationMinutes = diff
                val repo = plannerRepo
                val id = reminder.id
                val prev = plannerDetails
                coroutineScope.launch(Dispatchers.IO) {
                    repo.put(id, (prev ?: com.cagri.real.ui.components.planner.PlannerEventDetails()).copy(durationMinutes = diff))
                }
                showDetailEndPicker = false
            },
            exactAlarmsAllowed = exactAlarmsAllowed,
            onRequestExactAlarms = onRequestExactAlarms
        )
        if (showCamera) com.cagri.real.ui.components.CardCameraSheet(
            reduceAnimations = reduceAnimations,
            onDismiss = { showCamera = false },
            onUsePhoto = { file ->
                importPicked(emptyList(), 0, photosOnly = true, cameraFile = file)
                showCamera = false
            }
        )
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
        AnimatedVisibility(
            visible = showMoodFullscreen,
            enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(220, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))) + scaleIn(RealMotion.snappy(0.32f), initialScale = 0.96f),
            exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(160, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))) + scaleOut(tween(160), targetScale = 0.96f),
            modifier = Modifier.fillMaxSize().zIndex(100f)
        ) {
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

        previewAttachmentPath?.let { path ->
            Box(Modifier.fillMaxSize().zIndex(100f)) {
                ReminderPhotoPreview(path, { previewAttachmentPath = null }, statusHeight, allowZoom = true)
            }
        }
    }
}


private fun resolveBlockOrder(savedOrder: List<String>?, activeKeys: Set<String>): List<String> =
    resolveCardContentOrder(savedOrder, activeKeys)

private data class ContentBlockDescriptor(
    val key: String,
    val title: String,
    val subtitle: String,
    val glyph: ActionGlyph
)

@Composable
private fun ContentBlockReorderRow(
    desc: ContentBlockDescriptor,
    isDragging: Boolean,
    modifier: Modifier = Modifier
) {
    val rowBackground = if (isDragging) Color(0xFF282A33) else Color(0xFF141416)
    val rowBorderColor = if (isDragging) Color(0xFF91B9FF).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.10f)

    Row(
        modifier = modifier
            .graphicsLayer {
                if (isDragging) {
                    scaleX = 1.02f
                    scaleY = 1.02f
                    shadowElevation = 10.dp.toPx()
                }
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
            Text(desc.subtitle, color = Color(0xFF9797A2), style = RealType.detailBlockMetaSecondary, maxLines = 1)
        }
        ActionIcon(
            ActionGlyph.DragHandle,
            Modifier.size(20.dp),
            color = if (isDragging) Color(0xFF91B9FF) else Color(0xFF75757F)
        )
    }
}


private fun Modifier.isolateFloatingPanelGestures(): Modifier = this.pointerInput(Unit) {
    // Participate in hit-testing without consuming child controls' gestures.
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
            .heightIn(min = 56.dp).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
    ) {
        ActionIcon(glyph, Modifier.size(20.dp), tint)
        Text(label, fontSize = 10.sp, lineHeight = 12.sp, color = tint, maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
    }
}

private const val COMPLETED_JOURNAL_CARD_ID_PREFIX = "completed_journal_"

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(name = "Cam detay", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun DetailPreview() {
    RealTheme {
        ReminderDetailScreen(ReminderItem(1, "Akşam için birkaç küçük not", 1789610400000), {}, {}, {}, { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> true }, {})
    }
}

fun shouldShowNoAlertInDetail(reminder: ReminderItem): Boolean =
    !reminder.isCompleted && reminder.alertAt == null && reminder.plannedDateEpochDay == null &&
        !reminder.isQuick24HourActive() && reminder.customCategoryId == null

fun isStandaloneCategoryInDetail(reminder: ReminderItem): Boolean =
    !reminder.isCompleted && reminder.alertAt == null && reminder.plannedDateEpochDay == null &&
        !reminder.isQuick24HourActive() && reminder.customCategoryId != null

fun reminderDetailStatusText(
    reminder: ReminderItem,
    hasCategory: Boolean = (reminder.customCategoryId != null)
): String? = when {
    reminder.isCompleted -> "Tamamlandı"
    reminder.isQuick24HourActive() -> "24 hour"
    reminder.alertAt == null && reminder.plannedDateEpochDay == null -> if (!hasCategory) "Uyarı yok" else null
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
