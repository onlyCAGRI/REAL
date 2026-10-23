package com.cagri.real.ui.components.planner

import com.cagri.real.i18n.t
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import com.cagri.real.ui.components.liquidChrome
import com.cagri.real.ui.theme.RealType
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.rotate
import com.cagri.real.ui.connections.ConnectionAvatar
import com.cagri.real.data.CustomCategory
import com.cagri.real.data.ReminderItem
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import com.cagri.real.ui.components.ReminderColorChoices
import android.content.Context
import android.content.SharedPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/*
 * Category detail screen ("Planning" reference), AMOLED + category color.
 * Pure UI: everything comes in through PlannerCategoryDetailUi and callbacks, nothing is wired.
 *
 * Wiring sketch (when you are ready):
 *   PlannerCategoryDetailScreen(
 *       ui = PlannerCategoryDetailUi(
 *           name = category.name,
 *           dateLabel = "Saturday, October 3 · 2026",
 *           accent = plannerCategoryDetailAccent(category),
 *           goals = reminders.filter { it.customCategoryId == category.id }.map { PlannerGoalUi(...) },
 *           weekActivity = listOf(1, 0, 2, 3, 0, 1, 2), todayIndex = 5
 *       ),
 *       icon = { m, c -> CategoryIconGlyph(CategoryIcon.fromKey(category.iconKey), m, c) },
 *       onToggleGoal = { id -> ... }, onBack = { ... })
 */

data class PlannerGoalUi(
    val id: Long,
    val title: String,
    /** Small line above the title (e.g. category name, "Due Oct 12"). */
    val caption: String = "",
    val done: Boolean = false,
    /** Optional 0..1 partial progress for the goal that is in progress (fills the rail under it). */
    val progress: Float? = null,
    /** Tamamlama notu left when it was completed. */
    val note: String? = null
)

data class PlannerWeekActivityUi(
    val weekKey: String,
    val title: String,
    val doneCount: Int,
    val activity: List<Int>,
    val weekLabels: List<String> = listOf("M", "T", "W", "T", "F", "S", "S"),
    val todayIndex: Int = -1,
    val isCurrentWeek: Boolean = false
)

data class PlannerCategoryDetailUi(
    val categoryId: Long? = null,
    val name: String,
    val dateLabel: String,
    val accent: Color,
    val goals: List<PlannerGoalUi>,
    /** Completed items for Mon..Sun (7 values). */
    val weekActivity: List<Int> = List(7) { 0 },
    val weekLabels: List<String> = listOf("M", "T", "W", "T", "F", "S", "S"),
    val todayIndex: Int = -1,
    /** Chronological list of weeks (oldest first, current week last). */
    val weekHistory: List<PlannerWeekActivityUi> = emptyList(),
    /** Canonical REAL member display names; initials are drawn. */
    val members: List<String> = emptyList(),
    /** Same people with their uid and shared picture (Manage avatars); falls back to [members] names when empty. */
    val people: List<com.cagri.real.sharedcategories.SharedCategoryMember> = emptyList(),
    /** Text shown on the link page of the swipe card. */
    val linkText: String = t("link yazılacak")
)

object PlannerWeekHistoryStore {
    const val PREFS_NAME = "real_planner_week_history_v1"

    fun formatWeekTitle(monday: LocalDate, currentMonday: LocalDate): String {
        return when {
            monday == currentMonday -> "This week"
            monday == currentMonday.minusWeeks(1) -> "Last week"
            else -> {
                val sunday = monday.plusDays(6)
                if (monday.year != sunday.year) {
                    val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
                    "${monday.format(fmt)} – ${sunday.format(fmt)}"
                } else if (monday.monthValue != sunday.monthValue) {
                    val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
                    "${monday.format(fmt)} – ${sunday.format(fmt)}"
                } else {
                    val monthFmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
                    "${monday.format(monthFmt)} – ${sunday.dayOfMonth}"
                }
            }
        }
    }

    fun loadOrCreateWeekHistory(
        context: Context?,
        category: CustomCategory,
        members: List<ReminderItem>,
        today: LocalDate,
        zone: ZoneId,
        customPrefs: SharedPreferences? = null
    ): List<PlannerWeekActivityUi> {
        val currentMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val prefs = customPrefs ?: runCatching { context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }.getOrNull()

        // Read saved week keys if available
        val savedWeeksRaw = prefs?.getString("cat_${category.id}_weeks", null)
        val savedWeekKeys = if (!savedWeeksRaw.isNullOrBlank()) {
            savedWeeksRaw.split(",").filter { it.isNotBlank() }.toMutableSet()
        } else {
            prefs?.all?.keys
                ?.filter { it.startsWith("cat_${category.id}_week_") }
                ?.map { it.removePrefix("cat_${category.id}_week_") }
                ?.toMutableSet() ?: mutableSetOf()
        }

        // Determine earliest Monday
        val pastCompletionMondays = members.mapNotNull { r ->
            r.completedAt?.takeIf { it > 0L }?.let {
                val date = Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                date.minusDays((date.dayOfWeek.value - 1).toLong())
            }
        }
        val pastCreatedMondays = members.mapNotNull { r ->
            r.createdAt.takeIf { it > 0L }?.let {
                val date = Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                date.minusDays((date.dayOfWeek.value - 1).toLong())
            }
        }
        val categoryCreatedMonday = category.createdAt.takeIf { it > 0L }?.let {
            val date = Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
            date.minusDays((date.dayOfWeek.value - 1).toLong())
        }
        val storedStartMonday = prefs?.getString("cat_${category.id}_start_week", null)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        val savedMondays = savedWeekKeys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }

        val defaultStartMonday = currentMonday.minusWeeks(2)
        val allCandidateMondays = (pastCompletionMondays + pastCreatedMondays +
                listOfNotNull(categoryCreatedMonday, storedStartMonday) + savedMondays + listOf(defaultStartMonday))
            .filter { !it.isAfter(currentMonday) }

        val earliestMonday = allCandidateMondays.minOrNull() ?: defaultStartMonday

        // Store start week permanently
        prefs?.edit()?.putString("cat_${category.id}_start_week", earliestMonday.toString())?.apply()

