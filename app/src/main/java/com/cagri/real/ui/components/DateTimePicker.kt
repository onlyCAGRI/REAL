package com.cagri.real.ui.components

import android.annotation.SuppressLint

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.cagri.real.ui.DesignStyle
import com.cagri.real.data.RecurrenceRule
import com.cagri.real.data.clampedDate
import com.cagri.real.data.descriptionFor
import com.cagri.real.data.reminderTimestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class PickerSubScreen {
    Main,
    Repeat,
    Early
}

/**
 * Early warning + end ("Bitiş") for the card being dated. Provided by the activity: (reminderId, earlyAlertMinutes, endAt).
 */
val LocalSetReminderTiming = staticCompositionLocalOf<(Long, Int?, Long?) -> Unit> { { _, _, _ -> } }

/** "15 dk", "1 saat", "1 gün" … for an early warning. */
fun earlyAlertLabel(minutes: Int): String = when {
    minutes >= 1440 && minutes % 1440 == 0 -> "${minutes / 1440} gün"
    minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60} saat"
    minutes > 60 -> "${minutes / 60} sa ${minutes % 60} dk"
    else -> "$minutes dk"
}

private val DAYS_CACHE = (28..31).associateWith { len -> (1..len).toList() }
private fun daysForMonthLength(length: Int): List<Int> =
    DAYS_CACHE[length] ?: (1..length).toList()

private val MONTH_VALUES = (1..12).toList()
private val HOUR_VALUES = (0..23).toList()
private val MINUTE_VALUES = (0..59).toList()


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
    initialRepeatRule: RecurrenceRule = RecurrenceRule.NONE,
    initialTimeTab: Boolean = false,
    allowYearSelection: Boolean = true,
    initialEarlyAlertMinutes: Int? = null,
    initialEndAt: Long? = null,
    /** When set, "Erken uyarı" and "+ Bitiş" are offered; called with them just after [onConfirm]. */
    onConfirmTiming: ((Int?, Long?) -> Unit)? = null
) {
    var sessionKey by rememberSaveable { mutableIntStateOf(0) }
    var sessionOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible && !sessionOpen) {
            sessionKey++
            sessionOpen = true
        } else if (!visible && sessionOpen) {
            sessionOpen = false
        }
    }

    val density = LocalDensity.current
    val verticalOffsetPx = remember(density) { with(density) { 8.dp.roundToPx() } }
    AnimatedVisibility(
        visible = visible && sessionOpen,
        enter = if (reduceAnimations) EnterTransition.None
        else fadeIn(tween(200, easing = RealMotion.Emphasized)) +
                slideInVertically(tween(220, easing = RealMotion.Emphasized)) { verticalOffsetPx },
        exit = if (reduceAnimations) ExitTransition.None
        else fadeOut(tween(160, easing = RealMotion.Exit)) +
                slideOutVertically(tween(160, easing = RealMotion.Exit)) { verticalOffsetPx }
    ) {
        key(sessionKey) {
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
                initialRepeatRule = initialRepeatRule,
                initialTimeTab = initialTimeTab,
                allowYearSelection = allowYearSelection,
                sessionKey = sessionKey,
                initialEarlyAlertMinutes = initialEarlyAlertMinutes,
                initialEndAt = initialEndAt,
                onConfirmTiming = onConfirmTiming
            )
        }
    }
}


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


