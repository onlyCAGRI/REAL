package com.cagri.bigboscagri.ui

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
import com.cagri.bigboscagri.data.ReminderAttachment
import com.cagri.bigboscagri.data.AttachmentStorage
import com.cagri.bigboscagri.speech.TimeOfDay
import com.cagri.bigboscagri.ui.components.AnimatedDateTimePickerOverlay
import com.cagri.bigboscagri.ui.components.NotificationPermissionSheet
import com.cagri.bigboscagri.ui.components.MicrophonePermissionSheet
import com.cagri.bigboscagri.ui.components.RealWordmark
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.cagri.bigboscagri.ui.components.pressScale
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
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
import com.cagri.bigboscagri.ui.components.RealHaptics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.bigboscagri.R
import com.cagri.bigboscagri.data.RecurrenceRule
import com.cagri.bigboscagri.data.ChecklistItem
import com.cagri.bigboscagri.data.ReminderCategory
import com.cagri.bigboscagri.data.ReminderItem
import com.cagri.bigboscagri.data.inCategory
import com.cagri.bigboscagri.speech.VoicePhase
import com.cagri.bigboscagri.speech.VoiceReminderParser
import com.cagri.bigboscagri.speech.VoiceDraftCoordinator
import com.cagri.bigboscagri.speech.rememberVoiceInput
import com.cagri.bigboscagri.data.CategoryColorOverride
import com.cagri.bigboscagri.data.CustomCategory
import com.cagri.bigboscagri.ui.components.AccountMenuOverlay
import com.cagri.bigboscagri.ui.components.ActionGlyph
import com.cagri.bigboscagri.ui.components.ActionIcon
import com.cagri.bigboscagri.ui.components.CategoryEditor
import com.cagri.bigboscagri.ui.components.CategoryGlyph
import com.cagri.bigboscagri.ui.components.CategoryGrid
import com.cagri.bigboscagri.ui.components.CategorySelectionOverlay
import com.cagri.bigboscagri.ui.components.CreateCategoryDialog
import com.cagri.bigboscagri.ui.components.GlassComposer
import com.cagri.bigboscagri.ui.components.GuestAvatar
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.text.style.TextOverflow
import com.cagri.bigboscagri.ui.components.CategoryIcon
import com.cagri.bigboscagri.ui.components.CategoryIconGlyph
import com.cagri.bigboscagri.ui.components.ReminderCard
import com.cagri.bigboscagri.ui.components.ReminderColorChoices
import com.cagri.bigboscagri.data.WhatsAppConsentRepository
import com.cagri.bigboscagri.data.WhatsAppRecipientRepository
import com.cagri.bigboscagri.data.WhatsAppSender
import com.cagri.bigboscagri.ui.components.WhatsAppConsentSheet
import com.cagri.bigboscagri.ui.components.WhatsAppRecipientSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.cagri.bigboscagri.ui.components.ReminderEntryTracker
import com.cagri.bigboscagri.ui.components.SelectionActionButtons
import com.cagri.bigboscagri.ui.components.TopGlassOverlay
import com.cagri.bigboscagri.ui.components.glyph
import com.cagri.bigboscagri.ui.components.resolveStyle
import com.cagri.bigboscagri.data.ReminderSortOrder
import com.cagri.bigboscagri.data.filterAndSortReminders
import com.cagri.bigboscagri.data.HOME_COMPLETED_PREVIEW_LIMIT
import com.cagri.bigboscagri.ui.components.CategoryColorFilterButton
import com.cagri.bigboscagri.ui.components.CategoryColorSelectorHeader
import com.cagri.bigboscagri.ui.components.CategoryEditButton
import com.cagri.bigboscagri.ui.components.HomeSortControl
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class ReminderRoute(val category: ReminderCategory?, val customCategoryId: Long?, val detailId: Long?) {
    val key: String get() = detailId?.let { "detail_$it" }
        ?: customCategoryId?.let { "custom_cat_$it" }
        ?: category?.name
        ?: "home"
}

