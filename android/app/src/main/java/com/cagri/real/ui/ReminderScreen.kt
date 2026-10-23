@file:OptIn(androidx.compose.animation.core.ExperimentalTransitionApi::class)

package com.cagri.real.ui

import com.cagri.real.i18n.I18n
import com.cagri.real.i18n.t
import com.cagri.real.contextcards.accent

import com.cagri.real.ui.components.liquidChrome
import com.cagri.real.ui.components.label
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.cagri.real.data.ReminderAttachment
import com.cagri.real.data.dividerSlots
import com.cagri.real.data.ReminderLocation
import com.cagri.real.data.MusicBlock
import com.cagri.real.data.MediaBlock
import com.cagri.real.data.CardStackBlock
import com.cagri.real.data.ReminderVoiceRecording
import com.cagri.real.data.JournalMood
import com.cagri.real.data.MoodDetails
import com.cagri.real.data.MoodScope
import com.cagri.real.data.MoodTrackerEntry
import com.cagri.real.data.AttachmentStorage
import com.cagri.real.speech.TimeOfDay
import com.cagri.real.ui.components.AnimatedDateTimePickerOverlay
import com.cagri.real.ui.components.NotificationPermissionSheet
import com.cagri.real.ui.components.MicrophonePermissionSheet
import com.cagri.real.ui.components.HomeWordmark
import com.cagri.real.ui.components.ProfileAvatar
import com.cagri.real.ui.components.REAL_PET_PERCH_AVATAR
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import com.cagri.real.ui.components.pressScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import com.cagri.real.ui.components.RealHaptics
import com.cagri.real.ui.components.RealMotion
import androidx.compose.ui.unit.IntOffset
import com.cagri.real.ui.components.RealPetRuntime
import com.cagri.real.ui.components.RealPetKeywords
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.RecurrenceRule
import com.cagri.real.data.ReminderEditPatch
import com.cagri.real.data.firstPatternOccurrence
import com.cagri.real.data.patternLabel
import com.cagri.real.speech.VoiceCardCallbacks
import com.cagri.real.speech.VoiceRecentCards
import com.cagri.real.speech.runVoiceCardAction
import com.cagri.real.speech.applyVoiceBulkPlan
import com.cagri.real.data.SkipOccurrenceResult
import com.cagri.real.data.AddChecklistResult
import com.cagri.real.data.VoiceBulkShift
import com.cagri.real.speech.VoiceBulkPlan
import com.cagri.real.data.ChecklistItem
import com.cagri.real.data.ReminderCategory
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.ROLLING_24_HOURS_MILLIS
import com.cagri.real.data.isQuick24HourActive
import com.cagri.real.data.inCategory
import com.cagri.real.speech.VoicePhase
import com.cagri.real.speech.GeminiVoiceIntentClient
import com.cagri.real.speech.GeminiVoiceIntent
import com.cagri.real.speech.HomeVoiceAutoFinish
import com.cagri.real.speech.rememberDirectVoiceSave
import com.cagri.real.speech.voiceIntentLocation
import com.cagri.real.speech.resolveVoiceContentBlocks
import com.cagri.real.speech.rememberVoiceInput
import com.cagri.real.speech.VoiceRecognitionAlternatives
import com.cagri.real.data.CategoryColorOverride
import com.cagri.real.data.CustomCategory
import com.cagri.real.ui.components.AccountMenuOverlay
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import com.cagri.real.ui.components.CategoryEditor
import com.cagri.real.ui.components.DEFAULT_NEW_CATEGORY_COLOR_INDEX
import com.cagri.real.ui.components.ReminderColorChoices
import com.cagri.real.ui.components.CategoryCollections
import com.cagri.real.ui.components.parseCategoryOrderPreference
import com.cagri.real.ui.components.encodeCategoryOrderPreference
import com.cagri.real.ui.components.CategorySelectionOverlay
import com.cagri.real.ui.components.GlassComposer
import com.cagri.real.ui.components.HomeLiquidDock
import com.cagri.real.ui.components.VoiceListeningWindow
import com.cagri.real.ui.components.HomeWidgetScreen
import com.cagri.real.ui.components.YasamJournalSheet
import com.cagri.real.ui.components.HomeDockExtraBottomSpace
import com.cagri.real.ui.components.DateSuggestionType
import com.cagri.real.ui.components.detectDateSuggestion
import com.cagri.real.ui.components.MoodV3FullScreen
import com.cagri.real.ui.components.MoodTrackerHistoryScreen
import com.cagri.real.ui.components.MoodSuggestionsScreen
import com.cagri.real.ui.components.planner.PlannerScreen
import com.cagri.real.ui.components.planner.PlannerRepository
import com.cagri.real.ui.components.planner.PlannerEventDetails
import androidx.compose.foundation.border
import com.cagri.real.ui.components.ReminderCard
import com.cagri.real.data.WhatsAppConsentRepository
import com.cagri.real.data.WhatsAppRecipientRepository
import com.cagri.real.data.WhatsAppSender
import com.cagri.real.ui.components.WhatsAppConsentSheet
import com.cagri.real.ui.components.WhatsAppRecipientSheet
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.cagri.real.ui.components.ReminderEntryTracker
import com.cagri.real.ui.components.SelectionActionButtons
import com.cagri.real.ui.components.TopGlassOverlay
import com.cagri.real.ui.components.deferredBottomPadding
import com.cagri.real.ui.components.deferredHeight
import com.cagri.real.data.ReminderSortOrder
import com.cagri.real.data.filterAndSortReminders
import com.cagri.real.data.HOME_COMPLETED_PREVIEW_LIMIT
import com.cagri.real.ui.components.CategoryColorFilterButton
import com.cagri.real.ui.components.CategoryColorSelectorHeader
import com.cagri.real.ui.components.CategoryEditButton
import com.cagri.real.ui.components.HomeSortControl
import com.cagri.real.ui.components.categoryProgress
import com.cagri.real.ui.components.CategoryProgressPanel
import com.cagri.real.ui.components.defaultStyle
import com.cagri.real.ui.components.CategoryGlassActions
import com.cagri.real.ui.components.CategoryGlassBackButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Yaşam "Bugün yaz" ile oluşturulan günlük kartlarının başlık öneki. */
internal const val STANDALONE_JOURNAL_PREFIX = "Günlük · "

/** Yalnız günlük olarak oluşturulmuş kart: Günlüklerim'de görünür, genel kart listelerinde görünmez. */
/**
 * The note after a category's card count: overdue cards first, then what is on today.
 * Second = shown as a warning.
 */
private fun categoryHeaderDetail(items: List<ReminderItem>, today: LocalDate): Pair<String, Boolean>? {
    val zone = java.time.ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val todayDay = today.toEpochDay()
    val open = items.filter { !it.isCompleted }
    val overdue = open.count { r ->
        r.id >= 0 && ((r.alertAt != null && r.alertAt < now && r.notifiedAlertAt == r.alertAt) ||
            (r.alertAt == null && r.plannedDateEpochDay != null && r.plannedDateEpochDay < todayDay && r.quick24HourUntilAt == null))
    }
    if (overdue > 0) return t("{0} gecikmiş", overdue) to true
    fun day(r: ReminderItem): Long? = r.alertAt?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() }
        ?: r.plannedDateEpochDay
    val todayCount = open.count { day(it) == todayDay }
    return if (todayCount > 0) t("{0} tanesi bugün", todayCount) to false else null
}

private fun ReminderItem.isStandaloneJournal(): Boolean =
    journal != null && text.startsWith(STANDALONE_JOURNAL_PREFIX)

data class PendingReminderLocation(val reminderId: Long, val location: ReminderLocation)

internal data class ReminderRoute(val category: ReminderCategory?, val customCategoryId: Long?, val detailId: Long?) {
    val key: String get() = detailId?.let { "detail_$it" }
        ?: customCategoryId?.let { "custom_cat_$it" }
        ?: category?.name
        ?: "home"
}