        // Generate all weeks from earliestMonday up to currentMonday (no future weeks, no gaps)
        val allMondays = generateSequence(earliestMonday) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(currentMonday) }
            .toList()

        val history = allMondays.map { monday ->
            val weekKey = monday.toString()
            val isCurrent = (monday == currentMonday)
            val activity: List<Int>

            if (isCurrent) {
                // Live current week completions
                activity = (0..6).map { offset ->
                    val day = monday.plusDays(offset.toLong())
                    members.count { r ->
                        r.completedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == day } == true
                    }
                }
                // Persist current week so it is ready to be frozen when next week arrives
                prefs?.edit()?.putString("cat_${category.id}_week_$weekKey", activity.joinToString(","))?.apply()
            } else {
                // Past week: load frozen history if available
                val saved = prefs?.getString("cat_${category.id}_week_$weekKey", null)
                if (saved != null) {
                    activity = saved.split(",").mapNotNull { it.trim().toIntOrNull() }.take(7).let {
                        if (it.size == 7) it else it + List(7 - it.size) { 0 }
                    }
                } else {
                    // First run for this past week: compute once and freeze permanently
                    activity = (0..6).map { offset ->
                        val day = monday.plusDays(offset.toLong())
                        members.count { r ->
                            r.completedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == day } == true
                        }
                    }
                    prefs?.edit()?.putString("cat_${category.id}_week_$weekKey", activity.joinToString(","))?.apply()
                }
            }

            val title = formatWeekTitle(monday, currentMonday)
            val todayIndex = if (isCurrent) (today.dayOfWeek.value - 1) else -1
            PlannerWeekActivityUi(
                weekKey = weekKey,
                title = title,
                doneCount = activity.sum(),
                activity = activity,
                weekLabels = listOf("M", "T", "W", "T", "F", "S", "S"),
                todayIndex = todayIndex,
                isCurrentWeek = isCurrent
            )
        }

        // Update list of all week keys
        prefs?.edit()?.putString("cat_${category.id}_weeks", allMondays.joinToString(",") { it.toString() })?.apply()

        return history
    }
}

/** The category's own colour (as on its home card and in the monthly calendar). */
fun plannerCategoryDetailAccent(category: CustomCategory): Color =
    category.customSolidColorArgb?.let { Color(it) }
        ?: ReminderColorChoices.getOrNull(category.accentStart)?.color
        ?: Color(0xFF4558C8)


/**
 * Builds the screen model from the real data: every non-journal/non-mood reminder of the category,
 * oldest first (the same order the category list uses), plus Mon..Sun completion counts.
 */
internal fun plannerCategoryDetailUi(
    category: CustomCategory,
    reminders: List<ReminderItem>,
    today: LocalDate,
    zone: ZoneId,
    /** User-chosen goal order (ids). Goals not listed (new ones) follow, oldest first. */
    order: List<Long> = emptyList(),
    memberNames: List<String> = emptyList(),
    people: List<com.cagri.real.sharedcategories.SharedCategoryMember> = emptyList(),
    context: Context? = null
): PlannerCategoryDetailUi {
    val position = order.withIndex().associate { it.value to it.index }
    val members = reminders
        .filter { it.customCategoryId == category.id && it.journal == null && it.mood == null }
        .sortedWith(
            compareBy<ReminderItem> { position[it.id] ?: Int.MAX_VALUE }
                .thenBy { it.createdAt }.thenBy { it.id }
        )
    val dueFormat = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    val goals = members.map { r ->
        val due = r.alertAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            ?: r.plannedDateEpochDay?.let { LocalDate.ofEpochDay(it) }
        val items = if (r.isChecklist) r.checklistItems.filter { it.text.isNotBlank() } else emptyList()
        val checked = items.count { it.isCompleted }
        val caption = listOfNotNull(
            due?.let { "Due ${it.format(dueFormat)}" },
            if (items.isNotEmpty()) "$checked/${items.size}" else null
        ).joinToString("  ·  ")
        PlannerGoalUi(
            id = r.id,
            title = r.text.trim().ifBlank { t("Untitled") },
            caption = caption,
            done = r.isCompleted,
            progress = if (!r.isCompleted && items.isNotEmpty() && checked > 0) checked / items.size.toFloat() else null,
            note = r.completionNote
        )
    }
    val weekHistory = PlannerWeekHistoryStore.loadOrCreateWeekHistory(context, category, members, today, zone)
    val currentWeekActivity = weekHistory.lastOrNull { it.isCurrentWeek }?.activity
        ?: weekHistory.lastOrNull()?.activity
        ?: List(7) { 0 }

    return PlannerCategoryDetailUi(
        categoryId = category.id,
        name = category.name,
        dateLabel = today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d  ·  yyyy", Locale.ENGLISH)),
        accent = plannerCategoryDetailAccent(category),
        goals = goals,
        weekActivity = currentWeekActivity,
        todayIndex = today.dayOfWeek.value - 1,
        weekHistory = weekHistory,
        members = memberNames,
        people = people
    )
}

private val DetailSheet = Color(0xFF121216)
private val DetailMuted = Color(0xFF8C8C95)
private val DetailFaint = Color(0xFF55555D)
private val DetailLine = Color(0xFF2A2A31)
private val DetailBlack = Color(0xFF0B0B0D)

/** Rounded tile with a concave notch in the top-right corner (the pencil badge sits in it). */
private object NotchedTileShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val corner = with(density) { 28.dp.toPx() }
        val radius = with(density) { 31.dp.toPx() }
        val inset = with(density) { 24.dp.toPx() }
        val body = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner, corner)))
        }
        val hole = Path().apply { addOval(Rect(Offset(size.width - inset, inset), radius)) }
        return Outline.Generic(Path.combine(PathOperation.Difference, body, hole))
    }
}

// ---------- tiny canvas glyphs (no dependency on the icon set) ----------

@Composable
private fun CheckMark(color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val sw = w * 0.12f
    drawLine(color, Offset(w * 0.2f, h * 0.54f), Offset(w * 0.42f, h * 0.76f), sw, StrokeCap.Round)
    drawLine(color, Offset(w * 0.42f, h * 0.76f), Offset(w * 0.8f, h * 0.28f), sw, StrokeCap.Round)
}

@Composable
private fun LinkMark(color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
    val w = size.width
    val sw = w * 0.11f
    val r = w * 0.17f
    rotate(-45f, Offset(w / 2f, w / 2f)) {
        drawRoundRect(color, Offset(w * 0.04f, w * 0.34f), Size(w * 0.52f, w * 0.32f), CornerRadius(r), style = Stroke(sw, cap = StrokeCap.Round))
        drawRoundRect(color, Offset(w * 0.44f, w * 0.34f), Size(w * 0.52f, w * 0.32f), CornerRadius(r), style = Stroke(sw, cap = StrokeCap.Round))
    }
}

private fun plannerLinkHost(value: String): String =
    runCatching { android.net.Uri.parse(value).host?.removePrefix("www.") }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: value

@Composable
private fun PlusMark(color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
    val w = size.width
    val sw = w * 0.09f
    drawLine(color, Offset(w * 0.5f, w * 0.12f), Offset(w * 0.5f, w * 0.88f), sw, StrokeCap.Round)
    drawLine(color, Offset(w * 0.12f, w * 0.5f), Offset(w * 0.88f, w * 0.5f), sw, StrokeCap.Round)
}

