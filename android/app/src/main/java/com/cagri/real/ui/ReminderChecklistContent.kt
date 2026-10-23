package com.cagri.real.ui

import com.cagri.real.i18n.t
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.cagri.real.data.*
import com.cagri.real.ui.components.*
import com.cagri.real.ui.theme.RealType

@Composable
internal fun DetailChecklistBox(
    designStyle: DesignStyle,
    editing: Boolean,
    items: List<ChecklistItem>,
    newItemText: String,
    onNewItemTextChange: (String) -> Unit,
    onAddItem: () -> Unit,
    onItemTextChange: (Long, String) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onToggleItem: (Long) -> Unit,
    onRemoveChecklist: () -> Unit,
    onFocusField: () -> Unit,
    firstItemFocusRequester: FocusRequester,
    reduceAnimations: Boolean,
    savingDetail: Boolean
) {
    val cardShape = RoundedCornerShape(22.dp)
    val total = items.size
    val completedCount = items.count { it.isCompleted }
    val summaryText = if (total > 0) {
        if (completedCount > 0) t("{0} madde • {1} tamamlandı", total, completedCount) else t("{0} madde", total)
    } else ""

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .cardBlockSurface()
            .clip(cardShape)
            .then(
                if (designStyle == DesignStyle.LiquidGlass) Modifier.cardContentChrome(corners = 22.dp)
                else Modifier
                    .background(Color(0xFF141416))
                    .then(if (LocalCardContentComposition.current) Modifier else Modifier.border(0.7.dp, Color.White.copy(alpha = 0.10f), cardShape))
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // Items present when the card opens are shown as-is; only ones added afterwards animate in.
        val initialItemIds = remember { items.map { it.id }.toSet() }
        Column(
            modifier = Modifier.fillMaxWidth()
                .then(if (reduceAnimations) Modifier else Modifier.animateContentSize(RealMotion.smooth(0.3f))),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    t("Checklist"),
                    color = Color(0xFFB7B7C0),
                    style = RealType.detailBlockHeader,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (summaryText.isNotBlank()) {
                    Text(
                        text = if (LocalCardContentComposition.current) "  $completedCount/$total" else "  •  $summaryText",
                        color = Color(0xFFA5A5B5),
                        style = RealType.detailBlockMetaSecondary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (editing) {
                    IconButton(
                        onClick = onRemoveChecklist,
                        enabled = !savingDetail,
                        modifier = Modifier
                            .size(28.dp)
                            .semantics { contentDescription = t("Checklist kutusunu kaldır") }
                    ) {
                        ActionIcon(ActionGlyph.Close, Modifier.size(15.dp), Color(0xFF8E8E98))
                    }
                }
            }

            if (editing) {
                items.forEachIndexed { index, item ->
                    key(item.id) {
                        ChecklistRowAppear(item.id in initialItemIds, reduceAnimations) {
                        EditableChecklistItemRow(
                            item = item,
                            onTextChange = { newText -> onItemTextChange(item.id, newText) },
                            onDelete = { onDeleteItem(item.id) },
                            onToggle = { onToggleItem(item.id) },
                            onFocus = onFocusField,
                            reduceAnimations = reduceAnimations,
                            customFocusRequester = if (index == 0) firstItemFocusRequester else null
                        )
                        }
                    }
                }
                AddChecklistItemRow(
                    text = newItemText,
                    onTextChange = onNewItemTextChange,
                    onFocus = onFocusField,
                    onAdd = onAddItem
                )
            } else {
                items.forEach { item ->
                    key(item.id) {
                        ChecklistRowAppear(item.id in initialItemIds, reduceAnimations) {
                            ChecklistItemRow(
                                item = item,
                                reduceAnimations = reduceAnimations,
                                onToggle = { onToggleItem(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistItemRow(
    item: ChecklistItem,
    reduceAnimations: Boolean,
    onToggle: () -> Unit
) {
    val view = LocalView.current
    val itemTextColor by animateColorAsState(
        if (item.isCompleted) Color(0xFF6E6E77) else Color(0xFFF1F1F5),
        animationSpec = if (reduceAnimations) snap() else tween(180),
        label = "checkItemColor_${item.id}"
    )
    val itemAlpha by animateFloatAsState(
        if (item.isCompleted) 0.65f else 1.0f,
        animationSpec = if (reduceAnimations) snap() else tween(180),
        label = "checkItemAlpha_${item.id}"
    )
    val circleBorderColor by animateColorAsState(
        if (item.isCompleted) Color.Transparent else Color(0xFF5E5E68),
        animationSpec = if (reduceAnimations) snap() else tween(120),
        label = "checkCircleBorder_${item.id}"
    )
    val circleBgColor by animateColorAsState(
        if (item.isCompleted) Color(0xFF686873) else Color.Transparent,
        animationSpec = if (reduceAnimations) snap() else tween(120),
        label = "checkCircleBg_${item.id}"
    )
    val checkItemInteractionSource = remember(item.id) { MutableInteractionSource() }
    val checkSettle = remember(item.id) { Animatable(1f) }
    var hasRenderedInitialCheck by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.isCompleted) {
        if (!hasRenderedInitialCheck) {
            hasRenderedInitialCheck = true
            return@LaunchedEffect
        }
        if (item.isCompleted && !reduceAnimations) {
            checkSettle.snapTo(1.05f)
            checkSettle.animateTo(1.0f, tween(140, easing = RealMotion.Emphasized))
        } else {
            checkSettle.snapTo(1.0f)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pressScale(checkItemInteractionSource, reduceAnimations = reduceAnimations, targetScale = 0.98f)
            .clickable(
                interactionSource = checkItemInteractionSource,
                indication = null /* pressScale already gives the press feedback */,
                onClick = {
                    if (!item.isCompleted) {
                        RealHaptics.toggle(view, on = true)
                    }
                    onToggle()
                }
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    scaleX = checkSettle.value
                    scaleY = checkSettle.value
                }
                .background(circleBgColor, CircleShape)
                .border(1.5.dp, circleBorderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (item.isCompleted) {
                ActionIcon(ActionGlyph.Check, Modifier.size(12.dp), Color(0xFF1B1B1F))
            }
        }
        Text(
            text = item.text,
            color = itemTextColor,
            style = RealType.detailChecklistItemView.copy(
                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
            ),
            modifier = Modifier.weight(1f).graphicsLayer { alpha = itemAlpha }
        )
    }
}

@Composable
private fun EditableChecklistItemRow(
    item: ChecklistItem,
    onTextChange: (String) -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit,
    onFocus: () -> Unit = {},
    reduceAnimations: Boolean,
    customFocusRequester: FocusRequester? = null
) {
    val view = LocalView.current
    val circleBorderColor = if (item.isCompleted) Color.Transparent else Color(0xFF5E5E68)
    val circleBgColor = if (item.isCompleted) Color(0xFF686873) else Color.Transparent
    val defaultFocusRequester = remember { FocusRequester() }
    val focusRequester = customFocusRequester ?: defaultFocusRequester
    val keyboard = LocalSoftwareKeyboardController.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onFocus()
                focusRequester.requestFocus()
                keyboard?.show()
            }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(circleBgColor, CircleShape)
                .border(1.5.dp, circleBorderColor, CircleShape)
                .clickable {
                    if (!item.isCompleted) {
                        RealHaptics.toggle(view, on = true)
                    }
                    onToggle()
                },
            contentAlignment = Alignment.Center
        ) {
            if (item.isCompleted) {
                ActionIcon(ActionGlyph.Check, Modifier.size(12.dp), Color(0xFF1B1B1F))
            }
        }

        BasicTextField(
            value = item.text,
            onValueChange = {
                onFocus()
                onTextChange(it)
            },
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onFocus() }
                .semantics { contentDescription = t("Madde metni") },
            textStyle = RealType.detailChecklistItemEdit.copy(
                color = if (item.isCompleted) Color(0xFF8E8E98) else Color(0xFFF1F1F5),
                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
            ),
            cursorBrush = SolidColor(Color(0xFF82B3FF)),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done)
        )

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(28.dp)
                .semantics { contentDescription = t("Maddeyi sil") }
        ) {
            ActionIcon(ActionGlyph.Close, Modifier.size(16.dp), Color(0xFF8E8E98))
        }
    }
}

@Composable
private fun AddChecklistItemRow(
    text: String,
    onTextChange: (String) -> Unit,
    onFocus: () -> Unit = {},
    onAdd: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onFocus()
                focusRequester.requestFocus()
                keyboard?.show()
            }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(1.5.dp, Color(0xFF5E5E68), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onFocus()
                    focusRequester.requestFocus()
                    keyboard?.show()
                },
            contentAlignment = Alignment.Center
        ) {
            ActionIcon(ActionGlyph.Add, Modifier.size(12.dp), Color(0xFF8E8E98))
        }

        BasicTextField(
            value = text,
            onValueChange = {
                onFocus()
                onTextChange(it)
            },
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onFocus() }
                .semantics { contentDescription = t("Yeni madde ekle") },
            textStyle = RealType.detailChecklistItemEdit.copy(
                color = Color.White
            ),
            cursorBrush = SolidColor(Color(0xFF82B3FF)),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (text.isNotBlank()) onAdd()
                }
            ),
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text(
                        t("Yeni madde ekle..."),
                        color = Color(0xFF6E6E77),
                        style = RealType.composerPrompt
                    )
                }
                innerTextField()
            }
        )

        if (text.isNotBlank()) {
            IconButton(
                onClick = onAdd,
                modifier = Modifier
                    .size(28.dp)
                    .semantics { contentDescription = t("Maddeyi kaydet") }
            ) {
                ActionIcon(ActionGlyph.Check, Modifier.size(16.dp), Color(0xFF91B9FF))
            }
        }
    }
}

/** A newly added checklist row unfolds and fades in; rows that were already there appear immediately. */
@Composable
private fun ChecklistRowAppear(alreadyPresent: Boolean, reduceAnimations: Boolean, content: @Composable () -> Unit) {
    if (alreadyPresent || reduceAnimations) { content(); return }
    val state = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
    androidx.compose.animation.AnimatedVisibility(
        visibleState = state,
        enter = androidx.compose.animation.expandVertically(RealMotion.smooth(0.3f), expandFrom = Alignment.Top) +
            androidx.compose.animation.fadeIn(tween(200, delayMillis = 60))
    ) { content() }
}