@Composable
fun ReminderScreen(
    reminders: List<ReminderItem>,
    /** Ayraçlar: dividers drawn only in their own category's list. */
    dividers: List<ReminderItem> = emptyList(),
    onAddDivider: (Long, String) -> Unit = { _, _ -> },
    onEditDivider: (Long, String) -> Unit = { _, _ -> },
    onDeleteDivider: (Long) -> Unit = {},
    /** Tamamlama notu on a completed goal (category goal screen). */
    onSetCompletionNote: (Long, String?) -> Unit = { _, _ -> },
    /** Kontrol ettir on the selected cards (null takes it off). */
    onSetCheckRequest: (Set<Long>, com.cagri.real.data.CheckRequest?) -> Unit = { _, _ -> },
    onAddReminder: suspend (String, Long?, RecurrenceRule, Long?, Boolean, List<ChecklistItem>, Boolean, String?, List<ReminderAttachment>, List<ReminderVoiceRecording>, ReminderLocation?, Long?, Long?, List<MusicBlock>, List<MediaBlock>) -> Long?,
    onAddSuggestedReal: suspend (String, MusicBlock?, MediaBlock?, CardStackBlock?) -> Long? = { _, _, _, _ -> null },
    onUndoSuggestedReal: suspend (Long) -> Boolean = { false },
    /** Oyun keşfi "REAL'e ekle": a card with the game block. */
    onAddGame: suspend (com.cagri.real.data.CardGameBlock) -> Long? = { null },
    onRemoveGameBlock: (Long, String) -> Unit = { _, _ -> },
    onAddGameBlock: (Long, com.cagri.real.data.CardGameBlock) -> Unit = { _, _ -> },
    onToggleCompleted: (Long) -> Unit,
    onToggleImportant: (Long) -> Unit,
    onMoveTo24Hour: suspend (Long) -> Boolean = { false },
    onEditReminder: suspend (Long, String, Int?, Int?, Long?, List<ChecklistItem>?, Int?, Float?, Int?, List<ReminderAttachment>?, List<ReminderVoiceRecording>?, ReminderLocation?, String?, List<MusicBlock>, List<MediaBlock>, CardStackBlock?, List<String>?, Map<String, com.cagri.real.data.CardBlockPlacement>?, Boolean) -> Boolean,
    onDeleteReminder: (Long) -> Unit,
    /** Planner delete of a repeating event: (reminderId, tapped occurrence start, scope). Defaults to deleting the whole card. */
    onDeleteReminderOccurrence: (Long, Long, com.cagri.real.data.RepeatDeleteScope) -> Unit = { id, _, _ -> onDeleteReminder(id) },
    onSetAlert: (Long, Long?, RecurrenceRule) -> Unit,
    onSetRepeatPattern: (Long, Int, Long?) -> Unit = { _, _, _ -> },
    onVoiceEdit: (Long, ReminderEditPatch) -> Unit = { _, _ -> },
    onVoiceSkipOccurrence: suspend (Long, Long?) -> SkipOccurrenceResult? = { _, _ -> null },
    onVoiceBulkShift: (List<VoiceBulkShift>) -> Unit = {},
    onVoiceAddChecklistItems: suspend (Long, List<String>) -> AddChecklistResult? = { _, _ -> null },
    onSetJournal: (Long, JournalMood?, String) -> Unit,
    onDeleteJournal: (Long) -> Unit,
    onSetMood: (Long, JournalMood?, MoodDetails?) -> Unit = { _, _, _ -> },
    homeMoodEntries: List<MoodTrackerEntry> = emptyList(),
    onAddHomeMood: (JournalMood, MoodDetails) -> Unit = { _, _ -> },
    onClearAllMoods: () -> Unit = {},
    onToggleChecklistItem: (Long, Long) -> Unit = { _, _ -> },
    onDeleteReminders: (Set<Long>) -> Unit = { ids -> ids.forEach { onDeleteReminder(it) } },
    onBulkSetCategory: (Set<Long>, Long?) -> Unit = { _, _ -> },
    exactAlarmsAllowed: Boolean = true,
    onRequestExactAlarms: () -> Unit = {},
    requestedReminderId: Long? = null,
    onRequestHandled: () -> Unit = {},
    reduceDynamicBlur: Boolean = false,
    onReduceDynamicBlurChange: (Boolean) -> Unit = {},
    reduceAnimations: Boolean = false,
    onReduceAnimationsChange: (Boolean) -> Unit = {},
    realPetEnabled: Boolean = false,
    onRealPetEnabledChange: (Boolean) -> Unit = {},
    onRealPetSurfaceActiveChange: (Boolean) -> Unit = {},
    designStyle: DesignStyle = DesignStyle.Glass,
    onDesignStyleChange: (DesignStyle) -> Unit = {},
    customCategories: List<CustomCategory> = emptyList(),
    onCreateCategory: (String, String, Int, Int?, Boolean, Boolean, Float?, Int?, String?) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onCreateVoiceCategory: suspend (String, String, Int) -> CustomCategory?,
    onEditCategory: (Long, String, String, Int, Int?, Boolean, Boolean, Float?, Int?) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onDeleteCategory: (Long, Boolean) -> Unit = { _, _ -> },
    onDeleteCategories: (Set<Long>) -> Unit = { ids -> ids.forEach { onDeleteCategory(it, false) } },
    onOpenCustomCategory: (CustomCategory) -> Unit = {},
    builtInCategoryColors: Map<ReminderCategory, CategoryColorOverride> = emptyMap(),
    onUpdateBuiltInCategoryColor: (ReminderCategory, CategoryColorOverride?) -> Unit = { _, _ -> },
    pendingPanoBoardId: String? = null,
    onPanoLocationHandled: () -> Unit = {},
    pendingLocationShare: PendingReminderLocation? = null,
    onLocationShareHandled: () -> Unit = {},
    onRequestLocationShare: (Long) -> Unit = {},
    onResolveLocationPreview: (Long) -> Unit = {},
    pendingShareDraft: com.cagri.real.data.SharedPayload? = null,
    onShareDraftHandled: () -> Unit = {},
    userName: String = "",
    notificationsAllowed: Boolean = true,
    onRequestNotifications: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    isFirstHomeIntro: Boolean = false,
    onFirstHomeIntroComplete: () -> Unit = {},
    onPickAttachment: () -> Unit = {}
) {
    val calendarConnection = com.cagri.real.calendar.rememberCalendarConnection(reminders)
    com.cagri.real.calendar.CalendarPublicationPrompt(calendarConnection, reminders, reduceDynamicBlur)
    com.cagri.real.ui.auth.GoogleProfileNamePrompt(reduceDynamicBlur)
    fun calendarSyncStatus(card: ReminderItem): Boolean? {
        // revision observes association/publication receipts without putting calendar IDs into card JSON.
        val revision = calendarConnection.revision
        return runCatching {
            val links = com.cagri.real.calendar.CalendarCardLinks(calendarConnection.store)
            if (!links.hasExternalTarget(card.id)) null
            else links.publications()[card.id]?.let { it.applied && it.cardVersion == card.updatedAt && it.expiresAt > System.currentTimeMillis() } == true
        }.getOrDefault(null)
    }
    var showCalendarConnection by rememberSaveable { mutableStateOf(false) }
    val categoryUiContext = LocalContext.current
    val categoryUiPreferences = remember(categoryUiContext) { com.cagri.real.platform.android.sync.SyncLocal.preferences(categoryUiContext, "real_ui", Context.MODE_PRIVATE) }
    val realPetPreferences = remember(categoryUiContext) { categoryUiContext.getSharedPreferences("real_pet_prefs", Context.MODE_PRIVATE) }
    LaunchedEffect(realPetPreferences) {
        RealPetRuntime.keywordsEnabled.value = realPetPreferences.getBoolean("pet_keywords_enabled", false)
    }
    var categoryOrderKeys by remember {
        mutableStateOf(parseCategoryOrderPreference(categoryUiPreferences.getString("category_order_v1", null)))
    }
    DisposableEffect(categoryUiPreferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "category_order_v1") categoryOrderKeys = parseCategoryOrderPreference(categoryUiPreferences.getString(key, null))
        }
        categoryUiPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { categoryUiPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var category by rememberSaveable { mutableStateOf<ReminderCategory?>(null) }
    var customCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var listPetSurfaceActive by remember { mutableStateOf(true) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val realPetSurfaceActive = !showSettings && when {
        detailId != null -> true
        category == null && customCategoryId == null -> listPetSurfaceActive
        else -> false
    }
    val latestPetSurfaceCallback by rememberUpdatedState(onRealPetSurfaceActiveChange)
    LaunchedEffect(realPetSurfaceActive) {
        latestPetSurfaceCallback(realPetSurfaceActive)
    }
    DisposableEffect(Unit) {
        onDispose { latestPetSurfaceCallback(false) }
    }

    // The playful Add to REAL placeholder is page-scoped: once triggered it stays until
    // the user changes page/window, then the real placeholder is restored on return.
    LaunchedEffect(category, customCategoryId, detailId, showSettings) {
        RealPetRuntime.clearPlaceholderOverride()
        RealPetKeywords.onComposerClosedOrSubmitted()
    }
    var draft by rememberSaveable { mutableStateOf("") }
    var draftAttachments by rememberSaveable(
        stateSaver = Saver<List<ReminderAttachment>, ArrayList<String>>(
            save = { list -> ArrayList(list.map { "${it.id}|${it.localPath}" }) },
            restore = { list ->
                list.mapNotNull { str ->
                    val parts = str.split("|", limit = 2)
                    if (parts.size == 2) ReminderAttachment(parts[0], parts[1]) else null
                }
            }
        )
    ) { mutableStateOf(emptyList()) }
    var draftAlertAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var draftRepeatRule by rememberSaveable { mutableStateOf(RecurrenceRule.NONE) }
    var isShareDraft by rememberSaveable { mutableStateOf(false) }
    var draftReferenceUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val categoryColorFilters = rememberSaveable(
        saver = Saver<SnapshotStateMap<String, Int>, ArrayList<String>>(
            save = { map ->
                val list = ArrayList<String>(map.size)
                for ((k, v) in map) {
                    list.add("$k:$v")
                }
                list
            },
            restore = { list ->
                val map = mutableStateMapOf<String, Int>()
                for (item in list) {
                    val parts = item.split(":", limit = 2)
                    if (parts.size == 2) {
                        parts[1].toIntOrNull()?.let { v -> map[parts[0]] = v }
                    }
                }
                map
            }
        )
    ) {
        mutableStateMapOf<String, Int>()
    }
    val stateHolder = rememberSaveableStateHolder()
    LaunchedEffect(requestedReminderId) {
        if (requestedReminderId != null) {
            ColdStartTracker.markExecuted()
            if (reminders.any { it.id == requestedReminderId }) { detailId = requestedReminderId; showSettings = false }
            onRequestHandled()
        }
    }
    LaunchedEffect(pendingLocationShare) {
        if (pendingLocationShare != null && reminders.any { it.id == pendingLocationShare.reminderId }) {
            detailId = pendingLocationShare.reminderId
            showSettings = false
        }
    }
    LaunchedEffect(pendingShareDraft) {
        if (pendingShareDraft != null) {
            draft = pendingShareDraft.title
            draftReferenceUrl = pendingShareDraft.url
            isShareDraft = true
            detailId = null
            showSettings = false
            onShareDraftHandled()
        }
    }

    // Check the destination before closing detail. An absent/filtered card has no bounds to return to.
    fun hasReturnCard(route: ReminderRoute, reminderId: Long?): Boolean {
        val item = reminders.firstOrNull { it.id == reminderId } ?: return false
        val owner = customCategories.firstOrNull { it.id == item.customCategoryId }
        val belongs = when {
            route.customCategoryId != null -> item.customCategoryId == route.customCategoryId
            route.category == ReminderCategory.JournalEntries ->
                listOf(item).inCategory(ReminderCategory.JournalEntries).isNotEmpty()
            route.category != null -> owner?.hideFromGeneralLists != true &&
                listOf(item).inCategory(route.category).isNotEmpty()
            else -> owner?.hideFromGeneralLists != true &&
                (item.isCompleted || !item.isQuick24HourActive(System.currentTimeMillis()))
        }
        if (!belongs) return false
        val key = route.customCategoryId?.let { "custom_$it" }
            ?: route.category?.let { "cat_${it.name}" }
        val colorFilter = key?.let { categoryColorFilters[it] }
        return filterAndSortReminders(listOf(item), colorFilter, ReminderSortOrder.RECENTLY_ADDED).isNotEmpty()
    }

    // The page route is driven by a seekable transition so a back swipe or a pull-down on the
    // detail card can scrub the real card -> list shared-bounds transition with the finger.
    val routeNow = ReminderRoute(category, customCategoryId, detailId)
    val routeSeekState = remember { SeekableTransitionState(routeNow) }
    val routeScope = rememberCoroutineScope()
    val pullDensity = androidx.compose.ui.platform.LocalDensity.current
    val pullState = remember { DetailPullDismissState() }
    val pullDistancePx = with(pullDensity) { 320.dp.toPx() }
    val pullFlingPx = with(pullDensity) { 700.dp.toPx() }
    LaunchedEffect(pullState, routeSeekState) {
        snapshotFlow { pullState.active to pullState.distancePx }.collectLatest { (active, distance) ->
            if (active) {
                routeSeekState.seekTo(
                    (distance / pullDistancePx).coerceIn(0f, 1f),
                    ReminderRoute(category, customCategoryId, null)
                )
            }
        }
    }
    val detailPullDismiss = remember(routeSeekState) {
        DetailPullDismiss(
            onStart = { pullState.distancePx = 0f; pullState.active = true },
            onDrag = { distance -> pullState.distancePx = distance },
            onEnd = { distance, velocity ->
                pullState.active = false
                val commit = distance / pullDistancePx >= 0.33f || velocity >= pullFlingPx
                if (commit) {
                    detailId = null
                } else {
                    val backTo = ReminderRoute(category, customCategoryId, detailId)
                    routeScope.launch { routeSeekState.animateTo(backTo, RealMotion.snappy(0.32f)) }
                }
            },
            onCancel = {
                pullState.active = false
                val backTo = ReminderRoute(category, customCategoryId, detailId)
                routeScope.launch { routeSeekState.animateTo(backTo, RealMotion.snappy(0.32f)) }
            }
        )
    }

    // Detail -> list: finger-driven predictive back (reduce-animations keeps the plain handler).
    PredictiveBackHandler(enabled = !showSettings && detailId != null && !reduceAnimations) { progress ->
        val homeRoute = ReminderRoute(category, customCategoryId, null)
        val detailRoute = ReminderRoute(category, customCategoryId, detailId)
        try {
            progress.collect { event: BackEventCompat ->
                routeSeekState.seekTo(event.progress.coerceIn(0f, 1f), homeRoute)
            }
            detailId = null
        } catch (e: CancellationException) {
            withContext(NonCancellable) { routeSeekState.animateTo(detailRoute, RealMotion.snappy(0.32f)) }
            throw e
        }
    }
    BackHandler(
        enabled = !showSettings && (
            category != null || customCategoryId != null || (detailId != null && reduceAnimations)
        )
    ) {
        if (detailId != null) detailId = null
        else {
            category = null
            customCategoryId = null
        }
    }
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        AnimatedContent(
            targetState = showSettings,
            contentKey = { if (it) "settings" else "reminders" },
            transitionSpec = {
                if (reduceAnimations) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    val enterAnim = fadeIn(
                        tween(220, easing = RealMotion.Standard)
                    ) + scaleIn(
                        tween(260, easing = RealMotion.Standard),
                        initialScale = 0.98f
                    ) + slideInVertically(
                        tween(260, easing = RealMotion.Standard)
                    ) { it / 32 }

                    val exitAnim = fadeOut(
                        tween(180, easing = RealMotion.Exit)
                    ) + scaleOut(
                        tween(200, easing = RealMotion.Exit),
                        targetScale = 0.98f
                    )

                    enterAnim togetherWith exitAnim
                }
            },
            label = "settingsPage"
        ) { settingsVisible ->
            if (settingsVisible) {
                if (showCalendarConnection) {
                    com.cagri.real.calendar.CalendarConnectionScreen(
                        state = calendarConnection,
                        reminders = reminders,
                        reduceDynamicBlur = reduceDynamicBlur,
                        onBack = { showCalendarConnection = false }
                    )
                } else SettingsScreen(
                    reduceDynamicBlur = reduceDynamicBlur,
                    onReduceDynamicBlurChange = onReduceDynamicBlurChange,
                    reduceAnimations = reduceAnimations,
                    onReduceAnimationsChange = onReduceAnimationsChange,
                    realPetEnabled = realPetEnabled,
                    onRealPetEnabledChange = onRealPetEnabledChange,
                    designStyle = designStyle,
                    onDesignStyleChange = onDesignStyleChange,
                    notificationsAllowed = notificationsAllowed,
                    onRequestNotifications = onRequestNotifications,
                    exactAlarmsAllowed = exactAlarmsAllowed, onRequestExactAlarms = onRequestExactAlarms,
                    onPetScenarioTest = { kind -> RealPetRuntime.requestScenarioTest(kind) },
                    onClearAllMoods = onClearAllMoods,
                    widgetReminders = reminders,
                    widgetMoodEntries = remember(homeMoodEntries, reminders) {
                        homeMoodEntries + reminders.mapNotNull { card ->
                            card.mood?.let { mood ->
                                MoodTrackerEntry(Long.MIN_VALUE + card.id, mood,
                                    card.moodDetails ?: MoodDetails(scope = MoodScope.DAILY, loggedAt = card.updatedAt))
                            }
                        }
                    },
                    onToggleWidgetReminder = onToggleCompleted,
                    onOpenCalendarConnection = { showCalendarConnection = true },
                    onBack = { showSettings = false }
                )
            } else {
                @OptIn(ExperimentalSharedTransitionApi::class)
                SharedTransitionLayout {
                    val targetRoute = ReminderRoute(category, customCategoryId, detailId)
                    LaunchedEffect(targetRoute) {
                        if (routeSeekState.targetState != targetRoute || routeSeekState.currentState != targetRoute) {
                            routeSeekState.animateTo(targetRoute)
                        }
                    }
                    val routeTransition = rememberTransition(routeSeekState, label = "reminderPage")
                    routeTransition.AnimatedContent(
                        contentKey = { it.key },
                        transitionSpec = {
                            if (reduceAnimations) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                val isOpeningDetail = initialState.detailId == null && targetState.detailId != null
                                val isClosingDetail = initialState.detailId != null && targetState.detailId == null
                                val isClosingCategoryToHome =
                                    initialState.detailId == null &&
                                    (initialState.category != null || initialState.customCategoryId != null) &&
                                    targetState.detailId == null &&
                                    targetState.category == null &&
                                    targetState.customCategoryId == null

                                when {
                                    isOpeningDetail -> {
                                        fadeIn(
                                            tween(260, easing = RealMotion.Standard)
                                        ) togetherWith fadeOut(
                                            tween(180, easing = RealMotion.Exit)
                                        )
                                    }
                                    isClosingDetail && !hasReturnCard(targetState, initialState.detailId) -> {
                                        fadeIn(tween(160)) togetherWith fadeOut(tween(160))
                                    }
                                    isClosingDetail -> {
                                        fadeIn(
                                            tween(200, easing = RealMotion.Standard)
                                        ) togetherWith fadeOut(
                                            tween(240, easing = RealMotion.Exit)
                                        )
                                    }
                                    isClosingCategoryToHome -> {
                                        val enter = fadeIn(
                                            tween(220, easing = RealMotion.Standard)
                                        ) + scaleIn(
                                            tween(240, easing = RealMotion.Standard),
                                            initialScale = 0.98f
                                        )
                                        val exit = fadeOut(
                                            tween(180, easing = RealMotion.Exit)
                                        ) + scaleOut(
                                            tween(200, easing = RealMotion.Exit),
                                            targetScale = 0.98f
                                        )
                                        enter togetherWith exit
                                    }
                                    else -> {
                                        val enter = fadeIn(
                                            tween(220, easing = RealMotion.Standard)
                                        ) + scaleIn(
                                            tween(240, easing = RealMotion.Standard),
                                            initialScale = 0.98f
                                        ) + slideInVertically(
                                            tween(240, easing = RealMotion.Standard)
                                        ) { it / 36 }

                                        val exit = fadeOut(
                                            tween(170, easing = RealMotion.Exit)
                                        ) + scaleOut(
                                            tween(200, easing = RealMotion.Exit),
                                            targetScale = 0.98f
                                        )

                                        enter togetherWith exit
                                    }
                                }
                            }
                        }
                    ) reminderPageContent@ { route ->
                        stateHolder.SaveableStateProvider(route.key) {
                            val currentReminder = reminders.firstOrNull { it.id == route.detailId }
                            
                            var lastReminder by remember(route.detailId) { mutableStateOf(currentReminder) }
                            LaunchedEffect(currentReminder) {
                                if (currentReminder != null) lastReminder = currentReminder
                            }
                            val reminder = currentReminder ?: lastReminder
                            if (route.detailId != null && reminder != null) {
                                ReminderDetailScreen(
                                    reminder = reminder,
                                    calendarSyncPublished = calendarSyncStatus(reminder),
                                    onCalendarSyncClick = { calendarConnection.offerPublication(reminder.id) },
                                    onRemoveGameBlock = { blockId -> onRemoveGameBlock(reminder.id, blockId) },
                                    onAddGameBlock = { block -> onAddGameBlock(reminder.id, block) },
                                    incomingLocation = pendingLocationShare?.takeIf { it.reminderId == reminder.id }?.location,
                                    onIncomingLocationHandled = onLocationShareHandled,
                                    onRequestLocationShare = { onRequestLocationShare(reminder.id) },
                                    onResolveLocationPreview = { onResolveLocationPreview(reminder.id) },
                                    onBack = {
                                        detailId = null
                                    },
                                    onToggleCompleted = {
                                        onToggleCompleted(reminder.id)
                                    },
                                    onToggleImportant = { onToggleImportant(reminder.id) },
                                    onToggleChecklistItem = onToggleChecklistItem,
                                    onSave = { text, accentStart, accentEnd, customCatId, checklistItems, starBorderColorIndex, gradientBlendFraction, customSolidColorArgb, attachments, voiceRecordings, location, referenceUrl, musicBlocks, mediaBlocks, cardStack, contentBlockOrder, contentBlockLayout, useDarkText ->
                                        val saved = onEditReminder(reminder.id, text, accentStart, accentEnd, customCatId, checklistItems, starBorderColorIndex, gradientBlendFraction, customSolidColorArgb, attachments, voiceRecordings, location, referenceUrl, musicBlocks, mediaBlocks, cardStack, contentBlockOrder, contentBlockLayout, useDarkText)
                                        if (saved) calendarConnection.offerPublication(reminder.id)
                                        saved
                                    },
                                    onSaveJournal = { mood, text -> onSetJournal(reminder.id, mood, text) },
                                    onDeleteJournal = { onDeleteJournal(reminder.id) },
                                    onAddMoodCard = { mood, details -> onSetMood(reminder.id, mood, details) },
                                    onDelete = { onDeleteReminder(reminder.id); detailId = null },
                                    // No Planner day here: a repeating card targets its next surviving occurrence through the same
                                    // occurrence-aware delete. Non-repeating cards (null) keep the plain whole-card delete.
                                    repeatOccurrenceMillis = if (reminder.hasRepeat) com.cagri.real.data.nextDeletableOccurrence(reminder, System.currentTimeMillis()) else null,
                                    repeatOccurrenceIsNext = true,
                                    onDeleteRepeatOccurrence = { scope ->
                                        com.cagri.real.data.nextDeletableOccurrence(reminder, System.currentTimeMillis())
                                            ?.let { onDeleteReminderOccurrence(reminder.id, it, scope) }
                                        detailId = null
                                    },
                                    customCategories = customCategories,
                                    reduceAnimations = reduceAnimations, reduceDynamicBlur = reduceDynamicBlur,
                                    designStyle = designStyle, onSetAlert = { at, rule -> onSetAlert(reminder.id, at, rule) },
                                    exactAlarmsAllowed = exactAlarmsAllowed, onRequestExactAlarms = onRequestExactAlarms,
                                    notificationsAllowed = notificationsAllowed,
                                    onRequestNotificationPermission = onRequestNotificationPermission,
                                    sharedTransitionScope = if (
                                        targetRoute.detailId == null &&
                                        !hasReturnCard(targetRoute, route.detailId)
                                    ) null else this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@reminderPageContent,
                                    pullDismiss = if (!reduceAnimations && route.detailId == detailId) detailPullDismiss else null
                                )
                            } else {
                                val currentCustomCategory = customCategories.firstOrNull { it.id == route.customCategoryId }
                                val currentCategoryKey = route.customCategoryId?.let { "custom_$it" } ?: route.category?.let { "cat_${it.name}" }
                                ReminderListScreen(
                                    pendingPanoBoardId = pendingPanoBoardId,
                                    onPanoLocationHandled = onPanoLocationHandled,
                                    reminders = reminders, category = route.category,
                                    customCategory = currentCustomCategory,
                                    dividers = currentCustomCategory?.let { c -> dividers.filter { it.customCategoryId == c.id } }.orEmpty(),
                                    onAddDivider = onAddDivider, onEditDivider = onEditDivider, onDeleteDivider = onDeleteDivider,
                                    onSetCompletionNote = onSetCompletionNote, onSetCheckRequest = onSetCheckRequest,
                                    categoryColorFilter = currentCategoryKey?.let { categoryColorFilters[it] },
                                    onCategoryColorFilterChange = { selected ->
                                        if (currentCategoryKey != null) {
                                            if (selected != null) {
                                                categoryColorFilters[currentCategoryKey] = selected
                                            } else {
                                                categoryColorFilters.remove(currentCategoryKey)
                                            }
                                        }
                                    },
                                    builtInCategoryColors = builtInCategoryColors,
                                    onUpdateBuiltInCategoryColor = onUpdateBuiltInCategoryColor,
                                    draft = draft,
                                    alertAt = draftAlertAt, repeatRule = draftRepeatRule,
                                    onAlertChange = { at, rule ->
                                        draftAlertAt = at; draftRepeatRule = rule
                                        if (at == null) ComposerTimingDraft.clearTiming()
                                    },
                                    exactAlarmsAllowed = exactAlarmsAllowed, onRequestExactAlarms = onRequestExactAlarms,
                                    notificationsAllowed = notificationsAllowed,
                                    onRequestNotificationPermission = onRequestNotificationPermission,
                                    onDraftChange = { newDraft ->
                                        draft = newDraft
                                        RealPetKeywords.onComposerDraftChanged(
                                            newText = newDraft,
                                            isKeywordsEnabled = RealPetRuntime.keywordsEnabled.value
                                        )
                                    },
                                    onBack = {
                                        category = null
                                        customCategoryId = null
                                    },
                                    onOpenCategory = {
                                        category = it
                                        customCategoryId = null
                                    },
                                    onOpenReminder = { if (detailId == null) detailId = it },
                                    plannerDetailContent = { plannerReminderId, plannerOccurrenceMillis, closePlannerDetail ->
                                        val plannerReminder = reminders.firstOrNull { it.id == plannerReminderId }
                                        if (plannerReminder != null) {
                                            ReminderDetailScreen(
                                                reminder = plannerReminder,
                                                calendarSyncPublished = calendarSyncStatus(plannerReminder),
                                                onCalendarSyncClick = { calendarConnection.offerPublication(plannerReminder.id) },
                                                onRemoveGameBlock = { blockId -> onRemoveGameBlock(plannerReminder.id, blockId) },
                                                onAddGameBlock = { block -> onAddGameBlock(plannerReminder.id, block) },
                                                incomingLocation = pendingLocationShare?.takeIf { it.reminderId == plannerReminder.id }?.location,
                                                onIncomingLocationHandled = onLocationShareHandled,
                                                onRequestLocationShare = { onRequestLocationShare(plannerReminder.id) },
                                                onResolveLocationPreview = { onResolveLocationPreview(plannerReminder.id) },
                                                onBack = closePlannerDetail,
                                                onToggleCompleted = { onToggleCompleted(plannerReminder.id) },
                                                onToggleImportant = { onToggleImportant(plannerReminder.id) },
                                                onToggleChecklistItem = onToggleChecklistItem,
                                                onSave = { text, accentStart, accentEnd, customCatId, checklistItems, starBorderColorIndex, gradientBlendFraction, customSolidColorArgb, attachments, voiceRecordings, location, referenceUrl, musicBlocks, mediaBlocks, cardStack, contentBlockOrder, contentBlockLayout, useDarkText ->
                                                    val saved = onEditReminder(plannerReminder.id, text, accentStart, accentEnd, customCatId, checklistItems, starBorderColorIndex, gradientBlendFraction, customSolidColorArgb, attachments, voiceRecordings, location, referenceUrl, musicBlocks, mediaBlocks, cardStack, contentBlockOrder, contentBlockLayout, useDarkText)
                                                    if (saved) calendarConnection.offerPublication(plannerReminder.id)
                                                    saved
                                                },
                                                onSaveJournal = { mood, text -> onSetJournal(plannerReminder.id, mood, text) },
                                                onDeleteJournal = { onDeleteJournal(plannerReminder.id) },
                                                onAddMoodCard = { mood, details -> onSetMood(plannerReminder.id, mood, details) },
                                                onDelete = {
                                                    onDeleteReminder(plannerReminder.id)
                                                    closePlannerDetail()
                                                },
                                                repeatOccurrenceMillis = plannerOccurrenceMillis,
                                                onDeleteRepeatOccurrence = { scope ->
                                                    plannerOccurrenceMillis?.let { onDeleteReminderOccurrence(plannerReminder.id, it, scope) }
                                                    closePlannerDetail()
                                                },
                                                customCategories = customCategories,
                                                reduceAnimations = reduceAnimations,
                                                reduceDynamicBlur = reduceDynamicBlur,
                                                designStyle = designStyle,
                                                onSetAlert = { at, rule -> onSetAlert(plannerReminder.id, at, rule) },
                                                exactAlarmsAllowed = exactAlarmsAllowed,
                                                onRequestExactAlarms = onRequestExactAlarms,
                                                notificationsAllowed = notificationsAllowed,
                                                onRequestNotificationPermission = onRequestNotificationPermission
                                            )
                                        }
                                    },
                                    onPlannerSetAlert = { id, at, rule -> onSetAlert(id, at, rule) },
                                    onToggleCompleted = onToggleCompleted,
                                    onMoveTo24Hour = onMoveTo24Hour,
                                    onSetRepeatPattern = onSetRepeatPattern,
                                    onVoiceEdit = onVoiceEdit,
                                    voiceCardCallbacks = remember(onVoiceSkipOccurrence, onVoiceBulkShift, onVoiceAddChecklistItems, onDeleteReminderOccurrence) {
                                        VoiceCardCallbacks(onVoiceSkipOccurrence, onVoiceBulkShift, onVoiceAddChecklistItems, onDeleteReminderOccurrence)
                                    },
                                    onDeleteReminders = onDeleteReminders,
                                    onBulkSetCategory = onBulkSetCategory,
                                    homeMoodEntries = homeMoodEntries,
                                    onAddHomeMood = onAddHomeMood,
                                    reduceDynamicBlur = reduceDynamicBlur,
                                    reduceAnimations = reduceAnimations,
                                    designStyle = designStyle,
                                    onOpenSettings = { showSettings = true },
                                    customCategories = customCategories,
                                    onCreateCategory = onCreateCategory,
                                    onCreateVoiceCategory = onCreateVoiceCategory,
                                    onEditCategory = onEditCategory,
                                     onDeleteCategory = { id, deleteReminders ->
                                         onDeleteCategory(id, deleteReminders)
                                         customCategoryId = null
                                     },
                                     onDeleteCategories = onDeleteCategories,
                                     onOpenCustomCategory = { customCategoryItem ->
                                         category = null
                                         customCategoryId = customCategoryItem.id
                                         onOpenCustomCategory(customCategoryItem)
                                     },
                                     onCreateJournal = { journalTitle, journalText ->
                                         // Ana ekrandaki yazı taslağına/kategoriye dokunmadan ham ekleme.
                                         val journalId = onAddReminder(journalTitle, null, RecurrenceRule.NONE, null, false,
                                             emptyList(), false, null, emptyList(), emptyList(), null, null, null, emptyList(), emptyList())
                                         if (journalId != null) { onSetJournal(journalId, null, journalText); true } else false
                                     },
                                     onAddSuggestedReal = onAddSuggestedReal,
                                     onAddGame = onAddGame,
                                     onUndoSuggestedReal = onUndoSuggestedReal,
                                     onAddReminder = { text, at, rule, catId, isChecklist, checklistItems, sendImmediateNotification, refUrl, attachments, voiceRecordings, location, plannedDateEpochDay, quick24HourUntilAt, musicBlocks, mediaBlocks ->
                                         val createdId = onAddReminder(text, at, rule, catId, isChecklist, checklistItems, sendImmediateNotification, refUrl, attachments, voiceRecordings, location, plannedDateEpochDay, quick24HourUntilAt, musicBlocks, mediaBlocks)
                                         if (createdId != null) {
                                             draft = ""; draftAlertAt = null; draftRepeatRule = RecurrenceRule.NONE; draftReferenceUrl = null; isShareDraft = false
                                             if (category != null) {
                                                 if (catId != null) {
                                                     category = null
                                                     customCategoryId = catId
                                                 } else if (category != ReminderCategory.Today) {
                                                     category = if (at == null && plannedDateEpochDay == null) ReminderCategory.NoAlert else ReminderCategory.Planned
                                                 }
                                             }
                                         }
                                         createdId
                                     },
                                     isShareDraft = isShareDraft,
                                     onShareDraftChange = { isShareDraft = it },
                                     draftReferenceUrl = draftReferenceUrl,
                                     onDraftReferenceUrlChange = { draftReferenceUrl = it },
                                     draftAttachments = draftAttachments,
                                     onDraftAttachmentsChange = { draftAttachments = it },
                                     categorySharedTransitionsEnabled = true,
                                     sharedTransitionScope = this@SharedTransitionLayout,
                                     animatedVisibilityScope = this@reminderPageContent,
                                     userName = userName,
                                     isFirstHomeIntro = isFirstHomeIntro,
                                     onFirstHomeIntroComplete = onFirstHomeIntroComplete,
                                     isSettingsOpen = showSettings,
                                     onRealPetHomeSurfaceActiveChange = { listPetSurfaceActive = it },
                                     categoryOrderKeys = categoryOrderKeys,
                                     onCategoryOrderChange = { orderedKeys ->
                                         categoryOrderKeys = orderedKeys
                                         categoryUiPreferences.edit()
                                             .putString("category_order_v1", encodeCategoryOrderPreference(orderedKeys))
                                             .let { com.cagri.real.platform.android.sync.SyncLocal.saveUiPreference(categoryUiPreferences, it) }
                                     },
                                     onPickAttachment = onPickAttachment,
                                     onPlannerOpened = calendarConnection::refresh
                                 )
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class PendingNotificationAction {
    NONE,
    ENABLE_BELL,
    OPEN_DATE_PICKER
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ReminderListScreen(
    pendingPanoBoardId: String? = null, onPanoLocationHandled: () -> Unit = {},
    reminders: List<ReminderItem>, category: ReminderCategory?, draft: String,
    dividers: List<ReminderItem> = emptyList(),
    onAddDivider: (Long, String) -> Unit = { _, _ -> },
    onEditDivider: (Long, String) -> Unit = { _, _ -> },
    onDeleteDivider: (Long) -> Unit = {},
    /** Tamamlama notu on a completed goal (category goal screen). */
    onSetCompletionNote: (Long, String?) -> Unit = { _, _ -> },
    /** Kontrol ettir on the selected cards (null takes it off). */
    onSetCheckRequest: (Set<Long>, com.cagri.real.data.CheckRequest?) -> Unit = { _, _ -> },
    alertAt: Long?, repeatRule: RecurrenceRule, onAlertChange: (Long?, RecurrenceRule) -> Unit,
    exactAlarmsAllowed: Boolean, onRequestExactAlarms: () -> Unit,
    onDraftChange: (String) -> Unit, onBack: () -> Unit,
    onOpenCategory: (ReminderCategory) -> Unit, onOpenReminder: (Long) -> Unit,
    plannerDetailContent: @Composable (Long, Long?, () -> Unit) -> Unit = { _, _, _ -> },
    onPlannerSetAlert: (Long, Long, RecurrenceRule) -> Unit = { _, _, _ -> },
    onToggleCompleted: (Long) -> Unit,
    onDeleteReminders: (Set<Long>) -> Unit = {},
    onDeleteCategories: (Set<Long>) -> Unit = {},
    onBulkSetCategory: (Set<Long>, Long?) -> Unit = { _, _ -> },
    homeMoodEntries: List<MoodTrackerEntry> = emptyList(),
    onAddHomeMood: (JournalMood, MoodDetails) -> Unit = { _, _ -> },
    onCreateJournal: suspend (String, String) -> Boolean = { _, _ -> false },
    reduceDynamicBlur: Boolean,
    reduceAnimations: Boolean, designStyle: DesignStyle,
    onOpenSettings: () -> Unit, onAddReminder: suspend (String, Long?, RecurrenceRule, Long?, Boolean, List<ChecklistItem>, Boolean, String?, List<ReminderAttachment>, List<ReminderVoiceRecording>, ReminderLocation?, Long?, Long?, List<MusicBlock>, List<MediaBlock>) -> Long?,
    onMoveTo24Hour: suspend (Long) -> Boolean = { false },
    onSetRepeatPattern: (Long, Int, Long?) -> Unit = { _, _, _ -> },
    onVoiceEdit: (Long, ReminderEditPatch) -> Unit = { _, _ -> },
    voiceCardCallbacks: VoiceCardCallbacks = VoiceCardCallbacks(),
    onAddSuggestedReal: suspend (String, MusicBlock?, MediaBlock?, CardStackBlock?) -> Long? = { _, _, _, _ -> null },
    onUndoSuggestedReal: suspend (Long) -> Boolean = { false },
    onAddGame: suspend (com.cagri.real.data.CardGameBlock) -> Long? = { null },
    isShareDraft: Boolean = false,
    onShareDraftChange: (Boolean) -> Unit = {},
    draftReferenceUrl: String? = null,
    onDraftReferenceUrlChange: (String?) -> Unit = {},
    draftAttachments: List<ReminderAttachment> = emptyList(),
    onDraftAttachmentsChange: (List<ReminderAttachment>) -> Unit = {},
    customCategories: List<CustomCategory> = emptyList(),
    customCategory: CustomCategory? = null,
    builtInCategoryColors: Map<ReminderCategory, CategoryColorOverride> = emptyMap(),
    onUpdateBuiltInCategoryColor: (ReminderCategory, CategoryColorOverride?) -> Unit = { _, _ -> },
    onCreateCategory: (String, String, Int, Int?, Boolean, Boolean, Float?, Int?, String?) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onCreateVoiceCategory: suspend (String, String, Int) -> CustomCategory?,
    onEditCategory: (Long, String, String, Int, Int?, Boolean, Boolean, Float?, Int?) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onDeleteCategory: (Long, Boolean) -> Unit = { _, _ -> },
    onOpenCustomCategory: (CustomCategory) -> Unit = {},
    notificationsAllowed: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
    categorySharedTransitionsEnabled: Boolean = true,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    userName: String = "",
    isFirstHomeIntro: Boolean = false,
    onFirstHomeIntroComplete: () -> Unit = {},
    isSettingsOpen: Boolean = false,
    onRealPetHomeSurfaceActiveChange: (Boolean) -> Unit = {},
    categoryOrderKeys: List<String> = emptyList(),
    onCategoryOrderChange: (List<String>) -> Unit = {},
    categoryColorFilter: Int? = null,
    onCategoryColorFilterChange: (Int?) -> Unit = {},
    onPickAttachment: () -> Unit = {},
    /** Takvim ekranı açılınca bir kez eşitleme ister (arka planda sürekli eşitleme yok). */
    onPlannerOpened: () -> Unit = {}
) {
    val composerState = rememberReminderComposerState(category)
    val voiceState = rememberReminderVoiceState()
    var editing by composerState.editing
    val latestEditing = rememberUpdatedState(editing)
    // Sade kartlar (Ayarlar): home and every category, not the journal list.
    val plainHomeCards = category != ReminderCategory.JournalEntries && com.cagri.real.ui.components.HomeCardStyle.isPlain(LocalContext.current)
    var editingBuiltInCategory by rememberSaveable { mutableStateOf<ReminderCategory?>(null) }
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
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }
    var showComposerLocation by remember { mutableStateOf(false) }
    var showAlarmPermission by remember { mutableStateOf(false) }
    var showOverPhoneAsk by remember { mutableStateOf(false) }
    // Composer pin: plain place or "Konuma varınca hatırlat".
    var showLocationChoice by remember { mutableStateOf(false) }
    var locationPickForTrigger by remember { mutableStateOf(false) }
    /** A place reminder is waiting, so location is being watched: the pin shows a small dot. */
    val locationWatching = remember(reminders) { com.cagri.real.notifications.LocationReminders.active(reminders).isNotEmpty() }
    var showBulkCategoryPicker by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by composerState.showDatePicker
    var showNotificationPermissionSheet by rememberSaveable { mutableStateOf(false) }
    var pendingNotificationAction by rememberSaveable { mutableStateOf(PendingNotificationAction.NONE) }
    var showMicrophonePermissionSheet by rememberSaveable { mutableStateOf(false) }
    var pendingMicrophoneAction by rememberSaveable { mutableStateOf(false) }
    var showHomeMood by rememberSaveable { mutableStateOf(false) }
    var showMoodTrackerHistory by rememberSaveable { mutableStateOf(false) }
    var showPano by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pendingPanoBoardId) { if (pendingPanoBoardId != null) showPano = true }
    var showHomeWidgets by rememberSaveable { mutableStateOf(false) }
    // Yaşam ✎: bağımsız "bugün yaz" sayfası (yeni kart + günlük).
    var showYasamJournal by rememberSaveable { mutableStateOf(false) }
    // Home context card -> Widget screen deep link ("books" / "media"); consumed by HomeWidgetScreen.
    var widgetDestination by remember { mutableStateOf<String?>(null) }
    var moodOpenedFromContextCard by remember { mutableStateOf(false) }
    // Kitaplar ekranı açıkken kök kutu navigation bar boşluğu bırakmaz; kitap ekranı alta kadar çizilir.
    var booksOpen by remember { mutableStateOf(false) }
    var returnToHomeWidgetsAfterMood by rememberSaveable { mutableStateOf(false) }
    // Yaşam ⊕ ile açılan mood ekleme: geri Yaşam'a döner (geçmiş açılmaz). Kayıt/dönüşte false olur.
    var moodAddFromYasam by rememberSaveable { mutableStateOf(false) }
    /** Yaşam comes back after a mood flow it opened: no opening animation. */
    var yasamQuickReturn by rememberSaveable { mutableStateOf(false) }
    // Yaşam > Günlüklerim'den bir günlük kartının detayı açıldı: detay kapanınca Yaşam aynı modda geri gelir.
    var returnToYasamJournals by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (returnToYasamJournals) showHomeWidgets = true }
    var showPlanner by rememberSaveable { mutableStateOf(false) }
    /** Set when the planner is opened from a category's "Hedefler" button: it opens on that category's goals. */
    var plannerGoalCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(showPlanner) { if (showPlanner) onPlannerOpened() }
    val systemCategoryEnter: EnterTransition = if (reduceAnimations) {
        EnterTransition.None
    } else {
        fadeIn(tween(220)) +
            slideInVertically(tween(260, easing = RealMotion.Emphasized)) { it / 14 } +
            scaleIn(initialScale = 0.985f, animationSpec = tween(260, easing = RealMotion.Emphasized))
    }
    val systemCategoryExit: ExitTransition = if (reduceAnimations) {
        ExitTransition.None
    } else {
        fadeOut(tween(180)) +
            slideOutVertically(tween(220, easing = RealMotion.Exit)) { it / 14 } +
            scaleOut(targetScale = 0.985f, animationSpec = tween(220, easing = RealMotion.Exit))
    }
    var showMoodSuggestions by remember { mutableStateOf(false) }
    var activeSuggestionMood by remember { mutableStateOf<JournalMood?>(null) }
    var activeSuggestionDetails by remember { mutableStateOf<MoodDetails?>(null) }
    var homeMoodSelection by remember { mutableStateOf(homeMoodEntries.firstOrNull()?.mood ?: JournalMood.NEUTRAL) }
    var refocus by composerState.refocus
    var voiceAiParsing by voiceState.voiceAiParsing
    var voiceAiRequestSerial by voiceState.voiceAiRequestSerial
    // Home voice flow: once the AI answer has been applied, the reminder is saved automatically.
    var pendingAutoSubmit by voiceState.pendingAutoSubmit
    var pendingVoiceDurationMinutes by voiceState.pendingVoiceDurationMinutes
    var pendingVoicePeople by voiceState.pendingVoicePeople
    var pendingVoiceLocation by voiceState.pendingVoiceLocation
    var pendingVoiceRepeatMask by voiceState.pendingVoiceRepeatMask
    var pendingVoiceRepeatUntil by voiceState.pendingVoiceRepeatUntil
    // Card the next spoken follow-up ("onu 8'e al") refers to; expires after LAST_CARD_WINDOW_MILLIS.
    var lastVoiceCardId by voiceState.lastVoiceCardId
    var lastVoiceCardAt by voiceState.lastVoiceCardAt
    // Bulk voice command waiting for the user's confirmation chip. Nothing is applied before "Uygula".
    var pendingBulkPlan by voiceState.pendingBulkPlan
    var pendingVoiceMusicBlocks by voiceState.pendingVoiceMusicBlocks
    var pendingVoiceMediaBlocks by voiceState.pendingVoiceMediaBlocks

    val reminderMoodTrackerEntries = remember(reminders) {
        reminders.mapNotNull { reminder ->
            val mood = reminder.mood ?: return@mapNotNull null
            MoodTrackerEntry(
                id = Long.MIN_VALUE + reminder.id,
                mood = mood,
                details = reminder.moodDetails ?: MoodDetails(
                    scope = MoodScope.DAILY,
                    loggedAt = reminder.updatedAt
                )
            )
        }
    }
    val allMoodTrackerEntries = remember(homeMoodEntries, reminderMoodTrackerEntries) {
        (homeMoodEntries + reminderMoodTrackerEntries).sortedByDescending { it.details.loggedAt }
    }

    LaunchedEffect(notificationsAllowed) {
        if (notificationsAllowed && pendingNotificationAction != PendingNotificationAction.NONE) {
            when (pendingNotificationAction) {
                PendingNotificationAction.ENABLE_BELL -> {
                    sendImmediateNotification = true
                    refocus = true
                }
                PendingNotificationAction.OPEN_DATE_PICKER -> showDatePicker = true
                PendingNotificationAction.NONE -> {}
            }
            pendingNotificationAction = PendingNotificationAction.NONE
        }
    }

    var showCreateCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var showEditCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var showPeopleSheet by rememberSaveable { mutableStateOf(false) }
    var isColorFilterExpanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val plannerRepository = remember(context) {
        PlannerRepository(com.cagri.real.platform.android.sync.SyncLocal.preferences(context, "real_planner_v1", Context.MODE_PRIVATE))
    }
    val uiPreferences = remember { com.cagri.real.platform.android.sync.SyncLocal.preferences(context, "real_ui", Context.MODE_PRIVATE) }
    var homeCategoryPreviewCount by rememberSaveable {
        mutableIntStateOf(uiPreferences.getInt("home_category_preview_count_v1", 4).coerceAtLeast(1))
    }
    var sortOrder by rememberSaveable {
        val saved = uiPreferences.getString("sort_order", null)
        mutableStateOf(
            when (saved) {
                "reminder_time" -> ReminderSortOrder.REMINDER_TIME
                "oldest_first" -> ReminderSortOrder.OLDEST_FIRST
                else -> ReminderSortOrder.RECENTLY_ADDED
            }
        )
    }
    // Ayraç: "Ayraç ekle" in the ••• menu, or a long press on one → title dialog.
    var dividerAdding by remember { mutableStateOf(false) }
    var dividerEditing by remember { mutableStateOf<ReminderItem?>(null) }
    if (dividerAdding || dividerEditing != null) {
        com.cagri.real.ui.components.DividerTitleDialog(
            initial = dividerEditing?.text.orEmpty(), editing = dividerEditing != null,
            onDismiss = { dividerAdding = false; dividerEditing = null },
            onSave = { title ->
                val editing = dividerEditing
                if (editing != null) onEditDivider(editing.id, title) else customCategory?.let { onAddDivider(it.id, title) }
                dividerAdding = false; dividerEditing = null
            }
        )
    }
    // Category screens have their own 3-way sort (newest on top / newest at the bottom / nearest date).
    var categorySortOrder by rememberSaveable {
        mutableStateOf(
            when (uiPreferences.getString("category_sort_order", null)) {
                "oldest_first" -> ReminderSortOrder.OLDEST_FIRST
                "nearest_date" -> ReminderSortOrder.NEAREST_DATE
                else -> ReminderSortOrder.RECENTLY_ADDED
            }
        )
    }
    DisposableEffect(uiPreferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "home_category_preview_count_v1" -> homeCategoryPreviewCount = uiPreferences.getInt(key, 4).coerceAtLeast(1)
                "sort_order" -> sortOrder = when (uiPreferences.getString(key, null)) {
                    "reminder_time" -> ReminderSortOrder.REMINDER_TIME
                    "oldest_first" -> ReminderSortOrder.OLDEST_FIRST
                    else -> ReminderSortOrder.RECENTLY_ADDED
                }
                "category_sort_order" -> categorySortOrder = when (uiPreferences.getString(key, null)) {
                    "oldest_first" -> ReminderSortOrder.OLDEST_FIRST
                    "nearest_date" -> ReminderSortOrder.NEAREST_DATE
                    else -> ReminderSortOrder.RECENTLY_ADDED
                }
            }
        }
        uiPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { uiPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val effectiveSortOrder = if (category != null || customCategory != null) categorySortOrder else sortOrder
    var homeSortHint by remember { mutableStateOf<Pair<Int, String>?>(null) }
    LaunchedEffect(homeSortHint?.first) {
        if (homeSortHint != null) { kotlinx.coroutines.delay(1600); homeSortHint = null }
    }
    var showCategoryProgress by rememberSaveable(category?.name, customCategory?.id) { mutableStateOf(false) }
    /** "Bu kategoride ara" in the category header: filters the category's cards while typing. */
    var categoryQuery by rememberSaveable(category?.name, customCategory?.id) { mutableStateOf("") }
    /** Home: "Kartlarım" swiped left searches the cards (HomeCardsSearchTitle); the categories step aside meanwhile. */
    var homeSearchOpen by rememberSaveable { mutableStateOf(false) }
    var homeQuery by rememberSaveable { mutableStateOf("") }
    var saving by composerState.saving
    val whatsAppConsentRepo = remember { WhatsAppConsentRepository(context) }
    val whatsAppRecipientRepo = remember { WhatsAppRecipientRepository(context) }
    var isWhatsAppArmed by composerState.isWhatsAppArmed
    var isLabsOpen by composerState.isLabsOpen
    var showWhatsAppConsentSheet by rememberSaveable { mutableStateOf(false) }
    var showWhatsAppRecipientSheet by rememberSaveable { mutableStateOf(false) }
    val onToggleWhatsApp: () -> Unit = {
        if (!whatsAppConsentRepo.hasConsent()) {
            showWhatsAppConsentSheet = true
        } else if (!whatsAppRecipientRepo.hasRecipient()) {
            showWhatsAppRecipientSheet = true
        } else {
            isWhatsAppArmed = !isWhatsAppArmed
        }
    }
    val view = LocalView.current
    var showAccountMenu by rememberSaveable { mutableStateOf(false) }
    var showCategoryOrderEditor by rememberSaveable { mutableStateOf(false) }
    var composerDurationMinutes by composerState.composerDurationMinutes
    val inputFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val scope = rememberCoroutineScope()
    var copyingPhotos by composerState.copyingPhotos
    var photoImportGeneration by composerState.photoImportGeneration
    val attachmentActions = rememberReminderAttachmentActions(context, scope, composerState,
        draftAttachments, onDraftAttachmentsChange)
    val handlePickComposerAttachment = attachmentActions.pick
    val handleRemoveDraftAttachment = attachmentActions.remove

    val requestPermissionForAction: (PendingNotificationAction) -> Unit = { action ->
        if (!notificationsAllowed) {
            focusManager.clearFocus()
            keyboard?.hide()
            pendingNotificationAction = action
            showNotificationPermissionSheet = true
        } else {
            when (action) {
                PendingNotificationAction.ENABLE_BELL -> {
                    sendImmediateNotification = true
                }
                PendingNotificationAction.OPEN_DATE_PICKER -> {
                    focusManager.clearFocus()
                    keyboard?.hide()
                    showDatePicker = true
                }
                PendingNotificationAction.NONE -> {}
            }
        }
    }
    val listState = rememberLazyListState()
    val extendLiquidArea by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val scene = rememberGraphicsLayer()
    var scenePosition by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(customCategory) {
        if (customCategory != null) {
            draftCategoryId = customCategory.id
        }
    }

    LaunchedEffect(category) {
        if (category == ReminderCategory.Today && alertAt == null) {
            // 24 hour is a rolling focus bucket, not a calendar-day reminder.
            pendingDateHintEpochDay = null
            requiresTimeSelection = false
            pendingTimeOfDay = null
            manualDateClearedOrChanged = false
        }
    }

    val entrance = rememberReminderEntrance(category, customCategory, reduceAnimations,
        isFirstHomeIntro, onFirstHomeIntroComplete)
    val shouldAnimateColdStart = entrance.shouldAnimateColdStart
    var isColdStarting by entrance.isColdStarting
    val coldStartProgress = entrance.coldStartProgress
    var containerCoordinates by entrance.containerCoordinates
    var headerCoordinates by entrance.headerCoordinates
    var isFirstHomeIntroPlaying by entrance.isFirstHomeIntroPlaying
    val categoryEntranceProgress = entrance.categoryEntranceProgress
    val composerEntranceProgress = entrance.composerEntranceProgress
    var isHomeReadyForShimmer by entrance.isHomeReadyForShimmer

    val entryTracker = remember { ReminderEntryTracker(scope) }

    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectedCategoryIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val isSelectionMode = selectedIds.isNotEmpty()
    // Kontrol ettir: people to ask (members of the selected cards' one shared category, else only yourself).
    var checkTargets by remember { mutableStateOf<List<com.cagri.real.ui.components.CheckTarget>?>(null) }
    val checkScope = rememberCoroutineScope()
    fun openCheckSheet() {
        val me = com.cagri.real.platform.android.auth.AndroidBackend.auth.currentUser()?.takeIf { !it.isAnonymous }?.id
        val categories = reminders.filter { it.id in selectedIds }.map { it.customCategoryId }.toSet()
        val shared = categories.singleOrNull()?.let { id -> customCategories.firstOrNull { it.id == id } ?: customCategory?.takeIf { it.id == id } }?.takeIf { it.sharedId != null }
        val self = com.cagri.real.ui.components.CheckTarget(if (shared != null && me != null) me else com.cagri.real.data.CheckRequest.ME, "Kendime")
        if (shared == null || me == null) { checkTargets = listOf(self); return }
        checkScope.launch {
            val others = runCatching { com.cagri.real.sharedcategories.SharedCategorySync.members(shared) }.getOrDefault(emptyList())
                .filter { it.uid != me }.map { com.cagri.real.ui.components.CheckTarget(it.uid, it.name) }
            checkTargets = listOf(self) + others + if (others.size > 1) listOf(com.cagri.real.ui.components.CheckTarget(com.cagri.real.data.CheckRequest.EVERYONE, t("Herkes"))) else emptyList()
        }
    }
    checkTargets?.let { targets ->
        com.cagri.real.ui.components.CheckRequestSheet(
            targets = targets,
            canClear = reminders.any { it.id in selectedIds && it.checkRequest != null },
            onDismiss = { checkTargets = null },
            onSave = { target, startDay, color ->
                val me = com.cagri.real.platform.android.auth.AndroidBackend.auth.currentUser()?.takeIf { !it.isAnonymous }?.id
                onSetCheckRequest(selectedIds, com.cagri.real.data.CheckRequest(listOf(target), me, System.currentTimeMillis(), startDay, color))
                checkTargets = null; selectedIds = emptySet()
            },
            onClear = { onSetCheckRequest(selectedIds, null); checkTargets = null; selectedIds = emptySet() }
        )
    }
    val isCategorySelectionMode = category == null && customCategory == null && selectedCategoryIds.isNotEmpty()

    val composerInput = ReminderComposerSnapshot(draft, alertAt, repeatRule, category, customCategory,
        customCategories, reminders, draftAttachments, draftReferenceUrl, reduceAnimations)
    val composerCallbacks = ReminderComposerCallbacks(onAddReminder, onDraftChange, onAlertChange,
        onDraftAttachmentsChange, onDraftReferenceUrlChange, onShareDraftChange, onSetRepeatPattern,
        com.cagri.real.ui.components.LocalSetReminderTiming.current,
        com.cagri.real.ui.components.LocalSetReminderAlarm.current,
        com.cagri.real.ui.components.LocalSetReminderLocationTrigger.current)
    val latestVoiceReminders by rememberUpdatedState(reminders)
    val composerServices = ReminderComposerServices(context, view, focusManager, keyboard, scope,
        plannerRepository, entryTracker, whatsAppRecipientRepo, { latestVoiceReminders })
    val voiceActions = ReminderVoiceActions(onCreateVoiceCategory, onVoiceEdit, onToggleCompleted, voiceCardCallbacks)
    val submit = reminderSubmitAction(composerInput, composerState, voiceState, composerCallbacks,
        composerServices, requestPermissionForAction)
    val voiceFlow = rememberReminderVoiceFlow(composerInput, composerState, voiceState, composerCallbacks,
        composerServices, voiceActions)
    val voice = voiceFlow.voice
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingMicrophoneAction) {
            voice.start()
        }
        pendingMicrophoneAction = false
    }
    var voiceLineCount by voiceState.voiceLineCount
    // Home screen: dictation lives in its own liquid-glass window instead of the Add to REAL composer.
    val voiceOnHome = category == null && customCategory == null
    val voiceFeedback by voiceState.feedbackText
    val understandingCheck by voiceState.understandingCheck
    // The person's picks for the underlined words; a new check starts from the AI's reading again.
    var understandingText by remember(understandingCheck) { mutableStateOf(understandingCheck?.initial) }
    val voiceWindowVisible = understandingCheck != null || !voiceFeedback.isNullOrBlank() || ((voiceOnHome || voiceState.timeFollowUp.value != null) &&
        (voice.phase != VoicePhase.Idle || voiceAiParsing || pendingAutoSubmit))
    ReminderVoiceEffects(voice, voiceState, composerState, voiceOnHome, draft, submit)
    var textLineCount by remember { mutableIntStateOf(1) }
    LaunchedEffect(draft) {
        if (draft.isEmpty()) {
            voiceState.resubmitTypedCorrection.value = false
            textLineCount = 1
        }
    }
    val expanded = editing || (voice.phase != VoicePhase.Idle && !voiceOnHome) || alertAt != null || requiresTimeSelection || (draftCategoryId != null && draftCategoryId != customCategory?.id) || isChecklistMode || isVoiceDraft || isShareDraft || sendImmediateNotification || pendingNotificationAction != PendingNotificationAction.NONE || draftAttachments.isNotEmpty()
    // A view-only shared category (Sınırlı) cannot get new cards.
    val showComposer = category != ReminderCategory.JournalEntries && customCategory?.isReadOnlyShared != true
    LaunchedEffect(showDatePicker, showNotificationPermissionSheet, refocus) {
        if (!showDatePicker && !showNotificationPermissionSheet && refocus) { refocus = false; inputFocus.requestFocus(); keyboard?.show() }
    }
    val labsExtraHeight = if (isLabsOpen && expanded) 76.dp else 0.dp
    val attachmentExtraHeight = if (draftAttachments.isNotEmpty()) 48.dp else 0.dp
    val voiceAiMetaExtraHeight = if (
        pendingVoiceDurationMinutes != null || pendingVoicePeople.isNotEmpty() || pendingVoiceLocation != null
    ) 28.dp else 0.dp

    // The natural-language "Bugün/Yarın" suggestion is its own visual row. Reserve real
    // height for it so the text field never gets pushed into the top edge of the glass.
    val showComposerDateSuggestion = remember(draft, alertAt, pendingDateHintEpochDay) {
        val detected = detectDateSuggestion(draft)
        if (detected == null) {
            false
        } else {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val suggestedDate = when (detected) {
                DateSuggestionType.TODAY -> today
                DateSuggestionType.TOMORROW -> today.plusDays(1)
            }
            val selectedDate = alertAt?.let {
                java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
            } ?: pendingDateHintEpochDay?.let(LocalDate::ofEpochDay)
            selectedDate != suggestedDate
        }
    }
    val dateSuggestionExtraHeight = if (expanded && showComposerDateSuggestion) 42.dp else 0.dp
    // A tiny lift while Gemini is interpreting the transcript makes the state feel active
    // without moving the user's text around.
    val aiThinkingExtraHeight = if (expanded && voiceAiParsing) 10.dp else 0.dp

    val targetComposerHeight = when {
        understandingCheck != null && voice.phase == VoicePhase.Idle -> {
            val extraLines = (voiceLineCount - 1).coerceIn(0, 3)
            (156 + extraLines * 22).dp
        }
        voiceWindowVisible -> {
            val extraLines = (voiceLineCount - 1).coerceIn(0, 3)
            (118 + extraLines * 27).dp
        }
        isChecklistMode -> 280.dp + labsExtraHeight + attachmentExtraHeight + voiceAiMetaExtraHeight +
            dateSuggestionExtraHeight + aiThinkingExtraHeight
        voice.phase != VoicePhase.Idle -> {
            val extraLines = (voiceLineCount - 1).coerceIn(0, 3)
            (118 + extraLines * 27).dp + labsExtraHeight + attachmentExtraHeight + voiceAiMetaExtraHeight +
                dateSuggestionExtraHeight + aiThinkingExtraHeight
        }
        expanded -> {
            val extraLines = (textLineCount - 1).coerceIn(0, 3)
            (118 + extraLines * 27).dp + labsExtraHeight + attachmentExtraHeight + voiceAiMetaExtraHeight +
                dateSuggestionExtraHeight + aiThinkingExtraHeight
        }
        else -> 54.dp
    }
    // composerHeight / bottomGap animate on every composer open/close. They are kept as State and read
    // only in layout lambdas (deferredHeight / deferredBottomPadding / graphicsLayer), so the animation
    // relayouts the dock instead of recomposing this whole screen on every frame.
    val composerHeightState = animateDpAsState(
        targetComposerHeight,
        if (reduceAnimations) snap() else tween(280, easing = RealMotion.Emphasized),
        label = "composerHeight"
    )
    val labsProgress by animateFloatAsState(
        targetValue = if (isLabsOpen && expanded) 1f else 0f,
        animationSpec = if (reduceAnimations) snap() else tween(280, easing = RealMotion.Emphasized),
        label = "labsProgress"
    )
    val bottomGapState = animateDpAsState(
        if (expanded) 20.dp else 12.dp,
        if (reduceAnimations) snap() else tween(250),
        label = "bottomGap"
    )
    // Only the open/closed flip matters; reading the raw IME height here recomposed the whole
    // screen on every frame of the keyboard animation.
    val imeInsets = WindowInsets.ime
    val imeDensity = LocalDensity.current
    val keyboardVisible by remember(imeInsets, imeDensity) { derivedStateOf { imeInsets.getBottom(imeDensity) > 0 } }
    var keyboardWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(keyboardVisible) {
        if (keyboardVisible) keyboardWasVisible = true
        else if (keyboardWasVisible) {
            focusManager.clearFocus()
            keyboardWasVisible = false
        }
    }
    val anySelectionMode = isSelectionMode || isCategorySelectionMode
    BackHandler(enabled = homeSearchOpen && category == null && customCategory == null && !anySelectionMode) {
        homeSearchOpen = false; homeQuery = ""; focusManager.clearFocus(); keyboard?.hide()
    }
    BackHandler(enabled = isLabsOpen && !anySelectionMode) { isLabsOpen = false }
    BackHandler(enabled = isColorFilterExpanded && !anySelectionMode) { isColorFilterExpanded = false }
    BackHandler(enabled = isCategorySelectionMode) { selectedCategoryIds = emptySet() }
    BackHandler(enabled = isSelectionMode && !isCategorySelectionMode && !showBulkCategoryPicker) { selectedIds = emptySet() }
    BackHandler(enabled = showCategoryOrderEditor && !anySelectionMode) { showCategoryOrderEditor = false }
    BackHandler(enabled = showAccountMenu && !showCategoryOrderEditor && !anySelectionMode) { showAccountMenu = false }
    BackHandler(enabled = showCategoryPicker && !anySelectionMode) { showCategoryPicker = false }
    BackHandler(enabled = showBulkCategoryPicker) { showBulkCategoryPicker = false }
    BackHandler(enabled = (isChecklistMode || draftAttachments.isNotEmpty()) && !showAccountMenu && !showDatePicker && !showCategoryPicker && !anySelectionMode) {
        if (draftAttachments.isNotEmpty()) {
            photoImportGeneration++
            val discardedPhotos = draftAttachments
            scope.launch(Dispatchers.IO + NonCancellable) { AttachmentStorage.deleteAttachmentFiles(context, discardedPhotos) }
            onDraftAttachmentsChange(emptyList())
        }
        isChecklistMode = false
        checklistDraftItems = listOf("")
        if (isVoiceDraft) {
            voiceState.timeFollowUp.value = null
            voiceState.requestTimeListening.value = false
            isVoiceDraft = false
            requiresTimeSelection = false
            pendingDateHintEpochDay = null
            pendingTimeOfDay = null
            pendingVoiceDurationMinutes = null
            pendingVoicePeople = emptyList()
            pendingVoiceLocation = null; pendingVoiceRepeatMask = 0; pendingVoiceRepeatUntil = null
            pendingVoiceMusicBlocks = emptyList()
            pendingVoiceMediaBlocks = emptyList()
            onDraftChange("")
            onAlertChange(null, RecurrenceRule.NONE)
            draftCategoryId = if (customCategory != null) customCategory.id else null
            voiceCategoryExplicit = false
        }
        focusManager.clearFocus()
        keyboard?.hide()
    }
    BackHandler(enabled = !voiceFeedback.isNullOrBlank() && voice.phase == VoicePhase.Idle && !voiceAiParsing) {
        voiceState.feedbackText.value = null
        voiceState.requestTimeListening.value = false
    }
    BackHandler(enabled = (voiceFeedback.isNullOrBlank() || voice.phase != VoicePhase.Idle || voiceAiParsing) && (editing || voice.phase != VoicePhase.Idle || isVoiceDraft || isShareDraft) && !isChecklistMode && !showAccountMenu && !showDatePicker && !showCategoryPicker && !anySelectionMode) {
        voiceState.requestTimeListening.value = false
        val pendingClockCard = voiceState.timeFollowUp.value
        if (pendingClockCard != null && voiceAiParsing) {
            voiceAiRequestSerial += 1L
            voiceAiParsing = false
            pendingAutoSubmit = false
            onDraftChange(pendingClockCard.prepared.intent.title)
        }
        when {
            voice.phase != VoicePhase.Idle -> {
                voice.cancel()
                if (pendingClockCard != null) onDraftChange(pendingClockCard.prepared.intent.title)
                focusManager.clearFocus()
                keyboard?.hide()
            }
            editing -> {
                focusManager.clearFocus()
                keyboard?.hide()
                editing = false
            }
            isVoiceDraft -> {
                voiceState.timeFollowUp.value = null
                voiceState.requestTimeListening.value = false
                voiceAiRequestSerial += 1L
                voiceState.pendingDeletePlan.value = null
                voiceState.retryVoiceCreation.value = null
                voiceAiParsing = false
                pendingAutoSubmit = false
                isVoiceDraft = false
                requiresTimeSelection = false
                pendingDateHintEpochDay = null
                pendingTimeOfDay = null
                pendingVoiceDurationMinutes = null
                pendingVoicePeople = emptyList()
                pendingVoiceLocation = null; pendingVoiceRepeatMask = 0; pendingVoiceRepeatUntil = null
                pendingVoiceMusicBlocks = emptyList()
                pendingVoiceMediaBlocks = emptyList()
                onDraftChange("")
                onAlertChange(null, RecurrenceRule.NONE)
                draftCategoryId = if (customCategory != null) customCategory.id else null
                voiceCategoryExplicit = false
                voice.cancel()
                focusManager.clearFocus()
                keyboard?.hide()
            }
            isShareDraft -> {
                onShareDraftChange(false)
                onDraftReferenceUrlChange(null)
                onDraftChange("")
                onAlertChange(null, RecurrenceRule.NONE)
                draftCategoryId = if (customCategory != null) customCategory.id else null
                voiceCategoryExplicit = false
                focusManager.clearFocus()
                keyboard?.hide()
            }
        }
    }
    val generalReminders = remember(reminders) { reminders.filterNot { it.isStandaloneJournal() } }
    // Yaşam > Günlüklerim: bağımsız günlükler dahil günlüğü olan tüm kartlar.
    val journalReminders = remember(reminders) { reminders.filter { it.journal != null } }
    val journalWrittenToday = remember(journalReminders) {
        val today = java.time.LocalDate.now()
        journalReminders.any { r ->
            r.journal?.let { java.time.Instant.ofEpochMilli(it.updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today } == true
        }
    }
    val lists = rememberReminderListProjection(
        if (category == ReminderCategory.JournalEntries) reminders else generalReminders,
        category, customCategory, customCategories, categoryColorFilter, effectiveSortOrder)
    val today = lists.today
    val categoryProgressStats = lists.categoryProgressStats
    val customCategoriesById = lists.customCategoriesById
    val baseActive = lists.baseActive
    val baseCompleted = lists.baseCompleted
    val categorySearch = categoryQuery.trim().lowercase(java.util.Locale.forLanguageTag("tr"))
    val searchingCategory = categorySearch.isNotEmpty() && (category != null || customCategory != null)
    val homeSearch = if (homeSearchOpen && category == null && customCategory == null) homeQuery.trim().lowercase(java.util.Locale.forLanguageTag("tr")) else ""
    val searchingHome = homeSearch.isNotEmpty()
    val searchText = if (searchingCategory) categorySearch else homeSearch
    fun matchesCategorySearch(r: ReminderItem): Boolean {
        val tr = java.util.Locale.forLanguageTag("tr")
        return r.text.lowercase(tr).contains(searchText) || r.checklistItems.any { it.text.lowercase(tr).contains(searchText) } ||
            // On Home a category's name finds its cards too ("iş" → the İş cards).
            (searchingHome && r.customCategoryId?.let { customCategoriesById[it] }?.name?.lowercase(tr)?.contains(searchText) == true)
    }
    // Phone-calendar events filed under this category ("Kategorize et") join its cards, read-only (negative ids).
    // Home reads them too, for the category counts.
    val calendarCategoryCards = if (customCategory != null || category == null) com.cagri.real.calendar.rememberCategorizedCalendarCards(reminders)
        else com.cagri.real.calendar.CategorizedCalendarCards()
    val calendarCategoryCounts = remember(calendarCategoryCards) {
        calendarCategoryCards.items.mapNotNull { it.customCategoryId }.groupingBy { it }.eachCount()
    }
    val allCategoryCalendarItems = remember(calendarCategoryCards, customCategory?.id) {
        if (customCategory == null) emptyList() else calendarCategoryCards.items.filter { it.customCategoryId == customCategory.id }
    }
    // The coming year only, so endless calendars (birthdays…) stay bounded.
    val categoryCalendarItems = allCategoryCalendarItems
    var openedCalendarEvent by remember { mutableStateOf<com.cagri.real.calendar.CalendarOccurrence?>(null) }
    openedCalendarEvent?.let { event ->
        com.cagri.real.calendar.CalendarEventDetailHost(
            event = event, reminders = reminders, customCategories = customCategories, reduceDynamicBlur = reduceDynamicBlur,
            onCreateCalendarCard = { title, categoryId ->
                onAddReminder(title, null, RecurrenceRule.NONE, categoryId, false,
                    emptyList(), false, null, emptyList(), emptyList(), null, null, null, emptyList(), emptyList())
            },
            onCreateCategory = { name ->
                val used = customCategories.mapTo(HashSet()) { it.accentStart }
                val color = com.cagri.real.ui.components.ReminderColorDisplayOrder.drop(2).firstOrNull { it !in used } ?: 9
                onCreateCategory(name, "NONE", color, null, false, false, null, null, null)
            },
            onOpenCard = { onOpenReminder(it) },
            onDismiss = { openedCalendarEvent = null }
        )
    }
    // Home: the phone calendar's next 7 / 14 days as cards too (Ayarlar → Ana ekranda takvim), categorized ones with
    // their category stripe. Hidden-from-general-lists categories stay out, like REAL cards.
    val homeCalendarDays = if (category == null && customCategory == null) com.cagri.real.calendar.CalendarHomeDays.get(context) else 0
    val homeCalendarItems = remember(calendarCategoryCards, homeCalendarDays, customCategoriesById) {
        calendarCategoryCards.within(homeCalendarDays).filter { item ->
            item.customCategoryId?.let { customCategoriesById[it] }?.hideFromGeneralLists != true
        }
    }
    val searching = searchingCategory || searchingHome
    // Home search also reaches the categories kept off Home ("Yalnızca kategori içinde göster").
    val searchPoolActive = if (searchingHome) filterAndSortReminders(generalReminders.filter { !it.isCompleted }, null, effectiveSortOrder) else lists.active
    val searchPoolCompleted = if (searchingHome) filterAndSortReminders(
        generalReminders.inCategory(ReminderCategory.Completed).sortedByDescending { it.completedAt ?: it.createdAt }, null, effectiveSortOrder) else lists.completed
    val active = (if (searching) searchPoolActive.filter(::matchesCategorySearch) else lists.active) +
        (if (searchingCategory) categoryCalendarItems.filter(::matchesCategorySearch) else categoryCalendarItems) +
        (if (searchingHome) calendarCategoryCards.within(homeCalendarDays).filter(::matchesCategorySearch) else homeCalendarItems)
    val completed = if (searching) searchPoolCompleted.filter(::matchesCategorySearch) else lists.completed
    val journalGroups = lists.journalGroups
    val isHome = category == null && customCategory == null
    val realPetHomeSurfaceActive = isHome &&
        !showPano && !showHomeWidgets && !showPlanner && !showMoodTrackerHistory && !showHomeMood &&
        !showMoodSuggestions && !showAccountMenu && !showCategoryOrderEditor &&
        !showCreateCategoryDialog && !showEditCategoryDialog &&
        !showCategoryPicker && !showBulkCategoryPicker && !showDatePicker &&
        !showNotificationPermissionSheet && !showMicrophonePermissionSheet &&
        !showWhatsAppConsentSheet && !showWhatsAppRecipientSheet
    val latestHomePetSurfaceCallback by rememberUpdatedState(onRealPetHomeSurfaceActiveChange)
    LaunchedEffect(realPetHomeSurfaceActive) {
        latestHomePetSurfaceCallback(realPetHomeSurfaceActive)
    }
    DisposableEffect(Unit) {
        onDispose { latestHomePetSurfaceCallback(false) }
    }

    val onComposerMicClick: () -> Unit = onMicClick@{
        if (saving || voiceAiParsing) return@onMicClick
        voiceState.feedbackText.value = null
        voiceState.requestTimeListening.value = false
        val waitingClock = voiceState.timeFollowUp.value
        if (waitingClock != null && !requiresTimeSelection) {
            voiceState.timeFollowUp.value = null
        }
        focusManager.clearFocus()
        keyboard?.hide()
        if (voice.phase == VoicePhase.Listening) {
            voice.toggle()
        } else if (voice.phase == VoicePhase.Idle) {
            val hasMicPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasMicPermission) {
                if (voiceState.timeFollowUp.value != null) voice.startWithText("") else voice.toggle()
            } else {
                pendingMicrophoneAction = true
                showMicrophonePermissionSheet = true
            }
        }
    }
    // Home "Tamamlandı": 2 cards + a 3rd under glass (so the list stops there) until "Daha fazla" opens 10, then 10 more each time.
    // Leaving Home (category, planner, Pano, Yaşam) or the app brings it back to 3.
    var homeCompletedShown by remember { mutableIntStateOf(3) }
    // Category screens use the same 2 + 1 under glass + "Daha fazla" list (not "Tamamlananlar" itself, where everything is completed).
    val limitCompleted = category != ReminderCategory.Completed
    LaunchedEffect(isHome, category, customCategory?.id, showPlanner, showPano, showHomeWidgets) {
        homeCompletedShown = 3
    }
    val homeLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(homeLifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) homeCompletedShown = 3
        }
        homeLifecycle.addObserver(observer)
        onDispose { homeLifecycle.removeObserver(observer) }
    }
    val completedPreview = remember(completed, limitCompleted, homeCompletedShown) {
        if (limitCompleted) completed.take(homeCompletedShown) else completed
    }
    val homeCompletedMore = limitCompleted && completed.size > homeCompletedShown
    val statusHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    DisposableEffect(category, customCategory, categoryColorFilter) {
        isColorFilterExpanded = false
        showAccountMenu = false
        selectedIds = emptySet()
        selectedCategoryIds = emptySet()
        onDispose { }
    }

    LaunchedEffect(reminders) {
        val currentIds = reminders.map { it.id }.toSet()
        if (selectedIds.any { it !in currentIds }) {
            selectedIds = selectedIds.filter { it in currentIds }.toSet()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(if (booksOpen) Modifier else Modifier.navigationBarsPadding())
            .imePadding()
            .onGloballyPositioned { coords ->
                containerCoordinates = coords
            }
    ) {
        com.cagri.real.contextcards.HomeContextCard(
                        moodEntries = allMoodTrackerEntries,
                        reduceAnimations = reduceAnimations,
                        onOpen = { type ->
                            when (type) {
                                com.cagri.real.contextcards.ContextCardType.MOOD -> {
                                    homeMoodSelection = JournalMood.NEUTRAL
                                    moodOpenedFromContextCard = true
                                    showHomeMood = true
                                }
                                com.cagri.real.contextcards.ContextCardType.JOURNAL -> showYasamJournal = true
                                else -> {
                                    widgetDestination = when (type) {
                                        com.cagri.real.contextcards.ContextCardType.WATER -> "water"
                                        com.cagri.real.contextcards.ContextCardType.BOOK -> "books"
                                        com.cagri.real.contextcards.ContextCardType.WATCH -> "ratings"
                                        com.cagri.real.contextcards.ContextCardType.LISTEN -> "music"
                                        com.cagri.real.contextcards.ContextCardType.PLAY -> "games"
                                        else -> "media"
                                    }
                                    showHomeWidgets = true
                                }
                            }
                        },
                        journalWrittenToday = journalWrittenToday,
                        active = isHome
                    ) { activeContextTypes, renderContextCard ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                // Keyboard open: a tap on the list only closes the keyboard; it does not open the category or card under
                // the finger. Scrolling still works.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial, requireUnconsumed = false)
                        if (!latestEditing.value) return@awaitEachGesture
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                            if (!change.pressed) {
                                if (!moved) {
                                    change.consume()
                                    focusManager.clearFocus(); keyboard?.hide()
                                }
                                break
                            }
                        }
                    }
                }
                .onGloballyPositioned { scenePosition = it.positionInRoot() }
                .drawWithContent {
                    if (com.cagri.real.ui.components.shouldCaptureBackdrop(designStyle, reduceDynamicBlur, Build.VERSION.SDK_INT)) {
                        scene.record { this@drawWithContent.drawContent() }; drawLayer(scene)
                    } else drawContent()
                }
                .graphicsLayer {
                    if (isColdStarting) {
                        alpha = coldStartProgress.value
                    }
                },
            contentPadding = com.cagri.real.ui.components.DeferredBottomPaddingValues(statusHeight) {
                // The card under glass stays above the dock (it used to slide under it), so "Daha fazla" sits on its middle.
                if (showComposer) composerHeightState.value + bottomGapState.value + 24.dp +
                    (if (isHome) HomeDockExtraBottomSpace else 0.dp) else 24.dp
            }
        ) {
            item(key = "header") {
                val headerAlpha by animateFloatAsState(
                    targetValue = if (isSelectionMode) 0f else 1f,
                    animationSpec = if (reduceAnimations) snap() else tween(180),
                    label = "listHeaderAlpha"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = headerAlpha }
                        .then(if (isSelectionMode) Modifier.clearAndSetSemantics { } else Modifier)
                ) {
                    if (category == null && customCategory == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .width(112.dp)
                                .height(42.dp)
                                .onGloballyPositioned { coords ->
                                    headerCoordinates = coords
                                }
                        ) {
                            HomeWordmark(
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.cagri.real.updates.TesterUpdateButton()
                        val profileInteraction = remember { MutableInteractionSource() }
                        IconButton(
                            interactionSource = profileInteraction,
                            onClick = {
                                voice.cancel()
                                focusManager.clearFocus()
                                keyboard?.hide()
                                showAccountMenu = true
                            },
                            modifier = Modifier.then(if (designStyle == DesignStyle.LiquidGlass) Modifier.pressScale(profileInteraction, reduceAnimations = reduceAnimations, targetScale = 0.97f).liquidChrome() else Modifier).semantics { contentDescription = t("Hesap menüsünü aç") }
                        ) {
                            ProfileAvatar(
                                modifier = Modifier.size(48.dp),
                                liquidFallback = designStyle == DesignStyle.LiquidGlass,
                                petPerchId = REAL_PET_PERCH_AVATAR
                            )
                        }
                        }
                    }
                } else {
                    // Category screen: the category colour as a full-width header (CategoryFullHero); the colour filter
                    // opens under it.
                    Column(Modifier.fillMaxWidth()) {
                        // Pressing the sort arrow says what it now does, on the right under the header, then fades.
                        var sortHint by remember { mutableStateOf<Pair<Int, String>?>(null) }
                        LaunchedEffect(sortHint?.first) {
                            if (sortHint != null) { delay(1600); sortHint = null }
                        }
                        val saveCategorySort: (ReminderSortOrder) -> Unit = { next ->
                            sortHint = (sortHint?.first ?: 0) + 1 to when (next) {
                                ReminderSortOrder.OLDEST_FIRST -> t("Son eklenen altta")
                                ReminderSortOrder.NEAREST_DATE, ReminderSortOrder.REMINDER_TIME -> t("Tarihi en yakın üstte")
                                else -> t("Son eklenen üstte")
                            }
                            categorySortOrder = next
                            uiPreferences.edit().putString(
                                "category_sort_order",
                                when (next) {
                                    ReminderSortOrder.OLDEST_FIRST -> "oldest_first"
                                    ReminderSortOrder.NEAREST_DATE -> "nearest_date"
                                    else -> "recently_added"
                                }
                            ).let { com.cagri.real.platform.android.sync.SyncLocal.saveUiPreference(uiPreferences, it) }
                        }
                        com.cagri.real.ui.components.CategoryFullHero(
                            category = category, customCategory = customCategory,
                            override = category?.let { builtInCategoryColors[it] },
                            count = baseActive.size + baseCompleted.size + allCategoryCalendarItems.size,
                            todayCount = baseActive.count { r ->
                                r.alertAt?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today } == true ||
                                    r.plannedDateEpochDay == today.toEpochDay()
                            },
                            detail = remember(baseActive, allCategoryCalendarItems, today) {
                                categoryHeaderDetail(baseActive + allCategoryCalendarItems, today)
                            },
                            statusTop = statusHeight,
                            query = categoryQuery,
                            onQueryChange = { categoryQuery = it },
                            onBack = onBack,
                            progress = categoryProgressStats,
                            progressOpen = showCategoryProgress,
                            reduceAnimations = reduceAnimations,
                            hasFilter = categoryColorFilter != null,
                            onFilter = { isColorFilterExpanded = true },
                            onEdit = {
                                // Members get the editor too, limited to the description (read only) and hide switch.
                                if (customCategory != null) showEditCategoryDialog = true else editingBuiltInCategory = category
                            },
                            onPeople = customCategory?.let { { showPeopleSheet = true } },
                            onAddDivider = customCategory?.takeIf { !it.isReadOnlyShared }?.let { { dividerAdding = true } },
                            editLabel = if (customCategory != null) t("Kategoriyi düzenle") else t("Kategori rengini düzenle"),
                            onGoals = customCategory?.let { c -> { plannerGoalCategoryId = c.id; showPlanner = true } },
                            sharedTransitionScope = if (categorySharedTransitionsEnabled) sharedTransitionScope else null,
                            animatedVisibilityScope = if (categorySharedTransitionsEnabled) animatedVisibilityScope else null,
                            actions = { foreground ->
                                com.cagri.real.ui.components.CategoryHeroActions(
                                    sortOrder = categorySortOrder,
                                    onSortSelected = saveCategorySort,
                                    progressFraction = categoryProgressStats?.fraction,
                                    progressOpen = showCategoryProgress,
                                    onProgressClick = { showCategoryProgress = !showCategoryProgress },
                                    foreground = foreground,
                                    reduceAnimations = reduceAnimations,
                                    showSort = category != ReminderCategory.JournalEntries,
                                    ringTint = foreground
                                )
                            }
                        )
                        AnimatedVisibility(
                            visible = sortHint != null,
                            enter = if (reduceAnimations) fadeIn(tween(0)) else fadeIn(tween(160)) + expandVertically(tween(200)),
                            exit = if (reduceAnimations) fadeOut(tween(0)) else fadeOut(tween(260)) + shrinkVertically(tween(260))
                        ) {
                            var shown by remember { mutableStateOf("") }
                            sortHint?.second?.let { shown = it }
                            Text(
                                shown, color = Color(0xFFB8B9C2), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 24.dp, top = 10.dp)
                            )
                        }
                        AnimatedVisibility(
                            visible = isColorFilterExpanded,
                            enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(200)) + expandVertically(tween(240)),
                            exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(160)) + shrinkVertically(tween(200))
                        ) {
                            Box(Modifier.padding(top = 12.dp)) {
                                CategoryColorSelectorHeader(
                                    selectedColorFilter = categoryColorFilter,
                                    onSelectColor = { selected ->
                                        onCategoryColorFilterChange(selected)
                                        isColorFilterExpanded = false
                                    },
                                    onClose = { isColorFilterExpanded = false },
                                    reduceAnimations = reduceAnimations
                                )
                            }
                        }
                    }
                }
                }
                Spacer(Modifier.height(if (category == null && customCategory == null) 22.dp else 18.dp))
            }
            if (category == null && customCategory == null) {
                item(key = "categories") {
                    AnimatedVisibility(
                        visible = !homeSearchOpen,
                        enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(220)) + expandVertically(tween(300)),
                        exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(160)) + shrinkVertically(tween(300))
                    ) {
                    Column {
                    CategoryCollections(
                        sharedTransitionScope = if (categorySharedTransitionsEnabled) sharedTransitionScope else null,
                        animatedVisibilityScope = if (categorySharedTransitionsEnabled) animatedVisibilityScope else null,
                        listState = listState,
                        reminders = reminders,
                        calendarCounts = calendarCategoryCounts,
                        today = today,
                        categoryOrderKeys = categoryOrderKeys,
                        previewCount = homeCategoryPreviewCount,
                        customCategories = customCategories,
                        builtInCategoryColors = builtInCategoryColors,
                        onEditBuiltInCategory = { editingBuiltInCategory = it },
                        onOpenCategory = {
                            if (!anySelectionMode) {
                                voice.cancel(); focusManager.clearFocus(); keyboard?.hide(); onOpenCategory(it)
                            }
                        },
                        onOpenCustomCategory = {
                            if (!anySelectionMode) {
                                voice.cancel(); focusManager.clearFocus(); keyboard?.hide(); onOpenCustomCategory(it)
                            }
                        },
                        onAddCategory = {
                            if (!anySelectionMode) {
                                voice.cancel(); focusManager.clearFocus(); keyboard?.hide(); showCreateCategoryDialog = true
                            }
                        },
                        selectedCategoryIds = selectedCategoryIds,
                        onToggleSelectCategory = { id ->
                            selectedCategoryIds = if (id in selectedCategoryIds) selectedCategoryIds - id else selectedCategoryIds + id
                        },
                        onLongClickCategory = { id ->
                            voice.cancel(); focusManager.clearFocus(); keyboard?.hide()
                            selectedIds = emptySet()
                            selectedCategoryIds = if (id in selectedCategoryIds) selectedCategoryIds - id else selectedCategoryIds + id
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                        reduceAnimations = reduceAnimations
                    )
                    Spacer(Modifier.height(24.dp))
                    }
                    }
                    com.cagri.real.ui.components.HomeCardsSearchTitle(
                        open = homeSearchOpen,
                        query = homeQuery,
                        onQueryChange = { homeQuery = it },
                        onOpen = {
                            voice.cancel()
                            homeSearchOpen = true
                            scope.launch { listState.animateScrollToItem(0) }
                        },
                        onClose = { homeSearchOpen = false; homeQuery = ""; focusManager.clearFocus() },
                        reduceAnimations = reduceAnimations,
                        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, bottom = 12.dp),
                        titleAccessory = {
                            // One dot per context card waiting at the bottom of the list, right beside the title.
                            if (activeContextTypes.isNotEmpty()) Row(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                activeContextTypes.take(5).forEach { type ->
                                    Box(Modifier.size(7.dp).background(type.accent(), CircleShape))
                                }
                            }
                        }
                    ) {
                        // The category screen's sort arrow: newest on top → newest at the bottom → nearest date.
                        com.cagri.real.ui.components.CategoryHeroActions(
                            sortOrder = sortOrder,
                            onSortSelected = { picked ->
                                val next = if (picked == ReminderSortOrder.NEAREST_DATE) ReminderSortOrder.REMINDER_TIME else picked
                                homeSortHint = (homeSortHint?.first ?: 0) + 1 to when (next) {
                                    ReminderSortOrder.OLDEST_FIRST -> t("Son eklenen altta")
                                    ReminderSortOrder.REMINDER_TIME -> t("Tarihi en yakın üstte")
                                    else -> t("Son eklenen üstte")
                                }
                                sortOrder = next
                                uiPreferences.edit().putString(
                                    "sort_order",
                                    when (next) {
                                        ReminderSortOrder.REMINDER_TIME -> "reminder_time"
                                        ReminderSortOrder.OLDEST_FIRST -> "oldest_first"
                                        else -> "recently_added"
                                    }
                                ).let { com.cagri.real.platform.android.sync.SyncLocal.saveUiPreference(uiPreferences, it) }
                            },
                            progressFraction = null,
                            progressOpen = false,
                            onProgressClick = {},
                            foreground = Color(0xFFF2F2F4),
                            reduceAnimations = reduceAnimations
                        )
                    }
                    // What the arrow now does, on the right under it, then it fades.
                    AnimatedVisibility(
                        visible = homeSortHint != null,
                        enter = if (reduceAnimations) fadeIn(tween(0)) else fadeIn(tween(160)) + expandVertically(tween(200)),
                        exit = if (reduceAnimations) fadeOut(tween(0)) else fadeOut(tween(260)) + shrinkVertically(tween(260))
                    ) {
                        var shown by remember { mutableStateOf("") }
                        homeSortHint?.second?.let { shown = it }
                        Text(
                            shown, color = Color(0xFFB8B9C2), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 24.dp, bottom = 12.dp)
                        )
                    }
                }
            }
            val isListEmpty = when {
                category == ReminderCategory.JournalEntries -> journalGroups.isEmpty()
                customCategory != null -> active.isEmpty() && completed.isEmpty()
                category != null -> active.isEmpty()
                else -> active.isEmpty()
            }
            if (isListEmpty) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().height(104.dp).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                        val emptyMsg = when {
                            category == ReminderCategory.JournalEntries -> t("Henüz günlük eklenmemiş")
                            searchingHome -> t("Bununla eşleşen kart yok")
                            (category != null || customCategory != null) && categoryColorFilter != null -> t("Bu renkte hatırlatıcı yok")
                            customCategory != null -> t("Bu kategoride henüz hatırlatıcı yok")
                            category != null -> category.emptyMessage
                            else -> t("Henüz hatırlatıcı yok")
                        }
                        Text(emptyMsg, color = Color(0xFF888890), fontSize = 14.sp)
                    }
                }
            }
            if (category == ReminderCategory.JournalEntries) {
                journalGroups.forEach { (date, items) ->
                    item(key = "journal_date_${date}") {
                        SectionTitle(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", I18n.locale)).lowercase(I18n.locale))
                    }
                    items(items, key = { it.id }, contentType = { "reminder" }) { reminder ->
                        val isSelected = reminder.id in selectedIds
                        val isNewlyCreated = entryTracker.isAnimating(reminder.id)
                        val reminderCategory = remember(reminder.customCategoryId, customCategoriesById, customCategory) {
                            reminder.customCategoryId?.let { customCategoriesById[it] }
                                ?: if (reminder.customCategoryId == customCategory?.id) customCategory else null
                        }
                        ReminderCard(
                            reminder, onToggle = { onToggleCompleted(reminder.id) },
                            onOpen = { voice.cancel(); focusManager.clearFocus(); keyboard?.hide(); onOpenReminder(reminder.id) },
                            reduceAnimations = reduceAnimations,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            isNewlyCreated = isNewlyCreated,
                            onEntryAnimationFinished = { entryTracker.onAnimationFinished(reminder.id) },
                            customCategory = reminderCategory,
                            journalListMode = true,
                            showVoiceSummary = false,
                            showCategoryStripes = false,
                            onLongClick = {
                                if (!isCategorySelectionMode) {
                                    voice.cancel(); focusManager.clearFocus(); keyboard?.hide()
                                    selectedCategoryIds = emptySet()
                                    selectedIds = selectedIds + reminder.id
                                }
                            },
                            onSelectToggle = {
                                selectedIds = if (isSelected) selectedIds - reminder.id else selectedIds + reminder.id
                            },
                            modifier = Modifier
                                .then(if (reduceAnimations) Modifier else Modifier.animateItem(
                                    placementSpec = RealMotion.smooth<IntOffset>(0.35f),
                                    fadeInSpec = if (isNewlyCreated) null else tween(180),
                                    fadeOutSpec = tween(180)
                                ))
                                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        )
                    }
                }
            } else {
                // The context card sits on top of the cards, like one of them (swipe left to put it away).
                if (isHome && !searchingHome) {
                    item(key = "context_card") {
                        Box(if (reduceAnimations) Modifier else Modifier.animateItem()) { renderContextCard() }
                    }
                    item(key = "guest_backup_nudge") { com.cagri.real.ui.auth.GuestBackupNudge(reminders.size) }
                }
                // Ayraçlar sit between the cards at the moment they were added (category screens, added-time orders).
                val slots = if (customCategory != null && !searching) dividerSlots(active, dividers, effectiveSortOrder) else emptyMap()
                val canEditDividers = customCategory?.isReadOnlyShared != true
                fun androidx.compose.foundation.lazy.LazyListScope.dividerRows(at: Int) = slots[at]?.forEach { d ->
                    item(key = "divider_${d.id}", contentType = "divider") {
                        com.cagri.real.ui.components.CategoryDividerRow(
                            title = d.text, editable = canEditDividers,
                            onEdit = { dividerEditing = d }, onDelete = { onDeleteDivider(d.id) },
                            modifier = if (reduceAnimations) Modifier else Modifier.animateItem(
                                placementSpec = RealMotion.smooth<IntOffset>(0.35f), fadeInSpec = tween(180), fadeOutSpec = tween(180))
                        )
                    }
                }
                active.forEachIndexed { index, reminder ->
                    dividerRows(index)
                    item(key = reminder.id, contentType = "reminder") {
                    val isSelected = reminder.id in selectedIds
                    val isNewlyCreated = entryTracker.isAnimating(reminder.id)
                    // A phone-calendar event card: opens the event window; no completing, swiping or selecting.
                    val fromCalendar = reminder.id < 0
                    val reminderCategory = remember(reminder.customCategoryId, customCategoriesById, customCategory) {
                        reminder.customCategoryId?.let { customCategoriesById[it] }
                            ?: if (reminder.customCategoryId == customCategory?.id) customCategory else null
                    }
                    // Sınırlı access to a shared category: the card is only looked at.
                    val viewOnly = fromCalendar || reminderCategory?.isReadOnlyShared == true
                    ReminderCard(
                        reminder, onToggle = { if (!viewOnly) onToggleCompleted(reminder.id) },
                        onOpen = {
                            voice.cancel(); focusManager.clearFocus(); keyboard?.hide()
                            if (fromCalendar) openedCalendarEvent = calendarCategoryCards.occurrences[reminder.id]
                            else onOpenReminder(reminder.id)
                        },
                        reduceAnimations = reduceAnimations,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        isNewlyCreated = isNewlyCreated,
                        onEntryAnimationFinished = { entryTracker.onAnimationFinished(reminder.id) },
                        customCategory = reminderCategory,
                        showVoiceSummary = false,
                        showCategoryStripes = category == null && customCategory == null,
                        categoryLabel = if (searchingHome) reminderCategory else null,
                        onLongClick = {
                            if (!isCategorySelectionMode && !viewOnly) {
                                voice.cancel(); focusManager.clearFocus(); keyboard?.hide()
                                selectedCategoryIds = emptySet()
                                selectedIds = selectedIds + reminder.id
                            }
                        },
                        onSelectToggle = {
                            if (!viewOnly) selectedIds = if (isSelected) selectedIds - reminder.id else selectedIds + reminder.id
                        },
                        onSwipeRightTo24Hour = when {
                            viewOnly -> null
                            plainHomeCards -> ({ RealHaptics.success(view); onToggleCompleted(reminder.id) })
                            category != ReminderCategory.Today -> ({ scope.launch { onMoveTo24Hour(reminder.id) } })
                            else -> null
                        },
                        swipeRightLabel = if (!plainHomeCards) "Move 24 hour" else if (reminder.isCompleted) t("Geri aç") else t("Tamamla"),
                        // A completed card leaves the list; a repeating one stays (it moves to its next time).
                        swipeRightKeepsCard = !plainHomeCards || reminder.hasRepeat,
                        plainStyle = plainHomeCards,
                        onSwipeLeftDelete = if (viewOnly) null else ({ onDeleteReminders(setOf(reminder.id)) }),
                        modifier = Modifier
                            .then(if (reduceAnimations) Modifier else Modifier.animateItem(
                                placementSpec = RealMotion.smooth<IntOffset>(0.35f),
                                fadeInSpec = if (isNewlyCreated) null else tween(180),
                                fadeOutSpec = tween(180)
                            ))
                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                    }
                }
                dividerRows(active.size)
            }
            if (completed.isNotEmpty() && category != ReminderCategory.JournalEntries) {
                item(key = "completed_heading") {
                    Spacer(Modifier.height(18.dp)); SectionTitle(t("Tamamlandı"))
                }
                if (limitCompleted) {
                    item(key = "home_completed_preview_container") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (reduceAnimations) Modifier else Modifier.animateItem(
                                    placementSpec = RealMotion.smooth<IntOffset>(0.35f),
                                    fadeInSpec = tween(180),
                                    fadeOutSpec = tween(180)
                                ))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (reduceAnimations) Modifier else Modifier.animateContentSize(tween(240, easing = RealMotion.Emphasized)))
                            ) {
                                completedPreview.forEachIndexed { index, reminder ->
                                    key(reminder.id) {
                                        val isSelected = reminder.id in selectedIds
                                        val reminderCategory = remember(reminder.customCategoryId, customCategoriesById, customCategory) {
                                            reminder.customCategoryId?.let { customCategoriesById[it] }
                                                ?: if (reminder.customCategoryId == customCategory?.id) customCategory else null
                                        }
                                        // The last card shown, while more remain, sits "under glass": blurred, faded, with "Daha fazla" on it.
                                        val underGlass = homeCompletedMore && index == completedPreview.lastIndex
                                        Box(Modifier.fillMaxWidth()) {
                                        ReminderCard(
                                            reminder, onToggle = { onToggleCompleted(reminder.id) },
                                            onOpen = { voice.cancel(); focusManager.clearFocus(); keyboard?.hide(); onOpenReminder(reminder.id) },
                                            reduceAnimations = reduceAnimations,
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            isSelectionMode = isSelectionMode,
                                            isSelected = isSelected,
                                            customCategory = reminderCategory,
                                            showVoiceSummary = false,
                                            showCategoryStripes = category == null && customCategory == null,
                                            // Sade kartlar: no circle here either; a right swipe undoes the completion.
                                            plainStyle = plainHomeCards,
                                            onSwipeRightTo24Hour = if (plainHomeCards && !underGlass) ({ onToggleCompleted(reminder.id) }) else null,
                                            swipeRightLabel = t("Geri al"),
                                            swipeRightKeepsCard = false,
                                            onLongClick = {
                                                if (!isCategorySelectionMode) {
                                                    voice.cancel(); focusManager.clearFocus(); keyboard?.hide()
                                                    selectedCategoryIds = emptySet()
                                                    selectedIds = selectedIds + reminder.id
                                                }
                                            },
                                            onSelectToggle = {
                                                selectedIds = if (isSelected) selectedIds - reminder.id else selectedIds + reminder.id
                                            },
                                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                                                .then(if (underGlass) Modifier
                                                    .graphicsLayer { alpha = 0.5f }
                                                    .blur(7.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded) else Modifier)
                                        )
                                        if (underGlass) {
                                            // Swallow taps on the faded card; only the pill acts.
                                            Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { } })
                                            Text(
                                                t("Daha fazla"),
                                                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                                // In the middle of the card (its 8 dp bottom gap left out).
                                                modifier = Modifier.align(Alignment.Center).padding(bottom = 8.dp)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(Color(0xCC1C1D22))
                                                    .border(0.7.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
                                                    .clickable(role = Role.Button) {
                                                        homeCompletedShown = if (homeCompletedShown < 11) 11 else homeCompletedShown + 10
                                                    }
                                                    .padding(horizontal = 18.dp, vertical = 9.dp)
                                            )
                                        }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(completed, key = { it.id }, contentType = { "reminder" }) { reminder ->
                        val isSelected = reminder.id in selectedIds
                        val reminderCategory = remember(reminder.customCategoryId, customCategoriesById, customCategory) {
                            reminder.customCategoryId?.let { customCategoriesById[it] }
                                ?: if (reminder.customCategoryId == customCategory?.id) customCategory else null
                        }
                        ReminderCard(
                            reminder, onToggle = { onToggleCompleted(reminder.id) },
                            onOpen = { voice.cancel(); focusManager.clearFocus(); keyboard?.hide(); onOpenReminder(reminder.id) },
                            reduceAnimations = reduceAnimations,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            customCategory = reminderCategory,
                            showVoiceSummary = false,
                            showCategoryStripes = false,
                            plainStyle = plainHomeCards,
                            onSwipeRightTo24Hour = if (plainHomeCards) ({ onToggleCompleted(reminder.id) }) else null,
                            swipeRightLabel = t("Geri al"),
                            swipeRightKeepsCard = false,
                            onLongClick = {
                                voice.cancel(); focusManager.clearFocus(); keyboard?.hide()
                                selectedCategoryIds = emptySet()
                                selectedIds = selectedIds + reminder.id
                            },
                            onSelectToggle = {
                                selectedIds = if (isSelected) selectedIds - reminder.id else selectedIds + reminder.id
                            },
                            modifier = Modifier
                                .then(if (reduceAnimations) Modifier else Modifier.animateItem(
                                    placementSpec = RealMotion.smooth<IntOffset>(0.35f),
                                    fadeInSpec = tween(180),
                                    fadeOutSpec = tween(180)
                                ))
                                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        )
                    }
                }
            }
        }
        }
        TopGlassOverlay(
            scene,
            { scenePosition },
            Modifier.align(Alignment.TopCenter),
            reduceDynamicBlur = reduceDynamicBlur,
            extendLiquidArea = extendLiquidArea
        )
        if (category != null || customCategory != null) {
            // Once the big header has scrolled away, a thin bar in the category colour keeps back, sort and add.
            val heroGone by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 420 } }
            AnimatedVisibility(
                visible = heroGone && !isSelectionMode,
                enter = if (reduceAnimations) EnterTransition.None else slideInVertically(tween(260, easing = RealMotion.Emphasized)) { -it },
                exit = if (reduceAnimations) ExitTransition.None else slideOutVertically(tween(200)) { -it },
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
            ) {
                com.cagri.real.ui.components.CategoryCompactBar(
                    category = category, customCategory = customCategory,
                    override = category?.let { builtInCategoryColors[it] },
                    statusTop = statusHeight,
                    onBack = onBack,
                    actions = { foreground ->
                        com.cagri.real.ui.components.CategoryHeroActions(
                            sortOrder = categorySortOrder,
                            onSortSelected = { next ->
                                categorySortOrder = next
                                uiPreferences.edit().putString(
                                    "category_sort_order",
                                    when (next) {
                                        ReminderSortOrder.OLDEST_FIRST -> "oldest_first"
                                        ReminderSortOrder.NEAREST_DATE -> "nearest_date"
                                        else -> "recently_added"
                                    }
                                ).let { com.cagri.real.platform.android.sync.SyncLocal.saveUiPreference(uiPreferences, it) }
                            },
                            progressFraction = null,
                            progressOpen = showCategoryProgress,
                            onProgressClick = {},
                            foreground = foreground,
                            reduceAnimations = reduceAnimations,
                            showSort = category != ReminderCategory.JournalEntries,
                            buttonSize = 40.dp
                        )
                    }
                )
            }
        }
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 2 },
            exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(140)) + slideOutVertically(tween(180)) { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = statusHeight + 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val cancelInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .then(
                            if (designStyle == DesignStyle.LiquidGlass) {
                                Modifier
                                    .pressScale(cancelInteraction, reduceAnimations = reduceAnimations, targetScale = 0.95f)
                                    .liquidChrome()
                            } else {
                                Modifier.background(Color(0xFF1E1E22), CircleShape)
                            }
                        )
                        .clickable(
                            interactionSource = cancelInteraction,
                            indication = null,
                            role = Role.Button,
                            onClick = { selectedIds = emptySet() }
                        )
                        .semantics { contentDescription = t("Seçimi kapat") },
                    contentAlignment = Alignment.Center
                ) {
                    ActionIcon(ActionGlyph.Close, Modifier.size(18.dp), Color(0xFFF3F3F5))
                }

                Text(
                    text = t("{0} seçildi", selectedIds.size),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                val categoryInteraction = remember { MutableInteractionSource() }
                val capsuleShape = RoundedCornerShape(21.dp)
                Row(
                    modifier = Modifier
                        .height(42.dp)
                        .then(
                            if (designStyle == DesignStyle.LiquidGlass) {
                                Modifier
                                    .pressScale(categoryInteraction, reduceAnimations = reduceAnimations, targetScale = 0.96f)
                                    .liquidChrome(corners = 21.dp)
                            } else {
                                Modifier
                                    .background(Color(0xFF1E1E22), capsuleShape)
                                    .border(0.75.dp, Color.White.copy(alpha = 0.12f), capsuleShape)
                            }
                        )
                        .clickable(
                            interactionSource = categoryInteraction,
                            indication = null,
                            role = Role.Button,
                            onClick = {
                                voice.cancel()
                                focusManager.clearFocus()
                                keyboard?.hide()
                                showBulkCategoryPicker = true
                            }
                        )
                        .semantics { contentDescription = t("Kategoriye ekle") }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ActionIcon(ActionGlyph.Category, Modifier.size(16.dp), Color(0xFF91B9FF))
                    Text(
                        text = t("Kategori"),
                        color = Color(0xFF91B9FF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        val selectionProgress by animateFloatAsState(
            targetValue = if (isSelectionMode || isCategorySelectionMode) 1f else 0f,
            animationSpec = if (reduceAnimations) snap() else tween(270, easing = RealMotion.Standard),
            label = "selectionBottomTransition"
        )
        val density = LocalDensity.current
        fun bottomSlideOffsetPx() = with(density) { (composerHeightState.value + bottomGapState.value + 80.dp).toPx() }

        AnimatedVisibility(
            visible = showComposer && isChecklistMode && !voiceWindowVisible,
            enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(180)),
            exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(140))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .pointerInput(Unit) {
                        detectTapGestures {
                            isChecklistMode = false
                            checklistDraftItems = listOf("")
                            focusManager.clearFocus()
                            keyboard?.hide()
                        }
                    }
            )
        }

        if (showComposer) {
            BottomFade(Modifier.align(Alignment.BottomCenter).deferredHeight { composerHeightState.value + bottomGapState.value + 46.dp })
            HomeLiquidDock(
                enabled = isHome,
                interactionEnabled = !anySelectionMode && !showPano && !showPlanner && !showHomeWidgets && !isSettingsOpen &&
                    !showAccountMenu && !showCategoryOrderEditor,
                composerExpanded = expanded || voiceWindowVisible,
                composerHeight = { composerHeightState.value },
                listState = listState,
                onOpenPlanner = {
                    voice.cancel(); focusManager.clearFocus(); keyboard?.hide()
                    showHomeWidgets = false
                    showPlanner = true
                },
                onOpenWidget = {
                    voice.cancel(); focusManager.clearFocus(); keyboard?.hide()
                    showHomeWidgets = true
                },
                onOpenPano = {
                    voice.cancel(); focusManager.clearFocus(); keyboard?.hide()
                    showHomeWidgets = false
                    showPano = true
                },
                onMicClick = onComposerMicClick,
                micEnabled = !saving && !copyingPhotos && !voiceAiParsing,
                backdrop = scene, backdropPosition = { scenePosition },
                designStyle = designStyle,
                reduceDynamicBlur = reduceDynamicBlur,
                reduceAnimations = reduceAnimations,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp)
                    .deferredBottomPadding { bottomGapState.value }
                    .graphicsLayer {
                        translationY = if (isFirstHomeIntroPlaying) {
                            val progress = composerEntranceProgress.value
                            val motionEase = RealMotion.Emphasized.transform(progress)
                            with(density) { (32.dp * (1f - motionEase)).toPx() }
                        } else {
                            selectionProgress * bottomSlideOffsetPx()
                        }
                        alpha = if (isFirstHomeIntroPlaying) {
                            val progress = composerEntranceProgress.value
                            LinearOutSlowInEasing.transform(progress)
                        } else if (isColdStarting) {
                            coldStartProgress.value
                        } else {
                            (1f - selectionProgress)
                        }
                    }
            ) {
            if (voiceWindowVisible) {
            VoiceListeningWindow(
                text = draft,
                committedText = voice.committedText,
                partialText = voice.partialText,
                phase = voice.phase,
                level = voice.level,
                aiProcessing = voiceAiParsing || pendingAutoSubmit,
                backdrop = scene, backdropPosition = { scenePosition },
                designStyle = designStyle,
                reduceDynamicBlur = reduceDynamicBlur,
                reduceAnimations = reduceAnimations,
                onMicClick = {
                    if (!saving) {
                        // A new sentence replaces the one being checked.
                        understandingCheck?.cancel?.invoke()
                        onComposerMicClick()
                    }
                },
                feedbackText = voiceFeedback,
                canSaveRawText = !saving && !copyingPhotos && draft.isNotBlank(),
                onSaveRawText = {
                    if (!saving && !copyingPhotos && draft.isNotBlank()) {
                        val visibleText = draft
                        voice.cancel() // Preserve displayed partial text; do not wait for another STT final.
                        voiceFlow.saveRawText(visibleText)
                    }
                },
                onDismissFeedback = { voiceState.feedbackText.value = null; voiceState.requestTimeListening.value = false },
                onLineCountChange = { voiceLineCount = it },
                modifier = Modifier.fillMaxWidth().deferredHeight { composerHeightState.value },
                understanding = understandingText,
                onUnderstandingPick = { index, option ->
                    understandingText = understandingText?.let { com.cagri.real.speech.VoiceUnderstanding.pick(it, index, option) }
                },
                onUnderstandingConfirm = {
                    val check = understandingCheck
                    val finalText = understandingText?.text
                    if (check != null && finalText != null) check.confirm(finalText)
                },
                onUnderstandingCorrect = {
                    val check = understandingCheck
                    val finalText = understandingText?.text
                    if (check != null && finalText != null) check.correct(finalText)
                },
                onUnderstandingPlace = understandingCheck?.takeIf { it.place != null }?.let { check -> { check.fixPlace() } }
            )
            } else {
            GlassComposer(
            value = draft, onValueChange = onDraftChange,
            alertAt = alertAt, repeatRule = repeatRule,
            requiresTimeSelection = requiresTimeSelection,
            dateHint = pendingDateHintEpochDay?.let { LocalDate.ofEpochDay(it) },
            timeOfDay = pendingTimeOfDay,
            saving = saving || copyingPhotos,
            onPickDate = {
                requestPermissionForAction(PendingNotificationAction.OPEN_DATE_PICKER)
            },
            composerDurationMinutes = composerDurationMinutes,
            onComposerDurationMinutesChange = { composerDurationMinutes = it },
            onClearDate = {
                manualDateClearedOrChanged = true
                composerDurationMinutes = null
                onAlertChange(null, RecurrenceRule.NONE)
                requiresTimeSelection = false
                pendingDateHintEpochDay = null
                pendingTimeOfDay = null
            },
            onApplyDateSuggestion = { isToday ->
                manualDateClearedOrChanged = true
                val targetDate = if (isToday) LocalDate.now(ZoneId.systemDefault()) else LocalDate.now(ZoneId.systemDefault()).plusDays(1)
                pendingDateHintEpochDay = targetDate.toEpochDay()
                requiresTimeSelection = false
                pendingTimeOfDay = null
                onAlertChange(null, RecurrenceRule.NONE)
            },
            customCategories = customCategories,
            selectedCategoryId = draftCategoryId,
            onPickCategory = { voice.cancel(); focusManager.clearFocus(); keyboard?.hide(); showCategoryPicker = true },
            onPickAttachment = handlePickComposerAttachment,
            draftAttachments = draftAttachments,
            onRemoveDraftAttachment = handleRemoveDraftAttachment,
            onSubmit = {
                if (voice.phase != VoicePhase.Idle) voice.finish()
                // "Yeri yaz": the typed place gets its address and the card is saved.
                else if (voiceState.placeFix.value != null && draft.isNotBlank()) voiceState.placeFix.value?.invoke(draft)
                // A sentence corrected by hand after "Doğru mu anladım?" goes back through the AI.
                else if (voiceState.resubmitTypedCorrection.value && draft.isNotBlank()) voiceFlow.resubmit(draft)
                else submit(draft)
            },
            expanded = expanded, onFocusChanged = { editing = it }, focusRequester = inputFocus,
            backdrop = scene, backdropPosition = { scenePosition },
            voicePhase = voice.phase, voiceLevel = voice.level,
            isAiProcessing = voiceAiParsing,
            onMicClick = onComposerMicClick,
            showCompactMicrophone = !isHome,
            showMicrophoneWhenExpanded = !isHome,
            reduceDynamicBlur = reduceDynamicBlur,
            reduceAnimations = reduceAnimations,
            designStyle = designStyle,
            isChecklistMode = isChecklistMode,
            onToggleChecklistMode = {
                isChecklistMode = !isChecklistMode
                if (!isChecklistMode) {
                    checklistDraftItems = listOf("")
                }
            },
            checklistDraftItems = checklistDraftItems,
            onChecklistItemChange = { index, newText ->
                checklistDraftItems = checklistDraftItems.toMutableList().also {
                    if (index in it.indices) it[index] = newText
                }
            },
            onAddChecklistItem = {
                checklistDraftItems = checklistDraftItems + ""
            },
            onRemoveChecklistItem = { index ->
                if (checklistDraftItems.size > 1 && index in checklistDraftItems.indices) {
                    checklistDraftItems = checklistDraftItems.toMutableList().also { it.removeAt(index) }
                }
            },
            sendImmediateNotification = sendImmediateNotification,
            isAlarm = ComposerTimingDraft.alarm,
            onToggleImmediateNotification = {
                // The bell is notification only; the alarm is picked in the date sheet (Saat pushed right).
                if (!sendImmediateNotification) {
                    if (notificationsAllowed) {
                        sendImmediateNotification = true
                    } else {
                        requestPermissionForAction(PendingNotificationAction.ENABLE_BELL)
                    }
                } else {
                    sendImmediateNotification = false
                }
            },
            voiceCommittedText = voice.committedText,
            voicePartialText = voice.partialText,
            onVoiceLineCountChange = { voiceLineCount = it },
            textLineCount = textLineCount,
            onTextLineCountChange = { textLineCount = it },
            isVoiceDraft = isVoiceDraft,
            voiceDurationMinutes = pendingVoiceDurationMinutes,
            voicePeople = pendingVoicePeople,
            voiceLocationName = ComposerTimingDraft.locationTrigger?.label() ?: pendingVoiceLocation?.placeName,
            // The panel explains location once; with permission granted the pin goes straight to the map.
            onPickLocation = {
                voice.cancel(); focusManager.clearFocus(); keyboard?.hide()
                if (com.cagri.real.notifications.LocationReminders.hasLocation(context)) { locationPickForTrigger = true; showComposerLocation = true }
                else showLocationChoice = true
            },
            locationWatching = locationWatching,
            isShareDraft = isShareDraft,
            isHomeReady = isHomeReadyForShimmer && category == null && customCategory == null && !isSettingsOpen,
            isLabsOpen = isLabsOpen,
            labsProgress = labsProgress,
            isWhatsAppArmed = isWhatsAppArmed,
            onToggleWhatsApp = onToggleWhatsApp,
            hasSavedRecipient = whatsAppRecipientRepo.hasRecipient(),
            onConfigureRecipient = { showWhatsAppRecipientSheet = true },
            onSwipeUp = { if (expanded && !isLabsOpen) isLabsOpen = true },
            onSwipeDown = { if (isLabsOpen) isLabsOpen = false },
            modifier = Modifier.fillMaxWidth().deferredHeight { composerHeightState.value }
            )
            }
            }
        }
        val bulkPlan = pendingBulkPlan
        if (bulkPlan != null) {
            VoiceBulkChipHost(
                plan = bulkPlan,
                reminders = reminders,
                callbacks = voiceCardCallbacks,
                onToggleCompleted = onToggleCompleted,
                onDeleteMany = onDeleteReminders,
                onDismiss = { pendingBulkPlan = null },
                onFeedback = { showVoiceFeedback(voiceState, it) },
                modifier = Modifier.align(Alignment.BottomCenter).zIndex(25f)
                    .padding(start = 16.dp, end = 16.dp).deferredBottomPadding { composerHeightState.value + bottomGapState.value + 16.dp }
            )
        }
        voiceState.pendingDeletePlan.value?.let { plan ->
            VoiceDeletePrompt(
                plan = plan, reminders = reminders, callbacks = voiceCardCallbacks,
                backdrop = scene, backdropPosition = { scenePosition },
                designStyle = designStyle, reduceDynamicBlur = reduceDynamicBlur,
                onDeleteMany = onDeleteReminders,
                onDismiss = { voiceState.pendingDeletePlan.value = null },
                modifier = Modifier.zIndex(30f)
            )
        }
        voiceState.retryVoiceCreation.value?.let { retry ->
            VoiceCreationRetryPrompt(
                busy = voiceAiParsing || saving,
                onRetry = retry,
                onDismiss = { voiceState.retryVoiceCreation.value = null },
                modifier = Modifier.align(Alignment.BottomCenter).zIndex(26f)
                    .padding(start = 16.dp, end = 16.dp).deferredBottomPadding { composerHeightState.value + bottomGapState.value + 16.dp }
            )
        }
        // Alarm needs two system switches: full-screen alerts (Android 14+) and exact alarms.
        com.cagri.real.ui.components.PermissionSheet(
            visible = showAlarmPermission,
            title = t("Alarm için izin ver"),
            description = t("Alarmın kilit ekranında tam ekran açılıp tam saatinde çalabilmesi için REAL'e tam ekran bildirim ve alarm izni gerekiyor."),
            onAllow = {
                showAlarmPermission = false
                if (!com.cagri.real.notifications.RealAlarm.canRingFullScreen(context)) com.cagri.real.notifications.RealAlarm.openFullScreenSettings(context)
                else if (!exactAlarmsAllowed) onRequestExactAlarms()
                // Waits underneath; it is there when the person comes back from the system page.
                if (com.cagri.real.notifications.RealAlarm.shouldAskOverPhone(context)) showOverPhoneAsk = true
            },
            onLater = {
                showAlarmPermission = false
                if (com.cagri.real.notifications.RealAlarm.shouldAskOverPhone(context)) showOverPhoneAsk = true
            },
            reduceAnimations = reduceAnimations,
            glyph = com.cagri.real.ui.components.PermissionGlyph.Bell
        )
        // Optional, asked once: full screen also while the phone is in use (otherwise Android shows a heads-up).
        com.cagri.real.ui.components.PermissionSheet(
            visible = showOverPhoneAsk && !showAlarmPermission,
            title = t("Telefon açıkken de tam ekran?"),
            description = t("Telefonu kullanırken Android alarmı üstte bildirim olarak gösterir. \"Diğer uygulamaların üzerinde göster\" iznini açarsan alarm her zaman tam ekran açılır. İstediğin zaman Ayarlar'dan değiştirebilirsin."),
            onAllow = {
                showOverPhoneAsk = false
                com.cagri.real.notifications.RealAlarm.markOverPhoneAsked(context)
                com.cagri.real.notifications.RealAlarm.openOverPhoneSettings(context)
            },
            onLater = {
                showOverPhoneAsk = false
                com.cagri.real.notifications.RealAlarm.markOverPhoneAsked(context)
            },
            reduceAnimations = reduceAnimations,
            primaryLabel = t("İzni aç"),
            secondaryLabel = t("Gerek yok"),
            glyph = com.cagri.real.ui.components.PermissionGlyph.Bell
        )
        // Composer "Konum": the card detail's map picker; the pin rides on the draft until the card is saved.
        // The pin opens a permission-style panel that also is the disclosure Google asks for. "Konuma varınca
        // hatırlat" then goes straight to Android's own dialogs: precise/approximate + while using, then
        // "all the time" so the reminder works with REAL closed.
        fun openTriggerPicker() { locationPickForTrigger = true; showComposerLocation = true }
        val backgroundLocationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) android.widget.Toast.makeText(context, t("\"Her zaman izin ver\" seçilmezse hatırlatma REAL kapalıyken gelmez."), android.widget.Toast.LENGTH_LONG).show()
            openTriggerPicker()
        }
        val locationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val precise = result[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
            val approximate = result[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
            when {
                precise && !com.cagri.real.notifications.LocationReminders.hasBackground(context) ->
                    backgroundLocationLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                precise -> openTriggerPicker()
                approximate -> android.widget.Toast.makeText(context, t("Konuma varınca hatırlatma için \"Tam konum\" gerekir."), android.widget.Toast.LENGTH_LONG).show()
                // Denied twice before: Android answers "no" without showing its dialog, so only Settings is left.
                (context as? android.app.Activity)?.let {
                    !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(it, android.Manifest.permission.ACCESS_FINE_LOCATION)
                } == true -> {
                    android.widget.Toast.makeText(context, t("Konum izni kapalı. Ayarlar > İzinler > Konum'dan açabilirsin."), android.widget.Toast.LENGTH_LONG).show()
                    context.startActivity(android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${context.packageName}")
                    ))
                }
            }
        }
        com.cagri.real.ui.components.PermissionSheet(
            visible = showLocationChoice,
            title = t("Konum"),
            description = t("Yeri karta ekleyebilir ya da o yere varınca (veya oradan ayrılınca) bir kere hatırlatılabilirsin. Hatırlatma için REAL konumunu yalnızca bekleyen bir konum hatırlatman varken kullanır; REAL kapalıyken de çalışması için \"Her zaman izin ver\"i seç. Hatırlatma gelince takip biter, konum kullanılırken konum düğmesinde küçük bir nokta görünür."),
            // First choice: just add the place; reminding on arrival / departure is the second one.
            onAllow = { showLocationChoice = false; locationPickForTrigger = false; showComposerLocation = true },
            onLater = {
                showLocationChoice = false
                when {
                    !com.cagri.real.notifications.LocationReminders.hasLocation(context) -> locationLauncher.launch(arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                    !com.cagri.real.notifications.LocationReminders.hasBackground(context) ->
                        backgroundLocationLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    else -> openTriggerPicker()
                }
            },
            onDismiss = { showLocationChoice = false },
            reduceAnimations = reduceAnimations,
            primaryLabel = t("Konum ekle"),
            secondaryLabel = t("Varınca / ayrılınca hatırlat"),
            glyph = com.cagri.real.ui.components.PermissionGlyph.Pin
        )
        // The pin closed the keyboard; once the map is done the composer comes back up so the person keeps typing.
        var refocusComposer by remember { mutableIntStateOf(0) }
        LaunchedEffect(refocusComposer) {
            if (refocusComposer == 0) return@LaunchedEffect
            kotlinx.coroutines.delay(260)
            runCatching { inputFocus.requestFocus() }
            keyboard?.show()
        }
        com.cagri.real.ui.components.LocationPickerSheet(
            visible = showComposerLocation,
            initial = pendingVoiceLocation,
            onSave = { place ->
                showComposerLocation = false
                pendingVoiceLocation = place
                ComposerTimingDraft.locationTrigger = null
                refocusComposer++
            },
            // "Varınca / Ayrılınca hatırlat" on the map itself, offered once location permission is there.
            initialTrigger = ComposerTimingDraft.locationTrigger,
            onRemind = if (locationPickForTrigger) { place, trigger ->
                showComposerLocation = false
                pendingVoiceLocation = place
                ComposerTimingDraft.locationTrigger = trigger
                refocusComposer++
            } else null,
            // The Google Maps share hand-off needs a saved card, so for a draft it only closes the picker.
            onShareFromGoogleMaps = { showComposerLocation = false },
            onCancel = { showComposerLocation = false; refocusComposer++ }
        )
        CategorySelectionOverlay(
            visible = showCategoryPicker,
            modifier = Modifier.zIndex(20f),
            customCategories = customCategories,
            selectedCategoryId = draftCategoryId,
            onSelectCategory = { draftCategoryId = it; voiceCategoryExplicit = true },
            onCreateCategoryClick = { showCreateCategoryDialog = true },
            onDismiss = { showCategoryPicker = false },
            backdrop = scene,
            backdropPosition = { scenePosition },
            designStyle = designStyle,
            reduceDynamicBlur = reduceDynamicBlur,
            reduceAnimations = reduceAnimations
        )
        CategorySelectionOverlay(
            visible = showBulkCategoryPicker,
            modifier = Modifier.zIndex(20f),
            customCategories = customCategories,
            selectedCategoryId = null,
            onSelectCategory = { catId ->
                val toUpdate = selectedIds
                selectedIds = emptySet()
                showBulkCategoryPicker = false
                onBulkSetCategory(toUpdate, catId)
            },
            onCreateCategoryClick = { showCreateCategoryDialog = true },
            onDismiss = { showBulkCategoryPicker = false },
            backdrop = scene,
            backdropPosition = { scenePosition },
            designStyle = designStyle,
            reduceDynamicBlur = reduceDynamicBlur,
            reduceAnimations = reduceAnimations
        )
        if (selectionProgress > 0f || isSelectionMode || isCategorySelectionMode) {
            val count = if (isCategorySelectionMode) selectedCategoryIds.size else selectedIds.size
            SelectionActionButtons(
                selectedCount = count,
                onCancel = {
                    if (isCategorySelectionMode) selectedCategoryIds = emptySet()
                    else selectedIds = emptySet()
                },
                onDelete = {
                    if (isCategorySelectionMode) {
                        val toDelete = selectedCategoryIds
                        selectedCategoryIds = emptySet()
                        onDeleteCategories(toDelete)
                    } else {
                        val toDelete = selectedIds
                        selectedIds = emptySet()
                        onDeleteReminders(toDelete)
                    }
                },
                enabled = isSelectionMode || isCategorySelectionMode,
                onCheck = if (isCategorySelectionMode) null else ({ openCheckSheet() }),
                backdrop = scene,
                backdropPosition = { scenePosition },
                reduceDynamicBlur = reduceDynamicBlur,
                designStyle = designStyle,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp).deferredBottomPadding { bottomGapState.value }.height(58.dp)
                    .graphicsLayer {
                        translationY = (1f - selectionProgress) * bottomSlideOffsetPx()
                        alpha = selectionProgress
                    }
            )
        }
        if (showAccountMenu && category == null && customCategory == null) {
            AccountMenuOverlay(
                backdrop = scene,
                backdropPosition = { scenePosition },
                onDismiss = { showAccountMenu = false },
                onSettingsClick = onOpenSettings,
                onCategoryOrderClick = { showCategoryOrderEditor = true },
                reduceDynamicBlur = reduceDynamicBlur,
                reduceAnimations = reduceAnimations,
                designStyle = designStyle,
                userName = userName
            )
        }
        if (showCategoryOrderEditor && category == null && customCategory == null) {
            CategoryOrderEditorDialog(
                customCategories = customCategories,
                currentOrderKeys = categoryOrderKeys,
                currentPreviewCount = homeCategoryPreviewCount,
                backdrop = scene,
                backdropPosition = { scenePosition },
                reduceDynamicBlur = reduceDynamicBlur,
                designStyle = designStyle,
                onDismiss = { showCategoryOrderEditor = false },
                onSave = { orderedKeys, previewCount ->
                    onCategoryOrderChange(orderedKeys)
                    homeCategoryPreviewCount = previewCount
                    uiPreferences.edit()
                        .putInt("home_category_preview_count_v1", previewCount)
                        .let { com.cagri.real.platform.android.sync.SyncLocal.saveUiPreference(uiPreferences, it) }
                    showCategoryOrderEditor = false
                },
                reduceAnimations = reduceAnimations
            )
        }
        val pickerInitialMillis = alertAt ?: run {
            if (pendingDateHintEpochDay != null) {
                val hintDate = LocalDate.ofEpochDay(pendingDateHintEpochDay!!)
                val defaultHour = when (pendingTimeOfDay) {
                    TimeOfDay.Morning -> 9
                    TimeOfDay.Noon -> 13
                    TimeOfDay.Evening -> 19
                    TimeOfDay.Night -> 22
                    null -> {
                        val now = java.time.LocalTime.now(java.time.ZoneId.systemDefault())
                        if (hintDate == LocalDate.now(java.time.ZoneId.systemDefault())) {
                            (now.hour + 1).coerceIn(0, 23)
                        } else {
                            12
                        }
                    }
                }
                hintDate.atTime(defaultHour, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else null
        }
        AnimatedDateTimePickerOverlay(
            visible = showDatePicker,
            initial = pickerInitialMillis,
            initialRepeatRule = repeatRule,
            initialTimeTab = (requiresTimeSelection && alertAt == null),
            initialEarlyAlertMinutes = ComposerTimingDraft.earlyAlertMinutes,
            initialEndAt = ComposerTimingDraft.endAt,
            onConfirmTiming = { early, end -> ComposerTimingDraft.earlyAlertMinutes = early; ComposerTimingDraft.endAt = end },
            initialAlarm = ComposerTimingDraft.alarm,
            onConfirmAlarm = { on ->
                val turnedOn = on && !ComposerTimingDraft.alarm
                ComposerTimingDraft.alarm = on
                if (turnedOn) {
                    if (!com.cagri.real.notifications.RealAlarm.canRingFullScreen(context) || !exactAlarmsAllowed) showAlarmPermission = true
                    else if (com.cagri.real.notifications.RealAlarm.shouldAskOverPhone(context)) showOverPhoneAsk = true
                }
            },
            backdrop = scene, backdropPosition = { scenePosition },
            designStyle = designStyle, reduceDynamicBlur = reduceDynamicBlur,
            reduceAnimations = reduceAnimations,
            onDismiss = { showDatePicker = false },
            onConfirm = { at, rule ->
                manualDateClearedOrChanged = true
                onAlertChange(at, rule)
                requiresTimeSelection = false
                pendingDateHintEpochDay = null
                pendingTimeOfDay = null
                refocus = true
                showDatePicker = false
            },
            exactAlarmsAllowed = exactAlarmsAllowed, onRequestExactAlarms = onRequestExactAlarms
        )
        NotificationPermissionSheet(
            visible = showNotificationPermissionSheet,
            onAllow = {
                showNotificationPermissionSheet = false
                onRequestNotificationPermission()
            },
            onLater = {
                showNotificationPermissionSheet = false
                pendingNotificationAction = PendingNotificationAction.NONE
                refocus = true
            },
            reduceAnimations = reduceAnimations
        )
        MicrophonePermissionSheet(
            visible = showMicrophonePermissionSheet,
            onAllow = {
                showMicrophonePermissionSheet = false
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onLater = {
                showMicrophonePermissionSheet = false
                pendingMicrophoneAction = false
            },
            reduceAnimations = reduceAnimations
        )
        WhatsAppConsentSheet(
            visible = showWhatsAppConsentSheet,
            onAllow = {
                whatsAppConsentRepo.setConsent(true)
                showWhatsAppConsentSheet = false
                if (!whatsAppRecipientRepo.hasRecipient()) {
                    showWhatsAppRecipientSheet = true
                } else {
                    isWhatsAppArmed = true
                }
            },
            onDismiss = {
                showWhatsAppConsentSheet = false
            },
            reduceAnimations = reduceAnimations
        )
        WhatsAppRecipientSheet(
            visible = showWhatsAppRecipientSheet,
            initialPhoneNumber = whatsAppRecipientRepo.getRecipient(),
            onSaveRecipient = { normalizedPhone ->
                whatsAppRecipientRepo.setRecipient(normalizedPhone)
                isWhatsAppArmed = true
                showWhatsAppRecipientSheet = false
            },
            onDismiss = {
                showWhatsAppRecipientSheet = false
            },
            reduceAnimations = reduceAnimations
        )
        if (editingBuiltInCategory != null) {
            val targetCat = editingBuiltInCategory!!
            CategoryEditor(
                builtInCategory = targetCat,
                builtInColorOverride = builtInCategoryColors[targetCat],
                backdrop = scene,
                backdropPosition = { scenePosition },
                onDismiss = { editingBuiltInCategory = null },
                onSaveBuiltInColor = { cat, override ->
                    onUpdateBuiltInCategoryColor(cat, override)
                    editingBuiltInCategory = null
                },
                designStyle = designStyle,
                reduceDynamicBlur = reduceDynamicBlur,
                reduceAnimations = reduceAnimations
            )
        }
        if (showCreateCategoryDialog) {
            CategoryEditor(
                customCategory = null,
                backdrop = scene,
                backdropPosition = { scenePosition },
                onDismiss = { showCreateCategoryDialog = false },
                onSaveCustomCategory = { name, iconKey, start, end, hide, useDarkText, blendFraction, customSolidColorArgb, desc ->
                    onCreateCategory(name, iconKey, start, end, hide, useDarkText, blendFraction, customSolidColorArgb, desc)
                    showCreateCategoryDialog = false
                },
                designStyle = designStyle,
                reduceDynamicBlur = reduceDynamicBlur,
                reduceAnimations = reduceAnimations
            )
        }
        if (showPeopleSheet && customCategory != null) {
            val peopleContext = LocalContext.current
            com.cagri.real.ui.sharedcategories.SharedCategorySheet(
                category = customCategory,
                calendarLinked = remember(customCategory.id) {
                    com.cagri.real.calendar.CalendarCategoryLinks(peopleContext).hasCategory(customCategory.id)
                },
                onDismiss = { showPeopleSheet = false }
            )
        }
        if (showEditCategoryDialog && customCategory != null) {
            CategoryEditor(
                customCategory = customCategory,
                backdrop = scene,
                backdropPosition = { scenePosition },
                onDismiss = { showEditCategoryDialog = false },
                onSaveCustomCategory = { name, iconKey, start, end, hide, useDarkText, blendFraction, customSolidColorArgb, _ ->
                    val c = customCategory
                    // A member changes only this phone's hide switch; the rest is the owner's (sync keeps it in step).
                    if (c.isSharedMirror) onEditCategory(c.id, c.name, c.iconKey, c.accentStart, c.accentEnd, hide, c.useDarkText, c.gradientBlendFraction, c.customSolidColorArgb)
                    else onEditCategory(c.id, name, iconKey, start, end, hide, useDarkText, blendFraction, customSolidColorArgb)
                    showEditCategoryDialog = false
                },
                onDeleteCustomCategory = { deleteReminders ->
                    onDeleteCategory(customCategory.id, deleteReminders)
                    showEditCategoryDialog = false
                },
                designStyle = designStyle,
                reduceDynamicBlur = reduceDynamicBlur,
                reduceAnimations = reduceAnimations
            )
        }
        com.cagri.real.ui.motion.RealModuleTransition(
            visible = showPano,
            reduceMotion = reduceAnimations,
            modifier = Modifier.zIndex(80f)
        ) {
            com.cagri.real.ui.pano.PanoScreen(reminders, designStyle, onDismiss = { showPano = false },
                categories = customCategories, requestedBoard = pendingPanoBoardId, onRequestHandled = onPanoLocationHandled)
        }
        com.cagri.real.ui.motion.RealModuleTransition(
            visible = showHomeWidgets,
            reduceMotion = reduceAnimations || yasamQuickReturn,
            modifier = Modifier.zIndex(80f)
        ) {
            HomeWidgetScreen(
                moodEntries = allMoodTrackerEntries,
                initialDestination = widgetDestination,
                onInitialDestinationConsumed = { widgetDestination = null },
                onBooksOpenChange = { booksOpen = it },
                reminders = generalReminders,
                journalReminders = journalReminders,
                onOpenJournalCard = { id ->
                    // Detay Yaşam'ın üstünde açılır; kapanınca Yaşam Günlüklerim modunda geri gelir.
                    returnToYasamJournals = true
                    onOpenReminder(id)
                },
                startInJournals = returnToYasamJournals,
                onStartInJournalsConsumed = { returnToYasamJournals = false },
                quickReturn = yasamQuickReturn,
                onQuickReturnConsumed = { yasamQuickReturn = false },
                designStyle = designStyle,
                journalColor = builtInCategoryColors[ReminderCategory.JournalEntries],
                onDismiss = { showHomeWidgets = false },
                onOpenMoodTracker = {
                    // Treat Widget -> Moods as real navigation, not two full-screen layers
                    // rendered at the same z-index. This keeps the mood flow in front.
                    showHomeWidgets = false
                    returnToHomeWidgetsAfterMood = true
                    showMoodTrackerHistory = true
                },
                onAddMood = {
                    // Yaşam ⊕: mood kayıt akışı doğrudan açılır; kayıt/iptal sonrası Yaşam'a dönülür.
                    homeMoodSelection = JournalMood.NEUTRAL
                    showHomeWidgets = false
                    returnToHomeWidgetsAfterMood = true
                    moodAddFromYasam = true
                    showHomeMood = true
                },
                reduceAnimations = reduceAnimations,
                reduceDynamicBlur = reduceDynamicBlur,
                onToggleReminder = onToggleCompleted,
                onNewJournal = { showYasamJournal = true },
                onAddMedia = { media ->
                    reminders.firstOrNull { reminder -> reminder.mediaBlocks.any { existing ->
                        existing.type == media.type && existing.provider == media.provider &&
                            (if (media.providerId != null) existing.providerId == media.providerId else existing.providerUrl == media.providerUrl)
                    } }?.id ?: onAddSuggestedReal(media.title, null, media, null)
                },
                onAddGame = { game ->
                    reminders.firstOrNull { reminder -> reminder.gameBlocks.any { it.providerId == game.providerId } }?.id ?: onAddGame(game)
                },
                onAddMusic = { music ->
                    reminders.firstOrNull { reminder -> reminder.musicBlocks.any { it.providerUrl == music.providerUrl } }?.id
                        ?: onAddSuggestedReal(listOf(music.title, music.artist).filter { it.isNotBlank() }.joinToString(" – "), music, null, null)
                }
            )
        }
        if (showYasamJournal) {
            YasamJournalSheet(
                onDismiss = { showYasamJournal = false },
                onSave = { text ->
                    val title = STANDALONE_JOURNAL_PREFIX + java.time.LocalDate.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale("tr")))
                    onCreateJournal(title, text)
                }
            )
        }
        // Planner owns its Dialog entrance/exit; retain it until its slide completes.
        if (showPlanner) {
            PlannerScreen(
                reminders = reminders,
                ownerName = userName,
                customCategories = customCategories,
                designStyle = designStyle,
                reduceDynamicBlur = reduceDynamicBlur,
                reduceAnimations = reduceAnimations,
                exactAlarmsAllowed = exactAlarmsAllowed,
                onRequestExactAlarms = onRequestExactAlarms,
                onDismiss = { showPlanner = false; plannerGoalCategoryId = null },
                initialGoalCategoryId = plannerGoalCategoryId,
                onCreateCalendarCategory = { name ->
                    // First palette colour no category uses yet; the user can recolour it later.
                    val used = customCategories.mapTo(HashSet()) { it.accentStart }
                    val color = com.cagri.real.ui.components.ReminderColorDisplayOrder.drop(2).firstOrNull { it !in used } ?: 9
                    onCreateCategory(name, "NONE", color, null, false, false, null, null, null)
                },
                onSetReminderAlert = onPlannerSetAlert,
                onToggleReminder = onToggleCompleted,
                onSetCompletionNote = onSetCompletionNote,
                // Pencil popup on the category screen: a picked pastel is saved as the category's solid color.
                onUpdateCategoryColor = { categoryId, argb ->
                    customCategories.firstOrNull { it.id == categoryId }?.let { c ->
                        onEditCategory(c.id, c.name, c.iconKey, c.accentStart, null, c.hideFromGeneralLists,
                            true, null, argb)
                    }
                },
                onOpenCategory = { builtIn, customId ->
                    showPlanner = false
                    val fromCategory = plannerGoalCategoryId
                    plannerGoalCategoryId = null
                    // Opened from that category's own screen: going "to the category" is just closing the goals.
                    if (fromCategory != null && fromCategory == customId) Unit
                    else if (customId != null) customCategories.firstOrNull { it.id == customId }?.let(onOpenCustomCategory)
                    else if (builtIn != null) onOpenCategory(builtIn)
                },
                detailContent = plannerDetailContent,
                onCreateCalendarCard = { title ->
                    onAddReminder(title, null, RecurrenceRule.NONE, null, false,
                        emptyList(), false, null, emptyList(), emptyList(), null, null, null, emptyList(), emptyList())
                },
                onCreateGoal = { title, categoryId ->
                    onAddReminder(title, null, RecurrenceRule.NONE, categoryId, false,
                        emptyList(), false, null, emptyList(), emptyList(), null, null, null, emptyList(), emptyList())
                },
                onCreateReminder = { title, startMillis, rule, categoryId ->
                    onAddReminder(title, startMillis, rule, categoryId, false,
                        emptyList(), false, null, emptyList(), emptyList(), null, null, null, emptyList(), emptyList())
                }
            )
        }
        AnimatedVisibility(
            visible = showMoodTrackerHistory,
            enter = systemCategoryEnter,
            exit = if (reduceAnimations) ExitTransition.None else slideOutHorizontally(
                tween(280, easing = RealMotion.StandardInOut)
            ) { it },
            modifier = Modifier.zIndex(80f)
        ) {
            MoodTrackerHistoryScreen(
                entries = allMoodTrackerEntries,
                onDismiss = {
                    showMoodTrackerHistory = false
                    if (returnToHomeWidgetsAfterMood) {
                        yasamQuickReturn = true
                        showHomeWidgets = true
                        returnToHomeWidgetsAfterMood = false
                    }
                },
                onAddMood = {
                    homeMoodSelection = JournalMood.NEUTRAL
                    showMoodTrackerHistory = false
                    showHomeMood = true
                },
                reduceAnimations = reduceAnimations
            )
        }
        if (showHomeMood) {
            MoodV3FullScreen(
                selectedMood = homeMoodSelection,
                onMoodSelected = { homeMoodSelection = it },
                onDismiss = {
                    showHomeMood = false
                    if (moodAddFromYasam) {
                        // Yaşam ⊕: geri doğrudan Yaşam'a; mood geçmişi açılmaz.
                        yasamQuickReturn = true
                        showHomeWidgets = true
                        returnToHomeWidgetsAfterMood = false
                        moodAddFromYasam = false
                    }
                    // Opened from the Home context card: go straight back Home, not to mood history.
                    else if (moodOpenedFromContextCard) moodOpenedFromContextCard = false
                    else showMoodTrackerHistory = true
                },
                onSave = { mood, details ->
                    onAddHomeMood(mood, details)
                    moodOpenedFromContextCard = false
                    moodAddFromYasam = false
                    showHomeMood = false
                    activeSuggestionMood = mood
                    activeSuggestionDetails = details
                    showMoodSuggestions = true
                },
                initialDetails = null,
                reduceAnimations = reduceAnimations
            )
        }
        if (showMoodSuggestions && activeSuggestionMood != null && activeSuggestionDetails != null) {
            val finishMoodSuggestions: () -> Unit = {
                showMoodSuggestions = false
                activeSuggestionMood = null
                activeSuggestionDetails = null
                showMoodTrackerHistory = false
                showHomeMood = false
                if (returnToHomeWidgetsAfterMood) {
                    yasamQuickReturn = true
                    showHomeWidgets = true
                    returnToHomeWidgetsAfterMood = false
                }
            }
            // The mood is already saved. Free accounts skip recommendations entirely;
            // do not construct the AI screen, request consent, or open an upgrade panel.
            var canSuggest by remember { mutableStateOf(false) }
            LaunchedEffect(activeSuggestionMood, activeSuggestionDetails) {
                // Skip only when the account is known to be free. If the access check itself fails (network,
                // App Check, timeout) the screen still opens: the server decides and any problem is shown there
                // instead of the suggestions silently never appearing.
                val uid = com.cagri.real.platform.android.auth.AndroidBackend.auth.currentUser()?.id
                val access = if (uid == null) null else try {
                    com.cagri.real.platform.android.access.RealAccessRepository.cached(uid)
                        ?: com.cagri.real.platform.android.access.RealAccessRepository.refresh(uid)
                } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
                if (uid != null && (access == null || access.snapshot.allows(com.cagri.real.access.RealFeature.MoodAi))) {
                    canSuggest = true
                } else finishMoodSuggestions()
            }
            if (canSuggest) MoodSuggestionsScreen(
                mood = activeSuggestionMood!!,
                details = activeSuggestionDetails!!,
                onDismiss = finishMoodSuggestions,
                onAddSuggestedReal = onAddSuggestedReal,
                onUndoSuggestedReal = onUndoSuggestedReal,
                reduceAnimations = reduceAnimations
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color(0xFFF2F2F4), fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp))
}