@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class)
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
    initialRepeatRule: RecurrenceRule = RecurrenceRule.NONE,
    initialTimeTab: Boolean = false,
    allowYearSelection: Boolean = true,
    sessionKey: Int = 0,
    initialEarlyAlertMinutes: Int? = null,
    initialEndAt: Long? = null,
    onConfirmTiming: ((Int?, Long?) -> Unit)? = null
) {
    val zone = ZoneId.systemDefault()
    val seed = remember(initial, sessionKey) { Instant.ofEpochMilli(initial ?: (System.currentTimeMillis() + 3_600_000)).atZone(zone) }
    var day by rememberSaveable(sessionKey, initial) { mutableIntStateOf(seed.dayOfMonth) }
    var month by rememberSaveable(sessionKey, initial) { mutableIntStateOf(seed.monthValue) }
    var year by rememberSaveable(sessionKey, initial) { mutableIntStateOf(seed.year) }
    var hour by rememberSaveable(sessionKey, initial) { mutableIntStateOf(seed.hour) }
    var minute by rememberSaveable(sessionKey, initial) { mutableIntStateOf(seed.minute) }
    var includeYear by rememberSaveable(sessionKey, initial) { mutableStateOf(allowYearSelection && initial != null && !initialTimeTab) }
    var timeTab by rememberSaveable(sessionKey, initial, initialTimeTab) { mutableStateOf(initialTimeTab) }
    var currentSubScreen by rememberSaveable(sessionKey, initial) { mutableStateOf(PickerSubScreen.Main) }
    var selectedRepeatRule by rememberSaveable(sessionKey, initialRepeatRule) { mutableStateOf(initialRepeatRule) }
    val timingEnabled = onConfirmTiming != null
    var earlyMinutes by rememberSaveable(sessionKey) { mutableStateOf(initialEarlyAlertMinutes) }
    // Bitiş is edited on the same wheels: the header reads "Başlangıç → Bitiş" and tapping a side picks what they move.
    val endSeed = remember(sessionKey) { Instant.ofEpochMilli(initialEndAt ?: (seed.toInstant().toEpochMilli() + 3_600_000L)).atZone(zone) }
    var hasEnd by rememberSaveable(sessionKey) { mutableStateOf(initialEndAt != null) }
    var editingEnd by rememberSaveable(sessionKey) { mutableStateOf(false) }
    var endDay by rememberSaveable(sessionKey) { mutableIntStateOf(endSeed.dayOfMonth) }
    var endMonth by rememberSaveable(sessionKey) { mutableIntStateOf(endSeed.monthValue) }
    var endHour by rememberSaveable(sessionKey) { mutableIntStateOf(endSeed.hour) }
    var endMinute by rememberSaveable(sessionKey) { mutableIntStateOf(endSeed.minute) }
    var dateAnimatedOnce by remember { mutableStateOf(false) }
    var timeAnimatedOnce by remember { mutableStateOf(false) }
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
    // Without the year option the picked date keeps the year it was opened with (no guessing next year).
    val effectiveYear = when {
        !allowYearSelection -> seed.year
        includeYear -> year
        else -> inferredYear
    }
    val date = clampedDate(effectiveYear, month, day)
    val currentTimestamp = reminderTimestamp(date, hour, minute, zone)
    // The end's year: the first such date on or after the start's day (a December start can end in January).
    val endYear = if (clampedDate(date.year, endMonth, endDay) < date) date.year + 1 else date.year
    val endDate = clampedDate(endYear, endMonth, endDay)
    val endAt = if (hasEnd) reminderTimestamp(endDate, endHour, endMinute, zone) else null
    val shortDate = remember { DateTimeFormatter.ofPattern("d MMM", tr) }
    val dateFormat = remember(includeYear) {
        DateTimeFormatter.ofPattern(if (includeYear) "dd/MM/yyyy" else "dd/MM", tr)
    }
    val weekdayLabel = remember(date) {
        date.dayOfWeek
            .getDisplayName(java.time.format.TextStyle.FULL, tr)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(tr) else it.toString() }
    }
    val scrollChanged: (String, Boolean) -> Unit = { key, scrolling -> moving = if (scrolling) moving + key else moving - key }
    val view = LocalView.current
    val controlFill = when {
        reduceDynamicBlur -> Color(0xFF151518)
        designStyle == DesignStyle.LiquidGlass -> LiquidGlassTokens.contentColor
        designStyle == DesignStyle.Ice -> Color.White.copy(alpha = 0.055f)
        else -> Color.White.copy(alpha = 0.10f)
    }
    val selectedFill = when {
        reduceDynamicBlur -> Color(0xFF3B3B40)
        designStyle == DesignStyle.LiquidGlass -> LiquidGlassTokens.contentColor
        designStyle == DesignStyle.Ice -> Color.White.copy(alpha = 0.13f)
        else -> Color.White.copy(alpha = 0.19f)
    }
    val cardFill = when {
        reduceDynamicBlur -> Color(0xFF1A1A1E)
        designStyle == DesignStyle.LiquidGlass -> LiquidGlassTokens.contentColor
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
            designStyle = designStyle, liquidSheet = true,
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
                        val enterAnim = fadeIn(tween(200, easing = RealMotion.Standard)) +
                                slideInHorizontally(tween(220, easing = RealMotion.Standard)) {
                                    if (enteringForward) (it * 0.08f).toInt() else (-it * 0.08f).toInt()
                                }
                        val exitAnim = fadeOut(tween(150, easing = RealMotion.Exit)) +
                                slideOutHorizontally(tween(170, easing = RealMotion.Exit)) {
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
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    modifier = Modifier.then(
                                        if (reduceAnimations) Modifier
                                        else Modifier.animateContentSize(
                                            animationSpec = tween(220, easing = RealMotion.Emphasized)
                                        )
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    val startText = if (hasEnd) date.format(shortDate) + " · " + String.format(tr, "%02d:%02d", hour, minute)
                                        else weekdayLabel + " · " + date.format(dateFormat) + " · " + String.format(tr, "%02d:%02d", hour, minute)
                                    Text(
                                        startText,
                                        color = if (!editingEnd) Color(0xFFF2F2F5) else Color(0xFFD9D9DE).copy(alpha = 0.5f),
                                        fontSize = 13.sp,
                                        fontWeight = if (!editingEnd && hasEnd) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1,
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                            .clickable(enabled = moving.isEmpty()) { editingEnd = false }
                                            .padding(horizontal = 4.dp, vertical = 4.dp)
                                            .semantics { contentDescription = "Başlangıç $startText" }
                                    )
                                    if (timingEnabled) {
                                        Text("→", color = Color(0xFF8E8E93), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
                                        val endText = if (hasEnd) endDate.format(shortDate) + " · " + String.format(tr, "%02d:%02d", endHour, endMinute) else "Bitiş"
                                        Text(
                                            endText,
                                            color = when {
                                                editingEnd -> Color(0xFFF2F2F5)
                                                hasEnd -> Color(0xFFD9D9DE).copy(alpha = 0.5f)
                                                else -> Color(0xFF8E8E93)
                                            },
                                            fontSize = 13.sp,
                                            fontWeight = if (editingEnd) FontWeight.SemiBold else FontWeight.Medium,
                                            maxLines = 1,
                                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                                .combinedClickable(
                                                    enabled = moving.isEmpty(),
                                                    onClick = {
                                                        if (!hasEnd) {
                                                            val from = Instant.ofEpochMilli((currentTimestamp ?: System.currentTimeMillis()) + 3_600_000L).atZone(zone)
                                                            endDay = from.dayOfMonth; endMonth = from.monthValue; endHour = from.hour; endMinute = from.minute
                                                            hasEnd = true
                                                        }
                                                        editingEnd = true
                                                        error = null
                                                    },
                                                    // Long press removes the end.
                                                    onLongClick = {
                                                        if (hasEnd) { hasEnd = false; editingEnd = false; RealHaptics.tick(view) }
                                                    }
                                                )
                                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                                .semantics {
                                                    contentDescription = if (hasEnd) "Bitiş $endText" else "Bitiş ekle"
                                                    if (hasEnd) customActions = listOf(CustomAccessibilityAction("Bitişi kaldır") { hasEnd = false; editingEnd = false; true })
                                                }
                                        )
                                    }
                                }
                            }
                            if (designStyle == DesignStyle.LiquidGlass) {
                                LiquidSegmentedControl(
                                    labels = listOf("Tarih", "Saat"), selectedIndex = if (timeTab) 1 else 0,
                                    onSelect = { timeTab = it == 1 }, enabled = moving.isEmpty(),
                                    reduceAnimations = reduceAnimations
                                )
                            } else {
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(controlFill).padding(4.dp)) {
                                listOf(false to "Tarih", true to "Saat").forEach { (time, label) ->
                                    TextButton(onClick = { timeTab = time }, enabled = moving.isEmpty(), modifier = Modifier.weight(1f)
                                        .background(if (timeTab == time) selectedFill else Color.Transparent, RoundedCornerShape(22.dp))) {
                                        Text(label, color = Color.White)
                                    }
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
                                            tween(160, easing = RealMotion.Standard)
                                        ) + slideInHorizontally(
                                            tween(180, easing = RealMotion.Standard)
                                        ) { if (toTime) (it * 0.04f).toInt() else (-it * 0.04f).toInt() }

                                        val exitSpec = fadeOut(
                                            tween(130, easing = RealMotion.Exit)
                                        ) + slideOutHorizontally(
                                            tween(150, easing = RealMotion.Exit)
                                        ) { if (toTime) (-it * 0.04f).toInt() else (it * 0.04f).toInt() }

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

                                        val wheelDate = if (editingEnd) endDate else date
                                        NumberWheel(
                                            "Gün", daysForMonthLength(wheelDate.lengthOfMonth()), wheelDate.dayOfMonth, { it.toString() },
                                            { if (editingEnd) endDay = it else day = it; error = null }, { scrollChanged("day", it) },
                                            Modifier.weight(1f),
                                            animateInitial = shouldAnimateDate,
                                            initialScrollDelayMs = 70L,
                                            initialOffsetItems = 8,
                                            initialScrollDurationMs = 480,
                                            reduceAnimations = reduceAnimations
                                        )
                                        NumberWheel(
                                            "Ay", MONTH_VALUES, if (editingEnd) endMonth else month, { months[it - 1] }, {
                                                if (editingEnd) { endMonth = it; endDay = clampedDate(endYear, it, endDay).dayOfMonth }
                                                else { month = it; day = clampedDate(effectiveYear, it, day).dayOfMonth }
                                                error = null
                                            }, { scrollChanged("month", it) },
                                            Modifier.weight(1.2f),
                                            animateInitial = shouldAnimateDate,
                                            initialScrollDelayMs = 110L,
                                            initialOffsetItems = 4,
                                            initialScrollDurationMs = 500,
                                            reduceAnimations = reduceAnimations
                                        )
                                        if (includeYear && !editingEnd) {
                                            NumberWheel(
                                                "Yıl", years, year, { it.toString() }, {
                                                    year = it; day = clampedDate(it, month, day).dayOfMonth; error = null
                                                }, { scrollChanged("year", it) },
                                                Modifier.weight(1.3f),
                                                animateInitial = shouldAnimateDate,
                                                initialScrollDelayMs = 100L,
                                                reduceAnimations = reduceAnimations
                                            )
                                        }
                                    } else {
                                        val shouldAnimateTime = !timeAnimatedOnce && !reduceAnimations
                                        LaunchedEffect(Unit) {
                                            timeAnimatedOnce = true
                                        }

                                        NumberWheel(
                                            "Saat", HOUR_VALUES, if (editingEnd) endHour else hour, { it.toString().padStart(2, '0') },
                                            { if (editingEnd) endHour = it else hour = it; error = null }, { scrollChanged("hour", it) },
                                            Modifier.weight(1f),
                                            animateInitial = shouldAnimateTime,
                                            initialScrollDelayMs = 20L,
                                            reduceAnimations = reduceAnimations
                                        )
                                        Text(
                                            ":", color = Color.White, fontSize = 30.sp,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )
                                        NumberWheel(
                                            "Dakika", MINUTE_VALUES, if (editingEnd) endMinute else minute, { it.toString().padStart(2, '0') },
                                            { if (editingEnd) endMinute = it else minute = it; error = null }, { scrollChanged("minute", it) },
                                            Modifier.weight(1f),
                                            animateInitial = shouldAnimateTime,
                                            initialScrollDelayMs = 60L,
                                            reduceAnimations = reduceAnimations
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (allowYearSelection) {
                                    Row(
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .heightIn(min = 44.dp)
                                            .clickable(enabled = moving.isEmpty()) {
                                                val enabled = !includeYear
                                                includeYear = enabled
                                                if (enabled) year = inferredYear
                                                day = clampedDate(if (enabled) year else inferredYear, month, day).dayOfMonth
                                                error = null
                                            }
                                            .padding(horizontal = 4.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            Modifier.size(20.dp)
                                                .background(if (includeYear) Color.White else Color.Transparent, CircleShape)
                                                .border(1.2.dp, if (includeYear) Color.Transparent else Color(0xFF777780), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (includeYear) ActionIcon(ActionGlyph.Check, Modifier.size(13.dp), Color(0xFF111216))
                                        }
                                        Text(
                                            "Yıl gir",
                                            color = if (includeYear) Color.White else Color(0xFFE1E1E6),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(start = 6.dp),
                                            maxLines = 1
                                        )
                                    }
                                }

                                val repeatSelected = selectedRepeatRule != RecurrenceRule.NONE
                                Row(
                                    modifier = Modifier
                                        .weight(1.15f)
                                        .heightIn(min = 44.dp)
                                        .clickable(enabled = moving.isEmpty()) { currentSubScreen = PickerSubScreen.Repeat }
                                        .padding(horizontal = 4.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        Modifier.size(20.dp)
                                            .border(1.2.dp, if (repeatSelected) Color.White else Color(0xFF777780), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ActionIcon(ActionGlyph.Repeat, Modifier.size(11.dp), if (repeatSelected) Color.White else Color(0xFF9A9AA3))
                                    }
                                    Text(
                                        "Tekrarlama",
                                        color = if (repeatSelected) Color.White else Color(0xFFE1E1E6),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(start = 5.dp),
                                        maxLines = 1
                                    )
                                }

                                val earlySelected = earlyMinutes != null
                                Row(
                                    modifier = Modifier
                                        .weight(1.05f)
                                        .heightIn(min = 44.dp)
                                        .clickable(enabled = timingEnabled && moving.isEmpty()) { currentSubScreen = PickerSubScreen.Early }
                                        .padding(horizontal = 4.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        Modifier.size(20.dp).border(1.2.dp, if (earlySelected) Color.White else Color(0xFF777780), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ActionIcon(ActionGlyph.Bell, Modifier.size(11.dp), if (earlySelected) Color.White else Color(0xFF9A9AA3))
                                    }
                                    Text(
                                        earlyMinutes?.let { earlyAlertLabel(it) + " önce" } ?: "Erken uyarı",
                                        color = when {
                                            earlySelected -> Color.White
                                            timingEnabled -> Color(0xFFE1E1E6)
                                            else -> Color(0xFFA7A7AF)
                                        },
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(start = 5.dp),
                                        maxLines = 1
                                    )
                                }
                            }
                            if (!exactAlarmsAllowed) {
                                Text("Tam saatinde bildirim için Alarmlar ve hatırlatıcılar iznini açabilirsin. İzin kapalıysa Android geciktirebilir.",
                                    color = Color(0xFFA8A8B0), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 14.dp))
                                TextButton(onClick = onRequestExactAlarms) { Text("İzni aç", color = Color(0xFF91B9FF)) }
                            }
                            error?.let { Text(it, color = Color(0xFFFFA4AA), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
                            Row(Modifier.fillMaxWidth().padding(top = 12.dp).then(if (designStyle == DesignStyle.LiquidGlass) Modifier.liquidChrome(24.dp) else Modifier), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("İptal", color = Color.White, fontSize = 18.sp) }
                                Box(Modifier.size(1.dp, 22.dp).background(Color(0xFF555559)))
                                TextButton(enabled = moving.isEmpty(), onClick = {
                                    val selectedDate = clampedDate(effectiveYear, month, day)
                                    val at = reminderTimestamp(selectedDate, hour, minute, zone)
                                    if (at == null) error = "Bu saat, saat değişimi nedeniyle kullanılamıyor."
                                    else if (at <= System.currentTimeMillis()) error = "Gelecekte bir tarih ve saat seç."
                                    else if (hasEnd && endAt == null) error = "Bitiş saati, saat değişimi nedeniyle kullanılamıyor."
                                    else if (endAt != null && endAt <= at) error = "Bitiş, başlangıçtan sonra olmalı."
                                    else {
                                        onConfirm(at, selectedRepeatRule)
                                        onConfirmTiming?.invoke(earlyMinutes, endAt)
                                    }
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
                    PickerSubScreen.Early -> {
                        EarlyAlertSelectionScreen(
                            selected = earlyMinutes,
                            cardFill = cardFill,
                            onSelect = { earlyMinutes = it },
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
            RealBackButton(onBack, description = "Geri dön")
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
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.72f),
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
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

private enum class EarlyUnit(val minutes: Int, val word: String, val range: IntRange, val step: Int, val default: Int) {
    Minutes(1, "dakika", 5..180, 5, 15),
    Hours(60, "saat", 1..72, 1, 1),
    Days(1440, "gün", 1..30, 1, 1)
}

/** Which row a saved early warning belongs to: whole days, then whole hours, else minutes. */
private fun earlyUnitOf(minutes: Int): EarlyUnit = when {
    minutes >= 1440 && minutes % 1440 == 0 -> EarlyUnit.Days
    minutes >= 60 && minutes % 60 == 0 && minutes / 60 <= 72 -> EarlyUnit.Hours
    else -> EarlyUnit.Minutes
}

/**
 * Erken uyarı, laid out like the repeat list: Yok / 15 dakika önce / 1 saat önce / 1 gün önce. Tapping a row picks it,
 * its number grows and arrows appear; dragging the row (or tapping an arrow) rolls the number — no keyboard.
 * Minutes go up to 3 hours, hours up to 72, days up to 30.
 */
@Composable
private fun EarlyAlertSelectionScreen(
    selected: Int?,
    cardFill: Color,
    onSelect: (Int?) -> Unit,
    onBack: () -> Unit
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val selectedUnit = selected?.let { earlyUnitOf(it) }
    var active by rememberSaveable { mutableStateOf(selectedUnit) }
    val amounts = remember {
        mutableStateMapOf<EarlyUnit, Int>().apply {
            EarlyUnit.entries.forEach { put(it, it.default) }
            if (selected != null && selectedUnit != null) put(selectedUnit, (selected / selectedUnit.minutes).coerceIn(selectedUnit.range))
        }
    }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(34.dp, 3.dp).background(Color(0xFF666669), RoundedCornerShape(2.dp)))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            RealBackButton(onBack, description = "Geri dön")
            Text("Erken uyarı", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cardFill)) {
            @Composable
            fun Divider() = HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color.White.copy(alpha = 0.07f))

            Row(
                Modifier.fillMaxWidth().selectable(selected = active == null, onClick = { active = null; onSelect(null); onBack() })
                    .padding(horizontal = 18.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Yok", color = if (active == null) Color.White else Color.White.copy(alpha = 0.72f), fontSize = 16.sp,
                    fontWeight = if (active == null) FontWeight.SemiBold else FontWeight.Normal)
            }
            EarlyUnit.entries.forEach { unit ->
                Divider()
                val isActive = active == unit
                val amount = amounts[unit] ?: unit.default
                fun step(up: Boolean) {
                    val next = (amount + if (up) unit.step else -unit.step).coerceIn(unit.range)
                    if (next == amount) return
                    amounts[unit] = next
                    onSelect(next * unit.minutes)
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                }
                val stepPx = with(density) { 18.dp.toPx() }
                val arrowAlpha by animateFloatAsState(if (isActive) 0.55f else 0f, tween(180), label = "earlyArrows")
                val numberSize by animateFloatAsState(if (isActive) 26f else 16f, tween(220, easing = RealMotion.Emphasized), label = "earlyNumber")
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected = isActive, onClick = { active = unit; onSelect(amount * unit.minutes) })
                        .pointerInput(unit) {
                            var carried = 0f
                            detectVerticalDragGestures(
                                onDragStart = { carried = 0f; active = unit; onSelect((amounts[unit] ?: unit.default) * unit.minutes) },
                                onVerticalDrag = { change, dy ->
                                    change.consume()
                                    carried -= dy
                                    while (kotlin.math.abs(carried) >= stepPx) {
                                        val up = carried > 0
                                        carried += if (up) -stepPx else stepPx
                                        val current = amounts[unit] ?: unit.default
                                        val next = (current + if (up) unit.step else -unit.step).coerceIn(unit.range)
                                        if (next != current) {
                                            amounts[unit] = next
                                            onSelect(next * unit.minutes)
                                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                        }
                                    }
                                }
                            )
                        }
                        .semantics {
                            contentDescription = "$amount ${unit.word} önce"
                            customActions = listOf(
                                CustomAccessibilityAction("Arttır") { active = unit; step(true); true },
                                CustomAccessibilityAction("Azalt") { active = unit; step(false); true }
                            )
                        }
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ActionIcon(ActionGlyph.ChevronRight, Modifier.size(14.dp).graphicsLayer { rotationZ = -90f; alpha = arrowAlpha }
                            .clickable(enabled = isActive) { step(true) }, Color.White)
                        RollingNumberText(amount, color = if (isActive) Color.White else Color.White.copy(alpha = 0.72f),
                            style = androidx.compose.ui.text.TextStyle(fontSize = numberSize.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal))
                        ActionIcon(ActionGlyph.ChevronRight, Modifier.size(14.dp).graphicsLayer { rotationZ = 90f; alpha = arrowAlpha }
                            .clickable(enabled = isActive) { step(false) }, Color.White)
                    }
                    Text(" ${unit.word} önce", color = if (isActive) Color.White else Color.White.copy(alpha = 0.72f), fontSize = 16.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Tamam", color = Color.White, fontSize = 16.sp)
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
    initialScrollDelayMs: Long = 0L,
    initialOffsetItems: Int? = null,
    initialScrollDurationMs: Int = 0,
    reduceAnimations: Boolean = false
) {
    val targetIndex = values.indexOf(value).coerceAtLeast(0)
    val initialIndex = remember {
        if (animateInitial && values.size > 2) {
            val step = initialOffsetItems ?: when {
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
    val density = LocalDensity.current
    val rowHeight = 54.dp * density.fontScale.coerceIn(1f, 1.4f)
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
            if (initialScrollDurationMs > 0) {
                val itemHeightPx = with(density) { rowHeight.toPx() }
                val deltaPx = (targetIndex - initialIndex) * itemHeightPx
                state.animateScrollBy(
                    value = deltaPx,
                    animationSpec = tween(
                        durationMillis = initialScrollDurationMs,
                        easing = RealMotion.Emphasized
                    )
                )
                state.scrollToItem(targetIndex)
            } else {
                state.animateScrollToItem(
                    index = targetIndex,
                    scrollOffset = 0
                )
            }
            initialScrollDone = true
        }
    }

    var suppressSettleHapticValue by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(value, values.size) {
        if (initialScrollDone && !state.isScrollInProgress) {
            suppressSettleHapticValue = value
            state.scrollToItem(values.indexOf(value).coerceAtLeast(0))
        }
    }

    val view = LocalView.current
    LaunchedEffect(state, values) {
        var wasMoving = false
        snapshotFlow { state.isScrollInProgress }.distinctUntilChanged().collect { scrolling ->
            if (initialScrollDone && !scrolling && wasMoving && state.layoutInfo.visibleItemsInfo.isNotEmpty()) {
                val settled = values.getOrNull(centered)
                if (settled != null) {
                    if (settled == suppressSettleHapticValue) {
                        suppressSettleHapticValue = null
                    } else if (settled != latestValue) {
                        if (label != "Yıl") RealHaptics.tick(view)
                        latestOnValue(settled)
                    }
                }
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
        modifier = modifier.height(rowHeight * 3)
            .then(if (LocalDesignStyle.current == DesignStyle.LiquidGlass) Modifier.liquidWheelWindow(rowHeight) else Modifier)
            .semantics {
            contentDescription = label
            stateDescription = format(value)
            customActions = listOf(
                CustomAccessibilityAction("Önceki") { values.getOrNull(values.indexOf(value) - 1)?.let(onValue); true },
                CustomAccessibilityAction("Sonraki") { values.getOrNull(values.indexOf(value) + 1)?.let(onValue); true }
            )
        }
    ) {
        itemsIndexed(values, key = { _, item -> item }) { index, item ->
            val isCentered = index == centered
            val itemColor by animateColorAsState(
                targetValue = if (isCentered) Color.White else Color(0xFF8E8E93),
                animationSpec = if (reduceAnimations) snap() else tween(140),
                label = "wheelItemColor"
            )
            val itemScale by animateFloatAsState(
                targetValue = if (isCentered) {
                    if (reduceAnimations) 1.0f else 1.04f
                } else {
                    if (reduceAnimations) 1.0f else 0.96f
                },
                animationSpec = if (reduceAnimations) snap() else tween(140, easing = RealMotion.Emphasized),
                label = "wheelItemScale"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .clickable {
                        if (item != latestValue) {
                            if (label != "Yıl") RealHaptics.tick(view)
                            suppressSettleHapticValue = item
                            onValue(item)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    format(item),
                    color = itemColor,
                    fontSize = if (label == "Yıl") 26.sp else 28.sp,
                    maxLines = 1,
                    fontWeight = if (isCentered) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.graphicsLayer {
                        scaleX = itemScale
                        scaleY = itemScale
                    }
                )
            }
        }
    }
}
