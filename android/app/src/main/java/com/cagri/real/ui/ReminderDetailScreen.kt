package com.cagri.real.ui

import com.cagri.real.i18n.I18n
import com.cagri.real.i18n.t
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.SideEffect
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.ui.semantics.stateDescription
import com.cagri.real.data.CARD_GRID_UNITS
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.widthIn
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
import com.cagri.real.ui.components.MoodV3FullScreen
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
    /** Oyun bloğu ✕ (saved right away; game blocks are not part of the edit draft). */
    onRemoveGameBlock: (String) -> Unit = {},
    /** Oyun search → a game block on this card (saved right away). */
    onAddGameBlock: (com.cagri.real.data.CardGameBlock) -> Unit = {},
    pullDismiss: DetailPullDismiss? = null
) {
    var editing by rememberSaveable(reminder.id) { mutableStateOf(false) }
    var editedText by rememberSaveable(reminder.id) { mutableStateOf(reminder.text) }
    var accentStart by rememberSaveable(reminder.id) { mutableStateOf<Int?>(reminder.accentStart) }
    var accentEnd by rememberSaveable(reminder.id) { mutableStateOf<Int?>(reminder.accentEnd) }
    var customSolidColorArgb by rememberSaveable(reminder.id) { mutableStateOf(reminder.customSolidColorArgb) }
    var useDarkText by rememberSaveable(reminder.id) { mutableStateOf(reminder.useDarkText) }
    LaunchedEffect(accentStart, accentEnd, customSolidColorArgb) {
        useDarkText = com.cagri.real.ui.components.autoCardDarkText(accentStart, accentEnd, customSolidColorArgb)
    }
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
    /** Attachment id of the counter / market block whose settings dialog is open. */
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
    val latestReminderForWidgets by rememberUpdatedState(reminder)
    var checklistBlockOpen by remember(reminder.id, editing) { mutableStateOf(reminder.checklistItems.isNotEmpty()) }
    val firstItemFocusRequester = remember { FocusRequester() }
    var shouldFocusFirstItem by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    var rootBoxCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var bottomPanelCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
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
    // Kaydet's one-shot mic sweep through the bottom dock (0..1) and when it started, so the normal dock's buttons
    // can appear as the band passes over them.
    val saveSweep = remember(reminder.id) { Animatable(0f) }
    var saveSweepStartedAt by remember(reminder.id) { mutableLongStateOf(0L) }
    var musicSearchOpen by remember(reminder.id) { mutableStateOf(false) }
    var mediaSearchOpen by remember(reminder.id) { mutableStateOf(false) }
    var gameSearchOpen by remember(reminder.id) { mutableStateOf(false) }
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
    // Adding anything to the card (text, photo, location, checklist item, music…) lowers the edit dock
    // and closes "Renk ve kategori", so the new block is visible instead of hidden behind the panels.
    val addedContentCount = editedContentBlockOrder.size + (if (editedLocation != null) 1 else 0) +
        editedChecklistItems.size + editedAttachments.size + editedMusicBlocks.size + editedMediaBlocks.size +
        editedVoiceRecordings.size + (editedCardStack?.cards?.size ?: 0)
    var lastAddedContentCount by remember(reminder.id, editing) { mutableIntStateOf(addedContentCount) }
    // Opening the editor loads the card's content into the draft; that is not "adding", so the dock opens raised.
    var editSettled by remember(reminder.id, editing) { mutableStateOf(false) }
    val latestAddedContentCount by rememberUpdatedState(addedContentCount)
    LaunchedEffect(reminder.id, editing) {
        if (editing) {
            delay(700)
            lastAddedContentCount = latestAddedContentCount
            editSettled = true
        }
    }
    LaunchedEffect(addedContentCount) {
        if (editing && editSettled && addedContentCount > lastAddedContentCount) {
            secondaryPanelsExpanded = false
            editDockCollapsed = true
        }
        lastAddedContentCount = addedContentCount
    }
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
    // Kontrol ettir: opening the card puts its light out for this viewer.
    val checkContext = LocalContext.current
    LaunchedEffect(reminder.id, reminder.checkRequest?.at) { com.cagri.real.ui.components.CheckRequests.markSeen(checkContext, reminder) }
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
    val setReminderTiming = com.cagri.real.ui.components.LocalSetReminderTiming.current
    var showLocationGuide by rememberSaveable(reminder.id) { mutableStateOf(false) }
    var locationJustShared by remember(reminder.id) { mutableStateOf(false) }
    LaunchedEffect(reminder.id, reminder.location?.mapsUrl, reminder.location?.latitude) {
        if (reminder.location?.mapsUrl != null && reminder.location.latitude == null) onResolveLocationPreview()
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
    // Yapıştır (in Now Bar's place while Now Bar is off): what is on the clipboard, by type only.
    var pasteKind by remember { mutableStateOf(ClipboardPaste.peek(context)) }
    // Cam hap: only for something copied in the last 30 s, and only once per copy.
    var consumedClip by rememberSaveable { mutableLongStateOf(0L) }
    var freshClip by remember { mutableStateOf(ClipboardPaste.fresh(context)?.takeIf { it.second != consumedClip }) }
    fun refreshClipboard() {
        pasteKind = ClipboardPaste.peek(context)
        freshClip = ClipboardPaste.fresh(context)?.takeIf { it.second != consumedClip }
    }
    LaunchedEffect(freshClip) {
        val clip = freshClip ?: return@LaunchedEffect
        delay((clip.second + ClipboardPaste.FRESH_WINDOW_MS - System.currentTimeMillis()).coerceAtLeast(0L))
        freshClip = null
    }
    val pasteLifecycle = LocalLifecycleOwner.current
    DisposableEffect(pasteLifecycle) {
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        val listener = android.content.ClipboardManager.OnPrimaryClipChangedListener { refreshClipboard() }
        clipboard?.addPrimaryClipChangedListener(listener)
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refreshClipboard()
        }
        pasteLifecycle.lifecycle.addObserver(observer)
        onDispose { clipboard?.removePrimaryClipChangedListener(listener); pasteLifecycle.lifecycle.removeObserver(observer) }
    }
    var pasting by remember { mutableStateOf(false) }
    // Long press on Yapıştır: "Panodan ekle", items taken from the keyboard's own clipboard.
    var clipboardTrayOpen by remember { mutableStateOf(false) }
    suspend fun trayPasted(item: com.cagri.real.ui.components.TrayItem): ClipboardPaste.Pasted? = when (item) {
        is com.cagri.real.ui.components.TrayItem.Text -> ClipboardPaste.fromText(item.text)
        is com.cagri.real.ui.components.TrayItem.Image -> ClipboardPaste.fromUri(context, item.uri)
    }
    /**
     * Adds [source] (default: the clipboard) to the card: into the draft while editing, otherwise saved right away.
     * [afterwards] runs once it is in the card (the tray opens "Sırala" to place it in between).
     */
    fun pasteIntoCard(source: (suspend () -> ClipboardPaste.Pasted?)? = null, afterwards: () -> Unit = {}) {
        if (pasting) return
        pasting = true
        coroutineScope.launch {
            try {
                val pasted = source?.invoke() ?: if (source == null) ClipboardPaste.read(context) else null
                if (pasted == null || (pasted.content.isEmpty && pasted.location == null)) {
                    com.cagri.real.ui.components.RealToast.show(context, t("Yapıştırılacak bir şey bulunamadı"))
                    return@launch
                }
                val c = pasted.content
                fun mergedAttachments(current: List<ReminderAttachment>, currentUrl: String?): Pair<List<ReminderAttachment>, String?> {
                    var added = ReminderRepository.limitPhotos(c.attachments, current.count { it.type == AttachmentType.IMAGE })
                    var url = currentUrl
                    c.referenceUrl?.let { link ->
                        // A card has one link; a second one is kept as a text block instead of replacing it.
                        if (url == null || url == link) url = link
                        else added = added + ReminderAttachment(java.util.UUID.randomUUID().toString(),
                            type = AttachmentType.TEXT_SNIPPET, mimeType = "text/plain", text = link)
                    }
                    return (current + added) to url
                }
                if (editing) {
                    val (attachments, url) = mergedAttachments(editedAttachments, editedReferenceUrl)
                    editedAttachments = attachments
                    editedReferenceUrl = url
                    editedMusicBlocks = editedMusicBlocks + c.musicBlocks
                    editedVoiceRecordings = editedVoiceRecordings + c.voiceRecordings
                    if (pasted.location != null && editedLocation == null) editedLocation = pasted.location
                } else {
                    androidx.compose.runtime.snapshotFlow { savingDetail }.first { !it }
                    val r = latestReminderForWidgets
                    val (attachments, url) = mergedAttachments(r.attachments, r.referenceUrl)
                    val location = r.location ?: pasted.location
                    val updated = r.copy(attachments = attachments, referenceUrl = url, musicBlocks = r.musicBlocks + c.musicBlocks,
                        voiceRecordings = r.voiceRecordings + c.voiceRecordings, location = location)
                    savingDetail = true
                    try {
                        onSave(r.text, r.accentStart, r.accentEnd, r.customCategoryId, r.checklistItems, r.starBorderColorIndex,
                            r.gradientBlendFraction, r.customSolidColorArgb, updated.attachments, updated.voiceRecordings,
                            updated.location, updated.referenceUrl, updated.musicBlocks, r.mediaBlocks, r.cardStack,
                            r.contentBlockOrder, cardLayoutForKeys(r.contentBlockLayout, updated.cardContentKeys()), r.useDarkText)
                    } finally { savingDetail = false }
                }
                com.cagri.real.ui.components.RealToast.show(context, t("Yapıştırıldı · {0}", pasted.label))
                afterwards()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { com.cagri.real.ui.components.RealToast.show(context, t("Yapıştırılamadı")) }
            finally { pasting = false }
        }
    }
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
        if (uris.size > 20) { attachmentError = t("Bir seferde en fazla 20 dosya seçebilirsin"); return }
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
                        if (failed > 0) attachmentError = t("{0} ek eklenemedi. Fotoğraf sınırı: 3; kopyalama sınırı: 100 MB.", failed)
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
            attachmentError = t("REAL metni boş bırakılamaz.")
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
                attachmentError = t("Değişiklikler kaydedilemedi. Yeniden dene.")
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
                if (EnterExitState.PreEnter isTransitioningTo EnterExitState.Visible) {
                    tween(200, delayMillis = 80, easing = RealMotion.Standard)
                } else {
                    tween(120, easing = RealMotion.Exit)
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
                com.cagri.real.ui.components.RealBackButton(
                    onBack = {
                        when {
                            editing -> finishEditing()
                            inlineCardStackEditing -> saveDraftAndClose()
                            else -> onBack()
                        }
                    },
                    description = when {
                        editing -> t("Düzenlemeyi iptal et")
                        inlineCardStackEditing -> t("Yazıyı kaydet")
                        else -> t("Geri")
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
                    // Small and quiet: white when the calendar link is current, dim when the content is only in REAL.
                    // A tap spins the arrows once.
                    val syncColor = if (calendarSyncPublished) Color.White else Color.White.copy(alpha = 0.45f)
                    val syncSpin = remember { Animatable(0f) }
                    val syncScope = rememberCoroutineScope()
                    Row(
                        Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable {
                                if (!reduceAnimations) syncScope.launch { syncSpin.snapTo(0f); syncSpin.animateTo(360f, tween(650, easing = RealMotion.Emphasized)) }
                                onCalendarSyncClick()
                            }
                            .padding(horizontal = 10.dp)
                            .semantics { contentDescription = if (calendarSyncPublished)
                                t("Takvim web bağlantısı güncel") else t("İçerik yalnız REAL'de veya web bağlantısı güncel değil") },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ActionIcon(ActionGlyph.Repeat, Modifier.size(11.dp).graphicsLayer { rotationZ = syncSpin.value }, syncColor)
                        Text(
                            "SYNC", fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 2.2.sp,
                            fontWeight = FontWeight.SemiBold, color = syncColor, maxLines = 1
                        )
                    }
                }
                AnimatedVisibility(
                    visible = freshClip != null && !pasting,
                    enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(200)) + scaleIn(tween(220), initialScale = 0.85f),
                    exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.85f)
                ) {
                    val shownKind = freshClip?.first ?: pasteKind
                    DesignPanel(
                        scene, { scenePosition }, Modifier.height(40.dp),
                        corners = 20.dp, designStyle = designStyle, reduceDynamicBlur = reduceDynamicBlur,
                        liquidDynamic = true, liquidDock = true
                    ) {
                        Row(
                            Modifier.fillMaxHeight().clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    freshClip?.let { consumedClip = it.second }
                                    freshClip = null
                                    pasteIntoCard()
                                }
                                .padding(horizontal = 14.dp)
                                .semantics { contentDescription = t("Yapıştır: {0}", shownKind?.label.orEmpty()) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            ActionIcon(ActionGlyph.Paste, Modifier.size(16.dp), Color(0xFFE4E4EA))
                            Text(shownKind?.label.orEmpty(), color = Color(0xFFF2F2F5), fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
                val starSettleScale = remember(reminder.id) { Animatable(1f) }
                var hasRenderedInitialStar by remember(reminder.id) { mutableStateOf(false) }
                LaunchedEffect(reminder.isImportant) {
                    if (!hasRenderedInitialStar) {
                        hasRenderedInitialStar = true
                        return@LaunchedEffect
                    }
                    if (reminder.isImportant && !reduceAnimations) {
                        starSettleScale.snapTo(1.06f)
                        starSettleScale.animateTo(1.0f, tween(160, easing = RealMotion.Emphasized))
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
                        if (!nowBarEnabled) Box(
                            Modifier
                                .size(48.dp)
                                .pressScale(nowBarInteractionSource, enabled = !pasting, reduceAnimations = reduceAnimations, targetScale = 0.95f)
                                .clip(CircleShape)
                                .combinedClickable(
                                    interactionSource = nowBarInteractionSource,
                                    indication = androidx.compose.material3.ripple(bounded = false),
                                    enabled = !pasting,
                                    role = Role.Button,
                                    onClickLabel = t("Yapıştır"),
                                    onLongClickLabel = t("Panodan ekle"),
                                    onLongClick = { clipboardTrayOpen = true },
                                    // Always lit: an empty clipboard opens "Panodan ekle" instead.
                                    onClick = {
                                        pasteKind = ClipboardPaste.peek(context)
                                        if (pasteKind != null) pasteIntoCard() else clipboardTrayOpen = true
                                    }
                                )
                                .semantics {
                                    contentDescription = pasteKind?.let { t("Yapıştır: {0}", it.label) } ?: t("Yapıştır")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (pasting) com.cagri.real.ui.components.RealSpinner(Modifier.size(18.dp), color = Color.White, strokeWidth = 1.5.dp)
                            else ActionIcon(ActionGlyph.Paste, Modifier.size(20.dp), Color(0xFFC9CBD3))
                            com.cagri.real.ui.components.ClipboardTray(
                                visible = clipboardTrayOpen,
                                busy = pasting,
                                reduceAnimations = reduceAnimations,
                                onAppend = { item -> pasteIntoCard(source = { trayPasted(item) }) },
                                onDismiss = { clipboardTrayOpen = false }
                            )
                        } else IconButton(
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
                                        reminder.isCompleted -> t("Tamamlanan hatırlatıcı Now Bar'a sabitlenemez")
                                        !nowBarEnabled -> t("Now Bar ayarlardan kapalı")
                                        nowBarPinned -> t("Now Bar sabitlemesini kaldır")
                                        else -> t("Now Bar'a sabitle")
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
                                .semantics { contentDescription = if (reminder.isImportant) t("Önemli işaretini kaldır") else t("Önemli olarak işaretle") }
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

    // Set while the full-bleed hero is on screen; the top glass strip then fades in only as the page scrolls.
    var detailHeroActive by remember(reminder.id) { mutableStateOf(false) }

    val headerSpacerHeight by animateDpAsState(
        targetValue = if (editing) 0.dp else 48.dp,
        animationSpec = if (reduceAnimations) snap() else tween(180, easing = RealMotion.Emphasized),
        label = "detailHeaderSpacerHeight"
    )

    val detailBottomPadding by animateDpAsState(
        targetValue = if (editing) {
            if (editDockCollapsed) 132.dp else if (secondaryPanelsExpanded) 440.dp else 290.dp
        } else if (inlineCardStackEditing) 180.dp else 114.dp,
        animationSpec = if (reduceAnimations) snap() else tween(220, easing = RealMotion.Emphasized),
        label = "detailDockContentInset"
    )

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
        // Hava durumu: a block like the others (Sırala, size), but only while it has a forecast to show.
        var weatherHidden by remember(reminder.id) { mutableStateOf(false) }
        val weatherShown = remember(reminder.id, reminder.alertAt, reminder.plannedDateEpochDay, weatherHidden) {
            !weatherHidden && com.cagri.real.ui.components.CardWeatherRepository.mayShow(context, reminder)
        }
        val activeBlockKeys = remember(
            weatherShown,
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
            ).let { if (weatherShown) it else it - "weather" }
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
                    title = t("Not Metni"),
                    subtitle = if (editing) editedText.ifBlank { t("Boş not") } else reminder.text.ifBlank { t("Boş not") },
                    glyph = ActionGlyph.Note
                ),
                "card_stack" to ContentBlockDescriptor(
                    key = "card_stack",
                    title = t("Kart Yığını"),
                    subtitle = t("{0} kart", (if (editing || inlineCardStackEditing) editedCardStack else reminder.cardStack)?.cards?.size ?: 0),
                    glyph = ActionGlyph.Cards
                ),
                "checklist" to ContentBlockDescriptor(
                    key = "checklist",
                    title = t("Checklist"),
                    subtitle = t("{0} madde", (if (editing) editedChecklistItems else reminder.checklistItems).size),
                    glyph = ActionGlyph.Checklist
                ),
                "photos" to ContentBlockDescriptor(
                    key = "photos",
                    title = t("Fotoğraflar"),
                    subtitle = t("{0} fotoğraf", (if (editing) editedAttachments else reminder.attachments).count { it.type == AttachmentType.IMAGE }),
                    glyph = ActionGlyph.Photo
                ),
                "files" to ContentBlockDescriptor(
                    key = "files",
                    title = t("Dosyalar & Bağlantılar"),
                    subtitle = run {
                        val fCount = (if (editing) editedAttachments else reminder.attachments).count { it.type != AttachmentType.IMAGE }
                        val hasUrl = (if (editing) editedReferenceUrl else reminder.referenceUrl) != null
                        if (fCount > 0 && hasUrl) t("{0} dosya • 1 bağlantı", fCount) else if (fCount > 0) t("{0} dosya", fCount) else t("1 bağlantı")
                    },
                    glyph = ActionGlyph.File
                ),
                "voice" to ContentBlockDescriptor(
                    key = "voice",
                    title = t("Ses Kaydı"),
                    subtitle = t("{0} kayıt", (if (editing) editedVoiceRecordings else reminder.voiceRecordings).size),
                    glyph = ActionGlyph.AudioWave
                ),
                "music" to ContentBlockDescriptor(
                    key = "music",
                    title = t("Müzik"),
                    subtitle = t("{0} parça", (if (editing) editedMusicBlocks else reminder.musicBlocks).size),
                    glyph = ActionGlyph.Music
                ),
                "media" to ContentBlockDescriptor(
                    key = "media",
                    title = t("Film & Dizi"),
                    subtitle = t("{0} içerik", (if (editing) editedMediaBlocks else reminder.mediaBlocks).size),
                    glyph = ActionGlyph.Video
                ),
                "game" to ContentBlockDescriptor(
                    key = "game",
                    title = t("Oyun"),
                    subtitle = t("{0} oyun", reminder.gameBlocks.size),
                    glyph = ActionGlyph.Play
                ),
                "location" to ContentBlockDescriptor(
                    key = "location",
                    title = t("Konum"),
                    subtitle = (if (editing) editedLocation else reminder.location)?.placeName?.ifBlank { t("Konum") } ?: t("Konum"),
                    glyph = ActionGlyph.Pin
                ),
                "weather" to ContentBlockDescriptor(
                    key = "weather",
                    title = t("Hava durumu"),
                    subtitle = t("Apple Weather"),
                    glyph = ActionGlyph.Calendar
                ),
                "mood" to ContentBlockDescriptor(
                    key = "mood",
                    title = t("Mood"),
                    subtitle = reminder.mood?.label ?: t("Mood"),
                    glyph = ActionGlyph.MoodVeryGood
                ),
                "journal" to ContentBlockDescriptor(
                    key = "journal",
                    title = t("Günlük"),
                    subtitle = t("Günlük notu"),
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
                    "game" -> section.copy(key = key, title = reminder.gameBlocks.firstOrNull { it.id == id }?.title ?: t("Oyun"), subtitle = t("Oyun"))
                    "photos" -> section.copy(key = key, title = t("Fotoğraf"), subtitle = attachment?.displayName.orEmpty())
                    "files" -> section.copy(key = key, title = if (key == "files:reference") t("Bağlantı") else attachment?.displayName ?: section.title)
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
                        Text(t("İçerik Sıralaması"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

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

                // "En üstteki içeriği büyüt": the top block becomes a full-width hero, like a cover photo.
                // Stored with the existing layout fields (span 6 + tall minHeight), so no new sync field.
                fun isHero(key: String?) = key != null && editedContentBlockLayout[key]?.let {
                    it.span == CARD_GRID_UNITS && it.minHeight >= DETAIL_HERO_MIN_HEIGHT_DP
                } == true
                fun withHero(layout: Map<String, CardBlockPlacement>, key: String, on: Boolean): Map<String, CardBlockPlacement> {
                    val p = (layout[key] ?: CardBlockPlacement()).normalized()
                    return layout + (key to if (on) p.copy(span = CARD_GRID_UNITS, column = 0,
                        minHeight = maxOf(p.minHeight, DETAIL_HERO_MIN_HEIGHT_DP)).normalized() else p.copy(minHeight = 0).normalized())
                }
                val topKey = reorderKeys.firstOrNull()
                var heroOwner by remember { mutableStateOf(reorderKeys.firstOrNull { isHero(it) }) }
                // The hero belongs to whatever is on top: moving another block up hands the size over to it.
                LaunchedEffect(topKey) {
                    val owner = heroOwner
                    if (owner != null && topKey != null && owner != topKey) {
                        editedContentBlockLayout = withHero(withHero(editedContentBlockLayout, owner, false), topKey, true)
                        heroOwner = topKey
                    }
                }
                val heroOn = isHero(topKey)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = topKey != null, role = Role.Checkbox) {
                            val key = topKey ?: return@clickable
                            editedContentBlockLayout = withHero(editedContentBlockLayout, key, !heroOn)
                            heroOwner = if (heroOn) null else key
                        }
                        .semantics { stateDescription = if (heroOn) t("Açık") else t("Kapalı") }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val tick by animateFloatAsState(if (heroOn) 1f else 0f, if (reduceAnimations) snap() else RealMotion.snappy(), label = "heroTick")
                    Box(
                        Modifier.size(22.dp).clip(CircleShape)
                            .background(Color(0xFF91B9FF).copy(alpha = tick))
                            .border(1.5.dp, lerp(Color(0xFF6E6E78), Color(0xFF91B9FF), tick), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        ActionIcon(ActionGlyph.Check, Modifier.size(13.dp).graphicsLayer { alpha = tick; scaleX = 0.6f + 0.4f * tick; scaleY = 0.6f + 0.4f * tick }, Color(0xFF0B0B0D))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(t("En üstteki içeriği büyüt"), color = Color(0xFFE8E8EE), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(t("Listenin başındaki içerik kartın tepesinde tam genişlikte, büyük görünür."),
                            color = Color(0xFF8E8E98), fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))

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
                // "En üstteki içeriği büyüt" while reading (Liquid Glass): the top block leaves the grid and sits at
                // the very top, right under the status bar with the usual side margins, the back / star buttons
                // floating over it like a cover (same place it has while editing).
                val heroCandidate = currentOrder.firstOrNull()
                val heroKey = heroCandidate?.takeIf {
                    designStyle == DesignStyle.LiquidGlass && !editing && !inlineCardStackEditing &&
                        reminder.contentBlockLayout[it]?.let { p -> p.span == CARD_GRID_UNITS && p.minHeight >= DETAIL_HERO_MIN_HEIGHT_DP } == true
                }
                SideEffect { detailHeroActive = heroKey != null }
                if (designStyle == DesignStyle.LiquidGlass) {
                    if (heroKey == null && headerSpacerHeight > 0.dp) Spacer(Modifier.height(headerSpacerHeight))
                } else detailHeader()

                val cardShape = RoundedCornerShape(28.dp)
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
                            boundsTransform = { _, _ -> tween(320, easing = RealMotion.Standard) },
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

                val visibleBlocks = if (heroKey != null) currentOrder.drop(1) else currentOrder
                val baseDisplayedLayout = cardDetailReadablePlacements(cardLayoutForKeys(
                    if (editing || inlineCardStackEditing) editedContentBlockLayout else reminder.contentBlockLayout,
                    activeBlockKeys
                ), detailWidthDp)
                val displayedLayout = if (heroKey == null) baseDisplayedLayout else {
                    // Blocks placed freely below the hero kept tops that counted the hero's slot in the grid.
                    // With the hero drawn above the grid, lift them by that slot so no empty band opens up.
                    val hero = (baseDisplayedLayout[heroKey] ?: CardBlockPlacement()).normalized()
                    val heroSlot = (hero.top ?: 0) + hero.minHeight + hero.gapBefore
                    baseDisplayedLayout.mapValues { (key, p) ->
                        when {
                            key == heroKey -> p.copy(span = CARD_GRID_UNITS, column = 0)
                            p.top != null -> p.copy(top = (p.top - heroSlot).coerceAtLeast(0))
                            else -> p
                        }
                    }
                }
                if (heroKey == null) Spacer(Modifier.height(12.dp))
                // Opening from a list, the card always grows into the TOP of the detail: the cover when there is one,
                // otherwise the highest block (the title may have been moved further down in "Sırala").
                val sharedTopKey = heroKey ?: visibleBlocks.withIndex()
                    .minByOrNull { (index, key) -> displayedLayout[key]?.top ?: (index * 100_000) }?.value ?: "text"

                // Block renderer shared by the grid and the full-bleed hero at the top of the detail.
                val detailBlockContent: @Composable (String, Int) -> Unit = { blockKey, blockIndex ->
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
                            // As the cover the text sits centred in the block, below the status bar.
                            val heroInset = LocalDetailHeroInset.current
                            val titleAlignment = if (heroInset > 0.dp) TextAlign.Center else when (titlePlacement.textAlignment) {
                                "center" -> TextAlign.Center
                                "end" -> TextAlign.End
                                else -> TextAlign.Start
                            }
                            Box(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier
                                    .then(if (sharedTopKey == "text") sharedCardModifier else Modifier)
                                    .fillMaxWidth()
                                    .then(if (titleHeight > 0) Modifier.height(titleHeight.dp + heroInset) else Modifier)
                                    .cardBlockSurface()
                                    .clip(cardShape)
                                    .then(
                                        if (accentBrush == null) Modifier.background(
                                            if (designStyle == DesignStyle.LiquidGlass) Brush.verticalGradient(listOf(Color(0xFF18181A), LiquidGlassTokens.contentColor))
                                            else Brush.linearGradient(listOf(Color(0xFF25252C), Color(0xFF17171B), Color(0xFF131315)))
                                        ) else Modifier.background(accentBrush)
                                    )
                                    .then(cardBorderModifier)
                                    .padding(start = 22.dp, end = 22.dp, top = titlePadding + heroInset, bottom = titlePadding),
                                verticalArrangement = if (heroInset > 0.dp) Arrangement.Center else Arrangement.Top
                            ) {
                                val shownUseDarkText = if (editing) useDarkText else reminder.useDarkText
                                if (editing) {
                                    BasicTextField(
                                        value = editedText, onValueChange = { editedText = it },
                                        maxLines = titleLines,
                                        modifier = Modifier.fillMaxWidth()
                                            .heightIn(min = if (titleHeight > 0) 0.dp else 96.dp)
                                            .onFocusChanged { if (it.isFocused) secondaryPanelsExpanded = false }
                                            .semantics { contentDescription = t("Hatırlatıcı metni") },
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
                                                            // Only what the card has: no duration / end = no row (never "null").
                                                            val shownEnd = reminder.endAt?.let { com.cagri.real.ui.components.planner.formatTimeAmPm(it, ZoneId.systemDefault()) } ?: endTimeStr
                                                            if (dur != null || shownEnd != null) Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                modifier = Modifier.padding(top = 2.dp)
                                                            ) {
                                                                if (dur != null) Text(t("Süre: {0} dk", dur), color = Color(0xFFA5A5B0), style = RealType.detailBlockMetaSecondary)
                                                                if (dur != null && shownEnd != null) Text("•", color = Color(0xFF6B6B75), style = RealType.detailBlockMetaSecondary)
                                                                if (shownEnd != null) Text(t("Bitiş: {0}", shownEnd), color = Color(0xFFA5A5B0), style = RealType.detailBlockMetaSecondary)
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
                                                // Varınca / Ayrılınca: the place and how close counts, under the time (same arrow as on Home).
                                                reminder.locationTrigger?.takeIf { !reminder.isCompleted }?.let { trigger ->
                                                    val tone = if (trigger.onExit) Color(0xFFFFB86F) else Color(0xFF5DCAA5)
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        com.cagri.real.ui.components.PlaceTriggerGlyph(trigger.onExit, tone, Modifier.size(12.dp))
                                                        Text(
                                                            (if (trigger.onExit) t("Ayrılınca · ") else t("Varınca · ")) + trigger.placeName +
                                                                (if (trigger.onExit) t(" · {0} m dışına çıkınca", trigger.radiusMeters) else t(" · {0} m içinde", trigger.radiusMeters)),
                                                            color = tone, style = RealType.detailBlockMetaSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
                                                        )
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
                                        Text(details?.scope?.label ?: t("Mood"), color = moodTint, fontSize = 12.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(moodV2FamilyTitle(mood), color = Color(0xFFF7F7F9), fontSize = 21.sp,
                                            lineHeight = 26.sp, fontWeight = FontWeight.SemiBold,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (!details?.descriptors.isNullOrEmpty()) Text(
                                            details.descriptors.take(3).joinToString(" · ") { moodDescriptorLabelV2(it) },
                                            color = Color(0xFFC9C9D0), fontSize = 13.sp, lineHeight = 18.sp,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (!details?.factors.isNullOrEmpty()) Text(
                                            details.factors.take(4).joinToString(" · ") { moodFactorLabelV2(it) },
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
                            ReminderContentSections(
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
                        "game" -> {
                            com.cagri.real.ui.components.GameContentSection(
                                blocks = reminder.gameBlocks.filter { it.id == blockKey.substringAfter(':') },
                                display = com.cagri.real.data.catalogCardDisplay(displayedLayout[blockKey]?.span ?: com.cagri.real.data.CARD_GRID_UNITS),
                                showActions = displayedLayout[blockKey]?.hideActions != true,
                                designStyle = designStyle, editing = editing && !savingDetail,
                                onRemove = { id -> onRemoveGameBlock(id) }
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
                                // As the card's cover the stack sits below the status bar on a full-bleed dark ground.
                                val stackHeroInset = LocalDetailHeroInset.current
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (stackHeroInset > 0.dp) Modifier
                                            .height(LocalCardBlockHeight.current)
                                            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                                            .background(Brush.verticalGradient(listOf(Color(0xFF16161A), Color(0xFF0D0D0F))))
                                            .padding(start = 16.dp, end = 16.dp, top = stackHeroInset + 8.dp, bottom = 16.dp) else Modifier)
                                        .bringIntoViewRequester(stackBringRequester)
                                        .then(if (blockKey == inlineCardStackKey) Modifier.bringIntoViewRequester(cardStackBringIntoViewRequester) else Modifier)
                                ) {
                                    CardStackContentSection(
                                        block = stack,
                                        heightOverride = if (stackHeroInset > 0.dp) (LocalCardBlockHeight.current - stackHeroInset - 24.dp)
                                            else displayedLayout[blockKey]?.minHeight?.takeIf { it > 0 }?.dp,
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
                        "weather" -> {
                            val slot = displayedLayout[blockKey]?.minHeight?.takeIf { it > 0 }
                            com.cagri.real.ui.components.CardWeatherBlock(
                                reminder, reduceAnimations,
                                if (slot != null) Modifier.height(slot.dp) else Modifier,
                                compact = narrowBlock || (slot != null && slot < 150),
                                interactive = !editing,
                                onHide = { weatherHidden = true }
                            )
                        }
                        "location" -> {
                            val place = if (editing) editedLocation else reminder.location
                            if (place != null) {
                                val locCardShape = RoundedCornerShape(22.dp)
                                if (place.latitude != null && place.longitude != null) {
                                    // Yol: within an hour of the card's time, the driving route from the phone (taller map).
                                    var route by remember(reminder.id, place.latitude, place.longitude) {
                                        mutableStateOf<com.cagri.real.notifications.DepartureReminder.Route?>(null)
                                    }
                                    LaunchedEffect(reminder.id, reminder.alertAt, place.latitude, place.longitude, editing) {
                                        val left = (reminder.alertAt ?: 0L) - System.currentTimeMillis()
                                        route = if (!editing && !reminder.isCompleted && left in 0L..60 * 60_000L)
                                            com.cagri.real.notifications.DepartureReminder.route(context, place.latitude, place.longitude) else null
                                    }
                                    // One Apple Maps style card: map on top, place row, then the place chips.
                                    Column(
                                        Modifier.fillMaxWidth().cardBlockSurface()
                                            .clip(locCardShape)
                                            .background(if (designStyle == DesignStyle.LiquidGlass) LiquidGlassTokens.contentColor else Color(0xFF141416))
                                    ) {
                                    Box(
                                        Modifier.fillMaxWidth().height((displayedLayout[blockKey]?.minHeight?.takeIf { it > 0 } ?: 180).coerceAtLeast(if (route != null) 280 else 120).dp)
                                            .background(com.cagri.real.ui.components.MapAppearance.placeholder(context))
                                    ) {
                                        // Zoom chosen while editing is stored on the location; pinch previews it live.
                                        val baseZoom = place.mapZoom ?: if (place.approximateCoordinates) 15.10 else 15.35
                                        var pinchScale by remember(blockKey) { mutableFloatStateOf(1f) }
                                        var awaitingSnapshot by remember(blockKey) { mutableStateOf(false) }
                                        fun commitZoom(zoom: Double) {
                                            val next = zoom.coerceIn(3.0, 19.0)
                                            if (kotlin.math.abs(next - baseZoom) < 0.01) { pinchScale = 1f; return }
                                            awaitingSnapshot = true
                                            editedLocation = editedLocation?.copy(mapZoom = next)
                                        }
                                        ReminderLocationMap(
                                            place,
                                            Modifier.fillMaxSize().then(if (!editing) Modifier else Modifier.pointerInput(blockKey) {
                                                awaitEachGesture {
                                                    awaitFirstDown(requireUnconsumed = false)
                                                    var scale = 1f
                                                    var pinched = false
                                                    do {
                                                        val event = awaitPointerEvent()
                                                        if (event.changes.count { it.pressed } >= 2) {
                                                            scale = (scale * event.calculateZoom()).coerceIn(0.125f, 16f)
                                                            pinchScale = scale
                                                            pinched = true
                                                            event.changes.forEach { it.consume() }
                                                        }
                                                    } while (event.changes.any { it.pressed })
                                                    if (pinched) commitZoom(baseZoom + kotlin.math.ln(scale.toDouble()) / kotlin.math.ln(2.0))
                                                }
                                            }),
                                            cameraZoom = place.mapZoom,
                                            onOpen = {
                                                // While editing, the map is the place's edit target (as the bar's Konum button), not a link out.
                                                if (editing) showLocationGuide = true
                                                else if (!openReminderLocationInMaps(context, place))
                                                    com.cagri.real.ui.components.RealToast.show(context, t("Harita uygulaması açılamadı"))
                                            },
                                            previewScale = { pinchScale },
                                            onSnapshotShown = { if (awaitingSnapshot) { awaitingSnapshot = false; pinchScale = 1f } },
                                            route = route,
                                            animateRoute = !reduceAnimations
                                        )
                                        route?.minutes?.let { minutes ->
                                            Text(
                                                t("Trafikle {0} dk", minutes), color = Color(0xFFF3F1F4), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                                modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                                                    .background(Color(0xD90B0B0D), RoundedCornerShape(14.dp))
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                        // The picked place in small type at the bottom right (its name, or the street).
                                        com.cagri.real.ui.components.reminderLocationLabel(place)?.let { label ->
                                            Text(
                                                label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                                                    .then(Modifier.sizeIn(maxWidth = 200.dp))
                                                    .background(Color(0xB3101012), RoundedCornerShape(10.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        if (editing) {
                                            Row(
                                                Modifier.align(Alignment.BottomStart).padding(8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                listOf(ActionGlyph.Add to 1.0, ActionGlyph.Minus to -1.0).forEach { (glyph, step) ->
                                                    Box(
                                                        Modifier.size(32.dp).clip(CircleShape)
                                                            .background(Color(0xD918181B))
                                                            .border(0.5.dp, Color(0x4DFFFFFF), CircleShape)
                                                            .clickable(role = Role.Button) {
                                                                // Button zoom: grow/shrink the current snapshot at once, sharpen when the new one lands.
                                                                pinchScale = if (step > 0) 2f else 0.5f
                                                                commitZoom(baseZoom + step)
                                                            }
                                                            .semantics { contentDescription = if (step > 0) t("Haritayı yakınlaştır") else t("Haritayı uzaklaştır") },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        ActionIcon(glyph, Modifier.size(15.dp), Color(0xFFF1F1F4))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        ActionIcon(ActionGlyph.Pin, Modifier.size(16.dp), Color(0xFF8ED7C4))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                place.placeName.ifBlank { com.cagri.real.ui.components.reminderLocationLabel(place) ?: t("Konum") },
                                                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis
                                            )
                                            if (place.address.isNotBlank()) {
                                                Text(place.address, color = Color(0xFFA5A5B0), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        if (editing) {
                                            IconButton(
                                                onClick = { editedLocation = null; locationJustShared = false },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                ActionIcon(ActionGlyph.Close, Modifier.size(15.dp), Color(0xFF8E8E98))
                                            }
                                        } else {
                                            Text(
                                                t("Haritada aç"), color = Color(0xFF4DA3FF), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false,
                                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                                    if (!openReminderLocationInMaps(context, place))
                                                        com.cagri.real.ui.components.RealToast.show(context, t("Harita uygulaması açılamadı"))
                                                }.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    // Look Around, Ara, Web sitesi (Apple Maps), as on iOS.
                                    if (!editing) com.cagri.real.ui.components.PlaceDetails(place, Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp))
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
                                                        if (editing) showLocationGuide = true
                                                        else if (!openReminderLocationInMaps(context, place)) {
                                                            com.cagri.real.ui.components.RealToast.show(context, t("Harita uygulaması açılamadı"))
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
                                                        Text(t("Haritada aç"), color = Color(0xFF9E9EA4), style = RealType.detailBlockMetaSecondary, modifier = Modifier.padding(top = 2.dp))
                                                    } else if (!place.mapsUrl.isNullOrBlank()) {
                                                        Text(t("Haritada aç"), color = Color(0xFFA5A5B0), style = RealType.detailBlockMeta)
                                                    } else {
                                                        Text(t("Konum ayrıntısı bulunamadı"), color = Color(0xFFA5A5B0), style = RealType.detailBlockMeta)
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
                                                    text = t("Günlük"),
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
                                                        "d MMMM yyyy · HH:mm", I18n.locale
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
                                                    .semantics { contentDescription = t("Günlük notu") },
                                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFF4F4F6), fontSize = 16.sp, lineHeight = 22.sp),
                                                cursorBrush = SolidColor(Color(0xFF82B3FF)),
                                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                                decorationBox = { innerTextField ->
                                                    if (editedJournalText.isEmpty()) {
                                                        Text(t("Bir not bırak..."), color = Color(0xFF6E6E77), fontSize = 16.sp)
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
                                                        Text(t("Sil"), color = Color(0xFFF87171), fontSize = 14.sp)
                                                    }
                                                    Spacer(Modifier.weight(1f))
                                                } else {
                                                    TextButton(onClick = { finishEditing() }) {
                                                        Text(t("İptal"), color = Color(0xFFA5A5B0), fontSize = 14.sp)
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
                                                        Text(t("Kaydet"), color = Color(0xFF91B9FF), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                    }
                                                }
                                            }
                                        } else {
                                            if (reminder.journal?.text?.isNotBlank() == true) {
                                                Spacer(Modifier.height(12.dp))
                                                Text(
                                                    text = com.cagri.real.ui.components.CardBodyLayout(reminder.journal.text,
                                                        reminder.journal.styles, com.cagri.real.data.BodyFormat(), onDark = true).annotated,
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
                if (heroKey != null) {
                    // The cover reaches the screen edges and up under the status bar, like a category's header:
                    // the column's side margins and top inset are undone here, the block keeps its rounded bottom.
                    val heroBleedTop = statusHeight + 10.dp
                    Box(
                        Modifier.layout { measurable, constraints ->
                            val extraWidth = 32.dp.roundToPx()
                            val up = heroBleedTop.roundToPx()
                            val width = constraints.maxWidth + extraWidth
                            val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                            layout(constraints.maxWidth, (placeable.height - up).coerceAtLeast(0)) {
                                placeable.place(-extraWidth / 2, -up)
                            }
                        }.then(if (sharedTopKey == heroKey && heroKey != "text") sharedCardModifier else Modifier),
                        propagateMinConstraints = true
                    ) {
                        CompositionLocalProvider(
                            // Same contract as inside the grid: blocks fill the given width/height (a photo crops to fill).
                            LocalCardContentComposition provides true,
                            LocalCardBlockHeight provides displayedLayout.getValue(heroKey).minHeight.dp + heroBleedTop,
                            LocalDetailHeroInset provides heroBleedTop
                        ) {
                            detailBlockContent(heroKey, 0)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
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
                    },
                    content = { key, index ->
                        if (key == sharedTopKey && key != "text") {
                            Box(sharedCardModifier, propagateMinConstraints = true) { detailBlockContent(key, index) }
                        } else detailBlockContent(key, index)
                    }
                )

                if (musicSearchOpen && editing) {
                    MusicSearchSheet(designStyle = designStyle,
                        onDismiss = { musicSearchOpen = false },
                        onSelect = { track ->
                            editedMusicBlocks = editedMusicBlocks + track
                            musicSearchOpen = false
                        })
                }

                if (gameSearchOpen) {
                    com.cagri.real.ui.components.GameSearchSheet(designStyle = designStyle,
                        onDismiss = { gameSearchOpen = false },
                        onSelect = { game -> onAddGameBlock(game); gameSearchOpen = false })
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
                        text = t("Oluşturuldu · {0}", detailReminderDate(reminder.createdAt)),
                        color = Color(0xFF8E8E98),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.End
                    )
                    if (reminder.completedAt != null) {
                        Text(
                            text = t("Tamamlandı · {0}", detailReminderDate(reminder.completedAt)),
                            color = Color(0xFF8E8E98),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.End
                        )
                    }
                    if (reminder.journal != null) {
                        Text(
                            text = t("Günlüğe eklendi · {0}", detailReminderDate(reminder.journal.createdAt)),
                            color = Color(0xFF8E8E98),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
        TopGlassOverlay(scene, { scenePosition }, Modifier.align(Alignment.TopCenter).graphicsLayer {
            // Over a hero cover the dark strip would shade the photo: it fades in as the page scrolls past it.
            alpha = if (detailHeroActive) (detailContentScroll.value / 280.dp.toPx()).coerceIn(0f, 1f) else 1f
        }, reduceDynamicBlur)
        if (designStyle == DesignStyle.LiquidGlass) {
            Box(Modifier.align(Alignment.TopCenter).then(if (deleting) Modifier.clearAndSetSemantics { } else Modifier).padding(start = 16.dp, end = 16.dp, top = statusHeight + 10.dp)) {
                detailHeader()
            }
        }
        BottomFade(Modifier.align(Alignment.BottomCenter).height(130.dp))
        if (deleting) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.16f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { deleting = false }
                .semantics { contentDescription = t("Silme sorusunu kapat") })
        }
        // Moving a block: the docks slide out of the way and come back when the finger lifts.
        val dockAway by animateFloatAsState(
            targetValue = if (cardBlockDrag.active) 1f else 0f,
            animationSpec = if (reduceAnimations) snap() else tween(if (cardBlockDrag.active) 200 else 260, easing = RealMotion.Emphasized),
            label = "detailDockAway"
        )
        DetailLayoutColumn(
            Modifier
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { bottomPanelCoordinates = it }
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = chromeAlpha * (1f - dockAway)
                    translationY = dockAway * (size.height + 24.dp.toPx())
                }
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
                    },
                    collapsedBar = { handle ->
                        // Lowered dock: a slim glass bar that still holds the handle.
                        DesignPanel(backdrop = scene, backdropPosition = { scenePosition },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).isolateFloatingPanelGestures(),
                            corners = 26.dp, designStyle = designStyle, reduceDynamicBlur = reduceDynamicBlur,
                            liquidDynamic = true) {
                            Box(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) { handle() }
                        }
                    }
                ) { handle ->
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
                                onUseDarkTextChange = null,
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
                                    Text(t("Kategori"), color = Color(0xFFB7B7C0), fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                                            Text(t("Kategori yok"), color = Color(0xFF8E8E98), style = RealType.detailBlockMeta)
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
                            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 10.dp)) {
                                // The dock's grab handle sits inside the panel, above "Renk ve kategori".
                                handle()
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
                                        Text(t("Renk ve kategori"), color = Color(0xFFB7B7C0), fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                                                indication = null /* pressScale already gives the press feedback */
                                            ) {
                                                focusManager.clearFocus()
                                                keyboard?.hide()
                                                isReorderingBlocks = !isReorderingBlocks
                                            }
                                            .semantics { contentDescription = t("Sırala") },
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

                                // Most used first, least used last (counter and market tiles at the end).
                                DetailQuickActionButton(
                                    designStyle = designStyle,
                                    reduceAnimations = reduceAnimations,
                                    glyph = ActionGlyph.Calendar,
                                    description = t("Tarih / Saat"),
                                    enabled = !savingDetail && !reminder.isCompleted,
                                    active = reminder.alertAt != null || reminder.plannedDateEpochDay != null,
                                    onClick = requestPermissionForDatePicker
                                )
                                DetailQuickActionButton(
                                    designStyle = designStyle,
                                    reduceAnimations = reduceAnimations,
                                    glyph = ActionGlyph.Photo,
                                    description = t("Fotoğraf ekle"),
                                    enabled = !savingDetail && !attachmentBusy,
                                    active = photoCount > 0,
                                    count = if (photoCount > 0) photoCount else null,
                                    isFull = photoCount >= 3,
                                    onClick = {
                                        if (photoCount >= 3) {
                                            com.cagri.real.ui.components.RealToast.show(context, t("En fazla 3 fotoğraf eklenebilir"))
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
                                    description = t("Kamera ile fotoğraf ekle"),
                                    enabled = !savingDetail && !attachmentBusy && !voiceRecordingActive,
                                    active = showCamera,
                                    isFull = photoCount >= 3,
                                    onClick = {
                                        if (photoCount >= 3) com.cagri.real.ui.components.RealToast.show(context, t("En fazla 3 fotoğraf eklenebilir"))
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
                                    glyph = ActionGlyph.Checklist,
                                    description = t("Checklist"),
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
                                    glyph = ActionGlyph.Cards,
                                    description = t("Kart Yığını"),
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
                                    glyph = ActionGlyph.Pin,
                                    description = t("Konum"),
                                    enabled = !savingDetail,
                                    active = editedLocation != null,
                                    onClick = { showLocationGuide = true }
                                )
                                DetailQuickActionButton(
                                    designStyle = designStyle,
                                    reduceAnimations = reduceAnimations,
                                    glyph = ActionGlyph.AudioWave,
                                    description = t("Ses"),
                                    enabled = !savingDetail && !attachmentBusy && !voiceRecordingActive,
                                    active = voiceCount > 0,
                                    count = if (voiceCount > 0) voiceCount else null,
                                    onClick = { focusManager.clearFocus(); keyboard?.hide(); voiceRecorderOpen = true }
                                )
                                DetailQuickActionButton(
                                    designStyle = designStyle,
                                    reduceAnimations = reduceAnimations,
                                    glyph = ActionGlyph.Music,
                                    description = t("Müzik"),
                                    enabled = !savingDetail && !attachmentBusy && !voiceRecordingActive,
                                    active = musicCount > 0,
                                    count = if (musicCount > 0) musicCount else null,
                                    onClick = { focusManager.clearFocus(); keyboard?.hide(); musicSearchOpen = true }
                                )
                                DetailQuickActionButton(
                                    designStyle = designStyle,
                                    reduceAnimations = reduceAnimations,
                                    glyph = ActionGlyph.Video,
                                    description = t("Film & Dizi"),
                                    enabled = !savingDetail && !attachmentBusy && !voiceRecordingActive,
                                    active = mediaCount > 0,
                                    count = if (mediaCount > 0) mediaCount else null,
                                    onClick = { focusManager.clearFocus(); keyboard?.hide(); mediaSearchOpen = true }
                                )
                                DetailQuickActionButton(
                                    designStyle = designStyle,
                                    reduceAnimations = reduceAnimations,
                                    glyph = ActionGlyph.Play,
                                    description = t("Oyun"),
                                    enabled = !savingDetail && !attachmentBusy && !voiceRecordingActive,
                                    active = reminder.gameBlocks.isNotEmpty(),
                                    count = reminder.gameBlocks.size.takeIf { it > 0 },
                                    onClick = { focusManager.clearFocus(); keyboard?.hide(); gameSearchOpen = true }
                                )
                                DetailQuickActionButton(
                                    designStyle = designStyle,
                                    reduceAnimations = reduceAnimations,
                                    glyph = ActionGlyph.File,
                                    description = t("Dosya ekle"),
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
                                    glyph = ActionGlyph.MoodVeryGood,
                                    description = t("Mood"),
                                    enabled = !savingDetail,
                                    active = reminder.mood != null,
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboard?.hide()
                                        standaloneMoodDraft = reminder.mood ?: JournalMood.NEUTRAL
                                        showMoodFullscreen = true
                                    }
                                )
                                // Real Labs: beta tools, after a thin rule at the very end of the bar.
                                var mapPosterOpen by remember { mutableStateOf(false) }
                                Box(Modifier.padding(horizontal = 6.dp).size(width = 1.dp, height = 22.dp).background(Color.White.copy(alpha = 0.16f)))
                                Text(
                                    "Real Labs", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(end = 2.dp)
                                )
                                DetailQuickActionButton(
                                    designStyle = designStyle,
                                    reduceAnimations = reduceAnimations,
                                    glyph = ActionGlyph.Poster,
                                    description = t("Harita posteri"),
                                    enabled = !savingDetail && !attachmentBusy,
                                    active = false,
                                    onClick = {
                                        if (editedLocation?.latitude == null) {
                                            com.cagri.real.ui.components.RealToast.show(context, t("Poster için önce bir konum seç"))
                                            showLocationGuide = true
                                        } else {
                                            focusManager.clearFocus(); keyboard?.hide(); mapPosterOpen = true
                                        }
                                    }
                                )
                                var skyPosterOpen by remember { mutableStateOf(false) }
                                DetailQuickActionButton(
                                    designStyle = designStyle,
                                    reduceAnimations = reduceAnimations,
                                    glyph = ActionGlyph.Sky,
                                    description = t("Gökyüzü posteri"),
                                    enabled = !savingDetail && !attachmentBusy,
                                    active = false,
                                    onClick = {
                                        if (editedLocation?.latitude == null) {
                                            com.cagri.real.ui.components.RealToast.show(context, t("Poster için önce bir konum seç"))
                                            showLocationGuide = true
                                        } else {
                                            focusManager.clearFocus(); keyboard?.hide(); skyPosterOpen = true
                                        }
                                    }
                                )
                                editedLocation?.takeIf { skyPosterOpen }?.let { place ->
                                    // The card's moment: its alert, else its planned day at 21:00, else when it was made.
                                    val moment = reminder.alertAt
                                        ?: reminder.plannedDateEpochDay?.let {
                                            java.time.LocalDate.ofEpochDay(it).atTime(21, 0)
                                                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                        }
                                        ?: reminder.createdAt
                                    com.cagri.real.ui.poster.SkyPosterDialog(
                                        place, moment,
                                        onAttach = { file ->
                                            if (photoCount >= 3) {
                                                file.delete()
                                                com.cagri.real.ui.components.RealToast.show(context, t("En fazla 3 fotoğraf eklenebilir"))
                                            } else importPicked(emptyList(), 0, photosOnly = true, cameraFile = file)
                                        },
                                        onClose = { skyPosterOpen = false }
                                    )
                                }
                                editedLocation?.takeIf { mapPosterOpen }?.let { place ->
                                    com.cagri.real.ui.poster.MapPosterDialog(
                                        place,
                                        onAttach = { file ->
                                            if (photoCount >= 3) {
                                                file.delete()
                                                com.cagri.real.ui.components.RealToast.show(context, t("En fazla 3 fotoğraf eklenebilir"))
                                            } else importPicked(emptyList(), 0, photosOnly = true, cameraFile = file)
                                        },
                                        onClose = { mapPosterOpen = false }
                                    )
                                }
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
                                        reminder.earlyAlertMinutes?.let { early ->
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
                                                ActionIcon(ActionGlyph.Bell, Modifier.size(14.dp), Color(0xFF91B9FF))
                                                Text(com.cagri.real.ui.components.earlyAlertLabel(early) + t(" önce"), color = Color(0xFFD2D2DA), style = RealType.composerAuxiliaryPill)
                                            }
                                        }
                                        val currentEndTimeStr = reminder.endAt?.let { end ->
                                            java.time.Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault())
                                                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm", I18n.locale))
                                        }

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
                                            Text(if (detailDurationMinutes != null) t("Süre: {0} min", detailDurationMinutes) else t("Süre ekle"), color = Color(0xFFD2D2DA), style = RealType.composerAuxiliaryPill)
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
                                            Text(currentEndTimeStr?.let { t("Bitiş: {0}", it) } ?: t("+ Bitiş"), color = Color(0xFFD2D2DA), style = RealType.composerAuxiliaryPill)
                                            if (currentEndTimeStr != null) {
                                                ActionIcon(ActionGlyph.Close, Modifier.size(12.dp).clip(CircleShape)
                                                    .clickable { setReminderTiming(reminder.id, reminder.earlyAlertMinutes, null) }
                                                    .semantics { contentDescription = t("Bitişi kaldır") }, Color(0xFFA7A7AF))
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = { onSetAlert(null, RecurrenceRule.NONE) },
                                        modifier = Modifier.semantics { contentDescription = t("Tarih ve saati kaldır") }
                                    ) {
                                        ActionIcon(ActionGlyph.Close, Modifier.size(18.dp))
                                    }
                                }
                            } else if (reminder.plannedDateEpochDay != null) {
                                // A day without a time (e.g. moved to 24 hour): its date can be removed too.
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val scheduleShape = RoundedCornerShape(12.dp)
                                    Row(
                                        modifier = Modifier
                                            .weight(1f, fill = false)
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
                                        Text(detailPlannedDate(reminder.plannedDateEpochDay), color = Color(0xFFE2E8F0), style = RealType.composerScheduleChip)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    IconButton(
                                        onClick = { onSetAlert(null, RecurrenceRule.NONE) },
                                        modifier = Modifier.semantics { contentDescription = t("Tarihi kaldır") }
                                    ) {
                                        ActionIcon(ActionGlyph.Close, Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                            if (attachmentBusy) Text(t("Ek hazırlanıyor…"), color = Color(0xFFB4B4BE), style = RealType.detailBlockMetaSecondary)
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
                            Text(t("Günlük ekle"), color = Color(0xFFD3D3D9), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(t("Nasıl geçti? Aklında kalanı yaz."), color = Color(0xFF8E8E98), fontSize = 12.sp)
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
                val dockMode = when {
                    inlineCardStackEditing && !editing -> DetailDockMode.InlineStack
                    editing -> DetailDockMode.Edit
                    else -> DetailDockMode.Normal
                }
                DetailDockDrops(dockMode, saveSweepStartedAt, reduceAnimations, Modifier.matchParentSize())
                Box(Modifier.matchParentSize().detailDockSweep { saveSweep.value })
                AnimatedContent(
                    targetState = dockMode,
                    transitionSpec = { EnterTransition.None togetherWith ExitTransition.None using SizeTransform(clip = false) },
                    label = "detailDockMode"
                ) { mode ->
                // Fixed for this set's lifetime: staggered by the Kaydet sweep when it is still crossing the glass.
                val enterDelays = remember {
                    val elapsed = android.os.SystemClock.uptimeMillis() - saveSweepStartedAt
                    detailDockEnterDelays(mode.slots, elapsed.takeIf { mode == DetailDockMode.Normal })
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 0.dp), verticalAlignment = Alignment.CenterVertically) {
                    val item: @Composable (Int) -> Modifier = { index -> Modifier.weight(1f).then(detailDockItemMotion(enterDelays[index], reduceAnimations)) }
                    if (mode == DetailDockMode.InlineStack) {
                        DetailAction(
                            t("Bitti"),
                            ActionGlyph.Check,
                            item(0),
                            enabled = !savingDetail,
                            color = Color(0xFF91B9FF),
                            reduceAnimations = reduceAnimations,
                            onClick = saveDraftAndClose
                        )
                    } else if (mode == DetailDockMode.Edit) {
                        DetailAction(t("Vazgeç"), ActionGlyph.Close, item(0), reduceAnimations = reduceAnimations, onClick = finishEditing)
                        DetailAction(
                            t("Kaydet"),
                            ActionGlyph.Check,
                            item(1),
                            enabled = !attachmentBusy && !savingDetail && !voiceRecordingActive &&
                                (editedText.isNotBlank() || editedVoiceRecordings.isNotEmpty()) &&
                                (!journalEditing || editedJournalMood != null || editedJournalText.trim().isNotBlank()),
                            color = Color(0xFF91B9FF),
                            reduceAnimations = reduceAnimations,
                            onClick = {
                                // Only the Kaydet tap sends the mic sweep; the background-tap autosave closes quietly.
                                if (!reduceAnimations) {
                                    saveSweepStartedAt = android.os.SystemClock.uptimeMillis()
                                    coroutineScope.launch {
                                        saveSweep.snapTo(0f)
                                        saveSweep.animateTo(1f, tween(DETAIL_DOCK_MILLIS, easing = LinearEasing))
                                        saveSweep.snapTo(0f)
                                    }
                                }
                                saveDraftAndClose()
                            }
                        )
                    } else {
                        DetailAction(if (reminder.isCompleted) t("Geri al") else t("Tamamla"),
                            if (reminder.isCompleted) ActionGlyph.Undo else ActionGlyph.Check,
                            item(0), enabled = !deleting, reduceAnimations = reduceAnimations, onClick = {
                                if (!reminder.isCompleted) {
                                    RealHaptics.success(view)
                                }
                                onToggleCompleted()
                            })
                        DetailAction(t("Düzenle"), ActionGlyph.Edit, item(1), enabled = !deleting, reduceAnimations = reduceAnimations, onClick = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                            loadReminderIntoDraft()
                            inlineCardStackEditing = false
                            editing = true
                        })
                        DetailAction("Share", ActionGlyph.Share, item(2), enabled = !deleting, reduceAnimations = reduceAnimations, onClick = {
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
                        DetailAction(t("Sil"), ActionGlyph.Delete, item(3), reduceAnimations = reduceAnimations, onClick = { deleting = !deleting })
                    }
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
            initialEarlyAlertMinutes = reminder.earlyAlertMinutes,
            initialEndAt = reminder.endAt,
            onConfirmTiming = { early, end -> setReminderTiming(reminder.id, early, end) },
            onConfirm = { at, rule -> onSetAlert(at, rule); showDatePicker = false },
            exactAlarmsAllowed = exactAlarmsAllowed,
            onRequestExactAlarms = onRequestExactAlarms)

        AnimatedDateTimePickerOverlay(
            visible = showDetailEndPicker,
            initial = reminder.endAt ?: ((reminder.alertAt ?: System.currentTimeMillis()) + (detailDurationMinutes ?: 60) * 60_000L),
            initialRepeatRule = RecurrenceRule.NONE,
            onDismiss = { showDetailEndPicker = false },
            backdrop = scene,
            backdropPosition = { scenePosition },
            designStyle = designStyle,
            reduceDynamicBlur = reduceDynamicBlur,
            reduceAnimations = reduceAnimations,
            onConfirm = { at, _ ->
                // Bitiş is its own day and time; the duration chip stays as it is ("4 gün, her gün 60 dk").
                if (at > (reminder.alertAt ?: 0L)) setReminderTiming(reminder.id, reminder.earlyAlertMinutes, at)
                else com.cagri.real.ui.components.RealToast.show(context, t("Bitiş, başlangıçtan sonra olmalı."))
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
        val setLocationTrigger = com.cagri.real.ui.components.LocalSetReminderLocationTrigger.current
        com.cagri.real.ui.components.LocationPickerSheet(
            initialTrigger = reminder.locationTrigger,
            onRemind = { place, trigger ->
                showLocationGuide = false
                editing = true
                editedLocation = place
                locationJustShared = true
                setLocationTrigger(reminder.id, trigger)
            },
            visible = showLocationGuide,
            initial = editedLocation,
            onSave = { place ->
                showLocationGuide = false
                editing = true
                editedLocation = place
                locationJustShared = true
            },
            onShareFromGoogleMaps = { showLocationGuide = false; onRequestLocationShare() },
            onCancel = { showLocationGuide = false }
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
            enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(220, easing = RealMotion.Standard)) + scaleIn(RealMotion.snappy(0.32f), initialScale = 0.96f),
            exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(160, easing = RealMotion.Exit)) + scaleOut(tween(160), targetScale = 0.96f),
            modifier = Modifier.fillMaxSize().zIndex(100f)
        ) {
            MoodV3FullScreen(
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
                    indication = null /* pressScale already gives the press feedback */,
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
                indication = null /* pressScale already gives the press feedback */,
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
        ReminderDetailScreen(ReminderItem(1, t("Akşam için birkaç küçük not"), 1789610400000), {}, {}, {}, { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> true }, {})
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
    reminder.isCompleted -> t("Tamamlandı")
    reminder.isQuick24HourActive() -> "24 hour"
    reminder.alertAt == null && reminder.plannedDateEpochDay == null -> if (!hasCategory) "No alert" else null
    else -> "Planned"
}

fun formatDisplayUrl(url: String): String {
    return try {
        val uri = android.net.Uri.parse(url)
        uri.host?.removePrefix("www.") ?: url
    } catch (_: Exception) {
        url
    }
}

/** Height a block gets when "En üstteki içeriği büyüt" is on (within the 800dp layout limit). */
internal const val DETAIL_HERO_MIN_HEIGHT_DP = 360

/** Extra height the cover block reaches up under the status bar; 0 for every other block. */
internal val LocalDetailHeroInset = androidx.compose.runtime.compositionLocalOf { 0.dp }
