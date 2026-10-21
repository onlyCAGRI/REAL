package com.cagri.real.ui.components.planner

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.CustomCategory
import com.cagri.real.data.RecurrenceRule
import com.cagri.real.data.descriptionFor
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import com.cagri.real.ui.components.AnimatedDateTimePickerOverlay
import com.cagri.real.ui.components.CategoryIcon
import com.cagri.real.ui.components.CategoryIconGlyph
import com.cagri.real.ui.components.LiquidGlassRole
import com.cagri.real.ui.components.LiquidGlassSurface
import com.cagri.real.ui.components.ReminderColorChoices
import com.cagri.real.ui.components.liquidChrome
import com.cagri.real.ui.components.shouldCaptureBackdrop
import com.cagri.real.ui.theme.RealType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal data class PlannerSaveResult(val id: Long?, val complete: Boolean, val error: String? = null)

private enum class PlannerEditorTool { People, Card, Category }

@Composable
internal fun PlannerEventEditor(
    initialDay: LocalDate,
    initialHour: Int,
    customCategories: List<CustomCategory>,
    designStyle: DesignStyle,
    reduceDynamicBlur: Boolean,
    reduceAnimations: Boolean,
    exactAlarmsAllowed: Boolean,
    onRequestExactAlarms: () -> Unit,
    onDismiss: () -> Unit,
    // The planner's own scene (calendar content). When present, the panel uses the clear dock glass over it.
    glassBackdrop: GraphicsLayer? = null,
    glassBackdropPosition: () -> Offset = { Offset.Zero },
    onSave: suspend (String, Long, RecurrenceRule, PlannerEventDetails, Long?, Long?) -> PlannerSaveResult
) {
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var epochDay by rememberSaveable { mutableLongStateOf(initialDay.toEpochDay()) }
    var minutes by rememberSaveable { mutableIntStateOf(initialHour * 60) }
    var repeatRule by rememberSaveable { mutableStateOf(RecurrenceRule.NONE) }
    var showCalendarPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }
    var calendarConfigured by rememberSaveable { mutableStateOf(false) }
    var duration by rememberSaveable { mutableStateOf("") }
    // Bitiş: its own day and time (may be days later); the duration stays per day ("4 gün, her gün 60 dk").
    var endAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var earlyAlertMinutes by rememberSaveable { mutableStateOf<Int?>(null) }
    val setReminderTiming = com.cagri.real.ui.components.LocalSetReminderTiming.current
    var people by rememberSaveable { mutableStateOf("") }
    var colorIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var savedReminderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var activeTool by remember { mutableStateOf<PlannerEditorTool?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val backdropTap = remember { MutableInteractionSource() }
    val panelTap = remember { MutableInteractionSource() }
    val scene = rememberGraphicsLayer()
    var scenePosition by remember { mutableStateOf(Offset.Zero) }

    val day = LocalDate.ofEpochDay(epochDay)
    val time = LocalTime.of(minutes / 60, minutes % 60)
    val durationMinutes = duration.toIntOrNull()
    val startMillis = remember(day, time) {
        day.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    val endMillis = endAt?.takeIf { it > startMillis }
    val valid = title.isNotBlank() && (duration.isEmpty() || (durationMinutes != null && durationMinutes in 1..1440))
    val canEditReminder = !saving && savedReminderId == null
    val selectedCategory = remember(customCategories, selectedCategoryId) {
        customCategories.firstOrNull { it.id == selectedCategoryId }
    }

    fun save() {
        if (!valid || saving) return
        saving = true
        error = null
        scope.launch {
            try {
                val details = PlannerEventDetails(
                    durationMinutes = durationMinutes,
                    people = people.split(',')
                        .map(String::trim)
                        .filter(String::isNotBlank)
                        .distinct()
                        .take(12),
                    colorIndex = colorIndex
                )
                val result = onSave(
                    title.trim(),
                    day.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    repeatRule,
                    details,
                    savedReminderId,
                    selectedCategoryId
                )
                savedReminderId = result.id
                val savedId = result.id
                if (savedId != null && (earlyAlertMinutes != null || endMillis != null)) setReminderTiming(savedId, earlyAlertMinutes, endMillis)
                if (result.complete) onDismiss() else error = result.error
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Kaydedilemedi. Yeniden dene."
            } finally {
                saving = false
            }
        }
    }

    BackHandler(enabled = !saving, onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
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
                .background(Color.Black.copy(alpha = 0.34f))
                .clickable(
                    enabled = !saving,
                    interactionSource = backdropTap,
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = 14.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            val panelBase = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clickable(interactionSource = panelTap, indication = null, onClick = {})
            val panelContent: @Composable ColumnScope.() -> Unit = {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 3.dp)
                    .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(2.dp))
            )

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth().heightIn(min = 58.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = title,
                    onValueChange = { changed ->
                        title = changed.replace("\r", "").replace("\n", " ").take(500)
                    },
                    enabled = canEditReminder,
                    modifier = Modifier.weight(1f).padding(start = 8.dp, end = 8.dp),
                    textStyle = RealType.composerInput.copy(
                        color = Color(0xFFF7F7F8).copy(alpha = if (canEditReminder) 1f else 0.52f)
                    ),
                    cursorBrush = SolidColor(Color(0xFF9AB9FF)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            if (title.isEmpty()) {
                                Text("Aklında ne var?", color = Color(0xFF8E8E96), style = RealType.composerPrompt)
                            }
                            inner()
                        }
                    }
                )

                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                valid && !saving -> Color(0xFF295799)
                                else -> Color(0xFF1E1E24)
                            }
                        )
                        .clickable(enabled = valid && !saving, role = Role.Button, onClick = ::save)
                        .semantics { contentDescription = "Takvim kartını kaydet" },
                    contentAlignment = Alignment.Center
                ) {
                    if (saving) {
                        Text("…", color = Color(0xFF6B6B74), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    } else {
                        ActionIcon(
                            ActionGlyph.Check,
                            Modifier.size(16.dp),
                            if (valid) Color.White else Color(0xFF6B6B74)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Row 1: Start date/time & End time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Start
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.055f))
                        .border(0.6.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                        .clickable(enabled = canEditReminder, role = Role.Button) {
                            activeTool = null
                            showCalendarPicker = true
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Başlangıç", color = Color.White.copy(alpha = 0.50f), fontSize = 9.sp)
                        Text(
                            "${day.dayOfMonth} ${plannerMonthLabel(day)} · ${formatTimeAmPm(startMillis, ZoneId.systemDefault())}",
                            color = Color.White,
                            style = PlannerType.pickerValue
                        )
                    }
                    ActionIcon(ActionGlyph.ChevronRight, Modifier.size(13.dp), Color.White.copy(alpha = 0.40f))
                }

                // End
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.055f))
                        .border(0.6.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                        .clickable(enabled = canEditReminder, role = Role.Button) {
                            showEndPicker = true
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Bitiş", color = Color.White.copy(alpha = 0.50f), fontSize = 9.sp)
                        Text(
                            if (endMillis != null) {
                                val endDay = java.time.Instant.ofEpochMilli(endMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                                "${endDay.dayOfMonth} ${plannerMonthLabel(endDay)} · ${formatTimeAmPm(endMillis, ZoneId.systemDefault())}"
                            } else "—",
                            color = Color.White,
                            style = PlannerType.pickerValue
                        )
                    }
                    ActionIcon(ActionGlyph.ChevronRight, Modifier.size(13.dp), Color.White.copy(alpha = 0.40f))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Row 2: Duration quick chips (clean text chips: 30, 45, 60, 90, 120, 150, 180 min)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(30, 45, 60, 90, 120, 150, 180).forEach { mins ->
                    val isSelected = durationMinutes == mins
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFF91B9FF).copy(alpha = 0.22f) else Color.White.copy(alpha = 0.055f))
                            .border(
                                0.6.dp,
                                if (isSelected) Color(0xFF91B9FF).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.09f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = canEditReminder) {
                                duration = if (isSelected) "" else mins.toString()
                            }
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$mins min",
                            color = if (isSelected) Color(0xFFDCE9FF) else Color.White.copy(alpha = 0.72f),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Row 3: Tool buttons (reclaimed space, no redundant Takvim button)
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Top
            ) {
                PlannerToolButton(
                    title = "Kişi",
                    glyph = ActionGlyph.Person,
                    selected = activeTool == PlannerEditorTool.People,
                    modifier = Modifier.weight(1f),
                    enabled = !saving,
                    onClick = { activeTool = activeTool.toggle(PlannerEditorTool.People) }
                )
                PlannerToolButton(
                    title = "Kart",
                    glyph = ActionGlyph.Cards,
                    selected = activeTool == PlannerEditorTool.Card,
                    modifier = Modifier.weight(1f),
                    enabled = !saving,
                    onClick = { activeTool = activeTool.toggle(PlannerEditorTool.Card) }
                )
                PlannerToolButton(
                    title = "Kategori",
                    glyph = ActionGlyph.Category,
                    selected = activeTool == PlannerEditorTool.Category,
                    modifier = Modifier.weight(1f),
                    enabled = !saving,
                    accent = selectedCategory?.let(::plannerCategoryAccent),
                    onClick = { activeTool = activeTool.toggle(PlannerEditorTool.Category) }
                )
            }

            activeTool?.let { tool ->
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color.White.copy(alpha = 0.045f))
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    when (tool) {
                        PlannerEditorTool.People -> PlannerPeopleTool(
                            people = people,
                            onPeopleChange = { people = it.take(240) },
                            enabled = !saving
                        )

                        PlannerEditorTool.Card -> PlannerCardTool(
                            colorIndex = colorIndex,
                            onColorSelected = { colorIndex = it },
                            enabled = !saving
                        )

                        PlannerEditorTool.Category -> PlannerCategoryTool(
                            customCategories = customCategories,
                            selectedCategoryId = selectedCategoryId,
                            onSelect = { selectedCategoryId = it },
                            enabled = canEditReminder
                        )
                    }
                }
            }

            if (repeatRule != RecurrenceRule.NONE) {
                Text(
                    repeatRule.descriptionFor(
                        day.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        ZoneId.systemDefault()
                    ),
                    color = Color(0xFF91B9FF),
                    style = PlannerType.editorSupportingText,
                    modifier = Modifier.padding(start = 8.dp, top = 7.dp)
                )
            }

            if (duration.isNotEmpty() && (durationMinutes == null || durationMinutes !in 1..1440)) {
                Text(
                    "Süre 1–1440 dakika arasında olmalı.",
                    color = Color(0xFFFFBAA7),
                    style = PlannerType.editorSupportingText,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                )
            }

            error?.let {
                Text(
                    it,
                    color = Color(0xFFFFBAA7),
                    style = PlannerType.editorSupportingText,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                )
            }
            }
            val panelPadding = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp)
            val dockBackdrop = glassBackdrop?.takeIf {
                designStyle == DesignStyle.LiquidGlass &&
                    shouldCaptureBackdrop(designStyle, reduceDynamicBlur, Build.VERSION.SDK_INT)
            }
            if (dockBackdrop != null) {
                // reduceAnimations = true: no press-squish on a panel that hosts a text field.
                LiquidGlassSurface(
                    dockBackdrop, glassBackdropPosition, panelBase, 28.dp, reduceDynamicBlur,
                    reduceAnimations = true, role = LiquidGlassRole.Dock
                ) {
                    // Same dim as the editor scrim; the sampled calendar does not include it.
                    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.34f)))
                    Column(Modifier.fillMaxWidth().then(panelPadding), content = panelContent)
                }
            } else {
                Column(panelBase.liquidChrome(corners = 28.dp).then(panelPadding), content = panelContent)
            }
        }

        AnimatedDateTimePickerOverlay(
            visible = showCalendarPicker,
            initial = startMillis,
            initialRepeatRule = repeatRule,
            allowYearSelection = false,
            initialEarlyAlertMinutes = earlyAlertMinutes,
            initialEndAt = endMillis,
            onConfirmTiming = { early, end -> earlyAlertMinutes = early; endAt = end },
            backdrop = scene,
            backdropPosition = { scenePosition },
            designStyle = designStyle,
            reduceDynamicBlur = reduceDynamicBlur,
            reduceAnimations = reduceAnimations,
            exactAlarmsAllowed = exactAlarmsAllowed,
            onRequestExactAlarms = onRequestExactAlarms,
            onDismiss = { showCalendarPicker = false },
            onConfirm = { at, rule ->
                val zdt = java.time.Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
                epochDay = zdt.toLocalDate().toEpochDay()
                minutes = zdt.hour * 60 + zdt.minute
                repeatRule = rule
                calendarConfigured = true
                showCalendarPicker = false
            }
        )

        AnimatedDateTimePickerOverlay(
            visible = showEndPicker,
            initial = endMillis ?: (startMillis + (durationMinutes ?: 60) * 60_000L),
            initialRepeatRule = RecurrenceRule.NONE,
            backdrop = scene,
            backdropPosition = { scenePosition },
            designStyle = designStyle,
            reduceDynamicBlur = reduceDynamicBlur,
            reduceAnimations = reduceAnimations,
            exactAlarmsAllowed = exactAlarmsAllowed,
            onRequestExactAlarms = onRequestExactAlarms,
            onDismiss = { showEndPicker = false },
            onConfirm = { at, _ ->
                if (at > startMillis) { endAt = at; error = null } else error = "Bitiş, başlangıçtan sonra olmalı."
                showEndPicker = false
            }
        )
    }
}

