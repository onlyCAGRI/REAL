package com.cagri.bigboscagri.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.cagri.bigboscagri.ui.components.AnimatedDateTimePickerOverlay
import com.cagri.bigboscagri.ui.components.RealWordmark
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.bigboscagri.R
import com.cagri.bigboscagri.data.RecurrenceRule
import com.cagri.bigboscagri.data.ReminderCategory
import com.cagri.bigboscagri.data.ReminderItem
import com.cagri.bigboscagri.data.inCategory
import com.cagri.bigboscagri.speech.VoicePhase
import com.cagri.bigboscagri.speech.rememberVoiceInput
import com.cagri.bigboscagri.ui.components.AccountMenuOverlay
import com.cagri.bigboscagri.ui.components.ActionGlyph
import com.cagri.bigboscagri.ui.components.ActionIcon
import com.cagri.bigboscagri.ui.components.CategoryGrid
import com.cagri.bigboscagri.ui.components.GlassComposer
import com.cagri.bigboscagri.ui.components.GuestAvatar
import com.cagri.bigboscagri.ui.components.ReminderCard
import com.cagri.bigboscagri.ui.components.TopGlassOverlay
import kotlinx.coroutines.launch

private data class ReminderRoute(val category: ReminderCategory?, val detailId: Long?) {
    val key: String get() = detailId?.let { "detail_$it" } ?: category?.name ?: "home"
}

