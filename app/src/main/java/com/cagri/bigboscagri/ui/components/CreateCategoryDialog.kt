package com.cagri.bigboscagri.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.cagri.bigboscagri.data.CustomCategory
import com.cagri.bigboscagri.data.ReminderCategory
import com.cagri.bigboscagri.ui.DesignStyle

@Composable
fun CategoryEditor(
    builtInCategory: ReminderCategory? = null,
    customCategory: CustomCategory? = null,
    builtInColorIndex: Int? = null,
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    onDismiss: () -> Unit,
    onSaveCustomCategory: ((name: String, iconKey: String, accentStart: Int, accentEnd: Int?) -> Unit)? = null,
    onSaveBuiltInColor: ((category: ReminderCategory, colorIndex: Int?) -> Unit)? = null,
    onDeleteCustomCategory: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    designStyle: DesignStyle = DesignStyle.Glass,
    reduceDynamicBlur: Boolean = false,
    reduceAnimations: Boolean = false
) {
    val isBuiltIn = builtInCategory != null
    val isCustomEdit = customCategory != null
    val title = when {
        isBuiltIn -> "${builtInCategory!!.title} Rengini Düzenle"
        isCustomEdit -> "Kategoriyi Düzenle"
        else -> "Yeni Kategori"
    }

    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = {
        if (showDeleteConfirm) {
            showDeleteConfirm = false
        } else {
            onDismiss()
        }
    })

    var name by rememberSaveable(customCategory) { mutableStateOf(customCategory?.name ?: "") }
    var selectedIcon by rememberSaveable(customCategory) {
        mutableStateOf(customCategory?.let { CategoryIcon.fromKey(it.iconKey) } ?: CategoryIcon.WORK)
    }
    var selectedColorIndex by rememberSaveable(builtInCategory, customCategory, builtInColorIndex) {
        mutableStateOf<Int?>(if (isBuiltIn) builtInColorIndex else customCategory?.accentStart)
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()

    AnimatedVisibility(
        visible = true,
        enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(160)) + scaleIn(tween(180), initialScale = 0.96f),
        exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.96f)
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.70f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        keyboardController?.hide()
                        onDismiss()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            DesignPanel(
                backdrop = backdrop,
                backdropPosition = backdropPosition,
                corners = 28.dp,
                designStyle = designStyle,
                reduceDynamicBlur = reduceDynamicBlur,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 620.dp)
                    .imePadding()
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                    .semantics { paneTitle = title }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 22.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = title,
                        color = Color(0xFFF5F5F8),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 1. Live Category Preview
                    LiveCategoryPreview(
                        builtInCategory = builtInCategory,
                        customName = name,
                        selectedIcon = selectedIcon,
                        selectedColorIndex = selectedColorIndex
                    )

                    // 2. Category Name Input (Custom categories only)
                    if (!isBuiltIn) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(23.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(0.75.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(23.dp))
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (name.isEmpty()) {
                                Text(
                                    text = "Kategori adı",
                                    color = Color(0xFF888894),
                                    fontSize = 15.sp
                                )
                            }
                            BasicTextField(
                                value = name,
                                onValueChange = { if (it.length <= 20) name = it },
                                singleLine = true,
                                cursorBrush = SolidColor(Color(0xFF82B3FF)),
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = "Kategori adı" }
                            )
                        }
                    }

                    // 3. Color Palette
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Renk",
                            color = Color(0xFFBEBEC8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth()
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // "Varsayılan" option
                            item(key = "default") {
                                val isSelected = (selectedColorIndex == null)
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .selectable(
                                            selected = isSelected,
                                            role = Role.RadioButton,
                                            onClick = { selectedColorIndex = null }
                                        )
                                        .semantics {
                                            contentDescription = if (isSelected) "Varsayılan renk, seçili" else "Varsayılan renk"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1E1E24))
                                            .border(
                                                width = if (isSelected) 1.5.dp else 0.75.dp,
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.20f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            ActionIcon(ActionGlyph.Check, Modifier.size(13.dp), Color.White)
                                        } else {
                                            ActionIcon(ActionGlyph.Undo, Modifier.size(13.dp), Color(0xFF8E8E98))
                                        }
                                    }
                                }
                            }

                            // Color swatches from ReminderColorChoices
                            items(ReminderColorChoices.size, key = { it }) { index ->
                                val choice = ReminderColorChoices[index]
                                val isSelected = (selectedColorIndex == index)
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .selectable(
                                            selected = isSelected,
                                            role = Role.RadioButton,
                                            onClick = { selectedColorIndex = index }
                                        )
                                        .semantics {
                                            contentDescription = if (isSelected) "${choice.label}, seçili" else choice.label
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(choice.color)
                                            .border(
                                                width = if (isSelected) 1.5.dp else 0.6.dp,
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.16f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            ActionIcon(ActionGlyph.Check, Modifier.size(13.dp), Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Icon Selector (Custom categories only)
                    if (!isBuiltIn) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "İkon",
                                color = Color(0xFFBEBEC8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth()
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(CategoryIcon.entries.size, key = { CategoryIcon.entries[it].key }) { index ->
                                    val icon = CategoryIcon.entries[index]
                                    val isSelected = (icon == selectedIcon)
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                if (isSelected) Color.White.copy(alpha = 0.16f)
                                                else Color.White.copy(alpha = 0.04f)
                                            )
                                            .border(
                                                width = if (isSelected) 1.5.dp else 0.6.dp,
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            .clickable(
                                                role = Role.RadioButton,
                                                onClick = { selectedIcon = icon }
                                            )
                                            .semantics {
                                                contentDescription = if (isSelected) "İkon: ${icon.label}, seçili" else "İkon: ${icon.label}"
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CategoryIconGlyph(
                                            icon = icon,
                                            modifier = Modifier.size(22.dp),
                                            color = if (isSelected) Color.White else Color(0xFFB0B0BA)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. Action Buttons
                    if (showDeleteConfirm) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF221618).copy(alpha = 0.95f))
                                .border(0.8.dp, Color(0xFFFF858D).copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Bu kategoriyi silmek istiyor musun?",
                                    color = Color(0xFFF5F5F8),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    TextButton(
                                        onClick = { showDeleteConfirm = false },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(21.dp))
                                    ) {
                                        Text(text = "Vazgeç", color = Color(0xFFE9E9EF), fontSize = 13.sp)
                                    }
                                    TextButton(
                                        onClick = {
                                            showDeleteConfirm = false
                                            keyboardController?.hide()
                                            onDeleteCustomCategory?.invoke()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .background(Color(0xFFFF858D).copy(alpha = 0.18f), RoundedCornerShape(21.dp))
                                    ) {
                                        Text(text = "Sil", color = Color(0xFFFFA4AA), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    keyboardController?.hide()
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                            ) {
                                Text(
                                    text = "Vazgeç",
                                    color = Color(0xFFE9E9EF),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            val canSave = if (isBuiltIn) true else name.isNotBlank()
                            TextButton(
                                onClick = {
                                    if (canSave) {
                                        keyboardController?.hide()
                                        if (isBuiltIn) {
                                            onSaveBuiltInColor?.invoke(builtInCategory!!, selectedColorIndex)
                                        } else {
                                            val startIdx = selectedColorIndex ?: 0
                                            onSaveCustomCategory?.invoke(name.trim(), selectedIcon.key, startIdx, null)
                                        }
                                    }
                                },
                                enabled = canSave,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .background(
                                        if (canSave) Color(0xFF2C5696).copy(alpha = 0.85f)
                                        else Color.White.copy(alpha = 0.04f),
                                        RoundedCornerShape(22.dp)
                                    )
                            ) {
                                Text(
                                    text = if (isCustomEdit || isBuiltIn) "Kaydet" else "Oluştur",
                                    color = if (canSave) Color.White else Color(0xFF6B6B75),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (isCustomEdit && onDeleteCustomCategory != null) {
                            TextButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                ActionIcon(
                                    glyph = ActionGlyph.Delete,
                                    modifier = Modifier.size(15.dp),
                                    color = Color(0xFFFFA4AA)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Kategoriyi sil",
                                    color = Color(0xFFFFA4AA),
                                    fontSize = 13.sp,
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

@Composable
private fun LiveCategoryPreview(
    builtInCategory: ReminderCategory?,
    customName: String,
    selectedIcon: CategoryIcon,
    selectedColorIndex: Int?
) {
    val shape = RoundedCornerShape(18.dp)

    val (baseColor, upperColor, lowerColor) = when {
        builtInCategory != null && selectedColorIndex == null -> {
            val style = builtInCategory.defaultStyle()
            Triple(style.base, style.upper, style.lower)
        }
        selectedColorIndex != null -> {
            val c = ReminderColorChoices.getOrNull(selectedColorIndex)?.color ?: Color(0xFF233F73)
            Triple(Color(0xFF14151C), c.copy(alpha = 0.85f), c.copy(alpha = 0.60f))
        }
        else -> {
            val c = ReminderColorChoices[0].color
            Triple(Color(0xFF14151C), c.copy(alpha = 0.85f), c.copy(alpha = 0.60f))
        }
    }

    val displayName = when {
        builtInCategory != null -> builtInCategory.title
        customName.isNotBlank() -> customName
        else -> "Önizleme"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(104.dp)
    ) {
        Box(
            modifier = Modifier
                .width(104.dp)
                .height(82.dp)
                .clip(shape)
                .drawWithCache {
                    val topLight = Brush.radialGradient(
                        colors = listOf(upperColor, Color.Transparent),
                        center = Offset(size.width * 0.95f, -size.height * 0.1f),
                        radius = size.width * 1.12f
                    )
                    val bottomLight = Brush.radialGradient(
                        colors = listOf(lowerColor, Color.Transparent),
                        center = Offset(-size.width * 0.05f, size.height * 1.1f),
                        radius = size.width * 1.15f
                    )
                    onDrawBehind {
                        drawRect(baseColor)
                        drawRect(topLight)
                        drawRect(bottomLight)
                    }
                }
                .border(0.75.dp, Color.White.copy(alpha = 0.12f), shape),
            contentAlignment = Alignment.Center
        ) {
            if (builtInCategory != null) {
                CategoryGlyph(
                    glyph = builtInCategory.glyph(),
                    modifier = Modifier.size(32.dp)
                )
            } else {
                CategoryIconGlyph(
                    icon = selectedIcon,
                    modifier = Modifier.size(32.dp),
                    color = Color.White
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = displayName,
            color = if (builtInCategory != null || customName.isNotBlank()) Color(0xFFE7E7EB) else Color(0xFF7A7A85),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CreateCategoryDialog(
    initialCategory: CustomCategory? = null,
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    onDismiss: () -> Unit,
    onSave: (name: String, iconKey: String, accentStart: Int, accentEnd: Int?) -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    designStyle: DesignStyle = DesignStyle.Glass,
    reduceDynamicBlur: Boolean = false,
    reduceAnimations: Boolean = false,
    onCreate: (name: String, iconKey: String, accentStart: Int, accentEnd: Int?) -> Unit = onSave
) {
    CategoryEditor(
        customCategory = initialCategory,
        backdrop = backdrop,
        backdropPosition = backdropPosition,
        onDismiss = onDismiss,
        onSaveCustomCategory = onSave,
        onDeleteCustomCategory = onDelete,
        modifier = modifier,
        designStyle = designStyle,
        reduceDynamicBlur = reduceDynamicBlur,
        reduceAnimations = reduceAnimations
    )
}
