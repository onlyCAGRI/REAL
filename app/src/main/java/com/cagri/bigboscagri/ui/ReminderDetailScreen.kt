package com.cagri.bigboscagri.ui

import android.os.Build
import com.cagri.bigboscagri.ui.components.LiquidGlassTokens
import com.cagri.bigboscagri.ui.components.liquidContentEdge
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
import com.cagri.bigboscagri.ui.components.ReminderLocationMap
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.cagri.bigboscagri.data.RecurrenceRule
import com.cagri.bigboscagri.data.ReminderItem
import com.cagri.bigboscagri.data.buildReminderShareText
import com.cagri.bigboscagri.data.repeatSummary
import com.cagri.bigboscagri.ui.components.ActionGlyph
import com.cagri.bigboscagri.ui.components.ActionIcon
import com.cagri.bigboscagri.ui.components.journalMoodLabel
import com.cagri.bigboscagri.ui.components.MoodGlyph
import com.cagri.bigboscagri.ui.components.GlassPanel
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
import com.cagri.bigboscagri.ui.components.TopGlassOverlay
import com.cagri.bigboscagri.ui.theme.BigBosTheme

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ReminderDetailScreen(
    reminder: ReminderItem, onBack: () -> Unit, onToggleCompleted: () -> Unit,
    onToggleImportant: () -> Unit, onSave: (String, Int?, Int?, Long?, List<ChecklistItem>?, Int?, Float?, Int?, List<ReminderAttachment>?, ReminderLocation?) -> Unit, onDelete: () -> Unit,
    incomingLocation: ReminderLocation? = null,
    onIncomingLocationHandled: () -> Unit = {},
    onRequestLocationShare: () -> Unit = {},
    onResolveLocationPreview: () -> Unit = {},
    onSaveJournal: (JournalMood?, String) -> Unit = { _, _ -> },
    onDeleteJournal: () -> Unit = {},
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
        mutableStateOf(reminder.gradientBlendFraction ?: DEFAULT_GRADIENT_BLEND_FRACTION)
    }
    var starBorderColorIndex by rememberSaveable(reminder.id) {
        mutableStateOf(reminder.starBorderColorIndex ?: Math.floorMod(reminder.id, 10))
    }
    var selectedCategoryId by rememberSaveable(reminder.id) { mutableStateOf(reminder.customCategoryId) }
    var editedChecklistItems by remember(reminder.id, editing) { mutableStateOf(reminder.checklistItems) }
    var newChecklistItemText by rememberSaveable(reminder.id) { mutableStateOf("") }
    var secondaryPanelsExpanded by rememberSaveable(reminder.id) { mutableStateOf(false) }
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showNotificationPermissionSheet by rememberSaveable { mutableStateOf(false) }
    var pendingDetailDatePicker by rememberSaveable(reminder.id) { mutableStateOf(false) }
    var editedAttachments by remember(reminder.id, editing) { mutableStateOf(reminder.attachments) }
    var editedLocation by remember(reminder.id) { mutableStateOf(reminder.location) }
    var journalEditing by remember(reminder.id) { mutableStateOf(false) }
    var journalCreating by remember(reminder.id) { mutableStateOf(false) }
    var editedJournalMood by remember(reminder.id, journalEditing, journalCreating) { mutableStateOf(reminder.journal?.mood) }
    var editedJournalText by remember(reminder.id, journalEditing, journalCreating) { mutableStateOf(reminder.journal?.text ?: "") }
    var showMoodSelector by remember(reminder.id, journalEditing, journalCreating) { mutableStateOf(false) }

    var newlyAddedAttachments by remember(reminder.id, editing) { mutableStateOf<List<ReminderAttachment>>(emptyList()) }
    var previewAttachmentPath by rememberSaveable(reminder.id) { mutableStateOf<String?>(null) }
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

    val detailPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(3)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val remainingSlots = 3 - editedAttachments.size
            val toAdd = uris.take(remainingSlots).mapNotNull { uri ->
                AttachmentStorage.copyUriToAttachments(context, uri)
            }
            if (toAdd.isNotEmpty()) {
                editedAttachments = editedAttachments + toAdd
                newlyAddedAttachments = newlyAddedAttachments + toAdd
            }
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

    val finishEditing: () -> Unit = {
        if (newlyAddedAttachments.isNotEmpty()) {
            AttachmentStorage.deleteAttachmentFiles(newlyAddedAttachments)
            newlyAddedAttachments = emptyList()
        }
        journalCreating = false
        journalEditing = false
        showMoodSelector = false
        editedJournalMood = reminder.journal?.mood
        editedJournalText = reminder.journal?.text ?: ""
        editedAttachments = reminder.attachments
        editedLocation = reminder.location
        locationJustShared = false
        editing = false
        secondaryPanelsExpanded = false
        accentStart = reminder.accentStart
        accentEnd = reminder.accentEnd
        customSolidColorArgb = reminder.customSolidColorArgb
        selectedCategoryId = reminder.customCategoryId
        editedChecklistItems = reminder.checklistItems
        starBorderColorIndex = reminder.starBorderColorIndex ?: Math.floorMod(reminder.id, 10)
        gradientBlendFraction = reminder.gradientBlendFraction ?: DEFAULT_GRADIENT_BLEND_FRACTION
        newChecklistItemText = ""
        focusManager.clearFocus()
        keyboard?.hide()
    }
    BackHandler(previewAttachmentPath != null || editing || deleting || showCategoryPicker || journalEditing || journalCreating || showMoodSelector) {
        if (previewAttachmentPath != null) previewAttachmentPath = null
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
        remember { mutableStateOf(1f) }
    }

    val detailHeader: @Composable () -> Unit = {
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
                com.cagri.bigboscagri.ui.components.DesignIconButton(
                    backdrop = scene, backdropPosition = { scenePosition },
                    designStyle = designStyle, reduceDynamicBlur = reduceDynamicBlur,
                    onClick = {
                        RealHaptics.toggle(view, on = !reminder.isImportant)
                        onToggleImportant()
                    },
                    interactionSource = starInteractionSource,
                    modifier = Modifier
                        .pressScale(starInteractionSource, reduceAnimations = reduceAnimations, targetScale = 0.95f)
                        .then(if (designStyle == DesignStyle.LiquidGlass) Modifier else Modifier.background(Color(0xFF161619), CircleShape))
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

    Box(Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding().imePadding()) {
        Column(
            Modifier.fillMaxSize()
                .then(if (deleting) Modifier.clearAndSetSemantics { } else Modifier)
                .onGloballyPositioned { scenePosition = it.positionInRoot() }
                .drawWithContent {
                    if (com.cagri.bigboscagri.ui.components.shouldCaptureBackdrop(designStyle, reduceDynamicBlur, Build.VERSION.SDK_INT)) { scene.record { this@drawWithContent.drawContent() }; drawLayer(scene) }
                    else drawContent()
                }
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = statusHeight + 10.dp,
                    bottom = if (editing) {
                        if (secondaryPanelsExpanded) 300.dp else 140.dp
                    } else 114.dp)
        ) {
            if (designStyle == DesignStyle.LiquidGlass) Spacer(Modifier.height(48.dp))
            else detailHeader()

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

            Column(
                Modifier.padding(top = 12.dp)
                    .then(sharedCardModifier)
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
                            .then(if (reminder.isChecklist) Modifier else Modifier.heightIn(min = 96.dp))
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
                            color = if (reminder.isCompleted) Color(0xFF9999A2) else Color(0xFFF4F4F6),
                            fontSize = 21.sp,
                            lineHeight = 31.sp,
                            textDecoration = if (reminder.isCompleted && reminder.journal == null) TextDecoration.LineThrough else TextDecoration.None
                        )
                    }
                    if (reminder.isChecklist && reminder.checklistItems.isNotEmpty()) {
                        val total = reminder.checklistItems.size
                        val completedCount = reminder.checklistItems.count { it.isCompleted }
                        val summaryText = if (completedCount > 0) "$total madde • $completedCount tamamlandı" else "$total madde"
                        Text(
                            text = summaryText,
                            color = Color(0xFFA5A5B5),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }
                }
                if (reminder.isChecklist && (reminder.checklistItems.isNotEmpty() || editing)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (editing) 24.dp else 2.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (editing) {
                            editedChecklistItems.forEach { item ->
                                key(item.id) {
                                    EditableChecklistItemRow(
                                        item = item,
                                        onTextChange = { newText ->
                                            editedChecklistItems = editedChecklistItems.map {
                                                if (it.id == item.id) it.copy(text = newText) else it
                                            }
                                        },
                                        onDelete = {
                                            editedChecklistItems = editedChecklistItems.filter { it.id != item.id }
                                        },
                                        onToggle = {
                                            editedChecklistItems = editedChecklistItems.map {
                                                if (it.id == item.id) it.toggleCompleted(System.currentTimeMillis()) else it
                                            }
                                        },
                                        onFocus = { secondaryPanelsExpanded = false },
                                        reduceAnimations = reduceAnimations
                                    )
                                }
                            }
                            AddChecklistItemRow(
                                text = newChecklistItemText,
                                onTextChange = { newChecklistItemText = it },
                                onFocus = { secondaryPanelsExpanded = false },
                                onAdd = {
                                    if (newChecklistItemText.trim().isNotBlank()) {
                                        val newItem = ChecklistItem(
                                            id = System.currentTimeMillis(),
                                            text = newChecklistItemText.trim()
                                        )
                                        editedChecklistItems = editedChecklistItems + newItem
                                        newChecklistItemText = ""
                                    }
                                }
                            )
                        } else {
                            reminder.checklistItems.forEach { item ->
                                key(item.id) {
                                    ChecklistItemRow(
                                        item = item,
                                        reduceAnimations = reduceAnimations,
                                        onToggle = { onToggleChecklistItem(reminder.id, item.id) }
                                    )
                                }
                            }
                        }
                    }
                }
                val displayedAttachments = if (editing) editedAttachments else reminder.attachments
                if (displayedAttachments.isNotEmpty() || editing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        displayedAttachments.forEach { attachment ->
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                                    .clickable {
                                        if (!editing) {
                                            previewAttachmentPath = attachment.localPath
                                        }
                                    }
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
                                            .size(24.dp)
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xE61E1E24))
                                            .border(0.5.dp, Color(0x4DFFFFFF), CircleShape)
                                            .clickable {
                                                editedAttachments = editedAttachments.filter { it.id != attachment.id }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ActionIcon(ActionGlyph.Close, Modifier.size(10.dp), Color(0xFFEEEEF2))
                                    }
                                }
                            }
                        }
                        if (editing) {
                            Box(Modifier.size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                                .background(Color(0x1AFFFFFF))
                                .clickable { showLocationGuide = true }
                                .semantics { contentDescription = "Konum ekle veya değiştir" },
                                contentAlignment = Alignment.Center) {
                                ActionIcon(ActionGlyph.Pin, Modifier.size(20.dp), Color(0xFF91B9FF))
                            }
                        }
                        if (editing && displayedAttachments.size < 3) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                                    .background(Color(0x1AFFFFFF))
                                    .clickable {
                                        detailPhotoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                ActionIcon(ActionGlyph.Add, Modifier.size(20.dp), Color(0xFF91B9FF))
                            }
                        }
                    }
                }
                if (editing && editedLocation != null) {
                    val place = editedLocation!!
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(place.placeName, color = Color(0xFFD3DCE9), fontSize = 13.sp, maxLines = 1)
                            if (place.address.isNotBlank()) Text(place.address, color = Color(0xFF8996A8), fontSize = 11.sp, maxLines = 1)
                        }
                        TextButton(onClick = { editedLocation = null; locationJustShared = false }) {
                            Text("Kaldır", color = Color(0xFFAFB8C5), fontSize = 12.sp)
                        }
                    }
                }
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
                                        Text(reminderDate(reminder.alertAt), color = dateColor, fontSize = 14.sp)
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
                if (!reminder.referenceUrl.isNullOrBlank()) {
                    val displayHost = remember(reminder.referenceUrl) { formatDisplayUrl(reminder.referenceUrl) }
                    Row(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E1E24))
                            .border(0.8.dp, Color(0xFF363640), RoundedCornerShape(12.dp))
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(reminder.referenceUrl))
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    android.widget.Toast.makeText(context, "Bağlantı açılamadı", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActionIcon(ActionGlyph.Share, Modifier.size(14.dp), Color(0xFF91B9FF))
                        Text(
                            text = displayHost,
                            color = Color(0xFF91B9FF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                if (editing && !reminder.isCompleted) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = requestPermissionForDatePicker, modifier = Modifier.weight(1f)) {
                            val label = if (reminder.alertAt != null) {
                                val dateStr = reminderDate(reminder.alertAt)
                                if (reminder.hasRepeat) "$dateStr • ${reminder.repeatRule.displayName}" else dateStr
                            } else "Tarih ve saat ekle"
                            Text(label, color = Color(0xFF91B9FF), fontSize = 13.sp)
                        }
                        if (reminder.alertAt != null) IconButton(onClick = { onSetAlert(null, RecurrenceRule.NONE) },
                            modifier = Modifier.semantics { contentDescription = "Tarih ve saati kaldır" }) {
                            ActionIcon(ActionGlyph.Close, Modifier.size(18.dp))
                        }
                    }
                }
            }

            val locationForCard = if (editing) editedLocation.takeIf { locationJustShared } else reminder.location
            if (locationForCard != null) {
                val place = locationForCard
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Visual Connector (main card -> GPS card), matching the Journal connector style.
                    Box(
                        modifier = Modifier
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
                    if (place.latitude != null && place.longitude != null) {
                        Box(Modifier.fillMaxWidth().height(180.dp)
                            .clip(cardShape)
                            .background(if (designStyle == DesignStyle.LiquidGlass) LiquidGlassTokens.contentColor else Color(0xFF131820))
                            .then(cardBorderModifier)) {
                            ReminderLocationMap(place, Modifier.fillMaxSize()) {
                                if (!openReminderLocationInMaps(context, place))
                                    Toast.makeText(context, "Harita uygulaması açılamadı", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxWidth()
                            .clip(cardShape)
                            .background(if (designStyle == DesignStyle.LiquidGlass) LiquidGlassTokens.contentColor else Color(0xFF141416))
                            .then(cardBorderModifier)
                            .clickable {
                                if (!openReminderLocationInMaps(context, place)) {
                                    Toast.makeText(context, "Harita uygulaması açılamadı", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 22.dp, vertical = 20.dp)
                        ) {
                            Row(
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
                        }
                    }
                }
            }

            // --- JOURNAL SECTION ---
            val showJournalCard = reminder.journal != null || journalCreating
            AnimatedVisibility(
                visible = showJournalCard,
                enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { -it / 8 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 8 }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Visual Connector
                    Box(
                        modifier = Modifier
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
                    // Journal Card
                    // Editing an existing Journal entry is driven entirely by the main "Düzenle" edit
                    // mode (see the bottom edit action) — there is no separate pencil/tap-to-edit
                    // trigger on this card anymore.
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
                                    if (!isJournalActive && reminder.journal?.mood != null) {
                                        val viewMood = reminder.journal.mood
                                        MoodGlyph(viewMood, 16.dp, Color(0xFF91B9FF))
                                        Text(journalMoodLabel(viewMood), color = Color(0xFFA5A5B0), fontSize = 12.sp)
                                    }
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
                                Spacer(Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                            .clickable { showMoodSelector = !showMoodSelector }
                                            .padding(vertical = 6.dp, horizontal = 4.dp)
                                    ) {
                                        val chosenMood = editedJournalMood
                                        MoodGlyph(chosenMood, 16.dp, if (chosenMood == null) Color(0xFF6B6B75) else Color(0xFF91B9FF))
                                        Text(chosenMood?.let(::journalMoodLabel) ?: "Mood ekle",
                                            color = if (chosenMood == null) Color(0xFF8E8E98) else Color(0xFFD3D3D9), fontSize = 13.sp)
                                    }
                                }

                                AnimatedVisibility(
                                    visible = showMoodSelector,
                                    enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 8 },
                                    exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 8 }
                                ) {
                                    // 3 columns x 5 rows: JournalMood.entries has exactly 15 moods.
                                    val choices: List<JournalMood> = JournalMood.entries
                                    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        choices.chunked(3).forEach { rowMoods ->
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                rowMoods.forEach { mood ->
                                                    val selected = editedJournalMood == mood
                                                    val shape = RoundedCornerShape(10.dp)
                                                    Row(Modifier.weight(1f).height(38.dp).clip(shape)
                                                        .background(if (selected) Color(0xFF91B9FF).copy(alpha = 0.12f) else Color(0xFF1C1C21))
                                                        .border(0.8.dp, if (selected) Color(0xFF91B9FF).copy(alpha = 0.55f) else Color(0xFF303037), shape)
                                                        .clickable { editedJournalMood = mood; showMoodSelector = false }
                                                        .semantics { contentDescription = journalMoodLabel(mood) }
                                                        .padding(horizontal = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                                        val tint = if (selected) Color(0xFF91B9FF) else Color(0xFF9A9AA4)
                                                        MoodGlyph(mood, 16.dp, tint)
                                                        Text(journalMoodLabel(mood), color = tint,
                                                            fontSize = 11.sp, maxLines = 1,
                                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                    }
                                                }
                                            }
                                        }
                                        // "Mood kaldır" lives as a small secondary action below the 3x5 grid,
                                        // not as a 16th grid item. Reuses the existing İptal/Sil TextButton styling.
                                        if (editedJournalMood != null) {
                                            TextButton(
                                                onClick = { editedJournalMood = null; showMoodSelector = false },
                                                modifier = Modifier.padding(top = 2.dp)
                                            ) {
                                                Text("Mood kaldır", color = Color(0xFFA5A5B0), fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }

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
                                        // Standalone Journal creation still needs its own actions because the
                                        // main reminder is not in edit mode in this flow.
                                        TextButton(onClick = { finishEditing() }) {
                                            Text("İptal", color = Color(0xFFA5A5B0), fontSize = 14.sp)
                                        }
                                        TextButton(
                                            onClick = {
                                                val validText = editedJournalText.trim()
                                                onSaveJournal(editedJournalMood, validText)
                                                journalCreating = false
                                                journalEditing = false
                                            },
                                            enabled = editedJournalMood != null || editedJournalText.trim().isNotBlank()
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
            // --- END JOURNAL SECTION ---

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = chromeAlpha }
                    .padding(top = 15.dp, start = 10.dp, end = 10.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (reminder.completedAt != null) {
                    Text(
                        text = "Tamamlandı · ${reminderDate(reminder.completedAt)}",
                        color = Color(0xFF8E8E98),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.End
                    )
                }
                if (reminder.journal != null) {
                    Text(
                        text = "Günlüğe eklendi · ${reminderDate(reminder.journal.createdAt)}",
                        color = Color(0xFF8E8E98),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.End
                    )
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
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
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
            AnimatedVisibility(
                visible = editing && !secondaryPanelsExpanded,
                enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(160)) + scaleIn(tween(180), initialScale = 0.98f),
                exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.98f)
            ) {
                DesignPanel(
                    backdrop = scene,
                    backdropPosition = { scenePosition },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
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
                                secondaryPanelsExpanded = true
                            }
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Renk ve kategori", color = Color(0xFFB7B7C0), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        ActionIcon(ActionGlyph.ChevronRight, Modifier.size(14.dp), Color(0xFF8E8E98))
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
                DesignPanel(
                    backdrop = scene, backdropPosition = { scenePosition },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    corners = 22.dp, designStyle = designStyle, reduceDynamicBlur = reduceDynamicBlur
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .clickable {
                                journalCreating = true
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Nasıl geçti?", color = Color(0xFFD3D3D9), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        ActionIcon(ActionGlyph.Add, Modifier.size(16.dp), Color(0xFF91B9FF))
                    }
                }
            }

            DesignPanel(scene, { scenePosition }, modifier = Modifier.fillMaxWidth(), corners = 36.dp, designStyle = designStyle, reduceDynamicBlur = reduceDynamicBlur, liquidDynamic = true) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (editing) {
                        DetailAction("Vazgeç", ActionGlyph.Close, Modifier.weight(1f), reduceAnimations = reduceAnimations, onClick = finishEditing)
                        DetailAction("Kaydet", ActionGlyph.Check, Modifier.weight(1f),
                            enabled = editedText.isNotBlank() && (!journalEditing || editedJournalMood != null || editedJournalText.trim().isNotBlank()),
                            color = Color(0xFF91B9FF), reduceAnimations = reduceAnimations, onClick = {
                                RealHaptics.success(view)
                                val finalItems = if (reminder.isChecklist) {
                                    val pending = if (newChecklistItemText.trim().isNotBlank()) {
                                        listOf(ChecklistItem(id = System.currentTimeMillis(), text = newChecklistItemText.trim()))
                                    } else emptyList()
                                    editedChecklistItems + pending
                                } else null
                                val removedAttachments = reminder.attachments.filter { orig ->
                                    editedAttachments.none { it.id == orig.id }
                                }
                                AttachmentStorage.deleteAttachmentFiles(removedAttachments)
                                newlyAddedAttachments = emptyList()
                                onSave(
                                    editedText.trim(),
                                    accentStart,
                                    accentEnd,
                                    selectedCategoryId,
                                    finalItems,
                                    if (reminder.isImportant) starBorderColorIndex else reminder.starBorderColorIndex,
                                    if (accentStart != null && accentEnd != null) gradientBlendFraction else null,
                                    customSolidColorArgb,
                                    editedAttachments,
                                    editedLocation
                                )
                                if (journalEditing) {
                                    onSaveJournal(editedJournalMood, editedJournalText.trim())
                                }
                                finishEditing()
                            })
                    } else {
                        DetailAction(if (reminder.isCompleted) "Geri al" else "Tamamla",
                            if (reminder.isCompleted) ActionGlyph.Undo else ActionGlyph.Check,
                            Modifier.weight(1f), enabled = !deleting, reduceAnimations = reduceAnimations, onClick = {
                                if (!reminder.isCompleted) {
                                    RealHaptics.success(view)
                                } else {
                                    RealHaptics.toggle(view, on = false)
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
                            starBorderColorIndex = reminder.starBorderColorIndex ?: Math.floorMod(reminder.id, 10)
                            gradientBlendFraction = reminder.gradientBlendFraction ?: DEFAULT_GRADIENT_BLEND_FRACTION
                            newChecklistItemText = ""
                            editedAttachments = reminder.attachments
                            editedLocation = reminder.location
                            newlyAddedAttachments = emptyList()
                            secondaryPanelsExpanded = false
                            // Düzenle is now the single edit entry point: if a Journal entry already
                            // exists, it becomes editable here too (no separate pencil trigger).
                            if (reminder.journal != null) journalEditing = true
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
                    RealHaptics.toggle(view, on = !item.isCompleted)
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
    reduceAnimations: Boolean
) {
    val view = LocalView.current
    val circleBorderColor = if (item.isCompleted) Color.Transparent else Color(0xFF5E5E68)
    val circleBgColor = if (item.isCompleted) Color(0xFF686873) else Color.Transparent
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
                .clip(CircleShape)
                .background(circleBgColor, CircleShape)
                .border(1.5.dp, circleBorderColor, CircleShape)
                .clickable {
                    RealHaptics.toggle(view, on = !item.isCompleted)
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
            .heightIn(min = 64.dp).padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        ActionIcon(glyph, Modifier.size(24.dp), tint)
        Text(label, fontSize = 11.sp, color = tint, maxLines = 1)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(name = "Cam detay", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun DetailPreview() {
    BigBosTheme {
        ReminderDetailScreen(ReminderItem(1, "Akşam için birkaç küçük not", 1789610400000), {}, {}, {}, { _, _, _, _, _, _, _, _, _, _ -> }, {})
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
