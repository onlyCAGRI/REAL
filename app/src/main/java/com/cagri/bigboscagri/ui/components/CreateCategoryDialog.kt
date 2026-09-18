package com.cagri.bigboscagri.ui.components

import androidx.activity.compose.BackHandler
import com.cagri.bigboscagri.data.CustomCategory
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.cagri.bigboscagri.ui.DesignStyle

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
    val isEditMode = initialCategory != null
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = {
        if (showDeleteConfirm) {
            showDeleteConfirm = false
        } else {
            onDismiss()
        }
    })

    var name by rememberSaveable(initialCategory) { mutableStateOf(initialCategory?.name ?: "") }
    var selectedIcon by rememberSaveable(initialCategory) {
        mutableStateOf(initialCategory?.let { CategoryIcon.fromKey(it.iconKey) } ?: CategoryIcon.WORK)
    }
    var accentStart by rememberSaveable(initialCategory) { mutableIntStateOf(initialCategory?.accentStart ?: 0) }
    var accentEnd by rememberSaveable(initialCategory) { mutableStateOf(initialCategory?.accentEnd) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
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
                .semantics { paneTitle = if (isEditMode) "Kategoriyi Düzenle" else "Yeni Kategori" }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isEditMode) "Kategoriyi Düzenle" else "Yeni Kategori",
                    color = Color(0xFFF5F5F8),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // Live Preview Card
                CategoryPreviewCard(
                    name = name,
                    icon = selectedIcon,
                    accentStart = accentStart,
                    accentEnd = accentEnd
                )

                Spacer(Modifier.height(18.dp))

                // Name Input
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(0.75.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp),
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
                        cursorBrush = SolidColor(Color.White),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(18.dp))

                // Icon Picker Section
                Text(
                    text = "İkon Seç",
                    color = Color(0xFFBEBEC8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                // 4x4 Grid of 16 Icons
                val allIcons = remember { CategoryIcon.entries.toTypedArray() }
                allIcons.toList().chunked(4).forEach { iconRow ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        iconRow.forEach { icon ->
                            val isSelected = icon == selectedIcon
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) Color.White.copy(alpha = 0.18f)
                                        else Color.White.copy(alpha = 0.04f)
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.6.dp,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.09f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable(
                                        role = Role.RadioButton,
                                        onClick = { selectedIcon = icon }
                                    )
                                    .semantics { contentDescription = "İkon: ${icon.label}" },
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

                Spacer(Modifier.height(16.dp))

                // Color Picker Section
                Text(
                    text = "Renk Paleti",
                    color = Color(0xFFBEBEC8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                // Primary Color Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "1. Renk",
                        color = Color(0xFFB0B0BA),
                        fontSize = 11.sp,
                        modifier = Modifier.width(52.dp)
                    )
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(ReminderColorChoices.size, key = { it }) { index ->
                            val choice = ReminderColorChoices[index]
                            val selected = index == accentStart
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(choice.color)
                                    .border(
                                        width = if (selected) 2.dp else 0.7.dp,
                                        color = if (selected) Color.White else Color.White.copy(alpha = 0.16f),
                                        shape = CircleShape
                                    )
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { accentStart = index }
                                    )
                                    .semantics { contentDescription = "1. Renk: ${choice.label}" }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Secondary Color Row (Gradient)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "2. Renk",
                        color = Color(0xFFB0B0BA),
                        fontSize = 11.sp,
                        modifier = Modifier.width(52.dp)
                    )
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item(key = "none") {
                            val selected = accentEnd == null
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) Color(0xFF2D2D36) else Color(0xFF1B1B1F))
                                    .border(
                                        width = if (selected) 2.dp else 0.7.dp,
                                        color = if (selected) Color.White else Color.White.copy(alpha = 0.18f),
                                        shape = CircleShape
                                    )
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { accentEnd = null }
                                    )
                                    .semantics { contentDescription = "2. Renk: Tek renk (Gradient yok)" },
                                contentAlignment = Alignment.Center
                            ) {
                                ActionIcon(
                                    glyph = ActionGlyph.Close,
                                    modifier = Modifier.size(12.dp),
                                    color = if (selected) Color.White else Color(0xFF878792)
                                )
                            }
                        }
                        items(ReminderColorChoices.size, key = { it }) { index ->
                            val choice = ReminderColorChoices[index]
                            val selected = index == accentEnd
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(choice.color)
                                    .border(
                                        width = if (selected) 2.dp else 0.7.dp,
                                        color = if (selected) Color.White else Color.White.copy(alpha = 0.16f),
                                        shape = CircleShape
                                    )
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { accentEnd = index }
                                    )
                                    .semantics { contentDescription = "2. Renk: ${choice.label}" }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

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
                                        onDelete?.invoke()
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
                                .height(46.dp)
                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(23.dp))
                        ) {
                            Text(
                                text = "Vazgeç",
                                color = Color(0xFFE9E9EF),
                                fontSize = 14.sp
                            )
                        }

                        val canSave = name.isNotBlank()
                        TextButton(
                            onClick = {
                                if (canSave) {
                                    keyboardController?.hide()
                                    onSave(name.trim(), selectedIcon.key, accentStart, accentEnd)
                                }
                            },
                            enabled = canSave,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .background(
                                    if (canSave) Color(0xFF2C5696).copy(alpha = 0.85f)
                                    else Color.White.copy(alpha = 0.05f),
                                    RoundedCornerShape(23.dp)
                                )
                        ) {
                            Text(
                                text = if (isEditMode) "Kaydet" else "Oluştur",
                                color = if (canSave) Color.White else Color(0xFF6B6B75),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (isEditMode && onDelete != null) {
                        Spacer(Modifier.height(12.dp))
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                        ) {
                            ActionIcon(
                                glyph = ActionGlyph.Delete,
                                modifier = Modifier.size(16.dp),
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

@Composable
private fun CategoryPreviewCard(
    name: String,
    icon: CategoryIcon,
    accentStart: Int,
    accentEnd: Int?
) {
    val shape = RoundedCornerShape(18.dp)
    val startColor = ReminderColorChoices.getOrNull(accentStart)?.color ?: Color(0xFF233F73)
    val endColor = accentEnd?.let { ReminderColorChoices.getOrNull(it)?.color } ?: startColor

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
                        colors = listOf(startColor.copy(alpha = 0.85f), Color.Transparent),
                        center = Offset(size.width * 0.95f, -size.height * 0.1f),
                        radius = size.width * 1.12f
                    )
                    val bottomLight = Brush.radialGradient(
                        colors = listOf(endColor.copy(alpha = 0.70f), Color.Transparent),
                        center = Offset(-size.width * 0.05f, size.height * 1.1f),
                        radius = size.width * 1.15f
                    )
                    onDrawBehind {
                        drawRect(Color(0xFF14151C))
                        drawRect(topLight)
                        drawRect(bottomLight)
                    }
                }
                .border(0.75.dp, Color.White.copy(alpha = 0.12f), shape),
            contentAlignment = Alignment.Center
        ) {
            CategoryIconGlyph(
                icon = icon,
                modifier = Modifier.size(32.dp),
                color = Color.White
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = if (name.isNotBlank()) name else "Önizleme",
            color = if (name.isNotBlank()) Color(0xFFE7E7EB) else Color(0xFF7A7A85),
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