@Composable
fun ReminderScreen(
    reminders: List<ReminderItem>,
    onAddReminder: suspend (String, Long?, RecurrenceRule) -> Boolean,
    onToggleCompleted: (Long) -> Unit,
    onToggleImportant: (Long) -> Unit,
    onEditReminder: (Long, String, Int?, Int?) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    onSetAlert: (Long, Long?, RecurrenceRule) -> Unit,
    exactAlarmsAllowed: Boolean = true,
    onRequestExactAlarms: () -> Unit = {},
    requestedReminderId: Long? = null,
    onRequestHandled: () -> Unit = {},
    reduceDynamicBlur: Boolean = false,
    onReduceDynamicBlurChange: (Boolean) -> Unit = {},
    reduceAnimations: Boolean = false,
    onReduceAnimationsChange: (Boolean) -> Unit = {},
    designStyle: DesignStyle = DesignStyle.Glass,
    onDesignStyleChange: (DesignStyle) -> Unit = {}
) {
    var category by rememberSaveable { mutableStateOf<ReminderCategory?>(null) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var draftAlertAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var draftRepeatRule by rememberSaveable { mutableStateOf(RecurrenceRule.NONE) }
    val stateHolder = rememberSaveableStateHolder()
    LaunchedEffect(requestedReminderId) {
        if (requestedReminderId != null) {
            ColdStartTracker.markExecuted()
            if (reminders.any { it.id == requestedReminderId }) { detailId = requestedReminderId; showSettings = false }
            onRequestHandled()
        }
    }
    BackHandler(enabled = !showSettings && (detailId != null || category != null)) {
        if (detailId != null) detailId = null else category = null
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
                    exactAlarmsAllowed = exactAlarmsAllowed, onRequestExactAlarms = onRequestExactAlarms,
                    onBack = { showSettings = false }
                )
            } else {
                @OptIn(ExperimentalSharedTransitionApi::class)
                SharedTransitionLayout {
                    AnimatedContent(
                        targetState = ReminderRoute(category, detailId),
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
                                    onBack = { detailId = null },
                                    onToggleCompleted = { onToggleCompleted(reminder.id); detailId = null },
                                    onToggleImportant = { onToggleImportant(reminder.id) },
                                    onSave = { text, accentStart, accentEnd ->
                                        onEditReminder(reminder.id, text, accentStart, accentEnd)
                                    },
                                    onDelete = { onDeleteReminder(reminder.id); detailId = null },
                                    reduceAnimations = reduceAnimations, reduceDynamicBlur = reduceDynamicBlur,
                                    designStyle = designStyle, onSetAlert = { at, rule -> onSetAlert(reminder.id, at, rule) },
                                    exactAlarmsAllowed = exactAlarmsAllowed, onRequestExactAlarms = onRequestExactAlarms,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@AnimatedContent
                                )
                            } else {
                                ReminderListScreen(
                                    reminders = reminders, category = route.category, draft = draft,
                                    alertAt = draftAlertAt, repeatRule = draftRepeatRule,
                                    onAlertChange = { at, rule -> draftAlertAt = at; draftRepeatRule = rule },
                                    exactAlarmsAllowed = exactAlarmsAllowed, onRequestExactAlarms = onRequestExactAlarms,
                                    onDraftChange = { draft = it }, onBack = { category = null },
                                    onOpenCategory = { category = it }, onOpenReminder = { detailId = it },
                                    onToggleCompleted = onToggleCompleted,
                                    reduceDynamicBlur = reduceDynamicBlur,
                                    reduceAnimations = reduceAnimations,
                                    designStyle = designStyle,
                                    onOpenSettings = { showSettings = true },
                                    onAddReminder = { text, at, rule ->
                                        val saved = onAddReminder(text, at, rule)
                                        if (saved) {
                                            draft = ""; draftAlertAt = null; draftRepeatRule = RecurrenceRule.NONE
                                            if (category != null) category = if (at == null) ReminderCategory.NoAlert else ReminderCategory.Planned
                                        }
                                        saved
                                    },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@AnimatedContent
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ReminderListScreen(
    reminders: List<ReminderItem>, category: ReminderCategory?, draft: String,
    alertAt: Long?, repeatRule: RecurrenceRule, onAlertChange: (Long?, RecurrenceRule) -> Unit,
    exactAlarmsAllowed: Boolean, onRequestExactAlarms: () -> Unit,
    onDraftChange: (String) -> Unit, onBack: () -> Unit,
    onOpenCategory: (ReminderCategory) -> Unit, onOpenReminder: (Long) -> Unit,
    onToggleCompleted: (Long) -> Unit, reduceDynamicBlur: Boolean,
    reduceAnimations: Boolean, designStyle: DesignStyle,
    onOpenSettings: () -> Unit, onAddReminder: suspend (String, Long?, RecurrenceRule) -> Boolean,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    var editing by remember { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var refocus by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var showAccountMenu by rememberSaveable { mutableStateOf(false) }
    val inputFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scene = rememberGraphicsLayer()
    var scenePosition by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val shouldAnimateColdStart = remember { category == null && ColdStartTracker.isEligible(reduceAnimations) }
    var isColdStarting by remember { mutableStateOf(shouldAnimateColdStart) }
    val coldStartProgress = remember { Animatable(if (shouldAnimateColdStart) 0f else 1f) }
    var targetLogoOffset by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(shouldAnimateColdStart) {
        if (shouldAnimateColdStart) {
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
    val submit: (String) -> Unit = { text ->
        if (text.isNotBlank() && !saving) {
            if (alertAt != null && alertAt <= System.currentTimeMillis()) {
                Toast.makeText(context, "Gelecekte bir tarih ve saat seç.", Toast.LENGTH_LONG).show()
                showDatePicker = true
            } else {
                saving = true
                scope.launch {
                    try {
                        if (onAddReminder(text.trim(), alertAt, if (alertAt != null) repeatRule else RecurrenceRule.NONE)) {
                            focusManager.clearFocus(); keyboard?.hide()
                            if (reduceAnimations) listState.scrollToItem(0) else listState.animateScrollToItem(0)
                        }
                    } finally { saving = false }
                }
            }
        }
    }
    val voice = rememberVoiceInput(draft, onDraftChange, submit, observeLevel = !reduceAnimations)
    val expanded = editing || voice.phase != VoicePhase.Idle || alertAt != null
    LaunchedEffect(showDatePicker) {
        if (!showDatePicker && refocus) { refocus = false; inputFocus.requestFocus(); keyboard?.show() }
    }
    val composerHeight by animateDpAsState(
        if (expanded) 128.dp else 58.dp,
        if (reduceAnimations) snap() else tween(250),
        label = "composerHeight"
    )
    val bottomGap by animateDpAsState(
        if (expanded) 20.dp else 12.dp,
        if (reduceAnimations) snap() else tween(250),
        label = "keyboardGap"
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
    BackHandler(enabled = showAccountMenu) { showAccountMenu = false }
    BackHandler(enabled = (editing || voice.phase != VoicePhase.Idle) && !showAccountMenu && !showDatePicker) {
        voice.cancel()
        focusManager.clearFocus()
        keyboard?.hide()
    }
    val today = rememberReminderDay()
    val active = remember(reminders, category, today) {
        if (category == null) reminders.filter { !it.isCompleted } else reminders.inCategory(category, today)
    }
    val completed = remember(reminders, category) {
        if (category == null) reminders.inCategory(ReminderCategory.Completed) else emptyList()
    }
    val statusHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    DisposableEffect(category) {
        if (category != null) showAccountMenu = false
        onDispose { }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding().imePadding()) {
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
                if (category == null) {
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
                                    if (targetLogoOffset == null) {
                                        targetLogoOffset = coords.positionInRoot()
                                    }
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
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp, end = 16.dp, start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Ana sayfaya dön" }) {
                            ActionIcon(ActionGlyph.Back, Modifier.size(23.dp))
                        }
                        Text(category.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(22.dp))
            }
            if (category == null) {
                item(key = "categories") {
                    CategoryGrid(
                        onOpenCategory = { voice.cancel(); focusManager.clearFocus(); keyboard?.hide(); onOpenCategory(it) },
                        onAdd = { voice.cancel(); inputFocus.requestFocus(); keyboard?.show() },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    SectionTitle(if (active.any { it.alertAt != null }) "Hatırlatıcılar" else "Uyarı yok")
                }
            }
            if (active.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().height(104.dp).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                        Text(category?.emptyMessage ?: "Henüz hatırlatıcı yok", color = Color(0xFF888890), fontSize = 14.sp)
                    }
                }
            }
            items(active, key = { it.id }, contentType = { "reminder" }) { reminder ->
                ReminderCard(
                    reminder, onToggle = { onToggleCompleted(reminder.id) },
                    onOpen = { voice.cancel(); focusManager.clearFocus(); keyboard?.hide(); onOpenReminder(reminder.id) },
                    reduceAnimations = reduceAnimations,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    modifier = Modifier
                        .then(if (reduceAnimations) Modifier else Modifier.animateItem(placementSpec = tween(350), fadeInSpec = tween(200), fadeOutSpec = tween(200)))
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
            }
            if (completed.isNotEmpty()) {
                item(key = "completed_heading") {
                    Spacer(Modifier.height(18.dp)); SectionTitle("Tamamlandı")
                }
                items(completed, key = { it.id }, contentType = { "reminder" }) { reminder ->
                    ReminderCard(
                        reminder, onToggle = { onToggleCompleted(reminder.id) },
                        onOpen = { voice.cancel(); focusManager.clearFocus(); keyboard?.hide(); onOpenReminder(reminder.id) },
                        reduceAnimations = reduceAnimations,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        modifier = Modifier
                            .then(if (reduceAnimations) Modifier else Modifier.animateItem(placementSpec = tween(350), fadeInSpec = tween(200), fadeOutSpec = tween(200)))
                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                }
            }
        }
        TopGlassOverlay(
            scene,
            { scenePosition },
            Modifier.align(Alignment.TopCenter),
            reduceDynamicBlur = reduceDynamicBlur
        )
        BottomFade(Modifier.align(Alignment.BottomCenter).height(composerHeight + bottomGap + 46.dp))
        GlassComposer(
            value = draft, onValueChange = onDraftChange,
            alertAt = alertAt, repeatRule = repeatRule, saving = saving,
            onPickDate = { voice.cancel(); focusManager.clearFocus(); keyboard?.hide(); showDatePicker = true },
            onClearDate = { onAlertChange(null, RecurrenceRule.NONE) },
            onSubmit = { if (voice.phase == VoicePhase.Idle) submit(draft) else voice.finish() },
            expanded = expanded, onFocusChanged = { editing = it }, focusRequester = inputFocus,
            backdrop = scene, backdropPosition = { scenePosition },
            voicePhase = voice.phase, voiceLevel = voice.level,
            onMicClick = { focusManager.clearFocus(); keyboard?.hide(); voice.toggle() },
            reduceDynamicBlur = reduceDynamicBlur,
            reduceAnimations = reduceAnimations,
            designStyle = designStyle,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = bottomGap).height(composerHeight)
                .graphicsLayer {
                    if (isColdStarting) {
                        alpha = coldStartProgress.value
                    }
                }
        )
        if (showAccountMenu && category == null) {
            AccountMenuOverlay(
                backdrop = scene,
                backdropPosition = { scenePosition },
                onDismiss = { showAccountMenu = false },
                onSettingsClick = onOpenSettings,
                reduceDynamicBlur = reduceDynamicBlur,
                reduceAnimations = reduceAnimations,
                designStyle = designStyle
            )
        }
        AnimatedDateTimePickerOverlay(visible = showDatePicker, initial = alertAt,
            initialRepeatRule = repeatRule,
            backdrop = scene, backdropPosition = { scenePosition },
            designStyle = designStyle, reduceDynamicBlur = reduceDynamicBlur,
            reduceAnimations = reduceAnimations,
            onDismiss = { showDatePicker = false },
            onConfirm = { at, rule -> onAlertChange(at, rule); refocus = true; showDatePicker = false },
            exactAlarmsAllowed = exactAlarmsAllowed, onRequestExactAlarms = onRequestExactAlarms)
        if (isColdStarting) {
            val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
            val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
            val startCenterX = screenWidthPx / 2f
            val startCenterY = screenHeightPx / 2f

            val logoWidthPx = with(density) { 112.dp.toPx() }
            val logoHeightPx = with(density) { 42.dp.toPx() }

            val fallbackTargetLeft = with(density) { 16.dp.toPx() }
            val fallbackTargetTop = with(density) { statusHeight.toPx() + 20.dp.toPx() }

            val targetLeft = targetLogoOffset?.x ?: fallbackTargetLeft
            val targetTop = targetLogoOffset?.y ?: fallbackTargetTop
            val targetCenterX = targetLeft + (logoWidthPx / 2f)
            val targetCenterY = targetTop + (logoHeightPx / 2f)

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
    Box(modifier.fillMaxWidth().background(Brush.verticalGradient(
        0f to Color.Transparent, 0.6f to Color.Black.copy(alpha = 0.2f), 1f to Color.Black.copy(alpha = 0.92f)
    )))
}