@Composable
private fun PencilMark(color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
    val w = size.width
    drawLine(color, Offset(w * 0.30f, w * 0.70f), Offset(w * 0.74f, w * 0.26f), w * 0.15f, StrokeCap.Round)
    drawLine(color, Offset(w * 0.20f, w * 0.82f), Offset(w * 0.30f, w * 0.70f), w * 0.07f, StrokeCap.Round)
}

@Composable
private fun DotsMark(color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
    val r = size.height * 0.16f
    drawCircle(color, r, Offset(size.width * 0.28f, size.height * 0.5f))
    drawCircle(color, r, Offset(size.width * 0.72f, size.height * 0.5f))
}

@Composable
private fun RoundIconButton(
    size: Dp, border: Color, description: String, onClick: () -> Unit, content: @Composable () -> Unit
) {
    Box(
        Modifier.size(size).clip(CircleShape).border(1.dp, border, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) { content() }
}

// ---------- screen ----------

@Composable
fun PlannerCategoryDetailScreen(
    ui: PlannerCategoryDetailUi,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    reduceAnimations: Boolean = false,
    readOnly: Boolean = false,
    icon: @Composable (Modifier, Color) -> Unit = { m, c -> ActionIcon(ActionGlyph.Star, m, c) },
    onBack: () -> Unit = {},
    onDone: () -> Unit = {},
    onEditCategory: () -> Unit = {},
    onStartNext: (Long) -> Unit = {},
    onAddReminder: () -> Unit = {},
    /** Opens nothing outside this screen; kept so existing callers still compile. */
    onAddGoal: () -> Unit = {},
    /** Called with the typed title when the in-screen "Add to REAL" window submits a new goal. */
    onCreateGoal: (String) -> Unit = {},
    onToggleGoal: (Long) -> Unit = {},
    onEditGoal: (Long) -> Unit = {},
    onOpenGoal: (Long) -> Unit = {},
    onShareLink: () -> Unit = {},
    onPasteLink: ((String) -> Unit)? = null,
    visibleInCalendar: Boolean = false,
    onCalendarVisibilityChange: ((Boolean) -> Unit)? = null,
    onAddMember: () -> Unit = {},
    onSend: () -> Unit = {},
    /** Called once when a drag-reorder of the goals ends, with the new full id order. */
    onReorderGoals: (List<Long>) -> Unit = {},
    /** Tamamlama notu: saved on the completed goal (null clears it). */
    onSetCompletionNote: (Long, String?) -> Unit = { _, _ -> },
    categoryColorBackground: Boolean = false,
    /** Pastel currently used by this category (one of RealCalendarPastels). Null hides the color row. */
    selectedPastel: Color? = null,
    onPickPastel: ((Color) -> Unit)? = null,
    /** Square / rectangle card in the Focus carousel. Null hides the shape row. */
    cardShape: PlannerCategoryCardShape? = null,
    onCardShapeChange: ((PlannerCategoryCardShape) -> Unit)? = null,
    /** True while the screen is being dismissed: the entrance plays backwards. */
    closing: Boolean = false
) {
    val contextForLink = androidx.compose.ui.platform.LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var showPasteLink by remember { mutableStateOf(false) }
    if (showPasteLink && onPasteLink != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showPasteLink = false }) {
            val sheetShape = RoundedCornerShape(30.dp)
            Column(
                Modifier.fillMaxWidth().clip(sheetShape).background(Color(0xFF111115))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), sheetShape).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier.size(58.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF6FD6FF).copy(alpha = 0.38f), Color(0xFF6FD6FF).copy(alpha = 0.12f))))
                        .border(1.dp, Color(0xFF6FD6FF).copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { LinkMark(Color.White, Modifier.size(26.dp)) }
                Text(t("Kategori linki"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    t("Meet, Zoom veya başka bir web bağlantısını kopyala; klavye açmadan buradan yapıştır."),
                    color = Color(0xFF9A9AA2), fontSize = 14.sp, lineHeight = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(Color.White)
                        .clickable(role = Role.Button) {
                            val clipboard = contextForLink.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val value = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(contextForLink)?.toString().orEmpty()
                            val link = com.cagri.real.ui.components.validPlannerLink(value)
                            if (link == null) com.cagri.real.ui.components.RealToast.show(contextForLink, t("Geçerli bir web bağlantısı kopyala."))
                            else { onPasteLink(link); showPasteLink = false }
                        },
                    contentAlignment = Alignment.Center
                ) { Text(t("Panodan yapıştır"), color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(CircleShape)
                        .clickable(role = Role.Button) { showPasteLink = false },
                    contentAlignment = Alignment.Center
                ) { Text(t("Vazgeç"), color = Color(0xFF9A9AA2), fontSize = 15.sp) }
            }
        }
    }
    val accent = ui.accent
    val headerInk = if (categoryColorBackground) {
        if (accent.luminance() > 0.45f) PlannerInk else Color.White
    } else Color.White
    val headerMuted = if (categoryColorBackground) headerInk.copy(alpha = 0.6f) else DetailMuted
    val headerLine = if (categoryColorBackground) headerInk.copy(alpha = 0.25f) else DetailLine
    val headerAccent = if (categoryColorBackground) headerInk else accent
    val tileBackground = if (categoryColorBackground) DetailBlack else accent
    // On the category colour (real colour now, not a pastel): dark ink on light colours, white on dark ones.
    val onAccent = if (accent.luminance() > 0.45f) PlannerInk else Color.White
    val tileInk = if (categoryColorBackground) Color.White else onAccent
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view, categoryColorBackground, accent) {
        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val previousLightStatus = controller?.isAppearanceLightStatusBars
        if (categoryColorBackground) controller?.isAppearanceLightStatusBars = accent.luminance() > 0.45f
        onDispose {
            if (previousLightStatus != null) controller?.isAppearanceLightStatusBars = previousLightStatus
        }
    }
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    // While a drag is running the order lives here; null = show ui.goals as given.
    var dragOrder by remember { mutableStateOf<List<Long>?>(null) }
    // Reordering only works while this mode is on (pencil on the progress tile -> check to finish).
    var reorderMode by remember { mutableStateOf(false) }
    var stylePopupOpen by remember { mutableStateOf(false) }
    // The "Add to REAL" window for a new goal opens on this very screen.
    var composing by remember { mutableStateOf(false) }
    BackHandler(enabled = composing) { composing = false }
    BackHandler(enabled = reorderMode && !composing) { reorderMode = false }
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    // After a swap the list needs a frame or two to re-measure; deciding again earlier would use stale offsets.
    var awaitingLayout by remember { mutableStateOf(false) }
    LaunchedEffect(dragOrder) {
        if (dragOrder != null && awaitingLayout) {
            withFrameNanos { }
            withFrameNanos { }
            awaitingLayout = false
        }
    }
    val goals = remember(ui.goals, dragOrder) {
        val live = dragOrder
        if (live == null) ui.goals else {
            val byId = ui.goals.associateBy { it.id }
            live.mapNotNull { byId[it] } + ui.goals.filter { it.id !in live }
        }
    }
    val total = goals.size
    val doneCount = goals.count { it.done }
    val activeIndex = remember(goals) { goals.indexOfFirst { !it.done } }
    val nextGoal = goals.getOrNull(activeIndex)
    val pagerState = rememberPagerState { 3 }
    val overall by animateFloatAsState(
        if (total == 0) 0f else doneCount / total.toFloat(),
        animationSpec = if (reduceAnimations) snap() else tween(700), label = "detail-overall"
    )

    Box(modifier.fillMaxSize().background(if (categoryColorBackground) accent else Color.Black)) {
        // New retains its glow; New+ uses the category color as a solid surface.
        if (!categoryColorBackground) Box(
            Modifier.fillMaxWidth().height(480.dp).background(
                Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent))
            )
        )
        // Moves the dragged goal past neighbours whose centre its edge has crossed.
        fun settleDrag() {
            if (awaitingLayout) return
            val id = dragId ?: return
            val order = dragOrder ?: return
            val infos = listState.layoutInfo.visibleItemsInfo
            val me = infos.firstOrNull { it.key == "goal:$id" } ?: return
            val top = me.offset + dragDelta
            val bottom = top + me.size
            fun isGoal(key: Any) = (key as? String)?.startsWith("goal:") == true
            val down = infos.lastOrNull { isGoal(it.key) && it.offset > me.offset && bottom > it.offset + it.size / 2f }
            val up = infos.firstOrNull { isGoal(it.key) && it.offset < me.offset && top < it.offset + it.size / 2f }
            val target = down ?: up ?: return
            val targetId = (target.key as String).removePrefix("goal:").toLongOrNull() ?: return
            val from = order.indexOf(id)
            val to = order.indexOf(targetId)
            if (from < 0 || to < 0 || from == to) return
            val moved = order.toMutableList().also { it.removeAt(from); it.add(to, id) }
            val newTop = if (down != null) target.offset + target.size - me.size else target.offset
            dragDelta -= (newTop - me.offset)
            awaitingLayout = true
            dragOrder = moved
        }
        // Edge auto-scroll while the finger holds a goal near the top or bottom of the list.
        LaunchedEffect(dragId) {
            while (dragId != null) {
                val info = listState.layoutInfo
                val me = info.visibleItemsInfo.firstOrNull { it.key == "goal:${dragId}" }
                if (me != null) {
                    val center = me.offset + me.size / 2f + dragDelta
                    val speed = when {
                        center > info.viewportEndOffset - 150f -> 16f
                        center < info.viewportStartOffset + 260f -> -16f
                        else -> 0f
                    }
                    if (speed != 0f) {
                        dragDelta += listState.scrollBy(speed)
                        settleDrag()
                    }
                }
                delay(16)
            }
        }
        // Entrance choreography (motion only). Starts a moment after the card has grown into the screen.
        val intro = rememberPlannerIntro(reduceAnimations, delayMs = 140L)
        LaunchedEffect(closing) { if (closing && !reduceAnimations) intro.reverse() }
        LazyColumn(
            Modifier.fillMaxSize(), state = listState,
            contentPadding = PaddingValues(top = topPadding + 12.dp)
        ) {
            item("bar") {
                Row(
                    Modifier.plannerIntro(intro, 0, 360).fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.cagri.real.ui.components.RealBackButton(onBack, tint = headerInk)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Done", color = headerInk, fontSize = 15.sp,
                        modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onDone)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
            item("title") {
                Column(Modifier.plannerIntro(intro, 60, 560, slideY = 14.dp).padding(start = 24.dp, end = 24.dp, top = 26.dp)) {
                    val titleStyle = remember(ui.name) { RealType.responsiveEditorialTitle(ui.name) }
                    Text(
                        ui.name,
                        style = titleStyle,
                        color = if (categoryColorBackground) headerInk else titleStyle.color,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        ui.dateLabel,
                        style = RealType.editorialSubtitle.copy(color = headerMuted),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            item("tiles") {
              Column {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 26.dp).height(120.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Progress tile (category color, notched corner with the edit badge)
                    Box(Modifier.weight(1f).fillMaxHeight()
                        .plannerIntro(intro, 220, 520, fromScale = 0.6f, easing = PlannerPopEase)
                        .background(tileBackground, NotchedTileShape)) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(5.dp).size(30.dp).clip(CircleShape)
                                // The badge sits in the tile's notch, on the screen background.
                                .border(1.dp, (if (categoryColorBackground) headerInk else Color.White).copy(alpha = 0.45f), CircleShape)
                                .background(if (reorderMode) (if (categoryColorBackground) PlannerInk else Color.White) else Color.Transparent, CircleShape)
                                .clickable(enabled = !readOnly, role = Role.Button) {
                                    // Düzenle only reorders the goals; the badge then becomes the "done" check.
                                    reorderMode = !reorderMode
                                }
                                .semantics {
                                    contentDescription = if (reorderMode) t("Sıralamayı bitir") else t("Hedefleri sırala")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (reorderMode) CheckMark(accent, Modifier.size(18.dp))
                            else PencilMark(if (categoryColorBackground) headerInk else Color.White, Modifier.size(13.dp))
                            if (stylePopupOpen) CategoryStylePopup(
                                selectedPastel = selectedPastel, onPickPastel = onPickPastel,
                                cardShape = cardShape, onCardShapeChange = onCardShapeChange,
                                reduceAnimations = reduceAnimations,
                                visibleInCalendar = visibleInCalendar,
                                onCalendarVisibilityChange = onCalendarVisibilityChange,
                                onReorder = { stylePopupOpen = false; reorderMode = true },
                                onDismiss = { stylePopupOpen = false }
                            )
                        }
                        Box(
                            Modifier.align(Alignment.TopStart).padding(14.dp).size(34.dp).clip(CircleShape)
                                .background(DetailBlack),
                            contentAlignment = Alignment.Center
                        ) { icon(Modifier.size(15.dp), Color.White) }
                        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 12.dp)) {
                            Text(
                                "$doneCount/$total", color = tileInk, fontSize = 34.sp, lineHeight = 36.sp,
                                letterSpacing = (-1.2).sp
                            )
                            Text("goals done", color = tileInk.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }
                    // Swipe card: Next up + Start  ->  Link + Share link  ->  Members + Send
                    PlannerSwipeCard(
                        modifier = Modifier.weight(1.9f).fillMaxHeight()
                            .plannerIntro(intro, 300, 520, fromScale = 0.6f, easing = PlannerPopEase),
                        pagerState = pagerState, accent = headerAccent,
                        ink = headerInk, muted = headerMuted, line = headerLine,
                        nextTitle = nextGoal?.title, canStart = nextGoal != null,
                        onStart = { nextGoal?.let { onStartNext(it.id) } },
                        linkText = ui.linkText,
                        onShareLink = {
                            val link = com.cagri.real.ui.components.validPlannerLink(ui.linkText)
                            if (link != null) runCatching { uriHandler.openUri(link) }
                                .onFailure { com.cagri.real.ui.components.RealToast.show(contextForLink, t("Bağlantı açılamadı.")) }
                            else if (onPasteLink != null) showPasteLink = true else onShareLink()
                        },
                        onEditLink = if (onPasteLink != null) ({ showPasteLink = true }) else null,
                        members = ui.members, people = ui.people, onAddMember = onAddMember, onSend = onSend
                    )
                }
                // Page dots under the swipe card (aligned with it, not with the progress tile).
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.weight(1.9f), contentAlignment = Alignment.Center) {
                        PagerDots(pagerState.currentPage, 3, headerAccent, reduceAnimations)
                    }
                }
              }
            }
            item("week") {
                val weeks = ui.weekHistory.ifEmpty {
                    val currentDone = ui.weekActivity.sum()
                    listOf(
                        PlannerWeekActivityUi(
                            weekKey = "prev_2",
                            title = t("2 weeks ago"),
                            doneCount = 0,
                            activity = List(7) { 0 },
                            weekLabels = ui.weekLabels,
                            todayIndex = -1,
                            isCurrentWeek = false
                        ),
                        PlannerWeekActivityUi(
                            weekKey = "prev_1",
                            title = t("Last week"),
                            doneCount = 0,
                            activity = List(7) { 0 },
                            weekLabels = ui.weekLabels,
                            todayIndex = -1,
                            isCurrentWeek = false
                        ),
                        PlannerWeekActivityUi(
                            weekKey = "current",
                            title = t("This week"),
                            doneCount = currentDone,
                            activity = ui.weekActivity,
                            weekLabels = ui.weekLabels,
                            todayIndex = ui.todayIndex,
                            isCurrentWeek = true
                        )
                    )
                }
                val initialWeekPage = remember(weeks.size) { (weeks.size - 1).coerceAtLeast(0) }
                val weekPagerState = key(ui.categoryId ?: ui.name) {
                    rememberPagerState(initialPage = initialWeekPage) { weeks.size }
                }

                Column(Modifier.plannerIntro(intro, 380, 500, slideY = 16.dp).padding(start = 20.dp, end = 20.dp, top = 30.dp)) {
                    HorizontalPager(
                        state = weekPagerState,
                        key = { page -> weeks[page].weekKey },
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val week = weeks[page]
                        Column(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    week.title,
                                    style = RealType.editorialSection.copy(color = headerInk),
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${week.doneCount} done", color = headerMuted, fontSize = 13.sp)
                            }
                            WeekActivity(week, headerAccent, reduceAnimations, headerInk, headerMuted, headerLine)
                        }
                    }
                    if (!readOnly) Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp).clip(RoundedCornerShape(26.dp))
                            .border(1.dp, headerLine, RoundedCornerShape(26.dp))
                            .clickable(role = Role.Button, onClick = onAddReminder)
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Add a reminder to ${ui.name}", color = headerInk, fontSize = 14.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        ActionIcon(ActionGlyph.ChevronRight, Modifier.size(14.dp), headerMuted)
                    }
                }
            }

            // ---------- goals sheet ----------
            item("sheet-top") {
                Column(
                    Modifier.plannerIntro(intro, 200, 600, slideY = 140.dp, fade = false)
                        .fillMaxWidth().padding(top = 30.dp)
                        .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                        .background(DetailSheet).padding(start = 24.dp, end = 20.dp, top = 26.dp, bottom = 22.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Goals (${total})",
                            style = RealType.editorialSection.copy(color = Color.White),
                            modifier = Modifier.weight(1f)
                        )
                        if (!readOnly) RoundIconButton(40.dp, DetailFaint, t("Add goal"), { composing = true; onAddGoal() }) {
                            PlusMark(Color.White, Modifier.size(16.dp))
                        }
                    }
                    Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.weight(1f).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.09f))
                        ) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(overall.coerceIn(0f, 1f)).clip(CircleShape)
                                .background(accent))
                        }
                        Text("${(overall * 100).toInt()}%", color = DetailMuted, fontSize = 12.sp,
                            modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
            itemsIndexed(goals, key = { _, g -> "goal:${g.id}" }) { index, goal ->
                val dragging = dragId == goal.id
                PlannerGoalRow(
                    number = index + 1, goal = goal, active = index == activeIndex,
                    isLast = index == goals.lastIndex, accent = accent, reduce = reduceAnimations,
                    dragging = dragging, dragOffset = { if (dragging) dragDelta else 0f },
                    reorderMode = reorderMode,
                    modifier = (if (dragging || reduceAnimations) Modifier else Modifier.animateItem())
                        .plannerIntro(intro, 200, 600, slideY = 140.dp, fade = false)
                        .plannerIntro(intro, 520 + minOf(index, 6) * 55, 380, slideY = 10.dp),
                    readOnly = readOnly,
                    onToggle = { onToggleGoal(goal.id) }, onEdit = { onEditGoal(goal.id) },
                    onSaveNote = { text -> onSetCompletionNote(goal.id, text) },
                    onOpen = { onOpenGoal(goal.id) },
                    onDragStart = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        dragOrder = goals.map { it.id }
                        dragDelta = 0f
                        dragId = goal.id
                    },
                    onDrag = { dy -> dragDelta += dy; settleDrag() },
                    onDragEnd = {
                        val finished = dragOrder
                        dragId = null
                        dragDelta = 0f
                        if (finished != null && finished != ui.goals.map { it.id }) onReorderGoals(finished)
                        dragOrder = null
                    }
                )
            }
            if (!readOnly) item("add-goal") {
                Box(Modifier.plannerIntro(intro, 200, 600, slideY = 140.dp, fade = false)
                    .fillMaxWidth().background(DetailSheet).padding(start = 24.dp, end = 20.dp, top = 6.dp, bottom = 8.dp)) {
                    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 12f)) }
                    Row(
                        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(24.dp))
                            .clickable(role = Role.Button) { composing = true; onAddGoal() }
                            .drawBehind {
                                drawRoundRect(
                                    Color(0xFF3A3A42), cornerRadius = CornerRadius(24.dp.toPx()),
                                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = dash)
                                )
                            },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlusMark(accent, Modifier.size(15.dp))
                        Text(t("Add goal"), color = accent, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
            item("sheet-bottom") {
                Box(Modifier.plannerIntro(intro, 200, 600, slideY = 140.dp, fade = false)
                    .fillMaxWidth().height(140.dp).background(DetailSheet))
            }
        }
        if (composing) {
            GoalComposerWindow(
                accent = accent, categoryName = ui.name,
                onDismiss = { composing = false },
                onSubmit = { title -> composing = false; onCreateGoal(title) }
            )
        }
    }
}

