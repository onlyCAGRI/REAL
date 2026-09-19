package com.cagri.bigboscagri.ui

import android.os.Build
import com.cagri.bigboscagri.ui.components.DesignPanel
import com.cagri.bigboscagri.ui.components.AnimatedDateTimePickerOverlay
import com.cagri.bigboscagri.ui.components.NotificationPermissionSheet
import androidx.compose.material3.TextButton
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.bigboscagri.data.RecurrenceRule
import com.cagri.bigboscagri.data.ReminderItem
import com.cagri.bigboscagri.data.repeatSummary
import com.cagri.bigboscagri.ui.components.ActionGlyph
import com.cagri.bigboscagri.ui.components.ActionIcon
import com.cagri.bigboscagri.ui.components.GlassPanel
import com.cagri.bigboscagri.ui.components.DeleteReminderPrompt
import com.cagri.bigboscagri.data.CustomCategory
import com.cagri.bigboscagri.ui.components.CategoryIcon
import com.cagri.bigboscagri.ui.components.CategoryIconGlyph
import com.cagri.bigboscagri.ui.components.CategorySelectionOverlay
import com.cagri.bigboscagri.ui.components.ReminderColorChoices
import com.cagri.bigboscagri.ui.components.ReminderColorPicker
import com.cagri.bigboscagri.ui.components.reminderAccentBrush
import com.cagri.bigboscagri.ui.components.TopGlassOverlay
import com.cagri.bigboscagri.ui.theme.BigBosTheme

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ReminderDetailScreen(
    reminder: ReminderItem, onBack: () -> Unit, onToggleCompleted: () -> Unit,
    onToggleImportant: () -> Unit, onSave: (String, Int?, Int?, Long?) -> Unit, onDelete: () -> Unit,
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
    var selectedCategoryId by rememberSaveable(reminder.id) { mutableStateOf(reminder.customCategoryId) }
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showNotificationPermissionSheet by rememberSaveable { mutableStateOf(false) }
    var pendingDetailDatePicker by rememberSaveable(reminder.id) { mutableStateOf(false) }

    LaunchedEffect(notificationsAllowed) {
        if (notificationsAllowed && pendingDetailDatePicker) {
            showDatePicker = true
            pendingDetailDatePicker = false
        }
    }

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
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
        editing = false
        selectedCategoryId = reminder.customCategoryId
        focusManager.clearFocus()
        keyboard?.hide()
    }
    BackHandler(editing || deleting || showCategoryPicker) {
        if (showCategoryPicker) showCategoryPicker = false
        else if (deleting) deleting = false
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

    Box(Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding().imePadding()) {
        Column(
            Modifier.fillMaxSize()
                .then(if (deleting) Modifier.clearAndSetSemantics { } else Modifier)
                .onGloballyPositioned { scenePosition = it.positionInRoot() }
                .drawWithContent {
                    if (!reduceDynamicBlur && Build.VERSION.SDK_INT >= 31) { scene.record { this@drawWithContent.drawContent() }; drawLayer(scene) }
                    else drawContent()
                }
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = statusHeight + 10.dp,
                    bottom = if (editing) 300.dp else 114.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().graphicsLayer { alpha = chromeAlpha },
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (editing) finishEditing() else onBack() },
                    modifier = Modifier.background(Color(0xFF161619), CircleShape)
                        .semantics { contentDescription = if (editing) "Düzenlemeyi iptal et" else "Geri" }
                ) { ActionIcon(ActionGlyph.Back, Modifier.size(23.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.padding(bottom = 8.dp).size(34.dp, 3.dp)
                        .background(Color(0xFF5D5D65), CircleShape))
                    Text(if (editing) "Düzenle" else "Hatırlatıcı", color = Color(0xFFAAAAB3), fontSize = 13.sp,
                        fontWeight = FontWeight.Medium)
                }
                IconButton(
                    onClick = onToggleImportant,
                    modifier = Modifier.background(Color(0xFF161619), CircleShape)
                        .semantics { contentDescription = if (reminder.isImportant) "Önemli işaretini kaldır" else "Önemli olarak işaretle" }
                ) { ActionIcon(ActionGlyph.Star, Modifier.size(24.dp),
                    if (reminder.isImportant) Color(0xFFEACB75) else Color.White, filled = reminder.isImportant) }
            }

            val cardShape = RoundedCornerShape(28.dp)
            val shownAccentStart = if (editing) accentStart else reminder.accentStart
            val shownAccentEnd = if (editing) accentEnd else reminder.accentEnd
            val accentBrush = remember(shownAccentStart, shownAccentEnd, reminder.isCompleted) {
                reminderAccentBrush(shownAccentStart, shownAccentEnd, reminder.isCompleted)
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

            Column(
                Modifier.padding(top = 24.dp)
                    .then(sharedCardModifier)
                    .fillMaxWidth()
                    .clip(cardShape)
                    .then(
                        if (accentBrush == null) Modifier.background(
                            Brush.linearGradient(listOf(Color(0xFF25252C), Color(0xFF17171B), Color(0xFF131315)))
                        ) else Modifier.background(accentBrush)
                    )
                    .border(0.8.dp, Brush.verticalGradient(listOf(Color(0xFF494953), Color(0xFF252529))), cardShape)
                    .padding(horizontal = 22.dp, vertical = 25.dp)
            ) {
                if (editing) {
                    BasicTextField(
                        value = editedText, onValueChange = { editedText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
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
                            textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else TextDecoration.None
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
                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                        )
                    }
                }
                if (reminder.isChecklist && reminder.checklistItems.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (editing) 16.dp else 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        reminder.checklistItems.forEach { item ->
                            val itemTextColor by animateColorAsState(
                                if (item.isCompleted) Color(0xFF6E6E77) else Color(0xFFF1F1F5),
                                animationSpec = if (reduceAnimations) snap() else tween(200),
                                label = "checkItemColor_${item.id}"
                            )
                            val itemAlpha by animateFloatAsState(
                                if (item.isCompleted) 0.65f else 1.0f,
                                animationSpec = if (reduceAnimations) snap() else tween(200),
                                label = "checkItemAlpha_${item.id}"
                            )
                            val circleBorderColor by animateColorAsState(
                                if (item.isCompleted) Color(0xFF686873) else Color(0xFF5E5E68),
                                animationSpec = if (reduceAnimations) snap() else tween(200),
                                label = "checkCircleBorder_${item.id}"
                            )
                            val circleBgColor by animateColorAsState(
                                if (item.isCompleted) Color(0xFF686873) else Color.Transparent,
                                animationSpec = if (reduceAnimations) snap() else tween(200),
                                label = "checkCircleBg_${item.id}"
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onToggleChecklistItem(reminder.id, item.id)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(circleBgColor, CircleShape)
                                        .border(1.5.dp, circleBorderColor, CircleShape)
                                )
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
            Text(
                reminder.completedAt?.let { "Tamamlandı: ${reminderDate(it)}" }
                    ?: "Son düzenleme: ${reminderDate(reminder.updatedAt)}",
                color = Color(0xFF8E8E98), fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = chromeAlpha }.padding(top = 15.dp, start = 10.dp, end = 10.dp)
            )
        }
        TopGlassOverlay(scene, { scenePosition }, Modifier.align(Alignment.TopCenter), reduceDynamicBlur)
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
                visible = editing,
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
                        onStartChange = { accentStart = it },
                        onEndChange = { accentEnd = it },
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
                visible = deleting,
                enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(180)) + slideInVertically(tween(240)) { it / 8 } + scaleIn(tween(240), initialScale = 0.97f),
                exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 8 } + scaleOut(tween(180), targetScale = 0.97f)
            ) {
                DeleteReminderPrompt(scene, { scenePosition },
                    onDismiss = { deleting = false }, onConfirm = { deleting = false; onDelete() },
                    modifier = Modifier.padding(bottom = 12.dp), designStyle = designStyle, reduceDynamicBlur = reduceDynamicBlur)
            }
            DesignPanel(scene, { scenePosition }, modifier = Modifier.fillMaxWidth(), corners = 36.dp, designStyle = designStyle, reduceDynamicBlur = reduceDynamicBlur) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (editing) {
                        DetailAction("Vazgeç", ActionGlyph.Close, Modifier.weight(1f), onClick = finishEditing)
                        DetailAction("Kaydet", ActionGlyph.Check, Modifier.weight(1f), enabled = editedText.isNotBlank(),
                            color = Color(0xFF91B9FF), onClick = {
                                onSave(editedText.trim(), accentStart, accentEnd, selectedCategoryId)
                                finishEditing()
                            })
                    } else {
                        DetailAction(if (reminder.isCompleted) "Geri al" else "Tamamla",
                            if (reminder.isCompleted) ActionGlyph.Undo else ActionGlyph.Check,
                            Modifier.weight(1f), enabled = !deleting, onClick = onToggleCompleted)
                        DetailAction("Düzenle", ActionGlyph.Edit, Modifier.weight(1f), enabled = !deleting, onClick = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                            editedText = reminder.text
                            accentStart = reminder.accentStart
                            accentEnd = reminder.accentEnd
                            selectedCategoryId = reminder.customCategoryId
                            editing = true
                        })
                        DetailAction("Paylaş", ActionGlyph.Share, Modifier.weight(1f), enabled = !deleting, onClick = {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, reminder.text)
                            }, "Hatırlatıcıyı paylaş"))
                        })
                        DetailAction("Sil", ActionGlyph.Delete, Modifier.weight(1f), onClick = { deleting = !deleting })
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
    }
}

@Composable
private fun DetailAction(
    label: String, glyph: ActionGlyph, modifier: Modifier,
    enabled: Boolean = true, color: Color = Color(0xFFF0F0F4), onClick: () -> Unit
) {
    val tint = if (enabled) color else color.copy(alpha = 0.35f)
    Column(
        modifier.clip(RoundedCornerShape(28.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
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
        ReminderDetailScreen(ReminderItem(1, "Akşam için birkaç küçük not", 1789610400000), {}, {}, {}, { _, _, _, _ -> }, {})
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
