package com.cagri.real.ui.components.planner

import com.cagri.real.ui.components.RealMotion
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.cagri.real.ui.components.ReminderColorChoices
import com.cagri.real.ui.components.rememberCategoryPlannerPreferences
import com.cagri.real.ui.components.categoryPlannerKey
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cagri.real.data.CustomCategory
import com.cagri.real.data.RecurrenceRule
import com.cagri.real.data.ReminderCategory
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.ROLLING_24_HOURS_MILLIS
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import com.cagri.real.ui.components.AnimatedDateTimePickerOverlay
import com.cagri.real.ui.components.CategoryIcon
import com.cagri.real.ui.components.CategoryIconGlyph
import com.cagri.real.ui.components.LiquidGlassSurface
import com.cagri.real.ui.components.TopGlassOverlay
import com.cagri.real.ui.components.shouldCaptureBackdrop
import com.cagri.real.ui.components.liquidChrome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Standalone planner destination; callbacks reuse REAL's authoritative create/open paths. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PlannerScreen(
    reminders: List<ReminderItem>,
    onDismiss: () -> Unit,
    onCreateReminder: suspend (String, Long, RecurrenceRule, Long?) -> Long?,
    /** New goal (title, categoryId) typed in the category screen's own "Add to REAL" window. */
    onCreateGoal: suspend (String, Long) -> Long? = { _, _ -> null },
    onCreateCalendarCard: suspend (String) -> Long? = { null },
    ownerName: String = "",
    customCategories: List<CustomCategory> = emptyList(),
    designStyle: DesignStyle = DesignStyle.LiquidGlass,
    reduceDynamicBlur: Boolean = false,
    reduceAnimations: Boolean = false,
    exactAlarmsAllowed: Boolean = true,
    onRequestExactAlarms: () -> Unit = {},
    onSetReminderAlert: (Long, Long, RecurrenceRule) -> Unit = { _, _, _ -> },
    onOpenCategory: (ReminderCategory?, Long?) -> Unit = { _, _ -> },
    onToggleReminder: (Long) -> Unit = {},
    /** Category pastel picked in the category screen's pencil popup: (categoryId, ARGB). */
    onUpdateCategoryColor: (Long, Int) -> Unit = { _, _ -> },
    /** (reminderId, tapped occurrence start for repeating events or null, close) */
    detailContent: @Composable (Long, Long?, () -> Unit) -> Unit = { _, _, _ -> },
    /** Opens straight onto this category's goals (from the category screen); closing them closes the planner. */
    initialGoalCategoryId: Long? = null,
    /** "Kategorize et" → "Yeni kategori": creates a category with this name (a phone calendar's name). */
    onCreateCalendarCategory: (String) -> Unit = {}
) {
    val proGoals = com.cagri.real.ui.subscription.rememberProAction(com.cagri.real.access.RealFeature.PlannerGoals)
    val uiContext = LocalContext.current
    val context = uiContext.applicationContext
    val calendarStore = remember(context) { com.cagri.real.calendar.CalendarStore(context) }
    val calendarCards = remember(calendarStore) { com.cagri.real.calendar.CalendarCardLinks(calendarStore) }
    val lifecycle = LocalLifecycleOwner.current
    val repository = remember(context) {
        PlannerRepository(com.cagri.real.platform.android.sync.SyncLocal.preferences(context, "real_planner_v1", Context.MODE_PRIVATE))
    }
    var details by remember { mutableStateOf<Map<Long, PlannerEventDetails>>(emptyMap()) }
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }
    var zone by remember { mutableStateOf(ZoneId.systemDefault()) }
    val today = now.atZone(zone).toLocalDate()
    val (persistedViewMode, updatePersistedViewMode) = rememberPlannerViewModePreference()
    var viewMode by rememberSaveable { mutableStateOf(persistedViewMode) }
    fun setViewMode(requested: PlannerViewMode) {
        // Takvim is the month view only; every way into the old daily focus lands on the month.
        val mode = if (requested == PlannerViewMode.Daily) PlannerViewMode.Monthly else requested
        viewMode = mode
        updatePersistedViewMode(mode)
    }
    LaunchedEffect(persistedViewMode) {
        if (viewMode != persistedViewMode) {
            viewMode = persistedViewMode
        }
    }
    LaunchedEffect(viewMode) {
        updatePersistedViewMode(viewMode)
    }
    val calendar = viewMode != PlannerViewMode.Hours24
    val (dailyView, _) = rememberPlannerDailyViewPreference()
    var selectedDayEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val selectedDay = LocalDate.ofEpochDay(selectedDayEpoch)
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var editDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var editHour by rememberSaveable { mutableIntStateOf(9) }
    var openedExternalEvent by remember { mutableStateOf<com.cagri.real.calendar.CalendarOccurrence?>(null) }
    var openedReminderId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Start time of the exact occurrence that was tapped; lets a repeating event be deleted per day.
    var openedOccurrenceMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    // Category detail ("Planning") overlay; the last id is kept so the exit slide still has content.
    var detailCategoryId by rememberSaveable { mutableStateOf<Long?>(initialGoalCategoryId) }
    var detailLastCategoryId by remember { mutableStateOf<Long?>(initialGoalCategoryId) }
    fun closeGoals() {
        detailCategoryId = null
        if (initialGoalCategoryId != null) onDismiss()
    }
    // Tapped category card -> its detail screen grows out of that card (and shrinks back into it on close).
    val revealOrigin = remember { PlannerRevealOrigin() }
    val revealProgress = remember { Animatable(0f) }
    var detailOverlayRoot by remember { mutableStateOf(Offset.Zero) }
    // Manual goal order per category (drag the dots). Stored locally as comma separated reminder ids.
    val goalOrderPrefs = remember(context) { context.getSharedPreferences("real_planner_goal_order_v1", Context.MODE_PRIVATE) }
    // Square / rectangle card choice per custom category: pure presentation, kept in planner prefs.
    val categoryShapePrefs = remember(context) { context.getSharedPreferences("real_planner_category_shape_v1", Context.MODE_PRIVATE) }
    var categoryShapes by remember(customCategories) {
        mutableStateOf(customCategories.associate { it.id to PlannerCategoryCardShape.fromKey(categoryShapePrefs.getString("cat_${it.id}", null)) })
    }
    var goalOrders by remember { mutableStateOf<Map<Long, List<Long>>>(emptyMap()) }
    var durationEditorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var timeEditorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var endTimeEditorId by rememberSaveable { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val screenSlide = remember { Animatable(if (reduceAnimations) 0f else -1f) }
    var isClosingPlanner by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!isClosingPlanner) {
            screenSlide.animateTo(
                0f, tween(if (reduceAnimations) 0 else 300,
                    easing = RealMotion.Standard)
            )
        }
    }
    fun dismissWithSlide() {
        if (isClosingPlanner) return
        isClosingPlanner = true
        updatePersistedViewMode(viewMode)
        scope.launch {
            screenSlide.animateTo(
                -1f, tween(if (reduceAnimations) 0 else 280,
                    easing = RealMotion.StandardInOut)
            )
            onDismiss()
        }
    }
    val scene = rememberGraphicsLayer()
    var scenePosition by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val modeSwipeThresholdPx = remember(density) { with(density) { 72.dp.toPx() } }
    // Measured glass chrome heights (px). Seeded with estimates so the first frame does not jump;
    // only positive sizes are accepted so a transient 0 can never collapse the list padding.
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var topChromePx by remember { mutableIntStateOf(with(density) { (statusTop + 62.dp).roundToPx() }) }
    var monthBarPx by remember { mutableIntStateOf(with(density) { 56.dp.roundToPx() }) }
    fun chromeInset(mode: PlannerViewMode): Dp = with(density) {
        val top = topChromePx.coerceAtLeast(0).toDp()
        val bar = if (mode == PlannerViewMode.Daily && dailyView == PlannerDailyView.Classic) monthBarPx.coerceAtLeast(0).toDp() else 4.dp
        top + bar
    }

    suspend fun reload() {
        try {
            details = repository.load()
            ready = true
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ready = false
            error = "Plan ayrıntıları okunamadı. Yeniden denemek için dokun."
        }
    }

    LaunchedEffect(repository) {
        com.cagri.real.data.ReminderRepository.updates.collect { reload() }
    }

    LaunchedEffect(repository, lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            reload()
            while (true) {
                now = Instant.now()
                zone = ZoneId.systemDefault()
                delay(30_000)
            }
        }
    }

    val month = YearMonth.parse(monthKey)
    val queryDay = now.atZone(zone).toLocalDate()
    val queryRange = remember(month, zone, queryDay) { plannerQueryRange(month, queryDay, zone) }
    val eventRangeStart = queryRange.startMillis
    val eventRangeEnd = queryRange.endMillis
    val externalCalendar = com.cagri.real.calendar.rememberExternalCalendarEvents(eventRangeStart, eventRangeEnd, reminders)
    val (categoryPlannerPreferences, categoryPlannerRevision) = rememberCategoryPlannerPreferences()
    val categoryUser = com.cagri.real.ui.connections.currentConnectionsUser()
    val sharedCategoryApi = remember(categoryUser?.id) { categoryUser?.let { com.cagri.real.sharedcategories.SharedCategoryApi(context.applicationContext, it.id) } }
    var showSharedCategories by remember { mutableStateOf(false) }
    var manageCategory by remember { mutableStateOf<CustomCategory?>(null) }
    var sharedCategoryCatalog by remember(sharedCategoryApi) { mutableStateOf<List<com.cagri.real.sharedcategories.SharedCategory>>(emptyList()) }
    var sharedCategoryDetails by remember(sharedCategoryApi) { mutableStateOf<Map<String, com.cagri.real.sharedcategories.SharedCategoryDetail>>(emptyMap()) }
    val categoryLifecycle by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(sharedCategoryApi, categoryLifecycle) {
        if (sharedCategoryApi == null || !categoryLifecycle.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return@LaunchedEffect
        sharedCategoryCatalog = sharedCategoryApi.cachedList()
        try { sharedCategoryCatalog = sharedCategoryApi.list() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* The explicit shared-category browser exposes refresh errors. */ }
    }
    LaunchedEffect(detailCategoryId, sharedCategoryApi, sharedCategoryCatalog) {
        val selected = customCategories.firstOrNull { it.id == detailCategoryId } ?: return@LaunchedEffect
        val shared = sharedCategoryCatalog.firstOrNull { it.isOwner && it.category.id == selected.id && it.category.createdAt == selected.createdAt } ?: return@LaunchedEffect
        try {
            val loaded = sharedCategoryApi?.metadata(shared.id) ?: return@LaunchedEffect
            sharedCategoryDetails = sharedCategoryDetails + (shared.id to loaded)
            categoryPlannerPreferences.setLink(categoryPlannerKey(null, selected)!!, loaded.item.link)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Do not clear locally visible metadata while offline. */ }
    }
    if (showSharedCategories) com.cagri.real.ui.sharedcategories.SharedCategoryBrowser(reduceAnimations) { showSharedCategories = false }
    manageCategory?.let { selected ->
        com.cagri.real.ui.sharedcategories.SharedCategoryMembersDialog(selected,
            categoryPlannerPreferences.link(categoryPlannerKey(null, selected)!!), onChanged = { loaded ->
                sharedCategoryDetails = sharedCategoryDetails + (loaded.item.id to loaded)
                sharedCategoryCatalog = sharedCategoryCatalog.filterNot { it.id == loaded.item.id } + loaded.item
                categoryPlannerPreferences.setLink(categoryPlannerKey(null, selected)!!, loaded.item.link)
            }, onDismiss = { manageCategory = null })
    }
    // Every card the user scheduled in REAL belongs in its calendar and in "Next 24 hours". The per-category
    // "REAL takviminde göster" switch only decides which category cards the calendar lists (calendarCategories).
    val calendarReminders = reminders
    val calendarCategories = remember(customCategories, categoryPlannerPreferences, categoryPlannerRevision) {
        customCategories.filter { categoryPlannerPreferences.visible(categoryPlannerKey(null, it)) }
    }
    val setReminderTiming = com.cagri.real.ui.components.LocalSetReminderTiming.current
    val calendarCategoryLinks = remember(uiContext) { com.cagri.real.calendar.CalendarCategoryLinks(uiContext) }
    val calendarCategoryRevision = com.cagri.real.calendar.CalendarCategoryLinks.revision.intValue
    // A new category asked for from "Kategorize et": linked once it exists (category creation returns no id).
    var pendingCalendarCategory by remember { mutableStateOf<Triple<String, com.cagri.real.calendar.CalendarOccurrence, Boolean>?>(null) }
    LaunchedEffect(customCategories, pendingCalendarCategory) {
        val (name, occurrence, whole) = pendingCalendarCategory ?: return@LaunchedEffect
        val created = customCategories.filter { it.name == name }.maxByOrNull { it.createdAt } ?: return@LaunchedEffect
        calendarCategoryLinks.link(occurrence.eventId, occurrence.calendarId, created.id, whole)
        pendingCalendarCategory = null
    }
    val events = remember(calendarReminders, details, zone, eventRangeStart, eventRangeEnd, externalCalendar.events, calendarCategoryRevision) {
        (plannerEvents(calendarReminders, details, zone, eventRangeStart, eventRangeEnd) +
            externalPlannerEvents(externalCalendar.events, zone) { calendarCategoryLinks.categoryFor(it.eventId, it.calendarId) })
            .sortedBy { it.startMillis }
    }
    val next24HourEvents = remember(events, now) {
        val start = now.toEpochMilli()
        val end = start + ROLLING_24_HOURS_MILLIS
        events.filter { it.startMillis in start..end }
    }
    // "Move 24 hour" is an explicit user action, so those cards are not gated by the per-category
    // calendar visibility (which defaults to hidden); only cards of a deleted category are skipped.
    val next24HourManual = remember(reminders, customCategories, now) {
        val start = now.toEpochMilli()
        val end = start + ROLLING_24_HOURS_MILLIS
        val categoryIds = customCategories.mapTo(HashSet()) { it.id }
        reminders.filter { reminder ->
            !reminder.isCompleted &&
                (reminder.customCategoryId == null || reminder.customCategoryId in categoryIds) &&
                reminder.quick24HourUntilAt?.let { it > start } == true &&
                reminder.alertAt?.let { it in start..end } != true
        }
    }

    val days = remember(month) { (1..month.lengthOfMonth()).map(month::atDay) }
    val categoryColors = remember(customCategories) { customCategories.associate { it.id to plannerCalendarPastel(it) } }
    val gridDays = remember(month) { plannerMonthDays(month) }
    val eventsByDay = remember(events, gridDays, zone) {
        gridDays.associateWith { day ->
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            events.filter {
                val until = it.spanEndMillis ?: it.externalEndMillis
                it.startMillis < end && if (until != null) until > start else (it.endMillis ?: it.startMillis) >= start
            }
        }
    }
    val dayListState = rememberLazyListState()
    val calendarStateHolder = rememberSaveableStateHolder()
    LaunchedEffect(monthKey, selectedDayEpoch, viewMode, dailyView) {
        if (viewMode == PlannerViewMode.Daily && dailyView == PlannerDailyView.Classic) {
            val index = if (YearMonth.from(selectedDay) == month) selectedDay.dayOfMonth - 1 else 0
            dayListState.scrollToItem(index)
        }
    }

    fun rememberVisibleDay() {
        if (viewMode == PlannerViewMode.Daily && dailyView == PlannerDailyView.Classic) {
            days.getOrNull(dayListState.firstVisibleItemIndex)?.let { selectedDayEpoch = it.toEpochDay() }
        }
    }

    fun calendarBack() {
        if (isClosingPlanner) return
        rememberVisibleDay()
        dismissWithSlide()
    }

    fun changeMonth(value: YearMonth) {
        selectedDayEpoch = if (value == YearMonth.from(today)) today.toEpochDay() else value.atDay(1).toEpochDay()
        monthKey = value.toString()
    }

    fun add(day: LocalDate, hour: Int) {
        if (ready) {
            durationEditorId = null
            timeEditorId = null
            endTimeEditorId = null
            editHour = hour.coerceIn(0, 23)
            editDay = day.toEpochDay()
        }
    }

    fun openOccurrence(id: Long, occurrenceMillis: Long?): Unit {
        if (id < 0) {
            val association = runCatching { calendarCards.associations().firstOrNull {
                it.eventId == -id && calendarStore.options().readIdentities[it.calendarId] == it.identity
            } }.getOrNull()
            if (association != null && reminders.any { it.id == association.cardId }) {
                openedExternalEvent = null
                openOccurrence(association.cardId, null)
                return
            }
            openedExternalEvent = externalCalendar.events.firstOrNull { event ->
                val displayedStart = if (event.allDay) Instant.ofEpochMilli(event.start)
                    .atZone(ZoneId.of("UTC")).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
                else event.start
                event.eventId == -id && (occurrenceMillis == null || displayedStart == occurrenceMillis)
            }
            if (openedExternalEvent == null) error = "Etkinlik değişmiş olabilir. Takvimi yenile."
            return
        }
        editDay = null
        durationEditorId = null
        timeEditorId = null
        endTimeEditorId = null
        openedOccurrenceMillis = occurrenceMillis
        openedReminderId = id
    }

    fun open(id: Long) = openOccurrence(id, null)

    fun saveDuration(id: Long, minutes: Int) {
        if (id < 0) { openOccurrence(id, null); return }
        scope.launch {
            try {
                val current = details[id] ?: PlannerEventDetails()
                details = repository.put(id, current.copy(durationMinutes = minutes))
                error = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Süre kaydedilemedi. Yeniden dene."
            }
        }
    }

    fun pickTime(reminder: ReminderItem) {
        if (reminder.id < 0) { openOccurrence(reminder.id, reminder.alertAt); return }
        durationEditorId = null
        endTimeEditorId = null
        timeEditorId = reminder.id
    }

    fun pickEndTime(reminder: ReminderItem) {
        if (reminder.id < 0) { openOccurrence(reminder.id, reminder.alertAt); return }
        durationEditorId = null
        timeEditorId = null
        endTimeEditorId = reminder.id
    }
    // "Süre ekle" on a 24 hour / month card opens this under it: start, end and duration. Opens without a bounce.
    val inlineDurationEditor: @Composable (PlannerEvent, Boolean) -> Unit = { itemEvent, expanded ->
        AnimatedVisibility(
            visible = expanded,
            enter = if (reduceAnimations) EnterTransition.None
                else fadeIn(tween(200)) + expandVertically(tween(260, easing = com.cagri.real.ui.components.RealMotion.Emphasized)),
            exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(100)) + shrinkVertically(tween(160))
        ) {
            val startTimeStr = if (itemEvent.isDateOnly24Hour) "Saat ekle" else formatTimeAmPm(itemEvent.startMillis, zone)
            val endMillis = itemEvent.endMillis
            val endTimeStr = if (itemEvent.isDateOnly24Hour || endMillis == null) "Bitiş ekle" else formatTimeAmPm(endMillis, zone)
            PlannerInlineEventEditor(
                startTime = startTimeStr,
                endTime = endTimeStr,
                durationMinutes = itemEvent.details?.durationMinutes,
                designStyle = designStyle,
                onPickTime = { pickTime(itemEvent.reminder) },
                onPickEndTime = { pickEndTime(itemEvent.reminder) },
                onPickDuration = { minutes -> saveDuration(itemEvent.id, minutes) }
            )
        }
    }

    val timeEditorReminder = remember(timeEditorId, reminders) {
        timeEditorId?.let { id -> reminders.firstOrNull { it.id == id } }
    }

    val endTimeEditorReminder = remember(endTimeEditorId, reminders) {
        endTimeEditorId?.let { id -> reminders.firstOrNull { it.id == id } }
    }


    Dialog(
        onDismissRequest = ::calendarBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        openedExternalEvent?.let { event ->
            com.cagri.real.calendar.ExternalCalendarEventDetail(
                occurrence = event,
                reduceDynamicBlur = reduceDynamicBlur,
                categories = customCategories,
                currentCategoryId = remember(event, calendarCategoryRevision) { calendarCategoryLinks.categoryFor(event.eventId, event.calendarId) },
                currentIsWholeCalendar = remember(event, calendarCategoryRevision) {
                    calendarCategoryLinks.categoryFor(event.eventId, event.calendarId) == null || calendarCategoryLinks.isWholeCalendar(event.calendarId)
                },
                onCategorize = { categoryId, newName, whole ->
                    when {
                        categoryId != null -> calendarCategoryLinks.link(event.eventId, event.calendarId, categoryId, whole)
                        newName != null -> {
                            pendingCalendarCategory = Triple(newName.take(60), event, whole)
                            onCreateCalendarCategory(newName.take(60))
                        }
                        else -> calendarCategoryLinks.link(event.eventId, event.calendarId, null, false)
                    }
                },
                onDismiss = { openedExternalEvent = null },
                onCreateRealCard = {
                    val provider = com.cagri.real.calendar.DeviceCalendarProvider(uiContext)
                    val source = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        check(calendarStore.isCurrent())
                        provider.calendars().singleOrNull { it.id == event.calendarId &&
                            calendarStore.options().readIdentities[it.id] == it.identity }
                            ?: error("Kaynak takvim değişti; seçimi yenile.")
                    }
                    val existing = calendarCards.associations().firstOrNull {
                        it.eventId == event.eventId && it.calendarId == event.calendarId && it.identity == source.identity &&
                            reminders.any { r -> r.id == it.cardId }
                    }
                    val id = existing?.cardId ?: onCreateCalendarCard(event.title.take(2000))
                        ?: error("REAL kartı oluşturulamadı.")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) {
                        check(calendarStore.isCurrent())
                        if (existing == null) calendarCards.associate(event, source, id)
                    }
                    openedExternalEvent = null
                    openOccurrence(id, null)
                }
            )
        }
        BackHandler(enabled = openedReminderId != null && timeEditorId == null && endTimeEditorId == null) { openedReminderId = null }
        BackHandler(enabled = detailCategoryId != null && openedReminderId == null) { closeGoals() }
        BackHandler(enabled = detailCategoryId == null && editDay == null && openedReminderId == null && timeEditorId == null && endTimeEditorId == null, onBack = ::calendarBack)

        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            val previousDim = window?.attributes?.dimAmount
            val previousAnimations = window?.attributes?.windowAnimations
            window?.setDimAmount(0f)
            window?.setWindowAnimations(0)
            onDispose {
                if (previousDim != null) window?.setDimAmount(previousDim)
                if (previousAnimations != null) window?.setWindowAnimations(previousAnimations)
            }
        }
        SharedTransitionLayout(
            Modifier.fillMaxSize().graphicsLayer {
                translationX = size.width * screenSlide.value
            }
        ) {
        val cardMotion = remember { PlannerCardMotion(this) }
        cardMotion.openedId = openedReminderId
        cardMotion.reduceMotion = reduceAnimations
        CompositionLocalProvider(LocalPlannerCardMotion provides cardMotion) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { scenePosition = it.positionInRoot() }
                    .drawWithContent {
                        if (shouldCaptureBackdrop(designStyle, reduceDynamicBlur, Build.VERSION.SDK_INT)) {
                            scene.record { this@drawWithContent.drawContent() }
                            drawLayer(scene)
                        } else {
                            drawContent()
                        }
                    }
            ) {
            Column(
                Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 14.dp)
            ) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                // Timeline rows keep their own horizontal scroll. At their start edge,
                // an additional right swipe returns to 24 hour without stealing time scrolling.
                val calendarReturnScroll = remember(
                    viewMode, editDay, openedReminderId, timeEditorId, endTimeEditorId,
                    durationEditorId, modeSwipeThresholdPx, dailyView
                ) {
                    object : NestedScrollConnection {
                        var rightDrag = 0f
                        override fun onPostScroll(
                            consumed: Offset, available: Offset, source: NestedScrollSource
                        ): Offset {
                            if (viewMode != PlannerViewMode.Daily || dailyView.usesNewLayout || editDay != null ||
                                openedReminderId != null || timeEditorId != null ||
                                endTimeEditorId != null || durationEditorId != null ||
                                source != NestedScrollSource.UserInput) {
                                rightDrag = 0f
                                return Offset.Zero
                            }
                            if (available.x > 0f && kotlin.math.abs(available.x) > kotlin.math.abs(available.y)) {
                                rightDrag += available.x
                                if (rightDrag >= modeSwipeThresholdPx) {
                                    rememberVisibleDay()
                                    setViewMode(PlannerViewMode.Hours24)
                                    rightDrag = 0f
                                }
                                return Offset(available.x, 0f)
                            }
                            rightDrag = 0f
                            return Offset.Zero
                        }
                        override suspend fun onPreFling(available: Velocity): Velocity {
                            rightDrag = 0f
                            return Velocity.Zero
                        }
                    }
                }
                AnimatedContent(
                    targetState = viewMode,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(calendarReturnScroll)
                        .pointerInput(viewMode, dailyView, month, editDay, openedReminderId, timeEditorId, endTimeEditorId, durationEditorId) {
                            // Unconsumed gestures on the calendar background work in both directions.
                            if (!(viewMode == PlannerViewMode.Daily && dailyView.usesNewLayout) &&
                                editDay == null && openedReminderId == null && timeEditorId == null && endTimeEditorId == null && durationEditorId == null) {
                                var horizontalDrag = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { horizontalDrag = 0f },
                                    onHorizontalDrag = { change, dragAmount ->
                                        horizontalDrag += dragAmount
                                        change.consume()
                                    },
                                    onDragCancel = { horizontalDrag = 0f },
                                    onDragEnd = {
                                        when {
                                            viewMode == PlannerViewMode.Monthly && horizontalDrag <= -modeSwipeThresholdPx -> {
                                                changeMonth(month.plusMonths(1))
                                            }
                                            viewMode == PlannerViewMode.Monthly && horizontalDrag >= modeSwipeThresholdPx -> {
                                                changeMonth(month.minusMonths(1))
                                            }
                                            viewMode == PlannerViewMode.Hours24 && horizontalDrag <= -modeSwipeThresholdPx -> {
                                                durationEditorId = null
                                                setViewMode(PlannerViewMode.Daily)
                                            }
                                            viewMode == PlannerViewMode.Daily && horizontalDrag >= modeSwipeThresholdPx -> {
                                                rememberVisibleDay()
                                                durationEditorId = null
                                                setViewMode(PlannerViewMode.Hours24)
                                            }
                                        }
                                        horizontalDrag = 0f
                                    }
                                )
                            }
                        },
                    transitionSpec = {
                        val easing = RealMotion.Standard
                        if (reduceAnimations) {
                            fadeIn(tween(100)) togetherWith fadeOut(tween(100))
                        } else if (initialState == PlannerViewMode.Hours24 || targetState == PlannerViewMode.Hours24) {
                            val direction = if (targetState == PlannerViewMode.Hours24) -1 else 1
                            (fadeIn(tween(210)) + slideInHorizontally(tween(280, easing = easing)) { direction * it / 5 }) togetherWith
                                (fadeOut(tween(150)) + slideOutHorizontally(tween(260, easing = easing)) { -direction * it / 5 })
                        } else {
                            // Daily/monthly changes use a quiet fade only.
                            fadeIn(tween(150, easing = easing)) togetherWith fadeOut(tween(90))
                        }
                    },
                    label = "plannerModeTransition"
                ) { visibleMode ->
                    val modeScope = this
                    calendarStateHolder.SaveableStateProvider(visibleMode.name) {
                    val sharedDayModifier: @Composable (LocalDate) -> Modifier = { Modifier }
                    if (visibleMode == PlannerViewMode.Hours24) {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = chromeInset(visibleMode), bottom = 24.dp)
                        ) {
                            item { PlannerTodayHeader(now, zone) }
                            item {
                                Spacer(Modifier.height(10.dp))
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp, vertical = 8.dp)
                                ) {
                                    val upcomingItems = remember(next24HourManual, next24HourEvents, details) {
                                        val list = mutableListOf<PlannerEvent>()
                                        next24HourManual.forEach { r ->
                                            list.add(
                                                PlannerEvent(
                                                    reminder = r,
                                                    details = details[r.id],
                                                    startMillis = r.alertAt ?: r.createdAt,
                                                    isDateOnly24Hour = r.alertAt == null
                                                )
                                            )
                                        }
                                        list.addAll(next24HourEvents)
                                        // Timed plans first, nearest at the top; all-day / 24 hour cards follow below them.
                                        list.sortedWith(compareBy<PlannerEvent> { it.isDateOnly24Hour }.thenBy { it.startMillis }.thenBy { it.id })
                                    }

                                    PlannerNext24Title(
                                        count = upcomingItems.size,
                                        nextStartMillis = upcomingItems.firstOrNull {
                                            !it.isDateOnly24Hour && it.startMillis >= now.toEpochMilli()
                                        }?.startMillis,
                                        now = now
                                    )

                                    if (upcomingItems.isEmpty()) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "Önündeki 24 saatte etkinlik yok.",
                                                color = Color(0xFF8E8E93),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Normal
                                            )
                                        }
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                            upcomingItems.forEach { itemEvent ->
                                                val cardColor = resolveNext24CardColor(itemEvent, customCategories)
                                                val isEditorExpanded = durationEditorId == itemEvent.id

                                                Column(Modifier.fillMaxWidth()) {
                                                    PlannerNext24Card(
                                                        event = itemEvent,
                                                        cardColor = cardColor,
                                                        zone = zone,
                                                        ownerName = itemEvent.externalCalendarName ?: ownerName,
                                                        onClick = { openOccurrence(itemEvent.id, itemEvent.startMillis) },
                                                        now = now,
                                                        onToggleDuration = if (itemEvent.id < 0) null else ({
                                                            durationEditorId = if (isEditorExpanded) null else itemEvent.id
                                                        })
                                                    )

                                                    inlineDurationEditor(itemEvent, isEditorExpanded)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {

                            if (visibleMode == PlannerViewMode.Monthly) {
                                val activeDayWindow = remember(selectedDayEpoch, dayListState.firstVisibleItemIndex, today, month) {
                                    val topDay = days.getOrNull(dayListState.firstVisibleItemIndex) ?: selectedDay
                                    setOf(topDay, topDay.plusDays(1), topDay.plusDays(2), selectedDay, today)
                                }
                                PlannerMonthOverview(
                                    month = month,
                                    reduceAnimations = reduceAnimations,
                                    today = today,
                                    selectedDay = selectedDay,
                                    eventsByDay = eventsByDay,
                                    customCategories = customCategories,
                                    activeDays = activeDayWindow,
                                    onChangeMonth = ::changeMonth,
                                    onPickDay = { day ->
                                        if (day == selectedDay) {
                                            setViewMode(PlannerViewMode.Daily)
                                        } else {
                                            selectedDayEpoch = day.toEpochDay()
                                            monthKey = YearMonth.from(day).toString()
                                        }
                                    },
                                    onOpenDay = { day ->
                                        selectedDayEpoch = day.toEpochDay()
                                        monthKey = YearMonth.from(day).toString()
                                        setViewMode(PlannerViewMode.Daily)
                                    },
                                    onOpenEvent = { id, at -> openOccurrence(id, at) },
                                    durationEditorId = durationEditorId,
                                    onToggleDuration = { ev -> durationEditorId = if (durationEditorId == ev.id) null else ev.id },
                                    durationEditor = inlineDurationEditor,
                                    dayModifier = sharedDayModifier,
                                    topPadding = chromeInset(visibleMode),
                                    ownerName = ownerName
                                )
                            } else if (dailyView.usesNewLayout) {
                                PlannerNewDailyView(
                                    selectedDay = selectedDay,
                                    today = today,
                                    events = events,
                                    zone = zone,
                                    ownerName = ownerName,
                                    customCategories = customCategories,
                                    categoryCardCategories = calendarCategories,
                                    topPadding = chromeInset(visibleMode),
                                    ready = ready,
                                    reduceAnimations = reduceAnimations,
                                    now = now,
                                    onSelectDay = { day ->
                                        selectedDayEpoch = day.toEpochDay()
                                        monthKey = YearMonth.from(day).toString()
                                    },
                                    onOpen = { id, at -> openOccurrence(id, at) },
                                    onAdd = ::add,
                                    reminders = calendarReminders,
                                    categoryShapes = categoryShapes,
                                    revealOrigin = revealOrigin,
                                    onOpenCategory = { builtIn, customId ->
                                        if (customId != null) {
                                            proGoals {
                                                detailLastCategoryId = customId
                                                detailCategoryId = customId
                                            }
                                        } else onOpenCategory(builtIn, customId)
                                    }
                                )
                            } else {
                                LazyColumn(
                                    Modifier.fillMaxSize(),
                                    state = dayListState,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    contentPadding = PaddingValues(top = chromeInset(visibleMode), bottom = 20.dp)
                                ) {
                                    items(days, key = { it.toEpochDay() }) { day ->
                                        PlannerDayCard(
                                            day = day,
                                            events = eventsByDay[day].orEmpty(),
                                            zone = zone,
                                            onOpen = { id, at -> openOccurrence(id, at) },
                                            onAdd = ::add,
                                            categoryColors = categoryColors,
                                            customCategories = customCategories,
                                            today = today,
                                            now = now,
                                            ready = ready,
                                            reduceAnimations = reduceAnimations,
                                            modifier = sharedDayModifier(day)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    } // SaveableStateProvider: retain each day's horizontal scroll between layers.
                }
                } // SharedTransitionLayout
            }
            } // scene Box: only the calendar content is recorded as the glass backdrop

            // Glass chrome: lives OUTSIDE the recorded scene so it can sample the calendar behind it.
            // (Recording the chrome into `scene` while it also draws `scene` is a layer loop.)
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().zIndex(1f)) {
                TopGlassOverlay(
                    backdrop = scene,
                    backdropPosition = { scenePosition },
                    modifier = Modifier.align(Alignment.TopCenter),
                    reduceDynamicBlur = reduceDynamicBlur,
                    extendLiquidArea = true
                )
                Column(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .onSizeChanged { if (it.height > 0) topChromePx = it.height }
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            com.cagri.real.ui.components.RealBackButton(
                                ::calendarBack, description = "Önceki görünüme dön",
                                visualSize = 44.dp, glyphSize = 20.dp, surface = {
                                    PlannerGlass(Modifier.fillMaxSize(), 22.dp, designStyle, scene,
                                        { scenePosition }, reduceDynamicBlur, pressFeedback = false) {}
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            PlannerGlass(
                                Modifier.weight(1f).height(44.dp), 24.dp, designStyle, scene, { scenePosition }, reduceDynamicBlur
                            ) {
                                Row(
                                    Modifier.fillMaxSize().padding(3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .then(
                                                if (!calendar) Modifier.liquidChrome(corners = 21.dp, selected = true)
                                                else Modifier.clip(RoundedCornerShape(21.dp))
                                            )
                                            .clickable(role = Role.Tab) {
                                                durationEditorId = null
                                                rememberVisibleDay()
                                                setViewMode(PlannerViewMode.Hours24)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "24 hour",
                                            color = if (!calendar) Color.White else Color(0xFFB0B0B8),
                                            fontSize = 13.sp,
                                            fontWeight = if (!calendar) FontWeight.SemiBold else FontWeight.Medium
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .then(
                                                if (calendar) Modifier.liquidChrome(corners = 21.dp, selected = true)
                                                else Modifier.clip(RoundedCornerShape(21.dp))
                                            )
                                            .clickable(role = Role.Tab) {
                                                durationEditorId = null
                                                when (viewMode) {
                                                    PlannerViewMode.Hours24 -> setViewMode(PlannerViewMode.Daily)
                                                    PlannerViewMode.Daily -> { rememberVisibleDay(); setViewMode(PlannerViewMode.Monthly) }
                                                    PlannerViewMode.Monthly -> setViewMode(PlannerViewMode.Daily)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Takvim",
                                            color = if (calendar) Color.White else Color(0xFFB0B0B8),
                                            fontSize = 13.sp,
                                            fontWeight = if (calendar) FontWeight.SemiBold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            PlannerGlass(
                                Modifier.size(44.dp), 22.dp, designStyle, scene, { scenePosition }, reduceDynamicBlur
                            ) {
                                Box(
                                    Modifier.matchParentSize()
                                        .clickable(enabled = ready, role = Role.Button) {
                                            val nextHour = now.atZone(zone).plusHours(1)
                                            val targetDay = when (viewMode) {
                                                PlannerViewMode.Hours24 -> nextHour.toLocalDate()
                                                PlannerViewMode.Daily -> if (dailyView.usesNewLayout) selectedDay
                                                    else days.getOrNull(dayListState.firstVisibleItemIndex) ?: selectedDay
                                                PlannerViewMode.Monthly -> selectedDay
                                            }
                                            add(targetDay, nextHour.hour)
                                        }
                                        .semantics { contentDescription = "Yeni etkinlik ekle" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    ActionIcon(ActionGlyph.Add, Modifier.size(20.dp), Color.White)
                                }
                            }
                        }

                        externalCalendar.error?.let {
                            Text(it, color = Color(0xFFFFBEAA), fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                        }
                        error?.let {
                            Text(
                                it,
                                color = Color(0xFFFFBEAA),
                                fontSize = 13.sp,
                                modifier = Modifier.clickable { scope.launch { reload() } }.padding(12.dp)
                            )
                        }
                    }

                    if (viewMode == PlannerViewMode.Daily && dailyView == PlannerDailyView.Classic) {
                        // Month strip: labels follow the finger while swiping, then slide the next month in.
                        // The strip gives no press feedback (no ripple, no press-scale); taps stay immediate.
                        var monthBarWidthPx by remember { mutableIntStateOf(0) }
                        var monthSlide by remember { mutableFloatStateOf(0f) }
                        var monthSettleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                        val currentMonth by rememberUpdatedState(month)
                        val slideSpan = maxOf(monthBarWidthPx * 0.30f, with(density) { 96.dp.toPx() })
                        val labelSlide = Modifier.graphicsLayer {
                            translationX = monthSlide
                            alpha = 1f - 0.9f * (kotlin.math.abs(monthSlide) / slideSpan).coerceIn(0f, 1f)
                        }
                        val noFeedback = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        fun tapMonth(target: YearMonth) {
                            monthSettleJob?.cancel()
                            monthSlide = 0f
                            changeMonth(target)
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .onSizeChanged {
                                    if (it.height > 0) monthBarPx = it.height
                                    monthBarWidthPx = it.width
                                }
                                .pointerInput(reduceAnimations, slideSpan) {
                                    var rawDrag = 0f
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                            monthSettleJob?.cancel()
                                            rawDrag = 0f
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            rawDrag += dragAmount
                                            if (!reduceAnimations) {
                                                monthSlide = (monthSlide + dragAmount * 0.9f).coerceIn(-slideSpan, slideSpan)
                                            }
                                        },
                                        onDragCancel = {
                                            rawDrag = 0f
                                            monthSettleJob?.cancel()
                                            monthSettleJob = scope.launch {
                                                androidx.compose.animation.core.animate(monthSlide, 0f, animationSpec = tween(160)) { v, _ -> monthSlide = v }
                                            }
                                        },
                                        onDragEnd = {
                                            val dir = when {
                                                rawDrag <= -modeSwipeThresholdPx -> 1
                                                rawDrag >= modeSwipeThresholdPx -> -1
                                                else -> 0
                                            }
                                            rawDrag = 0f
                                            monthSettleJob?.cancel()
                                            monthSettleJob = scope.launch {
                                                if (reduceAnimations) {
                                                    if (dir != 0) changeMonth(currentMonth.plusMonths(dir.toLong()))
                                                } else if (dir == 0) {
                                                    androidx.compose.animation.core.animate(monthSlide, 0f, animationSpec = tween(180)) { v, _ -> monthSlide = v }
                                                } else {
                                                    // Old labels finish their exit, months switch, new labels glide in from the other side.
                                                    androidx.compose.animation.core.animate(
                                                        monthSlide, -dir * slideSpan,
                                                        animationSpec = tween(110, easing = RealMotion.Exit)
                                                    ) { v, _ -> monthSlide = v }
                                                    changeMonth(currentMonth.plusMonths(dir.toLong()))
                                                    monthSlide = dir * slideSpan
                                                    androidx.compose.animation.core.animate(
                                                        monthSlide, 0f,
                                                        animationSpec = tween(240, easing = RealMotion.Standard)
                                                    ) { v, _ -> monthSlide = v }
                                                }
                                            }
                                        }
                                    )
                                }
                                .padding(horizontal = 18.dp, vertical = 6.dp)
                        ) {
                            PlannerGlass(
                                Modifier.fillMaxWidth(), 22.dp, designStyle, scene, { scenePosition }, reduceDynamicBlur,
                                pressFeedback = false
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        plannerMonthLabel(month.minusMonths(1).atDay(1)),
                                        color = Color(0xFF8E8E93),
                                        fontSize = 18.sp,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                            .clickable(interactionSource = noFeedback, indication = null, role = Role.Button) { tapMonth(month.minusMonths(1)) }
                                            .then(labelSlide)
                                    )
                                    Text(
                                        "‹",
                                        color = Color(0xFFB0B0B8),
                                        fontSize = 22.sp,
                                        modifier = Modifier
                                            .clickable(interactionSource = noFeedback, indication = null) { tapMonth(month.minusMonths(1)) }
                                            .padding(horizontal = 10.dp)
                                    )
                                    Text(
                                        plannerMonthLabel(month.atDay(1)),
                                        color = Color.White,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .then(labelSlide)
                                            .semantics {
                                                contentDescription = "${plannerMonthLabel(month.atDay(1))} ${month.year}"
                                            }
                                    )
                                    Text(
                                        "›",
                                        color = Color(0xFFB0B0B8),
                                        fontSize = 22.sp,
                                        modifier = Modifier
                                            .clickable(interactionSource = noFeedback, indication = null) { tapMonth(month.plusMonths(1)) }
                                            .padding(horizontal = 10.dp)
                                    )
                                    Text(
                                        plannerMonthLabel(month.plusMonths(1).atDay(1)),
                                        color = Color(0xFF8E8E93),
                                        fontSize = 18.sp,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                            .clickable(interactionSource = noFeedback, indication = null, role = Role.Button) { tapMonth(month.plusMonths(1)) }
                                            .then(labelSlide),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = editDay,
                modifier = Modifier.fillMaxSize().zIndex(10f),
                transitionSpec = {
                    val easing = RealMotion.Standard
                    if (targetState != null) {
                        (fadeIn(tween(180)) + slideInVertically(tween(320, easing = easing)) { it / 2 }) togetherWith
                            fadeOut(tween(80))
                    } else {
                        fadeIn(tween(80)) togetherWith
                            (fadeOut(tween(170)) + slideOutVertically(tween(300, easing = easing)) { it })
                    }
                },
                label = "plannerAddFromBottom"
            ) { day ->
                if (day != null) {
                    PlannerEventEditor(
                        initialDay = LocalDate.ofEpochDay(day),
                        initialHour = editHour,
                        customCategories = customCategories,
                        designStyle = designStyle,
                        reduceDynamicBlur = reduceDynamicBlur,
                        reduceAnimations = reduceAnimations,
                        exactAlarmsAllowed = exactAlarmsAllowed,
                        onRequestExactAlarms = onRequestExactAlarms,
                        onDismiss = { editDay = null },
                        glassBackdrop = scene,
                        glassBackdropPosition = { scenePosition }
                    ) { title, at, repeatRule, metadata, priorId, categoryId ->
                        val id = priorId ?: onCreateReminder(title, at, repeatRule, categoryId)
                        if (id == null) {
                            PlannerSaveResult(null, false, "Kart kaydedilemedi. Yeniden dene.")
                        } else {
                            try {
                                details = repository.put(id, metadata)
                                PlannerSaveResult(id, true)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                PlannerSaveResult(
                                    id,
                                    false,
                                    "REAL kartı kaydedildi; plan ayrıntıları kaydedilemedi. Yeniden dene; ikinci kart oluşturulmayacak."
                                )
                            }
                        }
                    }
                }
            }

            val detailOpen = detailCategoryId != null
            var detailWasOpen by remember { mutableStateOf(false) }
            LaunchedEffect(detailOpen) {
                if (detailOpen) detailWasOpen = true
                else if (detailWasOpen) { detailWasOpen = false; revealOrigin.returnTick++ }
                if (reduceAnimations || revealOrigin.bounds == null) {
                    revealProgress.snapTo(if (detailOpen) 1f else 0f)
                } else if (detailOpen) {
                    revealProgress.snapTo(0f)
                    revealProgress.animateTo(1f, tween(PlannerRevealOpenMs, easing = PlannerEaseOut))
                } else {
                    revealProgress.animateTo(0f, tween(PlannerRevealCloseMs, easing = RealMotion.StandardInOut))
                    revealOrigin.bounds = null
                }
            }
            val growFromCard = !reduceAnimations && revealOrigin.bounds != null
            AnimatedVisibility(
                visible = detailCategoryId != null,
                modifier = Modifier.fillMaxSize().zIndex(15f),
                enter = if (reduceAnimations || growFromCard) EnterTransition.None else slideInHorizontally(
                    tween(300, easing = RealMotion.Standard)
                ) { it },
                exit = if (reduceAnimations) ExitTransition.None else if (growFromCard) {
                    // Keeps the layer alive while the card-shrink below plays; moves nothing itself.
                    slideOutHorizontally(tween(PlannerRevealCloseMs + 40)) { 0 }
                } else slideOutHorizontally(
                    tween(260, easing = RealMotion.StandardInOut)
                ) { it }
            ) {
                val shownId = detailCategoryId ?: detailLastCategoryId
                val shownCategory = customCategories.firstOrNull { it.id == shownId }
                if (shownCategory != null) {
                    val storedOrder = remember(shownCategory.id) {
                        goalOrderPrefs.getString("cat_${shownCategory.id}", null)
                            ?.split(',')?.mapNotNull { it.trim().toLongOrNull() }.orEmpty()
                    }
                    val goalOrder = goalOrders[shownCategory.id] ?: storedOrder
                    val sharedCategory = sharedCategoryCatalog.firstOrNull { it.isOwner && it.category.id == shownCategory.id && it.category.createdAt == shownCategory.createdAt }
                    val sharedMetadata = sharedCategory?.let { sharedCategoryDetails[it.id] }
                    val detailUi = remember(shownCategory, reminders, today, zone, goalOrder, ownerName, categoryPlannerRevision, sharedMetadata) {
                        plannerCategoryDetailUi(
                            shownCategory, reminders, today, zone, goalOrder,
                            memberNames = sharedMetadata?.members?.map { it.name } ?: listOfNotNull(ownerName.trim().takeIf { it.isNotEmpty() }),
                            context = context
                        ).copy(linkText = categoryPlannerPreferences.link(categoryPlannerKey(null, shownCategory)!!))
                    }
                    Box(
                        Modifier.fillMaxSize()
                            .onGloballyPositioned { detailOverlayRoot = it.positionInRoot() }
                            .graphicsLayer {
                                val from = revealOrigin.bounds
                                val p = revealProgress.value
                                if (from != null && p < 1f) {
                                    alpha = (p / 0.45f).coerceIn(0f, 1f)
                                    clip = true
                                    shape = plannerRevealShape(
                                        from.translate(-detailOverlayRoot.x, -detailOverlayRoot.y), p, 32.dp.toPx()
                                    )
                                }
                            }
                            .background(Color.Black)
                            // Swallow touches so nothing underneath (calendar, cards) reacts.
                            .pointerInput(Unit) { detectTapGestures { } }
                    ) {
                        PlannerCategoryDetailScreen(
                            ui = detailUi,
                            // Black screen; the tile, progress bar and checks wear the category's own colour.
                            categoryColorBackground = false,
                            selectedPastel = null,
                            onPickPastel = null,
                            visibleInCalendar = categoryPlannerPreferences.visible(categoryPlannerKey(null, shownCategory)),
                            onCalendarVisibilityChange = null,
                            onPasteLink = { value ->
                                scope.launch {
                                    try {
                                        val client = sharedCategoryApi
                                        if (client != null) {
                                            val item = client.ensure(shownCategory, value)
                                            if (item.link != value) client.link(item, value)
                                            val loaded = client.metadata(item.id)
                                            sharedCategoryDetails = sharedCategoryDetails + (item.id to loaded)
                                            sharedCategoryCatalog = sharedCategoryCatalog.filterNot { it.id == item.id } + loaded.item
                                        }
                                        categoryPlannerPreferences.setLink(categoryPlannerKey(null, shownCategory)!!, value)
                                    } catch (cancelled: CancellationException) { throw cancelled }
                                    catch (failure: Exception) { com.cagri.real.ui.components.RealToast.show(context, failure.message ?: "Link kaydedilemedi.", long = true) }
                                }
                            },
                            onAddMember = { manageCategory = shownCategory },
                            onSend = { manageCategory = shownCategory },
                            cardShape = categoryShapes[shownCategory.id] ?: PlannerCategoryCardShape.Square,
                            onCardShapeChange = { shape ->
                                categoryShapes = categoryShapes + (shownCategory.id to shape)
                                categoryShapePrefs.edit().putString("cat_${shownCategory.id}", shape.key).apply()
                            },
                            topPadding = statusTop,
                            reduceAnimations = reduceAnimations,
                            closing = detailCategoryId == null,
                            icon = { m, c -> CategoryIconGlyph(CategoryIcon.fromKey(shownCategory.iconKey), m, c) },
                            onBack = ::closeGoals,
                            onDone = ::closeGoals,
                            // Creating/editing lives in the category's own list screen.
                            onEditCategory = { onOpenCategory(null, shownCategory.id) },
                            onAddReminder = { onOpenCategory(null, shownCategory.id) },
                            onCreateGoal = { title -> proGoals { scope.launch { onCreateGoal(title, shownCategory.id) } } },
                            onStartNext = ::open,
                            onToggleGoal = onToggleReminder,
                            onReorderGoals = { ids ->
                                goalOrders = goalOrders + (shownCategory.id to ids)
                                goalOrderPrefs.edit().putString("cat_${shownCategory.id}", ids.joinToString(",")).apply()
                            },
                            onEditGoal = ::open,
                            onOpenGoal = ::open
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = openedReminderId,
                modifier = Modifier.fillMaxSize().zIndex(20f),
                transitionSpec = {
                    // Only the event/detail card transforms; the surrounding chrome fades.
                    (fadeIn(tween(if (reduceAnimations) 0 else 320)) togetherWith
                        fadeOut(tween(if (reduceAnimations) 0 else 320)))
                        .using(sizeTransform = null)
                },
                label = "plannerCardContainerDetail"
            ) { id ->
                if (id != null) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        detailContent(id, openedOccurrenceMillis) {
                            openedReminderId = null
                            openedOccurrenceMillis = null
                        }
                    }
                }
            }

            timeEditorReminder?.let { reminder ->
                val seed = reminder.alertAt ?: now.atZone(zone).plusHours(1).toInstant().toEpochMilli()
                AnimatedDateTimePickerOverlay(
                    visible = timeEditorId != null,
                    initial = seed,
                    initialRepeatRule = reminder.repeatRule,
                    initialTimeTab = true,
                    initialEarlyAlertMinutes = reminder.earlyAlertMinutes,
                    initialEndAt = reminder.endAt,
                    onConfirmTiming = { early, end -> setReminderTiming(reminder.id, early, end) },
                    backdrop = scene,
                    backdropPosition = { scenePosition },
                    designStyle = designStyle,
                    reduceDynamicBlur = reduceDynamicBlur,
                    reduceAnimations = reduceAnimations,
                    exactAlarmsAllowed = exactAlarmsAllowed,
                    onRequestExactAlarms = onRequestExactAlarms,
                    onDismiss = { timeEditorId = null },
                    onConfirm = { at, rule ->
                        if (details[reminder.id] == null) {
                            scope.launch {
                                try {
                                    details = repository.put(reminder.id, PlannerEventDetails())
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    error = "Plan süresi kaydedilemedi; saat yine de ayarlandı."
                                }
                            }
                        }
                        onSetReminderAlert(reminder.id, at, rule)
                        timeEditorId = null
                    }
                )
            }

            endTimeEditorReminder?.let { reminder ->
                val startAt = reminder.alertAt ?: reminder.createdAt
                val currentDur = details[reminder.id]?.durationMinutes
                val seed = reminder.endAt ?: (startAt + (currentDur ?: 60) * 60_000L)
                AnimatedDateTimePickerOverlay(
                    visible = endTimeEditorId != null,
                    initial = seed,
                    initialRepeatRule = RecurrenceRule.NONE,
                    backdrop = scene,
                    backdropPosition = { scenePosition },
                    designStyle = designStyle,
                    reduceDynamicBlur = reduceDynamicBlur,
                    reduceAnimations = reduceAnimations,
                    exactAlarmsAllowed = exactAlarmsAllowed,
                    onRequestExactAlarms = onRequestExactAlarms,
                    onDismiss = { endTimeEditorId = null },
                    onConfirm = { endAt, _ ->
                        // Bitiş: day and time; the duration (Süre) stays as set.
                        if (endAt > startAt) setReminderTiming(reminder.id, reminder.earlyAlertMinutes, endAt)
                        else error = "Bitiş, başlangıçtan sonra olmalı."
                        endTimeEditorId = null
                    }
                )
            }
        }
        } // CompositionLocalProvider
        } // SharedTransitionLayout
    }
}

@Composable
private fun PlannerInlineEventEditor(
    startTime: String,
    endTime: String,
    durationMinutes: Int?,
    designStyle: DesignStyle,
    onPickTime: () -> Unit,
    onPickEndTime: () -> Unit,
    onPickDuration: (Int) -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = 10.dp, end = 10.dp, bottom = 7.dp)
            .then(
                if (designStyle == DesignStyle.LiquidGlass) {
                    Modifier.liquidChrome(corners = 24.dp)
                } else {
                    Modifier.clip(shape)
                        .background(Color(0xFF1B1B1F))
                        .border(0.7.dp, Color.White.copy(alpha = 0.10f), shape)
                }
            )
            .padding(horizontal = 13.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.055f))
                    .border(0.6.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button, onClick = onPickTime)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("Başlangıç", color = Color.White.copy(alpha = 0.48f), fontSize = 8.sp)
                    Text(startTime, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.055f))
                    .border(0.6.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button, onClick = onPickEndTime)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("Bitiş", color = Color.White.copy(alpha = 0.48f), fontSize = 8.sp)
                    Text(endTime, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(30, 45, 60, 90, 120, 150, 180).forEach { minutes ->
                val selected = minutes == durationMinutes
                Box(
                    Modifier
                        .defaultMinSize(minWidth = 46.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.055f))
                        .border(
                            0.6.dp,
                            if (selected) Color.White.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.09f),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable(role = Role.Button) { onPickDuration(minutes) }
                        .padding(horizontal = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$minutes min",
                        color = if (selected) Color.Black else Color.White.copy(alpha = 0.76f),
                        fontSize = 9.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun PlannerQuick24Card(
    reminder: ReminderItem,
    details: PlannerEventDetails?,
    durationExpanded: Boolean,
    onOpen: () -> Unit,
    onPickTime: () -> Unit,
    onToggleDuration: () -> Unit,
    onPickDuration: (Int) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF121214)).padding(horizontal = 6.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(22.dp)).background(Color(0xFFB4BEC0))
            .clickable(role = Role.Button, onClick = onOpen).padding(18.dp)
    ) {
        Text(
            reminder.text,
            color = PlannerInk,
            fontSize = 21.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PlannerSchedulePill(
                label = "Saat",
                value = "Saat ekle",
                glyph = ActionGlyph.Calendar,
                onClick = onPickTime
            )
            PlannerSchedulePill(
                label = "Süre",
                value = details?.durationMinutes?.let { "${it} dk" } ?: "Süre ekle",
                glyph = ActionGlyph.Note,
                onClick = onToggleDuration
            )
        }
        AnimatedVisibility(
            visible = durationExpanded,
            enter = fadeIn(tween(150)) + expandVertically(tween(220)),
            exit = fadeOut(tween(100)) + shrinkVertically(tween(180))
        ) {
            PlannerDurationChoices(
                selected = details?.durationMinutes,
                dark = false,
                onPick = onPickDuration
            )
        }
    }
}

@Composable
private fun PlannerSchedulePill(
    label: String,
    value: String,
    glyph: ActionGlyph,
    onClick: () -> Unit
) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(PlannerInk.copy(alpha = 0.82f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        ActionIcon(glyph, Modifier.size(11.dp), Color.White.copy(alpha = 0.88f))
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(value, color = Color.White, fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Medium)
            Text(label, color = Color.White.copy(alpha = 0.52f), fontSize = 7.sp, lineHeight = 8.sp)
        }
    }
}

@Composable
private fun PlannerDurationChoices(
    selected: Int?,
    dark: Boolean,
    onPick: (Int) -> Unit
) {
    Row(
        Modifier
            .wrapContentWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        listOf(30, 45, 60, 90, 120, 150, 180).forEach { minutes ->
            val chosen = minutes == selected
            Box(
                Modifier.defaultMinSize(minWidth = 50.dp).clip(RoundedCornerShape(13.dp))
                    .background(
                        when {
                            chosen && dark -> Color.White
                            chosen -> PlannerInk
                            dark -> Color.White.copy(alpha = 0.08f)
                            else -> PlannerInk.copy(alpha = 0.12f)
                        }
                    )
                    .clickable(role = Role.Button) { onPick(minutes) }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$minutes min",
                    color = when {
                        chosen && dark -> Color.Black
                        chosen -> Color.White
                        dark -> Color.White.copy(alpha = 0.8f)
                        else -> PlannerInk.copy(alpha = 0.82f)
                    },
                    fontSize = 9.sp,
                    fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}


/**
 * Fixed-size glass pill for the planner chrome. Samples [scene] (the calendar content only), so it must
 * never wrap scrolling content: a glass layer as tall as a 6-week month exceeds the GPU texture limit.
 */
@Composable
private fun PlannerGlass(
    modifier: Modifier,
    corners: Dp,
    designStyle: DesignStyle,
    scene: androidx.compose.ui.graphics.layer.GraphicsLayer,
    scenePosition: () -> Offset,
    reduceDynamicBlur: Boolean,
    pressFeedback: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    if (designStyle == DesignStyle.LiquidGlass) {
        LiquidGlassSurface(
            backdrop = scene,
            backdropPosition = scenePosition,
            modifier = modifier,
            corners = corners,
            reduceDynamicBlur = reduceDynamicBlur,
            // The glass shrinks slightly while pressed; strips that must stay still opt out.
            reduceAnimations = if (pressFeedback) com.cagri.real.ui.components.LocalLiquidReduceMotion.current else true,
            content = content
        )
    } else {
        Box(modifier.plannerSafeGlass(corners), content = content)
    }
}

private fun Modifier.plannerSafeGlass(corners: Dp): Modifier {
    val shape = RoundedCornerShape(corners)
    return clip(shape)
        .background(Color(0x66131518))
        .border(0.8.dp, Color.White.copy(alpha = 0.16f), shape)
}

@Composable
private fun PlannerTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(42.dp).clip(RoundedCornerShape(26.dp))
            .background(if (selected) Color.White else Color.Black)
            .border(0.8.dp, if (selected) Color.White else Color(0xFF414143), RoundedCornerShape(26.dp))
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.Black else Color(0xFFD0D0D0), fontSize = 13.sp)
    }
}

@Composable
private fun PlannerEmpty(text: String) {
    Text(
        text,
        color = Color(0xFFAAAAAA),
        fontSize = 15.sp,
        modifier = Modifier.fillMaxWidth().background(Color(0xFF121214)).padding(24.dp)
    )
}