/** "Add to REAL" window shown over the category screen: dim scrim, glass input pill above the keyboard. */
@Composable
private fun BoxScope.GoalComposerWindow(
    accent: Color, categoryName: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    fun submit() {
        val title = text.trim()
        if (title.isNotEmpty()) { keyboard?.hide(); onSubmit(title) }
    }
    Box(
        Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                keyboard?.hide(); onDismiss()
            }
    )
    val pill = RoundedCornerShape(30.dp)
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .background(Color(0xFF121216), pill)
            .liquidChrome(corners = 30.dp)
            .padding(start = 20.dp, end = 10.dp, top = 12.dp, bottom = 10.dp)
    ) {
        Text("New goal in ${categoryName}", color = DetailMuted, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) Text(t("Add to REAL"), color = Color(0xFF7C7C85), fontSize = 18.sp)
                BasicTextField(
                    value = text, onValueChange = { text = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(if (text.isBlank()) Color.White.copy(alpha = 0.10f) else accent)
                    .clickable(enabled = text.isNotBlank(), role = Role.Button) { submit() }
                    .semantics { contentDescription = t("Add goal") },
                contentAlignment = Alignment.Center
            ) {
                PlusMark(if (text.isBlank()) Color(0xFF7C7C85) else PlannerInk, Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun WeekActivity(
    week: PlannerWeekActivityUi,
    accent: Color,
    reduce: Boolean,
    ink: Color = Color.White,
    muted: Color = DetailMuted,
    line: Color = DetailLine
) {
    val maxValue = max(1, week.activity.maxOrNull() ?: 1)
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp).height(104.dp).clip(RoundedCornerShape(28.dp))
            .border(1.dp, line, RoundedCornerShape(28.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom
    ) {
        week.activity.take(7).forEachIndexed { i, value ->
            val isToday = i == week.todayIndex
            val barHeight by animateFloatAsState(
                6f + 40f * value / maxValue.toFloat(),
                animationSpec = if (reduce) snap() else tween(500), label = "week-bar"
            )
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.fillMaxWidth(0.62f).height(barHeight.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (value == 0) ink.copy(alpha = 0.08f) else accent.copy(alpha = if (isToday) 1f else 0.55f))
                )
                Text(
                    week.weekLabels.getOrElse(i) { "" }, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp),
                    color = if (isToday) ink else muted
                )
            }
        }
    }
}

@Composable
private fun WeekActivity(
    ui: PlannerCategoryDetailUi,
    accent: Color,
    reduce: Boolean,
    ink: Color = Color.White,
    muted: Color = DetailMuted,
    line: Color = DetailLine
) {
    val fallback = ui.weekHistory.lastOrNull() ?: PlannerWeekActivityUi(
        weekKey = "current",
        title = t("This week"),
        doneCount = ui.weekActivity.sum(),
        activity = ui.weekActivity,
        weekLabels = ui.weekLabels,
        todayIndex = ui.todayIndex,
        isCurrentWeek = true
    )
    WeekActivity(fallback, accent, reduce, ink, muted, line)
}

// ---------- swipe card (Next up / Link / Members) ----------

@Composable
private fun PagerActionButton(
    label: String, accent: Color, enabled: Boolean = true, onClick: () -> Unit,
    glyph: ActionGlyph, ink: Color = Color.White
) {
    Column(
        Modifier.width(88.dp).fillMaxHeight().padding(6.dp).clip(RoundedCornerShape(22.dp))
            .border(1.dp, accent.copy(alpha = if (enabled) 0.8f else 0.3f), RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (enabled) accent else accent.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) { ActionIcon(glyph, Modifier.size(14.dp), if (accent.luminance() > 0.45f) PlannerInk else Color.White) }
        Text(label, color = ink, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlannerSwipeCard(
    modifier: Modifier,
    pagerState: androidx.compose.foundation.pager.PagerState,
    accent: Color,
    nextTitle: String?, canStart: Boolean, onStart: () -> Unit,
    linkText: String, onShareLink: () -> Unit, onEditLink: (() -> Unit)? = null,
    members: List<String>, onAddMember: () -> Unit, onSend: () -> Unit,
    people: List<com.cagri.real.sharedcategories.SharedCategoryMember> = emptyList(),
    ink: Color = Color.White, muted: Color = DetailMuted, line: Color = DetailLine
) {
    val shape = RoundedCornerShape(28.dp)
    HorizontalPager(
        state = pagerState,
        modifier = modifier.clip(shape).border(1.dp, line, shape)
    ) { page ->
        Row(Modifier.fillMaxSize()) {
            when (page) {
                0 -> {
                    Column(Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 6.dp)) {
                        Text("Next up", color = muted, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        Text(
                            nextTitle ?: "Everything is done", color = ink, fontSize = 15.sp,
                            lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis
                        )
                    }
                    PagerActionButton("Start", accent, canStart, onStart, ActionGlyph.ChevronRight, ink)
                }
                1 -> {
                    Column(Modifier.weight(1f).fillMaxHeight().padding(start = 18.dp, top = 14.dp, bottom = 14.dp, end = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinkMark(muted, Modifier.size(14.dp))
                            Text("Link", color = muted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (linkText.isBlank()) t("Link yapıştır") else plannerLinkHost(linkText),
                            color = if (linkText.isBlank()) muted else ink,
                            fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = if (onEditLink != null) Modifier.clickable(role = Role.Button, onClick = onEditLink) else Modifier
                        )
                    }
                    PagerActionButton(if (linkText.isBlank()) t("Yapıştır") else t("Aç"), accent, true, onShareLink, ActionGlyph.ChevronRight, ink)
                }
                else -> {
                    Column(Modifier.weight(1f).fillMaxHeight().padding(start = 18.dp, top = 14.dp, bottom = 14.dp, end = 6.dp)) {
                        Text("Members (${members.size})", color = muted, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy((-10).dp), verticalAlignment = Alignment.CenterVertically) {
                            // Real people (uid → colour and their shared picture); plain names only as a fallback.
                            if (people.isNotEmpty()) people.take(3).forEach { p ->
                                Box(Modifier.border(2.dp, DetailBlack, CircleShape).clip(CircleShape)) {
                                    ConnectionAvatar(p.name, key = p.uid, size = 36.dp, pictureVersion = p.pictureVersion)
                                }
                            } else members.take(3).forEach { name ->
                                Box(Modifier.border(2.dp, DetailBlack, CircleShape).clip(CircleShape)) {
                                    ConnectionAvatar(name, key = name, size = 36.dp)
                                }
                            }
                            if (members.size > 3) {
                                Box(
                                    Modifier.size(36.dp).border(2.dp, DetailBlack, CircleShape).clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) { Text("+${members.size - 3}", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                            }
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).border(1.dp, DetailFaint, CircleShape)
                                    .background(DetailBlack)
                                    .clickable(role = Role.Button, onClick = onAddMember)
                                    .semantics { contentDescription = t("Add member") },
                                contentAlignment = Alignment.Center
                            ) { PlusMark(Color.White, Modifier.size(14.dp)) }
                        }
                    }
                    PagerActionButton("Manage", accent, true, onSend, ActionGlyph.Person, ink)
                }
            }
        }
    }
}

@Composable
private fun PagerDots(current: Int, count: Int, accent: Color, reduce: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val active = i == current
            val width by animateFloatAsState(
                if (active) 18f else 6f, if (reduce) snap() else tween(200), label = "dot-width"
            )
            val color by animateColorAsState(
                if (active) accent else Color(0xFF3A3A42), if (reduce) snap() else tween(200), label = "dot-color"
            )
            Box(Modifier.width(width.dp).height(6.dp).clip(CircleShape).background(color))
        }
    }
}

private enum class GoalState { Done, Active, Locked }

@Composable
private fun PlannerGoalRow(
    number: Int, goal: PlannerGoalUi, active: Boolean, isLast: Boolean, accent: Color, reduce: Boolean,
    dragging: Boolean, dragOffset: () -> Float, reorderMode: Boolean, modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    onToggle: () -> Unit, onEdit: () -> Unit, onOpen: () -> Unit,
    /** Tamamlama notu written in place (null removes it). */
    onSaveNote: (String?) -> Unit = {},
    onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit
) {
    // Pencil on a completed goal: its note is written right here, with the keyboard (no separate window).
    var editingNote by remember(goal.id) { mutableStateOf(false) }
    var noteDraft by remember(goal.id) { mutableStateOf("") }
    val noteFocus = remember { FocusRequester() }
    var noteHadFocus by remember(goal.id) { mutableStateOf(false) }
    fun saveNote() {
        if (!editingNote) return
        editingNote = false
        noteHadFocus = false
        val text = noteDraft.trim().take(com.cagri.real.data.ReminderRepository.COMPLETION_NOTE_MAX)
        if (text != goal.note.orEmpty()) onSaveNote(text.ifBlank { null })
    }
    fun startNote() { noteDraft = goal.note.orEmpty(); editingNote = true }
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentDragEnd by rememberUpdatedState(onDragEnd)
    val state = when {
        goal.done -> GoalState.Done
        active -> GoalState.Active
        else -> GoalState.Locked
    }
    // Rail under this goal: fully filled once done, partially filled while it is the active goal.
    val railTarget = when (state) {
        GoalState.Done -> 1f
        GoalState.Active -> (goal.progress ?: 0f).coerceIn(0f, 1f)
        GoalState.Locked -> 0f
    }
    val rail by animateFloatAsState(railTarget, if (reduce) snap() else tween(650), label = "goal-rail")
    val titleColor by animateColorAsState(
        when (state) {
            GoalState.Done -> Color(0xFF6F6F78)
            GoalState.Active -> Color.White
            GoalState.Locked -> Color(0xFFC9C9D0)
        }, if (reduce) snap() else tween(250), label = "goal-title"
    )
    // Only the active goal draws a pulse. Read its State in draw, not in the whole row's composition.
    val glow = if (state == GoalState.Active && !reduce) {
        rememberInfiniteTransition(label = "goal-pulse").animateFloat(
            0.08f, 0.26f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "goal-pulse-alpha"
        )
    } else rememberUpdatedState(0.16f)

    Row(
        modifier.fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffset()
                val lift = if (dragging) 1.02f else 1f
                scaleX = lift; scaleY = lift
                shadowElevation = if (dragging) 24f else 0f
            }
            .background(if (dragging) Color(0xFF1B1B21) else DetailSheet)
            .height(IntrinsicSize.Min)
            .padding(start = 18.dp, end = 20.dp)
    ) {
        // Number circle + progress rail
        Column(Modifier.width(52.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                if (state == GoalState.Active) {
                    Box(Modifier.size(52.dp).clip(CircleShape).drawBehind {
                        drawRect(accent.copy(alpha = glow.value))
                    })
                }
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .then(
                            when (state) {
                                GoalState.Done -> Modifier.background(accent)
                                GoalState.Active -> Modifier.background(accent.copy(alpha = 0.16f))
                                    .border(2.dp, accent, CircleShape)
                                GoalState.Locked -> Modifier.border(1.dp, Color(0xFF3A3A42), CircleShape)
                            }
                        )
                        .clickable(enabled = !readOnly, role = Role.Checkbox, onClick = onToggle)
                        .semantics { contentDescription = "Goal $number, ${if (goal.done) "done" else "not done"}" },
                    contentAlignment = Alignment.Center
                ) {
                    if (state == GoalState.Done) {
                        CheckMark(if (accent.luminance() > 0.45f) PlannerInk else Color.White, Modifier.size(18.dp))
                    } else {
                        Text(
                            number.toString(), fontSize = 16.sp,
                            color = if (state == GoalState.Active) accent else Color(0xFF7C7C85)
                        )
                    }
                }
            }
            if (!isLast) {
                Box(
                    Modifier.weight(1f).width(2.dp).padding(vertical = 2.dp).drawBehind {
                        val r = CornerRadius(size.width / 2f)
                        drawRoundRect(Color.White.copy(alpha = 0.10f), cornerRadius = r)
                        if (rail > 0f) {
                            drawRoundRect(accent, size = Size(size.width, size.height * rail), cornerRadius = r)
                        }
                    }
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp, bottom = if (isLast) 14.dp else 30.dp)) {
            val caption = when {
                goal.done -> t("Tamamlandı")
                goal.caption.isNotBlank() -> goal.caption
                state == GoalState.Active -> "In progress"
                else -> "Up next"
            }
            Text(
                caption,
                style = RealType.editorialMeta.copy(
                    color = if (state == GoalState.Active) accent else DetailMuted,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            )
            val titleStyle = if (goal.done) {
                RealType.editorialItemCompleted.copy(color = DetailMuted)
            } else {
                RealType.editorialItem.copy(color = titleColor)
            }
            Text(
                goal.title,
                style = titleStyle,
                modifier = Modifier.padding(top = 4.dp).clickable(role = Role.Button, onClick = onOpen)
            )
            // Tamamlama notu: under a completed goal. The pencil (or a tap on the note) writes it in place.
            if (goal.done && editingNote) {
                LaunchedEffect(Unit) { noteFocus.requestFocus() }
                BasicTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it.take(com.cagri.real.data.ReminderRepository.COMPLETION_NOTE_MAX) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFFE6E6EC), fontSize = 13.sp, lineHeight = 18.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { saveNote() }),
                    decorationBox = { inner ->
                        Box {
                            if (noteDraft.isEmpty()) Text(t("Not yaz…"), color = DetailFaint, fontSize = 13.sp, lineHeight = 18.sp)
                            inner()
                        }
                    },
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth().focusRequester(noteFocus)
                        // Saves when the field loses the focus it had (the first report, before focus arrives, is skipped).
                        .onFocusChanged { if (it.isFocused) noteHadFocus = true else if (noteHadFocus) saveNote() }
                )
            } else if (goal.done && goal.note != null) {
                Text(
                    goal.note,
                    color = Color(0xFFB7B7C0), fontSize = 13.sp, lineHeight = 18.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.padding(top = 6.dp)
                        .then(if (readOnly) Modifier else Modifier.clickable(role = Role.Button) { startNote() })
                        .semantics { contentDescription = t("Tamamlama notu: {0}", goal.note) }
                )
            }
            goal.progress?.takeIf { !goal.done }?.let { p ->
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(120.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(p.coerceIn(0f, 1f)).clip(CircleShape).background(accent))
                    }
                    Text("${(p * 100).toInt()}%", color = DetailMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp))
                }
            }
            if (!readOnly) Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                // Drag handle: only live in reorder mode (pencil on the progress tile). Outside it the
                // dots are plain decoration, so a stray touch never moves a goal.
                Box(
                    Modifier.size(width = 44.dp, height = 36.dp)
                        .then(
                            if (reorderMode) Modifier.pointerInput(goal.id) {
                                detectDragGestures(
                                    onDragStart = { currentDragStart() },
                                    onDrag = { change, amount -> change.consume(); currentDrag(amount.y) },
                                    onDragEnd = { currentDragEnd() },
                                    onDragCancel = { currentDragEnd() }
                                )
                            } else Modifier
                        )
                        .semantics { if (reorderMode) contentDescription = t("Reorder goal") },
                    contentAlignment = Alignment.CenterStart
                ) {
                    DotsMark(
                        when {
                            dragging || reorderMode -> accent
                            else -> DetailFaint
                        },
                        Modifier.size(width = 22.dp, height = 18.dp)
                    )
                }
                Spacer(Modifier.width(2.dp))
                Box(
                    Modifier.size(32.dp).clip(CircleShape)
                        .clickable(role = Role.Button) { if (goal.done) startNote() else onEdit() }
                        .semantics { contentDescription = if (goal.done) t("Tamamlama notu yaz") else t("Edit goal") },
                    contentAlignment = Alignment.Center
                ) { PencilMark(Color(0xFF8C8C95), Modifier.size(18.dp)) }
                Spacer(Modifier.weight(1f))
                Text(
                    if (goal.done) "Undo" else "Mark done",
                    color = if (goal.done) DetailMuted else Color.White, fontSize = 12.sp,
                    modifier = Modifier.clip(CircleShape).border(1.dp, DetailFaint, CircleShape)
                        .clickable(role = Role.Button, onClick = onToggle)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// ---------- demo (stateful, handy to see/tweak the screen before wiring real data) ----------

// ---------- category style popup (pencil on the progress tile) ----------

/** Places the popup horizontally centered in the window, just below the pencil badge. */
private class BelowAnchorCentered(private val gapPx: Int, private val marginPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize
    ): IntOffset {
        val x = ((windowSize.width - popupContentSize.width) / 2).coerceAtLeast(marginPx)
        val y = (anchorBounds.bottom + gapPx)
            .coerceAtMost((windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(marginPx))
        return IntOffset(x, y)
    }
}

@Composable
private fun CategoryStylePopup(
    selectedPastel: Color?,
    onPickPastel: ((Color) -> Unit)?,
    cardShape: PlannerCategoryCardShape?,
    onCardShapeChange: ((PlannerCategoryCardShape) -> Unit)?,
    reduceAnimations: Boolean,
    visibleInCalendar: Boolean,
    onCalendarVisibilityChange: ((Boolean) -> Unit)?,
    onReorder: () -> Unit,
    onDismiss: () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val positioner = remember(density) { BelowAnchorCentered(with(density) { 10.dp.roundToPx() }, with(density) { 16.dp.roundToPx() }) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Popup(popupPositionProvider = positioner, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        AnimatedVisibility(
            visible = shown,
            enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.92f),
            exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.96f)
        ) {
            Column(
                Modifier.width(300.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF1B1B1F))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (onPickPastel != null) {
                    Text(t("Renk"), color = Color(0xFF8C8C95), fontSize = 12.sp)
                    // 10 app pastels, two rows of five.
                    RealCalendarPastels.chunked(5).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            row.forEach { pastel ->
                                val selected = selectedPastel != null && selectedPastel == pastel
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape).background(pastel)
                                        .then(if (selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                                        .clickable(role = Role.RadioButton) { onPickPastel(pastel) }
                                        .semantics { contentDescription = if (selected) t("Selected color") else t("Color") },
                                    contentAlignment = Alignment.Center
                                ) { if (selected) CheckMark(PlannerInk, Modifier.size(16.dp)) }
                            }
                        }
                    }
                }
                if (cardShape != null && onCardShapeChange != null) {
                    Text(t("Kart şekli"), color = Color(0xFF8C8C95), fontSize = 12.sp)
                    // Single-select: exactly one of the two is active.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlannerCategoryCardShape.entries.forEach { option ->
                            val selected = option == cardShape
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                                    .background(if (selected) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f))
                                    .border(1.dp, if (selected) Color.White.copy(alpha = 0.7f) else Color.Transparent, RoundedCornerShape(18.dp))
                                    .clickable(role = Role.RadioButton) { onCardShapeChange(option) }
                                    .semantics { contentDescription = if (selected) t("Selected shape") else t("Shape") }
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val previewColor = if (selected) Color.White else Color(0xFF8C8C95)
                                val (w, h) = if (option == PlannerCategoryCardShape.Square) 26.dp to 26.dp else 42.dp to 26.dp
                                Box(Modifier.size(w, h).border(1.8.dp, previewColor, RoundedCornerShape(7.dp)))
                                Text(
                                    if (option == PlannerCategoryCardShape.Square) t("Kare") else t("Dikdörtgen"),
                                    color = if (selected) Color.White else Color(0xFF8C8C95), fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
                if (onCalendarVisibilityChange != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = visibleInCalendar, onCheckedChange = onCalendarVisibilityChange)
                        Text(t("REAL takviminde göster"), color = Color.White, fontSize = 13.sp)
                    }
                }
                Text(
                    t("Hedefleri sırala"), color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .clickable(role = Role.Button, onClick = onReorder)
                        .padding(horizontal = 16.dp, vertical = 13.dp)
                )
            }
        }
    }
}