@Composable
fun BottomFade(modifier: Modifier = Modifier) {
    val brush = remember {
        Brush.verticalGradient(
            0f to Color.Transparent, 0.6f to Color.Black.copy(alpha = 0.2f), 1f to Color.Black.copy(alpha = 0.92f)
        )
    }
    Box(modifier.fillMaxWidth().background(brush))
}

/** Owns the chip's 20 s auto-dismiss and applies the confirmed plan. Kept separate so the big screen stays small. */
@Composable
private fun VoiceBulkChipHost(
    plan: VoiceBulkPlan,
    reminders: List<ReminderItem>,
    callbacks: VoiceCardCallbacks,
    onToggleCompleted: (Long) -> Unit,
    onDeleteMany: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    LaunchedEffect(plan) {
        delay(20_000L)
        onDismiss()
    }
    VoiceBulkConfirmChip(
        plan = plan,
        onConfirm = {
            onDismiss()
            val outcome = applyVoiceBulkPlan(plan, reminders, callbacks, onToggleCompleted, onDeleteMany)
            if (outcome.success) RealHaptics.success(view)
            onFeedback(outcome.message)
        },
        onCancel = onDismiss,
        modifier = modifier
    )
}

/** Confirmation chip for bulk voice commands: shows exactly what will happen; nothing runs until "Uygula". */
@Composable
private fun VoiceBulkConfirmChip(
    plan: VoiceBulkPlan,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xF21C1C1E)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(plan.question, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    t("Uygula"),
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(Color(0xFF3D8BFF), RoundedCornerShape(14.dp))
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                )
                Text(
                    t("Vazgeç"),
                    color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                )
            }
        }
    }
}
