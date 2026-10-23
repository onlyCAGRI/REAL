package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.IconButton
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.ReminderCategory
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.ReminderSortOrder
import com.cagri.real.ui.theme.RealType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/*
 * Category screen header, dock-glass look.
 *
 * Why "chip" and not LiquidGlassSurface: the category header lives inside the list content that the
 * screen records as its glass backdrop. A live-refracting surface there would sample the layer it is
 * being recorded into (layer loop). So these buttons reuse the dock's material - clear dark tint,
 * top-left light, bright two-corner rim - as a static surface.
 */

/** Static dock-look glass surface (no backdrop sampling, safe inside recorded content). */
fun Modifier.liquidDockChip(shape: Shape): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Color(0x9E1B1B20), Color(0xB80E0E12))))
    .drawWithCache {
        val light = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
            center = Offset(size.width * 0.16f, -size.height * 0.4f),
            radius = size.width
        )
        onDrawBehind { drawRect(light) }
    }
    .border(LiquidGlassTokens.dockEdgeWidth, liquidDockRimBrush(), shape)

private val GlyphColor = Color(0xFFC9CBD3)
private val GlyphActive = Color.White

@Composable
private fun DockGlassCircle(
    size: Dp, modifier: Modifier = Modifier,
    surface: (@Composable (Modifier) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier.size(size).then(if (surface == null) Modifier.liquidDockChip(CircleShape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        surface?.invoke(Modifier.fillMaxSize())
        content()
    }
}

@Composable
private fun GlassTap(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(source, reduceAnimations = LocalLiquidReduceMotion.current, targetScale = 0.94f)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics { role = Role.Button; contentDescription = description },
        contentAlignment = Alignment.Center,
        content = content
    )
}

private val HeaderButtonSize = 36.dp

@Composable
fun CategoryGlassBackButton(
    onBack: () -> Unit, modifier: Modifier = Modifier,
    surface: (@Composable (Modifier) -> Unit)? = null
) {
    RealBackButton(onBack, modifier, description = t("Ana sayfaya dön"), glyphSize = 20.dp, surface = {
        if (surface != null) surface(Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize().liquidDockChip(CircleShape))
    })
}

/** Ring that fills with the category's completion. Lime->aqua like the progress bar. */
@Composable
fun ProgressRingIcon(fraction: Float, active: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val stroke = 2.2.dp.toPx()
        val inset = stroke / 2f + 1.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        drawArc(
            Color.White.copy(alpha = if (active) 0.34f else 0.22f), 0f, 360f, false,
            topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke)
        )
        val sweep = 360f * fraction.coerceIn(0f, 1f)
        if (sweep > 0.5f) {
            drawArc(
                Brush.sweepGradient(listOf(Color(0xFFC4FF6A), Color(0xFF62F5DC), Color(0xFFC4FF6A))),
                startAngle = -90f, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
    }
}

/** The hero's progress button: a plain ring in the header's text colour, like the sort arrow beside it. */
@Composable
fun CategoryProgressDisc(fraction: Float, active: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val stroke = 2.6.dp.toPx()
        val inset = stroke / 2f + 1.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        drawArc(tint.copy(alpha = if (active) 0.34f else 0.24f), 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
        val sweep = 360f * fraction.coerceIn(0f, 1f)
        if (sweep > 0.5f) drawArc(tint, -90f, sweep, false, topLeft = Offset(inset, inset), size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun SortLinesGlyph(longestFirst: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val stroke = 1.6.dp.toPx()
        val widths = if (longestFirst) listOf(16f, 11f, 6f) else listOf(6f, 11f, 16f)
        widths.forEachIndexed { i, w ->
            val y = (4.5f + i * 5.5f).dp.toPx()
            drawLine(color, Offset(2.dp.toPx(), y), Offset((2f + w).dp.toPx(), y), stroke, StrokeCap.Round)
        }
    }
}

private data class SortChoice(val order: ReminderSortOrder, val label: String)

private val CategorySortChoices get() = listOf(
    SortChoice(ReminderSortOrder.RECENTLY_ADDED, t("Son eklenen üstte")),
    SortChoice(ReminderSortOrder.OLDEST_FIRST, t("Son eklenen en sonda")),
    SortChoice(ReminderSortOrder.NEAREST_DATE, t("Tarihi en yakın"))
)

@Composable
private fun SortGlyphFor(order: ReminderSortOrder, color: Color) {
    when (order) {
        ReminderSortOrder.OLDEST_FIRST -> SortLinesGlyph(longestFirst = false, color = color)
        ReminderSortOrder.NEAREST_DATE, ReminderSortOrder.REMINDER_TIME ->
            ActionIcon(ActionGlyph.Calendar, Modifier.size(18.dp), color)
        else -> SortLinesGlyph(longestFirst = true, color = color)
    }
}

@Composable
fun CategoryGlassActions(
    hasFilter: Boolean,
    selectedColorIndex: Int?,
    onFilterClick: () -> Unit,
    onEditClick: () -> Unit,
    editContentDescription: String,
    sortOrder: ReminderSortOrder,
    onSortSelected: (ReminderSortOrder) -> Unit,
    showSort: Boolean,
    progressFraction: Float?,
    progressOpen: Boolean,
    onProgressClick: () -> Unit,
    surface: (@Composable (Modifier) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (progressFraction != null) {
            DockGlassCircle(HeaderButtonSize, surface = surface) {
                GlassTap(
                    if (progressOpen) t("İlerlemeyi gizle") else t("Kategori ilerlemesini göster"),
                    onProgressClick, Modifier.size(HeaderButtonSize)
                ) { ProgressRingIcon(progressFraction, progressOpen) }
            }
        }
        if (showSort) {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                DockGlassCircle(HeaderButtonSize, surface = surface) {
                    GlassTap(t("Sıralama"), { menuOpen = true }, Modifier.size(HeaderButtonSize)) {
                        SortGlyphFor(sortOrder, if (menuOpen) GlyphActive else GlyphColor)
                    }
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.background(Color(0xFF1A1A1F))
                ) {
                    CategorySortChoices.forEach { choice ->
                        val selected = choice.order == sortOrder
                        DropdownMenuItem(
                            text = {
                                Text(
                                    choice.label,
                                    color = if (selected) Color.White else Color(0xFFB4B9C4),
                                    style = if (selected) RealType.categorySortMenuLabelSelected else RealType.categorySortMenuLabel
                                )
                            },
                            leadingIcon = { SortGlyphFor(choice.order, if (selected) Color.White else Color(0xFF8E8E93)) },
                            trailingIcon = { if (selected) ActionIcon(ActionGlyph.Check, Modifier.size(16.dp), Color.White) },
                            onClick = { menuOpen = false; onSortSelected(choice.order) }
                        )
                    }
                }
            }
        }
        DockGlassCircle(HeaderButtonSize, surface = surface) {
            CategoryColorFilterButton(
                hasFilter = hasFilter,
                selectedColorIndex = selectedColorIndex,
                onClick = onFilterClick,
                modifier = Modifier.size(HeaderButtonSize),
                applyLiquidChrome = false
            )
        }
        DockGlassCircle(HeaderButtonSize, surface = surface) {
            CategoryEditButton(
                onClick = onEditClick,
                contentDescription = editContentDescription,
                modifier = Modifier.size(HeaderButtonSize),
                applyLiquidChrome = false
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Progress of one category
// ---------------------------------------------------------------------------------------------

internal data class CategoryProgressStats(val done: Int, val total: Int, val doneToday: Int) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
    val remaining: Int get() = (total - done).coerceAtLeast(0)
}

/**
 * Progress of a category counting completed items too (the list itself hides them). Returns null for
 * categories where progress is meaningless (24 hour, Tamamlananlar, Günlüklerim).
 */
internal fun categoryProgress(
    reminders: List<ReminderItem>,
    category: ReminderCategory?,
    customCategoryId: Long?,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): CategoryProgressStats? {
    val tasks = reminders.filter { it.journal == null && it.mood == null }
    val members = when {
        customCategoryId != null -> tasks.filter { it.customCategoryId == customCategoryId }
        category == ReminderCategory.Planned -> tasks.filter { it.alertAt != null || it.plannedDateEpochDay != null }
        category == ReminderCategory.Important -> tasks.filter { it.isImportant }
        category == ReminderCategory.NoAlert ->
            tasks.filter { it.alertAt == null && it.plannedDateEpochDay == null && it.customCategoryId == null }
        else -> return null
    }
    val done = members.count { it.isCompleted }
    val doneToday = members.count {
        it.completedAt?.let { ms -> Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() == today } == true
    }
    return CategoryProgressStats(done, members.size, doneToday)
}

@Composable
private fun StatChip(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(
                0.8.dp,
                accentColor?.copy(alpha = 0.28f) ?: Color.White.copy(alpha = 0.10f),
                shape
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            color = Color(0xFFB4B9C4),
            style = RealType.categoryStatLabel.copy(
                fontSize = 11.5.sp,
                letterSpacing = (-0.2).sp
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "$value",
            color = if (accentColor != null && value > 0) accentColor else Color.White,
            style = RealType.categoryStatValue
        )
    }
}

@Composable
internal fun CategoryProgressPanel(
    title: String,
    stats: CategoryProgressStats,
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier,
    categoryColor: Color? = null
) {
    val accent = categoryColor ?: Color(0xFF62F5DC)
    val glowColor = remember(accent) { accent.copy(alpha = 0.40f) }
    val fill = remember { Animatable(if (reduceAnimations) stats.fraction else 0f) }
    LaunchedEffect(stats.fraction, reduceAnimations) {
        if (reduceAnimations) fill.snapTo(stats.fraction)
        else fill.animateTo(stats.fraction, tween(850, delayMillis = 80, easing = FastOutSlowInEasing))
    }
    WidgetGlowCard(glowColor, reduceAnimations, 0, modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(t("İlerleme"), color = Color.White, style = RealType.categoryProgressHeader, modifier = Modifier.weight(1f))
            Text(title, color = Color(0xFFB4B9C4), style = RealType.categoryProgressSubtitle)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${(fill.value * 100f).roundToInt()}", color = Color.White, style = RealType.categoryProgressPercent)
            Text("%", color = accent.copy(alpha = 0.85f), style = RealType.categoryProgressPercentSymbol)
        }
        Spacer(Modifier.height(8.dp))
        GlowProgressBar(
            fraction = fill.value,
            label = if (stats.total == 0) t("Kayıt yok") else "${stats.done} / ${stats.total}",
            height = 46.dp,
            accentColor = accent
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatChip(t("Tamamlanan"), stats.done, Modifier.weight(1f), accentColor = accent)
            StatChip(t("Kalan"), stats.remaining, Modifier.weight(1f))
            StatChip(t("Bugün"), stats.doneToday, Modifier.weight(1f))
        }
    }
}


/** Inline hero controls: newest first -> oldest first -> nearest date. */
@Composable
internal fun CategoryHeroActions(
    sortOrder: ReminderSortOrder,
    onSortSelected: (ReminderSortOrder) -> Unit,
    progressFraction: Float?,
    progressOpen: Boolean,
    onProgressClick: () -> Unit,
    foreground: Color,
    reduceAnimations: Boolean,
    showSort: Boolean = true,
    buttonSize: Dp = 48.dp,
    /** The category colour: the ring sits on a disc of its deep shade and fills with its light shade. */
    ringTint: Color? = null
) {
    val angle = when (sortOrder) {
        ReminderSortOrder.OLDEST_FIRST -> 180f
        ReminderSortOrder.NEAREST_DATE, ReminderSortOrder.REMINDER_TIME -> 90f
        else -> 0f
    }
    val rotation by animateFloatAsState(
        targetValue = angle,
        animationSpec = tween(if (reduceAnimations) 0 else 220),
        label = "categorySortArrow"
    )
    val currentLabel = when (sortOrder) {
        ReminderSortOrder.OLDEST_FIRST -> t("Son eklenen altta")
        ReminderSortOrder.NEAREST_DATE, ReminderSortOrder.REMINDER_TIME -> t("Tarihi en yakın")
        else -> t("Son eklenen üstte")
    }
    val next = when (sortOrder) {
        ReminderSortOrder.RECENTLY_ADDED -> ReminderSortOrder.OLDEST_FIRST
        ReminderSortOrder.OLDEST_FIRST -> ReminderSortOrder.NEAREST_DATE
        else -> ReminderSortOrder.RECENTLY_ADDED
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (progressFraction != null) IconButton(
            onClick = onProgressClick,
            modifier = Modifier.size(buttonSize).semantics {
                contentDescription = if (progressOpen) t("İlerlemeyi gizle") else t("Kategori ilerlemesini göster")
            }
        ) {
            if (ringTint != null) CategoryProgressDisc(progressFraction, progressOpen, ringTint)
            else ProgressRingIcon(progressFraction, progressOpen, Modifier.size(22.dp))
        }
        if (showSort) IconButton(
            onClick = { onSortSelected(next) },
            modifier = Modifier.size(buttonSize).semantics {
                contentDescription = t("Sıralamayı değiştir")
                stateDescription = currentLabel
            }
        ) {
            Canvas(Modifier.size(26.dp).graphicsLayer { rotationZ = rotation }) {
                val stroke = 1.8.dp.toPx()
                val tip = Offset(size.width * 0.5f, size.height * 0.18f)
                drawLine(foreground, Offset(size.width * 0.5f, size.height * 0.82f), tip, stroke, StrokeCap.Round)
                drawLine(foreground, Offset(size.width * 0.23f, size.height * 0.45f), tip, stroke, StrokeCap.Round)
                drawLine(foreground, Offset(size.width * 0.77f, size.height * 0.45f), tip, stroke, StrokeCap.Round)
            }
        }
    }
}
