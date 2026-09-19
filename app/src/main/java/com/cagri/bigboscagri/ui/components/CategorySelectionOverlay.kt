package com.cagri.bigboscagri.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.bigboscagri.data.CustomCategory
import com.cagri.bigboscagri.ui.DesignStyle

@Composable
fun CategorySelectionOverlay(
    visible: Boolean,
    customCategories: List<CustomCategory>,
    selectedCategoryId: Long?,
    onSelectCategory: (Long?) -> Unit,
    onDismiss: () -> Unit,
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    modifier: Modifier = Modifier,
    designStyle: DesignStyle = DesignStyle.Glass,
    reduceDynamicBlur: Boolean = false,
    reduceAnimations: Boolean = false
) {
    if (visible) {
        BackHandler(onBack = onDismiss)
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(180)) + slideInVertically(tween(240)) { it / 6 },
        exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 6 },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.BottomCenter
        ) {
            DesignPanel(
                backdrop = backdrop,
                backdropPosition = backdropPosition,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
                    .pointerInput(Unit) { detectTapGestures { /* consume panel tap */ } },
                corners = 28.dp,
                designStyle = designStyle,
                reduceDynamicBlur = reduceDynamicBlur
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Kategori Seç",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .semantics { contentDescription = "Kategori seçimini kapat" }
                        ) {
                            ActionIcon(ActionGlyph.Close, Modifier.size(16.dp), Color(0xFFA0A0AC))
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 290.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Option: Kategori yok
                        CategoryOptionItem(
                            title = "Kategori yok",
                            isSelected = selectedCategoryId == null,
                            badge = {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF23232A))
                                        .border(0.6.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ActionIcon(ActionGlyph.Close, Modifier.size(13.dp), Color(0xFFA0A0AC))
                                }
                            },
                            onClick = {
                                onSelectCategory(null)
                                onDismiss()
                            }
                        )

                        if (customCategories.isNotEmpty()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = Color.White.copy(alpha = 0.08f)
                            )
                        }

                        // Custom category options
                        customCategories.forEach { category ->
                            val startColor = ReminderColorChoices.getOrNull(category.accentStart)?.color ?: Color(0xFF233F73)
                            val endColor = category.accentEnd?.let { ReminderColorChoices.getOrNull(it)?.color } ?: startColor
                            val icon = CategoryIcon.fromKey(category.iconKey)

                            CategoryOptionItem(
                                title = category.name,
                                isSelected = selectedCategoryId == category.id,
                                badge = {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Brush.linearGradient(listOf(startColor, endColor)))
                                            .border(0.6.dp, Color.White.copy(alpha = 0.22f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CategoryIconGlyph(icon = icon, modifier = Modifier.size(14.dp), color = Color.White)
                                    }
                                },
                                onClick = {
                                    onSelectCategory(category.id)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private val CategoryOptionShape = RoundedCornerShape(14.dp)

@Composable
private fun CategoryOptionItem(
    title: String,
    isSelected: Boolean,
    badge: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CategoryOptionShape)
            .clickable(onClick = onClick)
            .background(if (isSelected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        badge()
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            color = if (isSelected) Color.White else Color(0xFFE2E2EA),
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            ActionIcon(ActionGlyph.Check, Modifier.size(18.dp), Color(0xFF91B9FF))
        }
    }
}
