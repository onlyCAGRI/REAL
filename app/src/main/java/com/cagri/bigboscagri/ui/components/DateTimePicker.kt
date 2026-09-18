package com.cagri.bigboscagri.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import com.cagri.bigboscagri.ui.DesignStyle
import com.cagri.bigboscagri.data.RecurrenceRule
import com.cagri.bigboscagri.data.clampedDate
import com.cagri.bigboscagri.data.descriptionFor
import com.cagri.bigboscagri.data.reminderTimestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class PickerSubScreen {
    Main,
    Repeat
}

/** Keeps the picker composed through its exit so both opening and closing stay smooth. */
@Composable
fun AnimatedDateTimePickerOverlay(
    visible: Boolean,
    initial: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long, RecurrenceRule) -> Unit,
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    designStyle: DesignStyle,
    reduceDynamicBlur: Boolean,
    reduceAnimations: Boolean,
    exactAlarmsAllowed: Boolean,
    onRequestExactAlarms: () -> Unit,
    initialRepeatRule: RecurrenceRule = RecurrenceRule.NONE
) {
    AnimatedVisibility(
        visible = visible,
        enter = if (reduceAnimations) EnterTransition.None
        else fadeIn(tween(200)) + slideInVertically(tween(350)) { it / 10 },
        exit = if (reduceAnimations) ExitTransition.None
        else fadeOut(tween(180)) + slideOutVertically(tween(250)) { it / 10 }
    ) {
        DateTimePickerOverlay(
            initial = initial,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            backdrop = backdrop,
            backdropPosition = backdropPosition,
            designStyle = designStyle,
            reduceDynamicBlur = reduceDynamicBlur,
            reduceAnimations = reduceAnimations,
            exactAlarmsAllowed = exactAlarmsAllowed,
            onRequestExactAlarms = onRequestExactAlarms,
            initialRepeatRule = initialRepeatRule
        )
    }
}

/** Overload for backwards compatibility */
@Composable
fun AnimatedDateTimePickerOverlay(
    visible: Boolean,
    initial: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    designStyle: DesignStyle,
    reduceDynamicBlur: Boolean,
    reduceAnimations: Boolean,
    exactAlarmsAllowed: Boolean,
    onRequestExactAlarms: () -> Unit
) {
    AnimatedDateTimePickerOverlay(
        visible = visible,
        initial = initial,
        onDismiss = onDismiss,
        onConfirm = { at, _ -> onConfirm(at) },
        backdrop = backdrop,
        backdropPosition = backdropPosition,
        designStyle = designStyle,
        reduceDynamicBlur = reduceDynamicBlur,
        reduceAnimations = reduceAnimations,
        exactAlarmsAllowed = exactAlarmsAllowed,
        onRequestExactAlarms = onRequestExactAlarms
    )
}