private fun PlannerEditorTool?.toggle(tool: PlannerEditorTool): PlannerEditorTool? =
    if (this == tool) null else tool

@Composable
private fun PlannerToolButton(
    title: String,
    glyph: ActionGlyph,
    selected: Boolean,
    modifier: Modifier,
    enabled: Boolean,
    accent: Color? = null,
    onClick: () -> Unit
) {
    val iconColor = when {
        selected -> Color(0xFF91B9FF)
        accent != null -> accent
        else -> Color(0xFFBABAC3)
    }
    Column(
        modifier
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) Color(0xFF91B9FF).copy(alpha = 0.10f) else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ActionIcon(glyph, Modifier.size(20.dp), iconColor)
        Text(
            title,
            color = if (selected) Color(0xFFDCE9FF) else Color(0xFF8E8E96),
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun PlannerPeopleTool(
    people: String,
    onPeopleChange: (String) -> Unit,
    enabled: Boolean
) {
    BasicTextField(
        value = people,
        onValueChange = onPeopleChange,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        textStyle = PlannerType.peopleInput.copy(color = Color.White),
        singleLine = true,
        cursorBrush = SolidColor(Color(0xFF9AB9FF)),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (people.isEmpty()) {
                    Text("İsimleri virgülle ayır", color = Color.White.copy(alpha = 0.34f), style = PlannerType.peoplePlaceholder)
                }
                inner()
            }
        }
    )
}

