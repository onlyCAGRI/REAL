package com.cagri.real.ui.components

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.unit.IntOffset
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.CategoryColorOverride
import com.cagri.real.data.CustomCategory
import com.cagri.real.data.ReminderCategory
import com.cagri.real.data.DEFAULT_GRADIENT_BLEND_FRACTION
import com.cagri.real.data.resolveGradientBlendFraction
import com.cagri.real.ui.DesignStyle
import kotlinx.coroutines.launch

const val DEFAULT_NEW_CATEGORY_COLOR_INDEX = 2 // Royal Mavi (Color(0xFF4558C8))

@OptIn(ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun CategoryEditor(
    builtInCategory: ReminderCategory? = null,
    customCategory: CustomCategory? = null,
    builtInColorOverride: CategoryColorOverride? = null,
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    onDismiss: () -> Unit,
    onSaveCustomCategory: ((name: String, iconKey: String, accentStart: Int, accentEnd: Int?, hideFromGeneralLists: Boolean, useDarkText: Boolean, gradientBlendFraction: Float?, customSolidColorArgb: Int?, description: String?) -> Unit)? = null,
    onSaveBuiltInColor: ((category: ReminderCategory, override: CategoryColorOverride?) -> Unit)? = null,
    onDeleteCustomCategory: ((deleteReminders: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    designStyle: DesignStyle = DesignStyle.Glass,
    reduceDynamicBlur: Boolean = false,
    reduceAnimations: Boolean = false
) {
    val proAction = com.cagri.real.ui.subscription.rememberProAction(com.cagri.real.access.RealFeature.CategoryGradients)
    val descriptionContext = LocalContext.current
    val descriptionPreferences = remember(descriptionContext) {
        com.cagri.real.platform.android.sync.SyncLocal.preferences(descriptionContext, "real_category_descriptions", android.content.Context.MODE_PRIVATE)
    }
    val descriptionKey = categoryDescriptionKey(builtInCategory, customCategory)
    // Someone else's shared category (full or limited member): only the owner's description (read only) and this
    // phone's "Yalnızca kategori içinde göster" switch. Name, colour, icon and delete stay with the owner.
    val memberMode = customCategory?.isSharedMirror == true
    var description by rememberSaveable(descriptionKey, memberMode) {
        mutableStateOf(
            if (memberMode) customCategory?.sharedDescription.orEmpty()
            else limitCategoryDescription(descriptionKey?.let { descriptionPreferences.getString(it, "") }.orEmpty())
        )
    }
    val (plannerPreferences, _) = rememberCategoryPlannerPreferences()
    val plannerKey = categoryPlannerKey(builtInCategory, customCategory)
    var visibleInCalendar by rememberSaveable(plannerKey) { mutableStateOf(plannerPreferences.visible(plannerKey)) }
    val isBuiltIn = builtInCategory != null
    val isCustomEdit = customCategory != null
    val title = when {
        isBuiltIn -> "${builtInCategory!!.title} Düzenle"
        isCustomEdit -> "Kategoriyi Düzenle"
        else -> "Yeni Kategori"
    }

    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var deleteRemindersOption by rememberSaveable { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    var isClosing by remember { mutableStateOf(false) }
    val animProgress = remember { Animatable(if (reduceAnimations) 1f else 0f) }

    LaunchedEffect(reduceAnimations) {
        if (!reduceAnimations && animProgress.value < 1f) {
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 230,
                    easing = RealMotion.Emphasized
                )
            )
        }
    }

    val requestDismiss: () -> Unit = {
        if (!isClosing) {
            isClosing = true
            keyboardController?.hide()
            if (reduceAnimations) {
                onDismiss()
            } else {
                coroutineScope.launch {
                    animProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = 160,
                            easing = RealMotion.Exit
                        )
                    )
                    onDismiss()
                }
            }
        }
    }

    BackHandler(onBack = {
        if (showDeleteConfirm) {
            showDeleteConfirm = false
        } else {
            requestDismiss()
        }
    })

    var name by rememberSaveable(customCategory) { mutableStateOf(customCategory?.name ?: "") }
    var hideFromGeneralLists by rememberSaveable(customCategory) { mutableStateOf(customCategory?.hideFromGeneralLists ?: false) }
    var selectedIcon by rememberSaveable(customCategory) {
        mutableStateOf(customCategory?.let { CategoryIcon.fromKey(it.iconKey) } ?: CategoryIcon.NONE)
    }
    var selectedColor1 by rememberSaveable(builtInCategory, customCategory, builtInColorOverride) {
        mutableStateOf<Int?>(if (isBuiltIn) builtInColorOverride?.start else (customCategory?.accentStart ?: DEFAULT_NEW_CATEGORY_COLOR_INDEX))
    }
    var selectedColor2 by rememberSaveable(builtInCategory, customCategory, builtInColorOverride) {
        mutableStateOf<Int?>(if (isBuiltIn) builtInColorOverride?.end else customCategory?.accentEnd)
    }
    var gradientBlendFraction by rememberSaveable(builtInCategory, customCategory, builtInColorOverride) {
        mutableStateOf<Float?>(
            if (isBuiltIn) builtInColorOverride?.gradientBlendFraction
            else customCategory?.gradientBlendFraction
        )
    }
    var customSolidColorArgb by rememberSaveable(builtInCategory, customCategory, builtInColorOverride) {
        mutableStateOf<Int?>(
            if (isBuiltIn) builtInColorOverride?.customSolidColorArgb
            else customCategory?.customSolidColorArgb
        )
    }

    var useDarkText by rememberSaveable(builtInCategory, customCategory, builtInColorOverride) {
        mutableStateOf(
            if (isBuiltIn) builtInColorOverride?.useDarkText ?: (builtInCategory?.defaultStyle()?.useDarkText == true)
            else customCategory?.useDarkText == true
        )
    }

    val canSave = if (isBuiltIn) true else (name.isNotBlank() && selectedColor1 != null)

    val scrollState = rememberScrollState()

    val progress = animProgress.value
    val scale = 0.965f + 0.035f * progress
    val translationY = 9.dp * (1f - progress)
    val panelAlpha = progress

    val saveAction: () -> Unit = {
        if (canSave) {
            plannerKey?.let { plannerPreferences.setVisible(it, visibleInCalendar) }
            val trimmedDescription = limitCategoryDescription(description).trim().takeIf { it.isNotEmpty() }
            if (!memberMode) descriptionKey?.let { key ->
                descriptionPreferences.edit().apply {
                    if (trimmedDescription == null) remove(key) else putString(key, trimmedDescription)
                }.apply()
            }
            keyboardController?.hide()
            if (isBuiltIn) {
                val override = if (
                    selectedColor1 != null ||
                    useDarkText ||
                    customSolidColorArgb != null
                ) {
                    CategoryColorOverride(
                        start = selectedColor1,
                        end = selectedColor2,
                        useDarkText = useDarkText,
                        gradientBlendFraction = if (selectedColor1 != null && selectedColor2 != null) gradientBlendFraction else null,
                        customSolidColorArgb = customSolidColorArgb
                    )
                } else {
                    null
                }
                onSaveBuiltInColor?.invoke(builtInCategory!!, override)
            } else {
                val startIdx = selectedColor1 ?: DEFAULT_NEW_CATEGORY_COLOR_INDEX
                onSaveCustomCategory?.invoke(
                    name.trim(),
                    selectedIcon.key,
                    startIdx,
                    selectedColor2,
                    hideFromGeneralLists,
                    useDarkText,
                    if (selectedColor2 != null) gradientBlendFraction else null,
                    customSolidColorArgb,
                    trimmedDescription
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.70f * progress))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { requestDismiss() }
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        // A bottom sheet like the notification / SYNC question: it slides up from the bottom edge.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .imePadding()
                .graphicsLayer { this.translationY = size.height * (1f - progress) }
                .realSheetSurface()
                .navigationBarsPadding()
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .semantics { paneTitle = title }
        ) {
            Column(Modifier.fillMaxWidth()) {
                RealSheetHandle(Modifier.align(Alignment.CenterHorizontally))
                EditorTopBar(
                    title = title,
                    saveLabel = if (isCustomEdit || isBuiltIn) "Kaydet" else "Oluştur",
                    canSave = canSave,
                    onCancel = { requestDismiss() },
                    onSave = saveAction
                )

                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // Production Collections card as the live preview for both edit AND create.
                    val previewCustomCategory = if (!isBuiltIn) {
                        CustomCategory(
                            id = customCategory?.id ?: Long.MIN_VALUE,
                            name = name.ifBlank { "Yeni kategori" },
                            iconKey = selectedIcon.key,
                            accentStart = selectedColor1 ?: DEFAULT_NEW_CATEGORY_COLOR_INDEX,
                            accentEnd = selectedColor2,
                            hideFromGeneralLists = hideFromGeneralLists,
                            useDarkText = useDarkText,
                            gradientBlendFraction = gradientBlendFraction,
                            customSolidColorArgb = customSolidColorArgb,
                            createdAt = customCategory?.createdAt ?: 0L
                        )
                    } else null

                    CollectionCategoryCard(
                        category = builtInCategory,
                        customCategory = previewCustomCategory,
                        override = if (isBuiltIn) {
                            CategoryColorOverride(
                                start = selectedColor1,
                                end = selectedColor2,
                                useDarkText = useDarkText,
                                gradientBlendFraction = gradientBlendFraction,
                                customSolidColorArgb = customSolidColorArgb
                            )
                        } else null,
                        count = 0,
                        showCount = false,
                        descriptionPreview = description,
                        reduceAnimations = reduceAnimations
                    )

                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState)
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isBuiltIn && !memberMode) {
                        EditorSection(title = "AD") {
                            EditorGroup {
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                        if (name.isEmpty()) {
                                            Text("Kategori adı", color = Color(0xFF6F6F78), fontSize = 16.sp)
                                        }
                                        BasicTextField(
                                            value = name,
                                            onValueChange = { if (it.length <= 20) name = it },
                                            singleLine = true,
                                            cursorBrush = SolidColor(EditorBlue),
                                            textStyle = TextStyle(
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .semantics { contentDescription = "Kategori adı" }
                                        )
                                    }
                                    Text("${name.length}/20", color = Color(0xFF6F6F78), fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (memberMode) EditorSection(
                        title = "AÇIKLAMA",
                        footer = "Açıklamayı yalnızca kategori sahibi değiştirebilir."
                    ) {
                        EditorGroup {
                            Text(
                                description.ifEmpty { "Kategori sahibi henüz açıklama yazmadı." },
                                color = if (description.isEmpty()) Color(0xFF6F6F78) else Color(0xFFF3F3F5),
                                fontSize = 15.sp, lineHeight = 20.sp,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
                            )
                        }
                    } else EditorSection(
                        title = "AÇIKLAMA",
                        footer = "Boş bırakırsan açıklama kutusu kaldırılır · ${categoryDescriptionWordCount(description)}/30 kelime"
                    ) {
                        EditorGroup {
                            Box(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 16.dp, vertical = 14.dp)) {
                                if (description.isEmpty()) {
                                    Text("Bu kategori ne için?", color = Color(0xFF6F6F78), fontSize = 15.sp)
                                }
                                BasicTextField(
                                    value = description,
                                    onValueChange = { description = limitCategoryDescription(it) },
                                    maxLines = 3,
                                    cursorBrush = SolidColor(EditorBlue),
                                    textStyle = TextStyle(color = Color(0xFFF3F3F5), fontSize = 15.sp, lineHeight = 20.sp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics { contentDescription = "Açıklama" }
                                )
                            }
                        }
                    }
                    }

                    if (!memberMode) EditorSection(title = "RENK") {
                        EditorGroup {
                            val isColor2Enabled = !(isBuiltIn && selectedColor1 == null)

                            CategoryEditorColorRow(
                                label = "Ana renk",
                                selectedIndex = selectedColor1,
                                allowNone = isBuiltIn,
                                noneGlyph = ActionGlyph.Undo,
                                enabled = true,
                                onSelected = { index ->
                                    val change: () -> Unit = {
                                        selectedColor1 = index
                                        customSolidColorArgb = null
                                        if (isBuiltIn && index == null) {
                                            selectedColor2 = null
                                            gradientBlendFraction = null
                                        }
                                    }
                                    if (selectedColor2 != null && index != null) proAction(change) else change()
                                }
                            )
                            EditorDivider()
                            CategoryEditorColorRow(
                                label = "Geçiş rengi",
                                selectedIndex = selectedColor2,
                                allowNone = true,
                                noneGlyph = ActionGlyph.Close,
                                enabled = isColor2Enabled,
                                onSelected = {
                                    val value = it
                                    val change: () -> Unit = {
                                        selectedColor2 = value
                                        customSolidColorArgb = null
                                        if (value == null) gradientBlendFraction = null
                                    }
                                    if (value == null) change() else proAction(change)
                                }
                            )

                            val transitionStart = selectedColor1?.let { ReminderColorChoices.getOrNull(it)?.color }
                            val transitionEnd = selectedColor2?.let { ReminderColorChoices.getOrNull(it)?.color }
                            if (transitionStart != null && transitionEnd != null) {
                                EditorDivider()
                                val fraction = resolveGradientBlendFraction(gradientBlendFraction)
                                val bridgeColor = remember(transitionStart, transitionEnd, fraction) {
                                    lerp(transitionStart, transitionEnd, fraction)
                                }
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Geçiş",
                                            color = Color(0xFFF5F5F8),
                                            fontSize = 15.sp,
                                            modifier = Modifier.width(72.dp)
                                        )
                                        ContinuousGradientTransitionBar(
                                            fraction = fraction,
                                            onFractionChange = {
                                                val value = it
                                                proAction { gradientBlendFraction = value; customSolidColorArgb = null }
                                            },
                                            startColor = transitionStart,
                                            endColor = transitionEnd,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .border(0.7.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                                            .clickable(role = Role.Button) {
                                                proAction {
                                                    customSolidColorArgb = bridgeColor.toArgb()
                                                    selectedColor2 = null
                                                    gradientBlendFraction = DEFAULT_GRADIENT_BLEND_FRACTION
                                                }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(bridgeColor)
                                                .border(0.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                        )
                                        Text(
                                            text = "Geçiş rengini kullan",
                                            color = Color(0xFFE2E2EA),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!isBuiltIn && !memberMode) {
                        EditorSection(title = "İKON") {
                            EditorGroup {
                                val selectableIcons = remember { CategoryIcon.selectableEntries }
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    selectableIcons.forEach { icon ->
                                        val isSelected = icon == selectedIcon
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(
                                                    if (isSelected) Color.White.copy(alpha = 0.18f)
                                                    else Color.White.copy(alpha = 0.05f)
                                                )
                                                .border(
                                                    width = if (isSelected) 1.5.dp else 0.6.dp,
                                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.08f),
                                                    shape = RoundedCornerShape(14.dp)
                                                )
                                                .selectable(
                                                    selected = isSelected,
                                                    role = Role.RadioButton,
                                                    onClick = { selectedIcon = icon }
                                                )
                                                .semantics {
                                                    contentDescription = if (isSelected) "İkon: ${icon.label}, seçili" else "İkon: ${icon.label}"
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (icon == CategoryIcon.NONE) {
                                                // "No icon": an empty ring with a slash.
                                                androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
                                                    val c = if (isSelected) Color.White else Color(0xFFB0B0BA)
                                                    val w = 1.8.dp.toPx()
                                                    drawCircle(c, size.minDimension / 2f - w, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                                                    drawLine(c, androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height * 0.75f),
                                                        androidx.compose.ui.geometry.Offset(size.width * 0.75f, size.height * 0.25f), w)
                                                }
                                            } else CategoryIconGlyph(
                                                icon = icon,
                                                modifier = Modifier.size(22.dp),
                                                color = if (isSelected) Color.White else Color(0xFFB0B0BA)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Text on the category colour is now black or white automatically (categoryPalette), so the
                    // manual "Koyu renk metin" switch is gone.
                    // "REAL takviminde göster" is gone: the category screen has its own goals / calendar button now.
                    if (!isBuiltIn) EditorSection(title = "GÖRÜNÜM") {
                        EditorGroup {
                            if (!isBuiltIn) {
                                EditorSwitchRow(
                                    title = "Yalnızca kategori içinde göster",
                                    subtitle = "Kartlar genel listelerde görünmez.",
                                    checked = hideFromGeneralLists,
                                    onCheckedChange = { hideFromGeneralLists = it }
                                )
                            }
                        }
                    }

                    if (isCustomEdit && onDeleteCustomCategory != null && !memberMode) {
                        EditorGroup {
                            if (showDeleteConfirm) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Bu kategoriyi silmek istiyor musun?",
                                        color = Color(0xFFF5F5F8),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    EditorSwitchRow(
                                        title = "Kategori içindeki kutucukları da sil",
                                        checked = deleteRemindersOption,
                                        onCheckedChange = { deleteRemindersOption = it },
                                        tint = Color(0xFFFF6B73)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f).height(46.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.08f))
                                                .clickable(role = Role.Button) { showDeleteConfirm = false },
                                            contentAlignment = Alignment.Center
                                        ) { Text("Vazgeç", color = Color(0xFFE9E9EF), fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f).height(46.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFFF6B73).copy(alpha = 0.22f))
                                                .clickable(role = Role.Button) {
                                                    showDeleteConfirm = false
                                                    keyboardController?.hide()
                                                    onDeleteCustomCategory.invoke(deleteRemindersOption)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) { Text("Sil", color = Color(0xFFFF9AA0), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(role = Role.Button) { showDeleteConfirm = true }
                                        .padding(vertical = 15.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    ActionIcon(
                                        glyph = ActionGlyph.Delete,
                                        modifier = Modifier.size(16.dp),
                                        color = Color(0xFFFF9AA0)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Kategoriyi sil",
                                        color = Color(0xFFFF9AA0),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Apple-style editor building blocks (private to this file) ----------

private val EditorBlue = Color(0xFF82B3FF)
private val EditorFill = Color.White.copy(alpha = 0.06f)
private val EditorStroke = Color.White.copy(alpha = 0.10f)
private val EditorMuted = Color(0xFF9A9AA2)
private val EditorGreen = Color(0xFF34C759)

@Composable
private fun EditorTopBar(title: String, saveLabel: String, canSave: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.heightIn(min = 40.dp).clip(CircleShape)
                .clickable(role = Role.Button, onClick = onCancel)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) { Text("Vazgeç", color = EditorBlue, fontSize = 16.sp) }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = Color(0xFFF5F5F8),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            Modifier.height(36.dp).clip(CircleShape)
                .background(if (canSave) Color(0xFF2C5696).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.06f))
                .then(if (canSave) Modifier.clickable(role = Role.Button, onClick = onSave) else Modifier)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                saveLabel,
                color = if (canSave) Color.White else Color(0xFF6B6B75),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EditorSection(title: String?, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                title,
                color = EditorMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
        content()
        if (footer != null) {
            Text(footer, color = Color(0xFF7A7A85), fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun EditorGroup(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(EditorFill).border(0.75.dp, EditorStroke, shape),
        content = content
    )
}

@Composable
private fun EditorDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(EditorStroke))
}

@Composable
private fun RealSwitch(checked: Boolean, tint: Color = EditorGreen) {
    val track by animateColorAsState(
        if (checked) tint else Color.White.copy(alpha = 0.16f),
        tween(180), label = "editorSwitchTrack"
    )
    val knobOffset = animateDpAsState(if (checked) 20.dp else 0.dp, tween(180), label = "editorSwitchKnob")
    Box(Modifier.size(width = 51.dp, height = 31.dp).clip(CircleShape).background(track).padding(2.dp)) {
        Box(Modifier.offset { IntOffset(knobOffset.value.roundToPx(), 0) }.size(27.dp).shadow(2.dp, CircleShape).background(Color.White, CircleShape))
    }
}

@Composable
private fun EditorSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tint: Color = EditorGreen
) {
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color(0xFFF5F5F8), fontSize = 15.sp)
            if (subtitle != null) {
                Text(subtitle, color = EditorMuted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        RealSwitch(checked, tint)
    }
}

@Composable
private fun CategoryEditorColorRow(
    label: String,
    selectedIndex: Int?,
    allowNone: Boolean,
    noneGlyph: ActionGlyph,
    enabled: Boolean,
    onSelected: (Int?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFFB7B7C0),
            fontSize = 11.sp,
            modifier = Modifier.width(72.dp)
        )

        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (allowNone) {
                item(key = "${label}_none") {
                    val selected = selectedIndex == null
                    Box(
                        modifier = Modifier
                            .size(31.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color(0xFF2D2D36) else Color(0xFF1B1B1F))
                            .border(
                                width = if (selected) 2.2.dp else 0.8.dp,
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.18f),
                                shape = CircleShape
                            )
                            .then(
                                if (enabled) {
                                    Modifier.selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { onSelected(null) }
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .semantics {
                                contentDescription = if (selected) {
                                    "$label: Varsayılan, seçili"
                                } else {
                                    "$label: Varsayılan"
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        ActionIcon(
                            glyph = noneGlyph,
                            modifier = Modifier.size(13.dp),
                            color = if (selected) Color.White else Color(0xFF878792)
                        )
                    }
                }
            }

            items(ReminderColorDisplayOrder.size, key = { "${label}_${ReminderColorDisplayOrder[it]}" }) { position ->
                val index = ReminderColorDisplayOrder[position]
                val choice = ReminderColorChoices[index]
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .size(31.dp)
                        .clip(CircleShape)
                        .background(choice.color)
                        .border(
                            width = if (selected) 2.2.dp else 0.8.dp,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.16f),
                            shape = CircleShape
                        )
                        .then(
                            if (enabled) {
                                Modifier.selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { onSelected(index) }
                                )
                            } else {
                                Modifier
                            }
                        )
                        .semantics {
                            contentDescription = if (selected) {
                                "$label: ${choice.label}, seçili"
                            } else {
                                "$label: ${choice.label}"
                            }
                        }
                )
            }
        }
    }
}