/** In-window overlay: preserves the same glass scene and never commits a cancelled selection. */
@Composable
fun DateTimePickerOverlay(
    initial: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long, RecurrenceRule) -> Unit,
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    designStyle: DesignStyle,
    reduceDynamicBlur: Boolean,
    reduceAnimations: Boolean = false,
    exactAlarmsAllowed: Boolean,
    onRequestExactAlarms: () -> Unit,
    initialRepeatRule: RecurrenceRule = RecurrenceRule.NONE
) {
    val zone = ZoneId.systemDefault()
    val seed = remember { Instant.ofEpochMilli(initial ?: (System.currentTimeMillis() + 3_600_000)).atZone(zone) }
    var day by rememberSaveable { mutableIntStateOf(seed.dayOfMonth) }
    var month by rememberSaveable { mutableIntStateOf(seed.monthValue) }
    var year by rememberSaveable { mutableIntStateOf(seed.year) }
    var hour by rememberSaveable { mutableIntStateOf(seed.hour) }
    var minute by rememberSaveable { mutableIntStateOf(seed.minute) }
    var includeYear by rememberSaveable { mutableStateOf(initial != null) }
    var timeTab by rememberSaveable { mutableStateOf(false) }
    var currentSubScreen by rememberSaveable { mutableStateOf(PickerSubScreen.Main) }
    var selectedRepeatRule by rememberSaveable { mutableStateOf(initialRepeatRule) }
    var dateAnimatedOnce by rememberSaveable { mutableStateOf(false) }
    var timeAnimatedOnce by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var moving by remember { mutableStateOf(emptySet<String>()) }
    val tr = remember { Locale.forLanguageTag("tr-TR") }
    val months = remember { listOf("OCA", "ŞUB", "MAR", "NİS", "MAY", "HAZ", "TEM", "AĞU", "EYL", "EKİ", "KAS", "ARA") }
    val currentYear = LocalDate.now(zone).year
    val firstYear = minOf(seed.year, currentYear)
    val years = remember(firstYear, currentYear) { (firstYear..currentYear + 20).toList() }
    val now = System.currentTimeMillis()
    val candidateThisYear = reminderTimestamp(clampedDate(currentYear, month, day), hour, minute, zone)
    val inferredYear = if (candidateThisYear != null && candidateThisYear > now) currentYear else currentYear + 1
    val effectiveYear = if (includeYear) year else inferredYear
    val date = clampedDate(effectiveYear, month, day)
    val currentTimestamp = reminderTimestamp(date, hour, minute, zone)
    val dateFormat = remember(includeYear) {
        DateTimeFormatter.ofPattern(if (includeYear) "EEE, dd/MM/yyyy" else "dd/MM", tr)
    }
    val scrollChanged: (String, Boolean) -> Unit = { key, scrolling -> moving = if (scrolling) moving + key else moving - key }
    val controlFill = when {
        reduceDynamicBlur -> Color(0xFF151518)
        designStyle == DesignStyle.Ice -> Color.White.copy(alpha = 0.055f)
        else -> Color.White.copy(alpha = 0.10f)
    }
    val selectedFill = when {
        reduceDynamicBlur -> Color(0xFF3B3B40)
        designStyle == DesignStyle.Ice -> Color.White.copy(alpha = 0.13f)
        else -> Color.White.copy(alpha = 0.19f)
    }
    val cardFill = when {
        reduceDynamicBlur -> Color(0xFF1A1A1E)
        designStyle == DesignStyle.Ice -> Color.White.copy(alpha = 0.06f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    BackHandler {
        if (currentSubScreen != PickerSubScreen.Main) {
            currentSubScreen = PickerSubScreen.Main
        } else {
            onDismiss()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelMax = maxHeight * 0.94f
        val scrimAlpha = if (reduceDynamicBlur) 0.62f else if (designStyle == DesignStyle.Ice) 0.54f else 0.38f
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(onClick = {
                if (currentSubScreen != PickerSubScreen.Main) {
                    currentSubScreen = PickerSubScreen.Main
                } else {
                    onDismiss()
                }
            }).semantics { contentDescription = "Tarih seçimini kapat" })
        DesignPanel(
            backdrop = backdrop,
            backdropPosition = backdropPosition,
            designStyle = designStyle,
            reduceDynamicBlur = reduceDynamicBlur,
            corners = 32.dp,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp).heightIn(max = panelMax)
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .semantics { paneTitle = "Hatırlatıcı tarihi ve saati" }
        ) {
            AnimatedContent(
                targetState = currentSubScreen,
                transitionSpec = {
                    if (reduceAnimations) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        val enteringForward = targetState != PickerSubScreen.Main && initialState == PickerSubScreen.Main
                        val enterAnim = fadeIn(tween(200, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))) +
                                slideInHorizontally(tween(220, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))) {
                                    if (enteringForward) (it * 0.08f).toInt() else (-it * 0.08f).toInt()
                                }
                        val exitAnim = fadeOut(tween(150, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))) +
                                slideOutHorizontally(tween(170, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))) {
                                    if (enteringForward) (-it * 0.08f).toInt() else (it * 0.08f).toInt()
                                }
                        (enterAnim togetherWith exitAnim).using(SizeTransform(clip = false))
                    }
                },
                label = "pickerSubScreenContent"
            ) { subScreen ->
                when (subScreen) {
                    PickerSubScreen.Main -> {
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(34.dp, 3.dp).background(Color(0xFF666669), RoundedCornerShape(2.dp)))
                            Text(date.format(dateFormat) + " · " + String.format(tr, "%02d:%02d", hour, minute),
                                color = Color(0xFFC6C6CB), fontSize = 15.sp, modifier = Modifier.padding(vertical = 20.dp))
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(controlFill).padding(4.dp)) {
                                listOf(false to "Tarih", true to "Saat").forEach { (time, label) ->
                                    TextButton(onClick = { timeTab = time }, enabled = moving.isEmpty(), modifier = Modifier.weight(1f)
                                        .background(if (timeTab == time) selectedFill else Color.Transparent, RoundedCornerShape(22.dp))) {
                                        Text(label, color = Color.White)
                                    }
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                            AnimatedContent(
                                targetState = timeTab,
                                transitionSpec = {
                                    if (reduceAnimations) {
                                        EnterTransition.None togetherWith ExitTransition.None
                                    } else {
                                        val toTime = targetState
                                        val enterSpec = fadeIn(
                                            tween(200, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f))
                                        ) + slideInHorizontally(
                                            tween(240, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f))
                                        ) { if (toTime) (it * 0.06f).toInt() else (-it * 0.06f).toInt() }

                                        val exitSpec = fadeOut(
                                            tween(140, easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f))
                                        ) + slideOutHorizontally(
                                            tween(180, easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f))
                                        ) { if (toTime) (-it * 0.06f).toInt() else (it * 0.06f).toInt() }

                                        (enterSpec togetherWith exitSpec).using(SizeTransform(clip = false))
                                    }
                                },
                                label = "timeTabContent"
                            ) { isTime ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (!isTime) {
                                        val shouldAnimateDate = !dateAnimatedOnce && !reduceAnimations
                                        LaunchedEffect(Unit) {
                                            dateAnimatedOnce = true
                                        }

                                        NumberWheel(
                                            "Gün", (1..date.lengthOfMonth()).toList(), date.dayOfMonth, { it.toString() },
                                            { day = it; error = null }, { scrollChanged("day", it) },
                                            Modifier.weight(1f),
                                            animateInitial = shouldAnimateDate,
                                            initialScrollDelayMs = 20L
                                        )
                                        NumberWheel(
                                            "Ay", (1..12).toList(), month, { months[it - 1] }, {
                                                month = it; day = clampedDate(year, it, day).dayOfMonth; error = null
                                            }, { scrollChanged("month", it) },
                                            Modifier.weight(1.2f),
                                            animateInitial = shouldAnimateDate,
                                            initialScrollDelayMs = 60L
                                        )
                                        if (includeYear) {
                                            NumberWheel(
                                                "Yıl", years, year, { it.toString() }, {
                                                    year = it; day = clampedDate(it, month, day).dayOfMonth; error = null
                                                }, { scrollChanged("year", it) },
                                                Modifier.weight(1.3f),
                                                animateInitial = shouldAnimateDate,
                                                initialScrollDelayMs = 100L
                                            )
                                        }
                                    } else {
                                        val shouldAnimateTime = !timeAnimatedOnce && !reduceAnimations
                                        LaunchedEffect(Unit) {
                                            timeAnimatedOnce = true
                                        }

                                        NumberWheel(
                                            "Saat", (0..23).toList(), hour, { it.toString().padStart(2, '0') },
                                            { hour = it; error = null }, { scrollChanged("hour", it) },
                                            Modifier.weight(1f),
                                            animateInitial = shouldAnimateTime,
                                            initialScrollDelayMs = 20L
                                        )
                                        Text(
                                            ":", color = Color.White, fontSize = 30.sp,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )
                                        NumberWheel(
                                            "Dakika", (0..59).toList(), minute, { it.toString().padStart(2, '0') },
                                            { minute = it; error = null }, { scrollChanged("minute", it) },
                                            Modifier.weight(1f),
                                            animateInitial = shouldAnimateTime,
                                            initialScrollDelayMs = 60L
                                        )
                                    }
                                }
                            }
                            if (!timeTab) {
                                Row(
                                    Modifier.fillMaxWidth().toggleable(
                                        value = includeYear,
                                        role = Role.Checkbox,
                                        enabled = moving.isEmpty(),
                                        onValueChange = { enabled ->
                                            includeYear = enabled
                                            if (enabled) year = inferredYear
                                            day = clampedDate(if (enabled) year else inferredYear, month, day).dayOfMonth
                                            error = null
                                        }
                                    ).padding(top = 14.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier.size(23.dp)
                                            .background(if (includeYear) Color(0xFF6EA8FF) else Color.Transparent, RoundedCornerShape(12.dp))
                                            .border(1.4.dp, if (includeYear) Color.Transparent else Color(0xFF777780), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (includeYear) ActionIcon(ActionGlyph.Check, Modifier.size(15.dp), Color(0xFF111216))
                                    }
                                    Text("Yıl gir", color = Color(0xFFE7E7EB), fontSize = 15.sp,
                                        modifier = Modifier.padding(start = 10.dp))
                                }
                            }

                            // Repeat Row
                            Spacer(Modifier.height(10.dp))
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cardFill)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable(enabled = moving.isEmpty()) {
                                        currentSubScreen = PickerSubScreen.Repeat
                                    }.padding(horizontal = 16.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Tekrarlama", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val repeatSummary = selectedRepeatRule.descriptionFor(currentTimestamp, zone)
                                        Text(repeatSummary, color = Color(0xFF91B9FF), fontSize = 14.sp)
                                        ActionIcon(ActionGlyph.ChevronRight, Modifier.size(14.dp), Color(0xFF8E8E96))
                                    }
                                }
                            }

                            if (!exactAlarmsAllowed) {
                                Text("Tam saatinde bildirim için Alarmlar ve hatırlatıcılar iznini açabilirsin. İzin kapalıysa Android geciktirebilir.",
                                    color = Color(0xFFA8A8B0), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 14.dp))
                                TextButton(onClick = onRequestExactAlarms) { Text("İzni aç", color = Color(0xFF91B9FF)) }
                            }
                            error?.let { Text(it, color = Color(0xFFFFA4AA), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
                            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("İptal", color = Color.White, fontSize = 18.sp) }
                                Box(Modifier.size(1.dp, 22.dp).background(Color(0xFF555559)))
                                TextButton(enabled = moving.isEmpty(), onClick = {
                                    val selectedDate = clampedDate(effectiveYear, month, day)
                                    val at = reminderTimestamp(selectedDate, hour, minute, zone)
                                    if (at == null) error = "Bu saat, saat değişimi nedeniyle kullanılamıyor."
                                    else if (at <= System.currentTimeMillis()) error = "Gelecekte bir tarih ve saat seç."
                                    else onConfirm(at, selectedRepeatRule)
                                }, modifier = Modifier.weight(1f)) { Text("Bitti", color = if (moving.isEmpty()) Color.White else Color.Gray, fontSize = 18.sp) }
                            }
                        }
                    }
                    PickerSubScreen.Repeat -> {
                        RepeatSelectionScreen(
                            selected = selectedRepeatRule,
                            alertTimestamp = currentTimestamp,
                            zone = zone,
                            cardFill = cardFill,
                            onSelect = {
                                selectedRepeatRule = it
                                currentSubScreen = PickerSubScreen.Main
                            },
                            onBack = { currentSubScreen = PickerSubScreen.Main }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RepeatSelectionScreen(
    selected: RecurrenceRule,
    alertTimestamp: Long?,
    zone: ZoneId,
    cardFill: Color,
    onSelect: (RecurrenceRule) -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(34.dp, 3.dp).background(Color(0xFF666669), RoundedCornerShape(2.dp)))
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp).semantics { contentDescription = "Geri dön" }
            ) {
                ActionIcon(ActionGlyph.Back, Modifier.size(20.dp), Color.White)
            }
            Text(
                "Tekrarlama",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cardFill)
        ) {
            val options = listOf(
                RecurrenceRule.NONE to "Yok",
                RecurrenceRule.DAILY to "Her gün",
                RecurrenceRule.WEEKLY to (alertTimestamp?.let { RecurrenceRule.WEEKLY.descriptionFor(it, zone) } ?: "Her hafta"),
                RecurrenceRule.MONTHLY to (alertTimestamp?.let { RecurrenceRule.MONTHLY.descriptionFor(it, zone) } ?: "Her ay"),
                RecurrenceRule.YEARLY to (alertTimestamp?.let { RecurrenceRule.YEARLY.descriptionFor(it, zone) } ?: "Her yıl")
            )

            options.forEachIndexed { index, (rule, title) ->
                val isSelected = selected == rule
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = isSelected,
                        onClick = { onSelect(rule) }
                    ).padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        title,
                        color = if (isSelected) Color(0xFF82B3FF) else Color.White,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (isSelected) {
                        ActionIcon(ActionGlyph.Check, Modifier.size(16.dp), Color(0xFF82B3FF))
                    }
                }
                if (index < options.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = Color.White.copy(alpha = 0.07f)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Kapat", color = Color.White, fontSize = 16.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberWheel(
    label: String,
    values: List<Int>,
    value: Int,
    format: (Int) -> String,
    onValue: (Int) -> Unit,
    onMoving: (Boolean) -> Unit,
    modifier: Modifier,
    animateInitial: Boolean = false,
    initialScrollDelayMs: Long = 0L
) {
    val targetIndex = values.indexOf(value).coerceAtLeast(0)
    val initialIndex = remember {
        if (animateInitial && values.size > 2) {
            // Start 8-12 items away (or as far as available in the list)
            // so the initial roll is clearly perceptible and visually satisfying
            val step = when {
                values.size >= 24 -> 10.coerceAtMost(values.size - 1)
                values.size >= 12 -> 7.coerceAtMost(values.size - 1)
                else -> 4.coerceAtMost(values.size - 1)
            }
            if (targetIndex >= step) {
                targetIndex - step
            } else if (values.lastIndex - targetIndex >= step) {
                targetIndex + step
            } else if (targetIndex >= 2) {
                targetIndex - 2
            } else {
                (targetIndex + 2).coerceAtMost(values.lastIndex)
            }
        } else {
            targetIndex
        }
    }
    val state = rememberLazyListState(initialIndex)
    val latestValue by rememberUpdatedState(value)
    val latestOnValue by rememberUpdatedState(onValue)
    val latestMoving by rememberUpdatedState(onMoving)
    val rowHeight = 54.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.4f)
    val centered by remember { derivedStateOf {
        val info = state.layoutInfo
        val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
        info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - middle) }?.index ?: 0
    } }

    var initialScrollDone by remember { mutableStateOf(!animateInitial || initialIndex == targetIndex) }

    LaunchedEffect(Unit) {
        if (!initialScrollDone) {
            if (initialScrollDelayMs > 0L) {
                delay(initialScrollDelayMs)
            }
            state.animateScrollToItem(
                index = targetIndex,
                scrollOffset = 0
            )
            initialScrollDone = true
        }
    }

    LaunchedEffect(value, values.size) {
        if (initialScrollDone && !state.isScrollInProgress) {
            state.scrollToItem(values.indexOf(value).coerceAtLeast(0))
        }
    }

    LaunchedEffect(state, values) {
        var wasMoving = false
        snapshotFlow { state.isScrollInProgress }.distinctUntilChanged().collect { scrolling ->
            if (initialScrollDone && !scrolling && wasMoving && state.layoutInfo.visibleItemsInfo.isNotEmpty()) {
                values.getOrNull(centered)?.takeIf { it != latestValue }?.let(latestOnValue)
            }
            wasMoving = scrolling
            latestMoving(scrolling)
        }
    }

    DisposableEffect(Unit) { onDispose { latestMoving(false) } }

    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = PaddingValues(vertical = rowHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.height(rowHeight * 3).semantics {
            contentDescription = label
            stateDescription = format(value)
            customActions = listOf(
                CustomAccessibilityAction("Önceki") { values.getOrNull(values.indexOf(value) - 1)?.let(onValue); true },
                CustomAccessibilityAction("Sonraki") { values.getOrNull(values.indexOf(value) + 1)?.let(onValue); true }
            )
        }
    ) {
        itemsIndexed(values, key = { _, item -> item }) { index, item ->
            Box(Modifier.fillMaxWidth().height(rowHeight).clickable { onValue(item) }, contentAlignment = Alignment.Center) {
                Text(
                    format(item),
                    color = if (index == centered) Color.White else Color(0xFF727277),
                    fontSize = if (label == "Yıl") 26.sp else 28.sp,
                    maxLines = 1,
                    fontWeight = if (index == centered) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
