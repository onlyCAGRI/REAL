package com.cagri.bigboscagri.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.bigboscagri.data.CustomCategory
import com.cagri.bigboscagri.data.ReminderCategory

private enum class Glyph { Calendar, Clock, Star, Note, Completed, Add }

private data class CategoryStyle(
    val label: String,
    val glyph: Glyph,
    val base: Color,
    val upper: Color,
    val lower: Color
)

private sealed interface GridItem {
    data class System(val style: CategoryStyle, val category: ReminderCategory) : GridItem
    data class Custom(val category: CustomCategory) : GridItem
    data object Add : GridItem
}

private val systemCategories = listOf(
    CategoryStyle("Bugün", Glyph.Calendar, Color(0xFF141E4E), Color(0xFF23457D), Color(0xFF863D59)) to ReminderCategory.Today,
    CategoryStyle("Planlı", Glyph.Clock, Color(0xFF202142), Color(0xFF32655E), Color(0xFF803B4D)) to ReminderCategory.Planned,
    CategoryStyle("Önemli", Glyph.Star, Color(0xFF352137), Color(0xFF8E664A), Color(0xFF7B355F)) to ReminderCategory.Important,
    CategoryStyle("Uyarı yok", Glyph.Note, Color(0xFF222142), Color(0xFF414E83), Color(0xFF674878)) to ReminderCategory.NoAlert,
    CategoryStyle("Tamamlananlar", Glyph.Completed, Color(0xFF152F35), Color(0xFF387D70), Color(0xFF333F77)) to ReminderCategory.Completed
)

private val addCategoryStyle = CategoryStyle("Ekle", Glyph.Add, Color(0xFF1C1C1E), Color.Transparent, Color.Transparent)