@Composable
private fun PlannerCardTool(
    colorIndex: Int,
    onColorSelected: (Int) -> Unit,
    enabled: Boolean
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlannerPastels.forEachIndexed { index, color ->
            Box(
                Modifier
                    .size(if (index == colorIndex) 34.dp else 30.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable(enabled = enabled, role = Role.Button) { onColorSelected(index) }
                    .semantics { contentDescription = "Kart rengi ${index + 1}" },
                contentAlignment = Alignment.Center
            ) {
                if (index == colorIndex) {
                    Box(Modifier.size(11.dp).clip(CircleShape).background(PlannerInk.copy(alpha = 0.78f)))
                }
            }
        }
    }
}

@Composable
private fun PlannerCategoryTool(
    customCategories: List<CustomCategory>,
    selectedCategoryId: Long?,
    onSelect: (Long?) -> Unit,
    enabled: Boolean
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PlannerCategoryChip(
            label = "Kategori yok",
            selected = selectedCategoryId == null,
            enabled = enabled,
            onClick = { onSelect(null) }
        )
        customCategories.forEach { category ->
            PlannerCategoryChip(
                label = category.name,
                selected = selectedCategoryId == category.id,
                enabled = enabled,
                accent = plannerCategoryAccent(category),
                icon = CategoryIcon.fromKey(category.iconKey),
                onClick = { onSelect(category.id) }
            )
        }
    }
}

@Composable
private fun PlannerCategoryChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    accent: Color = Color(0xFFBABAC3),
    icon: CategoryIcon? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.045f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (icon != null) {
            CategoryIconGlyph(icon, Modifier.size(16.dp), if (selected) accent else Color(0xFFB8B8C0))
        } else {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (selected) Color(0xFF91B9FF) else Color(0xFF777780)))
        }
        Text(
            label,
            color = if (selected) Color.White else Color(0xFFB8B8C0),
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

private fun plannerCategoryAccent(category: CustomCategory): Color =
    category.customSolidColorArgb?.let { Color(it) }
        ?: ReminderColorChoices.getOrNull(category.accentStart)?.color
        ?: Color(0xFF91B9FF)