@Composable
fun ReminderScreen(
    reminders: List<ReminderItem>,
    onAddReminder: suspend (String, Long?, RecurrenceRule, Long?, Boolean, List<ChecklistItem>, Boolean, String?, List<ReminderAttachment>) -> Long?,
    onToggleCompleted: (Long) -> Unit,
    onToggleImportant: (Long) -> Unit,
    onEditReminder: (Long, String, Int?, Int?, Long?, List<ChecklistItem>?, Int?, Float?, Int?, List<ReminderAttachment>?) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    onSetAlert: (Long, Long?, RecurrenceRule) -> Unit,
    onToggleChecklistItem: (Long, Long) -> Unit = { _, _ -> },
    onDeleteReminders: (Set<Long>) -> Unit = { ids -> ids.forEach { onDeleteReminder(it) } },
    exactAlarmsAllowed: Boolean = true,
    onRequestExactAlarms: () -> Unit = {},
    requestedReminderId: Long? = null,
    onRequestHandled: () -> Unit = {},
    reduceDynamicBlur: Boolean = false,
    onReduceDynamicBlurChange: (Boolean) -> Unit = {},
    reduceAnimations: Boolean = false,
    onReduceAnimationsChange: (Boolean) -> Unit = {},
    designStyle: DesignStyle = DesignStyle.Glass,
    onDesignStyleChange: (DesignStyle) -> Unit = {},
    customCategories: List<CustomCategory> = emptyList(),
    onCreateCategory: (String, String, Int, Int?) -> Unit = { _, _, _, _ -> },
    onEditCategory: (Long, String, String, Int, Int?) -> Unit = { _, _, _, _, _ -> },
    onDeleteCategory: (Long) -> Unit = {},
    onDeleteCategories: (Set<Long>) -> Unit = { ids -> ids.forEach { onDeleteCategory(it) } },
    onOpenCustomCategory: (CustomCategory) -> Unit = {},
    builtInCategoryColors: Map<ReminderCategory, CategoryColorOverride> = emptyMap(),
    onUpdateBuiltInCategoryColor: (ReminderCategory, CategoryColorOverride?) -> Unit = { _, _ -> },
    pendingShareDraft: com.cagri.bigboscagri.data.SharedPayload? = null,
    onShareDraftHandled: () -> Unit = {},
    userName: String = "",
    notificationsAllowed: Boolean = true,
    onRequestNotifications: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    isFirstHomeIntro: Boolean = false,
    onFirstHomeIntroComplete: () -> Unit = {},
    onPickAttachment: () -> Unit = {}
) {
    var category by rememberSaveable { mutableStateOf<ReminderCategory?>(null) }
    var customCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
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
    val scope = rememberCoroutineScope()
    var detailExitJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(requestedReminderId) {
        if (requestedReminderId != null) {
            ColdStartTracker.markExecuted()
            if (reminders.any { it.id == requestedReminderId }) { detailId = requestedReminderId; showSettings = false }
            onRequestHandled()
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
    BackHandler(enabled = !showSettings && (detailId != null || category != null || customCategoryId != null)) {
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
                        tween(220, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f))
                    ) + scaleIn(
                        tween(260, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)),
                        initialScale = 0.98f
                    ) + slideInVertically(
                        tween(260, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f))
                    ) { it / 32 }

                    val exitAnim = fadeOut(
                        tween(180, easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f))
                    ) + scaleOut(
                        tween(200, easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)),
                        targetScale = 0.98f
                    )

                    enterAnim togetherWith exitAnim
                }
            },
            label = "settingsPage"
        ) { settingsVisible ->
            if (settingsVisible) {
                SettingsScreen(
                    reduceDynamicBlur = reduceDynamicBlur,
                    onReduceDynamicBlurChange = onReduceDynamicBlurChange,
                    reduceAnimations = reduceAnimations,
                    onReduceAnimationsChange = onReduceAnimationsChange,
                    designStyle = designStyle,
                    onDesignStyleChange = onDesignStyleChange,
                    notificationsAllowed = notificationsAllowed,
                    onRequestNotifications = onRequestNotifications,
                    exactAlarmsAllowed = exactAlarmsAllowed, onRequestExactAlarms = onRequestExactAlarms,
                    onBack = { showSettings = false }
                )
            } else {
                @OptIn(ExperimentalSharedTransitionApi::class)
                SharedTransitionLayout {
                    AnimatedContent(
                        targetState = ReminderRoute(category, customCategoryId, detailId),
                        contentKey = { it.key },
                        transitionSpec = {
                            if (reduceAnimations) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                val isOpeningDetail = initialState.detailId == null && targetState.detailId != null
                                val isClosingDetail = initialState.detailId != null && targetState.detailId == null

                                when {
                                    isOpeningDetail -> {
                                        fadeIn(
                                            tween(260, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f))
                                        ) togetherWith fadeOut(
                                            tween(180, easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f))
                                        )
                                    }
                                    isClosingDetail -> {
                                        fadeIn(
                                            tween(200, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f))
                                        ) togetherWith fadeOut(
                                            tween(240, easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f))
                                        )
                                    }
                                    else -> {
                                        val enter = fadeIn(
                                            tween(220, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f))
                                        ) + scaleIn(
                                            tween(240, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)),
                                            initialScale = 0.98f
                                        ) + slideInVertically(
                                            tween(240, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f))
                                        ) { it / 36 }

                                        val exit = fadeOut(
                                            tween(170, easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f))
                                        ) + scaleOut(
                                            tween(200, easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)),
                                            targetScale = 0.98f
                                        )

                                        enter togetherWith exit
                                    }
                                }
                            }
                        }, label = "reminderPage"
                    ) { route ->
                        stateHolder.SaveableStateProvider(route.key) {
                            val currentReminder = reminders.firstOrNull { it.id == route.detailId }
                            // Keep the outgoing detail visible during its close animation after deletion.
                            var lastReminder by remember(route.detailId) { mutableStateOf(currentReminder) }
                            LaunchedEffect(currentReminder) {
                                if (currentReminder != null) lastReminder = currentReminder
                            }
                            val reminder = currentReminder ?: lastReminder
                            if (route.detailId != null && reminder != null) {
                                ReminderDetailScreen(
                                    reminder = reminder,
                                    onBack = {
                                        detailExitJob?.cancel()
                                        detailExitJob = null
                                        detailId = null
                                    },
                                    onToggleCompleted = {
                                        detailExitJob?.cancel()
                                        detailExitJob = null
                                        val wasCompleted = reminder.isCompleted
                                        onToggleCompleted(reminder.id)
                                        if (reminder.repeatRule == RecurrenceRule.NONE && !wasCompleted) {
                                            if (reduceAnimations) {
                                                detailId = null
                                            } else {
                                                detailExitJob = scope.launch {
                                                    delay(220)
                                                    detailId = null
                                                    detailExitJob = null
                                                }
                                            }
                                        }
                                    },
                                    onToggleImportant = { onToggleImportant(reminder.id) },
                                    onToggleChecklistItem = onToggleChecklistItem,
                                    onSave = { text, accentStart, accentEnd, customCatId, checklistItems, starBorderColorIndex, gradientBlendFraction, customSolidColorArgb, attachments ->
                                        onEditReminder(reminder.id, text, accentStart, accentEnd, customCatId, checklistItems, starBorderColorIndex, gradientBlendFraction, customSolidColorArgb, attachments)
                                    },
                                    onDelete = { onDeleteReminder(reminder.id); detailId = null },
                                    customCategories = customCategories,
                                    reduceAnimations = reduceAnimations, reduceDynamicBlur = reduceDynamicBlur,
                                    designStyle = designStyle, onSetAlert = { at, rule -> onSetAlert(reminder.id, at, rule) },
                                    exactAlarmsAllowed = exactAlarmsAllowed, onRequestExactAlarms = onRequestExactAlarms,
                                    notificationsAllowed = notificationsAllowed,
                                    onRequestNotificationPermission = onRequestNotificationPermission,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@AnimatedContent
                                )
                            } else {
                                val currentCustomCategory = customCategories.firstOrNull { it.id == route.customCategoryId }
                                val currentCategoryKey = route.customCategoryId?.let { "custom_$it" } ?: route.category?.let { "cat_${it.name}" }
                                ReminderListScreen(
                                    reminders = reminders, category = route.category,
                                    customCategory = currentCustomCategory,
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
                                    onAlertChange = { at, rule -> draftAlertAt = at; draftRepeatRule = rule },
                                    exactAlarmsAllowed = exactAlarmsAllowed, onRequestExactAlarms = onRequestExactAlarms,
                                    notificationsAllowed = notificationsAllowed,
                                    onRequestNotificationPermission = onRequestNotificationPermission,
                                    onDraftChange = { draft = it },
                                    onBack = { category = null; customCategoryId = null },
                                    onOpenCategory = { category = it; customCategoryId = null },
                                    onOpenReminder = { if (detailId == null) detailId = it },
                                    onToggleCompleted = onToggleCompleted,
                                    onDeleteReminders = onDeleteReminders,
                                    reduceDynamicBlur = reduceDynamicBlur,
                                    reduceAnimations = reduceAnimations,
                                    designStyle = designStyle,
                                    onOpenSettings = { showSettings = true },
                                    customCategories = customCategories,
                                    onCreateCategory = onCreateCategory,
                                    onEditCategory = onEditCategory,
                                     onDeleteCategory = { id ->
                                         onDeleteCategory(id)
                                         customCategoryId = null
                                     },
                                     onDeleteCategories = onDeleteCategories,
                                     onOpenCustomCategory = { customCategoryItem ->
                                         category = null
                                         customCategoryId = customCategoryItem.id
                                         onOpenCustomCategory(customCategoryItem)
                                     },
                                     onAddReminder = { text, at, rule, catId, isChecklist, checklistItems, sendImmediateNotification, refUrl, attachments ->
                                         val createdId = onAddReminder(text, at, rule, catId, isChecklist, checklistItems, sendImmediateNotification, refUrl, attachments)
                                         if (createdId != null) {
                                             draft = ""; draftAlertAt = null; draftRepeatRule = RecurrenceRule.NONE; draftReferenceUrl = null; isShareDraft = false
                                             if (category != null) {
                                                 if (catId != null) {
                                                     category = null
                                                     customCategoryId = catId
                                                 } else {
                                                     category = if (at == null) ReminderCategory.NoAlert else ReminderCategory.Planned
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
                                     sharedTransitionScope = this@SharedTransitionLayout,
                                     animatedVisibilityScope = this@AnimatedContent,
                                     userName = userName,
                                     isFirstHomeIntro = isFirstHomeIntro,
                                     onFirstHomeIntroComplete = onFirstHomeIntroComplete,
                                     isSettingsOpen = showSettings,
                                     onPickAttachment = onPickAttachment
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
    reminders: List<ReminderItem>, category: ReminderCategory?, draft: String,
    alertAt: Long?, repeatRule: RecurrenceRule, onAlertChange: (Long?, RecurrenceRule) -> Unit,
    exactAlarmsAllowed: Boolean, onRequestExactAlarms: () -> Unit,
    onDraftChange: (String) -> Unit, onBack: () -> Unit,
    onOpenCategory: (ReminderCategory) -> Unit, onOpenReminder: (Long) -> Unit,
    onToggleCompleted: (Long) -> Unit,
    onDeleteReminders: (Set<Long>) -> Unit = {},
    onDeleteCategories: (Set<Long>) -> Unit = {},
    reduceDynamicBlur: Boolean,
    reduceAnimations: Boolean, designStyle: DesignStyle,
    onOpenSettings: () -> Unit, onAddReminder: suspend (String, Long?, RecurrenceRule, Long?, Boolean, List<ChecklistItem>, Boolean, String?, List<ReminderAttachment>) -> Long?,
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
    onCreateCategory: (String, String, Int, Int?) -> Unit = { _, _, _, _ -> },
    onEditCategory: (Long, String, String, Int, Int?) -> Unit = { _, _, _, _, _ -> },
    onDeleteCategory: (Long) -> Unit = {},
    onOpenCustomCategory: (CustomCategory) -> Unit = {},
    notificationsAllowed: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    userName: String = "",
    isFirstHomeIntro: Boolean = false,
    onFirstHomeIntroComplete: () -> Unit = {},
    isSettingsOpen: Boolean = false,
    categoryColorFilter: Int? = null,
    onCategoryColorFilterChange: (Int?) -> Unit = {},
    onPickAttachment: () -> Unit = {}
) {
    var editing by remember { mutableStateOf(false) }
    var editingBuiltInCategory by rememberSaveable { mutableStateOf<ReminderCategory?>(null) }
    var isChecklistMode by rememberSaveable { mutableStateOf(false) }
    var isVoiceDraft by rememberSaveable { mutableStateOf(false) }
    var requiresTimeSelection by rememberSaveable { mutableStateOf(false) }
    var pendingDateHintEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingTimeOfDay by rememberSaveable { mutableStateOf<TimeOfDay?>(null) }
    var checklistDraftItems by rememberSaveable { mutableStateOf(listOf("")) }
    var sendImmediateNotification by rememberSaveable { mutableStateOf(false) }
    var draftCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showNotificationPermissionSheet by rememberSaveable { mutableStateOf(false) }
    var pendingNotificationAction by rememberSaveable { mutableStateOf(PendingNotificationAction.NONE) }
    var showMicrophonePermissionSheet by rememberSaveable { mutableStateOf(false) }
    var pendingMicrophoneAction by rememberSaveable { mutableStateOf(false) }
    var refocus by remember { mutableStateOf(false) }

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
    var isColorFilterExpanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val uiPreferences = remember { context.getSharedPreferences("bigbos_ui", Context.MODE_PRIVATE) }
    var sortOrder by rememberSaveable {
        val saved = uiPreferences.getString("sort_order", null)
        mutableStateOf(
            if (saved == "reminder_time") ReminderSortOrder.REMINDER_TIME
            else ReminderSortOrder.RECENTLY_ADDED
        )
    }
    var saving by remember { mutableStateOf(false) }
    val whatsAppConsentRepo = remember { WhatsAppConsentRepository(context) }
    val whatsAppRecipientRepo = remember { WhatsAppRecipientRepository(context) }
    var isWhatsAppArmed by rememberSaveable { mutableStateOf(false) }
    var isLabsOpen by rememberSaveable { mutableStateOf(false) }
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
    val inputFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val composerPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(3)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val remainingSlots = 3 - draftAttachments.size
            val toAdd = uris.take(remainingSlots).mapNotNull { uri ->
                AttachmentStorage.copyUriToAttachments(context, uri)
            }
            if (toAdd.isNotEmpty()) {
                onDraftAttachmentsChange(draftAttachments + toAdd)
            }
        }
    }
    val handlePickComposerAttachment: () -> Unit = {
        if (draftAttachments.size < 3) {
            composerPhotoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }
    val handleRemoveDraftAttachment: (Int) -> Unit = { index ->
        if (index in draftAttachments.indices) {
            val item = draftAttachments[index]
            AttachmentStorage.deleteAttachmentFile(item.localPath)
            onDraftAttachmentsChange(draftAttachments.filterIndexed { i, _ -> i != index })
        }
    }

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
    val scope = rememberCoroutineScope()
    val scene = rememberGraphicsLayer()
    var scenePosition by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(customCategory) {
        if (customCategory != null) {
            draftCategoryId = customCategory.id
        }
    }

    val density = LocalDensity.current
    val shouldAnimateColdStart = remember { category == null && customCategory == null && ColdStartTracker.isEligible(reduceAnimations) }
    var isColdStarting by remember { mutableStateOf(shouldAnimateColdStart) }
    val coldStartProgress = remember { Animatable(if (shouldAnimateColdStart) 0f else 1f) }
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var headerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val isFirstHome = remember { isFirstHomeIntro }
    val shouldAnimateFirstHomeIntro = remember { isFirstHome && !reduceAnimations && category == null && customCategory == null }
    var isFirstHomeIntroPlaying by remember { mutableStateOf(shouldAnimateFirstHomeIntro) }
    val categoryEntranceProgress = remember { Animatable(if (shouldAnimateFirstHomeIntro) 0f else 1f) }
    val composerEntranceProgress = remember { Animatable(if (shouldAnimateFirstHomeIntro) 0f else 1f) }
    var isHomeReadyForShimmer by remember { mutableStateOf(!shouldAnimateColdStart && !shouldAnimateFirstHomeIntro) }

    DisposableEffect(Unit) {
        onDispose {
            if (isFirstHome) {
                onFirstHomeIntroComplete()
            }
        }
    }

    LaunchedEffect(isFirstHome) {
        if (isFirstHome) {
            if (shouldAnimateFirstHomeIntro) {
                delay(80)
                launch {
                    categoryEntranceProgress.animateTo(
                        1f,
                        animationSpec = tween(505, easing = LinearEasing)
                    )
                }
                delay(330)
                launch {
                    composerEntranceProgress.animateTo(
                        1f,
                        animationSpec = tween(340, easing = LinearEasing)
                    )
                }
                delay(340)
                isFirstHomeIntroPlaying = false
                isHomeReadyForShimmer = true
                onFirstHomeIntroComplete()
            } else {
                isFirstHomeIntroPlaying = false
                isHomeReadyForShimmer = true
                onFirstHomeIntroComplete()
            }
        }
    }

    LaunchedEffect(isColdStarting) {
        if (!isColdStarting && !isFirstHomeIntroPlaying) {
            isHomeReadyForShimmer = true
        }
    }

    val entryTracker = remember { ReminderEntryTracker(scope) }

    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectedCategoryIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val isSelectionMode = category == null && customCategory == null && selectedIds.isNotEmpty()
    val isCategorySelectionMode = category == null && customCategory == null && selectedCategoryIds.isNotEmpty()

    val isTargetReady = shouldAnimateColdStart &&
        containerCoordinates?.isAttached == true &&
        headerCoordinates?.isAttached == true

    LaunchedEffect(isTargetReady) {
        if (isTargetReady) {
            ColdStartTracker.markExecuted()
            coldStartProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 400,
                    easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
                )
            )
            isColdStarting = false
        }
    }

    LaunchedEffect(shouldAnimateColdStart) {
        if (shouldAnimateColdStart) {
            delay(1000)
            if (isColdStarting) {
                ColdStartTracker.markExecuted()
                isColdStarting = false
            }
        }
    }
    val submit: (String) -> Unit = { text ->
        if (requiresTimeSelection && alertAt == null) {
            Toast.makeText(context, "Lütfen bir saat seç.", Toast.LENGTH_SHORT).show()
            requestPermissionForAction(PendingNotificationAction.OPEN_DATE_PICKER)
        } else if (isChecklistMode) {
            val validItems = checklistDraftItems.map { it.trim() }.filter { it.isNotEmpty() }
            if (text.isNotBlank() && validItems.isNotEmpty() && !saving) {
                if (alertAt != null && alertAt <= System.currentTimeMillis()) {
                    Toast.makeText(context, "Gelecekte bir tarih ve saat seç.", Toast.LENGTH_LONG).show()
                    showDatePicker = true
                } else {
                    saving = true
                    val wasWhatsAppArmed = isWhatsAppArmed
                    isWhatsAppArmed = false
                    scope.launch {
                        try {
                            val items = validItems.mapIndexed { idx, itmText -> ChecklistItem(id = 0L, text = itmText) }
                            val resolvedTitle = text.trim()
                            val createdId = onAddReminder(resolvedTitle, alertAt, if (alertAt != null) repeatRule else RecurrenceRule.NONE, draftCategoryId, true, items, sendImmediateNotification, draftReferenceUrl, draftAttachments)
                            if (createdId != null) {
                                onDraftAttachmentsChange(emptyList())
                                entryTracker.onReminderCreated(createdId, reduceAnimations)
                                RealHaptics.success(view)
                                draftCategoryId = if (customCategory != null) customCategory.id else null
                                isChecklistMode = false
                                checklistDraftItems = listOf("")
                                isLabsOpen = false
                                sendImmediateNotification = false
                                onDraftChange("")
                                isVoiceDraft = false
                                requiresTimeSelection = false
                                pendingDateHintEpochDay = null
                                pendingTimeOfDay = null
                                onShareDraftChange(false)
                                onDraftReferenceUrlChange(null)
                                focusManager.clearFocus(); keyboard?.hide()

                                if (wasWhatsAppArmed) {
                                    scope.launch {
                                        val recipientPhone = whatsAppRecipientRepo.getRecipient()
                                        val sendSuccess = WhatsAppSender.sendReminder(
                                            id = createdId,
                                            text = resolvedTitle,
                                            isChecklist = true,
                                            checklistItems = validItems,
                                            recipient = recipientPhone
                                        )
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                if (sendSuccess) "WhatsApp'a gönderildi" else "WhatsApp gönderilemedi",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                        } finally { saving = false }
                    }
                }
            }
        } else {
            if (text.isNotBlank() && !saving) {
                if (alertAt != null && alertAt <= System.currentTimeMillis()) {
                    Toast.makeText(context, "Gelecekte bir tarih ve saat seç.", Toast.LENGTH_LONG).show()
                    showDatePicker = true
                } else {
                    saving = true
                    val wasWhatsAppArmed = isWhatsAppArmed
                    isWhatsAppArmed = false
                    scope.launch {
                        try {
                            val reminderText = text.trim()
                            val createdId = onAddReminder(reminderText, alertAt, if (alertAt != null) repeatRule else RecurrenceRule.NONE, draftCategoryId, false, emptyList(), sendImmediateNotification, draftReferenceUrl, draftAttachments)
                            if (createdId != null) {
                                onDraftAttachmentsChange(emptyList())
                                entryTracker.onReminderCreated(createdId, reduceAnimations)
                                RealHaptics.success(view)
                                draftCategoryId = if (customCategory != null) customCategory.id else null
                                isLabsOpen = false
                                sendImmediateNotification = false
                                onDraftChange("")
                                isVoiceDraft = false
                                requiresTimeSelection = false
                                pendingDateHintEpochDay = null
                                pendingTimeOfDay = null
                                onShareDraftChange(false)
                                onDraftReferenceUrlChange(null)
                                focusManager.clearFocus(); keyboard?.hide()

                                if (wasWhatsAppArmed) {
                                    scope.launch {
                                        val recipientPhone = whatsAppRecipientRepo.getRecipient()
                                        val sendSuccess = WhatsAppSender.sendReminder(
                                            id = createdId,
                                            text = reminderText,
                                            isChecklist = false,
                                            checklistItems = emptyList(),
                                            recipient = recipientPhone
                                        )
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                if (sendSuccess) "WhatsApp'a gönderildi" else "WhatsApp gönderilemedi",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                        } finally { saving = false }
                    }
                }
            }
        }
    }
    val onVoiceFinalize: (String) -> Unit = { rawVoiceText ->
        val result = VoiceDraftCoordinator.finalizeVoice(
            rawVoiceText = rawVoiceText,
            currentAlertAt = alertAt,
            currentRepeatRule = repeatRule,
            currentSendImmediateNotification = sendImmediateNotification
        )
        onDraftChange(result.text)
        if (result.alertAt != null) {
            onAlertChange(result.alertAt, result.repeatRule)
            sendImmediateNotification = false
            requiresTimeSelection = false
            pendingDateHintEpochDay = null
            pendingTimeOfDay = null
        } else {
            requiresTimeSelection = result.requiresTimeSelection
            pendingDateHintEpochDay = result.dateHint?.toEpochDay()
            pendingTimeOfDay = result.timeOfDay
            if (result.repeatRule != RecurrenceRule.NONE) {
                onAlertChange(null, result.repeatRule)
            }
            if (result.requiresTimeSelection) {
                sendImmediateNotification = false
            }
        }
        if (result.isChecklist && result.checklistItems.isNotEmpty()) {
            isChecklistMode = true
            checklistDraftItems = result.checklistItems
        }
        isVoiceDraft = true
    }
    val voice = rememberVoiceInput(draft, onDraftChange, onVoiceFinalize, observeLevel = !reduceAnimations)
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingMicrophoneAction) {
            voice.start()
        }
        pendingMicrophoneAction = false
    }
    var voiceLineCount by remember { mutableIntStateOf(1) }
    LaunchedEffect(voice.phase) {
        if (voice.phase == VoicePhase.Idle) {
            voiceLineCount = 1
        }
    }
    var textLineCount by remember { mutableIntStateOf(1) }
    LaunchedEffect(draft) {
        if (draft.isEmpty()) {
            textLineCount = 1
        }
    }
    val expanded = editing || voice.phase != VoicePhase.Idle || alertAt != null || requiresTimeSelection || draftCategoryId != null || isChecklistMode || isVoiceDraft || isShareDraft || sendImmediateNotification || pendingNotificationAction != PendingNotificationAction.NONE || draftAttachments.isNotEmpty()
    LaunchedEffect(showDatePicker, showNotificationPermissionSheet, refocus) {
        if (!showDatePicker && !showNotificationPermissionSheet && refocus) { refocus = false; inputFocus.requestFocus(); keyboard?.show() }
    }
    val labsExtraHeight = if (isLabsOpen && expanded) 76.dp else 0.dp
    val attachmentExtraHeight = if (draftAttachments.isNotEmpty()) 48.dp else 0.dp
    val targetComposerHeight = when {
        isChecklistMode -> 280.dp + labsExtraHeight + attachmentExtraHeight
        voice.phase != VoicePhase.Idle -> {
            val extraLines = (voiceLineCount - 1).coerceIn(0, 3)
            (118 + extraLines * 27).dp + labsExtraHeight + attachmentExtraHeight
        }
        expanded -> {
            val extraLines = (textLineCount - 1).coerceIn(0, 3)
            (118 + extraLines * 27).dp + labsExtraHeight + attachmentExtraHeight
        }
        else -> 58.dp
    }
    val composerHeight by animateDpAsState(
        targetComposerHeight,
        if (reduceAnimations) snap() else tween(280, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)),
        label = "composerHeight"
    )
    val labsProgress by animateFloatAsState(
        targetValue = if (isLabsOpen && expanded) 1f else 0f,
        animationSpec = if (reduceAnimations) snap() else tween(280, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)),
        label = "labsProgress"
    )
    val bottomGap by animateDpAsState(
        if (expanded) 20.dp else 12.dp,
        if (reduceAnimations) snap() else tween(250),
        label = "bottomGap"
    )
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var keyboardWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(keyboardVisible) {
        if (keyboardVisible) keyboardWasVisible = true
        else if (keyboardWasVisible) {
            focusManager.clearFocus()
            keyboardWasVisible = false
        }
    }
    val anySelectionMode = isSelectionMode || isCategorySelectionMode
    BackHandler(enabled = isLabsOpen && !anySelectionMode) { isLabsOpen = false }
    BackHandler(enabled = isColorFilterExpanded && !anySelectionMode) { isColorFilterExpanded = false }
    BackHandler(enabled = isCategorySelectionMode) { selectedCategoryIds = emptySet() }
    BackHandler(enabled = isSelectionMode && !isCategorySelectionMode) { selectedIds = emptySet() }
    BackHandler(enabled = showAccountMenu && !anySelectionMode) { showAccountMenu = false }
    BackHandler(enabled = showCategoryPicker && !anySelectionMode) { showCategoryPicker = false }
    BackHandler(enabled = (isChecklistMode || draftAttachments.isNotEmpty()) && !showAccountMenu && !showDatePicker && !showCategoryPicker && !anySelectionMode) {
        if (draftAttachments.isNotEmpty()) {
            AttachmentStorage.deleteAttachmentFiles(draftAttachments)
            onDraftAttachmentsChange(emptyList())
        }
        isChecklistMode = false
        checklistDraftItems = listOf("")
        if (isVoiceDraft) {
            isVoiceDraft = false
            requiresTimeSelection = false
            pendingDateHintEpochDay = null
            pendingTimeOfDay = null
            onDraftChange("")
            onAlertChange(null, RecurrenceRule.NONE)
            draftCategoryId = if (customCategory != null) customCategory.id else null
        }
        focusManager.clearFocus()
        keyboard?.hide()
    }
    BackHandler(enabled = (editing || voice.phase != VoicePhase.Idle || isVoiceDraft || isShareDraft) && !isChecklistMode && !showAccountMenu && !showDatePicker && !showCategoryPicker && !anySelectionMode) {
        when {
            voice.phase != VoicePhase.Idle -> {
                voice.cancel()
                focusManager.clearFocus()
                keyboard?.hide()
            }
            editing -> {
                focusManager.clearFocus()
                keyboard?.hide()
                editing = false
            }
            isVoiceDraft -> {
                isVoiceDraft = false
                requiresTimeSelection = false
                pendingDateHintEpochDay = null
                pendingTimeOfDay = null
                onDraftChange("")
                onAlertChange(null, RecurrenceRule.NONE)
                draftCategoryId = if (customCategory != null) customCategory.id else null
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
                focusManager.clearFocus()
                keyboard?.hide()
            }
        }
    }
    val today = rememberReminderDay()
    val baseActive = remember(reminders, category, customCategory, today) {
        when {
            category == ReminderCategory.Completed -> reminders.inCategory(ReminderCategory.Completed, today)
            category != null -> reminders.inCategory(category, today)
            customCategory != null -> reminders.filter {
                it.customCategoryId == customCategory.id && !it.isCompleted
            }.sortedByDescending { it.createdAt }
            else -> reminders.filter { !it.isCompleted }
        }
    }
    val active = remember(baseActive, categoryColorFilter, sortOrder, category, customCategory) {
        val effectiveColorFilter = if (category != null || customCategory != null) categoryColorFilter else null
        filterAndSortReminders(baseActive, effectiveColorFilter, sortOrder)
    }
    val baseCompleted = remember(reminders, category, customCategory) {
        when {
            category == null && customCategory == null ->
                reminders.inCategory(ReminderCategory.Completed)
                    .sortedByDescending { it.completedAt ?: it.createdAt }
            customCategory != null -> reminders.filter {
                it.customCategoryId == customCategory.id && it.isCompleted
            }.sortedByDescending { it.completedAt ?: it.createdAt }
            else -> emptyList()
        }
    }
    val completed = remember(baseCompleted, categoryColorFilter, sortOrder, category, customCategory) {
        val effectiveColorFilter = if (category != null || customCategory != null) categoryColorFilter else null
        filterAndSortReminders(baseCompleted, effectiveColorFilter, sortOrder)
    }
    val isHome = category == null && customCategory == null
    val completedPreview = remember(completed, isHome) {
        if (isHome) completed.take(HOME_COMPLETED_PREVIEW_LIMIT) else completed
    }
    val statusHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val customCategoriesById = remember(customCategories) { customCategories.associateBy { it.id } }

    DisposableEffect(category, customCategory) {
        isColorFilterExpanded = false
        if (category != null || customCategory != null) {
            showAccountMenu = false
            selectedIds = emptySet()
            selectedCategoryIds = emptySet()
        }
        onDispose { }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .navigationBarsPadding()
            .imePadding()
            .onGloballyPositioned { coords ->
                containerCoordinates = coords
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                .onGloballyPositioned { scenePosition = it.positionInRoot() }
                .drawWithContent {
                    if (!reduceDynamicBlur && Build.VERSION.SDK_INT >= 31) {
                        scene.record { this@drawWithContent.drawContent() }; drawLayer(scene)
                    } else drawContent()
                }
                .graphicsLayer {
                    if (isColdStarting) {
                        alpha = coldStartProgress.value
                    }
                },
            contentPadding = PaddingValues(top = statusHeight, bottom = composerHeight + bottomGap + 24.dp)
        ) {
            item(key = "header") {
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
                            RealWordmark(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = if (isColdStarting) 0f else 1f
                                    }
                            )
                        }
                        IconButton(
                            onClick = {
                                voice.cancel()
                                focusManager.clearFocus()
                                keyboard?.hide()
                                showAccountMenu = true
                            },
                            modifier = Modifier.semantics { contentDescription = "Hesap menüsünü aç" }
                        ) {
                            GuestAvatar(Modifier.size(36.dp))
                        }
                    }
                } else {
                    AnimatedContent(
                        targetState = isColorFilterExpanded,
                        transitionSpec = {
                            if (reduceAnimations) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                (fadeIn(tween(200, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f))) +
                                    scaleIn(tween(200, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)), initialScale = 0.96f)) togetherWith
                                (fadeOut(tween(160, easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f))) +
                                    scaleOut(tween(160, easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)), targetScale = 0.96f))
                            }
                        },
                        label = "categoryHeaderFilterTransition"
                    ) { inFilterMode ->
                        if (inFilterMode) {
                            CategoryColorSelectorHeader(
                                selectedColorFilter = categoryColorFilter,
                                onSelectColor = { selected ->
                                    onCategoryColorFilterChange(selected)
                                    isColorFilterExpanded = false
                                },
                                onClose = { isColorFilterExpanded = false },
                                reduceAnimations = reduceAnimations
                            )
                        } else if (customCategory != null) {
                            val startColor = ReminderColorChoices.getOrNull(customCategory.accentStart)?.color ?: Color(0xFF233F73)
                            val endColor = customCategory.accentEnd?.let { ReminderColorChoices.getOrNull(it)?.color } ?: startColor
                            val icon = CategoryIcon.fromKey(customCategory.iconKey)
                            Row(
                                Modifier.fillMaxWidth().padding(top = 10.dp, end = 16.dp, start = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Ana sayfaya dön" }) {
                                    ActionIcon(ActionGlyph.Back, Modifier.size(23.dp))
                                }
                                Spacer(Modifier.width(2.dp))
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .drawWithCache {
                                            val topLight = Brush.radialGradient(
                                                colors = listOf(startColor.copy(alpha = 0.85f), Color.Transparent),
                                                center = Offset(size.width * 0.95f, -size.height * 0.1f),
                                                radius = size.width * 1.12f
                                            )
                                            val bottomLight = Brush.radialGradient(
                                                colors = listOf(endColor.copy(alpha = 0.70f), Color.Transparent),
                                                center = Offset(-size.width * 0.05f, size.height * 1.1f),
                                                radius = size.width * 1.15f
                                            )
                                            onDrawBehind {
                                                drawRect(Color(0xFF14151C))
                                                drawRect(topLight)
                                                drawRect(bottomLight)
                                            }
                                        }
                                        .border(0.75.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CategoryIconGlyph(icon = icon, modifier = Modifier.size(20.dp), color = Color.White)
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = customCategory.name,
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                CategoryColorFilterButton(
                                    hasFilter = categoryColorFilter != null,
                                    selectedColorIndex = categoryColorFilter,
                                    onClick = { isColorFilterExpanded = true }
                                )
                                CategoryEditButton(
                                    onClick = { showEditCategoryDialog = true },
                                    contentDescription = "Kategoriyi düzenle"
                                )
                            }
                        } else if (category != null) {
                            val resolvedStyle = category.resolveStyle(builtInCategoryColors[category])
                            val badgeColor = resolvedStyle.tint
                            val glyph = category.glyph()
                            Row(
                                Modifier.fillMaxWidth().padding(top = 10.dp, end = 16.dp, start = 4.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Ana sayfaya dön" }) {
                                    ActionIcon(ActionGlyph.Back, Modifier.size(23.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(badgeColor.copy(alpha = 0.2f))
                                        .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CategoryGlyph(glyph, Modifier.size(20.dp), tint = badgeColor)
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = category.title,
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                CategoryColorFilterButton(
                                    hasFilter = categoryColorFilter != null,
                                    selectedColorIndex = categoryColorFilter,
                                    onClick = { isColorFilterExpanded = true }
                                )
                                CategoryEditButton(
                                    onClick = { editingBuiltInCategory = category },
                                    contentDescription = "Kategori rengini düzenle"
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
            }
            if (category == null && customCategory == null) {
                item(key = "categories") {
                    CategoryGrid(
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
                            if (category == null && customCategory == null) {
                                voice.cancel(); focusManager.clearFocus(); keyboard?.hide()
                                selectedIds = emptySet()
                                selectedCategoryIds = if (id in selectedCategoryIds) selectedCategoryIds - id else selectedCategoryIds + id
                            }
                        },
                        itemEntranceProgress = if (isFirstHomeIntroPlaying) { { categoryEntranceProgress.value } } else null,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        reduceAnimations = reduceAnimations
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 6.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Hatırlatıcılar",
                            color = Color(0xFFF2F2F4),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        HomeSortControl(
                            sortOrder = sortOrder,
                            onToggleSortOrder = {
                                val next = if (sortOrder == ReminderSortOrder.RECENTLY_ADDED) {
                                    ReminderSortOrder.REMINDER_TIME
                                } else {
                                    ReminderSortOrder.RECENTLY_ADDED
                                }
                                sortOrder = next
                                uiPreferences.edit().putString(
                                    "sort_order",
                                    if (next == ReminderSortOrder.REMINDER_TIME) "reminder_time" else "recently_added"
                                ).apply()
                            },
                            reduceAnimations = reduceAnimations
                        )
                    }
                }
            }
            val isListEmpty = when {
                customCategory != null -> active.isEmpty() && completed.isEmpty()
                category != null -> active.isEmpty()
                else -> active.isEmpty()
            }
            if (isListEmpty) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().height(104.dp).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                        val emptyMsg = when {
                            (category != null || customCategory != null) && categoryColorFilter != null -> "Bu renkte hatırlatıcı yok"
                            customCategory != null -> "Bu kategoride henüz hatırlatıcı yok"
                            category != null -> category.emptyMessage
                            else -> "Henüz hatırlatıcı yok"
                        }
                        Text(emptyMsg, color = Color(0xFF888890), fontSize = 14.sp)
                    }
                }
            }
            items(active, key = { it.id }, contentType = { "reminder" }) { reminder ->
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
                    showCategoryStripes = category == null && customCategory == null,
                    onLongClick = {
                        if (category == null && customCategory == null && !isCategorySelectionMode) {
                            voice.cancel(); focusManager.clearFocus(); keyboard?.hide()
                            selectedCategoryIds = emptySet()
                            selectedIds = selectedIds + reminder.id
                        }
                    },
                    onSelectToggle = {
                        if (category == null && customCategory == null) {
                            selectedIds = if (isSelected) selectedIds - reminder.id else selectedIds + reminder.id
                        }
                    },
                    modifier = Modifier
                        .then(if (reduceAnimations) Modifier else Modifier.animateItem(
                            placementSpec = tween(
                                durationMillis = 240,
                                easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
                            ),
                            fadeInSpec = if (isNewlyCreated) null else tween(180),
                            fadeOutSpec = tween(180)
                        ))
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
            }
            if (completed.isNotEmpty()) {
                item(key = "completed_heading") {
                    Spacer(Modifier.height(18.dp)); SectionTitle("Tamamlandı")
                }
                if (isHome) {
                    item(key = "home_completed_preview_container") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (reduceAnimations) Modifier else Modifier.animateItem(
                                    placementSpec = tween(240, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)),
                                    fadeInSpec = tween(180),
                                    fadeOutSpec = tween(180)
                                ))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (reduceAnimations) Modifier else Modifier.animateContentSize(tween(240, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f))))
                            ) {
                                completedPreview.forEachIndexed { index, reminder ->
                                    key(reminder.id) {
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
                                            showCategoryStripes = true,
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
                                        )
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
                            showCategoryStripes = false,
                            onLongClick = { },
                            onSelectToggle = { },
                            modifier = Modifier
                                .then(if (reduceAnimations) Modifier else Modifier.animateItem(
                                    placementSpec = tween(240, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)),
                                    fadeInSpec = tween(180),
                                    fadeOutSpec = tween(180)
                                ))
                                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        )
                    }
                }
            }
        }
        TopGlassOverlay(
            scene,
            { scenePosition },
            Modifier.align(Alignment.TopCenter),
            reduceDynamicBlur = reduceDynamicBlur
        )
        val selectionProgress by animateFloatAsState(
            targetValue = if (isSelectionMode || isCategorySelectionMode) 1f else 0f,
            animationSpec = if (reduceAnimations) snap() else tween(270, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)),
            label = "selectionBottomTransition"
        )
        val density = LocalDensity.current
        val bottomSlideOffsetPx = with(density) { (composerHeight + bottomGap + 80.dp).toPx() }

        AnimatedVisibility(
            visible = isChecklistMode,
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

        BottomFade(Modifier.align(Alignment.BottomCenter).height(composerHeight + bottomGap + 46.dp))
        GlassComposer(
            value = draft, onValueChange = onDraftChange,
            alertAt = alertAt, repeatRule = repeatRule,
            requiresTimeSelection = requiresTimeSelection,
            dateHint = pendingDateHintEpochDay?.let { LocalDate.ofEpochDay(it) },
            timeOfDay = pendingTimeOfDay,
            saving = saving,
            onPickDate = {
                requestPermissionForAction(PendingNotificationAction.OPEN_DATE_PICKER)
            },
            onClearDate = {
                onAlertChange(null, RecurrenceRule.NONE)
                requiresTimeSelection = false
                pendingDateHintEpochDay = null
                pendingTimeOfDay = null
            },
            customCategories = customCategories,
            selectedCategoryId = draftCategoryId,
            onPickCategory = { voice.cancel(); focusManager.clearFocus(); keyboard?.hide(); showCategoryPicker = true },
            onPickAttachment = handlePickComposerAttachment,
            draftAttachments = draftAttachments,
            onRemoveDraftAttachment = handleRemoveDraftAttachment,
            onSubmit = { if (voice.phase == VoicePhase.Idle) submit(draft) else voice.finish() },
            expanded = expanded, onFocusChanged = { editing = it }, focusRequester = inputFocus,
            backdrop = scene, backdropPosition = { scenePosition },
            voicePhase = voice.phase, voiceLevel = voice.level,
            onMicClick = {
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
                        voice.toggle()
                    } else {
                        pendingMicrophoneAction = true
                        showMicrophonePermissionSheet = true
                    }
                }
            },
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
            onToggleImmediateNotification = {
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
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = bottomGap).height(composerHeight)
                .graphicsLayer {
                    translationY = if (isFirstHomeIntroPlaying) {
                        val progress = composerEntranceProgress.value
                        val motionEase = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f).transform(progress)
                        with(density) { (32.dp * (1f - motionEase)).toPx() }
                    } else {
                        selectionProgress * bottomSlideOffsetPx
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
        )
        CategorySelectionOverlay(
            visible = showCategoryPicker,
            customCategories = customCategories,
            selectedCategoryId = draftCategoryId,
            onSelectCategory = { draftCategoryId = it },
            onDismiss = { showCategoryPicker = false },
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
                backdrop = scene,
                backdropPosition = { scenePosition },
                reduceDynamicBlur = reduceDynamicBlur,
                designStyle = designStyle,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = bottomGap).height(58.dp)
                    .graphicsLayer {
                        translationY = (1f - selectionProgress) * bottomSlideOffsetPx
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
                reduceDynamicBlur = reduceDynamicBlur,
                reduceAnimations = reduceAnimations,
                designStyle = designStyle,
                userName = userName
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
            backdrop = scene, backdropPosition = { scenePosition },
            designStyle = designStyle, reduceDynamicBlur = reduceDynamicBlur,
            reduceAnimations = reduceAnimations,
            onDismiss = { showDatePicker = false },
            onConfirm = { at, rule ->
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
                onSaveCustomCategory = { name, iconKey, start, end ->
                    onCreateCategory(name, iconKey, start, end)
                    showCreateCategoryDialog = false
                },
                designStyle = designStyle,
                reduceDynamicBlur = reduceDynamicBlur,
                reduceAnimations = reduceAnimations
            )
        }
        if (showEditCategoryDialog && customCategory != null) {
            CategoryEditor(
                customCategory = customCategory,
                backdrop = scene,
                backdropPosition = { scenePosition },
                onDismiss = { showEditCategoryDialog = false },
                onSaveCustomCategory = { name, iconKey, start, end ->
                    onEditCategory(customCategory.id, name, iconKey, start, end)
                    showEditCategoryDialog = false
                },
                onDeleteCustomCategory = {
                    onDeleteCategory(customCategory.id)
                    showEditCategoryDialog = false
                },
                designStyle = designStyle,
                reduceDynamicBlur = reduceDynamicBlur,
                reduceAnimations = reduceAnimations
            )
        }
        if (isColdStarting) {
            val container = containerCoordinates
            val header = headerCoordinates
            if (container != null && container.isAttached && header != null && header.isAttached) {
                val targetTopLeft = container.localPositionOf(header, Offset.Zero)
                val targetCenterX = targetTopLeft.x + (header.size.width / 2f)
                val targetCenterY = targetTopLeft.y + (header.size.height / 2f)
                val startCenterX = container.size.width / 2f
                val startCenterY = container.size.height / 2f

                val p = coldStartProgress.value
                val currentCenterX = lerp(startCenterX, targetCenterX, p)
                val currentCenterY = lerp(startCenterY, targetCenterY, p)
                val currentScale = lerp(1.75f, 1.0f, p)

                Box(
                    modifier = Modifier
                        .size(112.dp, 42.dp)
                        .graphicsLayer {
                            translationX = currentCenterX - (size.width / 2f)
                            translationY = currentCenterY - (size.height / 2f)
                            scaleX = currentScale
                            scaleY = currentScale
                        }
                ) {
                    RealWordmark(Modifier.fillMaxSize())
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(112.dp, 42.dp)
                            .graphicsLayer {
                                scaleX = 1.75f
                                scaleY = 1.75f
                            }
                    ) {
                        RealWordmark(Modifier.fillMaxSize())
                    }
                }
            }
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