@Composable
fun CategoryGrid(
    customCategories: List<CustomCategory> = emptyList(),
    onOpenCategory: (ReminderCategory) -> Unit,
    onOpenCustomCategory: (CustomCategory) -> Unit = {},
    onAddCategory: () -> Unit,
    selectedCategoryIds: Set<Long> = emptySet(),
    onToggleSelectCategory: (Long) -> Unit = {},
    onLongClickCategory: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isSelectionMode = selectedCategoryIds.isNotEmpty()
    val allItems = remember(customCategories) {
        buildList<GridItem> {
            systemCategories.forEach { (style, cat) ->
                add(GridItem.System(style, cat))
            }
            customCategories.forEach { customCat ->
                add(GridItem.Custom(customCat))
            }
            add(GridItem.Add)
        }
    }

    val pageCount = (allItems.size + 5) / 6
    val pagerState = rememberPagerState(pageCount = { pageCount })

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fill,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val pageItems = allItems.drop(page * 6).take(6)
            val row1 = pageItems.take(3)
            val row2 = pageItems.drop(3).take(3)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row1.forEach { item ->
                        when (item) {
                            is GridItem.System -> CategoryCard(
                                category = item.style,
                                onClick = { if (!isSelectionMode) onOpenCategory(item.category) },
                                modifier = Modifier.weight(1f)
                            )
                            is GridItem.Custom -> CustomCategoryCard(
                                category = item.category,
                                isSelectionMode = isSelectionMode,
                                isSelected = item.category.id in selectedCategoryIds,
                                onClick = {
                                    if (isSelectionMode) onToggleSelectCategory(item.category.id)
                                    else onOpenCustomCategory(item.category)
                                },
                                onLongClick = { onLongClickCategory(item.category.id) },
                                modifier = Modifier.weight(1f)
                            )
                            is GridItem.Add -> CategoryCard(
                                category = addCategoryStyle,
                                onClick = { if (!isSelectionMode) onAddCategory() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    repeat(3 - row1.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }

                if (row2.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row2.forEach { item ->
                            when (item) {
                                is GridItem.System -> CategoryCard(
                                    category = item.style,
                                    onClick = { if (!isSelectionMode) onOpenCategory(item.category) },
                                    modifier = Modifier.weight(1f)
                                )
                                is GridItem.Custom -> CustomCategoryCard(
                                    category = item.category,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = item.category.id in selectedCategoryIds,
                                    onClick = {
                                        if (isSelectionMode) onToggleSelectCategory(item.category.id)
                                        else onOpenCustomCategory(item.category)
                                    },
                                    onLongClick = { onLongClickCategory(item.category.id) },
                                    modifier = Modifier.weight(1f)
                                )
                                is GridItem.Add -> CategoryCard(
                                    category = addCategoryStyle,
                                    onClick = { if (!isSelectionMode) onAddCategory() },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        repeat(3 - row2.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    // Empty row placeholder to maintain uniform 2-row height across all pages
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(3) {
                            Spacer(Modifier.weight(1f).height(110.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(category: CategoryStyle, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        // Only the image is rounded: clipping this column also clips label glyphs.
        modifier = modifier.clickable(
            role = Role.Button, onClickLabel = category.label, onClick = onClick
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .clip(shape)
                .drawWithCache {
                    val topLight = Brush.radialGradient(
                        colors = listOf(category.upper, Color.Transparent),
                        center = Offset(size.width * 0.95f, -size.height * 0.1f),
                        radius = size.width * 1.12f
                    )
                    val bottomLight = Brush.radialGradient(
                        colors = listOf(category.lower, Color.Transparent),
                        center = Offset(-size.width * 0.05f, size.height * 1.1f),
                        radius = size.width * 1.15f
                    )
                    onDrawBehind {
                        drawRect(category.base)
                        drawRect(topLight)
                        drawRect(bottomLight)
                    }
                }
                .border(0.75.dp, Color.White.copy(alpha = 0.07f), shape),
            contentAlignment = Alignment.Center
        ) {
            CategoryGlyph(category.glyph, Modifier.size(32.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = category.label,
            color = Color(0xFFE7E7EB), fontSize = 12.sp, lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall.copy(platformStyle = PlatformTextStyle(includeFontPadding = true)),
            modifier = Modifier.fillMaxWidth().padding(start = 1.dp, end = 1.dp, bottom = 3.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomCategoryCard(
    category: CustomCategory,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    val startColor = ReminderColorChoices.getOrNull(category.accentStart)?.color ?: Color(0xFF233F73)
    val endColor = category.accentEnd?.let { ReminderColorChoices.getOrNull(it)?.color } ?: startColor
    val icon = CategoryIcon.fromKey(category.iconKey)

    val borderColor = when {
        isSelected -> Color(0xFF3B82F6)
        isSelectionMode -> Color.White.copy(alpha = 0.20f)
        else -> Color.White.copy(alpha = 0.07f)
    }
    val borderWidth = if (isSelected) 1.5.dp else 0.75.dp

    Column(
        modifier = modifier.combinedClickable(
            role = Role.Button,
            onClickLabel = if (isSelectionMode) (if (isSelected) "Seçimi kaldır" else "Seç") else category.name,
            onLongClickLabel = if (!isSelectionMode) "Kategorileri seç" else null,
            onLongClick = onLongClick,
            onClick = onClick
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
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
                .border(borderWidth, borderColor, shape),
            contentAlignment = Alignment.Center
        ) {
            CategoryIconGlyph(icon, Modifier.size(32.dp), color = Color.White)

            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color(0xFF3B82F6) else Color.Black.copy(alpha = 0.4f))
                        .border(
                            1.dp,
                            if (isSelected) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.45f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Canvas(modifier = Modifier.size(10.dp)) {
                            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            val path = Path().apply {
                                moveTo(size.width * 0.2f, size.height * 0.5f)
                                lineTo(size.width * 0.45f, size.height * 0.75f)
                                lineTo(size.width * 0.85f, size.height * 0.25f)
                            }
                            drawPath(path, Color.White, style = stroke)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = category.name,
            color = Color(0xFFE7E7EB), fontSize = 12.sp, lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall.copy(platformStyle = PlatformTextStyle(includeFontPadding = true)),
            modifier = Modifier.fillMaxWidth().padding(start = 1.dp, end = 1.dp, bottom = 3.dp)
        )
    }
}

// Small repo-native glyphs keep the white icon style consistent without adding
// an icon-pack dependency. Coordinates use a 24-unit drawing space.
@Composable
private fun CategoryGlyph(glyph: Glyph, modifier: Modifier) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val white = Color.White
            val stroke = Stroke(width = 1.9f, cap = StrokeCap.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
                drawLine(white, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.9f, cap = StrokeCap.Round)
            }
            when (glyph) {
                Glyph.Calendar -> {
                    drawRoundRect(white, Offset(3.5f, 5f), Size(17f, 16f), CornerRadius(3f, 3f), style = stroke)
                    line(3.5f, 10f, 20.5f, 10f)
                    line(8f, 3f, 8f, 7f)
                    line(16f, 3f, 16f, 7f)
                    drawCircle(white, 1.15f, Offset(8.5f, 14f))
                    drawCircle(white, 1.15f, Offset(15.5f, 14f))
                    drawCircle(white, 1.15f, Offset(8.5f, 17.5f))
                }
                Glyph.Clock -> {
                    drawCircle(white, 9f, Offset(12f, 12f), style = stroke)
                    line(12f, 6.5f, 12f, 12.5f)
                    line(12f, 12.5f, 16.5f, 15f)
                }
                Glyph.Star -> {
                    val star = Path().apply {
                        moveTo(12f, 2f)
                        lineTo(15f, 8.4f)
                        lineTo(22f, 9.4f)
                        lineTo(17f, 14.4f)
                        lineTo(18.2f, 21.4f)
                        lineTo(12f, 18.1f)
                        lineTo(5.8f, 21.4f)
                        lineTo(7f, 14.4f)
                        lineTo(2f, 9.4f)
                        lineTo(9f, 8.4f)
                        close()
                    }
                    drawPath(star, white)
                }
                Glyph.Note -> {
                    drawRoundRect(white, Offset(5f, 3f), Size(14f, 18f), CornerRadius(2.5f, 2.5f), style = stroke)
                    line(9f, 8f, 15f, 8f)
                    line(9f, 12f, 15f, 12f)
                    line(9f, 16f, 12.5f, 16f)
                }
                Glyph.Completed -> {
                    drawCircle(white, 9f, Offset(12f, 12f), style = stroke)
                    line(7.5f, 12f, 10.7f, 15.2f)
                    line(10.7f, 15.2f, 17f, 8.8f)
                }
                Glyph.Add -> {
                    line(12f, 4.5f, 12f, 19.5f)
                    line(4.5f, 12f, 19.5f, 12f)
                }
            }
        }
    }
}
